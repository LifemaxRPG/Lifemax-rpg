# Library native slice inventory (Media and Journal)

Baseline: branch `track/docs` at `d56403c0410c73686ce8c5675d16cbe627e2ebd1` (2026-10-30, Native API 23). Read-only audit; no code was changed.

Since then the Library work (branch `track/library`) made the phase 0 fixes and the web half of phase 1. Section 9 lists what it changed and which findings and questions it answers; sections 1 to 8 are the audit as it was, with a pointer where something no longer holds. Line references are the baseline's and have drifted; function names are current.

Reference conventions. Paths are relative to `lifemax_modular_v73/src/`. Runtime segments are cited by file name alone (they all live in `runtime/segments/`, for example `04-runtime.js:54`). Segments are byte-cut, so a statement can start in one file and end in the next: `function revokeAudioUrl` starts at `07-runtime.js:260` ("function r") and continues at `08-runtime.js:1`; `function mediaAudioProgressMarkup` starts at `10-runtime.js:296` ("function med") and continues at `11-runtime.js:1`. Android paths are relative to `android-wrapper/`. "Verified" means read in the source at this baseline. Anything not verified is marked **(unverified)**; behaviour that follows from the code but was not run is marked **(from code reading)**.

---

## 1. Summary

**What the tab is for.** One bottom-bar tab, "Library", holding two pages:

- **Media** (`media`): a personal reading/watch list (books, audiobooks, articles, courses, podcasts, videos, documentaries) with status, notes and links, plus an in-app audiobook player for `.m4b`/`.m4a`/audio files the owner attaches. The files are stored on the device in IndexedDB. No XP, no economy.
- **Journal** (`history`): a day-by-day chronicle built from logged activities, workout-plan notes and the owner's own written reflections, with a deterministic offline "Journal coach" (day archetypes, insights, patterns, reflection prompts, welcome-back cards, weekly and monthly reviews) that pays small XP rewards. It can be locked behind the phone's fingerprint or screen lock (the "Journal lock").

**Page views.**

| View id | Section | Markup | Rendered by |
|---|---|---|---|
| `media` | `<section id="view-media" class="view">` | `views/media.html:1-36` (static shell: switch, header, stats, form, filters, `#mediaList`) | `renderMedia` `11-runtime.js:18-39` |
| `history` | `<section id="view-history" class="view">` | `views/history.html:1-8` (switch, `#journalHero`, `#journalDays`, `#journalEntry`) | `renderHistory` `04-runtime.js:54-75` |
| global | `#audioPlayer` and `#audioFileInput`, outside every view, fixed to the bottom of the window | `index.template.html:74-107` | `08-runtime.js:4-169` |

There is no `#view-library`. The nav key `library` is a group: `LMA_GROUPS={library:['media','history'],...}` (`34-navigation.js:7`); `lmaTabKey` maps both views to `library` (`34-navigation.js:8`); `lmaResolve('library')` returns `state.libPane||'media'` (`34-navigation.js:9`). `state.libPane` is written by the navigate wrapper in `42-scrubnav.js:204` on every visit to either page, so "Library" reopens the last pane. Raw `navigate('library')` would show a blank page (base `navigate` toggles `.view.active` by id, `04-runtime.js:49`); every caller found goes through `lmaNavTo`.

**Entry points (all verified).**

| Entry | Where | Path |
|---|---|---|
| Bottom-bar button "Library" (icon `book`) | `index.template.html:51` (`data-view="library"`, `onclick="lmaNavTo('library')"`); phone taps are captured by `42-scrubnav.js:189-195` | `lmaNavTo('library')` -> `libPane` |
| Bottom-bar scrubbing and tab swipe | `42-scrubnav.js:131-197` (veil icons `media`, `history`, `library` at `42-scrubnav.js:88,92,96`), `34-navigation.js:136-196` | `lmaNavTo(...)` |
| Media / Journal switch (`.libseg`, on both pages) | `views/media.html:2`, `views/history.html:2` | `lmaNavTo('media')` / `lmaNavTo('history')` |
| Native tab bars (Character, Food, Log screens) | `android-wrapper/native/src/main/java/com/lifemax/rpg/feature/character/presentation/components/CharacterChrome.kt:74,77` (`library` and `journal` -> `book`); NativeTabKit action listener `50-native-tabkit.js:133-136` | native `action` with `view:'library'` closes the native screen, then `lmaNavTo('library')` |
| Native Overview shell | `50-native-overview.js:165-175` (`activate`), `nativeView` uses `lmaResolve` (`50-native-overview.js:145`) | tab tap replayed on the web button |
| Overview "History ›" link (Recent activity card) | `18-runtime.js:49` | `navigate('history')` (raw navigate, no slide, still runs all wrappers) |
| Legacy dashboard "View history" button | `views/dashboard.html:36` | `navigate('history')`; this card belongs to the pre-redesign dashboard grid and is probably not visible with the current Overview **(unverified)** |
| Back history | `34-navigation.js:69-74,77-91` | previous view |
| Voice navigation "open library / media / books / journal / history" | `46-voice-nlu.js:37` (all five words map to id `media`), `46-voice-nlu.js:58-68`, `lmvGo` `46-voice-nlu.js:121-127` | `lmaNavTo('media')`. **Saying "open my journal" opens Media, not the Journal** (verified). Fixed in phase 0: "journal" and "history" open the Journal (section 9) |
| Voice reply chips "Open Journal" | `46-voice-nlu.js:295` (activity history answer), `50-voice-gen4.js:407` and `50-voice-gen5.js:723` (evening wrap-up) | `lmvOpenChip('history','Journal')` -> `lmaNavTo('history')` |
| Voice "search my library for X" | classifier `50-voice-gen4.js:203-204`, dispatch `:281`, handler `g4Library` `:386-393`, capability list `:570` | `lmvGo({id:'media'})`, then 60 ms later writes X into `#mediaSearch` and calls `renderMedia()` |
| Voice "what is on this screen" while on either pane | `50-voice-gen5.js:167` (`g5Screen` maps both to `library`), `:225` | read-only summary of media counts (also when the Journal is showing) |
| Voice dictionary words | `49-voice-time.js:8` (`library`, `journal`) | speech-recognition hints only |
| Deep links | none: `lifemax://open?feature=` knows only schedule, cats, quests (`50-deeplink.js:1-13,23`) | none |
| Launcher shortcuts, Assistant | none for Library (no match in `android-wrapper/res`) | none |
| Widget | none: the widget opens only `life-director` (`android-wrapper/native/LifeDialWidget.java:104`, `40-widget.js:31`) | none |
| Notifications | none open the Library | none |

The Library button can be hidden or moved by the owner in Settings, Bottom bar (`50-tabs-edit.js:9-28`); the pages stay reachable by voice and the Overview link.

**Size.** Markup: `views/media.html` 36 lines, `views/history.html` 8 lines, audio player 34 lines. Logic: media and audio about 360 lines (`07-runtime.js:120-260`, `08-runtime.js:1-182`, `10-runtime.js:296` + `11-runtime.js:1-39`, seed list `03-runtime.js:10-26`, `04-runtime.js:1-8`); Journal core 126 lines (`04-runtime.js:54-179`); Journal coach 358 lines (`47-journal-coach.js`); Journal lock 82 lines (`48-journal-lock.js`). About 1,000 lines in all. CSS: `07-finishing.css:118-121` (switch), `:154-286` (coach and redesign), `:288-295` (lock); `01-base.css:263-302` (journal base and phone layout); `02-layout.css:247-310,353-355` (media rows and audio player); light theme `00-00-foundation.css:185,190`. No embedded image or sound assets. Tests: `tests/lock.test.js` (66 lines), `tests/coach.test.js` (116 lines); for Media only the voice search check in `tests/voice4.test.js:142-144` (search opens `view-media` and fills the box). **No test covers adding, editing, finishing or deleting media, or the audio player.**

**Native risk.**

- Media: **low to medium**. Small list, no XP, simple writes. The audio side is hard: blobs live in IndexedDB, the player is a web `<audio>` element shared with every tab, and the file picker needs a user tap inside the WebView.
- Journal: **high**, for four reasons.
  1. Privacy. The lock is a CSS cover inside `#view-history` only. The journal text is rendered into the DOM on every `save()` even while locked (`renderAll` `04-runtime.js:182` -> `renderHistory`), and hidden only by `#view-history.jlocked>*:not(#jlOverlay):not(.libseg){visibility:hidden}` (`07-finishing.css:290`). NativeTabKit has no idea the lock exists (BACKLOG SEC-2 confirmed, see 5.4).
  2. Rendering writes the save. The coach decides and stores pattern chips, welcome records, prompt ids and review records while drawing, and two of those paths call `save()` from inside `renderHistory` (see 5.3). A projection that reuses these helpers would write during `snapshot()`.
  3. XP. Reflections and reviews pay XP through `jcAward` + `save()` into the core save `lifemax_rpg_prototype_v1`, which holds the owner's years of history.
  4. Text input (4,000-character reflections, 2,000-character review answers) and heavy time dependence (Today, yesterday, the 20:00 review cutoff, the 3-day chip window).

