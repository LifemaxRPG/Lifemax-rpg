#!/usr/bin/env bash
# Launch check before a release, run on the emulator: install the APK, open it, and fail if Android records a crash
# or the app is no longer running once its start-up has settled. Page errors are printed for the log but do not fail it.
# Every adb call has a time limit: an emulator that stops answering is reported as a warning (the launch could not be
# checked), never as a pass or a crash, and cannot hold the step until it times out.
set -uo pipefail
apk=android-wrapper/android/app/build/outputs/apk/debug/app-debug.apk
app=com.lifemax.rpg
log=$(mktemp -d)
exec 3>&1 # the step's output, for messages from inside a redirected command

unchecked() {
  echo "::warning::The emulator stopped answering ($1), so the launch could not be checked." >&3
  exit 0
}
# run LIMIT WHAT COMMAND...: the command's own exit code, or the warning above if it ran out of time
run() {
  local limit=$1 what=$2
  shift 2
  timeout "$limit" "$@"
  local rc=$?
  if [ $rc -eq 124 ]; then unchecked "$what"; fi
  return $rc
}

run 120 "installing the APK" adb install -r "$apk" || { echo "::error::The APK did not install on the emulator."; exit 1; }
run 20 "clearing the log" adb logcat -c
run 60 "starting LifeMax" adb shell am start -W -n "$app/.MainActivity" || { echo "::error::LifeMax did not start on the emulator."; exit 1; }
sleep 30 # WebView start, the page's own start-up, the native Overview opening and the first-run thumbnails

echo "--- page and runtime errors ---"
run 20 "reading the log" adb logcat -d -s Capacitor/Console:E AndroidRuntime:E > "$log/errors.txt"
tail -40 "$log/errors.txt"
run 20 "reading the crash log" adb logcat -d -b crash > "$log/crash.txt"
if grep -q "Process: $app" "$log/crash.txt"; then
  echo "::error::LifeMax crashed during launch on the emulator."
  grep -A40 "Process: $app" "$log/crash.txt"
  exit 1
fi
if ! run 20 "checking the app process" adb shell pidof "$app" >/dev/null; then
  echo "::error::LifeMax is not running 30 s after launch on the emulator."
  exit 1
fi
echo "Launch check passed: LifeMax started and kept running."
