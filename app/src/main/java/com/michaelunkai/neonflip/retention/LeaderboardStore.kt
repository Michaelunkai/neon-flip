package com.michaelunkai.neonflip.retention

import java.util.Calendar

/**
 * Local leaderboard history with a weekly season: every ISO week the board resets,
 * the previous season's best is archived, and a new season starts.
 * (Google Play Games can layer on top later; the local board is the offline truth.)
 */
object LeaderboardStore {

    class Entry(val rank: Int, val score: Int, val day: Long, val isLatestRun: Boolean = false)

    private const val MAX_ROWS = 50
    private const val SEASON_DAYS = 7

    /** Call at app open: rotates the season if the stored one expired. */
    fun ensureSeason() {
        val today = SaveStore.epochDay()
        val start = SaveStore.data.seasonStartDay
        if (start < 0) {
            SaveStore.edit { it.seasonStartDay = weekStart(today) }
            return
        }
        if (weekStart(today) != weekStart(start)) {
            SaveStore.edit {
                it.lastSeasonBest = it.scores.maxOfOrNull { r -> r.score } ?: 0
                it.scores.clear()
                it.seasonStartDay = weekStart(today)
            }
        }
    }

    /** Add a finished run; returns its 1-based season rank. */
    fun addScore(score: Int): Int {
        val today = SaveStore.epochDay()
        SaveStore.edit {
            it.scores.add(SaveStore.ScoreRow(score, today))
            it.scores.sortByDescending { r -> r.score }
            while (it.scores.size > MAX_ROWS) it.scores.removeAt(it.scores.size - 1)
        }
        var rank = 1
        for (r in SaveStore.data.scores) {
            if (r.score > score) rank++
        }
        return rank
    }

    /** Top-N of the current season (already sorted desc). */
    fun top(n: Int): List<Entry> {
        ensureSeason()
        return SaveStore.data.scores.take(n).mapIndexed { i, r -> Entry(i + 1, r.score, r.day) }
    }

    fun seasonBest(): Int = SaveStore.data.scores.maxOfOrNull { it.score } ?: 0
    fun lastSeasonBest(): Int = SaveStore.data.lastSeasonBest

    fun seasonNumber(): Int {
        val start = SaveStore.data.seasonStartDay
        if (start <= 0) return 1
        return (start / 7).toInt() // stable arbitrary season numbering
    }

    /** Days remaining in the current season (1..7). */
    fun daysLeftInSeason(): Int {
        val today = SaveStore.epochDay()
        val start = SaveStore.data.seasonStartDay
        if (start <= 0) return SEASON_DAYS
        val elapsed = (today - start).toInt()
        return (SEASON_DAYS - elapsed % SEASON_DAYS).coerceIn(1, SEASON_DAYS)
    }

    /** Monday-based week start in epoch days. */
    private fun weekStart(epochDay: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = epochDay * 86_400_000L
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon=0..Sun=6
        cal.add(Calendar.DAY_OF_YEAR, -dow)
        return cal.timeInMillis / 86_400_000L
    }
}
