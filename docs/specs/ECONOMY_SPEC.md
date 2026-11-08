# LifeMax economy specification: XP, rested XP, caps, quests, Sparks, decay

Status: describes the web page exactly as it behaves at commit `6b88d5c` (branch `track/logic-spec`). The page
(`lifemax_modular_v73`) is the authority; this document describes it and changes nothing. Where today's behaviour looks like a
bug or an accident it is marked `[Dnn]` and explained in section 20, but the rule as written here is still what the app does.

Purpose: the logic-phase trial. Someone who never reads the JavaScript should be able to reimplement this area from this
document alone and reproduce, case for case, the golden input/output cases recorded from the real page. Every rule gives its
source as `file:line`. Paths are relative to `lifemax_modular_v73/src/`; `logic.js` means
`features/life-director/logic.js`; a bare segment name such as `43-economy.js` lives in `runtime/segments/`.

Contents

0. How to read this (numbers, rounding, coercion, dates)
1. Boundary: what is in, what is out, and why
2. Inputs and outputs: environment, the activity ledger, save fields
3. Constants
4. Level curves
5. Derived XP: the ledger is the truth
6. Scoring an activity: rules, duration, quality
7. The award pipeline (`ecoAward`)
8. Rested XP
9. The daily cap
10. Attribute split and ascension stars
11. Award paths (Log, quests, health, voice, Life Director, fixed bonuses, uncapped awards) and the path matrix
12. Sparks
13. Perks
14. Ascension
15. Decay, rested accrual and rest days
16. Streak and streak shields
17. Save pipeline, take-back and boot order
18. Undo and removal
19. Numeric hazards (where a careless port diverges)
20. Defects and accidents in today's behaviour
21. Notes for recording golden cases

---

## 0. How to read this

Rules carry IDs (`CAP-2`, `AWARD-1` ...). Golden cases and Kotlin tests should cite them.

### 0.1 Numbers

- **NUM-1** All arithmetic is IEEE-754 binary64 (Kotlin `Double`), evaluated in exactly the order written, with the decimal
  constants exactly as written (`0.7`, `1.1`, `1.15`, `0.5`, `0.05`, `0.1`, `0.2`, `1.5`). Do not rewrite `x * 0.7` as
  `x * 7 / 10`, and do not use `BigDecimal`: the results differ for real inputs (section 19 lists them).
- **NUM-2** `round(v)` is JavaScript `Math.round`: the nearest integer, ties toward positive infinity (`round(2.5) = 3`,
  `round(-2.5) = -2`). Kotlin `Double.roundToInt()` / `roundToLong()` and Java `Math.round` behave the same;
  `kotlin.math.round` (ties to even) does not and must not be used.
- **NUM-3** `num(v)` is JavaScript `Number(v)`: `''` and `null` give 0, `undefined` and non-numeric strings give NaN,
  `true`/`false` give 1/0, numeric strings parse. `num(v) || d` means "d when `num(v)` is 0 or NaN". `num0(v)` is shorthand
  for `num(v) || 0`. `v || d` on a non-number means "d when v is missing, empty, 0, false or NaN".
- **NUM-4** `max`/`min` with a NaN argument give NaN; every comparison with NaN is false. This only matters for malformed
  saves and is noted where it changes an outcome.
- **NUM-5** Nothing truncates. Every XP value produced by these rules is an integer because it comes out of `round`, an
  integer constant, or a sum of those.

### 0.2 Dates and time

- **TIME-1** `now` is the device clock in epoch milliseconds (`Date.now()`).
- **TIME-2** `localDate(t)`: the calendar date of instant `t` in the device time zone, as `YYYY-MM-DD`
  (`toLocaleDateString('en-CA')`).
- **TIME-3** `today = localDate(now)` (`getToday`, `04-runtime.js:84`).
- **TIME-4** `activityDate(a) = localDate(parse(a.date))` (`04-runtime.js:85`). `a.date` is an ISO-8601 UTC string
  (`toISOString()`). A missing or unparseable date gives the string `Invalid Date`, which never equals a real key.
- **TIME-5** Date keys compare as strings; for valid keys this is chronological order.
- **TIME-6** `dayNum(key) = round(Date.UTC(y, m-1, d) / 86400000)`, days since 1970-01-01 (`05-runtime.js:44`);
  `ecoDayKey(n)` is its inverse, the UTC date of `n * 86400000` (`43-economy.js:13`). Pure calendar arithmetic, no zone.
- **TIME-7** `daysAgo(k)`: take `now`, move the local calendar date back `k` days keeping the wall-clock time
  (`d = new Date(); d.setDate(d.getDate() - k)`), then `localDate`. Every "last 7 days" window in this area is
  `[daysAgo(6), today]`, inclusive, compared as strings.
- **TIME-8** `weekMonday`: `now` moved back `(weekday + 6) mod 7` local days, weekday 0 = Sunday, then `localDate`
  (`04-runtime.js:87`, `07-runtime.js:7`). Weeks start on Monday.

### 0.3 Strings

Output messages are part of the contract (they are what the owner sees, and golden cases compare them). They contain these
non-ASCII characters, which must be reproduced exactly: `·` U+00B7 (always as `' · '`, space either side), `★` U+2605,
`’` U+2019 (in `Scholar’s Flow`, `Executive Edge` description, `Life Director’s`), `×` U+00D7.

---

## 1. Boundary

### 1.1 In: specified completely, to be reimplemented

| Area | Why it is in |
|---|---|
| Level curves: attribute, skill and character levels; quest stage locks (section 4) | Every XP number ends up here |
| Derivation of attribute and skill XP from the activity ledger (section 5) | XP is never stored independently; it is rebuilt on every save |
| Activity scoring: rules, duration curve, quality (section 6) | The base of every scored award |
| `ecoAward`: per-type limit, active-time limit, Scholar perk, rested XP, daily cap, messages (section 7) | The shared pipeline for Log, health, voice and Life Director |
| Rested XP: peek, take, accrual (sections 8, 15.5) | Spent by most award paths, earned by decay |
| Daily soft and hard cap (section 9) | Shared by every capped path |
| 70/30 split and ascension stars (section 10) | Applied after the pipeline |
| Each award path's own rule (section 11): Log, quests, health sync, voice past-log, Life Director (`xpFor`, `awardActivity`, execution bonus), nutrition, cat care, money deposits, plan sessions, attribute-sheet catalog, journal coach, dev boosts | Together they are every way XP enters the ledger (every `state.activities.push` in the source) |
| Sparks: earning, balance, spending, shop and reroll prices (section 12) | The economy's currency |
| Perks (section 13), ascension (section 14) | They change XP |
| Decay, rested accrual, rest days and rest tokens (section 15) | Decay lowers attribute XP and is the only source of rested XP |
| Streak and streak shields (section 16) | Owned by `44-rewards.js`; shields are bought with Sparks |
| One-time migration to the v2 economy (`ecoMigrate`, section 17.6) | Sets the character-level bonus and seeds Sparks; runs inside `save()` |
| Order of operations inside `save()`, take-back of a failed save, boot order (section 17) | Decides what an award leaves behind |
| Undo and removal (section 18) | Decides what is and is not reversed |

### 1.2 Contracts only: the trigger is outside, the award rule is inside

| Caller | What stays outside | What this spec covers |
|---|---|---|
| Daily Hand (`50-quests-hand.js`) | Drawing cards, verifying them (`dhVerifyMet`), card art | Cards are ordinary daily quests paid by `toggleQuest`; its auto-verify runs inside every `save()` (17.2); reroll price (12.5) |
| Life Director engine (`logic.js` above line 2339 and the app controller) | Scheduling, ledger, rest tokens of its own, streaks, matching quests to sessions | `LifeMaxHost.xpFor`, `awardActivity`, `completeQuest`, the bounty gate (11.5) |
| Plan tab (`06-runtime.js`, `14-runtime.js`) | Generating a plan day (`planDay`) and checking its items | What completing a plan day pays (11.7.1); `planDay(key).kind === 'rest'` as an input to rest days |
| Health Connect plugin (`android-wrapper/native/.../HealthSyncPlugin.kt`) | Reading records | Which records pay and how much (11.3) |
| Voice understanding (`45-voice.js`, `46-voice-nlu.js`, `50-voice-gen*.js`) | Parsing the sentence | Duration and date defaults, and the award (11.4) |
| Journal coach (`47-journal-coach.js`) | When the welcome-back chips appear, archetypes, patterns | Award amounts and once-only keys (11.7.3) |
| Pet (`15-runtime.js` ...) | Feeding, playing, petting | The cat-care bonus hooked onto them (11.6.2) |
| Money (`05-runtime.js`, `36-money-pay.js`) | Balance, paychecks | Deposit XP (11.6.3) |

### 1.3 Out, and why

- Rendering, toasts' timing, celebrations, level-up screens, badges (`checkBadges`, `BADGES`, `05-runtime.js:14-29`): they
  read the numbers this spec produces and change none of them (badges are written into the save, but no XP rule reads them).
- Titles (`TITLES`, `05-runtime.js:5`), the character radar, the weekly XP bars: presentation of derived values.
- Theme application (`ecoApplyTheme`), the toy catalogue (`PET_TOYS`): only their prices and ownership flags are in.
- The quest catalogue itself (`defaultQuests`, `CLASS_CONTENT`, `QUEST_LIBRARY`) and the activity catalogue (`ACTS`): content,
  not rules. Their XP numbers are inputs.
- Storage mechanics (`localStorage`, backups, imports): the spec assumes a save that loaded; it states only that `writeSave()`
  can fail (17.3).

---

## 2. Inputs and outputs

### 2.1 Environment

| Input | Type | Source |
|---|---|---|
| `now` | epoch ms | device clock |
| time zone | IANA zone | device; decides every `localDate` |
| `ATTRS` | ordered list of 8 attribute ids: `physique, endurance, intelligence, business, social, discipline, adventure, wellbeing`, each with `name`, `short` (`Physique, Endurance, Intelligence, Business, Social, Discipline, Adventure, Wellbeing`) | `00-runtime.js:2-11` |
| `rules` | map from activity type to rule (6.1) | `04-runtime.js:26,33` |

### 2.2 The activity ledger (`state.activities`)

An array of plain objects, in insertion order (order matters: it is the order of counting and of Spark crediting).

| Field | Type | Meaning, who reads it |
|---|---|---|
| `id` | string | unique id (format per path, 11.x) |
| `type` | string | a rules key for scored activities; otherwise `quest`, `steps`, `sleep`, `nutrition`, `catcare`, `money`, `plan`, `catalog`, `coach`. Read by the per-type limit (AWARD-1 step 3), passive test (2.4), health duplicate test |
| `name`, `icon`, `notes` | string | display only |
| `duration` | number, minutes (0 for fixed awards) | active-time limit, Scholar perk, rest-day test, Daily Hand verification |
| `quality` | number | stored only |
| `skill` | string | skill ledger key (trimmed) |
| `primary` | attribute id | fallback split; "trained" for decay |
| `secondary` | attribute id, `''` or absent (absent on quest records) | "trained" for decay |
| `xp` | number | the credited total (after cap, stars and bonus). Read by the cap total, Sparks, skill XP |
| `date` | ISO-8601 UTC string | the activity's moment; its day is `activityDate` |
| `xpSplit` | array of `{attr, xp}` | per-attribute credit; empty or missing means `[{attr: primary, xp}]` |
| `src` | `'health'`, `'voice'`, `'coach'` or absent | health records are excluded from the active-time limit and the manual duplicate test |
| `sp` | `1` or absent | Sparks already credited for this record |
| `epic` | boolean (quest records) | exempt from the daily cap; epic counting |
| `cad` | `'daily'`, `'weekly'`, `'epic'` (quest records) | quest limits |
| `opId` | string (Log) | idempotency of a native Save |
| `ldEventId`, `ldBonus` | string, number (Life Director) | idempotency; execution bonus paid |
| `hc` | string (health) | the Health Connect record key |
| `actId`, `link`, `result` | string (catalog) | catalog limits, plan/catalog exclusion, milestones |
| `kind`, `planDate` | string (plan) | plan/catalog exclusion; undo |
| `detail` | voice detail | display |

### 2.3 Save fields read or written by this area (`state.*`)

