package com.trackgas.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trackgas.app.tracking.DeleteReadingResult
import com.trackgas.app.tracking.ReadingError
import com.trackgas.app.tracking.ReadingStorageException
import com.trackgas.app.tracking.ReadingFailureCategory
import com.trackgas.app.tracking.SaveReadingResult
import com.trackgas.app.tracking.SaveReadingIntent
import com.trackgas.app.tracking.parseMeter
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
        repository.save(if (id == null) SaveReadingIntent.Add(LocalDate.parse(date), parseMeter(value, Locale.US), today)
        else SaveReadingIntent.Edit(id, parseMeter(value, Locale.US), today))

    @Test
    fun validatesNeighboursAndMissingTargetsWithinTransactions() = runBlocking {
        save("2026-10-01", "100")
        val last = (save("2026-10-04", "120") as SaveReadingResult.Saved).snapshot.readings.first()
        val invalid = save("2026-10-02", "125") as SaveReadingResult.Invalid
        assertEquals(ReadingError.ABOVE_NEXT, invalid.errors.value)
        val invalidEdit = save("2026-10-04", "99", last.id) as SaveReadingResult.Invalid
        assertEquals(ReadingError.BELOW_PREVIOUS, invalidEdit.errors.value)
        assertEquals(ReadingError.MISSING_READING, (save("2026-10-02", "101", "missing") as SaveReadingResult.Invalid).errors.value)
        assertEquals(DeleteReadingResult.Missing, repository.delete("missing"))
        assertEquals(2, repository.observeReadings().first().readings.size)
    }

    @Test
    fun failedWriteLeavesPreviouslyCommittedReadingsUntouched() = runBlocking {
        save("2026-10-01", "100")
        val before = repository.observeReadings().first().readings
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_insert BEFORE INSERT ON readings BEGIN SELECT RAISE(ABORT, 'rejected'); END",
        )
        try {
            save("2026-10-02", "101")
            fail("Expected the transaction to fail")
        } catch (error: ReadingStorageException) {
            assertEquals(ReadingFailureCategory.STORAGE_ACCESS, error.category)
            assertEquals(before, repository.observeReadings().first().readings)
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
        assertEquals(1, repository.observeReadings().first().readings.size)
    }

    @Test
    fun concurrentIncompatibleNeighboursCannotBothCommit() = runBlocking {
        save("2026-10-01", "100")
        save("2026-10-04", "120")
        val results = listOf(
            async { save("2026-10-02", "115") },
            async { save("2026-10-03", "110") },
        ).awaitAll()
        assertEquals(1, results.count { it is SaveReadingResult.Saved })
        val error = (results.single { it is SaveReadingResult.Invalid } as SaveReadingResult.Invalid).errors.value
        assertTrue(error == ReadingError.BELOW_PREVIOUS || error == ReadingError.ABOVE_NEXT)
        assertEquals(3, database.readings().getAll().size)
    }

    @Test
    fun addIdentityFunctionIsNotUsedByEditsAndRawScaleIsStored() = runBlocking {
        var calls = 0
        repository = RoomReadingRepository(database) { calls++; "generated" }
        val original = (save("2026-10-01", "100.0100") as SaveReadingResult.Saved).snapshot.readings.single()
        save("2026-10-10", "101.0200", original.id)
        assertEquals(1, calls)
        assertEquals(ReadingEntity("generated", "2026-10-01", "101.0200"), database.readings().getAll().single())
    }

    @Test
    fun editRetainsOriginalDateAndDeletePersists() = runBlocking {
        val original = (save("2026-10-01", "100.0100") as SaveReadingResult.Saved).snapshot.readings.single()
        val changed = (save("2026-10-02", "101.0200", original.id) as SaveReadingResult.Saved).snapshot.readings.single()
        assertEquals(original.id, changed.id)
        assertEquals(original.date, changed.date)
        assertEquals("101.0200", changed.cubicMetres.toString())
        assertTrue(repository.delete(original.id) is DeleteReadingResult.Deleted)
        assertTrue(repository.observeReadings().first().readings.isEmpty())
    }

    @Test
    fun malformedStoredValueIsReportedAsStorageFailure() = runBlocking {
        database.readings().insert(ReadingEntity("bad", "2026-10-01", "not-a-number"))
        try {
            repository.observeReadings().first()
            fail("Expected malformed data to fail loading")
        } catch (error: ReadingStorageException) {
            assertEquals(ReadingFailureCategory.STORED_DATA, error.category)
            assertTrue(error.cause?.cause is NumberFormatException)
        }
    }

    @Test
    fun malformedStoredFieldsFailLoadAndMutationsWithoutChangingAnyRows() = runBlocking {
        for (bad in listOf(
            ReadingEntity("", "2026-10-01", "100"),
            ReadingEntity("bad", "invalid-date", "100"),
            ReadingEntity("bad", "2026-10-01", "NaN"),
            ReadingEntity("bad", "2026-10-01", "-1"),
        )) {
            database.readings().insert(bad)
            val before = database.readings().getAll()
            for (operation in listOf<suspend () -> Unit>(
                { repository.observeReadings().first() },
                { save("2026-10-03", "110") },
                { repository.delete(bad.id) },
            )) {
                try {
                    operation()
                    fail("Expected malformed stored data")
                } catch (error: ReadingStorageException) {
                    assertEquals(ReadingFailureCategory.STORED_DATA, error.category)
                    assertNotNull(error.cause)
                    assertEquals(before, database.readings().getAll())
                }
            }
            database.readings().delete(bad.id)
        }
    }

    @Test
    fun decreasingStoredCollectionFailsLoadSaveDeleteAndRetryWithoutChangingRows() = runBlocking {
        val dao = database.readings()
        dao.insert(ReadingEntity("earlier", "2026-10-01", "120.0100"))
        dao.insert(ReadingEntity("later", "2026-10-02", "100.0100"))
        val before = dao.getAll()
        var identityCalls = 0
        repository = RoomReadingRepository(database) { identityCalls++; "new" }
        val operations = listOf<suspend () -> Unit>(
            { repository.observeReadings().first() },
            { save("2026-10-03", "130") },
            { save("2026-10-02", "125", "later") },
            { repository.delete("later") },
            { repository.delete("missing") },
        )
        repeat(2) {
            for (operation in operations) {
                try {
                    operation()
                    fail("Expected inconsistent stored collection")
                } catch (error: ReadingStorageException) {
                    assertEquals(ReadingFailureCategory.STORED_DATA, error.category)
                    assertEquals("Reading storage failed", error.message)
                    assertTrue(error.cause is StoredReadingDecodingException)
                    assertTrue(error.cause?.cause is IllegalArgumentException)
                    assertEquals(
                        "Invalid stored reading collection: DECREASING_VALUE",
                        error.cause?.cause?.message,
                    )
                }
                assertEquals(before, dao.getAll())
            }
        }
        assertEquals(0, identityCalls)
        // Only the test repairs its fixture; failed operations must not issue revisions.
        dao.update(ReadingEntity("later", "2026-10-02", "125.0100"))
        val firstValid = repository.observeReadings().first()
        assertEquals(1L, firstValid.revision)
        assertEquals(2, firstValid.readings.size)
    }

    @Test
    fun unrelatedUpdateInvariantIsNotARetryableStorageFailure() = runBlocking {
        val original = (save("2026-10-01", "100") as SaveReadingResult.Saved).snapshot.readings.single()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER ignore_update BEFORE UPDATE ON readings BEGIN SELECT RAISE(IGNORE); END",
        )
        try {
            save("2026-10-01", "101", original.id)
            fail("Expected update-count invariant")
        } catch (error: IllegalStateException) {
            assertEquals("Reading disappeared during update", error.message)
        }
        assertEquals("100", database.readings().getAll().single().cubicMetres)
        val sql = android.database.SQLException("private")
        val failure = classifyReadingFailure(sql) as ReadingStorageException
        assertSame(sql, failure.cause)
        assertEquals(ReadingFailureCategory.STORAGE_ACCESS, failure.category)
    }
}
