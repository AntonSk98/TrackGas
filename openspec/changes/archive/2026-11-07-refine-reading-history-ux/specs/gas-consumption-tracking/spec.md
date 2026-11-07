# Spec Delta

## MODIFIED Requirements

### Requirement: Empty and partially covered consumption
The dashboard SHALL show no consumption total or series when neither a reading interval nor replacement-start opening consumption overlaps the selected period, without sample values or invented totals. A lone reading SHALL establish only a baseline unless it is the first reading on a replacement installation's start date. Uncovered dates SHALL remain unknown internally, not zero or extrapolated. Totals SHALL sum available covered dates only. The empty state SHALL use concise localized text prompting the user to add at least two readings.

#### Scenario: No readings or only a baseline
- **WHEN** there are no readings, or only one reading
- **THEN** no consumption total or series is displayed, the concise empty state and add action remain available on Dashboard, period controls remain available, and any existing reading remains available in History

#### Scenario: No coverage in the selected period
- **WHEN** the user navigates to a period outside all recorded reading intervals
- **THEN** consumption content is empty and the empty state is shown without implying that existing readings elsewhere cover the selected period

### Requirement: Create a dated cumulative reading
The app SHALL provide an accessible add icon in the top-right toolbar. Its form SHALL request a date and cumulative meter reading in cubic metres, with today selected initially and a selectable earlier date. The Add form SHALL NOT offer a note field. Saving valid input SHALL add a reading and refresh consumption; cancelling SHALL leave readings unchanged.

#### Scenario: Save a backdated reading
- **WHEN** the user opens Add, changes today's date to an earlier valid date, enters a valid meter value, and saves
- **THEN** the reading appears with that date and value and the dashboard recalculates without resetting the selected period

#### Scenario: Add does not offer a note
- **WHEN** the user opens Add
- **THEN** the form retains its existing date and value fields without a note field

#### Scenario: Cancel creation
- **WHEN** the user dismisses an unsaved add form
- **THEN** no reading is added

#### Scenario: Add uses the current device date after a timezone change
- **WHEN** the device timezone changes while the app process remains alive and the user opens Add
- **THEN** its default date and future-date validation use today's date in the new timezone

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
The app SHALL expose all readings newest first, independently of dashboard period selection. Edit SHALL preserve the reading's date, identity, and installation, allow meter-value correction, and allow an optional note to be added, changed, or cleared. Add SHALL remain unchanged and SHALL NOT offer notes. Every reading SHALL be individually deletable after confirmation; deletion SHALL recalculate consumption only within its own installation.

#### Scenario: Edit date and value
- **WHEN** the user edits a reading, changes its valid meter value, enters a note, and saves
- **THEN** the existing reading is updated with the note while its date, identity, and installation remain unchanged

#### Scenario: Clear an existing note
- **WHEN** the user edits a reading and removes its note
- **THEN** the reading is saved without a note

#### Scenario: Preserve the reading date and identity
- **WHEN** the user edits a reading
- **THEN** the original date remains read-only and the reading is updated rather than duplicated

#### Scenario: Delete a reading from an independent history
- **WHEN** the user confirms deletion of any reading, including one created before or after a meter replacement
- **THEN** that reading is removed, other installations remain unchanged, and same-installation consumption is recalculated

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
- **THEN** cancelling preserves it and confirming removes it and recalculates consecutive intervals in that installation

#### Scenario: Remove the last usable interval
- **WHEN** deletion leaves fewer than two readings in an installation
- **THEN** that installation contributes no consumption interval while any remaining reading remains manageable and other installations remain unaffected

### Requirement: Consumption from chronological intervals
Consumption SHALL equal the later cumulative reading minus the earlier reading for every consecutive pair within the same installation sorted by date, irrespective of entry order. An interval SHALL cover dates after its earlier reading through its later reading, inclusive. Its first date SHALL be excluded, preventing double counting at shared endpoints. The first reading on a replacement installation's start date SHALL also contribute its cumulative value on that date as opening consumption, without persisting or exporting a synthetic reading. This amount SHALL be added to any other meter's consumption for that date. Different installations SHALL never form an interval.

#### Scenario: Consecutive days
- **WHEN** readings are 1000 on October 1 and 1004 on October 2 in the same installation
- **THEN** the interval total is 4 cubic metres, attributed to October 2

#### Scenario: Insert a middle reading
- **WHEN** 1003 on October 2 is inserted between 1000 on October 1 and 1012 on October 4 in one installation
- **THEN** the old interval is replaced by intervals totaling 3 and 9 cubic metres, and the entered cumulative values remain unchanged

