package com.trackgas.app.tracking

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Owns window insets once; content sees the capped surface's finite safe/IME bounds. */
@Composable
internal fun ResponsiveDialog(
    onDismissRequest: () -> Unit,
    dismissEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (dismissEnabled) onDismissRequest() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = dismissEnabled,
            dismissOnClickOutside = dismissEnabled,
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().pointerInput(onDismissRequest, dismissEnabled) {
                detectTapGestures { if (dismissEnabled) onDismissRequest() }
            })
            Box(
                Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = modifier.widthIn(max = 560.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 6.dp,
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth(), content = content)
                }
            }
        }
    }
}

/** Title, body and actions share one viewport, even if an action alone is taller than it. */
@Composable
internal fun ResponsiveDialogContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ResponsiveAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    dismissEnabled: Boolean = true,
) {
    ResponsiveDialog(onDismissRequest, dismissEnabled, modifier) {
        ResponsiveDialogContent {
            title?.let { ProvideTextStyle(MaterialTheme.typography.headlineSmall, it) }
            text?.let { ProvideTextStyle(MaterialTheme.typography.bodyMedium, it) }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}
