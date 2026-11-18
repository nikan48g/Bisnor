package com.hnn.bisnor.util

import com.hnn.bisnor.util.Tr

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import com.hnn.bisnor.R
import com.hnn.bisnor.data.repository.SegmentedDownloadManager

object DownloadHelper {

    private const val PREFS = "bisnor_download_prefs"
    private const val KEY_FOLDER = "last_folder_tree"

    /** Last folder the user picked (SAF tree uri), if any. */
    fun lastFolder(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FOLDER, null)

    fun rememberFolder(context: Context, tree: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_FOLDER, tree.toString()).apply()
    }

    /** "primary:Movies/Bisnor" → "Movies/Bisnor", "1234-ABCD:Films" → "کارت حافظه / Films". */
    fun folderLabel(tree: String): String = runCatching {
        val id = DocumentsContract.getTreeDocumentId(Uri.parse(tree))
        val (volume, path) = id.substringBefore(':') to id.substringAfter(':', "")
        val root = if (volume == "primary") Tr.s(R.string.s_548449e) else Tr.s(R.string.s_223d440)
        if (path.isEmpty()) root else "${root} / ${path}"
    }.getOrDefault(Tr.s(R.string.s_8c1967a))

    /**
     * Asks where to save: the app folder, the last chosen folder, or a new one.
     * [pickFolder] must open the system folder picker and call [downloadVideo] with the result.
     */
    fun chooseAndDownload(context: Context, title: String, url: String, pickFolder: () -> Unit) {
        if (url.isEmpty()) {
            Toast.makeText(context, Tr.s(R.string.s_4c666db), Toast.LENGTH_SHORT).show()
            return
        }
        val actions = mutableListOf(
            ActionSheet.Action(R.drawable.ic_folder, Tr.s(R.string.s_4eb5a88)) { downloadVideo(context, title, url) }
        )
        lastFolder(context)?.let { last ->
            actions += ActionSheet.Action(R.drawable.ic_folder, folderLabel(last)) { downloadVideo(context, title, url, last) }
        }
        actions += ActionSheet.Action(R.drawable.ic_more_horiz, Tr.s(R.string.s_c6bbceb)) { pickFolder() }
        ActionSheet.show(context, title = Tr.s(R.string.s_ec74a16), subtitle = title, actions = actions)
    }

    fun downloadVideo(context: Context, title: String, url: String, destTreeUri: String? = null) {
        if (url.isEmpty()) {
            Toast.makeText(context, Tr.s(R.string.s_4c666db), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            SegmentedDownloadManager.startDownload(context, title, url, destTreeUri)
            val where = destTreeUri?.let { " → ${folderLabel(it)}" } ?: ""
            Toast.makeText(context, Tr.s(R.string.s_3760a44, title, where), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, Tr.s(R.string.s_40edd6d, e.message), Toast.LENGTH_SHORT).show()
        }
    }
}
