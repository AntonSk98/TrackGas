# Design

## Context

See `proposal.md` for motivation and the two delta specs for behavior.

- `data\ReadingDatabase.kt` stores `readings`, `installations`, and `replacements` with restrictive foreign keys. Creation seeds installation `original`; deleting all rows without reseeding would violate `TrackerData.currentInstallation`.
- `data\RoomReadingRepository.kt` serializes mutations and fresh observation captures with one `Mutex`, validates full collections through `readRows()`, and issues revisions only after transactions return successfully.
- `tracking\TrackerViewModel.kt` accepts increasing committed revisions, projects both tabs together, and retains semantic dialogs in the activity's process-local session. `ReadingDialog.kt` scopes completion by session/dialog/request identity.
- `TrackerScreen.kt` already has an options menu shared by both tabs. `MainActivity.kt` binds its callbacks. Only `res\values\strings.xml` currently provides strings; there is no persisted preferences store to reset.
- Main specs explicitly limit the menu to Replace meter and list the exclusive dialog types. Both require delta modifications; ordinary boundary deletion protection and non-destructive load retry remain unchanged.

## Goals / Non-Goals

**Goals:** Extend existing boundaries rather than create a parallel reset subsystem; preserve transaction, revision, classification, and lifecycle invariants. Keep SQL in data storage, orchestration in the ViewModel, and presentation in a focused composable.

**Non-Goals:** Refactor all mutation handlers, introduce a settings framework or generic command engine, change consumption arithmetic, recover corrupt databases, or implement secure physical deletion of SQLite pages/WAL files.

## Decisions

### 1. Add one explicit repository operation

Add `suspend fun purge(): CommittedSnapshot` to `ReadingRepository` and `ReadingAction.PURGE` to failure context. A successful purge has only one outcome, so a separate sealed result hierarchy adds no value. Return a committed snapshot; expected failures continue to throw `ReadingStorageException`.

Document the operation briefly: destructive whole-tracker scope, valid empty installation, and commit-before-success. Implement it in every test double deliberately, without default interface no-ops that could falsely report success.

Alternative: Compose calls Room directly or a separate reset repository. Rejected because both bypass the existing shared ordering/revision boundary or duplicate it.

### 2. Delete and reseed in one existing transaction boundary

Implement `RoomReadingRepository.purge()` using `readingStorageOperation`, the existing `ordering.withLock`, and `database.withTransaction`, in that nesting order. Inside the transaction:

1. Call `readRows()` to retain existing persisted-collection validation before mutation.
2. Delete all replacements, then all readings, then all installations using explicit DAO queries.
3. Insert `InstallationEntity(ORIGINAL_INSTALLATION_ID)` with a null start date.
4. Read and validate the resulting tracker with `readRows()`.

Only after `withTransaction` returns, call the existing `snapshot()` under the same lock. Do not reset the revision counter, replace/close the shared database, disable foreign keys, loop through ordinary protected deletion, or clear application storage. The existing creation callback does not run after table deletion, so explicit reseeding is mandatory.

An empty tracker follows the same transaction and succeeds idempotently. Recreating the original installation also invalidates observations when reading rows were already empty. Purge removes everything committed before its transaction, not just records visible when the dialog opened; a valid mutation committed afterward is new data, not a failed reset.

Alternative: `clearAllTables()` or database-file deletion. Rejected because they obscure foreign-key order, reseeding, live observers, and atomic publication. SQL deletes and reseeding need no schema/version change.

### 3. Extend semantic dialog state without duplicating lifecycle logic

Add a purge confirmation variant to `ReadingDialog`, with `Ready`, `Pending(requestId)`, and `Failed(ReadingFailure)` phases. Extend `pending` and add a completion helper following `completeDelete`'s session/dialog/request checks.

Add small `openPurge()`, `purge()`, and matching completion functions in `TrackerViewModel`. Reuse `open()`, `ReadingRequest`, `viewModelScope`, `acceptCommittedSnapshot()`, and existing load guards. Purge is available whenever load is READY, including an empty tracker; other pending mutations block it.

On confirmation, mark pending synchronously before launching work. Accept only the repository's committed snapshot; never assign `TrackerData()` optimistically. Successful completion closes only its matching dialog; expected storage failures retain it with `ReadingAction.PURGE`. Catch only `ReadingStorageException`, preserving cancellation and defects.

