# Native Life Director logic: audit and plan

Audit date: 2026-11-08. Branch audited: `codex/director-dial-controls` at `2fdf60b`. Read-only audit of
`lifemax_modular_v73/src/features/life-director/logic.js` (3925 lines) and `engine.js` (2622 lines), the page's native
projection (`src/runtime/segments/50-native-director.js`), the Kotlin under
`android-wrapper/native/src/main/java/com/lifemax/rpg/feature/lifedirector` and `core/economy/LifeDirector.kt`, and the
Life Director tests and fixtures. Nothing was built. The only things run were node snippets that load `engine.js` in a `vm`
(section 3). Line numbers are for this commit and are approximate at the end of a range (a range runs from a function's first
line to the line before the next one).

Goal: a native Life Director whose decisions are provably identical to the page's, so that saving can later move from the
page to native (the "logic phase"). Same method as the economy and quests: spec, golden cases recorded from the page, fresh
Kotlin that must reproduce every case.

The scheduler spec (`docs/specs/LIFE_DIRECTOR_ENGINE_SPEC.md`, for `engine.js`) is written by us, part by part as each work package
starts (owner decision: no outside agent writes it; the brief `docs/briefs/GEMINI_LIFE_DIRECTOR_ENGINE_SPEC.md` is unused). This plan
covers the rest (the host in `logic.js`, the store, the projection) and how the engine port is proven and sequenced.

## 1. Summary

- The native side is a screen, not logic. About 18,000 lines of Kotlin under `feature/lifedirector` decode the page's
  projection and draw it; they send nine kinds of command back (section 2). The page decides everything: the plan, the
  blocks, the words, the timer, the dial's stops. The only native Life Director rules are the economy's four
  (`core/economy/LifeDirector.kt`, LD-1 to LD-4), replayed by the economy goldens.
- The page keeps all Life Director state in its own `localStorage` key (`lifemax_director_state_v1`, plus `.bak` and a
  quarantine key), not in the main save. A native writer therefore needs its own store codec as well as the economy save
  codec (which `awardActivity` and quest bounties write through).
- `logic.js` is about 40% presentation (render, dial SVG, build animation, tours, sheets). The logic that has to be ported is
  roughly 1,400 lines of host logic plus the two command tables `Do` and `Today` (about 330 lines), and a read model layer
  (day blocks, now state, week, goals, quests, settings, time) of about 600 lines that the page already computes once for
  both its own UI and the projection.
- `engine.js` splits into about 1,900 lines of pure scheduler (port), about 150 lines of built-in test scenarios (use as
  fixtures, do not port), about 100 lines of store, and about 265 lines of `LifeMaxHost` (an adapter onto the page's
  economy and quests, to be replaced by native adapters). The scheduler is deterministic on the same input: the ten built-in
  scenarios each produce byte-identical output when planned twice (checked, section 3).
- There are no engine golden cases today. `director-sleep.test.js` is the only test that calls the engine directly; the other
  `director-*.test.js` (14) drive the page in a browser, and the Kotlin tests replay screen fixtures (`director-*-golden.json`)
  that check decoding, not scheduling.
- Recommended first work package: WP-LD1, the recording harness and the time and hash kernel (section 5). It does not wait for
  the engine spec, it removes the largest determinism risk first, and every later package reuses its recorder.

## 2. What the native side does today

### 2.1 Reads (page to native)

The page sends one projection (`LifeDirector.native`, assembled in `50-native-director.js`; `logic.js:3006-3735`). Kotlin
decodes it in `WebLifeDirectorRepository.accept` (protocol 1, `webSessionId`, `sequence`; older updates dropped; a projection
from another page session accepted only on an explicit open) into `LifeDirectorProjection`. Decoders, one per section:

| Page function (`logic.js`) | Kotlin decoder (`feature/lifedirector/data`) | Carries |
|---|---|---|
| `nativeFrame` 3006 | `LifeDirectorJson` | header dot and line, clock (h12, locale), tabs, banner, notice, reduced motion |
| `nativeToday`, `nativeHandles`, `nativeBuild`, `nativeCore`, `nativeMix`, `nativeUpNext`, `nativeRows` 3019-3192 | `LifeDirectorJson`, `DialMotionJson` | dial arcs, centre, mix, strip, up next, running order, handles with every stop and its preview, build plan |
| `nativeWeek`, `nativeQuests`, `nativeSection` 3272-3307 | `SectionsJson` | week rows, quests, milestones |
| `nativeGoals`, `nativeSettings` 3613-3649 | `GoalsSettingsJson` | goal cards with shares and words, every setting with one label per stop |
| `nativeTime`, `nativeFirstRun`, `nativeForm` 3652-3690 | `TimeJson`, `FormSchemaJson`, `FormSheetsJson` | the sleep dial (clock text for each quarter hour), commitment and goal forms |
| `nativeSessionSheet`, `nativeSheet`, `nativeRemoval` 3344-3402 | `SheetsJson`, `TimerMealJson`, `MoveDisruptJson`, `RemovalJson` | session, timer, meal, Move, Something came up sheets; whether a session may be removed |
| `nativeGuide`, `nativeHelp` 3706-3728 | `GuideJson`, `GuideHelpJson` | guides |
| `glance` 2806 | `DirectorGlanceJson` | the Overview card |
| `nativeFrame.notice` | `FeedbackJson` | the page's last message and celebration target |

All times, words, colours and stop lists arrive decided. Native does no scheduling, no time-zone arithmetic and no snapping:
the dial's drag stops come from `dialStops` (`logic.js:1765`) and are only looked up by the screen.

### 2.2 Writes (native to page)

`LifeDirectorCommands` (`domain/LifeDirectorRepository.kt`) has nine calls, implemented by `bridge/web/NativeLifeDirectorPlugin.kt`:

| Command | Page function it runs | Writes? |
|---|---|---|
| `showDay`, `gotoDay`, `showSection`, `showSheet` | view state (`ui.dayIdx`, `ui.tab`, `ui.sheet`) | no |
| `tourSeen` | `nativeTourSeen` 3729 | `ST.ui.tours` |
| `change(name, args)` | `Do.*` 2331-2526 (`nativeRun` 3691) | yes: goals, settings, My time, backup |
| `today(name, args)` | `Today.*` 3420-3604 (`nativeTodayRun` 3606) | yes: done, logToday, skip, unskip, pin, moveTo, timer, disrupt, energy, replan, regroup, dismissBanner |
| `dialDrop`, `dialApply` | `nativeDrop`/`nativeApply` 3252-3271, `applyDial` 1883 | `dialApply` writes |

