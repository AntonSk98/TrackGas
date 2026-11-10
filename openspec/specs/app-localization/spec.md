# app-localization Specification

## Purpose

TBD: Update Purpose after archive.

## Requirements

### Requirement: Supported languages and default
The app SHALL support English and German. When no explicit app-language choice exists, the interface SHALL follow Android's resource-language resolution for the system locale preferences. When no supported language matches, the interface SHALL fall back to English. A stored explicit language choice SHALL take precedence over the system language.

#### Scenario: First launch on a German device
- **WHEN** the user launches the app for the first time with the system language set to German and no explicit app-language choice
- **THEN** the interface is displayed in German

#### Scenario: First launch on an English device
- **WHEN** the user launches the app for the first time with the system language set to English and no explicit app-language choice
- **THEN** the interface is displayed in English

#### Scenario: Regional German system language
- **WHEN** no explicit app-language choice exists and the system language is a regional German locale such as de-AT or de-CH
- **THEN** the interface uses German resources

#### Scenario: Unsupported system language
- **WHEN** no explicit app-language choice exists and none of the system locale preferences matches English or German
- **THEN** the interface is displayed in English

#### Scenario: Supported secondary system locale
- **WHEN** no explicit app-language choice exists and Android resolves the system locale preferences to German despite an unsupported primary locale
- **THEN** the interface is displayed in German

#### Scenario: Existing explicit choice overrides the system
- **WHEN** a stored explicit English choice exists and the system language is German
- **THEN** the interface is displayed in English

#### Scenario: System language changes without an explicit choice
- **WHEN** no explicit app-language choice exists and the user changes the system language from German to English and relaunches the app
- **THEN** the interface is displayed in English

### Requirement: In-app language selection
The options menu SHALL contain a "Language" action that opens a dialog offering "English" and "Deutsch", marking exactly the language currently displayed, including a system-inherited language or English fallback. Selecting one SHALL apply it immediately to all visible text and locale-formatted dates and numbers, close the dialog, and preserve readings, selected tab, and selected period.

#### Scenario: Switch to German
- **WHEN** the user opens Options, chooses Language, and selects Deutsch
- **THEN** all interface text, dates, and numbers appear in German and the dialog closes

#### Scenario: Switch back to English
- **WHEN** German is active and the user selects English
- **THEN** the interface returns to English

#### Scenario: Dismiss without change
- **WHEN** the user dismisses the language dialog with Back or an outside tap
- **THEN** the language and all tracker data are unchanged

#### Scenario: Accessible language option
- **WHEN** accessibility services focus the Language action or its choices
- **THEN** each exposes a meaningful label, selected state, and at least a 48 dp touch target

#### Scenario: Inherited German is selected
- **WHEN** the user opens the language dialog on a fresh installation displaying German without an explicit app-language choice
- **THEN** Deutsch is selected and English is not selected

#### Scenario: English fallback is selected
- **WHEN** the user opens the language dialog while the interface displays English because no supported system language matches
- **THEN** English is selected and Deutsch is not selected

#### Scenario: Explicit choice remains selected after restart
- **WHEN** the user selects English on a German device, restarts the app, and reopens the language dialog
- **THEN** the interface is English, English is selected, and Deutsch is not selected

#### Scenario: Selecting the inherited language makes an explicit choice
- **WHEN** the interface inherits German from the system and the user selects Deutsch in the language dialog before changing the system language to English and restarting the app
- **THEN** the interface remains German and the language dialog marks Deutsch

#### Scenario: Opening and dismissing does not create an override
- **WHEN** the user opens and dismisses the language dialog while German is inherited, then changes the system language to English and restarts the app
- **THEN** the interface and the selected language option are English

### Requirement: Persistent language choice
The selected language SHALL persist across app restarts and process death using Android's per-app language storage, not the reading database. Purging tracking data SHALL NOT reset the language.

#### Scenario: Restart after choosing German
- **WHEN** the user selects Deutsch and later relaunches the app
- **THEN** the interface is still German

#### Scenario: Purge data
- **WHEN** the user purges all tracking data while German is selected
- **THEN** the language remains German

### Requirement: Centralized translatable text
All user-visible text SHALL be defined in Android string resources: English in `values/strings.xml` and German in `values-de/strings.xml`, with identical keys and format placeholders. Kotlin sources SHALL NOT contain literal user-facing text. Language names SHALL be shown in their own language and not translated.

#### Scenario: Resource parity
- **WHEN** the resource files are checked
- **THEN** every translatable English key has a German counterpart with the same placeholders

#### Scenario: Change a label
- **WHEN** a label changes
- **THEN** it is edited only in the string resource files

### Requirement: Language-neutral data exchange
CSV export and import SHALL NOT depend on the selected language. Headers and stored values SHALL be identical in English and German; only messages shown to the user are translated.

#### Scenario: Export in German
- **WHEN** the user exports CSV while German is active
- **THEN** the file header and values match those exported in English, and the result can be imported in either language
