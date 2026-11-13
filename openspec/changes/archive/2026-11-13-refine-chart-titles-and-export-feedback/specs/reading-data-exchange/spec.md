# Spec Delta

## MODIFIED Requirements

### Requirement: Export completion and failure
The app SHALL report success only after the complete file is written and closed. Cancelling destination selection SHALL leave tracking data unchanged and SHALL NOT report success. A write failure SHALL show a privacy-safe error and permit another export attempt without changing tracking data. Success SHALL appear in a compact, dismissible window sized to its content that shows only the localized text “Saved” / “Gespeichert,” with no icon and no confirmation button.

#### Scenario: Destination or write fails
- **WHEN** the selected destination cannot be opened, written, or closed
- **THEN** the user sees an export failure instead of success and can choose a destination and try again

#### Scenario: Picker cancellation
- **WHEN** the user cancels the destination picker
- **THEN** no export success or error is reported and the tracker remains unchanged

#### Scenario: Successful export
- **WHEN** the complete file has been written and closed
- **THEN** a compact text-only “Saved” / “Gespeichert” window appears that does not span the full screen width, contains no icon, and can be dismissed
