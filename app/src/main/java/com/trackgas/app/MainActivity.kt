package com.trackgas.app

import android.os.Bundle
import android.graphics.Color
import android.app.ActivityManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.trackgas.app.tracking.TrackerScreen
import com.trackgas.app.tracking.TrackerViewModel
import com.trackgas.app.ui.theme.TrackGasTheme
import java.util.UUID

/**
 * Android entry point that configures edge-to-edge system bars and hosts the Compose UI.
 *
 * The application retains each activity's tracker across activity destruction within the process.
 */
class MainActivity : AppCompatActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            (application as TrackGasApplication).reminderStore.setPermissionDeclined(!granted)
        }
    private lateinit var trackerId: String
    private lateinit var csvPickerCoordinator: CsvDocumentPickerCoordinator
    private val tracker by lazy {
        val app = application as TrackGasApplication
        val activeTasks = getSystemService(ActivityManager::class.java).appTasks.map { it.taskInfo.id }.toSet()
        val store = app.trackerStoreOwner.obtain(trackerId, taskId, activeTasks)
        ViewModelProvider(
            store,
            TrackerViewModel.factory(
                app.readingRepository,
                app.temperatureServices,
                app.reminderStore,
                app.reminderScheduler,
            ),
        )[TrackerViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        trackerId = savedInstanceState?.getString(TRACKER_ID) ?: UUID.randomUUID().toString()
        csvPickerCoordinator = CsvDocumentPickerCoordinator(
            activityResultRegistry,
            applicationContext.contentResolver,
            tracker,
        )
        csvPickerCoordinator.restore(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            TrackGasApp(tracker, ::startExport, ::startImport)
        }
    }

    internal fun startExport() = csvPickerCoordinator.startExport()

    internal fun startImport() = csvPickerCoordinator.startImport()

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val store = (application as TrackGasApplication).reminderStore
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            store.setPermissionDeclined(false)
        } else if (!store.permissionDeclined()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(TRACKER_ID, trackerId)
        csvPickerCoordinator.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        (application as TrackGasApplication).trackerStoreOwner.onDestroyed(trackerId, isFinishing, isChangingConfigurations)
        super.onDestroy()
    }

    private companion object {
        const val TRACKER_ID = "tracker_id"
    }
}

/** Applies the app theme and binds the activity-owned state holder to the state/callback UI. */
@Composable
private fun TrackGasApp(tracker: TrackerViewModel, onExport: () -> Unit, onImport: () -> Unit) {
    TrackGasTheme {
        TrackerScreen(
            state = tracker.state,
            currentDate = tracker::today,
            onSelectTab = tracker::selectTab,
            onSelectPeriod = tracker::selectPeriod,
            onSelectChartMode = tracker::selectChartMode,
            onNavigate = tracker::navigate,
            onRetryLoad = tracker::retryLoad,
            onOpenAdd = tracker::openAdd,
            onOpenEdit = tracker::openEdit,
            onOpenDelete = tracker::openDelete,
            onDismissDialog = tracker::dismissDialog,
            onClearDialogError = tracker::clearDialogError,
            onSave = tracker::save,
            onDelete = tracker::delete,
            onOpenReplacement = tracker::openReplacement,
            onReplaceMeter = tracker::replaceMeter,
            onOpenPurge = tracker::openPurge,
            onPurge = tracker::purge,
            onOpenMeterLocation = tracker::openMeterLocation,
            onUpdateMeterLocationQuery = tracker::updateMeterLocationQuery,
            onSelectMeterLocation = tracker::selectMeterLocationSuggestion,
            onSaveMeterLocation = tracker::saveMeterLocation,
            onDismissMeterLocationDialog = tracker::dismissMeterLocationDialog,
            onExport = onExport,
            onImport = onImport,
            onConfirmCsvImport = tracker::confirmCsvImport,
            onCancelCsvImport = tracker::cancelCsvImport,
            onAcknowledgeCsvImport = tracker::acknowledgeCsvImport,
            onAcknowledgeExport = tracker::acknowledgeExport,
        )
    }
}
