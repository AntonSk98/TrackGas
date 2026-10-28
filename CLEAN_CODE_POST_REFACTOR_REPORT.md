# Clean Code and SOLID Review: Current Working Tree

Date: 2026-10-28

## Summary

The code is substantially cleaner than the hotspots described in
`CLEAN_CODE_REPORT.md`. The three follow-up concerns identified in this review
have now been addressed: collection-level storage validation, an explicit
repository API contract, and type-safe validation outcomes/identity arguments.
No architecture rewrite or database migration was needed.

The original static review covered all 29 handwritten production Kotlin files, application
resources/configuration, Gradle configuration, README architecture documentation,
and representative unit/instrumentation tests. It includes uncommitted and
untracked refactoring files; generated files and build outputs are excluded.
The numbered findings below preserve that pre-follow-up review, including its
original line references and code-path deductions; they are not outstanding
defects in the updated implementation. The resolution and execution evidence
below describe the completed follow-up. The original report remains untouched.

| ID | Priority | Finding | Principle |
| --- | --- | --- | --- |
| 1 | Medium | Stored snapshots validate individual rows but not their relationships | Boundary validation, invariant ownership |
| 2 | Low | Repository correctness depends on an ordering contract absent from its API | Explicit contracts, dependency inversion |
| 3 | Low | Validation types allow contradictory outcomes and conditional arguments | Encapsulation, type-safe API design |

Priority denotes follow-up urgency, not vulnerability severity.

## Follow-up outcome

| Finding | Status | Implementation evidence |
| --- | --- | --- |
| 1 | Addressed | `tracking\ReadingCollection.kt:3-18` defines shared identity/date/order checks. `data\RoomReadingRepository.kt:68-74` applies them before snapshots or mutations and translates only identified violations to stored-data failures. `tracking\Consumption.kt:16-23,37-42` uses the same checks. |
| 2 | Addressed | `tracking\ReadingRepository.kt:24-74` documents committed snapshot validity/ownership, revision lifetime and ordering, resubscription, atomic validation/write behavior, failure categories, and cancellation. |
| 3 | Addressed | `tracking\ReadingValidation.kt:16-23,34-70` provides Accepted/Rejected variants, rejects empty rejection errors, and exposes typed Add/Edit entry points. `data\RoomReadingRepository.kt:35-48` handles the variants exhaustively inside the transaction. |

Paths in this table are relative to
`app\src\main\java\com\trackgas\app`.

The follow-up passed 63 targeted unit tests covering collection invariants,
consumption/dashboard/chart projection, parsing, policy, failure classification,
and ViewModel orchestration. It also compiled the Android tests and passed 17
connected tests on the Android 15/API 35 emulator: `RoomReadingRepositoryTest`
(10), `OrderedRepositoryTest` (6), and `ReadingPersistenceTest` (1), with no
failures, errors, or skips.

The new regressions cover decreasing stored rows during load/Add/Edit/Delete,
repeated non-destructive retry, unchanged records, no revisions for rejected
stored collections, retained projections/dialogs, and rejected empty validation
errors. Existing ordering, cancellation, rollback, Add-only identity generation,
decimal-scale, and persistence cases remain covered. This is targeted acceptance,
not a claim that the full suite or manual UI checklist was rerun.

Room stays at version 1; schema, stored format, UI resources/behavior, permissions,
privacy policy, and revision ordering are unchanged by this follow-up.

## 1. Stored snapshots do not enforce collection-level invariants

**Evidence**

- `app\src\main\java\com\trackgas\app\data\ReadingDatabase.kt:25-38`
  rejects blank identities, malformed dates/numbers, and negative values per row.
- `app\src\main\java\com\trackgas\app\data\RoomReadingRepository.kt:63-65`
  maps rows and publishes a snapshot without checking chronological relationships.
- `app\src\main\java\com\trackgas\app\tracking\Consumption.kt:16-20,36-38`
  requires cumulative values to be nondecreasing across chronological readings.
- `app\src\main\java\com\trackgas\app\tracking\TrackerViewModel.kt:63-74,154-165`
  projects repository snapshots but handles only `ReadingStorageException`.

**Impact:** Two individually valid stored rows can still be invalid together.
For example, October 1 = 120 and October 2 = 100 both decode successfully, but
dashboard projection throws `IllegalArgumentException`. That exception bypasses
the recoverable stored-data error path. Normal repository saves prevent this
combination; the gap concerns inconsistent existing data, not ordinary valid
input. The schema's unique date index does not enforce cumulative ordering.

**Recommendation:** Validate persisted collection invariants at the repository
read boundary and report specifically identified stored-data violations through
the existing stored-data exception/category. Reuse or extract the relevant domain
checks rather than maintaining a second policy. Do not broadly catch
`IllegalArgumentException` around the ViewModel or whole transaction: programming
defects should still propagate.

**Acceptance criterion:** Individually valid but decreasing persisted readings
produce a `STORED_DATA` load failure instead of escaping from projection. Recovery
must remain non-destructive.

This is a boundary-ownership gap, not evidence that the entire ViewModel violates
Single Responsibility.

