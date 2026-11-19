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

    private data class External(val label: Int, val pkg: String)
    private val externals = listOf(
        External(R.string.dl_idm, "idm.internet.download.manager.plus"),
        External(R.string.dl_idm, "idm.internet.download.manager"),
        External(R.string.dl_adm, "com.dv.adm"),
        External(R.string.dl_adm, "com.dv.adm.pay"),
        External(R.string.dl_fdm, "org.freedownloadmanager.fdm")
    )

    private fun isInstalled(context: Context, pkg: String) =
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    private fun openWith(context: Context, pkg: String?, url: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setDataAndType(Uri.parse(url), "video/*")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            if (pkg != null) setPackage(pkg)
        }
        runCatching { context.startActivity(if (pkg == null) android.content.Intent.createChooser(intent, Tr.s(R.string.dl_title)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) else intent) }
            .onFailure { Toast.makeText(context, Tr.s(R.string.s_69cb93f, it.message), Toast.LENGTH_SHORT).show() }
    }

    /** First menu: Bisnor's manager, installed download managers, other apps, copy link. */
    fun chooseAndDownload(context: Context, title: String, url: String, pickFolder: () -> Unit) {
        if (url.isEmpty()) {
            Toast.makeText(context, Tr.s(R.string.s_4c666db), Toast.LENGTH_SHORT).show()
            return
        }
        val actions = mutableListOf(
            ActionSheet.Action(R.drawable.ic_download, Tr.s(R.string.dl_bisnor)) { chooseFolder(context, title, url, pickFolder) }
        )
        val seen = mutableSetOf<Int>()
        for (e in externals) {
            if (!isInstalled(context, e.pkg) || !seen.add(e.label)) continue
            actions += ActionSheet.Action(R.drawable.ic_download, Tr.s(e.label)) { openWith(context, e.pkg, url) }
        }
        actions += ActionSheet.Action(R.drawable.ic_more_horiz, Tr.s(R.string.dl_other)) { openWith(context, null, url) }
        actions += ActionSheet.Action(R.drawable.ic_share, Tr.s(R.string.dl_copy)) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("link", url))
            Toast.makeText(context, Tr.s(R.string.dl_copied), Toast.LENGTH_SHORT).show()
        }
        ActionSheet.show(context, title = Tr.s(R.string.dl_title), subtitle = title, actions = actions)
    }

    /** Where to save: the app folder, the last chosen folder, or a new one. */
    private fun chooseFolder(context: Context, title: String, url: String, pickFolder: () -> Unit) {
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
