# gas-consumption-tracking Specification

## Purpose

Let one local user record cumulative gas meter readings and inspect consumption across calendar periods while distinguishing measured intervals, estimated daily usage, and missing coverage.

## Requirements

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
The dashboard SHALL initially select the current Monday-to-Sunday week in the device's local date. It SHALL offer weekly, calendar-month, and calendar-year views with previous/next navigation and a visible period heading. The heading SHALL show the week range, month and year, or year, with year context kept out of individual bucket labels. Changing view type SHALL select the period containing today. Weekly and monthly views SHALL show daily consumption; yearly views SHALL show monthly totals.

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
Consumption content SHALL show dates or months, cubic-metre values, a total titled "Consumption", and a bar chart of the same covered buckets above the numeric list. It SHALL NOT show coverage counts, measured/estimated distribution labels, or allocation explanations. This presentation policy SHALL NOT change interval arithmetic, covered-date selection, or internal coverage/provenance metadata.

#### Scenario: Uncluttered skipped-day consumption
- **WHEN** the user views consumption derived from readings several days apart
- **THEN** the chart, covered rows, and total show their numeric values without "Estimated distribution", "days covered", or allocation explanations

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
The app SHALL expose all recorded notes, newest first, independently of dashboard period selection, with Edit and Delete actions. Edit SHALL prefill the existing date and value, allow changes only to the meter value, show the original date read-only, and preserve the note's original date and identity on save. Delete SHALL require confirmation. Successful edits and deletions SHALL refresh history and consumption immediately; cancelling SHALL leave data unchanged.

#### Scenario: Edit date and value
- **WHEN** the user opens Edit, sees the original date read-only, changes the meter value to a valid input, and saves
- **THEN** the existing note is updated rather than duplicated, its original date and identity remain unchanged, and adjacent intervals are recalculated

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
Dashboard SHALL display daily consumption bars for Week/Month and monthly bars for Year when covered usage exists. The chart SHALL use the same covered buckets as the numeric list, date-positioned within the selected range with a zero-based cubic-metre scale. Uncovered buckets SHALL have no plotted mark, not a zero value. Known zero SHALL remain identifiable. All covered values SHALL remain available in the list.

#### Scenario: Daily and monthly charts agree with values
- **WHEN** readings are 1000 on October 1, 2026 and 1012 on October 4, 2026
- **THEN** the October Month chart plots October 2, 3, and 4 at 4 cubic metres each, the Year chart plots October at 12 cubic metres, and their lists and Consumption totals agree

#### Scenario: Missing coverage is not zero
- **WHEN** only October 2 is covered with known zero usage
- **THEN** October 2 has an identifiable zero mark and a numeric zero row, uncovered dates have no consumption marks or rows, and the Consumption total is zero

#### Scenario: Empty or baseline-only data
- **WHEN** there are no readings, only one reading, or no interval overlapping the selected period
- **THEN** no chart or numeric total is shown, an explanatory empty state is visible, and period controls and Add remain available

#### Scenario: Narrow monthly chart
- **WHEN** all 31 days are covered in a Month view on a compact screen
- **THEN** all covered marks fit without horizontal chart scrolling, date ticks remain readable without requiring every date label, and every value remains available in the scrollable list

#### Scenario: Update after reading changes
- **WHEN** a valid reading is added, edited, or deleted
- **THEN** the chart, numeric list, and independently calculated total reflect the same updated snapshot without resetting the selected period or tab

#### Scenario: Preserve precision and accessible values
- **WHEN** an interval allocates 1 cubic metre over 3 days
- **THEN** the Consumption total remains 1 cubic metre, the numeric list uses the existing localized display rounding, and dates and values remain accessible without relying on chart color or pixel heights

### Requirement: Inspect consumption bars
Long-pressing a covered bar or known-zero mark SHALL show a tooltip at the chart top, clear of the finger, with its localized weekday (Week), two-digit day (Month), or month name (Year) and consumption in cubic metres. Values SHALL use original buckets and existing localized row rounding, never normalized coordinates. Ordinary swipes SHALL navigate pages, including over the chart. Inspection SHALL reset on release, cancellation, or new data/period snapshots.

