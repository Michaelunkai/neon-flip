package com.michaelunkai.neonflip.retention

/**
 * Daily streak with escalating rewards and streak-freeze rescue.
 * Day math is epoch-day based; a day with zero runs breaks the streak unless a
 * freeze is consumed automatically at open time (rescue).
 */
object StreakManager {

    class StreakInfo(
        val streak: Int,
        val broke: Boolean,
        val rescuedByFreeze: Boolean,
        val rewardAvailable: Boolean,
        val rewardAmount: Int
    )

    /** Reward escalates with streak day; caps so late streaks stay profitable but bounded. */
    fun rewardForDay(streakDay: Int): Int = (25 + (streakDay - 1) * 20).coerceAtMost(150)

    /** The streak day that "today" counts as, whether or not the first run happened yet. */
    fun todayStreakDay(): Int {
        val d = SaveStore.data
        val s = if (d.lastPlayEpochDay == SaveStore.epochDay()) d.streakCurrent else d.streakCurrent + 1
        return s.coerceAtLeast(1)
    }

    /** Call once per app open (from MainActivity.onCreate). */
    fun onAppOpen(): StreakInfo {
        val today = SaveStore.epochDay()
        val d = SaveStore.data
        var broke = false
        var rescued = false
        SaveStore.edit {
            it.lastOpenEpochDay = today
            if (it.lastPlayEpochDay in 0 until today - 1) {
                // Gap detected: try freeze rescue, else break streak.
                if (it.freezes > 0) {
                    it.freezes -= 1
                    it.lastPlayEpochDay = today - 1 // stitch the gap
                    rescued = true
                } else {
                    it.streakCurrent = 0
                    broke = true
                }
            }
            if (it.streakCurrent > it.streakBest) it.streakBest = it.streakCurrent
        }
        val streak = SaveStore.data.streakCurrent
        val rewardAvailable = SaveStore.data.streakRewardClaimedDay != today
        return StreakInfo(streak, broke, rescued, rewardAvailable, rewardForDay(todayStreakDay()))
    }

    /** Call at the moment the first run of the day starts (extends the streak). */
    fun onRunStartedToday() {
        val today = SaveStore.epochDay()
        SaveStore.edit {
            if (it.lastPlayEpochDay != today) {
                it.streakCurrent = if (it.lastPlayEpochDay == today - 1) it.streakCurrent + 1 else 1
                if (it.streakCurrent > it.streakBest) it.streakBest = it.streakCurrent
                it.lastPlayEpochDay = today
            }
        }
    }

    /** Claim today's daily reward; returns coins granted (0 if already claimed). */
    fun claimReward(): Int {
        val today = SaveStore.epochDay()
        if (SaveStore.data.streakRewardClaimedDay == today) return 0
        val amount = rewardForDay(todayStreakDay())
        SaveStore.edit { it.streakRewardClaimedDay = today }
        Economy.addCoins(amount)
        return amount
    }

    fun freezes(): Int = SaveStore.data.freezes

    /** Buy a streak freeze for soft coins (price from RevenueConfig). */
    fun buyFreeze(): Boolean {
        if (!Economy.spendCoins(Economy.streakFreezeCost())) return false
        SaveStore.edit { it.freezes += 1 }
        return true
    }
}
