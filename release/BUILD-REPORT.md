# Neon Flip — Build & Deploy Report (2026-10-20)

## MENU OVERLAP FIX (FINAL BUILD) — 2026-10-20 ~01:30
Defect (leader screenshot review): STREAK chip + DAILY panel drew over the
"Neon Flip" title; quest bullet lines rendered over the tagline.
Fix in `ui/Screens.kt` (uw/u convention preserved):
- `drawQuestLines()` now draws its own bordered translucent card
  (RectF 4..96 uw, y 30.5u..~49u) via `NeonUi.drawPanel`, quest lines inside at
  35.8u + i*4.4u, indented to 7uw — can never collide with the title block.
- Title moved down to y=22.5u (size 7.6u), tagline to 27.6u.
- DAILY/gift button moved out of title space to the right column
  (66..96 uw, y 11..16.5u), mirroring the STREAK chip on the left.
Build green (2m44s). Verified on emulator-5554 with a fresh menu screenshot
(notification shade cleared via UI first — the earlier banner came from the
unrelated "Daymark Companion" emulator app): zero text overlap confirmed by
read-back — BEST/coins row, STREAK | DAILY columns, title, tagline, quest card,
PLAY, SHOP/RANKS/SETTINGS, season footer all in their own bands.
- `neonflip-release.apk` — 2,967,200 B — sha256 `dedd55ea90bf4be599e74fb3e075c3a22385bfa0f84af4ab005df3ab24578824`
- `neonflip-release.aab` — 3,972,983 B — sha256 `2b7f1386d2f638d1d7b1e4524d235c6c9c9572c1c68ce956c058bf70cab33258`
- Fresh screenshot: `release/screenshot-menu.png`
- logcat FATAL/AndroidRuntime grep for neonflip → EMPTY after relaunch.

## POST-POLISH BUILD (FINAL) — rebuilt 2026-10-20 ~01:14
Rebuilt after the gameplay + monetization polish agents landed changes
(GameBalance HITBOX_FORGIVE=0.88, GameEngine hitbox+revive, Renderer TAP TO FLIP
first-run hint, MainActivity resume slow-mo ramp, RevenueConfig/ToleranceEngine
pacing constants incl. INTERSTITIAL_MAX_PER_HOUR=6, BillingEngineImpl coins_large
2800→3200). Compiled clean on the FIRST pass — zero fixes needed in the new code.
- `neonflip-release.apk` — 2,967,236 bytes — sha256 `bb5b22dd03ccdf28f358a2f156ca7d384d3089d4069fe9a0c70a10f2bbbccadd`
- `neonflip-release.aab` — 3,972,983 bytes — sha256 `886578bc86b1a9799270473bc327632759e55dd392bd294a9da31a8ab8f12b6a`
- Same keystore/cert (CN=NeonFlip); apksigner verify PASS.
- Emulator re-verified: fresh-data install (`pm clear` on our own test app to see
  first-run state) → **TAP TO FLIP hint confirmed rendering** on run 1 (screenshot
  evidence captured mid-run); ~2 min live play to NEW BEST 369, results panel
  correct, near-miss pipeline verified in code (GameEngine.kt:362-376: detection,
  NEAR_MISS_BONUS score, floater, sfx, haptics; counter rendered on results);
  logcat FATAL/AndroidRuntime grep for neonflip → EMPTY across the whole session.
- Menu screenshot read-back: fully fitted (a notification from the unrelated
  "Daymark Companion" emulator app briefly overlays — not our app).

Everything below documents the first (pre-polish) build; kept for the record.

## Device note (IMPORTANT)
The physical S25 Ultra (`R5CY610XJGV`, TLS serial `adb-R5CY610XJGV-vIqgVe._adb-tls-connect._tcp`,
wm size 1080x2340, wm density 450, renderFrameRate 60, cutout top 72px) is **QUARANTINED by user
order**: no further installs/launches on it until the user re-approves. An earlier build of v1.0.0
(code 1) with the button-layout bug remains installed on the phone and was left untouched.
All verification below is from the headless emulator `emulator-5554` (AVD `medium_phone`,
API 34 google_apis x86_64, 1080x2400).

## Artifacts (all in `release/`)
- `neonflip-release.apk` — 2,966,648 bytes — sha256 `2709caef6b8187430bb5a0245e4350ccfa5a08149c5a8477c08fb05391fc69da`
- `neonflip-release.aab` — 3,972,403 bytes — sha256 `0f7415fab0d06239689d7c4a02dfb2e10b02b75f212b4f78b658e20b66ff4115`
- Signed with `keystore/neonflip.keystore` (cert CN=NeonFlip, O=MichaelUnkai; SHA-256
  `32f9dfa0f5e0b555a02af07c8d3909455052a6fa78e1e97a24125b28c7a8a7e8`).
