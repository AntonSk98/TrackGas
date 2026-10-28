# Design

## Context

See `proposal.md` for motivation and the two capability deltas for acceptance contracts. This design is necessary because state ownership, transaction boundaries, observation ordering, and process-local retention interact.

Observed implementation:

- `tracking\TrackerScreen.kt` owns saveable form/deletion targets and session gates, pager/tab animation and indicator layout, load content, and dialog outcome consumption. `ReadingForm.kt` additionally owns date/value drafts, picker visibility, duplicate suppression, error interpretation, and responsive layout.
- `TrackerViewModel.kt` has target-independent `ReadingOperation`; both mutation results and observation invoke `publish`, and every successful mutation restarts observation.
- `RoomReadingRepository.kt` validates raw locale strings inside `withTransaction`, preserves edit dates, and returns complete committed snapshots. Its suspend and Flow wrappers duplicate broad exception translation. `ReadingEntity.toReading` in `data\ReadingDatabase.kt` parses persisted strings.
- Application/Activity jointly manage a map of task-associated `ViewModelStore`s. The README and activity comment explicitly require retention across non-finishing activity destruction within the same process, beyond normal activity scope.
- `TrackerViewModelTest.kt` reimplements policy in its fake. Real Room tests already cover neighbour rules, concurrent duplicate dates, rollback, immutable edit dates, malformed values, database reopen, and durable precision. `TrackerScreenPersistenceUiTest.kt` covers draft/pending restoration, retry, completion, picker feedback, and fresh-session reset.

Main specs already constrain exact arithmetic, localized UI, accessibility, chart gestures, offline storage, and fresh-process defaults. The only identified wording conflict is unconditional draft dismissal versus tested pending guards; the `app-shell` delta explicitly qualifies it. No visual or navigation redesign follows from that clarification.

## Goals / Non-Goals

**Goals**

- Make illegal target/action combinations unrepresentable, with one transition owner and explicit restoration identity.
- Retain immediate committed mutation snapshots while defining reliable ordering across observers and concurrent tracker sessions.
- Reuse storage-independent policy without introducing an application-service layer that has no additional caller.
- Make failure categories testable without logging private readings.
- Close the post-refactor stored-collection validation gap, make the repository's existing contract discoverable at its interface, and encode valid policy outcomes/identity arguments in types.

**Non-Goals**

- No schema/version/index/serialization change, migrations, data cleanup, or destructive recovery.
- No new input surfaces, features, analytics, network diagnostics, permissions, DI framework, modules per layer, or strategy interfaces for closed enums.
- No attempt to persist UI sessions, drafts, or in-memory revision numbers across process death.
- No replacement of decimal arithmetic, chart projection/renderer, date/timezone helpers, or established gesture/navigation semantics.

## Decisions

### D1. ViewModel owns semantic dialog transitions; Compose owns its draft

Replace `ReadingOperation` plus independent screen targets with a sealed dialog state in `TrackerState`:

- `Closed`.
- `Editing(dialogId, target, phase)`, where target is Add with captured opening date or Edit with the original reading. Save phases are Ready, Pending(requestId), Invalid(field errors), or Failed(category).
- `ConfirmingDeletion(dialogId, reading, phase)`, with Ready, Pending(requestId), Missing, or Failed(category).

Use distinct phase types so deletion cannot carry field validation and save cannot carry a deletion outcome. Successful outcomes transition directly to Closed; no persistent Saved/Deleted gate or general-purpose `consumeOperation` remains. `sessionId` identifies the retained ViewModel; `dialogId` changes at every opening; `requestId` identifies each submission. Completion handlers must match all applicable identities before changing the dialog. Committed data still goes through D4 even if the originating dialog no longer matches.

The ViewModel handles OpenAdd/OpenEdit/OpenDelete, Submit, InputChanged/ClearError, Dismiss, and completion transitions on its existing main-thread scope. Open/submit require load readiness; pending disables fields, switching targets, dismissal, and duplicate submission. Invalid/missing/expected failure returns to the same target with the same error semantics. Load state is independent of dialog phase.

`ReadingForm` becomes a form coordinator plus field/action layout and a focused Add-date picker. Its saveable draft holder contains raw value, selected date, picker state, and duplicate-feedback dismissal state. Restore these only for the exact `(sessionId, dialogId)` supplied by the authoritative ViewModel, using an explicit saved identity comparison before showing content; do not rely only on `rememberSaveable` input invalidation during restoration. A restored Closed or different-session/dialog state cannot resurrect a draft. The holder is not a second authority for the target or mutation phase.

