# Spec Delta

## Purpose

Allow users to take their recorded gas meter readings out of the app in a portable format.

## ADDED Requirements

### Requirement: Save readings as CSV
The app SHALL offer an accessible export action as the first Tracker options item on both tabs when tracking data is ready. The action SHALL include a visible export icon. It SHALL let the user choose a CSV destination and export all readings from one committed tracker snapshot, independent of the selected dashboard period. Export SHALL work offline and SHALL NOT modify tracking data or navigation.

#### Scenario: Find export in Tracker options
- **WHEN** the user opens Tracker options on Dashboard or History
- **THEN** Export CSV is the first menu item and displays an export icon

#### Scenario: Export from either tab
- **WHEN** the user exports from Dashboard or History with a past period selected
- **THEN** the file contains all readings from every installation and the selected tab and period remain unchanged

#### Scenario: Data is unavailable
- **WHEN** tracking data is still loading or has failed to load
- **THEN** export is unavailable rather than presenting an incomplete or stale tracker as current

#### Scenario: Empty tracker
- **WHEN** the user exports a successfully loaded tracker with no readings
- **THEN** the saved file contains the CSV header and no data rows

### Requirement: Portable reading CSV format
The file SHALL be UTF-8, semicolon-delimited CSV with the header `meter_id;date;meter_value`, in that order. Each reading SHALL have one row with its stable installation ID, ISO 8601 calendar date, and its exact cumulative cubic-metre value using a dot decimal separator and no grouping. Fields SHALL use CSV quoting and doubled quotes when needed.

#### Scenario: Locale-independent precision
- **WHEN** a reading dated 2026-10-28 in installation `11111111-1111-4111-8111-111111111111` has the value 123.450 and the device uses comma decimal formatting
- **THEN** its CSV row is `11111111-1111-4111-8111-111111111111;2026-10-28;123.450` and does not use localized dates or decimal separators

#### Scenario: Special characters in a field
- **WHEN** an exported field contains a semicolon, double quote, or line break
- **THEN** the field is quoted and any double quote is doubled so that a CSV reader can recover the original field

### Requirement: Preserve meter boundaries in exported readings
The export SHALL include every recorded reading, including both replacement endpoints, and SHALL use the same installation ID for readings of the same meter and distinct IDs for different installations. Rows SHALL be ordered by date ascending, then installation start order for same-date readings. It SHALL NOT include calculated consumption or fabricated readings.

#### Scenario: Replacement on one date
- **WHEN** an old meter ends at 1105 and a new meter begins at 0 on 2026-10-28
- **THEN** the file contains two rows for that date with their respective distinct meter IDs, old meter first

### Requirement: Persistent first installation identity
On a fresh install, the first installation SHALL have a persistent UUID. Purge SHALL create a fresh first-installation UUID.

#### Scenario: Reopen tracker
- **WHEN** a freshly installed tracker is reopened
- **THEN** its first installation retains the same UUID

#### Scenario: Purge tracker
- **WHEN** the user successfully purges tracking data
- **THEN** the new empty tracker has a fresh first-installation UUID

### Requirement: Export completion and failure
The app SHALL report success only after the complete file is written and closed. Cancelling destination selection SHALL leave tracking data unchanged and SHALL NOT report success. A write failure SHALL show a privacy-safe error and permit another export attempt without changing tracking data.

Success SHALL appear in a dismissible window with a success icon and no confirmation button.

#### Scenario: Destination or write fails
- **WHEN** the selected destination cannot be opened, written, or closed
- **THEN** the user sees an export failure instead of success and can choose a destination and try again

#### Scenario: Picker cancellation
- **WHEN** the user cancels the destination picker
- **THEN** no export success or error is reported and the tracker remains unchanged

#### Scenario: Rotation during export
- **WHEN** the device rotates while destination selection or writing is in progress
- **THEN** the operation is not duplicated and its completion or failure is presented once

### Requirement: Explain CSV scope
The app documentation SHALL describe the columns, units, delimiter, decimal and date formats, meter ID meaning, and the inclusion of replacement endpoints. It SHALL explain that the file is a readings export rather than a complete restorable app backup and that import is not provided by this feature.

#### Scenario: User consults export documentation
- **WHEN** the user reads the export documentation
- **THEN** they can interpret values and meter IDs and understand that future import behavior is not yet defined
