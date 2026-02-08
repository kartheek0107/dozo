package com.example.smallbasket.notifications

import android.text.format.DateUtils
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.smallbasket.R
import java.util.concurrent.TimeUnit

class NotificationAdapter(
    private var notifications: List<SavedNotification>,
    private val onItemClick: (SavedNotification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    inner class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView as CardView
        val iconContainer: CardView = itemView.findViewById(R.id.iconContainer)
        val ivNotificationIcon: ImageView = itemView.findViewById(R.id.ivNotificationIcon)
        val tvNotificationTitle: TextView = itemView.findViewById(R.id.tvNotificationTitle)
        val tvNotificationMessage: TextView = itemView.findViewById(R.id.tvNotificationMessage)
        val tvNotificationTime: TextView = itemView.findViewById(R.id.tvNotificationTime)
        val unreadDot: View = itemView.findViewById(R.id.unreadDot)
        val categoryBadge: CardView = itemView.findViewById(R.id.categoryBadge)
        val categoryText: TextView = itemView.findViewById(R.id.tvNotificationCategory)
        val actionButton: ImageButton? = try {
            itemView.findViewById(R.id.btnNotificationAction)
        } catch (e: Exception) {
            null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]

        // Set title and message
        holder.tvNotificationTitle.text = notification.title
        holder.tvNotificationMessage.text = notification.body

        // Set enhanced relative time
        holder.tvNotificationTime.text = getEnhancedRelativeTime(notification.timestamp)

        // Set icon and colors based on type
        val style = getNotificationStyle(notification.type)

        holder.ivNotificationIcon.setImageResource(style.iconRes)
        holder.ivNotificationIcon.setColorFilter(
            ContextCompat.getColor(holder.itemView.context, style.iconTint)
        )
        holder.iconContainer.setCardBackgroundColor(
            ContextCompat.getColor(holder.itemView.context, style.bgTint)
        )

        // Set category badge
        holder.categoryText.text = style.category
        holder.categoryBadge.setCardBackgroundColor(
            ContextCompat.getColor(holder.itemView.context, style.categoryBg)
        )
        holder.categoryText.setTextColor(
            ContextCompat.getColor(holder.itemView.context, style.categoryText)
        )

        // Show/hide unread indicator with smooth transition
        if (notification.isRead) {
            holder.unreadDot.animate()
                .alpha(0f)
                .scaleX(0.5f)
                .scaleY(0.5f)
                .setDuration(200)
                .withEndAction {
                    holder.unreadDot.visibility = View.GONE
                    holder.unreadDot.alpha = 1f
                    holder.unreadDot.scaleX = 1f
                    holder.unreadDot.scaleY = 1f
                }
                .start()
        } else {
            holder.unreadDot.visibility = View.VISIBLE
            // Add subtle pulse animation for unread
            holder.unreadDot.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(300)
                .withEndAction {
                    holder.unreadDot.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(300)
                        .start()
                }
                .start()
        }

        // Enhanced visual feedback for read/unread
        if (notification.isRead) {
            holder.cardView.setCardBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.gray_50)
            )
            holder.tvNotificationTitle.alpha = 0.7f
            holder.tvNotificationMessage.alpha = 0.7f
            holder.tvNotificationTime.alpha = 0.6f
        } else {
            holder.cardView.setCardBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, android.R.color.white)
            )
            holder.tvNotificationTitle.alpha = 1f
            holder.tvNotificationMessage.alpha = 1f
            holder.tvNotificationTime.alpha = 0.8f
        }

        // Set click listeners with haptic feedback
        holder.itemView.setOnClickListener {
            performHapticFeedback(it)

            // Add ripple effect animation
            it.animate()
                .scaleX(0.98f)
                .scaleY(0.98f)
                .setDuration(100)
                .withEndAction {
                    it.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()

            onItemClick(notification)
        }

        // Action button click
        holder.actionButton?.setOnClickListener {
            performHapticFeedback(it)
            onItemClick(notification)
        }

        // Add smooth entrance animation
        holder.itemView.alpha = 0f
        holder.itemView.translationY = 30f
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setStartDelay((position * 50L).coerceAtMost(300))
            .start()
    }

    override fun getItemCount(): Int = notifications.size

    /**
     * Update notifications with smooth DiffUtil animations
     */
    fun updateNotifications(newNotifications: List<SavedNotification>) {
        val diffCallback = NotificationDiffCallback(notifications, newNotifications)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        notifications = newNotifications
        diffResult.dispatchUpdatesTo(this)
    }

    /**
     * Get enhanced relative time with better formatting
     */
    private fun getEnhancedRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.MINUTES.toMillis(60) -> {
                val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
                "${mins}m ago"
            }
            diff < TimeUnit.HOURS.toMillis(24) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                "${hours}h ago"
            }
            diff < TimeUnit.DAYS.toMillis(7) -> {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                "${days}d ago"
            }
            else -> {
                // Fallback to standard DateUtils for older dates
                DateUtils.getRelativeTimeSpanString(
                    timestamp,
                    now,
                    DateUtils.DAY_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString()
            }
        }
    }

    /**
     * Get notification styling based on type
     */
    private fun getNotificationStyle(type: String): NotificationStyle {
        return when (type) {
            NotificationData.TYPE_NEW_REQUEST -> NotificationStyle(
                iconRes = R.drawable.ic_shopping_cart,
                iconTint = R.color.teal_600,
                bgTint = R.color.teal_100,
                category = "New Order",
                categoryBg = R.color.teal_50,
                categoryText = R.color.teal_700
            )
            NotificationData.TYPE_REQUEST_ACCEPTED -> NotificationStyle(
                iconRes = R.drawable.ic_done,
                iconTint = R.color.green_600,
                bgTint = R.color.green_100,
                category = "Accepted",
                categoryBg = R.color.green_50,
                categoryText = R.color.green_700
            )
            NotificationData.TYPE_REQUEST_COMPLETED -> NotificationStyle(
                iconRes = R.drawable.ic_done_all,
                iconTint = R.color.green_700,
                bgTint = R.color.green_100,
                category = "Completed",
                categoryBg = R.color.green_50,
                categoryText = R.color.green_800
            )
            NotificationData.TYPE_REQUEST_CANCELLED -> NotificationStyle(
                iconRes = R.drawable.ic_close,
                iconTint = R.color.red_500,
                bgTint = R.color.red_100,
                category = "Cancelled",
                categoryBg = R.color.red_50,
                categoryText = R.color.red_700
            )
            else -> NotificationStyle(
                iconRes = R.drawable.ic_logo,
                iconTint = R.color.gray_600,
                bgTint = R.color.gray_100,
                category = "Info",
                categoryBg = R.color.gray_50,
                categoryText = R.color.gray_700
            )
        }
    }

    /**
     * Provide haptic feedback for better UX
     */
    private fun performHapticFeedback(view: View) {
        try {
            view.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        } catch (e: Exception) {
            // Haptic feedback not available
        }
    }

    /**
     * DiffUtil callback for efficient updates
     */
    private class NotificationDiffCallback(
        private val oldList: List<SavedNotification>,
        private val newList: List<SavedNotification>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.isRead == newItem.isRead &&
                    oldItem.title == newItem.title &&
                    oldItem.body == newItem.body &&
                    oldItem.timestamp == newItem.timestamp
        }
    }

    /**
     * Data class to hold notification styling
     * Note: data classes automatically generate componentN() functions
     */
    private data class NotificationStyle(
        val iconRes: Int,
        val iconTint: Int,
        val bgTint: Int,
        val category: String,
        val categoryBg: Int,
        val categoryText: Int
    )
    // ✅ REMOVED all manual operator fun componentN() declarations
    // They are automatically provided by the 'data' class keyword
}