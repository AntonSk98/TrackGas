# Spec Delta

## MODIFIED Requirements

### Requirement: Meter-only History editing
The History editor SHALL allow changes to the meter value and an optional note. It SHALL show the original localized date in a read-only field labelled "Date", without "Reading date", "Choose date", or a date picker. Save SHALL retain the reading's original date and identity. Add SHALL retain its existing date-selection behavior and SHALL NOT offer a note field. When no meter exists, Add SHALL identify the meter value as its starting value.

#### Scenario: Consistent Add and Edit date presentation
- **WHEN** the user opens Add or Edit
- **THEN** both forms show the same outlined Date field and meter field, without a separate Reading date label or Choose date text button; tapping the Add date opens its picker with a meaningful accessible button label, while the Edit date remains immutable

#### Scenario: Edit a meter value
- **WHEN** the user taps a History entry, changes its valid meter value or note, and saves
- **THEN** the value or note updates while its date, identity, History selection, and dashboard period remain unchanged

#### Scenario: Add retains its current form
- **WHEN** the user opens Add
- **THEN** the existing date and meter fields remain unchanged and no note field is shown

#### Scenario: Identify a first meter's starting value
- **WHEN** the user opens Add while no meter exists
- **THEN** the meter-value field is labelled as the starting value and saving it creates the meter's baseline reading

#### Scenario: Inspect the read-only date
- **WHEN** the user taps the Date field in the History editor
- **THEN** no date picker opens and the date cannot be changed

#### Scenario: Request deletion
- **WHEN** the user long-presses a History entry
- **THEN** the delete prompt identifies that reading, Cancel leaves it unchanged, and Delete removes it only after confirmation

### Requirement: Explicit meter replacement form
When a meter exists, selecting Replace meter SHALL close the menu and open a minimal modal showing today's read-only date and a required starting-value field for the new meter. It SHALL NOT require a final old-meter reading or create a zero or other synthetic reading. When no meter exists, Replace meter SHALL be unavailable and Add SHALL remain available to create the first meter.

#### Scenario: Confirm an automatic zero baseline
- **WHEN** the user selects Replace meter, explicitly enters 0 as the starting value, and confirms
- **THEN** the new meter stores the user-supplied zero as its first reading; no zero is created automatically and the value is not counted as consumption

#### Scenario: Missing final reading
- **WHEN** the current meter has no reading today and the user confirms replacement with a valid starting value
- **THEN** the replacement succeeds without fabricating a final reading for the previous meter

#### Scenario: Confirm a supplied starting value
- **WHEN** the user selects Replace meter, enters a valid nonzero starting value, and confirms
- **THEN** a new meter and its first reading start today, existing readings remain in their prior meters, and the starting value is not counted as consumption

#### Scenario: No old-meter reading today
- **WHEN** the current meter has no reading today and the user confirms replacement with a valid starting value
- **THEN** the replacement succeeds without fabricating a final reading for the previous meter

#### Scenario: No meter exists
- **WHEN** the tracker has no readings
- **THEN** Replace meter is unavailable and Add can create the first meter with a starting value

#### Scenario: Cancel replacement
- **WHEN** the user cancels, presses Back, or taps outside a non-pending replacement form
- **THEN** the replacement confirmation closes without creating a meter or reading and existing readings remain unchanged

#### Scenario: Storage errors
- **WHEN** confirmation cannot commit
- **THEN** the replacement confirmation remains open with the existing generic storage-error treatment and permits retry

#### Scenario: Pending and restored replacement
- **WHEN** replacement is pending and the user rotates, attempts dismissal, or presses confirmation again
- **THEN** the matching disabled confirmation remains visible, dismissal and duplicate submission are blocked, and only its matching committed result closes it

#### Scenario: Shared visual and layout treatment
- **WHEN** the replacement dialog is opened in landscape or at 200% font size
- **THEN** its date, starting-value field, error feedback, and actions remain reachable using the existing light theme, localized formatting, scrolling, and system-inset behavior
