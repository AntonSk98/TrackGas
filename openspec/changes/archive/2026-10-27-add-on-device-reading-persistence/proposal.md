# Proposal

## Why

Meter readings currently disappear when the app process ends, preventing users from building a useful consumption history. The tracker needs durable, private on-device storage without introducing a cloud service or entangling the UI with database details.

## What Changes

- Persist actual readings locally across process death and app restarts, including successful additions, edits, and confirmed deletions.
- Restore history and its derived dashboard from the same authoritative stored readings; retain existing validation, identity, precision, and session navigation behavior.
- Show loading and storage failures honestly instead of presenting uncommitted changes or an empty tracker as success.
- Replace the temporary-storage disclosure with accurate local-storage messaging. Preserve the existing no-backup configuration.
- Keep cloud sync, accounts, import/export, persistence of tab or period selections, and migration of already-lost in-memory readings out of scope.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `gas-consumption-tracking`: Replace transient readings with durable local readings and specify restoration and failure behavior.
- `app-shell`: Replace the temporary-reading disclosure with accurate on-device storage messaging.

## Impact

The Android app's tracking state holder, reading forms and history actions, startup wiring, resources, tests, and Gradle configuration will change. A local Room database and repository boundary are proposed; no backend, account, network access, or cloud backup is introduced.
