# Design

## Context

See `proposal.md` for motivation and the two delta specs for acceptance behavior.

- `Reading` and Room's `ReadingEntity` currently contain identity, date, and decimal meter value only. Database v1 has global date uniqueness.
- `ReadingValidation.kt` compares global chronological neighbours; `ReadingCollection.kt` rejects every chronological decrease, including while loading persisted rows.
- `Consumption.kt` validates the whole collection and globally pairs adjacent readings. Simply allowing one lower save would consequently break loading and projection, not just validation.
- `RoomReadingRepository` validates and writes within a Room transaction and serializes captures/commits with a mutex and process-local revisions.
- `TrackerViewModel` retains session-bound dialogs, rejects stale revisions, and suppresses duplicate projection using reading equality alone. These mechanisms must account for installation metadata too.
- `TrackerScreen` has a shared top-right Add icon, `ReadingForm` owns a session/dialog-bound saveable draft, and History's tap/long-press gestures edit/delete.
- Existing specs explicitly promise globally nondecreasing values and one reading per date. This change replaces those rules with installation-scoped rules and one controlled replacement-day exception; it does not silently bypass them.

## Goals / Non-Goals

**Goals**

- Represent the real domain boundary durably, enforce it at the repository boundary, and keep pure domain policy reusable by feedback and final save.
- Preserve exact input precision, existing interval allocation, no-extrapolation semantics, local-only storage, revision ordering, and independent observation/mutation outcomes.
- Keep replacement explicit and all ordinary entry behavior predictable after multiple installations.

**Non-Goals**

- No generic settings framework, profile, validation override token, meter-value offset normalization, or synthetic daily consumption readings. The automatic zero baseline is a structural replacement endpoint, not a user-entered observation.
- No historical replacement insertion, same-day multiple replacements, boundary removal/undo, or intraday measurement.
- No restructuring unrelated dashboard, chart, pager, or lifecycle infrastructure.

## Decisions

### 1. Persist installations and explicit boundary records

Introduce installation identity on every `Reading` and three coordinated persisted collections:

| Collection | Fields and constraints |
| --- | --- |
| Installations | Stable ID and nullable start date; only the original installation has no lower bound |
| Readings | Existing ID/date/exact decimal text plus non-null installation ID; unique `(installationId, date)` and foreign key to installation |
| Replacements | Stable ID, replacement date, old/new installation IDs, and old-final/new-initial reading IDs; unique date, outgoing old installation, incoming new installation, and endpoint references |

Use restrictive foreign keys for installation and endpoint references, not cascading deletion. A boundary's IDs must resolve to readings belonging to the referenced installations on exactly the boundary date. The new installation's start date must equal that date. Model a single connected chronological chain with strictly increasing replacement dates and one terminal/current installation. Derive closing dates and the current installation from the chain instead of maintaining an additional independently mutable current-meter flag.

Seed one original installation for a new empty database and during migration. The original installation can contain arbitrarily backdated readings. Each replacement ends its old installation inclusively on the replacement date; the new initial reading is also on that date. Other readings of that new installation must be strictly after the start date. Closed installations accept no readings after their boundary. No readings may fall outside their installation's lifetime.

Extend `CommittedSnapshot` to include the full installations, replacements, and readings as one immutable tracker aggregate. Loading and mutation must validate identity uniqueness, per-installation date/value order, valid foreign references, chain shape, boundary endpoint roles/dates, and lifetime ranges before exposing data. A cross-installation decrease is valid only in this valid structure; arbitrary decreasing legacy rows are still stored-data failures.

**Alternative:** A reset boolean on a reading or a persisted "allow next decrease" flag is smaller initially but obscures meter identity, leaves deletion/reload semantics fragile, and does not provide a stable migration and calculation contract. Explicit installations make membership and boundaries unambiguous. Normalizing values with offsets was rejected because it alters raw-meter meaning and can hide incorrect cross-meter arithmetic.

### 2. Use today's saved old reading and a dedicated replacement command

The replacement form is bound to the opening device date, expected current installation ID, and displayed final reading's identity and exact value. Do not copy today's saved final reading into a second old-meter row.

Add a dedicated replacement intent/result and pure replacement policy rather than extending ordinary Add with a bypass flag. Replacement has no new-meter input: the repository establishes a zero baseline automatically. Capture the current device date at submission, as normal save does; if it differs from the opening date, report a stale-date condition and require reopening the confirmation.

