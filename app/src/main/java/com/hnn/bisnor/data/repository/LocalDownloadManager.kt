package com.hnn.bisnor.data.repository

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.os.Environment
import java.io.File

data class DownloadedFile(
    val id: Long,
    val title: String,
    val fileUri: String,
    val filePath: String,
    val totalBytes: Long,
    val bytesDownloaded: Long,
    val status: Int,
    val isDownloaded: Boolean
)

object LocalDownloadManager {

    fun getDownloadedVideos(context: Context): List<DownloadedFile> {
        val list = mutableListOf<DownloadedFile>()
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query()
        val cursor: Cursor? = downloadManager.query(query)

        cursor?.use {
            val idIndex = it.getColumnIndex(DownloadManager.COLUMN_ID)
            val titleIndex = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val uriIndex = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val bytesIndex = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val downloadedIndex = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)

            while (it.moveToNext()) {
                val id = if (idIndex != -1) it.getLong(idIndex) else 0L
                val title = if (titleIndex != -1) it.getString(titleIndex) ?: "دانلود" else "دانلود"
                val uri = if (uriIndex != -1) it.getString(uriIndex) ?: "" else ""
                val bytes = if (bytesIndex != -1) it.getLong(bytesIndex) else 0L
                val downloadedBytes = if (downloadedIndex != -1) it.getLong(downloadedIndex) else 0L
                val status = if (statusIndex != -1) it.getInt(statusIndex) else 0

                val isSuccess = status == DownloadManager.STATUS_SUCCESSFUL
                list.add(DownloadedFile(id, title, uri, uri, bytes, downloadedBytes, status, isSuccess))
            }
        }

        // Check Downloads directory for Bisnor mkv/mp4 files
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir.exists() && downloadsDir.isDirectory) {
            downloadsDir.listFiles()?.filter { it.extension.lowercase() in listOf("mkv", "mp4", "webm") }?.forEach { f ->
                if (list.none { it.title.contains(f.nameWithoutExtension, ignoreCase = true) }) {
                    list.add(DownloadedFile(f.hashCode().toLong(), f.nameWithoutExtension, f.toURI().toString(), f.absolutePath, f.length(), f.length(), DownloadManager.STATUS_SUCCESSFUL, true))
                }
            }
        }

        return list.sortedByDescending { it.id }
    }

    fun deleteDownload(context: Context, item: DownloadedFile): Boolean {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(item.id)

            if (item.filePath.isNotEmpty()) {
                val f = File(item.filePath)
                if (f.exists()) f.delete()
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }
}
