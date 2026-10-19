package com.michaelunkai.neonflip.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import com.michaelunkai.neonflip.R
import com.michaelunkai.neonflip.game.GameEngine
import com.michaelunkai.neonflip.monetization.RevenueConfig
import com.michaelunkai.neonflip.retention.DifficultyTuner
import com.michaelunkai.neonflip.retention.Economy
import com.michaelunkai.neonflip.retention.LeaderboardStore
import com.michaelunkai.neonflip.retention.QuestManager
import com.michaelunkai.neonflip.retention.SaveStore
import com.michaelunkai.neonflip.retention.StreakManager
import kotlin.math.min

enum class ScreenId { MENU, RESULTS, SHOP, RANKS, SETTINGS, PAUSE }

/** All user intents flow from canvas UI to the Activity through this. */
interface UiNav {
    fun play()
    fun restart()
    fun toMenu()
    fun pauseGame()
    fun resumeGame()
    fun reviveByAd()
    fun reviveByCoins()
    fun doubleCoinsByAd()
    fun open(screen: ScreenId)
    fun share()
    fun buySkinCoins(id: String)
    fun equipSkin(id: String)
    fun buyTheme(id: String)
    fun buyFreeze()
    fun purchaseIap(productId: String)
    fun claimStreak()
    fun claimGift()
    fun setSetting(key: String, on: Boolean)
    fun requestNotifPermission()
    fun resetProgress()
}

abstract class Screen(val ctx: Context, protected val nav: UiNav) {
    var shownAtNs = 0L
    val buttons = ArrayList<NeonUi.Btn>()

    abstract val id: ScreenId

    open fun show() { shownAtNs = System.nanoTime() }

    /** Entrance progress 0..1 with easing. */
    protected fun enter(tSec: Float, dur: Float = 0.32f): Float =
        NeonUi.Easing.outCubic((tSec / dur).coerceIn(0f, 1f))

    protected fun btn(idStr: String, l: Float, t: Float, r: Float, b: Float,
                      label: String, style: Int = NeonUi.STYLE_GHOST,
                      enabled: Boolean = true, sub: String? = null,
                      pulse: Float = 0f, u: Float, uw: Float = u): NeonUi.Btn {
        val btn = NeonUi.Btn(idStr).apply {
            rect.set(l * uw, t * u, r * uw, b * u)
            this.label = label; this.style = style; this.enabled = enabled
            this.sub = sub; this.pulse = pulse
        }
        buttons.add(btn)
        return btn
    }

    abstract fun draw(c: Canvas, w: Int, h: Int, tSec: Float)

    open fun tap(x: Float, y: Float): Boolean {
        for (b in buttons.asReversed()) {
            if (b.hit(x, y)) { onButton(b.id); return true }
        }
        return false
    }

    protected open fun onButton(idStr: String) {}

    // shared helpers
    protected fun s(res: Int): String = ctx.getString(res)
    protected fun s(res: Int, arg: Any): String = ctx.getString(res, arg)
    protected fun s(res: Int, arg1: Any, vararg more: Any?): String =
        ctx.getString(res, *arrayOf(arg1, *more))

    protected fun drawCoinsChip(c: Canvas, w: Int, h: Int) {
        val u = h / 100f; val uw = w / 100f
        val r = RectF(64f * uw, 4f * u, 96f * uw, 9.5f * u)
        NeonUi.drawChip(c, r, Economy.coins().toString(), NeonUi.GOLD, h.toFloat())
        NeonUi.coinDot(c, r.left + r.height() * 0.55f, r.centerY(), r.height() * 0.22f)
    }