---

## 2. States

### 2.1 Library (both panes)

| State | What decides it | Where |
|---|---|---|
| Which pane "Library" opens | `state.libPane` (`'media'` default), written by every navigation to either page | `34-navigation.js:9`, `42-scrubnav.js:204` |
| Switch highlight | `.libseg button.on` toggled by `data-lib===v` after each navigate; nothing is lit before the first visit | `42-scrubnav.js:208`, `07-finishing.css:119-121` |
| Onboarding / tutorial covering the app | `#onboarding.show`, `#tutorial.show`; NativeTabKit also refuses while `!state.onboardingComplete` | `index.template.html:10-36,111-123`, `50-native-tabkit.js:56` |
| First visit after the tutorial | Feature tip inserted if `state.tutorialDone && !state.tipsSeen[view]`: Media tip after `.pagehead`; Journal tip before the switch (the Journal has no `.pagehead`). Dismiss writes `tipsSeen` straight to storage | `FEATURE_TIPS.media` `13-runtime.js:60-64`, `.history` `:70-74`, `maybeShowFeatureTip` `:93-110`, `dismissFeatureTip` `:76-81`, called from `navigate` `04-runtime.js:49` |
| Tab hidden from the bar | `lmaLoad().tabs.hidden` | `50-tabs-edit.js:24-27` |
| Scrubbing the bar | blurred thumbnail of the `library` tab from `lifemax_thumbs_v1`; never taken of a locked Journal | `42-scrubnav.js:25-50,100-114` |

### 2.2 Media pane

| State | What decides it | Where |
|---|---|---|
| Fresh save | `freshState().mediaItems=[]`, then `seedReadingList()` adds 15 default books (status `unread`) once, guarded by `state.mediaSeededV1` | `04-runtime.js:9`, `04-runtime.js:1-8`, list `03-runtime.js:10-26` |
| Empty library | no items: "Your library is ready" + "＋ Add your first item" | `11-runtime.js:38` |
| No matches | items exist but none pass the filters: "No matching items / Try changing your search or filters." | `11-runtime.js:38` |
| Filtered list | `#mediaSearch` (substring over title, creator, notes, type), `#mediaFilter` (all/unread/progress/finished), `#mediaTypeFilter` (all + 8 types). DOM state only, not saved; survives tab switches, lost on reload | `views/media.html:30-32`, `11-runtime.js:26-28` |
| Form hidden / Add / Edit | `#mediaFormCard` `display`; Edit when `#mediaEditId` has a value | `views/media.html:13-27`, `07-runtime.js:120-132` |
| Item without audio / with audio | `item.audio` present (`name` set) | `08-runtime.js:170-182`, `11-runtime.js:1-6` |
| Audio progress label | Finished (status finished) / "Almost finished" (>= 99.5%) / "N% listened" / "Progress available after playback starts" (no duration yet) | `11-runtime.js:4-5,13` |
| Saving an attached file | toast "Saving audio to this device…" while `audioDbPut` runs | `07-runtime.js:223-231` |
| File too large | > 512 MB: toast, nothing stored | `07-runtime.js:221-222` |
| Storage failure | toast "Could not store audio (storage full or blocked)" | `07-runtime.js:245-248` |
| Audio file missing (metadata says attached, IndexedDB has nothing, e.g. after a restore) | toast "Audio file missing — attach it again", `item.audio` deleted, `save()` | `08-runtime.js:50-55` |

### 2.3 Audio player (global, any tab)

| State | What decides it | Where |
|---|---|---|
| Hidden | `#audioPlayer` without `.show` | `02-layout.css:275-282` |
| Loading | between Play and `loadedmetadata` | `08-runtime.js:56-74` |
| Shown expanded / minimized | `.show`, `.minimized` (minimized hides progress, controls, subtitle; shows the round mini play button) | `08-runtime.js:15-33`, `02-layout.css:283-292` |
| Playing / paused | `audioEl.paused`; labels "Play"/"Pause", glyph ▶/❚❚ | `08-runtime.js:80-89` |
| Seeking | `audioCtx.seeking` while the range is dragged | `08-runtime.js:105-116` |
| Autoplay refused | toast "Tap Play to start (browser blocked autoplay)" | `08-runtime.js:71` |
| Decode error | toast "This browser cannot decode that audio file" | `08-runtime.js:168` |
| Ended | marks the item finished once (sets `finishedAt`), keeps the position at the end | `08-runtime.js:146-167` |

The player stays up while the owner moves to other tabs. Every native tab (Character, Food, Log, and a future Library screen) is a ComposeView added above the WebView (`ComposeTabPlugin.kt:153-166`), so it covers the player: playback continues with no visible controls **(from code reading; not checked on device)**.

### 2.4 Journal pane

| State | What decides it | Where |
|---|---|---|
| Boot before the coach exists | the first `renderAll` (`13-runtime.js:115`) draws the plain list (`.journal-day-btn`) and plain reflection box; `47-journal-coach.js:358` redraws with the coach. Afterwards the non-coach branches of `renderHistory`, `renderJournalDay`, `saveDayReflection` are dead code | `04-runtime.js:68-74,153,177-178` |
| No journal days at all | `journalDays()` empty: hero is still drawn, list emptied, entry shows "No journal entries yet…". **No reflection box exists in this state** | `04-runtime.js:63-67` |
| Which days exist | a day appears only if it has an activity, a non-empty reflection, or a non-empty workout-plan note. **Today is absent until something is logged or a plan note is written today**, so today's reflection cannot be written before that (the "Nothing logged this day yet" body at `04-runtime.js:156-157` is reachable only for a day that has a reflection but nothing logged) | `04-runtime.js:77-88` |
| Selected item | module variable `histSel` (memory only): a date `YYYY-MM-DD`, or a review key `W:YYYY-Www` / `M:YYYY-MM`. Reset to the newest day (or today) when missing | `04-runtime.js:58,76,108` |
| Day page vs review page | `/^[WM]:/.test(histSel)` | `04-runtime.js:74` |
| Distress words in the day's reflection | care note replaces welcome, insight and prompt; the plain reflection box remains | `JC_DISTRESS` `47-journal-coach.js:208`, `jcCareNote` `:209`, `jcTop` `:248-255` |
| Day has no archetype | `jcArch` null when the day has neither activities nor a reflection: no insight, no prompt | `47-journal-coach.js:80-82,121-122` |
| Welcome back | only for today, after >= 3 empty days: tier 1 (3-6 days), 2 (7-13), 3 (14+); chips disabled once claimed | `47-journal-coach.js:211-233` |
| Reward eligibility of a reflection | today or yesterday, >= 40 characters, not yet paid (`journalCoach.days[d].xpAwarded`) | `47-journal-coach.js:140-152,256-263` |
| Review exists | weekly: >= 2 active days and the week has ended (or it is its last day after 20:00); monthly: >= 5 active days and the month has ended (same 20:00 rule); at most 40 listed | `47-journal-coach.js:267-280` |
| Review paid | `journalCoach.reviews[key].paid` | `47-journal-coach.js:350-355` |
| Phone vs wide layout | phone (< 761 px): tiles are a horizontal strip; wide: three-column grid beside the entry | `07-finishing.css:192-194,281-285`, `01-base.css:300-302` |
| Reduced motion | entry fade-in and tile press animation off | `07-finishing.css:216` |

### 2.5 Journal lock

