// File: app/src/main/java/com/example/smallbasket/repository/MapRepository.kt
package com.example.smallbasket.repository

import android.util.Log
import com.example.smallbasket.api.RetrofitClient
import com.example.smallbasket.models.NearbyUsersRequest
import com.example.smallbasket.models.NearbyUsersResponse
import com.example.smallbasket.models.MyGPSLocationResponse
import com.example.smallbasket.models.UsersInAreaResponse
import com.example.smallbasket.models.ReachableCountResponse
import com.example.smallbasket.models.ReachableByAreaResponse

class MapRepository {
    companion object {
        private const val TAG = "MapRepository"
    }

    private val api = RetrofitClient.apiService

    /**
     * Get nearby users with detailed logging
     */
    suspend fun getNearbyUsers(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = 5000.0
    ): Result<NearbyUsersResponse> {
        return try {
            Log.d(TAG, "=== Fetching nearby users ===")
            Log.d(TAG, "  Lat: $latitude")
            Log.d(TAG, "  Lng: $longitude")
            Log.d(TAG, "  Radius: ${radiusMeters}m")

            val request = NearbyUsersRequest(latitude, longitude, radiusMeters)
            val response = api.getNearbyUsers(request)

            Log.d(TAG, "Response code: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d(TAG, "✅ SUCCESS!")
                Log.d(TAG, "  Total users found: ${body.total}")
                Log.d(TAG, "  Users list: ${body.users.map { it.displayName ?: it.userId }}")

                body.users.forEachIndexed { index, user ->
                    Log.d(TAG, "  User $index:")
                    Log.d(TAG, "    - Display Name: ${user.displayName}")
                    Log.d(TAG, "    - User ID: ${user.userId}")
                    Log.d(TAG, "    - Location: (${user.latitude}, ${user.longitude})")
                    Log.d(TAG, "    - Primary Area: ${user.primaryArea}")
                    Log.d(TAG, "    - All Areas: ${user.allMatchingAreas}")
                    Log.d(TAG, "    - Is On Edge: ${user.isOnEdge}")
                    Log.d(TAG, "    - Distance: ${user.distanceMeters}m")
                }

                Result.success(body)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "❌ FAILED!")
                Log.e(TAG, "  Error code: ${response.code()}")
                Log.e(TAG, "  Error message: ${response.message()}")
                Log.e(TAG, "  Error body: $errorMsg")

                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ EXCEPTION while fetching nearby users", e)
            Log.e(TAG, "  Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "  Exception message: ${e.message}")
            e.printStackTrace()

            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    /**
     * ✅ OPTIMIZED: Get count of reachable users/devices with performance tracking
     *
     * @param area Optional area filter
     * @param countByDevice If true, counts unique devices instead of users
     */
    suspend fun getReachableUsersCount(
        area: String? = null,
        countByDevice: Boolean = true,
        includeNearby: Boolean = true // FIXED: New parameter
    ): Result<Int> {
        val startTime = System.currentTimeMillis()
        return try {
            Log.d(TAG, "⏱️ [START] Fetching reachable count...")
            Log.d(TAG, "  Area filter: ${area ?: "all"}")
            Log.d(TAG, "  Count by device: $countByDevice")
            Log.d(TAG, "  Include nearby: $includeNearby")

            val apiStartTime = System.currentTimeMillis()
            val response = api.getReachableUsersCount(area, countByDevice, includeNearby)
            val apiDuration = System.currentTimeMillis() - apiStartTime

            Log.d(TAG, "⏱️ API call took ${apiDuration}ms")

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val count = body.count
                val totalDuration = System.currentTimeMillis() - startTime

                Log.d(TAG, "✅ SUCCESS! Count: $count")
                Log.d(TAG, "⏱️ TOTAL took ${totalDuration}ms")

                Result.success(count)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "❌ Failed: HTTP ${response.code()}: $errorMsg")

                // FIXED: Parse 429 rate limit error
                if (response.code() == 429) {
                    Result.failure(Exception("Rate limit exceeded. Please wait before refreshing again."))
                } else {
                    Result.failure(Exception("Error ${response.code()}: $errorMsg"))
                }
            }
        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ Exception (took ${totalDuration}ms)", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    /**
     * ✅ OPTIMIZED: Get count of reachable users/devices grouped by area
     *
     * @param countByDevice If true, counts unique devices per area
     * @return Map of area name to count
     */
    suspend fun getReachableUsersByArea(
        countByDevice: Boolean = true,
        includeNearby: Boolean = true // FIXED: New parameter
    ): Result<Map<String, Int>> {
        val startTime = System.currentTimeMillis()
        return try {
            Log.d(TAG, "⏱️ [START] Fetching by area...")

            val response = api.getReachableUsersByArea(countByDevice, includeNearby)
            val duration = System.currentTimeMillis() - startTime

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val areaCounts = body.areaCounts

                Log.d(TAG, "✅ SUCCESS! (took ${duration}ms)")
                Result.success(areaCounts)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "❌ Failed (took ${duration}ms): $errorMsg")

                if (response.code() == 429) {
                    Result.failure(Exception("Rate limit exceeded"))
                } else {
                    Result.failure(Exception("Error ${response.code()}: $errorMsg"))
                }
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ Exception (took ${duration}ms)", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    /**
     * Get user's current GPS location
     */
    suspend fun getMyGPSLocation(): Result<MyGPSLocationResponse> {
        return try {
            Log.d(TAG, "Fetching my GPS location from backend")
            val response = api.getMyGPSLocation()

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d(TAG, "✅ Got my GPS location")
                Log.d(TAG, "  Has location: ${body.hasLocation}")
                Log.d(TAG, "  Primary area: ${body.primaryArea}")
                Log.d(TAG, "  All matching areas: ${body.allMatchingAreas}")
                Log.d(TAG, "  Is on edge: ${body.isOnEdge}")
                Log.d(TAG, "  Nearby areas: ${body.nearbyAreas}")

                if (body.gpsLocation != null) {
                    Log.d(TAG, "  GPS coordinates: (${body.gpsLocation.latitude}, ${body.gpsLocation.longitude})")
                    Log.d(TAG, "  Accuracy: ${body.gpsLocation.accuracy}m")
                    Log.d(TAG, "  Last updated: ${body.gpsLocation.lastUpdated}")
                }

                Result.success(body)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "❌ Failed to get my GPS: $errorMsg")
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception getting my GPS location", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    /**
     * Get all users in a specific area
     */
    suspend fun getUsersInArea(
        areaName: String,
        includeEdgeUsers: Boolean = true
    ): Result<UsersInAreaResponse> {
        return try {
            Log.d(TAG, "Fetching users in area: $areaName")
            Log.d(TAG, "  Include edge users: $includeEdgeUsers")

            val response = api.getUsersInArea(areaName, includeEdgeUsers)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d(TAG, "✅ Found ${body.total} users in ${body.area}")
                Log.d(TAG, "  Include edge users: ${body.includeEdgeUsers}")

                body.users.forEachIndexed { index, user ->
                    Log.d(TAG, "  User $index:")
                    Log.d(TAG, "    - Display Name: ${user.displayName}")
                    Log.d(TAG, "    - User ID: ${user.userId}")
                    Log.d(TAG, "    - Primary Area: ${user.primaryArea}")
                    Log.d(TAG, "    - Is On Edge: ${user.isOnEdge}")
                }

                Result.success(body)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "❌ Failed: $errorMsg")
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception", e)
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
}