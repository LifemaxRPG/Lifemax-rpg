# Settings native slice inventory

Baseline: branch `track/docs` at `d56403c0410c73686ce8c5675d16cbe627e2ebd1` (2026-10-30, Native API 23). Read-only audit; no code was changed.

Reference conventions. Paths are relative to `lifemax_modular_v73/src/`. Runtime segments are cited by file name alone (they all live in `runtime/segments/`, for example `04-runtime.js:264`). Many segments are minified one-statement-per-line files, so one line number can cover a whole function. Native paths are relative to `android-wrapper/`. "Verified" means read in the source at this baseline. Anything not verified is marked **(unverified)**. "From code reading" means the behaviour follows from the source but nobody has seen it on a device.

Load order matters, because later segments rewrap earlier functions. The order is the `@@INCLUDE` list in `index.template.html:132`: `00, 43-economy, 01 … 23, 24 … 32, 33-android, 34-navigation, 35-android-upkeep, 36 … 42, 44 … 49, 50-pet-hd, 50-voice-gen4, 50-voice-gen5, 50-voice-fallback, 50-haptics, 50-quests-*, 50-sfx*, 50-gym3d*, 50-settings-tabs, 50-tabs-edit, 50-food, 50-smartdiet, 50-deeplink, 50-voice-gemini, 50-calsync, 50-native-cash, 50-native-overview, 50-native-tabkit, 50-native-nutrition, 50-native-character, 50-native-log, 51-boot-ok`.

---

## 1. Summary

**What the tab is for.** It is one long page of 23 cards, filtered by five category chips. It mixes six kinds of things that have very different risk:

1. Pure preferences: light mode, bottom-bar order, swipe and scrub behaviour, quest card effects, sounds, deal style, card art, guided-workout screen, haptics, HD cat, tab previews.
2. Opt-ins for the native screens: Native Overview (on by default), Native Nutrition, Native Character, Native Log, and tab timing.
3. Phone integrations that run in the background from any tab: app updates (APK and web over the air), automatic folder backup, reminders, Health Connect sync, calendar import, the home-screen widget, journal lock, and voice.
4. Game rules stored in the core save: character profile (name, build, weight, height, picture), decay (on, grace, rate), XP base per activity type, developer mode.
5. The rewards shop (a sheet shared with Overview).
6. Data management: export JSON, export report, import JSON, reset to a starter profile, delete everything.

**Page view.** `settings` = `<section id="view-settings" class="view">` (`views/settings.html:1-130`). One view id; no sub-views are registered with `navigate`. The five category chips are DOM-local state (`50-settings-tabs.js:2-33`). Overlays started from this page are appended to `<body>`: the rewards sheet `.hsheet.rsheet` (`44-rewards.js:97-103`), the dev chat `#devChat` (`13-runtime.js:184-191,209`), voice diagnostics `.hsheet.lmv-diag` (`45-voice.js:531-535`), the onboarding class picker in preview mode `#onboarding` (`12-runtime.js:172`), the tutorial `#tutorial` (started by `resetTips`, `13-runtime.js:83-91`), the update pop-up `.updpop` (`35-android-upkeep.js:78-98`), the web-update pill `.lmaweb-pill` (`35-android-upkeep.js:59-65`) and the tab-capture progress bar `.thumbwarm` (`42-scrubnav.js:59`).

**Entry points (all verified unless marked).**

| Entry | Where | Path into the tab |
|---|---|---|
| Top-bar gear button | `index.template.html:40` (`.topgear`) | `navigate('settings')` |
| Native screens' shared bars (Overview, Character, Food, Log) | the top bar is a DOM capture; tapping its gear arrives as `nav-action` and runs `NativeOverviewSnapshot.activate` (`50-native-overview.js:165-174`), which clicks the real `.topgear` because Settings is not a native view | `navigate('settings')`; web destination, so the native screen closes at once (CONTRACT A23 item 2) |
| Native Character top-bar gear | `native/src/main/java/com/lifemax/rpg/feature/character/presentation/components/CharacterChrome.kt:49-57` emits `navigate`/`settings`; `bridge/web/ComposeTabPlugin.kt:168-177` turns it into `{view:'settings'}`; the kit closes and calls `lmaNavTo` (`50-native-tabkit.js:133-136`) | `lmaNavTo('settings')` |
| Optional bottom-bar tab | `LMA_TAB_EXTRA=[{view:'settings',…}]` (`50-tabs-edit.js:3`), off by default; turning it on in Bottom bar inserts a real `.navbtn` (`50-tabs-edit.js:13-18`) that then also takes part in swipe (`34-navigation.js:95`) and scrubbing; native bars map `settings` to the gear icon (`CharacterChrome.kt:81`) | `navigate('settings')` |
| Overview "Activity today" tile while health sync is off | `39-health.js:145` | `navigate('settings')` |
| Health sheet "Set up health sync" | `39-health.js:170,178` | removes the sheet, `navigate('settings')` |
| Voice "open settings", "settings", "options", "preferences" | `46-voice-nlu.js:42,62-68` → `lmvGo` (`46-voice-nlu.js:121-127`) | `lmaNavTo('settings')` |
| Voice "voice diagnostics", "diagnostics" | `46-voice-nlu.js:43`, `lmvGo` line 123 | opens the diagnostics sheet without visiting Settings. "voice settings" is listed for diagnostics too, but the `settings` row comes first and the first match wins (`46-voice-nlu.js:62`), so "voice settings" opens Settings |
| Back history | `34-navigation.js:69-90` (`lmaBack`, stack of up to 30 views) | previous view |
| Android instrumentation test | `android-test/OverviewParityTest.kt:90` evaluates `navigate('settings');setNativeOverviewEnabled(false)` | test only |
| Deep links | none: `lifemax://` knows `schedule`, `cats` and `open?feature=schedule/cats/quests` only (`50-deeplink.js:15-24`) | none |
| Launcher shortcuts, widget, notifications | none: no settings shortcut in `res/xml`; the widget opens Life Director (`40-widget.js:30-32`); reminders carry no route (`33-android.js:105`) | none |

Settings handlers that are also reached from other tabs: the Journal header "Backup" button calls `exportData()` (`47-journal-coach.js:70`); the onboarding "Import a backup" button calls `importData(event,true)` (`index.template.html:17`); the Overview Rewards tile opens the same rewards sheet (`44-rewards.js:139-142`).

**Size.** Markup: 130 lines, 19.8 KB (`views/settings.html`). 23 cards, about 120 interactive controls (counted from the markup and the renderers; Bottom bar rows depend on the tab count). Fifteen code paths render into the page on every `navigate('settings')` (section 5.1). The main segments that exist for this page are `33-android.js` (188 lines, notifications, backup format, file saving), `35-android-upkeep.js` (167, updates and folder backups), `39-health.js` (201), `48-journal-lock.js` (82), `50-calsync.js` (75), `50-haptics.js` (75), `40-widget.js` (41), `50-settings-tabs.js` (33), `50-tabs-edit.js` (52). Game-rule handlers sit in `04-runtime.js:181-267`, `05-runtime.js:53-64,159` and `07-runtime.js:114-118`. CSS: `.settingsline` (`styles/01-base.css:233-237`), categories and bar editor (`styles/07-finishing.css:309-320`), sheets (`styles/07-finishing.css:45-115`), dev chat (`styles/02-layout.css:770-843`).

**Native risk: presentation low to medium, data high.** The cards are mostly flat rows of toggles, selects and status text, which suit Compose well. The danger is in what the buttons do:

1. **Restoring a backup silently turns automatic backup off and forgets the folder.** Export strips `backup.on/uri/folder` from the prefs it includes (`33-android.js:168`); import writes those prefs back over the live ones (`33-android.js:169`) and reloads (`04-runtime.js:264-265`). After any restore the owner has no automatic backup until they choose the folder again. Verified from code.
2. **Automatic backup rotation can delete every good backup.** Each run writes the current save and then deletes the oldest `life-max-auto-*.json` files beyond "Keep the newest" (3, 7, 14 or 30; `35-android-upkeep.js:133`, `native/BackupFolderPlugin.java:70-122`). After a bad import, a reset or a delete, the schedule keeps running (prefs are untouched by reset/delete) and replaces good backups with bad ones within `keep × days` days. Nothing verifies a backup before pruning (ADR 0004 is accepted but not implemented).
3. **"Delete all local data" removes the safety copy and clears only the core save.** It removes `STORE`, `STORE_safety` and `STORE_unreadable` after one `confirm()` (`04-runtime.js:267`). Life Director, Android prefs, voice data, dev chat, thumbnails and the three IndexedDB databases stay. There is no way back except an exported file.
4. **Backups miss a lot.** `backupJson()` (`04-runtime.js:259`) carries the core save, the XP rules and three extra keys (`LMA_EXTRA_KEYS`, `33-android.js:167`: Life Director, Android prefs, theme). It does not carry about 20 other localStorage keys or any IndexedDB data (section 6.2), although the card says "Download all activities, stats, quests and settings".
5. **Settings writes XP.** Turning health sync on, "Sync now", the rewards shop, developer mode boosts and the class switch all change the core save and XP (sections 4.13, 4.18, 4.20). Every `save()` from this page also runs `dhAutoVerify` (`50-quests-hand.js:153`), which can complete quests.
6. **Two settings have no switch.** `state.fx.sound` and `state.fx.reduce` are read in at least ten places, but the controls `fxSound`/`fxReduce` no longer exist (`renderFxSettings` returns early, `07-runtime.js:98`). The Quest sounds hint still says "Also needs Sound on in the main sound setting" (`views/settings.html:28`).
7. **Unsaved edits are wiped by background saves.** `renderAll()` writes the profile fields back from `state` (`04-runtime.js:182`) and rebuilds the XP-rule inputs (`04-runtime.js:181`) on every `save()`. Health sync saves every 30 minutes (`39-health.js:104,133`) and paychecks save when they clear (`36-money-pay.js:55-66`), so a half-typed name, weight or XP value can revert while the owner types. Verified from code.

---

## 2. States

