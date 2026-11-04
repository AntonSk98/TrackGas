## 1. Setup

- [x] 1.1 Add `androidx.appcompat:appcompat` to `app/build.gradle.kts`
- [x] 1.2 Change theme parent in `themes.xml` to `Theme.AppCompat.Light.NoActionBar` keeping existing items; make `MainActivity` extend `AppCompatActivity`
- [x] 1.3 Add `res/xml/locales_config.xml` (en, de) and reference it via `android:localeConfig`; add AppCompat `AppLocalesMetadataHolderService` with `autoStoreLocales` to the manifest

## 2. Language logic

- [x] 2.1 Create `AppLanguage` enum and `AppLanguages` helper (`current()`, `select()`) wrapping `AppCompatDelegate`
- [x] 2.2 In `TrackGasApplication.onCreate`, apply English when `getApplicationLocales()` is empty

## 3. UI

- [x] 3.1 Add language icon drawable and a "Language" item to the options menu in `TrackerScreen.kt`
- [x] 3.2 Create `LanguageDialog` with English/Deutsch radio options, applying and closing on selection
- [x] 3.3 Add language strings (`language`, `language_english`, `language_german` as non-translatable names) to `values/strings.xml`

## 4. Text centralization

- [x] 4.1 Create `res/values-de/strings.xml` translating every key, preserving placeholders and `translatable="false"` entries
- [x] 4.2 Audit Kotlin sources for literal user-facing text and move any to string resources

## 5. Automated verification

- [x] 5.1 Add a unit test checking `values` and `values-de` key and placeholder parity
- [x] 5.2 Run the unit test suite and build
