# TrackGas

Native offline Android gas tracker using Kotlin and Jetpack Compose. Record
cumulative meter readings in cubic metres and inspect weekly, monthly, or yearly
consumption in a light Material 3 dashboard with warm off-white backgrounds,
white cards, teal accents, and consistent forms and controls.

## Dashboard and History

The app opens on **Dashboard**. Choose Weekly, Monthly, or Yearly and use
Previous/Next to browse calendar periods. Weekly and monthly views show only
covered days; yearly shows only covered months. Estimated usage between readings
and known zero usage remain visible. Uncovered dates are omitted rather than
listed as Unknown. A bar chart sits above the numeric list: daily bars for Weekly
and Monthly, monthly bars for Yearly. Dates retain their calendar positions,
uncovered dates have no marks, and known zero usage has a baseline marker.
Charts fit the selected period without panning or zooming; sparse date ticks keep
them readable on phones, while every covered value remains in the list below.
The Consumption total is calculated independently, not from rounded chart/list
values. No coverage counts or estimation labels/explanations are displayed.

Open the **History** tab to see all actual readings, newest first, and edit or
delete them. History is independent of the dashboard period and does not show
consumption rows. Add is available on either tab. Switching tabs preserves the
selected period, and saving or deleting a reading keeps the current tab selected.
Tap the labelled tabs or swipe horizontally to change pages, including across
the chart (left to History, right to Dashboard in left-to-right layouts).
Only a long press activates chart inspection and captures subsequent drags.
Each page keeps its own scroll position. Vertical gestures scroll the current
page. Weekly/Monthly/Yearly scrolls with the Dashboard heading and consumption,
rather than staying pinned beneath the navigation tabs. History has no period
controls. The tab indicator follows the swipe continuously, and tab taps highlight
their destination immediately rather than waiting for the page to settle.
Both pages display a compact temporary-reading notice near the top.

Chart ticks and consumption rows use weekday names in Weekly, day numbers in
Monthly, and month names in Yearly. The period heading provides the year once
(both years when a week crosses New Year), keeping repeated dates out of the
chart and list. History and reading forms still show full dates.
Long-press a bar to inspect its weekday, two-digit monthly day (such as `04`), or
yearly month, plus the same localized consumption value as its row. Keep holding
and slide left or right to inspect other bars without leaving Dashboard.
The tooltip stays at the chart top, away from your finger even for short bars
and known-zero marks. Releasing dismisses it.

Add defaults and future-date validation use the current device date, including
timezone changes while the app remains open. Selecting a period type also uses
that current date; existing readings and manual navigation are not changed automatically.
Tap a History entry to edit its meter value; its original date is read-only in a
field labelled **Date**. Long-press an entry to request deletion, then confirm or
cancel. There are no separate Edit/Delete icons; both entry actions have accessible
labels identifying the reading. Add uses the same outlined **Date** field; tap it
to select a date. Tap outside
the Add/Edit form, use Back, or choose Cancel to discard an unsaved draft.
Only the date picker reports an existing reading, immediately during date
selection. Opening Add/Edit itself shows no duplicate warning. Choose an
available date to clear the error; editing a reading on its own date is allowed.
Save still validates the complete draft and opens the picker if its date is a
duplicate, without saving or losing your input.

Weekly/Monthly/Yearly stays above the consumption card. Previous/next arrows and
the centred date range sit directly below the chart inside that card, before the
numeric list. Empty periods keep the same navigation below their message.

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
`TrackerScreen` coordinates the Foundation pager, tabs, and shared dialogs using state and callbacks supplied by
`MainActivity`; `DashboardContent`, `ReadingHistory`, and `ReadingForm` handle the
focused UI. `ReadingFormat` shares pure locale formatting. Reading, interval,
coverage, and period models enforce their invariants; validation and consumption
calculations remain independently testable without Android UI. `ui/theme` owns
the light Material colors, typography, and shapes. The chart's pure projection
keeps original decimal values and converts only rendering coordinates; the
isolated Vico renderer has no role in consumption arithmetic.

