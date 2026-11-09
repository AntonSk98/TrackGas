# Spec Delta

## MODIFIED Requirements

### Requirement: Create a dated cumulative reading
The app SHALL provide an accessible add icon in the top-right toolbar. Its form SHALL request a date, observation time, and cumulative meter reading in cubic metres, with the current device-local date and time selected initially and an earlier date/time selectable. The Add form SHALL NOT offer a note field. Saving valid input SHALL add a reading and refresh consumption; cancelling SHALL leave readings unchanged.

#### Scenario: Save a backdated reading
- **WHEN** the user opens Add, selects an earlier valid date and observation time, enters a valid meter value, and saves
- **THEN** the reading appears with that date, time, and value and the dashboard recalculates without resetting the selected period

#### Scenario: Add does not offer a note
- **WHEN** the user opens Add
- **THEN** the form offers date, time, and value fields without a note field

#### Scenario: Cancel creation
- **WHEN** the user dismisses an unsaved add form
- **THEN** no reading is added

#### Scenario: Add uses the current device date after a timezone change
- **WHEN** the device timezone changes while the app process remains alive and the user opens Add
- **THEN** its default date, observation time, timezone, and future-time validation use the device's new timezone

### Requirement: Edit and delete reading history
The app SHALL expose all readings newest first, independently of dashboard period selection. Edit SHALL preserve the reading's date, identity, and installation, allow observation-time and meter-value correction, and allow an optional note to be added, changed, or cleared. Add SHALL NOT offer notes. Every reading SHALL be individually deletable after confirmation; deletion SHALL recalculate consumption only within its own installation.

#### Scenario: Edit date and value
- **WHEN** the user edits a reading, changes its valid meter value or observation time, enters a note, and saves
- **THEN** the existing reading is updated while its date, identity, and installation remain unchanged

#### Scenario: Clear an existing note
- **WHEN** the user edits a reading and removes its note
- **THEN** the reading is saved without a note

#### Scenario: Preserve the reading date and identity
- **WHEN** the user edits a reading
- **THEN** the original date remains read-only, the observation time is editable, and the reading is updated rather than duplicated

#### Scenario: Delete a reading from an independent history
- **WHEN** the user confirms deletion of any reading, including one created before or after a meter replacement
- **THEN** that reading is removed, other installations remain unchanged, and same-installation consumption is recalculated

### Requirement: Compact reading history entries
History SHALL show each reading in two rows: its recorded local date and observation time, and a row containing its value. When multiple installations exist, a compact meter identifier SHALL remain available with the value. A saved note SHALL appear as a third row; a reading without a note SHALL NOT reserve a note row.

#### Scenario: History without notes
- **WHEN** History contains a reading without a note
- **THEN** the entry displays its localized recorded date/time and value in two rows without repeating "meter" or "reading" labels

#### Scenario: History with a note
- **WHEN** History contains a reading with a saved note
- **THEN** its recorded date/time, value, and note appear in three rows

#### Scenario: Distinguish independent meters
- **WHEN** History contains readings from more than one installation
- **THEN** each value remains associated with a compact meter identifier without adding a separate repeated role row

### Requirement: Temperature granularity and gaps
Week and Month SHALL plot interval-derived temperature means on covered consumption dates; Year SHALL plot the covered-date-weighted monthly mean of those values. An interval's mean SHALL be reused on each date to which its gas consumption is allocated, not presented as independently fetched daily weather. Buckets without temperature SHALL have no point and the line SHALL NOT bridge them.

#### Scenario: Missing temperature day
- **WHEN** an interval covering a day in the middle of the period has no stored mean
- **THEN** that day has no temperature point and the line is broken there

#### Scenario: Year view
- **WHEN** October has two covered dates with interval-derived temperatures of 8 and 10 degrees Celsius
- **THEN** the Year chart plots October at 9 degrees Celsius

#### Scenario: Multi-day interval
- **WHEN** one reading interval allocates gas to three calendar dates and has a stored mean of 6 degrees Celsius
- **THEN** all three dates use 6 degrees Celsius for temperature projection without claiming three separately observed daily means

