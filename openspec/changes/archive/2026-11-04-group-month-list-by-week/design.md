## Context

`CalendarPeriod.buckets()` yields daily buckets for Week and Month; `dashboardItems` in `DashboardContent.kt` renders one row per `dashboard.buckets` entry. `chartProjection` consumes the same daily buckets. Only the list needs to change.

## Goals / Non-Goals

**Goals:** compact Month list (≤6 rows) with exact weekly sums; unchanged chart, tooltip, total, and Week/Year lists.

**Non-Goals:** changing interval arithmetic, `Dashboard`/`ConsumptionBucket` data, or the chart.

## Decisions

- **Group at presentation time, not in `Dashboard`.** Add a pure `weeklyRows(period, buckets)` function (next to `ChartProjection.kt`) that groups covered daily buckets by Monday-based week, clipped to the month, and sums `cubicMetres` as `BigDecimal` without rounding. Rationale: keeps the domain/chart contract ("chart uses same buckets") intact and is easily unit-tested. Alternative of adding a weekly period type to `CalendarPeriod.buckets()` was rejected because it would affect the chart and allocation.
- **Row label** is a localized date range (e.g. "Oct 1 – Oct 4", `MMMd` skeleton via `DateFormat.getBestDateTimePattern`), reusing the existing `period_range` string where it fits; a single-day clipped week shows one date.
- **Partial/uncovered weeks:** sum covered days only; omit weeks with none. Known zero stays visible as zero. Estimated flag handling follows the existing `ConsumptionValue` display; if any day in a week is estimated, the row follows the same presentation (no new labels, per "Minimal consumption presentation").
- **Row keys** use the week's clipped start date (`"week-<start>"`).
- **Daily detail** remains reachable through the existing chart long-press tooltip.

## Risks / Trade-offs

- Users lose a direct per-day number list in Month → mitigated by tooltip; Week view still shows daily rows.
- Summing `ConsumptionAmount` must not mix null/uncovered values → only covered buckets (non-null `cubicMetres`) are summed.
- Locale week start: spec fixes Monday–Sunday, consistent with `CalendarPeriod`.

## Open Questions

None blocking.
