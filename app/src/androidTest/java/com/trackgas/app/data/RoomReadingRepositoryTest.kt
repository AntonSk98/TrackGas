package com.trackgas.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackgas.app.tracking.DeleteReadingResult
import com.trackgas.app.tracking.ReadingError
import com.trackgas.app.tracking.ReadingStorageException
import com.trackgas.app.tracking.SaveReadingResult
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomReadingRepositoryTest {
    private lateinit var database: ReadingDatabase
    private lateinit var repository: RoomReadingRepository
    private val today = LocalDate.parse("2026-10-26")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), ReadingDatabase::class.java,
        ).build()
        repository = RoomReadingRepository(database)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun save(date: String, value: String, id: String? = null): SaveReadingResult =
        repository.save(id, LocalDate.parse(date), value, today, Locale.US)

    @Test
    fun validatesNeighboursAndMissingTargetsWithinTransactions() = runBlocking {
        save("2026-10-01", "100")
        val last = (save("2026-10-04", "120") as SaveReadingResult.Saved).readings.first()
        val invalid = save("2026-10-02", "125") as SaveReadingResult.Invalid
        assertEquals(ReadingError.ABOVE_NEXT, invalid.errors.value)
        val invalidEdit = save("2026-10-04", "99", last.id) as SaveReadingResult.Invalid
        assertEquals(ReadingError.BELOW_PREVIOUS, invalidEdit.errors.value)
        assertEquals(ReadingError.MISSING_READING, (save("2026-10-02", "101", "missing") as SaveReadingResult.Invalid).errors.value)
        assertEquals(DeleteReadingResult.Missing, repository.delete("missing"))
        assertEquals(2, repository.observeReadings().first().size)
    }

    @Test
    fun failedWriteLeavesPreviouslyCommittedReadingsUntouched() = runBlocking {
        save("2026-10-01", "100")
        val before = repository.observeReadings().first()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_insert BEFORE INSERT ON readings BEGIN SELECT RAISE(ABORT, 'rejected'); END",
        )
        try {
            save("2026-10-02", "101")
            fail("Expected the transaction to fail")
        } catch (_: ReadingStorageException) {
            assertEquals(before, repository.observeReadings().first())
        }
    }

    @Test
    fun concurrentWritesCannotCreateDuplicateDates() = runBlocking {
        val results = listOf(
            async { save("2026-10-01", "100") },
            async { save("2026-10-01", "100") },
        ).awaitAll()
        assertEquals(1, results.count { it is SaveReadingResult.Saved })
        assertEquals(ReadingError.DUPLICATE_DATE, (results.single { it is SaveReadingResult.Invalid } as SaveReadingResult.Invalid).errors.date)
        assertEquals(1, repository.observeReadings().first().size)
    }

    @Test
    fun editRetainsOriginalDateAndDeletePersists() = runBlocking {
        val original = (save("2026-10-01", "100.0100") as SaveReadingResult.Saved).readings.single()
        val changed = (save("2026-10-02", "101.0200", original.id) as SaveReadingResult.Saved).readings.single()
        assertEquals(original.id, changed.id)
        assertEquals(original.date, changed.date)
        assertEquals("101.0200", changed.cubicMetres.toString())
        assertTrue(repository.delete(original.id) is DeleteReadingResult.Deleted)
        assertTrue(repository.observeReadings().first().isEmpty())
    }

    @Test
    fun malformedStoredValueIsReportedAsStorageFailure() = runBlocking {
        database.readings().insert(ReadingEntity("bad", "2026-10-01", "not-a-number"))
        try {
            repository.observeReadings().first()
            fail("Expected malformed data to fail loading")
        } catch (error: ReadingStorageException) {
            assertTrue(error.cause is NumberFormatException)
        }
    }
}
