# Proposal

## Why

TrackGas already has some responsive behavior, but the current Compose layouts do not establish reliable usability across phones and tablets in portrait and landscape. Non-wrapping history metadata, non-scrollable location suggestions and several dialog bodies, and fixed chart sizing leave gaps against the existing accessibility requirement; a focused responsiveness pass is the next task.

## What Changes

- Make Dashboard, History, toolbar, tabs, and options usable across compact phone, tablet, and resized Android windows in both orientations.
- Reflow crowded history metadata and dashboard content without truncating meaningful dates, values, units, or meter identifiers.
- Make every app-owned dialog and picker usable with limited height, 200% fonts, long localized text, and the keyboard open.
- Base layout decisions on available window/content space, including insets and keyboard constraints, rather than orientation or device type alone.
- Preserve selected tab, period, reading drafts, and pending operations during rotation and resizing.
- Define a concrete phone/tablet acceptance matrix instead of claiming compatibility with every physical device.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `app-shell`: Extend responsive-control requirements with phone/tablet window coverage, constrained dialog behavior, readable chart presentation, and safe rotation/resizing. Permit compact history metadata to reflow when it cannot fit on one line.

## Impact

- UI scope: `TrackerScreen.kt`, `TrackerPager.kt`, `DashboardContent.kt`, `ReadingHistory.kt`, `ConsumptionChart.kt`, reading forms and date/time pickers, location/language dialogs, and import/export/delete/replacement/purge feedback.
- Retain the existing centered, bounded single-column pages, separate Dashboard/History navigation, light theme, gesture actions, and localized strings.
- Use the current Compose/Material stack and JUnit setup; no production dependency, database, calculation, CSV-format, networking, or minimum-SDK change is planned.
- Add focused layout-policy regression coverage where logic is extracted, plus documented emulator/device acceptance evidence. Current source has local unit tests but no checked-in Android UI-test source set.
- Out of scope: a tablet two-pane redesign, simultaneous Dashboard and History, new features, Wear OS/TV/automotive interfaces, and guarantees for every device model.
- Assessment is source-based: the existing protections are real, but the listed layout risks are not claimed as reproduced device failures.
