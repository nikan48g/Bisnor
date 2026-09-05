package com.hnn.bisnor.util

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.hnn.bisnor.BuildConfig
import com.hnn.bisnor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object UpdateChecker {

    private const val GITHUB_OWNER = "nikan48g"
    private const val GITHUB_REPO = "Bisnor-autoupdate"

    // Dynamically retrieve the real app version from BuildConfig
    val currentAppVersionName: String
        get() = BuildConfig.VERSION_NAME

    val currentAppVersionCode: Int
        get() = BuildConfig.VERSION_CODE

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdates(context: Context, showToastIfLatest: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Bisnor-Android-App")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext
                    val json = JSONObject(body)
                    val rawTagName = json.optString("tag_name", "").trim()
                    val tagName = rawTagName.replace("v", "").trim()
                    val releaseNotes = json.optString("body", "تغییرات جدید و بهبود عملکرد کلی برنامه.")
                    val assets = json.optJSONArray("assets")
                    var downloadUrl = json.optString("html_url", "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases")

                    if (assets != null && assets.length() > 0) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.optString("name").endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", downloadUrl)
                                break
                            }
                        }
                    }

                    if (isNewerVersion(tagName, currentAppVersionName)) {
                        withContext(Dispatchers.Main) {
                            showFullScreenUpdateDialog(context, tagName, releaseNotes, downloadUrl)
                        }
                    } else if (showToastIfLatest) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "شما از آخرین نسخه بیسنور (v$currentAppVersionName) استفاده می‌کنید.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (showToastIfLatest) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "هنوز نسخه جدیدتری در مخزن منتشر نشده است.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            if (showToastIfLatest) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "خطا در برقراری ارتباط با سرور گیت‌هاب جهت بروزرسانی.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        if (remote.isEmpty()) return false
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    private fun showFullScreenUpdateDialog(
        context: Context,
        newVersion: String,
        notes: String,
        downloadUrl: String
    ) {
        val dialog = Dialog(context, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_fullscreen_update, null)
        dialog.setContentView(view)

        val tvCurrentVersion = view.findViewById<TextView>(R.id.tv_current_version)
        val tvNewVersion = view.findViewById<TextView>(R.id.tv_new_version)
        val tvChangelog = view.findViewById<TextView>(R.id.tv_changelog)
        val btnDownload = view.findViewById<MaterialButton>(R.id.btn_download_update)
        val btnClose = view.findViewById<ImageView>(R.id.btn_close_update)
        val btnRemind = view.findViewById<MaterialButton>(R.id.btn_remind_later)

        tvCurrentVersion.text = "v$currentAppVersionName"
        tvNewVersion.text = "v$newVersion"
        tvChangelog.text = if (notes.isNotBlank()) notes else "بهینه‌سازی کلی، رفع باگ‌ها و ارتقای سرعت پخش استریم."

        btnDownload.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        btnRemind.setOnClickListener { dialog.dismiss() }

        dialog.setCancelable(true)
        dialog.show()
    }
}