## Device acceptance checklist

Use a disposable session: force-stopping the app intentionally destroys readings.
These are manual checks, not claims of completed device verification.
Verify meaningful screen-reader labels for actions and readable dates/values in
the numeric list; no essential value requires interpreting or touching the chart.

- [ ] Launch offline/airplane mode: light TrackGas dashboard, current local
  Monday-to-Sunday week, Dashboard/History tabs, labelled top-right Add, no
  permissions or sign-in. Dashboard has no reading-history list or Edit/Delete.
- [ ] Switch to History: all readings are newest first with Edit/Delete, without
  period controls or consumption rows. Add works from either tab. Switch back:
  the previously selected period remains selected.
- [ ] Mix tab taps with ordinary left/right swipes, including across the chart. Try rapid,
  reversed, cancelled/partial and outward boundary swipes: the settled page and
  selected tab always agree without changing data or the selected period. During
  the drag, the indicator tracks the finger; tapping highlights the destination
  before the page animation completes, without a second settling animation.
- [ ] Scroll each page to a different position and switch back and forth: each
  list resumes independently. Vertical gestures scroll, not switch pages.
- [ ] Scroll far down Dashboard: Weekly/Monthly/Yearly scrolls away, leaving only
  the shared toolbar and tabs fixed. Scroll back: selection remains unchanged
  and the controls are actionable. At 200% fonts they wrap and remain usable;
  History never shows them.
- [ ] Switch Weekly/Monthly/Yearly: each selects today's period. Previous/Next
  move one week/month/year and the period heading matches. Check weekday/day/
  month labels with the year only in the heading, including both years for a
  week crossing New Year. History and form dates retain their full context.
- [ ] Long-press bars in Weekly/Monthly/Yearly: the tooltip shows the matching
  weekday/two-digit day/month and row value in m³, including known zero.
  Release to dismiss; changing period/readings must not show a stale tooltip.
  Keep holding and drag across bars: the tooltip updates without opening History.
  Quick taps show no tooltip. Ordinary chart swipes still switch pages, and
  ordinary vertical chart drags still scroll.
- [ ] Without force-stopping, change the device timezone across a date boundary:
  a newly opened Add defaults to the new local date, future-date validation uses
  it, and selecting a period type selects its containing period. Existing
  readings and tab selection remain intact.
- [ ] Empty tracker and one baseline: no consumption total or series. Navigate to
  an uncovered period after adding more notes: still no invented totals.
- [ ] Add defaults to today; choose an earlier date, save, and cancel another
  draft. Only the saved note appears. Add a consecutive-day equal reading:
  known zero remains visible while uncovered neighbouring days are omitted.
- [ ] Enter an unsaved Add/Edit draft and tap outside its surface: it closes
  without saving. Taps inside the surface/input do not close it. Dismissing the
  nested date picker returns to the unchanged draft.
- [ ] Choose or enter an already recorded date in the picker: the duplicate
  message appears immediately and confirmation is disabled. Choose a valid date:
  the message clears. Add on an already recorded day shows no warning until the
  picker opens; editing a reading on its own date remains valid. Save on a
  duplicate default opens the picker without saving. Cancel the picker: the draft
  is retained and no duplicate warning appears in the parent form.
- [ ] Enter 1000 on October 1, 2026 and 1012 on October 4, 2026 (when both dates
  are in the past): October 2–4 show 4 m³ each, with a Consumption total of 12 m³.
  No estimation labels, explanations, or coverage counts are displayed.
  Nothing is extrapolated after October 4.
- [ ] With those two readings, Monthly displays exactly October 2, 3, and 4;
  Yearly displays only October with 12 m³. No Unknown rows or zero-filled dates
  appear, and no estimation/coverage text is shown. The chart matches these rows,
  places bars at their actual calendar positions, and starts its value scale at zero.