    protected fun drawQuestLines(c: Canvas, w: Int, h: Int) {
        val u = h / 100f; val uw = w / 100f
        val quests = QuestManager.quests()
        // own card band so quest text can never overlap the title block
        val card = RectF(4f * uw, 30.5f * u, 96f * uw,
            (30.5f + 5.6f + quests.size * 4.4f) * u)
        NeonUi.drawPanel(c, card, h.toFloat())
        val p = NeonUi.textLeft()
        p.textSize = 2.6f * u
        for ((i, q) in quests.withIndex()) {
            val y = (35.8f + i * 4.4f) * u
            val done = q.claimed
            p.color = if (done) NeonUi.GOLD else 0xFFB9C7E6.toInt()
            p.alpha = if (done) 230 else 190
            val state = if (done) "OK" else "${min(q.progress, q.target)}/${q.target}"
            c.drawText("${s(R.string.quest_bullet)} ${q.title}  $state",
                7f * uw, y, p)
        }
        p.alpha = 255
    }
}

// =========================================================================== MENU

class MenuScreen(ctx: Context, nav: UiNav) : Screen(ctx, nav) {
    override val id = ScreenId.MENU
    var giftAmount = 0
    var seasonBest = 0

    override fun draw(c: Canvas, w: Int, h: Int, tSec: Float) {
        buttons.clear()
        val u = h / 100f; val uw = w / 100f
        val e = enter(tSec)
        val d = SaveStore.data

        // title
        val pulse = 1f + 0.015f * NeonUi.Easing.pulse(tSec, 0.8f)
        NeonUi.glowTitle(c, s(R.string.app_name), w / 2f, 22.5f * u, 7.6f * u * pulse,
            0xFFFFFFFF.toInt(), NeonUi.ACCENT)
        val sub = NeonUi.textCenter()
        sub.textSize = 2.8f * u
        sub.color = 0xFF8FA3C8.toInt()
        c.drawText(s(R.string.menu_tagline), w / 2f, 27.6f * u, sub)

        // best chip
        NeonUi.drawChip(c, RectF(4f * uw, 4f * u, 30f * uw, 9.5f * u),
            s(R.string.menu_best, d.bestScore), NeonUi.ACCENT, h.toFloat())
        // streak chip
        val streakCol = if (d.streakCurrent > 0) NeonUi.PINK else NeonUi.TEXT_DIM
        NeonUi.drawChip(c, RectF(4f * uw, 11f * u, 30f * uw, 15.5f * u),
            s(R.string.menu_streak, d.streakCurrent), streakCol, h.toFloat())

        // coins
        drawCoinsChip(c, w, h)

        // daily reward button
        val rewardReady = d.streakRewardClaimedDay != SaveStore.epochDay()
        if (rewardReady) {
            val amount = StreakManager.rewardForDay(StreakManager.todayStreakDay())
            val b = btn("streak", 66f, 11f, 96f, 16.5f, s(R.string.menu_daily),
                NeonUi.STYLE_GOLD, sub = "+$amount", pulse = 0.5f + 0.5f * NeonUi.Easing.pulse(tSec, 1.6f), u = u, uw = uw)
            NeonUi.drawButton(c, b, h.toFloat(), e)
        } else if (giftAmount > 0) {
            val b = btn("gift", 66f, 11f, 96f, 16.5f, s(R.string.menu_gift),
                NeonUi.STYLE_GOLD, sub = "+$giftAmount",
                pulse = 0.5f + 0.5f * NeonUi.Easing.pulse(tSec, 1.6f), u = u, uw = uw)
            NeonUi.drawButton(c, b, h.toFloat(), e)
        }

        drawQuestLines(c, w, h)

        // PLAY
        val playPulse = 0.5f + 0.5f * NeonUi.Easing.pulse(tSec, 1.1f)
        val play = btn("play", 26f, 55f, 74f, 66f, s(R.string.menu_play),
            NeonUi.STYLE_PRIMARY, pulse = playPulse, u = u, uw = uw)
        NeonUi.drawButton(c, play, h.toFloat(), e)

        // row
        val shop = btn("shop", 6f, 70f, 32f, 77.5f, s(R.string.menu_shop), u = u, uw = uw)
        val ranks = btn("ranks", 37f, 70f, 63f, 77.5f, s(R.string.menu_ranks), u = u, uw = uw)
        val set = btn("settings", 68f, 70f, 94f, 77.5f, s(R.string.menu_settings), u = u, uw = uw)
        NeonUi.drawButton(c, shop, h.toFloat(), e)
        NeonUi.drawButton(c, ranks, h.toFloat(), e)
        NeonUi.drawButton(c, set, h.toFloat(), e)

        // season footer
        val foot = NeonUi.textCenter()
        foot.textSize = 2.4f * u
        foot.color = NeonUi.TEXT_DIM
        c.drawText(s(R.string.menu_season, LeaderboardStore.seasonNumber(),
            LeaderboardStore.daysLeftInSeason(), LeaderboardStore.seasonBest()),
            w / 2f, 87f * u, foot)
        if (DifficultyTuner.lossStreak() > 0 && d.bestScore > 0) {
            foot.color = NeonUi.PINK
            c.drawText(s(R.string.menu_easing_on), w / 2f, 91f * u, foot)
        }
    }

