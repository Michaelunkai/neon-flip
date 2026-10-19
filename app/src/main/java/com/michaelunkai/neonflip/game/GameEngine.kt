package com.michaelunkai.neonflip.game

import com.michaelunkai.neonflip.retention.DifficultyTuner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Neon Flip core simulation. Fixed-timestep (120 Hz) updates driven by an internal
 * accumulator; the view feeds real frame deltas and renders with interpolation.
 * Pure Kotlin + a couple of retention hooks (DifficultyTuner); no Android UI here.
 */
class GameEngine {

    enum class State { MENU, RUNNING, DYING, OVER }
    enum class Stat { COIN, GATE, NEAR_MISS, FLIP }

    interface Events {
        fun onGameOver(r: RunResult)
        fun onStat(stat: Stat, amount: Int)
        fun onCombo(level: Int)
    }

    class RunResult(
        val score: Int,
        val coins: Int,
        val maxCombo: Int,
        val nearMisses: Int,
        val gates: Int,
        val flips: Int,
        val distanceM: Int,
        val durationSec: Float,
        val wasRevived: Boolean
    )

    enum class ObstacleType { SPIKE_FLOOR, SPIKE_CEIL, BLOCK, MOVER }

    class Obstacle(
        val type: ObstacleType,
        var x: Float,           // center x (px)
        val w: Float, val h: Float,
        var yTop: Float,        // blocks: top edge; spikes unused
        val onFloor: Boolean,   // spikes/blocks anchored side
        var phase: Float = 0f,  // mover
        var passed: Boolean = false
    )

    class Coin(var x: Float, var y: Float, var baseY: Float, var t: Float, var magnet: Boolean)
    class Gate(var x: Float, val onFloor: Boolean, var passed: Boolean, var pulse: Float = 0f)

    class Floater(val text: String, var x: Float, var y: Float, val life: Float,
                  var t: Float = 0f, val color: Int)

    // ------------------------------------------------------------------ world state

    var events: Events? = null
    var W = 1080f; var H = 1920f

    var state = State.MENU
        private set
    var paused = false

    // Ball
    var ballY = 0f; var ballPrevY = 0f; var ballVy = 0f
    var gravSign = 1f
    var invulnT = 0f
    var reviveUsed = false

    // Entities
    val obstacles = ArrayList<Obstacle>(24)
    val coins = ArrayList<Coin>(32)
    val gates = ArrayList<Gate>(6)
    val floaters = ArrayList<Floater>(8)
    val particles = Particles()

    // Run stats
    var scoreF = 0f; var runCoins = 0
    var combo = 1; var comboT = 0f; var maxCombo = 1
    var nearMisses = 0; var gatesPassed = 0; var flips = 0
    var distancePx = 0f; var runTime = 0f
    var lastResult: RunResult? = null
        private set

    // Juice (real-time)
    var timeScale = 1f
    var slowmoT = 0f
    var shake = 0f
    var flash = 0f
    var flashColor = 0
    var shakeX = 0f; var shakeY = 0f
    var time = 0f              // total real seconds since surface start (for pulses)

    // Internals
    private var acc = 0f
    private val rng = Random(System.nanoTime())
    private var spawnPx = 0f
    private var menuFlipT = 0f
    private var deathT = 0f
    private var overFired = false

    val floorY: Float get() = H * GameBalance.FLOOR_Y
    val ceilY: Float get() = H * GameBalance.CEIL_Y
    val ballX: Float get() = W * GameBalance.BALL_X_FRAC
    val ballR: Float get() = H * GameBalance.BALL_RADIUS
    val midY: Float get() = (floorY + ceilY) / 2f

    val difficulty: Float
        get() = (distancePx / H / GameBalance.DIFF_FULL_SCREENS).coerceIn(0f, 1f)

    val ramp: Float
        get() = 1f + GameBalance.MAX_RAMP * min(1f, distancePx / H / GameBalance.SPEED_RAMP_SCREENS)

    val tunerScale: Float get() = max(0.85f, DifficultyTuner.currentDifficultyScale())

    var speedCur = 0f
        private set

    fun renderAlpha(): Float = (acc / GameBalance.FIXED_DT).coerceIn(0f, 1f)

