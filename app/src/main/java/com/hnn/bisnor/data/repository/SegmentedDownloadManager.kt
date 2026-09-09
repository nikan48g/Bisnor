package com.hnn.bisnor.data.repository

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.hnn.bisnor.BisnorApp
import com.hnn.bisnor.ui.downloads.DownloadsActivity
import com.hnn.bisnor.util.InAppNotificationHelper
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class SegmentedTask(
    val id: Long,
    val title: String,
    val url: String,
    val filePath: String,
    var totalBytes: Long = 0L,
    val bytesDownloaded: AtomicLong = AtomicLong(0L),
    var isSegmented: Boolean = true,
    var partsCount: Int = 8,
    var status: Int = STATUS_RUNNING,
    var speedBytesPerSec: Long = 0L,
    var lastBytes: Long = 0L,
    var lastSpeedCheckTime: Long = System.currentTimeMillis(),
    @Transient var job: Job? = null
) {
    companion object {
        const val STATUS_RUNNING = 1
        const val STATUS_SUCCESS = 2
        const val STATUS_FAILED = 3
        const val STATUS_CANCELLED = 4
    }

    val progressPercent: Int
        get() = if (totalBytes > 0) ((bytesDownloaded.get() * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

    val isRunning: Boolean
        get() = status == STATUS_RUNNING

    val isDownloaded: Boolean
        get() = status == STATUS_SUCCESS
}

object SegmentedDownloadManager {

    private val tasks = ConcurrentHashMap<Long, SegmentedTask>()
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private const val PREFS_NAME = "bisnor_segmented_downloads"
    private const val KEY_TASKS = "tasks_json"

    fun init(context: Context) {
        loadPersistedTasks(context)
    }

    fun getDownloadDirectory(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun startDownload(context: Context, title: String, url: String): Long {
        val cleanUrl = url.trim().replace(" ", "%20")
        val cleanTitle = title.replace("[^a-zA-Z0-9آ-ی\\s._-]".toRegex(), "").trim()
        val fileName = "$cleanTitle.mkv"

        val dir = getDownloadDirectory(context)
        val targetFile = File(dir, fileName)

        val id = System.currentTimeMillis()
        val task = SegmentedTask(
            id = id,
            title = title,
            url = cleanUrl,
            filePath = targetFile.absolutePath,
            status = SegmentedTask.STATUS_RUNNING
        )
        tasks[id] = task
        saveTasks(context)

        task.job = downloadScope.launch {
            runDownload(context, task, targetFile, cleanUrl)
        }

        return id
    }

    private suspend fun runDownload(
        context: Context,
        task: SegmentedTask,
        targetFile: File,
        initialUrl: String
    ) = withContext(Dispatchers.IO) {
        try {
            var rangeSupported = false
            var totalLength = -1L
            var finalUrl = initialUrl

            try {
                val probeRequest = Request.Builder()
                    .url(initialUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept", "*/*")
                    .addHeader("Range", "bytes=0-0")
                    .build()

                httpClient.newCall(probeRequest).execute().use { response ->
                    finalUrl = response.request.url.toString()
                    if (response.code == 206) {
                        rangeSupported = true
                        val cr = response.header("Content-Range")
                        if (!cr.isNullOrEmpty() && cr.contains("/")) {
                            totalLength = cr.substringAfterLast("/").trim().toLongOrNull() ?: -1L
                        }
                    } else if (response.isSuccessful) {
                        rangeSupported = false
                        totalLength = response.body?.contentLength() ?: -1L
                    }
                }
            } catch (e: Exception) {
                // If probe fails, continue with fallback
            }

            if (totalLength <= 0L) {
                try {
                    val headRequest = Request.Builder()
                        .url(finalUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .head()
                        .build()
                    httpClient.newCall(headRequest).execute().use { response ->
                        totalLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                        val ar = response.header("Accept-Ranges")
                        if (ar?.contains("bytes", ignoreCase = true) == true) {
                            rangeSupported = true
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }

            task.totalBytes = totalLength
            task.isSegmented = rangeSupported && totalLength > 8 * 1024 * 1024L
            task.partsCount = if (task.isSegmented) 8 else 1
            saveTasks(context)

            if (totalLength > 0L) {
                try {
                    RandomAccessFile(targetFile, "rw").use { raf ->
                        if (raf.length() < totalLength) {
                            raf.setLength(totalLength)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore pre-allocation failure
                }
            }

            if (task.isSegmented && totalLength > 0L) {
                val numParts = 8
                val partSize = totalLength / numParts
                val partJobs = (0 until numParts).map { partIdx ->
                    downloadScope.async {
                        val start = partIdx * partSize
                        val end = if (partIdx == numParts - 1) totalLength - 1 else ((partIdx + 1) * partSize - 1)

                        val partReq = Request.Builder()
                            .url(finalUrl)
                            .addHeader("User-Agent", USER_AGENT)
                            .addHeader("Range", "bytes=$start-$end")
                            .build()

                        httpClient.newCall(partReq).execute().use { response ->
                            if (!response.isSuccessful && response.code != 206) {
                                throw IOException("Segment $partIdx failed with code ${response.code}")
                            }
                            val body = response.body ?: throw IOException("Empty body for part $partIdx")
                            val inputStream = body.byteStream()
                            val buffer = ByteArray(64 * 1024)
                            RandomAccessFile(targetFile, "rw").use { raf ->
                                raf.seek(start)
                                var read: Int
                                while (inputStream.read(buffer).also { read = it } != -1 && isActive) {
                                    raf.write(buffer, 0, read)
                                    task.bytesDownloaded.addAndGet(read.toLong())
                                }
                            }
                        }
                    }
                }
                partJobs.awaitAll()
            } else {
                val req = Request.Builder()
                    .url(finalUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .build()

                httpClient.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Failed with code ${response.code}")
                    val body = response.body ?: throw IOException("Empty body")
                    val inputStream = body.byteStream()
                    val buffer = ByteArray(64 * 1024)
                    RandomAccessFile(targetFile, "rw").use { raf ->
                        raf.seek(0)
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1 && isActive) {
                            raf.write(buffer, 0, read)
                            task.bytesDownloaded.addAndGet(read.toLong())
                        }
                    }
                }
            }

            if (isActive) {
                task.status = SegmentedTask.STATUS_SUCCESS
                val finalSize = targetFile.length()
                if (task.totalBytes <= 0L) {
                    task.totalBytes = finalSize
                }
                task.bytesDownloaded.set(finalSize)
                task.speedBytesPerSec = 0L
                saveTasks(context)

                MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)

                val activeActivity = BisnorApp.currentActivity
                if (activeActivity != null && activeActivity !is DownloadsActivity) {
                    activeActivity.runOnUiThread {
                        InAppNotificationHelper.showDownloadCompletedBanner(activeActivity, task.title)
                    }
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                task.status = SegmentedTask.STATUS_FAILED
                saveTasks(context)
            }
        }
    }

    fun updateSpeeds() {
        val now = System.currentTimeMillis()
        for (task in tasks.values) {
            if (task.isRunning) {
                val timeDeltaMs = now - task.lastSpeedCheckTime
                if (timeDeltaMs >= 800) {
                    val currentBytes = task.bytesDownloaded.get()
                    val byteDelta = currentBytes - task.lastBytes
                    task.speedBytesPerSec = if (timeDeltaMs > 0) (byteDelta * 1000) / timeDeltaMs else 0L
                    task.lastBytes = currentBytes
                    task.lastSpeedCheckTime = now
                }
            }
        }
    }

    fun cancelDownload(id: Long) {
        tasks[id]?.let { task ->
            task.job?.cancel()
            task.status = SegmentedTask.STATUS_CANCELLED
            try {
                val f = File(task.filePath)
                if (f.exists()) f.delete()
            } catch (e: Exception) {
                // Ignore
            }
            tasks.remove(id)
        }
    }

    fun getAllTasks(): List<SegmentedTask> {
        updateSpeeds()
        return tasks.values.toList()
    }

    fun getTask(id: Long): SegmentedTask? = tasks[id]

    private fun saveTasks(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val array = JSONArray()
            for (t in tasks.values) {
                val obj = JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("url", t.url)
                    put("filePath", t.filePath)
                    put("totalBytes", t.totalBytes)
                    put("bytesDownloaded", t.bytesDownloaded.get())
                    put("isSegmented", t.isSegmented)
                    put("partsCount", t.partsCount)
                    put("status", t.status)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_TASKS, array.toString()).apply()
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun loadPersistedTasks(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_TASKS, null) ?: return
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getLong("id")
                val status = obj.getInt("status")
                val filePath = obj.getString("filePath")
                val file = File(filePath)

                if (!file.exists() && status == SegmentedTask.STATUS_SUCCESS) continue

                val task = SegmentedTask(
                    id = id,
                    title = obj.getString("title"),
                    url = obj.getString("url"),
                    filePath = filePath,
                    totalBytes = obj.optLong("totalBytes", 0L),
                    isSegmented = obj.optBoolean("isSegmented", true),
                    partsCount = obj.optInt("partsCount", 8),
                    status = if (status == SegmentedTask.STATUS_RUNNING) SegmentedTask.STATUS_FAILED else status
                )
                task.bytesDownloaded.set(obj.optLong("bytesDownloaded", file.length()))
                tasks[id] = task
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}
