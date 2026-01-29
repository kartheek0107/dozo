package com.example.smallbasket.models

import com.google.gson.annotations.SerializedName

// ============================================
// REQUEST MODELS
// ============================================

data class CreateOrderRequest(
    @SerializedName("item") val item: List<String>,
    @SerializedName("pickup_location") val pickupLocation: String,
    @SerializedName("pickup_area") val pickupArea: String,
    @SerializedName("drop_location") val dropLocation: String,
    @SerializedName("drop_area") val dropArea: String,
    @SerializedName("reward") val reward: Double? = null,
    @SerializedName("item_price") val itemPrice: Double,
    @SerializedName("time_requested") val timeRequested: String? = null,
    @SerializedName("deadline") val deadline: String,
    @SerializedName("priority") val priority: Boolean = false,
    @SerializedName("notes") val notes: String? = null

)

data class DeliveryRequest(
    val orderId: String,
    val title: String,
    val pickup: String,
    val dropoff: String,
    val fee: String,
    val time: String,
    val priority: Boolean,
    val details: String,
    val bestBefore: String,
    val deadline: String,
    val rewardPercentage: Int,
    val itemPrice: Double,
    val pickupArea: String? = null,
    val dropArea: String? = null,
    val status: String? = null,
    val acceptorEmail: String? = null,
    val acceptorName: String? = null,
    val acceptorPhone: String? = null,
    val requesterEmail: String? = null,
    val requesterName: String? = null,
    val requesterPhone: String? = null
)

data class AcceptOrderRequest(
    @SerializedName("request_id") val requestId: String
)

data class UpdateOrderStatusRequest(
    @SerializedName("request_id") val requestId: String,
    @SerializedName("status") val status: String  // "completed", "cancelled"
)

// ============================================
// RESPONSE MODELS
// ============================================

data class Order(
    @SerializedName("request_id") val id: String,
    @SerializedName("posted_by") val userId: String,
    @SerializedName("poster_email") val posterEmail: String,
    @SerializedName("poster_name") val posterName: String? = null,
    @SerializedName("poster_phone") val posterPhone: String? = null,
    @SerializedName("accepted_by") val delivererId: String? = null,
    @SerializedName("acceptor_email") val acceptorEmail: String? = null,
    @SerializedName("acceptor_name") val acceptorName: String? = null,
    @SerializedName("acceptor_phone") val acceptorPhone: String? = null,
    @SerializedName("item") val items: List<String>,
    @SerializedName("pickup_location") val pickupLocation: String,
    @SerializedName("pickup_area") val pickupArea: String,
    @SerializedName("drop_location") val dropLocation: String,
    @SerializedName("drop_area") val dropArea: String,
    @SerializedName("reward") val reward: Double,
    @SerializedName("item_price") val item_price: Double,
    @SerializedName("time_requested") val bestBefore: String,
    @SerializedName("deadline") val deadline: String,
    @SerializedName("priority") val priorityFlag: Boolean,
    @SerializedName("status") val status: String,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("accepted_at") val acceptedAt: String? = null,
    @SerializedName("completed_at") val completedAt: String? = null,
    @SerializedName("is_expired") val isExpired: Boolean = false
) {
    val priority: String
        get() = if (priorityFlag) "emergency" else "normal"
}

data class SuccessResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: Map<String, Any>? = null
)

data class RequestStatsResponse(
    @SerializedName("total_posted") val totalOrders: Int,
    @SerializedName("total_accepted") val completedDeliveries: Int,
    @SerializedName("active_requests") val activeOrders: Int
)

data class UserProfileResponse(
    @SerializedName("uid") val uid: String,
    @SerializedName("email") val email: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("email_verified") val emailVerified: Boolean,
    @SerializedName("preferred_areas") val preferredAreas: List<String>? = null,
    @SerializedName("current_area") val currentArea: String? = null,
    @SerializedName("is_reachable") val isReachable: Boolean = false,
    @SerializedName("is_connected") val isConnected: Boolean = false,
    @SerializedName("location_permission_granted") val locationPermissionGranted: Boolean = false,
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("last_login") val lastLogin: String
)

// ============================================
// CONNECTIVITY MODELS (WITH DEVICE TRACKING)
// ============================================

data class DeviceInfo(
    @SerializedName("os") val os: String? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("app_version") val appVersion: String? = null
)

data class ConnectivityUpdateRequest(
    @SerializedName("is_connected") val isConnected: Boolean,
    @SerializedName("location_permission_granted") val locationPermissionGranted: Boolean,
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("device_info") val deviceInfo: DeviceInfo? = null
)

