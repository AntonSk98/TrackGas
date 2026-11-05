package com.trackgas.app.temperature

import android.content.Context
import android.content.SharedPreferences

/** A saved place name and its resolved coordinates for meter-area weather lookups. */
data class MeterLocation(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(displayName.isNotBlank())
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

/** Persists the user's selected meter location on this device. */
interface MeterLocationStore {
    fun read(): MeterLocation?
    fun save(location: MeterLocation)
}

/** Stores meter location fields in the app's private preferences. */
class SharedPreferencesMeterLocationStore(context: Context) : MeterLocationStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): MeterLocation? {
        val name = preferences.getString(KEY_NAME, null) ?: return null
        val latitude = preferences.getString(KEY_LATITUDE, null)?.toDoubleOrNull()
            ?: error("Stored meter location has no valid latitude")
        val longitude = preferences.getString(KEY_LONGITUDE, null)?.toDoubleOrNull()
            ?: error("Stored meter location has no valid longitude")
        return MeterLocation(name, latitude, longitude)
    }

    override fun save(location: MeterLocation) {
        check(preferences.edit()
            .putString(KEY_NAME, location.displayName)
            .putString(KEY_LATITUDE, location.latitude.toString())
            .putString(KEY_LONGITUDE, location.longitude.toString())
            .commit()) { "Could not persist meter location" }
    }

    private companion object {
        const val PREFERENCES_NAME = "meter_location"
        const val KEY_NAME = "name"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
    }
}
