package com.hnn.bisnor.ui.home

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.hnn.bisnor.MainActivity
import com.hnn.bisnor.R
import com.hnn.bisnor.data.model.RealMedia
import com.hnn.bisnor.data.remote.AuthManager
import com.hnn.bisnor.data.repository.FavoritesManager
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
import com.hnn.bisnor.util.ImageUrlHelper
import com.hnn.bisnor.util.PlayerLauncherHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var historyManager: PlaybackHistoryManager
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var authManager: AuthManager

    private var allFetchedMedia = listOf<RealMedia>()
    private var loadJob: Job? = null
    private var autoScrollJob: Job? = null
    private var heroCount = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        historyManager = PlaybackHistoryManager(requireContext())
        favoritesManager = FavoritesManager(requireContext())
        authManager = AuthManager(requireContext())

        binding.recyclerSections.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.recyclerSections.itemAnimator = null

        binding.swipeRefresh.setOnRefreshListener { loadData(userInitiated = true) }
        binding.btnHomeSearch.setOnClickListener { openExplore() }
        binding.cardHomeSearch.setOnClickListener { openExplore() }
        binding.btnHomeAvatar.setOnClickListener { openProfile() }
        binding.btnHomeRetry.setOnClickListener { loadData(userInitiated = true) }
        binding.btnContinueAll.setOnClickListener {
            (activity as? MainActivity)?.selectLibraryTab()
        }

        bindGreeting()
        loadData(userInitiated = false)
    }

    // ------------------------------------------------------------------ header

    private fun bindGreeting() {
        if (authManager.isLoggedIn && authManager.currentUsername.isNotEmpty()) {
            val display = authManager.currentUsername
            binding.tvHomeGreeting.text = "سلام، ${display.replaceFirstChar { it.uppercase() }}"
            binding.tvHomeGreetingSub.text = "امشب چه چیزی ببینیم؟"
        } else {
            binding.tvHomeGreeting.text = "سلام"
            binding.tvHomeGreetingSub.text = "به بیسنور خوش آمدی"
        }

        val avatarUrl = authManager.userAvatarUrl
        val initial = authManager.currentUsername.trim().take(1).uppercase(Locale.getDefault())
        when {
            avatarUrl.isNotEmpty() -> {
                binding.imgHomeAvatar.visibility = View.VISIBLE
                binding.tvHomeAvatarInitial.visibility = View.GONE
                binding.imgHomeAvatar.load(avatarUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_account_circle)
                    error(R.drawable.ic_account_circle)
                }
            }
            authManager.isLoggedIn && initial.isNotEmpty() -> {
                binding.imgHomeAvatar.visibility = View.GONE
                binding.tvHomeAvatarInitial.visibility = View.VISIBLE
                binding.tvHomeAvatarInitial.text = initial
            }
            else -> {
                binding.imgHomeAvatar.visibility = View.VISIBLE
                binding.tvHomeAvatarInitial.visibility = View.GONE
                binding.imgHomeAvatar.setImageResource(R.drawable.ic_account_circle)
            }
        }
    }

    private fun openExplore() {
        (activity as? MainActivity)?.selectExploreTab()
    }

    private fun openProfile() {
        (activity as? MainActivity)?.selectProfileTab()
    }

    // ------------------------------------------------------------------ data

    private fun loadData(userInitiated: Boolean) {
        if (_binding == null) return
        loadJob?.cancel()
        if (!userInitiated) {
            showSkeleton(true)
            binding.layoutHomeError.visibility = View.GONE
        } else {
            binding.progressBar.visibility = View.VISIBLE
        }
        binding.swipeRefresh.isRefreshing = false

        loadJob = lifecycleScope.launch {
            // Paint genuine cached data immediately, then refresh from the network.
            if (!userInitiated) {
                val cached = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    RealMediaRepository.cachedHomeCatalog()
                }
                if (cached != null && cached.hero.isNotEmpty() && _binding != null) {
                    showSkeleton(false)
                    renderCatalog(cached, userInitiated = false)
                    binding.progressBar.visibility = View.VISIBLE
                }
            }

            val catalog = runCatching { RealMediaRepository.getHomeCatalog() }
                .getOrElse {
                    RealMediaRepository.HomeCatalog(emptyList(), emptyList(), false, true)
                }
            if (_binding == null) return@launch

            showSkeleton(false)
            binding.progressBar.visibility = View.GONE

            if (catalog.failed || catalog.hero.isEmpty()) {
                // Keep a cached home on screen if we already painted one.
                if (binding.homeContent.visibility != View.VISIBLE) {
                    binding.homeContent.visibility = View.GONE
                    binding.layoutHomeError.visibility = View.VISIBLE
                    binding.tvHomeErrorTitle.text = "ارتباط با سرور برقرار نشد"
                    binding.tvHomeErrorDesc.text =
                        "کاتالوگ ایران‌فلیکس در دسترس نیست. اتصال اینترنت را بررسی کنید و دوباره تلاش کنید."
                }
                return@launch
            }

            renderCatalog(catalog, userInitiated)
        }
    }

    private fun renderCatalog(catalog: RealMediaRepository.HomeCatalog, userInitiated: Boolean) {
        binding.layoutHomeError.visibility = View.GONE
        binding.homeContent.visibility = View.VISIBLE

        allFetchedMedia = (catalog.hero + catalog.rows.flatMap { it.items }).distinctBy { it.id }
        bindHero(catalog.hero)
        bindSections(catalog.rows)
        updateContinueWatchingRow()

        if (catalog.fromCache && userInitiated) {
            Toast.makeText(requireContext(), "محتوای ذخیره‌شده نمایش داده می‌شود", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSkeleton(show: Boolean) {
        binding.skeletonHome.visibility = if (show) View.VISIBLE else View.GONE
        binding.homeContent.visibility = if (show) View.GONE else View.VISIBLE
        if (show) binding.skeletonHome.startShimmer() else binding.skeletonHome.stopShimmer()
    }

    // ------------------------------------------------------------------ hero

    private fun bindHero(items: List<RealMedia>) {
        if (items.isEmpty()) {
            binding.viewPagerHero.visibility = View.GONE
            binding.layoutHeroIndicator.visibility = View.GONE
            return
        }
        binding.viewPagerHero.visibility = View.VISIBLE
        heroCount = items.size

        binding.viewPagerHero.adapter = RealHeroBannerAdapter(
            items,
            onDetailsClick = { openDetail(it) },
            onPlayClick = { playOrOpen(it) },
            onSaveClick = { toggleWatchlist(it) }
        )
        binding.viewPagerHero.offscreenPageLimit = 1

        binding.viewPagerHero.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                renderIndicators(position)
                scheduleAutoScroll()
            }
        })

        renderIndicators(0)
        scheduleAutoScroll()
    }

    private fun renderIndicators(selected: Int) {
        val container = binding.layoutHeroIndicator
        container.removeAllViews()
        if (heroCount <= 1) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        val density = resources.displayMetrics.density
        for (i in 0 until heroCount) {
            val dot = View(requireContext())
            val width = if (i == selected) (20 * density).toInt() else (6 * density).toInt()
            val params = ViewGroup.MarginLayoutParams(width, (6 * density).toInt())
            params.marginStart = (3 * density).toInt()
            params.marginEnd = (3 * density).toInt()
            dot.layoutParams = params
            dot.background = GradientDrawable().apply {
                cornerRadius = 100f
                setColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (i == selected) R.color.primary else R.color.surface_container_highest
                    )
                )
            }
            container.addView(dot)
        }
    }

    private fun scheduleAutoScroll() {
        autoScrollJob?.cancel()
        if (!isResumed || heroCount <= 1 || !animationsEnabled()) return
        autoScrollJob = lifecycleScope.launch {
            delay(7000)
            val pager = _binding?.viewPagerHero ?: return@launch
            val next = (pager.currentItem + 1) % heroCount
            pager.setCurrentItem(next, true)
        }
    }

    private fun animationsEnabled(): Boolean {
        return try {
            Settings.Global.getFloat(
                requireContext().contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) > 0f
        } catch (_: Exception) {
            true
        }
    }

    // ------------------------------------------------------------------ sections

    private fun bindSections(rows: List<RealMediaRepository.HomeRow>) {
        val sections = rows.map { RealHomeSection(it.title, it.items) }
        binding.recyclerSections.adapter = RealHomeSectionAdapter(sections) { openDetail(it) }
    }

    // ------------------------------------------------------------------ continue watching

    private fun updateContinueWatchingRow() {
        if (_binding == null) return
        val history = runCatching { historyManager.getAllContinueWatching() }.getOrDefault(emptyList())
        if (history.isEmpty()) {
            binding.layoutContinueWatching.visibility = View.GONE
            return
        }
        binding.layoutContinueWatching.visibility = View.VISIBLE
        binding.recyclerContinueWatching.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerContinueWatching.adapter = ContinueWatchingAdapter(history)
    }

    private inner class ContinueWatchingAdapter(val items: List<PlaybackProgress>) :
        RecyclerView.Adapter<ContinueWatchingAdapter.Holder>() {

        inner class Holder(val b: ItemContinueWatchingBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val b = ItemContinueWatchingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(b)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val matched = allFetchedMedia.find { it.id == item.mediaId }
            val cover = item.mediaCover.ifEmpty {
                matched?.cover?.ifEmpty { matched.image } ?: ""
            }
            if (cover.isNotEmpty()) {
                holder.b.imgContinueCover.load(ImageUrlHelper.getOptimizedImageUrl(cover)) { crossfade(true) }
            }
            val title = item.mediaTitle.ifEmpty { matched?.title ?: "" }
            holder.b.tvContinueTitle.text = title.ifEmpty { "در حال تماشا" }

            val watchedMin = (item.positionMs / 60000).toInt()
            val totalMin = (item.durationMs / 60000).toInt()
            val ep = item.episodeTitle.trim()
            holder.b.tvContinueSubtitle.text = buildString {
                if (ep.isNotEmpty()) {
                    append(ep)
                    append(" • ")
                }
                if (totalMin > 0) {
                    append("$watchedMin از $totalMin دقیقه")
                } else {
                    append("$watchedMin دقیقه تماشا شده")
                }
            }
            holder.b.progressContinueBar.progress = item.progressPercent

            holder.b.root.setOnClickListener {
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra("video_title", if (ep.isNotEmpty()) "$title - $ep" else title)
                    putExtra("video_url", item.url)
                    putExtra("media_id", item.mediaId)
                    putExtra("media_title", title)
                    putExtra("media_cover", cover)
                    putExtra("episode_title", item.episodeTitle)
                    putExtra("episode_index", item.episodeIndex)
                    putExtra("start_position", item.positionMs)
                }
                startActivity(intent)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    // ------------------------------------------------------------------ actions

    private fun playOrOpen(item: RealMedia) {
        val playable = item.sources.firstOrNull {
            !it.quality.contains("تیزر") && !it.url.contains("tizer", true)
        }
        if (!item.isSeries && playable != null) {
            val banner = item.cover.ifEmpty { item.image }
            PlayerLauncherHelper.launchPlayer(
                activity = requireActivity(),
                playerChoice = historyManager.preferredPlayer,
                title = "${item.title} - ${playable.quality}",
                url = playable.url,
                mediaId = item.id,
                mediaCover = banner,
                episodeTitle = playable.quality,
                episodeIndex = 0,
                startPositionMs = 0L,
                mediaType = "movie",
                genres = ArrayList(item.genres.map { it.title })
            )
        } else {
            openDetail(item)
        }
    }

    private fun toggleWatchlist(item: RealMedia) {
        val added = favoritesManager.toggleFavorite(item)
        val message = if (added) "به واچ‌لیست اضافه شد" else "از واچ‌لیست حذف شد"
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun openDetail(item: RealMedia) {
        startActivity(
            Intent(requireContext(), DetailActivity::class.java).apply {
                putExtra("real_media", item)
            }
        )
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            bindGreeting()
            updateContinueWatchingRow()
            scheduleAutoScroll()
        }
    }

    override fun onPause() {
        super.onPause()
        autoScrollJob?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
        autoScrollJob?.cancel()
        _binding = null
    }
}
