# Library's Journal pane and the Library sheets still on the page: native build plan

Baseline, read at the start of this plan (final version, after review):
- **main is 8ad7560.** It has merged track/integ, and `NativeApi.VALUE` is 29 on main.
- In-flight tracks:
  - **.wt/swipe** (f9a4b0d) has already merged main (084b191). It is at 29 and plans Native API 30 (`.wt/swipe/docs/architecture/MIGRATION.md:176`). It takes CONTRACT **A34** and **ADR 0011**.
  - **.wt/settings** (5e853d2) and **.wt/ld2** (c49e94f) are at 28 and were branched before integ.
  - This track lands after all three, so expect NativeApi to be **at least 31** by then. It is set to main's value + 1 at merge.
  - The next free numbers are **A35** and **ADR 0012** (and 0013 for phase 2). The other plans being written now may take them first, so numbers are fixed at merge.
- Line numbers are main's unless marked. `seg/` is `lifemax_modular_v73/src/runtime/segments/`, `css/` is `lifemax_modular_v73/src/styles/`, `kt/` is `android-wrapper/native/src/main/java/com/lifemax/rpg/`.
- Scratch checks:
  - a fresh /tmp build of main, `/tmp/jplan/main.html`;
  - the earlier probes `/tmp/jscratch/probes/{lockprobe.js, perf.js}` and `/tmp/jplan/probes/honesty.js`;
  - two probes written for this review, `/tmp/jfinal/probes/{lockpaths.js, selectcost.js}`.

**How the review was settled** (the fixes are written into the sections named; nothing below is left open)

| # | Review point | Verdict | Where |
|---|---|---|---|
| 1 | A hidden screen keeps open data when the lock is switched on while it is hidden, and swipe draws hidden screens | **Accepted.** Journal data is dropped whenever the screen stops being drawn, **whatever the lock state**, and the screen then draws a neutral resting state (the switch on the page background). One part is **rejected**: a showing screen keeps its frame on app stop while the lock is off. The lock can only be switched on from the Settings card (48-journal-lock.js:70-86 is the only writer of `journalLock`; checked by grep across src, tools and tests), so the Journal is never showing at that moment, and every open accepts a fresh frame before it shows | §1, §3.2, A35 |
| 2 | The lock epoch restarts on page load while the native gate outlives it | **Accepted**; fixed by **removing the epoch**. The gate is a flag tied to the page session, set only when the last frame said the lock is on, and cleared by the page's withheld frame or by a new session | §3.2 |
| 3 | `follow()` reopens only with `cfg.watch`, so the screen never returns | **Accepted** (seg/50-native-tabkit.js:220-223). Adds `watch(observe)` and calls `tab.revisit()` once celebrations end | §3.4, §3.5 |
| 4 | Taking back the whole state discards a review answer or catch-up typed into the same save | **Accepted.** Each writer stores the text first with `writeSave()` and pays the award in its own `save()`; only the award is taken back | §3.7, §5 |
| 5 | A relock does not redraw, so private text stays in the DOM | **Accepted, probed** (`lockpaths.js`: text still in `#journalEntry` right after a visibility relock) | §3.2.6 |
| 6 | The hero and list memo goes stale | **Accepted.** The memo is keyed on a page counter, `histRev`, raised by `renderHistory` and by every Journal writer, so no kit hook or function wrapper is needed and the `no-func-assign` ratchet (91) is untouched | §3.3 |
| 7 | The selection sits inside the memoised list | **Accepted.** `selection` moves to the top level | §3.3 |
| 8 | `select` triggers a full save and render | **Accepted, probed** (`selectcost.js`: an undecided recent day runs `renderHistory` twice, nested, depth 2). Decisions are stored with `writeSave()`, and `beforeOpen` is dropped | §3.4, §3.5 |
| 9 | The first frame after unlock is built before `renderHistory` | **Accepted, probed** (`lockpaths.js`: the watcher runs with 0 renders done). The unlock renders first and uncovers after | §3.2.5 |
| 10 | The card never hears of prompting or an error, and `prompt()` can resolve `undefined` | **Accepted, probed** (a failure notifies 0 watchers; an ignored prompt resolves `undefined`). Adds `onStatus` and replies with `{ok: r === true}` | §3.2.3 |
| 11 | FLAG_SECURE is cleared while the native Journal is still drawn | **Accepted.** A native holder, `SecureWindow`, keeps the flag while a frame with the lock on is drawn | §3.2.4 |
| 12 | `onHidden` was designed against main's `hide()`, which swipe rewrote | **Accepted.** Data is dropped on a TabSwipe "rested" callback; the conflict list is extended | §3.2.4, §6 |
| 13 | The audio dock inside every tab screen; the catalog check | **Accepted.** One shell-owned dock beside the bars, with a catalog entry, registration and a BOUND row | §3.4, §3.7 |
| 14 | `backup` and `attach` block the one command queue | **Accepted.** Both are fire-and-forget; the page's toasts come back as notices | §3.4 |
| 15 | The player vanishes on the Journal pane | **Accepted.** One page predicate, `audioBusy()`, applies to both Library kits until phase 4 | §3.5 |
| 16 | Phase 2 autosave would pay XP mid-sentence | **Accepted.** Autosave writes the text only; the award is paid when you leave the field, the tab or the app | §3.4 |
| 17 | Baseline and coordination details | **Accepted, all six** | baseline, §3.6, §3.7 |
| 18 | Moving Life Director's `cardSlice` collides with ld2 | **Accepted.** Only the Library's pieces move; the phase 3 tick uses settings' `PageCheck` | §3.7 |
| 19 | The page underneath goes stale; `write` flashes the wrong day | **Accepted.** `select` redraws the page's own entry (one day, cheap), and `write` becomes a command that focuses the field while the screen still covers it | §3.4 |
| 20 | Simpler choices | **(a)** `jcDecide` narrowed to the two real decisions: accepted. **(b)** `noTabSwipe` on the strip and the editor: accepted. **(c)** The privacy branch moves into the tab; A35 is scoped to private projections: accepted. **(d)** The Overview selector cleanup is dropped: accepted | §3.2, §3.5, §3.6 |
| 21 | Picker details | **Accepted.** The launcher is registered in `load()`, the MIME list is widened, and native attach moves to **phase 4**, after the dock, because attaching plays the file | §3.4, §4 |
| 22 | Light-theme citations; per-kit toast counting; A14 for the write phases | **Accepted, all three** | §1, §3.5, §3.7 |

---

## 1. Decision

**Build the Journal as its own native tab, `NativeJournal`, on view `history`.**
- The page stays the only writer. The screen draws a projection of it.
- It ships in five phases after a web-only phase 0.
- **The lock is handled differently from BACKLOG SEC-2's proposal.** The screen always opens. While the lock is closed, it draws its **own lock card** from a *withheld* frame that carries no journal data.
- The Library sheets that still hand off to the page become native one by one:
  - **phase 3:** the form, the finished tick, remove, remove audio and the link become commands and native dialogs on `NativeMedia`;
  - **phase 4:** the audio player becomes a native dock owned by the shell, and attach and replace get a native file picker whose file the page fetches itself. They come after the dock because attaching ends by playing the file.

**Who draws what, phase by phase**

