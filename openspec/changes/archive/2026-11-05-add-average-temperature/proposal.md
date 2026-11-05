# Proposal

## Why

Gas consumption is driven largely by outdoor temperature, but TrackGas shows consumption in isolation. Showing daily average temperature next to consumption lets users see why usage changes. The just-added device-location approach is unsuitable: the meter is fixed, while the phone may be anywhere (for example, adding a reading while travelling would fetch the wrong climate). The user therefore states the meter's location once, and the app requires it.

## What Changes

- Add a "Meter location" entry to the tracker options menu where the user types a city or address; it is resolved to coordinates (Open-Meteo geocoding) and persisted on-device with a display name.
- When a reading is added, no location is saved, and the device is online, a location dialog opens that the user can close with "Later" (so an unavailable service never blocks the app); it reopens on the next add while no location is saved. While offline it is not shown.
- After a reading is added, edited, deleted, or imported, and after the location is saved or connectivity returns, an asynchronous event checks which days between the earliest and latest reading lack a stored temperature and fetches them from Open-Meteo in batched date-range requests (never one request per day, never blocking the UI). This also backfills temperatures for all pre-existing readings. While offline nothing is fetched; the check runs again when the network becomes available.
- Persist daily temperatures in the local Room database. Temperatures outside the date range of the remaining readings are removed when readings are deleted, edited, imported, or purged.
- The Dashboard chart overlays a temperature line on the consumption bars and refreshes by itself when temperatures arrive. The mean temperature is shown in the tooltip, in each row under the chart, and as the period average below the chart. Year aggregates daily temperatures into monthly means.
- **BREAKING**: Remove the device-location feature (approximate-location permission, rationale dialog, permission state store).
- Add the INTERNET and ACCESS_NETWORK_STATE permissions.

## Capabilities

### New Capabilities
- `meter-location`: user-entered, persisted meter location used as the temperature source.
- `average-temperature`: asynchronous, batched, connectivity-aware, persisted daily temperature retrieval, its lifecycle with readings, and its presentation.

### Modified Capabilities
- `gas-consumption-tracking`: the chart gains a temperature line, and rows and tooltips show mean temperature.
- `location-permission`: all requirements removed (capability retired).

## Impact

- Code: new `temperature` package (provider, geocoding, gap calculation, sync, aggregation, network monitor); `ReadingDatabase` (new table, version 3 migration, pruning in existing mutations); `TrackerViewModel`/`TrackerState`; `ConsumptionChart`, `DashboardContent`, `TrackerScreen`; new location dialog; deletion of `LocationPermission*` files, their test, the location permission, and strings (English and German).
- External: Open-Meteo geocoding and archive APIs (no API key, no new library).
- Privacy: only the entered location text, coordinates, and date ranges are sent; no reading values are transmitted.
