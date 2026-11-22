# Design

## Context

See proposal.md - Why. The startup path currently observes a complete `CommittedSnapshot`; Room first materializes all observed readings, then the repository reads them again inside a transaction. `TrackerViewModel` retains that tracker for both Dashboard and History, and History derives estimates and starting-reading markers from the complete list.

Consumption intervals cover `(earlier.date, later.date]`. A Dashboard period therefore needs readings inside the selected range plus per-installation neighbors where an interval can cross either boundary. The readings table has a unique `(installationId, date)` index but no global date index; the database is version 7 with migrations through 6-to-7.

TemperatureSync and the chart cache path previously loaded complete reading and temperature histories in the background. Although they did not gate the initial reading projection, their database work could contend with startup and period projection, especially for large histories.

## Goals / Non-Goals

**Goals:**
- Keep the initial Dashboard query and retained presentation state proportional to the selected period rather than all reading history.
- Page History data from storage, preserving order, estimates, starting-value markers, and retry behavior.
- Keep projection results consistent with the currently selected period and latest committed changes.
- Keep temperature-cache reads and weather backfills bounded to the selected period, including only boundary readings needed for interval calculations.
- Preserve existing consumption arithmetic, transactional mutation validation, CSV behavior, and stored reading data.

**Non-Goals:**
- Change CSV format or optimize full-tracker work performed by explicitly requested import/export or existing mutation transactions.
- Cap the amount of history a user can reach or change chart semantics.

## Decisions

1. **Separate period projections from complete-history access.** Add a repository path for Dashboard-period data and use it for initial loading, period navigation, and committed-data refreshes. Under one consistent database read, fetch all readings within the selected inclusive date range, the nearest reading before the range and after it for each installation, and installation metadata. This supplies every interval overlapping the period without loading unrelated history. Keep complete-history operations on their existing paths.

   Alternatives considered: filtering only by dates inside the period loses intervals whose endpoints lie outside it; retaining the full tracker for the screen preserves current behavior but does not reduce startup work.

2. **Use keyset pagination for History.** Fetch the first page only when History is selected, then request the next page as the user approaches the loaded end. Use a fixed internal page size and a cursor matching the visible newest-first ordering (date, installation order, and a stable identity tie-breaker). Offset pagination is simpler but can skip or repeat rows when readings are inserted or deleted between requests.

   Each page also needs limited per-installation neighbor context: a chronological predecessor for eligible 24-hour estimates and the persisted starting-reading identity for its marker. This keeps History details correct without loading all rows.

3. **Keep Dashboard and History request state independent.** Track loading, continuation, and failure separately. Associate results with their period or cursor and a committed-data/request generation; discard stale results after navigation, a newer snapshot, or a retry. Preserve already loaded History pages after a later-page failure and retry from the last successful cursor.

4. **Index the new access patterns without changing reading data.** Add a non-unique global date index for period filtering and newest-first page queries. Retain the existing unique per-installation date index for neighbor lookups and validation. Add the non-destructive 7-to-8 migration and the temperature-cache indexes in the 8-to-9 migration.

5. **Scope temperature work to Dashboard periods.** Read interval means by the selected period's later-reading identities and daily means by location/date range. Observe cache row counts as invalidation signals instead of emitting full cache lists. TemperatureSync requests the current week at startup, uses the latest selected period for connectivity retries, and checks historical periods when Dashboard navigation selects them. Validate cache writes with targeted database existence queries rather than materializing all readings.

## Risks / Trade-offs

- [Boundary selection omits an endpoint or includes an interval from another installation] → Test intervals starting before and ending after the selected range, replacement meters, uncovered periods, and equivalence with full-history projections.
- [Page cursors do not match the rendered comparator] → Define one stable ordering for SQL and UI, and test equal-date readings across installations with insertion and deletion between page requests.
- [Estimates or starting markers are wrong at page boundaries] → Fetch only the required per-installation neighbor/marker context and compare page projections with full-history projections.
- [Independent loading states create stale or success-shaped results] → Keep request generations and explicit retry/error states; do not treat failed or missing pages as empty history.
- [The unchanged background temperature worker still reads all history or competes for database time] → Keep it off the Dashboard readiness path and validate startup readiness separately from its best-effort work.
- [A global date index adds migration work] → Use a non-destructive migration and verify existing rows remain intact.
- [Scoped temperature reads omit an interval crossing a period boundary] → Include the nearest reading before and after the selected range and query means by the later endpoint.
- [Historical periods no longer backfill at startup] → Request a check whenever the selected Dashboard period changes.

## Migration Plan

Add the Room indexes through non-destructive versioned migrations, then introduce the scoped repository queries and independently loaded Dashboard/History state. No reading rows are transformed or deleted. Rollback is a normal code rollback; the added indexes can remain harmlessly in the database, and no destructive migration or data reset is required.
