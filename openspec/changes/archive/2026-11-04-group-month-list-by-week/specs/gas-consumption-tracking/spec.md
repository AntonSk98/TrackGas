## MODIFIED Requirements

### Requirement: Covered consumption rows
The dashboard SHALL display only buckets with covered usage, including estimated allocations and known zero consumption. Week views SHALL list covered days and Year views SHALL list covered months. Month views SHALL instead list one row per calendar week (Monday–Sunday, clipped to the selected month) containing at least one covered day, labelled with its date range and showing the sum of its covered days. Entirely uncovered days, weeks, or months SHALL be omitted rather than rendered as Unknown rows.

#### Scenario: Unknown days differ from zero usage
- **WHEN** readings of 100 on October 1 and 100 on October 2 are the only readings
- **THEN** the October weekly row covering October 1–4 shows zero consumption without Unknown labels, weeks with no covered days are omitted, and the total is zero without a coverage-count label

#### Scenario: Compact monthly and yearly consumption
- **WHEN** readings of 100 on October 1 and 112 on October 4 are the only readings
- **THEN** the October dashboard lists a single week row (October 1–4, 2026 being Thursday–Sunday) of 12 cubic metres, and the yearly dashboard displays only October with 12 cubic metres, without estimation labels or coverage counts

#### Scenario: Fully covered month is compact
- **WHEN** all 31 days of a month are covered
- **THEN** the list shows at most six week rows rather than 31 daily rows, the first and last rows are clipped to the month boundaries, and the week rows sum to the Consumption total

#### Scenario: Partially covered week
- **WHEN** only some days of a calendar week are covered
- **THEN** its row shows the sum of the covered days only, with no value invented for uncovered days

### Requirement: Period consumption bar chart
Dashboard SHALL display daily consumption bars for Week/Month and monthly bars for Year when covered usage exists. The chart SHALL use the same covered days or months as the underlying consumption data, date-positioned within the selected range with a zero-based cubic-metre scale. Uncovered buckets SHALL have no plotted mark, not a zero value. Known zero SHALL remain identifiable. Week and Year lists SHALL expose all covered values; in Month the daily values SHALL remain available through the chart inspection tooltip and be summarized per week in the list.

#### Scenario: Daily and monthly charts agree with values
- **WHEN** readings are 1000 on October 1, 2026 and 1012 on October 4, 2026
- **THEN** the October Month chart plots October 2, 3, and 4 at 4 cubic metres each, the Year chart plots October at 12 cubic metres, and the weekly list row and Consumption totals agree

#### Scenario: Missing coverage is not zero
- **WHEN** only October 2 is covered with known zero usage
- **THEN** October 2 has an identifiable zero mark, its week row shows zero, uncovered dates have no consumption marks, and the Consumption total is zero

#### Scenario: Empty or baseline-only data
- **WHEN** there are no readings, only one reading, or no interval overlapping the selected period
- **THEN** no chart or numeric total is shown, an explanatory empty state is visible, and period controls and Add remain available

#### Scenario: Narrow monthly chart
- **WHEN** all 31 days are covered in a Month view on a compact screen
- **THEN** all covered marks fit without horizontal chart scrolling, date ticks remain readable without requiring every date label, and the week rows are available in the scrollable list

#### Scenario: Update after reading changes
- **WHEN** a valid reading is added, edited, or deleted
- **THEN** the chart, numeric list, and independently calculated total reflect the same updated snapshot without resetting the selected period or tab

#### Scenario: Preserve precision and accessible values
- **WHEN** an interval allocates 1 cubic metre over 3 days
- **THEN** the Consumption total remains 1 cubic metre, weekly sums are computed from unrounded daily amounts before localized display rounding, and dates and values remain accessible without relying on chart color or pixel heights
