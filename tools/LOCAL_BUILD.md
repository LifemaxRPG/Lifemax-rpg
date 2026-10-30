# Building and testing the Android app locally

`tools/build_android_local.sh` does what the CI build step does: web build, Capacitor project, patches, then Gradle.

```
tools/build_android_local.sh compile   # Kotlin compile only, about 2 minutes the first time, seconds after
tools/build_android_local.sh           # compile, assemble, JVM unit tests (about 3.5 minutes)
```

It needs Java 21, node, python3 and an Android SDK (platform 34, build-tools 34.0.0) in `ANDROID_HOME`. The Kotlin it compiles is read straight from `android-wrapper/native/src`; the Java plugins are copied in.

## Setting up a cloud session that has none of this

1. Allow the Android and Maven hosts in the environment's network settings (`dl.google.com`, `maven.google.com`, `repo.maven.apache.org`), with package managers allowed.
2. SDK: download `commandlinetools-linux-*_latest.zip` from `dl.google.com/android/repository`, put it at `$ANDROID_HOME/cmdline-tools/latest`, run `sdkmanager --licenses`, then `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"`.
3. If Maven Central answers `429 Too Many Requests` (a shared proxy can throttle it), copy `tools/gradle-central-mirror.init.gradle` to `~/.gradle/init.d/`. It reads Central through Google's mirror and changes nothing in the repository.
4. If `npm install` cannot resolve `registry.npmjs.org`, run the script with the no-proxy variables unset: `env -u NO_PROXY -u no_proxy -u npm_config_noproxy -u GLOBAL_AGENT_NO_PROXY tools/build_android_local.sh`.
5. ktfmt: `tools/check_native_format.py <ktfmt-0.53-jar-with-dependencies.jar>` checks formatting; `java -jar ktfmt.jar --kotlinlang-style <files>` fixes it.

Generated folders (`android-wrapper/android`, `www`, `node_modules`, `package-lock.json`) are ignored by git. The emulator checks still run only in CI.
