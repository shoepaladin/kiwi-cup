package com.example.wallpaperrotator

import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Scale
import com.google.android.material.card.MaterialCardView

class WallpaperAdapter(
    private val onItemClick: (WallpaperConfig) -> Unit,
    private val onItemLongClick: (WallpaperConfig) -> Boolean,
    private val isSelected: (Long) -> Boolean
) : RecyclerView.Adapter<WallpaperAdapter.ViewHolder>() {

    private var configs = listOf<WallpaperConfig>()
    private var selectionMode = false

    fun submitList(newConfigs: List<WallpaperConfig>) {
        val diffCallback = WallpaperDiffCallback(configs, newConfigs)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        configs = newConfigs
        diffResult.dispatchUpdatesTo(this)
    }

    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wallpaper_grid, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(configs[position])
    }

    override fun getItemCount() = configs.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.card)
        private val thumbnail: ImageView = itemView.findViewById(R.id.thumbnail)
        private val selectionOverlay: View = itemView.findViewById(R.id.selectionOverlay)
        private val selectionCheck: CheckBox = itemView.findViewById(R.id.selectionCheck)
        private val screenTypeIcon: ImageView = itemView.findViewById(R.id.screenTypeIcon)
        private val rotationBadge: TextView = itemView.findViewById(R.id.rotationBadge)

        fun bind(config: WallpaperConfig) {
            // Load image with Coil
            try {
                val uri = Uri.parse(config.imageUri)
                thumbnail.load(uri) {
                    crossfade(true)
                    scale(Scale.FILL)
                    error(R.drawable.ic_broken_image)
                    placeholder(R.drawable.ic_image_placeholder)
                }
            } catch (e: Exception) {
                thumbnail.setImageResource(R.drawable.ic_broken_image)
            }

            // Selection state
            val selected = isSelected(config.id)
            selectionCheck.isChecked = selected
            selectionOverlay.visibility = if (selectionMode) View.VISIBLE else View.GONE
            card.strokeWidth = if (selected) 8 else 0
            card.strokeColor = if (selected) 
                itemView.context.getColor(R.color.selection_stroke) 
            else Color.TRANSPARENT

            // Screen type indicator
            when {
                config.forHomeScreen && config.forLockScreen -> {
                    screenTypeIcon.setImageResource(R.drawable.ic_both_screens)
                    screenTypeIcon.visibility = View.VISIBLE
                }
                config.forHomeScreen -> {
                    screenTypeIcon.setImageResource(R.drawable.ic_home_screen)
                    screenTypeIcon.visibility = View.VISIBLE
                }
                config.forLockScreen -> {
                    screenTypeIcon.setImageResource(R.drawable.ic_lock_screen)
                    screenTypeIcon.visibility = View.VISIBLE
                }
                else -> screenTypeIcon.visibility = View.GONE
            }

            // Rotation badge
            if (config.rotation != 0f) {
                rotationBadge.text = "${config.rotation.toInt()}°"
                rotationBadge.visibility = View.VISIBLE
            } else {
                rotationBadge.visibility = View.GONE
            }

            // Click listeners
            itemView.setOnClickListener {
                if (selectionMode) {
                    onItemLongClick(config)
                } else {
                    onItemClick(config)
                }
            }

            itemView.setOnLongClickListener {
                onItemLongClick(config)
            }

            selectionCheck.setOnClickListener {
                onItemLongClick(config)
            }
        }
    }

    private class WallpaperDiffCallback(
        private val oldList: List<WallpaperConfig>,
        private val newList: List<WallpaperConfig>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldList[oldItemPosition].id == newList[newItemPosition].id
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldList[oldItemPosition] == newList[newItemPosition]
    }
}
