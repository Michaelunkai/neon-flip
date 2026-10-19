package com.michaelunkai.neonflip.retention

import com.michaelunkai.neonflip.monetization.RevenueConfig

/**
 * Near-win loss-easing hook called by the game core.
 * Detects loss streaks and eases procedural difficulty so a frustrated player
 * gets a win shortly after (capped by RevenueConfig.NEAR_WIN_EASE_FACTOR).
 * A "loss" = a run that lands under ~1/3 of the player's best (or under 150 pts
 * when there is no meaningful best yet).
 */
object DifficultyTuner {

    fun onRunEnd(score: Int, best: Int) {
        val threshold = if (best >= 300) best / 3 else 150
        val isLoss = score < threshold
        SaveStore.edit {
            if (isLoss) it.lossStreak = (it.lossStreak + 1).coerceAtMost(LOSS_STREAK_HARD_CAP)
            else it.lossStreak = 0
        }
    }

    /** 0..1 — how deep the player is inside a loss streak. */
    fun easeLevel(): Float {
        val ls = SaveStore.data.lossStreak
        if (ls <= 0) return 0f
        return (ls.toFloat() / RevenueConfig.LOSS_STREAK_TRIGGER).coerceIn(0f, 1f)
    }

    /** Multiplier applied to procedural difficulty (speed ramp + spawn density). */
    fun currentDifficultyScale(): Float =
        1f - RevenueConfig.NEAR_WIN_EASE_FACTOR * easeLevel()

    fun lossStreak(): Int = SaveStore.data.lossStreak

    private const val LOSS_STREAK_HARD_CAP = 8
}
