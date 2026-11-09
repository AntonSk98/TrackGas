# Design

## Context

See `proposal.md` for motivation and the three delta specs for behavior.

The current implementation is date-based throughout:

- `tracking/Reading.kt` stores `LocalDate`; `ReadingInput`, `ReadingDraft`, and `ReadingForm` have no observation time. Edit keeps the date read-only.
- `tracking/Consumption.kt` allocates `(earlier.date, later.date]` by calendar days. `ChartProjection` and `DashboardContent` consume daily temperature maps.
- `temperature/TemperatureProvider.kt` requests Open-Meteo archive `daily=temperature_2m_mean` with `timezone=auto`.
- `TemperatureRepository` persists `DailyTemperatureEntity` keyed by date. `TemperatureSync` requests missing date ranges, with connectivity-triggered background checks.
- `data/RoomReadingRepository.kt` prunes temperatures outside the date range inside mutation transactions. That cannot invalidate an edited timestamp or split interval.
- `ReadingDatabase` currently has schema version 5. The Room builder registers no migrations; schema JSON is exported through KSP.
- CSV parser/writer and README document `meter_id;date;meter_value;note`.
- Meter location lives in private preferences, outside Room. The ViewModel currently saves it before requesting an asynchronous cache clear; that order needs stronger visibility and in-flight-result guards.

Existing focused tests include `ReadingValidationTest`, `ReadingPolicyTest`, `ReadingInputTest`, `ReadingEntityMappingTest`, `ConsumptionTest`, the CSV tests, `TemperatureProviderTest`, `TemperatureSyncTest`, `TemperatureAggregationTest`, and `ChartProjectionTest`.

## Goals / Non-Goals

**Goals:** Keep the existing consumption model and screens; add only the time information and interval-weather cache needed to align measurements. Store one derived value per interval with enough provenance to reject stale data. Reuse existing Room, coroutine, HTTP, localized form, and test patterns.

**Non-Goals:** Hourly persistence, interpolation, partial-hour weighting, hourly gas allocation, same-day multiple readings, editable reading dates, configurable timezone UI, new weather services, guaranteed request latency, automatic data destruction, or migration of existing installations/CSV.

## Decisions

### 1. Add minute-resolution timestamps without replacing calendar-date rules

Retain the recorded `LocalDate` used by installation routing, uniqueness, and consumption allocation. Add an absolute observation instant and its recording `ZoneId`; derive and validate the local date/time from these together. Persist the instant as a numeric epoch value and the zone ID as text, alongside the date needed by existing queries. Enforce consistency during decoding and final transactional validation rather than allowing independent date and instant values to drift.

Add a time control to Add/Edit. Capture the current device zone and current date/time, truncated to minutes, once when Add opens. Date changes retain the selected clock time. Edit loads the saved time and zone, keeps date/identity/installation fixed, and validates time against current neighbours. Extend the existing draft saver so rotation or operation retries do not reset time. History displays the recorded local date/time, not a conversion to the device's later timezone.

Resolve local input with Java time zone rules: reject gaps, choose the earlier offset in overlaps, and reject future instants or non-increasing neighbour instants. Imported explicit offsets may select either valid occurrence of an overlap; preserve that instant if its offset matches the supplied zone. An unchanged edited timestamp must round-trip without being re-resolved to a different overlap occurrence.

**Alternative rejected:** Replacing date-based allocation with duration-based daily allocation would change gas totals at calendar boundaries and require a much broader redesign. UTC-only display would also shift existing date-based history when travelling.

### 2. Fetch hourly data, calculate one simple mean, discard samples

Continue using `https://archive-api.open-meteo.com/v1/archive`. Request `hourly=temperature_2m`, `timezone=UTC`, and `timeformat=unixtime`, using `start_date`/`end_date` covering the endpoint instants in UTC. The API returns hourly samples; it does not calculate the custom interval mean for us.

For interval `[start, end)`, eligible timestamps are the UTC whole-hour instants at or after start and strictly before end. Sum the eligible Celsius values and divide by their count using the existing decimal arithmetic conventions. Do not round individual values, interpolate, or weight partial hours; round only for presentation.

Examples in Europe/Berlin in November:

- 06 Nov 08:00 to 07 Nov 08:00 includes 08:00 through the following 07:00.
- 06 Nov 08:15 to 07 Nov 08:15 includes 09:00 through the following 08:00.

Validate response alignment, timestamps, duplicates, and numeric values. Require all expected timestamps inside the window to have a value before committing the mean; missing data outside the window is irrelevant. Empty windows, possible for a short interval across midnight, remain without temperature and are excluded from requests.

Process one missing interval at a time. For long intervals, request at most 31 UTC calendar days per chunk, retaining only an in-memory running sum/count and coverage information until that interval is complete. A failed chunk leaves the entire mean missing; successful hourly chunks are not cached to disk. Adjacent intervals may fetch the same calendar date; accept that duplication rather than adding an hourly cache or cross-interval batch planner.

**Alternative rejected:** Persisting all hourly values improves offline recalculation but is unnecessary for the user's preferred storage model. Storing only a mean without checking completeness could permanently cache a biased value for a recent, partially available interval.

### 3. Cache by actual endpoint pair and provenance

Replace `daily_temperatures` with an interval-mean table. Each row has the installation ID, earlier/later reading IDs, their absolute timestamps, the saved location fingerprint, and one decimal mean. The endpoint pair is unique; timestamps and location identify the inputs under which that mean is valid. There is no hourly table and no persisted daily projection.

Derive eligible consecutive pairs from validated committed tracker data, never by pairing readings across installations. Replace date-gap scanning with lookup of missing valid pairs. Completed pairs need no network requests. Continue existing save/delete/import/location/connectivity triggers and explicitly request a check on startup so missing results can recover after process death. Use the existing conflated worker and sequential requests rather than adding a scheduler.

