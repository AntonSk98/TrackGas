# Tasks

## 1. Page and history reflow

- [x] 1.1 Update `TrackerScreen.kt` and `TrackerPager.kt` so title/brand, two 48 dp toolbar actions, tabs, and centered bounded pages fit the available window; verify both tabs and the complete options menu at 320 x 568 and 568 x 320 dp in English/German with 200% fonts, without hiding Add or options.
- [x] 1.2 Replace the non-wrapping value/meter arrangement in `ReadingHistory.kt` with compact content that reflows when necessary; verify full-precision values, localized timestamps, long notes, and multiple meter identities remain readable, with unchanged tap/long-press accessibility actions.
- [x] 1.3 Check Dashboard chips, headings, totals, and covered rows in `DashboardContent.kt` under narrow constraints and fix only observed clipping; verify empty and populated week/month/year views retain their existing content order and all period navigation remains reachable.
- [x] 1.4 Record page/history outcomes and reproduction settings with the change's acceptance evidence; identify actual window bounds, locale, font scale, and sample content without claiming unexercised device coverage. Keep the README minimal.

## 2. Forms, pickers, and modal reachability

- [x] 2.1 Update `ReadingFormLayout.kt` to choose scrolling from actual safe/IME-adjusted bounds rather than only `screenHeightDp`; verify every Add/Edit field, timezone, inline error, Save, and Cancel remains reachable in portrait and landscape with the keyboard open at 200% fonts, while outside-tap and pending guards remain unchanged.
- [x] 2.2 Update date/time picker presentation in `ReadingForm.kt` to fit measured inner constraints, using a time-input fallback where neither clock layout fits; verify date errors, picker actions, selected date/time, and parent drafts survive layout changes without accidental confirmation or dismissal.
- [x] 2.3 Bound location-dialog content and suggestion scrolling in `MeterLocationDialog.kt`, including minimum 48 dp suggestion targets; verify the query, prompt, every suggestion, resolving/error feedback, and Save/Cancel/Later can be reached with the keyboard open in a 568 x 320 dp window at 200% fonts.
- [x] 2.4 Make variable-length content in `LanguageDialog.kt`, `DeleteReadingDialog.kt`, `CsvImportDialog.kt`, and export error feedback in `TrackerScreen.kt` scroll safely as needed; verify every applicable title, message, enabled action, and selection remains reachable in constrained English/German windows.
- [x] 2.5 Exercise `ReplacementForm.kt` and `PurgeTrackingDataDialog.kt` against the same constraints and correct only remaining layout failures; verify long explanations, pending/failure feedback, explicit confirmation, cancellation, and dismissal protections still work.
- [x] 2.6 Add JUnit cases for any extracted pure constraint/picker policy and run them with `.\gradlew.bat :app:testDebugUnitTest --tests "*<ActualPolicyTestName>"`; record keyboard/picker/modal outcomes with the change's acceptance evidence.

## 3. Responsive charts

- [x] 3.1 Update `ConsumptionChart.kt` to derive horizontal tick density and shared value-axis reservation from measured width and localized font/label sizes, retaining every projected bucket and both plots' alignment; verify week/month/year charts at 320 dp width and 200% fonts have readable axes and inspection feedback.
- [x] 3.2 Make consumption/temperature plot heights account for readable axis content within bounded sizing, preserving vertical page scrolling and the shorter temperature hierarchy; verify narrow landscape and wide tablet windows retain usable plots and access to period navigation and numeric rows.
- [x] 3.3 Add JUnit regression cases for chart presentation-policy boundaries, font scale, and label space; run those cases together with existing chart/projection/format tests using `.\gradlew.bat :app:testDebugUnitTest --tests "*<ActualChartLayoutTestName>" --tests "*ChartProjectionTest" --tests "*ReadingFormatTest"`, and record chart outcomes with the change's acceptance evidence. Substitute the actual added class name for the selector placeholder.
- [x] 3.4 Shorten the actual plotted areas following user feedback, not just restore the old container heights; use compact 180/80 dp baselines with measured axis growth, retain zero-margin compact tooltips, and cover the plot-height reduction and accessibility bounds with chart policy regressions.

## 4. Rotation, resizing, and integrated acceptance

- [x] 4.1 Exercise rotation and the 960 x 640 -> 480 x 640 -> 320 x 480 dp resize sequence with a non-current period, independently scrolled tabs, an unsaved reading draft, and open date/time pickers; fix only state loss exposed by these transitions and verify selection, reading position, draft fields, errors, and picker choices are retained without saving.
- [x] 4.2 Exercise the same transitions during save/delete/replacement/purge/import, including pending and failed states; verify exactly one operation completes, no transition authorizes a mutation, and existing session behavior remains intact using `.\gradlew.bat :app:testDebugUnitTest --tests "*TrackerStoreOwnerTest" --tests "*TrackerViewModelTest"`.
- [x] 4.3 Complete the design's entire phone/tablet/resizing matrix on API 35 in English/German at 100%/200% fonts, including all pages/modals, keyboard cases, long data, empty/error states, and gesture/three-button navigation; repeat the small-phone/tablet smoke cases on API 26 and record actual results or explicit unavailable environments with the change's acceptance evidence. Verify no covered case has inaccessible content or actions before declaring device coverage complete.
- [x] 4.4 Build the integrated app with `.\gradlew.bat :app:assembleDebug` and verify normal portrait behavior, chart coverage/values, edit/delete gestures, CSV picker return, and fresh-process Dashboard/current-week defaults remain unchanged; distinguish representative coverage from an all-device guarantee.

Acceptance closure (2026-11-10): the user confirmed completing manual testing and
explicitly requested task completion and archival. Task 4.3 is closed on that
user-reported acceptance; no additional device-by-device results were supplied.
The acceptance sign-off is not an all-device guarantee. README acceptance reports
were subsequently removed at the user's request to keep it minimal.