| State | What decides it | Where |
|---|---|---|
| Lock off | `state.journalLock.on` falsy | `48-journal-lock.js:7` |
| Lock on, locked | `jlOn() && !jlUnlocked`: section gets `.jlocked`, cover `#jlOverlay` shown ("Journal locked" + Unlock), `FLAG_SECURE` set while the view is `history` | `48-journal-lock.js:14-33`, `07-finishing.css:289-295`, `android-wrapper/native/BiometricLockPlugin.java:78-90` |
| Prompting | `jlPrompting` true while the system BiometricPrompt is up; repeated taps are ignored. If the promise never settles, Unlock does nothing until reload **(edge case, from code reading)** | `48-journal-lock.js:34-41` |
| Unlock failed / cancelled | "Could not unlock. Try again." / no message | `48-journal-lock.js:39` |
| Unlocked | `jlUnlocked=true` (memory only), cover hidden, journal re-rendered | `48-journal-lock.js:38` |
| Grace | leaving the Journal records `jlLeftAt`; returning within 60 s stays unlocked; later it locks and prompts | `JL.graceMs` `48-journal-lock.js:5`, `jlOnNavigate` `:43-52` |
| App to background | locks at once (`visibilitychange` to hidden) | `48-journal-lock.js:80` |
| In a plain browser (no plugin) | toggle disabled: "Available in the Android app." If a save with the lock on is opened in a browser, `jlAuth` returns `{ok:false,error:'web'}` and the Journal can never be unlocked there **(from code reading)** | `48-journal-lock.js:8-10,57` |
| Phone without fingerprint or screen lock | turning on refuses ("Set up a fingerprint or screen lock…"). A restored backup carries `journalLock.on:true` (it is in the core save) to a phone that may have no screen lock; unlock and turning the lock off both need a successful prompt, so the Journal is unreachable in-app there **(from code reading)** | `48-journal-lock.js:61-73` |

---

## 3. Displayed facts

### 3.1 Media header and stats (`renderMedia` `11-runtime.js:18-25`)

| Element | Shows | Computed from |
|---|---|---|
| eyebrow, title, blurb | "YOUR PERSONAL LIBRARY", "Media library", "Reading list, courses, and audiobooks. Attach .m4b/.m4a files…" | static, `views/media.html:4` |
| `#mediaTotal` | number of items | `state.mediaItems.length` (all items, not the filtered list) |
| `#mediaFinished` | items with `status==='finished'` | `state.mediaItems` |
| `#mediaProgress` | items with `status==='progress'` | `state.mediaItems` |
| `#mediaPct`, `#mediaBar` width | `round(finished/total*100)` % | same; 0 when empty |

### 3.2 Media rows (`renderMedia` `11-runtime.js:28-38`, `mediaAudioButtons` `08-runtime.js:170-182`, `mediaAudioProgressMarkup` `10-runtime.js:296`/`11-runtime.js:1-6`)

Order is the stored array order: new items are added at the top (`unshift`, `07-runtime.js:141`), seeded books at the end, edits keep their place. No sort options.

| Element | Shows | From |
|---|---|---|
| checkbox | checked when finished; label "Mark {title} finished" | `x.status` |
| title | `x.title` | |
| subtitle | `type · creator` (type defaults to "Other") | `x.type`, `x.creator` |
| audio progress (only with audio) | label (see 2.2), `pos / dur` as `m:ss` or `h:mm:ss` (`fmtAudioTime` `07-runtime.js:194-199`), bar width (100% when finished) | `x.audio.position`, `x.audio.duration`, `x.status`; updated live during playback by `updateMediaAudioProgress` (`11-runtime.js:7-17`) which writes the DOM directly and `item.audio.position` in memory |
| status pill | "Want to read / watch" / "In progress" / "Finished" (finished pill styled) | `x.status` |
| link pill | "Open link ↗", `<a target="_blank" rel="noopener noreferrer">` | `x.link` (escaped but **scheme not checked at render**; only `saveMediaItem` enforces http/https, `07-runtime.js:137`; seeded and imported items are not checked) |
| notes | `x.notes` | |
| audio buttons with audio | "▶ Play", "Replace file", "Remove audio"; meta line `name · size (fmtBytes) · resume m:ss / dur` | `x.audio.*`, `fmtBytes` `07-runtime.js:200-205` |
| audio button without audio | "🎧 Attach .m4b / audio" | |
| Edit, Remove | buttons | |

### 3.3 Media form (`views/media.html:13-27`)

| Field | Control | Limits / default |
|---|---|---|
| Title * | `#mediaTitle` text | required, maxlength 160 |
| Type | `#mediaType` select | Book (default), Audiobook, Article, Course, Podcast, Video, Documentary, Other |
| Author / creator | `#mediaCreator` | maxlength 120 |
| Status | `#mediaStatus` | `unread` "Want to read / watch" (default), `progress`, `finished` |
| Link | `#mediaLink` `type=url` | maxlength 500; must start with http:// or https:// |
| Notes | `#mediaNotes` textarea | maxlength 1000 |
| heading | "Add to your library" / "Edit media item" | `07-runtime.js:122` |

### 3.4 Audio player (`index.template.html:74-107`)

| Element | Shows | From |
|---|---|---|
| `#audioPlayerTitle` | item title (or file name, or "Audiobook") | `08-runtime.js:60` |
| `#audioPlayerSub` | `file name · size` ("Ready" before any file) | `08-runtime.js:61` |
| `#audioTimeCur`, `#audioTimeDur` | current time, duration ("—" until known) | `updateAudioTimes` `08-runtime.js:117-126` |
| `#audioSeek` | 0..1000 position | same |
| `#audioPlayBtn`, `#audioMiniPlayBtn` | Play/Pause, ▶/❚❚ | `08-runtime.js:80-89` |
| `#audioRate` | 0.8×, 1× (default), 1.25×, 1.5×, 1.75×, 2× | not saved; after switching files the element's rate probably resets to 1 while the select keeps its old value **(unverified)** |
| `#audioMinBtn` | — (minimize) / □ (expand) | `08-runtime.js:15-33` |

### 3.5 Journal hero (`jcHeroHtml` `47-journal-coach.js:59-73`, into `#journalHero` by `04-runtime.js:62`)

| Element | Shows | Computed from |
|---|---|---|
| eyebrow, title | "YOUR CHRONICLE", "Journal" | static |
| "Backup" ghost button | | calls `exportData()` |
| day streak | `streak()` | `05-runtime.js:13`: days with non-passive activities, rest days, shield days, back from today, stopping at two missed days |
| days this month | days of the current calendar month (within the last 31) with `jcDay(d).n>0` | `n` counts activities that are neither coach nor passive (`47-journal-coach.js:33`, `ecoPassive` `43-economy.js:12`) |
| XP this week | sum of `jcDay(d).xp` over the last 7 days including today | `jcDay.xp` excludes coach XP but includes passive XP (steps, sleep) (`47-journal-coach.js:33-34`) |
| reflections | count of non-empty `state.dayReflections` | |
| "Last 4 weeks" strip | 28 cells (14 per row), oldest first; colour = archetype colour (`JC_ARCH` `47-journal-coach.js:75-79`), opacity `min(1, .35 + xp/max(1,avg*1.6)*.65)`; grey when no archetype; today outlined; tooltip `Mon d: N XP` | `jcArch`, `jcDay` |

### 3.6 Journal day list (`jcListHtml` `47-journal-coach.js:281-300`)

Built from `journalDays()` (`04-runtime.js:77-88`, newest first) merged with `jcReviewList` (`47-journal-coach.js:267-280`).

| Element | Shows | From |
|---|---|---|
| day tile | weekday short (or "Today"), date number, bar width `max(6, xp/maxXp*100)`% over the listed days, pencil glyph if a reflection exists, XP number; colour = archetype; classes `on` (selected) and `today`; aria-label "{Today/Yesterday/Mon, Oct 3}, N XP" | `jcArch`, `jcDay`, `journalDayLabel` `04-runtime.js:92-101` |
| quiet separator | "N quiet days" between two listed days more than 3 days apart | `47-journal-coach.js:292` |
| review row | "W" + "Week 42" + "weekly review", or "M" + month name + "monthly chronicle" | `47-journal-coach.js:287-290` |

Every day ever logged is listed, with no paging. On a years-long save this is hundreds of tiles rebuilt on every `save()`.

### 3.7 Journal day page (`renderJournalDay` `04-runtime.js:148-179`, `jcTop` `47-journal-coach.js:248-255`)

