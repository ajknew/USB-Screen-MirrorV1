# USB Screen MirrorV1 (fresh project)

Native Android/Kotlin + Jetpack Compose Material 3 project. This repository was created as a new project and does not copy source files from the prior USB Screen Mirror app.

## Current implementation status

Implemented in this source tree:
- Android USB Host discovery and ADB-interface descriptor filtering.
- USB permission request using a package-scoped `PendingIntent`, explicit mutability for the system permission result, and a registered receiver that reads `EXTRA_PERMISSION_GRANTED` rather than guessing.
- Clear separation between USB detection, USB permission, ADB authentication, capture, decoding, and rendered-frame state. The UI deliberately does not report streaming as active when no frame has been received.
- Material 3 dark UI, Indonesian labels, quality presets/target settings, bitrate unit conversion, layer layout controls, reset actions, and persisted preferences.
- Incremental ADB packet framing/parser and unit tests for fragmented reads.
- GitHub Actions workflow for unit tests, debug APK, lint, and artifact upload.

**Important limitation:** the ADB RSA authentication handshake, ADB shell service session, CLIENT screen capture process, H.264 access-unit assembly/decoder/render path, and end-to-end USB mirroring are not yet implemented in this alpha source. The UI states this honestly. USB permission or finding an ADB interface alone is not mirroring. Do not treat this alpha as meeting the final mission criteria or install it as a finished product.

## Build in GitHub Actions

The provided workflow installs JDK 17, Gradle 8.9, Android platform 35 and build tools 35.0.0, runs unit tests, assembles `app-debug.apk`, runs lint, and uploads artifacts. Workflow file: `.github/workflows/android.yml`.

The execution environment used to create this project did not have Android SDK, Gradle, emulator, ADB, connected devices, or network DNS access. Therefore no local Android build, installation, UI robot test, or physical USB streaming test has been claimed as passed. GitHub Actions must execute the workflow before build success can be reported.

## Target devices and protocol assumptions

- HOST: Android phone with USB Host/OTG role.
- CLIENT: Android phone with USB debugging enabled and an ADB USB interface exposed over the cable.
- The Android system USB permission dialog and the CLIENT-side ADB RSA authorization are separate gates.
- Device support varies by OEM/OS, cable role, USB configuration and debugging policy.

## Project coordinates

- Application ID: `com.dolanan.usbscreenmirror`
- Version: `1.0.0-alpha1`
- SDK: compile/target 35, minimum 26
- AGP 8.7.3, Kotlin 2.0.21, JDK 17

## Tests

`ProtocolAndSettingsTest` tests incremental packet parsing, bitrate unit conversion and input range validation. `LaunchSmokeTest` is an instrumented activity-launch smoke test and requires an Android test device/emulator.
