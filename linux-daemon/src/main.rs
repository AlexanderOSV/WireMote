mod protocol;
mod input;

use axum::{
    extract::ws::{Message as WsMessage, WebSocket, WebSocketUpgrade},
    response::IntoResponse,
    routing::get,
    Router,
};
use protocol::{is_allowed_command, Message, Response};
use serde_json::Value;
use std::net::SocketAddr;
use tower_http::trace::TraceLayer;
use tracing::{info, warn};

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();

    let app = Router::new()
        .route("/health", get(health))
        .route("/ws", get(websocket))
        .layer(TraceLayer::new_for_http());

    let address = SocketAddr::from(([0, 0, 0, 0], 39394));
    info!(?address, "my-remote daemon listening");
    let listener = tokio::net::TcpListener::bind(address).await.expect("bind daemon port");
    axum::serve(listener, app).await.expect("serve daemon");
}

async fn health() -> impl IntoResponse {
    axum::Json(serde_json::json!({ "service": "my-remote", "version": "0.1" }))
}

async fn websocket(ws: WebSocketUpgrade) -> impl IntoResponse {
    ws.on_upgrade(handle_socket)
}

async fn handle_socket(mut socket: WebSocket) {
    while let Some(Ok(message)) = socket.recv().await {
        let WsMessage::Text(text) = message else { continue };
        let response = match serde_json::from_str::<Message>(&text) {
            Ok(message) => handle_message(message),
            Err(error) => {
                warn!(%error, "invalid protocol message");
                Response::error(None, "invalid JSON message")
            }
        };

        let encoded = serde_json::to_string(&response).expect("serialize response");
        if socket.send(WsMessage::Text(encoded.into())).await.is_err() {
            break;
        }
    }
}

fn handle_message(message: Message) -> Response {
    match message.r#type.as_str() {
        "daemon.status" => Response::status(message.request_id),
        "command.execute" => handle_command(message),
        "mouse.move" => handle_input(message, |payload| {
            let dx = payload
                .get("dx")
                .and_then(Value::as_f64)
                .ok_or_else(|| "dx is required".to_string())?;
            let dy = payload
                .get("dy")
                .and_then(Value::as_f64)
                .ok_or_else(|| "dy is required".to_string())?;
            input::mouse_move(dx, dy)
        }),
        "mouse.button" => handle_input(message, input::mouse_button),
        "mouse.scroll" => handle_input(message, input::mouse_scroll),
        "keyboard.text" => handle_input(message, |payload| {
            let text = payload
                .get("text")
                .and_then(Value::as_str)
                .ok_or_else(|| "text is required".to_string())?;
            input::keyboard_text(text)
        }),
        "keyboard.key" => handle_input(message, |payload| {
            let key = payload
                .get("key")
                .and_then(Value::as_str)
                .ok_or_else(|| "key is required".to_string())?;
            let direction = payload.get("direction").and_then(Value::as_str).unwrap_or("click");
            input::keyboard_key(key, direction)
        }),
        "pair.begin" | "pair.confirm" | "remote.dpad" | "remote.select" | "remote.back"
        | "remote.play_pause" => Response::error(message.request_id, "adapter not configured"),
        _ => Response::error(message.request_id, "unsupported message type"),
    }
}

fn handle_command(message: Message) -> Response {
    let Some(command_id) = message.payload.get("command_id").and_then(Value::as_str) else {
        return Response::error(message.request_id, "command_id is required");
    };
    if !is_allowed_command(command_id) {
        return Response::error(message.request_id, "command is not allowlisted");
    }
    match input::execute_command(command_id) {
        Ok(()) => Response::result(message.request_id, command_id),
        Err(error) => Response::error(message.request_id, &error),
    }
}

fn handle_input<F>(message: Message, action: F) -> Response
where
    F: FnOnce(&Value) -> Result<(), String>,
{
    match action(&message.payload) {
        Ok(()) => Response::result(message.request_id, &message.r#type),
        Err(error) => Response::error(message.request_id, &error),
    }
}
