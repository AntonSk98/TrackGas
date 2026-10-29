package com.trackgas.app.data

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.trackgas.app.tracking.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OrderedRepositoryTest {
    private lateinit var database: ReadingDatabase
    private val today = LocalDate.parse("2026-10-26")
    private fun intent(day: Int, value: String) = SaveReadingIntent.Add(today.withDayOfMonth(day), ParsedMeter.Value(value.toBigDecimal()), today)

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReadingDatabase::class.java)
            .addCallback(ReadingDatabase.INITIALIZE).build()
    }
    @After fun tearDown() = database.close()

    private suspend fun awaitState(condition: () -> Boolean) = withTimeout(5_000) {
        while (!condition()) delay(10)
    }

    @Test fun bufferedOldRoomPayloadIsOnlyATriggerForFreshLockedCapture() = runBlocking {
        val dao = database.readings()
        val buffered = listOf(ReadingEntity("old", "2026-10-01", "100"))
        dao.insert(buffered.single())
        val triggers = Channel<List<ReadingEntity>>(Channel.UNLIMITED)
        val decorated = object : ReadingDao by dao {
            override fun observeAll(): Flow<List<ReadingEntity>> = triggers.receiveAsFlow()
        }
        val repository = RoomReadingRepository(database, decorated) { "new" }
        val deliveries = Channel<CommittedSnapshot>(Channel.UNLIMITED)
        val observer = launch { repository.observeReadings().collect { deliveries.send(it) } }
        try {
            triggers.send(buffered)
            val initial = withTimeout(5_000) { deliveries.receive() }
            val committed = (repository.save(intent(2, "101")) as SaveReadingResult.Saved).snapshot
            triggers.send(buffered)
            val fresh = withTimeout(5_000) { deliveries.receive() }
            assertTrue(initial.revision < committed.revision)
            assertTrue(committed.revision < fresh.revision)
            assertEquals(committed.readings, fresh.readings)
            assertEquals(2, fresh.readings.size)
        } finally { observer.cancelAndJoin() }
    }

    @Test fun captureStartedBeforeMutationKeepsLowerRevisionAndSerializesTheCommit() = runBlocking {
        val dao = database.readings()
        dao.insert(ReadingEntity("old", "2026-10-01", "100"))
        val captured = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var firstRead = true
        var insertCalls = 0
        val decorated = object : ReadingDao by dao {
            override suspend fun insert(reading: ReadingEntity) {
                insertCalls++
                dao.insert(reading)
            }
            override suspend fun getAll(): List<ReadingEntity> {
                val rows = dao.getAll()
                if (firstRead) {
                    firstRead = false
                    captured.complete(Unit)
                    release.await()
                }
                return rows
            }
        }
        val repository = RoomReadingRepository(database, decorated) { "new" }
        val observation = async { repository.observeReadings().first() }
        withTimeout(5_000) { captured.await() }
        val mutation = async(start = CoroutineStart.UNDISPATCHED) { repository.save(intent(2, "101")) as SaveReadingResult.Saved }
        assertFalse(mutation.isCompleted)
        assertEquals(0, insertCalls)
        release.complete(Unit)
        val old = withTimeout(5_000) { observation.await() }
        val committed = withTimeout(5_000) { mutation.await().snapshot }
        assertTrue(old.revision < committed.revision)
        assertEquals(1, old.readings.size)
        assertEquals(2, committed.readings.size)
        val latest = repository.observeReadings().first()
        assertTrue(latest.revision > committed.revision)
        assertEquals(committed.readings, latest.readings)
    }

    @Test fun observationCannotCaptureOrIssueRevisionWhileMutationTransactionIsPending() = runBlocking {
        val dao = database.readings()
        val inserted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val decorated = object : ReadingDao by dao {
            override suspend fun insert(reading: ReadingEntity) {
                dao.insert(reading)
                inserted.complete(Unit)
                release.await()
            }
        }
        val repository = RoomReadingRepository(database, decorated) { "new" }
        val mutation = async { repository.save(intent(1, "100")) as SaveReadingResult.Saved }
        withTimeout(5_000) { inserted.await() }
        val observer = async(start = CoroutineStart.UNDISPATCHED) { repository.observeReadings().first() }
        assertFalse(mutation.isCompleted)
        assertFalse(observer.isCompleted)
        release.complete(Unit)
        val committed = withTimeout(5_000) { mutation.await().snapshot }
        val observed = withTimeout(5_000) { observer.await() }
        assertTrue(committed.revision < observed.revision)
        assertEquals(committed.readings, observed.readings)
    }

    @Test fun cancellationInsideSaveOrDeleteTransactionRollsBackWithoutACommittedResult() = runBlocking {
        for (deleting in listOf(false, true)) {
            val dao = database.readings()
            val row = ReadingEntity("existing", "2026-10-01", "100")
            if (deleting) dao.insert(row)
            val entered = CompletableDeferred<Unit>()
            val decorated = object : ReadingDao by dao {
                override suspend fun insert(reading: ReadingEntity) {
                    dao.insert(reading)
                    entered.complete(Unit)
                    awaitCancellation()
                }
                override suspend fun delete(id: String): Int {
                    dao.delete(id)
                    entered.complete(Unit)
                    awaitCancellation()
                }
            }
            val repository = RoomReadingRepository(database, decorated) { "new" }
            val job = launch { if (deleting) repository.delete(row.id) else repository.save(intent(2, "101")) }
            withTimeout(5_000) { entered.await() }
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertEquals(if (deleting) listOf(row) else emptyList<ReadingEntity>(), dao.getAll())
            val fresh = repository.observeReadings().first()
            assertEquals(1L, fresh.revision)
        }
    }

    @Test fun cancellationAfterCommitBeforeDeliveryLeavesObservationAuthoritative() = runBlocking {
        val repository = RoomReadingRepository(database)
        val committed = CompletableDeferred<CommittedSnapshot>()
        val caller = launch {
            val result = repository.save(intent(1, "100.0100")) as SaveReadingResult.Saved
            committed.complete(result.snapshot)
            awaitCancellation() // Caller delivery is withheld after the real transaction returned.
        }
        val snapshot = withTimeout(5_000) { committed.await() }
        caller.cancelAndJoin()
        assertTrue(caller.isCancelled)
        assertEquals(snapshot.readings, repository.observeReadings().first().readings)
        assertEquals(snapshot.readings, RoomReadingRepository(database).observeReadings().first().readings)
    }

    @Test fun twoViewModelsShareOneRevisionDomainAndConvergeWithoutResettingNavigation() = runBlocking {
        val repository = RoomReadingRepository(database)
        val firstStore = ViewModelStore()
        val secondStore = ViewModelStore()
        try {
            withContext(Dispatchers.Main) {
                val factory = TrackerViewModel.factory(repository)
                val first = ViewModelProvider(firstStore, factory)[TrackerViewModel::class.java]
                val second = ViewModelProvider(secondStore, factory)[TrackerViewModel::class.java]
                awaitState { first.state.loadState == ReadingLoadState.READY && second.state.loadState == ReadingLoadState.READY }
                first.selectPeriod(PeriodType.MONTH)
                second.selectPeriod(PeriodType.YEAR)
                first.selectTab(TrackerTab.HISTORY)
                first.openAdd(); first.save(first.today().minusDays(1), "100.0001", java.util.Locale.US)
                awaitState { first.state.dialog == ReadingDialog.Closed }
                second.openAdd(); second.save(second.today(), "110.1250", java.util.Locale.US)
                awaitState { second.state.dialog == ReadingDialog.Closed }
                awaitState { first.state.readings.size == 2 && first.state.readings == second.state.readings }
                assertEquals(TrackerTab.HISTORY, first.state.tab)
                assertEquals(PeriodType.MONTH, first.state.period.type)
                assertEquals(PeriodType.YEAR, second.state.period.type)
                assertEquals("110.1250", second.state.readings.first().cubicMetres.toString())
                first.openDelete(first.state.readings.first()); first.delete()
                awaitState { first.state.readings.size == 1 && first.state.readings == second.state.readings }
                assertNull(first.state.dashboard.total.cubicMetres)
                assertNull(second.state.dashboard.total.cubicMetres)
            }
        } finally {
            withContext(Dispatchers.Main) { firstStore.clear(); secondStore.clear() }
        }
    }

    private suspend fun prepareReplacement(repository: RoomReadingRepository): ReplaceMeterIntent {
        repository.save(intent(25, "1100.0000"))
        repository.save(intent(26, "1105.0000"))
        return ReplaceMeterIntent(replacementTarget(repository.observeReadings().first().tracker, today), today)
    }

    @Test fun delayedCaptureAndBufferedPayloadCannotResurrectPurgedData() = runBlocking {
        val dao = database.readings()
        val setup = RoomReadingRepository(database)
        prepareReplacement(setup)
        val payload = dao.getAll()
        val captured = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val triggers = Channel<List<ReadingEntity>>(Channel.UNLIMITED)
        var firstRead = true
        val decorated = object : ReadingDao by dao {
            override fun observeAll(): Flow<List<ReadingEntity>> = triggers.receiveAsFlow()
            override suspend fun getAll(): List<ReadingEntity> {
                val rows = dao.getAll()
                if (firstRead) {
                    firstRead = false
                    captured.complete(Unit)
                    release.await()
                }
                return rows
            }
        }
        val repository = RoomReadingRepository(database, decorated) { java.util.UUID.randomUUID().toString() }
        val snapshots = Channel<CommittedSnapshot>(Channel.UNLIMITED)
        val observation = launch { repository.observeReadings().collect { snapshots.send(it) } }
        try {
            triggers.send(payload)
            withTimeout(5_000) { captured.await() }
            val purge = async(start = CoroutineStart.UNDISPATCHED) { repository.purge() }
            assertFalse(purge.isCompleted)
            release.complete(Unit)
            val old = withTimeout(5_000) { snapshots.receive() }
            val empty = withTimeout(5_000) { purge.await() }
            triggers.send(payload)
            val fresh = withTimeout(5_000) { snapshots.receive() }
            assertTrue(old.revision < empty.revision && empty.revision < fresh.revision)
            assertEquals(TrackerData(), empty.tracker)
            assertEquals(empty.tracker, fresh.tracker)
        } finally { observation.cancelAndJoin() }
    }

    @Test fun cancellationDuringEachPurgeWriteRollsBackWithoutRevision() = runBlocking {
        val dao = database.readings()
        val setup = RoomReadingRepository(database)
        val replacement = prepareReplacement(setup)
        setup.replaceMeter(replacement)
        val before = setup.observeReadings().first().tracker
        for (stage in 0..3) {
            val entered = CompletableDeferred<Unit>()
            suspend fun pause(current: Int) {
                if (current == stage) { entered.complete(Unit); awaitCancellation() }
            }
            val decorated = object : ReadingDao by dao {
                override suspend fun deleteReplacements() { dao.deleteReplacements(); pause(0) }
                override suspend fun deleteReadings() { dao.deleteReadings(); pause(1) }
                override suspend fun deleteInstallations() { dao.deleteInstallations(); pause(2) }
                override suspend fun insertInstallation(installation: InstallationEntity) {
                    dao.insertInstallation(installation); pause(3)
                }
            }
            val repository = RoomReadingRepository(database, decorated) { "unused" }
            val job = launch { repository.purge() }
            withTimeout(5_000) { entered.await() }
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            val after = repository.observeReadings().first()
            assertEquals(1L, after.revision)
            assertEquals(before, after.tracker)
        }
    }

    @Test fun competingMutationsAreOrderedAroundPurgeAndStaleTargetsAreRejected() = runBlocking {
        val repository = RoomReadingRepository(database)
        val target = prepareReplacement(repository)
        val final = target.target.finalReading!!
        val before = repository.save(intent(24, "1099")) as SaveReadingResult.Saved
        assertTrue(before.snapshot.readings.isNotEmpty())
        val purge = async { repository.purge() }
        val edit = async { repository.save(SaveReadingIntent.Edit(final.id, ParsedMeter.Value("1106".toBigDecimal()), today)) }
        val replacement = async { repository.replaceMeter(target) }
        val add = async { repository.save(intent(23, "1098")) }
        val purged = purge.await()
        val edited = edit.await()
        val replaced = replacement.await()
        val addedDuringRace = add.await() as SaveReadingResult.Saved
        assertEquals(TrackerData(), purged.tracker)
        if (edited is SaveReadingResult.Saved) assertTrue(edited.snapshot.revision < purged.revision)
        else assertEquals(ReadingError.MISSING_READING, (edited as SaveReadingResult.Invalid).errors.value)
        if (replaced is ReplaceMeterResult.Replaced) assertTrue(replaced.snapshot.revision < purged.revision)
        else assertTrue(replaced is ReplaceMeterResult.Rejected)
        val latest = repository.observeReadings().first()
        if (addedDuringRace.snapshot.revision < purged.revision) assertEquals(TrackerData(), latest.tracker)
        else assertEquals(addedDuringRace.snapshot.tracker, latest.tracker)
        val added = repository.save(intent(25, "1100")) as SaveReadingResult.Saved
        assertTrue(added.snapshot.revision > purged.revision)
        assertEquals(ORIGINAL_INSTALLATION_ID, added.snapshot.readings.single { it.date.dayOfMonth == 25 }.installationId)
        assertEquals(added.snapshot.tracker, repository.observeReadings().first().tracker)
    }

    @Test fun purgePropagatesToIndependentSessionsWithoutResettingNavigation() = runBlocking {
        val repository = RoomReadingRepository(database)
        prepareReplacement(repository)
        val firstStore = ViewModelStore()
        val secondStore = ViewModelStore()
        try {
            withContext(Dispatchers.Main) {
                val factory = TrackerViewModel.factory(repository)
                val first = ViewModelProvider(firstStore, factory)[TrackerViewModel::class.java]
                val second = ViewModelProvider(secondStore, factory)[TrackerViewModel::class.java]
                awaitState { first.state.loadState == ReadingLoadState.READY && second.state.loadState == ReadingLoadState.READY }
                first.selectTab(TrackerTab.HISTORY)
                first.selectPeriod(PeriodType.MONTH)
                second.selectPeriod(PeriodType.YEAR)
                repository.purge()
                awaitState { first.state.tracker == TrackerData() && second.state.tracker == TrackerData() }
                assertEquals(TrackerTab.HISTORY, first.state.tab)
                assertEquals(PeriodType.MONTH, first.state.period.type)
                assertEquals(PeriodType.YEAR, second.state.period.type)
            }
        } finally {
            withContext(Dispatchers.Main) { firstStore.clear(); secondStore.clear() }
        }
    }

    @Test fun cancellationAtEveryReplacementWriteRollsBackFullAggregateWithoutRevision() = runBlocking {
        val dao = database.readings()
        val setup = RoomReadingRepository(database)
        val intent = prepareReplacement(setup)
        val before = setup.observeReadings().first().tracker
        for (stage in listOf("installation", "reading", "replacement")) {
            val entered = CompletableDeferred<Unit>()
            suspend fun pause(at: String) {
                if (stage == at) { entered.complete(Unit); awaitCancellation() }
            }
            val decorated = object : ReadingDao by dao {
                override suspend fun insertInstallation(installation: InstallationEntity) {
                    dao.insertInstallation(installation); pause("installation")
                }
                override suspend fun insert(reading: ReadingEntity) {
                    dao.insert(reading); pause("reading")
                }
                override suspend fun insertReplacement(replacement: ReplacementEntity) {
                    dao.insertReplacement(replacement); pause("replacement")
                }
            }
            val repository = RoomReadingRepository(database, decorated) { java.util.UUID.randomUUID().toString() }
            val job = launch { repository.replaceMeter(intent) }
            withTimeout(5_000) { entered.await() }
            job.cancelAndJoin()
            val after = repository.observeReadings().first()
            assertEquals(1L, after.revision)
            assertEquals(before, after.tracker)
        }
    }

    @Test fun competingReplacementsCommitAtMostOnceAcrossRepositorySessions() = runBlocking {
        val first = RoomReadingRepository(database)
        val second = RoomReadingRepository(database)
        val intent = prepareReplacement(first)
        val results = listOf(async { first.replaceMeter(intent) }, async { second.replaceMeter(intent) }).awaitAll()
        assertEquals(1, results.count { it is ReplaceMeterResult.Replaced })
        assertEquals(ReplacementError.OBSOLETE_TARGET, (results.single { it is ReplaceMeterResult.Rejected } as ReplaceMeterResult.Rejected).error)
        val tracker = first.observeReadings().first().tracker
        assertEquals(2, tracker.installations.size)
        assertEquals(1, tracker.replacements.size)
        assertEquals(3, tracker.readings.size)
        assertEquals(tracker, second.observeReadings().first().tracker)
    }

    @Test fun editAndDeleteRacesRevalidateFinalTargetsAndProtectCommittedBoundaries() = runBlocking {
        val repository = RoomReadingRepository(database)
        val intent = prepareReplacement(repository)
        val final = intent.target.finalReading!!
        val edited = repository.save(SaveReadingIntent.Edit(final.id, ParsedMeter.Value("1106".toBigDecimal()), today))
        assertTrue(edited is SaveReadingResult.Saved)
        assertEquals(ReplacementError.OBSOLETE_TARGET, (repository.replaceMeter(intent) as ReplaceMeterResult.Rejected).error)
        val freshIntent = intent.copy(target = replacementTarget(repository.observeReadings().first().tracker, today))
        val deletion = async { repository.delete(final.id) }
        val replacement = async { repository.replaceMeter(freshIntent) }
        val deleted = deletion.await()
        val replaced = replacement.await()
        when (deleted) {
            is DeleteReadingResult.Deleted -> {
                assertEquals(ReplacementError.OBSOLETE_TARGET, (replaced as ReplaceMeterResult.Rejected).error)
                assertTrue(repository.observeReadings().first().tracker.replacements.isEmpty())
            }
            DeleteReadingResult.Protected -> assertTrue(replaced is ReplaceMeterResult.Replaced)
            else -> fail("Unexpected delete outcome")
        }
    }

    @Test fun staleAddDraftResolvesLatestInstallationAndHistoricalMembership() = runBlocking {
        val repository = RoomReadingRepository(database)
        val replacement = prepareReplacement(repository)
        val staleDraft = SaveReadingIntent.Add(today.plusDays(1), ParsedMeter.Value("3.0000".toBigDecimal()), today.plusDays(1))
        val committed = (repository.replaceMeter(replacement) as ReplaceMeterResult.Replaced).snapshot
        val added = (repository.save(staleDraft) as SaveReadingResult.Saved).snapshot
        assertEquals(committed.tracker.currentInstallation.id, added.readings.first().installationId)
        val historical = (repository.save(intent(24, "1099.0000")) as SaveReadingResult.Saved).snapshot
        assertEquals(ORIGINAL_INSTALLATION_ID, historical.readings.single { it.date.dayOfMonth == 24 }.installationId)
        assertEquals(ReadingError.DUPLICATE_DATE, (repository.save(intent(26, "1105")) as SaveReadingResult.Invalid).errors.date)
    }

    @Test fun pendingReplacementCannotPublishPartialTablesAndDelayedPayloadRecapturesAllMetadata() = runBlocking {
        val dao = database.readings()
        val setup = RoomReadingRepository(database)
        val intent = prepareReplacement(setup)
        val oldPayload = dao.getAll()
        val inserted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val triggers = Channel<List<ReadingEntity>>(Channel.UNLIMITED)
        val decorated = object : ReadingDao by dao {
            override fun observeAll(): Flow<List<ReadingEntity>> = triggers.receiveAsFlow()
            override suspend fun insertReplacement(replacement: ReplacementEntity) {
                dao.insertReplacement(replacement)
                inserted.complete(Unit)
                release.await()
            }
        }
        val repository = RoomReadingRepository(database, decorated) { java.util.UUID.randomUUID().toString() }
        val mutation = async { repository.replaceMeter(intent) as ReplaceMeterResult.Replaced }
        withTimeout(5_000) { inserted.await() }
        val observed = async(start = CoroutineStart.UNDISPATCHED) { repository.observeReadings().first() }
        triggers.send(oldPayload)
        assertFalse(observed.isCompleted)
        release.complete(Unit)
        val committed = mutation.await().snapshot
        val fresh = observed.await()
        assertTrue(fresh.revision > committed.revision)
        assertEquals(committed.tracker, fresh.tracker)
        assertEquals(2, fresh.tracker.installations.size)
        assertEquals(1, fresh.tracker.replacements.size)
        assertEquals(3, fresh.readings.size)
    }

    @Test fun metadataOnlyWritesInvalidateReplacementAndInstallationObservation() = runBlocking {
        val repository = RoomReadingRepository(database)
        repository.replaceMeter(prepareReplacement(repository))
        val deliveries = Channel<CommittedSnapshot>(Channel.UNLIMITED)
        val failures = Channel<Throwable>(Channel.UNLIMITED)
        val observer = launch {
            repository.observeReadings().catch { failures.send(it) }.collect { deliveries.send(it) }
        }
        try {
            val initial = withTimeout(5_000) { deliveries.receive() }
            database.withTransaction {
                database.openHelper.writableDatabase.execSQL("UPDATE replacements SET id = 'changed-metadata'")
            }
            val changed = withTimeout(5_000) { deliveries.receive() }
            assertEquals(initial.readings, changed.readings)
            assertEquals("changed-metadata", changed.tracker.replacements.single().id)
            assertTrue(changed.revision > initial.revision)
            database.readings().insertInstallation(InstallationEntity("disconnected", "2026-10-27"))
            val failure = withTimeout(5_000) { failures.receive() }
            assertEquals(ReadingFailureCategory.STORED_DATA, (failure as ReadingStorageException).category)
        } finally { observer.cancelAndJoin() }
    }

    @Test fun competingFinalEditAndReplacementEitherRejectStaleTargetOrCorrectProtectedEndpoint() = runBlocking {
        val repository = RoomReadingRepository(database)
        val intent = prepareReplacement(repository)
        val final = intent.target.finalReading!!
        val results = awaitAll(
            async { repository.replaceMeter(intent) },
            async { repository.save(SaveReadingIntent.Edit(final.id, ParsedMeter.Value("1106.0000".toBigDecimal()), today)) },
        )
        assertTrue(results[1] is SaveReadingResult.Saved)
        val tracker = repository.observeReadings().first().tracker
        assertEquals("1106.0000", tracker.readings.single { it.id == final.id }.cubicMetres.toString())
        when (val replacement = results[0]) {
            is ReplaceMeterResult.Replaced -> {
                assertEquals(1, tracker.replacements.size)
                assertEquals(final.id, tracker.replacements.single().finalReadingId)
                assertEquals(DeleteReadingResult.Protected, repository.delete(final.id))
            }
            is ReplaceMeterResult.Rejected -> {
                assertEquals(ReplacementError.OBSOLETE_TARGET, replacement.error)
                assertTrue(tracker.replacements.isEmpty())
            }
            else -> fail("Unexpected replacement outcome")
        }
    }

    @Test fun aggregateReadTransactionCannotInterleaveExternalMetadataAndReadingCommit() = runBlocking {
        val setup = RoomReadingRepository(database)
        val before = (setup.replaceMeter(prepareReplacement(setup)) as ReplaceMeterResult.Replaced).snapshot.tracker
        val boundary = before.replacements.single()
        val dao = database.readings()
        val captured = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val decorated = object : ReadingDao by dao {
            override fun observeAll(): Flow<List<ReadingEntity>> = flowOf(emptyList())
            override suspend fun getAll(): List<ReadingEntity> {
                val rows = dao.getAll()
                captured.complete(Unit)
                release.await()
                return rows
            }
        }
        val repository = RoomReadingRepository(database, decorated) { "unused" }
        val observation = async { repository.observeReadings().first() }
        withTimeout(5_000) { captured.await() }
        val writer = async(start = CoroutineStart.UNDISPATCHED) {
            database.withTransaction {
                dao.update(before.readings.single { it.id == boundary.initialReadingId }.copy(cubicMetres = "1.0000".toBigDecimal()).toEntity())
                database.openHelper.writableDatabase.execSQL("UPDATE replacements SET id = 'external-commit'")
            }
        }
        assertFalse(writer.isCompleted)
        release.complete(Unit)
        val old = withTimeout(5_000) { observation.await() }
        assertEquals(before, old.tracker)
        withTimeout(5_000) { writer.await() }
        val latest = repository.observeReadings().first()
        assertEquals("external-commit", latest.tracker.replacements.single().id)
        assertEquals("1.0000", latest.readings.single { it.id == boundary.initialReadingId }.cubicMetres.toString())
        assertTrue(latest.revision > old.revision)
    }

    @Test fun cancelledReplacementCallerAfterCommitDoesNotUndoOrResubmitReplacement() = runBlocking {
        val repository = RoomReadingRepository(database)
        val intent = prepareReplacement(repository)
        val committed = CompletableDeferred<CommittedSnapshot>()
        val caller = launch {
            val result = repository.replaceMeter(intent) as ReplaceMeterResult.Replaced
            committed.complete(result.snapshot)
            awaitCancellation()
        }
        val snapshot = withTimeout(5_000) { committed.await() }
        caller.cancelAndJoin()
        assertEquals(snapshot.tracker, repository.observeReadings().first().tracker)
        assertEquals(snapshot.tracker, RoomReadingRepository(database).observeReadings().first().tracker)
        assertEquals(ReplacementError.OBSOLETE_TARGET, (repository.replaceMeter(intent) as ReplaceMeterResult.Rejected).error)
    }
}
