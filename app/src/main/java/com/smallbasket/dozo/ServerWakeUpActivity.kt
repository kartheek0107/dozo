package com.smallbasket.dozo

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import com.smallbasket.dozo.api.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ServerWakeUpActivity : AppCompatActivity() {

    private lateinit var tvETA: TextView
    private lateinit var tvStatus: TextView
    private var countDownTimer: CountDownTimer? = null
    private var isPinging = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_wakeup)

        setupStatusBar()

        tvETA = findViewById(R.id.tvETA)
        tvStatus = findViewById(R.id.tvStatus)

        findViewById<ComposeView>(R.id.composeViewDino).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DinoGame()
            }
        }

        startCountdown()
        startPinging()
    }

    private fun setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.apply {
                statusBarColor = Color.parseColor("#F9FAFB")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }

    private fun startCountdown() {
        // Assume 90 seconds max wake up time for free tier
        countDownTimer = object : CountDownTimer(90000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                tvETA.text = "ETA: ${seconds}s"
            }

            override fun onFinish() {
                tvETA.text = "Almost there..."
            }
        }.start()
    }

    private fun startPinging() {
        lifecycleScope.launch {
            while (isPinging) {
                try {
                    // Use a new client without the Interceptor for the health check itself
                    // to avoid infinite loops (though the Interceptor skips /health)
                    val response = RetrofitClient.apiService.healthCheck()
                    if (response.isSuccessful) {
                        isPinging = false
                        onServerAwake()
                    }
                } catch (e: Exception) {
                    Log.d("ServerWakeUp", "Ping failed, server still sleeping...")
                }
                delay(3000) // Ping every 3 seconds
            }
        }
    }

    private fun onServerAwake() {
        runOnUiThread {
            countDownTimer?.cancel()
            tvStatus.text = "Server is Awake! Redirecting..."
            tvStatus.setTextColor(Color.parseColor("#10B981")) // Green
            
            lifecycleScope.launch {
                delay(1500)
                // Navigate to MainActivity to resume flow
                val intent = Intent(this@ServerWakeUpActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        isPinging = false
    }

    override fun onBackPressed() {
        // Prevent leaving while waking up, unless they really want to exit
        Toast.makeText(this, "Please wait for the server to wake up", Toast.LENGTH_SHORT).show()
    }
}
