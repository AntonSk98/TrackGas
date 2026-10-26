# app-shell Specification

## Purpose

Provide a minimal offline Android entry point that establishes a working native application and starter interface before product features are added.

## Requirements

### Requirement: Offline application launch
The installed app SHALL launch to the gas consumption dashboard without connectivity, an account, a server, or runtime permission prompts.

#### Scenario: Launch in airplane mode
- **WHEN** the user opens the installed app with connectivity disabled
- **THEN** the gas consumption dashboard appears without a crash, network error, sign-in, or permission prompt

### Requirement: Minimalist dark interface
The main screen SHALL display the app name "TrackGas" in a readable minimalist dark interface with labelled Dashboard and History tabs, Dashboard selected initially, working period controls on Dashboard, an accessible top-right add-reading icon on either tab, and reading management in History when notes exist. Dashboard SHALL omit reading history and individual Unknown consumption rows. The interface SHALL respect system insets and use labelled controls without exposing unfinished feature actions.

#### Scenario: View the starter screen
- **WHEN** the user launches the app
- **THEN** "TrackGas", Dashboard and History tabs, weekly-by-default dashboard controls, and the add-reading icon are visible on a dark screen, without reading history on Dashboard, sample analytics, or unfinished price or weather controls
