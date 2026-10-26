# Design

## Context

See proposal.md for motivation and the capability deltas for behavior. The existing Android app has one ComponentActivity and a private Compose entry point in `app\src\main\java\com\trackgas\app\MainActivity.kt`. It renders only the title using Material 3's dark color scheme and edge-to-edge safe insets. The module targets API 35, supports API 26+, and already depends on activity-compose and Material 3. There are no source tests or explicit test dependencies.

The existing app-shell specification explicitly prohibits dashboard controls. The app-shell delta intentionally replaces that starter-only contract while retaining offline launch and the dark theme.

## Goals / Non-Goals

**Goals:**
- Keep meter input, interval calculation, and presentation separate enough for deterministic testing without introducing a backend or generic architecture framework.
- Retain original dated readings as authoritative data; regenerate allocations rather than storing synthetic readings.
- Reuse Material 3 controls and preserve Android lifecycle behavior without disk persistence.

**Non-Goals:**
- No account/user model, multiple trackers, database abstraction, remote service, dependency-injection framework, or navigation framework.
- No prices, tariffs, currency, energy-unit conversion, weather provider, location permissions, correlation calculation, or weather-weighted allocation.
- No meter replacement/reset handling, intra-day timestamps, or recovery after process death.
- No TalkBack-specific integration or screen-reader acceptance requirement. Ordinary labelled controls, scalable text, and keyboard/inset usability remain in scope.

## Decisions

### 1. Small feature boundary and lifecycle-owned in-memory state

Keep MainActivity responsible for Android setup/theme and host a gas-tracking screen. Introduce a small feature-local state holder using an activity-scoped ViewModel, an immutable reading model, and pure validation/calculation functions. The ViewModel owns saved readings, selected period, and selected Dashboard/History tab; composables receive state and event callbacks. Forms hold their own drafts and never mutate saved readings before Save succeeds.

Use stable IDs independent of dates, LocalDate for reading dates, and BigDecimal for cumulative cubic-metre values. Validation returns specific field errors and checks the candidate collection before publishing an update. Obtain today through an injectable date function that resolves the device's current timezone on every call; retain a Clock-based constructor for deterministic callers. Normalize periods through one date-based calendar factory. Remove the unused ReadingDefaults factory so new reading identities are generated only in the ViewModel's Add workflow. History is newest first; interval calculations sort independently in ascending date order.

Use the compatible AndroidX lifecycle ViewModel integration already available through the Android stack; declare a focused direct dependency if needed rather than relying on accidental transitive APIs. Do not use SavedStateHandle or saved-instance-state serialization for readings: state survives rotation through the ViewModel but intentionally disappears with process termination. Recompute the current default date when opening Add, rather than capturing installation/startup day.

Alternatives: composable `remember` alone loses readings when the activity is recreated; `rememberSaveable`, preferences, or a database would blur the memory-only contract. A repository interface and user IDs provide no value for one transient tracker.

### 2. Explicit date and validation policy

The user confirmed one reading per date, no future dates, cubic metres, non-negative values, and non-decreasing cumulative readings. Equal adjacent values are valid. For Edit, remove the original ID from the candidate collection before duplicate-date and neighbour validation, then replace atomically on success.

Support decimal entry without floating-point conversion, parsing the device locale's decimal separator (and ordinary decimal-point input) with full-input validation and no grouping separators. Display localized dates/numbers and put user-facing text, unit labels, errors, and accessibility descriptions in Android string resources. Keep raw meter precision; round only calculated display values, to at most three decimal places.

Enforce intrinsic domain invariants at construction: nonblank reading identities, non-negative meter values, chronologically increasing interval endpoints with non-decreasing values, and calendar-aligned period starts. Reject duplicate source identities and overlapping/out-of-order allocation intervals explicitly rather than producing inconsistent consumption. User-entered date/chronology errors still flow through field validation before domain construction.

Alternatives: accepting multiple same-day notes requires timestamps and changes the interval model; silently replacing duplicate dates risks data loss. Meter rollover/reset support requires an explicit meter lifecycle and is deferred rather than interpreting a negative difference as usage.

### 3. Interval-first consumption with a stated date convention

Sort actual readings, pair consecutive entries, and derive an interval with source reading IDs, earlier/later dates, elapsed calendar-day count, and exact meter delta. For readings on dates A and B, attribute usage to dates `(A, B]`: exclude A and include B. A single-day interval is a measured daily difference; longer intervals have measured totals but estimated day allocations.

Use elapsed LocalDate calendar days, not milliseconds divided by 24 hours, so daylight-saving transitions do not alter allocations. For a selected bucket, intersect its inclusive date range with the interval's covered dates. Its contribution is:

`interval delta * number of overlapping covered days / interval day count`

Aggregate directly from source intervals, not from displayed daily numbers. Preserve exact BigDecimal deltas and use high-precision division for partial fractions; when an entire interval is included use its exact delta without division. Round only when formatting. This prevents summing rounded thirds into an incorrect full-interval total.

Recalculate from all readings after each successful mutation; never filter readings to the selected period before forming intervals. That is necessary for a February allocation from January and March readings. Generate only the requested day's/month's buckets, using overlap counts rather than materializing every skipped day across the entire history.

Alternatives: assigning the entire difference to the latest reading misrepresents skipped days and calendar boundaries. Storing daily averages loses provenance and makes edits and later weather analysis unreliable. Extrapolation would invent consumption and is excluded.

### 4. Calendar selection and minimal consumption display

Open on the current Monday-to-Sunday week. Monthly and Yearly select today's calendar month/year when chosen; previous/next move by the selected calendar unit. Show the date range explicitly. Navigation may reach an uncovered period; that remains empty.

