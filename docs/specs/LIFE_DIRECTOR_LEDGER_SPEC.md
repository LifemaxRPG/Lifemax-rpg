# Life Director engine specification, part 7: the completion ledger, quest progress and misses

Status: **complete for the ledger** (WP-LD6). Part 7 of the Life Director engine specification (`LIFE_DIRECTOR_ENGINE_SPEC.md` holds parts
1 to 6). It describes the engine's completion ledger, quest and goal progress, misses and their consequences, and the lifecycle transitions
(`engine.js:1904-2085`) as they behave at branch `codex/director-dial-controls`, commit `dc4307b`, and the native code that reproduces them
(`android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector`: `LedgerValues`, `CompletionLedger`, `QuestProgress`,
`MissConsequences`). The page is the authority: this document describes it and changes nothing. It has a file of its own so that it can
be written while part 6 (rescheduling) is written in the engine spec; it uses that file's conventions (sections 0.1 and 0.2 there: numbers,
how cases are compared). Nothing here is wired to a screen; the page is still the only writer of the ledger.

Paths: `engine.js` and `logic.js` are `lifemax_modular_v73/src/features/life-director/engine.js` and `logic.js`; Kotlin files are in
`core/lifedirector` unless named otherwise.

Contents

0. How to read this
1. Boundary
2. The ledger and the value rules (LED-21)
3. Identity: `emptyLedger`, `completionEventId` (LED-1, LED-2)
4. Recording a completion: `recordCompletion` (LED-3 to LED-10)
5. Keeping ids: `compactLedger`, `ledgerHas`, `revokeEvent` (LED-11 to LED-13)
6. Quest and goal progress: `reconcileQuestProgress` (LED-14 to LED-16)
7. Misses and transitions: `detectMisses`, `applyMissConsequence`, `canTransition` (LED-17 to LED-20)
8. The host's flows as the streams replay them (LED-22)
9. Defects (LG01 to LG16) and native deviations (DV-L1 to DV-L3)
10. Golden cases: the index, and the mutation checks
11. Open questions for the owner

---

## 0. How to read this

Rule IDs are `LED-n`; defects are `LGnn` (the engine spec's are `LDnn`, the store's `LSnn`); native deviations are `DV-Ln`. Each rule is:

- **Executed**: the golden file `lifemax_modular_v73/tests/fixtures/ld-ledger-golden.json.gz` holds cases recorded from the real page that
  show it (families or case ids are given), and `LedgerGoldenTest` replays every one natively.
- **Read**: derived from reading the code only.

JavaScript's `undefined` (a member that is not there) and JSON's `null` differ throughout; "truthy" is JavaScript's truthiness (`undefined`,
`null`, `false`, `0`, `-0`, `NaN` and `""` are false). `String(v)` is JavaScript's: numbers as JavaScript writes them, a list as its items
joined by `,`, an object `[object Object]`. Lengths are UTF-16 units. Every rule below is **Executed** unless marked **Read**.

## 1. Boundary

