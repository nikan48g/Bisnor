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
        setupFavoriteGenres()
        loadTasteStats()
        setupWhatToWatch()

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

        binding.btnResetTaste.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("پاکسازی تاریخچه سلیقه")
                .setMessage("آیا می‌خواهید تمام داده‌های یادگیری شده از تماشای فیلم‌ها پاک شوند؟")
                .setPositiveButton("بله، پاک شود") { _, _ ->
                    tasteDb.resetLearnedTaste()
                    loadTasteStats()
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

    private fun setupWhatToWatch() {
        val clickListener = View.OnClickListener {
            val topGenres = tasteDb.getTopRecommendedGenres()
            val genreDesc = topGenres.take(3).joinToString(" و ")

            Toast.makeText(this, "در حال یافتن بهترین پیشنهاد بر اساس ژانر «$genreDesc»...", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch(Dispatchers.IO) {
                val movies = RealMediaRepository.getLatestMovies(0)
                val series = RealMediaRepository.getPopularSeries(0)
                val mediaList = (movies + series).distinctBy { it.id }
                val candidate = mediaList.shuffled().firstOrNull { m ->
                    m.genres.any { g -> topGenres.any { tg -> g.title.contains(tg) } }
                } ?: mediaList.randomOrNull()

                withContext(Dispatchers.Main) {
                    if (candidate != null) {
                        MaterialAlertDialogBuilder(this@AccountProfileActivity)
                            .setTitle("🎲 پیشنهاد امشب بیسنور")
                            .setMessage("پیشنهاد بر اساس علاقه شما به ژانر $genreDesc:\n\n🎬 «${candidate.title}»\nسال ساخت: ${candidate.year}\nامتیاز: ${candidate.imdb}")
                            .setPositiveButton("مشاهده و تماشا") { _, _ ->
                                val intent = Intent(this@AccountProfileActivity, DetailActivity::class.java).apply {
                                    putExtra("real_media", candidate as java.io.Serializable)
                                }
                                startActivity(intent)
                            }
                            .setNegativeButton("پیشنهاد بعدی", null)
                            .show()
                    } else {
                        Toast.makeText(this@AccountProfileActivity, "موردی یافت نشد", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.cardWhatToWatch.setOnClickListener(clickListener)
        binding.btnWhatToWatch.setOnClickListener(clickListener)
    }

    private fun showAvatarSelectionDialog() {
        val avatars = listOf(
            "https://api.dicebear.com/7.x/bottts/png?seed=Bisnor1",
            "https://api.dicebear.com/7.x/bottts/png?seed=Bisnor2",
            "https://api.dicebear.com/7.x/bottts/png?seed=Bisnor3",
            "https://api.dicebear.com/7.x/bottts/png?seed=Bisnor4",
            "https://api.dicebear.com/7.x/bottts/png?seed=Bisnor5"
        )
        val items = arrayOf("آواتار ۱ 🤖", "آواتار ۲ 🦁", "آواتار ۳ 🚀", "آواتار ۴ 💎", "آواتار ۵ 🎭")

        MaterialAlertDialogBuilder(this)
            .setTitle("انتخاب تصویر پروفایل")
            .setItems(items) { _, which ->
                val chosen = avatars[which]
                authManager.updateAvatarUrl(chosen)
                binding.imgProfileAvatarLarge.load(chosen) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                }
                Toast.makeText(this, "تصویر پروفایل بروز شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
}
