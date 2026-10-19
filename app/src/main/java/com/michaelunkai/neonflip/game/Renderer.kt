package com.michaelunkai.neonflip.game

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.michaelunkai.neonflip.retention.Economy
import com.michaelunkai.neonflip.retention.SaveStore
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Canvas renderer: layered gradients, parallax grid, glowing corridor, neon entities,
 * particle draw-through, HUD with glow text. All paints pre-allocated; gradients only
 * rebuilt on size/theme change. 60fps-friendly on mid hardware.
 */
class Renderer(private val engine: GameEngine) {

    private var w = 0f; private var h = 0f

    // theme colors
    private var bgTop = 0; private var bgBottom = 0; private var corridorC = 0; private var accent = 0

    private val bgPaint = Paint()
    private val vignette = Paint()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val glowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }
    private val glowStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
    }
    private val textBig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER; letterSpacing = 0.04f
    }
    private val textGlow = Paint(textBig).apply { maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL) }
    private val textMid = Paint(textBig)
    private val textSmall = Paint(textBig).apply { textAlign = Paint.Align.LEFT }
    private val textRight = Paint(textBig).apply { textAlign = Paint.Align.RIGHT }
    private val particlesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rect = RectF()
    private val path = Path()
    private var frameAlpha = 0f

    fun onSizeChanged(w: Int, h: Int) {
        this.w = w.toFloat(); this.h = h.toFloat()
        rebuildGradients()
    }

    private fun rebuildGradients() {
        val th = Economy.equippedTheme()
        bgTop = th.bgTop; bgBottom = th.bgBottom; corridorC = th.corridor; accent = th.accent
        bgPaint.shader = LinearGradient(0f, 0f, 0f, h, bgTop, bgBottom, Shader.TileMode.CLAMP)
        vignette.shader = RadialGradient(w / 2f, h / 2f, h * 0.75f,
            0x00000000, 0x99000000, Shader.TileMode.CLAMP)
    }

    /** Ensure theme changes picked up (cheap id check). */
    fun refreshTheme() {
        val th = Economy.equippedTheme()
        if (th.bgTop != bgTop || th.accent != accent) rebuildGradients()
    }

    fun draw(c: Canvas, alpha: Float) {
        refreshTheme()
        frameAlpha = alpha
        val e = engine
        c.save()
        if (e.shakeX != 0f || e.shakeY != 0f) c.translate(e.shakeX, e.shakeY)

        drawBackground(c)
        drawCorridor(c)
        drawGates(c)
        drawCoins(c, alpha)
        drawObstacles(c, alpha)
        drawParticles(c)
        drawBall(c, alpha)
        drawFloaters(c)

        c.restore()

        // full-screen overlays (unshaken)
        drawSlowmoTint(c)
        drawVignette(c)
        if (e.state != GameEngine.State.MENU) drawHud(c)
        drawFlash(c)
    }

    // ------------------------------------------------------------------ layers

    private fun drawBackground(c: Canvas) {
        c.drawRect(0f, 0f, w, h, bgPaint)
        val e = engine
        val scroll = e.distancePx
        // parallax vertical lines, two depths
        for (layer in 0..1) {
            val speedF = if (layer == 0) 0.25f else 0.5f
            val gap = h * (if (layer == 0) 0.34f else 0.22f)
            val off = -((scroll * speedF) % gap)
            stroke.color = corridorC
            stroke.alpha = if (layer == 0) 26 else 40
            stroke.strokeWidth = if (layer == 0) 1.5f else 2f
            var x = off
            while (x < w + gap) {
                c.drawLine(x, 0f, x, h, stroke)
                x += gap
            }
        }
        // drifting glow orbs (cheap "stars")
        fill.alpha = 40
        fill.color = accent
        for (i in 0 until 7) {
            val seed = i * 9781
            val px = ((seed * 31 + scroll * 0.15f) % (w + 200f)) - 100f
            val py = h * (0.12f + 0.09f * (i % 7))
            c.drawCircle(px, py, h * 0.003f * (1 + i % 3), fill)
        }
        fill.alpha = 255
    }

    private fun drawCorridor(c: Canvas) {
        val e = engine
        val fy = e.floorY; val cy = e.ceilY
        // dark bands
        fill.color = Color.BLACK; fill.alpha = 120
        c.drawRect(0f, fy, w, h, fill)
        c.drawRect(0f, 0f, w, cy, fill)
        fill.alpha = 255
        // beat pulse
        val pulse = 0.55f + 0.45f * sin(e.time * (2.0 * PI / 1.1)).toFloat()
        // edges: glow pass then crisp pass
        stroke.strokeWidth = 9f; stroke.color = accent; stroke.alpha = (70 * pulse).toInt()
        c.drawLine(0f, fy, w, fy, stroke)
        c.drawLine(0f, cy, w, cy, stroke)
        stroke.strokeWidth = 2.6f; stroke.alpha = 255
        c.drawLine(0f, fy, w, fy, stroke)
        c.drawLine(0f, cy, w, cy, stroke)
        // scrolling ticks on edges
        val gap = h * 0.09f
        val off = -((e.distancePx) % gap)
        stroke.strokeWidth = 2f; stroke.alpha = 130
        var x = off
        while (x < w + gap) {
            c.drawLine(x, fy - h * 0.012f, x + h * 0.02f, fy, stroke)
            c.drawLine(x, cy + h * 0.012f, x + h * 0.02f, cy, stroke)
            x += gap
        }
    }

    private fun drawGates(c: Canvas) {
        val e = engine
        val lag = lagPx()
        val openH = h * GameBalance.GATE_OPEN
        for (g in e.gates) {
            val gx = g.x + lag
            val top = if (g.onFloor) e.floorY - openH else e.ceilY
            rect.set(gx - h * 0.02f, top, gx + h * 0.02f, top + openH)
            val pulse = 0.7f + 0.3f * sin(e.time * 6f) + g.pulse
            stroke.strokeWidth = 5f
            stroke.color = 0xFFFFD54A.toInt()
            stroke.alpha = (150 * pulse).toInt().coerceIn(60, 255)
            c.drawRoundRect(rect, h * 0.02f, h * 0.02f, stroke)
            stroke.strokeWidth = 2f; stroke.alpha = 255
            c.drawRoundRect(rect, h * 0.02f, h * 0.02f, stroke)
        }
    }

    private fun drawCoins(c: Canvas, alpha: Float) {
        val e = engine
        val lag = lagPx()
        val r = h * GameBalance.COIN_R
        for (cn in e.coins) {
            val cx = cn.x + lag
            if (cx < -r * 2 || cx > w + r * 2) continue
            val spin = abs(cos(cn.t * 5f)).coerceIn(0.18f, 1f)
            fill.color = 0xFFFFD54A.toInt()
            fill.alpha = 255
            glowFill.color = 0xFFFFC93C.toInt()
            glowFill.alpha = 110
            c.drawCircle(cx, cn.y, r * 1.6f, glowFill)
            c.drawOval(cx - r * spin, cn.y - r, cx + r * spin, cn.y + r, fill)
            fill.color = 0xFFFFF3C4.toInt()
            c.drawOval(cx - r * spin * 0.55f, cn.y - r * 0.55f,
                cx + r * spin * 0.55f, cn.y + r * 0.55f, fill)
        }
    }

    private fun drawObstacles(c: Canvas, alpha: Float) {
        val e = engine
        val lag = lagPx()
        val spikeW = h * GameBalance.SPIKE_W
        for (o in e.obstacles) {
            val ox = o.x + lag
            if (ox < -spikeW * 2 || ox > w + spikeW * 2) continue
            when (o.type) {
                GameEngine.ObstacleType.SPIKE_FLOOR, GameEngine.ObstacleType.SPIKE_CEIL -> {
                    val baseY = if (o.onFloor) e.floorY else e.ceilY
                    val apexY = if (o.onFloor) baseY - o.h else baseY + o.h
                    path.reset()
                    path.moveTo(ox - o.w / 2f, baseY)
                    path.lineTo(ox, apexY)
                    path.lineTo(ox + o.w / 2f, baseY)
                    path.close()
                    fill.color = 0xFF1A1030.toInt(); fill.alpha = 200
                    c.drawPath(path, fill)
                    fill.alpha = 255
                    stroke.strokeWidth = 3f
                    stroke.color = 0xFFFF2E97.toInt()
                    c.drawPath(path, stroke)
                    stroke.strokeWidth = 10f; stroke.alpha = 60
                    c.drawPath(path, stroke)
                    stroke.alpha = 255; stroke.strokeWidth = 3f
                }
                GameEngine.ObstacleType.BLOCK, GameEngine.ObstacleType.MOVER -> {
                    rect.set(ox - o.w / 2f, o.yTop, ox + o.w / 2f, o.yTop + o.h)
                    fill.color = 0xFF1A1030.toInt(); fill.alpha = 230
                    c.drawRoundRect(rect, h * 0.012f, h * 0.012f, fill)
                    fill.alpha = 255
                    stroke.strokeWidth = 3f
                    stroke.color = 0xFFFF2E97.toInt()
                    c.drawRoundRect(rect, h * 0.012f, h * 0.012f, stroke)
                    stroke.strokeWidth = 9f; stroke.alpha = 55
                    c.drawRoundRect(rect, h * 0.012f, h * 0.012f, stroke)
                    stroke.alpha = 255; stroke.strokeWidth = 3f
                    // inner core line
                    fill.color = 0xFFFF2E97.toInt(); fill.alpha = 130
                    c.drawRect(ox - o.w * 0.18f, o.yTop + o.h * 0.3f,
                        ox + o.w * 0.18f, o.yTop + o.h * 0.7f, fill)
                    fill.alpha = 255
                }
            }
        }
    }

    private fun drawParticles(c: Canvas) {
        engine.particles.draw(c)
    }

    private fun drawBall(c: Canvas, alpha: Float) {
        val e = engine
        if (e.state == GameEngine.State.DYING || e.state == GameEngine.State.OVER) return
        val skin = Economy.equippedSkin()
        val y = e.ballPrevY + (e.ballY - e.ballPrevY) * alpha
        val r = e.ballR
        // invulnerability blink
        if (e.invulnT > 0f && (sin(e.time * 22f) < 0f)) {
            // shield ring only
            stroke.strokeWidth = 2.5f
            stroke.color = skin.glow
            stroke.alpha = 200
            c.drawCircle(e.ballX, y, r * 2.1f, stroke)
            stroke.alpha = 255
            return
        }
        // squash & stretch by vy
        val vyn = (e.ballVy / (h * 1.4f)).coerceIn(-0.5f, 0.5f)
        val sx = 1f + abs(vyn) * 0.35f
        val sy = 1f - abs(vyn) * 0.35f
        // glow
        glowFill.color = skin.glow
        glowFill.alpha = 170
        c.drawCircle(e.ballX, y, r * 1.7f, glowFill)
        // body
        fill.color = skin.body
        c.drawOval(e.ballX - r * sx, y - r * sy, e.ballX + r * sx, y + r * sy, fill)
        // inner highlight
        fill.color = 0xFFFFFFFF.toInt(); fill.alpha = 90
        c.drawCircle(e.ballX - r * 0.3f, y - r * 0.32f, r * 0.3f, fill)
        fill.alpha = 255
    }

    private fun drawFloaters(c: Canvas) {
        for (f in engine.floaters) {
            val a = (1f - f.t / f.life).coerceIn(0f, 1f)
            textMid.color = f.color
            textMid.alpha = (a * 255).toInt()
            textMid.textSize = h * 0.022f
            c.drawText(f.text, f.x, f.y, textMid)
        }
    }

    // ------------------------------------------------------------------ HUD

    private fun drawHud(c: Canvas) {
        val e = engine
        val score = e.scoreF.toInt()
        // score with glow
        textBig.textSize = h * 0.052f
        textBig.color = Color.WHITE
        textGlow.color = accent
        textGlow.alpha = 160
        textGlow.textSize = textBig.textSize
        val sx = w / 2f
        val sy = h * 0.075f
        c.drawText(score.toString(), sx, sy, textGlow)
        c.drawText(score.toString(), sx, sy, textBig)
        // best
        val best = max(SaveStore.data.bestScore, score)
        textMid.textSize = h * 0.016f
        textMid.color = 0xFF8FA3C8.toInt()
        textMid.alpha = 220
        c.drawText("BEST $best", sx, sy + h * 0.026f, textMid)
        // coins chip (top right)
        textRight.textSize = h * 0.022f
        textRight.color = 0xFFFFD54A.toInt()
        val cy2 = h * 0.045f
        fill.color = 0xFFFFD54A.toInt()
        c.drawCircle(w - h * 0.075f, cy2 - h * 0.007f, h * 0.008f, fill)
        c.drawText(e.runCoins.toString(), w - h * 0.05f, cy2, textRight)
        // first-runs onboarding hint (first 3 runs, fades out after ~2.4s)
        if (e.state == GameEngine.State.RUNNING && e.runTime < 3f &&
            SaveStore.data.totalRuns < 3) {
            val fade = ((3f - e.runTime) / 0.6f).coerceIn(0f, 1f)
            textMid.textSize = h * 0.026f
            textMid.color = 0xFF9BE8FF.toInt()
            textMid.alpha = (fade * (170f + 60f * sin(e.time * 5f))).toInt().coerceIn(0, 255)
            c.drawText("TAP TO FLIP", sx, h * 0.55f, textMid)
            textMid.alpha = 255
        }
        // combo meter
        if (e.combo > 1) {
            val barW = w * 0.3f
            val frac = (e.comboT / GameBalance.COMBO_HOLD_S).coerceIn(0f, 1f)
            val barY = sy + h * 0.042f
            fill.color = 0x33FFFFFF
            c.drawRoundRect(sx - barW / 2f, barY, sx + barW / 2f, barY + h * 0.008f,
                h * 0.004f, h * 0.004f, fill)
            fill.color = 0xFFFFD54A.toInt()
            c.drawRoundRect(sx - barW / 2f, barY, sx - barW / 2f + barW * frac,
                barY + h * 0.008f, h * 0.004f, h * 0.004f, fill)
            val pulse = 1f + 0.12f * sin(e.time * 10f)
            textMid.textSize = h * 0.026f * pulse
            textMid.color = 0xFFFFD54A.toInt()
            c.drawText("x${e.combo}", sx, barY + h * 0.034f, textMid)
        }
    }

    // ------------------------------------------------------------------ overlays

    private fun drawSlowmoTint(c: Canvas) {
        val e = engine
        val ts = e.timeScale
        if (ts >= 0.97f) return
        val a = ((1f - ts) * 120).toInt().coerceIn(0, 120)
        fill.color = 0xFF66E0FF.toInt(); fill.alpha = a / 3
        c.drawRect(0f, 0f, w, h * 0.05f, fill)
        c.drawRect(0f, h * 0.95f, w, h, fill)
        fill.alpha = 255
    }

    private fun drawVignette(c: Canvas) {
        c.drawRect(0f, 0f, w, h, vignette)
    }

    private fun drawFlash(c: Canvas) {
        val e = engine
        if (e.flash <= 0.01f) return
        fill.color = e.flashColor
        fill.alpha = (e.flash * 110).toInt()
        c.drawRect(0f, 0f, w, h, fill)
        fill.alpha = 255
    }

    private fun lagPx(): Float {
        val e = engine
        val speed = if (e.state == GameEngine.State.MENU) h * GameBalance.MENU_SPEED else e.speedCur
        return speed * frameAlpha * GameBalance.FIXED_DT
    }
}
