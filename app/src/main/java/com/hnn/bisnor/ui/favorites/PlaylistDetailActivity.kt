package com.hnn.bisnor.ui.favorites

import com.hnn.bisnor.util.Tr

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
    private var playlistName: String = Tr.s(R.string.s_6936f97)
    private var allPlaylistItems = listOf<RealMedia>()
    private var currentFilter: String = "all"
    private var currentSearchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playlistId = intent.getStringExtra("playlist_id") ?: "fav"
        playlistName = intent.getStringExtra("playlist_name") ?: Tr.s(R.string.s_bec777c)

        favoritesManager = FavoritesManager(this)
        playlistsManager = PlaylistsManager(this)
        historyManager = PlaybackHistoryManager(this)

        binding.toolbarPlaylistDetail.title = playlistName
        binding.toolbarPlaylistDetail.setNavigationOnClickListener { finish() }
        binding.toolbarPlaylistDetail.inflateMenu(R.menu.menu_playlist_detail)
        binding.toolbarPlaylistDetail.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId != R.id.action_more) return@setOnMenuItemClickListener false
            com.hnn.bisnor.util.ActionSheet.show(
                this,
                title = playlistName,
                subtitle = Tr.s(R.string.s_b7d6727, allPlaylistItems.size),
                actions = listOf(
                    com.hnn.bisnor.util.ActionSheet.Action(R.drawable.ic_share, Tr.s(R.string.s_f78763a)) { sharePlaylistLink() },
                    com.hnn.bisnor.util.ActionSheet.Action(R.drawable.ic_filter, Tr.s(R.string.s_3c75edb)) { showSortDialog() },
                    com.hnn.bisnor.util.ActionSheet.Action(R.drawable.ic_lock, Tr.s(R.string.s_677feb1)) { showPrivacyDialog() }
                )
            )
            true
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

        // Search Query filter (Title, Description, Genres, Actors, Director)
        if (currentSearchQuery.isNotEmpty()) {
            val q = currentSearchQuery.lowercase()
            result = result.filter { item ->
                val titleMatch = item.title.contains(q, ignoreCase = true)
                val descMatch = item.description.contains(q, ignoreCase = true)
                val genreMatch = item.genres.any { g -> g.title.contains(q, ignoreCase = true) }
                val meta = com.hnn.bisnor.util.MediaMetadataHelper.parse(item.description, item.imdb)
                val actorMatch = (meta.actors ?: "").contains(q, ignoreCase = true)
                val directorMatch = (meta.director ?: "").contains(q, ignoreCase = true)

                titleMatch || descMatch || genreMatch || actorMatch || directorMatch
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
            Tr.s(R.string.s_7b7156e),
            Tr.s(R.string.s_dd659dc),
            Tr.s(R.string.s_4832a06),
            Tr.s(R.string.s_74414a6)
        )
        val keys = arrayOf("default", "user_rating", "imdb_high", "title_az")
        val currentIndex = keys.indexOf(currentSortOrder).coerceAtLeast(0)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(Tr.s(R.string.s_1f4cd58))
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                currentSortOrder = keys[which]
                filterAndDisplayList()
                dialog.dismiss()
            }
            .setNegativeButton(Tr.s(R.string.s_c8d2a1f), null)
            .show()
    }

    private fun showPrivacyDialog() {
        if (playlistId == "fav") {
            android.widget.Toast.makeText(this, Tr.s(R.string.s_6ac2923), android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val pl = playlistsManager.playlistsFlow.value.find { it.id == playlistId } ?: return
        val currentPublic = pl.isPublic
        val statusText = if (currentPublic) Tr.s(R.string.s_cdb8409) else Tr.s(R.string.s_701a5dd)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(Tr.s(R.string.s_8c6cbaf))
            .setMessage(Tr.s(R.string.s_23d77f2, statusText))
            .setPositiveButton(if (currentPublic) Tr.s(R.string.s_37ff1fa) else Tr.s(R.string.s_53aefd9)) { _, _ ->
                playlistsManager.setPlaylistPublic(playlistId, !currentPublic)
                loadPlaylistData()
                android.widget.Toast.makeText(this, Tr.s(R.string.s_ebcf625), android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(Tr.s(R.string.s_c8d2a1f), null)
            .show()
    }

    private fun showMediaOptionsDialog(item: RealMedia) {
        val existingReview = playlistsManager.getReview(playlistId, item.id)
        val reviewLabel = if (existingReview != null) Tr.s(R.string.s_e3be830, existingReview.userRating) else Tr.s(R.string.s_9f7b18e)
        val meta = listOfNotNull(
            if (item.isSeries) Tr.s(R.string.s_dee6fc4) else Tr.s(R.string.s_b063120),
            item.year.takeIf { it > 0 }?.toString()
        ).joinToString(" · ")

        com.hnn.bisnor.util.ActionSheet.show(
            this,
            title = item.title,
            subtitle = meta,
            imageUrl = item.image.ifEmpty { item.cover },
            actions = listOf(
                com.hnn.bisnor.util.ActionSheet.Action(R.drawable.ic_play, Tr.s(R.string.s_7e4e55e)) {
                    startActivity(Intent(this, DetailActivity::class.java).putExtra("real_media", item))
                },
                com.hnn.bisnor.util.ActionSheet.Action(R.drawable.ic_star, reviewLabel) { showReviewDialog(item) },
                com.hnn.bisnor.util.ActionSheet.Action(R.drawable.ic_delete, Tr.s(R.string.s_45d3be0), destructive = true) {
                    if (playlistId == "fav") {
                        favoritesManager.toggleFavorite(item)
                    } else {
                        playlistsManager.removeFromPlaylist(playlistId, item.id)
                    }
                    loadPlaylistData()
                    android.widget.Toast.makeText(this, Tr.s(R.string.s_b8ee3d7), android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        )
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
            android.widget.Toast.makeText(this, Tr.s(R.string.s_583487a), android.widget.Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun sharePlaylistLink() {
        if (allPlaylistItems.isEmpty()) {
            android.widget.Toast.makeText(this, Tr.s(R.string.s_677797d), android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val payload = com.hnn.bisnor.data.model.SharedPlaylistPayload(
            name = playlistName,
            items = allPlaylistItems
        )
        lifecycleScope.launch {
        // Short /s/<code> link when the worker can store the list, long ?data= link otherwise.
        val shareLink = com.hnn.bisnor.data.repository.SharedPlaylistImporter.shareLink(payload)
        val message = Tr.s(R.string.s_4d11dbf, playlistName, allPlaylistItems.size, shareLink)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, Tr.s(R.string.s_636fee2, playlistName))
            putExtra(Intent.EXTRA_TEXT, message)
        }
        startActivity(Intent.createChooser(intent, Tr.s(R.string.s_f3b4235)))
        }
    }
}
