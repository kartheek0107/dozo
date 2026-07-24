// File: app/src/main/java/com/smallbasket/dozo/location/PermissionCoordinator.kt
package com.smallbasket.dozo.location

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Centralized permission coordinator to prevent permission request loops
 *
 * Key features:
 * - Prevents recursive permission requests
 * - Implements cooldown period between requests
 * - Tracks permission state across lifecycle events
 * - Manages dialog conflicts
 */
class PermissionCoordinator private constructor(
    private val context: Context
) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "PermissionCoordinator"

        // Cooldown between permission requests to prevent loops
        private const val REQUEST_COOLDOWN_MS = 1000L

        // Maximum time a request can be "in progress" before auto-reset
        private const val MAX_REQUEST_DURATION_MS = 15000L

        @Volatile
        private var instance: PermissionCoordinator? = null

        fun getInstance(context: Context): PermissionCoordinator {
            return instance ?: synchronized(this) {
                instance ?: PermissionCoordinator(context.applicationContext)
                    .also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // State tracking
    private var isRequestInProgress = false
    private var lastRequestTime = 0L
    private var requestStartTime = 0L

    // Activity reference (weak to prevent leaks)
    private var currentActivity: FragmentActivity? = null

    // Permission manager instance
    private var permissionManager: LocationPermissionManager? = null

    // Callback storage
    private var pendingCallback: ((Boolean) -> Unit)? = null

    /**
     * Initialize with the current activity
     * Call this in Activity.onCreate()
     */
    fun initialize(activity: FragmentActivity) {
        Log.d(TAG, "Initializing PermissionCoordinator with new activity: ${activity.localClassName}")
        currentActivity = activity

        // Always create a new permission manager for the new activity instance
        // to avoid stale activity references (leaks and broken dialogs/launchers)
        permissionManager = LocationPermissionManager(activity)

        // Observe lifecycle to auto-reset on destroy
        activity.lifecycle.addObserver(this)
    }

    /**
     * Set the permission launcher
     * Call this in Activity.onCreate() after registerForActivityResult
     */
    fun setPermissionLauncher(launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
        permissionManager?.setPermissionLauncher(launcher)
    }

    /**
     * Check if all required permissions are granted
     */
    fun hasAllPermissions(): Boolean {
        return permissionManager?.hasAllPermissions() ?: false
    }

    /**
     * Request permissions with loop prevention
     *
     * @param fromOnResume If true, applies stricter checks to prevent resume loops
     * @param callback Called when request completes
     */
    fun requestPermissions(
        fromOnResume: Boolean = false,
        callback: (Boolean) -> Unit
    ) {
        val now = System.currentTimeMillis()

        // Check 1: Is a request already in progress?
        if (isRequestInProgress) {
            val timeSinceStart = now - requestStartTime

            if (timeSinceStart > MAX_REQUEST_DURATION_MS) {
                // Request has been stuck for too long - force reset
                Log.w(TAG, "Permission request stuck for ${timeSinceStart}ms - forcing reset")
                forceReset()
            } else {
                Log.d(TAG, "Permission request already in progress (${timeSinceStart}ms ago)")
                return
            }
        }

        // Check 2: Cooldown period
        val timeSinceLastRequest = now - lastRequestTime
        if (timeSinceLastRequest < REQUEST_COOLDOWN_MS) {
            Log.d(TAG, "Cooldown active - ${REQUEST_COOLDOWN_MS - timeSinceLastRequest}ms remaining")

            // If called from onResume, just skip silently
            if (fromOnResume) {
                return
            }

            // For explicit user actions, wait then retry
            scope.launch {
                delay(REQUEST_COOLDOWN_MS - timeSinceLastRequest)
                requestPermissions(fromOnResume = false, callback = callback)
            }
            return
        }

        // Check 3: Do we have an activity?
        val activity = currentActivity
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Cannot request permissions - no valid activity")
            callback(false)
            return
        }

        // Check 4: Do we have a permission manager?
        val manager = permissionManager
        if (manager == null) {
            Log.e(TAG, "Permission manager not initialized")
            callback(false)
            return
        }

        // All checks passed - proceed with request
        Log.d(TAG, "=== Starting permission request ===")
        Log.d(TAG, "  From onResume: $fromOnResume")
        Log.d(TAG, "  Time since last: ${timeSinceLastRequest}ms")

        isRequestInProgress = true
        requestStartTime = now
        lastRequestTime = now
        pendingCallback = callback

        // Schedule auto-reset as safety net
        scope.launch {
            delay(MAX_REQUEST_DURATION_MS)
            if (isRequestInProgress) {
                Log.w(TAG, "Permission request timeout - auto-resetting")
                forceReset()
                pendingCallback?.invoke(false)
                pendingCallback = null
            }
        }

        // Make the actual request
        manager.requestPermissions { granted ->
            handlePermissionResult(granted)
        }
    }

    /**
     * Handle permission result
     */
    private fun handlePermissionResult(granted: Boolean) {
        Log.d(TAG, "=== Permission result received ===")
        Log.d(TAG, "  Granted: $granted")
        Log.d(TAG, "  Duration: ${System.currentTimeMillis() - requestStartTime}ms")

        // Clear state
        isRequestInProgress = false
        requestStartTime = 0L

        // Invoke callback
        val callback = pendingCallback
        pendingCallback = null
        callback?.invoke(granted)
    }

    /**
     * Handle permission result from launcher
     * Call this from your ActivityResultLauncher callback
     */
    fun handlePermissionResult(permissions: Map<String, Boolean>) {
        permissionManager?.handlePermissionResult(permissions)
    }

    /**
     * Show location services dialog
     */
    fun showLocationServicesDialog() {
        if (isRequestInProgress) {
            Log.d(TAG, "Skipping location services dialog - request in progress")
            return
        }

        permissionManager?.showLocationServicesDialog()
    }

    /**
     * Force reset the coordinator state
     * Use this only in edge cases where state gets stuck
     */
    fun forceReset() {
        Log.w(TAG, "Force resetting permission coordinator")
        isRequestInProgress = false
        requestStartTime = 0L
        pendingCallback = null
    }

    /**
     * Get current state for debugging
     */
    fun getState(): PermissionState {
        return PermissionState(
            isRequestInProgress = isRequestInProgress,
            hasAllPermissions = hasAllPermissions(),
            timeSinceLastRequest = if (lastRequestTime > 0) {
                System.currentTimeMillis() - lastRequestTime
            } else {
                -1
            },
            cooldownRemaining = if (lastRequestTime > 0) {
                val elapsed = System.currentTimeMillis() - lastRequestTime
                (REQUEST_COOLDOWN_MS - elapsed).coerceAtLeast(0)
            } else {
                0
            }
        )
    }

    // Lifecycle callbacks
    override fun onResume(owner: LifecycleOwner) {
        Log.d(TAG, "Activity resumed - state: ${getState()}")
    }

    override fun onPause(owner: LifecycleOwner) {
        Log.d(TAG, "Activity paused")
    }

    override fun onDestroy(owner: LifecycleOwner) {
        Log.d(TAG, "Activity destroyed - cleaning up")
        currentActivity = null
        forceReset()
    }
}

/**
 * Data class representing permission coordinator state
 */
data class PermissionState(
    val isRequestInProgress: Boolean,
    val hasAllPermissions: Boolean,
    val timeSinceLastRequest: Long,
    val cooldownRemaining: Long
) {
    override fun toString(): String {
        return """
            PermissionState(
              inProgress=$isRequestInProgress,
              hasAll=$hasAllPermissions,
              timeSinceLast=${timeSinceLastRequest}ms,
              cooldown=${cooldownRemaining}ms
            )
        """.trimIndent()
    }
}