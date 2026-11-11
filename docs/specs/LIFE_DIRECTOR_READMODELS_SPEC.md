# Life Director read models: days, day blocks, now and the week

Status: **complete for WP-LD8** (plan `LIFE_DIRECTOR_NATIVE_PLAN.md`, WP-LD8: part a, sections 0 to 10; part b, sections 11 to 16). Part a describes how the Life Director page turns a
saved state into what its screens and its native projection show for the plan's days, a day's blocks, the live timeline, what is
happening now and the week (`logic.js`, as at branch `codex/director-dial-controls`, commit `5669a42`), and the native code that
reproduces it (`android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector/readmodels`). The page is the authority: this
document describes it and changes nothing. Nothing native is wired to a screen; the page still computes everything it shows.

Paths: `logic.js` is `lifemax_modular_v73/src/features/life-director/logic.js`, `engine.js` its neighbour; Kotlin files are in
`core/lifedirector/readmodels` unless named otherwise.

Contents

0. How to read this
1. Boundary and inputs
2. The plan's days (RM-1 to RM-4)
3. A day's blocks (RM-5 to RM-10)
4. Now: the live timeline and `nowState` (RM-11 to RM-13)
5. The week (RM-14 to RM-20)
6. Page words as data (RM-21)
7. The native API, and how a shadow check uses it
8. Defects (RD01 to RD14)
9. Golden cases: the index, and the mutation checks
10. Open questions for the owner

Part b

11. Boundary and inputs (part b)
12. The rules of part b (RM-21 to RM-40)
13. The native API of part b, and how a shadow check uses it
14. Defects (RD15 to RD23) and native deviations (DV-RM1, DV-RM2)
15. Golden cases of part b, and the mutation checks
16. Open questions for the owner (part b)

---

## 0. How to read this

Rule IDs are `RM-n`; defects are `RDnn` (the engine spec's are `LDnn`, the store's `LSnn`, the ledger's `LGnn`). Each rule is:

- **Executed**: the golden file `lifemax_modular_v73/tests/fixtures/ld-readmodels-golden.json.gz` holds cases recorded from the real page
  that show it (families are given), and `ReadModelsGoldenTest` replays every one natively.
- **Read**: derived from reading the code only.

Every rule below is **Executed** unless marked **Read**. "Truthy" is JavaScript's truthiness (`undefined`, `null`, `false`, `0`, `NaN`
and `""` are false). Numbers are compared as doubles, bit for bit; text as UTF-16. Epochs are seconds; `now` is always `nowEpoch()`.

## 1. Boundary and inputs

The read models are pure functions of four inputs: the saved state `ST` (as `lifemax_director_state_v1` holds it), the page's clock
(`Date.now()`), the zone the device reports (used only when `ST` stores none) and LifeMax's XP preview (`LifeMaxHost.xpFor`, with
`connected()`). They read `ST` and never write it.

