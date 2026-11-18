package com.hnn.bisnor.util

import com.hnn.bisnor.R
import com.hnn.bisnor.util.Tr

import android.content.Context
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hnn.bisnor.data.model.SharedPlaylistPayload
import com.hnn.bisnor.data.repository.PlaylistsManager
import com.hnn.bisnor.data.repository.SharedPlaylistImporter

/** Asks where an imported watchlist goes: a new list, or one of the user's existing lists. */
object ImportListDialog {

    fun show(context: Context, payload: SharedPlaylistPayload, onSaved: () -> Unit = {}) {
        val lists = PlaylistsManager(context).playlistsFlow.value
        val options = listOf(Tr.s(R.string.s_9bd2df4, payload.name)) +
            lists.map { Tr.s(R.string.s_3137f61, it.name, it.items.size) }

        MaterialAlertDialogBuilder(context)
            .setTitle(Tr.s(R.string.s_a029816, payload.name, payload.items.size))
            .setItems(options.toTypedArray()) { _, which ->
                val target = if (which == 0) null else lists[which - 1].id
                val saved = SharedPlaylistImporter.save(context, payload, target)
                val where = if (target == null) Tr.s(R.string.s_e0c8aab) else "«${lists[which - 1].name}»"
                val msg = if (saved == 0) Tr.s(R.string.s_b74f897, where) else Tr.s(R.string.s_1f028ac, saved, where)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                onSaved()
            }
            .setNegativeButton(Tr.s(R.string.s_c8d2a1f), null)
            .show()
    }
}
