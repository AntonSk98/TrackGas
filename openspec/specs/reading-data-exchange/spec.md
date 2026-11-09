# reading-data-exchange Specification

## Purpose

Allow users to take their recorded gas meter readings out of the app in a portable format.

## Requirements


### Requirement: Save readings as CSV
The app SHALL offer an accessible export action named “Export readings” as the first Tracker options item on both tabs when tracking data is ready. The action SHALL include a visible export icon. It SHALL let the user choose a CSV destination and export all readings and notes from one committed tracker snapshot, independent of the selected dashboard period. Export SHALL work offline and SHALL NOT modify tracking data or navigation.

#### Scenario: Find export in Tracker options
- **WHEN** the user opens Tracker options on Dashboard or History
- **THEN** Export readings is the first menu item and displays an export icon

#### Scenario: Export from either tab
- **WHEN** the user exports from Dashboard or History with a past period selected
- **THEN** the file contains all readings and notes from every installation and the selected tab and period remain unchanged

#### Scenario: Data is unavailable
- **WHEN** tracking data is still loading or has failed to load
- **THEN** export is unavailable rather than presenting an incomplete or stale tracker as current

#### Scenario: Empty tracker
- **WHEN** the user exports a successfully loaded tracker with no readings
- **THEN** the saved file contains the CSV header and no data rows

### Requirement: Portable reading CSV format
The file SHALL be UTF-8 semicolon-delimited CSV with header `meter_id;timestamp;timezone;meter_value;note`. Rows SHALL contain the stable installation UUID, ISO 8601 offset timestamp at minute precision, recording timezone ID, exact dot-decimal cumulative cubic-metre value without grouping, and optional note. Import SHALL accept only this header and valid timestamp/timezone combinations. Fields SHALL use CSV quoting and doubled quotes when needed.

#### Scenario: Locale-independent precision
- **WHEN** a reading taken at 08:15 on 2026-11-06 in Europe/Berlin has installation `11111111-1111-4111-8111-111111111111`, value 123.450, and no note
- **THEN** its row is `11111111-1111-4111-8111-111111111111;2026-11-06T08:15+01:00;Europe/Berlin;123.450;` without localized date or decimal separators

#### Scenario: Export a note
- **WHEN** a reading has a saved note
- **THEN** its note appears in the fifth field of the exported row

#### Scenario: Reject the previous three-column format
- **WHEN** the user imports a file with the previous `meter_id;date;meter_value` header
- **THEN** the import is rejected with the `CSV_HEADER_INVALID` error

#### Scenario: Import a four-column file
- **WHEN** the user imports the previous `meter_id;date;meter_value;note` format
- **THEN** the import is rejected with `CSV_HEADER_INVALID` instead of assigning an invented observation time

#### Scenario: Import the timestamp format
- **WHEN** the user imports a valid timestamp-format file
- **THEN** each timestamp, recording timezone, exact value, and non-empty note is retained with its corresponding reading

#### Scenario: Special characters in a field
- **WHEN** an exported field contains a semicolon, double quote, or line break
- **THEN** the field is quoted and any double quote is doubled so that a CSV reader can recover the original field

#### Scenario: Preserve the instant across device timezones
- **WHEN** a file is exported in one device timezone and imported while the device uses another
- **THEN** observation instants and recorded local dates/times remain unchanged

#### Scenario: Timestamp disagrees with timezone
- **WHEN** an imported timestamp's offset is invalid for its local date/time in the supplied timezone, or its local time is nonexistent
- **THEN** the whole import is rejected with localized row/field context

### Requirement: Persistent first installation identity
On a fresh install, the first installation SHALL have a persistent UUID. Purge SHALL create a fresh first-installation UUID.

#### Scenario: Reopen tracker
- **WHEN** a freshly installed tracker is reopened
- **THEN** its first installation retains the same UUID

#### Scenario: Purge tracker
- **WHEN** the user successfully purges tracking data
- **THEN** the new empty tracker has a fresh first-installation UUID

### Requirement: Export completion and failure
The app SHALL report success only after the complete file is written and closed. Cancelling destination selection SHALL leave tracking data unchanged and SHALL NOT report success. A write failure SHALL show a privacy-safe error and permit another export attempt without changing tracking data. Success SHALL appear in a dismissible window with a success icon and the localized text “Saved” / “Gespeichert,” without a confirmation button.

#### Scenario: Destination or write fails
- **WHEN** the selected destination cannot be opened, written, or closed
- **THEN** the user sees an export failure instead of success and can choose a destination and try again

#### Scenario: Picker cancellation
- **WHEN** the user cancels the destination picker
- **THEN** no export success or error is reported and the tracker remains unchanged

#### Scenario: Successful export
- **WHEN** the complete file has been written and closed
- **THEN** a dismissible success window displays the success icon and “Saved” / “Gespeichert”

#### Scenario: Rotation during export
- **WHEN** the device rotates while destination selection or writing is in progress
- **THEN** the operation is not duplicated and its completion or failure is presented once

### Requirement: Explain CSV scope
Documentation SHALL describe the columns, units, delimiter, decimal and timestamp formats, recording timezone, meter ID, and optional note. It SHALL state that CSV is not a complete backup, contains no cached temperatures, accepts only known UUIDs, recreates included histories, preserves omitted histories, and requires confirmation. Old date-only CSV formats SHALL be documented as unsupported.

#### Scenario: User consults export documentation
- **WHEN** the user reads the export documentation
- **THEN** they can interpret the timestamp and timezone, understand optional notes and known-meter restrictions, and know that omitted histories are preserved and confirmation is required

