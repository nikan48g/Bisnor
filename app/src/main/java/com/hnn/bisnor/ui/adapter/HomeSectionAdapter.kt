package com.hnn.bisnor.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hnn.bisnor.data.model.HomeSection
import com.hnn.bisnor.data.model.MediaItem
import com.hnn.bisnor.databinding.ItemHomeSectionBinding

class HomeSectionAdapter(
    private val sections: List<HomeSection>,
    private val onItemClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<HomeSectionAdapter.SectionViewHolder>() {

    inner class SectionViewHolder(val binding: ItemHomeSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(section: HomeSection) {
            binding.tvSectionTitle.text = section.title
            val mediaAdapter = MediaCardAdapter(section.items, onItemClick)
            binding.recyclerHorizontal.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = mediaAdapter
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
        val binding = ItemHomeSectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SectionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
        holder.bind(sections[position])
    }

    override fun getItemCount(): Int = sections.size
}
