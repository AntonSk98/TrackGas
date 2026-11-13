# Spec Delta

## MODIFIED Requirements

### Requirement: Empty and partially covered consumption
The dashboard SHALL show consumption only for dates covered by intervals between consecutive readings of the same meter. A meter's first reading is a baseline and SHALL NOT itself contribute consumption. Uncovered dates SHALL remain unknown internally, not zero or extrapolated. Totals SHALL sum covered usage only.

#### Scenario: No readings or only a baseline
- **WHEN** there are no readings or a meter has only its starting value
- **THEN** no consumption total or series is displayed, the empty state and add action remain available, and any reading remains available in History

#### Scenario: Starting value on a replacement date
- **WHEN** a new meter starts with a nonzero reading on its replacement date
- **THEN** that starting value contributes no consumption on that date

#### Scenario: No coverage in the selected period
- **WHEN** the user navigates to a period outside all recorded intervals
- **THEN** consumption content is empty even if readings exist elsewhere

### Requirement: Create a dated cumulative reading
The app SHALL let the user create a reading with a date, observation time, and non-negative cumulative meter value. When no meter exists, saving the reading SHALL create a meter and save the reading as its starting value. The starting value SHALL establish the baseline and SHALL NOT contribute consumption.

#### Scenario: Save the first reading
- **WHEN** the user saves a valid reading while no meter exists
- **THEN** a meter and its starting reading are committed together, and no consumption is attributed to the starting value

#### Scenario: Add after a starting value
- **WHEN** a meter starts at 1250 on October 1 and the user records 1254 on October 2
- **THEN** October 2 has 4 cubic metres of consumption

#### Scenario: Save a backdated reading
- **WHEN** the user creates the first meter with a valid reading dated before today
- **THEN** that date and value establish the meter's starting baseline without invented consumption

#### Scenario: Add before the meter's start date
- **WHEN** the user selects a date before the first reading that established the meter
- **THEN** the reading is rejected with a message that the date is before the meter's start date, not that a reading already exists

#### Scenario: Add does not offer a note
- **WHEN** the user opens Add
- **THEN** the form offers date, time, and value fields without a note field

#### Scenario: Cancel creation
- **WHEN** the user dismisses an unsaved reading form
- **THEN** no reading or meter is created

#### Scenario: Add uses the current device date after a timezone change
- **WHEN** the device timezone changes while the app process remains alive and the user opens Add
- **THEN** its default date, observation time, timezone, and future-time validation use the device's new timezone

### Requirement: Edit and delete reading history
The app SHALL expose all readings newest first, independently of dashboard period selection. Edit SHALL preserve a reading's date, identity, and meter, allow observation-time and meter-value correction, and allow an optional note to be added, changed, or cleared. Add SHALL NOT offer a note field. Delete SHALL require confirmation. Deleting a meter's last reading SHALL also remove that meter; deleting a reading while others remain SHALL recalculate consumption only within its meter.

#### Scenario: Distinguish meter starting values in History
- **WHEN** a History entry is the starting reading recorded when its meter was created
- **THEN** it has a distinct localized visual marker from regular readings

#### Scenario: Edit date and value
- **WHEN** the user edits a reading, changes its valid meter value or observation time, enters a note, and saves
- **THEN** the existing reading is updated while its date, identity, and meter remain unchanged

#### Scenario: Clear an existing note
- **WHEN** the user edits a reading and removes its note
- **THEN** the reading is saved without a note

#### Scenario: Preserve the reading date and identity
- **WHEN** the user edits a reading
- **THEN** the original date remains read-only, the observation time is editable, and the reading is updated rather than duplicated

#### Scenario: Delete a reading while other meter readings remain
- **WHEN** the user confirms deletion of a reading and another reading remains in that meter
- **THEN** only the selected reading is removed, other meters remain unchanged, and same-meter consumption is recalculated

#### Scenario: Delete a reading from an independent history
- **WHEN** the user confirms deletion of any reading, including one created before or after a meter replacement
- **THEN** that reading is removed, other meters remain unchanged, and same-meter consumption is recalculated

#### Scenario: Delete the last reading of a meter
- **WHEN** the user confirms deletion of a meter's only remaining reading
- **THEN** the reading and its meter are removed atomically, with no empty meter left behind

#### Scenario: Delete the last reading in the tracker
- **WHEN** the user deletes the final reading across all meters
- **THEN** History is empty, the tracker has no meters, and a later Add creates a new meter with its starting value

