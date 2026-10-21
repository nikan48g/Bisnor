package com.hnn.bisnor.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.hnn.bisnor.R
import com.hnn.bisnor.data.remote.AuthManager
import com.hnn.bisnor.ui.adapter.AvatarCarouselAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Shared sign-in / sign-up dialog. Reused by Profile and Settings so account
 * handling lives in exactly one place.
 */
object AuthDialogHelper {

    fun show(
        context: Context,
        authManager: AuthManager,
        scope: CoroutineScope,
        onSuccess: () -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_auth, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_auth_dialog_title)
        val layoutAvatarPicker = dialogView.findViewById<LinearLayout>(R.id.layout_avatar_picker)
        val recyclerAvatars = dialogView.findViewById<RecyclerView>(R.id.recycler_signup_avatars)
        val tvAvatarName = dialogView.findViewById<TextView>(R.id.tv_signup_avatar_name)
        val etUser = dialogView.findViewById<TextInputEditText>(R.id.et_auth_username)
        val etPass = dialogView.findViewById<TextInputEditText>(R.id.et_auth_password)
        val btnSubmit = dialogView.findViewById<MaterialButton>(R.id.btn_auth_submit)
        val btnSwitch = dialogView.findViewById<MaterialButton>(R.id.btn_auth_switch_mode)

        var isRegisterMode = false
        var selectedAvatarId = authManager.userAvatarId
        val avatars = AuthManager.FUNNY_AVATARS
        var selectedIndex = avatars.indexOfFirst { it.id == selectedAvatarId }.coerceAtLeast(0)

        val density = context.resources.displayMetrics.density
        val itemHeightPx = (72 * density).toInt()

        val carouselAdapter = AvatarCarouselAdapter(avatars) { avatar ->
            val index = avatars.indexOfFirst { it.id == avatar.id }
            if (index >= 0) {
                recyclerAvatars.smoothScrollToPosition(index)
                selectedIndex = index
                selectedAvatarId = avatar.id
                authManager.userAvatarId = avatar.id
                tvAvatarName.text = avatar.name
            }
        }
        recyclerAvatars.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        recyclerAvatars.adapter = carouselAdapter
        recyclerAvatars.itemAnimator = null
        LinearSnapHelper().attachToRecyclerView(recyclerAvatars)

        fun styleCarousel() {
            val viewport = recyclerAvatars.height
            if (viewport <= 0) return
            val side = ((viewport - itemHeightPx) / 2).coerceAtLeast(0)
            if (recyclerAvatars.paddingTop != side) {
                recyclerAvatars.setPadding(0, side, 0, side)
            }
            val centerY = viewport / 2f
            var closest = selectedIndex
            var minDistance = Float.MAX_VALUE
            for (i in 0 until recyclerAvatars.childCount) {
                val child = recyclerAvatars.getChildAt(i) ?: continue
                val distance = abs((child.top + child.height / 2f) - centerY)
                val fraction = (1f - distance / (viewport / 2f)).coerceIn(0f, 1f)
                child.scaleX = 0.62f + 0.52f * fraction
                child.scaleY = 0.62f + 0.52f * fraction
                child.alpha = 0.24f + 0.76f * fraction
                child.translationZ = fraction * 10f * density
                if (distance < minDistance) {
                    minDistance = distance
                    closest = recyclerAvatars.getChildAdapterPosition(child)
                }
            }
            if (closest >= 0 && closest != selectedIndex) {
                selectedIndex = closest
                val avatar = avatars.getOrNull(closest)
                if (avatar != null) {
                    selectedAvatarId = avatar.id
                    authManager.userAvatarId = avatar.id
                    tvAvatarName.animate().alpha(0f).setDuration(90).withEndAction {
                        tvAvatarName.text = avatar.name
                        tvAvatarName.animate().alpha(1f).setDuration(140).start()
                    }.start()
                    recyclerAvatars.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                }
            }
        }

        recyclerAvatars.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                styleCarousel()
            }
        })
        recyclerAvatars.post {
            styleCarousel()
            recyclerAvatars.scrollToPosition(selectedIndex)
            tvAvatarName.text = avatars.getOrNull(selectedIndex)?.name.orEmpty()
            recyclerAvatars.post { styleCarousel() }
        }

        val dialog = MaterialAlertDialogBuilder(context).setView(dialogView).create()

        fun updateUi() {
            if (isRegisterMode) {
                tvTitle.text = "ثبت‌نام در بیسنور"
                layoutAvatarPicker.visibility = View.VISIBLE
                tvAvatarName.text = avatars.getOrNull(selectedIndex)?.name.orEmpty()
                btnSubmit.text = "ایجاد حساب"
                btnSwitch.text = "حساب دارید؟ وارد شوید"
            } else {
                tvTitle.text = "ورود به حساب"
                layoutAvatarPicker.visibility = View.GONE
                btnSubmit.text = "ورود"
                btnSwitch.text = "حساب ندارید؟ ثبت‌نام"
            }
        }

        btnSwitch.setOnClickListener {
            isRegisterMode = !isRegisterMode
            updateUi()
        }

        btnSubmit.setOnClickListener {
            val username = etUser.text?.toString()?.trim() ?: ""
            val password = etPass.text?.toString()?.trim() ?: ""
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(context, "نام کاربری و رمز عبور را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            scope.launch {
                btnSubmit.isEnabled = false
                btnSubmit.text = "در حال ارتباط با سرور..."
                val (success, message) = if (isRegisterMode) {
                    authManager.register(username, password)
                } else {
                    authManager.login(username, password)
                }
                btnSubmit.isEnabled = true
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                if (success) {
                    dialog.dismiss()
                    onSuccess()
                } else {
                    updateUi()
                }
            }
        }

        updateUi()
        dialog.show()
    }
}
