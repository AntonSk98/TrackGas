# Spec Delta

## MODIFIED Requirements

### Requirement: Durable local temperatures
The app SHALL persist one interval mean per consecutive same-installation reading interval and one full-calendar-day mean per covered consumption date for the saved location. Both kinds of mean SHALL be available offline. Raw hourly weather SHALL NOT be persisted, and interval means SHALL NOT be stored for synthetic readings or cross-installation intervals.

#### Scenario: Restart
- **WHEN** the app restarts after interval and daily means have been fetched
- **THEN** both kinds of mean are shown without fetching completed data again

#### Scenario: One-day interval storage
- **WHEN** hourly weather for two readings exactly 24 hours apart contains all 24 required interval samples
- **THEN** one interval mean is stored for that pair and none of the 24 samples is persisted

#### Scenario: Daily means are shared by date
- **WHEN** a covered consumption date has a stored daily mean
- **THEN** every Dashboard projection of that date uses the same date-keyed mean rather than duplicating it per reading interval

### Requirement: Mean temperature aggregation
Each covered consumption date SHALL use the selected chart mode's temperature source: its contributing reading interval's stored mean for Interval consumption, or its full-calendar-day mean for Daily projection. Rows, chart buckets, and the selected period SHALL average known values by covered-date count, ignoring missing temperatures and uncovered dates. A bucket without known values SHALL show none.

#### Scenario: Weekly row mean
- **WHEN** a week row has three covered dates with interval means of 4, 6, and 8 degrees Celsius
- **THEN** the row shows a mean of 6 degrees Celsius

#### Scenario: Weekly row mean in Daily projection
- **WHEN** a week row has three covered dates with daily means of 4, 6, and 8 degrees Celsius
- **THEN** the row shows a mean of 6 degrees Celsius

#### Scenario: Year month mean
- **WHEN** October has two covered dates with selected-mode temperatures of 8 and 10 degrees Celsius
- **THEN** the Year view shows October at 9 degrees Celsius

#### Scenario: Bucket without data
- **WHEN** a row's covered dates have no means for the selected mode
- **THEN** that row shows no temperature

#### Scenario: Unequal interval lengths
- **WHEN** one interval contributes three covered dates at 4 degrees Celsius and another contributes one covered date at 8 degrees Celsius
- **THEN** a bucket containing those four dates shows 5 degrees Celsius, not an unweighted mean of the two interval means

#### Scenario: Clip an interval at a period boundary
- **WHEN** an interval projects to three covered dates but only one belongs to the selected period
- **THEN** its interval mean contributes once to that period's temperature aggregation in Interval consumption

### Requirement: Mean over hourly interval samples
For each missing interval mean, the app SHALL fetch hourly outdoor temperatures for the saved location and average samples at timestamps greater than or equal to the earlier observation and strictly before the later observation. Each sample SHALL have equal weight. The app SHALL NOT interpolate, weight partial hours, or substitute calendar-day means.

#### Scenario: Morning-to-morning interval
- **WHEN** observations in Europe/Berlin are November 6, 2026 at 08:00 and November 7 at 08:00
- **THEN** the interval mean uses 24 hourly samples from November 6 at 08:00 through November 7 at 07:00, not November 7's calendar-day mean

#### Scenario: Evening-to-evening interval
- **WHEN** observations are November 6 at 20:00 and November 7 at 20:00 in the same timezone
- **THEN** the interval mean uses hourly samples from November 6 at 20:00 through November 7 at 19:00

#### Scenario: Fractional-hour endpoints
- **WHEN** observations are November 6 at 08:15 and November 7 at 08:15 in the same timezone
- **THEN** samples from November 6 at 09:00 through November 7 at 08:00 are averaged equally without interpolation

#### Scenario: Daylight-saving duration
- **WHEN** an observation interval crosses a daylight-saving transition
- **THEN** sample inclusion uses absolute instants so repeated or skipped local hours are handled without assuming every local day is 24 hours

