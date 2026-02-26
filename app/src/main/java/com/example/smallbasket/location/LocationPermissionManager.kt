// File: app/src/main/java/com/example/smallbasket/location/LocationPermissionManager.kt
package com.example.smallbasket.location

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Manages location permissions with proper dialog lifecycle management
 *
 * FIXED: Prevents dialog stacking and properly dismisses previous dialogs
 */
class LocationPermissionManager(private val activity: FragmentActivity) {

    companion object {
        private const val TAG = "LocationPermissionMgr"

        val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var onPermissionResult: ((Boolean) -> Unit)? = null

    // ---- FIX: Track current dialog to prevent stacking
    private var currentDialog: AlertDialog? = null

    /**
     * Set the permission launcher from the Activity
     * Must be called in onCreate()
     */
    fun setPermissionLauncher(launcher: ActivityResultLauncher<Array<String>>) {
        this.permissionLauncher = launcher
    }

    /**
     * Check if all required permissions are granted
     */
    fun hasAllPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(
                activity,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if we should show permission rationale
     */
    private fun shouldShowRationale(): Boolean {
        return REQUIRED_PERMISSIONS.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }

    /**
     * Request permissions with callback
     */
    fun requestPermissions(onResult: (Boolean) -> Unit) {
        Log.d(TAG, "=== Permission request started ===")

        // Store callback
        onPermissionResult = onResult

        // Check if permissions already granted
        if (hasAllPermissions()) {
            Log.d(TAG, "All permissions already granted")
            onResult(true)
            return
        }

        // Check if launcher is set
        val launcher = permissionLauncher
        if (launcher == null) {
            Log.e(TAG, "Permission launcher not set!")
            onResult(false)
            return
        }

        // Check if we should show rationale
        if (shouldShowRationale()) {
            Log.d(TAG, "Showing rationale dialog")
            showPermissionRationaleDialog {
                Log.d(TAG, "Rationale accepted - launching permission request")
                launcher.launch(REQUIRED_PERMISSIONS)
            }
        } else {
            Log.d(TAG, "Launching permission request directly")
            launcher.launch(REQUIRED_PERMISSIONS)
        }
    }

    /**
     * Handle permission result from launcher
     */
    fun handlePermissionResult(permissions: Map<String, Boolean>) {
        Log.d(TAG, "=== Permission result received ===")
        permissions.forEach { (permission, granted) ->
            Log.d(TAG, "  $permission: $granted")
        }

        val allGranted = permissions.values.all { it }

        if (allGranted) {
            Log.d(TAG, "✅ All permissions granted")
            onPermissionResult?.invoke(true)
        } else {
            Log.d(TAG, "❌ Some permissions denied")

            // Check if any permission was permanently denied
            val anyPermanentlyDenied = REQUIRED_PERMISSIONS.any { permission ->
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) &&
                        ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
            }

            if (anyPermanentlyDenied) {
                Log.d(TAG, "Some permissions permanently denied - showing settings dialog")
                showPermissionDeniedDialog()
            } else {
                Log.d(TAG, "Permissions denied but not permanently")
                onPermissionResult?.invoke(false)
            }
        }

        onPermissionResult = null
    }

    /**
     * Show permission rationale dialog
     *
     * ---- FIX: Properly dismiss previous dialog before showing new one
     */
    private fun showPermissionRationaleDialog(onAccept: () -> Unit) {
        // ---- FIX: Dismiss any existing dialog first
        dismissCurrentDialog()

        Log.d(TAG, "Creating rationale dialog")

        currentDialog = AlertDialog.Builder(activity)
            .setTitle("Location Permission Required")
            .setMessage(
                "Dozo needs your location to:\n\n" +
                        "• Show nearby delivery requests\n" +
                        "• Calculate accurate distances\n" +
                        "• Enable real-time tracking\n\n" +
                        "Your location is only used while the app is open."
            )
            .setPositiveButton("Grant Permission") { dialog, _ ->
                Log.d(TAG, "User accepted rationale")
                dialog.dismiss()
                currentDialog = null
                onAccept()
            }
            .setNegativeButton("Not Now") { dialog, _ ->
                Log.d(TAG, "User declined rationale")
                dialog.dismiss()
                currentDialog = null
                onPermissionResult?.invoke(false)
                onPermissionResult = null
            }
            .setOnDismissListener {
                Log.d(TAG, "Rationale dialog dismissed")
                currentDialog = null
            }
            .setCancelable(false)
            .create()

        currentDialog?.show()
    }

    /**
     * Show permission permanently denied dialog
     *
     * ---- FIX: Properly dismiss previous dialog before showing new one
     */
    private fun showPermissionDeniedDialog() {
        // ---- FIX: Dismiss any existing dialog first
        dismissCurrentDialog()

        Log.d(TAG, "Creating permission denied dialog")

        currentDialog = AlertDialog.Builder(activity)
            .setTitle("Permission Required")
            .setMessage(
                "Location permission is essential for Dozo to function.\n\n" +
                        "Please enable it in Settings:\n" +
                        "Settings > Apps > Dozo > Permissions > Location > Allow"
            )
            .setPositiveButton("Open Settings") { dialog, _ ->
                Log.d(TAG, "User chose to open settings")
                dialog.dismiss()
                currentDialog = null
                openAppSettings()
                onPermissionResult?.invoke(false)
                onPermissionResult = null
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                Log.d(TAG, "User cancelled settings")
                dialog.dismiss()
                currentDialog = null
                onPermissionResult?.invoke(false)
                onPermissionResult = null
            }
            .setOnDismissListener {
                Log.d(TAG, "Permission denied dialog dismissed")
                currentDialog = null
            }
            .setCancelable(false)
            .create()

        currentDialog?.show()
    }

    /**
     * Show location services dialog
     *
     * ---- FIX: Properly dismiss previous dialog before showing new one
     */
    fun showLocationServicesDialog() {
        // ---- FIX: Dismiss any existing dialog first
        dismissCurrentDialog()

        Log.d(TAG, "Creating location services dialog")

        currentDialog = AlertDialog.Builder(activity)
            .setTitle("Enable Location Services")
            .setMessage(
                "Location services are turned off.\n\n" +
                        "Dozo requires location services to:\n" +
                        "• Find nearby delivery requests\n" +
                        "• Track your deliveries\n\n" +
                        "Please enable location services to continue."
            )
            .setPositiveButton("Enable") { dialog, _ ->
                Log.d(TAG, "User chose to enable location services")
                dialog.dismiss()
                currentDialog = null
                openLocationSettings()
            }
            .setNegativeButton("Not Now") { dialog, _ ->
                Log.d(TAG, "User declined to enable location services")
                dialog.dismiss()
                currentDialog = null
            }
            .setOnDismissListener {
                Log.d(TAG, "Location services dialog dismissed")
                currentDialog = null
            }
            .setCancelable(true)
            .create()

        currentDialog?.show()
    }

    /**
     * ---- FIX: Helper method to safely dismiss current dialog
     */
    private fun dismissCurrentDialog() {
        try {
            currentDialog?.let { dialog ->
                if (dialog.isShowing) {
                    Log.d(TAG, "Dismissing previous dialog")
                    dialog.dismiss()
                }
            }
            currentDialog = null
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing dialog", e)
            currentDialog = null
        }
    }

    /**
     * Open app settings
     */
    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app settings", e)
        }
    }

    /**
     * Open location settings
     */
    private fun openLocationSettings() {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening location settings", e)
        }
    }

    /**
     * Clean up resources
     * Call this when the activity is destroyed
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up LocationPermissionManager")
        dismissCurrentDialog()
        onPermissionResult = null
        permissionLauncher = null
    }
}