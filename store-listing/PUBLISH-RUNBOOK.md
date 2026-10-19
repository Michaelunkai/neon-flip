# PUBLISH RUNBOOK — Neon Flip on Google Play

> Owner click-by-click guide: from zero Google Play account to a live production listing.
> App: **Neon Flip** · `com.michaelunkai.neonflip` · v1.0.0 (versionCode 1)
> Every form field below is mapped to the exact file in this repo. Estimated total hands-on time: ~90 minutes + waiting periods.

---

## Part 0 — What you need before starting (5 min)

| You need | Where it is |
|---|---|
| Google account (use michaelunkai@gmail.com for consistency) | gmail.com |
| $25 USD payment method (credit/debit card) | — |
| Government ID for identity verification | wallet/passport |
| Signed release AAB | `release/neonflip-release.aab` (built by the release agent; verify with `ls release/`) |
| Store listing texts | `store-listing/listing-en.md` (+ ES/PT-BR/ID/TR/RU variants) |
| Store graphics | `store-listing/assets/` (feature-graphic.png, icons, phone screenshots) |
| Privacy policy (must be a live URL before you can publish) | `docs/privacy/index.html` → publish via GitHub Pages first (Part 2, step 10) |

**CRITICAL PRE-FLIGHT:** `app/src/main/AndroidManifest.xml` currently contains the **AdMob TEST app ID** (`ca-app-pub-3940256099942544~3347511713`). Before the production AAB is built you must replace it with your **real** AdMob App ID from Part 1, step 4, then rebuild. Publishing with the test ID violates no policy but earns zero revenue.

---

## Part 1 — Google Play Console account (~30 min + 1–2 days waiting)