    override fun onButton(idStr: String) {
        when (idStr) {
            "play" -> nav.play()
            "shop" -> nav.open(ScreenId.SHOP)
            "ranks" -> nav.open(ScreenId.RANKS)
            "settings" -> nav.open(ScreenId.SETTINGS)
            "streak" -> nav.claimStreak()
            "gift" -> nav.claimGift()
        }
    }
}

// =========================================================================== RESULTS

class ResultsScreen(ctx: Context, nav: UiNav) : Screen(ctx, nav) {
    override val id = ScreenId.RESULTS

    private var result: GameEngine.RunResult? = null
    private var newBest = false
    private var bonusCoins = 0
    private var doubleClaimed = false

    fun showResult(r: GameEngine.RunResult, isNewBest: Boolean, bonus: Int) {
        result = r; newBest = isNewBest; bonusCoins = bonus; doubleClaimed = false
        show()
    }

    fun markDoubled() { doubleClaimed = true }

    override fun draw(c: Canvas, w: Int, h: Int, tSec: Float) {
        buttons.clear()
        val u = h / 100f; val uw = w / 100f
        val e = enter(tSec, 0.38f)
        val r = result ?: return
        val best = SaveStore.data.bestScore

        // dim
        val dim = NeonUi.fillOpaque()
        dim.color = Color.BLACK
        dim.alpha = (200 * e).toInt()
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), dim)
        dim.alpha = 255

        // panel (slides up)
        val panel = RectF(6f * uw, 20f * u + (1f - e) * 18f * u, 94f * uw, 90f * u)
        NeonUi.drawPanel(c, panel, h.toFloat(),
            if (newBest) 0xAAFFD54A.toInt() else 0x6600E5FF)

        // title
        val title = if (newBest) s(R.string.results_new_best) else s(R.string.results_title)
        val titleCol = if (newBest) NeonUi.GOLD else NeonUi.PINK
        val tp = NeonUi.textCenter()
        tp.textSize = 4.2f * u * (1f + (if (newBest) 0.03f * NeonUi.Easing.pulse(tSec, 2f) else 0f))
        tp.color = titleCol
        c.drawText(title, w / 2f, 29f * u, tp)

        // score count-up
        val frac = NeonUi.Easing.outQuint((tSec / 0.8f).coerceIn(0f, 1f))
        val shown = (r.score * frac).toInt()
        NeonUi.glowTitle(c, shown.toString(), w / 2f, 41.5f * u, 11f * u,
            0xFFFFFFFF.toInt(), NeonUi.ACCENT)

        // best line / near-record highlight
        val bp = NeonUi.textCenter()
        bp.textSize = 2.8f * u
        if (newBest) {
            bp.color = NeonUi.GOLD
            c.drawText(s(R.string.results_beat_old, best), w / 2f, 47.5f * u, bp)
        } else {
            val gap = best - r.score
            if (gap in 1..(best * 0.12f).toInt().coerceAtLeast(1)) {
                bp.color = NeonUi.PINK
                bp.textSize = 3.1f * u
                c.drawText(s(R.string.results_so_close, gap, best), w / 2f, 47.5f * u, bp)
            } else {
                bp.color = NeonUi.TEXT_DIM
                c.drawText(s(R.string.results_best, best), w / 2f, 47.5f * u, bp)
            }
        }

        // stats row
        bp.textSize = 2.9f * u
        bp.color = NeonUi.GOLD
        c.drawText("+${r.coins}", 22f * uw, 53.5f * u, bp)
        bp.color = NeonUi.ACCENT
        c.drawText("x${r.maxCombo}", 50f * uw, 53.5f * u, bp)
        bp.color = NeonUi.PINK
        c.drawText(s(R.string.results_near, r.nearMisses), 78f * uw, 53.5f * u, bp)

        // bonus banner
        if (bonusCoins > 0) {
            val br = RectF(16f * uw, 56f * u, 84f * uw, 61.5f * u)
            NeonUi.drawChip(c, br, s(R.string.results_bonus, bonusCoins), NeonUi.GOLD,
                h.toFloat(), filled = true)
        }

        // revive row
        val adsReady = Locator.adsReady && Locator.ads.isRewardedReady()
        val reviveAllowed = !r.wasRevived
        val cost = Economy.reviveCost()
        val reviveAd = btn("revive_ad", 9f, 63.5f, 47f, 70f, s(R.string.results_revive),
            NeonUi.STYLE_PRIMARY, enabled = adsReady && reviveAllowed,
            sub = if (reviveAllowed) s(R.string.results_watch_ad) else s(R.string.results_revived), u = u, uw = uw)
        val reviveCoins = btn("revive_coins", 53f, 63.5f, 91f, 70f, s(R.string.results_revive),
            NeonUi.STYLE_GHOST, enabled = reviveAllowed && Economy.coins() >= cost,
            sub = "$cost ${s(R.string.coins_short)}", u = u, uw = uw)
        NeonUi.drawButton(c, reviveAd, h.toFloat(), e)
        NeonUi.drawButton(c, reviveCoins, h.toFloat(), e)

        // 2x + restart
        val double = btn("double", 9f, 72f, 47f, 78.5f, s(R.string.results_double),
            NeonUi.STYLE_GOLD, enabled = adsReady && !doubleClaimed,
            sub = if (doubleClaimed) s(R.string.results_doubled) else s(R.string.results_watch_ad), u = u, uw = uw)
        val restart = btn("restart", 53f, 72f, 91f, 78.5f, s(R.string.results_restart),
            NeonUi.STYLE_PRIMARY, u = u, uw = uw)
        NeonUi.drawButton(c, double, h.toFloat(), e)
        NeonUi.drawButton(c, restart, h.toFloat(), e)

        val menu = btn("menu", 32f, 80.5f, 68f, 87f, s(R.string.results_menu), u = u, uw = uw)
        NeonUi.drawButton(c, menu, h.toFloat(), e)
    }

    override fun onButton(idStr: String) {
        when (idStr) {
            "revive_ad" -> nav.reviveByAd()
            "revive_coins" -> nav.reviveByCoins()
            "double" -> nav.doubleCoinsByAd()
            "restart" -> nav.restart()
            "menu" -> nav.toMenu()
        }
    }
}

