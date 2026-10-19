package com.michaelunkai.neonflip.monetization

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.michaelunkai.neonflip.analytics.RevenueAnalytics
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * BillingEngineImpl — BillingEngine implementation over Play Billing v7
 * (billing-ktx:7.0.0). SKUs come from [RevenueConfig]:
 *
 *   remove_ads  $2.99 (one-time)   -> interstitials off forever, rewarded KEPT
 *   coins_small $0.99 (one-time)   -> 400 coins
 *   coins_medium $2.99 (one-time)  -> 1500 coins  [BEST-VALUE ANCHOR — see isBestValue]
 *   coins_large $4.99 (one-time)   -> 2800 coins
 *   skin_premium (one-time)        -> premium skin entitlement flag
 *   vip_monthly $1.99/mo (sub)     -> VipManager benefits
 *
 * Graceful degradation: on emulators / sideloads / no Play Store the client
 * fails to connect and EVERYTHING no-ops with [isAvailable] == false and zero
 * crashes. Entitlements persist in SharedPreferences and are re-verified from
 * Play on every [start] / [refreshEntitlements].
 *
 * GAME INTEGRATION (exact calls):
 *      BillingEngineImpl.attach(this)          // onCreate
 *      BillingEngineImpl.start { /* ready */ } // once
 *      BillingEngineImpl.attach(this); BillingEngineImpl.refreshEntitlements() // onResume
 *      BillingEngine.purchase("remove_ads")    // any contract call
 *      BillingEngineImpl.onCoinPackGranted = { productId -> grantCoins(productId) }
 */
object BillingEngineImpl : BillingEngine {

    /** Coin grants per pack (documented in MONETIZATION.md; game applies them). */
    val COIN_PACK_GRANTS: Map<String, Long> = mapOf(
        RevenueConfig.IAP_COINS_SMALL to 400L,
        RevenueConfig.IAP_COINS_MED to 1500L,   // best-value anchor price point
        RevenueConfig.IAP_COINS_LARGE to 3200L   // was 2800: 642 coins/$ keeps the per-dollar ladder strictly rising (400/501/642)
    )

    /** Fired (main thread, best-effort) after a coin pack purchase is verified. */
    var onCoinPackGranted: ((productId: String) -> Unit)? = null

    /** Fired after any entitlement change (UI should re-render prices/flags). */
    var onEntitlementsChanged: (() -> Unit)? = null

    private const val PREFS_FILE = "neonflip_billing"
    private const val KEY_ADS_REMOVED = "ads_removed"
    private const val KEY_PREMIUM_SKIN = "premium_skin"
    private const val KEY_GRANTED_TOKENS = "granted_tokens"

    private var appContext: Context? = null
    private var activityRef: WeakReference<Activity>? = null
    private var client: BillingClient? = null

    @Volatile private var connected = false
    private val starting = AtomicBoolean(false)
    private var reconnectAttempts = 0

    private var productCache = mutableMapOf<String, ProductDetails>()
    private var prefs: android.content.SharedPreferences? = null
    private var adsRemoved = false
    private var premiumSkin = false

    // ---------------------------------------------------------------- init / connect

