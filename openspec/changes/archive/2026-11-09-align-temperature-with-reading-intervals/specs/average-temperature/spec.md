# Spec Delta

## MODIFIED Requirements

### Requirement: Asynchronous temperature check
After a reading is added, edited, deleted, or imported, after the meter location is saved, when network connectivity becomes available, and on tracker startup, the app SHALL asynchronously check for missing interval means. Saving, deleting, or importing SHALL complete and update the UI without waiting for, or failing because of, this check.

#### Scenario: Reading saved
- **WHEN** a reading is saved
- **THEN** the save completes immediately and the temperature check runs in the background

#### Scenario: Provider failure is silent
- **WHEN** the check fails because the provider errors
- **THEN** no error blocks tracking, unrelated stored means are unchanged, and affected interval means stay missing for a later check

#### Scenario: Restart with missing weather
- **WHEN** the tracker starts with valid intervals lacking means and connectivity is available
- **THEN** missing means are requested asynchronously without delaying tracker availability

### Requirement: Durable local temperatures
Only one calculated temperature mean per consecutive same-installation reading interval SHALL persist across restarts. Stored means SHALL be available offline without a request. Raw hourly weather SHALL NOT be persisted, and means SHALL NOT be stored for synthetic readings or cross-installation intervals.

#### Scenario: Restart
- **WHEN** the app restarts after interval means were fetched
- **THEN** they are shown without fetching those completed intervals again

#### Scenario: One-day interval storage
- **WHEN** hourly weather for two readings exactly 24 hours apart contains all 24 required samples
- **THEN** one mean is stored for that pair and none of the 24 individual samples is persisted

### Requirement: Mean temperature aggregation
Each covered consumption date SHALL inherit its contributing reading interval's stored mean. Rows, chart buckets, and the selected period SHALL average these known projected values by covered-date count, ignoring missing temperatures and uncovered dates. A bucket without known values SHALL show none. This is an approximate projection, not a reconstruction of daily weather.

#### Scenario: Weekly row mean
- **WHEN** a week row has three covered dates with projected temperatures of 4, 6, and 8 degrees Celsius
- **THEN** the row shows a mean of 6 degrees Celsius

#### Scenario: Year month mean
- **WHEN** October has two covered dates with projected temperatures of 8 and 10 degrees Celsius
- **THEN** the Year view shows October at 9 degrees Celsius

#### Scenario: Bucket without data
- **WHEN** a row's covered dates have no interval temperature means
- **THEN** that row shows no temperature

#### Scenario: Unequal interval lengths
- **WHEN** one interval contributes three covered dates at 4 degrees Celsius and another contributes one covered date at 8 degrees Celsius
- **THEN** a bucket containing those four dates shows 5 degrees Celsius, not an unweighted mean of the two interval means

#### Scenario: Clip an interval at a period boundary
- **WHEN** an interval projects to three covered dates but only one belongs to the selected period
- **THEN** its mean contributes once to that period's temperature aggregation

### Requirement: Mean temperature between charts
When the selected period has known interval-derived temperatures on covered consumption dates, the Dashboard SHALL show their covered-date-weighted mean between the consumption and temperature charts, and each consumption row SHALL show its corresponding mean in degrees Celsius with the active locale's number format. Without any known temperature, no temperature text is shown.

#### Scenario: Period average between charts
- **WHEN** the selected week has three covered dates with projected temperatures of 4, 6, and 8 degrees Celsius
- **THEN** the text between the consumption and temperature charts shows a mean of 6 degrees Celsius

#### Scenario: No temperatures
- **WHEN** the selected period has no known interval-derived temperatures
- **THEN** no temperature text is shown and gas consumption and totals are unchanged

## REMOVED Requirements

### Requirement: Fill missing days in batches
**Reason**: Temperature belongs to an actual reading interval rather than each calendar day.
**Migration**: Replace day-gap lookup with missing-interval lookup and transient hourly requests. Fresh installation is required; daily cache migration is not supported.

### Requirement: Unavailable days are retried
**Reason**: Availability and completion are now evaluated for an entire reading interval.
**Migration**: Retry incomplete interval means on later checks rather than persisting partial daily results.

### Requirement: Temperatures follow the reading range
**Reason**: A calendar-date range cannot identify means made obsolete by changing interval endpoints.
**Migration**: Remove obsolete interval means atomically with reading mutations and retain only means for unchanged current endpoint pairs and location.

## ADDED Requirements

### Requirement: Mean over hourly interval samples
For each missing mean, the app SHALL fetch hourly outdoor temperatures for the saved location and average samples at timestamps greater than or equal to the earlier observation and strictly before the later observation. Each sample SHALL have equal weight. The app SHALL NOT interpolate, weight partial hours, or substitute calendar-day means.

