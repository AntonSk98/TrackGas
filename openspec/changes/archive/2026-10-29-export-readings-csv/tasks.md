# Tasks

## 1. CSV serialization

- [x] 1.1 Implement a platform-independent serializer for the frozen tracker snapshot with the specified header, UTF-8 semicolon CSV escaping, exact decimals, installation IDs, and chronological ordering; verify unit tests cover an empty tracker, multiple installations, same-day endpoints, decimals, and special characters.
- [x] 1.2 Verify serialization is independent of device locale and excludes calculated consumption using focused unit tests.

## 2. Export flow

- [x] 2.1 Add a retained single-request export state that captures the accepted committed tracker when requested, rejects duplicate requests, and associates picker/write completion with the request; verify view-model tests cover snapshot stability across subsequent edits or purge and lifecycle recreation.
- [x] 2.2 Integrate Android's CSV create-document picker and off-main-thread URI writer, reporting success only after close and handling cancellation and expected open/write/close failures without exposing private data; verify focused tests with successful, cancelled, and failing destinations.
- [x] 2.3 Add the accessible, localized Export CSV action as the first Tracker options item on both tabs with a visible export icon, enable it only for ready data and when appropriate, and present completion or retryable failure feedback; verify UI tests cover menu order and icon on both tabs, loading/error, empty data, cancellation, failure, and unchanged navigation.
- [x] 2.4 Verify activity recreation during picker and writing neither duplicates the request nor presents completion twice, and that a lost process request does not report a false success, using lifecycle/instrumented tests.
- [x] 2.5 Document the action, exact CSV format and examples, installation IDs, replacement endpoints, privacy and possible partial-file failures, and the absence of import or full restore; verify the README matches exported output.

## 3. Integration

- [x] 3.1 Run the relevant existing unit and Android instrumentation tests and verify an exported file from multiple installations matches the specified rows without modifying the tracker.

## 4. UUID identities and revised export

- [x] 4.1 Persist a UUID for the first installation on fresh installs and create a fresh UUID on purge; cover persistence, uniqueness, and rollback.
- [x] 4.2 Export `meter_id;date;meter_value` and present dismissible success feedback with an icon and no confirmation button; update documentation and verify CSV and UI behavior.
