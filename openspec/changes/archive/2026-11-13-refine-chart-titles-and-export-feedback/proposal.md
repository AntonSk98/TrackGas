# Proposal

## Why

Four rough edges remain after the chart mode toggle: the export confirmation is an oversized dialog with an unnecessary icon; chart titles "Verbrauch" / "Tagesverbrauch" are vague; the chart opens in the daily-projection mode rather than the measured one.

## What Changes

- Export success shows a compact, text-only confirmation ("Saved" / "Gespeichert") instead of a full-width icon dialog.
- Rename chart headings: absolute mode "Interval consumption" / "Intervallverbrauch"; daily mode "Daily projection" / "Tagesprojektion" (updating the accessible switch label and daily-estimate wording accordingly).
- Default chart mode is absolute (Intervallverbrauch) in a fresh session.
- Out of scope (deferred by decision): how Daily projection treats meter-replacement opening usage, opening/baseline semantics for first readings, and showing a chart after a single reading.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `gas-consumption-tracking`: default mode and chart titles.
- `reading-data-exchange`: export success presentation.
