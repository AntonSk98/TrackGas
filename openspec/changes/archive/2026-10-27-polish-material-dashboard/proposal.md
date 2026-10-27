# Proposal

## Why

TrackGas already has working reading management and calendar consumption calculations, but its text-heavy interface lacks a clear visual hierarchy and trend visualization. A cohesive Material 3 design, truthful charts, and swipe navigation will make the existing offline tracker feel polished without obscuring its temporary-storage limitation.

## What Changes

- Refine the existing Compose Material 3 UI into a warm off-white design with white cards, teal accents, clear typography, compact chrome, and responsive layouts across Dashboard, History, and reading dialogs.
- Add Vico bar charts above the existing exact-value consumption list: daily bars for Week/Month and monthly bars for Year, derived from the same covered buckets and independent period total.
- Support horizontal swipes and labelled tab taps between Dashboard and History with immediate visual feedback, preserving the selected period, selected tab across rotation, and independent page scroll positions.
- Keep Weekly/Monthly/Yearly selection in the normal Dashboard scroll flow rather than pinned beneath the tabs.
- Place date-range navigation directly below the chart inside its summary card, before numeric rows; retain it below the message in empty periods.
- Use compact weekday/day-number/month-name consumption labels, with year context in the period heading instead of every row or tick.
- Long-press a bar to show its weekday, two-digit monthly day, or month and localized consumption; drag while holding to inspect without changing pages.
- Keep ordinary swipe navigation available both inside and outside the chart, unless long-press inspection is active.
- Make empty states and Add/Edit/Delete actions clear while retaining inline validation, delete confirmation, localization, system insets, and visible temporary-reading disclosure.
- Tap a History entry to edit only its meter value, with a read-only Date field; long-press the entry to request deletion. Expose both actions to accessibility services without separate icons.
- Allow outside-tap dismissal of reading drafts and flag duplicate dates immediately during Add date selection using the same rules as Save.
- Use the same minimal outlined Date field in Add and Edit; Add opens its picker on tap while Edit keeps the date immutable.
- Keep presentation components focused and state-driven; do not introduce storage, new analytics, or changes to consumption arithmetic.

## Capabilities

### New Capabilities

None. The additions belong to the existing application-shell and gas-tracking capabilities.

### Modified Capabilities

- `app-shell`: Replace the dark-only shell with a cohesive, responsive Material 3 light interface and consistent usability for the app shell, content, and dialogs.
- `gas-consumption-tracking`: Extend tab navigation to swipes with preserved page state and add truthful consumption bars alongside exact values, without changing coverage or calculation rules.

## Impact

- Presentation: `MainActivity.kt`, a focused theme package, `TrackerScreen.kt`, `DashboardContent.kt`, `ReadingHistory.kt`, `ReadingForm.kt`, and localized resources.
- New focused chart adapter/renderer using existing `Dashboard` and `ConsumptionBucket` data; no chart types or Compose dependencies enter consumption arithmetic.
- Dependencies: retain the existing Compose BOM and Material 3; explicitly declare Compose Foundation for its pager and pin compatible Vico Compose/Material 3 artifacts after checking their Kotlin/Compose requirements.
- Validation: extend existing JUnit coverage for chart projection and navigation invariants, plus targeted device acceptance for swipes, charts, responsive layouts, and dialogs. Update README usage and its acceptance checklist.
- Compatibility: retain Android API 26+, offline launch, the top-right Add action, current period/date rules, and in-memory-only readings. No database, network permission, account, theme setting, price/weather feature, or process-death persistence is added.
