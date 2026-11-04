## Context

Observed: all UI text already lives in `res/values/strings.xml` and is read via `stringResource`; formatting uses `LocalConfiguration.current.locales[0]`, so locale-sensitive dates and numbers follow the active app locale automatically. `MainActivity` extends `ComponentActivity` and the theme derives from the platform `Theme.Material.Light.NoActionBar`. The options menu is in `TrackerScreen.kt`.

## Goals / Non-Goals

**Goals:** English default, German option, persistent in-app switch, all text in resource files, minimal new code.

**Non-Goals:** more languages, translating CSV headers, DataStore preferences, storing language in Room.

## Decisions

1. **Per-app locale via `AppCompatDelegate.setApplicationLocales`.** It persists the choice (platform storage on API 33+, AppCompat-managed storage via `AppLocalesMetadataHolderService` with `autoStoreLocales` on API 26-32), recreates the activity, and integrates with system per-app language settings. Alternative: own SharedPreferences/DataStore plus `attachBaseContext` wrapping - more code and re-implements the platform feature.
2. **AppCompat requirements.** Pre-33 support needs `AppCompatActivity` and an AppCompat theme parent (`Theme.AppCompat.Light.NoActionBar`), keeping existing items. Visual regression check is part of the tasks.
3. **English default regardless of system language.** `TrackGasApplication.onCreate` calls `setApplicationLocales(en)` only when `getApplicationLocales().isEmpty`. Afterwards the stored value is authoritative. Alternative (follow system) rejected per requirement.
4. **Single abstraction.** `AppLanguage` enum (`ENGLISH("en", R.string.language_english)`, `GERMAN("de", R.string.language_german)`) plus `AppLanguages` helper with `current()` and `select(language)`. UI depends only on it. Language names are shown in their own language ("English", "Deutsch") and are `translatable="false"`.
5. **UI.** A "Language" item (with a globe/translate icon) in the existing options menu opens a small dialog with radio options for English and Deutsch; the current one is selected. Selecting applies immediately and closes the dialog. Dialog visibility is local UI state, not tracker state. Disabled/enabled follows the same `mutationsEnabled` rule as the menu.
6. **Resources.** German strings in `values-de/strings.xml` with identical keys, preserving format placeholders (`%1$s`, `%1$d`) and `translatable="false"` entries. `res/xml/locales_config.xml` lists `en` and `de`, referenced by `android:localeConfig`. Any literal user-facing text found in Kotlin is moved to resources. Exception messages in `require`/`check` are not user-facing.
7. **Process-local tracker retention** keeps working: activity recreation on language change is the same configuration-change path the retained tracker session already supports.

## Risks / Trade-offs

- Switching theme parent to AppCompat could subtly change dialog/widget styling -> compare screens; Compose Material3 content is unaffected.
- Dropdown/dialog open at switch time is lost on recreation -> acceptable; language dialog closes on selection anyway.
- Untranslated or mis-sized German text (longer words) -> existing wrapping/200% font requirements apply; verify on compact screens.
- A string-parity test (every `values/` key has a `values-de/` counterpart and matching placeholders) prevents drift.

## Open Questions

None blocking. Assumption: German wording uses informal-neutral imperatives ("Speichern", "Abbrechen").