- [ ] Known-zero-only coverage displays baseline marks and zero rows, not positive
  bars or an empty state; uncovered dates have no marks. Navigate to an uncovered
  period and delete down to a baseline: no stale chart or invented total remains.
- [ ] A fully covered 31-day month fits all marks without horizontal chart scrolling.
  Tick labels can be sparse; every covered date/value remains available below.
- [ ] In a fresh session, enter 1000 on January 30, 2026 and 1030 on March 1:
  February has 28 m³ despite no February note.
  Yearly monthly rows total 30 m³, with no uncovered-month rows or metadata labels.
- [ ] Reject empty/malformed/negative values, future dates and duplicate dates.
  Edit a middle note below its predecessor or above its successor: inline error
  and no changes to history or totals. Equal values remain valid.
- [ ] Tap an entry and edit its decimal value: the read-only Date stays unchanged,
  identity is retained, adjacent intervals recalculate, and the selected period and History tab stay
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
  Save is the primary action; Cancel does not mutate data. Scroll through a full
  month and lengthy history.
- [ ] At 360 dp width with 200% font scale, labels/values wrap or reflow without
  losing information, period selection remains clear, and all actions remain
  reachable. Check wide screens also retain a centred, bounded reading width.
- [ ] Add/Previous/Next and History entry edit/delete actions expose meaningful accessibility labels;
  controls have at least 48 dp touch targets. Normal text has at least 4.5:1
  contrast (large text 3:1), and selection/errors are not communicated by color alone.

### Current implementation acceptance

On the API 35 emulator, exercised offline fresh launch, date entry, baseline-only
and populated states, daily/monthly bars, a full 31-day chart, genuine-zero marks,
uncovered periods, editing and confirmed/cancelled deletion, raw precision,
German locale formatting, mixed/rapid/cancelled/boundary navigation, chart-originated
swipes, rotation on History, and independent page scroll restoration. Also exercised
360 dp width at 200% font scale and landscape keyboard entry with scroll-reachable
fields and actions. The all-zero axis crash found during this pass is fixed.

The feedback revision also exercised the warm light palette, live swipe
indicator and rapid/cancelled tab changes, the earlier pinned-control revision at 200% fonts,
compact English/German dates, a week crossing New Year, and bar inspection in
all three period types, including known-zero tooltips and release dismissal.
That earlier revision allowed chart-originated page swipes; the final inspection
behavior below supersedes it.

An earlier interaction revision exercised normally scrolling period controls,
accessible pencil/trash actions at normal and 200% font sizes, outside dismissal
of unsaved Add/Edit drafts, nested picker cancellation, and compact landscape
keyboard actions. Duplicate-date feedback was exercised before Save in both
calendar and text-input modes, including correction and editing on the original
date. Tooltips were checked at the chart top for tall, short, and zero bars.

The preceding interaction pass confirmed picker-only duplicate feedback, rejection
of duplicate saves without losing the draft, and a clean Add form before and
after picker cancellation. Left/right chart drags updated the selected date and
consumption without leaving Dashboard; outside-chart swipes and tabs still
navigated, and vertical chart drags still scrolled the list. That immediate-drag
behavior is superseded by long-press-only inspection: ordinary chart swipes
navigate again.

A focused follow-up confirmed long-press tooltip activation and dragging without
page navigation, followed by ordinary chart swipes navigating again after release.

A focused History follow-up confirmed icon-free entries, tap-to-edit with a
read-only Date field, value-only saves retaining the original date, and long-press
deletion with both cancellation and confirmation. Add date selection remained usable.

On 2026-10-27, the user confirmed completing full acceptance and signed off the
remaining manual acceptance task (OpenSpec 5.2). This user-reported sign-off closes
the previously outstanding device checks; it is separate from the emulator work
recorded above. The checklist remains reusable for future regression passes.