| Field | Type | Read by | Written by |
|---|---|---|---|
| `activities` | Activity[] | everything | every award path; removals (18); `ecoSparksSync` and `ecoMigrate` (set `sp`) |
| `attrXP` | `{attrId: number}` | levels, decay, ascension | `rebuildAttrXP` (17.1, load); `applyDecay`; award paths (transient, DERIVE-4) |
| `skillXP` | `{skill: number}` | skill levels | `rebuildSkillXP`; award paths (transient) |
| `restedXP` | number | REST-1, REST-2 | REST-2; DECAY-5 |
| `sparks` | number | `ecoSparks` | `ecoSparksSync`, `ecoSpend`, `ecoMigrate` |
| `perk` | string or null | `ecoPerk` | `ecoBuy` |
| `perksOwned` | string[] | `ecoPerk` | `ecoBuy` |
| `attrStars` | `{attrId: 0..3}` | `ecoStars` | `ecoAscend` |
| `ascended` | `{attrId: number}` | `rebuildAttrXP`, `characterLevel` | `ecoAscend` |
| `decayTotal` | `{attrId: number}` | `rebuildAttrXP` | `applyDecay` |
| `decayApplied` | `{attrId: dayNum}` | `applyDecay` | `applyDecay`, `ecoMigrate` |
| `decayLog` | `[{date, attr, xp}]`, at most 30 | display | `applyDecay` |
| `decay` | `{on, grace, rate}` | `getDecay` | `saveDecay`; `ecoMigrate` (may delete) |
| `legacyCharXPBonus` | number | `characterLevel` | `ecoMigrate` |
| `xpSchemaV2` | boolean | `ecoMigrate` | `ecoMigrate` |
| `customRules` | `{type: Rule}` | load (6.1) | `save()` (`= rules`) |
| `quests` | Quest[]: `{id, name, cadence, xp, attribute?, stage?, kind?, hand?, ...}` | `toggleQuest`, `ecoQuestXP` | quest board, Daily Hand, `createQuest` |
| `questDone` | `{questKey: activityId}` | `isQuestDone` | `toggleQuest`, `deleteActivity` |
| `restDays` | `{date: true}` | rest days, streak | `ecoTakeRest` |
| `restTokens`, `restBoughtAt` | number, epoch ms | `ecoTakeRest`, `ecoBuy` | same |
| `streakShields`, `shieldTick`, `shieldMiss`, `shieldDays`, `shieldMilestone`, `shieldBoughtAt` | number, dayNum, number, `{date: true}`, number, epoch ms | 16.2, `ecoBuy` | same |
| `themesOwned`, `theme`, `toysBought` | string[], string or null, `{toyId: 1}` | shop | shop |
| `nutriDone` | `{date: 1}` | nutrition | nutrition |
| `careDay` | `{day, feed?, play?, pet?, paid?}` | cat care | cat care |
| `mealsMeta.calGoal`, food log (via `dayFoodLog`) | number, entries with `kcal` | nutrition | food area |
| `money` | `{start, tx[], peakBalance, ...}` | deposit XP | `recordMoneyTransaction`, `ecoMigrate` |
| `actXP` | `{catalogId: base}` | catalog | `saveBase` |
| `plan.checks` | per date `{s, v, log, actId}` | plan | `planToggle` |
| `health` | `{on, ex, steps, sleep, wt, seen, since, last, msg, sdk, granted, src, today, wtTime?, wtHist?}` (plus `snap`, display only) | health | health (HEALTH-0 to HEALTH-6) |
| `weight` | number (lb) | display | health weight (HEALTH-6) |
| `devMode` | boolean | dev boosts | settings |
| `dailyDraw` | `{date, nonce, rerollsUsed, cards: [{tpl, id, n}]}` | reroll price, Daily Hand | Daily Hand (SPARK-5) |
| `journalCoach` | `{days: {date: {xpAwarded?}}, reviews: {key: {answer, paid?}}, welcome: {date: {gap, claimed: {pet?, fresh?, line?}}}, cooldown, pats}` | journal coach | journal coach (COACH-1) |
| `dayReflections` | `{date: text}` | journal | catch-up note (COACH-1) |

### 2.4 Passive records

- **PASSIVE-1** `ecoPassive(a)` (`43-economy.js:12`): `a.type` is `steps`, `coach` or `sleep`, or `a.src === 'health'` and
  `a.skill` is `Walking` or `Sleep`. Passive records still pay XP and count toward the cap; they do not count for the streak
  (STREAK-1), for "trained" in decay (DECAY-2), as an active day for shields (SHIELD-1), or as a recovery rest day
  (REST-DAY-1).

---

## 3. Constants

`ECO` (`43-economy.js:6-7`):

| Name | Value | Meaning |
|---|---|---|
| `charBase` | 200 | character level 1 step |
| `charSlope` | 75 | added per character level |
| `softCap` | 180 | daily XP above which awards are halved |
| `hardCap` | 240 | daily XP ceiling |
| `minMin` | 5 | minutes below which a session earns nothing |
| `maxPerType` | 3 | rewarded logs per activity type per day (the third at half) |
| `maxManualMin` | 360 | manual active minutes per day |
| `restedCap` | 300 | rested pool ceiling |
| `restedRate` | 0.5 | share of decayed XP that becomes rested XP |
| `restedBonus` | 0.5 | rested bonus as a share of an award |
| `dailyQuests` | 3 | paid daily quests per day |
| `weeklyQuests` | 3 | paid weekly quests per 7 days |
| `epicPerDay` | 1 | paid epic quests per day |
| `depositMin` | 50 | net new savings that earns XP |
| `depositCapDay` | 25 | deposit XP per day |
| `depositCapWeek` | 50 | deposit XP per 7 days |
| `restPerWeek` | 2 | free rest days per 7 days |

Constants written inline in the code (not in `ECO`):

| Value | Use | Source |
|---|---|---|
| 0.7 | primary share of a two-attribute split | 10.1 |
| 1.1 at 45+ min | Scholar perk | `43-economy.js:45` |
| 1.15 | Compound Habit perk | `43-economy.js:55` |
| 0.05 per star, 3 stars max, level 20 to ascend | ascension | `44-rewards.js:10,20-21` |
| 0.1 / 0.2, 2..5 per session, 15 per day | Life Director execution bonus | `logic.js:2516-2521` |
| 100, 25 | attribute/skill level curve | `04-runtime.js:55` |
| 1000 | level ceiling of the level loop | `04-runtime.js:56` |
| 4, 3 | decay grace days and rate (% of the level step per day) defaults | `05-runtime.js:43` |
| 15 | decay ceiling per attribute per day | `05-runtime.js:48` |
| +2 grace days | Iron Grace perk | `05-runtime.js:47` |
| 7 | days idle before decay feeds rested XP | `05-runtime.js:49` |
| 8, 10 | nutrition and cat-care XP | `44-rewards.js:54,63` |
| 300, 600, 250, 250; toys 150/150/200/300/450 | shop prices | `44-rewards.js:6-7` |
| 50, 1 free per day | Daily Hand reroll | `50-quests-hand.js:5,116` |
| 0/10/20/30 by tier | Daily Hand card XP | `50-quests-hand.js:62` |
| 2 | maximum streak shields | `44-rewards.js:42,92` |

---

## 4. Level curves

- **CURVE-1** Attribute and skill level step (`xpForLevel`, `04-runtime.js:55`): `need(L) = 100 + 25 * (L - 1)`
  (L1 100, L2 125, L10 325, L20 575).
- **CURVE-2** `levelData(total, step = need)` (`04-runtime.js:56`): `level = 1; left = max(0, total)`;
  while `left >= step(level)` and `level < 1000`: `left -= step(level); level += 1`. Returns
  `{level, into: left, needed: step(level), pct: min(100, left / step(level) * 100)}`. Reaching a threshold exactly moves up
  (`levelData(100)` is level 2, into 0; `levelData(99)` is level 1, into 99). At level 1000 the loop stops, `into` may exceed
  `needed` and `pct` is clamped. A NaN total gives level 1, into NaN.
- **CURVE-3** Character level step (`charXpForLevel`, `43-economy.js:9`): `charNeed(L) = 200 + 75 * (L - 1)`.
- **CURVE-4** `charCumXP(L) = 37.5*L*L + 87.5*L - 125` (`43-economy.js:10`): the XP needed to reach character level L,
  equal to the sum of `charNeed(1..L-1)` (L1 0, L2 200, L3 475, L5 1250, L10 4500). Used only by the migration (17.6).
- **CURVE-5** Character level (`characterLevel`, `04-runtime.js:58`):
  `levelData(totalXP + (legacyCharXPBonus || 0) + sum(values(ascended || {})), charNeed)`, where `totalXP` is DERIVE-3.
  Ascended XP keeps counting toward the character after the attribute restarts (section 14).
- **CURVE-6** Attribute level (`attrLevel`, `04-runtime.js:83`): `levelData(attrXP(id))` with
  `attrXP(id) = num(state.attrXP[id] || 0)` (`04-runtime.js:59`).
- **CURVE-7** Skill level: `levelData(skillXP[name])` with CURVE-1; only skills with XP > 0 are listed, highest first
  (`15-runtime.js:48-52`, `50-native-character.js:34-37`).
- **CURVE-8** Dev boost amount (`xpToGainLevels(total, n)`, `04-runtime.js:272-278`): `d = levelData(max(0, num0(total)))`;
  `need = max(0, d.needed - d.into)`; `lvl = d.level + 1`; repeat `n - 1` times: `need += need(lvl); lvl += 1`; return
  `max(1, need)`.
- **CURVE-9** Quest stage lock (`13-runtime.js:272-273`): `stageLvl(s) = [0, 1, 3, 5, 8, 12][s] || 1`;
  `questLocked(q) = (q.stage || 1) > 1 && characterLevel().level < stageLvl(q.stage)`. So stages 2 to 5 need character levels
  3, 5, 8 and 12; a missing stage, stage 0 or 1, and any stage of 6 or more (the table has no entry, so the level is 1) never
  lock.

---

## 5. Derived XP: the ledger is the truth

Attribute and skill XP are caches of the ledger. Every `save()` rebuilds them (17.1); `load()` rebuilds them when the ledger is
non-empty (`04-runtime.js:25`).

- **DERIVE-1** `rebuildAttrXP` (`04-runtime.js:60-73`):
  1. `next = {id: 0}` for each of the 8 ATTRS ids.
  2. For each activity in ledger order: `splits = a.xpSplit` if it is truthy and has a non-zero length, else
     `[{attr: a.primary, xp: a.xp || 0}]`. For each split `z`: if `z` is truthy and `z.attr` is one of the 8 ids,
     `next[z.attr] += max(0, num0(z.xp))`; otherwise the split is ignored. (The second branch at `04-runtime.js:68` can
     never run [D32].)
  3. For each id: `next[id] = max(0, next[id] - max(0, num0(decayTotal[id])) - max(0, num0(ascended[id])))`.
  4. `state.attrXP = next` (exactly the 8 ids, in ATTRS order).
- **DERIVE-2** `rebuildSkillXP` (`04-runtime.js:74-81`): `next = {}`; for each activity, `sk = (a.skill || '').trim()`;
  skip when empty; `next[sk] = (next[sk] || 0) + max(0, num0(a.xp))`. `state.skillXP = next` (keys in order of first
  appearance). A record's whole `xp` goes to its skill, including stars and the Life Director bonus; quest records go to
  `Quest Completion`.
- **DERIVE-3** `totalXP = sum(values(state.attrXP))` (`04-runtime.js:57`).
- **DERIVE-4** Award paths also add to `state.attrXP` and `state.skillXP` in memory before they call `save()`, and removals
  subtract; `save()` replaces both with DERIVE-1/2, so these in-memory edits never survive and need not be reproduced
  [D15]. `applyDecay` writes `attrXP` directly, but always together with a matching `decayTotal` increase, so the next
  rebuild gives the same number.

---

## 6. Scoring an activity

### 6.1 Rules

- **RULES-1** `rules = {...copy(DEFAULT_RULES), ...state.customRules}` at load (`04-runtime.js:26`, `copy` a deep copy, so the
  defaults themselves never change): a shallow merge per type, so a stored entry replaces the default entry whole, and a stored
  key that is not a default type becomes a type of its own. With no save, `rules = copy(DEFAULT_RULES)` (`04-runtime.js:33`).
  `save()` writes `state.customRules = rules` (`04-runtime.js:44`). `saveRules` sets each type's
  `base = min(200, max(1, num(v) || 1))` in place (`0` gives 1), saves and toasts `'XP rules saved'` (`04-runtime.js:329`);
  `resetRules` restores a fresh copy of the defaults, saves and toasts `'Default XP rules restored'` (`04-runtime.js:330`); the
  migration may rewrite three fields (MIG-4).
  A rule is `{name, icon, base, skill, primary, secondary, desc}`.
- **RULES-2** Defaults (`00-runtime.js:12-22`):

| type | name | base | skill | primary | secondary |
|---|---|---|---|---|---|
| reading | Focused reading / study | 24 | Knowledge | intelligence | discipline |
| business | Focused business work | 25 | Entrepreneurship | business | discipline |
| strength | Strength workout | 28 | Lifting | physique | discipline |
| running | Run / cardio | 26 | Running | endurance | wellbeing |
| language | Language practice | 24 | Spanish | intelligence | social |
| recovery | Sleep / recovery routine | 16 | Recovery Routine | wellbeing | endurance |
| practical | Practical skill practice | 24 | Practical Skills | adventure | intelligence |
| task | Difficult planned task | 20 | Execution | discipline | business |
| custom | Custom activity | 20 | Custom Skill | adventure | (empty) |

Icons: book, briefcase, dumbbell, heart, chat, moon, mountain, target, star (same order).

### 6.2 Duration curve

- **DUR-1** `ecoDur(dur)` (`43-economy.js:16`): `d = num0(dur)`; `d < 5` gives 0; `d <= 60` gives `d / 30`; otherwise
  `min(3, 2 + (d - 60) / 60)`. Continuous: 5 min 0.1667, 15 min 0.5, 30 min 1, 60 min 2, 90 min 2.5, 120 min and more 3.
- **DUR-2** The same curve in words (`ECO_DURATION_RULES`, `43-economy.js:18`):
  `30 min = base XP, up to 3× at 120 min. Under 5 min earns nothing.`

### 6.3 Preview of a Log draft

