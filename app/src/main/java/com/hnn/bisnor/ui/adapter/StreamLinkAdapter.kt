package com.hnn.bisnor.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hnn.bisnor.data.model.StreamLink
import com.hnn.bisnor.databinding.ItemStreamLinkBinding

class StreamLinkAdapter(
    private val links: List<StreamLink>,
    private val onPlayClick: (StreamLink) -> Unit
) : RecyclerView.Adapter<StreamLinkAdapter.LinkViewHolder>() {

    inner class LinkViewHolder(val binding: ItemStreamLinkBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(link: StreamLink) {
            binding.tvLinkQuality.text = link.quality
            val info = "${link.type} ${if (!link.size.isNullOrEmpty()) " • ${link.size}" else ""}"
            binding.tvLinkInfo.text = info

            binding.btnLinkPlay.setOnClickListener {
                onPlayClick(link)
            }
            binding.root.setOnClickListener {
                onPlayClick(link)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LinkViewHolder {
        val binding = ItemStreamLinkBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LinkViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LinkViewHolder, position: Int) {
        holder.bind(links[position])
    }

    override fun getItemCount(): Int = links.size
}
