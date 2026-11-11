# Life Director host commands and the tick: specification (WP-LD9)

Status: **complete for the host commands** (WP-LD9). It describes Life Director's interface controller where it changes the plan, the
ledger and the store (`logic.js`, the `Do` and `Today` commands, completion, the tick, the dial's `applyDial`, the plan refusal and the
one-time early-done repair) as it behaves at branch `codex/director-dial-controls`, commit `5669a42`, and the native code that reproduces it
(`android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector/host`). The page is the authority: this document describes it and
changes nothing. Nothing here is wired to a screen; the page is still the only writer of Life Director's state.

Paths: `logic.js` and `engine.js` are `lifemax_modular_v73/src/features/life-director/logic.js` and `engine.js`; Kotlin files are in
`core/lifedirector/host` unless named otherwise. The engine functions the commands call are the earlier packages' (engine spec parts 1 to 6,
`LIFE_DIRECTOR_LEDGER_SPEC.md`, `LIFE_DIRECTOR_STORE_SPEC.md`) and are used unchanged.

Contents

0. How to read this
1. Boundary
2. The model: state, the pure entry point, the host, errors, words (HC-1 to HC-9)
3. Today (HC-10 to HC-19, HC-30 to HC-35)
4. Completion (HC-20 to HC-27)
5. The plan refusal and the early-done repair (HC-40 to HC-42)
6. Do (HC-50 to HC-66)
7. The tick: host sync, misses, rollover (HC-70 to HC-79)
8. The dial (HC-80 to HC-82)
9. Fuzz (HC-90)
10. Defects (LH01 to LH14)
11. Owner decisions D2, D4, D6, D8, D10, D11 as the goldens show them
12. Golden cases: the index, and the mutation checks
13. Swapping the host for the real adapters (WP-LD11)
14. Open questions

---

## 0. How to read this

