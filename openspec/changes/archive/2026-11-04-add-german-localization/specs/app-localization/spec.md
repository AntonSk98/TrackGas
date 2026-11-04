## ADDED Requirements

### Requirement: Supported languages and default
The app SHALL support English and German. English SHALL be used on first launch regardless of the system language. Any unsupported language SHALL fall back to English.

#### Scenario: First launch on a German device
- **WHEN** the user launches the app for the first time with the system language set to German
- **THEN** the interface is displayed in English

#### Scenario: Unsupported system language
- **WHEN** no stored language exists and the system language is neither English nor German
- **THEN** the interface is displayed in English

### Requirement: In-app language selection
The options menu SHALL contain a "Language" action that opens a dialog offering "English" and "Deutsch", marking the active language. Selecting one SHALL apply it immediately to all visible text and locale-formatted dates and numbers, close the dialog, and preserve readings, selected tab, and selected period.

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