- **PREV-1** `activityPreview(d)` (`04-runtime.js:257`):
  - `r = rules[d.type] || rules.custom`
  - `dur = max(0, num0(d.duration))`; `quality = num(d.quality) || 1`
  - `durFactor = ecoDur(dur)`; `q = dur < 20 ? min(1, quality) : quality`
  - `total = max(0, round((r.base * durFactor) * q))`
  - `primary = d.primary || r.primary`; `secondary = d.secondary || ''` (no fallback to the rule's secondary [D23]);
    `skill = (d.skill || r.skill).trim() || r.skill`
  - returns `{total, primary, secondary, skill, durFactor}`.

  Under 20 minutes a quality above 1 is ignored; a quality below 1 always applies. The form offers 0.8, 1, 1.1, 1.2
  (`views/log.html:8`, `50-native-log.js:4-9`). Examples: reading 15 min at 1.2 gives 12; at 0.8 gives round(9.6) = 10.

---

## 7. The award pipeline (`ecoAward`)

`ecoAward(type, total, dur, o)` (`43-economy.js:36-49`); `o.day` (date key, default today), `o.manual` (boolean),
`o.peek` (boolean). Returns `{xp, msg}`. Every scored award (Log, health, voice, Life Director) goes through it.

- **AWARD-1** Steps, in this order:
  1. `day = o.day || today`.
  2. If not `total > 0` (this includes NaN): return `{xp: 0, msg: 'Add at least 5 minutes to earn XP'}`.
  3. `acts` = ledger records with `activityDate(a) === day`, in ledger order.
     `same` = number of `acts` with `a.type === type`, whatever their source or XP (0-XP records count).
  4. `factor = same < 2 ? 1 : (same == 2 ? 0.5 : 0)`. If `factor` is 0: return
     `{xp: 0, msg: 'Daily XP limit reached for this activity type'}`.
  5. If `o.manual`: `mins` = sum of `num0(a.duration)` over `acts` with `a.type !== 'quest'` and `a.src !== 'health'`.
     If `mins + num0(dur) > 360`: return `{xp: 0, msg: 'Daily active-time limit (6 hours) reached'}`.
  6. `why = factor < 1 ? ['Last of this type today: half XP'] : []`.
  7. `x = max(1, round(total * factor))`.
  8. Scholar's Flow: if `type` is `reading` or `language`, `num0(dur) >= 45` and `ecoPerk('scholar')`:
     `s = round(x * 1.1)`; if `s > x` append `'Scholar’s Flow +' + (s - x)`; `x = s`.
  9. `r = restedPeek(x)` (REST-1); `t = dayXP(day, acts)` (CAP-1); `paid = cap(r, t)` (CAP-2). If `r > x` append
     `'Rested +' + (r - x)`.
  10. If `paid > 0` and not `o.peek`: `restedTake(x)` (REST-2). The pool loses the whole bonus computed from `x`, even when
      the cap let only part of it through [D05]. When `paid` is 0 the pool is untouched (since commit `1550306`).
  11. `x = paid`. If `0 < x < r`: append `t + x < 240 ? 'XP above 180 a day is halved' : 'Reaches the daily XP cap'`.
  12. Return `x > 0 ? {xp: x, msg: why.join(' · ')} : {xp: 0, msg: 'Daily XP cap reached'}`.
- **AWARD-2** Refusals are checked in the order total, per-type, active time: a fourth reading log reports the per-type limit
  even when the time limit is also exceeded.
- **AWARD-3** `o.day` changes which day's records are counted (steps 3, 5, 9); the rested pool is global and is spent the
  same whatever the day.
- **AWARD-4** `peek` changes nothing but step 10: a peek returns exactly what the commit would pay.
- **AWARD-5** The active-time total counts every non-quest, non-health record of the day, including Life Director sessions,
  voice logs and catalog entries; only callers passing `manual` (Log, voice) are subject to it.

Worked examples (verified in a sandbox against the page's own functions):

| State | Call | Result | Pool after |
|---|---|---|---|
| empty day, pool 0 | reading, total 24, dur 30, manual | `{24, ''}` | 0 |
| a business record of 230 XP today, pool 100 | reading 24 / 30 min | `{10, 'Rested +12 · Reaches the daily XP cap'}` | 88 |
| a business record of 240 XP today, pool 100 | reading 24 / 30 min | `{0, 'Daily XP cap reached'}` | 100 |
| a business record of 170 XP today | reading 24 / 30 min | `{17, 'XP above 180 a day is halved'}` | |
| two reading records today | reading, total 25 | `{13, 'Last of this type today: half XP'}` | |
| a business record of 150 today, pool 40, Scholar equipped | reading 45 min, quality 1.2 (total 43) | `{51, 'Scholar’s Flow +4 · Rested +24 · XP above 180 a day is halved'}` | 16 |

---

## 8. Rested XP

- **REST-1** `restedPeek(x)` (`ecoRestedPeek`, `43-economy.js:22`): `pool = num0(state.restedXP)`; if `pool > 0` and
  `x > 0`, return `x + min(pool, round(x * 0.5))`; else return `x`. Writes nothing.
- **REST-2** `restedTake(x)` (`ecoRestedTake`, `43-economy.js:23-26`): the same; when a bonus `b` applies,
  `state.restedXP = pool - b` and it returns `x + b`. When `pool <= 0` or `x <= 0` it returns `x` and writes nothing
  (it does not normalise a missing or negative pool).
- **REST-3** Where the pool is spent:

| Site | Order | Source |
|---|---|---|
| `ecoAward`, commit | peek, cap, take(x) only if the cap paid something | `43-economy.js:46-47` |
| `ecoQuestXP`, daily and weekly | peek, cap, take(base) only if paid | `43-economy.js:60` |
| `ecoQuestXP`, epic | take(base); no cap (epics are exempt) | `43-economy.js:54` |
| Nutrition bonus | take(8), then cap | `44-rewards.js:54` [D03] |
| Cat-care bonus | take(10), then cap | `44-rewards.js:63` [D03] |
| Money deposit | take(xp), then cap | `05-runtime.js:173` [D03] |

  Plan sessions, catalog entries, journal-coach awards, the Life Director execution bonus, dev boosts and stars never touch
  the pool. Health sync spends it on passive data too (steps and sleep go through `ecoAward`) [D34].
- **REST-4** The pool is filled only by decay (DECAY-5) and is capped at 300 there. It is shown as `+50%` with the rounded
  pool (`44-rewards.js:135`, `05-runtime.js:62`).

---

## 9. The daily cap

- **CAP-1** `dayXP(day, acts)` (`ecoDayXP`, `43-economy.js:20`) = sum over the day's records of
  `a.epic ? 0 : num0(a.xp)`. Every source counts (passive, coach, plan, catalog, quest, the Life Director bonus, stars)
  except epic quest records. Negative values are not clamped [D33].
- **CAP-2** `cap(x, t)` (`ecoCap`, `43-economy.js:28-33`), `t` the day's XP so far:
  1. If `x <= 0` or `t >= 240`: 0.
  2. `out = x`. If `t + x > 180`: `over = t + x - max(180, t)`; `out = x - over + round(over * 0.5)`.
  3. Return `max(0, min(out, 240 - t))`.

| t \ x | 1 | 10 | 30 | 61 | 100 |
|---|---|---|---|---|---|
| 0 | 1 | 10 | 30 | 61 | 100 |
| 100 | 1 | 10 | 30 | 61 | 90 |
| 179 | 1 | 6 | 16 | 31 | 51 |
| 180 | 1 | 5 | 15 | 31 | 50 |
| 200 | 1 | 5 | 15 | 31 | 40 |
| 239 | 1 | 1 | 1 | 1 | 1 |
| 240 | 0 | 0 | 0 | 0 | 0 |

  An award of 1 above the soft cap is not reduced (`round(0.5) = 1`), and odd overflows round up.

---

## 10. Attribute split and ascension stars

- **SPLIT-1** A two-attribute award gives the primary `round(xp * 0.7)` and the secondary `xp - round(xp * 0.7)`. It applies
  when a secondary exists and differs from the primary (plan sessions check only that a secondary exists, PLAN-1; no plan day
  has the same attribute twice). Where the secondary comes from: Log, the draft (PREV-1); health,
  voice and Life Director, the rule (or the call); nutrition wellbeing/physique; cat care wellbeing/social; money
  business/discipline; plan, the plan day. Single-attribute awards: quests (`q.attribute || 'discipline'`), steps
  (endurance), sleep (wellbeing), catalog (the sheet's attribute), journal coach (wellbeing).
- **STAR-1** `stars(attr) = (state.attrStars || {})[attr] || 0` (`44-rewards.js:9`);
  `starXP(attr, xp) = round(xp * (1 + 0.05 * stars(attr)))` (`44-rewards.js:10`), the factor `1 + 0.05 * stars` computed
  first. Applied to each part after the split, and after the cap [D06]. Applied by Log, health, voice and Life Director
  `xpFor`; not by quests, nutrition, cat care, money, plan, catalog, coach or the Life Director bonus.
- **LOGAWARD-1** `activityAward(d, peek)` (`04-runtime.js:259`), the Log award:
  1. `x = activityPreview(d)`. If `x.total` is falsy: `{xp: 0, split: [], msg: 'Log at least 5 minutes to earn XP'}`.
  2. `ec = ecoAward(d.type, x.total, num0(d.duration), {manual: true, peek})` (day = today). If `ec.xp <= 0`:
     `{xp: 0, split: [], msg: ec.msg}`.
  3. `p = x.primary`; `s = x.secondary !== p ? x.secondary : ''`; `pXP = s ? round(ec.xp * 0.7) : ec.xp`.
  4. `parts = [{attr: p, xp: pXP}]`, plus `{attr: s, xp: ec.xp - pXP}` when `s`.
  5. `why = ec.msg ? [ec.msg] : []`. For each part in order: `v = starXP(attr, xp)`; if `v > xp` append
     `short + ' ' + '★' repeated stars(attr) times + ' +' + (v - xp)`, where `short` is the ATTRS short name, or the id when
     unknown. The part becomes `{attr, xp: v}`.
  6. Return `{xp: sum of parts, split: parts, msg: why.join(' · ')}`.

  Example: the last row of the section 7 table, with Intelligence at 1 star and secondary discipline: `ec.xp = 51` splits
  36/15, the star makes it 38/15, result
  `{53, [{intelligence, 38}, {discipline, 15}], 'Scholar’s Flow +4 · Rested +24 · XP above 180 a day is halved · Intelligence ★ +2'}`.

---

## 11. Award paths

### 11.1 Log (`recordActivity`)

- **LOG-1** The draft: `{type, customName, duration, quality, skill, primary, secondary, notes, opId?}`. The web form gives
  strings (`activityDraftFromForm`, `04-runtime.js:256`). The native Log screen normalises first (`clean`,
  `50-native-log.js:33-48`): fields the draft lacks are first taken from the preset draft of its type (`draftFor`, the
  `reading` preset when the type is missing or not a rules key: name and notes `''`, duration `'30'`, quality `'1'`, the rule's
  skill, primary and secondary); then `type` must be a rules key, else `custom`; `customName` cut to 80 characters; `duration` becomes
  `String(min(1440, max(0, round(num0(duration)))))`; `quality` must be one of `'0.8' '1' '1.1' '1.2'`, else `'1'`; `skill`
  cut to 60; `primary` must be an ATTRS id, else the rule's; `secondary` must be `''` or an ATTRS id, else `''`; `notes` cut to
  2000. The web form does none of this.
  The native Save (`commands.submit({draft, operationId})`, `50-native-log.js:89-109`): cleans the draft; `opId =
  String(operationId || '')` cut to 80; an empty one returns `{ok: false, message: 'Could not save: missing operation id'}`
  and records nothing. Otherwise `res = recordActivity({...cleaned, opId})` (LOG-2). When `res.ok` is false it returns
  `{ok: false, message: res.message}` (no toast of its own); otherwise it toasts `res.message` and returns
  `{ok: true, message: res.message, xp: res.xp, navigate: 'dashboard'}`, a replayed `opId` included (the `duplicate` flag is
  not passed on).
- **LOG-2** `recordActivity(d)` (`04-runtime.js:263`), in order:
  1. If `d.opId` is truthy and a record with `a.opId === d.opId` exists: return
     `{ok: true, xp: that.xp, message: 'Activity saved · +' + that.xp + ' XP', duplicate: true}`. Nothing changes. The
     comparison is strict and records store `String(d.opId)`, so only a string `opId` (as the native Log sends) is ever matched.
  2. `was` = a JSON copy of the whole state.
  3. `r = rules[d.type] || rules.custom`; `x = activityPreview(d)`; `award = activityAward(d)` (commit: may spend rested XP).
  4. If `award.xp` is 0: return `{ok: false, message: award.msg}` (nothing was spent: AWARD-1 step 10).
  5. Record:
     `{id: random UUID (or String(now) without crypto), opId: String(d.opId) when given, type: d.type,
     name: d.type === 'custom' ? (trimmed customName || 'Custom activity') : r.name, icon: r.icon,
     duration: num0(d.duration), quality: num(d.quality) [D24], skill: x.skill, primary: x.primary, secondary: x.secondary,
     xp: award.xp, date: now as ISO, notes: trimmed String(d.notes || ''), xpSplit: award.split}`.
     An unknown type keeps its raw `type` but takes `custom`'s rule, so its name is `Custom activity`.
  6. Push it; update caches (DERIVE-4); `save()` (17.1, which credits Sparks and may auto-complete Daily Hand cards).
  7. If `save()` returned false: `takeBack(was)` (17.4) and return `{ok: false, message: unsavedMessage()}`.
  8. Return `{ok: true, xp: award.xp, message: 'Activity saved · +' + award.xp + ' XP'}`.
- **LOG-3** The preview a Log screen shows is `activityAward(d, true)` (`04-runtime.js:260`, `50-native-log.js:50-54`): the
  same function, peeking, so it always equals what Save pays at that moment.

### 11.2 Quests

- **QUEST-1** `questKey(q)` (`04-runtime.js:87`): daily `q.id + '_' + today`; weekly `q.id + '_' + weekMonday`; anything else
  (epic) `q.id + '_once'`. `isQuestDone(q) = !!questDone[questKey(q)]` (`04-runtime.js:88`).
- **QUEST-2** `toggleQuest(id, checked)` (`04-runtime.js:265`):
  1. `q` = the quest with that id; none: return (no save).
  2. `A = q.attribute || 'discipline'`.
  3. If `checked` and `questLocked(q)` (CURVE-9): toast `'Locked until you reach level ' + stageLvl(q.stage)`, then
     `renderQuests()`, then return without saving here. `renderQuests()` runs the Daily Hand's `dhAutoVerify()`
     (`14-runtime.js:35`), which can complete cards whose condition is met and so save (QUESTS_SPEC QST-55).
  4. `key = questKey(q)`.
  5. If `checked` and `questDone[key]` is falsy:
     1. `qid = 'quest_' + now` [D17]; `questDone[key] = qid` (set before the XP is computed).
     2. `xp = ecoQuestXP(q)` (QUEST-3; may spend rested XP).
     3. If `xp` is 0 and `num0(q.xp) > 0`: toast `'One epic quest reward per day'` for epics, else
        `'Quest XP limit reached for ' + (weekly ? 'this week' : 'today')` (also shown when it was the daily XP cap [D20]).
     4. Push `{id: qid, cad: q.cadence, epic: q.cadence === 'epic', skill: 'Quest Completion', type: 'quest',
        name: 'Quest: ' + q.name, icon: 'flag', duration: 0, xp, primary: A, date: now as ISO,
        notes: the quest card's trimmed note field or 'Quest completed', xpSplit: [{attr: A, xp}]}`. A record is pushed
        even when `xp` is 0, and it counts toward later quest limits.
     5. If `xp > 0`: toast `'Quest complete · +' + xp + ' XP'`.
  6. Else if not `checked` and `questDone[key]`: delete `questDone[key]`, remove the record with id equal to its value, toast
     `'Quest undone · XP removed'` (the in-memory subtraction of `q.xp` is overwritten by the rebuild [D15]).
  7. `save()` in every case that reaches here, even when nothing changed [D16]. The result is not checked: a failed save is
     not taken back [D13].
- **QUEST-3** `ecoQuestXP(q)` (`43-economy.js:51-61`), always a commit (there is no peek):
  1. `base = max(0, num0(q.xp))`; `cad = q.cadence`; `day = today`; `qs` = all ledger records with `type === 'quest'`.
  2. Epic: if the number of `qs` with `a.epic` and `activityDate(a) === day` is at least 1, return 0; else return
     `restedTake(base)` (no cap; epic records are excluded from the cap total).
  3. Daily with `ecoPerk('compound')`: `base = round(base * 1.15)`.
  4. `lim = 3` (weekly or any other cadence); `since = weekly ? daysAgo(6) : day` [D09].
  5. `n` = number of `qs` with `a.cad === cad` and `activityDate(a) >= since` (paid or not).
  6. If `n >= lim`: return 0.
  7. `paid = cap(restedPeek(base), dayXP(today))`; if `paid > 0`: `restedTake(base)`. Return `paid`.

  Examples: daily quest of 50 with Compound Habit and a pool of 10: base `round(57.49999...) = 57`, peek 67, paid 67, pool 0.
  Epic of 75 with a pool of 100: 113, pool 62.
- **QUEST-4** Quest XP values: custom quests `min(500, max(1, num(input) || 25))` (`04-runtime.js:268`), so an empty or `0`
  input gives 25 and a negative or fractional one below 1 gives 1. `createQuest` refuses an empty (trimmed) name with
  `'Give your quest a name'`; otherwise it pushes `{id: 'custom_' + now, name, desc: 'Custom quest created by you.', cadence
  (the form's), xp, kind: 'custom'}`, saves and toasts `'Quest created'`. Daily Hand cards are daily quests worth 0/10/20/30 by
  tier (`50-quests-hand.js:62,91`) and share the 3-a-day limit with every other daily.
- **QUEST-5** Callers of `toggleQuest`: the quest board (`qosToggle`, `14-runtime.js:2-5`), the Daily Hand's auto-verify,
  which runs inside every `save()` (17.2), voice (`50-voice-gen5.js:32,188,669`) and Life Director (11.5).

### 11.3 Health sync

- **HEALTH-1** `lmaHealthAward(o)` (`39-health.js:16-29`):
  1. `r = o.rule || rules[o.type] || rules.custom`; `day = localDate(o.at)`.
  2. `total = o.xp != null ? o.xp : max(0, round(r.base * ecoDur(o.duration)))` (no quality).
  3. `ec = ecoAward(o.type, total, o.duration, {day})` (not manual, commit). If `ec.xp <= 0`: return 0, nothing recorded.
  4. `p = o.primary || r.primary`; `sec = o.secondary === undefined ? r.secondary : o.secondary`. When `sec` is set and
     differs from `p`: `pXP = round(xp * 0.7)`, `sXP = xp - pXP`; else `pXP = xp`, `sXP = 0`. Stars on each part present.
     `xpT = pXP + sXP`.
  5. Push `{id: UUID, type: o.type, name: o.name, icon: r.icon, duration: o.duration || 0, quality: 1,
     skill: o.skill || r.skill, primary: p, secondary: sec, xp: xpT, date: ISO of o.at, notes: o.note || '',
     xpSplit: [{p, pXP}] plus [{sec, sXP}] when split, src: 'health', hc: o.hc}`.
  6. Returns `ec.xp`, the amount before stars, and adds that (not `xpT`) to the skill cache [D11].
- **HEALTH-0** A sync runs only in the Android app (the `HealthSync` plugin), when sync is on or the owner asked, and not while
  another sync runs (`39-health.js:30-38`). It first stores the plugin's status: `H.sdk = status.sdk`,
  `H.granted = status.granted || []`. It stops early, without awards, when Health Connect is unavailable, or when at least one
  wanted record type is not granted and none is (`want` = the types switched on: `ex` exercise, `steps`, `sleep`, `wt` weight).
  So a partial grant syncs the granted types only, and a sync with every type switched off runs through and reads nothing.
  Workouts are handled before steps (steps use the workouts just read), then sleep, then weight; each only when switched on and
  granted. If anything in it throws (the status or a read), the sync stops there: `H.msg = 'Sync failed: ' + message` (first
  100 characters), no summary, no toast, no pruning, `H.last` unchanged; what it already awarded and marked seen stays, and the
  `finally` save stores it.
- **HEALTH-2** Windows (`39-health.js:40-41`): `from = max(H.since || 0, now - 3 days)`. `H.since` is set to
  `now - 2 days` when unset (also when sync is switched on, `39-health.js:112`) and is never advanced afterwards [D01].
- **HEALTH-3** Workouts (`39-health.js:42-57`), only when `H.ex` and `exercise` is granted. Sessions read for
  `[now - 7 days, now]`, handled in the order returned. For each: `key = 'x' + id`, `min = round((end - start) / 60000)`;
  skip when `seen[key]`, `end > now`, `min < 5`, or `start < from`. `kind`: type string matching
  `/yoga|pilates|stretch|breathing|meditat|tai_chi/` is `recovery`, else matching
  `/weightlift|strength|calisthenics|boot_camp|gymnastic|martial|boxing|climb/` is `strength`, else `running` (Health Connect
  type names such as `walking`, `running`, `biking`; unknown types arrive as `other_workout`). If any record with
  `src !== 'health'`, the same `type`, and `|parse(a.date) - start| <= 2 h` exists: `seen[key] = end`, no award. Otherwise
  award `{type: kind, name: (title || nice(type)) + (origin ? ' · ' + appName(origin) : ''), duration: min, at: start, hc: key,
  skill: kind === 'running' ? nice(type with the first of _treadmill|_stationary|_machine|_open_water|_pool removed) : rule skill,
  note: 'Imported from Health Connect'}` and set `seen[key] = end` whatever it paid; when the session has an `origin`,
  `H.src = appName(origin)`. `nice(t)`: underscores to spaces, each word capitalised, `High Intensity Interval Training` to
  `HIIT` (a missing type is the string `undefined`, so `Undefined`). `appName`: Samsung Health, Google Fit, Fitbit, Garmin for
  their packages, else the last package segment. A `walking` workout gets skill `Walking` and so counts as passive [D10]. The
  by-hand test compares the hand-logged record's time (when it was saved, usually after the workout) with the workout's start
  [D37].
- **HEALTH-4** Steps (`39-health.js:58-73`), only when `H.steps` and `steps` is granted. Days read for the last 30 days.
  `sinceDay = localDate(H.since || from)`. For each day: today's count is only stored (`H.today`); skip when `seen['p'+date]`,
  `count < 2000` or `date < sinceDay`. `dayEx` = workouts from the exercise read (empty when workouts are off) whose
  `localDate(start)` is that day; `cardio` = sum of `max(0, (end - start) / 60000)` over `dayEx` that are neither recovery nor
  strength. `eff = count`; if `eff > 35000` and `dayEx` is empty, `eff = 8000`; `eff -= round(cardio) * 100`.
  `seen['p'+date] = parse(date + 'T23:59:00')` (local). If `eff < 4000`, no award. Else award `{type: 'steps',
  rule: {icon: 'target', skill: 'Walking', primary: 'endurance', secondary: ''}, name: 'Daily steps: ' + count (locale
  grouping), duration: 0, at: date + 'T20:00:00' (local), skill: 'Walking', primary: 'endurance', secondary: '',
  xp: min(15, round((eff - 2000) / 1000 * 1.5)), note: 'Imported from Health Connect'}`, so 3 to 15 XP.
- **HEALTH-5** Sleep (`39-health.js:74-93`), only when `H.sleep` and `sleep` is granted. Sessions read for the last 8 days.
  `best[d]` = the longest session with `end <= now` among those ending on local day `d`, whether seen, shorter than 3 hours or
  before the window; of two equally long ones the first returned stays. For each session:
  `key = 'z' + id`, `hrs = (end - start) / 3600000`; skip when seen, `end > now`, `hrs < 3`, or `end < from`; then
  `seen[key] = end`; skip unless it is `best` of its end day. `busy` = some workout read in this sync (none when workouts are off
  or not granted) with `start < sleepEnd - 30 min` and `end > sleepStart + 30 min`, or some non-health record whose time is
  strictly inside `(sleepStart + 30 min, sleepEnd - 30 min)`.
  `pts = (hrs < 5.5 or hrs > 12 or busy) ? 0 : hrs < 6.5 ? 8 : hrs < 7.5 ? 12 : hrs <= 9.5 ? 15 : 12`. When `pts > 0` award
  `{type: 'sleep', rule: {icon: 'moon', skill: 'Sleep', primary: 'wellbeing', secondary: ''}, name: 'Sleep: ' + h + 'h ' +
  two-digit m + 'm' (h = floor(hrs), m = round((hrs - h) * 60) in doubles, section 19; m can be 60 [D36]), duration: 0, at: end,
  skill: 'Sleep', primary: 'wellbeing', secondary: '', xp: pts, note: 'Imported from Health Connect'}`.
- **HEALTH-6** Weight pays nothing (`39-health.js:94-96`), only when `H.wt` and `weight` is granted: the latest reading of the
  last 60 days, `{kg, time}`; when `kg` is set, `lb = round(kg * 2.20462 * 10) / 10`, and when `time > (H.wtTime || 0)`:
  `H.wtTime = time`, `H.wtHist` gains `{t: time, lb}` (the last 60 kept), and when `state.weight !== lb`, `state.weight = lb` and
  the summary reports it. At the end `seen` entries older than `now - 21 days` are deleted (`39-health.js:98`)
  [D01], `H.last = now`, and `save()` runs in `finally` with no take-back (`39-health.js:104`). The summary
  (`39-health.js:100-102`): `bits` lists, when non-zero, `n workout` / `n workouts`, `n day of steps` / `n days of steps`,
  `n night of sleep` / `n nights of sleep` (a record counts once it reached `lmaHealthAward`, even when that paid 0; a
  workout skipped as already logged by hand does not), and `weight W lb`; `xp` is the sum of what `lmaHealthAward` returned
  (before stars, D11). `H.msg = 'Added ' + bits.join(', ') + (xp ? ' (+' + xp + ' XP)' : '') + '.'`, or `'Up to date.'`
  when `bits` is empty. When `bits` is non-empty or the sync was asked for, toast
  `'Health sync: ' + (bits ? bits.join(', ') + (xp ? ' · +' + xp + ' XP' : '') : 'nothing new')`. The early stops of
  HEALTH-0 set `H.msg` to `'Health Connect needs an update from the Play Store.'` (status `update`),
  `'Health Connect is not available on this phone.'` (any other status) or `'Allow access to start syncing.'`, and still save;
  without the plugin a sync that was asked for toasts `'Health sync works in the Android app'`, and neither that nor a sync
  that is switched off and was not asked for saves.
- **HEALTH-7** Triggers (outside): every 30 minutes, on resume after 15 minutes, 14 s after start (`39-health.js:130-135`).
- **HEALTH-8** The settings text promises different rules from these [D12].

### 11.4 Voice past-log

- **VOICE-1** `lmvLogXp(type, dur, day, peek)` (`49-voice-time.js:110-113`): `r = rules[type] || rules.custom`;
  `total = max(0, round(r.base * ecoDur(dur)))` (no quality); `ecoAward(type, total, dur, {manual: true, day, peek: !!peek})`.
- **VOICE-2** `lmvLogNow(o)` (`49-voice-time.js:115-125`): `ec = lmvLogXp(o.type, o.duration, o.day, false)`; if
  `ec.xp <= 0` return `{ok: false, msg: ec.msg || 'No XP for that one'}`. Split by the rule (`s && s !== p`), stars per part;
  push `{id: UUID, type: o.type, name: o.name, icon: r.icon, duration: o.duration, quality: 1, skill: r.skill, primary: r.primary,
  secondary: r.secondary, xp, date: ISO of o.atMs, notes: 'Logged by voice', xpSplit, src: 'voice'}`; `save()` with its result
  ignored and exceptions swallowed [D13]; toast `'Activity saved · +' + xp + ' XP'`; return `{ok: true, xp, id}`.
  `50-voice-gen5.js:68-74` wraps it: it may replace the name, sets `detail` and `notes = 'Logged by voice · ' + summary`, and
  records an undo entry.
- **VOICE-3** Inputs decided by `lmvHLog` (`49-voice-time.js:126-149`): type = the first of `LMV_TYPES`
  (`49-voice-time.js:100-108`) whose pattern matches, else `custom`. `dur` = the extracted minutes; missing or under 5 becomes
  30; over 480 is refused. The day is the explicit scope's day or today; a range or a future day is refused. The confirmation
  shows `'About +' + ec.xp + ' XP'` from a peek, before stars [D20].
  `lmvActType(s)` (`49-voice-time.js:109`) tests the (already normalised, lower-case) sentence against these patterns in order;
  each is a whole-word alternation (`\b(...)\b`), and `noun` is the first match. No match gives
  `{type: 'custom', name: 'Custom activity', noun: ''}`.

| # | type | name | words |
|---|---|---|---|
| 1 | running | Run / cardio | run, ran, running, jog, jogged, jogging, cardio, treadmill, cycle, cycled, cycling, bike, biked, swim, swam, swimming, hike, hiked, hiking, walk, walked, walking, elliptical, rowing |
| 2 | strength | Strength workout | weight, weights, lift, lifted, lifting, gym, strength, workout, worked out, pushup, push-up, push up (and plurals), pullup, pull-up, pull up (and plurals), squat, squats, bench, exercise, exercised, trained, training, crossfit |
| 3 | reading | Focused reading / study | read, reading, study, studied, studying, homework, course |
| 4 | language | Language practice | spanish, french, german, italian, japanese, language, duolingo |
| 5 | recovery | Sleep / recovery routine | yoga, stretch, stretched, stretching, `meditat` followed by any word characters, nap, napped, sleep, recovery, mobility |
| 6 | business | Focused business work | business, client, invoice, proposal, email, emails, deep work |
| 7 | practical | Practical skill practice | cook, cooked, clean, cleaned, repair, fixed, garden, gardening, woodwork, practice, practiced, laundry, chore, chores |

  The record's name: for `custom` always `Activity` (the noun is empty); for a noun among walk/walked/walking, yoga,
  swim/swam/swimming, hike/hiked/hiking, cycle/cycled/cycling, bike/biked, jog/jogged/jogging the names Walk, Yoga, Swim, Hike,
  Cycle, Bike ride, Jog; otherwise the pattern's name. The time `atMs`: today with no part of the day named, now (minus
  `min(dur, 90)` minutes when said as earlier); otherwise the named part of the day (morning 9:00, afternoon 14:00, evening 19:00,
  night or "last night" 21:00) or, for another day with none named, 12:00, in the device zone; a time still to come (today, or
  more than a minute ahead on any day) becomes one minute ago.

### 11.5 Life Director

- **LD-1** `typeForTag(tag)` (`logic.js:2391-2394`): the first pair in this list where `tag === prefix` or `tag` starts with
  `prefix + '.'` gives the type, else `custom`: `fitness.strength` strength, `fitness.cardio` running, `fitness` strength,
  `language` language, `reading` reading, `study` reading, `business` business, `admin.finance` business, `admin` task,
  `wellbeing` recovery, `nutrition` practical, `practical` practical, `creative` practical, `chores` task, `social` custom.
- **LD-2** `xpFor(type, minutes, quality, dateKey, commit)` (`logic.js:2490-2503`; its comment describes an old formula
  [D19]): `R = rules[type] || rules.custom`; `dur = max(0, num0(minutes))`; `q = dur < 20 ? min(1, quality || 1) :
  (quality || 1)`; `total = max(0, round(R.base * ecoDur(dur) * q))`; `day = dateKey || today`;
  `ec = total > 0 ? ecoAward(type, total, dur, {day, peek: !commit}) : {xp: 0}` (not manual). Split by `R` (`s && s !== p`),
  stars per part, `xp = pXP + sXP`. Returns `{xp, rule: R, split}`.
- **LD-3** `awardActivity(event, info)` (`logic.js:2505-2531`):
  1. Not connected: `{awarded: false, reason: 'HOST_NOT_CONNECTED'}`. Connected (`logic.js:2358-2370`) means
     `state.activities` and `state.quests` are both arrays and the host functions `save`, `questKey`, `isQuestDone`,
     `toggleQuest` and the `rules` table exist; in practice only a save whose `quests` is not an array is not connected.
  2. A record with `ldEventId === event.eventId` exists: `{awarded: false, reason: 'ALREADY_IN_LIFEMAX', activityId, xp}`.
  3. `type = typeForTag(event.activityTypeTag)`; `when = event.actualStartEpoch * 1000`; `was` = JSON copy of the state.
  4. `x = xpFor(type, event.actualDurationMinutes, 1, localDate(when), true)` (commit: may spend rested XP).
  5. Record `{id: 'ld_' + eventId, ldEventId, type, name: info.title || event.title, icon: R.icon,
     duration: event.actualDurationMinutes, quality: 1, skill: R.skill, primary: R.primary, secondary: R.secondary || '',
     xp: x.xp, date: ISO of when, notes: 'Logged from Life Director' + (' (partial)' when the event is PARTIALLY_COMPLETED),
     xpSplit: x.split}`.
  6. Execution bonus, only when `x.xp > 0`: `used` = sum of `a.ldBonus` over records whose day is `localDate(when)`;
     `b = min(max(0, 15 - used), max(2, min(5, round(x.xp * (ecoPerk('exec') ? 0.2 : 0.1)))))`. If `b > 0`:
     `xp += b`, `ldBonus = b`, append `{attr: 'discipline', xp: b}` to the split. Not capped, no stars, no rested [D07].
  7. Push the record, even when `x.xp` is 0 [D18]; `saved = save()` (an exception counts as false).
  8. Not saved: `takeBack(was)`; return `{awarded: false, reason: saveReadOnly ? 'HOST_READ_ONLY' : 'HOST_SAVE_FAILED'}`.
  9. Return `{awarded: true, activityId, xp: x.xp}`, the amount without the bonus [D20].
- **LD-4** Quest bounty: `completeQuest(hostRef)` (`logic.js:2533-2541`) refuses with `{completed: false}` and no reason when
  the host is not connected, `hostRef` or its `questId` is missing, or `hostRef.future` is set; then with a reason when the quest
  is gone (`QUEST_GONE`), its `questKey` is no longer `hostRef.key` (`PERIOD_CHANGED`), or it is done (`ALREADY_DONE`); otherwise
  `toggleQuest(id, true)` and returns `{completed: isQuestDone(q) after the toggle, xp: num0(q.xp), name: q.name}`: the quest's
  listed XP, not what was paid [D20], and `completed: false` for a locked quest (toggleQuest refused it with its toast,
  QUEST-2 step 3). After an award whose save failed (`HOST_SAVE_FAILED` or `HOST_READ_ONLY`, `logic.js:2802`), no bounty is
  paid for that event (`logic.js:2818,2838,2906`, since commit `1550306`). Activities logged elsewhere complete matching Life
  Director blocks without XP (`logic.js:2733`), but that path may still pay bounties.
- **LD-5** Life Director previews call `xpFor` without `commit` (`logic.js:3653-3654`).

### 11.6 Fixed bonuses through the cap

#### 11.6.1 Nutrition (`ecoNutritionCheck`, `44-rewards.js:47-58`)

**NUTRI-1** Triggered 50 ms after `addMealEntry` (`44-rewards.js:147`), which nothing calls any more [D02].
1. `goal = max(0, num0(mealsMeta.calGoal))`; 0: stop. `day = today`; `nutriDone` is created as `{}` when missing (so even a
   check that pays nothing can add it); `nutriDone[day]`: stop.
2. `kcal` = sum of `num0(e.kcal)` over today's food log. If `kcal < goal * 0.85` or `kcal > goal * 1.15`: stop. Both bounds
   are binary doubles (section 19: `1500 * 1.15` is `1724.9999999999998`, so 1,725 kcal misses a goal of 1,500).
3. `nutriDone[day] = 1`; `xp = cap(restedTake(8), dayXP(today))` [D03]; if `xp <= 0`: stop, without saving [D04].
4. Push `{id: 'nut_' + now, type: 'nutrition', name: 'Nutrition goal hit', icon: 'heart', duration: 0, skill: 'Nutrition',
   primary: 'wellbeing', secondary: 'physique', xp, date, notes: kcal + ' kcal against a goal of ' + goal,
   xpSplit: [{wellbeing, round(xp * 0.7)}, {physique, xp - that}]}`; `save()`; toast `'Nutrition goal hit · +' + xp + ' XP'`.

#### 11.6.2 Cat care (`ecoCare(kind)`, `44-rewards.js:60-66`)

**CARE-1** Triggered after `petFeed`, `petPlayDone` and `petPetDone` return `ok` (`44-rewards.js:144-146`; `petPetDone` and
`petPlayDone` return `ok` even when the interaction no longer counts).
1. `c = state.careDay`; if missing or `c.day !== today`, `c = state.careDay = {day: today}`.
2. `c[kind] = 1`. Unless `feed`, `play` and `pet` are all set and `paid` is not: stop (no save here).
3. `c.paid = 1`; `xp = cap(restedTake(10), dayXP(today))` [D03]; if `xp <= 0`: stop, without saving [D04].
4. Push `{id: 'care_' + now, type: 'catcare', name: 'Cat care: fed, played, petted', icon: 'paw', duration: 0,
   skill: 'Cat Care', primary: 'wellbeing', secondary: 'social', xp, date, notes: '', xpSplit: [{wellbeing, round(xp * 0.7)},
   {social, xp - that}]}`; `save()`; toast `'Cat care complete · +' + xp + ' XP'`.

#### 11.6.3 Money deposit (`recordMoneyTransaction`, `05-runtime.js:164-176`)

**MONEY-1** `recordMoneyTransaction(type, amount, note)`:

1. `amt = round(num(amount) * 100) / 100`; not finite, `<= 0` or `> 1e12`: `{ok: false, 'Enter a valid amount'}`. Type
   other than deposit/withdraw: `'Choose a transaction type'`. Withdrawal above the balance: `'Not enough in the account'`.
2. For a deposit: `bal = moneyBalance()` (`round((num0(start) + sum(deposit ? amount : -amount)) * 100) / 100`,
   `05-runtime.js:162`); `peak = max(num0(peakBalance), bal)`; `net = max(0, bal + amt - peak)`;
   `peakBalance = max(peak, bal + amt)` (updated whether or not XP is paid).
3. `weekXP` = sum of `num0(xp)` over `money` records with day `>= daysAgo(6)`. `doneToday` = a `money` record today, or a
   record with `link === 'business.transfer-money-into-savings'` today.
4. If not `doneToday` and `net >= 50`: `xp = min(25, max(5, round(net / 10)), max(0, 50 - weekXP))`; then
   `xp = xp > 0 ? cap(restedTake(xp), dayXP(today)) : 0` [D03].
   Every sum and difference here is a binary double in this order (section 19: on a balance of 3.01 a deposit of 125 nets
   `124.99999999999999` and pays 12, not 13), and `peakBalance` is stored as computed (`0.01 + 50.01` is `50.019999999999996`).
5. If `xp > 0`: push `{id: 'act_' + now, type: 'money', name: 'Deposit ' + currency(amt), icon: 'coin', duration: 0,
   skill: 'Saving', primary: 'business', secondary: 'discipline', xp, date, notes: note, xpSplit: [{business, round(xp * 0.7)},
   {discipline, xp - that}]}` and set the transaction's `actId`. `currency(v)` is
   `Intl.NumberFormat(undefined, {style: 'currency', currency: 'USD'})`: US dollars in the device locale (`$125.00` in en-US,
   which the golden cases use).
6. Push the transaction (`{id: 'tx_' + now, type, amount, note (trimmed, 60 chars), date}`); `save()` (result ignored);
   return `{ok: true, message: deposit ? 'Deposit recorded' + (xp ? ' · +' + xp + ' XP' : '') : 'Withdrawal recorded'}`.

Paycheck deposits (`36-money-pay.js:47-53`) push transactions directly: no XP and no peak update [D26].

### 11.7 Fixed awards outside the cap

These never go through `ecoAward`, the cap, rested XP or stars, but their XP counts toward the cap total and (when they have a
duration) the active-time total of later capped awards [D08].

#### 11.7.1 Plan sessions (`planToggle`, `06-runtime.js:47-52`)

**PLAN-1** When ticking an item makes the day complete (`dayDone`: every non-optional item has its count of ticks; rest days never
complete) and the day has no `actId`:
- `xp = 0` if `PLAN_LINK[d.kind]` (`07-runtime.js:4`: strength, cardio and athletic, recovery map to three catalog ids) names a
  catalog entry already recorded on that date, else `d.xp` (from `planDay`: 40 strength, 30 cardio, 20 recovery on the default
  and athlete plans, 15 otherwise on the athlete plan; 30/25/20/15 for strength/cardio/mobility/recovery and 15 otherwise on
  class plans, `06-runtime.js:32-41`, `14-runtime.js:52`).
- Split when `d.secondary` is set (SPLIT-1, without the differs-from-primary check); `date` = the plan date at local noon when it
  is before today, else now (so a day ticked ahead of time is dated today).
- Record `{id: 'plan_' + date, type: 'plan', kind: d.kind, planDate: date, name: 'Plan: ' + d.label, icon: d.icon, duration: 0,
  skill: 'Training', primary: d.primary, secondary: d.secondary, xp, date, notes: '', xpSplit}` replaces any record with that id;
  `c.actId` is set; message `d.short + ' complete · +' + xp + ' XP'`.
- Unticking so the day is no longer complete removes the record (`removeAct`) and `actId`; message
  `'Session reopened · XP removed'`. `save()` runs either way; the toast shows only if it saved. Returns `{saved, message}`
  (`message` empty when the day neither completed nor reopened).
- Ticks (outside the economy, but in the recorded `plan`): `planToggle(date, key, i, checked)` keeps
  `plan.checks[date] = {s: {itemKey: [bool, ...]}, v: {}, actId?}`; the item's array is padded with `false` to the item's count
  `n` and entry `i` set to `checked`.

#### 11.7.2 Attribute-sheet catalog (`actStatus`, `07-runtime.js:9-20`; `confirmAct`, `07-runtime.js:110-119`)

**CATALOG-1** The attribute sheet's entries (`ACTS`, ids `attribute.slug`):

- `baseXP(a) = max(1, num(actXP[a.id]) || a.xp)` (`07-runtime.js:6`). `saveBase(id)` stores a custom base
  `min(200, max(1, round(num(v) || 0)))` (never 0, so always stored) and toasts `'XP updated'`; `saveBase(id, reset)` deletes it
  and toasts `'XP reset to default'`; both save (`07-runtime.js:35`).
- Status, from records with `actId === a.id` (`n` = those today): one-time (`o` flag) and any record: `Already recorded`;
  daily (`d`) and `n > 0`: `Done today`; weekly (`w`) and a record on or after `weekMonday`: `Done this week`; `n >= 3`:
  `Daily limit reached`; otherwise `factor = [1, 0.5, 0.25][n]`. Then, if still open: a linked plan kind
  (`LINKS`, `07-runtime.js:3`) already paid today with `xp > 0` blocks (`Already counted via your workout plan`); the savings
  entry is blocked by a `money` record today (`Already counted via the Money tab`). `xp = max(1, round(baseXP * factor))`.
  `actStatus(a)` returns `{blocked, factor, n, xp}` (`blocked` the text, `''` when open; `factor` stays 1 when blocked); for a
  dev boost `{blocked: '', factor: 1, n, xp: max(1, num(a.xp) || xpToGainLevels(...))}`.
- `confirmAct(id)`, in order: blocked toasts the blocked text (`'Unavailable'` when empty); an entry with a minimum reads the
  duration field (`num0`, trimmed) and refuses below it with `'Needs at least ' + a.min + ' minutes'` (without a minimum the
  duration is 0); a milestone (`m` flag) refuses an empty result with `'Name the milestone first'` and one already recorded
  (case-insensitive) with `'That milestone is already recorded'`; a repeat within 2.5 s of the entry's last record is ignored
  silently. Records `{id: 'act_' + now + '_' + 4 random base-36 chars, type: 'catalog', actId, link: a.id, name,
  icon: the attribute's, duration, result (the trimmed result field, `''` when none), skill: a.skill || 'Practice',
  primary: attr, secondary: '', xp, date, notes (the trimmed note field), xpSplit: [{attr, xp}]}`; `save()` (result ignored);
  toast `a.name + ' · +' + xp + ' XP'`.
- `undoCat(id)` removes the entry's last record of today (none: nothing), saves and toasts `'Undone · XP removed'`.

#### 11.7.3 Journal coach (`jcAward`, `47-journal-coach.js:159-164`)

**COACH-1** `jcAward(xp, name, note)` pushes `{id: UUID, type: 'coach', name, icon: 'book', duration: 0, quality: 1,
skill: 'Reflection', primary: 'wellbeing', secondary: '', xp, date: now, notes: note || '', xpSplit: [{wellbeing, xp}],
src: 'coach'}` (passive for streak and decay) and returns `xp`; it does not save (its callers do). The once-only marks live in
`state.journalCoach` (`days`, `reviews`, `welcome`); the writers create what they need (`jcStore`), and drawing the Journal
reads them without creating any (`jcRead`, `jcRec`), so a fresh save has no `journalCoach` until something is written. Constants
in `JC` (`47-journal-coach.js:6`).

| Award | Condition and mark | Name, notes | Toast | Returns |
|---|---|---|---|---|
| Reflection 15 (`jcOnReflect(d, text)`, `47-journal-coach.js:184-188`) | trimmed text 40+ characters, `d` today or yesterday, `days[d].xpAwarded` unset, then set; so today's and yesterday's reflections can each pay once. A refused reflection changes nothing | `Journal reflection`, `''` | `'+15 XP · +2 bond · Reflection saved'` (and +2 bond to the cat) | `true` when paid, else `false` |
| Welcome back 10 (`jcClaim('pet')`, `47-journal-coach.js:306-309`) | today's card offers `pet` (`jcOffered`, below: tier 1); `claimed.pet` then set | `Welcome back`, `'Returned after ' + gap + ' days'` (the stored gap) | `'+10 XP · Welcome back'`; with a cat it first calls `petPetDone()`, which can complete cat care (CARE-1) | `true` when paid, else `false` |
| New chapter 25 (`jcClaim('fresh')`) | today's card offers `fresh` (tier 3); `claimed.fresh` then set | `New chapter`, same notes | `'+25 XP · New chapter'` | as above |
| Catch-up 20 (`jcCatchUp()`, `47-journal-coach.js:313-322`) | today's card offers `line` (tier 2), checked before the prompt; the typed sentence trimmed 12+ characters (else `'Write a full sentence (12+ characters)'`); it is appended (new line) to `dayReflections[today]`, cut to 4,000, unless the reflection already is or ends with it (a retry); `claimed.line` then set | `Catch-up note`, `''` | `'+20 XP · Catch-up saved'` | nothing |
| Weekly review 30, monthly chronicle 50 (`jcSaveReview(key, text)`, `47-journal-coach.js:451-456`) | `reviews[key].answer` = trimmed text cut to 2,000 on every call; pays when `paid` is unset and the text has 50+ (key `W...`) or 60+ (`M...`) characters, then `paid = true` | `Weekly review` / `Monthly chronicle`, `''` | `'+30 XP · Review saved'` / `'+50 XP · Review saved'` | nothing |

Today's card offers a chip (`jcOffered(kind)`, `47-journal-coach.js:303`, through `jcWelcomeData`, `276-287`) only when
`welcome[today]` has a `tier` and 3 or more quiet days lead to today now (`jcGap`, `47-journal-coach.js:62-65`: days in a row
before today with no record other than coach and passive ones and no written reflection, counted only when some record came
before them); tier 1 offers `pet`, tier 2 `line` (and a link to Life Director that pays nothing), tier 3 `fresh`, each until
claimed. So a record without a tier, a chip of another tier, a repeat, or a day the owner has since logged (the gap is gone)
pays nothing. The record itself is decided when today is first drawn after 3 or more quiet days (`jcWelcomeRecord`,
`47-journal-coach.js:266-273`): `{tier: gap >= 14 ? 3 : gap >= 7 ? 2 : 1, gap, claimed: {}}`, stored with a plain write.

The words come first (`jcKeepWords`, `47-journal-coach.js:168`): a reflection is stored by `saveDayReflection`
(`04-runtime.js:187`) before `jcOnReflect` runs, and the catch-up sentence and a review's answer are stored by `jcWrite`
(`47-journal-coach.js:23`: `state.customRules = rules`, then `writeSave()`, no rebuild, Sparks or Daily Hand). When the words
cannot be stored the page toasts `unsavedMessage()`, keeps them in memory and on screen, and pays nothing. A review that pays
nothing, and a refused catch-up, are only that write (and a redraw); they never run `save()`.

The reward is then paid in a save of its own (`jcPay`, `47-journal-coach.js:173-179`): `was = JSON.stringify(state)` (after the
words), the award, then `save()`. Stored: the reward's toast. Not stored: `takeBack(was, said)` (SAVE-4) puts the state back
as it was after the words, so only the reward is taken back, and says `'Reflection saved · XP not saved (storage full?)'`,
`'Catch-up saved · XP not saved (storage full?)'` or `'Review saved · XP not saved (storage full?)'` (the words were kept); a
welcome chip, which has no words of its own, gets `unsavedMessage()`. The toasts of a failed reward are therefore `save()`'s
`unsavedMessage()` (none while read-only) and then the take-back's line; the reward's own toast never appears. The claims, the
catch-up and reviews redraw the journal; that drawing may store `days` and `pats` entries of its own (the coach's day and
pattern memory) with a plain write, and they are outside the economy.

#### 11.7.4 Dev boosts (`07-runtime.js:10,117`, `04-runtime.js:279-326`)

**DEV-1** With developer mode on, an attribute sheet offers `+1` and `+2` levels: `xp = xpToGainLevels(attrXP(attr), n)` (CURVE-8),
recorded as a catalog entry with no limits.

### 11.8 Path matrix

| Path | Day counted | Per-type 3/day | 6 h limit | Scholar | Rested | Cap | Stars | Split | Failed save taken back |
|---|---|---|---|---|---|---|---|---|---|
| Log | today | yes | yes | yes | peek-cap-take | yes | yes | draft secondary | yes |
| Health workout | record's day | yes | no | (never reading/language) | peek-cap-take | yes | yes | rule secondary | no |
| Health steps, sleep | record's day | yes | no | no | peek-cap-take | yes | yes | none | no |
| Voice | chosen day | yes | yes | yes | peek-cap-take | yes | yes | rule secondary | no |
| Life Director | session start day | yes | no | yes | peek-cap-take | yes | yes | rule secondary | yes |
| LD execution bonus | session start day | - | - | - | no | no | no | discipline | yes |
| Quest daily/weekly | today | 3/day, 3/7 days | - | Compound (daily) | peek-cap-take | yes | no | none | no |
| Quest epic | today | 1/day | - | - | take | exempt | no | none | no |
| Nutrition | today | once/day | - | - | take-then-cap | yes | no | wellbeing/physique | no |
| Cat care | today | once/day | - | - | take-then-cap | yes | no | wellbeing/social | no |
| Money deposit | today | once/day, 25/day, 50/7 days | - | - | take-then-cap | yes | no | business/discipline | no |
| Plan | plan date (today when not before today) | once per date | - | - | no | no | no | plan secondary | no |
| Catalog | today | own flags, 1/0.5/0.25 | - | - | no | no | no | none | no |
| Journal coach | today | own keys | - | - | no | no | no | none | no |
| Dev boost | today | none | - | - | no | no | no | none | no |

---

## 12. Sparks

- **SPARK-1** Earning (`ecoSparksSync`, `44-rewards.js:13-16`), inside every `save()` after the rebuild and the migration
  (17.1): for each ledger record without a truthy `sp`, set `sp = 1` and add `max(0, num0(a.xp))`; when the total added is
  non-zero, `sparks = num0(sparks) + added`. One Spark per XP, credited at save time, never taken back [D14].
- **SPARK-2** Balance: `ecoSparks() = max(0, round(num0(sparks)))` (`44-rewards.js:11`).
- **SPARK-3** Spending: `ecoSpend(n)` (`44-rewards.js:76`): if `ecoSparks() < n`, toast `'Not enough Sparks'` and refuse; else
  `sparks = num0(sparks) - n`.
- **SPARK-4** Shop (`ecoBuy(kind, id)`, `44-rewards.js:77-96`; prices `44-rewards.js:6-7`). It first creates `perksOwned`,
  `themesOwned` (`[]`) and `toysBought` (`{}`) when missing, whatever the kind; a refused spend (SPARK-3) returns without saving.
  - theme (300): spend only when not owned, add to `themesOwned`; then toggle `theme` between `id` and null.
  - perk (600): spend only when not owned, add to `perksOwned`; then toggle `perk` between `id` and null (one equipped).
  - toy (`ball_b` 150, `yarn` 150, `feather` 200, `spring` 300, `wand` 450): already bought returns without saving; else spend
    and set `toysBought[id] = 1`, toast `'Toy unlocked for your cat'` [D31].
  - rest token (250): refused (`'One rest token a week'`) when `restBoughtAt > now - 7 days`; else spend,
    `restTokens += 1`, `restBoughtAt = now`.
  - shield refill (250): refused when `shieldBoughtAt > now - 30 days` (`'One shield refill a month'`) or
    `streakShields >= 2` (`'Shields are already full'`); else spend, `streakShields = 2`, `shieldBoughtAt = now`.
  - Every branch that does not return early ends with `save()`, an unknown kind included (it buys nothing).
- **SPARK-5** Daily Hand reroll (`dhReroll(slot)`, `50-quests-hand.js:116-131`): a finished card refuses
  (`'Finished cards cannot be rerolled'`, false); the price is 0 while `dailyDraw.rerollsUsed < 1`, else 50 Sparks through
  `ecoSpend` (refused: false). Then `rerollsUsed` and `nonce` go up by 1, the card is replaced by a new draw (outside the
  economy), toast `'Rerolled for 50 Sparks'` or `'Free reroll used'`, `save()`, true.
- **SPARK-6** The migration seeds `sparks = round(raw * 0.5)` and marks every record as credited (MIG-6).

---

## 13. Perks

**PERK-1** `ecoPerk(id) = state && state.perk === id && (state.perksOwned || []) contains id` (`44-rewards.js:8`).

| id | Name | Effect | Where |
|---|---|---|---|
| `scholar` | Scholar’s Flow | x1.1 on reading and language awards of 45+ minutes, inside `ecoAward` before rested XP and the cap | `43-economy.js:45` |
| `compound` | Compound Habit | x1.15 on daily quest base, before rested XP and the cap | `43-economy.js:55` |
| `exec` | Executive Edge | Life Director execution bonus 20% instead of 10% | `logic.js:2520` |
| `iron` | Iron Grace | +2 grace days before Physique and Endurance decay | `05-runtime.js:47` |

---

## 14. Ascension (`ecoAscend(id)`, `44-rewards.js:18-26`)

**ASCEND-1** An id that is not an attribute: nothing. Below level 20: toast `'Reach level 20 in ' + short + ' first'`. Three
stars: `short + ' already has three stars'`. Then the owner confirms `'Ascend ' + name + '? Its level restarts at 1 (all ' + XP
(locale grouping) + ' XP stays banked in your character level) and it earns +5% XP for good.'`; no: nothing. Then
`ascended[id] += attrXP(id)` and `attrStars[id] += 1`; `save()`; toast `short + ' ascended · ' + '★' repeated by the new star
count`. The rebuild subtracts `ascended` (DERIVE-1), so the attribute
restarts at 0 XP (level 1) while the character level is unchanged (CURVE-5). Each star adds 5% (STAR-1).

---

## 15. Decay, rested accrual and rest days

- **DECAY-1** Settings: `getDecay() = {on: true, grace: 4, rate: 3, ...state.decay}` (`05-runtime.js:43`); `saveDecay` stores
  `{on (the switch), grace = min(14, max(1, num(v) || 4)), rate = min(10, max(1, num(v) || 3))}` (`05-runtime.js:64`), saves and
  toasts `'Decay settings saved'`.
- **DECAY-2** `lastTrained(id)` (`05-runtime.js:45`): the latest `activityDate` among non-passive records (2.4) whose `primary`
  or `secondary` is `id`; null when none. Any non-passive record counts, including 0-XP records, quests (their `primary`),
  nutrition, cat care, money and plan [D35].
- **DECAY-3** `applyDecay()` (`05-runtime.js:46-52`), in order:
  1. `ecoShieldTick()` (16.2), errors ignored. Runs even when decay is off.
  2. If decay is off: return false.
  3. `today = dayNum(today)`. For each attribute in ATTRS order:
     1. `lt = lastTrained(id)`; null: next attribute.
     2. `from = max(dayNum(lt) + grace + (iron perk and id is physique or endurance ? 2 : 0) + 1, (decayApplied[id] || 0) + 1)`.
     3. For each day `n` from `from` to `today`: skip it when `restDay(ecoDayKey(n))` (REST-DAY-1). `xp = attrXP(id)`; stop
        when `xp <= 0`. `lv = levelData(xp)`; stop when `lv.into <= 0` (decay never lowers a level).
        `l = min(lv.into, max(1, min(15, round(need(lv.level) * rate / 100))))`; `attrXP[id] = xp - l`; `lost += l`.
     4. Rested accrual (DECAY-5).
     5. If `today >= from`: `decayApplied[id] = today`.
     6. If `lost > 0`: changed; `decayTotal[id] += lost`; prepend `{date: today key, attr: id, xp: lost}` to `decayLog`.
  4. Keep the first 30 `decayLog` entries; return whether anything was lost.
- **DECAY-4** Per-day loss at the default rate 3, `round(need(L) * 3 / 100)` clamped to 1..15 and to `into`: L1 3, L2 4
  (`round(3.75)`), L3 5 (`round(4.5)`), L4 5, L5 6, L6 7, L10 10 (`round(9.75)`), L16 14, and 15 from level 17 on. The
  product `need * rate` is formed before dividing by 100.
- **DECAY-5** Rested accrual (`05-runtime.js:49`): when `lost > 0` and `today - dayNum(lt) >= 7`,
  `restedXP = min(300, num0(restedXP) + round(lost * 0.5))`, per attribute per call. Depends on how often the app is opened
  [D21].
- **DECAY-6** Triggers: at boot after `load()` (`13-runtime.js:115`; when it changed something the state is written with
  `writeSave()`, not `save()`), and when the page becomes visible (`11-runtime.js:56`; `save()` when changed) [D29].
- **REST-DAY-1** `restDay(key)` (`ecoRestDay`, `43-economy.js:63-67`): `restDays[key]` is truthy, or `planDay(key).kind` is
  `rest` (errors ignored), or some record that day has `type === 'recovery'`, is not passive and `num0(duration) >= 20`.
- **REST-DAY-2** `ecoRestUsed()` (`43-economy.js:68`) = number of `restDays` keys `>= daysAgo(6)`.
  `ecoTakeRest()` (`43-economy.js:69-74`): already a rest day today (`restDays[today]` only, not REST-DAY-1's recovery session
  or plan rest day: those still let the owner take one; golden `rest/take-after-recovery`): `'Today is already a rest day'`; if
  `ecoRestUsed() >= 2`: spend a rest token if any (`'Rest token used'`), else refuse (`'Two rest days per week are used'`); then
  `restDays[today] = true`, `save()`, `'Rest day taken. No decay today.'`.

---

## 16. Streak and streak shields

Passive records are defined in 2.4 (PASSIVE-1).

- **STREAK-1** `streak()` (`05-runtime.js:13`): the set of days = `activityDate` of non-passive records, plus `restDays`
  keys, plus `shieldDays` keys (keys whatever their value; the page only ever stores `true`). Walk back from today, local date
  by local date, up to 400 days: a day in the set adds 1 and resets the miss count; a day not in the set adds 1 to the miss
  count, and the walk stops at the second consecutive miss.
  Single missed days neither count nor break the streak.
- **SHIELD-1** `ecoActive(key)` (`44-rewards.js:28`): some non-passive record that day.
- **SHIELD-2** `ecoShieldTick()` (`44-rewards.js:29-45`):
  1. `today = dayNum(today)`. If `shieldTick` is null or missing: `shieldTick = today - 1`, stop.
  2. `miss = shieldMiss || 0`, `sh = streakShields || 0`. For each day `n` from `shieldTick + 1` to `today - 1`:
     `k = ecoDayKey(n)`; present when `ecoActive(k)`, `restDay(k)` or `shieldDays[k]`. Present: `miss = 0`. Otherwise
     `miss += 1`; if `miss >= 2`, `sh > 0` and some non-passive record has `dayNum(activityDate) >= n - 30`:
     `shieldDays[k] = true`, `sh -= 1`, `miss = 1`, toast `'A streak shield covered a missed day'`.
  3. `shieldTick = today - 1`; `shieldMiss = miss`.
  4. `s = streak()`, `m = floor(s / 7)`; if `m > (shieldMilestone || 0)`: `sh = min(2, sh + m - (shieldMilestone || 0))`,
     toast `'Streak shield earned'`. `shieldMilestone = m` (written as `min(m, floor(s / 7))` [D28]); `streakShields = sh`.

---

## 17. Save pipeline, take-back and boot order

- **SAVE-1** Core `save()` (`04-runtime.js:44`), in order: `rebuildAttrXP`; `rebuildSkillXP`; then in one `try`:
  `ecoMigrate` (17.6) and `ecoSparksSync` (an error in the migration skips the Spark sync for that save);
  `state.customRules = rules`; `checkBadges`; `saved = writeSave()`; if saved, `checkLevelUps` (celebration); if not saved and
  not read-only, toast `unsavedMessage()`; `renderAll()`; return `saved`.
- **SAVE-2** `save` is wrapped three times, in segment order (`runtime/module-map.json`): `33-android.js:184` (after the core
  save, schedules an Android sync), `50-quests-hand.js:152-153` (then, unless the Daily Hand is already verifying and when
  `state.quests` exists, runs `dhAutoVerify()`, which may call `toggleQuest` and so pay quest XP and save again),
  `50-native-nutrition.js:621-628` (then schedules a native sync). The value returned is the core save's.
  `dhAutoVerify()` (`50-quests-hand.js:144-151`): with the Daily Hand marked busy for its duration (so the saves it causes do
  not verify again), it makes sure today's three cards exist (`dhEnsure`, outside), then for each card quest (`hand: true`) not
  done today whose verification is met on today's records, in order, calls `toggleQuest(id, true)` (QUEST-2, which pays
  through QUEST-3 and saves). The verification (`dhVerifyMet`, `50-quests-hand.js:133-142`) looks at today's records other than
  quests and coach awards: `act:T:M` is met by a record of type `T` with `num0(duration) >= M`; `any:M` by any record with
  `num0(duration) >= M`; `water:P` by today's water log reaching `P`% of the water goal; `pet:feed|pet|play` by the cat's count
  for today; `plan` by `dayDone(today)`; `self` and `none` never (the owner claims those by hand). The Daily Hand step
  runs even when the core save failed.
- **SAVE-3** `writeSave()` (`04-runtime.js:40-43`): refuses (false) while the save is read-only; false when storage throws.
- **SAVE-4** `takeBack(was, said)` (`04-runtime.js:54`): `state = JSON.parse(was)`, redraw, toast `said` when given, else
  `unsavedMessage()` (`'Not saved: this version is read-only for your save'` or `'Could not save to this browser (storage
  full?)'`, `04-runtime.js:46`). Everything the attempt changed is undone: the record, rested XP spent, Sparks credited, quests
  the Daily Hand paid. Only Log (LOG-2), Life Director (LD-3) and the Journal coach's rewards (COACH-1, which pass `said` to name
  the words they kept) use it [D13]. A failed save therefore toasts twice: `save()`'s `unsavedMessage()` (SAVE-1, none while
  read-only), then the take-back's (golden cases `log/save-fails`, `director/award-save-fails`). A path that does not take back
  shows `save()`'s message among its own: `'Quest complete · +15 XP'` then the message (`quest/save-fails-kept`), the message then
  `'Activity saved · +26 XP'` (`voice/log-save-fails-kept`). The redraw no longer saves (the Journal stores its own decisions with
  a plain write), so the message is not said a third time.
