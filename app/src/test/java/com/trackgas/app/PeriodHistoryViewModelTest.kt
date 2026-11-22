package com.trackgas.app

import androidx.lifecycle.ViewModelStore
import com.trackgas.app.tracking.*
import kotlinx.coroutines.CoroutineDispatcher
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodHistoryViewModelTest {
    private val today = LocalDate.parse("2026-10-26")
    private val viewModelStores = mutableListOf<ViewModelStore>()

    @Before fun setUp() = kotlinx.coroutines.Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() {
        viewModelStores.forEach(ViewModelStore::clear)
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test fun dashboardUsesTheSelectedRangeAndIgnoresLatePeriodResults() = runTest {
        val repository = PeriodHistoryRepository()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(repository, dispatcher)
        val initial = repository.dashboardRequests.receive()
        assertEquals(viewModel.state.period.range, initial.range)

        val firstReading = reading("first", today.minusDays(1), "10")
        initial.results.send(DashboardSnapshot(TrackerData(listOf(firstReading))))
        runCurrent()
        val oldProjection = viewModel.state.dashboard

        viewModel.selectPeriod(PeriodType.MONTH)
        val selected = repository.dashboardRequests.receive()
        assertEquals(viewModel.state.period.range, selected.range)
        val selectedReadings = listOf(
            reading("earlier", today.minusDays(2), "10"),
            reading("later", today, "15"),
        )
        initial.results.send(DashboardSnapshot(TrackerData(listOf(firstReading))))
        selected.results.send(DashboardSnapshot(TrackerData(selectedReadings)))
        runCurrent()

        assertEquals(selectedReadings.sortedByDescending { it.date }, viewModel.state.readings)
        assertEquals(dashboard(selectedReadings, viewModel.state.period), viewModel.state.dashboard)
        assertFalse(viewModel.state.dashboard === oldProjection)
    }

    @Test fun historyLoadsOnDemandAndRetryKeepsPagesWithoutDuplicates() = runTest {
        val repository = PeriodHistoryRepository()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(repository, dispatcher)
        val dashboard = repository.dashboardRequests.receive()
        dashboard.results.send(DashboardSnapshot(TrackerData()))
        runCurrent()
        assertTrue(repository.historyRequests.tryReceive().isFailure)

        viewModel.selectTab(TrackerTab.HISTORY)
        val first = repository.historyRequests.receive()
        assertEquals(null, first.cursor)
        val newest = reading("newest", today, "12")
        val cursor = HistoryCursor(today, today.minusDays(5), "meter", newest.id)
        first.result.complete(historyPage(newest, cursor))
        runCurrent()

        viewModel.loadMoreHistory()
        val failedPage = repository.historyRequests.receive()
        assertEquals(cursor, failedPage.cursor)
        failedPage.result.completeExceptionally(ReadingStorageException(IllegalStateException()))
        runCurrent()
        assertEquals(listOf(newest), viewModel.state.history.readings)
        assertTrue(viewModel.state.history.error)

        viewModel.retryHistory()
        val retry = repository.historyRequests.receive()
        assertEquals(cursor, retry.cursor)
        val oldest = reading("oldest", today.minusDays(10), "8")
        retry.result.complete(
            HistoryPage(
                readings = listOf(oldest, newest),
                predecessorReadings = emptyList(),
                installations = listOf(MeterInstallation("meter", today.minusDays(5))),
                startingReadingIds = setOf("start"),
                nextCursor = null,
            ),
        )
        runCurrent()

        assertEquals(listOf("newest", "oldest"), viewModel.state.history.readings.map { it.id })
        assertFalse(viewModel.state.history.error)
        assertFalse(viewModel.state.history.hasMore)
    }

    private fun historyPage(reading: Reading, cursor: HistoryCursor) = HistoryPage(
        readings = listOf(reading),
        predecessorReadings = emptyList(),
        installations = listOf(MeterInstallation("meter", today.minusDays(5))),
        startingReadingIds = emptySet(),
        nextCursor = cursor,
    )

    private fun reading(id: String, date: LocalDate, value: String) =
        Reading(id, date, value.toBigDecimal(), "meter")

    private fun createViewModel(repository: ReadingRepository, dispatcher: CoroutineDispatcher): TrackerViewModel {
        val store = ViewModelStore()
        val viewModel = TrackerViewModel(
            repository = repository,
            ioDispatcher = dispatcher,
            projectionDispatcher = dispatcher,
            currentDate = { today },
        )
        store.put("tracker", viewModel)
        viewModelStores += store
        return viewModel
    }

    private class PeriodHistoryRepository : ReadingRepository {
        override val usesPeriodScopedDashboardData = true
        val dashboardRequests = Channel<DashboardRequest>(Channel.UNLIMITED)
        val historyRequests = Channel<HistoryRequest>(Channel.UNLIMITED)

        override fun observeReadings(): Flow<CommittedSnapshot> = emptyFlow()

        override fun observeDashboardData(range: DateRange): Flow<DashboardSnapshot> = flow {
            val request = DashboardRequest(range, Channel(Channel.UNLIMITED))
            dashboardRequests.send(request)
            for (snapshot in request.results) emit(snapshot)
        }

        override suspend fun historyPage(cursor: HistoryCursor?, pageSize: Int): HistoryPage {
            val request = HistoryRequest(cursor, CompletableDeferred())
            historyRequests.send(request)
            return request.result.await()
        }

        override suspend fun save(intent: SaveReadingIntent): SaveReadingResult = error("Not used")
        override suspend fun delete(id: String): DeleteReadingResult = error("Not used")
        override suspend fun replaceMeter(intent: ReplaceMeterIntent): ReplaceMeterResult = error("Not used")
        override suspend fun purge(): CommittedSnapshot = error("Not used")
    }

    private data class DashboardRequest(val range: DateRange, val results: Channel<DashboardSnapshot>)
    private data class HistoryRequest(val cursor: HistoryCursor?, val result: CompletableDeferred<HistoryPage>)
}
