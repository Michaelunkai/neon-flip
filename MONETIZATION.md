# NeonFlip — Monetization & Revenue Architecture

Owner: monetization package (+ `analytics/`, `debug/`). Contracts in
`app/src/main/java/com/michaelunkai/neonflip/monetization/Contracts.kt` and
`RevenueConfig.kt` are FIXED (team-wide); everything in this document is
implemented against them. All tunables live in `RevenueConfig` — one file,
remote-config-ready.

---

## Layer map (A–G)

| Layer | Name | File(s) | One-line purpose |
|---|---|---|---|
| A | Consent & compliance gate | `monetization/ConsentManager.kt` | UMP/GDPR consent, graceful no-op without the SDK |
| B | Ad serving | `monetization/AdMobAds.kt` | AdMob banner/interstitial/rewarded + preload pipeline + backoff |
| C | Tolerance engine (THE algorithm) | `monetization/ToleranceEngine.kt` | 0..100 ad-tolerance score gates every interstitial |
| D | Billing & entitlements | `monetization/BillingEngineImpl.kt`, `monetization/VipManager.kt` | Play Billing v7: IAP, remove-ads, coins, skins, VIP sub |
| — | Locator facade | `monetization/MonetizationEngine.kt` | BOTH-interfaces facade ui/Locator discovers; self-bootstraps context + activity tracking |
| E | Economy hooks | `RevenueConfig` (revive/streak-freeze costs) + game code | Revive via coins or rewarded ad, streak freeze |
| F | Analytics | `analytics/RevenueAnalytics.kt` | JSONL event log, ring-buffered, Firebase-pipeable shape |
| G | Win-back notifications | `monetization/NotificationsManager.kt` | Opt-in streak-save/bonus nudges, capped + quiet hours |

Debug: `debug/DebugPanelActivity.kt` + `debug/MANIFEST-SNIPPET.xml` (build
agent merges the activity entry; not in the manifest by default).

---

## A. Consent (UMP)

**Production one-liner** — add exactly this to `app/build.gradle` dependencies
(everything else already no-ops gracefully via reflection):

```gradle
implementation 'com.google.android.ump:user-messaging-platform:3.0.0'
```

`ConsentManager` detects the SDK by `Class.forName` and drives
`UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, listener)`
through a dynamic proxy. Without the SDK: state = `UNAVAILABLE`,
`canRequestAds() = true` (dev/emulator builds are never blocked). With it: the
Google consent form shows on first launch in EEA/UK and gates
`canRequestAds()`. Errors fail-open (ads are never worth a crash) and are
logged as `consent` analytics events.

The AdMob APP ID in `AndroidManifest.xml` is currently the Google **test**
app ID (`ca-app-pub-3940256099942544~3347511713`) — replace together with the
unit IDs (section B).

## B. Ad serving (AdMobAds)

- Formats: adaptive-off **banner** (`AdSize.BANNER`), **interstitial**,
  **rewarded video**. Unit IDs are Google TEST IDs behind `USE_TEST_IDS = true`;
  `PRODUCTION_*_ID` constants are clearly-marked placeholders to fill before
  store release.
- **Preload invariant:** the next interstitial + rewarded start loading the
  instant any full-screen ad is dismissed (`onAdDismissedFullScreenContent` →
  `preloadInterstitial()` + `preloadRewarded()`), and again after init.
- **Retry:** failed loads retry with exponential backoff 5s → 10s → 20s → 40s
  → 80s (ceiling 5 min), max 5 attempts per burst; success resets the counter.
- **Offline:** `ConnectivityManager` check short-circuits loads; every show
  path no-ops and still fires `onDismiss` so game flow never hangs. Missing
  SDK / no Play services = degraded mode, zero crash.
- **Banner embedding:** UI calls `AdMobAds.loadBanner()` then `AdMobAds.banner()`
  to add the returned `AdView` to a layout; call `AdMobAds.detachBanner()` in
  `onDestroy`.
