# Spec Delta

## Purpose

Provide a minimal offline Android entry point that establishes a working native application and starter interface before product features are added.

## ADDED Requirements

### Requirement: Offline application launch
The installed app SHALL launch to a starter screen without connectivity, an account, a server, or runtime permission prompts.

#### Scenario: Launch in airplane mode
- **WHEN** the user opens the installed app with connectivity disabled
- **THEN** the starter screen appears without a crash, network error, sign-in, or permission prompt

### Requirement: Minimalist dark interface
The starter screen SHALL display the app name "TrackGas" in a readable dark interface without exposing unfinished feature controls.

#### Scenario: View the starter screen
- **WHEN** the user launches the app
- **THEN** "TrackGas" is visible on a dark screen with no reading form, dashboard analytics, or placeholder feature actions