Every write is idempotent by an operation id the screen mints, and the page answers `{ok, changed, message}`, `ok` only once
saved. The page remains the only writer.

### 2.3 What is native logic today

- `core/economy/LifeDirector.kt` (175 lines): LD-1 `typeForTag`, LD-2 `xpFor`, LD-3 `awardActivity`, LD-4 `completeQuest`.
  Complete against ECONOMY_SPEC section 11.5 (59 `director` golden cases). Nothing calls it from a screen yet.
- Geometry that depends only on what the page sent (`DragModel`, `DialHandles`, `DialBuild`, `DayWheel`, `SectionFlick`,
  `ReturnToTodayMotion`). Not rules.

### 2.4 What remains page-only

Everything else: the engine, the Life Director store, all of section 3's host logic, the timer, meal links (they read Smart Diet
through `LifeMaxMeals`), quest reading (`Host.readQuests`, which needs the quest library, `questKey`, `isQuestDone`,
`questLocked`) and plan sessions (`planDay`), and the external consumers of Life Director (Money's `shifts`/`workRules`, voice,
the Journal coach's `dayStats`, the widget snapshot, the Overview glance, and `33-android.js:45`, which reads the stored
schedule directly from `localStorage`).

## 3. Map of `logic.js`

Legend. **Pure** = a function of `ST` (and the engine) and the clock; no DOM, no storage. **Mut** = changes `ST` in memory.
**Side** = timer, DOM, storage, bridge, toast, host call. `now` = `Date.now()` read inside (needs a clock interface).
`ST` is the Life Director state (section 3.9 lists the fields); `ui` is page-only view state that must not be ported
(banner is the exception: it is read by logic).

### 3.1 Scheduling-adjacent

| Lines | Function | Reads | Writes | Kind |
|---|---|---|---|---|
| 102-110 | `hostQuests`, `completionHistory` | `ST.prefs`, `questOverlay`, `ledger` | none | pure; calls `Host.readQuests`, `Host.readPlanSessions` (page economy/quests) |
| 111-119 | `buildInput` | `ST` (availability, fixedCommitments, goals, schedule, prefs, energyCheckin) | none | pure, `now` |
| 120-131 | `applyOutput` | `out` | `ST.schedule`, `ST.changeLog`, `ST.lastRejected`; `ui.banner`, `ui.prevPlan`, build queue | Mut + view. Refuses an `INVARIANT_VIOLATION` output and keeps the old plan |
| 132-143 | `regenerate`, `autoReplan` | as above | `ST.schedule`, `ST.lastGeneratedWeekKey` | Mut + persist + toast. Calls `E.generateSchedule` |
| 144-147 | `planTimeChange` | `ST.schedule` | none | pure; `E.rescheduleActivity` with `TIME_CHANGED` |
| 148 | `weekKey` | tz | none | pure (Monday of the local week) |
| 149-168 | `reschedule`, `summarize` | `ST` | `ST.schedule`, `ST.fixedCommitments`, `ST.availability.blackouts`, `ST.changeLog` | Mut + toast on a rejected move. Calls `E.rescheduleActivity` |
| 394-461 | `horizonDays`, `unplannedSegments`, `dayBlocks` | `ST.schedule`, `ST.availability`, `ST.fixedCommitments`, `ST.prefs` | none | pure; `dayBlocks` merges segments, tasks and free time and cuts stale downtime that a moved task now overlaps. Calls `E.deriveAvailability` |
| 578-601 | `ruleOn`, `ruleInstances` | `ST.availability.rules` | none | pure. A second copy of the engine's `ruleAppliesOn` (defect D3) |
| 1043-1142 | `nextUp`, `liveTimeline`, `nowState` | schedule, `ST.ui.focus`, `now` | none | pure. Thresholds SOON 15 min, ENDING 10 min, LATE 10 min decide the amber "kick" |
| 1302-1346 | `weekSegs`, `weekBar`, `weekModel` | schedule | none | pure |
| 1365-1371 | `unscheduledGroups` | schedule diagnostics | none | pure |
| 1489-1517 | `timeModel` | `ST.availability`, prefs | none | pure |
| 2194-2214 | `moveTimes`, `moveModel` | schedule, `now` | none | pure; half-hour starts from the next quarter hour, first 48 |
| 2215-2241 | `disruptParts` | prefs, `now` | none | pure (option lists) |
| 2930-2965 | `workRules`, `shifts` | `ST.availability.rules`, `fixedCommitments`, `blackouts` | none | pure; the Money tab's source of shifts (excludes a shift half or more covered by a blackout) |
| 3086-3112 | `nativeHandles`, `nativeBuild` | rules, stops | none | pure |

### 3.2 Dial editing (outer ring handles)

| Lines | Function | Kind |
|---|---|---|
| 1724-1752 | `dialAngle`, `dragOn`, `moveHandle` | DOM only; not ported |
| 1753-1782 | `snapEp`, `dialStops` | pure: every place a handle can land, quarter-hour snap, every minute on a day whose clocks change; cached per zone and day |
| 1783-1817 | `dragPreview`, `dialChange`, `dialStep`, `dialHandles` | pure: the length a release gives the rule, mod 1440; under 15 minutes is "Too short" |
| 1853-1900 | `dialConfirm`, `applyDial` | `applyDial` Mut: sets the rule, `E.rescheduleActivity(TIME_CHANGED)`, rolls the rule back if the engine refuses; saves |
| 3240-3271 | `nativeChange`, `nativeDrop`, `nativeApply` | gate by `showing()==='today'`; same functions as above |

### 3.3 Completion and XP awarding (links to the economy)

