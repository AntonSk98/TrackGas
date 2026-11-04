# reading-data-exchange Specification

## Purpose

Allow users to take their recorded gas meter readings out of the app in a portable format.

## Requirements

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
The app documentation SHALL describe the columns, units, delimiter, decimal and date formats, meter ID meaning, and the inclusion of replacement endpoints. It SHALL explain that CSV contains readings, not a complete restorable backup; import accepts only known UUIDs, recreates included histories under the same UUIDs, removes replacement relationships touching imported meters, preserves omitted readings, and requires confirmation.

#### Scenario: User consults export documentation
- **WHEN** the user reads the export documentation
- **THEN** they can interpret the CSV and understand the known-meter restriction, replacement-link removal, omitted-meter behavior, and confirmation requirement

### Requirement: Import readings for known meters
The app SHALL accept CSV rows only for installation UUIDs known before import. For each included UUID, it SHALL recreate the installation and readings under the same UUID and remove saved replacement relationships that reference it. Readings for omitted UUIDs SHALL remain unchanged. The meter with the latest reading date SHALL be current; installation start date SHALL break ties.

#### Scenario: Recreate included meter histories
- **WHEN** the user confirms a valid CSV containing readings for known meter IDs
- **THEN** the included installations and readings are recreated under the same UUIDs, and omitted meter readings remain unchanged

#### Scenario: Remove a saved replacement relationship
- **WHEN** an included installation participates in a saved meter replacement
- **THEN** the replacement relationship is removed without requiring or inventing endpoint readings; readings for an omitted installation remain unchanged

#### Scenario: Select the current meter by latest reading
- **WHEN** independent meter histories have different latest reading dates
- **THEN** the installation with the latest reading date is current

#### Scenario: Break a latest-date tie
- **WHEN** multiple installations have a reading on the latest date
- **THEN** the installation with the latest start date is current

#### Scenario: Reject an unknown meter ID
- **WHEN** any CSV row references a UUID not known before import
- **THEN** the whole import is rejected, no data changes, and the user sees a short error with row context

### Requirement: Preview and apply CSV imports atomically
The app SHALL validate the complete import and show a concise warning that meter data will be replaced and related replacement links cleared. The preview SHALL NOT show meter UUIDs or per-meter counts. Cancellation or validation failure SHALL leave data unchanged; confirmation SHALL commit the import atomically.

#### Scenario: Cancel a valid import
- **WHEN** the user cancels after reviewing the preview
- **THEN** no installation, reading, or replacement data changes

#### Scenario: Invalid data prevents partial import
- **WHEN** any CSV row, meter history, or tracker integrity check fails
- **THEN** the whole import is rejected and no tracker data changes

#### Scenario: Commit a confirmed import
- **WHEN** the user confirms a valid preview and persistence succeeds
- **THEN** included histories are recreated, affected replacement links are removed, omitted readings remain unchanged, and the tracker publishes one committed snapshot

### Requirement: Resolve duplicate CSV readings deterministically
For duplicate rows with the same meter UUID and date, the app SHALL keep the last row in file order. Rows for different UUIDs on the same date SHALL remain distinct.

#### Scenario: Duplicate date for one meter
- **WHEN** the CSV contains multiple rows for one UUID and date with different values
- **THEN** the last value in file order is imported

#### Scenario: Same date across meters
- **WHEN** known meters have readings on the same date
- **THEN** each meter's reading remains associated with its own UUID

### Requirement: Report CSV import errors safely
The app SHALL map stable internal error codes to short localized messages, include row and field context when available, and SHALL NOT display internal code names or raw exception messages.

#### Scenario: Invalid CSV structure
- **WHEN** the header or a row has an invalid structure
- **THEN** a short localized message identifies the error and row when available; the internal code is CSV_HEADER_INVALID or CSV_ROW_INVALID

#### Scenario: Invalid meter, date, or value
- **WHEN** a row contains a malformed or unknown UUID, invalid date/value, or negative value
- **THEN** a short localized message includes row and field context; the internal code is CSV_METER_ID_INVALID, CSV_METER_UNKNOWN, CSV_DATE_INVALID, CSV_VALUE_INVALID, or CSV_VALUE_NEGATIVE

#### Scenario: Invalid meter history
- **WHEN** values decrease within one UUID or consumption intervals overlap across independent meters
- **THEN** import fails without changing data and uses internal code CSV_METER_HISTORY_INVALID, with row/field context when available

#### Scenario: File or import failure
- **WHEN** file access or an unexpected import operation fails
- **THEN** the user sees a short safe error mapped from CSV_FILE_READ_FAILED or CSV_IMPORT_FAILED, without exception text

#### Scenario: Imported history predates a former replacement date
- **WHEN** imported per-meter histories are valid but do not contain the tracker's former replacement date or endpoints
- **THEN** import succeeds without requiring or inventing replacement-boundary readings

### Requirement: Distinct CSV action icons
The CSV menu SHALL use visually distinct directional icons: Export CSV depicts data moving up and out of a tray, and Import CSV depicts data moving down into a tray.

#### Scenario: Distinguish CSV actions
- **WHEN** the user opens Tracker options on Dashboard or History
- **THEN** Export CSV shows an upward out-of-tray icon and Import CSV shows a downward into-tray icon
