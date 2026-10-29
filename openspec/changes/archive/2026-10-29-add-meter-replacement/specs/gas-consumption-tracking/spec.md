# Spec Delta

## ADDED Requirements

### Requirement: Durable successive meter installations
The local tracker SHALL retain successive meter installations, each reading's installation, and explicit replacement boundaries. Exactly one installation SHALL be current. An upgrade from a tracker without replacement support SHALL preserve all existing identities, dates, and exact decimal values in the original installation without resetting local data.

#### Scenario: Upgrade an existing tracker
- **WHEN** a user upgrades with existing readings
- **THEN** all readings remain in one original current installation and their history and consumption remain unchanged

#### Scenario: Restart after replacement
- **WHEN** the user confirms a replacement and restarts the app offline
- **THEN** both installations and their boundary readings reload, the new installation remains current, and consumption is recalculated without cross-meter subtraction

#### Scenario: Multiple replacements over time
- **WHEN** a meter is replaced again on a later day after a final reading has been saved for that day
- **THEN** another installation is created while all earlier installations, boundaries, and readings remain intact

### Requirement: Confirm replacement using today's final reading
Replacement SHALL take effect only today in the device's local date and SHALL require today's saved reading in the current installation as its final reading. Explicit confirmation SHALL automatically create a new installation with a zero baseline on the same date, without requesting a new-meter observation. That baseline SHALL NOT be compared to the old meter. At most one replacement SHALL be allowed per calendar date.

#### Scenario: Replace without entering a new-meter value
- **WHEN** today's old-meter final reading is 1105 and the user confirms replacement without entering a new-meter value
- **THEN** the old installation ends with 1105, the new installation begins with 0 today, and the replacement commits without weakening either installation's chronology

#### Scenario: First reading on the following day
- **WHEN** replacement establishes zero on October 28 and the user saves a new-meter value of 5 on October 29
- **THEN** October 29 consumption is 5 - 0 = 5 cubic metres, with no new-meter value required from the user on October 28

#### Scenario: Missing final reading
- **WHEN** today's current-meter reading is absent
- **THEN** confirmation reports the missing-final prerequisite and changes no committed state

#### Scenario: Second replacement on the same day
- **WHEN** a replacement is already committed for today and another replacement is requested
- **THEN** the app explains that today's replacement already exists and creates neither a second boundary nor another installation

#### Scenario: Date changes before confirmation
- **WHEN** the device date changes after the form opens but before confirmation
- **THEN** no replacement is committed under the stale date, the form explains that it must be reopened for the current date, and no existing reading is moved

### Requirement: Atomic replacement against current committed data
Replacement SHALL atomically validate its expected current installation and final reading, close that installation, and create the next installation and zero baseline. A stale or competing operation SHALL NOT retarget a replacement silently. Success SHALL be acknowledged only after commit, and failure SHALL preserve the confirmation and last committed tracker.

#### Scenario: Preserve an existing nonzero baseline
- **WHEN** a stored or corrected baseline has a nonzero value and a later replacement is confirmed
- **THEN** its exact value remains unchanged; only the newly created baseline is zero

#### Scenario: Partial storage failure
- **WHEN** storage fails while confirming replacement
- **THEN** no partial installation or baseline is retained, no current installation is lost, and the user can retry with the confirmation preserved

#### Scenario: Competing replacements
- **WHEN** two tracker sessions confirm replacement for the same current installation
- **THEN** at most one replacement commits and the other reports an obsolete target without creating another installation

#### Scenario: Final reading changed while form is open
- **WHEN** another session edits or deletes the displayed final reading before replacement confirmation
- **THEN** the stale confirmation is rejected without writes and the user must review the latest final reading before confirming again

#### Scenario: Observation failure after commit
- **WHEN** replacement commits but observation fails before or after its result arrives
- **THEN** the committed replacement remains successful, its form closes, the last committed tracker is retained, and load retry does not resubmit replacement

### Requirement: Boundary-safe reading management
The saved final old-meter reading and initial new-meter reading SHALL remain structural endpoints of their replacement. Their values SHALL be editable under their own installation's rules; their identities, dates, installation membership, and roles SHALL remain immutable. Ordinary deletion SHALL reject either endpoint without writes. Deleting other readings SHALL never erase a boundary or reconnect separate installations.

#### Scenario: Edit the initial reading
- **WHEN** the user changes a baseline from 0 to 1 with a subsequent reading of 3 in the same installation
- **THEN** the edit is accepted and subsequent measured usage becomes 2 without changing the old installation's consumption

#### Scenario: Invalid baseline correction
- **WHEN** the user attempts to change an initial reading above a later reading in its installation
- **THEN** the edit is rejected and committed values remain unchanged

#### Scenario: Delete a structural endpoint
- **WHEN** deletion targets either replacement endpoint, including through a stale dialog
- **THEN** the operation reports that this is a protected boundary reading and preserves both installations and their readings

#### Scenario: Delete all non-boundary readings of an installation
- **WHEN** ordinary readings are deleted leaving only structural endpoints
- **THEN** the endpoints and installations persist, and only valid remaining same-installation intervals contribute consumption

