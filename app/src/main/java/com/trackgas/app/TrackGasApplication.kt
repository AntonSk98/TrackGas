package com.trackgas.app

import android.app.Application
import androidx.room.Room
import com.trackgas.app.data.ReadingDatabase
import com.trackgas.app.data.RoomReadingRepository
import com.trackgas.app.reminders.ReminderScheduler
import com.trackgas.app.reminders.ReminderStore
import com.trackgas.app.reminders.SharedPreferencesReminderStore
import com.trackgas.app.reminders.WorkManagerReminderScheduler
import com.trackgas.app.tracking.ReadingRepository
import com.trackgas.app.temperature.ConnectivityNetworkMonitor
import com.trackgas.app.temperature.MeterLocationStore
import com.trackgas.app.temperature.OpenMeteoGeocoder
import com.trackgas.app.temperature.OpenMeteoTemperatureProvider
import com.trackgas.app.temperature.RoomTemperatureRepository
import com.trackgas.app.temperature.SharedPreferencesMeterLocationStore
import com.trackgas.app.temperature.TemperatureSync
import com.trackgas.app.temperature.LocationResolver
import com.trackgas.app.temperature.NetworkMonitor
import com.trackgas.app.temperature.TemperatureRepository
import com.trackgas.app.temperature.TemperatureServices
import com.trackgas.app.temperature.TemperatureCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TrackGasApplication : Application() {
    val trackerStoreOwner = TrackerStoreOwner()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        temperatureSync.requestCheck()
    }

    private val database by lazy {
        Room.databaseBuilder(this, ReadingDatabase::class.java, "readings.db")
            .build()
    }
    val readingRepository: ReadingRepository by lazy { RoomReadingRepository(database) }
    val reminderStore: ReminderStore by lazy { SharedPreferencesReminderStore(this) }
    val reminderScheduler: ReminderScheduler by lazy { WorkManagerReminderScheduler(this) }
    val temperatureRepository: TemperatureRepository by lazy { RoomTemperatureRepository(database) }
    val meterLocationStore: MeterLocationStore by lazy { SharedPreferencesMeterLocationStore(this) }
    val locationResolver: LocationResolver by lazy { OpenMeteoGeocoder() }
    val networkMonitor: NetworkMonitor by lazy { ConnectivityNetworkMonitor(this) }
    private val temperatureCoordinator by lazy { TemperatureCoordinator(meterLocationStore, temperatureRepository) }
    val temperatureSync: TemperatureSync by lazy {
        TemperatureSync(
            readingRepository,
            meterLocationStore,
            temperatureRepository,
            networkMonitor,
            OpenMeteoTemperatureProvider(),
            applicationScope,
            coordinator = temperatureCoordinator,
        )
    }
    val temperatureServices: TemperatureServices by lazy {
        TemperatureServices(
            locationResolver,
            meterLocationStore,
            networkMonitor,
            temperatureRepository,
            temperatureSync,
            temperatureCoordinator,
        )
    }
}