| Element | Shows | From |
|---|---|---|
| day head | big date number, weekday, "Month YYYY", **"N XP today"** (also for past days) | `totalXP` = sum of `a.xp` over `actsForDay(d)`, which **includes coach XP**, unlike the tile and hero (`04-runtime.js:152,174`) |
| meta | "N entries" / "N entry", "M exercise notes" | `actsForDay`, `planLogsForDay` `04-runtime.js:129-147` |
| care note (distress) | fixed text with a 988 crisis line | `jcCareNote` `47-journal-coach.js:209` |
| welcome card | moon art (inline SVG), heading, body with gap days, cat name, level; chips per tier: tier 1 "Pet {cat} · +10 XP" or "Claim +10 XP"; tier 2 "One-sentence catch-up · +20 XP" and "Open Life Director"; tier 3 "Start fresh · +25 XP" | `jcWelcome` `47-journal-coach.js:211-233`, cat from `petData()` (`15-runtime.js:62`), level from `characterLevel()` |
| insight card | archetype badge label + one sentence chosen by a hash of the date (stable), optional pattern line | `jcInsight` `47-journal-coach.js:105-111`, texts `JC_TXT` `:93-104`, pattern `jcChip` `:196-206` from `jcPatterns` `:154-195` |
| reflection box | prompt ("Reflect" + question, ↻ swap button) or plain label; textarea (maxlength 4000, placeholder "How did today feel?…"); meter bar; counter text: "+15 XP & +2 bond claimed" / "Saves for +15 XP" / "Write 40+ characters for +15 XP (n)" / "Reflections on older days earn no XP" | `jcReflectBox` `47-journal-coach.js:256-263`, `jcCounter` `:147-152`, prompt `jcPrompt` `:121-127`, pools `JC_POOLS` `:113-120` |
| timeline | per activity, oldest first: time, coloured node (attribute colour `JC_ATTR_COL` `47-journal-coach.js:56-57`), icon (`iconHtml` `06-runtime.js:86`), name, attribute pill, duration, skill if it differs from the name, "+N XP", notes; under a `plan` activity the workout notes (exercise label · weight — note) | `actsForDay`, `planLogsForDay` (labels from `planDay` when available) |
| "Workout notes" card | plan notes on a day with no `plan` activity | `04-runtime.js:167-169` |
| empty day body | "Nothing logged this day yet" + "Start with an activity…" (today) or "A blank page is still a page." | `04-runtime.js:156-157` |
| animation | each child fades up (`jxIn` .45 s, staggered), timeline items staggered by index | `07-finishing.css:213-216,252` |

Coach inputs read by `jcDay` (`47-journal-coach.js:31-50`): activities by local date (cached by `activities.length|today`), the 14 prior days (average XP of active days, default 150, or the tier-3 reset baseline 120), Life Director minutes scheduled and done (`LifeDirector.coach.dayStats`, `features/life-director/logic.js:5109-5126`), quest count, cat meals and pets (`petData()`), `state.restDays`, the reflection.

### 3.8 Journal review page (`jcReviewHtml` `47-journal-coach.js:304-349`)

| Element | Weekly | Monthly |
|---|---|---|
| head | "W42", "Week 42: N XP across k/7 days", date range | "OCT", "October Chronicle · Level a to b", date range |
| chart | 7 bars (archetype colours, value labels, narrow weekday) | heat grid Monday-first with leading blanks, opacity by XP, day numbers |
| grid | XP (+/-% vs prior week), Director sync (avg alignment, sharpest day), Top stat (share), Companion (days with cat) | Total XP, Levels gained, Quests cleared, Hours on the dial, Reflections written, Best day, Quiet stretches returned from |
| coach card | synthesis by change vs last week | second-half momentum and top stat |
| question + textarea | "What is ONE adjustment…" (maxlength 2000) | "If October was a chapter…" |
| counter | "Write 50+ characters for +30 XP" / "+30 XP claimed" | 60+ / +50 XP |

### 3.9 Lock cover and Settings card

- Cover: lock icon (inline SVG), "Journal locked", "Your reflections are private. Unlock with your fingerprint or screen lock.", Unlock button, error line (`48-journal-lock.js:19`).
- Settings, "Voice & journal" category (`50-settings-tabs.js:4`), card "Journal privacy" (`views/settings.html:55-58`): "Lock the Journal" checkbox, explanation, message line (`jlSettingsRender` `48-journal-lock.js:54-76`).

---

## 4. Actions

Every handler below runs in the web page. "save()" means the core save path (`04-runtime.js:13`): rebuild XP, economy sync, badges, level-up check (celebration and level-up overlay on any XP gain, `05-runtime.js:8-12`), write `lifemax_rpg_prototype_v1`, `renderAll()`; plus the wrappers `33-android.js:182` (native sync in 2.5 s), `50-quests-hand.js:153` (quest auto-verify, which ignores coach activities, `50-quests-hand.js:135`) and `50-native-nutrition.js:621-628` (Food screen refresh). "Direct write" means `localStorage.setItem(STORE, JSON.stringify(state))` without `save()`: no rebuild, no render, no failure message.

### 4.1 Library switch

| Action | Handler | Reads / writes | Side effects |
|---|---|---|---|
| Tap "Media" / "Journal" | inline `lmaNavTo('media'|'history')` (`views/media.html:2`, `views/history.html:2`) | writes `state.libPane` in memory (persisted by the next save or by `markVisit` every 30 s, `05-runtime.js:66,71`) | view transition slide (`34-navigation.js:31-51`); Back stack push (`34-navigation.js:84-87`); Journal: lock prompt if due (`48-journal-lock.js:43-46`); thumbnail scheduled (`42-scrubnav.js:209`) |

### 4.2 Media

| Action | Handler | Reads | Writes | Side effects / opens |
|---|---|---|---|---|
| "＋ Add media" / "＋ Add your first item" | `showMediaForm()` `07-runtime.js:120-131` | | form DOM | form card shown, smooth scroll, focus title after 250 ms |
| Edit | `showMediaForm(state.mediaItems.find(...))` inline (`11-runtime.js:37`) | item | form DOM | same, filled |
| Cancel (two buttons) | `hideMediaForm()` `07-runtime.js:132` | | form reset | |
| Save item (submit) | `saveMediaItem(e)` `07-runtime.js:133-143` (reads the form DOM) | form | `state.mediaItems` (new item `{id:'media_<ms>_<rand>',title,type,creator,status,link,notes,updatedAt}` unshifted, or merged into the edited item keeping `audio`, `finishedAt`); `save()` | toasts "Add a title first", "Use a valid http or https link", "Added to your library" / "Media item updated"; then **raw `navigate('media')`** (runs every navigate wrapper again, scrolls to top) |
| Search / filters | `oninput`/`onchange` -> `renderMedia()` (`views/media.html:30-32`) | DOM inputs | none | list redraw |
| Finished checkbox | `toggleMediaFinished(id,checked)` `07-runtime.js:144-148` | item | `status` = `finished` or `progress` (never back to `unread`), `updatedAt`; does **not** set `finishedAt`; `save()` | toast "Marked as finished" / "Moved back to in progress" |
| Open link | `<a href target=_blank>` | `x.link` | none | leaves the app to the browser **(Capacitor behaviour, unverified)** |
| Remove | `deleteMediaItem(id)` `07-runtime.js:153-157` | item | `confirm('Remove "…" from your media library?')`; closes the player if it is this item; `audioDbDelete(id)` (not awaited); removes the item; `save()` | toast "Removed from library" |
| Attach / Replace file | `pickAudioForMedia(id)` `07-runtime.js:207-218` -> `attachAudioFile` `:219-249` | hidden `#audioFileInput` (accept audio, .m4b, .m4a, .mp3, .aac, .ogg, .wav) | IndexedDB `lifemax_audio_v1/files[id]={blob,name,type,size,updatedAt}`; `item.audio={name,size,type,position,duration}` (position and duration kept on replace); type becomes Audiobook if it was Book, Other or empty; status `unread` -> `progress`; `save()` | system file picker; toasts; then plays from the start. `input.click()` needs a user gesture in the WebView, so it cannot be triggered from a native callback **(likely; unverified)** |
| Remove audio | `removeMediaAudio(id)` `07-runtime.js:250-259` | | `confirm(…)`; closes player if playing it; deletes blob; deletes `item.audio`; `save()` | toast "Audio removed" |
| ▶ Play | `playMediaAudio(id)` `08-runtime.js:34-79` | item, IndexedDB blob | object URL; `audioCtx`; on metadata `item.audio.duration` (memory) | player shown expanded, resumes at the saved position if > 5 s; see 2.2 for the missing-file path (which deletes `item.audio` and saves) |
| (dead code) | `setMediaStatus(id,status)` `07-runtime.js:149-152` | | `status`, `save()` | no caller found |

### 4.3 Audio player

