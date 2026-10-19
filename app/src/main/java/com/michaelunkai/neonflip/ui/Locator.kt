package com.michaelunkai.neonflip.ui

import android.content.Context
import android.util.Log
import com.michaelunkai.neonflip.monetization.AdEngine
import com.michaelunkai.neonflip.monetization.BillingEngine

/**
 * Lazy ServiceLocator bridging game/ui/retention code to the monetization layer.
 *
 * GAME-CORE only CALLS the fixed interfaces [AdEngine]/[BillingEngine]; the monetization
 * agent owns the real implementations. Wiring convention (any of these, if present on the
 * classpath, is picked up automatically via reflection at bootstrap):
 *   1. class/object `com.michaelunkai.neonflip.monetization.MonetizationEngine`
 *      implementing BOTH AdEngine and BillingEngine (no-arg construction), OR
 *   2. separate `com.michaelunkai.neonflip.monetization.AdEngineImpl` and
 *      `com.michaelunkai.neonflip.monetization.BillingEngineImpl` classes.
 *
 * If none is present, the no-op fallbacks below keep the whole game 100% functional:
 * rewarded buttons show as "unavailable", interstitial calls vanish, billing purchases
 * degrade to a friendly toast. No crashes, no lint errors, no hard dependency.
 */
object Locator {

    @Volatile var ads: AdEngine = NoopAds
        private set
    @Volatile var billing: BillingEngine = NoopBilling
        private set

    /** True once the real AdEngine finished init (rewarded buttons gate on this). */
    @Volatile var adsReady = false
        private set
    @Volatile var billingReady = false
        private set

    private const val TAG = "Locator"

    /** Resolve implementations IF present; otherwise keep no-ops. Cheap (a few Class.forName). */
    fun bootstrap(@Suppress("UNUSED_PARAMETER") context: Context) {
        tryFindBoth() || tryFindSeparate() || noImplsFound()
    }

    /** Called once from Application: async ad SDK warmup. */
    fun initAds() {
        try {
            ads.init { adsReady = true }
        } catch (t: Throwable) {
            Log.w(TAG, "AdEngine.init failed, ads stay unavailable", t)
            ads = NoopAds
        }
    }

    /** Called once from Application: async billing client warmup. */
    fun initBilling() {
        try {
            billing.start { billingReady = true }
        } catch (t: Throwable) {
            Log.w(TAG, "BillingEngine.start failed, purchases stay unavailable", t)
            billing = NoopBilling
        }
    }

    private fun tryFindBoth(): Boolean = try {
        val k = Class.forName("com.michaelunkai.neonflip.monetization.MonetizationEngine")
        val inst = instanceOf(k)
        if (inst is AdEngine && inst is BillingEngine) {
            ads = inst; billing = inst; true
        } else false
    } catch (t: Throwable) { false }

    private fun tryFindSeparate(): Boolean {
        var ok = false
        try {
            val a = instanceOf(Class.forName("com.michaelunkai.neonflip.monetization.AdEngineImpl"))
            if (a is AdEngine) { ads = a; ok = true }
        } catch (_: Throwable) {}
        try {
            val b = instanceOf(Class.forName("com.michaelunkai.neonflip.monetization.BillingEngineImpl"))
            if (b is BillingEngine) { billing = b; ok = true }
        } catch (_: Throwable) {}
        return ok
    }

    /** Supports both `object` (Kotlin INSTANCE field) and plain class (no-arg constructor). */
    private fun instanceOf(k: Class<*>): Any = try {
        k.getDeclaredField("INSTANCE").get(null)
    } catch (_: Throwable) {
        k.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
    }

    private fun noImplsFound(): Boolean {
        Log.i(TAG, "No monetization impls found on classpath - running with no-op revenue layer")
        return false
    }

    // --- No-op fallbacks (graceful degradation only; real impls live in monetization/) ---

    private object NoopAds : AdEngine {
        override fun init(onReady: () -> Unit) { /* never ready */ }
        override fun showRewarded(placement: String, onReward: () -> Unit, onDismiss: () -> Unit) = onDismiss()
        override fun maybeShowInterstitial(placement: String) {}
        override fun loadBanner(): Unit? = null
        override fun isRewardedReady(): Boolean = false
    }

    private object NoopBilling : BillingEngine {
        override fun start(onReady: () -> Unit) { /* never ready */ }
        override fun purchase(productId: String) {}
        override fun isAdsRemoved(): Boolean = false
        override fun isVip(): Boolean = false
    }
}
