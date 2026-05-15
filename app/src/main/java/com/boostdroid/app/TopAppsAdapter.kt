package com.boostdroid.app

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView

class TopAppsAdapter : RecyclerView.Adapter<TopAppsAdapter.ViewHolder>() {
    
    private val items = mutableListOf<AppRamInfo>()
    private val previousRam = HashMap<Int, Int>() // pid → previous MB
    private var maxRam = 1

    fun createLetterAvatar(context: Context, letter: Char, colorSeed: String): Drawable {
        // Create a simple colored circle with the first letter
        // Use colorSeed.hashCode() to pick a deterministic color from a palette
        val colors = intArrayOf(
            0xFF7C6FFF.toInt(), 0xFF00C896.toInt(), 0xFFFFB547.toInt(),
            0xFF29B6F6.toInt(), 0xFFFF5252.toInt(), 0xFF9C6FFF.toInt()
        )
        val bgColor = colors[Math.abs(colorSeed.hashCode()) % colors.size]
        
        return object : Drawable() {
            override fun draw(canvas: android.graphics.Canvas) {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                // Draw circle
                paint.color = bgColor
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val r = bounds.width() / 2f
                canvas.drawCircle(cx, cy, r, paint)
                // Draw letter
                paint.color = 0xFFFFFFFF.toInt()
                paint.textSize = r * 1.1f
                paint.textAlign = android.graphics.Paint.Align.CENTER
                paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                val textY = cy - (paint.descent() + paint.ascent()) / 2
                canvas.drawText(letter.toString(), cx, textY, paint)
            }
            override fun setAlpha(alpha: Int) { }
            override fun setColorFilter(cf: android.graphics.ColorFilter?) { }
            @Deprecated("Deprecated in Java")
            override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
    }
    
    fun updateList(newItems: List<AppRamInfo>) {
        maxRam = newItems.maxOfOrNull { it.ramMb }?.coerceAtLeast(1) ?: 1
        
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(old: Int, new: Int) = 
                items[old].pid == newItems[new].pid
            override fun areContentsTheSame(old: Int, new: Int): Boolean {
                val o = items[old]; val n = newItems[new]
                return o.ramMb == n.ramMb && o.displayName == n.displayName
            }
        })

        // Calculate deltas before updating items
        newItems.forEach { item ->
            // Delta calculation is handled in onBind
        }

        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ram_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val prev = previousRam[item.pid]
        val delta = if (prev != null) item.ramMb - prev else 0
        previousRam[item.pid] = item.ramMb
        
        holder.bind(item, delta, maxRam, ::createLetterAvatar)
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivIcon = view.findViewById<ShapeableImageView>(R.id.ivAppIcon)
        private val tvName = view.findViewById<TextView>(R.id.tvAppName)
        private val tvMem = view.findViewById<TextView>(R.id.tvAppMem)
        private val pb = view.findViewById<ProgressBar>(R.id.pbAppMem)
        private val tvDelta = view.findViewById<TextView>(R.id.tvAppDelta)
        
        fun bind(item: AppRamInfo, delta: Int, maxRam: Int, avatarFactory: (Context, Char, String) -> Drawable) {
            if (item.icon != null) {
                ivIcon.setImageDrawable(item.icon)
            } else {
                val letter = item.displayName.firstOrNull()?.uppercaseChar() ?: '?'
                ivIcon.setImageDrawable(
                    avatarFactory(itemView.context, letter, item.packageName)
                )
            }
            tvName.text = item.displayName
            tvMem.text = "${item.ramMb} MB"
            pb.max = maxRam
            pb.progress = item.ramMb
            
            if (delta > 5) {
                tvDelta.visibility = View.VISIBLE
                tvDelta.text = "▲ ${delta}MB"
                tvDelta.setTextColor(ContextCompat.getColor(itemView.context, R.color.colorRed))
                hideDeltaDelayed()
            } else if (delta < -5) {
                tvDelta.visibility = View.VISIBLE
                tvDelta.text = "▼ ${-delta}MB"
                tvDelta.setTextColor(ContextCompat.getColor(itemView.context, R.color.colorGreen))
                hideDeltaDelayed()
            } else {
                tvDelta.visibility = View.GONE
            }
        }
        
        private fun hideDeltaDelayed() {
            tvDelta.postDelayed({
                tvDelta.visibility = View.GONE
            }, 3000)
        }
    }
}