#### Scenario: Inspect a monthly day
- **WHEN** the user long-presses the October 4 bar with 4 cubic metres of consumption
- **THEN** the tooltip displays `04` and `4 m³` in the active locale

#### Scenario: Inspect a weekly or yearly bar
- **WHEN** the user long-presses a covered bar in Week or Year
- **THEN** the tooltip displays its weekday or month name respectively, with its corresponding consumption and no repeated year

#### Scenario: Inspect genuine zero
- **WHEN** the user long-presses a covered known-zero mark
- **THEN** the tooltip shows that bucket's date label and zero consumption, never an unknown bucket or a positive normalized value

#### Scenario: Keep inspection clear of the finger
- **WHEN** the user long-presses a short bar or a zero mark near the chart baseline
- **THEN** the tooltip remains at the top of the chart rather than following the bar height or overlapping the pressing finger

#### Scenario: Navigate after inspection
- **WHEN** the user swipes horizontally outside the chart, drags vertically from the chart, or changes the period or reading snapshot
- **THEN** page navigation and scrolling continue to work and no obsolete tooltip survives the snapshot change

### Requirement: Chart inspection gesture priority
Only long-press inspection SHALL capture chart drags. After a long press, sliding across bars SHALL update the tooltip without switching to History until release or cancellation. Ordinary horizontal swipes SHALL navigate pages even when starting on the chart; ordinary vertical drags SHALL scroll the page. A quick tap SHALL NOT show the tooltip.

#### Scenario: Slide across consumption bars
- **WHEN** the user long-presses the chart and then drags left or right across bars
- **THEN** the tooltip follows the selected bucket and shows its date/value while Dashboard remains selected

#### Scenario: Continue navigating outside the chart
- **WHEN** the user swipes without a long press, inside or outside the chart, or taps History
- **THEN** the app opens History without changing readings or the selected calendar period

### Requirement: Immediate reading-date feedback
Duplicate-date feedback SHALL appear only in the date picker, immediately for a conflicting selection without requiring Save or a meter value. Opening Add/Edit SHALL NOT show that warning in the parent form. Date checks SHALL share final-save rules and exclude the edited reading. Duplicate selections SHALL NOT be confirmable; valid selections SHALL clear the feedback. Save SHALL retain final validation and open the picker if it rejects a duplicate, preserving the draft.

#### Scenario: Select an existing date
- **WHEN** the user selects or enters a date already used by another reading
- **THEN** the picker immediately shows the duplicate-date message and disables date confirmation, without waiting for Save

#### Scenario: Open Add on an already recorded day
- **WHEN** Add defaults to a date already used by a reading
- **THEN** the parent form shows no duplicate-date warning, and opening date selection shows the conflict inside the picker

#### Scenario: Save an unchanged duplicate default
- **WHEN** the user submits a draft whose default date already belongs to another reading
- **THEN** no reading is saved, the picker opens with duplicate feedback, and dismissing it returns to the unchanged draft without a duplicate warning in the parent form

#### Scenario: Edit without changing the date
- **WHEN** the user edits a reading's meter value in History
- **THEN** the original date remains read-only and its own identity does not cause a duplicate-date error

#### Scenario: Correct a duplicate selection
- **WHEN** the user replaces a duplicate picker selection with an available valid date
- **THEN** the duplicate error clears and date confirmation becomes available without changing any saved reading

### Requirement: Target-bound reading dialogs
At most one Add, Edit, or deletion confirmation SHALL be active per tracker session. Pending actions and their errors SHALL belong to that dialog and target. Pending mutations SHALL block dismissal and duplicate submission. Configuration restoration SHALL retain the matching draft or confirmation; successful completion SHALL close it without reopening on restoration. A fresh tracker session SHALL discard previous transient dialogs.

#### Scenario: Preserve a pending save across rotation
- **WHEN** the user submits a draft and rotates before the save completes
- **THEN** the same draft remains visible and disabled, no second submission is made, and only its matching committed success closes it

#### Scenario: Retain an unsuccessful action
- **WHEN** validation, a missing target, or an expected storage failure prevents a mutation
- **THEN** the matching draft or deletion confirmation remains available with its existing error behavior, and unrelated targets do not receive that error

