# Design

## Context

See `proposal.md` for motivation and the intentional replacement of the prior English-only first-launch contract.

`AppLanguages.current()` reads the first entry in `AppCompatDelegate.getApplicationLocales()` and returns English when that list is empty. An empty list means no explicit override, not necessarily an English interface. `LanguageDialog` uses this result for each radio row without consulting the configuration used to render its strings.

`TrackGasApplication.onCreate()` also tries to set English whenever the delegate's locale list is empty. That initialization can interfere with system inheritance and is not a reliable indication that persisted locales have finished restoring. `MainActivity` is an `AppCompatActivity`; the manifest already enables AppCompat locale auto-storage and declares English and German in `locales_config.xml`. The app supports API 26 onward, spanning AppCompat-managed and Android 13+ platform-managed per-app locales.

The existing JUnit setup can cover pure locale mapping, and `LocalizedStringResourcesTest` already covers resource parity. No Android instrumentation or Compose test dependencies are configured.

## Goals / Non-Goals

**Goals:** Use the same activity-scoped locale configuration for displayed resources and selected-language state; keep inherited locale state distinct from explicit persistent choices; avoid initialization that races locale restoration.

**Non-Goals:** Introduce another preference store, migrate or reset existing overrides, persist a detected system language, or add testing libraries just for this fix.

## Decisions

### 1. Derive selected language from the effective UI configuration

Pass locale information from Compose's `LocalConfiguration.current` to the language-resolution helper and compute the selected language once per dialog composition. Resolve the first supported language in the effective configuration's locale preferences, matching by language code so regional German variants map to German; use English when none matches. Keep the mapping independent of Android APIs where practical so it can be covered by the existing JVM test setup.

The helper is a read-only projection of the configuration, not a second language authority. Observe `LocalConfiguration` rather than remembering a process-wide result so configuration updates and activity recreation refresh the selection.

Alternatives rejected: treating an empty explicit locale list as English reproduces the defect; reading `Locale.getDefault()` can disagree with activity resources; implementing a separate raw-system-locale resolver risks diverging from Android's supported-resource matching.

### 2. Leave automatic language selection to Android

Remove the startup English override and any import made unused by that removal. Do not write an app locale at launch, when opening the dialog, or when dismissing it. Without an override, Android chooses among the existing resource translations and falls back to default English resources.

Keep `AppLanguages.select()` backed by `setApplicationLocales()`, including when the chosen row is already selected through system inheritance. A deliberate selection must establish an explicit preference. Retain the current dialog close behavior and Android-managed persistence.

Alternatives rejected: persisting the detected German locale would stop following future system changes; adding a System option expands the requested UX; clearing overrides at startup could erase genuine user choices.

### 3. Preserve existing explicit choices without migration

An existing English override is indistinguishable from one written by old startup logic. Preserve both English and German overrides rather than guessing their origin. Removing startup initialization lets AppCompat or the platform restore existing preferences through its normal activity lifecycle.

### 4. Separate deterministic mapping coverage from Android lifecycle verification

Add a focused JUnit test for the locale-to-language policy, including German regional variants, English, unsupported-only preferences, and unsupported-primary/German-secondary preferences. Run it together with existing localization resource tests.

Verify the end-to-end behavior on both a pre-33 Android environment and Android 13 or later: fresh installation, inherited German/English, unsupported-language fallback, explicit selection and process restart, and system-language changes with and without overrides. Verify exactly one selected radio option, no override created by dismissal, and preservation of tracker state on language selection. These checks cover resource resolution and locale restoration that pure JVM mapping tests cannot prove.

## Risks / Trade-offs

- [Locale-list ordering can differ from a naive primary-system-locale assumption] -> Use the effective UI configuration, cover secondary supported locales, and compare the selected row to actual rendered resources on device.
- [AppCompat and Android 13+ restore locales through different paths] -> Avoid startup writes and verify both API families, including a stored German preference on an English device.
- [Some existing installations retain an English override created by old code] -> Preserve it to avoid resetting intentional choices; users can select German explicitly.
- [JVM tests do not exercise Android resource and lifecycle behavior] -> Require focused device/emulator acceptance checks without adding unrelated test infrastructure.

## Migration Plan

No schema, data, or locale-storage migration is required. Ship the code change using existing AppCompat storage and locale resources. Fresh installations without an override inherit the system-resolved language; existing explicit choices remain unchanged. A rollback restores the old initialization and selector behavior without altering tracking data.
