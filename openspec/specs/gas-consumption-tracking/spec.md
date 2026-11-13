# gas-consumption-tracking Specification

## Purpose

Let one local user record cumulative gas meter readings and inspect consumption across calendar periods while distinguishing measured intervals, estimated daily usage, and missing coverage.

## Requirements


### Requirement: Separate dashboard and history tabs
The app SHALL launch on Dashboard and offer History separately. Dashboard SHALL show period controls and consumption, not reading history. History SHALL show all readings newest first with Edit/Delete and optional per-reading 24-hour estimates, not period controls or Dashboard consumption rows. Add SHALL be available on both tabs. Switching tabs and reading mutations SHALL preserve the selected period; mutations SHALL preserve the selected tab. Tab selection SHALL survive configuration changes and reset to Dashboard in a fresh process.

#### Scenario: Manage readings without cluttering the dashboard
- **WHEN** the user switches from Dashboard to History
- **THEN** all entered readings and their Edit/Delete actions are shown with eligible per-reading 24-hour estimates but without Dashboard consumption rows or period controls

#### Scenario: Return to the selected dashboard period
- **WHEN** the user selects a past period, switches to History, edits a reading, and returns to Dashboard
- **THEN** Dashboard still shows that selected period with recalculated consumption, and no reading history is shown there

#### Scenario: Preserve tab selection on rotation
- **WHEN** the user rotates the device while viewing History
- **THEN** History remains selected and the same entered readings remain available

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

### Requirement: Empty and partially covered consumption
The dashboard SHALL show consumption only for dates covered by intervals between consecutive readings of the same meter. A meter's first reading is a baseline and SHALL NOT itself contribute consumption. Uncovered dates SHALL remain unknown internally, not zero or extrapolated. Totals SHALL sum covered usage only. The empty state SHALL use concise localized text prompting the user to add another reading when a baseline exists.

#### Scenario: No readings or only a baseline
- **WHEN** there are no readings or the tracker has only meter starting values
- **THEN** no consumption total or series is displayed, the empty state and add action remain available, and every reading remains available in History

#### Scenario: Starting value on a replacement date
- **WHEN** a new meter starts with a nonzero reading on its replacement date
- **THEN** that starting value contributes no consumption on that date

#### Scenario: No coverage in the selected period
- **WHEN** the user navigates to a period outside all recorded intervals
- **THEN** consumption content is empty and the empty state is shown without implying that existing readings elsewhere cover the selected period

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

### Requirement: Minimal consumption presentation
Consumption content SHALL show dates or months, absolute cubic-metre list values, a period total titled "Total consumption" / "Gesamtverbrauch", and a separately titled mode-aware bar chart above the numeric list. It SHALL NOT show coverage counts, measured/estimated distribution labels, or allocation explanations. This presentation policy SHALL NOT change interval arithmetic, absolute covered-date selection, or internal coverage/provenance metadata.

#### Scenario: Uncluttered skipped-day consumption
- **WHEN** the user views consumption derived from readings several days apart
- **THEN** the chart, covered rows, and total show their numeric values without "Estimated distribution", "days covered", or allocation explanations

#### Scenario: No coverage in selected period
- **WHEN** the user navigates to a period outside all recorded reading intervals
- **THEN** consumption content is empty even if readings exist elsewhere

### Requirement: Create a dated cumulative reading
The app SHALL provide an accessible add icon in the top-right toolbar. Its form SHALL request a date, observation time, and non-negative cumulative meter reading in cubic metres, with the current device-local date and time selected initially and an earlier date/time selectable. When no meter exists, saving SHALL create a meter and save the reading as its starting value; that value SHALL establish the baseline and SHALL NOT contribute consumption. The Add form SHALL NOT offer a note field. Cancelling SHALL leave readings unchanged.

#### Scenario: Save the first reading
- **WHEN** the user saves a valid reading while no meter exists
- **THEN** a meter and its starting reading are committed together, and no consumption is attributed to the starting value

#### Scenario: Add after a starting value
- **WHEN** a meter starts at 1250 on October 1 and the user records 1254 on October 2
- **THEN** October 2 has 4 cubic metres of consumption

#### Scenario: Save a backdated first reading
- **WHEN** the user creates the first meter with a valid reading dated before today
- **THEN** that date and value establish the meter's starting baseline without invented consumption

#### Scenario: Add before the meter's start date
- **WHEN** the user selects a date before the first reading that established the meter
- **THEN** the reading is rejected with a message that the date is before the meter's start date, not that a reading already exists

#### Scenario: Add a backdated reading
- **WHEN** the user opens Add, selects an earlier valid date and observation time, enters a valid meter value, and saves
- **THEN** the reading appears with that date, time, and value and the dashboard recalculates without resetting the selected period

#### Scenario: Add does not offer a note
- **WHEN** the user opens Add
- **THEN** the form offers date, time, and value fields without a note field

#### Scenario: Cancel creation
- **WHEN** the user dismisses an unsaved add form
- **THEN** no reading is added

#### Scenario: Add uses the current device date after a timezone change
- **WHEN** the device timezone changes while the app process remains alive and the user opens Add
- **THEN** its default date, observation time, timezone, and future-time validation use the device's new timezone

### Requirement: Validate reading input and chronology
The app SHALL allow one reading per date per installation, today or earlier, with a finite non-negative decimal value in cubic metres. Values SHALL never decrease within one installation; equal values are valid. Add and Edit SHALL validate against both neighbours in that installation, excluding the edited reading. Different installations SHALL remain independent and SHALL NOT create cross-meter readings or intervals. Invalid input SHALL show a helpful error without modifying saved readings.

#### Scenario: Invalid fields
- **WHEN** the user submits an empty date or value, malformed number, negative value, future date, or duplicate date in the applicable installation
- **THEN** the app identifies the invalid field and does not save

#### Scenario: Backdated reading violates the next reading
- **WHEN** readings in one installation are 100 on October 1 and 120 on October 4, and the user attempts to insert 125 on October 2
- **THEN** saving is rejected because the value exceeds the following reading

