# app-shell Specification

## Purpose

Provide a minimal offline Android entry point that establishes a working native application and starter interface before product features are added.

## Requirements

### Requirement: Offline application launch
The installed app SHALL launch to the gas consumption dashboard without connectivity, an account, or a server. It SHALL NOT show a location explanation or permission prompt at launch. A location-dependent feature MAY request approximate location only after the user invokes that feature, and declining SHALL NOT block existing tracking features.

#### Scenario: Launch in airplane mode
- **WHEN** the user opens the installed app with connectivity disabled
- **THEN** the gas consumption dashboard appears without a crash, network error, sign-in, or location prompt

#### Scenario: Launch without invoking a location-dependent feature
- **WHEN** the user opens the app without invoking a location-dependent feature
- **THEN** the dashboard appears without any location permission prompt regardless of the current permission state

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

### Requirement: Compact independent meter entries
History SHALL present a reading's date and value in two rows, with an optional third row for its note when space permits. When multiple meter histories exist, the meter identifier SHALL be compactly displayed with the value rather than in a separate repeated role row. When width or enlarged text prevents this arrangement, text and the meter identifier SHALL reflow onto additional lines without losing information or changing reading actions.

#### Scenario: Read a history entry without a note
- **WHEN** the user views a reading without a note at a width and font size that accommodate its date and value
- **THEN** its date and value fit in two rows and no separate meter-role row is shown

#### Scenario: Read a history entry with a note
- **WHEN** the user views a reading with a note at a width and font size that accommodate its content
- **THEN** its date, value, and note appear in three rows

#### Scenario: Identify an independent meter
- **WHEN** History includes readings from multiple meter installations
- **THEN** each value remains associated with a compact meter identifier

#### Scenario: Reflow a crowded independent meter entry
- **WHEN** a reading's localized timestamp, full-precision value, unit, meter identifier, or note cannot fit in the compact arrangement
- **THEN** its content wraps onto additional lines without clipping, overlap, or ellipsis hiding information, and tap-to-edit and long-press-to-delete remain available

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

### Requirement: Explicit tracking-data purge confirmation
Selecting Purge data SHALL close the options menu and open a single modal titled "Purge data". It SHALL concisely warn that all readings, meter history, and consumption will be removed and that this cannot be undone. Only an explicit "Purge all data" action SHALL authorize deletion; Cancel, Back, and outside taps SHALL dismiss a non-pending confirmation without writes.

#### Scenario: Review before purging
- **WHEN** the user selects Purge data on either tab
- **THEN** the menu closes and the warning, Purge all data, and Cancel are visible, with all saved data still intact

#### Scenario: Cancel without changes
- **WHEN** the user chooses Cancel, presses Back, or taps outside the non-pending confirmation
- **THEN** the dialog closes without changing tracking data, selected tab, or selected period

#### Scenario: Accessible destructive action
- **WHEN** the confirmation is viewed on a compact display, in landscape, with 200% fonts, or through accessibility services
- **THEN** its localized warning and labelled actions remain readable and reachable, touch targets are at least 48 dp, and destructive intent is communicated by text as well as color

### Requirement: Pending and failed purge presentation
Pending purge SHALL show progress, disable confirmation and cancellation, and block Back/outside dismissal and other mutations. A storage failure SHALL keep the confirmation open with a generic, accessible error and retry through explicit confirmation. Loading or load failure SHALL block purge submission and expose existing load-retry behavior without automatically submitting a purge.

#### Scenario: Prevent repeat submission
- **WHEN** purge is pending and the user confirms again, tries dismissal, or requests Add, Edit, Delete, or replacement
- **THEN** the same pending confirmation remains visible and no additional mutation is submitted

#### Scenario: Retry a failed purge
- **WHEN** a purge fails before commit
- **THEN** the warning and Cancel remain available, the error is announced without exposing stored data or internal exception text, and Purge all data can explicitly retry once loading is ready

#### Scenario: Loading is not an empty tracker
- **WHEN** tracking data is loading or has a load error
- **THEN** the options action cannot start a purge, an existing purge confirmation cannot submit, and load retry does not delete data

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

### Requirement: Application branding icon
The app SHALL use a recognizable gas-tracking brand mark as its Android launcher icon and display that mark beside "TrackGas" in the shared in-app toolbar.

#### Scenario: Find the installed app
- **WHEN** the user views TrackGas in the Android launcher
- **THEN** the app is represented by its gas-tracking brand icon

#### Scenario: View the tracker toolbar
- **WHEN** the user views Dashboard or History
- **THEN** the brand mark appears beside the "TrackGas" title