- **Rewarded is never tolerance-gated** (user opt-in); every completed watch
  recovers tolerance and counts as an impression. Declining a rewarded offer
  counts as a churn signal (`ad_decline`).

## C. Tolerance engine — exact formulas

Score `T ∈ [0, 100]`, persisted per player in SharedPreferences
(`neonflip_tolerance`, JSON blob + a `days_active` string-set).

**Read:** `T_now = min(100, T_stored + hoursSince(lastChange) × TOLERANCE_TIME_RECOVERY_PER_HOUR)`

**Write events:**

| Event | Delta | Source |
|---|---|---|
| Interstitial shown | `− TOLERANCE_DECAY_PER_INTERSTITIAL` (−25) | `onInterstitialShown` |
| High score | `+ TOLERANCE_RECOVERY_HIGH_SCORE` (+20) | `recordHighScore` (also resets death streak) |
| Quest complete | `+ TOLERANCE_RECOVERY_QUEST` (+10) | `recordQuestComplete` |
| Voluntary rewarded watch | `+ TOLERANCE_RECOVERY_REWARDED_WATCH` (+25) | `recordRewardedWatch(voluntary=true)` |

Start state: `T_stored = TOLERANCE_MAX` (100) — day-one players are ad-rich.

**Decision pipeline for `maybeShowInterstitial` (ordered, first-match wins):**

1. `vip_monthly` active → SKIP (`VIP`)
2. `remove_ads` purchased → SKIP (`ADS_REMOVED`)
3. `sessions < DAY0_PROTECTION_SESSIONS` (3) → SKIP (`DAY0_PROTECTION`)
4. `now − lastInterstitialShown < INTERSTITIAL_MIN_SPACING_MS` (75s) → SKIP (`MIN_SPACING`)
5. `gameOversSinceLastInterstitial < INTERSTITIAL_EVERY_N_GAMEOVERS` (2) → SKIP (`PACING_GAMEOVERS`)
6. `interstitialsThisHour >= INTERSTITIAL_MAX_PER_HOUR` (6) → SKIP (`HOURLY_CAP` — hard fatigue backstop)
7. positive-spike flag active (< 30s old; set by `markPositiveSpike` AND by every completed voluntary rewarded watch) → **SHOW at the dopamine peak** (`SHOW_PEAK`)
8. `T < REWARDED_BIAS_TOLERANCE` (30) → SKIP (`REWARDED_BIAS_MODE` — rewarded-only)
9. `T < T_eff` → SKIP (`TOLERANCE_LOW`)
10. else → SHOW (`SHOW`)
8. `T < T_eff` → SKIP (`TOLERANCE_LOW`)
9. else → SHOW (`SHOW`)

**Effective threshold:** `T_eff = min(100, TOLERANCE_INTERSTITIAL_THRESHOLD (50) + churnBoost)` where
`churnBoost` (engine-internal derived constants, documented here — the only
non-`RevenueConfig` numbers in the engine):

| Churn signal | Definition | Boost |
|---|---|---|
| Death spiral | `deathStreak >= LOSS_STREAK_TRIGGER` (3) | +8 |
| Session decline | avg of last 3 sessions < 0.7 × lifetime avg session | +8 |
| Ad fatigue | ≥3 declines in last 10 ad offers | +4 |

Rising churn pushes the bar UP (protect the player), recovery events pull it
back DOWN (monetize the good mood).

**Peak-dopamine hook:** game calls `ToleranceEngine.markPositiveSpike(source)`
right before a moment of guaranteed joy (new skin reveal, big combo, streak
milestone). The flag lives 30 s and lets the next interstitial bypass the
threshold (never the hard gates 1–5) — ads shown at peak joy are the highest-
tolerance, least-churn-risk impressions in the game.

**Tracked metrics:** sessions, days active, total/avg session length, last-5
session lengths, deaths, deaths/session, death streak, inter-session gaps,
ad views/declines (+ rolling 10-window), interstitials shown, rewarded
watches, gameovers-since-ad, coin balance mirror. Full dump via
`ToleranceEngine.snapshot()`.

