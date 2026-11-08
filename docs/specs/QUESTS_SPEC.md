# LifeMax quests and Daily Hand specification

Status: describes the quest board, custom quests, the Daily Hand and the 3D table ledger at commit `3eeaa1e` (branch
`claude/github-android-app-d3wlth`), from the segments `04-runtime.js`, `13-runtime.js`, `14-runtime.js`, `43-economy.js`,
`44-rewards.js`, `50-quests-hand.js` and `50-quests-table.js`, aligned with `ECONOMY_SPEC.md`. Today's behaviour is the
authority; defects and accidents are marked `[QDnn]` and listed in §12 without changing the rules.

**Provenance and review.** Drafted by an outside agent working in a chat window from a flat kit of those files (every rule is
labelled Read; nothing was executed). Reviewed against the code at `3eeaa1e`: 32 of the 62 rules were checked line by line
(QST-5, 6, 11, 12, 15–18, 21, 22, 26, 28, 30–36, 38–50, 52–56, 58–60, and QD03, QD14, QD16, QD17). Every behaviour checked
matched the code. Citations did not: in `50-quests-hand.js` past about line 190 the draft's line numbers were about 50 lines
low, and in `50-quests-table.js` about 3 lines low. The citations of the rules checked are corrected here; **a citation in
those two ranges that is not in the checked list may still be off: find the code by name before trusting the number.** Two
gaps the draft found in `ECONOMY_SPEC.md` (QUEST-2 step 3, SAVE-5) are fixed there.

---

## 0. How to read this

Every rule carries a continuous ID (`QST-1`…`QST-62`), a source (`file:line` or range), and a label (**Read** or
**Executed**).

- **QST-1** (**Read**, `ECONOMY_SPEC.md` NUM-1–NUM-5, `50-quests-hand.js:63-64`) Numeric semantics inherit `NUM-1`–`NUM-5`.
  Daily Hand hashing and pseudo-random generation also use 32-bit integer operations: `Math.imul(a, b)` (low 32 bits of the
  signed product), XOR `^`, unsigned right shift `>>>` (coerces to `Uint32`), and division by 2^32 = 4294967296 in binary64.
- **QST-2** (**Read**, `ECONOMY_SPEC.md` TIME-1–TIME-8, `04-runtime.js:83-86`, `13-runtime.js:271`) Date and time semantics
  inherit `TIME-1`–`TIME-8`. Weekday filtering uses local `new Date().getDay()` (`0` = `'sun'` … `6` = `'sat'`).
- **QST-3** (**Read**, `04-runtime.js:239`, `14-runtime.js:31`) Output strings and toasts are part of the contract, with exact
  punctuation (`·` U+00B7 in `' · '`, `’` U+2019 in `'Today’s missions cleared. Well played.'`).

---

## 1. Boundary

### 1.1 In: specified completely
- Quest model, class quest packs (`applyClassContent`), weekday filter (`questToday`), period keys, `questDone` aging.
- Stage locks (`stageLvl`, `questLocked`), board and Hand completion (`qosToggle`, `dhHoldStart`), and what deleting an activity
  does to `questDone`.
- Custom quest creation, limits and defaults on the board and in the 3D table (`createQuest`, `dhRest.create`).
- Quest board grouping, order, empty states and summary (`renderQuests`, `qosUpdateSummary`).
- Daily Hand template library, FNV-1a seed, Mulberry32 generator, recent XP window, weakest attribute, weighted pick, deal,
  rerolls, card verification (`dhVerifyMet`, `dhAutoVerify`), the `dhMig` migration, reveal / turn / dealt state, and the 3D
  ledger (`renderLedger`).

### 1.2 Contracts only: economy rules and items not in the kit
- **Economy rules (`ECONOMY_SPEC.md`):** quest XP, caps, rested XP, Sparks and the core `toggleQuest` follow `QUEST-1`–`QUEST-5`,
  `CURVE-9`, `CAP-1`–`CAP-2`, `REST-1`–`REST-3`, `SPARK-1`–`SPARK-5`, `PERK-1`, `REST-DAY-1`, `SAVE-1`–`SAVE-4` and `UNDO-1`. Cited by
  ID, never restated or changed.
