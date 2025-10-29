// File: app/src/main/java/com/example/smallbasket/location/ConnectivityStatusManager.kt
package com.example.smallbasket.location

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.provider.Settings
import android.util.Log
import com.example.smallbasket.api.RetrofitClient
import com.example.smallbasket.models.ConnectivityUpdateRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * CRITICAL: Manages connectivity status and syncs with backend
 * This is what makes users "reachable" in the backend
 */
class ConnectivityStatusManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ConnectivityManager"
        private const val UPDATE_INTERVAL_MS = 3 * 60 * 1000L // 3 minutes (more frequent)
        private const val OFFLINE_CHECK_INTERVAL_MS = 30 * 1000L // 30 seconds

        @Volatile
        private var instance: ConnectivityStatusManager? = null

        fun getInstance(context: Context): ConnectivityStatusManager {
            return instance ?: synchronized(this) {
                instance ?: ConnectivityStatusManager(context.applicationContext)
                    .also { instance = it }
            }
        }
    }

    private val api = RetrofitClient.apiService
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isMonitoring = false
    private var lastUpdateTime = 0L
    private var wasConnected = false

    /**
     * Network callback for real-time connectivity changes
     */
    private val connectivityCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "✅ Network AVAILABLE")
            scope.launch {
                delay(2000) // Let connection stabilize
                updateConnectivityStatus()

                // Broadcast to update UI
                val intent = Intent("com.example.smallbasket.CONNECTIVITY_CHANGED")
                intent.putExtra("connected", true)
                context.sendBroadcast(intent)
            }
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "❌ Network LOST")
            scope.launch {
                updateConnectivityStatus() // Update backend to offline

                val intent = Intent("com.example.smallbasket.CONNECTIVITY_CHANGED")
                intent.putExtra("connected", false)
                context.sendBroadcast(intent)
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            Log.d(TAG, "Network capabilities changed - Internet: $hasInternet, Validated: $isValidated")

            if (hasInternet && isValidated && !wasConnected) {
                // Connection quality improved
                scope.launch {
                    delay(1000)
                    updateConnectivityStatus()
                }
            }
        }
    }

    /**
     * CRITICAL: Start monitoring connectivity and update backend
     * This makes the user "reachable"
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Already monitoring")
            return
        }

        isMonitoring = true
        Log.i(TAG, "=== Starting Connectivity Monitoring ===")

        // Register network callback
        registerNetworkCallback()

        // Start periodic updates
        startPeriodicUpdates()

        // Do initial update immediately
        scope.launch {
            updateConnectivityStatus()
        }
    }

    /**
     * Stop monitoring (on logout or app termination)
     */
    fun stopMonitoring() {
        if (!isMonitoring) return

        isMonitoring = false
        Log.i(TAG, "Stopping connectivity monitoring")

        // Update backend to offline before stopping
        scope.launch {
            try {
                val request = ConnectivityUpdateRequest(
                    isConnected = false,
                    locationPermissionGranted = false,
                    deviceId = getDeviceId()
                )
                api.updateConnectivity(request)
                Log.d(TAG, "✅ Set user to offline before stopping")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting offline status", e)
            }
        }

        try {
            connectivityManager.unregisterNetworkCallback(connectivityCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering callback", e)
        }
    }

    /**
     * Register network callback to detect connectivity changes
     */
    private fun registerNetworkCallback() {
        try {
            connectivityManager.registerDefaultNetworkCallback(connectivityCallback)
            Log.d(TAG, "Network callback registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering network callback", e)
        }
    }

    /**
     * Start periodic connectivity updates
     */
    private fun startPeriodicUpdates() {
        scope.launch {
            while (isMonitoring) {
                try {
                    val now = System.currentTimeMillis()
                    val interval = if (checkInternetConnectivity()) {
                        UPDATE_INTERVAL_MS // 3 minutes when online
                    } else {
                        OFFLINE_CHECK_INTERVAL_MS // 30 seconds when offline (check more frequently)
                    }

                    if (now - lastUpdateTime >= interval) {
                        updateConnectivityStatus()
                        lastUpdateTime = now
                    }

                    delay(30_000) // Check every 30 seconds

                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic update", e)
                    delay(60_000) // Wait 1 minute on error
                }
            }
        }
    }

    /**
     * CRITICAL: Update connectivity status on backend
     * This sets is_reachable = true/false
     */
    private suspend fun updateConnectivityStatus() {
        try {
            val isConnected = checkInternetConnectivity()
            val hasLocationPermission = checkLocationPermission()
            val deviceId = getDeviceId()

            Log.d(TAG, "=== Updating Connectivity Status ===")
            Log.d(TAG, "Connected: $isConnected")
            Log.d(TAG, "Location Permission: $hasLocationPermission")
            Log.d(TAG, "Device ID: $deviceId")
            Log.d(TAG, "Will be reachable: ${isConnected && hasLocationPermission}")

            // Call backend API
            val request = ConnectivityUpdateRequest(
                isConnected = isConnected,
                locationPermissionGranted = hasLocationPermission,
                deviceId = deviceId
            )

            val response = api.updateConnectivity(request)

            if (response.isSuccessful) {
                val data = response.body()
                Log.d(TAG, "✅ Connectivity updated on backend")
                Log.d(TAG, "  - is_reachable: ${data?.data?.get("is_reachable")}")
                Log.d(TAG, "  - is_connected: ${data?.data?.get("is_connected")}")
                Log.d(TAG, "  - location_permission: ${data?.data?.get("location_permission_granted")}")
                Log.d(TAG, "  - device_id: ${data?.data?.get("device_id")}")

                wasConnected = isConnected
            } else {
                Log.e(TAG, "❌ Failed to update connectivity: ${response.code()}")
                Log.e(TAG, "  Error: ${response.errorBody()?.string()}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception updating connectivity", e)
        }
    }

    /**
     * Check if device has internet connectivity
     */
    private fun checkInternetConnectivity(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            hasInternet && isValidated
        } catch (e: Exception) {
            Log.e(TAG, "Error checking internet connectivity", e)
            false
        }
    }

    /**
     * Check if location permissions are granted
     */
    private fun checkLocationPermission(): Boolean {
        return try {
            LocationUtils.hasLocationPermission(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking location permission", e)
            false
        }
    }

    /**
     * Get unique device identifier
     */
    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device ID", e)
            "unknown"
        }
    }

    /**
     * Force update connectivity status (call manually if needed)
     */
    suspend fun forceUpdate() {
        Log.d(TAG, "Force updating connectivity status")
        updateConnectivityStatus()
    }
}