# Proposal

## Why

The Dashboard temperature chart currently reuses means calculated across reading timestamps, even when the gas chart is in Daily projection mode. This makes the temperature basis unclear and does not provide daily weather means for comparison with projected daily consumption.

## What Changes

- Keep interval consumption temperatures as the mean between consecutive reading timestamps.
- Give Daily projection its own full-calendar-day temperature means for covered consumption dates, independent of reading times.
- Select the matching temperature data and a concise, localized basis label with the gas chart mode; keep chart points, period mean, and row means consistent.
- Cache completed means by their respective interval or date, fetch only missing weather asynchronously, and invalidate cached data when its location changes.
- Preserve consumption calculations and existing interval-temperature behavior in Interval consumption mode.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `average-temperature`: Maintain and aggregate both interval means and full-day means for covered dates.
- `gas-consumption-tracking`: Show the matching temperature basis and daily means when Daily projection is selected.

## Impact

- Temperature provider, background synchronization, persistence, invalidation, and aggregation.
- Dashboard state, chart projection, temperature labels, and English/German resources.
- Temperature persistence, synchronization, aggregation, chart projection, and localized-resource tests.
