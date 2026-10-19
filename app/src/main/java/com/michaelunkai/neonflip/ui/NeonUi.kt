package com.michaelunkai.neonflip.ui

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.sin

/**
 * Canvas-UI toolkit: neon buttons, chips, toggles, glow text, easing curves.
 * Everything laid out in px per frame; hit rects are the same RectFs used to draw.
 */
object NeonUi {

    // ------------------------------------------------------------------ easing

    object Easing {
        fun outCubic(t: Float): Float { val p = t - 1f; return 1f + p * p * p }
        fun outQuint(t: Float): Float { val p = t - 1f; return 1f + p * p * p * p * p }
        fun outBack(t: Float): Float {
            val c = 1.70158f; val p = t - 1f
            return 1f + (c + 1f) * p * p * p + c * p * p
        }
        fun pulse(t: Float, hz: Float): Float = 0.5f + 0.5f * sin((t * 2.0 * PI * hz).toFloat())
    }

    // ------------------------------------------------------------------ button model

    const val STYLE_PRIMARY = 0
    const val STYLE_GHOST = 1
    const val STYLE_GOLD = 2
    const val STYLE_DANGER = 3

    class Btn(
        val id: String,
        val rect: RectF = RectF(),
        var label: String = "",
        var sub: String? = null,
        var style: Int = STYLE_GHOST,
        var enabled: Boolean = true,
        var pulse: Float = 0f          // 0..1 pulse amount
    ) {
        fun hit(x: Float, y: Float): Boolean =
            enabled && rect.contains(x, y)
    }

    // ------------------------------------------------------------------ paints (reused)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val textSub = Paint(text)

    val ACCENT = 0xFF00E5FF.toInt()
    val PINK = 0xFFFF2E97.toInt()
    val GOLD = 0xFFFFD54A.toInt()
    val DANGER = 0xFFFF4D5E.toInt()
    val TEXT_DIM = 0xFF8FA3C8.toInt()

    // ------------------------------------------------------------------ widgets

    fun drawButton(c: Canvas, b: Btn, h: Float, entrance: Float) {
        val r = b.rect
        if (r.isEmpty) return
        val accentCol = when (b.style) {
            STYLE_PRIMARY -> ACCENT
            STYLE_GOLD -> GOLD
            STYLE_DANGER -> DANGER
            else -> ACCENT
        }
        val scale = 1f + b.pulse * 0.035f
        val cx = r.centerX(); val cy = r.centerY()
        val hw = r.width() / 2f * scale * entrance
        val hh = r.height() / 2f * scale * entrance
        val radius = hh * 0.9f

        // glow
        stroke.strokeWidth = h * 0.008f
        stroke.color = accentCol
        stroke.alpha = if (b.enabled) 110 else 30
        c.drawRoundRect(cx - hw, cy - hh, cx + hw, cy + hh, radius, radius, stroke)
        // body
        fill.style = Paint.Style.FILL
        fill.color = if (b.enabled) 0xE60D1226.toInt() else 0x660D1226.toInt()
        c.drawRoundRect(cx - hw, cy - hh, cx + hw, cy + hh, radius, radius, fill)
        // border
        stroke.strokeWidth = if (b.style == STYLE_GHOST) h * 0.0022f else h * 0.0035f
        stroke.color = accentCol
        stroke.alpha = if (b.enabled) 255 else 70
        c.drawRoundRect(cx - hw, cy - hh, cx + hw, cy + hh, radius, radius, stroke)
        // top highlight line
        fill.color = accentCol; fill.alpha = if (b.enabled) 40 else 12
        c.drawRoundRect(cx - hw * 0.86f, cy - hh * 0.9f, cx + hw * 0.86f, cy - hh * 0.55f,
            radius * 0.5f, radius * 0.5f, fill)
        fill.alpha = 255
        // label
        text.textAlign = Paint.Align.CENTER
        text.color = if (b.enabled) 0xFFFFFFFF.toInt() else 0x66FFFFFF
        text.textSize = hh * 0.62f
        val labelY = if (b.sub != null) cy - hh * 0.12f else cy + hh * 0.22f
        c.drawText(b.label, cx, labelY, text)
        if (b.sub != null) {
            textSub.color = accentCol
            textSub.alpha = if (b.enabled) 230 else 70
            textSub.textSize = hh * 0.4f
            c.drawText(b.sub!!, cx, cy + hh * 0.52f, textSub)
        }
    }

