package com.hnn.bisnor.util

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.hnn.bisnor.R
import com.hnn.bisnor.ui.downloads.DownloadsActivity

object InAppNotificationHelper {

    fun showDownloadCompletedBanner(activity: Activity, downloadTitle: String) {
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread

            val rootView = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@runOnUiThread

            // Remove any existing banner
            val existing = rootView.findViewWithTag<View>("in_app_banner")
            if (existing != null) {
                rootView.removeView(existing)
            }

            val inflater = LayoutInflater.from(activity)
            val bannerView = inflater.inflate(R.layout.layout_in_app_notification, rootView, false)
            bannerView.tag = "in_app_banner"

            val density = activity.resources.displayMetrics.density
            val margin16 = (16 * density).toInt()

            val insets = ViewCompat.getRootWindowInsets(rootView)
            val statusBarTop = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: (24 * density).toInt()

            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
                leftMargin = margin16
                rightMargin = margin16
                topMargin = statusBarTop + (10 * density).toInt()
            }
            bannerView.layoutParams = params

            val tvDesc = bannerView.findViewById<TextView>(R.id.tv_banner_desc)
            val btnClose = bannerView.findViewById<ImageView>(R.id.btn_close_banner)

            tvDesc.text = "«$downloadTitle» آماده پخش آفلاین است (لمس کنید)"

            fun dismissBanner() {
                bannerView.animate()
                    .translationY(-350f)
                    .alpha(0f)
                    .setDuration(250)
                    .withEndAction {
                        rootView.removeView(bannerView)
                    }.start()
            }

            bannerView.setOnClickListener {
                dismissBanner()
                val intent = Intent(activity, DownloadsActivity::class.java)
                activity.startActivity(intent)
            }

            btnClose.setOnClickListener {
                dismissBanner()
            }

            // Slide down animation
            bannerView.translationY = -350f
            bannerView.alpha = 0f
            rootView.addView(bannerView)

            bannerView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(380)
                .setInterpolator(OvershootInterpolator(1.15f))
                .start()

            // Auto dismiss after 5 seconds
            bannerView.postDelayed({
                if (bannerView.parent != null) {
                    dismissBanner()
                }
            }, 5000)
        }
    }
}
