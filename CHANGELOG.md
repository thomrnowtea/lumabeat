# Changelog

All notable changes to LumaBeat are documented here.

## Unreleased

## 0.1.1 - 2026-09-01

- Reduce beat latency with near-continuous audio windows, non-blocking brightness dispatch, and a reusable WiZ UDP socket.
- Blend continuously between artwork colors and across track changes while keeping percussion pulses limited to brightness.
- Limit artwork palettes to three colors, keep white, ignore black and gray, and fall back safely for monochrome covers.
- Preserve faithful artwork colors, group similar hue families, and add Natural, Vivid, and Bold saturation choices.
- Move artwork controls and the live detected palette to the dashboard and replace modal settings with internal pages.
- Read Spotify TV artwork directly from active media sessions instead of accepting unrelated promotional notification images.
- Refresh the three-color palette when media-session metadata changes without requiring a new notification.
- Handle Android TV firmware that does not expose Notification Access settings and document the TCL ADB fallback.
- Keep powered-off WiZ lights visible but unavailable, monitor their power state, and automatically restore participation when they are switched on.
- Prevent brightness and color effects from powering on a lamp that is currently off.
- Add a Black screen mode that keeps beat tracking in the foreground without locking or suspending the device.
- Introduce a logo-derived semantic design system with saturated violet, cyan, and green accents, clearer surface hierarchy, and immediate high-contrast D-pad focus.
- Fit the dashboard and internal settings pages within a 16:9 landscape viewport without mandatory scrolling while retaining a responsive portrait layout.

## 0.1.0 - 2026-08-31

- Added low-latency output-mix percussion detection with three response profiles.
- Added local WiZ discovery and per-light participation controls.
- Added a landscape-first Android TV interface with D-pad focus.
- Added optional three-color album-art palette rotation through media notification access.
- Added verified in-app updates backed by GitHub Releases.
- Added the first LumaBeat icon and Android TV banner.
