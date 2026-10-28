# Spec Delta

## ADDED Requirements

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
