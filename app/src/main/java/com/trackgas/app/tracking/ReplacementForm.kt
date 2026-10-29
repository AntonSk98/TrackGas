package com.trackgas.app.tracking

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.trackgas.app.R
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReplacementForm(
    dialog: ReadingDialog.Replacing,
    locale: Locale,
    loadState: ReadingLoadState,
    onRetryLoad: () -> Unit,
    onConfirm: () -> Unit,
    onAddFinal: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pending = dialog.pending
    val unavailable = (dialog.phase as? ReplacementPhase.Unavailable)?.error
    Dialog(
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = { if (!pending) onDismiss() },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().pointerInput(onDismiss, pending) {
                detectTapGestures { if (!pending) onDismiss() }
            })
            Surface(
                modifier = Modifier.safeDrawingPadding().imePadding().padding(16.dp)
                    .widthIn(max = 560.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.replace_meter), style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(
                        value = dateText(dialog.target.date, locale), onValueChange = {},
                        modifier = Modifier.fillMaxWidth(), readOnly = true, enabled = !pending,
                        label = { Text(stringResource(R.string.date)) }, singleLine = true,
                    )
                    dialog.target.finalReading?.let {
                        Text(stringResource(
                            R.string.saved_final_value,
                            stringResource(R.string.volume, numberText(it.cubicMetres, locale, raw = true)),
                        ))
                    }
                    if (unavailable != null) {
                        Text(
                            stringResource(replacementErrorResource(unavailable)),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    } else {
                        Text(stringResource(R.string.replacement_explanation))
                    }
                    if (dialog.phase is ReplacementPhase.Failed) {
                        Text(
                            stringResource(R.string.replacement_storage_error),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    ReadingLoadContent(loadState, onRetryLoad, inline = true)
                    if (pending) Text(stringResource(R.string.replacing_meter))
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss, enabled = !pending) { Text(stringResource(R.string.cancel)) }
                        if (unavailable == ReplacementError.MISSING_FINAL) {
                            Button(onClick = onAddFinal, enabled = !pending && loadState == ReadingLoadState.READY) {
                                Text(stringResource(R.string.add_reading))
                            }
                        } else if (unavailable == null) {
                            Button(
                                onClick = onConfirm,
                                enabled = !pending && loadState == ReadingLoadState.READY,
                            ) { Text(stringResource(R.string.confirm_replacement)) }
                        }
                    }
                }
            }
        }
    }
}

private fun replacementErrorResource(error: ReplacementError): Int = when (error) {
    ReplacementError.MISSING_FINAL -> R.string.replacement_missing_final
    ReplacementError.ALREADY_REPLACED -> R.string.replacement_already_replaced
    ReplacementError.OBSOLETE_TARGET -> R.string.replacement_obsolete
    ReplacementError.STALE_DATE -> R.string.replacement_stale_date
    ReplacementError.NOT_LATEST -> R.string.replacement_not_latest
}