- **SAVE-5** `load()` (`04-runtime.js:20-36`): merges the stored save over `freshState()`, rebuilds attribute and skill XP when
  the ledger is non-empty, merges rules, then `migrateSave()` (`ecoMigrate`, reading list, class content) and `writeSave()`.
  A save from a newer build, or one whose migration throws, is shown read-only instead and never written. With nothing stored:
  `freshState()` (no `xpSchemaV2`), default rules, `seedReadingList()` (sets `mediaItems` and `mediaSeededV1`), then
  `writeSave()` without the migration (`04-runtime.js:33`).
- **SAVE-6** Boot (`13-runtime.js:115`): `load()`, then `applyDecay()` (written with `writeSave()` when it changed something),
  then badges and the first render.

### 17.6 Migration to the v2 economy (`ecoMigrate`, `43-economy.js:76-90`)

Runs in `migrateSave()` at load and at the top of every `save()`; does nothing once `xpSchemaV2` is set.
- **MIG-1** `raw` = sum of `attrXP` over ATTRS (after the rebuild); `old = levelData(raw)` on the attribute curve.
- **MIG-2** `target = charCumXP(old.level) + round(old.into / old.needed * charNeed(old.level))`;
  `legacyCharXPBonus = max(0, round(target - raw))`, so the character keeps the level and fraction it had on the old curve.