#### Scenario: Edit violates previous reading
- **WHEN** the user edits a reading to be less than its chronological predecessor in the same installation
- **THEN** saving is rejected and the original reading remains unchanged

#### Scenario: Decimal and equal readings
- **WHEN** the user saves 120.125 after a previous reading of 120.125 on an earlier date in the same installation
- **THEN** the reading is accepted and the intervening interval has zero consumption

#### Scenario: Ordinary Add cannot bypass validation
- **WHEN** the user submits a lower value through ordinary Add within the same installation
- **THEN** the previous-reading validation rejects it without creating another installation or allowing a one-use decrease

#### Scenario: Lower value in another meter history
- **WHEN** a new independent meter history starts with a value lower than the latest value in an earlier installation
- **THEN** the reading is accepted because chronology is validated only within its own installation

#### Scenario: Add to an independent meter history
- **WHEN** the user adds a reading to the current installation after replacing a meter
- **THEN** the reading is assigned to that installation and checked against only its own readings

#### Scenario: Duplicate date in one meter history
- **WHEN** the user adds a reading on a date already used by the same installation
- **THEN** saving is rejected without changing readings

#### Scenario: Resolve a backdated reading
- **WHEN** the user adds a date before a meter replacement after a new installation has become current
- **THEN** the reading is assigned to the installation whose start date covers it and checked against only that installation's neighbours

#### Scenario: Ordinary Add on replacement day
- **WHEN** the user adds a reading for the replacement date in the new installation
- **THEN** it is accepted if that installation has no reading on the date and is validated only against that installation

### Requirement: Edit and delete reading history
The app SHALL expose all readings newest first, independently of dashboard period selection. History SHALL visually distinguish a reading recorded as its meter's starting value from regular readings. Edit SHALL preserve a reading's date, identity, and meter, allow observation-time and meter-value correction, and allow an optional note to be added, changed, or cleared. Add SHALL NOT offer notes. Delete SHALL require confirmation. Deleting a meter's last reading SHALL also remove that meter; deleting a reading while others remain SHALL recalculate consumption only within its meter.

#### Scenario: Distinguish meter starting values in History
- **WHEN** a History entry is the starting reading recorded when its meter was created
- **THEN** it has a distinct localized visual marker from regular readings

#### Scenario: Edit date and value
- **WHEN** the user edits a reading, changes its valid meter value or observation time, enters a note, and saves
- **THEN** the existing reading is updated while its date, identity, and installation remain unchanged

#### Scenario: Clear an existing note
- **WHEN** the user edits a reading and removes its note
- **THEN** the reading is saved without a note

#### Scenario: Preserve the reading date and identity
- **WHEN** the user edits a reading
- **THEN** the original date remains read-only, the observation time is editable, and the reading is updated rather than duplicated

#### Scenario: Delete a reading from an independent history
- **WHEN** the user confirms deletion of any reading, including one created before or after a meter replacement
- **THEN** that reading is removed, other meters remain unchanged, and same-meter consumption is recalculated

#### Scenario: Delete a reading while other meter readings remain
- **WHEN** the user confirms deletion of a reading and another reading remains in that meter
- **THEN** only the selected reading is removed, other meters remain unchanged, and same-meter consumption is recalculated

#### Scenario: Delete the last reading of a meter
- **WHEN** the user confirms deletion of a meter's only remaining reading
- **THEN** the reading and its meter are removed atomically, with no empty meter left behind

#### Scenario: Delete the last reading in the tracker
- **WHEN** the user deletes the final reading across all meters
- **THEN** History is empty, the tracker has no meters, and a later Add creates a new meter with its starting value

### Requirement: Reading mutation completion
Delete SHALL require confirmation. Successful edits and deletions SHALL refresh History and consumption immediately; cancelling SHALL leave data unchanged.

#### Scenario: Save an edited reading
- **WHEN** the user saves a valid meter-value or note correction
- **THEN** History and consumption immediately reflect the committed correction

#### Scenario: Cancel an edited reading
- **WHEN** the user cancels an unsaved meter-value or note correction
- **THEN** the saved reading, History, and consumption remain unchanged

#### Scenario: Confirm or cancel deletion
- **WHEN** the user selects Delete for a reading
- **THEN** cancelling preserves it and confirming removes it and recalculates consumption for its meter

#### Scenario: Remove the last usable interval
- **WHEN** deletion leaves fewer than two readings in an installation
- **THEN** that installation contributes no consumption interval while any remaining reading remains manageable and other installations remain unaffected

#### Scenario: Remove a meter's last reading
- **WHEN** deletion removes a meter's last reading
- **THEN** the reading and meter disappear together from the committed tracker

### Requirement: Consumption from chronological intervals
Consumption SHALL equal the later cumulative reading minus the earlier reading for every consecutive pair within the same meter sorted by date, irrespective of entry order. An interval SHALL cover dates after its earlier reading through its later date, inclusive. Its first date SHALL be excluded, preventing double counting at shared endpoints. A meter's first reading is only a baseline and SHALL NOT create consumption. Different meters SHALL never form an interval.

#### Scenario: Consecutive days
- **WHEN** readings are 1000 on October 1 and 1004 on October 2 in the same installation
- **THEN** the interval total is 4 cubic metres, attributed to October 2

#### Scenario: Insert a middle reading
- **WHEN** 1003 on October 2 is inserted between 1000 on October 1 and 1012 on October 4 in one installation
- **THEN** the old interval is replaced by intervals totaling 3 and 9 cubic metres, and the entered cumulative values remain unchanged

#### Scenario: First new-meter reading on replacement date
- **WHEN** the old meter has readings of 45 on November 6 and 50 on November 7, replacement starts on November 7, and the new meter's first reading that day is 25
- **THEN** November 7 includes the old meter's 5 cubic metres but does not count the new meter's starting value of 25, with no interval created between meters

#### Scenario: First meter starting value
- **WHEN** the first meter starts at 25 on October 1 and the next reading is 28 on October 2
- **THEN** October 1 has no consumption from the starting value and October 2 has 3 cubic metres

