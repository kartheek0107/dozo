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
import com.google.gson.reflect.TypeToken
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

        /**
         * Save FCM token to SharedPreferences (static method for FCMService)
         */
        fun saveFCMToken(context: Context, token: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
            Log.d(TAG, "💾 FCM token saved locally: ${token.take(20)}...")
        }

        /**
         * Save a notification to history (static method for FCMService)
         */
        fun saveNotification(context: Context, notification: NotificationData) {
            getInstance(context).saveNotificationToHistory(notification)
        }

        /**
         * Initialize FCM (static method for FCMService)
         */
        fun initialize(context: Context) {
            getInstance(context).initialize()
        }
    }

    private val gson = Gson()
    private val api = RetrofitClient.apiService

    /**
     * Create all notification channels.
     * Must be called at app startup (in Application.onCreate) so channels exist
     * before any FCM message arrives — otherwise Android 8+ silently drops
     * background notifications even when FCM reports success.
     */
    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            // Channel 1: New Delivery Requests (HIGH priority)
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
            }

            // Channel 2: Order Updates (DEFAULT priority)
            val channelOrderUpdates = NotificationChannel(
                FCMService.CHANNEL_ORDER_UPDATES,
                "Order Updates",
                AndroidNotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Updates about your orders (accepted, completed, cancelled)"
                enableLights(true)
                lightColor = Color.parseColor("#14B8A6")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 150, 200)
                setSound(soundUri, audioAttributes)
                setShowBadge(true)
            }

            // Channel 3: General Notifications (LOW priority)
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

            Log.d(TAG, "✅ Notification channels created")
        }
    }

    /**
     * Initialize FCM and register token with backend.
     * Call this after successful login.
     */
    fun initialize() {
        Log.d(TAG, "=== Initializing FCM ===")

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "❌ Failed to get FCM token", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG, "✅ FCM Token retrieved: ${token.take(20)}...")

            // Save token locally
            saveFCMToken(context, token)

            // Register with backend
            registerTokenWithBackend(token)
        }
    }

    /**
     * Get saved FCM token
     */
    fun getFCMToken(): String? {
        return getPrefs().getString(KEY_FCM_TOKEN, null)
    }

    /**
     * ✅ FIXED: Register FCM token with backend using RetrofitClient
     */
    private fun registerTokenWithBackend(token: String) {
        // Get Firebase ID token
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
                    val body = response.body()
                    Log.d(TAG, "✅ FCM token registered with backend successfully")
                    Log.d(TAG, "  Response: ${body?.message}")
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "❌ Backend registration failed: ${response.code()}")
                    Log.e(TAG, "  Error: $errorBody")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception registering token with backend", e)
            }
        }
    }

    /**
     * ✅ FIXED: Unregister FCM token from backend using RetrofitClient
     */
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
                // Clear local data regardless of backend result
                clearLocalData()
            }
        }
    }

    /**
     * Clear all local notification data
     */
    private fun clearLocalData() {
        val prefs = getPrefs()
        prefs.edit().clear().apply()
        Log.d(TAG, "🗑️ Local notification data cleared")
    }

    /**
     * Save a notification to history.
     *
     * FIX: Notification ID now uses UUID.randomUUID() instead of
     * System.currentTimeMillis().toString(). Back-to-back FCM messages
     * arriving within the same millisecond would previously get the same ID,
     * causing one to silently overwrite the other in SharedPreferences.
     */
    fun saveNotificationToHistory(notification: NotificationData) {
        val prefs = getPrefs()
        val notificationsJson = prefs.getString(KEY_NOTIFICATIONS, "[]")
        val type = object : TypeToken<MutableList<SavedNotification>>() {}.type
        val notifications = gson.fromJson<MutableList<SavedNotification>>(notificationsJson, type)

        // FIX: Use UUID to guarantee uniqueness even under rapid fire messages
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
        notifications.add(0, savedNotification) // Add to beginning

        Log.d(TAG, "💾 Saving notification: ${notification.title}")
        Log.d(TAG, "  Type: ${notification.type}")
        Log.d(TAG, "  Order ID: ${notification.orderId}")

        // Keep only last MAX_NOTIFICATIONS
        val trimmedNotifications = if (notifications.size > MAX_NOTIFICATIONS) {
            notifications.take(MAX_NOTIFICATIONS).toMutableList()
        } else {
            notifications
        }

        // Save back
        val updatedJson = gson.toJson(trimmedNotifications)
        prefs.edit().putString(KEY_NOTIFICATIONS, updatedJson).apply()

        Log.d(TAG, "💾 Notification saved to history (total: ${trimmedNotifications.size})")
    }

    /**
     * Get all saved notifications
     */
    fun getNotifications(): List<SavedNotification> {
        val prefs = getPrefs()
        val notificationsJson = prefs.getString(KEY_NOTIFICATIONS, "[]")
        val type = object : TypeToken<List<SavedNotification>>() {}.type
        return gson.fromJson(notificationsJson, type) ?: emptyList()
    }

    /**
     * Get all saved notifications (alias for backward compatibility)
     */
    fun getSavedNotifications(): List<SavedNotification> {
        return getNotifications()
    }

    /**
     * Mark a notification as read
     */
    fun markAsRead(notificationId: String) {
        val prefs = getPrefs()
        val notificationsJson = prefs.getString(KEY_NOTIFICATIONS, "[]")
        val type = object : TypeToken<MutableList<SavedNotification>>() {}.type
        val notifications = gson.fromJson<MutableList<SavedNotification>>(notificationsJson, type)

        // Find and update
        val updated = notifications.map {
            if (it.id == notificationId) it.copy(isRead = true) else it
        }

        // Save back
        val updatedJson = gson.toJson(updated)
        prefs.edit().putString(KEY_NOTIFICATIONS, updatedJson).apply()

        Log.d(TAG, "✅ Notification marked as read: $notificationId")
    }

    /**
     * Mark all notifications as read
     */
    fun markAllAsRead() {
        val prefs = getPrefs()
        val notificationsJson = prefs.getString(KEY_NOTIFICATIONS, "[]")
        val type = object : TypeToken<MutableList<SavedNotification>>() {}.type
        val notifications = gson.fromJson<MutableList<SavedNotification>>(notificationsJson, type)

        // Mark all as read
        val updated = notifications.map { it.copy(isRead = true) }

        // Save back
        val updatedJson = gson.toJson(updated)
        prefs.edit().putString(KEY_NOTIFICATIONS, updatedJson).apply()

        Log.d(TAG, "✅ All notifications marked as read")
    }

    /**
     * Get count of unread notifications
     */
    fun getUnreadCount(): Int {
        val notifications = getNotifications()
        return notifications.count { !it.isRead }
    }

    /**
     * Delete a notification
     */
    fun deleteNotification(notificationId: String) {
        val prefs = getPrefs()
        val notificationsJson = prefs.getString(KEY_NOTIFICATIONS, "[]")
        val type = object : TypeToken<MutableList<SavedNotification>>() {}.type
        val notifications = gson.fromJson<MutableList<SavedNotification>>(notificationsJson, type)

        // Remove notification
        val updated = notifications.filter { it.id != notificationId }

        // Save back
        val updatedJson = gson.toJson(updated)
        prefs.edit().putString(KEY_NOTIFICATIONS, updatedJson).apply()

        Log.d(TAG, "🗑️ Notification deleted: $notificationId")
    }

    /**
     * Clear all notifications
     */
    fun clearAllNotifications() {
        val prefs = getPrefs()
        prefs.edit().putString(KEY_NOTIFICATIONS, "[]").apply()
        Log.d(TAG, "🗑️ All notifications cleared")
    }

    private fun getPrefs(): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}