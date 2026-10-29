# Spec Delta

## ADDED Requirements

### Requirement: Shared tracker options menu
The shared toolbar SHALL show a gear-shaped options icon immediately to the left of the existing top-right Add icon on Dashboard and History. It SHALL open a dismissible menu containing only the implemented "Replace meter" option, with no account or sign-in behavior. Opening or dismissing the menu SHALL preserve readings, tab, and period.

#### Scenario: Open options from either tab
- **WHEN** the user taps the gear icon on Dashboard or History
- **THEN** the menu opens beside the toolbar action, offers Replace meter, and leaves Add in its existing rightmost position

#### Scenario: Dismiss without changes
- **WHEN** the user dismisses the menu with Back or an outside tap
- **THEN** the menu closes without opening a form or modifying data or navigation

#### Scenario: Accessible compact toolbar
- **WHEN** accessibility services focus the options icon or the menu is used on a compact screen with enlarged fonts
- **THEN** the icon exposes a meaningful localized label, its touch target is at least 48 dp, and menu text and actions remain readable and reachable

### Requirement: Explicit meter replacement form
Selecting Replace meter SHALL close the menu and open a modal showing today's read-only date and the saved final old-meter value, without asking for a new-meter value. The form SHALL explain that confirmation automatically starts the new installation at zero on that date and require an explicit "Confirm replacement" action. If today's old-meter reading is missing, it SHALL instead explain that the final reading must first be saved through Add.

#### Scenario: Confirm an automatic zero baseline
- **WHEN** today's old-meter final reading exists and the user selects Replace meter
- **THEN** the form identifies that saved reading, explains the automatic zero baseline, offers no new-meter input, and provides Confirm replacement and Cancel

#### Scenario: Missing final reading
- **WHEN** no final reading exists for the current meter today
- **THEN** the dialog explains the prerequisite and provides a route to ordinary Add without creating a meter or fabricating a final reading

#### Scenario: Cancel replacement
- **WHEN** the user cancels, presses Back, or taps outside a non-pending replacement form
- **THEN** the replacement confirmation closes without creating a baseline and the already saved old-meter reading remains unchanged

#### Scenario: Storage errors
- **WHEN** confirmation cannot commit
- **THEN** the replacement confirmation remains open with the existing generic storage-error treatment and permits retry without requesting a meter value

#### Scenario: Pending and restored replacement
- **WHEN** replacement is pending and the user rotates, attempts dismissal, or presses confirmation again
- **THEN** the matching disabled confirmation remains visible, dismissal and duplicate submission are blocked, and only its matching committed result closes it

#### Scenario: Shared visual and layout treatment
- **WHEN** the replacement dialog is opened in landscape with a keyboard or at 200% font size
- **THEN** its read-only date, explanation, error feedback, and actions remain reachable using the existing light theme, localized formatting, scrolling, and system-inset behavior

### Requirement: Identifiable meter boundaries in History
History SHALL distinguish installations and label replacement-day final and initial readings without relying on color. Same-date endpoints SHALL be ordered deterministically with the new-meter initial reading before the old-meter final reading in newest-first History. Boundary readings SHALL support value editing, but not ordinary deletion; attempting deletion SHALL explain why rather than offer a destructive confirmation.

#### Scenario: Inspect replacement day
- **WHEN** History includes a replacement on October 28
- **THEN** both October 28 endpoints remain visible with distinct meter and final/initial labels, with the automatically established new-meter baseline first

#### Scenario: Correct a boundary value
- **WHEN** the user taps a replacement endpoint
- **THEN** the existing meter-value editor opens with its original read-only date, and saving preserves its installation and boundary role

#### Scenario: Request deletion of a boundary
- **WHEN** the user long-presses a structural final or initial reading
- **THEN** the app explains that the reading defines a meter replacement and cannot be deleted individually, without deleting or joining installations
