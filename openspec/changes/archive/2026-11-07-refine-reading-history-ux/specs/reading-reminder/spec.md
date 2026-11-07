# Spec Delta

## MODIFIED Requirements

### Requirement: Concise friendly reminder content
The notification SHALL have a short title and one-line friendly body prompting the user to add today's meter reading, localized in every supported app language. It SHALL use a recognizable monochrome gas-tracking brand mark as its notification icon. Tapping it SHALL open the app.

#### Scenario: Read the reminder
- **WHEN** the reminder is shown
- **THEN** it displays a brief title and a single-sentence prompt, such as "Your gas meter is calling" and "Don’t forget to log today’s gas meter value."

#### Scenario: Recognize the notification icon
- **WHEN** the reminder appears in the notification shade or status bar
- **THEN** its icon is a recognizable monochrome gas-tracking brand mark rather than a generic battery-shaped symbol

#### Scenario: Tap the notification
- **WHEN** the user taps the notification
- **THEN** the app opens to the dashboard
