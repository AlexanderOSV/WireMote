use enigo::{Axis, Button, Coordinate, Direction, Enigo, Key, Keyboard, Mouse, Settings};
use serde_json::Value;
use std::process::Command;

pub fn mouse_move(dx: f64, dy: f64) -> Result<(), String> {
    if !dx.is_finite() || !dy.is_finite() {
        return Err("mouse movement must be finite".to_string());
    }
    let mut enigo = Enigo::new(&Settings::default()).map_err(|error| error.to_string())?;
    enigo
        .move_mouse(dx.round() as i32, dy.round() as i32, Coordinate::Rel)
        .map_err(|error| error.to_string())
}

pub fn mouse_button(payload: &Value) -> Result<(), String> {
    let button = match payload.get("button").and_then(Value::as_str) {
        Some("left") => Button::Left,
        Some("right") => Button::Right,
        Some("middle") => Button::Middle,
        _ => return Err("button must be left, right, or middle".to_string()),
    };
    let direction = match payload.get("direction").and_then(Value::as_str) {
        Some("press") => Direction::Press,
        Some("release") => Direction::Release,
        Some("click") | None => Direction::Click,
        _ => return Err("direction must be press, release, or click".to_string()),
    };
    let mut enigo = Enigo::new(&Settings::default()).map_err(|error| error.to_string())?;
    enigo.button(button, direction).map_err(|error| error.to_string())
}

pub fn mouse_scroll(payload: &Value) -> Result<(), String> {
    let amount = payload
        .get("amount")
        .and_then(Value::as_i64)
        .ok_or_else(|| "scroll amount is required".to_string())? as i32;
    let axis = match payload.get("axis").and_then(Value::as_str) {
        Some("horizontal") => Axis::Horizontal,
        Some("vertical") | None => Axis::Vertical,
        _ => return Err("axis must be horizontal or vertical".to_string()),
    };
    let mut enigo = Enigo::new(&Settings::default()).map_err(|error| error.to_string())?;
    enigo.scroll(amount, axis).map_err(|error| error.to_string())
}

pub fn keyboard_text(text: &str) -> Result<(), String> {
    let mut enigo = Enigo::new(&Settings::default()).map_err(|error| error.to_string())?;
    enigo.text(text).map_err(|error| error.to_string())
}

pub fn keyboard_key(key: &str, direction: &str) -> Result<(), String> {
    let key = match key {
        "Enter" => Key::Return,
        "Escape" => Key::Escape,
        "Backspace" => Key::Backspace,
        "Tab" => Key::Tab,
        "Space" => Key::Space,
        "ArrowUp" => Key::UpArrow,
        "ArrowDown" => Key::DownArrow,
        "ArrowLeft" => Key::LeftArrow,
        "ArrowRight" => Key::RightArrow,
        value => value
            .chars()
            .next()
            .map(Key::Unicode)
            .ok_or_else(|| "key must not be empty".to_string())?,
    };
    let direction = match direction {
        "press" => Direction::Press,
        "release" => Direction::Release,
        "click" | "" => Direction::Click,
        _ => return Err("direction must be press, release, or click".to_string()),
    };
    let mut enigo = Enigo::new(&Settings::default()).map_err(|error| error.to_string())?;
    enigo.key(key, direction).map_err(|error| error.to_string())
}

pub fn remote_dpad(payload: &Value) -> Result<(), String> {
    let key = match payload.get("direction").and_then(Value::as_str) {
        Some("up") => "ArrowUp",
        Some("down") => "ArrowDown",
        Some("left") => "ArrowLeft",
        Some("right") => "ArrowRight",
        _ => return Err("direction must be up, down, left, or right".to_string()),
    };
    keyboard_key(key, "click")
}

pub fn execute_command(command_id: &str) -> Result<(), String> {
    let (program, args): (&str, &[&str]) = match command_id {
        "sleep" => ("systemctl", &["suspend"]),
        "lock" => ("loginctl", &["lock-session"]),
        "shutdown" => ("systemctl", &["poweroff"]),
        "reboot" => ("systemctl", &["reboot"]),
        _ => return Err("command is not allowlisted".to_string()),
    };

    let status = Command::new(program)
        .args(args)
        .status()
        .map_err(|error| format!("could not start {program}: {error}"))?;
    if status.success() {
        Ok(())
    } else {
        Err(format!("{command_id} exited with status {status}"))
    }
}
