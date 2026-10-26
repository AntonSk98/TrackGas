# TrackGas

Native offline Android gas tracker using Kotlin and Jetpack Compose. Record
cumulative meter readings in cubic metres and inspect weekly, monthly, or yearly
consumption in a dark dashboard.

## Dashboard and History

The app opens on **Dashboard**. Choose Weekly, Monthly, or Yearly and use
Previous/Next to browse calendar periods. Weekly and monthly views show only
covered days; yearly shows only covered months. Estimated usage between readings
and known zero usage remain visible. Uncovered dates are omitted rather than
listed as Unknown. Consumption content shows only dates/months, m³ values, and
a simple total, without coverage counts or estimation labels/explanations.

Open the **History** tab to see all actual readings, newest first, and edit or
delete them. History is independent of the dashboard period and does not show
consumption rows. Add is available on either tab. Switching tabs preserves the
selected period, and saving or deleting a reading keeps the current tab selected.

Add defaults and future-date validation use the current device date, including
timezone changes while the app remains open. Selecting a period type also uses
that current date; existing readings and manual navigation are not changed automatically.

## Temporary readings

This is one local, **in-memory-only** tracker. Readings, selected tab, and period
survive recomposition and screen rotation through an activity-scoped ViewModel.
They are not written to saved state, a file, preferences, or a database. Ending
the app process loses all readings; a fresh process opens empty on Dashboard for
the current Monday-to-Sunday week. This is not durable meter-record storage.

## How consumption is calculated

Readings are paired chronologically, regardless of entry order. An interval covers
the days **after** its first reading through its last reading, inclusive: `(A, B]`.
A single reading establishes only a baseline. Days outside measured intervals are
unknown, not zero, and an uncovered period has no consumption series or total.

The difference between two meter values is the measured interval total. For gaps,
that total is distributed uniformly across calendar days; this daily distribution
is **estimated**, not a measurement of actual daily variation. For example, 1000
on October 1 and 1012 on October 4 allocate an estimated 4 m³ to October 2, 3, and 4.
No reading or usage is invented for October 5.

Totals use overlap with all original intervals, including readings outside the
selected period. Readings of 1000 on January 30, 2026 and 1030 on March 1 allocate
28 m³ to February. Period totals include consumption for covered days only;
uncovered days are not counted as zero or extrapolated. Coverage and estimate
metadata are retained internally, not displayed on the dashboard.
Equal meter values establish known
zero usage. Only display values are rounded (at most three decimal places);
1 m³ across three days remains exactly 1 m³ for the whole interval, not 0.999 m³
from adding rounded daily displays. Raw meter precision and original notes remain
unchanged.

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
or server is needed. There is no database, price, or weather integration.

## Development checks

From PowerShell in the repository folder:

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\temurin-26.0.1"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:JAVA_OPTS = $env:JAVA_OPTS -replace '%JAVA_OPTS%', ''
.\gradlew.bat :app:testDebugUnitTest --tests "com.trackgas.app.*"
.\gradlew.bat :app:assembleDebug
```

## Code structure

`TrackerViewModel` owns the in-memory snapshot and validates mutations.
`TrackerScreen` coordinates tabs and dialogs using state and callbacks supplied by
`MainActivity`; `DashboardContent`, `ReadingHistory`, and `ReadingForm` handle the
focused UI. `ReadingFormat` shares pure locale formatting. Reading, interval,
coverage, and period models enforce their invariants; validation and consumption
calculations remain independently testable without Android UI.

## Device acceptance checklist

Use a disposable session: force-stopping the app intentionally destroys readings.
These are manual checks, not claims of completed device verification.
TalkBack and screen-reader acceptance are not required for this change.

- [ ] Launch offline/airplane mode: dark TrackGas dashboard, current local
  Monday-to-Sunday week, Dashboard/History tabs, labelled top-right Add, no
  permissions or sign-in. Dashboard has no reading-history list or Edit/Delete.
- [ ] Switch to History: all readings are newest first with Edit/Delete, without
  period controls or consumption rows. Add works from either tab. Switch back:
  the previously selected period remains selected.
- [ ] Switch Weekly/Monthly/Yearly: each selects today's period. Previous/Next
  move one week/month/year and the explicit range matches.
- [ ] Without force-stopping, change the device timezone across a date boundary:
  a newly opened Add defaults to the new local date, future-date validation uses
  it, and selecting a period type selects its containing period. Existing
  readings and tab selection remain intact.
- [ ] Empty tracker and one baseline: no consumption total or series. Navigate to
  an uncovered period after adding more notes: still no invented totals.
- [ ] Add defaults to today; choose an earlier date, save, and cancel another
  draft. Only the saved note appears. Add a consecutive-day equal reading:
  known zero remains visible while uncovered neighbouring days are omitted.
- [ ] Enter 1000 on October 1, 2026 and 1012 on October 4, 2026 (when both dates
  are in the past): October 2–4 show 4 m³ each, with a Consumption total of 12 m³.
  No estimation labels, explanations, or coverage counts are displayed.
  Nothing is extrapolated after October 4.
- [ ] With those two readings, Monthly displays exactly October 2, 3, and 4;
  Yearly displays only October with 12 m³. No Unknown rows or zero-filled dates
  appear, and no estimation/coverage text is shown.
- [ ] In a fresh session, enter 1000 on January 30, 2026 and 1030 on March 1:
  February has 28 m³ despite no February note.
  Yearly monthly rows total 30 m³, with no uncovered-month rows or metadata labels.
- [ ] Reject empty/malformed/negative values, future dates and duplicate dates.
  Edit a middle note below its predecessor or above its successor: inline error
  and no changes to history or totals. Equal values remain valid.
- [ ] Edit a note's date and decimal value: identity is retained, history reorders,
  adjacent intervals recalculate, and the selected period and History tab stay
  unchanged. Return to Dashboard to see the updated covered consumption rows.
- [ ] Navigate away from the notes' dates: all notes remain manageable in History.
  Cancel Delete, then confirm Delete. Remove down to one/zero notes and check
  that consumption becomes empty while any baseline remains manageable.
- [ ] Test decimal comma and decimal point in a comma-decimal locale, raw meter
  precision, localized number/date labels, and a timezone west/east of UTC.
  Date picker saves the exact selected date, including daylight-saving dates.
- [ ] Rotate after adding notes and navigating on each tab: readings, totals,
  period, and selected tab remain. Force-stop and relaunch: empty tracker on
  Dashboard for the current week.
- [ ] Use the keyboard and landscape mode: form fields, inline errors, Save/Cancel
  and date selection remain reachable; bars/keyboard do not obscure controls.
  Scroll through a full month and lengthy history.
- [ ] Increase system font/display size: period selection remains clear,
  Add/Edit/Delete have labels, rows show dates and values,
  and controls remain readable and usable without clipping.
