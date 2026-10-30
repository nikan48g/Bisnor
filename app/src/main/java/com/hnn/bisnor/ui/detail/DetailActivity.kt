package com.hnn.bisnor.ui.detail

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hnn.bisnor.R
import com.hnn.bisnor.data.model.RealEpisode
import com.hnn.bisnor.data.model.RealMedia
import com.hnn.bisnor.data.model.RealPerson
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
import com.hnn.bisnor.ui.adapter.PersonRowAdapter
import com.hnn.bisnor.ui.adapter.RealMediaAdapter
import com.hnn.bisnor.ui.person.PersonActivity
import com.hnn.bisnor.util.DownloadHelper
import com.hnn.bisnor.util.ImageUrlHelper
import com.hnn.bisnor.util.MediaMetadataHelper
import com.hnn.bisnor.util.ParsedMediaInfo
import com.hnn.bisnor.util.PersonPhotoResolver
import com.hnn.bisnor.util.PlayerLauncherHelper
import com.hnn.bisnor.util.ThemeHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var playlistsManager: PlaylistsManager
    private lateinit var historyManager: PlaybackHistoryManager

    private var mediaItem: RealMedia? = null
    private val rows = mutableListOf<DetailRow>()
    private val allSeasons = mutableListOf<RealSeason>()
    private var selectedSeasonIndex = 0
    private val similarList = mutableListOf<RealMedia>()
    private var trailerUrl: String? = null
    private var people: List<RealPerson> = emptyList()
    private var peopleAdapter: PersonRowAdapter? = null
    private var sectionTitle = "کیفیت‌های پخش"
    private lateinit var adapter: DetailRecyclerAdapter
    private var synopsisExpanded = false

    data class DetailRow(
        val source: RealSource,
        val title: String,
        val subtitle: String,
        val thumbnail: String?,
        val progress: PlaybackProgress?,
        val episodeIndex: Int,
        val sources: List<RealSource> = emptyList()
    ) {
        val isWatched: Boolean get() = progress?.isFinished == true
    }

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
        // The immersive header carries the title; the toolbar stays a clean back affordance.

        people = buildPeople(mediaItem!!)
        resolvePeoplePhotos()

        adapter = DetailRecyclerAdapter()
        binding.recyclerDetailContent.apply {
            layoutManager = LinearLayoutManager(this@DetailActivity)
            adapter = this@DetailActivity.adapter
            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }

        checkContentWarning(mediaItem!!)
        loadData()
        hydrateProviders()
    }

    /**
     * Next Movie list payloads carry no description/links, so the detail screen fills
     * them in once and re-renders. IranFlix metadata always stays authoritative.
     */
    private fun hydrateProviders() {
        val current = mediaItem ?: return
        lifecycleScope.launch {
            val hydrated = try {
                RealMediaRepository.hydrate(current)
            } catch (_: Exception) {
                current
            }
            if (isFinishing || isDestroyed) return@launch
            mediaItem = hydrated
            updateTrailer(hydrated)
            // Never blank the credits: a hydrated copy may arrive without people even
            // though the row already shows them.
            people = buildPeople(hydrated).ifEmpty { people }
            peopleAdapter = null
            resolvePeoplePhotos()
            adapter.notifyDataSetChanged()
            if (!hydrated.isSeries) buildMovieRows(hydrated)
        }
    }

    // ---------------------------------------------------------------- warning & data

    private fun checkContentWarning(item: RealMedia) {
        if (!historyManager.isContentWarningEnabled || isFinishing || isDestroyed) return
        val isAnim = item.genres.any {
            it.title.contains("انیمیشن") || it.title.contains("کارتون") || it.title.contains("انیمه")
        } || item.title.contains("انیمه") || item.title.contains("انیمیشن")
        val meta = MediaMetadataHelper.parse(item.description, item.imdb, isAnim)
        if (!meta.isAdultOr18) return

        MaterialAlertDialogBuilder(this)
            .setTitle("هشدار رده سنی")
            .setMessage("این اثر ممکن است برای افراد زیر ۱۸ سال مناسب نباشد. ادامه می‌دهید؟")
            .setPositiveButton("ادامه") { dialog, _ -> dialog.dismiss() }
            .setNegativeButton("بازگشت") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun loadData() {
        val item = mediaItem ?: return

        updateTrailer(item)

        lifecycleScope.launch {
            try {
                val genre = item.genres.firstOrNull()?.title
                if (!genre.isNullOrEmpty()) {
                    val results = RealMediaRepository.search(genre)
                    similarList.clear()
                    similarList.addAll(results.filter { it.id != item.id }.take(10))
                    if (!isFinishing && !isDestroyed) adapter.notifyDataSetChanged()
                }
            } catch (_: Exception) {
                // similar row is optional
            }
        }

        if (item.isSeries) {
            sectionTitle = "در حال دریافت قسمت‌ها..."
            adapter.notifyItemChanged(0)
            lifecycleScope.launch {
                val seasons = try {
                    RealMediaRepository.getSeriesSeasons(item)
                } catch (_: Exception) {
                    emptyList()
                }
                allSeasons.clear()
                allSeasons.addAll(seasons)
                selectedSeasonIndex = 0
                updateSeasonEpisodes()
            }
        } else {
            buildMovieRows(item)
        }
    }

    private fun updateTrailer(item: RealMedia) {
        trailerUrl = item.sources.firstOrNull {
            it.quality.contains("تیزر") || it.quality.contains("Trailer", true) || it.url.contains("tizer")
        }?.url
    }

    private fun buildMovieRows(item: RealMedia) {
        rows.clear()
        val choices = playableSources(item.sources)
        if (choices.isEmpty()) {
            sectionTitle = if (item.nextId > 0 && com.hnn.bisnor.data.repository.SourcePreferences.usesNext()) {
                "در حال دریافت لینک‌ها..."
            } else {
                "لینک پخشی برای این اثر موجود نیست"
            }
            if (!isFinishing && !isDestroyed) adapter.notifyDataSetChanged()
            return
        }
        // Every quality of a title lives inside the player now, so the list shows
        // one entry instead of one row per source.
        val best = choices.first()
        val progress = historyManager.getProgressByUrl(best.url)
        rows.add(
            DetailRow(
                source = best,
                title = "پخش فیلم",
                subtitle = buildString {
                    append(qualityLabel(best))
                    append(" • ").append(providerLabel(best))
                    if (choices.size > 1) append(" • ").append(choices.size).append(" نسخه")
                    if (progress != null && progress.positionMs > 1000) {
                        append(" • دیده‌شده تا ${formatMinutes(progress.positionMs)}")
                    }
                },
                thumbnail = null,
                progress = progress,
                episodeIndex = 0,
                sources = choices
            )
        )
        sectionTitle = "پخش و دانلود"
        if (!isFinishing && !isDestroyed) adapter.notifyDataSetChanged()
    }

    private fun qualityLabel(source: RealSource): String {
        val raw = source.quality
        val match = Regex("(2160|1440|1080|720|540|480|360|240)").find(raw)
        return match?.let { "${it.value}p" } ?: raw.ifBlank { "کیفیت اصلی" }
    }

    private fun qualityRank(source: RealSource): Int =
        Regex("(2160|1440|1080|720|540|480|360|240)").find(source.quality)?.value?.toIntOrNull() ?: 0

    private fun providerLabel(source: RealSource): String =
        if (source.origin == "next") "نکست‌مووی" else "ایران‌فلیکس"

    private fun playableSources(sources: List<RealSource>): List<RealSource> =
        sources.filter { it.url.isNotBlank() && !it.quality.contains("تیزر") && !it.url.contains("tizer") }
            .sortedByDescending { qualityRank(it) }

    private fun updateSeasonEpisodes() {
        rows.clear()
        if (allSeasons.isNotEmpty()) {
            val season = allSeasons.getOrNull(selectedSeasonIndex) ?: allSeasons.first()
            sectionTitle = "قسمت‌های ${season.title}"
            season.episodes.forEachIndexed { index, episode ->
                // One row per episode: every quality of every source stays inside
                // the player's quality picker instead of duplicating the episode.
                val choices = playableSources(episode.sources)
                if (choices.isEmpty()) return@forEachIndexed
                val best = choices.first()
                val progress = historyManager.getProgressByUrl(best.url)
                rows.add(
                    DetailRow(
                        source = best,
                        title = episode.title.ifBlank { "قسمت ${index + 1}" },
                        subtitle = buildString {
                            append("قسمت ${index + 1}")
                            if (!episode.duration.isNullOrBlank()) append(" • ${episode.duration}")
                            append(" • ${qualityLabel(best)}")
                            append(" • ${providerLabel(best)}")
                            if (choices.size > 1) append(" • ${choices.size} نسخه")
                        },
                        thumbnail = episode.image.takeIf { it.isNotBlank() },
                        progress = progress,
                        episodeIndex = index,
                        sources = choices
                    )
                )
            }
        } else {
            sectionTitle = "قسمتی برای این سریال یافت نشد"
        }
        if (!isFinishing && !isDestroyed) adapter.notifyDataSetChanged()
    }

    // ---------------------------------------------------------------- playback

    private fun play(row: DetailRow, resumePosition: Long = 0L) {
        val item = mediaItem ?: return
        if (row.source.url.isBlank()) {
            Toast.makeText(this, "آدرس ویدیو معتبر نیست", Toast.LENGTH_SHORT).show()
            return
        }
        val banner = item.cover.ifEmpty { item.image }
        val genreList = item.genres.map { it.title }
        val type = if (item.isSeries) "series" else "movie"
        val alternatives = if (row.sources.isNotEmpty()) row.sources else listOf(row.source)

        try {
            com.hnn.bisnor.data.database.SmartTasteDatabase(this).recordWatchEvent(
                mediaId = item.id,
                title = item.title,
                mediaType = type,
                genres = genreList
            )
        } catch (_: Exception) {
            // analytics is best-effort
        }

        PlayerLauncherHelper.launchPlayer(
            activity = this,
            playerChoice = historyManager.preferredPlayer,
            title = if (item.isSeries) "${item.title} • ${row.title}" else item.title,
            url = row.source.url,
            mediaId = item.id,
            mediaCover = banner,
            episodeTitle = if (item.isSeries) row.title else "",
            episodeIndex = row.episodeIndex,
            startPositionMs = maxOf(resumePosition, row.progress?.positionMs ?: 0L),
            mediaType = type,
            genres = ArrayList(genreList),
            sources = ArrayList(alternatives)
        )
    }

    private fun playPrimary() {
        val item = mediaItem ?: return
        if (rows.isEmpty()) {
            Toast.makeText(this, "هنوز لینک پخشی بارگذاری نشده است", Toast.LENGTH_SHORT).show()
            return
        }
        val resumeRow = rows.firstOrNull { it.progress != null && !it.progress.isFinished }
        val target = resumeRow ?: rows.firstOrNull { !it.isWatched } ?: rows.first()
        play(target)
    }

    private fun formatMinutes(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    // ---------------------------------------------------------------- watchlist & share

    private fun showAddToPlaylistDialog(media: RealMedia) {
        val playlists = playlistsManager.playlistsFlow.value
        val items = arrayOf("نشان‌شده‌ها (واچ‌لیست اصلی)") + playlists.map { it.name }

        MaterialAlertDialogBuilder(this)
            .setTitle("افزودن «${media.title}» به:")
            .setItems(items) { _, which ->
                if (which == 0) {
                    val added = favoritesManager.toggleFavorite(media)
                    adapter.notifyItemChanged(0)
                    Toast.makeText(
                        this,
                        if (added) "به واچ‌لیست اضافه شد" else "از واچ‌لیست حذف شد",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val target = playlists[which - 1]
                    val added = playlistsManager.addToPlaylist(target.id, media)
                    Toast.makeText(
                        this,
                        if (added) "به «${target.name}» اضافه شد" else "قبلاً در این لیست بود",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    private fun shareMedia(media: RealMedia) {
        val text = "«${media.title}» را در بیسنور ببینید.\nhttps://bisnor.app"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, media.title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "اشتراک‌گذاری"))
    }

    // ---------------------------------------------------------------- adapter

    inner class DetailRecyclerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val typeHeader = 0
        private val typeItem = 1
        private val typeFooter = 2

        override fun getItemViewType(position: Int): Int = when (position) {
            0 -> typeHeader
            rows.size + 1 -> typeFooter
            else -> typeItem
        }

        override fun getItemCount(): Int = rows.size + 2

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                typeHeader -> HeaderHolder(ItemDetailHeaderBinding.inflate(inflater, parent, false))
                typeFooter -> FooterHolder(ItemDetailFooterBinding.inflate(inflater, parent, false))
                else -> RowHolder(ItemStreamLinkBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is HeaderHolder -> holder.bind()
                is FooterHolder -> holder.bind()
                is RowHolder -> holder.bind(rows[position - 1], position - 1)
            }
        }

        inner class HeaderHolder(val b: ItemDetailHeaderBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind() {
                val item = mediaItem ?: return
                val backdrop = item.cover.ifEmpty { item.image }
                b.imgHeaderBackdrop.load(ImageUrlHelper.getOptimizedImageUrl(backdrop)) { crossfade(true) }
                b.imgHeaderPoster.load(ImageUrlHelper.getOptimizedImageUrl(item.image)) {
                    crossfade(true)
                    placeholder(R.drawable.bg_skeleton)
                    error(R.drawable.ic_movie_placeholder)
                }

                val isAnim = item.genres.any {
                    it.title.contains("انیمیشن") || it.title.contains("کارتون") || it.title.contains("انیمه")
                }
                val meta = MediaMetadataHelper.parse(item.description, item.imdb, isAnim, item.isSeries)
                val rating = meta.extractedImdb ?: item.imdb

                b.tvHeaderTitle.text = item.title

                val originalTitle = extractOriginalTitle(item.description)
                if (!originalTitle.isNullOrBlank() && !originalTitle.equals(item.title, true)) {
                    b.tvHeaderOriginalTitle.text = originalTitle
                    b.tvHeaderOriginalTitle.visibility = View.VISIBLE
                } else {
                    b.tvHeaderOriginalTitle.visibility = View.GONE
                }

                b.tvHeaderRating.text = if (rating > 0.0) {
                    String.format(Locale.US, "%.1f IMDb", rating)
                } else {
                    "بدون امتیاز"
                }
                b.tvHeaderRating.visibility = if (rating > 0.0) View.VISIBLE else View.GONE
                b.tvHeaderYear.text = if (item.year > 0) item.year.toString() else "نامشخص"
                b.tvHeaderType.text = when {
                    item.isSeries -> "سریال"
                    !item.duration.isNullOrBlank() -> item.duration
                    else -> "فیلم"
                }
                b.tvHeaderGenres.text = item.genres.joinToString("، ") { it.title }

                if (!meta.ageRating.isNullOrBlank()) {
                    b.tvHeaderAgeRating.text = meta.ageRating
                    b.tvHeaderAgeRating.visibility = View.VISIBLE
                } else {
                    b.tvHeaderAgeRating.visibility = View.GONE
                }

                val synopsis = meta.cleanStoryline.ifBlank { "توضیحاتی ثبت نشده است." }
                b.tvHeaderStoryline.text = synopsis
                b.btnExpandStory.visibility = if (synopsis.length > 150) View.VISIBLE else View.GONE
                b.btnExpandStory.text = if (synopsisExpanded) "کمتر" else "بیشتر"
                b.tvHeaderStoryline.maxLines = if (synopsisExpanded) Int.MAX_VALUE else 4
                b.btnExpandStory.setOnClickListener {
                    synopsisExpanded = !synopsisExpanded
                    notifyItemChanged(0)
                }

                bindBadges(meta, item)
                bindPeople()

                b.tvHeaderSectionTitle.text = sectionTitle

                val isFavorite = favoritesManager.isFavorite(item.id)
                b.btnHeaderAddPlaylist.setIconResource(
                    if (isFavorite) R.drawable.ic_bookmark else R.drawable.ic_bookmark
                )
                b.btnHeaderAddPlaylist.setIconTintResource(
                    if (isFavorite) R.color.primary else R.color.on_surface
                )
                b.btnHeaderAddPlaylist.setOnClickListener { showAddToPlaylistDialog(item) }
                b.btnHeaderShareChat.setOnClickListener { shareMedia(item) }

                if (!trailerUrl.isNullOrBlank()) {
                    b.btnHeaderTrailer.visibility = View.VISIBLE
                    b.btnHeaderTrailer.setOnClickListener {
                        play(DetailRow(RealSource(quality = "تیزر", url = trailerUrl!!), "تیزر", "", null, null, 0))
                    }
                } else {
                    b.btnHeaderTrailer.visibility = View.GONE
                }

                b.btnHeaderPlay.text = if (rows.any { it.progress != null && !it.progress.isFinished }) {
                    "ادامه تماشا"
                } else {
                    getString(R.string.btn_watch_now)
                }
                b.btnHeaderPlay.setOnClickListener { playPrimary() }

                bindSeasons()
            }

            private fun bindBadges(meta: ParsedMediaInfo, item: RealMedia) {
                val badges = buildBadges(meta, item)
                b.linearHeaderBadges.removeAllViews()
                if (badges.isEmpty()) {
                    b.scrollHeaderBadges.visibility = View.GONE
                    return
                }

                badges.forEachIndexed { index, badge ->
                    val text = TextView(this@DetailActivity).apply {
                        this.text = badge
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        typeface = ResourcesCompat.getFont(this@DetailActivity, R.font.vazirmatn_medium)
                        setTextColor(
                            ContextCompat.getColor(
                                this@DetailActivity,
                                if (index == 0) R.color.primary else R.color.on_surface_variant
                            )
                        )
                        setBackgroundResource(R.drawable.bg_soft_pill)
                        val padH = dp(11)
                        val padV = dp(5)
                        setPadding(padH, padV, padH, padV)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { marginEnd = dp(6) }
                    }
                    b.linearHeaderBadges.addView(text)
                }
                b.scrollHeaderBadges.visibility = View.VISIBLE
            }

            private fun bindPeople() {
                if (people.isEmpty()) {
                    b.layoutHeaderPeople.visibility = View.GONE
                    return
                }
                b.layoutHeaderPeople.visibility = View.VISIBLE
                val rowAdapter = peopleAdapter ?: PersonRowAdapter(people) { person ->
                    openPerson(person)
                }.also {
                    peopleAdapter = it
                    b.recyclerHeaderPeople.layoutManager = LinearLayoutManager(
                        this@DetailActivity, RecyclerView.HORIZONTAL, false
                    )
                    b.recyclerHeaderPeople.adapter = it
                }
                rowAdapter.updateData(people)
            }

            private fun bindSeasons() {
                if (allSeasons.size > 1) {
                    b.scrollSeasons.visibility = View.VISIBLE
                    b.chipGroupSeasons.removeAllViews()
                    allSeasons.forEachIndexed { index, season ->
                        val chip = Chip(this@DetailActivity).apply {
                            text = season.title
                            isCheckable = true
                            isChecked = selectedSeasonIndex == index
                            setOnClickListener {
                                selectedSeasonIndex = index
                                updateSeasonEpisodes()
                            }
                        }
                        b.chipGroupSeasons.addView(chip)
                    }
                } else {
                    b.scrollSeasons.visibility = View.GONE
                }
            }
        }

        inner class RowHolder(val b: ItemStreamLinkBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(row: DetailRow, index: Int) {
                b.tvLinkQuality.text = row.title
                b.tvLinkInfo.text = row.subtitle

                if (!row.thumbnail.isNullOrBlank()) {
                    b.cardLinkThumb.visibility = View.VISIBLE
                    b.imgLinkThumb.load(ImageUrlHelper.getOptimizedImageUrl(row.thumbnail)) {
                        crossfade(true)
                        placeholder(R.drawable.bg_skeleton)
                        error(R.drawable.ic_movie_placeholder)
                    }
                } else {
                    b.cardLinkThumb.visibility = View.GONE
                }

                val progress = row.progress
                if (progress != null && progress.positionMs > 1000) {
                    if (progress.isFinished) {
                        b.tvLinkResumeBadge.visibility = View.VISIBLE
                        b.tvLinkResumeBadge.text = "تماشا شده"
                        b.tvLinkResumeBadge.setTextColor(getColor(R.color.bisnor_success))
                        b.imgLinkWatched.visibility = View.VISIBLE
                        b.progressLink.visibility = View.GONE
                    } else {
                        b.tvLinkResumeBadge.visibility = View.VISIBLE
                        b.tvLinkResumeBadge.text =
                            "ادامه از ${formatMinutes(progress.positionMs)} • ${progress.progressPercent}%"
                        b.tvLinkResumeBadge.setTextColor(getColor(R.color.primary))
                        b.imgLinkWatched.visibility = View.GONE
                        b.progressLink.visibility = View.VISIBLE
                        b.progressLink.progress = progress.progressPercent
                    }
                } else {
                    b.tvLinkResumeBadge.visibility = View.GONE
                    b.imgLinkWatched.visibility = View.GONE
                    b.progressLink.visibility = View.GONE
                }

                b.btnLinkPlay.setOnClickListener { play(row) }
                b.root.setOnClickListener { play(row) }
                b.btnLinkDownload.setOnClickListener {
                    val title = "${mediaItem?.title ?: "Bisnor"} - ${row.title}"
                    DownloadHelper.downloadVideo(this@DetailActivity, title, row.source.url)
                }
            }
        }

        inner class FooterHolder(val b: ItemDetailFooterBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind() {
                if (similarList.isEmpty()) {
                    b.root.visibility = View.GONE
                    return
                }
                b.root.visibility = View.VISIBLE
                b.recyclerSimilarMoviesFooter.adapter = RealMediaAdapter(
                    similarList,
                    onItemClick = { similar ->
                        startActivity(
                            Intent(this@DetailActivity, DetailActivity::class.java).apply {
                                putExtra("real_media", similar)
                            }
                        )
                    }
                )
            }
        }
    }


    // ---------------------------------------------------------------- people & badges

    private fun buildPeople(item: RealMedia): List<RealPerson> {
        if (item.people.isNotEmpty()) return item.people
        val isAnim = item.genres.any {
            it.title.contains("انیمیشن") || it.title.contains("کارتون") || it.title.contains("انیمه")
        } || item.title.contains("انیمه") || item.title.contains("انیمیشن")
        val meta = MediaMetadataHelper.parse(item.description, item.imdb, isAnim, item.isSeries)
        val result = mutableListOf<RealPerson>()

        meta.director?.trim()?.takeIf { it.isNotBlank() }?.let {
            result.add(RealPerson(name = it.take(80), role = "director"))
        }
        meta.actors?.split(Regex("[,،|]"))
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && it.length <= 80 }
            ?.forEach { result.add(RealPerson(name = it, role = "actor")) }

        Regex("""(?i)(?:نویسنده|فیلمنامه|writer)\s*[:：\-]?\s*([^\n\r]+)""")
            .find(item.description)?.groupValues?.getOrNull(1)
            ?.split(Regex("[,،|]"))
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && it.length <= 80 }
            ?.forEach { result.add(RealPerson(name = it, role = "writer")) }

        return result
            .distinctBy { "${it.role}:${it.name.lowercase()}" }
            .take(12)
    }

    private fun resolvePeoplePhotos() {
        if (people.isEmpty()) return
        lifecycleScope.launch {
            val updated = coroutineScope {
                people.map { person ->
                    async {
                        if (person.photo.isNotBlank()) person
                        else person.copy(photo = PersonPhotoResolver.resolve(person.name))
                    }
                }.awaitAll()
            }
            if (isFinishing || isDestroyed) return@launch
            if (updated != people) {
                people = updated
                adapter.notifyItemChanged(0)
            }
        }
    }

    private fun openPerson(person: RealPerson) {
        startActivity(
            Intent(this, PersonActivity::class.java).apply {
                putExtra("person_name", person.name)
                putExtra("person_role", person.role)
                putExtra("person_photo", person.photo)
            }
        )
    }

    private fun buildBadges(meta: ParsedMediaInfo, item: RealMedia): List<String> {
        val badges = mutableListOf<String>()

        val age = meta.ageRating
            ?: item.classification?.trim()?.takeIf { it.isNotBlank() && !it.equals("N/A", true) }
        if (!age.isNullOrBlank()) badges.add("رده سنی $age")

        meta.language?.takeIf { it.isNotBlank() }?.let { badges.add(it) }

        if (meta.hasPersianSubtitle && badges.none { it.contains("زیرنویس") }) {
            badges.add("زیرنویس فارسی")
        }

        val country = meta.country ?: item.country.firstOrNull()?.title
        if (!country.isNullOrBlank() && !country.equals("جهانی", true)) {
            badges.add(country.trim().take(24))
        }

        meta.quality?.takeIf { it.isNotBlank() }?.let { raw ->
            val resolutions = Regex("""\d{3,4}""").findAll(raw).map { it.value }.distinct().toList()
            if (resolutions.isNotEmpty()) {
                badges.add("کیفیت " + resolutions.joinToString(" / ") { "${it}p" })
            } else {
                badges.add("کیفیت ${raw.take(20)}")
            }
        }

        meta.format?.takeIf { it.isNotBlank() }?.let { badges.add("فرمت ${it.uppercase()}") }
        meta.declaredSize?.takeIf { it.isNotBlank() }?.let { badges.add("حجم ${it.take(36)}") }

        return badges.distinct().take(7)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun extractOriginalTitle(description: String): String? {
        val regex = Regex("""(?i)(?:نام اصلی|original title|original name)\s*[:：]\s*([^\n\r]+)""")
        return regex.find(description)?.groupValues?.getOrNull(1)?.trim()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        ThemeHelper.applyTheme(this)
    }
}