1. Go to **https://play.google.com/console** → sign in → click **"Get started"** / **"Create account"**.
2. Choose account type: **Personal** (cheaper, faster to start; note: personal accounts created after Nov 13 2023 must pass closed testing before production — see Part 4). Organization only if you have a registered business (D-U-N-S number required).
3. **Developer name**: enter `Michael Unkai` (shown publicly on your listing). Agree to the Developer Distribution Agreement.
4. **Pay the one-time $25 registration fee** with your card.
5. **Identity verification** (required before you can publish anything):
   - Console → **Settings → Developer account → Identity verification** → **Start verification**.
   - Upload a government-issued photo ID (passport, driver's license, national ID). Use clear, uncropped photos.
   - Personal accounts must also verify **address** and (for paid apps/in-app products) **D&B or payment profile**. Neon Flip has IAP + subscription, so Google may ask for extra payment-profile verification — follow the prompts.
   - Wait for the "Verified" email — typically minutes to 48 hours.
6. While waiting: enable **2-Step Verification** on the Google account (mandatory for new developer accounts since 2023) if you haven't.

---

## Part 2 — Repo prep before the Console work (~15 min)

1. **Push the repo to GitHub** as `Michaelunkai/neon-flip` (private is fine for now; public before the GitHub-Release path in Part 6).
2. **Publish the privacy policy:**
   - GitHub repo → **Settings → Pages** → Source: `Deploy from a branch` → Branch: `main`, folder `/docs` → Save.
   - Wait ~1–2 min. The policy goes live at:
     `https://michaelunkai.github.io/neon-flip/privacy/`
   - Open it once and confirm it renders (dark neon page, last updated 2026-10-19).
   - Copy this URL — Console will demand it.
3. **AdMob setup** (do this now so the manifest fix is one edit):
   - https://apps.admob.com → **Apps → Add app → Android → "Not listed"** → name it `Neon Flip`.
   - Copy the **App ID** (`ca-app-pub-XXXXXXXXXXXX~YYYYYYYYYY`).
   - Create 3 **Ad units**: `Banner` (menus), `Interstitial` (between runs), `Rewarded` (bonus coins). Copy their unit IDs for the dev team.
   - Fill AdMob **Privacy & messaging** → create a **GDPR/UMP consent message** (2 min wizard) so EU users get a consent form.
4. Ask the build agent to set the real AdMob App ID in the manifest and rebuild → confirm `release/neonflip-release.aab` exists and is fresh.

---

## Part 3 — Create the app + store listing (~30 min)

In Play Console: **All apps → Create app**.

| Console field | Enter / source |
|---|---|
| App name | `Neon Flip: Gravity Runner` ← `store-listing/listing-en.md` (title) |
| Default language | `en-US` (English, United States) |
| App or game | **Game** |
| Free or paid | **Free** |
| App availability | check both boxes (it's a normal public app) |
| Declaration: content guidelines / US export laws | check both after reading |

**App dashboard** (`com.michaelunkai.neonflip` shows under it). Now fill the left menu **"Grow → Store presence → Main store listing"**:

| Console field | Source file | Limits |
|---|---|---|
| App name | `listing-en.md` → App title (25/30 chars) | ≤30 |
| Short description | `listing-en.md` → Short description (72/80 chars) | ≤80 |
| Full description | `listing-en.md` → Full description (~1900/4000 chars) | ≤4000 |
| App icon (512×512 PNG) | `store-listing/assets/app-icon-512.png` | 512×512, ≤1 MB |
| Feature graphic (1024×500) | `store-listing/assets/feature-graphic.png` | exactly 1024×500 |
| Phone screenshots (min 2, ideally 4–8) | `store-listing/assets/phone-screenshot-1..8.png` | 16:9 or 9:16, JPG/PNG, ≥320 px, ≤3840 px |
| 7" tablet screenshots (optional now) | `store-listing/assets/tablet-screenshot-*.png` | — |

**Store settings** (left menu, still under Store presence):
- **App category**: Games → **Arcade**. Tags: `arcade`, `runner`, `one tap` (pick from Google's list closest to `listing-en.md` → Tags).
- **Contact details**: Email `michaelunkai@gmail.com` (mandatory). Website: the GitHub Pages URL. Phone: optional, skip.
- **Store listing visibility**: leave default.

**Localize** the listing: Main store listing → **Manage translations** → add `es` (or es-419), `pt-BR`, `id`, `tr`, `ru` → paste title/short/full from `listing-es.md`, `listing-pt-br.md`, `listing-id.md`, `listing-tr.md`, `listing-ru.md`. (Defaults fall back to EN for anything unfilled.)

---

## Part 4 — Policy forms (left menu "Policy" section) (~15 min)

Fill these **before** uploading the AAB — the production gate re-checks them.

### 4a. App content → every item:

1. **Privacy policy** → paste `https://michaelunkai.github.io/neon-flip/privacy/`.
2. **Ads** → "Yes, my app contains ads" ← banner + interstitial + rewarded (declared in `listing-en.md` → Ads declaration).
3. **In-app purchases** → "Yes, Google Play In-app Billing" (one-time "remove ads") and **subscriptions: Yes** (VIP).
4. **Content ratings → Start questionnaire**. Answer per `listing-en.md` → Content rating table (essentially: no violence, no sex, no language, no gambling, no UGC). Result: **Everyone (PEGI 3)** → Submit for confirmation.
5. **Target audience** → Age groups: **13–15, 16–17, 18+** only. **Do NOT tick under-13 groups** (COPPA — the app is not child-directed; see `listing-en.md` → Target audience).
6. **Data safety** → use the table in `listing-en.md` → Data Safety Q&A (or click "Import from AdMob" then verify it matches):
   - Advertising ID: **Collected: Yes · Shared: Yes** → purpose Advertising/Analytics, optional.
   - App interactions/game data: **Collected: No** (local-only, never leaves device).
   - All data encrypted in transit: **Yes**. Deletion mechanism: **Yes** (email).
7. **Government apps / Financial features / Health** → all "No".
8. **News app declaration** → No. **COVID-19** → No. **Data safety form (CCPA/state)** → follows from step 6.

### 4b. Release dashboard declaration (appears with first release)
- **App access**: "All functionality is available without special access" (no login).
- **Ads declaration inside the release form**: Yes, contains ads.
- **Pre-launch report** will run automatically after the first upload — read it, screenshot issues are warnings only.

---

## Part 5 — Upload AAB + closed testing → production (~20 min + 14 days)

### 5a. Closed testing (mandatory for new personal developer accounts)

Google requires personal accounts created after Nov 13 2023 to run a **closed test with at least 12 testers opted-in for 14 consecutive days** before production access is granted.

1. Console → **Testing → Closed testing → Manage track → Create new track** (or use the default "Closed testing" track) → name it `v1.0.0 pre-launch`.
2. **Testers tab** → Create email list `neon-flip-testers`:
   - **Strategy — 12+ reliable humans:** 4–6 close friends/family with Android phones; 2–3 members of your maker/indie communities (ask in Discord/Telegram groups); 2–3 colleagues. **Do not** buy "testers" services — Google checks engagement (opt-in must be real, devices should actually open the app).
   - Every tester needs a **Google account email** (Gmail or Workspace — not iCloud/Outlook-only).
   - Add emails → **Save** → copy the **opt-in link** Console shows (`https://play.google.com/apps/testing/com.michaelunkai.neonflip`) and send it to all testers with the instruction: open on their Android phone → tap **Become a tester** → install from the Play Store link that appears.
3. **Releases tab → Create new release**:
   - Upload `release/neonflip-release.aab`.
   - Release name: `1.0.0 (1)`. Release notes: paste `listing-en.md` → Release notes v1.0.0 (246/500 chars).
   - **Next → Save → Review release → Start rollout to Closed testing.**
4. During the 14 days: message testers weekly; aim for each tester opening the app a few times (real usage looks better in review). Watch **Testing → Reports → Vitals** for crashes — fix any before production.

### 5b. Production

1. After the 14-day requirement is satisfied (Console shows production access unlocked), go to **Production → Create new release**.
2. Upload the **same** `release/neonflip-release.aab` (bump nothing; v1.0.0 versionCode 1 is fine).
3. Release notes: same v1.0.0 text from `listing-en.md`.
4. Countries: **Release → Countries/regions → Add** — start with all available (ad revenue + listings are already localized), or restrict if you prefer staged rollout.
5. **Review release → Start rollout to Production.** New-account first releases go through enhanced human review: expect **1–7 days**. Status shows "In review" → then "In production".
6. After it's live: store listing appears within a few hours at
   `https://play.google.com/store/apps/details?id=com.michaelunkai.neonflip`

---

## Part 6 — Alternative immediate path: GitHub Release APK (no waiting)

Play review + closed testing take weeks. To let anyone play **today**:

1. Make the GitHub repo public: repo → Settings → General → Danger Zone → Change visibility → Public.
2. Create the release: repo → **Releases → Draft a new release**:
   - Tag: `v1.0.0` · Title: `Neon Flip v1.0.0 — first release`
   - Description: paste the release notes from `listing-en.md` + a "Play Store coming soon" line.
   - Attach the built **signed APK** (`app-release.apk` — the APK twin of the AAB; ask the build agent if it isn't in `release/`) **and** the AAB if you like.
   - Publish release.
3. The README (project root) already links `https://github.com/Michaelunkai/neon-flip/releases` as the direct-download path.
4. **Caveats to state to users** (README already does): sideloaded APK = "install unknown apps" permission needed; no auto-updates; the Play Store release remains the primary distribution.

---

## Part 7 — Post-launch checklist

- [ ] App live on Play → verify listing renders (EN + all 5 translations), graphics not cropped.
- [ ] AdMob → check live ad requests/traffic (first impressions may take hours).
- [ ] Play Console → Policy → Data safety still matches shipped reality.
- [ ] Set up **Play Console notifications** (email) for policy violations/reviews.
- [ ] Week 1: respond to early reviews, watch ANR/crash vitals (<1% crash users target).
- [ ] Next version: bump `versionCode`/`versionName`, add release notes to `store-listing/listing-en.md`, repeat Part 5b (production review is fast after the first approval).
