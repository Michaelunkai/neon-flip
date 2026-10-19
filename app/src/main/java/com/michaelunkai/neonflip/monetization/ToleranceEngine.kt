package com.michaelunkai.neonflip.monetization

import android.content.Context
import com.michaelunkai.neonflip.analytics.RevenueAnalytics
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * ToleranceEngine — THE ad-decision brain of the revenue engine.
 *
 * Tracks per-player local metrics and gates every interstitial behind a 0..100
 * "ad tolerance" score that DECAYS on every interstitial shown and RECOVERS on
 * positive dopamine moments (high score, quest complete, voluntary rewarded
 * watch) and passively over time. Hard gates (day-0 protection, min spacing,
 * every-N-gameovers pacing, ads-removed/VIP) always win over the score.
 *
 * ALL knobs live in [RevenueConfig]. Engine-internal derived constants are the
 * only private constants here and are documented in MONETIZATION.md.
 *
 * State is persisted as a JSON blob in SharedPreferences; every path is
 * crash-proof (never throws) and works fully offline.
 *
 * Game code does NOT call this directly for interstitials — it calls
 * [AdEngine.maybeShowInterstitial] (implemented by AdMobAds) which consults
 * this engine. Game code DOES call the record / mark hooks below (see
 * MONETIZATION.md integration points).
 */
object ToleranceEngine {

    // --- Engine-internal derived constants (see MONETIZATION.md, section C) ---
    private const val CHURN_BOOST_DEATH_STREAK = 8      // +8 effective threshold while deathStreak >= LOSS_STREAK_TRIGGER
    private const val CHURN_BOOST_SESSION_DECLINE = 8   // +8 while last-3-session avg < 0.7x lifetime avg
    private const val CHURN_BOOST_AD_DECLINES = 4       // +4 when >=3 of last 10 ad offers were declined
    private const val SESSION_DECLINE_RATIO = 0.7f      // "declining session length" definition
    private const val RECENT_AD_WINDOW = 10             // rolling window for decline-rate signal
    private const val RECENT_AD_DECLINE_MIN = 3         // declines within window that count as churn
    private const val PEAK_SPIKE_WINDOW_MS = 30_000L    // positive-spike flag validity window
    private const val MS_PER_HOUR = 3_600_000L
    private const val PREFS_FILE = "neonflip_tolerance"
    private const val KEY_STATE = "state_v1"
    private const val KEY_DAYS = "days_active"

    /** Why an interstitial was (not) shown. Exposed for analytics + debug panel. */
    enum class Decision {
        SHOW,               // tolerance gate passed
        SHOW_PEAK,          // positive-spike moment bypassed the threshold (hard gates still held)
        VIP,                // VIP subscribers never see interstitials
        ADS_REMOVED,        // remove_ads purchaser — interstitials off, rewarded stays on
        DAY0_PROTECTION,    // first RevenueConfig.DAY0_PROTECTION_SESSIONS sessions
        MIN_SPACING,        // inside RevenueConfig.INTERSTITIAL_MIN_SPACING_MS
        PACING_GAMEOVERS,   // fewer than RevenueConfig.INTERSTITIAL_EVERY_N_GAMEOVERS deaths since last ad
        TOLERANCE_LOW,      // below effective threshold
        REWARDED_BIAS_MODE, // below REWARDED_BIAS_TOLERANCE -> rewarded-only
        HOURLY_CAP          // INTERSTITIAL_MAX_PER_HOUR reached this wall-clock hour
    }

    data class InterstitialDecision(
        val decision: Decision,
        val tolerance: Int,
        val effectiveThreshold: Int
    ) {
        val show: Boolean get() = decision == Decision.SHOW || decision == Decision.SHOW_PEAK
        val reason: String get() = decision.name
    }

    /** Wired by AdMobAds.init() to BillingEngineImpl so no package cycle exists. */
    var adsRemovedProvider: () -> Boolean = { false }
    var vipProvider: () -> Boolean = { false }

