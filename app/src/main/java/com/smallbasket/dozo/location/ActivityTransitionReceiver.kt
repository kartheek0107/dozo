package com.smallbasket.dozo.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Receives activity transition events and adjusts WorkManager schedule
 */
class ActivityTransitionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ActivityTransition"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent) ?: return

            CoroutineScope(Dispatchers.Default).launch {
                handleActivityTransitions(context, result)
            }
        }
    }

    /**
     * Process activity transitions and adjust location tracking interval
     */
    private fun handleActivityTransitions(context: Context, result: ActivityTransitionResult) {
        val repository = LocationRepository.getInstance(context)
        val scheduler = LocationWorkScheduler.getInstance(context)
        val activityManager = ActivityRecognitionManager(context)

        for (event in result.transitionEvents) {
            val activityName = activityManager.getActivityName(event.activityType)
            val transitionType = if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                "ENTER"
            } else {
                "EXIT"
            }

            Log.d(TAG, "Activity transition: $activityName $transitionType")

            // Only process ENTER transitions
            if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                when (event.activityType) {
                    DetectedActivity.WALKING,
                    DetectedActivity.RUNNING,
                    DetectedActivity.ON_BICYCLE -> {
                        // User started moving - increase frequency to 15-20 minutes
                        Log.i(TAG, "User is MOVING - scheduling every 15 minutes")
                        repository.saveActivityState(isMoving = true)
                        scheduler.scheduleLocationWork(intervalMinutes = 15)

                        // Capture starting location for distance tracking
                        captureStartingLocation(context, repository)
                    }

                    DetectedActivity.STILL -> {
                        // User became stationary - decrease frequency to 25-30 minutes
                        Log.i(TAG, "User is STATIONARY - scheduling every 30 minutes")
                        repository.saveActivityState(isMoving = false)
                        scheduler.scheduleLocationWork(intervalMinutes = 30)
                    }
                }
            }
        }
    }

    /**
     * Capture current location as the starting point for distance tracking
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun captureStartingLocation(context: Context, repository: LocationRepository) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!LocationUtils.hasLocationPermission(context)) return@launch

                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                val location = fusedLocationClient.lastLocation.await()

                if (location != null) {
                    val locationData = LocationData.fromLocation(
                        location = location,
                        source = LocationData.LocationSource.ACTIVITY_TRIGGERED,
                        activityType = "WALKING_START"
                    )
                    repository.setLastDistanceLocation(locationData)
                    Log.d(TAG, "Captured starting location for fitness tracking")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error capturing starting location", e)
            }
        }
    }
}