Rotation retains the ViewModel's semantic state and Compose's matching draft. Non-finishing activity recreation with saved UI state follows the same matching rule. A new ViewModel always starts Closed; database reads restore records, not drafts. Captured Add defaults remain stable while the form is open; current-date limits and final submission still reevaluate the injected local-date provider.

Duplicate feedback remains confined to the picker. A duplicate save rejection creates one picker-opening response for that submission; dismissing the picker preserves the form without a warning in the parent, including through rotation. A subsequent submission can reopen it. Edit keeps its date read-only with no picker.

**Alternative rejected:** placing all draft keystrokes/picker layout state in the ViewModel adds unnecessary presentation ownership; keeping independent saveable dialog targets plus a global operation retains the distributed state machine.

### D2. Extract by responsibility, not by line count

Keep `TrackerScreen` as a small state/callback route without a ViewModel dependency. Extract:

- Pager/tab shell: tab animation cancellation, initial synchronization to the ViewModel's tab, continuous indicator placement, independent page scroll state, and settled-page callbacks.
- Load-state content: loading/error/retry presentation shared by page and dialog contexts where appropriate.
- Delete-confirmation component: renders target and phase, dispatches confirm/dismiss only.
- Form draft/picker coordinator and stateless field/action layout: preserves compact-height scrolling, keyboard/insets, labels, and date/error presentation.

The route dispatches semantic dialog events and renders only the dialog represented by `TrackerState`. Extracted renderers receive only required state/events rather than forwarding an entire host or duplicating eligibility checks. Pager gesture behavior and chart inspection remain unchanged.

**Alternative rejected:** one new coordinator per existing code block would relocate coupling without creating useful test seams.

### D3. Parse outside persistence; evaluate shared typed policy inside the transaction

Split `ReadingValidation.kt` into focused pure responsibilities within the existing tracking package:

1. Locale parser: normalize digits/separator exactly as today; consume the complete trimmed input; return a typed decimal or VALUE_REQUIRED/INVALID_NUMBER parse error. Do not convert through floating point or normalize decimal scale.
2. Typed save intent: an explicit Add/Edit target, optional requested date for Add, parsed meter result, and submission-time `today`. Neither raw strings nor `Locale` cross `ReadingRepository.save`.
3. Pure save policy: resolve Edit first (preserving current MISSING_READING precedence), force its persisted identity/date, combine date and meter errors with current precedence, validate non-negativity and both neighbours, and return an explicit Accepted candidate or Rejected errors outcome as specified in D10. A missing Add date and invalid meter can still yield both field errors. Reuse the existing pure date check for picker feedback.

Keeping the parsed-error variant in the typed intent lets final policy preserve independent field errors and missing-target precedence without persisting malformed input. Negative numeric input remains a typed value until policy reports NEGATIVE_VALUE. Add identity generation is an injected/default UUID function used by repository orchestration and passed into the pure policy; Edit never creates a new identity.

`RoomReadingRepository.save` acquires the repository ordering mutex described in D4, enters `withTransaction`, reads and decodes the latest rows, invokes the shared pure policy, writes only an accepted candidate, and captures the resulting full rows. The transaction must return successfully before a Saved result or revision is issued. Missing/invalid outcomes write nothing. Delete keeps target lookup/deletion and final snapshot capture transactional; an absent target remains Missing. Unexpected update-count invariants remain defects.

Repository responsibility is explicitly transactional storage orchestration around a shared policy, not string interpretation or duplicated application rules. No standalone use-case/service class is added: there is one caller and one storage implementation today.

**Alternatives rejected:** UI/pretransaction chronology checks alone weaken atomic concurrency; a new service plus generic transaction interfaces adds indirection without a current caller.

### D4. One publication reducer with reliable process-local snapshot ordering

Retain mutation snapshots for immediate post-commit display. Change observation and successful mutation results to carry `CommittedSnapshot(revision, readings)`. Revisions are scoped to the single application-composed repository instance shared by all activity sessions, not stored in Room and not derived from wall time, reading counts, hashes, or a target's value.

Ordering protocol:

