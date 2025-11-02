package com.example.smallbasket.api

import com.example.smallbasket.models.*
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
 * ✅ FIXED: Proper async token handling
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
     * ✅ OPTIMIZED: Get OkHttpClient with cached token authentication
     */
    private fun getOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val authInterceptor = Interceptor { chain ->
            val currentUser = FirebaseAuth.getInstance().currentUser

            val request = if (currentUser != null) {
                try {
                    // ✅ SPEED FIX: Use cached token (forceRefresh = false)
                    // This is MUCH faster than getting a new token every time
                    val tokenResult = currentUser.getIdToken(false)

                    // Check if token is already available (cached)
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
                        // Token not cached, fetch it synchronously (slower path)
                        android.util.Log.w("RetrofitClient", "Token not cached, fetching...")
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
            .connectTimeout(15, TimeUnit.SECONDS)  // Increased from default
            .readTimeout(15, TimeUnit.SECONDS)     // Increased from default
            .writeTimeout(15, TimeUnit.SECONDS)    // Increased from default
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