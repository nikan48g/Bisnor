package com.hnn.bisnor

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hnn.bisnor.databinding.ActivityMainBinding
import com.hnn.bisnor.ui.explore.ExploreFragment
import com.hnn.bisnor.ui.favorites.FavoritesFragment
import com.hnn.bisnor.ui.home.HomeFragment
import com.hnn.bisnor.ui.settings.SettingsFragment
import com.hnn.bisnor.util.ThemeHelper
import com.hnn.bisnor.util.UpdateChecker
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val homeFragment = HomeFragment()
    private val exploreFragment = ExploreFragment()
    private val favoritesFragment = FavoritesFragment()
    private val settingsFragment = SettingsFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, homeFragment)
                .commit()
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> switchFragment(homeFragment)
                R.id.nav_explore -> switchFragment(exploreFragment)
                R.id.nav_favorites -> switchFragment(favoritesFragment)
                R.id.nav_settings -> switchFragment(settingsFragment)
                else -> false
            }
        }

        // Back button always goes back to Home screen if on another tab
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.bottomNavigation.selectedItemId != R.id.nav_home) {
                    binding.bottomNavigation.selectedItemId = R.id.nav_home
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Check for updates in background on startup
        lifecycleScope.launch {
            UpdateChecker.checkForUpdates(this@MainActivity, showToastIfLatest = false)
        }
    }

    fun selectExploreTab() {
        binding.bottomNavigation.selectedItemId = R.id.nav_explore
    }

    fun selectFavoritesTab() {
        binding.bottomNavigation.selectedItemId = R.id.nav_favorites
    }

    private fun switchFragment(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        return true
    }
}
