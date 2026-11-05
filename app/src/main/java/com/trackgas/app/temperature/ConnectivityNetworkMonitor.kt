package com.trackgas.app.temperature

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/** Monitors Android's default network and signals when it has validated internet access. */
class ConnectivityNetworkMonitor(context: Context) : NetworkMonitor {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasInternetAccess()
    }

    override fun addOnAvailableListener(listener: () -> Unit): AutoCloseable {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (isOnline()) listener()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (capabilities.hasInternetAccess()) listener()
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        return AutoCloseable { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private fun NetworkCapabilities.hasInternetAccess(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
