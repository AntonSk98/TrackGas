package com.trackgas.app.tracking

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class HistoryPagingState(
    val readings: List<Reading> = emptyList(),
    val context: TrackerData = TrackerData(emptyList()),
    val startingReadingIds: Set<String> = emptySet(),
    val cursor: HistoryCursor? = null,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: Boolean = false,
    val loaded: Boolean = false,
)

/** Loads History independently from Dashboard and retains successful pages across failures. */
internal class HistoryPagingController(
    private val scope: CoroutineScope,
    private val loadPage: suspend (HistoryCursor?) -> HistoryPage,
    private val fallbackInstallations: () -> List<MeterInstallation>,
    private val onStateChanged: (HistoryPagingState) -> Unit,
) {
    var state = HistoryPagingState()
        private set

    private var generation = 0L

    fun ensureLoaded() {
        if (!state.loaded) requestPage(reset = true)
    }

    fun loadMore() {
        requestPage(reset = !state.loaded)
    }

    fun retry() {
        if (!state.error) return
        update(state.copy(error = false))
        requestPage(reset = !state.loaded)
    }

    fun refreshAfterMutation(historySelected: Boolean) {
        if (!state.loaded) return
        generation++
        update(HistoryPagingState())
        if (historySelected) requestPage(reset = true)
    }

    private fun requestPage(reset: Boolean) {
        if (state.loading || (!reset && (!state.hasMore || state.error))) return

        val requestGeneration = ++generation
        val cursor = if (reset) null else state.cursor
        update(if (reset) HistoryPagingState(loading = true) else state.copy(loading = true, error = false))

        scope.launch {
            try {
                val page = loadPage(cursor)
                if (requestGeneration != generation) return@launch

                val existingReadings = if (reset) emptyList() else state.readings
                val readings = (existingReadings + page.readings).distinctBy { it.id }
                val contextReadings = (if (reset) emptyList() else state.context.readings) +
                    page.predecessorReadings
                update(
                    state.copy(
                        readings = readings,
                        context = TrackerData(
                            (readings + contextReadings).distinctBy { it.id },
                            page.installations.ifEmpty(fallbackInstallations),
                        ),
                        startingReadingIds = (if (reset) emptySet() else state.startingReadingIds) +
                            page.startingReadingIds,
                        cursor = page.nextCursor,
                        hasMore = page.nextCursor != null,
                        loading = false,
                        error = false,
                        loaded = true,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: ReadingStorageException) {
                if (requestGeneration == generation) {
                    update(state.copy(loading = false, error = true))
                }
            }
        }
    }

    private fun update(next: HistoryPagingState) {
        state = next
        onStateChanged(next)
    }
}