#### Scenario: Morning-to-morning interval
- **WHEN** observations in Europe/Berlin are November 6, 2026 at 08:00 and November 7 at 08:00
- **THEN** the mean uses 24 hourly samples from November 6 at 08:00 through November 7 at 07:00, not November 7's calendar-day mean

#### Scenario: Evening-to-evening interval
- **WHEN** observations are November 6 at 20:00 and November 7 at 20:00 in the same timezone
- **THEN** the mean uses hourly samples from November 6 at 20:00 through November 7 at 19:00

#### Scenario: Fractional-hour endpoints
- **WHEN** observations are November 6 at 08:15 and November 7 at 08:15 in the same timezone
- **THEN** samples from November 6 at 09:00 through November 7 at 08:00 are averaged equally without interpolation

#### Scenario: Daylight-saving duration
- **WHEN** an observation interval crosses a daylight-saving transition
- **THEN** sample inclusion uses absolute instants so repeated or skipped local hours are handled without assuming every local day is 24 hours

### Requirement: Incomplete interval weather stays missing
A mean SHALL be saved only if all expected whole-hour sample timestamps in its interval have non-null values. Provider failures or incomplete data SHALL leave the mean missing for a later check. An interval containing no whole-hour sample timestamp SHALL have no temperature and SHALL NOT trigger futile weather requests.

#### Scenario: Recent weather unavailable
- **WHEN** the provider returns no data or omits any required hourly value
- **THEN** no partial mean is stored and a later check retries that interval

#### Scenario: Short interval without a sample
- **WHEN** consecutive observations on different dates are at 23:45 and 00:00 with no whole-hour timestamp inside the half-open interval
- **THEN** no mean is shown or requested, and gas consumption remains available

#### Scenario: Request dates include extra hours
- **WHEN** a provider response has missing values outside the actual observation interval
- **THEN** those values do not prevent saving a complete mean for the required samples inside the interval

### Requirement: Fetch only missing interval means
Checks SHALL request weather only for eligible consecutive same-installation intervals without a valid stored mean. Completed intervals SHALL NOT be re-fetched. Without a saved location or an eligible pair of readings, no request SHALL be made. Requests SHALL run in the background with bounded concurrency.

#### Scenario: Add one latest reading
- **WHEN** earlier interval means are complete and one later reading is saved
- **THEN** only the newly formed interval needs a mean

#### Scenario: Unrelated installations
- **WHEN** two readings exist but belong to different installations
- **THEN** no weather interval is created between them

#### Scenario: No location
- **WHEN** consecutive readings exist but no meter location is saved
- **THEN** no temperature request is made

### Requirement: Targeted interval mean invalidation
Reading mutations SHALL atomically remove means whose endpoint identity, timestamp, or adjacency changed and preserve unchanged means. A location change SHALL invalidate all means. Purge SHALL remove all means. Note-only and value-only edits SHALL retain means when the weather window is unchanged. Obsolete means SHALL NOT remain visible while replacement data is pending.

#### Scenario: Insert a middle reading
- **WHEN** A and C have a mean and reading B is inserted between them
- **THEN** A-C's mean is removed, A-B and B-C become missing, and unrelated interval means remain available

#### Scenario: Delete a middle reading
- **WHEN** B is deleted between A and C
- **THEN** A-B and B-C means are removed and the new A-C mean is requested rather than calculated from the two old means

#### Scenario: Change an observation time
- **WHEN** B's observation time changes between A and C
- **THEN** only the A-B and B-C means are invalidated

#### Scenario: Edit only a note or meter value
- **WHEN** an accepted edit changes no observation timestamp or interval adjacency
- **THEN** existing means remain valid without weather requests

#### Scenario: Import an included history
- **WHEN** import replaces one installation's readings while omitting others
- **THEN** obsolete means for the replaced history are removed and unchanged intervals in omitted histories retain their means

#### Scenario: Location changes or tracking data is purged
- **WHEN** the user changes location or purges tracking data
- **THEN** no mean from the old location or purged tracker remains available

### Requirement: Reject obsolete weather results
A completed request SHALL store a mean only if its endpoint identities, timestamps, consecutive relationship, and location still match the committed tracker. Reading changes, imports, purge, or location changes during a request SHALL NOT allow its obsolete result to restore invalidated temperatures.

#### Scenario: Edit during an in-flight request
- **WHEN** an endpoint's observation time changes before a weather request for its old interval completes
- **THEN** the old request's result is discarded and the current interval stays missing until its own request succeeds

#### Scenario: Change location during a request
- **WHEN** a response for the old location arrives after a location change
- **THEN** it cannot populate temperatures for the new location

#### Scenario: Delete or purge during a request
- **WHEN** a request completes after its endpoint was deleted or the tracker was purged
- **THEN** it cannot recreate a temperature mean for that obsolete pair
