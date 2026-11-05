# Reading Reminder Specification

## Purpose

The reading reminder prompts users to submit a gas meter reading after a full day without one, helping keep consumption history current. It works offline and honors the user's notification permission choice.

## Requirements

### Requirement: Reminder after 24 hours without a new reading
The app SHALL show one local push notification when at least 24 hours have passed since the last reading submission and no new reading has been submitted. A submission is any successful add, edit, meter replacement, or CSV import. The reminder SHALL work without connectivity.

#### Scenario: No reading for 24 hours
- **WHEN** 24 hours pass after the last submission with no new submission
- **THEN** a reminder notification is shown

#### Scenario: Reading submitted within 24 hours
- **WHEN** the user submits a new reading before 24 hours have passed
- **THEN** no reminder is shown for the earlier submission and the 24-hour countdown restarts

#### Scenario: Single reminder per lapse
- **WHEN** a reminder has been shown and the user still submits nothing
- **THEN** no further reminder is shown until a new submission is made and 24 hours pass again

#### Scenario: No readings yet or data purged
- **WHEN** the tracker has no readings
- **THEN** no reminder is scheduled or shown

### Requirement: Concise friendly reminder content
The notification SHALL have a short title and one-line friendly body prompting the user to add today's meter reading, localized in every supported app language. Tapping it SHALL open the app.

#### Scenario: Read the reminder
- **WHEN** the reminder is shown
- **THEN** it displays a brief title and a single-sentence prompt, such as "Your gas meter is calling" and "Don’t forget to log today’s gas meter value."

#### Scenario: Tap the notification
- **WHEN** the user taps the notification
- **THEN** the app opens to the dashboard

### Requirement: Notification permission requested at launch
Where the OS requires notification permission, the app SHALL request it at launch when it is not granted and the user has not declined the prompt before. A permission that was granted and later revoked SHALL count as not declined, so the app MAY ask again. After the user declines, the app SHALL NOT ask again, reminders SHALL be silently skipped, and tracking SHALL remain fully usable.

#### Scenario: First launch
- **WHEN** the user opens the app for the first time and permission is not granted
- **THEN** the notification permission prompt appears

#### Scenario: Permission declined
- **WHEN** the user declines the prompt
- **THEN** no reminder is shown, later launches do not prompt again, and all tracking features continue to work

#### Scenario: Permission revoked after being granted
- **WHEN** the user revokes a previously granted permission in system settings and reopens the app
- **THEN** the prompt appears again
