# TrackGas

TrackGas is an offline Android app for recording cumulative gas-meter readings
and viewing consumption over time. It is built with Kotlin, Jetpack Compose, and
Material 3.

## Features

- Record, edit, and delete dated meter readings.
- View consumption by week, month, or year; browse history separately.
- Replace a meter with a same-day zero baseline for the new meter.
- Export and import readings as CSV.
- Store data locally on the device; no account or server is required.

## Meter readings and consumption

Readings are cumulative values in cubic metres. Consumption is calculated from
consecutive readings for the same meter; values from different meters are never
subtracted. Gaps between readings are distributed evenly across covered days,
and usage is not extrapolated beyond the last reading.

To replace a meter, first save its final reading for today. Confirm **Replace
meter** to start the next meter at zero on the same date. Replacement cannot be
undone through the Replace meter action.

## CSV import and export

Choose **Tracker options > Export CSV** or **Import CSV** on either tab. The
UTF-8, semicolon-delimited file has this header:

```csv
meter_id;date;meter_value
11111111-1111-4111-8111-111111111111;2026-10-27;1099.500
11111111-1111-4111-8111-111111111111;2026-10-28;1105.000
22222222-2222-4222-8222-222222222222;2026-10-28;0
22222222-2222-4222-8222-222222222222;2026-10-29;3.250
```

Dates use `YYYY-MM-DD`; values use a dot decimal separator and preserve their
precision. `meter_id` is the installation UUID, not a displayed meter number.
Fields containing semicolons, quotes, or line breaks are quoted.

Import accepts only UUIDs already in the tracker. It replaces the complete
history for each UUID in the file, recreating that meter under the same UUID;
meters omitted from the file keep their readings. Replacement relationships
involving imported meters are cleared, so CSV is not a complete tracker backup.
Unknown UUIDs reject the whole import. Duplicate rows for one UUID and date use
the last row in file order. Meter values must remain chronological and
nondecreasing, and usage intervals across meters must not overlap. Review the
short warning and confirm to apply the import atomically.

The current meter is the one with the latest reading date. If dates tie, the
installation with the later start date is current.

## Storage and privacy

Readings and meter history are stored in an app-private Room database. Data is
available offline and is not backed up to the cloud. Uninstalling the app or
clearing its data removes the tracker. CSV files saved outside the app-private
directory may be accessible to other apps or people, depending on their location.

## Build and test

Requires Android Studio, the Android SDK (API 35 / Build Tools 36.0.0), and a
JDK. On this project setup, use Android Studio's bundled JBR:

```powershell
$env:JAVA_HOME = Join-Path $env:USERPROFILE "android studio\jbr"
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA "Android\Sdk"
.\gradlew.bat :app:assembleDebug
```

Run JVM tests with:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Instrumented tests require a connected Android device or emulator:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Project structure

- `app/src/main/java`: Compose UI, ViewModel, tracker policies, and Room repository.
- `app/src/test`: local JVM tests for parsing, validation, and calculations.
- `app/src/androidTest`: Room, migration, Activity, and Compose integration tests.

`MainActivity` hosts the app and delegates document-picker handling to
`CsvDocumentPickerCoordinator`. `TrackerViewModel` owns screen/import state;
`ReadingRepository` and `RoomReadingRepository` provide committed snapshots and
atomic mutations.
