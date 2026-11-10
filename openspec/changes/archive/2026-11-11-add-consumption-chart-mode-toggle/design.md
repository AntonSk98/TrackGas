# Design

## Context

See `proposal.md` for motivation and the delta specification for observable behavior.

- `Consumption.kt` already separates `ReadingInterval.cubicMetresPer24Hours` (actual seconds, DECIMAL128) from calendar-day `allocate` / `dashboard` amounts. History reuses that interval property.
- `dashboard(tracker, period)` additionally includes replacement-start opening consumption, which has no observation duration.
- `ChartProjection.kt` currently copies absolute Dashboard buckets into decimal chart entries, scales them for graphics, and aligns temperature points. Its existing contract assumes chart and list amounts are identical.
- `DashboardContent.kt` currently renders the "Consumption" label, large absolute total, both charts, and period navigation in one surface. There is no separate gas-chart heading or mode state.
- `ConsumptionChart.kt` uses cubic-metre strings for gas axis titles and inspection. Its producer and inspection are snapshot-keyed, preventing stale dates or scales during model updates.
- `TrackerViewModel` retains session navigation in `TrackerState`; `TrackerScreen` and `MainActivity` wire actions. Existing JUnit tests cover calculations, chart layout, projection, resources, and state. No emulator testing is requested.

The current calendar-period, minimal-presentation, chart/inspection, timestamp-semantics, and History-freshness requirements need explicit amendments: daily chart amounts will no longer always equal absolute rows, and time-only edits will intentionally refresh daily chart values. Absolute accounting remains unchanged.

## Goals / Non-Goals

**Goals:**

- Keep absolute accounting and normalized rates separate, using shared interval validation and normalization rather than a second arithmetic implementation.
- Make active mode part of chart identity, unit formatting, and UI state so equal numeric values cannot retain stale inspection or units.
- Distinguish the invariant total from mode-specific chart data without adding explanatory clutter.

**Non-Goals:**

- No replacement of calendar allocation with time-overlap integration, interpolation of gas usage, weather changes, statistical correlation calculation, or combined gas/temperature scale.
- No stored rates, durable mode preference, CSV fields, schema changes, new dependencies, or README report.
- No changes to list aggregation, History estimate formatting, chart gestures, or overall period navigation.

## Decisions

### 1. Derive a separate daily-rate projection from validated intervals

Add a small pure daily-rate projection alongside existing consumption arithmetic, accepting the committed tracker and requested period/range. Reuse `readingIntervals(tracker)` and `ReadingInterval.cubicMetresPer24Hours`. Associate each eligible covered date with its interval's rate using the same earlier-date-exclusive/later-date-inclusive ownership as gas and temperature. Clip intervals to the selected range before enumerating dates so work is bounded by the displayed year rather than the age of the tracker.

Week/Month use those daily values directly. Year groups them by month and divides the unrounded sum by the count of eligible dates with DECIMAL128 precision. Do not divide by calendar-month length, sum rates, or average interval rates without date weighting. Opening consumption is absent from this projection, even if it shares a date with an old-meter interval; only the interval's rate contributes on that date.

**Alternative rejected:** Dividing absolute bucket amounts by covered days is simpler but loses actual observation duration and incorrectly treats opening consumption as a daily estimate. Time-weighted weather integration would be a different feature and inconsistent with the existing covered-date temperature aggregation.

### 2. Keep absolute Dashboard amounts immutable and make chart mode explicit

Introduce a typed `ConsumptionChartMode` with daily-estimate and absolute cases. Preserve `Dashboard` and its total/buckets as absolute data. Let the pure chart projection select between existing absolute buckets and separately derived rate buckets, retaining original decimal active-mode values for scaling and inspection.

Prefer naming that does not represent a rate as an absolute volume: use an amount/value field plus explicit mode/unit metadata rather than quietly changing the meaning of `ChartEntry.cubicMetres`. Update tightly coupled chart helpers and tests together. Never derive accounting totals from rate entries or normalized graphic coordinates.

Include mode in `ChartProjection` identity, even when both sets of amounts are equal. Keep the existing snapshot-owned producer/host strategy and reset inspection on mode changes. Compute gas axes, tooltip values, and unit-dependent layout measurements from the same active projection. Reuse the decimal zero/underflow policy and existing number formatting.

Use only the Material switch's native animation; do not crossfade chart panels or render outgoing charts. Replace the gas snapshot immediately on mode changes so its axes, scale, values, and inspection remain consistent. Keep the temperature host mounted using a temperature-only projection whose identity excludes gas values, scale, and mode, while still tracking period and weather changes. This avoids duplicating both chart panels during each toggle and rebuilding the unaffected temperature model.

