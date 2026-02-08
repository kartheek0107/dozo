package com.example.smallbasket

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowInsetsController
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smallbasket.databinding.ActivityOrderBinding
import com.example.smallbasket.models.CreateOrderRequest
import com.example.smallbasket.repository.OrderRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class OrderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderBinding
    private val repository = OrderRepository()
    private val auth = FirebaseAuth.getInstance()
    private var selectedDeadline = "1h" // ✅ Default to 1 hour
    private var customDeadlineMinutes: Int? = null
    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupStatusBar()
        enableEdgeToEdge()

        binding = ActivityOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize vibrator for haptic feedback
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        setupSpinners()
        setupBackButton()
        setupDeadlineRadioGroup()
        setupListeners()
        setupScrollListener() // ✅ RESTORED: Needed for status bar transitions with header

        // Restore state if coming back from confirmation screen
        restoreState()
    }

    private fun setupSpinners() {
        // Setup Pickup Area Spinner
        val pickupAreas = resources.getStringArray(R.array.pickup_areas)
        val pickupAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            pickupAreas
        )
        pickupAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.pickupArea.adapter = pickupAdapter

        // Setup Drop Area Spinner
        val dropAreas = resources.getStringArray(R.array.drop_areas)
        val dropAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            dropAreas
        )
        dropAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.dropArea.adapter = dropAdapter
    }

    private fun restoreState() {
        // Get data from Intent (if returning from OrderConfirmationActivity)
        val item = intent.getStringExtra("item")
        val pickup = intent.getStringExtra("pickup")
        val pickupArea = intent.getStringExtra("pickup_area")
        val drop = intent.getStringExtra("drop")
        val dropArea = intent.getStringExtra("drop_area")
        val itemPrice = intent.getDoubleExtra("item_price", 0.0)
        val deadline = intent.getStringExtra("deadline")
        val customMinutes = intent.getIntExtra("custom_deadline_minutes", -1)
        val priority = intent.getBooleanExtra("priority", false)
        val notes = intent.getStringExtra("notes")

        // Restore fields if data exists
        if (item != null) {
            binding.item.setText(item)
        }

        if (pickup != null) {
            binding.pickup.setText(pickup)
        }

        if (pickupArea != null) {
            val pickupAreas = resources.getStringArray(R.array.pickup_areas)
            val pickupPosition = pickupAreas.indexOf(pickupArea)
            if (pickupPosition >= 0) {
                binding.pickupArea.setSelection(pickupPosition)
            }
        }

        if (drop != null) {
            binding.drop.setText(drop)
        }

        if (dropArea != null) {
            val dropAreas = resources.getStringArray(R.array.drop_areas)
            val dropPosition = dropAreas.indexOf(dropArea)
            if (dropPosition >= 0) {
                binding.dropArea.setSelection(dropPosition)
            }
        }

        if (itemPrice > 0.0) {
            binding.fare.setText(itemPrice.toString())
        }

        if (deadline != null) {
            selectedDeadline = deadline
            if (deadline == "custom" && customMinutes != -1) {
                customDeadlineMinutes = customMinutes
                // Calculate and show the custom time
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.MINUTE, customMinutes)
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val formattedTime = timeFormat.format(calendar.time)
                binding.tvCustomTime.visibility = View.VISIBLE
                binding.tvCustomTime.text = "Expires at $formattedTime"
            }
            selectDeadlineRadioButton(deadline)
        }

        binding.priority.isChecked = priority

        if (notes != null) {
            binding.notes.setText(notes)
        }
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

        // ✅ Start with dark status bar (light icons) for teal header
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0, // Dark status bar
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }

    // ✅ RESTORED: Dynamic status bar switching based on scroll
    private fun setupScrollListener() {
        binding.scrollView.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            // When scrolled past header, switch to light status bar (dark icons)
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
                // When at top, dark status bar (light icons) for teal header
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

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            performMediumHaptic()
            finish()
        }
    }

    private fun setupDeadlineRadioGroup() {
        // Set default selection (1 hour is pre-checked in XML)
        selectedDeadline = "1h"

        binding.deadlineGroup.setOnCheckedChangeListener { group, checkedId ->
            performLightHaptic()

            when (checkedId) {
                R.id.deadline_30m -> {
                    selectedDeadline = "30m"
                    customDeadlineMinutes = null
                    binding.tvCustomTime.visibility = View.GONE
                }
                R.id.deadline_1h -> {
                    selectedDeadline = "1h"
                    customDeadlineMinutes = null
                    binding.tvCustomTime.visibility = View.GONE
                }
                R.id.deadline_2h -> {
                    selectedDeadline = "2h"
                    customDeadlineMinutes = null
                    binding.tvCustomTime.visibility = View.GONE
                }
                R.id.deadline_4h -> {
                    selectedDeadline = "4h"
                    customDeadlineMinutes = null
                    binding.tvCustomTime.visibility = View.GONE
                }
                R.id.deadline_custom -> {
                    showCustomTimePicker()
                }
            }
        }

        // Priority toggle haptic
        binding.priority.setOnCheckedChangeListener { _, isChecked ->
            performMediumHaptic()
        }
    }

    private fun selectDeadlineRadioButton(deadline: String) {
        when (deadline) {
            "30m" -> binding.deadline30m.isChecked = true
            "1h" -> binding.deadline1h.isChecked = true
            "2h" -> binding.deadline2h.isChecked = true
            "4h" -> binding.deadline4h.isChecked = true
            "custom" -> binding.deadlineCustom.isChecked = true
        }
    }

    private fun showCustomTimePicker() {
        val calendar = Calendar.getInstance()

        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                performLightHaptic()

                val currentCalendar = Calendar.getInstance()
                val selectedCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                if (selectedCalendar.timeInMillis <= currentCalendar.timeInMillis) {
                    selectedCalendar.add(Calendar.DAY_OF_MONTH, 1)
                }

                val diffInMillis = selectedCalendar.timeInMillis - currentCalendar.timeInMillis
                val diffInMinutes = (diffInMillis / (1000 * 60)).toInt()

                if (diffInMinutes < 10) {
                    performMediumHaptic()
                    Toast.makeText(
                        this,
                        "Please select a time at least 10 minutes from now",
                        Toast.LENGTH_SHORT
                    ).show()
                    selectDeadlineRadioButton(selectedDeadline)
                    return@TimePickerDialog
                }

                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val formattedTime = timeFormat.format(selectedCalendar.time)

                binding.tvCustomTime.visibility = View.VISIBLE
                binding.tvCustomTime.text = "Expires at $formattedTime"

                selectedDeadline = "custom"
                customDeadlineMinutes = diffInMinutes
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        )

        timePickerDialog.setTitle("Select expiry time")

        timePickerDialog.setOnCancelListener {
            selectDeadlineRadioButton(selectedDeadline)
        }

        timePickerDialog.show()
    }

    private fun setupListeners() {
        binding.btnPlaceNow.setOnClickListener {
            // Clear previous errors
            binding.pickupError.visibility = View.GONE
            binding.dropError.visibility = View.GONE
            binding.fareError.visibility = View.GONE

            // Get form values
            val item = binding.item.text.toString().trim()
            val pickup = binding.pickup.text.toString().trim()
            val pickupArea = binding.pickupArea.selectedItem?.toString() ?: ""
            val drop = binding.drop.text.toString().trim()
            val dropArea = binding.dropArea.selectedItem?.toString() ?: ""
            val priceText = binding.fare.text.toString().trim()
            val priority = binding.priority.isChecked
            val notes = binding.notes.text.toString().trim()

            // Validation
            var hasError = false

            if (item.isEmpty()) {
                performMediumHaptic()
                Toast.makeText(this, "Item name is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pickup.isEmpty()) {
                binding.pickupError.text = "Pickup location is required"
                binding.pickupError.visibility = View.VISIBLE
                hasError = true
            }

            if (drop.isEmpty()) {
                binding.dropError.text = "Drop location is required"
                binding.dropError.visibility = View.VISIBLE
                hasError = true
            }

            // Validate price (optional)
            var itemPrice = 0.0
            if (priceText.isNotEmpty()) {
                try {
                    itemPrice = priceText.toDouble()
                    if (itemPrice <= 0) {
                        binding.fareError.text = "Please enter a valid price"
                        binding.fareError.visibility = View.VISIBLE
                        hasError = true
                    }
                } catch (e: NumberFormatException) {
                    binding.fareError.text = "Please enter a valid price"
                    binding.fareError.visibility = View.VISIBLE
                    hasError = true
                }
            }

            if (hasError) {
                performMediumHaptic()
                return@setOnClickListener
            }

            performMediumHaptic()

            // Navigate to confirmation screen with all data
            val intent = Intent(this, OrderConfirmationActivity::class.java)
            intent.putExtra("username", auth.currentUser?.displayName ?: "User")
            intent.putExtra("item", item)
            intent.putExtra("pickup", pickup)
            intent.putExtra("pickup_area", pickupArea)
            intent.putExtra("drop", drop)
            intent.putExtra("drop_area", dropArea)
            intent.putExtra("item_price", itemPrice)
            intent.putExtra("deadline", selectedDeadline)
            intent.putExtra("custom_deadline_minutes", customDeadlineMinutes)
            intent.putExtra("priority", priority)
            intent.putExtra("notes", notes)
            startActivity(intent)
            finish()
        }
    }

    // ==================== HAPTIC FEEDBACK METHODS ====================

    /**
     * Light Haptic: 10ms duration, 40% amplitude
     * Used for: RadioGroup selections, time picker interactions
     */
    private fun performLightHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(
                10,  // 10ms duration
                102  // ~40% amplitude (40% of 255 = 102)
            )
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }

    /**
     * Medium Haptic: 15ms duration, 80% amplitude
     * Used for: Back button, priority toggle, form submission, errors
     */
    private fun performMediumHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(
                15,  // 15ms duration
                204  // ~80% amplitude (80% of 255 = 204)
            )
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(15)
        }
    }
}