// =========================================================================== SHOP

class ShopScreen(ctx: Context, nav: UiNav) : Screen(ctx, nav) {
    override val id = ScreenId.SHOP
    private var tab = 0

    override fun draw(c: Canvas, w: Int, h: Int, tSec: Float) {
        buttons.clear()
        val u = h / 100f; val uw = w / 100f
        val e = enter(tSec)

        val title = NeonUi.textLeft()
        title.textSize = 5f * u
        title.color = 0xFFFFFFFF.toInt()
        c.drawText(s(R.string.shop_title), 6f * uw, 9f * u, title)
        drawCoinsChip(c, w, h)

        // tabs
        for (i in 0..2) {
            val l = (6 + i * 31) .toFloat()
            val label = when (i) {
                0 -> s(R.string.shop_tab_skins)
                1 -> s(R.string.shop_tab_coins)
                else -> s(R.string.shop_tab_extras)
            }
            val b = btn("tab$i", l, 12f, l + 28f, 17.5f, label,
                if (tab == i) NeonUi.STYLE_PRIMARY else NeonUi.STYLE_GHOST, u = u, uw = uw)
            NeonUi.drawButton(c, b, h.toFloat(), e)
        }

        when (tab) {
            0 -> drawSkins(c, w, h, u, uw, e)
            1 -> drawCoinPacks(c, w, h, u, uw, e)
            else -> drawExtras(c, w, h, u, uw, e)
        }
    }

