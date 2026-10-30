# ADR 0009: A release reaches the phone only after it launches, and the previous one stays available

Status: implemented in `.github/workflows/build-android.yml`.

## Context

Every push to the release branch publishes an APK and a web bundle that the owner's phone offers as an update; the owner cannot repair a broken install without a computer. The old publish step deleted the release before creating the new one (a cancelled run could leave nothing to download), kept no previous build, could publish an APK signed with a throwaway key, and launched the APK on an emulator only after publishing, without the power to stop it.

## Decision

1. A release waits for the APK build with its unit and screenshot tests, the web tests with the hand-over simulation, and a blocking launch check: the APK must install, open and keep running on an emulator (`android-wrapper/smoke_launch.sh`).
2. The `latest` release is updated in place, `version.json` last, so an interrupted publish leaves a consistent or an older set; the build it replaces is kept as the `previous` release.
3. A release-branch build without the permanent signing key fails.
4. Native changes must raise `NativeApi.VALUE`, so phones on an older APK are sent to the new APK instead of a web bundle they cannot run (guard step).

## Consequences

- A crash on launch can no longer reach the phone through CI; a page-level error that does not crash is caught by the web tests, not by the launch check.
- The emulator boots on every release build; an emulator infrastructure failure now blocks a release until re-run.
- The owner can always reinstall the previous APK from the releases page.