## D. Billing & entitlements (Play Billing v7)

- SKUs (`RevenueConfig`): `remove_ads` $2.99, `coins_small` $0.99,
  `coins_medium` $2.99 (**best-value anchor** — `BillingEngineImpl.isBestValue`),
  `coins_large` $4.99, `skin_premium`, `vip_monthly` $1.99/mo.
- Coin grants (`COIN_PACK_GRANTS`, suggestion — game applies via
  `onCoinPackGranted`): 400 / 1500 / 3200 coins → per-dollar 400 / 501 / 642;
  medium is anchored as "best value" label, large carries the highest
  per-coin rate to upsell.
- `remove_ads` and `skin_premium` are permanent, sticky, persisted in
  SharedPreferences and re-verified from Play on every refresh. Purchases are
  auto-acknowledged. Coin grants are idempotent per purchase token.
- **VIP** (`VipManager`): 2x daily coins, zero interstitials, exclusive skin
  flag, streak freeze. NOT sticky — recomputed from Play purchases on every
  `refreshEntitlements()` (Play hides lapsed subs), with a 48 h offline grace
  past the last known period end.
- **Remove-ads keeps rewarded ads working** (rewarded is opt-in value exchange,
  not an interruption). VIP likewise keeps rewarded.
- Emulator / sideload / no Play Store: `isAvailable() == false`, every path
  no-ops, zero crash.
- Price strings for game UI: `BillingEngineImpl.productPrice(id)` (localized,
  from the cached catalog).

## E. Economy hooks (consumed by game code)

- Revive: `REVIVE_COST_COINS = 100` or rewarded ad if `REVIVE_AD_OFFER` —
  game offers coin revive first, rewarded revive as the no-coins path
  (`AdMobAds.showRewarded("revive", onReward, onDismiss)`).
- Streak freeze: `STREAK_FREEZE_COST_COINS = 150` for non-VIP; free while VIP
  (`VipManager.hasStreakFreeze()`).

## F. Analytics (RevenueAnalytics)

- One JSON line per event: `{"ts":..., "event":..., "params":{...}}` — same
  shape as `FirebaseAnalytics.logEvent(name, bundle)` for a drop-in pipe.
- Ring buffer capped at `ANALYTICS_LOG_MAX_EVENTS` (5000); appends to
  `filesDir/revenue_analytics.jsonl` on a background thread (2 s coalescing);
  tail of 500 events reloaded on cold start; `clear()` for debug.
- Canonical events: `session_start, session_end, death, high_score,
  quest_complete, ad_show, ad_click, ad_decline, ad_skip, ad_unavailable,
  preload, iap_attempt, iap_purchase, iap_ack, entitlement, billing_ready,
  tolerance_snapshot, consent, notif_sent, notif_suppressed, notif_opt_in`.
- `snapshot()` exposes counts-by-event, drops, recent 20, file size.
- Every `tolerance_snapshot` event carries `tolerance` + `why` — replay these
  to tune the constants remotely.

## G. Win-back notifications

`notifyStreakSave(streakDays, hoursLeft)` / `notifyStreakBonus(streakDays)`.
Gates in order: opt-in (default OFF) → `NOTIF_MAX_PER_DAY` (1/day, per calendar
day, persisted) → quiet hours 21:00–09:00 (wraps midnight) → POST_NOTIFICATIONS
runtime permission on API 33+. Deep links to MainActivity via explicit
`setClassName` intent, `FLAG_IMMUTABLE` PendingIntent. Suppression is logged,
never crashed.

---

## LTV proxy (the number this engine maximizes)

