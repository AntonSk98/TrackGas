# Spec Delta

## ADDED Requirements

### Requirement: Responsive initial dashboard projection
During startup, the app SHALL keep the interface responsive while preparing the complete initial reading snapshot and its dashboard projection. The dashboard SHALL display chart data only after it is projected from the complete, current snapshot.

#### Scenario: Launch with a year of readings
- **WHEN** the app launches with about one year of saved readings and the initial dashboard projection is being prepared
- **THEN** the app remains responsive, shows no partial or stale chart, and displays the chart with values from the complete snapshot as soon as projection is ready