#### Scenario: Usage after a replacement baseline
- **WHEN** a replacement meter starts at 25 on November 7 and reads 28 on November 8
- **THEN** November 8 has 3 cubic metres of new-meter consumption, with no interval between meters

#### Scenario: First reading after the replacement date remains a baseline
- **WHEN** a meter's first reading is dated after its start date and there is no later reading
- **THEN** it establishes a baseline only and contributes no consumption interval

#### Scenario: Replacement-day opening usage and skipped new-meter days
- **WHEN** the new installation starts on October 28 and its first reading is 6 that day, followed by 12 on October 31
- **THEN** the starting value contributes no consumption on October 28 and the 6-to-12 interval contributes 2 cubic metres per day on October 29, 30, and 31

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

### Requirement: One durable local tracker
The app SHALL store one tracker of actual readings on the device without an account, server, network access, or cloud backup. Successfully saved readings SHALL survive process death and app restart. Tab and period selections SHALL continue to use their existing fresh-process defaults.

#### Scenario: Restore in a fresh process
- **WHEN** the user saves several readings, terminates the app process, and opens the app again
- **THEN** History contains the same actual readings with their identities, dates, and exact decimal values, and Dashboard recalculates from them with the current week and Dashboard selected

#### Scenario: Rotate the device
- **WHEN** readings have been added and the device rotates
- **THEN** the same readings and derived consumption remain available

#### Scenario: Restore changes
- **WHEN** the user successfully edits one reading and confirms deletion of another, then restarts the app
- **THEN** the edited value remains associated with its original date and identity, and the deleted reading remains absent

#### Scenario: Offline use
- **WHEN** the user records readings and reopens the app without connectivity
- **THEN** the readings and their consumption are available without signing in or contacting a server

### Requirement: Reliable local reading operations
The app SHALL distinguish loading, successful storage, and storage failure. It SHALL acknowledge a mutation only after it commits and SHALL preserve the last committed data if that mutation fails. The user SHALL be able to retry a failed load or mutation without losing a draft.

#### Scenario: Loading readings
- **WHEN** stored readings have not yet loaded
- **THEN** the app indicates loading, does not present an empty tracker as authoritative, and prevents reading mutations until the stored data is available

#### Scenario: Load failure
- **WHEN** stored readings cannot be read
- **THEN** the app displays a recoverable storage error instead of an empty history and provides a retry action without deleting the database

#### Scenario: Save failure
- **WHEN** a valid add or edit cannot be committed
- **THEN** the form stays open with its draft and a helpful storage error, and history and dashboard retain the last committed readings

#### Scenario: Delete failure
- **WHEN** a confirmed deletion cannot be committed
- **THEN** the reading remains in history, consumption remains unchanged, and the user sees an error and can retry

#### Scenario: Duplicate or overlapping operations
- **WHEN** a reading operation is in progress and the user attempts another mutation
- **THEN** the app prevents duplicate submissions and preserves date uniqueness and chronological validation

### Requirement: Swipe navigation between tracker pages
Users SHALL switch between Dashboard and History by swiping or tapping labelled tabs. In left-to-right layouts, swiping left from Dashboard SHALL open History and swiping right SHALL return. Tab feedback SHALL update during navigation without waiting for settlement; the indicator SHALL track the swipe. Navigation SHALL preserve period and independent scroll positions without changing readings. Fresh processes SHALL start on Dashboard.

#### Scenario: Swipe forward and back
- **WHEN** the user swipes left from Dashboard and then right from History in a left-to-right layout
- **THEN** History and then Dashboard become selected, their tab indicators match, and the selected calendar period and readings are unchanged

#### Scenario: Mix taps and swipes
- **WHEN** the user taps History, swipes to Dashboard, and taps History again
- **THEN** the selected tab and visible page agree after each transition without oscillation or a second gesture being required

#### Scenario: Preserve independent scroll positions
- **WHEN** the user scrolls Dashboard, switches to History and scrolls there, then returns to each page
- **THEN** each page resumes its own scroll position and Dashboard retains its selected period

#### Scenario: Rotate after a swipe
- **WHEN** the user swipes to History and rotates the device
- **THEN** History remains selected with the same readings and selected dashboard period

#### Scenario: Separate chart inspection from page navigation
- **WHEN** the user swipes horizontally over the chart without a long press, or long-presses and then drags
- **THEN** ordinary swipes switch pages, while long-press drags inspect bars without switching pages or panning/zooming; ordinary vertical gestures still scroll content

#### Scenario: Boundary and interrupted gestures
- **WHEN** the user swipes outward at the first or last page, cancels a partial swipe, or changes direction rapidly
- **THEN** navigation stays within the two pages and the tab indicator agrees with the final settled page without modifying data or period selection

#### Scenario: Immediate navigation feedback
- **WHEN** the user drags horizontally between pages or taps the other tab
- **THEN** the indicator follows the drag continuously and a tab tap highlights its destination before page animation finishes, without an additional settling delay

### Requirement: Scrolling dashboard period selection
Dashboard SHALL place Weekly, Monthly, and Yearly selection in the normal content scroll flow, not pinned beneath the Dashboard/History tabs. The controls SHALL remain usable with enlarged text, retain the selected period type, and SHALL NOT appear on History.

#### Scenario: Scroll past period selection
- **WHEN** the user scrolls down a populated Dashboard
- **THEN** Weekly, Monthly, and Yearly scroll away with the content while Dashboard/History tabs remain visible, and scrolling back restores the controls with their selection unchanged

#### Scenario: Switch to History
- **WHEN** the user switches from a scrolled Dashboard to History
- **THEN** no period controls appear on History, and returning to Dashboard restores its selected period and independent scroll position

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

### Requirement: Chart inspection gesture priority
Only long-press inspection SHALL capture chart drags. After a long press, sliding across bars SHALL update the tooltip without switching to History until release or cancellation. Ordinary horizontal swipes SHALL navigate pages even when starting on the chart; ordinary vertical drags SHALL scroll the page. A quick tap SHALL NOT show the tooltip.