1. One repository-scoped coroutine `Mutex` serializes mutation transactions and observation snapshot capture. Room transactions continue to supply the database concurrency guarantee; the mutex supplies delivery ordering within this process.
2. Successful mutations capture their full snapshot in the transaction, wait for `withTransaction` to return, then allocate a strictly increasing revision while still holding the mutex. Failed/invalid/missing operations do not manufacture a committed mutation result.
3. Room's existing `observeAll()` emissions are invalidation triggers, not authoritative captured payloads. For each trigger, acquire that same mutex and re-read/decode the current rows through `getAll()`, then allocate the next revision before releasing the mutex. Never assign a fresh revision to old rows buffered by the Room Flow.
4. `TrackerViewModel.acceptCommittedSnapshot` is the only data publication reducer. Reject revisions at or below its accepted watermark. For a newer revision, advance the watermark and compare canonical readings with the accepted data. Sort and recompute the dashboard only when data differ; compare exact identities/dates/BigDecimal values including scale so raw precision changes remain visible.
5. Period changes deliberately reproject the accepted data through the same projection helper; tab changes do not. A duplicate data delivery does not replace projection/inspection identity. Accepting an older mutation result still completes its matching action once: snapshot acceptance and action completion are separate.

The shared mutex prevents a read captured before commit from receiving a later revision than that commit. An already captured old snapshot can still arrive late, but its lower revision is rejected. Multiple ViewModels use the same ordering domain. Flow conflation is safe: committed mutations have direct snapshots, while observation need only converge to the newest captured committed state. No persisted commit token or schema migration is necessary. A new process/repository starts a new revision domain together with new ViewModels; repository replacement beneath live ViewModels is not supported.

Keep one healthy observation job per ViewModel. Do not restart it after save/delete. Retry replaces a failed observation only, with an observation-generation guard so a cancelled old collector cannot overwrite a newer collector's load status. A successful current observation establishes READY even when its data are deduplicated.

Observation and mutation errors remain separate. If observation fails during a mutation, retain its ERROR state and last data, accept any later successful committed mutation snapshot, and close that matching dialog. Do not relabel the commit as failed or silently clear the load error. Block new mutations until explicit load retry succeeds. If the mutation itself fails, retain its dialog/draft and the existing observation state. A successful commit cannot be undone by observation failure, stale results, or retry.

Cancellation propagates: before commit Room rolls back; if a caller is cancelled after commit but before receiving its result, observation/reload remains authoritative and no false failed-mutation result is synthesized. Clearing a retained store cancels its ViewModel; another session or a fresh process can reload committed records.

**Alternatives rejected:** observation-only completion requires reliable commit correlation and complicates immediate success/error recovery. Simply deduplicating list equality cannot reject an older but different snapshot. Per-ViewModel counters cannot order concurrent sessions. Restarting observation after every mutation is unnecessary.

### D5. Narrow failure translation and retain an explicit safe category

Use one expected-failure classifier shared by suspend operations and Flow handling:

- `StorageAccess`: known SQL/I/O failures at the storage boundary.
- `StoredData`: explicit decoding failures at `ReadingEntity.toReading` (invalid stored date/decimal or violated persisted Reading invariants), or explicitly identified invalid persisted collection relationships at the repository read boundary (D8).
- Cancellation: rethrow unchanged before classification.
- Everything else: rethrow unchanged as an unexpected defect, including unrelated IllegalArgumentException/IllegalStateException and update-count checks.

At decoding, narrowly catch date/number parse exceptions and explicitly check the persisted identity/value invariants before constructing `Reading`. Translate only those known malformed-data cases into a decoding failure; do not wrap an entire transaction or every constructor call in broad argument/state catches. The shared storage wrapper carries the original cause and category without including row values in its own message.

The ViewModel retains only safe category/action context in load/dialog failure state while rendering existing generic localized errors. This explicit category satisfies diagnostic needs without adding logging, telemetry, database paths, raw SQL, row identity/date/value, raw input, or throwable-message output. Causes remain attached to internal exceptions for controlled debugging, not emitted into UI state or logs. Retry rereads normally; stored-data failure never triggers automatic repair, deletion, or backup.

**Alternatives rejected:** catch-all retryable storage errors hide defects; new corruption-specific UI or remote crash collection expands behavior/privacy scope unnecessarily.

### D6. Isolate, do not replace, custom retention

Move the task-associated map and store obtain/release/prune policy into a focused `TrackerStoreOwner`. Keep it application-owned for the process lifetime, separate from Room composition. The Activity retains its saved UUID and supplies its current task and active-task IDs (including its own task); the owner encapsulates registry/cleanup decisions, including the finishing/not-changing-configurations guard. Multiple activity identities remain distinct even within one task.

