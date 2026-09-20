use serde::{Deserialize, Serialize};
use serde_json::Value;
use uuid::Uuid;

#[derive(Debug, Deserialize)]
pub struct Message {
    pub r#type: String,
    pub request_id: Option<String>,
    #[serde(default)]
    pub payload: Value,
}

#[derive(Debug, Serialize)]
pub struct Response {
    pub r#type: &'static str,
    pub request_id: Option<String>,
    pub payload: Value,
}

impl Response {
    pub fn status(request_id: Option<String>) -> Self {
        Self {
            r#type: "daemon.status",
            request_id,
            payload: serde_json::json!({
                "daemon_id": Uuid::nil(),
                "version": "0.1",
                "capabilities": ["mouse", "keyboard", "remote", "commands"]
            }),
        }
    }

    pub fn error(request_id: Option<String>, message: &str) -> Self {
        Self {
            r#type: "error",
            request_id,
            payload: serde_json::json!({ "message": message }),
        }
    }

    pub fn result(request_id: Option<String>, operation: &str) -> Self {
        Self {
            r#type: "command.result",
            request_id,
            payload: serde_json::json!({ "ok": true, "operation": operation }),
        }
    }
}

pub fn is_allowed_command(command_id: &str) -> bool {
    matches!(command_id, "sleep" | "lock" | "shutdown" | "reboot")
}
