# Spec Delta

## Purpose

Explain why a location-dependent feature needs approximate location and request it only when the user chooses that feature.

## ADDED Requirements

### Requirement: Request location in context
The app SHALL NOT show a location explanation or permission prompt at launch. When the user invokes a location-dependent feature without permission, show a concise explanation before requesting permission if permission has never been requested or was previously granted. The explanation SHALL say location access lets TrackGas fetch outdoor temperatures for tracked dates and offer Continue and Not now.

#### Scenario: Launch without using a location-dependent feature
- **WHEN** the user opens the app without invoking a location-dependent feature
- **THEN** no location explanation or permission prompt is shown

#### Scenario: Invoke a location-dependent feature without previously requesting permission
- **WHEN** the user invokes a location-dependent feature, approximate location is not granted, and permission has never been requested
- **THEN** the explanation appears before any Android permission prompt

#### Scenario: Continue to system prompt
- **WHEN** the user taps Continue in the explanation
- **THEN** the Android approximate-location permission prompt is shown

#### Scenario: Not now
- **WHEN** the user taps Not now
- **THEN** no Android permission prompt is shown, no location-dependent work such as fetching temperature is performed, and future attempts do not show the explanation again; existing tracking features remain available

#### Scenario: Deny the system prompt
- **WHEN** the user denies the Android permission prompt
- **THEN** no location-dependent work such as fetching temperature is performed, and future attempts do not show the explanation or system prompt again; existing tracking features remain available

### Requirement: Request approximate location only
The app SHALL request only approximate location access and SHALL NOT request precise location or background location access.

#### Scenario: Permission scope
- **WHEN** the system permission prompt is shown
- **THEN** only approximate location is requested

### Requirement: Honor current permission state
When approximate location is granted, the app SHALL proceed without a prompt and record the grant. After a recorded grant is revoked, the app SHALL show the explanation and request permission again on the next feature attempt. Dismissal or denial SHALL suppress later attempts.

#### Scenario: Already granted
- **WHEN** the user invokes a location-dependent feature with location permission already granted
- **THEN** no dialog or permission prompt is shown, the permission state is recorded as granted, and the feature proceeds

#### Scenario: Previously granted permission is revoked
- **WHEN** approximate location was previously granted, is now revoked, and the user invokes a location-dependent feature
- **THEN** the explanation is shown again before requesting permission, and the feature does not proceed unless permission is granted

#### Scenario: Permission granted later in system settings
- **WHEN** the user previously declined, then grants approximate location in system settings and invokes a location-dependent feature
- **THEN** the feature proceeds without showing the explanation or system permission prompt, and the grant is recorded

### Requirement: Tracking works without location
All existing tracking features SHALL work identically whether location permission is granted, denied, or never requested. Users MAY grant access later in Android system settings.

#### Scenario: Declined user keeps full functionality
- **WHEN** the user declined location access
- **THEN** they can add, edit, delete, import, and export readings and view Dashboard and History as before

### Requirement: Localized explanation
The explanation dialog text SHALL be available in every language the app supports (English and German) and follow the in-app language selection.

#### Scenario: German explanation
- **WHEN** the app language is German when a location-dependent feature is invoked without permission
- **THEN** the dialog title, body, and actions appear in German