## MODIFIED Requirements

### Requirement: Validate reading input and chronology
The app SHALL allow one reading per date per installation, today or earlier, with a finite non-negative decimal value in cubic metres. Values SHALL never decrease within an installation; equal values are valid. Add and Edit SHALL validate against both neighbours in that installation, excluding the edited note. Only explicit replacement SHALL create two installations' endpoints on one date. Invalid input SHALL show a helpful error without modifying saved readings.

#### Scenario: Invalid fields
- **WHEN** the user submits an empty date or value, malformed number, negative value, future date, or duplicate date in the applicable installation
- **THEN** the app identifies the invalid field and does not save

#### Scenario: Backdated reading violates the next reading
- **WHEN** readings in one installation are 100 on October 1 and 120 on October 4, and the user attempts to insert 125 on October 2
- **THEN** saving is rejected because the value exceeds the following reading

#### Scenario: Edit violates previous reading
- **WHEN** the user edits a reading to be less than its chronological predecessor in the same installation
- **THEN** saving is rejected and the original note remains unchanged

#### Scenario: Decimal and equal readings
- **WHEN** the user saves 120.125 after a previous reading of 120.125 on an earlier date in the same installation
- **THEN** the reading is accepted and the intervening interval has zero consumption

#### Scenario: Ordinary Add cannot bypass validation
- **WHEN** the user submits a lower value through ordinary Add without an explicit replacement
- **THEN** the previous-reading validation rejects it without creating an installation or permitting a one-use decrease

#### Scenario: Resolve a backdated reading
- **WHEN** the user adds a date before a replacement after a new installation has become current
- **THEN** the reading is assigned to the installation covering that date and checked against that installation's neighbours

#### Scenario: Ordinary Add on replacement day
- **WHEN** the user attempts ordinary Add for a replacement date
- **THEN** the date is rejected as occupied, rather than adding a third reading or ambiguously choosing an installation

### Requirement: Edit and delete reading history
The app SHALL expose all recorded notes, newest first, independently of dashboard period selection, with Edit and Delete actions except that structural replacement endpoints SHALL reject deletion with an explanation. Edit SHALL prefill the existing date and value, allow changes only to the meter value, show the original date read-only, and preserve the note's original date, identity, and installation on save. Delete SHALL require confirmation. Successful edits and deletions SHALL refresh history and consumption immediately; cancelling SHALL leave data unchanged.

#### Scenario: Edit date and value
- **WHEN** the user opens Edit, sees the original date read-only, changes the meter value to a valid input, and saves
- **THEN** the existing note is updated rather than duplicated, its original date, identity, and installation remain unchanged, and adjacent same-installation intervals are recalculated

#### Scenario: Confirm or cancel deletion
- **WHEN** the user selects Delete for a non-boundary reading
- **THEN** cancelling preserves the note and confirming removes it and recalculates the remaining consecutive same-installation intervals

#### Scenario: Remove the last usable interval
- **WHEN** deletion leaves fewer than two readings in an installation
- **THEN** that installation contributes no consumption interval while any remaining baseline note is still manageable and other installations remain unaffected

### Requirement: Consumption from chronological intervals
Consumption SHALL equal the later cumulative reading minus the earlier reading for every consecutive pair within the same installation sorted by date, irrespective of entry order. An interval SHALL cover dates after its earlier reading through its later reading, inclusive. Its first date SHALL be excluded, preventing double counting at shared endpoints. The original entered readings SHALL remain available separately from calculated usage. Different installations SHALL never form an interval.

#### Scenario: Consecutive days
- **WHEN** readings are 1000 on October 1 and 1004 on October 2 in the same installation
- **THEN** the interval total is 4 cubic metres, attributed to October 2

#### Scenario: Insert a middle reading
- **WHEN** 1003 on October 2 is inserted between 1000 on October 1 and 1012 on October 4 in one installation
- **THEN** the old interval is replaced by intervals totaling 3 and 9 cubic metres, and the entered cumulative values remain unchanged

#### Scenario: Replacement preserves the established day convention
- **WHEN** the old meter reads 1100 on October 27 and finishes at 1105 on October 28, the new meter starts at 0 on October 28 and reads 3 on October 29
- **THEN** October 28 shows 5 cubic metres, October 29 shows 3, and the combined covered total is 8, without an interval between 1105 and 0

#### Scenario: New baseline alone adds no consumption
- **WHEN** replacement creates a new baseline but no later new-meter reading has been saved
- **THEN** old-meter covered usage remains available and the new baseline contributes neither a consumption interval nor an invented zero-usage day

#### Scenario: Automatic zero baseline and skipped new-meter days
- **WHEN** replacement automatically starts a new meter at zero on October 28 and the user next records 6 on October 31
- **THEN** October 29, 30, and 31 each receive 2 cubic metres, with total 6 and existing estimated-distribution metadata

