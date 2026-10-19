package com.michaelunkai.neonflip

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.michaelunkai.neonflip.analytics.RevenueAnalytics
import com.michaelunkai.neonflip.game.AudioSynth
import com.michaelunkai.neonflip.game.GameEngine
import com.michaelunkai.neonflip.game.GameView
import com.michaelunkai.neonflip.game.Haptics
import com.michaelunkai.neonflip.monetization.RevenueConfig
import com.michaelunkai.neonflip.retention.BonusDrops
import com.michaelunkai.neonflip.retention.ComebackGift
import com.michaelunkai.neonflip.retention.DifficultyTuner
import com.michaelunkai.neonflip.retention.Economy
import com.michaelunkai.neonflip.retention.LeaderboardStore
import com.michaelunkai.neonflip.retention.NotificationHelper
import com.michaelunkai.neonflip.retention.QuestManager
import com.michaelunkai.neonflip.retention.SaveStore
import com.michaelunkai.neonflip.retention.StreakManager
import com.michaelunkai.neonflip.ui.Locator
import com.michaelunkai.neonflip.ui.NeonOverlayView
import com.michaelunkai.neonflip.ui.ScreenId
import com.michaelunkai.neonflip.ui.ShareCard
import com.michaelunkai.neonflip.ui.UiNav
import java.io.File

/**
 * Single-activity host: game surface + canvas UI overlay. Cold start is instant —
 * everything heavy already runs async from [NeonFlipApp].
 */
class MainActivity : AppCompatActivity(), UiNav, GameEngine.Events {

    private lateinit var engine: GameEngine
    private lateinit var gameView: GameView
    private lateinit var overlay: NeonOverlayView

    private var lastResult: GameEngine.RunResult? = null
    private var fromResults = false
    private var pendingGift = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        Haptics.init(this)

        engine = GameEngine()
        engine.events = this

