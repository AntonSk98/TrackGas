# Proposal

## Why

TrackGas currently has no Android application. Establish the smallest native Android foundation and confirm that it builds and launches before adding any application features.

## What Changes

- Bootstrap a native Kotlin Android app with Jetpack Compose and a minimalist Material 3 dark theme.
- Wire the native Android launcher activity to one static Compose starter screen displaying the app name.
- Verify that the app builds, installs, and launches on an Android emulator or device.
- Document prerequisites and Android Studio/Windows command-line build and startup instructions.
- Treat "native backend setup" as the Kotlin Android application/build foundation only: no server, repository, data model, ViewModel, or business logic is needed yet.
- Defer all gas-tracking features, in-memory data, Room persistence, authentication, location/weather, notes, charts, and import to later changes.

## Capabilities

### New Capabilities

- `app-shell`: Offline Android launch to a minimal dark starter screen.

### Modified Capabilities

None; no existing capability specifications are present.

## Impact

- Adds the Android Gradle project, wrapper, app module, launcher activity, and static Compose UI.
- Expands the root README with reproducible setup and startup instructions.
- Introduces only dependencies needed for Kotlin Android and Compose/Material 3 startup; no data-layer, network, location, security, or chart dependencies.
- Requires a JDK and Android SDK for development, plus an emulator or device to launch; first-time build dependency downloads need connectivity, but the installed app does not.
- Makes no promise about AI development spend or future third-party API availability. No hosted service or runtime API cost is introduced.
