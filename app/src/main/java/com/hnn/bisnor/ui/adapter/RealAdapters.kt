package com.hnn.bisnor.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hnn.bisnor.data.model.RealMedia
import com.hnn.bisnor.databinding.ItemHeroBannerBinding
import com.hnn.bisnor.databinding.ItemMediaCardBinding
import com.hnn.bisnor.databinding.ItemStreamLinkBinding
import com.hnn.bisnor.data.model.RealSource

class RealHeroBannerAdapter(
    private val items: List<RealMedia>,
    private val onItemClick: (RealMedia) -> Unit
) : RecyclerView.Adapter<RealHeroBannerAdapter.HeroViewHolder>() {

    inner class HeroViewHolder(val binding: ItemHeroBannerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RealMedia) {
            val bannerUrl = if (item.cover.isNotEmpty()) item.cover else item.image
            binding.imgBackdrop.load(bannerUrl) {
                crossfade(true)
            }
            binding.tvBadgeRating.text = String.format("%.1f", item.imdb)
            binding.tvHeroTitle.text = item.title
            val genreNames = item.genres.take(2).joinToString("، ") { it.title }
            val subtitle = "${if (item.year > 0) item.year else ""}  •  $genreNames"
            binding.tvHeroGenreYear.text = subtitle

            binding.root.setOnClickListener { onItemClick(item) }
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

class RealMediaAdapter(
    private var items: List<RealMedia>,
    private val onItemClick: (RealMedia) -> Unit
) : RecyclerView.Adapter<RealMediaAdapter.MediaViewHolder>() {

    fun updateData(newItems: List<RealMedia>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class MediaViewHolder(val binding: ItemMediaCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RealMedia) {
            binding.imgPoster.load(item.image) {
                crossfade(true)
            }
            binding.tvRating.text = String.format("%.1f", item.imdb)
            binding.tvTitle.text = item.title
            val genre = item.genres.firstOrNull()?.title ?: ""
            binding.tvGenreYear.text = "${if (item.year > 0) item.year else ""} • $genre"

            // Smart Badge: Dubbed, 1080p, Series, Subtitle
            val titleLower = item.title.lowercase()
            val descLower = item.description.lowercase()
            val hasDubbed = titleLower.contains("دوبله") || descLower.contains("دوبله")
            val hasSub = titleLower.contains("زیرنویس") || descLower.contains("زیرنویس")

            binding.tvBadgeDubbed.visibility = View.VISIBLE
            when {
                hasDubbed -> {
                    binding.tvBadgeDubbed.text = "دوبله فارسی"
                    binding.tvBadgeDubbed.setBackgroundResource(com.hnn.bisnor.R.drawable.badge_background)
                }
                item.type == "serie" -> {
                    binding.tvBadgeDubbed.text = "سریال"
                }
                hasSub -> {
                    binding.tvBadgeDubbed.text = "زیرنویس"
                }
                else -> {
                    binding.tvBadgeDubbed.text = "1080p"
                }
            }

            binding.root.setOnClickListener { onItemClick(item) }
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

data class RealHomeSection(
    val title: String,
    val items: List<RealMedia>
)

class RealHomeSectionAdapter(
    private val sections: List<RealHomeSection>,
    private val onItemClick: (RealMedia) -> Unit
) : RecyclerView.Adapter<RealHomeSectionAdapter.SectionViewHolder>() {

    inner class SectionViewHolder(val binding: com.hnn.bisnor.databinding.ItemHomeSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(section: RealHomeSection) {
            binding.tvSectionTitle.text = section.title
            val mediaAdapter = RealMediaAdapter(section.items, onItemClick)
            binding.recyclerHorizontal.apply {
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                    context, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
                )
                adapter = mediaAdapter
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
        val binding = com.hnn.bisnor.databinding.ItemHomeSectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SectionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
        holder.bind(sections[position])
    }

    override fun getItemCount(): Int = sections.size
}

class RealSourceAdapter(
    private val sources: List<RealSource>,
    private val onPlayClick: (RealSource) -> Unit
) : RecyclerView.Adapter<RealSourceAdapter.SourceViewHolder>() {

    inner class SourceViewHolder(val binding: ItemStreamLinkBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(source: RealSource) {
            binding.tvLinkQuality.text = source.quality
            binding.tvLinkInfo.text = "فرمت: ${source.type.uppercase()}"
            binding.btnLinkPlay.setOnClickListener { onPlayClick(source) }
            binding.root.setOnClickListener { onPlayClick(source) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceViewHolder {
        val binding = ItemStreamLinkBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SourceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SourceViewHolder, position: Int) {
        holder.bind(sources[position])
    }

    override fun getItemCount(): Int = sources.size
}
