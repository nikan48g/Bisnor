package com.hnn.bisnor.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton
import com.hnn.bisnor.R
import com.hnn.bisnor.data.repository.PlaybackHistoryManager
import com.hnn.bisnor.data.repository.SharedPlaylistImporter
import com.hnn.bisnor.data.repository.SocialLink
import com.hnn.bisnor.data.repository.SocialLinksRepository
import com.hnn.bisnor.data.repository.SourcePreferences
import com.hnn.bisnor.databinding.FragmentSettingsBinding
import com.hnn.bisnor.ui.downloads.DownloadsActivity
import com.hnn.bisnor.util.PlayerLauncherHelper
import com.hnn.bisnor.util.ThemeHelper
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var historyManager: PlaybackHistoryManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        historyManager = PlaybackHistoryManager(requireContext())

        // Light / Dark / System Themes
        when (historyManager.themeMode) {
            "light" -> binding.chipThemeLight.isChecked = true
            "dark" -> binding.chipThemeDark.isChecked = true
            else -> binding.chipThemeSystem.isChecked = true
        }

        binding.chipGroupThemes.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
            val newTheme = when (checkedId) {
                R.id.chip_theme_light -> "light"
                R.id.chip_theme_dark -> "dark"
                else -> "system"
            }
            if (newTheme != historyManager.themeMode) {
                historyManager.themeMode = newTheme
                val targetNight = when (newTheme) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(targetNight)
                ThemeHelper.applyTheme(requireActivity())
                requireActivity().recreate()
            }
        }

        // Content source (Next Movie / IranFlix / both)
        when (SourcePreferences.mode) {
            SourcePreferences.IRANFLIX -> binding.chipSourceIranflix.isChecked = true
            SourcePreferences.BOTH -> binding.chipSourceBoth.isChecked = true
            else -> binding.chipSourceNext.isChecked = true
        }
        binding.chipGroupSources.setOnCheckedStateChangeListener { _, checkedIds ->
            val newMode = when (checkedIds.firstOrNull()) {
                R.id.chip_source_iranflix -> SourcePreferences.IRANFLIX
                R.id.chip_source_both -> SourcePreferences.BOTH
                else -> SourcePreferences.NEXT
            }
            if (newMode != SourcePreferences.mode) {
                SourcePreferences.setMode(newMode)
                requireActivity().recreate()
            }
        }

        // External Player Selector
        updatePlayerLabel()
        binding.btnSettingPlayerChoice.setOnClickListener {
            showPlayerBottomSheet()
        }

        // Content Warning
        binding.switchContentWarning.isChecked = historyManager.isContentWarningEnabled
        binding.switchContentWarning.setOnCheckedChangeListener { _, isChecked ->
            historyManager.isContentWarningEnabled = isChecked
        }

        // Auto Next Episode
        binding.switchAutoNext.isChecked = historyManager.isAutoNextEnabled
        binding.switchAutoNext.setOnCheckedChangeListener { _, isChecked ->
            historyManager.isAutoNextEnabled = isChecked
            updateAutoNextLabel()
        }

        updateAutoNextLabel()
        binding.btnSettingAutoNextTime.setOnClickListener {
            showAutoNextTimeDialog()
        }

        binding.btnSettingDownloads.setOnClickListener {
            startActivity(Intent(requireContext(), DownloadsActivity::class.java))
        }

        binding.btnSettingImport.setOnClickListener { showImportDialog() }

        lifecycleScope.launch { loadSocialLinks() }
    }

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
            params.marginEnd = (10 * resources.displayMetrics.density).toInt()
            layoutParams = params
            setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                runCatching { startActivity(intent) }
                    .onFailure { Toast.makeText(requireContext(), "برنامه‌ای برای باز کردن این لینک نیست", Toast.LENGTH_SHORT).show() }
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
        else -> R.drawable.ic_social_website
    }

    private fun showImportDialog() {
        val input = EditText(requireContext()).apply {
            hint = "https://…/import?data=… یا کد دریافتی"
            setSingleLine(false)
            minLines = 2
            maxLines = 5
            setTextColor(resources.getColor(R.color.on_surface, null))
            setHintTextColor(resources.getColor(R.color.outline, null))
            gravity = Gravity.START or Gravity.TOP
            setPadding(24, 24, 24, 24)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("وارد کردن واچ‌لیست")
            .setMessage("لینک اشتراک‌گذاری یا کد را اینجا بچسبانید؛ لیست به‌صورت یک پلی‌لیست جدید ذخیره می‌شود.")
            .setView(input)
            .setPositiveButton("افزودن") { _, _ ->
                val payload = SharedPlaylistImporter.parse(input.text?.toString())
                if (payload == null) {
                    Toast.makeText(requireContext(), "کد قابل خواندن نیست یا لیست خالی است", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val saved = SharedPlaylistImporter.save(requireContext(), payload)
                Toast.makeText(requireContext(), "$saved اثر به پلی‌لیست اضافه شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun updatePlayerLabel() {
        val players = PlayerLauncherHelper.getSupportedPlayers(requireContext())
        val current = players.find { it.id == historyManager.preferredPlayer } ?: players.first()
        binding.tvCurrentPlayerLabel.text = current.name
    }

    private fun showPlayerBottomSheet() {
        val context = requireContext()
        val bottomSheet = BottomSheetDialog(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 24)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val header = TextView(context).apply {
            text = "انتخاب پخش‌کننده ویدیو"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setPadding(24, 16, 24, 16)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        container.addView(header)

        val players = PlayerLauncherHelper.getSupportedPlayers(context)
        for (player in players) {
            val itemView = LayoutInflater.from(context).inflate(R.layout.item_player_choice, container, false)
            val imgIcon = itemView.findViewById<ImageView>(R.id.img_player_icon)
            val tvName = itemView.findViewById<TextView>(R.id.tv_player_name)
            val tvStatus = itemView.findViewById<TextView>(R.id.tv_player_status)
            val radio = itemView.findViewById<MaterialRadioButton>(R.id.radio_player_selected)
            val btnInstall = itemView.findViewById<MaterialButton>(R.id.btn_player_install)

            tvName.text = player.name
            imgIcon.imageTintList = null
            if (player.appIcon != null) {
                imgIcon.setImageDrawable(player.appIcon)
            } else {
                imgIcon.setImageResource(player.iconRes)
            }

            val isSelected = player.id == historyManager.preferredPlayer

            if (player.isInstalled) {
                tvStatus.text = if (player.id == "internal") "پیش‌فرض بیسنور • پشتیبانی از ژست لمسی" else "نصب شده و آماده پخش"
                tvStatus.setTextColor(resources.getColor(R.color.on_surface_variant, null))
                radio.visibility = View.VISIBLE
                radio.isChecked = isSelected
                btnInstall.visibility = View.GONE
            } else {
                tvStatus.text = "روی دستگاه شما نصب نیست"
                tvStatus.setTextColor(resources.getColor(R.color.outline, null))
                radio.visibility = View.GONE
                btnInstall.visibility = View.VISIBLE
                btnInstall.setOnClickListener {
                    PlayerLauncherHelper.openInGooglePlay(context, player.packageName)
                    bottomSheet.dismiss()
                }
            }

            itemView.setOnClickListener {
                if (player.isInstalled) {
                    historyManager.preferredPlayer = player.id
                    updatePlayerLabel()
                    Toast.makeText(context, "پخش‌کننده «${player.name}» فعال شد", Toast.LENGTH_SHORT).show()
                    bottomSheet.dismiss()
                } else {
                    PlayerLauncherHelper.openInGooglePlay(context, player.packageName)
                    bottomSheet.dismiss()
                }
            }

            container.addView(itemView)
        }

        bottomSheet.setContentView(container)
        bottomSheet.show()
    }

    private fun updateAutoNextLabel() {
        if (!historyManager.isAutoNextEnabled) {
            binding.tvAutoNextMinutesLabel.text = "غیرفعال"
            binding.tvAutoNextMinutesLabel.setTextColor(resources.getColor(R.color.outline, null))
            binding.btnSettingAutoNextTime.alpha = 0.6f
        } else {
            val mins = historyManager.autoNextMinutes
            binding.tvAutoNextMinutesLabel.text = "$mins دقیقه مانده به پایان"
            binding.tvAutoNextMinutesLabel.setTextColor(resources.getColor(R.color.primary, null))
            binding.btnSettingAutoNextTime.alpha = 1.0f
        }
    }

    private fun showAutoNextTimeDialog() {
        val options = arrayOf(
            "غیرفعال (عدم پخش خودکار قسمت بعدی)",
            "۱ دقیقه مانده به پایان",
            "۲ دقیقه مانده به پایان",
            "۳ دقیقه مانده به پایان",
            "۵ دقیقه مانده به پایان"
        )
        val values = intArrayOf(0, 1, 2, 3, 5)
        val currentIndex = if (!historyManager.isAutoNextEnabled) {
            0
        } else {
            val idx = values.indexOf(historyManager.autoNextMinutes)
            if (idx > 0) idx else 2
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("پیشنهاد و پخش خودکار قسمت بعدی")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val selected = values[which]
                if (selected == 0) {
                    historyManager.isAutoNextEnabled = false
                    binding.switchAutoNext.isChecked = false
                } else {
                    historyManager.isAutoNextEnabled = true
                    historyManager.autoNextMinutes = selected
                    binding.switchAutoNext.isChecked = true
                }
                updateAutoNextLabel()
                dialog.dismiss()
            }
            .show()
    }

    private fun showAutoNextCountdownDialog() {
        val options = arrayOf("۵ ثانیه", "۱۰ ثانیه", "۱۵ ثانیه", "۲۰ ثانیه", "۳۰ ثانیه")
        val values = intArrayOf(5, 10, 15, 20, 30)
        val currentIndex = values.indexOf(historyManager.autoNextCountdownSeconds).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("مدت زمان شمارش معکوس")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                historyManager.autoNextCountdownSeconds = values[which]
                updateAutoNextLabel()
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