#### Scenario: First new-meter reading on replacement date
- **WHEN** the old meter has readings of 45 on November 6 and 50 on November 7, replacement starts on November 7, and the new meter's first reading that day is 25
- **THEN** November 7 consumption is 30 cubic metres (the old meter's 5 plus the new meter's opening 25), with no interval created between installations

#### Scenario: First reading after the replacement date remains a baseline
- **WHEN** a new installation's first reading is dated after its start date and there is no later reading
- **THEN** it establishes a baseline only and contributes no consumption interval

#### Scenario: Replacement-day opening usage and skipped new-meter days
- **WHEN** the new installation starts on October 28 and its first reading is 6 that day, followed by 12 on October 31
- **THEN** 6 cubic metres contribute on October 28 and the actual 6-to-12 interval contributes 2 cubic metres per day on October 29, 30, and 31, without persisting a synthetic zero reading

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

### Requirement: Classified local reading failures
Expected storage-access and invalid stored-data failures SHALL have distinct privacy-safe categories with generic errors and non-destructive retry. Persisted readings and installations SHALL be validated before publication or mutation. Cancellation and unrelated defects SHALL propagate unchanged. Failure handling SHALL NOT transmit diagnostics or delete, repair, or expose reading data.

#### Scenario: Malformed stored reading
- **WHEN** a persisted identity, date, or decimal value cannot be decoded into a valid reading
- **THEN** loading or the transaction reports a stored-data category, retains its cause internally, shows the existing generic storage error, and does not reset or partially modify the database

#### Scenario: Infrastructure failure
- **WHEN** an expected SQL or I/O failure prevents a load or commit
- **THEN** a storage-access category is retained, the existing retry behavior remains available, and drafts and previously committed data are preserved

#### Scenario: Individually valid rows form an invalid collection
- **WHEN** stored rows decode individually but cumulative values decrease within one installation, identity or installation/date uniqueness is violated, or no current installation can be determined
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
- **WHEN** stored readings decrease only between independent installations after a valid meter replacement
- **THEN** the tracker loads normally because each installation is validated independently

### Requirement: Atomic replacement against current committed data
Replacement SHALL atomically validate its expected current installation and date, create a new empty installation, and make it current. A stale or competing operation SHALL NOT retarget a replacement silently. Success SHALL be acknowledged only after commit, and failure SHALL preserve the confirmation and last committed tracker.

#### Scenario: Partial storage failure
- **WHEN** storage fails while confirming replacement
- **THEN** no partial installation is retained, no current installation is lost, and the user can retry with the confirmation preserved

#### Scenario: Preserve an existing nonzero baseline
- **WHEN** the current installation has a nonzero reading and a replacement is confirmed
- **THEN** the old reading remains unchanged and no synthetic reading is created

#### Scenario: Competing replacements
- **WHEN** two tracker sessions confirm replacement for the same current installation and date
- **THEN** at most one replacement commits and the other reports an obsolete target without creating another installation

#### Scenario: Final reading changed while form is open
- **WHEN** another session changes readings or the current installation before replacement confirmation
- **THEN** the stale confirmation is rejected without writes and the user must review the current meter before confirming again

#### Scenario: Observation failure after commit
- **WHEN** replacement commits but observation fails before or after its result arrives
- **THEN** the committed replacement remains successful, its form closes, the last committed tracker is retained, and load retry does not resubmit replacement

### Requirement: Committed purge projections and durability
Purge success SHALL be acknowledged only after commit and immediately supply a complete committed empty tracker to History and Dashboard, preserving the selected tab and period. Older observations SHALL NOT restore purged data. The reset SHALL survive process death and offline restart. A new first reading SHALL establish a baseline without constraints from erased history or invented consumption.

#### Scenario: Refresh both tabs after commit
- **WHEN** purge commits while the user has selected History and a past dashboard period
- **THEN** History becomes empty, Dashboard has no chart, consumption rows, total, or obsolete inspection tooltip, and the selected tab and period remain unchanged

#### Scenario: Delayed old observation
- **WHEN** a pre-purge snapshot arrives after committed purge success
- **THEN** it cannot repopulate History or consumption, and a duplicate empty snapshot does not repeat completion

#### Scenario: Restart after reset
- **WHEN** the user restarts offline after committed purge
- **THEN** all meter histories remain empty, one installation remains current, and Dashboard opens on the current week with no old confirmation

#### Scenario: Begin again without historical constraints
- **WHEN** the user adds a valid first reading on a previously used date with a value lower than an erased reading
- **THEN** it is saved in the current installation and contributes only a baseline, while subsequent readings and replacements follow ordinary rules

