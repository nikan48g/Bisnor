package com.hnn.bisnor.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hnn.bisnor.R
import com.hnn.bisnor.databinding.ActivitySettingsBinding
import com.hnn.bisnor.util.ThemeHelper

/**
 * Settings are no longer a primary destination: Profile owns the entry point and
 * this activity hosts the existing, fully featured SettingsFragment.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarSettings.setNavigationOnClickListener { finish() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        ThemeHelper.applyTheme(this)
    }
}