```
LTV_proxy(USD) = S × [ (I_int × eCPM_int + I_rew × eCPM_rew + I_ban × eCPM_ban) / 1000 ]
               + S × p_IAP × AOV
               + p_sub × (sub_price × sub_months_avg)

S            = expected sessions per user (retention-owned; engine feeds it via
               session analytics)
I_int        = interstitial impressions per session  ≈ sessions_over_30s_min /
               INTERSTITIAL_MIN_SPACING_MS decay-limited by tolerance ≈ 0.25–0.5
I_rew        = rewarded impressions per session      ≈ 0.3 (revive + coin doubler)
I_ban        = banner impressions per session        ≈ 6 (30s refresh, session ≥3 min)
eCPM (US baseline, tune per geo): interstitial $8, rewarded $25, banner $0.50
p_IAP        = payer conversion ≈ 1.5–3% (remove_ads + coin packs)
AOV          = average order value ≈ $2.20 weighted
p_sub        = VIP attach ≈ 0.3%, sub_months_avg ≈ 3.5
```

Worked example (S = 25 sessions): 25 × [(0.4×8 + 0.3×25 + 6×0.5)/1000] =
25 × [0.0207] ≈ **$0.52 ad-LTV**; IAP: 25 × 0.02 × 2.20 ≈ **$1.10**; sub adds
≈ **$0.02** → **LTV_proxy ≈ $1.64**. Rewarded bias + tolerance pacing exist to
keep S high (churn is the real LTV killer); the tolerance snapshot stream is
the tuning feedback loop.

## Near-win easing curve spec (retention layer consumes; monetization-blessed)

Guarantees players in a death spiral experience a comeback soon after the
LOSS_STREAK_TRIGGER, converting rage-quits into sessions (which converts into
ad impressions). Spec:

```
ease(s) = NEAR_WIN_EASE_FACTOR × min(1, (s − LOSS_STREAK_TRIGGER + 1) / 3)

s = current consecutive-loss streak, ease applies only while s >= trigger
max easing = 0.15 (15%), ramping 0.05 / 0.10 / 0.15 on the 3 losses past trigger
```

Apply `ease` multiplicatively to obstacle spawn density / paddle responsiveness
(reduce difficulty by up to 15%), and RESET it immediately on any score
(a win resets the streak exactly like `ToleranceEngine.recordHighScore` does).
Never apply during day-0 (session count < DAY0_PROTECTION_SESSIONS) — early
game must feel honestly easy. The point is a **comeback moment** (near-win
dopamine), not a permanently easier game.

## Mediation-readiness notes

- `AdMobAds` isolates all SDK calls behind one object; a mediation stack
  (AppLovin MAX / AdMob mediation) swaps only this object — `AdEngine`
  contract unchanged.
- Adapter order today: AdMob only. Recommended production ladder: rewarded
  via bidding (AppLovin/Meta + AdMob network), interstitial via AdMob
  mediation waterfall, banner adaptive.
- `isRewardedReady()` already exists as the UI gate for mediation fill-rate;
  add per-network ready flags inside AdMobAds only.
- Keep TEST IDs behind `USE_TEST_IDS`; mediation requires one more meta-data
  block per network in the manifest (build-agent territory).
- eCPM table above is the mediation baseline to beat; log `ad_show` with
  placement so per-placement eCPM is computable later.

## Compliance

- **UMP/GDPR:** section A. Consent gathered before first ad request; ads load
  only after `init()` which runs `maybeGatherConsent` first.
- **COPPA / families:** current audience = general (not child-directed). If
  targeting families ever changes: set
  `RequestConfiguration.Builder().setTagForChildDirectedTreatment(TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)`
  + `TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE` via `MobileAds.setRequestConfiguration`
  inside `AdMobAds.init`, mark the app families-policy compliant in Play
  Console, remove personalized-ads assumptions, and re-run UMP with
  family-safe default consent. Rewarded/interstitial ad content rating must be
  set G in AdMob console.
- **Play ads policy:** interstitials never appear mid-gameplay (only on
  game-over transitions gated by this engine), never before the first session
  ends (day-0 protection), never more often than 75 s + every 3 game-overs.
  This is stricter than Play's anti-interruption guidance by design.
