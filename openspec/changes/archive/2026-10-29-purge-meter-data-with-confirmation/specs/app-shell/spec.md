# Spec Delta

## MODIFIED Requirements

### Requirement: Shared tracker options menu
The shared toolbar SHALL show a gear-shaped options icon immediately to the left of the existing top-right Add icon on Dashboard and History. It SHALL open a dismissible menu containing only the implemented "Replace meter" and "Purge data" options, with no account or sign-in behavior. Purge data SHALL have a delete icon. Opening or dismissing the menu SHALL preserve readings, tab, and period.

#### Scenario: Open options from either tab
- **WHEN** the user taps the gear icon on Dashboard or History
- **THEN** the menu opens beside the toolbar action, offers Replace meter and Purge data, and leaves Add in its existing rightmost position

#### Scenario: Dismiss without changes
- **WHEN** the user dismisses the menu with Back or an outside tap
- **THEN** the menu closes without opening a form or modifying data or navigation

#### Scenario: Accessible compact toolbar
- **WHEN** accessibility services focus the options icon or the menu is used on a compact screen with enlarged fonts
- **THEN** the icon exposes a meaningful localized label, its touch target is at least 48 dp, and menu text and actions remain readable and reachable

## ADDED Requirements

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
