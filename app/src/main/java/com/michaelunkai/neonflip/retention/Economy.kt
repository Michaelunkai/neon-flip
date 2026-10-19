package com.michaelunkai.neonflip.retention

import com.michaelunkai.neonflip.monetization.RevenueConfig

/**
 * Skins/themes soft-currency economy. All visual traits live here so the renderer
 * can style the ball and corridor purely from the equipped ids.
 */
object Economy {

    enum class Currency { COINS, IAP }

    class Skin(
        val id: String,
        val name: String,
        val priceCoins: Int,          // 0 = free default
        val iapProduct: String?,      // non-null => purchasable with real money via BillingEngine
        val body: Int,                // core color
        val glow: Int,                // glow/trail color
        val trailStyle: Int           // 0 dots, 1 streak, 2 flame
    )

    class Theme(val id: String, val name: String, val priceCoins: Int,
                val bgTop: Int, val bgBottom: Int, val corridor: Int, val accent: Int)

    val SKINS = listOf(
        Skin("cyan",   "Cyan Core",   0,   null, 0xFF00E5FF.toInt(), 0xFF00A5FF.toInt(), 1),
        Skin("pink",   "Pink Pulse",  250, null, 0xFFFF2E97.toInt(), 0xFFFF6EC7.toInt(), 0),
        Skin("lime",   "Volt",        400, null, 0xFFB4FF39.toInt(), 0xFF6FFF2E.toInt(), 1),
        Skin("violet", "Ultraviolet", 600, null, 0xFF9D4EFF.toInt(), 0xFFC86BFF.toInt(), 2),
        Skin("gold",   "Midas",       900, null, 0xFFFFC93C.toInt(), 0xFFFFE9A3.toInt(), 2),
        Skin("ember",  "Ember",       1200,null, 0xFFFF5A2E.toInt(), 0xFFFFB13C.toInt(), 2),
        Skin("ghost",  "Ghost",       0,   RevenueConfig.IAP_SKIN_PREMIUM, 0xFFEAF6FF.toInt(), 0xFF8FD8FF.toInt(), 0),
        Skin("prism",  "Prism",       1500,null, 0xFF5EFFD6.toInt(), 0xFFFF5EDB.toInt(), 2)
    )

    val THEMES = listOf(
        Theme("blue",   "Midnight",  0,    0xFF0B0E1A.toInt(), 0xFF101830.toInt(), 0xFF1B2B52.toInt(), 0xFF00E5FF.toInt()),
        Theme("sunset", "Sunset",    800,  0xFF1A0B14.toInt(), 0xFF2E1026.toInt(), 0xFF521B3A.toInt(), 0xFFFF2E97.toInt()),
        Theme("toxic",  "Toxic",     1200, 0xFF081408.toInt(), 0xFF0E2410.toInt(), 0xFF1B3A1E.toInt(), 0xFFB4FF39.toInt())
    )

    enum class BuyResult { OK, OWNED, INSUFFICIENT, UNAVAILABLE, EQUIPPED }

    fun skin(id: String): Skin = SKINS.firstOrNull { it.id == id } ?: SKINS.first()
    fun equippedSkin(): Skin = skin(SaveStore.data.equippedSkin)
    fun theme(id: String): Theme = THEMES.firstOrNull { it.id == id } ?: THEMES.first()
    fun equippedTheme(): Theme = theme(SaveStore.data.equippedTheme)

    fun coins(): Int = SaveStore.data.coins

    fun addCoins(n: Int) {
        if (n <= 0) return
        SaveStore.edit { it.coins += n; it.totalCoinsEarned += n }
    }

    /** @return false if balance insufficient (no mutation). */
    fun spendCoins(n: Int): Boolean {
        if (n <= 0) return true
        if (SaveStore.data.coins < n) return false
        SaveStore.edit { it.coins -= n }
        return true
    }

    fun isSkinOwned(id: String): Boolean = SaveStore.data.ownedSkins.contains(id)

    fun buySkinWithCoins(id: String): BuyResult {
        val s = skin(id)
        if (isSkinOwned(id)) return BuyResult.OWNED
        if (s.iapProduct != null) return BuyResult.UNAVAILABLE // IAP skins only via BillingEngine
        if (!spendCoins(s.priceCoins)) return BuyResult.INSUFFICIENT
        SaveStore.edit { it.ownedSkins.add(id); it.equippedSkin = id }
        return BuyResult.OK
    }

    /** Called by monetization glue after a successful IAP delivery. */
    fun grantIapSkin(id: String) {
        SaveStore.edit { it.ownedSkins.add(id); it.equippedSkin = id }
    }

    fun equipSkin(id: String): BuyResult {
        if (!isSkinOwned(id)) return BuyResult.UNAVAILABLE
        SaveStore.edit { it.equippedSkin = id }
        return BuyResult.EQUIPPED
    }

    fun isThemeOwned(id: String): Boolean = SaveStore.data.ownedThemes.contains(id)

    fun buyThemeWithCoins(id: String): BuyResult {
        val t = theme(id)
        if (isThemeOwned(id)) return BuyResult.OWNED
        if (!spendCoins(t.priceCoins)) return BuyResult.INSUFFICIENT
        SaveStore.edit { it.ownedThemes.add(id); it.equippedTheme = id }
        return BuyResult.OK
    }

    fun equipTheme(id: String): BuyResult {
        if (!isThemeOwned(id)) return BuyResult.UNAVAILABLE
        SaveStore.edit { it.equippedTheme = id }
        return BuyResult.EQUIPPED
    }

    /** Revive price comes from the central revenue config, never hardcoded. */
    fun reviveCost(): Int = RevenueConfig.REVIVE_COST_COINS

    fun streakFreezeCost(): Int = RevenueConfig.STREAK_FREEZE_COST_COINS
}
