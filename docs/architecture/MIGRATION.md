# Migration plan and initial ownership ledger

## Current baseline

| Area | Presentation | Authority | Web dependency |
|---|---|---|---|
| Overview build 172 | Hybrid native controls/live web body | Existing web owners | Required |
| Character | Web | Core gameplay, web-owned | Required |
| Cash | Existing native activity/presentation | Existing web command/state path | Required |
| Core gameplay | Mixed consumers | Web-owned | Required |
| Life Director | Web | Feature/core coupling audit required | Required |
| Settings/platform stores | Mixed | Inventory each store; no new cutover assumed | Required where currently used |

These are baseline descriptions, not a claim of complete writer inventory or transaction safety. Native Cash currently delegates commands through 50-native-cash.js, calls existing web operations/save, verifies persisted money and attempts in-memory rollback. Its session-local seen map does NOT establish restart-safe idempotency or atomic whole-gameplay commit. Do not copy that pattern as the new mutation contract.

## Character: first implementation sequence

### C0 — Inventory and deterministic fixtures

Read current character.html, 01/04/07/14/15-runtime.js, later wrappers, navigation, stores and CSS. Produce CHARACTER_INVENTORY.md identifying exact displayed facts/derived values, chart and comparison modes, selection/sorting, skill progression, avatar, weight/BMI, benchmarks and every action/sheet/navigation path. The eight IDs are physique, endurance, intelligence, business, social, discipline, adventure and wellbeing. Benchmark addition is visibly present in the baseline; inventory its complete handler path and any generated attribute-detail actions rather than claiming Character has no mutations.

Use synthetic fixtures for new, established and legacy/ascended states, plus missing/custom/large-history cases. Do not publish personal saves. Measure fixture size and storage headroom; no 5-MB quota assumption is a substitute for handling actual write failure. Capture the current visual and interaction baseline.

### C1 — Build and native foundations

Register direct authored source/test roots idempotently and verify compile/test discovery, inclusion once and rename/removal behavior. Use the existing Compose setup. Add only required theme primitives, manual composition root and one MainActivity destination host coordinated with existing navigation. Preserve existing APK signing, package identity and update flow.

### C2 — Read-only native Character prototype

Finalize the static character projection schema from C0. Implement capability/readiness negotiation, full snapshots and session/projection-sequence freshness through a feature-owned repository and lifecycle-bound transport. No new native gameplay writes or durable bridge metadata solely for this read slice. Snapshot invalidation must observe legacy changes and time-derived values even when durable save revisions do not exist.

Build native charts/cards, selection, comparison and sorting from the authoritative projection and local UI state. Render no Character DOM/captured page in the native presentation. Include loading/error/reconnect states, font scaling, light/dark/accent themes and reduced motion. Native formatting/layout math is allowed; rewards and gameplay calculations remain authoritative.

Integrate behind a persisted native/classic choice with session fallback on unavailable capabilities. An incomplete developer prototype is not a default production replacement. For existing mutating actions, either delegate explicitly to their working web presentation and record that hybrid dependency, or hold default rollout until the native interaction is complete. Never silently omit a button or pretend it saved locally.

### C3 — Lifecycle and acceptance

Verify shared state after real web actions, stale/out-of-order read responses, live/time-sensitive changes, native covering the web authority, navigation/Back, rapid tab changes, background/resume, rotation, process recreation, unreadable/unavailable data and Classic cleanup. Confirm no duplicate hosts, leaks, background render load or transition flash. Record physical-device performance separately from automated/emulator checks.

### C4 — Mutations and full Character interaction parity

Before each native write capability, adopt an operation-specific command/commit/reconciliation design satisfying CONTRACT A14–A15. Audit legacy save handlers and side effects; do not wrap arbitrary global mutations and label them atomic. Test the failure matrix before wiring the control. Replace remaining web sheets/edits and verify both native and existing web consumers observe the same authoritative results without duplicate effects.

Only then label Character native presentation with complete interaction parity. Core authority can still remain web-owned. Retiring that dependency is a separate later cluster cutover.

## Per-feature/aggregate record template

- Presentation implementation, native coverage and remaining web sheets/tools.
- Host, entry points and Back/navigation owner.
- Authority scope/owner, persistence keys, schema version IF VERIFIED, stored facts and derived projections.
- All writers and cross-domain/store side effects; gaps explicitly recorded.
- Read/command catalog versions; session/projection freshness and, when implemented, epoch/revision semantics.
- Pending-operation recovery and receipt-retention/admission policy for each mutating capability.
- Native rule/importer shadow-verification status and equivalence evidence.
- Consumers remaining; safe fallback combinations and import/export/older-reader limits.
- Rollout state, current commit/build, automated/screenshot/device evidence.
- Temporary exceptions, next ownership gate and retirement conditions.

