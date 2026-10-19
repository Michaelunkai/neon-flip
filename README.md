<div align="center">

# 🌌 NEON FLIP

**One touch. Flipped gravity. Pure neon.**

*The one-touch gravity-flip arcade runner for Android.*

[![Platform](https://img.shields.io/badge/platform-Android_7.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-24%20to%2034-00f0ff?style=for-the-badge)](#install)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#architecture)
[![License](https://img.shields.io/badge/license-MIT-ff2bd6?style=for-the-badge)](#-license)
[![Price](https://img.shields.io/badge/price-FREE-success?style=for-the-badge)](#-monetization)
[![Ads](https://img.shields.io/badge/ads-yes%2C%20optional%20rewards-orange?style=for-the-badge)](#-monetization)

[![Download](https://img.shields.io/badge/⬇_DOWNLOAD-Get_the_APK-00f0ff?style=for-the-badge&labelColor=101024)](https://github.com/Michaelunkai/neon-flip/releases)
[![Play Store](https://img.shields.io/badge/Google_Play-Coming_soon-ff2bd6?style=for-the-badge&labelColor=101024)](https://github.com/Michaelunkai/neon-flip/releases)

</div>

---

You never steer. You never stop. **One tap flips gravity** and the entire world turns with you — floors become ceilings, ceilings become floors, and only your timing stands between you and a wall of neon. Chase one more meter, one more coin, one more personal best.

> 📸 *Screenshots — see the real thing:*
>
> | ![Gameplay screenshot](store-listing/assets/phone-screenshot-1.png) | ![Gameplay screenshot](store-listing/assets/phone-screenshot-2.png) | ![Gameplay screenshot](store-listing/assets/phone-screenshot-3.png) |
> |:---:|:---:|:---:|
>
> 🎬 *Gameplay GIF placeholder:* `store-listing/assets/gameplay.gif` — *(drop the captured GIF here)*
>
> 🖼️ *Feature graphic:* `store-listing/assets/feature-graphic.png`

---

## ✨ Features

**Gameplay**
- ⚡ **One-touch controls** — flip gravity with a single tap; learnable in 5 seconds, hard to master
- ♾️ **Endless procedural tracks** — no two runs are ever the same
- 🌀 **Smooth, fair gravity-flip physics** — tuned for flow, not frustration
- 🪙 **Coins & unlockable ball skins** — collect while you run
- 🏆 **Local high scores** — your best run is always one attempt away
- 📵 **Fully playable offline** — no internet needed for the core game
- 🪶 **Tiny download** — smooth even on older phones (Android 7.0+)

**Monetization — full transparency**

| Thing | How it works | Pay to win? |
|---|:---|:---:|
| Banner ads | Menus only, never during gameplay | — |
| Interstitial ads | Between runs, never mid-run | — |
| Rewarded video | **Always optional** → bonus coins or a second chance | No |
| One-time "Remove ads" purchase | Kills all ads forever | No |
| VIP subscription (optional) | Supports development → golden trail + VIP badge | No |
| Gameplay content | **100% free** — every track, every skin path, no locks | — |

Nothing is behind a paywall. No loot boxes. No energy timers. No tracking you around the internet. Details: [`MONETIZATION.md`](MONETIZATION.md) · Privacy: [policy page](https://michaelunkai.github.io/neon-flip/privacy/) ([source](docs/privacy/index.html))

---

## 📲 Install

### Option A — Direct APK (fastest)

1. Go to **[Releases](https://github.com/Michaelunkai/neon-flip/releases)**
2. Download `neonflip-v1.0.0.apk` from the latest release
3. Open it (Android will ask to allow installs from your browser/files app once)
4. Play. That's it.

> Google Play version is coming soon — see [`store-listing/PUBLISH-RUNBOOK.md`](store-listing/PUBLISH-RUNBOOK.md).

### Option B — Build from source

Requirements (the contained toolchain — no global installs needed):

| Tool | Version |
|---|---|
| JDK | 17+ |
| Android SDK | 34 (build-tools/platform) |
| Gradle | 8.x via the wrapper |

```bash
git clone https://github.com/Michaelunkai/neon-flip.git
cd neon-flip
./gradlew :app:assembleDebug        # debug APK → app/build/outputs/apk/debug/
./gradlew :app:bundleRelease        # release AAB (needs keystore/secrets — see below)
```

Release signing reads `keystore/neonflip.keystore` + `keystore/secrets.properties` (`STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) — private, never committed. Without them the release build still assembles, unsigned. Debug builds are always signed automatically.

---

## 🏗 Architecture

Single-module Kotlin app (`com.michaelunkai.neonflip`), built to stay small and fast:

```
app/src/main/java/.../neonflip/
├── NeonFlipApp.kt        # Application: AdMob SDK init
├── MainActivity.kt       # Single portrait activity, owns the game loop
├── engine/               # Game loop, gravity-flip physics, procedural track generation
├── ui/                   # Menus, HUD, neon rendering
├── ads/                  # AdMob wrapper (banner / interstitial / optional rewarded)
└── billing/              # Play Billing: remove-ads IAP + optional VIP subscription
```

- **Min SDK 24 / Target SDK 34** · Kotlin (JVM 17) · R8 minify + resource shrinking for release
- **Local-first**: scores, settings and progress live in on-device storage; v1.0.0 ships **zero analytics upload** — telemetry never leaves the device
- Third-party surface: Google AdMob (`play-services-ads` 23.2.0) and Google Play Billing (`billing-ktx` 7.0.0) — nothing else

---

## 💸 Monetization

Neon Flip is free and fully playable forever. Ads (banner / interstitial / **opt-in** rewarded) keep the lights on; one optional purchase removes ads permanently; an optional clearly-labeled VIP subscription is for supporters (cosmetics only). Full design, prices and fairness rules: **[`MONETIZATION.md`](MONETIZATION.md)**.

---

## 🛠 Troubleshooting

| Problem | Fix |
|---|---|
| APK won't install ("unknown apps") | Settings → Apps → your browser/files app → allow *Install unknown apps*, then retry |
| "App not installed" | Uninstall any older copy first (signature mismatch), then install |
| Game stutters on first run | It's compiling shaders — the second run is smooth |
| No ads loading (offline / new install) | Ads need internet; the game itself never does |
| Bought "Remove ads", ads still show | Menu → *Restore purchases*; if it persists, email below |
| Progress lost after reinstall | Progress is device-local (by design, for privacy); a cloud-sync option is being explored |
| Still stuck | [Open an issue](https://github.com/Michaelunkai/neon-flip/issues) or email michaelunkai@gmail.com |

---

## 🔒 Privacy

No account. No name. No email. No location. Scores stay on your device; the only data that ever leaves it is the advertising ID used by Google AdMob to show ads (resettable in Android settings). Full plain-language policy: **[Privacy Policy](https://michaelunkai.github.io/neon-flip/privacy/)** · [`docs/privacy/index.html`](docs/privacy/index.html) · Not directed at children under 13.

## 🗺 Roadmap

- [ ] Google Play Store release (in progress — see [`store-listing/PUBLISH-RUNBOOK.md`](store-listing/PUBLISH-RUNBOOK.md))
- [ ] More skins & daily challenge mode
- [ ] Optional cloud high-score sync (opt-in only)

## 📄 License

Released under the [MIT License](LICENSE) — play with it, mod it, ship it. Just keep the license notice.

---

<div align="center">
<sub>⭐ Star the repo if one tap ruined your study schedule. · Made with 💜 and questionable reflexes by <b>Michael Unkai</b> · michaelunkai@gmail.com</sub>
</div>
