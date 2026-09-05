package com.hnn.bisnor.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hnn.bisnor.MainActivity
import com.hnn.bisnor.data.model.RealMedia
import com.hnn.bisnor.data.repository.PlaybackHistoryManager
import com.hnn.bisnor.data.repository.PlaybackProgress
import com.hnn.bisnor.data.repository.RealMediaRepository
import com.hnn.bisnor.databinding.FragmentHomeBinding
import com.hnn.bisnor.databinding.ItemContinueWatchingBinding
import com.hnn.bisnor.ui.adapter.RealHeroBannerAdapter
import com.hnn.bisnor.ui.adapter.RealHomeSection
import com.hnn.bisnor.ui.adapter.RealHomeSectionAdapter
import com.hnn.bisnor.ui.detail.DetailActivity
import com.hnn.bisnor.ui.player.PlayerActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.random.Random

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var historyManager: PlaybackHistoryManager
    private var allFetchedMedia = listOf<RealMedia>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        historyManager = PlaybackHistoryManager(requireContext())

        binding.recyclerSections.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

        binding.swipeRefresh.setOnRefreshListener { loadData() }
        binding.btnQuickSearch.setOnClickListener {
            (activity as? MainActivity)?.selectExploreTab()
        }

        binding.btnRandomMovie.setOnClickListener {
            showRandomMovieDialog()
        }

        loadData()
    }

    private fun showRandomMovieDialog() {
        val goodCandidates = allFetchedMedia.filter { it.imdb >= 7.0 && it.image.isNotEmpty() }
        val pool = if (goodCandidates.isNotEmpty()) goodCandidates else allFetchedMedia
        if (pool.isNotEmpty()) {
            val randomItem = pool[Random.nextInt(pool.size)]
            val genreStr = randomItem.genres.joinToString("، ") { it.title }
            val yearStr = if (randomItem.year > 0) " (${randomItem.year})" else ""

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("🎲 پیشنهاد امشب بیسنور:")
                .setMessage("فیلم: «${randomItem.title}$yearStr»\n⭐ نمره آی‌ام‌دی‌بی: ${String.format("%.1f", randomItem.imdb)}\n🎭 ژانر: $genreStr\n\nآیا مایلید این اثر را مشاهده کنید؟")
                .setPositiveButton("تماشای فیلم") { _, _ ->
                    openDetail(randomItem)
                }
                .setNeutralButton("یک پیشنهاد دیگر 🔄") { _, _ ->
                    showRandomMovieDialog()
                }
                .setNegativeButton("انصراف", null)
                .show()
        } else {
            Toast.makeText(requireContext(), "در حال دریافت لیست فیلم‌ها...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            updateContinueWatchingRow()
        }
    }

    private fun loadData() {
        if (_binding == null) return
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val moviesP0 = async { RealMediaRepository.getLatestMovies(0) }
            val moviesP1 = async { RealMediaRepository.getLatestMovies(1) }
            val seriesP0 = async { RealMediaRepository.getPopularSeries(0) }
            val seriesP1 = async { RealMediaRepository.getPopularSeries(1) }
            val topMovies = async { RealMediaRepository.getTopImdbMovies(0) }
            val korean = async { RealMediaRepository.getPostersByCountry(14, 0) }
            val anime = async { RealMediaRepository.getPostersByCountry(10, 0) }

            val allMovies = (moviesP0.await() + moviesP1.await()).distinctBy { it.id }
            val allSeries = (seriesP0.await() + seriesP1.await()).distinctBy { it.id }
            val topList = topMovies.await()
            val koreanList = korean.await()
            val animeList = anime.await()

            allFetchedMedia = (allMovies + allSeries + topList + koreanList + animeList).distinctBy { it.id }

            if (_binding == null) return@launch

            binding.progressBar.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false

            updateContinueWatchingRow()

            // Hero banner
            val heroItems = (allMovies.take(4) + allSeries.take(3)).filter { it.cover.isNotEmpty() || it.image.isNotEmpty() }
            if (heroItems.isNotEmpty()) {
                binding.viewPagerHero.adapter = RealHeroBannerAdapter(heroItems) { openDetail(it) }
            }

            // Rich and diverse movie categories
            val sections = mutableListOf<RealHomeSection>()
            if (allMovies.isNotEmpty()) sections.add(RealHomeSection("🔥 تازه‌ترین فیلم‌های روز", allMovies.take(18)))
            if (allSeries.isNotEmpty()) sections.add(RealHomeSection("📺 سریال‌های پرمخاطب و داغ", allSeries.take(18)))
            if (topList.isNotEmpty()) sections.add(RealHomeSection("⭐ برترین فیلم‌های تاریخ سینما (IMDb Top)", topList))
            if (koreanList.isNotEmpty()) sections.add(RealHomeSection("🇰🇷 سریال‌های محبوب کره‌ای (K-Drama)", koreanList))
            if (animeList.isNotEmpty()) sections.add(RealHomeSection("🇯🇵 انیمه و دنیای سینمای ژاپن", animeList))

            val actionMovies = allMovies.filter { it.genres.any { g -> g.title.contains("اکشن") || g.title.contains("هیجان") } }
            if (actionMovies.isNotEmpty()) sections.add(RealHomeSection("💥 هیجان و اکشن بدون توقف", actionMovies))

            val animationMovies = allMovies.filter { it.genres.any { g -> g.title.contains("انیمیشن") || g.title.contains("کارتون") } }
            if (animationMovies.isNotEmpty()) sections.add(RealHomeSection("🎨 انیمیشن‌های خانوادگی و جذاب", animationMovies))

            val comedyMovies = allMovies.filter { it.genres.any { g -> g.title.contains("کمدی") || g.title.contains("خنده") } }
            if (comedyMovies.isNotEmpty()) sections.add(RealHomeSection("🍿 فیلم‌های کمدی و سرگرم‌کننده", comedyMovies))

            binding.recyclerSections.adapter = RealHomeSectionAdapter(sections) { openDetail(it) }
        }
    }

    private fun updateContinueWatchingRow() {
        if (_binding == null) return
        try {
            val historyList = historyManager.getAllContinueWatching()
            if (historyList.isNotEmpty()) {
                binding.layoutContinueWatching.visibility = View.VISIBLE
                binding.recyclerContinueWatching.adapter = ContinueWatchingAdapter(historyList)
            } else {
                binding.layoutContinueWatching.visibility = View.GONE
            }
        } catch (e: Exception) {
            // Safe guard
        }
    }

    private fun openDetail(item: RealMedia) {
        val intent = Intent(requireContext(), DetailActivity::class.java).apply {
            putExtra("real_media", item)
        }
        startActivity(intent)
    }

    inner class ContinueWatchingAdapter(
        private val items: List<PlaybackProgress>
    ) : RecyclerView.Adapter<ContinueWatchingAdapter.Holder>() {

        inner class Holder(val b: ItemContinueWatchingBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val b = ItemContinueWatchingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(b)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val matchedMedia = allFetchedMedia.find { it.id == item.mediaId }
            val banner = item.mediaCover.ifEmpty { matchedMedia?.cover?.ifEmpty { matchedMedia.image } ?: "" }

            if (banner.isNotEmpty()) {
                holder.b.imgContinueCover.load(banner) { crossfade(true) }
            }

            val title = item.mediaTitle.ifEmpty { matchedMedia?.title ?: "فیلم / سریال" }
            holder.b.tvContinueTitle.text = title

            val mins = (item.positionMs / 1000) / 60
            val totalMins = (item.durationMs / 1000) / 60
            val epSubtitle = if (item.episodeTitle.isNotEmpty()) "${item.episodeTitle} • " else ""
            holder.b.tvContinueSubtitle.text = "$epSubtitle$mins از $totalMins دقیقه"

            holder.b.progressContinueBar.progress = item.progressPercent

            holder.b.root.setOnClickListener {
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra("video_title", "$title - ${item.episodeTitle}")
                    putExtra("video_url", item.url)
                    putExtra("media_id", item.mediaId)
                    putExtra("media_title", title)
                    putExtra("media_cover", banner)
                    putExtra("episode_title", item.episodeTitle)
                    putExtra("episode_index", item.episodeIndex)
                    putExtra("start_position", item.positionMs)
                }
                startActivity(intent)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
