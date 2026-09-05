package com.hnn.bisnor.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hnn.bisnor.data.model.MediaItem
import com.hnn.bisnor.databinding.ItemMediaCardBinding

class MediaCardAdapter(
    private var items: List<MediaItem>,
    private val onItemClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaCardAdapter.MediaViewHolder>() {

    fun updateData(newItems: List<MediaItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class MediaViewHolder(val binding: ItemMediaCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MediaItem) {
            binding.imgPoster.load(item.poster) {
                crossfade(true)
            }
            binding.tvRating.text = item.rating
            binding.tvTitle.text = item.title
            val subtitle = "${item.year} • ${item.genres.firstOrNull() ?: ""}"
            binding.tvGenreYear.text = subtitle

            binding.tvBadgeDubbed.visibility = if (item.isDubbed) View.VISIBLE else View.GONE

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