Within the existing ordered repository mutation boundary and a single database transaction:

1. Read and validate the full latest tracker aggregate.
2. Resolve the expected current installation and its saved reading for the confirmed date. Require this to be its last reading and reject an already occupied replacement date.
3. Check that the displayed final reading still exists with the expected identity, installation, and exact value. If not, return an explicit obsolete-target error and require review/reopening, without silently choosing another reading.
4. Establish the new installation's baseline as exact decimal zero without parsing user input or comparing with the old meter.
5. Create the new installation, its structural zero-baseline reading, and the replacement record, then validate the resulting aggregate.
6. Publish the complete committed snapshot and a new revision only after the transaction succeeds.

The old installation becomes closed through the boundary. Repeated confirmation cannot produce a second replacement because the expected old installation is no longer current, and date/chain constraints also reject it. Competing edit, delete, Add, or replacement operations revalidate against current rows, not UI state. Cancellation and expected failures retain existing handling without broad catches.

**Alternative:** Asking for a new-meter value during replacement adds an unnecessary entry step. Automatically creating the zero baseline in the same transaction makes the next day's first reading immediately usable: 5 minus 0 gives 5. Saving the final old reading separately is intentional: it is already an ordinary valid reading and remains valid if replacement is cancelled. Existing stored or subsequently corrected nonzero baselines are not reset.

### 3. Resolve ordinary dates by installation, preserve edit membership

Create one shared pure installation/date resolver used by Add picker feedback and transactional Add policy. Dates before a boundary belong to the relevant historical installation; dates after the latest boundary belong to the current installation. Every replacement date is occupied for ordinary Add because both endpoints already exist. Never offer an installation selector for ordinary Add.

Value checks use only the resolved installation's previous and next readings. Edit resolves its persisted target and retains date, identity, installation, and role; it excludes itself and compares only same-installation neighbours. A counterpart endpoint on the same date in another installation is not an edit duplicate.

A draft opened before another session's replacement does not control persisted membership: final Add resolves again from the current committed chain, and final Edit keeps the target's persisted membership. Existing malformed/negative/future/duplicate error precedence remains where applicable; replacement prerequisite, protected endpoint, obsolete target, and stale date need explicit domain outcomes and resource messages.

**Alternative:** Always assigning Add to the current meter breaks historical entry after replacement. Asking users to select an installation adds unnecessary ambiguity for a strictly chronological local tracker.

### 4. Keep arithmetic installation-local and allocation unchanged

Build `ReadingInterval` pairs independently within each installation, then merge intervals chronologically before existing allocation and dashboard/chart projection. Validate a pair's matching installation identity. Two replacement endpoints share a date but never form an interval; no zero-day interval is constructed.

The agreed example is:

| Date | Old installation | New installation | Daily consumption |
| --- | --- | --- | --- |
| 2026-10-27 | 1100 | - | Baseline only |
| 2026-10-28 | Final 1105 | Initial 0 | 5 |
| 2026-10-29 | - | 3 | 3 |

Old coverage ends on October 28 and new coverage begins on October 29, so existing non-overlapping interval allocation remains correct. The zero baseline is automatic; a first reading of 5 on October 29 gives 5 - 0 = 5 for that day. Previously stored or corrected baselines still participate in ordinary subtraction without rewriting their values. Skipped-day estimation, exact whole-interval totals, chart zero markers, unknown-day omission, period totals, and rounding stay unchanged. Period totals sum usage from all installations whose same-installation intervals overlap the period.

**Alternative:** Subtracting across meters yields negative or inflated consumption. Clamping a negative delta to zero would fabricate known-zero coverage and lose usage. Moving new usage to the previous day would contradict existing `(A, B]` semantics.

### 5. Integrate with the existing dialog/session architecture

Add a gear vector resource and an options `IconButton` immediately before Add in `TopAppBar.actions`. Use a Material menu with only Replace meter; menu expansion is transient UI state, not a persisted domain state or new tab. Provide localized semantics and existing minimum touch targets.

Extend the semantic dialog union with a replacement target and ready/pending/obsolete/failed phases. Add parameterless replacement submission callbacks through `MainActivity` and `TrackerScreen`; use the existing session/dialog/request identity mechanism so old completions cannot close unrelated confirmations. Replacement has no baseline parsing, field errors, or editable draft.

