package com.trackgas.app.data

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
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
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReadingDatabase::class.java).build()
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
        val decorated = object : ReadingDao by dao {
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
        assertEquals(1, dao.getAll().size)
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
}
