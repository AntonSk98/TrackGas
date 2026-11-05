# Average Temperature Specification

## Purpose

Retrieve, store, and keep daily average outdoor temperatures for the days covered by tracked readings without slowing or blocking tracking.

## Requirements

### Requirement: Asynchronous temperature check
After a reading is added, edited, deleted, or imported, after the meter location is saved, and when network connectivity becomes available, the app SHALL asynchronously check for temperature gaps. Saving, deleting, or importing SHALL complete and update the UI without waiting for, or failing because of, this check.

#### Scenario: Reading saved
- **WHEN** a reading is saved
- **THEN** the save completes immediately and the temperature check runs in the background

#### Scenario: Provider failure is silent
- **WHEN** the check fails because the provider errors
- **THEN** no error blocks tracking, stored data is unchanged, and the days stay missing for the next check

### Requirement: No fetching while offline
While the device has no network connectivity, the app SHALL NOT fetch temperatures. When connectivity becomes available it SHALL run the gap check again.

#### Scenario: Offline save
- **WHEN** a reading is saved while offline
- **THEN** no temperature request is made and no error is shown

#### Scenario: Connectivity returns
- **WHEN** the device regains connectivity while temperatures are missing
- **THEN** the gap check runs and fetches them

### Requirement: Fill missing days in batches
The check SHALL consider every calendar day from the earliest to the latest reading date, including days between readings that are far apart and days of all pre-existing readings. For days without a stored temperature it SHALL request daily mean temperatures for the saved location in date-range batches covering contiguous missing days, not one request per day, and SHALL NOT request days that already have a temperature.

#### Scenario: Gap between readings
- **WHEN** readings exist on October 1 and October 10 and no temperatures are stored
- **THEN** temperatures for October 1 through 10 are requested in a single batch and all ten days are stored

#### Scenario: Backfill existing history
- **WHEN** a location is saved for a tracker with 150 existing readings and no stored temperatures
- **THEN** every day from the earliest to the latest reading is requested in batched ranges and stored

#### Scenario: Only missing days
- **WHEN** October 1–5 have stored temperatures and a new reading extends the range to October 8
- **THEN** only October 6–8 are requested

#### Scenario: Nothing missing
- **WHEN** every covered day already has a temperature
- **THEN** no network request is made

#### Scenario: No location or no range
- **WHEN** no meter location is saved or fewer than two readings exist
- **THEN** no network request is made

### Requirement: Unavailable days are retried
A day for which the provider returns no value SHALL stay missing, SHALL NOT be stored as a temperature, and SHALL be requested again by a later check.

#### Scenario: Recent day not yet available
- **WHEN** the provider has no value for yesterday
- **THEN** yesterday has no stored temperature and is requested again on the next check

### Requirement: Durable local temperatures
Fetched temperatures SHALL persist in the on-device database across restarts and SHALL be shown without a network request.

#### Scenario: Restart
- **WHEN** the app restarts after temperatures were fetched
- **THEN** they are shown without a network request

### Requirement: Temperatures follow the reading range
After a reading is deleted, edited, or imported, stored temperatures dated outside the earliest-to-latest range of the remaining readings SHALL be removed atomically with that change. Temperatures inside the range SHALL be kept. Purging all tracking data SHALL remove all temperatures.

#### Scenario: Delete the latest reading
- **WHEN** the reading dated October 10 is deleted and the latest remaining reading is October 6
- **THEN** temperatures for October 7 through 10 are removed and those through October 6 are kept

#### Scenario: Delete a middle reading
- **WHEN** a reading between the earliest and latest readings is deleted
- **THEN** all temperatures remain, because those days are still covered

#### Scenario: Purge
- **WHEN** the user purges all tracking data
- **THEN** all stored temperatures are removed

### Requirement: Automatic chart refresh
While Dashboard is shown, temperatures that finish loading or are removed SHALL appear in the chart, rows, and period average without user action and without resetting the selected period or tab.

#### Scenario: Fetch completes while viewing
- **WHEN** the Dashboard chart is visible and a background fetch stores new temperatures for its period
- **THEN** the temperature line and values appear automatically

### Requirement: Mean temperature aggregation
A period's mean temperature SHALL be the mean of the stored daily temperatures it contains, ignoring days without a value. A weekly row, a monthly row, and the Year chart's monthly points SHALL each use this mean. A bucket without any stored temperature SHALL show none.

#### Scenario: Weekly row mean
- **WHEN** a week row covers days with stored temperatures of 4, 6, and 8 °C
- **THEN** the row shows a mean of 6 °C

#### Scenario: Year month mean
- **WHEN** October has stored daily temperatures of 8 and 10 °C
- **THEN** the Year view shows October at 9 °C

#### Scenario: Bucket without data
- **WHEN** a row's days have no stored temperatures
- **THEN** that row shows no temperature

### Requirement: Mean temperature between charts
When the selected period has stored temperatures, the Dashboard SHALL show the period's mean temperature between the consumption and temperature charts, and each consumption row SHALL show its mean temperature in degrees Celsius with the active locale's number format. Without any temperature, no temperature text is shown.

#### Scenario: Period average between charts
- **WHEN** the selected week has stored temperatures of 4, 6, and 8 °C
- **THEN** the text between the consumption and temperature charts shows a mean of 6 °C

#### Scenario: No temperatures
- **WHEN** the selected period has no stored temperatures
- **THEN** no temperature text is shown and the chart and totals are unchanged

### Requirement: Localized temperature text
Temperature labels SHALL be available in English and German.

#### Scenario: German label
- **WHEN** the app language is German
- **THEN** the temperature labels and number format are German
