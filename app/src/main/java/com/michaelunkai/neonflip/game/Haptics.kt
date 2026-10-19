package com.michaelunkai.neonflip.game

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.michaelunkai.neonflip.retention.SaveStore

/**
 * Haptic feedback helper. Every game feel-event maps to a short, distinct vibration.
 * All calls respect the persisted haptics setting; nothing here can throw on any API level.
 */
object Haptics {

    private var vibrator: Vibrator? = null

    fun init(ctx: Context) {
        vibrator = try {
            if (Build.VERSION.SDK_INT >= 31) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (t: Throwable) { null }
    }

    private fun enabled(): Boolean = SaveStore.data.hapticsOn

    private fun oneShot(ms: Long, amp: Int) {
        val v = vibrator ?: return
        if (!enabled()) return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, amp.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION") v.vibrate(ms)
            }
        } catch (_: Throwable) {}
    }

    private fun click() {
        val v = vibrator ?: return
        if (!enabled()) return
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else oneShot(12, 80)
        } catch (_: Throwable) {}
    }

    fun flip() = oneShot(18, 140)
    fun coin() = click()
    fun nearMiss() = oneShot(35, 180)
    fun comboUp() = oneShot(28, 160)
    fun uiTap() = click()
    fun purchase() = oneShot(45, 200)
    fun death() {
        oneShot(60, 255)
        // double-thump for weight
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ oneShot(80, 220) }, 70)
    }
}
