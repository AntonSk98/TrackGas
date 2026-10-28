# Spec Delta

## MODIFIED Requirements

### Requirement: Dismiss reading drafts outside the form
While no reading mutation is pending, the Add/Edit dialog SHALL dismiss and discard its unsaved draft when the user taps outside its visible form surface. Interactions within the form SHALL NOT dismiss it. Back and Cancel SHALL remain available except while a mutation is pending; dismissing the nested date picker SHALL return to the unsaved form rather than save or discard it.

#### Scenario: Tap outside Add
- **WHEN** the user enters an unsaved Add draft and taps the surrounding backdrop
- **THEN** the form closes without creating a reading or changing the selected tab or period

#### Scenario: Interact within the form
- **WHEN** the user taps the form surface, input, or date action
- **THEN** the form remains open unless an explicit dismissal or successful Save occurs

#### Scenario: Dismiss only the date picker
- **WHEN** the user dismisses the date picker without confirming
- **THEN** the parent form remains open with its previous draft intact

#### Scenario: Attempt dismissal during save
- **WHEN** a save is pending and the user taps outside the form, presses Back, or attempts Cancel
- **THEN** the form and draft remain present, Cancel is disabled, and the same submission continues without a second mutation

## ADDED Requirements

### Requirement: Process-local tracker retention
Each activity's tracker session SHALL survive configuration changes and non-finishing activity destruction within the same process while its associated task remains active. Distinct activity tracker sessions SHALL remain independent. Finishing an activity outside configuration change or pruning an abandoned task SHALL release its retained session. A new process SHALL create a fresh session without losing committed readings.

#### Scenario: Recreate within an active task
- **WHEN** an activity is destroyed without finishing and recreated with its saved tracker identity while its task and process remain active
- **THEN** it retains its tracker session, selected tab and period, and any pending operation rather than starting a duplicate operation

#### Scenario: Release one of several sessions
- **WHEN** one activity finishes or its task is abandoned while another tracker activity remains active
- **THEN** the released session is cleared without clearing the other activity's tracker or the shared committed readings

#### Scenario: Restore after process death
- **WHEN** saved activity state is restored in a fresh process
- **THEN** committed readings reload, Dashboard and the current week are selected, and old drafts, confirmations, and operation results are not restored into the new session