In the same Room transaction as a reading mutation, remove rows that no longer match current consecutive endpoint IDs and timestamps. Leave all other rows intact. A note or value edit does not alter weather inputs, so its means survive. Import currently regenerates reading IDs for included installations, making those means obsolete, while omitted installations retain their rows. Purge clears all means.

**Alternative rejected:** A row keyed only by the later date cannot distinguish an interval split, timestamp edit, or independently imported history. Invalidating every mean on every change wastes requests.

### 4. Guard publication against concurrent edits and location changes

Never hold database transactions or coordinator locks during HTTP requests. Capture immutable endpoints, timestamps, location, and the current location generation before fetching. On completion, a guarded Room operation rechecks endpoint existence, timestamps, installation, and adjacency in the same transaction that writes the mean.

Route location saves and weather-result publication through a small shared coordinator in `TemperatureServices`, rather than retaining the ViewModel's separate preference write plus later cache clear. Serialize short location-change and publication sections; increment the generation for a changed saved location, invalidate visible means, and clear old cache rows before treating refresh as complete. The result commit must match both location and generation. Reading transactions remain protected by the Room endpoint/adjacency recheck.

Because preferences and Room do not share a transaction, also persist the location fingerprint on cache rows and filter observed means against the currently saved location. A process stopping between preference save and cache deletion must not show old-location weather after restart. Same-process generation checks cover in-flight responses, including an A-to-B-to-A location sequence. Surface location-save/storage failures through existing failure handling; do not claim a refresh succeeded if invalidation failed.

Filter displayed means against the current tracker snapshot as well as location. Separate Room observations must not briefly display a mean belonging to an older endpoint pair after a new tracker snapshot is accepted.

**Alternative rejected:** Cancellation alone cannot guarantee an already completed response will not commit. Checking only once before HTTP or doing an unchecked upsert leaves stale-result races.

### 5. Preserve chart structure through a derived daily projection

Reuse each valid interval mean on the same calendar dates receiving that interval's gas allocation: earlier date excluded, later date included. Build this projection in memory from intervals plus stored means. Thus the bar labelled 07 Nov can show the temperature measured across 06 Nov 08:00 to 07 Nov 08:00, instead of weather for the 07 Nov calendar day.

Continue passing a daily projected map into chart and row aggregation, but distinguish its meaning in naming and documentation. Multi-day intervals repeat their one mean across their allocated dates; they cannot reveal actual weather differences inside the interval. Clip projected dates to each requested bucket and average known values by covered-date count. Do not average interval means equally regardless of interval length, and do not weight by gas consumption.

Missing means produce no points or text; preserve the existing no-bridging line behavior and automatic refresh without resetting navigation. Opening consumption on a replacement start date has no genuine earlier timestamp and creates no weather interval. If that date also has an actual interval from another installation, only that genuine interval supplies a projected temperature; never invent weather for opening usage.

README must explain that calendar charts are approximate interval-mean projections, especially when an interval spans a week/month boundary. No new interpolation, temporal clipping of weather within an interval, or detailed allocation UI is added.

**Alternative rejected:** Fetching daily weather separately for charts would reintroduce the mismatch and a second cache. Redesigning all charts around irregular timestamp intervals is broader than needed.

### 6. Break the CSV contract explicitly

Use exactly `meter_id;timestamp;timezone;meter_value;note`. Export the recorded local timestamp with explicit offset at minute precision, for example `2026-11-06T08:15+01:00`, and its zone ID, for example `Europe/Berlin`. This preserves both the observation instant and the timezone needed for later time edits.

Validate timestamps and zone IDs; check that the offset is valid for the local time in that zone. Reject seconds, missing offsets, nonexistent times, future instants, and invalid histories using localized timestamp/timezone field errors. Preserve existing UUID restrictions, atomic preview/commit, notes, decimal precision, and last-row-wins per installation and recorded local date. Export remains ordered by recorded local date, then installation start order.

Reject both old three-column and four-column date-only formats. Export no weather cache: imported included histories fetch new interval means asynchronously.

**Alternative rejected:** Assigning midnight or the import time to old rows would fabricate measurement times. An offset alone does not retain zone rules for later edits.

## Risks / Trade-offs

- Refetching requires connectivity and provider availability -> readings commit immediately; invalid temperatures disappear and missing means retry on later events/startup. Do not promise instant requests.
- Archive data can lag recent readings by several days -> no partial mean is finalized and no forecast fallback is introduced.
- One request sequence per missing interval can be slower for a large history -> sequential bounded chunks avoid request bursts and unbounded memory; retain completed interval means across restarts.
- Fractional hours and multi-day daily projections are approximate -> deterministic sample selection and documented limitations, rather than extra storage or interpolation.
- New timestamp and old date semantics coexist -> centralize construction/decoding validation and retain regression coverage for gas allocation, replacements, and timezone changes.
- Concurrent mutations and separate location storage can expose stale values -> endpoint guards, location generation, persisted provenance, and snapshot-filtered display.

## Migration Plan

1. Implement only for a fresh tracker installation. Bump Room schema and export its new schema JSON, replacing daily temperature storage. Do not add a date-only migration, automatic destructive fallback, or historical timestamp defaults.
2. Document uninstall/reinstall or explicit app-data clearing as the prerequisite for an existing installation. Do not erase user data automatically on an unsupported database version.
3. Ship only the timestamp CSV format. Update README examples and rejection messages together with parser/writer changes.
4. Rollback to an older application build requires another explicit fresh installation; the new database and timestamp CSV are not compatible with old builds.
