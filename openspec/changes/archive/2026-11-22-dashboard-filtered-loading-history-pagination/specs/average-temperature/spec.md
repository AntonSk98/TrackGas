# Spec Delta

## ADDED Requirements

### Requirement: Period-scoped temperature loading
The Dashboard SHALL load interval and daily temperature means only for the selected period and the reading boundaries required to project it. Temperature-cache change observation SHALL NOT materialize the complete cache, and background synchronization SHALL inspect only the selected period's readings and covered dates. Historical weather SHALL be checked when the user selects its period; complete reading history SHALL NOT be required for startup synchronization.

#### Scenario: Startup with extensive reading history
- **WHEN** the app starts with readings and cached temperatures across many years
- **THEN** Dashboard temperature loading and background synchronization use only the current period and its required boundary readings

#### Scenario: Select a historical period
- **WHEN** the user navigates to a period whose temperatures are not cached
- **THEN** the app checks for missing interval and daily means for that period without scanning unrelated history

#### Scenario: Temperature cache changes
- **WHEN** a temperature mean is stored or removed while Dashboard is visible
- **THEN** the app refreshes the selected period's cache values without emitting every cached temperature row