Temperature means and positions remain sourced from the existing absolute covered buckets and temperature map, independently of gas mode. This preserves current temperature behavior even when daily mode omits opening-only gas data. Avoid an early return that hides the mode control, total, or any otherwise valid temperature chart just because rate entries are empty.

**Alternative rejected:** Replacing `state.dashboard` with rate amounts would accidentally change the large number, weekly/monthly rows, and other consumers. Building a second chart implementation would duplicate gestures, layout, and zero handling.

### 3. Own selection in the retained tracker session

Add a default-daily mode field and a selection action to `TrackerState` / `TrackerViewModel`. The action changes only presentation state, without repository writes or weather requests. Wire it through `MainActivity` and `TrackerScreen` to `dashboardItems`, then into the chart.

Keep selection when copying state for navigation, data mutations, weather refresh, empty periods, and configuration changes. A new ViewModel/process uses daily mode by default, matching the existing session-only tab/period lifecycle. Derive active data from the same committed tracker snapshot; cancelled or failed edits must not leak drafts into either mode.

**Alternative rejected:** State inside a lazy-list item or chart-keyed subtree can reset on scrolling, projection replacement, or tab changes. A persistent preference adds storage and migration scope without a user requirement.

### 4. Use a distinct chart section with a compact inline switch

Inside the existing summary surface, retain this hierarchy:

1. "Total consumption" / "Gesamtverbrauch" and the unchanged large absolute value.
2. One row with the title-medium chart heading and a compact trailing switch: "Daily consumption" / "Tagesverbrauch" when enabled, or "Consumption" / "Verbrauch" when disabled.
3. Gas plot, then the existing temperature summary/chart and period navigation below.

Use a standard Material 3 `Switch` beside the heading rather than a separate row of chips. The heading takes the remaining width and can wrap with enlarged text; the switch retains a minimum 48 dp touch target without scaling hacks. Its accessible label identifies daily consumption, and its localized state identifies daily or absolute mode. Keep theme styling and normal content scrolling.

Use new chart-specific resources rather than repurposing the History `24h` strings. Heading text contains no "24"; daily axis units remain `m³/day` / `m³/Tag`, while daily tooltips show `≈ value m³` without a per-day suffix. Localized accessible chart wording still identifies an estimated daily rate. Absolute axis/inspection use existing volume formatting. Measure the active unit title in the shared chart layout so longer rate units do not unalign date positions between charts.

Hide the chart section with the existing no-consumption state when there is no absolute coverage. When absolute coverage exists but no rate is eligible, retain heading/selector and show concise localized text such as "Daily estimate unavailable" / "Tagesverbrauch nicht verfügbar" instead of an invented rate, leaving absolute total/list and mode switching usable.

**Alternative rejected:** Two labeled chips on a separate row consume too much space. The inline switch instead relies on the adjacent mode-aware heading and explicit accessible state. Reusing the large total's title for the mode would imply that an absolute total was a daily rate.

### 5. Extend existing JVM coverage and resource checks

Add targeted cases to `ConsumptionTest`, `ChartProjectionTest`, `TrackerViewModelTest`, `LocalizedStringResourcesTest`, and the existing layout/format tests where affected. Cover unequal-duration intervals, DST and fractional hours, cross-boundary clipping, weighted monthly averages, partial coverage, zero versus absent rates, independent installations, opening-only/mixed opening dates, time-only edits, mode-aware identity/units, and unchanged absolute and temperature projections.

Use the existing Gradle unit-test runner and debug assembly for implementation validation. No emulator, connected test task, new screenshot framework, or dependency is required. Review composable structure for wrapping, semantic selection, and minimum touch targets; pure layout/resource tests complement this review without claiming emulator-based visual evidence.

## Risks / Trade-offs

- [A daily rate is not a reconstruction of actual per-date usage] -> Reuse interval rates only on existing covered dates, identify approximation in inspection/accessibility, and keep actual period totals prominent.
- [Year chart and list intentionally differ in daily mode] -> Separate headings and explicit rate units identify daily averages versus absolute monthly amounts.
- [Opening consumption has no duration] -> Omit it only from rates; retain absolute mode, total/list values, and an explicit unavailable state.
- [Mode switches with equal values can retain stale units or tooltips] -> Include mode in snapshot identity and format/measurement inputs.
- [More card hierarchy uses vertical space] -> Use existing title styles and a compact wrapping control without adding a separate surface or explanatory paragraphs.
- [No emulator visual evaluation] -> Use existing JVM layout/resource checks, compile the Compose UI, and review responsive/accessibility structure; do not claim device-rendered validation.

## Migration Plan

No persisted data changes are necessary. Deliver pure projections and state/UI wiring together so fresh sessions default to daily mode while absolute consumption remains accessible. Rollback removes the mode UI and projection path and restores the former chart heading without modifying readings, means, or exports.