    // --- persisted state ---
    private var prefs: android.content.SharedPreferences? = null
    private var toleranceStored = RevenueConfig.TOLERANCE_MAX
    private var lastChangeMs = 0L
    private var sessionCount = 0
    private var totalSessionMs = 0L
    private var deaths = 0L
    private var adViews = 0L            // rewarded watches + interstitial impressions
    private var adDeclines = 0L         // user declined a rewarded offer
    private var interstitialsShown = 0L
    private var rewardedWatches = 0L
    private var deathStreak = 0L        // consecutive game-overs without a high score
    private var gameOversSinceAd = 0L
    private var lastInterstitialMs = 0L
    private var lastSessionEndMs = 0L
    private var gapSumMs = 0L
    private var gapCount = 0L
    private var coinBalance = 0L        // mirror; game remains source of truth
    private var recentAdResults = ArrayDeque<String>()   // "v" view / "d" decline, last RECENT_AD_WINDOW
    private var recentSessionMs = ArrayDeque<Long>()     // last 5 session lengths
    private var peakUntilMs = 0L
    private var peakSource = ""
    private var sessionStartMs = 0L     // current session
    private var hourCapCount = 0        // interstitials shown in the current wall-clock hour
    private var hourCapBucket = 0L      // epoch-hour bucket the count belongs to

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // ---------------------------------------------------------------- lifecycle

    /** Call once from Application/Activity attach. Idempotent, offline-safe. */
    fun init(context: Context) {
        if (prefs != null) return
        try {
            prefs = context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            load()
        } catch (t: Throwable) {
            prefs = null // in-memory only; never crash
        }
    }

    private fun load() {
        val p = prefs ?: return
        try {
            JSONObject(p.getString(KEY_STATE, "{}")).let { j ->
                toleranceStored = j.optInt("tol", RevenueConfig.TOLERANCE_MAX)
                lastChangeMs = j.optLong("lc", 0L)
                sessionCount = j.optInt("ses", 0)
                totalSessionMs = j.optLong("tses", 0L)
                deaths = j.optLong("dth", 0L)
                adViews = j.optLong("adv", 0L)
                adDeclines = j.optLong("add", 0L)
                interstitialsShown = j.optLong("int", 0L)
                rewardedWatches = j.optLong("rw", 0L)
                deathStreak = j.optLong("dst", 0L)
                gameOversSinceAd = j.optLong("goa", 0L)
                lastInterstitialMs = j.optLong("li", 0L)
                lastSessionEndMs = j.optLong("lse", 0L)
                gapSumMs = j.optLong("gs", 0L)
                gapCount = j.optLong("gc", 0L)
                coinBalance = j.optLong("coin", 0L)
                peakUntilMs = j.optLong("pu", 0L)
                peakSource = j.optString("ps", "")
                hourCapCount = j.optInt("ihc", 0)
                hourCapBucket = j.optLong("ihb", 0L)
                recentAdResults.clear()
                j.optJSONArray("rar")?.let { a -> for (i in 0 until a.length()) recentAdResults.add(a.optString(i, "v")) }
                recentSessionMs.clear()
                j.optJSONArray("rsl")?.let { a -> for (i in 0 until a.length()) recentSessionMs.add(a.optLong(i)) }
            }
        } catch (t: Throwable) { /* corrupted state -> defaults */ }
    }

    private fun persist() {
        val p = prefs ?: return
        try {
            val rar = org.json.JSONArray(); recentAdResults.forEach { rar.put(it) }
            val rsl = org.json.JSONArray(); recentSessionMs.forEach { rsl.put(it) }
            val j = JSONObject()
                .put("tol", toleranceStored).put("lc", lastChangeMs)
                .put("ses", sessionCount).put("tses", totalSessionMs)
                .put("dth", deaths).put("adv", adViews).put("add", adDeclines)
                .put("int", interstitialsShown).put("rw", rewardedWatches)
                .put("dst", deathStreak).put("goa", gameOversSinceAd)
                .put("li", lastInterstitialMs).put("lse", lastSessionEndMs)
                .put("gs", gapSumMs).put("gc", gapCount)
                .put("coin", coinBalance).put("pu", peakUntilMs).put("ps", peakSource)
                .put("ihc", hourCapCount).put("ihb", hourCapBucket)
                .put("rar", rar).put("rsl", rsl)
            p.edit().putString(KEY_STATE, j.toString()).apply()
        } catch (t: Throwable) { /* never crash on persistence */ }
    }

    private fun now(): Long = System.currentTimeMillis()

    // ---------------------------------------------------------------- session hooks

