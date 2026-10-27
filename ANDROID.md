# Building the Android app (no computer needed)

1. Push to GitHub. The **Build Android APK** workflow runs automatically (or run it by hand from the repo's *Actions* tab → *Build Android APK* → *Run workflow*).
2. When it finishes (~2 min), open the repo's **Releases** page on your phone, download `LifeMax-RPG.apk` from the *latest* release and tap it to install (allow "install unknown apps" for your browser when asked).
3. To use it as your home screen: Settings → Apps → Default apps → **Home app** → LifeMax.

## The permanent signing key (one-time setup)

Android only installs an update over an app signed with the **same key**. The workflow signs with a key stored in a GitHub secret named `ANDROID_KEYSTORE_BASE64`:

1. GitHub → this repo → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**.
2. Name: `ANDROID_KEYSTORE_BASE64`. Value: the long text of the keystore (kept outside the repo). Save.
3. Re-run the workflow. The release's `version.json` then says `"stable": true`.

Keep a private copy of that text: if it is lost, a new key is needed and the app must be uninstalled and reinstalled once (export a backup first; the first screen has *Import a backup*).

## Updating

After the key is in place, the app updates itself: **Settings → App updates → Check now → Update** (it also checks about once a day). Android shows its normal install confirmation. The first time, it asks you to allow *Install unknown apps* for LifeMax.

To ship a new version: put the new single-file build in `lifemax_modular_v73/dist/` named `Life_Max_RPG_vNN.html`; the highest version number is the one packaged. The build number is the workflow run number.

## Layout

`android-wrapper/` is a Capacitor shell. The Android project is generated fresh in CI; `native/` (Java plugins and MainActivity), `res/` (pixel-art icon and splash) and the `patch_*.py` scripts are applied on top.


## Over-the-air web updates (v133)
Most changes touch only the web app (screens, logic, voice, journal, cat art). Those no longer need an APK install.
- **Each build publishes three files** on the `latest` release: `LifeMax-RPG.apk`, `app.html` (the web app on its own) and `version.json`, which carries `nativeApi` and a `web` block (`build`, `url`, `sha256`, `size`, `minNativeApi`).
- **The phone decides.** After the launch check (and from Settings > App updates): if the release's `nativeApi` is higher than the installed APK's, an APK is needed and the usual pop-up appears. Otherwise, if its web build is newer than the one running, the app downloads `app.html` quietly, checks the SHA-256, stores it in the app's own files (`files/live/<build>/index.html`), and shows a small "Update ready · Restart" pill. It also starts on the next launch by itself.
- **Safety.** The copy inside the APK is always the fallback. A downloaded build is on trial until it reports healthy (the last segment, `51-boot-ok.js`, sets `window.__lmaBooted` and calls `LiveWeb.healthy()` six seconds after start); after two starts without that it is deleted and never fetched again. A newer APK than the stored build removes the stored build. Downloads are https only and must match their fingerprint.
- **The one rule.** `android-wrapper/native/NativeApi.java` holds `VALUE`. **Bump it by one in any change where the web app starts relying on new native code** (a new plugin or plugin method, a new permission, a changed plugin). Web-only changes never bump it. If you forget, a phone with the old APK gets a web build that calls a plugin it does not have.
- **Keep `51-boot-ok.js` the last segment** (new segments go before it).
- The first APK containing this (build 55 or later) must be installed the normal way; after that, web-only changes arrive on their own.
