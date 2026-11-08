# Demo mode

Settings has a "Run a demo" row. It lets the owner show someone LifeMax from the very first screen without starting a new save of their own. It starts a temporary save; Settings then has a "Return to my save" button, which discards the demo and opens the real save again.

Status: web only. No native code changed, so there is no new capability string, no `NativeApi` bump and no new bridge entry (section 7 says why none was needed).

## 1. Design

**A storage namespace, chosen before anything is read.** The page keeps one marker key, `lm_demo_active`, in the WebView's localStorage. `00-demo-storage.js` is the first script segment: before the page reads any save, preference or database it looks for the marker. When the marker exists:

- `window.localStorage` is replaced by a view over the same store in which every key is prefixed `lmdemo:v1:` on the way in and unprefixed on the way out. `getItem`, `setItem`, `removeItem`, `key`, `length`, `clear`, property access and `Object.keys` all work, and all of them see only the demo's keys. The real keys are never read, written, listed or cleared by page code, so the real save and every other real key stay byte-identical.
- IndexedDB database names get the same prefix (`IDBFactory.open`, `deleteDatabase`, `databases`), so the audio library, the USDA food database and the trainer pack are the demo's own.

Because the view replaces the one object every script uses, this covers the 40-odd places that touch `localStorage` directly, the Life Director feature scripts, and anything added later, without guarding each writer. The page's own code did not change for it. A new storage key is demo-safe automatically; a new native plugin is not (section 4).

**Why the marker survives a kill.** It is a localStorage key, so it lives as long as the app's data. A killed or crashed app starts again in the demo, with the demo's progress, and Settings still offers the way back. Only `LM_DEMO.leave()` removes it.

**Fail safe.** The marker means "demo" by its presence alone, whatever its value. An empty, truncated or garbage marker still shows the demo (blank), never the real save, and Return still works from it. If reading the marker throws, the real save cannot be read either. If the storage view cannot be installed (`defineProperty` refused), `LM_DEMO.on` stays false and the page runs on the real save with no demo feature on, which writes nothing demo-related. If the IndexedDB patch fails in a demo, `indexedDB` is made unavailable rather than left pointing at the real databases.

**Entering.** Settings > Data & advanced > Run a demo asks first: the save is temporary, the real one is not opened, changed or deleted, there is a button to go back, and backups, reminders, health sync, calendar import, the widget and Android backup are off. On "Start the demo" `LM_DEMO.enter(seed)` clears any leftover demo data, writes the starting preferences (below) under the demo prefix, and sets the marker last, so a failure before it leaves everything as it was. Native screens step aside (`lmaLeaveNative`) and the page reloads. The demo page finds no save in its namespace, so it does what a fresh install does: onboarding from the very first screen, then the tutorial.

**Starting preferences.** The demo copies, into its own namespace, the owner's look and layout choices (theme, the native-tab switches and bar order in `lifemax_android_prefs_v1`, and the `lma_*` look keys) so it looks like the owner's app. It does not copy `backup`, `apps` or reminders (`notif.on` is false), the Gemini key, the voice choices, the Life Director plan, the cat's last-seen time or any save. The real keys are only read, once, at this moment.

**Leaving.** "Return to my save" (a card at the top of Look & feel while in the demo, and the DEMO chip on every page screen) asks "Discard the demo data?"; the main button is "Discard and return", the other is "Stay in the demo". `LM_DEMO.leave()` removes the marker first (from then on the next start is the real save, whatever happens), then deletes the demo's keys and databases, and the page reloads onto the real save. A "keep it for next time" option was left out; the demo is always discarded. A real start also clears any demo keys that an interrupted exit (the page can write once more while it unloads) left behind.

**Visible on every screen.** A DEMO chip (page screens; tapping it opens the way back), the top bar pill reads DEMO, and the status bar is amber. The page cannot draw over native Compose screens, and a native change for a chip was not cheap (it would touch the shell, every projection and the screenshots), so the status bar is the signal that reaches them: it is set through the existing `AppLauncher.setSystemBars` call, which Android draws above everything. The status bar colour was not checked on a device in this work. Native Settings shows the "Demo mode" card at the top of Look & feel, with its Return button.

## 2. What is namespaced