    fun onSurface(w: Int, h: Int) {
        W = w.toFloat(); H = h.toFloat()
        if (state == State.MENU) ballY = midY
    }

    // ------------------------------------------------------------------ control

    fun startRun() {
        obstacles.clear(); coins.clear(); gates.clear(); floaters.clear()
        particles.clear()
        scoreF = 0f; runCoins = 0; combo = 1; comboT = 0f; maxCombo = 1
        nearMisses = 0; gatesPassed = 0; flips = 0
        distancePx = 0f; runTime = 0f
        ballY = midY; ballPrevY = midY; ballVy = 0f; gravSign = 1f
        invulnT = 0f; reviveUsed = false
        timeScale = 1f; slowmoT = 0f; shake = 0f; flash = 0f
        spawnPx = H * 1.2f
        deathT = 0f; overFired = false
        state = State.RUNNING
    }

    fun resetToMenu() {
        obstacles.clear(); coins.clear(); gates.clear(); floaters.clear()
        particles.clear()
        state = State.MENU
        ballY = midY; ballPrevY = midY; ballVy = 0f
        timeScale = 1f; shake = 0f; flash = 0f; slowmoT = 0f
    }

    /** Single input: tap anywhere -> gravity flip. */
    fun flip() {
        if (state != State.RUNNING || paused) return
        gravSign = -gravSign
        ballVy *= GameBalance.FLIP_VY_DAMP
        flips++
        events?.onStat(Stat.FLIP, 1)
        particles.flipPuff(ballX, ballY, -gravSign, H, 0xFF9BE8FF.toInt())
        AudioSynth.play(AudioSynth.Sfx.FLIP, 1f + rng.nextFloat() * 0.05f)
        Haptics.flip()
    }

    fun revive() {
        if (state != State.DYING && state != State.OVER) return
        val bx = ballX
        obstacles.removeAll { it.x > bx - W * 0.15f && it.x < bx + W * 1.1f }
        gates.removeAll { it.x > bx - W * 0.15f && it.x < bx + W * 1.1f }
        coins.removeAll { it.x > bx - W * 0.15f && it.x < bx + W * 1.1f }
        state = State.RUNNING
        reviveUsed = true
        ballVy = 0f; gravSign = 1f   // clean restart of the fall, no stale death velocity
        invulnT = GameBalance.REVIVE_INVULN
        timeScale = 1f; slowmoT = 0f; shake = 0f; flash = 0f
        overFired = false
        particles.sparks(ballX, ballY, H, 0xFFB4FF39.toInt(), 22)
        AudioSynth.play(AudioSynth.Sfx.DROP)
    }

    // ------------------------------------------------------------------ main update

    fun update(realDt: Float) {
        val dt = min(realDt, GameBalance.MAX_FRAME_DT)
        time += dt

        // --- real-time juice ---
        slowmoT = max(0f, slowmoT - dt)
        val target = when {
            state == State.DYING || state == State.OVER -> GameBalance.DEATH_SLOWMO
            slowmoT > 0f -> GameBalance.SLOWMO_SCALE
            else -> 1f
        }
        timeScale += (target - timeScale) * min(1f, dt * 9f)

        shake *= exp(-GameBalance.SHAKE_DECAY * dt)
        if (shake > 0.002f) {
            shakeX = (rng.nextFloat() - 0.5f) * 2f * shake * H * GameBalance.SHAKE_AMP
            shakeY = (rng.nextFloat() - 0.5f) * 2f * shake * H * GameBalance.SHAKE_AMP
        } else { shakeX = 0f; shakeY = 0f }

        flash = max(0f, flash - dt * 2.6f)

        // floaters rise in real time
        val fit = floaters.iterator()
        while (fit.hasNext()) {
            val f = fit.next()
            f.t += dt
            f.y -= H * 0.06f * dt
            if (f.t >= f.life) fit.remove()
        }

        if (paused) return

        // --- fixed-step sim ---
        acc += dt * timeScale
        var steps = 0
        while (acc >= GameBalance.FIXED_DT && steps < 8) {
            step(GameBalance.FIXED_DT)
            acc -= GameBalance.FIXED_DT
            steps++
        }
        if (steps >= 8) acc = 0f

        particles.update(dt)
    }

    // ------------------------------------------------------------------ fixed step

