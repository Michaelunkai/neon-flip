package com.michaelunkai.neonflip.retention

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single offline persistence layer (SharedPreferences + JSON payload).
 * Survives app updates (same prefs file), fully offline-capable, atomic writes.
 * Everything retention/economy-related persists through [edit]; keep field additions
 * backward-compatible (missing JSON keys fall back to defaults on read).
 */
object SaveStore {

    private const val PREFS = "neonflip_save"
    private const val KEY_SAVE = "json"
    private const val VERSION = 1

    // --- Daily helpers (UTC-based epoch day; day boundaries only drive streaks/quests) ---
    fun epochDay(nowMs: Long = System.currentTimeMillis()): Long = nowMs / 86_400_000L

    // --- Quest row (persisted) ---
    class QuestRow(val template: Int, val tier: Int, var progress: Int, var claimed: Boolean)

    // --- Leaderboard row (persisted) ---
    class ScoreRow(var score: Int, var day: Long)

    class Data {
        var version = VERSION
        // Economy
        var coins = 0
        var bestScore = 0
        var totalRuns = 0
        var totalCoinsEarned = 0
        var totalDistanceM = 0
        // Skins/themes
        val ownedSkins = LinkedHashSet<String>()
        var equippedSkin = "cyan"
        val ownedThemes = LinkedHashSet<String>()
        var equippedTheme = "blue"
        // Settings
        var musicOn = true
        var sfxOn = true
        var hapticsOn = true
        // Streak
        var streakCurrent = 0
        var streakBest = 0
        var lastPlayEpochDay = -1L      // last day a RUN was played
        var lastOpenEpochDay = -1L      // last day the app was opened
        var streakRewardClaimedDay = -1L
        var freezes = 1                 // streak-freeze inventory (start with one free)
        // Loss-streak / difficulty easing
        var lossStreak = 0
        // Bonus drops (variable ratio)
        var gameoversUntilDrop = 3
        var totalDrops = 0
        // Quests
        var questDay = -1L
        val quests = ArrayList<QuestRow>()
        // Leaderboard / season
        var seasonStartDay = -1L
        var lastSeasonBest = 0
        val scores = ArrayList<ScoreRow>()
        // Flags
        var notifPermissionAsked = false
        var tutorialDone = false
    }

    @Volatile
    var data = Data()
        private set

