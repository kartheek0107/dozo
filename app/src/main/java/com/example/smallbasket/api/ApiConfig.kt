package com.example.smallbasket.api

import com.example.smallbasket.BuildConfig

object ApiConfig {
    // ✅ Loaded from BuildConfig (which reads from secrets.properties)
    val BASE_URL: String = BuildConfig.API_BASE_URL

    // Network timeouts
    const val CONNECT_TIMEOUT = 15L
    const val READ_TIMEOUT = 15L
    const val WRITE_TIMEOUT = 15L
}