    private fun step(dt: Float) {
        when (state) {
            State.MENU -> stepMenu(dt)
            State.RUNNING -> stepRun(dt)
            State.DYING, State.OVER -> {
                deathT += dt
                if (state == State.DYING && deathT >= GameBalance.DEATH_T && !overFired) {
                    overFired = true
                    state = State.OVER
                    events?.onGameOver(lastResult ?: snapshotResult())
                }
                moveEntities(dt, attract = true)
            }
        }
    }

    private fun stepMenu(dt: Float) {
        val speed = H * GameBalance.MENU_SPEED
        distancePx += speed * dt
        // attract-mode autopilot bounce
        menuFlipT -= dt
        if (menuFlipT <= 0f) {
            gravSign = -gravSign
            menuFlipT = 0.9f + rng.nextFloat() * 0.8f
        }
        ballPrevY = ballY
        ballVy += GameBalance.GRAVITY * H * gravSign * dt
        ballY += ballVy * dt
        clampBall()
        moveEntities(dt, attract = true)
        if (rng.nextFloat() < dt * 2f) {
            particles.ambientDust(W + 40f, ceilY + rng.nextFloat() * (floorY - ceilY), H,
                0xFF3A5C9E.toInt())
        }
    }

    private fun stepRun(dt: Float) {
        runTime += dt
        invulnT = max(0f, invulnT - dt)
        comboT = max(0f, comboT - dt)
        if (comboT <= 0f && combo > 1) combo = 1

        val speed = H * GameBalance.BASE_SPEED * ramp * tunerScale
        speedCur = speed
        val dx = speed * dt
        distancePx += dx
        scoreF += (dx / H) * GameBalance.SCORE_PER_SCREEN * combo

        // ball physics
        ballPrevY = ballY
        ballVy += GameBalance.GRAVITY * H * gravSign * dt
        ballY += ballVy * dt
        clampBall()

        // trail particles
        val skinGlow = com.michaelunkai.neonflip.retention.Economy.equippedSkin().glow
        particles.trail(ballX, ballY, H, skinGlow)

        // spawning
        spawnPx -= dx
        if (spawnPx <= 0f) spawnPattern()

        moveEntities(dt, attract = false)
        collideAndPickup()
    }

    private fun clampBall() {
        val r = ballR
        val f = floorY - r
        val c = ceilY + r
        if (ballY > f) {
            ballY = f
            if (ballVy > 0f) ballVy = ballVy * GameBalance.WALL_BOUNCE
        } else if (ballY < c) {
            ballY = c
            if (ballVy < 0f) ballVy = ballVy * GameBalance.WALL_BOUNCE
        }
    }

