# Building the Android app (no computer needed)

1. Push to GitHub. The **Build Android APK** workflow runs automatically (or run it by hand from the repo's *Actions* tab → *Build Android APK* → *Run workflow*).
2. When it finishes (~5 min), open the repo's **Releases** page on your phone, download `LifeMax-RPG.apk` from the *latest* release and tap it to install (allow "install unknown apps" for your browser when asked).
3. To use it as your home screen: Settings → Apps → Default apps → **Home app** → LifeMax RPG.

To ship a new version: put the new single-file build in `lifemax_modular_v73/dist/` named `Life_Max_RPG_vNN.html`; the highest version number is the one packaged.

`android-wrapper/` is a Capacitor shell; the Android project is generated fresh in CI, so nothing Android-specific is checked in.
