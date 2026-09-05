package com.hnn.bisnor.ui.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hnn.bisnor.data.model.RealSource
import com.hnn.bisnor.data.repository.PlaybackHistoryManager
import com.hnn.bisnor.data.repository.RealMediaRepository
import com.hnn.bisnor.databinding.ActivityPlayerBinding
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
    private var currentSpeedIndex = 1 // 1.0x
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

    // Next Episode logic
    private var nextEpisodeSource: RealSource? = null
    private var isNextCardShown = false
    private var isNextCardDismissed = false
    private var countdownSeconds = 10

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

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (countdownSeconds > 0) {
                binding.tvNextCountdown.text = "پخش خودکار تا $countdownSeconds ثانیه..."
                countdownSeconds--
                handler.postDelayed(this, 1000)
            } else {
                playNextEpisodeNow()
            }
        }
    }

    private val progressSaveRunnable = object : Runnable {
        override fun run() {
            saveCurrentPlaybackProgress()
            checkNextEpisodeTrigger()
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

        // Aspect Ratio
        binding.btnAspectRatio.setOnClickListener {
            currentAspectIndex = (currentAspectIndex + 1) % aspectModes.size
            binding.playerView.resizeMode = aspectModes[currentAspectIndex]
            val lbl = when (currentAspectIndex) {
                1 -> "Zoom"
                2 -> "Stretch"
                else -> "Fit"
            }
            binding.btnAspectRatio.text = lbl
            showIndicator("📐 نسبت تصویر", aspectLabels[currentAspectIndex])
        }

        // Lock Screen
        binding.btnLockScreen.setOnClickListener {
            isScreenLocked = true
            binding.playerView.useController = false
            binding.topPlayerBar.visibility = View.GONE
            binding.cardUnlockScreen.visibility = View.VISIBLE
            showIndicator("🔒 قفل لمس صفحه", "برای باز کردن، دکمه بالا را لمس کنید")
        }

        binding.cardUnlockScreen.setOnClickListener {
            isScreenLocked = false
            binding.playerView.useController = true
            binding.cardUnlockScreen.visibility = View.GONE
            binding.topPlayerBar.visibility = View.VISIBLE
            showIndicator("🔓 قفل صفحه باز شد", "")
            handler.postDelayed(hideControlsRunnable, 4000)
        }

        binding.btnSpeed.setOnClickListener {
            currentSpeedIndex = (currentSpeedIndex + 1) % speeds.size
            val spd = speeds[currentSpeedIndex]
            exoPlayer?.setPlaybackSpeed(spd)
            binding.btnSpeed.text = speedLabels[currentSpeedIndex]
            showIndicator("⚡ سرعت پخش", speedLabels[currentSpeedIndex])
        }

        binding.btnPip.setOnClickListener {
            enterPictureInPicture()
        }

        binding.btnTracks.setOnClickListener {
            showTrackSelectionDialog()
        }

        binding.btnPlayNextNow.setOnClickListener {
            playNextEpisodeNow()
        }

        binding.btnCloseNextCard.setOnClickListener {
            isNextCardDismissed = true
            handler.removeCallbacks(countdownRunnable)
            binding.cardNextEpisode.visibility = View.GONE
        }

        setupGestures()
        initializePlayer(videoUrl)
        preloadNextEpisode()
    }

    private fun extractEpisodeNumber(text: String): Int? {
        val normalized = text
            .replace('۰', '0').replace('۱', '1').replace('۲', '2').replace('۳', '3').replace('۴', '4')
            .replace('۵', '5').replace('۶', '6').replace('۷', '7').replace('۸', '8').replace('۹', '9')

        val regex = Regex("""(?:قسمت|ep|episode|e)\s*(\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(normalized)
        if (match != null) {
            return match.groupValues[1].toIntOrNull()
        }

        val allDigits = Regex("""\b(\d+)\b""").findAll(normalized).toList()
        return allDigits.lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun preloadNextEpisode() {
        if (mediaId == 0) return
        lifecycleScope.launch {
            try {
                val seasons = RealMediaRepository.getSeriesSeasons(mediaId)
                val allEps = seasons.flatMap { it.episodes }
                if (allEps.isEmpty()) return@launch

                // 1. Try finding current episode index by matching video URL
                var currentFoundIndex = allEps.indexOfFirst { ep -> ep.sources.any { it.url == videoUrl } }

                // 2. If not found by URL, try matching exact episode number from title (e.g. 1169 -> next is 1170)
                val currentEpNum = extractEpisodeNumber(episodeTitle) ?: extractEpisodeNumber(mediaTitle)
                if (currentFoundIndex == -1 && currentEpNum != null) {
                    currentFoundIndex = allEps.indexOfFirst { ep -> extractEpisodeNumber(ep.title) == currentEpNum }
                }

                // 3. Fallback to passed episodeIndex if within bounds
                if (currentFoundIndex == -1 && episodeIndex in allEps.indices) {
                    currentFoundIndex = episodeIndex
                }

                // Now locate the NEXT episode:
                var nextEp: com.hnn.bisnor.data.model.RealEpisode? = null

                // If we know current episode number, look directly for epNum + 1 first
                if (currentEpNum != null) {
                    nextEp = allEps.find { ep -> extractEpisodeNumber(ep.title) == currentEpNum + 1 }
                }

                // Otherwise, sequential index + 1
                if (nextEp == null && currentFoundIndex != -1 && currentFoundIndex + 1 < allEps.size) {
                    nextEp = allEps[currentFoundIndex + 1]
                }

                if (nextEp != null) {
                    val src = nextEp.sources.firstOrNull()
                    if (src != null) {
                        val displayTitle = if (nextEp.title.isNotBlank()) nextEp.title else "قسمت بعدی"
                        nextEpisodeSource = src.copy(quality = displayTitle)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun checkNextEpisodeTrigger() {
        if (!historyManager.isAutoNextEnabled || isNextCardShown || isNextCardDismissed || isScreenLocked) return
        val nextSrc = nextEpisodeSource ?: return

        exoPlayer?.let { player ->
            val pos = player.currentPosition
            val dur = player.duration
            if (dur > 60000 && pos > 0) {
                val triggerThresholdMs = historyManager.autoNextMinutes * 60 * 1000L
                val remainingMs = dur - pos
                if (remainingMs in 1000..triggerThresholdMs) {
                    isNextCardShown = true
                    binding.tvNextEpisodeTitle.text = "قسمت بعدی: ${nextSrc.quality}"
                    binding.cardNextEpisode.visibility = View.VISIBLE
                    binding.cardNextEpisode.animate().alpha(1f).setDuration(300).start()
                    countdownSeconds = 10
                    handler.post(countdownRunnable)
                }
            }
        }
    }

    private fun playNextEpisodeNow() {
        val nextSrc = nextEpisodeSource ?: return
        handler.removeCallbacks(countdownRunnable)
        saveCurrentPlaybackProgress()

        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("video_title", "$mediaTitle - ${nextSrc.quality}")
            putExtra("video_url", nextSrc.url)
            putExtra("media_id", mediaId)
            putExtra("media_title", mediaTitle)
            putExtra("media_cover", mediaCover)
            putExtra("episode_title", nextSrc.quality)
            putExtra("episode_index", episodeIndex + 1)
        }
        startActivity(intent)
        finish()
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

    private fun setupGestures() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isScreenLocked) return false
                val screenWidth = binding.playerView.width
                if (e.x < screenWidth / 3) {
                    exoPlayer?.let {
                        val newPos = (it.currentPosition - 10000).coerceAtLeast(0L)
                        it.seekTo(newPos)
                        showIndicator("⏪ ۱۰- ثانیه", "")
                    }
                    return true
                } else if (e.x > (screenWidth * 2) / 3) {
                    exoPlayer?.let {
                        val newPos = (it.currentPosition + 10000).coerceAtMost(it.duration)
                        it.seekTo(newPos)
                        showIndicator("⏩ ۱۰+ ثانیه", "")
                    }
                    return true
                }
                return false
            }
        })

        var initialY = 0f
        var isLeftSwipe = false
        var maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        binding.playerView.setOnTouchListener { _, event ->
            if (isScreenLocked) return@setOnTouchListener false
            gestureDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.y
                    isLeftSwipe = event.x < (binding.playerView.width / 2)
                    maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = initialY - event.y
                    if (abs(deltaY) > 40) {
                        if (isLeftSwipe) {
                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val step = if (deltaY > 0) 1 else -1
                            val newVol = (currentVol + step).coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            val pct = (newVol * 100) / maxVolume
                            showIndicator("🔊 بلندی صدا", "$pct%")
                        } else {
                            val lp = window.attributes
                            var currentB = lp.screenBrightness
                            if (currentB < 0) currentB = 0.5f
                            val step = if (deltaY > 0) 0.05f else -0.05f
                            val newB = (currentB + step).coerceIn(0.01f, 1.0f)
                            lp.screenBrightness = newB
                            window.attributes = lp
                            val pct = (newB * 100).toInt()
                            showIndicator("☀️ روشنایی", "$pct%")
                        }
                        initialY = event.y
                    }
                }
            }
            false
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

    private fun initializePlayer(url: String) {
        if (url.isEmpty()) {
            Toast.makeText(this, "آدرس ویدیو یافت نشد", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            val cleanUrl = url.trim().replace(" ", "%20")

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000)

            val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            trackSelector = DefaultTrackSelector(this)

            exoPlayer = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(mediaSourceFactory)
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
                            Toast.makeText(
                                this@PlayerActivity,
                                "خطا در برقراری ارتباط با سرور ویدیو: ${error.message ?: error.errorCodeName}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    })
                }

            handler.postDelayed(hideControlsRunnable, 4000)
            handler.postDelayed(progressSaveRunnable, 2000)

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
        handler.removeCallbacks(countdownRunnable)
        try {
            exoPlayer?.release()
        } catch (e: Exception) {
            // Ignore release exception
        }
        exoPlayer = null
    }
}
