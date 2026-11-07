# TrackGas

TrackGas is an offline Android app for recording cumulative gas-meter readings
and viewing consumption over time. It is built with Kotlin, Jetpack Compose, and
Material 3.

## Features

- Record, edit, and delete dated meter readings.
- View consumption by week, month, or year; browse history separately.
- Start an independent history when replacing a meter.
- Export and import readings as CSV.
- Store data locally on the device; no account or server is required.

## Meter readings and consumption

Readings are cumulative values in cubic metres. Consumption is calculated from
consecutive readings for the same meter; values from different meters are never
subtracted. Gaps between readings are distributed evenly across covered days,
and usage is not extrapolated beyond the last reading.

Confirm **Replace meter** to start a new, empty installation dated today.
Replacement does not require a final reading, add an automatic zero reading, or
link the old and new meter histories. Readings and consumption remain independent
for each installation. Replacement cannot be undone through the Replace meter
action.

The first reading for a replacement installation contributes its value to
consumption on the installation's start date. If the previous meter also has
consumption on that date, both amounts are included in that day's total. No
reading is added automatically; a first reading entered after the start date
remains a baseline until a later reading is recorded.

## CSV import and export

Choose **Tracker options > Export CSV** or **Import CSV** on either tab. The
UTF-8, semicolon-delimited file has this header:

```csv
meter_id;date;meter_value;note
11111111-1111-4111-8111-111111111111;2026-10-27;1099.500;
11111111-1111-4111-8111-111111111111;2026-10-28;1105.000;Meter cabinet replaced
22222222-2222-4222-8222-222222222222;2026-10-28;0;
22222222-2222-4222-8222-222222222222;2026-10-29;3.250;
```

The columns are the installation UUID (`meter_id`), reading date, cumulative
reading in cubic metres, and an optional note. Dates use `YYYY-MM-DD`; values
use a dot decimal separator and preserve their precision. Fields containing
semicolons, quotes, or line breaks are quoted, with embedded quotes doubled.
Import requires this four-column header; the previous three-column format is
not supported.

Import accepts only UUIDs already in the tracker. It replaces the complete
reading history, including notes, for each UUID in the file while preserving
that installation's identity; meters omitted from the file keep their readings.
Unknown UUIDs reject the whole import. Duplicate rows for one UUID and date use
the last row in file order. Meter values must remain chronological and
nondecreasing, and usage intervals across meters must not overlap. Review the
warning that included histories will be replaced and confirm to apply the
import atomically. CSV contains readings, not a complete restorable tracker
backup.

The current installation is the one with the most recent date among its start
date and latest reading; ties favor the later start date. Replacing a meter
starts a new independent history without adding a reading to either installation.

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

From Git Bash, use the Unix-style wrapper and paths:

```bash
export JAVA_HOME="$(cygpath -u "$USERPROFILE")/android studio/jbr"
export ANDROID_HOME="$(cygpath -u "$LOCALAPPDATA")/Android/Sdk"
./gradlew :app:assembleDebug
```

Run JVM tests from Git Bash with:

```bash
./gradlew :app:testDebugUnitTest
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Release deployment

See [DEPLOYMENT.md](DEPLOYMENT.md) for keystore setup and the release checklist.

## Project structure

- `app/src/main/java`: Compose UI, ViewModel, tracker policies, and Room repository.
- `app/src/test`: local JVM tests for parsing, validation, and calculations.

`MainActivity` hosts the app and delegates document-picker handling to
`CsvDocumentPickerCoordinator`. `TrackerViewModel` owns screen/import state;
`ReadingRepository` and `RoomReadingRepository` provide committed snapshots and
atomic mutations.