#### Scenario: Slide across consumption bars
- **WHEN** the user long-presses the chart and then drags left or right across bars
- **THEN** the tooltip follows the selected bucket and shows its date/value while Dashboard remains selected

#### Scenario: Continue navigating outside the chart
- **WHEN** the user swipes without a long press, inside or outside the chart, or taps History
- **THEN** the app opens History without changing readings or the selected calendar period

### Requirement: Immediate reading-date feedback
Duplicate-date feedback SHALL appear only in the date picker, immediately for a conflicting selection without requiring Save or a meter value. Checks SHALL use the installation assigned to the selected date and SHALL NOT treat a meter replacement as a reading or occupied reading date. Opening Add/Edit SHALL NOT show that warning in the parent form. Date checks SHALL share final-save rules and exclude the edited reading. Duplicate selections SHALL NOT be confirmable; valid selections SHALL clear the feedback.

#### Scenario: Select an existing date
- **WHEN** the user selects or enters a date already used by another reading in the applicable installation
- **THEN** the picker immediately shows the duplicate-date message and disables date confirmation, without waiting for Save

#### Scenario: Open Add on an already recorded day
- **WHEN** Add defaults to a date already used in its applicable installation
- **THEN** the parent form shows no duplicate-date warning, and opening date selection shows the conflict inside the picker

#### Scenario: Edit without changing the date
- **WHEN** the user edits a reading's meter value or note
- **THEN** the original date remains read-only and readings in another installation do not cause a duplicate-date error

#### Scenario: Correct a duplicate selection
- **WHEN** the user replaces a duplicate picker selection with an available valid date
- **THEN** the duplicate error clears and date confirmation becomes available without changing any saved reading

### Requirement: Duplicate-date save recovery
Save SHALL retain final validation and open the picker if it rejects a duplicate, preserving the draft.

#### Scenario: Save an unchanged duplicate default
- **WHEN** the user submits a draft whose default date already belongs to another reading in its applicable installation
- **THEN** no reading is saved, the picker opens with duplicate feedback, and dismissing it returns to the unchanged draft without a duplicate warning in the parent form

### Requirement: Target-bound reading dialogs
At most one Add, Edit, deletion confirmation, replacement, or tracking-data purge confirmation SHALL be active per tracker session. Pending actions and their errors SHALL belong to that dialog and target, including the expected installation for replacement and the whole tracker for purge. Pending mutations SHALL block dismissal and duplicate submission.

#### Scenario: Retain an unsuccessful action
- **WHEN** validation, a missing or obsolete target, or an expected storage failure prevents a mutation
- **THEN** the matching draft or confirmation remains available with its error behavior, and unrelated targets do not receive that error

#### Scenario: Dismiss or replace a dialog
- **WHEN** the user dismisses a non-pending dialog and later opens another reading, replacement, or purge action
- **THEN** the old draft, picker state, and errors do not appear in the new dialog and no second dialog is simultaneously active

### Requirement: Reading dialog restoration
Configuration restoration SHALL retain the matching draft or confirmation; successful completion SHALL close it without reopening on restoration. A fresh tracker session SHALL discard previous transient dialogs.

#### Scenario: Preserve a pending save across rotation
- **WHEN** the user submits a draft and rotates before the save completes
- **THEN** the same draft remains visible and disabled, no second submission is made, and only its matching committed success closes it

#### Scenario: Restore after completion or session replacement
- **WHEN** saved UI state is restored after a dialog completed, or against a newly created tracker session
- **THEN** no completed or previous-session draft, confirmation, or pending action reappears

### Requirement: Atomic final reading validation
Final saving SHALL resolve the target, preserve an edit's identity and original date, validate uniqueness and chronological neighbours against the latest committed readings, and write atomically. When no meter exists, the first valid Add SHALL atomically create both its meter and starting reading. Locale-aware parsing SHALL retain current decimal precision, accepted input, and field-error semantics. Picker feedback SHALL remain advisory and SHALL NOT replace final transactional validation.

#### Scenario: Competing saves use the latest neighbours
- **WHEN** two saves race for the same date or for values that cannot both fit chronological order
- **THEN** only saves valid against their transaction's latest readings commit, and rejected saves leave committed data unchanged

#### Scenario: Parse and report independent fields
- **WHEN** a submission has both an invalid date and invalid meter text
- **THEN** both applicable field errors are retained with the existing validation precedence and no write occurs

#### Scenario: Ignore a supplied replacement edit date
- **WHEN** an edit submission supplies a date different from its persisted target
- **THEN** validation and saving use the original persisted date and identity, or report a missing target if it no longer exists

#### Scenario: Concurrent first readings
- **WHEN** two sessions attempt to create the first meter while no readings exist
- **THEN** transactions serialize against the latest tracker, creating at most one initial meter and leaving no orphan meter or partial reading

### Requirement: Ordered committed tracker snapshots
History and consumption SHALL derive from the same committed snapshot of readings and installations. Mutation success SHALL follow commit and make its committed state available immediately without resetting tab or period. Delayed older observation SHALL NOT replace newer accepted data. Repeated delivery of identical tracker data SHALL NOT produce a second data-driven projection or reset chart inspection; installation changes SHALL count as data changes.

#### Scenario: Observation arrives after a newer mutation
- **WHEN** an older captured snapshot is delivered after a newer mutation snapshot
- **THEN** History and consumption remain on the newer committed state without a temporary reversal

#### Scenario: Observation repeats a committed mutation
- **WHEN** the same committed tracker arrives through mutation completion and observation
- **THEN** the action completes once, History and consumption stay consistent, and duplicate delivery does not reset chart inspection

#### Scenario: Concurrent tracker sessions
- **WHEN** two active tracker sessions mutate the same local tracker
- **THEN** their successful operations commit atomically and each session converges to the latest committed tracker without losing its own tab or period selection

### Requirement: Independent observation and mutation outcomes
An observation failure SHALL preserve the last accepted committed data and any active draft or confirmation, and offer load retry. A confirmed commit SHALL remain successful even if observation fails before or after its result arrives. Observation failure SHALL NOT cause an already committed action to be offered as a failed mutation for resubmission. Mutations SHALL stay blocked until observation is ready again.

