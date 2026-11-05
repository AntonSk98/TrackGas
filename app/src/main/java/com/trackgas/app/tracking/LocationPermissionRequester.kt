package com.trackgas.app.tracking

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
internal fun LocationPermissionRequester(
    onPermissionResult: (Boolean) -> Unit,
    content: @Composable (requestLocation: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val currentPermissionResult by rememberUpdatedState(onPermissionResult)
    var rationaleVisible by rememberSaveable { mutableStateOf(false) }
    val store = remember(context) { LocationPermissionStore(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        store.write(
            if (granted) LocationPermissionState.PREVIOUSLY_GRANTED
            else LocationPermissionState.DECLINED,
        )
        currentPermissionResult(granted)
    }
    val requestLocation = remember(context, permissionLauncher, store) {
        {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) store.write(LocationPermissionState.PREVIOUSLY_GRANTED)
            if (shouldRequestLocation(granted, store.read())) rationaleVisible = true
            else currentPermissionResult(granted)
        }
    }

    content(requestLocation)
    if (rationaleVisible) {
        LocationPermissionDialog(
            onContinue = {
                rationaleVisible = false
                permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            onNotNow = {
                rationaleVisible = false
                store.write(LocationPermissionState.DECLINED)
                currentPermissionResult(false)
            },
        )
    }
}
