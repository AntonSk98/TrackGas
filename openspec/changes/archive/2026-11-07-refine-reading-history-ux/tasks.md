# Tasks

## 1. Independent meter histories

- [x] 1.1 Remove replacement-boundary entities and protection, and make Replace meter create an empty current installation without a final reading or zero baseline; verify the tracker opens a fresh database and the new empty meter remains current.
- [x] 1.2 Update meter-history, chronology, replacement, import-current-meter, and deletion tests; verify readings remain isolated by installation, backdated entries resolve correctly, and no replacement creates or protects readings.

## 2. Reading notes and History

- [x] 2.1 Add an optional persisted note to readings and expose it only in Edit; verify storage round-trips notes and Add retains its current date/value-only form.
- [x] 2.2 Render History in two rows without a note and three with one, keep a compact meter identifier when needed, and retain explicit deletion confirmation; verify populated single- and multi-meter History layouts and note clearing.

## 3. CSV notes and documentation

- [x] 3.1 Export the optional note column and accept only the four-column format; verify the previous three-column header is rejected, empty notes, quoted delimiters/quotes/line breaks, and exact meter-value precision with CSV tests.
- [x] 3.2 Update the README CSV format, compatibility, and independent-meter replacement explanation; verify the documented header and import behavior match the parser and writer.

## 4. Localized presentation and notification

- [x] 4.1 Update English and German strings for the empty state, date picker, duplicate-date error, value label, delete confirmation, location field, export/import actions, and export success; remove the in-app storage disclosure and verify resource key/placeholder parity.
- [x] 4.2 Match consumption and temperature chart title typography and reduce title-to-chart spacing where straightforward; verify charts retain readable titles, tooltip visibility, and existing inspection gestures.
- [x] 4.3 Add and use a notification-specific monochrome gas-tracking icon; verify the reminder icon is recognizable in the status bar and notification shade.

## 5. Integration verification

- [x] 5.1 Run the app unit tests and debug build from a clean local database; verify the updated tracker, History, CSV, localization, and notification behavior works together without requiring a database migration.
- [x] 5.2 Show chart metrics as title/value rows, use CSV menu labels, remove the location-dialog explanation, and include a replacement meter's first same-day reading in daily consumption; verify with regression tests.
