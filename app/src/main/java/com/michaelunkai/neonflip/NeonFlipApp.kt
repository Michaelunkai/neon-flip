package com.michaelunkai.neonflip

import android.app.Application
import com.michaelunkai.neonflip.analytics.RevenueAnalytics
import com.michaelunkai.neonflip.game.AudioSynth
import com.michaelunkai.neonflip.retention.SaveStore
import com.michaelunkai.neonflip.ui.Locator

/**
 * Application entry point. Cold start stays <2s: only cheap synchronous work here,
 * everything heavy (audio prerender, ad SDK warmup) goes to background threads.
 */
class NeonFlipApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Persistence first (everything reads it).
        SaveStore.init(this)

        // 2. Wire monetization implementations if present, else no-ops (see Locator KDoc).
        Locator.bootstrap(this)

        // 3. Ads warmup is async and failure-tolerant; game is fully playable without it.
        Locator.initAds()

        // 4. Billing warmup (async, graceful "unavailable" if not ready).
        Locator.initBilling()

        // 5. Procedural audio prerender on a background thread.
        AudioSynth.init()

        // 6. Analytics: cold-start marker.
        RevenueAnalytics.log("app_open", mapOf("ts" to System.currentTimeMillis()))
    }
}
