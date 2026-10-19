package com.michaelunkai.neonflip.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import com.michaelunkai.neonflip.R
import com.michaelunkai.neonflip.retention.Economy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * Renders the score into a premium-looking 4:5 share card bitmap (no external assets).
 * The Activity attaches it to an ACTION_SEND image intent via FileProvider.
 */
object ShareCard {

    const val W = 1080
    const val H = 1350

    fun build(ctx: Context, score: Int, best: Int, coins: Int, newBest: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val theme = Economy.equippedTheme()
        val skin = Economy.equippedSkin()

        // background
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(),
                theme.bgTop, theme.bgBottom, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        // grid
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f; color = theme.corridor; alpha = 60
        }
        var x = 0f
        while (x < W) { c.drawLine(x, 0f, x, H.toFloat(), grid); x += 90f }
        var y = 0f
        while (y < H) { c.drawLine(0f, y, W.toFloat(), y, grid); y += 90f }

        // pulsing orbs
        val orb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.accent; alpha = 70 }
        for (i in 0 until 6) {
            val ox = (150 + i * 160).toFloat()
            val oy = (200 + 130 * sin(i * 1.7)).toFloat() + 120f
            c.drawCircle(ox, oy, 10f + 8f * (i % 3), orb)
        }

        // frame
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 5f; color = theme.accent; alpha = 180
        }
        c.drawRoundRect(40f, 40f, W - 40f, H - 40f, 48f, 48f, frame)

        val center = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val glow = Paint(center).apply {
            maskFilter = BlurMaskFilter(26f, BlurMaskFilter.Blur.NORMAL)
        }

        // title
        center.textSize = 96f
        center.color = 0xFFFFFFFF.toInt()
        glow.textSize = 96f
        glow.color = theme.accent
        glow.alpha = 170
        c.drawText(ctx.getString(R.string.app_name), W / 2f, 250f, glow)
        c.drawText(ctx.getString(R.string.app_name), W / 2f, 250f, center)

        // ball decoration
        val ballY = 430f
        glow.style = Paint.Style.FILL
        glow.color = skin.glow
        glow.alpha = 160
        c.drawCircle(W / 2f, ballY, 95f, glow)
        glow.alpha = 255
        val body = Paint(Paint.ANTI_ALIAS_FLAG)
        body.color = skin.body
        c.drawCircle(W / 2f, ballY, 62f, body)
        body.color = 0xFFFFFFFF.toInt(); body.alpha = 110
        c.drawCircle(W / 2f - 20f, ballY - 20f, 18f, body)
        body.alpha = 255
        // floor lines
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 6f; color = theme.accent
        }
        c.drawLine(140f, 530f, W - 140f, 530f, line)

        // score
        center.textSize = 300f
        center.color = 0xFFFFFFFF.toInt()
        glow.textSize = 300f
        glow.color = theme.accent
        glow.alpha = 170
        val scoreStr = score.toString()
        c.drawText(scoreStr, W / 2f, 830f, glow)
        c.drawText(scoreStr, W / 2f, 830f, center)

        // badge
        center.textSize = 58f
        if (newBest) {
            val pulse = 1f + 0.04f * sin(System.nanoTime() * 0.000000001 * 2.0 * PI).toFloat()
            center.textSize = 58f * pulse
            center.color = NeonUi.GOLD
            c.drawText(ctx.getString(R.string.share_new_best), W / 2f, 940f, center)
        } else {
            center.textSize = 50f
            center.color = 0xFF8FA3C8.toInt()
            c.drawText(ctx.getString(R.string.share_best, best), W / 2f, 940f, center)
        }

        // coins + date
        center.textSize = 46f
        center.color = NeonUi.GOLD
        c.drawText(ctx.getString(R.string.share_coins, coins), W / 2f, 1040f, center)
        center.textSize = 38f
        center.color = 0xFF8FA3C8.toInt()
        val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            .format(Date())
        c.drawText(date, W / 2f, 1110f, center)

        // footer
        center.textSize = 44f
        center.color = theme.accent
        c.drawText(ctx.getString(R.string.share_footer), W / 2f, 1240f, center)

        return bmp
    }
}
