package com.smallbasket.dozo

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.smallbasket.dozo.location.LocationTrackingCoordinator
import com.google.firebase.FirebaseApp
//import com.smallbasket.dozo.notifications.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay

class SmallBasketApplication : Application() {

    companion object {
        private const val TAG = "SmallBasketApp"
        private const val INITIALIZATION_DELAY = 3000L
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override  fun onCreate() {
        super.onCreate()

        Log.d(TAG, "SmallBasket Application created")

        try {
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "✅ Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing Firebase", e)
        }

        // Create notification channels immediately at app start (before any message arrives)
        // This is critical — if channels don't exist when a background FCM message arrives,
        // Android 8+ silently drops the notification even though FCM reports success.
        try {
            com.smallbasket.dozo.notifications.NotificationManager
                .getInstance(this)
                .createChannels()
            Log.d(TAG, "✅ Notification channels created at startup")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating notification channels", e)
        }

        // Initialize location tracking coordinator with a small delay
        applicationScope.launch(Dispatchers.Main) {
            try {
                // Wait for activity to start and system to stabilize
                delay(1000) 

                Log.d(TAG, "Initializing location tracking coordinator...")
                LocationTrackingCoordinator.getInstance(this@SmallBasketApplication).initialize()
                Log.d(TAG, "✅ Location tracking coordinator initialized")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error initializing location tracking coordinator", e)
            }
        }


        applicationScope.launch {
            try {
                delay(INITIALIZATION_DELAY)

                val notificationManager = com.smallbasket.dozo.notifications.NotificationManager.getInstance(this@SmallBasketApplication)
                notificationManager.initialize()
                Log.d(TAG, "✅ Notifications initialized")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error initializing notifications", e)
            }
        }
    }


    override fun onTerminate() {
        super.onTerminate()

        Log.d(TAG, "SmallBasket Application terminated")

        // Clean up coordinator resources
        try {
            LocationTrackingCoordinator.getInstance(this).cleanup()
            Log.d(TAG, "Location tracking coordinator cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up location tracking coordinator", e)
        }
    }
}