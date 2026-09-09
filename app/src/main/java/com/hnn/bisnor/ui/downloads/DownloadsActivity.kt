package com.hnn.bisnor.ui.downloads

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hnn.bisnor.data.repository.DownloadedFile
import com.hnn.bisnor.data.repository.LocalDownloadManager
import com.hnn.bisnor.databinding.ActivityDownloadsBinding
import com.hnn.bisnor.databinding.ItemDownloadFileBinding
import com.hnn.bisnor.ui.player.PlayerActivity

class DownloadsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadsBinding
    private var downloadsList = listOf<DownloadedFile>()
    private lateinit var adapter: DownloadAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarDownloads.setNavigationOnClickListener { finish() }

        adapter = DownloadAdapter()
        binding.recyclerDownloads.layoutManager = LinearLayoutManager(this)
        binding.recyclerDownloads.adapter = adapter

        loadDownloads()
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadDownloads(silent = true)
            // If any download is still running, poll every 1 second
            if (downloadsList.any { it.isRunning }) {
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadDownloads()
        handler.removeCallbacks(refreshRunnable)
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun loadDownloads(silent: Boolean = false) {
        downloadsList = LocalDownloadManager.getDownloadedVideos(this)
        adapter.notifyDataSetChanged()
        binding.tvEmptyDownloads.visibility = if (downloadsList.isEmpty()) View.VISIBLE else View.GONE
        binding.cardStorageSummary.visibility = if (downloadsList.isEmpty()) View.GONE else View.VISIBLE

        val totalBytes = downloadsList.sumOf { it.totalBytes }
        val totalMb = totalBytes / (1024.0 * 1024.0)
        binding.tvStorageDesc.text = "${downloadsList.size} فایل در لیست دانلودها"
        binding.tvTotalDownloadSize.text = if (totalMb >= 1024.0) {
            String.format("%.2f گیگابایت", totalMb / 1024.0)
        } else {
            String.format("%.1f مگابایت", totalMb)
        }
    }

    inner class DownloadAdapter : RecyclerView.Adapter<DownloadAdapter.Holder>() {

        inner class Holder(val b: ItemDownloadFileBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val b = ItemDownloadFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(b)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = downloadsList[position]
            holder.b.tvDownloadTitle.text = item.title

            if (item.isDownloaded) {
                holder.b.layoutDownloadProgress.visibility = View.GONE
                holder.b.layoutPlayOfflineContainer.visibility = View.VISIBLE
                val mb = String.format("%.1f", item.totalBytes / (1024.0 * 1024.0))
                holder.b.tvDownloadInfo.text = "تکمیل شده • $mb مگابایت"
            } else {
                holder.b.layoutDownloadProgress.visibility = View.VISIBLE
                holder.b.layoutPlayOfflineContainer.visibility = View.GONE

                val pct = item.progressPercent
                holder.b.progressBarDownload.isIndeterminate = (item.totalBytes <= 0L)
                if (item.totalBytes > 0L) {
                    holder.b.progressBarDownload.progress = pct
                }

                val downloadedMb = String.format("%.1f", item.bytesDownloaded / (1024.0 * 1024.0))
                val totalMb = item.totalBytes / (1024.0 * 1024.0)
                val speedMb = item.speedBytesPerSec / (1024.0 * 1024.0)
                val speedStr = if (speedMb >= 0.05) String.format("%.1f مگابایت/ثانیه", speedMb) else "در حال دریافت"
                val partLabel = if (item.isSegmented) "۸ تکه‌ای" else "تکه‌ای"

                if (item.totalBytes > 0L) {
                    val totalMbStr = String.format("%.1f مگابایت", totalMb)
                    holder.b.tvDownloadProgressText.text = "$pct٪ • $downloadedMb از $totalMbStr ($partLabel • $speedStr)"
                    holder.b.tvDownloadInfo.text = "در حال دانلود ($pct٪) • $speedStr"
                } else {
                    holder.b.tvDownloadProgressText.text = "$downloadedMb مگابایت دریافت شده ($partLabel • $speedStr)"
                    holder.b.tvDownloadInfo.text = "در حال دانلود • $speedStr"
                }
            }

            holder.b.btnDeleteDownload.setOnClickListener {
                MaterialAlertDialogBuilder(this@DownloadsActivity)
                    .setTitle("حذف فایل دانلود")
                    .setMessage("آیا می‌خواهید «${item.title}» را حذف کنید؟")
                    .setPositiveButton("حذف") { _, _ ->
                        LocalDownloadManager.deleteDownload(this@DownloadsActivity, item)
                        loadDownloads()
                        Toast.makeText(this@DownloadsActivity, "فایل حذف شد", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("انصراف", null)
                    .show()
            }

            holder.b.btnPlayOffline.setOnClickListener {
                val intent = Intent(this@DownloadsActivity, PlayerActivity::class.java).apply {
                    putExtra("video_title", item.title)
                    putExtra("video_url", item.filePath.ifEmpty { item.fileUri })
                }
                startActivity(intent)
            }
        }

        override fun getItemCount(): Int = downloadsList.size
    }
}