| Lines | Function | Reads | Writes | Notes |
|---|---|---|---|---|
| 277-282 | `unsaved`, `xpWords` | host reply | none | maps `HOST_SAVE_FAILED`, `HOST_READ_ONLY`, `HOST_NOT_CONNECTED` to words |
| 284-305 | `afterEvent` | quests, goals, schedule | `ST.goals`, `ST.questOverlay[key].eventIds`, `ST.schedule.scheduledInstances` | Mut. Calls `E.reconcileQuestProgress`, then `Host.completeQuest` (LD-4) when a quest's requirements are met and the XP save worked |
| 307-332 | `finishInstance` | schedule, prefs, ledger | `ST.ledger`, schedule and history, goals, overlay; main save via `Host.awardActivity` (LD-3) | Mut + side. Order: `E.recordCompletion` (ledger first), then the economy award, then history, then quest progress, then a residual re-plan, then `persist`. A failed award does not undo the ledger event (defect D2) |
| 338-348 | `doable`, `showAsDoneNow` | `now`, tz | none | pure. A session may be done from 6 hours before it, or any time on its day |
| 351-371 | `repairEarlyDone` | history | history, schedule, `ST.fixes.earlyDone`, `ui.banner` | Mut, once per install (flag) |
| 375-391 | `logToday` | schedule, ledger | ledger, history, goals | Mut + side (`Host.awardActivity`). Records an ad-hoc event for today and keeps the planned session |
| 1215-1220 | `xpOf` | `Host.xpFor` (LD-2 with `commit` off) | none | preview only |
| 3421-3439 | `Today.done`, `Today.logToday` | | | wrappers: guard, clamp minutes to 1..480, answer |
| 3440-3470 | `Today.skip`, `Today.unskip` | ledger, schedule | ledger via `E.applyMissConsequence('SKIP')`, schedule via `reschedule(SKIPPED)` | Mut. Take-back is in memory only, 2 minutes, valid while the plan JSON string is unchanged (defect D8) |

LD-1 to LD-4 in `core/economy/LifeDirector.kt` cover the award side; the engine ledger (event ids, rest tokens, misses) is not
yet native.

### 3.4 Timers and focus sessions

| Lines | Function | Kind |
|---|---|---|
| 1929 | `focusMs` | pure: `max(0, (paused ?? Date.now()) - start - pausedMs)`, wall clock in milliseconds |
| 1933-1939 | `focusModel` | pure (XP estimate via `xpOf`) |
| 1940-1951, 1952-1975 | `focusSheet`, `focusTick` | DOM and a 1 s interval (`tickClocks` 2766); `focusTick` calls `Today.timer({act:'autoFinish'})` when elapsed reaches the plan |
| 3491-3552 | `Today.timer` | Mut: acts `start`, `replace`, `pause`, `resume`, `reset`, `finish`, `cancel`, `autoFinish`; state is `ST.ui.focus = {id, start, paused, pausedMs, plannedMs}` (persisted, milliseconds) |

Auto finish logs the planned minutes, not elapsed, and only runs while the page is open (defect D7).

### 3.5 Meals

| Lines | Function | Kind |
|---|---|---|
| 1074-1108 | `scheduledMealRef`, `scheduledMeal`, `mealAnchor`, `openScheduledMeal`, `mealModel` | reads Smart Diet through `LifeMaxMeals.resolveScheduled` / `openScheduled` (page globals). The meal block itself is the engine's (`placeMeal`) |
| 1099-1107 | `mealPreview` | DOM sheet |
| 2464-2470 | `Do.meals` | Mut: `ST.prefs.mealsEnabled`, auto re-plan |

Meal content is not Life Director's. Native needs a Smart Diet adapter before the meal sheet can be native-computed.

### 3.6 Goals and quest overlays

| Lines | Function | Kind |
|---|---|---|
| 1385-1406 | `goalShare`, `goalCards` | pure: allocation, trajectory warning, clarification prompt, words |
| 1603-1673 | `goalTypeModel`, `goalFormModel`, `goalForm` | pure form model (fold fields for a target) plus DOM |
| 1425-1440 | `questsModel` | pure: coverage rows, passive reminders, milestones (from `Host.milestones`) |
| 2332-2355 | `Do.goalPriority/goalPause/goalRemove/clarify` | Mut + re-plan |
| 2498-2518 | `Do.goalAdd` | Mut; id is `g-` plus the first 12 characters of the operation id |
| 102-105, 284-305 | quest overlay | `ST.questOverlay[questKey].eventIds` counts sessions toward quests; the quest board owns the period and the XP |
| 171-240 | `syncHost` step 3 | Mut: retire planned blocks whose quests are done, or that are untimed; adds `passiveReminders` |

### 3.7 Availability rules and My time

| Lines | Function | Kind |
|---|---|---|
| 1456-1475 | `daysText`, `ruleText`, `sleepRules`, `sleepContext` | pure words |
| 1560-1602 | `ruleForm`, `ruleSheet` | pure form model plus DOM |
| 2411-2424 | `Do.sleepSave` | Mut: replaces SLEEP rules, `sleepRequirementMinutes`, re-plans; under 5 hours refused |
| 2425-2455 | `Do.ruleSave`, `Do.ruleRemove` | Mut: new rule id is `r-` plus the operation id; rotation `{anchorDate, cycleLength: on+off, activeDays: 0..on-1}` |
| 2456-2470 | `Do.routine`, `Do.meals` | Mut: preference minutes, clamped to the stepper's range |
| 2356-2376 | `Do.pref`, `prefRange`, `restDay` | Mut: validated against the option tables, then re-plan |
| 2471-2497 | `Do.activityAtAdd` | Mut: a pinned manual session; checks the local time exists (DST gap), is in the future, inside the horizon, and clear of blocked kinds |
| 3553-3572 | `Today.disrupt` | Mut: extra shift, block hours, day off, sick: builds a trigger for `E.rescheduleActivity` |
| 2519-2523 | `Do.finishSetup` | Mut: needs a SLEEP rule; sets `setupComplete`, first `regenerate` |

### 3.8 Rollover, misses, host sync, lifecycle

| Lines | Function | Kind |
|---|---|---|
| 171-240 | `syncHost` | Mut + side. (1) activities logged elsewhere become done blocks (no XP, already paid) or ad-hoc ledger events, via a cursor and a `seen` set; (2) activities undone in the Log are revoked in the ledger and the block goes back to the plan if still ahead; (3) quest check-offs retire blocks |
| 242-268 | `processMisses` | Mut: `E.detectMisses` with the grace setting; at most 12 per tick; each miss repaired locally (`reschedule MISSED`) then `E.applyMissConsequence`; a thrown repair records the block as `MISSED_UNEXPLAINED` and goes on; may raise the regroup banner |
| 269-275 | `maybeRollover` | Mut: restores the intensity after light mode expires; full `regenerate` when the week key changed, fewer than 2 days of plan remain, or the zone changed |
| 2900-2907 | `tick` | runs `syncHost`, `processMisses`, `maybeRollover` in that order, only while the Life Director view is mounted and active; every 5 minutes and on `visibilitychange` (defect D4) |
| 2908-2928 | `mount` | DOM wiring and intervals |
| 3585-3599 | `Today.regroup` | Mut: pause 48 hours, light mode for 7 days, or carry on |

