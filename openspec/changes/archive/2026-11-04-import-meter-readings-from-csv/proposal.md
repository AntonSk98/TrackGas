# Proposal

## Why

TrackGas currently exports reading CSVs but cannot import them. Users need to update histories for meter installations already known to the app, while malformed rows and unknown meter IDs must be understandable without exposing raw system exception text.

## What Changes

- Add CSV import using the existing semicolon-delimited format and meter IDs.
- Accept only meter UUIDs already present in the app. If any CSV row references an unknown meter, reject the entire file and leave all data unchanged.
- Recreate each included meter and its complete reading history under the same UUID. Remove saved replacement relationships touching included meters; do not require or invent replacement-boundary readings. Keep omitted meters and their readings. The meter with the latest reading date becomes current; installation start date breaks ties.
- Reject a candidate when separate meter histories create overlapping consumption coverage, which the dashboard cannot safely aggregate.
- Show a concise replacement warning before confirmation, then apply all changes atomically.
- For duplicate rows with the same meter ID and date, use the last row in file order. Preserve same-date rows for different meter IDs.
- Show short localized errors with row/field context where available; keep error codes stable internally and never display raw exception messages.
- Update CSV documentation to describe import's known-meter replacement behavior and the replacement-history limitation.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- reading-data-exchange: add safe CSV import, known-meter validation, and per-meter replacement semantics to the existing CSV exchange behavior.

## Impact

CSV parsing and file selection, meter/reading validation and persistence, import preview/confirmation UI, localized error messages, tests, and README/spec documentation. The supplied CSV contains two meter IDs. If only one is already known to the app, the import must reject the whole file without partially replacing that meter's readings. Replacing a meter also removes replacement relationships involving it; omitted meter readings remain, but any relationship touching an imported meter does not. The installation with the latest reading date becomes current; overlapping consumption coverage across independent meter histories remains invalid. No Room schema change is required.
