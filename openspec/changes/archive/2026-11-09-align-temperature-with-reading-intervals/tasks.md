# Tasks

## 1. Timestamped reading model and storage

- [x] 1.1 Extend `Reading`, save intents, and validation with a minute-precision observation instant and recording timezone while retaining the recorded date and one-reading-per-date rule. Extend `ReadingTest`, `ReadingInputTest`, `ReadingValidationTest`, and `ReadingPolicyTest` for future instants, missing/invalid times, DST gaps/overlaps, unchanged imported overlap instants, and neighbour timestamp order; verify these cases with the existing JVM runner.
- [x] 1.2 Update `ReadingEntity`, entity mappings, collection integrity checks, and save transactions to persist and validate timestamp/timezone/date consistency. Extend `ReadingEntityMappingTest`, `ReadingCollectionTest`, and existing repository tests to cover round trips, malformed persisted timestamps, and atomic rejection; verify no date-only timestamp default is introduced.
- [x] 1.3 Bump the Room schema and generate its schema JSON through the existing Gradle/KSP build. Verify the fresh schema contains the timestamp fields and preserved installation/date uniqueness; retain unsupported-upgrade failure rather than adding migration or automatic destructive fallback.
- [x] 1.4 Document the fresh-install prerequisite and observation-time meaning in README. Verify the instructions clearly distinguish explicit reinstall/data clearing from automatic data deletion.

## 2. Reading entry and history

- [x] 2.1 Extend `ReadingDraft` restoration, `ReadingForm`, `ReadingFormLayout`, dialog state, and `TrackerViewModel` submission with the observation time and captured recording timezone. Verify through existing draft/ViewModel tests that defaults are captured once, rotation preserves time, Edit keeps date read-only, and time-only edits retain identity.
- [x] 2.2 Add localized English/German time controls and errors, and render recorded date/time in `ReadingHistory` without adding a separate history row. Verify time formatting, date/value/note layout, accessible labels, future-time feedback, and preserved timestamps after changing the device timezone using existing formatting tests and a focused UI walkthrough.

## 3. Timestamp CSV exchange

- [x] 3.1 Update the CSV writer/parser, import models, and candidate construction for exactly `meter_id;timestamp;timezone;meter_value;note`. Extend the existing CSV tests for offset/zone round trips, exact decimal precision, quoted notes, valid overlap offsets, future/invalid times, invalid zones, and rejection of both old headers; verify them with the existing JVM runner.
- [x] 3.2 Extend import error mappings/localized resources and preserve known-meter restrictions, per-recorded-date last-row-wins, preview atomicity, omitted histories, and export ordering. Verify CSV parser/writer/import tests include cross-timezone imports and offsets crossing UTC date boundaries.
- [x] 3.3 Replace README CSV examples and field explanations, including unsupported date-only files and exclusion of temperature caches. Verify examples use minute-resolution offset timestamps and valid matching timezone IDs.

## 4. Hourly fetch and in-memory mean

- [x] 4.1 Replace daily-provider responses with transient UTC hourly samples from the existing archive endpoint using `hourly=temperature_2m` and epoch timestamps. Extend `TemperatureProviderTest` for response alignment, nulls, invalid/duplicate timestamps, numeric precision, date bounds, and HTTP failures; verify parsing uses absolute instants.
- [x] 4.2 Implement equal-weight sample selection for `[start, end)` and complete-coverage validation, with no interpolation or partial-hour weighting. Extend focused temperature tests for the November 6/7 morning and evening examples, 08:15 endpoints, exclusion of the ending hour, DST 23/25-hour intervals, incomplete data, and a cross-midnight window without samples; verify expected counts and means.
- [x] 4.3 Bound long requests to 31 UTC-date chunks with an in-memory sum/count and coverage check, sequential execution, and no persisted partial results. Extend provider tests for chunk boundaries and mid-sequence failure; verify a complete interval produces one result and incomplete sequences produce none.
- [x] 4.4 Document hourly sample averaging, archive delays, and the lack of interpolation in README; verify the explanation does not promise immediate weather availability or exact continuous measurements.

## 5. Interval cache, targeted invalidation, and background refresh

