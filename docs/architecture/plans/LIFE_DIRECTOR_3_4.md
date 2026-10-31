# Life Director, phases 3 and 4: everything still drawn by the page, made native — build plan

Paths are relative to /home/user/Lifemax-rpg.
- "logic.js" means `lifemax_modular_v73/src/features/life-director/logic.js` on main 8ad7560.
- "ld2" means `.wt/ld2` (track/ld2, c49e94f). It branches from 958eeee, not from 8ed253c. List its commits with `git -C .wt/ld2 log 958eeee..HEAD` (the same 7 commits as `8ed253c..HEAD`). Diff it with `git diff 8ad7560...c49e94f`. A plain diff from 8ed253c shows main's later fixes as removals.
- Line numbers are main's unless marked ld2.

Scratch builds and probes:
- /tmp/ld34-main (main built) and /tmp/ld34-scripts;
- the reviewer's /tmp/ld34-review/probe-review.js, re-run by me on /tmp/ld34-main/page.html;
- my /tmp/ld34-final/probe-ov.js.

Nothing in any worktree was changed.

## 1. Decision

**Build it as one track with three merges:**
- **Phase 3:** Week, Quests, Goals and Settings, built in two halves (3A, then 3B) and merged once.
- **4A:** everything on Today.
- **4B:** My time and set-up.

Each merge ships behind the existing "Native Life Director (beta)" switch, which stays off by default. Each section's guide ships in the same merge as the section. The keyboard fix (BACKLOG NAV-5) lands before 4B as its own small shell change.

"One track" means:
- one inventory;
- one projection that grows;
- one command table;
- one view model family;
- one beta switch.

Each merge only widens what the native screen covers, so each one leaves the app working, with the rest handed to the page exactly as phase 1 does today.

| Merge | Half | What becomes native | Guide | Writes |
|---|---|---|---|---|
| **Phase 3** | **3A** | Week, Quests, the section tabs, and the section flick between native sections. Also the coach-mark layer. Page and kit fixes for verified hand-over bugs (§3.2, §3.3, §3.6) | Week's (3 steps) | none |
| | **3B** | Goals and Settings, native confirmations, Copy and Restore backup, Diagnostics. Also `SheetHost` in core, with phase 2's confirmation moved onto it | none (these sections have no guide) | set-like |
| **4A** | | The session, timer, meal, Move and "Something came up" sheets. Done with minutes, Log it for today, Skip, Pin, Re-plan, the banner buttons and the energy check-in. XP celebrations drawn natively. A glance tap opens the native sheet | Today's (dial modes, name tags, pulsing handles) | XP-paying, keyed |
| *(shell)* | | NAV-5: the shared bottom bar hides while the keyboard is up, on every native screen | | none |
| **4B** | | My time (with the mini sleep dial), the first-run card, set-up and the first build, the commitment sheet, and the goal type and goal form sheets | Set-up's (mini dial name tags) | forms, keyed |

**What stays on the page, and how the screen reaches it**

| Stays on the page | Why | How the screen reaches it |
|---|---|---|
| The planning engine, the store, every rule and every message | The page is the only writer (A10). | Every write is a kit command that calls the page's own function (§3.5). |
| The Simulation Lab section | It is a developer harness, on by default only outside LifeMax (logic.js:2647). | Its section tab hands over: the screen closes and the page shows Lab. Its toggle is native (3B). Listed under `delegatedInteractions`. |
| The Quest Board (3D table) | Owner's rule (ADR 0003). | "Open Quest Board" is the `navigate` action to `quests`. The kit goes there without closing first, and a web view closes the screen at once (A23 item 2). |
| Plan, and Food's Plan pane (meals) | They are other tabs. | "Open in Plan" and "Open meal menu" are commands that navigate while the screen still covers the page, like Recipes' `log`. The reply never arrives, by design (NATIVE_TAB_GUIDE). |
| The Overview glance, the widget picture, session reminders, voice, calendar import, Money pay, the Journal coach | Other surfaces read Life Director or write through its seams. | Unchanged. From 4A a glance tap opens the native sheet. Voice and calendar writes reach the screen through `render()` → `onChange`. |
| Each guide until its section's merge | It measures the DOM. | Today's guide stays the page's until 4A, set-up's until 4B. The screen steps aside for them, as phase 1 does for Today's. 3A makes Back end them. |

**Why this shape**
- **The page stays the only writer.** Today the handlers read the DOM: `val('#…')`, `:checked`, `b.checked` and `b.parentElement.open` (logic.js:4428-4599). Each write case becomes a small DOM-free function in one frozen `Do` object, which takes values.
  - `perform()` and `onChange()` read the form, then call the same `Do` function.
  - The native command calls it with a payload.
  - So there is one code path, and both callers behave identically. The precedent is the Library's `mediaUpsert` split (07-runtime.js:144-150).
- **Projections sit next to the renderers, as phase 1's do.**
  - `nativeWeek()`, `nativeQuests()`, `nativeGoals()` and the others return the same decisions and words as `renderWeek()` and its siblings, as data, the way `nativeToday` mirrors `headerHtml` (logic.js:4883-4890).
  - A projection-against-DOM test proves parity.
  - The classic renderers are not rewritten. Only a few tiny pure helpers are extracted where both sides need the same arithmetic: the week bar spans, the unscheduled groups, the XP yield, the goal share with its fallback, and Move's open times. Each extraction is covered by an HTML golden recorded before it.
- **There is one day value and one section value, both the page's own.**
  - The kit today keeps its own `day` (50-native-director.js:7, 50-52), which causes the verified Week bug. From 3A the `day` and `gotoDay` commands write the page's `ui.dayIdx`.
  - "Today whenever you come to Life Director" moves from first open to arrival (§3.2), so a page-drawn Week can never reset the day the owner just chose.
- **Coverage grows by capability, and the capabilities are known before the first entry.**
  - The kit asks the plugin's handshake as soon as the tab is enabled, not first inside `open()` (§3.3).
  - An old APK with a new page covers only Today. A new APK with an old page gets no `covers` field, so it also covers only Today (A19).
- **Every write is safe to repeat (A14/A15, A33.3).**
  - Each command either sets a value (absolute minutes, on/off, a priority) or carries a key that refuses a repeat. A key is an operation id that becomes the event or commitment id, or a timer's start time.
  - A repeat of a write that already landed answers `ok`, `changed:false`, "Already recorded." It is never an error. The full table is in §3.5.
- **XP is celebrated the way Workout plan does it.**
  - The reply carries the held celebrations, the screen draws them, and the page plays only the sound and buzz (`xpCue`; 50-native-plan.js:221-229, 07-runtime.js:77-88).
  - A level-up still shows on the page. The screen steps aside for it and comes back by itself once it is dismissed, because Life Director now passes `watch()` as Workout plan does (50-native-plan.js:267-273; NATIVE_TAB_GUIDE line 37).
- **Each guide ships with its section.**
  - The coach-mark layer arrives in 3A with Week's three plain steps. Today's dial modes come in 4A, and set-up's mini-dial tags in 4B.
  - No native section ever hands over to a page guide for its own first visit.
  - From 4A, `showing()` never reports 'guide' over Today, so the first build after set-up plays on the native Today with ld2's `coveredBy` unchanged.
- **One sheet host lives in core.** It closes BACKLOG L2 (`LifeMaxSheetHost`, never built). Phase 2's `DialConfirmSheet` moves onto it first, with its references unchanged.

**One push or a split: the honest answer**
- One *track*: yes. Each merge only widens `covers` behind the same off-by-default switch.
- One *merge*: no. The risk is very uneven.
  - Phase 3 is read-only (3A) plus set-like writes and two confirmations (3B).
  - 4A carries every XP-paying write that is not safe to repeat as it stands: about 15 commands, with the timer.
  - 4B carries about 45 form controls, pickers, a second draggable dial, and the keyboard.
- A single merge would put about 70 actions, about 45 fields, about 30 write commands and about 60 new screenshot references into one review. Any finding would hold back all of it, and the owner could only switch everything off.
- **Why 3A and 3B merge together.** Goals sits between Week and Quests (logic.js:2961). Merged apart, 3A would leave a page island in the middle of the main flick path (Today, Week, Goals, Quests), and every flick through it would hand over to the classic page and back. Both halves are low risk, so they merge once, with a review at the half-way step.
- **Why 4A and 4B stay apart.** 4A is where XP can be paid twice. 4B is where the keyboard, pickers and forms arrive. The owner should live with each on the phone before the next writes arrive.
- Until 4B, My time remains a page island between Quests and Settings (§2).
- A developer-mode control, "Life Director native parts" (sections / sheets / set-up / guides, all on by default), narrows `covers` without a new APK. A regression in one part can then be isolated on the phone.
- Order: phase 3 → 4A → NAV-5 → 4B. The order is fixed: 4A needs the frame from 3A and `SheetHost` from 3B.

