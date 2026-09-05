package com.hnn.bisnor.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hnn.bisnor.data.model.MediaItem
import com.hnn.bisnor.databinding.ItemHeroBannerBinding

class HeroBannerAdapter(
    private val items: List<MediaItem>,
    private val onItemClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<HeroBannerAdapter.HeroViewHolder>() {

    inner class HeroViewHolder(val binding: ItemHeroBannerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MediaItem) {
            val bannerUrl = item.backdrop ?: item.poster
            binding.imgBackdrop.load(bannerUrl) {
                crossfade(true)
            }
            binding.tvBadgeRating.text = item.rating
            binding.tvHeroTitle.text = item.title
            val subtitle = "${item.year}  •  ${item.genres.take(2).joinToString("، ")}"
            binding.tvHeroGenreYear.text = subtitle

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeroViewHolder {
        val binding = ItemHeroBannerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HeroViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HeroViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
