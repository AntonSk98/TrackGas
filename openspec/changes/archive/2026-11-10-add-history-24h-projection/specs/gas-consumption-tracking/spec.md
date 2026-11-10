# Spec Delta

## ADDED Requirements

### Requirement: Timestamp-normalized 24-hour history estimate
For each consecutive same-installation reading pair, History SHALL show on the later reading an approximate consumption per 24 hours equal to the meter difference multiplied by 86,400 seconds and divided by actual elapsed seconds between recorded observation instants. This SHALL apply to every positive duration, including intervals shorter than 24 hours. Calculation SHALL retain decimal precision until presentation and SHALL NOT use rounded hours or calendar-day counts.

#### Scenario: Thirty-nine-hour interval
- **WHEN** same-installation readings are 1000 on November 6, 2026 at 08:00 and 1013 on November 7 at 23:00 in the same timezone without an offset change
- **THEN** the later reading shows an approximate 24-hour consumption of 8 cubic metres, using 39 elapsed hours rather than one calendar day

#### Scenario: Shorter than 24 hours
- **WHEN** consecutive readings increase by 3 cubic metres over 12 elapsed hours on different recorded dates
- **THEN** the later reading shows an approximate consumption of 6 cubic metres per 24 hours

#### Scenario: Exactly 24 hours and zero consumption
- **WHEN** an interval spans exactly 24 hours
- **THEN** its 24-hour estimate equals its meter difference, including a visible zero when the cumulative readings are equal

#### Scenario: Preserve partial hours
- **WHEN** readings increase by 7 cubic metres over 10 hours and 30 minutes on different recorded dates
- **THEN** the later reading's estimate is 16 cubic metres per 24 hours, without truncating elapsed time to whole hours

#### Scenario: Daylight-saving transition
- **WHEN** consecutive readings increase by 23 cubic metres from March 28, 2026 at 08:00 to March 29 at 08:00 in Europe/Berlin
- **THEN** History shows an approximate consumption of 24 cubic metres per 24 hours using the actual 23-hour interval

#### Scenario: Different recording timezones
- **WHEN** consecutive same-installation readings retain different recording timezones
- **THEN** normalization uses the elapsed time between their absolute observation instants, not subtraction of their displayed local times

### Requirement: Installation-local estimate availability
A 24-hour estimate SHALL exist only when a reading has a chronological predecessor in its own installation. The first reading in each installation SHALL have no estimate or reserved estimate placeholder, including a replacement-start reading that contributes opening consumption to Dashboard. Estimates SHALL NOT bridge installations, create synthetic readings, or extrapolate values onto unobserved dates.

#### Scenario: Empty history and baseline
- **WHEN** the tracker has no readings or a single baseline reading
- **THEN** no 24-hour estimate is displayed and existing empty-history or baseline presentation remains available

#### Scenario: Independent meter replacement
- **WHEN** a new installation has its first reading and another installation already has readings
- **THEN** the new installation's first reading has no estimate even if it is on the replacement date, and its second reading uses only its own predecessor

### Requirement: Derived history estimate freshness
History estimates SHALL derive from the same committed tracker snapshot as their readings and refresh after committed additions, meter-value or observation-time edits, deletions, imports, and reloads. Failed or cancelled mutations SHALL retain prior estimates. Estimates SHALL remain independent of Dashboard period selection and SHALL NOT be persisted or added to CSV exports.

#### Scenario: Insert and delete a middle reading
- **WHEN** a backdated reading is committed between two readings in an installation
- **THEN** the old estimate is replaced by estimates for the two resulting consecutive pairs, and deleting the middle reading restores the estimate for the remaining pair

#### Scenario: Edit time without changing calendar consumption
- **WHEN** a committed observation-time correction changes an interval's elapsed duration without changing dates or meter values
- **THEN** affected History estimates refresh while Dashboard gas consumption and calendar allocation remain unchanged

#### Scenario: Import, restart, and navigation
- **WHEN** valid timestamped readings are imported or restored offline after restart, or the selected Dashboard period changes
- **THEN** History estimates reflect the committed readings regardless of the selected Dashboard period without requiring extra stored or exported fields

