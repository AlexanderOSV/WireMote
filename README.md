# WireMote

**WireMote** is an Android remote control for Linux PCs on the same local
network. It lets you control a Linux PC from your phone using touch, keyboard,
and remote control buttons. It also supports saving PC profiles and waking a
configured PC with Wake-on-LAN.

## Requirements for users

- An Android phone running Android 8.0 (API 26) or newer.
- The prebuilt WireMote APK.
- A Linux PC running the WireMote daemon.
- The phone and Linux PC connected to the same local network.
- Network access to UDP port `39393` and TCP port `39394`.
- If Wake-on-LAN is required, Wake-on-LAN must be enabled in the PC's
  BIOS/UEFI firmware and supported by the Linux network adapter configuration.
- If running the daemon from this repository, the Linux PC also needs Rust and
  Cargo. The daemon's dependencies are downloaded automatically by Cargo.

Users do not need Android Studio, Java, Gradle, or the Android project source
code to install and use the prebuilt APK.

## Installation

### Android app

1. Download the prebuilt [`app-release.apk`](app-release.apk) from the project's
   release page.
2. Connect the Android phone to the computer with a USB cable, unlock the
   phone, and select **File transfer** or **MTP** when Android asks how to use
   the USB connection.
3. Copy `app-release.apk` to the phone's **Downloads** folder.
4. Open **Files** or **Files by Google**, open **Downloads**, and tap the APK.
5. If Android blocks the installation, open **Settings** from the warning and
   enable **Allow from this source** for the browser or Files app.
6. Return to the APK and tap **Install**.
7. If an older WireMote installation prevents the update, uninstall it and
   install the new APK.

### Linux daemon

The daemon must be installed and running on the Linux PC that WireMote will
control. The following commands are for Debian or Ubuntu based Linux systems.

1. Install Git, Rust, Cargo, and the native packages needed to build the
   daemon:

```bash
sudo apt update
sudo apt install -y git curl build-essential pkg-config libx11-dev
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

If Rust is already installed, update it instead:

```bash
rustup update
```

2. Copy the WireMote repository to the Linux PC. To download it with Git:

```bash
cd ~
git clone https://github.com/AlexanderOSV/WireMote.git
cd WireMote
```

If the repository has already been copied, update it instead:

```bash
cd ~/WireMote
git pull
```

3. Build and start the daemon:

```bash
cd ~/WireMote/linux-daemon
cargo run
```

The first `cargo run` downloads the Rust dependencies and compiles the daemon.
Keep this terminal open while using the Android app. The daemon listens for LAN
discovery on UDP port `39393` and control traffic on TCP port `39394`.

4. If the Linux PC uses UFW, allow the required local-network ports:

```bash
sudo ufw allow 39393/udp
sudo ufw allow 39394/tcp
sudo ufw reload
sudo ufw status
```

The phone and Linux PC must be connected to the same local network. If the
daemon is running on a different Linux distribution, install Git, Rust/Cargo,
and the equivalent X11/build packages using that distribution's package
manager before running the `git clone` and `cargo run` commands above.

**Wake-on-LAN note:** Wake-on-LAN may need to be enabled in the PC's BIOS/UEFI
firmware settings and in the Linux network adapter settings. The exact option
name varies by manufacturer; look for **Wake on LAN**, **PCI-E wake**, or a
similar power-management setting.

## How to use WireMote

1. Start the Linux daemon on the PC you want to control.
2. Open WireMote on the phone and click on the settings icon.
3. Navigate to the PC profile section and select **+ profile**.
4. Select **Detect PCs** to find daemons on the local network, or enter the PC
   name, IP address, and port manually. The default port is `39394`.
5. Optionally enter the PC's MAC address and broadcast address to enable
   Wake-on-LAN.
6. Save the profile and select it.
7. Tap **Connect**.
8. Use the app controls:
   - **Mouse:** move the pointer, scroll, and send left or right clicks.
   - **Keyboard:** send text and common keys.
   - **Remote:** send directional, select and back actions.
9. Adjust mouse sensitivity in the selected profile if needed.
10. If Wake-on-LAN is configured, use the power button to wake a sleeping PC
    and reconnect.
