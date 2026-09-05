package com.hnn.bisnor.ui.explore

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
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
    private var allLoaded = listOf<RealMedia>()

    // Filter states
    private var filterType: String = "مهم نیست" // سینمایی, سریال, مهم نیست
    private var filterContent: String = "مهم نیست" // دوبله, زیرنویس, مهم نیست
    private val selectedGenres = mutableSetOf<String>()
    private var filterCountry: String = "مهم نیست" // کره, ژاپن, ترکیه, آمریکا, مهم نیست
    private var filterMinImdb: Float = 0.0f
    private var filterYearRange: String = "مهم نیست" // ۲۰۲۴ - ۲۰۲۶, ۲۰۲۰ - ۲۰۲۳, قبل از ۲۰۲۰, مهم نیست
    private var filterSort: String = "جدیدترین" // جدیدترین, بالاترین نمره IMDb

    // Randomizer
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

        adapter = RealMediaAdapter(emptyList()) { item ->
            val intent = Intent(requireContext(), DetailActivity::class.java).apply {
                putExtra("real_media", item)
            }
            startActivity(intent)
        }

        val spanCount = resources.getInteger(R.integer.grid_columns_count)
        binding.recyclerSearchResults.layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.recyclerSearchResults.adapter = adapter

        binding.etSearch.addTextChangedListener { text ->
            val q = text?.toString()?.trim() ?: ""
            binding.btnClearSearch.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(400)
                if (q.isNotEmpty()) {
                    historyManager.addRecentSearch(q)
                    setupRecentSearches()
                }
                performSearch(q)
            }
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val q = binding.etSearch.text.toString().trim()
                if (q.isNotEmpty()) {
                    historyManager.addRecentSearch(q)
                    setupRecentSearches()
                    performSearch(q)
                }
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

        binding.btnOpenFiltersDialog.setOnClickListener {
            showComprehensiveFilterDialog()
        }

        binding.btnExploreRandomMovie.setOnClickListener {
            showRandomMovieDialog()
        }

        setupRecentSearches()
        performSearch("")
    }

    private fun showComprehensiveFilterDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_search_filter, null)

        val btnType = dialogView.findViewById<MaterialButton>(R.id.btn_filter_type)
        val btnContent = dialogView.findViewById<MaterialButton>(R.id.btn_filter_content)
        val btnGenres = dialogView.findViewById<MaterialButton>(R.id.btn_filter_genres)
        val btnCountry = dialogView.findViewById<MaterialButton>(R.id.btn_filter_country)
        val sliderImdb = dialogView.findViewById<Slider>(R.id.slider_imdb)
        val tvImdbLabel = dialogView.findViewById<TextView>(R.id.tv_dialog_imdb_label)
        val btnYear = dialogView.findViewById<MaterialButton>(R.id.btn_filter_year)
        val btnSort = dialogView.findViewById<MaterialButton>(R.id.btn_filter_sort)

        val btnApply = dialogView.findViewById<View>(R.id.btn_dialog_apply)
        val btnReset = dialogView.findViewById<View>(R.id.btn_dialog_reset)

        btnType.text = filterType
        btnContent.text = filterContent
        btnGenres.text = if (selectedGenres.isEmpty()) "مهم نیست" else "${selectedGenres.size} ژانر انتخاب شد"
        btnCountry.text = filterCountry
        btnYear.text = filterYearRange
        btnSort.text = filterSort
        sliderImdb.value = filterMinImdb
        tvImdbLabel.text = if (filterMinImdb > 0f) "امتیاز IMDb: حداقل ${String.format("%.1f", filterMinImdb)}" else "امتیاز IMDb: مهم نیست"

        sliderImdb.addOnChangeListener { _, value, _ ->
            filterMinImdb = value
            tvImdbLabel.text = if (value > 0f) "امتیاز IMDb: حداقل ${String.format("%.1f", value)}" else "امتیاز IMDb: مهم نیست"
        }

        // 1. Type Dropdown
        btnType.setOnClickListener {
            val items = arrayOf("مهم نیست", "سینمایی", "سریال")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("دسته:")
                .setItems(items) { _, which ->
                    filterType = items[which]
                    btnType.text = filterType
                }.show()
        }

        // 2. Content Dropdown
        btnContent.setOnClickListener {
            val items = arrayOf("مهم نیست", "دوبله", "زیرنویس", "زیرنویس جدا")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("محتوا:")
                .setItems(items) { _, which ->
                    filterContent = items[which]
                    btnContent.text = filterContent
                }.show()
        }

        // 3. Multi-Genre Checkbox Modal (As shown in screenshot)
        btnGenres.setOnClickListener {
            val allGenresList = arrayOf(
                "کمدی", "اکشن", "عاشقانه", "ترسناک", "علمی تخیلی", "آخرالزمانی",
                "انیمیشن", "انیمه", "تاریخی", "درام", "مستند", "جنایی", "ماجراجویی", "خانوادگی"
            )
            val checkedItems = BooleanArray(allGenresList.size) { i -> selectedGenres.contains(allGenresList[i]) }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("انتخاب ژانر (چند موردی)")
                .setMultiChoiceItems(allGenresList, checkedItems) { _, which, isChecked ->
                    val g = allGenresList[which]
                    if (isChecked) selectedGenres.add(g) else selectedGenres.remove(g)
                }
                .setPositiveButton("تایید") { _, _ ->
                    btnGenres.text = if (selectedGenres.isEmpty()) "مهم نیست" else "${selectedGenres.size} ژانر انتخاب شد"
                }
                .show()
        }

        // 4. Country Dropdown
        btnCountry.setOnClickListener {
            val items = arrayOf("مهم نیست", "کره جنوبی (K-Drama)", "ژاپن (انیمه)", "ترکیه", "ایران", "آمریکا و بین‌الملل")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("کشور:")
                .setItems(items) { _, which ->
                    filterCountry = items[which]
                    btnCountry.text = filterCountry
                }.show()
        }

        // 5. Year Range Dropdown
        btnYear.setOnClickListener {
            val items = arrayOf("مهم نیست", "۲۰۲۴ - ۲۰۲۶", "۲۰۲۰ - ۲۰۲۳", "قبل از ۲۰۲۰")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("سال انتشار:")
                .setItems(items) { _, which ->
                    filterYearRange = items[which]
                    btnYear.text = filterYearRange
                }.show()
        }

        // 6. Sort Dropdown
        btnSort.setOnClickListener {
            val items = arrayOf("جدیدترین", "بالاترین نمره IMDb", "سال ساخت")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("ترتیب بر اساس:")
                .setItems(items) { _, which ->
                    filterSort = items[which]
                    btnSort.text = filterSort
                }.show()
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        btnReset.setOnClickListener {
            filterType = "مهم نیست"
            filterContent = "مهم نیست"
            selectedGenres.clear()
            filterCountry = "مهم نیست"
            filterMinImdb = 0.0f
            filterYearRange = "مهم نیست"
            filterSort = "جدیدترین"
            filterList()
            dialog.dismiss()
            Toast.makeText(requireContext(), "تمام فیلترها پاک شدند", Toast.LENGTH_SHORT).show()
        }

        btnApply.setOnClickListener {
            filterList()
            dialog.dismiss()
            Toast.makeText(requireContext(), "فیلترها اعمال شدند", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showRandomMovieDialog() {
        val pool = allLoaded.filter { it.imdb >= 6.5 && it.image.isNotEmpty() }
        randomCandidates = if (pool.isNotEmpty()) pool.shuffled() else allLoaded.shuffled()
        if (randomCandidates.isEmpty()) {
            Toast.makeText(requireContext(), "در حال دریافت فیلم‌ها...", Toast.LENGTH_SHORT).show()
            return
        }

        randomizerIndex = 0
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_random_movie, null)
        val imgPreview = dialogView.findViewById<ImageView>(R.id.img_random_preview)
        val tvRating = dialogView.findViewById<TextView>(R.id.tv_random_rating)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_random_title)
        val tvGenreYear = dialogView.findViewById<TextView>(R.id.tv_random_genre_year)
        val tvDesc = dialogView.findViewById<TextView>(R.id.tv_random_desc)
        val btnPrev = dialogView.findViewById<MaterialButton>(R.id.btn_random_prev)
        val btnNext = dialogView.findViewById<MaterialButton>(R.id.btn_random_next)
        val btnWatch = dialogView.findViewById<MaterialButton>(R.id.btn_random_watch)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        fun updateCard() {
            val item = randomCandidates[randomizerIndex % randomCandidates.size]
            val banner = if (item.cover.isNotEmpty()) item.cover else item.image
            imgPreview.load(banner) { crossfade(true) }
            tvRating.text = String.format("%.1f", item.imdb)
            tvTitle.text = item.title
            val genreStr = item.genres.joinToString("، ") { it.title }
            val yearStr = if (item.year > 0) "${item.year} • " else ""
            val typeStr = if (item.type == "serie") "سریال" else "سینمایی"
            tvGenreYear.text = "$yearStr$typeStr • $genreStr"
            tvDesc.text = if (item.description.isNotEmpty()) item.description else "توضیحاتی موجود نیست."

            btnWatch.setOnClickListener {
                dialog.dismiss()
                val intent = Intent(requireContext(), DetailActivity::class.java).apply {
                    putExtra("real_media", item)
                }
                startActivity(intent)
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

    private fun setupRecentSearches() {
        if (_binding == null) return
        val recents = historyManager.getRecentSearches()
        if (recents.isNotEmpty()) {
            binding.layoutRecentSearches.visibility = View.VISIBLE
            binding.chipGroupRecentSearches.removeAllViews()
            for (query in recents) {
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
        } else {
            binding.layoutRecentSearches.visibility = View.GONE
        }
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            val list = RealMediaRepository.search(query)
            allLoaded = list
            filterList()
        }
    }

    private fun filterList() {
        var filtered = allLoaded

        // 1. Type
        if (filterType == "سینمایی") {
            filtered = filtered.filter { it.type == "movie" }
        } else if (filterType == "سریال") {
            filtered = filtered.filter { it.type == "serie" }
        }

        // 2. Content
        if (filterContent == "دوبله") {
            filtered = filtered.filter { it.title.contains("دوبله") || it.description.contains("دوبله") }
        } else if (filterContent == "زیرنویس") {
            filtered = filtered.filter { it.title.contains("زیرنویس") || it.description.contains("زیرنویس") }
        }

        // 3. Multi Genres
        if (selectedGenres.isNotEmpty()) {
            filtered = filtered.filter { item ->
                selectedGenres.any { sg -> item.genres.any { g -> g.title.contains(sg, ignoreCase = true) } }
            }
        }

        // 4. Country
        if (filterCountry.contains("کره")) {
            filtered = filtered.filter { it.country.any { c -> c.title.contains("کره") } || it.title.contains("کره") }
        } else if (filterCountry.contains("ژاپن")) {
            filtered = filtered.filter { it.country.any { c -> c.title.contains("ژاپن") } || it.genres.any { g -> g.title.contains("انیمه") } }
        } else if (filterCountry.contains("ترکیه")) {
            filtered = filtered.filter { it.country.any { c -> c.title.contains("ترکیه") } || it.title.contains("ترکی") }
        }

        // 5. Min IMDb
        if (filterMinImdb > 0.0f) {
            filtered = filtered.filter { it.imdb >= filterMinImdb }
        }

        // 6. Year Range
        filtered = when {
            filterYearRange.contains("۲۰۲۴") -> filtered.filter { it.year >= 2024 }
            filterYearRange.contains("۲۰۲۰") -> filtered.filter { it.year in 2020..2023 }
            filterYearRange.contains("قبل") -> filtered.filter { it.year in 1..2019 }
            else -> filtered
        }

        // 7. Sort
        filtered = when (filterSort) {
            "بالاترین نمره IMDb" -> filtered.sortedByDescending { it.imdb }
            "سال ساخت" -> filtered.sortedByDescending { it.year }
            else -> filtered
        }

        adapter.updateData(filtered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