Declare the composition dependency explicitly as `ReadingRepository`; continue lazy singleton Room construction. Do not let screens or the owner depend on Room types. Update README to explain why ordinary activity-scoped ownership would lose the required non-finishing-destruction retention, and describe release/prune/process-death behavior.

**Alternative rejected:** standard activity scope does not meet the documented retention requirement; a DI framework is not needed to expose an interface or own a small registry.

### D7. Test orchestration with scripts; test policy and persistence where they live

- Replace `TrackerViewModelTest`'s policy fake with a scripted double: supplied snapshots/revisions, recorded typed intents, queued outcomes, explicit gates, and controllable observation errors. It performs no target lookup, UUID generation, validation, replacement, sorting, or deletion policy.
- Move realistic save-rule assertions to pure parser/policy tests and adapt real `RoomReadingRepositoryTest`; retain database reopen/persistence, rollback, exact decimal scale, and concurrent duplicate-date tests.
- Add pure transition tests for dialog exclusivity, target/request matching, pending guards, error clearing, and stale completion rejection. Keep UI restoration tests against semantic state, not the old global operation enum.
- Deterministically test old observation after new mutation, duplicate delivery without reprojecting, observation-before-result, newer other-session commit before older result, observation failure during/after commit, retry/generation guards, and no normal resubscription. Gate the real repository capture/transaction paths to verify revision ordering; scripted tests alone cannot establish D4's contract.
- Expand real Room concurrency tests to incompatible neighbouring writes and expected decode/SQL failure categories. Test cancellation and unrelated invariant exceptions are not translated. Verify malformed identity/date/value leave the database untouched.
- Keep `TrackGasApplicationTest` coverage and add owner release/prune/isolation tests plus activity recreation acceptance. Use `TrackerScreenPersistenceUiTest` for same-session draft/picker restoration, fresh-session discard, pending dismissal blocking, failed delete retry, and successful closure without resurrection.

**Alternative rejected:** sharing an increasingly realistic fake would still duplicate orchestration and would not prove Room atomicity.

### D8. Validate stored collection relationships before publication or mutation

The post-refactor review found that individually valid decoded rows may still form decreasing cumulative readings. Extract or reuse explicit pure collection checks for unique identities, unique dates, and nondecreasing cumulative values in chronological order. Consumption interval construction and the repository must share these invariant definitions rather than maintain parallel policy implementations. Unordered valid collections and equal cumulative values remain valid; checks must not normalize decimal scale.

After decoding in the shared repository row-read path, check the collection before issuing a snapshot or using those rows for save/delete policy. Translate only an explicitly identified persisted collection violation into the existing StoredData exception/category with a generic safe message and retained internal cause. Do not catch all argument/state exceptions from projection, constructors, or transactions. Cancellation and unrelated programming/update-count defects remain unchanged.

Invalid stored collections must not reach dashboard projection. Failed observation retains the last accepted snapshot and open dialog; failed mutation retains the draft/confirmation without writing, deleting, repairing, or resetting rows. Retry rereads normally and reports the same failure if data remain invalid. Add pure invariant tests, real Room observation/save/delete tests, and a ViewModel StoredData-load regression. No new UI messages, telemetry, database constraints, or migration are needed.

### D9. Put the repository contract at the abstraction

Add KDoc to `ReadingRepository`, its snapshot type, and methods covering:

- One process-local repository-instance revision domain shared by observations and successful mutation results, with strictly increasing capture/commit ordering; counters do not reset on resubscription. Replacing the repository beneath a live consumer is unsupported.
- Snapshots represent committed, collection-valid full data and are stable after delivery; callers must not mutate supplied collections. Row order is not the freshness signal, and exact decimal scale remains meaningful.
- Invalid/missing/failed operations do not manufacture successful mutation snapshots. Final save validation and writes are atomic against the latest transactional rows, and success follows commit.
- Observations may repeat identical data or skip intermediate states but must converge to current committed data. Buffered old payloads must not be assigned a revision newer than a later captured commit.
- Expected failures use the documented storage-access/stored-data categories; cancellation and unrelated defects propagate. Cancellation before commit rolls back; cancellation after commit cannot imply that the database write was undone.

Retain the existing Room ordering tests and scripted ViewModel tests. Do not create a generic adapter hierarchy or second production implementation merely to test documentation.

### D10. Encode policy outcomes and Add identity requirements

