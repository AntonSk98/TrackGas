# Design

## Context

Single-activity Compose app (`MainActivity` hosting `TrackerScreen`), state in `TrackerViewModel`, Room for readings, no preferences storage yet, no permissions in the manifest. `MainActivity` already registers activity-result handling via `CsvDocumentPickerCoordinator`. Strings exist in `values` and `values-de` with a parity test. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Request `ACCESS_COARSE_LOCATION` only when the user invokes a location-dependent feature.
- Keep permission-state transitions pure and unit-testable.

**Non-Goals:**
- Reading location, network access, Open-Meteo, in-app re-request/settings UI.

## Decisions

- **Contextual rationale dialog before system prompt (Compose `AlertDialog`)**: matches existing dialog style (e.g. `DeleteReadingDialog`) and lets "Not now" avoid consuming the system prompt. It is not shown at app launch; the location-dependent feature invokes the flow when needed.
- **Coarse permission only**: enough for daily weather lookups and less intrusive than fine.
- **Persist permission outcome, not a generic asked flag**: store one of never requested, declined, or previously granted. Dismissing the rationale or denying the Android prompt records declined, so later feature attempts do not repeatedly prompt. When the feature observes a grant, record previously granted. If a later feature attempt finds permission revoked after a recorded grant, show the rationale and request permission again; this covers expiration of an Android one-time grant.
- **Pure decision function** `shouldRequestLocation(granted, previousState)` kept in the tracking package for unit testing. The feature entry point checks current permission and state, shows the rationale when allowed, and launches `ActivityResultContracts.RequestPermission` only after Continue.
- **Do not proceed without permission**: the permission-result callback tells the location-dependent feature whether it may continue; dismissal, denial, or revoked access cannot trigger temperature fetching.
- **Dialog visibility held in saveable Compose state** so a configuration change does not re-show or lose it while the user is responding.

## Risks / Trade-offs

- [No location-dependent feature exists yet] → Keep app launch prompt-free; the future feature must invoke this request flow and handle missing permission gracefully.
- [Android may stop showing its prompt after repeated denials] → The feature remains unavailable after a denial; the user can grant permission in system settings. An observed grant that is later revoked can be requested again on a later feature attempt.
- [Process death while dialog is showing] → No permission request has been made; the user can invoke the feature again later.
