package com.hnn.bisnor.util

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
        val root = if (volume == "primary") "حافظه داخلی" else "کارت حافظه"
        if (path.isEmpty()) root else "$root / $path"
    }.getOrDefault("پوشه انتخاب‌شده")

    /**
     * Asks where to save: the app folder, the last chosen folder, or a new one.
     * [pickFolder] must open the system folder picker and call [downloadVideo] with the result.
     */
    fun chooseAndDownload(context: Context, title: String, url: String, pickFolder: () -> Unit) {
        if (url.isEmpty()) {
            Toast.makeText(context, "آدرس دانلود نامعتبر است", Toast.LENGTH_SHORT).show()
            return
        }
        val actions = mutableListOf(
            ActionSheet.Action(R.drawable.ic_folder, "پوشه برنامه بیسنور") { downloadVideo(context, title, url) }
        )
        lastFolder(context)?.let { last ->
            actions += ActionSheet.Action(R.drawable.ic_folder, folderLabel(last)) { downloadVideo(context, title, url, last) }
        }
        actions += ActionSheet.Action(R.drawable.ic_more_horiz, "انتخاب پوشه دیگر…") { pickFolder() }
        ActionSheet.show(context, title = "کجا ذخیره شود؟", subtitle = title, actions = actions)
    }

    fun downloadVideo(context: Context, title: String, url: String, destTreeUri: String? = null) {
        if (url.isEmpty()) {
            Toast.makeText(context, "آدرس دانلود نامعتبر است", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            SegmentedDownloadManager.startDownload(context, title, url, destTreeUri)
            val where = destTreeUri?.let { " → ${folderLabel(it)}" } ?: ""
            Toast.makeText(context, "دانلود «$title» آغاز شد$where", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "خطا در شروع دانلود: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
