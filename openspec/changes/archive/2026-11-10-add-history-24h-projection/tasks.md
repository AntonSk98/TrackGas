# Tasks

## 1. Pure elapsed-time normalization

- [x] 1.1 Add a pure 24-hour amount property/helper for validated `ReadingInterval` in `Consumption.kt`, using absolute observation instants and BigDecimal DECIMAL128 arithmetic; add JVM cases for 39 hours (13 cubic metres becomes 8), 12 hours, exactly 24 hours, partial hours, zero, and non-terminating division, asserting retained precision.
- [x] 1.2 Cover DST spring/fall transitions and different recorded zones in JVM tests; verify normalization uses actual elapsed time and existing validation still rejects non-increasing instants rather than producing a fallback estimate.
- [x] 1.3 Add a small pure later-reading-ID projection based on `readingIntervals(tracker)` for History reuse; verify empty/baseline omission, replacement-start baseline omission, shuffled input, multiple installations, and absence of cross-meter estimates through JVM tests.
- [x] 1.4 Exercise projection recalculation after middle insertion/deletion and value/time changes; extend the existing time-only consumption regression to prove that History estimates change while calendar allocations and Dashboard totals do not.

## 2. Minimal localized History presentation

- [x] 2.1 Render eligible estimates in `ReadingHistory.kt` using the pure projection once per committed snapshot and each row's ID; place smaller secondary estimate text in the existing wrapping value row, omit baseline placeholders, and verify by source inspection that raw precision, meter identifiers, notes, stable keys, and edit/delete semantics remain intact.
- [x] 2.2 Add matching English/German visible estimate and accessible-description resources, reuse `numberText` for calculated rounding, and preserve the row's action semantics; add formatting assertions for both locales and verify resource key/placeholder parity with `LocalizedStringResourcesTest`.
- [x] 2.3 Verify the integration derives estimates from the full committed tracker rather than selected-period data, visible rows, save time, or mutable cached state; cover reconstructing the projection from imported/reloaded timestamped readings with existing JVM fixtures, and confirm failed/cancelled mutations retain the prior snapshot.

## 3. Integration verification

- [x] 3.1 Run the targeted JVM cases together with existing consumption, Dashboard, observation-time, formatting, and localization regressions using `.\gradlew.bat :app:testDebugUnitTest --tests "com.trackgas.app.ConsumptionTest" --tests "com.trackgas.app.DashboardTest" --tests "com.trackgas.app.ObservationTimeTest" --tests "com.trackgas.app.ReadingFormatTest" --tests "com.trackgas.app.LocalizedStringResourcesTest"`; include any new projection test class in that same invocation.
- [x] 3.2 Run `.\gradlew.bat :app:assembleDebug` to verify Compose and both resource sets compile; inspect the final diff to confirm there are no database, CSV format, Dashboard allocation, weather calculation, dependency, or unrelated README changes. Do not add or run emulator tests.
