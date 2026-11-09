# TrackGas

**Understand your gas use, one reading at a time.**

TrackGas is a simple Android app for logging gas-meter readings, following your
consumption, and comparing it with outdoor temperatures. No account needed.

## What you can do

- **Track your consumption** with weekly, monthly, and yearly charts.
- **Compare gas use with the weather** using temperatures for the time between readings.
- **Keep a reading history** with the date, time, and optional notes.
- **Get reminders** to record your meter reading.
- **Replace your meter** without losing the old meter's history.
- **Export readings to CSV** for your own records or a spreadsheet.
- **Use English or German**, and record readings even without an internet connection.

## Get started

Download an APK from [GitHub Releases](https://github.com/AntonSk98/TrackGas/releases)
and install it on an Android device running Android 8.0 or later.

1. Tap **Add** and enter the number shown on your gas meter, in cubic metres.
2. Select when you read the meter using the date and time pickers.
3. Add a second reading on a later day to see your consumption.
4. Set your meter's location to include outdoor temperatures in the charts.

You can record one reading per meter per day. Use **History** to correct a value
or time, add a note, or delete a reading.

> **Updating from the older date-only version?** This version requires a fresh
> installation or clearing the app's data. Keep a copy of any readings you need
> before doing so. Old CSV files cannot be imported into this version.

## Understanding the charts

Consumption is the difference between consecutive readings. If you skip days,
the app spreads that consumption evenly across the days between readings.

Temperatures come from [Open-Meteo](https://open-meteo.com/) and represent the
average weather between your reading times. For readings several days apart,
the same average is shown across those days, so daily comparisons are approximate.
Recent temperatures may take a few days to become available. You can keep logging
readings while the app waits for weather data.

## Your data stays on your device

Readings are stored locally, with no account or cloud backup. An internet
connection is only needed for location search and weather updates; these use
your selected location, not your meter readings.

**Uninstalling the app or clearing its data deletes your history.** Exported CSV
files remain wherever you saved them, so keep them somewhere private.

## CSV import and export

Open **Tracker options > Export CSV** to save your readings and notes.

**Import CSV** replaces the history of the meters included in the file after
you confirm. Other meters are left unchanged. Only meters already known to the
app can be imported, so **CSV is not a full backup and cannot restore a tracker
after reinstalling**. Weather data is fetched again when needed.

<details>
<summary>CSV format reference</summary>

Files use UTF-8, semicolons between columns, and this exact header:

```csv
meter_id;timestamp;timezone;meter_value;note
11111111-1111-4111-8111-111111111111;2026-11-06T08:15+01:00;Europe/Berlin;123.450;
```

The columns contain the meter's installation ID, reading date/time with UTC
offset, recording timezone, cumulative value in cubic metres, and an optional
note. Values use a decimal point without thousands separators. Times use minute
precision, and the offset must match the timezone. Fields containing semicolons,
quotes, or line breaks use CSV quoting, with embedded quotes doubled.

For duplicate readings on the same meter and local date, the last row wins.
Old date-only CSV formats are not supported.

</details>

## Building from source

Open the project in Android Studio with Android SDK 35 and Build Tools 36.0.0.
With a JDK configured, build using `.\gradlew.bat :app:assembleDebug` or run local
tests using `.\gradlew.bat :app:testDebugUnitTest`.

For signing and publishing releases, see [DEPLOYMENT.md](DEPLOYMENT.md).
