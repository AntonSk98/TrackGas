# Design

## Context

See the proposal for motivation. `TrackerViewModel` currently owns a synchronous in-memory snapshot, validates against it, and returns immediate save/delete results to Compose dialogs. The dashboard is derived from actual `Reading` values, which use `LocalDate`, stable string IDs, and `BigDecimal`. The manifest already disables backup; the current specs explicitly require transient data and temporary notices.

## Goals / Non-Goals

**Goals:**
- Keep domain validation and consumption independent of Android storage APIs, with one authoritative source of committed readings.
- Preserve exact input precision, chronological invariants, existing session navigation, and responsive UI through asynchronous disk access.
- Make database and storage failures observable and testable without data loss.

**Non-Goals:**
- Persist UI drafts, selected period, tab, or derived consumption.
- Cloud synchronization, account management, import/export, encryption guarantees, or recovery after uninstall or data clearing.

## Decisions

### Room and a narrow repository boundary

Use Android Jetpack Room over an app-private SQLite database, with a reading entity, DAO, versioned database, and a repository interface expressed in domain types. Instantiate a single database/repository through explicit application-level wiring and a ViewModel factory; avoid introducing a dependency-injection framework for this small app. The application retains a separate ViewModel store per activity identity, preserving in-flight outcomes if it is destroyed without process death while isolating dialogs in separate activities; it releases stores when activities finish and prunes removed tasks when an activity starts. A fresh process starts new sessions. Room provides compile-time query checks, transactions, observable queries, and schema migration support. Raw SQLite would add boilerplate and risk, while DataStore is better suited to small settings than ordered, individually editable records.

### Store only lossless source readings

Store the ID as a primary key, calendar date as an ISO date string with a unique index, and the meter value as the exact `BigDecimal` string, mapping explicitly at the repository boundary. Never store decimal values as floating point or use a numeric SQLite affinity that may round them. Readings are ordered by date, and projections are rebuilt by existing domain code. Invalid stored representations must surface as a load failure rather than being silently dropped. Preserve the existing unique-date and nondecreasing-value rules.

### Commit and validate atomically

The repository exposes asynchronous observation/load and mutation operations, with domain validation against the current persisted snapshot and the write in one database transaction. A unique date constraint protects against duplicate dates; an edit/delete must check the affected identity and report a missing target rather than insert or silently succeed. Serialize mutation requests in the state holder and disable repeated submissions while pending; the database transaction is the final integrity boundary. Avoid optimistic success: only committed data is published, and history and dashboard are rebuilt from the same committed snapshot.

### Explicit UI states and error handling

Replace synchronous success-shaped callbacks with asynchronous operation state/results. Loading blocks reading actions until a real snapshot is available. Load errors expose retry without destroying storage; mutation errors leave committed state unchanged, keep an add/edit draft or delete confirmation open, and allow retry. Validation errors remain inline and localized; storage errors have distinct localized messages. Cancel and lifecycle handling must not misreport a committed write as failed or dismiss a form before confirmation. Preserve the selected tab and period during successful updates.

### Schema evolution and privacy

Export/version the initial Room schema and add explicit migrations for future versions; do not use destructive fallback. Keep `android:allowBackup="false"` and no network capability. This is app-private local persistence, not encryption or a guarantee against rooted devices, OS/device-vendor transfers, or user-initiated exports.

## Risks / Trade-offs

- [Asynchronous loading or writes can race validation and UI actions] -> Disable overlapping mutations and revalidate within the transaction; cover rapid retries and lifecycle transitions.
- [A database or mapping error could masquerade as an empty tracker] -> Separate empty success from failure; expose retry and never reset storage automatically.
- [No cloud backup means data is lost when the app is uninstalled or its data is cleared] -> Disclose local-only storage and do not imply recovery.
- [Room adds Gradle/compiler compatibility and schema-maintenance costs] -> Select compatible versions for the existing Android Gradle and Kotlin setup, verify the build, and check in the exported schema.

## Migration Plan

Install the new version with an initially empty version-1 database. There is no prior on-disk reading store to migrate; readings in an old process are not recoverable once that process stops. Future schema changes require non-destructive migrations. Rolling back to the old app restores transient behavior; it cannot read the new database and may not support Android version downgrades, so data preservation across rollback is not promised.