#### Scenario: Commit succeeds while observation fails
- **WHEN** observation fails while a save or delete is in flight and that mutation subsequently reports a committed result
- **THEN** its committed snapshot is retained, its dialog closes, the load error and retry remain available, and no mutation retry is requested

#### Scenario: Retry observation with an unsuccessful draft
- **WHEN** observation fails while a draft or deletion confirmation is open and the user retries loading
- **THEN** the dialog contents remain intact, submission stays blocked during loading, and successful observation re-enables eligible actions without resubmitting them

### Requirement: Classified local reading failures
Expected storage-access and invalid stored-data failures SHALL have distinct privacy-safe categories with generic errors and non-destructive retry. Persisted readings and meters SHALL be validated before publication or mutation. An empty tracker with no readings and no meters SHALL be valid. Cancellation and unrelated defects SHALL propagate unchanged. Failure handling SHALL NOT transmit diagnostics or delete, repair, or expose reading data.

#### Scenario: Malformed stored reading
- **WHEN** a persisted identity, date, or decimal value cannot be decoded into a valid reading
- **THEN** loading or the transaction reports a stored-data category, retains its cause internally, shows the existing generic storage error, and does not reset or partially modify the database

#### Scenario: Infrastructure failure
- **WHEN** an expected SQL or I/O failure prevents a load or commit
- **THEN** a storage-access category is retained, the existing retry behavior remains available, and drafts and previously committed data are preserved

#### Scenario: Individually valid rows form an invalid collection
- **WHEN** stored rows decode individually but cumulative values decrease within one meter, identity or meter/date uniqueness is violated, a reading references no meter, or a meter has no readings
- **THEN** observation and mutation attempts report a stored-data category before publishing or writing, preserve the last accepted snapshot and any open draft or confirmation, and leave all stored rows unchanged

#### Scenario: Retry an inconsistent stored collection
- **WHEN** the user retries after a stored collection invariant failure and the same inconsistent rows remain
- **THEN** the existing generic error remains available without exposing stored values, resetting the database, repairing records, or allowing invalid data to reach consumption projection

#### Scenario: Cancellation and unexpected defects
- **WHEN** an operation is cancelled or an unrelated programming/invariant exception occurs outside stored-data decoding
- **THEN** cancellation propagates unchanged and the unexpected exception remains a defect rather than being presented as a routine retryable storage failure

#### Scenario: Privacy-safe diagnostic state
- **WHEN** an expected failure is retained for presentation or inspected through diagnostics
- **THEN** only its category and action context are exposed, not raw meter input, readings, dates, identities, SQL arguments, or exception messages

#### Scenario: Lower values across a valid replacement
- **WHEN** stored readings decrease only between independent meters after a valid replacement
- **THEN** the tracker loads normally because each meter is validated independently

### Requirement: Atomic replacement against current committed data
Replacement SHALL atomically validate its expected current meter and date, save the supplied starting reading, and make the new meter current. A stale or competing operation SHALL NOT retarget a replacement silently. Success SHALL be acknowledged only after commit, and failure SHALL preserve the confirmation and last committed tracker.

#### Scenario: Replacement creates a starting reading
- **WHEN** the user confirms replacement with a valid starting value for today
- **THEN** a new meter and its first reading commit together, the starting value is excluded from consumption, and the previous meter's readings remain unchanged

#### Scenario: Partial storage failure
- **WHEN** storage fails while confirming replacement
- **THEN** no partial meter or reading is retained, no current meter is lost, and the user can retry with the confirmation preserved

#### Scenario: Preserve an existing nonzero baseline
- **WHEN** the current meter has a nonzero reading and a replacement is confirmed with a new starting value
- **THEN** the old reading remains unchanged and the new meter stores the user-supplied value as its baseline

#### Scenario: Competing replacements
- **WHEN** two tracker sessions confirm replacement for the same current meter and date
- **THEN** at most one replacement commits and the other reports an obsolete target without creating another meter

#### Scenario: Final reading changed while form is open
- **WHEN** another session changes the current meter or its readings before replacement confirmation
- **THEN** the stale confirmation is rejected without writes and the user must review the current meter before confirming again

#### Scenario: Meter changes while the form is open
- **WHEN** another session changes the current meter or its readings before replacement confirmation
- **THEN** the stale confirmation is rejected without writes and the user must review the current meter before confirming again

#### Scenario: Observation failure after commit
- **WHEN** replacement commits but observation fails before or after its result arrives
- **THEN** the committed replacement remains successful, its form closes, the last committed tracker is retained, and load retry does not resubmit replacement

### Requirement: Committed purge projections and durability
Purge success SHALL be acknowledged only after commit and immediately supply a complete committed empty tracker with no readings or meters to History and Dashboard, preserving the selected tab and period. Older observations SHALL NOT restore purged data. The reset SHALL survive process death and offline restart. A new first reading SHALL establish a baseline without constraints from erased history or invented consumption.

#### Scenario: Refresh both tabs after commit
- **WHEN** purge commits while the user has selected History and a past dashboard period
- **THEN** History becomes empty, Dashboard has no chart, consumption rows, total, or obsolete inspection tooltip, and the selected tab and period remain unchanged

#### Scenario: Delayed old observation
- **WHEN** a pre-purge snapshot arrives after committed purge success
- **THEN** it cannot repopulate History or consumption, and a duplicate empty snapshot does not repeat completion

#### Scenario: Restart after reset
- **WHEN** the user restarts offline after committed purge
- **THEN** no meters or readings remain, and Dashboard opens on the current week with no old confirmation

#### Scenario: Begin again without historical constraints
- **WHEN** the user adds a valid first reading on a previously used date with a value lower than an erased reading
- **THEN** a new meter and starting reading are committed, contributing no consumption until a later reading is saved

