package com.example.smallbasket.api

import com.example.smallbasket.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/**
 * Retrofit client singleton for API communication
 * ✅ SECURE: Auth tokens never logged in release, sensitive headers redacted
 */
object RetrofitClient {

    private var retrofit: Retrofit? = null

    /**
     * Get configured Retrofit instance
     */
    private fun getRetrofit(): Retrofit {
        if (retrofit == null) {
            retrofit = Retrofit.Builder()
                .baseUrl(ApiConfig.BASE_URL)
                .client(getOkHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!
    }

    /**
     * ✅ SECURE: Get OkHttpClient with proper logging configuration
     */
    private fun getOkHttpClient(): OkHttpClient {
        // ✅ SECURE: Logging level based on build type
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                // Debug: Log body but redact sensitive headers
                HttpLoggingInterceptor.Level.BODY
            } else {
                // Release: No logging at all
                HttpLoggingInterceptor.Level.NONE
            }

            // ✅ Redact sensitive headers even in debug
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }

        val authInterceptor = Interceptor { chain ->
            val currentUser = FirebaseAuth.getInstance().currentUser

            val request = if (currentUser != null) {
                try {
                    // ✅ Use cached token (faster)
                    val tokenResult = currentUser.getIdToken(false)

                    if (tokenResult.isComplete) {
                        val token = tokenResult.result?.token
                        if (token != null) {
                            chain.request().newBuilder()
                                .addHeader("Authorization", "Bearer $token")
                                .build()
                        } else {
                            chain.request()
                        }
                    } else {
                        // Token not cached, fetch synchronously
                        val token = runBlocking {
                            try {
                                tokenResult.await().token
                            } catch (e: Exception) {
                                android.util.Log.e("RetrofitClient", "Error getting auth token", e)
                                null
                            }
                        }

                        if (token != null) {
                            chain.request().newBuilder()
                                .addHeader("Authorization", "Bearer $token")
                                .build()
                        } else {
                            chain.request()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RetrofitClient", "Exception in auth interceptor", e)
                    chain.request()
                }
            } else {
                chain.request()
            }

            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .connectTimeout(ApiConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(ApiConfig.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(ApiConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * Get API service instance
     */
    val apiService: ApiService by lazy {
        getRetrofit().create(ApiService::class.java)
    }

    /**
     * Reset retrofit instance (useful for configuration changes)
     */
    fun reset() {
        retrofit = null
    }
}