Everything the page or a feature script keeps in localStorage or IndexedDB (section 5 lists them). Native-owned stores are not read or written by the page in the demo, because the plugin that reaches them is switched off (section 4).

## 3. Settings, classic and native

The two cards are ordinary Settings cards with ordinary rows, so the native Settings screen draws them from the page like every other card: "Run a demo" (Data & advanced) and, in the demo, "Demo mode" (first card in Look & feel). The buttons are not bound commands, so in native Settings they are delegations: tapping one closes the native screen and shows the page's card with the button focused, then the owner taps it and the page's confirmation appears (it is a `.lmdl-ask` overlay, which the tab kit already treats as something the page stays in front of). No command was added, so no fixture, no screenshot and no hand-over command set changed. One line in `50-native-settings.js` (`cards()`) now skips a card whose `hidden` attribute is set, which is how the page shows only the card that fits the mode.

## 4. What is switched off in the demo

`lmaPlugin()` returns null (as it does in a browser, where every caller already copes) for `LocalNotifications`, `HealthSync`, `CalendarSync`, `WidgetBridge`, `BackupFolder`, `DeviceBackup` (the last also at its direct use in `50-device-backup.js`) and `NativeShadow` (the native economy check writes its ring in the no-backup folder; its page side also stands aside while `LM_DEMO.on`, so the check is about the owner's real save only). In addition `exportData`, `importData`, `applyBackup` and `lmaRunBackup` refuse, the update pop-up does not open, and `lmgOn()` is false (no demo data to a cloud model, although the key is not in the demo's storage anyway). The Settings cards for reminders, widget, health, calendar and automatic backup say "Off in the demo". Reminders the owner already has scheduled are never listed, cancelled or replaced: `lmaSync`, which cancels and reschedules everything it finds, cannot run without the plugin. The two reset buttons only reset the demo, and say so.

Left on, deliberately: `AppLauncher` (launching apps, system bars), `NavHaptics`, `PageThumb`, `BiometricLock` (its lock choice is in the save, so it starts off), `VoiceEngine` (on-device recognition of what is said into the demo), `NativeAudio`, `FileSaver` (reports; a backup file cannot be reached because the export is refused), `Updater`/`LiveWeb` (they update the app, not the data).

Rule for later work: a new native plugin that writes outside the page's localStorage (SharedPreferences, files, a system service) must be added to `OFF` in `50-demo-mode.js`, or namespaced on the native side, and listed here.

## 5. Audit of every persisted key

| Key or store | Owner | Demo treatment |
| --- | --- | --- |
| `lifemax_rpg_prototype_v1` (the save), `_safety`, `_unreadable_*` | page (`04-runtime.js`) | namespaced; starts empty; the real one is never read |
| `lifemax_android_prefs_v1` | page (`33-android.js`) | namespaced; seeded with the owner's layout choices minus backup, apps, reminders |
| `lifeMaxTheme` | page | namespaced; seeded from the real one |
| `lma_sfxvol`, `lma_qfx`, `lma_qdeal`, `lma_qpack`, `lma_gwr3d`, `lma_g3dbg` | page | namespaced; seeded from the real ones |
| `lma_set_cat` (Settings category) | page | namespaced; not seeded |
| `lm_pet_seen` | page | namespaced; not seeded |
| `lifemax_thumbs_v1` (tab previews) | page | namespaced; not seeded |
| `lifemax_director_state_v1`, `lifemax_director_quarantine_*` | Life Director | namespaced; not seeded |
| `lifemax_calsync_v1` | page | namespaced; the sync is off |
| `lifemax_voice_enabled`, `_prefs_v1`, `_alias_v1`, `_macros_v1`, `_undo_v1`, `_timer_v1`, `_ratings_v1`, `_op_journal_v1`, `_fallback_logs_v1` | voice | namespaced; not seeded |
| `lifemax_gemini_key_v1`, `lifemax_gemini_on_v1`, `lifemax_gemini_ui` | cloud voice | namespaced; not seeded, so no key, so no call |
| `lifeMaxDevChat` | page | namespaced |
| `lifemax_dl_ran` (sessionStorage) | deep links | not namespaced: it is per app start and only records links already acted on |
| IndexedDB `lifemax_audio_v1`, `lifemax_usda_branded_v1`, `lma_trainer` | page | database names prefixed; deleted on Return |
| `lm_demo_active` | demo mode | the marker; the only real key the demo adds, removed on Return |
| Journal lock (`state.journalLock`) | save | in the (empty) demo save, so off; `BiometricLock` itself stores nothing |
| Cookies, Cache API, service workers, Capacitor Preferences | none used | none |
| Native `SharedPreferences`: `LiveWeb` (OTA state), `DeviceBackupChoice`, widget data (`LifeDialWidget`) | native | not reached from the demo: `DeviceBackup` and `WidgetBridge` are off; `LiveWeb` holds app-update state, not data |
| Android Auto Backup of the WebView data | Android | the demo's keys are inside the same WebView store; with device backup on, a backup taken mid-demo carries them (they are removed by Return) |

