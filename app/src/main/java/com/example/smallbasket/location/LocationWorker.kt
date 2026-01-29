// File: app/src/main/java/com/example/smallbasket/location/LocationWorker.kt
package com.example.smallbasket.location

import android.Manifest
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.smallbasket.api.RetrofitClient
import com.example.smallbasket.models.UpdateGPSLocationRequest
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * FIXED: Background worker that fetches location AND syncs with backend immediately
 * Now handles device restart gracefully without crashing
 */
class LocationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "LocationWorker"
        const val WORK_NAME = "location_tracking_work"
        private const val LOCATION_TIMEOUT_MS = 5000L
        private const val CACHE_MAX_AGE_MS = 10 * 60 * 1000L
        private const val MAX_RETRIES = 3
        private const val INITIALIZATION_DELAY = 2000L // Wait 2 seconds for app initialization
    }

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val repository = LocationRepository.getInstance(context)
    private val api = RetrofitClient.apiService

    override suspend fun doWork(): Result {
        Log.d(TAG, "=== LocationWorker STARTED ===")

        return try {
            // STEP 1: Wait for app initialization (critical after device restart)
            delay(INITIALIZATION_DELAY)
            Log.d(TAG, "Initialization delay completed")

            // STEP 2: Verify Firebase is initialized (prevents crashes)
            if (!isFirebaseReady()) {
                Log.w(TAG, "❌ Firebase not ready, will retry later")
                return Result.retry()
            }

            // STEP 3: Check if user is authenticated
            if (!isUserAuthenticated()) {
                Log.w(TAG, "⚠️ User not authenticated, skipping location tracking")
                return Result.success()
            }

            // STEP 4: Check if tracking is enabled (with error handling)
            if (!isTrackingEnabled()) {
                Log.d(TAG, "Tracking disabled, skipping")
                return Result.success()
            }

            // STEP 5: Verify permissions
            if (!LocationUtils.hasLocationPermission(applicationContext)) {
                Log.w(TAG, "❌ No location permission, stopping work")
                return Result.failure()
            }

            // STEP 6: Check location services
            if (!LocationUtils.isLocationEnabled(applicationContext)) {
                Log.w(TAG, "⚠️ Location services disabled, will retry")
                return Result.retry()
            }

            // STEP 7: Proceed with location tracking
            performLocationTracking()

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception - missing permissions", e)
            Result.failure()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "❌ IllegalStateException - app not ready", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unexpected error in LocationWorker", e)
            Result.retry()
        }
    }

    /**
     * Check if Firebase is properly initialized
     */
    private fun isFirebaseReady(): Boolean {
        return try {
            FirebaseApp.getInstance()
            Log.d(TAG, "✅ Firebase is ready")
            true
        } catch (e: IllegalStateException) {
            Log.e(TAG, "❌ Firebase not initialized", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking Firebase state", e)
            false
        }
    }

    /**
     * Check if user is authenticated
     */
    private fun isUserAuthenticated(): Boolean {
        return try {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                Log.d(TAG, "✅ User authenticated: ${currentUser.uid}")
                true
            } else {
                Log.w(TAG, "⚠️ No user signed in")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking auth state", e)
            false
        }
    }

    /**
     * Check if tracking is enabled (with error handling)
     */
    private fun isTrackingEnabled(): Boolean {
        return try {
            repository.isTrackingEnabled()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking tracking state", e)
            // Default to false on error to prevent crashes
            false
        }
    }

    /**
     * Perform the actual location tracking
     */
    private suspend fun performLocationTracking(): Result {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Attempting to get location")

        // Step 1: Get location (try cache first, then fresh)
        val location = getCachedLocation() ?: run {
            Log.d(TAG, "No fresh cache, requesting new location")
            requestFreshLocation()
        }

        if (location != null) {
            Log.d(TAG, "✓ Got location: (${location.latitude}, ${location.longitude})")

            // Step 2: Save locally
            try {
                val locationData = LocationData.fromLocation(
                    location = location,
                    source = LocationData.LocationSource.BACKGROUND_WORKER,
                    activityType = if (repository.getLastActivityState()) "MOVING" else "STILL",
                    batteryLevel = repository.getBatteryLevel(applicationContext)
                )
                repository.saveLocation(locationData)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error saving location locally", e)
                // Continue even if local save fails
            }

            // Step 3: IMMEDIATELY sync with backend (THIS IS THE FIX!)
            val syncSuccess = syncLocationWithBackend(location)

            if (syncSuccess) {
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "✅ Work completed successfully in ${duration}ms")
                return Result.success()
            } else {
                Log.w(TAG, "⚠️ Location sync failed, will retry")
                return Result.retry()
            }
        } else {
            Log.w(TAG, "✗ Failed to get location")
            return Result.retry()
        }
    }

    /**
     * FIXED: Sync location with backend API with retry logic
     * Returns true if successful, false otherwise
     */
    private suspend fun syncLocationWithBackend(location: Location): Boolean {
        var retryCount = 0

        while (retryCount < MAX_RETRIES) {
            try {
                Log.d(TAG, "Syncing location with backend (attempt ${retryCount + 1}/$MAX_RETRIES)")

                val request = UpdateGPSLocationRequest(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    fastMode = true // Use fast mode for background updates
                )

                // Call backend API
                val response = api.updateGPSLocation(request)

                if (response.isSuccessful) {
                    val data = response.body()
                    Log.d(TAG, "✅ Location synced successfully!")
                    Log.d(TAG, "  - Primary area: ${data?.data?.primaryArea}")
                    Log.d(TAG, "  - All areas: ${data?.data?.allMatchingAreas}")
                    Log.d(TAG, "  - Is on edge: ${data?.data?.isOnEdge}")
                    return true
                } else {
                    Log.w(TAG, "⚠️ Backend returned error: ${response.code()} - ${response.message()}")
                    val errorBody = response.errorBody()?.string()
                    Log.w(TAG, "  Error body: $errorBody")
                }

            } catch (e: IllegalStateException) {
                // Firebase not ready or auth token issue
                Log.e(TAG, "❌ IllegalStateException during sync (attempt ${retryCount + 1})", e)
                // Don't retry immediately if it's a state issue
                delay(5000) // Wait 5 seconds before retry
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception syncing location (attempt ${retryCount + 1})", e)
            }

            retryCount++
            if (retryCount < MAX_RETRIES) {
                // Exponential backoff: 1s, 2s, 4s
                val delayMs = (1000L * Math.pow(2.0, retryCount.toDouble() - 1)).toLong()
                Log.d(TAG, "Retrying in ${delayMs}ms...")
                delay(delayMs)
            }
        }

        Log.e(TAG, "❌ Failed to sync location after $MAX_RETRIES attempts")
        return false
    }

    @Suppress("MissingPermission")
    private fun getCachedLocation(): Location? {
        return try {
            if (!LocationUtils.hasLocationPermission(applicationContext)) {
                Log.w(TAG, "Missing location permission for cached location")
                return null
            }

            val task = fusedLocationClient.lastLocation
            val location = Tasks.await(task, 1000, TimeUnit.MILLISECONDS)

            if (location != null) {
                val age = System.currentTimeMillis() - location.time
                Log.d(TAG, "Cached location age: ${age}ms")

                if (LocationUtils.isLocationFresh(location.time, CACHE_MAX_AGE_MS)) {
                    Log.d(TAG, "✅ Using cached location")
                    location
                } else {
                    Log.d(TAG, "⚠️ Cached location too old")
                    null
                }
            } else {
                Log.d(TAG, "No cached location available")
                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception getting cached location", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting cached location", e)
            null
        }
    }

    @Suppress("MissingPermission")
    private suspend fun requestFreshLocation(): Location? {
        return try {
            if (!LocationUtils.hasLocationPermission(applicationContext)) {
                Log.w(TAG, "Missing location permission for fresh location")
                return null
            }

            Log.d(TAG, "Requesting fresh location with balanced power")

            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(60000)
                .setDurationMillis(LOCATION_TIMEOUT_MS)
                .build()

            val task = fusedLocationClient.getCurrentLocation(request, null)
            val location = Tasks.await(task, LOCATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)

            if (location == null) {
                Log.w(TAG, "⚠️ Location request returned null")
            } else {
                Log.d(TAG, "✅ Fresh location obtained: accuracy=${location.accuracy}m")
            }

            location

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception requesting fresh location", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error requesting fresh location", e)
            null
        }
    }
}