## MODIFIED Requirements

### Requirement: Shared tracker options menu
The shared toolbar SHALL show a gear-shaped options icon immediately to the left of the existing top-right Add icon on Dashboard and History. It SHALL open a dismissible menu containing only the implemented options (Export CSV, Import CSV, Replace meter, Purge data, and Language), with no account or sign-in behavior. Purge data SHALL have a delete icon and Language SHALL have a language icon. Opening or dismissing the menu SHALL preserve readings, tab, and period.

#### Scenario: Open options from either tab
- **WHEN** the user taps the gear icon on Dashboard or History
- **THEN** the menu opens beside the toolbar action, offers Replace meter, Purge data, and Language among its implemented options, and leaves Add in its existing rightmost position

#### Scenario: Dismiss without changes
- **WHEN** the user dismisses the menu with Back or an outside tap
- **THEN** the menu closes without opening a form or modifying data or navigation

#### Scenario: Accessible compact toolbar
- **WHEN** accessibility services focus the options icon or the menu is used on a compact screen with enlarged fonts
- **THEN** the icon exposes a meaningful localized label, its touch target is at least 48 dp, and menu text and actions remain readable and reachable
