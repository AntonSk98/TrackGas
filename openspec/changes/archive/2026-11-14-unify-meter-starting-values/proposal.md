# Proposal

## Why

TrackGas currently starts a replacement with an empty meter history, while its first-ever reading is saved against a pre-created empty installation. Treating both cases as creation of a meter with an explicit starting value makes their behavior consistent and avoids counting the meter's existing register value as gas consumption.

## What Changes

- **BREAKING:** Create a meter and its first reading together. The first reading when no readings exist is its starting value; replacing a meter also requires the new meter's starting value.
- Exclude a meter's starting value from consumption, while calculating later usage from that baseline as usual.
- Remove a meter atomically when its last reading is deleted. A tracker with no readings has no meter; the next reading creates a new one.
- Keep each meter's reading chronology independent and preserve the other meters' history and consumption.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `gas-consumption-tracking`: Define meter creation, starting-value consumption, replacement, and last-reading deletion behavior.
- `app-shell`: Update the first-reading and replacement forms to collect and identify a meter's starting value.

## Impact

The change affects the reading and installation domain model, validation and consumption projection, `TrackerViewModel` and Compose forms, and Room repository/database initialization, deletion, and import/purge handling. This is a development-only, fresh-install change: the existing Room schema is unchanged, but existing databases may contain empty installation rows that violate the new invariant. Reinstalling clears that legacy data; no migration is planned.
