package com.example.smallbasket.notifications

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NotificationManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "NotificationManager"
        private const val PREFS_NAME = "notification_prefs"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_NOTIFICATIONS = "saved_notifications"
        private const val MAX_NOTIFICATIONS = 100

        // Your backend URL - UPDATE THIS!
        private const val BACKEND_URL = "http://10.0.2.2:8000" // For emulator, use your actual backend URL

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
            Log.d(TAG, "💾 FCM token saved locally")
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
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Initialize FCM and register token with backend
     * Call this after successful login
     */
    fun initialize() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "❌ Failed to get FCM token", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG, "✅ FCM Token retrieved: $token")

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
     * Register FCM token with backend
     */
    private fun registerTokenWithBackend(token: String) {
        // Get Firebase ID token
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w(TAG, "⚠️ User not logged in, skipping backend registration")
            return
        }

        currentUser.getIdToken(false).addOnSuccessListener { result ->
            val idToken = result.token ?: return@addOnSuccessListener

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val json = JSONObject().apply {
                        put("fcm_token", token)
                    }

                    val requestBody = json.toString()
                        .toRequestBody("application/json".toMediaType())

                    val request = Request.Builder()
                        .url("$BACKEND_URL/notifications/register")
                        .addHeader("Authorization", "Bearer $idToken")
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        Log.d(TAG, "✅ FCM token registered with backend")
                    } else {
                        Log.e(TAG, "❌ Backend registration failed: ${response.code}")
                    }

                    response.close()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error registering token with backend", e)
                }
            }
        }
    }

    /**
     * Unregister FCM token from backend (call on logout)
     */
    fun unregisterToken() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w(TAG, "⚠️ User not logged in, skipping unregister")
            clearLocalData()
            return
        }

        currentUser.getIdToken(false).addOnSuccessListener { result ->
            val idToken = result.token ?: return@addOnSuccessListener

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val request = Request.Builder()
                        .url("$BACKEND_URL/notifications/unregister")
                        .addHeader("Authorization", "Bearer $idToken")
                        .delete()
                        .build()

                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        Log.d(TAG, "✅ FCM token unregistered from backend")
                    } else {
                        Log.e(TAG, "❌ Backend unregister failed: ${response.code}")
                    }

                    response.close()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error unregistering token", e)
                }

                // Clear local data regardless of backend result
                clearLocalData()
            }
        }.addOnFailureListener {
            // If getting token fails, still clear local data
            clearLocalData()
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
     * Save a notification to history
     */
    fun saveNotificationToHistory(notification: NotificationData) {
        val prefs = getPrefs()
        val notificationsJson = prefs.getString(KEY_NOTIFICATIONS, "[]")
        val type = object : TypeToken<MutableList<SavedNotification>>() {}.type
        val notifications = gson.fromJson<MutableList<SavedNotification>>(notificationsJson, type)

        // Add new notification
        val savedNotification = SavedNotification(
            id = System.currentTimeMillis().toString(),
            type = notification.type,
            title = notification.title,
            body = notification.body,
            orderId = notification.orderId,
            timestamp = System.currentTimeMillis(),
            isRead = false,
            priority = notification.priority
        )
        notifications.add(0, savedNotification) // Add to beginning

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