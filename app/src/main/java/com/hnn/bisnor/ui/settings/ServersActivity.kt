package com.hnn.bisnor.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hnn.bisnor.R
import com.hnn.bisnor.data.repository.SourcePreferences
import com.hnn.bisnor.databinding.ActivityServersBinding
import com.hnn.bisnor.util.ThemeHelper

/** Automatic server, or pick Server 1 / Server 2 / both by hand. */
class ServersActivity : AppCompatActivity() {
    private lateinit var binding: ActivityServersBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)
        binding = ActivityServersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarServers.setNavigationOnClickListener { finish() }
        render()
    }

    private fun render() {
        val sp = SourcePreferences
        val dp = resources.displayMetrics.density
        binding.switchServerAuto.setOnCheckedChangeListener(null)
        binding.switchServerAuto.isChecked = sp.auto
        binding.tvServerHint.visibility = if (sp.auto) View.VISIBLE else View.GONE
        val box = binding.serverOptions
        box.removeAllViews()
        for ((mode, label) in listOf(sp.NEXT to R.string.srv_1, sp.IRANFLIX to R.string.srv_2, sp.BOTH to R.string.srv_both)) {
            box.addView(TextView(this).apply {
                setText(label)
                setTextAppearance(R.style.TextAppearance_Bisnor_Body)
                val v = (14 * dp).toInt(); setPadding(v, v, v, v)
                setBackgroundResource(R.drawable.bg_onb_option)
                isSelected = sp.manualMode == mode && !sp.auto
                alpha = if (sp.auto) 0.45f else 1f
                isEnabled = !sp.auto; isFocusable = !sp.auto; isClickable = !sp.auto
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8 * dp).toInt() }
                setOnClickListener { sp.setMode(mode); render() }
            })
        }
        binding.switchServerAuto.setOnCheckedChangeListener { _, on -> sp.setAuto(on); render() }
    }
}
