package com.hnn.bisnor.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
            "مهمان"
        }
        binding.tvProfileUsername.text = when {
            loggedIn -> "@$name"
            else -> "برای همگام‌سازی وارد شوید"
        }
        // Logged-in users manage the profile from the account row below; the
        // header itself must stay informational.
        binding.btnProfileAction.visibility = if (loggedIn) View.GONE else View.VISIBLE
        binding.btnProfileAction.text = "ورود"

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
                toPersianDigits("$hours")
            } else if (minutes > 0) {
                toPersianDigits("$minutes")
            } else {
                "۰"
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
                "هنوز فعالیتی ثبت نشده"
            } else {
                val h = totalMinutes / 60
                val m = totalMinutes % 60
                if (h > 0) {
                    "در بازه اخیر: ${toPersianDigits(h.toString())} ساعت و ${toPersianDigits(m.toString())} دقیقه"
                } else {
                    "در بازه اخیر: ${toPersianDigits(m.toString())} دقیقه"
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
            holder.b.tvContinueTitle.text = item.mediaTitle.ifBlank { "در حال تماشا" }
            val mins = (item.positionMs / 60000).toInt()
            val total = (item.durationMs / 60000).toInt()
            holder.b.tvContinueSubtitle.text = buildString {
                if (item.episodeTitle.isNotBlank()) {
                    append(item.episodeTitle)
                    append(" • ")
                }
                if (total > 0) append("$mins از $total دقیقه") else append("$mins دقیقه")
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
            .setTitle("خروج از حساب")
            .setMessage("از حساب کاربری خارج می‌شوید؟ داده‌های محلی تماشا روی این دستگاه باقی می‌مانند.")
            .setPositiveButton("خروج") { _, _ ->
                authManager.logout()
                Toast.makeText(requireContext(), "از حساب خارج شدید", Toast.LENGTH_SHORT).show()
                bindIdentity()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("بیسنور • تماشا بدون مرز")
            .setMessage(
                "نسخه ${BuildConfig.VERSION_NAME}\n\n" +
                    "بیسنور یک کلاینت متن‌باز برای مرور و پخش فیلم و سریال است. " +
                    "اطلاعات و لینک‌های پخش از سرویس ایران‌فلیکس دریافت می‌شوند."
            )
            .setPositiveButton("مخزن گیت‌هاب") { _, _ ->
                runCatching {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/nikan48g/Bisnor")
                        )
                    )
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun toPersianDigits(value: String): String {
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
}
