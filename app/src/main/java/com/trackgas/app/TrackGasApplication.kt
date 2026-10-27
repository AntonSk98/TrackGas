package com.trackgas.app

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.trackgas.app.data.ReadingDatabase
import com.trackgas.app.data.RoomReadingRepository

class TrackGasApplication : Application() {
    private data class TrackerStore(var taskId: Int, val store: ViewModelStore)
    private val trackerStores = mutableMapOf<String, TrackerStore>()

    fun trackerStore(id: String, taskId: Int): ViewModelStore =
        trackerStores.getOrPut(id) { TrackerStore(taskId, ViewModelStore()) }.also {
            it.taskId = taskId
        }.store

    fun releaseTrackerStore(id: String) {
        trackerStores.remove(id)?.store?.clear()
    }

    fun pruneTrackerStores(activeTaskIds: Set<Int>) {
        val abandoned = trackerStores.filterValues { it.taskId !in activeTaskIds }.keys.toList()
        abandoned.forEach(::releaseTrackerStore)
    }

    private val database by lazy {
        Room.databaseBuilder(this, ReadingDatabase::class.java, "readings.db").build()
    }
    val readingRepository by lazy { RoomReadingRepository(database) }
}
