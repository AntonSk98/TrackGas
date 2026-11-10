# Design

## Context

See `proposal.md` for motivation and `specs/app-shell/spec.md` for the acceptance contract.

The current UI already has useful protections:

- `TrackerPager.kt` centers each page, uses safe-drawing insets and keyboard padding, and owns separate lazy-list states.
- `TrackerScreen.kt:225-229` bounds the single-column content to 840 dp and provides vertical scrolling.
- `DashboardContent.kt` uses wrapping rows for period chips and consumption rows.
- `ReadingFormLayout.kt:64-85` handles safe areas, keyboard padding, and scrolling, but chooses whole-form scrolling using `screenHeightDp < 480`, not the actual remaining dialog height.
- `ReadingForm.kt` saves the reading draft and scrolls the time picker; its horizontal picker decision uses screen dimensions even though the surface is capped at 560 dp.
- Replacement and purge already scroll their form bodies.

Source-level gaps and risks:

- `ReadingHistory.kt:86-94` puts the full-precision value and meter identity in a non-wrapping row.
- `MeterLocationDialog.kt:35-65` renders all suggestions in a non-scrollable column; suggestion targets use padding rather than an explicit minimum height.
- Language, delete, CSV failure, and export failure dialogs do not provide explicit scrolling for their variable-length content.
- `ConsumptionChart.kt` fixes plot heights to 220/110 dp, fixes the shared value-axis width to 56 dp, and chooses horizontal-axis spacing only from period type.
- The toolbar title and fixed row of actions compete for width at large font sizes.
- The repository has JUnit tests under `app\src\test`, but no checked-in `app\src\androidTest` tests or Compose UI-test dependencies in `app\build.gradle.kts`. Generated historical reports are not current coverage.

These are observed implementation constraints, not evidence that every configuration fails. The existing app-shell spec already demands compact/landscape/200% font usability. Its exact two/three-row History language conflicts with necessary reflow, so the delta explicitly relaxes only that presentation constraint.

## Goals / Non-Goals

**Goals:** Make the existing composables satisfy the window matrix through intrinsic wrapping, bounded scrolling, and a small shared constraint policy where needed. Keep data/state ownership and mutation callbacks unchanged.

**Non-Goals:** Introduce a new navigation architecture, a tablet master/detail layout, orientation locks, device-model checks, new production libraries, or responsive branches in repositories and domain calculations.

## Decisions

### 1. Adapt to available space; retain the bounded single column

Keep the 840 dp page and 560 dp form caps, centered on wide windows. Apply layout decisions at a bounded container before entering a vertically unbounded lazy-list item or scrolling body. Use current Compose constraints and density/font metrics to choose compact spacing and picker arrangement; use safe-area/IME-adjusted bounds for custom forms.

Prefer wrapping content without a breakpoint. Extract a pure layout-policy helper only for actual choices such as chart label density or picker layout; cover its boundaries with existing JUnit. Do not copy screen-width heuristics into every dialog.

Alternative: Material adaptive scaffolds or two-pane tablets. Rejected because the existing page separation and visual order are intentional, and improved usability does not require redesign or another dependency.

### 2. Reflow content before shrinking or hiding it

Replace the History value/meter row with a wrapping arrangement, retaining the current compact appearance when it fits. Ensure long numeric text, dates, timezone strings, and notes can wrap without hiding significant information. Keep gesture semantics on the complete entry and preserve the association between value and meter.

For the toolbar, reserve the two 48 dp actions and make title/brand layout consume only remaining space; let the toolbar grow or reflow its title if required at 200% fonts instead of reducing text scale or obscuring Add/options. Keep the brand visible. Preserve labeled tabs and the existing period-navigation hierarchy.

Alternative: ellipsis or smaller text. Rejected for meaningful reading data and primary controls because it hides information or defeats the user's font setting.

### 3. Bound dialog scrolling by actual safe space

Reuse the custom form approach only where its sizing and dismissal behavior are needed. Resolve content bounds once, avoid double-applying insets, and allow the entire form including action rows to scroll when available height cannot accommodate pinned actions. Preserve backdrop hit testing and pending-dismissal protections.

Give location suggestions a bounded scrollable region with explicit minimum 48 dp targets; ensure introductory text, query, errors, and action rows also remain reachable under the tightest height. Merely scrolling suggestions is insufficient if the fixed header and buttons consume all space.

For standard AlertDialogs, add bounded body scrolling where content can exceed space and confirm title/actions remain reachable. If Material's fixed title/action slots still fail the minimum-height matrix, reuse the bounded whole-dialog surface rather than adding per-dialog pixel offsets. Do not replace otherwise-working modal implementations gratuitously.

Choose horizontal/vertical time-picker presentation from the measured inner surface, not the physical screen width. Keep date-picker errors and confirmation accessible. Do not change date/time validation or picker dismissal semantics.

