# Spec Delta

## REMOVED Requirements

### Requirement: One transient local tracker
**Reason**: Readings must survive process termination rather than remain in memory only.
**Migration**: Replace with the durable local tracker requirement; old in-memory readings cannot be recovered after the previous process has ended.

## ADDED Requirements

### Requirement: One durable local tracker
The app SHALL store one tracker of actual readings on the device without an account, server, network access, or cloud backup. Successfully saved readings SHALL survive process death and app restart. Tab and period selections SHALL continue to use their existing fresh-process defaults.

#### Scenario: Restore in a fresh process
- **WHEN** the user saves several readings, terminates the app process, and opens the app again
- **THEN** History contains the same actual readings with their identities, dates, and exact decimal values, and Dashboard recalculates from them with the current week and Dashboard selected

#### Scenario: Rotate the device
- **WHEN** readings have been added and the device rotates
- **THEN** the same readings and derived consumption remain available

#### Scenario: Restore changes
- **WHEN** the user successfully edits one reading and confirms deletion of another, then restarts the app
- **THEN** the edited value remains associated with its original date and identity, and the deleted reading remains absent

#### Scenario: Offline use
- **WHEN** the user records readings and reopens the app without connectivity
- **THEN** the readings and their consumption are available without signing in or contacting a server

### Requirement: Reliable local reading operations
The app SHALL distinguish loading, successful storage, and storage failure. It SHALL acknowledge a mutation only after it commits and SHALL preserve the last committed data if that mutation fails. The user SHALL be able to retry a failed load or mutation without losing a draft.

#### Scenario: Loading readings
- **WHEN** stored readings have not yet loaded
- **THEN** the app indicates loading, does not present an empty tracker as authoritative, and prevents reading mutations until the stored data is available

#### Scenario: Load failure
- **WHEN** stored readings cannot be read
- **THEN** the app displays a recoverable storage error instead of an empty history and provides a retry action without deleting the database

#### Scenario: Save failure
- **WHEN** a valid add or edit cannot be committed
- **THEN** the form stays open with its draft and a helpful storage error, and history and dashboard retain the last committed readings

#### Scenario: Delete failure
- **WHEN** a confirmed deletion cannot be committed
- **THEN** the reading remains in history, consumption remains unchanged, and the user sees an error and can retry

#### Scenario: Duplicate or overlapping operations
- **WHEN** a reading operation is in progress and the user attempts another mutation
- **THEN** the app prevents duplicate submissions and preserves date uniqueness and chronological validation
