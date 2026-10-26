# Tasks

## 1. Native Android and UI framework setup

- [x] 1.1 Create a single-module Kotlin Android project with a Gradle wrapper, pinned compatible toolchain, `com.trackgas.app` application ID, minimum SDK 26, launcher manifest/activity, and ignore rules for generated and machine-local files; verify Gradle configures the app module and generated/local files remain untracked.
- [x] 1.2 Wire Jetpack Compose and Material 3 into the launcher activity with a static dark screen displaying "TrackGas"; verify `.\gradlew.bat :app:assembleDebug` produces the debug APK without adding data layers, business logic, feature controls, or unnecessary dependencies.
- [x] 1.3 Document the actual JDK/SDK prerequisites, supported Android baseline, Android Studio startup steps, Windows PowerShell build/install/launch commands, and APK location in README; verify the instructions match the delivered project and clearly state that this is only an app skeleton with no backend service or tracking features.

## 2. Startup acceptance

- [x] 2.1 Follow the documented commands to build, install, and launch on an Android emulator or device; verify "TrackGas" appears on a dark screen without a crash, sign-in, or permission prompts even with connectivity disabled. If SDK/device prerequisites are unavailable, report the blocker and leave launch acceptance incomplete.
