# Tasks

Tasks 1-7 record the completed original implementation. Tasks 8-11 are the approved post-review plan extension and remain unchecked until implemented and verified through apply. Follow `design.md` D1-D10 and the `app-shell` / `gas-consumption-tracking` deltas; preserve all unchanged main-spec behavior. Planning approval alone does not mark implementation complete.

## 1. Explicit dialog ownership and orchestration tests

- [x] 1.1 Replace the target-independent operation and independent screen targets with ViewModel-owned Closed/Editing/ConfirmingDeletion states, distinct action phases, and session/dialog/request matching (D1); verify transition tests cover exclusivity, target-bound errors, stale completions, and success directly closing the matching dialog.
- [x] 1.2 Implement open/submit/dismiss/error-clearing guards and adapt `MainActivity` callback wiring; verify tests reject submissions while loading/pending, block pending Back/Cancel/outside dismissal, retain failed/missing targets, and preserve tab/period across every outcome.
- [x] 1.3 Replace the policy-implementing `TrackerViewModelTest` fake with a scripted double recording requests and exposing explicit outcomes, gates, and observation delivery (D7); verify orchestration tests pass without any double-side lookup, ID generation, validation, sorting, or mutation policy, relocating realistic rule assertions to pure/Room tests rather than deleting coverage.
- [x] 1.4 Introduce the form-local saveable draft holder with explicit restored session/dialog identity checks; adapt `TrackerScreenPersistenceUiTest` and verify matching draft/pending restoration, fresh-session discard, new-dialog reset, and no completed-dialog resurrection.
- [x] 1.5 Document dialog/action versus draft ownership and the pending-dismissal exception in README code structure/usage; verify the text matches implemented guards and does not promise draft persistence across process death.

## 2. Focused UI boundaries

- [x] 2.1 Extract the pager/tab shell, keeping initial ViewModel-tab authority, animation cancellation, continuous indicator placement, and independent scroll states (D2); verify existing tab/gesture tests or device checks cover taps, partial/reversed swipes, rotation, and fresh-process Dashboard defaults without a second settling animation.
- [x] 2.2 Extract load-state presentation and target-bound deletion confirmation with narrow state/event inputs; verify UI tests retain loading-versus-empty distinctions, disabled pending controls, missing/delete errors, retry, and successful dismissal.
- [x] 2.3 Split form coordination/picker behavior from field/action layout; verify `TrackerScreenPersistenceUiTest` covers duplicate-save picker opening once per attempt, picker dismissal/restoration without parent warnings, retained raw drafts, read-only Edit dates, and non-pending outside dismissal.
- [x] 2.4 Preserve compact-height scrolling, keyboard/insets, accessible labels/touch targets, page layout, and chart gesture priority during extraction; verify existing README compact/landscape/200%-font and chart navigation/inspection acceptance cases and document the new focused UI boundaries in README.

## 3. Typed parsing and transaction-local save policy

- [x] 3.1 Extract locale parsing and typed parsed-meter/Add/Edit intents (D3), removing raw strings and Locale from the repository save contract; verify parser tests preserve localized digits/separators, ordinary decimal point, trimming, full-input rejection, exponent/grouping rejection, non-finite rejection, and exact decimal scale.
- [x] 3.2 Implement shared pure save policy and retain the shared picker date predicate; verify pure tests cover missing-target precedence, both independent field errors, negative values, missing/future/duplicate dates, both neighbours, equal readings, edit self-exclusion, immutable edit identity/date, and submission-time today.
- [x] 3.3 Invoke typed policy against freshly decoded rows inside Room `withTransaction`, generating Add identities only through the defined identity function and capturing successful snapshots before transaction return; verify real Room tests retain rollback, missing-target handling, uniqueness, immutable edit dates, deletion behavior, and exact stored decimal strings.
- [x] 3.4 Adapt all callers and scripted fixtures to typed requests without reimplementing save policy; verify unit tests assert the submitted typed intent/current date and Room concurrency tests cover both competing duplicate dates and incompatible neighbouring writes.
- [x] 3.5 Update README code structure with the parser/policy/transaction boundary; verify documentation explicitly states final validation and writes are atomic and picker checks are advisory, with no new service layer or persistence format.

## 4. Failure classification and privacy

