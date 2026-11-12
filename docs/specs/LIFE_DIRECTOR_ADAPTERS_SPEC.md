# Life Director host adapters: specification (WP-LD11)

Status: **complete for the host surface** (WP-LD11). This document describes `LifeMaxHost`, the page's adapter between Life Director and the
rest of LifeMax (`engine.js:2355-2620`), as it behaves at branch `codex/director-dial-controls`, commit `5669a42`, the two pieces of page code
it reaches through (Smart Diet's meal link, `50-smartdiet.js:211-252`, and the meal reference `logic.js:1076-1081` builds), and the native code
that reproduces it on the native economy and quest board (`android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector/hostadapters`).
The page is the authority: this document describes it and changes nothing. Nothing here is wired to a screen; the page is still the only writer
of the save and of the Life Director store.

Paths: `engine.js` and `logic.js` are `lifemax_modular_v73/src/features/life-director/engine.js` and `logic.js`; `04-runtime.js`, `06-runtime.js`,
`13-runtime.js`, `50-quests-hand.js` and `50-smartdiet.js` are in `lifemax_modular_v73/src/runtime/segments`. Kotlin files are in
`core/lifedirector/hostadapters` unless named otherwise.

Contents

0. How to read this
1. Boundary and the port
2. Words and tags (HA-2 to HA-8)
3. Time zones: which zone dates what (HA-9, HA-11)
4. XP, awards and bounties (HA-10, HA-12 to HA-15, HA-25)
5. Reading the board (HA-16, HA-18, HA-19)
6. The workout plan (HA-17, HA-21)
7. Activities from elsewhere (HA-20)
8. Meals (HA-22, HA-23)
9. Sequences, the golden cases and the mutation checks (HA-1, HA-24)
10. Defects (LA01 to LA12) and native deviations (DV-A1 to DV-A3)
11. Bridging to WP-LD9's host interface
12. Open questions for the owner

---

## 0. How to read this

Rule IDs are `HA-n`; defects are `LAnn` (the engine spec's are `LDnn`, the ledger's `LGnn`, the store's `LSnn`); native deviations are `DV-An`.
Each rule is:

- **Executed**: the golden file `lifemax_modular_v73/tests/fixtures/ld-adapters-golden.json.gz` holds cases recorded from the real page that
  show it (families or case ids are given), and `AdapterGoldenTest` replays every one natively: each answer, the economy the calls leave
  (every save field the recorder compares) and the messages shown.
- **Read**: derived from reading the code only.

JavaScript's `undefined` (a member that is not there) and JSON's `null` differ throughout; "truthy" is JavaScript's truthiness. `String(v)` is
JavaScript's. Every rule below is **Executed** unless marked **Read**.

## 1. Boundary and the port

- **HA-1** (every family) `LifeMaxHost` is the only way Life Director reaches LifeMax: it reads the save's quests, activities and plan, pays XP
  by appending a normal activity and calling the page's `save()`, and pays quest bounties through the quest board's own `toggleQuest`. Native
  `HostPort` (`HostPort.kt`) is that surface without its screen calls (`icon`, `toast`, `openView`, `openActivity`, `openPlanDay`,
  `capabilities` beyond `connected`), implemented by `EconomyHost` (`EconomyHost.kt`) on the native economy (`core/economy`:
  `LifeDirector`, `Quests`, `SavePipeline`; ECONOMY_SPEC 11.5 LD-1 to LD-4) and the save codec (`core/save`). No `HostPort` method throws:
  where the page throws, the answer is `HostAnswer.Refused` with a `HostRefusal` (`UNKNOWN_ZONE` and `INVALID_TIME` where the page throws a
  `RangeError`, `NO_BOARD` where it throws a `TypeError`, `UNREADABLE_QUEST` for DV-A1).

| `engine.js` | Page function | Native | Rules |
|---|---|---|---|
| 2361-2373 | `capabilities`, `connected` | `EconomyHost.connected` | HA-2 |
| 2377-2380 | `playerId` | `EconomyHost.playerId` | HA-2 |
| 2383-2398 | `KIND_TAG`, `PASSIVE_KINDS`, `ATTR_TAG`, `TAG_TYPE`, `TYPE_TAG`, `typeForTag`, `tagForType` | `HostTexts` (typeForTag is LD-1) | HA-3 |
| 2399-2405 | `parseCount`, `parseMinutes` (private, `H._test`) | `HostTexts.parseCount`, `parseMinutes` | HA-4, HA-6 |
| 2406-2415 | `mapQuestTag` | `HostTexts.mapQuestTag` | HA-5 |
| 2418-2423 | `weekBounds` (private, `H._test`) | `QuestReading.weekBounds` | HA-9 |
| 2427-2432 | `EARNED_RE`, `isMilestone`, `isProjectMilestone` | `HostTexts.isMilestone`, `isProjectMilestone` | HA-7 |
| 2433-2436 | `milestones` | `QuestReading.milestones` | HA-18 |
| 2439-2443 | `isUntimedQuest` | `HostTexts.isUntimedQuest` | HA-8 |
| 2445-2452 | `retiredUntimedQuest` | `QuestReading.retired` | HA-19 |
| 2453-2486 | `readQuests` | `QuestReading.readQuests` | HA-16 |
| 2488-2503 | `readPlanSessions` | `PlanReading.sessions` | HA-17 |
| 2508-2521 | `xpFor` | `LifeDirector.xpFor` (LD-2) | HA-10 |
| 2523-2549 | `awardActivity` | `LifeDirector.awardActivity` (LD-3) | HA-12 to HA-14 |
| 2551-2559 | `completeQuest` | `LifeDirector.completeQuest` (LD-4) | HA-15 |
| 2561-2566 | `activitiesSince`, `activityExists` | `EconomyHost` | HA-20 |
| 2567 | `isPlanDayDone` | `PlanDays.isDone` | HA-17 |
| 2569-2593 | `activityDescription` | `PlanReading.describe` | HA-21 |
| `50-smartdiet.js:214-233`, 252 | `sdDirectorDay`, `sdDirectorSlot`, `sdDirectorMeal` (`LifeMaxMeals.resolveScheduled`) | `HostMeals` | HA-22 |
| `logic.js:1076-1081` | `scheduledMealRef`, `scheduledMeal` | `HostMeals.refOf`, `EconomyHost.mealOfBlock` | HA-23 |

Inputs that are not the save (DV-A3): the workout plan's days (`planDay`, `dayDone`, `06-runtime.js:27-43`, a few hundred lines of page code
over the class content) and Smart Diet's days (`sdDay`) are not native yet. `EconomyHost` takes them through two ports, `PlanDays` and
`MealDays`; the recorder logs what the page's own functions answered while each call ran (`output.outside`) and the replay hands exactly those
answers to the ports, failing a case whose adapter asks for a day the page did not. The Daily Hand's library (`QUEST_LIBRARY`) is native
already (`QuestContent.hand.templates`, read from `quest-content.json`).

How the economy is compared: the recorded save (the fresh save of `header.base` with the case's fields) is decoded by the production codec
(`EconomySaveCodec`, `AS_PAGE` reading); after the calls the economy `EconomyHost` holds is encoded back over the same document, and every
field of the recorder's list (the economy golden's `FIELDS`, plus `created`, `name`, `dhMig`, `dailyDraw`) must equal the page's after the
calls; then the toasts, in order.

## 2. Words and tags

- **HA-2** (family `player`; `not-connected` cases throughout) `connected()` is true when the save has a list of activities and a list of quests
  and the page's functions exist (they always do in the built page): native `stored.activities is JsonArray && stored.quests is JsonArray`. When
  not connected: `playerId` is `player`, `readQuests`, `readPlanSessions`, `milestones`, `activitiesSince` are empty, `activityExists` false,
  `activityDescription` empty, `awardActivity` answers `{awarded: false, reason: 'HOST_NOT_CONNECTED'}`, `completeQuest` `{completed: false}`
  (`xpFor` and the meal link do not ask). `playerId()` is `'lm_' + stableId('p', String(created || '') + '|' + String(name || '')).slice(2, 14)`
  (LDH-3 `stableId`). It is read only when the Life Director store is created or reset (`logic.js:87`, `2393`); the store keeps its own copy
  (Read).
- **HA-3** (family `tags`) `typeForTag(tag)`: the first of `fitness.strength strength, fitness.cardio running, fitness strength, language,
  reading, study reading, business, admin.finance business, admin task, wellbeing recovery, nutrition practical, practical, creative practical,
  chores task, social custom` whose prefix is the tag or the tag starts with prefix and `.`, else `custom` (LD-1). `tagForType(type)`:
  `TYPE_TAG[type] || 'general'`. `KIND_TAG`, `ATTR_TAG`, `TYPE_TAG` are the tables at `engine.js:2383-2396`, copied in `HostTexts`.
- **HA-4** (families `tags`, `quests`, `milestones`) The word rules are JavaScript regular expressions with the `i` flag and `\b`, `\w`, `\s`,
  `\d`. Native writes each with explicit classes, because Java's and Android ICU's differ from JavaScript's outside ASCII: a word character is
  `[A-Za-z0-9_]`, `\b` is the boundary between one and not one, `\s` is JavaScript's set (tab, line feed, vertical tab, form feed, carriage
  return, space, U+00A0, U+1680, U+2000-U+200A, U+2028, U+2029, U+202F, U+205F, U+3000, U+FEFF), and `i` is applied by folding `A`-`Z` to lower
  case before matching (a JavaScript `i` pattern of ASCII letters matches only ASCII letters outside `u` mode). `$` and `^` are never used in
  native patterns (Java's `$` also matches before a final line break): whole-text checks are `matches`, suffixes `endsWith`. Cases with a
  no-break space, an em space, the Kelvin sign and accented letters are recorded (`tags/parseCount/59..66`).
- **HA-5** (family `tags`, `mapQuestTag/*`) `mapQuestTag(q)`: `KIND_TAG[q.kind]` when the kind is truthy and in the table; else, on
  `(q.name + ' ' + (q.desc || '')).toLowerCase()` (full Unicode lower case: the Kelvin sign becomes `k`), the first of: `workout|lift|strength`
  `fitness.strength`, `run|cardio|walk` `fitness.cardio`, `read|book|page` `reading`, `study|learn|course` `study`, `spanish|language|vocab`
  `language`, each a plain substring test (so `brunch` is `fitness.cardio`); else `ATTR_TAG[q.attribute] || 'general'`.
- **HA-6** (family `tags`, `parseCount/*`, `parseMinutes/*`) `parseCount(text)`: the first match of a count (digits, or one of `one` to `ten`,
  `once`, `twice`, as a whole word) followed, after up to three other words (fewest first), by one of `session(s) workout(s) times block(s)
  run(s) day(s) lesson(s) chapter(s) walk(s) meal(s)` as a whole word; the count (`+digits`, or the word's number), `0` read as 1, clamped to 1..14;
  1 without a match (`Run twice this week` is 1: `week` is not a noun of the list). `parseMinutes(text)`: the first one to three digits
  followed by spaces or hyphens and `min` or `minute(s)` ending a word (no boundary before the digits: `1234 min` reads `234`), clamped to
  5..240; 0 without one. `text || ''` first.
- **HA-7** (family `tags`, `milestoneKind/*`; family `milestones`) A milestone quest is one of kind `milestone` or cadence `epic`. `isMilestone`:
  a milestone quest whose `name + ' ' + (desc || '')` matches `EARNED_RE` (`engine.js:2427`: words such as `consecutive`, `streak`, `in total`,
  `maintain…`, a number and up to three words before `sessions hours weeks days workouts`, or `ten twenty fifty hundred` and an optional
  `completed` before `volumes sessions hours weeks days`). Never planned. `isProjectMilestone`: a milestone quest that is not that.
- **HA-8** (family `tags`, `untimed/*`) `isUntimedQuest(q)`: `q.hand === true`, `q.cadence === 'daily'`, `q.verify === 'self'`, and the words
  `String(q.name || '') + ' ' + String(q.desc || '')` have no `parseMinutes` and no whole word `minute(s) min(s) hour(s) hr(s) second(s) sec(s)`.
  `hand: 'true'` (text) is not a Hand card.

## 3. Time zones: which zone dates what

- **HA-9** (family `week`; `quests/zones/*`, `quests/dst-week`, `quests/spring-week`) `weekBounds(nowEpoch, tz)`: in the plan's zone `tz`, the
  Monday of `nowEpoch`'s local week (`localParts`, LDT-4; `ymdAdd`, LDT-11), and `start`, `end`, `nextEnd` the instants of local midnight on that
  Monday, the next and the one after (`zonedToEpoch`, LDT-6 to LDT-9, so a week with a clock change is 167 or 169 hours), `monKey` its
  `ymdKey`. An unknown zone, or a `nowEpoch` that is not a time, throws a `RangeError` (`UNKNOWN_ZONE`, `INVALID_TIME`); `readQuests` computes the
  bounds first, so it throws even for an empty board, but not when not connected.
- **HA-11** (families `xp`, `award`, `bounty`, `quests`, `plan`, `meals`, `flow`, `fuzz`; decision D1) The host dates things in two zones,
  and the page's answers depend on both:

  | What | Zone | Source |
  |---|---|---|
  | the day an award is paid on, the daily cap and diminishing returns it counts, its execution bonus's 15-a-day | **device** (`toLocaleDateString('en-CA')` of the session's start) | `engine.js:2530`, `2536-2537` |
  | `xpFor` without a day | device today | `engine.js:2513` |
  | the preview Life Director shows (`xpOf`) | **plan** (the day key of the session's start in `ST.availability.timezone`) | `logic.js:1217` |
  | a quest's period key and whether it is done (`questKey`, `isQuestDone`), a bounty's `PERIOD_CHANGED` check | device (QUEST-1) | `04-runtime.js:87-88` |
  | a weekly quest's window and deadline, next week's copy and its key | plan | `engine.js:2418-2423`, `2474-2478` |
  | plan sessions' dates, windows and keys | plan | `engine.js:2490-2496` |
  | a meal block's day | plan; then checked against the device's local noon of that day (HA-22) | `logic.js:1077`, `50-smartdiet.js:217` |

  Native reproduces both: the economy's clock (`EconomyClock`, the device zone) for the first rows, the kernel (`WallClock`, `ZoneNames`) for the
  plan zone. D1 is ported as the page does it; LA01 lists what follows.

## 4. XP, awards and bounties

- **HA-10** (family `xp`; `flow/preview-then-award`) `xpFor(type, minutes, quality, dateKey, commit)` is LD-2 (ECONOMY_SPEC 11.5): the rule
  `rules[type] || rules.custom`, `Number(minutes) || 0` minutes, quality `quality || 1` (at most 1 under 20 minutes), the duration factor and
  base, then the economy's award on `dateKey || device today` as a preview (`peek`) unless `commit` is truthy, split 70/30 and starred. The
  answer is `{xp, rule, split}` with the whole rule object. With `commit` the rested XP it takes is spent from the held save without a save
  (Read: no caller in `logic.js` passes `commit`; the award does, inside).
- **HA-12** (family `award`; `flow/*`) `awardActivity(event, info)` is LD-3: refused `HOST_NOT_CONNECTED`; `ALREADY_IN_LIFEMAX` with the earlier
  record's id and XP when an activity carries the event's id as `ldEventId` (whatever its own id); else one activity `ld_<eventId>` of type
  `typeForTag(event.activityTypeTag)`, name `info && info.title || event.title`, duration `event.actualDurationMinutes` as given, date the
  start's ISO text, notes `Logged from Life Director` (with ` (partial)` for `PARTIALLY_COMPLETED`), XP scored by `xpFor(…, commit)` on the
  device day of the start (HA-11), recorded even when it pays nothing (D18). Answer `{awarded: true, activityId, xp}`, `xp` without the bonus
  (D20). The start's milliseconds are taken toward zero (`fractional-start`).
- **HA-13** (family `award`, `bonus-*`, `exec-*`, `d1/*`; `flow/three-awards-bonus`) The execution bonus: when the session pays, `round(xp ×
  0.1)` (0.2 with Executive Edge) clamped to 2..5, and at most `15 −` the `ldBonus` of every activity dated (device zone) the same day; added
  to the record's XP, as `ldBonus`, and as a `discipline` share, outside the daily cap (D07).
- **HA-14** (family `award`, `save-fails`, `read-only`; `flow/save-fails-then-ok`) When the save fails the award is taken back whole
  (`takeBack`: no activity, no rested XP spent, no Sparks), with `HOST_SAVE_FAILED`, or `HOST_READ_ONLY` while the save is read-only; the
  page's unsaved message is shown (twice for a full storage: once by `save()`, once by `takeBack`).
- **HA-15** (family `bounty`; `flow/*`) `completeQuest(hostRef)` is LD-4: `{completed: false}` when not connected, without a reference, without
  `questId`, or `future`; `QUEST_GONE` when no quest has that id; `PERIOD_CHANGED` when `questKey(q)` (device) is not `hostRef.key`;
  `ALREADY_DONE`; else `toggleQuest(q.id, true)` (QUEST-2, QUEST-3: the quest XP caps, rested XP, compound, the messages) and `{completed:
  isQuestDone(q), xp: Number(q.xp) || 0, name: q.name}`. A locked quest (`bounty/locked`) is refused by the toggle, so the answer is
  `{completed: false, xp, name}` (LA11). A failed save is not taken back (D13, `bounty/save-fails`).
- **HA-25** (family `verify`) The award's `save()` is followed by the Daily Hand's check of today's cards (`50-quests-hand.js:144-153`,
  SAVE-2): each card not done whose `act:` or `any:` check today's records now meet is paid through `toggleQuest`, inside the same award, with
  its messages. Native: `SavePipeline.save` verifies the cards after the award (no deal: the recorder hides `QUEST_LIBRARY` so the page deals
  none either). Two cards paid in one award get the same record id (LA06).

## 5. Reading the board

The board is the save's `quests` list. Native reads it once, strictly (`BoardQuests`, DV-A1): falsy entries are skipped by every reader as the
page skips them; an entry is otherwise an object whose `id`, `name`, `cadence`, `kind`, `attribute`, `day`, `verify` are text or absent or
`null` (a `null` name refused), `desc` text or falsy, `stage` a whole number or absent; `xp` is `Number(q.xp) || 0` of anything; `hand` is
`=== true`.

- **HA-16** (family `quests`; `flow/*`, `fuzz`) `readQuests(nowEpoch, tz, overlay, revoked)`: empty when not connected; the plan zone's week
  bounds (HA-9); then for each quest with a truthy `id` that is not a milestone (HA-7), in board order:
  - `locked` (CURVE-9 `questLocked`: `(q.stage || 1) > 1` and the character level below the stage's level), `done` (`isQuestDone`, device
    key), `key` (`questKey`), `passive` (kind in `PASSIVE_KINDS` or an untimed card, HA-8), `mins` (`parseMinutes` of the words), the target
    (1 for a daily quest, else `parseCount`), and the count: the overlay's event ids for the key that are not truthy in `revoked`;
  - the shared members: `title` (the name), `tier` (`OPTIONAL` for kind `milestone`, else `CORE`), `tag` (HA-5), `schedulingMode` (`NONE` when
    passive, else `SESSIONS`), `sessionMinutes` and `minSessionMinutes` (`mins`), `bounty: {xp}`, `source: 'lifemax'`;
  - locked: `{id, status: 'LOCKED', requiredCount: target}`;
  - daily: `{id, status: 'ACTIVE', recurrence: 'DAILY', onlyDays, requiredCount: 1, progressCount: done ? 1 : min(1, count), hostRef: {questId,
    key, cadence: 'daily', done}}`, `onlyDays` `null` unless `q.day` is truthy and not `any`, then `[DAYS.indexOf(day)]` without `-1` (so `[]`
    for an unknown day, LA09);
  - weekly: `{id, status: done ? 'COMPLETED' : 'ACTIVE', requiredCount: target, progressCount: done ? target : min(target, count),
    deadlineEpoch: end − 60, hostRef}` and next week's copy `{id: id + '@next', status: 'ACTIVE', requiredCount: target, progressCount: 0,
    windowStartEpoch: end, deadlineEpoch: nextEnd − 60, hostRef: {questId, key: id + '_' + ymdKey(localParts(end + 3600, tz)), cadence:
    'weekly', future: true}}`;
  - any other cadence: `{id, status, requiredCount: once, progressCount, hostRef: {…, cadence: q.cadence || 'once', done}}` with `tier:
    'OPTIONAL'`, `once` 1 for a project milestone, else the target.
- **HA-18** (family `milestones`) `milestones()`: empty when not connected; every quest that `isMilestone` (no `id` check), as `{id, title,
  desc: desc || '', done, locked}` (an absent id or name is left out).
- **HA-19** (family `retired`) `retiredUntimedQuest(ref)`: with `id = String(ref && ref.questId || '')`: null unless `id` starts with `dh_` and
  the library exists and no board quest has `String(q.id) === id`; then the library templates named exactly `ref.questTitle`: exactly one, and
  untimed (HA-8) once given `hand: true, cadence: 'daily'`, gives `{id, title: name, schedulingMode: 'NONE', anchor: 'WAKE'}`, else null. The
  board is read without the connected check, so a save without a quest list throws a `TypeError` (`NO_BOARD`, LA03).

## 6. The workout plan

- **HA-17** (family `plan`; `flow/plan-and-describe`, `fuzz`) `readPlanSessions(nowEpoch, tz, days)`: empty when not connected or the save's
  `plan` is falsy; else, from the plan zone's date of `nowEpoch`, for `i = 0` while `i < days` (JavaScript's comparison: `'3'` is 3, `2.5` gives
  three days, `null` none): the day `planDay(key)` (null when it throws); skipped when null, without a non-empty `items` list, or its
  `String(kind || '')` contains `rest` in any case; `done` is `dayDone(key)` (false when it throws); `{id: 'plan:' + key, title: 'Plan: ' +
  (label || 'Session'), tier: 'CORE', tag: KIND_TAG[kind] || ATTR_TAG[primary] || 'fitness', status, requiredCount: 1, progressCount,
  sessionMinutes: clamp((min || minutes || items.length × 8) | 0, 30, 90), exclusive: true, windowStartEpoch, deadlineEpoch: the plan zone's
  midnights of the day and the next, hostRef: {plan: key, done}, source: 'lifemax-plan'}` (LA02, LA05). `isPlanDayDone(key)` is `!!dayDone(key)`,
  false when it throws, with no connected check. Native: `PlanDays` gives `planDay` as `PlanDay` (kind, label, primary, the first truthy of `min`
  and `minutes`, the item names, the description) and `dayDone`.
- **HA-21** (family `describe`) `activityDescription(inst)`: `''` when not connected or no session; for each link in order, with `id` its
  `String(questId || '')` less a final `@next`: a `plan:` id whose `planDay` exists gives its trimmed `description` when it is non-blank text,
  else its item names (`item` when text, else `item.name || item.title || ''`) that are not empty joined by ` · ` (LA04); then the first board
  quest with `String(q.id) === id` gives its trimmed `desc` when non-blank text; then a `dh_` id gives the trimmed description of the single
  library template named `ref.questTitle || inst.title` with a non-blank description. `''` when no link gives one.

## 7. Activities from elsewhere

- **HA-20** (family `since`; `flow/award-then-quests-then-bounty`) `activitiesSince(sinceIso)`: when connected, every activity that is not
  Life Director's own (`ldEventId` falsy, so an empty one counts as not its own) and not a quest record, and, with a truthy cursor, whose
  `String(date)` is greater than the cursor by UTF-16 units (text, not time: a date written with an offset, `…T11:20:00.000+02:00`, compares by its
  characters with a `Z` cursor); as
  `{id, type, name, minutes: Number(duration) || 0, iso: date, tag, planDate: planDate || null}`, the tag `KIND_TAG[kind] || 'fitness'` for a
  `plan` record and `tagForType(type)` otherwise. `activityExists(id)`: connected and some activity's id is `id`.

## 8. Meals

- **HA-22** (family `meals`) `LifeMaxMeals.resolveScheduled(ref)` (`sdDirectorMeal`): null without a reference; the day: when `ref.day` is
  truthy, `String(ref.day)` if it is four, two and two digits joined by `-` and `new Date(day + 'T12:00:00')` in the device zone shows that same
  date (an impossible date rolls over and fails; a noon a clock change skips moves on with the change, so the missing 2011-12-30 of
  `Pacific/Apia` fails), else null; without a day, the device's local date of `Number(ref.startEpoch)` seconds when that is finite, positive and
  a time (year not padded). The slot: `String(ref.slot || ref.title || '')` trimmed, lower case, each run of white space and hyphens made `_`,
  looked up in `breakfast lunch dinner snack snacks→snack before_training/pre_workout→pre_workout after_training/post_workout→post_workout`.
  Then Smart Diet's day (`sdDay`): its first item whose meal slot is the slot gives `{day, index, slot, name, kcal: round(kcal), logged,
  skipped}` (`round` half up, JavaScript's); null otherwise.
- **HA-23** (family `meals`, `block/*`) A meal block's reference (`logic.js:1076-1078`): `start = mealStartEpoch || startEpoch`, `{title,
  startEpoch: start, day: ymdKey(localParts(start, planZone))}`, so the day is the plan zone's and HA-22's device check then applies to it
  (LA01). An unknown plan zone throws (the page catches it in `scheduledMeal` and shows no meal).

## 9. Sequences, the golden cases and the mutation checks

- **HA-24** (families `flow`, `fuzz`) Calls compose on one held save: what an award or bounty changes (activities, XP caches, rested XP, Sparks,
  `questDone`, the level) is what the next call reads (a bounty after an award, a second award of the same event, quests unlocked by the XP an
  award paid, the day moving on between calls). `EconomyHost` holds the economy's save and replaces it with each outcome, collecting its
  messages; the quest list itself is read once from the stored save (no host call changes it).

Golden file `lifemax_modular_v73/tests/fixtures/ld-adapters-golden.json.gz`, recorder `tools/gen_ld_adapters_golden.js`, cases
`tools/ld_adapters_cases.js`, web test `tests/ld-adapters-golden.test.js` (re-records every case from the built page and requires the file byte
for byte; checks the layout, the families, every cited rule defined here, that `logic.js` still holds the meal reference the recorder copies,
and that the cases reach every answer). Every case is recorded twice on fresh state in the real page in Chromium and must agree. 1,651 cases:

| Family | Cases | What |
|---|---|---|
| `tags` | 571 | typeForTag, tagForType, parseCount, parseMinutes, mapQuestTag, the milestone and untimed tests over 81 texts (non-ASCII spaces and letters among them), every kind, attribute and Hand template |
| `week` | 68 | week bounds in 11 zones around both 2026 clock changes and the year's turn; unknown and lower-case zones |
| `player` | 11 | player ids, connected and not |
| `xp` | 39 | previews and commits, durations, caps, stars, the device-day and plan-day split |
| `award` | 50 | every answer, the bonus, failed and read-only saves, every tag, the device day in five zones, a New York fold |
| `bounty` | 37 | every answer, every cadence, the caps, the device key in four zones |
| `quests` | 219 | the four class packs, the defaults, Hand cards, a mixed board in three plan zones; overlays, revocations, locks, odd values; 72 device-and-plan zone pairs; one quest per text; locked quests with counts |
| `plan` | 71 | the five plans in three zones, day counts, ticked days, four plan zones at three instants, clock changes (Cairo's fall on weekdays, so a plan day of 25 and 23 hours) |
| `milestones` | 23 | every text, every board, done, locked |
| `retired` | 64 | every Hand template, and every reason for none |
| `since` | 40 | cursors, types, plan kinds, existence |
| `describe` | 38 | plan days, quests, retired cards |
| `meals` | 84 | every slot word, impossible days, epochs in three device zones, Apia's missing day, meal blocks in four plan zones, meals logged and skipped on the Food screen |
| `flow` | 12 | sequences (HA-24) |
| `verify` | 24 | the Hand's check after the award, every `act:`/`any:` template |
| `fuzz` | 300 | seeded boards, ledgers, plans and calls in the eight zones of the matrix |

`AdapterGoldenTest` replays every case: answers equal as JSON (numbers by value), every compared save field equal, the toasts equal, and the
four DV-A1 cases refused (pinned). `AdapterHandTest` (14 checks) covers the typed surface, and the rules the shipped content never reaches (a rest day with items, a plan kind
outside the tag table, two Hand templates of one name, a blank template description). `AdapterMutationTest` puts nine plausible mistakes in
front of the port (the award dated in UTC, the week in the device zone, a save that never fails, revocations ignored, a bounty for an old
period, plan days never done, the plan's own records handed back, meal blocks dated on the device, previews dated today) and requires each to
be noticed.

Source mutations: 43 single changes to the adapter's own code (and one change that does nothing, as a control), each compiled with the tests and
run against `AdapterGoldenTest` and `AdapterHandTest`: 42 caught, one survivor, equivalent. Caught: the count's cap, the minutes' floor and cap,
epics not milestones, the untimed test without `verify`, a word dropped from each tag guess, case-sensitive patterns, Java's white space, a tag
table entry, both weekly deadlines, next week's key a day early, a week from Sunday, a weekly quest's done mark ignored, a daily quest's target
read from its words (locked), `any` kept as a day, the tier of other cadences, locks ignored, a project milestone's count, passive kinds
planned, a retired card still on the board, the first of two templates of one name, the plan's floor and minutes an item, rest days with
items planned, the plan's `primary` ignored, a day's end as 24 hours (a 25-hour Cairo day), `@next` kept on a description link, a blank
template description, logged meals skipped, calories floored, the meal's own start ignored, a missing day accepted, the `snacks` slot, an
empty event id read as Life Director's, an inclusive cursor, the plan kind fallback, a short player id, no plan check, connected without a
quest list, `hand` read as truthy. The survivor: `parseCount`'s up-to-three-words filler made greedy; the count captured is the same whichever
way the filler matches, so the answer cannot change.

## 10. Defects and native deviations

| ID | Where | What |
|---|---|---|
| LA01 | HA-11 | Two zones date one session (D1): the preview Life Director shows (`xpOf`, plan zone) and the award (device zone) can fall on different days, so the shown XP is not what is paid near midnight or after travel (`award/d1/*`: a 30-minute read at 03:30 UTC pays 12 in New York, where its device day already holds two reads, while the plan zone's day of the start, which the preview uses, holds one); the 15-a-day bonus counts the device's day; a weekly quest's window is the plan zone's week but its key and done mark the device's week, and next week's key is the plan zone's Monday, so on either side of Monday the two can name different weeks (`quests/zones/*`); a meal block's day is the plan zone's date checked at the device's noon. Executed. |
| LA02 | HA-17 | Plan session titles carry HTML entities: the plan's labels are escaped for the Plan screen (`esc` in `athletePlanDay`), and Life Director escapes them again when it draws them, so `Plan: Foundation Strength B (Hinge &amp; Pull)` is shown with `&amp;` (the title is Executed, the display Read). |
| LA03 | HA-19 | `retiredUntimedQuest` reads the quest list without the connected check and throws without one. Unreachable from `logic.js` (`syncHost` checks first, `logic.js:172`). Executed (`retired/not-connected`). |
| LA04 | HA-21 | A plan session never shows instructions: plan items are `{k, l, d, n}` (no `name` or `title`) and plan days have no `description`, so `activityDescription` gives `''` for every plan day of every class. Executed (`describe/plan-*`). |
| LA05 | HA-17 | A plan session's length ignores the plan's own duration: no plan day has `min` or `minutes`, so it is eight minutes an item, 30 to 90 (`About 45 minutes` in the note, 80 planned for ten items). Executed (`plan/*`). |
| LA06 | HA-25 | Two Daily Hand cards paid by one award's check get the same record id (`quest_` and the same millisecond, economy D17), so undoing one later removes both records. Executed (`verify/strength-25`, `verify/strength-45`). |
| LA07 | HA-20 | An activity without a date passes every cursor (`'undefined'` sorts after digits); `syncHost`'s `seen` set keeps it from being counted twice. Read. |
| LA08 | HA-3, HA-5, HA-17, HA-20, HA-22 | The tag and slot tables are plain objects: a kind, attribute, type or slot word named like an `Object.prototype` member (`constructor`, `toString`) finds a function, which becomes the tag (and is dropped from the JSON) or a slot no meal has. Read for tags; `meals/slot/"constructor"` Executed (null either way). DV-A2. |
| LA09 | HA-16 | A daily quest whose `day` is not one of `sun` to `sat` exactly (`funday`, and so `Mon` or `monday`) gets `onlyDays: []`, which the engine's `normalizeQuest` reads as no limit (LDQ-1, `engine.js:580`): the quest is planned every day instead of on its day. Executed (`quests/mixed/*`). |
| LA10 | HA-2 | The player id is derived from the save's birth time and name; renaming then resetting Life Director gives a new id, so completion event ids made before and after differ (LDH-5). Read. |
| LA11 | HA-15 | A bounty the board refuses for a lock answers `{completed: false, xp, name}`, a shape that looks paid; `afterEvent` reads only `completed` (`logic.js:297`). Executed (`bounty/locked`). |
| LA12 | HA-17 | FIXED in the page and native. The workout plan keeps a day's label as escaped HTML (`label: esc(s.title)`, runtime 14), and `readPlanSessions` used it as text, so Life Director drew "Active Mobility &amp; Structural Recovery". The title is now the plain words: the five entities `esc` writes are undone, `&amp;` last (`plainLabel`; native `PlanReading.plainLabel`). 70 recorded `plan` cases re-recorded, each differing only in the decoded Plan title (checked). Still open: the activity the economy logs when a plan day is finished is named `Plan: <label>` from the same label (`FixedAwards.kt`, page `planToggle`), so a saved activity can carry the escaped text; that name is part of the economy's recorded behaviour and is not changed here. Executed. |

Native deviations:

- **DV-A1** The board reader refuses a quest whose `id`, `name`, `cadence`, `kind`, `attribute`, `day` or `verify` is neither text nor absent
  nor `null` (or `name` is `null`), whose `desc` is truthy and not text, or whose `stage` is not a whole number; the page computes with them
  (`String(7)`, `null + ' '`). The page never writes such quests. Four cases are refused and pinned: `quests/number-id`,
  `retired/board-has-number-id`, `describe/quest-number-desc`, `tags/mapQuestTag/name-null`.
- **DV-A2** Table lookups use own entries only (LA08): a kind or type named like an `Object.prototype` member is unknown natively.
- **DV-A3** The workout plan's days and Smart Diet's days are ports, given the page's answers in the replay; they become native with the plan
  content and Smart Diet ports (section 12).

## 11. Bridging to WP-LD9's host interface

WP-LD9 drives `logic.js` against a recording host whose surface is `connected`, `toast`, `playerId`, `icon`, `readQuests`, `readPlanSessions`,
`awardActivity`, `completeQuest`, `activitiesSince`, `activityExists`, `milestones`, `retiredUntimedQuest` (`tools/ld_host_vm.js` on
`logic/ld9-host`), and defines its own Kotlin interface (`HostEffects.kt`, subpackage `host`). The two meet in one thin adapter, written when
both land, that implements LD9's interface with a `HostPort`:

- each read returns the typed answer's page JSON (`HostQuest.json()`, `PlanSession.json()`, `Milestone.json()`, `RetiredQuest.json()`,
  `HostActivity.json()`, `AwardResult.json()`, `BountyResult.json()`), which is what LD9's goldens record from the stub;
- the arguments are read as the page passes them (an event of the ledger as `AwardEvent`, `{title}` as the info title, `hostRef` as
  `LifeDirector.QuestRef`, `ST.questOverlay` as `QuestOverlay`, the keys of `ST.ledger.revoked` with truthy values as the revoked set, a
  link's `String(questId || '')` as `QuestLink`): `AdapterReplay.run` is that reading and can be moved into the adapter as is;
- a `HostAnswer.Refused` becomes the throw LD9's flow expects (`RangeError` for `UNKNOWN_ZONE` and `INVALID_TIME`, `TypeError` for `NO_BOARD`);
- `toast` and `icon` stay with the screen; `xpOf`'s preview uses `HostPort.xpFor` with `commit` false and the plan zone's day key.
After each command the economy `EconomyHost.save` holds is what the economy save codec encodes, once the logic phase moves saving to native.

## 12. Open questions for the owner

- **D1** (LA01): keep the device zone for awards, bonuses and quest keys (as ported), or date everything by the plan zone (the plan's
  recommendation)? Either way the preview and the payment should use one zone.
- LA02: unescape plan labels before they reach Life Director (and its native projection)?
- LA04, LA05: should plan sessions carry the day's exercises as their instructions and the plan's own duration?
- LA06: give each Hand card its own record id (an economy change, D17)?
- LA09: should a day word the board does not know (`Mon`) be read as that day, or refused, rather than every day?
- DV-A3: the plan's days and Smart Diet's days need native sources before cutover (LIFE_DIRECTOR_NATIVE_PLAN 5.2, item 3); until then the
  native host is fed by the page's projections or stays in shadow.