- **MIG-3** `decayApplied[id] = dayNum(today)` for every attribute (no back-dated decay); `state.decay` is deleted when it is
  exactly the old defaults (`grace 3`, `rate 2`).
- **MIG-4** For each type in `ECO_OLD_RULES` (`43-economy.js:8`: reading 20/discipline, business 25/discipline, strength
  32/discipline, running 27/discipline, language 20/social, recovery 16/discipline, practical 23/intelligence, task 15/'',
  custom 20/''): when the current rule's `base` equals the old base and its `secondary || ''` the old secondary, take `base`,
  `primary` and `secondary` from `DEFAULT_RULES`. Then `customRules = rules`.
- **MIG-5** `money.peakBalance = moneyBalance()` (creating `state.money` when missing), so only savings above today's balance
  earn deposit XP.
- **MIG-6** `sparks = round(raw * 0.5)`; every record gets `sp = 1`; `xpSchemaV2 = true`. Because it runs inside `save()`
  after the new record is pushed, the first save of a state without `xpSchemaV2` treats that record as old [D22].

---

## 18. Undo and removal

**UNDO-1** What each removal reverses:

| Action | Source | Reverses | Does not reverse |
|---|---|---|---|
| Delete an activity | `deleteActivity`, `04-runtime.js:269`: asks first (no: nothing), then saves and toasts `'Activity undone'` | the record; money transactions with that `actId`; the plan day's `actId`; `questDone` entries pointing at it (so the quest can be paid again) | Sparks, rested XP spent, decay, `peakBalance`, badges, `nutriDone`, `careDay.paid` |
| Remove an activity (plan, catalog undo, voice undo) | `removeAct`, `06-runtime.js:46`; `undoCat`, `07-runtime.js:120`; `50-voice-gen5.js:29` | the record | the same |
| Uncheck a quest | QUEST-2 step 6 | the record and `questDone` | Sparks, rested XP |
| Remove a money transaction | `removeMoneyTransaction(id)`, `05-runtime.js:179-181`: false for an unknown id, else saves and returns true (`deleteTx` asks first and toasts `'Transaction removed'`) | the transaction and its record | `peakBalance` (a re-deposit does not pay again), Sparks |
| Reopen a plan day | 11.7.1 | the record and `actId` | Sparks |