| `engine.js` | What | Native | Rules |
|---|---|---|---|
| 1908-1911 `emptyLedger` | a new ledger | `CompletionLedger.empty` | LED-1 |
| 1916-1920 `completionEventId` | the idempotency key | `CompletionLedger.eventId` (LD1's `EngineHash.completionEventId` is the typed form) | LED-2 |
| 1921 `COMPLETABLE`, 1933-1976 `recordCompletion` | one completion | `CompletionLedger.record`, `Recorded` | LED-3 to LED-10 |
| 1979-1984 `compactLedger` (private) | dropping old bodies | `CompletionLedger.compact` | LED-11 |
| 1985 `ledgerHas` | is an id known | `CompletionLedger.has` | LED-12 |
| 1924-1931 `revokeEvent` | an event undone in the LifeMax log | `CompletionLedger.revoke` | LED-13 |
| 1988-2045 `reconcileQuestProgress` | quests, goals, released sessions | `QuestProgress.reconcile`, `Reconciled` | LED-14 to LED-16 |
| 2048-2050 `detectMisses` | sessions overdue | `MissConsequences.detect` | LED-17 |
| 2052-2076 `applyMissConsequence` | the bounded consequence | `MissConsequences.apply`, `MissOutcome` | LED-18, LED-19 |
| 2078-2084 `TRANSITIONS`, `canTransition` | the lifecycle table | `MissConsequences.canTransition` | LED-20 |

Used from earlier parts, unchanged: `stableId` (LDH-3, `EngineHash.stableId`), `ceilTo15` (LDT-20, `QuarterHours.roundUp`), `deepClone`
(`ScheduleJson.clone`), `clamp` (LDG-1), `tagProfile` (LDD-3, `ActivityTags.profile`), `tagMatches` (LDD-1, `ActivityTags.matches`),
`normalizeQuest` (LDQ-1, `PlayerQuests.of`), `normalizeGoal` and `goalTemplates` (LDD-4 to LDD-7, `PlayerGoals`), `normalizePreferences`
(LDP-2, `PlanPreferences.of`), and for the streams' day key `localParts` and `ymdKey` (LDT-4, LDT-12).

The host's use (Read; it is WP-LD9's): `logic.js:89` creates the ledger at boot; `184` records host log activities ad hoc
(`clientEventId 'host:' + activity id`); `191-199` revokes events whose host record was deleted and puts the session back with the next
revision; `242-262` `processMisses`; `284-305` `afterEvent` (quest and goal progress, with the day key of the event's start in the
player's zone); `308-326` `finishInstance`; `374-390` `logToday` (`clientEventId 'ld-today:' + op`); `2590`, `3450`, `3853` skips. No caller
uses `canTransition` (LG05).

## 2. The ledger and the value rules

The ledger (`ST.ledger`, saved inside the Life Director store, `LIFE_DIRECTOR_STORE_SPEC.md`) is
`{schemaVersion, playerId, events: {id: event}, order: [id], bounties: {}, penalties: {penaltyId: {instanceId, atEpoch, consequence}},
restTokens, completionsSinceToken, consecutiveMisses, consequencesByBwc: {dayId: penaltyId}, regroupActive, streaks: {spacingKey: n}}`, with
`revoked: {id: {reason, atEpoch}}` and `archivedIds: [id]` added when first needed. `bounties` is never written by the engine (Read).
Every function takes the ledger as a value and returns a new one (a `deepClone`, so NaN, the infinities and `-0` become `null` and `0`);
none changes its argument. Unknown members are kept.

- **LED-21** (families `malformed`, `record`, `reconcile`, `miss`, `compact`) The value rules. Reading a member of `null` or `undefined`
  throws `TypeError`; reading one of a primitive gives `undefined` (a text answers `length` and its indexes). Writing a member of a primitive,
  `null` or `undefined` throws `TypeError` (the module is strict). `a + b` joins texts when either side is text (a list or object counts as
  its text: `"2" + 1` is `"21"`), else adds numbers; `-`, `*`, `/`, `++`, `--` read numbers (`Number(v)`); `<`, `<=`, `>=` compare texts
  by UTF-16 units when both sides are text, else numbers, and NaN compares false (LDG-6). `list.includes(x)` matches the same kind and
  value; on a text it finds `String(x)` as a part. `list.concat(x).slice(-500)` on a text joins the texts and keeps the last 500 units.
  `Object.assign({}, v)` copies an object's members, a text's characters and a list's items by index. Lookups by a key that comes from
  data find inherited members of `Object.prototype` (LG03). Native (`LedgerValues`) follows all of this; where it does not, it refuses
  (section 9, DV-L1 to DV-L3).

## 3. Identity

- **LED-1** (family `empty`, 19 cases) `emptyLedger(playerId)`: schema version 1, `playerId` `String(playerId || 'player')` (so `0`,
  `false`, `''` and absent give `'player'`; `[1, 2]` gives `'1,2'`), empty `events`, `order`, `bounties`, `penalties`,
  `consequencesByBwc`, `streaks`, one rest token, both counters 0, no regroup.
- **LED-2** (family `event`, 313 cases; `record` `revision:*`, `instance-id:*`, `adhoc-no-client`) `completionEventId(playerId, instanceId,
  clientEventId, revision)` (LDH-5 of part 1, now over every value): with a truthy `instanceId`, `stableId('ev', 'DONE:' + playerId + ':' +
  instanceId + (revision ? ':rev' + revision : ''))`; else with a truthy `clientEventId`, `stableId('ev', 'ADHOC:' + playerId + ':' +
  clientEventId)`; else it throws `Error('Ad-hoc completions need a clientEventId')`. Values join as `String(v)`: an instance id `0` is
  falsy and goes the ad-hoc way, `'0'` is truthy; a revision of `0`, `''`, `null` adds nothing, `'2'` and `2` give the same id, `true` gives
  `:revtrue`. A session therefore has one event id per revision for ever, and a host activity one id for ever. **This id is the only
  defence against paying twice**: native computes it from the same text with the same hash (LDH-1 to LDH-3), and every id in the file is
  compared.

## 4. Recording a completion

`recordCompletion(ci)`, `ci` = `{ledger, playerId, instance | adhoc + clientEventId, actualStartEpoch, actualDurationMinutes, nowEpoch,
satisfiedRatio, rpe}`. In this order:

- **LED-3** (families `record`, `stream`, `fuzz`, `dst`) The ledger is a clone of `ci.ledger` when truthy, else `emptyLedger(ci.playerId)`.
  The player id of the event and of its id is **the ledger's** `playerId`, not `ci.playerId` (`record/player:*`,
  `stream/ledger-player@*`): once a ledger exists, a change of the host's player id changes no event id. `inst` is `ci.instance` when
  truthy. The result is `{applied, eventId, event, ledger, lifecycleState, residualTask, tokenEarned}`, or for a refusal
  `{applied: false, reason, eventId, ledger: ci.ledger}` (the ledger exactly as given, `undefined` when none was).