    private fun drawSkins(c: Canvas, w: Int, h: Int, u: Float, uw: Float, e: Float) {
        val skins = Economy.SKINS
        for ((i, sk) in skins.withIndex()) {
            val col = i % 2; val row = i / 2
            val l = (6 + col * 46) * uw
            val t = (20 + row * 14.2f) * u
            val rr = RectF(l, t, l + 42f * uw, t + 13f * u)
            val owned = Economy.isSkinOwned(sk.id)
            val equipped = SaveStore.data.equippedSkin == sk.id
            NeonUi.drawPanel(c, rr, h.toFloat(),
                if (equipped) 0xAAFFD54A.toInt() else 0x4400E5FF)
            NeonUi.drawBallPreview(c, rr.left + 7f * uw, rr.centerY(),
                3.4f * u, sk.body, sk.glow, sk.glow)
            val p = NeonUi.textLeft()
            p.textSize = 2.7f * u
            p.color = 0xFFFFFFFF.toInt()
            c.drawText(sk.name, rr.left + 12.5f * uw, rr.top + 5f * u, p)
            p.textSize = 2.4f * u
            when {
                equipped -> { p.color = NeonUi.GOLD; c.drawText(s(R.string.shop_equipped),
                    rr.left + 12.5f * uw, rr.top + 9.5f * u, p) }
                owned -> { p.color = NeonUi.TEXT_DIM; c.drawText(s(R.string.shop_owned),
                    rr.left + 12.5f * uw, rr.top + 9.5f * u, p) }
                sk.iapProduct != null -> { p.color = NeonUi.ACCENT
                    c.drawText(s(R.string.shop_premium), rr.left + 12.5f * uw,
                        rr.top + 9.5f * u, p) }
                else -> {
                    p.color = NeonUi.GOLD
                    c.drawText("${sk.priceCoins}", rr.left + 16.5f * uw, rr.top + 9.5f * u, p)
                    NeonUi.coinDot(c, rr.left + 14.5f * uw, rr.top + 8.6f * u, 1.1f * u)
                }
            }
            val label = when {
                equipped -> s(R.string.shop_using)
                owned -> s(R.string.shop_use)
                else -> s(R.string.shop_buy)
            }
            val b = btn("skin:${sk.id}", l / uw, t / u, (l + 42f * uw) / uw, (rr.bottom) / u,
                label, if (equipped) NeonUi.STYLE_GHOST else NeonUi.STYLE_PRIMARY,
                enabled = true, u = u, uw = uw)
            // invisible hit area over the card (label drawn above by panel text)
            b.label = ""
            b.sub = null
        }
        // themes row
        val p = NeonUi.textLeft()
        p.textSize = 2.6f * u; p.color = NeonUi.TEXT_DIM
        c.drawText(s(R.string.shop_themes), 6f * uw, 81f * u, p)
        for ((i, th) in Economy.THEMES.withIndex()) {
            val l = (6 + i * 31).toFloat()
            val owned = Economy.isThemeOwned(th.id)
            val equipped = SaveStore.data.equippedTheme == th.id
            val label = if (equipped) s(R.string.shop_using)
            else if (owned) s(R.string.shop_use) else "${th.priceCoins}"
            val b = btn("theme:${th.id}", l, 83f, l + 28f, 89f, label,
                if (equipped) NeonUi.STYLE_GOLD else NeonUi.STYLE_GHOST,
                sub = th.name, u = u, uw = uw)
            NeonUi.drawButton(c, b, h.toFloat(), e)
        }
    }

