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
The main screen SHALL display the app name "TrackGas" in a readable minimalist light interface with labelled Dashboard and History tabs, Dashboard selected initially, working period controls on Dashboard, an accessible top-right add-reading icon on either tab, and reading management in History when notes exist. Dashboard SHALL omit reading history and individual Unknown consumption rows. The interface SHALL respect system insets and use labelled controls without exposing unfinished feature actions.

#### Scenario: View the starter screen
- **WHEN** the user launches the app
- **THEN** "TrackGas", Dashboard and History tabs, weekly-by-default dashboard controls, and the add-reading icon are visible on a light screen, without reading history on Dashboard, sample analytics, or unfinished price or weather controls

### Requirement: Cohesive light visual hierarchy
The interface SHALL use warm off-white backgrounds, white cards, teal accents, and consistent typography, spacing, and controls across Dashboard, History, Add/Edit, and delete confirmation. Dashboard SHALL emphasize the selected period and consumption total. History SHALL emphasize reading dates and values with identifiable Edit/Delete actions. Temporary-reading disclosure SHALL remain readable on both pages without dominating the content.

#### Scenario: Browse a populated dashboard
- **WHEN** the user views a period with consumption
- **THEN** Weekly/Monthly/Yearly selection, a prominent Consumption total, a chart, date-range navigation, and supporting values appear in that order beneath the shared toolbar and tabs, with temporary-reading disclosure visible in the page and date-range navigation directly beneath the chart inside its card

#### Scenario: Manage a reading with consistent styling
- **WHEN** the user opens History, edits a reading, or requests deletion
- **THEN** the list and dialogs share the same visual language, Edit/Delete remain identifiable, validation errors remain inline, and deletion still requires explicit confirmation

#### Scenario: Understand temporary storage
- **WHEN** the user opens either Dashboard or History
- **THEN** the page includes readable text explaining that readings are temporary and lost when the app process ends

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
The Add/Edit dialog SHALL dismiss and discard its unsaved draft when the user taps outside its visible form surface. Interactions within the form SHALL NOT dismiss it. Back and Cancel SHALL remain available; dismissing the nested date picker SHALL return to the unsaved form rather than save or discard it.

#### Scenario: Tap outside Add
- **WHEN** the user enters an unsaved Add draft and taps the surrounding backdrop
- **THEN** the form closes without creating a reading or changing the selected tab or period

#### Scenario: Interact within the form
- **WHEN** the user taps the form surface, input, or date action
- **THEN** the form remains open unless an explicit dismissal or successful Save occurs

#### Scenario: Dismiss only the date picker
- **WHEN** the user dismisses the date picker without confirming
- **THEN** the parent form remains open with its previous draft intact
