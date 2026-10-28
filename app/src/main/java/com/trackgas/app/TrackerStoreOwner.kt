package com.trackgas.app

import androidx.lifecycle.ViewModelStore

/** Process-local retention beyond ordinary activity/configuration ownership. Main-thread only. */
class TrackerStoreOwner {
    private data class Entry(var taskId: Int, val store: ViewModelStore)
    private val stores = mutableMapOf<String, Entry>()

    fun obtain(id: String, taskId: Int, activeTaskIds: Set<Int>): ViewModelStore {
        prune(activeTaskIds + taskId)
        return stores.getOrPut(id) { Entry(taskId, ViewModelStore()) }.also { it.taskId = taskId }.store
    }

    fun release(id: String) {
        stores.remove(id)?.store?.clear()
    }

    fun prune(activeTaskIds: Set<Int>) {
        stores.filterValues { it.taskId !in activeTaskIds }.keys.toList().forEach(::release)
    }

    fun onDestroyed(id: String, isFinishing: Boolean, isChangingConfigurations: Boolean) {
        if (isFinishing && !isChangingConfigurations) release(id)
    }
}
