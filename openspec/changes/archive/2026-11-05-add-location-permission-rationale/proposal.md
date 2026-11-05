# Proposal

## Why

A future location-dependent feature may fetch outdoor temperatures for tracked reading dates. Requesting location at app launch, before the user reaches such a feature, is premature. This change keeps the app prompt-free at launch and establishes a concise explanation for requesting approximate location only when the user chooses a feature that needs it. No weather data is fetched yet.

## What Changes

- When the user invokes a location-dependent feature without approximate location access, show a concise explanation before the Android system permission prompt. The dialog offers "Continue" and "Not now".
- Declare and request only the approximate location permission (`ACCESS_COARSE_LOCATION`).
- Do not show the explanation or system prompt at app launch. If the user declines the explanation or denies the system prompt, the location-dependent feature remains unavailable and later attempts do not show the dialog again; all existing tracking features continue to work. The user can grant access in Android settings.
- If permission is already granted when a location-dependent feature is invoked, proceed without showing the explanation or system prompt.
- If permission was previously granted but is later revoked (including expiration of a one-time grant), show the explanation and request permission again when the user next invokes a location-dependent feature.
- Add English and German strings for the dialog.
- Keep app launch free of runtime location permission prompts.
- Out of scope: reading the location, calling Open-Meteo, any network access or internet permission, in-app settings entry to re-request permission, displaying weather in the UI.

## Capabilities

### New Capabilities
- `location-permission`: Contextual explanation and request of approximate location access when a location-dependent feature is invoked.

### Modified Capabilities
- `app-shell`: "Offline application launch" requirement must permit the one-time location rationale/permission prompt.

## Impact

- `app/src/main/AndroidManifest.xml`: add `ACCESS_COARSE_LOCATION`.
- Tracking UI: a concise rationale dialog and permission request flow invoked by location-dependent features; persist whether permission was declined or previously granted so repeated denials are suppressed but revoked grants can be requested again.
- `res/values/strings.xml`, `res/values-de/strings.xml`: new strings (covered by the existing localization parity test).
- Tests: unit tests for the prompt decision logic.
- No new dependencies, no network use.
