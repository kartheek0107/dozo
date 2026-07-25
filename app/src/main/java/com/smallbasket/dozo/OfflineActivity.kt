package com.smallbasket.dozo

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.smallbasket.dozo.databinding.ActivityOfflineBinding

class OfflineActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOfflineBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityOfflineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Force dark icons and set status bar background to match app
        // NOTE: Must be called AFTER setContentView or on some devices decorView is null
        window.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                statusBarColor = Color.parseColor("#F9FAFB")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insetsController?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }

        setupGame()
        setupBackNavigation()
        
        // Primary Action Button matching Login button style
        binding.retryButton.setOnClickListener {
            if (isNetworkAvailable()) {
                // Navigate back to the entry point instead of just finishing
                // This handles cases where OfflineActivity was the first screen (app start while offline)
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Still offline. Try again in a moment.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.apply {
                statusBarColor = Color.TRANSPARENT
                navigationBarColor = Color.WHITE
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        )
            }
        }

        // Ensure Status Bar icons are visible (Dark icons on light background)
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
    }

    private fun setupGame() {
        binding.composeViewDino.apply {
            // Dispose of the Composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DinoGame()
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isNetworkAvailable()) {
                    isEnabled = false // Disable this callback and let the system handle back
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    // Prevent leaving if still offline, or show a toast
                    Toast.makeText(this@OfflineActivity, "Please reconnect to continue using DOZO", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

}
