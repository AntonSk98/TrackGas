package com.trackgas.app.tracking

internal enum class LocationPermissionState { NEVER_REQUESTED, DECLINED, PREVIOUSLY_GRANTED }

internal fun shouldRequestLocation(
    granted: Boolean,
    previousState: LocationPermissionState,
): Boolean = !granted && previousState != LocationPermissionState.DECLINED
