# Tasks

## 1. Reading model and validation

- [x] 1.1 Add the feature-local immutable reading model with stable identity, LocalDate, BigDecimal cubic metres, and injectable Clock; add minimal JUnit support to `app\build.gradle.kts` and verify a model test runs through `.\gradlew.bat :app:testDebugUnitTest --tests "com.trackgas.app.*"`.
- [x] 1.2 Implement fully validated decimal parsing and candidate-collection validation for Add/Edit; add tests for empty/malformed/negative input, locale decimal separators, future/duplicate dates, equal readings, edit self-exclusion, and conflicts with either chronological neighbour; verify invalid candidates leave saved data unchanged.

## 2. Consumption and calendar calculations

- [x] 2.1 Derive chronological source-linked reading intervals and exact deltas using the `(earlier date, later date]` convention; verify unit tests for no readings, baseline-only, consecutive-day usage, equal readings, unordered entry, and inserted middle readings.
- [x] 2.2 Implement uniform skipped-day overlap allocation without synthetic notes or extrapolation, preserving measured interval totals and estimated provenance; verify tests for 12 cubic metres over 3 days, unknown outer dates, long gaps, and 1 cubic metre over 3 days without summing rounded daily displays.
- [x] 2.3 Implement current-week/month/year ranges, type switching to today's period, previous/next navigation, and daily/monthly buckets; verify fixed-clock tests for Monday/Sunday boundaries, month lengths, leap years, year transitions, and calendar-day behavior around daylight-saving changes.
- [x] 2.4 Aggregate from all source intervals into selected buckets and period totals with explicit coverage and estimate metadata; verify tests for known zero versus unknown, partially covered weeks, January 30 to March 1 producing 28 cubic metres in February 2026, and consistent yearly totals.
- [x] 2.5 Document the allocation convention, partial-coverage meaning, and measured-versus-estimated distinction in README; verify the examples agree with the calculation tests and preserve the existing APK build instructions.

## 3. In-memory lifecycle state

- [x] 3.1 Add an activity-scoped feature ViewModel with immutable observable state, synchronous Add/Edit/Delete operations, and selected period; declare only the direct lifecycle dependency needed; verify state tests cover stable IDs, newest-first history, atomic invalid updates, and selected-period preservation.
- [x] 3.2 Wire successful mutations to recalculated dashboard data; verify state tests cover editing a date/value, inserting/deleting a middle reading, deleting down to a baseline or empty tracker, and unchanged source readings after projection.
- [x] 3.3 Keep readings exclusively in ViewModel memory without saved-state serialization or disk storage; document rotation survival and process-death loss in README and verify a fresh state holder starts empty on the current week.

## 4. Dashboard and reading workflow

- [x] 4.1 Replace the starter title-only body with a Material 3 Scaffold and dark dashboard, accessible top-right add icon, selected period controls, explicit range, and previous/next actions; verify the current week is selected on launch and all three calendar views navigate as specified.
- [x] 4.2 Render accessible daily rows for weekly/monthly views and monthly rows for yearly views with coverage, known zero, unknown markers, and estimate explanations; verify empty/baseline-only/uncovered periods display no consumption series or total and partial periods never claim full coverage.
- [x] 4.3 Build one reusable Material add/edit form with today's default, prefilled edits, date picker, decimal field, inline errors, Save, and Cancel; add JVM tests for date-picker UTC/LocalDate round-trips and verify valid saves refresh the selected dashboard while cancellation and invalid input do not alter readings.
- [x] 4.4 Render complete newest-first history independently of the selected dashboard period and wire Edit and confirmed Delete; verify off-period notes remain manageable, cancelled deletion preserves data, and deleting the final usable interval empties consumption content.
- [x] 4.5 Put labels, errors, date/number formatting, unit text, icon descriptions, and the temporary-storage notice in Android resources using existing Material styling; add a README acceptance checklist covering offline launch, form workflows, locale/timezone behavior, rotation, process restart, keyboard/insets, scrolling, large text, and labelled controls; verify no unfinished price/weather actions or new runtime permissions are introduced.

## 5. Integrated acceptance

- [x] 5.1 Run the feature JVM tests together with `.\gradlew.bat :app:testDebugUnitTest --tests "com.trackgas.app.*"` and build the existing debug APK with `.\gradlew.bat :app:assembleDebug`, using the README's Java/SDK environment setup as needed; verify both commands complete and the APK exists at the documented output path.
- [x] 5.2 Exercise the README acceptance checklist on an available Android device/emulator using empty, consecutive-day, skipped-day, cross-month, invalid-edit, and deletion flows; verify the offline dashboard, rotation retention, fresh-process reset, labelled controls, large text, and keyboard/inset usability end to end, recording any device-access blocker instead of claiming unperformed checks. TalkBack and screen-reader acceptance are excluded.

### Device acceptance status (2026-10-26)