- **Not in the kit, so contract only** (inferred from call sites; what is needed is in §14):
  - `defaultQuests`, `ATTRS`, `CLASS_CONTENT`: read by `freshState()` (`04-runtime.js:11`), `applyClassContent()`
    (`13-runtime.js:264-270`) and `dhWeakestAttr()` (`50-quests-hand.js:72`).
  - `luckToday()`: called by `dhLuckScore()` (`50-quests-hand.js:65`); returns truthy `{score}` or falsy.
  - `dayDone(date)`: called by `dhVerifyMet` (`50-quests-hand.js:140`) inside `try/catch`, coerced with `!!`.
  - `petData()` and `petDay()`: called by `dhVerifyMet` (`50-quests-hand.js:139`); reads `{alive, day, fedToday, petsToday,
    playsToday}`.
  - `state.waterLog` and `state.mealsMeta.waterGoalMl`: read by `dhVerifyMet` (`50-quests-hand.js:138`).
  - `checkBadges()`, `checkLevelUps()`, `BADGES`, `state.badges`: `save()` calls `checkBadges()` before `writeSave()` and
    `checkLevelUps()` if saved (`04-runtime.js:44`); `renderLedger` filters `BADGES` by `state.badges[x.id]` (`50-quests-table.js:366`).
  - `dhDealMode()`, `dhGate()`, `dhCinematic()`, `fxReduced()`, `QFX.supported()`: queried by the deal (`50-quests-hand.js:224-307`).
  - Life Director's quest bridge (`LifeMaxHost.completeQuest`, `readQuests`, `isMilestone`) and voice callers; `ECONOMY_SPEC.md`
    `LD-3`, `LD-4` and `QUEST-5` give their economy contracts.

### 1.3 Out, and why
- Visuals, markup, CSS animation, haptics, audio, WebGL, camera and card physics (`14-runtime.js`, `50-quests-hand.js`,
  `50-quests-table.js` before the ledger, and the art, fx, cine and gate files).
- The full text of `QUEST_LIBRARY` entries and `TABLE_OBJ` / `TABLE_TEX`: content, not rules.

---

## 2. Inputs, outputs and constants

- **QST-4** (**Read**, `04-runtime.js:11,86,239`, `13-runtime.js:264-270`, `50-quests-hand.js:94-173`) Save fields the quest rules
  read or write (`state.*`):
  - read and written: `quests` (`Quest[]`), `questDone` (`{periodKey: activityId}`), `classContent` (`string|null`), `dhMig`
    (`1|undefined`), `dailyDraw` (`{date, nonce, rerollsUsed, cards: [{tpl, id, n}], dealt?, up?, turned?}`), `activities`,
    `attrXP`, `skillXP`, `sparks`, `restedXP`;
  - read only here: `classId`, `perk`, `perksOwned`, `restDays`, `mealsMeta.waterGoalMl`, `waterLog` (`[{date, ml}]`), `badges`.
- **QST-5** (**Read**, `50-quests-hand.js:5-6,62,155`, `13-runtime.js:272`, `04-runtime.js:242`) Constants:
  - `DH_FREE_REROLLS = 1`, `DH_REROLL_COST = 50`, `DH_HOLD_MS = 900`;
  - `DH_CLASS_ATTR`: `athlete: ['physique','endurance','wellbeing']`, `scholar: ['intelligence','discipline','wellbeing']`,
    `entrepreneur: ['business','social','discipline']`, `explorer: ['adventure','endurance','wellbeing']`;
  - `DH_SLOT_NAMES = ['Maintenance', 'Class', 'Wildcard']`, `DH_NUMERALS = ['I', 'II', 'III']`;
  - card XP by tier `[0, 10, 20, 30][tier]`; stage unlock levels `[0, 1, 3, 5, 8, 12]`; custom quest XP clamp `1..500` (default `25`).
- **QST-6** (**Read**, `50-quests-hand.js:317`) `window.LifeMaxDailyHand` exposes `{deal: dhMaybeDeal, replay: dhReplayDeal,
  reveal: dhReveal, library: QUEST_LIBRARY, ensure: dhEnsure, reroll: dhReroll, cards: dhCards, verify: dhAutoVerify,
  verifyMet: dhVerifyMet, draw: dhDraw, weakest: dhWeakestAttr, cost: dhRerollCost}`.

---

## 3. Quest model, class quest packs and period keys

### 3.1 The quest object

- **QST-7** (**Read**, `04-runtime.js:15,239,242`, `13-runtime.js:268,271-274`, `14-runtime.js:36-48`, `50-quests-hand.js:90-92`) A
  quest in `state.quests` is a plain object:
  - `id` (string, required): `q1`…`q6` or other starter ids, `cc_*` for class quests, `custom_<ms>` for custom quests,
    `dh_<YYYY-MM-DD>_<slot>_<nonce>` for Daily Hand cards;
  - `name` (string, required): prefixed with `'Quest: '` on its ledger record (`QUEST-2`);
  - `desc` (string, optional): `''` on cards and ledger when absent, `'One-time milestone'` on unlocked epics (`14-runtime.js:47`);
  - `cadence` (`'daily' | 'weekly' | 'epic'`, or another string): the period key (QST-12), the board section and the XP caps
    (`QUEST-3`). "Milestones" in the quest area means `cadence === 'epic'` quests, not catalog milestones (`CATALOG-1`);
  - `xp` (number): shown and paid as `Math.max(0, Number(q.xp) || 0)`;
  - `attribute` (string, optional): `'discipline'` when missing, in completion and undo (`04-runtime.js:239`); card display falls
    back to `ATTRS[0]` for the label and `discipline` for the arc `[QD04]`;
  - `kind` (string, optional): `'custom'`, `'hand'`, `'nutrition'`, `'hydration'` or absent;
  - `day` (string, optional): `'any'` or a lowercase weekday (`'sun'`…`'sat'`) for `questToday` (QST-11);
  - `stage` (number, optional): the unlock tier of an epic (QST-15);
  - `hand` (boolean, optional): `true` only on Daily Hand cards;
  - `slot` (`0|1|2`), `tpl`, `verify`, `tier` (`1|2|3`): only on Daily Hand cards (`50-quests-hand.js:90-92`).

