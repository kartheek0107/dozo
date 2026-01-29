package com.example.smallbasket

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import android.widget.ViewSwitcher
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.smallbasket.databinding.ActivityHomepageBinding
import com.example.smallbasket.location.*
import com.example.smallbasket.models.Order
import com.example.smallbasket.models.DeliveryRequest
import com.example.smallbasket.repository.OrderRepository
import com.example.smallbasket.repository.MapRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.util.Calendar

class Homepage : AppCompatActivity() {

    companion object {
        private const val TAG = "Homepage"
        private const val CONNECTIVITY_UPDATE_DELAY = 1500L
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityHomepageBinding
    private lateinit var activeRequestsContainer: LinearLayout
    private lateinit var onlineUsersSwitcher: ViewSwitcher
    private lateinit var vibrator: Vibrator

    private val orderRepository = OrderRepository()
    private val mapRepository = MapRepository()

    private lateinit var locationCoordinator: LocationTrackingCoordinator
    private lateinit var permissionManager: LocationPermissionManager
    private lateinit var connectivityManager: ConnectivityStatusManager

    private var isLoadingUsers = false
    private var deviceId: String = ""

    private val connectivityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val connected = intent?.getBooleanExtra("connected", false) ?: false
            Log.d(TAG, "📡 Connectivity changed: $connected")

            // ✅ CHANGED: Don't auto-refresh, just log the change
            if (!connected) {
                // Only update UI when going offline
                runOnUiThread {
                    binding.tvOnlineUsers.text = "0"
                    onlineUsersSwitcher.displayedChild = 1
                }
            }
            // When coming online, don't auto-refresh - wait for manual refresh
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "Permission result: $permissions")
        if (::permissionManager.isInitialized) {
            permissionManager.handlePermissionResult(permissions)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setupStatusBar()
            enableEdgeToEdge()

            auth = FirebaseAuth.getInstance()
            binding = ActivityHomepageBinding.inflate(layoutInflater)
            setContentView(binding.root)

            deviceId = getUniqueDeviceId()
            Log.d(TAG, "📱 Device ID: $deviceId")

            initializeHaptics()
            initializeLocationTracking()

            binding.tvGreeting.text = getGreeting()
            binding.tvUserName.text = updateName()
            setupButtonHaptics()
            setupCustomBottomNav()
            setupScrollListener()

            activeRequestsContainer = findViewById(R.id.activeRequestsContainer)
            onlineUsersSwitcher = findViewById(R.id.onlineUsersSwitcher)

            onlineUsersSwitcher.displayedChild = 0

            // ✅ FIX: Load data on startup
            loadTopTwoRequests()

            // Load online count after a short delay (let connectivity sync first)
            lifecycleScope.launch {
                delay(2000) // Wait 2 seconds for connectivity to establish
                refreshOnlineUserCount()
            }

            binding.root.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh).setOnRefreshListener {
                performLightHaptic(binding.root)
                refreshAllData()
            }

            try {
                binding.onlineUsersCard.setOnClickListener {
                    performSelectionHaptic(it)
                    showAreaUsersDialog()
                }
            } catch (e: Exception) {
                Log.w(TAG, "onlineUsersCard not found in layout", e)
            }

