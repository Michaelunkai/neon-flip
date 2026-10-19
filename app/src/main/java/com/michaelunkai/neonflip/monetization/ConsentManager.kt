package com.michaelunkai.neonflip.monetization

import android.app.Activity
import com.michaelunkai.neonflip.analytics.RevenueAnalytics

/**
 * ConsentManager — GDPR/UMP consent gateway.
 *
 * PRODUCTION NOTE: the `google user-messaging-platform` dependency is NOT in
 * gradle yet. This manager detects the UMP SDK via reflection and NO-OPS
 * gracefully (returns OBTAINED / canRequestAds=true) when it is absent, so the
 * game never crashes on emulator/sideload builds. For production add exactly
 * one gradle line (documented in MONETIZATION.md section A):
 *
 *     implementation 'com.google.android.ump:user-messaging-platform:3.0.0'
 *
 * With UMP present, this class drives
 * UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, listener)
 * through a dynamic proxy so this file stays compile-clean without the dep.
 */
object ConsentManager {

    enum class State { NOT_REQUIRED, OBTAINED, ERROR, UNAVAILABLE }

    @Volatile var state: State = State.NOT_REQUIRED
        private set

    /** Resolved lazily; null when the UMP SDK is not on the classpath. */
    private val umpClass: Class<*>? by lazy {
        try { Class.forName("com.google.android.ump.UserMessagingPlatform") } catch (t: Throwable) { null }
    }

    fun sdkPresent(): Boolean = umpClass != null

    /** True when ads may be requested under the current consent state. */
    fun canRequestAds(context: android.content.Context): Boolean {
        if (umpClass == null) return true // no UMP -> test/dev build, do not block
        return try {
            val ciClass = Class.forName("com.google.android.ump.ConsentInformation")
            val get = umpClass!!.getMethod("getConsentInformation", android.content.Context::class.java)
            val ci = get.invoke(null, context)
            ciClass.getMethod("canRequestAds").invoke(ci) as Boolean
        } catch (t: Throwable) {
            true // fail-open: an ad request never justifies a crash
        }
    }

    /**
     * Shows the Google consent form if required for this region/device.
     * No-op (UNAVAILABLE) when UMP is absent. Never throws, never blocks > form UI.
     */
    fun maybeGatherConsent(activity: Activity) {
        if (umpClass == null) {
            state = State.UNAVAILABLE
            return
        }
        try {
            val listenerClass = Class.forName("com.google.android.ump.UserMessagingPlatform\$OnConsentGatheredListener")
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                listenerClass.classLoader, arrayOf(listenerClass)
            ) { _, method, args ->
                if (method.name == "onConsentGathered") {
                    val error = args?.getOrNull(0)
                    state = if (error == null) State.OBTAINED else State.ERROR
                    RevenueAnalytics.log("consent", mapOf("state" to state.name))
                }
                null
            }
            umpClass!!
                .getMethod("loadAndShowConsentFormIfRequired", Activity::class.java, listenerClass)
                .invoke(null, activity, proxy)
        } catch (t: Throwable) {
            state = State.ERROR
            RevenueAnalytics.log("consent", mapOf("state" to State.ERROR.name, "error" to (t.message ?: "unknown")))
        }
    }
}
