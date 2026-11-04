# Tasks

## 1. CSV Parsing and Validation

- [x] 1.1 Add a pure parser for the existing UTF-8 semicolon CSV format, including quoted fields and physical row tracking; verify it parses the supplied meter_readings.csv fixture and export-format edge cases.
- [x] 1.2 Add typed validation results and the specified stable error codes for invalid headers/rows, malformed or unknown meter UUIDs, invalid dates/values, negative values, tracker conflicts, and file/read failures; verify each code and row/field context with unit tests and confirm no throwable message is exposed.
- [x] 1.3 Deduplicate by meter UUID plus date using the last row in file order while retaining same-date rows for different meters; verify deterministic behavior with unit tests.

## 2. Repository Preview and Atomic Replacement

- [x] 2.1 Add repository import-preview behavior that accepts only known UUIDs, removes replacement relationships touching included meters, stages independent per-meter histories, preserves omitted readings, selects the latest-reading meter as current, and rejects decreasing histories or overlapping consumption coverage; verify with repository tests.
- [x] 2.2 Use a serialized Room transaction to remove affected replacement rows, recreate included installation rows with the same UUIDs, and replace their readings without synthetic boundary data; verify rollback, foreign keys, and omitted-meter preservation with database tests.
- [x] 2.3 Return one committed snapshot after import, derive current from latest reading date with installation start date as tie-breaker, and reject stale previews; verify revisions and stale-confirmation behavior with repository/ViewModel tests.

## 3. Import Flow and User Feedback

- [x] 3.1 Add an Import CSV option and Android document-picker flow; read selected content off the main thread and treat picker cancellation as a no-op, verified by instrumented picker-flow tests.
- [x] 3.2 Add explicit ViewModel import states for selection, preview, pending confirmation, completion, cancellation, and failure; verify duplicate submission is prevented and rotation does not repeat or lose an import using lifecycle/UI tests.
- [x] 3.3 Show a concise replacement warning without meter UUIDs or counts; use short localized errors with row/field context, without internal code names or exception text; verify preview, cancel, validation, and fallback states with Compose UI tests.
- [x] 3.4 Update README CSV documentation for known-meter-only imports, replacement-link removal, per-meter replacement, duplicate handling, and non-backup limitations; verify it agrees with the spec and CSV format.

## 4. Integration Verification

- [ ] 4.1 Run the full JVM unit-test suite and Android connected tests on the project emulator, then verify the debug APK builds with the documented Gradle tasks.
