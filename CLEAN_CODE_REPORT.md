# Clean Code and SOLID Review

Date: 2026-10-28

## Summary

The codebase has several maintainability hotspots, but it does not need a wholesale
architecture rewrite. The strongest concerns are oversized UI coordinators,
distributed operation/dialog state, and a persistence interface that also owns
input interpretation and application policy.

This is a static review of the handwritten production Kotlin sources, Android
configuration, and representative unit/instrumentation tests. Generated code and
build outputs are excluded. Findings describe observable design issues and their
risks, not reproduced runtime failures. Priority means refactoring urgency:
**Medium** warrants focused improvement; **Low** is secondary cleanup.

| ID | Priority | Finding | Principle |
| --- | --- | --- | --- |
| 1 | Medium | Screen and form combine too many responsibilities | Single Responsibility, cohesion |
| 2 | Medium | Dialog lifecycle and operation state form a distributed state machine | Encapsulation, explicit state |
| 3 | Medium | Repository combines persistence with draft parsing and save policy | Single Responsibility, separation of concerns |
| 4 | Medium | Broad exception translation hides failure categories and duplicates policy | Clear error contracts, DRY |
| 5 | Low | Mutation results and observation both publish complete snapshots | Single source of truth, DRY |
| 6 | Low | Application duplicates lifecycle ownership and exposes implementation details | Single Responsibility, encapsulation |
| 7 | Low | Test repository duplicates production save orchestration | DRY, test fidelity |

## Findings

### 1. Screen and form combine too many responsibilities

**Evidence**

- `app\src\main\java\com\trackgas\app\tracking\TrackerScreen.kt:95-325`
  coordinates session restoration, operation eligibility, pager synchronization,
  custom tab-indicator layout, loading/error content, editor opening, and deletion.
- `app\src\main\java\com\trackgas\app\tracking\ReadingForm.kt:70-243`
  combines draft ownership, operation-to-error interpretation, duplicate-picker
  policy, compact-layout behavior, dialog dismissal, fields, actions, and picker UI.

**Why it matters:** These functions change for independent reasons. A pager
animation adjustment shares a coordinator with persistence recovery and dialog
restoration. The deeply nested layout and inline callbacks make state transitions
harder to inspect and regression boundaries harder to isolate. Function length
alone is not the issue; the independent responsibilities are.

**Recommendation:** Extract a focused pager/tab shell, load-state content, and
delete-confirmation component. Split the form's draft/picker coordination from its
field layout. Keep a small route-level coordinator for genuinely shared behavior;
do not merely move arbitrary blocks into equally coupled helpers.

### 2. Dialog lifecycle and operation state form a distributed state machine

**Evidence**

- `app\src\main\java\com\trackgas\app\tracking\TrackerViewModel.kt:18-36`
  models an operation independently from its target and dialog.
- `app\src\main\java\com\trackgas\app\tracking\TrackerScreen.kt:107-121`
  stores separate form/deletion targets and derives eligibility through several
  Boolean conditions and a session token.
- `app\src\main\java\com\trackgas\app\tracking\TrackerScreen.kt:256-325`
  hides dialogs on completion and repeatedly coordinates operation consumption.
- `app\src\main\java\com\trackgas\app\tracking\ReadingForm.kt:87-96,201-224`
  adds another layer of picker and duplicate-dismissal state.

**Why it matters:** A legal user interaction depends on combinations of state
owned by different layers. The types permit both dialog targets to exist, or a
generic pending/failed operation without an associated action or target. Current
callbacks guard normal paths, but correctness relies on all entry, dismissal,
completion, and restoration paths maintaining those conventions. `Saved` and
`Deleted` also serve as persistent gates that suppress dialogs rather than just
describing the mutation outcome.

**Recommendation:** Model mutually exclusive dialog states explicitly, for example
closed/editing/confirming deletion, and associate pending/results with the relevant
action. Give transitions a clear owner. Preserve the existing requirements for
rotation, process-session reset, retained drafts, and blocked pending dismissal.
This does not require putting every transient UI detail into the ViewModel.

### 3. Repository combines persistence with draft parsing and save policy

