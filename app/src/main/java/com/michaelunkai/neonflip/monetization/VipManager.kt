package com.michaelunkai.neonflip.monetization

import android.content.Context

/**
 * VipManager — single source of truth for the vip_monthly subscription state
 * and the benefit flags game code consumes. [BillingEngineImpl.isVip] and
 * [ToleranceEngine.vipProvider] both route through here.
 *
 * Benefits (documented in MONETIZATION.md section D):
 *   - 2x daily login coins        -> [hasDailyCoinMultiplier]
 *   - zero interstitials          -> AdMobAds hard gate via [isVip]
 *   - exclusive VIP skin flag     -> [hasExclusiveSkin]
 *   - streak freeze               -> [hasStreakFreeze] (also purchasable with
 *                                    coins for non-VIP at STREAK_FREEZE_COST_COINS)
 *
 * Remove-ads purchasers are NOT VIP: they lose interstitials only; rewarded
 * ads keep working for both groups.
 */
object VipManager {

    private const val PREFS_FILE = "neonflip_vip"
    private const val KEY_VIP_ACTIVE = "vip_active"
    private const val KEY_VIP_UNTIL = "vip_until_ms"

    /** Grace window applied after the last known subscription period end while
     *  Play Billing is unreachable (offline launch): 48h, per Play policy norms. */
    private const val OFFLINE_GRACE_MS = 48L * 3_600_000L

    private var prefs: android.content.SharedPreferences? = null
    private var vipActive = false
    private var vipUntilMs = -1L // -1 = no expiry known; trust refresh cycle

    fun init(context: Context) {
        if (prefs != null) return
        try {
            prefs = context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            vipActive = prefs!!.getBoolean(KEY_VIP_ACTIVE, false)
            vipUntilMs = prefs!!.getLong(KEY_VIP_UNTIL, -1L)
        } catch (t: Throwable) { prefs = null }
    }

    /** Called by BillingEngineImpl after every successful entitlement refresh. */
    fun setActive(active: Boolean, knownPeriodEndMs: Long = -1L) {
        vipActive = active
        if (knownPeriodEndMs > 0) vipUntilMs = knownPeriodEndMs
        try {
            prefs?.edit()
                ?.putBoolean(KEY_VIP_ACTIVE, vipActive)
                ?.putLong(KEY_VIP_UNTIL, vipUntilMs)
                ?.apply()
        } catch (t: Throwable) {}
    }

    /** Authoritative live check: persisted flag, Play-verified on each refresh,
     *  with an offline grace window past the last known period end. */
    fun isVip(): Boolean {
        if (!vipActive) return false
        if (vipUntilMs <= 0) return true // no expiry known (offline) -> honor
        return System.currentTimeMillis() < vipUntilMs + OFFLINE_GRACE_MS
    }

    // --- benefit flags (game code reads these, not raw VIP) ---

    fun hasDailyCoinMultiplier(): Boolean = isVip()   // 2x daily login coins

    fun hasNoInterstitials(): Boolean = isVip()

    fun hasExclusiveSkin(): Boolean = isVip()         // exclusive VIP skin unlock flag

    fun hasStreakFreeze(): Boolean = isVip()          // free streak freeze while subscribed
}
