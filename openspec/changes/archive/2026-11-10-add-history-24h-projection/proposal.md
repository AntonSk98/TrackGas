# Proposal

## Why

Readings taken at different times can span substantially more or less than 24 hours, making their consumption differences difficult to compare. A compact, timestamp-based 24-hour estimate in History makes those intervals comparable without changing calendar-based Dashboard consumption.

## What Changes

- Show a normalized 24-hour consumption estimate on each reading with a preceding reading in the same meter installation, including intervals shorter than, equal to, or longer than 24 hours.
- Calculate the estimate from the actual observation instants: meter difference multiplied by 24 hours and divided by elapsed time. November 6 at 08:00 to November 7 at 23:00 is 39 hours.
- Keep the estimate visually secondary beside the raw meter value, clearly marked as approximate and expressed in cubic metres per 24 hours.
- Recalculate from committed readings after additions, edits, deletions, imports, and reloads; omit the estimate for installation baselines.
- Preserve Dashboard allocation, weather calculations, reading precision, storage, and CSV formats. Use existing JVM testing and build tooling; no emulator tests.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `gas-consumption-tracking`: Permit compact per-reading 24-hour estimates in History, define elapsed-time normalization and baseline behavior, and preserve the distinction from calendar consumption.

## Impact

- Tracking domain: reuse installation-local `ReadingInterval` pairing in `Consumption.kt` and add a pure derived calculation, without modifying calendar allocation.
- History UI: extend `ReadingHistory.kt` with secondary metadata in its existing wrapping value row.
- Presentation: reuse `ReadingFormat.kt` and add matching English/German string resources.
- Coverage: targeted JVM domain, formatting, localization, and existing consumption regression tests. No new dependencies, database migration, background jobs, or README expansion.