Following user feedback, retain calendar date selection instead of forcing date
text entry. Keep the calendar's intrinsic grid width in a horizontally pannable
viewport when necessary and the complete dialog vertically scrollable. The
calendar header can pan the viewport without invoking Material's month swipe.
Leave the time-picker clock/input policy unchanged.

Alternative: make every modal full-screen. Rejected as the default because it needlessly changes tablet and portrait UX; scrolling within the current surface is sufficient unless measured constraints demonstrate otherwise.

### 4. Size chart presentation without changing chart data

Keep chart projections, covered buckets, zero handling, temperature gaps, and long-press inspection intact. Pass measured content width/font information into presentation policy. Select horizontal-label spacing from available plot width and measured localized label sizes; reduce tick density, not the number of plotted values.

Determine one shared value-axis reservation for both plots from formatted label/title measurements. Let plot height account for font/axis needs while remaining bounded; keep temperature visually shorter where readable. Do not compress charts so aggressively in landscape that plots become illegible: the page already scrolls.

Use compact 180/80 dp chart baselines, increasing height when measured axis
requirements need more room. Merely restoring the original 220/110 dp containers
did not restore compact plots: removing the old tooltip margins enlarged their
visible plotting areas. Reduce the plots themselves instead of reintroducing
tooltip margins. Inspection uses a content-sized bubble anchored to the selected
bucket and clamped to chart edges. It overlays the plot without reserving Vico
marker margins, avoiding exhausted temperature canvases at large fonts.

Alternative: horizontal page scrolling or dropping buckets. Rejected because the existing chart is fitted to the period and responsiveness must not alter represented coverage. Sparse axis ticks plus existing inspection preserve access to bucket details.

### 5. Preserve state at the existing owners

Do not key a tracker session, pager, list state, draft, or operation by window size or orientation. Keep saved draft identities and retained tracker ownership. Use current lazy-list saved state to preserve the same first visible item as closely as reflow permits; change this mechanism only if transition acceptance exposes a concrete failure.

Picker state must remain stable when its layout changes. Confirm active mutations continue once after recreation, and retain fresh-process reset behavior. Do not broaden this change into draft persistence across process death.

Alternative: recreate the entire screen tree per layout class. Rejected because it risks losing drafts and replaying operations.

### 6. Use a reproducible window matrix with existing tooling

No new test framework is required. Add JUnit regression tests for extracted pure presentation policy and run relevant existing tests. Perform actual Compose rendering and interaction acceptance on Android emulators/devices; a pure sizing test cannot prove visual responsiveness.

| Window group | Window sizes in dp |
| --- | --- |
| Small phone | 320 x 568 and 568 x 320 |
| Typical phone | 360 x 800 and 800 x 360 |
| Small tablet | 600 x 960 and 960 x 600 |
| Large tablet | 800 x 1280 and 1280 x 800 |
| Resizable window sequence | 960 x 640, 480 x 640, 320 x 480, then expand again |

Run each window at 100% and 200% fonts in English and German. Sizes describe app-window bounds before internal safe-area/IME padding; record actual bounds, API level, font scale, and keyboard state. Exercise an API 35 device for edge-to-edge/keyboard behavior and repeat small-phone/tablet smoke cases on the minimum supported API 26.

For every matrix entry, exercise both tabs, options, populated/empty Dashboard, all period types, both charts, long values/notes, multiple meter histories, all app-owned dialogs, and date/time pickers. Open the keyboard in Add/Edit/location search, show validation/error content, and scroll to every enabled action. System document pickers remain platform-owned: exercise return/rotation integration without promising to redesign their UI.

Rotate/resize while editing and while a mutation is pending. Check navigation, per-tab positions, draft fields, pending guards, and exactly-once completion. Record pass/fail evidence and any unavailable environments in directly related documentation; incomplete device coverage must not be reported as universal support.

## Risks / Trade-offs

- Nested scrolling or unbounded constraints can make content unreachable -> measure outside scroll containers and ensure a whole-content scroll fallback at short heights.
- Insets applied at multiple levels can waste space or cause clipping -> define one owner per window/surface and exercise gesture and three-button navigation.
- Larger axis labels can consume the narrow plot -> measure labels, use the existing compact axis number formatting, and reduce tick density while retaining exact inspection data.
- A fixed time-picker control may exceed the inner width at large fonts -> select layout from actual bounds and use the Material text-input alternative if neither clock layout fits, preserving the same selected time.
- Manual visual coverage is not an automated UI regression suite -> retain a repeatable matrix and focused policy tests; adding a new UI-test framework is not part of this change.
- Absolute guarantees for all hardware are not meaningful -> use continuous constraint-driven behavior plus the explicit representative matrix.

## Migration Plan

No data migration or permission change. Implement page reflow, dialog constraints, then chart sizing in small UI-only increments. Run targeted local tests and the acceptance matrix before marking tasks complete. Rollback consists of reverting the UI changes without changing stored readings or domain behavior.
