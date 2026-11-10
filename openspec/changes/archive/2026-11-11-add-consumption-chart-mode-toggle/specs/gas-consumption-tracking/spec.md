# Spec Delta

## ADDED Requirements

### Requirement: Chart-only consumption mode selection
Dashboard SHALL offer daily-estimate and absolute consumption chart modes, defaulting to daily estimates in a fresh process. Mode changes SHALL affect only gas chart values, units, heading, and inspection, not absolute period totals, list amounts, History, temperatures, or selected dates. Selection SHALL survive tab changes, period navigation, committed data refreshes, and configuration changes without being stored as tracker data or exported.

#### Scenario: Default and switch
- **WHEN** a fresh process opens Dashboard and the user selects absolute mode
- **THEN** the chart initially shows daily estimates and then absolute bucket amounts while the large period total and all list amounts remain unchanged

#### Scenario: Retain selection in the session
- **WHEN** the user selects absolute mode, navigates periods, visits History, rotates, and receives committed reading or temperature updates
- **THEN** absolute mode remains selected without resetting the tab or selected period

#### Scenario: Fresh launch
- **WHEN** the app process is terminated and a new process starts
- **THEN** the chart starts in daily-estimate mode with the existing Dashboard and current-week defaults

### Requirement: Timestamp-normalized daily chart values
Daily-estimate mode SHALL use each consecutive same-installation pair's meter difference multiplied by 86,400 and divided by actual elapsed seconds. Its unrounded rate SHALL be projected onto each covered calendar date after the earlier reading through the later reading. Calculation SHALL preserve decimal precision, use absolute observation instants, and remain independent of calendar allocation of absolute consumption.

#### Scenario: Thirty-nine-hour interval
- **WHEN** readings increase from 1000 on November 6 at 08:00 to 1013 on November 7 at 23:00 without an offset change
- **THEN** the November 7 daily-estimate bar shows approximately 8 cubic metres per day and absolute mode shows 13 cubic metres, while the period total and list retain 13

#### Scenario: Multi-day interval
- **WHEN** readings increase by 12 cubic metres over exactly 72 hours and cover three calendar dates
- **THEN** each of those dates has a daily estimate of 4 cubic metres per day without creating synthetic readings

#### Scenario: Short and fractional intervals
- **WHEN** readings on different recorded dates increase by 7 cubic metres over 10 hours and 30 minutes
- **THEN** daily-estimate mode shows 16 cubic metres per day without truncating elapsed time to whole hours

#### Scenario: Daylight-saving and timezone changes
- **WHEN** an interval crosses a daylight-saving transition or its readings retain different recording timezones
- **THEN** daily normalization uses actual elapsed seconds between absolute instants, including 24 cubic metres per day for a 23-cubic-metre increase over 23 hours

### Requirement: Covered-date-weighted yearly daily estimates
Year's daily-estimate mode SHALL show one monthly bar equal to the arithmetic mean of that month's eligible covered-date rates. Only dates inside that month SHALL contribute; uncovered dates and opening-only consumption SHALL NOT contribute to the sum or denominator. The mean SHALL NOT be a sum of rates, an unweighted mean of interval rates, or absolute monthly consumption divided by the month's length.

#### Scenario: Unequal interval lengths
- **WHEN** a month contains three eligible covered dates at 4 cubic metres per day and one at 8
- **THEN** its daily-estimate bar is 5 cubic metres per day, not 6 or 20

#### Scenario: Partial-month coverage
- **WHEN** a month has two eligible dates at 6 cubic metres per day and all its other dates are uncovered
- **THEN** its daily-estimate bar is 6 cubic metres per day without treating uncovered dates as zero

#### Scenario: Clip at a month or year boundary
- **WHEN** one reading interval covers dates in two months or two years
- **THEN** each selected monthly bar averages only the eligible dates in its own calendar month and selected year

### Requirement: Daily estimate availability
Daily estimates SHALL exist only on dates covered by actual consecutive same-installation readings. Opening consumption without an observation pair SHALL have no rate and SHALL NOT be mixed into another interval's rate. Missing rates SHALL remain absent, known zero rates SHALL remain inspectable, and mode selection SHALL remain available when absolute consumption exists without eligible rates.

