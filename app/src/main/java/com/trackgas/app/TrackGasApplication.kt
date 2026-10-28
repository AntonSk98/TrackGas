package com.trackgas.app

import android.app.Application
import androidx.room.Room
import com.trackgas.app.data.ReadingDatabase
import com.trackgas.app.data.RoomReadingRepository
import com.trackgas.app.tracking.ReadingRepository

class TrackGasApplication : Application() {
    val trackerStoreOwner = TrackerStoreOwner()

    private val database by lazy {
        Room.databaseBuilder(this, ReadingDatabase::class.java, "readings.db").build()
    }
    val readingRepository: ReadingRepository by lazy { RoomReadingRepository(database) }
}
