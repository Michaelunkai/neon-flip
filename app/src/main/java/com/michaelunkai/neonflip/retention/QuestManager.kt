package com.michaelunkai.neonflip.retention

import com.michaelunkai.neonflip.retention.SaveStore.QuestRow
import kotlin.random.Random

/**
 * Daily quests: 3 per day, deterministic pick from a seed of the day, auto-claiming
 * on completion (with a callback so the UI can celebrate). Progress persists.
 */
object QuestManager {

    enum class Metric { COINS, DISTANCE, GATES, NEAR_MISS, FLIPS, RUNS, ONE_RUN_SCORE, MAX_COMBO }

    class Quest(
        val template: Int,
        val tier: Int,
        val metric: Metric,
        val target: Int,
        val reward: Int,
        val title: String,
        var progress: Int,
        var claimed: Boolean
    )

    /** Live celebration hook (set by UI layer). */
    @Volatile var onQuestCompleted: ((Quest) -> Unit)? = null

    // templateId -> (metric, targets per tier, reward per tier)
    private val TEMPLATES = arrayOf(
        Triple(0, Metric.COINS,         intArrayOf(20, 30, 45))   to intArrayOf(40, 60, 90),
        Triple(1, Metric.DISTANCE,      intArrayOf(400, 700, 1000)) to intArrayOf(40, 60, 90),
        Triple(2, Metric.GATES,         intArrayOf(4, 6, 10))     to intArrayOf(50, 70, 100),
        Triple(3, Metric.NEAR_MISS,     intArrayOf(3, 5, 8))      to intArrayOf(50, 80, 120),
        Triple(4, Metric.FLIPS,         intArrayOf(60, 100, 150)) to intArrayOf(40, 60, 90),
        Triple(5, Metric.RUNS,          intArrayOf(3, 4, 5))      to intArrayOf(40, 60, 90),
        Triple(6, Metric.ONE_RUN_SCORE, intArrayOf(300, 500, 800)) to intArrayOf(60, 90, 130),
        Triple(7, Metric.MAX_COMBO,     intArrayOf(2, 3, 4))      to intArrayOf(60, 90, 130)
    )

    /** Ensure today's 3 quests exist (deterministic daily pick, tiers scale with streak). */
    fun ensureToday() {
        val today = SaveStore.epochDay()
        if (SaveStore.data.questDay == today && SaveStore.data.quests.size == 3) return
        val rng = Random(today)
        val picks = TEMPLATES.map { it.first.first }.shuffled(rng).take(3)
        val streak = SaveStore.data.streakCurrent
        val rows = ArrayList<QuestRow>(3)
        for ((idx, tpl) in picks.withIndex()) {
            // Tier escalates with streak (0..2), plus a little per-slot variance.
            val tier = (streak / 3 + (idx + 1) / 2).coerceIn(0, 2)
            rows.add(QuestRow(tpl, tier, 0, false))
        }
        SaveStore.edit {
            it.questDay = today
            it.quests.clear()
            it.quests.addAll(rows)
        }
    }

    /** Snapshot of today's quests (live values from store). */
    fun quests(): List<Quest> {
        ensureToday()
        return SaveStore.data.quests.mapNotNull { row ->
            val tpl = TEMPLATES.firstOrNull { it.first.first == row.template } ?: return@mapNotNull null
            val (meta, rewards) = tpl
            val tier = row.tier.coerceIn(0, 2)
            Quest(row.template, row.tier, meta.second, meta.third[tier], rewards[tier],
                titleFor(row.template, meta.third[tier]), row.progress, row.claimed)
        }
    }

    private fun titleFor(template: Int, target: Int): String = when (template) {
        0 -> "Collect $target coins"
        1 -> "Run ${target}m in total"
        2 -> "Pass $target gates"
        3 -> "Get $target near-misses"
        4 -> "Flip $target times"
        5 -> "Finish $target runs"
        6 -> "Score $target in one run"
        else -> "Reach a ${target}x combo"
    }

    /** Streamed live from the game loop (already aggregated per frame event). */
    fun onMetric(metric: Metric, amount: Int) {
        if (amount <= 0) return
        val completed = ArrayList<Quest>(1)
        SaveStore.edit { d ->
            for (row in d.quests) {
                val tpl = TEMPLATES.firstOrNull { it.first.first == row.template } ?: continue
                if (tpl.first.second != metric || row.claimed) continue
                row.progress = when (metric) {
                    Metric.ONE_RUN_SCORE, Metric.MAX_COMBO ->
                        maxOf(row.progress, amount) // "best in a single event" semantics
                    else -> (row.progress + amount)
                }
                if (row.progress >= tpl.first.third[row.tier.coerceIn(0, 2)]) {
                    row.claimed = true
                    Economy.addCoins(rewardsFor(tpl, row.tier))
                    completed.add(Quest(row.template, row.tier, metric,
                        tpl.first.third[row.tier.coerceIn(0, 2)],
                        rewardsFor(tpl, row.tier),
                        titleFor(row.template, tpl.first.third[row.tier.coerceIn(0, 2)]),
                        row.progress, true))
                }
            }
        }
        for (q in completed) onQuestCompleted?.invoke(q)
    }

    private fun rewardsFor(tpl: Pair<Triple<Int, Metric, IntArray>, IntArray>, tier: Int): Int =
        tpl.second[tier.coerceIn(0, 2)]
}
