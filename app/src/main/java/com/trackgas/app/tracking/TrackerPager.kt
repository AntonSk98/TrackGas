package com.trackgas.app.tracking

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trackgas.app.R
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackerPager(
    tab: TrackerTab,
    onSelectTab: (TrackerTab) -> Unit,
    toolbar: @Composable () -> Unit,
    pageContent: @Composable (TrackerTab, LazyListState) -> Unit,
) {
    val dashboardScroll = rememberLazyListState()
    val historyScroll = rememberLazyListState()
    val pager = rememberPagerState(initialPage = tab.ordinal) { TrackerTab.entries.size }
    val scope = rememberCoroutineScope()
    var tabAnimation by remember { mutableStateOf<Job?>(null) }
    var navigationReady by remember { mutableStateOf(false) }
    val selectTab by rememberUpdatedState(onSelectTab)
    // The session, not restored pager state, defines fresh-process selection.
    LaunchedEffect(pager) {
        pager.scrollToPage(tab.ordinal)
        navigationReady = true
        snapshotFlow { pager.settledPage }.distinctUntilChanged()
            .collect { selectTab(TrackerTab.entries[it]) }
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                toolbar()
                val selectedPage = if (navigationReady) pager.targetPage else tab.ordinal
                PrimaryTabRow(
                    selectedTabIndex = selectedPage,
                    containerColor = MaterialTheme.colorScheme.background,
                    indicator = {
                        TabRowDefaults.PrimaryIndicator(
                            width = Dp.Unspecified,
                            modifier = Modifier.tabIndicatorLayout { measurable, constraints, positions ->
                                val position = positions[pager.currentPage]
                                val width = 40.dp.roundToPx().coerceAtMost(constraints.maxWidth)
                                val indicator = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                                // Follow the finger without a second offset animation.
                                val offset = (position.left.toPx() +
                                    position.width.toPx() * pager.currentPageOffsetFraction +
                                    (position.width.toPx() - width) / 2).roundToInt()
                                layout(constraints.maxWidth, indicator.height) { indicator.placeRelative(offset, 0) }
                            },
                        )
                    },
                ) {
                    TrackerTab.entries.forEach { destination ->
                        Tab(
                            selected = selectedPage == destination.ordinal,
                            enabled = navigationReady,
                            onClick = {
                                tabAnimation?.cancel()
                                tabAnimation = scope.launch { pager.animateScrollToPage(destination.ordinal) }
                            },
                            text = {
                                Text(stringResource(if (destination == TrackerTab.DASHBOARD) R.string.dashboard_tab else R.string.history_tab))
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
                pageContent(TrackerTab.entries[page], if (page == TrackerTab.DASHBOARD.ordinal) dashboardScroll else historyScroll)
            }
        }
    }
}