- **LED-4** (`record/duplicate*`, `state:*`; streams: 1,066 repeated completions and 238 repeated ad-hoc logs refused) Refusals, in order:
  `DUPLICATE_EVENT_ID` when `ledger.events[eventId]` is truthy (checked before the state, so a repeat of a finished session says
  "Already recorded."), then `NOT_COMPLETABLE_<state>` when there is an instance whose `lifecycleState` is not one of `PLANNED`,
  `IN_PROGRESS`, `MISSED_UNEXPLAINED`, `EXCUSED_BY_TOKEN` (strict text; absent gives `NOT_COMPLETABLE_undefined`). Only event **bodies**
  are consulted, not `archivedIds` (LG01: `record/archived-then-again`).
- **LED-5** (`record/minutes:*`, `ratio:*`, `scheduled:*`) `actualMin = max(1, round(Number(actualDurationMinutes)))` (absent or text that
  is not a number gives NaN, which stays NaN); `schedMin = round((endEpoch - startEpoch) / 60)` with an instance, else `actualMin`;
  `ratio = clamp(actualMin / schedMin, 0, 1)` (a zero-length session gives 1, a negative one 0, NaN stays NaN); `partial` when there is
  an instance and `ratio < (satisfiedRatio || 0.75)` (so 45 of 60 minutes is not partial, 44 is; a ratio of 0 means 0.75, LG07). The host
  passes the preference `partialSatisfiedRatio` (`logic.js:312`); ad-hoc completions are never partial. Lengths are real seconds: a session
  laid out from 01:00 to 03:00 by the wall clock on a spring-forward night is 60 minutes long (family `dst`, `gap@*`).
- **LED-6** (`record/start:*`, `start-falsy:*`) `start = actualStartEpoch || (inst ? inst.startEpoch : nowEpoch)`. State: partial gives
  `PARTIALLY_COMPLETED`; else with an instance, `start + actualMin * 60 <= startEpoch` gives `COMPLETED_EARLY` (finished by the planned
  start), else `start >= endEpoch` gives `COMPLETED_LATE`; else `COMPLETED_ON_TIME` (always for ad hoc).
