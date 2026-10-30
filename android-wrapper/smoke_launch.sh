#!/usr/bin/env bash
# Launch check before a release, run on the emulator: install the APK, open it, and fail if Android records a crash
# or the app is no longer running once its start-up has settled. Page errors are printed for the log but do not fail it.
set -euo pipefail
apk=android-wrapper/android/app/build/outputs/apk/debug/app-debug.apk
app=com.lifemax.rpg

adb install -r "$apk"
adb logcat -c
adb shell am start -W -n "$app/.MainActivity"
sleep 30 # WebView start, the page's own start-up, the native Overview opening and the first-run thumbnails

echo "--- page and runtime errors ---"
adb logcat -d -s Capacitor/Console:E AndroidRuntime:E | tail -40
crashes=$(adb logcat -d -b crash | grep -A40 "Process: $app" || true)
if [ -n "$crashes" ]; then
  echo "::error::LifeMax crashed during launch on the emulator."
  echo "$crashes"
  exit 1
fi
if ! adb shell pidof "$app" >/dev/null; then
  echo "::error::LifeMax is not running 30 s after launch on the emulator."
  exit 1
fi
echo "Launch check passed: LifeMax started and kept running."
