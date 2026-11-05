# Spec Delta

## Purpose

Let the user state where the meter is located so outdoor temperatures can be looked up for that place instead of the device's current position.

## ADDED Requirements

### Requirement: Select and save the meter location
The tracker options menu SHALL offer "Meter location". Its dialog SHALL accept place names without digits, show no more than three matching suggestions, and allow saving only after the user selects a suggestion. When a location is already saved, its name SHALL appear in the input. The saved location SHALL persist on-device across restarts and SHALL NOT depend on the device's position.

#### Scenario: Select a suggestion
- **WHEN** the user enters "Berlin" while online and selects a suggestion
- **THEN** the selected place is saved, the dialog closes, and its name appears in the input when reopened

#### Scenario: Unresolvable location
- **WHEN** the entry matches no place
- **THEN** an error is shown in the dialog, the previously saved location is unchanged, and the user can edit and retry

#### Scenario: Suggestions are limited
- **WHEN** more than three places match the query
- **THEN** no more than three suggestions are displayed

#### Scenario: Typed text is not selected
- **WHEN** the user types a place name but does not select a suggestion
- **THEN** saving is unavailable

### Requirement: Location is requested when adding a reading
When a reading is added, no meter location is saved, and the device is online, the app SHALL save the reading and then open the location dialog automatically with a "Later" action that closes it without saving. Outside tap and back SHALL behave like "Later", so a failing service never blocks the app. The dialog SHALL reopen on every later add while no location is saved. While offline the dialog SHALL NOT be shown, and it SHALL be requested again on the next add without a saved location.

#### Scenario: First reading without a location
- **WHEN** the user saves a new reading and no location is saved
- **THEN** the reading is stored and the location dialog opens and stays until a location is saved or the user chooses Later

#### Scenario: Offline add without a location
- **WHEN** the user saves a new reading, no location is saved, and the device is offline
- **THEN** the reading is stored, no dialog is shown, and the next add without a location while online opens the location dialog

#### Scenario: Close with Later
- **WHEN** the dialog is shown and the user taps Later, taps outside it, or presses back
- **THEN** the dialog closes, no location is saved, tracking continues, and the dialog reopens on the next add

#### Scenario: Service unavailable
- **WHEN** the lookup fails because the service is unreachable
- **THEN** a retryable error is shown and the user can still close the dialog with Later

#### Scenario: Location already saved
- **WHEN** the user saves a new reading and a location is saved
- **THEN** no location dialog opens

#### Scenario: Existing readings, no location
- **WHEN** a user with existing readings and no saved location adds a reading
- **THEN** the location dialog opens, and after saving a location temperatures are fetched for the whole reading range

### Requirement: Location change refreshes temperatures
When the saved location changes, the app SHALL remove previously stored temperatures and request temperatures for the full date range covered by readings at the new location. If offline, it SHALL perform the request when connectivity becomes available.

#### Scenario: Change location
- **WHEN** the user saves a different location
- **THEN** temperatures from the previous location are removed and the full covered date range is fetched for the new location

#### Scenario: Change location while offline
- **WHEN** the user changes the saved location while offline
- **THEN** old temperatures are removed and the full date range is fetched when connectivity returns

### Requirement: Localized location dialog
The location dialog and its errors SHALL be available in English and German and follow the in-app language.

#### Scenario: German dialog
- **WHEN** the app language is German and the user opens Meter location
- **THEN** title, field label, actions, and errors appear in German
