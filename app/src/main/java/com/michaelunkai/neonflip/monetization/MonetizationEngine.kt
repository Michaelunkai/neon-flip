package com.michaelunkai.neonflip.monetization

import android.app.Activity
import android.app.Application
import com.michaelunkai.neonflip.analytics.RevenueAnalytics

/**
 * MonetizationEngine — BOTH-interfaces facade (AdEngine + BillingEngine) that
 * `ui/Locator` discovers FIRST via reflection
 * (`com.michaelunkai.neonflip.monetization.MonetizationEngine`). It delegates
 * everything to [AdMobAds] and [BillingEngineImpl].
 *
 * Why it exists: Locator.bootstrap(context) + initAds()/initBilling() never
 * hand us the Context or an Activity (the fixed contract methods have no
 * params). This facade therefore self-bootstraps:
 *   1. grabs the Application via ActivityThread/AppGlobals reflection
 *      (crash-safe, fails -> graceful no-op),
 *   2. initializes every subsystem with it,
 *   3. registers ActivityLifecycleCallbacks so the CURRENT activity is always
 *      attached for full-screen ad shows, billing flows and UMP consent.
 *
 * Games that prefer explicit wiring may still call AdMobAds.attach(activity) /
 * BillingEngineImpl.attach(activity) themselves — both paths coexist.
 */
object MonetizationEngine : AdEngine, BillingEngine {

    // ------------------------------------------------------------ AdEngine (delegates)

    override fun init(onReady: () -> Unit) {
        bootstrapSelf()
        AdMobAds.init(onReady)
    }

    override fun maybeShowInterstitial(placement: String) = AdMobAds.maybeShowInterstitial(placement)

    override fun showRewarded(placement: String, onReward: () -> Unit, onDismiss: () -> Unit) =
        AdMobAds.showRewarded(placement, onReward, onDismiss)

    override fun loadBanner(): Unit? = AdMobAds.loadBanner()

    override fun isRewardedReady(): Boolean = AdMobAds.isRewardedReady()

    // ------------------------------------------------------------ BillingEngine (delegates)

    override fun start(onReady: () -> Unit) {
        bootstrapSelf()
        BillingEngineImpl.start(onReady)
    }

    override fun purchase(productId: String) = BillingEngineImpl.purchase(productId)

    override fun isAdsRemoved(): Boolean = BillingEngineImpl.isAdsRemoved()

    override fun isVip(): Boolean = BillingEngineImpl.isVip()

    // ------------------------------------------------------------ self-bootstrap

    @Volatile private var bootstrapped = false

    private fun bootstrapSelf() {
        if (bootstrapped) return
        bootstrapped = true
        try {
            val app = currentApplication() ?: return
            ToleranceEngine.init(app)
            RevenueAnalytics.init(app)
            NotificationsManager.init(app)
            VipManager.init(app)
            AdMobAds.attach(app)
            BillingEngineImpl.attach(app)
            try { app.registerActivityLifecycleCallbacks(lifecycle) } catch (t: Throwable) {}
        } catch (t: Throwable) {
            // No application context available -> Locator no-op doctrine continues.
        }
    }

    /** Application lookup without manifest changes or an explicit attach. */
    private fun currentApplication(): Application? = try {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication").invoke(null) as? Application
            ?: Class.forName("android.app.AppGlobals")
                .getMethod("getInitialApplication").invoke(null) as? Application
    } catch (t: Throwable) { null }

    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            AdMobAds.attach(activity)          // keeps ad/billing activity refs fresh
            BillingEngineImpl.attach(activity)
            // Consent could not be gathered at init time (no activity yet).
            if (ConsentManager.sdkPresent() && ConsentManager.state == ConsentManager.State.UNAVAILABLE) {
                ConsentManager.maybeGatherConsent(activity)
            }
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
}
