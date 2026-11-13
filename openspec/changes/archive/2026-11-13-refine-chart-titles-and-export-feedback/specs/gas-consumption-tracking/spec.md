# Spec Delta

## MODIFIED Requirements

### Requirement: Chart-only consumption mode selection
Dashboard SHALL offer daily-projection and absolute (interval) consumption chart modes, defaulting to absolute interval consumption in a fresh process. Mode changes SHALL affect only gas chart values, units, heading, and inspection, not absolute period totals, list amounts, History, temperatures, or selected dates. Selection SHALL survive tab changes, period navigation, committed data refreshes, and configuration changes without being stored as tracker data or exported.

#### Scenario: Default and switch
- **WHEN** a fresh process opens Dashboard and the user selects daily-projection mode
- **THEN** the chart initially shows absolute interval amounts and then daily projections while the large period total and all list amounts remain unchanged

#### Scenario: Retain selection in the session
- **WHEN** the user selects daily-projection mode, navigates periods, visits History, rotates, and receives committed reading or temperature updates
- **THEN** daily-projection mode remains selected without resetting the tab or selected period

#### Scenario: Fresh launch
- **WHEN** the app process is terminated and a new process starts
- **THEN** the chart starts in absolute mode with the existing Dashboard and current-week defaults

### Requirement: Localized accessible chart mode control
The gas chart SHALL have a compact switch beside its heading: "Daily projection" / "Tagesprojektion" when enabled and "Interval consumption" / "Intervallverbrauch" when disabled. The switch SHALL expose its label and mode accessibly in English and German. Daily axes SHALL use cubic metres per day; daily tooltips SHALL show only the approximation sign, value, and cubic metres without a per-day suffix. Controls SHALL remain usable on narrow screens and with enlarged text.

#### Scenario: German daily presentation
- **WHEN** German Dashboard shows daily-projection mode
- **THEN** the chart heading is "Tagesprojektion", its axis uses cubic metres per day, its tooltip shows an approximate cubic-metre value without "/Tag", and the adjacent switch is enabled and accessibly identifies daily mode

#### Scenario: German absolute presentation
- **WHEN** German Dashboard shows absolute mode
- **THEN** the chart heading is "Intervallverbrauch" and the switch is disabled and accessibly identifies absolute mode
