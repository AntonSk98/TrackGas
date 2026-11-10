# Tasks

## 1. Pure daily-rate projection

- [x] 1.1 Add a typed daily-estimate/absolute chart mode and a pure period-bounded daily-rate projection using validated `readingIntervals(tracker)` and the existing `cubicMetresPer24Hours` property; verify in `ConsumptionTest` that 13 cubic metres over 39 hours produces 8 per day, fractional/short intervals and DST use actual seconds, and absolute allocations remain unchanged.
- [x] 1.2 Implement covered-date-weighted monthly daily-rate averages for Year, clipping intervals to the selected period before date expansion; verify in `ConsumptionTest` that three dates at 4 plus one at 8 produce 5, partial coverage excludes missing dates, and month/year boundaries include only the selected dates.
- [x] 1.3 Exclude opening contributions and cross-installation pairs from rates without dropping genuine zeros; add `ConsumptionTest` cases for empty/baseline data, opening-only usage, opening plus an old-meter interval on the same date, independent meters, and missing versus zero rates.

## 2. Mode-aware chart snapshot and presentation

- [x] 2.1 Update `ChartProjection.kt` to select absolute buckets or daily-rate values without mutating `Dashboard`, use value naming plus explicit mode metadata instead of treating rates as absolute cubic metres, and retain temperature aggregation from absolute covered buckets; verify both modes' decimal values, scales, date positions, unchanged totals/rows, and identical temperature points in `ChartProjectionTest`.
- [x] 2.2 Include mode in chart snapshot identity and update tightly coupled format/inspection helpers in `ConsumptionChart.kt` so axes, measurements, tooltips, and approximation/accessibility wording use the active units; verify in chart and format JVM tests that equal numeric values still create distinct mode snapshots, rounding uses original decimal values, and zero/underflow behavior remains correct.
- [x] 2.3 Preserve the snapshot-owned chart producer and long-press gesture behavior, clear inspection when mode changes, and permit an empty gas-rate projection without hiding surrounding controls or valid temperature content; verify the opening-only projection in `ChartProjectionTest` and review snapshot keys, reset paths, and no-rate rendering branches.

## 3. Session state and action wiring

- [x] 3.1 Add default-daily mode selection to `TrackerState` / `TrackerViewModel` as session-only presentation state with no repository or weather side effects; verify in `TrackerViewModelTest` that tab/period navigation, empty periods, data refresh, and retained ViewModel state preserve selection while a new ViewModel defaults to daily mode.
- [x] 3.2 Wire the mode and selection callback through `MainActivity.kt`, `TrackerScreen.kt`, `DashboardContent.kt`, and the chart, passing the committed tracker-derived rate projection alongside the unchanged absolute Dashboard; verify compilation and ViewModel tests for time-only edits, reading insert/delete/import refreshes, failed/cancelled edits, and preserved absolute amounts and mode.

## 4. Localized dashboard control and visual hierarchy

- [x] 4.1 Keep the large number absolute under "Total consumption" / "Gesamtverbrauch", add a separate "Daily consumption" / "Tagesverbrauch" or "Consumption" / "Verbrauch" chart heading with a compact Material 3 switch on the same row; verify localized labels/state, 48 dp touch targets, heading wrapping, reduced spacing, theme usage, and the absence of "24" in chart titles.
- [x] 4.2 Add English/German chart-specific daily axis units, approximation/accessibility strings, and unavailable-estimate text without changing History strings; keep daily tooltips as approximation sign, value, and cubic metres without a per-day suffix. Extend `LocalizedStringResourcesTest` and affected format/layout tests to verify resource parity, placeholders, active unit measurements, and unchanged aligned chart positions.
- [x] 4.3 Render the unavailable-estimate message and retain the selector, total, list, and navigation for opening-only coverage; preserve the existing no-consumption state when no absolute coverage exists. Verify projection/resource cases and review each composable branch for operable mode switching and no fabricated zero or rate.

## 5. Integration validation

- [x] 5.1 Run the existing targeted JVM coverage in one invocation: `.\gradlew.bat :app:testDebugUnitTest --tests "com.trackgas.app.ConsumptionTest" --tests "com.trackgas.app.DashboardTest" --tests "com.trackgas.app.ChartProjectionTest" --tests "com.trackgas.app.ChartLayoutPolicyTest" --tests "com.trackgas.app.ReadingFormatTest" --tests "com.trackgas.app.HistoryProjectionTest" --tests "com.trackgas.app.TrackerViewModelTest" --tests "com.trackgas.app.LocalizedStringResourcesTest"`; verify all selected cases pass and address failures caused by this change.
- [x] 5.2 Run `.\gradlew.bat :app:assembleDebug` and review the final diff against the delta specification; verify Compose/resource wiring compiles, period totals and list amounts remain absolute, titles contain no "24", and no persistence, CSV, weather, dependency, or unrelated README changes were introduced. Do not run emulator or connected-device tests.
