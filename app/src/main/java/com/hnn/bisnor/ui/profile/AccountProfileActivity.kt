package com.hnn.bisnor.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hnn.bisnor.R
import com.hnn.bisnor.data.database.SmartTasteDatabase
import com.hnn.bisnor.data.remote.AuthManager
import com.hnn.bisnor.data.repository.RealMediaRepository
import com.hnn.bisnor.databinding.ActivityAccountProfileBinding
import com.hnn.bisnor.ui.detail.DetailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountProfileBinding
    private lateinit var authManager: AuthManager
    private lateinit var tasteDb: SmartTasteDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)
        tasteDb = SmartTasteDatabase(this)

        binding.toolbarProfile.setNavigationOnClickListener {
            finish()
        }

        setupProfileData()
        setupContentPreference()
        setupFavoriteGenres()
        loadTasteStats()
        loadMonthlyAnalytics()

        binding.btnSaveProfile.setOnClickListener {
            val newName = binding.etUsername.text?.toString()?.trim() ?: ""
            if (newName.isNotEmpty()) {
                authManager.updateUsername(newName)
                binding.tvProfileName.text = newName
                Toast.makeText(this, "اطلاعات با موفقیت ذخیره شد", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnChangeAvatarRound.setOnClickListener {
            showAvatarSelectionDialog()
        }
        binding.imgProfileAvatarLarge.setOnClickListener {
            showAvatarSelectionDialog()
        }

        binding.btnResetTaste.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("پاکسازی تاریخچه سلیقه")
                .setMessage("آیا می‌خواهید تمام داده‌های یادگیری شده از تماشای فیلم‌ها پاک شوند؟")
                .setPositiveButton("بله، پاک شود") { _, _ ->
                    tasteDb.resetLearnedTaste()
                    loadTasteStats()
                    loadMonthlyAnalytics()
                    Toast.makeText(this, "تاریخچه سلیقه پاکسازی شد", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        binding.btnProfileLogoutFull.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("خروج از حساب")
                .setMessage("آیا مطمئن هستید می‌خواهید از حساب کاربری خود خارج شوید؟")
                .setPositiveButton("خروج") { _, _ ->
                    authManager.logout()
                    Toast.makeText(this, "از حساب کاربری خارج شدید", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }
    }

    private fun setupProfileData() {
        val username = authManager.currentUsername.ifEmpty { "کاربر بیسنور" }
        binding.tvProfileName.text = username
        binding.etUsername.setText(username)

        val avatarUrl = authManager.userAvatarUrl
        if (avatarUrl.isNotEmpty()) {
            binding.imgProfileAvatarLarge.load(avatarUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_account_circle)
                error(R.drawable.ic_account_circle)
            }
        } else {
            binding.imgProfileAvatarLarge.setImageResource(AuthManager.getAvatarDrawable(authManager.userAvatarId))
        }
    }

    private fun setupFavoriteGenres() {
        val savedFavorites = tasteDb.getFavoriteGenres()
        binding.chipGroupGenres.removeAllViews()

        for (genre in SmartTasteDatabase.DEFAULT_GENRES) {
            val chip = Chip(this).apply {
                text = genre
                isCheckable = true
                isChecked = savedFavorites.contains(genre)
                setOnCheckedChangeListener { _, isChecked ->
                    tasteDb.setFavoriteGenre(genre, isChecked)
                }
            }
            binding.chipGroupGenres.addView(chip)
        }
    }

    private fun loadTasteStats() {
        binding.layoutGenreStats.removeAllViews()
        val stats = tasteDb.getGenreStats()

        if (stats.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "هنوز ویدیویی تماشا نکرده‌اید. با تماشای فیلم‌ها و سریال‌ها، هوش سلیقه‌سنج بیسنور به‌طور خودکار علاقه‌مندی شما را تحلیل و درصدبندی می‌کند."
                setTextColor(getColor(R.color.on_surface_variant))
                textSize = 12f
                setPadding(0, 16, 0, 16)
                gravity = android.view.Gravity.CENTER
            }
            binding.layoutGenreStats.addView(emptyTv)
            return
        }

        for (stat in stats) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val titleTv = TextView(this).apply {
                text = stat.genreName
                textSize = 13f
                setTextColor(getColor(R.color.on_surface))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val pctTv = TextView(this).apply {
                text = "${stat.percentage}٪"
                textSize = 12f
                setTextColor(getColor(R.color.primary))
            }

            header.addView(titleTv)
            header.addView(pctTv)

            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = stat.percentage
                progressDrawable = getDrawable(R.drawable.bg_capsule_progress)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (10 * resources.displayMetrics.density).toInt()
                ).apply {
                    topMargin = (4 * resources.displayMetrics.density).toInt()
                }
            }

            row.addView(header)
            row.addView(pb)
            binding.layoutGenreStats.addView(row)
        }
    }

    private fun setupContentPreference() {
        val currentPref = tasteDb.getContentTypePreference()
        when (currentPref) {
            "movie" -> binding.toggleGroupContentPref.check(R.id.btn_pref_movies)
            "series" -> binding.toggleGroupContentPref.check(R.id.btn_pref_series)
            else -> binding.toggleGroupContentPref.check(R.id.btn_pref_both)
        }

        binding.toggleGroupContentPref.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newPref = when (checkedId) {
                    R.id.btn_pref_movies -> "movie"
                    R.id.btn_pref_series -> "series"
                    else -> "all"
                }
                tasteDb.setContentTypePreference(newPref)
                val msg = when (newPref) {
                    "movie" -> "ترجیح شما روی بیشتر فیلم تنظیم شد 🎬"
                    "series" -> "ترجیح شما روی بیشتر سریال تنظیم شد 📺"
                    else -> "ترجیح شما روی هر دو (متعادل) تنظیم شد 🍿"
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadMonthlyAnalytics() {
        val monthly = tasteDb.getMonthlyStats()

        // Movie Duration
        val mMin = monthly.movieDurationMs / 1000 / 60
        val mH = mMin / 60
        val mR = mMin % 60
        binding.tvMonthlyMovieHours.text = if (mH > 0) "$mH ساعت و $mR دقیقه" else if (mR > 0) "$mR دقیقه" else "۰ دقیقه"

        // Series Duration
        val sMin = monthly.seriesDurationMs / 1000 / 60
        val sH = sMin / 60
        val sR = sMin % 60
        binding.tvMonthlySeriesHours.text = if (sH > 0) "$sH ساعت و $sR دقیقه" else if (sR > 0) "$sR دقیقه" else "۰ دقیقه"

        // Data Usage
        val bytes = monthly.totalDataUsageBytes
        binding.tvMonthlyDataUsage.text = formatDataUsage(bytes)

        // Visual Distribution Bar & Monthly List
        binding.layoutGenreDistributionBar.removeAllViews()
        binding.layoutMonthlyGenreList.removeAllViews()

        val palette = listOf(
            "#FFB86B", "#80D5DB", "#FFB4AB", "#9DF1F7",
            "#FFD54F", "#B388FF", "#69F0AE", "#FF8A80", "#80CBC4"
        )

        if (monthly.genreStats.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "هنوز تماشایی در ۳۰ روز اخیر ثبت نشده است. با دیدن فیلم‌ها و سریال‌ها، نمودار تفکیکی در این بخش نمایش می‌یابد."
                setTextColor(getColor(R.color.on_surface_variant))
                textSize = 11.5f
                setPadding(0, 12, 0, 12)
                gravity = android.view.Gravity.CENTER
            }
            binding.layoutMonthlyGenreList.addView(emptyTv)
            return
        }

        monthly.genreStats.forEachIndexed { index, stat ->
            val colorHex = palette[index % palette.size]
            val colorInt = android.graphics.Color.parseColor(colorHex)

            // Segment in distribution bar
            val seg = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, stat.percentage.toFloat())
                setBackgroundColor(colorInt)
            }
            binding.layoutGenreDistributionBar.addView(seg)

            // Row in monthly list
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 6, 0, 6)
            }

            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (10 * resources.displayMetrics.density).toInt(),
                    (10 * resources.displayMetrics.density).toInt()
                ).apply {
                    marginEnd = (8 * resources.displayMetrics.density).toInt()
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(colorInt)
                }
            }

            val titleTv = TextView(this).apply {
                text = stat.genreName
                textSize = 12.5f
                setTextColor(getColor(R.color.on_surface))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val pctTv = TextView(this).apply {
                text = "${stat.percentage}٪"
                textSize = 12f
                setTextColor(getColor(R.color.on_surface_variant))
            }

            row.addView(dot)
            row.addView(titleTv)
            row.addView(pctTv)
            binding.layoutMonthlyGenreList.addView(row)
        }
    }

    private fun formatDataUsage(bytes: Long): String {
        if (bytes <= 0L) return "۰ مگابایت"
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) {
            String.format(java.util.Locale.US, "%.1f گیگابایت", gb)
        } else {
            val mb = (bytes / (1024L * 1024L)).coerceAtLeast(1L)
            "$mb مگابایت"
        }
    }

    private fun showAvatarSelectionDialog() {
        com.hnn.bisnor.util.AvatarPickerDialogHelper.show(this, authManager) { avatar ->
            binding.imgProfileAvatarLarge.setImageResource(avatar.drawableRes)
            Toast.makeText(this, "تصویر پروفایل به «${avatar.name}» تغییر یافت", Toast.LENGTH_SHORT).show()
        }
    }
}