Attribute and skill XP follow automatically through the rebuild.

---

## 19. Numeric hazards

Computed with the page's own expressions; an exact-decimal or reordered port gets these wrong:

| Expression | JavaScript (binary64) | Exact decimal |
|---|---|---|
| `round(45 * 0.7)` (split of 45) | 31 (`31.499999999999996`) | 32 |
| `round(x * 0.7)` also differs for x = 85, 165, 175, 325, 335, 345, 355, 365 | | |
| `round(50 * 1.15)` (Compound Habit on 50; 3 stars on 50) | 57 (`57.49999999999999`) | 58 |
| `round(x * 1.15)` also differs for x = 90, 110, 170, 190, 210, 330, 350, 370, 390, 410, 430 | | |
| `1 + 0.05 * 3` | `1.1499999999999999` | 1.15 |
| `round(25 * 1.1)` | 28 (`27.500000000000004`) | 28 (same, by luck) |
| business (base 25), 78 min, quality 1: `round(25 * ecoDur(78))`, `ecoDur(78) = 2 + 18/60` | 57 (`57.49999999999999`) | 58 |
| business, 61 min, quality 1.2: `round(25 * ecoDur(61) * 1.2)` | 60 (`60.49999999999999`) | 61 |
| business at quality 1.2 also differs for 63, 67, 71, 77, 79, 93, 107, 109, 113, 115, 119 min | | |
| Deposit XP on a balance of 3.01 (peak 3.01), deposit 125: `net = 3.01 + 125 - 3.01`, then `round(net / 10)` | 12 (`net = 124.99999999999999`) | 13 |
| the same on balances such as 3.29, 4.20, 5.04, 6.23 (a round deposit on a balance with odd cents) | | |
| Deposit threshold: balance 0.01, peak 0.02, deposit 50.01: `net = 0.01 + 50.01 - 0.02` | no XP (`49.99999999999999 < 50`) | 5 |
| Nutrition upper bound `goal * 1.15` for a goal of 1500 (also 1300, 1350, 1400, 1450, 1550 ... in steps of 50) | `1724.9999999999998`: 1,725 kcal misses | 1,725 qualifies |
| Sleep name minutes `round((hrs - h) * 60)` for 6 h 8 min 30 s (and most half minutes from 6 h 8 min 30 s on) | `08` (`8.499999999999986`) | `09` |
| the same for 3 h 0 min 30 s | `00` | `01` |

