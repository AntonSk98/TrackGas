# Spec Delta

## MODIFIED Requirements

### Requirement: Responsive initial dashboard projection
During startup or selected-period changes, the app SHALL keep the interface responsive while preparing the complete data set needed to project the active period. Dashboard SHALL display chart data only after projection from the complete, current set of relevant readings; loading the complete reading history SHALL NOT be required to render the initial Dashboard.

#### Scenario: Launch with a year of readings
- **WHEN** the app launches with about one year of saved readings and the initial Dashboard projection is being prepared
- **THEN** the app remains responsive, prepares only the readings needed for the current period, shows no partial or stale chart, and displays the chart from the complete current projection as soon as it is ready

#### Scenario: Older period request completes late
- **WHEN** the user changes Dashboard periods while a period-data request is in flight and an older request completes after a newer one
- **THEN** Dashboard displays only the complete projection for the currently selected period

## ADDED Requirements

### Requirement: Period-scoped Dashboard data
Each Dashboard request SHALL load readings needed to calculate same-installation intervals overlapping the selected period, including readings outside its boundaries when needed. Existing coverage, precision, temperature, and no-extrapolation semantics SHALL remain unchanged.

#### Scenario: Interval crosses the selected period
- **WHEN** one meter has readings on January 30 and March 1 and February is selected
- **THEN** the February Dashboard includes the covered February dates from that interval and calculates the same total as a full-history projection

#### Scenario: No interval overlaps the selected period
- **WHEN** readings exist but no same-installation interval covers any date in the selected period
- **THEN** Dashboard shows the existing empty-consumption state without extrapolating from readings elsewhere in history

### Requirement: Paginated reading history
When History is selected, the app SHALL load readings in stable newest-first pages and request more as the user approaches the end. Every reading SHALL remain reachable without requiring the complete history to be loaded first.

#### Scenario: Open History after Dashboard startup
- **WHEN** the app starts on Dashboard with a large reading history and the user later opens History
- **THEN** Dashboard becomes ready without waiting for the full History list, and History displays its first page while further pages load as the user scrolls

#### Scenario: Continue through history pages
- **WHEN** the user scrolls through a history containing multiple pages
- **THEN** each page continues the newest-first order without duplicate or missing readings, and the user can reach the oldest reading

#### Scenario: A later page fails to load
- **WHEN** loading another History page fails
- **THEN** previously loaded readings remain visible and the user can retry loading the next page without duplicates

### Requirement: Correct history details at page boundaries
History SHALL show the same eligible 24-hour estimates and starting-reading markers for loaded readings as a full-history view, using same-installation neighboring data even when a neighbor is outside the loaded pages.

#### Scenario: Estimate predecessor is on another page
- **WHEN** a loaded reading's chronological predecessor is not in the currently loaded History pages
- **THEN** the reading still shows the estimate calculated from its actual predecessor, or no estimate when it is the installation's baseline

#### Scenario: Starting reading is outside loaded pages
- **WHEN** a loaded reading is its installation's persisted starting reading
- **THEN** History marks it as the starting value regardless of which page contains it