    private var prefs: SharedPreferences? = null
    private var lastPersistMs = 0L

    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        data = decode(prefs!!.getString(KEY_SAVE, null))
    }

    /** Apply mutations then persist (throttled: at most once per 500ms; force to flush). */
    @Synchronized
    fun edit(mutate: (Data) -> Unit) {
        mutate(data)
        persist()
    }

    @Synchronized
    fun persist(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistMs < 500) return
        lastPersistMs = now
        val p = prefs ?: return
        p.edit().putString(KEY_SAVE, encode(data)).apply()
    }

    @Synchronized
    fun reset() {
        data = Data()
        persist(force = true)
    }

    // --- JSON codec ---

    private fun encode(d: Data): String {
        val o = JSONObject()
        o.put("v", d.version)
        o.put("coins", d.coins); o.put("best", d.bestScore)
        o.put("runs", d.totalRuns); o.put("coinsEarned", d.totalCoinsEarned)
        o.put("distM", d.totalDistanceM)
        o.put("skins", JSONArray(d.ownedSkins)); o.put("skin", d.equippedSkin)
        o.put("themes", JSONArray(d.ownedThemes)); o.put("theme", d.equippedTheme)
        o.put("music", d.musicOn); o.put("sfx", d.sfxOn); o.put("haptics", d.hapticsOn)
        o.put("streak", d.streakCurrent); o.put("streakBest", d.streakBest)
        o.put("lastPlayDay", d.lastPlayEpochDay); o.put("lastOpenDay", d.lastOpenEpochDay)
        o.put("streakClaimDay", d.streakRewardClaimedDay); o.put("freezes", d.freezes)
        o.put("lossStreak", d.lossStreak)
        o.put("gvUntilDrop", d.gameoversUntilDrop); o.put("drops", d.totalDrops)
        o.put("questDay", d.questDay)
        val qa = JSONArray()
        for (q in d.quests) qa.put(JSONObject().put("t", q.template).put("tier", q.tier)
            .put("p", q.progress).put("c", q.claimed))
        o.put("quests", qa)
        o.put("seasonStart", d.seasonStartDay); o.put("lastSeasonBest", d.lastSeasonBest)
        val sa = JSONArray()
        for (s in d.scores) sa.put(JSONObject().put("s", s.score).put("d", s.day))
        o.put("scores", sa)
        o.put("notifAsked", d.notifPermissionAsked); o.put("tutorial", d.tutorialDone)
        return o.toString()
    }

    private fun decode(raw: String?): Data {
        val d = Data()
        if (raw.isNullOrBlank()) return d
        return try {
            val o = JSONObject(raw)
            d.version = o.optInt("v", 1)
            d.coins = o.optInt("coins", 0); d.bestScore = o.optInt("best", 0)
            d.totalRuns = o.optInt("runs", 0); d.totalCoinsEarned = o.optInt("coinsEarned", 0)
            d.totalDistanceM = o.optInt("distM", 0)
            val skins = o.optJSONArray("skins"); if (skins != null) {
                d.ownedSkins.clear(); d.ownedSkins.add(d.equippedSkin)
                for (i in 0 until skins.length()) d.ownedSkins.add(skins.optString(i))
            }
            d.equippedSkin = o.optString("skin", "cyan")
            val themes = o.optJSONArray("themes"); if (themes != null) {
                d.ownedThemes.clear()
                for (i in 0 until themes.length()) d.ownedThemes.add(themes.optString(i))
            }
            d.equippedTheme = o.optString("theme", "blue")
            d.musicOn = o.optBoolean("music", true); d.sfxOn = o.optBoolean("sfx", true)
            d.hapticsOn = o.optBoolean("haptics", true)
            d.streakCurrent = o.optInt("streak", 0); d.streakBest = o.optInt("streakBest", 0)
            d.lastPlayEpochDay = o.optLong("lastPlayDay", -1L)
            d.lastOpenEpochDay = o.optLong("lastOpenDay", -1L)
            d.streakRewardClaimedDay = o.optLong("streakClaimDay", -1L)
            d.freezes = o.optInt("freezes", 1)
            d.lossStreak = o.optInt("lossStreak", 0)
            d.gameoversUntilDrop = o.optInt("gvUntilDrop", 3); d.totalDrops = o.optInt("drops", 0)
            d.questDay = o.optLong("questDay", -1L)
            d.quests.clear()
            val qa = o.optJSONArray("quests"); if (qa != null) {
                for (i in 0 until qa.length()) {
                    val q = qa.optJSONObject(i) ?: continue
                    d.quests.add(QuestRow(q.optInt("t"), q.optInt("tier"),
                        q.optInt("p", 0), q.optBoolean("c", false)))
                }
            }
            d.seasonStartDay = o.optLong("seasonStart", -1L)
            d.lastSeasonBest = o.optInt("lastSeasonBest", 0)
            d.scores.clear()
            val sa = o.optJSONArray("scores"); if (sa != null) {
                for (i in 0 until sa.length()) {
                    val s = sa.optJSONObject(i) ?: continue
                    d.scores.add(ScoreRow(s.optInt("s", 0), s.optLong("d", 0L)))
                }
            }
            d.notifPermissionAsked = o.optBoolean("notifAsked", false)
            d.tutorialDone = o.optBoolean("tutorial", false)
            d
        } catch (t: Exception) {
            d // corrupted save -> fresh start, never crash
        }
    }
}
