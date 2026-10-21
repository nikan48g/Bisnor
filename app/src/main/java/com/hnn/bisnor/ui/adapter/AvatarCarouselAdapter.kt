package com.hnn.bisnor.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.hnn.bisnor.data.remote.AuthManager
import com.hnn.bisnor.data.remote.FunnyAvatar

/** Vertical avatar carousel used by the sign-up dialog. */
class AvatarCarouselAdapter(
    private val items: List<FunnyAvatar>,
    private val onAvatarPicked: (FunnyAvatar) -> Unit
) : RecyclerView.Adapter<AvatarCarouselAdapter.Holder>() {

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(com.hnn.bisnor.R.id.img_carousel_avatar)
        val card: View = view.findViewById(com.hnn.bisnor.R.id.card_carousel_avatar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(com.hnn.bisnor.R.layout.item_avatar_carousel, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val avatar = items[position]
        holder.image.setImageResource(avatar.drawableRes)
        holder.itemView.setOnClickListener {
            onAvatarPicked(avatar)
        }
    }

    fun avatarAt(position: Int): FunnyAvatar? = items.getOrNull(position)
}