| State | What decides it | Where |
|---|---|---|
| Browser (no Android shell) | `lmaNative()` false: Notifications, Updates, Backup, Tab previews, Widget, Calendar, Health show a one-line note; Journal lock toggle disabled; native toggles disabled | `33-android.js:12,140`, `35-android-upkeep.js:102,141`, `42-scrubnav.js:76`, `40-widget.js:22`, `50-calsync.js:62`, `39-health.js:115`, `48-journal-lock.js:56-57`, `50-native-tabkit.js:66-70` |
| Older APK without a plugin | toggle disabled and a hint: Native Overview "Install the latest Android update…" (`50-native-overview.js:64-65`); Native Nutrition "Requires an Android APK…" (`50-native-nutrition.js:40-44`); Native Character/Log just disabled (`50-native-tabkit.js:66-70`); tab timing disabled without `setTimingToast` (`50-native-overview.js:149`); haptics text "This build of the app cannot set the motor strength yet…" (`50-haptics.js:65`); thumbnails "The snapshot helper is not in this build" (`42-scrubnav.js:33`) | as listed |
| Onboarding not finished | `#onboarding.show` covers the app, so the gear is unreachable; Back is swallowed (`34-navigation.js:56`) | `13-runtime.js:113`, `11-runtime.js:47` |
| Category chip | `setCat` from `localStorage.lma_set_cat`, default `look`; cards whose `h3` text is not in `SET_CATS` would show in every category (none today) | `50-settings-tabs.js:2-10,19-25` |
| Category `look` (Look & feel) | Appearance, Bottom bar, Navigation, Tab previews, Cat graphics, Haptics, Quest effects, Guided workout | `50-settings-tabs.js:3` |
| Category `voice` | Voice (beta), Journal privacy | `50-settings-tabs.js:4` |
| Category `phone` | App updates, Home-screen widget, Health sync, Calendar import, Automatic backup, Notifications | `50-settings-tabs.js:5` |
| Category `game` | Character profile, Rewards, Decay system, XP rules | `50-settings-tabs.js:6` |
| Category `data` | Food database, Data management, Developer mode | `50-settings-tabs.js:7` |
| Updates card | `lmaUpd.state`: `idle`, `checking`, `latest`, `web`, `webdl`, `webready`, `available`, `downloading`, `permission`, `opened`, `error`; plus `autoErr` from a failed silent launch check and `info.stable===false` (temporary signing key warning) | `35-android-upkeep.js:3,26-40,99-116` |
| Backup card | no folder (controls disabled), on/off, `status` `ok`/`lost`/`error` with a note, `lmaBakBusy` ("Saving…") | `35-android-upkeep.js:139-154` |
| Notifications card | permission `granted`/`denied`/`prompt`/`prompt-with-rationale`/`unsupported`; exact alarm `denied`; master off disables every sub-control; sub-controls disabled when their parent is off | `33-android.js:137-165` |
| Health card | `H.sdk` `unavailable`/`update` (note), missing permissions (note with "Allow access"), off (type toggles disabled), busy ("Syncing…") | `39-health.js:113-129` |
| Calendar card | off (Check now disabled), on, `msg` note (no permission, Life Director not set up, read error) | `50-calsync.js:60-68` |
| Widget card | count on home screen, `canPin`, last push time, error message, busy ("Drawing…") | `40-widget.js:20-29` |
| Tab previews card | count captured of total, last error, capture-all running ("Working…") | `42-scrubnav.js:74-79` |
| Journal lock card | Android or browser; on or off; message after a change | `48-journal-lock.js:54-76` |
| Haptics card | haptics off (strength and try buttons disabled); strength `light`/`normal`/`strong`/`max`; motor probe `null`/`true`/`false` | `50-haptics.js:11,52-67,72` |
| Voice card | voice on/off; fallback log empty or not (Export/Clear disabled); cloud rows only when `localStorage.lifemax_gemini_ui==='1'` | `45-voice.js:583-590`, `50-voice-fallback.js:96-106`, `50-voice-gemini.js:80-95` |
| Food database card | "Checking…", count, "Not imported yet", import progress, failure text; Remove disabled at 0; Remove armed for 4 s after the first tap | `50-food.js:243-254`, `08-runtime.js:235-245` |
| Guided workout card | trainer pack loaded or not (Remove shown only when loaded) | `50-gym3d.js:234` |
| Developer mode | `state.devMode` shows `#devModeTools` | `04-runtime.js:207-220` |
| Decay inputs | from `getDecay()` (defaults on, grace 4, rate 3) | `05-runtime.js:43,63` |
| Overlay open (rewards, voice diagnostics, health) | `.hsheet` in `lmaTopOverlay`, Back closes it | `34-navigation.js:59` |
| Dev chat open | `#devChat.open`; **not** in `lmaTopOverlay`, so Back changes the tab underneath and leaves the chat covering the screen (from code reading) | `13-runtime.js:209`, `34-navigation.js:54-67`, `styles/02-layout.css:770-771,836-837` |
| Onboarding preview or tutorial open | Back swallowed; only their own buttons close them | `34-navigation.js:56` |
| Save failed | `save()` toasts "Could not save to this browser (storage full?)" and returns false; Settings handlers ignore the result and still toast success (for example "Character saved", `04-runtime.js:199`) | `04-runtime.js:13` |
| After reset or delete | `freshState()` has no `onboardingComplete`, so every native screen is blocked (`50-native-tabkit.js:56`, `50-native-overview.js:76`) until the app is reopened and onboarding is done again (from code reading) | `04-runtime.js:9,266-267` |
| After importing a save without `onboardingComplete` | same block, and onboarding never reappears because the save has activities (`11-runtime.js:47`), so native screens stay off for good (from code reading; not expected for the owner's save, **unverified**) | `04-runtime.js:264` |

---

## 3. Displayed facts

### 3.1 Page head and category bar

| Element | Shows | Computed by | Reads |
|---|---|---|---|
| `.pagehead` | "RULES & DATA", "Settings & data", "Data stays in this browser unless you export it." (stale for Android) | static | none |
| `#setCatBar` (created on first apply, after `.pagehead`) | five pill buttons, the active one `.on` with `aria-selected` | `setApplyCats` (`50-settings-tabs.js:11-26`) | `setCat`, `SET_CATS` |
| Card visibility | `.set-hide` on cards outside the category; the `.grid.two-col` wrapper hides when both its cards are hidden | `50-settings-tabs.js:19-24` | each card's `h3` text |

### 3.2 Appearance (`views/settings.html:3-11`)

| Element | Shows | Computed by | Reads |
|---|---|---|---|
| `#lightModeToggle` | light mode on | `initTheme` at boot (`04-runtime.js:11`, called `13-runtime.js:115`), `setLightMode` | `localStorage.lifeMaxTheme` |
| `#nativeNutritionToggle`, `#nativeNutritionHint` | on, disabled when no plugin; hint replaced at runtime | `syncSettings` (`50-native-nutrition.js:32-45`) | `lmaLoad().nativeNutrition`, plugin `NativeNutrition` |
| `#tabTimingToggle`, `#tabTimingHint` | on; hint becomes "Last: …" from the plugin | `syncTiming` (`50-native-overview.js:149-150`) | `lmaLoad().tabTiming`, `NativeOverview.timing()` |
| `#nativeOverviewToggle`, `#nativeOverviewHint` | on (default true), disabled when no plugin | `syncSetting` (`50-native-overview.js:61-66`) | `lmaLoad().nativeOverview!==false` |
| `#nativeCharacterToggle` | on, disabled without plugin | kit `sync` (`50-native-tabkit.js:65-71`) | `lmaLoad().nativeCharacter===true` |
| `#nativeLogToggle` | on, disabled without plugin | kit `sync` | `lmaLoad().nativeLog===true` |

### 3.3 Bottom bar (`views/settings.html:12-17`, body `#tabsEditBody`)

| Element | Shows | Computed by | Reads |
|---|---|---|---|
| One row per bar button | icon (copied from the button), label, earlier/later arrows (disabled at the ends), visible checkbox; dimmed when hidden | `lmaTabsRender` (`50-tabs-edit.js:27-36`) | live `.sidebar .navbtn[data-view]` order and `.tab-off` |
| Extra rows | "Settings" with gear, no arrows, unchecked, while not added | same, `50-tabs-edit.js:34` | `lmaTabsCfg().extra` |

### 3.4 Navigation (`views/settings.html:18-24`)

| Element | Shows | Computed by | Reads |
|---|---|---|---|
| `#lmaSwipeToggle`, `#lmaSwipeAnimToggle`, `#lmaScrubSpeed` | swipe on (default), animation on (default), speed (default `fast`) | boot only, `setTimeout(…,500)` (`34-navigation.js:197`) | `lmaLoad().swipe`, `.swipeAnim`, `.scrubSpeed`, `LMA_SCRUB_GAINS` (`42-scrubnav.js:8`) |
| Static note | Back closes a popup, then returns to the previous tab | static | none |

### 3.5 Quest effects (`views/settings.html:25-31`)

| Element | Shows | Computed by | Reads |
|---|---|---|---|
| `#qfxToggle` | card effects on (default) | boot `50-quests-fx.js:134` | `localStorage.lma_qfx!=='off'` (`50-quests-fx.js:11`) |
| `#qsfxVol` | Off / Low 0.35 / Medium 0.7 (default) / High 1 | boot `50-sfx.js:31` (rounds the stored value into a bucket) | `localStorage.lma_sfxvol` (`50-sfx.js:7`) |
| `#qdealSel` | 3D table (default) / Classic cinematic / Quick / Instant | boot `50-quests-cine.js:160` | `localStorage.lma_qdeal` (`50-quests-cine.js:16`) |
| `#qpackSel` | "Automatic (by season)" plus one option per pack (Classic, Halloween at this baseline) | boot `50-quests-art.js:21` | `QART_PACKS` (generated, `50-quests-art.js:2`), `localStorage.lma_qpack` |

### 3.6 Guided workout (`views/settings.html:32-37`)

| Element | Shows | Computed by | Reads |
|---|---|---|---|
| `#gwr3dSel`, `#g3DbgSel` | 3D gym (default) or Classic; diagnostics Off (default) or On | boot `50-gym3d.js:514` | `localStorage.lma_gwr3d`, `lma_g3dbg` (`50-gym3d.js:307,362`) |
| `#g3PackStatus`, `#g3PackRemove` | "Loaded: <name>" or "None loaded. The built-in trainer is used."; Remove only when loaded | `g3PackStatus` (`50-gym3d.js:234`), boot and after load/clear | IndexedDB `lma_trainer`, store `pack`, key `current` (`50-gym3d.js:216-222`) |

### 3.7 Haptics (`#hapBody`, built with DOM calls, `50-haptics.js:52-67`)

| Element | Shows | Reads |
|---|---|---|
| "Haptic feedback" checkbox | on (default) | `fxOpt().haptics` (`07-runtime.js:43`) |
| Strength buttons Light / Normal / Strong / Max | current one primary and `aria-pressed`; disabled when off | `fxOpt().hapticLevel`, `HAP_LEVELS` (`50-haptics.js:5-10,12`) |
| "Try:" buttons Tap / Double tap / XP gain / Level up | disabled when off | none |
| Capability note | three texts depending on the motor probe | `hapNative`, `hapNav()` (`50-haptics.js:65`) |

### 3.8 App updates (`#lmaUpdBody`, `lmaRenderUpdates`, `35-android-upkeep.js:99-116`)

| Element | Shows | Reads |
|---|---|---|
| Title | "Installed build N" plus " · app updated over the air to build M" | `lmaUpd.info.current`, `lmaUpd.live.activeBuild` |
| Status line | one text per state (section 2), or "Last checked <ago>." plus the launch-check error | `lmaUpd.state`, `pct`, `err`, `autoErr`, `lmaLoad().updChecked`, `lmaAgo` (`35-android-upkeep.js:5`) |
| Release line | "Latest release: X · Android API N / requires M" | `info.latest`, `live.nativeApi`/`info.nativeApi`, `info.latestNativeApi`, `info.webMinApi` |
| Button | Update / Restart / Download / Check now (none while checking or downloading) | `lmaUpd.state` |
| Signing note | "This build was signed with a temporary key…" | `info.stable===false` and `available` |

### 3.9 Voice (beta) (`#lmvSettingsBody`)

| Element | Shows | Computed by | Reads |
|---|---|---|---|
| "Voice scheduling" row with Open | static text | `lmvSettingsRender` (`45-voice.js:583-590`) | none |
| "Turn voice on" checkbox | on unless the flag is `'false'` | same | `localStorage.lifemax_voice_enabled` (`45-voice.js:5,9`) |
| "Export Voice Fallback Log" row | count of flagged answers and of thumbs-up; Export and Clear disabled at zero | wrapper `50-voice-fallback.js:96-106` | `lifemax_voice_fallback_logs_v1` (max 200), `lifemax_voice_ratings_v1` (max 100) (`50-voice-fallback.js:4`) |
| Cloud understanding rows (hidden by default) | an on/off checkbox and a password field holding an API key | wrapper `50-voice-gemini.js:80-95` | `lifemax_gemini_ui`, `lifemax_gemini_on_v1`, `lifemax_gemini_key_v1` (`50-voice-gemini.js:5`) |

### 3.10 Cat graphics (`#hdSettingsBody`, `50-pet-hd.js:199-203`)

"HD walking" checkbox, on unless `state.hdCat===false` (`50-pet-hd.js:7`).

### 3.11 Journal privacy (`#jlSettingsBody`, `48-journal-lock.js:54-76`)

"Lock the Journal" checkbox (`state.journalLock.on`), disabled in a browser; a message line `#jlMsg` ("The Journal is locked.", "The lock is off.", or why it could not turn on).

### 3.12 Tab previews (`#lmaThumbBody`, `42-scrubnav.js:74-79`)

"N of T tabs captured" (`Object.keys(lmaThumbs).length` of `lmaScrub.items.length||14`), the last problem or a description, and "Capture all" (disabled while running).

### 3.13 Home-screen widget (`#lmaWidgetBody`, `40-widget.js:20-29`)

Error note (`lmaWidgetMsg`), "N on your home screen" or "Not on your home screen yet" (`WidgetBridge.status().count`), "Picture updated <ago>" (`lmaWidgetAt`), "Add widget" or "How to add" (`status().canPin`), "Refresh" ("Drawing…" while busy).

### 3.14 Calendar import (`#lmaCalBody`, `50-calsync.js:60-68`)

"Import from my calendar" checkbox (`lifemax_calsync_v1.on`), a note with `msg`, "Last check <ago> · N events tracked, next 14 days" or "Not yet", "Check now" (disabled when off).

### 3.15 Health sync (`#lmaHealthBody`, `39-health.js:113-129`)

| Element | Shows | Reads |
|---|---|---|
| Notes | Health Connect unavailable or needs an update; missing permission list with "Allow access" | `state.health.sdk`, `.msg`, `.granted` |
| "Sync health data" | on | `state.health.on` |
| Workouts / Daily steps / Sleep / Weight | per-type on, disabled when the master is off, with fixed rule text | `state.health.ex/steps/sleep/wt` |
| Daily steps goal | 5,000 … 15,000 (default 8,000) | `state.health.goal` |
| Last sync | ago, source app, message, "Today so far: N steps" | `.last`, `.src`, `.msg`, `.today` |
| Static help | how to let Samsung Health write to Health Connect | static |

### 3.16 Food database (`#usdaSettingsBody`, `50-food.js:243-247`)

"Branded foods (USDA)" text, `#usdaImportStatus` ("USDA database ready · N foods stored on this device" or "Not imported yet · optional"; progress "Importing USDA foods… N processed"), "Import file", "Remove". Count from `usdaCount()` (`08-runtime.js:235`), IndexedDB `lifemax_usda_branded_v1`.

### 3.17 Automatic backup (`#lmaBakBody`, `35-android-upkeep.js:139-154`)

| Element | Shows | Reads |
|---|---|---|
| Notes | "Android no longer lets LifeMax write to that folder. Choose it again." or "The last backup failed: <err>" | `backup.status`, `.err` |
| "Automatic backup" | on, disabled until a folder exists | `backup.on`, `.uri` |
| Folder | folder name or "Nothing chosen yet."; button Choose folder / Change | `backup.folder` |
| How often | Daily / Every 3 days / Weekly | `backup.days` |
| Keep the newest | 3 / 7 / 14 / 30 files | `backup.keep` |
| Last backup | ago and file name, or "None yet"; "Back up now" ("Saving…") | `backup.last`, `.lastName`, `lmaBakBusy` |

There is no "stale backup" warning (ADR 0004 asks for one).

### 3.18 Notifications (`#lmaNotifBody`, `33-android.js:137-165`)

Notes (blocked, needs permission with "Allow notifications", exact timing with "Allow exact timing"), then rows: Reminders (master), Planned sessions, Reminder lead time (At start, 5, 10, 15, 30, 60 min), Workout days, Workout time, Evening nudge (8 PM), Daily check-in, Check-in time, Quiet hours, Quiet from / to, "Send a test reminder", and a footnote. All values from `lmaLoad().notif` with defaults in `LMA_DEFAULTS.notif` (`33-android.js:5-6`). Permission from `LocalNotifications.checkPermissions()`, exact alarm from `checkExactNotificationSetting()` (both async, each render).

### 3.19 Character profile (`views/settings.html:92-112`)

| Element | Shows | Computed by | Reads |
|---|---|---|---|
| `#charAvatar` | the picture as a background image, or the first letter of the name | `renderAvatar` (`07-runtime.js:114`), via `renderExtras` on every `save()` | `state.avatar` (JPEG data URL), `state.name` |
| `#characterName`, `#characterBuild`, `#characterWeight`, `#characterHeightFt`, `#characterHeightIn` | current values | `renderAll` (`04-runtime.js:182`), no focus guard | `state.name`, `.build`, `.weight`, `.heightFt`, `.heightIn` |

Build options are fixed: The Life Architect, Athlete, Entrepreneur, Scholar, Explorer, Balanced Build, Custom Build. A build string outside this list (for example a class that is not ready, `05-runtime.js:200-215`) would leave the select empty, and "Save character" would then store an empty build (from code reading).

### 3.20 Rewards card (`views/settings.html:113`)

Static text and "Open rewards". The sheet's facts are in section 4.18.

### 3.21 Decay system (`views/settings.html:114`)

`#decayOn`, `#decayGrace` (1-14), `#decayRate` (1-10), filled by `renderDecay` (`05-runtime.js:63`) on every render unless `#decayGrace` has focus. The Character page's decay watch is drawn by the same function; Settings only shows the inputs.

### 3.22 Developer mode (`views/settings.html:115-127`)

`#devModeOn` (focus-guarded) and `#devModeTools` visibility from `renderDevMode` (`04-runtime.js:214-220`); `#devChatBtn` label "Open Dev chat (N)" from `dcBtn` (`13-runtime.js:183`), N = all stored and seeded notes; `#devClassSel` lists only Athlete and Scholar (Entrepreneur is playable but missing).

### 3.23 XP rules (`#xpRules`, `renderRules`, `04-runtime.js:181`)

One row per rule in `rules` (nine by default: reading, business, strength, running, language, recovery, practical, task, custom; `00-runtime.js:12-22`): icon, name, "<skill> · base XP", number input `#rule-<id>` (1-200). Rebuilt on every `save()`.

### 3.24 Data management (`views/settings.html:129`)

Five rows with static text and buttons; the privacy note says data is in "this browser" and is "not a secure account system". There are no user accounts anywhere in the app; "Account settings" belongs to the Money tab (`views/money.html:6`).

### 3.25 Transient feedback

Toasts from almost every action (`toast`, `04-runtime.js:48`, 2.2 s). Haptic "double" when a strength is chosen. One sound when the quest volume changes (`50-sfx.js:30`). No celebrations or animations are started from Settings directly, but `save()` can start badge toasts (`05-runtime.js:29`) and quest auto-verification.

---

## 4. Actions

Columns: handler (file:line), reads, writes, side effects. "Save" means `save()` (`04-runtime.js:13`), which rebuilds XP, migrates the economy if needed, syncs Sparks, checks badges and level-ups, writes `lifemax_rpg_prototype_v1` and calls `renderAll()`; through its wrappers it also schedules a reminder sync (`33-android.js:182`), runs quest auto-verification (`50-quests-hand.js:153`) and refreshes native Food (`50-native-nutrition.js:621-628`).

### 4.1 Category bar

| Control | Handler | Writes | Side effects |
|---|---|---|---|
| Chip tap | `setPickCat(id)` (`50-settings-tabs.js:27`) | `localStorage.lma_set_cat` | re-filters cards, scrolls the bar into view |

### 4.2 Appearance

| Control | Handler | Reads | Writes | Side effects |
|---|---|---|---|---|
| Light mode | `setLightMode(checked)` (`04-runtime.js:10`), wrapped by `33-android.js:184` | none | `localStorage.lifeMaxTheme` | toggles `html.light-theme`, toast, system bar colour after 50 ms (`33-android.js:111-120`); native screens pick up new tokens on their next snapshot (`50-native-tabkit.js:21-27,58-60`) |
| Native Nutrition | `setNativeNutritionEnabled` (`50-native-nutrition.js:47-58`) | plugin | `lmaLoad().nativeNutrition`, prefs saved | closes the native Food screen when turned off |
| Show tab timing | `setTabTimingToast` (`50-native-overview.js:151`) | plugin | `lmaLoad().tabTiming` | `NativeOverview.setTimingToast({on})` |
| Native Overview | `setNativeOverviewEnabled` (`50-native-overview.js:67-74`) | plugin | `lmaLoad().nativeOverview` | closes the native Overview when off; opens it when on and the view is dashboard |
| Native Character / Native Log | kit `enable` (`50-native-tabkit.js:214-219`), globals `setNativeCharacterEnabled` (`50-native-character.js:107`), `setNativeLogEnabled` (`50-native-log.js:126`) | plugin | `lmaLoad().nativeCharacter` / `.nativeLog` | closes the screen when off |

### 4.3 Bottom bar

| Control | Handler | Writes | Side effects |
|---|---|---|---|
| ◀ / ▶ | `lmaTabMove(v,d)` (`50-tabs-edit.js:37-40`) | `lmaLoad().tabs.order` | moves the real `.navbtn`, so swipe, scrub and the captured native bars follow |
| Show checkbox | `lmaTabShow(v,on)` (`50-tabs-edit.js:41-50`) | `tabs.hidden` or `tabs.extra` (+ order) | "Keep at least two tabs in the bar" toast and re-render when it would leave fewer than two |
| Reset to default | `lmaTabsReset()` (`50-tabs-edit.js:51`) | `tabs=null` | removes extra buttons, restores template order, toast; no confirmation |

### 4.4 Navigation

| Control | Handler | Writes | Side effects |
|---|---|---|---|
| Swipe to switch tabs | `lmaSetSwipe` (`34-navigation.js:94`) | `lmaLoad().swipe` | toast |
| Swipe animation | `lmaSetSwipeAnim` (`34-navigation.js:119`) | `lmaLoad().swipeAnim` | toast |
| Bar scrubbing speed | `lmaSetScrubSpeed` (`42-scrubnav.js:10`) | `lmaLoad().scrubSpeed` | toast |

### 4.5 Quest effects

| Control | Handler | Writes | Side effects |
|---|---|---|---|
| Card effects | `qfxSet` (`50-quests-fx.js:133`) | `lma_qfx` | hides or shows the particle canvas, toast, re-renders the hand |
| Quest and dice sounds | `qsfxSetVol` (`50-sfx.js:30`) | `lma_sfxvol` | toast, plays `card-place` (silent if `fx.sound` is false) |
| Card deal | `dhSetDealMode` (`50-quests-cine.js:17`) | `lma_qdeal` | toast |
| Card art | `qartSetPack` (`50-quests-art.js:9`) | `lma_qpack` | toast, clears the hand signature and re-renders |

### 4.6 Guided workout

| Control | Handler | Writes | Side effects |
|---|---|---|---|
| Workout screen | `g3SetPref(value==='on')` (`50-gym3d.js:309`) | `lma_gwr3d` | toast. The gym's own "classic" button also writes it (`50-gym3d.js:493`), and the select is only synced at boot, so it can show the wrong value until restart |
| 3D diagnostics | `g3SetDbg` (`50-gym3d.js:363`) | `lma_g3dbg` | toast |
| Load trainer pack | hidden `#g3PackFile` → `g3PackLoadFile(file)` (`50-gym3d.js:224-231`) | IndexedDB `lma_trainer/pack/current` | Android file picker; rejects files over 40 MB or failing `g3PackValid`; toast |
| Remove | `g3PackClear()` (`50-gym3d.js:233`) | deletes the pack | toast; no confirmation |

### 4.7 Haptics

| Control | Handler | Writes | Side effects |
|---|---|---|---|
| Haptic feedback | `hapSet({haptics})` (`50-haptics.js:51`) | `state.fx`, save | re-render |
| Strength | `hapSet({hapticLevel})` + `hapPlay('double')` | `state.fx`, save | buzz at the new strength |
| Try buttons | `hapPlay(name)` (`50-haptics.js:47-50`) | none | `NavHaptics.pattern` or `navigator.vibrate` |

### 4.8 App updates (see also 4.21 for the pop-up and pill)

| Control | Handler | Reads | Writes | Side effects |
|---|---|---|---|---|
| Check now | `lmaCheckUpdate(false)` (`35-android-upkeep.js:26-40`) | `Updater.check()`, `LiveWeb.status()` | `lmaLoad().updChecked` | plan via `lmaUpdPlan` (`35-android-upkeep.js:8-25`); a web build is downloaded straight away unless `lmaLoad().webAuto===false` (no control sets `webAuto`); an APK opens the update pop-up |
| Download | `lmaWebUpdate()` (`35-android-upkeep.js:42-53`) | `LiveWeb.fetch({url,sha256,build})` | LiveWeb stage (native SharedPreferences) | progress events re-render; pill on success |
| Restart | `lmaWebRestart()` (`35-android-upkeep.js:54-57`) | none | none | reloads the app into the staged build |
| Update | `lmaInstallUpdate()` (`35-android-upkeep.js:66-75`) | `info.apk` | none | downloads the APK, opens Android's installer or the "Install unknown apps" screen |

### 4.9 Voice

| Control | Handler | Writes | Side effects / opens |
|---|---|---|---|
| Open | `lmvDiagOpen` (`45-voice.js:531-535`) | none | voice diagnostics sheet (4.22) |
| Turn voice on | inline (`45-voice.js:588`) | `lifemax_voice_enabled` | `lmvApplyFlag` shows or hides the floating mic (`45-voice.js:578-581`) |
| Export | `VoiceFallbackLogger.exportFile()` (`50-voice-fallback.js:38-42`) | none | `lmaDownload` text file, Android save picker |
| Clear | `VoiceFallbackLogger.clearLogs()` (`50-voice-fallback.js:15`) | removes both log keys | re-render; no confirmation |
| Cloud on/off, key Save (hidden) | inline (`50-voice-gemini.js:89,93`) | `lifemax_gemini_on_v1`, `lifemax_gemini_key_v1` (plain text) | re-render |

### 4.10 Cat graphics, Journal privacy, Tab previews

| Control | Handler | Writes | Side effects |
|---|---|---|---|
| HD walking | listener (`50-pet-hd.js:202`) | `state.hdCat`, save | clears the HD frame cache |
| Lock the Journal (on) | listener (`48-journal-lock.js:59-68`) | `state.journalLock={on:true}`, save; deletes `lmaThumbs.history` and `.library` | `BiometricLock.status()` then a biometric prompt; message |
| Lock the Journal (off) | same (`48-journal-lock.js:69-73`) | `state.journalLock={on:false}`, save | biometric prompt |
| Capture all | `lmaThumbWarm(true)` (`42-scrubnav.js:52-73`), wrapped by `50-native-overview.js:159` | `lifemax_thumbs_v1` | **navigates through every bar tab** every 650 ms with `lmaNavTo(v,{fast:true,skip:true})`, takes a `PageThumb.snap`, then returns home; a touch stops it. Native Overview is blocked while it runs (`50-native-overview.js:76`), but Native Character, Food and Log are not (`50-native-tabkit.js:56`, `50-native-nutrition.js:346`), so those screens would open and close in turn (from code reading, **unverified on device**) |

### 4.11 Home-screen widget and calendar

| Control | Handler | Writes | Side effects |
|---|---|---|---|
| Add widget / How to add | `lmaWidgetPin()` (`40-widget.js:14-19`) | none | pushes a fresh picture, then Android's pin dialog or a how-to toast |
| Refresh | `lmaWidgetPush(true)` (`40-widget.js:3-13`) | none | `LifeDirector.widgetSnapshot(720)` → `WidgetBridge.update` |
| Import from my calendar | `lmcSet('on',v)` (`50-calsync.js:50-59`) | `lifemax_calsync_v1` | calendar permission prompt, then `lmcSync(true)`, which adds and removes Life Director commitments through `LifeDirector.voice.add/remove` (`50-calsync.js:20-49`) and toasts the added titles. Turning it off leaves imported blocks in place |
| Check now | `lmcSync(true)` | same | same |

### 4.12 Food database

| Control | Handler | Writes | Side effects |
|---|---|---|---|
| Import file | hidden `#usdaImportFile` → `importUsdaFoods(event).then(fv2UsdaRender)` (`08-runtime.js:237-245`) | IndexedDB `lifemax_usda_branded_v1` (batches of 400) | streams a gzip through `DecompressionStream`; progress every 5,000 foods; toast; can run for minutes on a large file **(duration unverified)** |
| Remove (tap twice within 4 s) | `fv2UsdaClear(btn)` (`50-food.js:249-254`) | `indexedDB.deleteDatabase` | clears the search cache, toast |

### 4.13 Health sync

| Control | Handler | Writes | Side effects |
|---|---|---|---|
| Sync health data on | `lmaHealthSet('on',true)` (`39-health.js:112`) | `state.health`, save | sets `since` to two days ago, asks Health Connect permission when a type is missing (`lmaHealthAsk`, `39-health.js:106-111`), otherwise syncs at once |
| Type toggles, steps goal | `lmaHealthSet(k,v)` | `state.health[k]`, save | sync (no-op while off) |
| Allow access | `lmaHealthAsk()` | `state.health.granted`, save | Health Connect permission screen, then sync |
| Sync now | `lmaHealthSync(true)` (`39-health.js:30-105`) | **`state.activities` (new `src:'health'` activities), `state.attrXP`, `state.skillXP`, `state.weight`, `state.health.seen/snap/wtHist/today/last/msg`**, save in `finally` | XP through `ecoAward`, `ecoStarXP` and `rules` (`39-health.js:16-29`), toast with the count and XP, refreshes the Overview tile and the health sheet |

### 4.14 Automatic backup

| Control | Handler | Writes | Side effects |
|---|---|---|---|
| Choose folder / Change | `lmaPickBackupFolder()` (`35-android-upkeep.js:119-126`) | `backup.uri/folder/on=true/status/err` | Android folder picker; then an immediate backup |
| Automatic backup | `lmaSetBackup('on',v)` (`35-android-upkeep.js:138`) | `backup.on` | first backup if none was made yet |
| How often, Keep the newest | `lmaSetBackup(k,+v)` | `backup.days` / `.keep` | none until the next run, which prunes to the new `keep` |
| Back up now | `lmaRunBackup(true)` (`35-android-upkeep.js:127-137`) | `backup.last/status/err/lastName` | `BackupFolder.writeBackup` with `backupJson()`, prunes old files, toast |

### 4.15 Notifications

| Control | Handler | Writes | Side effects |
|---|---|---|---|
| Every toggle, select and time | `lmaSet(key,value)` (`33-android.js:122-125`) | `lmaLoad().notif[key]` | re-render; master on asks permission; then `lmaSync(true)` cancels every pending reminder and schedules the next three days (`33-android.js:91-108`) |
| Allow notifications | `lmaPermission(true)` | none | Android permission dialog, re-render, resync |
| Allow exact timing | `lmaExactTiming()` (`33-android.js:133-136`) | none | opens Android's alarm access screen |
| Send a test reminder | `lmaTestNotification()` (`33-android.js:126-132`) | none | schedules id 1 in 5 s. Any later `lmaSync` cancels all pending reminders when the count differs (`33-android.js:98-100`), and every `navigate` schedules one in 4 s (`33-android.js:183`), so leaving Settings within about a second can cancel the test (from code reading) |

### 4.16 Character profile

| Control | Handler | Reads | Writes | Side effects |
|---|---|---|---|---|
| Picture tap, Upload photo | `#avatarFile` click → `avatarPicked(e)` (`07-runtime.js:116-117`) | the file | `state.avatar` (256 px JPEG at 0.85), save | Android picker; toast; the picture also shows in the brand logo, hero and Character page |
| Remove | `removeAvatar()` (`07-runtime.js:118`) | none | deletes `state.avatar`, save | toast; no confirmation |
| Save character | `saveCharacter()` (`04-runtime.js:199`) | the five inputs (DOM) | `state.name` (default "The Life Architect"), `.build`, `.weight` (null when 0), `.heightFt`, `.heightIn`, save | toast even if the save failed. Health sync also writes `state.weight` (`39-health.js:96`) |

### 4.17 Decay, tips, XP rules

| Control | Handler | Writes | Side effects |
|---|---|---|---|
| Save decay settings | `saveDecay()` (`05-runtime.js:64`) reads the three inputs (DOM) | `state.decay={on,grace (1-14, default 4),rate (1-10, default 3)}`, save | toast; decay is applied on the next resume (`11-runtime.js:41`) |
| Show tips again | `resetTips()` (`13-runtime.js:83-91`) | `state.tipsSeen={}`, `state.tutorialDone=false`, **direct `localStorage.setItem(STORE,…)`** (bypasses `save()` and its hooks) | removes tip boxes, toast, starts the tutorial overlay after 300 ms |
| Save XP rules | `saveRules()` (`04-runtime.js:257`) reads each `#rule-<id>` | `rules[id].base` (clamped 1-200; an empty field becomes 1), `state.customRules` via save | toast; affects future logs, health imports and the native Log preview |
| Reset defaults | `resetRules()` (`04-runtime.js:258`) | `rules=DEFAULT_RULES`, save | toast; no confirmation |

### 4.18 Rewards (button `ecoShopOpen`, `44-rewards.js:97-103`) and the rewards sheet

The sheet `.hsheet.rsheet` is rendered by `ecoShopRender` (`44-rewards.js:104-131`) and keeps its scroll position across re-renders. Fields: Sparks balance; streak days, shields (0-2) and rest tokens; Rest day token (once a week, price `ECO_PRICE.rest` 250); Streak shield refill (once a month, 250); four perks (equip one, 600 each, `44-rewards.js:5`); five accent colours (300 each, `44-rewards.js:4`); cat toys (150-450, only those in `PET_TOYS`); ascension per attribute (at level 20, up to three stars).

| Control | Handler | Writes | Side effects |
|---|---|---|---|
| ‹ Back | inline | none | removes the sheet (Back button also closes it) |
| Buy / Equip / Use / Turn off | `ecoBuy(kind,id)` (`44-rewards.js:77-96`) | `state.sparks`, `perksOwned`, `perk`, `themesOwned`, `theme`, `toysBought`, `restTokens`, `restBoughtAt`, `streakShields`, `shieldBoughtAt`, save | accent colour through `ecoApplyTheme` (`44-rewards.js:68-75`), toasts, Overview tile refresh |
| Ascend | `ecoAscend(id)` (`44-rewards.js:18-26`) | `state.ascended[id]`, `state.attrStars[id]`, save | `confirm()` dialog, toast |

### 4.19 Developer mode

| Control | Handler | Writes | Side effects / opens |
|---|---|---|---|
| Enable developer mode | `toggleDevMode(on)` (`04-runtime.js:207-213`) | `state.devMode`, **direct localStorage write** | shows tools; adds "Dev: +1/+2 levels" to attribute sheets (`04-runtime.js:223-252`) and cat test buttons (`17-runtime.js:264`) |
| Open Dev chat | `openDevChat()` (`13-runtime.js:209`) | none | the dev chat overlay (4.23) |
| Reset today's luck roll | `luckResetToday()` (`18-runtime.js:105`) | `state.luck`, `state.luckLog[today]`, save | toast |
| Switch (class) | `devSwitchClass()` (`14-runtime.js:124-125`) → `switchClassTo` (`14-runtime.js:121`) | `state.classId`, `state.build`, **`state.plan=null`** (ends the workout plan), class quests via `applyClassContent`, save | re-renders Meals, Recipes, Quests, Plan; toast; no confirmation |
| ▶ Preview start screen | `previewOnboarding()` (`12-runtime.js:172`) | none until confirmed | onboarding preview (4.24) |

### 4.20 Data management

| Control | Handler | Reads | Writes | Side effects |
|---|---|---|---|---|
| Export JSON | `exportData()` (`04-runtime.js:260`) | `backupJson()` (`04-runtime.js:259`) | none | `lmaDownload` (`33-android.js:171-179`): `FileSaver.saveFile` (Android save-as picker) or a browser download; toast "Backup saved" / "Save cancelled" |
| Export report Word / Text | `exportReport('doc'|'txt')` (`05-runtime.js:159`) via `reportDoc`/`reportText` (`05-runtime.js:88-90`) | whole save | none | the "Word" file is HTML saved as `.doc` with `application/msword` |
| Import JSON | `#importFile` → `importData(e)` (`04-runtime.js:265`) → `applyBackup` (`04-runtime.js:264`) | the file | `STORE_safety` (`keepSafetyCopy`, `04-runtime.js:262`), whole `state`, `rules`, save, then `lmaRestoreExtras` overwrites Life Director, Android prefs and theme | rejects a file without `activities` array and `attrXP` object; `confirm()` when the current save has activities; aborts if the safety copy cannot be written; `alert()` on failure; reloads after 500 ms when extras were restored. Section 6.3 lists what this does to prefs |
| Reset demo data | `resetDemo()` (`04-runtime.js:266`) | none | `STORE_safety`, `state=freshState()`, `rules=DEFAULT_RULES`, save | `confirm()`; aborts without a safety copy; no reload (onboarding returns only after a restart) |
| Delete all local data | `deleteAll()` (`04-runtime.js:267`) | none | removes `STORE`, `STORE_safety`, `STORE_unreadable`, then saves a fresh state | one `confirm()`; no safety copy; other stores untouched |

### 4.21 Update pop-up and web-update pill (opened by the update logic, also from Settings)

| Surface | Fields | Actions |
|---|---|---|
| `.updpop` (`35-android-upkeep.js:78-98`), deferred 15 s while onboarding or the tutorial shows | title "Update available"; body per state (ready with build numbers, downloading with a bar, installer open, needs "Install unknown apps", error) | Update now / Try again (`lmaInstallUpdate`), Later (hides until the next launch, `lmaUpdLater`), OK (close); Back closes it (`34-navigation.js:58`) |
| `.lmaweb-pill` (`35-android-upkeep.js:59-65`), removes itself after 20 s | "Update ready" | Restart (`lmaWebRestart`), ✕ (dismiss for this session) |

### 4.22 Voice diagnostics sheet (`.hsheet.lmv-diag`, `45-voice.js:531-576`)

Header with ‹ Back and on/off badge. Cards: "Type a command" (text input, Send, three example chips, current frame state); "Speech engine" (provider, status, detail); "Last command timings" (six measurements); "Operation journal" (last five entries of `lifemax_voice_op_journal_v1`); "Capabilities" (intent id and status); "Settings" ("Voice enabled", "Always preview before saving" → `lifemax_voice_prefs_v1`); "Log" (last 12 lines). Send runs the full voice pipeline (`lmvHandle`), which can schedule into Life Director after confirmation.

### 4.23 Dev chat overlay (`#devChat`, `13-runtime.js:184-210`)

Fields: room buttons Dev chat / Party Chat, Copy thread, Save .txt, Paste notes (textarea + "Add notes"), Clear room (tap twice within 2.5 s), the thread (author, time, text, per-message delete tapped twice), "Posting as" author chips, compose textarea and Post. Storage `lifeMaxDevChat` `{m:[{id,h,from,text,t,room,sid}], seen:[]}` (`13-runtime.js:118-121`), seeded at load from notes embedded in nine segments (`devChatSeed` calls in `13, 14, 19, 20, 23, 24, 25, 26, 29-pet-pair`). Close button only; Back does not close it (section 2). "Save .txt" uses an anchor download, not `lmaDownload` (`13-runtime.js:208`), which probably does nothing inside the Android WebView **(unverified)**.

### 4.24 Onboarding preview (`#onboarding`, `12-runtime.js:170-173`)

Opens at the class step with the current profile filled in. The main button reads "Switch to <class> →" when a different playable class is selected, otherwise "Close preview ×". Switching calls `switchClassTo` (ends the plan, swaps class quests, save, toast). Closing without switching changes nothing. Back is swallowed.

### 4.25 System dialogs started from Settings

File pickers (avatar, import JSON, trainer pack, USDA file), save-as picker (exports), folder picker (backup), notification permission, exact-alarm settings screen, Health Connect permissions, calendar permission, biometric prompt, widget pin dialog, Android package installer and "Install unknown apps", and the WebView's `confirm()`/`alert()` (import, reset, delete, ascend).

---

## 5. Cross-tab couplings

### 5.1 Wrappers and redefinitions

Rendering on entry. Fifteen wrappers of `navigate` act when the view is `settings`. They run in reverse load order (the last wrapper runs first, then calls the previous one), and each renders its own card after the core `navigate` (`04-runtime.js:49`) has switched views:

| Wrapper | What it does for `settings` |
|---|---|
| `33-android.js:183` | `lmaRenderSettings()` (Notifications); also `lmaSoon(4000)` on every navigation |
| `35-android-upkeep.js:157` | `lmaRenderUpdates()`, `lmaRenderBackup()` |
| `39-health.js:131` | `lmaHealthRender()` |
| `40-widget.js:34` | `lmaWidgetRender()` (async plugin call) |
| `42-scrubnav.js:217` | `lmaThumbRender()` |
| `45-voice.js:600` | `lmvSettingsRender()`, resolved at call time, so it runs the version redefined by `50-voice-fallback.js:97` and then `50-voice-gemini.js:81` |
| `48-journal-lock.js:79` | `jlSettingsRender()` (also tracks the previous view for the lock) |
| `50-pet-hd.js:205` | `petHdSettingsRender()` |
| `50-haptics.js:69` | `hapRender()` |
| `50-food.js:255` | `fv2UsdaRender()` (IndexedDB count) |
| `50-calsync.js:70` | `lmcRender()` |
| `50-native-overview.js:146` | `syncSetting()`, `syncTiming()` |
| `50-native-tabkit.js:199` (one per kit: Character, Log) | `sync()` of the toggle |
| `50-native-nutrition.js:597` | `syncSettings()` |
| `34-navigation.js:79-90` | not settings-specific: Back stack and bar scroll |

Other `navigate` wrappers that also run on the way in: `31-overview-edit.js:151` (closes Overview editing), `42-scrubnav.js:200`, `50-native-cash.js:74`, `50-native-overview.js:36` (`NativeTabs.heal()` after every navigation), `50-quests-hand.js:316`, and the native tabs' leave logic (CONTRACT A30 counts these).

Not re-rendered on entry, only at boot: Light mode (`13-runtime.js:115`), Navigation controls (`34-navigation.js:197`), Quest effects controls (`50-quests-fx.js:134`, `50-sfx.js:31`, `50-quests-cine.js:160`, `50-quests-art.js:21`), Guided workout controls (`50-gym3d.js:514`). Re-rendered on every `save()` through `renderAll`/`renderExtras`: profile fields, avatar, decay inputs, developer mode, XP rules (`04-runtime.js:182`, `05-runtime.js:30`).

Function redefinitions that touch this page:

| Original | Redefined by | Effect |
|---|---|---|
| `save` (`04-runtime.js:13`) | `33-android.js:182` (reminder resync), `50-quests-hand.js:153` (quest auto-verify), `50-native-nutrition.js:623` (native Food refresh), temporarily emptied by `32-pet-dna.js:627,636` during adoption | every Settings save carries these effects |
| `setLightMode` (`04-runtime.js:10`) | `33-android.js:184` | system bar colour |
| `lmvSettingsRender` (`45-voice.js:583`) | `50-voice-fallback.js:96-106`, `50-voice-gemini.js:80-95` | extra rows appended |
| `haptic` (`07-runtime.js:45`) | `50-haptics.js:30-34` | strength-aware, native motor |
| `lmaBuzz` (`42-scrubnav.js:14-21`) | `50-haptics.js:36-46` | strength-aware bar ticks |
| `lmaThumbWarm` (`42-scrubnav.js:52`) | `50-native-overview.js:159` | refuses while native Overview is open or opening |
| `renderOverview` | `39-health.js:162`, `44-rewards.js:144` | tiles that link back to Settings and the rewards sheet |
| `globalThis[cfg.render]` | `50-native-tabkit.js:202-209` | native Character/Log refresh after their page renders |

### 5.2 Timers, observers and global listeners started by Settings segments

None of these depend on the Settings view being visible; they run from any tab. A native Settings screen must not take them over.

| What | Where | Interval / trigger |
|---|---|---|
| Category re-apply | `MutationObserver` on `#view-settings` subtree, guarded by a busy flag (`50-settings-tabs.js:28-33`) | every DOM change inside Settings, including update progress re-renders |
| Reminder sync | `33-android.js:185-187` | 5 min; on resume after 1.5 s; 3 s after boot; 2.5 s after every save; 4 s after every navigation |
| Backup | `35-android-upkeep.js:158-165` | on leaving the app when due; every 6 h when due; 4 s after boot |
| Update check | `35-android-upkeep.js:160-166` | 4 s after boot (silent, three retries at 15 s, 45 s, 2 min); on resume after 30 min |
| Health sync | `39-health.js:132-134` | 30 min; on resume after 15 min; 14 s after boot |
| Widget picture | `40-widget.js:35-40` | on leaving the app; 5 min check (10 min staleness); 9 s after boot |
| Calendar sync | `50-calsync.js:71-73` | on resume after 0.8 s; 5 min while visible; 4 s after boot |
| Journal lock | `48-journal-lock.js:80-81` | relock on leaving the app; 1.8 s after boot |
| Thumbnail capture | `42-scrubnav.js:41-49,215-216` | 1.1 s after each visit; first full pass 9 s after boot when no thumbnails exist |
| Haptic motor probe | `50-haptics.js:70-73` | once, 2.2 s after boot |
| Visit marker | `05-runtime.js:66,71-72` | every 30 s and on resume, **direct full-save write** to `lifemax_rpg_prototype_v1` |
| Decay | `11-runtime.js:41` | on resume, `save()` when decay applies |
| Native toggles | `50-native-tabkit.js:221-222`, `50-native-overview.js:164`, `50-native-nutrition.js:630-631` | 0.7-0.8 s after boot |
| Swipe listeners | `34-navigation.js:136-196` | document touch listeners; inputs, selects and the scrollable chip bar block a swipe start (`34-navigation.js:97-106`) |

### 5.3 Other tabs that read Settings' data

| Data | Read by |
|---|---|
| `lifeMaxTheme`, `--accent` (rewards theme) | the whole page; native screens through `tokens()`/`light()` (`50-native-tabkit.js:21-27`); system bars |
| `nativeOverview/Nutrition/Character/Log`, `tabTiming` | the native tab modules (section 5.1) |
| `tabs` | bottom bar, swipe order, scrub order, captured native bars |
| `swipe`, `swipeAnim`, `scrubSpeed` | `34-navigation.js`, `42-scrubnav.js` |
| `lma_qfx`, `lma_qdeal`, `lma_qpack`, `lma_sfxvol` | Quests (hand, table, cinematic, particles), Luck dice and gym beeps through `QSFX.vol()` (`50-gym3d.js:315`) |
| `lma_gwr3d`, `lma_g3dbg`, trainer pack | Workout plan guided workout (`50-gym3d.js:308`) |
| `state.fx` | every `haptic()`, `lmaBuzz`, WebAudio sounds, `fxReduced()` animations everywhere |
| `state.hdCat` | Cat room drawing |
| `state.journalLock` | Journal (`#view-history`), thumbnails; native screens are not lock-aware (BACKLOG SEC-2) |
| voice keys | voice button, HUD, deep link `schedule` path |
| `lifemax_thumbs_v1` | scrub veil (`42-scrubnav.js:108`) |
| `state.health` | Overview "Activity today" tile and the health sheet (`39-health.js:139-201`); Character weight |
| `lifemax_calsync_v1` | Life Director commitments (`cal_<id>_<minute>` ids) |
| USDA database | Food search (`08-runtime.js:248+`) |
| `notif` prefs | reminder scheduling from Life Director sessions, plan days and check-ins (`33-android.js:42-81`) |
| `state.name/build/weight/height/avatar` | top bar brand, Overview hero, Character page, native Character projection (`50-native-character.js:39-71`), onboarding preview, reports |
| `state.decay` | Character decay watch, decay engine, Overview tips |
| `rules` / `state.customRules` | Log activity XP and its native projection (`50-native-log.js:16-65`), health imports |
| `state.devMode` | attribute sheets, cat tools (`17-runtime.js:264`, `24-pet-depth.js:165,200`, `26-pet-brain.js:791`) |
| Sparks, perks, stars, shields | XP calculations, Overview Rewards tile, Life Director execution bonus perk |

### 5.4 Writers of Settings' data outside this page

`state.weight` (health sync, onboarding), `state.name/build/avatar/classId` (onboarding `12-runtime.js:170`), `lma_gwr3d` (3D gym's classic button, `50-gym3d.js:493`), `state.sparks` (every save through `ecoSparksSync`, `44-rewards.js:13-16`), `rules` (economy migration `43-economy.js:71-84` rewrites old default bases once), `lifemax_director_state_v1` (Life Director itself), `lmaLoad().apps` (Apps launcher, `41-ov-apps.js:8-17`), `lmaThumbs` (thumbnail capture on every visit).

---

## 6. Persistence

### 6.1 Keys and stores

| Key / store | Schema (as used) | Written from Settings by | In backup file? |
|---|---|---|---|
| `lifemax_rpg_prototype_v1` (`STORE`, `01-runtime.js:46`) | whole game state; Settings touches `name`, `build`, `weight`, `heightFt`, `heightIn`, `avatar`, `decay{on,grace,rate}`, `customRules`, `fx{sound,haptics,reduce,hapticLevel}`, `hdCat`, `journalLock{on}`, `health{on,ex,steps,sleep,wt,goal,seen,last,since,msg,sdk,granted,src,today,snap,wtTime,wtHist}`, `devMode`, `tipsSeen`, `tutorialDone`, `classId`, `plan`, `luck`, `luckLog`, shop fields, `ascended`, `attrStars` | `save()` and three direct writers (`toggleDevMode`, `resetTips`, `markVisit`) | yes (`state`, plus `rules`) |
| `STORE+'_safety'` | `{reason:'import'|'reset', at, save:<raw string>}`, one slot | `keepSafetyCopy` | no; deleted by "Delete all" |
| `STORE+'_unreadable'` | raw string, written once | `load()` | no; deleted by "Delete all" |
| `lifemax_android_prefs_v1` (`33-android.js:3`) | `{notif{on,sessions,lead,workout,workoutTime,nudge,checkin,checkinTime,quiet,quietFrom,quietTo}, apps[], swipe, swipeAnim, scrubSpeed, updChecked, webAuto?, backup{on,uri,folder,days,keep,last,status,err,lastName}, tabs{order[],hidden[],extra[]}|null, nativeOverview, nativeNutrition, nativeCharacter, nativeLog, tabTiming}`; cached in memory as `lmaPrefs` (`33-android.js:25-31`) | `lmaSave()` | yes, with `backup.uri/folder/on/last/status` blanked |
| `lifeMaxTheme` | `'light'` or `'dark'` | `setLightMode` | yes |
| `lifemax_director_state_v1` (+ `.bak`, `lifemax_director_quarantine_*`) | Life Director store (`features/life-director/logic.js:2244-2300`) | calendar import only | main key yes; `.bak` no |
| `lma_set_cat`, `lma_qfx`, `lma_sfxvol`, `lma_qdeal`, `lma_qpack`, `lma_gwr3d`, `lma_g3dbg` | strings | their setters (section 4) | no |
| `lifemax_voice_enabled`, `lifemax_voice_prefs_v1` `{alwaysPreview,debug}` | string, JSON | voice card, diagnostics | no |
| `lifemax_voice_op_journal_v1`, `lifemax_voice_fallback_logs_v1` (≤200), `lifemax_voice_ratings_v1` (≤100), `lifemax_voice_alias_v1`, `lifemax_voice_timer_v1`, `lifemax_voice_macros_v1`, `lifemax_voice_undo_v1` | JSON arrays/objects | voice engine; Clear removes the two log keys | no |
| `lifemax_gemini_key_v1`, `lifemax_gemini_on_v1`, `lifemax_gemini_ui` | API key in plain text, flags | hidden voice rows | no |
| `lifemax_thumbs_v1` | `{view: JPEG data URL}` | capture, journal lock | no |
| `lifemax_calsync_v1` | `{on, ids{calId:{s,t}}, last, msg, n}` | calendar card and sync | no |
| `lifeMaxDevChat` | `{m[], seen[]}` | dev chat, seeds | no |
| `lm_pet_seen` | pet | not Settings | no |
| IndexedDB `lifemax_usda_branded_v1` | store `foods` (keyPath `id`, indexes `terms` multi-entry and `name`) | Food database card | no |
| IndexedDB `lma_trainer` | store `pack`, key `current` | Guided workout card | no |
| IndexedDB `lifemax_audio_v1` | audiobook files (`07-runtime.js:158-166`) | not Settings | no |
| Native SharedPreferences (LiveWeb) | staged and active web builds (`native/LiveWeb.java:23-48`) | Download / Restart | no |
| Backup folder (SAF tree URI) | `life-max-auto-YYYY-MM-DD-HHMM.json`, pruned to `keep` newest by modification time | automatic backup | n/a |

### 6.2 Backup file format

`backupJson()` (`04-runtime.js:259`): `{app:'Life Max RPG Prototype', version:1, exportedAt, state, rules, extras:{lifemax_director_state_v1, lifemax_android_prefs_v1, lifeMaxTheme}}`, pretty-printed with two-space indents. `version` is written but never checked on import. The state has no schema version (BACKLOG, data safety). Missing from the file, although the Data management card says "all … settings": the seven `lma_*` keys, all voice keys, the calendar import map, thumbnails, dev chat, the Life Director `.bak`, the safety copy, and all IndexedDB data (USDA foods, trainer pack, audiobook files).

### 6.3 Restore semantics (`applyBackup`, `04-runtime.js:264`)

1. Accepts `data.state` or a bare state; requires `activities` to be an array and `attrXP` an object; nothing else is validated.
2. `state={...freshState(), ...imported}`: unknown keys are kept, missing keys come from the fresh state (so a save without `onboardingComplete` stays without it unless the import came from onboarding).
3. `rules` from `data.rules`, else `state.customRules`, over the defaults.
4. `save()` runs the economy migration when `xpSchemaV2` is missing (`43-economy.js:71-84`): it sets `legacyCharXPBonus`, resets `decayApplied` to today, deletes an old `decay {grace:3,rate:2}`, upgrades old default rule bases, sets `sparks` to half of raw XP and marks every activity as paid.
5. `lmaRestoreExtras` writes the three extras verbatim (`33-android.js:169`). Because export blanked the backup fields, the restored prefs have **automatic backup off and no folder**; native-screen opt-ins, bar order, reminders and swipe settings also become those of the file.
6. Reload after 500 ms. Until then, the in-memory `lmaPrefs` is the old one, and any `lmaSave()` in that window would write the old prefs back over the restored ones (low probability, from code reading).

### 6.4 Migrations

No Settings-specific migration. Defaults fill missing fields at read time: `lmaLoad` (`33-android.js:29`), `fxOpt` (`07-runtime.js:43`), `getDecay` (`05-runtime.js:43`), `lmaHealthCfg` (`39-health.js:6-10`), `lmcLoad` (`50-calsync.js:5`), `lmvPrefs` (`45-voice.js:10`). `rules` is rebuilt at load as defaults plus `customRules` (`04-runtime.js:12`), so new rule types appear automatically and removed ones linger in `customRules`.

### 6.5 Sizes

Not measured on the owner's phone. What the code bounds: avatar is a 256×256 JPEG at quality 0.85 (tens of KB, **unverified**); thumbnails are one-quarter-scale screenshots of up to 14 tabs in localStorage (**size unverified**; they share the WebView quota with the core save, and a full quota makes `save()` fail); voice logs are capped at 200 and 100 entries; the operation journal at 50 operations (`45-voice.js:5`); health `seen` entries are pruned after 21 days (`39-health.js:98`); `wtHist` keeps 60 readings; dev chat is unbounded. The backup file size is the core save plus the Life Director state; the test fixture `tests/fixtures/log-activity-golden.json` is 153 KB for a synthetic profile, and the owner's real save is certainly larger **(unverified)**. A trainer pack may be up to 40 MB and a USDA import hundreds of thousands of rows, both in IndexedDB.

---

## 7. Native readiness

### 7.1 Easy (projection plus Compose)

- Category chips and card layout (local UI state; persist the chip through a command or keep `lma_set_cat`).
- Every on/off row and fixed select: light mode, native opt-ins, tab timing, swipe, swipe animation, scrub speed, quest effects, sound volume, deal mode, card art (pack names only), workout screen, 3D diagnostics, haptics on and strength, HD cat, voice on, reminder toggles, lead time, backup frequency and keep, health type toggles and steps goal, calendar on.
- Read-only status rows: installed and latest build, update state text, last backup, last health sync, widget count, calendar last check, thumbnail count, USDA count, trainer pack name, Sparks.
- Bottom-bar editor: a list with up/down buttons and a checkbox per row; the rule "keep at least two" can be shown natively but must stay enforced by the web handler.

### 7.2 Medium

- Text and number inputs: profile name, weight, height, decay grace and rate, nine XP rule bases, quiet hours and reminder times (time pickers). The web handlers read the DOM (`saveCharacter`, `saveDecay`, `saveRules`), so commands need payload-taking versions first, as was done for Log ("logic callable without the form").
- Asynchronous facts. `NativeTabKit` builds the projection synchronously (`50-native-tabkit.js:58-60`), but permission state, exact-alarm state, widget status, the USDA count and the motor probe are only known asynchronously inside today's renderers. They need a small cache that the renderers fill (for example `window.SettingsFacts`), read by `projection()`.
- Live progress: update download percentages and backup "Saving…" need a refresh trigger; the existing `MutationObserver` on `#view-settings` (`50-settings-tabs.js:31`) fires on every card re-render and could call the kit's `schedule()`.
- The avatar: a data URL of tens of KB in the projection is fine under the 2 MB snapshot cap, but it is re-sent on every refresh.
- The rewards sheet: flat lists, but it is shared with Overview and spends Sparks.

### 7.3 Hard (keep web at first)

- **File pickers.** Import JSON, avatar upload, trainer pack and USDA import all use hidden `<input type="file">`. A native command that calls `input.click()` has no user gesture behind it, and Chromium normally refuses to open a file chooser without one, so this probably fails inside the WebView **(unverified)**. Either the web Settings page must be shown so the owner taps the web button, or a native "open document" plugin must hand the file content to the web handler.
- **`confirm()` and `alert()`.** Import, reset, delete and ascend block the JS thread on a WebView dialog. Running them from a command while a native screen covers the page is untested; delegating by closing the screen first is safer.
- **Capture all.** It navigates through every tab with the page visible. It must run with the web page in front and native screens blocked (see hazard 2 below).
- **Dev chat, voice diagnostics, onboarding preview, tutorial.** Each is a large web overlay with text input; keep them web.
- **Update flow.** Installer, unknown-sources screen and LiveWeb restart are platform flows driven by web state with progress listeners; a native screen can show the state and delegate the buttons.
- **USDA import.** Streams a gzip into IndexedDB for minutes; must stay web.

### 7.4 Web dependencies that stay delegated in phase 1

All writers of the core save (profile, decay, XP rules, haptics, HD cat, journal lock, developer mode and its tools, rewards, health sync), all of Data management, every file picker, every sheet in sections 4.18 to 4.24, the update buttons, "Capture all", the calendar and health permission flows, and the backup folder picker. The background timers in section 5.2 stay in web code and must keep running while a native Settings screen is shown (CONTRACT A16: do not pause WebView timers the authority needs).

### 7.5 Hazards a native Settings screen must respect

1. **The switch back to Classic lives in Settings itself.** If native Settings breaks, the owner could lose the way back. A native Settings screen needs an always-visible "Classic Settings" row, and the opt-in must also stay reachable in web Settings.
2. **`blocked()` for Settings must include more than the kit's default** (`50-native-tabkit.js:56`): `lmaThumbWarmBusy` (capture navigates away), `#devChat.open` (not in `lmaTopOverlay`), a pending file chooser, and a pending `confirm()`. `.hsheet` sheets, `.updpop`, onboarding and the tutorial are already covered by `lmaTopOverlay` (`34-navigation.js:54-67`).
3. **Unsaved edits.** Today any background save rewrites the profile fields and XP inputs. A native form must keep its own draft until the user saves, and must not overwrite it from a fresh projection while a field has focus.
4. **Native toggles change native hosts.** Turning off Native Overview/Character/Food/Log from a native Settings screen closes those screens; turning on Native Overview opens one only on the dashboard. The kit's hand-over must keep Settings in front.
5. **Data actions are the riskiest writes in the app.** Import replaces the whole save and reloads; reset and delete replace it with a fresh state; backups prune. No native command for these should exist before ADR 0004 (verified backup, restore test, save version).
6. **Settings toggles that write the core save run the full `save()` chain**, including quest auto-verification and reminder resync. A native command through those handlers inherits this.
7. **Two web settings have no control** (`fx.sound`, `fx.reduce`). A native screen should not silently invent them; ask the owner (section 8).

### 7.6 Proposed phasing

| Phase | Scope | Writes |
|---|---|---|
| 0 | Prerequisites: async facts cache; payload versions of `saveCharacter`, `saveDecay`, `saveRules`; extend `blocked()`; a "Native Settings (preview)" opt-in in Appearance and a Classic row in the native screen | none new |
| 1 | Presentation only: native categories, every card's facts and controls, drawn from the projection. Every tap delegates by closing the screen and running the page's own handler, with the web page scrolled to the card (`cfg.onAction`). Sheets, file pickers and data actions are reached the same way | none from native |
| 2 | Commands for preference setters that do not touch the core save: theme, native opt-ins, tab timing, bar order and visibility, swipe, scrub, quest effects, workout screen, diagnostics, voice on, reminder prefs, backup frequency and keep, "Back up now", "Check now", widget refresh. Each command calls the existing web setter; the web stays the only writer | prefs keys, through web handlers |
| 3 | Commands for core-save settings through the payload handlers: profile, decay, XP rules, haptics, HD cat; with the unsaved-draft rule and pending/confirmed display (CONTRACT A11) | core save, through web handlers |
| 4 | Data management, journal lock, health and calendar on/off, rewards: only after ADR 0004 is done and the owner agrees; until then they stay delegated | core save, whole-save replacement |

### 7.7 Phase 1 sketch (proposal; nothing here is implemented)

`50-native-settings.js` would call `NativeTabKit.create` with:

```
id: "settings", plugin: "NativeSettings", capability: "settings.projection.v1",
setting: "nativeSettings", toggle: "nativeSettingsToggle",
render: "renderAll",            // core-save facts; the #view-settings observer also calls schedule()
blocked: () => lmaThumbWarmBusy || !!document.querySelector('#devChat.open') || SettingsFacts.pickerOpen,
globals: { open: "openNativeSettings", enable: "setNativeSettingsEnabled", projection: "NativeSettingsProjection" }
```

Projection fields (all values from the sources cited in section 3; strings already formatted by the page, so Kotlin only lays out):

```
{
  category: "look" | "voice" | "phone" | "game" | "data",
  categories: [{ id, name }],
  android: bool,                                         // lmaNative()
  appearance: { light, timing: { available, on, last },
                nativeTabs: [{ id: "nativeOverview"|"nativeNutrition"|"nativeCharacter"|"nativeLog"|"nativeSettings",
                               label, hint, available, on }] },
  bar: { rows: [{ view, label, icon, visible, extra, first, last }] },
  navigation: { swipe, swipeAnim, scrubSpeed: "normal"|"fast"|"faster" },
  questFx: { cardFx, volume: "0"|"0.35"|"0.7"|"1", deal: "table"|"cine"|"quick"|"off",
             pack, packs: [{ id, name }] },
  workout: { screen: "on"|"off", diagnostics: "on"|"off", trainerPack: { loaded, name } },
  haptics: { on, level, levels: [{ id, label }], note },
  updates: { state, title, status, release, button: null|"check"|"download"|"restart"|"update", stableWarning },
  voice: { enabled, fallbackCount, likedCount },         // hidden cloud rows stay out of the projection
  cat: { hd },
  journalLock: { available, on, message },
  thumbs: { available, have, total, error, busy },
  widget: { available, count, canPin, updated, message, busy },
  calendar: { available, on, lastText, message },
  health: { available, on, types: [{ id, label, hint, on }], goal, goals: [..], missing: [..], sdkNote, lastText, busy },
  usda: { status, count },
  backup: { available, on, folder, days, keep, lastText, note, busy },
  notifications: { available, permission, exact, prefs: { on, sessions, lead, workout, workoutTime, nudge,
                   checkin, checkinTime, quiet, quietFrom, quietTo } },
  profile: { name, build, builds: [..], weight, heightFt, heightIn, avatar, initial },
  rewards: { sparks },
  decay: { on, grace, rate },
  dev: { on, chatCount, classes: [{ id, name }] },
  xpRules: [{ id, name, icon, skill, base }],
  data: { activities }                                   // for the import/reset/delete confirmations later
}
```

Actions in phase 1 (all through `onAction`, which closes the screen and runs the web handler): `{action:"set", id, value}` mapped to the setter in section 4; `{action:"open", id:"rewards"|"devChat"|"voiceDiag"|"preview"|"tutorial"}`; `{action:"run", id:"checkUpdate"|"download"|"restart"|"install"|"backupNow"|"chooseFolder"|"captureAll"|"widgetPin"|"widgetRefresh"|"healthSync"|"healthAllow"|"calendarCheck"|"notifTest"|"notifAllow"|"exactTiming"|"exportJson"|"exportReport"|"resetTips"|"luckReset"|"switchClass"|"resetRules"|"tabsReset"}`; `{action:"reveal", card}` for anything needing a web input (file pickers, text fields, import, reset, delete), which closes the screen and scrolls the web page to that card. `revisit()` is called when `#devChat` closes, when an `.hsheet` is removed, and when `lmaThumbWarm` finishes.

Tests to add with it (NATIVE_TAB_GUIDE): a node projection test with fixtures for browser, Android without plugins, and Android with every plugin; a hand-over simulation case for Settings (it is reachable from every native screen's gear); screenshot tests per category, light and dark.

---

## 8. Open questions for the owner

1. **Restore and automatic backup.** Restoring a backup turns automatic backup off and forgets the folder. Should a restore keep your current backup folder and schedule?
2. **Backup rotation.** "Keep the newest N" can replace all good backups with bad ones a few days after a mistake. Would you like the app to keep, say, one backup per week for a month, or refuse to prune when the save suddenly shrinks?
3. **What a backup should contain.** Today it leaves out quest effect and sound choices, voice settings and logs, calendar import state, the dev chat, the USDA foods, the trainer pack and audiobook files. Which of these do you want in the file?
4. **"Delete all local data".** It deletes the safety copy and only the main save. Should it also clear Life Director, settings and the other data, and should it keep a safety copy first? Do you need the button at all?
5. **"Reset demo data".** Is it still useful, or can it go?
6. **Sound and reduced effects.** There is no switch for game sound or for reduced effects any more, although the quest sound setting still refers to one. Should both come back in Settings?
7. **Unsaved edits.** Typing in Character profile or XP rules can be undone by a background save. Have you noticed this? (It will be fixed either way; the answer tells us how urgent it is.)
8. **Developer tools.** Do you still use developer mode, the dev chat, "Preview start screen", the class switch and the luck reset? If not, a native Settings can leave them on the classic page.
9. **Rarely used cards.** Do you use Tab previews ("Capture all"), 3D diagnostics, the trainer pack, the Food database import and the hidden cloud voice option?
10. **Priority.** Settings has the riskiest buttons in the app. Should it be one of the last tabs to go native, with Data management staying on the classic page until verified backups exist?
11. **Settings in the bottom bar.** Should Settings be in the bar by default, or stay behind the gear?
12. **XP rules.** Clearing a field and saving sets that activity's base XP to 1, and "Reset defaults" asks for no confirmation. Is that acceptable?