| Piece | Phase | Native / page | How the screen reaches the page |
|---|---|---|---|
| Journal: switch, tip, hero (stats, 28-day rhythm), tile strip, day page (head, meta, care note, welcome card, insight and pattern, reflection box, timeline, workout notes, empty states), review pages (bars, heat grid, grid, coach card, answer box) | 1 | native | projection `library.journal.projection.v1` |
| Lock card (locked state), with "prompting" and the error line | 1 | native, from a withheld frame | command `unlock` → the page's own `jlPrompt` (the system BiometricPrompt over the screen) |
| Resting state (a screen not on show: the switch on the page background) | 1 | native only | — |
| Window security while the native Journal is drawn with the lock on | 1 | native holder beside the page's flag | — |
| Select a day or review | 1 | native highlight at once; the page decides and redraws its own entry | command `select` |
| ↻ swap question, tip ✕, Backup | 1 | native tap | commands `swap`, `tip`, `backup` (Android "Save as" over the screen; the reply does not wait for it) |
| Writing a reflection or a review answer | 1 → 2 | 1: the page's field; 2: a native editor | 1: command `write`. The page selects the day and focuses its field while the screen still covers it, then the screen steps aside; it comes back after the field is left and the celebrations have played. 2: commands `reflect` and `answer` |
| Welcome chips (pet, fresh), catch-up | 1 → 2 | 1: page; 2: native | 1: actions `claim` and `catchUp` (the screen closes, the page runs, back after `afterCelebrations`). 2: commands, with a native celebration |
| "Open Life Director" chip, Media in the switch | 1 | — | `{view}` actions through the kit (make-before-break) |
| Media add/edit form | 3 | native bottom sheet | command `upsert` (with an operation id) |
| Finished tick | 3 | native, optimistic like Plan's tick, drawn with core `PageCheck` | command `setStatus` |
| Remove, remove audio | 3 | native confirm dialogs | commands `remove`, `removeAudio` |
| Open link | 3 | native `ACTION_VIEW` intent, as Recipes (`RecipesScreen.kt:166`) | none |
| Audio player | 4 | native dock, one view owned by the shell beside the bars | plugin `NativeAudio` (the page pushes state; commands call the page's audio functions) |
| Attach / replace audio | 4 | native picker; **the page fetches the file** | command `attach {url}`, outside the command queue. The page `fetch`es `/_capacitor_content_/…` and calls its own `attachAudioFile`. If the spike fails, the current hand-off to the row's button stays |
| Journal privacy card (Settings), the biometric prompt itself, audio bytes (IndexedDB), position persistence | — | stay page / system | — |
| Pet room, 3D quest table | — | stay web (owner's rule) | — |

**Why this shape**
- **It finishes the Library tab.** Today the Media/Journal switch flips between a native screen and the web page: different fonts and metrics, and the switch itself jumps. With a native Journal the switch is one core component, `PageSegments`, drawn in the same place on both screens.
- **A lock card from a withheld frame is better than SEC-2's "stay on the web view" wall:**
  - The destination always opens. `NativeTabs.covered('history')` (= `enabled()`) is then true, which is what it claims. Make-before-break from native Media and `heal()` keep working, and no kit predicate needs to learn about the lock.
  - With a web wall, a successful unlock would show the web Journal for a few frames before the native screen opens on top. `jlPrompt` → `jlApply` uncovers the page, then the screen opens asynchronously (48-journal-lock.js:49). Native avoids that flash. Phase 0 also reorders the unlock so the first open frame is already complete (§3.2.5).
  - Swipe (when it lands) sees the Library tab on the Journal as native. Its `wouldOpen` reads `cfg.blocked`, which does not include the lock.
  - Privacy is kept in four structural layers:
    1. The Journal's `projection()` starts by returning the withheld frame whenever `LifeMaxJournalLock.blocksNative()` is true, so the open builders never run while locked.
    2. The Kotlin `Locked` and `Resting` states have no field able to hold journal data.
    3. **A screen that is not on show holds no journal data at all.** Data is dropped whenever the screen stops being drawn, whatever the lock state. A swipe may draw a hidden screen (A34), and the lock can be switched on while the Journal is hidden without any frame reaching it. A showing screen switches to `Locked` on app stop when the lock is on, and accepts no open frame from that page session until the page's relock frame has arrived.
    4. While a frame with the lock on is drawn, the window stays secure until the screen is gone, not just until the page leaves `history`.
- **One plugin per view.** `NativeTabKit.create` takes one view id: `cfg.id` is used by `blocked()`, the navigate wrapper, register and cover (`seg/50-native-tabkit.js:72-74, 228-241`). So the Journal is its own kit and plugin, not a second pane of `NativeMedia`.
- **The page keeps every rule.** The coach's archetypes, prompts, patterns, rewards and the 988 care note stay in 47-journal-coach.js. Native receives worded strings and resolved colours, and formats nothing that comes from the locale (weekday, month, time and `toLocaleString` numbers are projected).
- **Phase 0 comes first** because four things in today's code would make a projection unsafe or slow:
  - drawing writes the save, and does it inside the drawing: `jcChip` calls `save()` inside `renderHistory`, which runs a second, nested full render (47-journal-coach.js:204; probed);
  - coach writers claim success on a failed save (probed, below);
  - the page puts private text in the DOM while locked, and a relock does not even redraw (probed);
  - the lock tells nobody when a prompt starts or fails, and an unlock notifies its watchers before the Journal is drawn (probed).

**Rejected**
- *Keep the Journal web.* The owner wants the beautiful screens native, and the Library would stay half native, with a jumping switch.
- *Web wall for the lock (SEC-2 as written).* It flashes the web Journal on every unlock, and it needs `covered()` and swipe to learn about the lock.
- *One `NativeLibrary` plugin for both panes.* Both kits would hear every `action` event and both would close (LIBRARY_INVENTORY 7.2). It would also need a kit that takes a set of views.
- *A kit-level `privacy` option* (the first draft). It has one consumer. An early return in the Journal's own projection, proven by canary tests and simulation mutations, gives the same guarantee with less kit surface.
- *A lock epoch on every frame* (the first draft). It restarts at 0 on every page load while the plugin's gate outlives the page. A flag tied to the page session does the same job and survives reloads.
- *An audio dock inside each tab screen.* It would draw two docks during every make-before-break overlap and swipe drag, run a ticker in every composed screen, and change per tab the bottom inset that ChromeHost owns.
- *Native audio storage now (Media3, files out of IndexedDB).* That is a data move under ADR 0004 and a separate track. The dock and picker designs leave the bytes where they are.

**Choices for the owner, with my default**
1. **Separate switch.** Default: **Native Journal (beta)** as its own switch, off by default, because privacy-sensitive work rolls out on its own. It joins "Native Library" at A33 graduation.
2. **Light theme.** Today the Journal's white-alpha fills and lines vanish on the light theme. In `css/07-finishing.css` these are:
   - the off cells and the today outline (189) and the heat grid's off cells (276);
   - the tile and meter tracks (199, 246);
   - the meta pills (223) and the pattern chip (230);
   - the chips and the swap button (235, 168);
   - the ghost button and the stat tiles (180, 182);
   - the hero's inner highlight (177), the notebook lines (245), the timeline rail (251) and the review grid (277).

   The only light override today is `.jx-bignum` (219). Default: **fix them on the page and natively together.** One custom property, `--jx-ink` (white on dark, the text colour on light), is mixed at each rule's own alpha, so the two stay identical.
3. **Tip position.** The Journal's tip sits above the switch (`13-runtime.js:109`, because the Journal has no `.pagehead`), so the switch moves down when the tip shows. Default: **move the tip below the switch on the page too**, so the switch never moves across Media↔Journal.
4. **Page DOM while locked.** Default: **yes, stop drawing private text in the page's DOM while locked, and empty it on every relock** (phase 0; invisible change, see §3.2.6).
5. **Audio dock switch.** Default: its own **Native audiobook controls (beta)** switch, because it appears over every native tab.
6. Unchanged from LIBRARY_INVENTORY §8, still open: today absent until something is logged (Q7), "XP today" includes coach XP (Q9), untick → In progress (Q10), the grace minute (Q11).

**What I checked in the code**

| Claim | Status | Where |
|---|---|---|
| Main has integ and Native API 29. Swipe merged main and is at 29 (plans 30). Settings and ld2 are at 28, without integ | ✔ | `git log`, `git merge-base`; `android-wrapper/native/NativeApi.java:10` in each tree |
| The page renders reflection text into the DOM while locked; a save re-renders it | ✔ probed | `/tmp/jscratch/probes/lockprobe.js` → `{locked:true,textInDom:true}`, re-rendered after `save()` |
| A relock by visibility leaves the text in `#journalEntry` | ✔ probed | `/tmp/jfinal/probes/lockpaths.js` → `textInDomAfterRelock:true`; `jlApply` has no render (48-journal-lock.js:26-35) |
| Turning the lock on fires no `onChange`; a failed prompt notifies nobody; an ignored prompt resolves `undefined`; on success the watchers run before `renderHistory` | ✔ probed | `lockpaths.js` → `toldAfterTurnOn:0`, `toldOnFailure:0`, `whilePrompting:"undefined"`, `whenUnlocked:"undefined"`, rendersAtNotify 0; 48-journal-lock.js:41-51, 77 |
| `jcOnReflect` toasts "+15 XP · +2 bond · Reflection saved" when its save failed; the paid flag and coach activity stay in memory only; `jcSwap` likewise | ✔ probed | `/tmp/jplan/probes/honesty.js`: disk `{paid:false,coachActs:0}`, memory `{paid:true,coachActs:1}`; 47-journal-coach.js:130, 143-145, 236-238, 245, 353-354 |
| `jcSaveReview` and `jcCatchUp` store the text in the same `save()` as the award; `saveDayReflection` writes the text first | ✔ | 47-journal-coach.js:244-245, 351-354; 04-runtime.js:153-158 |
| Selecting an undecided recent day runs `renderHistory` twice, nested, with one write; again: one render, no write | ✔ probed | `/tmp/jfinal/probes/selectcost.js` → `{renders:2,writes:1,nestedDepth:2}`, then `{renders:1,writes:0}` |
| First `renderHistory` writes the save once; selecting a review creates its (empty) record | ✔ probed | `/tmp/jscratch/probes/perf.js` on a 978-day save |
| Long-history cost on this sandbox | ✔ probed (noisy CPU) | renderHistory 0.75–0.85 s cold, 0.17–0.22 s warm; `save()` 0.46–0.55 s; 978 tiles; tile JSON ≈ 55 KB |
| Tests on current main | ✔ run here, 2026-10-31 | lock: 28/28 in four of five runs, 27/28 in one (the timing flake the map saw does recur; phase 0 makes that check wait on the save). Coach 46/46. Media 37/37 and native-library 37/37 (earlier run) |
| Geometry at 412 dp | ✔ re-measured | switch 172×42 at y 80; hero 388×263; cells 21×21; strip 388×99; tile 66×89; review row 104×89; textarea 316×124; swap 34×34; page 1,620 dp tall |
| `takeBack(was)` exists for whole-state take-back | ✔ | `seg/04-runtime.js:53` (used by Log and Life Director) |
| `dismissFeatureTip` stores a decision-only field with `writeSave()` and no render | ✔ | `seg/13-runtime.js:76-81` |
| Kit: `blocked()` has no lock term; `update()` refuses once `lmaView()!==cfg.id`; `{view}` actions do not close first; `follow()` reopens only with `cfg.watch`; commands run one at a time in one chain | ✔ | `seg/50-native-tabkit.js:72-74, 97-100, 116-140, 160-166, 220-223` |
| `ComposeTabPlugin.open` accepts the projection before making the view VISIBLE; `update` is ignored while hidden; plugin instances (and their fields) outlive a page load, which only removes the screen | ✔ | `kt/bridge/web/ComposeTabPlugin.kt:103-107, 125, 152-153, 219-228` |
| On .wt/swipe, `hide()` no longer sets GONE: TabSwipe owns visibility, parks neighbours INVISIBLE and shows them while a drag pulls them in | ✔ | `.wt/swipe/.../ComposeTabPlugin.kt:237-239`; `TabSwipe.kt:86-89`; StageScene `Parked`/`Shown`; `.wt/swipe/docs/architecture/CONTRACT.md` A24 note |
| `ComposeTabPlugin.createView` registers every tab with TabSwipe (no registry list to edit) | ✔ | `.wt/swipe/.../ComposeTabPlugin.kt:209` |
| Capacitor 7.6.10 `Plugin.handleOnStop`/`handleOnPause` exist; `Bridge.onStop` notifies plugins and does not pause the WebView | ✔ | `node_modules/@capacitor/android/.../Plugin.java:990-995`; `Bridge.java:1367-1383` |
| Plugin calls run on one "CapacitorPlugins" thread, in order | ✔ | `Bridge.java:138, 863` |
| The page origin can read a `content://` URI as `/_capacitor_content_/…` | ✔ by code; **not verified on device** | `WebViewLocalServer.java:680-683`; `AndroidProtocolHandler.java:72-86`; LiveWeb keeps the local server (`LiveWeb.java:77`) |
| `window.prompt` is a native AlertDialog (a window above every view) | ✔ by code | `BridgeWebChromeClient.java:206-215` |
| core `boxShadow` has no spread and draws under the box (fine only when the box is opaque) | ✔ | `kt/core/designsystem/Css.kt:35-45`; the Journal's cells, nodes and heat cells are translucent |
| Swipe: `wouldOpen = enabled() && coverable()`, with `cfg.blocked` in `coverable`; it refuses a start on `.view.active.jlocked`; its web test asserts Library-on-Journal is web | ✔ | `.wt/swipe` 50-native-tabkit.js diff; `.wt/swipe/.../50-native-swipe.js:36-46`; `native-swipe.test.js:118`. `SwipeStateTest.kt:165-171` is a pure model test given the kinds and stays as it is |
| The web blocks a tab swipe on any overflowing strip, in both directions; swipe ships `Modifier.noTabSwipe()` | ✔ | `seg/34-navigation.js:99-108`; `.wt/swipe/.../core/designsystem/gesture/NoTabSwipe.kt` |
| The settings track passes `{requestId}` to command handlers; ld2 adds `active()` to the kit; settings adds core `PageCheck` | ✔ | `track/settings` and `track/ld2` diffs |
| Native Settings' BOUND list has no `nativeJournal` or `nativeAudio` | ✔ | `.wt/settings/.../50-native-settings.js:22-35` |
| `check_bridge_catalog.py` requires every `Native*` plugin to be registered and catalogued | ✔ | `tools/check_bridge_catalog.py:20-27` |
| Food and every Compose tab take their insets from `ChromeHost.show` | ✔ | `kt/bridge/web/ComposeTabPlugin.kt:200-209`; `NativeNutritionPlugin.kt` (ChromeHost.show) |
| Log and Plan ship mutating commands with a `mutationsGate` note naming their A14/A15 gap | ✔ | `docs/architecture/BRIDGE_CATALOG.json` (log, plan); CONTRACT A14 |
| Compose BOM 2024.12.01 (UI 1.7.x, Material3 1.3.x) | ✔ | `android-wrapper/patch_project.py:60` |
| `document.visibilityState` turns hidden when another activity (Save as) covers ours | Not verified | The page lock already relies on it (48-journal-lock.js:91) |
| BiometricPrompt shows above a sibling ComposeView | Not verified on device | It is a fragment dialog over the activity |
| Programmatic focus of a WebView textarea raises the keyboard once a native screen steps aside | Not verified | Same open question as Plan (MIGRATION, Workout plan) |
| `fetch()` of a 300–500 MB content stream into a Blob, then IndexedDB, on the S25 | Not verified | Phase 4 spike |

---

## 2. What the owner sees and feels

**Phase 1: the Journal is native.**
- **The switch.** Tapping Journal on native Media opens the native Journal first; Media closes after it. The switch pills stay at exactly the same place and size: one core `PageSegments`, at the same 12 dp sides and top inset + 16 dp, which is the LazyColumn padding of `MediaScreen.kt:125`.
- **The hero.**
  - The hero card's two radial glows (`#7c9cff26` top-left, `#f59e0b1c` bottom-right) sit over the panel gradient.
  - The four stats use the page's colours (streak `#fb923c`, month `#60a5fa`, week XP `#34d399`, reflections `#f472b6`).
  - The 28-cell rhythm: 14×2 cells of 21 dp, archetype colour, opacity `.35…1`, a soft glow, today outlined.
- **The tile strip.**
  - It scrolls horizontally and snaps to tile starts. A drag that starts on it is never a tab swipe, as on the web.
  - A tap moves the highlight **at once**: the tile lifts 2 dp, its border takes the archetype colour, and the glow appears (180 ms). The press shrinks it to .96.
  - The day or review below crossfades when the page's answer lands, one plugin round trip later. The page redraws only its own entry for that day, never the whole Journal (§3.4). Then the page's stagger plays: the head, coach cards and box fade up 10 dp in 450 ms at 0/40/80/120/160/200 ms, and timeline items at 200 + 50·i ms.
  - Unlike the page, the stagger does **not** replay on every save; it plays on a new selection and on open.
- **The day page.**
  - The 58 dp gradient day number, the glowing XP total, and the coach cards with their 4 dp coloured edge.
  - The welcome card with its crescent moon. The care note replaces everything above the box, word for word.
  - The notebook-ruled reflection box with its meter and counter, then the timeline: time, glowing node, pixel icon, attribute pill, XP.
- **Writing.**
  - Tapping the reflection box (or a review's answer box) hands over to the page field. The page scrolls to the field and focuses it while the native screen still shows, so the web appears already on that day, with the field ready. Whether the keyboard rises is still to be checked.
  - When you leave the field, the reward toast and the XP orbs (and a level-up) play on the page; then the native screen returns with your text.
- **The lock.**
  - With the lock on, the Journal opens on a **native** lock card (lock icon, "Journal locked", the page's sentence, Unlock), and the fingerprint prompt rises over it at once, as today.
  - While the prompt is up the card's Unlock is disabled. Cancel shows nothing; a failure shows "Could not unlock. Try again." at once.
  - Success crossfades the card into the Journal in 200 ms, with no web frame between and nothing popping in afterwards (no late pattern chip or welcome card).
  - Home, Recents: the app's preview is blank (the page's FLAG_SECURE). Back in the app, you see the lock card, never a frame of your text.
  - Leaving the Journal for another tab: the window stays secure until the Journal is gone from the screen. A screenshot taken in the moment of the switch is blank, as it is on the Journal itself.
- **With a book loaded or playing**, the Library tab stays the classic page on both panes, as native Media already does (until phase 4). The audiobook controls never appear and vanish inside the one tab.
- **Swiping (once Native tab swipe is on).** A swipe toward Library-on-Journal shows the Journal's switch on an empty page while your finger drags it. The Journal (or its lock card) fills in when the page opens it.
- **Time.** At midnight and at 20:00 the open screen updates within 15 s: Today becomes Yesterday, and the weekly review appears on its last evening. The page itself only updates on its next save.

**Phase 2: writing is native.** The reflection and review answers are typed in the native notebook field:
- lines follow the text;
- the counter is live ("Write 40+ characters for +15 XP (23)");
- the text is stored 1.5 s after typing stops, and on leaving the field, the tab or the app;
- the +15 XP is paid, and its celebration flies to the top bar natively, **only when you leave the field, the tab or the app**, as on the page. It never fires mid-sentence.

The welcome chips and the one-sentence catch-up become native too. Nothing on the Journal flashes the web any more.

**Phase 3: the Media sheets are native.**
- "＋ Add media" and Edit open a native sheet with the page's fields and words. Validation messages are the page's own ("Add a title first", "Use a valid http or https link") instead of the WebView's bubble. An old item's unusual type is kept, not blanked.
- The finished tick turns at once (faded until the page answers).
- Remove and Remove audio ask in native dialogs, without the stray backslash the page shows before quotes.
- Links open the browser.
- Attach still hands over to the row's own button, as today.

**Phase 4: the audiobook never vanishes.**
- A native dock above the bottom bar on every native tab, and on the native Overview: title, file and size, a seek bar that moves every second, −15/Play/+15/+30, speed, minimise to the round play button, close.
- Today Character, Food, Log, Plan, Life Director and Recipes cover the page's player, so a playing book has no visible controls.
- Native Media and the Journal stop stepping aside for a loaded book. Play stays native.
- Attach opens Android's file picker directly from the native row (spike permitting). The book then starts in the dock.

**Known differences from the page**, to record in MIGRATION:
- Roboto, not Inter.
- Whole-pixel text layout.
- The stagger only on a new selection or open.
- A crossfade on unlock.
- The time-dependent labels refresh within 15 s.
- Snap scrolling is Compose's (it settles on a tile start; CSS `proximity` may leave it between).
- Buttons keep the page's drawn sizes, with Compose's touch widening (DS-3).
- During a swipe, the Journal shows its switch on an empty page until it opens.
- Cash (its own activity) has no audio dock.

---

## 3. Architecture

### 3.1 Who owns what

| Layer | Owns |
|---|---|
| Page (47/48/04/07/08 segments) | data, rules, decisions (chip, welcome), rewards, the save, the lock state and the biometric prompt, the page's FLAG_SECURE, the backup file, audio bytes and the `<audio>` element |
| `50-native-journal.js` (NativeTabKit) | the projection (pure, never writes, withheld first while locked), commands that call page owner functions, delegations, covering rules, the lock and status subscriptions |
| `NativeTabKit` | session and sequence, the shared toast notice (one counter for every kit), hand-over |
| `NativeJournalPlugin` (ComposeTabPlugin) | decoding, dropping data at rest, the stop gate, the secure hold, the screen |
| `TabSwipe` (from .wt/swipe) | the panes' visibility, and the new "rested" callback when a pane that is not open stops being drawn |
| `SecureWindow` (new, `kt/app/shell`) | the window's FLAG_SECURE, held by the page's BiometricLock and by the Journal screen |
| `PageSegments`, `PageTipCard`, Css helpers, `cardPiece` (core) | the shared switch, the tip, glows and gradients, one card across list items |

### 3.2 The lock (new CONTRACT A35 "Private projections", ADR 0012)

1. **The projection decides first, in the tab.**

   ```js
   function projection() {
     if (LifeMaxJournalLock.blocksNative()) return withheldFrame();   // nothing below runs while locked
     return openFrame();
   }
   ```

   - The page subscribes: `LifeMaxJournalLock.onChange(() => { tab.send(); tab.revisit(); })`. `send()` does nothing while the screen is closed, and `revisit()` does nothing while it is open, so one line serves both.
   - A relock therefore pushes a withheld frame at once, and an unlock pushes the open one.
2. **The withheld frame** carries only:
   - `panes`;
   - `lock {on, card}`;
   - `status {prompting, error}`;
   - `notice` (the page's toasts carry no journal text: "Backup saved" after Save as is one);
   - the kit's protocol, session, sequence, tokens and light.

   It has no tip, no hero, no list, no selection and no entry.
3. **Lock status.** 48-journal-lock.js gains:
   - `jlError`, a variable that replaces the DOM-only `#jlErr` text (the line reads from it);
   - `LifeMaxJournalLock.state()` → `{on, locked, prompting, error}`;
   - `LifeMaxJournalLock.onStatus(fn)`, called whenever `prompting` or `error` changes.

   The existing `onChange` (blocked or not) keeps its meaning and its test.
   - The Journal subscribes with `onStatus(() => tab.send())`, so the page's own auto-prompt on entry (48-journal-lock.js:56), and its failure or cancel, reach the card at once.
   - `jlPrompt` keeps returning `undefined` when it ignores a call; the command maps it with `r === true`.
4. **Native holds nothing it should not.**
   - **States.** `WebJournalRepository` holds `JournalFrame.Open`, `Locked` or `Resting`. Only `Open` has a field for journal data. `Locked` and `Resting` keep the chrome: panes, tokens, light and the lock's words.
   - **At rest, always.** When the screen stops being drawn, the repository drops to `Resting` and the secure hold is released, whatever the lock state. This is the swipe track's new callback (`TabSwipe.register(…, onRest)`), called when a pane that is not open leaves the Shown stage (Parked or Gone), and `remove()`. `ComposeTabPlugin` exposes it as `protected open fun onRested()`.
     - The hook is not in `hide()`: on .wt/swipe, a source that is closed keeps sliding out while still drawn (`TabSwipe.kt:89`), and dropping there would blank it mid-slide.
     - The rule ("left Shown while not open") is a pure function of two stage scenes, unit-tested beside swipe's model tests.
   - **On a re-show**, `open` accepts the fresh frame before the pane is shown (`ComposeTabPlugin.kt:103-107`; swipe's `opened` likewise), so no stale frame is drawn. A swipe that pulls the parked Journal in draws `Resting` until then.
   - **On app stop.** `NativeJournalPlugin.handleOnStop()`, while the screen shows a frame whose `lock.on` is true:
     - switches to `Locked` with its last card words;
     - sets `relockFor = webSessionId`;
     - from then on, an open frame from that session is refused until the page's withheld frame (its relock) has been accepted.

     The order is safe:
     - frames of one session are accepted in sequence order, and an older sequence is ignored (A23 item 3);
     - so an open frame built before the relock but still in flight is refused, whether it lands before the withheld frame (gate) or after it (sequence).

     The gate is not set while the lock is off. It is cleared by the withheld frame, and by an open from a **new** session (a reloaded page: OTA restart or import), so a reload can never leave the screen stuck on the card.
   - **The secure hold.** `SecureWindow.hold(owner, on)` sets FLAG_SECURE while any holder wants it.
     - `BiometricLockPlugin.secure` becomes `SecureWindow.INSTANCE.hold(this, on)`.
     - `NativeJournalPlugin` holds while its screen is drawn with a frame saying `lock.on`, and releases at rest.
     - So the page clearing its flag on leaving `history` (48-journal-lock.js:58-60) no longer uncovers a Journal that is still on screen during make-before-break (up to the 2.5 s net) or a swipe slide.
5. **Unlock.**
   - The command `unlock` runs:

     ```js
     if (!LifeMaxJournalLock.blocksNative()) LifeMaxJournalLock.relock();
     return { ok: (await LifeMaxJournalLock.prompt()) === true };
     ```

     If native locked itself on stop but the page never saw a hidden visibility, the page relocks first; its withheld frame clears native's gate, and the prompt follows.
   - **Phase 0 reorders `jlPrompt`'s success path** to `jlUnlocked = true; renderHistory(); jlApply();`.
     - `renderHistory` first decides for `histSel` and warms the coach cache.
     - Then `jlApply` → `jlNotify` → `send()` builds the first open frame complete.
     - The page's DOM is drawn under the still-present cover, then uncovered.
6. **The page leaves private text out of the DOM while locked** (phase 0, owner choice 4):
   - **`renderHistory` and `renderJournalEntry`** empty the hero, tiles and entry while `blocksNative()` is true. Every branch still runs `LifeMaxCoach.reset()` and `jcDecide(histSel)`, so the decisions keep today's timing.
   - **`jlApply`**, whenever it finds the Journal locked, calls `renderHistory()` after its blur has saved. This covers every relock path: visibility (48:91), the grace minute (48:56), `jlRelock` (48:53) and the boot timer.
   - **`saveDayReflection`'s soft list redraw** is skipped while locked.
   - **At load**, 48 calls `renderHistory()` once when `jlOn()`, because the boot render and the coach's redraw run before 48 loads (13-runtime.js:115; 47-journal-coach.js:358).
   - The cover stays exactly as it is (`07-finishing.css:290`), and the existing lock tests still hold.
7. **What does not change:**
   - The page's own FLAG_SECURE behaviour (`jlSecure`) stays; the native hold only adds to it.
   - The grace minute, the background relock, thumbnails (PageThumb draws only the WebView) and the Settings card stay as they are.
   - The lock cover is **not** added to `lmaTopOverlay()`: Back would "close" it and it would stop tab swipes (34-navigation.js:55-76, 141).
   - The Overview's dead `.lock-ov,.journal-lock` selectors (`seg/50-native-overview.js:80`) are left alone. Swipe changes that file and they cost the Journal nothing.

**A35 "Private projections"** (CONTRACT, phase 1). A tab that draws data the page keeps private:
1. builds a withheld frame, with no private field, whenever the page's lock blocks native; the open builders do not run then;
2. holds no private data in a screen that is not on show: it drops it whenever the screen stops being drawn, whatever the lock state, because a swipe may draw a hidden screen (A34) and the lock can change while the screen is hidden;
3. switches a showing screen to its locked state when the app stops with the lock on, and refuses that page session's open frames until its withheld frame arrives;
4. holds the window secure while a frame with the lock on is drawn, until the screen stops being drawn;
5. is proven by canary tests on the web, in the fixtures, in the simulation and in Kotlin, each shown failing on a mutation.

A24 gains a note: a private tab's parked screen draws only its resting state.

### 3.3 The projection, `library.journal.projection.v1`

Every string comes worded by the page: dates and times through the WebView locale, numbers as the page prints them, colours as ARGB through `NativeTabKit.color`. CSS-only presentation (uppercase eyebrows, badges and stat labels; letter spacing) is native. The care note's text comes from one page constant, `JC_CARE {lead, text}`, which both the page's HTML and the projection read.

**Open frame:**

```json
{
  "protocol": 1, "webSessionId": "history-web-…", "sequence": 41, "tokens": {"bg": 4278979346, "…": 0}, "light": false,
  "withheld": false,
  "panes": [{"id":"media","label":"Media","current":false},{"id":"history","label":"Journal","current":true}],
  "lock": {"on": true, "card": {"title":"Journal locked","body":"Your reflections are private. Unlock with your fingerprint or screen lock.","unlock":"Unlock"}},
  "motion": true,
  "edit": "page",
  "tip": null,
  "hero": {"eyebrow":"YOUR CHRONICLE","title":"Journal","backup":{"label":"Backup","spoken":"Export backup"},
    "stats": [{"value":"5","label":"day streak","color":4294677308}, {"value":"26","label":"days this month","color":4284522234},
              {"value":"1,055","label":"XP this week","color":4281652121}, {"value":"2","label":"reflections","color":4294210806}],
    "rhythm": {"title":"Last 4 weeks","legend":"brighter = bigger day",
      "cells": [{"color":4279541940,"alpha":0.71,"off":false,"today":false,"label":"Oct 1: 177 XP"}]}},
  "list": {"rev": "12|2026-10-28|0", "items": [
    {"kind":"day","key":"2026-10-28","weekday":"Today","date":"28","bar":42,"xp":"177","pen":true,"color":4279541940,"today":true,"spoken":"Today, 177 XP"},
    {"kind":"quiet","text":"4 quiet days"},
    {"kind":"review","key":"W:2026-W43","badge":"W","title":"Week 43","sub":"weekly review"}]},
  "selection": "2026-10-28",
  "empty": null,
  "entry": {"kind":"day","key":"2026-10-28",
    "head": {"number":"28","weekday":"Wednesday","month":"October 2026","xp":"177","xpLabel":"XP today"},
    "meta": ["3 entries","2 exercise notes"],
    "care": null,
    "welcome": {"head":"Good to see you again.","body":"It has been 9 days since your last log. …",
                "chips":[{"id":"line","label":"One-sentence catch-up · +20 XP","done":false},{"id":"director","label":"Open Life Director","done":false}]},
    "insight": {"label":"Steady Stride","color":4279541940,"text":"You kept the gears turning with 177 XP. …","pattern":"Pattern noticed: 70% of your XP …"},
    "reflect": {"prompt":{"kicker":"Reflect","text":"What is one small win from today that did not show up as XP?","id":"PRM_C2","swap":"Swap question"},
                "label":null,"text":"","placeholder":"How did today feel? What mattered? What will you do differently?",
                "max":4000,"min":40,"canPay":true,"awarded":false,"meter":0,
                "counter":{"awarded":"+15 XP & +2 bond claimed","ready":"Saves for +15 XP","short":"Write 40+ characters for +15 XP","old":"Reflections on older days earn no XP"}},
    "timeline": [{"time":"7:15 AM","color":4294406257,"icon":"dumbbell","name":"Strength training","attr":{"text":"Physique","color":4294406257},
                  "minutes":"45 min","skill":null,"xp":"+30","notes":"Felt strong today, kept the pace easy.","plan":[{"label":"Bench press","weight":"60 kg","note":"easy"}]}],
    "workoutNotes": null,
    "blank": null},
  "notice": null
}
```

**Withheld frame:**

```json
{
  "protocol": 1, "webSessionId": "history-web-…", "sequence": 42, "tokens": {"…": 0}, "light": false,
  "withheld": true,
  "panes": [{"id":"media","label":"Media","current":false},{"id":"history","label":"Journal","current":true}],
  "lock": {"on": true, "card": {"title":"Journal locked","body":"Your reflections are private. Unlock with your fingerprint or screen lock.","unlock":"Unlock"}},
  "status": {"prompting": false, "error": "Could not unlock. Try again."},
  "notice": {"id": 7, "text": "Backup saved"}
}
```

Field notes:
- **Selection is top level.** `selection` is the page's `histSel`. The native highlight follows it once a reply lands. `list` holds only the memoised items, so `list.rev` describes exactly what it covers, and the decoder keeps its previous list object when the rev is unchanged.
- **Review entry.** It has the same shape, with `"kind":"review"`, `head {badge:"W43"|"OCT", title, range}` and a chart:
  - weekly: `{"kind":"bars","bars":[{"label":"M","value":"114","height":35,"color":…,"today":false}]}`;
  - monthly: `{"kind":"heat","weekdays":["M",…],"lead":2,"cells":[{"day":"1","color":…,"alpha":0.62,"off":false,"label":"Oct 1: 120 XP"}]}`.

  It also carries `grid [{label,value}]`, `coach {badge:"Coach", text}`, `question`, `answer`, `placeholder:"Write if you want to."`, `max:2000`, `min:50|60`, `paid`, and the counter words.
- **Counter.** `jcCounter`'s logic is a choice among four projected strings, plus " (n)" while typing (47-journal-coach.js:147-152). Native picks with `awarded`, `canPay` and the projected `min`; a parity fixture holds the page's answers.
- **Undecided days.** The builders are read-only. A chip or welcome that has not been decided reads as none. The page decides at every render and every `select`, so the open day is always decided by the time it is drawn.
- **Empty journal.** `"empty":{"icon":"◷","title":"No journal entries yet","body":"Log an activity, …"}`, with `entry:null`, no list items and no box, as the page (04-runtime.js:102-106).
- **Care note.** `"care":{"lead":"That sounds really heavy.","text":"You do not have to carry it alone. If you might hurt yourself, please call or text 988 (in the US) or your local emergency number right now, or reach out to someone you trust. The coach stays quiet today."}`. With a care note, `welcome`, `insight` and `prompt` are null and `label` is "Reflection (optional)" (`jcTop`, 47-journal-coach.js:248-255).
- **Icons** are the resolved pixel name `ICONS[v]?v:(EMOJI_MAP[v]||'star')` (06-runtime.js:86). Every name is in core `PixelIcon`.
- **Sizes.** A tile is about 120 bytes, so 978 days are about 120 KB, well under `MAX_SNAPSHOT_CHARS` 2,000,000 (`ComposeTabPlugin.kt:231`). There is no paging. Only the selected entry's text is sent.

**Cost and freshness.**
- The projection never calls `LifeMaxCoach.reset()`. It reuses the coach cache that `renderHistory` filled.
- **Hero and list are memoised on `histRev | getToday() | (hour ≥ 20)`.** `histRev` is a page counter (`04-runtime.js`), raised by:
  - `renderHistory`, every branch: every `save()` reaches it through `renderAll`, and so does every `takeBack`;
  - `saveDayReflection` after its write. This is the path that never renders: an older day, under 40 characters, or a re-edit after paying (04-runtime.js:153-172; 47-journal-coach.js:142);
  - phase 2's write owners.

  It is a plain counter, not a wrapper, so the lint ratchet (`no-func-assign` 91, `tools/lint-baseline.json`) does not move.
- Writes that bypass both paths, such as `markVisit`'s 30 s direct write or the workout plan's note writes, leave the page's own Journal as stale as today, and the native list stays equally stale. The next save converges both.
- The 15 s refresh therefore costs one entry build, the tokens and the JSON. A new day or the 20:00 review cutoff still shows within 15 s.

### 3.4 Commands and actions

**Phase 1 commands** (the screen stays; the reply carries the next projection). Every command except `unlock` refuses with `{ok:false}` while `blocksNative()` is true.

| Command | Payload | Page | Writes | Idempotence / honesty |
|---|---|---|---|---|
| `select` | `{key}` | `selectJournalEntry(key)`: sets `histSel`, redraws only the page's entry (its drawing decides as today) and moves the tiles' `on` class | the chip and cooldown, and the welcome record (with the tier-3 base), each by `writeSave()` only when newly decided. No `renderAll` | Same key twice: no write. Unknown key: `{ok:false}` with "That day is not in your Journal." |
| `write` | `{key}` | `selectJournalEntry(key)` if needed; scroll to and focus the entry's textarea; `setTimeout(tab.follow)` | none | The focused field makes `blocked()` true, so the screen steps aside through the hand-over, with the page already on that day. The way back is below |
| `swap` | `{d, from}` | `jcSwapPrompt(d, from)`, then the page redraws its entry | `journalCoach.days[d].promptId` by `writeSave()` | Compare-and-set: changes only if the current id is `from`. A failed write puts `from` back and says nothing was saved |
| `unlock` | `{}` | §3.2.5 | memory only | `{ok: r === true}` |
| `backup` | `{}` | `exportData()`, **not awaited**; reply at once | none (a file) | Save as and its toast ("Backup saved" / "Save cancelled") arrive later as `notice`, in withheld frames too. A Save as that never settles cannot hold the queue |
| `tip` | `{}` | `dismissFeatureTip('history')` | `tipsSeen.history` (direct write, failure ignored, as today) | repeat is a no-op |

**Phase 1 actions** (the kit closes the screen, the page runs, then the screen comes back):

| Action | Page | Back |
|---|---|---|
| `claim {id:'pet'\|'fresh'}` | `jcClaim(id)` | `acting` holds `blocked()` until `afterCelebrations(() => { acting = false; tab.revisit(); })` |
| `catchUp` | `jcCatchUp()` (window.prompt) | the same |
| `{view:'media'}`, `{view:'life-director'}` | `lmaNavTo(view,{fast:true})`. Life Director opens its feature (34-navigation.js:36-38) | hand-over |

**The way back after `write`.**
- A capture-phase `focusout` on `EDITING` (`#journalEntry textarea`) sets `returning = true`, which `blocked()` counts.
- It then queues `setTimeout(() => afterCelebrations(() => { returning = false; tab.revisit(); }))`. The timeout lets the page's `change` handler run first, so its XP orbs and level-up are queued before the wait starts.
- `returning` matters because `celebrateXP` appends to `<body>`, which the kit's body watcher hears. Without it, the screen would come back over the orbs.
- The same path runs when a relock blurs the field (Home while typing). The screen then reopens on a withheld frame, which is the lock card on return.

**Phase 2 commands**, behind capability `library.journal.write.v1`. The page sets `edit:"native"` only when the handshake lists it. ADR 0013 and the catalog's `mutationsGate` come first (§3.7).
- `reflect {d, text, base, final}` → `journalSaveReflection(d, text, base, {award: final})`, split from `saveDayReflection`. It returns `{ok, changed, paid, message, conflict?}`.
  - It refuses with `conflict` and the stored text when the stored text is neither `base` nor `text` (for example the catch-up appended).
  - Autosave sends `final:false` and only writes the text. Leaving the field, hide and app stop send `final:true`, which may pay, as the page's `change` does.
  - Pay-once stays keyed by day (`days[d].xpAwarded`), stored in the same `save()` as the coach activity.
- `answer {key, text, base, final}` works the same way through `jcSaveReview`.
- `claim {kind}` uses `welcome[today].claimed[kind]` as its receipt.
- `catchUp {text}` → `jcCatchUpWith(text)`; its receipt is `claimed.line`.

Every paying command runs inside `holdCelebrations` and returns `celebrations` for the screen to draw, as Plan does (`seg/07-runtime.js:77`; `seg/50-native-plan.js:222`).

**Retry rule** (both phases 2 and 3, written in each view model): a screen that reopens while it waits for a reply stops waiting (`opened()`, as `LogViewModel.opened`), keeps the request and its id, and a retry sends the same payload. The natural receipt makes it land at most once.

**Phase 3 commands** (on `NativeMedia`, capability `library.media.write.v1`):

| Command | Page owner (split in phase 0) | Receipt and honesty |
|---|---|---|
| `upsert {op, id?, fields}` | `mediaUpsert` | Adds store `item.addOp = op` in the same save, so a repeat returns the same id. Edits are a plain set. A stored type that is not among the options is kept, not blanked. The current take-back stays (07-runtime.js:150-160) |
| `setStatus {id, status}` | `mediaSetStatus(id, status)` → `{ok, changed, message}`. It replaces the unused `setMediaStatus`; `toggleMediaFinished` calls it, so the toasts keep the page's words | Same status: no change and no save |
| `remove {id}` | `mediaRemove(id)`. `deleteMediaItem` = confirm + it; the confirm drops the `\"` escape | id gone: `{ok:true, changed:false}` |
| `removeAudio {id}` | `mediaRemoveAudio(id)`. `removeMediaAudio` = confirm + it | no audio: no change |

The link opens natively. Play and attach stay delegations until phase 4.

**Phase 4 (`NativeAudio`, `NativeMedia` additions).**
- **State push.** The page calls `NativeAudio.state({v:1, visible, minimized, loading, mediaId, title, sub, playing, position, duration, rate})` on `play`, `pause`, `seeked`, `ratechange`, `loadedmetadata`, `durationchange`, `ended` and `emptied`, on the player's class changes, and every 10 s while playing as a drift correction.
- Native extrapolates the position between pushes and ticks once a second, only while the dock is on screen and playing.
- **Commands** call the page's own functions:
  - `toggle` → `audioTogglePlay`;
  - `skip` → `audioSkip`;
  - `seek {frac}` → `audioSeekChange(frac*1000)`;
  - `rate` → set `#audioRate` and call `audioSetRate`;
  - `minimize` → `toggleMinimizeAudioPlayer`;
  - `close` → `closeAudioPlayer(true)`.
- Position persistence stays the page's (08-runtime.js:128-140). `audioCtx.loading` changes dispatch a small `lma-audio` event from `playMediaAudio`.
- **Media gains `play {id}`.**
- **Media gains `attach {id, url, name, type, size}`**, fire-and-forget. The command starts the page's job and replies `{ok:true, started:true}` at once. The job is `fetch(url)` → Blob → `attachAudioFile(id, new File([blob], name, {type}))`, with the existing take-back and blob restore (07-runtime.js:243-280). Its toasts ("Saving audio to this device…", "Audio attached · name", or the failure) arrive as notices. Playback then starts in the dock.
- **The picker.** `NativeMediaPlugin` registers an `ActivityResultContracts.OpenDocument` launcher in `load()`; a tap after the activity has resumed cannot register one, and a native tap has no `PluginCall` for Capacitor's `startActivityForResult`.
  - It asks for `audio/*`, `audio/mp4`, `audio/x-m4a`, `audio/x-m4b`, `audio/mpeg` and `application/octet-stream`, because `.m4b` is often reported as one of the last two.
  - It checks the picked name against the page's accept list (tpl:74) and refuses anything else with the page's words.
  - It takes no persistable grant; the page reads the file at once.

### 3.5 How it fits the kit

```js
const EDITING = "#journalEntry textarea";
let acting = false, returning = false;

const tab = NativeTabKit.create({
  id: "history", plugin: "NativeJournal", capability: "library.journal.projection.v1",
  setting: "nativeJournal", toggle: "nativeJournalToggle",
  projection,                       // withheld first whenever LifeMaxJournalLock.blocksNative() (§3.2)
  notices: true,                    // the kit's shared toast notice
  commands, onAction, render: "renderHistory",
  // a hand-over in progress, the page's field, a level-up, and (until the dock) a loaded book: the same rule as native Media
  blocked: () => acting || returning || !!document.activeElement?.matches(EDITING) || !!document.querySelector(".luov") || audioBusy(),
  // the page gives the pane back: a level-up gone (the body watcher), the entry redrawn after a hand-over
  watch(observe) {
    observe(document.getElementById("journalEntry"), { childList: true });
    observe(document.getElementById("audioPlayer"), { attributes: true, attributeFilter: ["class"] });
  },
  globals: { open: "openNativeJournal", enable: "setNativeJournalEnabled", projection: "NativeJournalTabProjection" },
});
LifeMaxJournalLock.onChange(() => { tab.send(); tab.revisit(); });
LifeMaxJournalLock.onStatus(() => tab.send());
```

- There is no `beforeOpen`. The page decided `histSel` at its last render (every save renders, and so does the coach's load).
- **`audioBusy()`** is a new page predicate next to the player in `08-runtime.js`: `audioCtx.loading`, `audioCtx.mediaId` or the player showing. 50-native-library.js uses it too, in place of its three terms (`seg/50-native-library.js:208-210`). Phase 4 drops it from both kits once `shell.audio.v1` is in the handshake.

**Kit change** (`seg/50-native-tabkit.js`): only `notices: true`.
- One MutationObserver on `#toast` and **one counter shared by every kit** (in the kit's module, not per tab).
- A toast goes into **at most one frame of any kit**: the first frame built while it shows. A toast shown by native Media is therefore not replayed by the Journal it hands over to. A toast raised during a delegation, while the screen was closed, is carried by the frame that brings the screen back.
- `NativeTabKit.claimNotice()` marks the current toast as already shown. Media uses it for its file-button hint, in place of its private `hint`.
- Notices ride in withheld frames too.
- 50-native-library.js moves onto it; `native-library.test.js` unchanged is the proof.
- A toast claimed by a frame that is then not sent (the screen was leaving) is lost; that is recorded in the guide.

Media's covering rules are otherwise untouched until phase 4.

**Coach split** (47-journal-coach.js, phase 0):
- **Pure data builders.** `jcHeroData`, `jcListData(days)` (no selection in it), `jcDayData(d)` and `jcReviewData(key)` read the store as `state.journalCoach || EMPTY` and never create a record. The empty `days[d]={}` and `reviews[key]={}` that `jcPrompt` and `jcReviewHtml` create today (lines 123, 305) are no longer made; the writers create what they need.
- **The HTML builders become thin renderers over them**, so the page and the projection read one source. The page's renderer adds the `on` class from `histSel`.
- **`jcDecide(d)`** covers the two real decisions only:
  - `jcChip(d)` (pattern and cooldown);
  - `jcWelcomeRecord(d)` (tier, gap and, for tier 3, the base).

  Both store with `writeSave()` when they decide something new, as `dismissFeatureTip` does. These are decision-only fields with no XP, so there is nothing for `save()`'s rebuild, badges or render to do. This removes the nested render that `selectcost.js` shows. The page's own drawing calls `jcDecide` where `jcTop` decides today; `renderHistory`'s locked branch calls it too.

The projection is the data builders plus the lock, tip and selection frame, and it never writes.

### 3.6 Hand-over, entry points and Back

- **Entry points.** Every way in ends in `navigate('history')`, so the kit wrapper opens the screen:
  - the Media page's switch and native Media's switch (`{view:'history'}`, kit line 161);
  - the bar's Library tab when `libPane==='history'` (`lmaResolve`, 34-navigation.js:9);
  - native bar taps (`NativeOverviewSnapshot.activate`, which sets `native-cover` from `covered('history')`, 50-native-overview.js:174-176);
  - Overview "History ›" (raw `navigate`, 18-runtime.js:49);
  - voice and chips (`lmaNavTo('history')`);
  - Back.

  There is no deep link, widget or notification for the Journal (50-deeplink.js:4, `LaunchRules.java:14`).
- **Media↔Journal.** Native to native with make-before-break. `libPane`, the lit bar tab and `.libseg` still update through the scrubnav wrapper (42-scrubnav.js:198-212). The lock wrapper still records `jlLeftAt` and clears the page's flag, because every switch goes through `lmaNavTo`. The native secure hold keeps the window covered until the Journal has rested.
- **Back.** Android Back → `lmaBack` (MainActivity.java:131-140), which pops `lmaNavStack` (Media if you came from there) through `lmaGoView`, so it is make-before-break into native Media.
  - The Journal registers no Back handler (A24).
  - Native dialogs and sheets (phase 3) are separate windows that take Back first (Material3 API; to check on the phone).
  - With the keyboard up, Back closes the keyboard first.
  - A phase 2 draft is flushed (text only) on hide, and paid as the page would on leaving.
- **Swipe (after .wt/swipe lands).**
  - **No registry step.** `ComposeTabPlugin.createView` registers every tab with TabSwipe, and the page's `kind()` reads `NativeTabs`. With `nativeJournal` on and nothing blocking, Library-on-Journal is a native neighbour.
  - **Tests.** `native-swipe.test.js:118` keeps its case with the switch off (web) and gains one with it on (native). `SwipeStateTest.libraryOnTheJournalIsAWebWall` is a pure model test given the kinds and stays.
  - **The parked Journal** draws only its resting state (§3.2.4).
  - **The tile strip and the phase 2 editor** carry `Modifier.noTabSwipe()`, matching the web, which refuses a swipe on any overflowing strip in both directions (34-navigation.js:104-105).
  - **Starts from the lock card.** Swipe's `covered()` refuses a start on `.view.active.jlocked` (50-native-swipe.js:37, 45). On the web that keeps a finger on the cover from swiping; the native lock card shows no data, so my default is to allow it. Phase 1 changes the check to apply only when that view's native screen is not open (`!NativeTabs.isOpen(lmaView())`), agreed with the swipe track.
  - **A loaded book** keeps the Library tab a web wall on both panes, through `audioBusy()` in `blocked()`, as for Media.
- **Simulation.** `heal()` opens the Journal (lock card or open) whenever its view lacks a screen. A23 is unchanged: the destination always opens.

### 3.7 Files

**Phase 0 (web only)**

| File | Change |
|---|---|
| `seg/47-journal-coach.js` | the data builders; read-only store access; `jcDecide` narrowed, deciding by `writeSave()`; `jcSwapPrompt(d, from)` (compare-and-set, direct write, put back on failure); `JC_CARE {lead, text}`; `histRev` raised by each writer. **Honest writers**, each storing the text first with `writeSave()` (kept in memory and in the box on failure, as `saveDayReflection` does), then paying the award in its own `save()` with `takeBack(was)` (`was` taken after the text write), and toasting success only after a stored save: `jcOnReflect`, `jcSaveReview`, `jcCatchUp` (text + award), `jcClaim` (award only) |
| `seg/04-runtime.js` | `renderHistory` and `renderJournalEntry` over the data builders, with the locked branch (blank, reset, `jcDecide(histSel)`); `selectJournalEntry(key)` (entry only, `on` class moved; the page's own tile tap keeps `selectJournalDay`); `histRev`; the soft list redraw gated while locked |
| `seg/48-journal-lock.js` | `jlError`, `state()`, `onStatus`; the success path reordered (render, then apply); `jlApply` renders when locked; one `renderHistory()` at load when on |
| `seg/07-runtime.js` | `mediaSetStatus`, `mediaRemove`, `mediaRemoveAudio`; the `mediaUpsert` op receipt |
| `seg/08-runtime.js` | `audioBusy()` |
| `seg/13-runtime.js`, `css/07-finishing.css` | the tip below the switch; `--jx-ink` for every white-alpha Journal rule listed in owner choice 2 |
| `tests/coach.test.js`, `lock.test.js`, `media.test.js` | see §5 |

**Phase 1**
- **New (web):**
  - `seg/50-native-journal.js`, included after `50-native-library.js` in `src/index.template.html`;
  - `tests/native-journal.test.js`.
- **Changed (web):**
  - `seg/50-native-tabkit.js` (shared notices);
  - `seg/50-native-library.js`: onto the kit's notices and `audioBusy()`. Its header comment, which says the Journal stays on the page (line 6), is rewritten;
  - `seg/50-native-swipe.js` (the lock-card start rule, agreed with the swipe track) and `tests/native-swipe.test.js:118` (a case with the switch on);
  - `views/settings.html`:
    - a row **Native Journal (beta)** after the Library row (line 14), off by default, hint: "The native Journal: your days, the coach and the reviews, behind the Journal lock. Writing a reflection or a review answer and the welcome-back chips use the classic page. Turn off for the classic Journal.";
    - the **Native Library (beta)** hint loses "the Journal stays classic" and gains "The Journal has its own switch.";
  - `tools/gen_native_fixtures.js` (`journal()`);
  - `tests/native-handover-sim.test.js`;
  - module map and symbol index regenerated (`tools/gen_module_map.py`).
- **After .wt/settings merges:** `["nativeJournal", "#nativeJournalToggle", PREFS]` in its BOUND list.
- **New (Kotlin):**
  - `kt/bridge/web/NativeJournalPlugin.kt` (`tabId "history"`; overrides `onRested`, `handleOnStop`; holds `SecureWindow`);
  - `kt/app/JournalContainer.kt`;
  - `kt/app/shell/SecureWindow.kt`;
  - `kt/feature/journal/domain/{JournalFrame.kt, JournalRepository.kt}`;
  - `kt/feature/journal/data/{WebJournalRepository.kt, JournalJson.kt, JournalEvents.kt}`;
  - `kt/feature/journal/presentation/{JournalViewModel.kt, JournalScreen.kt}`;
  - `kt/feature/journal/presentation/components/{JournalColors.kt, JournalHero.kt, JournalTiles.kt, DayPage.kt, CoachCards.kt, ReflectionBox.kt, Timeline.kt, ReviewPage.kt, LockCard.kt, RestingPage.kt}`.

  Every file stays under 600 lines.
- **Core** (Library pieces only):
  - `components/PageSegments.kt` (moved `LibrarySwitch`, `LibraryHead.kt:23-53`; closes finding 22);
  - `components/PageTipCard.kt` (moved `FeatureTipCard`);
  - `Css.kt`: `boxShadow(…, spread, clipOutBox)` for translucent boxes, and `cssRadialGradient` (elliptical, `at x% y%`);
  - `cardPiece` moved from `LibrarySurfaces.kt:45`. Life Director's `cardSlice` (finding 24's other half) waits for a follow-up after ld2, which rewrites `LifeDirectorScreen.kt` and re-records its references.
- **Changed (Kotlin and Java):**
  - `ComposeTabPlugin.kt` (`onRested()`, wired to TabSwipe's rest callback and to `remove()`);
  - `.wt/swipe`'s `TabSwipe.kt` (`register(…, onRest)`, called when a pane that is not open leaves Shown; the rule is pure and unit-tested);
  - `BiometricLockPlugin.java` (`secure` through `SecureWindow`);
  - `MediaScreen.kt` and the Library components (imports only; references byte-identical);
  - `MainActivity.java` (register after `NativeMediaPlugin`, line 91);
  - `NativeApi.java` (main's value + 1 at merge; at least 31).
- **Docs:**
  - CONTRACT A35 and the A24 note;
  - ADR 0012 (private projections: the lock card from withheld frames instead of SEC-2's wall, data dropped at rest, the session gate, the secure hold);
  - NATIVE_TAB_GUIDE (private projections, shared notices, a command that steps aside through `blocked()`);
  - BRIDGE_CATALOG `journal` entry, which `check_bridge_catalog.py` enforces;
  - MIGRATION ledger row and section;
  - LIBRARY_INVENTORY §9;
  - BACKLOG (SEC-2 resolved for native screens; 22 done; 24 half done);
  - a note to the logic-spec track: phase 0 moves the coach award lines that `ECONOMY_SPEC.md` 11.7.3 cites (`47-journal-coach.js:133-146, 234-246, 350-355`).

**Phase 2**
- Core `components/PageTextArea.kt` (DS-5): ruled notebook lines drawn from the `TextLayoutResult`, `bringIntoView` on focus, `noTabSwipe`, using MainActivity's IME padding (NAV-5 decided for these screens: keep the activity padding, hide the shared bottom bar while the IME shows).
- Core `components/XpCelebration.kt`, moved from `PlanFeedbackLayer.kt:58-59` with Plan's references unchanged.
- The journal view model's draft, text-only autosave (1.5 s), and the final write (which may pay) on leaving the field, on hide and on stop.
- Web: the owner splits (`journalSaveReflection` with its award option, `jcCatchUpWith`) and the `edit` capability gate.
- Docs: ADR 0013, writes keyed by the page's natural receipts. BRIDGE_CATALOG `journal.mutationsGate`: "natural receipts (`days[d].xpAwarded`, `welcome[d].claimed[kind]`, `reviews[key].paid`) stored in the same save as the award; text writes are a set with a base check; no native pending record, nothing replayed after a restart".

**Phase 3**
- `feature/library/presentation/components/{MediaSheet.kt, MediaConfirm.kt}`; the tick on core `PageCheck` (from .wt/settings).
- The Media view model's optimistic tick and its retry rule.
- Web: commands in `50-native-library.js`.
- Capability `library.media.write.v1`. BRIDGE_CATALOG `library.mutationsGate`: "`addOp` stored on the item in the same save; status is a set; remove and remove audio are idempotent by id"; ADR 0013 extended to Media.

**Phase 4**
- `kt/bridge/web/NativeAudioPlugin.kt` (not a tab) and its BRIDGE_CATALOG `audio` entry and MainActivity registration (`check_bridge_catalog.py`).
- `kt/app/shell/audio/{AudioDockState.kt, AudioDock.kt}`. **One dock view owned by the shell, beside the bars:**
  - one instance, with ChromeHost's owner token;
  - shown above whichever native bars show (the shared ChromeHost bars, or the Overview's own);
  - ticking only while on screen.
- `ChromeHost.contentInsetsDp()` adds the dock's height while it shows. A dock that appears or goes pushes the new insets to the bar owner, so the open screen's content moves without a new projection. `NativeNutritionPlugin` takes the same push (a few lines).
- `NativeMediaPlugin`: the OpenDocument launcher in `load()`, `play` and `attach`.
- `seg/50-native-audio.js`.
- Settings row "Native audiobook controls (beta)", and `["nativeAudio", "#nativeAudioToggle", PREFS]` in native Settings' BOUND list.
- With `shell.audio.v1` present, `50-native-library.js` and `50-native-journal.js` drop `audioBusy()` from `blocked()`.

---

## 4. Phases

Each phase is shippable alone. Phases 1 and 2 sit behind **Native Journal (beta)**; 3 behind **Native Library (beta)** plus its capability; 4 behind **Native audiobook controls (beta)**. All are off by default.

**Phase 0: web groundwork** (no switch; behaviour-preserving except the honesty fixes and owner choices 2–4).
- The owner gets:
  - no more "+15 XP · Reflection saved" for a reward that was not stored;
  - claims, catch-up, reviews and swaps that say when nothing was saved and take back only the reward, never the words typed;
  - no private text in the page while it is locked, including right after Home or the grace minute;
  - a Journal that no longer renders itself twice when a recent day is selected;
  - (if chosen) a light-theme Journal whose off cells, tracks and lines are visible.
- Gate: coach, lock and media tests, the lock test's blur check made to wait on the save, and an HTML-parity test proving the builder split leaves the page's markup identical.

**Phase 1: the native Journal for reading, behind its own lock card.** §3.2–3.6.
- The owner gets the whole Journal native:
  - the switch never jumps;
  - instant tile selection;
  - the lock card with the system prompt, its status and its error;
  - no stale frame after Home;
  - a window that stays secure until the Journal is gone.

  Writing still hands over to the page.
- Gate: A33 items 2–5 for these states, the simulation with the lock (§5), and the owner's phone checks 1–14.
- Land after swipe, settings and ld2. Rebase onto their kit, `ComposeTabPlugin`, `TabSwipe`, fixtures, catalog and simulation changes, and set NativeApi at merge.

**Phase 2: writing in the native Journal.**
- The owner gets native typing for reflections and review answers, paid when the field is left; a native welcome card and catch-up; and the XP celebration drawn natively. The Journal no longer shows the web at all.
- Gate: ADR 0013 and the `mutationsGate` entry; repeat and failure tests for every paying command; the text-only autosave test; the flush-on-stop test; phone checks 15–17.

**Phase 3: the Media sheets.**
- The owner gets the native add/edit sheet, a tick with no flash, native confirms and links in the browser.
- Attach still hands over to the row's button, because attaching ends in playback, which keeps the pane on the page until the dock exists.
- Gate: catalog `mutationsGate`, the commands' repeat and failure tests, phone check 18.

**Phase 4: the audio dock, then the native picker.**
- Starts with a half-step spike: on the S25, pick a 300 MB `.m4b` natively and have the page fetch `/_capacitor_content_/…` into IndexedDB. Measure memory and time, and confirm that the temporary read grant holds. If it fails, attach and replace keep today's hand-off and the dock still ships.
- The owner gets audiobook controls on every native tab and the Overview. Native Media and the Journal stop stepping aside for a loaded book, Play becomes a command, and (spike permitting) attach opens the system picker from the native row.
- Background playback and lock-screen controls stay out of scope (a logic move under ADR 0004).
- Gate: dock screenshots, the simulation with the dock across hand-overs, phone checks 19–20.

**Phase 5 (optional, only if the phone shows the need).**
- While the native Journal covers the page, `renderHistory` skips its DOM build. It keeps `LifeMaxCoach.reset()` and `jcDecide(histSel)`, so decisions and the cache stay as they are. The kit re-renders once on close (`active()` from the ld2 kit change, plus a new `cfg.onClose`).
- The owner gets faster saves on a long history.
- Gate: tab timing (A25) and a save-time measurement on the phone before and after.

---

## 5. Tests

**Web (Playwright, run in CI's web-tests job)**
- **coach.test.js:**
  - the data builders against the HTML (the same fixtures render identical markup);
  - for each coach writer, a failed and a read-only save:
    - no success toast;
    - paid or claimed flags and coach activities taken back;
    - **the reflection, the typed review answer and the catch-up sentence still in memory and on screen**;
  - `swap` compare-and-set twice changes once, and a failed write puts the old id back;
  - `jcDecide` stores exactly what the page's drawing stores, compared on `journalCoach`; selecting an undecided recent day runs `renderHistory` at most once and no `save()`;
  - the builders create no record (a review opened twice leaves `journalCoach` unchanged);
  - the care note text verbatim and its precedence.
- **lock.test.js:**
  - `state()`, `onStatus` on prompt start, failure and cancel; `onChange` unchanged;
  - no reflection, activity name or hero number in `#journalHero`, `#journalDays` or `#journalEntry` while locked: after boot, after a save, **right after a visibility relock, after a grace-minute relock and after `relock()`**;
  - the unlock renders before it notifies (a watcher sees the entry drawn);
  - "typed text saved before the cover" waits for the save instead of a fixed delay.
- **media.test.js:** the three new owners (idempotent repeat, failed and read-only save), the `addOp` receipt, an unknown stored type kept, and confirm text without the backslash.
- **native-journal.test.js** (new):
  - every projected field compared with the page's own pane: stats, cells (colour, opacity, title), tiles (labels, bars, pens, quiet pills, reviews), day head, meta, insight, pattern, prompt, counter cases, timeline (time, name, pill, minutes, skill, XP, notes, plan notes), workout-notes card, empty and blank states, review grid and chart;
  - `snapshot()` changes nothing (state JSON before and after equal; no `localStorage.setItem`);
  - **memo:**
    - a 15 s refresh with no change does not rebuild the list (count `jcDay` calls);
    - the clock crossing midnight and 20:00 does;
    - a short reflection on an older day updates the tile's ✎ and the hero's count;
  - **selection:** a reply to `select` carries the new `selection` with an unchanged `list.rev`; the page's own entry shows the selected day afterwards;
  - **lock:**
    - a withheld snapshot contains no canary string (reflection, review answer, activity name, note, plan note);
    - a relock pushes a withheld frame at once;
    - the page's auto-prompt failing puts the error on the card frame, and a cancel clears it;
    - the first open frame after unlock already has the decided chip and welcome, and the next frame does not differ in content;
    - `unlock` relocks first when the page thinks it is open and replies `{ok:false}` (never `undefined`) when ignored;
    - notices ride in withheld frames;
  - **covering:** a focused textarea, `.luov`, a loaded book (`audioBusy()`);
  - **`write`:** the page's field is focused with the screen still open, the screen then steps aside, it does **not** come back while the orbs play, and it does come back after them. The same for `claim` and `catchUp`;
  - **queue:** `backup` replies before Save as settles, and a `select` sent during a pending Save as is answered;
  - **notices:** a toast shown by native Media is not shown again by the Journal it hands over to; a toast raised during a delegation is carried by the frame that brings the screen back;
  - Media's switch reaching native Journal with no close before it opens.
- **native-library.test.js:** unchanged through the notices move and `audioBusy()`. Phase 3 adds the commands; phase 4 removes the player terms and adds `play` and `attach`.
- **native-swipe.test.js:** Library-on-Journal is web with the switch off and native with it on; a start from the native lock card is allowed.
- **native-handover-sim.test.js (A28):**
  - WANT gains `history:'journal'`. Hops gain `library`, `tap-journal` (Media's switch) and `tap-media` (the Journal's switch).
  - A fake `BiometricLock` is added. A lock mode inserts random relocks (visibility hidden and visible), the lock switched on in Settings while the Journal is hidden, unlock ok/cancel, and page reloads between hops.
  - The fake Journal plugin models native honestly: data dropped at rest, the stop gate keyed by session.
  - New faults:
    - a frame carrying a canary that the page built while `blocksNative()` was true;
    - a hidden Journal screen holding open data at any point;
    - open data showing after settle while locked;
    - a Journal update while hidden;
    - the Journal not showing after settle on `history`;
    - the Journal stuck on the card after a reload with the lock off.
  - Shown failing on three mutations: the projection ignoring the lock branch, dropping the `onChange` subscription, and a fake native that drops only when `lock.on`.

**Fixtures** (`tools/gen_native_fixtures.js`, fixed clock 2026-10-28T12:00Z, `en-US`, `UTC`): a lived-in six weeks built through the page's own writers (activities, reflections, plan notes, a cat, rest days).
- They produce `fixtures/journal-{today,pastday,care,welcome1,welcome2,welcome3,week,month,empty,tip,locked,locked-error,longstrip}.json` and `journal-counter.json`, the page's counter answers for sample lengths.
- The generator asserts the locked fixtures hold no canary.

**Kotlin unit** (`native/src/test/.../feature/journal/JournalTest.kt`, plus `SecureWindowTest.kt` and swipe's rest-rule test):
- **decoder:** required keys, `pageTokens()`, older sequence ignored, malformed rejected keeping the last good state;
- **withheld:** a withheld frame → `Locked` with the open state gone;
- **rest:** → `Resting`, with or without `lock.on`;
- **stop:**
  - a showing `lock.on` screen goes to `Locked` and refuses a late open frame from the same session, then accepts the withheld frame and then the unlock;
  - stop with the lock off keeps the frame and sets no gate;
  - stop then a **new session** whose first frame is open → accepted;
- **SecureWindow:** held by either owner sets the flag; released by both clears it; a stale release changes nothing;
- **swipe's rest rule:** Shown → Parked or Gone while not open rests the pane; a source still sliding out does not;
- **view model:** local selection, the highlight following `selection`, the list object reused when `list.rev` is unchanged, the notice shown once, the counter choice against `journal-counter.json`, wire names.

Phase 2 adds: the draft kept across projections while focused, autosave timing with `final:false`, the final write on leave, hide and stop before the drop, the conflict reply, celebrations drawn once, and the retry reusing the request. Phase 3: sheet validation with the page's words, the op id reused on retry, the optimistic tick settling. Phase 4: extrapolation, clamp, the ticker only while on screen and playing, command mapping, one dock across a hand-over, insets pushed on show and hide.

**Screenshots** (Paparazzi, A32; square screenfuls at 1080 px, `maxPercentDifference = 0.001`):
- `JournalScreenshotTest`, dark and light:
  - hero;
  - strip (selected, today, review rows, quiet pill);
  - today with insight, pattern and prompt;
  - past day with notes and workout notes;
  - blank day;
  - care note;
  - welcome tiers 1, 2 and 3 (one with claimed chips);
  - weekly and monthly reviews;
  - empty journal;
  - tip;
  - lock card (idle, prompting, and with the error line);
  - resting state;
  - font scale 2.0.
- `GalleryScreenshotTest` sections: `PageSegments`, `PageTipCard`, spread and clip-out glow, elliptical radial gradient.
- **All Media, Plan, Recipes and Life Director references unchanged** after the core moves.

Phases 2–4 add: the editor (empty, typing, awarded), the media sheet, the confirm dialogs, the tick, the dock expanded and minimised.

**Phone checks** (S25 Ultra, release build, owner's checklist)

Phase 1:
1. Media↔Journal ten times: the switch never moves, no web flash, and tab timing is recorded.
2. Lock on: enter by the bar, the switch, Overview "History ›" and voice. The lock card shows and the prompt rises over it. Cancel, then a failure (the error shows at once), then success: crossfade, no web frame, nothing popping in.
3. Lock on, Journal open: Home, then Recents (the preview is blank), then back (the lock card, never your text). Repeat with screen off/on.
4. Leave for more than a minute, then come back (locked); less than a minute (open).
5. Lock on, Journal open: switch to Media and take a screenshot at once: blank until the Journal is gone.
6. With Native tab swipe on: lock off, open the Journal, go to Character, turn the lock on in Settings, Home and back, then swipe toward Library. The Journal slides in as its switch on an empty page, then the lock card; never text.
7. The tile strip on a long history: flinging and snapping, and a drag on it never swipes the tab. The highlight moves instantly; note how long the entry takes on old days.
8. Tap the reflection box: the page appears already on that day; does the keyboard rise? Type, leave: the text is saved, the reward and level-up play on the page, and only then does the screen return.
9. ↻ swap: no flash. Tip ✕.
10. After a 3+ day gap: the welcome chips.
11. Backup with the lock on: Save as appears; on return, the lock card, and "Backup saved" shows on it.
12. Light theme: the cells, tracks, pills and lines.
13. Largest font.
14. TalkBack: tiles read "Today, 177 XP" with their selected state, and the switch reads as tabs.

Phase 2:

15. A long reflection: the keyboard never hides the line being typed; the lines follow; the counter is live; no XP while typing, +15 XP on leaving the field.
16. Home mid-sentence with the lock on: the text is saved, paid if long enough, and the card shows on return. Force-stop mid-sentence: at most the last ~1.5 s is lost.
17. A review answer, the catch-up and the claims, each with its celebration.

Phase 3:

18. Add and edit with bad input and an old item of an odd type; the tick; remove; remove audio; a link.

Phase 4:

19. Play a book, then visit every native tab and the Overview. One dock follows, with no doubling during a switch or a swipe; seek, speed, minimise and close work; the position is kept after a restart; note what happens with the screen off.
20. Attach a 300 MB `.m4b` natively (also one reported as `application/octet-stream`); storage nearly full.

---

## 6. Risks and mitigations

- **A projection bug leaks private text.**
  - The withheld branch is the projection's first statement.
  - `JournalFrame.Locked` and `Resting` cannot hold data.
  - A screen not on show holds nothing, whatever the lock state. A showing screen locks itself on stop, and the session gate refuses late frames.
  - Canary tests in web tests, fixtures, the simulation and Kotlin, each shown failing on a mutation.
  - The page also stops putting text in its DOM while locked, including right after every relock.
- **A stale unlocked frame after Home.**
  - `handleOnStop` locks locally with no page round trip.
  - `open` accepts the new frame before the pane is shown.
  - `unlock` relocks first when the page missed the hidden visibility (not verified on device).
- **The window uncovered mid-switch.** The native secure hold lasts until the Journal has rested, whatever the page's flag says.
- **The gate outliving the page.** It is keyed by the page session, so a reload (OTA restart, import) clears it. It is never set while the lock is off.
- **The coach writes while drawing.** The projection uses pure builders only. `jcDecide` keeps the page's decision moments (render and select) and stores decisions by direct write, with no nested render. A test compares state before and after `snapshot()`.
- **Rewards on a lost reply or a failed save.**
  - The receipts are the existing keys (`days[d].xpAwarded`, `welcome[today].claimed`, `reviews[key].paid`), stored in the same single write as the coach activity.
  - The text is stored first, so a failed award never takes the owner's words with it.
  - A33 item 3's repeat and failure tests; ADR 0013 and the `mutationsGate` note.
- **Long-history cost.**
  - Hero and list are memoised on `histRev`; the coach cache is reused warm; `list.rev`; `select` redraws only the page's entry.
  - Phase 5 skips the hidden DOM if the phone needs it.
  - The page's own `renderHistory` cost per save exists today, and phase 0 removes its nested second render.
- **Time.** The memo key carries the date and the 20:00 flag, and the 15 s refresh catches both. The page underneath stays as stale as today, but the next save converges it.
- **Locale.** Every date, time and number string is projected; nothing is formatted natively.
- **Phase 1 writing flash, and a keyboard that may not rise on programmatic focus.** The field is focused and on the right day before the screen steps aside. It is a beta, phase 2 removes it, and the owner checks it (item 8).
- **The screen coming back over a celebration.** `returning` and `acting` hold `blocked()` until `afterCelebrations` ends, so the body watcher's revisit (the orbs are appended to `<body>`) is refused until then.
- **IME ownership (NAV-5).** MainActivity pads `android.R.id.content` by the IME and consumes the insets (MainActivity.java:119-126), so Compose `imePadding` is a no-op. Phase 2 keeps that padding, uses `bringIntoView`, and hides the shared bottom bar while the IME shows; decided and recorded in BACKLOG.
- **Glow fidelity on translucent boxes.** Core `boxShadow` draws under the box (Css.kt:35-45), which would brighten the translucent cells and nodes. The clip-out variant and spread are added with Gallery proof.
- **BiometricPrompt and FileSaver over the native screen** are unverified on device: phone checks 2 and 11. Fallback for the prompt: close to the page's cover (the web wall) behind a flag.
- **A command queue held by a long call.** `backup` and `attach` reply at once and report through notices; a test sends a command during a pending Save as.
- **Swipe interplay.** No registry change. The parked Journal draws only its resting state. The tile strip and the editor carry `noTabSwipe`. The lock-card start rule and the rest callback are agreed with the swipe track.
- **Merge conflicts.**
  - `50-native-tabkit.js`, the simulation, `gen_native_fixtures.js` and `BRIDGE_CATALOG.json`: swipe, settings and ld2 all touch them.
  - `ComposeTabPlugin.kt`, `ChromeHost.kt`, `50-native-overview.js` and the new `TabSwipe.kt`: swipe.
  - `settings.html` and BOUND: settings and swipe.
  - `MainActivity.java`: settings adds a plugin.
  - `NativeApi.java`: all of them.

  Land after all three, rebase, set NativeApi at merge, and regenerate module maps.
- **Picker spike fails** (grant, fetch or Blob memory): attach and replace keep the button hand-off; the dock still ships.
- **Dock drift and battery.** Local extrapolation, a 1 Hz ticker only while the dock is on screen and playing, and corrections on events and every 10 s. One dock instance, so no doubling during a hand-over.
- **Duplicated Media adds after a lost reply.** The `addOp` receipt is stored on the item (an additive field; `SAVE_SCHEMA` unchanged).
- **Care-note wording drift.** One page constant feeds both renderers, and a test asserts the exact text.
- **A never-settling biometric promise leaves the page "prompting".** It already exists on the page (48-journal-lock.js:46-48). The card shows "prompting" from `state()`, its Unlock comes back after a reload, and it is noted in BACKLOG.
- **A toast lost on a frame that was never sent** (the screen was leaving as it claimed it). Rare and harmless; recorded in the guide.

---

## 7. Effort

Unit: an **agent build step** is one focused, tested commit the size of the Library track's "web half" (a65a73d: 15 files, about 2,270 lines with fixtures) or "the screen" (a5d3226: 41 files, about 2,160 lines with references). For comparison:
- **Library (Media pane)** was about 6 steps: phase 0 ×2 small, web half, screen, review fixes, docs.
- **Recipes** was about 4: web half, screen, two reviews.

| Phase | Steps | Breakdown |
|---|---|---|
| 0 Web groundwork | 2.25 | Coach split, narrowed `jcDecide` by direct write, honest writers split into text and award, `histRev`, `selectJournalEntry`, tests (1.25). Lock status, unlock order, blank on relock and at load, the deflaked blur check (0.5). Media owners, op receipt, `audioBusy()`, light-theme and tip choices, tests (0.5) |
| 1 Native Journal, reading and lock card | 5.25 | Web half: projection, memo, shared notices, commands and delegations with their way back, fixtures, simulation with the lock and reloads, the swipe test and rule (1.5). Screen A: plugin, decoder, rest and stop handling with the session gate, `SecureWindow`, TabSwipe rest callback, lock card and resting state, switch, tip, hero, core moves and Css helpers (1.5). Screen B: tiles, day page, coach cards, timeline, reviews, screenshots (1.25). Review fixes (0.75). Docs, ADR 0012, A35 (0.25) |
| 2 Native writing | 3.5 | Core text area and IME decision (1). Write commands with text-only autosave, celebration moved to core (1). Final writes, flush, conflict, retry, ADR 0013, tests (1). Review (0.5) |
| 3 Media sheets | 2.5 | Web commands and owners wired, catalog gate (0.5). Sheet, confirms, tick on `PageCheck`, link (1.25). Review and screenshots (0.75) |
| 4 Audio dock and native picker | 4 | Web state and commands (0.75). The shell-owned dock, pushed insets for every Compose tab, Food and the Overview, screenshots (1.5). Picker spike (0.5). Launcher, MIME check, `attach` outside the queue (0.5). Covering rules relaxed in both Library kits, simulation, review (0.75) |
| 5 Hidden-DOM skip (optional) | 1 | Only after phone timing shows it is needed |
| **Total** | **≈ 18.5** | **Phases 0–1 (the readable native Journal) ≈ 7.5**, a little more than the Library's Media track, because the Journal draws about twice the shapes and carries the lock with its native safeguards |
