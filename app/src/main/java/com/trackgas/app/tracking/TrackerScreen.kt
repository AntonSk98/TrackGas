package com.trackgas.app.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trackgas.app.R
import java.time.LocalDate
import java.util.Locale

/** Captures the Add/Edit target and its opening date independently of subsequent recompositions. */
private data class FormTarget(val reading: Reading?, val initialDate: LocalDate)

/**
 * Compose entry point for Dashboard/History navigation and reading-management dialogs.
 *
 * [state] and event callbacks are supplied by the host; this composable has no dependency
 * on a ViewModel. Local state controls drafts, confirmation dialogs, and each tab's scroll
 * position. [currentDate] is called when Add opens and when rendering date limits, while
 * [onSave] returns inline errors without exposing state mutation to the form.
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
    onSave: (String?, LocalDate?, String, Locale) -> ReadingErrors,
    onDelete: (String) -> Boolean,
) {
    val locale = LocalConfiguration.current.locales[0]
    var form by remember { mutableStateOf<FormTarget?>(null) }
    var deleting by remember { mutableStateOf<Reading?>(null) }
    var deleteError by remember { mutableStateOf(false) }
    val dashboardScroll = rememberLazyListState()
    val historyScroll = rememberLazyListState()
    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = { form = FormTarget(null, currentDate()) }) {
                            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.add_reading))
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                    TrackerTab.entries.forEach { tab ->
                        Tab(
                            selected = state.tab == tab,
                            onClick = { onSelectTab(tab) },
                            text = {
                                Text(stringResource(if (tab == TrackerTab.DASHBOARD) R.string.dashboard_tab else R.string.history_tab))
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            state = if (state.tab == TrackerTab.DASHBOARD) dashboardScroll else historyScroll,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "temporary-notice") {
                Text(stringResource(R.string.temporary_notice), style = MaterialTheme.typography.bodyMedium)
            }
            if (state.tab == TrackerTab.DASHBOARD) {
                dashboardItems(state.period, state.dashboard, locale, onSelectPeriod, onNavigate)
            } else {
                historyItems(
                    state.readings,
                    locale,
                    onEdit = { form = FormTarget(it, it.date) },
                    onDelete = { deleting = it; deleteError = false },
                )
            }
        }
    }
    form?.let { target ->
        ReadingForm(
            reading = target.reading,
            initialDate = target.initialDate,
            today = currentDate(),
            locale = locale,
            onSave = { date, value -> onSave(target.reading?.id, date, value, locale) },
            onDismiss = { form = null },
        )
    }
    deleting?.let { reading ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_reading)) },
            text = {
                Text(
                    if (deleteError) stringResource(R.string.reading_missing)
                    else stringResource(
                        R.string.delete_confirmation,
                        stringResource(R.string.volume, numberText(reading.cubicMetres, locale, raw = true)),
                        dateText(reading.date, locale),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onDelete(reading.id)) deleting = null else deleteError = true
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
