# Spec Delta

## REMOVED Requirements

### Requirement: Request location in context
**Reason**: Temperatures use a user-entered meter location; the device location is never needed.
**Migration**: Use the Meter location entry in the options menu (see `meter-location`).

### Requirement: Request approximate location only
**Reason**: The app no longer requests any location permission.
**Migration**: None; the permission is removed from the manifest.

### Requirement: Honor current permission state
**Reason**: There is no permission state to honor.
**Migration**: None; the stored permission state is no longer read.

### Requirement: Tracking works without location
**Reason**: Superseded by "Location is optional" in `meter-location`.
**Migration**: None.

### Requirement: Localized explanation
**Reason**: The explanation dialog is removed.
**Migration**: None.
