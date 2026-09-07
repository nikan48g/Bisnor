package com.hnn.bisnor.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.google.android.material.textfield.TextInputEditText
import com.hnn.bisnor.MainActivity
import com.hnn.bisnor.R
import com.hnn.bisnor.data.remote.AuthManager
import com.hnn.bisnor.data.repository.PlaybackHistoryManager
import com.hnn.bisnor.databinding.FragmentSettingsBinding
import com.hnn.bisnor.ui.downloads.DownloadsActivity
import com.hnn.bisnor.util.PlayerLauncherHelper
import com.hnn.bisnor.util.UpdateChecker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var historyManager: PlaybackHistoryManager
    private lateinit var authManager: AuthManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        historyManager = PlaybackHistoryManager(requireContext())
        authManager = AuthManager(requireContext())

        setupUserProfileSection()

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
        }

        updateAutoNextLabel()
        binding.btnSettingAutoNextTime.setOnClickListener {
            showAutoNextTimeDialog()
        }

        binding.btnSettingAutoNextCountdown.setOnClickListener {
            showAutoNextCountdownDialog()
        }

        binding.btnSettingDownloads.setOnClickListener {
            startActivity(Intent(requireContext(), DownloadsActivity::class.java))
        }

        binding.btnSettingPlaylists.setOnClickListener {
            (activity as? MainActivity)?.selectFavoritesTab()
        }

        binding.btnCheckUpdate.setOnClickListener {
            lifecycleScope.launch {
                Toast.makeText(requireContext(), "در حال بررسی آخرین نسخه در گیت‌هاب...", Toast.LENGTH_SHORT).show()
                UpdateChecker.checkForUpdates(requireContext(), showToastIfLatest = true)
            }
        }
    }

    private fun setupUserProfileSection() {
        if (authManager.isLoggedIn) {
            binding.tvProfileUsername.text = "@${authManager.currentUsername}"
            binding.imgProfileAvatar.setImageResource(AuthManager.getAvatarDrawable(authManager.userAvatarId))
            binding.layoutProfileExtraActions.visibility = View.VISIBLE

            if (authManager.lastSyncTime > 0L) {
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(authManager.lastSyncTime))
                binding.tvProfileSyncStatus.text = "همگام‌سازی ابری فعال (آخرین: $timeStr)"
            } else {
                binding.tvProfileSyncStatus.text = "متصل به فضای ابری"
            }
            binding.btnProfileAction.text = "همگام‌سازی 🔄"
            binding.btnProfileAction.setOnClickListener {
                lifecycleScope.launch {
                    Toast.makeText(requireContext(), "در حال همگام‌سازی لیست‌ها با سرور...", Toast.LENGTH_SHORT).show()
                    val ok = authManager.syncUp(requireContext())
                    if (ok) {
                        setupUserProfileSection()
                        Toast.makeText(requireContext(), "لیست‌ها و نشان‌شده‌ها با موفقیت در ابری ذخیره شدند!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "خطا در همگام‌سازی. لطفاً اتصال اینترنت را چک کنید.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            binding.imgProfileAvatar.setOnClickListener {
                showAvatarPickerDialog()
            }
            binding.btnProfileChangeAvatar.setOnClickListener {
                showAvatarPickerDialog()
            }
            binding.imgAvatarBadgeEdit.visibility = View.VISIBLE

            binding.cardUserProfile.setOnLongClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("خروج از حساب")
                    .setMessage("آیا مایل به خروج از حساب کاربری «${authManager.currentUsername}» هستید؟")
                    .setPositiveButton("خروج") { _, _ ->
                        authManager.logout()
                        setupUserProfileSection()
                        Toast.makeText(requireContext(), "از حساب خارج شدید.", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("انصراف", null)
                    .show()
                true
            }
        } else {
            binding.tvProfileUsername.text = "کاربر مهمان"
            binding.imgProfileAvatar.setImageResource(R.drawable.ic_account_circle)
            binding.imgProfileAvatar.setOnClickListener(null)
            binding.imgAvatarBadgeEdit.visibility = View.GONE
            binding.layoutProfileExtraActions.visibility = View.GONE
            binding.tvProfileSyncStatus.text = "جهت ذخیره و سینک لیست‌ها وارد شوید"
            binding.btnProfileAction.text = "ورود / ثبت‌نام"
            binding.btnProfileAction.setOnClickListener {
                showAuthDialog()
            }
            binding.cardUserProfile.setOnLongClickListener(null)
        }
    }

    private fun showAvatarPickerDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_select_avatar, null)
        val opt1 = dialogView.findViewById<View>(R.id.btn_pick_avatar_1)
        val opt2 = dialogView.findViewById<View>(R.id.btn_pick_avatar_2)
        val opt3 = dialogView.findViewById<View>(R.id.btn_pick_avatar_3)
        val opt4 = dialogView.findViewById<View>(R.id.btn_pick_avatar_4)
        val img1 = dialogView.findViewById<ImageView>(R.id.img_pick_avatar_1)
        val img2 = dialogView.findViewById<ImageView>(R.id.img_pick_avatar_2)
        val img3 = dialogView.findViewById<ImageView>(R.id.img_pick_avatar_3)
        val img4 = dialogView.findViewById<ImageView>(R.id.img_pick_avatar_4)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btn_confirm_avatar)

        var selected = authManager.userAvatarId
        val items = listOf(
            opt1 to (img1 to "m3_android"),
            opt2 to (img2 to "m3_popcorn"),
            opt3 to (img3 to "m3_cinema"),
            opt4 to (img4 to "m3_star")
        )

        fun updateSelection() {
            items.forEach { (_, pair) ->
                val (iv, id) = pair
                if (id == selected) {
                    iv.alpha = 1.0f
                    iv.scaleX = 1.15f
                    iv.scaleY = 1.15f
                } else {
                    iv.alpha = 0.5f
                    iv.scaleX = 0.92f
                    iv.scaleY = 0.92f
                }
            }
        }

        updateSelection()

        items.forEach { (view, pair) ->
            view.setOnClickListener {
                selected = pair.second
                updateSelection()
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                authManager.updateAvatar(selected)
                setupUserProfileSection()
                Toast.makeText(requireContext(), "آواتار شما با موفقیت تغییر کرد!", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }


    private fun showAuthDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_auth, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_auth_dialog_title)
        val layoutAvatarPicker = dialogView.findViewById<LinearLayout>(R.id.layout_avatar_picker)
        val imgAvatar1 = dialogView.findViewById<ImageView>(R.id.img_avatar_opt_1)
        val imgAvatar2 = dialogView.findViewById<ImageView>(R.id.img_avatar_opt_2)
        val imgAvatar3 = dialogView.findViewById<ImageView>(R.id.img_avatar_opt_3)
        val imgAvatar4 = dialogView.findViewById<ImageView>(R.id.img_avatar_opt_4)
        val etUser = dialogView.findViewById<TextInputEditText>(R.id.et_auth_username)
        val etPass = dialogView.findViewById<TextInputEditText>(R.id.et_auth_password)
        val btnSubmit = dialogView.findViewById<MaterialButton>(R.id.btn_auth_submit)
        val btnSwitch = dialogView.findViewById<MaterialButton>(R.id.btn_auth_switch_mode)

        var isRegisterMode = false
        var selectedAvatarId = authManager.userAvatarId

        val avatarViews = listOf(
            imgAvatar1 to "m3_android",
            imgAvatar2 to "m3_popcorn",
            imgAvatar3 to "m3_cinema",
            imgAvatar4 to "m3_star"
        )

        fun updateAvatarSelection() {
            avatarViews.forEach { (iv, id) ->
                if (id == selectedAvatarId) {
                    iv.alpha = 1.0f
                    iv.scaleX = 1.15f
                    iv.scaleY = 1.15f
                } else {
                    iv.alpha = 0.5f
                    iv.scaleX = 0.95f
                    iv.scaleY = 0.95f
                }
            }
        }

        avatarViews.forEach { (iv, id) ->
            iv.setOnClickListener {
                selectedAvatarId = id
                authManager.userAvatarId = id
                updateAvatarSelection()
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        fun updateUI() {
            if (isRegisterMode) {
                tvTitle.text = "ثبت‌نام در بیسنور"
                layoutAvatarPicker.visibility = View.VISIBLE
                updateAvatarSelection()
                btnSubmit.text = "ایجاد حساب کاربری"
                btnSwitch.text = "حساب دارید؟ وارد شوید"
            } else {
                tvTitle.text = "ورود به حساب کاربری"
                layoutAvatarPicker.visibility = View.GONE
                btnSubmit.text = "ورود به حساب"
                btnSwitch.text = "حساب کاربری ندارید؟ ثبت‌نام"
            }
        }

        btnSwitch.setOnClickListener {
            isRegisterMode = !isRegisterMode
            updateUI()
        }

        btnSubmit.setOnClickListener {
            val username = etUser.text?.toString()?.trim() ?: ""
            val password = etPass.text?.toString()?.trim() ?: ""

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "لطفاً تمامی فیلدها را پر کنید.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                btnSubmit.isEnabled = false
                btnSubmit.text = "در حال ارتباط با سرور..."

                val (success, message) = if (isRegisterMode) {
                    authManager.register(username, password)
                } else {
                    authManager.login(username, password)
                }

                btnSubmit.isEnabled = true
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

                if (success) {
                    dialog.dismiss()
                    setupUserProfileSection()
                } else {
                    updateUI()
                }
            }
        }

        updateUI()
        dialog.show()
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
            if (player.appIcon != null) {
                imgIcon.setImageDrawable(player.appIcon)
                imgIcon.imageTintList = null
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
        val mins = historyManager.autoNextMinutes
        binding.tvAutoNextMinutesLabel.text = "$mins دقیقه مانده به پایان"
        val secs = historyManager.autoNextCountdownSeconds
        binding.tvAutoNextCountdownLabel.text = "$secs ثانیه"
    }

    private fun showAutoNextTimeDialog() {
        val options = arrayOf("۱ دقیقه مانده به پایان", "۲ دقیقه مانده به پایان", "۳ دقیقه مانده به پایان", "۵ دقیقه مانده به پایان")
        val values = intArrayOf(1, 2, 3, 5)
        val currentIndex = values.indexOf(historyManager.autoNextMinutes).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("زمان شروع پیشنهاد قسمت بعدی")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                historyManager.autoNextMinutes = values[which]
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
