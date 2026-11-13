# Design

## Context

See proposal.md for motivation and specs/reading-data-exchange/spec.md for the behavior contract. CSV validation currently rejects UUIDs absent from the tracker, and candidate construction only retains existing installations. The repository already applies validated imports in a Room transaction.

## Goals / Non-Goals

**Goals:**
- Reuse the CSV UUID as the new installation's stable identity.
- Preserve full-file validation, preview, confirmation, and atomic persistence.
- Keep invalid UUIDs and unrelated CSV validation failures as errors.

**Non-Goals:**
- Change the CSV format or generate replacement UUIDs.
- Infer meter installation dates or change meter-history validation.
- Add a database migration.

## Decisions

- Accept any valid canonicalizable UUID during CSV validation, mapping known IDs to their stored identity and retaining a normalized UUID for unknown IDs. A malformed UUID remains invalid. Rejecting valid unknown IDs or substituting newly generated IDs would prevent importing the CSV's meter history under its source identity.
- Extend candidate construction to add one `MeterInstallation` for each included UUID not already present, alongside the imported readings. Leave its start date unset because the CSV does not provide that metadata; do not invent a date.
- Keep persistence in the existing repository transaction. It already replaces affected installations and inserts candidate installations and readings together, so adding new installations to the candidate preserves rollback and atomicity without schema changes.
- Update the preview warning and CSV documentation to distinguish replaced existing histories from newly created meters. Remove the obsolete unknown-meter rejection path and its user-facing error mapping; malformed UUID handling remains.

## Risks / Trade-offs

- [A valid but unintended UUID now creates a meter instead of stopping the import] → Keep full validation and explicit confirmation before committing; the preview continues to omit UUIDs and per-meter counts.
- [An imported meter has no known start date] → Leave it unset rather than fabricating metadata; current-meter selection continues to use imported reading dates and existing tie-breaking rules.

## Migration Plan

No persisted data migration is needed. Update CSV validation, candidate construction, repository/import presentation tests, and the CSV documentation. The change can be rolled back as an application update; existing stored meters and readings are unchanged unless a user confirms an import.
