package com.trackgas.app.temperature

/** Reports connectivity and notifies listeners when an internet-capable network appears. */
interface NetworkMonitor {
    fun isOnline(): Boolean

    fun addOnAvailableListener(listener: () -> Unit): AutoCloseable
}

/** No-connectivity implementation for callers that do not provide a platform monitor. */
object OfflineNetworkMonitor : NetworkMonitor {
    override fun isOnline() = false
    override fun addOnAvailableListener(listener: () -> Unit) = AutoCloseable {}
}
