# Proposal

## Why

On a fresh installation with German as the system language, the interface can appear in German while the language dialog marks English. The selector must reflect the language actually displayed, preserving the user's accepted system-language default rather than forcing English.

## What Changes

- Follow Android's supported resource-language resolution when no explicit app-language choice exists, with English resources as the fallback.
- Mark the effective interface language in the language dialog, including inherited German and regional German locales.
- Remove the unconditional English override at application startup while retaining Android-managed persistence for explicit language choices.
- Add regression coverage for inherited language, fallback, explicit overrides, and restart behavior.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `app-localization`: Replace the English-only first-launch requirement with system-derived language behavior and explicitly require the selector to match the effective interface language.

## Impact

Affected areas are `AppLanguage.kt`, `tracking/LanguageDialog.kt`, application locale initialization in `TrackGasApplication.kt`, and localization tests. Existing AppCompat locale storage and supported locale resources remain in use; no dependencies, database changes, or data migrations are planned.

The existing localization spec requires English on first launch regardless of the system language. This proposal intentionally replaces that requirement to honor the reported desired German first-launch behavior. Existing explicit English or German choices remain authoritative; no attempt will be made to infer whether an existing override was selected by a user or set by older startup code.

Adding a system-default option, adding languages, changing CSV behavior, and unrelated localization cleanup are out of scope.
