# Tasks

## 1. Meter start and consumption rules

- [x] 1.1 Allow an empty tracker, enforce that each persisted meter has readings, and use one domain policy for creating a meter with its first reading from either first Add or replacement; verify empty-state, start-value validation, duplicate-date, and stale-replacement cases with `./gradlew :app:testDebugUnitTest --tests com.trackgas.app.ReadingCollectionTest --tests com.trackgas.app.ReadingValidationTest --tests com.trackgas.app.MeterReplacementPolicyTest`.
- [x] 1.2 Remove replacement-only opening consumption so every meter's first reading is a baseline, and verify initial/replacement baselines plus later deltas with `./gradlew :app:testDebugUnitTest --tests com.trackgas.app.ConsumptionTest --tests com.trackgas.app.DashboardTest --tests com.trackgas.app.ChartProjectionTest`.

## 2. Persisted meter lifecycle

- [x] 2.1 Remove the new-database empty-installation seed without changing the Room schema or version, and verify a fresh database starts with no meters.
- [x] 2.2 Commit meter creation and its starting reading atomically; delete a meter with its last reading; make purge leave no meters; and reconcile CSV imports with the non-empty-meter invariant. Verify policy, view-model, and import behavior with unit tests; no emulator tests.

## 3. Forms and tracker state

- [x] 3.1 Update Add, Replace, and tracker state so first Add creates the initial meter and replacement collects its starting value through the shared start policy; verify first-meter, no-meter replacement, and replacement retry behavior with `./gradlew :app:testDebugUnitTest --tests com.trackgas.app.TrackerViewModelTest --tests com.trackgas.app.MeterReplacementPolicyTest`.
- [x] 3.2 Add localized starting-value labels and replacement validation/empty-state copy in English and German; verify Android resources and Compose compile with `./gradlew :app:assembleDebug`, keeping German copy informal.

## 4. Integration verification

- [x] 4.1 Verify the domain lifecycle—first reading as a non-consuming baseline, later interval usage, replacement starting value excluded from statistics, and empty state after deletion or purge—with `./gradlew :app:testDebugUnitTest :app:assembleDebug`; inspect Room transaction paths because no repository integration harness is available, and omit emulator tests per user request.
- [x] 4.2 Distinguish dates before the first meter's start date from duplicate reading dates in live picker and save validation, localize the feedback, and cover the distinction with unit tests.
- [x] 4.3 Ensure replacement captures observation time at the precision accepted by starting-reading validation, with a ViewModel regression test.
- [x] 4.4 Visually distinguish persisted meter starting readings in History with localized labeling and test which entries qualify.