    /**
     * Attach a context: Application for engine wiring, Activity for billing
     * flows (game should re-attach the current Activity on every onResume —
     * or let MonetizationEngine's lifecycle tracker do it).
     */
    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        (context as? Activity)?.let { activityRef = WeakReference(it) }
        VipManager.init(context)
        initPrefs(context)
    }

    private fun initPrefs(context: Context) {
        if (prefs != null) return
        try {
            prefs = context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            adsRemoved = prefs!!.getBoolean(KEY_ADS_REMOVED, false)
            premiumSkin = prefs!!.getBoolean(KEY_PREMIUM_SKIN, false)
        } catch (t: Throwable) { prefs = null }
    }

    override fun start(onReady: () -> Unit) {
        val ctx = appContext
        if (ctx == null || client != null || !starting.compareAndSet(false, true)) {
            runReady(onReady); return
        }
        try {
            client = BillingClient.newBuilder(ctx)
                .setListener(PurchasesUpdatedListener { result, purchases ->
                    handlePurchases(result, purchases)
                })
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
                )
                .build()
            client!!.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    connected = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                    if (connected) {
                        reconnectAttempts = 0
                        queryProducts()
                        refreshEntitlements()
                        RevenueAnalytics.log("billing_ready", mapOf("available" to true))
                    } else {
                        connected = false
                        RevenueAnalytics.log(
                            "billing_ready",
                            mapOf("available" to false, "code" to billingResult.responseCode)
                        )
                    }
                    runReady(onReady)
                }
                override fun onBillingServiceDisconnected() {
                    connected = false
                    scheduleReconnect()
                }
            })
        } catch (t: Throwable) {
            // No Play Store (emulator / sideload) or missing service: degraded mode.
            connected = false
            client = null
            starting.set(false)
            RevenueAnalytics.log("billing_ready", mapOf("available" to false, "error" to (t.message ?: "unknown")))
            runReady(onReady)
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= 3) return
        reconnectAttempts++
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try { client?.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connected = result.responseCode == BillingClient.BillingResponseCode.OK
                    if (connected) { queryProducts(); refreshEntitlements() }
                }
                override fun onBillingServiceDisconnected() { connected = false; scheduleReconnect() }
            }) } catch (t: Throwable) {}
        }, 30_000L * reconnectAttempts)
    }

    /** Contract: true only when a live Play Billing connection exists. */
    fun isAvailable(): Boolean = connected && client?.isReady == true

    private fun runReady(onReady: () -> Unit) {
        try { android.os.Handler(android.os.Looper.getMainLooper()).post {
            try { onReady() } catch (t: Throwable) {}
        } } catch (t: Throwable) { try { onReady() } catch (ignored: Throwable) {} }
    }

    // ---------------------------------------------------------------- contract surface

    override fun isAdsRemoved(): Boolean = adsRemoved

    override fun isVip(): Boolean = VipManager.isVip()

    /** Launch the purchase flow for a SKU. No-ops (logged) when unavailable. */
    override fun purchase(productId: String) {
        val activity = activityRef?.get()
        if (!isAvailable() || activity == null) {
            RevenueAnalytics.log("iap_attempt", mapOf("product" to productId, "result" to "unavailable"))
            return
        }
        val details = productCache[productId]
        if (details == null) {
            RevenueAnalytics.log("iap_attempt", mapOf("product" to productId, "result" to "not_cached"))
            queryProducts() // refresh for next attempt
            return
        }
        try {
            val productType = if (productId == RevenueConfig.SUB_VIP_MONTHLY) BillingClient.ProductType.SUBS
                              else BillingClient.ProductType.INAPP
            val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
            if (productType == BillingClient.ProductType.SUBS) {
                details.subscriptionOfferDetails?.firstOrNull()?.offerToken?.let { paramsBuilder.setOfferToken(it) }
            }
            val result = client!!.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(paramsBuilder.build()))
                    .build()
            )
            RevenueAnalytics.log("iap_attempt", mapOf(
                "product" to productId, "result" to "launched", "code" to result.responseCode))
        } catch (t: Throwable) {
            RevenueAnalytics.log("iap_attempt", mapOf("product" to productId, "result" to "error"))
        }
    }

    // ---------------------------------------------------------------- catalog + prices

    private fun queryProducts() {
        val c = client ?: return
        try {
            val inappIds = listOf(
                RevenueConfig.IAP_REMOVE_ADS, RevenueConfig.IAP_COINS_SMALL,
                RevenueConfig.IAP_COINS_MED, RevenueConfig.IAP_COINS_LARGE,
                RevenueConfig.IAP_SKIN_PREMIUM
            )
            val subsIds = listOf(RevenueConfig.SUB_VIP_MONTHLY)
            c.queryProductDetailsAsync(
                QueryProductDetailsParams.newBuilder().setProductList(
                    inappIds.map { QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it).setProductType(BillingClient.ProductType.INAPP).build() } +
                    subsIds.map { QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it).setProductType(BillingClient.ProductType.SUBS).build() }
                ).build()
            ) { result, detailsList ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    detailsList.forEach { productCache[it.productId] = it }
                }
            }
        } catch (t: Throwable) { /* catalog stays empty; purchase() re-queries */ }
    }

    /** Localized price string for game UI (null when unknown/unavailable). */
    fun productPrice(productId: String): String? {
        return try {
            val d = productCache[productId] ?: return null
            d.oneTimePurchaseOfferDetails?.formattedPrice
                ?: d.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
        } catch (t: Throwable) { null }
    }

    /** BEST-VALUE ANCHOR flag (RevenueConfig comments designate coins_medium). */
    fun isBestValue(productId: String): Boolean = productId == RevenueConfig.IAP_COINS_MED

    // ---------------------------------------------------------------- entitlements

    /** Re-reads purchases from Play and rebuilds all entitlement flags.
     *  Game calls this on every onResume. */
    fun refreshEntitlements() {
        val c = client ?: return
        if (!c.isReady) return
        try {
            c.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            ) { result, purchases -> if (result.responseCode == BillingClient.BillingResponseCode.OK) applyPurchases(purchases) }
            c.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
            ) { result, purchases -> if (result.responseCode == BillingClient.BillingResponseCode.OK) applyPurchases(purchases) }
        } catch (t: Throwable) { /* keep persisted flags */ }
    }

    private fun handlePurchases(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            applyPurchases(purchases)
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            RevenueAnalytics.log("iap_attempt", mapOf("result" to "user_canceled"))
        } else {
            RevenueAnalytics.log("iap_attempt", mapOf("result" to "failed", "code" to result.responseCode))
        }
    }

    private fun applyPurchases(purchases: List<Purchase>) {
        var newAdsRemoved = false
        var newPremiumSkin = false
        var newVip = false
        for (p in purchases) {
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            val products = p.products
            if (products.contains(RevenueConfig.IAP_REMOVE_ADS)) newAdsRemoved = true
            if (products.contains(RevenueConfig.IAP_SKIN_PREMIUM)) newPremiumSkin = true
            if (products.contains(RevenueConfig.SUB_VIP_MONTHLY)) newVip = true
            COIN_PACK_GRANTS.keys.forEach { pack ->
                if (products.contains(pack)) grantCoinPack(pack, p)
            }
            acknowledgeIfNeeded(p)
        }
        // Remove-ads and skin are permanent one-times: sticky-OR across refreshes.
        if (newAdsRemoved && !adsRemoved) {
            adsRemoved = true
            persistFlag(KEY_ADS_REMOVED, true)
            RevenueAnalytics.log("entitlement", mapOf("entitlement" to "ads_removed", "active" to true))
        }
        if (newPremiumSkin && !premiumSkin) {
            premiumSkin = true
            persistFlag(KEY_PREMIUM_SKIN, true)
            RevenueAnalytics.log("entitlement", mapOf("entitlement" to "skin_premium", "active" to true))
        }
        // VIP is NOT sticky: subscription state is recomputed from Play every refresh.
        VipManager.setActive(newVip)
        if (newVip) RevenueAnalytics.log("entitlement", mapOf("entitlement" to "vip_monthly", "active" to true))
        try { onEntitlementsChanged?.invoke() } catch (t: Throwable) {}
    }

    /** Idempotent coin grant: each purchase token grants each pack at most once. */
    private fun grantCoinPack(pack: String, purchase: Purchase) {
        try {
            val token = purchase.purchaseToken + ":" + pack
            val granted = (prefs?.getStringSet(KEY_GRANTED_TOKENS, emptySet()) ?: emptySet())
            if (granted.contains(token)) return
            prefs?.edit()?.putStringSet(KEY_GRANTED_TOKENS, granted + token)?.apply()
            RevenueAnalytics.log("iap_purchase", mapOf(
                "product" to pack, "price_usd" to priceOf(pack), "token" to purchase.purchaseToken.hashCode()))
            try { onCoinPackGranted?.invoke(pack) } catch (t: Throwable) {}
        } catch (t: Throwable) {}
    }

    private fun acknowledgeIfNeeded(p: Purchase) {
        try {
            if (p.isAcknowledged) return
            client?.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
            ) { result ->
                RevenueAnalytics.log("iap_ack", mapOf("code" to result.responseCode))
            }
        } catch (t: Throwable) {}
    }

    private fun priceOf(productId: String): String = when (productId) {
        RevenueConfig.IAP_COINS_SMALL -> "0.99"
        RevenueConfig.IAP_COINS_MED -> "2.99"
        RevenueConfig.IAP_COINS_LARGE -> "4.99"
        RevenueConfig.SUB_VIP_MONTHLY -> "1.99"
        RevenueConfig.IAP_REMOVE_ADS -> "2.99"
        else -> "0"
    }

    private fun persistFlag(key: String, value: Boolean) {
        try { prefs?.edit()?.putBoolean(key, value)?.apply() } catch (t: Throwable) {}
    }

    // ---------------------------------------------------------------- debug / telemetry

    fun debugStatus(): Map<String, Any?> = mapOf(
        "available" to isAvailable(),
        "ads_removed" to adsRemoved,
        "vip" to isVip(),
        "premium_skin" to premiumSkin,
        "cached_products" to productCache.keys.toList(),
        "prices" to productCache.keys.associateWith { productPrice(it) ?: "?" }
    )

    /** Debug-panel only: force a fresh connection attempt. */
    fun resetForDebug() {
        try { client?.endConnection() } catch (t: Throwable) {}
        client = null
        connected = false
        starting.set(false)
    }
}
