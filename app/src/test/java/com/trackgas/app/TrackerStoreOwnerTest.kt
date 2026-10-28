package com.trackgas.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.*
import org.junit.Test

class TrackerStoreOwnerTest {
    class Probe : ViewModel() {
        var clears = 0
        override fun onCleared() { clears++ }
    }

    private fun probe(store: ViewModelStore) = ViewModelProvider(store, object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(Probe())!!
    })[Probe::class.java]

    @Test fun activeTaskAndSameIdentityRetainAcrossEveryNonFinishingDestruction() {
        val owner = TrackerStoreOwner()
        val store = owner.obtain("first", 10, emptySet())
        val first = probe(store)
        for ((finishing, configuring) in listOf(false to false, false to true, true to true)) {
            owner.onDestroyed("first", finishing, configuring)
            assertSame(store, owner.obtain("first", 10, emptySet()))
            assertSame(first, probe(store))
            assertEquals(0, first.clears)
        }
        owner.onDestroyed("first", true, false)
        assertEquals(1, first.clears)
        owner.release("first"); owner.prune(emptySet())
        assertEquals(1, first.clears)
        assertNotSame(store, owner.obtain("first", 10, emptySet()))
    }

    @Test fun independentIdentitiesEvenInSameTaskAndPruningClearOnlyAbandonedStores() {
        val owner = TrackerStoreOwner()
        val first = probe(owner.obtain("first", 10, setOf(10, 20)))
        val sameTask = probe(owner.obtain("second", 10, setOf(10, 20)))
        val otherTask = probe(owner.obtain("third", 20, setOf(10, 20)))
        assertNotSame(first, sameTask)
        owner.release("first")
        assertEquals(1, first.clears)
        assertEquals(0, sameTask.clears)
        owner.prune(setOf(10))
        assertEquals(1, otherTask.clears)
        assertEquals(0, sameTask.clears)
        owner.prune(setOf(10))
        assertEquals(1, otherTask.clears)
        // obtain includes its own task, and updates the task associated with a retained identity.
        val retained = owner.obtain("second", 30, setOf(10))
        owner.prune(setOf(30))
        assertSame(sameTask, probe(retained))
        assertEquals(0, sameTask.clears)
        val freshProcess = TrackerStoreOwner()
        assertNotSame(sameTask, probe(freshProcess.obtain("second", 30, emptySet())))
        owner.release("second")
        assertEquals(1, sameTask.clears)
    }
}
