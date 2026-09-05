package com.hnn.bisnor.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

object DownloadHelper {
    fun downloadVideo(context: Context, title: String, url: String) {
        if (url.isEmpty()) {
            Toast.makeText(context, "آدرس دانلود نامعتبر است", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val cleanUrl = url.trim().replace(" ", "%20")
            val cleanTitle = title.replace("[^a-zA-Z0-9آ-ی\\s]".toRegex(), "").trim()
            val fileName = "$cleanTitle.mkv"

            val request = DownloadManager.Request(Uri.parse(cleanUrl))
                .setTitle(title)
                .setDescription("در حال دانلود از بیسنور...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(context, "دانلود «$title» در پوشه Downloads آغاز شد", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "خطا در شروع دانلود: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
