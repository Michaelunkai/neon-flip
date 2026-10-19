package com.michaelunkai.neonflip.game

/**
 * Game-feel tuning constants, all normalized to view height (H) so the game is
 * resolution independent. Revenue tunables NEVER live here — those are in
 * monetization.RevenueConfig. This file is only physics/spacing/pacing feel.
 */
object GameBalance {
    // --- Loop ---
    const val FIXED_DT = 1f / 120f
    const val MAX_FRAME_DT = 0.1f

    // --- Corridor geometry (fractions of H) ---
    const val CEIL_Y = 0.16f
    const val FLOOR_Y = 0.90f

    // --- Ball ---
    const val BALL_RADIUS = 0.022f
    const val BALL_X_FRAC = 0.26f        // fraction of W
    const val GRAVITY = 3.4f             // H / s^2
    const val FLIP_VY_DAMP = 0.35f       // vy kept at flip instant
    const val WALL_BOUNCE = -0.16f       // vy on floor/ceiling touch

    // --- Speed / difficulty ---
    const val BASE_SPEED = 0.50f         // H / s at distance 0
    const val SPEED_RAMP_SCREENS = 26f   // screens scrolled to reach max ramp
    const val MAX_RAMP = 0.95f           // +95% speed at full ramp
    const val DIFF_FULL_SCREENS = 30f    // screens scrolled for full difficulty 0..1
    const val TUNER_SCALE_MAX = 0.9f     // never let easing drop speed below 90% of tuner scale

    // --- Spawning ---
    const val GAP_NEAR_FRAC = 0.78f      // H of gap between patterns at difficulty 0
    const val GAP_FAR_FRAC = 0.42f       // at difficulty 1
    const val SPIKE_H = 0.075f
    const val SPIKE_W = 0.075f
    const val BLOCK_H = 0.16f
    const val BLOCK_W = 0.055f
    const val BLOCK_WIDE_W = 0.10f

    // --- Collision fairness ---
    const val HITBOX_FORGIVE = 0.88f    // lethal hitbox is 88% of visual radius (reads as fair)

    // --- Pickups / gates ---
    const val COIN_R = 0.015f
    const val MAGNET_R = 0.16f
    const val MAGNET_PULL = 26f          // H / s^2 toward ball
    const val GATE_OPEN = 0.34f

    // --- Scoring ---
    const val SCORE_PER_SCREEN = 60f     // meters of score per H scrolled
    const val NEAR_MISS_BONUS = 5
    const val COIN_SCORE = 2
    const val COMBO_MAX = 8
    const val COMBO_HOLD_S = 5.5f

    // --- Juice ---
    const val NEAR_MISS_MARGIN = 0.055f  // H clearance that counts as a near miss
    const val SLOWMO_SCALE = 0.35f
    const val SLOWMO_DUR = 0.42f
    const val DEATH_SLOWMO = 0.22f
    const val DEATH_T = 0.85f            // real seconds before results
    const val SHAKE_DECAY = 6.5f
    const val SHAKE_AMP = 0.018f         // H
    const val REVIVE_INVULN = 2.2f

    // --- Menu attract mode ---
    const val MENU_SPEED = 0.40f
}
