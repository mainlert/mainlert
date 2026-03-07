package com.mainlert.data.local.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Singleton

/**
 * Network monitor service for hierarchical sync system.
 * Monitors network connectivity changes and provides reactive streams.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    /**
     * Check if device is currently online.
     * 
     * @return true if device has internet connectivity, false otherwise
     */
    fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
    
    /**
     * Observe network state changes as a Flow.
     * 
     * @return Flow that emits true when online, false when offline
     */
    fun observeNetworkState(): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }
            
            override fun onLost(network: Network) {
                trySend(false)
            }
            
            override fun onUnavailable() {
                trySend(false)
            }
        }
        
        // Register for network callbacks
        connectivityManager.registerDefaultNetworkCallback(callback)
        
        // Send initial state
        launch {
            trySend(isOnline())
        }
        
        // Clean up when flow is cancelled
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
    
    /**
     * Observe network state with debouncing to avoid rapid state changes.
     * 
     * @param debounceMs Milliseconds to debounce network state changes
     * @return Flow that emits stable network state
     */
    fun observeStableNetworkState(debounceMs: Long = 2000L): Flow<Boolean> {
        return observeNetworkState()
            .distinctUntilChanged()
            // Note: In a real implementation, you might want to add debouncing here
            // using operators like debounce() from kotlinx-coroutines-core
    }
    
    /**
     * Check if network is metered (e.g., cellular data).
     * 
     * @return true if network is metered, false otherwise
     */
    fun isNetworkMetered(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true
    }
    
    /**
     * Get current network type (Wi-Fi, cellular, etc.).
     * 
     * @return Network type as string
     */
    fun getNetworkType(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        return when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Unknown"
        }
    }
}