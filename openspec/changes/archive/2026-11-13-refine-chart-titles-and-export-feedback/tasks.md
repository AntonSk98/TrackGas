# Tasks

## 1. Export confirmation

- [x] 1.1 In `TrackerScreen.kt`, replace the success `ResponsiveDialog` icon box with a content-sized, dismissible, text-only "Saved"/"Gespeichert" window (no icon, no button, not full width); remove the unused `ic_success` drawable/test tag if nothing else uses them; verify via compilation and review of the dialog sizing.

## 2. Chart titles and default

- [x] 2.1 Update `chart_absolute_title` to "Interval consumption"/"Intervallverbrauch" and `chart_daily_title` plus daily description/unavailable strings to "Daily projection"/"Tagesprojektion" wording in `values/strings.xml` and `values-de/strings.xml` (informal Du-form where addressing the user); update `LocalizedStringResourcesTest` expectations.
- [x] 2.2 Change `TrackerState.chartMode` default to `ABSOLUTE`; update `TrackerViewModelTest` and any chart/dashboard tests assuming a daily default.

## 3. Validation

- [x] 3.1 Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug`; no emulator tests.
