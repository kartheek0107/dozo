package com.example.smallbasket.notifications

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smallbasket.databinding.ActivityNotificationBinding

class NotificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationBinding
    private lateinit var notificationManager: NotificationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup transparent status bar BEFORE setContentView
        setupStatusBar()
        enableEdgeToEdge()

        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        notificationManager = NotificationManager.getInstance(this)

        setupUI()
        setupRecyclerView()
        loadNotifications()
    }

    /**
     * Setup transparent status bar matching Homepage design
     */
    private fun setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.apply {
                statusBarColor = Color.TRANSPARENT
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        )
            }
        }

        // Make status bar icons dark/light based on background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0, // Dark icons for light background, 0 for light icons on dark background
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }

    private fun setupUI() {
        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Mark all as read button
        binding.btnMarkAllRead.setOnClickListener {
            markAllNotificationsAsRead()
        }

        // Refresh button (in empty state)
        try {
            binding.btnRefresh.setOnClickListener {
                refreshNotifications()
            }
        } catch (e: Exception) {
            // btnRefresh might not exist if not using new layout
        }
    }

    private fun setupRecyclerView() {
        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
    }

    private fun loadNotifications() {
        val notifications = notificationManager.getSavedNotifications()

        // Update notification count
        try {
            binding.tvNotificationCount.text = notifications.size.toString()
        } catch (e: Exception) {
            // Count badge might not exist in old layout
        }

        if (notifications.isEmpty()) {
            // Show empty state
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.notificationsCard.visibility = View.GONE
            try {
                binding.infoCard.visibility = View.GONE
            } catch (e: Exception) {
                // Info card might not exist
            }
        } else {
            // Show notifications
            binding.tvEmptyState.visibility = View.GONE
            binding.notificationsCard.visibility = View.VISIBLE
            try {
                binding.infoCard.visibility = View.VISIBLE
            } catch (e: Exception) {
                // Info card might not exist
            }

            // TODO: Create NotificationAdapter when ready
            // binding.rvNotifications.adapter = NotificationAdapter(notifications) { notification ->
            //     // Handle notification click
            //     notificationManager.markAsRead(notification.id)
            //     navigateToOrder(notification.orderId)
            // }

            // For now, show a toast with notification count
            Toast.makeText(
                this,
                "You have ${notifications.size} notification(s)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun markAllNotificationsAsRead() {
        notificationManager.markAllAsRead()
        Toast.makeText(this, "All notifications marked as read", Toast.LENGTH_SHORT).show()

        // Reload to update UI
        loadNotifications()
    }

    private fun refreshNotifications() {
        loadNotifications()
        Toast.makeText(this, "Notifications refreshed", Toast.LENGTH_SHORT).show()
    }

    private fun navigateToOrder(orderId: String?) {
        if (orderId != null) {
            // TODO: Navigate to order detail activity
            Toast.makeText(this, "Opening order: $orderId", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload notifications when returning to this screen
        loadNotifications()
    }
}