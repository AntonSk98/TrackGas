# Proposal

## Why

`CLEAN_CODE_REPORT.md` identifies seven focused maintainability risks in TrackGas: oversized UI coordinators, distributed dialog state, mixed parsing/persistence policy, broad error translation, duplicate snapshot publication, custom lifecycle bookkeeping, and policy-heavy test doubles. Refactoring these boundaries now will make the existing offline tracker safer to maintain without a wholesale architecture rewrite or new product features.

The subsequent `CLEAN_CODE_POST_REFACTOR_REPORT.md` identifies three remaining follow-ups: persisted collection invariants are not checked before publication, repository ordering guarantees are absent from its API contract, and validation types allow contradictory outcomes or an Add without its required identity. Extend the existing refactoring to address these focused gaps without reopening the completed UI/lifecycle work.

## What Changes

- Give dialog targets and action phases one explicit owner; extract pager/tab navigation, load-state content, deletion confirmation, and form coordination/layout boundaries (findings 1–2).
- Separate locale-aware draft parsing from typed save policy; execute latest-snapshot policy and writes together inside Room transactions (finding 3).
- Centralize expected infrastructure and stored-data failure classification, retain privacy-safe categories, propagate cancellation, and stop disguising programming defects as retryable storage failures (finding 4).
- Funnel observation and committed mutation snapshots through one revision-aware, deduplicating publication path, without restarting healthy observation after each mutation (finding 5).
- Isolate the existing task-associated tracker-store owner and expose `ReadingRepository` at application composition, preserving retention across activity destruction within the same process (finding 6).
- Replace ViewModel policy reimplementations with scripted outcomes; exercise shared pure policy and real Room transactions separately (finding 7).
- Validate relationships within decoded stored reading collections at the repository boundary, reporting explicitly identified invalid stored data without wrapping unrelated programming defects (post-refactor finding 1).
- Document repository revision scope, capture/commit ordering, snapshot ownership, transactional validation, expected failures, and cancellation at the interface (post-refactor finding 2).
- Replace nullable candidate/error combinations with explicit accepted/rejected validation outcomes and require Add identity provision through a type-appropriate policy entry point (post-refactor finding 3).
- Preserve UI appearance/accessibility, navigation/scroll/period selection, exact decimal values and arithmetic, validation semantics, immutable edit dates, stored schema/data, atomic concurrent writes, offline/privacy behavior, rotation, fresh-session reset, and error/retry behavior.

This workflow creates planning Markdown only. Implementation, test/configuration edits, dependency changes, and apply are not authorized here.

## Capabilities

### New Capabilities

None. Architecture changes belong in the design, not a new user-facing capability.

### Modified Capabilities

- `app-shell`: make the existing pending-mutation exception to draft dismissal explicit and specify the documented process-local tracker retention contract.
- `gas-consumption-tracking`: specify target-bound dialog lifecycles, atomic typed-save behavior, ordered committed-state delivery, and distinguishable expected storage/data failures, including invalid relationships between individually valid persisted rows, without changing generic user messages.

These are testable reliability contracts within existing capabilities, not requirements invented for helper classes. Existing presentation, arithmetic, input rules, and persistence format remain authoritative.

## Impact

Future implementation affects `app\src\main\java\com\trackgas\app\tracking\TrackerScreen.kt`, `ReadingForm.kt`, `TrackerViewModel.kt`, `ReadingRepository.kt`, `ReadingValidation.kt`, `data\RoomReadingRepository.kt`, entity decoding in `data\ReadingDatabase.kt`, `TrackGasApplication.kt`, and `MainActivity.kt`, plus focused helpers, existing unit/instrumentation suites, and README architecture/retention documentation.

No Room migration, data reset, network/telemetry, permission, DI framework, broad module split, or enum strategy hierarchy is proposed. Internal Kotlin contracts and test doubles may change; externally observable successful workflows do not.

**Existing spec tension:** `app-shell` currently promises outside/Back/Cancel dismissal without a pending-operation qualification, while `ReadingForm`, deletion confirmation, and `TrackerScreenPersistenceUiTest` explicitly block dismissal during a mutation. The delta will clarify that exception to preserve the user-requested existing behavior, rather than silently relaxing the pending guard. Existing references to Edit/Delete actions remain compatible with labelled tap/long-press gestures; no icons will be added.

Validation during this proposal is OpenSpec strict validation only. Application build, unit, connected-device, and manual acceptance checks are planned for implementation, not claimed as executed.
