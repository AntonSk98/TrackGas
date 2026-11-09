# Proposal

## Why

Gas consumption between two readings is currently paired with calendar-day weather, even when the readings were taken in the morning and most consumption occurred the previous day. Recording the actual reading time allows weather to match the measurement interval without introducing hourly storage or high-precision interpolation.

## What Changes

- Add an editable observation time to Add/Edit, defaulting new readings to the current time, and retain an unambiguous timestamp with timezone information locally and in CSV.
- Fetch Open-Meteo hourly temperatures for each missing consecutive same-meter interval, average samples within `[earlier timestamp, later timestamp)` in memory, and store only one mean per interval. Discard hourly samples after calculation.
- Re-fetch only intervals whose endpoints or location changed, including intervals split by insertion or merged by deletion; preserve unrelated cached means.
- Keep reading operations immediate and offline-capable. Missing or invalidated temperatures remain absent until a background request succeeds.
- Preserve the existing one-reading-per-calendar-date rule, read-only date in Edit, calendar-day consumption allocation, replacement behavior, and daily/monthly chart structure. Project interval means onto the same covered consumption dates, explicitly treating multi-day projections as approximations.
- **BREAKING**: Replace daily-temperature persistence and the date-only CSV contract. Target fresh installations; do not migrate old tracker data, invent historical reading times, or support old CSV formats.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `gas-consumption-tracking`: Capture, edit, persist, and display observation times; use interval-associated temperatures in existing consumption presentation without changing gas allocation.
- `average-temperature`: Replace daily caching with one durable mean per actual reading interval, calculated from transient hourly data, with targeted invalidation and background retry.
- `reading-data-exchange`: Export/import unambiguous observation timestamps and timezone information instead of date-only readings.

## Impact

- Kotlin reading models, drafts, validation, history, ViewModel state, Room entities/schema/repositories, and their existing tests.
- `TemperatureProvider`, `TemperatureSync`, `TemperatureRepository`, `TemperatureGaps`, and `TemperatureAggregation`; chart and row projections in `ChartProjection` and `DashboardContent`.
- CSV writer/parser, localized English/German time labels and validation, README format and weather explanations.
- Same Open-Meteo archive service and existing networking/coroutine stack; no new service, hourly database, interpolation, thermal-lag model, or dependency is required.
- Archive latency remains possible. Request speed is not guaranteed, and an unavailable temperature must never delay or fail a reading mutation.
