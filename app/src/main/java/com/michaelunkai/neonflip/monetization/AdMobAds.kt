package com.michaelunkai.neonflip.monetization

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.michaelunkai.neonflip.analytics.RevenueAnalytics
import java.lang.ref.WeakReference

/**
 * AdMobAds — AdEngine implementation (Google Mobile Ads SDK v23).
 *
 * PRODUCTION-ID PLACEHOLDERS: the unit IDs below are Google's PUBLIC TEST IDs.
 * Before store release replace [PRODUCTION_BANNER_ID], [PRODUCTION_INTERSTITIAL_ID],
 * [PRODUCTION_REWARDED_ID] with real units and flip [USE_TEST_IDS] to false
 * (the AdMob APP ID meta-data in AndroidManifest.xml must be swapped too —
 * currently the test app ID ca-app-pub-3940256099942544~3347511713).
 *
 * Design invariants:
 *  - Fully offline-graceful: EVERY path no-ops with zero crash when the SDK is
 *    missing, ads are unfilled, there is no network, or no Activity is attached.
 *  - Preload pipeline: the next interstitial + rewarded start loading the
 *    instant any ad is dismissed; failures retry with exponential backoff.
 *  - Interstitials are NEVER shown directly — every request is arbitrated by
 *    [ToleranceEngine]; VIP / ads-removed users keep rewarded ads working.
 *
 * GAME INTEGRATION (exact calls, from the owning Activity):
 *      AdMobAds.attach(this)               // in onCreate, BEFORE init
 *      AdMobAds.init { /* sdk ready */ }   // once
 */
@SuppressLint("MissingPermission")
object AdMobAds : AdEngine {

    // --- IDs: Google TEST units active; PRODUCTION-ID placeholders alongside ---
    private const val USE_TEST_IDS = true
    const val PRODUCTION_BANNER_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN"      // PRODUCTION-ID placeholder
    const val PRODUCTION_INTERSTITIAL_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN" // PRODUCTION-ID placeholder
    const val PRODUCTION_REWARDED_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN"     // PRODUCTION-ID placeholder

    private const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    // --- exponential backoff for failed loads ---
    private const val BACKOFF_BASE_MS = 5_000L
    private const val BACKOFF_FACTOR = 2
    private const val BACKOFF_MAX_ATTEMPTS = 5
    private const val BACKOFF_CEILING_MS = 300_000L

    private val bannerId: String get() = if (USE_TEST_IDS) TEST_BANNER_ID else PRODUCTION_BANNER_ID
    private val interstitialId: String get() = if (USE_TEST_IDS) TEST_INTERSTITIAL_ID else PRODUCTION_INTERSTITIAL_ID
    private val rewardedId: String get() = if (USE_TEST_IDS) TEST_REWARDED_ID else PRODUCTION_REWARDED_ID

    private var appContext: Context? = null
    private var activityRef: WeakReference<Activity>? = null
    private val main = Handler(Looper.getMainLooper())

    @Volatile var sdkInitialized = false
        private set

    private var banner: AdView? = null
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null

    private var interstitialAttempts = 0
    private var rewardedAttempts = 0
    private var bannerAttempts = 0
    private var interstitialLoading = false
    private var rewardedLoading = false
    private var bannerLoading = false

    // ---------------------------------------------------------------- init

