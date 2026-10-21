package com.hnn.bisnor.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hnn.bisnor.R
import com.hnn.bisnor.data.model.RealPerson
import com.hnn.bisnor.databinding.ItemDetailPersonBinding

class PersonRowAdapter(
    private var items: List<RealPerson>,
    private val onItemClick: (RealPerson) -> Unit
) : RecyclerView.Adapter<PersonRowAdapter.PersonViewHolder>() {

    fun updateData(newItems: List<RealPerson>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonViewHolder {
        val binding = ItemDetailPersonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PersonViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: PersonViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class PersonViewHolder(val b: ItemDetailPersonBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(person: RealPerson) {
            b.tvPersonName.text = person.name
            b.tvPersonRole.text = roleLabel(person.role)

            val context = b.root.context
            val photo = person.photo
            if (photo.isNotBlank()) {
                b.imgPersonThumb.setPadding(0, 0, 0, 0)
                b.imgPersonThumb.imageTintList = null
                b.imgPersonThumb.load(photo) {
                    crossfade(true)
                    placeholder(R.drawable.bg_skeleton)
                    error(R.drawable.ic_person)
                }
            } else {
                b.imgPersonThumb.setPadding(14.dp(context), 14.dp(context), 14.dp(context), 14.dp(context))
                b.imgPersonThumb.imageTintList = ColorStateList.valueOf(
                    context.getColor(R.color.on_surface_variant)
                )
                b.imgPersonThumb.setImageResource(R.drawable.ic_person)
            }

            b.root.setOnClickListener { onItemClick(person) }
        }
    }

    private fun Int.dp(context: android.content.Context): Int =
        (this * context.resources.displayMetrics.density).toInt()

    companion object {
        fun roleLabel(role: String): String = when (role) {
            "director" -> "کارگردان"
            "writer" -> "نویسنده"
            else -> "بازیگر"
        }
    }
}
