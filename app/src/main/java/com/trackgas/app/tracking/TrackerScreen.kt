package com.trackgas.app.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.trackgas.app.R
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Captures the Add/Edit target and its opening date independently of subsequent recompositions. */
private data class FormTarget(val reading: Reading?, val initialDate: LocalDate)

private val readingSaver = Saver<Reading?, ArrayList<String>>(
    save = { reading ->
        if (reading == null) arrayListOf() else
            arrayListOf(reading.id, reading.date.toString(), reading.cubicMetres.toString())
    },
    restore = { if (it.isEmpty()) null else Reading(it[0], LocalDate.parse(it[1]), it[2].toBigDecimal()) },
)
private val formTargetSaver = Saver<FormTarget?, ArrayList<String>>(
    save = { target ->
        if (target == null) arrayListOf() else
            arrayListOf(target.initialDate.toString(), target.reading?.id.orEmpty(), target.reading?.cubicMetres?.toString().orEmpty())
    },
    restore = {
        if (it.isEmpty()) null else {
            val date = LocalDate.parse(it[0])
            FormTarget(if (it[1].isEmpty()) null else Reading(it[1], date, it[2].toBigDecimal()), date)
        }
    },
)
/**
 * Compose entry point for Dashboard/History navigation and reading-management dialogs.
 *
 * [state] and event callbacks are supplied by the host; this composable has no dependency
 * on a ViewModel. Local state controls drafts, confirmation dialogs, and each tab's scroll
 * position. [currentDate] is called when Add opens and when rendering date limits, while
 * [onSave] and [onDelete] start operations whose outcomes are observed in [state].
 * Add is shared by both tabs; Edit/Delete are exposed only by History.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(
    state: TrackerState,
    currentDate: () -> LocalDate,
    onSelectTab: (TrackerTab) -> Unit,
    onSelectPeriod: (PeriodType) -> Unit,
    onNavigate: (Long) -> Unit,
    onRetryLoad: () -> Unit,
    onConsumeOperation: () -> Unit,
    onSave: (String?, LocalDate?, String, Locale) -> Unit,
    onDelete: (String) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var sessionToken by rememberSaveable { mutableStateOf(state.sessionId) }
    var form by rememberSaveable(stateSaver = formTargetSaver) { mutableStateOf<FormTarget?>(null) }
    var deleting by rememberSaveable(stateSaver = readingSaver) { mutableStateOf<Reading?>(null) }
    val sameSession = sessionToken == state.sessionId
    val pending = state.operation == ReadingOperation.Pending
    val completed = state.operation == ReadingOperation.Saved || state.operation == ReadingOperation.Deleted
    val ready = sameSession && state.loadState == ReadingLoadState.READY
    val canOpen = ready && (state.operation == ReadingOperation.Idle || completed)
    val canSubmit = ready && !pending && !completed
    LaunchedEffect(sameSession) {
        if (!sameSession) {
            form = null
            deleting = null
            sessionToken = state.sessionId
        }
    }
    val dashboardScroll = rememberLazyListState()
    val historyScroll = rememberLazyListState()
    val pager = rememberPagerState(initialPage = state.tab.ordinal) { TrackerTab.entries.size }
    val scope = rememberCoroutineScope()
    var tabAnimation by remember { mutableStateOf<Job?>(null) }
    var navigationReady by remember { mutableStateOf(false) }
    val selectTab by rememberUpdatedState(onSelectTab)
    // The ViewModel, not restored pager saved state, defines the selection after process death.
    LaunchedEffect(pager) {
        pager.scrollToPage(state.tab.ordinal)
        navigationReady = true
        snapshotFlow { pager.settledPage }
            .distinctUntilChanged()
            .collect { selectTab(TrackerTab.entries[it]) }
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    },
                    expandedHeight = 56.dp,
                    actions = {
                        IconButton(enabled = canOpen, onClick = {
                            if (completed) onConsumeOperation()
                            deleting = null
                            form = FormTarget(null, currentDate())
                        }) {
                            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.add_reading))
                        }
                    },
                )
                val selectedPage = if (navigationReady) pager.targetPage else state.tab.ordinal
                PrimaryTabRow(
                    selectedTabIndex = selectedPage,
                    containerColor = MaterialTheme.colorScheme.background,
                    indicator = {
                        TabRowDefaults.PrimaryIndicator(
                            width = Dp.Unspecified,
                            modifier = Modifier.tabIndicatorLayout { measurable, constraints, positions ->
                                val tab = positions[pager.currentPage]
                                val width = 40.dp.roundToPx().coerceAtMost(constraints.maxWidth)
                                val indicator = measurable.measure(
                                    constraints.copy(minWidth = width, maxWidth = width),
                                )
                                // Follow the finger directly; the default offset adds another animation.
                                val offset = (tab.left.toPx() +
                                    tab.width.toPx() * pager.currentPageOffsetFraction +
                                    (tab.width.toPx() - width) / 2).roundToInt()
                                layout(constraints.maxWidth, indicator.height) {
                                    indicator.placeRelative(offset, 0)
                                }
                            },
                        )
                    },
                ) {
                    TrackerTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedPage == tab.ordinal,
                            enabled = navigationReady,
                            onClick = {
                                tabAnimation?.cancel()
                                tabAnimation = scope.launch { pager.animateScrollToPage(tab.ordinal) }
                            },
                            text = {
                                Text(stringResource(if (tab == TrackerTab.DASHBOARD) R.string.dashboard_tab else R.string.history_tab))
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().padding(padding),
            key = { TrackerTab.entries[it] },
            userScrollEnabled = navigationReady,
        ) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    state = if (page == TrackerTab.DASHBOARD.ordinal) dashboardScroll else historyScroll,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (state.loadState != ReadingLoadState.READY) {
                        item(key = "local-notice") { LocalStorageNotice() }
                        item(key = "load-state") {
                            Column(
                                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (state.loadState == ReadingLoadState.LOADING) {
                                    CircularProgressIndicator()
                                    Text(stringResource(R.string.loading_readings))
                                } else {
                                    Text(stringResource(R.string.load_storage_error))
                                    Button(onClick = onRetryLoad) { Text(stringResource(R.string.retry)) }
                                }
                            }
                        }
                    } else if (page == TrackerTab.DASHBOARD.ordinal) {
                        dashboardItems(state.period, state.dashboard, locale, onSelectPeriod, onNavigate)
                    } else {
                        item(key = "local-notice") { LocalStorageNotice() }
                        historyItems(
                            state.readings,
                            locale,
                            onEdit = {
                                if (canOpen) {
                                    if (completed) onConsumeOperation()
                                    deleting = null
                                    form = FormTarget(it, it.date)
                                }
                            },
                            onDelete = {
                                if (canOpen) {
                                    if (completed) onConsumeOperation()
                                    form = null
                                    deleting = it
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    if (sameSession && !completed) form?.let { target ->
        ReadingForm(
            reading = target.reading,
            readings = state.readings,
            initialDate = target.initialDate,
            today = currentDate(),
            locale = locale,
            loadState = state.loadState,
            operation = state.operation,
            canSubmit = canSubmit,
            onRetryLoad = onRetryLoad,
            onConsumeOperation = onConsumeOperation,
            onSave = { date, value ->
                if (state.operation != ReadingOperation.Idle) onConsumeOperation()
                onSave(target.reading?.id, date, value, locale)
            },
            onDismiss = {
                if (state.operation != ReadingOperation.Idle) onConsumeOperation()
                form = null
            },
        )
    }
    if (sameSession && !completed) deleting?.let { reading ->
        val dismissDelete = {
            if (state.operation != ReadingOperation.Idle) onConsumeOperation()
            deleting = null
        }
        AlertDialog(
            onDismissRequest = { if (!pending) dismissDelete() },
            title = { Text(stringResource(R.string.delete_reading)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(
                        R.string.delete_confirmation,
                        stringResource(R.string.volume, numberText(reading.cubicMetres, locale, raw = true)),
                        dateText(reading.date, locale),
                    ))
                    if (state.operation == ReadingOperation.Failed || state.operation == ReadingOperation.Missing) {
                        Text(
                            stringResource(if (state.operation == ReadingOperation.Missing) R.string.reading_missing else R.string.delete_storage_error),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    if (state.loadState == ReadingLoadState.ERROR) {
                        Text(
                            stringResource(R.string.load_storage_error),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        TextButton(onClick = onRetryLoad) { Text(stringResource(R.string.retry)) }
                    } else if (state.loadState == ReadingLoadState.LOADING) {
                        Text(stringResource(R.string.loading_readings))
                    }
                    if (pending) Text(stringResource(R.string.deleting_reading))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canSubmit,
                    onClick = {
                        if (state.operation != ReadingOperation.Idle) onConsumeOperation()
                        onDelete(reading.id)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(enabled = !pending, onClick = dismissDelete) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