#### Scenario: Opening-only consumption
- **WHEN** the selected period contains only replacement-start opening consumption
- **THEN** daily mode shows a localized unavailable-estimate message without a fabricated bar, the period total and list stay visible, and selecting absolute mode shows the opening amount

#### Scenario: Opening usage beside an eligible interval
- **WHEN** a replacement date includes old-meter interval consumption and new-meter opening consumption
- **THEN** daily mode uses only the old-meter interval's rate while absolute mode, the total, and the list include both consumption contributions

#### Scenario: Baselines and independent meters
- **WHEN** readings have no same-installation predecessor or the selected dates are outside all eligible intervals
- **THEN** no daily rate is invented, no interval bridges installations, and no rate is extrapolated onto uncovered dates

#### Scenario: Zero and missing remain distinct
- **WHEN** an eligible interval has equal meter readings while adjacent dates are uncovered
- **THEN** its dates have identifiable, inspectable zero-rate marks and the adjacent dates have no marks

### Requirement: Localized accessible chart mode control
The gas chart SHALL have a compact switch beside its heading: "Daily consumption" / "Tagesverbrauch" when enabled and "Consumption" / "Verbrauch" when disabled, without "24". The switch SHALL expose its label and mode accessibly in English and German. Daily axes SHALL use cubic metres per day; daily tooltips SHALL show only the approximation sign, value, and cubic metres without a per-day suffix. Controls SHALL remain usable on narrow screens and with enlarged text.

#### Scenario: German daily presentation
- **WHEN** German Dashboard shows daily mode
- **THEN** the chart heading is "Tagesverbrauch", its axis uses cubic metres per day, its tooltip shows an approximate cubic-metre value without "/Tag", and the adjacent switch is enabled and accessibly identifies daily mode

#### Scenario: Absolute presentation
- **WHEN** the user chooses absolute mode
- **THEN** the heading becomes "Consumption" or "Verbrauch", the axis and tooltip use cubic metres rather than a rate, and the selector exposes absolute mode

#### Scenario: Separate the total from the chart
- **WHEN** either chart mode is displayed
- **THEN** the large absolute period number remains above the separate mode-aware chart section under "Total consumption" / "Gesamtverbrauch"

#### Scenario: Compact accessible layout
- **WHEN** the display is narrow or text is enlarged
- **THEN** the heading and compact switch share one row, with the heading wrapping if necessary, without clipping, overlapping the total, relying only on color, or reducing the switch touch target below 48 dp

#### Scenario: Smooth mode transition
- **WHEN** the user switches chart modes, including rapidly switching back
- **THEN** the switch uses its native animation, the gas chart immediately shows the selected mode with matching units and scale and no outgoing chart overlay, and the unchanged temperature chart remains mounted

## MODIFIED Requirements

### Requirement: Calendar dashboard periods
The dashboard SHALL initially select the current Monday-to-Sunday week in the device's local date. It SHALL offer weekly, calendar-month, and calendar-year views with previous/next navigation and a visible period heading. The heading SHALL show the week range, month and year, or year, with year context kept out of individual bucket labels. Changing view type SHALL select the period containing today. Weekly and monthly views SHALL use daily chart positions; yearly views SHALL use monthly chart positions, showing absolute totals or daily estimates according to the chart mode. Consumption lists SHALL retain absolute amounts.

#### Scenario: Launch and navigate periods
- **WHEN** the user opens the app on October 26, 2026
- **THEN** Weekly is selected for October 26 through November 1, and Previous selects October 19 through October 25

#### Scenario: Switch view types
- **WHEN** the user switches to Monthly and then Yearly
- **THEN** the app selects October 2026 and then 2026, and navigation advances by a calendar month or year respectively

#### Scenario: Navigate below chart content
- **WHEN** the user views a populated or empty Dashboard period
- **THEN** Weekly/Monthly/Yearly remains above the summary card, while previous/next arrows and the centred date-range heading appear inside the card below the chart or empty message, before any numeric rows, and remain usable in either state