#### Scenario: Dismiss or replace a dialog
- **WHEN** the user dismisses a non-pending dialog and later opens another reading action
- **THEN** the old draft, picker state, and errors do not appear in the new dialog and no second dialog is simultaneously active

#### Scenario: Restore after completion or session replacement
- **WHEN** saved UI state is restored after a dialog completed, or against a newly created tracker session
- **THEN** no completed or previous-session draft, confirmation, or pending action reappears

### Requirement: Atomic final reading validation
Final saving SHALL resolve the target, preserve an edit's identity and original date, validate uniqueness and chronological neighbours against the latest committed readings, and write atomically. Locale-aware parsing SHALL retain current decimal precision, accepted input, and field-error semantics. Picker feedback SHALL remain advisory and SHALL NOT replace final transactional validation.

#### Scenario: Competing saves use the latest neighbours
- **WHEN** two saves race for the same date or for values that cannot both fit chronological order
- **THEN** only saves valid against their transaction's latest readings commit, and rejected saves leave committed data unchanged

#### Scenario: Parse and report independent fields
- **WHEN** a submission has both an invalid date and invalid meter text
- **THEN** both applicable field errors are retained with the existing validation precedence and no write occurs

#### Scenario: Ignore a supplied replacement edit date
- **WHEN** an edit submission supplies a date different from its persisted target
- **THEN** validation and saving use the original persisted date and identity, or report a missing target if it no longer exists

### Requirement: Ordered committed tracker snapshots
History and consumption SHALL derive from the same committed snapshot. Mutation success SHALL follow commit and make its committed state available immediately without resetting tab or period. Delayed older observation SHALL NOT replace newer accepted data. Repeated delivery of identical readings SHALL NOT produce a second data-driven projection or reset chart inspection.

#### Scenario: Observation arrives after a newer mutation
- **WHEN** an older captured snapshot is delivered after a newer mutation snapshot
- **THEN** history and consumption remain on the newer committed state without a temporary reversal

#### Scenario: Observation repeats a committed mutation
- **WHEN** the same committed readings arrive through mutation completion and observation
- **THEN** the action completes once, history and consumption stay consistent, and duplicate delivery does not reset chart inspection

#### Scenario: Concurrent tracker sessions
- **WHEN** two active tracker sessions mutate the same local tracker
- **THEN** their successful operations commit atomically and each session converges to the latest committed readings without losing its own tab or period selection

### Requirement: Independent observation and mutation outcomes
An observation failure SHALL preserve the last accepted committed data and any active draft or confirmation, and offer load retry. A confirmed commit SHALL remain successful even if observation fails before or after its result arrives. Observation failure SHALL NOT cause an already committed action to be offered as a failed mutation for resubmission. Mutations SHALL stay blocked until observation is ready again.

#### Scenario: Commit succeeds while observation fails
- **WHEN** observation fails while a save or delete is in flight and that mutation subsequently reports a committed result
- **THEN** its committed snapshot is retained, its dialog closes, the load error and retry remain available, and no mutation retry is requested

#### Scenario: Retry observation with an unsuccessful draft
- **WHEN** observation fails while a draft or deletion confirmation is open and the user retries loading
- **THEN** the dialog contents remain intact, submission stays blocked during loading, and successful observation re-enables eligible actions without resubmitting them

### Requirement: Classified local reading failures
Expected storage-access and invalid stored-data failures SHALL have distinct privacy-safe categories with generic errors and non-destructive retry. Persisted collections SHALL be validated before publication or mutation. Cancellation and unrelated defects SHALL propagate unchanged. Failure handling SHALL NOT transmit diagnostics or delete, repair, or expose reading data.

#### Scenario: Malformed stored reading
- **WHEN** a persisted identity, date, or decimal value cannot be decoded into a valid reading
- **THEN** loading or the transaction reports a stored-data category, retains its cause internally, shows the existing generic storage error, and does not reset or partially modify the database

#### Scenario: Infrastructure failure
- **WHEN** an expected SQL or I/O failure prevents a load or commit
- **THEN** a storage-access category is retained, the existing retry behavior remains available, and drafts and previously committed data are preserved

#### Scenario: Individually valid rows form an invalid collection
- **WHEN** stored rows decode individually but their chronological cumulative values decrease, or their collection otherwise violates identity/date uniqueness
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
