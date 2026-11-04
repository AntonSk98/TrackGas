# Design

## Context

See proposal.md for motivation and specs/reading-data-exchange/spec.md for the observable contract. The app has a CSV writer and a Room-backed repository. CSV rows use meter_id, date, and meter_value. Room identifies readings by (installationId, date), stores persistent installation IDs, and stores replacement records with foreign keys to the old-final and new-initial reading rows. After import removes replacement links, current-meter selection is derived from reading dates.

## Goals / Non-Goals

**Goals:**
- Import readings only into installations whose UUIDs already exist in the current tracker.
- Replace complete histories for meter IDs present in the file, leaving omitted installations unchanged.
- Give users a preview and make confirmation, validation, and persistence all-or-nothing.
- Recreate included installations and readings with the same UUIDs; select the latest-reading installation as current.
- Remove replacement relationships involving imported installations and validate histories independently per meter.
- Keep preview and commit atomic and avoid exposing exception details.

**Non-Goals:**
- Creating or mapping new meter UUIDs, restoring a tracker on a fresh install, or importing a complete database backup.
- Merging individual CSV rows into existing readings.
- Changing the CSV export format or inventing readings/replacement endpoints absent from the file.

## Decisions

### Use the document picker and existing CSV contract
Add an Import CSV action beside Export CSV and use Android's document picker so the user chooses a file. Parse the existing UTF-8, semicolon-delimited header and its quoted fields; do not add a new format or a parser dependency unless implementation proves the current format cannot be handled safely with a small tested parser. Read and parse off the main thread. A picker cancellation is a no-op.

### Stage, validate, preview, then commit
Read a consistent committed tracker snapshot and parse the entire file into a staged candidate before any writes. Normalize duplicate rows by the pair (meter UUID, date), keeping the last occurrence in file order. Check every UUID against the snapshot's known installations before presenting a preview; any unknown UUID rejects the entire file. Recreate installations and readings only for included UUIDs, retaining readings for omitted installations. Remove replacement records that reference an included UUID, including links to omitted installations; do not require or synthesize boundary readings. Validate date uniqueness and nondecreasing values independently within each meter, validate unaffected replacement components, and reject candidate consumption intervals that overlap across independent meters.

The preview shows one concise warning that existing meter data will be replaced with CSV values. It does not display meter UUIDs or per-meter counts. On confirmation, the repository re-reads and validates against current data inside its existing serialized transaction. If the tracker changed since preview, invalidate the preview and require the user to review it again rather than overwriting newer UI edits. The installation with the latest reading date becomes current; installation start date breaks ties and selects a meter when there are no readings.

### Preserve Room identity and replacement integrity
Keep each imported InstallationEntity UUID, but recreate its installation row and readings from the CSV. Generate new ReadingEntity identities. Inside one Room transaction, delete replacement rows that reference affected UUIDs first to satisfy foreign keys, then delete affected readings and installations, recreate the installations with their existing UUIDs, and insert imported readings. Keep omitted installations and readings. Any replacement record touching an imported UUID is removed even when its other installation is omitted; no endpoint readings are synthesized. A failed transaction rolls back all changes.

The tracker validator must support multiple independent installation roots as well as unaffected replacement components. For a root with no incoming replacement, the former replacement start date does not constrain imported reading dates. A replacement component that remains in the tracker must still have valid endpoints and per-meter chronology. Select the installation with the latest reading date as current; use installation start date as a deterministic tie-breaker and empty-tracker fallback. No separate current-meter state is persisted.

### Keep parser errors typed and presentation-safe
Represent validation failures as typed import errors containing a stable code and optional one-based CSV row/field. Map them to short localized user-facing text and row/field context; do not display internal code names, Throwable.message, stack traces, or platform exception text. Keep the stable codes: CSV_HEADER_INVALID, CSV_ROW_INVALID, CSV_METER_ID_INVALID, CSV_METER_UNKNOWN, CSV_DATE_INVALID, CSV_VALUE_INVALID, CSV_VALUE_NEGATIVE, CSV_METER_HISTORY_INVALID, CSV_FILE_READ_FAILED, and CSV_IMPORT_FAILED. Unexpected failures use the safe fallback code; cancellation remains cancellation, not an import error.

### Preserve the tracker's current presentation state
Publish a successful committed snapshot through the existing repository/ViewModel flow. Keep the current tab and selected period; refresh the displayed readings and dashboard from the imported snapshot. Represent file selection, preview, pending commit, completion, and failure as explicit import state so rotation does not duplicate an import or show stale success.

## Risks / Trade-offs

- [Importing a meter removes its saved replacement relationship, including a link to an omitted meter] → State this in the concise preview warning and documentation; keep the omitted meter's readings unchanged.
- [Independent installations make a single connected chain an invalid tracker assumption] → Validate each meter's chronology independently, support multiple roots, and derive the current meter from reading dates with a deterministic start-date tie-break.
- [Independent histories can have overlapping usage intervals that the current dashboard cannot aggregate] → Reject those candidates with the safe history error before preview or commit.
- [Unknown UUIDs cannot be recreated safely from readings alone] → Reject the whole file with CSV_METER_UNKNOWN and row context; adding new IDs remains out of scope.
- [A file can be valid syntactically but conflict with current meter history] → Show a stable tracker-integrity error and make no changes; never partially import.
- [Import replaces user-entered history for listed meters] → Preview affected UUIDs and counts, explain replacement, require explicit confirmation, and retain omitted meters unchanged.

## Migration Plan

Room remains at schema version 2. Implement import as a repository transaction over existing tables, update tracker validation to allow independent roots, and update README/spec documentation to explain replacement-link removal and the non-backup limitation. Rollback is a normal app update rollback; no persisted schema or CSV format changes are introduced.