### Requirement: Purge failure and lifecycle safety
A failed purge SHALL leave all prior records unchanged and retain the confirmation for explicit retry. Rotation and same-process session restoration SHALL retain pending work without resubmitting it; completion SHALL affect only its matching dialog and request. Observation failure SHALL NOT turn a committed purge into a failed mutation. Load retry SHALL remain non-destructive, and purge SHALL NOT bypass invalid stored-data validation.

#### Scenario: Roll back partial deletion
- **WHEN** storage fails or cancellation occurs before the purge commits
- **THEN** all original readings and meters remain intact and no successful empty snapshot is issued; expected storage failures retain their classified retry behavior and cancellation propagates unchanged

#### Scenario: Retain pending confirmation across recreation
- **WHEN** a purge is pending and its activity rotates or is recreated within the same retained process session
- **THEN** the same disabled confirmation and operation remain active without a second purge, and only the matching completion closes it

#### Scenario: Observation failure does not invalidate a commit
- **WHEN** purge commits while observation fails before or after its result arrives
- **THEN** the committed empty tracker is retained, its confirmation closes successfully, load error and retry remain available, and no failed-purge retry is offered

#### Scenario: Invalid stored data is not silently repaired
- **WHEN** load or purge encounters invalid persisted tracking data
- **THEN** it reports the stored-data failure without partial deletion or repair, and retry never purges automatically

### Requirement: Serialized purge and other tracker mutations
Purge SHALL act on the latest valid committed tracker at its transaction, including changes made after confirmation opened. Competing mutations SHALL have a single commit order without partial state or revived deleted records. Writes committed before purge SHALL be removed; valid writes committed afterward SHALL obey normal rules against the reset tracker. Independent sessions SHALL converge without resetting their navigation.

#### Scenario: Another session changes data before purge commits
- **WHEN** another session commits an added reading or replacement after the purge warning opens but before the purge transaction
- **THEN** the confirmed full-tracker purge also removes that committed data rather than deleting only records present when the warning opened

#### Scenario: Mutation after purge
- **WHEN** another session submits an edit targeting erased data or adds a new valid reading after purge
- **THEN** stale edits are rejected by normal target validation, valid new additions can commit, and every session converges to the latest committed tracker

### Requirement: Separate temperature chart
When temperatures are stored for the selected period, the Dashboard SHALL show a temperature line chart below the consumption bar chart. The charts SHALL use aligned date positions and independent value scales so temperature is not visually compared with gas consumption. Temperature points SHALL appear only for dates or months that have both a consumption bar and a stored temperature.

#### Scenario: Separate charts share dates
- **WHEN** a Week with consumption bars and stored temperatures is shown
- **THEN** the temperature line chart appears below the consumption chart with points aligned to the matching bars and its own temperature scale

#### Scenario: No temperature data
- **WHEN** no temperature is stored for the period
- **THEN** only the consumption chart is shown

### Requirement: Temperature granularity and gaps
Week and Month SHALL plot interval-derived temperature means on covered consumption dates; Year SHALL plot the covered-date-weighted monthly mean of those values. An interval's mean SHALL be reused on each date to which its gas consumption is allocated, not presented as independently fetched daily weather. Buckets without temperature SHALL have no point and the line SHALL NOT bridge them.

#### Scenario: Missing temperature day
- **WHEN** an interval covering a day in the middle of the period has no stored mean
- **THEN** that day has no temperature point and the line is broken there

#### Scenario: Year view
- **WHEN** October has two covered dates with interval-derived temperatures of 8 and 10 degrees Celsius
- **THEN** the Year chart plots October at 9 degrees Celsius

#### Scenario: Multi-day interval
- **WHEN** one reading interval allocates gas to three calendar dates and has a stored mean of 6 degrees Celsius
- **THEN** all three dates use 6 degrees Celsius for temperature projection without claiming three separately observed daily means

### Requirement: Temperature inspection and consumption rows
Long-pressing a consumption bar SHALL show its date and consumption only. Long-pressing a temperature point SHALL show its date and interval-derived temperature only. Each consumption row SHALL show the bucket's mean interval-derived temperature beside its consumption when a value exists.

#### Scenario: Inspect consumption
- **WHEN** the user long-presses a consumption bar for a bucket with a temperature
- **THEN** the tooltip shows the date and consumption without temperature

#### Scenario: Inspect temperature
- **WHEN** the user long-presses a temperature point
- **THEN** the tooltip shows the date and interval-derived temperature in degrees Celsius without consumption

#### Scenario: Consumption row shows mean temperature
- **WHEN** a consumption row has interval-derived temperatures for its covered dates
- **THEN** it shows their covered-date-weighted mean temperature beside consumption

### Requirement: Start replacement as an independent meter history
Replacement SHALL take effect only after explicit confirmation for today's device-local date. It SHALL create a new meter with a persisted starting reading supplied by the user, without requiring a final reading for the previous meter or linking readings across meters. A replacement SHALL NOT be available when no meter exists; Add SHALL create the first meter instead.

#### Scenario: Replace without a final reading
- **WHEN** the user confirms replacement with a valid starting value
- **THEN** the new meter begins with that reading today and the previous meter's readings remain unchanged

#### Scenario: Replace with existing readings
- **WHEN** the current meter has readings and the user confirms replacement with a valid starting value
- **THEN** those readings remain unchanged in the old meter and the new meter stores the supplied starting reading

#### Scenario: Replace without a previous meter's reading today
- **WHEN** the user confirms replacement and the previous meter has no reading for today
- **THEN** the replacement succeeds without fabricating a final reading for the previous meter

#### Scenario: First reading after replacement
- **WHEN** the user adds the first reading after replacement
- **THEN** it belongs to the new meter and its consumption is calculated from the saved starting value

#### Scenario: Restart after replacement
- **WHEN** the user restarts the app offline after confirming replacement
- **THEN** the new meter and its starting reading remain current and all existing meter histories reload unchanged

#### Scenario: Multiple replacements over time
- **WHEN** the user replaces a meter again on a later date
- **THEN** another meter with its own starting reading is created while all earlier meters and readings remain available