- [x] 4.1 Centralize expected SQL/I/O and stored-decoding failure classification across suspend and Flow paths (D5); verify classification tests distinguish StorageAccess/StoredData, preserve internal causes, and rethrow cancellation and unrelated argument/state/invariant failures unchanged.
- [x] 4.2 Narrow translation in `ReadingEntity.toReading` to known parse failures and explicit persisted invariants; verify real Room tests for malformed identity/date/decimal and negative stored values report StoredData without partial writes, repairs, or database deletion.
- [x] 4.3 Carry category/action context in load and dialog failures while preserving generic localized messages and retry; verify ViewModel/UI tests retain drafts/confirmations and last committed data, and assert diagnostic state contains no raw input, readings, SQL arguments, or throwable messages.
- [x] 4.4 Document safe failure categories and non-destructive retry in README architecture notes; verify no logging/telemetry, permission changes, or broad retryable programming-error translation is introduced.

## 5. Ordered, deduplicated committed publication

- [x] 5.1 Add repository-scoped in-memory revisions and shared capture/mutation mutex (D4), allocating mutation revisions only after commit and re-reading current rows under the mutex for observation triggers; verify gated real repository tests reject any scheme that stamps buffered old rows with a new revision and confirm Room schema/exported schema remain unchanged.
- [x] 5.2 Route observation and mutation snapshots through the sole revision-aware ViewModel acceptance reducer; verify scripted tests cover older observation after newer mutation, observation before mutation return, newer other-session snapshot before an older successful result, exact-scale changes, and unchanged tab/period.
- [x] 5.3 Deduplicate identical canonical readings before dashboard projection and retain projection identity; verify tests show duplicate deliveries advance only the watermark/readiness, do not recompute the data projection or reset chart inspection, and still permit intentional period reprojection.
- [x] 5.4 Remove normal mutation-driven observation restarts and add failed-observer retry generation guards; verify subscription-count and gated tests prove one healthy collector, explicit retry after failure, and no stale collector changing current load state.
- [x] 5.5 Keep commit/action completion independent of observation failure and snapshot acceptance; verify tests for observation failure during and after save/delete retain successful committed data, close the matching dialog once, preserve load ERROR until retry, block new mutation, and never offer resubmission of a committed action.
- [x] 5.6 Verify cancellation before commit rolls back and cancellation after commit cannot manufacture a failed-mutation response; use controlled repository/coroutine tests to confirm another observer or fresh load sees committed data where applicable.
- [x] 5.7 Document the singleton revision domain, fresh locked capture, no-schema-change guarantee, and independent load/mutation outcomes in README code structure; verify real two-ViewModel integration tests converge on committed data and that documentation does not imply revisions survive process death.

## 6. Focused retained-store ownership

- [x] 6.1 Extract application-owned `TrackerStoreOwner` with obtain/release/prune and destruction guard policy (D6), leaving the Activity to supply saved identity/task lifecycle inputs; verify owner/application tests cover same-identity retention, independent identities including the same task, abandoned-task pruning, own-task inclusion, and exactly-once store clearing.
- [x] 6.2 Explicitly expose the lazy singleton composition dependency as `ReadingRepository`; verify `TrackGasApplicationTest` retains singleton identity, disabled backup, and absence of INTERNET permission, and ensure the owner has no Room dependency.
- [x] 6.3 Verify rotation, non-finishing destruction/recreation, finishing outside configuration change, and fresh-process defaults through focused lifecycle tests/device acceptance; confirm committed records survive while old process-session dialogs do not.
- [x] 6.4 Update README to explain the custom retention requirement, focused owner, release/prune rules, and process-death boundary; verify it does not claim ordinary activity scope is equivalent or promise durable drafts.

## 7. Cross-cutting acceptance and implementation completion

