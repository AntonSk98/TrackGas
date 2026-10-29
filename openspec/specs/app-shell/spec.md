# app-shell Specification

## Purpose

Provide a minimal offline Android entry point that establishes a working native application and starter interface before product features are added.

## Requirements

### Requirement: Offline application launch
The installed app SHALL launch to the gas consumption dashboard without connectivity, an account, a server, or runtime permission prompts.

#### Scenario: Launch in airplane mode
- **WHEN** the user opens the installed app with connectivity disabled
- **THEN** the gas consumption dashboard appears without a crash, network error, sign-in, or permission prompt

### Requirement: Minimalist light interface
The main screen SHALL display the app name "TrackGas" in a readable minimalist light interface with labelled Dashboard and History tabs, Dashboard selected initially, working period controls on Dashboard, an accessible top-right add-reading icon on either tab, and reading management in History when notes exist. The interface SHALL respect system insets and use labelled controls.

#### Scenario: View the starter screen
- **WHEN** the user launches the app
- **THEN** "TrackGas", Dashboard and History tabs, weekly-by-default dashboard controls, and the add-reading icon are visible on a light screen, without reading history on Dashboard, sample analytics, or unfinished price or weather controls

### Requirement: Focused tracker content
Dashboard SHALL omit reading history and individual Unknown consumption rows. The interface SHALL NOT expose unfinished feature actions.

#### Scenario: Keep Dashboard focused
- **WHEN** the user views Dashboard with recorded readings and partially covered consumption
- **THEN** reading history remains in History and individual Unknown consumption rows are not displayed

#### Scenario: Expose only implemented actions
- **WHEN** the user views either tracker tab or opens its options menu
- **THEN** no unfinished feature actions are offered

### Requirement: Cohesive light visual hierarchy
The interface SHALL use warm off-white backgrounds, white cards, teal accents, and consistent typography, spacing, and controls across Dashboard, History, Add/Edit, and delete confirmation. Dashboard SHALL emphasize the selected period and consumption total. History SHALL emphasize reading dates and values with identifiable Edit/Delete actions. Local-storage disclosure SHALL remain readable on both pages without dominating the content.

#### Scenario: Browse a populated dashboard
- **WHEN** the user views a period with consumption
- **THEN** Weekly/Monthly/Yearly selection, a prominent Consumption total, a chart, date-range navigation, and supporting values appear in that order beneath the shared toolbar and tabs, with local-storage disclosure visible in the page and date-range navigation directly beneath the chart inside its card

#### Scenario: Manage a reading with consistent styling
- **WHEN** the user opens History, edits a reading, or requests deletion
- **THEN** the list and dialogs share the same visual language, Edit/Delete remain identifiable, validation errors remain inline, and deletion still requires explicit confirmation

#### Scenario: Understand temporary storage
- **WHEN** the user opens either Dashboard or History
- **THEN** the page includes readable text explaining that readings are stored on the device and are not backed up to the cloud

### Requirement: Responsive readable controls
The interface SHALL keep tab, period, reading-management, and dialog actions reachable on compact screens, in landscape, with enlarged system fonts, and with the keyboard open. Interactive controls SHALL provide at least 48 dp touch targets and meaningful labels. Text SHALL meet 4.5:1 contrast for normal text and 3:1 for large text against its surface. Color SHALL NOT be the only indication of selection or error.

#### Scenario: Compact display and enlarged text
- **WHEN** the user increases system font size to 200% on a 360 dp-wide display
- **THEN** labels and values wrap or reflow without losing information, selected controls remain identifiable, and all page and dialog actions remain reachable by scrolling where needed

#### Scenario: Landscape entry with keyboard
- **WHEN** the user edits a reading in landscape with the decimal keyboard open
- **THEN** the date, meter input, inline errors, Save, and Cancel remain reachable without being obscured by system bars or the keyboard

#### Scenario: Identify icon actions
- **WHEN** the user accesses Add or period-navigation icons through accessibility services
- **THEN** each action exposes a meaningful localized label and an adequate touch target

#### Scenario: Manage history through entry gestures
- **WHEN** the user views a recorded reading in History
- **THEN** no Edit/Delete icons appear, tapping the entry opens its editor, and long-pressing it opens deletion confirmation without also opening Edit

#### Scenario: Accessible history actions
- **WHEN** accessibility services focus a History entry
- **THEN** its date and value are readable and localized, reading-specific click-to-edit and long-click-to-delete action labels are available

### Requirement: Meter-only History editing
The History editor SHALL allow changes only to the meter value. It SHALL show the original localized date in a read-only field labelled "Date", without "Reading date", "Choose date", or a date picker. Save SHALL retain the reading's original date and identity. Add SHALL retain its existing date-selection behavior.

#### Scenario: Consistent Add and Edit date presentation
- **WHEN** the user opens Add or Edit
- **THEN** both forms show the same outlined Date field and meter field, without a separate Reading date label or Choose date text button; tapping the Add date opens its picker with a meaningful accessible button label, while the Edit date remains immutable

#### Scenario: Edit a meter value
- **WHEN** the user taps a History entry, changes its valid meter value, and saves
- **THEN** the value updates while its date, identity, History selection, and dashboard period remain unchanged

#### Scenario: Inspect the read-only date
- **WHEN** the user taps the Date field in the History editor
- **THEN** no date picker opens and the date cannot be changed

#### Scenario: Request deletion
- **WHEN** the user long-presses a History entry
- **THEN** the delete prompt identifies that reading, Cancel leaves it unchanged, and Delete removes it only after confirmation

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