### Requirement: Mean temperature between charts
When the selected period has known temperatures for covered consumption dates, the Dashboard SHALL show their selected-mode, covered-date-weighted mean between the consumption and temperature charts, and each consumption row SHALL show its corresponding mean in degrees Celsius with the active locale's number format. Without any known temperature for the selected mode, no temperature text is shown.

#### Scenario: Period average between charts
- **WHEN** the selected week has three covered dates with interval means of 4, 6, and 8 degrees Celsius
- **THEN** the text between the consumption and temperature charts shows a mean of 6 degrees Celsius

#### Scenario: Period average in Daily projection
- **WHEN** the selected week has three covered dates with daily means of 4, 6, and 8 degrees Celsius
- **THEN** the text between the consumption and temperature charts shows a mean of 6 degrees Celsius

#### Scenario: No temperatures
- **WHEN** the selected period has no known temperatures for the selected mode
- **THEN** no temperature text is shown and gas consumption and totals are unchanged

## ADDED Requirements

### Requirement: Mean over full calendar days
For Daily projection, the app SHALL obtain a separate mean for each full calendar date covered by gas consumption, independent of reading timestamps. A daily mean SHALL represent that date at the saved meter location, not a slice of a reading interval.

#### Scenario: Reading interval covers a date
- **WHEN** readings are recorded on November 6 at 08:00 and November 7 at 08:00
- **THEN** the November 7 consumption date uses November 7's full-day mean, not the mean from November 6 at 08:00 through November 7 at 08:00

#### Scenario: Date has no consumption coverage
- **WHEN** a calendar date is not covered by any reading interval
- **THEN** its daily mean is not projected into Dashboard temperatures

### Requirement: Fetch only missing daily means
Temperature checks SHALL request daily means only for covered consumption dates without a valid stored mean for the saved location. Checks SHALL run asynchronously with bounded concurrency, and completed date means SHALL NOT be fetched again.

#### Scenario: Fetch missing covered dates
- **WHEN** covered dates are missing daily means while other dates already have valid means
- **THEN** only the missing dates are requested

#### Scenario: No location or covered dates
- **WHEN** no location is saved or no consumption date is covered
- **THEN** no daily-mean request is made

### Requirement: Incomplete daily weather stays missing
A daily mean SHALL be stored only when the provider supplies a valid full-day mean for that date. Missing or invalid daily data SHALL leave the date without a mean for a later check.

#### Scenario: Daily weather unavailable
- **WHEN** the provider omits or returns no valid mean for a covered date
- **THEN** no daily mean is stored for that date and a later check retries it

### Requirement: Targeted daily mean invalidation
Daily means SHALL remain valid while their date and location remain unchanged. Reading mutations SHALL retain means for dates that remain covered, remove means for dates no longer covered, and request means for newly covered dates. A location change or tracking-data purge SHALL remove all daily means.

#### Scenario: Reading edit preserves a covered date
- **WHEN** a reading timestamp changes but a date remains covered at the same location
- **THEN** that date's daily mean remains available while affected interval means are invalidated separately

#### Scenario: Reading mutation changes coverage
- **WHEN** an insertion, deletion, or import removes some covered dates and adds others
- **THEN** means for still-covered dates remain, obsolete dates are removed, and newly covered dates are requested

#### Scenario: Location changes or data is purged
- **WHEN** the saved location changes or tracking data is purged
- **THEN** no daily mean from the old location or purged tracker remains available

### Requirement: Reject obsolete daily weather results
A daily-mean request SHALL store a result only while its location is still current and its date is still covered by committed readings. Reading mutations, imports, purge, or location changes during a request SHALL NOT allow an obsolete result to restore invalidated data.

#### Scenario: Coverage changes during a request
- **WHEN** a daily-mean request completes after its date is no longer covered
- **THEN** the result is discarded

#### Scenario: Location changes during a request
- **WHEN** a response for the old location arrives after a location change
- **THEN** it cannot populate daily temperatures for the new location
