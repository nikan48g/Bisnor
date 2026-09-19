package com.hnn.bisnor.ui.player

import android.app.Activity
import android.media.AudioManager
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.media3.ui.PlayerView
import kotlin.math.abs

/**
 * Encapsulates touch gesture detection for the video player:
 * - Double tap on left/right for 10s seek
 * - Vertical drag on left side for volume
 * - Vertical drag on right side for screen brightness
 */
class PlayerGestureController(
    private val activity: Activity,
    private val audioManager: AudioManager,
    private val playerView: PlayerView,
    private val onDoubleTapSeek: (forward: Boolean) -> Unit,
    private val onIndicatorUpdate: (title: String, subtitle: String) -> Unit,
    private val isScreenLocked: () -> Boolean
) {

    private val gestureDetector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (isScreenLocked()) return false
            val screenWidth = playerView.width
            if (e.x < screenWidth / 3) {
                onDoubleTapSeek(false) // 10s rewind
                return true
            } else if (e.x > (screenWidth * 2) / 3) {
                onDoubleTapSeek(true) // 10s forward
                return true
            }
            return false
        }
    })

    private var initialY = 0f
    private var isLeftSwipe = false
    private var maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    fun attach() {
        playerView.setOnTouchListener { _, event ->
            if (isScreenLocked()) return@setOnTouchListener false
            gestureDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.y
                    isLeftSwipe = event.x < (playerView.width / 2)
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
                            onIndicatorUpdate("🔊 بلندی صدا", "$pct%")
                        } else {
                            val lp = activity.window.attributes
                            var currentB = lp.screenBrightness
                            if (currentB < 0) currentB = 0.5f
                            val step = if (deltaY > 0) 0.05f else -0.05f
                            val newB = (currentB + step).coerceIn(0.01f, 1.0f)
                            lp.screenBrightness = newB
                            activity.window.attributes = lp
                            val pct = (newB * 100).toInt()
                            onIndicatorUpdate("☀️ روشنایی", "$pct%")
                        }
                        initialY = event.y
                    }
                }
            }
            false
        }
    }
}