| Action | Handler | Writes | Notes |
|---|---|---|---|
| Play/Pause (two buttons) | `audioTogglePlay()` `08-runtime.js:90-95` | position via `persistAudioPosition()` (debounced direct write) | |
| −15 s, +15 s, +30 s | `audioSkip(sec)` `08-runtime.js:96-100` | forced direct write | |
| Seek drag / release | `audioSeekInput` `:105-110`, `audioSeekChange` `:111-116` | forced direct write on release | |
| Speed | `audioSetRate(v)` `:101-104` | none | not persisted |
| Minimize / expand | `toggleMinimizeAudioPlayer()` `:15-26` | none | |
| Close ✕ | `closeAudioPlayer()` `:4-14` | none (position was written on pause) | stops and unloads |
| `timeupdate` (several times a second) | `wireAudioElement` `:143` | `item.audio.position` in memory; restarts the 1.2 s debounce each time, so **during continuous playback the debounced write never fires**; the position reaches storage on pause/skip/seek, or through `markVisit` every 30 s (`05-runtime.js:66,71`) | wired once at boot (`13-runtime.js:115`) |
| `pause` | `:145` | forced direct write | |
| `ended` | `:146-167` | finished + `finishedAt` + `save()` | toast "Book finished · {title}" |

Each forced write serializes the whole core save (years of history) to localStorage.

### 4.4 Journal

| Action | Handler | Reads | Writes | Side effects / opens |
|---|---|---|---|---|
| Tap a day tile or review row | `selectJournalDay(key)` `04-runtime.js:108` -> `renderHistory()` | | `histSel` (memory). Drawing the newly selected day can write coach state (see 5.3): `pats`/`cooldown` + `save()` for a recent day not decided yet; `days[d]` record; `welcome[today]`; `reviews[key]` | entry fades in |
| Type in the reflection | `oninput` -> `jcCounter(this,d)` `47-journal-coach.js:147-152` | | none | counter and meter |
| Leave the reflection (blur) | `onchange` -> `saveDayReflection(d,val)` `04-runtime.js:109-128` | | `state.dayReflections[d]` = trimmed text, or deleted if empty (no length cap in code; the textarea caps at 4000); **direct write, failure ignored silently**; soft redraw of the day list; then `jcOnReflect(d,t)` `47-journal-coach.js:140-146`: if today or yesterday, >= 40 characters and not yet paid, `jcAward(15,'Journal reflection')` (activity `{type:'coach',skill:'Reflection',primary:'wellbeing',xp:15,src:'coach'}`), `jcCare(2)` (+2 bond to the living cat via `petGain`), `xpAwarded=true`, `save()` | toast "+15 XP · +2 bond · Reflection saved"; XP celebration; may earn activity-count badges (`05-runtime.js:15-17` count every activity, coach ones included). Shortening or deleting the text later does not take the XP back |
| ↻ Swap question | `jcSwap(d)` `47-journal-coach.js:128-131` | | `journalCoach.days[d].promptId` (next in pool); `save()`; `renderHistory()` | |
| Welcome "Pet {cat} · +10 XP" / "Claim +10 XP" | `jcClaim('pet')` `:234-239` | | `petPetDone()` (counts one of 3 daily pets, `15-runtime.js:96-99`); coach activity +10; `welcome[today].claimed.pet`; `save()`; `renderHistory()` | toast "+10 XP · Welcome back" |
| "Start fresh · +25 XP" | `jcClaim('fresh')` | | coach activity +25; `claimed.fresh`; `save()` | toast "+25 XP · New chapter" |
| "One-sentence catch-up · +20 XP" | `jcCatchUp()` `:240-246` | | **`window.prompt()`** dialog; >= 12 characters appended to today's reflection (cut to 4000); coach activity +20; `claimed.line`; `save()` | toasts |
| "Open Life Director" chip | inline `lmaNavTo('life-director');LifeMaxFeatures.open(...)` `:226` | | | navigation |
| Review answer (blur) | `jcSaveReview(key,val)` `:350-355` | | `journalCoach.reviews[key].answer` (cut to 2000); first time >= 50 (weekly) / 60 (monthly) characters: `paid=true`, coach activity +30 / +50; `save()`; `renderHistory()` | toast "+30 XP · Review saved" |
| "Backup" in the hero | `exportData()` `04-runtime.js:260` -> `lmaDownload` `33-android.js:171-179` | whole save | none | native FileSaver dialog; the file contains every reflection in plain text |
| Dismiss tip | `dismissFeatureTip('history')` `13-runtime.js:76-81` | | `tipsSeen.history`; direct write | |
| Tile strip scroll | browser | | | the strip blocks tab swipes while it overflows (`34-navigation.js:102-103`) |

There is no edit or delete of activities in the Journal: the timeline is read-only.

### 4.5 Journal lock

| Action | Handler | Writes | Side effects |
|---|---|---|---|
| Unlock | `jlPrompt()` `48-journal-lock.js:34-41` (button listener `:21`) | `jlUnlocked` (memory) | system BiometricPrompt (`BiometricLockPlugin.java:47-72`); on success `jlApply()` + `renderHistory()` |
| Open the Journal while locked | `jlOnNavigate` `48-journal-lock.js:43-46` | | prompt starts automatically |
| Leave the Journal | `jlOnNavigate` `:47-51` | `jlLeftAt` | `FLAG_SECURE` off |
| App hidden | `48-journal-lock.js:80` | `jlUnlocked=false` | cover back (no re-render) |
| Settings toggle on | `48-journal-lock.js:59-68` | `state.journalLock={on:true}`, `jlUnlocked=true`, deletes thumbnails `history` and `library`, `save()` | status check, confirm prompt |
| Settings toggle off | `:69-73` | `state.journalLock={on:false}`, `save()` | confirm prompt |

---

## 5. Cross-tab couplings

### 5.1 Navigate wrappers that matter here

`navigate` is reassigned 21 times at this baseline (20 sites; NativeTabKit wraps once per tab that uses it). The ones that change Library behaviour, innermost first:

| Wrapper | What it does for Library |
|---|---|
| base `04-runtime.js:49` | toggles `.view.active`, scrolls to top, feature tip, `petSync()`. Does **not** re-render Media or the Journal (they only redraw on `save()`/`renderAll`, filter input, or unlock) |
| `34-navigation.js:77-91` | Back stack, `lmaSyncActive` (lights `library`), bar scroll |
| `42-scrubnav.js:198-212` | writes `state.libPane`, lights the `library` button and the `.libseg` pill, schedules a thumbnail (`lmaThumbSnapNow` refuses a locked Journal, `42-scrubnav.js:31`) |
| `48-journal-lock.js:79` | records the previous view, then `jlOnNavigate`: relock after grace, cover, prompt, `FLAG_SECURE` |
| `50-native-overview.js:146` | `nativeView(v)` resolves `library` through `libPane`; closes the Overview when leaving |
| `50-native-tabkit.js:191-201` (Character, Log) and `50-native-nutrition.js:588` | close their screen when you go to `media`/`history` (web destination: immediate close, A23 item 2) |

Because nothing redraws on navigation, the Journal's time-dependent text ("Today", the welcome card, the 3-day chip window, reviews that unlock at 20:00) goes stale while the app stays open until some `save()` happens **(from code reading)**.

### 5.2 Who else reads or writes this tab's data