### 3.2 Starter quests and class quest packs

- **QST-8** (**Read**, `04-runtime.js:15,20-39`, `50-quests-hand.js:319`) `freshState()` sets `quests` to a deep copy of
  `defaultQuests`, `questDone: {}` and `classId: null` (`classContent` and `dhMig` unset). Loading an existing save runs
  `migrateSave()`, which runs `applyClassContent()`; a first launch with nothing stored skips the migration, so every
  `defaultQuests` entry stays until `dhEnsure()` runs at the segment's boot.
- **QST-9** (**Read**, `13-runtime.js:264-270`) `applyClassContent()`:
  1. `cc = CLASS_CONTENT[state.classId] || null`; `cur = state.classContent || null`.
  2. If `(cc ? state.classId : null) === cur`, return (with both unset, `null === null` returns without stripping `q1`…`q6` `[QD01]`).
  3. `state.quests = (state.quests || []).filter(q => !/^(q[1-6]|cc_.*)$/.test(q.id))`; custom quests, Hand cards and other ids stay.
  4. If `cc`: take `cc.quests` without the dailies other than nutrition and hydration ones
     (`!(q.cadence === 'daily' && q.kind !== 'nutrition' && q.kind !== 'hydration')`), shallow-copy each and put them first.
  5. Else: for each non-daily `defaultQuests` entry not already present by id, push a shallow copy.
  6. `state.classContent = cc ? state.classId : null`.
- **QST-10** (**Read**, `14-runtime.js:121,124-125`) `switchClassTo(id)` sets `classId`, `build`, clears the plan, runs
  `applyClassContent()`, saves and re-renders Meals, Recipes, Quests and Plan. Today's dealt Hand cards are not redrawn `[QD02]`.
  `devSwitchClass()` toasts `'Already ' + name` for the same class, else switches and toasts
  `'Switched to ' + name + ' · class content refreshed'`.

### 3.3 Weekday filter and period keys

- **QST-11** (**Read**, `13-runtime.js:271`) `questToday(q)` is true iff `!q.day || q.day === 'any' ||
  q.day === ['sun','mon','tue','wed','thu','fri','sat'][new Date().getDay()]` (local time, case-sensitive).
- **QST-12** (**Read**, `04-runtime.js:86`, `QUEST-1`, `TIME-2`, `TIME-8`) `questKey(q)` at call time:
  - daily: `q.id + '_' + getToday()` (resets at local midnight);
  - weekly: the local Monday of this week (`first.setDate(d.getDate() - ((d.getDay() + 6) % 7))`, `toLocaleDateString('en-CA')`):
    `q.id + '_' + monday`, resetting at local Sunday→Monday midnight;
  - any other cadence, `'epic'` included: `q.id + '_once'` (never resets).
  `isQuestDone(q)` is `!!state.questDone[questKey(q)]`.
- **QST-13** (**Read**, `04-runtime.js:86,239,243`) `questDone` maps period key → activity id. Old keys are never pruned `[QD03]`;
  a key goes only when unchecked, when its activity is deleted, or when the save is replaced.
- **QST-14** (**Read**, `04-runtime.js:224`) `renderStats()` sets `achievementCount` and `questsStat` to
  `Object.keys(state.questDone).length` (every historical key `[QD03]`), and the daily mini's `'Quests completed'` to the number of
  daily quests done today (ignoring `questToday` and `kind`).

---

## 4. Stage locks, completion and undo

### 4.1 Stage locks

- **QST-15** (**Read**, `13-runtime.js:272-273`, `CURVE-9`) `stageLvl(s) = [0, 1, 3, 5, 8, 12][s] || 1`;
  `questLocked(q) = (q.stage || 1) > 1 && characterLevel().level < stageLvl(q.stage)`.
- **QST-16** (**Read**, `04-runtime.js:239`, `14-runtime.js:2-4`) Ticking a locked quest: `toggleQuest` toasts
  `'Locked until you reach level ' + stageLvl(q.stage)`, calls `renderQuests()` and returns without saving itself. `qosToggle`
  only passes the tick on; the toast is `toggleQuest`'s. `renderQuests()` runs `dhAutoVerify()` (QST-25), which can complete and
  save Hand cards.

### 4.2 Completion and celebration

- **QST-17** (**Read**, `14-runtime.js:2-14`) `qosToggle(id, checked)`: finds `q`, notes `wasDone` and `locked`, calls
  `toggleQuest(id, checked)`; when `checked && !wasDone && !locked && isQuestDone(q)` and motion is not reduced, it shows
  `#qosCelebration` with `<strong>QUEST CLEARED</strong><small>+<xp> XP EARNED</small>` (listed XP), restarts its animation, and
  hides it again after 900 ms.
