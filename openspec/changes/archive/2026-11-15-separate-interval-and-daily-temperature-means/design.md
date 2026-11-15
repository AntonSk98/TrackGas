# Design

## Context

See `proposal.md` for motivation and the spec deltas for required behavior.

- `TemperatureSync` currently fetches hourly samples, calculates one mean per consecutive reading interval, and stores `IntervalTemperatureEntity` rows.
- `TemperatureAggregation` projects interval means onto consumption dates. `TrackerViewModel` exposes one date-keyed map, which all Dashboard temperature displays currently share regardless of chart mode.
- `RoomReadingRepository` prunes obsolete interval means transactionally after reading mutations. The Room schema is version 6; the database builder currently registers no migrations.
- The provider uses Open-Meteo archive hourly data in UTC. Meter readings retain their recorded local dates and observation times.

## Goals / Non-Goals

**Goals:**
- Keep the existing timestamp-window mean for Interval consumption and introduce a distinct full-local-day mean for Daily projection.
- Store only completed means, reuse cached date means across reading intervals, and fetch only missing values without delaying reading operations.
- Feed the selected source consistently to the temperature line, its period mean, rows, and inspection.
- Preserve existing interval means during an additive database migration.

**Non-Goals:**
- Change gas consumption, daily rate calculations, interval-temperature semantics, reading CSV formats, or weather location selection.
- Persist hourly weather samples or make chart selection wait for a fetch.

## Decisions

### 1. Keep interval means and daily means as separate cached data

Retain `IntervalTemperatureEntity`, whose identity and validity depend on the exact reading pair, timestamps, installation, and location. Add a date-keyed daily-mean entity scoped to the saved location. A date row represents one complete local calendar day at that location and is shared by all Dashboard projections; it is not duplicated for each reading interval.

Keep both entity types behind the temperature repository/coordinator boundary, and expose a snapshot containing both caches. Extend location-change clearing to both. In reading-mutation transactions, preserve daily rows for dates that remain covered and remove rows for dates no longer covered; newly covered dates remain missing until the asynchronous check fills them. Purge clears both caches.

An alternative was to derive daily values by splitting the interval mean or its hourly samples. That cannot produce a full-day mean when reading timestamps cover only part of the day and would preserve the ambiguity this change addresses.

### 2. Use the provider's daily mean separately from interval sampling

Keep the existing hourly request and `[earlier timestamp, later timestamp)` calculation unchanged for interval means. Add a provider operation for Open-Meteo's daily mean field using its location-local calendar dates. Request only dates covered by gas consumption that lack a valid daily row; batch contiguous missing dates where possible. Do not infer a full-day value from partial interval samples or persist raw weather.

A check fills both kinds of gaps asynchronously. A simple new one-day interval may therefore need an hourly interval fetch and a daily-date fetch; either fetch can be skipped when its data is already cached, and a multi-date daily range can be batched. Long hourly intervals may still require multiple bounded requests. Reading saves and other mutations never wait for these requests.

### 3. Preserve source-specific projections through the Dashboard

Keep interval and daily temperature maps distinct in ViewModel state. Select the appropriate map from `ConsumptionChartMode` when building the chart projection, period mean, and rows, so no renderer silently averages mixed sources. Reuse covered-date-weighted aggregation and existing missing-point segmentation. Give the temperature presentation a localized short basis label: “Mean between readings” / “Mittel zwischen Ablesungen” in absolute mode, and “Daily mean” / “Tagesmittel” in daily mode. Keep the current independent temperature scale and tooltip value formatting.

An alternative was to change only the text while leaving the values unchanged. That would mislabel Daily projection and would not satisfy its full-day semantics.

### 4. Add a Room migration without replacing the interval cache

Increment the database version and add only the daily-mean table through a `6 -> 7` migration. Preserve all readings and interval-temperature rows; do not backfill daily means from interval means. Export the version 7 schema and register the migration in `TrackGasApplication`. Add migration coverage for retaining existing rows and creating the empty daily cache.

### 5. Verify the two data paths and mode selection independently

Add focused provider tests for daily-date parsing, location-local date ranges, missing values, and interval-window behavior remaining unchanged. Extend sync/repository tests for missing-only daily fetching, offline retry, location changes, reading coverage edits, purge, and obsolete responses. Test mode-specific chart/row/period aggregation, localized basis labels, and the Room migration with existing host-side and Android test tooling.

## Risks / Trade-offs

- **Daily projection needs a second kind of weather data** → Fetch only missing means asynchronously, batch dates, and keep consumption available when weather is absent.
- **Historical daily weather may be unavailable or incomplete** → Leave that date without a daily mean and retain the separate interval mean for Interval consumption.
- **Reading edits can change date coverage while requests are in flight** → Guard publication by current location and covered-date state; prune stale date rows transactionally.
- **Schema changes can disrupt existing installs** → Use an additive migration that preserves all version 6 data and validate the exported schema.

## Migration Plan

Ship database version 7 with the new date-mean table and a `6 -> 7` migration. Existing interval means remain usable immediately; daily means begin empty and are fetched by the existing background-check lifecycle. If the feature is rolled back, do not delete user data or attempt to rewrite interval means; older app versions may not open a newer Room schema.
