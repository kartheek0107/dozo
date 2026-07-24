package com.smallbasket.dozo.notifications

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.smallbasket.dozo.databinding.ActivityNotificationBinding
import com.smallbasket.dozo.RequestDetailActivity

class NotificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationBinding
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationAdapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup transparent status bar BEFORE setContentView
        setupTransparentStatusBar()
        enableEdgeToEdge()

        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle window insets for edge-to-edge
        setupWindowInsets()

        notificationManager = NotificationManager.getInstance(this)

        setupUI()
        setupRecyclerView()
        loadNotifications()
    }

    /**
     * Setup fully transparent status bar with light icons
     */
    private fun setupTransparentStatusBar() {
        window.apply {
            // Make status bar fully transparent
            statusBarColor = Color.TRANSPARENT

            // Enable drawing behind status bar
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }

        // Set light status bar icons (white) for dark gradient background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11 and above
            window.insetsController?.setSystemBarsAppearance(
                0, // 0 = light icons (white), APPEARANCE_LIGHT_STATUS_BARS = dark icons
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // For Android 6 to 10
            @Suppress("DEPRECATION")
            var flags = window.decorView.systemUiVisibility
            // Remove light status bar flag to get white icons
            flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }
    }

    /**
     * Handle window insets for proper edge-to-edge layout
     */
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Apply top padding to header to account for status bar
            binding.headerLayout.setPadding(
                binding.headerLayout.paddingLeft,
                systemBars.top + resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height) / 3,
                binding.headerLayout.paddingRight,
                binding.headerLayout.paddingBottom
            )

            insets
        }
    }

    private fun setupUI() {
        // Back button with haptic feedback
        binding.btnBack.setOnClickListener {
            performHapticFeedback()
            finish()
        }

        // Mark all as read button with haptic feedback
        binding.btnMarkAllRead.setOnClickListener {
            performHapticFeedback()
            markAllNotificationsAsRead()
        }

        // Refresh button (in empty state)
        // FIX: Removed fragile try-catch — btnRefresh is a real binding view in the layout.
        // If it didn't exist, the crash would happen at inflate time, not here.
        binding.btnRefresh.setOnClickListener {
            performHapticFeedback()
            refreshNotifications()
        }
    }

    private fun setupRecyclerView() {
        binding.rvNotifications.apply {
            layoutManager = LinearLayoutManager(this@NotificationActivity)

            // FIX: Use proper dp-to-px conversion instead of misusing android.R.dimen.app_icon_size
            val spacingPx = (6 * resources.displayMetrics.density).toInt() // 6dp
            addItemDecoration(object : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: android.graphics.Rect,
                    view: View,
                    parent: androidx.recyclerview.widget.RecyclerView,
                    state: androidx.recyclerview.widget.RecyclerView.State
                ) {
                    outRect.bottom = spacingPx
                    outRect.top = spacingPx
                }
            })
        }
    }

    private fun loadNotifications() {
        val notifications = notificationManager.getSavedNotifications()
        val unreadCount = notifications.count { !it.isRead }

        // Update notification count with unread count only
        updateNotificationBadge(unreadCount)

        if (notifications.isEmpty()) {
            // FIX: Clear adapter data before showing empty state to prevent stale items
            // showing behind the empty state view when the last notification is deleted.
            if (::notificationAdapter.isInitialized) {
                notificationAdapter.updateNotifications(emptyList())
            }
            showEmptyState()
        } else {
            // Show notifications with smooth transition
            showNotificationsList(notifications.size, unreadCount)

            // Initialize adapter if not already done
            if (!::notificationAdapter.isInitialized) {
                notificationAdapter = NotificationAdapter(notifications) { notification ->
                    // FIX: Mark as read and navigate immediately.
                    // onResume() already calls loadNotifications() when returning from
                    // RequestDetailActivity, so there is no need to call it here before
                    // navigation — that caused a visible list flash/redraw + scroll reset.
                    performHapticFeedback()
                    notificationManager.markAsRead(notification.id)
                    navigateToNotification(notification)
                }
                binding.rvNotifications.adapter = notificationAdapter
            } else {
                // Update existing adapter
                notificationAdapter.updateNotifications(notifications)
            }
        }
    }

    /**
     * Update the notification badge to show only unread count.
     * Badge disappears when count is 0.
     */
    private fun updateNotificationBadge(unreadCount: Int) {
        try {
            if (unreadCount > 0) {
                binding.tvNotificationCount.visibility = View.VISIBLE

                // Add subtle pulse animation for new notifications
                binding.tvNotificationCount.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .setDuration(150)
                    .withEndAction {
                        binding.tvNotificationCount.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .start()
                    }
                    .start()
            } else {
                // Fade out badge when all notifications are read
                binding.tvNotificationCount.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(200)
                    .withEndAction {
                        binding.tvNotificationCount.visibility = View.GONE
                        binding.tvNotificationCount.alpha = 1f
                        binding.tvNotificationCount.scaleX = 1f
                        binding.tvNotificationCount.scaleY = 1f
                    }
                    .start()
            }
        } catch (e: Exception) {
            // Count badge might not exist in old layout
            Log.e("NotificationActivity", "Error updating badge", e)
        }
    }

    /**
     * Show empty state with elegant fade-in animation
     */
    private fun showEmptyState() {
        binding.tvEmptyState.apply {
            alpha = 0f
            visibility = View.VISIBLE
            translationY = 20f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .start()
        }

        binding.notificationsCard.apply {
            animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    visibility = View.GONE
                }
                .start()
        }

        try {
            binding.infoCard.visibility = View.VISIBLE
        } catch (e: Exception) {
            // Info card might not exist
        }
    }

    /**
     * Show notifications list with elegant fade-in animation
     */
    private fun showNotificationsList(totalCount: Int, unreadCount: Int) {
        binding.tvEmptyState.apply {
            animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    visibility = View.GONE
                }
                .start()
        }

        binding.notificationsCard.apply {
            alpha = 0f
            visibility = View.VISIBLE
            translationY = 20f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .start()
        }

        try {
            binding.infoCard.visibility = View.VISIBLE
        } catch (e: Exception) {
            // Info card might not exist
        }

        // Show informative toast with proper grammar
        val message = when {
            unreadCount == 0 -> "✓ All caught up!"
            unreadCount == 1 -> "You have 1 unread notification"
            else -> "You have $unreadCount unread notifications"
        }

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun markAllNotificationsAsRead() {
        val notifications = notificationManager.getSavedNotifications()
        val unreadCount = notifications.count { !it.isRead }

        if (unreadCount > 0) {
            notificationManager.markAllAsRead()

            // Show success message
            Toast.makeText(
                this,
                "✓ $unreadCount notification${if (unreadCount != 1) "s" else ""} marked as read",
                Toast.LENGTH_SHORT
            ).show()

            // Reload to update UI with smooth transition
            loadNotifications()
        } else {
            Toast.makeText(
                this,
                "All notifications are already read",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun refreshNotifications() {
        loadNotifications()
    }

    /**
     * FIX: Type-aware navigation.
     *
     * Previously all notification types called the same navigateToOrder(orderId?) which:
     *   1. Showed a confusing "No order associated" Toast for general notifications that
     *      never have an orderId by design.
     *   2. Called loadNotifications() BEFORE startActivity(), causing a visible list
     *      redraw/scroll-reset flash before navigation.
     *
     * Now each type routes to its correct destination and general notifications show
     * their message body as a brief info toast instead of an error.
     */
    private fun navigateToNotification(notification: SavedNotification) {
        performHapticFeedback()

        when (notification.type) {
            NotificationData.TYPE_NEW_REQUEST,
            NotificationData.TYPE_REQUEST_ACCEPTED,
            NotificationData.TYPE_REQUEST_COMPLETED,
            NotificationData.TYPE_REQUEST_CANCELLED -> {
                if (notification.orderId.isNullOrEmpty()) {
                    Toast.makeText(this, "No order associated with this notification", Toast.LENGTH_SHORT).show()
                    return
                }
                val intent = Intent(this, RequestDetailActivity::class.java)
                intent.putExtra("order_id", notification.orderId)
                Log.d("NotificationActivity", "Opening order: ${notification.orderId}")
                startActivity(intent)
            }
            else -> {
                // General / info notifications have no destination screen.
                // Show the body as a brief toast so the tap feels responsive.
                Toast.makeText(this, notification.body.ifBlank { notification.title }, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Provide subtle haptic feedback for better user experience
     */
    private fun performHapticFeedback() {
        try {
            binding.root.performHapticFeedback(
                android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        } catch (e: Exception) {
            // Haptic feedback not available
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload notifications when returning to this screen
        loadNotifications()
    }
}