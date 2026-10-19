package com.michaelunkai.neonflip.ui

import android.content.Context
import android.graphics.Canvas
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import com.michaelunkai.neonflip.game.AudioSynth
import com.michaelunkai.neonflip.game.GameEngine
import com.michaelunkai.neonflip.game.Haptics

/**
 * Fullscreen transparent overlay that renders the active UI screen (menu, results,
 * shop, ranks, settings, pause) on top of the live game surface, with animated
 * transitions and toast-style banners. Owns its own 60fps invalidate loop while visible.
 */
class NeonOverlayView(
    context: Context,
    private val engine: GameEngine,
    private val nav: UiNav
) : View(context, null, 0), Choreographer.FrameCallback {

    private val screens: Map<ScreenId, Screen> = mapOf(
        ScreenId.MENU to MenuScreen(context, nav),
        ScreenId.RESULTS to ResultsScreen(context, nav),
        ScreenId.SHOP to ShopScreen(context, nav),
        ScreenId.RANKS to RankScreen(context, nav),
        ScreenId.SETTINGS to SettingsScreen(context, nav),
        ScreenId.PAUSE to PauseScreen(context, nav)
    )

    var current: Screen? = null
        private set

    private class Banner(val text: String, val color: Int, val bornNs: Long)

    private val banners = ArrayList<Banner>(4)

    val menu: MenuScreen get() = screens.getValue(ScreenId.MENU) as MenuScreen
    val results: ResultsScreen get() = screens.getValue(ScreenId.RESULTS) as ResultsScreen

    // ------------------------------------------------------------------ state

    fun show(id: ScreenId) {
        val s = screens.getValue(id)
        if (id == ScreenId.MENU) menu.giftAmount = 0 // consumed gifts cleared by nav first
        current = s
        s.show()
        visibility = VISIBLE
        isClickable = true
        startTick()
    }

    fun showResults(r: GameEngine.RunResult, newBest: Boolean, bonusCoins: Int) {
        results.showResult(r, newBest, bonusCoins)
        current = results
        visibility = VISIBLE
        isClickable = true
        startTick()
    }

    fun markDoubled() = results.markDoubled()

    fun hide() {
        current = null
        visibility = GONE
        isClickable = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    fun banner(text: String, color: Int) {
        banners.add(Banner(text, color, System.nanoTime()))
        if (banners.size > 4) banners.removeAt(0)
        if (current == null) { visibility = VISIBLE; isClickable = false }
        startTick()
    }

    private fun startTick() {
        val ch = Choreographer.getInstance()
        ch.removeFrameCallback(this)
        ch.postFrameCallback(this)
    }

    /** @return true if consumed (activity back should not exit). */
    fun onBack(): Boolean {
        return when (current?.id) {
            ScreenId.SHOP, ScreenId.RANKS, ScreenId.SETTINGS -> { nav.open(ScreenId.MENU); true }
            ScreenId.PAUSE -> { nav.resumeGame(); true }
            ScreenId.RESULTS -> { nav.toMenu(); true }
            else -> false
        }
    }

    // ------------------------------------------------------------------ loop + draw

    override fun doFrame(frameTimeNanos: Long) {
        if (visibility != VISIBLE) return
        postInvalidateOnAnimation()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width; val h = height
        val cur = current
        if (cur != null) {
            val tSec = (System.nanoTime() - cur.shownAtNs) / 1_000_000_000f
            cur.draw(canvas, w, h, tSec)
        }
        drawBanners(canvas, w, h)
        if (cur == null && banners.isEmpty() && visibility == VISIBLE) {
            visibility = GONE // banner-only display finished
        }
    }

    private fun drawBanners(c: Canvas, w: Int, h: Int) {
        if (banners.isEmpty()) return
        val now = System.nanoTime()
        val it = banners.iterator()
        var index = 0
        while (it.hasNext()) {
            val b = it.next()
            val t = (now - b.bornNs) / 1_000_000_000f
            if (t > 2.4f) { it.remove(); continue }
            val alphaIn = (t / 0.18f).coerceIn(0f, 1f)
            val alphaOut = ((2.4f - t) / 0.4f).coerceIn(0f, 1f)
            val alpha = min(alphaIn, alphaOut)
            val y = h * (0.16f + index * 0.055f)
            NeonUi.glowTitle(c, b.text, w / 2f, y, h * 0.024f,
                b.color, b.color)
            index++
        }
    }

    // ------------------------------------------------------------------ input

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event)
        val cur = current ?: return false
        val consumed = cur.tap(event.x, event.y)
        if (consumed) {
            AudioSynth.play(AudioSynth.Sfx.UI)
            Haptics.uiTap()
        }
        return consumed || super.onTouchEvent(event)
    }

    private fun min(a: Float, b: Float): Float = if (a < b) a else b
}
