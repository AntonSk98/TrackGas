# Tasks

## 1. Establish baseline and projection coverage

- [x] 1.1 Add a repeatable tracker fixture with about one year of readings and tests for exact weekly, monthly, and yearly dashboard values, including gaps and a meter replacement; verify the expectations with the focused projection tests.
- [x] 1.2 Measure dashboard and daily-rate projection locally using the one-year fixture and a temporary JVM harness; verify the baseline excludes Gradle startup, storage, and chart rendering.

## 2. Reuse full-history derivations

- [x] 2.1 Refactor dashboard and daily-rate preparation to share validated reading intervals for the same tracker and period; verify exact results with existing consumption tests and the new representative-history tests.

## 3. Prepare the current projection asynchronously

- [x] 3.1 Move initial full-snapshot projection to an injected background dispatcher and remove full-history derivation from composable rendering; verify a focused ViewModel unit test keeps the main dispatcher usable while projection is pending and publishes correct values when complete.
- [x] 3.2 Guard initial projection publication by snapshot revision, observation generation, and request generation; verify stale observation results cannot replace the current complete projection or mark loading complete prematurely.

## 4. Verify startup improvement

- [x] 4.1 Repeat the same local JVM projection measurement and run the focused projection and ViewModel unit tests; verify projection work improves without stale/partial data or changed chart values.

## Workflow follow-up

- Verify end-to-end launch-to-chart timing and responsiveness on a physical device; local JVM measurements cover projection work only.