### Requirement: Purge failure and lifecycle safety
A failed purge SHALL leave all prior records unchanged and retain the confirmation for explicit retry. Rotation and same-process session restoration SHALL retain pending work without resubmitting it; completion SHALL affect only its matching dialog and request. Observation failure SHALL NOT turn a committed purge into a failed mutation. Load retry SHALL remain non-destructive, and purge SHALL NOT bypass invalid stored-data validation.

#### Scenario: Roll back partial deletion
- **WHEN** storage fails or cancellation occurs before the purge commits, including during restoration of the empty current installation
- **THEN** all original readings and installations remain intact and no successful empty snapshot is issued; expected storage failures retain their classified retry behavior and cancellation propagates unchanged

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

## REMOVED Requirements

### Requirement: Boundary-safe reading management
**Reason**: Independent meter histories no longer create structural final/initial readings or protect any reading from ordinary deletion.
**Migration**: No local database migration is required for this unreleased app; reinstalling locally is acceptable. Any retained readings are ordinary entries in their respective installations.

### Requirement: Durable successive meter installations
**Reason**: This requirement is superseded by creating an empty independent installation on replacement; it does not define a distinct behavior beyond the replacement flow.
**Migration**: No local database migration is required for this unreleased app; reinstalling locally is acceptable.

### Requirement: Confirm replacement using today's final reading
**Reason**: Replacement no longer requires a final reading, creates no synthetic zero baseline, and does not protect endpoint readings.
**Migration**: No local database migration is required for this unreleased app; reinstalling locally is acceptable.

### Requirement: Confirmed full tracking-data reset
**Reason**: This requirement is superseded by the confirmed reset behavior for independent meter histories.
**Migration**: No local database migration is required for this unreleased app; reinstalling locally is acceptable.

## ADDED Requirements

### Requirement: Start replacement as an independent meter history
Replacement SHALL take effect only after explicit confirmation for today's device-local date. It SHALL create a new empty current installation without requiring a final reading, creating a zero baseline, or linking readings across installations. At most one replacement SHALL be allowed per calendar date.

#### Scenario: Replace without a final reading
- **WHEN** the user confirms replacement and today's old-meter reading is absent
- **THEN** the new empty installation is created without changing or fabricating a reading in either history

#### Scenario: Replace with existing readings
- **WHEN** the current meter has readings and the user confirms replacement
- **THEN** those readings remain unchanged in the old installation and the new installation has no initial reading

#### Scenario: First reading after replacement
- **WHEN** the user adds the first reading after replacement
- **THEN** it belongs to the new current installation and establishes only that meter's baseline

#### Scenario: Restart after replacement
- **WHEN** the user restarts the app offline after confirming replacement
- **THEN** the new empty installation remains current and the existing meter histories reload unchanged

#### Scenario: Multiple replacements over time
- **WHEN** the user replaces a meter again on a later date
- **THEN** another empty installation becomes current while all earlier installations and readings remain available

#### Scenario: Second replacement on the same day
- **WHEN** a replacement is already committed for today and another replacement is requested
- **THEN** the app explains that today's replacement already exists and creates no additional installation

#### Scenario: Date changes before confirmation
- **WHEN** the device date changes after the form opens but before confirmation
- **THEN** no replacement is committed under the stale date, the form explains that it must be reopened for the current date, and no existing reading is moved

### Requirement: Confirmed reset of independent tracking data
An explicitly confirmed purge SHALL atomically remove all readings and historical installations, leaving exactly one empty current installation. Purging an already empty tracker SHALL succeed with the same valid empty state.

#### Scenario: Purge successive meter installations
- **WHEN** the user confirms purge with readings across multiple independent installations
- **THEN** all readings and historical installations are removed together, exactly one empty installation remains current, and no partial tracker is published

#### Scenario: Purge an empty tracker
- **WHEN** the user confirms purge with no readings
- **THEN** the operation completes normally and leaves exactly one empty current installation

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
History SHALL show each reading in two rows: its date and a row containing its value. When multiple installations exist, a compact meter identifier SHALL remain available with the value. A saved note SHALL appear as a third row; a reading without a note SHALL NOT reserve a note row.

#### Scenario: History without notes
- **WHEN** History contains a reading without a note
- **THEN** the entry displays its date and value in two rows without repeating “meter” or “reading” labels

#### Scenario: History with a note
- **WHEN** History contains a reading with a saved note
- **THEN** its date, value, and note appear in three rows

#### Scenario: Distinguish independent meters
- **WHEN** History contains readings from more than one installation
- **THEN** each value remains associated with a compact meter identifier without adding a separate repeated role row

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
