package com.example.smallbasket.api

import com.example.smallbasket.models.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ============================================
    // REQUEST ENDPOINTS
    // ============================================

    @POST("request/create")
    suspend fun createOrder(@Body request: CreateOrderRequest): Response<Order>

    @GET("request/all")
    suspend fun getAllOrders(
        @Query("status") status: String? = null,
        @Query("pickup_area") pickupArea: String? = null,
        @Query("drop_area") dropArea: String? = null
    ): Response<List<Order>>

    @GET("request/mine")
    suspend fun getUserOrders(): Response<List<Order>>

    @GET("request/accepted")
    suspend fun getAcceptedOrders(): Response<List<Order>>

    @GET("request/status/{request_id}")
    suspend fun getOrder(@Path("request_id") orderId: String): Response<Order>

    @POST("request/accept")
    suspend fun acceptOrder(@Body request: AcceptOrderRequest): Response<Order>

    @POST("request/update-status")
    suspend fun updateOrderStatus(@Body request: UpdateOrderStatusRequest): Response<Order>

    // ============================================
    // USER ENDPOINTS
    // ============================================

    @GET("user/stats")
    suspend fun getUserStats(): Response<RequestStatsResponse>

    @GET("user/profile")
    suspend fun getUserProfile(): Response<UserProfileResponse>

    // ============================================
    // CONNECTIVITY ENDPOINTS (WITH DEVICE TRACKING)
    // ============================================

    @POST("user/connectivity/update")
    suspend fun updateConnectivity(
        @Body request: ConnectivityUpdateRequest
    ): Response<SuccessResponse>

    // ============================================
    // AREA ENDPOINTS
    // ============================================

    @GET("areas/list")
    suspend fun getAvailableAreas(): Response<AreasListResponse>

    @PUT("user/preferred-areas")
    suspend fun setPreferredAreas(@Body request: PreferredAreasRequest): Response<SuccessResponse>

    // ============================================
    // NOTIFICATION ENDPOINTS
    // ============================================

    @POST("notifications/register")
    suspend fun registerFCMToken(@Body request: FCMTokenRequest): Response<SuccessResponse>

    @DELETE("notifications/unregister")
    suspend fun unregisterFCMToken(): Response<SuccessResponse>

    // ============================================
    // HEALTH CHECK
    // ============================================

    @GET("/")
    suspend fun healthCheck(): Response<Map<String, Any>>

    // ============================================
    // LOCATION/MAP ENDPOINTS
    // ============================================

    /**
     * Update user's GPS location
     */
    @POST("location/update-gps")
    suspend fun updateGPSLocation(
        @Body request: UpdateGPSLocationRequest
    ): Response<UpdateGPSLocationResponse>

    /**
     * Get user's current GPS location
     */
    @GET("location/my-gps")
    suspend fun getMyGPSLocation(): Response<MyGPSLocationResponse>

    /**
     * Get nearby users within specified radius
     */
    @POST("location/nearby-users")
    suspend fun getNearbyUsers(
        @Body request: NearbyUsersRequest
    ): Response<NearbyUsersResponse>

    /**
     * Get all users in a specific area
     */
    @GET("location/users-in-area/{area_name}")
    suspend fun getUsersInArea(
        @Path("area_name") areaName: String,
        @Query("include_edge_users") includeEdgeUsers: Boolean = true
    ): Response<UsersInAreaResponse>

    // ============================================
    // REACHABLE USERS COUNT (WITH DEVICE TRACKING)
    // ============================================

    /**
     * Get count of reachable users or unique devices
     *
     * Example URLs:
     * - GET /users/reachable-count
     * - GET /users/reachable-count?area=SBIT
     * - GET /users/reachable-count?area=SBIT&count_by_device=true
     */
    @GET("users/reachable-count")
    suspend fun getReachableUsersCount(
        @Query("area") area: String? = null,
        @Query("count_by_device") countByDevice: Boolean = true
    ): Response<ReachableCountResponse>

    /**
     * Get count of reachable users/devices grouped by all areas
     *
     * Example URLs:
     * - GET /users/reachable-by-area
     * - GET /users/reachable-by-area?count_by_device=true
     */
    @GET("users/reachable-by-area")
    suspend fun getReachableUsersByArea(
        @Query("count_by_device") countByDevice: Boolean = true
    ): Response<ReachableByAreaResponse>

    // ============================================
    // RATING ENDPOINTS
    // ============================================

    /**
     * Create a rating for a completed delivery
     */
    @POST("rating/create")
    suspend fun createRating(@Body request: CreateRatingRequest): Response<RatingResponse>

    /**
     * Update an existing rating
     */
    @PUT("rating/{rating_id}")
    suspend fun updateRating(
        @Path("rating_id") ratingId: String,
        @Body request: UpdateRatingRequest
    ): Response<RatingResponse>

    /**
     * Get all ratings received by a user as deliverer
     */
    @GET("rating/deliverer/{user_uid}")
    suspend fun getDelivererRatings(
        @Path("user_uid") userUid: String
    ): Response<UserRatingsResponse>

    /**
     * Get current user's ratings as deliverer
     */
    @GET("rating/my-deliverer-ratings")
    suspend fun getMyDelivererRatings(): Response<UserRatingsResponse>

    /**
     * Get user's rating summary
     */
    @GET("rating/summary/{user_uid}")
    suspend fun getRatingSummary(
        @Path("user_uid") userUid: String
    ): Response<RatingStatsResponse>

    /**
     * Get current user's rating summary
     */
    @GET("rating/my-summary")
    suspend fun getMyRatingSummary(): Response<RatingStatsResponse>

    /**
     * Delete a rating
     */
    @DELETE("rating/{rating_id}")
    suspend fun deleteRating(
        @Path("rating_id") ratingId: String
    ): Response<SuccessResponse>
}