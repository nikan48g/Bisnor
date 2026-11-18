package com.hnn.bisnor.util

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
        val options = listOf("＋  ساخت لیست جدید «${payload.name}»") +
            lists.map { "${it.name}  ·  ${it.items.size} اثر" }

        MaterialAlertDialogBuilder(context)
            .setTitle("«${payload.name}» · ${payload.items.size} اثر\nکجا ذخیره شود؟")
            .setItems(options.toTypedArray()) { _, which ->
                val target = if (which == 0) null else lists[which - 1].id
                val saved = SharedPlaylistImporter.save(context, payload, target)
                val where = if (target == null) "لیست جدید" else "«${lists[which - 1].name}»"
                val msg = if (saved == 0) "همه‌ی این آثار از قبل در $where بودند" else "$saved اثر به $where اضافه شد"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                onSaved()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
}
