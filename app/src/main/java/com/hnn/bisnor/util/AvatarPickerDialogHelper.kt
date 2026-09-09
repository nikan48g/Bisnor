package com.hnn.bisnor.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.hnn.bisnor.R
import com.hnn.bisnor.data.remote.AuthManager
import com.hnn.bisnor.data.remote.FunnyAvatar

object AvatarPickerDialogHelper {

    fun show(
        context: Context,
        authManager: AuthManager,
        onAvatarSelected: (FunnyAvatar) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_select_avatar, null)

        val imgPreview = dialogView.findViewById<ImageView>(R.id.img_avatar_live_preview)
        val tvPreviewName = dialogView.findViewById<TextView>(R.id.tv_avatar_preview_character)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recycler_avatars_selection)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btn_confirm_avatar)

        val avatars = AuthManager.FUNNY_AVATARS
        var currentSelectedId = authManager.userAvatarId
        var selectedAvatar = avatars.find { it.id == currentSelectedId } ?: avatars.first()

        fun updateLivePreview(avatar: FunnyAvatar) {
            imgPreview.setImageResource(avatar.drawableRes)
            tvPreviewName.text = avatar.name
        }

        updateLivePreview(selectedAvatar)

        val adapter = object : RecyclerView.Adapter<AvatarViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvatarViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_avatar_choice, parent, false)
                return AvatarViewHolder(v)
            }

            override fun getItemCount(): Int = avatars.size

            override fun onBindViewHolder(holder: AvatarViewHolder, position: Int) {
                val avatar = avatars[position]
                holder.imgAvatar.setImageResource(avatar.drawableRes)
                holder.tvTitle.text = avatar.name.split("(").first().trim()

                val isSelected = avatar.id == selectedAvatar.id
                holder.viewHalo.visibility = if (isSelected) View.VISIBLE else View.GONE
                holder.itemView.scaleX = if (isSelected) 1.08f else 0.95f
                holder.itemView.scaleY = if (isSelected) 1.08f else 0.95f

                holder.itemView.setOnClickListener {
                    selectedAvatar = avatar
                    updateLivePreview(selectedAvatar)
                    notifyDataSetChanged()
                }
            }
        }

        recycler.layoutManager = GridLayoutManager(context, 4)
        recycler.adapter = adapter

        btnConfirm.setOnClickListener {
            authManager.userAvatarId = selectedAvatar.id
            authManager.updateAvatarUrl("") // Clear remote URL so local character avatar displays
            onAvatarSelected(selectedAvatar)
            dialog.dismiss()
        }

        dialog.setContentView(dialogView)
        dialog.show()
    }

    private class AvatarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgAvatar: ImageView = view.findViewById(R.id.img_avatar_item)
        val viewHalo: View = view.findViewById(R.id.view_selection_halo)
        val tvTitle: TextView = view.findViewById(R.id.tv_avatar_item_title)
    }
}
