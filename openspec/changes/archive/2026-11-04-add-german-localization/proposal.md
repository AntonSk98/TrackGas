## Why

TrackGas is English-only. German-speaking users need the UI in German, and the choice must be explicit, persistent, and independent of the Room reading database.

## What Changes

- Add German (`de`) translations alongside English (`en`, the default and fallback language).
- Add a "Language" item to the existing options (gear) menu that opens a dialog with English and Deutsch choices; selecting one applies immediately and survives app restarts.
- Persist the choice through Android's per-app language mechanism (AppCompat `AppCompatDelegate.setApplicationLocales`, backed by the platform on Android 13+ and AppCompat's own storage on older versions), not Room.
- On first launch, when no language has been chosen, use English regardless of the system language.
- Keep every user-visible string in Android string resources (`values/strings.xml` for English, `values-de/strings.xml` for German); no literal UI text in Kotlin.
- Declare supported languages in `locales_config.xml` so Android's per-app language settings list English and German.
- Migrate the activity to `AppCompatActivity` with an AppCompat-based theme and add the `androidx.appcompat` dependency.

## Capabilities

### New Capabilities
- `app-localization`: supported languages, default language, in-app language selection, persistence, and string-resource centralization.

### Modified Capabilities
- `app-shell`: the shared options menu gains a Language option (it currently lists only implemented actions).

## Impact

- `app/build.gradle.kts`: add `androidx.appcompat`.
- `AndroidManifest.xml`, `res/xml/locales_config.xml`, `res/values/themes.xml`.
- `MainActivity.kt` (base class), `TrackGasApplication.kt` (first-launch default), `TrackerScreen.kt` (menu item), new language dialog and a small `AppLanguage` abstraction.
- `res/values/strings.xml` (new language strings), new `res/values-de/strings.xml`.
- No database, CSV format, or data-migration changes. CSV headers and stored values remain language-neutral.
- Out of scope: languages other than en/de, translating CSV headers, right-to-left layouts.
