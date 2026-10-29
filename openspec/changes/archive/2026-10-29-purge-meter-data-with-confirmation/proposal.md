# Proposal

## Why

Users can replace a meter or delete ordinary readings, but cannot deliberately start over without clearing Android app data. A confirmed purge should remove the complete tracking history safely, including protected replacement endpoints, and leave the app ready for a new first reading.

## What Changes

- Add **Purge data** beside Replace meter in Tracker options on both tabs.
- Require a separate confirmation explaining that all readings, meter installations, replacement history, and derived consumption will be removed permanently.
- Atomically reset the database to the valid initial tracker: no readings or replacements and one empty original installation.
- Reuse pending-action, classified-error, retry, session restoration, and ordered committed-snapshot behavior; never clear the UI optimistically.
- Preserve the selected tab and period during the current session. A fresh process retains existing Dashboard/current-week defaults.
- Keep implementation responsibilities separated, functions short and cohesive, and documentation limited to non-obvious contracts and invariants.
- Exclude selective/date-range deletion, undo, export/backup, preferences/settings work, automatic corruption recovery, schema changes, and forensic secure erasure.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `app-shell`: Extend the implemented options menu and provide an accessible, explicitly destructive purge confirmation.
- `gas-consumption-tracking`: Add an atomic full-tracker reset with committed-state publication and existing failure/lifecycle guarantees.

## Impact

- `ReadingRepository`, `RoomReadingRepository`, and `ReadingDao`: a typed purge operation, foreign-key-safe transactional deletion, and original-installation reseeding.
- `ReadingDialog`, `TrackerViewModel`, `TrackerScreen`, `MainActivity`, and string resources: confirmation state, callbacks, pending/error feedback, and production wiring.
- Existing JVM, Room instrumentation, Compose, persistence, and lifecycle tests, including repository test doubles.
- README usage and local-storage documentation. No new dependencies, permissions, accounts, network calls, database version, or migration.