- **QST-18** (**Read**, `04-runtime.js:239`, `QUEST-2`, `QUEST-3`) `toggleQuest(id, checked)`:
  - checking an undone quest: `qid = 'quest_' + Date.now()`, `questDone[key] = qid`, `xp = ecoQuestXP(q)`; when `xp` is 0 and the
    listed XP is above 0, toast `'One epic quest reward per day'` (epic) or `'Quest XP limit reached for ' + ('this week' |
    'today')`; the note is `#qnote-<id>`'s trimmed value; credit `attrXP[A]` and `skillXP['Quest Completion']`; push the activity
    (`type: 'quest'`, `cad`, `epic`, `skill: 'Quest Completion'`, `name: 'Quest: ' + q.name`, `icon: 'flag'`, `duration: 0`,
    `xp`, `primary: A`, ISO `date`, `notes: qnote || 'Quest completed'`, `xpSplit: [{attr: A, xp}]`, `id: qid`); when `xp > 0`,
    toast `'Quest complete · +' + xp + ' XP'`;
  - unchecking a done quest: delete the key, subtract the listed XP from `attrXP[A]` and `skillXP['Quest Completion']` (floored at
    0), remove the activity, toast `'Quest undone · XP removed'`;
  - `save()` in every case, even when nothing changed `[QD05]`.

### 4.3 Undo

- **QST-19** (**Read**, `04-runtime.js:243`, `UNDO-1`) `deleteActivity(id)` removes every `questDone` key whose value is `id`,
  so the quest can be done again in that period.
- **QST-20** (**Read**, `14-runtime.js:101`, `50-quests-table.js:376-400`) Epic checkboxes on the board and the ledger's toggle
  (`dhLedgerToggle` / `dhRest.toggle`) go through `qosToggle` and so the same `toggleQuest` and `ecoQuestXP` rules.

---

## 5. Custom quests

- **QST-21** (**Read**, `04-runtime.js:240-242`, `QUEST-4`) On the board: `showCustomQuest()` shows `#customQuestCard` and focuses
  `#newQuestName`; `createQuest()` reads the trimmed name; empty: toast `'Give your quest a name'`, nothing saved. Else it pushes
  `{id: 'custom_' + Date.now(), name, desc: 'Custom quest created by you.', cadence: #newQuestCadence.value,
  xp: Math.min(500, Math.max(1, Number(#newQuestXP.value) || 25)), kind: 'custom'}`, clears the name, hides the card, saves
  (result ignored, no take-back `[QD06]`) and toasts `'Quest created'`.
- **QST-22** (**Read**, `50-quests-table.js:371,376`) In the 3D table: the form has `#nqName` (`maxlength="60"`), `#nqCad` (Daily
  or Weekly, Daily first) and `#nqXp` (number, 1–500, 25). `dhRest.create()` refuses an empty trimmed name with the same toast,
  pushes the same object as QST-21, closes the form, saves, toasts `'Quest created'` and refreshes the ledger.
- **QST-23** (**Read**, `04-runtime.js:242`, `50-quests-table.js:376`) XP: `''`, `'0'`, `0` and text give 25; negatives clamp to 1;
  above 500 clamps to 500; fractions are kept (`'12.7'` stores 12.7 `[QD07]`). Neither path sets `attribute`, `day`, `stage` or
  `hand`, so a custom quest always trains `discipline` `[QD08]`, shows every day, is never locked and is never a Hand card. Two
  created in the same millisecond share an id `[QD09]`.
- **QST-24** (**Read**, `14-runtime.js:34-51`, `50-quests-table.js:356-376`) No quest screen edits or deletes a custom quest
  `[QD10]`. Custom quests survive `applyClassContent()` and `dhEnsure()` and go only with a whole-save replacement.

---

## 6. Quest board

- **QST-25** (**Read**, `14-runtime.js:34-35`) `renderQuests()` first runs `dhAutoVerify()` and `dhRenderHand()` in a
  `try/catch` (`console.warn('[hand]', e)`). `#dailyHand` shows `dhCards()` sorted by slot.
- **QST-26** (**Read**, `14-runtime.js:36-43`) Sections, each in `state.quests` order:
  1. `daily = quests.filter(q => q.cadence === 'daily' && q.kind !== 'nutrition' && questToday(q))` (hydration dailies stay);
  2. `#dailyQuests` shows `daily.filter(q => !q.hand)`; when that is empty it shows nothing if `daily` has cards, and
     `'No daily missions yet. Forge one of your own.'` only when `daily` is empty;
  3. `weekly = quests.filter(q => q.cadence === 'weekly' && q.kind !== 'nutrition')` (no `questToday`); empty:
     `'No weekly missions yet. Build your next objective.'`;
  4. `nut = quests.filter(q => q.kind === 'nutrition' && (q.cadence !== 'daily' || questToday(q)))`; `#nutritionCard` hidden when empty.
  A quest with another cadence and not nutrition shows nowhere `[QD11]`.
