<p align="center">
  <img src="app/src/main/res/drawable-nodpi/lumabeat_logo.png" width="180" alt="LumaBeat logo">
</p>

<h1 align="center">LumaBeat</h1>

<p align="center">
  Music-reactive WiZ lighting for Android and Android TV.
</p>

LumaBeat listens to playback audio that Android explicitly shares, detects percussion with low latency, and turns each hit into a local WiZ brightness pulse. It runs without a WiZ account or cloud connection and is designed to stay open on an Android TV.

## Features

- Discovers compatible WiZ lights over the local network.
- Sends low-latency brightness pulses without changing white temperature by default.
- Offers Soft, Punchy, and Intense percussion profiles.
- Lets each powered-on light opt in or out of dynamic changes. Powered-off lights remain visible but unavailable and automatically resume their saved participation state when switched on.
- Optionally extracts up to three colors from visible Spotify, YouTube, or other player artwork and blends smoothly between them without notification access.
- Uses a logo-derived dark neon design system, a scroll-free 16:9 Android TV dashboard, high-contrast D-pad focus, and native internal settings pages.
- Provides a Black screen action while tracking. It keeps audio analysis and the foreground service running, lowers the application window brightness, hides system bars, and returns on any key press or tap.
- Checks verified GitHub Releases, downloads with Android `DownloadManager`, and validates the SHA-256 digest, package identity, version, and signing certificate before opening the system installer.

## Requirements

- Android 10 or newer.
- WiZ lights and the Android device on the same local network.
- Audio recording permission and approval of Android's playback-capture prompt when tracking starts.
- Optional per-app install permission for in-app updates.

## Build

Open the project in Android Studio or run:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
./gradlew.bat testCoreDebugUnitTest lintCoreDebug assembleCoreDebug
```

The debug APK is written to `app/build/outputs/apk/core/debug/app-core-debug.apk`.

## Audio capture and app compatibility

LumaBeat uses Android's supported MediaProjection and Audio Playback Capture APIs. Starting beat tracking opens a system-owned consent dialog; LumaBeat cannot approve or bypass it. On recent Android versions, Android requires fresh approval for every new tracking session, including an automatic start.

Capture is limited to audio usages intended for media or games, and the source application remains in control. Apps can allow capture, restrict it, or opt out entirely. Protected playback may therefore produce no signal even when it is audible. LumaBeat does not extract, decrypt, store, or upload audio.

This is the practical per-app boundary: LumaBeat reacts only to streams Android permits in the approved capture session, not indiscriminately to microphones, calls, alarms, or private app audio.

## Distribution editions

GitHub Releases publish the **Core** edition. Its APK does not declare a notification-listener service, avoiding the sensitive notification-access capability that Play Protect enhanced fraud protection blocks for some direct internet installs. Artwork colors are implemented through the same explicit screen-capture session used while beat tracking is active.

The source tree also contains an experimental **Full** flavor that reads media-session artwork through notification access. It is intentionally not distributed as a GitHub APK. That capability should be distributed through a verified store path or reviewed separately before public use.

## How artwork colors work in Core

When artwork colors are enabled, LumaBeat creates one low-resolution virtual display inside the MediaProjection session that Android has explicitly approved. While tracking, it samples the visible player screen approximately every two seconds and extracts up to three genuinely distinct dominant colors locally. Return to the Spotify, YouTube, or other Now Playing screen after starting LumaBeat so its artwork is visible to the approved capture.

Diversity is measured by hue as well as RGB distance, so light and dark variants from the same color family do not occupy separate palette slots. White is eligible; black and gray pixels are ignored because they would duplicate dimming. Black or protected frames do not overwrite the last valid palette.

The sampled frame exists only in memory at a maximum edge of 320 pixels. LumaBeat does not save, transmit, or expose screenshots. The live palette is shown on the dashboard with its RGB hex values. Natural intensity preserves sampled colors, while Vivid and Bold increase saturation without increasing their peak RGB channel. A low-frequency color loop blends through the palette while the percussion detector controls brightness.

## Experimental media-session artwork (Full source flavor)

The Full source flavor asks Android for notification-listener access. It reads artwork already exposed by active media sessions, samples the image locally, and selects up to three genuinely distinct dominant colors. Diversity is measured by hue as well as RGB distance, so light and dark variants from the same color family do not occupy separate palette slots. White is eligible; black and gray pixels are ignored because they would duplicate dimming. Fully black or grayscale artwork falls back to neutral white.

Artwork availability depends on what each media app publishes through Android's media session.

## Black screen mode

Black screen mode renders black pixels and requests the minimum application-window brightness while keeping LumaBeat awake and tracking in the foreground. Any remote key, Back, or touch exits the mode and restores the normal window brightness and system bars. It deliberately does not lock the device or place the TV in standby.

Android does not expose a portable application API for switching off an LCD television's physical panel backlight while leaving playback and application processing active. On OLED displays, black pixels normally emit no light. On LCD televisions, the panel may still keep its backlight on at a firmware-defined level even though the image is black. Vendor-specific "picture off" or "audio only" controls are outside LumaBeat's current cross-device scope.

### Full flavor on Android TV firmware without Notification Access settings

Some vendor firmware, including certain TCL Android TV 11 builds, omits the system screen used to grant notification-listener access. With ADB already connected to the TV, grant the standard listener access directly:

```powershell
adb shell cmd notification allow_listener com.lumabeat.app/com.lumabeat.app.media.MediaNotificationListenerService
```

If the vendor also blocks background service binding, allow its auto-start AppOp:

```powershell
adb shell cmd appops set com.lumabeat.app APP_AUTO_START allow
```

These commands apply only to a locally built Full flavor and grant only the capability needed to observe active media artwork. They do not bypass DRM or grant access to protected audio content.

## Updates

Official releases contain:

- `LumaBeat.apk`
- `LumaBeat.apk.sha256`
- `release.json`

The app trusts only immutable assets under `thomrnowtea/lumabeat` on GitHub. Android still requires explicit approval for unknown-source installation and final package installation.

Download the latest stable build from [GitHub Releases](https://github.com/thomrnowtea/lumabeat/releases/latest).

## Privacy

Audio analysis and reduced-resolution screen color sampling stay on the device. Screen frames are processed in memory and are never saved or uploaded. LumaBeat communicates with WiZ lights over the LAN and contacts GitHub only to check or download application updates. The public Core APK does not request notification access.

## Hardware longevity

LumaBeat changes RGB and dimming setpoints through the WiZ local protocol; it does not repeatedly cut and restore mains power. This is closer to WiZ's own dimming and dynamic-scene behavior than to physical on/off cycling.

There is no published WiZ endurance rating for continuous third-party LAN updates at LumaBeat's frequency, so the project cannot claim that reactive use has zero effect on lamp life. Repeated changes keep the LED driver active, while heat remains the main practical concern for LED and power-electronics longevity. For an always-on TV installation, use the Soft profile, keep the lamp adequately ventilated, avoid enclosed fixtures unless the lamp is rated for them, and stop the effect if a lamp flickers, becomes unusually hot, or behaves erratically. See the [WiZ Pro API reference](https://docs.pro.wizconnected.com/) for the separate RGB, dimming, and dynamic-scene controls used by compatible WiZ products.

## License

Copyright © 2026 LumaBeat. All rights reserved. No license is granted to copy, modify, or redistribute this source code unless the copyright owner provides one separately. Third-party components retain their respective licenses.