## Native Character prototype implementation

The `codex/native-character` slice implements Compose presentation behind the opt-in Native Character preview setting (Classic remains default). Models and owner calculations enter through `50-native-character.js`, with a volatile session/sequence and no durable gameplay writes. Kotlin decodes into immutable projections; a feature repository interface and ViewModel own native presentation state. The platform plugin attaches ComposeView to MainActivity and owns its lifecycle references. Main/test roots are registered directly in Gradle. ktfmt 0.53 formatting is checksum-pinned and checked for the new source tree.

Implemented: hero, radar current/previous comparison and selection, attribute sorting/cards, selected detail and spark bars, skills/show-all, benchmark readout and original-handler delegation. Logging, Luck and benchmark editing remain explicit web dependencies. Native ownership, safe mutating commands and complete native interaction parity are NOT implemented. Physical-device testing, restored transient UI state and complete visual/accessibility parity remain rollout gates; do not label this fully migrated Character or enable it by default.

Evidence: Node projection test, JVM selection/sequence/schema tests, Android Compose-in-MainActivity/no-WebView-subtree test and unchanged core-save assertion; reference/native/scroll/light screenshots are emitted by CI for review. Synthetic projection fixture is not a claim of complete raw-save migration fixtures.


## Ledger as of Native API 17 (2026-10-30)

| Area | Presentation | Authority | Rollout | Remaining web dependency |
|---|---|---|---|---|
| Overview | Hybrid shell (native bars) over the live WebView | Web-owned | Native is the default; Classic is a setting | The whole body |
| Character | Compose screen, hidden-but-alive | Web-owned | Opt-in preview | Logging, Luck, benchmark editing |
| Food (Nutrition) | Compose screen (Plan, Log, Shopping), hidden-but-alive | Web-owned | Opt-in beta | Add-food sheets and swap sheet delegate to web |
| Cash | Native activity (Compose) | Web-owned commands | Existing | Commands and state |
| Life Director (Today) | Compose Today screen (header, sections, day strip, Canvas dial, centre, mix, up next, all-day quests, running order), hidden-but-alive, over the page while Today is clear | Web-owned (Life Director store and engine) | Opt-in beta, off by default | Every tap but the day strip runs on the page: session, meal and timer sheets, Done, the guide, Something came up, Re-plan, banner buttons; Week, Goals, Quests, My time and Settings; dial handles and the build animation |
| Library (Media) | Compose Media pane, hidden-but-alive, over the page while nothing it does not draw covers it | Web-owned | Opt-in beta, off by default | The add/edit form, finished checkbox, remove, attach/replace/remove audio, the player, link, tip, Journal |
| Tab shell (bars) | Native drawing of a DOM capture | Web page state | Shared by the three native tabs | DOM capture (see CONTRACT_REVISION_2_PROPOSAL.md, A22) |

Statements here are from reading the repository and the Settings wiring; device acceptance is not recorded.

## Source layout as of Native API 20

Every native Kotlin file now lives in the canonical tree (`android-wrapper/native/src/main/java/com/lifemax/rpg`) and is formatted with ktfmt; CI checks all of them. Java plugins stay in `android-wrapper/native/` until a feature needs them converted.

| Path | Holds |
|---|---|
| `app/shell` | `ChromeHost`, `OverviewBars`, `TabTiming`, `LiveOverviewShell`: the shared bars and the tab hand-over |
| `app/CharacterContainer.kt` | composition root for Character |
| `bridge/web` | `ComposeTabPlugin` (the shared base of a native tab) and one Capacitor plugin per native destination: Character (a subclass), Food (`NativeNutrition`), Overview, Cash, plus `HealthSync` |
| `core/designsystem` | theme tokens from the page |
| `feature/character` | domain, data, presentation (the reference layout) |
| `feature/food/presentation` | `FoodContext` and one file per pane (Plan, Log, Shopping), the header, week strip, switcher and search bar; rendered by screenshot tests, still reading raw JSON |
| `feature/cash` | `CashActivity` and its session (a documented activity exception, A04) |

Removed in this step: the retired Canvas `OverviewActivity` (nothing opened it since the live Overview shell), its manifest entry and its test assertion.

## Prep completed before the native rewrite (Native API 20)

