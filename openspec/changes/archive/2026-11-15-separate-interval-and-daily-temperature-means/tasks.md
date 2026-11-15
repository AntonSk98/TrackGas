# Tasks

## 1. Daily weather source and persistence

- [x] 1.1 Add a provider operation for full location-local calendar-day means while preserving the existing hourly interval API; verify daily parsing, missing values, and date handling in `TemperatureProviderTest`.
- [x] 1.2 Add the date-keyed daily-mean Room entity and repository operations, register the additive `6 -> 7` migration, and export the new schema; verify migration retains readings and interval means and creates an empty daily cache.

## 2. Background synchronization and aggregation

- [x] 2.1 Extend temperature checks to fetch only missing daily means for covered dates alongside missing interval means; verify asynchronous fetching, batching, offline retry, location changes, and obsolete responses in `TemperatureSyncTest`.
- [x] 2.2 Preserve daily rows for dates still covered and prune dates no longer covered transactionally on reading edits, insertions, deletions, imports, and purge; verify retained, removed, and newly requested dates in repository/sync tests.
- [x] 2.3 Add distinct date-keyed daily projection and covered-date aggregation without changing interval projection; verify missing-date handling, period weighting, and location filtering in `TemperatureAggregationTest`.

## 3. Mode-aware Dashboard presentation

- [x] 3.1 Expose interval and daily temperature maps separately and select one consistently for the chart, period mean, rows, and inspection; verify both modes and unchanged gas totals in chart projection and ViewModel tests.
- [x] 3.2 Add concise English/German temperature-basis labels and associate the selected label accessibly with the temperature presentation; verify resource parity and mode-specific wording in `LocalizedStringResourcesTest` and applicable UI tests.

## 4. Integration verification

- [x] 4.1 Run the focused temperature/provider/sync/aggregation/chart tests and `./gradlew testDebugUnitTest`; resolve failures without changing interval-temperature or gas-accounting semantics.
- [x] 4.2 Run `./gradlew assembleDebug` and `openspec validate separate-interval-and-daily-temperature-means --strict`; verify the version 7 schema and all required artifacts are valid.
