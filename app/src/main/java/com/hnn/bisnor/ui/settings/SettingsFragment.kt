package com.hnn.bisnor.ui.settings

import com.hnn.bisnor.util.Tr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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

        // Language, interface style, font
        val ctx = requireContext()
        if (com.hnn.bisnor.util.AppPrefs.lang(ctx) == com.hnn.bisnor.util.AppPrefs.LANG_FA) binding.chipLangFa.isChecked = true else binding.chipLangEn.isChecked = true
        binding.chipGroupLang.setOnCheckedStateChangeListener { _, ids ->
            val tag = if (ids.firstOrNull() == R.id.chip_lang_en) com.hnn.bisnor.util.AppPrefs.LANG_EN else com.hnn.bisnor.util.AppPrefs.LANG_FA
            if (tag != com.hnn.bisnor.util.AppPrefs.lang(ctx)) { com.hnn.bisnor.util.AppPrefs.setLang(ctx, tag); requireActivity().recreate() }
        }
        if (com.hnn.bisnor.util.AppPrefs.glass(ctx)) binding.chipUiModern.isChecked = true else binding.chipUiClassic.isChecked = true
        binding.chipGroupUi.setOnCheckedStateChangeListener { _, ids ->
            val glass = ids.firstOrNull() != R.id.chip_ui_classic
            if (glass != com.hnn.bisnor.util.AppPrefs.glass(ctx)) { com.hnn.bisnor.util.AppPrefs.setGlass(ctx, glass); requireActivity().recreate() }
        }
        if (com.hnn.bisnor.util.AppPrefs.font(ctx) == "fancy") binding.chipFontFancy.isChecked = true else binding.chipFontAjor.isChecked = true
        binding.chipGroupFont.setOnCheckedStateChangeListener { _, ids ->
            val f = if (ids.firstOrNull() == R.id.chip_font_fancy) "fancy" else "ajor"
            if (f != com.hnn.bisnor.util.AppPrefs.font(ctx)) { com.hnn.bisnor.util.AppPrefs.setFont(ctx, f); requireActivity().recreate() }
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

        binding.tvSettingVersion.text = Tr.s(R.string.s_95197c8, com.hnn.bisnor.BuildConfig.VERSION_NAME)
        binding.btnSettingCheckUpdate.setOnClickListener {
            lifecycleScope.launch {
                com.hnn.bisnor.util.UpdateChecker.checkForUpdates(requireActivity(), showToastIfLatest = true)
            }
        }
        binding.btnSettingClearCache.setOnClickListener {
            val loader = coil.Coil.imageLoader(requireContext())
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
            Toast.makeText(requireContext(), Tr.s(R.string.s_629b87d), Toast.LENGTH_SHORT).show()
        }

    }

    private fun showImportDialog() {
        val input = EditText(requireContext()).apply {
            hint = Tr.s(R.string.s_a54ea1c)
            setSingleLine(false)
            minLines = 2
            maxLines = 5
            setTextColor(resources.getColor(R.color.on_surface, null))
            setHintTextColor(resources.getColor(R.color.outline, null))
            gravity = Gravity.START or Gravity.TOP
            setPadding(24, 24, 24, 24)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(Tr.s(R.string.s_594a166))
            .setMessage(Tr.s(R.string.s_e8e9a8d))
            .setView(input)
            .setPositiveButton(Tr.s(R.string.s_20e034c)) { _, _ ->
                val text = input.text?.toString()
                viewLifecycleOwner.lifecycleScope.launch {
                    val payload = SharedPlaylistImporter.resolve(text)
                    if (!isAdded) return@launch
                    if (payload == null) {
                        Toast.makeText(requireContext(), Tr.s(R.string.s_936795a), Toast.LENGTH_LONG).show()
                    } else {
                        com.hnn.bisnor.util.ImportListDialog.show(requireContext(), payload)
                    }
                }
            }
            .setNegativeButton(Tr.s(R.string.s_c8d2a1f), null)
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
            layoutDirection = if (com.hnn.bisnor.util.AppPrefs.isRtl(requireContext())) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        }

        val header = TextView(context).apply {
            text = Tr.s(R.string.s_4ff4020)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setPadding(24, 16, 24, 16)
            layoutDirection = if (com.hnn.bisnor.util.AppPrefs.isRtl(requireContext())) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
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
                tvStatus.text = if (player.id == "internal") Tr.s(R.string.s_bc8ade3) else Tr.s(R.string.s_bc6b5e9)
                tvStatus.setTextColor(resources.getColor(R.color.on_surface_variant, null))
                radio.visibility = View.VISIBLE
                radio.isChecked = isSelected
                btnInstall.visibility = View.GONE
            } else {
                tvStatus.text = Tr.s(R.string.s_da68e31)
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
                    Toast.makeText(context, Tr.s(R.string.s_60747f5, player.name), Toast.LENGTH_SHORT).show()
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
            binding.tvAutoNextMinutesLabel.text = Tr.s(R.string.s_7fdadc7)
            binding.tvAutoNextMinutesLabel.setTextColor(resources.getColor(R.color.outline, null))
            binding.btnSettingAutoNextTime.alpha = 0.6f
        } else {
            val mins = historyManager.autoNextMinutes
            binding.tvAutoNextMinutesLabel.text = Tr.s(R.string.s_e4e1c29, mins)
            binding.tvAutoNextMinutesLabel.setTextColor(resources.getColor(R.color.primary, null))
            binding.btnSettingAutoNextTime.alpha = 1.0f
        }
    }

    private fun showAutoNextTimeDialog() {
        val options = arrayOf(
            Tr.s(R.string.s_60a4837),
            Tr.s(R.string.s_ede27ca),
            Tr.s(R.string.s_34205e4),
            Tr.s(R.string.s_01ff2fb),
            Tr.s(R.string.s_0d23d82)
        )
        val values = intArrayOf(0, 1, 2, 3, 5)
        val currentIndex = if (!historyManager.isAutoNextEnabled) {
            0
        } else {
            val idx = values.indexOf(historyManager.autoNextMinutes)
            if (idx > 0) idx else 2
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(Tr.s(R.string.s_4e9387a))
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
        val options = arrayOf(Tr.s(R.string.s_19d9e2e), Tr.s(R.string.s_c069a96), Tr.s(R.string.s_a3f1241), Tr.s(R.string.s_6ca46aa), Tr.s(R.string.s_0041beb))
        val values = intArrayOf(5, 10, 15, 20, 30)
        val currentIndex = values.indexOf(historyManager.autoNextCountdownSeconds).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(Tr.s(R.string.s_017d758))
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
