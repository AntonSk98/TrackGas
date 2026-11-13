# Design

## Context

- `TrackerScreen.kt` renders export success in a `ResponsiveDialog` containing a 48 dp `ic_success` icon in a full-width box; failure uses `ResponsiveAlertDialog`.
- `TrackerState.chartMode` defaults to `DAILY_ESTIMATE`; titles come from `chart_daily_title` / `chart_absolute_title`.

## Decisions

1. **Export success**: keep a dismissible dialog but size it to its content with only the localized `export_success` text, no icon, no button; remove the `ic_success` drawable and the `export-success-icon` tag if unused elsewhere.
2. **Titles**: change string values only (`chart_absolute_title` → Interval consumption/Intervallverbrauch, `chart_daily_title` → Daily projection/Tagesprojektion), plus daily description/unavailable strings that say "daily consumption" (German: "Tagesprojektion"). Du-form is unaffected (no address in these strings).
3. **Default mode**: `TrackerState.chartMode` defaults to `ABSOLUTE`.
