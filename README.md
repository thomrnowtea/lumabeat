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
- Uses an Android TV dashboard with D-pad focus, direct artwork controls, and native internal settings pages.
- Optionally reads active media artwork through Android notification access, extracts up to three distinct dominant colors locally, displays the live palette, and blends smoothly between them while beat tracking remains active.
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

## How artwork colors work

When enabled from the main dashboard, LumaBeat asks Android for notification-listener access. It reads artwork already exposed by active media sessions, samples the image locally, and selects up to three genuinely distinct dominant colors. Diversity is measured by hue as well as RGB distance, so light and dark variants from the same color family do not occupy separate palette slots. White is eligible; black and gray pixels are ignored because they would duplicate dimming. Fully black or grayscale artwork falls back to neutral white.

The live palette is shown on the dashboard with its RGB hex values. Natural intensity preserves the sampled artwork colors, while Vivid and Bold increase saturation without increasing their peak RGB channel. A low-frequency color loop continuously blends through the resulting palette and across track changes while the percussion detector controls only brightness.

No protected audio is extracted, stored, or decrypted. Artwork availability depends on what each media app publishes through Android's media session.

### Android TV firmware without Notification Access settings

Some vendor firmware, including certain TCL Android TV 11 builds, omits the system screen used to grant notification-listener access. With ADB already connected to the TV, grant the standard listener access directly:

```powershell
adb shell cmd notification allow_listener com.lumabeat.app/com.lumabeat.app.media.MediaNotificationListenerService
```

If the vendor also blocks background service binding, allow its auto-start AppOp:

```powershell
adb shell cmd appops set com.lumabeat.app APP_AUTO_START allow
```

These commands grant only the capabilities needed to observe active media artwork. They do not bypass DRM or grant access to protected audio content.

## Updates

Official releases contain:

- `LumaBeat.apk`
- `LumaBeat.apk.sha256`
- `release.json`

The app trusts only immutable assets under `thomrnowtea/lumabeat` on GitHub. Android still requires explicit approval for unknown-source installation and final package installation.

Download the latest stable build from [GitHub Releases](https://github.com/thomrnowtea/lumabeat/releases/latest).

## Privacy

Audio analysis stays on the device. LumaBeat communicates with WiZ lights over the LAN and contacts GitHub only to check or download application updates. Notification access is optional and is used only to read active media artwork for color extraction.

## Hardware longevity

LumaBeat changes RGB and dimming setpoints through the WiZ local protocol; it does not repeatedly cut and restore mains power. This is closer to WiZ's own dimming and dynamic-scene behavior than to physical on/off cycling.

There is no published WiZ endurance rating for continuous third-party LAN updates at LumaBeat's frequency, so the project cannot claim that reactive use has zero effect on lamp life. Repeated changes keep the LED driver active, while heat remains the main practical concern for LED and power-electronics longevity. For an always-on TV installation, use the Soft profile, keep the lamp adequately ventilated, avoid enclosed fixtures unless the lamp is rated for them, and stop the effect if a lamp flickers, becomes unusually hot, or behaves erratically. See the [WiZ Pro API reference](https://docs.pro.wizconnected.com/) for the separate RGB, dimming, and dynamic-scene controls used by compatible WiZ products.

## License

Copyright © 2026 LumaBeat. All rights reserved. No license is granted to copy, modify, or redistribute this source code unless the copyright owner provides one separately. Third-party components retain their respective licenses.
