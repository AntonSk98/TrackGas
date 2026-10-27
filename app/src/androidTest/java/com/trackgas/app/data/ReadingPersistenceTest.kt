package com.trackgas.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackgas.app.tracking.DeleteReadingResult
import com.trackgas.app.tracking.SaveReadingResult
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingPersistenceTest {
    @Test
    fun databaseReopenRetainsEditsAndDeletions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-readings-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, ReadingDatabase::class.java, name).build()
        var database = open()
        try {
            var repository = RoomReadingRepository(database)
            val today = LocalDate.parse("2026-10-26")
            val first = repository.save(null, LocalDate.parse("2026-10-01"), "100.0100", today, Locale.US) as SaveReadingResult.Saved
            val firstId = first.readings.single().id
            val second = repository.save(null, LocalDate.parse("2026-10-04"), "110.0200", today, Locale.US) as SaveReadingResult.Saved
            val secondId = second.readings.first().id
            database.close()

            database = open()
            repository = RoomReadingRepository(database)
            assertEquals(listOf("110.0200", "100.0100"), repository.observeReadings().first().map { it.cubicMetres.toString() })
            assertTrue(repository.save(firstId, LocalDate.parse("2026-10-01"), "101.0300", today, Locale.US) is SaveReadingResult.Saved)
            assertTrue(repository.delete(secondId) is DeleteReadingResult.Deleted)
            database.close()

            database = open()
            val final = RoomReadingRepository(database).observeReadings().first().single()
            assertEquals(firstId, final.id)
            assertEquals(LocalDate.parse("2026-10-01"), final.date)
            assertEquals("101.0300", final.cubicMetres.toString())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
