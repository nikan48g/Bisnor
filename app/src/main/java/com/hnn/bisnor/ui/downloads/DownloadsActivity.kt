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

    override fun onResume() {
        super.onResume()
        loadDownloads()
    }

    private fun loadDownloads() {
        downloadsList = LocalDownloadManager.getDownloadedVideos(this)
        adapter.notifyDataSetChanged()
        binding.tvEmptyDownloads.visibility = if (downloadsList.isEmpty()) View.VISIBLE else View.GONE
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

            val mb = String.format("%.1f", item.totalBytes / (1024.0 * 1024.0))
            val statusStr = if (item.isDownloaded) "تکمیل شده • $mb MB" else "در حال دانلود..."
            holder.b.tvDownloadInfo.text = statusStr

            holder.b.btnDeleteDownload.setOnClickListener {
                MaterialAlertDialogBuilder(this@DownloadsActivity)
                    .setTitle("حذف فایل دانلود شده")
                    .setMessage("آیا می‌خواهید «${item.title}» را از حافظه دستگاه حذف کنید؟")
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
