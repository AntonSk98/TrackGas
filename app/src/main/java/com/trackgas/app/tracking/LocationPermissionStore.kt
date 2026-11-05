package com.trackgas.app.tracking

import android.content.Context

internal class LocationPermissionStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("location_permission", Context.MODE_PRIVATE)

    fun read(): LocationPermissionState =
        prefs.getString("state", null)
            ?.let { name -> LocationPermissionState.entries.firstOrNull { it.name == name } }
            ?: LocationPermissionState.NEVER_REQUESTED

    fun write(state: LocationPermissionState) {
        prefs.edit().putString("state", state.name).apply()
    }
}