Weekly/monthly views use daily rows and yearly uses monthly rows, accompanied by a period total only when coverage exists. Simple labelled Material rows are sufficient for the first dashboard, avoiding a chart dependency or bespoke drawing code. Omit entirely uncovered buckets instead of rendering individual Unknown rows. Covered buckets remain visible even when their consumption is zero or their distribution is estimated. Missing dates remain unknown in the calculation model, never zero-filled or extrapolated. A fully uncovered dashboard has no consumption series or total; existing readings remain available in the History tab.

Track covered-day counts and whether a contribution originates from a multi-day interval separately from the numeric amount for calculation and testing only. Per the user's revised presentation decision, show dates, cubic-metre values, and a total titled "Consumption", without coverage counts, measured/estimated labels, or allocation explanations. Totals still sum covered dates only; hiding metadata must not invent usage on uncovered dates. A known zero remains visible. No sample data or loading placeholders are needed for local synchronous state.

Alternatives: zero-filling hides missing data; presenting partial totals as complete creates misleading comparisons. A custom chart or third-party graph is not needed to deliver an accessible minimal dashboard.

### 5. Reuse Material controls for one screen and one form

Use Scaffold/TopAppBar with an IconButton add action and labelled Dashboard/History tabs. Dashboard is selected initially and contains standard single-choice period controls, labelled previous/next buttons, a simple consumption total, and a LazyColumn of covered dates and values only. History has its own scrollable newest-first reading list with Edit/Delete actions and no consumption rows or period controls. Keep Add available from either tab. Reuse one add/edit form with an OutlinedTextField, Material date picker, Save/Cancel, and inline errors. Date pickers must round-trip LocalDate through the picker's UTC date representation without local-timezone day shifts. A Material AlertDialog confirms Delete.

Default Add to today; prefill Edit from the selected note. Keep period and tab selection after mutations, and preserve the selected period when switching tabs. Retain the selected tab through configuration changes alongside the existing ViewModel state; a fresh process opens Dashboard on the current week. Show all readings in History independently of period selection so baseline and out-of-period readings are manageable. Provide a short visible notice that readings are temporary and lost when the process ends. Preserve dark styling, safe system insets, keyboard usability, scrollability, scalable text, touch targets, and labelled icon actions.

Prefer existing Android/Compose icon resources; do not import a large extended icon pack just for Add/Edit/Delete. No new screens or routing layer are necessary.

Keep TrackerScreen as a navigation/dialog coordinator receiving TrackerState and event callbacks, not a ViewModel. MainActivity wires those callbacks to the activity-scoped state holder. Extract dashboard rows and reading history into focused UI files and locale formatting into a pure shared file used by both history and the form. Preserve the existing simple display, dialogs, and per-tab scroll state; add no DI, repository, or navigation framework.

### 6. Future weather correlation uses measured intervals, not smoothed days

Retain raw readings and derived interval boundaries/deltas independently from the daily presentation projection. That is the only preparation needed now. A future feature can aggregate actual weather (for example, heating degree-days) over the same intervals and compare it to measured interval consumption, acknowledging that date-only readings do not record exact collection times.

Do not correlate smoothed daily averages with daily temperature or treat an estimate as a new observation. Weather-weighted allocation, if introduced later, would be another explicitly estimated projection and require a separate change. No weather-specific model, interfaces, dependencies, or speculative services are introduced here.

Alternatives: pre-building weather integration enlarges scope without a chosen provider/model; discarding original readings would prevent reliable interval-based analysis later.

### 7. Focused validation close to each layer

Add local JVM tests for pure validation, interval arithmetic, calendar ranges, coverage, and mutation behavior under `app\src\test`. Since no test setup exists, add only the standard JUnit dependency needed by Android's Gradle unit-test task. Add lifecycle-test support only if required by the selected ViewModel implementation; prefer synchronous state transitions without introducing asynchronous machinery for in-memory work.

Pair UI work with an explicit device/manual acceptance checklist in README rather than introducing an emulator/test stack solely for this small feature. Exercise date-picker timezone conversion, form errors, cancellation, period controls, history actions, large text, and rotation. Run targeted Gradle unit tests for the feature and the existing debug APK build during implementation. Do not claim a manual check ran without an available device.

## Risks / Trade-offs

- [Process termination loses all readings] -> Make temporary storage visible in the app and README; do not present it as persistent tracking.
- [Daily interpolation hides true daily variation] -> Preserve measured-versus-estimated provenance internally, document uniform allocation in README, and never extrapolate. The user explicitly excludes estimation explanations and labels from the dashboard.
- [Date-only readings have unknown collection times] -> Define the end-date allocation convention explicitly; future weather analysis must acknowledge this limitation.
- [Cross-period intervals and rounding can distort totals] -> Aggregate overlap fractions from source intervals and test exact full-interval totals and partial boundaries.
- [Backdated edits can invalidate chronology] -> Validate both neighbours atomically before publishing any saved state.
- [Locale, timezone, and calendar edge cases] -> Use LocalDate and decimal arithmetic; test leap years, year boundaries, daylight-saving dates, and date-picker round-trips.
- [No existing automated test infrastructure] -> Add only minimal JVM test support and keep UI acceptance steps reproducible rather than adding a large tooling stack.

## Migration Plan

No persisted data or schema migration exists. Implement the feature in the current app module and build the normal debug APK. The initial launch remains empty. Rollback is a source/build rollback to the starter shell; there is no disk data to migrate or recover. Update README in the same implementation change to replace starter-only claims and explain memory-only behavior.
