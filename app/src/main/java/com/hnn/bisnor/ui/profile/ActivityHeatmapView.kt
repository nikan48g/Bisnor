package com.hnn.bisnor.ui.profile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.hnn.bisnor.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * GitHub-style watching heatmap built from real, locally recorded watch events.
 * Responsive by design: the number of visible weeks and cell size adapt to the
 * available width, so tablets get a wider, denser grid instead of a stretched one.
 */
class ActivityHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var activity: Map<Long, Long> = emptyMap()
    private var today: Long = localEpochDay(System.currentTimeMillis())

    private var weeks: Int = 17
    private var cellSize = 0f
    private var gap = 0f
    private var originLeft = 0f
    private var originTop = 0f

    fun setData(dailyWatchMs: Map<Long, Long>) {
        this.activity = dailyWatchMs
        this.today = localEpochDay(System.currentTimeMillis())
        requestLayout()
        invalidate()
    }

    private fun localEpochDay(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 86_400_000L
    }

    private fun startDay(): Long {
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK) // Sunday=1 ... Saturday=7
        val daysSinceSaturday = (dow + 1) % 7
        val saturdayThisWeek = today - daysSinceSaturday
        return saturdayThisWeek - (weeks - 1) * 7L
    }

    private fun computeGrid(widthPx: Int, heightPx: Int) {
        val density = resources.displayMetrics.density
        gap = 3f * density
        val availableWidth = widthPx - paddingLeft - paddingRight
        if (availableWidth <= 0) return

        // Pick a week count that keeps cells pleasantly sized at any width. The
        // upper bound is a full year, so tablets fill the card instead of
        // leaving a large empty strip on the right.
        val targetCell = 17f * density
        val possibleWeeks = ((availableWidth + gap) / (targetCell + gap)).toInt()
        weeks = possibleWeeks.coerceIn(17, 53)

        cellSize = ((availableWidth - gap * (weeks - 1)) / weeks).coerceIn(9f * density, 24f * density)
        originTop = paddingTop + (heightPx - paddingTop - paddingBottom - (cellSize * 7 + gap * 6)) / 2f
        originLeft = widthPx - paddingRight - (cellSize * weeks + gap * (weeks - 1))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        gap = 3f * density
        val targetCell = 17f * density
        val possibleWeeks = ((width - paddingLeft - paddingRight + gap) / (targetCell + gap)).toInt()
        val measuredWeeks = possibleWeeks.coerceIn(17, 53)
        val cell = ((width - paddingLeft - paddingRight - gap * (measuredWeeks - 1)) / measuredWeeks)
            .coerceIn(9f * density, 24f * density)
        val desiredHeight = (cell * 7 + gap * 6 + paddingTop + paddingBottom).toInt()
            .coerceAtLeast((104 * density).toInt())
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        computeGrid(width, height)
        if (cellSize <= 0f) return

        val start = startDay()
        val maxMs = activity.values.maxOrNull() ?: 0L

        for (week in 0 until weeks) {
            for (day in 0 until 7) {
                val epochDay = start + week * 7L + day
                if (epochDay > today) continue
                val ms = activity[epochDay] ?: 0L
                val level = levelFor(ms, maxMs)
                cellPaint.color = ContextCompat.getColor(context, colorForLevel(level))

                val x = originLeft + (weeks - 1 - week) * (cellSize + gap)
                val y = originTop + day * (cellSize + gap)
                val rect = RectF(x, y, x + cellSize, y + cellSize)
                canvas.drawRoundRect(rect, cellSize * 0.3f, cellSize * 0.3f, cellPaint)
            }
        }
    }

    private fun levelFor(ms: Long, maxMs: Long): Int {
        if (ms <= 0L) return 0
        val minutes = ms / 60000.0
        val level = when {
            minutes < 15 -> 1
            minutes < 45 -> 2
            minutes < 90 -> 3
            else -> 4
        }
        // Keep the scale meaningful when the user has just started watching.
        return if (maxMs in 1..(20 * 60_000L) && level > 2) 2 else level
    }

    private fun colorForLevel(level: Int): Int = when (level) {
        1 -> R.color.heat_1
        2 -> R.color.heat_2
        3 -> R.color.heat_3
        4 -> R.color.heat_4
        else -> R.color.heat_0
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            computeGrid(width, height)
            if (cellSize <= 0f) return true
            val week = ((width - paddingRight - event.x) / (cellSize + gap)).toInt().coerceIn(0, weeks - 1)
            val day = ((event.y - originTop) / (cellSize + gap)).toInt().coerceIn(0, 6)
            val start = startDay()
            val epochDay = start + (weeks - 1 - week) * 7L + day
            if (epochDay in start..today) {
                val ms = activity[epochDay] ?: 0L
                val minutes = (ms / 60000).toInt()
                val date = SimpleDateFormat("d MMMM", Locale("fa")).format(Date(epochDay * 86_400_000L))
                val text = if (minutes <= 0) "$date • بدون تماشا" else "$date • $minutes دقیقه تماشا"
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
            performClick()
            return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
