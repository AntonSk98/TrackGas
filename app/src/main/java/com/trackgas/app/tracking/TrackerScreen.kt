package com.trackgas.app.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
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
    onOpenReplacement: () -> Unit = {},
    onReplaceMeter: () -> Unit = {},
    onOpenPurge: () -> Unit,
    onPurge: () -> Unit,
    onExport: () -> Unit = {},
    onAcknowledgeExport: (String) -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val ready = state.loadState == ReadingLoadState.READY
    val mutationsEnabled = ready && !state.dialog.pending
    var optionsExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(mutationsEnabled) { if (!mutationsEnabled) optionsExpanded = false }
    TrackerPager(
        tab = state.tab,
        onSelectTab = onSelectTab,
        toolbar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge) },
                expandedHeight = 56.dp,
                actions = {
                    Box {
                        IconButton(
                            modifier = Modifier.size(48.dp),
                            enabled = mutationsEnabled,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (optionsExpanded) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                contentColor = if (optionsExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            onClick = { optionsExpanded = !optionsExpanded },
                        ) {
                            Icon(painterResource(R.drawable.ic_options), stringResource(R.string.tracker_options))
                        }
                        DropdownMenu(
                            expanded = optionsExpanded && mutationsEnabled,
                            onDismissRequest = { optionsExpanded = false },
                            modifier = Modifier.widthIn(min = 216.dp, max = 280.dp),
                            offset = DpOffset(0.dp, 4.dp),
                            shape = MaterialTheme.shapes.medium,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 0.dp,
                            shadowElevation = 4.dp,
                        ) {
                            DropdownMenuItem(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                text = { Text(stringResource(R.string.export_csv), style = MaterialTheme.typography.bodyLarge) },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.ic_export),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                enabled = state.export is ExportState.Idle,
                                onClick = { optionsExpanded = false; onExport() },
                            )
                            DropdownMenuItem(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                text = {
                                    Text(
                                        stringResource(R.string.replace_meter),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.ic_replace_meter),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = { optionsExpanded = false; onOpenReplacement() },
                            )
                            DropdownMenuItem(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                text = { Text(stringResource(R.string.purge_tracking_data), style = MaterialTheme.typography.bodyLarge) },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.ic_purge),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = { optionsExpanded = false; onOpenPurge() },
                            )
                        }
                    }
                    IconButton(modifier = Modifier.size(48.dp), enabled = mutationsEnabled, onClick = onOpenAdd) {
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
                historyItems(state.readings, locale, onEdit = onOpenEdit, onDelete = onOpenDelete, tracker = state.tracker)
            }
        }
    }
    val export = state.export
    if (export is ExportState.Complete && export.succeeded) {
        Dialog(onDismissRequest = { onAcknowledgeExport(export.requestId) }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Box(modifier = Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_success),
                        contentDescription = stringResource(R.string.export_success),
                        modifier = Modifier.size(48.dp).testTag("export-success-icon"),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    } else if (export is ExportState.Complete) {
        AlertDialog(
            onDismissRequest = { onAcknowledgeExport(export.requestId) },
            text = { Text(stringResource(R.string.export_failure)) },
            confirmButton = {
                TextButton(onClick = { onAcknowledgeExport(export.requestId) }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
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
                tracker = state.tracker,
            )
        }
        is ReadingDialog.ConfirmingDeletion -> DeleteReadingDialog(
            dialog, locale, state.loadState, onRetryLoad, onDelete, onDismissDialog,
        )
        is ReadingDialog.Replacing -> key(state.sessionId, dialog.dialogId) {
            ReplacementForm(
                dialog = dialog,
                locale = locale,
                loadState = state.loadState,
                onRetryLoad = onRetryLoad,
                onConfirm = onReplaceMeter,
                onAddFinal = onOpenAdd,
                onDismiss = onDismissDialog,
            )
        }
        is ReadingDialog.ConfirmingPurge -> key(state.sessionId, dialog.dialogId) {
            PurgeTrackingDataDialog(dialog, state.loadState, onRetryLoad, onPurge, onDismissDialog)
        }
    }
}