With no final reading today, selecting Replace meter opens an explanation with an Add action. Choosing Add replaces that explanation with ordinary Add for today; it never auto-confirms replacement after Save. With a valid target, display today's read-only date, saved final value, an explanation of the automatic zero baseline, Confirm replacement, and Cancel. Do not display a new-meter input field or require keyboard entry. A previously completed replacement today shows an explanatory unavailable state instead of permitting another submission.

Reuse formatting, storage-error presentation, and scroll/inset layout patterns rather than forcing replacement into ordinary Add's editable-date form. Retain the confirmation through configuration restoration with matching session/dialog identity; no replacement input draft needs saving. Block mutation, dismissal, target switching, and duplicate submission while pending. Failed observation preserves the confirmation but disables submission until reload is ready. A committed result closes the dialog even if observation is currently failed.

Snapshot acceptance and duplicate-data comparison must include installations and boundaries, not only readings. Project History and Dashboard from that same accepted aggregate; changes retain tab and period. Fresh-process sessions reload committed data but discard transient dialogs.

**Alternative:** A standalone composable performing database writes would bypass established ordering, failure, and lifecycle guarantees. A generic settings screen adds unnecessary navigation for one action.

### 6. Keep structural endpoints editable but not deletable

History sorts by date descending, then installation chain position descending for same-date endpoints; stable IDs remain list keys. Show localized installation labels (for example, numbered meter labels) and initial/final roles rather than technical IDs. Date, value, meter label, and role must also disambiguate accessibility action labels.

Tap continues to edit the meter value. Long-press on ordinary rows retains deletion confirmation. For either structural endpoint, show a clear explanation instead. The repository must independently return a protected-endpoint result for deletion, including an old deletion dialog whose target became an endpoint in another session. Boundary IDs remain unchanged when endpoint values are corrected.

This is an intentional exception to existing unrestricted deletion. Full replacement reversal is excluded rather than allowing a delete that could merge installations or silently erase other readings. It is documented explicitly in the specs and README.

**Alternative:** Deleting the boundary together with its baseline or cascading an installation would destroy valid history; silently transferring the boundary to a neighbouring reading changes the recorded installation event.

## Risks / Trade-offs

- [A daily model cannot divide usage inside replacement day] -> Attribute old usage to the saved final reading's date and new usage after its initial reading. Explain this in the confirmation and README; do not imply sub-day accuracy.
- [The saved final reading might not actually be the removal reading] -> Label and display it as the final value and require explicit confirmation. The user must correct it through Edit before confirming if necessary.
- [Two rows share the replacement date] -> Composite uniqueness, stable keys, meter/role labels, and deterministic ordering distinguish them; ordinary Add still treats that date as occupied.
- [Protecting endpoints limits undo] -> Explain protection before destructive confirmation and allow value corrections. Do not claim replacement can be undone in this version.
- [Device date moves backwards or persisted later readings exist] -> Reject stale replacement dates or a target that is not the latest reading; never move saved dates, infer another final reading, or repair data automatically.
- [Reading-only observation or equality hides metadata changes] -> Observe all three tables, capture a transactionally consistent aggregate under the existing ordering mutex, and compare the complete aggregate.
- [Migration failure or invalid stored chain] -> Preserve the database and use existing classified non-destructive failure behavior; never use destructive migration fallback.

## Migration Plan

1. Export Room schema v2 while retaining the checked-in v1 schema. Add installation/replacement tables and rebuild the readings table to add the non-null installation reference and replace the global unique-date index with `(installationId, date)`.
2. In the explicit v1-to-v2 migration, create one fixed original installation, copy every existing reading with its identity/date/decimal text unchanged into that installation, and preserve the `readings.db` filename. Empty databases also receive exactly one original installation. Apply the same initialization invariant to fresh v2 creation.
3. Register the migration and new-database initialization in `TrackGasApplication` and appropriate test database factories. Keep foreign keys restrictive and do not add a destructive fallback.
4. Extend DAO invalidation to installation, reading, and replacement tables. Capture complete snapshots within read transactions; Room emission payloads remain invalidation triggers, preserving the existing mutex/revision strategy.
5. Exercise migration using a real file-backed v1 fixture, covering empty and populated data, exact decimal scale, schema/index constraints, cold reopen, and unchanged original consumption. Use existing instrumentation infrastructure and framework SQL fixture creation; no new test dependency is needed.
6. Deliver schema, repository, domain, and UI integration together. There is no automatic v2-to-v1 downgrade: if a release must be rolled back, ship a forward-compatible corrective build preserving v2 data rather than installing an old build that cannot understand replacement records.
