# Design

## Context

See `proposal.md` for motivation and the capability deltas for behavior contracts.

The app already uses Compose Material 3 through the Compose BOM. `MainActivity` applies the default `darkColorScheme()` directly; there is no dedicated theme. `TrackerScreen` hosts a toolbar, `PrimaryTabRow`, and one conditional `LazyColumn`, with separate remembered list states and shared reading dialogs. Navigation currently supports taps only.

`TrackerViewModel` owns the selected tab, period, readings, and derived `Dashboard` in an activity-scoped, in-memory snapshot. `DashboardContent` renders covered buckets and an independently calculated total; `ReadingHistory` renders actual readings newest first. `ReadingForm` already provides a scrollable Material dialog, date picker, and inline validation. Existing tests use JUnit; there is no instrumented Compose test setup.

The original main spec requires a dark UI. User feedback explicitly supersedes that visual policy with warm off-white, white cards, and teal accents; the app-shell delta records this change. Temporary-storage disclosure, no coverage/estimation labels, no unknown rows, and no process-death persistence remain constraints, as do Android API 26+, the existing Kotlin/Compose toolchain, and offline operation.

## Goals / Non-Goals

**Goals:**
- Create a restrained Material 3 visual system rather than introduce a second UI toolkit.
- Keep a single domain snapshot and focused presentation components with explicit inputs and callbacks.
- Make charts supplemental: the list provides readable values even when labels must be sparse or the user cannot interpret a chart.
- Prefer standard pager and chart components over custom gesture detection and hand-drawn chart infrastructure.

**Non-Goals:**
- No new storage, onboarding, authentication, settings, theme toggle/dynamic-color mode, navigation framework, or dependency-injection framework.
- No changes to interval allocation, date validation, precision rules, or the transient tracker lifecycle.
- No chart pan/zoom, comparison statistics, predicted usage, cumulative-meter charts, or demo data.
- No promise that unfinished form drafts survive rotation; preserve the existing saved-reading guarantees.

## Decisions

### 1. Build a small Material 3 design system

Extract a focused `ui/theme` package and apply `TrackGasTheme` from `MainActivity`. Define semantic color roles, type scale, and shapes once rather than hardcoding presentation colors in feature composables.

Initial visual direction:

| Element | Direction |
| --- | --- |
| Background | Warm off-white, starting at `#F7F6F2` |
| Content surfaces | White cards, starting at `#FFFFFF` |
| Primary accent | Deep teal, starting at `#17685F`, with white on-primary |
| Primary text | Dark green-grey, starting at `#1F2C27` |
| Secondary text | Muted green-grey, starting at `#53625C` |
| Errors/destructive intent | Material error roles; never accent color alone |
| Geometry | Approximately 16 dp card corners and 24 dp dialog corners, minimal elevation |
| Rhythm | 4/8/12/16/24 dp spacing; 16 dp compact outer padding and 24 dp on wider layouts |
| Type | Platform sans-serif through Material typography; large Consumption value, clear section titles, quieter supporting text |

These are design starting values, not final contrast claims. Complete all color pairs as a coherent scheme and adjust them to meet the spec contrast thresholds. Use edge-to-edge light system bars with dark icons, and match the Android launch-window background to the Compose theme. Avoid gradients, decorative gauges, excessive card nesting, and downloaded fonts.

Use a centred content region of about 840 dp maximum width, not stretched phone rows on tablets. Keep a single-column reading order; wrap/reflow controls rather than forcing fixed heights at large font sizes.

Alternative: adopt another Material wrapper or a broad third-party design kit. Rejected because Material 3 is already present and additional abstractions would not solve the current hierarchy problem.

### 2. Define a deliberate screen composition

The shared shell retains the TrackGas title, accessible top-right Add icon, and labelled Dashboard/History tabs in a compact toolbar. Do not replace these with bottom navigation or add a competing floating Add button.

Dashboard reading order:

```text
TrackGas                                      [+]
       Dashboard               History

[ Weekly ] [ Monthly ] [ Yearly ]

Temporary readings: lost when the process ends.

Consumption
12 m3
[               bar chart                      ]
[previous]      selected date range       [next]

date/month                          value
date/month                          value
```

