# Tasks

## 1. Effective language resolution and dialog state

- [x] 1.1 Replace the override-only `AppLanguages.current()` lookup in `AppLanguage.kt` with a read-only mapping from effective UI locale preferences to a supported `AppLanguage`. Match regional variants by language, honor locale ordering, and fall back to English. Add `AppLanguageTest` using existing JUnit dependencies to cover German, de-AT, de-CH, English, empty preferences, unsupported-only preferences, and unsupported-primary/German-secondary preferences; verify all mapping assertions pass.
- [x] 1.2 Update `tracking/LanguageDialog.kt` to observe `LocalConfiguration.current`, derive the selected language once per composition, and use that value for row and radio-button state without writing a locale on open or dismissal. Preserve existing labels, accessibility semantics, callbacks, and touch targets. Verify the app compiles and a German-rendered dialog marks only Deutsch.

## 2. System inheritance and explicit language persistence

- [x] 2.1 Remove the forced English locale initialization and unused import from `TrackGasApplication.onCreate()` while preserving unrelated startup work. Retain `AppLanguages.select()` and existing Android locale auto-storage; verify fresh installations inherit German or English and that restarting with a stored German choice on an English device does not reset that choice.
- [x] 2.2 Verify deliberate selection of the already inherited Deutsch row establishes a persistent explicit choice, while merely opening and dismissing the dialog does not. After changing the system language to English and restarting, confirm the former stays German and the latter follows English. Confirm an explicit English choice on a German device also persists and remains selected.

## 3. Cross-version integration

- [x] 3.1 Run `.\gradlew.bat :app:testDebugUnitTest --tests "com.trackgas.app.AppLanguageTest" --tests "com.trackgas.app.LocalizedStringResourcesTest"` and `.\gradlew.bat :app:assembleDebug`; verify locale-policy tests, resource parity, and the debug build succeed.
- [x] 3.2 On a pre-33 Android device/emulator and Android 13 or later, verify fresh German, English, regional German, unsupported-only, and unsupported-primary/German-secondary locale setups against `specs/app-localization/spec.md`. Confirm the rendered language and exactly one selected dialog option agree, including after process restart and system-language changes without an override.
- [x] 3.3 On both API families, switch between English and German and reopen the dialog; verify immediate text/date/number changes, dialog closure, correct selected state, accessibility selected semantics, and preservation of readings, selected tab, and selected period. Confirm purging tracking data does not reset an explicit language choice.

## Implementation status

The locale resolver, configuration-aware dialog, and removal of the startup English override are implemented. Targeted JVM coverage and the debug build succeed. On the available Android 35 emulator, the inherited English interface marks English, opening/dismissing the dialog leaves the override empty, and a German app locale renders German with only Deutsch selected. Selecting English from that dialog stores an English override that survives a process restart.

The user confirmed the fix works and explicitly requested completion of the remaining tasks and archival on 2026-11-10. Tasks 2.1, 2.2, 3.2, and 3.3 are closed based on that user acceptance, not additional agent-run cross-version checks. Agent-run device coverage was limited to the available English-system Android 35 emulator; no pre-33 device/emulator was available and the shared emulator's system language was not changed.