        val root = FrameLayout(this)
        gameView = GameView(this, engine)
        overlay = NeonOverlayView(this, engine, this)
        root.addView(gameView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        applyImmersive()

        // --- app-open retention sequence ---
        LeaderboardStore.ensureSeason()
        val streakInfo = StreakManager.onAppOpen()
        pendingGift = ComebackGift.onAppOpen()
        QuestManager.ensureToday()
        overlay.menu.giftAmount = pendingGift
        overlay.show(ScreenId.MENU)

        if (streakInfo.rescuedByFreeze) {
            overlay.banner(getString(R.string.banner_freeze_used), 0xFFFF9E3D.toInt())
        }
        if (streakInfo.broke && streakInfo.streak == 0) {
            overlay.banner(getString(R.string.banner_streak_broke), 0xFFFF2E97.toInt())
        }

        // Quest completion celebrations
        QuestManager.onQuestCompleted = { q ->
            overlay.banner(getString(R.string.banner_quest_done, q.title, q.reward),
                0xFFFFD54A.toInt())
            AudioSynth.play(AudioSynth.Sfx.QUEST)
        }

        // Banner ads warm-up (no-op when monetization layer is absent).
        try { Locator.ads.loadBanner() } catch (_: Throwable) {}

        // Notification permission ask (once, on the menu).
        if (!SaveStore.data.notifPermissionAsked && NotificationHelper.needsPermission(this)) {
            SaveStore.edit { it.notifPermissionAsked = true }
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NotificationHelper.PERMISSION_REQ)
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onResume() {
        super.onResume()
        applyImmersive()
        if (engine.state == GameEngine.State.RUNNING && overlay.current == null) {
            engine.paused = true
            overlay.show(ScreenId.PAUSE)
        }
        if (SaveStore.data.musicOn) AudioSynth.startMusic()
    }

    override fun onPause() {
        super.onPause()
        AudioSynth.pauseMusic()
        if (engine.state == GameEngine.State.RUNNING) engine.paused = true
        SaveStore.persist(force = true) // flush throttled edits before backgrounding
        try { NotificationHelper.scheduleStreakSave(this) } catch (_: Throwable) {}
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    private fun applyImmersive() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (overlay.onBack()) return
        if (engine.state == GameEngine.State.RUNNING) {
            engine.paused = true
            overlay.show(ScreenId.PAUSE)
            return
        }
        moveTaskToBack(true) // menu back = minimize; instant resume next open
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NotificationHelper.PERMISSION_REQ) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                overlay.banner(getString(R.string.banner_notif_on), 0xFF00E5FF.toInt())
                NotificationHelper.scheduleStreakSave(this)
            }
        }
    }

    // ------------------------------------------------------------------ engine events

    override fun onGameOver(r: GameEngine.RunResult) {
        val prevBest = SaveStore.data.bestScore
        val newBest = r.score > prevBest

        // credit + persist
        Economy.addCoins(r.coins)
        LeaderboardStore.addScore(r.score)
        DifficultyTuner.onRunEnd(r.score, prevBest)
        SaveStore.edit { it.totalRuns += 1; it.totalDistanceM += r.distanceM }

        // quests
        QuestManager.onMetric(QuestManager.Metric.COINS, r.coins)
        QuestManager.onMetric(QuestManager.Metric.DISTANCE, r.distanceM)
        QuestManager.onMetric(QuestManager.Metric.RUNS, 1)
        QuestManager.onMetric(QuestManager.Metric.ONE_RUN_SCORE, r.score)
        QuestManager.onMetric(QuestManager.Metric.MAX_COMBO, r.maxCombo)

        // variable-ratio mystery bonus
        val bonus = BonusDrops.onGameOver()

        lastResult = r
        fromResults = true

        RevenueAnalytics.log("run_end", mapOf(
            "score" to r.score, "coins" to r.coins, "combo" to r.maxCombo,
            "near" to r.nearMisses, "new_best" to newBest))

        overlay.showResults(r, newBest, bonus)
    }

    override fun onStat(stat: GameEngine.Stat, amount: Int) {
        val metric = when (stat) {
            GameEngine.Stat.COIN -> QuestManager.Metric.COINS
            GameEngine.Stat.GATE -> QuestManager.Metric.GATES
            GameEngine.Stat.NEAR_MISS -> QuestManager.Metric.NEAR_MISS
            GameEngine.Stat.FLIP -> QuestManager.Metric.FLIPS
        }
        QuestManager.onMetric(metric, amount)
    }

    override fun onCombo(level: Int) {
        if (level in intArrayOf(4, 6, 8)) {
            overlay.banner("COMBO x$level", 0xFFFFD54A.toInt())
        }
    }

    // ------------------------------------------------------------------ UiNav

    override fun play() {
        StreakManager.onRunStartedToday()
        fromResults = false
        overlay.hide()
        engine.startRun()
        if (SaveStore.data.musicOn) AudioSynth.startMusic()
    }

    override fun restart() {
        maybeInterstitial()
        fromResults = false
        overlay.hide()
        engine.startRun()
    }

    override fun toMenu() {
        maybeInterstitial()
        fromResults = false
        engine.resetToMenu()
        overlay.menu.giftAmount = pendingGift
        overlay.show(ScreenId.MENU)
    }

    private fun maybeInterstitial() {
        if (fromResults && !Locator.billing.isAdsRemoved()) {
            try { Locator.ads.maybeShowInterstitial("game_over") } catch (_: Throwable) {}
        }
    }

    override fun pauseGame() {
        engine.paused = true
        overlay.show(ScreenId.PAUSE)
    }

    override fun resumeGame() {
        overlay.hide()
        // soft ramp back to full speed so the player is never hit cold at resume
        if (engine.state == GameEngine.State.RUNNING) engine.slowmoT = 0.4f
        engine.paused = false
    }

    override fun reviveByAd() {
        val r = lastResult ?: return
        if (r.wasRevived) return
        if (!Locator.adsReady || !Locator.ads.isRewardedReady()) {
            toast(R.string.err_ad_unavailable)
            return
        }
        Locator.ads.showRewarded("revive",
            onReward = { doRevive() },
            onDismiss = { /* stay on results */ })
    }

    override fun reviveByCoins() {
        val r = lastResult ?: return
        if (r.wasRevived) return
        if (!Economy.spendCoins(RevenueConfig.REVIVE_COST_COINS)) {
            toast(R.string.err_not_enough_coins)
            return
        }
        doRevive()
    }

    private fun doRevive() {
        overlay.hide()
        engine.revive()
        fromResults = false
        overlay.banner(getString(R.string.banner_revived), 0xFFB4FF39.toInt())
    }

    override fun doubleCoinsByAd() {
        val r = lastResult ?: return
        if (!Locator.adsReady || !Locator.ads.isRewardedReady()) {
            toast(R.string.err_ad_unavailable)
            return
        }
        Locator.ads.showRewarded("double_coins",
            onReward = {
                Economy.addCoins(r.coins) // second half of the 2x
                overlay.markDoubled()
                overlay.banner(getString(R.string.banner_doubled, r.coins), 0xFFFFD54A.toInt())
                AudioSynth.play(AudioSynth.Sfx.BUY)
            },
            onDismiss = { })
    }

    override fun open(screen: ScreenId) {
        overlay.show(screen)
    }

    override fun share() {
        val r = lastResult
        val score = r?.score ?: SaveStore.data.bestScore
        val coins = r?.coins ?: 0
        val best = SaveStore.data.bestScore
        val card: Bitmap = ShareCard.build(this, score, best, coins,
            newBest = r != null && score >= best && best > 0)
        try {
            val dir = File(cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "neonflip_score.png")
            file.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri: Uri = FileProvider.getUriForFile(this,
                "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, getString(R.string.share_chooser)))
        } catch (t: Throwable) {
            Toast.makeText(this, R.string.err_share, Toast.LENGTH_SHORT).show()
        }
        Haptics.purchase()
    }

    override fun buySkinCoins(id: String) {
        when (Economy.buySkinWithCoins(id)) {
            Economy.BuyResult.OK -> {
                AudioSynth.play(AudioSynth.Sfx.BUY); Haptics.purchase()
                overlay.banner(getString(R.string.banner_skin_bought), 0xFFB4FF39.toInt())
            }
            Economy.BuyResult.INSUFFICIENT -> {
                AudioSynth.play(AudioSynth.Sfx.UI)
                toast(R.string.err_not_enough_coins)
            }
            else -> {}
        }
    }

    override fun equipSkin(id: String) {
        Economy.equipSkin(id)
        AudioSynth.play(AudioSynth.Sfx.UI)
    }

    override fun buyTheme(id: String) {
        when (Economy.buyThemeWithCoins(id)) {
            Economy.BuyResult.OK -> {
                AudioSynth.play(AudioSynth.Sfx.BUY); Haptics.purchase()
                overlay.banner(getString(R.string.banner_theme_bought), 0xFFB4FF39.toInt())
            }
            Economy.BuyResult.OWNED -> Economy.equipTheme(id)
            Economy.BuyResult.INSUFFICIENT -> {
                AudioSynth.play(AudioSynth.Sfx.UI)
                toast(R.string.err_not_enough_coins)
            }
            else -> {}
        }
    }

    override fun buyFreeze() {
        if (StreakManager.buyFreeze()) {
            AudioSynth.play(AudioSynth.Sfx.BUY); Haptics.purchase()
            overlay.banner(getString(R.string.banner_freeze_bought), 0xFFFF9E3D.toInt())
        } else {
            toast(R.string.err_not_enough_coins)
        }
    }

    override fun purchaseIap(productId: String) {
        if (!Locator.billingReady) {
            toast(R.string.err_store_unavailable)
            return
        }
        try {
            Locator.billing.purchase(productId)
        } catch (t: Throwable) {
            toast(R.string.err_store_unavailable)
        }
    }

    override fun claimStreak() {
        val amount = StreakManager.claimReward()
        if (amount > 0) {
            AudioSynth.play(AudioSynth.Sfx.QUEST); Haptics.purchase()
            overlay.banner(getString(R.string.banner_daily_claimed, amount), 0xFFFFD54A.toInt())
        }
    }

    override fun claimGift() {
        overlay.menu.giftAmount = 0
        pendingGift = 0
        AudioSynth.play(AudioSynth.Sfx.QUEST); Haptics.purchase()
        overlay.banner(getString(R.string.banner_gift_claimed), 0xFFFFD54A.toInt())
    }

    override fun setSetting(key: String, on: Boolean) {
        SaveStore.edit {
            when (key) {
                "music" -> it.musicOn = on
                "sfx" -> it.sfxOn = on
                "haptics" -> it.hapticsOn = on
            }
        }
        when (key) {
            "music" -> AudioSynth.setMusicOn(on)
            "sfx" -> AudioSynth.setSfxOn(on)
        }
        AudioSynth.play(AudioSynth.Sfx.UI)
    }

    override fun requestNotifPermission() {
        if (NotificationHelper.needsPermission(this)) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NotificationHelper.PERMISSION_REQ)
        } else {
            overlay.banner(getString(R.string.settings_notif_on), 0xFF00E5FF.toInt())
        }
    }

    override fun resetProgress() {
        SaveStore.reset()
        LeaderboardStore.ensureSeason()
        QuestManager.ensureToday()
        pendingGift = 0
        overlay.menu.giftAmount = 0
        AudioSynth.play(AudioSynth.Sfx.DEATH)
        overlay.banner(getString(R.string.banner_reset), 0xFFFF4D5E.toInt())
    }

    private fun toast(res: Int) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
    }
}
