# Proposal

## Why

The tracker has several unnecessarily verbose labels and dialogs, while its reading history and charts could communicate the same information more compactly and consistently. Editing also lacks a way to attach context to a reading, and meter replacement currently creates special linked readings and deletion restrictions that do not fit the requested independent meter histories.

## What Changes

- Remove the local-storage disclosure from the Dashboard and History, and shorten the empty-consumption, date-picker, duplicate-date, value-field, and reading-deletion copy. Keep all updated text grammatically correct in German and English.
- Preserve the current Add form. Add an optional note to Edit, display it in History, and include it in CSV export/import using the four-column format.
- Show History entries in two rows (date, then value with a compact meter identifier when needed); use a third row only when a note exists. Do not paginate: History already uses a lazy list.
- Keep “Replace meter,” but make it start an independent meter history without requiring a final reading, creating a zero baseline, or protecting replacement-boundary readings from deletion. Preserve existing readings as independent entries. No migration of unreleased local development data is required; reinstalling locally is acceptable.
- Match the consumption and temperature chart title styling and reduce the title-to-chart gap if this can be done without complicating or obstructing tooltip inspection.
- Use concise “Export readings” / “Import readings” actions, show “Saved” / “Gespeichert” alongside the export success icon, use a recognizable monochrome brand mark for reminder notifications, and label the location input “Meter location” / “Standort des Zählers.”

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `gas-consumption-tracking`: Add edit-only notes and show them in History; compact history presentation; simplify empty-consumption copy; make replacement create an independent meter history without synthetic or protected boundary readings.
- `app-shell`: Remove the local-storage disclosure and update the related form, history, chart, and confirmation presentation.
- `reading-data-exchange`: Include optional notes in CSV while accepting existing files, and revise export/import action and success copy.
- `reading-reminder`: Use a recognizable monochrome brand mark for the notification icon.
- `meter-location`: Update the location field label without expanding lookup beyond its current city/place search.

## Impact

The changes affect localized Android string resources, Compose reading and location forms, History and chart presentation, reminder notification resources, CSV serialization and parsing, reading/installations persistence, and meter-replacement validation. Existing requirements for centralized English/German resources remain in force. CSV import requires the four-column format; local database migration from the unreleased development version is out of scope.
