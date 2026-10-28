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
            val owner = application.trackerStoreOwner
            try {
                val first = ViewModelProvider(owner.obtain(firstId, 100, setOf(100, 200)), factory)[TrackerViewModel::class.java]
                val recreated = ViewModelProvider(owner.obtain(firstId, 100, setOf(100, 200)), factory)[TrackerViewModel::class.java]
                val other = ViewModelProvider(owner.obtain(secondId, 200, setOf(100, 200)), factory)[TrackerViewModel::class.java]
                assertSame(first, recreated)
                assertNotSame(first, other)
                owner.prune(setOf(100))
                val retained = ViewModelProvider(owner.obtain(firstId, 100, setOf(100, 200)), factory)[TrackerViewModel::class.java]
                val replacement = ViewModelProvider(owner.obtain(secondId, 200, setOf(100, 200)), factory)[TrackerViewModel::class.java]
                assertSame(first, retained)
                assertNotSame(other, replacement)
            } finally {
                owner.release(firstId)
                owner.release(secondId)
            }
        }
    }
}
