# Design

## Context

Readings live in Room (`ReadingDatabase` v2) behind `ReadingRepository`, which emits committed snapshots; `TrackerViewModel` projects them into `Dashboard` buckets and `ChartProjection`, rendered by a Vico column chart. The device-location feature is retired by this change. See proposal.md for motivation and the specs for behavior.

## Goals / Non-Goals

**Goals:**
- Batched, async, connectivity-aware, resumable temperature backfill that never blocks or fails tracking.
- Chart line, row, and tooltip presentation without touching consumption calculations.
- Small single-purpose units behind interfaces so each can be unit-tested with fakes.

**Non-Goals:**
- Temperatures in CSV export/import or History rows; forecasts, hourly data, other units, multiple locations.
- Emulator/instrumented tests (unit tests only).

## Decisions

1. **Package `com.trackgas.app.temperature`** holds all new logic; `tracking` only consumes it. Units: `MeterLocation` + `MeterLocationStore`, `LocationResolver` (interface) + `OpenMeteoGeocoder`, `TemperatureProvider` (interface) + `OpenMeteoTemperatureProvider`, `TemperatureGaps` (pure), `TemperatureSync` (coordinator), `NetworkMonitor` (interface) + `ConnectivityNetworkMonitor`, `TemperatureAggregation` (pure). The ViewModel and UI know none of the HTTP details.
2. **User-entered location.** Options-menu dialog â†’ Open-Meteo geocoding (`count=1`) â†’ name, latitude, longitude saved in SharedPreferences (replacing the retired permission store). After a committed *add*, if no location is saved and `NetworkMonitor.isOnline()`, the ViewModel sets a `LocationDialog.Requested` state whose dismiss, outside tap, and back all map to "Later" (no persistence; the next add asks again); offline, nothing is shown and the next add asks again. The dialog therefore never depends on connectivity it lacks. Alternatives: raw coordinates (poor UX); device location (wrong when travelling, rejected).
3. **Provider:** Open-Meteo archive API, `daily=temperature_2m_mean`, `start_date`/`end_date`, `timezone=auto`; one request per contiguous missing range, split into chunks of â‰¤ 366 days. Null values are skipped so recent not-yet-archived days remain missing and are retried. HTTP via `HttpURLConnection`, JSON via `org.json` (add `testImplementation("org.json:json")` if the Android stub is insufficient); parsing is a pure function tested with JSON strings. No new runtime dependency.
4. **Storage:** Room entity `daily_temperatures(date TEXT PK, celsius TEXT)`, database v3 with `MIGRATION_2_3`, schema exported; decimal text like readings. A separate `TemperatureRepository` interface (`observe(): Flow<Map<LocalDate, BigDecimal>>`, `storedDates()`, `upsert()`) keeps `ReadingRepository` unchanged, backed by a `TemperatureDao` in the same database.
5. **Pruning in the same transaction.** Reading delete, save-edit, CSV import, and purge already run in Room transactions in `RoomReadingRepository`; each additionally runs one DAO statement `DELETE FROM daily_temperatures WHERE date < (SELECT MIN(date) FROM readings) OR date > (SELECT MAX(date) FROM readings)` (purge: delete all). Temperatures are per day, not per reading; middle deletions keep the day because it is still inside the covered range and would otherwise be refetched. Not keyed by location, so a location change keeps old values and fills gaps only.
6. **Event + listener:** `TemperatureSync` owns a conflating `MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)` and a collector on the IO dispatcher in application scope. It exposes `requestCheck()`. Emitters: the ViewModel after committed save/delete/import and after a location save; the `NetworkMonitor` callback when a network becomes available; `TrackGasApplication` once at start. Each check: return if offline; return if no location; read min/max reading dates, return if < 2 readings; compute `missingRanges(range, storedDates)` (pure); fetch each range; upsert. Provider errors are swallowed (cancellation rethrown). Idempotent, so duplicate triggers are harmless. Chosen over WorkManager to avoid a dependency.
7. **Connectivity:** `ConnectivityNetworkMonitor` wraps `ConnectivityManager.registerDefaultNetworkCallback` (needs `ACCESS_NETWORK_STATE`), exposing `isOnline()` and a callback fired on `onAvailable`. Offline checks do nothing; `onAvailable` triggers a new check.
8. **Reactive presentation:** the ViewModel collects `TemperatureRepository.observe()` into `TrackerState.temperatures`, so a background insert or prune automatically recomputes projections and the chart (already re-keyed per projection) without user action. `TemperatureAggregation.mean(days)` is the only averaging function, used for Year points, weekly/monthly rows, the period average, and the tooltip, guaranteeing equal values everywhere. `ChartProjection` gains an optional temperature series (own normalization; bars untouched); Vico adds a `LineCartesianLayer` with an end `VerticalAxis`, with gaps preserved by splitting the series at missing points. `DashboardContent` shows the period mean under the chart and a mean per row.
9. **Removal of device location:** delete `LocationPermission*` sources and test, the `ACCESS_COARSE_LOCATION` permission, and the related en/de strings.
10. **Localization:** all new strings in `values` and `values-de`; `LocalizedStringResourcesTest` must pass.

## Risks / Trade-offs

- [Archive lags a few days] â†’ null days stay missing and retry on later triggers.
- [Provider outage] â†’ silent; tracking unaffected; retried by later triggers.
- [No location while offline] â†’ the dialog is skipped; temperatures start once a later online add (or the options menu) provides a location. Connectivity can drop between the check and resolving; the dialog then shows a retryable error and the reading is already saved.
- [Old temperatures from a previous location persist] â†’ deliberate user decision, documented in spec.
- [Geocoding ambiguity (first match)] â†’ the dialog shows the resolved display name (name, region, country) for verification.
- [Chart gaps in Vico] â†’ verified via projection unit tests; rendering is checked in review, no emulator tests.

## Migration Plan

`MIGRATION_2_3` creates the table; no data transformation. Existing readings are backfilled by the first check after the user saves a location (forced on the next added reading). Forward-only schema, as with v2.
