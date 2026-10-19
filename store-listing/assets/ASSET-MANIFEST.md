# NeonFlip — Store Asset Manifest

Art direction: dark `#0B0E1A` base, neon cyan `#00E5FF`, hot pink `#FF2E97`, additive
glow, gradient depth, vignettes. All assets generated programmatically (Python/PIL,
generators in `_gen/` — re-run any script to regenerate its assets byte-identically
in composition; `verify.py` re-checks every dimension).

## APK launcher icon resources (`app/src/main/res/`)

| File | Dimensions | Play Console / build field |
|---|---|---|
| `mipmap-anydpi-v26/ic_launcher.xml` | adaptive-icon XML (bg `@mipmap/ic_launcher_background` + fg `@mipmap/ic_launcher_foreground`) | APK resource — `android:icon` |
| `mipmap-anydpi-v26/ic_launcher_round.xml` | adaptive-icon XML (same layers) | APK resource — round icon (API 26+) |
| `mipmap-mdpi/ic_launcher.png` | 48x48 | APK resource — legacy launcher icon |
| `mipmap-hdpi/ic_launcher.png` | 72x72 | APK resource — legacy launcher icon |
| `mipmap-xhdpi/ic_launcher.png` | 96x96 | APK resource — legacy launcher icon |
| `mipmap-xxhdpi/ic_launcher.png` | 144x144 | APK resource — legacy launcher icon |
| `mipmap-xxxhdpi/ic_launcher.png` | 192x192 | APK resource — legacy launcher icon |
| `mipmap-mdpi/ic_launcher_foreground.png` | 108x108 | APK resource — adaptive foreground (motif inside 66dp safe zone) |
| `mipmap-hdpi/ic_launcher_foreground.png` | 162x162 | APK resource — adaptive foreground |
| `mipmap-xhdpi/ic_launcher_foreground.png` | 216x216 | APK resource — adaptive foreground |
| `mipmap-xxhdpi/ic_launcher_foreground.png` | 324x324 | APK resource — adaptive foreground |
| `mipmap-xxxhdpi/ic_launcher_foreground.png` | 432x432 | APK resource — adaptive foreground |
| `mipmap-mdpi/ic_launcher_background.png` | 108x108 | APK resource — adaptive background (dark radial) |
| `mipmap-hdpi/ic_launcher_background.png` | 162x162 | APK resource — adaptive background |
| `mipmap-xhdpi/ic_launcher_background.png` | 216x216 | APK resource — adaptive background |
| `mipmap-xxhdpi/ic_launcher_background.png` | 324x324 | APK resource — adaptive background |
| `mipmap-xxxhdpi/ic_launcher_background.png` | 432x432 | APK resource — adaptive background |

Icon design: glowing cyan ball mid-flip on a dotted trail between a cyan and a pink
neon line, speed ticks, sparkles, dark radial background with cyan/pink corner wash.

## Play Console uploads (`store-listing/assets/`)

| File | Dimensions | Play Console field |
|---|---|---|
| `icon-512.png` | 512x512 | App icon (store listing, 512x512 PNG, 32-bit) |
| `feature-graphic.png` | 1024x500 | Feature graphic (1024x500 PNG) — "NEON FLIP" stacked neon title + flip motif + grid floor |
| `screenshot-1-gameplay.png` | 1080x2340 | Phone screenshot 1 (lead) — corridor gameplay: ball, spikes, coins, score + combo HUD, wave bar |
| `screenshot-2-results.png` | 1080x2340 | Phone screenshot 2 — run-over panel: score/best, 5s revive countdown, REVIVE / END RUN |
| `screenshot-3-skins.png` | 1080x2340 | Phone screenshot 3 — skins shop grid (6 skins, prices, equipped state) + coin balance |
| `screenshot-4-quests.png` | 1080x2340 | Phone screenshot 4 — daily streak card + 3 quest cards with progress + CLAIM button |
| `screenshot-5-leaderboard.png` | 1080x2340 | Phone screenshot 5 — weekly leaderboard: top-3 podium, ranks 4-7, highlighted YOU row |
| `screenshot-6-menu.png` | 1080x2340 | Phone screenshot 6 — main menu: NEON/FLIP logo, pulsing PLAY, SHOP/QUESTS/RANKS |
| `share-card.png` | 1080x1350 | Not uploaded — in-game score-share card template (score `12 847` placeholder, brand lockup, "PLAY FREE ON GOOGLE PLAY" CTA); render real score over the `_gen/gen_share.py` layout at runtime |

## Notes

- Screenshot order leads with gameplay (conversion best practice); all are full-bleed
  1080x2340 (9:19.5) — accepted by Play (min 320px, max 3840px, 16:9 or 9:16 ratio
  recommended but taller phone shots are accepted for phone screenshots).
- No watermarks, no placeholder sludge; all text is large and readable.
- `manifest`/Kotlin untouched; this slice only added `mipmap-*` resources and
  `store-listing/assets/`.
