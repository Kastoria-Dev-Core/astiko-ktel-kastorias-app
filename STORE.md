# Publishing on Google Play

What's ready in this repo, and what's left to do in the Play Console.

## Ready

* **The App Bundle:** `release.yml` builds `astika-kastorias-<version>.aab`, signed with the release key, next to the APK in every release. That's what Google Play takes
* **Target SDK:** 36 (Android 16), as Google Play asks of new apps and updates
* **Store listing,** in Greek (`el-GR`) and English (`en-US`), in `fastlane/metadata/android/<locale>/`:
  * `title.txt`, `short_description.txt`, `full_description.txt`
  * `images/icon.png` (512 x 512), `images/featureGraphic.png` (1024 x 500)
  * `images/phoneScreenshots/` (1080 x 1920, the app's screenshots with a caption). Made by `scripts/store_assets.py` from the Screenshots workflow's full-size images
* **Privacy policy:** https://kastoria-dev-core.github.io/privacy/ (and `/en/privacy/`)
* **Support:** https://kastoria-dev-core.github.io/support/

The folder layout is fastlane's, so `fastlane supply` can upload the listing and the bundle from CI once there's a Play Console account and a service account key.

## Left to do

1. **The account.** A Google Play developer account (25 USD, once). An organisation account, e.g. Astiko KTEL Kastorias's, needs a D-U-N-S number. A personal account must run a closed test with at least 12 testers for 14 days before the app can go public
2. **The operator's say-so.** The app is named after the operator and says it's their official app, so Google wants it published from the operator's account or with their written authorisation
3. **App signing.** Turn on Play App Signing and upload the first bundle. The key in this repo's secrets becomes the upload key; keep a copy of it somewhere safe
4. **The questionnaires** in the Play Console, with the answers below

## Answers for the Play Console

**Data safety**

* Does the app collect or share user data? **No.**
  * Location is used only on the device, when the user asks for stops near them, and is never sent off it. Google's definition of "collected" covers data sent off the device, so it isn't collected
  * No accounts, no analytics, no ads, no crash reporting
* Is data encrypted in transit? **Yes**, everything the app downloads is over HTTPS
* Can users ask for their data to be deleted? Nothing is collected; what's on the phone goes when the app is uninstalled

**Content rating (IARC):** a utility app with no violence, sexual content, language, controlled substances, gambling, user interaction or sharing of location with others. It comes out as "Everyone" / PEGI 3.

**Target audience:** everyone; not designed for children specifically.

**Ads:** no ads.

**App access:** everything works without signing in.

**Category:** Maps & Navigation (or Travel & Local).

**Permissions:** location (approximate and precise, foreground only) to find stops near the user; internet and network state for the timetables, live buses and announcements. No background location.