Rule IDs are `HC-n`; defects are `LHnn` (the engine spec's are `LDnn`, the ledger's `LGnn`, the store's `LSnn`). Each rule is:

- **Executed**: the golden file `lifemax_modular_v73/tests/fixtures/ld-host-golden.json.gz` holds cases recorded from the real page that
  show it (families or case ids are given), and `HostGoldenTest` replays every step natively.
- **Read**: derived from reading the code only (a path the cases cannot reach, said why).

Every rule is **Executed** unless marked **Read**. JavaScript's `undefined` and JSON's `null` differ throughout; "truthy" is JavaScript's
truthiness. `String(v)`, `Number(v)` and `Math.round` are JavaScript's. A command's answer is `{ok, changed, message}` unless said otherwise:
`ok` only when what it did (or found already done) is saved.

## 1. Boundary

| `logic.js` | What | Native | Rules |
|---|---|---|---|
| 12-14, 29 `nowEpoch`, `tz`, `say` | clock, zone, notice | `HostPage.now`, `tz`, `say` | HC-2, HC-6 |
| 71-83 `persist` | save with trimming | `HostPage.persist` | HC-7 |
| 84-99 `boot` | load, repair, banners | `HostPage.boot` | HC-1 |
| 102-119 `hostQuests`, `completionHistory`, `buildInput` | the engine's input | `HostPage` | HC-8 |
| 120-131 `applyOutput` | publish or refuse a plan | `HostPage.applyOutput` | HC-40, HC-41 |
| 132-168 `regenerate`, `autoReplan`, `reschedule`, `summarize` | re-plan and repair | `HostPage` | HC-9 |
| 171-240 `syncHost` | LifeMax log and quests into Life Director | `HostTick.syncHost` | HC-70 to HC-74 |
| 242-268 `processMisses` | misses | `HostTick.processMisses` | HC-75 to HC-77 |
| 269-275 `maybeRollover` | light mode end, weekly re-plan | `HostTick.maybeRollover` | HC-78 |
| 277-305 `unsaved`, `xpWords`, `afterEvent` | quest and goal progress | `HostCompletion.afterEvent` | HC-23 |
| 307-332 `finishInstance` | a completion | `HostCompletion.finish` | HC-20 to HC-26 |
| 338-349 `doable`, `showAsDoneNow` | done "now-ish" | `HostCompletion.doable` | HC-12 |
| 351-371 `repairEarlyDone` | one-time repair | `HostCompletion.repairEarlyDone` | HC-42 |
| 375-391 `logToday` | log for today | `HostCompletion.logToday` | HC-27 |
| 602, 1470 `handleLabel`, `fHm`; 17 `fDay` | words | `HostWords` | HC-6 |
| 1883-1900 `applyDial` | the dial's change | `TimeCommands.applyDial` | HC-80 to HC-82 |
| 2215-2229 `disruptParts` (options only) | Something came up's choices | `HostWords.DISRUPT_*` | HC-30 |
| 2325-2525 `Do` (`nativeRun` 3691) | settings, goals, My time, backup | `DoCommands`, `TimeCommands` | HC-50 to HC-66 |
| 2900-2907 `tick` | the tick's body | `HostTick.tick` | HC-79 |
| 3395-3605 `Today` (`nativeTodayRun` 3606) | Today's writes | `TodayCommands` | HC-10 to HC-35 |

Not here: `Today.timer` and the focus state machine (WP-LD10); the read models (`dayBlocks`, `dialStops`, `nativeChange`; WP-LD8); every
DOM, animation and sheet function (`render`, `queueBuild`, `closeSheet`); the voice and coach seams (callers of these functions).

## 2. The model

- **HC-1** (family `boot`; every case's first step) `boot` (`logic.js:84-99`): the store's `load({playerId: Host.playerId(), timezone:
  deviceTz(), nowEpoch})`; a ledger (`emptyLedger(ST.playerId)`), `questOverlay` `{}` and `hostSync` `{cursor: null, seen: {}}` where
  missing (falsy); `repairEarlyDone()` and, when it ran (HC-42), a save; then the banner: storage unavailable (`MEMORY_ONLY`, red, `This
  browser blocks storage here, ...`), recovered from `.bak` (`Your last save was unreadable, ... kept as <quarantine key>.`), migrated
  (`Life Director data upgraded from version <n>.`); none of these leaves the repair's banner; when LifeMax is not connected and `ST.ui.lab`
  is `undefined`, `ST.ui.lab = true`; the tab is `setup` before set-up, else `ST.ui.tab || 'today'`. Host calls in order: `playerId`,
  `store.load`, (`store.save`), `connected`. Cases `boot/new`, `blocked`, `migrated`, `unreadable`, `recovered`, `invalid-main`,
  `offline-lab`.
- **HC-2** The commands are pure functions `HostCommands.run(state, nowMs, host, command) -> HostOutcome(state', reply, calls)`. `state`
  (`HostState`) is the page's memory between commands: `ST` and the controller's own variables a command reads or leaves: `ui.banner`,
  `ui.notice` (`{id, text, at}`), `ui.tab`, `ui.dayIdx`, `ui.pendingDial`, the Skip's take-back `{id, text, at, before: {schedule, ledger},
  left}`, the last take-back's `op`, the last save's outcome `{ok, error}` and the last refused completion's reason. The clock is
  `Date.now()` in milliseconds; `nowEpoch()` is its whole seconds. Inside, the commands change a mutable copy in place, as the page does
  (`HostPage`, over `JsTree`'s mutable values; the engine's ported functions take and give `JsonValue`), so references the page keeps into
  `ST` behave the same. Presentation state (`ui.animate`, `ui.prevPlan`, the build queue, sheets) is not modelled.
- **HC-3** A reply is `HostReply`: a command's `CommandReply` (`ok`, `message`, and where the page gives them `changed`, `undo`, `said`,
  `text`, `moved`), a tick function's flag, the ledger's object for `finishInstance` and `logToday`, `{duplicate: true}`, or a throw (HC-5).
  The page's three answer helpers (`logic.js:2325, 2328, 3409-3411`): `saved(m)` is `{ok: true, message: m}` after a good save, else
  `{ok: false, message: 'Not saved: ' + error}`; `refuse(m)` is `{ok: false, changed: false, message: m}`; `recorded(m)` saves again and is
  `{ok: <that save>, changed: false, message: m}`; `applied()` is `saved('')` with `changed: true`. Refusals in Today and in the goal and
  settings commands carry no `changed`.
- **HC-4** The host (`HostEffects`): every call the page makes to `LifeMaxHost` (`connected`, `toast`, `playerId`, `readQuests`,
  `readPlanSessions`, `awardActivity`, `completeQuest`, `activitiesSince`, `activityExists`, `milestones`, `retiredUntimedQuest` and whether
  it exists), to the store (`load`, `save`, `clear`, `exportJson`, `importJson`), `crypto.randomUUID` and the device zone. Answers are
  typed values; no call may throw. `RecordingHost` notes every call with its arguments and answer: the outcome's third part, compared call
  for call with the page's (`HostReplay`). The page calls the host in a fixed order (for example `buildInput` reads quests before plan
  sessions; `say` toasts after the notice), and native keeps it.
- **HC-5** Where the page throws, native reports the same error name (`TypeError`, `RangeError`, `Error`) and the page's message, and the
  state is what the page would hold at that point (changes made before the throw stay). One case reaches it: `rollover/zone-change@London`
  (LH01). `HostCommands.run` itself never throws.
- **HC-6** Words (`HostWords`): `say(m)` sets the notice (`id` one more than the last, `at` the clock in ms) and calls `Host.toast(m)` (a
  false answer makes the page draw its own toast; nothing else changes). `fDay` writes `Wed, Nov 4` (en-US, the plan's zone); `fHm(m)`
  writes `10:00 PM` (UTC, minutes after midnight; the space is U+0020 in the recording browser). `handleLabel` is `Bedtime`/`Wake up` for
  sleep, else the rule's title (or its kind's label, or `Commitment`) and ` starts`/` ends`. The option tables are the page's (`INTENSITY`,
  `HORIZON`, `MISSES`, `PREF_RANGES`, `GRACE`, `ROUTINES`, `MEAL_RANGE`, `RULE_KINDS`, `RULE_LOADS`, `KIND_LABEL`, `CUSTOM_TAGS`,
  `GOAL_LEVELS`, `disruptParts`' options, `DISRUPT_ID`, `REMOVE_WHY`, `XP_WORDS`).
- **HC-7** `persist` (`logic.js:71-83`): history whose `endEpoch` is not after now minus 21 days is dropped from the schedule (the ledger
  keeps everything), the change log keeps its last 60 entries, `updatedEpoch` is now, then `store.save(ST)`; its outcome is the last save; a
  refusal sets the red banner `Not saved: <error>`, a good save clears a red banner that starts `Not saved`. Every command's save is a
  `store.save` call in the outcome (cases `done/not-saved@*`, `dial/not-saved*`, `time/setup-store-fails`, `backup/store-fails`).
- **HC-8** `buildInput(now)` (`logic.js:102-119`): the engine's input: the zone, `now` as the current time and horizon start, the
  normalised horizon days, the availability with the zone, `fixedCommitments || []`, the goals, `activeQuests` = `Host.readQuests(now,
  zone, questOverlay, ledger.revoked)` then `Host.readPlanSessions(now, zone, days)`, the existing schedule (planned then history), the
  completion history (ledger events in order, not revoked, `completionRatio >= 0.75`, as `{templateId, spacingKey, startEpoch:
  actualStartEpoch}`), the stored preferences and `{checkin: energyCheckin}`. A null ledger throws reading `revoked` (LH01).
- **HC-9** `regenerate(reason, quiet)` (`logic.js:132-139`): `generateSchedule(buildInput(now))`, applied with the label `Re-planned
  (<reason>)` (HC-40); when taken, `lastGeneratedWeekKey` is the Monday of now's local week; saved; unless quiet and when taken, `Plan
  updated · <n> activities` (planned sessions). `autoReplan` re-plans quietly once set up, else only saves. `reschedule(trigger)`
  (`logic.js:149-158`): `rescheduleActivity`; a refused move is said (its message) and answers null; a refused plan is saved and answers
  null; otherwise the plan is applied with the trigger reason, `fixedCommitments` and (when given) the blackouts are the repair's, and the
  change log gains `{at, label, churn, actions: <count>}`. `summarize`: `n excused (no penalty)`, `n re-placed`, `n dropped for this week`,
  `n shortened`, joined by ` · `, else `Nothing else needed to move`.

## 3. Today

`nativeTodayRun(name, payload)` boots first when there is no state, answers `Life Director cannot do that.` for a name `Today` lacks, and
gives the command its payload (an object, else `{}`). Sessions are found by `String(id)` among planned sessions (`plannedInst`); a resolved
session by its id as given among history (`resolved`, LH05).

- **HC-10** (`done/*`, `fuzz`) `done({id, minutes})`: no planned session: `recorded('Already recorded.')` when the history holds it with a
  state containing `COMPLETED`, else `That is no longer on the plan.`; a planned session not `PLANNED` is gone; HC-12; then
  `finishInstance(inst, minutesOf(...), {quietRefusal: true})` (HC-20); a refusal answers HC-14; when the running timer was this session,
  `ST.ui.focus = null` and a second save; `saved('')`.
- **HC-11** (`done/minutes-*`) `minutesOf(inst, m)`: `Math.round(Number(m))`, or the plan's `durationSlots * 15` when that is 0 or NaN
  (D11: 0 and anything under 0.5 mean "as planned"), kept to 1..480. `true` is 1 minute, `'45'` 45, `-5` 1, `1000` 480.
- **HC-12** (`done/too-early@*`, `six-hours-ahead@*`, `boundary@*`) `doable`: a session may be done from 6 hours before its start (`start - now <=
  21600`), or any time on its own local day; otherwise `You can mark it done on the day, or from 6 hours before.`
- **HC-13** (`done/after-skip@*`) A session resolved otherwise (skipped) is gone.
- **HC-14** (`done/*`, `sync/undone-in-log@*`) A refused completion: `DUPLICATE_EVENT_ID` answers `recorded('Already recorded.')`; any
  other reason `Cannot complete this block (<reason>).` (**Read**: a planned session is always completable; the page's own toast is
  suppressed by `quietRefusal`).
- **HC-15** (`log/*`) `logToday({id, minutes, op})`: gone without a planned session; `Nothing was logged.` without `op`; HC-27 with
  `String(op)`; its duplicate answers `recorded('Already recorded.')`; `saved('')`.
- **HC-16** (`skip/*`) `skip({id})`: without a planned session, a history entry with a state containing `SKIPPED` answers
  `recorded('Already skipped.')` plus `undo` (the take-back's words) when the take-back is for this id and the save went through; else gone. A
  running timer on it: `Its timer is running. Stop the timer first.` Otherwise: the plan and ledger are copied (`JSON.parse`), the skip's
  consequence (`applyMissConsequence(..., 'SKIP')`, LG08) is recorded, `reschedule({type: 'SKIPPED', instanceId: id})`, saved, and said:
  `Skipped`, ` · used a rest token` when the consequence spent one, ` · <summary>` when repaired; the take-back is `{id: String(id), text,
  at: Date.now(), before, left: JSON.stringify(ST.schedule)}`; the answer is `saved('')` with `changed: true, undo: text`.
- **HC-17** (`skip/*`) `unskip({id, op})`: `op` equal to the last take-back's `op` answers `recorded('Already back on the plan.')`; no
  take-back for this id: `recorded('Already back on the plan.')` while it is planned, else `That can no longer be taken back.`; more than
  120,000 ms after the Skip, or a plan whose JSON text differs from `left`: `The plan has changed since, so that cannot be taken back.` (D8);
  otherwise plan and ledger are restored, the take-back is spent (`op` kept, or null), the change log gains `Skip taken back`, saved, said
  `Back on the plan`, `applied()`. Cases `skip-undo`, `too-late` (121 s), `just-in-time` (120 s), `plan-changed`, `other-id`.
- **HC-18** (`pin/*`) `pin({id, on})`: gone without a planned session; `isUserLocked === !!on` is `recorded('')`; else set, an explanation
  gets the pinned summary (or keeps its own when unpinned), saved, said `Pinned` or `Unpinned`, `saved('')`.
- **HC-19** (`pin/move*`) `moveTo({id, start})`: gone; `Number(start)` not an integer: `That time is not on the plan.`; the same start of a
  pinned session: `recorded('')`; `reschedule({type: 'MOVE', ...})` refused: `It does not fit there. Nothing was moved.`; else saved, said
  `Moved and pinned · <summary>`, `saved('')`.
- **HC-30** (`disrupt/*`) `disrupt({kind, op, ...})`: a kind that is not one of `work`, `block`, `day`, `sick`: `Life Director cannot do
  that.`; no `op`: `Nothing was added.`; the id is `c-`, `b-`, `d-` or `s-` plus `op`, and a commitment or blackout with that id (as text)
  answers `recorded('Already added')`; `block` and `sick` need one of the offered hours (1, 2, 3, 4, 6) or days (1, 2, 3), compared as text,
  else `That is not one of the choices.`; `work` needs a `YYYY-MM-DD` date and whole minutes of the day, else `Enter a date, start and end`,
  and ends the next day when the end is not after the start (`zonedToEpoch`, so a clock change shifts it: `dst-shift-*`); `block` is now to
  now + hours, `day` now to the next local midnight, `sick` now to now + days. Then HC-31.
- **HC-31** (`disrupt/*`) The repair: refused, the answer is `{ok: false, said: true, message}` with the notice said by the refusal, else the
  banner's words, else `Nothing was changed.` (**Read**: no case's repair refuses); repaired: saved, said `<summary>` (`Rest up · ` first
  when sick), `applied()`.
- **HC-32** (`disrupt/energy@*`) `energy({level})`: `LOW`, `NORMAL` or `HIGH` (as text), else `That is not one of the choices.`;
  `energyCheckin = {level, epoch: now}`, a quiet re-plan, `Energy noted`, `applied()`.
- **HC-33** (`replan/*`) `replan()`: a red banner goes; `regenerate('requested')` (said); a refused plan answers with its banner
  (**Read**: no case's plan is refused).
- **HC-34** (`regroup/*`, `misses/regroup@*`) `regroup({v, op})`: `v` other than `pause`, `light`, `go`: `Life Director cannot do that.`;
  `pause` without `op`: `Nothing was changed.`; with no regroup banner showing: `recorded` (`Already added` for `pause`) when it already
  landed (a blackout `p-<op>`; light mode with time left; no misses in a row), else `That question is no longer showing.`; with it: `pause`
  repairs with a 48-hour blackout `Paused` (a refused repair still counts, LH07), `light` remembers the intensity, sets `LIGHT` for 7 days
  and re-plans; every answer sets `consecutiveMisses` to 0, drops the banner, saves, `applied()`.
- **HC-35** (`regroup/dismiss@*`, `refusals@*`) `dismissBanner({text})`: only the banner with exactly those words (`String(text || '')`);
  otherwise `{ok: true, changed: false}`; the regroup question answers `Choose one of the answers.`; nothing is saved.

## 4. Completion

- **HC-20** (`done/*`, `done/finish-direct@*`, `sync/*`) `finishInstance(inst, minutes, opt)` (`logic.js:307-332`): the start is
  `opt.startEpoch` when truthy, else the earlier of the plan's start and `minutes` before now (now when that is 0 or NaN), and at most 12
  hours before the plan's start; `recordCompletion` with the preferences' partial ratio. The last refusal is remembered (HC-14); a refusal is
  said (`Already recorded.` or `Cannot complete this block (<reason>).`) unless silent or quiet. Then the ledger, the award (HC-21), the
  session leaves the plan for the history (`lifecycleState`, `completionEventId`, `hostActivityId`, `actualMinutes`, `resolvedAtEpoch`),
  HC-23, HC-22, HC-25, the save, and unless silent the notice: `<title> done` (or ` partly done`), the XP words, the notes, joined by ` · `.
- **HC-21** (`done/award-*`) The award unless `opt.awardXp === false`: `Host.awardActivity(event, {title})`; awarded: `+<xp> XP` and its
  activity id kept; `HOST_NOT_CONNECTED`: `recorded (XP needs LifeMax)`; `HOST_READ_ONLY` and `HOST_SAVE_FAILED`: their words, and no quest
  bounty; `ALREADY_IN_LIFEMAX`: no words, no id. The completion stays recorded whatever the award says (D2).
- **HC-22** (`done/partial@*`) A partial completion's residual task is re-planned (`reschedule({type: 'RESIDUAL', ...})`) and noted `Rest
  of it re-planned` when repaired.
- **HC-23** (`done/quest*`, `log/*`, `sync/*`) `afterEvent(event, inst, bounty)` (`logic.js:284-305`): quests are read again
  (`hostQuests(now)`), `reconcileQuestProgress` with the day key of the event's start in the plan's zone; the goals become its goals; for
  each quest it moved that LifeMax knows (`hostRef`), the overlay of the quest's key remembers the event once; requirements met with a quest
  id and a bounty allowed: `Host.completeQuest(hostRef)` and, when completed, `Quest complete: <name> +<xp> XP`; not met: `<title> <count>/<
  target>` of the first requirement; the plan keeps the pruned sessions without the released ones.
- **HC-24** (`done/focus-cleared@*`, `skip/running@*`) The running timer's session: cleared by done (with a second save), refused by skip.
- **HC-25** (`done/token-earned@*`) A rest token earned is noted `Rest token earned`.
- **HC-26** (`done/award-*`, `sync/*`) The history entry's `hostActivityId` is the award's, the host log's (sync), or null.
- **HC-27** (`log/*`) `logToday(inst, minutes, op)` (`logic.js:375-391`): an ad-hoc completion `ld-today:<op>` (a new id when none) for
  now minus `minutes`; a duplicate with an `op` answers `{duplicate: true}`; another refusal is said `Could not log it (<reason>).`
  (**Read**: unreachable); the award; a history entry `adhoc-<eventId>` shown as done now (`showAsDoneNow`: ending now, at least 15 minutes
  long) with the explanation `Logged for today. The session planned for <fDay> is still on your plan.`; HC-23 with the award's bounty
  rule; saved; said `<title> logged for today · <xp words> · <notes> · still planned for <fDay>`. The planned session stays.

## 5. The plan refusal and the early-done repair

- **HC-40** (`output/refused@*`) `applyOutput(out, label)`: an output with `status: 'INVARIANT_VIOLATION'` is refused: the red banner
  `Life Director refused to publish a plan that broke a safety rule (<codes joined by ', '>). Your previous plan is kept. Please report this
  from Settings → Diagnostics.`, `lastRejected = {at, violations: <first 10>}`, false; the plan is unchanged.
- **HC-41** (`output/*`) Any other output becomes `ST.schedule`; a truthy label adds `{at, label}` to the change log; true.
- **HC-42** (`early/*`) `repairEarlyDone` (`logic.js:351-371`), once (`fixes.earlyDone`; false when set): sets the flag (keeping other
  fixes); without a plan, true. Each history entry completed (state containing `COMPLETED`, not ad hoc, with a resolution time) more than 6
  hours before its plan and on an earlier local day is shown as done when resolved (`showAsDoneNow` with its actual minutes, or the plan's),
  remembers `movedFromEpoch`, and when its planned time is still ahead and no planned session overlaps it, goes back on the plan as
  `<id>-r` (the next revision, completion fields null). Any repaired: the warning banner `<n> session(s) was/were marked done before
  its/their day. They’re back on the plan, ...`. Runs at boot (HC-1).

## 6. Do

`nativeRun(name, payload)` as Today's (HC-10's preamble). Goal and settings refusals carry no `changed`; My time's use `refuse`.

- **HC-50** (`goals/priority@*`) `goalPriority({id, value})`: the goal by `String(id)` (`That goal is gone.`); `Number(value)` one of 1 to 5
  (`That is not a priority.`); set, quiet re-plan, `saved('')`.
- **HC-51** (`goals/pause@*`) `goalPause({id, paused})`: the wanted state is `paused` when boolean, else the toggle; already so:
  `{ok: true, message: ''}` (nothing saved); else `PAUSED`/`ACTIVE`, quiet re-plan.
- **HC-52** (`goals/remove@*`) `goalRemove({id})` and `clarify({id, arch})` (`That is not a kind of goal.` for an unknown archetype).
- **HC-53** (`goals/add*`) `goalAdd(form)`: an archetype or `CUSTOM` (`That is not a kind of goal.`); a trimmed name (`Give the goal a
  name`); priority 1 to 5 (`Number(priority) || 3`), a level of `GOAL_LEVELS` (`|| 'NOVICE'`), and a custom goal's tag of `CUSTOM_TAGS`
  (`That is not one of the choices.`); `op` (`Nothing was added.`); the id `g-` plus the first 12 characters of `op` (D10), and a goal with
  it answers `recorded('Already added')`; a custom goal's template (minutes `|| 30`, per week `|| 3`, efforts `|| 2`); a language's subject
  (trimmed, or null); a measurable target when the archetype has a volume template, units and rate are positive and the deadline is a date
  (its 23:59 local); then a quiet re-plan once set up (else a save), `Goal added`, `applied()`.
- **HC-56** (`prefs/pref@*`, `range@*`) `pref({key, value})`: the key's options compared as text (`That is not a setting.`); stored as a
  number when it reads as one; an intensity ends light mode (`prefsLightUntil = null`); `replanOrSave`. `prefRange`: the ranges of
  `PREF_RANGES` and the grace, on the step from the lowest (`That is not a setting.`).
- **HC-57** (`prefs/rest-day@*`) `restDay({day, on})`: an integer 0 to 6 (`That is not a day.`); already so: `{ok: true, message: ''}`.
- **HC-58** (`prefs/routine@*`) `routine({key, value})`: a routine or meal key and a finite number (`refuse('That is not a setting.')`),
  rounded and kept to the stepper; the same value `recorded('')`; `meals({on})` likewise; `autoReplan('routines changed')`.
- **HC-59** (`prefs/ui@*`) `labToggle({on})` (`ST.ui.lab = !!on`, saved) and `tourReset()` (no tours seen, saved; then the tab is `today`
  when set up with a plan, else `setup`). An unknown command name: `Life Director cannot do that.`
- **HC-60** (`time/sleep@*`) `sleepSave({bed, wake, weekend})`: whole minutes of the day (`Enter both times`); the length `((wake - bed) +
  1440) % 1440 || 480` (D6) under 300: `That is under 5 hours of sleep. Please check the times.`; the rules `sleep-wk` (every day, or Sunday
  to Thursday nights) and `sleep-we` (Friday and Saturday nights, not checked for length); the same rules (by JSON text) and need:
  `recorded('')`; else they replace the SLEEP rules, the need is set, `autoReplan`, said `Sleep saved` (`· plan rebuilt` once set up).
- **HC-61** (`time/rule*`) `ruleSave(form)` and `ruleRemove({id})`, refusals in this order: `Enter start and end times`, `Pick at least one
  day`, `Pick the first day on`, `That is not one of the choices.`, `That commitment was removed.` (an id that is not a commitment),
  `Nothing was added.`; a new one is `r-` plus `op` and answers `recorded('Already added')` when it exists; the rule (title trimmed or the
  kind's label, start, length `|| 1440` (D6), travel 0..240, unwind 0..120, rotation `{anchorDate, cycleLength: on + off, activeDays}` with
  `on`, `off` `|| 4` and at least 1); unchanged (by JSON text): `recorded('')`; `autoReplan`, said `Saved` or `Commitment added` (with `·
  plan rebuilt`). `ruleRemove`: gone is `recorded('')`; sleep or preferred time: `That is not a commitment.`; else removed, `Removed`.
- **HC-62** (`time/setup@*`, `setup-store-fails`) `finishSetup()`: a SLEEP rule (`Save your sleep times first`); `setupComplete`, a said
  re-plan, and when taken the tab `today` (`ST.ui.tab` too); saved; `applied()`.
- **HC-63** (`activity/*`) `activityAtAdd({op, title, tag, date, start, minutes})`: refusals in order: `op` a non-empty text of at most
  128 (`Nothing was added.`), a plan (`Make a plan for this day first.`), `manual-<op>` already there (`recorded('Already added')`), a name of
  1 to 80 characters, a tag of `CUSTOM_TAGS`, a date, a 15-minute start and 15 to 180 minutes in steps of 15, a local time that exists (a
  clock change's gap: `dst-*`), not in the past, inside the planned horizon, clear of sleep, work, commitments and planned sessions not
  cancelled or excused (the engine's availability for two days from it). Then a JSON copy of `ST` with the pinned session `manual-<op>`
  (tier 2, `CUSTOM`, locked), sorted by start, becomes `ST` only when saved (`Activity could not be saved. Nothing was added.`); `Activity
  added`.
- **HC-65** (`backup/*`) `exportBackup()`: `{ok: true, message: '', text: <the store's export>}`. `importBackup({text})`: the store's
  `importJson(String(text || ''))`; its error message is the answer (`Backup is not valid: ...`, the JSON parser's own words); the state
  replaces `ST` only when saved (`Not saved: <error> Nothing was restored.`); said `Backup restored`. The imported state is not repaired as
  boot repairs a loaded one (LH01).
- **HC-66** (`backup/reset@*`, `store-fails`) `reset()`: the store's `clear({playerId, timezone, nowEpoch})` is the last save; refused:
  the red banner and answer `Life Director was not reset: <error>`; else banner, notice and day are cleared and the state boots again (HC-1);
  `Life Director reset`.

## 7. The tick

- **HC-70** (`sync/match-and-adhoc@*`, `plan-day@UTC`) `syncHost` step 1 (`logic.js:171-187`), only when LifeMax is connected and there is
  a plan: each activity since the cursor not yet seen is marked seen; a planned session on the same local day whose tag matches (either way)
  or whose quest link is the activity's plan day is finished silently with the activity's minutes (or the plan's), without XP, with the
  activity's id and start (HC-20); otherwise an ad-hoc event `host:<id>` (at least 1 minute, 15 when none) and its progress (HC-23). Then
  the cursor moves to the latest `iso` of all activities.
- **HC-71** (`sync/undone-*`) Step 2: a completion whose LifeMax activity is gone from the log is revoked (`UNDONE_IN_LIFEMAX_LOG`), its
  history entry marked `undone`, and when its end is still ahead it goes back on the plan with the next revision; said `“<title>” was
  undone in your log; Life Director updated its records.`
- **HC-72** (`sync/quest-done@*`) Step 3: quests done (status `COMPLETED`, and earned milestones) leave the planned sessions' links; a
  session left with no link, no goal and not pinned is dropped; a pinned one keeps an orphan warning (**Read** for the warning: the case's
  pinned session serves a goal).
- **HC-73** (`sync/untimed@*`, `retired@*`) Untimed quests (`schedulingMode: 'NONE'`, and retired daily cards the host resolves): planned,
  unpinned, not running sessions without goals whose every link is untimed are retired to passive reminders (`{questId, title, anchor ||
  'WAKE'}`, once per quest).
- **HC-74** (`sync/offline@UTC`, `no-plan@UTC`) Not connected, or no plan: nothing, false.
- **HC-75** (`misses/*`) `processMisses` (`logic.js:242-268`): with a plan, the sessions `detectMisses` finds with the grace, at most 12;
  each still planned is repaired (`reschedule({type: 'MISSED'})`; a repair that throws records it `MISSED_UNEXPLAINED` in place, **Read**),
  then its consequence (`'MISS'`); a repaired one's history entry takes the consequence's state and consequence; saved when any.
- **HC-76** (`misses/MODERATE@*`, `HARDCORE_RPG@*`, `worn-ledger@*`) The consequences by mode, as the ledger spec's LED-18.
- **HC-77** (`misses/regroup@*`, `worn-ledger@*`) A consequence that opens the regroup sets the regroup banner (`Looks like life got
  hectic. ...`).
- **HC-78** (`rollover/*`) `maybeRollover` (`logic.js:269-275`): only once set up; light mode past its end restores the remembered intensity
  (or `BALANCED`) and clears the end; a quiet re-plan (`weekly rollover`) when there is no plan, the week key changed, under 2 days of plan
  remain, or the plan's zone is not the zone now.
- **HC-79** (`rollover/tick@*`, `misses/*`, `rollover/zone-change@London`) The tick's body (`logic.js:2904-2906`): sync, misses, rollover in
  that order; an error stops it with the red banner `Life Director hit an error: <message>`; any change (or the error) is saved. The
  page runs it only while Life Director is on screen (D4).

## 8. The dial

- **HC-80** (`dial/*`) `applyDial(now)` with the held change `P` (`logic.js:1883-1900`): no rule `P.ruleId`: `{ok: false, changed: false,
  moved: false, message: 'There is no change to make. Nothing was changed.'}`. The rule takes `P.startMin` and `P.durationMin`. Before set-up:
  saved only (`{ok: <save>, changed: true, moved: false}`). After: `rescheduleActivity` with `TIME_CHANGED` from `buildInput(now)`, applied as
  `Time adjusted`; refused: the rule goes back, saved, the banner's words (**Read**).
- **HC-81** (`dial/kept@*`, `bedtime@*`) `moved` is whether any session's start or end changed, or the number of sessions; the message is
  `<handleLabel> now <fHm(P.to)>` and ` · schedule adjusted` or ` · sessions kept in place`, said only when saved.
- **HC-82** (`dial/not-saved*`) A failed save answers with the banner's words and changes the rule all the same.

## 9. Fuzz

- **HC-90** (`fuzz/seed-*`) 160 seeded sequences of 3 to 8 steps (done, skip and take-back, pin, move, log for today, block hours, the
  tick hours later, re-plan, energy, rest days, priorities, award answers) in the eight zones, three ledgers and preference sets.

## 10. Defects

| Id | `logic.js` | What | Suggested |
|---|---|---|---|
| LH01 | 2385-2391, 102-105 | `importBackup` installs the backup's state without boot's repairs: a backup with a null ledger (any v0 prototype backup, which `migrate` builds with `ledger: null`) is accepted, then the next re-plan throws `TypeError: Cannot read properties of null (reading 'revoked')`, and every tick shows `Life Director hit an error: ...` until reset (`rollover/zone-change@London`). | Run boot's repairs on import |
| LH02 | 3440-3470 | The Skip's take-back is in memory only, lasts 2 minutes, and compares the plan's JSON text (D8). Any save in between that only trims history (a day boundary) makes it fail. | D8 |
| LH03 | 318-322 | A failed award keeps the completion (D2): the history entry has no activity id, the quest gets no bounty, and done again is `Already recorded.` (`done/award-failed@*`). | D2 |
| LH04 | 3408 | `minutesOf`: 0 and anything under 0.5 is "as planned", `true` is 1 minute (D11). | D11 |
| LH05 | 3405-3407 | `plannedInst` compares `String(id)`, `resolved` the id as given: a numeric id repeated after a done answers `gone`, not `Already recorded.` | Compare as text in both |
| LH06 | 2411-2423 | Friday and Saturday sleep is not checked for 5 hours (`sleep-we` takes any length: one hour is saved in `time/sleep@*`). | Check both |
| LH07 | 3585-3598 | Regroup `pause` answers `applied()` and clears the misses even when its repair is refused (**Read**). | Answer the refusal |
| LH08 | 2900-2907 | The tick runs only on screen (D4), and at most 12 misses per tick. | D4 |
| LH09 | 171-187 | `hostSync.seen` keeps every LifeMax activity id ever seen, in the saved state, for ever. | Prune with the cursor |
| LH10 | 2561 (engine), 181 | The cursor is strict `>` on the ISO text: an activity logged later with the same timestamp as the cursor, or back-dated before it (a log entry for yesterday), is never synced; the `seen` set then guards nothing the cursor does not already exclude (**Read**). | Inclusive cursor with `seen` |
| LH11 | 242-268 | A miss whose repair is refused keeps its consequence but stays planned, so it is found again every tick (the consequence is not doubled: one per session). | Move it to history |
| LH12 | 2436, 2443 | A commitment with equal start and end is a 24-hour rule (D6, `time/rule-refusals@*` `same`). | D6 |
| LH13 | 2506, 2436, 2474, 3566 | Ids from the operation id (D10): two goals whose `op` share 12 characters are one goal (`goals/add-before-setup@UTC`: the second is `Already added`). | D10 |
| LH14 | 3553-3570 | Something came up's work shift on a clock-change night is placed by the wall clock (`zonedToEpoch`), so its length differs from start-to-end by the change (`disrupt/dst-shift-*`). | Informational |

Native deviations: none. Every step of every case is reproduced (`HostGoldenTest`).

## 11. Owner decisions as the goldens show them

- **D2** (award failure): HC-21, LH03. The completion and its history stay; the XP and the bounty are lost; a retry is a duplicate.
  Native reproduces it; a later retry of the award would need the activity id slot (`hostActivityId: null`) as its marker.
- **D4** (tick only on screen): HC-79, LH08. `HostTick.tick` is a pure function of the state and the clock, so a background job (WP-LD10)
  can run it; the 12-miss cap would then apply per job run.
- **D6** (equal times): HC-60 (`sleepSave` 480), HC-61 (`ruleSave` 1440), cases `time/sleep@*` (bed 600, wake 600) and
  `time/rule-refusals@*` (`same`).
- **D8** (take-back): HC-17, LH02. Native keeps the take-back in `HostState` (so it survives as long as the caller keeps the state) and
  compares the plan's `JSON.stringify` text (`PageJson.text`), as the page does.
- **D10** (ids from operation ids): HC-30, HC-53, HC-61, HC-63, HC-34; LH13. Native takes the ids the screen sends; `HostEffects.newId` is
  the place to mint UUIDs.
- **D11** (0 minutes): HC-11, LH04.

## 12. Golden cases: the index, and the mutation checks

File `lifemax_modular_v73/tests/fixtures/ld-host-golden.json.gz` (859 cases, 4,835 steps with the boots; about 85 MB unzipped, 5.0 MB zipped).
Recorder `tools/gen_ld_host_golden.js` with the runner `tools/ld_host_vm.js` (logic.js with three behaviour-neutral hooks found exactly
once: the private functions exported on `__ldHost`, `tick(true)` skipping the on-screen guard, `closeSheet` returning without a root; the
engine and store modules exactly as the file has them; a recording host; a memory storage that can fail like a full or broken one; a clock
that stands still between steps; `performance.now()` 0, so `computeTimeMs` is 0), run inside the built page in Chromium (the page's own
JavaScript engine and Intl) and in node; every case run twice and required equal. Definitions `tools/ld_host_cases.js` (no Intl; states,
zones, clocks and payloads; sessions named by their place in the state, `{$ref}`). A step's outcome is `{at, reply, calls, session, state}`,
`state` being what changed in `ST` since the step before. Web test `tests/ld-host-golden.test.js` (re-records every case in Chromium byte
for byte; node agrees but for an explanation penalty's last bit in 6 cases, LD20; rules defined here; every refusal and repeat reached; the
hooks found once; the page carries logic.js and the engine as the files have them). Native: `HostGoldenTest` (every step: reply, host
calls with arguments and answers, volatile state, `ST`; `computeTimeMs` not compared), `HostHandTest` (by hand), the fake `ReplayHost`, the
runner `HostReplay`, under `android-wrapper/native/src/test/java/com/lifemax/rpg/core/lifedirector/host`.

Zones: UTC, New York, London, Sydney, Kolkata, Los Angeles, Kiritimati, Santiago (the WP-LD1 strict matrix), with clock changes in
`disrupt/dst-shift-*` (New York 2026-11-01 and 2027-03-14, London 2026-10-25, Sydney 2026-10-04, Santiago 2026-09-06) and
`activity/dst-*` (gaps and folds in New York, London and Sydney). Clocks: mornings of 2 to 6 November 2026 at several hours; steps hours,
days and a week later. Ledgers: none (boot creates it), one rest token, a worn ledger (no tokens, 4 completions towards one, 2 misses in a
row, a streak).

| Family | Cases | What | Rules |
|---|---|---|---|
| `done` | 175 | done at the start, a hand-made session at 02:00 done exactly 6 hours before (and one second earlier), minutes of every kind, too early, the 6-hour edge, gone, after a skip, every award answer, partial, not saved, quests with and without bounty, two quests sharing a period key, the timer, `finishInstance` directly, tokens earned | HC-10 to HC-14, HC-20 to HC-26 |
| `log` | 40 | later days, repeats, refusals, award answers, `logToday` directly | HC-15, HC-27 |
| `skip` | 59 | skip and take-back, too late, just in time, plan changed, other ids, refusals, tokens, the running timer | HC-16, HC-17, HC-24 |
| `pin` | 24 | pin, unpin, repeats; moves, refused moves | HC-18, HC-19 |
| `disrupt` | 53 | every kind, repeats, refusals, a shift ending when it starts (the next day), energy, clock-change nights | HC-30 to HC-32 |
| `replan` | 25 | twice, the red banner, before set-up, a full change log | HC-33 |
| `regroup` | 41 | every answer and its repeat, refusals, the banner's ×, light mode ending | HC-34, HC-35 |
| `goals` | 26 | priority, pause, remove, clarify, add (archetype, custom with and without its numbers, language, target), refusals, before set-up | HC-50 to HC-53 |
| `prefs` | 21 | options, ranges, rest days, routines, meals, Lab, tours, before set-up | HC-56 to HC-59 |
| `time` | 28 | sleep, commitments (new, repeat, edit, rotation, remove), refusals, set-up, a broken store | HC-60 to HC-62 |
| `activity` | 23 | pinned activities, every refusal, clock changes, no plan, not saved | HC-63 |
| `backup` | 7 | export, import (good, broken, invalid, v0), reset, a full store | HC-65, HC-66 |
| `sync` | 59 | matches and ad hoc, tags more and less specific than the session's, plan days, undone in the log (ahead and past), quests done, untimed and retired cards, not connected, no plan | HC-70 to HC-74 |
| `misses` | 34 | the three modes, grace, regroup, a worn ledger, nothing missed, no plan | HC-75 to HC-77 |
| `rollover` | 36 | next week, a two-day horizon running out, light mode ending, the tick over days, zone change (LH01), not set up, first plan | HC-78, HC-79 |
| `dial` | 27 | bedtime, work end, kept sessions, unknown rule, before set-up, not saved | HC-80 to HC-82 |
| `output` | 4 | refused plans, plans applied | HC-40, HC-41 |
| `early` | 10 | the repair with every kind of entry, one, no plan, done already | HC-42 |
| `boot` | 7 | new, blocked, migrated, unreadable, recovered, invalid main copy, offline | HC-1 |
| `fuzz` | 160 | seeded sequences | HC-90 |

Mutation checks: 69 source mutations of the native host code (a script copies `host/`, changes one place, compiles it against the
rest and replays the file; the last 20 again after cases were added for the first round's survivors). 60 make the replay fail: the words
(`Already recorded`, `used a token`, the time and day formats, `Rest up`, `Nothing was restored`, the tick's error banner), the limits
(480 minutes, the 120,000 ms take-back, 12 hours before the plan, 21 days of history, 60 change-log entries, 10 violations kept, 12 misses
a tick, 5 hours of sleep, 8 hours for equal times, 240 minutes of travel, 1440 for an equal-time commitment, two days of horizon, 12
characters of an operation id, 7 days of light mode), the boundaries (the 6-hour edge, `>=` of the early-done day, a shift ending when it
starts), the order and presence of every change (the take-back's plan check and its `undo`, the repeat of a pin, the misses reset, the
regroup question kept, the red banner on re-plan, the week key, the blackouts a repair keeps, the change log's action count, the Lab when
offline, the quest overlay once per event, the bounty after a failed award, the activity id from the award, the history slot count, the
`-r` id, the notes for quests not met, the `seen` flag's absence, the cursor, the log's undo check, the consequence and the regroup banner
of a miss, light mode's restored intensity, the passive reminder's anchor, retired cards, a pref's end of light mode, the goal pause
repeat, the deadline minute, a custom goal's minutes, the range step, the activity sort, the dial's notice only when saved). The 9 that
survive: the repair refusal of Something came up (HC-31, unreachable), the dial's refusal putting the rule back (HC-80, unreachable),
the 0.75 of the completion history (no case completes between 0.70 and 0.75 where it would move a session), the `dropped for this week`
summary (no repair sheds), the `seen` check (equivalent while the cursor is strict, LH10), an undone session ending within a minute of now,
the second tag direction of a sync match (the first matches in every case), cancelled or excused sessions in the clash check (a plan never
keeps them among planned sessions) and a dial change that only changes the number of sessions.

## 13. Swapping the host for the real adapters (WP-LD11)

`HostEffects` (this package, `host/HostEffects.kt`) is what the commands need; WP-LD11 builds the real adapters with its own port
(`hostadapters/HostPort.kt`). To swap: implement `HostEffects` once over that port (an adapter class in `hostadapters`, say
`DirectorHost(port: HostPort, store: DirectorStore, clock)`), mapping each method to the port's call: `awardActivity` to the economy's
LD-3 award (answering `AwardReply` with the same four reasons), `completeQuest` to the quest board's guarded check-off, `readQuests` and
`readPlanSessions` to the quest and plan readers (the engine's quest records), `activitiesSince` and `activityExists` to the activity log,
`milestones` and `retiredUntimedQuest` to the quest library, the store methods to WP-LD7's `DirectorStore` (`load`, `save`, `clear`,
`BackupText`), `newId` to `UUID.randomUUID()`, `deviceZone` to the device's zone. Then pass it to `HostCommands.run`; nothing in `host`
changes. The fake for tests stays `ReplayHost` (answers recorded from the page); a hand-written fake for new tests can implement the same
interface. Wrap the adapter in `RecordingHost` (as `HostCommands.run` does) to keep the call log for shadow mode (WP-LD12).

## 14. Open questions

- **OQ-H1** LH01: should `importBackup` run boot's repairs (a ledger, overlay, sync cursor), or should the store's `importJson` refuse a
  ledger-less state? A page change first, with new cases.
- **OQ-H2** LH05, LH06, LH07, LH11: fix in the page (with new cases), or keep as they are? They are reproduced natively.
- **OQ-H3** D2, D4, D8 are to be ruled before cutover (plan section 6); D6, D10 and D11 as the screens that send these commands move.
