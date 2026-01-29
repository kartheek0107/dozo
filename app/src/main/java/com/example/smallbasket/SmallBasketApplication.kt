package com.example.smallbasket

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.example.smallbasket.location.LocationTrackingCoordinator
import com.google.firebase.FirebaseApp
//import com.example.smallbasket.notifications.NotificationScheduler
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

        // Initialize location tracking coordinator
        applicationScope.launch(Dispatchers.IO) {
            try {
                // Add delay to ensure all system services are ready after device boot
                delay(INITIALIZATION_DELAY)

                Log.d(TAG, "Initializing location tracking coordinator...")
                LocationTrackingCoordinator.getInstance(this@SmallBasketApplication).initialize()
                Log.d(TAG, "✅ Location tracking coordinator initialized")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error initializing location tracking coordinator", e)
            }
        }


        // Initialize notifications
//        applicationScope.launch {
//            try {
//                val notificationManager = com.example.smallbasket.notifications.NotificationManager.getInstance(this@SmallBasketApplication)
//                notificationManager.initialize()
//                Log.d(TAG, "Notifications initialized")
//
//                // Schedule promotional notifications
//                NotificationScheduler.schedulePromotionalNotifications(this@SmallBasketApplication)
//
//            } catch (e: Exception) {
//                Log.e(TAG, "Error initializing notifications", e)
//            }
//        }
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