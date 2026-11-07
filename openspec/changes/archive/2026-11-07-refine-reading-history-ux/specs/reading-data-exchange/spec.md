# Spec Delta

## MODIFIED Requirements

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
The file SHALL be UTF-8, semicolon-delimited CSV with the header `meter_id;date;meter_value;note`. Each reading SHALL have one row with its stable installation ID, ISO 8601 calendar date, exact cumulative cubic-metre value using a dot decimal separator and no grouping, and an optional note. The importer SHALL accept only this four-column header and SHALL reject the previous three-column format. Fields SHALL use CSV quoting and doubled quotes when needed.

#### Scenario: Locale-independent precision
- **WHEN** a reading dated 2026-10-28 in installation `11111111-1111-4111-8111-111111111111` has the value 123.450 and no note
- **THEN** its CSV row is `11111111-1111-4111-8111-111111111111;2026-10-28;123.450;` and does not use localized dates or decimal separators

#### Scenario: Export a note
- **WHEN** a reading has a saved note
- **THEN** its note appears in the fourth field of the exported row

#### Scenario: Reject the previous three-column format
- **WHEN** the user imports a file with the previous `meter_id;date;meter_value` header
- **THEN** the import is rejected with the `CSV_HEADER_INVALID` error

#### Scenario: Import a four-column file
- **WHEN** the user imports a valid file with the `meter_id;date;meter_value;note` header
- **THEN** each non-empty note is associated with its corresponding reading

#### Scenario: Special characters in a field
- **WHEN** an exported field contains a semicolon, double quote, or line break
- **THEN** the field is quoted and any double quote is doubled so that a CSV reader can recover the original field

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
The app documentation SHALL describe the columns, units, delimiter, decimal and date formats, meter ID meaning, and optional note field. It SHALL explain that CSV contains readings, not a complete restorable backup; import accepts only known UUIDs, recreates included histories under the same UUIDs, preserves omitted histories, and requires confirmation.

#### Scenario: User consults export documentation
- **WHEN** the user reads the export documentation
- **THEN** they can interpret the CSV, identify optional notes, understand the known-meter restriction and omitted-history behavior, and know that import requires confirmation

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

## REMOVED Requirements

### Requirement: Preserve meter boundaries in exported readings
**Reason**: Meter replacement no longer creates linked boundary readings; installations are exported as independent histories.
**Migration**: No local database migration is required for this unreleased app; reinstalling locally is acceptable. Existing reading rows remain ordinary rows in their installations.

## ADDED Requirements

### Requirement: Preserve independent meter histories in exported readings
The export SHALL include every recorded reading and its optional note, using the same stable installation ID for readings of one meter and distinct IDs for different installations. Rows SHALL be ordered by date ascending, then installation start order for same-date readings. It SHALL NOT include calculated consumption or fabricated readings.

#### Scenario: Export readings from separate meters
- **WHEN** the tracker contains readings for multiple independent installations
- **THEN** each row retains its installation ID and no reading is linked to another meter

#### Scenario: Same date across meters
- **WHEN** independent meters have readings on the same date
- **THEN** both rows are exported in installation start order without treating either as a boundary reading
