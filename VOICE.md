# LifeMax Voice, phase 1: engineering report

## Repository audit (what was actually found)
- **Android shell:** Capacitor 7 app generated in CI (`android-wrapper/`, package `com.lifemax.rpg`, minSdk 26). One `MainActivity` (BridgeActivity); the page is the single-file web build loaded by Capacitor (origin `https://localhost`). Native code reaches JS only through Capacitor plugins registered in `MainActivity` (`AppLauncher`, `FileSaver`, `Updater`, `BackupFolder`, `HealthSync`, `WidgetBridge`, `NavHaptics`, `PageThumb`, and now `VoiceEngine`). There are no `@JavascriptInterface` methods and no `file://` loading, so the spec's origin-token fallback is not needed.
- **Web layer:** plain JavaScript segments (`lifemax_modular_v73/src/runtime/segments/*.js`) concatenated into one script; no bundler or TypeScript. Voice lives in `45-voice.js`.
- **Life Director** (`src/features/life-director/logic.js`): one-off timed items are *fixed commitments*. The engine's own `reschedule({type:'NEW_COMMITMENT'})` adds one and re-plans the rest of the day with minimal churn. There was no public create/query/delete API, so a small seam was added (`LifeDirector.voice`: `ready, timezone, find, findMatch, add, remove, overlaps, workOn, dayItems`). `remove(id)` drops the commitment and regenerates the plan: that is the verified inverse that makes Undo real. Corona dial refresh = Life Director `render()` plus the Overview `glance()`.
- **CI:** `.github/workflows/build-android.yml` builds the debug APK on every push; there was no JS test runner, so `tests/voice.test.js` (Playwright) was added.

## Deviations from the written spec, and why
- **Bridge:** the spec prefers `WebViewCompat.addWebMessageListener`. This app already has Capacitor's bridge, which only talks to the app's own page, and every native feature uses it. Adding a second, parallel bridge would be a larger attack surface, not a smaller one. `VoiceEngine` therefore exposes only five methods (`capabilities`, `requestPermission`, `start`, `stop`, `cancel`), validates `sessionId` (<= 64 chars) and `locale` (regex), caps outbound events at 16 KB, and never evaluates transcript text as code. Transcripts are rendered with `textContent` only.
- **Language:** the spec's TypeScript module tree is one plain-JS segment here, to match the project.
- **sherpa-onnx:** not bundled in phase 1 (as the spec says). The plugin reports `OFFLINE_ENGINE_NOT_READY` when the on-device recognizer or its language pack is missing, and the typing simulator always works.

## Change manifest
**Native (needs an APK build, which CI does on push):** `android-wrapper/native/VoiceEnginePlugin.java` (new), `MainActivity.java` (register plugin), `patch_manifest.py` (RECORD_AUDIO permission, RecognitionService query).
**Web (ships with the web build):** `src/runtime/segments/45-voice.js` (new), `src/features/life-director/logic.js` (voice seam, 1.9.0), `src/views/settings.html` (Voice card), `src/styles/07-finishing.css`, `src/index.template.html`, `src/runtime/module-map.json`, `tests/voice.test.js` (new).

## Verification matrix
| Verified automatically (`node tests/voice.test.js`, 28 checks) | Needs a real phone |
|---|---|
| canonical utterance, slot values, save into Life Director, Undo | live microphone capture |
| multi-turn "gym time" -> "around six" -> "evening, for an hour", same operationId | Android 12+ on-device language pack present and working |
| after-work with no hours, "next Friday", past time today, DST gap and fold | `VoiceEnginePlugin` runtime behaviour (it compiles in CI; it has not run on a device) |
| overlap with a sleep block shown in the preview | permission prompt flow |
| tier 1/2/3 idempotency including lost-response reconciliation | haptics, HUD feel |
| markup in a transcript stays inert | |
| registry statuses; source check that `createSpeechRecognizer` is never called | |

## Try it on the phone
1. Install the new build. Settings -> **Voice (beta)** -> Open: type "Put an hour long workout tomorrow morning at nine", Send, then Save activity. Check the dial on the Overview.
2. The round mic button (bottom right, above the tab bar) uses the offline microphone. First tap asks for microphone permission. If the phone has no offline speech pack the card says so; add it in Android Settings -> Speech services, or keep typing.