- **LED-7** (`record/adhoc*`, `effort:*`, `profile:*`, `rpe:*`, `xp:*`) The event, frozen: `eventId`, `playerId`, `instanceId` (the
  instance's, else null), `clientEventId` (null with an instance), and from `src = inst || ci.adhoc || {}`: `activityTypeTag` `tag ||
  'general'`, `templateId` and `spacingKey` (`|| null`), `title` (`|| 'Activity'`), `linkedGoalIds` (a copy; a text stays text),
  `linkedQuestRequirementIds` (each item reduced to `{questId, reqId}`, absent members left out; a null item throws), then
  `actualStartEpoch: start`, `actualDurationMinutes: actualMin`, `scheduledDurationMinutes: schedMin`, `completionRatio: round(ratio *
  100) / 100`, `lifecycleState`, `intensityRating: clamp(round(rpe || max(IP, IC)), 1, 5)`, `recordedAtEpoch: nowEpoch`, `source`
  `SCHEDULE` or `ADHOC`, `suggestedEffort: {minutes, IP, IC, xpHint: round(actualMin * (1 + 0.1 * (max(IP, IC) - 1)))}` where `IP = src.IP ||
  tagProfile(src.tag).IP` and the same for `IC` (an IP of 0 takes the tag's). The engine awards nothing: `xpHint` is a suggestion the host
  may ignore (`logic.js:318` hands the event to the economy).
- **LED-8** (`record/tokens:*`, `refund:*`; streams: 617 tokens earned, 371 spent) After the event is stored and its id appended to
  `order`: `completionsSinceToken++`; at 5 it returns to 0 and, when `restTokens < 3`, one token is added (`tokenEarned`). Then
  `consecutiveMisses = 0` and `regroupActive = false` (any completion ends a regroup). Completing an instance whose state is
  `EXCUSED_BY_TOKEN` refunds a token: `restTokens = min(3, restTokens + 1)` (LG11).
- **LED-9** (`record/streak:*`) When the event has a spacing key and is not partial, `streaks[spacingKey] = (streaks[spacingKey] || 0) + 1`
  (a stored `"2"` becomes `"21"`, LED-21).
- **LED-10** (`record/residual:*`; streams: 864 residual tasks) A partial completion of an instance that carries its task (`source`) and is
  tier 3 or more, or counts for a quest (`linkedQuestRequirementIds.length`), returns a residual task: the task's members, then `id
  stableId('t', 'residual:' + instanceId)`, `title` the **instance's** title plus ` (finish)` (`undefined (finish)` without one), `dOpt`,
  `dMin`, `dMax` all `ceilTo15(schedMin - actualMin)` (at least 15), `tierReason 'RESIDUAL'`, `windowStart nowEpoch`, `parentInstanceId`.
  The host re-plans it (`logic.js:321`, rescheduling, part 6). Last, the ledger is compacted (LED-11).

## 5. Keeping ids

- **LED-11** (family `compact`, 15 cases; `record/compacts-*`, `archived-then-again`) `compactLedger(ledger, now)` (private; run after every
  recorded completion): nothing while `order.length < 2000`. From 2000 ids: `archivedIds` is created if missing (even when nothing moves);
  then, walking `order`, every id whose body is truthy and was recorded strictly before `now - 400 days` loses its body, leaves `order` and
  is appended to `archivedIds`. An id held twice in `order` keeps its second copy (the body is gone by then); an id without a body stays.
  Times compare as LED-21 (text times compare as numbers against the numeric cutoff).
- **LED-12** (family `has`, 25 cases) `ledgerHas(ledger, id)`: a truthy body under `String(id)`, or `id` in `archivedIds` (a list, by
  kind and value; a text, as a part). Inherited names (`toString`) answer true (LG03).
- **LED-13** (family `revoke`, 49 cases; streams: 764 revocations) `revokeEvent(ledger, id, reason, now)`: when the id has a truthy body and
  is not yet in `revoked`, a clone with `revoked[id] = {reason: reason || 'HOST_UNDO', atEpoch: now || 0}` and `revoked: true`; else the
  ledger as given and `revoked: false` (an archived id cannot be revoked). The event, its id, its order place and every count it caused
  stay (LG02). The host then puts the session back with `revision + 1` (`logic.js:199`), whose completion is a new event (LED-2).

## 6. Quest and goal progress

`reconcileQuestProgress(event, quests, goals, futureInstances, opts)` returns `{updatedQuests, updatedGoals, questBountyRewards,
prunedScheduleInstances, removedInstanceIds, questDeltas}`. `satisfied` is `event.completionRatio >= (opts.satisfiedRatio || 0.75)`; the
host passes only `{dayKey}` (LG06).

- **LED-14** (family `reconcile`, `Q` cases) Each quest (a clone of the stored one; `normalizeQuest` LDQ-1 reads it) is left as it is when its
  status is not `ACTIVE` or its `appliedEventIds` holds the event id. Otherwise each requirement matches when the event links it
  (`{questId, reqId}` equal as text) or, for a quest that is not exclusive, when `tagMatches(requirement tag, event tag)`. A matching
  requirement with a minutes target adds the event's minutes (capped at the target); one with a count target adds one when the event is
  satisfied and lasted at least the requirement's `minSessionMinutes`. A quest any requirement of which changed: `appliedEventIds` gets the
  event id (the last 500 kept), the stored requirements get the new counts (`Object.assign` over each stored item) or, for a quest without
  a requirement list, `progressCount` and `progressMinutes`; a delta `{questId, title, eventId, before, after}` is reported; when every
  requirement is met, a quest that is not daily becomes `COMPLETED` with `completedEventId` and a bounty `{bountyId: stableId('bounty',
  'QUEST_BOUNTY:' + id), questId, title, bounty}`, and a daily one gets `completedForDayKey: opts.dayKey || null` (LG13).
- **LED-15** (family `reconcile`, `G` cases) Each goal (a clone) counts the event when `event.linkedGoalIds` holds `String(id || goalId)`, or,
  for an ad-hoc event only, when one of the goal's templates (LDD-6, LDD-7) and the event's tag match either way. Then, unless its
  `appliedEventIds` already holds the id: the id is added (last 500), `sessionsCompleted += satisfied ? 1 : 0`, `minutesCompleted +=
  actualDurationMinutes` (both `(x || 0) + ...`, LED-21), and a target with `unitsPerHour` gets `completed = min(units, (completed || 0) +
  minutes * unitsPerHour / 60)`.
- **LED-16** (`reconcile/prune*`; `stream/release@*`) The quests whose updated status is `COMPLETED` (stored
  ones included, not only those this event completed) release planned sessions: a session still `PLANNED` and not the event's own loses its
  links to them; with no quest link, no goal link and not pinned it leaves the plan (`removedInstanceIds`); with no link left but pinned,
  or with goal links, it stays with the rest of its links (`orphanWarning: true` when no link of any kind is left) and its task's links are
  replaced too. Unchanged sessions are returned as they were given.

## 7. Misses and transitions

- **LED-17** (family `misses`, 51 cases) `detectMisses(instances, now, graceMin)`: the sessions whose state is exactly `PLANNED` and whose
  `endEpoch + (graceMin || 60) * 60 < now` (strict: a session ending at 10:00 is missed from 11:00:01), in their order. A grace of 0 means
  60 (LG07); `IN_PROGRESS` sessions are never missed (LG12). The host takes the first 12 per pass.
- **LED-18** (family `miss`, 394 cases; `stream` and `fuzz` ticks) `applyMissConsequence(ledger, inst, prefs, now, kind)`: the penalty id is
  `stableId('pen', 'MISS_PENALTY:' + instanceId)`; when `penalties` already has it the answer is `ALREADY_PROCESSED` with the ledger as
  given, so a session pays at most once whatever the kind. A kind other than `SKIP` adds one to `consecutiveMisses`; reaching 3 while no
  regroup is active starts one and sets `regroupPrompt` (this miss is still judged below, LG09). The state is `SKIPPED_VOLUNTARY` for a
  skip, else `MISSED_UNEXPLAINED`.
- **LED-19** The consequence, first match wins, each recorded under the penalty id as `{instanceId, atEpoch: now, consequence}`:
  `LENIENT_MODE` when the (normalized) mode is `LENIENT`, the default; `ABOVE_MINIMUM_NO_PENALTY` for `tier <= 2` (an absent tier is not
  `<= 2`, LG10); `TACTICAL_REGROUP_PAUSED` while a regroup is active that did not start with this miss; `ONE_PER_DAY_LIMIT` when the
  session's waking day (`bwcId`) already had one. Otherwise the day is marked, and: with a rest token, it is spent (`REST_TOKEN_SPENT`,
  `tokensLeft`; the state becomes `EXCUSED_BY_TOKEN` for a miss and stays `SKIPPED_VOLUNTARY` for a skip, LG08); without one, the session's
  streak decays by one (floor 0) and the consequence is `HP_DRAIN` (10, floor 1 HP, applied by the host if at all) in `HARDCORE_RPG` and
  `STREAK_DECAY` (the key) in `MODERATE`.
- **LED-20** (family `transition`, 257 cases) `canTransition(from, to)`: `to` is in the list of `from`: `PLANNED` to any of `IN_PROGRESS`,
  the four completions, `SKIPPED_VOLUNTARY`, `MISSED_UNEXPLAINED`, `EXCUSED_DISPLACED`, `EXCUSED_BY_TOKEN`, `CANCELLED_BY_QUEST`;
  `IN_PROGRESS` to the four completions and `EXCUSED_DISPLACED`; `MISSED_UNEXPLAINED` to `COMPLETED_LATE`, `EXCUSED_BY_TOKEN`,
  `PARTIALLY_COMPLETED`; `EXCUSED_BY_TOKEN` to `COMPLETED_LATE`, `PARTIALLY_COMPLETED`; nothing else. `from` is read as `String(from)`
  (`['PLANNED']` works), `to` must be the same text. A `from` named like an `Object.prototype` member throws `TypeError` (LG04).

## 8. The host's flows as the streams replay them

- **LED-22** (families `stream` 135, `dst` 86, `fuzz` 400) A stream is a list of steps on one state (ledger, quests, goals, plan, history),
  replayed by the recorder's `runStream` (`tools/gen_ld_ledger_golden.js`) and natively by `LedgerStream` (test code: it stands for the
  host, WP-LD9): complete a planned session (or repeat one with the copy last kept), log ad hoc with a client id (new or repeated), revoke
  the event of an earlier step, reopen a revoked session with the next revision, skip, tick (detect misses with the preference grace and
  apply up to 12), ask whether an id is known. After a completion the event goes to quests and goals with the day key of its start in the
  stream's zone (`logic.js:287`; zones of the strict matrix, starts around every 2026-2027 clock change in family `dst`). Each step is
  compared with the ledger after it (all but the event bodies, which never change once written and each of which is compared in full in
  the step that wrote it) and the stream's final state in full: 621 streams, 12,737 steps, 4,406 events, 64 player ids.

## 9. Defects and native deviations

Each is behaviour the native code copies faithfully today (the golden cases pin it) unless it is a deviation; "port as is" stands until the
owner rules.

| ID | Where | What | Suggested ruling |
|---|---|---|---|
| LG01 | `engine.js:1938`, 1979-1985 | **A second award.** `recordCompletion` refuses a duplicate by the event **body** only; once the ledger holds 2,000 ids, bodies older than 400 days move to `archivedIds`, and a completion with one of those ids is applied again (`record/archived-then-again`, `duplicate-archived-not-seen`), though `ledgerHas` says the id is known. Reachable only for a session or host activity re-completed more than 400 days later (an old host log entry replayed, a restored backup). | Refuse with `ledgerHas` (one line; page first, new cases) |
| LG02 | 1924-1931; `logic.js:191-199` | **Undo and redo count twice.** Revoking an event changes nothing it caused: `completionsSinceToken` (so tokens), streaks, the regroup reset, goal sessions and minutes, quest progress (the host's quest overlay keeps its own list, `logic.js:104`). The redo is a new revision, a new event, counted again (`stream/repeat@*`: the streak and the goal's sessions go 1, then 2). Deleting and re-logging in the LifeMax log farms rest tokens and streaks. | Reverse the counts on revoke, or carry the revoked event's counts to its redo |
| LG03 | 1938, 1965, 1985, 1925, 2057, 2066, 2072, 468 | Lookups by data keys find `Object.prototype`'s members: `ledgerHas(l, 'toString')` is true, `revokeEvent(l, 'constructor')` is false, a day id `constructor` is always "taken", a spacing key `toString` turns the streak into text, a tag root `constructor` gives a function as the profile. Unreachable: ids are hashes, day ids `bwc-<epoch>`, keys `goal:template`. | Use maps without a prototype, at cutover |
| LG04 | 2084 | `canTransition` throws `TypeError` for a `from` named like an `Object.prototype` member (`transition/canTransition/constructor>*`). | With LG03 |
| LG05 | 1921, 2078-2084 | The transition table is used by nothing, and disagrees with `recordCompletion`: a `MISSED_UNEXPLAINED` or `EXCUSED_BY_TOKEN` session completed with a start before its end is recorded `COMPLETED_ON_TIME` or `COMPLETED_EARLY`, which the table refuses (319 stream steps). | Decide which is the rule; native keeps both |
| LG06 | 1943, 1990; `logic.js:287, 312` | Two thresholds: the ledger's partial state uses the preference `partialSatisfiedRatio`, quest counts and goal sessions use 0.75 (the host passes no ratio to `reconcileQuestProgress`). With 0.5, a 60% session is complete (streak, no residual task) but counts for no quest and no goal session (`stream/partial-ratio@*`). | Pass the preference to both |
| LG07 | 1943, 1990, 2049 | A ratio or grace of 0 cannot be chosen: `|| 0.75` and `|| 60` read 0 as "not set". | Informational |
| LG08 | 2063-2073 | A voluntary skip of a tier-3 session outside lenient mode is treated as a miss in every way but the count of misses in a row: it spends a rest token, or decays the streak, or drains HP in hardcore (`miss/HARDCORE_RPG/SKIP/3/0`). A skipped session cannot be completed later, so the token is never refunded. | Confirm intended |
| LG09 | 2058-2065; `logic.js:263` | The third miss in a row opens the regroup **and** is penalised (the pause starts with the next miss). In lenient mode (the default), misses still count and the regroup banner still opens although nothing is ever penalised. | Confirm intended |
| LG10 | 2064 | A session without a tier is penalised (`undefined <= 2` is false). Sessions always carry one. | Informational |
| LG11 | 1967 | A late completion of an excused session refunds the token but keeps its penalty record and the day's slot, so a second miss that day is still `ONE_PER_DAY_LIMIT`. | Informational |
| LG12 | 2049 | A session started (`IN_PROGRESS`) and never finished is never missed; it stays in the plan until a re-plan. | Decide (a grace from the end, as for planned ones) |
| LG13 | 2012-2015 (Read) | A daily quest that is done keeps its counts at the target and only gets `completedForDayKey`; later events on the same quest stamp it again. Resetting it is the host's (`Host.readQuests`). | Informational |
| LG14 | 1959, 1965, 1928 | A stored ledger whose `events`, `streaks` or `revoked` is a list (never written by the page) takes writes that its JSON drops: the completion is `applied` but not saved, so it would be paid again after a reload; a revocation says `true` and is lost. | DV-L1 (native refuses) |
| LG15 | 1925 | An archived event cannot be revoked (`revoked: false`), so an undo in the LifeMax log of a completion more than 400 days old is ignored. | With LG01 |
| LG16 | 1981-1983 | Compaction creates `archivedIds` even when nothing moves, and leaves the second copy of an id held twice in `order` (`compact/compactLedger/duplicates`). | Informational |

Native deviations: the strict decoder (`LedgerValues`) refuses with `LedgerRefusal` where the page carries on with shapes its own writers
never produce. `LedgerGoldenTest` requires exactly these 8 cases to be refused and every other case to match:

- **DV-L1** A list where the page keeps an object (`events`, `streaks`, `revoked` of a ledger; the map of `ledgerHas`): the page writes
  members onto the list that its JSON then drops (LG14). Cases `malformed/record:ledger.events=[]`, `record:ledger.streaks=[]`,
  `revoke:revoked-list`, `has:events-list`.
- **DV-L2** A quest or goal that is not an object, or a quest list given as text (the page iterates its characters):
  `malformed/reconcile:quests-text`, `quest-text`, `quest-list`, `goal-text`.
- **DV-L3** Inherited names (LG03) wherever the page would use the inherited value as a number, text or profile, and `__proto__` as a key
  written: refused. Truthiness reads (has, revoke, the day slot, the penalty) are reproduced. No case: unreachable by construction.

## 10. Golden cases: the index, and the mutation checks

File `lifemax_modular_v73/tests/fixtures/ld-ledger-golden.json.gz` (2,217 cases; 59 MB unzipped, 3.7 MB zipped). Recorder
`tools/gen_ld_ledger_golden.js` (LD1's method: the engine text with `compactLedger` hooked, `tools/ld_engine_vm.js` `kernelSource`, run inside
the built page in Chromium, node as a second runner; every case run twice and required equal), definitions `tools/ld_ledger_cases.js` (no
`Intl`; the plans whose sessions the streams complete are made by the engine in node). Web test `tests/ld-ledger-golden.test.js` (re-records
every case in Chromium byte for byte, node must agree on every case and does, rule ids defined here, strict zones, only the cases built to
throw do, every outcome reached). Native: `LedgerGoldenTest` (exact replay, family counts, the deviation set), `LedgerHandTest` (13 checks by
hand, with three ids the page gave), adapters `LedgerReplay`, the stream runner `LedgerStream`, under
`android-wrapper/native/src/test/java/com/lifemax/rpg/core/lifedirector`.

| Family | Cases | Function | What it covers | Rules |
|---|---|---|---|---|
| `empty` | 19 | `emptyLedger` | player ids of every kind | LED-1 |
| `event` | 313 | `completionEventId` | 7 player ids x 10 instance ids x client ids x revisions; revisions of every kind; client ids | LED-2 |
| `transition` | 257 | `canTransition` | every pair of the 11 states and 4 odd targets, odd sources, inherited names | LED-20 |
| `record` | 274 | `recordCompletion` | no ledger, player ids, minutes, ratios x thresholds, lengths (zero, negative, fractions), start boundaries, every state, duplicates and archived ids, revisions, instance ids, ad hoc, effort, profiles, rpe, xp hints, tokens, refunds, streaks, residual tasks, compaction | LED-2 to LED-11 |
| `revoke` | 49 | `revokeEvent` | known, unknown, repeated, falsy reasons and times, archived, inherited names, numeric ids | LED-13 |
| `has` | 25 | `ledgerHas` | bodies, archived lists and texts, numeric ids, inherited names | LED-12 |
| `compact` | 15 | `compactLedger` | sizes 0 to 2,100, the cutoff to the second, duplicates, kept and null archives, text times and clocks | LED-11 |
| `reconcile` | 98 | `reconcileQuestProgress` | counts, minutes, both, session minimums, thresholds, links, exclusive quests, requirement lists, statuses, applied ids (and the 500 cap), legacy members, daily quests, bounties; goals by id, `goalId`, number, ad-hoc tag (both ways, language subjects, custom and disabled templates), counters as text, targets; pruning (own session, pinned, orphaned, sources, text goal lists, numeric quest ids) | LED-14 to LED-16 |
| `misses` | 51 | `detectMisses` | 5 clocks x 9 graces, boundaries, text ends and clocks | LED-17 |
| `miss` | 394 | `applyMissConsequence` | 5 modes x 4 kinds x 7 tiers x tokens, then per mode: absent tier, already processed, regroup counters, day slots, absent day ids, tokens of every kind, streaks, clocks, instance ids; a row of five misses | LED-18, LED-19 |
| `stream` | 135 | the host's flows | on 12 engine plans (scenarios in 11 strict zones): repeat-revoke-reopen, tokens earned and spent and refunded, skips and regroups, partial completions, a 0.5 ratio, ad-hoc logs repeated and revoked, the whole plan done, the whole plan missed, player ids, a ledger's own player id; three streams in which a done quest releases its quest-only sessions (one pinned stays as an orphan) | LED-22 and the rest |
| `dst` | 86 | the host's flows | plans starting the morning before each 2026-2027 clock change of the strict zones, four seeded streams each; six streams of sessions laid out by the wall clock across the change | LED-5, LED-6, LED-22 |
| `fuzz` | 400 | the host's flows | seeded streams of 10 to 40 random steps, the three modes, graces, ratios, given ledgers, 9 player ids | all |
| `malformed` | 101 | every function | shapes the page never writes (93 match natively, most of them a `TypeError`) and the 8 deviations | LED-21 |

Mutation checks: 64 source mutations of the native ledger code (`LedgerValues`, `CompletionLedger`, `QuestProgress`, `MissConsequences`; a
script copies the four files, changes one place, compiles and replays the file, of 2,214 cases, before the three `release@*` streams were added). All 64 make the replay fail. Event
identity: the default player `Player` (57 cases), `:r` for `:rev` (141), `Done:` (1,090), a revision of 0 counted (5), the ad-hoc id without
the player (519). Keeping ids: `has` needing body and archive (385), a null revoke reason kept (204), revoking twice (194), keeping 90 days (5),
compacting from 1,999 (2), an inclusive cutoff (3), `record` never compacting (2). Recording: excused sessions not completable (133), a minute
floor of 0 (6), the ratio capped at 2 (236), partial inclusive (66), a threshold of 0.7 (278), the start from the clock (887), early strict
(247), late strict (98), an IP of 0 kept (10), ratings up to 4 (11), an xp step of 0.12 (835), the ratio unrounded (474), a token every 4
(513), tokens up to 4 (47), misses not reset (511), partial completions in the streak (493), no refund (125), residuals from tier 2 (136),
unrounded (418), windowed from the start (461). Progress: remembering 499 (5), links ignoring the requirement (2), exclusive ignored (37),
minutes uncapped (4), counts without `satisfied` (181), no session minimum (3), daily quests completing (115), no day key (114), tag matches
for planned events (49), partial goal sessions (440), units per 30 minutes (383), pinned sessions dropped (7), no orphan warning (6), task links
kept (75), the event's own session pruned (1). Misses: a grace of 30 (18), misses inclusive (6), regroup after 2 (413), free up to tier 3 (497),
the regroup pausing its first miss (200), no daily limit (311), a token at zero (226), skips excused (160), streaks below 0 (200), a drain of 5
(106), skips counting as misses (457), inherited state names allowed (24), missed sessions unable to complete late (57). Values: text
`includes` finding nothing (4), keeping the first 500 ids (3), `+` always numeric (467), inherited members unset (13). The thinnest are
pinned by named cases (the cap of 500 by `applied-cap*`, compaction by `compact/*`, the own session by `reconcile/prune-own`).

## 11. Open questions for the owner

- **OQ-L1** LG01: refuse an archived id in `recordCompletion` (use `ledgerHas`)? A page change first, with new cases; it changes nothing
  for a ledger under 2,000 ids.
- **OQ-L2** LG02: should a revocation take back what the event counted (token progress, streak, goal and quest progress), or should the
  redo inherit them? Today undo-and-redo counts twice.
- **OQ-L3** LG05, LG06: one rule for "done enough" (the preference everywhere) and one rule for which completions are allowed (the
  transition table, or `COMPLETABLE` as it is)?
- **OQ-L4** LG08, LG09, LG12: a voluntary skip costs a token or a streak like a miss; the third miss is penalised as it opens the regroup;
  started sessions are never missed. Intended?