#### Scenario: Device timezone changes during the session
- **WHEN** the device timezone changes while the app process remains alive and the user selects a period type
- **THEN** the selected period contains today in the device's new timezone, without discarding existing readings or resetting the selected tab

#### Scenario: Compact labels with unambiguous year context
- **WHEN** the user views a week, month, or year
- **THEN** chart and row labels show localized weekday names, day numbers, or month names respectively, with the selected year in the period heading rather than repeated on every bucket

#### Scenario: Week crossing a year boundary
- **WHEN** the selected week starts in December and ends in January of the following year
- **THEN** its heading identifies both years, while the individual daily labels remain weekdays

### Requirement: Minimal consumption presentation
Consumption content SHALL show dates or months, absolute cubic-metre list values, a period total titled "Total consumption" / "Gesamtverbrauch", and a separately titled mode-aware bar chart above the numeric list. It SHALL NOT show coverage counts, measured/estimated distribution labels, or allocation explanations. This presentation policy SHALL NOT change interval arithmetic, absolute covered-date selection, or internal coverage/provenance metadata.

#### Scenario: Uncluttered skipped-day consumption
- **WHEN** the user views consumption derived from readings several days apart
- **THEN** the chart, covered rows, and total show their numeric values without "Estimated distribution", "days covered", or allocation explanations

#### Scenario: No coverage in selected period
- **WHEN** the user navigates to a period outside all recorded reading intervals
- **THEN** consumption content is empty even if readings exist elsewhere

### Requirement: Period consumption bar chart
Dashboard SHALL display daily consumption bars for Week/Month and monthly bars for Year according to the selected chart mode. Bars SHALL be date-positioned within the selected range with a zero-based value scale and mode-appropriate units. Absolute mode SHALL use the underlying calendar consumption buckets; daily mode SHALL use eligible timestamp-normalized estimates. Uncovered buckets SHALL have no plotted mark, not a zero value. Known zero SHALL remain identifiable. Week and Year lists SHALL expose all covered absolute values; in Month the absolute daily values SHALL remain available through absolute-mode chart inspection and be summarized per week in the list.

#### Scenario: Daily and monthly charts agree with values
- **WHEN** readings are 1000 on October 1, 2026 and 1012 on October 4, 2026 and absolute mode is selected
- **THEN** the October Month chart plots October 2, 3, and 4 at 4 cubic metres each, the Year chart plots October at 12 cubic metres, and the weekly list row and Total consumption amounts agree

#### Scenario: Missing coverage is not zero
- **WHEN** only October 2 is covered with known zero usage
- **THEN** October 2 has an identifiable zero mark, its week row shows zero, uncovered dates have no consumption marks, and the Total consumption amount is zero

#### Scenario: Empty or baseline-only data
- **WHEN** there are no readings, only one baseline reading without opening consumption, or no consumption overlapping the selected period
- **THEN** no chart or numeric total is shown, an explanatory empty state is visible, and period controls and Add remain available

#### Scenario: Narrow monthly chart
- **WHEN** all 31 days are covered in a Month view on a compact screen
- **THEN** all covered marks fit without horizontal chart scrolling, date ticks remain readable without requiring every date label, and the week rows are available in the scrollable list

#### Scenario: Update after reading changes
- **WHEN** a valid reading is added, edited, or deleted
- **THEN** the active-mode chart, absolute numeric list, and independently calculated absolute total reflect the same updated snapshot without resetting the selected mode, period, or tab

#### Scenario: Preserve precision and accessible values
- **WHEN** an interval allocates 1 cubic metre over 3 days
- **THEN** Total consumption remains 1 cubic metre, weekly sums are computed from unrounded daily amounts before localized display rounding, and dates and mode-appropriate values remain accessible without relying on chart color or pixel heights

### Requirement: Inspect consumption bars
Long-pressing a covered bar or known-zero mark SHALL show a tooltip at the chart top, clear of the finger, with its localized weekday (Week), two-digit day (Month), or month name (Year) and mode-appropriate consumption and units. Values SHALL use original unrounded active-mode projected amounts and existing localized rounding, never normalized coordinates. Ordinary swipes SHALL navigate pages, including over the chart. Inspection SHALL reset on release, cancellation, mode changes, or new data/period snapshots.