- [x] 7.1 Using the README PowerShell environment (`JAVA_HOME=$env:USERPROFILE\.jdks\temurin-26.0.1`, `ANDROID_HOME=$env:LOCALAPPDATA\Android\Sdk`, strip literal `%JAVA_OPTS%`), run `.\gradlew.bat :app:testDebugUnitTest --tests "com.trackgas.app.*"` and `.\gradlew.bat :app:assembleDebug`; verify both pass and retain arithmetic, validation, formatting, timezone, chart projection, and orchestration coverage.
- [x] 7.2 With an available device/emulator, run `.\gradlew.bat :app:connectedDebugAndroidTest`; verify real database reopen/persistence, atomic concurrency, failure classification, retained stores, and UI restoration pass together, or explicitly record the unavailable environment and leave verification outstanding.
- [x] 7.3 Exercise the relevant README device checklist across offline launch, locale/timezone changes, compact/landscape/200% fonts, reading gestures, tabs/scroll/period navigation, chart inspection, pending/errors/retry, rotation, activity recreation, and process restart; record actual results and do not mark unavailable checks passed.
- [x] 7.4 Review implementation diff against report findings 1–7 and design coverage table, confirming unchanged Room version/schema/data and no new features/frameworks/permissions or duplicated save policy; verify existing pre-change `gradlew` and `CLEAN_CODE_REPORT.md` work remains intact.
- [x] 7.5 Run `openspec validate refactor-tracker-clean-code --strict --json` and review all delta scenarios against implementation evidence; verify no strict issues remain before considering the change complete.

## 8. Persisted collection invariant boundary

- [x] 8.1 Extract/reuse explicit pure collection checks for unique identities/dates and chronological nondecreasing cumulative values (D8), sharing the definitions with consumption interval construction; verify pure tests reject invalid collections while allowing unordered valid input, equal values, empty/single readings, and unchanged decimal scale.
- [x] 8.2 Apply those checks after decoding in the shared repository row-read path before snapshots or mutation policy; verify only identified stored collection violations become StoredData with generic messages/internal causes, while no broad argument/state catches, automatic repair/deletion, or schema changes are introduced.
- [x] 8.3 Add real Room observation/save/delete regressions for individually valid but decreasing persisted rows and ViewModel StoredData-load handling; verify unchanged database rows, no invalid projection, retained last accepted data/dialog, non-destructive repeated retry, and unchanged unrelated-defect/cancellation handling.

## 9. Explicit repository abstraction contract

- [x] 9.1 Document snapshot ownership/validity, the shared process-local revision domain, ordering and resubscription guarantees, atomic validation/write and post-commit success, expected failures, cancellation, and unsupported live repository replacement at the API (D9); reconcile KDoc with existing ordering tests and README without adding adapters/frameworks.

## 10. Type-safe policy results and identity provision

- [x] 10.1 Replace nullable candidate/independent errors with sealed Accepted/Rejected validation outcomes, requiring nonempty rejection errors (D10); verify result-construction and policy tests retain combined field errors, precedence, neighbour equality, immutable edits, and exact decimal scale.
- [x] 10.2 Introduce typed Add/Edit policy entry points requiring an Add identity and no irrelevant Edit identity, adapt every production/test caller, and retain shared private policy logic; verify real repository coverage still proves Add-only identity generation and final policy/writes remain transaction-local.

## 11. Follow-up acceptance and documentation

- [x] 11.1 Run the smallest existing unit selectors covering collection invariants, consumption/dashboard, policy, failure classification, and ViewModel behavior, and the affected Room instrumentation classes using the README PowerShell environment; verify affected Android tests compile, record actual outcomes, and leave this task outstanding if device execution is unavailable rather than marking it passed.
- [x] 11.2 Update README architecture notes and the post-refactor report with the actual implemented follow-up outcome, review the diff for unchanged schema/UI/privacy/ordering behavior, and run `openspec validate refactor-tracker-clean-code --strict --json`; verify planning artifacts and implementation evidence agree without disturbing unrelated work.

Follow-up execution evidence (2026-10-28): 63 targeted unit tests passed across ReadingCollectionTest, ConsumptionTest, DashboardTest, ReadingValidationTest, ReadingPolicyTest, ReadingFailuresTest, TrackerViewModelTest, ReadingInputTest, and ChartProjectionTest. The connected Android run compiled the instrumentation sources and passed 17 tests across RoomReadingRepositoryTest (10), OrderedRepositoryTest (6), and ReadingPersistenceTest (1) on the running TrackGas_API_35 emulator (Android 15), with zero failures/errors/skips. Both runs used the documented PowerShell Java/SDK setup and existing Gradle tasks. No full manual UI acceptance rerun is claimed for this backend-only follow-up.

README and the post-refactor report record the implemented boundaries and outcomes. Strict OpenSpec validation passed with no issues; final diff review confirmed this follow-up does not alter schema, resources, permissions, dependencies, UI/lifecycle implementation, or the pre-existing staged `gradlew` and original report.
