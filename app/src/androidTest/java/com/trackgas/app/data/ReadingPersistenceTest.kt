package com.trackgas.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackgas.app.tracking.DeleteReadingResult
import com.trackgas.app.tracking.SaveReadingResult
import com.trackgas.app.tracking.SaveReadingIntent
import com.trackgas.app.tracking.parseMeter
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
            val first = repository.save(SaveReadingIntent.Add(LocalDate.parse("2026-10-01"), parseMeter("100.0100", Locale.US), today)) as SaveReadingResult.Saved
            val firstId = first.snapshot.readings.single().id
            val second = repository.save(SaveReadingIntent.Add(LocalDate.parse("2026-10-04"), parseMeter("110.0200", Locale.US), today)) as SaveReadingResult.Saved
            val secondId = second.snapshot.readings.first().id
            database.close()

            database = open()
            repository = RoomReadingRepository(database)
            assertEquals(listOf("110.0200", "100.0100"), repository.observeReadings().first().readings.map { it.cubicMetres.toString() })
            assertTrue(repository.save(SaveReadingIntent.Edit(firstId, parseMeter("101.0300", Locale.US), today)) is SaveReadingResult.Saved)
            assertTrue(repository.delete(secondId) is DeleteReadingResult.Deleted)
            database.close()

            database = open()
            val final = RoomReadingRepository(database).observeReadings().first().readings.single()
            assertEquals(firstId, final.id)
            assertEquals(LocalDate.parse("2026-10-01"), final.date)
            assertEquals("101.0300", final.cubicMetres.toString())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
