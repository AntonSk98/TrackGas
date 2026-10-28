package com.trackgas.app.tracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.trackgas.app.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReadingFormLayout(
    editing: Boolean,
    displayedDate: String,
    value: String,
    errors: ReadingErrors,
    pending: Boolean,
    failed: Boolean,
    loadState: ReadingLoadState,
    onValueChange: (String) -> Unit,
    onChooseDate: () -> Unit,
    onRetryLoad: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    Dialog(
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = { if (!pending) onDismiss() },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().pointerInput(onDismiss, pending) {
                detectTapGestures { if (!pending) onDismiss() }
            })
            Surface(
                modifier = Modifier.safeDrawingPadding().imePadding()
                    .padding(if (compactHeight) 8.dp else 16.dp)
                    .widthIn(max = 560.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column(modifier = if (compactHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier) {
                    Column(
                        modifier = (if (compactHeight) Modifier else Modifier.weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()))
                            .padding(horizontal = 24.dp, vertical = if (compactHeight) 8.dp else 24.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compactHeight) 8.dp else 12.dp),
                    ) {
                        Text(
                            stringResource(if (editing) R.string.edit_reading else R.string.add_reading),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Box {
                            OutlinedTextField(
                                value = displayedDate,
                                onValueChange = {},
                                modifier = Modifier.fillMaxWidth().then(if (!editing) Modifier.clearAndSetSemantics {} else Modifier),
                                label = { Text(stringResource(R.string.date)) },
                                enabled = !pending,
                                readOnly = true,
                                singleLine = true,
                            )
                            if (!editing) {
                                val label = stringResource(R.string.choose_date, displayedDate)
                                Box(Modifier.matchParentSize()
                                    .clickable(enabled = !pending, role = Role.Button, onClick = onChooseDate)
                                    .semantics { contentDescription = label })
                            }
                        }
                        errors.date?.let { DateError(it) }
                        OutlinedTextField(
                            value = value,
                            onValueChange = onValueChange,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !pending,
                            label = { Text(stringResource(R.string.value_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            isError = errors.value != null,
                            supportingText = errors.value?.let { error -> { Text(stringResource(errorResource(error))) } },
                        )
                        if (failed) {
                            Text(
                                stringResource(R.string.save_storage_error),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        ReadingLoadContent(loadState, onRetryLoad, inline = true)
                        if (pending) Text(stringResource(R.string.saving_reading))
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compactHeight) 4.dp else 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss, enabled = !pending) { Text(stringResource(R.string.cancel)) }
                        Button(enabled = !pending && loadState == ReadingLoadState.READY, onClick = onSave) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }
}
