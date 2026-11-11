# Life Director timers and background work: specification (WP-LD10)

Status: **complete for the timer and the wake calculator** (WP-LD10). It describes Life Director's focus timer (`Today.timer` and the
once-a-second check `focusTick`, the running timer `ST.ui.focus`), how the page reads the clock for it, and the instants at which the
page's own code (the timer check, and the tick's misses and rollover) changes the saved state, as they behave at branch
`codex/director-dial-controls`, commit `644a57c`; and the native code that reproduces them
(`android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector/timer`). The page is the authority: this document describes it
and changes nothing. Nothing here is wired to a screen, a service or an alarm; the page is still the only writer of Life Director's state.
Section 13 is the design for what would drive the timer and the tick in the background, and the recommendation for decisions D4 and D7.

Paths: `logic.js` and `engine.js` are `lifemax_modular_v73/src/features/life-director/logic.js` and `engine.js`; Kotlin files are in
`core/lifedirector/timer` unless named otherwise. The host commands the timer calls (`finishInstance`, `persist`, `say`, the tick) are
WP-LD9's (`LIFE_DIRECTOR_HOST_SPEC.md`, `core/lifedirector/host`) and are used unchanged.

Contents

0. How to read this
1. Boundary
2. The model (TM-1 to TM-4)
3. Start (TM-10 to TM-14)
4. Pause and resume (TM-20 to TM-22)
5. Reset (TM-25, TM-26)
6. Replace (TM-30 to TM-32)
7. Finish and cancel (TM-40 to TM-45)
8. The automatic finish (TM-50 to TM-53)
9. The once-a-second check (TM-60, TM-61)
10. The clock (TM-70 to TM-73)
11. What should wake the app (TM-80 to TM-88)
12. Fuzz (TM-90)
13. Background design, and the recommendation for D4 and D7
14. Defects (LT01 to LT17)
15. Golden cases: the index, and the mutation checks
16. Open questions

---

## 0. How to read this

Rule IDs are `TM-n`; defects are `LTnn` (the host spec's are `LHnn`, the engine spec's `LDnn`). Each rule is:

- **Executed**: the golden file `lifemax_modular_v73/tests/fixtures/ld-timer-golden.json.gz` holds cases recorded from the real page that
  show it (families or case ids are given), and `TimerGoldenTest` replays every step natively (`WakeGoldenTest` for section 11).
- **Read**: derived from reading the code only (a path the cases cannot reach, or one that needs a DOM or an interval, said why).

Every rule is **Executed** unless marked **Read**. JavaScript's `undefined` and JSON's `null` differ throughout; "truthy" is JavaScript's
truthiness; `===` is strict (a number id is not its text). A command's answer is `{ok, changed, message}` with WP-LD9's helpers (HC-3):
`recorded(m)` saves and is `{ok: <that save>, changed: false, message: m}`, `saved('')` is `{ok, message: ''}` by the last save, `gone` is
`{ok: false, message: 'That is no longer on the plan.'}`. Times are wall-clock milliseconds from `Date.now()` unless said otherwise;
`nowEpoch()` is their whole seconds.

## 1. Boundary

| `logic.js` | What | Native | Rules |
|---|---|---|---|
| 1929 `focusMs` | elapsed time of the running timer | `TimerCommands.focusMs` | TM-2 |
| 1933-1939 `focusModel` | the timer sheet's words and XP estimate | not here (WP-LD8's read models, `sheet: 'timer'`) | |
| 1952-1971 `focusTick` | the once-a-second check (1952-1959) and the clocks it draws (1960-1971) | `TimerCommands.focusTick` (the check) | TM-60, TM-61 |
| 2766 `tickClocks` | `setInterval(focusTick, 1000)`, started by the first Life Director or Overview glance drawn | Read | TM-60 |
| 3491-3549 `Today.timer` | start, replace, pause, resume, reset, finish, cancel, autoFinish | `TimerCommands.timer`, through `nativeTodayRun` (`TodayCommands`, name `timer`) | TM-4 to TM-53 |
| 2552-2582 `onClick` `focus*` | the page's own buttons: they change `ST.ui.focus` directly, not through `Today.timer` | not ported (page-only, Read) | TM-4 |
| 2865-2870 `glanceIntent` | the Overview's Start: refuses another running timer, else `Today.timer({act: 'start'})` | not here (the glance's screen) | |
| 242-275 `processMisses`, `maybeRollover`; 2900-2907 `tick` | the tick (WP-LD9) | `HostTick` | TM-72, TM-81 to TM-84 |
| 2918, 2923, 2926, 2169 | when the page ticks: on `visibilitychange`, every 5 minutes, on mount, at the dial's midnight | `Wakes` (Read) | TM-85, TM-86 |
| 1188-1196 `alertBuzz` | vibration 5 minutes before a session, on screen | `Wakes` (Read) | TM-87 |
| `engine.js:2048-2050` `detectMisses` | when a session counts as missed | `Wakes` | TM-81 |

