# Spec Delta

## MODIFIED Requirements

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

### Requirement: Explain CSV scope
Documentation SHALL describe the columns, units, delimiter, decimal and timestamp formats, recording timezone, meter ID, and optional note. It SHALL state that CSV is not a complete backup, contains no cached temperatures, accepts only known UUIDs, recreates included histories, preserves omitted histories, and requires confirmation. Old date-only CSV formats SHALL be documented as unsupported.

#### Scenario: User consults export documentation
- **WHEN** the user reads the export documentation
- **THEN** they can interpret the timestamp and timezone, understand optional notes and known-meter restrictions, and know that omitted histories are preserved and confirmation is required

#### Scenario: Temperature data after import
- **WHEN** the user reads the import documentation
- **THEN** it explains that affected interval means are fetched again in the background rather than restored from CSV

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

## ADDED Requirements

### Requirement: Keep date-based CSV identity rules with timestamps
Import deduplication and export ordering SHALL use the observation's recorded local date rather than a date converted into the importing device's timezone. Duplicate rows for the same installation and recorded date SHALL keep the last complete row, including its timestamp, timezone, value, and note. Adding timestamps SHALL NOT enable multiple readings per installation per date.

#### Scenario: Duplicate date with different times
- **WHEN** two CSV rows share an installation UUID and recorded date but have different observation times
- **THEN** only the last row in file order is imported with all its fields

#### Scenario: Offset crosses a UTC date boundary
- **WHEN** a reading's recorded local date differs from its UTC date
- **THEN** duplicate detection and calendar consumption allocation use its recorded local date while weather lookup uses its absolute instant