    /** Game calls at every app foreground. Returns true if this is a NEW active day. */
    fun recordSessionStart(): Boolean {
        val t = now()
        sessionStartMs = t
        sessionCount++
        // inter-session gap
        if (lastSessionEndMs > 0) {
            val gap = t - lastSessionEndMs
            if (gap > 0) { gapSumMs += gap; gapCount++ }
            // Comeback grace: a lapsed player (>= COMEBACK_RESET_GAP_MS away) returns
            // ad-rich — reset tolerance to MAX so re-engagement is monetized day one.
            if (gap >= RevenueConfig.COMEBACK_RESET_GAP_MS) {
                toleranceStored = RevenueConfig.TOLERANCE_MAX
                lastChangeMs = t
                deathStreak = 0
                RevenueAnalytics.log("comeback", mapOf("gap_hours" to gap / MS_PER_HOUR, "tolerance" to toleranceStored))
            }
        }
        var newDay = false
        try {
            val p = prefs
            if (p != null) {
                val today = dayFmt.format(java.util.Date(t))
                val days = (p.getStringSet(KEY_DAYS, emptySet()) ?: emptySet()).toMutableSet()
                if (days.add(today)) {
                    newDay = true
                    p.edit().putStringSet(KEY_DAYS, days).apply()
                }
            }
        } catch (t2: Throwable) { /* ignore */ }
        RevenueAnalytics.log("session_start", mapOf("sessions" to sessionCount, "tolerance" to currentTolerance()))
        persist()
        return newDay
    }

    /** Game calls at every app background with the session duration in ms. */
    fun recordSessionEnd(durationMs: Long) {
        if (durationMs > 0) {
            totalSessionMs += durationMs
            recentSessionMs.add(durationMs)
            while (recentSessionMs.size > 5) recentSessionMs.removeFirst()
        }
        lastSessionEndMs = now()
        RevenueAnalytics.log(
            "session_end",
            mapOf("duration_ms" to durationMs, "avg_session_ms" to avgSessionMs(), "tolerance" to currentTolerance())
        )
        persist()
    }

    /** Game calls on every game over. */
    fun recordGameOver() {
        deaths++
        deathStreak++
        gameOversSinceAd++
        RevenueAnalytics.log("death", mapOf("streak" to deathStreak, "gameovers_since_ad" to gameOversSinceAd))
        persist()
    }

    // ---------------------------------------------------------------- dopamine hooks (tolerance recovery)

    private fun recover(amount: Int, why: String) {
        toleranceStored = min(RevenueConfig.TOLERANCE_MAX, currentTolerance() + amount)
        lastChangeMs = now()
        RevenueAnalytics.log("tolerance_snapshot", mapOf("delta" to amount, "why" to why, "tolerance" to toleranceStored))
        persist()
    }

    /** New personal/high score. Resets the death-streak churn signal. */
    fun recordHighScore(score: Long) {
        deathStreak = 0
        RevenueAnalytics.log("high_score", mapOf("score" to score))
        recover(RevenueConfig.TOLERANCE_RECOVERY_HIGH_SCORE, "high_score")
    }

    /** Daily quest / mission completed. */
    fun recordQuestComplete(questId: String) {
        RevenueAnalytics.log("quest_complete", mapOf("quest" to questId))
        recover(RevenueConfig.TOLERANCE_RECOVERY_QUEST, "quest")
    }

    /**
     * User finished a rewarded ad. Voluntary watches (coins/revive offer the user
     * chose) recover tolerance; non-voluntary placements only count the impression.
     */
    fun recordRewardedWatch(voluntary: Boolean, placement: String) {
        rewardedWatches++
        adViews++
        pushAdResult("v")
        RevenueAnalytics.log("ad_show", mapOf("format" to "rewarded", "placement" to placement, "voluntary" to voluntary))
        if (voluntary) {
            recover(RevenueConfig.TOLERANCE_RECOVERY_REWARDED_WATCH, "rewarded_watch")
            // A completed voluntary watch is a guaranteed dopamine peak: open the
            // spike window so the NEXT interstitial lands at maximum good will.
            peakUntilMs = now() + PEAK_SPIKE_WINDOW_MS
            peakSource = "rewarded:$placement"
        }
    }

    /** User declined a rewarded offer (offered but backed out / pressed no). */
    fun recordAdDecline(placement: String) {
        adDeclines++
        pushAdResult("d")
        RevenueAnalytics.log("ad_decline", mapOf("placement" to placement))
        persist()
    }

    /** Game code sets this RIGHT BEFORE a moment of peak positive dopamine
     *  (new skin reveal, big combo, streak milestone). Valid [PEAK_SPIKE_WINDOW_MS]. */
    fun markPositiveSpike(source: String) {
        peakUntilMs = now() + PEAK_SPIKE_WINDOW_MS
        peakSource = source
        RevenueAnalytics.log("positive_spike", mapOf("source" to source, "tolerance" to currentTolerance()))
        persist()
    }