#### Scenario: Temperature data after import
- **WHEN** the user reads the import documentation
- **THEN** it explains that affected interval means are fetched again in the background rather than restored from CSV

### Requirement: Import readings for known meters
The app SHALL accept CSV rows only for installation UUIDs known before import. For each included UUID, it SHALL replace that installation's readings and notes while preserving its identity. Readings for omitted UUIDs SHALL remain unchanged. The installation with the latest reading date SHALL be current; installation start date SHALL break ties, except that a newly created empty current installation SHALL remain current when another installation's readings are imported.

#### Scenario: Recreate included meter histories
- **WHEN** the user confirms a valid CSV containing readings for known meter IDs
- **THEN** the included installations and readings are recreated under the same UUIDs, and omitted meter histories remain unchanged

#### Scenario: Remove a saved replacement relationship
- **WHEN** an included installation has a legacy saved meter-replacement relationship
- **THEN** the obsolete relationship is removed without requiring or inventing endpoint readings, and readings for omitted installations remain unchanged

#### Scenario: Preserve the empty current installation
- **WHEN** a replacement has started a new empty current installation and the user imports readings for a different known installation
- **THEN** the imported history is replaced and the empty current installation remains available and current

#### Scenario: Select the current meter by latest reading
- **WHEN** no newly created empty installation is current and independent meter histories have different latest reading dates
- **THEN** the installation with the latest reading date is current

#### Scenario: Break a latest-date tie
- **WHEN** multiple meters have a reading on the latest date
- **THEN** the installation with the latest start date is current

#### Scenario: Reject an unknown meter ID
- **WHEN** any CSV row references a UUID not known before import
- **THEN** the whole import is rejected, no data changes, and the user sees a short error with row context

### Requirement: Preview and apply CSV imports atomically
The app SHALL validate the complete import and show a concise warning that included meter histories will be replaced. The preview SHALL NOT show meter UUIDs or per-meter counts. Cancellation or validation failure SHALL leave data unchanged; confirmation SHALL commit the import atomically.

#### Scenario: Cancel a valid import
- **WHEN** the user cancels after reviewing the preview
- **THEN** no installation, reading, or note changes

#### Scenario: Invalid data prevents partial import
- **WHEN** any CSV row, meter history, or tracker integrity check fails
- **THEN** the whole import is rejected and no tracker data changes

#### Scenario: Commit a confirmed import
- **WHEN** the user confirms a valid preview and persistence succeeds
- **THEN** included histories and notes are replaced, omitted histories remain unchanged, and the tracker publishes one committed snapshot

### Requirement: Resolve duplicate CSV readings deterministically
For duplicate rows with the same meter UUID and date, the app SHALL keep the last row in file order. Rows for different UUIDs on the same date SHALL remain distinct.

#### Scenario: Duplicate date for one meter
- **WHEN** the CSV contains multiple rows for one UUID and date with different values
- **THEN** the last value in file order is imported

#### Scenario: Same date across meters
- **WHEN** known meters have readings on the same date
- **THEN** each meter's reading remains associated with its own UUID

### Requirement: Report CSV import errors safely
The app SHALL map stable internal error codes to short localized messages, include row and field context when available, and SHALL NOT display internal code names or raw exception messages. Timestamp and timezone errors SHALL identify their respective fields without silently assigning a default.

#### Scenario: Invalid CSV structure
- **WHEN** the header or a row has an invalid structure
- **THEN** a short localized message identifies the error and row when available; the internal code is CSV_HEADER_INVALID or CSV_ROW_INVALID

#### Scenario: Invalid meter, date, or value
- **WHEN** a row contains a malformed or unknown UUID, malformed or future timestamp, invalid timezone, incompatible offset, invalid value, or negative value
- **THEN** a localized message includes row/field context, mapped to CSV_METER_ID_INVALID, CSV_METER_UNKNOWN, CSV_TIMESTAMP_INVALID, CSV_TIMEZONE_INVALID, CSV_VALUE_INVALID, or CSV_VALUE_NEGATIVE as applicable

#### Scenario: Invalid meter history
- **WHEN** values decrease within one UUID, observation instants do not increase in date order, or consumption intervals overlap across independent meters
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

### Requirement: Preserve independent meter histories in exported readings
The export SHALL include every recorded reading and its optional note, using the same stable installation ID for readings of one meter and distinct IDs for different installations. Rows SHALL be ordered by date ascending, then installation start order for same-date readings. It SHALL NOT include calculated consumption or fabricated readings.

#### Scenario: Export readings from separate meters
- **WHEN** the tracker contains readings for multiple independent installations
- **THEN** each row retains its installation ID and no reading is linked to another meter

#### Scenario: Same date across meters
- **WHEN** independent meters have readings on the same date
- **THEN** both rows are exported in installation start order without treating either as a boundary reading

### Requirement: Keep date-based CSV identity rules with timestamps
Import deduplication and export ordering SHALL use the observation's recorded local date rather than a date converted into the importing device's timezone. Duplicate rows for the same installation and recorded date SHALL keep the last complete row, including its timestamp, timezone, value, and note. Adding timestamps SHALL NOT enable multiple readings per installation per date.

#### Scenario: Duplicate date with different times
- **WHEN** two CSV rows share an installation UUID and recorded date but have different observation times
- **THEN** only the last row in file order is imported with all its fields

#### Scenario: Offset crosses a UTC date boundary
- **WHEN** a reading's recorded local date differs from its UTC date
- **THEN** duplicate detection and calendar consumption allocation use its recorded local date while weather lookup uses its absolute instant
