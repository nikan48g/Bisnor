package com.hnn.bisnor.ui.player

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import com.hnn.bisnor.data.model.RealEpisode
import com.hnn.bisnor.data.model.RealSource
import com.hnn.bisnor.data.repository.PlaybackHistoryManager
import com.hnn.bisnor.data.repository.RealMediaRepository
import com.hnn.bisnor.databinding.ActivityPlayerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages fetching, countdown and automatic playback trigger for next series episodes.
 */
class NextEpisodeManager(
    private val context: Context,
    private val binding: ActivityPlayerBinding,
    private val historyManager: PlaybackHistoryManager,
    private val scope: CoroutineScope,
    private val mediaId: Int,
    private val mediaTitle: String,
    private val mediaCover: String,
    private val currentVideoUrl: String,
    private val currentEpisodeTitle: String,
    private val currentEpisodeIndex: Int,
    private val onSaveProgress: () -> Unit
) {

    var nextEpisodeSource: RealSource? = null
        private set

    private var isNextCardShown = false
    private var isNextCardDismissed = false
    private var countdownSeconds = 10
    private val handler = Handler(Looper.getMainLooper())

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (countdownSeconds > 0) {
                binding.tvNextCountdown.text = "پخش خودکار تا $countdownSeconds ثانیه..."
                countdownSeconds--
                handler.postDelayed(this, 1000)
            } else {
                playNextNow()
            }
        }
    }

    fun preload() {
        if (mediaId == 0) return
        scope.launch {
            try {
                val seasons = RealMediaRepository.getSeriesSeasons(mediaId)
                val allEps = seasons.flatMap { it.episodes }
                if (allEps.isEmpty()) return@launch

                // 1. Match by URL
                var currentFoundIndex = allEps.indexOfFirst { ep -> ep.sources.any { it.url == currentVideoUrl } }

                // 2. Match by parsed episode number
                val currentEpNum = extractEpisodeNumber(currentEpisodeTitle) ?: extractEpisodeNumber(mediaTitle)
                if (currentFoundIndex == -1 && currentEpNum != null) {
                    currentFoundIndex = allEps.indexOfFirst { ep -> extractEpisodeNumber(ep.title) == currentEpNum }
                }

                // 3. Fallback to passed index
                if (currentFoundIndex == -1 && currentEpisodeIndex in allEps.indices) {
                    currentFoundIndex = currentEpisodeIndex
                }

                var nextEp: RealEpisode? = null
                if (currentEpNum != null) {
                    nextEp = allEps.find { ep -> extractEpisodeNumber(ep.title) == currentEpNum + 1 }
                }

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
            } catch (_: Exception) {}
        }
    }

    fun checkTrigger(currentPositionMs: Long, durationMs: Long, isScreenLocked: Boolean) {
        if (!historyManager.isAutoNextEnabled || isNextCardShown || isNextCardDismissed || isScreenLocked) return
        val nextSrc = nextEpisodeSource ?: return

        if (durationMs > 60000 && currentPositionMs > 0) {
            val triggerThresholdMs = historyManager.autoNextMinutes * 60 * 1000L
            val remainingMs = durationMs - currentPositionMs
            if (remainingMs in 1000..triggerThresholdMs) {
                isNextCardShown = true
                binding.tvNextEpisodeTitle.text = "قسمت بعدی: ${nextSrc.quality}"
                binding.cardNextEpisode.visibility = View.VISIBLE
                binding.cardNextEpisode.animate().alpha(1f).setDuration(300).start()
                countdownSeconds = historyManager.autoNextCountdownSeconds
                handler.post(countdownRunnable)
            }
        }
    }

    fun dismissCard() {
        isNextCardDismissed = true
        handler.removeCallbacks(countdownRunnable)
        binding.cardNextEpisode.animate().alpha(0f).setDuration(200).withEndAction {
            binding.cardNextEpisode.visibility = View.GONE
        }.start()
    }

    fun playNextNow() {
        val nextSrc = nextEpisodeSource ?: return
        handler.removeCallbacks(countdownRunnable)
        onSaveProgress()

        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("video_title", "$mediaTitle - ${nextSrc.quality}")
            putExtra("video_url", nextSrc.url)
            putExtra("media_id", mediaId)
            putExtra("media_title", mediaTitle)
            putExtra("media_cover", mediaCover)
            putExtra("episode_title", nextSrc.quality)
            putExtra("episode_index", currentEpisodeIndex + 1)
        }
        context.startActivity(intent)
        if (context is android.app.Activity) {
            context.finish()
        }
    }

    fun destroy() {
        handler.removeCallbacks(countdownRunnable)
    }

    companion object {
        fun extractEpisodeNumber(text: String): Int? {
            if (text.isEmpty()) return null
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
    }
}
