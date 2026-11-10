# Design

## Context

See `proposal.md` for motivation. `Reading` already stores minute-precision `observedAt` instants and a recording zone. `ReadingInterval` and `readingIntervals(tracker)` in `Consumption.kt` already validate and pair consecutive observations within installations. Its `days` property intentionally counts calendar dates for Dashboard allocation; it is not elapsed duration.

`ReadingHistory.kt` receives the committed tracker, sorts rows newest first, and renders a date/time followed by a wrapping `FlowRow` containing the raw value and optional meter number. Notes are separate. `numberText` in `ReadingFormat.kt` preserves raw precision on request and otherwise rounds calculated values HALF_UP to at most one decimal. English/German resources and JVM JUnit tests already exist.

The existing tracking spec prohibits consumption in History and restricts observation-time effects to weather. The delta explicitly narrows those restrictions for the new History estimate while retaining calendar allocation.

## Goals / Non-Goals

**Goals:**
- Keep time normalization a pure, independently testable domain calculation.
- Reuse existing validation, pairing, number formatting, committed data flow, and wrapping layout.
- Avoid duplicate mutable state or per-row scans of all readings.

**Non-Goals:**
- No change to `days`, `allocate`, Dashboard totals/charts, weather windows, or one-reading-per-installation-date validation.
- No forecasting the next actual day, interpolated meter readings, synthetic replacement baselines, or daily series.
- No persistence, CSV fields, new dependencies, configuration toggles, or emulator/instrumentation test setup.

## Decisions

### 1. Derive a decimal 24-hour amount from a validated interval

Add a small pure property/helper on `ReadingInterval` in `Consumption.kt`. Use `Duration.between(earlier.observedAt, later.observedAt)` and calculate `cubicMetres * 86400 / elapsedSeconds` with `BigDecimal` and `MathContext.DECIMAL128`. Stored instants are minute-aligned, so elapsed seconds retain all supported precision. Do not truncate to hours, convert through `Double`, or round the result to display precision.

Use recorded observation instants rather than save time, current device zone, or subtraction of local date/times. Existing collection validation guarantees positive duration; invalid data must retain existing explicit invariant/stored-data failure behavior, not become zero estimates or be swallowed by a catch.

Alternative: changing `ReadingInterval.days` or the allocation formula would alter Dashboard semantics and existing regression guarantees. Calendar allocation and elapsed-time normalization remain separate derived views.

### 2. Associate each estimate with its later reading

At the History projection boundary, call `readingIntervals(tracker)` once and build a map keyed by each interval's `later.id`. Render lookup results in the existing rows, using absence only for a genuine installation baseline. Do not pair the descending visual list directly; it can contain separate meters on the same date.

Build from the complete committed tracker, not selected-period data or only visible rows. Rebuild when that snapshot changes; if memoization is introduced at a composable caller, key it by the complete tracker, including timestamps and installations. No repository/ViewModel estimate cache or new mutable UI state is needed. This naturally incorporates committed imports, edits, deletions, restart loads, and purges.

Alternative: searching for a predecessor inside each lazy row duplicates pairing rules and produces quadratic work. Persisting a projection creates unnecessary invalidation and migration work.

### 3. Extend the existing value row, not the history structure

Keep the raw reading primary. Add compact secondary text after the existing value/meter metadata within the `FlowRow`, using `bodySmall` and `onSurfaceVariant`. Render an approximation marker with the localized value and a cubic-metres-per-24-hours unit (conceptually `~8 m3 / 24 h`, with the existing proper cubic-metre typography in resources). No dedicated card, heading, explanation, placeholder, or always-reserved third row.

Reuse `numberText(estimate, locale)` for the established calculated-value rounding. Add matching English/German visible and accessibility strings; the accessible wording explicitly identifies estimated 24-hour consumption rather than relying on the symbol alone. Apply that description to the estimate text without replacing parent edit/delete semantics. Keep number formatting outside the arithmetic helper.

The existing flow layout permits wrapping when text size or width requires it. Preserve raw value precision, recorded date/time, meter number, note, stable row keys, and edit/delete gestures.

Alternative: a new fixed row adds unnecessary height to every entry; an unlabeled bare number is ambiguous and can be mistaken for an actual meter observation.

### 4. Validate with existing host-side tooling

Add focused JVM tests in `ConsumptionTest.kt` (or a small adjacent test class if clearer) for 39-hour, sub-24-hour, exact-24-hour, partial-hour, zero, repeating-decimal, DST, and different-zone intervals. Exercise baseline omission and the later-ID projection using shuffled readings, multiple installations, middle insertion/deletion, and time/value edits. If the ID mapping is extracted into a helper, keep it pure and cover it directly rather than adding a UI framework.

Reuse `ReadingFormatTest` for decimal presentation and `LocalizedStringResourcesTest` for resource parity. Run existing consumption/Dashboard and observation-time regressions to protect the unchanged calendar behavior. Compile Compose and Android resources with the existing debug build. No emulator tests or new test tools.

## Risks / Trade-offs

- A normalized rate can be mistaken for measured daily usage or a future-day forecast -> use an approximation marker, explicit per-24-hours unit, and accessible estimate wording.
- Short intervals amplify small meter differences -> intentionally normalize every valid interval, as confirmed by the user; do not introduce a hidden threshold.
- Display rounding can hide very small positive usage -> retain the app's established one-decimal calculated-value presentation while keeping unrounded arithmetic.
- DST and zone changes make calendar hours misleading -> use absolute instants and include host-side transition cases.
- Separate meters can appear adjacent in History -> reuse installation-local interval pairing and key by reading identity.

## Migration Plan

Ship as an additive History presentation change. Existing timestamped readings produce estimates on load, offline, without backfill writes, network requests, schema migration, or CSV changes. Rollback removes the derived helper/UI/resources without transforming user data.