- [x] 5.1 Replace `DailyTemperatureEntity`/daily DAO methods with one interval-mean entity keyed by endpoint pair and carrying timestamps and location provenance; update `TemperatureRepository` and the exported Room schema. Verify storage tests show one row for a completed 24-sample interval, no hourly table, and persistence across reopen.
- [x] 5.2 Replace date-range pruning in add/edit/delete/import/purge transactions with current-pair/timestamp invalidation. Extend repository tests for insertion splits, deletion merges, time edits, note/value-only preservation, omitted imported installations, replacement opening consumption, and purge; verify unrelated means survive and invalidated means disappear atomically.
- [x] 5.3 Add guarded result storage that rechecks endpoint existence, timestamps, installation, and adjacency in the write transaction. Extend repository/sync tests with controlled in-flight requests; verify edits, insertion, deletion, import, and purge cannot restore obsolete means.
- [x] 5.4 Route location saves and result publication through the shared short-lived coordinator in `TemperatureServices`; add generation checks, persisted location provenance filtering, and cache invalidation before refresh completion. Extend location/ViewModel/sync tests for in-flight location changes, A-to-B-to-A changes, save/clear failures, and restart between preference save and cache deletion; verify old-location means never become visible.
- [x] 5.5 Replace `TemperatureGaps` daily scanning with missing eligible interval lookup and adapt `TemperatureSync` plus application/ViewModel wiring. Extend `TemperatureSyncTest` for sequential missing-only requests, no cross-meter pairs, empty-sample windows, offline saves, connectivity return, startup retry, unavailable data, cancellation, and completed-cache reuse; verify reading mutations never wait for HTTP.

## 6. Interval temperature presentation

- [x] 6.1 Derive an in-memory daily projection from valid interval means and their existing covered gas dates; filter against the accepted tracker and saved location before publication. Extend `TemperatureAggregationTest` for stale snapshots, multi-day repeated means, unequal interval lengths, uncovered dates, missing means, and period boundaries; verify three dates at 4 plus one at 8 produce 5.
- [x] 6.2 Wire projected values into `TrackerViewModel`, `ChartProjection`, `DashboardContent`, and temperature inspection while preserving independent scales, missing-point line breaks, row aggregation, locale formatting, and selected tab/period. Extend `ChartProjectionTest` and relevant ViewModel/formatting tests; verify automatic appearance/removal without stale values or navigation resets.
- [x] 6.3 Retain gas allocation/replacement behavior and document the approximate multi-day chart projection in README. Run the existing `ConsumptionTest` and chart tests together with the related temperature tests; verify time-only edits change weather windows but not gas totals, and opening consumption does not create invented weather intervals.

## 7. End-to-end integration

Device-only acceptance (including the focused UI walkthrough in 2.2, persistence
across device reopen, and 7.2) is owned by the user. No emulator or instrumentation
tests are written or run for this change.

Local verification: all 206 JVM tests pass, including observation/DST/CSV,
controlled in-flight mutations and location changes, stale-observation filtering,
weighted projection, and unchanged gas allocation. The generated schema and actual
DAO pruning SQL were also checked using local SQLite for splits/merges, timestamp
edits, note/value preservation, omitted histories, rollback, purge, and file reopen.
That SQLite check is not Android/Room reopen acceptance. On 2026-11-09, the user
confirmed that manual device testing looked good and requested completion of
the remaining acceptance tasks 2.2, 5.1, and 7.2.
`:app:assembleDebug` also passes; KSP generated schema 6 and the debug APK.
Local commands used the already-installed Temurin JDK because this machine's
documented Android Studio JBR path is absent.

- [x] 7.1 Run the existing Gradle JVM runner with all affected reading, repository, CSV, temperature, chart, and ViewModel selectors in one invocation, then `.\gradlew.bat :app:assembleDebug` to verify Compose/resources/Room integration. Resolve only failures caused by this change and verify the exported schema matches the implementation.
- [x] 7.2 On a fresh installation, walk through morning/evening reading entry, a time-only edit, middle-reading insertion/deletion, offline retry, location change, CSV round trip, and restart. Verify one mean per interval, immediate reading commits, missing weather while pending, correct sample-window examples, and unchanged navigation/gas allocation.
