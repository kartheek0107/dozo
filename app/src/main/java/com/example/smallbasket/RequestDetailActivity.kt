package com.example.smallbasket

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class RequestDetailActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Make status bar transparent
        window.apply {
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            statusBarColor = android.graphics.Color.TRANSPARENT
            navigationBarColor = android.graphics.Color.TRANSPARENT
        }

        setContentView(R.layout.activity_request_details)

        // Get all data from intent
        val orderId = intent.getStringExtra("order_id") ?: ""
        val title = intent.getStringExtra("title") ?: ""
        val pickup = intent.getStringExtra("pickup") ?: ""
        val drop = intent.getStringExtra("drop") ?: ""
        val details = intent.getStringExtra("details") ?: ""
        val priority = intent.getStringExtra("priority") ?: "normal"
        val bestBefore = intent.getStringExtra("best_before")
        val deadline = intent.getStringExtra("deadline")
        val rewardPercentage = intent.getDoubleExtra("reward_percentage", 0.0)
        val isImportant = intent.getBooleanExtra("isImportant", false)
        val fee = intent.getStringExtra("fee") ?: "₹0"
        val time = intent.getStringExtra("time") ?: ""
        val pickupArea = intent.getStringExtra("pickup_area")
        val dropArea = intent.getStringExtra("drop_area")
        val itemPrice = intent.getDoubleExtra("item_price", 0.0)
        val status = intent.getStringExtra("status") ?: "open"

        // Requester (Poster) Info
        val requesterEmail = intent.getStringExtra("requester_email")
        val requesterName = intent.getStringExtra("requester_name")
        val requesterPhone = intent.getStringExtra("requester_phone")

        // Acceptor Info
        val acceptorEmail = intent.getStringExtra("acceptor_email")
        val acceptorName = intent.getStringExtra("acceptor_name")
        val acceptorPhone = intent.getStringExtra("acceptor_phone")

        // Initialize views
        initializeViews(
            title, pickup, drop, details, priority, bestBefore,
            deadline, fee, isImportant, itemPrice, status
        )

        // Populate requester and acceptor cards
        populateRequesterInfo(requesterName, requesterEmail, requesterPhone)
        populateAcceptorInfo(acceptorEmail, acceptorName, acceptorPhone, status)

        // Setup back button
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            onBackPressed()
        }

        // Setup accept button (if status is "open")
        setupAcceptButton(orderId, status)
    }

    private fun initializeViews(
        title: String,
        pickup: String,
        drop: String,
        details: String,
        priority: String,
        bestBefore: String?,
        deadline: String?,
        fee: String,
        isImportant: Boolean,
        itemPrice: Double,
        status: String
    ) {
        // Set title
        findViewById<TextView>(R.id.tvItemTitle).text = title

        // Set priority badge visibility
        val layoutPriority = findViewById<LinearLayout>(R.id.layoutPriority)
        layoutPriority.visibility = if (isImportant) View.VISIBLE else View.GONE

        // Set pickup and drop locations
        findViewById<TextView>(R.id.tvPickup).text = pickup
        findViewById<TextView>(R.id.tvDrop).text = drop

        // Set deadline time with proper formatting
        val layoutBestBefore = findViewById<LinearLayout>(R.id.layoutBestBefore)
        if (!deadline.isNullOrEmpty()) {
            val timeRemaining = com.example.smallbasket.utils.TimeUtils.getTimeRemaining(deadline)
            findViewById<TextView>(R.id.tvBestBefore).text = timeRemaining
            layoutBestBefore.visibility = View.VISIBLE
        } else {
            layoutBestBefore.visibility = View.GONE
        }

        // Set item price
        val layoutItemPrice = findViewById<LinearLayout>(R.id.layoutItemPrice)
        if (itemPrice > 0) {
            findViewById<TextView>(R.id.tvItemPrice).text = "₹$itemPrice"
            layoutItemPrice.visibility = View.VISIBLE
        } else {
            layoutItemPrice.visibility = View.GONE
        }

        // Set delivery fee
        findViewById<TextView>(R.id.tvReward).text = fee

        // Set notes
        val cardNotes = findViewById<CardView>(R.id.cardNotes)
        if (details.isNotEmpty()) {
            findViewById<TextView>(R.id.tvNotes).text = details
            cardNotes.visibility = View.VISIBLE
        } else {
            cardNotes.visibility = View.GONE
        }
    }

    private fun populateRequesterInfo(
        requesterName: String?,
        requesterEmail: String?,
        requesterPhone: String?
    ) {
        // Get the included requester card
        val requesterCard = findViewById<View>(R.id.includeRequesterInfo)
        val tvRequesterName = requesterCard.findViewById<TextView>(R.id.tvRequesterName)
        val tvRequesterEmail = requesterCard.findViewById<TextView>(R.id.tvRequesterEmail)
        val tvRequesterPhone = requesterCard.findViewById<TextView>(R.id.tvRequesterPhone)
        val layoutRequesterPhone = requesterCard.findViewById<LinearLayout>(R.id.layoutRequesterPhone)

        // Populate requester data
        tvRequesterName.text = requesterName ?: "Unknown"
        tvRequesterEmail.text = requesterEmail ?: "N/A"

        // Show or hide phone number based on availability
        if (!requesterPhone.isNullOrEmpty()) {
            tvRequesterPhone.text = requesterPhone
            layoutRequesterPhone.visibility = View.VISIBLE
        } else {
            layoutRequesterPhone.visibility = View.GONE
        }
    }

    private fun populateAcceptorInfo(
        acceptorEmail: String?,
        acceptorName: String?,
        acceptorPhone: String?,
        status: String
    ) {
        // Get the acceptor card
        val acceptorCard = findViewById<View>(R.id.includeAcceptorInfo)

        // Only show acceptor card if the request has been accepted
        // Status could be "accepted", "in_progress", "completed", etc. (anything except "open")
        if (!acceptorEmail.isNullOrEmpty() && status != "open") {
            acceptorCard.visibility = View.VISIBLE

            val tvAcceptorName = acceptorCard.findViewById<TextView>(R.id.tvAcceptorName)
            val tvAcceptorEmail = acceptorCard.findViewById<TextView>(R.id.tvAcceptorEmail)
            val tvAcceptorPhone = acceptorCard.findViewById<TextView>(R.id.tvAcceptorPhone)
            val layoutAcceptorPhone = acceptorCard.findViewById<LinearLayout>(R.id.layoutAcceptorPhone)
            val tvAcceptorStatus = acceptorCard.findViewById<TextView>(R.id.tvAcceptorStatus)

            // Set acceptor name and email
            tvAcceptorName.text = acceptorName ?: "Unknown"
            tvAcceptorEmail.text = acceptorEmail

            // Show or hide phone number based on availability
            if (!acceptorPhone.isNullOrEmpty()) {
                tvAcceptorPhone.text = acceptorPhone
                layoutAcceptorPhone.visibility = View.VISIBLE
            } else {
                layoutAcceptorPhone.visibility = View.GONE
            }

            // Update status badge based on current status
            when (status.lowercase()) {
                "accepted" -> {
                    tvAcceptorStatus.text = "Accepted"
                    tvAcceptorStatus.setTextColor(getColor(R.color.teal_600))
                }
                "in_progress", "in progress" -> {
                    tvAcceptorStatus.text = "In Progress"
                    tvAcceptorStatus.setTextColor(getColor(R.color.teal_700))
                }
                "completed" -> {
                    tvAcceptorStatus.text = "Completed"
                    tvAcceptorStatus.setTextColor(getColor(R.color.green_600))
                }
                "delivering" -> {
                    tvAcceptorStatus.text = "Delivering"
                    tvAcceptorStatus.setTextColor(getColor(R.color.orange_600))
                }
                else -> {
                    tvAcceptorStatus.text = status.replaceFirstChar { it.uppercase() }
                }
            }
        } else {
            // Hide acceptor card if request is still open
            acceptorCard.visibility = View.GONE
        }
    }

    private fun setupAcceptButton(orderId: String, status: String) {
        val btnAcceptRequest = findViewById<CardView>(R.id.btnAcceptRequest)
        val btnAcceptRequestInner = findViewById<android.widget.Button>(R.id.btnAcceptRequestInner)

        // Only show accept button if status is "open"
        if (status == "open") {
            btnAcceptRequest.visibility = View.VISIBLE
            btnAcceptRequestInner.setOnClickListener {
                // Get current user
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Toast.makeText(this, "Please log in to accept deliveries", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Disable button to prevent double clicks
                btnAcceptRequestInner.isEnabled = false
                btnAcceptRequestInner.text = "Accepting..."

                // Use backend API instead of Firestore
                lifecycleScope.launch {
                    try {
                        val repository = com.example.smallbasket.repository.OrderRepository()
                        val result = repository.acceptOrder(orderId)

                        result.onSuccess { order ->
                            Toast.makeText(this@RequestDetailActivity, "Delivery accepted successfully!", Toast.LENGTH_SHORT).show()
                            // Close the activity and return to previous screen
                            finish()
                        }

                        result.onFailure { error ->
                            Toast.makeText(this@RequestDetailActivity, "Failed to accept delivery", Toast.LENGTH_SHORT).show()
                            // Re-enable button on failure
                            btnAcceptRequestInner.isEnabled = true
                            btnAcceptRequestInner.text = "Accept Delivery"
                        }

                    } catch (e: Exception) {
                        Toast.makeText(this@RequestDetailActivity, "Failed to accept delivery", Toast.LENGTH_SHORT).show()
                        // Re-enable button on exception
                        btnAcceptRequestInner.isEnabled = true
                        btnAcceptRequestInner.text = "Accept Delivery"
                    }
                }
            }
        } else {
            // Hide accept button if already accepted
            btnAcceptRequest.visibility = View.GONE
        }
    }
}