### 3.9 Persistence

| Lines | Function | Notes |
|---|---|---|
| 71-83 | `persist` | drops history older than 21 days by `endEpoch`, keeps 60 change-log entries, stamps `updatedEpoch`, `store.save` (verified by read-back, previous copy kept as `.bak`); failure sets a red banner |
| 84-99 | `boot` | `store.load` (migrate, restore `.bak`, quarantine unreadable data), ensure `ledger`, `questOverlay`, `hostSync`, one-time `repairEarlyDone`, banner for storage problems or migration |
| 2384-2410 | `Do.exportBackup/importBackup/reset` | text backup `{format:'lifemax-director-backup', version, state}` |
| `engine.js` 2240-2340 | `LifeDirectorStore` | key `lifemax_director_state_v1`, schema version 1, `migrate`, `validate`, quarantine |
| `33-android.js:45, 173` | | the Android layer reads the stored schedule for the widget and includes the key in device backups |

State fields (`ST`), as read in `logic.js` by number of uses: `schedule` (66, with `scheduledInstances`, `historyInstances`,
`segments`, `passiveReminders`, `goalAllocation`, `questCoverage`, `trajectoryWarnings`, `clarificationPrompts`,
`unscheduledDiagnostics`, `horizonStartEpoch`/`horizonEndEpoch`, `timezone`), `ui` (`focus`, `tab`, `tours`, `lab`,
`hostNoticeDismissed`), `setupComplete`, `availability` (`rules`, `blackouts`, `timezone`, `sleepRequirementMinutes`), `ledger`
(`events`, `order`, `revoked`, `restTokens`, `completionsSinceToken`, `consecutiveMisses`, `consequencesByBwc`, `regroupActive`,
`streaks`, `archivedIds`), `prefs`, `fixedCommitments`, `goals`, `questOverlay`, `hostSync` (`cursor`, `seen`), `fixes`,
`changeLog`, `energyCheckin`, `lastGeneratedWeekKey`, `lastRejected`, `prefsLightUntil`, `prefsLightRestore`, `playerId`,
`updatedEpoch`, plus `labQuests` and `bridge` defaults.

### 3.10 Presentation and page-only (not ported)

