# Spec Delta

## ADDED Requirements

### Requirement: Separate temperature chart
When temperatures are stored for the selected period, the Dashboard SHALL show a temperature line chart below the consumption bar chart. The charts SHALL use aligned date positions and independent value scales so temperature is not visually compared with gas consumption. Temperature points SHALL appear only for dates or months that have both a consumption bar and a stored temperature.

#### Scenario: Separate charts share dates
- **WHEN** a Week with consumption bars and stored temperatures is shown
- **THEN** the temperature line chart appears below the consumption chart with points aligned to the matching bars and its own temperature scale

#### Scenario: No temperature data
- **WHEN** no temperature is stored for the period
- **THEN** only the consumption chart is shown

### Requirement: Temperature granularity and gaps
Week and Month SHALL plot daily mean temperatures for covered consumption buckets; Year SHALL plot each covered month's mean temperature. Days or months without a temperature SHALL have no point and the line SHALL NOT bridge them.

#### Scenario: Missing temperature day
- **WHEN** one day in the middle of the period has no stored temperature
- **THEN** that day has no point and the line is broken there

#### Scenario: Year view
- **WHEN** October has stored daily temperatures of 8 and 10 °C
- **THEN** the Year chart plots October at 9 °C

### Requirement: Temperature inspection and consumption rows
Long-pressing a consumption bar SHALL show its date and consumption only. Long-pressing a temperature point SHALL show its date and temperature only. Each consumption row SHALL show the bucket's mean temperature beside its consumption when a value exists.

#### Scenario: Inspect consumption
- **WHEN** the user long-presses a consumption bar for a bucket with a temperature
- **THEN** the tooltip shows the date and consumption without temperature

#### Scenario: Inspect temperature
- **WHEN** the user long-presses a temperature point
- **THEN** the tooltip shows the date and temperature in °C without consumption

#### Scenario: Consumption row shows mean temperature
- **WHEN** a consumption row has stored temperatures for its date range
- **THEN** it shows their mean temperature beside consumption
