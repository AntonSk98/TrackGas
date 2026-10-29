package com.trackgas.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackgas.app.tracking.DeleteReadingResult
import com.trackgas.app.tracking.SaveReadingResult
import com.trackgas.app.tracking.SaveReadingIntent
import com.trackgas.app.tracking.parseMeter
import com.trackgas.app.tracking.*
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
    @Test fun replacementHistoryAndConsumptionSurviveOfflineReopenAndEndpointCorrections() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "replacement-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, ReadingDatabase::class.java, name)
            .addMigrations(ReadingDatabase.MIGRATION_1_2).addCallback(ReadingDatabase.INITIALIZE).build()
        var database = open()
        try {
            var repository = RoomReadingRepository(database)
            val replacementDay = LocalDate.parse("2026-10-28")
            val today = replacementDay.plusDays(1)
            suspend fun add(date: LocalDate, value: String) =
                repository.save(SaveReadingIntent.Add(date, parseMeter(value, Locale.US), today)) as SaveReadingResult.Saved
            add(replacementDay.minusDays(1), "1100.0000")
            val final = add(replacementDay, "1105.0000").snapshot.readings.first()
            val target = replacementTarget(repository.observeReadings().first().tracker, replacementDay)
            val replaced = (repository.replaceMeter(ReplaceMeterIntent(target, replacementDay))
                as ReplaceMeterResult.Replaced).snapshot.tracker
            val boundary = replaced.replacements.single()
            assertEquals("0", replaced.readings.single { it.id == boundary.initialReadingId }.cubicMetres.toString())
            add(today, "5.0000")
            val before = repository.observeReadings().first().tracker
            assertEquals(listOf("5.0000", "5.0000"), readingIntervals(before).map { it.cubicMetres.toString() })
            assertEquals("10.0000", readingIntervals(before).map { it.cubicMetres }.reduce { a, b -> a + b }.toString())
            database.close()
            database = open()
            repository = RoomReadingRepository(database)
            assertEquals(before, repository.observeReadings().first().tracker)
            assertEquals(boundary.newInstallationId, before.currentInstallation.id)
            assertEquals(ORIGINAL_INSTALLATION_ID, before.readings.single { it.id == final.id }.installationId)
            assertEquals(DeleteReadingResult.Protected, repository.delete(final.id))
            assertEquals(DeleteReadingResult.Protected, repository.delete(boundary.initialReadingId))
            val edit = repository.save(SaveReadingIntent.Edit(boundary.initialReadingId, parseMeter("1.0000", Locale.US), today)) as SaveReadingResult.Saved
            assertEquals(listOf("5.0000", "4.0000"), readingIntervals(edit.snapshot.tracker).map { it.cubicMetres.toString() })
            assertEquals(replaced.replacements, edit.snapshot.tracker.replacements)
            assertEquals(ReadingError.ABOVE_NEXT,
                (repository.save(SaveReadingIntent.Edit(boundary.initialReadingId, parseMeter("6", Locale.US), today)) as SaveReadingResult.Invalid).errors.value)
            val corrected = repository.observeReadings().first().tracker
            database.close()
            database = open()
            val reopened = RoomReadingRepository(database).observeReadings().first().tracker
            assertEquals(corrected, reopened)
            assertEquals(before.installations, reopened.installations)
            assertEquals(before.replacements, reopened.replacements)
            assertEquals(before.readings.filter { it.installationId == ORIGINAL_INSTALLATION_ID },
                reopened.readings.filter { it.installationId == ORIGINAL_INSTALLATION_ID })
            assertEquals(listOf("5.0000", "4.0000"), readingIntervals(reopened).map { it.cubicMetres.toString() })
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun databaseReopenRetainsEditsAndDeletions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-readings-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, ReadingDatabase::class.java, name)
            .addMigrations(ReadingDatabase.MIGRATION_1_2).addCallback(ReadingDatabase.INITIALIZE).build()
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
