# Proposal

## Why

CSV import currently rejects the entire file when a row contains a valid meter UUID that is not in the tracker. Importing that row should instead create the meter and its reading, so valid CSV data can be brought into the app without manually creating the meter first.

## What Changes

- Accept valid meter UUIDs not yet in the tracker and create a meter installation and its imported reading history for them.
- Preserve existing replacement behavior for known meters and keep the import atomic; malformed UUIDs and other invalid CSV data remain errors.
- Update the CSV import specification and user-facing documentation to describe importing previously unknown meters.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `reading-data-exchange`: allow CSV imports to create meters for valid UUIDs not already known to the tracker.

## Impact

CSV validation and candidate construction, import persistence, related tests, and the CSV import documentation. No CSV format change is required.
