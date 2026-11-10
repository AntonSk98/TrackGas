# Proposal

## Why

Calendar-allocated consumption does not account for actual elapsed observation time, and Year bars also reflect bucket length, making comparison with temperature less useful. The timestamp-normalized daily estimate already available in History can support a comparable chart while preserving access to absolute usage.

## What Changes

- Add an in-card consumption chart mode toggle: estimated daily consumption by default, absolute bucket consumption on demand.
- Reuse actual elapsed-time normalization from consecutive same-installation readings; show daily estimates in Week/Month and covered-date-weighted daily averages per month in Year.
- Give the chart its own mode-aware heading: "Daily consumption" / "Tagesverbrauch" or "Consumption" / "Verbrauch", without "24" in either title.
- Keep the large period total and all consumption list values absolute, as confirmed with the user. Distinguish the large number with "Total consumption" / "Gesamtverbrauch".
- Preserve temperature values, date alignment, navigation, and History estimates. Retain mode during the current session and configuration changes, with daily mode on fresh launch.
- Use a compact, accessible Material 3 control and localized rate units; cover behavior with existing JVM tests and build tooling, without emulator tests.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `gas-consumption-tracking`: Add chart-only daily/absolute modes and their calculation, labeling, inspection, availability, and lifecycle contracts; clarify that calendar totals and rows do not change.

## Impact

- Pure consumption projection and chart presentation in `Consumption.kt`, `ChartProjection.kt`, and `ConsumptionChart.kt`.
- Dashboard card hierarchy and state wiring in `DashboardContent.kt`, `TrackerScreen.kt`, `TrackerViewModel.kt`, and `MainActivity.kt`.
- English/German string resources and existing consumption, chart, localization, and ViewModel JVM tests.
- No storage migration, CSV changes, weather-provider changes, new dependencies, or README expansion.
