package com.hnn.bisnor.util

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
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
    private const val GITHUB_REPO = "Bisnor"

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
        val wvChangelog = view.findViewById<android.webkit.WebView?>(R.id.wv_changelog)
        val tvChangelog = view.findViewById<TextView>(R.id.tv_changelog)
        val btnDownload = view.findViewById<MaterialButton>(R.id.btn_download_update)
        val btnClose = view.findViewById<ImageView>(R.id.btn_close_update)
        val btnRemind = view.findViewById<MaterialButton>(R.id.btn_remind_later)

        tvCurrentVersion.text = "v$currentAppVersionName"
        tvNewVersion.text = "v$newVersion"

        if (wvChangelog != null) {
            wvChangelog.settings.apply {
                javaScriptEnabled = false
                loadsImagesAutomatically = true
                domStorageEnabled = false
                defaultTextEncodingName = "utf-8"
            }
            wvChangelog.setBackgroundColor(0x00000000)
            val htmlData = buildChangelogHtml(notes)
            wvChangelog.loadDataWithBaseURL(null, htmlData, "text/html; charset=utf-8", "UTF-8", null)
        } else {
            val formattedNotes = formatChangelog(notes)
            tvChangelog.visibility = View.VISIBLE
            tvChangelog.text = if (formattedNotes.isNotBlank()) formattedNotes else "بهینه‌سازی کلی، رفع باگ‌ها و ارتقای سرعت پخش استریم."
        }

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

    private fun buildChangelogHtml(raw: String): String {
        val lines = raw.lines()
        val sb = StringBuilder()
        var inList = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (inList) {
                    sb.append("</ul>\n")
                    inList = false
                }
                continue
            }

            // Preserved HTML tags: <div ...>, </div>, <img ...>, <p ...>, </p>
            if (trimmed.startsWith("<div", ignoreCase = true) ||
                trimmed.startsWith("</div", ignoreCase = true) ||
                trimmed.startsWith("<center", ignoreCase = true) ||
                trimmed.startsWith("</center", ignoreCase = true) ||
                trimmed.startsWith("<p", ignoreCase = true) ||
                trimmed.startsWith("</p", ignoreCase = true)
            ) {
                if (inList) {
                    sb.append("</ul>\n")
                    inList = false
                }
                sb.append(trimmed).append("\n")
                continue
            }

            // Horizontal dividers
            if (trimmed.all { it == '-' || it == '*' || it == '_' } && trimmed.length >= 3) {
                if (inList) {
                    sb.append("</ul>\n")
                    inList = false
                }
                sb.append("<hr/>\n")
                continue
            }

            // Bullet points
            if (trimmed.startsWith("* ") || trimmed.startsWith("- ")) {
                if (!inList) {
                    sb.append("<ul>\n")
                    inList = true
                }
                val content = parseInlineMarkdown(trimmed.substring(2).trim())
                sb.append("<li>").append(content).append("</li>\n")
                continue
            } else if (inList) {
                sb.append("</ul>\n")
                inList = false
            }

            // Markdown Headers
            when {
                trimmed.startsWith("####") -> {
                    val h = parseInlineMarkdown(trimmed.removePrefix("####").trim())
                    sb.append("<h4>").append(h).append("</h4>\n")
                }
                trimmed.startsWith("###") -> {
                    val h = parseInlineMarkdown(trimmed.removePrefix("###").trim())
                    sb.append("<h3>").append(h).append("</h3>\n")
                }
                trimmed.startsWith("##") -> {
                    val h = parseInlineMarkdown(trimmed.removePrefix("##").trim())
                    sb.append("<h2>").append(h).append("</h2>\n")
                }
                trimmed.startsWith("#") -> {
                    val h = parseInlineMarkdown(trimmed.removePrefix("#").trim())
                    sb.append("<h1>").append(h).append("</h1>\n")
                }
                else -> {
                    val p = parseInlineMarkdown(trimmed)
                    sb.append("<p>").append(p).append("</p>\n")
                }
            }
        }

        if (inList) {
            sb.append("</ul>\n")
        }

        return """
            <!DOCTYPE html>
            <html dir="rtl" lang="fa">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
            <style>
              @import url('https://fonts.googleapis.com/css2?family=Vazirmatn:wght@300;400;600;700&display=swap');
              * { box-sizing: border-box; }
              body {
                background-color: transparent;
                color: #E2E8F0;
                font-family: 'Vazirmatn', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                font-size: 13.5px;
                line-height: 1.8;
                margin: 0;
                padding: 4px 2px;
                direction: rtl;
                text-align: right;
              }
              div[align="center"], [align="center"], center {
                text-align: center !important;
                direction: ltr !important;
              }
              img {
                max-width: 100%;
                height: auto;
                border-radius: 6px;
                vertical-align: middle;
                margin: 3px 2px;
                display: inline-block;
              }
              h1 {
                color: #FFB300;
                font-size: 20px;
                margin: 16px 0 8px 0;
                font-weight: 700;
              }
              h2 {
                color: #FFC107;
                font-size: 16.5px;
                margin: 14px 0 6px 0;
                font-weight: 700;
              }
              h3 {
                color: #FFD54F;
                font-size: 14.5px;
                margin: 12px 0 4px 0;
                font-weight: 600;
              }
              h4 {
                color: #FFE082;
                font-size: 13.5px;
                margin: 10px 0 4px 0;
                font-weight: 600;
              }
              p {
                margin: 6px 0;
              }
              ul {
                padding-right: 20px;
                padding-left: 0;
                margin: 6px 0;
              }
              li {
                margin-bottom: 4px;
              }
              a {
                color: #FFB300;
                text-decoration: none;
              }
              hr {
                border: none;
                border-top: 1px solid #2D3748;
                margin: 16px 0;
              }
              strong, b {
                color: #FFFFFF;
                font-weight: 700;
              }
            </style>
            </head>
            <body>
              ${sb.toString()}
            </body>
            </html>
        """.trimIndent()
    }

    private fun parseInlineMarkdown(text: String): String {
        var res = text

        // Linked images: [![alt](imgUrl)](linkUrl)
        res = res.replace(Regex("\\[!\\[([^\\]]*)\\]\\(([^\\)]+)\\)\\]\\(([^\\)]+)\\)")) { m ->
            val alt = m.groupValues[1]
            val img = m.groupValues[2]
            val link = m.groupValues[3]
            """<a href="$link"><img src="$img" alt="$alt" /></a>"""
        }

        // Markdown images: ![alt](url)
        res = res.replace(Regex("!\\[([^\\]]*)\\]\\(([^\\)]+)\\)")) { m ->
            val alt = m.groupValues[1]
            val img = m.groupValues[2]
            """<img src="$img" alt="$alt" />"""
        }

        // Markdown links: [text](url)
        res = res.replace(Regex("\\[([^\\]]+)\\]\\(([^\\)]+)\\)")) { m ->
            val t = m.groupValues[1]
            val u = m.groupValues[2]
            """<a href="$u">$t</a>"""
        }

        // Bold: **text** or __text__
        res = res.replace(Regex("\\*\\*(.*?)\\*\\*")) { "<strong>${it.groupValues[1]}</strong>" }
        res = res.replace(Regex("__(.*?)__")) { "<strong>${it.groupValues[1]}</strong>" }

        // Code: `code`
        res = res.replace(Regex("`([^`]+)`")) { "<code style=\"background:#1F2937; padding:2px 4px; border-radius:4px;\">${it.groupValues[1]}</code>" }

        return res
    }

    private fun formatChangelog(raw: String): CharSequence {
        if (raw.isBlank()) return ""
        val lines = raw.lines()
        val cleanedLines = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("<div", ignoreCase = true) || 
                trimmed.startsWith("</div", ignoreCase = true) ||
                trimmed.startsWith("<p", ignoreCase = true) ||
                trimmed.startsWith("</p", ignoreCase = true) ||
                trimmed.startsWith("<center", ignoreCase = true) ||
                trimmed.startsWith("</center", ignoreCase = true)) {
                continue
            }
            if (trimmed.all { it == '-' || it == '*' || it == '_' } && trimmed.length >= 3) continue
            if (trimmed.startsWith("![") && trimmed.contains("](")) continue
            if (trimmed.contains("<img", ignoreCase = true)) continue
            var cleanLine = trimmed
            when {
                cleanLine.startsWith("####") -> cleanLine = "▫️ " + cleanLine.removePrefix("####").trim()
                cleanLine.startsWith("###") -> cleanLine = "\n🔸 " + cleanLine.removePrefix("###").trim()
                cleanLine.startsWith("##") -> cleanLine = "\n💎 " + cleanLine.removePrefix("##").trim()
                cleanLine.startsWith("#") -> cleanLine = "\n🚀 " + cleanLine.removePrefix("#").trim()
            }
            if (cleanLine.startsWith("- ") || cleanLine.startsWith("* ")) {
                cleanLine = "  • " + cleanLine.substring(2).trim()
            }
            cleanLine = cleanLine.replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)")) { it.groupValues[1] }
            cleanLine = cleanLine.replace(Regex("<[^>]*>"), "")
            cleanLine = cleanLine.replace("**", "").replace("__", "").replace("`", "")
            val finalLine = cleanLine.trimEnd()
            if (finalLine.isNotBlank()) cleanedLines.add(finalLine)
        }
        return cleanedLines.joinToString("\n").trim()
    }
}
