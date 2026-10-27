package com.trackgas.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trackgas.app.tracking.TrackerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TrackGasApplicationTest {
    @Test
    fun applicationUsesOneLocalRepositoryWithoutBackupOrNetworkPermission() {
        val application = ApplicationProvider.getApplicationContext<TrackGasApplication>()
        assertSame(application.readingRepository, application.readingRepository)
        assertEquals(0, application.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        val info = application.packageManager.getPackageInfo(
            application.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        assertFalse(info.requestedPermissions.orEmpty().contains("android.permission.INTERNET"))
    }

    @Test
    fun activityStoresRetainTheirOwnTrackers() {
        val application = ApplicationProvider.getApplicationContext<TrackGasApplication>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val firstId = UUID.randomUUID().toString()
            val secondId = UUID.randomUUID().toString()
            val factory = TrackerViewModel.factory(application.readingRepository)
            try {
                val first = ViewModelProvider(application.trackerStore(firstId, 100), factory)[TrackerViewModel::class.java]
                val recreated = ViewModelProvider(application.trackerStore(firstId, 100), factory)[TrackerViewModel::class.java]
                val other = ViewModelProvider(application.trackerStore(secondId, 200), factory)[TrackerViewModel::class.java]
                assertSame(first, recreated)
                assertNotSame(first, other)
                application.pruneTrackerStores(setOf(100))
                val retained = ViewModelProvider(application.trackerStore(firstId, 100), factory)[TrackerViewModel::class.java]
                val replacement = ViewModelProvider(application.trackerStore(secondId, 200), factory)[TrackerViewModel::class.java]
                assertSame(first, retained)
                assertNotSame(other, replacement)
            } finally {
                application.releaseTrackerStore(firstId)
                application.releaseTrackerStore(secondId)
            }
        }
    }
}
