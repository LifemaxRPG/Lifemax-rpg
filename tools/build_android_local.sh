#!/usr/bin/env bash
# Build and test the Android app the way CI does, on this machine.
#   tools/build_android_local.sh            # compile + unit tests (assembleDebug testDebugUnitTest)
#   tools/build_android_local.sh compile    # Kotlin compile only (fastest feedback)
# Needs ANDROID_HOME (SDK with platform 34 and build-tools 34.0.0), Java 21, node and python3.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
: "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK folder}"
mode="${1:-full}"
wrapper="$root/android-wrapper"

python3 "$root/lifemax_modular_v73/build.py" >/dev/null
mkdir -p "$wrapper/www"
python3 "$root/lifemax_modular_v73/forge_features.py" "$root/lifemax_modular_v73/dist/Life_Max_RPG_v73_modular_build.html" "$wrapper/www/index.html"

cd "$wrapper"
[ -d node_modules ] || npm install --no-audit --no-fund
if [ ! -d android ]; then
  npx cap add android
  python3 patch_manifest.py android/app/src/main/AndroidManifest.xml
  cp native/*.java android/app/src/main/java/com/lifemax/rpg/
  python3 patch_project.py android
  mkdir -p android/app/src/androidTest/java/com/lifemax/rpg
  rm -rf android/app/src/androidTest/java/com/getcapacitor
  cp android-test/*.kt android/app/src/androidTest/java/com/lifemax/rpg/
  cp -r res/. android/app/src/main/res/
  find android/app/src/main/res -name 'splash.png' -exec cp res/drawable/splash.png {} \;
  python3 patch_gradle.py android/app/build.gradle 1 0
  python3 patch_styles.py android/app/src/main/res/values/styles.xml
else
  # keep the Java plugins in step with the sources; Kotlin is read straight from native/src
  cp native/*.java android/app/src/main/java/com/lifemax/rpg/
fi
npx cap sync android >/dev/null
echo "sdk.dir=$ANDROID_HOME" > android/local.properties
cd android
if [ "$mode" = compile ]; then ./gradlew :app:compileDebugKotlin --console=plain; else ./gradlew assembleDebug testDebugUnitTest --console=plain; fi
