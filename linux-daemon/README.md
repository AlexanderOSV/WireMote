# Linux daemon

The daemon listens on TCP port `39394` for the prototype WebSocket transport.

```powershell
cargo run
```

Current behavior is deliberately conservative: protocol messages are parsed, status is reported, and command IDs are checked against an allowlist. OS integrations for input injection, system power actions, discovery, pairing, and persistent daemon configuration are the next implementation layer.
