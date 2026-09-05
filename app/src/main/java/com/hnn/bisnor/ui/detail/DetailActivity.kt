package com.hnn.bisnor.ui.detail

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hnn.bisnor.R
import com.hnn.bisnor.data.model.RealMedia
import com.hnn.bisnor.data.model.RealSeason
import com.hnn.bisnor.data.model.RealSource
import com.hnn.bisnor.data.repository.FavoritesManager
import com.hnn.bisnor.data.repository.PlaybackHistoryManager
import com.hnn.bisnor.data.repository.PlaybackProgress
import com.hnn.bisnor.data.repository.PlaylistsManager
import com.hnn.bisnor.data.repository.RealMediaRepository
import com.hnn.bisnor.databinding.ActivityDetailBinding
import com.hnn.bisnor.databinding.ItemDetailFooterBinding
import com.hnn.bisnor.databinding.ItemDetailHeaderBinding
import com.hnn.bisnor.databinding.ItemStreamLinkBinding
import com.hnn.bisnor.ui.adapter.RealMediaAdapter
import com.hnn.bisnor.util.DownloadHelper
import com.hnn.bisnor.util.PlayerLauncherHelper
import com.hnn.bisnor.util.ThemeHelper
import kotlinx.coroutines.launch

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var playlistsManager: PlaylistsManager
    private lateinit var historyManager: PlaybackHistoryManager
    private var mediaItem: RealMedia? = null
    private val sourcesList = mutableListOf<RealSource>()
    private val allSeasons = mutableListOf<RealSeason>()
    private var selectedSeasonIndex = 0
    private val similarList = mutableListOf<RealMedia>()
    private var trailerUrl: String? = null
    private lateinit var detailAdapter: DetailRecyclerAdapter
    private var sectionTitle = "کیفیت‌های پخش و تماشا"

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        favoritesManager = FavoritesManager(this)
        playlistsManager = PlaylistsManager(this)
        historyManager = PlaybackHistoryManager(this)
        mediaItem = intent.getSerializableExtra("real_media") as? RealMedia

        if (mediaItem == null) {
            finish()
            return
        }

        binding.toolbarDetail.setNavigationOnClickListener { finish() }
        binding.toolbarDetail.title = mediaItem!!.title

        detailAdapter = DetailRecyclerAdapter()
        binding.recyclerDetailContent.apply {
            layoutManager = LinearLayoutManager(this@DetailActivity)
            adapter = detailAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }

        checkContentWarning(mediaItem!!)
        loadData()
    }

    private fun checkContentWarning(item: RealMedia) {
        if (!historyManager.isContentWarningEnabled || isFinishing || isDestroyed) return

        val desc = item.description
        val isAdultOr16 = desc.contains("16") || desc.contains("18") || desc.contains("بزرگسال") || desc.contains("+16") || desc.contains("+18")
        if (isAdultOr16) {
            try {
                MaterialAlertDialogBuilder(this)
                    .setTitle("⚠️ هشدار رده‌بندی سنی")
                    .setMessage("این اثر ممکن است شامل صحنه‌ها یا موضوعات مناسب افراد بالای ۱۶ یا ۱۸ سال باشد.\nآیا مایل به ادامه هستید؟")
                    .setPositiveButton("ادامه و تماشا") { dialog, _ -> dialog.dismiss() }
                    .setNegativeButton("بازگشت") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override fun onResume() {
        super.onResume()
        detailAdapter.notifyDataSetChanged()
    }

    private fun loadData() {
        val item = mediaItem ?: return

        val trailerSource = item.sources.find { it.quality.contains("تیزر") || it.quality.contains("Trailer", ignoreCase = true) || it.url.contains("tizer") }
        trailerUrl = trailerSource?.url

        lifecycleScope.launch {
            try {
                val genre = item.genres.firstOrNull()?.title
                if (!genre.isNullOrEmpty()) {
                    val results = RealMediaRepository.search(genre)
                    similarList.clear()
                    similarList.addAll(results.filter { it.id != item.id }.take(10))
                    if (!isFinishing && !isDestroyed) {
                        detailAdapter.notifyDataSetChanged()
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        if (item.type == "serie") {
            sectionTitle = "در حال دریافت فصل‌ها و قسمت‌ها..."
            detailAdapter.notifyItemChanged(0)

            lifecycleScope.launch {
                try {
                    val seasons = RealMediaRepository.getSeriesSeasons(item.id)
                    allSeasons.clear()
                    allSeasons.addAll(seasons)
                    selectedSeasonIndex = 0
                    updateSeasonEpisodes()
                } catch (e: Exception) {
                    sectionTitle = "خطا در بارگذاری قسمت‌ها"
                    if (!isFinishing && !isDestroyed) {
                        detailAdapter.notifyDataSetChanged()
                    }
                }
            }
        } else {
            sourcesList.clear()
            for (src in item.sources) {
                if (src.quality.contains("تیزر") || src.url.contains("tizer")) continue
                val q = if (src.quality.isNotEmpty() && src.quality != "null") src.quality else "کیفیت اصلی"
                sourcesList.add(src.copy(quality = q))
            }
            detailAdapter.notifyDataSetChanged()
        }
    }

    private fun updateSeasonEpisodes() {
        sourcesList.clear()
        if (allSeasons.isNotEmpty()) {
            val season = allSeasons.getOrNull(selectedSeasonIndex) ?: allSeasons.first()
            sectionTitle = "قسمت‌های ${season.title}"
            for ((epIdx, ep) in season.episodes.withIndex()) {
                val epTitle = if (ep.title.isNotEmpty() && ep.title != "null") ep.title else "قسمت ${epIdx + 1}"
                for (src in ep.sources) {
                    val qualityText = if (src.quality.isNotEmpty() && src.quality != "null") " (${src.quality})" else ""
                    val displayTitle = "$epTitle$qualityText"
                    // id contains episode id to accurately match next episode in player
                    sourcesList.add(src.copy(id = ep.id, quality = displayTitle))
                }
            }
        } else {
            sectionTitle = "قسمتی برای این سریال یافت نشد."
        }
        if (!isFinishing && !isDestroyed) {
            detailAdapter.notifyDataSetChanged()
        }
    }

    private fun playStream(title: String, source: RealSource, resumePosition: Long = 0L, epIndex: Int = 0) {
        if (source.url.isEmpty()) {
            Toast.makeText(this, "آدرس ویدیو نامعتبر است", Toast.LENGTH_SHORT).show()
            return
        }
        val item = mediaItem ?: return
        val banner = if (item.cover.isNotEmpty()) item.cover else item.image

        PlayerLauncherHelper.launchPlayer(
            activity = this,
            playerChoice = historyManager.preferredPlayer,
            title = "$title - ${source.quality}",
            url = source.url,
            mediaId = item.id,
            mediaCover = banner,
            episodeTitle = source.quality,
            episodeIndex = epIndex,
            startPositionMs = resumePosition
        )
    }

    private fun showAddToPlaylistDialog(media: RealMedia) {
        val playlists = playlistsManager.playlistsFlow.value
        val items = arrayOf("❤️ نشان‌شده‌ها (اصلی)") + playlists.map { "📁 ${it.name}" }

        MaterialAlertDialogBuilder(this)
            .setTitle("افزودن «${media.title}» به لیست:")
            .setItems(items) { _, which ->
                if (which == 0) {
                    val added = favoritesManager.toggleFavorite(media)
                    detailAdapter.notifyItemChanged(0)
                    Toast.makeText(this, if (added) "به نشان‌شده‌ها اضافه شد" else "از نشان‌شده‌ها حذف شد", Toast.LENGTH_SHORT).show()
                } else {
                    val targetPl = playlists[which - 1]
                    val added = playlistsManager.addToPlaylist(targetPl.id, media)
                    val msg = if (added) "به لیست «${targetPl.name}» افزوده شد" else "قبلاً در این لیست وجود دارد"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    inner class DetailRecyclerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_HEADER = 0
        private val TYPE_ITEM = 1
        private val TYPE_FOOTER = 2

        override fun getItemViewType(position: Int): Int {
            return when (position) {
                0 -> TYPE_HEADER
                sourcesList.size + 1 -> TYPE_FOOTER
                else -> TYPE_ITEM
            }
        }

        override fun getItemCount(): Int = sourcesList.size + 2

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                TYPE_HEADER -> {
                    val b = ItemDetailHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                    HeaderViewHolder(b)
                }
                TYPE_FOOTER -> {
                    val b = ItemDetailFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                    FooterViewHolder(b)
                }
                else -> {
                    val b = ItemStreamLinkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                    ItemViewHolder(b)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is HeaderViewHolder -> holder.bind()
                is FooterViewHolder -> holder.bind()
                is ItemViewHolder -> holder.bind(sourcesList[position - 1], position - 1)
            }
        }

        inner class HeaderViewHolder(val b: ItemDetailHeaderBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind() {
                val item = mediaItem ?: return
                val banner = if (item.cover.isNotEmpty()) item.cover else item.image
                b.imgHeaderBackdrop.load(banner) { crossfade(true) }

                b.tvHeaderTitle.text = item.title
                b.tvHeaderGenres.text = item.genres.joinToString("، ") { it.title }
                b.tvHeaderRating.text = String.format("%.1f", item.imdb)
                b.tvHeaderYear.text = if (item.year > 0) item.year.toString() else "نامشخص"
                b.tvHeaderType.text = item.duration ?: if (item.type == "serie") "سریال" else "سینمایی"
                b.tvHeaderStoryline.text = if (item.description.isNotEmpty()) item.description else "توضیحاتی موجود نیست."
                b.tvHeaderSectionTitle.text = sectionTitle

                val isFav = favoritesManager.isFavorite(item.id)
                b.btnHeaderAddPlaylist.setIconResource(R.drawable.ic_bookmark)
                b.btnHeaderAddPlaylist.setIconTintResource(if (isFav) R.color.primary else R.color.outline)

                b.btnHeaderAddPlaylist.setOnClickListener {
                    showAddToPlaylistDialog(item)
                }

                // Season Selector Tabs
                if (allSeasons.size > 1) {
                    b.scrollSeasons.visibility = View.VISIBLE
                    b.chipGroupSeasons.removeAllViews()
                    for (i in allSeasons.indices) {
                        val season = allSeasons[i]
                        val chip = Chip(this@DetailActivity).apply {
                            text = season.title
                            isCheckable = true
                            isChecked = selectedSeasonIndex == i
                            setOnClickListener {
                                selectedSeasonIndex = i
                                updateSeasonEpisodes()
                            }
                        }
                        b.chipGroupSeasons.addView(chip)
                    }
                } else {
                    b.scrollSeasons.visibility = View.GONE
                }

                // Trailer Button
                if (!trailerUrl.isNullOrEmpty()) {
                    b.btnHeaderTrailer.visibility = View.VISIBLE
                    b.btnHeaderTrailer.setOnClickListener {
                        val tSource = RealSource(quality = "تیزر رسمی", url = trailerUrl!!)
                        playStream(item.title, tSource)
                    }
                } else {
                    b.btnHeaderTrailer.visibility = View.GONE
                }

                // Continue Watching check
                val lastWatched = historyManager.getLastWatchedEpisode(item.id)
                if (lastWatched != null && !lastWatched.isFinished) {
                    val mins = (lastWatched.positionMs / 1000) / 60
                    val secs = (lastWatched.positionMs / 1000) % 60
                    val epLabel = if (lastWatched.episodeTitle.isNotEmpty()) lastWatched.episodeTitle else "قسمت دیده شده"
                    b.btnHeaderPlay.text = "ادامه: $epLabel (${String.format("%02d:%02d", mins, secs)})"

                    b.btnHeaderPlay.setOnClickListener {
                        val matchedIndex = sourcesList.indexOfFirst { it.url == lastWatched.url }
                        val matchedSource = if (matchedIndex >= 0) sourcesList[matchedIndex] else null
                        val epIdx = if (matchedIndex >= 0) matchedIndex else lastWatched.episodeIndex
                        if (matchedSource != null) {
                            playStream(item.title, matchedSource, lastWatched.positionMs, epIdx)
                        } else {
                            val dummySource = RealSource(quality = lastWatched.episodeTitle, url = lastWatched.url)
                            playStream(item.title, dummySource, lastWatched.positionMs, epIdx)
                        }
                    }
                } else {
                    b.btnHeaderPlay.text = getString(R.string.btn_watch_now)
                    b.btnHeaderPlay.setOnClickListener {
                        val mainMovieSource = sourcesList.find { !it.quality.contains("تیزر") && !it.url.contains("tizer") } ?: sourcesList.firstOrNull()
                        val epIdx = if (mainMovieSource != null) sourcesList.indexOf(mainMovieSource) else 0
                        mainMovieSource?.let { playStream(item.title, it, 0L, epIdx) }
                            ?: Toast.makeText(this@DetailActivity, "در حال بارگذاری یا لینکی موجود نیست", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        inner class ItemViewHolder(val b: ItemStreamLinkBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(src: RealSource, index: Int) {
                b.tvLinkQuality.text = src.quality
                val fmt = if (src.type.isNotEmpty() && src.type != "null") src.type.uppercase() else "MKV"
                b.tvLinkInfo.text = "کیفیت استریم و پخش روان • $fmt"

                val progress = historyManager.getProgressByUrl(src.url)
                if (progress != null && progress.positionMs > 1000) {
                    val mins = (progress.positionMs / 1000) / 60
                    val secs = (progress.positionMs / 1000) % 60
                    val totalMins = (progress.durationMs / 1000) / 60
                    b.tvLinkResumeBadge.visibility = View.VISIBLE
                    if (progress.isFinished) {
                        b.tvLinkResumeBadge.text = "✓ دیده شده"
                        b.tvLinkResumeBadge.setTextColor(getColor(R.color.outline))
                    } else {
                        b.tvLinkResumeBadge.text = "دیده شده تا ${String.format("%02d:%02d", mins, secs)} از $totalMins دقیقه (${progress.progressPercent}%)"
                        b.tvLinkResumeBadge.setTextColor(getColor(R.color.secondary))
                    }
                } else {
                    b.tvLinkResumeBadge.visibility = View.GONE
                }

                b.btnLinkPlay.setOnClickListener {
                    playStream(mediaItem?.title ?: "", src, progress?.positionMs ?: 0L, index)
                }
                b.root.setOnClickListener {
                    playStream(mediaItem?.title ?: "", src, progress?.positionMs ?: 0L, index)
                }

                b.btnLinkDownload.setOnClickListener {
                    val itemTitle = mediaItem?.title ?: "Bisnor_Media"
                    val downloadTitle = "$itemTitle - ${src.quality}"
                    DownloadHelper.downloadVideo(this@DetailActivity, downloadTitle, src.url)
                }
            }
        }

        inner class FooterViewHolder(val b: ItemDetailFooterBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind() {
                if (similarList.isNotEmpty()) {
                    b.root.visibility = View.VISIBLE
                    val simAdapter = RealMediaAdapter(similarList) { simItem ->
                        val intent = Intent(this@DetailActivity, DetailActivity::class.java).apply {
                            putExtra("real_media", simItem)
                        }
                        startActivity(intent)
                    }
                    b.recyclerSimilarMoviesFooter.adapter = simAdapter
                } else {
                    b.root.visibility = View.GONE
                }
            }
        }
    }
}
