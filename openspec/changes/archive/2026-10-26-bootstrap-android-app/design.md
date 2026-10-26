# Design

## Context

The repository has OpenSpec configuration and a minimal README but no Android application or Gradle build. The revised first step is only to establish a native app that starts. See proposal.md for scope.

## Goals / Non-Goals

**Goals:** Establish a reproducible native Android build and connect its launcher activity to the UI framework with the fewest moving parts.

**Non-Goals:** No server, data layer, repository, domain model, ViewModel, navigation, database, or gas-tracking functionality. Do not scaffold unused abstractions for future features.

## Decisions

### Single native Android module

Use one `app` module with Kotlin, Kotlin Gradle DSL, a committed Gradle wrapper, and pinned compatible Android Gradle Plugin, Gradle, Kotlin, Compose, and SDK versions. Start with namespace/application ID `com.trackgas.app` and minimum SDK 26 as documented initial assumptions. A multi-module architecture adds no value for a single starter screen.

The native foundation consists of the Android manifest, launcher activity, resources, and build configuration. "Backend" does not mean a separate service or data layer in this step.

### Static Compose screen

Use the launcher activity to render one Jetpack Compose screen with a Material 3 dark theme and a visible "TrackGas" title. No navigation or interactive product controls are needed. Compose with Material 3 establishes the requested UI framework without implementing a dashboard.

Include only startup dependencies. Do not add Room, networking, location, security, analytics, chart libraries, or runtime permission requests.

### Build and launch acceptance

Build the debug APK using `.\gradlew.bat :app:assembleDebug`, then install and launch it on an emulator or device. Confirm that the title appears without a crash, including with connectivity disabled. This build-and-launch smoke check is sufficient for the static skeleton; do not introduce domain tests or an extensive test suite for nonexistent functionality.

Document the actual chosen JDK/SDK requirements, Android Studio import/run steps, Windows PowerShell build/install/launch commands, and generated APK location in README. Dependency and SDK downloads can require connectivity; the installed app cannot. Keep machine-specific SDK paths, build outputs, and secrets out of version control.

## Risks / Trade-offs

- [Android SDK or emulator is unavailable] -> Document exact prerequisites and report launch validation as blocked rather than treating APK assembly alone as proof that the app starts.
- [Toolchain versions are incompatible] -> Pin a supported combination and confirm it with the debug build.
- [Future features require architecture] -> Add those layers when their behavior is specified, not as speculative setup work.
- [API 26 excludes older devices] -> State the baseline in README; broader compatibility can be considered separately.
