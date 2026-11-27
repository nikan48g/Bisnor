package com.hnn.bisnor.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hnn.bisnor.R
import com.hnn.bisnor.data.repository.PersonIndex
import com.hnn.bisnor.databinding.ItemPersonSearchBinding

/**
 * The people strip above search results: a circular portrait with the name underneath.
 * Written right-to-left by the app locale, so the first person sits at the start edge.
 */
class PersonSearchAdapter(
    private var items: List<PersonIndex.Entry> = emptyList(),
    private val onItemClick: (PersonIndex.Entry) -> Unit
) : RecyclerView.Adapter<PersonSearchAdapter.Holder>() {

    fun updateData(newItems: List<PersonIndex.Entry>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemPersonSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(val b: ItemPersonSearchBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(person: PersonIndex.Entry) {
            b.tvPeopleName.text = if (person.name.isNotBlank()) person.name else person.nameFa
            if (person.photo.isNotBlank()) {
                b.imgPeopleThumb.setPadding(0, 0, 0, 0)
                b.imgPeopleThumb.imageTintList = null
                b.imgPeopleThumb.load(person.photo) {
                    crossfade(true)
                    placeholder(R.drawable.bg_skeleton)
                    error(R.drawable.ic_person)
                }
            } else {
                val context = b.root.context
                val pad = (16 * context.resources.displayMetrics.density).toInt()
                b.imgPeopleThumb.setPadding(pad, pad, pad, pad)
                b.imgPeopleThumb.imageTintList = ColorStateList.valueOf(
                    context.getColor(R.color.on_surface_variant)
                )
                b.imgPeopleThumb.setImageResource(R.drawable.ic_person)
            }
            b.root.contentDescription = listOf(
                if (person.name.isNotBlank()) person.name else person.nameFa,
                PersonRowAdapter.roleLabel(person.role)
            ).filter { it.isNotBlank() }.joinToString(", ")
            b.root.setOnClickListener { onItemClick(person) }
        }
    }
}