| `logic.js` | What | Native | Rules |
|---|---|---|---|
| 13 `nowEpoch`, 14-15 `deviceTz`, `tz` | the moment and the zone | `DirectorReadModels.nowEpoch`, `zoneValue`, `zone` | RM-1, RM-2 |
| 394-402 `horizonDays` | the plan's days still to come | `DirectorReadModels.horizonDays` | RM-3 |
| 3308-3315 `calendarDays` | seven days from an offset (the bezel's calendar) | `DirectorReadModels.calendarDays` | RM-4 |
| 404-425 `unplannedSegments` | availability preview of a day off the plan | `DayBlocks.unplanned` | RM-5 |
| 426-457 `dayBlocks`, 458-459 `KIND_CLASS`, 572 `blockClass`, 573 `sesState` | a day's blocks | `DayBlocks.of`, `DayBlock`, `StoredSession` | RM-6 to RM-10 |
| 1056 `SOON`, `ENDING`, `LATE`, 1058-1071 `liveTimeline` | today and tomorrow as one list | `LiveNow.timeline` | RM-11 |
| 1120-1135 `nowState` | what is on now, next, and the warnings | `LiveNow.nowState`, `NowState` | RM-12, RM-13 |
| 1215-1218 `xpOf` | LifeMax's XP preview of a session | `Week.xpOf`, `SessionXpPreview` | RM-14 |
| 1318-1345 `weekModel`, 25 `mins` | the week's rows, figures and fit text | `Week.model`, `WeekModel`, `Week.mins` | RM-15 to RM-18, RM-20 |
| 1302-1311 `weekSegs`, 1365-1371 `unscheduledGroups`, `unfitSummary`, 3272-3288 `nativeWeek` | the week as the projection carries it | `WeekProjections.of`, `WeekProjection` | RM-19 |

Used from the engine, unchanged and already native: `buildHorizon` (engine.js:137-149, `PlanHorizon.days`), `localParts`, `zonedToEpoch`,
`ymdAdd` (LD1 kernel: `WallClock`, `CalendarMath`), `deriveAvailability` (LD2: `Availability.derive`), `typeForTag` (`core/economy`
`LifeDirector.typeForTag`), zone names (`ZoneNames.resolve`).

Part b (sections 11 to 16) adds goals, quests, settings, My time, the forms, Something came up, Move, the shifts and the dial's handles
and stops; the rest of `nativeToday` is in neither part.

## 2. The plan's days

**RM-1** `tz()` (logic.js:15) is `ST.availability.timezone` when truthy, else the device's zone (`Intl` resolved, `UTC` when that fails).
An empty stored zone falls back to the device's (family `zone`, cases `empty`, `device`). A state with no `availability` throws a
TypeError. A zone name is read by `Intl`, which ignores letter case (cases `lower`); an unknown name throws a RangeError at the first
date computed (cases `unknown`; see RD04). Native: `DirectorReadModels.zoneValue`, `resolve`.

**RM-2** `nowEpoch()` (logic.js:13) is `Math.floor(Date.now() / 1000)`: whole seconds, rounded down. The cases' moments carry a fraction of a
second so the floor is exercised (every family). Native: `DirectorReadModels.nowEpoch` (`Math.floorDiv`).

**RM-3** `horizonDays(ST.schedule, tz())` (logic.js:394-402): `buildHorizon(horizonStartEpoch, Math.round((horizonEndEpoch -
horizonStartEpoch) / 86400), zone)` (local midnight to local midnight, so a clock-change day has 92 or 100 quarter hours), then only the
days whose `endEpoch > now`. When none is left (the plan is over), the one local day that holds `now`. Days keep their `index` in the
plan (so the first shown day can have index 3; RD14). With a plan that starts on a later day, every day is kept and the first is not
today (RD01). Families `seed` (moments before, inside and after the plan), `dst`, `scenario`, `plan`, `reschedule`, `fuzz`.

**RM-4** `calendarDays(offset, zone)` (logic.js:3308-3315): seven days from the local date `offset` days after today's (`localParts(now)`),
null for an offset that is not a safe integer or a date whose midnight is not a finite instant. Family `calendar` (offsets -10 to 40).

## 3. A day's blocks

**RM-5** `unplannedSegments(day, zone)` (logic.js:404-425): the engine's availability (`deriveAvailability`) over three days from the
local midnight before `day`, with the stored availability (its zone replaced by `zone`), commitments and preferences, and no sessions.
Each run of quarter hours painted by one block whose kind is one of `SLEEP`, `WORK`, `CLASS`, `APPOINTMENT`, `COMMUTE`, `DECOMPRESS`,
`BLACKOUT`, `SICK`, `EMERGENCY`, `UNAVAILABLE` becomes a segment (the block's members, cut to the day). Meals, routines, wind-down and
downtime are not previewed (RD12). Families `seed`, `calendar`, `fuzz` (days off the plan).

**RM-6** `dayBlocks(sched, day, zone)` (logic.js:426-433): for the saved plan, a day that ends at or before the plan's start or starts at
or after its end is drawn from the preview (RM-5); any other day from `sched.segments`. Each segment that overlaps the day and whose code
is not `FREE`, `SCHEDULED_TASK` or `USER_LOCKED_ACTIVITY` gives a block cut to the day, with `code`, `kind`, `title`, `refId` copied (a
member the segment lacks is left out), `mealStartEpoch` (the segment's own start, uncut) on a `MEAL`, and `clipped` true when the segment
reaches past either end of the day. Families `scenario`, `dst`, `midnight`, `calendar`.

**RM-7** (logic.js:435-437) Every stored session, scheduled first and then history, that overlaps the day and whose state is not
`EXCUSED_DISPLACED` or `CANCELLED_BY_QUEST` gives a block `{code: 'TASK', kind: 'TASK', title, inst}` with the session's own times
(not cut to the day). The blocks are sorted by start, a segment before a session at the same start (stable otherwise). Families `life`,
`reschedule`.

**RM-8** (logic.js:438-446) A segment overlapped by a **planned** session is cut around every session that overlaps it (any state), the
pieces kept in order; a segment overlapped only by sessions in other states is kept whole (RD08). The result is sorted by start again.
Families `life`, `reschedule`, `fuzz`.

**RM-9** (logic.js:448-455) Walking the blocks with a cursor from the day's start: a gap of at least 900 seconds before a block becomes free
time `{code: 'FREE', kind: 'FREE', title: 'Free time'}`; a block is kept when it ends after the cursor or is a session (so a segment wholly
covered by earlier ones is dropped, a session never); the cursor moves to the furthest end. A gap of 900 seconds or more after the last
block is free time to the day's end. Every family that draws a day.

**RM-10** `blockClass(b)` (logic.js:572) is `task` for a session, else `KIND_CLASS[kind]`, else `rest` for code `RECOVERY_PROTECTED`, else
`free`. `sesState(state)` (logic.js:573) is `done` when the state contains `COMPLETED` and not `PARTIAL`, `part` when it contains
`PARTIAL`, `plan` for exactly `PLANNED`, else `off`. Families `life`, `week`, every family through RM-12 and RM-19.

## 4. Now

**RM-11** `liveTimeline(now)` (logic.js:1058-1071): `[]` without a plan; else the blocks of the first two shown days (RM-3, RM-6 to RM-9),
copied, sorted by start, and each non-session block joined into the one before it when that is also not a session, has the same code and
the same title, and the new one starts no later than 60 seconds after it ends (the joined block keeps the first piece's other members;
RD09). Families `now`, `focus`, `midnight` (a meal and a commitment cut at midnight are joined again), `edge` (gaps of 30, 60 and 61 seconds).

**RM-12** `nowState(now)`, the blocks (logic.js:1120-1127):
- `cur`: the first session block that is live (not `off`, RM-10) and on now (`start <= now < end`), else the first non-session block on
  now, else none.
- `next`: the first block that is not `cur` (by identity), starts at or after `(cur ? cur.end : now) - 60`, is not free time, is live and is
  not a done session.
- `nextTask`: the first session block, not `cur`, that starts after `now` and is `PLANNED`.
- `isSes`: `cur` is a `PLANNED` session.
Families `now` (every second around the thresholds), `edge` (the 60-second slack, a session starting exactly now), `focus`, `seed`, `scenario`, `fuzz`.

**RM-13** The warnings (logic.js:1128-1134), with `SOON` 900, `ENDING` 600 and `LATE` 600 seconds:
- `running`: the timer `ST.ui.focus` exists, is not paused, and its `id` equals `cur`'s session's `instanceId`.
- `kick`: `late` with `now - cur.start` when `isSes`, not `running`, there is **no timer at all** and `now - cur.start >= LATE` (RD03);
  else `ending` with `cur.end - now` when `cur` ends within `ENDING`, there is a `next`, and `cur` is not free time; else none.
- `soon`: `{kind: 'soon', b: nextTask, sec}` when `nextTask` starts within `SOON`.
- `alert` is `kick || soon`.
The page holds `isSes` and `running` as `null`, `undefined` or `false` for "no"; every reader tests truthiness, so the cases and native
hold booleans. Families `now`, `focus` (running, paused, on another session, on one that left the plan, ending).

## 5. The week

**RM-14** `xpOf(inst, minutes)` (logic.js:1215-1218): null when LifeMax is not connected; else `Host.xpFor(typeForTag(tag), minutes ||
round(length in minutes), 1, local date key of the start in tz())`, null when that throws. Native takes the preview as an interface
(`SessionXpPreview`): the golden replay answers with the recorded answers and checks the calls, in order; production gives it `core/economy`
`LifeDirector.xpFor`. Families `host` (not connected: no call is made), `life` (actual minutes, including 0: RD06), `zone` (travel: RD05).

**RM-15** `weekModel()` rows (logic.js:1318-1334): one row per shown day (RM-3): its blocks (RM-6 to RM-9), `n` the session blocks, `done`
those `done` (RM-10), `m` the sum of each session's planned length rounded to whole minutes (RD07), `name` `Today` for the first row
(RD01) else the day name, `aria` `Today` or `<day name> <date>`, then `: <n> sessions, <mins(m)>` (RD02). Families `week`, `life`,
`scenario`.

**RM-16** The XP (logic.js:1327-1332, 1338-1339): for each session that is `PLANNED` or `done`, `xpOf(i, i.actualMinutes || null)`; the preview's
`xp` added to the total and each share of its `split` to its attribute, under `plan` for a planned session and `done` otherwise.
Attributes are sorted by `plan + done`, largest first (stable); `maxA` is the largest total, at least 1. Families `life`, `host`, `seed`.

**RM-17** The figures (logic.js:1342): `[n, 'sessions' + (done ? ', <done> done' : '')]`, `[mins(total minutes), 'planned']`,
`[xp ? '≈' + xp : '—', 'XP on the table']`. Families `week`, `life`.

**RM-18** The fit text (logic.js:1335-1336, 1343): `<mins(sum of capacity recoveryMin)> of protected downtime and <mins(sum of capacity
freeMin)> left completely free. <round(coverageRequired × 100)>% of must-do sessions (weekly minimums and quests) are placed.` A plan
without `capacity` or `validation.qualityMetrics` throws a TypeError (RD10, Read). Family `week` (coverage 0 to 1 with rounding edges,
recovery minutes 0 to 135).

**RM-19** `nativeWeek()` (logic.js:3272-3288), the week as the projection's `week` carries it: null while set-up is unfinished or there is
no plan; else `stats` as `{value, label}`, the ticks, one day per row (`index`, `label`, `date`, `today` for the first row, `aria`,
`count` as text, `done` `✓<n>` or `''`, `minutes` `mins(m)` or `—`, and `segs`: `weekSegs`, every non-free block as `{kind, state, left,
width}` in percent of the day's real length (a 23- or 25-hour day is its own 100%), rounded to hundredths), the legend, `yield` (null
without XP; else title, note and per attribute `label` (first letter upper-cased), `color` (ARGB from LifeMax's attribute colour, a
fallback for an unknown attribute), `done` and `plan` as fractions of `maxA` rounded to hundredths, `total` rounded) and `fit` (title,
text, the all-fit phrase, and `unscheduled`: null when no diagnostic has a truthy tier (RD11); else `<n> session(s) could not fit` and
one group per title and reason, `×<n>` when repeated, sorted by tier, highest first). Families `week`, `host`, `seed`, `life`, `fuzz`.

**RM-20** `mins(m)` (logic.js:25): `<h>h` and ` <r>m` when there is a remainder, for 60 minutes or more; else `<m>m`. Numbers as JavaScript
writes them. Every family that reaches the week.

## 6. Page words as data (RM-21)

Every word the read models produce comes from the page, exported by the recorder and never retyped in Kotlin:
`android-wrapper/native/src/main/resources/lifedirector/readmodel-words.json` (read by `ReadModelWords`). The tables are the page's own
constants (`DOW`, `KIND_CLASS`, `WEEK_LEGEND`, `WEEK_TICKS`, `YIELD_NOTE`, `ALL_FIT`, `SOON`, `ENDING`, `LATE`); the phrases written inline
in `dayBlocks`, `weekModel`, `nativeWeek`, `unfitSummary` and `mins` are taken by their position among each function's string literals
(`gen_ld_readmodels_golden.js` `PHRASES`), with the literal count pinned, so an added or removed phrase stops the export until the table is
checked; the attribute colours are `NativeTabKit.color(ATTR_COL[a])`. The web test fails when the file differs from what the page
exports now; `ReadModelsGoldenTest.theWordsAreThePagesExport` checks that the packaged file is the one read.

## 7. The native API, and how a shadow check uses it

`DirectorReadModels(state, nowMillis, deviceZone, xp, words)` takes the state as core/save JSON (no `org.json`) and only reads it. Its
answers are typed (`PlanDay`, `DayBlock`, `NowState`, `WeekModel`, `WeekProjection`); `ReadModelJson` writes each in the page's shape
(members the page leaves out are left out, sessions named `s<k>`/`h<k>` by where they are stored), which is what the golden replay compares.
A page throw is `PageTypeError` or `PageRangeError`.

For WP-LD12 (read shadow): the projection's `week` is `nativeWeek()` exactly, so `ReadModelJson.week(weekProjection())` is compared with
it member for member. Today's projection (`nativeToday`) is built from `horizonDays`, `calendarDays`, `dayBlocks` and `nowState`; until
WP-LD8b ports the dial, the shadow compares those inputs, which the page can send beside the projection in the debug build with the state
copy plan section 5.1 describes. The XP preview must then be the page's answers (or the native economy once WP-LD11 lands), as the
replay does.

## 8. Defects

Recorded as the page behaves; native reproduces each. None is fixed here.

| ID | Where | What | Severity |
|---|---|---|---|
| RD01 | logic.js:1333, 3280 | When the saved plan starts on a later day (made ahead, or the clock went back), the first row of the week is named `Today`, its aria says Today, and the projection marks it `today: true` although it is another date (79 cases, families `fuzz`, `seed`). | Medium: wrong date shown |
| RD02 | logic.js:1333 | The row's aria says `1 sessions` (no singular); screen readers read it (677 cases). | Low |
| RD03 | logic.js:1131 | The late warning needs no timer at all: a paused timer, a timer on another session or on a session that has left the plan hides "late" for the session that is on (family `focus`: 42 and 21 cases). | Medium |
| RD04 | logic.js:15, 395 | A stored zone `Intl` does not know makes `horizonDays` throw a RangeError, so Today's timeline, now, Week and the projection's week all fail (family `zone`, `unknown`); nothing falls back to the device's zone. | High if it happens: Life Director cannot draw |
| RD05 | logic.js:1217 | The XP preview dates a session by its local date in the current zone, not the zone the plan was made in; after travel a session near midnight is previewed on another day, which can change daily caps (family `zone`, `travel`). Ties to plan decision D1. | Low |
| RD06 | logic.js:1329 | `i.actualMinutes || null`: a done session with 0 actual minutes is previewed at its planned length (9 cases, family `life`). | Low |
| RD07 | logic.js:1323-1326 | Week's session count and planned minutes include skipped and missed sessions, and count done sessions at their planned length, not their actual minutes (family `life`). | Low: figures overstate |
| RD08 | logic.js:441 | Segments are trimmed only under a planned session; a done, partial, skipped or missed session moved over a meal or downtime is drawn on top of it (family `life`, 69 cases with a session over a segment). | Low |
| RD09 | logic.js:1067 (Read) | The timeline joins neighbouring blocks by code and title alone: two commitments with the same title and code but another kind or reference, within a minute of each other, become one; the joined block keeps the first piece's `clipped`, `kind`, `refId` and `mealStartEpoch`. | Low |
| RD10 | logic.js:1335-1336 (Read) | A plan without `capacity` or `validation.qualityMetrics` makes Week throw; a missing `coverageRequired` writes `NaN%`. The engine always writes both, so this needs a damaged save. | Low |
| RD11 | logic.js:1366 | Diagnostics with a tier of 0 or null (work the engine could not name) are left out of the count and the list (family `week`, 22 cases). | Low |
| RD12 | logic.js:413 | A day off the plan (the calendar's preview) shows only sleep and commitments: meals, routines, wind-down and downtime are not previewed, though the plan's days show them (family `calendar`). | Informational |
| RD13 | logic.js:395 | The day count of a plan is `Math.round` of its length in days; a stored plan whose length is not whole days (a damaged or hand-edited save) gains or loses its last day (Read). | Informational |
| RD14 | logic.js:400-401 | Shown days keep their index in the plan after the past days are dropped, so `days[0].index` can be 3; the rows use their own position (Executed, every family after the plan's first day). | Informational |

Native deviations: none. Every one of the 2,599 cases is reproduced, including the throws.

## 9. Golden cases: the index, and the mutation checks

Recorder `lifemax_modular_v73/tools/gen_ld_readmodels_golden.js`: the controller module of `logic.js` (`tools/ld_logic_vm.js`, the
module's text exactly as the file has it, with one statement added before its final `return` that hands the read-model functions to the
recorder when a `__ldReadModels` function is in scope) is evaluated inside the built page in Chromium, with the page's own engine, the
page's own LifeMax host (`connected()` set per case, `xpFor` watched) and the page's `Date` pinned to the case's moment. The page's own
controller, screens and storage are not touched, and the page's source is unchanged. Each case runs twice on fresh copies of its state and
must give the same answer both times. States come from the page and the engine, never typed: the store seeds the real page writes
(`tools/ld_store_seeds.js`), plans from `ld-schedule-golden.json.gz` (11 strict zones, every 2026-2027 clock change) and the last good
step of reschedule cases from `ld-reschedule-golden.json.gz`. Cases: `tools/ld_readmodels_cases.js`. Web test
`lifemax_modular_v73/tests/ld-readmodels-golden.test.js` re-captures the seeds, defines and records every state and case again and fails
on any byte of drift, and on any drift of the exported words.

432 states, 2,599 cases:

| Family | Cases | What |
|---|---|---|
| seed | 70 | the page's own saved states (fresh, planned, lived, its backup, imported, unfinished, migrated v0) at ten moments from a day before to eight days after |
| scenario | 330 | the ten engine scenarios in every strict zone: at the start, five minutes before a session, a seeded moment |
| dst | 240 | plans across every clock change: the day's start, either side of the change, its last second |
| plan | 284 | short, tight, re-planned and seeded plans; a seeded moment and two minutes before a session ends |
| reschedule | 202 | plans after moves, misses, skips and disruptions, with history |
| now | 650 | every threshold of RM-13, second by second around a session's start and end |
| focus | 150 | the timer running, paused, on another session, on one that left the plan, near the end |
| zone | 51 | read in another zone (travel), no zone, an empty one, lower case, an unknown one |
| life | 134 | sessions in every lifecycle state, with actual minutes, pinned, moved over commitments and each other, history changed |
| midnight | 39 | a meal and another commitment moved across midnight between two days |
| edge | 36 | blocks off the quarter hour: a meal 30, 60 or 61 seconds before an identical block, a commitment starting 30, 60 or 61 seconds before a done session ends, a planned session starting exactly now under an earlier one |
| calendar | 42 | the calendar's days off the plan, before, inside and after it |
| host | 31 | LifeMax not connected |
| week | 40 | unplaced work by tier and reason, coverage rounding, downtime minutes |
| fuzz | 300 | seeded moments from two days before to two days after the plan, with seeded zones, timers, states, host and calendar |

Native: `ReadModelsGoldenTest` replays every case (all sections equal, the XP calls in order, the family counts pinned);
`ReadModelsMutationTest` puts eleven plausible porting mistakes in place of one read model or one word and requires the replay to notice
each in the families it should.

Source mutations of the native code, each compiled and replayed against the whole file: 42, 41 caught, the survivor equivalent on every
state the file holds. Three (20, 25, 26) first survived and showed blocks off the quarter hour were never recorded; the `edge` family was
added for them and catches each. Number 7 (the preview derived from the day itself rather than the day before) gives the same segments
as the page on all 1,290 days after the end of every plan of the file (compared in node with the engine text), because a three-day
derivation from the day itself already covers it; it is kept as the page has it.

| # | File | Mutation | Result (cases that differ; counted on the file before the edge family unless marked) |
|---|---|---|---|
| 0 | `DirectorReadModels.kt` | nowEpoch rounded, not floored | caught 166 |
| 1 | `DirectorReadModels.kt` | a day ending now kept | caught 73 |
| 2 | `DirectorReadModels.kt` | no fallback day, past days kept | caught 1634 |
| 3 | `DirectorReadModels.kt` | an empty stored zone used | caught 8 |
| 4 | `DirectorReadModels.kt` | day count floored | caught 160 |
| 5 | `DirectorReadModels.kt` | calendar one day late | caught 78 |
| 6 | `DayBlocks.kt` | preview over one day | caught 2539 |
| 7 | `DayBlocks.kt` | preview from the day itself | survived: equivalent on the data (see above) |
| 8 | `DayBlocks.kt` | DECOMPRESS not visible in preview | caught 1104 |
| 9 | `DayBlocks.kt` | locked activity segments drawn | caught 11 |
| 10 | `DayBlocks.kt` | cancelled sessions shown | caught 107 |
| 11 | `DayBlocks.kt` | preview boundary | caught 22 |
| 12 | `DayBlocks.kt` | clipped only at the start | caught 1623 |
| 13 | `DayBlocks.kt` | sessions by start only | caught 10 |
| 14 | `DayBlocks.kt` | sessions before segments at the same start | caught 10 |
| 15 | `DayBlocks.kt` | any session trims segments | caught 59 |
| 16 | `DayBlocks.kt` | short pieces dropped | caught 5 |
| 17 | `DayBlocks.kt` | covered sessions dropped | caught 160 |
| 18 | `DayBlocks.kt` | last free time from more than 15 min | caught 10 |
| 19 | `DayBlocks.kt` | meal start clipped | caught 16 |
| 20 | `LiveNow.kt` | pieces joined only when touching | caught 8 (after the edge family) |
| 21 | `LiveNow.kt` | pieces joined by code alone | caught 1311 |
| 22 | `LiveNow.kt` | timeline today only | caught 1898 |
| 23 | `LiveNow.kt` | a dead session as current | caught 20 |
| 24 | `LiveNow.kt` | a skipped session as current | caught 22 |
| 25 | `LiveNow.kt` | no slack for next | caught 8 (after the edge family) |
| 26 | `LiveNow.kt` | next session starting now | caught 12 (after the edge family) |
| 27 | `LiveNow.kt` | late despite a timer | caught 96 |
| 28 | `LiveNow.kt` | ending on free time | caught 135 |
| 29 | `Week.kt` | zero actual minutes previewed | caught 5 |
| 30 | `Week.kt` | done sessions give no XP | caught 151 |
| 31 | `Week.kt` | done XP counted as planned | caught 147 |
| 32 | `Week.kt` | attributes smallest first | caught 2280 |
| 33 | `Week.kt` | coverage floored | caught 440 |
| 34 | `Week.kt` | partial counted done | caught 58 |
| 35 | `Week.kt` | an hour as 60m | caught 452 |
| 36 | `WeekProjection.kt` | bars on a fixed 24 h | caught 495 |
| 37 | `WeekProjection.kt` | x2 dropped | caught 715 |
| 38 | `WeekProjection.kt` | lowest tier first | caught 715 |
| 39 | `WeekProjection.kt` | tierless diagnostics counted | caught 40 |
| 40 | `WeekProjection.kt` | attribute colours lost | caught 2285 |
| 41 | `WeekProjection.kt` | attribute names not capitalised | caught 2285 |

## 10. Open questions for the owner

- **OQ-RM1** (RD01) Should the first row be named by its date when it is not today, and `today` be false? Native would follow the page once
  it changes.
- **OQ-RM2** (RD03) Should a paused timer or a timer on another session still let "late" show for the session that is on?
- **OQ-RM3** (RD04) Should an unknown stored zone fall back to the device's zone (and be repaired on load), rather than stop Life Director
  drawing?
- **OQ-RM4** (RD07, RD06) Should Week count only sessions that are planned or done, and count done ones at their actual minutes (and
  preview XP for 0 actual minutes as 0)?

---

# Part b: goals, quests, settings, My time, the forms, the sheets, shifts and the dial's handles

Status: **complete for WP-LD8b**. Part b describes, from the same saved state, what the page's native projection and sheets carry for
Goals, Quests, Settings, My time, the commitment and goal forms, Something came up, Move, the work rules and shifts other LifeMax features
read, and each day's dial handles and stops (`logic.js` as at branch `codex/director-dial-controls`, commit `d728b06`), and the native code
that reproduces it (same package). Line numbers below are that commit's. As in part a, nothing native is wired to a screen.

## 11. Boundary and inputs (part b)

Part b reads part a's inputs (`DirectorReadModels`: the state, `Date.now()`, the device's zone) and three more: what LifeMax answers
(`Host.capabilities()`, `Host.milestones()`; native takes them as `DirectorHostView`), how the store loaded the state (`loadReport.source`,
only `MEMORY_ONLY` matters) and the page's locale (RM-22). It never writes.

| `logic.js` | What | Native | Rules |
|---|---|---|---|
| 22-23 `fTime`, `fDay`, 1481 `fHm`, 618-620 `H12`, `hourWord`, 3661 `nativeHourWords` | times, dates and the dial's hour words | `ClockWords` (`PanelValues.kt`) | RM-22 |
| 1396 `goalShare`, 1401 `goalCards`, 3624 `nativeGoals` | Goals | `Goals`, `GoalsPanel` | RM-23, RM-24 |
| 1436 `questsModel`, 3301 `nativeQuests` | Quests | `Quests`, `QuestsPanel` | RM-25 |
| 3637 `nativeSettings`, 1683 `graceLabel`, 1685 `diagnosticsLine` | Settings | `Settings`, `SettingsPanel` | RM-26, RM-27 |
| 1500 `timeModel`, 1497-1499 `ROUTINES`, `MEAL_RANGE`, `stopLabels`, 1467 `daysText`, 1477 `ruleText`, 1484 `sleepContext`, 3663 `nativeTime` | My time | `MyTime`, `TimePanel` | RM-28 to RM-30 |
| 1559 `formField`, 1571 `ruleForm`, 1614 `goalTypeModel`, 1628 `goalFormModel`, 3679 `activityAtForm`, 3693 `nativeForm`, 3385 `nativeSheet` | the forms | `Forms`, `FormSheet`, `FormField` | RM-31 to RM-33 |
| 2225 `DISRUPT`, 2226 `disruptParts`, 3385 `nativeSheet('disrupt')` | Something came up | `Sheets.disrupt`, `DisruptSheet` | RM-34 |
| 2205 `moveTimes`, 2216 `moveModel`, 3395 `nativeSheet('move')` | Move | `Sheets.move`, `MoveSheet` | RM-35 |
| 2941 `workRules`, 2945 `shifts`, 589 `ruleOn` | work rules and shifts | `Shifts`, `WorkRule`, `Shift` | RM-36 to RM-38 |
| 600 `ruleInstances`, 1816 `dialHandles`, 613 `handleLabel`, 1764 `snapEp`, 1776 `dialStops`, 1794 `dragPreview`, 1801 `dialChange`, 580 `angOf`, 1827 `minuteOf`, 3097 `nativeHandles` | the dial's handles and stops | `Dial`, `DayHandles` | RM-38 to RM-40 |

Used unchanged: part a's `horizonDays`, `calendarDays` and `dayBlocks`; the engine's `normalizePreferences` (`PlanPreferences.of`),
`ARCHETYPES` (`DemandTables.archetypes`, exported by the demand recorder), `localParts`, `zonedToEpoch`, `tzOffsetSec`, `ymdAdd`, `ymdKey`
(`WallClock`, `CalendarMath`).

Not in part a or b (later work): the rest of `nativeToday` (the arcs, the words on the rings, the core, the mix, up next and the rows with
their chips), the session, timer and meal sheets, `nativeFrame`, the guides, the first-run card and the widget. They are presentation
over part a's blocks and now state; their times are the RM-22 words.

The page's stored values reach the read models as `JSON.parse` gives them, and the page combines them with JavaScript's operators, so a
`startMin` stored as text `"540"` is concatenated by `+` (`"540" + 480` is `"540480"`) and converted by `-`, `%` and `Date.UTC`. Native
does the same (`Js.add`, `PageValues`), so the answers match for every stored value the cases hold.

## 12. The rules of part b

**RM-21** Page words as data (both parts; part a's are section 6's `readmodel-words.json`). Every word part b writes comes from the page: the controller's tables (`PRIO`, `GOALS_EXPLAIN`,
`GOALS_EMPTY`, `GOAL_REMOVE_CONFIRM`, `CLARIFY_TEXT`, `QUEST_HEAD`, `MILESTONE_NOTE`, `KIND_LABEL`, `ROUTINES`, `MEAL_RANGE`, `RULE_KINDS`,
`RULE_LOADS`, `NEW_RULE`, `CUSTOM_TAGS`, `GOAL_LEVELS`, `INTENSITY`, `PREF_RANGES`, `GRACE`, `HORIZON`, `MISSES`, `SETTINGS_TEXT`,
`DRAG_WORDS`, `DIAL`, `MINI_SNAP`, `MINI_GROUP`, `DISRUPT`, `TAG_ICON`, `DRAG_KINDS`, `DOW`), the engine's version, `H12`, and the phrases
written inline in 24 functions, taken by their position among each function's string literals (`gen_ld_readmodels_b_golden.js`
`PHRASES`: the literal count of each function pinned, literals that must stay equal pinned), exported to
`android-wrapper/native/src/main/resources/lifedirector/readmodel-words-b.json` and read strictly by `PanelWords` (a missing, unknown or
mistyped member refuses the file; `PanelPhrases.REQUIRED` names every phrase native reads). Literals not exported are codes (states,
kinds, field keys, commands such as `ruleSave`) that native names as the page's API. The numbers of the forms (a name's 40 characters, a
session's 15 to 180 minutes, travel up to 240) are logic, checked by the cases, not words. The web test fails when the file differs
from the page's export; `PanelsGoldenTest.theWordsAreThePagesExport` checks the packaged file is the one read, and
`aChangedWordsFileIsRefused` that a changed one is refused. Every family.

**RM-22** Times as the page writes them, in `en-US` (the locale the page is recorded in; `fmt` uses the device's locale): `fTime` is
`h:mm AM` with a plain space, `fDay` is `Wed, Oct 28` (Intl's own short names), `fHm(m)` is minute `m` of 1 January 2020 in UTC (so a
minute past 1440 or below 0 wraps, and NaN throws a RangeError), `hourWord(h)` is `Midnight`, `Noon`, `3 AM`, `3 PM` with `H12` true
(`03:00` style otherwise, Read). A moment a Date cannot hold throws a RangeError, as `Intl.DateTimeFormat.format` does. Native:
`ClockWords`, en-US only (DV-RM1). Every family.

**RM-23** `goalShare(g, alloc)` (1396-1400): the goal's allocation (`alloc.find` by `goalId === g.id`); when its `targetShare` is not
truthy (a local repair left it 0), a copy with `targetShare = (g.priority || 3) / max(1, Σ over the allocation of the matching goal's
priority || 3)`, the sum taken with `+` (a priority stored as text concatenates, then `Math.max` reads the text as a number). Families
`goals` (241 plans repaired by the engine, and seeded zero shares), `fuzz`.

**RM-24** `goalCards()` and `nativeGoals()` (1401-1420, 3624-3635): one card per goal, in the stored order: `id`, `title` and
`priority` as stored; `sub` the archetype's title (or `Custom`), ` · `, `(level || 'NOVICE').toLowerCase()` and ` · paused` for status
`PAUSED` (RD16); `share` null without an allocation, else `target` and `scheduled` as `min(1, max(0, value || 0))` with the page's
rounded percentages (`Math.round((v || 0) * 100)` and `%`) and the line `<plannedSessions> sessions · <mins(scheduledMin)> this week ·
<title> <med>[–<base>]×, ...` (`–<base>` only when `base > med`); `warning` the trajectory warning's message, ` Projected finish: ` and
`fDay` of its projected epoch; `clarify` the plan's suggested archetypes, or the first four archetypes when it suggests none, each with
its title (an unknown archetype throws); `pause` `Resume` or `Pause`; `remove`. The panel: the explanation, the priorities 5 to 1 with
`PRIO`'s names, the cards, `GOALS_EMPTY` when there is no goal, `Add a goal`, the remove confirmation. Families `goals`, `seed`, `fuzz`.

**RM-25** `questsModel()` and `nativeQuests()` (1436-1456, 3301-3305): the head is `QUEST_HEAD` when LifeMax is connected, else `Quest
Board not detected` with the missing capabilities joined by `, `. Without a plan: no rows, no reminders, `empty` `''`, no milestones,
and `Host.milestones()` is not asked. Else one row per `questCoverage` entry whose `String(questId)` does not end in `@next`: `when`
`Daily` (recurrence `DAILY`), `Due <fDay(deadline)>` or `No deadline`; `status` by the first that holds: `All-day reminder` (PASSIVE),
`Done this period` (COMPLETED), `<n> session(s) scheduled` when covered (singular only for exactly the number 1), `Could not fit` when a
diagnostic names the quest (`linkedQuestIds` includes its id, or `taskId` is its id), `Locked`, else `<n> scheduled`; `tone` `ok`
(covered or COMPLETED), `pas` (PASSIVE) or `no`; `why` the first naming diagnostic's explanation when not covered, else `''`. Reminders
`passiveReminders` as `Tracked on the Quest Board`, `All-day`. Milestones: LifeMax's unlocked ones, `<done> of <n> earned`, each
`Earned` or `In progress`; null when none. Families `quests`, `host`, `seed`, `fuzz`.

**RM-26** `nativeSettings()` (3637-3659): the preferences as `normalizePreferences` gives them; the intensity, the three ranges and the
grace range each with a label per stop from low to high by step (`mins` for the ranges, `<m>m` for grace), `value` the preference as
normalized (grace and the routines are not normalized: RD17); rest days Sunday first with two letters and whether the preference
includes the day; the planning window (`String` of the value, options 7 and 14: RD18); misses with the text in three spans, the rest
tokens `String(ledger.restTokens)` in bold; the guide and data texts with `storageUnavailable` true only for a `MEMORY_ONLY` load;
diagnostics (RM-27); the lab switch `!!ST.ui.lab`. A state without a ledger throws a TypeError (RD15). Families `settings`, `host`,
`seed`, `fuzz`.

**RM-27** `diagnosticsLine()` (1685-1689): `engine <ENGINE_VERSION> · ` then `<status> · <computeTimeMs> ms · quality <sqi> ·
<scheduledInstances.length> planned` (or `no plan`), ` · events <ledger.order.length>`, ` · host connected` or ` · host missing <missing
joined by ,>`, and ` · last rejected plan: <JSON.stringify(violations)>` when a rejected plan is stored. Families `settings`, `host`.

**RM-28** `timeModel()` sleep (1500-1513): the weeknight sleep is the SLEEP rule with id `sleep-wk`, else the first SLEEP rule; the
weekend one the rule `sleep-we`. Bed and wake are the rule's start and `(start + duration) % 1440` (23:00 and 07:00 without one), their
`fHm` texts, the length `mins(((wake - bed) + 1440) % 1440 || 1440)` (a zero length reads as 24 hours), the weekend's times (an hour
later than the week's when there is no weekend rule), the labels (`Weeknight bedtime` when a weekend rule exists), and the save button
(`Save sleep and continue`, primary, without a sleep rule). Set-up's steps (while set-up is unfinished): Sleep, Work & school, Goals,
Build, each `ok`, `cur` or `''` by what exists. Families `time`, `seed`, `fuzz`.

**RM-29** `timeModel()` the rest (1514-1527), `daysText`, `ruleText` (1467-1480): commitments are the rules that are neither SLEEP nor
PREFERRED, each `<KIND_LABEL or kind> · <days> · <fHm start>–<fHm end>[ · <commute>m travel each way]`, the days as `rotating <on> on /
<off> off`, `every day`, `weekdays`, `weekends`, or the day names of the days sorted as text; the dot `block` for UNAVAILABLE; the
routines with a stop label per step (`Off` for 0) and the meals' switch; the goals' titles as chips; the build card with `Time zone:
<tz()>` while set-up is unfinished, else Re-plan. (`daysText`'s night patterns, `Sun–Thu nights` and `Fri and Sat nights`, are for
SLEEP rules, which My time never lists: Read.) Families `time`, `seed`, `fuzz`.

**RM-30** `nativeTime()` (3663-3669): the sleep part gains the snap (15), `sleepContext` (each rule neither SLEEP nor PREFERRED that
rotates or has a day from Monday to Friday, as `{from: startMin, to: startMin + durationMin}`), the clock text of every quarter hour,
the length text of every quarter-hour length (0 as a full day), the dial face (`DIAL`, `hourWord` every three hours), and the labels'
`group`. Families `time`, every family.

**RM-31** `ruleForm(r)` (1559-1590) through `nativeSheet('rule', id)`: a new commitment (id `''`) starts from `NEW_RULE`; an id names a
rule that is not SLEEP or PREFERRED (`{kind, id, gone: true}` otherwise). Fields in order: name (text, 40), type (`RULE_KINDS`),
rotating (a check that reveals on/off/anchor and hides days), days (the stored list copied, `[]` while rotating), days on and off (the
rotation's counts, 4 and 4 without one), first day on (the rotation's anchor, else today's local date), start and end (time, with
`fHm`), travel (0 to 240 by 5), unwind (`decompressMin` when not null, else 30 for WORK that is not LIGHT, else 0), how tiring
(`RULE_LOADS`, `MIXED_MODERATE` by default). Each field carries what `formField` gives its kind (time: text; text: maxLength and
placeholder; number: min, max, step and placeholder; check: reveals and hides). Families `forms`, `seed`, `fuzz`.

**RM-32** `goalTypeModel()` and `goalFormModel(arch)` (1614-1651): the goal kinds are the engine's archetypes in order (icon by
`tagIcon` of the first template's tag, the templates' titles joined), then `Something else`. A goal form for a known archetype or
`CUSTOM` (null for any other id): name; `Language` with `spanish` for SKILL_LANGUAGE; for your own activity the tag, minutes, times per
week and the two efforts; priority (`'3'`) and experience; the measurable target (fold) only when a template drives volume. Families
`forms`, `fuzz`.

**RM-33** `activityAtForm(id)` and `nativeForm` (3679-3700): `at:<epoch>` with a safe positive integer gives the one-activity form at
that local date and minute in `tz()`; anything else (`at:0`, `at:-900`, `at:abc`, `at:1.5`, beyond 2^53) gives null. Families `forms`,
`dst`, `fuzz`.

**RM-34** `nativeSheet('disrupt')` (2225-2240, 3397): null while set-up is unfinished or there is no plan; else `DISRUPT`'s title and
note and the five parts: work (today's local date, travel 0 by 5, 17:00 to 21:00 with their texts), the next hours (1, 2, 3, 4, 6), the
day, sick (1 to 3 days) and the energy check-in. Every family.

**RM-35** `nativeSheet('move', id)` and `moveTimes(inst)` (2205-2218, 3395): null without a plan; `{kind, gone: true}` unless `id` is a
`PLANNED` session of `scheduledInstances`. The times: on every shown day (part a's `horizonDays`), the day's free blocks and the
session's own blocks sorted by start and merged when they touch or overlap; in each, every half hour from `max(start, ceil((now + 900)
/ 900) × 900)` where the session's length fits, except its own start; the first 48, labelled `<short day> <fTime>`. The title is `Move
“<title>”` and the note gives the length in `mins` of `(end - start) / 60`. Families `move`, `dst`, `seed`, `fuzz`.

**RM-36** `workRules()` (2941-2944): the WORK rules as `{id, title || 'Work', days (copied, [] when not truthy), startMin, durationMin}`.
Every family.

**RM-37** `shifts(from, to)` (2945-2974): for each local date from the day before `from`'s to `to`'s (at most 400), each WORK rule on that
date (RM-38) gives a shift from `zonedToEpoch(date, startMin)` to `zonedToEpoch(date, startMin + durationMin)`, kept when it ends after
`from` and no later than `to` (key `<id>|<date>`); then each one-off commitment whose kind (or, without one, `commitmentType` CLASS or
EMERGENCY, else WORK: RD19) is WORK, with truthy start and end, ending in the same window (key `one|<id or commitmentId or start>`,
`ruleId` null, `Extra shift` without a title, minutes rounded). A shift is dropped when blackouts with truthy start and end cover at
least half of it. Sorted by end, stable. Families `shifts`, `dst`, `seed`, `fuzz`.

**RM-38** `ruleOn(r, ymd)` and `ruleInstances(day)` (589-612), `dialHandles(day)` (1816-1826): a rule is on a date within its
`validFrom`..`validTo` (compared as the page compares), on a rotation's active day (days since the anchor, as `Math.round` of the UTC
difference, taken `((n % c) + c) % c` with the stored cycle length, so a cycle stored as text concatenates), else when its `days` list
(every day when it has none) includes the date's weekday. The dial's rules are those of a `DRAG_KINDS` kind; each occurrence that starts
on the day before or the day and overlaps the day gives a handle at each end that falls on the day (an end at the day's very start is
not a handle), keyed `<id>|<end>|<date>|<epoch>`, kind `sleep` for SLEEP, else `work`. Families `handles`, `dst`, `fuzz`.

**RM-39** `snapEp` and `dialStops(d0, d1)` (1764-1792): a place is the nearest quarter hour of the wall clock (`Math.round`; 24:00 is
23:45), on `d0`'s date. On a plain day (86400 seconds from a local midnight whose offset is the same a minute before the end) the snap
is asked at the start and at minute 8 of each quarter hour; on any other day at every minute; a stop is kept where the snapped minute
changes, with its angle (`angOf`), the angle it is chosen from (`(t - d0) / (d1 - d0) × 360`) and its `fHm` text. Families `handles`,
`dst` (every 2026-2027 clock change).

**RM-40** `dragPreview`, `dialChange` (1794-1806) and `nativeHandles` (3097-3110): for each handle, its angle, its minute, its label
(`Bedtime`/`Wake up` for sleep, else `<title || KIND_LABEL || 'Commitment'> starts/ends`), the TalkBack label with `fTime`, and for every
stop the rule's new length (start handle: `(end - m) mod 1440`; end handle: `(m - start) mod 1440`), `''` when under 15 minutes (the
readout says `Too short`), else `mins` with ` of sleep` for sleep; and the angle where the lit arc ends (the other end of the span,
`zonedToEpoch` on `d0`'s date, kept on the day). No handles: `{handles: [], stops: [], dragWords}`. The golden adds the day's start and
end, as the projection's `dial` carries them beside these members. Families `handles`, `dst`, `seed`, `fuzz`.

## 13. The native API of part b, and how a shadow check uses it

`DirectorPanels(models, host, loadSource, words)` takes part a's `DirectorReadModels`, a `DirectorHostView` and the load source. Its
answers are typed (`GoalsPanel`, `QuestsPanel`, `SettingsPanel`, `TimePanel`, `FormSheet` (`Form`, `GoalTypes`, `Gone`), `DisruptSheet`,
`MoveSheet` (`Move`, `Gone`), `WorkRule`, `Shift`, `DayHandles`), each with `json()`, the page's JSON for it (members the page leaves out
left out). Nothing throws: every answer is a `ReadAnswer` (`Value`, or `Threw` with the page's error name; a native fault that stands
for nothing on the page is `Threw("Native…")`, so a shadow records it rather than stopping). `TimePanel` keeps the page's object whole
because a screen reads it whole.

For the read shadow (stage A, plan 5.1): the projection's goals, quests, settings and time are `nativeGoals()`, `nativeQuests()`,
`nativeSettings()` and `nativeTime()` exactly, and a sheet is `nativeSheet(kind, id)` exactly, so the page can send them beside part a's
sections in the same envelope, with the host's two answers it gave while they ran (as part a sends the XP previews) and the load source;
native compares `json()` member for member. Today's dial handles are `nativeToday(...).dial`'s `handles`, `stops` and `dragWords`
(with `startEpoch`, `endEpoch`): `handles(day)` compares with them. `PanelsReplay.replay` is the reference for building the sections;
`RecordedHost` there is how the page's answers are replayed. The shadow packages are not changed by this work.

## 14. Defects (part b)

Recorded as the page behaves; native reproduces each. None is fixed here.

| ID | Where | What | Severity |
|---|---|---|---|
| RD15 | logic.js:3655, 1688 | Settings reads `ST.ledger.restTokens` and `ledger.order.length` with no guard: a state without a ledger (a damaged save; the store always writes one) makes Settings throw (Executed, `fuzz`). | Low |
| RD16 | logic.js:1409 | A goal card's subtitle shows the stored level lower-cased (`novice`, `intermediate`, `advanced`), not the words the goal form offers for it (`New to this`, `Some experience`, `Experienced`) (Executed, every goal card). | Low: inconsistent words |
| RD17 | logic.js:3647, 3653 | The grace range's and the routines' values are the stored preferences, which `normalizePreferences` does not check: a value outside the slider (600, 0) or stored as text reaches the screen as it is (Executed for grace, 23 settings answers; Read for the routines, whose cases stay in range). | Low |
| RD18 | logic.js:3650 | The planning window offers 7 and 14 days, but the preferences allow 1 to 28: any other stored value (10) is shown with no option chosen (Executed, 18 answers). | Low |
| RD19 | logic.js:2962 | A one-off commitment without `kind` counts as work unless its `commitmentType` is CLASS or EMERGENCY, so an older appointment (`commitmentType` APPOINTMENT or anything else) is reported to the rest of LifeMax as an extra shift (Executed, `shifts`: 13 shifts). | Medium for the features that read shifts |
| RD20 | logic.js:2213 | Move lists only the first 48 half hours, so on a free week only the next day or two can be chosen (Executed, `move`). | Low |
| RD21 | logic.js:2213, 22-23 | The move label cuts `fDay` at its first comma: in a locale whose date has no comma the label keeps the whole date (Read). | Informational |
| RD22 | logic.js:1520 | A commitment of a kind `KIND_LABEL` does not name (a hand-edited or future kind) shows its code (`GYM · ...`) (Executed, 32 rows). | Informational |
| RD23 | logic.js:1504 | My time's sleep length is `|| 1440`: a sleep rule whose start equals its end reads as 24 hours (Executed). | Informational |

Native deviations:

- **DV-RM1** Times are written as `en-US` writes them, with a 12-hour clock, whatever the device's locale (`ClockWords`). The page uses the
  device's locale (`fmt(opts)` with `undefined`), so on a device in another locale native's times, dates and hour words differ (OQ-RM5).
  Every recorded case is `en-US`.
- **DV-RM2** An archetype or kind name that is a property of every JavaScript object (`constructor`, `toString`) is looked up by the page
  on the prototype; native looks up own members only (Read; no stored value holds one).

## 15. Golden cases of part b, and the mutation checks

Recorder `lifemax_modular_v73/tools/gen_ld_readmodels_b_golden.js`, as part a's: the controller module with its hook (`ld_logic_vm.js`
`READ_MODELS_B`, with a setter for the load report added beside the state's, in the recorder's copy only) evaluated in the built page in
Chromium, with the page's engine, a host whose `capabilities()` and `milestones()` give the case's answers (`page`: the page's own, its
answers recorded) and a pinned `Date`; every case twice. States are part a's (`tools/ld_readmodels_cases.js` `defineStates`), not repeated
in this file: the web test requires every state a case names to be part a's byte for byte. A plan state, stored by the engine with
`ledger: null`, gets the lived-in day's ledger first (the page always has one), except a few fuzz cases (RD15). Cases:
`tools/ld_readmodels_b_cases.js`. Web test `lifemax_modular_v73/tests/ld-readmodels-b-golden.test.js` (layout, families, rules defined
here, throws only where built, every outcome reached, then re-records every case and the words; `LD_QUICK=1` runs the checks without the
page).

1,601 cases over part a's 432 states (each case records every read model, so every family exercises every rule; the families say what
each varies):

| Family | Cases | What |
|---|---|---|
| seed | 42 | the page's own saved states (fresh, planned, lived, its backup, imported, unfinished, migrated v0) at six moments |
| goals | 213 | paused, re-prioritised and custom goals, repaired and out-of-range shares, warnings, clarifications with and without suggestions |
| quests | 200 | every coverage status, covered or not, 0, 1 and more sessions, deadlines, `@next`, diagnostics naming a quest, reminders, LifeMax answers |
| settings | 146 | every preference, out-of-range and text values, rest tokens, a memory-only load, the lab, a rejected plan, a plan or none |
| time | 144 | sleep rules (week, weekend, none, other ids, crossing midnight), commitments of every kind and day pattern, rotations, routines, set-up |
| forms | 132 | the commitment sheet for every rule (new, rotating, light, with unwind), the goal kinds, every goal form, an activity at a held time, gone and malformed ids |
| move | 142 | sessions on every day, done and moved ones, a session starting soon, ids that are gone |
| shifts | 122 | work rules with valid dates, no title or days, a night shift, one-off shifts of every kind, blackouts over them |
| handles | 140 | the first and later days, drag rules of every kind added, sleep across midnight, rotations, other zones |
| dst | 60 | every plan made across a 2026-2027 clock change, read on the changing day: stops every minute, shifts, move, an activity at a held time |
| host | 60 | LifeMax not connected, partly connected, milestones earned, locked and in progress |
| fuzz | 200 | seeded states, moments, zones and changes from every family |

Native: `PanelsGoldenTest` replays every case (every section equal, the questions asked of LifeMax the same as the page's, the family
counts pinned); `PanelsMutationTest` puts ten plausible porting mistakes in place of one word or one read model and requires the replay
to notice each.

Source mutations of the native code, each compiled and replayed against the whole file:

MUTATION_TABLE

## 16. Open questions for the owner (part b)

- **OQ-RM5** (DV-RM1) Which locale should native write times in once it draws these screens: the device's (and then native needs the
  page's Intl output recorded per locale, or a check in the shadow on the owner's device), or en-US always?
- **OQ-RM6** (RD19) Should a one-off commitment without `kind` count as a shift only when its `commitmentType` says work?
- **OQ-RM7** (RD16, RD18) Should the goal card show the experience words the form offers, and should the planning window offer every
  value the preferences allow (or the preferences keep to 7 and 14)?
- **OQ-RM8** (RD17) Should the grace and routine values be checked and kept in range when the preferences are read?
