package com.trackgas.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TrackGasApplication : Application() {
    val trackerStoreOwner = TrackerStoreOwner()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppLanguages.select(AppLanguage.ENGLISH)
        }
        temperatureSync.requestCheck()
    }

    private val database by lazy {
        Room.databaseBuilder(this, ReadingDatabase::class.java, "readings.db")
            .addCallback(ReadingDatabase.INITIALIZE)
            .build()
    }
    val readingRepository: ReadingRepository by lazy { RoomReadingRepository(database) }
    val reminderStore: ReminderStore by lazy { SharedPreferencesReminderStore(this) }
    val reminderScheduler: ReminderScheduler by lazy { WorkManagerReminderScheduler(this) }
    val temperatureRepository: TemperatureRepository by lazy { RoomTemperatureRepository(database) }
    val meterLocationStore: MeterLocationStore by lazy { SharedPreferencesMeterLocationStore(this) }
    val locationResolver: LocationResolver by lazy { OpenMeteoGeocoder() }
    val networkMonitor: NetworkMonitor by lazy { ConnectivityNetworkMonitor(this) }
    val temperatureSync: TemperatureSync by lazy {
        TemperatureSync(
            readingRepository,
            meterLocationStore,
            temperatureRepository,
            networkMonitor,
            OpenMeteoTemperatureProvider(),
            applicationScope,
        )
    }
    val temperatureServices: TemperatureServices by lazy {
        TemperatureServices(
            locationResolver,
            meterLocationStore,
            networkMonitor,
            temperatureRepository,
            temperatureSync,
        )
    }
}
