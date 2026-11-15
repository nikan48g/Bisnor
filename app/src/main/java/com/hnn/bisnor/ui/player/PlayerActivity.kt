package com.hnn.bisnor.ui.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.DefaultTimeBar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hnn.bisnor.R
import com.hnn.bisnor.data.model.RealSource
import com.hnn.bisnor.data.repository.PlaybackHistoryManager
import com.hnn.bisnor.data.repository.RealMediaRepository
import com.hnn.bisnor.databinding.ActivityPlayerBinding
import com.hnn.bisnor.databinding.DialogPlayerSettingsBinding
import kotlinx.coroutines.launch
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var historyManager: PlaybackHistoryManager
    private lateinit var audioManager: AudioManager

    private var videoUrl: String = ""
    private var mediaId: Int = 0
    private var mediaTitle: String = ""
    private var mediaCover: String = ""
    private var episodeTitle: String = ""
    private var episodeIndex: Int = 0
    private var startPositionMs: Long = 0L
    private var mediaType: String = "movie"
    private var mediaGenres: ArrayList<String> = arrayListOf()
    private var lastRecordedPosMs: Long = 0L
    private var currentSpeedIndex = 1 // 1.0x
    private var selectedTextLabel: String = "خاموش"
    private var selectedAudioLabel: String = ""
    private var textDisabled: Boolean = true
    private var sourceList: ArrayList<RealSource> = arrayListOf()
    private var currentSource: RealSource? = null
    private val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private val speedLabels = listOf("0.75x", "1.0x", "1.25x", "1.5x", "2.0x")

    // Aspect Ratio Modes (Fit, Zoom/Crop, Fill/Stretch)
    private var currentAspectIndex = 0
    private val aspectModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    )
    private val aspectLabels = listOf("Fit (تناسب)", "Zoom (تمام‌صفحه)", "Stretch (کشیده)")

    // Screen Touch Lock
    private var isScreenLocked = false

    // Modular Controllers
    private lateinit var gestureController: PlayerGestureController
    private lateinit var nextEpisodeManager: NextEpisodeManager

    private val handler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable {
        if (!isScreenLocked) {
            binding.topPlayerBar.animate().alpha(0f).setDuration(300).withEndAction {
                binding.topPlayerBar.visibility = View.GONE
            }.start()
        }
        hideSystemBars()
    }

    private val hideIndicatorRunnable = Runnable {
        binding.cardGestureIndicator.animate().alpha(0f).setDuration(200).withEndAction {
            binding.cardGestureIndicator.visibility = View.GONE
            binding.cardGestureIndicator.alpha = 1f
        }.start()
    }

    private val progressSaveRunnable = object : Runnable {
        override fun run() {
            saveCurrentPlaybackProgress()
            exoPlayer?.let { player ->
                nextEpisodeManager.checkTrigger(player.currentPosition, player.duration, isScreenLocked)
            }
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideSystemBars()

        historyManager = PlaybackHistoryManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val fullTitle = intent.getStringExtra("video_title") ?: "بیسنور سینما"
        videoUrl = intent.getStringExtra("video_url") ?: ""
        mediaId = intent.getIntExtra("media_id", 0)
        mediaTitle = intent.getStringExtra("media_title") ?: fullTitle
        mediaCover = intent.getStringExtra("media_cover") ?: ""
        episodeTitle = intent.getStringExtra("episode_title") ?: ""
        episodeIndex = intent.getIntExtra("episode_index", 0)
        startPositionMs = intent.getLongExtra("start_position", 0L)
        mediaType = intent.getStringExtra("media_type") ?: (if (episodeTitle.isNotEmpty() || episodeIndex > 0) "series" else "movie")
        mediaGenres = intent.getStringArrayListExtra("media_genres") ?: arrayListOf()
        @Suppress("DEPRECATION")
        val incomingSources = intent.getSerializableExtra("video_sources") as? ArrayList<RealSource>
        sourceList = ArrayList((incomingSources ?: arrayListOf()).sortedByDescending { qualityRank(it) })
        currentSource = sourceList.firstOrNull { it.url == videoUrl }

        if (startPositionMs <= 0L && videoUrl.isNotEmpty()) {
            val saved = historyManager.getProgressByUrl(videoUrl)
            if (saved != null && !saved.isFinished) {
                startPositionMs = saved.positionMs
            }
        }

        binding.tvPlayerTitle.text = fullTitle
        binding.btnPlayerBack.setOnClickListener { finish() }

        binding.playerView.setControllerVisibilityListener(androidx.media3.ui.PlayerView.ControllerVisibilityListener { visibility ->
            if (isScreenLocked) {
                binding.playerView.hideController()
                return@ControllerVisibilityListener
            }
            if (visibility == View.VISIBLE) {
                binding.topPlayerBar.visibility = View.VISIBLE
                binding.topPlayerBar.animate().alpha(1f).setDuration(200).start()
                handler.removeCallbacks(hideControlsRunnable)
                handler.postDelayed(hideControlsRunnable, 4000)
            } else {
                handler.removeCallbacks(hideControlsRunnable)
                binding.topPlayerBar.animate().alpha(0f).setDuration(200).withEndAction {
                    binding.topPlayerBar.visibility = View.GONE
                }.start()
                hideSystemBars()
            }
        })

        // Unified playback settings (subtitles, audio, speed, aspect, external player)
        binding.btnLockScreen.setOnClickListener { lockScreen() }
        binding.cardUnlockScreen.setOnClickListener { unlockScreen() }
        binding.btnPlayerSettings.setOnClickListener { showPlaybackSettings() }
        binding.btnPlayerQuality.setOnClickListener { chooseQuality() }
        updateQualityButton()

        binding.btnPlayNextNow.setOnClickListener {
            nextEpisodeManager.playNextNow()
        }

        binding.btnCloseNextCard.setOnClickListener {
            nextEpisodeManager.dismissCard()
        }

        gestureController = PlayerGestureController(
            activity = this,
            audioManager = audioManager,
            playerView = binding.playerView,
            onDoubleTapSeek = { forward ->
                exoPlayer?.let {
                    val delta = if (forward) 10000L else -10000L
                    val newPos = (it.currentPosition + delta).coerceIn(0L, it.duration)
                    it.seekTo(newPos)
                    showIndicator(if (forward) "⏩ ۱۰+ ثانیه" else "⏪ ۱۰- ثانیه", "")
                }
            },
            onIndicatorUpdate = { title, subtitle ->
                showIndicator(title, subtitle)
            },
            isScreenLocked = { isScreenLocked }
        )
        gestureController.attach()

        nextEpisodeManager = NextEpisodeManager(
            context = this,
            binding = binding,
            historyManager = historyManager,
            scope = lifecycleScope,
            mediaId = mediaId,
            mediaTitle = mediaTitle,
            mediaCover = mediaCover,
            currentVideoUrl = videoUrl,
            currentEpisodeTitle = episodeTitle,
            currentEpisodeIndex = episodeIndex,
            onSaveProgress = { saveCurrentPlaybackProgress() }
        )
        nextEpisodeManager.preload()

        styleSeekBar()
        initializePlayer(videoUrl)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun showIndicator(title: String, subtitle: String) {
        handler.removeCallbacks(hideIndicatorRunnable)
        binding.tvIndicatorIcon.text = title
        binding.tvIndicatorText.text = subtitle
        binding.cardGestureIndicator.visibility = View.VISIBLE
        binding.cardGestureIndicator.alpha = 1f
        handler.postDelayed(hideIndicatorRunnable, 1200)
    }

    private fun enterPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Toast.makeText(this, "تصویر در تصویر در این دستگاه پشتیبانی نمی‌شود", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (exoPlayer?.isPlaying == true) {
            enterPictureInPicture()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            binding.topPlayerBar.visibility = View.GONE
            binding.cardNextEpisode.visibility = View.GONE
            binding.cardUnlockScreen.visibility = View.GONE
            binding.playerView.useController = false
        } else {
            binding.playerView.useController = !isScreenLocked
            hideSystemBars()
        }
    }

    private fun showTrackSelectionDialog() {
        val tracks = exoPlayer?.currentTracks ?: return
        val audioTracks = mutableListOf<Pair<Int, String>>()
        val subTracks = mutableListOf<Pair<Int, String>>()

        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val lang = format.language ?: "صوت ${audioTracks.size + 1}"
                    audioTracks.add(Pair(i, "صدا: $lang (${format.label ?: "پیش‌فرض"})"))
                }
            } else if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val lang = format.language ?: "زیرنویس ${subTracks.size + 1}"
                    subTracks.add(Pair(i, "زیرنویس: $lang (${format.label ?: "فارسی"})"))
                }
            }
        }

        val allItems = arrayOf("غیرفعال‌سازی زیرنویس") + audioTracks.map { it.second } + subTracks.map { it.second }

        MaterialAlertDialogBuilder(this)
            .setTitle("انتخاب صوت و زیرنویس")
            .setItems(allItems) { _, which ->
                if (which == 0) {
                    trackSelector.setParameters(trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true))
                    Toast.makeText(this, "زیرنویس غیرفعال شد", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "لاین ${allItems[which]} انتخاب شد", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
    // ---------------- Unified playback settings (presentation only) ----------------

    private fun styleSeekBar() {
        binding.playerView.post {
            val timeBar = binding.playerView.findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress) ?: return@post
            // Media3 exposes colour and key-step setters publicly; thickness is increased
            // visually without replacing the controller or touching seeking logic.
            timeBar.setPlayedColor(getColor(R.color.primary))
            timeBar.setScrubberColor(getColor(R.color.primary))
            timeBar.setBufferedColor(getColor(R.color.on_surface_variant))
            timeBar.setUnplayedColor(getColor(R.color.surface_container_highest))
            timeBar.setKeyTimeIncrement(5000L)
            timeBar.scaleY = 1.45f
            timeBar.scaleX = 1.02f
        }
    }

    private fun showPlaybackSettings() {
        val sheet = BottomSheetDialog(this)
        val sheetBinding = DialogPlayerSettingsBinding.inflate(layoutInflater)
        sheet.setContentView(sheetBinding.root)
        sheet.behavior.apply {
            skipCollapsed = true
            isFitToContents = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }

        sheetBinding.tvSheetStreamState.text = buildString {
            append(mediaTitle)
            if (episodeTitle.isNotBlank()) append(" • ").append(episodeTitle)
        }
        sheetBinding.tvSheetSubtitleValue.text = selectedTextLabel
        sheetBinding.tvSheetAspectValue.text = aspectLabels[currentAspectIndex]
        sheetBinding.tvSheetAudioValue.text =
            selectedAudioLabel.ifBlank { describeDefaultAudio() }

        val speedChipId = when (currentSpeedIndex) {
            0 -> R.id.chip_speed_075
            2 -> R.id.chip_speed_125
            3 -> R.id.chip_speed_15
            4 -> R.id.chip_speed_2
            else -> R.id.chip_speed_1
        }
        sheetBinding.chipGroupSpeed.check(speedChipId)
        sheetBinding.chipGroupSpeed.setOnCheckedStateChangeListener { _, checkedIds ->
            currentSpeedIndex = when (checkedIds.firstOrNull()) {
                R.id.chip_speed_075 -> 0
                R.id.chip_speed_125 -> 2
                R.id.chip_speed_15 -> 3
                R.id.chip_speed_2 -> 4
                else -> 1
            }
            val speed = speeds[currentSpeedIndex]
            exoPlayer?.setPlaybackSpeed(speed)
            showIndicator("سرعت پخش", speedLabels[currentSpeedIndex])
        }

        sheetBinding.btnSheetSubtitles.setOnClickListener {
            sheet.dismiss()
            chooseTextTrack()
        }
        sheetBinding.tvSheetSubtitleStyleValue.text = subtitleStyleSummary()
        sheetBinding.btnSheetSubtitleStyle.setOnClickListener {
            sheet.dismiss()
            showSubtitleStyle()
        }
        sheetBinding.btnSheetSubtitleOff.setOnClickListener {
            disableSubtitles()
            Toast.makeText(this, "زیرنویس خاموش شد", Toast.LENGTH_SHORT).show()
            sheet.dismiss()
        }
        sheetBinding.btnSheetAudio.setOnClickListener {
            sheet.dismiss()
            chooseAudioTrack()
        }
        sheetBinding.btnSheetAspect.setOnClickListener {
            currentAspectIndex = (currentAspectIndex + 1) % aspectModes.size
            binding.playerView.resizeMode = aspectModes[currentAspectIndex]
            sheetBinding.tvSheetAspectValue.text = aspectLabels[currentAspectIndex]
            showIndicator("نسبت تصویر", aspectLabels[currentAspectIndex])
        }
        sheetBinding.btnSheetLock.setOnClickListener {
            sheet.dismiss()
            lockScreen()
        }
        sheetBinding.btnSheetExternal.setOnClickListener {
            sheet.dismiss()
            openWithExternalPlayer()
        }

        sheet.show()
    }

    private fun chooseQuality() {
        if (sourceList.size < 2) {
            Toast.makeText(this, "برای این مورد یک نسخه موجود است", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = sourceList.map { "${qualityLabel(it)} • ${providerLabel(it)}" }.toTypedArray()
        val checked = sourceList.indexOfFirst { it.url == (currentSource?.url ?: videoUrl) }
        MaterialAlertDialogBuilder(this)
            .setTitle("انتخاب کیفیت")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                switchSource(sourceList[which])
            }
            .show()
    }

    private fun switchSource(source: RealSource) {
        if (source.url == videoUrl) return
        val resumeAt = exoPlayer?.currentPosition ?: startPositionMs
        saveCurrentPlaybackProgress()
        currentSource = source
        videoUrl = source.url
        startPositionMs = resumeAt
        selectedTextLabel = "خاموش"
        textDisabled = true
        initializePlayer(source.url)
        updateQualityButton()
        showIndicator("کیفیت", "${qualityLabel(source)} • ${providerLabel(source)}")
    }

    /** Short quality label next to the settings button; hidden for single-version items. */
    private fun updateQualityButton() {
        val button = binding.btnPlayerQuality
        if (sourceList.size < 2) {
            button.visibility = View.GONE
            return
        }
        button.visibility = View.VISIBLE
        val active = currentSource ?: sourceList.firstOrNull { it.url == videoUrl }
        button.text = active?.let { qualityLabel(it) } ?: "کیفیت"
    }

    private fun qualityLabel(source: RealSource): String {
        val match = Regex("(2160|1440|1080|720|540|480|360|240)").find(source.quality)
        return match?.let { "${it.value}p" } ?: source.quality.ifBlank { "کیفیت اصلی" }
    }

    private fun qualityRank(source: RealSource): Int =
        Regex("(2160|1440|1080|720|540|480|360|240)").find(source.quality)?.value?.toIntOrNull() ?: 0

    private fun providerLabel(source: RealSource): String =
        if (source.origin == "next") "نکست‌مووی" else "ایران‌فلیکس"

    private fun applySubtitleStyle() {
        val prefs = getSharedPreferences("bisnor_subtitles", MODE_PRIVATE)
        val size = prefs.getInt("size", 22)
        val textColor = prefs.getInt("color", Color.WHITE)
        val background = prefs.getInt("bg", Color.argb(150, 0, 0, 0))
        binding.playerView.subtitleView?.apply {
            setStyle(
                CaptionStyleCompat(
                    textColor,
                    Color.TRANSPARENT,
                    background,
                    CaptionStyleCompat.EDGE_TYPE_NONE,
                    Color.BLACK,
                    null
                )
            )
            setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
        }
    }

    private fun subtitleStyleSummary(): String {
        val prefs = getSharedPreferences("bisnor_subtitles", MODE_PRIVATE)
        val size = when (prefs.getInt("size", 22)) {
            16 -> "کوچک"
            30 -> "بزرگ"
            else -> "متوسط"
        }
        val color = when (prefs.getInt("color", Color.WHITE)) {
            Color.YELLOW -> "زرد"
            0xFF7CFC00.toInt() -> "سبز"
            0xFF40E0D0.toInt() -> "فیروزه‌ای"
            else -> "سفید"
        }
        return "$size • $color"
    }

    private fun showSubtitleStyle() {
        val options = arrayOf("اندازه متن", "رنگ متن", "رنگ پس‌زمینه")
        MaterialAlertDialogBuilder(this)
            .setTitle("ظاهر زیرنویس")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickSubtitleSize()
                    1 -> pickSubtitleColor()
                    else -> pickSubtitleBackground()
                }
            }
            .show()
    }

    private fun applySubtitlePref(key: String, value: Int) {
        getSharedPreferences("bisnor_subtitles", MODE_PRIVATE).edit().putInt(key, value).apply()
        applySubtitleStyle()
    }

    private fun pickSubtitleSize() {
        val values = intArrayOf(16, 22, 30)
        val labels = arrayOf("کوچک", "متوسط", "بزرگ")
        val prefs = getSharedPreferences("bisnor_subtitles", MODE_PRIVATE)
        val current = values.indexOf(prefs.getInt("size", 22)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("اندازه زیرنویس")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                applySubtitlePref("size", values[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun pickSubtitleColor() {
        val values = intArrayOf(Color.WHITE, Color.YELLOW, 0xFF7CFC00.toInt(), 0xFF40E0D0.toInt())
        val labels = arrayOf("سفید", "زرد", "سبز روشن", "فیروزه‌ای")
        val prefs = getSharedPreferences("bisnor_subtitles", MODE_PRIVATE)
        val current = values.indexOf(prefs.getInt("color", Color.WHITE)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("رنگ متن زیرنویس")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                applySubtitlePref("color", values[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun pickSubtitleBackground() {
        val values = intArrayOf(Color.TRANSPARENT, Color.argb(150, 0, 0, 0), Color.BLACK)
        val labels = arrayOf("بدون پس‌زمینه", "نیمه‌شفاف", "مشکی کامل")
        val prefs = getSharedPreferences("bisnor_subtitles", MODE_PRIVATE)
        val current = values.indexOf(prefs.getInt("bg", Color.argb(150, 0, 0, 0))).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("پس‌زمینه زیرنویس")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                applySubtitlePref("bg", values[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun chooseTextTrack() {
        val groups = exoPlayer?.currentTracks?.groups
            ?.filter { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
            .orEmpty()
        if (groups.isEmpty()) {
            Toast.makeText(this, "این ویدیو زیرنویس قابل انتخاب ندارد", Toast.LENGTH_SHORT).show()
            return
        }

        val options = mutableListOf<String>()
        groups.forEach { group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                options.add(
                    format.label?.takeIf { it.isNotBlank() }
                        ?: format.language?.takeIf { it.isNotBlank() }
                        ?: "زیرنویس ${options.size + 1}"
                )
            }
        }

        val items = arrayOf("خاموش") + options.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("زیرنویس")
            .setItems(items) { _, which ->
                if (which == 0) {
                    disableSubtitles()
                } else {
                    var flat = which - 1
                    for (group in groups) {
                        if (flat < group.length) {
                            selectTextGroup(group, flat)
                            selectedTextLabel = options[which - 1]
                            textDisabled = false
                            Toast.makeText(this, "زیرنویس: ${options[which - 1]}", Toast.LENGTH_SHORT).show()
                            break
                        }
                        flat -= group.length
                    }
                }
            }
            .show()
    }

    private fun chooseAudioTrack() {
        val groups = exoPlayer?.currentTracks?.groups
            ?.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
            .orEmpty()
        if (groups.isEmpty()) {
            Toast.makeText(this, "ترک صوتی قابل انتخابی وجود ندارد", Toast.LENGTH_SHORT).show()
            return
        }

        val options = mutableListOf<String>()
        groups.forEach { group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                options.add(
                    format.label?.takeIf { it.isNotBlank() }
                        ?: format.language?.takeIf { it.isNotBlank() }
                        ?: "صوت ${options.size + 1}"
                )
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("ترک صوتی")
            .setSingleChoiceItems(options.toTypedArray(), -1) { dialog, which ->
                var flat = which
                for (group in groups) {
                    if (flat < group.length) {
                        selectAudioGroup(group, flat)
                        selectedAudioLabel = options[which]
                        Toast.makeText(this, "صدا: ${options[which]}", Toast.LENGTH_SHORT).show()
                        break
                    }
                    flat -= group.length
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun selectTextGroup(group: Tracks.Group, trackIndex: Int) {
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
        )
    }

    private fun selectAudioGroup(group: Tracks.Group, trackIndex: Int) {
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
        )
    }

    private fun disableSubtitles() {
        trackSelector.setParameters(
            trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        )
        selectedTextLabel = "خاموش"
        textDisabled = true
    }

    private fun describeDefaultAudio(): String {
        val group = exoPlayer?.currentTracks?.groups
            ?.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 } ?: return ""
        val format = group.getTrackFormat(0)
        return format.label ?: format.language ?: ""
    }

    private fun lockScreen() {
        isScreenLocked = true
        binding.playerView.useController = false
        binding.topPlayerBar.visibility = View.GONE
        binding.cardUnlockScreen.visibility = View.VISIBLE
        showIndicator("قفل لمس صفحه", "برای باز کردن دکمه بالا را لمس کنید")
    }

    private fun unlockScreen() {
        isScreenLocked = false
        binding.playerView.useController = true
        binding.cardUnlockScreen.visibility = View.GONE
        binding.topPlayerBar.visibility = View.VISIBLE
        showIndicator("قفل صفحه باز شد", "")
        handler.postDelayed(hideControlsRunnable, 4000)
    }

    private fun openWithExternalPlayer() {
        try {
            val cleanUrl = videoUrl.trim().replace(" ", "%20")
            if (cleanUrl.isEmpty()) {
                Toast.makeText(this, "آدرس ویدیو یافت نشد", Toast.LENGTH_SHORT).show()
                return
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(cleanUrl), "video/*")
                putExtra("title", mediaTitle)
                putExtra(Intent.EXTRA_TITLE, mediaTitle)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, "پخش ویدیو با:"))
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در باز کردن پلیر خارجی: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyVideoUrl() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Video URL", videoUrl)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "لینک ویدیو در حافظه کپی شد", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در کپی لینک: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handlePlaybackError(error: PlaybackException) {
        val errorMsg = error.message ?: error.errorCodeName
        val isNetworkOrSource = error.errorCode in listOf(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
        ) || errorMsg.contains("Source", ignoreCase = true) || errorMsg.contains("Response code", ignoreCase = true)

        val detailText = if (isNetworkOrSource) {
            "سرور ویدیو پاسخ نداد یا اتصال اینترنت با خطا مواجه شد.\n\n" +
            "💡 نکته مهم:\n" +
            "اکثر سرورهای دانلود داخل ایران در صورت روشن بودن فیلترشکن (VPN) دسترسی آی‌پی‌های خارجی را مسدود می‌کنند.\n\n" +
            "راهکارهای سریع:\n" +
            "۱. فیلترشکن خود را موقتاً خاموش کنید و «تلاش مجدد» را بزنید.\n" +
            "۲. یا با دکمه «پخش با VLC / MX Player» ویدیو را در برنامه‌های جانبی باز کنید."
        } else {
            "خطا در رندر تصویر یا صدای ویدیو: $errorMsg\n\nپیشنهاد می‌شود ویدیو را با VLC یا MX Player باز کنید."
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("خطا در برقراری ارتباط با سرور ویدیو")
            .setMessage(detailText)
            .setPositiveButton("🚀 پخش با VLC / MX / پلیر جانبی") { _, _ ->
                openWithExternalPlayer()
                finish()
            }
            .setNeutralButton("🔄 تلاش مجدد") { _, _ ->
                initializePlayer(videoUrl)
            }
            .setNegativeButton("📋 کپی لینک") { _, _ ->
                copyVideoUrl()
            }
            .setCancelable(true)
            .show()
    }

    private fun initializePlayer(url: String) {
        if (url.isEmpty()) {
            Toast.makeText(this, "آدرس ویدیو یافت نشد", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            exoPlayer?.release()
            exoPlayer = null

            val cleanUrl = url.trim().replace(" ", "%20")

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000)
                .setKeepPostFor302Redirects(true)

            val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            trackSelector = DefaultTrackSelector(this)

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 15000,
                    /* maxBufferMs = */ 50000,
                    /* bufferForPlaybackMs = */ 500,
                    /* bufferForPlaybackAfterRebufferMs = */ 1000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            exoPlayer = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .build().apply {
                    binding.playerView.player = this
                    val mediaItem = MediaItem.fromUri(Uri.parse(cleanUrl))
                    setMediaItem(mediaItem)

                    if (startPositionMs > 0L) {
                        seekTo(startPositionMs)
                        val mins = (startPositionMs / 1000) / 60
                        val secs = (startPositionMs / 1000) % 60
                        val timeStr = String.format("%02d:%02d", mins, secs)
                        Toast.makeText(this@PlayerActivity, "ادامه پخش از دقیقه $timeStr", Toast.LENGTH_SHORT).show()
                    }

                    prepare()
                    playWhenReady = true

                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            handlePlaybackError(error)
                        }
                    })
                }

            handler.postDelayed(hideControlsRunnable, 4000)
            handler.postDelayed(progressSaveRunnable, 2000)
            binding.playerView.post { applySubtitleStyle() }

        } catch (e: Exception) {
            Toast.makeText(this, "خطا در پخش: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveCurrentPlaybackProgress() {
        exoPlayer?.let { player ->
            val pos = player.currentPosition
            val dur = player.duration
            if (dur > 0 && pos > 0 && videoUrl.isNotEmpty()) {
                historyManager.saveProgress(
                    url = videoUrl,
                    mediaId = mediaId,
                    mediaTitle = mediaTitle,
                    mediaCover = mediaCover,
                    episodeTitle = episodeTitle,
                    episodeIndex = episodeIndex,
                    positionMs = pos,
                    durationMs = dur
                )

                val deltaMs = pos - lastRecordedPosMs
                if (deltaMs in 2000L..60000L) {
                    val bytesEst = (deltaMs * 350L).coerceAtLeast(0L) // approx ~2.8 Mbps stream data
                    try {
                        val tasteDb = com.hnn.bisnor.data.database.SmartTasteDatabase(this)
                        tasteDb.recordDataUsage(bytesEst)
                        tasteDb.recordWatchEvent(
                            mediaId = mediaId,
                            title = mediaTitle,
                            mediaType = mediaType,
                            genres = mediaGenres,
                            durationMs = deltaMs,
                            bytesUsed = bytesEst,
                            isCompleted = (pos * 100 / dur) >= 90
                        )
                    } catch (_: Exception) {}
                    lastRecordedPosMs = pos
                } else if (lastRecordedPosMs == 0L && pos > 0) {
                    lastRecordedPosMs = pos
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveCurrentPlaybackProgress()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            // Keep playing in PiP
        } else {
            exoPlayer?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveCurrentPlaybackProgress()
        handler.removeCallbacks(hideControlsRunnable)
        handler.removeCallbacks(progressSaveRunnable)
        handler.removeCallbacks(hideIndicatorRunnable)
        if (::nextEpisodeManager.isInitialized) {
            nextEpisodeManager.destroy()
        }
        try {
            exoPlayer?.release()
        } catch (e: Exception) {
            // Ignore release exception
        }
        exoPlayer = null
    }
}

