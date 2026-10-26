# TrackGas

Native Android starter app using Kotlin and Jetpack Compose. Currently it shows
only a dark screen with the **TrackGas** title.

## Generate the APK

Open **Git Bash** in the repository folder and run:

```bash
export JAVA_HOME="$(cygpath -u "$USERPROFILE\.jdks\temurin-26.0.1")"
export ANDROID_HOME="$(cygpath -m "$LOCALAPPDATA\Android\Sdk")"
export JAVA_OPTS="${JAVA_OPTS//%JAVA_OPTS%/}"

bash ./gradlew :app:assembleDebug
```

Java 26 and the Android SDK (Platform 35, Build-Tools 36.0.0) are already installed
on the current machine. Adjust the paths if using another computer.
The first build may need Internet access to download dependencies.
Android Studio and an emulator are not required.

Your installable debug APK is:

**`app\build\outputs\apk\debug\app-debug.apk`**

## Install on your phone

1. Copy `app-debug.apk` to your phone.
2. Open it in the phone's file manager. If prompted, allow that file manager to
   **install unknown apps**.
3. Install it, then open **TrackGas**.

Requires Android 8.0 or newer. The app runs offline. No USB debugging, account,
or server is needed. Gas tracking and database features will be added later.
