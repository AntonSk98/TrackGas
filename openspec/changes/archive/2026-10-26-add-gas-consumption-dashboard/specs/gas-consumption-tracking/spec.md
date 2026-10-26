# Spec Delta

## Purpose

Let one local user record cumulative gas meter readings and inspect consumption across calendar periods while distinguishing measured intervals, estimated daily usage, and missing coverage.

## ADDED Requirements

### Requirement: Separate dashboard and history tabs
The app SHALL launch on Dashboard and offer History separately. Dashboard SHALL show period controls and consumption, not reading history. History SHALL show all readings newest first with Edit/Delete, not period controls or consumption. Add SHALL be available on both tabs. Switching tabs and reading mutations SHALL preserve the selected period; mutations SHALL preserve the selected tab. Tab selection SHALL survive configuration changes and reset to Dashboard in a fresh process.

#### Scenario: Manage readings without cluttering the dashboard
- **WHEN** the user switches from Dashboard to History
- **THEN** all entered readings and their Edit/Delete actions are shown without consumption rows or period controls

#### Scenario: Return to the selected dashboard period
- **WHEN** the user selects a past period, switches to History, edits a reading, and returns to Dashboard
- **THEN** Dashboard still shows that selected period with recalculated consumption, and no reading history is shown there

#### Scenario: Preserve tab selection on rotation
- **WHEN** the user rotates the device while viewing History
- **THEN** History remains selected and the same entered readings remain available

### Requirement: Calendar dashboard periods
The dashboard SHALL initially select the current Monday-to-Sunday week in the device's local date. It SHALL offer weekly, calendar-month, and calendar-year views with previous/next navigation and a visible date range. Changing view type SHALL select the period containing today. Weekly and monthly views SHALL show daily consumption; yearly views SHALL show monthly totals.

#### Scenario: Launch and navigate periods
- **WHEN** the user opens the app on October 26, 2026
- **THEN** Weekly is selected for October 26 through November 1, and Previous selects October 19 through October 25

#### Scenario: Switch view types
- **WHEN** the user switches to Monthly and then Yearly
- **THEN** the app selects October 2026 and then 2026, and navigation advances by a calendar month or year respectively

#### Scenario: Device timezone changes during the session
- **WHEN** the device timezone changes while the app process remains alive and the user selects a period type
- **THEN** the selected period contains today in the device's new timezone, without discarding existing readings or resetting the selected tab

### Requirement: Empty and partially covered consumption
The dashboard SHALL show no consumption total or series when no reading interval overlaps the selected period, without sample values or invented totals. A lone reading SHALL establish only a baseline. Uncovered dates SHALL remain unknown internally, not zero or extrapolated. Totals SHALL sum available covered dates only.

#### Scenario: No readings or only a baseline
- **WHEN** there are no readings, or only one reading
- **THEN** no consumption total or series is displayed, while the add action and period controls remain available on Dashboard and any existing reading remains available in History

### Requirement: Covered consumption rows
The dashboard SHALL display only buckets with covered usage, including estimated allocations and known zero consumption. Entirely uncovered days or months SHALL be omitted rather than rendered as individual Unknown rows.

#### Scenario: Unknown days differ from zero usage
- **WHEN** readings of 100 on October 1 and 100 on October 2 are the only readings
- **THEN** October 2 shows known zero consumption, October 1 and dates after October 2 are omitted from the consumption rows rather than shown as Unknown or zero, and the total is zero without a coverage-count label

#### Scenario: Compact monthly and yearly consumption
- **WHEN** readings of 100 on October 1 and 112 on October 4 are the only readings
- **THEN** the October dashboard displays only October 2, 3, and 4, each with 4 cubic metres, and the yearly dashboard displays only October with 12 cubic metres, without estimation labels or coverage counts

### Requirement: Minimal consumption presentation
Consumption content SHALL show only dates or months, cubic-metre values, and a total titled "Consumption". It SHALL NOT show coverage counts, measured/estimated distribution labels, or allocation explanations. This presentation policy SHALL NOT change interval arithmetic, covered-date selection, or internal coverage/provenance metadata.

#### Scenario: Uncluttered skipped-day consumption
- **WHEN** the user views consumption derived from readings several days apart
- **THEN** the covered rows and total show their numeric values without "Estimated distribution", "days covered", or allocation explanations

#### Scenario: No coverage in selected period
- **WHEN** the user navigates to a period outside all recorded reading intervals
- **THEN** consumption content is empty even if readings exist elsewhere

### Requirement: Create a dated cumulative reading
The app SHALL provide an accessible add icon in the top-right toolbar. Its form SHALL request a date and cumulative meter reading in cubic metres, with today selected initially and a selectable earlier date. Saving valid input SHALL add a note and refresh consumption; cancelling SHALL leave readings unchanged.

#### Scenario: Save a backdated reading
- **WHEN** the user opens Add, changes today's date to an earlier valid date, enters a valid meter value, and saves
- **THEN** the note appears with that date and value and the dashboard recalculates without resetting the selected period

#### Scenario: Cancel creation
- **WHEN** the user dismisses an unsaved add form
- **THEN** no note is added