| Item | State |
|---|---|
| ktfmt over every native Kotlin file, enforced in CI | done |
| Every native Kotlin file in the canonical tree, retired Canvas Overview removed | done |
| Bridge catalog entry for every destination | done (CI test that enforces it still to come) |
| JavaScript ESLint ratchet in CI | done (`tools/lint_js.py`) |
| Local Android build and unit tests (`tools/build_android_local.sh`) | done |
| Screenshot tests with reference images, in CI | done (Character dark/light; Food Log, Plan, Shopping, Plan light; Workout plan) |
| `NativeTabKit` (web) and `ComposeTabPlugin` (native) | done; Character uses them, Food and Cash still have their own |
| Food screen split into a context and panes | done, screenshots identical |
| Tab hand-over simulation on virtual time, races fixed, safety net | done (A23 items 7 and 8, A28) |
| Log activity logic callable without the form, 163 golden cases | done |
| Food and Cash onto the shared scaffold | to do as each is converted |
| Automatic backups before any data or logic port | done (ADR 0004); on the phone once the owner picks a backup folder |

## Log activity (Native API 23)

Native presentation, web authority. `NativeLogPlugin` (ComposeTabPlugin) with `feature/log` (domain, data, presentation) and shared `core/designsystem/components`. The page projects presets, attributes, effort levels, the duration limits, the draft and the XP Save would pay with the page's reason when it is not the raw total (`50-native-log.js`, from `activityAward`, the function the save pays with); the screen sends `preview` and `submit` commands through the new command channel of NativeTabKit/ComposeTabPlugin, which never closes the screen. Opt-in beta in Settings. A prefill in the web form (Life Director, links) is taken over when the screen opens. Remaining before it can become the default: physical-device check by the owner, and the backlog items tagged Log.

## Workout plan (Native API 25)

Native presentation, web authority. `NativePlanPlugin` (ComposeTabPlugin) with `feature/plan` (domain, data, presentation) draws the page's projection: the guided-workout launcher, the active plan, the calendar of weeks and days, the selected day's exercises and the General rules, and the page's toggle message and XP celebration. A ticked box shows the state asked for, faded, until the page answers; the answer comes with the page's fresh projection, so a refused or failed save shows the box as the page holds it, and a box whose answer is lost takes the projection's state again after 5 seconds. `50-native-plan.js` (NativeTabKit, `plan.projection.v1`, Settings "Native Workout plan (beta)", off by default) projects every week, day and item of the active plan, already worded as the page words it, with the page's selected day (`selected.revision`), the guided-workout launcher, the program key and the General rules. Day and week selection are the screen's own. Ticking a set is the `toggle` command, which runs the page's own `planToggle` with set semantics; the celebration comes back in the reply for the screen to draw, with the orbs flying to the top bar's logo as on the page, and the page plays its sound and buzz. A failed save is reported as not saved and, as after the page's own checkbox, stays in memory with everything the save set off until the next save stores it. Inventory: `PLAN_INVENTORY.md`; fixtures from the web app: `native/src/test/resources/fixtures/plan-*.json`.

| Remaining web dependency | How the screen reaches it |
|---|---|
| Guided workout runner (classic and 3D gym) | closes; `gwrResume` or `gwrStart(day)`; back when the runner closes |
| Value and session-log fields | closes; the page selects the day and focuses the field; back when the field loses focus |
| Leave plan (confirm) | closes; `leavePlan`; back if the plan is kept |
| Sign-up offers (no plan) | not covered; back when a plan is started |
| Level-up overlay, sheets, badge toasts | the screen steps aside for an overlay and comes back; badge toasts are not visible under the screen |

Screenshots: `PlanScreenshotTest` (each fixture dark and two light; the exercises of today with a box waiting for the page, of a done day and of a day to come; the toast in both themes). Not yet done: the owner's check on the phone, including whether the keyboard rises for a field handed to the page.

## Life Director, Today (phase 1 of 4, Native API 25)

