package com.michaelunkai.neonflip.monetization
/**
 * SINGLE TUNABLES FILE for the entire revenue engine. Every threshold, cap and price
 * the monetization algorithm uses MUST live here (remote-config-ready).
 */
object RevenueConfig {
    // --- Ad pacing (tolerance engine) ---
    const val TOLERANCE_MAX = 100
    const val TOLERANCE_INTERSTITIAL_THRESHOLD = 50   // interstitial only when tolerance >= this (was 55; spacing+pacing gates are the real ceiling)
    const val TOLERANCE_DECAY_PER_INTERSTITIAL = 25   // was 30; keep T above threshold for back-to-back (spacing-limited) ads
    const val TOLERANCE_RECOVERY_HIGH_SCORE = 20
    const val TOLERANCE_RECOVERY_REWARDED_WATCH = 25
    const val TOLERANCE_RECOVERY_QUEST = 10
    const val TOLERANCE_TIME_RECOVERY_PER_HOUR = 8    // was 5; faster re-arm between sessions
    const val INTERSTITIAL_MIN_SPACING_MS = 75_000L   // 60-90s cap (Play-policy floor)
    const val INTERSTITIAL_EVERY_N_GAMEOVERS = 2      // was 3; max every 2nd game-over (+~50% impressions, still never every death)
    const val DAY0_PROTECTION_SESSIONS = 3            // no interstitials before this
    const val REWARDED_BIAS_TOLERANCE = 30            // below this: rewarded-only (was 35; lowered to match new threshold)
    const val INTERSTITIAL_MAX_PER_HOUR = 6           // hard hourly ceiling — fatigue backstop behind all soft gates
    const val COMEBACK_RESET_GAP_MS = 172_800_000L    // 48h+ away -> tolerance resets to MAX (returning players are ad-rich again)
    // --- Economy ---
    const val REVIVE_COST_COINS = 100
    const val REVIVE_AD_OFFER = true
    const val STREAK_FREEZE_COST_COINS = 150
    // --- IAP prices (micros) ---
    const val IAP_REMOVE_ADS = "remove_ads"
    const val IAP_COINS_SMALL = "coins_small"      // $0.99
    const val IAP_COINS_MED = "coins_medium"       // $2.99 (best-value anchor)
    const val IAP_COINS_LARGE = "coins_large"      // $4.99
    const val IAP_SKIN_PREMIUM = "skin_premium"
    const val SUB_VIP_MONTHLY = "vip_monthly"      // $1.99/mo
    // --- Notifications ---
    const val NOTIF_MAX_PER_DAY = 1
    const val NOTIF_QUIET_START_HOUR = 21
    const val NOTIF_QUIET_END_HOUR = 9
    // --- Retention ---
    const val NEAR_WIN_EASE_FACTOR = 0.15f           // max difficulty easing after loss streak
    const val LOSS_STREAK_TRIGGER = 3
    // --- Analytics ---
    const val ANALYTICS_LOG_MAX_EVENTS = 5000
}