### Requirement: Reading mutation completion
Successful edits and deletions SHALL refresh History and consumption immediately; cancelling SHALL leave data unchanged.

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
- **WHEN** deletion removes a meter's last reading
- **THEN** that meter contributes no interval, its reading and meter disappear together, and other meters remain unaffected

#### Scenario: Remove a meter's last reading
- **WHEN** deletion removes a meter's last reading
- **THEN** the reading and meter disappear together from the committed tracker

### Requirement: Consumption from chronological intervals
Consumption SHALL equal the later cumulative reading minus the earlier reading for every consecutive pair within the same meter sorted by date, irrespective of entry order. An interval SHALL cover dates after its earlier reading through its later date, inclusive. Its first date SHALL be excluded, preventing double counting at shared endpoints. A meter's first reading is only a baseline and SHALL NOT create consumption. Different meters SHALL never form an interval.

#### Scenario: Consecutive days
- **WHEN** readings are 1000 on October 1 and 1004 on October 2 in one meter
- **THEN** the interval total is 4 cubic metres, attributed to October 2

#### Scenario: Insert a middle reading
- **WHEN** 1003 on October 2 is inserted between 1000 on October 1 and 1012 on October 4 in one meter
- **THEN** the old interval is replaced by intervals totaling 3 and 9 cubic metres, and the entered cumulative values remain unchanged

#### Scenario: First new-meter reading on replacement date
- **WHEN** an old meter has readings of 45 on November 6 and 50 on November 7, and its replacement starts at 25 on November 7
- **THEN** November 7 includes the old meter's 5 cubic metres but does not count the new meter's starting value of 25

#### Scenario: First meter starting value
- **WHEN** the first meter starts at 25 on October 1 and the next reading is 28 on October 2
- **THEN** October 1 has no consumption from the starting value and October 2 has 3 cubic metres

#### Scenario: Usage after a replacement baseline
- **WHEN** a replacement meter starts at 25 on November 7 and reads 28 on November 8
- **THEN** November 8 has 3 cubic metres of new-meter consumption, with no interval between meters

#### Scenario: First reading after the replacement date remains a baseline
- **WHEN** a meter's first reading is dated after its start date and there is no later reading
- **THEN** that reading establishes a baseline only and contributes no consumption

#### Scenario: Replacement-day opening usage and skipped new-meter days
- **WHEN** the new meter starts at 6 on October 28 and reads 12 on October 31
- **THEN** the starting value contributes no consumption on October 28 and the 6-to-12 interval contributes 2 cubic metres per day on October 29, 30, and 31

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
- **WHEN** the user adds a reading after replacement
- **THEN** it belongs to the new meter and its consumption is calculated from the saved starting value

#### Scenario: Restart after replacement
- **WHEN** the user restarts the app offline after confirming replacement
- **THEN** the new meter and its starting reading remain current and all existing meter histories reload unchanged

#### Scenario: Multiple replacements over time
- **WHEN** the user replaces a meter again on a later date
- **THEN** another meter with its own starting reading is created while all earlier meters and readings remain available

#### Scenario: Second replacement on the same day
- **WHEN** a replacement is already committed for today and another replacement is requested
- **THEN** the app explains that today's replacement already exists and creates no additional meter or reading

#### Scenario: Date changes before confirmation
- **WHEN** the device date changes after the form opens but before confirmation
- **THEN** no replacement is committed under the stale date, the form explains that it must be reopened for the current date, and no existing reading is moved

#### Scenario: No meter exists
- **WHEN** the tracker has no readings and the user requests Replace meter
- **THEN** replacement is unavailable and the user can create a meter through Add

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
- **THEN** no meter histories or readings remain, and Dashboard opens on the current week with no old confirmation

#### Scenario: Begin again without historical constraints
- **WHEN** the user adds a valid first reading on a previously used date with a value lower than an erased reading
- **THEN** a new meter and starting reading are committed, contributing no consumption until a later reading is saved

### Requirement: Confirmed reset of independent tracking data
An explicitly confirmed purge SHALL atomically remove all readings and meters, leaving no current meter. Purging an already empty tracker SHALL succeed with the same valid empty state.

#### Scenario: Purge successive meter installations
- **WHEN** the user confirms purge with readings across multiple independent meters
- **THEN** all readings and meters are removed together and no partial tracker is published

#### Scenario: Purge an empty tracker
- **WHEN** the user confirms purge with no readings or meters
- **THEN** the operation completes normally and leaves no meter

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
