package com.michaelunkai.neonflip.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pooled particle system: explosions, ball trails, coin bursts, sparks, confetti.
 * Zero allocation per frame after warmup. Drawn with additive blending for neon pop.
 */
class Particles(private val capacity: Int = 640) {

    class P {
        var x = 0f; var y = 0f
        var vx = 0f; var vy = 0f
        var life = 0f; var max = 1f
        var size = 4f; var endSize = 0f
        var color = 0
        var drag = 2f; var grav = 0f
        var spark = false           // spark: elongated streak along velocity
        var alive = false
    }

    private val pool = Array(capacity) { P() }
    private var cursor = 0
    private val rng = Random(System.nanoTime())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    private fun obtain(): P {
        // ring buffer: overwrite oldest when full
        var tries = 0
        var p = pool[cursor]
        while (p.alive && tries < capacity) {
            cursor = (cursor + 1) % capacity
            p = pool[cursor]; tries++
        }
        cursor = (cursor + 1) % capacity
        return p
    }

    private fun emit(x: Float, y: Float, vx: Float, vy: Float, life: Float,
                     size: Float, endSize: Float, color: Int,
                     drag: Float = 2f, grav: Float = 0f, spark: Boolean = false) {
        val p = obtain()
        p.x = x; p.y = y; p.vx = vx; p.vy = vy
        p.life = life; p.max = life
        p.size = size; p.endSize = endSize
        p.color = color; p.drag = drag; p.grav = grav; p.spark = spark
        p.alive = true
    }

    // ---------------------------------------------------------------- emitters (all in px)

    fun explosion(x: Float, y: Float, h: Float, color: Int, color2: Int) {
        for (i in 0 until 42) {
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val sp = (0.25f + rng.nextFloat() * 1.0f) * h
            emit(x, y, cos(a) * sp, sin(a) * sp,
                0.45f + rng.nextFloat() * 0.5f,
                h * (0.006f + rng.nextFloat() * 0.010f), 0f,
                if (i % 3 == 0) color2 else color,
                drag = 2.6f, grav = h * 0.35f, spark = i % 2 == 0)
        }
        // shockwave ring
        emit(x, y, 0f, 0f, 0.32f, h * 0.02f, h * 0.22f, color, drag = 0f)
    }

    fun trail(x: Float, y: Float, h: Float, color: Int) {
        val a = rng.nextFloat() * 2f * PI.toFloat()
        val sp = rng.nextFloat() * h * 0.05f
        emit(x, y, cos(a) * sp, sin(a) * sp, 0.30f + rng.nextFloat() * 0.22f,
            h * (0.004f + rng.nextFloat() * 0.005f), 0f, color, drag = 1.5f)
    }

    fun flipPuff(x: Float, y: Float, dir: Float, h: Float, color: Int) {
        for (i in 0 until 10) {
            val spread = (rng.nextFloat() - 0.5f) * h * 0.12f
            emit(x + spread * 0.3f, y, spread * 2.2f, dir * h * (0.15f + rng.nextFloat() * 0.25f),
                0.28f + rng.nextFloat() * 0.15f, h * 0.007f, 0f, color, drag = 3f)
        }
    }

    fun coinBurst(x: Float, y: Float, h: Float, color: Int) {
        for (i in 0 until 12) {
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val sp = (0.18f + rng.nextFloat() * 0.35f) * h
            emit(x, y, cos(a) * sp, sin(a) * sp, 0.30f + rng.nextFloat() * 0.25f,
                h * 0.005f, 0f, color, drag = 3f, spark = true)
        }
    }

    fun sparks(x: Float, y: Float, h: Float, color: Int, n: Int = 14) {
        for (i in 0 until n) {
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val sp = (0.2f + rng.nextFloat() * 0.6f) * h
            emit(x, y, cos(a) * sp, sin(a) * sp, 0.25f + rng.nextFloat() * 0.2f,
                h * 0.0035f, 0f, color, drag = 3.2f, spark = true)
        }
    }

    fun confetti(x: Float, y: Float, h: Float, c1: Int, c2: Int, c3: Int) {
        val colors = intArrayOf(c1, c2, c3)
        for (i in 0 until 36) {
            val a = -PI.toFloat() / 2f + (rng.nextFloat() - 0.5f) * 2.2f
            val sp = (0.25f + rng.nextFloat() * 0.55f) * h
            emit(x, y, cos(a) * sp, sin(a) * sp, 0.9f + rng.nextFloat() * 0.5f,
                h * (0.005f + rng.nextFloat() * 0.005f), 0f,
                colors[i % 3], drag = 1.6f, grav = h * 0.5f)
        }
    }

    fun ambientDust(x: Float, y: Float, h: Float, color: Int) {
        emit(x, y, -h * (0.03f + rng.nextFloat() * 0.05f), (rng.nextFloat() - 0.5f) * h * 0.02f,
            1.6f + rng.nextFloat(), h * 0.0022f, 0f, color, drag = 0.2f)
    }

    fun gateRing(x: Float, y: Float, h: Float, color: Int) {
        for (i in 0 until 16) {
            val a = rng.nextFloat() * 2f * PI.toFloat()
            emit(x + cos(a) * h * 0.02f, y + sin(a) * h * 0.12f,
                cos(a) * h * 0.3f, sin(a) * h * 0.3f, 0.4f,
                h * 0.004f, 0f, color, drag = 3f, spark = true)
        }
    }

    // ---------------------------------------------------------------- sim + draw

    fun update(dt: Float) {
        for (p in pool) {
            if (!p.alive) continue
            p.life -= dt
            if (p.life <= 0f) { p.alive = false; continue }
            val d = exp(-p.drag * dt)
            p.vx *= d; p.vy *= d
            p.vy += p.grav * dt
            p.x += p.vx * dt; p.y += p.vy * dt
        }
    }

    fun draw(c: Canvas) {
        for (p in pool) {
            if (!p.alive) continue
            val t = p.life / p.max // 1 -> 0
            val size = if (p.endSize > 0f) p.size + (p.endSize - p.size) * (1f - t) else p.size * t
            val alpha = (t.coerceIn(0f, 1f) * 255f).toInt()
            paint.color = p.color
            paint.alpha = alpha
            if (p.spark) {
                paint.strokeWidth = size
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                c.drawLine(p.x, p.y, p.x - p.vx * 0.03f, p.y - p.vy * 0.03f, paint)
                paint.style = Paint.Style.FILL
                paint.strokeCap = Paint.Cap.BUTT
            } else {
                c.drawCircle(p.x, p.y, size.coerceAtLeast(0.5f), paint)
            }
        }
    }

    fun clear() {
        for (p in pool) p.alive = false
    }
}