    // ---------------------------------------------------------------- economy mirror (optional, informational)

    /** Game may mirror the player's coin balance for churn analytics. */
    fun recordCoinBalance(balance: Long) {
        coinBalance = balance
        persist()
    }

    // ---------------------------------------------------------------- interstitial decision

    /**
     * Pure evaluation — NO state mutation (safe to probe from the debug panel).
     * AdMobAds calls [onInterstitialShown] only when an ad actually displays.
     */
    fun requestInterstitial(placement: String): InterstitialDecision {
        val tol = currentTolerance()
        val eff = effectiveThreshold()
        return when {
            vipProvider() -> InterstitialDecision(Decision.VIP, tol, eff)
            adsRemovedProvider() -> InterstitialDecision(Decision.ADS_REMOVED, tol, eff)
            sessionCount < RevenueConfig.DAY0_PROTECTION_SESSIONS ->
                InterstitialDecision(Decision.DAY0_PROTECTION, tol, eff)
            now() - lastInterstitialMs < RevenueConfig.INTERSTITIAL_MIN_SPACING_MS ->
                InterstitialDecision(Decision.MIN_SPACING, tol, eff)
            gameOversSinceAd < RevenueConfig.INTERSTITIAL_EVERY_N_GAMEOVERS ->
                InterstitialDecision(Decision.PACING_GAMEOVERS, tol, eff)
            hourCapExceeded() -> InterstitialDecision(Decision.HOURLY_CAP, tol, eff)
            peakActive() -> InterstitialDecision(Decision.SHOW_PEAK, tol, eff)
            tol < RevenueConfig.REWARDED_BIAS_TOLERANCE ->
                InterstitialDecision(Decision.REWARDED_BIAS_MODE, tol, eff)
            tol < eff -> InterstitialDecision(Decision.TOLERANCE_LOW, tol, eff)
            else -> InterstitialDecision(Decision.SHOW, tol, eff)
        }
    }

    /**
     * Called by AdMobAds when an interstitial ACTUALLY displayed.
     * Applies decay with time-recovery folding, stamps spacing + pacing counters.
     */
    fun onInterstitialShown(placement: String) {
        adViews++
        interstitialsShown++
        rollHourBucket()
        hourCapCount++
        gameOversSinceAd = 0
        lastInterstitialMs = now()
        peakUntilMs = 0L; peakSource = "" // consume spike
        toleranceStored = max(0, currentTolerance() - RevenueConfig.TOLERANCE_DECAY_PER_INTERSTITIAL)
        lastChangeMs = now()
        RevenueAnalytics.log(
            "tolerance_snapshot",
            mapOf("delta" to -RevenueConfig.TOLERANCE_DECAY_PER_INTERSTITIAL, "why" to "interstitial",
                "placement" to placement, "tolerance" to toleranceStored)
        )
        persist()
    }

    /** Decision was YES but no ad was fillable — no penalty, trigger preload upstream. */
    fun onInterstitialUnavailable(placement: String) {
        RevenueAnalytics.log("ad_unavailable", mapOf("placement" to placement, "format" to "interstitial"))
        persist()
    }

    // ---------------------------------------------------------------- live readouts

    /** Stored tolerance folded with time recovery: +PER_HOUR for every full hour
     *  since the last decay/recovery event, capped at TOLERANCE_MAX. Pure. */
    fun currentTolerance(): Int {
        if (lastChangeMs <= 0L) return min(toleranceStored, RevenueConfig.TOLERANCE_MAX)
        val hours = (now() - lastChangeMs) / MS_PER_HOUR
        return min(RevenueConfig.TOLERANCE_MAX, toleranceStored + (hours * RevenueConfig.TOLERANCE_TIME_RECOVERY_PER_HOUR).toInt())
    }

    /** Effective interstitial threshold = base + churn boosts (see constants above). */
    fun effectiveThreshold(): Int =
        min(RevenueConfig.TOLERANCE_MAX,
            RevenueConfig.TOLERANCE_INTERSTITIAL_THRESHOLD + churnBoost())

    /** Below this the engine wants rewarded-only monetization. */
    fun isRewardedBias(): Boolean = currentTolerance() < RevenueConfig.REWARDED_BIAS_TOLERANCE

    fun isDay0Protected(): Boolean = sessionCount < RevenueConfig.DAY0_PROTECTION_SESSIONS