#### Scenario: Failed or cancelled edit
- **WHEN** an edit fails to commit or the user cancels it
- **THEN** History retains its estimates from the last committed tracker snapshot

## MODIFIED Requirements

### Requirement: Separate dashboard and history tabs
The app SHALL launch on Dashboard and offer History separately. Dashboard SHALL show period controls and consumption, not reading history. History SHALL show all readings newest first with Edit/Delete and optional per-reading 24-hour estimates, not period controls or Dashboard consumption rows. Add SHALL be available on both tabs. Switching tabs and reading mutations SHALL preserve the selected period; mutations SHALL preserve the selected tab. Tab selection SHALL survive configuration changes and reset to Dashboard in a fresh process.

#### Scenario: Manage readings without cluttering the dashboard
- **WHEN** the user switches from Dashboard to History
- **THEN** all entered readings and their Edit/Delete actions are shown with eligible per-reading 24-hour estimates but without Dashboard consumption rows or period controls

#### Scenario: Return to the selected dashboard period
- **WHEN** the user selects a past period, switches to History, edits a reading, and returns to Dashboard
- **THEN** Dashboard still shows that selected period with recalculated consumption, and no reading history is shown there

#### Scenario: Preserve tab selection on rotation
- **WHEN** the user rotates the device while viewing History
- **THEN** History remains selected and the same entered readings remain available

### Requirement: Compact reading history entries
History SHALL show each reading in two logical rows: its recorded local date and observation time, and its value with any 24-hour estimate as secondary metadata. When multiple installations exist, a compact meter identifier SHALL remain available with the value. A saved note SHALL appear as a third logical row; a reading without a note SHALL NOT reserve a note row. Value-row metadata SHALL wrap on narrow screens or with enlarged text without obscuring values or actions.

#### Scenario: History without notes
- **WHEN** History contains a reading without a note
- **THEN** the entry displays its localized recorded date/time and value in two logical rows without repeating "meter" or "reading" labels, with an eligible 24-hour estimate in the value row

#### Scenario: History with a note
- **WHEN** History contains a reading with a saved note
- **THEN** its recorded date/time, value with any eligible estimate, and note appear in three logical rows

#### Scenario: Distinguish independent meters
- **WHEN** History contains readings from more than one installation
- **THEN** each value remains associated with a compact meter identifier without adding a separate repeated role row

#### Scenario: Minimal localized estimate
- **WHEN** History displays a 24-hour estimate
- **THEN** it uses a compact approximation marker and cubic-metres-per-24-hours unit in smaller secondary text, locale-aware decimal formatting with existing calculated-value rounding, and English/German localized accessible wording that identifies an estimated 24-hour consumption

#### Scenario: Narrow or enlarged layout
- **WHEN** an entry's value, meter identifier, and estimate do not fit on one visual line
- **THEN** the metadata wraps without clipping or replacing the raw reading, note, or existing tap-to-edit and long-press-to-delete actions

### Requirement: Preserve calendar consumption semantics with timestamps
Observation times SHALL determine weather windows and History's 24-hour estimates, not gas calendar allocation. Consecutive readings SHALL retain the existing earlier-date-exclusive, later-date-inclusive allocation and equal distribution across covered dates. Replacement-start opening consumption SHALL retain its existing behavior and SHALL NOT create a weather interval or 24-hour estimate without two actual readings.

#### Scenario: Morning measurements
- **WHEN** readings are taken on November 6 at 08:00 and November 7 at 08:00
- **THEN** their gas difference remains attributed to November 7 while its temperature represents the actual timestamp interval

#### Scenario: Correct only an observation time
- **WHEN** a valid observation-time edit leaves dates and meter values unchanged
- **THEN** gas totals and their calendar allocation remain unchanged while affected temperature means are invalidated and History's affected 24-hour estimates are recalculated

#### Scenario: Opening consumption without an interval
- **WHEN** a replacement meter has only its first reading on its start date
- **THEN** opening gas consumption remains available but no temperature or 24-hour estimate is invented for that contribution