    private fun drawCoinPacks(c: Canvas, w: Int, h: Int, u: Float, uw: Float, e: Float) {
        val packs = arrayOf(
            Triple(RevenueConfig.IAP_COINS_SMALL, s(R.string.shop_pack_small), s(R.string.price_small)),
            Triple(RevenueConfig.IAP_COINS_MED, s(R.string.shop_pack_med), s(R.string.price_med)),
            Triple(RevenueConfig.IAP_COINS_LARGE, s(R.string.shop_pack_large), s(R.string.price_large))
        )
        for ((i, pk) in packs.withIndex()) {
            val t = (21 + i * 14.5f) * u
            val rr = RectF(10f * uw, t, 90f * uw, t + 12.5f * u)
            NeonUi.drawPanel(c, rr, h.toFloat())
            val b = btn("iap:${pk.first}", 10f, t / u, 90f, t / u + 12.5f, pk.second,
                NeonUi.STYLE_GOLD, sub = pk.third, u = u, uw = uw)
            NeonUi.drawButton(c, b, h.toFloat(), e)
        }
        val vipT = 21f * u + 3 * 14.5f * u
        val vip = btn("iap:${RevenueConfig.SUB_VIP_MONTHLY}", 10f, vipT / u, 90f,
            (vipT + 12.5f * u) / u, s(R.string.shop_vip), NeonUi.STYLE_PRIMARY,
            sub = s(R.string.price_vip) + if (Locator.billing.isVip())
                "  ${s(R.string.shop_vip_active)}" else "", u = u, uw = uw)
        NeonUi.drawButton(c, vip, h.toFloat(), e)
    }

    private fun drawExtras(c: Canvas, w: Int, h: Int, u: Float, uw: Float, e: Float) {
        val adsGone = Locator.billing.isAdsRemoved()
        val ra = btn("iap:${RevenueConfig.IAP_REMOVE_ADS}", 10f, 21f, 90f, 33.5f,
            s(R.string.shop_remove_ads),
            if (adsGone) NeonUi.STYLE_GHOST else NeonUi.STYLE_GOLD,
            enabled = !adsGone, sub = if (adsGone) s(R.string.shop_owned) else s(R.string.price_remove_ads), u = u, uw = uw)
        NeonUi.drawButton(c, ra, h.toFloat(), e)

        val fr = btn("freeze", 10f, 35.5f, 90f, 48f, s(R.string.shop_freeze),
            NeonUi.STYLE_GHOST, sub = "${Economy.streakFreezeCost()} ${s(R.string.coins_short)}" +
                    "  (x${SaveStore.data.freezes})", u = u, uw = uw)
        NeonUi.drawButton(c, fr, h.toFloat(), e)

        val p = NeonUi.textCenter()
        p.textSize = 2.4f * u
        p.color = NeonUi.TEXT_DIM
        c.drawText(s(R.string.shop_extras_note), 50f * uw, 56f * u, p)
    }

    override fun onButton(idStr: String) {
        when {
            idStr == "tab0" -> tab = 0
            idStr == "tab1" -> tab = 1
            idStr == "tab2" -> tab = 2
            idStr.startsWith("skin:") -> {
                val sid = idStr.removePrefix("skin:")
                if (Economy.isSkinOwned(sid)) nav.equipSkin(sid) else nav.buySkinCoins(sid)
            }
            idStr.startsWith("theme:") -> nav.buyTheme(idStr.removePrefix("theme:"))
            idStr.startsWith("iap:") -> nav.purchaseIap(idStr.removePrefix("iap:"))
            idStr == "freeze" -> nav.buyFreeze()
        }
    }
}

// =========================================================================== RANKS

class RankScreen(ctx: Context, nav: UiNav) : Screen(ctx, nav) {
    override val id = ScreenId.RANKS