#### Scenario: Add uses the current device date after a timezone change
- **WHEN** the device timezone changes while the app process remains alive and the user opens Add
- **THEN** its default date and future-date validation use today's date in the new timezone

### Requirement: Validate reading input and chronology
The app SHALL allow one reading per date, today or earlier, with a finite non-negative decimal value in cubic metres. Readings SHALL never decrease chronologically; equal values are valid. Add and Edit SHALL validate against both chronological neighbours, excluding the note being edited. Invalid input SHALL show a helpful error without modifying saved readings.

#### Scenario: Invalid fields
- **WHEN** the user submits an empty date or value, malformed number, negative value, future date, or duplicate date
- **THEN** the app identifies the invalid field and does not save

#### Scenario: Backdated reading violates the next reading
- **WHEN** readings are 100 on October 1 and 120 on October 4, and the user attempts to insert 125 on October 2
- **THEN** saving is rejected because the value exceeds the following reading

#### Scenario: Edit violates previous reading
- **WHEN** the user edits a reading to be less than its chronological predecessor
- **THEN** saving is rejected and the original note remains unchanged

#### Scenario: Decimal and equal readings
- **WHEN** the user saves 120.125 after a previous reading of 120.125 on an earlier date
- **THEN** the reading is accepted and the intervening interval has zero consumption

### Requirement: Edit and delete reading history
The app SHALL expose all recorded notes, newest first, independently of dashboard period selection, with Edit and Delete actions. Edit SHALL prefill the existing date and value and preserve the note's identity on save. Delete SHALL require confirmation. Successful edits and deletions SHALL refresh history and consumption immediately; cancelling SHALL leave data unchanged.

#### Scenario: Edit date and value
- **WHEN** the user opens Edit, changes a note's date and value to valid inputs, and saves
- **THEN** the existing note is updated rather than duplicated, history reorders if necessary, and adjacent intervals are recalculated

#### Scenario: Confirm or cancel deletion
- **WHEN** the user selects Delete
- **THEN** cancelling preserves the note and confirming removes it and recalculates the remaining consecutive intervals

#### Scenario: Remove the last usable interval
- **WHEN** deletion leaves fewer than two readings
- **THEN** consumption content becomes empty while any remaining baseline note is still manageable

### Requirement: Consumption from chronological intervals
Consumption SHALL equal the later cumulative reading minus the earlier reading for every consecutive pair sorted by date, irrespective of entry order. An interval SHALL cover dates after its earlier reading through its later reading, inclusive. Its first date SHALL be excluded, preventing double counting at shared endpoints. The original entered readings SHALL remain available separately from calculated usage.

#### Scenario: Consecutive days
- **WHEN** readings are 1000 on October 1 and 1004 on October 2
- **THEN** the interval total is 4 cubic metres, attributed to October 2

#### Scenario: Insert a middle reading
- **WHEN** 1003 on October 2 is inserted between 1000 on October 1 and 1012 on October 4
- **THEN** the old interval is replaced by intervals totaling 3 and 9 cubic metres, and the entered cumulative values remain unchanged

### Requirement: Skipped-day allocation and provenance
For a gap of N calendar days, the app SHALL allocate the measured interval total evenly across those N days. Multi-day allocations SHALL retain estimated-distribution metadata internally, distinct from single-day measured differences. No usage SHALL be extrapolated beyond available reading intervals. Distribution labels and explanations SHALL NOT be displayed on the dashboard.

#### Scenario: Three-day gap
- **WHEN** readings are 1000 on October 1 and 1012 on October 4
- **THEN** the interval total is 12 cubic metres and October 2, 3, and 4 each show 4 cubic metres, with estimated provenance retained internally and no value inferred for October 5

#### Scenario: Original readings do not become synthetic notes
- **WHEN** skipped days receive estimated allocations
- **THEN** history still contains only the actual entered readings, not fabricated daily meter notes

### Requirement: Aggregate covered consumption across boundaries
Period totals SHALL sum the allocations on covered dates within the selected period, including intervals whose endpoint readings lie outside it. Coverage and estimated-distribution metadata SHALL remain available internally but SHALL NOT produce dashboard labels or explanations. Presentation rounding SHALL NOT change the underlying interval total or accumulate rounding error across daily and monthly aggregations.

#### Scenario: Month boundary without an in-month note
- **WHEN** readings are 1000 on January 30, 2026 and 1030 on March 1, 2026
- **THEN** the 30-day interval allocates 1 cubic metre per day, February shows 28 cubic metres without a distribution label, and no February meter note is fabricated

#### Scenario: Non-terminating daily average
- **WHEN** an interval has 1 cubic metre over 3 days
- **THEN** its complete interval total remains 1 cubic metre rather than the sum of rounded daily display values

### Requirement: One transient local tracker
The app SHALL keep one tracker in memory without accounts, network access, databases, files, or preference-based persistence. Readings SHALL survive recomposition and activity recreation for configuration changes, but SHALL reset when the app process is terminated. The interface SHALL disclose that readings are temporary.

#### Scenario: Rotate the device
- **WHEN** readings have been added and the device rotates
- **THEN** the same readings and derived consumption remain available

#### Scenario: Fresh process
- **WHEN** the app process is terminated and the app is opened in a new process
- **THEN** the tracker contains no saved readings and initially selects the current week
