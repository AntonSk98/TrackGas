package com.trackgas.app

import android.os.Bundle
import android.graphics.Color
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
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
class MainActivity : ComponentActivity() {
    private lateinit var trackerId: String
    private val pendingPickers = mutableSetOf<String>()
    private val pickerLaunchers = mutableMapOf<String, ActivityResultLauncher<String>>()
    private val tracker by lazy {
        val app = application as TrackGasApplication
        val activeTasks = getSystemService(ActivityManager::class.java).appTasks.map { it.taskInfo.id }.toSet()
        val store = app.trackerStoreOwner.obtain(trackerId, taskId, activeTasks)
        ViewModelProvider(store, TrackerViewModel.factory(app.readingRepository))[TrackerViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        trackerId = savedInstanceState?.getString(TRACKER_ID) ?: UUID.randomUUID().toString()
        savedInstanceState?.getStringArrayList(PENDING_PICKERS)?.toList()?.forEach { requestId ->
            pendingPickers.add(requestId)
            registerExportPicker(requestId)
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            TrackGasApp(tracker, ::startExport)
        }
    }

    internal fun startExport() {
        val requestId = tracker.requestExport() ?: return
        pendingPickers.add(requestId)
        val picker = registerExportPicker(requestId)
        try {
            picker.launch("trackgas-readings.csv")
        } catch (_: ActivityNotFoundException) {
            pendingPickers.remove(requestId)
            pickerLaunchers.remove(requestId)?.unregister()
            tracker.beginExportWrite(requestId)
            tracker.finishExportWrite(requestId, false)
        } catch (_: SecurityException) {
            pendingPickers.remove(requestId)
            pickerLaunchers.remove(requestId)?.unregister()
            tracker.beginExportWrite(requestId)
            tracker.finishExportWrite(requestId, false)
        }
    }

    private fun registerExportPicker(requestId: String): ActivityResultLauncher<String> {
        val launcher = activityResultRegistry.register(
            "csv_export_$requestId",
            ActivityResultContracts.CreateDocument("text/csv"),
        ) { uri ->
            pendingPickers.remove(requestId)
            pickerLaunchers.remove(requestId)?.unregister()
            if (uri == null) tracker.cancelExport(requestId)
            else {
                val resolver = applicationContext.contentResolver
                tracker.writeExport(requestId) { resolver.openOutputStream(uri, "w") }
            }
        }
        if (requestId in pendingPickers) pickerLaunchers[requestId] = launcher
        else launcher.unregister()
        return launcher
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(TRACKER_ID, trackerId)
        outState.putStringArrayList(PENDING_PICKERS, ArrayList(pendingPickers))
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        (application as TrackGasApplication).trackerStoreOwner.onDestroyed(trackerId, isFinishing, isChangingConfigurations)
        super.onDestroy()
    }

    private companion object {
        const val TRACKER_ID = "tracker_id"
        const val PENDING_PICKERS = "pending_csv_pickers"
    }
}

/** Applies the app theme and binds the activity-owned state holder to the state/callback UI. */
@Composable
private fun TrackGasApp(tracker: TrackerViewModel, onExport: () -> Unit) {
    TrackGasTheme {
        TrackerScreen(
            state = tracker.state,
            currentDate = tracker::today,
            onSelectTab = tracker::selectTab,
            onSelectPeriod = tracker::selectPeriod,
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
            onExport = onExport,
            onAcknowledgeExport = tracker::acknowledgeExport,
        )
    }
}
