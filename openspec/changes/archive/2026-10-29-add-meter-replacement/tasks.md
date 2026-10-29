# Tasks

Sections 1-6 record the original completed delivery, including its superseded explicit-baseline input. Section 7 implements the revised automatic-zero replacement workflow; the current proposal, specs, and design govern that revision.

## 1. Installation-aware domain and reading policy

- [x] 1.1 Add immutable installation and replacement models, installation identity on `Reading`, and a full tracker aggregate in `CommittedSnapshot`; adapt existing fixtures/fakes and verify existing single-installation assertions preserve identities and exact decimal scale.
- [x] 1.2 Extend `ReadingCollection` validation to installation/date uniqueness, same-installation monotonicity, one connected chronological replacement chain, endpoint references/dates/roles, lifetime ranges, and one current installation; extend `ReadingCollectionTest` with valid lower replacement values and invalid chains and verify targeted JUnit cases.
- [x] 1.3 Add the shared date-to-installation resolver and update Add/Edit policy and picker feedback to use it, preserving immutable edit membership and existing error precedence; extend `ReadingValidationTest` and `ReadingPolicyTest` for backdated historical entry, occupied replacement dates, same-date counterpart editing, and ordinary decreases, then verify those tests.
- [x] 1.4 Add a dedicated replacement intent, pure policy, explicit prerequisite/duplicate/obsolete/stale-date results, and protected-boundary deletion policy; add JUnit coverage for missing final reading, invalid/zero/nonzero baseline, expected final value changes, date changes, repeated same-day replacement, and subsequent-day replacement.

## 2. Consumption across installations

- [x] 2.1 Update interval construction to pair only readings in the same installation, reject mixed-installation pairs, and merge non-overlapping intervals in chronological order; extend `ConsumptionTest` for the agreed October 27-29 example, zero and nonzero baselines, baseline-only new meters, and multiple replacements, verifying 5 on October 28, 3 on October 29, and exact total 8.
- [x] 2.2 Extend `DashboardTest` and `ChartProjectionTest` for replacement across week/month/year boundaries, skipped new-meter days, exact whole-interval totals, known zero versus unknown, and endpoint edits/deletions; verify chart, list, and total use identical covered buckets without cross-meter subtraction.
- [x] 2.3 Update README's calculation section with installation-local arithmetic, `(A, B]` replacement-day attribution, and the agreed example; verify the text distinguishes explicit initial values from assumed zero and does not promise sub-day accuracy.

## 3. Durable schema and atomic repository operations

- [x] 3.1 Add Room v2 installation/replacement entities, restrictive foreign keys, composite reading-date uniqueness, full-aggregate queries/invalidation, and original-installation initialization; generate and inspect the v2 schema while retaining v1, and extend `ReadingDatabaseTest` to verify allowed cross-installation endpoint dates and rejected same-installation duplicates.
- [x] 3.2 Implement and register the explicit v1-to-v2 migration in database construction, preserving `readings.db`, all original IDs/dates/decimal text, and one seeded original installation; add file-backed migration instrumentation fixtures using existing infrastructure and verify empty/populated upgrades, schema constraints, unchanged consumption, and cold reopen without destructive fallback.
- [x] 3.3 Extend `RoomReadingRepository` to transactionally read/validate the complete tracker and atomically replace a meter under the existing ordering mutex; extend `RoomReadingRepositoryTest` to verify committed boundary/baseline state, exact decimals, rollback on failure/cancellation, missing/stale final targets, same-day duplicate replacement, and boundary deletion protection.
- [x] 3.4 Preserve revision and observation guarantees for all three collections, including transactionally consistent capture and metadata-only invalidation; extend `OrderedRepositoryTest` for competing replacements, edit/delete races, stale Add drafts, delayed observation, and non-interleaved snapshots, verifying at most one competing replacement commits.
- [x] 3.5 Extend stored-data decoding/failure coverage to invalid installation references, broken chains, invalid endpoints, and same-installation decreases while accepting valid cross-meter decreases; verify existing failure tests still distinguish stored-data from storage-access errors and do not catch cancellation or unrelated defects.
- [x] 3.6 Extend `ReadingPersistenceTest` for save-final, replace, add-next-day, close/reopen, edit-endpoint, and protected-deletion sequences; verify installation membership, current installation, old history, and consumption survive offline reopening.

