package com.michaelunkai.neonflip.game

import android.annotation.SuppressLint
import android.content.Context
import android.view.Choreographer
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Game surface: Choreographer-driven rendering loop on a hardware-accelerated SurfaceView.
 * The engine owns the fixed timestep internally; the view just feeds frame deltas and
 * renders with interpolation alpha. Tap = gravity flip.
 */
@SuppressLint("ViewConstructor")
class GameView(
    context: Context,
    val engine: GameEngine
) : SurfaceView(context, null, 0), SurfaceHolder.Callback, Choreographer.FrameCallback {

    private val renderer = Renderer(engine)
    private var running = false
    private var lastNanos = 0L
    private var surfaceReady = false

    init {
        holder.addCallback(this)
        setFocusable(true)
        isFocusableInTouchMode = true
    }

    // ------------------------------------------------------------------ loop

    private fun tick(frameTimeNanos: Long) {
        if (!running || !surfaceReady) return
        val dtSec = if (lastNanos == 0L) GameBalance.FIXED_DT
        else (frameTimeNanos - lastNanos) / 1_000_000_000f
        lastNanos = frameTimeNanos

        engine.update(dtSec)

        val surface: Surface = holder.surface
        if (surface.isValid) {
            val canvas = try {
                holder.lockHardwareCanvas()
            } catch (t: Throwable) {
                try { holder.lockCanvas() } catch (_: Throwable) { null }
            }
            if (canvas != null) {
                try {
                    renderer.draw(canvas, engine.renderAlpha())
                } finally {
                    try { holder.unlockCanvasAndPost(canvas) } catch (_: Throwable) {}
                }
            }
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) = tick(frameTimeNanos)

    fun resume() {
        if (running) return
        running = true
        lastNanos = 0L
        val ch = Choreographer.getInstance()
        ch.removeFrameCallback(this)
        ch.postFrameCallback(this)
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    // ------------------------------------------------------------------ input

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN ||
            event.action == MotionEvent.ACTION_POINTER_DOWN) {
            engine.flip()
            return true
        }
        return super.onTouchEvent(event)
    }

    // ------------------------------------------------------------------ surface

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        resume()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        engine.onSurface(width, height)
        renderer.onSizeChanged(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        pause()
    }
}
