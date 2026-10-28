package com.trackgas.app

import android.os.Bundle
import android.graphics.Color
import android.app.ActivityManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
    private val tracker by lazy {
        val app = application as TrackGasApplication
        val activeTasks = getSystemService(ActivityManager::class.java).appTasks.map { it.taskInfo.id }.toSet()
        val store = app.trackerStoreOwner.obtain(trackerId, taskId, activeTasks)
        ViewModelProvider(store, TrackerViewModel.factory(app.readingRepository))[TrackerViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        trackerId = savedInstanceState?.getString(TRACKER_ID) ?: UUID.randomUUID().toString()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            TrackGasApp(tracker)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(TRACKER_ID, trackerId)
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
private fun TrackGasApp(tracker: TrackerViewModel) {
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
        )
    }
}
