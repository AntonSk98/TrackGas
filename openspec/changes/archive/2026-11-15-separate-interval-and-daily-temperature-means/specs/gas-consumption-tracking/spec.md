# Spec Delta

## MODIFIED Requirements

### Requirement: Chart-only consumption mode selection
Dashboard SHALL offer daily-projection and absolute (interval) consumption chart modes, defaulting to absolute interval consumption in a fresh process. Mode changes SHALL affect gas chart values, units, heading, inspection, and the temperature source used for the chart, period mean, and rows; they SHALL NOT affect absolute period totals, list amounts, History, or selected dates. Selection SHALL survive tab changes, period navigation, committed data refreshes, and configuration changes without being stored as tracker data or exported.

#### Scenario: Default and switch
- **WHEN** a fresh process opens Dashboard and the user selects daily-projection mode
- **THEN** the chart initially shows absolute interval amounts and then daily projections, the temperature presentation uses full-calendar-day means, and the large period total and all list amounts remain unchanged

#### Scenario: Retain selection in the session
- **WHEN** the user selects daily-projection mode, navigates periods, visits History, rotates, and receives committed reading or temperature updates
- **THEN** daily-projection mode remains selected without resetting the tab or selected period

#### Scenario: Fresh launch
- **WHEN** the app process is terminated and a new process starts
- **THEN** the chart starts in absolute mode with the existing Dashboard and current-week defaults

### Requirement: Temperature granularity and gaps
Week and Month SHALL plot the selected mode's temperature mean for each covered consumption date; Year SHALL plot the covered-date-weighted monthly mean. Interval consumption SHALL reuse each reading interval's mean on the dates receiving its gas allocation. Daily projection SHALL use each covered date's full-calendar-day mean. Buckets without temperature SHALL have no point and the line SHALL NOT bridge them.

#### Scenario: Missing temperature day
- **WHEN** a covered date in the middle of the period has no mean for the selected mode
- **THEN** that day has no temperature point and the line is broken there

#### Scenario: Year view
- **WHEN** October has two covered dates with selected-mode temperatures of 8 and 10 degrees Celsius
- **THEN** the Year chart plots October at 9 degrees Celsius

#### Scenario: Multi-day interval
- **WHEN** one reading interval allocates gas to three calendar dates and has a stored mean of 6 degrees Celsius
- **THEN** all three dates use 6 degrees Celsius in Interval consumption

#### Scenario: Multi-day interval in Daily projection
- **WHEN** one reading interval allocates gas to three calendar dates with daily means of 4, 6, and 8 degrees Celsius
- **THEN** those dates use their respective full-day means in Daily projection

### Requirement: Temperature inspection and consumption rows
Long-pressing a consumption bar SHALL show its date and consumption only. Long-pressing a temperature point SHALL show its date and selected-mode temperature only. Each consumption row SHALL show the covered-date-weighted mean for the selected mode beside its consumption when a value exists.

#### Scenario: Inspect consumption
- **WHEN** the user long-presses a consumption bar for a bucket with a temperature
- **THEN** the tooltip shows the date and consumption without temperature

#### Scenario: Inspect temperature
- **WHEN** the user long-presses a temperature point
- **THEN** the tooltip shows the date and selected-mode temperature in degrees Celsius without consumption

#### Scenario: Consumption row shows mean temperature
- **WHEN** a consumption row has selected-mode temperatures for its covered dates
- **THEN** it shows their covered-date-weighted mean temperature beside consumption

### Requirement: Localized accessible chart mode control
The gas chart SHALL have a compact switch beside its heading: "Daily projection" / "Tagesprojektion" when enabled and "Interval consumption" / "Intervallverbrauch" when disabled. The switch SHALL expose its label and mode accessibly in English and German. Daily axes SHALL use cubic metres per day; daily tooltips SHALL show only the approximation sign, value, and cubic metres without a per-day suffix. Controls SHALL remain usable on narrow screens and with enlarged text.

#### Scenario: German daily presentation
- **WHEN** German Dashboard shows daily-projection mode
- **THEN** the chart heading is "Tagesprojektion", its axis uses cubic metres per day, its tooltip shows an approximate cubic-metre value without "/Tag", and the adjacent switch is enabled and accessibly identifies daily mode

#### Scenario: German absolute presentation
- **WHEN** German Dashboard shows absolute mode
- **THEN** the chart heading is "Intervallverbrauch" and the switch is disabled and accessibly identifies absolute mode

#### Scenario: Absolute presentation
- **WHEN** the user chooses absolute mode
- **THEN** the heading becomes "Interval consumption" or "Intervallverbrauch", the axis and tooltip use cubic metres rather than a rate, and the selector exposes absolute mode

#### Scenario: Separate the total from the chart
- **WHEN** either chart mode is displayed
- **THEN** the large absolute period number remains above the separate mode-aware chart section under "Total consumption" / "Gesamtverbrauch"

#### Scenario: Compact accessible layout
- **WHEN** the display is narrow or text is enlarged
- **THEN** the heading and compact switch share one row, with the heading wrapping if necessary, without clipping, overlapping the total, relying only on color, or reducing the switch touch target below 48 dp

#### Scenario: Smooth mode transition
- **WHEN** the user switches chart modes, including rapidly switching back
- **THEN** the switch uses its native animation, the gas chart immediately shows the selected mode with matching units and scale, and the temperature chart updates to the matching mean source without an outgoing chart overlay

## ADDED Requirements

### Requirement: Temperature basis label
The Dashboard SHALL label the temperature chart with a concise, localized description of the selected temperature basis: a mean between readings in Interval consumption and a daily mean in Daily projection. The label SHALL be available to assistive technology and SHALL remain associated with the temperature presentation.

#### Scenario: English interval label
- **WHEN** the app language is English and Interval consumption is selected
- **THEN** the temperature presentation identifies its value as a mean between readings

#### Scenario: German interval label
- **WHEN** the app language is German and Interval consumption is selected
- **THEN** the temperature presentation identifies its value as a mean between readings in German

#### Scenario: Daily label
- **WHEN** Daily projection is selected
- **THEN** the temperature presentation identifies its values as daily means in the active language
