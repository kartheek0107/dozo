package com.example.smallbasket.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.smallbasket.R
import com.example.smallbasket.Homepage
import com.example.smallbasket.RequestDetailActivity
import com.example.smallbasket.MyLogsActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"

        // FIX: Channel IDs suffixed with "_v2".
        // Android notification channel importance is WRITE-ONCE per device — once registered,
        // the OS ignores any importance upgrade on subsequent createNotificationChannel calls.
        // Changing the ID is the only way to get a fresh channel with the correct IMPORTANCE_HIGH
        // on devices that already had the old channels registered with lower importance.
        // These constants are the single source of truth: NotificationManager.createChannels()
        // registers them, and showNotification() here uses them — they must always match.
        const val CHANNEL_NEW_REQUESTS = "new_delivery_requests_v2"
        const val CHANNEL_ORDER_UPDATES = "order_updates_v2"
        const val CHANNEL_GENERAL = "general_notifications_v2"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "✅ New FCM Token: $token")
        com.example.smallbasket.notifications.NotificationManager.saveFCMToken(this, token)
        com.example.smallbasket.notifications.NotificationManager.initialize(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "📨 Message received from: ${message.from}")
        Log.d(TAG, "📋 Data: ${message.data}")
        Log.d(TAG, "📋 Notification: ${message.notification}")

        // Ensure channels exist
        com.example.smallbasket.notifications.NotificationManager
            .getInstance(this)
            .createChannels()

        val data = message.data
        val type = data["type"] ?: "general"
        val title = data["title"] ?: message.notification?.title ?: "New Notification"
        val body = data["body"] ?: message.notification?.body ?: ""
        val orderId = data["order_id"] ?: data["request_id"]

        Log.d(TAG, "📋 Parsed - Type: $type, Title: $title, OrderID: $orderId")

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

        com.example.smallbasket.notifications.NotificationManager.saveNotification(this, notificationData)
        showNotification(notificationData)
    }

    private fun showNotification(data: NotificationData) {
        val channelId = when (data.type) {
            "new_request" -> CHANNEL_NEW_REQUESTS
            "request_accepted", "request_completed", "request_cancelled" -> CHANNEL_ORDER_UPDATES
            else -> CHANNEL_GENERAL
        }

        // FIX: Consistent navigation — all order-related types go to RequestDetailActivity.
        // Previously new_request went to RequestActivity (list) while in-app went to detail.
        val intent = when (data.type) {
            "new_request" -> {
                if (!data.orderId.isNullOrEmpty()) {
                    Intent(this, RequestDetailActivity::class.java).apply {
                        putExtra("order_id", data.orderId)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                } else {
                    Intent(this, Homepage::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
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

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // FIX: setFullScreenIntent serves as a second guarantee for the heads-up banner.
        // On Android 8+, heads-up (peekaboo) requires BOTH channel IMPORTANCE_HIGH AND
        // builder PRIORITY_HIGH. setFullScreenIntent with a non-null intent acts as an
        // additional signal to the OS that this notification demands immediate attention,
        // which is the reliable way to ensure the banner appears.
        // We reuse the same pendingIntent — it won't actually launch full-screen unless
        // the device is locked; while unlocked it just reinforces the heads-up behaviour.
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            (System.currentTimeMillis() + 2).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(data.title)
            .setContentText(data.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(data.body))
            // FIX: Always PRIORITY_HIGH — both new_request AND order updates need to banner.
            // Previously order updates were PRIORITY_DEFAULT which silently prevented heads-up
            // even when the channel was IMPORTANCE_HIGH (both conditions must be met).
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, false) // false = not a full-screen takeover, just banner
            .setAutoCancel(true)
            .setColor(android.graphics.Color.parseColor("#14B8A6"))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // FIX: Removed .setDefaults(NotificationCompat.DEFAULT_ALL).
            // The channel already defines sound + vibration via setSound() and vibrationPattern.
            // DEFAULT_ALL on the builder interferes with channel-defined audio attributes on
            // Android 8+ and can reset the sound URI, which undermines heads-up triggering.
            .setGroup("SMALLBASKET_NOTIFICATIONS")

        // Type-specific large icon
        try {
            val largeIconRes = when (data.type) {
                "new_request" -> R.drawable.ic_shopping_cart
                "request_accepted" -> R.drawable.ic_done
                "request_completed" -> R.drawable.ic_done_all
                "request_cancelled" -> R.drawable.ic_close
                else -> R.drawable.ic_logo
            }
            val largeIcon = android.graphics.BitmapFactory.decodeResource(resources, largeIconRes)
            notificationBuilder.setLargeIcon(largeIcon)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set large icon", e)
        }

        // "View Details" action button for new requests
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
            notificationBuilder.addAction(R.drawable.ic_arrow_forward, "View Details", viewPendingIntent)
        }

        // Rich inbox-style layout for new requests
        if (data.type == "new_request") {
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(data.title)
                .addLine("📍 ${data.pickupArea ?: "Unknown"} → ${data.dropArea ?: "Unknown"}")
            if (data.reward != null) inboxStyle.addLine("💰 Reward: ₹${data.reward}")
            if (data.deadline != null) inboxStyle.addLine("⏰ ${data.deadline}")
            notificationBuilder.setStyle(inboxStyle)
        }

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