package com.example.smallbasket.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.smallbasket.R
import com.example.smallbasket.Homepage
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        const val CHANNEL_NEW_REQUESTS = "new_delivery_requests"
        const val CHANNEL_ORDER_UPDATES = "order_updates"
        const val CHANNEL_GENERAL = "general_notifications"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "✅ New FCM Token: $token")

        // Save token locally
        com.example.smallbasket.notifications.NotificationManager.saveFCMToken(this, token)

        // Try to register with backend (will retry if not logged in)
        com.example.smallbasket.notifications.NotificationManager.initialize(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "📨 Message received from: ${message.from}")

        // Create notification channels first
        createNotificationChannels()

        // Extract notification data from RemoteMessage
        val data = message.data
        val type = data["type"] ?: "general"
        val title = data["title"] ?: message.notification?.title ?: "New Notification"
        val body = data["body"] ?: message.notification?.body ?: ""
        val orderId = data["order_id"] ?: data["request_id"]

        Log.d(TAG, "📋 Notification - Type: $type, Title: $title")

        // Create notification data object
        val notificationData = NotificationData(
            type = type,
            title = title,
            body = body,
            orderId = orderId,
            priority = if (type == "new_request") "HIGH" else "DEFAULT",
            pickupArea = data["pickup_area"],
            dropArea = data["drop_area"],
            reward = data["reward"],
            deadline = data["deadline"]
        )

        // Save to notification history
        com.example.smallbasket.notifications.NotificationManager.saveNotification(this, notificationData)

        // Show system notification
        showNotification(notificationData)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Channel 1: New Delivery Requests (HIGH priority)
            val channelNewRequests = NotificationChannel(
                CHANNEL_NEW_REQUESTS,
                "New Delivery Requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new delivery requests in your area"
                enableLights(true)
                lightColor = android.graphics.Color.parseColor("#009688")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }

            // Channel 2: Order Updates (DEFAULT priority)
            val channelOrderUpdates = NotificationChannel(
                CHANNEL_ORDER_UPDATES,
                "Order Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Updates about your orders (accepted, completed, cancelled)"
                enableLights(true)
                lightColor = android.graphics.Color.parseColor("#009688")
                enableVibration(true)
            }

            // Channel 3: General Notifications (LOW priority)
            val channelGeneral = NotificationChannel(
                CHANNEL_GENERAL,
                "General Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "General app notifications and announcements"
            }

            // Register all channels
            notificationManager.createNotificationChannel(channelNewRequests)
            notificationManager.createNotificationChannel(channelOrderUpdates)
            notificationManager.createNotificationChannel(channelGeneral)

            Log.d(TAG, "✅ Notification channels created")
        }
    }

    private fun showNotification(data: NotificationData) {
        // Determine which channel to use based on notification type
        val channelId = when (data.type) {
            "new_request" -> CHANNEL_NEW_REQUESTS
            "request_accepted", "request_completed", "request_cancelled" -> CHANNEL_ORDER_UPDATES
            else -> CHANNEL_GENERAL
        }

        // Create the appropriate intent based on notification type
        val intent = when (data.type) {
            "new_request" -> {
                // For now, open Homepage. Update this to RequestDetailActivity when available
                Intent(this, Homepage::class.java).apply {
                    putExtra("REQUEST_ID", data.orderId)
                    putExtra("PICKUP_AREA", data.pickupArea)
                    putExtra("DROP_AREA", data.dropArea)
                    putExtra("REWARD", data.reward)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            else -> {
                // Open Homepage for other notifications
                Intent(this, Homepage::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
        }

        // Create pending intent
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(), // Unique request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification) // Your notification icon
            .setContentTitle(data.title)
            .setContentText(data.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(data.body)) // Expandable text
            .setPriority(
                if (data.priority == "HIGH")
                    NotificationCompat.PRIORITY_HIGH
                else
                    NotificationCompat.PRIORITY_DEFAULT
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Dismiss when tapped
            .setColor(android.graphics.Color.parseColor("#009688")) // Teal color

        // Add sound and vibration for high priority notifications
        if (data.priority == "HIGH") {
            notificationBuilder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        // Show the notification
        try {
            val notificationManager = NotificationManagerCompat.from(this)
            val notificationId = System.currentTimeMillis().toInt()
            notificationManager.notify(notificationId, notificationBuilder.build())

            Log.d(TAG, "✅ Notification shown: ${data.title}")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Permission denied to show notification", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing notification", e)
        }
    }
}