**Evidence**

- `app\src\main\java\com\trackgas\app\tracking\ReadingRepository.kt:19-30`
  accepts nullable dates, raw input strings, the current date, and a locale.
- `app\src\main\java\com\trackgas\app\data\RoomReadingRepository.kt:30-54`
  generates identities, resolves edit targets, forces original edit dates,
  invokes draft validation, writes entities, and returns a full snapshot.
- `app\src\main\java\com\trackgas\app\tracking\ReadingValidation.kt:45-79`
  combines locale-aware number parsing with chronological business rules.

**Why it matters:** The repository abstraction is real, and the ViewModel correctly
depends on it. Nevertheless, its contract mixes a persistence boundary with an
application use case. A different input surface or import mechanism must conform
to a locale/string-oriented save API, and a new persistence implementation must
reproduce policy such as immutable edit dates and identity creation.

**Recommendation:** Separate draft parsing from typed save commands. Move shared
save policy into an application service/use case if additional callers or storage
implementations justify it. Keep neighbor/date validation against the latest
snapshot and the write in the same transaction; moving validation into the UI
alone would weaken the existing concurrency guarantee.

**Classification:** Primarily an SRP/boundary concern, not proof of a blanket
Dependency Inversion violation. Small applications can intentionally use a
policy-owning repository, but that choice should be explicit.

### 4. Broad exception translation hides failure categories and duplicates policy

**Evidence**

- `app\src\main\java\com\trackgas\app\data\RoomReadingRepository.kt:67-88`
  implements two exception-classification paths. Both translate
  `IllegalArgumentException` and `IllegalStateException` into storage failures
  alongside SQL, I/O, and stored-date failures.
- `app\src\main\java\com\trackgas\app\tracking\TrackerViewModel.kt:75-76,100-101,119-120`
  reduces the resulting exception to a generic load/error outcome without retaining
  or recording its cause.

**Why it matters:** These broad exception classes can represent malformed stored
data, database lifecycle problems, or programming/invariant errors. Translating
all of them across the entire transaction makes unrelated defects look like
retryable device-storage failures. The original cause is preserved by the wrapper,
but is then discarded at the presentation boundary. Maintaining two classifiers
also risks inconsistent behavior between reads and writes.

**Recommendation:** Centralize classification. Narrow stored-data decoding
translation to the mapping boundary, distinguish expected infrastructure/data
failures where recovery differs, and avoid reclassifying unrelated programming
errors. Retain privacy-safe diagnostics or an explicit error category when showing
a generic user message. Preserve the existing cancellation propagation.

### 5. Mutation results and observation both publish complete snapshots

**Evidence**

- `app\src\main\java\com\trackgas\app\tracking\ReadingRepository.kt:9-16`
  returns complete reading lists after successful writes and deletions.
- `app\src\main\java\com\trackgas\app\data\RoomReadingRepository.kt:38-53,57-63`
  reads full snapshots inside mutation transactions.
- `app\src\main\java\com\trackgas\app\tracking\TrackerViewModel.kt:71-73,94-97,113-116`
  publishes mutation snapshots, restarts observation, and then publishes observed
  snapshots as well.
- `app\src\main\java\com\trackgas\app\tracking\TrackerViewModel.kt:125-127`
  sorts and recalculates the dashboard on each publication.

**Why it matters:** There are two delivery paths for the same committed data,
plus observer cancellation/re-subscription after every successful mutation.
This adds redundant projection work and ordering/recovery behavior to reason
about. All currently published snapshots can still be committed snapshots;
this finding is not a claim that uncommitted data is displayed.

**Recommendation:** Define one authoritative publication strategy. If observation
owns snapshots, mutation results can describe the outcome and completion can be
correlated with an observed commit. If immediate mutation snapshots are retained,
document their ordering contract and avoid redundant restarts/reprojections where
possible. Preserve the guarantee that success never precedes commit.

### 6. Application duplicates lifecycle ownership and exposes implementation details

**Evidence**

- `app\src\main\java\com\trackgas\app\TrackGasApplication.kt:10-25`
  maintains a process-wide registry of task-associated `ViewModelStore` instances.
