# Changelog

All notable changes to LumaBeat are documented here.

## Unreleased

- Blend continuously between artwork colors and across track changes while keeping percussion pulses limited to brightness.
- Limit artwork palettes to three colors, keep white, ignore black and gray, and fall back safely for monochrome covers.
- Preserve faithful artwork colors, group similar hue families, and add Natural, Vivid, and Bold saturation choices.
- Move artwork controls and the live detected palette to the dashboard and replace modal settings with internal pages.
- Read Spotify TV artwork directly from active media sessions instead of accepting unrelated promotional notification images.
- Refresh the three-color palette when media-session metadata changes without requiring a new notification.
- Handle Android TV firmware that does not expose Notification Access settings and document the TCL ADB fallback.

## 0.1.0 - 2026-08-31

- Added low-latency output-mix percussion detection with three response profiles.
- Added local WiZ discovery and per-light participation controls.
- Added a landscape-first Android TV interface with D-pad focus.
- Added optional three-color album-art palette rotation through media notification access.
- Added verified in-app updates backed by GitHub Releases.
- Added the first LumaBeat icon and Android TV banner.
