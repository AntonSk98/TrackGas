# Spec Delta

## MODIFIED Requirements

### Requirement: Explain CSV scope
Documentation SHALL describe the columns, units, delimiter, decimal and timestamp formats, recording timezone, meter ID, and optional note. It SHALL state that CSV is not a complete backup, contains no cached temperatures, creates meters for valid UUIDs not already known, recreates included histories, preserves omitted histories, and requires confirmation. Old date-only CSV formats SHALL be documented as unsupported.

#### Scenario: User consults export documentation
- **WHEN** the user reads the export documentation
- **THEN** they can interpret the timestamp and timezone, understand optional notes and that valid unknown UUIDs create meters, and know that omitted histories are preserved and confirmation is required

#### Scenario: Temperature data after import
- **WHEN** the user reads the import documentation
- **THEN** it explains that affected interval means are fetched again in the background rather than restored from CSV

### Requirement: Import readings for known meters
The app SHALL accept CSV rows for valid installation UUIDs whether or not they are known before import. For each included UUID, it SHALL replace the readings and notes of an existing installation or create a new installation with the imported history. Readings for omitted UUIDs SHALL remain unchanged. The installation with the latest reading date SHALL be current; installation start date SHALL break ties, except that a newly created empty current installation SHALL remain current when another installation's readings are imported.

#### Scenario: Recreate included meter histories
- **WHEN** the user confirms a valid CSV containing readings for a known meter ID
- **THEN** that installation and its readings are recreated under the same UUID, and omitted meter histories remain unchanged

#### Scenario: Reject an unknown meter ID
- **WHEN** the user confirms a valid CSV containing a UUID not known before import
- **THEN** the app creates an installation with that UUID and imports its readings without reporting an unknown-meter error

#### Scenario: Remove a saved replacement relationship
- **WHEN** an included installation has a legacy saved meter-replacement relationship
- **THEN** the obsolete relationship is removed without requiring or inventing endpoint readings, and readings for omitted installations remain unchanged

#### Scenario: Preserve the empty current installation
- **WHEN** a replacement has started a new empty current installation and the user imports readings for a different installation
- **THEN** the imported history is replaced and the empty current installation remains available and current

#### Scenario: Select the current meter by latest reading
- **WHEN** no newly created empty installation is current and independent meter histories have different latest reading dates
- **THEN** the installation with the latest reading date is current

#### Scenario: Break a latest-date tie
- **WHEN** multiple meters have a reading on the latest date
- **THEN** the installation with the latest start date is current

### Requirement: Preview and apply CSV imports atomically
The app SHALL validate the complete import and show a concise warning that included existing meter histories will be replaced and new meters will be created. The preview SHALL NOT show meter UUIDs or per-meter counts. Cancellation or validation failure SHALL leave data unchanged; confirmation SHALL commit the import atomically.

#### Scenario: Cancel a valid import
- **WHEN** the user cancels after reviewing the preview
- **THEN** no installation, reading, or note changes

#### Scenario: Invalid data prevents partial import
- **WHEN** any CSV row, meter history, or tracker integrity check fails
- **THEN** the whole import is rejected and no tracker data changes

#### Scenario: Commit a confirmed import
- **WHEN** the user confirms a valid preview and persistence succeeds
- **THEN** included histories are replaced or new installations are created as applicable, omitted histories remain unchanged, and the tracker publishes one committed snapshot

### Requirement: Report CSV import errors safely
The app SHALL map stable internal error codes to short localized messages, include row and field context when available, and SHALL NOT display internal code names or raw exception messages. Timestamp and timezone errors SHALL identify their respective fields without silently assigning a default.

#### Scenario: Invalid CSV structure
- **WHEN** the header or a row has an invalid structure
- **THEN** a short localized message identifies the error and row when available; the internal code is CSV_HEADER_INVALID or CSV_ROW_INVALID

#### Scenario: Invalid meter, date, or value
- **WHEN** a row contains a malformed UUID, malformed or future timestamp, invalid timezone, incompatible offset, invalid value, or negative value
- **THEN** a localized message includes row/field context, mapped to CSV_METER_ID_INVALID, CSV_TIMESTAMP_INVALID, CSV_TIMEZONE_INVALID, CSV_VALUE_INVALID, or CSV_VALUE_NEGATIVE as applicable

#### Scenario: Invalid meter history
- **WHEN** values decrease within one UUID, observation instants do not increase in date order, or consumption intervals overlap across independent meters
- **THEN** import fails without changing data and uses internal code CSV_METER_HISTORY_INVALID, with row/field context when available

#### Scenario: File or import failure
- **WHEN** file access or an unexpected import operation fails
- **THEN** the user sees a short safe error mapped from CSV_FILE_READ_FAILED or CSV_IMPORT_FAILED, without exception text

#### Scenario: Imported history predates a former replacement date
- **WHEN** imported per-meter histories are valid but do not contain the tracker's former replacement date or endpoints
- **THEN** import succeeds without requiring or inventing replacement-boundary readings