    private fun moveEntities(dt: Float, attract: Boolean) {
        val speed = when (state) {
            State.MENU -> H * GameBalance.MENU_SPEED
            else -> speedCur // slow-mo already applied via the accumulator
        }
        val dx = speed * dt
        val bx = ballX

        val oi = obstacles.iterator()
        while (oi.hasNext()) {
            val o = oi.next()
            o.x -= dx
            if (o.type == ObstacleType.MOVER) {
                o.phase += dt * 2.1f
                val amp = (floorY - ceilY) / 2f - o.h / 2f - H * 0.02f
                o.yTop = midY + amp * sin(o.phase) - o.h / 2f
            }
            if (o.x < -o.w) oi.remove()
        }
        val ci = coins.iterator()
        while (ci.hasNext()) {
            val cn = ci.next()
            cn.x -= dx
            cn.t += dt
            if (attract) {
                if (cn.x < -40f) ci.remove()
                continue
            }
            // bob + magnet
            if (!cn.magnet) cn.y = cn.baseY + sin(cn.t * 3.2f) * H * 0.006f
            val ddx = ballX - cn.x; val ddy = ballY - cn.y
            val d = sqrt(ddx * ddx + ddy * ddy)
            if (!cn.magnet && d < H * GameBalance.MAGNET_R) cn.magnet = true
            if (cn.magnet) {
                val k = min(1f, dt * 9f)
                cn.x += ddx * k
                cn.y += ddy * k
                if (rng.nextFloat() < dt * 20f) particles.coinBurst(cn.x, cn.y, H * 0.3f,
                    0xFFFFD54A.toInt())
            }
            if (cn.x < -40f) ci.remove()
        }
        val gi = gates.iterator()
        while (gi.hasNext()) {
            val g = gi.next()
            g.x -= dx
            g.pulse = max(0f, g.pulse - dt * 2f)
            if (g.x < -80f) gi.remove()
        }
        if (attract) {
            // ball follows world scroll in menu (stays put on screen)
        } else {
            // spike/block crossing bookkeeping (near-miss detection)
            for (o in obstacles) {
                if (!o.passed && o.x + o.w / 2f < bx) {
                    o.passed = true
                    val clearance = clearanceTo(o) - ballR * GameBalance.HITBOX_FORGIVE
                    if (clearance in 0f..(H * GameBalance.NEAR_MISS_MARGIN)) {
                        nearMisses++
                        events?.onStat(Stat.NEAR_MISS, 1)
                        scoreF += GameBalance.NEAR_MISS_BONUS
                        slowmoT = GameBalance.SLOWMO_DUR
                        particles.sparks(bx, ballY, H, 0xFF8FF6FF.toInt(), 16)
                        floaters.add(Floater("NEAR MISS", bx + H * 0.02f, ballY - H * 0.05f,
                            0.8f, 0f, 0xFF8FF6FF.toInt()))
                        AudioSynth.play(AudioSynth.Sfx.NEAR)
                        Haptics.nearMiss()
                        if (combo < GameBalance.COMBO_MAX) {
                            combo++
                            comboT = GameBalance.COMBO_HOLD_S
                            maxCombo = max(maxCombo, combo)
                            events?.onCombo(combo)
                        }
                    }
                }
            }
            for (g in gates) {
                if (!g.passed && g.x <= bx) {
                    g.passed = true
                    g.pulse = 1f
                    val onGateSide = if (g.onFloor) ballY > midY else ballY < midY
                    if (onGateSide) {
                        gatesPassed++
                        if (combo < GameBalance.COMBO_MAX) combo++
                        comboT = GameBalance.COMBO_HOLD_S
                        maxCombo = max(maxCombo, combo)
                        events?.onStat(Stat.GATE, 1)
                        events?.onCombo(combo)
                        particles.gateRing(bx, ballY, H, 0xFFFFD54A.toInt())
                        floaters.add(Floater("x$combo", bx + H * 0.03f, ballY - H * 0.06f,
                            0.7f, 0f, 0xFFFFD54A.toInt()))
                        AudioSynth.play(AudioSynth.Sfx.GATE)
                        Haptics.comboUp()
                    } else if (combo > 1) {
                        combo = 1
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ collision

    /** Signed clearance from ball circle surface to obstacle body (negative = penetrating). */
    private fun clearanceTo(o: Obstacle): Float {
        val bx = ballX; val by = ballY
        return when (o.type) {
            ObstacleType.SPIKE_FLOOR, ObstacleType.SPIKE_CEIL -> {
                val baseY = if (o.onFloor) floorY else ceilY
                val apexY = if (o.onFloor) baseY - o.h else baseY + o.h
                val ax = o.x; val ay = apexY
                val l = distToSeg(bx, by, o.x - o.w / 2f, baseY, o.x, ay)
                val r2 = distToSeg(bx, by, o.x, ay, o.x + o.w / 2f, baseY)
                val b = abs(by - baseY)
                min(l, min(r2, b))
            }
            ObstacleType.BLOCK, ObstacleType.MOVER -> {
                val cx = bx.coerceIn(o.x - o.w / 2f, o.x + o.w / 2f)
                val cy = by.coerceIn(o.yTop, o.yTop + o.h)
                sqrt((bx - cx) * (bx - cx) + (by - cy) * (by - cy))
            }
        }
    }

    private fun distToSeg(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        val len2 = dx * dx + dy * dy
        val t = if (len2 <= 0f) 0f else ((px - x1) * dx + (py - y1) * dy) / len2
        val tc = t.coerceIn(0f, 1f)
        val cx = x1 + tc * dx; val cy = y1 + tc * dy
        return sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
    }

    private fun collideAndPickup() {
        val r = ballR
        if (invulnT <= 0f) {
            for (o in obstacles) {
                if (abs(o.x - ballX) > o.w / 2f + r + H * 0.02f) continue
                // forgiving hitbox: deaths must never look like they grazed
                if (clearanceTo(o) < r * GameBalance.HITBOX_FORGIVE) { die(); return }
            }
        }
        val cr = H * GameBalance.COIN_R
        val ci = coins.iterator()
        while (ci.hasNext()) {
            val cn = ci.next()
            val dx = ballX - cn.x; val dy = ballY - cn.y
            val d2 = dx * dx + dy * dy
            val rad = r + cr
            if (d2 < rad * rad) {
                ci.remove()
                runCoins++
                scoreF += GameBalance.COIN_SCORE * combo
                comboT = GameBalance.COMBO_HOLD_S
                particles.coinBurst(cn.x, cn.y, H, 0xFFFFD54A.toInt())
                AudioSynth.play(AudioSynth.Sfx.COIN, 1f + 0.05f * min(combo, 8))
                Haptics.coin()
                events?.onStat(Stat.COIN, 1)
            }
        }
    }

    private fun die() {
        state = State.DYING
        deathT = 0f
        shake = 1f
        flash = 1f
        flashColor = 0xFFFF3355.toInt()
        particles.explosion(ballX, ballY, H, 0xFFFF2E5A.toInt(), 0xFFFFD54A.toInt())
        AudioSynth.play(AudioSynth.Sfx.DEATH)
        Haptics.death()
        lastResult = snapshotResult()
    }

    private fun snapshotResult(): RunResult = RunResult(
        score = scoreF.toInt(),
        coins = runCoins,
        maxCombo = maxCombo,
        nearMisses = nearMisses,
        gates = gatesPassed,
        flips = flips,
        distanceM = (distancePx / H * GameBalance.SCORE_PER_SCREEN).toInt(),
        durationSec = runTime,
        wasRevived = reviveUsed
    )

    // ------------------------------------------------------------------ spawning

    private fun spawnPattern() {
        val x0 = W + H * 0.12f
        val d = difficulty
        val tier = when {
            d < 0.18f -> 0
            d < 0.42f -> 1
            d < 0.70f -> 2
            else -> 3
        }
        val pool = patterns[tier]
        val pattern = pool[rng.nextInt(pool.size)]
        val width = pattern(x0)
        val gapNear = H * GameBalance.GAP_NEAR_FRAC
        val gapFar = H * GameBalance.GAP_FAR_FRAC
        val eased = 1f / tunerScale // easing opens gaps a little
        spawnPx = width + (gapNear + (gapFar - gapNear) * d) * eased *
                (0.9f + rng.nextFloat() * 0.3f)
    }

    /** Each pattern returns its horizontal footprint in px. */
    private val patterns: Array<Array<(Float) -> Float>> = arrayOf(
        arrayOf(::pSpikeFloor, ::pSpikeCeil, ::pBlockLow, ::pCoinRun),
        arrayOf(::pSpikeFloor, ::pSpikeCeil, ::pBlockLow, ::pBlockHigh, ::pDoubleFloor,
            ::pDoubleCeil, ::pGateEasy, ::pCoinRun),
        arrayOf(::pDoubleFloor, ::pDoubleCeil, ::pStairs, ::pMover, ::pBlockLow,
            ::pGateRisky, ::pCoinArc),
        arrayOf(::pStairs, ::pMover, ::pStairsHigh, ::pGateRisky, ::pMoverWide, ::pCoinArc)
    )
    private fun pSpikeFloor(x0: Float): Float {
        addSpike(x0, true)
        maybeCoinsOver(x0, 1)
        return H * GameBalance.SPIKE_W
    }

    private fun pSpikeCeil(x0: Float): Float {
        addSpike(x0, false)
        maybeCoinsOver(x0, 1)
        return H * GameBalance.SPIKE_W
    }

    private fun pDoubleFloor(x0: Float): Float {
        val w = H * GameBalance.SPIKE_W
        addSpike(x0 - w * 0.7f, true)
        addSpike(x0 + w * 0.7f, true)
        maybeCoinsOver(x0, 2)
        return w * 2.4f
    }

    private fun pDoubleCeil(x0: Float): Float {
        val w = H * GameBalance.SPIKE_W
        addSpike(x0 - w * 0.7f, false)
        addSpike(x0 + w * 0.7f, false)
        maybeCoinsOver(x0, 2)
        return w * 2.4f
    }

    private fun pBlockLow(x0: Float): Float {
        obstacles.add(Obstacle(ObstacleType.BLOCK, x0, H * GameBalance.BLOCK_W,
            H * GameBalance.BLOCK_H, floorY - H * GameBalance.BLOCK_H, true))
        maybeCoinsOver(x0, 1)
        return H * GameBalance.BLOCK_W
    }

    private fun pBlockHigh(x0: Float): Float {
        obstacles.add(Obstacle(ObstacleType.BLOCK, x0, H * GameBalance.BLOCK_W,
            H * GameBalance.BLOCK_H, ceilY, false))
        maybeCoinsOver(x0, 1)
        return H * GameBalance.BLOCK_W
    }

    private fun pStairs(x0: Float): Float {
        val w = H * GameBalance.SPIKE_W
        addSpike(x0, true)
        addSpike(x0 + w * 2.2f, false)
        maybeCoinsOver(x0 + w, 2)
        return w * 3.4f
    }

    private fun pStairsHigh(x0: Float): Float {
        val w = H * GameBalance.SPIKE_W
        addSpike(x0, false)
        addSpike(x0 + w * 2.0f, true)
        addSpike(x0 + w * 4.0f, false)
        maybeCoinsOver(x0 + w * 2f, 2)
        return w * 5.2f
    }

    private fun pMover(x0: Float): Float {
        obstacles.add(Obstacle(ObstacleType.MOVER, x0, H * GameBalance.BLOCK_W,
            H * GameBalance.BLOCK_H, midY, true, phase = rng.nextFloat() * 2f * PI.toFloat()))
        return H * GameBalance.BLOCK_W * 1.4f
    }

    private fun pMoverWide(x0: Float): Float {
        obstacles.add(Obstacle(ObstacleType.MOVER, x0, H * GameBalance.BLOCK_WIDE_W,
            H * GameBalance.BLOCK_H * 0.85f, midY, true, phase = rng.nextFloat() * 2f * PI.toFloat()))
        return H * GameBalance.BLOCK_WIDE_W * 1.6f
    }

    private fun pGateEasy(x0: Float): Float {
        gates.add(Gate(x0, rng.nextBoolean(), false))
        if (rng.nextBoolean()) addSpike(x0 + H * 0.28f, true)
        return H * 0.34f
    }

    private fun pGateRisky(x0: Float): Float {
        gates.add(Gate(x0, rng.nextBoolean(), false))
        addSpike(x0 + H * 0.22f, true)
        addSpike(x0 + H * 0.22f, false)
        maybeCoinsOver(x0 + H * 0.1f, 2)
        return H * 0.42f
    }

    private fun pCoinRun(x0: Float): Float {
        val n = 4 + rng.nextInt(4)
        val y = ceilY + (floorY - ceilY) * (0.25f + rng.nextFloat() * 0.5f)
        for (i in 0 until n) coins.add(Coin(x0 + i * H * 0.055f, y, y,
            rng.nextFloat() * 3f, false))
        return n * H * 0.055f
    }

    private fun pCoinArc(x0: Float): Float {
        val n = 6
        for (i in 0 until n) {
            val t = i / (n - 1).toFloat()
            val y = floorY - (floorY - ceilY) * 0.62f * sin(t * PI.toFloat())
            coins.add(Coin(x0 + i * H * 0.05f, y, y, rng.nextFloat() * 3f, false))
        }
        return n * H * 0.05f
    }

    private fun addSpike(x: Float, onFloor: Boolean) {
        obstacles.add(Obstacle(ObstacleType.SPIKE_FLOOR.takeIf { onFloor }
            ?: ObstacleType.SPIKE_CEIL,
            x, H * GameBalance.SPIKE_W, H * GameBalance.SPIKE_H, 0f, onFloor))
    }

    private fun maybeCoinsOver(x: Float, count: Int) {
        if (rng.nextFloat() > 0.55f) return
        val y = midY + (rng.nextFloat() - 0.5f) * H * 0.16f
        for (i in 0 until count) {
            coins.add(Coin(x + (i - (count - 1) / 2f) * H * 0.055f, y, y,
                rng.nextFloat() * 3f, false))
        }
    }
}
