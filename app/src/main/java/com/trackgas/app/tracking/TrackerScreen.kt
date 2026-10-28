package com.trackgas.app.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trackgas.app.R
import java.time.LocalDate
import java.util.Locale

/** State/callback route. Semantic dialogs come exclusively from the session state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(
    state: TrackerState,
    currentDate: () -> LocalDate,
    onSelectTab: (TrackerTab) -> Unit,
    onSelectPeriod: (PeriodType) -> Unit,
    onNavigate: (Long) -> Unit,
    onRetryLoad: () -> Unit,
    onOpenAdd: () -> Unit,
    onOpenEdit: (Reading) -> Unit,
    onOpenDelete: (Reading) -> Unit,
    onDismissDialog: () -> Unit,
    onClearDialogError: () -> Unit,
    onSave: (LocalDate?, String, Locale) -> Unit,
    onDelete: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val ready = state.loadState == ReadingLoadState.READY
    TrackerPager(
        tab = state.tab,
        onSelectTab = onSelectTab,
        toolbar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge) },
                expandedHeight = 56.dp,
                actions = {
                    IconButton(enabled = ready && !state.dialog.pending, onClick = onOpenAdd) {
                        Icon(painterResource(R.drawable.ic_add), stringResource(R.string.add_reading))
                    }
                },
            )
        },
    ) { tab, scroll ->
        LazyColumn(
            modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
            state = scroll,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!ready) {
                item(key = "local-notice") { LocalStorageNotice() }
                item(key = "load-state") { ReadingLoadContent(state.loadState, onRetryLoad) }
            } else if (tab == TrackerTab.DASHBOARD) {
                dashboardItems(state.period, state.dashboard, locale, onSelectPeriod, onNavigate)
            } else {
                item(key = "local-notice") { LocalStorageNotice() }
                historyItems(state.readings, locale, onEdit = onOpenEdit, onDelete = onOpenDelete)
            }
        }
    }
    when (val dialog = state.dialog) {
        ReadingDialog.Closed -> Unit
        is ReadingDialog.Editing -> key(state.sessionId, dialog.dialogId) {
            ReadingForm(
                sessionId = state.sessionId,
                dialog = dialog,
                readings = state.readings,
                today = currentDate(),
                locale = locale,
                loadState = state.loadState,
                onRetryLoad = onRetryLoad,
                onClearError = onClearDialogError,
                onSave = { date, value -> onSave(date, value, locale) },
                onDismiss = onDismissDialog,
            )
        }
        is ReadingDialog.ConfirmingDeletion -> DeleteReadingDialog(
            dialog, locale, state.loadState, onRetryLoad, onDelete, onDismissDialog,
        )
    }
}