Replace `ReadingValidation(candidate?, errors)` with sealed Accepted/Rejected variants. Accepted contains a non-null `Reading`; Rejected requires nonempty field errors. Repository consumers branch exhaustively rather than inferring success from nullability. Preserve independent field errors, missing-target precedence, immutable edit identity/date, neighbour equality, and exact scale.

Expose distinct typed Add/Edit policy entry points (overloads are sufficient): Add requires an explicit identity argument; Edit accepts no irrelevant Add identity. Keep any shared resolved-field validation private and reused. Repository orchestration branches on the typed intent, invokes the existing identity function only for Add, evaluates against the latest validated rows inside the transaction, and writes only Accepted results. No public nullable/default identity argument remains.

Update all policy callers and tests together, retaining negative-input and invalid-date semantics. Add result-construction coverage rejecting empty errors and preserve the real repository test proving Edit never generates an identity.

## Risks / Trade-offs

- [More explicit dialog types change internal test APIs] → Update host wiring and fixtures together; retain all existing behavior assertions rather than deleting tests.
- [Mutex and trigger re-read add serialization and one extra read per invalidation] → Accept for one small local tracker, deduplicate projection work, and never hold the mutex while waiting for UI or collecting Flow. No performance benchmark framework is needed.
- [Misnumbered stale Flow payloads could regress data] → Treat payloads only as triggers; revision after locked fresh capture is mandatory and covered by deterministic repository tests.
- [Revision scope accidentally spans different repository instances] → Application composes one instance, tests assert it, and every new ViewModel starts with no watermark. Do not persist revisions.
- [Broad decoding catches could again hide defects] → Explicitly validate persisted fields and catch only known parse failures at mapping; test unrelated exceptions outside mapping.
- [Saveable inputs alone do not reject stale restored drafts] → Check stored session/dialog identity explicitly, using ViewModel state as authority before rendering.
- [Owner extraction could clear live tasks or retain abandoned stores] → Preserve current active-task discovery and own-task inclusion; test finishing/configuration/non-finishing paths and independent activity identities.

## Migration Plan

After separate apply authorization, implement in test-backed slices: semantic dialog ownership, focused UI extraction, typed policy/error boundaries, ordered publication, and retention ownership. Update scripted fixtures with each contract change and keep the real Room suites throughout.

No persisted migration is required: Room stays version 1 with unchanged fields/index and exact decimal strings. Existing installs reopen the same `readings.db`. New state/revision identifiers are process-local only. Rollback restores the prior implementation without converting or deleting data; neither rollout nor rollback introduces a data-reset path.

The approved follow-up applies D8-D10 in targeted slices after planning updates: shared collection validation and failure regressions, repository contract documentation, then type-safe policy results/entry points and caller adaptation. Retain all previously completed behavior; use the existing unit and Room instrumentation runners for affected tests, and explicitly leave unavailable device verification pending.

Implementation verification uses the README PowerShell environment (`JAVA_HOME=$env:USERPROFILE\.jdks\temurin-26.0.1`, `ANDROID_HOME=$env:LOCALAPPDATA\Android\Sdk`, remove literal `%JAVA_OPTS%`) and existing commands:

- `.\gradlew.bat :app:testDebugUnitTest --tests "com.trackgas.app.*"`
- `.\gradlew.bat :app:assembleDebug`
- `.\gradlew.bat :app:connectedDebugAndroidTest` with an available device/emulator.

Run the relevant existing manual acceptance checklist for offline/privacy, compact/landscape/200% fonts, locale/timezone, tabs/swipes/chart inspection, and recreation/process death. Record unavailable device checks as pending, not passed. This planning-only workflow runs OpenSpec strict validation, not application checks.

## Finding Coverage

| Report ID | Concrete decision | Implementation task groups |
| --- | --- | --- |
| 1 | D2 focused UI and D1 form ownership | 1–2 |
| 2 | D1 target-bound state and explicit restoration identity | 1–2 |
| 3 | D3 parser/typed policy inside transaction | 3 |
| 4 | D5 classifier, decode boundary, safe categories | 4 |
| 5 | D4 revision-ordered deduplicating reducer | 5 |
| 6 | D6 focused retained-store owner and interface exposure | 6 |
| 7 | D7 scripted orchestration double, pure/real policy coverage | 1, 3–5, 7 |
| Post-refactor 1 | D8 shared persisted collection checks and safe failure boundary | 8 |
| Post-refactor 2 | D9 repository API contract | 9 |
| Post-refactor 3 | D10 explicit validation outcomes and Add/Edit entry points | 10 |
