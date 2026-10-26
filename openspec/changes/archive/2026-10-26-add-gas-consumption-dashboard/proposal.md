# Proposal

## Why

The app currently stops at a starter screen and does not let a user track gas usage history. We need a minimal, offline-first dashboard and note workflow so a single user can record meter readings and immediately see weekly, monthly, and yearly consumption trends.

## What Changes

- Add a gas dashboard that opens on the current Monday-to-Sunday week, with calendar-month/year selection and previous/next period navigation.
- Keep consumption content empty when the selected period has no calculable consumption; unknown days are not zero.
- Show only covered consumption days/months, including estimated usage and known zero, without individual Unknown rows, coverage counts, or estimation labels/explanations.
- Add a top-right action to create a gas meter note with a date and current meter value, defaulting date to today.
- Add note management actions to edit and delete existing notes.
- Separate Dashboard and History tabs so complete reading history and its Edit/Delete actions do not clutter the main dashboard.
- Accept one non-negative cumulative reading in cubic metres per date, today or earlier, with chronological non-decreasing validation.
- Derive consumption from consecutive dated readings. Average skipped-day intervals evenly, retain estimate provenance internally, and recalculate after changes. Keep the dashboard limited to dates, cubic-metre values, and a consumption total.
- Preserve original readings separately from derived allocations so future weather analysis can compare weather and measured consumption over matching intervals.
- Keep data in memory for one local tracker, surviving screen rotation but not process termination (no accounts, database, files, or preferences).
- Reuse existing Compose Material components and keep a minimal UI style.
- Keep the default date responsive to runtime device-timezone changes, enforce domain construction invariants, and separate UI rendering/formatting from the state holder without adding architecture frameworks.
- Exclude prices/tariffs, weather integration/correlation, weather-weighted estimates, multiple meters, and meter resets from this change.

## Capabilities

### New Capabilities
- `gas-consumption-tracking`: Single-user in-app tracking of gas meter notes and period-based consumption dashboard with in-memory data.

### Modified Capabilities
- `app-shell`: Replace the starter-only shell behavior with the first usable product screen while preserving offline launch.

## Impact

- Affected code: Android Compose UI layer in `app/src/main/java/com/trackgas/app`, plus string resources.
- APIs: No external API changes; all behavior remains local to the app.
- Dependencies: Reuse existing Compose Material 3; add only focused Android lifecycle or test dependencies if required. No backend, database, charting, or weather dependency.
- Systems: Existing `app-shell` spec expectations must be updated to reflect the new dashboard-first startup experience.
