# My Remote

An Android remote for controlling Linux PCs over a local network.

## Repository layout

- `android/` - Android Studio project for the phone remote.
- `linux-daemon/` - Rust daemon running on each Linux PC.
- `protocol/` - Versioned transport and message documentation shared by both clients.

## Initial development

The first milestone is a local-network vertical slice:

1. Discover a daemon on the LAN.
2. Pair a phone with a daemon using a one-time code.
3. Persist a named PC profile on the phone.
4. Send mouse, keyboard, remote-control, and custom command actions.
5. Wake a configured PC with Wake-on-LAN.

See `protocol/README.md` for the wire contract and security notes.
