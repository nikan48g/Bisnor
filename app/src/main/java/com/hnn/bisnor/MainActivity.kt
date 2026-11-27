package com.hnn.bisnor

import com.hnn.bisnor.R
import com.hnn.bisnor.util.Tr

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
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
    private var topInset = 0

    private val isTv: Boolean by lazy {
        val ui = getSystemService(UI_MODE_SERVICE) as android.app.UiModeManager
        ui.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }

    /** Tablets and TVs get an Apple TV style tab bar floating at the top-left. */
    private val navOnTop: Boolean by lazy { isTv || resources.configuration.smallestScreenWidthDp >= 600 }

    /**
     * tvOS behaviour: the tab bar is shown while it has focus and slides away once the
     * D-pad moves into the content, so a focused card is never hidden underneath it.
     */
    private fun setupTvTabBar() {
        binding.root.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            val inNav = newFocus != null && isDescendantOf(newFocus, binding.navGlass)
            binding.navGlass.animate()
                .alpha(if (inNav) 1f else 0f)
                .translationY(if (inNav) 0f else -binding.navGlass.height * 0.7f)
                .setDuration(220)
                .start()
        }
        // Start on the tab bar, like Apple TV, instead of a random card.
        binding.bottomNavigation.post {
            binding.bottomNavigation.findViewById<View>(binding.bottomNavigation.selectedItemId)?.requestFocus()
        }
    }

    /** Space a screen must leave at its top for the floating tab bar (0 on phones). */
    val topNavSpace: Int
        get() = if (navOnTop) binding.navGlass.layoutParams.height + (28 * resources.displayMetrics.density).toInt() else 0

    /** Home draws its hero edge-to-edge under the status bar; other tabs start below it (and the top bar). */
    private fun applyTopInset() {
        binding.fragmentContainer.updatePadding(top = if (activeTag == TAG_HOME) 0 else topInset + topNavSpace)
    }

    private fun placeNavOnTop() {
        val d = resources.displayMetrics.density
        binding.navGlass.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
            bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            leftToLeft = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            width = (560 * d).toInt()
            height = (76 * d).toInt()
            leftMargin = (28 * d).toInt()
        }
        binding.navGlass.cornerRadius = 38 * d
        binding.bottomNavigation.itemIconSize = (28 * d).toInt()
        binding.bottomNavigation.setItemTextAppearanceActive(R.style.TextAppearance_Bisnor_BottomNav_Large_Active)
        binding.bottomNavigation.setItemTextAppearanceInactive(R.style.TextAppearance_Bisnor_BottomNav_Large)
    }

    companion object {
        private const val TAG_HOME = "tab_home"
        private const val TAG_EXPLORE = "tab_explore"
        private const val TAG_LIBRARY = "tab_library"
        private const val TAG_PROFILE = "tab_profile"
        private const val KEY_ACTIVE_TAG = "active_tab_tag"
    }

    // D-pad: the floating nav bar is not reachable by plain focus search on TV, so route Down/Up to it.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && ::binding.isInitialized) {
            val nav = binding.bottomNavigation
            val focused = currentFocus
            val toNav = if (navOnTop) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
            val fromNav = if (navOnTop) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
            val dir = if (navOnTop) View.FOCUS_UP else View.FOCUS_DOWN
            if (event.keyCode == toNav && focused != null &&
                !isDescendantOf(focused, nav) &&
                focused.focusSearch(dir) == null
            ) {
                nav.findViewById<View>(nav.selectedItemId)?.requestFocus()
                return true
            }
            if (event.keyCode == fromNav && focused != null && isDescendantOf(focused, nav)) {
                // Nearest view in that direction (e.g. the header right under the bar),
                // not whatever the container happens to list first.
                val dirOut = if (navOnTop) View.FOCUS_DOWN else View.FOCUS_UP
                val next = android.view.FocusFinder.getInstance()
                    .findNextFocus(binding.root as ViewGroup, focused, dirOut)
                    ?.takeIf { !isDescendantOf(it, nav) }
                (next ?: binding.fragmentContainer).requestFocus(dirOut)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { runCatching { com.hnn.bisnor.data.remote.AuthManager(applicationContext).refreshFromCloud(applicationContext) } }
    }

    /** Slides the glass highlight over the selected tab, like light catching a lens. */
    private fun glideNavLight(itemId: Int, animate: Boolean = true) {
        binding.bottomNavigation.post {
            val item = binding.bottomNavigation.findViewById<View>(itemId) ?: return@post
            binding.navGlass.moveLightTo(item.x + item.width / 2f, animate)
        }
    }

    private fun isDescendantOf(v: View, root: View): Boolean {
        var p: android.view.ViewParent? = v.parent
        while (p != null) { if (p === root) return true; p = p.parent }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        com.hnn.bisnor.ui.startup.StartupOverlay.show(this, binding.root as android.view.ViewGroup, listOf(binding.navGlass))

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topInset = bars.top
            applyTopInset()
            val baseMargin = (14 * resources.displayMetrics.density).toInt()
            binding.navGlass.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                if (navOnTop) topMargin = baseMargin + bars.top else bottomMargin = baseMargin + bars.bottom
            }
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { view, insets ->
            view.setPadding(0, 0, 0, 0)
            insets
        }

        // Material paints its own surface behind the items; the glass must show through.
        binding.bottomNavigation.background = null
        if (navOnTop) placeNavOnTop()
        if (isTv) setupTvTabBar()
        restoreFragments(savedInstanceState)
        glideNavLight(binding.bottomNavigation.selectedItemId, animate = false)

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            glideNavLight(item.itemId)
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
        applyTopInset()
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
                    path.trimEnd('/') == "/import" || path.startsWith("/s/")
                if (host.contains("workers.dev") && path.startsWith("/t/")) {
                    // Shared title: open it in Explore by name.
                    val title = android.net.Uri.decode(path.removePrefix("/t/")).trim()
                    if (title.isNotEmpty()) selectExploreTab(title, "all")
                } else if (isPlaylistLink) {
                    lifecycleScope.launch {
                        val payload = com.hnn.bisnor.data.repository.SharedPlaylistImporter.resolve(uri.toString())
                        if (payload == null) {
                            android.widget.Toast.makeText(
                                this@MainActivity, Tr.s(R.string.s_1d06817), android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else if (!isFinishing && !isDestroyed) {
                            com.hnn.bisnor.util.ImportListDialog.show(this@MainActivity, payload) { selectFavoritesTab() }
                        }
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
