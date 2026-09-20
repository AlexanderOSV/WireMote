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
use std::time::Duration;
use tower_http::trace::TraceLayer;
use tracing::{info, warn};

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();

    tokio::spawn(discovery_broadcast());

    let app = Router::new()
        .route("/health", get(health))
        .route("/ws", get(websocket))
        .layer(TraceLayer::new_for_http());

    let address = SocketAddr::from(([0, 0, 0, 0], 39394));
    info!(?address, "my-remote daemon listening");
    let listener = tokio::net::TcpListener::bind(address).await.expect("bind daemon port");
    axum::serve(listener, app).await.expect("serve daemon");
}

async fn discovery_broadcast() {
    let socket = match tokio::net::UdpSocket::bind("0.0.0.0:39393").await {
        Ok(socket) => socket,
        Err(error) => {
            warn!(%error, "could not bind discovery socket");
            return;
        }
    };
    if let Err(error) = socket.set_broadcast(true) {
        warn!(%error, "could not enable discovery broadcast");
        return;
    }

    let host = match std::net::UdpSocket::bind("0.0.0.0:0")
        .and_then(|socket| {
            socket.connect("8.8.8.8:80")?;
            socket.local_addr()
        }) {
        Ok(address) => address.ip().to_string(),
        Err(error) => {
            warn!(%error, "could not determine discovery host address");
            return;
        }
    };
    let mac = std::fs::read_to_string("/proc/net/route")
        .ok()
        .and_then(|routes| {
            routes.lines().skip(1).find_map(|line| {
                let fields: Vec<_> = line.split_whitespace().collect();
                (fields.get(1) == Some(&"00000000"))
                    .then(|| fields.first().map(|interface| (*interface).to_string()))?
            })
        })
        .and_then(|interface| {
            std::fs::read_to_string(format!("/sys/class/net/{interface}/address"))
                .ok()
                .map(|address| address.trim().to_string())
                .filter(|address| address.len() == 17 && address != "00:00:00:00:00:00")
        });
    let advertisement = serde_json::json!({
        "service": "my-remote",
        "version": 1,
        "name": "WireMote",
        "host": host,
        "port": 39394,
        "mac": mac,
    }).to_string();
    let destination = SocketAddr::from(([255, 255, 255, 255], 39393));
    let mut interval = tokio::time::interval(Duration::from_secs(5));

    let mut buffer = [0u8; 1024];
    loop {
        tokio::select! {
            result = interval.tick() => {
                let _ = result;
                if let Err(error) = socket.send_to(advertisement.as_bytes(), destination).await {
                    warn!(%error, "discovery broadcast failed");
                }
            }
            result = socket.recv_from(&mut buffer) => {
                match result {
                    Ok((length, address)) => {
                        let is_probe = serde_json::from_slice::<serde_json::Value>(&buffer[..length])
                            .map(|message| {
                                message.get("service").and_then(Value::as_str) == Some("my-remote")
                                    && message.get("version").and_then(Value::as_i64) == Some(1)
                                    && message.get("action").and_then(Value::as_str) == Some("discover")
                            })
                            .unwrap_or(false);
                        if is_probe {
                            if let Err(error) = socket.send_to(advertisement.as_bytes(), address).await {
                                warn!(%error, "discovery response failed");
                            }
                        }
                    }
                    Err(error) => warn!(%error, "discovery receive failed"),
                }
            }
        }
    }
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
        "system.sleep" => match input::execute_command("sleep") {
            Ok(()) => Response::result(message.request_id, "system.sleep"),
            Err(error) => Response::error(message.request_id, &error),
        },
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
        "remote.dpad" => handle_input(message, input::remote_dpad),
        "remote.select" => match input::remote_select() {
            Ok(()) => Response::result(message.request_id, "remote.select"),
            Err(error) => Response::error(message.request_id, &error),
        },
        "remote.back" => match input::remote_back() {
            Ok(()) => Response::result(message.request_id, "remote.back"),
            Err(error) => Response::error(message.request_id, &error),
        },
        "pair.begin" | "pair.confirm" | "remote.play_pause" => {
            Response::error(message.request_id, "adapter not configured")
        }
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
