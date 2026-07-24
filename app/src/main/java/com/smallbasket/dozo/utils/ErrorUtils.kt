package com.smallbasket.dozo.utils

import android.content.Context
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
        
        return when {
            // Network issues
            error is UnknownHostException || 
            error is IOException || 
            message.contains("network", ignoreCase = true) -> 
                context.getString(R.string.error_no_internet)
            
            error is SocketTimeoutException || 
            message.contains("timeout", ignoreCase = true) ->
                context.getString(R.string.error_timeout)
            
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
            message.contains("503") ||
            message.contains("server", ignoreCase = true) ->
                context.getString(R.string.error_service_unavailable)
            
            // Default fallback
            else -> context.getString(R.string.error_generic)
        }
    }
}
