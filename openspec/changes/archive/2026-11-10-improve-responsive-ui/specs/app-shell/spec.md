# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
