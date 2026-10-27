# Tasks

## 1. Local data layer

- [x] 1.1 Configure Room, compatible code generation, coroutine support, and schema export for the existing Gradle setup; verify the app compiles and the version-1 schema is generated.
- [x] 1.2 Implement the reading entity, DAO, app-private database, and lossless domain mapping with stable IDs, unique dates, and exact decimal strings; verify database tests cover date ordering, precision, and duplicate-date rejection.
- [x] 1.3 Add a domain-facing repository that loads/observes readings and performs add/edit/delete with validation and writes in a transaction; verify tests cover chronological neighbours, missing targets, rollback, and overlapping writes.
- [x] 1.4 Configure one database and repository instance and inject them through the activity's ViewModel factory; verify the app starts offline and no destructive migration or cloud backup is enabled.

## 2. State and user interaction

- [x] 2.1 Refactor the tracker state holder to load committed readings asynchronously and derive history and dashboard together while preserving tab/period semantics; verify unit tests cover loading, restart restoration, rotation, and projections.
- [x] 2.2 Implement serialized asynchronous save/delete outcomes with validation, pending state, and explicit storage errors; verify unit tests cover failed writes, retry, duplicate submission, missing edits/deletes, and unchanged committed snapshots.
- [x] 2.3 Update Compose forms, delete confirmation, and loading/error presentation to preserve drafts and allow retry without claiming success before commit; verify UI tests or reproducible device checks for loading, validation, failed save/delete, retry, and lifecycle changes during an operation.
- [x] 2.4 Replace temporary notices and associated resources/comments with accurate local-only, no-cloud-backup disclosure; verify both Dashboard and History display the updated localized text.

## 3. End-to-end verification

- [x] 3.1 Verify on a device or emulator that adding, editing, and deleting readings survive process termination and relaunch offline, preserving exact values and recalculated consumption while resetting to Dashboard/current week.
- [x] 3.2 Run the existing relevant unit tests and Android build plus Room database tests; verify existing reading validation, navigation, and dashboard behaviors still pass.
