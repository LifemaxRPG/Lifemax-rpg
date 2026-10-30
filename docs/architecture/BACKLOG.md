# Backlog from the pre-rewrite audit

The audit of 30 October 2026 (ten review areas) found no critical issue. Three high-severity defects were confirmed and fixed with regression tests: system Back on native Food (NAV-1), whole-save replacement without confirmation or safety copy (data safety), and most web tests not running in CI (CI-2); plus a defect in the first data-safety fix (LOG-2). Earlier in the same session: the unreadable-save overwrite, the silent failed write and the double-paying submit.

Everything below is recorded, not scheduled. Items marked *verified* were checked against the source by an independent agent; the rest are the reviewer's own reading. Take an item when the tab or change it belongs to comes up; do not refactor ahead of need.

## Done since the audit

- NAV-6: the hand-over reads the NativeTabs registry (`has`, `covered`); a new native tab needs no edit in 50-native-overview.js.
- CI-1: on the release branches the APK must launch and keep running on an emulator before anything is published (android-wrapper/smoke_launch.sh); the tab-switch timing check after it stays advisory.
- CI-3: publishing replaces the files of the `latest` release in place (upload under a temporary name, then rename), version.json last, and keeps the APK it replaces as `LifeMax-RPG-previous.apk` in the same release.
- CI-4: a release-branch build without the permanent signing key fails instead of publishing a throwaway-signed APK.
- C1: every web test, including log-activity, native-handover and native-cash, and the hand-over simulation run in CI (web-tests job).
- C4: A33 in CONTRACT.md is the definition of done for a native tab (beta until then; web code deleted only after a release as default).
- C5: ADRs 0005 (presentation first, shared scaffold), 0006 (hand-over registry and safety net), 0007 (screenshot proof, local build), 0008 (save protection), 0009 (release safety).
- A27's check: `tools/check_bridge_catalog.py` (in CI) fails when a native destination plugin is unregistered or uncatalogued, or an entry names no plugin.
- SEC-4: Android device backup is off by default with a Settings switch (ADR 0010); the web bundle is never in a device backup.
- CI-6, partly: caching would not shorten the time to a release, because the web-tests job is the longer path; not done.
- Workout plan, native (Native API 25, beta): ticks go to the page's own toggle as a kit command; the screen draws the XP celebration itself and the page plays only its sound and buzz (`xpCue`), which settles L1 below for this tab.
- Life Director Today, phase 1 of 4 (Native API 25, beta): view-only; every write is the page's own handler, and the screen covers the page again only once its celebrations have played (`afterCelebrations`), which settles L1 for this tab. Phase 2 (the dial's drag handles, the build animation) is next.
- Shared CSS drawing (`core/designsystem/Css.kt`): box-shadow, linear-gradient, blur paint and color-mix, used by both new tabs; no box shadow before Android 9, which cannot blur it.
- SEC-1: over-the-air downloads (version.json, web bundle, APK) start only at https://github.com, and every redirect is checked before it is contacted and must stay https on github.com or *.githubusercontent.com (the real path today is one redirect to release-assets.githubusercontent.com). The wildcard is deliberately wider than the hosts in use, so a change of storage host never stops updates; the web bundle's SHA-256 check (kept) and Android's APK signature check carry the integrity. `app/update/DownloadRules.java`; `tests/updater-network.test.py` runs it offline against hostile redirects, and it was run once against the live release from the JVM (redirect allowed, app.html hash matched). Still to see on the phone: one web update and one APK update.
- SEC-3: a link never changes the save by itself. Feed opens the cat room and asks; a schedule link goes through `LifeMaxVoice.fromLink` (a new Life Director entry only, never another voice command or the online fallback, always stopped at the preview even with "always preview" off); title and description capped at 120 characters and cleaned of control and direction-override characters; links over 4,096 characters ignored; at most five queued. `lma_open` is accepted only for screens in `app/launch/LaunchRules.java` (today only the dial widget's `life-director`). Every producer was checked: shortcuts.xml (3 shortcuts, 2 capabilities), LifeDialWidget, WidgetBridgePlugin, and the Gemini voice path (the owner's own request, which still feeds at once, also when it asks to open the feeding screen). `tests/deeplink.test.js` (41 checks, fails on the old code), `tests/launch-intent.test.py`.
- SEC-5, partly: one APK download (`Updater.install`) and one `LiveWeb.fetch` at a time; size caps (web 80 MB, APK 300 MB, version.json 256 KB) checked against the declared and the counted size. The shared limits helper for new tab plugins is not done.
- SEC-6: the FileProvider shares only `cache/update/` (UpdaterPlugin's APK for the installer) and the app's external `Pictures/` (Capacitor's camera capture for an `<input capture>`, unused today), replacing the template's storage-root and whole-cache entries. FileSaverPlugin and BackupFolderPlugin use the storage access framework and share nothing. CONTRACT.md records that native code logs nothing. Checked in the built APK; the first in-app APK update made *from* this build is the device check.
- NAV-4, partly: a launch intent handed back after process death, a re-creation or a reopen from Recents (saved state or FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) is stripped before the bridge reads its launch URL; every delivered intent, including onNewIntent, is marked consumed; the page does not re-run its launch URL after a reload in the same session (an over-the-air restart; `tests/deeplink.test.js` drives it through a faked App plugin). The Android side has no automated test (it needs an instrumented or Robolectric check); still to see on the phone: a widget tap or link, then a reopen from Recents and a return after the process was killed, each opening nothing again. Not done: the 9 s wait before a widget tap opens Life Director on a cold start.
- Data safety (ADR 0004): verified folder backups, a verified snapshot before an import, a reset or an update, `SAVE_SCHEMA` with `migrateSave()` and read-only for a newer save, and golden saves restored and compared in `backup-restore.test.js`. Also the load() half of the save epoch item (Native write path): an unreadable save is kept, never stored over; the epoch itself comes with the first native write.
- LOG-4, the preview half of the write-path item and edit 1 of the outcome-surface item: the web form and the native Log preview the XP Save would pay, from the function Save pays with (`activityAward(draft, true)`, a peek that changes nothing), with the page's reason when it is not the raw total (repeat, perk, rested XP, daily cap, stars) or the refusal Save would give. A save reports the XP it stored, star bonus included. Proven on every golden case and a scenario matrix, down to the skill's XP (tests/log-activity.test.js).
- LOG-5, in part: the web card and the projection word the duration rules from one string next to ecoDur (`ECO_DURATION_RULES`, 'up to 3× at 120 min'); the projection carries `minMinutes`, and the native duration stays within the page's limits.

## CI and tests

- **medium** — Release is published without ever launching the APK, and the only emulator check on the release branch runs after publish and cannot fail  
  Move the emulator steps (release daemon, KVM, android-emulator-runner) so they run before 'Publish to a GitHub Release' on the release branch. Make the new step blocking by dropping continue-on-error there. Point it at a new… *(ci-and-tests CI-1, verified)*
- **medium** — Publish step deletes the only release before creating the new one, with cancel-in-progress able to interrupt between the two; previous APK is never kept  
  Close the cancel window by making the publish uncancellable and the swap non-destructive. Smallest change: keep the release, and in the publish step do `if gh release view latest --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then… *(ci-and-tests CI-3, verified)*
- **medium** — Missing signing key silently publishes a throwaway-signed APK as latest  
  On BRANCH_OK refs, make the key step `exit 1` when the secret is empty (keep the warning behaviour only for workflow_dispatch/codex branches), and skip the publish step when stable != 1. *(ci-and-tests CI-4, not verified)*
- **medium** — NativeApi version guard silently passes when it cannot compare, and ignores several inputs that change the APK  
  Fail (not skip) when version.json is unreachable on BRANCH_OK refs unless a `[skip-guard]` marker is in the commit message; add package.json and package-lock.json to the diffed paths. *(ci-and-tests CI-5, not verified)*
- **medium** — Build is slow and not reproducible: no caches, floating versions, no check that plugins are catalogued  
  Switch to `npm ci`, add `cache: npm` with cache-dependency-path android-wrapper/package-lock.json, add `gradle/actions/setup-gradle@v4` before the build (it caches the Gradle user home), and pin ubuntu-24.04. Add a 15-line python… *(ci-and-tests CI-6, not verified)*
- **medium** — Most web tests in CI test a stale snapshot, not the current source  
  Unless given a file, 25 of the 41 `tests/*.test.js` (21 of them run in CI, among them voice4, gemini, ota, lock, swipe and food) load the newest committed `dist/Life_Max_RPG_vNNN.html`: v209 of 28 October, 46 source commits old, without NativeTabKit or the native Log. Ten others read source segments directly; six build the current page (log-activity, both handover tests, native-log, native-overview-chrome and, since SEC-3, deeplink). Fix: one helper that runs build.py and forge_features.py once and passes the page to every test, then repair what fails against the current source. *(found 30 October 2026, verified)*
- **low** — A reload of the `file://` test page sometimes comes back without what the page stored: sessionStorage is empty, and localStorage can lack the last writes  
  Seen several times in 40 reloads; backup-restore.test.js serves its page over `http://127.0.0.1` for that reason. Likely the cause of the tabs.test.js flake in SKIP_IN_CI.txt: serve that page over http too. *(backup work)*

## Design system and accessibility

- **medium** — Food ignores LifeMaxTheme and web tokens; its light mode is hand-patched and never screenshot-tested  
  Before Log is finished, map FoodPalette surfaces/text to the LifeMaxTheme colour scheme (or a small extended-tokens object in core/designsystem), keep only the macro/meal semantic colours (rose/amber/teal) as named constants,… *(design-system DS-1, not verified)*
- **medium** — Duplicated primitives: pixel icon table and card exist twice, plus a third private progress bar/dot set  
  Delete CharacterPixelIcon/CharacterCard/tokenColor extension in favour of the core ones; move CharacterProgress into core as ProgressBar; add a single icons-parity check (the web masks vs Kotlin map) to the existing fixture… *(design-system DS-2, not verified)*
- **medium** — Accessibility is near-absent in Food and the shared tap targets are not enforced  
  Introduce a shared `Modifier.tapTarget(label, role)` in core/designsystem that sets minimumInteractiveComponentSize + Role + onClickLabel, use it in Food and as the only way to make clickables in Log; add Paparazzi cases with… *(design-system DS-3, not verified)*
- **medium** — No typography/spacing/radius/motion tokens; every screen uses literal sp/dp and the theme has no Typography or Shapes  
  Add a small LifeMaxType/LifeMaxShapes/LifeMaxSpacing set and a LocalReduceMotion composition local fed from the same projection; make the token fallback fail in debug/tests (check required keys list) instead of defaulting to… *(design-system DS-4, not verified)*
- **medium** — Missing shared controls that Log needs: stepper, text field with IME, sheet/dialog, toggle, list row  
  Build text field, stepper and bottom sheet in core/designsystem only when Log first needs them, with insets/IME handled once; not before. *(design-system DS-5, not verified)*
- **low** — No component gallery or variant matrix for screenshot tests; dark/light and font scale uncovered  
  One GalleryScreenshotTest rendering SurfaceCard/ChoicePill/SegmentedChoice/RoundButton/PixelIcon set in dark, light, and fontScale 2.0; add light and 2.0 variants for Log when it lands. *(design-system DS-6, not verified)*

## Navigation and shell

- **medium** — There is no way into Log except navigate('log'); nothing prefills it, and the native prefill hook has no caller  
  Add one entry function, e.g. openLog({type,duration,quality,skill,notes}). It validates against `rules` and the NativeLogProjection.clean limits, writes to both the DOM form and setDraft, then calls lmaNavTo('log'). Route… *(navigation-shell NAV-3, not verified)*
- **medium** — *Replay done (see above); the 9 s cold-start wait remains.* Launch intent (deep link, widget extra) is replayed after process death or a recents restore; widget tap on a cold start waits 9 s  
  In MainActivity.onCreate skip launchTarget and clear the launch URL when savedInstanceState != null or the intent carries FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY. Mark a delivered intent consumed (strip the extra and setIntent(new… *(navigation-shell NAV-4, not verified)*
- **medium** — Keyboard over a native screen and landscape: Compose imePadding is a no-op and the chrome stays stale  
  Decide one owner for IME insets. Either stop consuming ime in the content listener and let Compose apply imePadding, or hide the shared bottom bar while the IME is visible. Add a resize/orientationchange trigger to NativeTabKit… *(navigation-shell NAV-5, not verified)*
- **medium** — Adding a native tab still means editing hard-coded tab lists in 50-native-overview.js; A22/A30 do not need to be built before Log  
  Now (small): derive nativeTab(v) and `covered` from NativeTabs (use covered(v) and the registered map) so a new tab needs no change here. Add a sim-test row that every registered tab appears in the activate path. Later, together… *(navigation-shell NAV-6, not verified)*
- **low** — NativeTabKit closes the native screen on every native action, but Log needs live preview and a submit that can fail  
  When wiring Log, give the kit's action handler an opt-out. For an event with `e.keepOpen`, or a separate "command" listener, skip `close()`, call `cfg.onCommand(e)`, and return the reply with plugin.update or a result event. Put… *(navigation-shell NAV-2, verified)*

## Performance and lifecycle

- **medium** — No app-specific Baseline Profile and no startup trace; cold open of each native tab is uncovered and only measured by an optional, non-gating emulator step  
  Add Compose-library baseline profiles come for free through the transitive profileinstaller; add a small app baseline-profile.txt (hand-listed com/lifemax/rpg/** classes, packaged in src/main/baseline-prof.txt) now, which needs… *(performance-lifecycle P1, not verified)*
- **medium** — No release-style hardening exists, and the shipped build is assembleDebug without R8; the minify flag in the release block is a trap for later  
  Do not enable R8 now. Add the keep rules (-keep @com.getcapacitor.annotation.CapacitorPlugin class *, -keepclassmembers class * { @com.getcapacitor.PluginMethod <methods>; }) and a one-line CI check that fails if build.gradle… *(performance-lifecycle P2, not verified)*
- **medium** — Nothing releases hidden native trees under memory pressure and the native state is not restorable after process death  
  Instrument now: log Debug.getPss / Runtime totalMemory after each tab's first compose into the same nav-perf output. Cheap mitigation when a number says so: in ComposeTabPlugin register ComponentCallbacks2 and on… *(performance-lifecycle P3, not verified)*
- **medium** — Each projection update is serialized and parsed several times, partly on the main thread  
  Not a refactor: do the size check from the raw call payload length once (or measure time of toString in TabTiming), parse to immutable data classes off the main thread, and publish them to state. Defer until a native tab shows a… *(performance-lifecycle P4, not verified)*
- **low** — Most native screens use Column plus verticalScroll, which composes all content eagerly and keeps it composed while hidden  
  Add a note to NATIVE_TAB_GUIDE.md: any list that grows with user data must be a LazyColumn with stable keys. No code change now. *(performance-lifecycle P5, not verified)*
- **low** — Proposed performance gate: record, then ratchet, using emulator relative numbers  
  (1) Hard gate, deterministic, no device: fail CI if APK bytes grow >5% or app.html grows >10% versus the previous release version.json (size ratchet), and if the method count / dex size jumps. (2) Soft gate on emulator: median of… *(performance-lifecycle P6, not verified)*

## Security and privacy

- **low** — Device backup: a fresh install restores whatever Android's copy holds without saying so; a copy made before the switch shipped is stale, and choosing the backup folder again then writes that stale save as the newest backup file  
  Override onRestoreFinished in DeviceBackupAgent to record the restore; show a one-time note ("LifeMax was restored from an Android backup; import a newer backup file if you have one") and hold the first automatic folder backup until the owner acknowledges it. *(device-backup review, verified against AOSP)*
- **low** — Device backup copies the saved Gemini API key when the switch is on  
  Move the key out of WebView storage into an excluded native store, or name it in the switch text. *(device-backup review)*
- **low** — patch_manifest.py exits early on an already-patched manifest, so a local android/ folder keeps an older manifest; check_source.py's module map lacks the native and device-backup segments (not run in CI)  
  Delete android/ after a change to patch_*.py (say so in tools/LOCAL_BUILD.md) or apply the idempotent blocks before the early exit; regenerate src/runtime/module-map.json or drop the count assertion. *(device-backup review)*

- **medium** — Journal lock is a DOM overlay inside #view-history only; native tab screens have no lock awareness  
  Add a rule to CONTRACT.md and NativeTabKit.open: a tab whose view is 'history', or whose projection can contain journal text, may open only when !jlOn() || LifeMaxJournalLock.unlocked(). Otherwise it stays on the web view with… *(security-privacy SEC-2, not verified)*
- **medium** — *Done (see above).* lifemax:// deep links from any app or web page mutate state and feed unsanitised text into the voice engine  
  Cap title and description at about 120 characters and strip control characters in lmDlParse. Make 'cats/feed' open the pet tab and show a toast instead of saving, or require a tap. In MainActivity, accept lma_open only for a… *(security-privacy SEC-3, not verified)*
- **medium** — allowBackup is true (Capacitor default) and not constrained, so journal and health data in WebView storage leave the device through backup  
  In patch_manifest.py set android:allowBackup="false", or ship dataExtractionRules that exclude files/live and shared_prefs/lifemax_live, if the owner relies on device-to-device transfer. The in-app backup folder export already… *(security-privacy SEC-4, not verified)*
- **low** — *Done: GitHub-only source and redirects (see above); still no signature of our own.* Web bundle update is only hash-checked against a hash from the same server, so it is not authenticated  
  The smallest change that closes the gap without new signing infrastructure is in LiveWebPlugin.fetch(). Parse the host and require it to be github.com (or objects.githubusercontent.com after redirect). Reject when the response's… *(security-privacy SEC-1, verified)*
- **low** — *Partly done: in-progress guards and size caps (see above).* Native plugins have no input validation layer; sizes and ranges are checked ad hoc  
  Add a small shared helper for plugin limits (max string length, clamp from/to, https host allow-list) and use it from the new tab plugins. Guard Updater.install and LiveWeb.fetch with an in-progress flag. Reject sizes above a cap… *(security-privacy SEC-5, not verified)*
- **low** — *Done (see above).* FileProvider exposes the whole external storage root and the whole cache dir  
  Narrow it to <cache-path name="update" path="update/"/> plus the exact folder FileSaverPlugin writes into, and remove the external-path root entry. Record in CONTRACT.md that native code does not log personal data. *(security-privacy SEC-6, not verified)*

## Contract and documentation

- **medium** — Contract-mandated safety tests do not run in CI (hand-over sim, Log golden, Cash, handover)  
  In build-android.yml, add these three lines after the director-sleep line and before build.py: `node lifemax_modular_v73/tests/log-activity.test.js`, `node lifemax_modular_v73/tests/native-handover.test.js`, `node… *(contract-docs C1, verified)*
- **medium** — Contract states ratchets/enforcement that do not exist (A26, A27, A03/A26 vs flat Java)  
  Edit A26: state that Java plugins, MainActivity.java and NativeApi.java in android-wrapper/native/ are exempt; list CashActivity.kt and 50-native-nutrition.js as recorded exceptions; relabel the over-long-line and file-length… *(contract-docs C2, not verified)*
- **medium** — Catalog and ledger are inaccurate: Log tab in progress unrecorded, 'five destinations' untrue, stale tables  
  Add a Log row to MIGRATION.md ('in progress, not wired') and a Log catalog entry in the commit that adds its plugin; mark the old baseline and API 17 tables historical or delete them; give the character entry the same fields as… *(contract-docs C3, not verified)*
- **medium** — No definition of done for making a native tab default or deleting its web tab  
  Add A33 'Tab definition of done': opt-in until (1) every web action is native or listed as delegated in its catalog entry, (2) dark and light screenshot references, (3) projection node test plus a golden if it writes, (4) the tab… *(contract-docs C4, not verified)*
- **medium** — Missing ADRs for decisions already taken  
  Add short ADRs: 0005 hybrid logic-in-web plus shared scaffold; 0006 heal safety net and its limits; 0007 Paparazzi, fixture generation, local build and Maven mirror (including OverviewActivity removal); 0008 save protection… *(contract-docs C5, not verified)*
- **low** — No contract section for interaction capabilities remaining tabs need; A24 cites a ledger note that does not exist  
  Add A34 stub 'Platform interaction rules' with one-line rules for IME insets and Back dismissing the keyboard before leaving the tab, sheets and dialogs sharing the A16 Back owner, haptics only via NavHaptics, lists with stable… *(contract-docs C6, not verified)*

## Log tab

- **medium** — Hidden rules and web-only behaviours the native Log must carry or drop on purpose  
  Record this list as the Log parity checklist in docs/architecture (the copy, `minMinutes` and `durationRules` are done). In the… *(log-tab-readiness LOG-5, not verified)*
- **low** — Removing the Log DOM makes navigate('log') throw and abort navigation  
  In 04-runtime.js navigate, change `if(view==='log')updatePreview()` to `if(view==='log'&&document.getElementById('previewXP'))updatePreview()`. Do this only when the native Log actually stops rendering the form, since the throw… *(log-tab-readiness LOG-1, verified)*
- **low** — Projection gaps: what the native Log needs that the page does not yet expose  
  Add the fields to snapshot(): `skills` (the top 10 of state.skillXP), `recent` (the last 5 activities), `limits` {sameTypeToday per type, manualMinutesLeft}. Extend the submit result with {xpSplit, duplicate}. Add the matching… *(log-tab-readiness LOG-6, not verified)*
- **low** — Life Director keeps its own copy of the activity XP formula  
  H.xpFor (features/life-director/logic.js) repeats activityPreview and activityAward, and its comment still names the removed calcPreview(). For the Life Director track: have it call activityAward, which would need the activity's day and no manual-time limit, or at least point the comment at recordActivity()/activityAward(). *(log XP preview review, verified)*

## Remaining tabs

- **medium** — Web-only feedback surfaces (level-up ceremony, XP float, toast) are invisible while a native tab is open and are not in the overlay list  
  Before Workout plan starts: (a) add .luov, .lk-ov, .jl-overlay and the toast to lmaTopOverlay or an equivalent 'web is presenting' predicate so the native tab hands over (or defers the web ceremony until the tab closes); (b) have… *(remaining-tabs L1, not verified)*
- **medium** — Overlay and Back handling is a per-surface hard-coded list; native sheets/dialogs have no shared stack  
  Not a rewrite now. When Workout plan is built, add one small shared 'LifeMaxSheetHost' (stack list + a single BackHandler that pops it, imePadding built in) in core/designsystem and make Life Director use it from day one; record… *(remaining-tabs L2, not verified)*
- **medium** — Owner's open question: Quests table and Cat room may stay web, but that fixes the shell and hand-over design and needs a 1-day spike before Life Director…  
  Spike now, small: from the existing Character tab add a debug action that opens the gym3d scene and returns, in the web WebView, while the Compose surface is closed, and measure context loss, memory and the hand-back on the S25… *(remaining-tabs L3, not verified)*
- **medium** — No shared feedback channel from native screens: haptics, sound and the user's Settings > Haptics/Sound choices are web-only  
  Add fx settings to the shared snapshot and a single Kotlin feedback helper in core/designsystem when Workout plan starts. SFX assets: export the 580 KB samples to res/raw from tools (they come from Kenney CC0 files in art/sfx)… *(remaining-tabs L4, not verified)*
- **medium** — Large art and data blobs live inside the JS bundle; native has no path to them and the bridge snapshot is capped at 2 MB  
  No change now. Rule for the guide: new projections send ids plus visible rows and a query/limit action for lists; assets stay generated from art/ and are packaged by the build rather than extracted from JS. Decide on the pet DNA… *(remaining-tabs L5, not verified)*
- **low** — Long-running timers and notifications have no native owner (workout rest timers, Life Director dial, pet sleep)  
  When Workout plan reaches a rest timer, do a one-screen spike (AlarmManager + notification channel, POST_NOTIFICATIONS) before building the UI; keep it out of the audit backlog until then. *(remaining-tabs L6, not verified)*

## Native write path

- **medium** — The shared tab scaffolding has no command path, and the kit closes the screen before it runs any action and drops the result  
  Two small changes, and no new protocol. (A) Now, independent of the native work, in recordActivity (04-runtime.js:191): capture `const rested=state.restedXP` before ecoAward. If `!save()`, undo exactly what the function did:… *(log-write-path, verified)*
- **medium** — save() is not a commit boundary: a failed persist still reports 'Activity saved', effects run on both sides of the write, and a refused log mutates state  
  Three small edits, all in the web code. The tested patch is /tmp/claude-0/-home-user-Lifemax-rpg/73f0826e-b578-5741-9922-ec416a3fc508/scratchpad/patch.py. (a) 04-runtime.js recordActivity, undo what it staged when the write… *(log-write-path, verified)*
- **medium** — A native Submit can be tapped when the page cannot hear it: events are dropped silently, page starts wipe listeners, and nothing native is persisted  
  Do not build durable-op or DataStore machinery now. Do the smallest part that closes the stale-screen hole: in ComposeTabPlugin.load(), add a bridge WebViewListener whose onPageStarted calls hide() on the UI thread, and also have… *(log-write-path, verified)*
- **medium** — The outcome surface is wrong for a native screen: web toasts, celebrations and the level-up overlay are hidden or overwritten, and derived effects never reach…  
  Two small edits now, the rest deferred to the native Log submit work. Edit 1, return the stored value, is done (see Done). Edit 2… *(log-write-path, verified)*
- **medium** — The owner function does no input validation (the preview half of this item is done)  
  At the top of recordActivity, reject with {ok:false,message,code:'invalid_field'} if `!rules[d.type]`, if `d.primary` is non-empty and not in ATTRS, or if `!Number.isFinite(Number(d.quality))`; cap notes, skill and customName… *(log-write-path, verified)*
- **medium** — The save has no epoch, and the only whole-save replacement paths include one that can silently overwrite it with a blank state  
  In the load() catch block, use a unique key per failure, `STORE+'_unreadable_'+Date.now()` (as the reviewer proposed), instead of the single write-once key. Only overwrite STORE when the copy was written successfully. Add a small… *(log-write-path, verified)*

## Data safety

- **medium** — There is no backup the owner can rely on before a risky change: the folder backup is opt-in and off by default, never runs before an update, restore or reset,…  
  Take a pre-change snapshot, and block the action if it fails. Add 'async function lmaPreflightBackup(){ if(!lmaNative()) return true; try { await lmaRunBackup(true); return lmaLoad().backup.status==="ok" } catch(e){ return false… *(data-safety, verified)*
- **medium** — The core save has no schema version; migrations are scattered boolean flags that run at load and commit before a build is proven healthy, so APK/OTA rollbacks…  
  Smallest useful step, with no new architecture. (1) Add `schema:1` to freshState and a constant SAVE_SCHEMA=1. (2) In load(), after parsing, if (state.schema||1) > SAVE_SCHEMA, do not call the migrations and do not write back;… *(data-safety, verified)*
- **medium** — Golden tests cover only an empty profile, and the Playwright suites that guard the save-writing path (log-activity, ota, handover-sim) are not run by CI; there…  
  Add one CI step before the node tests in build-android.yml: `npm i --no-save playwright@<pinned> && npx playwright install --with-deps chromium`. Then run `NODE_PATH=$PWD/node_modules node… *(data-safety, verified)*
