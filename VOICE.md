# LifeMax Voice, phase 1: engineering report

## Conversational NLU and more domains (v126)
`46-voice-nlu.js` runs before any scheduling. `lmvClassify` decides what a sentence is FOR from word buckets (question markers, quest / stat / schedule / cat / steps / food nouns, create / move / cancel / complete verbs), so "what are my quests today" is a quest read and never a scheduling question that asks "What time?". An explicit scheduling verb ("put", "add", "book", "remind me", "block out", "set me") always wins, even with a trailing question mark.
- **Open-question replies** ("any time", "whenever (I'm free)", "you pick", "doesn't matter", "now", "right now", "in 30 minutes", "in an hour", bare "morning / afternoon / evening / tonight", "noon", "midnight") resolve to a real free slot (searching 9:00 to 21:00, or from the next quarter hour today) or the stated time, and go to the preview with a note saying what was picked. If nothing is open, it says so and asks for a time.
- **Escape:** "cancel", "never mind", "forget it", "stop", "nothing", "abort", "scratch that" end the pending request at any point.
- **Barge-in:** a complete command or question for another domain while a question is open drops the half-built activity and runs the new command.
- **Handlers (all read from the app's own stores):** quests (`rpg.view_quests`), XP / level / streak (`rpg.query_stats`), schedule and free time (`life_director.query_schedule`, `find_free_time`), cats (`virtual_pets.check_status`, feed / pet / play), steps and today's activity (`health_fitness.query_steps`), calories and macros (`nutrition.check_totals`; water is not tracked and says so), and opening any screen (`navigation_system.open_section`). Results show in the HUD with an Open button.
- **Activity actions:** complete a session through Life Director's own completion (XP and quest hooks fire; only once it has started); move (shift or absolute time) and cancel always show a before / after preview and wait for confirmation. They work on planned sessions and on commitments, including ones made by voice.
- Not done: logging food, water or workouts by voice; waking a specific one of several cats by name beyond the first two.

## Past, present and ranges (v130)
`49-voice-time.js` adds one temporal resolver (`lmvScope`) used by every read handler, so an answer is about the day that was asked.
- **Days:** today, yesterday, the day before yesterday, N days ago (digits or words), last night, earlier today, this morning, tomorrow, last / this / next weekday, "October 20", "10/25". For questions, a bare weekday means the most recent one; for the schedule it means the coming one.
- **Ranges:** this week (Monday to today), last week, past N days, this month, last month, all time.
- **Honest zeros:** a day with nothing says so ("0 steps yesterday", "No workouts logged last Friday", "0 kcal yesterday") and shows that day's date; it never falls back to today. Steps are kept for 30 days, and older days say so. Range answers give the total and a daily average.
- **Handlers:** steps, workouts, XP (earned on a day or range, total, until level N, level, title, streak, achievements), calories and macros, quests finished, and "what did I do / log yesterday" (reads your activity log; today and later still read the plan).
- **Follow-ups:** for 90 seconds, "what about yesterday?", "and this week?", "how about tomorrow?" re-ask the last question for the new day, and "what about calories?" switches the metric and keeps the day.
- **Logging what already happened:** "log a 30 minute run", "I did 20 minutes of yoga yesterday", "log 45 minutes of weight training earlier today". Shows a preview with the time, length and estimated XP, then saves the same record the Log activity screen makes, for that day (no "time already passed" error; the future is refused; scheduling still keeps that guard). Steps cannot be typed in, because they come from Samsung Health, and it says so.
- **Speech repair:** common mishearings ("west" or "requests" for quests, "carona", "life direct her", "mack rose", "x p", "work out", "how many stairs") are fixed before the sentence is understood, then a one-letter fuzzy match against the app's own words.
- **Screens:** every tab and feature opens by voice (overview, go home, character, log activity, quests, workout plan, library, settings, Life Director, Corona dial, cat room), with or without "open".

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
| Verified automatically (`node tests/voice.test.js`, 222 checks) | Needs a real phone |
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

## Generation 4 (v138)
`src/runtime/segments/50-voice-gen4.js`, tested by `tests/voice4.test.js`. It wraps `lmvHandle`, `lmvClassify`, `lmvDomain`, `lmvExecute` and `lmvRender` instead of changing their logic, in this order: shortcut you taught it -> edit of the card on screen -> self-correction -> "right after that" -> two-part split -> the normal pipeline. New intents: `assistant.daily_briefing`, `life_director.whats_next`, `assistant.check_goal_progress`, `assistant.recommend_action`, `analytics.compare_periods`, `fitness.workout_plan`, `library.search`, `navigation_system.start_focus_session`, `navigation_system.launch_external_app`, `assistant.voice_help`, `assistant.routine`, `assistant.teach_alias`. Scheduling still always previews first; a two-part request waits for the first yes. Not device-tested: opening other apps (uses the existing AppLauncher plugin), the timer notification, and speech through the real recogniser.
