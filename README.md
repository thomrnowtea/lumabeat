<p align="center">
  <img src="app/src/main/res/drawable-nodpi/lumabeat_logo.png" width="180" alt="LumaBeat logo">
</p>

<h1 align="center">LumaBeat</h1>

<p align="center">
  Music-reactive WiZ lighting for Android and Android TV.
</p>

LumaBeat listens to the device output mix, detects percussion with low latency, and turns each hit into a local WiZ brightness pulse. It runs without a WiZ account or cloud connection and is designed to stay open on an Android TV.

## Features

- Discovers compatible WiZ lights over the local network.
- Sends low-latency brightness pulses without changing white temperature by default.
- Offers Soft, Punchy, and Intense percussion profiles.
- Lets each discovered light opt in or out of dynamic changes.
- Uses an Android TV dashboard with D-pad focus and no page-level landscape scrolling.
- Optionally reads active media artwork through Android notification access, extracts its three dominant colors locally, and cycles through them while beat tracking remains active.
- Checks verified GitHub Releases, downloads with Android `DownloadManager`, and validates the SHA-256 digest, package identity, version, and signing certificate before opening the system installer.

## Requirements

- Android 8.0 or newer.
- WiZ lights and the Android device on the same local network.
- Audio recording permission for output-mix analysis.
- Optional notification access for album-art colors.
- Optional per-app install permission for in-app updates.

## Build

Open the project in Android Studio or run:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
./gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Emulator and real WiZ lights

An Android emulator cannot directly receive LAN broadcast responses. Start the restricted host bridge in a separate PowerShell window:

```powershell
./scripts/wiz-host-bridge.ps1
```

Then build, install, and launch the app. The emulator automatically routes discovery and allow-listed WiZ commands through `10.0.2.2:38900`.

The bridge accepts commands only for lights it discovered itself. It must remain running during emulator-to-LAN tests.

## How artwork colors work

When enabled, LumaBeat asks Android for notification-listener access. It reads artwork already exposed by active media sessions, samples the image locally, rejects dark and neutral pixels, and selects three distinct dominant colors. The next eligible beat advances through that palette every few seconds, so color traffic does not add latency to the brightness pulse stream.

No protected audio is extracted, stored, or decrypted. Artwork availability depends on what each media app publishes through Android's media session.

## Updates

Official releases contain:

- `LumaBeat.apk`
- `LumaBeat.apk.sha256`
- `release.json`

The app trusts only immutable assets under `thomrnowtea/lumabeat` on GitHub. Android still requires explicit approval for unknown-source installation and final package installation.

Download the latest stable build from [GitHub Releases](https://github.com/thomrnowtea/lumabeat/releases/latest).

## Privacy

Audio analysis stays on the device. LumaBeat communicates with WiZ lights over the LAN and contacts GitHub only to check or download application updates. Notification access is optional and is used only to read active media artwork for color extraction.

## License

Copyright © 2026 LumaBeat. All rights reserved. No license is granted to copy, modify, or redistribute this source code unless the copyright owner provides one separately. Third-party components retain their respective licenses.
