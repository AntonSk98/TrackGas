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
import com.trackgas.app.tracking.*
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
        ).addCallback(ReadingDatabase.INITIALIZE).build()
        repository = RoomReadingRepository(database)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun save(date: String, value: String, id: String? = null): SaveReadingResult =
        repository.save(if (id == null) SaveReadingIntent.Add(LocalDate.parse(date), parseMeter(value, Locale.US), today)
        else SaveReadingIntent.Edit(id, parseMeter(value, Locale.US), today))

    @Test
    fun purgeRemovesMultipleReplacementHistoriesAndReseedsOriginalInstallation() = runBlocking {
        save("2026-10-19", "100")
        val final = (save("2026-10-20", "110") as SaveReadingResult.Saved).snapshot.readings.first()
        val firstDay = LocalDate.parse("2026-10-20")
        val first = repository.observeReadings().first()
        val target = replacementTarget(first.tracker, firstDay)
        assertTrue(repository.replaceMeter(ReplaceMeterIntent(target, firstDay)) is ReplaceMeterResult.Replaced)
        save("2026-10-22", "5")
        val secondDay = LocalDate.parse("2026-10-22")
        val second = repository.observeReadings().first()
        assertTrue(repository.replaceMeter(ReplaceMeterIntent(replacementTarget(second.tracker, secondDay), secondDay)) is ReplaceMeterResult.Replaced)
        assertEquals(DeleteReadingResult.Protected, repository.delete(final.id))
        val populated = repository.observeReadings().first()
        assertEquals(2, populated.tracker.replacements.size)

        val purged = repository.purge()
        assertTrue(purged.revision > populated.revision)
        assertEquals(TrackerData(), purged.tracker)
        assertEquals(TrackerData(), repository.observeReadings().first().tracker)
        val repeated = repository.purge()
        assertTrue(repeated.revision > purged.revision)
        assertEquals(TrackerData(), repeated.tracker)
    }

    @Test
    fun purgeFailuresAndCancellationRollbackEveryStage() = runBlocking {
        save("2026-10-19", "100")
        save("2026-10-20", "110")
        val date = LocalDate.parse("2026-10-20")
        val tracker = repository.observeReadings().first().tracker
        repository.replaceMeter(ReplaceMeterIntent(replacementTarget(tracker, date), date))
        val dao = database.readings()

        for (stage in 0..3) {
            for (kind in 0..2) {
                val decorated = object : ReadingDao by dao {
                    fun fail(current: Int) {
                        if (current != stage) return
                        when (kind) {
                            0 -> throw android.database.SQLException("injected")
                            1 -> throw kotlinx.coroutines.CancellationException("injected")
                            else -> throw IllegalStateException("injected")
                        }
                    }
                    override suspend fun deleteReplacements() { dao.deleteReplacements(); fail(0) }
                    override suspend fun deleteReadings() { dao.deleteReadings(); fail(1) }
                    override suspend fun deleteInstallations() { dao.deleteInstallations(); fail(2) }
                    override suspend fun insertInstallation(installation: InstallationEntity) {
                        dao.insertInstallation(installation)
                        fail(3)
                    }
                }
                val subject = RoomReadingRepository(database, decorated) { "unused" }
                val before = subject.observeReadings().first()
                try {
                    subject.purge()
                    fail("Purge should fail at stage $stage")
                } catch (error: Exception) {
                    when (kind) {
                        0 -> assertEquals(ReadingFailureCategory.STORAGE_ACCESS, (error as ReadingStorageException).category)
                        1 -> assertTrue(error is kotlinx.coroutines.CancellationException)
                        else -> assertTrue(error is IllegalStateException)
                    }
                }
                val after = subject.observeReadings().first()
                assertEquals(before.tracker, after.tracker)
                assertEquals(before.tracker.currentInstallation, after.tracker.currentInstallation)
                assertEquals(before.revision + 1, after.revision)
            }
        }
    }

    @Test
    fun purgeRejectsMalformedStoredDataWithoutDeletingIt() = runBlocking {
        val malformed = ReadingEntity("bad", "2026-10-01", "not-a-number")
        database.readings().insert(malformed)
        try {
            repository.purge()
            fail("Expected stored-data failure")
        } catch (error: ReadingStorageException) {
            assertEquals(ReadingFailureCategory.STORED_DATA, error.category)
        }
        assertEquals(listOf(malformed), database.readings().getAll())
        assertEquals(listOf(InstallationEntity(ORIGINAL_INSTALLATION_ID)), database.readings().getInstallations())
    }

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

    private suspend fun replacementIntent(): ReplaceMeterIntent =
        ReplaceMeterIntent(replacementTarget(repository.observeReadings().first().tracker, today), today)

    @Test fun replacementCommitsAllCollectionsWithAutomaticZeroAndProtectedEndpoints() = runBlocking {
        save("2026-10-25", "1100.0000")
        val final = (save("2026-10-26", "1105.0000") as SaveReadingResult.Saved).snapshot.readings.first()
        val intent = replacementIntent()
        val result = repository.replaceMeter(intent) as ReplaceMeterResult.Replaced
        val tracker = result.snapshot.tracker
        assertEquals(2, tracker.installations.size)
        assertEquals(1, tracker.replacements.size)
        assertEquals(3, tracker.readings.size)
        val boundary = tracker.replacements.single()
        assertEquals(final.id, boundary.finalReadingId)
        val initial = tracker.readings.single { it.id == boundary.initialReadingId }
        assertEquals("0", initial.cubicMetres.toString())
        assertEquals("1105.0000", tracker.readings.single { it.id == final.id }.cubicMetres.toString())
        assertEquals(tracker.currentInstallation.id, initial.installationId)
        assertEquals(today, initial.date)
        assertEquals(ReplacementError.OBSOLETE_TARGET, (repository.replaceMeter(intent) as ReplaceMeterResult.Rejected).error)
        val repeated = replacementIntent()
        assertEquals(ReplacementError.ALREADY_REPLACED, (repository.replaceMeter(repeated) as ReplaceMeterResult.Rejected).error)
        for (id in listOf(final.id, initial.id)) assertEquals(DeleteReadingResult.Protected, repository.delete(id))
        assertEquals(tracker, repository.observeReadings().first().tracker)
        assertTrue(result.snapshot.revision < repository.observeReadings().first().revision)
    }

    @Test fun replacementRejectsMissingChangedDeletedAndStaleFinalTargetsWithoutWrites() = runBlocking {
        assertEquals(ReplacementError.MISSING_FINAL, (repository.replaceMeter(replacementIntent()) as ReplaceMeterResult.Rejected).error)
        val final = (save("2026-10-26", "1105") as SaveReadingResult.Saved).snapshot.readings.single()
        val original = replacementIntent()
        assertEquals(ReplacementError.STALE_DATE, (repository.replaceMeter(original.copy(today = today.plusDays(1))) as ReplaceMeterResult.Rejected).error)
        save("2026-10-26", "1106", final.id)
        assertEquals(ReplacementError.OBSOLETE_TARGET, (repository.replaceMeter(original) as ReplaceMeterResult.Rejected).error)
        val changed = replacementIntent()
        repository.delete(final.id)
        assertEquals(ReplacementError.OBSOLETE_TARGET, (repository.replaceMeter(changed) as ReplaceMeterResult.Rejected).error)
        val tracker = repository.observeReadings().first().tracker
        assertEquals(1, tracker.installations.size)
        assertTrue(tracker.replacements.isEmpty())
        assertTrue(tracker.readings.isEmpty())
    }

    @Test fun automaticZeroOnLaterReplacementDoesNotResetStoredNonzeroBaseline() = runBlocking {
        save("2026-10-26", "1105.0000")
        val first = (repository.replaceMeter(replacementIntent()) as ReplaceMeterResult.Replaced).snapshot.tracker
        val baselineId = first.replacements.single().initialReadingId
        repository.save(SaveReadingIntent.Edit(baselineId, parseMeter("12.5000", Locale.US), today))
        val tomorrow = today.plusDays(1)
        repository.save(SaveReadingIntent.Add(tomorrow, parseMeter("18.2500", Locale.US), tomorrow))
        val before = repository.observeReadings().first().tracker
        val next = (repository.replaceMeter(ReplaceMeterIntent(replacementTarget(before, tomorrow), tomorrow))
            as ReplaceMeterResult.Replaced).snapshot.tracker
        assertEquals("12.5000", next.readings.single { it.id == baselineId }.cubicMetres.toPlainString())
        val newBaselineId = next.replacements.last().initialReadingId
        assertEquals("0", next.readings.single { it.id == newBaselineId }.cubicMetres.toPlainString())
        assertEquals(before.readings, next.readings.filterNot { it.id == newBaselineId })
        assertEquals(next, RoomReadingRepository(database).observeReadings().first().tracker)
    }

    @Test fun failureAtEachReplacementWriteRollsBackAllTablesAndDoesNotIssueRevision() = runBlocking {
        save("2026-10-26", "1105.0000")
        for (table in listOf("installations", "readings", "replacements")) {
            val intent = replacementIntent()
            val before = repository.observeReadings().first()
            val sql = database.openHelper.writableDatabase
            sql.execSQL("CREATE TRIGGER reject_replacement BEFORE INSERT ON $table BEGIN SELECT RAISE(ABORT, 'rejected'); END")
            try {
                repository.replaceMeter(intent)
                fail("Expected replacement write failure")
            } catch (error: ReadingStorageException) {
                assertEquals(ReadingFailureCategory.STORAGE_ACCESS, error.category)
            } finally { sql.execSQL("DROP TRIGGER reject_replacement") }
            val after = repository.observeReadings().first()
            assertEquals(before.tracker, after.tracker)
            assertEquals(before.revision + 1, after.revision)
        }
    }

    @Test fun invalidMetadataAndEndpointsFailEveryBoundaryWithoutRepairOrRevision() = runBlocking {
        save("2026-10-26", "1105")
        repository.replaceMeter(replacementIntent())
        val valid = repository.observeReadings().first().tracker
        val boundary = valid.replacements.single()
        val sql = database.openHelper.writableDatabase
        val corruptions = listOf(
            "UPDATE readings SET installationId = '' WHERE id = '${boundary.initialReadingId}'" to
                "UPDATE readings SET installationId = '${boundary.newInstallationId}' WHERE id = '${boundary.initialReadingId}'",
            "UPDATE readings SET installationId = 'absent' WHERE id = '${boundary.initialReadingId}'" to
                "UPDATE readings SET installationId = '${boundary.newInstallationId}' WHERE id = '${boundary.initialReadingId}'",
            "UPDATE installations SET id = '' WHERE id = '${boundary.newInstallationId}'" to
                "UPDATE installations SET id = '${boundary.newInstallationId}' WHERE id = ''",
            "UPDATE installations SET startDate = 'not-a-date' WHERE id = '${boundary.newInstallationId}'" to
                "UPDATE installations SET startDate = '$today' WHERE id = '${boundary.newInstallationId}'",
            "UPDATE replacements SET id = ''" to "UPDATE replacements SET id = '${boundary.id}'",
            "UPDATE replacements SET date = 'not-a-date'" to "UPDATE replacements SET date = '$today'",
            "UPDATE replacements SET date = '2026-10-25'" to "UPDATE replacements SET date = '$today'",
            "UPDATE replacements SET finalReadingId = 'missing'" to
                "UPDATE replacements SET finalReadingId = '${boundary.finalReadingId}'",
            "UPDATE replacements SET finalReadingId = '${boundary.initialReadingId}'" to
                "UPDATE replacements SET finalReadingId = '${boundary.finalReadingId}'",
            "INSERT INTO installations (id, startDate) VALUES ('disconnected', '2026-10-27')" to
                "DELETE FROM installations WHERE id = 'disconnected'",
        )
        for ((corrupt, repair) in corruptions) {
            sql.execSQL("PRAGMA foreign_keys=OFF")
            sql.execSQL(corrupt)
            sql.execSQL("PRAGMA foreign_keys=ON")
            val beforeRows = database.readings().getAll()
            val beforeInstallations = database.readings().getInstallations()
            val beforeReplacements = database.readings().getReplacements()
            val brokenRepository = RoomReadingRepository(database)
            repeat(2) {
                for (operation in listOf<suspend () -> Unit>(
                    { brokenRepository.observeReadings().first() },
                    { brokenRepository.save(SaveReadingIntent.Add(today.plusDays(1), parseMeter("3", Locale.US), today.plusDays(1))) },
                    { brokenRepository.delete(boundary.finalReadingId) },
                    { brokenRepository.replaceMeter(ReplaceMeterIntent(replacementTarget(valid, today), today)) },
                )) {
                    try { operation(); fail("Expected stored-data failure") } catch (error: ReadingStorageException) {
                        assertEquals(ReadingFailureCategory.STORED_DATA, error.category)
                        assertEquals("Reading storage failed", error.message)
                        assertTrue(error.cause is StoredReadingDecodingException)
                    }
                    assertEquals(beforeRows, database.readings().getAll())
                    assertEquals(beforeInstallations, database.readings().getInstallations())
                    assertEquals(beforeReplacements, database.readings().getReplacements())
                }
            }
            sql.execSQL("PRAGMA foreign_keys=OFF")
            sql.execSQL(repair)
            sql.execSQL("PRAGMA foreign_keys=ON")
            val restored = brokenRepository.observeReadings().first()
            assertEquals(1L, restored.revision)
            assertEquals(valid, restored.tracker)
        }
    }
}
