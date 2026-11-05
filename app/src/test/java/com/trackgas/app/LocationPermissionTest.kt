package com.trackgas.app

import com.trackgas.app.tracking.LocationPermissionState
import com.trackgas.app.tracking.shouldRequestLocation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPermissionTest {
    @Test
    fun neverPromptsWhenGranted() {
        LocationPermissionState.entries.forEach {
            assertFalse(shouldRequestLocation(granted = true, previousState = it))
        }
    }

    @Test
    fun asksWhenNeverRequested() {
        assertTrue(shouldRequestLocation(false, LocationPermissionState.NEVER_REQUESTED))
    }

    @Test
    fun suppressedAfterDecline() {
        assertFalse(shouldRequestLocation(false, LocationPermissionState.DECLINED))
    }

    @Test
    fun asksAgainAfterRevocation() {
        assertTrue(shouldRequestLocation(false, LocationPermissionState.PREVIOUSLY_GRANTED))
    }
}