#### Scenario: Second replacement on the same day
- **WHEN** a replacement is already committed for today and another replacement is requested
- **THEN** the app explains that today's replacement already exists and creates no additional meter

#### Scenario: Date changes before confirmation
- **WHEN** the device date changes after the form opens but before confirmation
- **THEN** no replacement is committed under the stale date, the form explains that it must be reopened for the current date, and no existing reading is moved

#### Scenario: No meter exists
- **WHEN** the tracker has no readings and the user requests Replace meter
- **THEN** replacement is unavailable and the user can create a meter with a starting value through Add

### Requirement: Confirmed reset of independent tracking data
An explicitly confirmed purge SHALL atomically remove all readings and meters, leaving no current meter. Purging an already empty tracker SHALL succeed with the same valid empty state.

#### Scenario: Purge successive meter installations
- **WHEN** the user confirms purge with readings across multiple independent meters
- **THEN** all readings and meters are removed together and no partial tracker is published

#### Scenario: Purge an empty tracker
- **WHEN** the user confirms purge with no readings or meters
- **THEN** the operation completes normally and leaves no meter

### Requirement: Attach optional notes when editing readings
The Edit form SHALL provide an optional note field. A saved note SHALL be associated with its reading, preserved across app restarts, and available to History and CSV exchange. Empty notes SHALL be treated as absent.

#### Scenario: Add a note while editing
- **WHEN** the user enters a note while editing a reading and saves successfully
- **THEN** the note is stored with that reading and appears in History

#### Scenario: Clear a note while editing
- **WHEN** the user clears a previously saved note and saves successfully
- **THEN** the reading has no note in storage or History

#### Scenario: Restart after saving a note
- **WHEN** the app restarts after a note has been saved
- **THEN** the note remains associated with the same reading

### Requirement: Compact reading history entries
History SHALL show each reading in two logical rows: its recorded local date and observation time, and its value with any 24-hour estimate as secondary metadata. When multiple installations exist, a compact meter identifier SHALL remain available with the value. A saved note SHALL appear as a third logical row; a reading without a note SHALL NOT reserve a note row. Value-row metadata SHALL wrap on narrow screens or with enlarged text without obscuring values or actions.

#### Scenario: History without notes
- **WHEN** History contains a reading without a note
- **THEN** the entry displays its localized recorded date/time and value in two logical rows without repeating "meter" or "reading" labels, with an eligible 24-hour estimate in the value row

#### Scenario: History with a note
- **WHEN** History contains a reading with a saved note
- **THEN** its recorded date/time, value with any eligible estimate, and note appear in three logical rows

#### Scenario: Distinguish independent meters
- **WHEN** History contains readings from more than one installation
- **THEN** each value remains associated with a compact meter identifier without adding a separate repeated role row

#### Scenario: Minimal localized estimate
- **WHEN** History displays a 24-hour estimate
- **THEN** it uses a compact approximation marker and cubic-metres-per-24-hours unit in smaller secondary text, locale-aware decimal formatting with existing calculated-value rounding, and English/German localized accessible wording that identifies an estimated 24-hour consumption

#### Scenario: Narrow or enlarged layout
- **WHEN** an entry's value, meter identifier, and estimate do not fit on one visual line
- **THEN** the metadata wraps without clipping or replacing the raw reading, note, or existing tap-to-edit and long-press-to-delete actions

### Requirement: Concise reading form and deletion copy
Reading forms and confirmations SHALL use concise localized labels. The date picker SHALL omit a redundant visible title while retaining an accessible date-control label; duplicate-date feedback SHALL be short and precise. The value field SHALL be labelled “Meter reading (m³)” in English and “Zählerstand (m³)” in German. Reading deletion SHALL remain explicitly confirmed with a concise identification of the reading.

#### Scenario: Add and edit labels
- **WHEN** the user opens Add or Edit
- **THEN** the value label identifies a meter reading in cubic metres and the Add date picker has no redundant visible title

#### Scenario: Duplicate date feedback
- **WHEN** the user chooses a date already occupied in that installation
- **THEN** the picker shows a short, precise localized duplicate-date message

#### Scenario: Confirm reading deletion
- **WHEN** the user requests deletion of a reading
- **THEN** a concise localized confirmation identifies the reading and offers explicit Delete and Cancel actions

### Requirement: Unambiguous observation timestamps
Each reading SHALL retain an observation timestamp at minute precision and its recording timezone across restarts, edits, and device timezone changes. Add SHALL capture the device timezone when the draft opens; Edit SHALL retain the recorded timezone. Observation time means when the meter was read, not when Save was pressed. Time controls and errors SHALL be localized in English and German.

#### Scenario: Delayed submission
- **WHEN** the user saves at 20:00 but enters an observation time of 08:00
- **THEN** the reading retains 08:00 as its observation time

#### Scenario: Device timezone changes
- **WHEN** the device timezone changes after a reading is saved
- **THEN** the reading retains its recorded date/time and the same absolute observation instant

#### Scenario: Restore a draft
- **WHEN** the device rotates with an unsaved observation time selected
- **THEN** the matching draft retains that time and timezone without replacing them with the current clock time

### Requirement: Validate observation times
Saving SHALL reject a missing or invalid observation time, an instant in the future, a nonexistent local time, or timestamps that do not strictly increase between same-installation chronological readings. Repeated local times during daylight-saving fallback SHALL use the earlier valid offset. Existing one-reading-per-date and nondecreasing-value rules SHALL remain in effect.

#### Scenario: Future time today
- **WHEN** the user selects today's date and a time later than the current instant
- **THEN** saving identifies the time error without modifying readings

#### Scenario: Daylight-saving clock gap
- **WHEN** the user selects a local observation time skipped by the recorded timezone's daylight-saving transition
- **THEN** saving identifies an invalid time instead of silently shifting it

#### Scenario: Repeated clock hour
- **WHEN** a selected local observation time occurs twice during fallback
- **THEN** its timestamp uses the earlier valid offset consistently in storage and weather lookup

