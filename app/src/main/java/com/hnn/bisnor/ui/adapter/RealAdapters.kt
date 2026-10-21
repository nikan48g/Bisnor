package com.hnn.bisnor.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hnn.bisnor.R
import com.hnn.bisnor.data.model.RealMedia
import com.hnn.bisnor.data.model.RealSource
import com.hnn.bisnor.databinding.ItemHeroBannerBinding
import com.hnn.bisnor.databinding.ItemMediaCardBinding
import com.hnn.bisnor.databinding.ItemStreamLinkBinding
import com.hnn.bisnor.util.ImageUrlHelper
import java.util.Locale

/** Hero carousel card. Artwork, copy and metadata are bound from the live catalogue only. */
class RealHeroBannerAdapter(
    private val items: List<RealMedia>,
    private val onDetailsClick: (RealMedia) -> Unit,
    private val onPlayClick: (RealMedia) -> Unit,
    private val onSaveClick: (RealMedia) -> Unit
) : RecyclerView.Adapter<RealHeroBannerAdapter.HeroViewHolder>() {

    inner class HeroViewHolder(val binding: ItemHeroBannerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RealMedia) {
            val rawBanner = if (item.cover.isNotEmpty()) item.cover else item.image
            binding.imgBackdrop.load(ImageUrlHelper.getOptimizedImageUrl(rawBanner)) {
                crossfade(true)
            }

            binding.tvBadgeRating.text = String.format(Locale.US, "%.1f", item.imdb)
            binding.tvBadgeRating.visibility = if (item.imdb > 0.0) View.VISIBLE else View.GONE

            binding.tvHeroTitle.text = item.title
            binding.tvHeroType.text = if (item.isSeries) "سریال" else "فیلم"

            val genreNames = item.genres.take(3).joinToString("، ") { it.title }
            val parts = mutableListOf<String>()
            if (item.year > 0) parts.add(item.year.toString())
            if (genreNames.isNotEmpty()) parts.add(genreNames)
            binding.tvHeroGenreYear.text = parts.joinToString("  •  ")

            val desc = com.hnn.bisnor.util.MediaMetadataHelper
                .parse(item.description, item.imdb, isSeries = item.isSeries).cleanStoryline
            binding.tvHeroDesc.text = desc
            binding.tvHeroDesc.visibility = if (desc.isNotBlank()) View.VISIBLE else View.GONE

            binding.btnHeroPlay.setOnClickListener { onPlayClick(item) }
            binding.btnHeroDetails.setOnClickListener { onDetailsClick(item) }
            binding.btnHeroSave.setOnClickListener { onSaveClick(item) }
            binding.root.setOnClickListener { onDetailsClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeroViewHolder {
        val binding = ItemHeroBannerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HeroViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HeroViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size
}

/** Poster card used in horizontal rails and the search grid. */
class RealMediaAdapter(
    private var items: List<RealMedia>,
    private val onItemClick: (RealMedia) -> Unit,
    private val isGrid: Boolean = false
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun updateData(newItems: List<RealMedia>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class MediaViewHolder(val binding: ItemMediaCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RealMedia) {
            bindArtwork(binding.imgPoster, item)
            binding.tvRating.text = String.format(Locale.US, "%.1f", item.imdb)
            binding.tvRating.visibility = if (item.imdb > 0.0) View.VISIBLE else View.GONE
            binding.tvTitle.text = item.title
            val genre = item.genres.firstOrNull()?.title ?: ""
            binding.tvGenreYear.text = listOfNotNull(
                item.year.takeIf { it > 0 }?.toString(),
                genre.takeIf { it.isNotBlank() }
            ).joinToString(" • ")
            bindBadge(binding.tvBadgeDubbed, item)
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    inner class GridMediaViewHolder(val binding: com.hnn.bisnor.databinding.ItemMediaCardGridBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RealMedia) {
            bindArtwork(binding.imgPoster, item)
            binding.tvRating.text = String.format(Locale.US, "%.1f", item.imdb)
            binding.tvRating.visibility = if (item.imdb > 0.0) View.VISIBLE else View.GONE
            binding.tvTitle.text = item.title
            val genre = item.genres.firstOrNull()?.title ?: ""
            binding.tvGenreYear.text = listOfNotNull(
                item.year.takeIf { it > 0 }?.toString(),
                genre.takeIf { it.isNotBlank() }
            ).joinToString(" • ")
            bindBadge(binding.tvBadgeDubbed, item)
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    private fun bindArtwork(view: android.widget.ImageView, item: RealMedia) {
        val rawImg = if (item.image.isNotEmpty()) item.image else item.cover
        view.load(ImageUrlHelper.getOptimizedImageUrl(rawImg)) {
            crossfade(true)
            placeholder(R.drawable.bg_skeleton)
            error(R.drawable.ic_movie_placeholder)
        }
    }

    /** Only claims a badge the metadata actually supports. */
    private fun bindBadge(view: android.widget.TextView, item: RealMedia) {
        val haystack = (item.title + " " + item.description).lowercase()
        when {
            haystack.contains("دوبله") -> {
                view.text = "دوبله"
                view.visibility = View.VISIBLE
            }
            item.isSeries -> {
                view.text = "سریال"
                view.visibility = View.VISIBLE
            }
            haystack.contains("زیرنویس") -> {
                view.text = "زیرنویس"
                view.visibility = View.VISIBLE
            }
            else -> view.visibility = View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (isGrid) {
            val binding = com.hnn.bisnor.databinding.ItemMediaCardGridBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            GridMediaViewHolder(binding)
        } else {
            val binding = ItemMediaCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            MediaViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is GridMediaViewHolder -> holder.bind(item)
            is MediaViewHolder -> holder.bind(item)
        }
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
            binding.recyclerHorizontal.apply {
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                    context, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
                )
                adapter = RealMediaAdapter(section.items, onItemClick)
                itemAnimator = null
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
        val binding = com.hnn.bisnor.databinding.ItemHomeSectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SectionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) = holder.bind(sections[position])

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
            binding.tvLinkInfo.text = when {
                source.type.isNotBlank() && source.type != "null" -> "فرمت ${source.type.uppercase()}"
                else -> ""
            }
            binding.btnLinkDownload.visibility = View.GONE
            binding.btnLinkPlay.setOnClickListener { onPlayClick(source) }
            binding.root.setOnClickListener { onPlayClick(source) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceViewHolder {
        val binding = ItemStreamLinkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SourceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SourceViewHolder, position: Int) = holder.bind(sources[position])

    override fun getItemCount(): Int = sources.size
}
