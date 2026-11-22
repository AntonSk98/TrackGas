# Proposal

## Why

The app currently loads and projects the complete reading history before the first Dashboard is ready, even though Dashboard only displays one selected period. Large histories also make the History tab pay the cost of constructing and retaining every row at once. Load only the data each screen needs for its presentation so the initial Dashboard does not wait for full history, while keeping every reading accessible.

## What Changes

- Replace the startup full-tracker observation with a Dashboard-period data request containing the readings needed to calculate all intervals overlapping that period, including intervals with endpoints outside it.
- Request Dashboard data again when the selected period changes, and publish results only for the latest period and committed data.
- Load History on demand in stable, newest-first pages; fetch further pages as the user approaches the end, without duplicates or gaps.
- Include neighboring-reading context for Dashboard interval boundaries and History 24-hour estimates so filtering and pagination preserve existing calculations.
- Scope temperature-cache reads and best-effort backfills to the active Dashboard period, loading historical weather when a user selects that period.
- Keep complete-history operations such as CSV export available by loading the full data only when those operations are requested. Preserve existing mutation validation, chart semantics, and recoverable loading errors.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `gas-consumption-tracking`: Dashboard startup and period changes use period-scoped data; History exposes all readings through on-demand pagination while preserving ordering, estimates, and mutation behavior.
- `average-temperature`: Dashboard cache reads and synchronization use only the selected period and required reading boundaries.

## Impact

Likely changes include `ReadingRepository`, Room DAO queries and `RoomReadingRepository`, `TrackerViewModel` state/loading flows, Dashboard and History UI, temperature/history projections, and repository, projection, and UI tests. No persisted data format change is intended.
