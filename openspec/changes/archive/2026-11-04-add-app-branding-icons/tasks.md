# Tasks

## 1. Launcher branding

- [x] 1.1 Create a compact teal gas-tracking vector mark and adaptive-icon foreground/background resources; verify the foreground remains legible within the launcher safe area at small preview sizes.
- [x] 1.2 Register the adaptive icon in `AndroidManifest.xml`; verify Android resource processing succeeds with `.\gradlew.bat :app:processDebugResources`.

## 2. In-app title branding

- [x] 2.1 Display the shared brand vector beside the existing app name in the toolbar title, keeping it decorative to accessibility services; verify the app compiles with `.\gradlew.bat :app:assembleDebug`.

## 3. CSV action icons

- [x] 3.1 Update the CSV vectors so export points up/out of its tray and import points down/into its tray; verify both icons remain distinguishable at menu size and the Android build succeeds.
