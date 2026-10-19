package com.michaelunkai.neonflip.monetization
/** Interfaces fixed by team contract: game code (game/ui/retention) may CALL these,
 *  only the monetization package may IMPLEMENT them. */
interface AdEngine {
    fun init(onReady: () -> Unit)
    fun showRewarded(placement: String, onReward: () -> Unit, onDismiss: () -> Unit)
    fun maybeShowInterstitial(placement: String)          // engine decides via tolerance
    fun loadBanner(): Unit?
    fun isRewardedReady(): Boolean
}
interface BillingEngine {
    fun start(onReady: () -> Unit)
    fun purchase(productId: String)
    fun isAdsRemoved(): Boolean
    fun isVip(): Boolean
}
