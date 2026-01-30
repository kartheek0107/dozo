package com.example.smallbasket.repository

import android.util.Log
import com.example.smallbasket.api.RetrofitClient
import com.example.smallbasket.models.*
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class OrderRepository {
    private val api = RetrofitClient.apiService
    private val TAG = "OrderRepository"

    // ============================================
    // REQUEST OPERATIONS
    // ============================================

    suspend fun createOrder(request: CreateOrderRequest): Result<Order> {
        return try {
            val response = api.createOrder(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getAllOrders(status: String? = null, area: String? = null): Result<List<Order>> {
        return try {
            // Convert frontend status to backend status
            val backendStatus = when(status) {
                "pending" -> "open"
                "picked_up", "delivered" -> "completed"
                else -> status
            }

            val response = api.getAllOrders(backendStatus, area, null)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getUserOrders(): Result<List<Order>> {
        return try {
            // 🔍 DEBUG: Check authentication status
            val currentUser = FirebaseAuth.getInstance().currentUser
            Log.d(TAG, "getUserOrders called")
            Log.d(TAG, "Current user: ${currentUser?.email ?: "NOT LOGGED IN"}")
            Log.d(TAG, "User UID: ${currentUser?.uid ?: "NULL"}")

            if (currentUser == null) {
                Log.e(TAG, "⚠️ NO USER LOGGED IN - This will cause 401/500 error")
                return Result.failure(Exception("Not authenticated. Please log in again."))
            }

            // Try to get token to verify it's valid
            try {
                val tokenResult = currentUser.getIdToken(false)
                if (tokenResult.isComplete) {
                    val token = tokenResult.result?.token
                    if (token != null) {
                        Log.d(TAG, "✅ Auth token available (first 20 chars): ${token.take(20)}...")
                    } else {
                        Log.e(TAG, "⚠️ Auth token is NULL")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "⚠️ Error checking token: ${e.message}")
            }

            Log.d(TAG, "Making API call to /request/mine...")
            val response = api.getUserOrders()
            Log.d(TAG, "Response code: ${response.code()}")
            Log.d(TAG, "Response successful: ${response.isSuccessful}")

            if (response.isSuccessful && response.body() != null) {
                val orders = response.body()!!
                Log.d(TAG, "✅ Successfully got ${orders.size} orders")
                Result.success(orders)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "❌ API Error ${response.code()}: $errorMsg")

                // Provide helpful error messages based on status code
                val userFriendlyMsg = when(response.code()) {
                    401 -> "Authentication failed. Please log out and log in again."
                    403 -> "Access forbidden. Please check your account permissions."
                    500 -> "Server error. Please try again later or contact support.\nDetails: $errorMsg"
                    else -> "Error ${response.code()}: $errorMsg"
                }

                Result.failure(Exception(userFriendlyMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Network exception: ${e.message}", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getAcceptedOrders(): Result<List<Order>> {
        return try {
            // 🔍 DEBUG: Check authentication status
            val currentUser = FirebaseAuth.getInstance().currentUser
            Log.d(TAG, "getAcceptedOrders called")
            Log.d(TAG, "Current user: ${currentUser?.email ?: "NOT LOGGED IN"}")

            if (currentUser == null) {
                Log.e(TAG, "⚠️ NO USER LOGGED IN")
                return Result.failure(Exception("Not authenticated. Please log in again."))
            }

            Log.d(TAG, "Making API call to /request/accepted...")
            val response = api.getAcceptedOrders()
            Log.d(TAG, "Response code: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val orders = response.body()!!
                Log.d(TAG, "✅ Successfully got ${orders.size} accepted orders")
                Result.success(orders)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "❌ API Error ${response.code()}: $errorMsg")

                val userFriendlyMsg = when(response.code()) {
                    401 -> "Authentication failed. Please log out and log in again."
                    403 -> "Access forbidden. Please check your account permissions."
                    500 -> "Server error. Please try again later.\nDetails: $errorMsg"
                    else -> "Error ${response.code()}: $errorMsg"
                }

                Result.failure(Exception(userFriendlyMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Network exception: ${e.message}", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getOrder(orderId: String): Result<Order> {
        return try {
            val response = api.getOrder(orderId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun acceptOrder(orderId: String, delivererId: String = "", estimatedPrice: Double = 0.0): Result<Order> {
        return try {
            val request = AcceptOrderRequest(orderId)
            val response = api.acceptOrder(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun updateOrderStatus(orderId: String, status: String): Result<Order> {
        return try {
            // Map frontend status to backend status
            val backendStatus = when(status) {
                "picked_up", "delivered" -> "completed"
                "pending" -> "open"
                else -> status
            }

            val request = UpdateOrderStatusRequest(orderId, backendStatus)
            val response = api.updateOrderStatus(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    // ============================================
    // USER OPERATIONS
    // ============================================

    suspend fun getUserStats(userId: String = ""): Result<UserStats> {
        return try {
            val response = api.getUserStats()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun getUserProfile(): Result<UserProfileResponse> {
        return try {
            val response = api.getUserProfile()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    // ============================================
    // CONNECTIVITY OPERATIONS
    // ============================================

    suspend fun updateConnectivity(isConnected: Boolean, locationGranted: Boolean): Result<SuccessResponse> {
        return try {
            val request = ConnectivityUpdateRequest(isConnected, locationGranted)
            val response = api.updateConnectivity(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    // ============================================
    // AREA OPERATIONS
    // ============================================

    suspend fun getAvailableAreas(): Result<AreasListResponse> {
        return try {
            val response = api.getAvailableAreas()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun setPreferredAreas(areas: List<String>): Result<SuccessResponse> {
        return try {
            val request = PreferredAreasRequest(areas)
            val response = api.setPreferredAreas(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    // ============================================
    // NOTIFICATION OPERATIONS
    // ============================================

    suspend fun registerFCMToken(token: String): Result<SuccessResponse> {
        return try {
            val request = FCMTokenRequest(token)
            val response = api.registerFCMToken(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun unregisterFCMToken(): Result<SuccessResponse> {
        return try {
            val response = api.unregisterFCMToken()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}