- **QST-27** (**Read**, `13-runtime.js:274-279`) A row (`questRow`): checked when done, wired to `qosToggle`; the listed XP; locked
  quests are not shown disabled (the tick is refused, QST-16); while undone, a note field `qnote-<id>` (`maxlength="300"`,
  `'Add a private note (optional)'`).
- **QST-28** (**Read**, `14-runtime.js:44-49`) `#achievements`: epics sorted by `(stage || 0)` (stable). Locked: subtitle
  `'Unlock at level ' + stageLvl(stage)`, lock icon, no checkbox; else `desc || 'One-time milestone'`, trophy when done, flag
  otherwise. Empty: `'Your first epic milestone awaits.'`. A nutrition epic shows in both Nutrition and here `[QD11]`.
- **QST-29** (**Read**, `14-runtime.js:15-19`) `qosUpdateSummary(daily, weekly, nut)`: `dDone` = done in `daily`; `avail` = listed
  XP of every undone quest in all three, with no caps or perks `[QD12]`; `pct = daily.length ? Math.round(dDone / daily.length * 100) : 0`.
- **QST-30** (**Read**, `14-runtime.js:20-31`) Outputs: `dDone + ' / ' + daily.length`, `pct + '%'` (and the bar width), the totals,
  `avail.toLocaleString()`, counts as `n + ' quests'` (always plural); the line is `'Today’s missions cleared. Well played.'` when
  `pct >= 100 && daily.length`, else `'Keep that momentum going.'` when `dDone`, else `'Your next quest is waiting.'`.

---

## 7. Daily Hand: library, seed, generator, attribute

- **QST-31** (**Read**, `50-quests-hand.js:8-62`) `QUEST_LIBRARY`: 53 tuples `[attr, tier, name, desc, verify]` mapped to
  `{id: 't' + (i + 1), attr, tier, name, desc, verify, xp: [0, 10, 20, 30][tier]}`. Per attribute (tier 1 / 2 / 3): physique 3/3/1,
  endurance 3/2/1, intelligence 3/3/1, business 2/3/1, social 3/2/1, discipline 3/3/1, adventure 2/3/1, wellbeing 4/3/1. Every
  tier is 1–3, so a card is worth 10, 20 or 30 XP.
- **QST-32** (**Read**, `50-quests-hand.js:63`) `dhSeed(str)`, FNV-1a over UTF-16 code units: `h = 2166136261`; per unit
  `h ^= code; h = Math.imul(h, 16777619)`; return `h >>> 0`.
- **QST-33** (**Read**, `50-quests-hand.js:64`) `dhRng(seed)`, Mulberry32: `s = seed >>> 0`; each call `s = (s + 0x6D2B79F5) >>> 0;
  t = Math.imul(s ^ (s >>> 15), s | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296`.
- **QST-34** (**Read**, `50-quests-hand.js:65`) `dhLuckScore()`: `L = luckToday(); return L && L.score ? L.score : 5500`.
- **QST-35** (**Read**, `50-quests-hand.js:66-71`) `dhRecentAttrXP(days)`: `s` = the local date `days - 1` days ago; every ATTRS
  id starts at 0; for each activity not of type `quest` and dated `>= s` (no upper bound `[QD13]`), add
  `Math.max(0, Number(x.xp) || 0)` for each `x` of `a.xpSplit || [{attr: a.primary, xp: a.xp}]` whose `attr` is an ATTRS id (an
  empty `xpSplit` adds nothing `[QD14]`). Passive records (steps, sleep, coach) count.
- **QST-36** (**Read**, `50-quests-hand.js:72`) `dhWeakestAttr()`: ATTRS ids sorted by `dhRecentAttrXP(7)` ascending, ties by
  ATTRS order; the first.
- **QST-37** (**Read**, `50-quests-hand.js:83`) `dhClassAttrs()`: `DH_CLASS_ATTR[state.classId] || null`.

---

## 8. Daily Hand: draw, cards, migration

- **QST-38** (**Read**, `50-quests-hand.js:74-82`, `REST-DAY-1`) `dhPick(rng, attrs, taken, date, slot)`:
  1. `rest = ecoRestDay(date)`;
  2. `pool` = library entries with `(!attrs || attrs includes t.attr) && t.id not in taken && !(rest && t.tier === 3)`, in order;
  3. empty: the same without the attribute filter;
  4. `luck = (dhLuckScore() - 1000) / 8999` (not clamped);
  5. weight: tier 1 `1.2 - luck * 0.4`, tier 2 `1`, tier 3 `0.5 + luck`, and a tier-3 card in the maintenance slot × 0.3;
  6. `r = rng() * total` (one draw); walk the pool subtracting weights, return the first with `r <= 0`, else the last.
