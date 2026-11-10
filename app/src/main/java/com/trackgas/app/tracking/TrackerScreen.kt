package com.trackgas.app.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.Color
import com.trackgas.app.AppLanguages
import com.trackgas.app.R
import com.trackgas.app.temperature.MeterLocation
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
    onSelectChartMode: (ConsumptionChartMode) -> Unit,
    onNavigate: (Long) -> Unit,
    onRetryLoad: () -> Unit,
    onOpenAdd: () -> Unit,
    onOpenEdit: (Reading) -> Unit,
    onOpenDelete: (Reading) -> Unit,
    onDismissDialog: () -> Unit,
    onClearDialogError: () -> Unit,
    onSave: (LocalDate?, String, Locale, String?, String, java.time.ZoneId) -> Unit,
    onDelete: () -> Unit,
    onOpenReplacement: () -> Unit = {},
    onReplaceMeter: () -> Unit = {},
    onOpenPurge: () -> Unit,
    onPurge: () -> Unit,
    onOpenMeterLocation: () -> Unit = {},
    onUpdateMeterLocationQuery: (String) -> Unit = {},
    onSelectMeterLocation: (MeterLocation) -> Unit = {},
    onSaveMeterLocation: () -> Unit = {},
    onDismissMeterLocationDialog: () -> Unit = {},
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    onConfirmCsvImport: () -> Unit = {},
    onCancelCsvImport: (String) -> Unit = {},
    onAcknowledgeCsvImport: (String) -> Unit = {},
    onAcknowledgeExport: (String) -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val ready = state.loadState == ReadingLoadState.READY
    val dailyRates = remember(state.tracker, state.period) {
        dailyConsumptionRates(state.tracker, state.period)
    }
    val mutationsEnabled = ready && !state.dialog.pending && state.csvImport is CsvImportState.Idle
    var optionsExpanded by remember { mutableStateOf(false) }
    var languageDialogOpen by remember { mutableStateOf(false) }
    LaunchedEffect(mutationsEnabled) { if (!mutationsEnabled) optionsExpanded = false }
    TrackerPager(
        tab = state.tab,
        onSelectTab = onSelectTab,
        toolbar = {
            TrackerToolbar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_brand_mark),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified,
                        )
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    }
                },
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
                                text = { Text(stringResource(R.string.import_csv), style = MaterialTheme.typography.bodyLarge) },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.ic_import),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                enabled = state.export is ExportState.Idle,
                                onClick = { optionsExpanded = false; onImport() },
                            )
                            DropdownMenuItem(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                text = { Text(stringResource(R.string.meter_location), style = MaterialTheme.typography.bodyLarge) },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.ic_location),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = { optionsExpanded = false; onOpenMeterLocation() },
                            )
                            DropdownMenuItem(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                text = { Text(stringResource(R.string.language), style = MaterialTheme.typography.bodyLarge) },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.ic_language),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = { optionsExpanded = false; languageDialogOpen = true },
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
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
                item(key = "load-state") { ReadingLoadContent(state.loadState, onRetryLoad) }
            } else if (tab == TrackerTab.DASHBOARD) {
                dashboardItems(
                    state.period, state.dashboard, state.temperatures, locale,
                    state.chartMode, dailyRates, onSelectChartMode, onSelectPeriod, onNavigate,
                )
            } else {
                historyItems(state.readings, locale, onEdit = onOpenEdit, onDelete = onOpenDelete, tracker = state.tracker)
            }
        }
    }
    if (languageDialogOpen) {
        LanguageDialog(
            onDismiss = { languageDialogOpen = false },
            onSelect = { languageDialogOpen = false; AppLanguages.select(it) },
        )
    }
    val export = state.export
    if (export is ExportState.Complete && export.succeeded) {
        ResponsiveDialog(onDismissRequest = { onAcknowledgeExport(export.requestId) }) {
            ResponsiveDialogContent {
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
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
        ResponsiveAlertDialog(
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
                onSave = { date, value, note, time, zone -> onSave(date, value, locale, note, time, zone) },
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
                onDismiss = onDismissDialog,
            )
        }
        is ReadingDialog.ConfirmingPurge -> key(state.sessionId, dialog.dialogId) {
            PurgeTrackingDataDialog(dialog, state.loadState, onRetryLoad, onPurge, onDismissDialog)
        }
    }
    CsvImportDialog(
        state = state.csvImport,
        loadState = state.loadState,
        onRetryLoad = onRetryLoad,
        onConfirm = onConfirmCsvImport,
        onCancel = onCancelCsvImport,
        onAcknowledge = onAcknowledgeCsvImport,
    )
    (state.locationDialog as? MeterLocationDialog.Editing)?.let { dialog ->
        MeterLocationDialog(
            state = dialog,
            onQueryChange = onUpdateMeterLocationQuery,
            onSelect = onSelectMeterLocation,
            onSave = onSaveMeterLocation,
            onDismiss = onDismissMeterLocationDialog,
        )
    }
}
