package com.example.smallbasket.notifications

import com.google.gson.annotations.SerializedName

/**
 * Model for notification data from FCM
 */
data class NotificationData(
    @SerializedName("type") val type: String,
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String,
    @SerializedName("order_id") val orderId: String? = null,
    @SerializedName("priority") val priority: String? = null,
    @SerializedName("pickup_area") val pickupArea: String? = null,
    @SerializedName("drop_area") val dropArea: String? = null,
    @SerializedName("reward") val reward: String? = null,
    @SerializedName("deadline") val deadline: String? = null,
    @SerializedName("click_action") val clickAction: String? = null
) {
    companion object {
        const val TYPE_NEW_REQUEST = "new_request"
        const val TYPE_REQUEST_ACCEPTED = "request_accepted"
        const val TYPE_REQUEST_COMPLETED = "request_completed"
        const val TYPE_REQUEST_CANCELLED = "request_cancelled"
        const val TYPE_GENERAL = "general"
    }
}

/**
 * Saved notification for history
 */
data class SavedNotification(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val orderId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val priority: String? = null
)