## 1. Foundation

- [x] 1.1 Add `androidx.work:work-runtime-ktx` to `app/build.gradle.kts` and `POST_NOTIFICATIONS` to `AndroidManifest.xml`
- [x] 1.2 Add `ReminderPolicy.isDue(lastSubmissionMillis, nowMillis)` (24 h threshold) with unit tests
- [x] 1.3 Add `ReminderStore` (SharedPreferences last-submission timestamp, set/get/clear)

## 2. Scheduling and notification

- [x] 2.1 Add `ReminderScheduler` interface plus WorkManager implementation (unique one-time work, 24 h delay, REPLACE; cancel on purge)
- [x] 2.2 Add `ReminderWorker`: re-check policy and permission, post notification on a "Reminders" channel, tap opens `MainActivity`
- [x] 2.3 Wire store and scheduler in `TrackGasApplication`; call from `TrackerViewModel` after successful save, replacement, CSV import, and purge (fake scheduler in `TrackerViewModelTest`)

## 3. Permission and strings

- [x] 3.1 Request notification permission (API 33+) at launch when not granted and not previously declined; record a `declined` flag in `ReminderStore` on denial and clear it when granted (revocation re-prompts)
- [x] 3.2 Add concise reminder title/body and channel name strings in all supported languages; keep `LocalizedStringResourcesTest` passing

## 4. Verification

- [x] 4.1 Run `./gradlew testDebugUnitTest` and manually verify: reminder after 24 h (shortened delay), cancelled by new reading, none after denial or purge
