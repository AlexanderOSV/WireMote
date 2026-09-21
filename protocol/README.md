# WireMote Protocol

Protocol version: `0.1`

## Transport

- LAN discovery uses UDP broadcast on port `39393`.
- Control traffic uses a WebSocket connection to `ws://<daemon>:39394/ws` during the prototype.
- Every control message is a UTF-8 JSON envelope described in `schema.json`.
- Production pairing must upgrade the connection to authenticated TLS before commands are enabled.

## Discovery

A daemon broadcasts a compact JSON advertisement once every five seconds:

```json
{
  "service": "my-remote",
  "version": 1,
  "name": "living-room-pc",
  "host": "192.168.1.20",
  "port": 39394,
  "fingerprint": "sha256:..."
}
```

The Android app should show discovered devices as candidates only. A user must explicitly pair a device and confirm the displayed code before storing its profile.

## Message model

Messages are request/response or event envelopes:

```json
{"type":"mouse.move","request_id":"...","payload":{"dx":4.0,"dy":-2.0}}
```

Supported initial message types:

- `pair.begin`, `pair.confirm`
- `mouse.move`, `mouse.button`, `mouse.scroll`
- `keyboard.text`, `keyboard.key`
- `remote.dpad`, `remote.select`, `remote.back`, `remote.play_pause`
- `command.execute`
- `command.result`, `daemon.status`, `error`

## Profiles and settings

Profiles live on the Android device and contain a stable daemon ID, display name, network endpoint, pairing metadata, and user-selected command definitions. Mouse sensitivity is a profile setting and must remain local to the phone. Command execution is allowlisted by the daemon; arbitrary shell text is never accepted from the network.

## Wake-on-LAN

Wake-on-LAN is a local UDP magic-packet operation initiated by the Android app. A profile stores a validated MAC address and optional broadcast address. The daemon is not required to be running for wake-up.

## Linux actions

The daemon accepts only the fixed command IDs `sleep`, `lock`, `shutdown`, and `reboot`. Mouse and keyboard messages are dispatched through the Linux desktop input adapter. Arbitrary shell commands are never accepted from the network.

## Security baseline

The prototype transport is intentionally marked insecure. Before production use, add authenticated encryption, replay protection, pairing expiry, per-profile device keys, rate limits, and explicit command authorization on the daemon.
