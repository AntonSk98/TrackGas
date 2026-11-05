# Proposal

## Why

Consumption tracking is only useful with regular readings. Users forget to submit one, leaving gaps shown as Unknown consumption. A gentle nudge when a full day has passed without a new reading keeps the history continuous.

## What Changes

- When 24 hours have passed since the last reading was submitted and no new reading has been added, show a single local push notification reminding the user to enter the meter value.
- The notification is short and friendly, localized in every supported app language, and opens the app on tap.
- At most one reminder per lapse; saving a new reading (add, replace, or CSV import) restarts the 24-hour countdown.
- Android 13+ notification permission is requested at launch unless the user already declined it; a revoked permission triggers the prompt again. Declining silently disables reminders without blocking tracking.
- Works offline; no server or account.

## Capabilities

### New Capabilities
- `reading-reminder`: Local notification reminding the user to submit a gas meter reading after 24 hours without a new submission.

### Modified Capabilities

## Impact

- New: WorkManager dependency, reminder scheduler/worker, notification channel, last-submission timestamp store (SharedPreferences).
- `AndroidManifest.xml`: `POST_NOTIFICATIONS` permission.
- `TrackerViewModel`/`TrackGasApplication`: reschedule on successful save, replacement, import, and purge.
- String resources in all supported languages; unit tests for the due-time logic.