### Requirement: Phone and tablet window coverage
The app SHALL keep Dashboard, History, toolbar, tabs, and options usable in portrait and landscape phone and tablet windows from 320 dp wide and 320 dp high. Pages SHALL remain centered with bounded readable content on wide windows and SHALL scroll vertically when needed, without page-level horizontal scrolling. Layout SHALL respond to window resizing, not only physical orientation.

#### Scenario: Representative phone and tablet sizes
- **WHEN** the app is used at window sizes 320 x 568, 360 x 800, 600 x 960, and 800 x 1280 dp and at each size with width and height exchanged
- **THEN** both tabs, Add, options, period selection and navigation, reading management, and all page content remain reachable without overlap or lost information

#### Scenario: Resize a tablet window
- **WHEN** the user resizes a tablet window between 960 x 640, 480 x 640, and 320 x 480 dp
- **THEN** the current page reflows to the available space without requiring relaunch or exposing off-window controls

#### Scenario: Localization and enlarged fonts
- **WHEN** each representative window and resized window is used in English or German at 100% or 200% system font size
- **THEN** labels, dates, numeric values, and units remain readable and actions remain reachable by vertical scrolling or reflow, with at least 48 dp touch targets

### Requirement: Constrained dialog and picker access
Every app-owned dialog and date/time picker SHALL keep its content, feedback, and enabled actions reachable within the available safe area, including with the keyboard open. Content SHALL wrap and scroll when necessary without obscuring actions. Location suggestions SHALL remain individually reachable and selectable. These rules SHALL preserve existing validation, confirmation, dismissal, and pending-operation protections.

#### Scenario: Search locations with little height
- **WHEN** the location dialog shows suggestions or lookup errors in a landscape phone window with the keyboard open and 200% fonts
- **THEN** the query, all suggestions, feedback, Save, and Cancel or Later remain reachable, and each suggestion has at least a 48 dp touch target

#### Scenario: Read and act on every modal
- **WHEN** Add, Edit, date/time selection, Language, delete, replacement, purge, import preview/progress/result/error, or export result/error is shown in a representative constrained window
- **THEN** all applicable content and enabled actions remain reachable without clipping or system-bar obstruction, and unavailable or pending actions remain disabled as before

#### Scenario: Edit with the keyboard visible
- **WHEN** a user edits a reading in portrait or landscape with the keyboard open and inline validation errors visible
- **THEN** the date, time, timezone, meter value, note, errors, Save, and Cancel remain reachable without dismissing the form

### Requirement: Responsive dashboard chart readability
Dashboard SHALL preserve readable consumption and temperature charts, metric values, period controls, and covered rows as available space changes. Chart labels and inspection feedback SHALL remain readable without overlap that hides information. All covered buckets SHALL remain represented and their values available in the existing chart inspection and numeric presentation; responsiveness SHALL NOT change calculations or coverage.

#### Scenario: Read charts on a narrow phone
- **WHEN** a populated weekly, monthly, or yearly Dashboard with temperatures is shown on a 320 dp-wide window at 200% fonts
- **THEN** chart labels do not collide, totals and units remain readable, long-press inspection remains usable, and period navigation and covered numeric rows remain reachable by vertical scrolling

#### Scenario: Read charts in landscape or on a tablet
- **WHEN** a populated Dashboard is rotated or its window is widened or shortened
- **THEN** charts fit the bounded content width, retain readable plots and labels, and do not prevent reaching content below them

### Requirement: Responsive transitions preserve tracker work
Rotation and window resizing within the active process SHALL preserve selected tab and period, per-tab reading position, unsaved Add/Edit field values, and active tracker dialogs and pending operations. Layout transitions SHALL NOT save, discard, duplicate, or authorize a mutation. Existing fresh-process defaults SHALL remain unchanged.

#### Scenario: Rotate while editing
- **WHEN** a user enters an unsaved reading value, time, or note and rotates or resizes the window
- **THEN** the same editor retains its draft and validation feedback without saving or discarding it

#### Scenario: Transition during a pending mutation
- **WHEN** the window changes size while save, delete, replacement, purge, or CSV import is pending
- **THEN** the same operation continues once, its pending protections remain effective, and its matching result is presented without a duplicate submission

#### Scenario: Keep navigation and list position
- **WHEN** the user scrolls each tab, selects a non-current Dashboard period, and rotates or resizes while viewing History
- **THEN** History stays selected, each tab retains its reading position as closely as reflow permits, and returning to Dashboard shows the same selected period