            // ✅ FIX: Android 13+ requires explicit receiver export flag
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(
                        connectivityReceiver,
                        IntentFilter("com.example.smallbasket.CONNECTIVITY_CHANGED"),
                        Context.RECEIVER_NOT_EXPORTED  // Our custom broadcast, not for other apps
                    )
                } else {
                    registerReceiver(
                        connectivityReceiver,
                        IntentFilter("com.example.smallbasket.CONNECTIVITY_CHANGED")
                    )
                }
                Log.d(TAG, "✅ Connectivity receiver registered")
            } catch (e: Exception) {
                Log.e(TAG, "Error registering connectivity receiver", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ FATAL: onCreate crashed", e)
            Toast.makeText(this, "Startup error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()

        try {
            if (::permissionManager.isInitialized) {
                if (!permissionManager.hasAllPermissions()) {
                    Log.d(TAG, "⚠️ Permissions missing on resume, requesting...")
                    requestPermissions()
                } else if (!LocationUtils.isLocationEnabled(this)) {
                    Log.d(TAG, "⚠️ Location services disabled, showing dialog...")
                    permissionManager.showLocationServicesDialog()
                } else {
                    // ✅ CHANGED: Only update connectivity, don't auto-refresh count
                    lifecycleScope.launch {
                        if (::connectivityManager.isInitialized) {
                            connectivityManager.forceUpdate()
                        }
                    }
                }
            }

            loadTopTwoRequests()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onResume", e)
        }
    }

    private fun getUniqueDeviceId(): String {
        return try {
            Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device ID", e)
            "unknown"
        }
    }

    private fun initializeHaptics() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun setupButtonHaptics() {
        binding.profileSection.setOnClickListener {
            performLightHaptic(it)
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.btnOrderNow.setOnClickListener {
            performMediumHaptic(it)
            val intent = Intent(this, OrderActivity::class.java)
            intent.putExtra("username", updateName())
            startActivity(intent)
        }

        binding.notification.setOnClickListener {
            performLightHaptic(it)
            startActivity(Intent(this, NotificationActivity::class.java))
        }
    }

    private fun performLightHaptic(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.CLOCK_TICK,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(10, 40))
        }
    }

    private fun performMediumHaptic(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.CONTEXT_CLICK,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(15, 80))
        }
    }

    private fun performSelectionHaptic(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.GESTURE_START
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(8, 50))
        }
    }

    private fun initializeLocationTracking() {
        Log.d(TAG, "=== Initializing Location Tracking ===")

        try {
            locationCoordinator = LocationTrackingCoordinator.getInstance(this)
            permissionManager = LocationPermissionManager(this)
            permissionManager.setPermissionLauncher(permissionLauncher)
            connectivityManager = ConnectivityStatusManager.getInstance(applicationContext)

            Log.d(TAG, "✅ Location components initialized")
            checkAndStartTracking()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing location tracking", e)
            Toast.makeText(this, "Location setup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndStartTracking() {
        if (!::permissionManager.isInitialized) return

        val hasPermissions = permissionManager.hasAllPermissions()
        val locationEnabled = LocationUtils.isLocationEnabled(this)

        Log.d(TAG, "Permissions: $hasPermissions, Location enabled: $locationEnabled")

        when {
            !hasPermissions -> {
                Log.d(TAG, "Requesting permissions")
                requestPermissions()
            }
            !locationEnabled -> {
                Log.w(TAG, "Location services disabled")
                permissionManager.showLocationServicesDialog()
            }
            else -> {
                Log.i(TAG, "✅ Starting tracking")
                startLocationTracking()
            }
        }
    }

    private fun requestPermissions() {
        if (!::permissionManager.isInitialized) return

        permissionManager.requestPermissions { granted ->
            Log.d(TAG, "Permission granted: $granted")
            if (granted && LocationUtils.isLocationEnabled(this)) {
                startLocationTracking()
            } else {
                Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startLocationTracking() {
        if (!::locationCoordinator.isInitialized || !::connectivityManager.isInitialized) return

        lifecycleScope.launch {
            try {
                Log.i(TAG, "STEP 1: Starting connectivity monitoring with device_id...")
                connectivityManager.startMonitoring()

                Log.i(TAG, "STEP 2: Waiting for connectivity sync...")
                delay(CONNECTIVITY_UPDATE_DELAY)

                Log.i(TAG, "STEP 3: Starting background location tracking...")
                locationCoordinator.startTracking()
                Log.d(TAG, "✅ Background tracking started")

                // ✅ CHANGED: Don't auto-refresh on startup
                // User must manually pull-to-refresh to see online count

//                Toast.makeText(this@Homepage, "Location tracking active ✅", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error starting tracking", e)
                Toast.makeText(this@Homepage, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshOnlineUserCount() {
        if (isLoadingUsers) {
            Log.d(TAG, "Already loading, skipping")
            return
        }

        runOnUiThread {
            if (onlineUsersSwitcher.displayedChild != 0) {
                onlineUsersSwitcher.displayedChild = 0
            }
        }

        isLoadingUsers = true
        Log.d(TAG, "=== Refreshing Online User Count ===")

        lifecycleScope.launch {
            try {
                val result = mapRepository.getReachableUsersCount(countByDevice = true)

                result.onSuccess { count ->
                    Log.d(TAG, "✅ SUCCESS! $count unique devices online")
                    runOnUiThread {
                        binding.tvOnlineUsers.text = count.toString()
                        onlineUsersSwitcher.displayedChild = 1
                    }

                    val message = if (count > 0) {
                        "$count users available"
                    } else {
                        "No users online"
                    }
                    Toast.makeText(this@Homepage, message, Toast.LENGTH_SHORT).show()
                }

                result.onFailure { error ->
                    Log.e(TAG, "❌ FAILED: ${error.message}")
                    runOnUiThread {
                        binding.tvOnlineUsers.text = "0"
                        onlineUsersSwitcher.displayedChild = 1
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception loading count", e)
                runOnUiThread {
                    binding.tvOnlineUsers.text = "0"
                    onlineUsersSwitcher.displayedChild = 1
                }
            } finally {
                isLoadingUsers = false
            }
        }
    }

    private fun refreshAllData() {
        val refreshLayout = binding.root.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        refreshLayout.isRefreshing = true

        if (isLoadingUsers) {
            Log.d(TAG, "Already loading, skipping refresh")
            refreshLayout.isRefreshing = false
            return
        }

        Log.d(TAG, "=== MANUAL REFRESH TRIGGERED ===")

        lifecycleScope.launch {
            try {
                if (::connectivityManager.isInitialized) {
                    connectivityManager.forceUpdate()
                    delay(CONNECTIVITY_UPDATE_DELAY)
                }

                refreshOnlineUserCount()
                loadTopTwoRequests()

            } catch (e: Exception) {
                Log.e(TAG, "Error during refresh", e)
                Toast.makeText(this@Homepage, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                refreshLayout.isRefreshing = false
            }
        }
    }

    private fun showAreaUsersDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_area_users, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rvAreaUsers)
        // ✅ FIX: btnClose is an ImageView, not a Button
        val btnClose = dialogView.findViewById<ImageView>(R.id.btnClose)
        val loadingText = dialogView.findViewById<TextView>(R.id.tvLoading)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.visibility = View.GONE
        loadingText.visibility = View.VISIBLE
        loadingText.text = "Loading area data..."

        lifecycleScope.launch {
            try {
                val result = mapRepository.getReachableUsersByArea(countByDevice = true)

                result.onSuccess { areaCounts ->
                    runOnUiThread {
                        if (areaCounts.isEmpty()) {
                            loadingText.text = "No users online in any area"
                        } else {
                            loadingText.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE

                            val areaList = areaCounts.map { it.key to it.value }
                                .sortedByDescending { it.second }

                            recyclerView.adapter = AreaUserAdapter(areaList)
                        }
                    }
                }

                result.onFailure { error ->
                    Log.e(TAG, "Error loading area data", error)
                    runOnUiThread {
                        loadingText.text = "Failed to load data: ${error.message}"
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception loading area data", e)
                runOnUiThread {
                    loadingText.text = "Error: ${e.message}"
                }
            }
        }

        btnClose.setOnClickListener {
            performLightHaptic(it)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun loadTopTwoRequests() {
        lifecycleScope.launch {
            try {
                val result = orderRepository.getAllOrders(status = "open")
                result.onSuccess { orders ->
                    runOnUiThread {
                        activeRequestsContainer.removeAllViews()
                        orders.take(2).forEach { order ->
                            // ✅ FIX: Safe null handling and include all order fields
                            val request = DeliveryRequest(
                                orderId = order.id,
                                title = order.items.joinToString(", "),
                                pickup = extractLocation(order.pickupLocation, order.pickupArea),
                                dropoff = extractLocation(order.dropLocation, order.dropArea),
                                fee = formatFee(order),
                                time = calculateTimeDisplay(order.deadline),
                                priority = isPriorityOrder(order.priority),
                                details = order.notes ?: "",
                                bestBefore = order.bestBefore ?: "",
                                deadline = order.deadline,
                                rewardPercentage = extractRewardPercentage(order),
                                itemPrice = order.item_price ?: 0.0,
                                pickupArea = order.pickupArea,
                                dropArea = order.dropArea,
                                status = order.status,
                                acceptorEmail = order.acceptorEmail,
                                acceptorName = order.acceptorName,
                                acceptorPhone = order.acceptorPhone,
                                requesterEmail = order.posterEmail,
                                requesterName = order.posterName,
                                requesterPhone = order.posterPhone
                            )
                            val cardView = layoutInflater.inflate(R.layout.item_active_request_card, activeRequestsContainer, false)
                            bindRequestToCard(cardView, request)
                            activeRequestsContainer.addView(cardView)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading requests", e)
            }
        }
    }

    private fun setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.apply {
                statusBarColor = Color.TRANSPARENT
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }

    private fun setupCustomBottomNav() {
        binding.navHome.setOnClickListener {
            performLightHaptic(it)
        }
        binding.navBrowse.setOnClickListener {
            performMediumHaptic(it)
            startActivity(Intent(this, RequestActivity::class.java))
        }
        binding.navActivity.setOnClickListener {
            performMediumHaptic(it)
            startActivity(Intent(this, MyLogsActivity::class.java))
        }
        binding.navProfile.setOnClickListener {
            performMediumHaptic(it)
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun setupScrollListener() {
        binding.scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (scrollY > 200) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility =
                        window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility =
                        window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
            }
        }
    }

    private fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    private fun updateName(): String {
        return auth.currentUser?.displayName ?: "User"
    }

    private fun extractLocation(location: String?, area: String?): String {
        return when {
            location.isNullOrBlank() && area.isNullOrBlank() -> "Unknown"
            location.isNullOrBlank() -> area!!
            area.isNullOrBlank() -> location
            else -> "$location, $area"
        }
    }

    // ✅ CRITICAL FIX: Handle nullable reward consistently
    private fun extractRewardPercentage(order: Order): Int = try {
        order.reward.toInt()
    } catch (e: Exception) {
        Log.e(TAG, "Error extracting reward", e)
        0
    }

    private fun formatFee(order: Order): String {
        val reward = extractRewardPercentage(order)
        return if (reward > 0) "₹$reward" else "₹0"
    }

    private fun isPriorityOrder(priority: String?): Boolean {
        return priority?.equals("emergency", ignoreCase = true) == true ||
                priority?.equals("high", ignoreCase = true) == true ||
                priority?.equals("urgent", ignoreCase = true) == true
    }

    private fun calculateTimeDisplay(deadline: String?): String {
        return when {
            deadline == null || deadline.isEmpty() -> "ASAP"
            deadline.contains("30") || deadline.contains("30m") -> "30 min"
            deadline.contains("1h") || deadline.contains("60") -> "1 hour"
            deadline.contains("2h") || deadline.contains("120") -> "2 hours"
            deadline.contains("4h") || deadline.contains("240") -> "4 hours"
            deadline.lowercase().contains("asap") -> "ASAP"
            else -> deadline
        }
    }

    private fun bindRequestToCard(cardView: View, request: DeliveryRequest) {
        cardView.findViewById<TextView>(R.id.tvRequestTitle).text = request.title
        cardView.findViewById<TextView>(R.id.tvPickupLocation).text = request.pickup
        cardView.findViewById<TextView>(R.id.tvDropoffLocation).text = request.dropoff

        cardView.findViewById<ImageView>(R.id.ivPickupIcon).setColorFilter(getColor(R.color.teal_500))
        cardView.findViewById<ImageView>(R.id.ivDropoffIcon).setColorFilter(getColor(R.color.teal_700))

        val viewLink = cardView.findViewById<TextView>(R.id.tvViewDetail)
        val clickHandler = View.OnClickListener {
            performSelectionHaptic(it)
            navigateToDetailFromHome(request)
        }
        viewLink.setOnClickListener(clickHandler)
        cardView.setOnClickListener(clickHandler)
    }

    private fun navigateToDetailFromHome(request: DeliveryRequest) {
        val intent = Intent(this, RequestDetailActivity::class.java).apply {
            putExtra("order_id", request.orderId)
            putExtra("title", request.title)
            putExtra("pickup", request.pickup)
            putExtra("drop", request.dropoff)
            putExtra("details", request.details)
            putExtra("priority", if (request.priority) "emergency" else "normal")
            putExtra("best_before", request.bestBefore)
            putExtra("deadline", request.deadline)
            putExtra("reward_percentage", request.rewardPercentage?.toDouble() ?: 0.0)
            putExtra("isImportant", request.priority)
            putExtra("fee", request.fee)
            putExtra("time", request.time)
            putExtra("item_price", request.itemPrice)

            // ✅ ADD ALL ORDER DATA to match RequestActivity
            putExtra("pickup_area", request.pickupArea)
            putExtra("drop_area", request.dropArea)
            putExtra("status", request.status)
            // ✅ ADD ACCEPTOR INFO
            putExtra("acceptor_email", request.acceptorEmail)
            putExtra("acceptor_name", request.acceptorName)
            putExtra("acceptor_phone", request.acceptorPhone)
            // ✅ ADD REQUESTER INFO
            putExtra("requester_email", request.requesterEmail)
            putExtra("requester_name", request.requesterName)
            putExtra("requester_phone", request.requesterPhone)
        }
        startActivity(intent)
    }

    private class AreaUserAdapter(private val areas: List<Pair<String, Int>>) :
        RecyclerView.Adapter<AreaUserAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val areaName: TextView = view.findViewById(R.id.tvAreaName)
            val userCount: TextView = view.findViewById(R.id.tvUserCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_area_user, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (area, count) = areas[position]
            holder.areaName.text = area
            holder.userCount.text = count.toString()
        }

        override fun getItemCount() = areas.size
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            unregisterReceiver(connectivityReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering connectivity receiver", e)
        }

        if (::connectivityManager.isInitialized) {
            connectivityManager.stopMonitoring()
        }
    }
}