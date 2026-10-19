package com.michaelunkai.neonflip.retention

/**
 * Comeback gift for lapsed players: absent 3+ days -> escalating coin gift
 * waiting on the menu. One gift per lapsed gap.
 */
object ComebackGift {

    private const val LAPSE_DAYS = 3
    private const val BASE_GIFT = 75
    private const val PER_EXTRA_DAY = 25
    private const val MAX_EXTRA_DAYS = 4

    /** Returns gift amount if one was just granted (0 = none), consumed immediately. */
    fun onAppOpen(): Int {
        val today = SaveStore.epochDay()
        val last = SaveStore.data.lastOpenEpochDay
        if (last in 0 until today - LAPSE_DAYS + 1 || last == -1L) {
            if (last == -1L) return 0 // brand-new player: no gift, they have onboarding
            val lapsed = (today - last).toInt().coerceAtLeast(LAPSE_DAYS)
            val amount = BASE_GIFT + (lapsed - LAPSE_DAYS).coerceAtMost(MAX_EXTRA_DAYS) * PER_EXTRA_DAY
            Economy.addCoins(amount)
            return amount
        }
        return 0
    }
}