| Data | Other writers / readers |
|---|---|
| `state.activities` (the Journal's main input) | Log (`recordActivity` `04-runtime.js:191`), quests (`toggleQuest` `:193`), attribute sheets (`confirmAct` `07-runtime.js:103-112`), plan, Life Director completions (`features/life-director/logic.js:2523`), health sync, money deposits, undo (`deleteActivity` `04-runtime.js:197`). The coach adds `type:'coach'` activities |
| `state.plan.checks[d].log` / `.v` | Workout plan tab; shown as workout notes (`04-runtime.js:81-86,129-147`) |
| Life Director schedule | read for the dial numbers (`47-journal-coach.js:43`). The feature script loads after the main script, so the boot render and the coach's own redraw happen before `LifeDirector` exists: dial-based archetypes appear only after the next `save()` **(from code reading)** |
| cat (`state.pet`/`state.pet2`) | read for care and names; written by `jcCare` (`petGain`) and `jcClaim('pet')` (`petPetDone`) |
| `state.restDays`, `state.shieldDays` | economy (`43-economy.js:66-68`); read for archetype and streak |
| `state.mediaItems` | voice reads it (`50-voice-gen4.js:388`, `50-voice-gen5.js:225`); nothing else writes it |
| `state.dayReflections`, `state.journalCoach`, `state.journalLock` | only `04-runtime.js`, `47-journal-coach.js`, `48-journal-lock.js`; exported by every backup (`backupJson` `04-runtime.js:259`) |
| Overview | links to the Journal (`18-runtime.js:49`); its "Recent activity" shows the same activity names and XP without any lock |
| Voice | "Your log" lists activity names and XP for past days without the lock (`46-voice-nlu.js:285-296`); never reads reflection text (verified: `dayReflections` appears only in the two Journal segments) |
| Badges | "First Step", "Getting Going", "Habit Formed" count all activities, so coach rewards count toward them (`05-runtime.js:15-17`) |
| Quest auto-verify | excludes coach activities (`50-quests-hand.js:135`) |

### 5.3 The coach writes the save while drawing

| Helper | Called from | Writes | Calls `save()` |
|---|---|---|---|
| `jcChip(d)` `47-journal-coach.js:196-206` | `jcTop` for the selected day | `journalCoach.pats[d]` and `cooldown[id]`, once per day, only for days from today-3 to today | **yes** (`:204`), from inside `renderHistory` -> nested `renderAll` |
| `jcWelcome(d)` `:211-233` | `jcTop` (today only) | `journalCoach.welcome[d]`; tier 3 also `journalCoach.base` | yes for the tier-3 baseline (`:216`) |
| `jcPrompt(d)` `:121-127` | `jcTop` | creates `journalCoach.days[d]={}` | no |
| `jcReviewHtml(key)` `:304-349` | `renderHistory` for a selected review | creates `journalCoach.reviews[key]={}` | no |
| redraw at load `47-journal-coach.js:358` | boot | the first chip of a recent day | yes: on the first launch of each day with recent data, the coach saves the core save during start-up (before segments 48+ have loaded) |

`jcChip` returns early once `pats[d]` exists, so the nested render does not loop. A native projection must not call these helpers as they are (see 7.3).

### 5.4 Journal lock versus native screens (BACKLOG SEC-2)

Confirmed at this baseline:

1. `NativeTabKit.blocked()` checks view, onboarding, top overlay, `noscroll` and the tab's own `cfg.blocked` only (`50-native-tabkit.js:55-57`). No lock check.
2. `lmaTopOverlay()` (`34-navigation.js:54-67`) does not include the lock cover. It **must not** be added there as it stands (BACKLOG L1 suggests adding `.jl-overlay`): `lmaBack` would call its `close()` (`34-navigation.js:70`) and every touch on the locked Journal would stop tab swipes (`34-navigation.js:139`). A separate predicate is needed.
3. The Native Overview's blocking list names `.lock-ov` and `.journal-lock` (`50-native-overview.js:76`). Neither class exists (the cover is `.jl-overlay`, the section gets `.jlocked`). Harmless today because the Overview only covers `dashboard`, but do not copy it.
4. The page relocks on `visibilitychange` to hidden (`48-journal-lock.js:80`). NativeTabKit reacts only to visible (`50-native-tabkit.js:210-212`), and `ComposeTabPlugin.handleOnResume` asks for a refresh (`ComposeTabPlugin.kt:132-134`). A native Journal would show its last frame on return until the page answers.
5. A hidden native screen stays composed with its last data (`ComposeTabPlugin.kt:191-196`, A24).
6. `FLAG_SECURE` is window-wide (`BiometricLockPlugin.java:85`), so it also covers a native screen in MainActivity, but only while the page's view is `history` and the lock is on (`48-journal-lock.js:32`).
7. Thumbnails: `PageThumb` draws the WebView only (`android-wrapper/native/PageThumbPlugin.java`), and a locked Journal is never captured (`42-scrubnav.js:31`); turning the lock on deletes the old `library`/`history` thumbnails (`48-journal-lock.js:67`).
8. Added in phase 0: `LifeMaxJournalLock.blocksNative()` (lock on and not unlocked) and `onChange(fn)`, the seam a native screen showing journal text follows (section 9). Items 1 and 4 to 6 still stand for that screen.

### 5.5 Timers, observers and global listeners touching this tab

| Listener | Effect here |
|---|---|
| `setInterval(markVisit…,30000)` `05-runtime.js:71`, and on visible `:72` | direct write of the whole state every 30 s: persists `libPane`, the in-memory audio position, coach records |
| `visibilitychange` -> `applyDecay` + `save()` `11-runtime.js:41`; health sync on visible after 15 min and every 30 min (`39-health.js:104,132-133`); `payTick` on visible and every minute, saving when a paycheck schedule changes (`36-money-pay.js:55-66,130-131`) | each `save()` redraws the Journal (see the risk below) |
| `visibilitychange` hidden -> relock `48-journal-lock.js:80` | |
| lock boot timer 1.8 s `48-journal-lock.js:81` | applies the cover, draws the Settings card |
| `<audio>` events `08-runtime.js:143-168` | live progress, position writes, finish |
| NativeTabKit 15 s refresh `50-native-tabkit.js:170` | would re-project a Library screen |

**Data-loss risk (from code reading; not reproduced on device). Fixed in phase 0, see section 9.** The reflection textarea saves only on `change` (`47-journal-coach.js:261`). Any `save()` before the owner leaves the field (health sync, decay, a payment, a coach award from another action) or a successful unlock (`48-journal-lock.js:38`) replaces `#journalEntry` (`04-runtime.js:74`) with the stored text, so what was being typed is lost. Leaving the app does not necessarily blur the field. The same applies to review answers.

---

## 6. Persistence

All Library state is in the core save `lifemax_rpg_prototype_v1` (`STORE`, `01-runtime.js:46`) except the audio files.

| Key | Schema | Writers | Notes |
|---|---|---|---|
| `mediaItems` | array of `{id, title, type, creator, status:'unread'|'progress'|'finished', link, notes, updatedAt?, finishedAt?, audio?:{name, size, type, position (s), duration (s)}}` | `07-runtime.js:133-157,219-259`, `08-runtime.js:53,127-167`, `11-runtime.js:8` | in `freshState` (`04-runtime.js:9`); seeded items have no `updatedAt` |
| `mediaSeededV1` | boolean | `seedReadingList` `04-runtime.js:1-8`, run by every `load()` (`04-runtime.js:12`) | the only Library migration. Not in `freshState`: after "Reset" or "Delete all" (`04-runtime.js:266-267`), or importing a backup that predates the flag, the next launch adds the default books again (skipping matching titles) |
| `dayReflections` | `{ 'YYYY-MM-DD': string }` (textarea max 4000; not enforced in code) | `04-runtime.js:109-114`, `47-journal-coach.js:244` | created lazily |
| `journalCoach` | `{days:{[d]:{promptId?, xpAwarded?}}, reviews:{['W:YYYY-Www'|'M:YYYY-MM']:{answer?, paid?}}, cooldown:{[patternId]:d}, pats:{[d]:{id,text}|0}, welcome:{[d]:{tier,gap,claimed:{pet?,line?,fresh?}}}, base:{from,until,value}|null}` | `47-journal-coach.js` throughout | never pruned; grows by about one `pats` entry per day plus a record per day or review viewed |
| `journalLock` | `{on:boolean}` | `48-journal-lock.js:66,72` | travels with backups (see 2.5) |
| `libPane` | `'media'|'history'` | `42-scrubnav.js:204` | a UI preference stored in the gameplay save |
| `tipsSeen.media`, `tipsSeen.history` | boolean | `13-runtime.js:76-81` | |
| activities `type:'coach'` | `{id, type:'coach', name, icon:'book', duration:0, quality:1, skill:'Reflection', primary:'wellbeing', secondary:'', xp, date, notes, xpSplit:[{attr:'wellbeing',xp}], src:'coach'}` | `jcAward` `47-journal-coach.js:133-138` | passive: no streak or training day (`43-economy.js:12`); bypasses `ecoAward` caps; Sparks follow through `ecoSparksSync` in `save()` |

Other stores:

| Store | Content | Notes |
|---|---|---|
| IndexedDB `lifemax_audio_v1` (version 1), object store `files`, key = media id | `{blob, name, type, size, updatedAt}` | `07-runtime.js:160-193`. Soft limit 512 MB per file. **Not in any backup** (`LMA_EXTRA_KEYS` `33-android.js:167`, folder backup `35-android-upkeep.js:127-133` writes `backupJson()` only), so files are gone after a reinstall or a new phone. **Not cleared** by Reset, Delete all or Import (`04-runtime.js:264-267`): orphaned files stay and use storage. Deleting an item deletes its file |
| `lifemax_thumbs_v1` (localStorage) | JPEG data URL per tab key, including `library` | `42-scrubnav.js:25-28` |
| `lifemax_android_prefs_v1` | where a future `nativeLibrary` toggle would live (`lmaLoad`, `33-android.js:25-32`) | |
| memory only | `histSel`, `jlUnlocked`, `jlLeftAt`, `jlPrompting`, `jlPrevView`, `audioCtx`, coach cache `jcCache`, form and filter DOM state | |

No schema version exists for any of this (data-safety backlog). Sizes: the media list is small (15 seeded items, about 3 KB). Reflections are up to about 4 KB per day, so a years-long journal can reach megabytes. The owner's real sizes are unknown (no personal save was read); measure before projecting (see 7.3).

---

## 7. Native readiness

### 7.1 Easy

- Media stats, list rows, status pills, notes, filters and search (native-local state), empty states. The list is short; use a `LazyColumn` with item ids as keys.
- Journal hero numbers, the 28-cell strip, tiles (`LazyRow` with date keys), quiet separators, review rows, the day timeline, insight and welcome cards, the weekly bar chart and the monthly heat grid (small Compose drawings), the lock card.
- Colours: archetype colours (`JC_ARCH`), attribute colours (`JC_ATTR_COL`) and icons (`iconHtml` resolves through `ICONS`/`EMOJI_MAP`, `06-runtime.js:85-86`; native already has `core/designsystem/components/PixelIcon.kt`) can be projected as resolved values.

### 7.2 Hard

- **Lock integration** (5.4): opening rule, redaction on hide and stop, refresh after unlock, `FLAG_SECURE` ownership.
- **Audio.** Blobs in IndexedDB cannot reach native without a storage move (hundreds of MB cannot cross the bridge; `ComposeTabPlugin.MAX_SNAPSHOT_CHARS` is 2,000,000, `ComposeTabPlugin.kt:208`). There is no background playback, notification or lock-screen control today, and whether the WebView keeps playing with the screen off is **unverified**. Attaching needs a user tap in the WebView. A native player (Media3 with a media session) is a data and logic move, so ADR 0004 applies.
- **Text input.** Reflections (4000), review answers (2000), the media form, `prompt()` for the catch-up. Native shared text field, IME and keyboard insets are missing (BACKLOG DS-5, NAV-5).
- **Render-time writes** in the coach (5.3).
- **Time.** "Today/Yesterday", reward eligibility (today or yesterday), the 3-day chip window, 20:00 review cutoff, week ids (ISO week, `47-journal-coach.js:265`). The projection must be recomputed on the 15 s timer and on resume, not cached by `activities.length` alone (`jcIndex` keys its cache on count and today, `47-journal-coach.js:23-24`).
- **Long lists.** Every day ever logged becomes a tile; `jcArch` runs per listed day on every refresh.
- **Two views in one tab.** `NativeTabKit.create` takes one view id (`cfg.id`, used by `blocked()`, the navigate wrapper and `NativeTabs.register`). Two kits sharing one plugin would both receive every `action` event and both close (`50-native-tabkit.js:133-138`). Use one plugin per view (for example `NativeMedia` and `NativeJournal`), or extend the kit to accept a set of views.
- **The care note** (distress regex and crisis text) must keep exactly its current wording and its precedence over everything else on the page.

### 7.3 Web dependencies that stay delegated at first

Media form (add/edit), delete confirm, attach/replace/remove audio, play and the whole player, link opening (until native re-checks the scheme), the reflection editor and counter, review answers, prompt swap, welcome claims, the catch-up prompt, Backup export, Unlock (the biometric prompt is started by the page), tip dismissal, the lock Settings card.

Before any Journal projection, split pure data builders from the HTML builders (`jcHeroHtml`, `jcListHtml`, `renderJournalDay`, `jcTop`, `jcReviewHtml`), and split `jcChip` into "peek" (read `pats[d]`, or compute without storing) and "decide and store". Until then, the projection reads only what the page's own `renderHistory` already stored for `histSel`. Do not page the full reflection history through the bridge: send the tile summary and only the selected day.

### 7.4 Proposed phasing

**Phase 0, web only (small, testable, no native).** Done; section 9 says how.
1. Keep the focused textarea's text across `renderHistory` (fixes the data-loss risk), and report a failed reflection write instead of ignoring it (`04-runtime.js:114`).
2. A lock predicate for native screens, for example `LifeMaxJournalLock.blocksNative()` = `jlOn() && !jlUnlocked`, plus a hook the kit can follow on unlock and relock.
3. `mediaUpsert(fields)` as a DOM-free owner function split from `saveMediaItem` (the same split Log made with `activityDraftFromForm`/`recordActivity`).
4. Fix the voice mapping so "journal"/"history" open the Journal (`46-voice-nlu.js:37`).
5. Media tests (only the voice search is tested today): add, edit, finish, delete, filters, and the seed-once rule.

**Phase 1, native Media, presentation only.** Journal stays web: the native switch's "Journal" emits `navigate` `history`, a web destination, so the native screen closes at once and the web lock does its job unchanged.

The web half is built (`50-native-library.js`, section 9); the sketch that stood here wrapped `hideMediaForm` and `closeAudioPlayer` by reassigning them, which the lint ratchet forbids, so the screen follows the page through observers and flags instead. What the screen gets, every string worded as the page words it:

```
{
  protocol: 1, webSessionId, sequence, tokens, light,          // from NativeTabKit
  panes:  [{id:"media", label:"Media", current:true}, {id:"history", label:"Journal", current:false}],
  header: {eyebrow, title, blurb, add},
  tip:    null | {icon, title, body, dismiss},                 // FEATURE_TIPS.media while tutorialDone && !tipsSeen.media
  stats:  [{label, value, foot, bar}] x 4                      // value is text ("17%"); bar is null, or the Completion percent
  search: {label, placeholder},
  statusFilter: {label:"Show", options:[{id, label}]},         // the page's own select: all, unread, progress, finished
  typeFilter:   {label:"Media type", options:[{id, label}]},   // all and the 8 types
  filter: {search, status, type, revision},                    // the page's filter; revision rises when the page sets it
  items:  [{id, title, type, creator, subtitle, status, statusLabel, finished, notes, link,
            audio: null | {file, meta, progress:{pct, label, time}}}],
  empty:   {icon, title, body, add},
  noMatch: {icon, title, body},
  buttons: {edit, remove, play, replace, removeAudio, attach, link},
  player:  {mediaId, visible, playing},                        // idle whenever the screen covers the pane
  notice:  null | {id, text}                                   // the page's toast while it shows; id counts the page's toasts
}
```

`type` and `status` are as stored, so the screen's filter compares exactly what the page's does (an item without a type shows "Other" but no type filter matches it); `link` is "" unless http or https. Actions, all delegated (the kit closes the screen, then the page's handler runs, then `revisit()`): `add`, `edit {id}`, `finished {id}` and `unfinished {id}` (a native action carries only a name and an id, so the tick's direction is in the name), `remove {id}`, `play {id}`, `attach {id}` and `replace {id}` (the row's own file button shown, focused and named in a toast), `removeAudio {id}`, `link {id}`, `tip`, and `{view:"history"}` for the switch's Journal. Recorded in `BRIDGE_CATALOG.json` as `delegatedInteractions` with `writes: []`.

**Phase 2, Media commands (no close; the web handler stays the only writer).** `setStatus {id, status}` (idempotent set; `setMediaStatus` already exists, unused), `upsert {id?, fields, opId}` through `mediaUpsert`, `delete {id}` after a native confirm dialog. A native mini-player drawn from `player`, with commands `toggle`, `skip`, `seek`, `rate`, `close` mapped to the existing audio functions (the audio stays in the WebView). Then drop the `#audioPlayer` block. `mediaUpsert` takes no operation id yet: an add repeated after a lost answer would add the item twice, so the `upsert` command brings one, as `recordActivity` has.

**Phase 3, native Journal, read-only, behind the lock.** Only after the kit rule of SEC-2:
- `cfg.blocked()` adds `LifeMaxJournalLock.blocksNative()`; unlock success calls `revisit()`; on `visibilitychange` to hidden with the lock on, send a redacted snapshot (`{locked:true}`, no text) and close through the hand-over; the native screen clears Journal content on `ON_STOP` when its last projection said `lockOn`, and when hidden (A24).
- Pane switches go through `lmaNavTo('history'|'media')` so the grace minute and `FLAG_SECURE` keep working.
- Projection: `hero {streak, monthDays, weekXp, reflections, cells:[{d, color, opacity, today, title}]}`; `list [{kind:"day", d, weekday, date, xp, barPct, color, today, selected, hasReflection} | {kind:"quiet", days} | {kind:"review", key, title, sub, selected}]` (consider the most recent N days plus a "more" command); `entry` for the selected key only: a day `{head, xpToday, counts, care, welcome, insight, prompt, reflection:{text, canPay, awarded, label, meterPct}, timeline:[...], workoutNotes:[...]}` or a review `{head, range, chart, grid, synth, question, answer, paid, bonus, minLen}`.
- Commands: `select {key}` -> `selectJournalDay(key)` (the page stores whatever the coach decides; the projection then reads it). Everything that writes text or pays XP stays delegated (close, then focus the web field or run the web chip).

**Phase 4, Journal commands.** `saveReflection {d, text, opId}`, `swapPrompt {d}`, `claim {kind, opId}`, `saveReview {key, text, opId}`. Only after A14/A15: today `saveDayReflection` writes directly and ignores failure, and `jcOnReflect` saves separately, so a failure leaves memory and storage different. Text editing natively also needs the shared text field and IME work.

**Separate track.** Moving audio files out of IndexedDB (and into backups, or not) is a data move under ADR 0004, not part of the presentation phases.

---

## 8. Open questions for the owner

1. Which pane first? The proposal is native Media first (low risk), with the Journal staying web until the lock work for native screens is done.
2. What should the Journal lock protect? Today it hides the Journal page only. Activity names and XP also appear in the Overview and in voice answers, activity notes appear in the character report export (`05-runtime.js:86`), and the Backup button and Settings export write every reflection in plain text. Should the lock also cover exports?
3. When a backup with the lock on is restored to a phone without a fingerprint or screen lock, or opened in a browser, the Journal cannot be opened or unlocked there. Should the lock setting stay out of backups, or should there be a recovery path?
4. "Open my journal" by voice opens Media today. Should it open the Journal? *Done in phase 0 as the task asked: "journal" and "history" open the Journal; say if you want it back.*
5. Is the audiobook player used? Do you expect it to keep playing with the screen off and to show lock-screen controls? (Phase 1 keeps the Media pane on the web page whenever a book is loaded, so the controls never disappear under a native screen. Today the native Character, Food and Log screens do cover the player.)
6. Audiobook files are not in any backup and would be lost on a new phone or reinstall. Should they be backed up (they can be very large), or is a warning enough? Old files are also not removed by Reset or Delete all.
7. Today's reflection can only be written after something is logged (or a plan note is written) that day. Is that intended, or should today always be on the list?
8. Have you ever lost a reflection while typing (for example after switching apps, or after unlocking)? The code allowed it; the phase 0 fix keeps what you typed (a save from elsewhere no longer redraws the box you are in, and the lock lets go of the box, saving it, before it covers the Journal).
9. The day page's "XP today" includes reflection and review rewards; the tiles, hero and coach exclude them. Which should it be? The label also says "today" on past days.
10. Unticking "Finished" moves an item to "In progress", never back to "Want to read / watch". Keep that?
11. The lock re-locks one minute after leaving the Journal and immediately when leaving the app. Keep both for the native screen?
12. On the native Media screen, ticking an item finished closes the screen for a moment while the page saves it, then the screen comes back (a delegation until phase 2 makes it a command). Is that flash acceptable for the beta?
13. The page still draws a stored link whatever its scheme (only its form checks for http or https); the native screen shows only http and https links. Should the page drop the others too?
14. The native screen keeps its own search and filters while you add, edit or play from the page, and takes the page's filter only when the page sets one (a voice search). Is that what you expect, or should it start empty each time you open the Library?
15. Attaching an audio file from the native screen shows the row's own button on the page for you to tap, because Android opens a file picker only from a tap in the page; a toast says which button to tap ("Tap "🎧 Attach .m4b / audio" to choose the file"). Fine for now, or should attaching wait for a native picker (it needs the audio files moved out of the page's storage, a separate decision)?

---

## 9. What the Library work changed (phase 0 and phase 1)

Phase 0 and the web half are web only; the native screen of phase 1 is the last paragraph. Tests named here run in CI with the other web tests.

**Journal (phase 0).**
- `renderHistory` does not redraw the entry while its reflection or review box has the focus; it marks the entry stale and draws it once the box loses the focus. A save from elsewhere (health sync, decay, a payday, a coach reward) no longer replaces what is being typed. The day list and hero are still redrawn.
- Found while fixing it: hiding the Journal under the lock cover moves the focus to the page without a change event, so text typed before the app went to the background was still lost on the next unlock. `jlApply` now blurs a focused field inside the Journal before it covers it, which fires the field's own change and saves it.
- `saveDayReflection` reports a write that failed ("Could not save to this browser (storage full?)", or "Not saved: this version is read-only for your save") and then stops: no XP for a reflection that was not stored. The text stays in memory and in the box, so the next successful save stores it.
- `LifeMaxJournalLock.blocksNative()` is `jlOn() && !jlUnlocked`; `onChange(fn)` calls `fn(blocked)` each time that answer changes (unlock, relock after the grace minute, the app going to the background, the lock turned on or off). Every change of the lock state ends in `jlApply`, which notifies. Nothing uses it yet: it is the seam for a native Journal (5.4).
- Tests: `coach.test.js` (typing kept across a save and saved on leaving, for a day and a review; a failed and a read-only write reported with no XP; these checks fail on the old code), `lock.test.js` (the predicate and the notifications through relock, failed and good unlock, background and lock off; typing saved before the cover).

**Media (phase 0).**
- `mediaUpsert(fields)` adds an item, or changes the one with `fields.id`, by the form's rules (a title, an http or https link, and now the form's lengths: title 160, creator 120, link 500, notes 1000), saves, and returns `{ok, id, message}`; it never touches the page. A failed save is taken back out and reported. `saveMediaItem` reads the form and calls it; when the save fails the form now stays open with what was typed (it used to close and say "Added to your library"). Type and status are stored as given, as before: the form only offers valid ones, and an old item's odd type comes back from the form as "".
- One source for what both the page and the projection show: `MEDIA_STATUS` (the status labels), `mediaStats(items)`, `mediaFilterOnPage()` and `mediaMatching(items, filter)` (the filter, unchanged), `mediaAudioProgress(status, position, duration)` (the progress label, time and bar, now also used by the live update during playback) and `mediaAudioMeta(audio)` (the file line). Rows carry `data-media-id`.
- `audioCtx.loading` holds the id whose file `playMediaAudio` is reading. Between closing the old player and showing the new one the page used to look as if no player were there; the native tab reads this flag so it does not cover the pane in that gap.
- Ticking, removing and removing the audio check their save too (`toggleMediaFinished`, `deleteMediaItem`, `removeMediaAudio`): a save that fails takes the change back, redraws the list and says so instead of claiming success, and the audio file is deleted only once the save has stored its removal (a failed removal used to leave the item without its file). `unsavedMessage()` (04-runtime.js) is the one wording of a write that was not stored, naming a read-only save as the reason; the page's other writers that reported it use it too.
- Tests: `media.test.js` (35 checks; before, only the voice search touched Media).

