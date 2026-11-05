# Spec Delta

## MODIFIED Requirements

### Requirement: Offline application launch
The installed app SHALL launch to the gas consumption dashboard without connectivity, an account, or a server. It SHALL NOT show a location explanation or permission prompt at launch. A location-dependent feature MAY request approximate location only after the user invokes that feature, and declining SHALL NOT block existing tracking features.

#### Scenario: Launch in airplane mode
- **WHEN** the user opens the installed app with connectivity disabled
- **THEN** the gas consumption dashboard appears without a crash, network error, sign-in, or location prompt

#### Scenario: Launch without invoking a location-dependent feature
- **WHEN** the user opens the app without invoking a location-dependent feature
- **THEN** the dashboard appears without any location permission prompt regardless of the current permission state
