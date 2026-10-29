# Design

## Context

`TrackerViewModel` publishes validated committed `TrackerData`, including readings, installations, and replacement boundaries. `Reading` holds a `LocalDate`, an installation ID, and a `BigDecimal`; History derives display meter numbers from installation start order. `TrackerScreen` exposes Tracker options on both tabs, while `MainActivity` connects the retained tracker to Compose.

## Goals / Non-Goals

**Goals:**
- Produce a consistent, precise export without network access or broad storage permissions.
- Keep export state and result handling safe across activity recreation.

**Non-Goals:**
- Import, validation of third-party CSV files, automatic backup, or reconstruction of all app state.
- Export of calculated daily consumption or dashboard selections.

## Decisions

1. **Use Android's create-document flow.** Add Export CSV to Tracker options and launch `ActivityResultContracts.CreateDocument` with `text/csv` and a suggested `.csv` filename. Write only to the returned URI through `ContentResolver`; do not request broad filesystem access. Cancellation is a no-op. Alternatives such as a fixed Downloads path or immediate sharing do not give the same destination control.

2. **Freeze a committed snapshot at the export action.** When ready, capture the immutable accepted `TrackerData` and retain it with a single export request through picker completion and writing. Later edits or purge do not alter this file; the export represents the committed snapshot accepted when requested, not necessarily subsequent changes. Prevent duplicate requests and associate callbacks and results with that request. If the process is killed and the retained request is lost, do not claim success or reconstruct data from a stale URI; allow a new export.

3. **Use the installation identity, not the reading ID or displayed meter number.** Write `meter_id;date;meter_value`, where `meter_id` is the stored installation UUID, including for the first installation. Preserve the assigned UUID on reopen and create a fresh UUID after purge. Sort by date and installation start order, including both replacement endpoints. Display meter numbers are derived presentation labels, not stable identifiers. The three columns enable useful grouping and leave a simple foundation for future import, but do not encode all identities and explicit replacement records required for a guaranteed lossless restore.

4. **Serialize without localization or floating point.** Use UTF-8, ISO dates, `BigDecimal.toPlainString()` for existing entered values, and semicolon-delimited CSV with standard quote escaping. Preserve fractional trailing zeros. Put serialization in a platform-independent function for deterministic unit tests; do not use localized display formatters. This avoids locale-dependent parse ambiguity.

5. **Keep I/O and UI outcomes separate.** Use a retained operation state and execute file I/O off the UI thread, close the stream before recording success, and expose localized success/failure feedback. Success is a dismissible dialog with an icon and no confirmation button. Handle expected destination and I/O errors without including meter data, URI details, or raw exception messages in user-facing diagnostics; cancellation and unexpected defects must not be disguised as success. Do not retry automatically.

## Risks / Trade-offs

- **A failed provider write can leave a partial destination file** → report failure, warn in documentation, and let the user choose a new destination; do not promise atomic output or delete an existing user-selected document.
- **External files can expose meter history** → make saving user initiated and document that the chosen location and its sharing permissions are outside app-private storage.
- **Future import may require richer metadata or conflict rules** → keep it a separate change and document that this CSV is not a full restore format.
- **Picker and write lifecycle events can be duplicated** → retain request state across recreation, use matching completion, and cover picker cancellation, rotation and failures with tests.

## Migration Plan

This change targets fresh installations; legacy data migration is not required. Uninstalling removes existing app data. Ship the action and documentation together; removing the feature later leaves already-created CSV files untouched.