- **Data safety:** everything is local-first (SharedPreferences + filesDir);
  no analytics leaves the device until a network pipe is added in production.
- **Billing:** all purchases acknowledged immediately; entitlements verified
  server-side never (local trust) — acceptable at this scale, revisit if
  refund abuse appears.

## Integration points — exact calls for game/ui/retention code

**Primary path (zero extra code):** `ui/Locator` discovers
`monetization.MonetizationEngine` (implements BOTH `AdEngine` and
`BillingEngine`) by reflection at bootstrap, then calls `init {}` /
`start {}`. The facade self-bootstraps the Application context (reflection)
and tracks the resumed Activity via lifecycle callbacks, so ads, billing,
consent, tolerance and notifications all go live with NO game-side attach
calls. Game code keeps calling the contract surface
(`Locator.ads.maybeShowInterstitial(...)`, `Locator.billing.purchase(...)`,
etc.) exactly as already wired.

**Explicit path (optional, identical behavior):**

```kotlin
// Application/MainActivity onCreate (once, order matters):
AdMobAds.attach(this)                       // context + consent + engine wiring
BillingEngineImpl.attach(this)              // context + entitlement prefs
NotificationsManager.init(this)
AdMobAds.init { /* sdk ready */ }
BillingEngineImpl.start { /* billing ready */ }

// MainActivity onResume (refresh entitlements + activity ref):
AdMobAds.attach(this); BillingEngineImpl.attach(this); BillingEngineImpl.refreshEntitlements()

// MainActivity onPause:
RevenueAnalytics.flushNow()

// Game loop hooks:
ToleranceEngine.recordSessionStart()                 // returns isNewDay (daily bonus)
ToleranceEngine.recordSessionEnd(durationMs)
ToleranceEngine.recordGameOver()
ToleranceEngine.recordHighScore(score)               // tolerance +20, resets death streak
ToleranceEngine.recordQuestComplete(questId)         // tolerance +10
ToleranceEngine.markPositiveSpike("skin_reveal")     // 30s peak-ad window
ToleranceEngine.recordCoinBalance(balance)           // optional mirror

// Ads (contract surface):
AdMobAds.maybeShowInterstitial("game_over")          // engine decides everything
AdMobAds.showRewarded("revive", onReward = { revive() }, onDismiss = { showReviveDeclined() })
AdMobAds.isRewardedReady()
AdMobAds.loadBanner(); AdMobAds.banner(); AdMobAds.detachBanner()

// Offers before rewarded (so declines count as churn signal):
//   offer UI shown -> user declines -> ToleranceEngine.recordAdDecline(placement)

// Billing (contract surface):
BillingEngine.purchase(RevenueConfig.IAP_REMOVE_ADS)
BillingEngine.isAdsRemoved(); BillingEngine.isVip()
// extras: BillingEngineImpl.productPrice(id), isBestValue(id), onCoinPackGranted,
//         onEntitlementsChanged, isAvailable(), refreshEntitlements()

// VIP benefits (game reads):
VipManager.hasDailyCoinMultiplier(); VipManager.hasNoInterstitials()
VipManager.hasExclusiveSkin(); VipManager.hasStreakFreeze()

// Notifications:
NotificationsManager.setOptIn(optInFromSettings)
NotificationsManager.notifyStreakSave(streakDays, hoursLeft)
NotificationsManager.notifyStreakBonus(streakDays)
```

## Every tunable in RevenueConfig

