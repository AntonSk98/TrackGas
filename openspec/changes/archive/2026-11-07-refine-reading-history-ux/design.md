# Design

## Context

See `proposal.md` for motivation and the capability delta specs for user-visible requirements. The tracker stores readings in Room, associates each reading with a meter installation, and calculates consumption only between readings from the same installation. Replacement currently adds a linked replacement record plus final/initial readings and protects those endpoints from deletion. CSV currently has three required fields, History is already a `LazyColumn`, and the reminder uses the battery-shaped brand vector as its small notification icon.

## Goals / Non-Goals

**Goals:**
- Keep meter histories independent while allowing Replace meter to start a new empty current installation.
- Persist edit-only notes through Room and CSV without changing the existing Add form.
- Apply concise, localized copy and consistent compact UI without weakening accessibility or tooltip inspection.

**Non-Goals:**
- Add address geocoding; the location picker continues to search cities and places.
- Add History pagination; the existing lazy list already composes visible entries on demand.
- Migrate data from unreleased local development databases.

## Decisions

1. **Represent replacement as a new independent installation, not boundary readings.** Keep installation IDs and per-installation chronology, but remove replacement records, endpoint references, automatic zero readings, and endpoint deletion protection. Replace meter will atomically create an empty installation dated today. Backdated readings remain assigned according to installation start dates; readings in different installations never form consumption intervals. Determine the current installation from the latest of its start date and latest reading date, with start date and stable ID as tie-breakers, so a newly created empty installation can be current without overriding a later-dated imported history.

   Alternatives considered: keep linked replacement metadata without protected readings, or continue creating a zero baseline and merely hide its label. Both retain state and validation that the requested independent histories do not need, and the latter still creates a synthetic reading.

2. **Persist notes as optional reading data and use one strict CSV format.** Add a nullable note to the reading model and Room row. Only Edit exposes the field; blank text becomes absent. Export a fourth `note` field, including an empty field when absent. Import only the four-column format; reject the previous three-column format. Keep the CSV delimiter, locale-neutral values, stable meter IDs, and quoting rules.

   Alternatives considered: keep notes outside the reading record or omit them from CSV. Either risks losing the association or dropping notes during export/import.

3. **Keep the existing Compose interaction patterns and reduce presentation only.** Reuse the current Add/Edit dialog, date picker, confirmation flow, localized string resources, and lazy History list. Edit adds one optional note field; History places it after the date and value. Keep the meter identifier inline with the value only when multiple installations exist. Do not introduce pagination or a new navigation state.

4. **Simplify labels while retaining assistive descriptions.** Remove the on-screen local-storage notice, use the requested shorter German and English copy, label the location input “Standort des Zählers” / “Meter location,” and label reading actions “Zählerstände exportieren/importieren” / “Export/Import readings.” The date picker has no redundant visible title; keep a meaningful accessibility label and use “OK” for its confirmation action.

5. **Use a notification-specific monochrome mark.** Provide a small monochrome gas-tracking glyph for the notification small icon rather than reusing the multicolor/battery-shaped brand vector or adaptive launcher icon. Android's notification icon mask and monochrome treatment otherwise reduce the current brand mark to an ambiguous battery silhouette.

6. **Align chart headings without redesigning inspection.** Apply the same typography and color hierarchy to the consumption and temperature chart headings. Reduce the gap between each heading and its chart using the existing layout. Preserve Vico's current tooltip inspection and gestures; allow overlap only if it requires no new positioning or interaction logic.

## Risks / Trade-offs

- [Removing replacement links changes the tracker schema and loses unreleased local history on update] → No released user data exists; use a clean install or clear local app data when validating this change, as approved by the user. Do not add a silent destructive migration.
- [A new empty installation must remain the current meter and backdated readings must still map to the appropriate history] → Cover replacement, imported histories, current-meter selection, and backdated add behavior with domain and repository tests.
- [Existing three-column CSV files do not contain notes] → Require the current four-column header and reject the previous format rather than maintaining a compatibility path.
- [A shortened History layout or chart spacing could obscure meter identity or tooltip content] → Keep meter identity inline for multi-meter trackers, test enlarged text, and retain tooltip visibility and chart gestures.
- [Notification small icons are monochrome and system-masked] → Verify the dedicated glyph in both the status bar and notification shade on supported Android versions.

## Migration Plan

No local database migration is planned because the app has not been released and the user accepts reinstalling locally. Bump the Room schema as required by the removed replacement table and added note field; validate from a clean database. Existing three-column CSV files are not supported; files must use the four-column format.
