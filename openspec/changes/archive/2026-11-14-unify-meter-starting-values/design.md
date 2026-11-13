# Design

## Context

See proposal.md and the two capability deltas. The Room database currently seeds an empty installation, replacement adds another empty installation, and dashboard projection counts the first reading on a replacement's start date as opening consumption. Readings already store an installation identity, and Room mutations already use transactions.

## Goals / Non-Goals

**Goals:**
- Keep a meter and its starting reading as one committed unit for both first Add and replacement.
- Permit a truly empty tracker and ensure every stored meter has at least one reading.
- Preserve existing readings and use a meter's first reading only as the baseline for subsequent intervals.

**Non-Goals:**
- Requiring a final reading from the old meter before replacement.
- Changing CSV columns, reading precision, interval allocation, or the per-meter chronology rules.

## Decisions

### Use the first persisted reading as the meter's starting value

Do not add a separate baseline field or synthetic zero reading. The starting value is an ordinary persisted reading and the earliest reading for that meter. Existing interval pairing already excludes a lone reading; remove the dashboard's special replacement-day opening contribution so every meter follows the same baseline rule.

### Share meter creation validation and transaction behavior

Use one domain operation to create an installation and its first reading. Add invokes it when the tracker has no meter; Replace invokes it with today's date after validating the expected current meter. The installation and reading are committed in the same Room transaction, with the supplied locale-parsed value and the reading's observation metadata. Later Add and Edit keep their existing neighbor validation.

Represent an empty tracker with no installations and make the current-meter lookup nullable. Disable replacement when there is no meter. Removing a reading deletes its installation in the same transaction if no readings remain; removing the final meter leaves the tracker empty. Purge follows the same empty-state invariant.

### Require every persisted meter to have a reading

Update collection validation and CSV import reconciliation so a tracker may contain zero meters only when it has zero readings, every reading references a persisted meter, and every persisted meter is referenced by at least one reading. Keep CSV serialization based on actual readings; no synthetic baseline or empty meter is exported.

### Require a clean database for the new invariant

The Room schema does not change, so do not add a version bump or migration. Remove the on-create seed installation so fresh databases start with no meters. Existing databases may contain empty installations from the old behavior and are incompatible with the new invariant; this development-only change requires reinstalling the app, which clears that legacy data.

## Risks / Trade-offs

- [Existing databases may contain empty installations] → Treat this as a fresh-install-only development change; reinstall the app to clear old data rather than adding a migration or silently repairing stored state.
- [Removing a meter's last reading changes the current meter and available history] → Perform reading and meter deletion atomically, publish only the committed tracker, and test deletion of both the last reading in one meter and the last reading overall.
- [Starting values currently contribute opening consumption on replacement dates] → Remove that special contribution and add focused projection cases for initial and replacement baselines plus later deltas.

## Migration Plan

1. Remove the on-create seed installation and ship the new empty-state invariant without changing the Room schema or version.
2. Reinstall during development to clear existing databases that may contain legacy empty installations; this resets local readings and settings.
3. Update purge and CSV-import paths, and ensure all normal meter creation paths save a starting reading atomically.