    /**
     * Attach a context: Application for engine wiring, Activity for ad shows
     * (game should re-attach the current Activity on every onResume — or let
     * MonetizationEngine's lifecycle tracker do it).
     */
    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        (context as? Activity)?.let { activityRef = WeakReference(it) }
        ToleranceEngine.init(context)
        RevenueAnalytics.init(context)
    }

    override fun init(onReady: () -> Unit) {
        val ctx = appContext
        if (ctx == null) {
            RevenueAnalytics.log("ads_init", mapOf("result" to "no_context"))
            runReady(onReady)
            return
        }
        try {
            activityRef?.get()?.let { ConsentManager.maybeGatherConsent(it) }
            // Wire tolerance gates to billing entitlements (no package cycle).
            ToleranceEngine.adsRemovedProvider = { BillingEngineImpl.isAdsRemoved() }
            ToleranceEngine.vipProvider = { VipManager.isVip() }
            MobileAds.initialize(ctx) {
                sdkInitialized = true
                preloadAll("init")
                RevenueAnalytics.log("ads_init", mapOf("result" to "ok"))
                runReady(onReady)
            }
        } catch (t: Throwable) {
            // Missing SDK / Play services absent -> zero-crash degraded mode.
            sdkInitialized = false
            RevenueAnalytics.log("ads_init", mapOf("result" to "unavailable", "error" to (t.message ?: "unknown")))
            runReady(onReady)
        }
    }

    private fun runReady(onReady: () -> Unit) {
        try { main.post { try { onReady() } catch (t: Throwable) {} } } catch (t: Throwable) { try { onReady() } catch (ignored: Throwable) {} }
    }

    // ---------------------------------------------------------------- AdEngine contract

    /** Contract entry: the TOLERANCE ENGINE decides. Hard VIP gate here as belt-and-braces. */
    override fun maybeShowInterstitial(placement: String) {
        val activity = activityRef?.get()
        if (activity == null || activity.isFinishing) return
        if (VipManager.isVip()) {
            RevenueAnalytics.log("ad_skip", mapOf("format" to "interstitial", "placement" to placement, "reason" to "VIP"))
            return
        }
        val decision = ToleranceEngine.requestInterstitial(placement)
        if (!decision.show) {
            RevenueAnalytics.log(
                "ad_skip",
                mapOf("format" to "interstitial", "placement" to placement,
                    "reason" to decision.reason, "tolerance" to decision.tolerance)
            )
            return
        }
        val ad = interstitial
        if (ad == null) {
            ToleranceEngine.onInterstitialUnavailable(placement)
            preloadInterstitial()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                ToleranceEngine.onInterstitialShown(placement)
                RevenueAnalytics.log("ad_show", mapOf("format" to "interstitial", "placement" to placement))
            }
            override fun onAdClicked() {
                RevenueAnalytics.log("ad_click", mapOf("format" to "interstitial", "placement" to placement))
            }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitial = null
                preloadInterstitial()
            }
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                preloadInterstitial() // preload-next-immediately invariant
            }
        }
        try {
            ad.show(activity)
        } catch (t: Throwable) {
            interstitial = null
            ToleranceEngine.onInterstitialUnavailable(placement)
            preloadInterstitial()
        }
    }

    /**
     * Contract entry: rewarded video. Rewarded ads are NEVER tolerance-gated
     * (they are user-opt-in and always recover tolerance). If no ad is ready
     * this invokes [onDismiss] immediately so game flow never hangs, and
     * records an offer decline + starts a backgrounded load.
     */
    override fun showRewarded(placement: String, onReward: () -> Unit, onDismiss: () -> Unit) {
        val activity = activityRef?.get()
        val ad = rewarded
        if (activity == null || activity.isFinishing || ad == null) {
            ToleranceEngine.recordAdDecline(placement)
            try { onDismiss() } catch (t: Throwable) {}
            preloadRewarded()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() { /* logged on reward or below */ }
            override fun onAdClicked() {
                RevenueAnalytics.log("ad_click", mapOf("format" to "rewarded", "placement" to placement))
            }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewarded = null
                ToleranceEngine.recordAdDecline(placement)
                try { onDismiss() } catch (t: Throwable) {}
                preloadRewarded()
            }
            override fun onAdDismissedFullScreenContent() {
                rewarded = null
                try { onDismiss() } catch (t: Throwable) {}
                preloadRewarded() // preload-next-immediately invariant
            }
        }
        try {
            ad.show(activity) { rewardItem ->
                ToleranceEngine.recordRewardedWatch(voluntary = true, placement = placement)
                try { onReward() } catch (t: Throwable) {}
            }
        } catch (t: Throwable) {
            rewarded = null
            ToleranceEngine.recordAdDecline(placement)
            try { onDismiss() } catch (t: Throwable) {}
            preloadRewarded()
        }
    }

    /**
     * Contract entry: creates + loads a cached banner. UI reads it via [banner]
     * and adds the returned AdView to a layout. Returns null by contract.
     */
    override fun loadBanner(): Unit? {
        val activity = activityRef?.get() ?: return null
        if (bannerLoading || banner != null) return null
        if (!isOnline()) return null
        return try {
            bannerLoading = true
            val view = AdView(activity)
            view.setAdSize(AdSize.BANNER)
            view.adUnitId = bannerId
            view.adListener = object : AdListener() {
                override fun onAdClicked() {
                    RevenueAnalytics.log("ad_click", mapOf("format" to "banner"))
                }
                override fun onAdLoaded() {
                    bannerLoading = false
                    bannerAttempts = 0
                    RevenueAnalytics.log("ad_show", mapOf("format" to "banner"))
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    bannerLoading = false
                    scheduleBannerRetry()
                }
            }
            banner = view
            view.loadAd(AdRequest.Builder().build())
            null
        } catch (t: Throwable) {
            bannerLoading = false
            scheduleBannerRetry()
            null
        }
    }

    /** Live banner for UI embedding (null until loadBanner() succeeds). */
    fun banner(): AdView? = banner

    /** Call from the owning Activity's onDestroy to avoid leaks. */
    fun detachBanner() {
        try { banner?.destroy() } catch (t: Throwable) {}
        banner = null
        bannerLoading = false
    }

    override fun isRewardedReady(): Boolean = rewarded != null

    fun isInterstitialReady(): Boolean = interstitial != null

    // ---------------------------------------------------------------- preload pipeline

    /** (Re)load both full-screen formats now. Called at init and after every show. */
    fun preloadAll(why: String) {
        RevenueAnalytics.log("preload", mapOf("why" to why))
        preloadInterstitial()
        preloadRewarded()
    }

    fun preloadInterstitial() {
        val ctx = appContext ?: return
        if (interstitial != null || interstitialLoading || !sdkInitialized || !isOnline()) return
        if (interstitialAttempts >= BACKOFF_MAX_ATTEMPTS) return
        interstitialLoading = true
        try {
            InterstitialAd.load(ctx, interstitialId, AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitial = ad
                        interstitialLoading = false
                        interstitialAttempts = 0
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialLoading = false
                        scheduleRetry(::preloadInterstitial) { interstitialAttempts++ }
                    }
                })
        } catch (t: Throwable) {
            interstitialLoading = false
            scheduleRetry(::preloadInterstitial) { interstitialAttempts++ }
        }
    }

    fun preloadRewarded() {
        val ctx = appContext ?: return
        if (rewarded != null || rewardedLoading || !sdkInitialized || !isOnline()) return
        if (rewardedAttempts >= BACKOFF_MAX_ATTEMPTS) return
        rewardedLoading = true
        try {
            RewardedAd.load(ctx, rewardedId, AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewarded = ad
                        rewardedLoading = false
                        rewardedAttempts = 0
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedLoading = false
                        scheduleRetry(::preloadRewarded) { rewardedAttempts++ }
                    }
                })
        } catch (t: Throwable) {
            rewardedLoading = false
            scheduleRetry(::preloadRewarded) { rewardedAttempts++ }
        }
    }

    private fun scheduleBannerRetry() {
        scheduleRetry(::loadBanner) { bannerAttempts++ }
    }

    /** Exponential backoff: 5s, 10s, 20s, 40s, 80s (ceiling 5 min), max 5 tries per burst. */
    private fun scheduleRetry(load: () -> Unit, bump: () -> Unit) {
        bump()
        val attempts = maxOf(interstitialAttempts, rewardedAttempts, bannerAttempts)
        val delay = minOf(BACKOFF_BASE_MS * (1L shl minOf(attempts, 6)), BACKOFF_CEILING_MS)
        try { main.postDelayed({ try { load() } catch (t: Throwable) {} }, delay) } catch (t: Throwable) {}
    }

    // ---------------------------------------------------------------- offline + debug

    private fun isOnline(): Boolean {
        return try {
            val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true // assume online; loads fail gracefully anyway
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (t: Throwable) { true }
    }

    /** Live state for the debug panel. */
    fun debugStatus(): Map<String, Any?> = mapOf(
        "sdk_initialized" to sdkInitialized,
        "consent" to ConsentManager.state.name,
        "consent_sdk_present" to ConsentManager.sdkPresent(),
        "interstitial_ready" to (interstitial != null),
        "rewarded_ready" to (rewarded != null),
        "banner_ready" to (banner != null),
        "interstitial_backoff_attempts" to interstitialAttempts,
        "rewarded_backoff_attempts" to rewardedAttempts,
        "banner_backoff_attempts" to bannerAttempts,
        "online" to isOnline(),
        "banner_id" to bannerId,
        "interstitial_id" to interstitialId,
        "rewarded_id" to rewardedId
    )
}