Native presentation of Today, web authority. Life Director stays the only writer: `LifeDirector.native` (`src/features/life-director/logic.js`) returns the frame (header, section tabs, banner, last message) and Today (strip, dial, centre, mix, up next, reminders, running order) as data from the same decisions as its renderers, says what the page is showing, runs a handed-back tap through the same code as the tapped element (`perform`), and tells listeners when what it shows changes. `50-native-director.js` (NativeTabKit) covers the page only while that is Today with nothing over it, keeps the chosen day itself (command `day`, read-only; today again whenever the user comes to Life Director, kept when the screen comes back after a delegated tap), and delegates every other tap: the screen closes, the page runs the tap, and the screen comes back as soon as Today is clear again (a sheet or the guide closes, Today is chosen again) and the XP celebration of a Done or a finished timer has played on the page (`afterCelebrations`, 07-runtime.js). A tap on a session or meal that left the plan after the screen drew it is answered with the page's message saying so. Fixtures come from the engine planning a lived-in week (`tests/fixtures/director-day.js`, `tools/gen_native_fixtures.js`). Remaining web dependencies: everything in the ledger row above. The native screen (`NativeLifeDirectorPlugin`, `feature/lifedirector`) draws the dial on a Compose Canvas in its 360-unit square: its face, arcs, words and icons are prepared whenever their data changes (`DialArt`), so the 15-second refresh that only moves the time redraws prepared shapes; the next session's halo pulses and the hand turns in layers of their own, and nothing pulses or ticks while the screen is hidden (`ComposeTabPlugin.visible`, A24) or the app is in the background. Taps find their arc by ring and angle, and an arc shorter than 48 units is also reached from just beside it, so every arc is a target at least 48 dp long; every arc is also a TalkBack node with its state. Screenshots, each a screenful at full resolution, all from the page's own states: the whole of today, tomorrow, the light theme with the earlier blocks open, the dial alone, a running timer, a session about to start and one late, late evening (a session done in part, nothing doable before tomorrow), a failed save's banner with its message, a day asked for, no plan yet, and larger text. Known differences from the web: no drag handles and no build animation on the dial yet, so the hint under it leaves out the handles; Roboto and the system monospace font instead of Inter and JetBrains Mono; buttons and the "Earlier today" fold at least 48 dp tall. Next: dial dragging (phase 2), Week, Goals and Quests (phase 3), then sheets, forms, set-up and the guide (phase 4).

## Library, Media pane (phase 1)