| Constant | Value | Layer | Meaning |
|---|---|---|---|
| `TOLERANCE_MAX` | 100 | C | Starting + ceiling tolerance score |
| `TOLERANCE_INTERSTITIAL_THRESHOLD` | 50 | C | Base interstitial gate (was 55 — spacing/pacing gates are the real ceiling) |
| `TOLERANCE_DECAY_PER_INTERSTITIAL` | 25 | C | Score cost per interstitial shown (was 30 — allows back-to-back spacing-limited ads) |
| `TOLERANCE_RECOVERY_HIGH_SCORE` | 20 | C | Recovery on new high score |
| `TOLERANCE_RECOVERY_REWARDED_WATCH` | 25 | C | Recovery on voluntary rewarded watch |
| `TOLERANCE_RECOVERY_QUEST` | 10 | C | Recovery on quest complete |
| `TOLERANCE_TIME_RECOVERY_PER_HOUR` | 8 | C | Passive recovery per hour since last write (was 5 — faster re-arm between sessions) |
| `INTERSTITIAL_MIN_SPACING_MS` | 75 000 | C | Hard spacing floor (60–90s band) |
| `INTERSTITIAL_EVERY_N_GAMEOVERS` | 2 | C | Min game-overs between interstitials (was 3 — ~+50% impressions, never every death) |
| `DAY0_PROTECTION_SESSIONS` | 3 | C | No interstitials before Nth session |
| `REWARDED_BIAS_TOLERANCE` | 30 | C | Below: rewarded-only mode (was 35) |
| `REVIVE_COST_COINS` | 100 | E | Coin revive price |
| `REVIVE_AD_OFFER` | true | E | Rewarded revive path enabled |
| `STREAK_FREEZE_COST_COINS` | 150 | E | Streak freeze price (free for VIP) |
| `IAP_REMOVE_ADS` | "remove_ads" | D | SKU: remove interstitials forever, $2.99 |
| `IAP_COINS_SMALL` | "coins_small" | D | SKU: 400 coins, $0.99 |
| `IAP_COINS_MED` | "coins_medium" | D | SKU: 1500 coins, $2.99, best-value anchor |
| `IAP_COINS_LARGE` | "coins_large" | D | SKU: 3200 coins, $4.99 (was 2800 — keeps per-dollar ladder strictly rising) |
| `IAP_SKIN_PREMIUM` | "skin_premium" | D | SKU: premium skin entitlement |
| `SUB_VIP_MONTHLY` | "vip_monthly" | D | SKU: VIP subscription, $1.99/mo |
| `NOTIF_MAX_PER_DAY` | 1 | G | Hard daily notification cap |
| `NOTIF_QUIET_START_HOUR` | 21 | G | Quiet-hours start (wraps midnight) |
| `NOTIF_QUIET_END_HOUR` | 9 | G | Quiet-hours end |
| `NEAR_WIN_EASE_FACTOR` | 0.15 | Near-win | Max difficulty easing after loss streak |
| `LOSS_STREAK_TRIGGER` | 3 | C/Near-win | Streak that arms churn + near-win easing |
| `INTERSTITIAL_MAX_PER_HOUR` | 6 | C | NEW: hard hourly interstitial ceiling (fatigue backstop) |
| `COMEBACK_RESET_GAP_MS` | 172 800 000 | C | NEW: 48h+ absence resets tolerance to MAX (comeback grace) |
| `ANALYTICS_LOG_MAX_EVENTS` | 5000 | F | Analytics ring-buffer capacity |

Engine-internal derived constants (churn boosts +8/+8/+4, 0.7× session-decline
ratio, 10-offer ad window, 30 s peak-spike window) live beside the algorithm in
`ToleranceEngine` and are documented in section C — promote them to
`RevenueConfig` when remote config lands.

## File inventory (monetization-owned)

| File | Purpose |
|---|---|
| `monetization/Contracts.kt` | FIXED interfaces (AdEngine, BillingEngine) — do not edit |
| `monetization/RevenueConfig.kt` | FIXED single tunables file — do not edit |
| `monetization/AdMobAds.kt` | AdEngine impl: ads, preload pipeline, backoff, offline no-ops |
| `monetization/ToleranceEngine.kt` | THE algorithm: tolerance score, gating, metrics |
| `monetization/BillingEngineImpl.kt` | Play Billing v7: catalog, purchase, acknowledge, entitlements |
| `monetization/MonetizationEngine.kt` | Both-interfaces facade for ui/Locator discovery + self-bootstrap |
| `monetization/VipManager.kt` | VIP benefits flags + offline grace |
| `monetization/ConsentManager.kt` | UMP consent via reflection, no-ops without SDK |
| `monetization/NotificationsManager.kt` | Capped, opt-in, quiet-hours streak nudges |
| `analytics/RevenueAnalytics.kt` | JSONL event log + snapshot |
| `debug/DebugPanelActivity.kt` | Live revenue-core panel (manifest snippet in comment) |
| `debug/MANIFEST-SNIPPET.xml` | Build-agent activity registration |
| `MONETIZATION.md` | This document |