- **QST-39** (**Read**, `50-quests-hand.js:84-89`) `dhDraw(date, nonce, slotIdx, taken)`: slot `['maintenance','class','wildcard']`;
  `rng = dhRng(dhSeed(date + '|' + dhLuckScore() + '|' + nonce + '|' + slotIdx))`; attributes `[dhWeakestAttr()]`, `dhClassAttrs()`
  or `null`; `dhPick(...)`.
- **QST-40** (**Read**, `50-quests-hand.js:90-92`) `dhMakeQuest(t, date, slotIdx, nonce)` = `{id: 'dh_' + date + '_' + slotIdx + '_' +
  nonce, hand: true, slot, tpl: t.id, name, desc, cadence: 'daily', xp: t.xp, kind: 'hand', attribute: t.attr, verify, tier}`.
- **QST-41** (**Read**, `50-quests-hand.js:94-114`) `dhEnsure()`: no library → `null`. Migration (QST-42), stale cards (QST-43).
  If `dailyDraw` is missing, from another date, or without three cards: draw slots 0–2 with nonce 0, each excluding the earlier
  ones, and set `dailyDraw = {date, nonce: 0, rerollsUsed: 0, cards}` (no `dealt`, `up`, `turned`). Nothing is saved here
  `[QD15]`. Then any card without its quest gets one (`dhMakeQuest`). Returns the draw.
- **QST-42** (**Read**, `50-quests-hand.js:98-100`) Until `dhMig` is set, remove quests with id matching `/^(q[1-3]|cc_.*)$/`,
  daily, and not nutrition or hydration (`q4`…`q6` are not matched `[QD01]`); set `dhMig = 1`.
- **QST-43** (**Read**, `50-quests-hand.js:101`) Remove Hand cards from other days
  (`!q.hand || q.id.indexOf('dh_' + date + '_') === 0`); their completions stay in `activities` and `questDone`.
- **QST-44** (**Read**, `50-quests-hand.js:115,319`) `dhCards()` = the Hand cards. At load the segment runs `dhEnsure()` and
  `renderQuests()` in a `try/catch`.

---

## 9. Daily Hand: rerolls, deal state, card interactions

- **QST-45** (**Read**, `50-quests-hand.js:116`, `SPARK-5`) `dhRerollCost()`: 0 while `rerollsUsed < 1`, else 50 (also 50 with no draw).
- **QST-46** (**Read**, `50-quests-hand.js:117-131`, `SPARK-3`) `dhReroll(slotIdx)`: no card → false; a done card → toast
  `'Finished cards cannot be rerolled'`, false; a cost Sparks cannot pay (`ecoSpend`, which toasts `'Not enough Sparks'`) → false.
  Then `rerollsUsed++`, `nonce++`; `taken` = all three current templates; draw; replace the quest and the card
  (`{tpl, id: 'dh_' + date + '_' + slot + '_' + nonce, n: nonce}`); `up[slot] = false` (`turned[slot]` is left `[QD16]`); toast
  `'Rerolled for ' + cost + ' Sparks'` or `'Free reroll used'`; `save()` (whose auto-verify can complete the new card at once);
  true.
- **QST-47** (**Read**, `50-quests-hand.js:162-173`) `dhIsUp(q)` = done, or `up[slot]`. `dhReveal(slot)`: already up and turned →
  `dhTurn`; already up → nothing; else `up[slot] = true` and `writeSave()`. `dhTurn(slot)`: only when up; toggles `turned[slot]`
  and `writeSave()`.