// ============================================
// AREA MODELS
// ============================================

data class AreasListResponse(
    @SerializedName("areas") val areas: List<String>,
    @SerializedName("total") val total: Int
)

data class PreferredAreasRequest(
    @SerializedName("preferred_areas") val preferredAreas: List<String>
)

// ============================================
// NOTIFICATION MODELS
// ============================================

data class FCMTokenRequest(
    @SerializedName("fcm_token") val fcmToken: String
)

// ============================================
// LOCATION/GPS MODELS
// ============================================

data class GPSLocation(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy") val accuracy: Float? = null,
    @SerializedName("last_updated") val lastUpdated: String? = null
)

data class UpdateGPSLocationRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy") val accuracy: Float? = null,
    @SerializedName("fast_mode") val fastMode: Boolean = false
)

data class UpdateGPSLocationResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("fast_mode") val fastMode: Boolean,
    @SerializedName("data") val data: LocationUpdateData?
)

data class LocationUpdateData(
    @SerializedName("primary_area") val primaryArea: String?,
    @SerializedName("all_matching_areas") val allMatchingAreas: List<String>?,
    @SerializedName("is_on_edge") val isOnEdge: Boolean?,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)

data class MyGPSLocationResponse(
    @SerializedName("has_location") val hasLocation: Boolean,
    @SerializedName("gps_location") val gpsLocation: GPSLocation?,
    @SerializedName("primary_area") val primaryArea: String?,
    @SerializedName("all_matching_areas") val allMatchingAreas: List<String>?,
    @SerializedName("is_on_edge") val isOnEdge: Boolean?,
    @SerializedName("nearby_areas") val nearbyAreas: List<String>?
)

data class NearbyUsersRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("radius_meters") val radiusMeters: Double
)

data class NearbyUsersResponse(
    @SerializedName("total") val total: Int,
    @SerializedName("users") val users: List<MapUserData>
)

data class UsersInAreaResponse(
    @SerializedName("area") val area: String,
    @SerializedName("total") val total: Int,
    @SerializedName("include_edge_users") val includeEdgeUsers: Boolean,
    @SerializedName("users") val users: List<MapUserData>
)

data class MapUserData(
    @SerializedName("user_id") val userId: String,
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("primary_area") val primaryArea: String?,
    @SerializedName("all_matching_areas") val allMatchingAreas: List<String>?,
    @SerializedName("is_on_edge") val isOnEdge: Boolean?,
    @SerializedName("distance_meters") val distanceMeters: Double?,
    @SerializedName("last_updated") val lastUpdated: String?
)

// ============================================
// REACHABLE COUNT MODELS (DEVICE TRACKING)
// ============================================

data class ReachableCountResponse(
    @SerializedName("count") val count: Int,
    @SerializedName("counting_method") val countingMethod: String,
    @SerializedName("area") val area: String,
    @SerializedName("message") val message: String
)

data class ReachableByAreaResponse(
    @SerializedName("area_counts") val areaCounts: Map<String, Int>,
    @SerializedName("counting_method") val countingMethod: String,
    @SerializedName("note") val note: String
)

// ============================================
// RATING MODELS
// ============================================

data class CreateRatingRequest(
    @SerializedName("request_id") val requestId: String,
    @SerializedName("rating") val rating: Int,
    @SerializedName("comment") val comment: String? = null
)

data class UpdateRatingRequest(
    @SerializedName("rating") val rating: Int,
    @SerializedName("comment") val comment: String? = null
)

data class RatingResponse(
    @SerializedName("rating_id") val ratingId: String,
    @SerializedName("request_id") val requestId: String,
    @SerializedName("poster_uid") val posterUid: String,
    @SerializedName("deliverer_uid") val delivererUid: String,
    @SerializedName("rating") val rating: Int,
    @SerializedName("comment") val comment: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String?
)

data class RatingStatsResponse(
    @SerializedName("average_rating") val averageRating: Double,
    @SerializedName("total_ratings") val totalRatings: Int,
    @SerializedName("rating_distribution") val ratingDistribution: Map<String, Int>,
    @SerializedName("rating_badge") val ratingBadge: String?
)

data class UserRatingsResponse(
    @SerializedName("stats") val stats: RatingStatsResponse,
    @SerializedName("ratings") val ratings: List<Map<String, Any>>
)

// ============================================
// GENERIC API RESPONSE WRAPPER
// ============================================

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: T? = null
)

// ============================================
// COMPATIBILITY ALIASES
// ============================================

typealias UserStats = RequestStatsResponse
