package com.trackgas.app.tracking

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trackgas.app.R

@Composable
internal fun LocationPermissionDialog(
    onContinue: () -> Unit,
    onNotNow: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text(stringResource(R.string.location_permission_title)) },
        text = { Text(stringResource(R.string.location_permission_body)) },
        confirmButton = {
            TextButton(
                modifier = Modifier.heightIn(min = 48.dp),
                onClick = onContinue,
            ) {
                Text(stringResource(R.string.location_permission_continue))
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.heightIn(min = 48.dp),
                onClick = onNotNow,
            ) {
                Text(stringResource(R.string.location_permission_not_now))
            }
        },
    )
}