#### Scenario: Timestamp order conflicts with date order
- **WHEN** a proposed timestamp is not strictly between the absolute instants of its same-installation neighbours
- **THEN** final transactional validation rejects it without modifying data

#### Scenario: Another reading on the same date
- **WHEN** the user selects a different time on an already occupied date in the same installation
- **THEN** the existing duplicate-date rule still rejects the additional reading

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

### Requirement: Timestamp-normalized 24-hour history estimate
For each consecutive same-installation reading pair, History SHALL show on the later reading an approximate consumption per 24 hours equal to the meter difference multiplied by 86,400 seconds and divided by actual elapsed seconds between recorded observation instants. This SHALL apply to every positive duration, including intervals shorter than 24 hours. Calculation SHALL retain decimal precision until presentation and SHALL NOT use rounded hours or calendar-day counts.

#### Scenario: Thirty-nine-hour interval
- **WHEN** same-installation readings are 1000 on November 6, 2026 at 08:00 and 1013 on November 7 at 23:00 in the same timezone without an offset change
- **THEN** the later reading shows an approximate 24-hour consumption of 8 cubic metres, using 39 elapsed hours rather than one calendar day

#### Scenario: Shorter than 24 hours
- **WHEN** consecutive readings increase by 3 cubic metres over 12 elapsed hours on different recorded dates
- **THEN** the later reading shows an approximate consumption of 6 cubic metres per 24 hours

#### Scenario: Exactly 24 hours and zero consumption
- **WHEN** an interval spans exactly 24 hours
- **THEN** its 24-hour estimate equals its meter difference, including a visible zero when the cumulative readings are equal

#### Scenario: Preserve partial hours
- **WHEN** readings increase by 7 cubic metres over 10 hours and 30 minutes on different recorded dates
- **THEN** the later reading's estimate is 16 cubic metres per 24 hours, without truncating elapsed time to whole hours

#### Scenario: Daylight-saving transition
- **WHEN** consecutive readings increase by 23 cubic metres from March 28, 2026 at 08:00 to March 29 at 08:00 in Europe/Berlin
- **THEN** History shows an approximate consumption of 24 cubic metres per 24 hours using the actual 23-hour interval

#### Scenario: Different recording timezones
- **WHEN** consecutive same-installation readings retain different recording timezones
- **THEN** normalization uses the elapsed time between their absolute observation instants, not subtraction of their displayed local times

### Requirement: Installation-local estimate availability
A 24-hour estimate SHALL exist only when a reading has a chronological predecessor in its own installation. The first reading in each installation SHALL have no estimate or reserved estimate placeholder, including a replacement-start reading that contributes opening consumption to Dashboard. Estimates SHALL NOT bridge installations, create synthetic readings, or extrapolate values onto unobserved dates.

#### Scenario: Empty history and baseline
- **WHEN** the tracker has no readings or a single baseline reading
- **THEN** no 24-hour estimate is displayed and existing empty-history or baseline presentation remains available

#### Scenario: Independent meter replacement
- **WHEN** a new installation has its first reading and another installation already has readings
- **THEN** the new installation's first reading has no estimate even if it is on the replacement date, and its second reading uses only its own predecessor

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

### Requirement: Chart-only consumption mode selection
Dashboard SHALL offer daily-projection and absolute (interval) consumption chart modes, defaulting to absolute interval consumption in a fresh process. Mode changes SHALL affect only gas chart values, units, heading, and inspection, not absolute period totals, list amounts, History, temperatures, or selected dates. Selection SHALL survive tab changes, period navigation, committed data refreshes, and configuration changes without being stored as tracker data or exported.

#### Scenario: Default and switch
- **WHEN** a fresh process opens Dashboard and the user selects daily-projection mode
- **THEN** the chart initially shows absolute interval amounts and then daily projections while the large period total and all list amounts remain unchanged

#### Scenario: Retain selection in the session
- **WHEN** the user selects daily-projection mode, navigates periods, visits History, rotates, and receives committed reading or temperature updates
- **THEN** daily-projection mode remains selected without resetting the tab or selected period

#### Scenario: Fresh launch
- **WHEN** the app process is terminated and a new process starts
- **THEN** the chart starts in absolute mode with the existing Dashboard and current-week defaults

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
The gas chart SHALL have a compact switch beside its heading: "Daily projection" / "Tagesprojektion" when enabled and "Interval consumption" / "Intervallverbrauch" when disabled. The switch SHALL expose its label and mode accessibly in English and German. Daily axes SHALL use cubic metres per day; daily tooltips SHALL show only the approximation sign, value, and cubic metres without a per-day suffix. Controls SHALL remain usable on narrow screens and with enlarged text.

#### Scenario: German daily presentation
- **WHEN** German Dashboard shows daily-projection mode
- **THEN** the chart heading is "Tagesprojektion", its axis uses cubic metres per day, its tooltip shows an approximate cubic-metre value without "/Tag", and the adjacent switch is enabled and accessibly identifies daily mode

#### Scenario: German absolute presentation
- **WHEN** German Dashboard shows absolute mode
- **THEN** the chart heading is "Intervallverbrauch" and the switch is disabled and accessibly identifies absolute mode

#### Scenario: Absolute presentation
- **WHEN** the user chooses absolute mode
- **THEN** the heading becomes "Interval consumption" or "Intervallverbrauch", the axis and tooltip use cubic metres rather than a rate, and the selector exposes absolute mode

#### Scenario: Separate the total from the chart
- **WHEN** either chart mode is displayed
- **THEN** the large absolute period number remains above the separate mode-aware chart section under "Total consumption" / "Gesamtverbrauch"

#### Scenario: Compact accessible layout
- **WHEN** the display is narrow or text is enlarged
- **THEN** the heading and compact switch share one row, with the heading wrapping if necessary, without clipping, overlapping the total, relying only on color, or reducing the switch touch target below 48 dp

#### Scenario: Smooth mode transition
- **WHEN** the user switches chart modes, including rapidly switching back
- **THEN** the switch uses its native animation, the gas chart immediately shows the selected mode with matching units and scale and no outgoing chart overlay, and the unchanged temperature chart remains mounted
