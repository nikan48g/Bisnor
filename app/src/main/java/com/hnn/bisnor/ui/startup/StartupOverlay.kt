package com.hnn.bisnor.ui.startup

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.hnn.bisnor.R
import com.hnn.bisnor.data.repository.ServerHealth
import com.hnn.bisnor.data.repository.SourcePreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Launch check, full screen over everything (the nav bar hides): internet, then the servers.
 * If nothing is reachable a red screen explains how to fix it.
 */
object StartupOverlay {
    private var doneThisProcess = false

    fun show(activity: AppCompatActivity, parent: ViewGroup, hide: List<View>) {
        if (doneThisProcess) return
        val dp = activity.resources.displayMetrics.density
        val root = FrameLayout(activity).apply { isClickable = true; isFocusable = true; translationZ = 200 * dp }
        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            val p = (28 * dp).toInt(); setPadding(p, p, p, p)
        }
        root.addView(col, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        parent.addView(root, ViewGroup.LayoutParams(-1, -1))
        hide.forEach { it.visibility = View.INVISIBLE }
        fun close() {
            doneThisProcess = true
            hide.forEach { it.visibility = View.VISIBLE }
            root.animate().alpha(0f).setDuration(220).withEndAction { parent.removeView(root) }.start()
        }

        fun backdrop(center: Int, edge: Int) {
            root.background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = 520 * dp; setGradientCenter(0.5f, 0.32f)
                colors = intArrayOf(center, edge)
            }
        }
        fun tv(style: Int, text: CharSequence, top: Int, color: Int = Color.WHITE, alpha: Float = 1f) = TextView(activity).apply {
            setTextAppearance(style); this.text = text; gravity = Gravity.CENTER
            setTextColor(color); this.alpha = alpha
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (top * dp).toInt() }
        }
        fun logo(size: Int) = ImageView(activity).apply {
            setImageResource(R.mipmap.ic_launcher_round)
            layoutParams = LinearLayout.LayoutParams((size * dp).toInt(), (size * dp).toInt())
        }

        fun checking() {
            backdrop(Color.parseColor("#12306B"), Color.parseColor("#050A18"))
            col.removeAllViews()
            col.addView(logo(112))
            col.addView(tv(R.style.TextAppearance_Bisnor_Display, activity.getString(R.string.app_name), 18))
            col.addView(tv(R.style.TextAppearance_Bisnor_Body, activity.getString(R.string.st_tagline), 6, alpha = 0.75f))
            val labels = listOf(R.string.st_check_net, R.string.st_check_srv)
            val rows = labels.map {
                tv(R.style.TextAppearance_Bisnor_Body, "○  " + activity.getString(it), 18, alpha = 0.6f).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
            }
            col.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams((260 * dp).toInt(), -2).apply { topMargin = (26 * dp).toInt() }
                rows.forEach { addView(it) }
            })
            val bar = LinearProgressIndicator(activity).apply {
                isIndeterminate = true; trackCornerRadius = (3 * dp).toInt()
                setIndicatorColor(Color.parseColor("#3DA5FF")); trackColor = Color.parseColor("#22FFFFFF")
                layoutParams = LinearLayout.LayoutParams((200 * dp).toInt(), -2).apply { topMargin = (26 * dp).toInt() }
            }
            col.addView(bar)
            activity.lifecycleScope.launch {
                val started = System.currentTimeMillis()
                val r = withTimeoutOrNull(10_000) { ServerHealth.check(activity) }
                fun mark(i: Int, ok: Boolean) {
                    rows[i].text = (if (ok) "✓  " else "✕  ") + activity.getString(labels[i])
                    rows[i].alpha = 1f
                    rows[i].setTextColor(if (ok) Color.WHITE else Color.parseColor("#FF8A80"))
                }
                if (r == null) {
                    // The probe timed out: that means a slow server, not an offline device.
                    // The catalogue itself reports a real failure; never block home with a
                    // false "no connection" screen.
                    mark(0, true); mark(1, true)
                    delay(maxOf(0L, 600 - (System.currentTimeMillis() - started)))
                    close()
                    return@launch
                }
                mark(0, r.network); mark(1, r.anyServer)
                delay(maxOf(0L, 800 - (System.currentTimeMillis() - started)))
                if (r.network && r.anyServer) { delay(250); close() } else offline(!r.network)
            }
        }

        fun offlineScreen(noNet: Boolean) {
            backdrop(Color.parseColor("#5A1414"), Color.parseColor("#12080A"))
            col.removeAllViews()
            col.addView(ImageView(activity).apply {
                setImageResource(R.drawable.ic_wifi_off)
                setColorFilter(Color.parseColor("#FF8A80"))
                layoutParams = LinearLayout.LayoutParams((84 * dp).toInt(), (84 * dp).toInt())
            })
            col.addView(tv(R.style.TextAppearance_Bisnor_Headline, activity.getString(if (noNet) R.string.off_title_net else R.string.off_title_srv), 22))
            col.addView(tv(R.style.TextAppearance_Bisnor_Body, activity.getString(if (noNet) R.string.off_sub_net else R.string.off_sub_srv), 8, alpha = 0.8f))
            val tips = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply { setColor(Color.parseColor("#B0000000")); cornerRadius = 18 * dp }
                val p = (16 * dp).toInt(); setPadding(p, p, p, p)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (22 * dp).toInt() }
            }
            for (t in listOf(R.string.off_tip_1, R.string.off_tip_2, R.string.off_tip_3)) {
                tips.addView(tv(R.style.TextAppearance_Bisnor_Body, "•  " + activity.getString(t), 8).apply { gravity = Gravity.START })
            }
            col.addView(tips)
            fun btn(t: Int, filled: Boolean, onClick: () -> Unit) = MaterialButton(activity).apply {
                setText(t); isAllCaps = false; cornerRadius = (100 * dp).toInt()
                if (filled) { setBackgroundColor(Color.parseColor("#3DA5FF")); setTextColor(Color.parseColor("#00203F")) }
                else { setBackgroundColor(Color.parseColor("#33000000")); setTextColor(Color.WHITE) }
                setOnClickListener { onClick() }
                layoutParams = LinearLayout.LayoutParams(-1, (54 * dp).toInt()).apply { topMargin = (14 * dp).toInt() }
            }
            col.addView(btn(R.string.off_retry, true) { checking() })
            col.addView(btn(R.string.off_continue, false) { close() })
            col.addView(tv(R.style.TextAppearance_Bisnor_Caption, activity.getString(R.string.app_name), 18, alpha = 0.5f))
        }
        offlineHandler = ::offlineScreen
        checking()
    }

    private var offlineHandler: (Boolean) -> Unit = {}
    private fun offline(noNet: Boolean) = offlineHandler.invoke(noNet)
}
