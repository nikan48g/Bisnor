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
    val isDownloaded: Boolean,
    val isSegmented: Boolean = true,
    val speedBytesPerSec: Long = 0L
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

    val isRunning: Boolean
        get() = status == SegmentedTask.STATUS_RUNNING || status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING
}

object LocalDownloadManager {

    fun getDownloadedVideos(context: Context): List<DownloadedFile> {
        val list = mutableListOf<DownloadedFile>()

        // 1. Get all tasks from SegmentedDownloadManager
        val segTasks = SegmentedDownloadManager.getAllTasks()
        for (st in segTasks) {
            val isSuccess = st.status == SegmentedTask.STATUS_SUCCESS
            list.add(
                DownloadedFile(
                    id = st.id,
                    title = st.title,
                    fileUri = "file://${st.filePath}",
                    filePath = st.filePath,
                    totalBytes = st.totalBytes,
                    bytesDownloaded = st.bytesDownloaded.get(),
                    status = st.status,
                    isDownloaded = isSuccess,
                    isSegmented = st.isSegmented,
                    speedBytesPerSec = st.speedBytesPerSec
                )
            )
        }

        // 2. Check local directories (both app private downloads and public downloads)
        val candidateDirs = listOfNotNull(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )

        for (dir in candidateDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.filter { it.isFile && it.extension.lowercase() in listOf("mkv", "mp4", "webm") }?.forEach { f ->
                    val cleanName = f.nameWithoutExtension
                    if (list.none { it.filePath == f.absolutePath || it.title.contains(cleanName, ignoreCase = true) }) {
                        list.add(
                            DownloadedFile(
                                id = f.hashCode().toLong(),
                                title = cleanName,
                                fileUri = f.toURI().toString(),
                                filePath = f.absolutePath,
                                totalBytes = f.length(),
                                bytesDownloaded = f.length(),
                                status = SegmentedTask.STATUS_SUCCESS,
                                isDownloaded = true,
                                isSegmented = true,
                                speedBytesPerSec = 0L
                            )
                        )
                    }
                }
            }
        }

        // 3. Check legacy Android DownloadManager
        try {
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

                    if (list.none { it.id == id || it.title == title }) {
                        list.add(
                            DownloadedFile(
                                id = id,
                                title = title,
                                fileUri = uri,
                                filePath = uri,
                                totalBytes = bytes,
                                bytesDownloaded = downloadedBytes,
                                status = status,
                                isDownloaded = isSuccess,
                                isSegmented = false,
                                speedBytesPerSec = 0L
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        return list.sortedByDescending { it.id }
    }

    fun deleteDownload(context: Context, item: DownloadedFile): Boolean {
        try {
            // Cancel and remove from SegmentedDownloadManager
            SegmentedDownloadManager.cancelDownload(item.id)

            // Remove file if exists
            if (item.filePath.isNotEmpty()) {
                val f = File(item.filePath)
                if (f.exists()) f.delete()
            }

            // Remove from system DownloadManager
            try {
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadManager.remove(item.id)
            } catch (_: Exception) {}

            return true
        } catch (e: Exception) {
            return false
        }
    }
}
