package com.smallbasket.dozo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smallbasket.dozo.models.Order
import com.smallbasket.dozo.utils.TimeUtils

class MyLogsAdapter(
    private var orders: List<Order>,
    private val isMyOrders: Boolean = false,
    private val onItemClick: (Order) -> Unit = {}
) : RecyclerView.Adapter<MyLogsAdapter.LogViewHolder>() {

    inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvItems = itemView.findViewById<TextView>(R.id.tvLogItems)
        val tvPickup = itemView.findViewById<TextView>(R.id.tvLogPickup)
        val tvDrop = itemView.findViewById<TextView>(R.id.tvLogDrop)
        val tvStatus = itemView.findViewById<TextView>(R.id.tvLogStatus)
        val tvDate = itemView.findViewById<TextView>(R.id.tvLogDate)
        val tvReward = itemView.findViewById<TextView>(R.id.tvLogReward)
        val btnView = itemView.findViewById<View>(R.id.btnViewLog)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_my_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val order = orders[position]

        holder.tvItems.text = order.items.joinToString(", ")

        // Safely display pickup/drop areas
        holder.tvPickup.text = order.pickupArea ?: "Unknown"
        holder.tvDrop.text = order.dropArea ?: "Unknown"

        // ✅ Use 'reward' (from backend) instead of 'rewardPercentage'
        // Fallback to 0 if null or invalid
        // If isMyOrders is true, show (item_price + reward)
        val displayAmount = if (isMyOrders) {
            val itemPrice = order.item_price ?: 0.0
            val reward = order.reward ?: 0.0
            (itemPrice + reward).toInt()
        } else {
            (order.reward ?: 0.0).toInt()
        }
        
        holder.tvReward.text = "₹$displayAmount"

        // Format status with color and background tint
        val status = order.status?.lowercase() ?: "unknown"
        holder.tvStatus.text = status.replaceFirstChar { it.uppercase() }

        val (textColor, bgColor) = when (status) {
            "open" -> Pair("#D97706", "#FEF3C7")      // Amber
            "accepted" -> Pair("#2563EB", "#DBEAFE")  // Blue
            "completed" -> Pair("#059669", "#D1FAE5") // Green
            "cancelled" -> Pair("#DC2626", "#FEE2E2") // Red
            else -> Pair("#4B5563", "#F3F4F6")        // Gray
        }
        
        holder.tvStatus.setTextColor(android.graphics.Color.parseColor(textColor))
        holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(bgColor))

        // Format date
        holder.tvDate.text = order.createdAt?.let { TimeUtils.formatDateTime(it) } ?: "Unknown"

        // Click listeners
        val clickListener = View.OnClickListener { onItemClick(order) }
        holder.itemView.setOnClickListener(clickListener)
        holder.btnView.setOnClickListener(clickListener)
    }

    override fun getItemCount(): Int = orders.size

    fun updateData(newOrders: List<Order>) {
        orders = newOrders
        notifyDataSetChanged()
    }
}