    override fun draw(c: Canvas, w: Int, h: Int, tSec: Float) {
        buttons.clear()
        val u = h / 100f; val uw = w / 100f
        val e = enter(tSec)

        val close = btn("close", 3f, 4f, 11f, 10.5f, "X", u = u, uw = uw)
        NeonUi.drawButton(c, close, h.toFloat(), e)

        val p = NeonUi.textLeft()
        p.textSize = 5f * u; p.color = 0xFFFFFFFF.toInt()
        c.drawText(s(R.string.ranks_title), 15f * uw, 9.5f * u, p)
        p.textSize = 2.7f * u; p.color = NeonUi.ACCENT
        c.drawText(s(R.string.ranks_season, LeaderboardStore.seasonNumber(),
            LeaderboardStore.daysLeftInSeason()), 15f * uw, 14f * u, p)

        val rows = LeaderboardStore.top(8)
        if (rows.isEmpty()) {
            p.textSize = 3f * u; p.color = NeonUi.TEXT_DIM
            p.textAlign = android.graphics.Paint.Align.CENTER
            c.drawText(s(R.string.ranks_empty), w / 2f, 40f * u, p)
            p.textAlign = android.graphics.Paint.Align.LEFT
        }
        for ((i, r) in rows.withIndex()) {
            val t = (18 + i * 7.2f) * u
            val rr = RectF(6f * uw, t, 94f * uw, t + 6.2f * u)
            val medal = when (r.rank) {
                1 -> NeonUi.GOLD
                2 -> 0xFFC8D8F0.toInt()
                3 -> 0xFFE0946A.toInt()
                else -> NeonUi.TEXT_DIM
            }
            NeonUi.drawChip(c, rr, "#${r.rank}", medal, h.toFloat(), filled = r.rank <= 3)
            val rp = NeonUi.textRight()
            rp.textSize = 3.4f * u
            rp.color = if (r.rank == 1) NeonUi.GOLD else 0xFFFFFFFF.toInt()
            c.drawText(r.score.toString(), 90f * uw, t + 4.4f * u, rp)
        }

        val last = LeaderboardStore.lastSeasonBest()
        if (last > 0) {
            NeonUi.drawChip(c, RectF(20f * uw, 77.5f * u, 80f * uw, 82f * u),
                s(R.string.ranks_last_season, last), NeonUi.TEXT_DIM, h.toFloat())
        }

        val share = btn("share", 28f, 84f, 72f, 91f, s(R.string.ranks_share),
            NeonUi.STYLE_GOLD, u = u, uw = uw)
        NeonUi.drawButton(c, share, h.toFloat(), e)
    }

    override fun onButton(idStr: String) {
        when (idStr) {
            "close" -> nav.open(ScreenId.MENU)
            "share" -> nav.share()
        }
    }
}

// =========================================================================== SETTINGS

class SettingsScreen(ctx: Context, nav: UiNav) : Screen(ctx, nav) {
    override val id = ScreenId.SETTINGS
    private var resetArmedAtNs = 0L

    private fun resetArmed(): Boolean =
        resetArmedAtNs > 0 && System.nanoTime() - resetArmedAtNs < 3_000_000_000L

    override fun draw(c: Canvas, w: Int, h: Int, tSec: Float) {
        buttons.clear()
        val u = h / 100f; val uw = w / 100f
        val e = enter(tSec)
        val d = SaveStore.data

        val close = btn("close", 3f, 4f, 11f, 10.5f, "X", u = u, uw = uw)
        NeonUi.drawButton(c, close, h.toFloat(), e)

        val p = NeonUi.textLeft()
        p.textSize = 5f * u; p.color = 0xFFFFFFFF.toInt()
        c.drawText(s(R.string.settings_title), 15f * uw, 9.5f * u, p)

        toggleRow(c, w, h, u, uw, 20f, s(R.string.settings_music), d.musicOn, "music")
        toggleRow(c, w, h, u, uw, 29f, s(R.string.settings_sfx), d.sfxOn, "sfx")
        toggleRow(c, w, h, u, uw, 38f, s(R.string.settings_haptics), d.hapticsOn, "haptics")

        val notifNeeded = d.notifPermissionAsked == false
        val nb = btn("notif", 10f, 47f, 90f, 54f, s(R.string.settings_notif),
            if (notifNeeded) NeonUi.STYLE_PRIMARY else NeonUi.STYLE_GHOST,
            sub = if (notifNeeded) s(R.string.settings_notif_sub) else s(R.string.settings_notif_on), u = u, uw = uw)
        NeonUi.drawButton(c, nb, h.toFloat(), e)

        val armed = resetArmed()
        val rb = btn("reset", 10f, 60f, 90f, 67f,
            if (armed) s(R.string.settings_reset_confirm) else s(R.string.settings_reset),
            NeonUi.STYLE_DANGER, sub = if (armed) s(R.string.settings_reset_sub2)
            else s(R.string.settings_reset_sub), u = u, uw = uw)
        NeonUi.drawButton(c, rb, h.toFloat(), e)

        val p2 = NeonUi.textCenter()
        p2.textSize = 2.2f * u; p2.color = NeonUi.TEXT_DIM
        c.drawText(s(R.string.settings_stats, d.totalRuns, d.bestScore),
            w / 2f, 78f * u, p2)
        c.drawText(s(R.string.settings_credits), w / 2f, 90f * u, p2)
    }

