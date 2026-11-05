# Tasks

## 1. Remove device-location feature

- [x] 1.1 Delete `LocationPermission*` sources, `LocationPermissionTest`, the `ACCESS_COARSE_LOCATION` manifest entry, and related en/de strings; verify the project compiles and `LocalizedStringResourcesTest` passes (`gradlew testDebugUnitTest` with the Android Studio JBR as `JAVA_HOME`)

## 2. Meter location

- [x] 2.1 Add `LocationResolver` interface, `OpenMeteoGeocoder` with a pure JSON parser, `MeterLocation`, and `MeterLocationStore`; add `INTERNET` permission; verify with unit tests for parsing (match, no match, malformed)
- [x] 2.2 Add the "Meter location" options-menu item and dialog (resolving/error/saved states, blank disabled, en/de strings) wired through `TrackerViewModel`; verify with ViewModel tests using a fake resolver (success, no match, failure keeps old location)
- [x] 2.3 Open a location dialog with a Later action after a committed add when no location is saved and the device is online (via `NetworkMonitor`, task 4.2 provides the real one; use a fake here); verify with ViewModel tests (opens when none saved and online, not when saved, not when offline, Later/back/outside close it and it reopens on the next add, reading still saved)

## 3. Temperature storage

- [x] 3.1 Add `daily_temperatures` entity, `TemperatureDao`, `TemperatureRepository` (observe, storedDates, upsert), Room v3 with `MIGRATION_2_3`, and exported schema; verify `kspDebugKotlin` exports the schema and the project builds
- [x] 3.2 Prune out-of-range temperatures inside the existing delete, edit, import, and purge transactions in `RoomReadingRepository`; verify with tests for deleting the latest reading, deleting a middle reading, import, and purge

## 4. Batched asynchronous sync

- [x] 4.1 Implement pure `missingRanges` (contiguous batches, â‰¤ 366-day chunks) and `OpenMeteoTemperatureProvider` with a pure JSON parser (null days skipped); verify with unit tests for gaps, partial coverage, 150-reading backfill span, nothing missing, null values
- [x] 4.2 Add `NetworkMonitor` with `ConnectivityNetworkMonitor` and `ACCESS_NETWORK_STATE`; implement `TemperatureSync` (conflated event flow, IO listener, offline/no-location/<2-readings guards, silent provider failures, upsert); verify with coroutine tests using fakes (single batched request for a gap, only missing days, offline makes no request, reconnect triggers fetch, provider error does not fail)
- [x] 4.3 Emit `requestCheck()` from save, delete, import, location save, network available, and app start; verify the ViewModel tests assert emission and that saves complete without waiting on the check

## 5. Presentation

- [x] 5.1 Add `TemperatureAggregation` (mean ignoring gaps) and collect `TemperatureRepository.observe()` into `TrackerState` so charts refresh automatically; verify with tests for weekly, monthly, and Year means and that a new temperature emission updates state without resetting period or tab
- [x] 5.2 Extend `ChartProjection` with the temperature series (daily; monthly mean for Year; gaps preserved) and add the Vico line layer with Â°C end axis and tooltip temperature; verify with `ChartProjectionTest` additions (alignment, gaps, bars unchanged)
- [x] 5.3 Show the period mean below the chart and the mean in each row in `DashboardContent` (en/de, locale formatting); verify with unit tests (4, 6, 8 â†’ 6; hidden with no data; row equals tooltip)

## 6. Integration

- [x] 6.1 Run `gradlew testDebugUnitTest assembleDebug` and `openspec validate add-average-temperature --strict`; verify both succeed