The disclosure is readable secondary text near the top of each page, below period controls on Dashboard and above the list on History, not a prominent boxed warning. Keep it visible without requiring scrolling to the end of a long list. Weekly/Monthly/Yearly controls are a normal Dashboard list item, scrolling away with the heading and consumption content. Only the shared toolbar and Dashboard/History tabs remain fixed. This supersedes the earlier sticky-header refinement. History has no period controls.

Place the teal-accented total and chart in one white surface with a restrained border and a slightly shorter plot. Put previous/next navigation and the centred date-range heading directly below the chart within that same surface, before numeric rows. Empty periods retain the navigation below their explanatory message in the same card. Neither selector is pinned to the screen bottom. Keep the supporting value list lean, using alignment and subtle dividers instead of a card for every bucket. Chart ticks and rows share a pure localized bucket formatter: abbreviated weekdays for Week, day numbers for Month, and abbreviated month names for Year. History and reading forms keep full dates. The period heading supplies context once: week range with the year on the end date (both dates when crossing years), full month/year, or year. Build these heading patterns through Android's locale-aware date skeleton support. Keep chart ticks sparse.

History keeps date and full-precision cumulative value visually prominent without action icons. Use Compose `combinedClickable` on each whole entry: tap opens Edit and long press requests delete confirmation. Supply localized, reading-specific click and long-click action labels for accessibility, with at least a 48 dp target. Dividers remain outside the interactive entry. History has no period controls, chart, or consumption series. A simple empty state explains that no meter readings exist and points to toolbar Add.

Use the existing empty-consumption message for uncovered periods, styled as a purposeful empty state rather than a zero-valued summary. Keep the selected period controls and shared Add available.

Restyle the shared Add/Edit form and delete confirmation through theme roles. Make Save the primary visual action and Cancel secondary, keeping inline errors, date-picker semantics, full-precision edits, and explicit delete confirmation. On compact-height layouts, scroll the entire dialog including actions; otherwise keep a scrollable body and separate action row. This prevents a landscape keyboard and enlarged text from squeezing fields into an unreadable viewport. Do not redesign the form into a new workflow.

History Edit allows changing only the meter value. Show its original localized date in a read-only outlined field labelled `Date`, with no `Reading date` or `Choose date` text and no picker. Submit the original reading date explicitly on Save. Add retains its date picker and duplicate-date feedback; this restriction is in the History editor, not a change to the domain's general validation API.

Add shares that same outlined Date presentation, spacing, and meter field, without a separate Reading date label or Choose date text button. A full-field clickable overlay opens the picker and exposes a localized button label containing the current date; hide the underlying Add text-field semantics to avoid duplicate accessibility targets. Edit retains ordinary read-only field semantics and no picker action. Date selection never requires the keyboard outside the picker.

The full-screen dialog layout has a separate tappable backdrop behind its bounded form surface. Taps outside the surface discard the draft, while taps within it never fall through; Back and Cancel retain their dismissal behavior. Respect safe drawing and keyboard insets on the form itself. The nested date picker handles its own dismissal without discarding the parent draft.

Extract the existing pure date checks into `validateReadingDate`, shared by save validation and the date picker. Pass the authoritative reading snapshot to the form; exclude the edited reading's own identity. Display duplicate-date feedback only inside the date picker, immediately for a conflicting selection and without requiring a meter value or Save. Merely opening Add/Edit must not show a duplicate warning in the parent form. If Save rejects an unchanged duplicate date, open the picker to show the error there, retaining the unsaved draft and any value errors. Keep invalid dates selectable so the user can see the reason, but disable date confirmation until valid. Keep the picker error above its bounded, independently scrollable calendar so the feedback remains visible instead of being clipped below it. Announce inline date errors politely to accessibility services. Save still performs all date, value, and chronological checks atomically.

Alternative: replace exact-value rows with charts. Rejected because precise values remain essential. The user explicitly chose tap/long-press History actions instead of icons; labelled accessibility actions preserve non-visual access.

### 3. Use Compose Foundation HorizontalPager with one navigation coordinator

Explicitly depend on BOM-managed Compose Foundation and use its two-page `HorizontalPager`. Each page owns a `LazyColumn` using the existing separate list states, remembered outside disposable pager page content. Key pages by `TrackerTab` identity and continue to key readings by their IDs.

