package com.hnn.bisnor.util

import android.content.Context
import android.widget.Toast
import com.hnn.bisnor.data.repository.SegmentedDownloadManager

object DownloadHelper {
    fun downloadVideo(context: Context, title: String, url: String) {
        if (url.isEmpty()) {
            Toast.makeText(context, "آدرس دانلود نامعتبر است", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            SegmentedDownloadManager.startDownload(context, title, url)
            Toast.makeText(context, "دانلود ۸ تکه‌ای «$title» آغاز شد", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "خطا در شروع دانلود: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