Native presentation, web authority. `50-native-library.js` (NativeTabKit on view `media`, plugin `NativeMedia`, `library.media.projection.v1`, Settings "Native Library (beta)", off by default) projects the Media pane worded as the page words it: the Media/Journal switch, the header and the four stats, every item (the stored type and status that the filter compares, the page's subtitle and status label, the notes, the link only when it is http or https, and for an item with audio the page's progress label, time and bar and its file line), the filter controls with the page's current filter, the empty and no-match states, the button words, the player state and the feature tip while the page would show it. Search, status and type filters are the screen's own; the page's filter carries a revision that rises whenever the page sets it (typed on the page, or a voice search), and the screen adopts the page's filter whenever that revision differs from the one it holds. `fixtures/media-filter-parity.json` holds the rows the page itself shows for sample filters of the full list, so the screen's filter can be proven identical. Every tap is delegated: the kit closes the screen, the page's handler runs, and the screen comes back (`revisit()`) once nothing it does not draw is over the pane. It never covers the pane while the add/edit form is open, while the audio player is loading, loaded or showing (until a native mini-player exists, so the player's controls never vanish under a screen), while a file button handed to the owner has the focus, while a handed-back tap is still running, or while a level-up plays; it follows these through MutationObservers (one of them on the list, which every save redraws, so a play that fails on the page gives the pane back) and a focusout, never by reassigning a page function, and `blocked()` also reads the page's `audioCtx.loading` flag. The page's toast lies under the screen, so the projection carries it as `notice` while it shows, numbered so that the same words twice are two messages, and a toast that comes while the screen shows is sent at once; the screen draws it as the page's toast (core `PageToast`) once, so every delegated tap comes back with the page's own answer, success or failure. A toast cut short by the screen closing is not shown again, and the toast that names a file button is left out, as it is about the page. The Media writers a tap reaches say honestly when nothing was stored: ticking, removing and removing the audio take the change back when the save fails and report it (`unsavedMessage()`, naming a read-only save as the reason), and the audio file is deleted only after its removal is stored. The Journal stays on the page: the switch's Journal is a web destination, so the screen closes at once and the Journal lock works unchanged.

Phase 0, web only, came first: a reflection or review answer being typed survives a redraw and a relock, and a failed reflection write is reported; `LifeMaxJournalLock.blocksNative()` and `onChange(fn)` are the seam a native Journal will follow (nothing native draws the Journal yet); `mediaUpsert(fields)` is the DOM-free owner of adding and editing, split from the form; voice "journal" and "history" open the Journal; Media has its own tests (`media.test.js`). Inventory: `LIBRARY_INVENTORY.md` (section 9 lists what this work changed); fixtures from the web app: `native/src/test/resources/fixtures/media-*.json`.

| Remaining web dependency | How the screen reaches it |
|---|---|
| Add and edit (the media form) | closes; `showMediaForm()` or `showMediaForm(item)`; back when the form closes, saved or cancelled |
| Finished checkbox | closes; `toggleMediaFinished(id, true)` for `finished`, `(id, false)` for `unfinished`; back at once |
| Remove (confirm) | closes; `deleteMediaItem(id)` with its confirm; back at once |
| Attach or replace an audio file | closes; the row's own file button is shown, focused and named in a toast (a page filter hiding the row is cleared quietly), since a file picker opens only from a tap on the page; back when the button is left, or when the player playing the chosen file closes |
| Remove audio (confirm) | closes; `removeMediaAudio(id)`; back at once |
| Play, and the whole audio player | closes; `playMediaAudio(id)`; the pane stays on the page while the player is loading, loaded or showing; back when it closes, or when a play started on the page finds its file missing |
| Open link | closes; an anchor like the row's (new window, no referrer), http and https only; back at once. Not yet seen on the phone: that Capacitor hands it to the browser |
| Feature tip | closes; `dismissFeatureTip('media')`; back at once |
| Journal (the switch) | closes; `lmaNavTo('history')`, the web Journal behind its lock |
| Level-up overlay | the screen steps aside and comes back |

The native screen (`NativeMediaPlugin`, `feature/library`, `app/LibraryContainer.kt`) draws everything the pane shows, sized from the page's styles at phone width: the switch, the page head and its add button, the feature tip, the four stats with the Completion bar, and one card holding the search box, the two selects and the rows (finished box, title, type and creator, an audiobook's progress, the status and link pills, the notes, the audio buttons and file line, Edit and Remove), or the empty or no-match message in their place. The rows are a LazyColumn keyed by the page's item ids, so the card is drawn in a piece per item that joins into one border and one shadow. The search and filters are the screen's own (`MediaViewModel`) and give exactly the page's rows (`MediaFilter`, the page's mediaMatching with JavaScript's trim; proven against `media-filter-parity.json`); the page's filter is taken whenever its revision changes. One tap goes to the page per opening, as the page answers each by closing the screen. For TalkBack each filter's control carries its label (the search box's label is part of the text field, as a browser reads the page's label), and the tip's icon is hidden, as the page hides it. Known differences from the web: the system's fonts (Roboto and its fallbacks for ✓ ▤ ▶ ↗ 🎧), as in the phone's WebView, which has no Inter; text and padding laid out in whole pixels, so each row is about 1.5 to 4.5 dp taller than the page's (an audiobook 285.5 against 281 dp) and the top of the screen 2 to 4.5 dp lower, which adds up to about 33 dp by the 14th row (measured at 412 dp against the page in Chromium, both in Roboto); Android's line breaking, which follows Unicode's rule against breaking before "/" even after a space where Chrome breaks at any space, so the audiobook file line breaks after "resume" instead of after "4:10:00" (Compose already breaks greedily, as CSS does: its default is `LineBreak.Simple`); a flat background without the page's radial glows, as on the other native tabs; the search box always on a line of its own (the page puts it beside the selects in a pane wider than about 430 dp); a select opens a menu under it instead of the WebView's list dialog, and is as wide as its longest option plus what Chrome adds (47 dp, measured); the tip appears without its 0.35 s fade (the projection carries no reduced-motion flag); no 1 dp inset highlight on buttons and cards; the tick keeps its size at large font sizes; the whole link pill takes the tap, not only its words; the keyboard's search key puts the keyboard away. Targets keep the page's sizes, which Compose widens to 48 dp for touch without moving anything; the tip's ✕ has a full 48 dp target (`tapTarget`) inside the tip's padding.

Evidence: `native-library.test.js` (37 checks: the projection compared with the page's own pane, filter adoption including a voice search, each covering rule, each delegation and its return with the page's toast, a delegated write that is not stored, a play that fails on the page, the file button's toast left off the screen), `media.test.js` (each Media writer's failed and read-only save), the tab in the hand-over simulation's fakes; `LibraryTest` (decoder, malformed and older projections including a missing or mistyped key, the 15 parity filters directly and through the view model, the trim, filter adoption, the page's message shown once, every action's wire name, one tap per opening); `MediaScreenshotTest` (21 square screenfuls at the device's resolution, 1080 px, dark and light: the top, the stats, an audiobook row, finished and linked rows, an audiobook not played yet and an old item, the end of the card, the voice search's list, no match, the empty library, the tip, and the largest font size), and the toast in `GalleryScreenshotTest`. Paparazzi draws a single frame, so three states have no image: a select's open menu (a popup), the focused search box and the toast rising over the screen. Not yet done: the owner's check on the phone, including the keyboard over the search box, the menus of the selects, and TalkBack on the filters.
