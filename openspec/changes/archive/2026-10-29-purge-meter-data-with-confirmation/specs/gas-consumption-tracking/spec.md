# Spec Delta

## MODIFIED Requirements

### Requirement: Target-bound reading dialogs
At most one Add, Edit, deletion confirmation, replacement, or tracking-data purge confirmation SHALL be active per tracker session. Pending actions and their errors SHALL belong to that dialog and target, including the expected installation for replacement and the whole tracker for purge. Pending mutations SHALL block dismissal and duplicate submission.

#### Scenario: Retain an unsuccessful action
- **WHEN** validation, a missing or obsolete target, or an expected storage failure prevents a mutation
- **THEN** the matching draft or confirmation remains available with its error behavior, and unrelated targets do not receive that error

#### Scenario: Dismiss or replace a dialog
- **WHEN** the user dismisses a non-pending dialog and later opens another reading, replacement, or purge action
- **THEN** the old draft, picker state, and errors do not appear in the new dialog and no second dialog is simultaneously active

## ADDED Requirements

### Requirement: Confirmed full tracking-data reset
An explicitly confirmed purge SHALL atomically remove all readings, replacement boundaries, and historical installations, leaving exactly one empty original installation as the current meter. Protected replacement endpoints SHALL be removable by this operation without weakening ordinary deletion protection. Purging an already empty tracker SHALL succeed with the same valid empty state.

#### Scenario: Purge successive meter installations
- **WHEN** the user confirms purge with readings across multiple replacements
- **THEN** all readings, final/initial endpoints, and replacement history are removed together, exactly one empty original installation remains current, and no partial tracker is published

#### Scenario: Purge an empty tracker
- **WHEN** the user confirms purge with no readings or replacement history
- **THEN** the operation completes normally and leaves exactly one empty original installation

#### Scenario: Ordinary deletion stays protected
- **WHEN** the user requests ordinary deletion of a replacement endpoint without confirming a full purge
- **THEN** the endpoint remains protected and no installation or replacement record is removed

### Requirement: Committed purge projections and durability
Purge success SHALL be acknowledged only after commit and immediately supply a complete committed empty tracker to History and Dashboard, preserving the selected tab and period. Older observations SHALL NOT restore purged data. The reset SHALL survive process death and offline restart. A new first reading SHALL establish a baseline without constraints from erased history or invented consumption.

#### Scenario: Refresh both tabs after commit
- **WHEN** purge commits while the user has selected History and a past dashboard period
- **THEN** History becomes empty, Dashboard has no chart, consumption rows, total, or obsolete inspection tooltip, the selected tab and period remain unchanged, and the confirmation closes

#### Scenario: Delayed old observation
- **WHEN** a pre-purge snapshot arrives after committed purge success
- **THEN** it cannot repopulate history or consumption, and a duplicate empty snapshot does not repeat completion

#### Scenario: Restart after reset
- **WHEN** the user restarts offline after committed purge
- **THEN** history and replacement history remain empty, one original installation remains current, and Dashboard opens on the current week with no old confirmation

#### Scenario: Begin again without historical constraints
- **WHEN** the user adds a valid first reading on a previously used date with a value lower than an erased reading
- **THEN** it is saved in the original current installation and contributes only a baseline, while subsequent readings and replacements follow ordinary rules

### Requirement: Purge failure and lifecycle safety
A failed purge SHALL leave all prior records unchanged and retain the confirmation for explicit retry. Rotation and same-process session restoration SHALL retain pending work without resubmitting it; completion SHALL affect only its matching dialog and request. Observation failure SHALL NOT turn a committed purge into a failed mutation. Load retry SHALL remain non-destructive, and purge SHALL NOT bypass invalid stored-data validation.

#### Scenario: Roll back partial deletion
- **WHEN** storage fails or cancellation occurs before the purge commits, including during restoration of the empty original installation
- **THEN** all original readings, boundaries, and installations remain intact and no successful empty snapshot is issued; expected storage failures retain their classified retry behavior and cancellation propagates unchanged

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
- **WHEN** another session submits an edit or replacement targeting erased data, or adds a new valid reading after purge
- **THEN** stale edits and replacement targets are rejected by normal target validation, valid new additions can commit, and every session converges to the latest committed tracker
