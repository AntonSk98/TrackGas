# Proposal

## Why

On launch, the dashboard remains on “Loading readings…” until the initial committed snapshot has been loaded and its projections are calculated. The current path derives reading intervals and dashboard values across the full history, with additional period calculations before the chart can appear; reducing this startup work will make the chart available sooner without sacrificing complete, accurate history.

## What Changes

- Reduce the time from app launch to the first usable dashboard chart by avoiding redundant full-history projection work and keeping CPU-heavy projection from blocking UI responsiveness.
- Preserve the full committed reading history, existing chart values, and loading/error behavior; do not show a partial or stale chart while the current snapshot is being prepared.
- Verify startup behavior and performance with a representative history of about one year, and establish a baseline before setting any numeric target.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `gas-consumption-tracking`: Specify that startup loading should prepare the full, accurate dashboard chart promptly and remain responsive while doing so.

## Impact

Likely areas include `TrackerViewModel`, reading interval and dashboard projection code, dashboard chart rendering, and related tests. No storage format or public API change is intended.
