package com.hnn.bisnor.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.hnn.bisnor.R
import kotlin.math.max

/**
 * "Liquid glass" container: blurs whatever is drawn behind it (live, every frame the
 * window redraws), boosts its saturation, tints it, and adds a bright rim + top sheen.
 *
 * Android has no backdrop-blur API (RenderEffect only blurs a view's own pixels, and only
 * on 12+), so this samples the window root into a small bitmap and box-blurs it in Kotlin.
 * The bitmap is 1/[DOWNSCALE] of the glass area, so a full-width nav bar is ~3k pixels.
 */
class GlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    var cornerRadius: Float
    private val tintColor: Int
    private val density = resources.displayMetrics.density

    private var source: View? = null
    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private var scratch = IntArray(0)
    private var capturing = false
    private val selfLoc = IntArray(2)
    private val rootLoc = IntArray(2)

    private val clip = Path()
    private val bounds = RectF()
    private val blurPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.7f) })
    }
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    // Specular light: a soft spot that follows touches / the selected tab (0..1 across the width).
    private var lightX = 0.5f
    private var lightAnimator: android.animation.ValueAnimator? = null
    private val glowColor = ContextCompat.getColor(context, R.color.lg_glow)
    private val baseTintAlpha = android.graphics.Color.alpha(ContextCompat.getColor(context, R.color.lg_tint))
    private val isNight = (resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

    /** Glides the highlight to [x] (px, in this view's coordinates). */
    fun moveLightTo(x: Float, animate: Boolean = true) {
        if (width <= 0) return
        val target = (x / width).coerceIn(0f, 1f)
        lightAnimator?.cancel()
        if (!animate) {
            lightX = target; invalidate(); return
        }
        lightAnimator = android.animation.ValueAnimator.ofFloat(lightX, target).apply {
            duration = 420
            interpolator = android.view.animation.PathInterpolator(0.2f, 0.9f, 0.25f, 1f)
            addUpdateListener { lightX = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> moveLightTo(ev.x)
            android.view.MotionEvent.ACTION_MOVE -> moveLightTo(ev.x, animate = false)
        }
        return super.dispatchTouchEvent(ev)
    }

    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        if (isShown && width > 0 && height > 0) capture()
        true
    }

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.GlassView)
        cornerRadius = a.getDimension(R.styleable.GlassView_glassCornerRadius, 28 * density)
        tintColor = a.getColor(
            R.styleable.GlassView_glassTint,
            ContextCompat.getColor(context, R.color.lg_tint)
        )
        a.recycle()
        setWillNotDraw(false)
        rimPaint.strokeWidth = max(1f, 1.1f * density)
        tintPaint.color = tintColor
        // Focusable glass buttons lift and ring when the D-pad lands on them (TV).
        if (isFocusable) {
            stateListAnimator = android.animation.AnimatorInflater.loadStateListAnimator(context, R.animator.focus_lift)
        }
    }

    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.on_surface)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Sample the whole window content, so glass reflects every layer behind it.
        source = rootView
        rootView.viewTreeObserver.addOnPreDrawListener(preDraw)
    }

    override fun onDetachedFromWindow() {
        source?.viewTreeObserver?.removeOnPreDrawListener(preDraw)
        source = null
        bitmap?.recycle()
        bitmap = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        clip.reset()
        clip.addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)
        val c1 = ContextCompat.getColor(context, R.color.lg_rim_top)
        val c2 = ContextCompat.getColor(context, R.color.lg_rim_bottom)
        rimPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), c1, c2, Shader.TileMode.CLAMP)
        sheenPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.55f,
            ContextCompat.getColor(context, R.color.lg_sheen), 0, Shader.TileMode.CLAMP
        )
        val bw = max(1, w / DOWNSCALE)
        val bh = max(1, h / DOWNSCALE)
        bitmap?.recycle()
        bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        pixels = IntArray(bw * bh)
        scratch = IntArray(bw * bh)
    }

    private fun capture() {
        val root = source ?: return
        val bmp = bitmap ?: return
        getLocationInWindow(selfLoc)
        root.getLocationInWindow(rootLoc)
        val canvas = Canvas(bmp)
        bmp.eraseColor(ContextCompat.getColor(context, R.color.background))
        canvas.scale(bmp.width / width.toFloat(), bmp.height / height.toFloat())
        canvas.translate(-(selfLoc[0] - rootLoc[0]).toFloat(), -(selfLoc[1] - rootLoc[1]).toFloat())
        capturing = true
        try {
            root.draw(canvas)
        } catch (_: Exception) {
            // Hardware-only content (e.g. a playing SurfaceView) can't be sampled; keep last frame.
        } finally {
            capturing = false
        }
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        repeat(3) { boxBlur(pixels, scratch, bmp.width, bmp.height, RADIUS) }
        bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        adaptTint(averageLuminance(pixels))
        invalidate()
    }

    /**
     * Apple-style adaptivity: dark glass gets denser over bright content (and lighter
     * over dark content) so the icons on it keep their contrast; light glass mirrors that.
     */
    private fun adaptTint(lum: Float) {
        val swing = if (isNight) (lum - 0.35f) else (0.6f - lum)
        tintPaint.alpha = (baseTintAlpha * (1f + swing * 0.9f)).toInt().coerceIn(20, 235)
    }

    override fun draw(canvas: Canvas) {
        // While the root is being sampled, draw nothing so the glass never blurs itself.
        if (capturing) return
        super.draw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        val save = canvas.save()
        canvas.clipPath(clip)
        bitmap?.let { canvas.drawBitmap(it, null, bounds, blurPaint) }
        canvas.drawRect(bounds, tintPaint)
        canvas.drawRect(bounds, sheenPaint)
        // Specular spot near the top edge, centred on the light position.
        val lx = lightX * width
        glowPaint.shader = android.graphics.RadialGradient(
            lx, 0f, max(height * 1.4f, 1f), glowColor, 0, Shader.TileMode.CLAMP
        )
        canvas.drawRect(bounds, glowPaint)
        canvas.restoreToCount(save)
        val inset = rimPaint.strokeWidth / 2
        canvas.drawRoundRect(
            inset, inset, width - inset, height - inset,
            cornerRadius - inset, cornerRadius - inset, rimPaint
        )
        // The rim catches the light: brighter right where the spot is.
        glowRimPaint.strokeWidth = rimPaint.strokeWidth * 1.3f
        glowRimPaint.shader = android.graphics.RadialGradient(
            lx, 0f, max(width * 0.35f, 1f), android.graphics.Color.WHITE, 0, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            inset, inset, width - inset, height - inset,
            cornerRadius - inset, cornerRadius - inset, glowRimPaint
        )
        if (isFocused) {
            focusPaint.strokeWidth = 3 * density
            val fi = focusPaint.strokeWidth / 2
            canvas.drawRoundRect(fi, fi, width - fi, height - fi, cornerRadius - fi, cornerRadius - fi, focusPaint)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val save = canvas.save()
        canvas.clipPath(clip)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }

    companion object {
        private const val DOWNSCALE = 8
        private const val RADIUS = 3

        /** Mean perceived luminance (0..1) of ARGB pixels, sampling every 4th one. */
        internal fun averageLuminance(px: IntArray): Float {
            if (px.isEmpty()) return 0f
            var sum = 0L
            var n = 0
            var i = 0
            while (i < px.size) {
                val c = px[i]
                sum += (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
                n++
                i += 4
            }
            return sum / (n * 255f)
        }

        /** One horizontal + one vertical box-blur pass, in place on [px]. */
        internal fun boxBlur(px: IntArray, tmp: IntArray, w: Int, h: Int, r: Int) {
            pass(px, tmp, w, h, r, horizontal = true)
            pass(tmp, px, w, h, r, horizontal = false)
        }

        private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
            val lines = if (horizontal) h else w
            val len = if (horizontal) w else h
            val window = 2 * r + 1
            for (line in 0 until lines) {
                var a = 0; var rr = 0; var g = 0; var b = 0
                fun idx(i: Int): Int {
                    val c = i.coerceIn(0, len - 1)
                    return if (horizontal) line * w + c else c * w + line
                }
                for (i in -r..r) {
                    val p = src[idx(i)]
                    a += p ushr 24; rr += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
                }
                for (i in 0 until len) {
                    dst[idx(i)] = ((a / window) shl 24) or ((rr / window) shl 16) or
                        ((g / window) shl 8) or (b / window)
                    val out = src[idx(i - r)]
                    val inn = src[idx(i + r + 1)]
                    a += (inn ushr 24) - (out ushr 24)
                    rr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                    g += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                    b += (inn and 0xFF) - (out and 0xFF)
                }
            }
        }
    }
}