    /** Small rounded info chip. Returns content width. */
    fun drawChip(c: Canvas, r: RectF, label: String, color: Int, h: Float, filled: Boolean = false) {
        if (r.isEmpty) return
        stroke.strokeWidth = h * 0.0018f
        stroke.color = color
        stroke.alpha = if (filled) 255 else 170
        fill.color = color
        fill.alpha = if (filled) 36 else 18
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fill)
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, stroke)
        text.textSize = r.height() * 0.52f
        text.textAlign = Paint.Align.CENTER
        text.color = color
        text.alpha = 255
        c.drawText(label, r.centerX(), r.centerY() + r.height() * 0.18f, text)
        fill.alpha = 255
    }

    fun drawToggle(c: Canvas, r: RectF, on: Boolean, h: Float) {
        fill.color = if (on) 0x5900E5FF else 0x33FFFFFF
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fill)
        stroke.strokeWidth = h * 0.0018f
        stroke.color = if (on) ACCENT else 0x88FFFFFF.toInt()
        stroke.alpha = 200
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, stroke)
        fill.color = if (on) ACCENT else 0xFFC9D4EE.toInt()
        val kx = if (on) r.right - r.height() / 2f else r.left + r.height() / 2f
        c.drawCircle(kx, r.centerY(), r.height() * 0.38f, fill)
        fill.alpha = 255
    }

    fun glowTitle(c: Canvas, s: String, cx: Float, cy: Float, size: Float,
                  color: Int, glowColor: Int) {
        glow.style = Paint.Style.FILL_AND_STROKE
        glow.color = glowColor
        glow.alpha = 170
        glow.strokeWidth = size * 0.05f
        text.textSize = size
        text.color = color
        text.alpha = 170
        c.drawText(s, cx, cy, glow)
        text.alpha = 255
        c.drawText(s, cx, cy, text)
        glow.strokeWidth = 0f
    }

    /** Draws the neon ball preview used in shop cards. */
    fun drawBallPreview(c: Canvas, cx: Float, cy: Float, r: Float, body: Int, glowC: Int, trail: Int) {
        glow.style = Paint.Style.FILL
        glow.color = glowC
        glow.alpha = 160
        c.drawCircle(cx, cy, r * 1.5f, glow)
        fill.color = trail; fill.alpha = 120
        val rectF = RectF(cx - r * 3.2f, cy - r * 0.28f, cx - r * 0.9f, cy + r * 0.28f)
        c.drawRoundRect(rectF, r * 0.28f, r * 0.28f, fill)
        fill.alpha = 255
        fill.color = body
        c.drawCircle(cx, cy, r, fill)
        fill.color = 0xFFFFFFFF.toInt(); fill.alpha = 100
        c.drawCircle(cx - r * 0.3f, cy - r * 0.32f, r * 0.28f, fill)
        fill.alpha = 255
    }

    /** Vertical gradient helper (allocated per call; used in static screens only, not the game loop). */
    fun gradient(top: Int, bottom: Int, r: RectF): LinearGradient =
        LinearGradient(0f, r.top, 0f, r.bottom, top, bottom, Shader.TileMode.CLAMP)

    fun drawPanel(c: Canvas, r: RectF, h: Float, borderColor: Int = 0x6600E5FF) {
        fill.color = 0xF00B0E1A.toInt()
        c.drawRoundRect(r, h * 0.02f, h * 0.02f, fill)
        stroke.strokeWidth = h * 0.002f
        stroke.color = borderColor
        stroke.alpha = 200
        c.drawRoundRect(r, h * 0.02f, h * 0.02f, stroke)
        stroke.alpha = 255
    }

    fun coinDot(c: Canvas, x: Float, y: Float, radius: Float) {
        fill.color = GOLD
        c.drawCircle(x, y, radius, fill)
        fill.color = 0xFFFFF3C4.toInt()
        c.drawCircle(x - radius * 0.25f, y - radius * 0.25f, radius * 0.45f, fill)
    }

    /** Shared opaque-fill paint for fullscreen dims (mutate color/alpha per use). */
    fun fillOpaque(): Paint = dimPaint

    private val dimPaint = Paint().apply { style = Paint.Style.FILL }

    fun textLeft(): Paint { text.textAlign = Paint.Align.LEFT; return text }
    fun textCenter(): Paint { text.textAlign = Paint.Align.CENTER; return text }
    fun textRight(): Paint { text.textAlign = Paint.Align.RIGHT; return text }
}