    fun peakActive(): Boolean = now() < peakUntilMs

    fun avgSessionMs(): Long = if (sessionCount <= 0) 0L else totalSessionMs / sessionCount

    fun deathsPerSession(): Float = if (sessionCount <= 0) 0f else deaths.toFloat() / sessionCount

    fun daysActive(): Int = try {
        prefs?.getStringSet(KEY_DAYS, emptySet())?.size ?: 0
    } catch (t: Throwable) { 0 }

    fun avgInterSessionGapMs(): Long = if (gapCount <= 0) 0L else gapSumMs / gapCount

    private fun churnBoost(): Int {
        var boost = 0
        if (deathStreak >= RevenueConfig.LOSS_STREAK_TRIGGER) boost += CHURN_BOOST_DEATH_STREAK
        if (sessionLengthDeclining()) boost += CHURN_BOOST_SESSION_DECLINE
        if (recentAdDeclineRate()) boost += CHURN_BOOST_AD_DECLINES
        return boost
    }

    private fun sessionLengthDeclining(): Boolean {
        if (recentSessionMs.size < 3 || sessionCount < 5) return false
        val last3 = recentSessionMs.takeLast(3).average()
        val lifetime = avgSessionMs().toDouble()
        return lifetime > 0 && last3 < lifetime * SESSION_DECLINE_RATIO
    }

    private fun recentAdDeclineRate(): Boolean {
        if (recentAdResults.size < RECENT_AD_WINDOW) return false
        return recentAdResults.count { it == "d" } >= RECENT_AD_DECLINE_MIN
    }

    /** Hard fatigue backstop: max INTERSTITIAL_MAX_PER_HOUR per wall-clock hour. */
    private fun rollHourBucket() {
        val bucket = now() / MS_PER_HOUR
        if (bucket != hourCapBucket) { hourCapBucket = bucket; hourCapCount = 0 }
    }

    private fun hourCapExceeded(): Boolean {
        rollHourBucket()
        return hourCapCount >= RevenueConfig.INTERSTITIAL_MAX_PER_HOUR
    }

    private fun pushAdResult(r: String) {
        recentAdResults.add(r)
        while (recentAdResults.size > RECENT_AD_WINDOW) recentAdResults.removeFirst()
    }

    // ---------------------------------------------------------------- debug / telemetry

    /** Full live state for the debug panel and analytics. */
    fun snapshot(): Map<String, Any?> {
        val d = requestInterstitial("snapshot_probe")
        return mapOf(
            "tolerance" to currentTolerance(),
            "tolerance_stored" to toleranceStored,
            "effective_threshold" to effectiveThreshold(),
            "churn_boost" to churnBoost(),
            "next_decision" to d.reason,
            "rewarded_bias" to isRewardedBias(),
            "day0_protected" to isDay0Protected(),
            "peak_active" to peakActive(),
            "peak_source" to peakSource,
            "sessions" to sessionCount,
            "days_active" to daysActive(),
            "avg_session_ms" to avgSessionMs(),
            "avg_gap_ms" to avgInterSessionGapMs(),
            "deaths" to deaths,
            "deaths_per_session" to Math.round(deathsPerSession() * 100f) / 100f,
            "death_streak" to deathStreak,
            "ad_views" to adViews,
            "ad_declines" to adDeclines,
            "interstitials_shown" to interstitialsShown,
            "rewarded_watches" to rewardedWatches,
            "gameovers_since_ad" to gameOversSinceAd,
            "interstitials_this_hour" to hourCapCount,
            "coin_balance" to coinBalance
        )
    }

    /** Debug-panel only: wipe back to fresh-install state. */
    fun resetForDebug() {
        toleranceStored = RevenueConfig.TOLERANCE_MAX
        lastChangeMs = 0L; sessionCount = 0; totalSessionMs = 0; deaths = 0; adViews = 0
        adDeclines = 0; interstitialsShown = 0; rewardedWatches = 0; deathStreak = 0
        gameOversSinceAd = 0; lastInterstitialMs = 0; lastSessionEndMs = 0
        gapSumMs = 0; gapCount = 0; coinBalance = 0; peakUntilMs = 0; peakSource = ""
        hourCapCount = 0; hourCapBucket = 0
        recentAdResults.clear(); recentSessionMs.clear()
        try { prefs?.edit()?.remove(KEY_STATE)?.remove(KEY_DAYS)?.apply() } catch (t: Throwable) {}
        persist()
    }
}
