package com.hnn.bisnor

import android.content.Intent
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
import com.hnn.bisnor.ui.home.HomeFragment
import com.hnn.bisnor.ui.library.LibraryFragment
import com.hnn.bisnor.ui.profile.ProfileFragment
import com.hnn.bisnor.util.ThemeHelper
import com.hnn.bisnor.util.UpdateChecker
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var homeFragment: HomeFragment? = null
    private var exploreFragment: ExploreFragment? = null
    private var libraryFragment: LibraryFragment? = null
    private var profileFragment: ProfileFragment? = null

    private var activeTag: String = TAG_HOME

    companion object {
        private const val TAG_HOME = "tab_home"
        private const val TAG_EXPLORE = "tab_explore"
        private const val TAG_LIBRARY = "tab_library"
        private const val TAG_PROFILE = "tab_profile"
        private const val KEY_ACTIVE_TAG = "active_tab_tag"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.fragmentContainer.updatePadding(top = bars.top)
            val baseMargin = (14 * resources.displayMetrics.density).toInt()
            binding.bottomNavigation.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = baseMargin + bars.bottom
            }
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { view, insets ->
            view.setPadding(0, 0, 0, 0)
            insets
        }

        restoreFragments(savedInstanceState)

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showTab(TAG_HOME)
                R.id.nav_explore -> showTab(TAG_EXPLORE)
                R.id.nav_library -> showTab(TAG_LIBRARY)
                R.id.nav_profile -> showTab(TAG_PROFILE)
                else -> false
            }
        }

        // Android back first returns to Home, then leaves the app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val onHome = binding.bottomNavigation.selectedItemId == R.id.nav_home
                if (!onHome && !isFinishing && !isDestroyed) {
                    binding.bottomNavigation.selectedItemId = R.id.nav_home
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })

        lifecycleScope.launch {
            UpdateChecker.checkForUpdates(this@MainActivity, showToastIfLatest = false)
        }

        handleIntent(intent)
    }

    /**
     * Tabs are created lazily and kept alive afterwards: hidden tabs do not perform
     * network work or lose scroll state.
     */
    private fun restoreFragments(savedInstanceState: Bundle?) {
        homeFragment = supportFragmentManager.findFragmentByTag(TAG_HOME) as? HomeFragment
        exploreFragment = supportFragmentManager.findFragmentByTag(TAG_EXPLORE) as? ExploreFragment
        libraryFragment = supportFragmentManager.findFragmentByTag(TAG_LIBRARY) as? LibraryFragment
        profileFragment = supportFragmentManager.findFragmentByTag(TAG_PROFILE) as? ProfileFragment

        if (savedInstanceState == null) {
            activeTag = TAG_HOME
            ensureFragment(TAG_HOME)
        } else {
            activeTag = savedInstanceState.getString(KEY_ACTIVE_TAG) ?: TAG_HOME
            binding.bottomNavigation.selectedItemId = when (activeTag) {
                TAG_EXPLORE -> R.id.nav_explore
                TAG_LIBRARY -> R.id.nav_library
                TAG_PROFILE -> R.id.nav_profile
                else -> R.id.nav_home
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_ACTIVE_TAG, activeTag)
        super.onSaveInstanceState(outState)
    }

    private fun createFragment(tag: String): Fragment = when (tag) {
        TAG_EXPLORE -> ExploreFragment()
        TAG_LIBRARY -> LibraryFragment()
        TAG_PROFILE -> ProfileFragment()
        else -> HomeFragment()
    }

    private fun ensureFragment(tag: String): Fragment {
        val existing = supportFragmentManager.findFragmentByTag(tag)
        if (existing != null) {
            cache(tag, existing)
            return existing
        }
        val fragment = createFragment(tag)
        if (supportFragmentManager.isStateSaved) {
            binding.root.post { if (!isFinishing && !isDestroyed) showTab(tag) }
            return fragment
        }
        try {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, fragment, tag)
                .commitNow()
            cache(tag, fragment)
        } catch (_: IllegalStateException) {
            binding.root.post {
                if (!isFinishing && !isDestroyed) {
                    ensureFragment(tag)
                    showTab(tag)
                }
            }
        }
        return fragment
    }

    private fun cache(tag: String, fragment: Fragment) {
        when (tag) {
            TAG_HOME -> homeFragment = fragment as? HomeFragment
            TAG_EXPLORE -> exploreFragment = fragment as? ExploreFragment
            TAG_LIBRARY -> libraryFragment = fragment as? LibraryFragment
            TAG_PROFILE -> profileFragment = fragment as? ProfileFragment
        }
    }

    private fun showTab(tag: String): Boolean {
        val target = ensureFragment(tag)
        if (!target.isAdded) return true
        if (tag == activeTag && target.isVisible) return true

        val fm = supportFragmentManager
        // Never commit during/after state saving: back or a config change can
        // land here while the host is being torn down.
        if (fm.isStateSaved || isFinishing || isDestroyed) return true
        fm.beginTransaction().apply {
            for (t in listOf(TAG_HOME, TAG_EXPLORE, TAG_LIBRARY, TAG_PROFILE)) {
                val f = fm.findFragmentByTag(t) ?: continue
                if (t == tag) show(f) else hide(f)
            }
        }.commit()
        activeTag = tag
        return true
    }

    private fun selectTab(tag: String, itemId: Int) {
        if (binding.bottomNavigation.selectedItemId != itemId) {
            binding.bottomNavigation.selectedItemId = itemId
        } else {
            showTab(tag)
        }
    }

    fun selectExploreTab(query: String? = null, scope: String = "all") {
        selectTab(TAG_EXPLORE, R.id.nav_explore)
        if (!query.isNullOrEmpty()) {
            (ensureFragment(TAG_EXPLORE) as? ExploreFragment)?.setSearchQueryAndScope(query, scope)
        }
    }

    fun selectLibraryTab() {
        selectTab(TAG_LIBRARY, R.id.nav_library)
    }

    fun selectFavoritesTab() {
        selectTab(TAG_LIBRARY, R.id.nav_library)
        (ensureFragment(TAG_LIBRARY) as? LibraryFragment)?.selectTab(LibraryFragment.TAB_LISTS)
    }

    fun selectProfileTab() {
        selectTab(TAG_PROFILE, R.id.nav_profile)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri != null) {
            try {
                val host = uri.host ?: ""
                val path = uri.path ?: ""
                val isPlaylistLink = path.contains("playlist") || host.contains("playlist") ||
                    path.trimEnd('/') == "/import"
                if (isPlaylistLink) {
                    val payload = com.hnn.bisnor.data.repository.SharedPlaylistImporter
                        .parse(uri.toString())
                    if (payload != null) {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("دریافت لیست «${payload.name}»")
                            .setMessage("این لیست شامل ${payload.items.size} اثر است. به لیست‌های شما اضافه شود؟")
                            .setPositiveButton("ذخیره") { _, _ ->
                                val saved = com.hnn.bisnor.data.repository.SharedPlaylistImporter
                                    .save(this, payload)
                                android.widget.Toast.makeText(
                                    this, "$saved اثر ذخیره شد", android.widget.Toast.LENGTH_SHORT
                                ).show()
                                selectFavoritesTab()
                            }
                            .setNegativeButton("انصراف", null)
                            .show()
                    }
                }
            } catch (_: Exception) {
                // malformed deep link — ignore
            }
        }

        val extQuery = intent?.getStringExtra("search_query")
        val extScope = intent?.getStringExtra("search_scope") ?: "all"
        if (!extQuery.isNullOrEmpty()) {
            selectExploreTab(extQuery, extScope)
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        ThemeHelper.applyTheme(this)
    }
}
