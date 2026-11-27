package com.hnn.bisnor.ui.explore

import com.hnn.bisnor.util.Tr

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.hnn.bisnor.R
import com.hnn.bisnor.data.model.RealMedia
import com.hnn.bisnor.data.repository.PlaybackHistoryManager
import com.hnn.bisnor.data.repository.RealMediaRepository
import com.hnn.bisnor.databinding.FragmentExploreBinding
import com.hnn.bisnor.ui.adapter.RealMediaAdapter
import com.hnn.bisnor.ui.detail.DetailActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ExploreFragment : Fragment() {

    private var _binding: FragmentExploreBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: RealMediaAdapter
    private lateinit var historyManager: PlaybackHistoryManager
    private var searchJob: Job? = null
    private var performJob: Job? = null
    private var searchSeq = 0
    private var allLoaded = listOf<RealMedia>()

    // Filter state — every option maps to a real catalogue field.
    private var filterType: String = Tr.s(R.string.s_c1dfce9)
    private var filterContent: String = Tr.s(R.string.s_c1dfce9)
    private val selectedGenres = mutableSetOf<String>()
    private var filterCountry: String = Tr.s(R.string.s_c1dfce9)
    private var filterMinImdb: Float = 0.0f
    private var filterYearRange: String = Tr.s(R.string.s_c1dfce9)
    private var filterSort: String = Tr.s(R.string.s_ca3e0f8)
    private var searchScope: String = "all"

    private var randomizerIndex = 0
    private var randomCandidates = listOf<RealMedia>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExploreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        historyManager = PlaybackHistoryManager(requireContext())

        adapter = RealMediaAdapter(emptyList(), onItemClick = { openDetail(it) }, isGrid = true)
        binding.recyclerSearchResults.layoutManager =
            GridLayoutManager(requireContext(), resources.getInteger(R.integer.grid_columns_count))
        binding.recyclerSearchResults.adapter = adapter
        binding.recyclerSearchResults.itemAnimator = null
        com.hnn.bisnor.util.HeaderAutoHide.attach(binding.root as android.view.ViewGroup, binding.recyclerSearchResults.parent as android.view.View, listOf(binding.recyclerSearchResults))

        binding.etSearch.addTextChangedListener { text ->
            val q = text?.toString()?.trim() ?: ""
            binding.btnClearSearch.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
            // Local matches are painted on the same keystroke; the network gets a debounce.
            ++searchSeq
            performJob?.cancel()
            showLocalResults(q)
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(350)
                performSearch(q)
            }
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val q = binding.etSearch.text.toString().trim()
                if (q.isNotEmpty()) {
                    historyManager.addRecentSearch(q)
                    setupRecentSearches()
                }
                performSearch(q)
                true
            } else false
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
            performSearch("")
        }

        binding.btnClearRecentSearches.setOnClickListener {
            historyManager.clearRecentSearches()
            setupRecentSearches()
        }

        binding.btnOpenFiltersDialog.setOnClickListener { showComprehensiveFilterDialog() }
        binding.btnExploreRandomMovie.setOnClickListener { showRandomMovieDialog() }
        binding.btnResetFilters.setOnClickListener {
            resetFilters()
            binding.etSearch.setText("")
            performSearch("")
        }

        setupRecentSearches()

        val incomingQuery = arguments?.getString("search_query")
        val incomingScope = arguments?.getString("search_scope")
        if (!incomingScope.isNullOrEmpty()) {
            searchScope = incomingScope
        }
        if (!incomingQuery.isNullOrEmpty()) {
            binding.etSearch.setText(incomingQuery)
            binding.etSearch.setSelection(incomingQuery.length)
            performSearch(incomingQuery)
        } else {
            performSearch("")
        }
    }

    fun setSearchQueryAndScope(query: String, scope: String = "all") {
        searchScope = scope
        if (_binding != null) {
            binding.etSearch.setText(query)
            binding.etSearch.setSelection(query.length)
            performSearch(query)
        } else {
            arguments = Bundle().apply {
                putString("search_query", query)
                putString("search_scope", scope)
            }
        }
    }

    private fun showLocalResults(query: String) {
        if (_binding == null || query.isBlank()) return
        val local = runCatching { RealMediaRepository.localSearch(query) }.getOrDefault(emptyList())
        if (local.isEmpty()) return
        allLoaded = local
        filterList()
    }

    private fun performSearch(query: String) {
        if (_binding == null) return
        binding.progressSearch.visibility = View.VISIBLE
        binding.layoutEmptyResults.visibility = View.GONE
        // Only the latest search may publish: a slow default-catalog load must never
        // overwrite the results of a query typed after it.
        val seq = ++searchSeq
        performJob?.cancel()
        performJob = lifecycleScope.launch {
            val list = runCatching { RealMediaRepository.search(query) }.getOrDefault(emptyList())
            if (_binding == null || seq != searchSeq) return@launch
            binding.progressSearch.visibility = View.GONE
            allLoaded = list
            filterList()
        }
    }

    private fun filterList() {
        if (_binding == null) return
        var filtered = allLoaded
        val query = binding.etSearch.text?.toString()?.trim()?.lowercase() ?: ""

        if (searchScope == "actor") {
            filtered = filtered.filter { item ->
                val meta = com.hnn.bisnor.util.MediaMetadataHelper.parse(item.description, item.imdb)
                val actors = (meta.actors ?: "").lowercase()
                actors.isNotEmpty() && (query.isEmpty() || actors.contains(query) || item.description.lowercase().contains(query))
            }
        } else if (searchScope == "director") {
            filtered = filtered.filter { item ->
                val meta = com.hnn.bisnor.util.MediaMetadataHelper.parse(item.description, item.imdb)
                val director = (meta.director ?: "").lowercase()
                director.isNotEmpty() && (query.isEmpty() || director.contains(query) || item.description.lowercase().contains(query))
            }
        }

        if (filterType == Tr.s(R.string.s_c189a24)) filtered = filtered.filter { it.type == "movie" }
        else if (filterType == Tr.s(R.string.s_dee6fc4)) filtered = filtered.filter { it.isSeries }

        if (filterContent == Tr.s(R.string.s_cfa7558)) {
            filtered = filtered.filter { it.title.contains("دوبله") || it.description.contains("دوبله") }
        } else if (filterContent == Tr.s(R.string.s_87a4b33)) {
            filtered = filtered.filter { it.title.contains("زیرنویس") || it.description.contains("زیرنویس") }
        }

        if (selectedGenres.isNotEmpty()) {
            filtered = filtered.filter { item ->
                selectedGenres.any { sg -> item.genres.any { it.title.contains(sg, ignoreCase = true) } }
            }
        }

        if (filterCountry != Tr.s(R.string.s_c1dfce9)) {
            val needle = if (filterCountry.contains("کره")) "کره" else filterCountry
            if (needle.isNotEmpty()) {
                filtered = filtered.filter { item ->
                    item.country.any { it.title.contains(needle) } ||
                        item.genres.any { it.title.contains(needle) }
                }
            }
        }

        if (filterMinImdb > 0f) filtered = filtered.filter { it.imdb >= filterMinImdb }

        filtered = when {
            filterYearRange.contains("2024") || filterYearRange.contains("۲۰۲۴") -> filtered.filter { it.year >= 2024 }
            filterYearRange.contains("قبل") || filterYearRange.contains("Before") -> filtered.filter { it.year in 1..2019 }
            filterYearRange.contains("2020") || filterYearRange.contains("۲۰۲۰") -> filtered.filter { it.year in 2020..2023 }
            else -> filtered
        }

        filtered = when (filterSort) {
            Tr.s(R.string.s_460b303) -> filtered.sortedByDescending { it.imdb }
            Tr.s(R.string.s_c95d85a) -> filtered.sortedByDescending { it.year }
            else -> filtered
        }

        adapter.updateData(filtered)

        val hasQuery = query.isNotEmpty() || selectedGenres.isNotEmpty() ||
            filterType != Tr.s(R.string.s_c1dfce9) || filterContent != Tr.s(R.string.s_c1dfce9) || filterCountry != Tr.s(R.string.s_c1dfce9) ||
            filterMinImdb > 0f || filterYearRange != Tr.s(R.string.s_c1dfce9)
        binding.tvResultsCount.visibility = if (filtered.isNotEmpty()) View.VISIBLE else View.GONE
        binding.tvResultsCount.text = Tr.s(R.string.s_863ba5c, filtered.size)
        binding.layoutEmptyResults.visibility =
            if (filtered.isEmpty() && hasQuery) View.VISIBLE else View.GONE
        binding.recyclerSearchResults.visibility =
            if (filtered.isEmpty() && hasQuery) View.INVISIBLE else View.VISIBLE
        binding.tvEmptyTitle.text = Tr.s(R.string.s_c616574)
        binding.tvEmptyDesc.text = Tr.s(R.string.s_dbb0c09, query.ifBlank { Tr.s(R.string.s_667be3f) })
    }

    private fun resetFilters() {
        filterType = Tr.s(R.string.s_c1dfce9)
        filterContent = Tr.s(R.string.s_c1dfce9)
        selectedGenres.clear()
        filterCountry = Tr.s(R.string.s_c1dfce9)
        filterMinImdb = 0f
        filterYearRange = Tr.s(R.string.s_c1dfce9)
        filterSort = Tr.s(R.string.s_ca3e0f8)
        searchScope = "all"
    }

    private fun setupRecentSearches() {
        if (_binding == null) return
        val recents = historyManager.getRecentSearches()
        if (recents.isEmpty()) {
            binding.layoutRecentSearches.visibility = View.GONE
            return
        }
        binding.layoutRecentSearches.visibility = View.VISIBLE
        binding.chipGroupRecentSearches.removeAllViews()
        recents.forEach { query ->
            val chip = Chip(requireContext()).apply {
                text = query
                isClickable = true
                setOnClickListener {
                    binding.etSearch.setText(query)
                    binding.etSearch.setSelection(query.length)
                    performSearch(query)
                }
            }
            binding.chipGroupRecentSearches.addView(chip)
        }
    }

    // ---------------------------------------------------------------- filters dialog

    /** Every genre option comes from the live catalogue returned by the provider. */
    private fun buildDialogGenreChips(
        group: ChipGroup,
        items: List<RealMedia>,
        selected: Set<String>
    ) {
        group.removeAllViews()
        val genres = items.flatMap { it.genres }
            .map { it.title.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        genres.forEach { title ->
            val chip = Chip(requireContext()).apply {
                text = title
                isCheckable = true
                isChecked = selected.any { it.equals(title, ignoreCase = true) }
            }
            group.addView(chip)
        }
        group.visibility = if (genres.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showComprehensiveFilterDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_search_filter, null)
        val btnType = dialogView.findViewById<MaterialButton>(R.id.btn_filter_type)
        val btnContent = dialogView.findViewById<MaterialButton>(R.id.btn_filter_content)
        val scopeGroup = dialogView.findViewById<ChipGroup>(R.id.chip_group_dialog_scope)
        val genreGroup = dialogView.findViewById<ChipGroup>(R.id.chip_group_dialog_genres)
        val btnCountry = dialogView.findViewById<MaterialButton>(R.id.btn_filter_country)
        val sliderImdb = dialogView.findViewById<Slider>(R.id.slider_imdb)
        val tvImdbLabel = dialogView.findViewById<TextView>(R.id.tv_dialog_imdb_label)
        val btnYear = dialogView.findViewById<MaterialButton>(R.id.btn_filter_year)
        val btnSort = dialogView.findViewById<MaterialButton>(R.id.btn_filter_sort)
        val btnApply = dialogView.findViewById<View>(R.id.btn_dialog_apply)
        val btnReset = dialogView.findViewById<View>(R.id.btn_dialog_reset)

        var tempType = filterType
        var tempContent = filterContent
        val tempGenres = selectedGenres.toMutableSet()
        var tempCountry = filterCountry
        var tempYear = filterYearRange
        var tempSort = filterSort
        var tempMinImdb = filterMinImdb
        var tempScope = searchScope

        when (tempScope) {
            "actor" -> scopeGroup.check(R.id.chip_dialog_scope_actor)
            "director" -> scopeGroup.check(R.id.chip_dialog_scope_director)
            else -> scopeGroup.check(R.id.chip_dialog_scope_all)
        }
        scopeGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            tempScope = when (checkedIds.firstOrNull()) {
                R.id.chip_dialog_scope_actor -> "actor"
                R.id.chip_dialog_scope_director -> "director"
                else -> "all"
            }
        }
        buildDialogGenreChips(genreGroup, allLoaded, tempGenres)

        btnType.text = tempType
        btnContent.text = tempContent
        btnCountry.text = tempCountry
        btnYear.text = tempYear
        btnSort.text = tempSort
        sliderImdb.value = tempMinImdb
        tvImdbLabel.text = if (tempMinImdb > 0f) {
            Tr.s(R.string.s_54e65b0, String.format("%.1f", tempMinImdb))
        } else Tr.s(R.string.s_f48b0f8)

        sliderImdb.addOnChangeListener { _, value, _ ->
            tempMinImdb = value
            tvImdbLabel.text = if (value > 0f) {
                Tr.s(R.string.s_54e65b0, String.format("%.1f", value))
            } else Tr.s(R.string.s_f48b0f8)
        }

        btnType.setOnClickListener {
            val items = arrayOf(Tr.s(R.string.s_c1dfce9), Tr.s(R.string.s_c189a24), Tr.s(R.string.s_dee6fc4))
            MaterialAlertDialogBuilder(requireContext()).setTitle(Tr.s(R.string.s_7fdc425))
                .setItems(items) { _, which -> tempType = items[which]; btnType.text = tempType }.show()
        }
        btnContent.setOnClickListener {
            val items = arrayOf(Tr.s(R.string.s_c1dfce9), Tr.s(R.string.s_cfa7558), Tr.s(R.string.s_87a4b33))
            MaterialAlertDialogBuilder(requireContext()).setTitle(Tr.s(R.string.s_cf9d126))
                .setItems(items) { _, which -> tempContent = items[which]; btnContent.text = tempContent }.show()
        }
        btnCountry.setOnClickListener {
            val items = (listOf(Tr.s(R.string.s_c1dfce9)) + allLoaded.flatMap { it.country }.map { it.title }.filter { it.isNotBlank() }.distinct().sorted()).toTypedArray()
            MaterialAlertDialogBuilder(requireContext()).setTitle(Tr.s(R.string.s_4edca30))
                .setItems(items) { _, which -> tempCountry = items[which]; btnCountry.text = tempCountry }.show()
        }
        btnYear.setOnClickListener {
            val items = arrayOf(Tr.s(R.string.s_c1dfce9), Tr.s(R.string.s_640cc79), Tr.s(R.string.s_583c81a), Tr.s(R.string.s_b3e7f3e))
            MaterialAlertDialogBuilder(requireContext()).setTitle(Tr.s(R.string.s_7bb7894))
                .setItems(items) { _, which -> tempYear = items[which]; btnYear.text = tempYear }.show()
        }
        btnSort.setOnClickListener {
            val items = arrayOf(Tr.s(R.string.s_ca3e0f8), Tr.s(R.string.s_460b303), Tr.s(R.string.s_c95d85a))
            MaterialAlertDialogBuilder(requireContext()).setTitle(Tr.s(R.string.s_607fca7))
                .setItems(items) { _, which -> tempSort = items[which]; btnSort.text = tempSort }.show()
        }

        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create()

        btnReset.setOnClickListener {
            resetFilters()
            filterList()
            dialog.dismiss()
            Toast.makeText(requireContext(), Tr.s(R.string.s_c5be926), Toast.LENGTH_SHORT).show()
        }

        btnApply.setOnClickListener {
            filterType = tempType
            filterContent = tempContent
            selectedGenres.clear()
            selectedGenres.addAll(
                genreGroup.checkedChipIds.mapNotNull { id ->
                    (genreGroup.findViewById<Chip>(id))?.text?.toString()
                }
            )
            searchScope = tempScope
            filterCountry = tempCountry
            filterMinImdb = tempMinImdb
            filterYearRange = tempYear
            filterSort = tempSort
            filterList()
            dialog.dismiss()
        }

        dialog.show()
    }

    // ---------------------------------------------------------------- random picker

    private fun showRandomMovieDialog() {
        if (allLoaded.isEmpty()) {
            Toast.makeText(requireContext(), Tr.s(R.string.s_aa12b04), Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                allLoaded = RealMediaRepository.getExploreCatalog()
                showRandomMovieDialog()
            }
            return
        }

        val tasteDb = com.hnn.bisnor.data.database.SmartTasteDatabase(requireContext())
        val contentPref = tasteDb.getContentTypePreference()
        val favoriteGenres = tasteDb.getFavoriteGenres()
        val learnedGenres = tasteDb.getTopRecommendedGenres()
        val preferred = (favoriteGenres + learnedGenres).distinct()

        val filteredByType = allLoaded.filter { m ->
            when (contentPref) {
                "movie" -> !m.isSeries
                "series" -> m.isSeries
                else -> true
            }
        }
        val pool = if (filteredByType.isNotEmpty()) filteredByType else allLoaded

        // Deterministic scoring from real taste signals + real IMDb rating.
        val scored = pool.map { item ->
            var score = item.imdb * 1.5
            preferred.forEach { genre ->
                if (item.genres.any { it.title.contains(genre, ignoreCase = true) }) {
                    score += 6.0
                    if (favoriteGenres.contains(genre)) score += 4.0
                }
            }
            if (item.imdb >= 8.0) score += 5.0
            if (item.imdb in 0.1..5.9) score -= 10.0
            item to score
        }.sortedByDescending { it.second }

        randomCandidates = scored.take(25).map { it.first }
        if (randomCandidates.isEmpty()) {
            Toast.makeText(requireContext(), Tr.s(R.string.s_b95dad4), Toast.LENGTH_SHORT).show()
            return
        }

        randomizerIndex = 0
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_random_movie, null)
        val imgPreview = dialogView.findViewById<ImageView>(R.id.img_random_preview)
        val tvRating = dialogView.findViewById<TextView>(R.id.tv_random_rating)
        val tvMatchReason = dialogView.findViewById<TextView>(R.id.tv_random_match_reason)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_random_title)
        val tvGenreYear = dialogView.findViewById<TextView>(R.id.tv_random_genre_year)
        val tvDesc = dialogView.findViewById<TextView>(R.id.tv_random_desc)
        val btnPrev = dialogView.findViewById<MaterialButton>(R.id.btn_random_prev)
        val btnNext = dialogView.findViewById<MaterialButton>(R.id.btn_random_next)
        val btnWatch = dialogView.findViewById<MaterialButton>(R.id.btn_random_watch)

        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create()

        fun updateCard() {
            val item = randomCandidates[randomizerIndex % randomCandidates.size]
            imgPreview.load(
                com.hnn.bisnor.util.ImageUrlHelper.getOptimizedImageUrl(
                    item.cover.ifEmpty { item.image }
                )
            ) { crossfade(true) }
            tvRating.text = String.format("%.1f", item.imdb)

            val matched = item.genres.firstOrNull { g -> preferred.any { g.title.contains(it) } }?.title
                ?: preferred.firstOrNull()
            val typeBadge = if (item.isSeries) Tr.s(R.string.s_dee6fc4) else Tr.s(R.string.s_b063120)
            tvMatchReason.text = if (matched != null) {
                Tr.s(R.string.s_a32cc11, matched, typeBadge)
            } else {
                Tr.s(R.string.s_c427d5d, typeBadge)
            }

            tvTitle.text = item.title
            val genreStr = item.genres.joinToString(Tr.s(R.string.s_8a78cc5)) { it.title }
            val yearStr = if (item.year > 0) "${item.year} • " else ""
            tvGenreYear.text = "${yearStr}${typeBadge} • ${genreStr}"

            val meta = com.hnn.bisnor.util.MediaMetadataHelper.parse(
                item.description, item.imdb, isSeries = item.isSeries
            )
            tvDesc.text = meta.cleanStoryline

            btnWatch.setOnClickListener {
                dialog.dismiss()
                openDetail(item)
            }
        }

        btnNext.setOnClickListener {
            randomizerIndex = (randomizerIndex + 1) % randomCandidates.size
            updateCard()
        }
        btnPrev.setOnClickListener {
            randomizerIndex = if (randomizerIndex > 0) randomizerIndex - 1 else randomCandidates.size - 1
            updateCard()
        }

        updateCard()
        dialog.show()
    }

    private fun openDetail(item: RealMedia) {
        startActivity(
            Intent(requireContext(), DetailActivity::class.java).apply {
                putExtra("real_media", item)
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }
}
