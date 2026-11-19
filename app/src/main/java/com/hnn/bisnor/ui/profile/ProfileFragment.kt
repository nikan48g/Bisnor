package com.hnn.bisnor.ui.profile

import com.hnn.bisnor.util.Tr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hnn.bisnor.BuildConfig
import com.hnn.bisnor.MainActivity
import com.hnn.bisnor.R
import com.hnn.bisnor.data.database.SmartTasteDatabase
import com.hnn.bisnor.data.remote.AuthManager
import com.hnn.bisnor.data.repository.PlaybackHistoryManager
import com.hnn.bisnor.data.repository.PlaybackProgress
import com.hnn.bisnor.data.repository.SocialLink
import com.hnn.bisnor.data.repository.SocialLinksRepository
import com.hnn.bisnor.databinding.FragmentProfileBinding
import com.hnn.bisnor.databinding.ItemContinueWatchingBinding
import com.hnn.bisnor.ui.downloads.DownloadsActivity
import com.hnn.bisnor.ui.player.PlayerActivity
import com.hnn.bisnor.ui.settings.SettingsActivity
import com.hnn.bisnor.util.AuthDialogHelper
import com.hnn.bisnor.util.ImageUrlHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var authManager: AuthManager
    private lateinit var historyManager: PlaybackHistoryManager
    private lateinit var tasteDb: SmartTasteDatabase
    private var heatmapView: ActivityHeatmapView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupServerCard()
        viewLifecycleOwner.lifecycleScope.launch { loadSocialLinks() }
        authManager = AuthManager(requireContext())
        historyManager = PlaybackHistoryManager(requireContext())
        tasteDb = SmartTasteDatabase(requireContext())

        binding.btnProfileSettings.setOnClickListener { openSettings() }
        binding.btnProfileAppearance.setOnClickListener { openSettings() }
        binding.btnProfileDownloads.setOnClickListener {
            startActivity(Intent(requireContext(), DownloadsActivity::class.java))
        }
        binding.btnProfileAccount.setOnClickListener {
            if (authManager.isLoggedIn) {
                startActivity(Intent(requireContext(), AccountProfileActivity::class.java))
            } else {
                showAuthDialog()
            }
        }
        binding.btnProfileAction.setOnClickListener {
            if (authManager.isLoggedIn) {
                startActivity(Intent(requireContext(), AccountProfileActivity::class.java))
            } else {
                showAuthDialog()
            }
        }
        binding.btnProfileAbout.setOnClickListener { showAboutDialog() }
        binding.btnProfileLogout.setOnClickListener { confirmLogout() }

        // Created programmatically: responsive on any width, and immune to XML inflation issues.
        val heatmap = ActivityHeatmapView(requireContext())
        binding.heatmapContainer.addView(
            heatmap,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        heatmapView = heatmap
    }

    override fun onResume() {
        super.onResume()
        if (_binding == null) return
        bindIdentity()
        renderStats()
        renderHeatmap()
        renderContinueWatching()
    }

    // ---------------------------------------------------------------- identity

    private fun bindIdentity() {
        if (_binding == null) return
        val loggedIn = authManager.isLoggedIn
        val name = authManager.currentUsername
        binding.tvProfileName.text = if (loggedIn && name.isNotBlank()) {
            name.replaceFirstChar { it.uppercase() }
        } else {
            Tr.s(R.string.s_b315663)
        }
        binding.tvProfileUsername.text = when {
            loggedIn -> "@${name}"
            else -> Tr.s(R.string.s_9196d0f)
        }
        // Logged-in users manage the profile from the account row below; the
        // header itself must stay informational.
        binding.btnProfileAction.visibility = if (loggedIn) View.GONE else View.VISIBLE
        binding.btnProfileAction.text = Tr.s(R.string.s_32a81e5)

        val avatarUrl = authManager.userAvatarUrl
        binding.tvProfileInitial.visibility = View.GONE
        binding.imgProfileAvatar.visibility = View.VISIBLE
        when {
            avatarUrl.isNotEmpty() -> binding.imgProfileAvatar.load(avatarUrl) {
                crossfade(true)
                placeholder(AuthManager.getAvatarDrawable(authManager.userAvatarId))
                error(AuthManager.getAvatarDrawable(authManager.userAvatarId))
            }
            loggedIn -> binding.imgProfileAvatar.setImageResource(
                AuthManager.getAvatarDrawable(authManager.userAvatarId)
            )
            else -> binding.imgProfileAvatar.setImageResource(R.drawable.ic_account_circle)
        }

        binding.btnProfileLogout.visibility = if (loggedIn) View.VISIBLE else View.GONE
        binding.dividerLogout.visibility = if (loggedIn) View.VISIBLE else View.GONE
        binding.tvProfileVersion.text = "v${BuildConfig.VERSION_NAME}"
    }

    // ---------------------------------------------------------------- stats & heatmap

    private fun renderStats() {
        lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) { tasteDb.getProfileStats() }
            if (_binding == null) return@launch
            binding.tvStatMovies.text = toPersianDigits(stats.moviesWatched.toString())
            binding.tvStatSeries.text = toPersianDigits(stats.seriesWatched.toString())
            val hours = stats.totalWatchTimeMs / 3_600_000
            val minutes = (stats.totalWatchTimeMs % 3_600_000) / 60_000
            binding.tvStatHours.text = if (hours > 0) {
                toPersianDigits("${hours}")
            } else if (minutes > 0) {
                toPersianDigits("${minutes}")
            } else {
                Tr.s(R.string.s_9d13af3)
            }
        }
    }

    private fun renderHeatmap() {
        lifecycleScope.launch {
            val daily = withContext(Dispatchers.IO) { tasteDb.getDailyWatchActivity(371) }
            if (_binding == null) return@launch
            heatmapView?.setData(daily)
            val totalMs = daily.values.sum()
            val totalMinutes = totalMs / 60_000
            binding.tvHeatmapTotal.text = if (totalMinutes <= 0) {
                Tr.s(R.string.s_165767b)
            } else {
                val h = totalMinutes / 60
                val m = totalMinutes % 60
                if (h > 0) {
                    Tr.s(R.string.s_6596bc9, toPersianDigits(h.toString()), toPersianDigits(m.toString()))
                } else {
                    Tr.s(R.string.s_94d5d5d, toPersianDigits(m.toString()))
                }            }
        }
    }

    private fun renderContinueWatching() {
        if (_binding == null) return
        val items = runCatching { historyManager.getAllContinueWatching() }.getOrDefault(emptyList())
        if (items.isEmpty()) {
            binding.layoutProfileContinue.visibility = View.GONE
            return
        }
        binding.layoutProfileContinue.visibility = View.VISIBLE
        binding.recyclerProfileContinue.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerProfileContinue.adapter = ContinueAdapter(items)
    }

    private inner class ContinueAdapter(private val items: List<PlaybackProgress>) :
        RecyclerView.Adapter<ContinueAdapter.Holder>() {

        inner class Holder(val b: ItemContinueWatchingBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val b = ItemContinueWatchingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(b)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            if (item.mediaCover.isNotEmpty()) {
                holder.b.imgContinueCover.load(ImageUrlHelper.getOptimizedImageUrl(item.mediaCover)) {
                    crossfade(true)
                }
            }
            holder.b.tvContinueTitle.text = item.mediaTitle.ifBlank { Tr.s(R.string.s_f025339) }
            val mins = (item.positionMs / 60000).toInt()
            val total = (item.durationMs / 60000).toInt()
            holder.b.tvContinueSubtitle.text = buildString {
                if (item.episodeTitle.isNotBlank()) {
                    append(item.episodeTitle)
                    append(" • ")
                }
                if (total > 0) append(Tr.s(R.string.s_a8fdbeb, mins, total)) else append(Tr.s(R.string.s_b59d572, mins))
            }
            holder.b.progressContinueBar.progress = item.progressPercent
            holder.b.root.setOnClickListener {
                startActivity(
                    Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra("video_title", item.mediaTitle)
                        putExtra("video_url", item.url)
                        putExtra("media_id", item.mediaId)
                        putExtra("media_title", item.mediaTitle)
                        putExtra("media_cover", item.mediaCover)
                        putExtra("episode_title", item.episodeTitle)
                        putExtra("episode_index", item.episodeIndex)
                        putExtra("start_position", item.positionMs)
                    }
                )
            }
        }

        override fun getItemCount(): Int = items.size
    }

    // ---------------------------------------------------------------- actions

    private fun openSettings() {
        startActivity(Intent(requireContext(), SettingsActivity::class.java))
    }

    private fun showAuthDialog() {
        AuthDialogHelper.show(requireContext(), authManager, viewLifecycleOwner.lifecycleScope) {
            if (_binding == null) return@show
            bindIdentity()
            renderStats()
        }
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(Tr.s(R.string.s_e75c397))
            .setMessage(Tr.s(R.string.s_fa09508))
            .setPositiveButton(Tr.s(R.string.s_6080666)) { _, _ ->
                authManager.logout()
                Toast.makeText(requireContext(), Tr.s(R.string.s_9825d25), Toast.LENGTH_SHORT).show()
                bindIdentity()
            }
            .setNegativeButton(Tr.s(R.string.s_c8d2a1f), null)
            .show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(Tr.s(R.string.s_ce0639b))
            .setMessage(
                Tr.s(R.string.s_8335edc, BuildConfig.VERSION_NAME) +
                    Tr.s(R.string.s_e055ad0) +
                    Tr.s(R.string.s_ef370fa)
            )
            .setPositiveButton(Tr.s(R.string.s_8bbf960)) { _, _ ->
                runCatching {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/nikan48g/Bisnor")
                        )
                    )
                }
            }
            .setNegativeButton(Tr.s(R.string.s_fa88277), null)
            .show()
    }

    private fun setupServerCard() {
        val sp = com.hnn.bisnor.data.repository.SourcePreferences
        val box = binding.serverOptions
        fun render() {
            binding.switchServerAuto.setOnCheckedChangeListener(null)
            binding.switchServerAuto.isChecked = sp.auto
            binding.tvServerHint.visibility = if (sp.auto) View.VISIBLE else View.GONE
            box.removeAllViews()
            val dp = resources.displayMetrics.density
            val items = listOf(
                sp.NEXT to R.string.srv_1, sp.IRANFLIX to R.string.srv_2, sp.BOTH to R.string.srv_both
            )
            for ((mode, label) in items) {
                val selected = sp.manualMode == mode
                box.addView(android.widget.TextView(requireContext()).apply {
                    setText(label)
                    setTextAppearance(R.style.TextAppearance_Bisnor_Body)
                    val v = (14 * dp).toInt(); setPadding(v, v, v, v)
                    setBackgroundResource(R.drawable.bg_onb_option)
                    isSelected = selected && !sp.auto
                    alpha = if (sp.auto) 0.45f else 1f
                    isEnabled = !sp.auto; isFocusable = !sp.auto; isClickable = !sp.auto
                    layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8 * dp).toInt() }
                    setOnClickListener { sp.setMode(mode); render() }
                })
            }
            binding.switchServerAuto.setOnCheckedChangeListener { _, on -> sp.setAuto(on); render() }
        }
        render()
    }

    private fun toPersianDigits(value: String): String {
        if (!com.hnn.bisnor.util.AppPrefs.isRtl(requireContext())) return value
        val builder = StringBuilder(value.length)
        value.forEach { c ->
            builder.append(
                when (c) {
                    '0' -> '۰'; '1' -> '۱'; '2' -> '۲'; '3' -> '۳'; '4' -> '۴'
                    '5' -> '۵'; '6' -> '۶'; '7' -> '۷'; '8' -> '۸'; '9' -> '۹'
                    else -> c
                }
            )
        }
        return builder.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ------------------------------------------------------------------ follow us

    private suspend fun loadSocialLinks() {
        val links = SocialLinksRepository.fetch()
        if (!isAdded) return
        if (links.isEmpty()) {
            binding.settingsSocialSection.visibility = View.GONE
            return
        }
        val row = binding.socialLinksRow
        row.removeAllViews()
        for (link in links) row.addView(buildSocialButton(link))
        binding.settingsSocialSection.visibility = View.VISIBLE
    }

    private fun buildSocialButton(link: SocialLink): ImageButton {
        val size = (46 * resources.displayMetrics.density).toInt()
        val padding = (11 * resources.displayMetrics.density).toInt()
        return ImageButton(requireContext()).apply {
            setImageResource(socialIcon(link.platform))
            imageTintList = android.content.res.ColorStateList.valueOf(
                resources.getColor(R.color.on_surface, null)
            )
            background = resources.getDrawable(R.drawable.bg_social_icon, null)
            contentDescription = link.label
            if (android.os.Build.VERSION.SDK_INT >= 26) tooltipText = link.label
            setPadding(padding, padding, padding, padding)
            val params = LinearLayout.LayoutParams(size, size)
            // Equal margins on both sides keep the centred row symmetric.
            params.marginStart = (6 * resources.displayMetrics.density).toInt()
            params.marginEnd = params.marginStart
            layoutParams = params
            setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                runCatching { startActivity(intent) }
                    .onFailure { Toast.makeText(requireContext(), Tr.s(R.string.s_96efa99), Toast.LENGTH_SHORT).show() }
            }
            isFocusable = true
        }
    }

    private fun socialIcon(platform: String): Int = when (platform) {
        "instagram" -> R.drawable.ic_social_instagram
        "telegram" -> R.drawable.ic_social_telegram
        "x" -> R.drawable.ic_social_x
        "youtube" -> R.drawable.ic_social_youtube
        "facebook" -> R.drawable.ic_social_facebook
        "email" -> R.drawable.ic_social_email
        "github" -> R.drawable.ic_github
        else -> R.drawable.ic_social_website
    }
}