Motion helpers 31-52; render and all `*Html` 462-560, 615-800, 1143-1300, 1347-1384, 1518-1559, 1679-1722; the build animation
802-1040 (`queueBuild`, `buildPlan`, `runBuild`); tours 1998-2160 (their seen flags, `ST.ui.tours`, are state and are already
written by the native `tourSeen`); sheet plumbing 2242-2324; Lab 2666-2765 (a test harness); glance DOM 2770-2860; widget
snapshot 2966-3005 (rasterises the page's SVG, so a native widget needs its own drawing, which the native dial already has).

### 3.11 Voice and coach contracts

`voiceApi` (3761-3883) and `coachApi` (3884-3900): `add`, `remove`, `find`, `overlaps`, `workOn`, `sessions`,
`commitments`, `completeSession`, `skipSession`, `moveSession`, `moveCommitment`, `dayItems`, and `coach.dayStats`. These are
callers of the same functions above. They must keep working during shadow mode and be re-pointed at cutover.

## 4. Porting `engine.js`: size and risk

### 4.1 Size

| Part (`engine.js` lines) | JS lines | Port? | Risk |
|---|---|---|---|
| Hashing, RNG, time kernel 34-135 | 100 | yes (WP-LD1) | medium (time zones) |
| Grid, rules, preferences, sleep, availability 151-466 | 320 | yes | medium-high |
| Tags, goals, templates, quests, candidates 468-800 | 330 | yes; data tables exported from the page as JSON | medium |
| Energy model, state, scoring, commit/remove 804-1160 | 360 | yes | high (floats) |
| `generateSchedule`, repair, fill, output 1163-1535 | 370 | yes | high |
| Validation 1537-1618 | 80 | yes | low-medium |
| `rescheduleActivity` and hold placement 1619-1905 | 290 | yes | high |
| Ledger, quest progress, misses 1908-2085 | 180 | yes | medium |
| Scenarios 2087-2240 | 150 | no (use as fixtures) | n/a |
| Store 2240-2340 | 100 | yes, as a codec (WP-LD7) | high (save format) |
| `LifeMaxHost` 2355-2620 | 265 | no; replaced by adapters (WP-LD10) | medium |

Estimate: 2,500 to 3,500 lines of Kotlin for the scheduler, 1,500 for host logic and read models, tests on top.

### 4.2 Determinism hazards, each with the countermeasure

1. **Hash `cyrb53` / `stableId` (34-47).** Uses `Math.imul`, `>>>`, and returns a number up to 2^53; ids come from
   `toString(36)` of it. In Kotlin use `Int` arithmetic with explicit wrap for the 32-bit steps and `Long` for the final
   combine; the string is UTF-16 code units, as Kotlin `Char` is. Ids are idempotency keys (event ids, task ids, change ids) so
   a wrong bit makes duplicate XP possible. Countermeasure: golden vectors for random and edge strings (empty, non-BMP, 1 KB).
   `inputHash` (1529) feeds `JSON.stringify(pick, Object.keys(pick).sort())`: an array replacer filters keys at every nesting
   depth, which drops any nested key that is not also a top-level key name. It is stored but never compared in `logic.js`
   (checked by search). Ruling needed (D12): treat it as opaque, or reproduce the quirk.
2. **Time zones and DST (64-135, plus `Intl` in `logic.js`).** The engine uses `Intl.DateTimeFormat('en-US', hourCycle h23)`
   `formatToParts`, then `zonedToEpoch` tries three offset candidates: a time in the spring gap resolves to the instant after
   the gap; an ambiguous fall-back time resolves to the first occurrence. `java.time.ZonedDateTime.ofLocal` has the same two
   rules, but this must be proved, not assumed. Countermeasures: sweeps of every 15 minutes across the DST weeks of 2026 and
   2027 for UTC, America/New_York, Europe/London, Australia/Lord_Howe (30-minute shift), Asia/Kolkata (+5:30), Asia/Kathmandu
   (+5:45), Pacific/Auckland, America/Sao_Paulo; also `localParts` at second resolution. tzdata can differ between the
   recording node ICU, the CI JVM and the phone: record the node ICU and tzdata versions in the golden file header, keep the
   corpus to dates inside 2026-2027, and run the corpus once on a device.
   `logic.js` additionally uses `Intl.DateTimeFormat(undefined, …)` for display (`fTime`, `fDay`, `fDate`, `fHm`) in the
   device locale: the words the screen shows. These must keep coming from the locale-aware platform formatter, and are
   compared as strings in the read-model goldens with the locale fixed (the existing goldens use `en-US`).
3. **Float arithmetic (energy and scoring, 804-1015).** `Math.pow(0.5, x)` (844), `Math.exp` at 906, 993, 1006, plus `**`.
   Placement picks the minimum score, so a one-unit-in-the-last-place difference can flip a tie and change the whole plan.
   V8 implements `exp` and `pow` from fdlibm; use `StrictMath` on the JVM (not `Math`, whose intrinsics may differ). Do not use
   `Math.fma`; compile with no fused-multiply-add; verify on a device because ART may differ from the HotSpot JVM. Keep the same
   operation order in every expression (no algebraic "cleanups"). `explainInstance` rounds with `Math.round(v*10)/10` (1448):
   values that land on .x5 differ if the float differs by an ulp, so the corpus must include score-boundary cases.
   `Math.round` ties toward +infinity in both JS and Kotlin `roundToLong`; never use `Math.rint` or banker's rounding.
4. **Sort stability and comparators.** JS `Array.prototype.sort` is stable; Kotlin `sortedWith` is stable too. The risk is
   comparators with ties that depend on input order (for example 321 and 1863 sort by tier and start only), so the input order
   must match: build every list in the same order. Default `.sort()` with no comparator is a string sort (208 days, 606 ids,
   1225 goal ids, 1531 keys): the days are single digits so they agree, but ids need UTF-16 code unit order, which is
   `String.compareTo` in Kotlin (not `Collator`, not locale).
5. **Object key order.** Objects keyed by string (`perKey`, `perReason`, `Object.keys(...).map(Number).sort`, `Object.values(goalAlloc)`,
   `Object.entries(d.factors)`) iterate integer-like keys first in ascending order, then string keys in insertion order. Use
   `LinkedHashMap` and replicate the integer-key rule where keys can be numeric (1420 sorts numerically, so it is safe, but
   the others must be checked in the spec). Output arrays are compared in order; output objects are compared as parsed JSON.
   `stateFingerprint` (1141) builds a string from `JSON.stringify(S.fair)` and sorted keys: used only inside the repair loop for
   cycle detection, so it need not be byte-equal if the Kotlin loop terminates the same way, but that must be proved by cases.
6. **Numbers.** Epochs are seconds, as doubles holding integers; page timers are milliseconds. `Date.UTC` with day overflow
   (`ymdAdd`) must be replaced by `LocalDate.plusDays`. `Number` parsing of strings, `+x` on `undefined` (NaN) and truthiness
   of `0` (for example `minutesOf`: a minute value of 0 means "use the plan") are rule-bearing; the spec must list them.
   JSON integer versus float: the page writes `900` and `900.5` alike; the Kotlin codec must keep the original number text for
   unknown fields.
7. **Nondeterministic fields.** `computeTimeMs` (1520, 1786) comes from `performance.now()`; exclude it. Everything else in
   `generateSchedule` and `rescheduleActivity` is a function of the input (checked for the ten scenarios).
8. **Module loading trap.** `engine.js` contains three IIFE modules that each assign `module.exports`, so in node
   `require('engine.js')` returns the last one (the store), not the engine. The recorder must load the file in a `vm` context
   with `self` set, as in the scratch check run for this audit; the existing tests load it in a browser.

### 4.3 How equivalence is proved

Layered, each layer a gate for its work package.

1. **Recorder, in node, no browser.** `tools/ld_record.js` loads `engine.js` in a `vm`, installs a pinned `TZ`, and writes
   JSONL cases `{fn, tz, input, output}` for pure engine functions. The engine is pure, so it needs no page. Fixture file under
   `tests/fixtures/life-director-engine-golden.jsonl` (compressed if large), read in place by the Kotlin test, one file for both
   sides, as the economy does with `GoldenFiles.kt`.
2. **Corpus.**
   - The ten built-in scenarios (`office`, `night`, `rotating`, `student`, `abundant`, `limited`, `manygoals`, `deadlines`,
     `misses`, `workchange`) at seeds 1 to 20, in each zone of the matrix, starting at several "now" values (including 30
     minutes before a DST change).
   - Function level: every exported function and the `_internal` ones the spec names, with edge inputs.
   - **Fuzz.** A seeded generator (its own RNG, not the engine's) builds random but valid inputs: random rules (rotations,
     overnight, validFrom/validTo), commitments, blackouts, goals from every archetype, quests of every kind, existing
     schedules with locked and history items, prefs. Then random triggers for `rescheduleActivity` (each trigger type) applied
     to the generated plan, and random completions and misses against the ledger. Commit a small corpus (for example 3,000
     cases, under 10 MB) for CI; generate a large one (100,000) on demand for a pre-cutover soak. Failing seeds are minimised
     by dropping inputs one at a time and kept as regression cases.
3. **Differential run.** The Kotlin JVM test replays each case and compares: integers, strings, booleans exact; arrays in
   order; objects as parsed JSON (key order ignored, but the spec marks fields whose iteration order is observable); numbers
   by equality of the double (no tolerance) except the explicitly named rounded display fields. Report the first differing
   path and the case seed.
4. **Invariant cross-check.** `validateSchedule` on the Kotlin output (once WP-LD4 is in) as a second oracle, independent of
   the recorded outputs.
5. **Host-level goldens (needs a browser).** For `Do.*`, `Today.*`, `tick` and the dial: record
   `{ST before, now, tz, host stub results, command, payload} -> {reply, ST after, host calls made}` by driving the real page
   under Playwright's clock, as `tests/fixtures/director-day.js` and the `director-*-golden.json` recorders already do.
   `LifeDirectorApp._debug()` exposes `ST`. The host stub (`Host.awardActivity`, `readQuests`, `activitiesSince` …) is recorded
   too, so Life Director is tested without the economy.
6. **Device pass.** Run the committed corpus once as an instrumented test on a phone, for tzdata and floating point.
7. **Mutation spot checks.** Flip an operator in the Kotlin port and confirm that the corpus fails; if it does not, the
   corpus lacks coverage there.

## 5. Work packages

Sizes: S under 3 days, M 3 to 8, L over 8 (of focused work). Packages marked "engine" wait for the engine spec only where
noted; each one starts by reading its part of that spec and recording the cases.

### WP-LD1. Recorder harness and the time and hash kernel (M, risk medium) - recommended first

**Status: done** (branch `logic/ld1-kernel`, NativeApi 114). Recorder `tools/gen_ld_kernel_golden.js` (the real page in Chromium,
node as a second runner; loader `tools/ld_engine_vm.js`, cases `tools/ld_kernel_cases.js`), 15,231 cases in
`tests/fixtures/ld-kernel-golden.json` (header records the versions), web test `tests/ld-kernel-golden.test.js`; Kotlin kernel in
`core/lifedirector` (`WallClock`, `ZoneNames`, `PageZone`, `CalendarMath`, `EcmaTime`, `PlanHorizon`, `SlotGrid`, `QuarterHours`,
`EngineHash`, `DirectorClock`) reproduces every case, with mutation checks; spec sections 0-2, 4 (time, LDT-0 to LDT-22) and 5
(hashing, LDH-1 to LDH-7) written. Findings: in an autumn fold the page takes the **second** occurrence at and east of Greenwich
(LD01; `java.time`'s default differs), `java.time` needed its own handling for case-insensitive and legacy zone names and offsets
past 18:00, and the tz data of the page's ICU and the JVM differ for Vancouver and Chisinau (spec 6.2). Everything in the scope list
below is covered, plus `createGrid`'s time fields, `dayEndAfter`, `weekKey` and `weekBounds`; `mulberry32` reuses the Daily Hand's
generator. Open: spec section 9 (OQ-1 to OQ-5), and the device pass.

Scope: `tools/ld_record.js` (vm loader, pinned zone, JSONL writer, canonical form, fixture header with ICU/tzdata version);
Kotlin: `Clock` interface (seconds and milliseconds), `cyrb53`, `stableId`, `mulberry32`, `localParts`, `tzOffsetSec`,
`zonedToEpoch`, `ymdAdd`, `ymdKey`, `parseYmd`, `daysBetween`, `buildHorizon`, `ceilTo15`; a `GoldenFiles`-style loader.
Tests: hash vectors (including non-BMP), DST sweep for the eight-zone matrix, round trips, an overnight rule across a DST
change. Needs from the owner: the zone list that matters (which zones the owner actually uses or travels to), and whether a
travel day (zone change mid-horizon, which today forces a full re-plan at `maybeRollover`) is in scope.
Does not need the engine spec: the code is unambiguous. It de-risks the largest hazard first and gives every later package its
recorder.

### WP-LD2. Availability: rules, sleep, preferences (M, risk medium-high; engine)

**Status: done** (branch `logic/ld2-availability`, NativeApi 119). Recorder `tools/gen_ld_availability_golden.js` (same method as LD1:
the engine text with its private helpers hooked, `tools/ld_engine_vm.js` `AVAILABILITY`, run in the built page in Chromium and in node;
cases `tools/ld_availability_cases.js`), 3,797 cases in `tests/fixtures/ld-availability-golden.json` (12 families: helpers, rules, the
engine's and the page's rule question, materialised intervals, preferences, sleep, paint, deriveAvailability by hand, the ten scenarios
in the 11 strict matrix zones, every 2026-2027 clock change, seeded fuzz), web test `tests/ld-availability-golden.test.js`; Kotlin in
`core/lifedirector` (`PageValues`, `AvailabilityRule`, `PageRuleCheck`, `PlanPreferences`, `SleepWindows`, `AvailabilityGrid`,
`Availability`) reproduces every case (`AvailabilityGoldenTest`, `AvailabilityMutationTest`; 21 source mutations each caught). Spec part 2
(`LIFE_DIRECTOR_ENGINE_SPEC.md` section 10: LDG-1 to 6, LDA-1 to 17, LDP-1 to 3, LDS-1 to 8; defects LD04 to LD14; index 8.2; OQ-6 to 9).
Findings: D3 measured (the page's copy differs only on stored shapes its own forms never write, LD06); a non-numeric rule time or a
non-text rotation anchor makes every plan throw (LD04, LD05; part 1's "NaN for an Invalid Date" corrected to a `RangeError`); a routine
stored as text joins instead of adding in the sleep lead (LD09); load `NONE` reads as moderate (LD12); rules made in the app never get the
default post-shift decompression because the form stores 0 (LD14). `tagMatches` is WP-LD3's and is not here. Open: OQ-6 (D3) to OQ-9.

Scope: `normalizeRule`, `ruleAppliesOn`, `materializeRule`, `normalizePreferences`, `defaultPreferences`, `deriveSleep`,
`deriveAvailability` (grid, slot codes, commutes, decompress, maintenance, meals), `paint`. Tests: scenario availability
grids (slot arrays) for the ten scenarios in the zone matrix; fuzzed rules; sleep conflicts with hard intervals. Decision
needed: D3 (one rule-applicability function for page and native).

### WP-LD3. Demand: tags, goals, quests, candidates (M, risk medium; engine)

Scope: `tagMatches`, `tagDepth`, `suggestTemplates`, `normalizeGoal`, `goalTemplates`, `expandGoalActivities`,
`normalizeQuest`, `findQuestCycles`, `compileCandidates`, `standaloneQuestTask`, `makeTask`, `freshIds`. The archetype and
tag tables (`ARCHETYPES`, `TAG_PROFILE`) are exported from the page as a JSON file (like ECONOMY WP2), never retyped; a test
fails if the export drifts. Tests: golden candidates per scenario; quest cycles; fuzz.

### WP-LD4. Placement core and generation (L, risk high; engine)

Scope: energy model, `buildState`, `prepTask`, `checkHard`, `scorePlacement`, `evalInBwc`, `evalAcross`, `commit`,
`removeInst`, snapshot/restore, `generateSchedule`, `runLocalRepair`, `fillPass`, rejection reasons, `explainInstance`,
`segments`, `capacityByDay`, `questCoverage`, `assembleOutput`, `validateSchedule`. Tests: full generation goldens (ten
scenarios x seeds x zones), fuzz, validateSchedule on every output. The float rules of section 4.2(3) apply. Decisions: D12
(`inputHash`), whether explanation strings are part of the contract (they are shown in sheets; recommended yes).

### WP-LD5. Rescheduling and hold placement (M to L, risk high; engine)

Scope: `rescheduleActivity` for every trigger (`MISSED`, `SKIPPED`, `MOVE`, `RESIDUAL`, `TIME_CHANGED`, `NEW_COMMITMENT`,
`BLOCK_HOURS`, `BLACKOUT_DAY`, `SICK`), `prepareStateWithHold`, `placeByRadius`, `dayEndAfter`, change record. Tests: for
each trigger, goldens on the scenario plans and fuzz sequences of triggers (a plan, then ten random changes), compared after
each step.

### WP-LD6. Ledger, completion, misses (M, risk medium; engine)

Scope: `emptyLedger`, `completionEventId`, `recordCompletion` (ratio, partial, rest tokens, residual task), `revokeEvent`,
`compactLedger`, `ledgerHas`, `reconcileQuestProgress`, `detectMisses`, `applyMissConsequence`, `canTransition`. Idempotency
keys are the thing that must not drift: a different event id means a second award. Tests: event streams (complete, repeat,
revoke, skip, miss, token earn and spend) with the ledger compared after every step; fuzz.

### WP-LD7. Life Director store codec (M, risk high)

Scope: read and write `lifemax_director_state_v1` with unknown-field and number-text preservation, `validate`, `migrate`,
`.bak` rotation, quarantine, read-back verification, backup text format. Uses the same preserving-JSON facility as the
economy save codec (ECONOMY_NATIVE_PLAN WP1); build that first. Tests: the saved-state fixtures (record several real states
from the page, including a migrated v0 one, a corrupt one and an oversized one), round trip byte-for-byte on fields the page
owns, page loads native-written state and the reverse (browser test). Decisions: where the key physically lives once native
owns it (the page's `localStorage` is not reachable from native without the web view), and the backup format's future.

### WP-LD8. Read models from state (M, risk low-medium)

Scope: native computation of what the projection carries today: `horizonDays`, `unplannedSegments`, `dayBlocks`, `liveTimeline`,
`nowState`, `weekModel`, `goalCards`, `questsModel`, settings and time models, `moveTimes`, `disruptParts`, `shifts`,
`workRules`, dial stops and handles. Page words (the strings) are exported as data, not retyped. Tests: golden pairs
`(ST, now, tz) -> projection section` recorded from the page; this is the first package that can run in shadow mode (4 below),
because it only reads. Split into a (day, now, week) and b (goals, quests, settings, time, forms).

### WP-LD9. Host commands and tick (L, risk high)

Scope: `Do.*` and `Today.*` as pure `(state, now, host, payload) -> (state', reply, hostCalls)`; `finishInstance`, `logToday`,
`repairEarlyDone`, `syncHost`, `processMisses`, `maybeRollover`, `applyOutput` including the refusal path, `afterEvent`, undo
of a skip, the dial's `applyDial`, `regroup`. Tests: the host-level goldens of 4.3(5), including every refusal message and
every repeat ("Already recorded.", "Already skipped.", "Already added"). Decisions: D2, D4, D6, D8, D10, D11.

### WP-LD10. Timers and background work (S to M, risk medium)

Scope: `ST.ui.focus` state machine; auto finish; what keeps the timer true when the app is closed (a foreground
notification or an alarm), the 5-minute tick and the day rollover as a background job. Decisions: D4, D7, and whether the
timer should use a monotonic clock.

### WP-LD11. Adapters to the native economy and quests (M, risk medium; depends on economy WP5 and the quests port)

Scope: replace `LifeMaxHost`: `xpFor`, `awardActivity`, `completeQuest` (LD-2 to LD-4, already in `core/economy`), plus
`readQuests`, `readPlanSessions`, `milestones`, `retiredUntimedQuest`, `activitiesSince`, `activityExists`, meal lookups. Needs
the quest board native (QUESTS_SPEC) and the Plan day content. Decision: D1 (which time zone dates an award).

### WP-LD12. Shadow mode (L, risk medium)

Scope and plan in 5.1.

### WP-LD13. Cutover (L, risk high)

Prerequisites and plan in 5.2.

Order: LD1 → (LD2, LD3 in parallel) → LD4 → LD5 → LD6 → LD7 (can start any time after the economy save codec) → LD8 (can start
after LD1 and run in shadow mode early) → LD9 → LD10 → LD11 → LD12 → LD13. Ask the owner before LD4 to confirm that the
engine spec has landed and been reviewed; LD1 and LD8 do not depend on it.

### 5.1 Shadow mode

Native computes; the page still writes. No behaviour change on screen.

1. **A, read shadow (after LD8).** The projection gains a debug-only `state` (a copy of `ST`, `now`, `tz`). Native recomputes
   the sections it can, diffs them against the page's own projection field by field, and logs mismatches with the state that
   caused them to a device file. Only the web view build with the debug flag sends the state.
2. **B, engine shadow (after LD4 to LD6).** After each page `regenerate` and `reschedule`, send the engine input and the page's
   output; native runs the same input and diffs the output (ignoring `computeTimeMs`). Log the input of every divergence.
3. **C, command shadow (after LD9).** For each page command, send `(ST before, payload, host replies)` and the page's
   `(reply, ST after)`; native replays and diffs. Host calls (awards, quest completions) are compared as a list.
4. **Exit criteria:** 30 consecutive days of the owner's own use with zero divergences, covering at least one weekly rollover,
   one DST change or zone change (or a simulated one in the corpus if none occurs in the window), a skip with take-back, a
   missed block with regroup, and a timer that ran in the background. Each divergence is either fixed in native or ruled a
   page defect (and then fixed on the page first, with a new golden, so both sides agree).

### 5.2 Cutover prerequisites

All of these, before native is the only writer:

1. Economy save codec in production (`ECONOMY_NATIVE_PLAN.md` WP1), because completing a session writes an activity (and rested
   XP, the daily cap, the execution bonus) into the main save, and a quest bounty toggles a quest.
2. The Life Director store codec (WP-LD7) and a decision on where the data lives; one migration that moves the key and keeps
   the `.bak`.
3. Native quest board and Plan day content (LD11), because `hostQuests` is an input to every plan.
4. All page consumers re-pointed: Money `shifts`/`workRules`, voice, the Journal coach, the widget, the Overview glance,
   `33-android.js:45`, device backup `LMA_EXTRA_KEYS`.
5. Shadow mode exit criteria met, and the corpus run once on the owner's phone.
6. A rollback: the page's Life Director stays in the build for one release, read-only, with a switch that makes it the
   writer again, and native writes the same store so the page can load it.
7. Owner rulings on every item in section 6 marked "before cutover".

## 6. Defects and accidents that need an owner ruling

Each is behaviour the port would otherwise copy faithfully. "Port as is" is the default if not ruled.

| ID | Where | What | Question |
|---|---|---|---|
| D1 | `engine.js:2513-2537` (host `awardActivity`, `xpFor`) versus `logic.js:1216` (`xpOf`) | The award dates the session and the execution-bonus cap by `toLocaleDateString('en-CA')`, which uses the device's zone. The preview and the schedule use `ST.availability.timezone`. If the two differ (travel, or a manually set zone) a session near midnight is previewed on one day and paid on another, and the 15 XP daily bonus cap is counted on the device's day. | Which zone dates an award? Recommended: the plan's zone, as the preview does. |
| D2 | `logic.js:318-322`, `3421-3430` | When the economy save fails (`HOST_SAVE_FAILED`, `HOST_READ_ONLY`), the session stays recorded as done in the Life Director ledger and history, with no activity and no retry; only the toast says XP was not saved. Re-doing it is refused as a duplicate. | Roll the completion back, or keep it and retry the award later? |
| D3 | `logic.js:578-588` (`ruleOn`) versus `engine.js:221-229` (`ruleAppliesOn`) | The same rule question is answered by two functions. They differ at the edges (the page's copy does not normalise `days`, and treats a missing list as every day; the engine drops invalid days). `shifts()` (Money) and the dial handles use the page's copy; the plan uses the engine's. | One function in native, taken from the engine? Does Money's income change if the two differ today? |
| D4 | `logic.js:2900-2907` | `tick` (host sync, misses, rollover) runs only while the Life Director view is mounted and active, every 5 minutes or when the app returns to the foreground. A player who does not open the tab gets no miss handling, rollover or sync until they do, and then at most 12 misses per tick. | Should these run in the background natively (a job at least daily), or only on open as today? |
| D5 | `engine.js:8, 2240` | Three modules in one file each assign `module.exports`; `require` returns the store, not the engine. Tooling trap, not a player bug. | Split the file when the page build is touched? (No action needed for the port.) |
| D6 | `logic.js:2413` (`sleepSave`), `2441` (`ruleSave`) | Equal bed and wake times give 8 hours of sleep (`|| 480`); equal start and end of a commitment give a 24-hour rule (`|| 1440`). Probably intended for sleep, doubtful for a commitment. | Keep both? |
| D7 | `logic.js:1929, 1952-1975, 3491-3552` | Timers use the wall clock in milliseconds (a manual clock change corrupts the elapsed time) and auto finish runs only while the page is open, then logs the planned minutes with a start of `f.start`. A timer left running overnight finishes when the app is next opened with a session that started hours earlier. | Monotonic clock? Finish in the background? Cap or ask when the timer is far past its end? |
| D8 | `logic.js:3402, 3440-3470` | The take-back for a Skip is held in memory only (lost on restart), lasts 2 minutes, and is valid only while the whole plan, serialised, is unchanged: it depends on JSON key order. `reschedule` also changes `fixedCommitments` and `blackouts`, which the take-back does not restore. | Persist the take-back? Use a plan version counter in native? |
| D9 | `logic.js:73-75` | `persist` drops history older than 21 days from the schedule on every write; the ledger keeps events, but any screen showing older sessions cannot. | Is a 21-day window intended as the limit of what the Week and Today history can show? |
| D10 | `logic.js:2506` (`goalAdd`), `2436` (`ruleSave`), `2474` (`activityAtAdd`) | Ids are formed from the operation id (`g-` plus 12 characters, `r-` plus all, `manual-` plus all). The 12-character cut gives 44 bits for a UUID but only a few for a counter or a short id. | Native must mint UUIDs; confirm that no screen builds ids from a counter. |
| D11 | `logic.js:3408` (`minutesOf`) | A completion of 0 minutes means "as planned" (zero is falsy); negative or tiny values become 1; the maximum is 480. | Is "0 means planned" intended, or should 0 be refused? |
| D12 | `engine.js:1529-1532` | `inputHash` uses an array replacer, which silently drops nested keys not named at the top level, so the hash does not cover most of the input. It is stored but nowhere compared. | Drop it from the contract, or keep byte-equal? |
| D13 | `logic.js:84-99` | `boot` runs `repairEarlyDone` (a one-time 1.4.1 repair, flagged in `ST.fixes`) before anything else. | Can it be dropped natively (it only matters for stores written before 1.4.1)? |
| D14 | `logic.js:2766, 1952` | The page runs a 1 s interval and a 15 s interval for the whole life of the page, even with Life Director not shown (`focusTick` returns early only without a timer). | Page-only; native has no equivalent need. Informational. |
| D15 | `logic.js:3500-3503` | `Today.timer start` refuses when another timer is running on a session still on the plan, but silently overwrites one whose session left the plan (`plannedInst(f.id)` false). | Intended clean-up, or should it say so? |

Items to rule before cutover: D1, D2, D4, D7, D8, D12. The rest can be ruled as the package that touches them starts.

## 7. Recommended first work package

WP-LD1: the recording harness and the time and hash kernel. It is the only package that does not depend on the engine spec,
it proves (or disproves) that `java.time` reproduces the page's `Intl`-based zone arithmetic before anything is built on it,
and the recorder it delivers is what every later package uses to produce golden cases. It also needs one owner answer:
which zones to put in the test matrix. Suggested second step, in parallel because it only reads: WP-LD8a (day blocks, now
state, week model), which can run in shadow mode as soon as it exists and so starts producing divergence data early.