Observation and mutation outcomes stay independent: a committed purge still closes successfully if load becomes ERROR, and load retry never resubmits purge. Preserve session identity and navigation; do not recreate the ViewModel to simulate a reset. Other sessions converge through existing observation, with stale target actions rejected by normal repository validation.

Alternative: local `rememberSaveable` confirmation/progress booleans. Rejected because they can restore completed work, conflict with other dialogs, or detach errors from requests.

### 4. Add a focused, fully wired confirmation UI

Add Purge data below Replace meter with a delete icon, using existing menu enablement and close-on-selection behavior. Add explicit open/confirm callbacks to `TrackerScreen` and bind them from `MainActivity`; production behavior must not depend on default no-op callbacks.

Use a small `PurgeTrackingDataDialog` composable rather than overloading the reading-specific deletion dialog. Reuse Material 3, existing theme, `ReadingLoadContent`, localized string resources, accessible live-region error feedback, and destructive action styling. Ensure warning content can scroll on compact/enlarged layouts.

Provide strings for the menu/title, irreversible full-tracker warning, Purge all data button, pending message, and generic purge failure. Explain that this resets tracking history, not physically replaces a meter. Cancel, Back, and outside taps dismiss only when not pending. No typed phrase, second confirmation, new settings screen, or success snackbar is necessary; the emptied tracker and closed dialog show completion.

Alternative: reuse the existing per-reading prompt verbatim. Rejected because its reading-specific date/value and protected-endpoint messaging misrepresent a whole-tracker operation.

### 5. Focused responsibilities and concise documentation

Prefer intention-revealing names and short functions with one responsibility; extract only meaningful transaction/state/presentation pieces. Do not impose an arbitrary line-count limit, introduce unnecessary interfaces, or perform unrelated SOLID refactors. Add brief KDoc/comments only for the public purge contract, foreign-key deletion/reseed invariant, or non-obvious ordering. Update README's options list and local-storage behavior succinctly.

## Risks / Trade-offs

- Accidental irreversible loss -> Separate explicit warning and destructive confirmation; opening/dismissing never writes.
- Missing original installation or restrictive foreign-key failure -> Ordered SQL deletes plus reseeding in one transaction, with injected-failure rollback tests.
- Old observations resurrect data -> Existing lock/revision domain and stale-snapshot rejection, covered by controlled delivery tests.
- Cross-session writes -> Transaction-order semantics are explicit; writes after purge may legitimately populate the tracker. Purge is not a permanent write lock.
- Corruption blocks reset -> Preserve current validation and READY-only UI contract; a corruption-recovery tool is outside this feature.
- Interface additions break test doubles -> Update all implementations and compile JVM and instrumentation sources, not only production.
- Logical deletion is not forensic erasure -> Do not promise unrecoverable on-disk overwriting; no backup/export or undo is added.

## Migration Plan

No schema migration, dependency, permission, or startup reset. Ship as an additional user-triggered operation against the existing database version. Existing installations and upgrades keep all records until explicit confirmation. Code rollback can remove the feature but cannot restore data already purged.

## Validation Approach

Use existing JUnit/coroutines, Room instrumentation, and Compose tooling. Extend nearby suites or add focused purge suites alongside them:

- Repository/ordering: multiple replacements, exact initial shape, empty reset, failure during each destructive stage/reseed, cancellation rollback, corrupt-data rejection, increasing revisions, delayed observation, and competing mutations.
- ViewModel: cancel/no write, READY gating, one pending submission, cross-action exclusion, classified failure/retry, matching completion identities, unchanged tab/period, stale observations, and independent load failure.
- Compose/lifecycle/persistence: both menu entry points and intact Replace/Add, destructive warning and cancellation routes, pending controls, accessible error/retry, compact/enlarged layout, rotation while pending, file-backed reopen, and adding a new baseline after reset.

Reference suites: `TrackerViewModelTest`, `RoomReadingRepositoryTest`, `OrderedRepositoryTest`, `ReadingPersistenceTest`, `MeterReplacementUiTest`, `TrackerScreenPersistenceUiTest`, and `TrackerLifecycleTest`. No new testing framework is needed.