Exercised on the TrackGas_API_35 emulator through ADB/UI Automator: offline
launch; weekly/monthly/yearly navigation; empty and baseline-only states; known
zero and skipped-day estimates; cross-month February and yearly totals; invalid
input, duplicate dates and neighbour-conflicting edits; successful date/value
edits; cancellation; off-period history; confirmed deletion down to empty;
rotation retention; fresh-process reset; German decimal comma/dot and localized
dates; UTC date-picker round-trips in western/eastern timezones; scrolling,
labelled controls, large text, and landscape keyboard workflows. The keyboard
exercise exposed a form-inset issue, fixed with a safe-inset Material dialog and
scrollable body; save/recalculation and large-text/landscape flows were repeated
after the fix.

**Scope revision:** the user excluded TalkBack and screen-reader acceptance from
this change. No screen-reader checks are claimed as performed or required.
Task 5.2 is complete based on the emulator flows recorded above; ordinary
labelled controls, large text, and keyboard/inset usability remain in scope.

## 6. Dashboard usability refinement

- [x] 6.1 Project only covered consumption buckets for display, preserving estimated allocations, known zero, exact totals, and complete-period coverage counts; update JVM tests for compact weekly/monthly/yearly lists, empty periods, cross-month allocation, and unchanged source readings.
- [x] 6.2 Add Dashboard/History tab selection to the feature ViewModel with Dashboard as the fresh-process default; verify switching tabs preserves the selected period and successful/invalid mutations preserve the selected tab and refresh derived data correctly.
- [x] 6.3 Render labelled Material Dashboard and History tabs; keep period controls, compact coverage summaries, and covered consumption rows exclusively on Dashboard, and move all newest-first readings and Edit/Delete actions exclusively to History; keep Add available from either tab and preserve existing form, confirmation, inset, and scrolling behavior.
- [x] 6.4 Apply the user's further presentation simplification: show only dates/months, cubic-metre values, and a simple Consumption total; remove all measured/estimated labels, allocation explanations, and coverage counts from the dashboard while preserving internal metadata and calculation behavior. This supersedes the coverage-summary presentation in task 6.3.
- [x] 6.5 Update the README acceptance checklist and usage explanation for tabs, omitted Unknown rows, and hidden calculation metadata; run feature JVM tests and build the debug APK, then exercise the two-reading monthly/yearly views, absence of estimation/coverage text, known zero, tab navigation, Add/Edit/Delete, period preservation, and rotation on an available device/emulator. TalkBack and screen-reader acceptance remain excluded.

### Refinement acceptance status (2026-10-26)

Feature JVM tests and the debug APK build completed for the revised presentation.
On TrackGas_API_35, two October readings produced exactly three daily rows in
Monthly and one October row in Yearly. Neither view displayed Unknown rows,
coverage counts, distribution labels, or allocation explanations. Add worked
from both tabs; History contained all readings and Edit/Delete without period
controls or consumption. Rotation retained History and its readings. Invalid
edits and cancelled deletion preserved data; a valid date/value edit refreshed
the selected period and preserved the active tab. Known zero remained visible,
deleting down to a baseline removed consumption, and a fresh process reset to
Dashboard on the current week. TalkBack was not exercised or required.

## 7. Maintainability improvements

- [x] 7.1 Resolve the default current date dynamically after device-timezone changes while retaining deterministic date/Clock injection; centralize date-based period normalization and remove the unused ReadingDefaults factory. Add regression tests for runtime timezone changes and preserve identity/date precision behavior.
- [x] 7.2 Enforce reading, reading-interval, and calendar-period construction invariants and explicitly reject duplicate source identities and invalid allocation interval ordering/overlap; cover invalid construction and unchanged valid arithmetic with focused JVM tests.
- [x] 7.3 Make TrackerScreen consume state and callbacks rather than a ViewModel; extract focused dashboard/history rendering and shared pure formatting files, update MainActivity wiring, and retain KDoc and existing UI/reading behavior without adding frameworks.
- [x] 7.4 Update implementation documentation, run the feature JVM tests and debug APK build, exercise the refactored tabs/reading workflow and live timezone date behavior on an available emulator, then synchronize this change's delta specs into main specs without archiving.

### Maintenance acceptance status (2026-10-26)

All 32 feature JVM tests and the debug APK build completed. On TrackGas_API_35,
Add from both tabs, newest-first History, monthly/yearly totals, hidden metadata,
Edit, known zero, cancelled/confirmed deletion, period/tab preservation, rotation,
and fresh-process reset were exercised after the UI decomposition. A live switch
from Pacific/Kiritimati (October 27) to Pacific/Honolulu (October 26) changed Add's
default date and rejected an existing October 27 reading on Save as future-dated,
without restarting the process or losing readings. Switching back allowed the
edit. The emulator timezone was restored and our emulator stopped.

Synchronized both app-shell requirements into its existing main spec, preserving
its Purpose, and created the gas-consumption-tracking main spec with all 12
requirements and the delta's Purpose. Main-spec and active-change validation
completed; the existing long app-shell interface description produces a
non-blocking length warning. The change remains active and unarchived.
