// File: app/src/main/java/com/example/smallbasket/location/ConnectivityStatusManager.kt
package com.example.smallbasket.location

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.smallbasket.api.RetrofitClient
import com.example.smallbasket.models.ConnectivityUpdateRequest
import com.example.smallbasket.models.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * CRITICAL: Manages connectivity status and syncs with backend
 * ✅ UPDATED: Now sends device_id for accurate device counting
 */
class ConnectivityStatusManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ConnectivityManager"
        private const val UPDATE_INTERVAL_MS = 3 * 60 * 1000L // 3 minutes
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

    // ✅ NEW: Store device ID
    private val deviceId: String by lazy {
        try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device ID", e)
            "unknown"
        }
    }

    private val connectivityCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "✅ Network AVAILABLE")
            scope.launch {
                delay(2000) // Let connection stabilize
                updateConnectivityStatus()

                val intent = Intent("com.example.smallbasket.CONNECTIVITY_CHANGED")
                intent.putExtra("connected", true)
                context.sendBroadcast(intent)
            }
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "❌ Network LOST")
            scope.launch {
                updateConnectivityStatus()

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
                scope.launch {
                    delay(1000)
                    updateConnectivityStatus()
                }
            }
        }
    }

    fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Already monitoring")
            return
        }

        isMonitoring = true
        Log.i(TAG, "=== Starting Connectivity Monitoring ===")
        Log.i(TAG, "📱 Device ID: $deviceId")

        registerNetworkCallback()
        startPeriodicUpdates()

        scope.launch {
            updateConnectivityStatus()
        }
    }

    fun stopMonitoring() {
        if (!isMonitoring) return

        isMonitoring = false
        Log.i(TAG, "Stopping connectivity monitoring")

        scope.launch {
            try {
                val request = ConnectivityUpdateRequest(
                    isConnected = false,
                    locationPermissionGranted = false,
                    deviceId = deviceId  // ✅ Send device_id
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

    private fun registerNetworkCallback() {
        try {
            connectivityManager.registerDefaultNetworkCallback(connectivityCallback)
            Log.d(TAG, "Network callback registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering network callback", e)
        }
    }

    private fun startPeriodicUpdates() {
        scope.launch {
            while (isMonitoring) {
                try {
                    val now = System.currentTimeMillis()
                    val interval = if (checkInternetConnectivity()) {
                        UPDATE_INTERVAL_MS
                    } else {
                        OFFLINE_CHECK_INTERVAL_MS
                    }

                    if (now - lastUpdateTime >= interval) {
                        updateConnectivityStatus()
                        lastUpdateTime = now
                    }

                    delay(30_000) // Check every 30 seconds

                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic update", e)
                    delay(60_000)
                }
            }
        }
    }

    /**
     * ✅ UPDATED: Now sends device_id to backend
     */
    /**
     * FIXED: Make device_id required
     */
    /**
     * Update connectivity status with proper device info
     * FIXED: Use DeviceInfo data class instead of Map
     */
    private suspend fun updateConnectivityStatus() {
        try {
            val isConnected = checkInternetConnectivity()
            val hasLocationPermission = checkLocationPermission()

            Log.d(TAG, "=== Updating Connectivity Status ===")
            Log.d(TAG, "Connected: $isConnected")
            Log.d(TAG, "Location Permission: $hasLocationPermission")
            Log.d(TAG, "Device ID: $deviceId")
            Log.d(TAG, "Will be reachable: ${isConnected && hasLocationPermission}")

            // FIXED: Validate device_id before sending
            if (deviceId == "unknown" || deviceId.isEmpty()) {
                Log.e(TAG, "❌ Invalid device_id, cannot update connectivity")
                return
            }

            // ✅ FIXED: Create DeviceInfo object instead of Map
            val deviceInfo = try {
                DeviceInfo(
                    os = "Android",
                    model = Build.MODEL,
                    appVersion = context.packageManager
                        .getPackageInfo(context.packageName, 0)
                        .versionName,
                    manufacturer = Build.MANUFACTURER
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not get full device info", e)
                DeviceInfo(
                    os = "Android",
                    model = android.os.Build.MODEL,
                    appVersion = "unknown",
                    manufacturer = android.os.Build.MANUFACTURER
                )
            }

            // ✅ FIXED: Use ConnectivityUpdateRequest with DeviceInfo
            val request = ConnectivityUpdateRequest(
                isConnected = isConnected,
                locationPermissionGranted = hasLocationPermission,
                deviceId = deviceId,
                deviceInfo = deviceInfo  // ✅ Now using DeviceInfo object
            )

            val response = api.updateConnectivity(request)

            if (response.isSuccessful) {
                val data = response.body()
                Log.d(TAG, "✅ Connectivity updated on backend")
                Log.d(TAG, "  - is_reachable: ${data?.data?.get("is_reachable")}")
                Log.d(TAG, "  - is_connected: ${data?.data?.get("is_connected")}")
                Log.d(TAG, "  - location_permission: ${data?.data?.get("location_permission_granted")}")
                Log.d(TAG, "  - device_id: ${data?.data?.get("device_id")}")
                Log.d(TAG, "  - device_tracked: ${data?.data?.get("device_tracked")}")

                wasConnected = isConnected
            } else {
                Log.e(TAG, "❌ Failed to update connectivity: ${response.code()}")
                Log.e(TAG, "  Error: ${response.errorBody()?.string()}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception updating connectivity", e)
        }
    }

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

    private fun checkLocationPermission(): Boolean {
        return try {
            LocationUtils.hasLocationPermission(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking location permission", e)
            false
        }
    }

    suspend fun forceUpdate() {
        Log.d(TAG, "Force updating connectivity status")
        updateConnectivityStatus()
    }
}