**Rejected alternatives**
- **Shared `Model.*` functions with the renderers rewritten as templates**, the earlier draft of this plan. It rewrites the classic page the owner falls back to (A33), and parity does not need it: phase 1 proved parity with projection-against-DOM tests. It would also collide with ld2's line positions, and it was most of the estimated growth.
- **The guide as a last slice (4C).** It would leave page guides inside native sections for three merges. Week's first visit would hand over to the page 500 ms after the native slide (logic.js:3040, 4192-4198). Set-up would be a patchwork, and the first build after set-up would stay on the page until the end.
- **Project only the section on screen** and fetch the next one on switch. Every switch would cost a bridge round trip plus a page render. With all covered sections projected (§3.4), a switch is instant and the page catches up underneath.
- **Drive the page's own DOM controls by command**, the native Settings technique (.wt/settings 50-native-settings.js:1-10). For sheet forms, the page's sheet would have to be open under the screen, which makes `showing()` return 'sheet' and closes the screen. It also ties every command to selectors.
- **A HorizontalPager for the sections.** It would compose Today's dial offscreen at drag start. It would hand the day strip's overscroll to the pager, where the web never swipes from the strip (logic.js:4801). It would also add a third swipe feel next to the tab swipe's catch. The page's flick with its 18 px slide (logic.js:4798-4810, style.css:398) is matched instead.
- **Keep page sheets and let the screen step aside** (phase 1's pattern) for writes. That pattern causes the flicker 4A removes, and it shows the classic page under a native app.
- **Split logic.js into several files now.** forge joins a feature's scripts into one `<script>` (forge_features.py:30-34), and the controller is one closure the native code needs. The split gets its own ADR right after this track (the A26 exception, §3.6).

**What I checked in the code**

| Claim | Status | Where |
|---|---|---|
| Choosing a day in the page's Week brings the native screen back on day 0 | ✔ re-run: page `ui.dayIdx` 2, projection `dayIndex` 0, `ST.ui.tab` still 'week' | /tmp/ld34-scripts/probe-day.js; 50-native-director.js:7, 50-52; logic.js:4488 |
| Resetting the day in `beforeOpen(entered)` brings the bug back whenever the screen was blocked on entry | ✔ re-run (case D): page-drawn Week, tap Thursday → pageDay 0, nativeDay 0, one reset | 50-native-tabkit.js:179-181, 235; /tmp/ld34-review case D |
| The handshake is asked only after `blocked()` passes, so capabilities are unknown on an entry to a non-Today section | ✔ re-run (case B): entering on Week gives no NativeLifeDirector call at all | 50-native-tabkit.js:151 against 176 |
| The swipe reads the same `blocked()` | ✔ | .wt/swipe 50-native-tabkit.js:73-77, 234; 50-native-swipe.js:49-52 |
| A dismissed level-up never brings Life Director back | ✔ re-run (case C): close, then nothing for 20 s. Only `watch` tabs revisit on body changes | 50-native-tabkit.js:220-223; NATIVE_TAB_GUIDE line 37 |
| A command that leaves the page on something the screen does not draw keeps the screen up until the 15 s refresh | ✔ re-run (case A): `update` at once, `close` after 16 s | 50-native-tabkit.js:129-133 |
| Back during the guide leaves Life Director with `ui.tour` set; on return the screen stays away | ✔ re-run (a1–a3) | 34-navigation.js:55-70; logic.js:5038-5047 |
| A page sheet left open when leaving Life Director blocks native Character and steals Back | ✔ re-run | 34-navigation.js:64; probe-leak.js and its control |
| **New:** the same left-open sheet blocks the native Overview too, and Food checks the same unscoped selector | ✔ Overview: no `open` with the sheet; `open` in the control run. Food by reading only | /tmp/ld34-final/probe-ov.js; 50-native-overview.js:80; 50-native-nutrition.js:346 |
| A glance tap opens the native screen and closes it at once | ✔ re-run: calls `open`, `close`; the page sheet shows | logic.js:4763-4772 |
| `openFromGlance` returns early when there is no replay target | ✔ | logic.js:4767-4770 |
| Week's guide starts by itself the first time | ✔ re-run (g: 'guide', key 'week') | logic.js:4115-4199 |
| A build stopped by `cover()` starts no page guide (`stop()`, not `finish()`) | ✔ | ld2 logic.js:3497-3514, 5260 |
| Write handlers read the DOM; `prefRange` (4596) and `goalPrio` (4597) are handled in `onChange` | ✔ | logic.js:4428-4599 |
| Settings has three ranges plus the grace slider; the grace label never follows the finger (the `onInput` lookup finds no `#v-missGraceMinutes`) | ✔ | logic.js:3928-3935, 4592 |
| `goalDel` and `reset` use `window.confirm` | ✔ | logic.js:4570, 4577 |
| Ad-hoc event ids come from `clientEventId`; the ledger refuses duplicates; `awardActivity` refuses a second activity for one event | ✔ | logic.js:1916-1939; 2505-2508 |
| `logToday` mints `'ld-today:'+uuid()` every time, and a duplicate says "Could not log it (DUPLICATE_EVENT_ID)" | ✔ | logic.js:2894-2910 |
| Disruption ids are uuids, and the engine appends commitments without checking ids | ✔ | logic.js:4502-4512; 1655-1658 |
| Every store save copies the previous value to `.bak`, so a restore keeps one step back only until the next save | ✔ | logic.js:2309-2320, 4576 |
| The kit does not tell a tab its capabilities, and returns `follow` (main) but not `active` (ld2 adds it) | ✔ | 50-native-tabkit.js:263; ld2 :247 |
| ld2's plugin advertises `director.today.v1` while its kit asks `director.today.v2` (review item 37 still open) | ✔ | ld2 NativeLifeDirectorPlugin.kt; ld2 50-native-director.js |
| ld2 does not touch `SectionTabs` and `DayStrip`, so it keeps main's selectable rewrite after the merge | ✔ | `git diff 958eeee 8ad7560` and `958eeee c49e94f` on DirectorHeader.kt and DayStrip.kt |
| In flight: ld2 (7 commits), settings (9), swipe (12). integ is already in main | ✔ | `git rev-list --count`; fe40e15 is an ancestor of 8ad7560 |
| `maxDailyGoalMinutes` is capped at 300 unless All in, while its slider runs to 480 (it snaps back after a re-plan) | ✔ | logic.js:262-265, 3925 |
| Life Director's buzzes ignore Settings → Haptics | ✔ | logic.js:3299, 3632, 3975 against 07-runtime.js:45 |
| The timer is wall clock, from `Date.now`, `start` and `pausedMs` | ✔ | logic.js:4087-4110, 4467-4487 |
| Engine time on the 10 scenarios: 10–218 ms at 7 days; 965 ms for 'abundant' at 14 days | ✔ on this 4-core machine, not the phone | /tmp/ld34-scripts/engine-time.js |
| Week projection about 5.2 KB at 7 days, 9.8 KB at 14; Quests about 2.1 KB; main's snapshot 11.6 KB, ld2's 22.1 KB | ✔ estimated with a stand-in for the fields | /tmp/ld34-scripts/probe-size.js |
| Workout plan projects `celebrationTarget()` from `#brandLogo`; Life Director's frame has none | ✔ | 50-native-plan.js:171-175; logic.js:4891-4901 |
| MainActivity pads the content view by max(bars, IME) and consumes the insets, so Compose cannot see the IME | ✔ the code; the phone not seen | MainActivity.java:120-125; BACKLOG NAV-5 |
| Compose BOM 2024.12.01 with material3 (`TimePicker`, `DatePicker` exist) | ✔ the BOM; the picker API not tried | android/app/build.gradle:49-51 |
| A Compose `BackHandler` beats MainActivity's `lmaBack` callback | Relies on what ld2's `DialConfirmSheet` relies on. Not verified on a phone by me | ld2 LifeDirectorScreen.kt:104 |

**Review points settled.** Every point of the review is accepted. The checks behind each are in the table above.

| # | Point | Settled by |
|---|---|---|
| 1 | Capabilities unknown on a cold entry to Week | The kit asks the handshake as soon as the tab is enabled and revisits when it answers; `has()` is false until then (§3.3). Web test and sim hop: a cold start with `ST.ui.tab='week'` opens the native Week. |
| 2 | Day reset at first open, not at arrival | A kit `arrive()` hook resets `ui.dayIdx` at arrival, before the page's mount renders; `beforeOpen` no longer touches the day (§3.2). The day test runs with Week native and with `director.week.v1` missing. |
| 3 | No return after a level-up | `watch()` in Life Director's config; test: Done → level-up → dismissed → the screen reopens (§3.3, §5). |
| 4 | First build promised natively but played on the page | Guides ship per section; ld2's `coveredBy(() => tab.active())` stays. From 4A the first build is native; in phase 3 it is the page's, as today (§2). |
| 5 | Guide last undermines the other merges | The coach-mark layer comes in 3A with Week's guide, Today's in 4A, set-up's in 4B. 4C is gone. |
| 6 | Screen stays up after a command until the 15 s refresh | The kit steps aside right after a command's reply whenever the page, still on the view, is blocked (§3.3). A section outside `covers` is handed over with `Tap('tab')`. New sim fault. |
| 7 | New op per sheet opening breaks A14 | Ops are kept per (command, target), and per draft for forms. They survive hide and clear only on a terminal reply (§3.5, §3.7). Sim hop: `logToday` answered after hide → reopen → tap → one event. |
| 8 | Model refactor not needed | Dropped; `native*` projections sit next to the renderers, with tiny shared helpers (§3.6). |
| 9 | A26 | An explicit exception with a retirement condition and a growth cap, recorded in step 0 (§3.6, §4). |
| 10 | "Never flashes" is not true until 4B | Said in §2 and in the phone checks. 3A and 3B merge together, so only My time (and Lab) stay page islands. |
| 11 | No command opens a Week day | `gotoDay {index}`, also used by the up-next "See tomorrow" (logic.js:3707). |
| 12 | A retry after success reads as a failure | Repeats answer `ok`, `changed:false`, "Already recorded." / "Already added" / "Already skipped." (§3.5). |
| 13 | Absolute routine steps drop quick taps | The screen keeps the pending value and commits once after 400 ms of quiet (§3.5). |
| 14 | `SheetHost` blur and animation change `DialConfirmSheet`; ld2 review still open | No blur; entries render settled under inspection mode. §3.3 and §3.7 are rebased on whatever ld2 merges (items 37, 38). |
| 15 | Slider and mini-dial labels formatted natively | The page projects one label per stop: about 85 for the sliders, and 96 clock and 96 length labels for the mini dial. Form time fields are the one native formatter, tested against the page (§3.4). |
| 16 | NAV-5 changes the shell for every tab | Its own small shell change before 4B, with the sim and every tab's references. The IME is detected in MainActivity's insets listener (§4). |
| 17 | No target for the orbs | `celebrationTarget()` moves into the kit in 4A; the frame carries it (§3.4). |
| 18 | Projection cost not measured | `__nativeTabPerf.projectionMs` and the `day`/`section` round trip are asserted on the 14-day fixture. A lazy page render is the fallback, with its catch named (§6 risk 4). |
| 19 | Inaccurate claims | Corrected above: `prefRange` is at 4596; three ranges plus grace; ld2's base; what is in flight. `openFromGlance` clears its replay flag on every exit (§3.6). |

**Owner decisions needed**
1. Three merges (recommended), four (3A and 3B apart), or one.
2. Back on a native section other than Today: leave Life Director, as the page does (recommended, for parity), or go to Today first.
3. Restore backup. Keep the page's behaviour (no confirmation, no copy, no re-plan), or add all three to the shared `Do.importBackup` for page and screen (recommended):
   - a confirmation;
   - the replaced state kept in one slot, `lifemax_director_state_v1.replaced`, until the next restore;
   - a re-plan.
4. The keyboard (NAV-5). Hide the shared bottom bar on native screens while the keyboard is up (recommended), or keep it.
5. Short choices (≤4 options, such as commitment type or how tiring) as segmented pills (recommended), or native drop-downs as the page's `<select>` are. Long lists (12 activity tags) stay drop-downs.
6. A new "Coming up: one-offs" list with Remove, covering voice, calendar and "Something came up" items. Nothing in Life Director lists them today (logic.js:4336-4344, 5083-5108). It would be a new feature on both sides, so it is not in this plan unless asked.
7. The `maxDailyGoalMinutes` snap-back: keep it (parity), or limit the slider's range to what the intensity allows (a page fix).

## 2. What the owner sees and feels

**Everywhere**
- Life Director stays the dark instrument panel in both themes, using `DirectorPalette` (style.css:1-24).
- Moving between native sections never flashes the classic page. Two places are still the page:
  - **My time until 4B.** A tap or flick to it hands over to the classic My time. The next tap or flick back to a native section brings the screen back.
  - **The Lab, always.**
- A section change slides the body 18 dp and fades it in over 320 ms on the page's ease, cubic-bezier(.22,1,.36,1). The direction follows tab order. Reduced motion makes it instant.
- The header, clock and section tabs stay still while the body moves.
- A firm horizontal flick (≤700 ms, ≥70 dp, |dy| ≤0.6|dx|) moves to the next or previous section, only after set-up. It never starts on the day strip, the dial, the mini dial, a field, a slider, a stepper, day chips or the Move time grid.
- Messages are the page's own words, shown for 4 s.
- Coming back to Life Director always lands on today in the strip, on the native screen and on the page alike.
- **Guides.** A native guide is a dimmed screen with a spotlight on the part being explained (round for the dial), and a card:
  - "n of N" and dots;
  - Skip guide, Back and Next ("Got it" on the last step, "Let's go" for set-up);
  - "Tap anywhere to continue" on hint steps.
  - Back ends the guide, as Skip does. Each guide shows once, and ? replays it.

**Phase 3, first half (3A)**
- **Week.** One row per day, 7 or 14 of them: "Today" or the weekday and date, a 24-hour bar of coloured segments, then the count with "✓N" and the minutes. The bars are drawn on a Canvas with the dial's colours.
- Around the rows: the stats row with "≈N XP on the table", the 00·06·12·18·24 ticks, the legend, "Where your XP goes" (solid is earned, outlined is still planned) and "How the week fits", with the fold of sessions that could not fit.
- Tapping a day opens Today on that day at once, with the strip scrolled to it.
- The first visit to Week plays its three-step guide natively.
- **Quests.** The connection card with "Open Quest Board", which goes straight to the 3D table. Then the quest rows with green, amber or red edges and the reason a quest could not fit, the all-day reminders, and the milestones.
- Fixed along the way:
  - Back during any page guide ends it.
  - A Life Director sheet left open no longer blocks Character, the Overview or Food.
  - A glance tap no longer flickers.
  - Life Director's buzzes follow Settings → Haptics.
  - A cold start on Week opens the native Week.

**Phase 3, second half (3B)**
- **Goals.** The explanation card, then one card per goal.
  - Priority is five choices: Top, High, Medium, Light, Low.
  - Each card shows the Target share and Scheduled bars and the sessions line.
  - A paused card is dimmed.
  - A trajectory warning, and the clarification choices when the engine needs them.
  - Pause/Resume and Remove. Remove asks first, in a native sheet with the page's words.
  - Each change shows a quiet "Re-planning…" state on that control. The new plan's build plays on the next Today, through phase 2's build player.
- **Settings.**
  - How much to schedule.
  - Three sliders and the grace slider. The label follows the finger in the page's own words for each stop; the value commits once, on release.
  - Rest-day chips, the planning window, and "When I miss something" with the rest tokens.
  - "Show the guide again".
  - Copy backup goes to the Android clipboard. Restore backup opens a native paste sheet. Reset asks first.
  - A Diagnostics fold, and the Lab toggle.
- Phase 2's dial confirmation sheet can now be dragged down to dismiss.
- After set-up, the first build and Today's guide still play on the page, as in phase 1, until 4A.

**4A**
- Every tap on Today stays on the native screen.
- **Session sheet.** It slides up from above the bottom bar (320 ms in, 240 ms out). The scrim is dimmed. The grabber drags, and a drag past min(140 dp, 30% of the panel) or faster than 0.55 dp/ms dismisses. Back closes it.
- Its contents: the coloured rail, the title, the "Wed, Oct 28 · 7:15 PM–8:15 PM · 1h" line, chips (quests, goal, "+N XP · Attribute"), and one of the page's three bodies.
  - Start timer or Open timer, a minutes stepper with Mark done, and Open in Plan / Move / Pin here or Unpin / Skip.
  - Or: "Did it today anyway?" with Log it for today, and the small print.
  - Or: the past state.
  - Then the "Why this time?" fold with the factor bars.
- **Done.** Orbs fly natively from the tapped button to the bar's logo, and the page plays the sound and buzz. A level-up still shows on the page. The screen steps aside for it and comes back as soon as it is tapped away.
- **Timer sheet.** The 220-unit ring ticks every second, amber when paused, with "m:ss" or "h:mm:ss", "of 1h planned", and the XP estimate refreshed every 15 s. Then Finish and log, Pause or Resume, and Stop without logging.
- **Move.** A grid of up to 48 open times.
- **Something came up.** Five accordion sections. "Called into work" has a date picker, two time pickers that follow the page's 12- or 24-hour style, and a travel stepper.
- The banner's buttons, Re-plan from now and the energy check-in all answer on the screen.
- A glance tap on the Overview opens the native sheet directly. No flash.
- **Today's guide is native.** On the dial steps the other layers dim, and name tags such as Now, Sleep, Work and Session pin onto the arcs. On the handles step, the handle rings pulse.
- After set-up (still the page's My time until 4B), the first build plays on the native Today, then the native guide. The classic Today may show the first frames of that build for the moment the screen takes to open. That is ld2's `cover()` hand-over, reasoned from code and not run.

**NAV-5 (shell)**
- While the keyboard is up over a native screen (Log, Recipes, Library, Cash, and later Life Director's forms), the shared bottom bar hides. It comes back when the keyboard goes.

**4B**
- **My time.** The numbered set-up steps (Sleep, Work & school, Goals, Build) until set-up is done.
- **The mini sleep dial.** Two handles snap to quarter hours with a light tick each. The arc, the centre ("Sleep · 8h · 11:00 PM – 7:00 AM") and the two time fields follow the finger, in the page's words for each quarter hour. "Different on Friday and Saturday nights" reveals two more fields. Nothing is saved until Save sleep.
- The commitments list (tap to edit), the Getting ready, Wind-down and Meals steppers, the goal chips, and Build my week.
- **Commitment sheet.** Name, type, rotating pattern (days on and off, and the first day on, from a date picker) or day chips, start and end pickers, travel and unwind steppers, and how tiring it is.
- **Goal sheets.** "Add a goal" is a grid of the eight goal kinds plus "Something else". Picking one morphs the same sheet into the form over 300 ms: name, language, custom activity fields, priority, experience, and the optional measurable target.
- **First run.** The demo dial with "Tell it when you're busy. It plans the rest." and Set up my time, all native.
- **Set-up's guide is native**, with the Bedtime and Wake up tags on the mini dial.
- After "Build my week", the first build plays on the native Today without the page showing at all, then Today's guide.

## 3. Architecture

### 3.1 Who owns what
- **The page** (logic.js) owns the store, the engine, every rule and message, the section and day state, every write, and the guides' "seen" flags. It projects data and runs commands. It never learns that a sheet is open on the screen.
- **50-native-director.js** (NativeTabKit) owns the lifecycle and the covering rule. It holds `sheet` (what the screen asked to see) and `intent` (a one-shot request from an entry such as the glance), builds the projection, and runs the commands.
- **The native screen** owns presentation-only state:
  - the open sheet stack;
  - form drafts and pending operation ids;
  - the pending section or day while the page answers;
  - "Earlier today" open or closed;
  - the guide step;
  - builds already played.

When hidden, the screen closes every sheet and guide (A24), as ld2's `hidden()` does for the dial confirmation. Drafts and pending operation ids survive the hide, as `LogViewModel` keeps its draft and id (LogViewModel.kt:16-48). Reopening a form restores its draft, and a retry reuses its id.

### 3.2 One day, one section
- `day {index}` (the strip) sets `ui.dayIdx` and calls `render()`, so the page underneath shows the same day. It keeps phase 1's check that the day is on the plan.
- `gotoDay {index}` mirrors the page's handler (logic.js:4488): `ui.dayIdx`, `ui.tab = 'today'`, `render()`, no `persist()`. Week rows and the up-next "See tomorrow" use it. One command means one render and one reply.
- `section {id}` sets `ui.tab` and `ST.ui.tab` and calls `render()`, with no `persist()`. The tab rides on the next save. The page's own `goTab` persists every switch (logic.js:2964), a full write and read-back of about 120 KB.
- **Arrival.** The kit's navigate wrapper already marks an arrival (`entered = true`, 50-native-tabkit.js:235). A new optional `cfg.arrive()` runs at that same moment.
  - Life Director's hook calls `LifeDirector.native.arrive()`, which sets `ui.dayIdx = 0` without rendering. The feature's mount renders right after, on every entry path that opens the feature: bar button, `lmaNavTo`, widget, voice, the Journal coach, and the glance.
  - It runs only while the switch is on, so the classic page keeps its current behaviour when the switch is off.
  - `beforeOpen` no longer touches the day, and the kit's own `day` variable disappears.
  - A day chosen on a page-drawn Week therefore survives to the first native open, and every arrival lands on today.
- `showing()` changes in 3A:
  - Week without a plan reports 'first-run', since it draws `needSetup` (logic.js:3740).
  - A due guide reports 'guide' only when the native screen does not draw that guide (§3.3).
  - 'replay' is reported while a glance replay is pending (§3.6).

### 3.3 Covering rules
In 50-native-director.js:

```js
const PARTS = { week: "director.week.v1", quests: "director.week.v1", goals: "director.goals.v1", settings: "director.goals.v1",
                setup: "director.setup.v1", "first-run": "director.setup.v1" };
const GUIDES = { week: "director.week.v1", today: "director.sheets.v1", setup: "director.setup.v1" };
const covers = () => ["today", ...Object.keys(PARTS).filter((k) => tab.has(PARTS[k]) && part(k))]; // part(): developer narrowing
const guides = (key) => tab.enabled() && !!GUIDES[key] && tab.has(GUIDES[key]) && part("guides");

// NativeTabKit.create({...})
blocked: () => celebrating || !director() || !covers().includes(director().showing()) || !!document.querySelector(".luov"),
watch() {},                                                     // back by itself once the page is clear (a level-up dismissed)
arrive: () => { if (tab.enabled()) director()?.arrive(); },     // today on the strip whenever the owner comes to Life Director

// on DOMContentLoaded
ld.coveredBy(() => tab.active());                               // ld2's, unchanged
ld.guidedBy(guides);                                            // the page starts no guide the screen draws
```

- **Capabilities.** A capability means "this screen draws these sections, their guides and their sheets".
  - `director.week.v1`: Week, Quests and Week's guide.
  - `director.goals.v1`: Goals and Settings.
  - `director.sheets.v1` (4A): Today's sheets and commands, and Today's guide.
  - `director.setup.v1` (4B): My time, the first-run card and set-up's guide.
  - Without `director.sheets.v1`, Today's taps are handed over as in phase 1.
  - The base capability is whatever ld2 merges (`director.today.v2` once review item 37 lands).
- **Guides.**
  - `guidedBy(fn)` mirrors ld2's `coveredBy`. `tourAuto()` returns at once when `fn(ui.tab)` is true.
  - `showing()` reports 'guide' only for `ui.tour` (a page guide actually showing) or a due guide the screen does not draw.
  - The page's own ? still starts the page guide when the page is visible.
  - The projection carries the guide's model and `guide.help`, the key `tourHelp()` would pick (logic.js:4285-4289). The native ? plays that guide natively when `guides(key)` holds, switching section first if needed. Otherwise it hands over `Tap('tour')`.
- **The first build.** `coveredBy` stays ld2's `() => tab.active()`. From 4A, `showing()` is 'today' over a due Today guide, so the screen covers and plays the build:
  - after the page's set-up (4A), it covers through `cover()`;
  - after the native set-up (4B), the page skips its build because the screen is already active.
- **Kit changes** (50-native-tabkit.js). None changes what another tab does today; each tab's web tests and the sim prove it.
  1. `has(capability)` reads the cached handshake.
  2. The handshake is asked once, as soon as the tab is enabled (plugin available and switch on, at load and when the switch is turned on), not first inside `open()`. When it answers, the kit calls `revisit()`. `open()` awaits the same promise. `has()` is false until the answer, so the swipe's `kind()` sees Life Director as 'web' for that moment only.
  3. `cfg.arrive()` is called in the navigate wrapper where `entered = true` is set.
  4. After a command's reply, if the page is still on the tab's view and `blocked()` holds, the kit steps aside at once (`send()`, main's `follow()` branch). It used to cancel that refresh and wait for the 15 s timer (50-native-tabkit.js:129-133). The view check leaves a command that navigates (Recipes' `log`) to the navigate wrapper's hand-over.
  5. (4A) `NativeTabKit.celebrationTarget()` moves out of 50-native-plan.js:171-175. Workout plan's projection stays byte-identical, as its fixture test proves.
- The settings row stays `nativeDirector` / `#nativeDirectorToggle`. Its hint in `views/settings.html:12` is updated per merge, and native Settings reads it from the page (.wt/settings 50-native-settings.js:1-10, 30).

### 3.4 The projection

`Object.assign(ld.frame(), { covers, today, week, quests, goals, settings, time, firstRun, sheet, intent, guide })`.

A section field is present only while that section is covered. `null` means "nothing to draw" (for example, no plan).

Every string is formatted by the page: `fTime`, `fDay`, `mins`, `fHm`, plurals. Where a label must follow the finger, the page sends one label per stop. The one exception is a form's time field after a picker (`h12` and `locale` below). Rich text comes as spans, `[{"t":"Rest tokens: "},{"t":"1","b":true},…]`, for the few places the page uses `<b>` (guide text, rest tokens).

Time on the wire (A18):
- instants are epoch seconds;
- recurring times are minutes after local midnight, 0..1439, in the plan's zone `tz()`;
- dates are `"YYYY-MM-DD"` in that zone;
- durations are minutes.

**frame additions**
```json
"covers": ["today","week","quests","goals","settings"], "setup": true,
"haptics": "normal",                        // fxOpt().haptics ? hapLevelKey() : "off" (the page's own buzzes follow it too, 3A)
"clock": {"time":"3:10 PM","day":"Wed, Oct 28","h12":true,"locale":"en-US"},   // h12 = logic.js:3093 H12; locale from Intl (4A)
"celebrationTarget": {"x":38,"y":44}       // NativeTabKit.celebrationTarget() (4A)
```
A screen that finds no `covers` treats it as `["today"]`. When the projection names a section the screen cannot draw, the screen keeps its last body until the close that follows in the same tick.

**week** (3A; `nativeWeek()`, beside `renderWeek`, sharing `weekSpans(day)` and `unscheduledGroups(s)`)
```json
{"stats":[{"value":"12","label":"sessions, 3 done"},{"value":"9h 30m","label":"planned"},{"value":"≈640","label":"XP on the table"}],
 "ticks":["00","06","12","18","24"],
 "days":[{"index":0,"label":"Today","date":28,"today":true,"aria":"Today: 3 sessions, 2h 15m","count":"3","done":"✓1","minutes":"2h 15m",
          "segs":[{"kind":"sleep","from":0,"to":29.17},{"kind":"task","state":"done","from":31.25,"to":34.38}]}],
 "legend":[{"kind":"sleep","label":"Sleep"}],
 "yield":{"title":"Where your XP goes","note":"…","rows":[{"label":"Physique","color":4280391411,"done":0.4,"plan":0.6,"total":"210"}]},
 "fit":{"title":"How the week fits","text":"…","allFit":"Everything requested fits this week.",
        "unscheduled":{"summary":"3 sessions could not fit","groups":[{"title":"Gym ×2","tier":"tier 3","text":"…"}]}}}
```
`color` is ARGB from the host's `ATTR_COL`, converted with `NativeTabKit.color`, with the page's fallback #10B981 (logic.js:3762).

**quests** (3A)
```json
{"head":{"title":"Connected to your Quest Board","text":"…","open":"Open Quest Board"},
 "rows":[{"title":"…","when":"Due Fri, Oct 30","status":"2 sessions scheduled","tone":"ok|pas|no","why":""}],
 "passive":[{"title":"…","when":"Tracked on the Quest Board","status":"All-day"}], "empty":"No active quests.",
 "milestones":{"summary":"2 of 5 earned","note":"…","rows":[{"title":"…","desc":"…","earned":true,"status":"Earned"}]}}
```

**goals** (3B; `nativeGoals()`, sharing `goalShare(g, alloc)` with the page's fallback for a target share of 0, logic.js:3789)
```json
{"explain":{"title":"How your time is shared","text":"…"},"priorities":[{"value":5,"label":"Top"}],
 "cards":[{"id":"g-muscle","title":"Build muscle","sub":"Build Muscle · intermediate","paused":false,"priority":4,
   "share":{"target":0.44,"targetText":"44%","scheduled":0.38,"scheduledText":"38%","line":"4 sessions · 3h this week · Gym 3–4×"},
   "warning":null,"clarify":{"text":"…","options":[{"arch":"HABIT_READING","label":"Read More"}]},"pause":"Pause","remove":"Remove"}],
 "empty":null,"add":"Add a goal","removeConfirm":"Remove this goal? Its future sessions are removed; completed ones stay in your history."}
```

**settings** (3B)
- `intensity {value, options, note}`
- `ranges[{key, label, hint, min, max, step, value, stops[]}]`: the three ranges and grace, with values already normalised. `stops` holds one label per step, in the page's words: `mins()` for the ranges and 'Nm' for grace. That is 31 + 23 + 15 + 16 labels.
- `restDays[{day, label, on}]`
- `horizon {value, options}`
- `misses {value, options, text, tokens}`
- `guide {title, text, button}`
- `data {text, storageWarning, copy, restore, reset, resetConfirm, restoreTitle, restoreText}`
- `diagnostics {line}`
- `lab {label, on}`

**time** (4B, My time) and **firstRun** (4B)
- `first`, `steps[{label, state}]`, `lede`.
- `sleep`:
  - `bed`, `wake`, their texts and `length`;
  - `weekend {on, bed, wake}`;
  - labels;
  - `context[{from, to}]`, the mini dial's work arcs in minutes;
  - `step: 15`, the page's snap (logic.js:4078), sent rather than copied;
  - `clock[96]`, `fHm` for every quarter hour, and `lengths[96]`, `mins` for every quarter-hour length, so the centre and fields follow the finger in the page's words;
  - `save {label, primary}`.
- `commitments {rows[{id, title, dot, line}], empty, add}`.
- `routines {rows[{key, label, hint, value, text, stops[], min, max, step}], meals {on, value, text, stops[], min, max, step, toggle}, note}`.
- `goals {chips, empty, add {label, primary}, manage}`.
- `build {text, label, enabled}` before set-up, or `replan` after it.
- `firstRun` carries the demo arcs (logic.js:3683-3687) and its words.

**sheet** (4A/4B) is the model of what the screen asked for with the `sheet` command. It is cleared whenever the screen opens. `{"kind":…, "gone":true}` means the item left the plan; the screen then closes the sheet and shows the page's "That is no longer on the plan."
- `session` holds exactly instSheet's branches (logic.js:3944-3967):
  - `id`, `state`, `icon`, `title`, `when`, `chips`;
  - `mode` now|later|past;
  - `note`;
  - `minutes {label, value, min:1, max:480}`;
  - `primary`, `complete {label, command, smallPrint}`;
  - `row[{label, command|sheet, …}]`;
  - `past`;
  - `why {title, summary, factors[{name, points, frac}], feasible}`;
  - `orphan`.
- `timer`: `id`, `title`, `label`, `paused`, `startedAt` (ms), `pausedMs`, `pausedAt`, `plannedMs`, `planned`, `xp` (the estimate at projection time; the kit's 15 s refresh matches the page's, logic.js:4108), `finish`, `pause`, `stop`, `note`.
- `meal`: `start`, `title`, `when`, `chips`, `open`. From `LifeMaxMeals.resolveScheduled`.
- `move`: `id`, `title`, `note`, `times[{label, start}]` (≤48, from the shared `moveTimes(inst)`, logic.js:4324-4335), `none`.
- `disrupt`: `sections[{key, title, open, form|options|button, note}]`.
- `rule`, `goalType`, `goalForm` (4B) are a **form schema**: `fields[{key, kind, label, value, min, max, step, maxLength, placeholder, options, reveals, hides, group}]`.
  - `kind` is text | number | time | date | select | check | days.
  - `group` lays fields out as the page's `ld-grid2`/`ld-grid3` rows.
  - Then `fold` (the optional target), `save`, `cancel`, `remove`, `help`.
  - Labels, limits and defaults come from ruleSheet (logic.js:3882-3900) and goalForm (logic.js:3909-3918).
  - A time field shows the page's string until edited. After a picker, the screen formats the time with java.time, using `clock.h12` and `clock.locale`, and after the save it shows the page's string again. A fixture test compares 48 sample minutes with the page's `fHm` in en-US and en-GB.

**intent** (4A): `{"id":7,"sheet":"session","target":"i-123"}`. It is set by `openFromGlance`, and the screen takes it once by id.

**guide** (3A Week, 4A Today, 4B set-up): `{key, auto, help, steps[{anchor, mode, title, text(spans), hint, round, pad}], labels {skip, back, next, last, go, tap}}`.
- `steps` is TOURS filtered as `tourSteps` filters it (logic.js:4182).
- Anchors are named from the page's selectors:
  - Week: `week.rows`, `week.stats`, `week.yield`;
  - Today: `dial`, `dial.centre`, `upNext`, `footer`;
  - set-up: `setup.steps`, `setup.mini`, `setup.commit`, `setup.routines`, `setup.goals`, `setup.build`.

**Size and cost.**
- By estimate, the full snapshot on the lived-in 7-day fixture is about 45–60 KB once every section is covered, against phase 2's measured 22 KB. Week grows from about 5 KB at 7 days to about 10 KB at 14. The per-stop labels add about 5 KB.
- A web test asserts that the snapshot stays under 150 KB on a 14-day fixture. The cap is 2,000,000 characters (ComposeTabPlugin.kt:229, ld2).
- The same test records `__nativeTabPerf.projectionMs` and the page-side time of a `day` and a `section` command. Budgets on the CI machine, as trend evidence only: projection under 40 ms, each command under 80 ms. The phone figure comes from TabTiming and Diagnostics (A25).

### 3.5 Commands

All commands run one at a time (the kit). The reply is `{command, requestId, ok, changed?, message, celebrations?, open?, close?}`.
- `ok` means the change is saved: `persist()` returned true, as ld2's `applyDial` reports it.
- A refused Life Director save stays in memory with its "Not saved" banner. That is the store's rule (logic.js:2623-2634). The reply says `ok:false` with the banner's text.
- On success, the page's `say()` notice comes with the projection and is shown once. A refusal's `message` is shown by the screen. The `Do` functions return validation messages instead of calling `say()`, and `perform()` says them for the page path.
- **A repeat of a write that already landed** answers `{ok, changed:false, message}`, with "Already recorded.", "Already added" or "Already skipped.". Its `ok` re-runs `persist()`, so it tells whether the recorded state is saved now. It is never the page's error text, such as "Could not log it (DUPLICATE_EVENT_ID)" (logic.js:2896-2897).
- **Operation ids (A14).**
  - The screen mints `op`, a UUID, per (command, target): for example (`logToday`, instance id), (`disrupt`, kind) or (`regroup`, 'pause').
  - A form gets one per draft, as `LogViewModel` keeps one per draft.
  - The id is kept until a terminal reply: `ok`, including the `changed:false` a retry earns, or a definite refusal.
  - A reply lost because the screen hid leaves the id pending. Reopening and tapping again reuses it, and the page answers "Already recorded.".
  - After a terminal reply, the next tap is a new operation, so logging the same session twice on purpose still works.

| Command (half) | Payload | Page function (`Do.*`, from) | Why a repeat is safe |
|---|---|---|---|
| `section` (3A) | `{id}` | sets `ui.tab`, `ST.ui.tab`, `render()` | set. An id outside `covers` is refused; the screen hands those over with `Tap('tab')`. After the reply the kit steps aside if the page shows a guide or first-run card the screen does not draw (§3.3) |
| `gotoDay` (3A) | `{index}` | gotoDay (4488): `ui.dayIdx`, `ui.tab='today'`, `render()` | set |
| `day` (3A, changed) | `{index}` | `ui.dayIdx`, `render()` | set |
| `tourSeen` (3A) | `{key, finished}` | tourEnd's save and its skip message | set |
| `goalPriority` (3B) | `{id, priority}` | onChange goalPrio (4597) | set; a missing id is refused, "That goal was removed." |
| `goalPause` (3B) | `{id, paused}` | goalPause (4569) | set, not a toggle |
| `goalClarify` (3B) | `{id, arch}` | clarify (4568) | set |
| `goalRemove` (3B, after a native confirmation) | `{id}` | goalDel (4570) without `confirm()` | a missing id answers `changed:false` with no re-plan |
| `pref` (3B) | `{key, value}`; keys allow-listed: the three ranges, grace, intensity, window, misses | pref (4571), onChange prefRange (4596) | set; the page clamps and normalises |
| `restDay` (3B) | `{day, on}` | restDay (4572) | set, not a toggle |
| `tourReset`, `lab` (3B) | `{}`, `{on}` | 4583, labToggle | set |
| `exportBackup` (3B) | `{}` | `store.exportJson` | read-only; the reply carries the text and the screen copies it |
| `importBackup` (3B) | `{text, op}` | doImport (4576), plus decision 3 | a state already equal to the text answers `changed:false` |
| `reset` (3B, after a native confirmation) | `{}` | 4577 without `confirm()` | clearing twice leaves it clear |
| `sheet` (4A) | `{kind, id}` | sets the kit's `sheet` | read-only |
| `done` (4A) | `{id, minutes}` | done/doneQuick (4492-4493), including clearing the focus | the event id comes from instance and revision; a repeat answers "Already recorded." (2830-2834) |
| `logToday` (4A) | `{id, minutes, op}` | logToday (2894) with `clientEventId 'ld-today:'+op` | the ledger refuses the event id and `ldEventId` refuses a second LifeMax activity, also after a reload (2505-2508); the repeat answers "Already recorded." |
| `timer` (4A) | `{act: start|pause|resume|finish|cancel, id, start?}` | focus* (4467-4487) | start is set-like for the same id; pause and resume are guarded on state; finish and cancel are refused unless `start` equals `ST.ui.focus.start`; a finish repeated after it landed (no focus, the instance completed from that start) answers "Already recorded." |
| `skip` (4A) | `{id}` | skip (4495) | refused unless the instance is PLANNED, plus the page's penalty-id guard (2052-2057); a repeat after it landed answers "Already skipped." |
| `pin` (4A) | `{id, on}` | lock (4496) | set, not a toggle |
| `moveTo` (4A) | `{id, start}` | moveTo (4498) | absolute; already there and pinned is a no-op |
| `disrupt` (4A) | `{kind: work|block|day|sick, op, date?, from?, until?, commute?, hours?, days?}` | dWork…dSick (4502-4511) | the commitment or blackout id is `<prefix>-op`; an existing id answers "Already added" |
| `energy` (4A) | `{level}` | dEnergy (4512) | set (it re-plans) |
| `replan` (4A) | `{}` | replan (4513) | a re-plan twice is the same plan |
| `regroup` (4A) | `{v, op}` | regroup (4514-4518) | only while the regroup banner shows; the pause id comes from `op` |
| `dismissBanner` (4A) | `{text}` | dismissBanner | only if that banner is the one showing |
| `openPlan`, `openMeal` (4A) | `{key}`, `{start}` | 4499, 4441 | navigate while covering; the reply never arrives |
| `sleepSave` (4B) | `{bed, wake, weekend: {bed, wake} \| null}` | saveSleep (4519) | set |
| `ruleSave` (4B) | `{id \| null, op, kind, title, days[], rotation: {on, off, anchor} \| null, start, end, commute, unwind, load}` | saveRule (4534) | a new rule's id is `'r-'+op` (already there: "Already added"); an edit of a removed rule is refused instead of re-added |
| `ruleRemove` (4B) | `{id}` | delRule | a missing id is a no-op |
| `routine` (4B) | `{key, value}` | routine (4461), but **absolute** | set; the page clamps. The screen keeps the pending value (projected value plus local steps), sends it once after 400 ms without a tap or when the card loses focus, and adopts the page's value from the reply |
| `meals` (4B) | `{on}` | meals (4466) | set |
| `goalAdd` (4B) | `{op, arch, title, priority, level, subject?, custom?, target?}` | saveGoal (4556) | the id comes from `op`; already there: "Already added" |
| `finishSetup` (4B) | `{}` | finishSetup (4550) | a second run is a re-plan |

`done`, `logToday` and `timer finish` run inside `holdCelebrations`. Their reply carries `celebrations: [{xp, gains}]`, and the page plays `xpCue` 120 ms later (`CUE_AFTER_MS`, 50-native-plan.js:17). The quest bounty (`Host.completeQuest` → `toggleQuest`) is held the same way. A level-up is not held: it appends `.luov` 950 ms later (07-runtime.js), the kit steps aside, and `watch()` brings the screen back once it is removed. From 4A no handed-over tap pays XP, so phase 1's `celebrate()` and its `celebrating` flag are removed.

### 3.6 The page side
- **logic.js** (changed):
  - `nativeWeek`, `nativeQuests`, `nativeGoals`, `nativeSettings`, `nativeTime`, `nativeFirstRun`, `nativeSheet(kind, id)` and `nativeGuide(key)` sit beside `nativeToday`. The renderers are untouched apart from the extracted helpers: `weekSpans`, `unscheduledGroups`, `xpYield`, `goalShare`, `moveTimes`. Each extraction is guarded by an HTML golden recorded before it (§5).
  - The `Do` object replaces the bodies of `perform()`'s and `onChange()`'s write cases. Those still read the DOM, then call `Do`.
  - `LifeDirector.native` gains `week`, `quests`, `goals`, `settings`, `time`, `firstRun`, `sheet(kind, id)`, `guide()`, `section(id)`, `gotoDay(i)`, `day(i)`, `arrive()`, `intent()`, `guidedBy(fn)` and `run(name, payload)`. `run` is the frozen `Do` map, with payload type checks.
  - `showing()` gains 'first-run' for Week, 'replay', and the guide rule (§3.3). `tourAuto()` honours `guidedBy`.
  - `openFromGlance`, in 3A, sets `ui.replay` before it navigates. It clears the flag on every exit: no target, no twin (logic.js:4767-4770), and after the replayed click has run. Each clear calls `changed()`, so the screen opens only when the replay did not open a page sheet. From 4A it sets an `intent` instead of replaying when the screen will cover. Start runs `Do.timer` and the timer sheet opens.
  - The three buzzes respect `fxOpt().haptics` (3A).
  - The grace label gets its `#v-missGraceMinutes` id and the page's 'Nm' wording, so its label follows the finger on the page as the other three do (3B).
  - Estimated net growth is 450–700 lines (ld2's logic.js is 5,419 lines).
- **A26 exception**, recorded in MIGRATION.md and BACKLOG.md in step 0. CONTRACT.md A26 says a file over 600 lines is split before it grows further, and logic.js is already 5,245 lines on main.
  - The exception allows at most 700 more lines in this track.
  - Retirement condition: an ADR splits logic.js right after this track, before the switch may default to on.
    - First the engine, the store and the host adapter move into their own feature scripts. That is a pure move, proven by a byte-identical built page, since forge joins `feature.json` "scripts" in order (forge_features.py:30-34).
    - Then the controller is split by section behind one declared internal seam.
    - The web tests that read logic.js by path (director-finish, director-meals, director-sleep) are changed to read the joined scripts.
- **50-native-director.js** (changed): `covers`, `guides`, `sheet`/`intent`, the projection, `.luov` in `blocked`, `watch`, `arrive`, `guidedBy`, celebrations in replies. The command table moves to a new **50-native-director-commands.js**: thin wrappers that type-check and call `LifeDirector.native.run`. Both are listed in `index.template.html` after 50-native-tabkit.js.
- **50-native-tabkit.js** (changed): the five kit changes of §3.3.
- **LD overlays scoped to their view (3A).** An LD sheet or guide only matters on Life Director's own view. Three places query it globally today:
  - **34-navigation.js:64** (`lmaTopOverlay`):
    ```js
    const ld=q('#view-life-director.active .ld-sheet.open:not([hidden])'); …
    const lt=q('#view-life-director.active .ld-tour.on:not([hidden])');
    if(lt)return {close(){const b=lt.querySelector('[data-act=tourEnd]');b?b.click():0}};
    ```
  - **50-native-overview.js:80**: Overview's own `blocked()` selector.
  - **50-native-nutrition.js:346**: Food's `COVERED`.
  
  All three take the scoped form. Without the scope, a guide that survives leaving would block every native screen, exactly as a left-open sheet does today.
- **views/settings.html**: the hint per merge; "Life Director native parts" in `#devModeTools` (settings.html:124) as `lmaLoad().nativeDirectorParts`.
- **tools/gen_native_fixtures.js**, **tests/fixtures/director-day.js**: §5. Regenerate module-map.json and symbol-index.json.

### 3.7 The native side

New files are under `android-wrapper/native/src/main/java/com/lifemax/rpg/`, each under 600 lines (A26).

- **bridge/web/NativeLifeDirectorPlugin.kt** (changed): capabilities per merge; command plumbing moves to `feature/lifedirector/data/DirectorBridge.kt`, which implements the command interfaces; `swipeSource = false` comes from the swipe track.
- **app/LifeDirectorContainer.kt** (changed): wires the new controllers.
- **feature/lifedirector/domain**:
  - `Section.kt`: the section ids, `covers` (an absent field means Today only), and `SectionFlick.decide(dx, dy, dtMs)` (pure).
  - `WeekModel.kt`, `QuestsModel.kt`, `GoalsModel.kt`, `SettingsModel.kt`, `TimeModel.kt`, `SheetModels.kt`, `FormSchema.kt`, `GuideModel.kt`, `RichText.kt`.
  - `LifeDirectorCommands` splits into three:
    - `SectionCommands`: section, gotoDay, day, tourSeen and the dial;
    - `PlanCommands`: Today's writes;
    - `SetupCommands`: forms.
- **feature/lifedirector/data**: `SectionsJson.kt` (week, quests), `GoalsSettingsJson.kt`, `SheetsJson.kt`, `FormJson.kt`, `GuideJson.kt`; `directorReply` grows (`changed`, celebrations, open, close). Missing or mistyped objects use `objectOrNull`, moved into `core/data/PageJson.kt` as the backlog asks.
- **feature/lifedirector/presentation**:
  - `LifeDirectorScreen.kt` becomes the frame. It is still one LazyColumn of panel slices:
    - a "top" item (header, tabs, banner) whose key never changes;
    - then the body items of the current section.
    
    A section change runs one `Animatable` that every body item reads in `graphicsLayer` (18 dp, alpha), so the header never moves. If the list is past the tabs, it scrolls so they show, as the page scrolls the active tab into view.
  - On top of the list: the `SheetHost`, the coach-mark layer, and the toast. The toast sits above the sheet.
  - `sections/`: `TodaySection.kt` (moved), `WeekSection.kt` with `WeekBars.kt` (Canvas), `XpYieldCard.kt`, `WeekFitCard.kt`; `QuestsSection.kt`; `GoalsSection.kt`, `GoalCard.kt`; `SettingsSection.kt`; `TimeSection.kt`, `SleepCard.kt`, `CommitmentsCard.kt`, `RoutinesCard.kt`, `FirstRunCard.kt`.
  - `sheets/`: `SessionSheet.kt`, `TimerSheet.kt`, `MealSheet.kt`, `MoveSheet.kt`, `DisruptSheet.kt`, `RuleSheet.kt`, `GoalSheets.kt`, `ConfirmSheet.kt`, `RestoreSheet.kt`. `DialConfirmSheet.kt` moves onto the host.
  - `form/`:
    - `DirectorForm.kt`, the schema renderer;
    - `Fields.kt`: `DirectorTextField` on BasicTextField, `DirectorStepper` with long-press repeat and a pending value, `DirectorChoice` (segmented pills for ≤4 options, else a menu), `DirectorCheck`, `DayChips`, `TimeField`, `DateField`;
    - `Pickers.kt`: material3 `TimePicker` with `is24Hour = !h12`, and `DatePicker`. Each is pushed as a child sheet on the same host, so it is styled, can be screenshotted, and Back pops it.
  - `dial/MiniSleepDial.kt` reuses `DialGeometry` and phase 2's `DragGeometry`, and labels from the projected `clock` and `lengths`.
  - `guide/CoachMarks.kt` (3A); `dial/TourMode.kt` (4A: layer dimming, name-tag pills and pulsing handles in `DialArt`; the pulse is static under reduced motion); mini-dial tags (4B).
  - View models:
    - `LifeDirectorViewModel.kt` keeps the frame and Today and gains `pendingSection` and a pending day.
    - `SheetController.kt` (new) holds the stack, pending and terminal replies, and one hand-over per opening. Its operation ids are keyed by (command, target) and survive hide.
    - `FormDrafts.kt` (new) keeps drafts and their ids across hide.
    - `GuideController.kt` (new) holds the step, auto-start, and Back as Skip.
- **core/designsystem/sheet/SheetHost.kt** (new, 3B), closing BACKLOG L2:
  - `SheetStack` (push, replace, pop) and `BoxScope.SheetHost(stack, visible, bottomInset, look)`.
  - One `BackHandler(enabled = visible && stack.isNotEmpty())`.
  - A scrim (tap dismisses unless the entry is modal), and a panel up to 560 dp wide with radius 24. It sits above the shared bottom bar at the position ld2 finally merges for `DialConfirmSheet` (review item 38, "sheet over bar").
  - The grabber drag with the page's thresholds, as a pure function `SheetDrag.dismisses(dy, height, v)`, and upward resistance 0.18.
  - The enter and exit curves; a height morph on `replace` (300 ms); children rising 8 dp (320 ms). All are instant with reduced motion. Under `LocalInspectionMode` an entry is drawn settled on its first frame, so Paparazzi references, `DialConfirmSheet`'s included, stay unchanged.
  - No backdrop blur. The page's 2 px blur is invisible under the .72 scrim, and dropping it keeps `DialConfirmSheet`'s pixels.
  - `paneTitle`, cleared semantics behind it, and focus on the first control.
  - Colours come through `SheetLook`, so LifeMax-themed tabs can use it later.
- **core/designsystem/feedback/XpCelebration.kt** (4A): moved out of Workout plan's `PlanFeedbackLayer.kt`, with Workout plan's references unchanged as the proof.
- **The shell (NAV-5, before 4B):**
  - MainActivity's existing insets listener already reads the IME (MainActivity.java:120-125). It publishes "keyboard up" to a small `app/shell/ImeState`.
  - `ChromeHost` hides the shared bottom bar while it holds, and reports the bottom inset without the bar.
  - The content padding by max(bars, IME) stays, so the WebView behaves as today.
- **android-wrapper/native/NativeApi.java**: main's value + 1 at each merge.

### 3.8 Hand-over, Back, swipe and entries

| Where | Back | How |
|---|---|---|
| A native section | the page's Back: leave Life Director (decision 2) | no BackHandler; `lmaBack` runs |
| A native sheet or picker | closes the top one | SheetHost's single BackHandler |
| A native guide | ends it like Skip guide | BackHandler in `CoachMarks`; `tourSeen {finished:false}` |
| A page sheet or guide still handed over | closes or ends it | `lmaTopOverlay`, scoped (3A) |

- A section tap or flick to a section outside `covers` is `Tap('tab', id)`. The kit closes the screen and the page shows that section, as phase 1 does. From a page section to a covered one, the page renders, then `onChange` → `revisit()` → the screen covers.
- A command that leaves the page on something the screen does not draw (a due page guide, a first-run card, a page sheet) is followed at once by the kit's step-aside (§3.3).
- Life Director stays a swipe destination and never a swipe source (`swipeSource=false`; `OWN_GESTURES`, .wt/swipe 50-native-swipe.js:35). The section flick lives inside the screen, so there is no conflict. Arriving by swipe opens the section in `ui.tab`, on today. The swipe reads Life Director as native once the warm handshake has answered.
- Widget, voice "open", the Journal coach, scrub-nav and deep links: unchanged. They navigate, `arrive()` resets the day, and the kit opens on `ui.tab`.

## 4. Phases

**Merge base.**
- Start after ld2, settings and swipe have merged.
  - ld2 is 7 commits on 958eeee, with its review items 37–40 still open (capability v2, the sheet over the bar, sim determinism, re-recorded references).
  - settings is 9 commits and swipe is 12.
  - integ is already in main (8ad7560).
- NativeApi is 29 on main, and 30–32 once those three merge.
- Rebase each merge onto main. Line numbers in the controller move by about +116 after ld2 (in `perform()` and the sheets). ld2 leaves the engine, store and host adapter (lines 1-2557) untouched.

**Step 0 (in 3A).**
- `docs/architecture/LIFE_DIRECTOR_INVENTORY.md`: every fact the page shows and every action, as RECIPES_INVENTORY does, with the merge that makes each one native. The map's facts are its draft.
- The A26 exception and its retirement condition in MIGRATION.md and BACKLOG.md.

### Phase 3, first half (3A): Week, Quests, section switching, the coach-mark layer (read-only)
- **Kit:** `has()`, the warm handshake with `revisit()`, `arrive()`, the step-aside after a command.
- **Page:**
  - `nativeWeek` and `nativeQuests`, with `weekSpans` and `unscheduledGroups` extracted under an HTML golden;
  - `section`, `gotoDay`, the changed `day` and `arrive`;
  - `showing()` with 'first-run' for Week, 'replay' and the guide rule; `guidedBy`; `tourSeen`;
  - `covers`;
  - the three scoped overlay checks and Back in the page guide;
  - the glance `replay` flag, cleared on every exit;
  - haptics in the three buzzes;
  - the developer parts control.
- **Native:**
  - the frame refactor (top item and body transition), `SectionFlick`, `pendingSection`, selectable section tabs for every native section;
  - Week and Quests;
  - `CoachMarks` with Week's guide.
- **Still handed over:** the Goals, My time, Settings and Lab tabs; every Today sheet and action; Today's and set-up's guides.

### Phase 3, second half (3B): Goals, Settings, SheetHost — merged with 3A
- `SheetHost` first, with `DialConfirmSheet` on it and its references unchanged.
- `nativeGoals` (with `goalShare` extracted under the golden) and `nativeSettings` with per-stop labels.
- `Do` for priority, pause, clarify, remove, pref, rest day, guide reset, lab, export, import and reset. Each gets a repeat test and a refused-save test.
- Native sections; `ConfirmSheet` (the page's words); `RestoreSheet` (a multi-line field, then a confirmation if decision 3 asks for one).
- The clipboard: `ClipboardManager`; the fallback is a sheet with selectable text, as the page falls back to a textarea.
- Sliders commit on release. The grace label fix on the page.
- **Still handed over:** "Add a goal" (the page's goal sheet over the page's Goals), My time, the Lab tab, Today's sheets and actions, Today's and set-up's guides.
- **The owner gets, at this merge:**
  - Today, Week, Goals, Quests and Settings on the native screen, with honest pending and failure states;
  - a correct day from Week;
  - guides that end with Back;
  - native tabs never blocked by a forgotten Life Director sheet;
  - no glance flicker;
  - a native Week on a cold start.

### 4A: Today's sheets and actions, Today's guide
- Models for the session, timer, meal, Move and disrupt sheets (`moveTimes` extracted under the golden).
- Keyed `Do` functions with "Already recorded." answers.
- `sheet` and `intent`; celebrations in replies; `NativeTabKit.celebrationTarget()`; `.luov` and `watch()`; the glance opens the native sheet.
- `XpCelebration` moved to core.
- The sheets; time, date and stepper fields with pickers (needed by "Called into work"). The up-next Done, Finish and log, the banner buttons and Re-plan become commands.
- Today's guide: `TourMode` in the dial (dimming, name tags, pulsing handles) and `director.sheets.v1` in `GUIDES`. The first build after set-up becomes native.
- **Still handed over:** My time and its forms, set-up's guide, the Lab.
- **The owner gets:** the whole daily loop (open, start, finish, done, log, move, skip, pin, something came up) without leaving the native screen, plus Today's guide.

### NAV-5: the keyboard (shell, its own small merge)
- `ImeState` from MainActivity's insets listener; `ChromeHost` hides the bottom bar while the keyboard is up and reports insets without it.
- Every native tab's references stay unchanged (the bar is only hidden with the keyboard up), plus one new reference per tab with a text field, keyboard up.
- A sim case: a tab switch while the keyboard is up leaves the bar correct on the next screen.

### 4B: My time, first run, set-up, forms, set-up's guide
- `nativeTime`, `nativeFirstRun`, the rule and goal form schemas.
- `Do` for sleep, rule save and remove, routine (absolute), meals, goal add, and finish set-up. A parity golden test drives the DOM path and the payload path to the same state.
- The form kit (text, choice, check, day chips, pickers); the mini sleep dial; the rule and goal sheets with the morph; the first-run card; set-up end to end; the first build on the native Today with the page never playing it.
- Set-up's guide with the mini-dial tags.
- **Still handed over:** the Lab only.
- **The owner gets:**
  - set-up and every change to "my time" natively, with pickers in the page's clock style;
  - Life Director meeting A33.1, every action native or listed (the Lab and the Quest Board).
  
  The switch could then default to on, once the owner says so and once the A26 retirement split has landed.

## 5. Tests

**Web (Playwright, fake plugins, Playwright's clock; `tools/run_tests.sh` runs them in CI)**
- **`director-render-golden.test.js`** guards only the renderers a helper extraction touches: Week, Goals and Move. It is recorded on the commit before each extraction and stores their HTML on the fixture states. Afterwards the HTML must be byte-identical.
- **`director-do.test.js`.**
  - A golden recorded before the `Do` split stores, for every write case driven through the page's own form, the resulting `ST` (ids masked) and message. After the split it must be identical.
  - For every `Do` function, the page path (fill the page's form, click its button) and the payload path (`LifeDirector.native.run`) give equal `ST`, save and message.
  - Each write also has:
    - a repeat test: the same request twice changes the save once, and the second reply is `ok`, `changed:false` with the "Already…" message;
    - a refused-save test: a quota fault injected as the fixtures already do (gen_native_fixtures.js `setItem` throwing). The reply says `ok:false` with the banner's words, and a repeat after the fault clears answers `ok:true`, `changed:false`;
    - `logToday` and `done` twice: one ledger event and one LifeMax activity;
    - `disrupt` twice with one `op`: one commitment;
    - `timer finish` with a stale `start`: refused; repeated after it landed: "Already recorded.";
    - `skip` repeated after it landed: "Already skipped.";
    - `ruleSave` editing a removed rule: refused, not re-added.
- **`native-director-sections.test.js`** (phase 3):
  - **The projection against the page's DOM:** Week rows, labels and aria equal `.ld-wrow`; stats and yield texts; quest rows and tones; goal cards; settings values and each range's `stops` against `mins()`.
  - **Covering:**
    - the covering rules for each `covers` value, and each capability missing;
    - a cold start with `ST.ui.tab='week'`: the handshake is asked at load and the native Week opens on entry;
    - the swipe's `kind('life-director')` is 'native' on Week once the handshake has answered.
  - **The day:**
    - the day bug, run twice, once with Week native and once with `director.week.v1` missing (Week page-drawn). A Week row gives `dayIndex` equal to `ui.dayIdx`, and no reset follows;
    - leave on Thursday and come back: day 0 on page and screen;
    - `section` and `gotoDay` never persist.
  - **Stepping aside:** `section` to a section whose page guide is due, and to Week without a plan: `close` within one tick of the reply, not at the 15 s refresh.
  - **Scoped overlays:** with a left-open LD sheet, Character, the Overview and Food all open their native screens. Back on Character goes back.
  - **Guides:** Back ends the page guide. Week's first native visit starts no page guide, and `tourSeen` saves and says the skip message.
  - **The glance:** no open-then-close, and a glance tap with no replay target still opens the screen.
  - **Size and timing:** the snapshot stays under 150 KB on a 14-day fixture; `projectionMs` and the `day`/`section` command times are within the §3.4 budgets.
- **`native-director-sheets.test.js`** (4A):
  - each sheet model against instSheet, focusSheet, mealPreview, moveSheet and disruptSheet on the page (same times, same texts);
  - `gone`; `intent` from the glance;
  - celebrations held and `xpCue` played; `celebrationTarget` in the frame;
  - Done → a level-up shows → the screen steps aside → the level-up is dismissed → the screen reopens;
  - replies for every command;
  - after the page's set-up with `director.sheets.v1`: the screen opens on Today, the projection carries the build, the page's build is stopped, and no page guide starts.
- **`native-director-forms.test.js`** (4B):
  - each schema against the page's rendered form controls by id (labels, min, max, step, defaults, options, order);
  - `finishSetup` without sleep returns the page's message;
  - the native set-up's first build: the page never calls `runBuild`, and `nativeBuild` reaches the reply;
  - `clock` and `lengths` against `fHm` and `mins`;
  - 48 sample minutes formatted as the screen formats them, against `fHm` in en-US and en-GB.
- **`native-director-guide.test.js`** (grows each merge): steps and anchors equal TOURS filtered as `tourSteps` filters them; no page tour while the screen draws that guide; `help` equals the key `tourHelp` picks.
- **`native-director.test.js`**: adjusted for the single day value and the arrival reset; its 67 checks otherwise stay.
- **`ldsheet.test.js`** stays: the page sheets still cover the bottom bar.
- **Each tab's existing web tests** pass unchanged against the kit changes, and Workout plan's projection fixture stays byte-identical after `celebrationTarget` moves.
- **`native-handover-sim.test.js`** (A28):
  - **New hops:**
    - `section-director`;
    - `sheet-director`;
    - `do-director`: a `done` or `logToday` mid-hop, sometimes answered after the screen left;
    - `cold-director`: a reload with `ST.ui.tab='week'`;
    - `lost-reply-director`: `logToday` answered after the screen hid, then reopen and tap again.
  - **New faults:**
    - a second ledger event for one `op` or instance;
    - Life Director showing off its view;
    - a reply landing on a hidden screen;
    - the screen showing a section that `showing()` does not name.
  - The existing invariants still hold.
  - **Shown failing on four mutations:**
    - dropping the `op` → `clientEventId` link;
    - dropping `covers` from `blocked`;
    - dropping the warm handshake;
    - dropping the step-aside after a command.
- **Fixtures** (`gen_native_fixtures.js director()`, all reached through the page):
  - phase 3: director-week, -week14, -quests, -goals, -goals-clarify, -settings, -guide-week;
  - 4A: -sheet-session (now, later, past, with the "why" fold open), -sheet-timer (running, paused), -sheet-meal, -sheet-move (and none), -sheet-disrupt, -guide-today;
  - 4B: -time-first, -time, -sheet-rule-new, -sheet-rule-rotating, -sheet-goal-type, -sheet-goal-read, -sheet-goal-custom, -first-run, -guide-setup;
  - reply files per merge.
  - `director-day.js` gains seed options for a custom goal (to get a clarification prompt), a goal with a deadline target (a trajectory warning), 14 days, and a regroup banner. Not verified: whether the current lived-in week produces clarification prompts or trajectory warnings; probably not.

**Kotlin unit (`native/src/test/java/com/lifemax/rpg/…`)**
- `LifeDirectorSectionsTest`: decoders (malformed, missing or mistyped key rejected; older sequence ignored); `covers`, including an absent field; `pendingSection` and a refused section; an undrawable projected section keeps the last body; `SectionFlick` thresholds and exclusions.
- `SheetHostTest` (core): stack behaviour; `SheetDrag.dismisses` against the page's numbers; Back pops the top; nothing is open after `hidden`.
- `LifeDirectorSheetsTest`:
  - sheet decoders;
  - operation ids:
    - an id is kept per (command, target) until a terminal reply;
    - it survives hide and is reused on the next tap;
    - after an `ok` reply, a new one is minted;
  - a second tap is dropped while pending;
  - a late reply to an older request is ignored;
  - the page's message shows once;
  - `gone` closes;
  - `intent` is taken once.
- `LifeDirectorFormsTest`: schema decode; payloads; drafts survive hide with their id; A18 (minutes 0..1439, `YYYY-MM-DD`, epoch seconds); `h12` → `is24Hour`; steppers clamp to the schema and commit once after 400 ms of quiet; the mini dial snaps to `step` and labels from `clock`/`lengths`.
- `LifeDirectorGuideTest`: missing anchors are skipped; Back means Skip; no auto-start during a build; ? picks `help`.
- `ImeStateTest` (shell): the bar hides and the inset drops while the keyboard is up.
- `LifeDirectorTest` and `LifeDirectorDialTest` stay green; `NativeScreensTest` is unchanged.

**Screenshots (Paparazzi, A32, screenfuls at device resolution, dark and light, plus large text; 0.001 % for the new ones as NATIVE_TAB_GUIDE asks)**

| Test | Merge | What it shows |
|---|---|---|
| `DirectorWeekScreenshotTest` | 3 | Week top, the 14 rows, yield and fit with the fold open, light, large text |
| `DirectorQuestsScreenshotTest` | 3 | |
| `DirectorGoalsScreenshotTest` | 3 | cards, paused, clarify plus warning, empty, the confirmation |
| `DirectorSettingsScreenshotTest` | 3 | top, sliders, data and diagnostics, restore |
| `DirectorSheetsScreenshotTest` | 4A | the session sheet in three modes, the timer running and paused, meal, Move, disrupt, the regroup banner, a picker child sheet |
| `DirectorSetupScreenshotTest` | 4B | My time first and later, the rule sheet new and rotating, the goal type grid, the goal form read and custom, first run |
| `DirectorGuideScreenshotTest` | grows each merge | one per Week, Today and set-up step |
| `GalleryScreenshotTest` | 3, 4A | gains SheetHost (3) and XpCelebration (a frame, 4A) |
| One keyboard-up reference per native tab with a text field | NAV-5 | |

- Every existing Life Director and Workout plan reference stays unchanged. `DialConfirmSheet` keeps its pixels on the host, because entries are drawn settled and there is no blur.
- About 60 new references in all.
- Paparazzi draws one frame, so a sheet's slide, the drag, the morph, the handle pulse and the orbs in flight have no image.

**Phone checks for the owner (S25 Ultra, release build)**

Phase 3:
1. Flick across Today, Week, Goals and Quests. It never fires from the strip or the dial, and never during a vertical scroll. The header stays still. A flick from Quests to My time shows the classic My time; that is expected until 4B.
2. Week → tap Thursday → Today on Thursday. Leave Life Director and come back: Today on today.
3. Open a session (still the page's sheet in phase 3) and leave Life Director with it open. Go to Character, then the Overview: both native screens open.
4. Close the app while on Week, start it again, open Life Director: the native Week, not the classic one.
5. Week's first visit plays its guide natively, and Back ends it. Back during the page's Today guide ends it.
6. Priority, pause and remove on a goal. A slider's label follows the finger and the value commits once on release. Read Diagnostics' compute ms after each change and report it. That is the first phone figure for the engine.
7. Copy backup, paste it in a notes app; Restore it back.
8. Drag phase 2's confirmation down to dismiss it.

4A:

9. Open a session, Mark done with 20 minutes. One XP burst and one sound, and the Log shows one activity. Repeat quickly twice: still one.
10. Earn a level-up from a Done. It shows on the page; tap it away; the native screen is back at once.
11. The timer ring for 2 minutes, pause and resume, then leave the app and come back. The time is right and the ring ticks only while shown.
12. Move, Skip, Pin, each disruption, and the energy check-in. Back closes each sheet.
13. Tap a session on the Overview glance. The native sheet opens directly.
14. Settings → Show the guide again → Today's guide natively, with the dial's name tags and pulsing handles. Back ends it.

NAV-5:

15. Type in Log's, Recipes' and Library's fields. The shared bar hides while the keyboard is up and returns after.

4B:

16. Set up from a fresh Life Director: drag the mini dial, add a rotating shift with the pickers, add a "Read More" goal with a target, then Build my week. The build plays natively with no classic frame, then Today's guide.
17. The keyboard over every form field. TalkBack across a form.

Each merge:

18. Turn the switch off and on mid-session. The classic Life Director is always intact.

## 6. Risks and mitigations
1. **XP paid twice or lost (4A).**
   - Event ids come from the instance and revision, or from `op` for Log it for today. The ledger and `ldEventId` refuse duplicates (verified).
   - Ids survive hide and clear only on a terminal reply, so a lost reply never mints a new id (A14).
   - Repeat tests, the sim's "two events" fault and lost-reply hop, and the owner's check 9.
2. **A refused Life Director save stays in memory with its banner** (the store's rule). It is not taken back as LifeMax's award is.
   - Replies say `ok:false` with the page's words, and the banner shows. A repeat re-runs `persist()`, so its `ok` is honest.
   - A staged commit for Life Director's own writes (A15) stays a backlog item outside this visual track.
3. **Engine time on the WebView thread.** A re-plan runs synchronously in most writes: 10–218 ms here at 7 days, about 1 s at 14 days; the phone is unmeasured. Commands queue behind it.
   - Pending states on the control. Sliders commit on release and steppers after 400 ms of quiet. One command at a time.
   - Diagnostics gives the phone figure (check 6).
   - If it is slow, defer the re-plan behind a "Re-planning…" reply in a follow-up (the reply would then say "saved, plan updating").
4. **The page renders under the screen on every section and day change.**
   - It keeps hand-overs correct: the page underneath always matches. Its cost is asserted (§3.4) and measured on the phone with TabTiming.
   - The fallback is to render lazily, only when the screen steps aside. Its catch: `showing()` reads the DOM through `tourCanRun` (logic.js:4183-4188), so a lazy render must first make `tourCanRun` read state, or the covering rule goes stale.
5. **Projection size and cost**, with every covered section on each refresh.
   - The test bound is under 150 KB, with timing budgets. The fallback is the current section plus Today.
6. **Helper extraction and the `Do` split touch the classic page.**
   - HTML goldens for the touched renderers and an `ST` golden for every write case, both recorded before the change, must stay identical.
7. **Kit changes reach every native tab** (warm handshake, `arrive`, step-aside after a command).
   - Each is opt-in or a no-op for a tab that does not use it, except the step-aside, which only acts when that tab is already `blocked()` on its own view.
   - Every tab's web tests and the sim run on the change. The sim is shown failing on the step-aside and handshake mutations.
8. **Forms and the keyboard.** The IME pads the content view, so the shared bar rides on the keyboard (NAV-5).
   - NAV-5 lands first as its own shell change, with phone checks 15 and 17. Fields scroll into view inside the sheet's scroll.
9. **Pickers and formatting.** Intl on the page and java.time on the phone may disagree in some locales.
   - Labels that follow a finger are the page's own strings. A form's time field after a picker is formatted natively, and tested against the page in en-US and en-GB. After a save the field shows the page's string. Not verified beyond those two locales.
10. **A guide in a lazy list.** Anchors exist only when composed.
    - Scroll first with `animateScrollToItem`, then measure; missing steps are skipped, as on the page. Week's three steps prove the layer in phase 3 before Today's dial modes arrive.
11. **4A's first build with set-up still on the page.** The page starts its build before the screen opens and `cover()` stops it, so a few classic frames may show.
    - It is ld2's existing hand-over, gone in 4B when set-up is native. Reasoned from code, not run.
12. **Writers outside the sections.** Voice-gen5 changes `ST` through `_debug()` (50-voice-gen5.js:15, 23). Calendar import, `tick` and the glance also write.
    - They reach the screen through `render()` → `onChange`. The glance's own `persist()` does not render Life Director, so its change shows at the next 15 s refresh. Acceptable.
13. **One switch over three merges.**
    - The developer "native parts" control narrows `covers` and guides without a new APK.
14. **Version skew** (APK and page).
    - Capabilities per merge. An old page sends no `covers`, and a new field is ignored by old decoders. `webMinApi` does not rise.
15. **Merge conflicts** in logic.js line positions, 50-native-tabkit.js (ld2's `active()`, main's observer and `follow()`, swipe's `coverable()` and `wouldOpen`), the sim, settings.html, and the native Settings key list.
    - Start after ld2, settings and swipe. NativeApi is set at merge time. §3.3 and §3.7 are rebased on what ld2's review merges.
16. **logic.js grows past A26.**
    - The recorded exception has a cap and a retirement split that must land before the switch defaults to on (§3.6).
17. **Fonts.** Roboto replaces Inter and JetBrains Mono, as in phase 1. It is a known difference, listed in the ledger.
18. **Haptics on One UI.** Native buzzes follow the projected `haptics` level, and the page's own buzzes follow `fxOpt()` from 3A.

## 7. Effort

An "agent build step" is about one reviewable commit of work, as the earlier tracks were cut:
- Recipes took about 4: web half, screen, web review, native review.
- Library took about 5.
- Life Director phase 2 took about 8: 3 web steps, plus 5 native steps (dial layers; domain and commands; drag and confirmation; build; tests and docs).

| Merge | Steps | Breakdown | Comparable to |
|---|---|---|---|
| Phase 3 | 11 | **3A:** (1) inventory, A26 exception, kit changes; (2) page fixes, `nativeWeek`/`nativeQuests` with goldens, commands, `covers`, guide rule, fixtures, tests, sim; (3) native frame, transition, flick; (4) Week and Quests with screenshots; (5) coach marks and Week's guide; (6) half-way review. **3B:** (7) SheetHost with `DialConfirmSheet` moved; (8) `nativeGoals`/`nativeSettings`, `Do` with repeat tests, fixtures, sim; (9) Goals and confirmation; (10) Settings with sliders, backup and diagnostics; (11) review, docs, NativeApi | two Library tracks |
| 4A | 9 | (1) sheet models and keyed `Do`; (2) celebrations, `celebrationTarget`, `.luov` and `watch`, glance intent; (3) fixtures, tests, sim; (4) XpCelebration to core; (5) session, timer and meal sheets; (6) Move, disrupt and pickers; (7) Today's remaining commands; (8) Today's guide with `TourMode`; (9) review, docs, NativeApi | a little more than Life Director phase 2 |
| NAV-5 | 2 | (1) `ImeState` and ChromeHost; (2) sim and references | a fix track |
| 4B | 8 | (1) models, `Do` and parity goldens; (2) schemas, fixtures, sim; (3) the form kit; (4) My time and the mini dial; (5) rule sheet; (6) goal sheets with the morph; (7) first run, set-up, first build, set-up's guide; (8) review, docs, NativeApi | Life Director phase 2 |
| **Total** | **≈30** | | about 7½ Recipes tracks, 6 Library tracks or 4 Life Director phase 2s |

If done as one merge, add about 3 steps for the integration review of the combined change, and expect any finding to hold back every part. The A26 retirement split after the track is not counted: about 3 steps (the pure move with a byte-identical page, the controller split, the tests that read logic.js by path).

**Not verified by me:**
- engine timings and projection cost on the phone;
- Compose's Back ordering on the device (phase 2 depends on it too);
- the material3 picker API in this BOM;
- Intl against java.time formatting outside en-US and en-GB;
- the bottom bar over the keyboard on the phone;
- whether the lived-in fixture yields clarification prompts and trajectory warnings;
- the classic frames of 4A's first build before `cover()` (reasoned from ld2's code).

The projection sizes for the new sections are estimates from a stand-in for the proposed fields.

Scratch evidence:
- /tmp/ld34-main (main 8ad7560, built);
- /tmp/ld34-scripts/probe-day.js, probe-leak.js, probe-leak-control.js, probe-size.js, engine-time.js;
- /tmp/ld34-review/probe-review.js (cases A–D, re-run on /tmp/ld34-main/page.html with the reviewer's patched page);
- /tmp/ld34-final/probe-ov.js (the Overview blocked by a left-open LD sheet, with its control run);
- the earlier probes in /tmp/ldprobe-scripts/probe2.js, re-run against /tmp/ld34-main/page.html.
