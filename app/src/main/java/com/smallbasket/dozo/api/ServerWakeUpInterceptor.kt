package com.smallbasket.dozo.api

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.smallbasket.dozo.ServerWakeUpActivity
import okhttp3.Interceptor
import okhttp3.Response
import java.net.SocketTimeoutException

/**
 * Interceptor to detect if the server is asleep (slow response or timeout)
 * and trigger the ServerWakeUpActivity.
 */
class ServerWakeUpInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        // Skip wake-up check for health check itself to avoid infinite loop
        if (request.url.toString().contains("health") || request.url.toString().endsWith("/")) {
            return chain.proceed(request)
        }

        try {
            val response = chain.proceed(request)
            
            // Check for 503 Service Unavailable or 504 Gateway Timeout
            if (response.code == 503 || response.code == 504) {
                if (isInternetAvailable()) {
                    triggerWakeUpScreen()
                }
            }
            
            return response
        } catch (e: Exception) {
            if (e is SocketTimeoutException) {
                if (isInternetAvailable()) {
                    Log.d("ServerWakeUp", "Timeout detected while internet is on. Triggering wake-up screen.")
                    triggerWakeUpScreen()
                }
            }
            throw e
        }
    }

    private fun isInternetAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun triggerWakeUpScreen() {
        try {
            val intent = Intent(context, ServerWakeUpActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("ServerWakeUp", "Failed to start ServerWakeUpActivity", e)
        }
    }
}
