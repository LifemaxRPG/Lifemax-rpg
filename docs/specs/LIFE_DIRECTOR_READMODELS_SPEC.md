# Life Director read models: days, day blocks, now and the week

Status: **complete for WP-LD8a** (plan `LIFE_DIRECTOR_NATIVE_PLAN.md`, WP-LD8, part a). It describes how the Life Director page turns a
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
6. Page words as data
7. The native API, and how a shadow check uses it
8. Defects (RD01 to RD14)
9. Golden cases: the index, and the mutation checks
10. Open questions for the owner

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

Not in this package (WP-LD8b and later): the dial's geometry, handles and stops, `nativeToday`'s sheets and chips, goals, quests,
settings, time and forms.

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
RD09). Families `now`, `focus`, `midnight` (a meal and a commitment cut at midnight are joined again).

**RM-12** `nowState(now)`, the blocks (logic.js:1120-1127):
- `cur`: the first session block that is live (not `off`, RM-10) and on now (`start <= now < end`), else the first non-session block on
  now, else none.
- `next`: the first block that is not `cur` (by identity), starts at or after `(cur ? cur.end : now) - 60`, is not free time, is live and is
  not a done session.
- `nextTask`: the first session block, not `cur`, that starts after `now` and is `PLANNED`.
- `isSes`: `cur` is a `PLANNED` session.
Families `now` (every second around the thresholds), `focus`, `seed`, `scenario`, `fuzz`.

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

## 6. Page words as data

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

Native deviations: none. Every one of the 2,563 cases is reproduced, including the throws.

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

432 states, 2,563 cases:

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
| calendar | 42 | the calendar's days off the plan, before, inside and after it |
| host | 31 | LifeMax not connected |
| week | 40 | unplaced work by tier and reason, coverage rounding, downtime minutes |
| fuzz | 300 | seeded moments from two days before to two days after the plan, with seeded zones, timers, states, host and calendar |

Native: `ReadModelsGoldenTest` replays every case (all sections equal, the XP calls in order, the family counts pinned);
`ReadModelsMutationTest` puts eleven plausible porting mistakes in place of one read model or one word and requires the replay to notice
each in the families it should.

Source mutations of the native code, each compiled and replayed against the whole file (MUTATION_RESULTS):

MUTATION_TABLE

## 10. Open questions for the owner

- **OQ-RM1** (RD01) Should the first row be named by its date when it is not today, and `today` be false? Native would follow the page once
  it changes.
- **OQ-RM2** (RD03) Should a paused timer or a timer on another session still let "late" show for the session that is on?
- **OQ-RM3** (RD04) Should an unknown stored zone fall back to the device's zone (and be repaired on load), rather than stop Life Director
  drawing?
- **OQ-RM4** (RD07, RD06) Should Week count only sessions that are planned or done, and count done ones at their actual minutes (and
  preview XP for 0 actual minutes as 0)?
