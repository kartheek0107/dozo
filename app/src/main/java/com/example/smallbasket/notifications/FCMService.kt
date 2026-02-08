package com.example.smallbasket.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.smallbasket.R
import com.example.smallbasket.Homepage
import com.example.smallbasket.RequestActivity
import com.example.smallbasket.RequestDetailActivity
import com.example.smallbasket.MyLogsActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * PREMIUM FCM Service with:
 * - ✅ App logo as small icon (white monochrome)
 * - ✅ User name instead of email in notifications
 * - ✅ Sound and vibration patterns
 * - ✅ Type-specific large icons
 * - ✅ Action buttons
 * - ✅ Rich notification content
 */
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
        Log.d(TAG, "📋 Data: ${message.data}")
        Log.d(TAG, "📋 Notification: ${message.notification}")

        // Create notification channels first
        createNotificationChannels()

        // Extract notification data from RemoteMessage
        val data = message.data
        val type = data["type"] ?: "general"
        val title = data["title"] ?: message.notification?.title ?: "New Notification"
        val body = data["body"] ?: message.notification?.body ?: ""
        val orderId = data["order_id"] ?: data["request_id"]

        Log.d(TAG, "📋 Parsed - Type: $type, Title: $title, OrderID: $orderId")

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

            // Custom notification sound
            val soundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            // Channel 1: New Delivery Requests (HIGH priority)
            val channelNewRequests = NotificationChannel(
                CHANNEL_NEW_REQUESTS,
                "New Delivery Requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new delivery requests in your area"
                enableLights(true)
                lightColor = android.graphics.Color.parseColor("#14B8A6")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 200, 250)
                setSound(soundUri, audioAttributes)
                setShowBadge(true)
            }

            // Channel 2: Order Updates (DEFAULT priority)
            val channelOrderUpdates = NotificationChannel(
                CHANNEL_ORDER_UPDATES,
                "Order Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Updates about your orders (accepted, completed, cancelled)"
                enableLights(true)
                lightColor = android.graphics.Color.parseColor("#14B8A6")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 150, 200)
                setSound(soundUri, audioAttributes)
                setShowBadge(true)
            }

            // Channel 3: General Notifications (LOW priority)
            val channelGeneral = NotificationChannel(
                CHANNEL_GENERAL,
                "General Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "General app notifications and announcements"
                setShowBadge(false)
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
                Intent(this, RequestActivity::class.java).apply {
                    putExtra("REQUEST_ID", data.orderId)
                    putExtra("PICKUP_AREA", data.pickupArea)
                    putExtra("DROP_AREA", data.dropArea)
                    putExtra("REWARD", data.reward)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            "request_accepted", "request_completed", "request_cancelled" -> {
                Intent(this, MyLogsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            else -> {
                Intent(this, Homepage::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
        }

        // Create pending intent
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ✅ FIX 1: Always use simple white icon as small icon (notification bar)
        // This should be a simple white monochrome icon (Android requirement)
        val smallIconRes = R.drawable.ic_logo  // Make sure this is WHITE

        // Build the notification with premium features
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(smallIconRes)  // ✅ White app logo in status bar
            .setContentTitle(data.title)
            .setContentText(data.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(data.body))
            .setPriority(
                if (data.priority == "HIGH")
                    NotificationCompat.PRIORITY_HIGH
                else
                    NotificationCompat.PRIORITY_DEFAULT
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(android.graphics.Color.parseColor("#14B8A6"))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setGroup("SMALLBASKET_NOTIFICATIONS")

        // ✅ FIX 2: Add type-specific colored large icon (shows in notification drawer)
        try {
            val largeIconRes = when (data.type) {
                "new_request" -> R.drawable.ic_shopping_cart
                "request_accepted" -> R.drawable.ic_done
                "request_completed" -> R.drawable.ic_done_all
                "request_cancelled" -> R.drawable.ic_close
                else -> R.drawable.ic_logo
            }

            val largeIcon = android.graphics.BitmapFactory.decodeResource(
                resources,
                largeIconRes
            )
            notificationBuilder.setLargeIcon(largeIcon)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set large icon", e)
        }

        // Add action buttons for new requests
        if (data.type == "new_request" && data.orderId != null) {
            val viewIntent = Intent(this, RequestDetailActivity::class.java).apply {
                putExtra("order_id", data.orderId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val viewPendingIntent = PendingIntent.getActivity(
                this,
                (System.currentTimeMillis() + 1).toInt(),
                viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notificationBuilder.addAction(
                R.drawable.ic_arrow_forward,
                "View Details",
                viewPendingIntent
            )
        }

        // Add inbox style for multiple notifications
        if (data.type == "new_request") {
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(data.title)
                .addLine("📍 ${data.pickupArea ?: "Unknown"} → ${data.dropArea ?: "Unknown"}")
            if (data.reward != null) {
                inboxStyle.addLine("💰 Reward: ₹${data.reward}")
            }
            if (data.deadline != null) {
                inboxStyle.addLine("⏰ ${data.deadline}")
            }
            notificationBuilder.setStyle(inboxStyle)
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