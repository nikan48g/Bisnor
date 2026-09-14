package com.hnn.bisnor.ui.favorites

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hnn.bisnor.R
import com.hnn.bisnor.data.model.RealMedia
import com.hnn.bisnor.data.repository.CustomPlaylist
import com.hnn.bisnor.data.repository.FavoritesManager
import com.hnn.bisnor.data.repository.PlaybackHistoryManager
import com.hnn.bisnor.data.repository.PlaylistsManager
import com.hnn.bisnor.databinding.FragmentFavoritesBinding
import com.hnn.bisnor.databinding.ItemPlaylistRowBinding
import com.hnn.bisnor.ui.adapter.RealMediaAdapter
import com.hnn.bisnor.ui.detail.DetailActivity
import kotlinx.coroutines.launch

class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    private lateinit var favoritesManager: FavoritesManager
    private lateinit var playlistsManager: PlaylistsManager
    private lateinit var historyManager: PlaybackHistoryManager
    private lateinit var playlistAdapter: PlaylistCardAdapter
    private var watchFilter: String = "all"
    private var favoritesSearchQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        favoritesManager = FavoritesManager(requireContext())
        playlistsManager = PlaylistsManager(requireContext())
        historyManager = PlaybackHistoryManager(requireContext())

        playlistAdapter = PlaylistCardAdapter()
        binding.recyclerPlaylistCards.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPlaylistCards.adapter = playlistAdapter

        binding.etFavoritesSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                favoritesSearchQuery = s?.toString()?.trim()?.lowercase() ?: ""
                binding.btnClearFavoritesSearch.visibility = if (favoritesSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                playlistAdapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.btnClearFavoritesSearch.setOnClickListener {
            binding.etFavoritesSearch.setText("")
        }

        binding.btnCreatePlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }

        binding.chipGroupWatchFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            watchFilter = when (checkedId) {
                R.id.chip_filter_watched -> "watched"
                R.id.chip_filter_unwatched -> "unwatched"
                else -> "all"
            }
            playlistAdapter.notifyDataSetChanged()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            playlistsManager.playlistsFlow.collect {
                playlistAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        playlistAdapter.notifyDataSetChanged()
    }

    private fun openPlaylistDetail(pl: CustomPlaylist) {
        val intent = Intent(requireContext(), PlaylistDetailActivity::class.java).apply {
            putExtra("playlist_id", pl.id)
            putExtra("playlist_name", pl.name)
        }
        startActivity(intent)
    }

    private fun showCreatePlaylistDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }
        val input = EditText(requireContext()).apply {
            hint = "نام واچ‌لیست (مثلا: انیمه‌ها یا فیلم‌های برتر)"
        }
        val switchPublic = com.google.android.material.materialswitch.MaterialSwitch(requireContext()).apply {
            text = "واچ‌لیست عمومی باشد (قابل اشتراک و جستجو)"
            isChecked = false
            textSize = 13f
        }
        layout.addView(input)
        layout.addView(switchPublic)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("ایجاد واچ‌لیست جدید")
            .setView(layout)
            .setPositiveButton("ایجاد") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val authManager = com.hnn.bisnor.data.remote.AuthManager(requireContext())
                    val isPub = switchPublic.isChecked
                    playlistsManager.createPlaylist(name, isPublic = isPub, creator = authManager.currentUsername)
                    Toast.makeText(requireContext(), "واچ‌لیست «$name» ایجاد شد", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    inner class PlaylistCardAdapter : RecyclerView.Adapter<PlaylistCardAdapter.Holder>() {

        private fun getDisplayPlaylists(): List<CustomPlaylist> {
            val favs = favoritesManager.favoritesFlow.value
            val mainFavPlaylist = CustomPlaylist(id = "fav", name = "⭐ واچ‌لیست اصلی (نشان‌شده‌ها)", items = favs.toMutableList())
            val userPlaylists = playlistsManager.playlistsFlow.value
            val allLists = listOf(mainFavPlaylist) + userPlaylists

            if (favoritesSearchQuery.isEmpty()) {
                return allLists
            }

            // Filter playlists that either match by playlist name or contain matching items
            return allLists.mapNotNull { pl ->
                val matchingItems = pl.items.filter { item ->
                    val titleMatch = item.title.lowercase().contains(favoritesSearchQuery)
                    val descMatch = item.description.lowercase().contains(favoritesSearchQuery)
                    val genreMatch = item.genres.any { it.title.contains(favoritesSearchQuery, ignoreCase = true) }
                    val meta = com.hnn.bisnor.util.MediaMetadataHelper.parse(item.description, item.imdb)
                    val actorMatch = (meta.actors ?: "").lowercase().contains(favoritesSearchQuery)
                    val directorMatch = (meta.director ?: "").lowercase().contains(favoritesSearchQuery)

                    titleMatch || descMatch || genreMatch || actorMatch || directorMatch
                }
                val plNameMatch = pl.name.lowercase().contains(favoritesSearchQuery)

                if (plNameMatch || matchingItems.isNotEmpty()) {
                    pl.copy(items = if (matchingItems.isNotEmpty()) matchingItems.toMutableList() else pl.items)
                } else {
                    null
                }
            }
        }

        inner class Holder(val b: ItemPlaylistRowBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val b = ItemPlaylistRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(b)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val allLists = getDisplayPlaylists()
            if (position >= allLists.size) return
            val pl = allLists[position]

            holder.b.tvPlaylistName.text = pl.name

            val filteredItems = when (watchFilter) {
                "watched" -> pl.items.filter { historyManager.isMediaWatched(it.id) }
                "unwatched" -> pl.items.filter { !historyManager.isMediaWatched(it.id) }
                else -> pl.items
            }

            holder.b.tvPlaylistCountBadge.text = "${filteredItems.size} اثر"

            // Clicking on header or card expands to full PlaylistDetailActivity
            holder.b.layoutPlaylistHeader.setOnClickListener {
                openPlaylistDetail(pl)
            }
            holder.b.root.setOnClickListener {
                openPlaylistDetail(pl)
            }

            // Delete button for custom playlists only
            if (pl.id != "fav") {
                holder.b.btnPlaylistDelete.visibility = View.VISIBLE
                holder.b.btnPlaylistDelete.setOnClickListener {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("حذف لیست")
                        .setMessage("آیا می‌خواهید لیست «${pl.name}» را حذف کنید؟")
                        .setPositiveButton("حذف") { _, _ ->
                            playlistsManager.deletePlaylist(pl.id)
                            notifyDataSetChanged()
                        }
                        .setNegativeButton("انصراف", null)
                        .show()
                }
            } else {
                holder.b.btnPlaylistDelete.visibility = View.GONE
            }

            if (filteredItems.isNotEmpty()) {
                holder.b.tvPlaylistEmptyHint.visibility = View.GONE
                holder.b.recyclerPlaylistItems.visibility = View.VISIBLE
                val innerAdapter = RealMediaAdapter(filteredItems) { item ->
                    val intent = Intent(requireContext(), DetailActivity::class.java).apply {
                        putExtra("real_media", item)
                    }
                    startActivity(intent)
                }
                holder.b.recyclerPlaylistItems.adapter = innerAdapter
            } else {
                holder.b.recyclerPlaylistItems.visibility = View.GONE
                holder.b.tvPlaylistEmptyHint.visibility = View.VISIBLE
                holder.b.tvPlaylistEmptyHint.text = if (watchFilter != "all") "اثری با این فیلتر در این لیست یافت نشد." else "این لیست خالی است. برای ورود کلیک کنید یا در صفحه فیلم‌ها روی نشان بزنید."
            }
        }

        override fun getItemCount(): Int = getDisplayPlaylists().size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