## 2. The repository API leaves its temporal contract implicit

**Evidence**

- `app\src\main\java\com\trackgas\app\tracking\ReadingRepository.kt:24-31`
  exposes a revision and three methods without documenting their guarantees.
- `app\src\main\java\com\trackgas\app\tracking\TrackerViewModel.kt:39,154-161`
  retains a revision watermark and rejects all snapshots at or below it.
- `app\src\main\java\com\trackgas\app\data\RoomReadingRepository.kt:22-29,45-48,65`
  serializes captures/mutations and allocates revisions after successful commits.
- `README.md:184-194` explains the intended ordering, and
  `app\src\androidTest\java\com\trackgas\app\data\OrderedRepositoryTest.kt:31-110`
  exercises its Room implementation.

**Impact:** The behavior is documented and deliberately implemented, but not at
the abstraction on which the consumer depends. A future adapter that resets its
counter on resubscription, or uses incomparable counters for observations and
mutation results, could silently suppress valid updates. Correct method
signatures alone are insufficient to implement this interface safely.

**Recommendation:** Add API-level KDoc defining the revision domain/lifetime,
freshness ordering, immutable snapshot expectation, committed-success semantics,
atomic validation/write behavior, expected failures, and cancellation behavior.
If another production implementation is introduced, extract reusable behavioral
contract tests from the existing ordering scenarios.

**Acceptance criterion:** An implementer can determine the required guarantees
from the interface without reverse-engineering Room or reading the ViewModel.

This is an abstraction-contract weakness, not a demonstrated Liskov violation:
the current production adapter implements the intended ordering.

## 3. Validation exposes combinations that should not be constructible

**Evidence**

- `app\src\main\java\com\trackgas\app\tracking\ReadingValidation.kt:16-17`
  represents a result as nullable `candidate` plus independent `errors`.
- `app\src\main\java\com\trackgas\app\tracking\ReadingValidation.kt:29-39`
  accepts an optional `addId`, then requires it only for Add.
- `app\src\main\java\com\trackgas\app\data\RoomReadingRepository.kt:35-37`
  repeats the Add check and treats any missing candidate as invalid.

**Impact:** `ReadingValidation(null, ReadingErrors())` and a candidate accompanied
by errors are legal constructions despite contradicting the result's documented
meaning. Likewise, an Add call can omit its required identity and compile.
The existing evaluator constructs consistent outcomes, so this is a
maintainability risk rather than a demonstrated current failure.

**Recommendation:** Represent validation as explicit accepted/rejected variants,
with an accepted variant containing a non-null reading and a rejected variant
requiring at least one error. Make Add identity provision explicit in a
type-appropriate entry point or resolved policy command. Keep database identity
generation and final policy evaluation inside the transactional orchestration;
do not move authoritative validation into the UI.

**Acceptance criterion:** Consumers handle exhaustive success/failure variants
instead of inferring success from a nullable field, and Add cannot accidentally
omit its identity.

## Improvements visible in the current code

| Earlier concern | Current assessment |
| --- | --- |
| Oversized screen/form coordination | Focused `TrackerPager`, `ReadingLoadContent`, `DeleteReadingDialog`, and `ReadingFormLayout` now isolate major responsibilities. |
| Distributed semantic dialog state | `ReadingDialog`, action-specific phases, and correlated completion helpers make ownership and legal transitions much clearer. |
| Raw text/locale in the repository | `parseMeter` runs before repository entry; `SaveReadingIntent` carries typed input and shared pure policy handles final validation. |
| Broad exception reclassification | Central classification distinguishes stored data from storage access and leaves unrelated defects/cancellation unchanged. |
| Competing snapshot deliveries | A single revision-aware reducer handles both paths, avoids redundant projections, and does not restart healthy observation after mutations. Keep these guarantees rather than removing a path mechanically. |
| Application lifecycle bookkeeping | `TrackerStoreOwner` isolates the custom retention policy; the application exposes `ReadingRepository`. Its nonstandard lifetime is explicitly justified in the README. |
| Policy-heavy ViewModel test fake | `ScriptedReadingRepository` scripts outcomes without duplicating save policy. |

Generic user-facing storage messages are intentional (`README.md:176-182`).
Keeping safe failure categories without displaying exception details is not
itself a dirty spot or a reason to introduce logging of stored readings.

## SOLID assessment

| Principle | Assessment |
| --- | --- |
| Single Responsibility | Generally good after extraction. Persisted collection validation now has an explicit boundary and shares domain checks with consumption. |
| Open/Closed | No concrete violation found. Exhaustive branches over a small closed domain do not justify strategy hierarchies. |
| Liskov Substitution | No demonstrated violation. The temporal repository contract is now explicit for future adapters. |
| Interface Segregation | The three-method repository is cohesive. A screen's numerous event callbacks alone do not establish an ISP violation. |
| Dependency Inversion | The ViewModel depends on the documented `ReadingRepository` contract; Room construction remains at the composition boundary. |

All three findings are addressed. No additional DI framework, generic repository
hierarchy, or service layer is justified by these findings.