## 6. Tests

`lifemax_modular_v73/tests/demo-mode.test.js` drives the real page. The real storage and database names are read through a same-origin frame (the page's view does not cover it), before the demo, while it runs, after play, after a reset and after Return. It checks:

- every real key, byte for byte, and every real database name, are unchanged while the demo runs and after play, and the only additions are the marker and keys under the demo prefix;
- a wrapper installed before the page on `Storage.prototype.setItem/removeItem/clear` sees only demo-prefixed writes from a demo page (and no `clear`);
- the demo starts at onboarding with an empty save and the owner's look, without their backup folder, apps, Gemini key or reminders;
- a mock of every native plugin sees no call to the plugins listed in section 4 when the reminder, health, calendar, widget, backup, file-save and Android-backup entry points are all driven; a control run outside the demo shows the same driver does reach them;
- "Delete all local data" clears only the demo;
- a new page in the same browser profile (an app kill) is still the demo, with its progress, and Settings still offers Return;
- Return restores the real save and every real key byte for byte (the real start-up's own cat-seen time excepted), leaves no demo key, marker or database, and the real audio record is still there;
- a marker that is empty, garbage, the text `null` or `0` is the blank demo, never the real save, writes nothing real, and Return works from it; a demo with no real keys at all works; demo keys left by an interrupted exit are cleared by the next real start;
- the storage view behaves like `localStorage` and `clear()` cannot reach a real key.

Also run: `node tests/native-handover-sim.test.js 10` (the Settings command set is unchanged), `native-settings-fields.test.js`, `device-backup.test.js` and `backup-restore.test.js`. `native-settings.test.js` changed in two places for the new card and nothing else: the card list it compares the projection with leaves out a card the page hides, and the page now has 24 cards (it had 23).

## 7. Native side

An audit of `android-wrapper/native` (Java and Kotlin) for `SharedPreferences`, `filesDir`, `noBackupFilesDir`, DataStore and file output found: `LiveWeb` (OTA bookkeeping), `LifeDialWidget`/`WidgetBridgePlugin` (widget picture, written only through `WidgetBridge`), `DeviceBackupChoice`/`DeviceBackupAgent` (the device backup choice, written only through `DeviceBackup`) and `BackupFolderPlugin` (files in a folder the owner picked, only through `BackupFolder`). The native tab screens draw page projections and keep nothing of their own. The page switches the four writing plugins off, so nothing needed a native change, a capability string (`settings.demo.v1` was not needed) or a `NativeApi` bump.

## 8. Risks and limits

- A native plugin added later that persists data is not covered until it is added to `OFF` (section 4). The check is a review rule, not a test.
- The DEMO chip is not drawn on native screens; the amber status bar is the signal there, and it was not seen on a device. If it turns out to be hidden by an edge-to-edge native screen, a chip in the shell is the follow-up (a capability string, a `NativeApi` bump and new screenshots).
- The native Settings buttons are delegations, so starting or ending a demo from native Settings takes two taps (reveal, then the button). A bound command would make it one, at the price of a new command and a hand-over simulation extension.
- The demo copies look and layout preferences at the start only; changes the owner makes to the real preferences while a demo runs are not possible (the real keys are out of reach), and changes made in the demo are lost when it ends.
- `sessionStorage` is shared with the real session within one app run (only `lifemax_dl_ran`, deep links already handled).
- A write that fails because storage is full is reported by native Settings by key name; in the demo the key is the prefixed one, so that one message may read as a generic failure.
- Android Auto Backup, when the owner has turned device backup on, can capture the demo's keys while the demo runs; Return removes them.
