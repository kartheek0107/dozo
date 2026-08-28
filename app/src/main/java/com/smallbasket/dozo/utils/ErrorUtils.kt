package com.smallbasket.dozo.utils

import android.content.Context
import android.content.Intent
import com.smallbasket.dozo.R
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Utility for mapping exceptions to user-friendly, professional messages.
 * ✅ SECURE: Never exposes server IPs, stack traces, or technical details.
 */
object ErrorUtils {

    /**
     * Get a professional error message for any throwable
     */
    fun getFriendlyMessage(context: Context, error: Throwable?): String {
        if (error == null) return context.getString(R.string.error_generic)
        
        val message = error.message ?: ""
        
        // Check for server sleep indicators (timeout/503/504)
        if (error is SocketTimeoutException || 
            message.contains("503") || 
            message.contains("504") ||
            message.contains("timeout", ignoreCase = true)) {
            
            // Redirect to ServerWakeUpActivity is handled by Interceptor
            // but we keep this for direct repository calls if any
            try {
                val intent = Intent(context, com.smallbasket.dozo.ServerWakeUpActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback
            }
            return "Server is waking up, please wait..."
        }

        return when {
            // Network issues
            error is UnknownHostException || 
            error is IOException ||
            message.contains("network", ignoreCase = true) -> 
                context.getString(R.string.error_no_internet)
            
            // HTTP Status Codes (heuristic mapping)
            message.contains("429") ->
                context.getString(R.string.error_too_many_requests)
            
            message.contains("401") || 
            message.contains("403") || 
            message.contains("auth", ignoreCase = true) ->
                context.getString(R.string.error_session_expired)
            
            message.contains("404") ->
                context.getString(R.string.error_not_found)
            
            message.contains("500") || 
            message.contains("502") || 
            message.contains("server", ignoreCase = true) ->
                context.getString(R.string.error_service_unavailable)
            
            // Default fallback
            else -> context.getString(R.string.error_generic)
        }
    }
}