Retain `TrackerState.tab` as the activity-lifetime retained selection, not a second independently interactive navigation mechanism:

1. Bootstrap the pager from `state.tab` when the screen attaches. If pager saveable state was restored, reconcile it with the fresh ViewModel selection before observing navigation, so process death cannot reopen History in an empty new tracker.
2. Tab taps request pager movement through a single coordinator. Last requested tab wins; cancel any obsolete programmatic animation when a newer request arrives.
3. Observe distinct settled-page changes after bootstrap and publish them through `onSelectTab` only for retained selection. Visual selection follows the pager's live target; the indicator position follows `currentPage + currentPageOffsetFraction` directly, without a second animation. Taps get immediate feedback and swipes move the indicator continuously, while a partial/cancelled drag is not a new retained selection.
4. Do not run an unguarded state-to-pager animation effect for every ViewModel update: publishing a swipe must not animate back toward a stale tab or create feedback loops.
5. Ordinary horizontal swipes, including chart-originated swipes, belong to the pager; ordinary vertical gestures belong to the page list. Disable chart pan/zoom. Only after a long press does the chart consume drag movement for inspection, using Compose's standard long-press drag detector. Vico tracks the pointer throughout; a stable marker wrapper gates drawing until inspection activates and delegates margin measurement to preserve top tooltip space. Release/cancellation clears inspection. No separate navigation state or custom timer is needed.

Add/Edit and delete dialogs remain outside pager pages so there is one editor/confirmation coordinator. Neither tab changes nor pager effects publish domain mutations or reset the selected period. Layout direction follows the standard pager behavior; left/right descriptions in acceptance scenarios assume LTR.

Alternative: two always-active synchronization effects driven by `state.tab` and `currentPage`. Rejected because mid-drag and animation updates can cause oscillation or duplicate selection events. Custom swipe detectors are also unnecessary and risk fighting nested vertical scroll.

### 4. Isolate Vico behind a focused chart adapter and renderer

Use Vico's Compose Cartesian column/bar chart and Material 3 integration, which fit the user's selected Compose-native bar-chart approach. Pin a stable release compatible with the project's Kotlin 2.2.10 and Compose BOM baseline; do not automatically take the newest Vico release or upgrade the entire toolchain. Check the candidate release's published metadata/sample dependency requirements before adding the version. Declare only its required Android Compose/Material 3 artifacts.

Keep all Vico APIs inside a new `ConsumptionChart` renderer. A separate small, pure Kotlin projection maps the selected period and existing `Dashboard.buckets` into ordered chart entries. It must not reconstruct intervals or calculate a new total.

Projection contract:
- Retain each bucket's date/month identity and original `BigDecimal` amount for formatting and tests.
- Use day offsets from the period start for Week/Month and month offsets for Year; do not compact dates to sequential indices. Missing dates occupy calendar space but create no chart entries or zero bars.
- Keep chronological order and include genuine zero-valued buckets.
- Only convert at the rendering boundary. Normalize nonzero magnitudes by the maximum using decimal arithmetic before producing finite chart coordinates in `[0, 1]`; format axis values back in cubic metres using that same scale. This avoids overflow from converting arbitrarily large meter deltas directly to floats/doubles.
- For an all-zero series, show identifiable zero marks and a zero label without inventing positive usage or dividing by zero.
- Continue displaying `dashboard.total`, never a sum of rounded rows or normalized chart coordinates.

The renderer uses a zero-based vertical axis, restrained horizontal grid lines, one accent series, and no redundant legend. All 7/28-31/12 calendar positions fit the plot; reduce tick density rather than enabling chart pan. Use a distinguishable zero marker anchored to the baseline, not an artificially positive-height bar. Uncovered positions have no such marker. Retain the date/value list as the accessible and precision-preserving alternative; no essential value requires touching the plot.