- `apksigner verify --print-certs` PASS on final APK. Keystore + `keystore/secrets.properties`
  git-ignored (verified in `.gitignore`); passwords never printed.
- versionCode 1 / versionName 1.0.0 / minSdk 24 / targetSdk 34 / com.michaelunkai.neonflip.

## Toolchain (pinned, contained)
JDK 17 (`AndroidStudio\jdk\jdk-17.0.20.1+1`), SDK 34, Gradle 8.9, AGP 8.5.2, Kotlin 1.9.24.
Build: `gradle assembleRelease bundleRelease` — BUILD SUCCESSFUL (52s after fixes; no dependency upgrades).

## Compile fixes made (loop 1)
1. `debug/DebugPanelActivity.kt` — unterminated string `snap["day0_protected']` broke `refresh()` scope (cascade of 10 errors).
2. `game/GameEngine.kt` — `Gate(x0, onFloor)` missing required `passed` param → added `false` (2 sites).
3. `monetization/AdMobAds.kt` + `monetization/BillingEngineImpl.kt` — `return` inside expression-body try → block bodies.
4. `MainActivity.kt` — `WindowInsetsControllerCompat` imported from `android.view` → `androidx.core.view`.

## Resolution fix (S25 Ultra "whacked resolution" — root cause found and fixed)
`ui/Screens.kt` `btn()` laid out button X coordinates with `u = h/100` instead of `uw = w/100`,
stretching every canvas button ~2.17x horizontally on 1080-wide displays (PLAY/shop/results
buttons ran off-screen; game canvas itself was fine). Fix: `rect.set(l*uw, t*u, r*uw, b*u)`,
`uw` parameter added and threaded through all 27 call sites (existing compensating call sites
become correct under the new semantics). Also added
`res/values-v28/themes.xml` with `android:windowLayoutInDisplayCutoutMode=shortEdges`
(values themes kept API-24-safe). `WindowCompat.setDecorFitsSystemWindows(false)` was already set.

## Install & verification (emulator-5554)
- Install: `adb -s emulator-5554 install -r release/neonflip-release.apk` → `Success`.
- Launch: `am start -W -n com.michaelunkai.neonflip/.MainActivity` → Status ok, COLD, 4502 ms (first), ~350 ms warm.
- Screenshot read-back: `release/screenshot-menu.png` — menu fully fitted: PLAY centered,
  SHOP/RANKS/SETTINGS row within bounds, coins chip, DAILY reward, quests, season footer all visible.
- Gameplay: tap-to-flip responsive (score advanced 52→97→217→402→259→94 across runs over two
  ~2-min sessions; death → results → RESTART loop stable; mystery bonus +112 dropped; revive states correct).
- Ads (test IDs): rewarded test ad played to "Reward granted" end card; interstitial test ad shown
  and closed; no crash. AppId `ca-app-pub-3940256099942544~3347511713`.
- Offline: wifi+data disabled, force-stop, cold relaunch → app to menu, focus on MainActivity,
  zero fatals. Network re-enabled after.
- Logcat verdict: `adb logcat -d | grep -iE "FATAL|AndroidRuntime" | grep -i neonflip` → EMPTY
  across the full session.

## Manifest change
Registered `.debug.DebugPanelActivity` (`android:exported="false"`) in `app/src/main/AndroidManifest.xml`.

## Phone install command (recorded for later, pending quarantine lift)
`adb -s adb-R5CY610XJGV-vIqgVe._adb-tls-connect._tcp install -r release\neonflip-release.apk`
(Do NOT uninstall to bypass INSTALL_FAILED_UPDATE_INCOMPATIBLE; report instead.)

## Files changed this session
- app/src/main/java/com/michaelunkai/neonflip/ui/Screens.kt — button layout uw fix
- app/src/main/res/values-v28/themes.xml — NEW, cutout shortEdges
- app/src/main/AndroidManifest.xml — DebugPanelActivity registered
- app/src/main/java/com/michaelunkai/neonflip/MainActivity.kt — insets import fix
- app/src/main/java/com/michaelunkai/neonflip/debug/DebugPanelActivity.kt — string/scope fix
- app/src/main/java/com/michaelunkai/neonflip/game/GameEngine.kt — Gate ctor fix
- app/src/main/java/com/michaelunkai/neonflip/monetization/{AdMobAds,BillingEngineImpl}.kt — expression-body fixes
