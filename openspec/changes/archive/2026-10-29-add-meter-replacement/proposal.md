# Proposal

## Why

TrackGas currently treats every reading as belonging to one unchanging physical meter, so a replacement meter's lower values are rejected. Meter replacement must be represented explicitly and durably so validation and consumption remain correct without a one-use validation bypass.

## What Changes

- Add a gear-shaped options button immediately to the left of the existing top-right Add icon on both tabs. Its menu initially contains only "Replace meter".
- Support replacement effective today after the user saves today's final old-meter reading through ordinary Add/Edit.
- Open a replacement confirmation showing the saved final old-meter reading and a read-only date. Do not request a new-meter value; confirmation automatically establishes a zero baseline for the new installation on that date.
- Store successive meter installations and their replacement boundaries, preserving all historical readings and exact decimal values.
- Scope chronology and date uniqueness to each installation. Ordinary Add resolves the installation by date; the replacement action alone creates the new installation's automatic same-day zero baseline, without requiring a new-meter observation that day.
- Calculate intervals only within one installation. Keep the existing `(earlier date, later date]` convention: old 1100 on October 27, old final 1105 and new baseline 0 on October 28, new 3 on October 29 produce 5 m3 on October 28 and 3 m3 on October 29.
- The first new-meter reading can be entered through ordinary Add the next day: a value of 5 means 5 - 0 = 5 m3 for that day.
- Identify replacement endpoints in History and prevent ordinary deletion of structural boundary readings; allow value corrections with installation-scoped validation.
- Migrate existing on-device data without resetting it and extend existing transactional, error, lifecycle, and snapshot guarantees to replacement.

This version does not add accounts, profile management, other unfinished menu options, retroactive replacement, multiple replacements per calendar day, replacement undo, or sub-day consumption allocation.

## Capabilities

### New Capabilities

None. Replacement is part of the existing local gas tracker.

### Modified Capabilities

- `app-shell`: Add the shared accessible options menu and replacement dialog, and distinguish structural readings in History.
- `gas-consumption-tracking`: Model installation boundaries, installation-scoped reading rules, durable atomic replacement, boundary-safe management, and consumption across successive meters.

## Impact

- Kotlin domain models, `ReadingValidation`, `ReadingCollection`, `ReadingRepository`, and `Consumption` must operate on installation-aware committed data.
- Room schema v1, entity mapping, DAO queries, `RoomReadingRepository`, and database construction require an explicit non-destructive migration.
- `TrackerViewModel`, `ReadingDialog`, reading drafts/forms, `TrackerScreen`, `ReadingHistory`, and `MainActivity` callbacks must carry replacement state and installation identity consistently.
- Existing JUnit and Android/Compose instrumentation suites need installation, migration, race, lifecycle, and replacement UI coverage. README reading-management and calculation documentation must explain the workflow.
- No new runtime dependency or network service is required.