#### Scenario: Inspect a monthly day
- **WHEN** the user long-presses the October 4 bar with 4 cubic metres of consumption in absolute mode
- **THEN** the tooltip displays `04` and `4 m³` in the active locale

#### Scenario: Inspect a weekly or yearly bar
- **WHEN** the user long-presses a covered bar in Week or Year
- **THEN** the tooltip displays its weekday or month name respectively, with its corresponding active-mode consumption and units and no repeated year

#### Scenario: Inspect genuine zero
- **WHEN** the user long-presses a covered known-zero mark
- **THEN** the tooltip shows that bucket's date label and zero consumption in the active unit, never an unknown bucket or a positive normalized value

#### Scenario: Keep inspection clear of the finger
- **WHEN** the user long-presses a short bar or a zero mark near the chart baseline
- **THEN** the tooltip remains at the top of the chart rather than following the bar height or overlapping the pressing finger

#### Scenario: Navigate after inspection
- **WHEN** the user swipes horizontally outside the chart, drags vertically from the chart, or changes the period or reading snapshot
- **THEN** page navigation and scrolling continue to work and no obsolete tooltip survives the snapshot change

#### Scenario: Switch mode during inspection
- **WHEN** the chart mode changes while an inspection tooltip exists
- **THEN** the old tooltip disappears and the next inspection uses the new mode's amount and unit even when both modes have numerically identical values

### Requirement: Preserve calendar consumption semantics with timestamps
Observation times SHALL determine weather windows, History's 24-hour estimates, and daily-estimate chart rates, not absolute gas calendar allocation. Consecutive readings SHALL retain the existing earlier-date-exclusive, later-date-inclusive allocation and equal distribution across covered dates. Replacement-start opening consumption SHALL retain its existing absolute behavior and SHALL NOT create a weather interval or daily estimate without two actual readings.

#### Scenario: Morning measurements
- **WHEN** readings are taken on November 6 at 08:00 and November 7 at 08:00
- **THEN** their absolute gas difference remains attributed to November 7 while its temperature represents the actual timestamp interval and the daily chart shows that interval's normalized rate on November 7

#### Scenario: Correct only an observation time
- **WHEN** a valid observation-time edit leaves dates and meter values unchanged
- **THEN** absolute gas totals and their calendar allocation remain unchanged while affected temperature means are invalidated and History's estimates and daily-estimate chart rates are recalculated

#### Scenario: Opening consumption without an interval
- **WHEN** a replacement meter has only its first reading on its start date
- **THEN** opening gas consumption remains available in absolute mode, totals, and rows but no temperature or daily estimate is invented for that contribution

### Requirement: Derived history estimate freshness
History estimates SHALL derive from the same committed tracker snapshot as their readings and refresh after committed additions, meter-value or observation-time edits, deletions, imports, and reloads. Failed or cancelled mutations SHALL retain prior estimates. Estimates SHALL remain independent of Dashboard period selection and SHALL NOT be persisted or added to CSV exports.

#### Scenario: Insert and delete a middle reading
- **WHEN** a backdated reading is committed between two readings in an installation
- **THEN** the old estimate is replaced by estimates for the two resulting consecutive pairs, and deleting the middle reading restores the estimate for the remaining pair

#### Scenario: Edit time without changing calendar consumption
- **WHEN** a committed observation-time correction changes an interval's elapsed duration without changing dates or meter values
- **THEN** affected History estimates and daily-estimate chart values refresh while absolute Dashboard gas consumption and calendar allocation remain unchanged

#### Scenario: Import, restart, and navigation
- **WHEN** valid timestamped readings are imported or restored offline after restart, or the selected Dashboard period changes
- **THEN** History estimates reflect the committed readings regardless of the selected Dashboard period without requiring extra stored or exported fields

#### Scenario: Failed or cancelled edit
- **WHEN** an edit fails to commit or the user cancels it
- **THEN** History retains its estimates from the last committed tracker snapshot
