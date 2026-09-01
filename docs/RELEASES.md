# Release process

## Continuous verification

Every push to `main` and every pull request runs unit tests, Android lint, and debug APK assembly. The resulting debug APK is retained as a short-lived workflow artifact.

## Create a release

1. Update `CHANGELOG.md` and the fallback version in `app/build.gradle.kts`.
2. Run `./gradlew.bat testCoreDebugUnitTest lintCoreDebug assembleCoreDebug compileFullDebugKotlin` locally.
3. Push the release commit to `main`.
4. Create and push an annotated semantic tag such as `v0.2.0`.
5. GitHub Actions builds and verifies the signed Core APK, asserts that it contains no notification-listener declaration, and creates the GitHub Release.

Each release publishes `LumaBeat.apk`, `LumaBeat.apk.sha256`, and schema-v1 `release.json`.

The public APK is the `core` distribution. It uses Android's explicit playback-capture consent and deliberately omits notification-listener access so direct installs are not classified as sensitive notification-access sideloads. The `full` source variant retains the experimental artwork-color integration but is not published as a GitHub APK.

## Signing

The workflow requires `ANDROID_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` repository secrets. Signing material and recovery information remain private and outside the repository.

## Updater safety contract

LumaBeat accepts metadata and APK assets only from exact HTTPS release paths under `thomrnowtea/lumabeat`. Before opening Android's installer it verifies metadata identity, SHA-256, package name, version code, version name, and signing-certificate lineage. The system's unknown-source permission and final installation confirmation remain mandatory.
