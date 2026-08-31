# Contributing

## Local verification

Run the complete local gate before opening a pull request:

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

For UI changes, install the debug APK and verify both portrait and landscape layouts. For WiZ behavior, use real lights on an isolated local network or the restricted emulator bridge.

## Releases

Stable versions use annotated semantic tags such as `v0.1.0`. A pushed `v*` tag triggers the release workflow, which builds a consistently signed APK and publishes its checksum and updater metadata.

Release signing material must never be committed to this repository.
