package com.smallbasket.dozo

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smallbasket.dozo.databinding.ActivityOrderConfirmationBinding
import com.smallbasket.dozo.models.CreateOrderRequest
import com.smallbasket.dozo.repository.OrderRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class OrderConfirmationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderConfirmationBinding
    private val repository = OrderRepository()
    private lateinit var vibrator: Vibrator

    // Order data
    private lateinit var item: String
    private lateinit var pickup: String
    private lateinit var pickupArea: String
    private lateinit var drop: String
    private lateinit var dropArea: String
    private var itemPrice: Double = 0.0
    private lateinit var deadline: String
    private var customDeadlineMinutes: Int? = null
    private var priority: Boolean = false
    private var notes: String? = null

    // Reward data
    private var estimatedReward: Double = 0.0
    private var isRewardFetched = false

    companion object {
        private const val TAG = "OrderConfirmation"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupStatusBar()
        enableEdgeToEdge()

        binding = ActivityOrderConfirmationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Get data from Intent
        val username = intent.getStringExtra("username") ?: "User"
        item = intent.getStringExtra("item") ?: ""
        pickup = intent.getStringExtra("pickup") ?: ""
        pickupArea = intent.getStringExtra("pickup_area") ?: ""
        drop = intent.getStringExtra("drop") ?: ""
        dropArea = intent.getStringExtra("drop_area") ?: ""
        itemPrice = intent.getDoubleExtra("item_price", 0.0)
        deadline = intent.getStringExtra("deadline") ?: "30m"
        customDeadlineMinutes = intent.getIntExtra("custom_deadline_minutes", -1).let {
            if (it == -1) null else it
        }
        priority = intent.getBooleanExtra("priority", false)
        notes = intent.getStringExtra("notes")

        // Review message
        binding.tvThankYou.text = "Review Your Order, $username"

        // Show initial order summary
        displayOrderSummary()

        // Setup listeners
        setupListeners()
        setupScrollListener()

        // Fetch reward estimate from backend
        fetchRewardEstimate()
    }

    private fun setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.apply {
                statusBarColor = Color.TRANSPARENT
                navigationBarColor = getColor(R.color.white)
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        )
            }
        }

        // Start with dark status bar (light icons) for teal header
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0, // Dark status bar (light icons)
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }

    private fun setupScrollListener() {
        binding.root.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (scrollY > 100) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility =
                        window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility =
                        window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
            }
        }
    }

    private fun displayOrderSummary() {
        // Item and locations
        binding.tvSampleItem.text = item
        binding.tvOrderPickup.text = "$pickup ($pickupArea)"
        binding.tvOrderDrop.text = "$drop ($dropArea)"

        // Pricing - Initial State
        binding.tvItemPrice.text = "₹${String.format(Locale.getDefault(), "%.2f", itemPrice)}"
        
        if (isRewardFetched) {
            binding.tvDeliveryFee.text = "₹${String.format(Locale.getDefault(), "%.2f", estimatedReward)}"
            binding.tvTotalAmount.text = "₹${String.format(Locale.getDefault(), "%.2f", itemPrice + estimatedReward)}"
            binding.btnConfirmOrder.isEnabled = true
        } else {
            binding.tvDeliveryFee.text = "Calculating..."
            binding.tvTotalAmount.text = "₹${String.format(Locale.getDefault(), "%.2f", itemPrice)} + ..."
            binding.btnConfirmOrder.isEnabled = false
        }

        // Priority
        if (priority) {
            binding.tvPriority.text = "⚡ High Priority"
            binding.labelPriority.visibility = View.VISIBLE
            binding.layoutPriority.visibility = View.VISIBLE
        } else {
            binding.labelPriority.visibility = View.GONE
            binding.layoutPriority.visibility = View.GONE
        }

        // Notes
        if (!notes.isNullOrEmpty()) {
            binding.tvNotes.text = notes
            binding.labelNotes.visibility = View.VISIBLE
            binding.layoutNotes.visibility = View.VISIBLE
        } else {
            binding.labelNotes.visibility = View.GONE
            binding.layoutNotes.visibility = View.GONE
        }
    }

    private fun fetchRewardEstimate() {
        Log.d(TAG, "Fetching reward estimate...")
        
        lifecycleScope.launch {
            val result = repository.estimateReward(
                itemPrice = itemPrice,
                pickupArea = pickupArea,
                dropArea = dropArea,
                priority = priority
            )

            result.onSuccess { estimate ->
                Log.d(TAG, "✅ Reward estimate fetched: ${estimate.finalReward}")
                estimatedReward = estimate.finalReward
                isRewardFetched = true
                displayOrderSummary()
            }

            result.onFailure { error ->
                Log.e(TAG, "❌ Failed to fetch reward estimate: ${error.message}")
                Toast.makeText(
                    this@OrderConfirmationActivity,
                    com.smallbasket.dozo.utils.ErrorUtils.getFriendlyMessage(this@OrderConfirmationActivity, error),
                    Toast.LENGTH_LONG
                ).show()
                
                // Fallback to a default if necessary, or let user retry
                binding.tvDeliveryFee.text = "Error"
                binding.btnConfirmOrder.text = "Retry Calculation"
                binding.btnConfirmOrder.isEnabled = true
                binding.btnConfirmOrder.setOnClickListener {
                    binding.btnConfirmOrder.text = "Calculating..."
                    binding.btnConfirmOrder.isEnabled = false
                    fetchRewardEstimate()
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            performMediumHaptic()
            navigateBackToEdit()
        }

        binding.btnConfirmOrder.setOnClickListener {
            if (isRewardFetched) {
                performMediumHaptic()
                confirmAndCreateOrder()
            } else {
                // If clicked during error state/retry
                performMediumHaptic()
                binding.btnConfirmOrder.isEnabled = false
                binding.btnConfirmOrder.text = "Calculating..."
                fetchRewardEstimate()
            }
        }

        binding.btnBackToEdit.setOnClickListener {
            performMediumHaptic()
            navigateBackToEdit()
        }
    }

    private fun navigateBackToEdit() {
        val intent = Intent(this, OrderActivity::class.java).apply {
            putExtra("item", item)
            putExtra("pickup", pickup)
            putExtra("pickup_area", pickupArea)
            putExtra("drop", drop)
            putExtra("drop_area", dropArea)
            putExtra("item_price", itemPrice)
            putExtra("deadline", deadline)
            putExtra("custom_deadline_minutes", customDeadlineMinutes)
            putExtra("priority", priority)
            putExtra("notes", notes)
        }
        startActivity(intent)
        finish()
    }

    private fun confirmAndCreateOrder() {
        binding.btnConfirmOrder.isEnabled = false
        binding.btnConfirmOrder.text = "Creating Order..."

        val deadlineISO = convertDeadlineToISO8601(deadline, customDeadlineMinutes)

        val orderRequest = CreateOrderRequest(
            item = listOf(item),
            pickupLocation = pickup,
            pickupArea = pickupArea,
            dropLocation = drop,
            dropArea = dropArea,
            itemPrice = itemPrice,
            timeRequested = deadlineISO, // Using deadline as requested time for simplicity
            deadline = deadlineISO,
            priority = priority,
            notes = notes,
            reward = estimatedReward // Use the same value estimated upfront
        )

        createOrder(orderRequest)
    }

    private fun convertDeadlineToISO8601(deadline: String, customMinutes: Int?): String {
        val calendar = Calendar.getInstance()
        when (deadline) {
            "30m" -> calendar.add(Calendar.MINUTE, 30)
            "1h" -> calendar.add(Calendar.HOUR_OF_DAY, 1)
            "2h" -> calendar.add(Calendar.HOUR_OF_DAY, 2)
            "4h" -> calendar.add(Calendar.HOUR_OF_DAY, 4)
            "custom" -> calendar.add(Calendar.MINUTE, customMinutes ?: 30)
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(calendar.time)
    }

    private fun createOrder(request: CreateOrderRequest) {
        lifecycleScope.launch {
            val result = repository.createOrder(request)

            result.onSuccess {
                performSuccessHaptic()
                Toast.makeText(
                    this@OrderConfirmationActivity,
                    "Order placed successfully!",
                    Toast.LENGTH_SHORT
                ).show()

                val intent = Intent(this@OrderConfirmationActivity, Homepage::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }

            result.onFailure { error ->
                Toast.makeText(
                    this@OrderConfirmationActivity,
                    com.smallbasket.dozo.utils.ErrorUtils.getFriendlyMessage(this@OrderConfirmationActivity, error),
                    Toast.LENGTH_LONG
                ).show()
                binding.btnConfirmOrder.isEnabled = true
                binding.btnConfirmOrder.text = "Confirm Order"
            }
        }
    }

    private fun performMediumHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(15, 204))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(15)
        }
    }

    private fun performSuccessHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(
                longArrayOf(0, 8, 40, 10),
                intArrayOf(0, 120, 0, 160),
                -1
            ))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }
}