## 4. Session and mutation-state integration

- [x] 4.1 Extend `ReadingDialog`, replacement draft retention, and `TrackerViewModel` open/submit/completion behavior with session/dialog/request identity and a current-date source; extend `TrackerViewModelTest` for pending locking, invalid/obsolete/stale-date outcomes, preserved drafts on failure, and matching completion only.
- [x] 4.2 Accept/project full tracker snapshots and compare installations/boundaries as well as readings for deduplication; extend ViewModel tests for metadata changes, repeated identical data, stale revisions, independent observation failure after confirmed replacement, and unchanged tab/period.
- [x] 4.3 Wire replacement and protected-delete callbacks through `MainActivity` and `TrackerScreen`, including missing-final-to-Add routing without automatic replacement; extend lifecycle instrumentation to verify retained pending operations on recreation and fresh-process restoration without old drafts or duplicate mutations.

## 5. Options menu, replacement form, and History

- [x] 5.1 Add the gear vector and localized options/menu labels using existing resource patterns, place the menu button immediately left of Add on both tabs, and expose only Replace meter; add Compose UI coverage for action order, labels, 48 dp targets, menu dismissal, navigation preservation, and loading/pending mutation gating.
- [x] 5.2 Implement the replacement modal with today's read-only date, displayed saved final value, blank new baseline, explicit confirmation, cancellation, prerequisite/unavailable explanations, inline validation, storage retry, and current-date rejection; extend `TrackerScreenPersistenceUiTest` or a focused replacement UI class and verify valid, cancelled, failed, pending, restored, and repeated same-day flows.
- [x] 5.3 Update History with localized installation/endpoint labels, deterministic same-date ordering, disambiguated accessibility actions, endpoint value editing, and explained deletion protection; add UI cases verifying two labelled replacement-day rows, valid baseline correction, blocked endpoint deletion, and unchanged ordinary delete confirmation.
- [x] 5.4 Extend existing layout instrumentation for replacement/menu at 360 dp and 200% font, landscape with keyboard, system insets, reachable scrolling, and non-pending outside dismissal; verify pending forms cannot dismiss and existing Add/Edit/pager interactions remain intact.
- [x] 5.5 Update README's reading-management and local-storage sections with the gear workflow, save-final-first prerequisite, explicitly entered initial value, durable installations, and today's-only/one-per-day/no-undo limits including protected endpoints; review against delivered UI labels and behavior.

## 6. End-to-end integration

- [x] 6.1 Run the existing Gradle unit-test runner with related policy, collection, consumption, dashboard, chart, failure, and ViewModel selectors together; then run the existing connected instrumentation runner for changed database, migration, repository, persistence, lifecycle, replacement, and layout classes on the configured emulator, verifying the complete agreed scenario and existing single-meter behavior.
- [x] 6.2 Generate the debug APK with `.\gradlew.bat :app:assembleDebug` and exercise the offline flow end-to-end: save 1100 on October 27, save final 1105 on October 28, confirm baseline 0 on October 28, add 3 on October 29, inspect both daily bars and total 8, then reopen the app and confirm identical history and consumption without changing selected-period behavior.

## 7. Automatic zero-baseline replacement

- [x] 7.1 Remove replacement value input from the domain intent, policy, repository results, and ViewModel submission. Automatically commit a zero baseline after validating today's saved final target; update policy, session, and persistence coverage while retaining existing stored nonzero baselines and editable endpoints.
- [x] 7.2 Remove the new-meter field, input errors, and replacement draft from the confirmation UI; wire parameterless confirmation, update localized explanations and README, and adapt UI/layout/lifecycle coverage for retry, pending restoration, and missing-final routing without value entry.
- [x] 7.3 Verify the no-input replacement flow, next-day reading 5 producing exactly 5 - 0 = 5, skipped days, and offline reopening with targeted unit and connected instrumentation suites; regenerate the debug APK.
