# Proposal

## Why

TrackGas currently has no launcher icon, and its toolbar title is text-only. The CSV menu also uses two nearly identical downward-arrow icons, making import and export harder to distinguish.

## What Changes

- Add a cohesive gas-tracking brand icon for the installed APK and display the mark beside "TrackGas" in the app toolbar.
- Give Export CSV and Import CSV clearly distinct directional icons: export moves out/up from a tray, while import moves in/down to a tray.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `app-shell`: Define the app's launcher identity and brand mark beside the in-app title.
- `reading-data-exchange`: Require visually distinct export and import action icons.

## Impact

- Android launcher resources and `AndroidManifest.xml`.
- The shared toolbar in `TrackerScreen.kt` and CSV menu vector drawables.
- The `app-shell` and `reading-data-exchange` specifications; no new dependencies or data/API changes.
