package com.example.smallbasket.notifications

import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import com.example.smallbasket.api.RetrofitClient
import com.example.smallbasket.models.FCMTokenRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class NotificationManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "NotificationManager"
        private const val PREFS_NAME = "notification_prefs"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_NOTIFICATIONS = "saved_notifications"
        private const val MAX_NOTIFICATIONS = 100

        @Volatile
        private var INSTANCE: NotificationManager? = null

        fun getInstance(context: Context): NotificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun saveFCMToken(context: Context, token: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
            Log.d(TAG, "💾 FCM token saved locally: ${token.take(20)}...")
        }

        fun saveNotification(context: Context, notification: NotificationData) {
            getInstance(context).saveNotificationToHistory(notification)
        }

        fun initialize(context: Context) {
            getInstance(context).initialize()
        }
    }

    private val gson = Gson()
    private val api = RetrofitClient.apiService

    // ✅ FIX: Named helper functions using Array deserialization instead of
    // anonymous TypeToken subclasses. Anonymous TypeToken subclasses are collapsed
    // by R8 in release builds, causing "Class cannot be cast to ParameterizedType".
    // Array<T>::class.java is a plain class reference — R8 never touches it.

    private fun jsonToMutableNotificationList(json: String): MutableList<SavedNotification> {
        return try {
            gson.fromJson(json, Array<SavedNotification>::class.java)
                ?.toMutableList() ?: mutableListOf()
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing notifications", e)
            mutableListOf()
        }
    }

    private fun jsonToNotificationList(json: String): List<SavedNotification> {
        return try {
            gson.fromJson(json, Array<SavedNotification>::class.java)
                ?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing notifications", e)
            emptyList()
        }
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channelNewRequests = NotificationChannel(
                FCMService.CHANNEL_NEW_REQUESTS,
                "New Delivery Requests",
                AndroidNotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new delivery requests in your area"
                enableLights(true)
                lightColor = Color.parseColor("#14B8A6")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 200, 250)
                setSound(soundUri, audioAttributes)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val channelOrderUpdates = NotificationChannel(
                FCMService.CHANNEL_ORDER_UPDATES,
                "Order Updates",
                AndroidNotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Updates about your orders (accepted, completed, cancelled)"
                enableLights(true)
                lightColor = Color.parseColor("#14B8A6")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 150, 200)
                setSound(soundUri, audioAttributes)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val channelGeneral = NotificationChannel(
                FCMService.CHANNEL_GENERAL,
                "General Notifications",
                AndroidNotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "General app notifications and announcements"
                setShowBadge(false)
            }

            nm.createNotificationChannel(channelNewRequests)
            nm.createNotificationChannel(channelOrderUpdates)
            nm.createNotificationChannel(channelGeneral)

            Log.d(TAG, "✅ Notification channels created (v2)")
        }
    }

    fun initialize() {
        Log.d(TAG, "=== Initializing FCM ===")

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "❌ Failed to get FCM token", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG, "✅ FCM Token retrieved: ${token.take(20)}...")

            saveFCMToken(context, token)
            registerTokenWithBackend(token)
        }
    }

    fun getFCMToken(): String? {
        return getPrefs().getString(KEY_FCM_TOKEN, null)
    }

    private fun registerTokenWithBackend(token: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w(TAG, "⚠️ User not logged in, skipping backend registration")
            return
        }

        Log.d(TAG, "Registering FCM token with backend for user: ${currentUser.email}")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = FCMTokenRequest(fcmToken = token)
                val response = api.registerFCMToken(request)

                if (response.isSuccessful) {
                    Log.d(TAG, "✅ FCM token registered with backend successfully")
                    Log.d(TAG, "  Response: ${response.body()?.message}")
                } else {
                    Log.e(TAG, "❌ Backend registration failed: ${response.code()}")
                    Log.e(TAG, "  Error: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception registering token with backend", e)
            }
        }
    }

    fun unregisterToken() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w(TAG, "⚠️ User not logged in, skipping unregister")
            clearLocalData()
            return
        }

        Log.d(TAG, "Unregistering FCM token from backend")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = api.unregisterFCMToken()

                if (response.isSuccessful) {
                    Log.d(TAG, "✅ FCM token unregistered from backend")
                } else {
                    Log.e(TAG, "❌ Backend unregister failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error unregistering token", e)
            } finally {
                clearLocalData()
            }
        }
    }

    private fun clearLocalData() {
        getPrefs().edit().clear().apply()
        Log.d(TAG, "🗑️ Local notification data cleared")
    }

    fun saveNotificationToHistory(notification: NotificationData) {
        val prefs = getPrefs()
        // ✅ FIX: Use array-based helper instead of anonymous TypeToken
        val notifications = jsonToMutableNotificationList(
            prefs.getString(KEY_NOTIFICATIONS, "[]") ?: "[]"
        )

        val savedNotification = SavedNotification(
            id = UUID.randomUUID().toString(),
            type = notification.type,
            title = notification.title,
            body = notification.body,
            orderId = notification.orderId,
            timestamp = System.currentTimeMillis(),
            isRead = false,
            priority = notification.priority
        )
        notifications.add(0, savedNotification)

        Log.d(TAG, "💾 Saving notification: ${notification.title}")
        Log.d(TAG, "  Type: ${notification.type}")
        Log.d(TAG, "  Order ID: ${notification.orderId}")

        val trimmedNotifications = if (notifications.size > MAX_NOTIFICATIONS) {
            notifications.take(MAX_NOTIFICATIONS).toMutableList()
        } else {
            notifications
        }

        prefs.edit().putString(KEY_NOTIFICATIONS, gson.toJson(trimmedNotifications)).apply()
        Log.d(TAG, "💾 Notification saved to history (total: ${trimmedNotifications.size})")
    }

    fun getNotifications(): List<SavedNotification> {
        val prefs = getPrefs()
        // ✅ FIX: Use array-based helper instead of anonymous TypeToken
        return jsonToNotificationList(
            prefs.getString(KEY_NOTIFICATIONS, "[]") ?: "[]"
        )
    }

    fun getSavedNotifications(): List<SavedNotification> {
        return getNotifications()
    }

    fun markAsRead(notificationId: String) {
        val prefs = getPrefs()
        // ✅ FIX: Use array-based helper instead of anonymous TypeToken
        val notifications = jsonToMutableNotificationList(
            prefs.getString(KEY_NOTIFICATIONS, "[]") ?: "[]"
        )

        val updated = notifications.map {
            if (it.id == notificationId) it.copy(isRead = true) else it
        }

        prefs.edit().putString(KEY_NOTIFICATIONS, gson.toJson(updated)).apply()
        Log.d(TAG, "✅ Notification marked as read: $notificationId")
    }

    fun markAllAsRead() {
        val prefs = getPrefs()
        // ✅ FIX: Use array-based helper instead of anonymous TypeToken
        val notifications = jsonToMutableNotificationList(
            prefs.getString(KEY_NOTIFICATIONS, "[]") ?: "[]"
        )

        val updated = notifications.map { it.copy(isRead = true) }
        prefs.edit().putString(KEY_NOTIFICATIONS, gson.toJson(updated)).apply()
        Log.d(TAG, "✅ All notifications marked as read")
    }

    fun getUnreadCount(): Int {
        return getNotifications().count { !it.isRead }
    }

    fun deleteNotification(notificationId: String) {
        val prefs = getPrefs()
        // ✅ FIX: Use array-based helper instead of anonymous TypeToken
        val notifications = jsonToMutableNotificationList(
            prefs.getString(KEY_NOTIFICATIONS, "[]") ?: "[]"
        )

        val updated = notifications.filter { it.id != notificationId }
        prefs.edit().putString(KEY_NOTIFICATIONS, gson.toJson(updated)).apply()
        Log.d(TAG, "🗑️ Notification deleted: $notificationId")
    }

    fun clearAllNotifications() {
        getPrefs().edit().putString(KEY_NOTIFICATIONS, "[]").apply()
        Log.d(TAG, "🗑️ All notifications cleared")
    }

    private fun getPrefs(): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}