Native entry points: `TimerMachine.run(state, nowMs, host, command) -> TimerOutcome(state', reply, calls)` with typed commands
(`TimerCommand.Start`, `Replace`, `Pause`, `Resume`, `Reset`, `Finish`, `Cancel`, `AutoFinish`, `Other`, `Check`) and typed answers
(`TimerReply.Answer`, `Checked(FocusTick)`, `Threw`); `HostCommands.run` with `HostCommand.Today("timer", payload)` and
`HostCommand.FocusTick` (WP-LD9's entry point, which the replay uses); `Wakes.of(st, nowMs, deviceZone, tickAnchorMs)`;
`FocusClock` (section 10).

## 2. The model

- **TM-1** The running timer is `ST.ui.focus = {id, start, paused, pausedMs, plannedMs}` (`logic.js:3517-3523`), saved with the rest of
  `ST` by every `persist` (so it survives closing the app: the timer "keeps running" because its start is a wall-clock instant), or
  `null`. `id` is the session id as the request gave it (text, or a number: TM-14); `start` and `paused` are `Date.now()` instants;
  `pausedMs` is the total paused time; `plannedMs` is the session's length when the timer started (TM-3). Native: the timer is the same
  object in `HostState.st` (WP-LD9's state), and `TimerMachine.run` is a pure function of `(state, now, host, command)`: it boots first
  when there is no state (`nativeTodayRun`, HC-10), every save and toast is a host call in the outcome, and nothing throws: a state without
  `ui` (where the page reads `ST.ui.focus` and throws `TypeError`) answers `TimerReply.Threw`. Cases: every family.
- **TM-2** `focusMs(f)` (`logic.js:1929`): `Math.max(0, (f.paused || Date.now()) - f.start - (f.pausedMs || 0))`: wall-clock
  milliseconds, frozen while paused, never below 0 (a clock set back counts nothing, LT01). Cases `pause/*`, `clock/*`.
- **TM-3** The target is `f.plannedMs || Math.max(60, inst.endEpoch - inst.startEpoch) * 1000` (`logic.js:3499`, `1955`): the length
  stored at the start wins over the session's present length (`auto/stale-planned-ms@*`, LT14); a missing or zero `plannedMs` falls back
  to the session's (`auto/no-planned-ms@*`, `auto/planned-ms-zero@*`, `tick/no-planned-ms@*`); a session with no length gets the
  60-second floor (`start/zero-length@*`).
- **TM-4** `Today.timer({act, id, start})` (`logic.js:3491`): `act` is compared exactly: `start`, `replace`, `pause`, `resume`, `reset`,
  `finish`, `cancel`, `autoFinish`; anything else (`explode`, none, `Start`) answers `Life Director cannot do that.` (`cancel/unknown@*`).
  Sessions are found as WP-LD9 finds them: `plannedInst(id)` among planned sessions by `String(id)`, `findInst(id)` (`logic.js:2307`)
  among planned and history sessions by `===`. `start` in a payload is compared to the timer's as `f.start !== Number(start)`. The page's
  own buttons (`onClick`, `logic.js:2552-2582`) do the same things without the checks (Start sets the timer without the doable or
  other-timer checks once its sheet allowed it, Pause and Resume as TM-20 and TM-21, Finish logs `focusMs` from `f.start` without
  `quietRefusal`, Stop says `Timer stopped. Nothing was logged.`); they are page-only and not ported (**Read**).

## 3. Start

- **TM-10** (`start/basic@*`) `start({id})`: no planned session of that id, or one not `PLANNED`, is `gone`; not doable (HC-12: from 6
  hours before its start, or on its own local day) answers `You can start it on the day, or from 6 hours before.`; the timer already on
  this session (`f.id === id`) is `recorded('')`; else `ST.ui.focus = {id, start: Date.now(), paused: null, pausedMs: 0, plannedMs}`,
  saved, `saved('')`.
- **TM-11** (`start/basic@*`, `six-hours@*`, `not-planned@*`, `no-plan@*`) The refusals in that order: `gone` (an unknown id, none, a
  number with no such session, a session `IN_PROGRESS`, no plan at all), then the 6-hour rule (a session at 02:00 on Friday starts from
  20:00 on Thursday, not a second before; one at 10:00 tomorrow from 04:00, by the same-day rule), then a running timer on another session
  still planned: `Another timer is running. Finish or stop it first.`
- **TM-12** (`start/orphan@*`, `orphan-paused@*`, `tick/history-target@*`) A running timer whose session is no longer planned (`plannedInst(f.id)`
  false: gone from the plan, done, missed, or never there) is replaced silently, paused or not (D15).
- **TM-13** (`start/not-saved@*`) Start does not look at its save: a refused save leaves the new timer in memory, with the red banner, and
  answers `Not saved: <error>`; the same Start again is `recorded('')` and saves (LT08).
- **TM-14** (`start/numeric-id@*`, `numeric-auto@*`) A number id finds the session (`String(7)` is `'7'`) and is kept as a number: a
  later `autoFinish` with `'7'` is not this timer (`recorded('')`), one with `7` is; Skip's running check (`nativeRemoval`, `ST.ui.focus.id ===
  inst.instanceId`, `logic.js:3397`, `3448`) misses it, so the session can be skipped while its timer runs, after which the check answers `gone`
  every second (LT04).

## 4. Pause and resume

- **TM-20** (`pause/*`) `pause()`: no timer, or paused already: `recorded('')`; else `f.paused = Date.now()`, saved, `saved('')` (by the
  save: `pause/not-saved@*`). The id and start of the request are not looked at (`pause/any-id@*`, LT09).
- **TM-21** (`pause/pause-resume@*`) `resume()`: no timer, or not paused: `recorded('')`; else `f.pausedMs = (f.pausedMs || 0) +
  Date.now() - f.paused`, `f.paused = null`, saved, `saved('')`. A finish then logs the time between start and finish less the pauses
  (36 minutes less 6 paused is 21, rounded).
- **TM-22** (`pause/clock-back@*`, `pause-before-start@*`, `reset/clock-back@*`) By the wall clock: a resume after the clock went back
  makes `pausedMs` negative (-300,000 ms), so the elapsed time grows by the change; a pause taken before the start (the clock set back
  past it) freezes the timer at 0 and its resume counts the time before the start as paused (LT02).

## 5. Reset

- **TM-25** (`reset/reset@*`, `mismatch@*`, `clock-back@*`) `reset({id, start})`: no timer, another id, or another start is
  `recorded('')`; else the timer is copied with `start` and `paused` both `max(Date.now(), f.start + 1)` and `pausedMs` 0: back to zero
  and **paused** (a Resume follows; LT11). With the clock set back the new start is the old start plus 1 ms.
- **TM-26** (`reset/not-saved@*`) A refused save puts the timer back as it was and answers `Timer could not be reset. Please try again.`

## 6. Replace

- **TM-30** (`replace/replace@*`, `clock-back@*`, `orphan@*`) `replace({id, start})` (the page's "stop this one without logging and start
  that one"): the new session `gone` or not doable as TM-10 (the words differ: `This session is not available to start yet.`, LT12); a
  timer whose start is not `start`: `recorded('')` when it is already on this id (the replacement landed), else `The timer changed. Please
  choose Start again.`; the same id: `recorded('')`; else the new timer starts at `max(Date.now(), f.start + 1)` (after the old one,
  even with the clock set back), saved, `saved('')`. A timer on a session off the plan is replaced as well (`orphan`).
- **TM-31** (`replace/refusals@*`) The refusals in that order: gone (an unknown id, a session `IN_PROGRESS`), not available, the timer
  changed (also with no timer at all), the same id.
- **TM-32** (`replace/not-saved@*`) A refused save keeps the old timer and answers `The new timer could not be saved. Your previous timer is
  unchanged.`

## 7. Finish and cancel

- **TM-40** (`finish/finish@*`, `short@*`, `paused@*`, `late@*`, `award-*`, `not-saved@*`, `engine@*`, `replan-while-running@*`)
  `finish({id, start})` with the timer's start: the minutes are `Math.max(1, Math.round(focusMs(f) / 60000))` (20 seconds is 1 minute;
  a paused timer's frozen time); the timer is cleared; the session of `f.id` (not the request's id) is finished with `finishInstance(inst,
  minutes, {startEpoch: Math.floor(f.start / 1000), quietRefusal: true})` (HC-20: the award, the history, quests, a residual re-plan of a
  partial session, the save, the notice `<title> done · +<xp> XP`); `saved('')`. An award that fails keeps the completion (D2, HC-21).
  A re-plan while the timer runs keeps the session (`logic.js:206`), and the finish lands.
- **TM-41** (`finish/finish@*`, `mismatch@*`) Without a timer of that start: `Already recorded.` (`recorded`) when the history holds the
  request's id with a state containing `COMPLETED` (a repeat of a finish that landed), else `recorded('')` with no timer, else `That timer is
  not running.`
- **TM-42** (`finish/gone@*`, `clock/missed-while-running@*`) The timer's session no longer planned: the timer is cleared, saved, said
  `That session is no longer on the plan. Log it from the Log page.`, and the answer is `saved('')`: ok, though nothing was logged (LT17).
- **TM-43** (`finish/duplicate@*`) A completion the ledger refuses (here a session put back on the plan after its completion was recorded:
  `DUPLICATE_EVENT_ID`) answers `Already recorded.` (`recorded`, WP-LD9's `refusedFinish`); the timer stays cleared. Any other refusal
  (`Cannot complete this block (<reason>).`) is **Read**: a planned session is always completable.
- **TM-45** (`cancel/*`) `cancel({id, start})` with the timer's start: cleared, saved, said `Timer stopped. Nothing was logged.`,
  `saved('')`; the id is not looked at (`cancel 'zz'` with the running start stops it, LT10). Without that start: `recorded('')` with no
  timer, else `That timer is not running.` (cancel has no `Already recorded.`).

## 8. The automatic finish

- **TM-50** (`auto/auto@*`, `paused@*`, `wrong@*`) `autoFinish({id, start})`: no timer, another start, another id (`!==`) or paused:
  `recorded('')`; the session not planned: `gone` (TM-52); elapsed below the target (TM-3): `recorded('')`, to the millisecond (2,699,999
  ms of 2,700,000 is not yet). Every early or stray call saves (`recorded`).
- **TM-51** (`auto/auto@*`, `hours-later@*`, `no-planned-ms@*`, `planned-ms-zero@*`, `stale-planned-ms@*`, `early@*`, `not-saved@*`)
  At or past the target it is a finish (TM-40) with **the target's minutes** (`Math.max(1, Math.round(target / 60000))`), not the
  elapsed time, and the timer's start: nine hours later the session is logged as 45 minutes from 10:01 (D7, LT03); a timer started
  early (from 6 hours before) logs a session done early.
- **TM-52** (`auto/gone@*`) A timer on a session off the plan: `gone`, and the timer is **kept** (nothing saved); every later check asks
  again (TM-61).
- **TM-53** (`auto/duplicate@*`) A finish the ledger refuses puts the timer back (`ST.ui.focus = f`) and saves, then answers `Already
  recorded.` (which saves again); the next second's check does the same, for ever (LT07).

## 9. The once-a-second check

- **TM-60** (`tick/tick@*`, `paused@*`, `no-timer@*`, `not-saved@*`, `wake/timer@*`) `focusTick()` (`logic.js:1952-1959`): no state or
  no timer: nothing; with a target (`f.plannedMs`, else the session's length, the session found by `findInst(f.id)` among planned **and**
  history sessions) and not paused, once `focusMs(f) >= target`: `Today.timer({act: 'autoFinish', id: f.id, start: f.start})`; else it
  draws the clocks (not modelled; the page's `document.querySelectorAll` for the Overview glance's clocks, answered with none by the
  recorder). The page runs it every second (`setInterval`, `logic.js:2766`) once Life Director or the Overview glance has drawn, for the
  life of the page (D14), **only while the page runs** (D7). Native: `TimerCommands.focusTick` answers what it did: `FocusTick.Idle`,
  `Counting(elapsedMs, targetMs)`, or `AutoFinished(reply)`.
- **TM-61** (`tick/history-target@*`, `unknown@*`, `start/numeric-id@*`) A timer whose session left the plan: found in history, the
  check asks `autoFinish` every second and is told `gone`, nothing saved, the timer kept; with an id found nowhere and no `plannedMs`
  there is no target and the timer never finishes; with `plannedMs` it asks every second too. Only a Start (TM-12), Replace, Finish or
  Stop clears it (LT13).

## 10. The clock

- **TM-70** (`clock/back-before-start@*`, `forward-jump@*`, `dst-night@*`, `dst-auto@*`) The timer reads only the wall clock
  (`Date.now()`), in UTC milliseconds: a clock set back an hour before the finish counts 0 and logs 1 minute, with a start after the
  finish's own time (LT01); a clock jumped forward five hours finishes the timer at once with the planned minutes; a clock-change night
  does not move it (a session from 01:00 over the New York and London fall-back and the Sydney and Santiago spring-forward is timed in
  real milliseconds: 90 minutes later logs 90, and the check finishes it exactly 120 minutes after the start), unlike the wall-clock
  placement of Something came up (LH14).
- **TM-71** (`clock/reopen-tick-first@*`, `reopen-glance-first@*`) The app closed while the timer runs and opened two hours after the
  session ended: when Life Director is what opens, `mount` runs the tick first (`logic.js:2926`), its misses take the session (TM-72), and
  the check a second later is told `gone`: the timer is kept and nothing is logged; when the Overview glance draws first (Life Director not
  on screen, so its tick does not run), the check finishes it with the planned minutes, and the tick afterwards finds nothing missed. The
  same state gives a completion or a miss by which screen opens first (LT06).
- **TM-72** (`clock/missed-while-running@*`, `start/numeric-id@*`) The tick's misses (`processMisses`, HC-75) do not spare the running
  timer's session: a timer still running after the session's end plus the grace has its session missed and repaired under it; the check
  then answers `gone` and Finish `That session is no longer on the plan.` (LT05). `syncHost` does spare it (`logic.js:218`, HC-73).
- **TM-73** (`TimerHandTest`; **Read** for the page, which has no monotonic clock) The two-clock model `FocusClock`: a `ClockReading` is
  the wall clock and a monotonic one (`elapsedRealtime`, with the boot it belongs to); a `MonoFocus` is a native timer's monotonic record
  (boot, start, paused at, paused total), kept in step with the page's timer by `FocusClock.follow` from the timer before and after each
  command (a new id or start begins a record, paused if the timer is; a pause or resume moves it; no timer ends it; a record of another
  boot begins again). `elapsed` answers the page's `focusMs` and the monotonic elapsed time, their difference (`clockShiftMs`), whether
  the wall clock moved by more than 2 seconds (`clockChanged`), and the time to trust (monotonic when there is one); `endAtWallMs` the
  wall-clock instant at which the target is reached by the trusted clock, for an alarm. Example: started at wall 1,000,000 and monotonic
  50,000, the clock set back an hour 30 minutes later: the page counts 0, the monotonic clock 1,800,000, and the alarm for a 45-minute
  target is 15 minutes from now.

## 11. What should wake the app

`Wakes.of(st, nowMs, deviceZone, tickAnchorMs)` answers a `WakePlan`: every `Wake(kind, atMs, sessionId)`, earliest first; `due` (the
wakes at or before now), `nextBackground` (the earliest that changes the saved state) and `of(kind)`. The instants are where the page's
own conditions first hold, to the millisecond; the page tests each only when it runs, so its change comes at its first run at or after
the instant (the check within a second; the tick within 5 minutes, at the dial's midnight or on returning to the app, and only on screen,
D4). Host sync (activities logged in LifeMax, HC-70) has no instant and is not here. The zone is the plan's (`tz()`), else the device's;
a zone the page cannot read leaves out the wakes that need it; nothing throws. Each **Executed** rule here is probed by a `wake` case: the
page's own check run a millisecond before the instant changes nothing, at the instant it acts, and `WakeGoldenTest` requires the
calculator, given the state before the probes, to name exactly that instant.

- **TM-80** (`wake/timer@*`) `TIMER_END`: a timer not paused, with a target (TM-3, TM-60's lookup): `f.start + (f.pausedMs || 0) +
  target`. None while paused, or with no target (LT13).
- **TM-81** (`wake/miss@*`, with the grace 60 by default, 15, 240 and 30 minutes) `MISS`: `detectMisses` takes a `PLANNED` session once
  `endEpoch + grace * 60 < nowEpoch()` (`engine.js:2049`; the grace is the normalised preference, `|| 60`): the earliest planned session's
  `(floor(end + grace * 60) + 1) * 1000`. The 12-a-tick cap (HC-75) does not move the instant.
- **TM-82** (`wake/week@*`) `WEEK_ROLLOVER`, once set up: `maybeRollover` re-plans when `lastGeneratedWeekKey !== weekKey(now)`: now
  when the key differs already, else the next local Monday at 00:00 in the plan's zone (`logic.js:148`, `272`; the page's `zonedToEpoch`
  for that midnight).
- **TM-83** (`wake/horizon@*`) `HORIZON_LOW`: `horizonEndEpoch - now < 2 * 86400`: the second after `horizonEndEpoch - 172800`, or now
  when already so.
- **TM-84** (`wake/light@*`) `LIGHT_END`: `now > prefsLightUntil` restores the intensity: the second after. The page then changes
  `ST.prefs` but, when no re-plan follows, does not save it (LT15).
- **TM-85** (**Read**: the page's `liveTick`, a DOM interval) `DAY_END`: the next local midnight. The page's 15-second `liveTick` runs
  the tick and redraws once `now >= d1`, the end of today's dial (`logic.js:2169`). Not a background wake: what the tick would do then is
  already a MISS, WEEK, HORIZON or LIGHT wake.
- **TM-86** (**Read**: an interval) `TICK`: the page's `setInterval(tick, 300000)` from mount (`logic.js:2923`), so `tickAnchorMs + k ×
  300,000`, the first after now (`Wakes.nextTick`); also on `visibilitychange` back to visible (`logic.js:2918`) and on mount (2926).
  Not a background wake (it is the page's polling).
- **TM-87** (**Read**: needs `document.hidden` and the dial's now-state) `SESSION_ALERT`: `alertBuzz` vibrates once when the next
  session is 5 minutes away or less (`logic.js:1191`), while the page is visible: 300 seconds before the next planned session that has
  not started. Not a background wake on the page; the natural "your session starts" reminder for a native notification.
- **TM-88** (`TimerHandTest`; the conditions are HC-78's, executed there) `REPLAN_NOW`: once set up, no plan or a plan whose zone is
  not the zone now: due now.

## 12. Fuzz

- **TM-90** (`fuzz/seed-*`) 120 seeded sequences of 4 to 10 steps over the hand plan in the eight zones: starts, pauses and resumes,
  finishes, cancels, resets and automatic finishes with the timer's start or a wrong one, replacements, the check hours later and with
  the clock set back up to an hour, the tick hours later, done and skip, saves that fail and recover, three preference sets.

## 13. Background design, and the recommendation for D4 and D7

Today the page does all of this only while it runs: the timer check every second, the tick every 5 minutes and on return, both only in
the WebView (D4, D7, D14). WP-LD10 adds no service or alarm; it gives the two pure pieces a native driver needs: `TimerMachine` (what the
timer does at any instant, by the page's rules) and `Wakes` (when something next changes). A driver only has to wake at
`Wakes.of(...).nextBackground`, run the matching step (`TimerCommand.Check`, or `HostCommand.Tick`), save, and ask `Wakes` again. Until
cutover (WP-LD13) the page is the only writer, so a driver built before then may only notify, not write.

**The two Android mechanisms.**

| | Ongoing notification with a chronometer (plus an alarm at the end) | Foreground service |
|---|---|---|
| What runs while the timer runs | nothing: the system draws the counting time (`setUsesChronometer`, `setWhen(start + pausedMs)`, frozen text while paused) | a process kept alive, the app's own code ticking |
| The finish at the target | an alarm at `TIMER_END` (`setExactAndAllowWhileIdle`, which needs `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM` from Android 12/13; without it `setWindow` of a minute) whose receiver runs `TimerMachine.run(Check)` and saves | the service's own check every second (as the page) |
| Cost and policy | none while it runs; survives the process being killed; one wake at the end | battery; Android 14 requires a declared service type and a matching permission, and a 45-minute focus timer fits none of the dedicated types cleanly (`specialUse` needs a Play declaration); a notification is shown anyway |
| Clock changes | the alarm is on the wall clock: re-arm on `ACTION_TIME_CHANGED` from `FocusClock.endAtWallMs` (or arm on `ELAPSED_REALTIME_WAKEUP`, which ignores clock changes) | the service can read `elapsedRealtime` directly |
| Reboot | re-arm on `BOOT_COMPLETED`; the monotonic record is void (TM-73), the wall clock is used | must be restarted the same way |

For the tick: the misses, the weekly and horizon re-plans and the end of light mode do not need to be exact (the page itself is up to 5
minutes late, or days when the tab is not opened). One inexact wake (WorkManager one-off work with an initial delay, or
`AlarmManager.setWindow`) at `nextBackground` among `MISS`, `WEEK_ROLLOVER`, `HORIZON_LOW`, `LIGHT_END`, re-armed after each run, plus
runs on `ACTION_TIMEZONE_CHANGED` and `ACTION_TIME_CHANGED` (a zone change is `REPLAN_NOW`) and on boot, covers what the page does on
screen. A periodic job (WorkManager's 15-minute floor) would also do, at more cost and less precision. `DAY_END`, `TICK` and
`SESSION_ALERT` are the foreground rhythm and need no background wake (a session reminder, if wanted, is a separate product choice: an
inexact alarm at `SESSION_ALERT`).

**Recommendation.**

- **D7, the clock**: measure natively with the monotonic clock (`FocusClock`), keep the page's wall-clock fields in the saved timer
  (`start`, `paused`, `pausedMs`) so the page and backups still read it, and when `clockChanged` correct the wall-clock fields from the
  monotonic elapsed time at the next command (so the saved timer stays true). Across a reboot fall back to the wall clock, as the page.
- **D7, finishing in the background**: yes, with the ongoing notification and an exact alarm at `TIMER_END` (not a foreground
  service). Finishing at the target in the background also removes LT05 and LT06 (the miss can no longer pre-empt the finish), and the
  completion is resolved at the right time rather than when the app is next opened. Keep logging the planned minutes for an automatic
  finish (what the page does); no cap is needed for the minutes, which are capped by the plan, but ask when the app is opened more than a
  day after the target without the alarm having run (rare: no exact-alarm permission and the process killed).
- **D4, the tick**: run it in the background natively, as one inexact wake at `nextBackground` (above), not only on open. The 12-miss cap
  then applies per run, as HC-75.
- **Page fixes worth ruling before cutover** (each a page change first, with new cases): exclude the running timer's session from
  `processMisses` (LT05), stop the check retrying a refused or gone finish every second (LT07, LT13), compare ids as text in Skip's
  running check (LT04), and save the end of light mode (LT15).

## 14. Defects

| Id | `logic.js` | What | Suggested |
|---|---|---|---|
| LT01 | 1929, 3517-3523 | The timer reads only the wall clock: set back, it counts 0 and finishes with 1 minute and a start after now; set forward, it finishes at once (`clock/back-before-start@*`, `forward-jump@*`). | D7: monotonic (TM-73) |
| LT02 | 3532-3533 | A resume after the clock went back makes `pausedMs` negative and the elapsed time grows; a pause before the start freezes at 0 and counts the time before the start as paused (`pause/clock-back@*`, `pause-before-start@*`, `reset/clock-back@*`). | D7 |
| LT03 | 3495-3502 | The automatic finish logs the target's minutes with the timer's start, whenever it runs: a timer left overnight is logged as done on time hours earlier, recorded when the app is next opened (`auto/hours-later@*`). | D7: finish at the target in the background |
| LT04 | 3397, 3405, 3496 | A number id: the plan finds `String(id)`, the timer keeps the number; Skip's running check and `autoFinish`'s id check are strict, so a running session can be skipped and its timer then answers `gone` every second (`start/numeric-id@*`). | Compare as text |
| LT05 | 242-268 | The misses do not spare the running timer's session (`syncHost` does, 218): a timer that runs past the end plus the grace has its session missed under it; Finish then says it is no longer on the plan (`clock/missed-while-running@*`). | Skip the running session in `processMisses` |
| LT06 | 2926, 2847 | Reopened after the end plus the grace, the same timer is missed (Life Director opens: tick first) or finished (the Overview glance draws first) (`clock/reopen-*`). | D7 (background finish), LT05 |
| LT07 | 3545, 1958 | A refused automatic finish puts the timer back and saves; the check retries every second, two saves a second, for ever (`auto/duplicate@*`). | Clear the timer, or stop retrying |
| LT08 | 3523, 3532, 3533 | Start, pause and resume keep their change in memory when the save is refused (and answer `Not saved`), where replace and reset put the old timer back (`start/not-saved@*`, `pause/not-saved@*`). | One rule for all |
| LT09 | 3532-3533 | Pause and resume do not look at the id or start: a stale screen pauses whichever timer runs (`pause/any-id@*`). | Name the timer |
| LT10 | 3534-3539 | Cancel and finish do not look at the id, only the start (`cancel/cancel@*`: `zz` with the running start stops it). | Informational |
| LT11 | 3525-3530 | Reset leaves the timer paused at zero (`reset/reset@*`). | Intended? |
| LT12 | 3507, 3520 | Replace's not-doable words differ from Start's (`This session is not available to start yet.`). | Informational |
| LT13 | 1952-1959, 3497 | A timer whose session left the plan is never cleared by the check: it asks `autoFinish` every second and is told `gone` (or has no target at all) until a Start, Replace, Finish or Stop (`tick/history-target@*`, `tick/unknown@*`, `auto/gone@*`). | Clear it, saying so |
| LT14 | 3499, 1955 | `plannedMs` is the length when the timer started: a session shortened or moved afterwards still finishes on the old length (`auto/stale-planned-ms@*`). | Informational |
| LT15 | 271, 2905 | The end of light mode without a re-plan changes `ST.prefs` but `maybeRollover` answers false, so the tick does not save it (`wake/light@*`: the change, no `store.save`). | Answer true |
| LT16 | 1952, 2766, 2900 | The check and the tick run only while the page runs (and the tick only on screen): no automatic finish, miss or rollover with the app closed (D4, D7, D14). | Section 13 |
| LT17 | 3541-3542 | Finish of a timer whose session left the plan answers ok (`saved('')`), saying only in the notice that nothing was logged (`finish/gone@*`). | Answer `ok: false` |

Native deviations: none. Every step of every case is reproduced (`TimerGoldenTest`).

## 15. Golden cases: the index, and the mutation checks

File `lifemax_modular_v73/tests/fixtures/ld-timer-golden.json.gz` (564 cases, 2,956 steps with the boots; 12.2 MB unzipped, 0.56 MB
zipped). Recorder `tools/gen_ld_timer_golden.js` with WP-LD9's runner `tools/ld_host_vm.js` (logic.js with its three behaviour-neutral
hooks applied at record time, the file unchanged; WP-LD10 adds to the first hook's export list `focusTick` and `focusMs`, to the runner a
`focusTick` step, a stub `document.querySelectorAll` answering none, and `{$ref: 'focus'}` for the running timer's start; none of these
changes what the page does or what WP-LD9's cases record), run inside the built page in Chromium and in node; every case run twice and
required equal. Definitions `tools/ld_timer_cases.js` (no Intl; most cases on a plan made by hand, four sessions at known local times,
so every instant is known; a few on the engine's plan). Web test `tests/ld-timer-golden.test.js` (re-records every case in Chromium byte
for byte; node agrees on every case; rules defined here; every answer of `Today.timer` reached; the wake probes). Native:
`TimerGoldenTest` (every step: reply, host calls with arguments and answers, volatile state, `ST`), `WakeGoldenTest` (the 40 probes),
`TimerHandTest` (by hand: the typed entry point, refusals and a missing `ui`, saves refused, the wakes of a running timer and plan, zones
with and without clock changes, the two-clock model), with WP-LD9's `HostReplay` (now reading a named fixture, the `focusTick` step and
the `focus` reference) and `ReplayHost`, under `android-wrapper/native/src/test/java/com/lifemax/rpg/core/lifedirector/timer`.

Zones: the eight of the WP-LD1 strict matrix (UTC, New York, London, Sydney, Kolkata, Los Angeles, Kiritimati, Santiago), with
clock-change nights in `clock/dst-*` (New York 2026-11-01, London 2026-10-25, Sydney 2026-10-04, Santiago 2026-09-06). Clocks: Wednesday
4 November 2026 from 10:01 local, steps seconds to days later, and backwards.

| Family | Cases | What | Rules |
|---|---|---|---|
| `start` | 64 | start, repeats, every refusal in order, the 6-hour edge across midnight, a timer on a session off the plan (running and paused), a refused save, a session in progress, a number id, no plan, a session with no length | TM-3, TM-10 to TM-14 |
| `pause` | 40 | pause and resume with repeats, two pauses, no timer, the clock set back before a resume and before the start, any id, refused saves | TM-20 to TM-22 |
| `reset` | 24 | reset, every mismatch, the clock set back, a refused save | TM-25, TM-26 |
| `replace` | 28 | replace and its repeats, every refusal, a refused save, the clock set back, a timer off the plan | TM-30 to TM-32 |
| `finish` | 72 | finish, repeats, mismatches, 20 seconds, paused, off the plan, every award answer, a duplicate, a refused save, late, the engine's plan with a quest, a re-plan while running | TM-40 to TM-43 |
| `cancel` | 20 | cancel, repeats, mismatches, unknown acts, a refused save | TM-4, TM-45 |
| `auto` | 68 | before and at the target, paused (also past the target), wrong start or id, off the plan, nine hours later, no or zero or stale `plannedMs`, a duplicate, started early, a refused save | TM-50 to TM-53 |
| `tick` | 40 | the check before and at the target, paused, without `plannedMs`, a session in history, an unknown id with and without `plannedMs`, no timer, a refused save | TM-60, TM-61 |
| `clock` | 48 | the clock set back and jumped, reopened after hours (tick first, glance first), missed while running, four clock-change nights | TM-70 to TM-72 |
| `wake` | 40 | each kind probed a millisecond before and at its instant, in every zone, four graces | TM-80 to TM-84 |
| `fuzz` | 120 | seeded sequences | TM-90 |

Mutation checks: 43 source mutations of the native timer (`mutate.py` copies `timer/`, changes one place, compiles it against the rest
and runs the three timer tests); see the work package's report in the plan for the count caught. The survivors are listed there with why.

## 16. Open questions

- **OQ-T1** D7: monotonic clock, background finish, and the long-absence prompt as section 13 recommends?
- **OQ-T2** D4: the background tick as one inexact wake at `nextBackground`?
- **OQ-T3** D15 (TM-12) and LT13: should a timer whose session left the plan be cleared (and said) at once?
- **OQ-T4** LT04, LT05, LT07, LT15, LT17: fix in the page (with new cases) before cutover, or port as they are? They are reproduced natively.
