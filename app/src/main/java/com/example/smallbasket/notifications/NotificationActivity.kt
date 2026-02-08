package com.example.smallbasket.notifications

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
import com.example.smallbasket.databinding.ActivityNotificationBinding
import com.example.smallbasket.RequestDetailActivity

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
        try {
            binding.btnRefresh.setOnClickListener {
                performHapticFeedback()
                refreshNotifications()
            }
        } catch (e: Exception) {
            // btnRefresh might not exist if not using new layout
        }
    }

    private fun setupRecyclerView() {
        binding.rvNotifications.apply {
            layoutManager = LinearLayoutManager(this@NotificationActivity)
            // Add item spacing
            val spacing = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 4
            addItemDecoration(object : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: android.graphics.Rect,
                    view: View,
                    parent: androidx.recyclerview.widget.RecyclerView,
                    state: androidx.recyclerview.widget.RecyclerView.State
                ) {
                    outRect.bottom = spacing / 2
                    outRect.top = spacing / 2
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
            // Show empty state with smooth transition
            showEmptyState()
        } else {
            // Show notifications with smooth transition
            showNotificationsList(notifications.size, unreadCount)

            // Initialize adapter if not already done
            if (!::notificationAdapter.isInitialized) {
                notificationAdapter = NotificationAdapter(notifications) { notification ->
                    // Handle notification click
                    performHapticFeedback()
                    notificationManager.markAsRead(notification.id)
                    loadNotifications() // Refresh to update badge
                    navigateToOrder(notification.orderId)
                }
                binding.rvNotifications.adapter = notificationAdapter
            } else {
                // Update existing adapter
                notificationAdapter.updateNotifications(notifications)
            }
        }
    }

    /**
     * Update the notification badge to show only unread count
     * Badge disappears when count is 0
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
            unreadCount == 0 -> "✓ All caught up! No unread notifications"
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
        // Show refresh animation
        try {
            binding.btnRefresh.animate()
                .rotation(360f)
                .setDuration(500)
                .withEndAction {
                    binding.btnRefresh.rotation = 0f
                    loadNotifications()
                }
                .start()
        } catch (e: Exception) {
            loadNotifications()
        }

        Toast.makeText(this, "Refreshing notifications...", Toast.LENGTH_SHORT).show()
    }

    /**
     * ✅ FIXED: Navigate to RequestDetailActivity with order_id
     * RequestDetailActivity will fetch order details from backend
     */
    private fun navigateToOrder(orderId: String?) {
        if (orderId.isNullOrEmpty()) {
            Toast.makeText(this, "No order associated with this notification", Toast.LENGTH_SHORT).show()
            return
        }

        performHapticFeedback()

        // Navigate to RequestDetailActivity with just the order_id
        val intent = Intent(this, RequestDetailActivity::class.java)
        intent.putExtra("order_id", orderId)

        Log.d("NotificationActivity", "Opening order: $orderId")
        startActivity(intent)
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