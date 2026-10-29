# Proposal

## Why

Readings are currently confined to the device. A CSV export lets users retain and analyse their recorded meter values outside the app.

## What Changes

- Add an offline action to save all recorded readings as a CSV file selected by the user.
- Export one row per reading with its date, meter installation identifier, and exact cumulative value in cubic metres, including replacement endpoints.
- Define a stable, locale-independent format suitable for a future import proposal, without implementing import or promising a complete restorable backup.
- Explain the export format and its limitations in the app documentation.

## Capabilities

### New Capabilities

- `reading-data-exchange`: Export recorded meter readings to a portable CSV file.

### Modified Capabilities

None.

## Impact

- Android tracker options, export state and file handling, reading serialization, resources, and tests.
- Uses the existing committed tracker model and Android's document picker; no server or account is required.
- CSV files contain personal meter history and are outside the app's private storage once saved.
