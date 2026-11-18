package com.hnn.bisnor.ui.downloads

import com.hnn.bisnor.R
import com.hnn.bisnor.util.Tr

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
import com.hnn.bisnor.data.repository.SegmentedDownloadManager
import com.hnn.bisnor.data.repository.SegmentedTask
import com.hnn.bisnor.databinding.ActivityDownloadsBinding
import com.hnn.bisnor.databinding.ItemDownloadFileBinding
import com.hnn.bisnor.ui.player.PlayerActivity
import com.hnn.bisnor.util.ThemeHelper

class DownloadsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadsBinding
    private var downloadsList = listOf<DownloadedFile>()
    private lateinit var adapter: DownloadAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
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
        binding.tvStorageDesc.text = Tr.s(R.string.s_d54c990, downloadsList.size)
        binding.tvTotalDownloadSize.text = if (totalMb >= 1024.0) {
            Tr.s(R.string.fmt_gb, String.format("%.2f", totalMb / 1024.0))
        } else {
            Tr.s(R.string.s_7c690f1, String.format("%.1f", totalMb))
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

            when {
                item.isDownloaded -> {
                    holder.b.layoutDownloadProgress.visibility = View.GONE
                    holder.b.layoutPlayOfflineContainer.visibility = View.VISIBLE
                    holder.b.progressBarDownload.visibility = View.GONE
                    val mb = String.format("%.1f", item.totalBytes / (1024.0 * 1024.0))
                    holder.b.tvDownloadInfo.text = Tr.s(R.string.s_4bd4120, mb)
                    holder.b.root.setOnClickListener(null)
                }
                item.status == SegmentedTask.STATUS_FAILED -> {
                    holder.b.layoutDownloadProgress.visibility = View.VISIBLE
                    holder.b.layoutPlayOfflineContainer.visibility = View.GONE
                    holder.b.progressBarDownload.visibility = View.GONE
                    holder.b.tvDownloadProgressText.text = Tr.s(R.string.s_b364778)
                    holder.b.tvDownloadInfo.text = Tr.s(R.string.s_e91db49)
                    holder.b.root.setOnClickListener {
                        SegmentedDownloadManager.retryDownload(this@DownloadsActivity, item.id)
                        Toast.makeText(this@DownloadsActivity, Tr.s(R.string.s_08dafcb), Toast.LENGTH_SHORT).show()
                        loadDownloads(silent = true)
                    }
                }
                else -> {
                    holder.b.layoutDownloadProgress.visibility = View.VISIBLE
                    holder.b.layoutPlayOfflineContainer.visibility = View.GONE
                    holder.b.progressBarDownload.visibility = View.VISIBLE
                    holder.b.root.setOnClickListener(null)

                    val pct = item.progressPercent
                    holder.b.progressBarDownload.isIndeterminate = (item.totalBytes <= 0L)
                    if (item.totalBytes > 0L) {
                        holder.b.progressBarDownload.progress = pct
                    }

                    val downloadedMb = String.format("%.1f", item.bytesDownloaded / (1024.0 * 1024.0))
                    val totalMb = item.totalBytes / (1024.0 * 1024.0)
                    val speedMb = item.speedBytesPerSec / (1024.0 * 1024.0)
                    val speedStr = if (speedMb >= 0.05) Tr.s(R.string.fmt_mbps, String.format("%.1f", speedMb)) else Tr.s(R.string.s_5e9b8ad)
                    val partLabel = if (item.isSegmented) Tr.s(R.string.s_6d450a8) else Tr.s(R.string.s_7721427)

                    if (item.totalBytes > 0L) {
                        val totalMbStr = Tr.s(R.string.s_7c690f1, String.format("%.1f", totalMb))
                        holder.b.tvDownloadProgressText.text = Tr.s(R.string.s_b686e3e, pct, downloadedMb, totalMbStr, partLabel, speedStr)
                        holder.b.tvDownloadInfo.text = Tr.s(R.string.s_4706970, pct, speedStr)
                    } else {
                        holder.b.tvDownloadProgressText.text = Tr.s(R.string.s_9f81e3d, downloadedMb, partLabel, speedStr)
                        holder.b.tvDownloadInfo.text = Tr.s(R.string.s_8a0a05b)
                    }
                }
            }

            holder.b.btnDeleteDownload.setOnClickListener {
                MaterialAlertDialogBuilder(this@DownloadsActivity)
                    .setTitle(Tr.s(R.string.s_b5fb436))
                    .setMessage(Tr.s(R.string.s_9de66fe, item.title))
                    .setPositiveButton(Tr.s(R.string.s_3b9854e)) { _, _ ->
                        LocalDownloadManager.deleteDownload(this@DownloadsActivity, item)
                        loadDownloads()
                        Toast.makeText(this@DownloadsActivity, Tr.s(R.string.s_421e4fe), Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(Tr.s(R.string.s_c8d2a1f), null)
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
