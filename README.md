# TrackGas

**Make every meter reading count.** TrackGas helps you follow gas use over
time, compare it with local weather, and keep a clear history—all without an
account.

[Download the latest APK](https://github.com/AntonSk98/TrackGas/releases) and
install it on Android 8.0 or later.

## Start tracking

Add a reading in cubic metres, then add another later to see your consumption.
Set your location to compare usage with outdoor temperatures. You can also add
notes, set reminders, and replace a meter without losing its history.

TrackGas works in English and German, and readings can be recorded offline.
Charts use weather data from [Open-Meteo](https://open-meteo.com/); recent
temperatures may take a few days to arrive.

## Your data, your device

Readings are stored locally—there's no account or cloud backup. Export CSV
files from **Tracker options > Export CSV**. Importing replaces history for
meters in the file; CSV is not a full backup.

> **Coming from the older date-only version?** Back up any readings you need:
> this version requires a fresh install or cleared app data, and old CSV files
> cannot be imported.

## Build

Open the project in Android Studio with Android SDK 37 and Build Tools 36.0.0,
or run `./gradlew :app:assembleDebug`. For release publishing, see
[DEPLOYMENT.md](DEPLOYMENT.md).
