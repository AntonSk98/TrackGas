# Spec Delta

## MODIFIED Requirements

### Requirement: Cohesive light visual hierarchy
The interface SHALL use warm off-white backgrounds, white cards, teal accents, and consistent typography, spacing, and controls across Dashboard, History, Add/Edit, and delete confirmation. Dashboard SHALL emphasize the selected period and consumption total. History SHALL emphasize reading dates and values with identifiable Edit/Delete actions. Consumption and temperature metrics SHALL each place a title above a separate value row; their title typography SHALL match, and the temperature value SHALL use the line chart's color. Spacing between metric values and charts SHALL remain compact. The local-storage disclosure SHALL NOT appear on Dashboard or History.

#### Scenario: Browse a populated dashboard
- **WHEN** the user views a period with consumption
- **THEN** Weekly/Monthly/Yearly selection, a prominent Consumption total, charts with matching title styling, date-range navigation, and supporting values appear in that order beneath the shared toolbar and tabs, with date-range navigation directly beneath the charts inside its card

#### Scenario: Manage a reading with consistent styling
- **WHEN** the user opens History, edits a reading, or requests deletion
- **THEN** the list and dialogs share the same visual language, Edit/Delete remain identifiable, validation errors remain inline, and deletion still requires explicit confirmation

#### Scenario: Understand temporary storage
- **WHEN** the user opens either Dashboard or History
- **THEN** no local-storage disclosure is shown, and the app continues storing readings locally without an on-screen notice

### Requirement: Meter-only History editing
The History editor SHALL allow changes to the meter value and an optional note. It SHALL show the original localized date in a read-only field labelled "Date", without "Reading date", "Choose date", or a date picker. Save SHALL retain the reading's original date and identity. Add SHALL retain its existing date-selection behavior and SHALL NOT offer a note field.

#### Scenario: Consistent Add and Edit date presentation
- **WHEN** the user opens Add or Edit
- **THEN** both forms show the same outlined Date field and meter field, without a separate Reading date label or Choose date text button; tapping the Add date opens its picker with a meaningful accessible button label, while the Edit date remains immutable

#### Scenario: Edit a meter value
- **WHEN** the user taps a History entry, changes its valid meter value or note, and saves
- **THEN** the value or note updates while its date, identity, History selection, and dashboard period remain unchanged

#### Scenario: Add retains its current form
- **WHEN** the user opens Add
- **THEN** the existing date and meter fields remain unchanged and no note field is shown

#### Scenario: Inspect the read-only date
- **WHEN** the user taps the Date field in the History editor
- **THEN** no date picker opens and the date cannot be changed

#### Scenario: Request deletion
- **WHEN** the user long-presses a History entry
- **THEN** the delete prompt identifies that reading, Cancel leaves it unchanged, and Delete removes it only after confirmation

### Requirement: Shared tracker options menu
The shared toolbar SHALL show a gear-shaped options icon immediately to the left of the existing top-right Add icon on Dashboard and History. It SHALL open a dismissible menu containing only the implemented options (Export CSV, Import CSV, Replace meter, Purge data, and Language), with no account or sign-in behavior. Purge data SHALL have a delete icon and Language SHALL have a language icon. Opening or dismissing the menu SHALL preserve readings, tab, and period.

#### Scenario: Open options from either tab
- **WHEN** the user taps the gear icon on Dashboard or History
- **THEN** the menu offers Export CSV, Import CSV, Replace meter, Purge data, and Language, and leaves Add in its existing rightmost position

#### Scenario: Dismiss without changes
- **WHEN** the user dismisses the menu with Back or an outside tap
- **THEN** the menu closes without opening a form or modifying data or navigation

#### Scenario: Accessible compact toolbar
- **WHEN** accessibility services focus the options icon or the menu is used on a compact screen with enlarged fonts
- **THEN** the icon exposes a meaningful localized label, its touch target is at least 48 dp, and menu text and actions remain readable and reachable

### Requirement: Explicit meter replacement form
Selecting Replace meter SHALL close the menu and open a modal showing today's read-only date. It SHALL explain that confirmation starts an independent, empty meter history on that date and require an explicit confirmation action. It SHALL NOT require a final old-meter reading or create a synthetic initial reading.

#### Scenario: Confirm an automatic zero baseline
- **WHEN** the user selects Replace meter and confirms
- **THEN** a new empty meter history starts today, existing readings remain in their prior histories, and no synthetic baseline or linked reading is created

#### Scenario: Missing final reading
- **WHEN** the current meter history has no reading and the user confirms
- **THEN** the replacement succeeds without a final reading, fabricating a reading, or displaying a missing-final warning

#### Scenario: Cancel replacement
- **WHEN** the user cancels, presses Back, or taps outside a non-pending replacement form
- **THEN** the replacement confirmation closes without creating an installation and existing readings remain unchanged

#### Scenario: Storage errors
- **WHEN** confirmation cannot commit
- **THEN** the replacement confirmation remains open with the existing generic storage-error treatment and permits retry

#### Scenario: Pending and restored replacement
- **WHEN** replacement is pending and the user rotates, attempts dismissal, or presses confirmation again
- **THEN** the matching disabled confirmation remains visible, dismissal and duplicate submission are blocked, and only its matching committed result closes it

#### Scenario: Shared visual and layout treatment
- **WHEN** the replacement dialog is opened in landscape or at 200% font size
- **THEN** its date, explanation, error feedback, and actions remain reachable using the existing light theme, localized formatting, scrolling, and system-inset behavior

## REMOVED Requirements

### Requirement: Identifiable meter boundaries in History
**Reason**: Replacing a meter now starts an independent empty history and no longer creates specially labelled or protected readings.
**Migration**: No local database migration is required for this unreleased app; reinstalling locally is acceptable. Any retained readings remain ordinary readings associated with their installation.

## ADDED Requirements

### Requirement: Compact independent meter entries
History SHALL present a reading's date and value in two rows, with an optional third row for its note. When multiple meter histories exist, the meter identifier SHALL be compactly displayed with the value rather than in a separate repeated role row.

#### Scenario: Read a history entry without a note
- **WHEN** the user views a reading without a note
- **THEN** its date and value fit in two rows and no separate meter-role row is shown

#### Scenario: Read a history entry with a note
- **WHEN** the user views a reading with a note
- **THEN** its date, value, and note appear in three rows

#### Scenario: Identify an independent meter
- **WHEN** History includes readings from multiple meter installations
- **THEN** each value remains associated with a compact meter identifier
