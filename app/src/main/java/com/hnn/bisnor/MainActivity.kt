package com.hnn.bisnor

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
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
        enableEdgeToEdge()
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle WindowInsets for Edge-to-Edge display
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply top inset (status bar) as padding to fragmentContainer so top bars are never covered
            binding.fragmentContainer.updatePadding(top = systemBars.top)

            // Lift floating bottom navigation above the navigation bar / gesture pill
            val baseMarginBottom = (16 * resources.displayMetrics.density).toInt()
            binding.bottomNavigation.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = baseMarginBottom + systemBars.bottom
            }

            insets
        }

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

        handleIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        try {
            val host = uri.host ?: ""
            val path = uri.path ?: ""

            // Scheme: bisnor://app/playlist?data=... OR https://bisnor.app/playlist?data=...
            if (path.contains("playlist") || host.contains("playlist")) {
                val compressedData = uri.getQueryParameter("data")
                if (!compressedData.isNullOrEmpty()) {
                    val decoded = java.net.URLDecoder.decode(compressedData, "UTF-8")
                    val json = com.hnn.bisnor.data.remote.SupabaseManager.decompressString(decoded)
                    val payload = com.hnn.bisnor.data.model.SharedPlaylistPayload.fromJson(json)
                    if (payload != null && payload.items.isNotEmpty()) {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("دریافت لیست «${payload.name}»")
                            .setMessage("آیا می‌خواهید این لیست شامل ${payload.items.size} فیلم به لیست‌های شخصی شما اضافه شود؟")
                            .setPositiveButton("ذخیره در لیست‌ها") { _, _ ->
                                val playlistsManager = com.hnn.bisnor.data.repository.PlaylistsManager(this)
                                val newId = playlistsManager.createPlaylist("${payload.name} (اشتراکی)")
                                for (item in payload.items) {
                                    playlistsManager.addToPlaylist(newId, item)
                                }
                                android.widget.Toast.makeText(this, "لیست با موفقیت ذخیره شد!", android.widget.Toast.LENGTH_SHORT).show()
                                selectFavoritesTab()
                            }
                            .setNegativeButton("انصراف", null)
                            .show()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