Use Vico's standard marker gated by long-press inspection, without enabling pan/zoom. Once the long press activates, dragging across bars updates the marker without switching pages; quick swipes still reach the pager, including from the chart. Anchor the tooltip at the top of the plot using Vico's reserved top margin, never around a bar's endpoint where a short or zero bar would place it beneath the finger. Its compact themed tooltip shows the localized weekday (Week), two-digit day such as `04` (Month), or month name (Year), followed by consumption and the cubic-metre unit. Resolve the selected x offset back to the original projected entry; never reconstruct a tooltip value from normalized coordinates. Use the same decimal formatting as numeric rows, including genuine zero. The snapshot-keyed host resets inspection when period or reading data changes.

Remember the chart model producer and update it from the complete period-plus-bucket snapshot. Do not reuse stale models when changing period type, mutating readings, or transitioning from populated to empty. Empty data renders the empty state instead of leaving the previous chart visible.

Alternative: a Compose Canvas chart. Rejected because axis layout, scaling, and future maintenance would become application code. A legacy View chart wrapper adds an avoidable interoperability layer. Line charts are not selected: bars describe per-bucket amounts and avoid suggesting continuous readings over uncovered dates.

### 5. Keep responsibilities narrow

| Area | Responsibility |
| --- | --- |
| `MainActivity` / theme | Theme application and existing state/callback wiring |
| `TrackerScreen` | Shared shell, pager coordination, and shared dialogs |
| `DashboardContent` | Period controls, summary, chart placement, and numeric list |
| `ConsumptionChart` / pure projection | Chart-only rendering and conversion of existing buckets |
| `ReadingHistory` | Actual readings and edit/delete callbacks |
| `ReadingForm` | Draft entry, inline errors, date picker, and save/cancel presentation |
| `TrackerViewModel` / consumption domain | Existing authoritative state, validation, and calculations |

Reuse `ReadingFormat` and string resources for visible/accessibility labels. Extract only repeated presentation pieces actually shared across screens; avoid a generic card/control framework or a new presentation-state hierarchy.

### 6. Validate at existing seams

Extend JUnit tests for the pure chart projection: empty data, day/month offsets, known zero versus absent buckets, one positive bucket, all-zero series, large/tiny magnitudes with finite coordinates, mutation updates, and precision preservation. Retain existing dashboard, calendar, validation, and ViewModel tests as regression contracts.

Extend ViewModel tests where useful to cover alternating tab selection and mutations preserving period/tab. These cannot establish that a real swipe works. Verify actual pager and chart gesture behavior on a device/emulator using the README acceptance checklist; do not claim coverage from ViewModel-only tests.

Use the existing Gradle test/build commands. Do not add a screenshot framework or unrelated testing toolchain. Device acceptance explicitly covers rapid/cancelled outside-chart swipes, horizontal chart inspection, vertical chart scrolling, separate long-list scroll positions, rotation on History, process restart, all-zero/no-coverage transitions, localized values, 360 dp width at 200% font scale, landscape keyboard, touch targets, and contrast. Revise the README statement that excludes screen-reader acceptance for the controls/value-list checks added here.

## Risks / Trade-offs

- [Vico release may require a newer Kotlin/Compose baseline] -> Verify and pin a compatible stable version before integration; do not silently change the baseline or substitute libraries.
- [Pager and retained tab selection can fight each other] -> Bootstrap once, publish settled pages, and coordinate programmatic navigation through one path rather than competing effects.
- [A chart can make missing coverage look like zero] -> Use calendar-positioned covered entries only, explicit genuine-zero marks, and retain numeric rows.
- [Plotting large/tiny decimal amounts can lose precision] -> Preserve decimal values and totals; normalize only visual coordinates and retain localized numeric values below the plot.
- [A 31-day chart cannot label every date on a phone] -> Keep all marks, show sparse ticks, and provide every covered date/value in the list.
- [A polished UI could imply durable storage] -> Keep the temporary-reading warning prominent on both pages and do not weaken README lifecycle guidance.
- [No automated device UI harness exists] -> Use focused JUnit tests for projection/state and an explicit manual acceptance record for gestures, rendering, and accessibility; never present manual work as completed without running it.

## Migration Plan

This is a presentation/dependency change with no data migration or server rollout. Implement the theme and pager, then integrate the isolated chart and polish the existing feature components. Update usage documentation and complete the targeted checks before release.

Rollback removes the chart dependency/renderer and restores the previous shell/theme while preserving the existing domain and ViewModel interfaces. There is no stored data format to migrate or roll back.