### Requirement: Temperature inspection and consumption rows
Long-pressing a consumption bar SHALL show its date and consumption only. Long-pressing a temperature point SHALL show its date and interval-derived temperature only. Each consumption row SHALL show the bucket's mean interval-derived temperature beside its consumption when a value exists.

#### Scenario: Inspect consumption
- **WHEN** the user long-presses a consumption bar for a bucket with a temperature
- **THEN** the tooltip shows the date and consumption without temperature

#### Scenario: Inspect temperature
- **WHEN** the user long-presses a temperature point
- **THEN** the tooltip shows the date and interval-derived temperature in degrees Celsius without consumption

#### Scenario: Consumption row shows mean temperature
- **WHEN** a consumption row has interval-derived temperatures for its covered dates
- **THEN** it shows their covered-date-weighted mean temperature beside consumption

## ADDED Requirements

### Requirement: Unambiguous observation timestamps
Each reading SHALL retain an observation timestamp at minute precision and its recording timezone across restarts, edits, and device timezone changes. Add SHALL capture the device timezone when the draft opens; Edit SHALL retain the recorded timezone. Observation time means when the meter was read, not when Save was pressed. Time controls and errors SHALL be localized in English and German.

#### Scenario: Delayed submission
- **WHEN** the user saves at 20:00 but enters an observation time of 08:00
- **THEN** the reading retains 08:00 as its observation time

#### Scenario: Device timezone changes
- **WHEN** the device timezone changes after a reading is saved
- **THEN** the reading retains its recorded date/time and the same absolute observation instant

#### Scenario: Restore a draft
- **WHEN** the device rotates with an unsaved observation time selected
- **THEN** the matching draft retains that time and timezone without replacing them with the current clock time

### Requirement: Validate observation times
Saving SHALL reject a missing or invalid observation time, an instant in the future, a nonexistent local time, or timestamps that do not strictly increase between same-installation chronological readings. Repeated local times during daylight-saving fallback SHALL use the earlier valid offset. Existing one-reading-per-date and nondecreasing-value rules SHALL remain in effect.

#### Scenario: Future time today
- **WHEN** the user selects today's date and a time later than the current instant
- **THEN** saving identifies the time error without modifying readings

#### Scenario: Daylight-saving clock gap
- **WHEN** the user selects a local observation time skipped by the recorded timezone's daylight-saving transition
- **THEN** saving identifies an invalid time instead of silently shifting it

#### Scenario: Repeated clock hour
- **WHEN** a selected local observation time occurs twice during fallback
- **THEN** its timestamp uses the earlier valid offset consistently in storage and weather lookup

#### Scenario: Timestamp order conflicts with date order
- **WHEN** a proposed timestamp is not strictly between the absolute instants of its same-installation neighbours
- **THEN** final transactional validation rejects it without modifying data

#### Scenario: Another reading on the same date
- **WHEN** the user selects a different time on an already occupied date in the same installation
- **THEN** the existing duplicate-date rule still rejects the additional reading

### Requirement: Preserve calendar consumption semantics with timestamps
Observation times SHALL determine weather windows, not gas calendar allocation. Consecutive readings SHALL retain the existing earlier-date-exclusive, later-date-inclusive allocation and equal distribution across covered dates. Replacement-start opening consumption SHALL retain its existing behavior and SHALL NOT create a weather interval without two actual readings.

#### Scenario: Morning measurements
- **WHEN** readings are taken on November 6 at 08:00 and November 7 at 08:00
- **THEN** their gas difference remains attributed to November 7 while its temperature represents the actual timestamp interval

#### Scenario: Correct only an observation time
- **WHEN** a valid observation-time edit leaves dates and meter values unchanged
- **THEN** gas totals and their calendar allocation remain unchanged while affected temperature means are invalidated

#### Scenario: Opening consumption without an interval
- **WHEN** a replacement meter has only its first reading on its start date
- **THEN** opening gas consumption remains available but no temperature is invented for that contribution