`round(x * 0.5)`, `round(x * 0.1)`, `round(x * 0.2)` and `round(x * 1.1)` agree with exact decimals for x up to 300; so do the
step points `round((eff - 2000) / 1000 * 1.5)` (exact ties only at odd thousands, which binary holds exactly). `2000 * 1.15` and
`2000 * 0.85` happen to be exact (2300, 1700). Money amounts are binary doubles throughout
(`amt = round(num(amount) * 100) / 100`, the balance re-rounded to cents, but `net` and the stored `peakBalance` are not), so a
port must not switch to exact cents.

---

## 20. Defects and accidents in today's behaviour

Each is today's behaviour, to be reproduced unless the owner decides otherwise.

- **D01** Health step days are paid again. Step days are read for 30 days and filtered only by `H.since`, which is fixed at the
  day sync was switched on (`39-health.js:41,59,63`), while their `seen` marks are deleted after 21 days (`39-health.js:98`).
  Days 22 to 30 days old become new again and are paid a second time at full XP and a third at half (per-type limit); the
  mark set by each payment is pruned again at the end of the same sync. After the third payment every sync, until the day
  leaves the 30-day read, still counts it (`'Added 1 day of steps.'`, toast `'Health sync: 1 day of steps'`) while paying 0.
  A sandbox run of 40 daily syncs paid one day 11, 11 and 6 XP; the golden case `health/steps-paid-again` pays 12, 12, 6, 0.
