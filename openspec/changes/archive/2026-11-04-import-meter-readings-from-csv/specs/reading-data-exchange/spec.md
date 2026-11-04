# Spec Delta

## MODIFIED Requirements

### Requirement: Explain CSV scope
The app documentation SHALL describe the columns, units, delimiter, decimal and date formats, meter ID meaning, and the inclusion of replacement endpoints. It SHALL explain that CSV is readings data, not a restorable backup; imports recreate histories only for UUIDs already known to the tracker, remove replacement relationships involving included UUIDs, leave omitted meter readings unchanged, and require confirmation.

#### Scenario: User consults export documentation
- **WHEN** the user reads the CSV documentation
- **THEN** they can interpret the exported columns and understand the known-meter restriction, replacement-history removal, unchanged omitted readings, and confirmation requirement

## ADDED Requirements

### Requirement: Import readings for known meters
For each known UUID in a valid CSV, import SHALL recreate its installation history under the same UUID, replacing its existing readings and any replacement relationships that reference it. Readings for omitted UUIDs SHALL remain unchanged.

#### Scenario: Recreate included meter histories
- **WHEN** the user confirms a valid CSV containing readings for known meter IDs
- **THEN** each included installation's existing readings are replaced by the CSV readings under the same UUID, while readings for omitted meters remain unchanged

#### Scenario: Remove a saved replacement relationship
- **WHEN** an included installation participates in a saved meter replacement
- **THEN** the replacement relationship is removed, no replacement endpoint rows are required or invented, and any omitted installation's readings remain unchanged

#### Scenario: Choose the current meter by latest reading
- **WHEN** an import creates independent histories whose latest readings have different dates
- **THEN** the installation with the latest reading date becomes current

#### Scenario: Break latest-date ties
- **WHEN** multiple installations have readings on the latest date
- **THEN** the installation with the latest start date becomes current

#### Scenario: CSV contains an unknown meter ID
- **WHEN** any CSV row references a meter UUID not known to the tracker
- **THEN** the entire import is rejected, no tracker data changes, and the user sees the unknown-meter error with the relevant row information

### Requirement: Preview and apply CSV imports atomically
The app SHALL validate a complete import and show a concise warning that existing meter data will be replaced by CSV values and saved replacement relationships touching imported meters will be removed. The preview SHALL NOT display meter UUIDs or per-meter counts. Cancellation or any validation failure SHALL leave tracker data unchanged; confirmed valid changes SHALL be applied as one atomic operation.

#### Scenario: Cancel a valid import
- **WHEN** the user cancels after reviewing the import preview
- **THEN** all existing tracker data remains unchanged

#### Scenario: Invalid row prevents partial import
- **WHEN** any row fails parsing, meter validation, or tracker-integrity validation
- **THEN** no rows are imported and existing data remains unchanged

#### Scenario: Commit a confirmed import
- **WHEN** the user confirms a valid preview and persistence succeeds
- **THEN** included installations and readings are recreated together, affected replacement relationships are removed, omitted readings remain unchanged, and the tracker reflects one committed snapshot

### Requirement: Resolve duplicate CSV readings deterministically
For duplicate rows with the same meter ID and date, the app SHALL keep the last row in file order. Rows for different meter IDs on the same date SHALL remain distinct.

#### Scenario: Duplicate date for one meter
- **WHEN** the CSV contains multiple rows with the same meter ID and date but different values
- **THEN** the value from the last such row in file order is imported for that meter and date

#### Scenario: Same date across different meters
- **WHEN** the CSV contains rows for different known meter IDs on the same date
- **THEN** both readings remain in the imported tracker

### Requirement: Report CSV import errors safely
The app SHALL map stable internal error codes to short localized messages, include row and field context when available, and SHALL NOT display raw exception messages, system-generated details, or internal code names.

#### Scenario: Invalid CSV structure
- **WHEN** the header or a row has an invalid structure
- **THEN** the user sees a short localized error with a row number when applicable; the internal code is CSV_HEADER_INVALID or CSV_ROW_INVALID and no raw exception text is shown

#### Scenario: Invalid or unknown meter ID
- **WHEN** a row contains a malformed or unrecognized meter ID
- **THEN** the user sees a short localized error with row context; the internal code is CSV_METER_ID_INVALID or CSV_METER_UNKNOWN and no raw exception text is shown

#### Scenario: Invalid date or value
- **WHEN** a row contains a date or meter value that cannot be accepted
- **THEN** the user sees a short localized error with row and field context; the internal code is CSV_DATE_INVALID, CSV_VALUE_INVALID, or CSV_VALUE_NEGATIVE and no raw exception text is shown

#### Scenario: Imported data violates tracker invariants
- **WHEN** imported values decrease in date order within one meter UUID, or their usage intervals overlap another independent meter's intervals
- **THEN** the import is rejected with internal code CSV_METER_HISTORY_INVALID and existing data remains unchanged

#### Scenario: Imported history predates an old replacement date
- **WHEN** imported per-meter histories are chronologically valid but do not contain the tracker's former replacement date or endpoints
- **THEN** the import succeeds without requiring or inventing replacement-boundary readings

#### Scenario: Unexpected read or import failure
- **WHEN** an unexpected file access or import failure occurs
- **THEN** the user sees CSV_FILE_READ_FAILED or CSV_IMPORT_FAILED as appropriate and never sees the exception message
