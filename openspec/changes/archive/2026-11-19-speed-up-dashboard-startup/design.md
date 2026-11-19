# Design

## Context

See proposal.md - Why. `RoomReadingRepository.observeReadings()` produces a complete committed snapshot. `TrackerViewModel` accepts it and synchronously sorts readings, derives temperature and dashboard projections, and publishes READY afterward. Dashboard composition separately derives daily rates from the tracker, which repeats interval construction. `TrackerScreen` hides the dashboard while readings are loading.

The full snapshot is also needed by History and reading mutations, and a consumption interval may begin before the selected period. The optimization must therefore preserve complete history and exact projection semantics.

## Goals / Non-Goals

**Goals:**
- Keep initial full-snapshot projection from blocking UI responsiveness.
- Avoid repeating full-history interval derivation for related dashboard values.
- Publish initial results only for the latest committed snapshot and observation.
- Preserve existing loading/error behavior, full-history availability, and chart values.

**Non-Goals:**
- Limit or downsample stored readings, or load only the visible period.
- Display a cached, partial, or stale chart while the current snapshot is loading.
- Change the storage schema, repository contract, chart semantics, or weather synchronization.

## Decisions

1. **Measure projection work locally before and after the change.** Use the representative year fixture in a local JVM harness to time dashboard and daily-rate derivation, excluding Gradle startup, storage delivery, and chart rendering. This provides a comparable projection baseline without emulator tests; end-to-end launch-to-chart behavior remains for the user to verify on a device.

2. **Prepare the initial projection off the UI thread.** Keep Room snapshot capture on its existing storage path. On an injected background dispatcher, derive ordered history, reading intervals, dashboard buckets, and daily rates from one immutable snapshot. Reuse interval derivation for subsequent projections too, and keep full-history traversal out of composable rendering. Period navigation and later committed mutations remain synchronous; the local projection profile does not justify adding asynchronous state transitions to those already-loaded paths.

   Alternatives considered: restricting database reads to the selected period would be cheaper but can omit the earlier reading needed to calculate covered consumption and would break full History availability. Persisting a projection cache adds invalidation and consistency complexity without evidence that it is needed. Moving work off-thread without eliminating redundant derivation could leave avoidable startup cost.

3. **Guard asynchronous initial results.** Associate each request with the snapshot revision, observation generation, and a monotonically increasing projection generation. Publish on the main thread only if they still match the latest inputs. Keep the initial load state unresolved until the complete current projection is ready; never publish an older result after a retry or newer request.

4. **Preserve projection semantics and storage boundaries.** Reuse the current reading validation, interval allocation, and chart projection rules. Keep repository APIs and persisted data unchanged; projection failures must continue through explicit existing error handling rather than yielding success-shaped empty data.

## Risks / Trade-offs

- [A background computation can finish after an observation is retried] → Check projection generation, observation generation, and snapshot revision before publishing.
- [Refactoring shared interval derivation can change subtle coverage, ordering, or decimal behavior] → Compare existing dashboard and daily-rate outputs against the current implementation in unit tests, including gaps and meter replacements.
- [The measured bottleneck may be storage delivery rather than projection] → Keep storage unchanged and use the user's device test to determine whether further optimization is warranted.
- [A complete history still requires work proportional to reading count] → Preserve correctness and measure representative sizes; do not silently cap or omit readings.
- [JVM projection timings do not predict Android launch-to-chart latency] → Treat them only as a local comparison and verify end-to-end startup on the user's device.

## Migration Plan

No persisted data or schema migration is required. Implement and verify the projection changes behind existing loading/error states; rollback consists of reverting the projection path while retaining the unchanged repository and database format.
