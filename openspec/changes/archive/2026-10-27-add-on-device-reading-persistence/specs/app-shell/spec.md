# Spec Delta

## MODIFIED Requirements

### Requirement: Cohesive light visual hierarchy
The interface SHALL use warm off-white backgrounds, white cards, teal accents, and consistent typography, spacing, and controls across Dashboard, History, Add/Edit, and delete confirmation. Dashboard SHALL emphasize the selected period and consumption total. History SHALL emphasize reading dates and values with identifiable Edit/Delete actions. Local-storage disclosure SHALL remain readable on both pages without dominating the content.

#### Scenario: Browse a populated dashboard
- **WHEN** the user views a period with consumption
- **THEN** Weekly/Monthly/Yearly selection, a prominent Consumption total, a chart, date-range navigation, and supporting values appear in that order beneath the shared toolbar and tabs, with local-storage disclosure visible in the page and date-range navigation directly beneath the chart inside its card

#### Scenario: Manage a reading with consistent styling
- **WHEN** the user opens History, edits a reading, or requests deletion
- **THEN** the list and dialogs share the same visual language, Edit/Delete remain identifiable, validation errors remain inline, and deletion still requires explicit confirmation

#### Scenario: Understand temporary storage
- **WHEN** the user opens either Dashboard or History
- **THEN** the page includes readable text explaining that readings are stored on the device and are not backed up to the cloud
