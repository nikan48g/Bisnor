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

        adapter = RealMediaAdapter(emptyList()) { item ->
            val intent = Intent(this, DetailActivity::class.java).apply {
                putExtra("real_media", item)
            }
            startActivity(intent)
        }

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

        adapter.updateData(result)
        binding.tvEmptyPlaylistDetail.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
    }
}
