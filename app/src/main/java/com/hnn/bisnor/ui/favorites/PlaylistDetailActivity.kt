package com.hnn.bisnor.ui.favorites

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.GridLayoutManager
import com.hnn.bisnor.R
import com.hnn.bisnor.data.model.RealMedia
import com.hnn.bisnor.data.repository.CustomPlaylist
import com.hnn.bisnor.data.repository.FavoritesManager
import com.hnn.bisnor.data.repository.PlaybackHistoryManager
import com.hnn.bisnor.data.repository.PlaylistsManager
import com.hnn.bisnor.databinding.ActivityPlaylistDetailBinding
import com.hnn.bisnor.ui.adapter.RealMediaAdapter
import com.hnn.bisnor.ui.detail.DetailActivity
import com.hnn.bisnor.util.ThemeHelper

class PlaylistDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistDetailBinding
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var playlistsManager: PlaylistsManager
    private lateinit var historyManager: PlaybackHistoryManager
    private lateinit var adapter: RealMediaAdapter

    private var playlistId: String = "fav"
    private var playlistName: String = "لیست"
    private var allPlaylistItems = listOf<RealMedia>()
    private var currentFilter: String = "all"
    private var currentSearchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playlistId = intent.getStringExtra("playlist_id") ?: "fav"
        playlistName = intent.getStringExtra("playlist_name") ?: "لیست فیلم‌ها"

        favoritesManager = FavoritesManager(this)
        playlistsManager = PlaylistsManager(this)
        historyManager = PlaybackHistoryManager(this)

        binding.toolbarPlaylistDetail.title = playlistName
        binding.toolbarPlaylistDetail.setNavigationOnClickListener { finish() }
        binding.toolbarPlaylistDetail.inflateMenu(R.menu.menu_playlist_detail)
        binding.toolbarPlaylistDetail.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_share_playlist -> {
                    sharePlaylistLink()
                    true
                }
                R.id.action_sort_playlist -> {
                    showSortDialog()
                    true
                }
                R.id.action_toggle_privacy -> {
                    showPrivacyDialog()
                    true
                }
                else -> false
            }
        }

        adapter = RealMediaAdapter(emptyList(), onItemClick = { item ->
            showMediaOptionsDialog(item)
        }, isGrid = true)

        val spanCount = resources.getInteger(R.integer.grid_columns_count)
        binding.recyclerPlaylistDetailGrid.layoutManager = GridLayoutManager(this, spanCount)
        binding.recyclerPlaylistDetailGrid.adapter = adapter

        binding.etPlaylistSearch.addTextChangedListener { text ->
            currentSearchQuery = text?.toString()?.trim() ?: ""
            binding.btnPlaylistClearSearch.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
            filterAndDisplayList()
        }

        binding.btnPlaylistClearSearch.setOnClickListener {
            binding.etPlaylistSearch.setText("")
        }

        binding.chipGroupDetailFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            currentFilter = when (checkedId) {
                R.id.chip_detail_watched -> "watched"
                R.id.chip_detail_unwatched -> "unwatched"
                else -> "all"
            }
            filterAndDisplayList()
        }

        loadPlaylistData()
    }

    override fun onResume() {
        super.onResume()
        loadPlaylistData()
    }

    private fun loadPlaylistData() {
        if (playlistId == "fav") {
            allPlaylistItems = favoritesManager.favoritesFlow.value
        } else {
            val pl = playlistsManager.playlistsFlow.value.find { it.id == playlistId }
            allPlaylistItems = pl?.items ?: emptyList()
        }
        filterAndDisplayList()
    }

    private var currentSortOrder: String = "default" // default, user_rating, imdb_high, title_az

    private fun filterAndDisplayList() {
        var result = allPlaylistItems

        // Search Query filter
        if (currentSearchQuery.isNotEmpty()) {
            result = result.filter {
                it.title.contains(currentSearchQuery, ignoreCase = true) ||
                it.description.contains(currentSearchQuery, ignoreCase = true) ||
                it.genres.any { g -> g.title.contains(currentSearchQuery, ignoreCase = true) }
            }
        }

        // Watched / Unwatched filter
        result = when (currentFilter) {
            "watched" -> result.filter { historyManager.isMediaWatched(it.id) }
            "unwatched" -> result.filter { !historyManager.isMediaWatched(it.id) }
            else -> result
        }

        // Sorting & Ranking
        result = when (currentSortOrder) {
            "user_rating" -> result.sortedByDescending { playlistsManager.getReview(playlistId, it.id)?.userRating ?: 0f }
            "imdb_high" -> result.sortedByDescending { it.imdb }
            "title_az" -> result.sortedBy { it.title }
            else -> result
        }

        adapter.updateData(result)
        binding.tvEmptyPlaylistDetail.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showSortDialog() {
        val options = arrayOf(
            "پیش‌فرض (ترتیب افزودن)",
            "⭐ نمره و امتیاز من (بالاترین)",
            "🏆 نمره IMDb (بالاترین)",
            "🔤 الفبایی (عنوان اثر)"
        )
        val keys = arrayOf("default", "user_rating", "imdb_high", "title_az")
        val currentIndex = keys.indexOf(currentSortOrder).coerceAtLeast(0)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("رتبه‌بندی و مرتب‌سازی لیست")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                currentSortOrder = keys[which]
                filterAndDisplayList()
                dialog.dismiss()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showPrivacyDialog() {
        if (playlistId == "fav") {
            android.widget.Toast.makeText(this, "لیست نشان‌شده‌های اصلی همواره خصوصی است.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val pl = playlistsManager.playlistsFlow.value.find { it.id == playlistId } ?: return
        val currentPublic = pl.isPublic
        val statusText = if (currentPublic) "عمومی (قابل مشاهده برای سایرین)" else "خصوصی (فقط در دستگاه شما)"

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("حریم خصوصی واچ‌لیست")
            .setMessage("وضعیت فعلی: $statusText\n\nآیا می‌خواهید وضعیت این واچ‌لیست را تغییر دهید؟")
            .setPositiveButton(if (currentPublic) "خصوصی کردن 🔒" else "عمومی کردن 🌍") { _, _ ->
                playlistsManager.setPlaylistPublic(playlistId, !currentPublic)
                loadPlaylistData()
                android.widget.Toast.makeText(this, "وضعیت لیست تغییر یافت.", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showMediaOptionsDialog(item: RealMedia) {
        val existingReview = playlistsManager.getReview(playlistId, item.id)
        val reviewLabel = if (existingReview != null) "⭐ مشاهده و ویرایش نمره من (${existingReview.userRating}/10)" else "⭐ ثبت نظر و امتیاز به این اثر"

        val options = arrayOf(
            "▶ تماشای فیلم / سریال",
            reviewLabel,
            "🗑️ حذف از این واچ‌لیست"
        )

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(item.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(this, DetailActivity::class.java).apply {
                            putExtra("real_media", item)
                        }
                        startActivity(intent)
                    }
                    1 -> showReviewDialog(item)
                    2 -> {
                        if (playlistId == "fav") {
                            favoritesManager.toggleFavorite(item)
                        } else {
                            playlistsManager.removeFromPlaylist(playlistId, item.id)
                        }
                        loadPlaylistData()
                        android.widget.Toast.makeText(this, "از لیست حذف شد", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showReviewDialog(item: RealMedia) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_media_review, null)
        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tv_review_movie_name)
        val tvRatingVal = dialogView.findViewById<android.widget.TextView>(R.id.tv_review_rating_val)
        val slider = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.slider_user_rating)
        val etComment = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_review_comment)
        val btnSave = dialogView.findViewById<android.view.View>(R.id.btn_save_review)
        val btnCancel = dialogView.findViewById<android.view.View>(R.id.btn_cancel_review)

        tvTitle.text = item.title

        val existing = playlistsManager.getReview(playlistId, item.id)
        if (existing != null) {
            slider.value = existing.userRating.coerceIn(1.0f, 10.0f)
            tvRatingVal.text = "⭐ ${existing.userRating} / 10"
            etComment.setText(existing.reviewText)
        } else {
            slider.value = 8.0f
            tvRatingVal.text = "⭐ 8.0 / 10"
        }

        slider.addOnChangeListener { _, value, _ ->
            tvRatingVal.text = "⭐ ${String.format("%.1f", value)} / 10"
        }

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val userScore = slider.value
            val userText = etComment.text?.toString()?.trim() ?: ""
            playlistsManager.addReview(playlistId, item.id, userScore, userText)
            dialog.dismiss()
            filterAndDisplayList()
            android.widget.Toast.makeText(this, "امتیاز و یادداشت شما با موفقیت ثبت شد!", android.widget.Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun sharePlaylistLink() {
        if (allPlaylistItems.isEmpty()) {
            android.widget.Toast.makeText(this, "لیست خالی قابل اشتراک‌گذاری نیست.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val payload = com.hnn.bisnor.data.model.SharedPlaylistPayload(
            name = playlistName,
            items = allPlaylistItems
        )
        val json = payload.toJson()
        val compressedBase64 = com.hnn.bisnor.data.remote.SupabaseManager.compressString(json)
        val urlSafeBase64 = java.net.URLEncoder.encode(compressedBase64, "UTF-8")

        val shareLink = "bisnor://app/playlist?data=$urlSafeBase64"
        val message = "🎬 واچ‌لیست «$playlistName» در بیسنور شامل ${allPlaylistItems.size} فیلم و سریال:\n$shareLink\n\n(با لمس این لینک، لیست مستقیماً در اپلیکیشن بیسنور باز می‌شود)"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "اشتراک‌گذاری واچ‌لیست «$playlistName» در بیسنور")
            putExtra(Intent.EXTRA_TEXT, message)
        }
        startActivity(Intent.createChooser(intent, "اشتراک‌گذاری لینک واچ‌لیست با دوستان:"))
    }
}