- **QST-48** (**Read**, `50-quests-hand.js:155-160,190`) Self-reported cards (`verify` `self` or `none`) have a Hold button: held
  900 ms without release, leave or cancel, it calls `qosToggle(id, true)`; Enter or Space on the board's button does so at once
  (not on the 3D table's dock). Neither dock offers an undo once done; only deleting the activity (QST-19) or
  `toggleQuest(id, false)` undoes it.
- **QST-49** (**Read**, `50-quests-hand.js:224-307`, `50-quests-table.js:293`) Deal state: on the Quests view with `!dealt`, the deal
  waits 260 ms (`50-quests-hand.js:228`). Reduced motion, no Web Animations, or the gate's `'skip'`: `dealt = true`, `writeSave()`
  (`:254`, `:260`). Leaving mid-deal keeps `dealt` false, so it runs next visit. A finished flat deal sets `dealt = true` and saves
  (`:306`), with `up` still all false; a finished or skipped 3D reading sets `dealt = true` and `up = [true, true, true]`
  (`50-quests-table.js:293`). A replay resets `up` in memory at its start and restores the flags at its end.
- **QST-50** (**Read**, `50-quests-hand.js:313-316`) `navigate` is wrapped: the `q3d` class on entering Quests with the 3D table,
  the core navigation, then `dhRenderHand()` after 120 ms when the view is Quests.
- **QST-51** (**Read**, `50-quests-hand.js`, `dhRenderHand`) The Hand is rebuilt only when its signature changes: each card's id,
  done, up and turned, the reroll cost and `dealt`.

---

## 10. Daily Hand: verification and auto-verify

- **QST-52** (**Read**, `50-quests-hand.js:133-137`) `dhVerifyMet(q, date)` splits `verify` (default `'self'`) on `:`. `self`,
  `none`: false. `acts` = activities not `quest` or `coach`, dated `date`. `act:<type>:<min>`: one activity of that type lasting at
  least `min` minutes (sessions do not add up `[QD17]`). `any:<min>`: any activity that long (no template uses it).
- **QST-53** (**Read**, `50-quests-hand.js:138`) `water:<pct>`: `goal = Math.max(1, Number(waterGoalMl) || 2000)`; total ml logged on
  `date` (negatives not clamped) `>= goal * (Number(pct) || 100) / 100`.
- **QST-54** (**Read**, `50-quests-hand.js:139-141`) `pet:<what>`: false unless the pet exists, is alive and `p.day === petDay()`;
  `feed` → `fedToday > 0`, `pet` → `petsToday > 0`, anything else → `playsToday > 0`. `plan`: `!!dayDone(date)` (false if it throws).
  Anything else: false.
- **QST-55** (**Read**, `50-quests-hand.js:143-151`) `dhAutoVerify()`: returns 0 while busy; else, busy, `dhEnsure()`, and each
  undone card whose condition is met is completed with `toggleQuest(id, true)` (not `qosToggle`, so the banner does not show,
  though the toast does); not busy in `finally`; returns the count.
- **QST-56** (**Read**, `50-quests-hand.js:152-153`, `SAVE-2`, `SAVE-4`) `save` is wrapped: the core save first, then
  `dhAutoVerify()` when not busy, then the core result. Auto-verify runs even after a failed save `[QD18]`; callers that take back
  (`recordActivity`, `LifeMaxHost.awardActivity`) restore their snapshot and so drop those completions too.
- **QST-57** (**Read**, `14-runtime.js:35`, `50-quests-hand.js:153,317`) `dhAutoVerify()` runs on every save (when not busy),
  every `renderQuests()`, and `LifeMaxDailyHand.verify()`; any save or board render on the day completes matching cards.

---

## 11. 3D table ledger and rest mode

- **QST-58** (**Read**, `50-quests-table.js:315-336,389`) Rest mode: progress counts the three Hand slots and how many are done,
  `n + ' / ' + t + ' FULFILLED'` (unlike the board's count, which includes custom dailies), plus `'WEEKLY ' + done + ' / ' + total`
  when there are weekly quests. Every 450 ms it looks for a slot whose quest or done state changed, and redraws that card, the open
  dock, the progress, and the ledger if open.
- **QST-59** (**Read**, `50-quests-table.js:357-366`) `renderLedger()`, top to bottom: `WEEKLY MISSIONS` (weekly, not nutrition;
  empty `'None this week.'`); `YOUR OWN QUESTS` (daily, not nutrition, not Hand, `questToday`; empty `'Forge one with New Quest.'`);
  `NUTRITION` only when there are any; `EPIC MILESTONES` (by stage; empty `'Your first epic awaits.'`); `BADGES <earned> / <total>`
  (earned in `BADGES` order; empty `'None earned yet.'`).
- **QST-60** (**Read**, `50-quests-table.js:357-366`) A row: its toggle disabled when locked, showing `✓` when done, `🔒` when
  locked, else nothing, calling `dhLedgerToggle(id)`; no note field, so ledger completions record `'Quest completed'`; subtitle
  `'Unlocks at level ' + n` when locked (the board says `'Unlock'`), else `desc || ''`; reward `'+' + xp` (no `' XP'`). The ledger is
  replaced only when its HTML changed, keeping its scroll and focus.
- **QST-61** (**Read**, `50-quests-table.js:369-395`) In rest mode `window.dhRest` = `{back, redraw, ledger, open, create, toggle,
  again, _stop}` (null otherwise), reached through `dhSceneBack()`, `dhSceneRedraw(n)`, `dhSceneLedger(on)`, `dhSceneNew(on)`,
  `dhSceneCreate()` and `dhLedgerToggle(id)`.
- **QST-62** (**Read**, `50-quests-table.js:371-379`) Opening the ledger scrolls it to the top when it was hidden and puts down a
  lifted card. `dhRest.toggle(id)` calls `qosToggle(id, !done)` and after 60 ms refreshes the ledger and the progress.

---

## 12. Defects and accidents

Besides the economy defects of `ECONOMY_SPEC.md` (D09, D13–D17, D20, D35):

- **QD01** `q4`…`q6` starter dailies: a starter profile (no class) returns early in `applyClassContent()`, and `dhEnsure()`'s
  migration strips only `q1`…`q3` dailies, so a daily among `q4`…`q6` would survive until a class is chosen.
- **QD02** Switching class keeps today's Class card until tomorrow or a reroll.
- **QD03** `questDone` grows forever, and `achievementCount` / `questsStat` count every historical key.
- **QD04** Missing attribute: completion uses `discipline`, the card label `ATTRS[0]`.
- **QD05** A `toggleQuest` that changes nothing still saves (and so auto-verifies).
- **QD06** `createQuest`, `dhReroll`, `dhReveal` and `dhTurn` do not take back a failed save.
- **QD07** Custom quest XP is not rounded (fractions reach the ledger).
- **QD08** Custom quests always train `discipline`.
- **QD09** Two custom quests in the same millisecond share an id.
- **QD10** Custom quests cannot be edited or deleted.
- **QD11** A nutrition epic shows twice; a quest with an unknown cadence and not nutrition shows nowhere.
- **QD12** "Available XP" ignores every cap and perk.
- **QD13** Recent attribute XP counts future-dated activities.
- **QD14** An empty `xpSplit` counts nothing in recent XP (`rebuildAttrXP` falls back to the primary, `04-runtime.js:62`).
- **QD15** A new day's draw is not saved until something else saves.
- **QD16** A reroll leaves the card's `turned` flag, so the new card starts turned.
- **QD17** `act:` and `any:` need one long enough session; shorter ones do not add up.
- **QD18** Auto-verify runs even when the save failed.

Found by the Daily Hand golden cases (`tests/fixtures/quests-golden.json`, WP3; **Executed**, each cited by its cases). They add
detail QST-35, QST-41 and QST-46 leave out; none changes a rule above.

- **QD19** `dhEnsure` checks a card's quest by the card's `id` but builds a missing one from `c.n`
  (`'dh_' + date + '_' + slot + '_' + c.n`). A card whose `n` disagrees with its `id` (or has none: `_undefined`) gets another
  copy of its quest on every `dhEnsure`. A card whose `tpl` is not a library id never gets a quest.
- **QD20** A record with a missing or unreadable `date` counts in `dhRecentAttrXP` for ever: its day is `'Invalid Date'`, which
  sorts after every day key (TIME-4, TIME-5). A record dated with a bare `YYYY-MM-DD` is read as UTC midnight, so west of UTC it
  falls on the day before.
- **QD21** One malformed quest makes every `dhEnsure` throw until the save is mended, so no card is dealt, rerolled or
  auto-verified: a `null` entry in `quests`, a `hand` quest whose `id` is not a string (or an array), a `quests` that is not an
  array, a draw whose `cards` is a three-character string or an object with `length: 3`, a `null` card, or a `classId` that names
  an `Object.prototype` member (`toString`, `__proto__`). A quest flagged `hand` whose id is not today's `dh_<date>_` is removed,
  whatever it is.
- **QD22** A reroll on 49.5 Sparks is paid (`ecoSparks()` rounds to 50, SPARK-3) and leaves −0.5 Sparks.
- **QD23** A draw without `nonce` rerolls to `NaN` (`dh_<date>_<slot>_NaN`, stored `n: null`); a draw without `rerollsUsed` is
  `NaN` in memory (every reroll paid) but stored as `null`, which reads as 0 after a reload (the next reroll free again).

---

## 13. Golden cases

Pin before each case: the clock and time zone (with cases across local midnight, Sunday→Monday, and a clock change); the luck
score (1000, the 5500 fallback, 9999); `xpSchemaV2: true`, `dhMig`, `classId` and a matching `classContent`; the activities of
the last eight days (the `daysAgo(6)` boundary, ATTRS tie order, quest records excluded, passive ones included); rest days, a
plan rest day or a 20-minute recovery; Sparks, rested XP, perks, the attribute XP that fixes the character level at each stage
threshold, and earlier quest records (the caps); water goal and log, the pet, `dayDone`; and hold back boot and journal saves.

Families: seed and generator vectors (`dhSeed`, the first five `dhRng` outputs, `dhDraw` for every slot, nonces 0–3, rest and
active days, each class and none, three luck values); period keys and the weekday filter; stage locks at each threshold; class
packs and the migration; custom quest refusal and XP clamping; rerolls (free, 50 Sparks, 49 Sparks, a done card, the
three-template exclusion, an instant auto-verify, the stale turn); every verification scheme including split sessions and the
cap crossed mid-loop; board, summary and ledger projections.

---

## 14. Open questions

- **OQ-1** `defaultQuests`: ids, cadences, attributes and stages; is any of `q4`…`q6` daily (QD01)?
- **OQ-2** `CLASS_CONTENT`: hydration quests, weekday limits, stages on non-epics, nutrition epics (QD11)?
- **OQ-3** `luckToday().score`: range and determinism (always 1000–9999, seeded by the date?).
- **OQ-4** `views/quests.html`: the `#newQuestCadence` options (is `epic` offered?) and `#newQuestName`'s maxlength.
- **OQ-5** `petDay()`'s format; can the pet's counters move while it is not alive?
- **OQ-6** `LifeMaxHost.readQuests`: which quests it exposes to Life Director (Hand cards, locked epics, off-day quests) and how
  `isMilestone` is decided.
- **OQ-7** Can `dhGate()` or `dhCinematic()` write `dailyDraw`?
- **OQ-8** For the native port: reproduce QD02, QD07, QD15 and QD16 first for golden parity, or fix them behind a flag?