- `app\src\main\java\com\trackgas\app\MainActivity.kt:23-33,43-52`
  manages tracker IDs, active-task discovery, saved-state restoration, pruning,
  and explicit store release.
- `app\src\main\java\com\trackgas\app\TrackGasApplication.kt:27-30`
  also constructs infrastructure and exposes an inferred concrete
  `RoomReadingRepository` property.

**Why it matters:** Application composition and task/activity lifecycle bookkeeping
are separate responsibilities. Store cleanup now depends on application-specific
coordination across two classes rather than only standard activity ownership.
The custom retention policy may be intentional, but it introduces lifecycle
maintenance obligations. Exposing the concrete repository also widens the
dependency surface unnecessarily.

**Recommendation:** Document which retention requirement exceeds the normal
activity `ViewModelStore`. Use standard activity ownership if it meets that
requirement; otherwise isolate the custom registry and its cleanup policy behind
a focused owner. Declare the exposed dependency as `ReadingRepository`.

**Caution:** Do not replace the registry mechanically: the README explicitly
describes retention across activity destruction within the process.

### 7. Test repository duplicates production save orchestration

**Evidence**

- `app\src\test\java\com\trackgas\app\TrackerViewModelTest.kt:415-456`
  implements target lookup, missing-target handling, ID creation, original-date
  preservation, validation, replacement, sorting, and deletion in the fake.
- `app\src\main\java\com\trackgas\app\data\RoomReadingRepository.kt:38-63`
  contains the corresponding production orchestration.
- `app\src\androidTest\java\com\trackgas\app\data\RoomReadingRepositoryTest.kt:42-100`
  provides useful direct coverage of the real implementation.

**Why it matters:** ViewModel tests rely on a separately maintained implementation
of repository policy. A change to one implementation can leave the other
incorrectly modeling production behavior. Real repository tests mitigate this
risk; the fake itself is not evidence that the suite is invalid.

**Recommendation:** Use a scripted repository double for tests concerned only with
ViewModel responses to outcomes. For tests needing realistic behavior, share
storage-independent policy where appropriate or exercise both implementations
against a common behavioral contract. Keep Room transaction/concurrency tests
against the real database.

## SOLID assessment

| Principle | Assessment |
| --- | --- |
| Single Responsibility | Main improvement area: UI coordination and the policy/persistence boundary combine independently changing concerns. |
| Open/Closed | No compelling current violation. Exhaustive `when` expressions over a small, closed set of periods/errors are appropriate; replacing them with strategy hierarchies is not justified by hypothetical extensions. |
| Liskov Substitution | No demonstrated violation. The repository has an implementation and a fake, but different test/infrastructure behavior alone does not establish a broken substitution contract. |
| Interface Segregation | The three-method repository interface is cohesive and small. Numerous screen callbacks signal coordination complexity, not automatically an ISP violation. |
| Dependency Inversion | The ViewModel already depends on `ReadingRepository`, not Room. Improvements concern the abstraction's responsibilities and concrete exposure at the composition boundary, not a missing abstraction everywhere. |

## What is already clean

- Domain invariants are enforced at construction in `Reading`, `ReadingInterval`,
  `DateRange`, `ConsumptionAmount`, and `CalendarPeriod`.
- Consumption arithmetic is separated from rendering, preserves decimal precision,
  and distinguishes unknown coverage from known zero.
- Chart projection is isolated from the Vico renderer.
- Date validation is reused by picker feedback and save validation.
- Writes validate and commit transactionally; cancellation is not swallowed.
- Focused domain, ViewModel, persistence, and UI tests already exist.

## Suggested order

1. Make dialog/operation transitions explicit, then extract focused UI components.
2. Consolidate and narrow error classification while preserving recovery behavior.
3. Clarify the repository/use-case boundary and its test-double contract together.
4. Simplify snapshot publication and lifecycle ownership only after preserving
   their existing completion, restoration, and retention guarantees.

Avoid adding a DI framework, a module per layer, or an interface per class solely
to satisfy SOLID terminology. Focused changes at these boundaries are proportionate
to the current application.
