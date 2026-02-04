package com.example.smallbasket

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
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smallbasket.databinding.ActivityProfileBinding
import com.example.smallbasket.repository.OrderRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val repository = OrderRepository()
    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupStatusBar()

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val user = auth.currentUser

        setupClickListeners()

        if (user != null) {
            val displayName = user.displayName ?: "User"
            binding.tvFullName.text = displayName
            binding.tvEmailAddress.text = user.email ?: "No email"

            user.metadata?.creationTimestamp?.let { timestamp ->
                binding.tvJoiningDate.text =
                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
            }

            loadUserDataFromFirestore(user.uid)
            user.email?.let { email ->
                parseEmailAndShowDetails(email)
            }
            loadUserStats()
        } else {
            redirectToLogin()
        }
    }

    private fun setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.apply {
                statusBarColor = Color.TRANSPARENT
                navigationBarColor = Color.TRANSPARENT
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        )
            }
        }

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

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            performMediumHaptic()
            finish()
        }

        // ✅ UPDATED: Now actually opens edit dialog
        binding.btnEditProfile.setOnClickListener {
            performMediumHaptic()
            showEditProfileDialog()
        }

        binding.btnLogout.setOnClickListener {
            performMediumHaptic()
            auth.signOut()
            redirectToLogin()
        }
    }

    // ✅ NEW: Show dialog to edit name and phone
    private fun showEditProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)

        val etName = dialogView.findViewById<EditText>(R.id.etEditName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etEditPhone)

        // Pre-fill with current values
        etName.setText(binding.tvFullName.text.toString())
        val currentPhone = binding.tvMobileNumber.text.toString()
        if (currentPhone != "Not set" && currentPhone != "Not available" && currentPhone != "Error loading") {
            etPhone.setText(currentPhone)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Profile")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = etName.text.toString().trim()
                val newPhone = etPhone.text.toString().trim()

                if (validateProfileInput(newName, newPhone)) {
                    updateUserProfile(newName, newPhone)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    // ✅ NEW: Validate name and phone input
    private fun validateProfileInput(name: String, phone: String): Boolean {
        if (name.isEmpty()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
            return false
        }

        if (name.length < 2 || name.length > 100) {
            Toast.makeText(this, "Name must be between 2 and 100 characters", Toast.LENGTH_SHORT).show()
            return false
        }

        if (phone.isEmpty()) {
            Toast.makeText(this, "Phone number cannot be empty", Toast.LENGTH_SHORT).show()
            return false
        }

        if (phone.length < 10 || phone.length > 20) {
            Toast.makeText(this, "Phone number must be between 10 and 20 characters", Toast.LENGTH_SHORT).show()
            return false
        }

        // Optional: Validate phone format (digits, spaces, +, -)
        val phoneRegex = "^[+\\d\\s-]+$".toRegex()
        if (!phone.matches(phoneRegex)) {
            Toast.makeText(this, "Phone number can only contain digits, spaces, +, and -", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    // ✅ NEW: Update user profile in Firestore
    private fun updateUserProfile(name: String, phone: String) {
        val user = auth.currentUser ?: return

        lifecycleScope.launch {
            try {
                // Show loading
                Toast.makeText(this@ProfileActivity, "Updating profile...", Toast.LENGTH_SHORT).show()

                // Update Firestore
                firestore.collection("users")
                    .document(user.uid)
                    .update(
                        mapOf(
                            "name" to name,
                            "phone" to phone,
                            "updated_at" to com.google.firebase.Timestamp.now()
                        )
                    )
                    .await()

                // Update Firebase Auth display name
                val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
                    displayName = name
                }
                user.updateProfile(profileUpdates).await()

                // Refresh UI
                binding.tvFullName.text = name
                binding.tvMobileNumber.text = phone

                performMediumHaptic()
                Toast.makeText(
                    this@ProfileActivity,
                    "✅ Profile updated successfully!",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Toast.makeText(
                    this@ProfileActivity,
                    "❌ Error updating profile: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun loadUserDataFromFirestore(uid: String) {
        binding.tvMobileNumber.text = "Not set"
        lifecycleScope.launch {
            try {
                val document = firestore.collection("users").document(uid).get().await()
                if (document.exists()) {
                    val phone = document.getString("phone")
                    val name = document.getString("name")
                    binding.tvMobileNumber.text = phone ?: "Not set"
                    if (!name.isNullOrEmpty()) {
                        binding.tvFullName.text = name
                    }
                } else {
                    binding.tvMobileNumber.text = "Not available"
                }
            } catch (e: Exception) {
                binding.tvMobileNumber.text = "Error loading"
            }
        }
    }

    private fun loadUserStats() {
        binding.tvStatDeliveries.text = "0"
        binding.tvStatOrders.text = "0"
        binding.tvStatEarned.text = "₹0"

        lifecycleScope.launch {
            try {
                val result = repository.getUserStats(auth.currentUser?.uid ?: "")
                result.onSuccess { stats ->
                    binding.tvStatDeliveries.text = stats.completedDeliveries.toString()
                    binding.tvStatOrders.text = stats.totalOrders.toString()
                    val totalEarned = stats.completedDeliveries * 10
                    binding.tvStatEarned.text = "₹$totalEarned"
                }
            } catch (e: Exception) {
                // Silent fail
            }
        }
    }

    private fun redirectToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun parseEmailAndShowDetails(email: String) {
        val username = email.substringBefore("@")
        val pattern = Pattern.compile("(\\d{8})")
        val matcher = pattern.matcher(username)

        if (!matcher.find()) {
            binding.cardStudentInfo.visibility = View.GONE
            return
        }

        val digits = matcher.group(1) ?: return
        if (digits.length != 8) {
            binding.cardStudentInfo.visibility = View.GONE
            return
        }

        try {
            val admissionDigit = digits[0]
            val batchSuffix = digits.substring(1, 3)
            val branchCode = digits.substring(3, 5)
            val rollStr = digits.substring(5, 8)

            val batchStartYear = 2000 + batchSuffix.toInt()
            val rollNumber = rollStr.toInt()

            val branch = when (branchCode) {
                "11" -> "Computer Science (CSE)"
                "12" -> "Information Technology (IT)"
                "13" -> "Data Science"
                else -> "Unknown Branch"
            }

            val section = if (branchCode == "11") {
                if (rollNumber > 60) "B" else "A"
            } else null

            val isDasa = admissionDigit == '2'

            binding.cardStudentInfo.visibility = View.VISIBLE
            binding.layoutBatch.visibility = View.VISIBLE
            binding.tvBatch.text = "Batch: $batchStartYear–${batchStartYear + 4}"
            binding.layoutBranch.visibility = View.VISIBLE
            binding.tvBranch.text = "Branch: $branch"

            if (section != null) {
                binding.layoutSection.visibility = View.VISIBLE
                binding.tvSection.text = "Section: $section"
            } else {
                binding.layoutSection.visibility = View.GONE
            }

            binding.layoutDasa.visibility = if (isDasa) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            binding.cardStudentInfo.visibility = View.GONE
        }
    }

    private fun performMediumHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(15, 80)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(15)
        }
    }
}