    private fun toggleRow(c: Canvas, w: Int, h: Int, u: Float, uw: Float,
                          y: Float, label: String, on: Boolean, key: String) {
        val p = NeonUi.textLeft()
        p.textSize = 3.4f * u; p.color = 0xFFE8EEFF.toInt()
        c.drawText(label, 10f * uw, (y + 3.8f) * u, p)
        val tr = RectF(74f * uw, y * u, 90f * uw, (y + 4.6f) * u)
        NeonUi.drawToggle(c, tr, on, h.toFloat())
        // invisible tap target
        val b = btn("tgl:$key", 60f, y - 1f, 92f, y + 6.5f, "", u = u, uw = uw)
        b.label = ""
    }

    override fun onButton(idStr: String) {
        when {
            idStr == "close" -> nav.open(ScreenId.MENU)
            idStr == "notif" -> nav.requestNotifPermission()
            idStr == "reset" -> {
                if (resetArmed()) { nav.resetProgress(); resetArmedAtNs = 0L }
                else resetArmedAtNs = System.nanoTime()
            }
            idStr.startsWith("tgl:") -> {
                val key = idStr.removePrefix("tgl:")
                val d = SaveStore.data
                when (key) {
                    "music" -> nav.setSetting("music", !d.musicOn)
                    "sfx" -> nav.setSetting("sfx", !d.sfxOn)
                    "haptics" -> nav.setSetting("haptics", !d.hapticsOn)
                }
            }
        }
    }
}

// =========================================================================== PAUSE

class PauseScreen(ctx: Context, nav: UiNav) : Screen(ctx, nav) {
    override val id = ScreenId.PAUSE

    override fun draw(c: Canvas, w: Int, h: Int, tSec: Float) {
        buttons.clear()
        val u = h / 100f; val uw = w / 100f
        val e = enter(tSec, 0.2f)

        val dim = NeonUi.fillOpaque()
        dim.color = Color.BLACK
        dim.alpha = (170 * e).toInt()
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), dim)
        dim.alpha = 255

        NeonUi.glowTitle(c, s(R.string.pause_title), w / 2f, 34f * u, 7f * u,
            0xFFFFFFFF.toInt(), NeonUi.ACCENT)

        val resume = btn("resume", 26f, 42f, 74f, 49.5f, s(R.string.pause_resume),
            NeonUi.STYLE_PRIMARY, u = u, uw = uw)
        val restart = btn("restart", 26f, 52f, 74f, 59.5f, s(R.string.pause_restart), u = u, uw = uw)
        val menu = btn("menu", 26f, 62f, 74f, 69.5f, s(R.string.pause_menu), u = u, uw = uw)
        NeonUi.drawButton(c, resume, h.toFloat(), e)
        NeonUi.drawButton(c, restart, h.toFloat(), e)
        NeonUi.drawButton(c, menu, h.toFloat(), e)
    }

    override fun onButton(idStr: String) {
        when (idStr) {
            "resume" -> nav.resumeGame()
            "restart" -> nav.restart()
            "menu" -> nav.toMenu()
        }
    }
}
