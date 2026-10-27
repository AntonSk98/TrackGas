package com.trackgas.app

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.trackgas.app.tracking.TrackerScreen
import com.trackgas.app.tracking.TrackerViewModel
import com.trackgas.app.ui.theme.TrackGasTheme

/**
 * Android entry point that configures edge-to-edge system bars and hosts the Compose UI.
 *
 * [TrackerViewModel] is scoped to this activity's ViewModel store, so readings survive
 * configuration changes without being serialized into saved state or written to disk.
 */
class MainActivity : ComponentActivity() {
    private val tracker by lazy { ViewModelProvider(this)[TrackerViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            TrackGasApp(tracker)
        }
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
            onSave = tracker::save,
            onDelete = tracker::delete,
        )
    }
}
