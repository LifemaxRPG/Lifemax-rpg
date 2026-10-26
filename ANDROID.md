# Building the Android app (no computer needed)

1. Push to GitHub. The **Build Android APK** workflow runs automatically (or run it by hand from the repo's *Actions* tab → *Build Android APK* → *Run workflow*).
2. When it finishes (~2 min), open the repo's **Releases** page on your phone, download `LifeMax-RPG.apk` from the *latest* release and tap it to install (allow "install unknown apps" for your browser when asked).
3. To use it as your home screen: Settings → Apps → Default apps → **Home app** → LifeMax RPG.

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
