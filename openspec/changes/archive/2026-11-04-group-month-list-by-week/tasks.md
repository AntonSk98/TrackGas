## 1. Weekly grouping logic

- [x] 1.1 Add a pure `weeklyRows` function (near `ChartProjection.kt`) grouping covered daily buckets into Monday–Sunday weeks clipped to the month, summing unrounded `BigDecimal` amounts and omitting uncovered weeks
- [x] 1.2 Unit-test it: fully covered 31-day month (≤6 rows, sums equal total), partial week, known-zero week, clipped first/last weeks, uncovered weeks omitted, month starting on Monday/Sunday

## 2. Dashboard UI

- [x] 2.1 In `DashboardContent.kt`, render weekly rows for `PeriodType.MONTH` and keep daily/monthly rows for Week/Year, with `week-<start>` keys
- [x] 2.2 Add localized week range label (string resources and formatter) incl. single-day clipped weeks
- [x] 2.3 Verify chart, tooltip, and Consumption total are unchanged

## 3. Verification

- [x] 3.1 Update existing tests asserting daily Month rows (`DashboardTest`, `ChartProjectionTest`, `TrackerViewModelTest` if affected)