**Voice (phase 0).** "journal" and "history" have their own entry in the section table, before Library's, and open the Journal; library, media and books open Media. A voice library search now sets the search box through an `input` event (the box's own handler redraws the list), so a native screen can tell that the page set its filter. Tests: `voice4.test.js`.

**Native Library, web half (phase 1).** `50-native-library.js` on `NativeTabKit.create` (view `media`, plugin `NativeMedia`, capability `library.media.projection.v1`, setting `nativeLibrary`, toggle `nativeLibraryToggle`, Settings "Native Library (beta)", off by default). The projection and actions are in 7.4. Covering: not while the form is open, the player is loading, loaded or showing, a file button handed to the owner (marked `data-media-file`) has the focus, a handed-back tap is running, or a level-up plays; the screen follows these through MutationObservers (the form's style, the player's class, the body's children, the list, which every save redraws) and a focusout, and comes back through `revisit()`; `blocked()` also reads `audioCtx.loading`. The page's toast, which lies under the screen, is projected as `notice` while it shows, numbered so that the same words twice are two messages; a toast while the screen shows is sent at once. The page's filter carries a revision raised by every `input` on the search box or the two selects. Fixtures from the page: `fixtures/media-{full,search,empty,tip}.json` and `fixtures/media-filter-parity.json`. Tests: `native-library.test.js` (37 checks) and the hand-over simulation. Not checked on the phone: that a link opened from a native tap reaches the browser (a script-made anchor click with `target=_blank`), and how the file picker behaves after the screen hands the button over.

**Native Library, the screen (phase 1).** `NativeMediaPlugin` (ComposeTabPlugin, `feature/library`, `app/LibraryContainer.kt`) draws the projection of 7.4 faithfully to the pane at phone width and filters it with the page's own rule; every tap goes to the page as listed there. What it draws, how it is proven and how it differs from the page: `MIGRATION.md`, Library.