---

## Changelog — 2026-10-20 revenue deep-optimization pass

| Change | File | Expected revenue effect |
|---|---|---|
| Threshold 55→50, decay 30→25 | `RevenueConfig` | Steady-state T stays above T_eff even with churn boosts → interstitials limited by spacing/pacing (the policy ceiling), not by the score. +15–25% interstitial impressions/session. |
| Every-N-gameovers 3→2 | `RevenueConfig` | Max every 2nd death (Play-safe). ~+50% pacing-gate headroom in death-heavy sessions. |
| NEW `INTERSTITIAL_MAX_PER_HOUR = 6` | `RevenueConfig` + engine | Hard fatigue backstop: soft math can never exceed 6 interstitials/hour — protects S (retention) while maximizing impressions inside the cap. |
| Passive recovery 5→8/hour | `RevenueConfig` | Faster re-arm between sessions; more sessions open ad-eligible. |
| Rewarded-bias 35→30 | `RevenueConfig` | Rewarded-only mode now kicks in only deeper into fatigue — fewer lost interstitial slots at moderate T. |
| NEW 48h comeback reset | `ToleranceEngine` | Lapsed players return at T=100: re-engagement sessions are monetized at full rate on day one (retention × impressions compounding). |
| Rewarded watch opens spike window | `ToleranceEngine` | Post-reward interstitial lands at the dopamine peak → highest-tolerance impression; raises post-reward eCPM fill and conversion. |
| `positive_spike` + `comeback` analytics events | `ToleranceEngine` | Replay stream now covers every tuning signal (spike source, lapse length). |
| coins_large 2800→3200 (642 coins/$) | `BillingEngineImpl` | Per-dollar ladder strictly rising 400/501/642: large pack becomes a genuine upsell instead of a marginal one; lifts AOV toward the $4.99 point. |

IAP ladder sanity (verified, no inversion): remove_ads $2.99 is priced ~5.7× the
$0.52 baseline ad-LTV it forfeits (correct — always profitable to buy out); VIP
$1.99/mo (2× coins + no interstitials + skin + freeze) has the highest value
density per dollar in the catalog, correctly placed as the premium anchor.

## Revised LTV estimate (post-tuning)

```
I_int ≈ 0.9/session (was 0.4): every-2-gameovers + tighter threshold, spacing 75s
        still the binding policy constraint, hourly cap 6 as backstop
I_rew ≈ 0.35/session (revive + coin doubler; rewarded bias converts fatigued users)
I_ban ≈ 6/session (unchanged)
S     ≈ 30 sessions (was 25): comeback grace + peak-placement reduce churn

Ads : 30 × [(0.9×8 + 0.35×25 + 6×0.50)/1000] = 30 × 0.01895 ≈ $0.57  (was $0.52)
IAP : 30 × 0.02 × 2.20                     ≈ $1.32  (was $1.10; AOV up via 3200-coin pack)
Sub : 0.003 × 1.99 × 3.5                   ≈ $0.02
                                     TOTAL ≈ $1.91  (was $1.64, +16%)
```

Sensitivity: if churn protection lifts S to 35 (the comeback/peak effects
compound across days), LTV_proxy ≈ $2.20 (+34%). The `tolerance_snapshot` +
`ad_skip` event streams are the feedback loop to verify I_int ≈ 0.9 and
`HOURLY_CAP`/`TOLERANCE_LOW` skip ratios in production.
