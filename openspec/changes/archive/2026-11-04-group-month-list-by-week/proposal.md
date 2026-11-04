# Proposal

## Why

In the Month view the numeric list under the chart shows one row per covered day, so a fully covered month renders 28–31 rows. This is noisy and pushes useful content off screen. Grouping the list by calendar week gives a compact (4–6 rows) summary, while the chart above still shows day-level detail.

## What Changes

- Month view: the numeric list under the chart shows one row per calendar week (Monday–Sunday, clipped to the month) instead of one row per day. Each row shows the week's date range and the summed consumption of its covered days.
- Weeks with no covered days are omitted; a partially covered week is summed over its covered days only.
- Week and Year views keep their current rows (daily and monthly respectively).
- The Month chart and its long-press tooltip stay daily; the Consumption total is unchanged.
- **BREAKING (spec-level)**: the requirement that every covered daily value is available in the list no longer holds for Month; daily values remain available via the chart tooltip.

## Capabilities

### New Capabilities

### Modified Capabilities
- `gas-consumption-tracking`: "Covered consumption rows", "Minimal consumption presentation" and "Period consumption bar chart" are updated so the Month list is weekly-grouped.

## Impact

- `tracking/DashboardContent.kt` (list rendering, label text), likely a small pure grouping function alongside `ChartProjection.kt`/`Consumption.kt`, string resources for week range labels, and unit tests (e.g. `DashboardTest`, `ChartProjectionTest`).
- No data model, storage, or interval arithmetic changes.
