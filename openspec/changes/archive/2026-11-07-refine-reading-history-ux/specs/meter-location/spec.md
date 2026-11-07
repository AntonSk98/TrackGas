# Spec Delta

## MODIFIED Requirements

### Requirement: Localized location dialog
The location dialog and its errors SHALL be available in English and German and follow the in-app language. Its input label SHALL be “Meter location” in English and “Standort des Zählers” in German. When opened from Tracker options, it SHALL omit the explanatory city/address sentence. The existing city/place lookup behavior SHALL remain unchanged.

#### Scenario: German dialog
- **WHEN** the app language is German and the user opens Meter location
- **THEN** the title, “Standort des Zählers” field label, actions, and errors appear in German

#### Scenario: English dialog
- **WHEN** the app language is English and the user opens Meter location
- **THEN** the title, “Meter location” field label, actions, and errors appear in English
