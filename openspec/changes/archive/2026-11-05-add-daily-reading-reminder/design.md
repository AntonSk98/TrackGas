## Context

Offline Android app (minSdk 26, targetSdk 35, Compose, Room). `Reading` stores only a calendar date, so the actual submission time is not available. Dependencies are created in `TrackGasApplication`; the tracker flows through `ReadingRepository`.

## Goals / Non-Goals

**Goals:** one concise local reminder after 24 h without a submission; survive app kill and reboot; no launch-time prompts.
**Non-Goals:** user-configurable times, repeated nagging, snooze, settings UI, server push (FCM).

## Decisions

- **Local notification, not FCM.** App has no backend; "push notification" is satisfied by a system notification.
- **Submission timestamp in SharedPreferences** (`ReminderStore`, epoch millis), written on successful save/replace/import. Reading dates cannot express 24 h. Cleared on purge. Alternative (Room column + migration) rejected as heavier for one value.
- **WorkManager one-time work** with a 24 h initial delay, unique name with REPLACE policy, rescheduled on each submission. Survives reboot and is battery-friendly; inexact timing is acceptable. Alternative AlarmManager exact alarms rejected (permission, reboot handling).
- **Worker re-checks** the stored timestamp before posting (due only if now − last ≥ 24 h) and posts only if notifications are permitted; then does not reschedule, giving one reminder per lapse.
- **Pure `ReminderPolicy`** (`isDue(lastSubmission, now)`) for unit testing.
- **Permission:** `POST_NOTIFICATIONS` requested via activity result API at launch on API 33+ when not granted and no decline is recorded. `ReminderStore` keeps a `declined` flag set on denial and cleared whenever the permission is observed granted, so a later revocation prompts again. Denial otherwise disables reminders silently.
- **Hook point:** `TrackerViewModel` success paths call a `ReminderScheduler` interface injected from `TrackGasApplication` (fake in tests).
- **Content:** channel "Reminders", default importance; strings in every locale in `res/values-*`, enforced by `LocalizedStringResourcesTest`. Tap opens `MainActivity` via `PendingIntent`.

## Risks / Trade-offs

- Existing users upgrading have no timestamp → treat missing as "no reminder until next submission" (avoids a surprise notification).
- WorkManager delays may exceed 24 h under Doze → acceptable for a reminder.
- Imported/edited readings count as submissions; accepted for simplicity.