### Requirement: Immediate reading-date feedback
Duplicate-date feedback SHALL appear only in the date picker, immediately for a conflicting selection without requiring Save or a meter value. Checks SHALL use the installation covering the selected date and treat replacement dates as occupied for ordinary Add. Opening Add/Edit SHALL NOT show that warning in the parent form. Date checks SHALL share final-save rules and exclude the edited reading. Duplicate selections SHALL NOT be confirmable; valid selections SHALL clear the feedback. Save SHALL retain final validation and open the picker if it rejects a duplicate, preserving the draft.

#### Scenario: Select an existing date
- **WHEN** the user selects or enters a date already used by another reading in the applicable installation or occupied by a replacement
- **THEN** the picker immediately shows the duplicate-date message and disables date confirmation, without waiting for Save

#### Scenario: Open Add on an already recorded day
- **WHEN** Add defaults to a date already used in its applicable installation
- **THEN** the parent form shows no duplicate-date warning, and opening date selection shows the conflict inside the picker

#### Scenario: Save an unchanged duplicate default
- **WHEN** the user submits a draft whose default date already belongs to another reading in its applicable installation
- **THEN** no reading is saved, the picker opens with duplicate feedback, and dismissing it returns to the unchanged draft without a duplicate warning in the parent form

#### Scenario: Edit without changing the date
- **WHEN** the user edits a reading's meter value in History
- **THEN** the original date remains read-only and its own identity and a counterpart endpoint in another installation do not cause a duplicate-date error

#### Scenario: Correct a duplicate selection
- **WHEN** the user replaces a duplicate picker selection with an available valid date
- **THEN** the duplicate error clears and date confirmation becomes available without changing any saved reading

### Requirement: Target-bound reading dialogs
At most one Add, Edit, deletion confirmation, or replacement dialog SHALL be active per tracker session. Pending actions and their errors SHALL belong to that dialog and target, including the expected installation for replacement. Pending mutations SHALL block dismissal and duplicate submission. Configuration restoration SHALL retain the matching draft or confirmation; successful completion SHALL close it without reopening on restoration. A fresh tracker session SHALL discard previous transient dialogs.

#### Scenario: Preserve a pending save across rotation
- **WHEN** the user submits a draft and rotates before the save completes
- **THEN** the same draft remains visible and disabled, no second submission is made, and only its matching committed success closes it

#### Scenario: Retain an unsuccessful action
- **WHEN** validation, a missing or obsolete target, or an expected storage failure prevents a mutation
- **THEN** the matching draft or confirmation remains available with its error behavior, and unrelated targets do not receive that error

#### Scenario: Dismiss or replace a dialog
- **WHEN** the user dismisses a non-pending dialog and later opens another reading or replacement action
- **THEN** the old draft, picker state, and errors do not appear in the new dialog and no second dialog is simultaneously active

#### Scenario: Restore after completion or session replacement
- **WHEN** saved UI state is restored after a dialog completed, or against a newly created tracker session
- **THEN** no completed or previous-session draft, confirmation, or pending action reappears

### Requirement: Ordered committed tracker snapshots
History and consumption SHALL derive from the same committed snapshot of readings, installations, and boundaries. Mutation success SHALL follow commit and make its committed state available immediately without resetting tab or period. Delayed older observation SHALL NOT replace newer accepted data. Repeated delivery of identical tracker data SHALL NOT produce a second data-driven projection or reset chart inspection; installation or boundary changes SHALL count as data changes.

#### Scenario: Observation arrives after a newer mutation
- **WHEN** an older captured snapshot is delivered after a newer mutation snapshot
- **THEN** history and consumption remain on the newer committed state without a temporary reversal

#### Scenario: Observation repeats a committed mutation
- **WHEN** the same committed tracker arrives through mutation completion and observation
- **THEN** the action completes once, history and consumption stay consistent, and duplicate delivery does not reset chart inspection

#### Scenario: Concurrent tracker sessions
- **WHEN** two active tracker sessions mutate the same local tracker
- **THEN** their successful operations commit atomically and each session converges to the latest committed tracker without losing its own tab or period selection

### Requirement: Classified local reading failures
Expected storage-access and invalid stored-data failures SHALL have distinct privacy-safe categories with generic errors and non-destructive retry. Persisted collections, installations, and boundaries SHALL be validated before publication or mutation. Cancellation and unrelated defects SHALL propagate unchanged. Failure handling SHALL NOT transmit diagnostics or delete, repair, or expose reading data.

#### Scenario: Malformed stored reading
- **WHEN** a persisted identity, date, or decimal value cannot be decoded into a valid reading
- **THEN** loading or the transaction reports a stored-data category, retains its cause internally, shows the existing generic storage error, and does not reset or partially modify the database

#### Scenario: Infrastructure failure
- **WHEN** an expected SQL or I/O failure prevents a load or commit
- **THEN** a storage-access category is retained, the existing retry behavior remains available, and drafts and previously committed data are preserved

#### Scenario: Individually valid rows form an invalid collection
- **WHEN** stored rows decode individually but cumulative values decrease within one installation, identity or installation/date uniqueness is violated, or a boundary has missing endpoints, inconsistent dates, overlapping installation coverage, or no unique current installation
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
- **WHEN** stored readings decrease only between installations joined by a valid explicit boundary
- **THEN** the tracker loads normally, without treating that legitimate replacement as corrupted stored data
