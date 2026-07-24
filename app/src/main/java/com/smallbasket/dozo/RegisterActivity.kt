package com.smallbasket.dozo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smallbasket.dozo.databinding.ActivityRegisterBinding
import com.smallbasket.dozo.location.LocationUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check internet on start
        if (!LocationUtils.checkInternetAndRedirect(this)) {
            finish()
            return
        }

        enableEdgeToEdge()
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Register"

        binding.tvGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.btnSignUp.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val mobile = binding.etMobile.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || mobile.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_fields_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!email.endsWith("@iiitsonepat.ac.in")) {
                Toast.makeText(this, getString(R.string.error_invalid_email_domain), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (mobile.length != 10) {
                Toast.makeText(this, getString(R.string.error_invalid_phone), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, getString(R.string.error_weak_password), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnSignUp.isEnabled = false
            binding.btnSignUp.text = "Creating Account..."

            createAccount(name, email, password, mobile)
        }
    }

    private fun createAccount(name: String, email: String, password: String, mobile: String) {
        lifecycleScope.launch {
            try {
                // Create Firebase Auth User
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user

                if (user != null) {
                    // Update display name
                    val profileUpdates = userProfileChangeRequest {
                        displayName = name
                    }
                    user.updateProfile(profileUpdates).await()

                    // Send email verification
                    user.sendEmailVerification().await()

                    // Store user data in Firestore
                    val userData = hashMapOf(
                        "uid" to user.uid,
                        "email" to email,
                        "name" to name,
                        "phone" to mobile,
                        "email_verified" to false,
                        "created_at" to com.google.firebase.Timestamp.now(),
                        "last_login" to com.google.firebase.Timestamp.now()
                    )

                    firestore.collection("users")
                        .document(user.uid)
                        .set(userData)
                        .await()

                    // Sign out until email is verified
                    auth.signOut()

                    // Show modern verification dialog
                    showVerificationDialog(email)

                } else {
                    throw Exception("User creation failed")
                }

            } catch (e: Exception) {
                val errorMessage = com.smallbasket.dozo.utils.ErrorUtils.getFriendlyMessage(this@RegisterActivity, e)

                Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_LONG).show()
                binding.btnSignUp.isEnabled = true
                binding.btnSignUp.text = getString(R.string.register)
            }
        }
    }

    private fun showVerificationDialog(email: String) {
        AlertDialog.Builder(this)
            .setTitle("✅ Account Created!")
            .setMessage(
                "A verification email has been sent to:\n\n$email\n\n" +
                        "📧 Check your inbox and click the verification link\n\n" +
                        "Once verified, come back and login immediately - no waiting required!"
            )
            .setPositiveButton("Go to Login") { _, _ ->
                startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                finish()
            }
            .setCancelable(false)
            .show()
    }
}