- **D02** The nutrition bonus is unreachable: its only trigger wraps `addMealEntry` (`44-rewards.js:147`), and nothing calls
  `addMealEntry` (the Food screen logs through `fv2Log`, `50-food.js:30-33`, and smart-diet meals push to the food log
  directly, `50-smartdiet.js:65`).
- **D03** Nutrition, cat care and deposits take rested XP before the cap (`44-rewards.js:54,63`, `05-runtime.js:173`), so the
  pool is spent even when the cap pays nothing. Commit `1550306` fixed this only in `ecoAward` and `ecoQuestXP`.
- **D04** Nutrition and cat care set their once-a-day flag before the cap and stop without saving when it pays nothing; the flag
  and the spent pool are written by the next unrelated save, and the day's bonus is gone.
- **D05** A capped award that pays anything spends the whole rested bonus computed from the uncapped amount
  (`43-economy.js:47,60`): an award of `x` that the cap cuts to 1 XP still takes `round(x * 0.5)` from the pool.
- **D06** Stars are applied after the cap, so a day can end above 240 XP (`04-runtime.js:259`, `39-health.js:24`,
  `49-voice-time.js:119`, `logic.js:2500`).
- **D07** The Life Director execution bonus is added after the cap and computed from the starred XP (`logic.js:2516-2521`).
- **D08** Plan, catalog, coach and dev awards are uncapped but count toward the cap total and the active-time total of later
  capped awards, so the order of logging changes what the day pays.
- **D09** The weekly quest limit counts a rolling 7 days (`43-economy.js:57`) while a weekly quest's period is the calendar
  week from Monday (`04-runtime.js:87`).
- **D10** A walking workout imported from Health Connect gets skill `Walking` (`39-health.js:54`) and so matches the passive
  test meant for step counts (`43-economy.js:12`): it pays XP but does not count for the streak, decay or shields.
- **D11** `lmaHealthAward` adds the pre-star amount to the skill cache and returns it to the sync summary, while the record
  stores the starred amount (`39-health.js:27-28`). The rebuild corrects the cache; the toast undercounts.
- **D12** The health settings text (`39-health.js:124`) says steps pay "3 XP per 1,000 up to 15,000" from 2,000 steps, and
  sleep pays from 3 hours; the rules pay 1.5 XP per 1,000 effective steps above 2,000, nothing below 4,000 effective, at most
  15, and nothing for sleep under 5.5 hours.
- **D13** Only Log, Life Director and the Journal coach's rewards take a failed save back. Quests, voice, health, nutrition, cat
  care, money, plan, catalog, the shop and rest days leave the attempt in memory (records, rested XP spent, Sparks credited, quests the Daily Hand
  paid in the save wrapper), and the next successful save stores it.
- **D14** Sparks are never revoked (SPARK-1). Checking and unchecking a quest, or deleting and re-logging an activity, pays new
  Sparks each time (and spends rested XP each time) while the XP nets out.
- **D15** Unchecking a quest subtracts the quest's listed XP, not what was paid, from the caches in memory; harmless because the
  save rebuilds them.
- **D16** `toggleQuest` saves even when it changed nothing.
- **D17** Ids built from the millisecond clock (`quest_`, `nut_`, `care_`, `act_` for deposits) collide if two awards land in
  the same millisecond; unchecking one quest would then remove both records.
- **D18** Life Director records an activity even when it pays 0 XP (cap, per-type limit, under 5 minutes); the record still
  counts toward the per-type limit, the active-time total of manual logs, the streak and decay.
- **D19** The comment on `xpFor` (`logic.js:2487-2489`) describes a formula the code no longer uses (0.4x to 3x, diminishing
  returns after 3 and 5 logs).
- **D20** Reported amounts differ from stored ones: Life Director reports XP without the bonus; voice previews before stars;
  `completeQuest` and the quest board celebration (`14-runtime.js:11`) report the quest's listed XP; the quest "limit reached"
  toast also appears when the daily XP cap was the reason.
- **D21** Rested accrual depends on when decay is applied: it counts only losses applied while the attribute has been idle 7+
  days, and rounds per application. Opening the app daily for 12 idle days banked 24 rested XP; opening it once at the end
  banked 28 for the same 56 XP of decay (sandbox). In the golden cases `decay/rested-opened-daily` and `-once` the same 48 XP of
  Physique decay banks 18 when decay runs on each of 8 days and 24 when it runs once.
- **D22** The first save of a state without `xpSchemaV2` migrates with the just-added record already in the ledger: Sparks
  become `round(raw * 0.5)` instead of the record's XP, the record is marked credited, a `legacyCharXPBonus` is derived from it
  and `state.money` is created. A stored save is always migrated by `load()` first, so in the app this needs a state that
  never went through `load()`'s migration (the starter profile before its first save, or a test's `freshState()`).
- **D23** A Log draft's secondary attribute does not fall back to the rule's (the primary does); health, voice and Life
  Director always use the rule's.
- **D24** A Log record stores `quality: num(d.quality)` (0 for `''`, null in JSON for NaN) although XP used `num(d.quality) || 1`.
- **D25** Nutrition checks today's food log whatever day the entry was for (moot while D02 stands).
- **D26** Deposits: paycheck deposits do not move `peakBalance`, so money withdrawn after a paycheck and deposited again earns
  XP; the deposit limits use a rolling 7 days.
- **D27** Messages hard-code numbers that live in `ECO` (`6 hours`, `180`), so changing a constant would not change the text.
- **D28** `shieldMilestone = min(m, floor(s / 7))` is just `m`; because it follows the streak down, shields are earned again
  each time a broken streak rebuilds.
- **D29** The boot writes the save only when decay lost XP, so shield bookkeeping and `decayApplied` updates on other days wait
  for the next save.
- **D30** The decay watch display ignores Iron Grace (`05-runtime.js:57`).
- **D31** Buying a toy id without a price would make Sparks NaN (`44-rewards.js:86`); the shop only lists priced toys.
- **D32** `rebuildAttrXP` has an unreachable branch (`04-runtime.js:68`).
- **D33** The cap total does not clamp negative record XP (`43-economy.js:20`) while the rebuild does.
- **D34** Health steps and sleep spend rested XP, so passive data can drain the pool meant for coming back to training.
- **D35** Quests, nutrition, cat care and deposits count as training for decay through their primary and secondary attributes
  (cat care keeps Wellbeing and Social from decaying), although the economy header says passive data no longer stops decay.
- **D36** A night within 30 seconds below a whole hour is named with 60 minutes: 7 h 59 min 45 s is `Sleep: 7h 60m`
  (`39-health.js:90`, `m = round((hrs - h) * 60)` with `h = floor(hrs)`). Only the name is affected; the points use `hrs`.
- **D37** The health sync's "already logged by hand" test (`39-health.js:53`) compares the hand-logged record's time, which for
  the Log is when it was saved, with the workout's start, within 2 hours. A workout longer than about 2 hours that is logged by
  hand when it ends is paid twice (golden case `health/workout-long-logged-by-hand`: a 150-minute hike logged by hand as a run
  at its end pays 78 XP twice).

---

## 21. Notes for recording golden cases

The cases are recorded by `tools/gen_economy_golden.js` into `tests/fixtures/economy-golden.json` (its header defines the case
format; the first case, `fresh/save`, holds the fresh save every other case's state is written against), and
`tests/economy-golden.test.js` records them again from the current page on every test run (it is one of the `tests/*.test.js`
that `tools/run_tests.sh` runs in CI) and fails when the page and the file disagree, or when a rule or defect ID here has no case
and no stated reason. Pitfalls met while recording, besides
the ones listed below: `dayNum('2026-10-28')` is 20754 (a day number written by hand is easily a year off); drawing the page
must not save before the case begins (a save credits Sparks, marks records and migrates), so the generator holds back any save a
drawing makes (the journal's drawing no longer saves: its chip and welcome decisions use a plain write); a welcome case must seed
a record with a `tier` and a last record `gap + 1` days back, or `jcOffered` refuses every chip; a health sync looks back only to `max(since, now - 3 days)` (HEALTH-2), so one sync
cannot exercise a week of sleep bands; a stage-3 quest needs character level 5, not 3 (CURVE-9).

- Drive the real built page (`build.py`, `forge_features.py`) with a fixed clock and time zone, as
  `tests/log-activity.test.js` does; every day key depends on both.
- Seed `xpSchemaV2: true` unless the case is about the migration (D22), and seed `restedXP`, `perk`/`perksOwned`,
  `attrStars`, `decayTotal`, `ascended` explicitly.
- The save wrapper runs the Daily Hand's auto-verify (SAVE-2): a case whose activity fulfils a card on the fixed date gets quest
  XP on top. The generator switches the Daily Hand off in every case except those marked `hand` (its cards) and `verify` (its
  auto-verify too: `save/daily-hand-pays-inside-save` seeds today's three cards so the draw is not involved).
- Some recorded fields hold page state that is outside the economy and that a fresh implementation may ignore or compare
  loosely: the coach's `journalCoach.days` and `.pats` entries added when the journal is redrawn (COACH-1), `plan.checks[date].s`
  ticks (PLAN-1, specified but outside), the money object's paycheck settings dated by the case's day, and the "not saved" toast said
  twice in a row by a failed save and its take-back (SAVE-4).
- Life Director's host sync (LD-4) can complete quests after a Log; keep Life Director state empty in Log cases.
- Record both the return value and the state delta (ledger tail, `attrXP`, `skillXP`, `restedXP`, `sparks`, `questDone`) and
  the messages.
- Families worth covering: each row of the path matrix; the cap table (CAP-2); every refusal message in AWARD-1; Scholar,
  Compound, Executive Edge; 1, 2 and 3 stars; each value in section 19; rested peek/take at the cap (D05) and take-then-cap
  (D03); per-type counting across sources; the active-time limit with health and quest records present; weekly quest window
  at a week boundary (D09); decay across rest days and level boundaries; rested accrual daily versus in one go (D21); shields
  over a gap; the health step re-payment (D01).
- The health plugin is faked per call (`env.health`): its status, the exercise, steps and sleep records, the weight reading,
  and `fail`, the name of a read that throws (HEALTH-0's `Sync failed` path).
