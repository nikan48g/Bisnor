package com.hnn.bisnor.ui.library

import com.hnn.bisnor.util.Tr

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hnn.bisnor.MainActivity
import com.hnn.bisnor.R
import com.hnn.bisnor.data.model.RealMedia
import com.hnn.bisnor.data.repository.CustomPlaylist
import com.hnn.bisnor.data.repository.FavoritesManager
import com.hnn.bisnor.data.repository.PlaybackHistoryManager
import com.hnn.bisnor.data.repository.PlaylistsManager
import com.hnn.bisnor.databinding.FragmentLibraryBinding
import com.hnn.bisnor.databinding.ItemLibraryMediaBinding
import com.hnn.bisnor.databinding.ItemPlaylistRowBinding
import com.hnn.bisnor.ui.adapter.RealMediaAdapter
import com.hnn.bisnor.ui.detail.DetailActivity
import com.hnn.bisnor.ui.favorites.PlaylistDetailActivity
import com.hnn.bisnor.ui.player.PlayerActivity
import com.hnn.bisnor.util.ImageUrlHelper
import kotlinx.coroutines.launch

/**
 * The library is a view over genuine local/synced user data only:
 * real playback progress, the real watchlist and the user's real playlists.
 */
class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var favoritesManager: FavoritesManager
    private lateinit var playlistsManager: PlaylistsManager
    private lateinit var historyManager: PlaybackHistoryManager

    private var tab: String = TAB_LISTS
    private var sortMode: String = SORT_RECENT
    private var isGrid: Boolean = false
    private var query: String = ""

    private val libraryAdapter = LibraryListAdapter()
    private val playlistAdapter = PlaylistCardAdapter()

    private val prefs by lazy {
        requireContext().getSharedPreferences("bisnor_library_ui", android.content.Context.MODE_PRIVATE)
    }

    companion object {
        const val TAB_CONTINUE = "continue"
        const val TAB_WATCHLIST = "watchlist"
        const val TAB_WATCHED = "watched"
        const val TAB_LISTS = "lists"

        const val SORT_RECENT = "recent"
        const val SORT_ALPHA = "alpha"
        const val SORT_YEAR = "year"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        favoritesManager = FavoritesManager(requireContext())
        playlistsManager = PlaylistsManager(requireContext())
        historyManager = PlaybackHistoryManager(requireContext())

        isGrid = prefs.getBoolean("grid", false)
        applyLayoutIcon()

        binding.recyclerLibrary.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerLibrary.adapter = libraryAdapter
        com.hnn.bisnor.util.HeaderAutoHide.attach(binding.root as android.view.ViewGroup, binding.recyclerLibrary.parent as android.view.View, listOf(binding.recyclerLibrary, binding.recyclerPlaylistCards))

        binding.recyclerPlaylistCards.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPlaylistCards.adapter = playlistAdapter

        binding.chipGroupLibraryTabs.setOnCheckedStateChangeListener { _, checkedIds ->
            tab = when (checkedIds.firstOrNull()) {
                R.id.lib_tab_watchlist -> TAB_WATCHLIST
                R.id.lib_tab_watched -> TAB_WATCHED
                R.id.lib_tab_lists -> TAB_LISTS
                else -> TAB_CONTINUE
            }
            render()
        }
        syncTabChips()

        binding.etFavoritesSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString()?.trim()?.lowercase() ?: ""
                binding.btnClearFavoritesSearch.visibility =
                    if (query.isNotEmpty()) View.VISIBLE else View.GONE
                render()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.btnClearFavoritesSearch.setOnClickListener { binding.etFavoritesSearch.setText("") }
        binding.btnCreatePlaylist.setOnClickListener { showCreatePlaylistDialog() }
        binding.btnLibrarySort.setOnClickListener { showSortDialog() }
        binding.btnLibraryLayout.setOnClickListener {
            isGrid = !isGrid
            prefs.edit().putBoolean("grid", isGrid).apply()
            applyLayoutIcon()
            render()
        }
        binding.btnLibraryDiscover.setOnClickListener {
            if (tab == TAB_LISTS) showCreatePlaylistDialog()
            else (activity as? MainActivity)?.selectExploreTab()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            playlistsManager.playlistsFlow.collect { if (tab == TAB_LISTS && _binding != null) render() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            favoritesManager.favoritesFlow.collect { if (tab != TAB_LISTS && _binding != null) render() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) render()
    }

    fun selectTab(newTab: String) {
        tab = newTab
        if (_binding != null) {
            syncTabChips()
            render()
        }
    }

    private fun syncTabChips() {
        binding.chipGroupLibraryTabs.check(
            when (tab) {
                TAB_WATCHLIST -> R.id.lib_tab_watchlist
                TAB_WATCHED -> R.id.lib_tab_watched
                TAB_LISTS -> R.id.lib_tab_lists
                else -> R.id.lib_tab_continue
            }
        )
    }

    private fun applyLayoutIcon() {
        binding.btnLibraryLayout.setIconResource(
            if (isGrid) R.drawable.ic_grid else R.drawable.ic_list
        )
    }

    // ---------------------------------------------------------------- rendering

    private fun render() {
        if (_binding == null) return
        val listsTab = tab == TAB_LISTS
        binding.recyclerLibrary.visibility = if (listsTab) View.GONE else View.VISIBLE
        binding.btnLibraryLayout.visibility =
            if (listsTab || tab == TAB_CONTINUE) View.GONE else View.VISIBLE
        binding.btnLibrarySort.visibility = if (listsTab) View.GONE else View.VISIBLE

        if (listsTab) {
            val hasWatchlist = favoritesManager.favoritesFlow.value.isNotEmpty()
            val personalLists = playlistsManager.playlistsFlow.value.size
            val isEmpty = !hasWatchlist && personalLists == 0
            binding.recyclerPlaylistCards.visibility = if (isEmpty) View.GONE else View.VISIBLE
            if (!isEmpty) playlistAdapter.notifyDataSetChanged()
            // The main watchlist only counts once it has something in it.
            binding.tvLibraryCount.text = Tr.s(R.string.s_ab43d1b, personalLists + if (hasWatchlist) 1 else 0)
            binding.layoutLibraryEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.tvLibraryEmptyTitle.text = Tr.s(R.string.s_a3903f7)
            binding.tvLibraryEmptyDesc.text =
                Tr.s(R.string.s_201cff1)
            binding.btnLibraryDiscover.text = Tr.s(R.string.s_713f483)
            return
        }

        binding.recyclerPlaylistCards.visibility = View.GONE
        val entries = buildEntries()
        bindListOrGrid(entries)
        binding.tvLibraryCount.text = Tr.s(R.string.s_b7d6727, entries.size)
        binding.layoutLibraryEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

        when (tab) {
            TAB_CONTINUE -> {
                binding.tvLibraryEmptyTitle.text = Tr.s(R.string.s_47da447)
                binding.tvLibraryEmptyDesc.text =
                    Tr.s(R.string.s_239156e)
            }
            TAB_WATCHLIST -> {
                binding.tvLibraryEmptyTitle.text = Tr.s(R.string.s_3af850b)
                binding.tvLibraryEmptyDesc.text =
                    Tr.s(R.string.s_46759e3)
            }
            else -> {
                binding.tvLibraryEmptyTitle.text = Tr.s(R.string.s_359a46f)
                binding.tvLibraryEmptyDesc.text =
                    Tr.s(R.string.s_ca836d4)
            }
        }
        binding.btnLibraryDiscover.text = Tr.s(R.string.s_57fdd3d)
    }

    private data class Entry(
        val mediaId: Int,
        val title: String,
        val cover: String,
        val isSeries: Boolean,
        val year: Int,
        val progressPercent: Int,
        val episodeTitle: String,
        val metaLine: String,
        val timestamp: Long,
        val resumeUrl: String?,
        val media: RealMedia?
    )

    private fun buildEntries(): List<Entry> {
        val progressByMedia = historyManager.historyFlow.value.values
            .filter { it.mediaId != 0 }
            .groupBy { it.mediaId }
            .mapValues { (_, list) -> list.maxByOrNull { it.timestamp } }

        val entries = when (tab) {
            TAB_CONTINUE -> historyManager.getAllContinueWatching().map { p ->
                Entry(
                    mediaId = p.mediaId,
                    title = p.mediaTitle.ifBlank { Tr.s(R.string.s_f025339) },
                    cover = p.mediaCover,
                    isSeries = p.episodeTitle.isNotBlank(),
                    year = 0,
                    progressPercent = p.progressPercent,
                    episodeTitle = p.episodeTitle,
                    metaLine = buildString {
                        val watched = p.positionMs / 60000
                        val total = p.durationMs / 60000
                        if (total > 0) append(Tr.s(R.string.s_a8fdbeb, watched, total))
                        else append(Tr.s(R.string.s_3fddcda, watched))
                    },
                    timestamp = p.timestamp,
                    resumeUrl = p.url,
                    media = favoritesManager.favoritesFlow.value.find { it.id == p.mediaId }
                )
            }

            TAB_WATCHLIST -> favoritesManager.favoritesFlow.value.map { m ->
                val progress = progressByMedia[m.id]
                Entry(
                    mediaId = m.id,
                    title = m.title,
                    cover = m.image.ifEmpty { m.cover },
                    isSeries = m.isSeries,
                    year = m.year,
                    progressPercent = progress?.progressPercent ?: 0,
                    episodeTitle = progress?.episodeTitle ?: "",
                    metaLine = buildString {
                        append(if (m.isSeries) Tr.s(R.string.s_dee6fc4) else Tr.s(R.string.s_b063120))
                        if (m.year > 0) append(" • ${m.year}")
                        val genre = m.genres.firstOrNull()?.title
                        if (!genre.isNullOrBlank()) append(" • ${genre}")
                    },
                    timestamp = progress?.timestamp ?: 0L,
                    resumeUrl = progress?.url,
                    media = m
                )
            }

            else -> historyManager.historyFlow.value.values
                .filter { it.isFinished && it.mediaId != 0 }
                .distinctBy { it.mediaId }
                .map { p ->
                    Entry(
                        mediaId = p.mediaId,
                        title = p.mediaTitle.ifBlank { Tr.s(R.string.s_52b7246) },
                        cover = p.mediaCover,
                        isSeries = p.episodeTitle.isNotBlank(),
                        year = 0,
                        progressPercent = 100,
                        episodeTitle = p.episodeTitle,
                        metaLine = if (p.episodeTitle.isNotBlank()) Tr.s(R.string.s_2dfdf27) else Tr.s(R.string.s_52b7246),
                        timestamp = p.timestamp,
                        resumeUrl = null,
                        media = favoritesManager.favoritesFlow.value.find { it.id == p.mediaId }
                    )
                }
        }

        val filtered = entries.filter { e ->
            query.isEmpty() ||
                e.title.lowercase().contains(query) ||
                e.metaLine.lowercase().contains(query) ||
                (e.media?.genres?.any { it.title.contains(query, true) } == true)
        }

        return when (sortMode) {
            SORT_ALPHA -> filtered.sortedBy { it.title }
            SORT_YEAR -> filtered.sortedByDescending { it.year }
            else -> filtered.sortedByDescending { it.timestamp }
        }
    }

    private fun bindListOrGrid(entries: List<Entry>) {
        val gridForTab = isGrid && tab != TAB_CONTINUE
        if (gridForTab) {
            val span = resources.getInteger(R.integer.grid_columns_count)
            binding.recyclerLibrary.layoutManager = GridLayoutManager(requireContext(), span)
            val gridItems = entries.mapNotNull { it.media }.ifEmpty {
                entries.map { RealMedia(id = it.mediaId, title = it.title, image = it.cover, year = it.year) }
            }
            binding.recyclerLibrary.adapter = RealMediaAdapter(
                gridItems,
                onItemClick = { openDetail(it) },
                isGrid = true
            )
        } else {
            binding.recyclerLibrary.layoutManager = LinearLayoutManager(requireContext())
            binding.recyclerLibrary.adapter = libraryAdapter
            libraryAdapter.submit(entries)
        }
    }

    // ---------------------------------------------------------------- adapters

    private inner class LibraryListAdapter : RecyclerView.Adapter<LibraryListAdapter.Holder>() {
        private var items = listOf<Entry>()

        fun submit(newItems: List<Entry>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class Holder(val b: ItemLibraryMediaBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val b = ItemLibraryMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(b)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val e = items[position]
            val b = holder.b
            if (e.cover.isNotEmpty()) {
                b.imgLibraryPoster.load(ImageUrlHelper.getOptimizedImageUrl(e.cover)) { crossfade(true) }
            } else {
                b.imgLibraryPoster.setImageResource(R.drawable.ic_movie_placeholder)
            }
            b.tvLibraryTitle.text = e.title
            b.tvLibraryMeta.text = e.metaLine

            if (e.progressPercent in 1..99) {
                b.progressLibrary.visibility = View.VISIBLE
                b.progressLibrary.progress = e.progressPercent
            } else {
                b.progressLibrary.visibility = View.GONE
            }

            if (e.episodeTitle.isNotBlank() && tab == TAB_CONTINUE) {
                b.tvLibraryEpisode.visibility = View.VISIBLE
                b.tvLibraryEpisode.text = e.episodeTitle
            } else {
                b.tvLibraryEpisode.visibility = View.GONE
            }

            b.btnLibraryPlay.visibility = if (e.resumeUrl != null) View.VISIBLE else View.GONE
            b.btnLibraryPlay.setOnClickListener { resume(e.resumeUrl, e) }
            b.root.setOnClickListener {
                if (e.resumeUrl != null && tab == TAB_CONTINUE) resume(e.resumeUrl, e)
                else e.media?.let { openDetail(it) } ?: resume(null, e)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private inner class PlaylistCardAdapter : RecyclerView.Adapter<PlaylistCardAdapter.Holder>() {
        inner class Holder(val b: ItemPlaylistRowBinding) : RecyclerView.ViewHolder(b.root)

        private fun playlists(): List<CustomPlaylist> {
            val main = CustomPlaylist(
                id = "fav",
                name = Tr.s(R.string.s_8cf8d5d),
                items = favoritesManager.favoritesFlow.value.toMutableList()
            )
            val all = listOf(main) + playlistsManager.playlistsFlow.value
            if (query.isEmpty()) return all
            return all.filter { pl ->
                pl.name.lowercase().contains(query) || pl.items.any {
                    it.title.lowercase().contains(query) ||
                        it.genres.any { g -> g.title.contains(query, true) }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val b = ItemPlaylistRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(b)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val pl = playlists()[position]
            val b = holder.b
            b.tvPlaylistName.text = pl.name
            b.tvPlaylistCountBadge.text = Tr.s(R.string.s_b7d6727, pl.items.size)

            b.layoutPlaylistHeader.setOnClickListener { openPlaylist(pl) }
            b.root.setOnClickListener { openPlaylist(pl) }

            if (pl.id != "fav") {
                b.btnPlaylistDelete.visibility = View.VISIBLE
                b.btnPlaylistDelete.setOnClickListener {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(Tr.s(R.string.s_8ae4b6a))
                        .setMessage(Tr.s(R.string.s_a386b4b, pl.name))
                        .setPositiveButton(Tr.s(R.string.s_3b9854e)) { _, _ ->
                            playlistsManager.deletePlaylist(pl.id)
                            notifyDataSetChanged()
                        }
                        .setNegativeButton(Tr.s(R.string.s_c8d2a1f), null)
                        .show()
                }
            } else {
                b.btnPlaylistDelete.visibility = View.GONE
            }

            if (pl.items.isNotEmpty()) {
                b.tvPlaylistEmptyHint.visibility = View.GONE
                b.recyclerPlaylistItems.visibility = View.VISIBLE
                b.recyclerPlaylistItems.adapter = RealMediaAdapter(
                    pl.items.toList(),
                    onItemClick = { openDetail(it) }
                )
            } else {
                b.recyclerPlaylistItems.visibility = View.GONE
                b.tvPlaylistEmptyHint.visibility = View.VISIBLE
                b.tvPlaylistEmptyHint.text = Tr.s(R.string.s_2194f09)
            }
        }

        override fun getItemCount(): Int = playlists().size
    }

    // ---------------------------------------------------------------- actions

    private fun resume(url: String?, entry: Entry) {
        if (url.isNullOrBlank()) {
            entry.media?.let { openDetail(it) }
            return
        }
        val progress = historyManager.getProgressByUrl(url)
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra("video_title", entry.title)
            putExtra("video_url", url)
            putExtra("media_id", entry.mediaId)
            putExtra("media_title", entry.title)
            putExtra("media_cover", entry.cover)
            putExtra("episode_title", progress?.episodeTitle ?: entry.episodeTitle)
            putExtra("episode_index", progress?.episodeIndex ?: 0)
            putExtra("start_position", progress?.positionMs ?: 0L)
        }
        startActivity(intent)
    }

    private fun openDetail(item: RealMedia) {
        startActivity(
            Intent(requireContext(), DetailActivity::class.java).apply {
                putExtra("real_media", item)
            }
        )
    }

    private fun openPlaylist(pl: CustomPlaylist) {
        startActivity(
            Intent(requireContext(), PlaylistDetailActivity::class.java).apply {
                putExtra("playlist_id", pl.id)
                putExtra("playlist_name", pl.name)
            }
        )
    }

    private fun showCreatePlaylistDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }
        val input = EditText(requireContext()).apply {
            hint = Tr.s(R.string.s_bd2b43d)
        }
        val switchPublic = com.google.android.material.materialswitch.MaterialSwitch(requireContext()).apply {
            text = Tr.s(R.string.s_bd661c5)
            isChecked = false
            textSize = 13f
        }
        layout.addView(input)
        layout.addView(switchPublic)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(Tr.s(R.string.s_e0c8aab))
            .setView(layout)
            .setPositiveButton(Tr.s(R.string.s_2b89d69)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val authManager = com.hnn.bisnor.data.remote.AuthManager(requireContext())
                    playlistsManager.createPlaylist(
                        name,
                        isPublic = switchPublic.isChecked,
                        creator = authManager.currentUsername
                    )
                    Toast.makeText(requireContext(), Tr.s(R.string.s_3de037f, name), Toast.LENGTH_SHORT).show()
                    tab = TAB_LISTS
                    syncTabChips()
                    render()
                }
            }
            .setNegativeButton(Tr.s(R.string.s_c8d2a1f), null)
            .show()
    }

    private fun showSortDialog() {
        val labels = arrayOf(Tr.s(R.string.s_ab2eed0), Tr.s(R.string.s_5d26d08), Tr.s(R.string.s_43eaa75))
        val values = arrayOf(SORT_RECENT, SORT_ALPHA, SORT_YEAR)
        val current = values.indexOf(sortMode).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(Tr.s(R.string.s_a98ea44))
            .setSingleChoiceItems(labels, current) { dialog, which ->
                sortMode = values[which]
                binding.btnLibrarySort.text = labels[which]
                render()
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
