package com.michaelunkai.neonflip.retention

import kotlin.random.Random

/**
 * Variable-ratio bonus drops (slot-machine schedule): after a random number of
 * game-overs (2..6), the results screen pays a mystery coin bonus. The unpredictable
 * schedule is the retention hook; amounts vary too.
 */
object BonusDrops {

    private const val MIN_GAMEOVERS = 2
    private const val MAX_GAMEOVERS = 6
    private const val MIN_PAYOUT = 30
    private const val MAX_PAYOUT = 120

    /** Call once per game over. Returns bonus coins (0 = no drop this time). */
    fun onGameOver(): Int {
        var payout = 0
        SaveStore.edit {
            it.gameoversUntilDrop -= 1
            if (it.gameoversUntilDrop <= 0) {
                payout = Random.nextInt(MIN_PAYOUT, MAX_PAYOUT + 1)
                it.gameoversUntilDrop = Random.nextInt(MIN_GAMEOVERS, MAX_GAMEOVERS + 1)
                it.totalDrops += 1
            }
        }
        if (payout > 0) Economy.addCoins(payout)
        return payout
    }

    fun dropsSoFar(): Int = SaveStore.data.totalDrops
}
