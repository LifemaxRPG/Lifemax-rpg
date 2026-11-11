# Life Director engine specification: the scheduler in `engine.js`

Status: **parts 1 to 5 of several** (WP-LD1 to WP-LD5). Sections 0 to 2 and the rules for time and hashing (sections 4 and 5) are
part 1; availability (rules, preferences, sleep, the painted grid) is part 2, section 10; demand (tags, goals, quests, candidate tasks,
the per-task numbers, exp and pow) is part 3, section 11; placement (preparation, energy, feasibility, scoring, the construction phase of
`generateSchedule`, rejections) is part 4, section 12; the rest of `generateSchedule` (the repair, the fill pass, explanations, segments,
capacity, the output and its `inputHash`) and `validateSchedule` are part 5, section 13. The scheduler's other parts (rescheduling, ledger, store) follow as their work packages start (`LIFE_DIRECTOR_NATIVE_PLAN.md` section 5). It describes the engine as it behaves at branch `codex/director-dial-controls`,
commit `14a6908`. The page (`lifemax_modular_v73`) is the authority: this document describes it and changes nothing. Where today's
behaviour looks like a bug or an accident it is marked `[LDnn]` and explained in section 7; the rule as written is still what the
engine does.

Purpose: the logic-phase method used for the economy and the quests. Someone who never reads the JavaScript should be able to
reimplement each part from this document alone and reproduce, case for case, the golden cases recorded from the real page. Every
rule gives its source as `file:line`. `engine.js` and `logic.js` are `lifemax_modular_v73/src/features/life-director/engine.js` and
`logic.js`; other paths are from the repository root.

Contents

0. How to read this (labels, numbers, dates, strings, how cases are compared)
1. Boundary: what is in, what is out, and why (all of `engine.js`, by part)
2. Inputs and outputs of the time and hash layer
3. Constants
4. Time: the clock, zones, wall clock, local days, the slot grid, weeks
5. Hashing and identities
6. The page's `Intl` and `java.time`: the mapping and every difference
7. Defects and accidents
8. Golden cases: the index (8.1 the kernel, 8.2 availability, 8.3 demand, 8.4 placement, 8.5 repair, output and validation)
9. Open questions for the owner
10. Availability: rules, preferences, sleep and the painted grid (part 2)
11. Demand: tags, goals, quests and candidate tasks (part 3)
12. Placement: preparation, energy, feasibility, scoring and the construction (part 4)
13. Repair, output and validation (part 5)

---

## 0. How to read this

Rules carry IDs and labels. IDs: `LDN-n` numbers and semantics, `LDT-n` time, `LDH-n` hashing and identities (part 1); `LDG-n` the
grid and value rules, `LDA-n` availability, `LDP-n` preferences, `LDS-n` sleep (part 2, section 10); `LDD-n` tags, goals and templates,
`LDQ-n` quests, `LDC-n` candidate tasks, `LDE-n` per-task and per-cycle numbers, `LDF-n` floating point (part 3, section 11); `LDR-n`
preparation and energy, `LDK-n` feasibility, `LDU-n` scoring, `LDW-n` the construction, `LDJ-n` rejections (part 4, section 12); `LDX-n`
the repair and the fill pass, `LDO-n` the output, `LDV-n` validation (part 5, section 13). Later parts
will add their own prefixes. Each rule is labelled:

- **Executed**: a golden file holds cases recorded from the real page that show it; the case ids or families are given. Part 1's
  file is `lifemax_modular_v73/tests/fixtures/ld-kernel-golden.json`, part 2's `ld-availability-golden.json`, part 3's
  `ld-demand-golden.json.gz`, part 4's `ld-placement-golden.json.gz`, part 5's `ld-schedule-golden.json.gz`. The native code replays
  every one of them (section 8).
- **Read**: derived from reading the code only; no case pins it yet.

### 0.1 Numbers

- **LDN-1** (**Executed**: families `num`, `cal`; ECONOMY_SPEC NUM-1 to NUM-4) Numbers are IEEE-754 binary64 and follow
  `ECONOMY_SPEC.md` NUM-1 to NUM-4: evaluation in the order written, `round` is JavaScript `Math.round` (ties toward +infinity),
  `Number(text)` as NUM-3, NaN propagating through `max`, `min` and arithmetic. Results that JSON cannot hold (NaN, the infinities,
  `-0`) are part of the contract: the golden file writes them `{"$num": "NaN"}` and so on, and `-0` differs from `0`
  (`slot/slotCeil/1793422800+-1` gives `-0`).
- **LDN-2** (**Executed**: `parts/localParts/*@half-second`, `offset/tzOffsetSec/*@half-second`, `@ms`) Engine epochs are **seconds**,
  held as doubles that are whole numbers in every real input (`engine.js:13`). Page timers are milliseconds (`logic.js:13`, plan
  section 3.4). An epoch is turned into a JavaScript date as `new Date(epochSec * 1000)`: the product is truncated toward zero to a
  whole millisecond, and a date outside +-8.64e15 ms is Invalid (every `Intl` call on it throws a `RangeError`).
- **LDN-3** (**Executed**: `wall/zonedToEpoch/*@26/3/8/600`, `@2026/3/8/90.5`, `@2026/13/1/600`, `cal/ymdAdd/odd:*`) Calendar steps are
  `Date.UTC(year, month0, day, hours, minutes, seconds)`: every argument is truncated toward zero; a year from 0 to 99 is read as
  1900 to 1999 (`Date.UTC(26, ...)` is 1926); a month outside 0..11 and a day outside the month roll over into the next or the
  previous year or month (`Date.UTC(2026, 12, 1)` is 2027-01-01, day 0 is the last day of the month before, minute 4320 is three days
  later); a non-finite argument or a result beyond +-8.64e15 ms gives NaN. Year 275760-09-13 is the last valid date.
- **LDN-4** (**Executed**: family `cal`, `parseYmd` cases) Text read as a number is `Number(text)` (ECONOMY_SPEC NUM-3): surrounding
  JavaScript white space is ignored (including U+00A0 and U+3000), blank is 0, `0x`, `0o`, `0b` prefixes and exponents are read,
  `Infinity` is infinity, anything else is NaN.
- **LDN-5** (**Executed**: `hash/cyrb53/*@-1`, `@4294967303`, `@1.9`, family `rng`) 32-bit integer steps: `Math.imul(a, b)` is the low
  32 bits of the product as a signed integer, `^` and `|` work on ToInt32 of their operands, `>>>` on ToUint32 and gives an unsigned
  value. ToInt32 and ToUint32 take the whole part toward zero and reduce it modulo 2^32 (NaN and infinities give 0), so a seed of
  `1.9` is `1`, `4294967303` is `7`, `-1` is `0xFFFFFFFF`.
- **LDN-6** (**Executed**: families `hash`, `stable`, `fuzz`) Text is a sequence of UTF-16 code units. `str.length` and `charCodeAt`
  count units: a character outside the Basic Multilingual Plane (an emoji) is two units, a lone surrogate is one unit and is hashed
  as itself. Kotlin's `String` and `Char` are the same units, so nothing is normalised and nothing is decoded as code points.

### 0.2 How the golden cases are compared

Strings and booleans are compared exactly, numbers as doubles bit for bit (no tolerance), arrays in order, objects member by member
(key order is not observable in this part). A call that throws is recorded as `{threw: <error name>}`: `RangeError` from `Intl`
for a zone it refuses, `Error` for the engine's own refusals. The format of the file is the header comment of
`lifemax_modular_v73/tools/gen_ld_kernel_golden.js`.

---

## 1. Boundary

The engine (`engine.js:6-2232`) is the first of three modules in the file; the other two are the Life Director store (`2234-2340`)
and the LifeMax host adapter (`2342-2622`). The engine and the store both assign `module.exports` (lines 8 and 2240), so
`require('engine.js')` returns the store, not the engine (plan defect D5); the recorder loads only the first module, in a `vm`
(`lifemax_modular_v73/tools/ld_engine_vm.js`).

### 1.1 In this part: specified completely

| Area | Functions (`engine.js`) | Rules |
|---|---|---|
| The clock the engine is given | (`logic.js:13-15` `nowEpoch`, `deviceTz`, `tz`) | LDT-0 |
| Slot size | `SLOT_SEC` 16, `SLOTS_PER_HOUR` 17 | LDT-1 |
| Zones and the wall clock | `_fmt` 66-78, `isValidTimeZone` 79-81, `localParts` / `_localParts` 82-98, `tzOffsetSec` 99-102 | LDT-2 to LDT-5 |
| Wall clock to instant | `zonedToEpoch` / `_zonedToEpoch` 103-127 | LDT-6 to LDT-10 |
| Calendar arithmetic | `ymdAdd` 128-131, `ymdKey` 132, `parseYmd` 133, `daysBetween` 134 | LDT-11 to LDT-14 |
| Local days and the grid | `buildHorizon` 137-149, `createGrid` 151-179 (the time fields), `slotOf`, `slotCeil`, `epochOf` 180-182 | LDT-15 to LDT-17 |
| Local day and week boundaries | `dayEndAfter` 1791-1795; the host's `weekBounds` 2418-2423; the page's `weekKey` (`logic.js:148`) | LDT-18, LDT-19, LDT-21 |
| Duration rounding | `ceilTo15` 63 | LDT-20 |
| Hash, ids, generator | `cyrb53` 34-44, `stableId` 46-48, `mulberry32` 49-58, `completionEventId` 1916-1920 | LDH-1 to LDH-7 |

### 1.2 Contracts only in this part (specified when their work package starts)

These use the kernel and are the next parts of this spec; the plan section names the package.

| `engine.js` lines | Part | Package |
|---|---|---|
| 59-62 `clamp`, `cmpStr` | specified in part 2 (LDG-1, LDG-2) | LD2, done |
| 60 `byId` | the sorts by id of part 3 (LDC-4, LDC-10) | LD3, done |
| 62 `deepClone` | with its callers, `taskSnapshot` and `snapshot` (LDO-8, LDX-2) | LD5, done |
| 151-179 `createGrid` (the typed arrays other than `localMin` and `dayOf`), 184-198 `paint` | part 2 (LDG-3, LDG-4) | LD2, done |
| 200-428 rules, preferences, sleep, `deriveAvailability`, maintenance, meals; `logic.js:577-588` `ruleOn` | part 2, section 10 | LD2, done |
| 431-789 tags, archetypes, templates, goals, quests, candidates, `freshIds` 768-776; `weeksOf` 1152-1159, `goalInfoMap` 1190-1203, `prepTask` 899-908, the shares 1225-1227, `questCoverage` 1496-1503, `recomputeBwc` 820-836, `Math.exp`/`Math.pow` | part 3, section 11 | LD3, done |
| 791-1087 energy (but `recomputeBwc`), state, feasibility, scoring, `commit`, `occupy`; 1106-1109 `anchorsDependents`; 1160 `bwcIndexAt` | part 4, section 12 | LD4, done |
| 1163-1288 `prepareState` and `generateSchedule` up to its repair (the construction); 1394-1431 `buildRejection` | part 4, section 12 | LD4, done |
| 1088-1105 `removeInst`, 1110-1147 `taskSnapshot`, `snapshot`, `restore` (`stateFingerprint` only by the page's tests), 887-898 `recoveryBlocks` | part 5, section 13 | LD5, done |
| 1289-1393 repair and fill, 1432-1535 explanations, segments, capacity, output, `inputHash` 1529-1532 | part 5, section 13 | LD5, done |
| 1537-1618 validation | part 5, section 13 | LD5, done |
| 1619-1905 `rescheduleActivity`, hold placement (`dayEndAfter` 1791-1795 is in part 1, LDT-18) | part 6, section 14 | LD5 (WP-LD5b), done |
| 1908-2085 ledger, quest progress, misses (`completionEventId` is in this part) | ledger | LD6 |
| 2234-2340 `LifeDirectorStore` | store codec | LD7 |
| 2342-2622 `LifeMaxHost` (its `weekBounds` 2418-2423 and `playerId` 2377-2380 are in this part) | host adapters | LD11 |

### 1.3 Out, and why

- 2087-2215 the built-in scenarios (`scenarioBase`, `G`, `SCENARIOS`, `buildScenario`): test inputs, used as fixtures, not ported.
- 1149 `clockMs` (`performance.now()`): only times a plan (`computeTimeMs`); excluded from every comparison (plan 4.2(7)).
- The caches `_fmtCache`, `_lpCache` (cleared above 50,000 entries), `_zCache` (cleared above 20,000): no observable effect on any
  result (LDT-10).
- Display formatting in `logic.js` (`fTime`, `fDay`, `fDate`, `fHm`, `Intl.DateTimeFormat(undefined, ...)`): device-locale words,
  part of the read models (LD8), not of the engine.
- 2234-2340 `LifeDirectorStore` (the store, WP-LD7, done): specified in its own file, `LIFE_DIRECTOR_STORE_SPEC.md` (rules `LST-n`,
  defects `LS01` to `LS12`, its golden file `ld-store-golden.json.gz`), because it is not scheduler code.

---

## 2. Inputs and outputs of the time and hash layer

Nothing in this layer reads the clock, the device zone, storage or randomness. Every function is a function of its arguments and of
the time zone data of the JavaScript engine it runs in (section 6).

| Function | Inputs | Output |
|---|---|---|
| `localParts(epochSec, tz)` | epoch seconds (double); zone name | `{y, mo, d, h, mi, s, dow}`: local year, month 1-12, day, hour 0-23, minute, second, weekday 0 = Sunday |
| `tzOffsetSec(epochSec, tz)` | as above | double: local wall clock read as UTC minus `epochSec` |
| `zonedToEpoch(y, mo, d, minuteOfDay, tz)` | numbers (any; LDN-3); zone | epoch seconds (double), NaN for an Invalid Date |
| `isValidTimeZone(tz)` | any value (text in practice) | boolean |
| `ymdAdd({y, mo, d}, n)` | numbers | `{y, mo, d, dow}`, every field NaN for an Invalid Date |
| `ymdKey({y, mo, d})` | numbers | text `y-mm-dd` |
| `parseYmd(key)` | text | as `ymdAdd` |
| `daysBetween(a, b)` | two `{y, mo, d}` | double (whole days, or NaN) |
| `buildHorizon(startEpoch, days, tz)` | epoch; count; zone | `[{index, ymd: {y, mo, d, dow}, key, dow, startEpoch, endEpoch, slots}]` |
| `createGrid(days, tz)` | a horizon; zone | grid with `H0`, `HN`, `n`, `localMin[n]`, `dayOf[n]` (and the availability arrays of LD2) |
| `slotOf(g, e)`, `slotCeil(g, e)`, `epochOf(g, s)` | grid start `H0`; epoch or slot | double |
| `dayEndAfter(input, epoch)` | `input.timezone`, else `input.playerAvailability.timezone`, else `'UTC'`; epoch | epoch |
| `weekKey(now)` (page), `weekBounds(now, tz)` (host) | epoch; zone (`weekKey` reads `tz()`, LDT-0) | text; `{start, end, nextEnd, monKey}` |
| `ceilTo15(min)` | number | number |
| `cyrb53(str, seed)` | text; number | double, a whole number below 2^53 |
| `stableId(prefix, str)` | text; text | text |
| `mulberry32(seed)` | number | a function returning doubles in [0, 1) |
| `completionEventId(playerId, instanceId, clientEventId, revision)` | text; text or nothing; text or nothing; number or nothing | text, or throws `Error('Ad-hoc completions need a clientEventId')` |

Exported as `LifeDirectorEngine.util` (`engine.js:2228`): `stableId`, `mulberry32`, `localParts`, `zonedToEpoch`, `ymdAdd`, `ymdKey`,
`tzOffsetSec`, `isValidTimeZone`, `buildHorizon`, `ceilTo15`, `deepClone`; `completionEventId` at the top level (2226). `logic.js` uses
`U.localParts` 31 times, `U.zonedToEpoch` 23, `U.ymdKey` 24, `U.ymdAdd` 9, `U.buildHorizon` 3, `U.tzOffsetSec` 2: everything those
read models compute about days and times rests on this layer.

---

## 3. Constants

| Name | Value | Source | Rule |
|---|---|---|---|
| `SLOT_SEC` | 900 (seconds, 15 minutes) | `engine.js:16` | LDT-1 |
| `SLOTS_PER_HOUR` | 4 | `engine.js:17` | LDT-1 |
| hash lane starts | `0xdeadbeef`, `0x41c6ce57` | `engine.js:35` | LDH-1 |
| hash steps | 2654435761, 1597334677 | `engine.js:38-39` | LDH-1 |
| hash mix | 2246822507, 3266489909; shifts 16 and 13 | `engine.js:41-42` | LDH-1 |
| hash top bits | `2097151` (21 bits), times 4294967296 | `engine.js:43` | LDH-2 |
| stable id seeds | 1 and 7; base 36; 11 digits each | `engine.js:47` | LDH-3 |
| Mulberry32 step | `0x6D2B79F5`; multipliers `t | 1`, `t | 61`; shifts 15, 7, 14 | `engine.js:52-56` | LDH-7 |
| duration step | 15 minutes, minimum 15 | `engine.js:63` | LDT-20 |
| `Intl` format | `'en-US'`, `hourCycle: 'h23'`, numeric y/m/d/h/m/s, weekday short | `engine.js:71-74` | LDT-4 |

---

## 4. Time

### 4.1 The clock and the zone

- **LDT-0** (**Read**, `logic.js:13-15`) The engine never reads a clock; its caller passes `now`. The page's `now` is
  `Math.floor(Date.now() / 1000)` (whole seconds, rounded down), and its zone is `ST.availability.timezone`, or, when that is empty,
  the device zone (`Intl.DateTimeFormat().resolvedOptions().timeZone`, `'UTC'` if that fails). Timers use `Date.now()` in
  milliseconds (plan 3.4).
- **LDT-1** (**Executed**: `slot/SLOT_SEC/value`, `slot/SLOTS_PER_HOUR/value`) A slot is 900 seconds; an hour is 4 slots. Every
  plan instant the grid produces is a slot boundary from the horizon's start.

### 4.2 Zones

- **LDT-2** (**Executed**: families `zone` and `tzfragile/zone`, 693 names: 659 valid, 34 not) `isValidTimeZone(tz)` is whether
  `new Intl.DateTimeFormat('en-US', {timeZone: tz})` can format a date. It accepts:
  1. every IANA zone name and link the JavaScript engine's ICU data holds, and ICU's legacy names (`EST`, `MST`, `HST`, `PST`, `IST`,
     `ACT` ... the three-letter ids, the `SystemV/*` ids, `US/Pacific-New`, `Canada/East-Saskatchewan`, `ROC`, `Factory`), each
     **without regard to ASCII letter case** (`utc`, `AMERICA/NEW_YORK`, `aSiA/kOlKaTa`, `systemv/est5` are valid);
  2. a UTC offset `+HH`, `+HHMM` or `+HH:MM`, with `+`, `-` or U+2212 as the sign, hours 00 to 23 and minutes 00 to 59 (`+23:59` and
     `-23:59` are valid, `+24:00`, `+05:60`, `+5`, `+5:30`, `05:30` are not).
  
  Not valid: `Z`, `GMT+5`, `UTC+5`, `UTC+05:30`, `+05:30:00`, `Etc/GMT+13`, `Etc/GMT-15`, empty and blank texts, names with spaces,
  doubled or trailing slashes, a trailing NUL, non-ASCII letters (`İstanbul`, full-width `ＵＴＣ`, `América/Bogotá`), `localtime`,
  `posixrules`, `Etc/Unknown`, `EST5`, `UTC0`. An unknown name throws `RangeError` and `isValidTimeZone` catches it.
  `tz === undefined` formats in the device zone and is valid (**Read**; no caller passes it: `deriveAvailability` and `dayEndAfter`
  default to `'UTC'`).
- **LDT-3** (**Executed**: family `alias`, every name of LDT-2 at 2026-01-15 and 2026-07-15 12:00 UTC) Each valid name follows its
  zone's rules: a link follows its target (`US/Eastern`, `EST5EDT` and `America/New_York` agree), a legacy name follows the zone ICU
  maps it to (`PST` is Los Angeles with daylight saving, `EST` is a fixed -05:00, `IST` is Kolkata, `BST` is Dhaka, `NST` is
  Auckland), an offset is fixed. Every engine function given an invalid name throws `RangeError` (`alias/*` cases of invalid names).

### 4.3 Wall clock

- **LDT-4** (**Executed**: family `parts`, 2248 cases; `fuzz`) `localParts(epochSec, tz)` is the local wall clock of the instant, to
  the second, from `Intl.DateTimeFormat('en-US', {timeZone, hourCycle: 'h23'}).formatToParts`: year, month 1-12, day, hour 0-23
  (an hour `24` would be read as 0; ICU with `h23` never gives it), minute, second (whole seconds, rounded down: LDN-2), and the
  weekday 0 = Sunday to 6 = Saturday. Local midnight is hour 0 of the new date (`parts/localParts/*@*-1s` and the midnights
  themselves). The result is cached per zone and second (LDT-10).
- **LDT-5** (**Executed**: family `offset`, 2023 cases; `alias`) `tzOffsetSec(epochSec, tz)` =
  `Date.UTC(local wall clock to the second) / 1000 - epochSec`: the zone's offset in seconds, east positive (New York in winter
  -18000, Kolkata 19800, Kiritimati 50400, `Etc/GMT+12` -43200). A fraction of a second in `epochSec` carries into the result:
  `offset/tzOffsetSec/UTC@half-second` is `-0.5` (no real input has one, LDN-2).

### 4.4 Wall clock to instant

- **LDT-6** (**Executed**: family `wall`, 4900 cases; `fuzz`) `zonedToEpoch(y, mo, d, minuteOfDay, tz)`: let `W` =
  `Date.UTC(y, mo - 1, d, 0, minuteOfDay) / 1000`, the wanted wall clock written as if it were UTC (LDN-3: minutes may exceed 1440
  or be negative and roll the date; `wall/zonedToEpoch/*@*+1441`, `@2026/3/8/-1440`, `@2026/3/8/4320`). When `W` is no date (a NaN
  argument, or out of range) the page **throws `RangeError`**: `tzOffsetSec(W)` formats an Invalid Date (corrected in part 2: part 1
  said NaN; `materialize/materializeRule/nan@*`, LD04). The native `WallClock.instantOf` returns NaN there and the availability layer
  turns that into the throw (`AvailabilityTime`). The result is an instant at which the zone's wall clock reads `W` (to the minute), chosen by LDT-7 to LDT-9.
- **LDT-7** (**Executed**: every `wall` case without `-gap` or `-fold`) A wall clock that occurs once gives that instant: `W - offset`.
- **LDT-8** (**Executed**: 488 `*-gap` cases, every 2026 and 2027 spring change of the matrix) A wall clock inside a spring-forward
  gap gives `W - (offset before the gap)`: the wall clock moved forward by the length of the gap. New York 2026-03-08 02:30 is
  03:30 EDT (`...America/New_York@2026-3-8+150-gap` = 1772955000, 07:30 UTC); London 2026-03-29 01:30 is 02:30 BST; Lord Howe's
  30-minute gap moves a time by 30 minutes. The gap's first minute gives the first instant after the gap: Santiago 2026-09-06 has no
  local midnight, and 00:00 gives 01:00 (`...America/Santiago@2026-9-6+0-gap` = 1788667200).
- **LDT-9** (**Executed**: 422 `*-fold` cases) A wall clock inside an autumn fold (it occurs twice) gives the occurrence **whose
  offset is the one in force at the instant `W` itself** (the wanted wall clock read as UTC). Because the change happens at a UTC
  instant `T`, and `W` is before `T` exactly when the zone is west of Greenwich:
  - west of Greenwich (the Americas, `Etc/GMT+n`): the **first** occurrence. New York 2026-11-01 01:30 is 01:30 EDT
    (`...America/New_York@2026-11-1+90-fold` = 1793511000, 05:30 UTC); Santiago 2026-04-04 23:30 is 23:30 at -03.
  - at Greenwich and east of it (London, Europe, Australia, New Zealand): the **second** occurrence. London 2026-10-25 01:30 is
    01:30 GMT (`...Europe/London@2026-10-25+90-fold` = 1792891800, 01:30 UTC, not 00:30); Sydney 2026-04-05 02:30 is 02:30 AEST
    (1775320200, 16:30 UTC on the 4th); Lord Howe 01:45 is 01:45 +10:30 (1775315700).
  
  The engine's own comment (`engine.js:104-105`, "ambiguous times resolve to the first occurrence") is true only west of Greenwich
  [LD01]. How the engine arrives there (`engine.js:113-127`): it tries the candidates `c1 = W - off(W)`, `c2 = W - off(c1)`,
  `c3 = W - off(c2)`; of those whose wall clock matches `W` to the minute it returns the earliest; if none matches (a gap) the earliest
  candidate whose wall clock is later than `W`, else the latest candidate. In a fold the iteration stops at its first candidate, which
  has the offset at `W`; in a gap it returns `W - (offset before)`. Every case of the corpus agrees with LDT-7 to LDT-9 as stated; a
  zone with two changes within a few hours (none in 2025-2028) would need the candidate rule itself (**Read**).
- **LDT-10** (**Read**, `engine.js:66-127`) The caches (`_fmtCache` per zone, `_lpCache` per zone and second, `_zCache` per zone and
  arguments) change no result. `localParts` hands every caller the same cached object; no caller in `engine.js` or `logic.js`
  modifies it.

### 4.5 Calendar arithmetic (no zone)

- **LDT-11** (**Executed**: `cal/ymdAdd/*`, 270 cases) `ymdAdd({y, mo, d}, n)` = the UTC date of `Date.UTC(y, mo - 1, d + n)` (LDN-3),
  as `{y, mo, d, dow}` with `dow` 0 = Sunday. Rolls over months and years, knows leap years (2024-02-29, 2028-02-29, 2000-02-29 exist,
  2100-02-29 does not). An Invalid Date gives every field NaN (`cal/ymdAdd/odd:275760-9-14+0`). `{y: 26}` is 1926 (LDN-3).
- **LDT-12** (**Executed**: `cal/ymdKey/*`) `ymdKey({y, mo, d})` = `String(y) + '-' + pad2(mo) + '-' + pad2(d)`: the year as JavaScript
  writes the number, not padded (`5-01-02`, `999-01-01`, `-44-03-15`, `12345-06-07`); month and day padded on the left with `0` to at
  least two characters (`2026-100-100` stays). NaN fields give `NaN-NaN-NaN`. Keys of real dates from 1000 to 9999 compare as text in
  date order.
- **LDT-13** (**Executed**: `cal/parseYmd/*`, 35 cases) `parseYmd(key)`: split `key` at every `-`, read the first three pieces with
  `Number` (LDN-4; a missing piece is NaN), then `ymdAdd(..., 0)`. So `2026-02-30` is 2026-03-02, `2026-13-01` is 2027-01-01,
  `2026-00-10` is 2025-12-10, `0026-01-05` is 1926-01-05, `2026--5` is 2025-12-05 (the empty month is 0), `--` is 1899-11-30,
  ` 2026-01-05 ` and `2026-0x0A-05` (2026-10-05) parse, `2026-01-5.5` is the 5th, `2026-01-05-07` ignores the rest; `2026-01`,
  `2026`, `x`, `2026-01-05T10:00` and `2026-01-Infinity` are Invalid [LD02].
- **LDT-14** (**Executed**: `cal/daysBetween/*`) `daysBetween(a, b)` = `round((Date.UTC(b) - Date.UTC(a)) / 86400000)`: whole days from
  `a` to `b`, negative when `b` is earlier, each read by LDN-3 (unnormalised dates allowed); NaN when either is Invalid.

### 4.6 Local days and the slot grid

- **LDT-15** (**Executed**: family `horizon`, 119 cases; `fuzz`) `buildHorizon(startEpoch, days, tz)`: day 0 is the local date of
  `startEpoch` (the date it falls in, even one second before midnight); for `i` from 0 while `i < days`, day `i` is the date `i` days
  later, with `startEpoch = zonedToEpoch(date, 0)` and `endEpoch = zonedToEpoch(next date, 0)` (so a day whose midnight is skipped
  starts at the end of the gap, LDT-8, and a day ends where the next one starts), `slots = (endEpoch - startEpoch) / 900`, `key =
  ymdKey(date)`, `dow` the weekday. `days` of 0 gives no days. Recorded day lengths: 96 slots (1239 days), 92 (17, spring), 100 (14,
  autumn), 94 and 98 (Lord Howe's half-hour changes). Santiago 2026-09-06 runs from 01:00 local (92 slots).
- **LDT-16** (**Executed**: family `grid`, 22 cases) `createGrid(days, tz)` time fields: `H0` = day 0's start, `HN` = the last day's
  end, `n = (HN - H0) / 900`; slot `i` belongs to the day whose range holds it (`dayOf`). `localMin[i]`, the slot's minute of the
  local day: on a day whose `tzOffsetSec` at its start equals that at its last second (`endEpoch - 1`), slot `k` of the day has
  minute `15k`; on any other day it is `h * 60 + mi` of `localParts` at the slot's start, so a fold repeats an hour of minutes and a
  gap skips one.
- **LDT-17** (**Executed**: family `slot`, 102 cases) With `H0` the grid's start: `slotOf(e) = floor((e - H0) / 900)`, `slotCeil(e) =
  ceil((e - H0) / 900)`, `epochOf(s) = H0 + s * 900`, in doubles (a slot can be negative, fractional or `-0`: `slotCeil` of one second
  before `H0` is `-0`).

### 4.7 Local day and week boundaries

- **LDT-18** (**Executed**: `week/dayEndAfter/*`; `fuzz`) `dayEndAfter(input, epoch)`: the zone is `input.timezone`, else
  `input.playerAvailability.timezone`, else `'UTC'`; the result is `zonedToEpoch` of the local date after `epoch`'s local date, at
  minute 0 (the next local midnight by LDT-8).
- **LDT-19** (**Executed**: `week/weekKey/*`, `week/weekBounds/*`; `fuzz`) Weeks start on Monday. The Monday of `now`'s week is
  `ymdAdd(local date, -((dow + 6) mod 7))` (LDT-21). The page's `weekKey(now)` (`logic.js:148`) is that Monday's `ymdKey`; the host's
  `weekBounds(now, tz)` (`engine.js:2418-2423`) is `{start: zonedToEpoch(Monday, 0), end: zonedToEpoch(Monday + 7, 0), nextEnd:
  zonedToEpoch(Monday + 14, 0), monKey: ymdKey(Monday)}`. A week that holds a clock change is 167 or 169 hours long (167.5 or 168.5
at Lord Howe).
- **LDT-20** (**Executed**: family `num`, 41 cases) `ceilTo15(min) = max(15, ceil(min / 15) * 15)`: any length rounds up to a whole
  number of 15-minute slots and is at least 15 (`0`, `-30` and `0.0001` give 15, `15.0000001` gives 30, `1e15` gives
  1000000000000005). NaN stays NaN, `Infinity` stays `Infinity`, `-Infinity` and `-0` give 15.
- **LDT-21** (**Executed**: `parts/*`, `week/*`) Weekdays are 0 = Sunday to 6 = Saturday everywhere in this layer (`WD`,
  `engine.js:67`; `getUTCDay`, 130). `(dow + 6) % 7` is the number of days since Monday.

### 4.8 Time zone data

- **LDT-22** (**Executed** for the matrix, see section 6.3) Which offsets a zone has is data, not a rule: the JavaScript engine's ICU
  tz database. On the phone that is the WebView's own ICU copy, which Android updates with the WebView, separately from the
  system's tz data that `java.time` reads. The golden file's header records the Chromium version it was recorded in.

---

## 5. Hashing and identities

- **LDH-1** (**Executed**: family `hash`, 200 cases; `fuzz`, 150) `cyrb53(str, seed)`, on 32-bit integers (LDN-5):
  1. `h1 = 0xdeadbeef ^ seed`, `h2 = 0x41c6ce57 ^ seed` (the seed through ToInt32).
  2. For each UTF-16 code unit `ch` of `str` (LDN-6), in order: `h1 = imul(h1 ^ ch, 2654435761)`, `h2 = imul(h2 ^ ch, 1597334677)`.
  3. `h1 = imul(h1 ^ (h1 >>> 16), 2246822507) ^ imul(h2 ^ (h2 >>> 13), 3266489909)`; then, with that new `h1`,
     `h2 = imul(h2 ^ (h2 >>> 16), 2246822507) ^ imul(h1 ^ (h1 >>> 13), 3266489909)`.
  4. Result per LDH-2.
  
  `cyrb53('', 0)` = 3338908027751811, `cyrb53('a', 1)` = 5368154436228575 (`hash/cyrb53/0:@0`, `hash/cyrb53/1:a@1`).
- **LDH-2** (**Executed**: as LDH-1) The result is `4294967296 * (h2 & 2097151) + (h1 >>> 0)`: 21 bits of `h2` above the 32 unsigned
  bits of `h1`, a whole number from 0 to 2^53 - 1, exact as a double.
- **LDH-3** (**Executed**: family `stable`, 96 cases; `fuzz`, 150) `stableId(prefix, str) = prefix + '_' + base36(cyrb53(str, 1)) +
  base36(cyrb53(str, 7))`, each half in lower-case base 36 left-padded with `0` to 11 digits (2^53 needs at most 11, so the length is
  always `prefix.length + 23`). `stableId('t', '')` = `t_26c8wzs8fs10wivrqs6s3t`. The comment's "~106 bits" (`engine.js:45`) is the
  two 53-bit halves; they are not independent hashes of different data, only of different seeds.
- **LDH-4** (**Read**) Where the engine forms ids (each a `stableId` of a text built by concatenation; numbers in the text are
  JavaScript's `String(n)`, whole epochs written as plain digits):
  - task ids `stableId('t', base + k)` for `k = 0, 1, ...`, skipping one whose instance id `'i' + id.slice(1)` is already used, at most
    `count + 200` tries (`freshIds`, `engine.js:768-776`; LD3);
  - re-placed tasks `stableId('t', (origInstanceId || source.id) + ':r:' + k + ':' + now)` (1744) and change records
    `stableId('chg', type + ':' + (instanceId || '') + ':' + now + ':' + actions.length)` (1781) (LD5);
  - residual tasks `stableId('t', 'residual:' + instanceId)` (1971), bounties `stableId('bounty', 'QUEST_BOUNTY:' + questId)` (2014),
    miss penalties `stableId('pen', 'MISS_PENALTY:' + instanceId)` (2055) (LD6);
  - the plan's `inputHash` `stableId('in', ...)` (1529-1532; plan defect D12) (LD4);
  - the host's player id `'lm_' + stableId('p', String(created || '') + '|' + String(name || '')).slice(2, 14)`, the first 12 base-36
    digits of the first half (2377-2380), else `'player'` (LD11).
- **LDH-5** (**Executed**: family `event`, 74 cases) `completionEventId(playerId, instanceId, clientEventId, revision)`:
  - when `instanceId` is truthy (any non-empty text, `'0'` included): `stableId('ev', 'DONE:' + playerId + ':' + instanceId +
    (revision ? ':rev' + revision : ''))`; a revision of 0, null or absent adds nothing;
  - else when `clientEventId` is truthy: `stableId('ev', 'ADHOC:' + playerId + ':' + clientEventId)`;
  - else it throws `Error('Ad-hoc completions need a clientEventId')` (`event/completionEventId/player|null|null`).
  
  One instance therefore has one event id for ever (per revision): repeating a completion is recognised by the id, which is why a
  wrong bit in LDH-1 to LDH-3 would pay a session twice.
- **LDH-6** (**Read**, `engine.js:1529-1532`) `inputHash` hashes `JSON.stringify` of the input with an array replacer; its text depends
  on JavaScript's JSON writer and key order and is specified with generation (LD4, plan D12).
- **LDH-7** (**Executed**: family `rng`, 22 cases, two of 200 outputs) `mulberry32(seed)` is Mulberry32, the same generator as the
  Daily Hand's (`QUESTS_SPEC.md` QST-33): state `a = seed >>> 0` (ToUint32: `1.5` is 1, `-1` is 4294967295, `4294967296` is 0); each
  call `a = (a + 0x6D2B79F5) >>> 0; t = imul(a ^ (a >>> 15), a | 1); t ^= t + imul(t ^ (t >>> 7), t | 61);` returns
  `((t ^ (t >>> 14)) >>> 0) / 4294967296`. `t + imul(...)` is a double sum, but `^=` wraps it to 32 bits, so a 32-bit wrapping sum is
  the same. It is used only by the scenarios (2088); planning never draws a random number.

---

## 6. The page's `Intl` and `java.time`

The native kernel (`android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector`) takes offsets from `java.time` and
states the page's rules itself. This section is the mapping, and every place the two differ.

### 6.1 The mapping

| Page | Native | Why it is equal |
|---|---|---|
| `Intl` zone name lookup | `ZoneNames.resolve`: ASCII case folded, then a table of the JVM's zone ids, `ZoneId.SHORT_IDS` and four further names (`ROC`, `US/Pacific-New`, `Canada/East-Saskatchewan`, `Factory`) plus `GMT+0`/`GMT-0`; offsets parsed by the page's grammar into a fixed offset in seconds (`PageZone`) | `zone` and `alias` families, every name |
| offset at an instant (`formatToParts`) | `ZoneRules.getOffset(Instant)` (a fixed offset for offset names) | `offset`, `parts`, `alias`, `fuzz` |
| `localParts` fields | the instant (milliseconds truncated, LDN-2) plus the offset, as a UTC `LocalDateTime` | `parts` |
| `zonedToEpoch` | `ZoneRules.getValidOffsets(local)`: one offset, `W - offset`; none (gap), `W - transition.offsetBefore`; two (fold), the offset `getOffset(Instant W)` (LDT-9) | `wall`, `fuzz`, and the mutation checks |
| `Date.UTC`, `getUTC*` | `EcmaTime` (truncation, 1900 years, roll-over, range, TimeClip) over `LocalDate` epoch days | `cal`, `wall` edge cases |
| `Number(text)` | `jsNumber` (economy) | `parseYmd` cases |

### 6.2 Where they differ, and how each is handled

1. **Fold resolution.** `java.time`'s default (`ZonedDateTime.ofLocal(local, zone, null)`, "the earlier offset") picks the first
   occurrence; the page picks the second at and east of Greenwich (LDT-9). Handled: the native rule is the page's;
   `KernelMutationTest.javaTimesFoldRuleIsNoticed` replaces it with `java.time`'s default and the fold cases east of Greenwich fail.
2. **Zone names.** `ZoneId.of` is case sensitive, accepts `Z`, `GMT+5`, `UTC+05:30` and offsets with seconds, rejects
   `PST`-style names without `SHORT_IDS`, and lacks `ROC`, `US/Pacific-New`, `Canada/East-Saskatchewan`, `Factory`, `GMT+0` and
   `GMT-0` from its id list. Handled: `ZoneId.of` is never called on the page's text (6.1).
3. **Offsets past 18 hours.** The page accepts `+23:59`; `java.time.ZoneOffset` stops at +-18:00. Handled: an offset name becomes a
   fixed offset in seconds (`PageZone.fixed`), never a `ZoneOffset`.
4. **Dates.** `LocalDate.of` refuses month 13 and day 0 and has no 1900 rule for years 0 to 99. Handled: `EcmaTime` (LDN-3).
5. **tz database versions** (data, not semantics; LDT-22). Measured with the zone-transition scan of every JVM and ICU zone id
   (604 names) from 2024 to 2029, the page in Chromium 153 and node 22 (ICU 77.1, tz 2025b) agree with each other; the JVM used for
   the tests (tz database 2026b) disagrees in exactly two places:
   - `America/Vancouver` (and its link `Canada/Pacific`): the JVM's 2026b data has British Columbia on permanent daylight time from
     2026-11-01 (no change back to -08:00); the page's data still changes back on 2026-11-01 and every year after.
   - `Europe/Chisinau` (and `Europe/Tiraspol`): every change from 2024 on is one hour later in the JVM's data (01:00 UTC) than in the
     page's (00:00 UTC).
   
   Every zone of the matrix agrees from 1970 to 2037. The corpus uses neither disagreeing zone, so it passes; a player in
   Vancouver or Chisinau would see native and page disagree. On the phone the two copies are the WebView's ICU and Android's tz
   data module, updated independently; the corpus must be run there once (plan 4.3(6)), and the owner must decide which copy wins
   (section 9).
6. **Node and Chromium.** Chromium's ICU knows the tz database's `Factory` zone, node 22's does not; the file holds Chromium's answer and
   the web test lists the three cases. With the same node data as the recording no other case differs. Another node's tz data may
   differ (the web test then compares node's ICU and tz versions with the header's): on the CI runner, Morocco's Ramadan suspensions
   (`Africa/Casablanca`, 16 fuzz cases; `Africa/El_Aaiun` follows the same rules), which every tz release predicts afresh; on node 21
   (tz 2024a), `America/Asuncion` (permanent -03 from tz 2024b) and `America/Coyhaique` (a zone of its own from 2025a). These are
   listed, with that evidence, in `tools/ld_kernel_cases.js` (`TZ_FRAGILE`, `TZ_CHANGED_IN`); a disagreement in any other zone fails.
   The Morocco cases also make the corpus sensitive to Chromium's own tz updates: when Chromium's data changes, the byte-exact
   re-record fails there first, and the file is regenerated.
7. **Offset zones need a recent JavaScript engine.** Node 20 (V8 11.3) and node 21 (V8 11.8) refuse `+05:30`-style zone names, which
   Chromium 153 and node 22 accept (LDT-2). An Android WebView old enough to refuse them would throw for such a zone where the
   corpus says it works. The page only stores names that its own `Intl` produced (`resolvedOptions().timeZone`, an IANA name), so
   this matters only for a hand-made zone; the device pass should confirm the WebView version.
8. **Zones whose offsets depend on the tz release: native follows the phone's data there.** The CI runner's JDK (Java 21.0.13)
   disagreed with Chromium on 13 Casablanca cases, so a strict replay of such zones would make CI depend on whichever tz release the
   runner's JDK ships. The offset transitions of all 604 zone ids from 2024 to 2029 were compared across five tz data sets (node 21:
   tz 2024a; node 22: 2025b; Chromium 153; the JVM: 2026b; the system's zoneinfo: 2026e). The zones that differ in any of them,
   and two whose rules are revised in most releases, are **tz-fragile** (`TZ_FRAGILE` in `tools/ld_kernel_cases.js`, each with
   its evidence):

   | Zone | Evidence |
   |---|---|
   | `Africa/Casablanca`, `Africa/El_Aaiun` | Morocco's Ramadan suspensions, predicted afresh by every release; 2026e ends +01 in September 2026; the CI JDK differs |
   | `America/Asuncion` | permanent -03 from 2024b (2024a differs) |
   | `America/Coyhaique` | a zone of its own from 2025a (2024a lacks it) |
   | `America/Vancouver`, `Canada/Pacific` | permanent daylight time in 2026b and 2026e, not in Chromium's data |
   | `Europe/Chisinau`, `Europe/Tiraspol` | change instants an hour apart between releases |
   | `Asia/Gaza`, `Asia/Hebron` | not seen to differ; rules follow Ramadan and are revised in most releases |
   | `Factory` | present in some ICU builds only |

   Their 79 cases are the family `tzfragile`: the web test still requires them byte for byte from Chromium, node may differ on them
   when its tz version differs from the recording's, and the native replay reports them (matches and mismatches) and checks only
   that the kernel applies its rules (LDT-7 to LDT-9, LDT-15, LDT-18, LDT-19) to the JVM's own transitions
   (`KernelTzFragileTest`). Every other zone of the corpus is replayed strictly, with exact equality (`KernelGoldenTest`): its
   transitions were identical in all five data sets, including all 15 zones of the matrix, which have had no rule change for years
   (Brazil, `America/Sao_Paulo`, ended daylight saving in 2019; Samoa, `Pacific/Apia`, in 2021). A future tz release that changes a
   strict zone breaks the strict replay on a runner that has it; the fix is to move that zone to `TZ_FRAGILE` with the evidence.

   **On the phone** native reads Android's tz data and the page the WebView's ICU, so near a tz-fragile zone (and any zone whose
   rules changed between the two copies) they can disagree about where a local day or a clock change falls. Recommended policy
   (OQ-3): native always uses the phone's own data and never patches it; the page sends, with each comparison, its UTC offset at
   the instants involved (the day starts, `now`), as the economy shadow already does (`ECONOMY_SHADOW_PLAN.md` N4); where native's
   offset for the same zone and instant differs, the comparison is skipped and counted as `tzdata`, not reported as a mismatch.
   In addition every comparison in a tz-fragile zone that disagrees on a day boundary is counted `tzdata`. The count of `tzdata`
   skips is itself the evidence for when the two copies drift.

### 6.3 What was measured

- The 15,231 golden cases: identical in Chromium 153 (the file) and in node 22 except the three of 6.2(6); the native kernel on the
  JVM (tz 2026b) reproduces all 15,231 exactly, the 15,152 strict ones as a test and the 79 `tzfragile` ones as a report. On the CI
  runner's JDK 13 Casablanca cases differ (6.2(8)).
- The transition scan of 6.2(5) (a scratch measurement, not committed): hourly offsets with the change instant found to the second,
  for 604 zone ids, 2024 to 2029, in node, Chromium and the JVM; and 1970 to 2037 for the 15 matrix and plan zones.

---

## 7. Defects and accidents

- **LD01** (`engine.js:104-105`, `113-127`; LDT-9) A wall clock in an autumn fold resolves to the second occurrence at and east of
  Greenwich, against the comment's "first occurrence". Visible as: a rule or a sleep window starting at a repeated local time in
  London, Europe, Australia or New Zealand starts an hour later in UTC than the comment says, so a session placed in the repeated
  hour is placed after the clocks go back. Ported as is; ruling needed (section 9).
- **LD02** (`engine.js:133`; LDT-13) `parseYmd` reads anything `Number` reads: a rotation anchor typed `0026-01-05` is 1926, `2026--5`
  is 2025-12-05, and an invalid anchor is a NaN date, so `ruleAppliesOn` (LD2) treats the rule as never applying without saying so.
  Inputs come from the page's own date fields, so this only matters for a hand-edited or corrupt store.
- **LD03** (`engine.js:99-102`; LDT-5) `tzOffsetSec` of a fractional epoch is fractional (`-0.5` in UTC). Engine epochs are whole
  seconds, so this is harmless; noted because a port that returns the zone's offset directly differs on such inputs.

Part 2 (availability, section 10):

- **LD04** (`engine.js:113-118`, `209-210`, `237-238`; LDT-6, LDA-8, LDG-5) `zonedToEpoch` throws `RangeError` for a wall clock that is
  no date (part 1 said NaN; corrected in LDT-6). So one stored rule whose `startMin` or `durationMin` is not a number (`"abc"`, `{}`;
  LDA-1 keeps NaN) makes `materializeRule`, and with it every `deriveAvailability` and so every plan, throw
  (`materialize/materializeRule/nan@*`, `avail/deriveAvailability/rule-start-text`, `sleep-length-object`). The page's forms store
  numbers, so only a hand-edited or corrupt store reaches it. Ported as is (OQ-7).
- **LD05** (`engine.js:133`, `226`; LDA-6) A rotation whose `anchorDate` is truthy but not text (a number, a list, `true`) passes
  LDA-4 and then throws `TypeError` (`key.split is not a function`) when asked about any date, so every plan fails
  (`applies/*/oddrotation20` to `22`, `materialize/*/number-anchor`, `avail/*/number-anchor`, `preferred-number-anchor`). The page's copy
  (LDA-9) reads the same anchor through `String` and does not fail. Ported as is (OQ-7).
- **LD06** (`logic.js:577-588` against `engine.js:202-230`; LDA-9; plan D3) The page's copy of the rule question differs from the
  engine's for a stored `dayOfWeek`, a fractional, tiny, text or list `cycleLength`, and a non-text anchor (the LDA-9 table, 14 recorded
  rules), and reads `startMin`, `durationMin` and a commitment's kind differently (**Read**). For every rule the page itself writes
  (`Do.ruleSave`, `logic.js:2425-2445`: a `days` list of integers, a whole `cycleLength` `on + off` from steppers, a padded anchor,
  numbers for the minutes) the two agree on every recorded date (all `week`, `cadence`, `valid` cases), so the dial and the Money tab
  see today what the plan sees. (**Read**: `on` and `off` are `Math.max(1, Number(x) || 4)`, not rounded, so a fractional value from a
  caller other than the steppers would reach the fractional row.) Ruling needed (OQ-6).
- **LD07** (`engine.js:214-216`, `226-227`; LDA-4, LDA-6) `includes` finds NaN: a rotation whose index is NaN (an anchor that is no
  date, or a stored cycle from just above 0 to below 0.5, which passes `> 0` and rounds to 0) applies on **every** date when
  `activeDays` holds an entry that is not a number (`"x"` reads NaN), and on none otherwise (`applies/*/oddrotation4`, `oddrotation17`).
- **LD08** (`engine.js:208`, `223-224`, `229`; LDA-3, LDA-7) Stored values are compared, not converted: a day stored as text (`"3"`) or
  `true` survives LDA-3's filter but never matches a weekday, so the rule silently never applies on it; `validFrom` and `validTo` are
  compared as text, so an unpadded bound (`2026-1-5`) is wrong by months (`applies/*/unpadded-from`: from 1 October), a month bound `2026-10` ends before October (`month-to`), and a number bound never limits.
- **LD09** (`engine.js:255-268`, `284`, `381-390`; LDP-2, LDS-7) `normalizePreferences` checks only five numbers and two choices. The
  others are used as stored: `morningRoutineMinutes` stored as `"30"` gives deriveSleep a 50-hour lead (text join, LDS-7) while the
  routine block reads 30 minutes; `mealOffsetsMinutes` not a list throws `TypeError`; a non-numeric meal length or offset records a
  block with NaN bounds (LD10). The page's `Do.routine` and `Do.meals` store numbers in range.
- **LD10** (`engine.js:184-188`, `212`, `346-347`, `415-424`; LDG-3, LDA-12, LDA-15) `paint` records a block before it knows whether it
  writes a slot, and a NaN bound is never "empty" (`b <= a` is false), so `g.blocks` can hold blocks with NaN bounds
  (`avail/*/decompress-text`, `meal-text`). Also `decompressMin` is clamped, not converted (LDG-1), so `"30"` stays text in the rule and
  is read as 30 minutes at LDA-12.
- **LD11** (`engine.js:184-198`; LDG-3) Blocks that wrote nothing stay in `g.blocks` (common: every commute under a sleep, every
  routine already covered); a block's index matters only through `tag`. Placement (WP-LD4) reads `g.blocks`, and its part of this spec
  must say whether it ever reads one that no slot points to.
- **LD12** (`engine.js:30`, `335`; LDA-11) The shift class `NONE` is 0, which is falsy, so `LOAD[load] || MIXED_MODERATE` turns a rule
  or commitment with load `NONE` into `MIXED_MODERATE` (3) (`avail/deriveAvailability/load-none`): a "no load" shift drains energy
  like a moderate one. `NONE` is not offered by the page's forms (OQ-9).
- **LD13** (`engine.js:203-204`, `335`; LDA-2, LDA-11; **Read**) Two lookups use plain objects keyed by a stored text: a `ruleType` of
  `constructor`, `toString` or `__proto__` makes the kind a function or an object, and a `load` of `toString` a function (painted as
  load 0). Nothing writes such values; the native code never yields them (its tables hold only the listed names).
- **LD14** (`logic.js:2441`; LDA-12; **Read**) The page's rule form always stores `decompressMin` as a number (0 when the player sets
  none), so the engine's default 30 minutes of decompression after a shift of 3 hours or more (LDA-12) never applies to a rule the
  player made: only to rules without the member (the built-in scenarios, older stores). Probably not intended (OQ-8). Ported as is.

Part 3 (demand, section 11):

- **LD15** (`engine.js:568-586`, `657`; LDQ-1, LDC-4) `normalizeQuest` does not keep a quest's `anchor`, so every passive reminder's anchor
  is `WAKE` whatever the quest says (`candidates/compileCandidates/passive`: a quest with `anchor: 'SLEEP'`). Today the host's quests never
  set one, so nothing visible changes; a later passive quest meant for the evening would be reminded at waking. Ported as is.
- **LD16** (`engine.js:738-749`; LDC-6, LDC-10) A goal session that takes on the requirements of a quest **and** of its prerequisite (both
  of one activity, as a two-stage questline usually is) gets its own id, and its siblings', in `dependsOn`
  (`candidates/compileCandidates/prereq-same-tag`: both merged sessions carry A and B and depend on themselves). Placement (WP-LD4,
  `taskEarliestLatest`, `engine.js:912-916`) refuses a task whose dependency is not yet placed, so such a session can never be placed:
  the goal loses those sessions and the questline its coverage, silently. Looks accidental; ruling needed (OQ-10).
- **LD17** (`engine.js:667-676`; LDC-4) A daily quest measured in minutes (`requiredMinutes`, no count) asks for `ceil(minutes / length)`
  sessions today but **one** session on each later day: there `targetCount || 1` makes the count 1 before the minutes are looked at
  (`candidates/compileCandidates/daily-minutes`: 60 minutes a day gives four 15-minute sessions today, one on each later day). Looks
  accidental (OQ-11).
- **LD18** (`engine.js:711`, `733`, `760-762`; LDC-7, **Read**) `standaloneQuestTask`'s last parameter is meant to give a quest's own
  sessions the recovery spacing of a goal session of the same kind (its comment says so), but the non-daily call passes `null` and the
  daily call is then ignored by the `recurrence !== 'DAILY'` test: the inheritance never happens. With a compatible goal session a
  non-daily quest makes no own sessions at all (LDC-6 makes copies instead), so no output shows it; noted because a port that "fixes"
  it changes plans.
- **LD19** (`engine.js:486`, `525`; LDD-4) A stored goal without `id` and `goalId` is the goal `"undefined"`; two of them share every
  template id, spacing key and task id base, so their sessions collide (`expand/expandGoalActivities/set5@*`: two plans with goal
  `undefined`), and `goalInfoMap` keeps only the last. The page's `Do.goalAdd` always sets an id; only an old or hand-edited store.
- **LD20** (`engine.js:844`, `906`, `993`, `1006`; LDF-1, LDF-2) The page's plan depends on the JavaScript engine of the WebView. The
  recording Chromium 153 computes `Math.exp` and `Math.pow` correctly rounded; node 22's engine (V8 12.4), and so presumably WebViews of
  that generation, use fdlibm; on the recorded arguments they differ in the last bit on 222 of 2,121 `exp` and 115 of 720 `pow` values,
  and in `_urgBase` of 176 of the 510 `prep`, `scenario` and `fuzz` cases. In LD4's scores these last bits are mostly removed by `q6`
  (`engine.js:802`, rounding to 1e-6), but a score that lands on a rounding boundary, or an energy check at its 0.45 threshold
  (`engine.js:944`), can still flip, and with it a placement. Native follows the recorder (`PageMath`). Ruling needed (OQ-12): which
  WebView is the reference, and the device pass must show which kind the phone's WebView is.
- **LD21** (`engine.js:488`, `504`; LDD-4, LDD-6) A language subject keeps only ASCII letters, digits and `_` after lower-casing:
  `Español` becomes `espaol` (so a quest tagged `language.español.*` never matches the goal's sessions), and a subject in another script
  (`日本語`, `ΣΑΣ`) becomes empty, so the templates keep the plain `language.vocab` tags and a quest naming the language matches nothing
  (`template/goalTemplates/subject:*`, `goal/normalizeGoal/legacy*`). The page's form passes the player's text as typed
  (`logic.js:2508`). Looks accidental for non-English players (OQ-13).
- **LD22** (`engine.js:489`, `1225-1227`; LDD-4, LDE-2, LDE-4) A stored priority that is no number (`"abc"`, `{}`) normalises to NaN
  (`goal/normalizeGoal/priority=17`). `prepTask` reads it as 3 (`NaN || 3`), but the fairness shares do not: the priority sum is NaN,
  read as 1, so every goal's share becomes its bare priority (and that goal's NaN) instead of a fraction. The page's forms store 1 to 5.
- **LD23** (`engine.js:468`, `501`, `569`, `903`, `1497`; **Read**) As LD13: the demand layer looks up stored text in plain objects
  (`ARCHETYPES`, `TAG_PROFILE`, the tier map, `goalInfo`, the status map), so `constructor`, `toString` or `__proto__` as an archetype,
  a tag root, a tier, a goal id or a quest id reads an inherited member (and `goalInfoMap` writing `m['__proto__']` changes the map's
  prototype). Nothing writes such values; the native maps hold only their own entries, and the corpus has none.
- **LD24** (`engine.js:768-776`; LDC-9, LDC-4) `freshIds` tries at most `count + 200` ids, so when more than 200 of one base's ids are
  already used it returns fewer than asked, without a word (`ids/freshIds/used-first201#2`); and it rounds a fractional count up, so a
  stored `requiredCount` of 2.5 makes three sessions (`candidates/compileCandidates/fractional-count`). Neither happens with the page's
  own data (instance ids of one base number at most a week's sessions; counts are whole).
- **LD25** (`engine.js:1498-1501`; LDQ-4) `questCoverage` counts only sessions, not minutes: a quest asking for minutes with no count has
  `remainingCount` 0 and shows as covered with no session at all (`coverage/questCoverage/case7`). The quests section's coverage rows
  (plan 3.6) read it. Looks accidental (OQ-11).

Part 4 (placement, section 12):

- **LD26** (`engine.js:919-920`; LDK-1) A prior-volume task counts a completed session's **start** as its end, and reaches 7 days before its
  window for completed sessions but not for sessions on the plan (`windowStart - 0`, a leftover). So an assessment may start at the very
  instant an earlier counted session started. Harmless in practice (completed sessions are in the past, before `now`); looks accidental.
- **LD27** (`engine.js:1084-1085`; LDR-3) A stored session without `linkedGoalIds` (or with a value that is not a list or text) stops every
  plan with a `TypeError` (`prepare/prepare/locked-no-links`); a text is read unit by unit as goal ids. The page's own sessions always
  carry the list, so only a hand-edited or corrupt store hits it. Port as is.
- **LD28** (`engine.js:1176-1178`; LDR-3, **Read**) A pinned or started session's clash test starts at the first plannable slot, so the
  part of it before the clock may lie over sleep, a commitment or the morning routine, and `occupy` then writes LOCKED over those slots
  (their drain becomes the session's). Only the past is overwritten, so no placement changes; the painted grid read by later parts
  (`segments`, LD5) shows the session there.
- **LD29** (`engine.js:862`; LDR-5, **Read**) The check-in level is looked up in object literals: a level naming an inherited member
  (`constructor`, `toString`) counts as a check-in whose value is a function, so every energy from its instant on is NaN and no
  high-intensity session fits. As LD13 and LD23; the page's own check-in writes only the three levels.
- **LD30** (`engine.js:930-931`, `1022-1030`; LDK-2, LDW-1) Inside `evalInBwc`, `checkHard`'s WINDOW and BUSY tests can never fail (the
  loop bounds and the loop's own run test come first), and BUSY is tallied only at the shortest length, so LDJ-1's day attribution never
  sees WINDOW from a tried day. Informational; ported as written.
- **LD31** (`engine.js:1010`; LDU-2, **Read**) Fairness multiplies the whole utility, penalties included: a negative utility (a session far
  from its old time, heavily compressed) times `F > 1` becomes more negative, so a goal behind its share has its poor placements pushed
  further down instead of up. No recorded placement has a negative score with `F != 1`. Looks accidental.
- **LD32** (`engine.js:1274-1275`; LDW-3) Equal selection keys go to the smaller task id, which is a `cyrb53` hash of the template, week and
  index (LDH-2, LDC-9): which of two equally good sessions is placed first, and so where both go, is decided by a hash
  (`rules/place/twin-goals`, `twin-custom`; mutation `equal keys to the larger id` changes many cases). Deterministic and so portable;
  noted because renaming a template or a goal can move sessions that did not change.
- **LD33** (`engine.js:935`, `1083`; LDK-2, LDR-3) A day's "goal minutes" count every session on it, quest sessions and kept ones too, so
  the daily cap (`maxDailyGoalMinutes`, "Max daily effort" in Settings) limits quests as well as goals (`rules/place/daily-cap-30`). The
  setting's name says effort, so this is probably intended; noted because the variable says goals.
- **LD34** (`engine.js:1171-1174`; LDR-3, **Read**) With the clock before the plan's first day, a planned session wholly before the plan
  that has not yet ended is neither history nor kept nor replannable: it disappears from the plan's output. The page always plans from
  today, so the case does not arise.

Part 5 (repair, output and validation, section 13):

- **LD35** (`engine.js:1552`; LDV-1) `validateSchedule` reads the other session's title as `planned[occ[i]].title`; when the slot it
  overlaps belongs to a pinned session (marked -2) that is `planned[-2]`, `undefined`, and validation throws a `TypeError` instead of
  reporting `I1_OVERLAP` (`validate/validate/overlap-locked`). A plan the engine makes never overlaps a pinned session, so only a caller's
  own session list can hit it. Port as is.
- **LD36** (`engine.js:1176-1183`, `1595-1603`; LDR-3, LDV-5; `fuzz/generate/seed85`, `replan/generate/seed20`, `seed46`, `seed103`) A
  pinned session still `PLANNED` that ended before the clock but lies inside the plan is kept when the clock is in a waking day, but goes
  to history when it is not (`bwcIndexAt(max(start, now))` is the clock's slot: asleep, or in free time no waking day owns). As history it
  is not on the plan, so validation reports `I7 A pinned activity was moved`, the plan's status is `INVARIANT_VIOLATION`, and the page
  refuses the plan (`logic.js:121` keeps the old one). Five recorded plans (a re-plan made while the player sleeps, after a pinned session
  passed without being marked) are refused this way. Looks accidental and has an effect on the screen (a re-plan that silently does not
  happen); OQ-17.
- **LD37** (`engine.js:1529-1532`; LDO-9) Plan D12 says the array replacer drops nested keys, so the hash does not cover most of the input:
  true of the first half only; the second half, `JSON.stringify(pick)`, is the whole input, so the hash covers everything. It does depend
  on the order of every object's members (`hash/hash/key-order`): two equal inputs built in a different order hash differently. The page
  stores the hash and never compares it. Native reproduces it byte for byte (the owner's default for D12); when native builds engine
  inputs itself it must build them in the page's member order, or D12 must drop the hash from the contract (OQ-5).
- **LD38** (`engine.js:1453`; LDO-2; `rules/generate/explain-dollar-title`) The goal title goes into the explanation through
  `String.prototype.replace` with a text pattern, so `$&`, `$$`, `` $` `` and `$'` in a goal title are interpreted (`$&` becomes `the goal`,
  `$$` one dollar). A title with a dollar sign reads wrong in the session sheet. Looks accidental; harmless otherwise.
- **LD39** (`engine.js:1444`; LDO-2, **Read**) `explainInstance` prepares a task again with no goal information when its urgency base is not
  a number, which resets its lag to 0.5 for that and every later score of the task. The base is NaN only for a non-numeric deadline, which
  quest normalisation never leaves (no recorded case reaches it, `rules/generate/explain-deadline-text` included). Ported as written.
- **LD40** (`engine.js:1255-1288`, `1292`, `1404-1419`; LDW-2, LDX-3, LDX-6, LDJ-1; `rules/generate/chain-lower-tier-*`, `tight/generate/chain*`)
  A quest whose prerequisite quest has a lower tier is never placed, though it fits: the construction handles its tier while its
  prerequisite is not yet placed (its window is blocked, LDK-1), the repair cannot help when the prerequisite's session is the only
  movable one (removing it blocks the window again: one attempt, one rollback), and the fill pass, which would place it, runs only after a
  repair that changed something. Its rejection then names an unrelated reason: the day attribution of LDJ-1 gives each day the most
  tallied refusal even when another start that day fits, or `RECOVERY_PROTECTED` when nothing was refused (`... the only free time left is
  protected recovery`). An `OPTIONAL` errand that unlocks a `CORE` quest is the ordinary way to meet it. Looks accidental; OQ-18.
- **LD41** (`engine.js:1324-1338`; LDX-4, **Read**) During move A the shrunk sessions are committed again, and a goal session's commit can
  cover the target itself when it is a daily quest task (LDW-6); the move then still commits the target's own session, so the target is
  placed twice over (covered and with a session). No recorded case shows it.
- **LD42** (`engine.js:1377-1392`; LDX-6) The fill pass has no share cap (LDW-4) and no cover pass (LDW-2): plain and coverable tasks of a
  tier compete together, and one goal can take more than its share. Informational.
- **LD43** (`engine.js:1318`; LDX-3; `rules/generate/repair-four-weeks`, `SchedulePerfTest`) The repair's budget of 400 counts every waking
  day move A looks at, before its window test, so every target costs at least one move per day of the plan: on a four-week plan the budget
  is gone after about fourteen targets, and later targets (lower tier, larger id) never get a turn (the four-week case: 311 attempts, 4
  successes, 151 tasks left out). The page's comment says the bound is there to keep results device-independent, which it does; how far
  the repair reaches depends on the plan's length. Looks accidental; OQ-19.
- **LD44** (`engine.js:1483-1495`, `1563`; LDO-4, LDV-1; `validate/validate/cross-day-*`) Availability can leave free slots that no waking
  day owns (`bwcOf` -1; a short seeded plan has them after its first waking day). Nothing is ever placed there (no day), yet capacity counts
  them as free and usable minutes, and `I1_SLEEP_CROSS` can only be reached through them. Informational.
- **LD45** (`engine.js:1128`, `1498`; LDX-2, LDO-7; `rules/generate/kept-no-quest-links`) A stored session without
  `linkedQuestRequirementIds` makes every repair snapshot throw a `TypeError`, and so does quest coverage once an active quest exists. As
  LD27 (goal links): the page's own sessions always carry the list. Port as is.

Part 6 (rescheduling, section 14):

- **LD46** (`engine.js:1698`, `1721`, `1733`, `1835`; LDB-1, LDB-4; e.g. `scenario/reschedule/office@UTC`) The hold marks each kept session
  with `_hold: true` and removes the mark only from the planned sessions (`engine.js:1763`). A session the hold lets go, the recovery
  protection takes, or the cascade removes goes to `historyInstances` as the held copy, so the stored history carries `_hold: true` (the
  cascade's entries also `kept` and `bwcId`); 1,610 recorded outputs do. Nothing reads the member today. Looks accidental; port as is
  (native writes it too), and a fix is a page change first.
- **LD47** (`engine.js:1782-1783`; LDZ-5; every `time` case) The trigger-to-reason table has no `TIME_CHANGED`, so its change record has no
  `triggerReason` (the member is undefined and absent from JSON; 302 recorded steps). The page's `planTimeChange` reads only the plan
  today, so nothing shows it; a change-log entry built from the record would read `undefined`. Looks accidental; OQ-21.
- **LD48** (`engine.js:1798-1842`, `964-968`, `1510`, `1610`; LDZ-2; every rescheduled output with a goal) The held state never gets the
  goals' fairness shares (`generateSchedule` sets them at `engine.js:1225-1227`, the hold does not). So every rescheduled plan reports
  `targetShare: 0` for every goal (3,162 recorded outputs), its validation's fairness metric is 1, and every goal session this reschedule
  places is scored with a fairness multiplier `clamp(1 + 1.5 * (0 - its goal's current share), 0.75, 1.25)`: the more time a goal already
  has, the more its replacements are pulled down. Looks accidental; OQ-21.
- **LD49** (`engine.js:1744`; LDY-1; `sequence/reschedule/seed57` step 8; steps count from 0) A replacement's id is `stableId('t', replaced id + ':r:' + its
  index + ':' + clock)`. Two reschedules at the same clock that replace the same session the same way (a residual, then the session
  missed; the page's clock is in seconds, so it takes two changes within a second, or a demo) give the second replacement the id of the
  first: two sessions with one id on the plan (the index by id keeps the later, the earlier one is explained as the later's task, LDZ-3).
  Rare in use; completions and misses are keyed by instance id (WP-LD6), so it would matter. OQ-22.
- **LD50** (`engine.js:1893-1897`; LDY-4; 177 recorded steps, e.g. `scenario/reschedule/abundant@UTC` step 3) A session evicted at
  radius 4 that finds no new place is shed: it leaves the plan with only a `SHED` action, and goes neither to the history nor to the
  rejections (compare part 5's eviction, which rejects it as `DISPLACED_BY_HIGHER_PRIORITY`). The page's ledger never sees it (no miss,
  no excuse). Looks accidental; OQ-22.
- **LD51** (`engine.js:1836`, `1721-1722`; LDB-3; e.g. `miss/reschedule/seed176`, `residual/reschedule/seed7`) The recovery protection
  records `reason: 'RECOVERY_PROTECTION'` for the sessions it takes, but the excuse uses the trigger's type (`excuseReason: 'MISSED'`,
  `'QUEST_CHANGED'` ...), so the history and the change record say a missed session displaced a session on another day. Informational.
- **LD52** (`engine.js:1506`, `1596-1601`; LDZ-4; `move/reschedule/again-as-the-page-does`, `then-miss-as-the-page-does`,
  `then-commitment-as-the-page-does`; `logic.js:110-121`) The page builds every reschedule's input with the current plan as its
  `existingSchedule`, so a session the player moved (pinned) is a pinned stored session in the next one. `assembleOutput` validates without
  the sessions the hold let go (`displacedIds`), and a retired session is in no rejection, so when the next reschedule moves that session
  again, misses or skips it, or puts a commitment, a block or sickness over it, the new plan is `I7` (`A pinned activity was moved`),
  `INVARIANT_VIOLATION`, and the page refuses to publish it (the banner 'Life Director refused to publish a plan that broke a safety rule'
  and the old plan kept). 222 of the 3,602 recorded rescheduled plans fail validation, nearly all with `I7`. On the screen: a session can
  be moved once; moving it again, marking it missed or adding an appointment over it is refused. Accidental, and it shows; OQ-20.
- **LD53** (`engine.js:1805`, `1812`, `1702`; LDB-1, LDM-3; `refuse/reschedule/move-onto-pinned-later`, `-earlier`) A move onto a session
  pinned earlier: the hold places pinned sessions by start, so when the pinned one starts first the move is refused (`BLOCKED_TIME`), and
  when the moved one starts first the earlier pin is let go (excused, and placed again unpinned). Whether the player's earlier pin or the
  new move wins depends on which starts first. Looks accidental; OQ-23.
- **LD54** (`engine.js:1749`, `1437`; LDZ-3) A replacement keeps its source's `tierReason` (only a source without one gets
  `REPLACEMENT`), so its explanation says why the original was planned, and the `REPLACEMENT` wording ('it replaces a session that was
  missed or displaced') is never reached from the engine's own sessions. Informational (wording).
- **LD55** (`engine.js:1807-1813`; LDM-3, LDB-1; 35 recorded steps, e.g. `scenario/reschedule/night@Pacific/Kiritimati` step 1) The hold
  checks a held session's slots only from the plannable slot on, and only when the session is not over, so a move to a time that has
  already passed (inside the plan, in a waking day) is accepted and pinned there, painted over whatever was there. Looks accidental; OQ-23.
- **LD56** (`engine.js:1728-1737`; LDB-4, LDZ-4; `move/reschedule/seed10`, `seed142`, `scenario/reschedule/deadlines@Asia/Kolkata` step 1)
  The cascade follows only tasks that left the plan; a prerequisite moved (pinned) after its dependent, or a dependent moved before its
  prerequisite, stays, and the plan fails `I5`; 26 recorded plans do (and the page refuses them, as LD52). Looks accidental; OQ-23.

---

## 8. Golden cases: the index

### 8.1 The kernel (part 1)

File `lifemax_modular_v73/tests/fixtures/ld-kernel-golden.json` (15,231 cases, about 2.9 MB). Recorder
`lifemax_modular_v73/tools/gen_ld_kernel_golden.js` (Chromium, the real built page; node as a second runner), definitions
`tools/ld_kernel_cases.js` (no `Intl`: every instant comes from `Date.UTC` and a table of the 28 clock changes of 2026-2027 in the
zones that have them, which the web test checks against the recorded offsets), loader `tools/ld_engine_vm.js`. Web test
`tests/ld-kernel-golden.test.js`; native tests `KernelGoldenTest` (exact replay of every family but `tzfragile`, family counts), `KernelTzFragileTest` (report and
rules, 6.2(8)) and `KernelMutationTest` under
`android-wrapper/native/src/test/java/com/lifemax/rpg/core/lifedirector`.

Zones: the matrix `UTC`, `America/New_York`, `America/Phoenix`, `America/Los_Angeles`, `America/Santiago`, `Europe/London`,
`Asia/Kolkata`, `Australia/Sydney`, `Pacific/Apia`, `Pacific/Kiritimati`, `Etc/GMT+12`, and the plan's `Australia/Lord_Howe`,
`Asia/Kathmandu`, `Pacific/Auckland`, `America/Sao_Paulo`; the fuzz adds 50 more (half-hour and 45-minute zones, Troll's two-hour
change, Casablanca, Chatham, Eucla, Nuuk, Asuncion, links and legacy names).

| Family | Cases | Function | What it covers | Rules |
|---|---|---|---|---|
| `hash` | 200 | `cyrb53` | 52 texts (empty, ASCII, BMP, emoji and ZWJ sequences, flags, U+10FFFF, lone and reversed surrogates, U+FFFF, NUL, 1 KB, 4 KB, 1000 and 600-unit repeats, the engine's key shapes) at seeds 0, 1, 7; 32-bit seed edges | LDH-1, LDH-2, LDN-5, LDN-6 |
| `stable` | 96 | `stableId` | the texts with `t`; 11 prefixes (empty, emoji) | LDH-3 |
| `event` | 74 | `completionEventId` | players, instances, revisions 0/1/2/10/none, ad-hoc ids, refusals | LDH-5 |
| `rng` | 22 | `mulberry32` | 20 seeds (negative, fractional, 2^32 edges) x 8 outputs; 2 x 200 | LDH-7 |
| `num` | 41 | `ceilTo15` | boundaries, fractions, negatives, NaN, infinities, -0 | LDT-20 |
| `zone` | 682 | `isValidTimeZone` | every JVM and ICU zone id and link, legacy names, case variants, offsets, junk | LDT-2, LDT-3 |
| `alias` | 1364 | `tzOffsetSec` | every name of `zone` in January and July 2026 | LDT-3, LDT-5 |
| `parts` | 2248 | `localParts` | every 2026 change: 30-minute steps over a day, each minute for 10 minutes, the second either side; 2027 hourly; midnights of month, year and leap-day boundaries in 15 zones; 1970, 2038, 2100, fractions | LDT-4, LDT-21 |
| `offset` | 2023 | `tzOffsetSec` | the same instants | LDT-5 |
| `wall` | 4900 | `zonedToEpoch` | the dates around every change: each 15 minutes, each minute of every gap (488) and fold (422), overnight minutes to 2880; boundaries; roll-over, negative, fractional, two-digit years | LDT-6 to LDT-9, LDN-3 |
| `cal` | 334 | `ymdAdd`, `ymdKey`, `parseYmd`, `daysBetween` | leap years, month and year ends, 1969/1970, out-of-range fields, Invalid Dates, odd texts | LDT-11 to LDT-14, LDN-4 |
| `horizon` | 119 | `buildHorizon` | 7 starts in 15 zones (06:00, midnight, a second before, 1 day, 28 days, year end, 0 days); each 2026 change | LDT-15 |
| `grid` | 22 | `createGrid` | each 2026 change; plain days | LDT-16 |
| `slot` | 102 | `slotOf`, `slotCeil`, `epochOf`, constants | before, on and after slot edges, fractions | LDT-17, LDT-1 |
| `week` | 1071 | `dayEndAfter`, `weekKey`, `weekBounds` | Sundays, Mondays, midnights -1 s, year end, each 2026 change -1 s, at, +3 days | LDT-18, LDT-19 |
| `fuzz` | 1854 | the time functions; `cyrb53`, `stableId` | 1600 seeded instants, wall times and zones (40% within two hours of a change), 300 seeded texts of every UTF-16 shape | all |
| `tzfragile` | 79 | as the family in its id (`tzfragile/<family>/...`) | every case above whose zone is tz-fragile (6.2(8)) | as the original family |

Mutation checks (`KernelMutationTest`, committed; and source mutations of the native kernel, run by hand for WP-LD1): each of
these makes the replay fail: `java.time`'s fold default (227 cases), the gap resolved with the later offset (494), a hash constant
off by one (664) or a shift changed (663), 10-digit id halves (217), durations rounded down (17), the weekday off by one (533), no
1900 rule (25), 600-second slots (414), `daysBetween` truncated (3), the offset's fraction dropped (22), every grid day treated as
steady (13), case-sensitive names (30), offsets capped at 18 hours (6).

Not recorded in this part: `localParts` of an Invalid Date (throws), `tz === undefined` (device zone), years before 1900 (local mean
time offsets with seconds), zones with two changes within hours.

### 8.2 Availability (part 2)

File `lifemax_modular_v73/tests/fixtures/ld-availability-golden.json` (3,797 cases, about 6.3 MB). Recorder
`lifemax_modular_v73/tools/gen_ld_availability_golden.js` (the real built page in Chromium, node as a second runner; format in its
header), definitions `tools/ld_availability_cases.js` (no `Intl`; instants from `Date.UTC` and part 1's clock-change table), loader
`tools/ld_engine_vm.js` (helper list `AVAILABILITY`). Web test `tests/ld-availability-golden.test.js` (re-records every case in
Chromium and node, byte for byte; every cited rule defined here; no tz-fragile zone; the scenarios' equivalence; the D3 set). Native
tests `AvailabilityGoldenTest` (exact replay of every case, family counts, the D3 set) and `AvailabilityMutationTest`, adapters in
`AvailabilityReplay`, under `android-wrapper/native/src/test/java/com/lifemax/rpg/core/lifedirector`.

Zones: only the 11 strict zones of the matrix (section 8.1), plus the invalid `Mars/Phobos` and `utc` (LDT-2's case folding).

| Family | Cases | Function | What it covers | Rules |
|---|---|---|---|---|
| `helper` | 66 | `clamp`, `cmpStr` | every JSON type, NaN, infinities, -0, text in and out of range; UTF-16 order | LDG-1, LDG-2 |
| `rule` | 557 | `normalizeRule` | each field set to 30 odd JSON values and left out; 10 kinds x 9 legacy types; legacy field names; 22 `days` lists; 25 rotations; whole values that are not objects | LDA-1 to LDA-4 |
| `applies` | 390 | `ruleAppliesOn` | all 128 weekday sets over 14 days; 18 cadences x 10 anchors over 42 days (anchors before and after, 1970, 2099, leap days); 26 odd rotations; 20 validity windows, with and without a rotation, an unpadded and a month bound; legacy `dayOfWeek`; year, leap and century boundaries | LDA-5 to LDA-7 |
| `ruleon` | 390 | the page's `ruleOn` | the same inputs as `applies` | LDA-9 |
| `materialize` | 588 | `materializeRule` | 9 rule shapes x 11 zones x (a week, the year end); every 2026-2027 clock change with rules starting, ending and 15 minutes long at each edge of the gap or fold, overnight and whole-day rules | LDA-8, LDT-8, LDT-9 |
| `prefs` | 110 | `defaultPreferences`, `normalizePreferences` | every checked member at its bounds, text, NaN, fractions; the unchecked members as stored; whole values that are not objects; the goal ceiling | LDP-1 to LDP-3 |
| `sleep` | 510 | `deriveSleep` | per zone: default nights, early and late bedtimes, one and two conflicts, a conflict below an hour, explicit sleep, merges at 30 and 31 minutes, a nap and a short night, text preferences; 400 seeded inputs (30% around a clock change) | LDS-1 to LDS-7 |
| `paint` | 161 | `paint` | 160 seeded sequences of 1 to 7 paints of every code, unaligned and negative spans, null meta, loads 0 to 4; spans outside the grid and empty | LDG-3, LDG-4 |
| `avail` | 169 | `deriveAvailability` | per zone: office, default sleep, empty, heavy, light, given decompression, preferred and unavailable, commitments, blackouts, no meals, five meals, a meal in a shift, a meal that slides; the inputs' other names; odd zones, horizons, commitments and preferences; every `TypeError`, `RangeError` and `Error` of LDG-5 | LDA-10 to LDA-17, LDS-2, LDS-7, LDS-8, LDG-5 |
| `scenario` | 96 | `deriveAvailability` | the ten built-in scenarios' availability, a week from Monday 06:00 (section 10.8) | LDA-10, LDA-13 to LDA-15 |
| `dst` | 160 | `deriveAvailability` | every 2026-2027 clock change of the matrix x 8 rule sets (section 10.8) | LDA-8, LDA-10, LDA-13, LDS-1, LDT-8, LDT-9 |
| `fuzz` | 600 | `deriveAvailability` | seeded inputs: 0 to 5 rules of every kind (days, rotations, commutes, decompression, loads, validity windows, numeric text), 0 to 3 commitments, blackouts, preferences; 30% on a clock change's day, 10% unaligned starts | LDA-10, LDS-1 and the rest |

Mutation checks: `AvailabilityMutationTest` (committed) puts three porting mistakes in place of an adapter: the page's copy answering
the engine's question (D3), the decompression read as a number, `java.time`'s fold choice for rule intervals. And 21 source mutations
of the native code, run by hand for WP-LD2, each make the replay fail: `Math.round` without -0 (2 cases), days sorted as numbers (1),
day 7 kept (4), the cycle not rounded (7), `validTo` exclusive (54), no interval for the day before the plan (1348), sleep merged within
31 minutes (11), the routine lead added instead of joined (1), the commute left out of the wake-up (115), equal codes overwritten (190),
NaN paints dropped (2), load `NONE` kept (23), light shifts decompressed (20), meals searched 7 slots instead of 8 (20), meals searched
earlier first (11), a waking day needing 3 slots (11), a main sleep from 4 hours (3), the HARDCORE floor at 200 (5), the horizon not
rounded (3), the page copy's text join dropped (2), commitments sorted by id only (44).

Not recorded in part 2: inputs whose epochs are not numbers (a commitment start stored as text would join at LDA-12's `end + cm`),
sleep rules or a sleep need that give NaN sleep windows (their sort then compares NaN, where V8's and Kotlin's sorts may order
differently), the prototype names of LD13.

### 8.3 Demand (part 3)

File `lifemax_modular_v73/tests/fixtures/ld-demand-golden.json.gz` (10,556 cases; about 19.5 MB of text, 1.1 MB gzipped; the test
loaders unzip it). Recorder `lifemax_modular_v73/tools/gen_ld_demand_golden.js` (the real built page in Chromium, node as a second runner;
format in its header; it also writes the demand tables to `android-wrapper/native/src/main/resources/lifedirector/demand-tables.json`),
definitions `tools/ld_demand_cases.js` (no `Intl`), loader `tools/ld_engine_vm.js` (helper list `DEMAND`). Web test
`tests/ld-demand-golden.test.js` (re-records every case in Chromium, byte for byte, and the tables; node agrees except in the last bit of
exp and pow values, LD20; every cited rule defined here; strict zones only). Native tests `DemandGoldenTest` (exact replay of every case,
family counts, the exp and pow record) and `DemandMutationTest`, adapters in `DemandReplay` and `DemandJson`, under
`android-wrapper/native/src/test/java/com/lifemax/rpg/core/lifedirector`.

Zones: the 11 strict zones of the matrix (section 8.1).

| Family | Cases | Function | What it covers | Rules |
|---|---|---|---|---|
| `tag` | 3,008 | `tagMatches`, `tagDepth`, `tagProfile` | every pair of 52 tags (wildcards at the end, in the middle and alone, empty parts, case, ligatures, accents, CJK, emoji), odd requirement and template values; the depth of 51 tags against four templates; the profile of each tag and of odd values | LDD-1 to LDD-3 |
| `goal` | 739 | `normalizeGoal`, `suggestTemplates` | 21 members x 30 odd values and left out; 22 legacy shapes (old member names, subjects in other scripts and cases, priorities at and past the ends); whole values that are not objects; 60 titles | LDD-4, LDD-5 |
| `template` | 310 | `goalTemplates` | each archetype plain, with its first template disabled, with a subject; 11 subjects; 6 disabled lists; 6 odd archetype ids; custom templates with each of 13 members odd; frequencies and minutes at every rounding edge; 11 lists | LDD-6, LDD-7 |
| `expand` | 288 | `expandGoalActivities` | every archetype x level x intensity; 162 pacing cases (18 targets, two of them with weekly minutes just past a rounding step, x 3 volume archetypes x 3 intensities); 8 odd targets; 10 goal sets (clarifications, statuses, order, missing ids, custom templates) under two intensities; odd lists | LDD-8 to LDD-10 |
| `goalinfo` | 23 | `goalInfoMap` | elapsed against achieved at 11 points; 9 odd goals; duplicates; none | LDE-1 |
| `quest` | 1,246 | `normalizeQuest` | 30 members x 30 odd values and left out; 10 requirement members x 30; 11 odd shapes; 5 whole values | LDQ-1, LDQ-2 |
| `cycle` | 236 | `findQuestCycles` | 16 hand graphs (self loops, two and three cycles, diamonds, nested, unknown and repeated ids, UTF-16 order, a 60-quest ring, legacy ids, numbers); 220 seeded graphs | LDQ-3 |
| `ids` | 173 | `freshIds`, `weeksOf` | 6 bases x 10 counts (fractions, negative, NaN); used ids around the 200-skip limit; the plan's weeks for 9 lengths in 11 zones and across a clock change | LDC-9, LDC-1 |
| `candidates` | 121 | `compileCandidates` | one behaviour per case: horizons, levels x intensities, history and kept counts, used ids, quest tiers, deadlines either side of the critical boundary, windows, statuses, passive quests, minutes, fractional counts, session lengths, minimum sessions and intensity, exclusivity, check-in tags, depth and tie ranking, daily quests (tags, minutes, progress, `onlyDays`, midday, optional, deadline, kept), two requirements, prerequisites (chains, same tag, done, paused, without tasks, unknown, cycles, two), legacy quests, other zones | LDC-2 to LDC-10 |
| `coverage` | 24 | `questCoverage` | 11 quests x planned sessions, with and without a status | LDQ-4 |
| `prep` | 366 | `prepTask` | lengths, priorities x weights, intensities, goal lag, 61 urgency steps from before now to past the horizon, 25 horizon lengths, 220 seeded tasks | LDE-2 |
| `drain` | 271 | `recomputeBwc` | 260 seeded one- and two-day grids (every slot code, loads, intensities, restorative and plain blocks, cycles and now anywhere); every load class; tasks then recovery; restorative maintenance; the two `TypeError`s; an empty cycle | LDE-3 |
| `float` | 3,241 | `exp`, `pow`, `square` | the arguments of the urgency, check-in, spacing and movement formulas (stepped and seeded), 400 random arguments, and the edges (overflow, underflow, subnormal, signed zero, NaN, infinities) | LDF-1, LDF-2 |
| `scenario` | 110 | demand (LDE-4) | the ten scenarios in the 11 strict zones | LDC-2 to LDC-10, LDE-1, LDE-2, LDE-4 |
| `fuzz` | 400 | demand (LDE-4) | seeded goals, quests, history, kept sessions and used ids (section 11.9) | LDC-2 to LDC-10, LDE-2, LDE-4 |

Mutation checks: `DemandMutationTest` (committed) puts porting mistakes in place of an adapter: fdlibm's `exp` and `pow` for the page's,
tags compared without case, fresh ids that ignore the used ones, cycle members sorted. And 42 source mutations of the native code, run by
hand for WP-LD3, each make the replay fail: a wildcard prefix without its dot (64 cases), the depth keeping the wildcard (33), a default
goal priority of 2 (301), the subject not lower-cased (15), four suggestions (2), custom daily spacing from 6 a week (1), the advanced
base `fTarget + 2` (140), `BALANCED` keeping the stretch (290), pacing minutes rounded down (12), a lag gain of 1.5 (182), SIDE quests as
CORE (115), negative counts kept (10), the legacy exclusive flag ignored (26), a self-prerequisite not a cycle (112), daily coverage needing
every session (2), a skip limit of 100 (3), weeks of six days (471), the weekly minimum one higher (365), goal windows from the week's start
(455), a prerequisite lag of 10 minutes (84), blocking without the "still needs" test (13), the critical boundary strict (1), later daily
days using the minutes (39), kept sessions not counted (45), no session length check (1), check-in tags claimable (2), the fewest in-window
sessions ranked first (18), the daily cover spacing at 23 hours (2), an equal tier relabelled (114), merged indexes from 99 (69), own
sessions 30 minutes longer (343), own spacing of 4 hours (305), an urgency rate of 2.9 (620), priority over 5 steps (787), fdlibm's
exponential in `prepTask` (176), a relief of 0.05 (171), restorative commutes ignored (71), free runs counted before now (183), a drain
not rounded to single precision (22), shares over the goal count (376), the reduction without the low part of ln 2 (1,974), underflow from
-740 (1). Cases were added for the first run's three survivors (four suggestions, pacing rounds down, the critical boundary). Three
mutations proved equivalent and were replaced: the history filter's 7 days made 6 (LDE-4's filter cannot change the candidates, since
history before the plan's first day never counts, LDC-2), and in `PageMath` dropping the rounding margin or using 6 Taylor terms instead of
12 (the double-double result stays correctly rounded on every recorded argument, and an undecided rounding falls back to 60 digits).

Not recorded in part 3: a template `d` with fewer than three items in a plan (LDD-9), the prototype names of LD23, a quest list or goal
list that is a text (iterated by character), goals or quests whose ids hash to the same `cyrb53` value.

### 8.4 Placement (part 4)

File `lifemax_modular_v73/tests/fixtures/ld-placement-golden.json.gz` (2,998 cases; about 47.7 MB of text, 5.2 MB gzipped; the native
loader reads it line by line and keeps no case). Recorder `lifemax_modular_v73/tools/gen_ld_placement_golden.js` (the real built page in
Chromium, node as a second runner; format in its header; every prepare and place case run twice and required equal), definitions
`tools/ld_placement_cases.js` (no `Intl`; the re-plan inputs take an earlier plan from the engine in node), loader `tools/ld_engine_vm.js`
(helper list `PLACEMENT`, `placementSource` with the two stage calls, section 12). Web test `tests/ld-placement-golden.test.js` (re-records
every case in Chromium, byte for byte; the stage calls change no plan; node agrees on every anchors case and on most others, LD20; every
cited rule defined here; strict zones only; only the three cases built to throw do). Native tests `PlacementGoldenTest` (exact replay of
every case and the family counts) and `PlacementPerfTest`, adapters in `PlacementReplay` and `PlacementJson`, under
`android-wrapper/native/src/test/java/com/lifemax/rpg/core/lifedirector`.

Zones: the 11 strict zones of the matrix (section 8.1).

| Family | Cases | Function | What it covers | Rules |
|---|---|---|---|---|
| `prepare` | 178 | `prepareState` | the ten scenarios in five zones; 80 seeded inputs, half re-plans of the engine's own plan (sessions planned, pinned, started, done, skipped, missed, moved); 48 hand cases: existing sessions in every state and place (before and after the plan, on work, on sleep, overlapping, same start, off the slot grid, without goal links or intensities, intensities as text and past a byte, outside every waking day), check-ins (each level, late, odd level, zero and text epochs, off the grid), sleep needs (0, text, huge), recovery minutes (two of them not a multiple of 15), lead minutes (0 to 600 and negative), the clock in and past the plan, history spacing | LDR-1 to LDR-6 |
| `anchors` | 15 | `anchorsDependents` | placed, unplaced and covered dependents, other prerequisites, missing, null, empty and text lists, self, prefixes, an undefined task id | LDW-5 |
| `scenario` | 110 | the construction | the ten scenarios in the 11 strict zones, every active task probed | LDR, LDK, LDU, LDW, LDJ |
| `seeds` | 220 | the construction | the ten scenarios at seeds 2 to 12 (their work hours move) in UTC and Kolkata | as `scenario` |
| `dst` | 120 | the construction | three scenarios starting the morning before, and the morning of, each 2026-2027 clock change of the strict zones | as `scenario`, LDR-2 |
| `rules` | 55 | the construction | one behaviour each on a two-day plan: every archetype; avoid windows (numbers and text); rest days (high work, text, meal prep); check-ins; the high-intensity, consecutive and daily caps; a heavy shift; recovery 360; lead 60; short sleep; twin goals and twin custom goals (equal keys); the share cap (and only capped candidates); quest chains (placed and blocked), daily covers (plain, exclusive, wildcard), a critical deadline, merged sessions, minutes, a long session, a deadline too close; prior volume; re-plans at four clocks and with a pin; history spacing near and far; no free time; custom templates with text numbers, an object for phases (throws), no spacing, a two-hour ideal spacing; recovery 100; a preferred window; ten advanced goals in one day; a stored session without goal links (throws) | as named in each case |
| `fuzz` | 700 | the construction | seeded inputs (section 12.7), a third of them re-plans | LDR, LDK, LDU, LDW, LDJ |
| `small` | 1,600 | the construction | seeded short inputs (one to three days, up to three goals and two quests), one task probed | as `fuzz` |

A place case records: the first raw score table, every placement in order with its selection, the sessions, each task's state, each day's
counts, the ledgers, the slot codes, `anchorsDependents` of every session, `buildRejection` of every unplaced task, and the probes
(`taskEarliestLatest`, `evalAcross`, `evalInBwc` with its reason tally in every day, and `scorePlacement`'s detail at the best slot). In all:
23,213 placements, 8,195 rejections, 899 covered tasks, 1,112 compressed sessions, 24 cap-relaxed placements, 210 sessions that anchor a
dependent, 89 probed placements with a movement penalty, 100 kept and 7 displaced sessions in `prepare`.

Mutation checks: 52 source mutations of the native placement code, run by hand for WP-LD4 (a script copies `core/lifedirector`, changes one
place, compiles and replays the file), each make the replay fail: the sleep factor's ceiling 1.0 (632 cases), the base multiplied in single
precision (729), a check-in half-life of 5 hours (366), fdlibm's `pow` for the check-in (26), energy not cut to 0..1 (131), an energy-fit
shortfall of 2.5 (853), the recovery need rounded down (3), no lead before the first slot (2,834), the clash checked from the session's
start (3), a session starting now replanned (14), the shortfall ignoring a truncated start (88), prior history from 6 days (15), the
dependency lag ignored (143), the daily cap inclusive (938), the per-day limit one lower (2,263), spacing inclusive (692), a fatigue floor of
0.40 (420), downstream from the same start (4), kept sessions not demanding (7), post-shift protection for any high task (109), an urgency
floor of 0.80 (1,612), an avoided preference of 0.20 (138), fdlibm's `exp` in the spacing (1) and the movement (2), a spacing sigma floor
of 3 (1), variety counting kept neighbours (78), fragmentation for gaps of 3 (1,292), movement "soon" under 12 hours (235), compression 6
a slot (900), a fairness gain of 1.0 (1,106), scores to 1e-5 (2,239), rest days not checked for high tasks (83), the preferred window
ignored (288), equal keys to the larger id (1,555), the later day winning a tie (44), the later slot winning a tie (1,288), a regret
weight of 0.30 (2,271), scarcity 10 (2,299), the cover pass first (511), a cap share of 0.65 (5), the cap never relaxed (6), the second-best
without fairness (1,077), re-scoring only the winner's day (1,276), first scores with fairness (1,274), dynamic rows not re-scored (364),
coverage ignoring exclusivity (8), coverage letting check-ins cover (8), anchors counting covered tasks (13), the rejection's specific
reason tie to the last (3), the rejection's day ranking tie to the higher code (290), recovery fitting on free slots only (555). The first
run's two survivors (the recovery need rounded down: every stored minimum was a multiple of 15; the spacing sigma floor: no ideal spacing
below 8 hours) got cases (`prepare/prepare/recovery-37`, `-100`, `rules/place/recovery-100`, `rules/place/custom-near-spacing`), and the
cap mutation was rewritten after it failed to compile. Weakly covered, by few cases each: DOWNSTREAM (15 tallied refusals, no rejection
names it), the spacing and movement exponentials' last bit (1 and 2 cases), a cap share near 0.60 (5).

Not recorded in part 4: stored values of an odd type in the existing schedule beyond the intensities and links (text epochs, a text
`spacingKey` that matches a number), a check-in level naming an inherited member (LD29), preferences of an odd type other than avoid
windows and rest days (a text `maxHighIntensityPerDay`), a fractional prior-volume count, the clock before the plan's first day with
sessions before it (LD34), and the 2,000-round guard of the construction (no recorded tier comes near it).

### 8.5 Repair, output and validation (part 5)

File `lifemax_modular_v73/tests/fixtures/ld-schedule-golden.json.gz` (2,915 cases; about 99.7 MB of text, 9.6 MB gzipped; the native
loader reads it line by line and keeps no case, and the web test keeps each case as its text). Recorder
`lifemax_modular_v73/tools/gen_ld_schedule_golden.js` (the real built page in Chromium, node as a second runner; format in its header; every
case run twice and required equal), definitions `tools/ld_schedule_cases.js` (no `Intl`; it reuses placement's input builders, and every
plan that serves as an input comes from the engine in node), loader `tools/ld_engine_vm.js` (helper list `SCHEDULE`, `scheduleSource` with
three stage calls that only look: each repair target, after the repair, after the fill pass). Web test `tests/ld-schedule-golden.test.js`
(re-records every case in Chromium, byte for byte; the stage calls change no plan; node agrees on every validate and hash case and on
most others, LD20; every cited rule defined here; strict zones only; only the cases built to throw do; some repairs spend the whole budget;
every violation and warning code and every repair move is reached). Native tests `ScheduleGoldenTest` (exact replay of every case and the
family counts) and `SchedulePerfTest`, adapters in `ScheduleReplay`, under `android-wrapper/native/src/test/java/com/lifemax/rpg/core/lifedirector`.

Zones: the 11 strict zones of the matrix (section 8.1).

| Family | Cases | Function | What it covers | Rules |
|---|---|---|---|---|
| `scenario` | 110 | `generateSchedule` | the ten scenarios in the 11 strict zones, the whole output | LDX, LDO, LDV |
| `seeds` | 140 | `generateSchedule` | the ten scenarios at seeds 2 to 8 in UTC and Kolkata | as `scenario` |
| `dst` | 120 | `generateSchedule` | `office`, `limited` and `manygoals` from the morning before, and the morning of, each 2026-2027 clock change | as `scenario` |
| `rules` | 100 | `generateSchedule` | placement's 55 one-behaviour plans planned to the end, and part 5's: compression, swap, eviction, budgets of 4, 6 and 8 days, a chain, a pinned full day, the four-week plan of `SchedulePerfTest`, chains whose prerequisite has a lower tier (LD40), goal titles with `$` patterns, quotes and nothing, quests with goals, two quests, rest days, spacing, scarce days, a check-in, a text deadline, kept sessions with and without words, full copies, displaced pins, history, no quest links (throws), text lengths, the same start, segments without sleep, with a blackout and with commitments, half and late days, allocation (paused, behind, a second-template driver, twin templates, numeric goal ids), passive quests, warning statuses, an empty plan, a `clock` member | as named in each case |
| `fuzz` | 500 | `generateSchedule` | placement's seeded inputs (section 12.7), a third re-plans | as `scenario` |
| `small` | 900 | `generateSchedule` | placement's short seeded inputs | as `scenario` |
| `tight` | 430 | `generateSchedule` | 350 seeded plans with more urgent work than time (one to five days, long shifts, two to five goals at high intensities, three to eight quests due inside the plan, low caps; a quarter re-plans), 80 seeded quest chains with lower-tier prerequisites | as `scenario`, LDW-5 |
| `replan` | 250 | `generateSchedule` | the engine's own plan re-planned: the clock moved on (sometimes off the quarter hour), sessions done, missed, skipped, partial, started, cancelled, pinned, moved by up to a day, kept words, a new commitment over a session, work hours moved, a blackout, a check-in | as `scenario`, LDR-3 |
| `repair` | 273 | `generateSchedule`, watched | the state at every repair target, after the repair and after the fill pass (STATE: sessions, placed and covered flags, fairness, each day's counts, the spacing ledger, the slot codes, the sums of the drain, run, intensity and owner arrays; the placement records): `limited`, `office`, `abundant`, `manygoals` in every strict zone, 220 tight plans, the hand repair cases | LDX-1 to LDX-6 |
| `validate` | 55 | `validateSchedule` | a real plan as planned and broken one way at a time: zero and negative length, on sleep and work, overlapping (a pinned one throws, LD35), across days (free time no day owns), before and after the plan, started, no or empty explanation, deadline, window, prerequisites missing, late and by instance id, spacing (plain, pinned, kept), downtime and the daily cap, shortfall, sleep warnings, pins kept, moved, missing and excused (by id, by rejection, by state), rejections without words, metrics, malformed arguments (null, zero, a null item and text plans, a text source, null dependencies, off-grid and text epochs, no input, a bad zone), the `office` scenario in three zones as planned and shifted | LDV-1 to LDV-6 |
| `hash` | 37 | `inputHash` | the two halves: `clock` dropped, nested members named like top-level ones, key order, numbers, Unicode, empty, arrays, numeric keys, unsorted names, 25 seeded inputs | LDO-9 |

In all: 2,547 whole plans (1,143 `VALID_SCHEDULE`, 1,398 `CONSTRAINT_WARNING`, 6 `INVARIANT_VIOLATION`: the five I7 of LD36 and the
zero-length pin of `kept-same-start`), 26,071 planned sessions (885 kept, 3,171 compressed), 3,526 history sessions, 11,315 diagnoses,
45,154 repair attempts (857 successes: 660 compressions, 184 swaps, 13 evictions; 44,297 rollbacks), 75 fill placements, 34 repairs that
spend the whole budget, 1,549 watched repair targets, 136,716 segments, 25,191 scored and 880 locked explanations.

Mutation checks: 46 source mutations of the native part 5 code (a script copies `core/lifedirector`, changes one place, compiles and
replays the file); 44 make the replay fail. First run, of 2,826 cases: the budget checked after it grows in move A (7) and move B (24), a
budget of 399 (32), targets lowest tier first (222), targets by id descending (494), the shrunk sessions latest first (56), freed slots
having to exceed the need (546), only lower tiers movable (1,095), shrunk sessions always re-placed by score (401), eviction for tier 1
only (6), no relocation in a swap (142), incumbents latest first (140), the fill pass taking displaced tasks (1), the fill tie to the
larger id (22), restore keeping drains (523), free runs (537), intensities (375), fairness minutes (1,035) and covers (44), removal
keeping the spacing start (428), covered tasks (16) and the key count (390), explanations with three factors (2,133), scarce up to three
days (984), factors to whole points (2,133), the `$` replacement taken literally (1), a started session read as pinned (117), segments
ignoring a session's end (1,559), capacity counting kept sessions as upkeep (392), segments without the block reference (2,036),
validation checking started slots (14), downtime inclusive (784), spacing for kept sessions (1), an SQI weight of 35 (2,352), the daily
cap inclusive (715), zero length allowed (1), hash names unsorted (2,493), the hash keeping the clock (3), displaced pins listed first
(40), the displaced rejection ignored (7). Survivors of the first run got cases and were run again, of 2,915 cases: the fill pass after
every repair (10, from the new `chain-lower-tier-*` and `tight/generate/chain*` cases, which also found LD40), restore keeping the spacing
ledger (1,154; the first form of this mutation changed nothing, as `restore` clears the map first), the allocation keeping the first
plan's status (1, `alloc-driver-second`), planned sessions of the same start by id descending (3, after the mutation was made symmetric;
`kept-same-start`). Two survive, and are reported as what they are: a fill-pass regret weight of 0.30 (in 7,000 seeded plans one fill
round, `tight` seed 64, would choose differently, and the two placements commute) and validation's fairness summed over the keys as
inserted instead of `Object.keys` order (the sum is only shown rounded to thousandths, and in the SQI to tenths, so a last-bit difference
cannot show; `alloc-numeric-ids-three` holds the case where the orders differ).

Not recorded in part 5: `rescheduleActivity` (part 6, section 8.6), LD39 and LD41 (no input reaches them), the fill pass's 200-round guard,
a waking day id repeated, and sessions removed that are not in the list (LDX-1's `splice(-1)`).

### 8.6 Rescheduling and hold placement (part 6)

File `lifemax_modular_v73/tests/fixtures/ld-reschedule-golden.json.gz` (2,298 cases, 4,329 steps; about 137.7 MB of text, 12.0 MB gzipped;
the native loader reads it line by line and keeps no case, and the web test keeps each case as its text). Recorder
`lifemax_modular_v73/tools/gen_ld_reschedule_golden.js` (the real built page in Chromium, node as a second runner; format in its header;
every case run twice and required equal), definitions `tools/ld_reschedule_cases.js` (no `Intl`; it reuses placement's and part 5's input
builders; the triggers that name a session or a time are chosen by playing the steps in node), loader `tools/ld_engine_vm.js` (`SCHEDULE`,
`scheduleSource`, its stage calls off). A case is a plan (made by the runner, not recorded: part 5 covers it) and a list of steps, each a
trigger with, optionally, top-level input members replaced first (the clock moved on, quests or availability changed) and the input's
`existingSchedule` set to the current plan's sessions (as the page's `buildInput` does); every step's whole result is recorded (the plan
without `computeTimeMs`, the change record without it, the refusal, the engine input; a result member that is the very object passed in,
or equal to it as JSON, as a marker), and the next step starts from it. Web test `tests/ld-reschedule-golden.test.js` (re-records every case
in Chromium, byte for byte; node agrees on most, LD20; every cited rule defined here; strict zones only; only the steps built to throw do;
every trigger, refusal, radius, kind of action and result marker is reached). Native tests `RescheduleGoldenTest` (exact replay of every
step, member order included, and the family counts) and `ReschedulePerfTest`, runner in `RescheduleReplay`, under
`android-wrapper/native/src/test/java/com/lifemax/rpg/core/lifedirector`. Steps count from 0 where a defect names one.

Zones: the 11 strict zones of the matrix (section 8.1); the tz-fragile zones are in no family.

| Family | Cases | Steps | What it covers | Rules |
|---|---|---|---|---|
| `scenario` | 110 | 370 | the ten scenarios in the 11 strict zones: a miss, a move (into free time, onto a session, onto hard time or recovery, into the past, outside the plan, a day on or back, off the quarter hour) and a skip, every third zone a fourth trigger of any kind; the clock moved on before some | LDM, LDB, LDY, LDZ |
| `seeds` | 60 | 120 | the scenarios at seeds 2 to 4 in UTC and Kolkata: a miss or a move, then any trigger, some with the page's stored schedule | as `scenario` |
| `dst` | 120 | 240 | a three-day plan from the morning before, and the morning of, each 2026-2027 clock change of the strict zones: two moves, a block of hours and a miss; a move onto the change; a block of hours over it | LDM-3, LDM-6, LDB-1, LDZ |
| `miss` | 400 | 400 | a miss or a skip on seeded short, tight and chained plans, the clock moved on and the stored schedule in some | LDM-1, LDM-2, LDB, LDY, LDZ |
| `move` | 458 | 602 | one or two moves of every kind on seeded plans; a move onto a session, there and back, three times, off the quarter hour, then missed, and three moves of a pinned session as the page builds its input (LD52) | LDM-3, LDB, LDY, LDZ |
| `residual` | 159 | 161 | the rest of a session (tiers 1 to 4, no parent, no window, a snapshot holding `goalTitle: null`, then the session missed at the same clock and a minute later, LD49) | LDM-4, LDY, LDZ |
| `disrupt` | 406 | 406 | a new commitment, a block of hours, a blackout, sickness in turn on seeded plans; the four over the first session; a sick week; a blackout of the whole plan | LDM-5, LDM-6, LDB, LDY, LDZ |
| `quest` | 208 | 208 | quests completed, failed, archived, removed, deadlines moved earlier, to tomorrow, later, removed; a daily quest closed; nothing changed | LDM-7, LDB, LDY, LDZ |
| `time` | 153 | 153 | work moved, removed, added hours, a blackout, then `TIME_CHANGED`; nothing changed | LDM-7, LDB, LDY, LDZ |
| `sequence` | 160 | 1,600 | a seeded plan, then ten triggers of any kind, each on what the one before left: the clock and the horizon start moved on, the stored schedule, moves back | all |
| `refuse` | 16 | 19 | every refusal (onto sleep, onto work, after and before the plan, into the past, onto recovery, six moves into a short day), the throws (an unknown session for a move, a miss and a skip, an unknown trigger, no trigger), a refused move then an accepted one, a move onto a pinned session that starts earlier and later (LD53) | LDM-1, LDM-3, LDB-1, LDB-3, LDZ-5 |
| `hold` | 13 | 15 | a missed, skipped, moved and covered prerequisite and a missed dependent (the cascade), a pin then a block over it, a started session kept, an ended one carried, a shorter horizon, the stored schedule, the recovery protection three ways | LDB, LDM |
| `radius` | 35 | 35 | packed days (10 to 13 hours of work) with urgent quests: misses, skips and blocks over each urgent session, sickness and a blackout, so that radius 3 and 4 run | LDY, LDM-6 |

Reached (the web test checks each): all ten triggers, the three refusals (315 `BLOCKED_TIME`, 124 `OUTSIDE_PLAN`, 18
`RECOVERY_PROTECTED` steps), 269 throws, radius 1 to 4, every kind of action (`MISS`, `SKIP`, `MOVE`, `INSERT`, `COMPRESS`, `EXCUSE` with
the trigger's name, `PREREQUISITE_MOVED` and `QUEST_DEADLINE_CHANGED`, `DELETE`, `RELINK`, `SHED` for a disruption and an eviction), and
the three result markers. Rescheduled plans: 1,620 `VALID_SCHEDULE`, 1,760 `CONSTRAINT_WARNING`, 222 `INVARIANT_VIOLATION` (LD52, LD56).

Mutations (section 14.6). Not recorded in part 6: a stored source of another shape than the engine's (native refuses it,
`UnreadableSource`, LDB-2), a carried or held movable session without an explanation (the engine always writes one), a `QUEST_CHANGED`
session whose links are not a list, the radius-3 and radius-4 iteration bounds (600, 900; the most any recorded reschedule spends is 195), the
20-round guards of the cascade and the recovery protection, and a `newStartEpoch` that is not a number.

---

## 9. Open questions for the owner

- **OQ-1** The zone matrix: are these the zones that matter (the ones the owner lives in or travels to)? Adding one is a line in
  `tools/ld_kernel_cases.js` and a regenerated file.
- **OQ-2** Travel: is a zone change in the middle of a horizon in scope (today it forces a full re-plan at `maybeRollover`)?
- **OQ-3** Which tz data wins on the phone when the WebView's ICU and Android's tz data disagree (6.2(5), 6.2(8): today Vancouver,
  Chisinau and, with newer data, Morocco would)? Recommended (6.2(8)): native uses the phone's data, the page sends its offsets,
  and a disagreement is skipped as `tzdata` (as the economy shadow's N4) rather than reported.
- **OQ-4** LD01: keep the second occurrence in a fold east of Greenwich (port as is), or move both page and native to the first?
  A change moves sessions on the autumn change day in those zones, so it needs a new golden file.
- **OQ-5** Plan D12 (`inputHash`): drop from the contract, or keep byte-equal (LDH-6)? Part 5 keeps it byte-equal (the owner's default:
  native reproduces it, LDO-9, every recorded case), and finds D12's premise half true (LD37: the hash covers the whole input, in its
  member order). Keeping it costs native nothing while the page builds the inputs; once native builds them (cutover) it must build them
  in the page's member order, or the hash is dropped then (it is stored and never compared).
- **OQ-6** Plan D3 (LD06): one rule question for page and native? Recommended: the engine's (LDA-1, LDA-5 to LDA-7) everywhere,
  including the dial handles and Money's shifts. For every rule the page's forms write the two agree, so Money's income does not change
  today; they differ only on hand-edited or legacy stores (the LDA-9 table). Until ruled, native ports both as they are
  (`AvailabilityRule.appliesOn`, `PageRuleCheck`).
- **OQ-7** LD04, LD05: should a malformed stored rule (a non-numeric time, a non-text anchor) keep failing every plan, or be skipped
  with a warning? Port as is until ruled; a change is a page change first, with new cases.
- **OQ-8** LD14: the default 30 minutes of decompression after a long shift never applies to rules made in the app, because the form
  stores 0. Intended (the player chooses), or should the form store nothing when the player leaves it empty?
- **OQ-9** LD12: should load `NONE` mean no drain (it reads as moderate today)? It is not offered by the forms; informational unless a
  later form adds it.

- **OQ-10** LD16: a session merged into a quest and its prerequisite depends on itself and is never placed. Should a task never depend on
  itself (drop its own id, and its siblings', from `dependsOn`), or should the coalescing refuse to attach a quest whose prerequisite the
  session already carries? Either is a page change first, with new cases. Port as is until ruled.
- **OQ-11** LD17, LD25: minutes-based quests. Should a daily quest's later days ask for the same minutes as today, and should coverage
  count minutes? Port as is until ruled.
- **OQ-12** LD20: the reference for `Math.exp` and `Math.pow`. The recorder's Chromium is correctly rounded and native reproduces it
  (`PageMath`); a phone whose WebView still uses fdlibm would compute a few urgencies one bit differently from native. Recommended: make
  the correctly rounded value the contract (it is the mathematically exact one, and what current Chromium does), confirm the phone's
  WebView in the device pass, and in shadow mode report a difference that is only in the last bit of an exp or pow input as `float`
  rather than a divergence, until the page runs on a WebView of the same kind everywhere.
- **OQ-13** LD21: should a language subject keep its letters (Unicode letters and digits, lower-cased), so that `Español` and `日本語`
  make their own tags? A change renames the tags of existing goals' sessions, which matters to quests that name them.
- **OQ-14** LD26, LD31: the prior-volume history that counts a session's start as its end, and fairness multiplying negative utilities.
  Both look accidental; a fix moves sessions, so it is a page change first, with new cases. Port as is until ruled.
- **OQ-15** LD20 reaches placement: one bit in an exp or pow can change which slot wins (node 22, fdlibm, records a different answer
  for some prepare and place cases). Recommended as OQ-12, and in shadow mode a placement divergence whose input has a check-in or a
  spacing or movement exponential within an ulp is reported as `float` until the device pass confirms the WebView.
- **OQ-16** Plan WP-LD4's decision: explanation strings (`humanExplanation` here, `explainInstance` in WP-LD5) recorded as part of the
  contract, as recommended; confirm. Part 5 records `explainInstance`'s summaries as contract too (LDO-2). D12: OQ-5.
- **OQ-17** LD36: a pinned session that ended unmarked makes a re-plan made while the player sleeps fail validation (I7), and the page
  then keeps the old plan. Should a pinned session wholly before the clock go to history and be exempt from I7 (or always be kept)?
  A page change first, with new cases. Port as is until ruled.
- **OQ-18** LD40: a quest whose prerequisite quest has a lower tier is never placed though it fits, and is rejected with an unrelated
  reason. Should the fill pass always run (one line; it then places such quests, the mutation `fill after every repair` shows which
  cases move), or should a dependent's tier be raised to its prerequisite's? A page change first. Port as is until ruled.
- **OQ-19** LD43: the repair's budget is spent per day looked at, so on long plans most urgent tasks get no repair. Should it count
  attempts instead? A change moves sessions on long plans; a page change first.
- **OQ-20** LD52: once a session has been moved, the page's own reschedules of it (move again, miss, skip, a commitment over it) fail `I7`
  and are refused. Recommended: pass the hold's let-go and retired pinned ids to the validation as excused (`displacedIds`; one line in
  `assembleOutput`'s call for a reschedule), or have the validation exempt the trigger's own session. A page change first, with new cases
  (the three `*-as-the-page-does` cases show it). Port as is until ruled.
- **OQ-21** LD47, LD48: give `TIME_CHANGED` a trigger reason, and give the held state the goals' shares (as `generateSchedule` does), so
  rescheduled plans report their target shares and score replacements as a fresh plan would? Both move recorded answers (LD48 moves
  sessions), so a page change first. Port as is until ruled.
- **OQ-22** LD49, LD50: should a replacement's id include something unique to the reschedule (the change id, or the count of earlier
  replacements), and should a session evicted with no new place go to history (or the rejections) as part 5's eviction does? Page changes
  first. Port as is until ruled.
- **OQ-23** LD53, LD55, LD56: the rules of a move. Should a move onto another pinned session always be refused, a move to a time already
  past be refused (`OUTSIDE_PLAN` or a new reason), and a move that breaks a prerequisite order be refused or cascade? Page changes first.
  Port as is until ruled.
---

## 10. Availability: rules, preferences, sleep and the painted grid (part 2, WP-LD2)

This part covers `engine.js:59-61` (`clamp`, `cmpStr`), `151-198` (the grid's arrays and `paint`), `200-428` (rules, preferences,
sleep, `deriveAvailability`, maintenance, meals) and the page's own copy of the rule question, `logic.js:577-588` (`ymdDow`, `ruleOn`).
New prefixes: `LDG-n` the grid and the value rules, `LDA-n` availability rules and the painted plan, `LDP-n` preferences, `LDS-n`
sleep. **Executed** rules cite families of `lifemax_modular_v73/tests/fixtures/ld-availability-golden.json` (section 8.2); the
native code in `android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector` (`PageValues`, `AvailabilityRule`,
`PageRuleCheck`, `PlanPreferences`, `SleepWindows`, `AvailabilityGrid`, `Availability`) replays every case.

Inputs are what the page's store holds: JSON values. The engine reads them without a schema, so a member may be absent, `null`, or of
any JSON type, and several rules below say what an unusual type does. Where that looks accidental it is marked `[LDnn]` (section 7).

### 10.1 Value rules

- **LDG-6** (**Executed**: families `rule`, `prefs`, `helper`; `avail/*text*`) The JavaScript value rules the layer depends on, for a
  stored value `v`:
  - *truthy*: everything except absent, `null`, `false`, `0`, `-0`, NaN and the empty text. `a || b` is `a` when truthy, else `b`.
  - `Number(v)` (and `+v`, `v * 60`, `v / 15`, `v - x`): absent NaN, `null` and `false` 0, `true` 1, text by ECONOMY_SPEC NUM-3
    (LDN-4), an array or object through its text (`[30]` is 30, `[1, 2]` and `{}` are NaN).
  - `String(v)`: absent `undefined`, numbers as JavaScript writes them (`-0` is `0`), an array its items' texts joined by `,` (a
    `null` item empty), an object `[object Object]`.
  - `a < b`: when both are text (an array or object counts as its text) by UTF-16 units, otherwise as numbers, NaN never less.
  - `n + v` with `n` a number: when `v` is text (or an array or object) the text of `n` followed by `v`'s text (`3 + "8"` is `"38"`),
    else the sum. Read back as a number afterwards when it is multiplied (LDS-7, LDA-9).
  - `Math.round(x)`: nearest whole number, ties toward +infinity, `-0` for `-0` and for every `x` from -0.5 up to 0
    (`rule/normalizeRule/startMin=9:-0.5` gives `startMin` `-0`).
  - `list.includes(x)` and `x === y`: same type and value; `-0` equals `0`; for `includes` NaN finds NaN (LD07); `"3"` is not `3`.
  - A member of a value that is not an object is absent (text, numbers and arrays have none of the members the engine reads).
- **LDG-1** (**Executed**: `helper/clamp/*`, 50 cases) `clamp(x, lo, hi) = x < lo ? lo : x > hi ? hi : x` with LDG-6's `<`: NaN and
  any value whose number is NaN come back unchanged, and so does a value in range **as itself** (`clamp("30", 0, 120)` is the text
  `"30"`, `clamp(true, 0, 120)` is `true`), while an out-of-range value becomes the number `lo` or `hi` (`"200"` gives 120).
- **LDG-2** (**Executed**: `helper/cmpStr/*`, 16 cases) `cmpStr(a, b)` is -1, 0 or 1 by UTF-16 units (`"rule10" < "rule2"`,
  `"Z" < "a"`, `"\u{1F600}" < "￿"`): Kotlin's `String.compareTo`, never a collator.
- **LDG-5** (**Executed**: every case whose `out` is `{threw}`: 18 `TypeError`, 24 `RangeError`, 1 `Error`) Where the layer stops instead of answering, and with which
  error, as the page throws it:
  - `TypeError`: a rule or commitment stored as `null`; `rules` (or `fixedCommitments`) truthy but not a list; a rotation's
    `activeDays` truthy but not a list (LDA-4); a rotation's `anchorDate` that is not text, when the rule is asked about a date
    (LDA-6, LD05); `mealOffsetsMinutes` not a list when a meal is placed (LDA-15); no plan days (`horizonDays` of 0 or less after
    LDA-10, or `materializeRule` with no days).
  - `RangeError`: a rule whose `startMin` or `durationMin` is not a number after LDA-1 (`"abc"`, `{}`): its wall clock is no date and
    `zonedToEpoch` throws (LD04); a horizon start that is not a date.
  - `Error('Invalid IANA timezone: ' + zone)`: `deriveAvailability` given a zone LDT-2 refuses (`avail/deriveAvailability/bad-zone`).
  In each case the whole call fails: a single malformed stored rule makes every plan fail (LD04, LD05).

### 10.2 Rules

- **LDA-1** (**Executed**: family `rule`, 557 cases; `engine.js:202-220`) `normalizeRule(r, index)` reads a stored rule `r` (the
  `index`th of its list) into the engine's form. `r` `null` (or absent) throws `TypeError`; any other value that is not an object has no
  members and gives a rule of defaults. Members, in the page's order (`x != null` means "not absent and not `null`"):
  - `id`: `String(r.id || r.ruleId || 'rule' + index)` (`{id: 0, ruleId: 7}` is `"7"`, `{id: [1, 2]}` is `"1,2"`).
  - `kind`: LDA-2. `title`: `r.title || kind` (any truthy stored value, kept as stored).
  - `days`: LDA-3.
  - `startMin`: `clamp(round(Number(r.startMin != null ? r.startMin : (r.startMinuteOfDay || 0))), 0, 1439)`.
  - `durationMin`: `clamp(round(Number(r.durationMin != null ? r.durationMin : (r.durationMinutes || 60))), 15, 1440)`: a stored `0`
    is 15, but an absent or `null` `durationMin` with `durationMinutes: 0` is 60.
  - `commuteMin`: `clamp(round(Number(r.commuteMin || 0)), 0, 240)`.
  - `decompressMin`: `r.decompressMin != null ? clamp(r.decompressMin, 0, 120) : null`, **not** read as a number (LDG-1): a stored
    `"30"` stays the text `"30"`, `"abc"` stays `"abc"` (LD10).
  - `load`: `r.load || r.shiftIntensity || 'MIXED_MODERATE'`, as stored (any value).
  - `rotation`: LDA-4. `validFrom`, `validTo`: `r.validFrom || null`, `r.validTo || null`, as stored.
  
  A `startMin` or `durationMin` whose number is NaN stays NaN here; LDA-8 then throws (LD04).
- **LDA-2** (**Executed**: `rule/normalizeRule/<kind>|<ruleType>`, 90 cases) `kind` is `r.kind` when it is exactly one of `WORK`,
  `CLASS`, `APPOINTMENT`, `UNAVAILABLE`, `SLEEP`, `PREFERRED` (case matters: `work` is not); else the legacy `r.ruleType` looked up by
  its text: `HARD_COMMITMENT` WORK, `SLEEP_WINDOW` SLEEP, `UNAVAILABLE`, `PREFFERED_WINDOW` (sic) and `PREFERRED_WINDOW` PREFERRED
  (`["SLEEP_WINDOW"]` works, as its text); else `WORK`. (**Read**, `engine.js:203-204`) The lookup is a plain object, so a
  `ruleType` naming one of its inherited members (`constructor`, `toString`, `__proto__`) gives a function or an object as the kind;
  no input makes one and the corpus holds none (LD13).
- **LDA-3** (**Executed**: `rule/normalizeRule/days*`, `legacy*`; `applies/*/legacy*`) `days`: a copy of `r.days` when it is a list,
  else `[r.dayOfWeek]` when that is not absent or `null`, else every day `[0..6]`. Then kept: the entries whose number (LDG-6) is from 0
  to 6, as stored (`"3"`, `" 3 "`, `null`, `true`, `""`, `[5]`, `0.5` stay; `7`, `-1`, `"x"`, `{}`, `[1, 2]` go); then sorted by their
  texts (JavaScript's default sort: `[10, 2, 1]`'s survivors `[2, 1]` become `[1, 2]`; `["", " 2 ", 0, 0.5, "3", [5], null, true]` is
  the order of their texts). Only number entries ever match a weekday (LDA-5), so a day stored as text or `true` is kept and never
  applies (LD08).
- **LDA-4** (**Executed**: `rule/normalizeRule/rotation*`, 25 cases) `rotation`: when `r.rotation` is truthy, its `anchorDate` truthy
  and its `cycleLength`'s number above 0: `{anchorDate (as stored), cycleLength: round(Number(cycleLength)), activeDays}` with
  `activeDays` the stored list's items through `Number` (`["1", null, "x"]` is `[1, 0, NaN]`), an empty list for a falsy one, and a
  `TypeError` for a truthy one that is not a list (`"012"`, `{0: 1}`, `5`). Otherwise `null`, and the rule is weekly. Note the order:
  the test `cycleLength > 0` is on the stored value, the rounding after it, so `0.4` passes and becomes `0` (LD07).

### 10.3 Which dates a rule applies on

- **LDA-5** (**Executed**: `applies/ruleAppliesOn/week:*`, the 128 subsets of the week over 14 days; `legacy*`) A weekly rule
  (`rotation` null) applies on a date when its `days` hold the date's weekday (LDT-21) as a number.
- **LDA-6** (**Executed**: `applies/ruleAppliesOn/cadence*`, 180 cases, 18 cadences x 10 anchors over 42 days; `oddrotation*`;
  `calendar:*`) A rotation applies on date `D` when `activeDays` includes `i = ((daysBetween(parseYmd(anchorDate), D) % c) + c) % c`
  (`c` the rounded cycle length; LDT-13, LDT-14; `%` is the remainder with the dividend's sign, so the `+ c` folds dates before the
  anchor). `days` are ignored. `parseYmd` needs text: an anchor that is a number, a list or `true` throws `TypeError` when the
  question is asked (LD05). An anchor that is no date (`garbage`, `2026-10`) gives NaN days, so `i` is NaN, which matches only a NaN in
  `activeDays` (LD07); a cycle of 0 (stored above 0 and below 0.5) gives NaN the same way.
- **LDA-7** (**Executed**: `applies/ruleAppliesOn/valid*`, 40 cases; `unpadded-from`, `month-to`) Before anything else: the rule does not apply when `validFrom`
  is truthy and the date's key (LDT-12) is less than it, or `validTo` is truthy and the key is greater, by LDG-6's `<`: as text when
  the stored bound is text (or a list or object: `["2026-10-27"]` works as its text, `{}` is `[object Object]`, after every key), never
  when it is a number or `true`. Both bounds are inclusive. Text comparison makes an unpadded bound wrong: with `validFrom` `2026-1-5` the keys
  `2026-01-01` to `2026-09-30` sort before it (`'0' < '1'`) and `2026-10-01` on after it (`'0' > '-'`), so the rule starts on 1 October
  (LD08). The page's own forms write padded keys.
- **LDA-8** (**Executed**: family `materialize`, 588 cases; `dst`) `materializeRule(rule, days, zone)`: for each date from the day
  before the plan's first day to the day after its last (`days.length + 2` dates), when LDA-5 to LDA-7 say it applies, the interval
  `{ruleId: rule.id, title: rule.title, kind: rule.kind, startEpoch: zonedToEpoch(date, startMin), endEpoch: zonedToEpoch(date,
  startMin + durationMin), ymd: date, rule}` (LDT-6 to LDT-9: a start in a spring gap moves forward by the gap, a time in an autumn
  fold takes LD01's occurrence, an end past 1440 minutes lands on the next date). So an interval keeps the wall clock of its own date:
  a 23:00 + 8 h rule on a 25-hour night is 9 h long. No days throws `TypeError`; a NaN start or length throws `RangeError` (LD04). The
  intervals are in date order; nothing merges or sorts them.
- **LDA-9** (**Executed**: family `ruleon`, 390 cases, the inputs of `applies`; plan defect D3) The page answers the same question
  itself for the dial's handles (`ruleInstances`, `logic.js:589-601`) and the Money tab (`shifts`, `workRules`, `logic.js:2930-2965`), with
  `ruleOn(r, ymd)` (`logic.js:577-588`) on the **stored** rule, not on LDA-1's: LDA-7 as above; a rotation under the same test as LDA-4
  but with the anchor read as `String(anchorDate).split('-').map(Number)` and the stored `cycleLength` `c` used as it is in
  `((n % c) + c) % c`; else the stored `days` list (or every day when it is not a list). They give different answers for exactly
  these stored rules (the web test and `AvailabilityGoldenTest` check the set, LD06):

  | Stored rule | Engine (LDA-1, LDA-5, LDA-6) | Page (`ruleOn`) | Cases |
  |---|---|---|---|
  | `dayOfWeek` without a `days` list | that weekday | every day | `legacy0` to `legacy3` |
  | fractional `cycleLength` (7.4, 7.6, 2.5) | rounded (7, 8, 3) | used as is: `n % 7.4` | `oddrotation0` to `2` |
  | `cycleLength` 0.4 | 0: NaN index, applies only with a NaN active day | `n % 0.4` | `oddrotation3`, `4` |
  | `cycleLength` text or a list (`"8"`, `[5]`) | its number | `(n % c) + c` joins text: `3 + "8"` is `"38"` | `oddrotation5`, `7` |
  | `anchorDate` a number, list or `true` | `TypeError` | read through `String` | `oddrotation20` to `22` |

  (`"3"` happens to agree: `"x3"` and `x` have the same remainder by 3.) Also **Read**, not in the corpus: `ruleInstances` and `shifts` use
  the stored `startMin` and `durationMin` without LDA-1's rounding and clamping, and `shifts` takes a commitment's kind as `c.kind ||
  ...` where LDA-11 puts `EMERGENCY` first, so a commitment with `kind: 'WORK', commitmentType: 'EMERGENCY'` is an emergency to the
  plan and a shift to Money.

### 10.4 Preferences

- **LDP-1** (**Executed**: `prefs/defaultPreferences/defaults`) `defaultPreferences()`: `intensityLevel` `BALANCED`,
  `maxDailyGoalMinutes` 180, `minDailyRecoveryMinutes` 90, `maxConsecutiveHighLoadMinutes` 90, `minSleepMinutes` 450,
  `consequenceMode` `LENIENT`, `preferredRestDays` `[]`, `planningHorizonDays` 7, `missGraceMinutes` 60, `maxHighIntensityPerDay` 3,
  `morningRoutineMinutes` 30, `windDownMinutes` 30, `mealMinutes` 30, `mealOffsetsMinutes` `[60, 330, 630]`, `mealsEnabled` `true`,
  `defaultBedtimeMin` 1380, `avoidWindows` `[]`, `maxFreeMovesPerTask` 3, `leadMinutes` 15, `partialSatisfiedRatio` 0.75
  (`engine.js:244-254`).
- **LDP-2** (**Executed**: family `prefs`, 110 cases; `engine.js:255-264`) `normalizePreferences(p)`: the defaults, then every own member
  of `p` copied over them as stored (`Object.assign`: an object's members, a list's items under `"0"`, `"1"` ..., a text's UTF-16 units
  likewise, nothing from a number or `true`; a falsy `p` copies nothing). Then, each read with LDG-6's `Number` and replaced by its
  default when that is 0 or NaN: `maxDailyGoalMinutes` clamped to 30..480, `minDailyRecoveryMinutes` 30..360,
  `maxConsecutiveHighLoadMinutes` 30..240, `minSleepMinutes` 300..720 (not rounded: `451` and `1e-9`-like values stay, see LDS-6);
  `planningHorizonDays` `clamp(round(Number(x) || 7), 1, 28)` (0.4 is 1, 2.5 is 3, 28.49 is 28); `intensityLevel` not exactly one of
  `LIGHT`, `BALANCED`, `AGGRESSIVE`, `HARDCORE` becomes `BALANCED`, `consequenceMode` not one of `LENIENT`, `MODERATE`, `HARDCORE_RPG`
  becomes `LENIENT`. Every other member is kept exactly as stored, unchecked: the routine, wind-down and meal minutes, the meal
  offsets, `mealsEnabled`, `defaultBedtimeMin`, rest days, and members the page does not know (LD09).
- **LDP-3** (**Executed**: `prefs/normalizePreferences/ceiling:*`, `maxDailyGoalMinutes=*`) Then the ceiling on goal minutes: under
  `HARDCORE`, `min(480, max(goal, 240))`; then `min(goal, HARDCORE ? 480 : 300)`. So no level but `HARDCORE` plans more than 300
  goal minutes a day, and `HARDCORE` always allows at least 240.

### 10.5 Sleep

`deriveSleep(rules, days, zone, prefs, sleepNeedMinutes, hard)` (`engine.js:273-304`) returns `{intervals, warnings}`. In
`deriveAvailability` the rules are LDA-1's sorted by id, `prefs` LDP-2's, `sleepNeedMinutes` is `sleepRequirementMinutes || 480`, and
`hard` the hard intervals of work, class and appointments (LDA-11). The corpus also calls it directly (family `sleep`).

- **LDS-1** (**Executed**: family `sleep`, `dst`) When at least one rule's kind is `SLEEP`, the sleep windows are those rules'
  intervals (LDA-8), and the sleep need, the default bedtime and `hard` play no part.
- **LDS-2** (**Executed**: `sleep/*@default*`, `fuzz*`; `avail/*@default-sleep`) With no SLEEP rule, one default night for each date
  from the day before the plan to the day after: from `zonedToEpoch(date, Number(defaultBedtimeMin))` (Date.UTC reading: fractional
  minutes truncated, a NaN bedtime throws `RangeError`) lasting `sleepNeedMinutes * 60` seconds, `{ruleId: 'default-sleep', title:
  'Sleep', kind: 'SLEEP', ymd: date}`, then LDS-3.
- **LDS-3** (**Executed**: `sleep/*@conflict*`, `*@two-conflicts`, `fuzz*`) The lead is `(morningRoutineMinutes + 15) * 60` seconds
  (LDS-7). The conflict is the earliest-starting interval of `hard` (the first listed among equal starts) that starts at or after the
  bedtime `s` and before the night's end `e` plus the lead. If there is one, the wake-up becomes `start - Number(commuteMin || 0) * 60 -
  lead`, the end `max(s + 3600, wake-up)` and the start `end - sleepNeedMinutes * 60` (so a night is never shortened, only moved
  earlier, and moved by at least an hour less than its length); and a warning `{code: 'SLEEP_SHIFTED', date: ymdKey(date), message:
  'Bedtime moved earlier so you can wake in time for ' + String(title) + '.'}`.
- **LDS-4** (**Executed**: `sleep/*@explicit`, `*@merge*`) The windows are sorted by start (stable: equal starts keep their order, rules
  in id order and each in date order).
- **LDS-5** (**Executed**: `sleep/*@merge-30`, `*@merge-31`) Then merged in order: a window starting no later than 30 minutes after the
  previous merged window's end extends it to the later of the two ends (keeping the first's `ruleId`, `title` and `ymd`); otherwise it
  starts a new one.
- **LDS-6** (**Executed**: `sleep/*@nap-and-short`, `*@conflict-floor`) For each merged window from 3 hours long but shorter than
  `minSleepMinutes`, a warning `{code: 'SLEEP_BELOW_FLOOR', startEpoch, message: 'A sleep window is shorter than your ' +
  String(minSleepMinutes / 60) + 'h minimum. Life Director will never schedule into sleep, but cannot add sleep you have not
  entered.'}`; the number is written as JavaScript writes it (`7.5h`, `7.516666666666667h` for 451).
- **LDS-7** (**Executed**: `sleep/*@string-prefs`, `avail/deriveAvailability/routine-text`) The lead of LDS-3 adds 15 to the stored
  `morningRoutineMinutes` with JavaScript's `+`, so a routine stored as text is joined, not added: `"30"` gives a lead of 3015
  minutes (50 h 15 min), which makes every commitment in the next two days a conflict; LDA-14 reads the same value as the number 30
  (LD09).
- **LDS-8** (**Executed**: `avail/deriveAvailability/overlap-warning`, `odd-commitments`; `fuzz`) After painting sleep,
  `deriveAvailability` warns `{code: 'SLEEP_COMMITMENT_OVERLAP', startEpoch, message: String(title) + ' overlaps your sleep window.
  Commitments win; please check your sleep times.'}` once for each pair of a work or class interval (not an appointment) and a sleep
  window that overlap, when the interval also overlaps the plan (its end after `H0`, its start before `HN`), in the order of the
  intervals and then of the windows.

### 10.6 The grid and painting

- **LDG-4** (**Executed**: families `paint`, `avail`; `engine.js:151-167`) `createGrid` (LDT-16) gives, for its `n` slots, `type` 0
  (FREE), `load` 0, `tag` -1, `bwcOf` -1 and an empty `blocks` list; `taskOwner`, `taskIP`, `taskIC`, `baseP`, `baseC`, `drainP`, `drainC`,
  `runLen`, `runBack` are placement's (WP-LD4) and availability does not write them. The slot codes (`SLOT`, `engine.js:22-25`) are FREE 0,
  EMERGENCY 1, HARD 2, COMMUTE 3, SLEEP 4, MAINT 5, LOCKED 6, RECOVERY 7, PERSONAL 8, TASK 9; a lower code is stronger.
- **LDG-3** (**Executed**: family `paint`, 161 cases; every `avail` case) `paint(g, start, end, code, meta)` (`engine.js:184-198`):
  `a = clamp(slotOf(start), 0, n)`, `b = clamp(slotCeil(end), 0, n)` (LDT-17). When `b <= a` it returns `null` and records nothing.
  Otherwise it records the block `{code, startEpoch: epochOf(a), endEpoch: epochOf(b), ...meta}` at index `blocks.length` **whether or
  not it then writes any slot** (LD11); then each slot `a <= i < b` holding FREE or a weaker (higher) code takes `code` and `tag = index`,
  and, when `code` is HARD and `meta.load` truthy, `load = meta.load`. It returns the index when it wrote a slot, else `null`. So:
  a slot keeps the first of equal codes; a stronger code painted later takes the slot and its tag but leaves its `load`; a NaN bound
  (`b <= a` is false for NaN) records a block with NaN bounds that writes nothing (LD10).

### 10.7 `deriveAvailability`

- **LDA-10** (**Executed**: families `avail`, `scenario`, `dst`, `fuzz`; `engine.js:307-315`) `deriveAvailability(params)`, in this
  order: the zone is `params.timezone || params.playerAvailability.timezone || 'UTC'`, read as its text (a list `["UTC"]` is UTC; the
  result's `tz` is the value as given), refused by LDT-2 with `Error('Invalid IANA timezone: ' + zone)`; `prefs` LDP-2 of
  `params.playerPreferences`; the days LDT-15 from `params.horizonStartEpoch` for `Number(params.horizonDays || prefs.planningHorizonDays)`
  days (`"2"` is 2, `1.5` is 2: the days `i` with `i < count`), none of them throwing `TypeError` (LDG-5); the grid (LDT-16, LDG-4); the
  rules `(av.rules || av.weeklyRecurringRules || [])` each through LDA-1 with its list index, sorted by id (LDG-2, stable). The page's
  caller (`logic.js:111-118`) passes its own zone in both places, the availability as stored, `ST.prefs` and the normalised horizon.
  Then LDA-11 to LDA-17. The result is `{grid, wakeCycles, warnings, prefs, tz, rules}`; warnings come in the order SLEEP_SHIFTED and
  SLEEP_BELOW_FLOOR (LDS), then SLEEP_COMMITMENT_OVERLAP (LDS-8).
- **LDA-11** (**Executed**: `avail/*@commitments`, `*@blackouts`, `odd-commitments`, `load-none`, `legacy-names`; `fuzz`;
  `engine.js:317-336`) The hard intervals, in this order: each non-SLEEP, non-PREFERRED rule's intervals (LDA-8) with its `commuteMin`,
  `load` and `decompressMin`; the one-off `fixedCommitments` (a list, or `TypeError`; a `null` item `TypeError`) sorted by `Number(start)`
  then by `cmpStr(String(id || commitmentId))`, each `{ruleId: String(id || commitmentId), title: title || 'Commitment', kind,
  startEpoch, endEpoch, commuteMin: commuteMin || 0, load: shiftIntensity || load || 'MIXED_MODERATE', decompressMin}` where `kind` is
  `EMERGENCY` when `commitmentType` or `kind` is exactly `EMERGENCY`, else `kind || (commitmentType === 'CLASS' ? 'CLASS' : 'WORK')` (any
  stored value: a commitment of kind `SLEEP` or `MEETING` is painted as a commitment); and the blackouts (`av.blackouts ||
  av.oneTimeBlackouts`, in stored order) `{ruleId: String(id || blackoutId), title: reason || title || 'Unavailable', kind: SICK when
  `kind === 'SICK'` else BLACKOUT}`. Only those ending after `H0 - 1 day` and starting before `HN + 1 day` take part ("timed"). Each
  timed interval is painted (LDG-3): BLACKOUT, SICK and EMERGENCY as EMERGENCY `{kind, title, refId}`; UNAVAILABLE as PERSONAL `{kind:
  'UNAVAILABLE', title, refId}`; every other kind as HARD `{kind, title, refId, load}` with `load` the class of `String(load)`
  (`PHYSICAL_HEAVY` 1, `COGNITIVE_HEAVY` 2, `MIXED_MODERATE` 3, `LIGHT` 4; anything else, and `NONE`, whose class 0 is falsy, 3: LD12).
- **LDA-12** (**Executed**: `avail/*@office`, `*@heavy`, `*@light-no-decompress`, `*@decompress-given`, `decompress-text`; `dst/*commitment-across`;
  `engine.js:337-348`) Then, for each timed interval of kind WORK, CLASS or APPOINTMENT: with `cm = Number(commuteMin || 0) * 60` truthy,
  a COMMUTE `{kind: 'COMMUTE', title: 'Commute', refId}` from `start - cm` to the start and `{..., title: 'Commute home'}` from the end to
  `end + cm`; and the decompression: `decompressMin` when not absent or `null`, else 30 for a WORK interval of 3 hours or more whose load
  is not exactly `LIGHT`, else 0; when that is truthy, a COMMUTE `{kind: 'DECOMPRESS', title: 'Decompress', refId, restorative: true}`
  from `end + cm` to `end + cm + Number(dec) * 60`. A stored decompression of `"abc"` is truthy and gives NaN, so its block is recorded
  with a NaN end and writes nothing (LD10); a decompression after a commute of NaN minutes likewise.
- **LDA-13** (**Executed**: every `avail`, `scenario`, `dst` and `fuzz` case; `naps-only`; `engine.js:349-377`) After sleep (LDS-1 to
  LDS-6) is painted as SLEEP `{kind: 'SLEEP', title: 'Sleep', protected: true}` and LDS-8 is checked: the main sleeps are the windows
  of 3 hours or more (shorter ones are naps). After each main sleep `i` a waking day runs from its end `wake` to the next main sleep's
  start, or `wake + 16 h` after the last; it is skipped when that end is at or before `H0` or `wake` at or after `HN`, and when its slots
  `a = clamp(slotCeil(wake), 0, n)` to `b = clamp(slotOf(end), 0, n)` number fewer than 2. Otherwise it is `{index, id: 'bwc-' + wake,
  wakeEpoch, sleepEpoch, startSlot: a, endSlot: b, sleepBeforeMin: length of the sleep / 60, dateKey: ymdKey(localParts(wake)), dow,
  label: null, truncatedStart: wake < H0, heavyShiftEndSlot: -1}` plus placement's counters at zero (`goalMin`, `highCount`, `perKey`
  `{}`, `recoverySlots`, `freeSlots`) and `maintBlocks` `[]`, and its slots get `bwcOf = index` (waking days never overlap: each ends where the
  next main sleep starts).
- **LDA-14** (**Executed**: `avail/*@no-meals`, `*@five-meals`, `meal-offsets-text`; every plan case; `engine.js:378-412`) For each
  waking day not `truncatedStart`: the morning routine from `wake` to `wake + Number(morningRoutineMinutes) * 60` (`ROUTINE`,
  `restorative: false`), then the meals (LDA-15) when `mealsEnabled` is truthy (`mealOffsetsMinutes` not a list throws `TypeError`),
  then the wind-down from `sleepEpoch - Number(windDownMinutes) * 60` to `sleepEpoch` (`WIND_DOWN`, `restorative: true`). The routine and
  wind-down slots are those of LDG-3 clamped to the waking day (`startSlot` to `endSlot`), and are painted as MAINT `{kind, title}`
  only when at least one of them is FREE.
- **LDA-15** (**Executed**: `avail/*@meal-in-shift`, `*@five-meals`, `meal-text`; plan cases; `engine.js:384-389, 415-428`) The `k`th
  offset gives a meal titled `Breakfast`, `Lunch`, `Dinner`, then `Meal`, starting at `s = wake + Number(offset) * 60`, skipped when
  `s` is at or after the wind-down's start. It needs `ceil(Number(mealMinutes) / 15)` slots. When the slot of `s` (`slotOf`) is inside the
  grid and HARD, the meal is taken to be eaten on a break: nothing is painted and nothing listed. Otherwise the first run of that many
  slots, all FREE and inside the waking day, starting at `want = slotOf(s)`, then `want + 1`, `want - 1`, `want + 2` ... up to
  `want - 8` (two hours either way), is painted MAINT `{kind: 'MEAL', title, restorative: true}`; with none, `{skipped: title}` is added to
  the day's `maintBlocks`. Only a HARD first slot counts as a break: a meal starting in a commute or an emergency slides.
- **LDA-16** (**Executed**: `avail/*@heavy`, `fuzz`; `engine.js:392-397`) For each timed WORK interval of 6 hours or more whose load is
  exactly `PHYSICAL_HEAVY`, every waking day with `wake < end <= sleepEpoch` gets `heavyShiftEndSlot = max(itself, clamp(slotCeil(end), 0,
  n))`.
- **LDA-17** (**Executed**: `avail/*@preferred-unavailable`, `dst/*+quarters`, `fuzz`; `engine.js:398-404`) Last, `preferredMask`: 1 for
  every slot from `clamp(slotOf(start), 0, n)` up to `clamp(slotCeil(end), 0, n)` of every PREFERRED rule's intervals (LDA-8), else 0. A
  PREFERRED rule paints nothing and affects nothing else here.

### 10.8 What was measured

- 3,797 cases in Chromium 153 (the file), identical in node 22 (the web test requires both); the native code on the JVM reproduces all
  3,797 exactly (`AvailabilityGoldenTest`). Every zone is a strict zone of the WP-LD1 matrix.
- The ten scenarios: `office`, `night`, `rotating`, `student`, `abundant`, `limited` in all 11 zones (office and student, whose rules
  depend on the seed, at seeds 1 and 2); `manygoals`, `deadlines`, `misses` and `workchange` plan office's availability exactly (the web
  test checks their rules and sleep need for seeds 1 to 20) and are recorded in UTC and New York.
- Every 2026 and 2027 clock change of the matrix (New York, Los Angeles, Santiago, London, Sydney; 20 changes): rule starts and ends
  in the gap or fold and either side of it, a sleep across it and one ending in it, quarter-hour rules at its edges, a default bedtime
  inside it, a commitment across it; Santiago's skipped midnight (LDT-8) included.
- Source mutations of the native code, each of which makes the replay fail (section 8.2).

---

## 11. Demand: tags, goals, quests and candidate tasks (part 3, WP-LD3)

This part covers `engine.js:431-789` (the archetype and tag tables, tags, goals and their templates, the goal plans, quests, prerequisite
cycles, `compileCandidates`, `standaloneQuestTask`, `freshIds`, `makeTask`, `diag`), and from the placement part the pieces the plan's
demand half needs before anything is placed: `weeksOf` (`1152-1159`), `goalInfoMap` (`1190-1203`), `prepTask` (`899-908`), the fairness
shares (`1225-1227`), `questCoverage` (`1496-1503`), and `recomputeBwc` (`820-836`, the wake cycle's drain arrays), plus the two
floating-point functions the energy model and the scores use, `Math.exp` and `Math.pow` (`844`, `906`, `993`, `1006`). New prefixes:
`LDD-n` tags, goals and templates, `LDQ-n` quests, `LDC-n` candidate tasks, `LDE-n` the per-task and per-cycle numbers, `LDF-n`
floating point. **Executed** rules cite families of `lifemax_modular_v73/tests/fixtures/ld-demand-golden.json.gz` (section 8.3); the native
code in `android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector` (`DemandTables`, `ActivityTags`, `PlayerGoals`,
`GoalPlans`, `PlayerQuests`, `CandidateTask`, `Candidates`, `QuestDemand`, `TaskPreparation`, `WakeCycleDrain`, `PlanDemand`, `PageMath`)
replays every case.

Inputs are the stored goals (`ST.goals`, written by `Do.goalAdd`, `logic.js:2498-2518`, and the clarify and priority commands) and the
quests the host reads (`Host.readQuests`, `engine.js:2453`), as JSON values; LDG-6's value rules apply throughout. The tables are never
retyped: the recorder exports `ARCHETYPES`, `TAG_PROFILE`, `QUEST_WEIGHT` and `LOAD_DRAIN` from the page to
`android-wrapper/native/src/main/resources/lifedirector/demand-tables.json`, the web test fails when the export drifts, and the native
code reads that file (strictly: an unknown or missing member refuses it).

### 11.1 Tables

| Table | Source | Content |
|---|---|---|
| `ARCHETYPES` | `engine.js:435-460` | 8 goal kinds, each `{title, keywords, templates}`; a template is `{key, title, tag, d: [min, opt, max] minutes, f: [MED, target, MRV] per week, IP, IC (1-5), spacingMinH, spacingIdealH}` and optionally `spacingMinHNovice`, `avoidBeforeSleepMin`, `phases`, `volumeDriver`, `preferRestDay`, `maxPerBwc`, `requiresPrior: {keys, count}` |
| `TAG_PROFILE` | `462-467` | 11 tag roots `{IP, IC, d}`: the default demand of a quest's own session |
| `QUEST_WEIGHT` | `587` | MANDATORY 5, CORE 4, OPTIONAL 2 |
| `LOAD_DRAIN` | `31` | shift load class 1 to 4: `[physical, cognitive]` drain per hour |

### 11.2 Tags

- **LDD-1** (**Executed**: family `tag`, `tagMatches`, 2,704 pairs of 52 tags plus 40 odd values; `engine.js:470-474`) `tagMatches(req, tpl)`:
  false when either is falsy. A requirement ending in `.*` with base `b` (the text before it) matches `tpl === b` or `tpl` starting with
  `b + '.'`; any other requirement matches itself and anything starting with `req + '.'`. Exact UTF-16 text: no case folding (`Fitness`
  is not `fitness`), no normalisation (`ﬁtness` with the ligature is not `fitness`). Consequences of the plain prefix test, all recorded:
  `.*` alone matches only tags starting with a dot (there is no "match everything"); `*` is no wildcard; `fitness.` matches only
  `fitness.` itself; `a.*.b` matches only itself (a wildcard in the middle is literal); `""` never matches. A requirement that is truthy
  and not text (a number, a list, `true`) throws `TypeError`, and so does a template tag that is not text once it is compared by prefix
  (`tag/tagMatches/odd:*`).
- **LDD-2** (**Executed**: `tag/tagDepth/*`, 204 cases; `engine.js:475`) `tagDepth(req, tpl)` ranks a match: 1000 when the two are the same
  text, else the number of `.`-separated parts of `req` after its **first** `.*` is removed, wherever it is (`a.*.b` counts as `a.b`, 2;
  `fitness.*` 1; `""` 1).
- **LDD-3** (**Executed**: `tag/tagProfile/*`, 60 cases; `engine.js:468`) `tagProfile(tag)`: the `TAG_PROFILE` entry of the text before the
  first dot of `String(tag || 'general')`, else `general` `{IP 2, IC 2, d 30}` (`["study", "x"]` is the text `study,x`: general). (**Read**)
  The lookup is a plain object: a root naming an inherited member (`constructor`) gives a function; no input in the corpus does (LD23).

### 11.3 Goals and templates

- **LDD-4** (**Executed**: family `goal`, `normalizeGoal`, 679 cases: 21 members x 30 odd values and left out, 22 legacy shapes, 6 whole
  values; `engine.js:484-497`) `normalizeGoal(g)` (`null` throws `TypeError`; any other non-object has no members):
  - `id`: `String(g.id || g.goalId)`: a goal with neither is `"undefined"` (LD19); `title`: `String(g.title || 'Goal')`.
  - `archetypeId`: `g.archetypeId || (g.domainArchetype && g.domainArchetype !== 'CUSTOM' ? g.domainArchetype : null)`, as stored.
  - `subject`: when truthy, `String(subject).toLowerCase()` with everything but `a-z`, `0-9` and `_` removed, else null. Lower-casing is
    Unicode's (`İSTANBUL` is `istanbul`, the dotted capital becoming `i` plus a combining dot that is then removed; `ΣΑΣ` lower-cases with
    a final sigma and then vanishes); every other letter is dropped (`Español` is `espaol`, `日本語` is `""`) (LD21).
  - `priority`: `clamp(round(Number(g.priority || 3)), 1, 5)`: `0` is 3, `4.5` is 5, `5.5` is 5, `"2"` 2, and a value that is no number is
    NaN, kept (`goal/normalizeGoal/priority=17`, LD22).
  - `level`: `g.level || g.experienceLevel` when it is exactly `NOVICE`, `INTERMEDIATE` or `ADVANCED`, else `NOVICE`.
  - `customTemplates`, `disabledTemplates`: the stored list, or `[]` for anything that is not a list.
  - `target`: `g.target` when truthy (as stored, any value), else, when `targetMetricValue` is truthy, `{units: targetMetricValue,
    completed: currentMetricValue || 0, unit: metricUnit, deadlineEpoch, unitsPerHour: unitsPerHour || null}` (absent members left out),
    else null.
  - `createdEpoch`: `g.createdEpoch || g.startEpoch || null`; `status`: `g.status || g.schedulingState || 'ACTIVE'`, as stored. Only the
    exact text `ACTIVE` is planned.
- **LDD-5** (**Executed**: `goal/suggestTemplates/*`, 60 titles; `engine.js:477-482`) `suggestTemplates(title)`: in `String(title ||
  '').toLowerCase()`, count each archetype's keywords that occur as text (`fit` occurs in `fitness`, `read` in `bread`, `save` in
  `sauvegarder`); keep the archetypes with a count above 0, most first, then by id (UTF-16); the first three ids.
- **LDD-6** (**Executed**: family `template`, the archetype cases; `engine.js:499-506`) `goalTemplates(goal)`: first the archetype's
  templates (`ARCHETYPES[String(archetypeId)]` when `archetypeId` is truthy: `["MINDFULNESS"]` works through its text), in table order,
  leaving out those whose `key` is in `disabledTemplates` (strict equality: `"VOCAB"` does not disable `vocab`); each is the table's
  object with `templateId` and `spacingKey` `goal.id + ':' + key`, `goalId`, and, when the subject is non-empty and the tag starts with
  `language.`, the tag with its first `language.` replaced by `language.<subject>.` (`language.spanish.vocab`).
- **LDD-7** (**Executed**: `template/goalTemplates/custom:*`, `perWeek:*`, `minutes:*`, `list*`; `engine.js:507-518`) then each custom template
  `c` of the list that is truthy with a truthy `tag` and a truthy `minutes` or `d` (others are skipped): `dOpt = ceilTo15(c.minutes ||
  c.d[1] || 30)` (LDT-20; `c.d[1]` is a list's second item, a text's second character, an object's member `"1"`); `freq =
  clamp(round(Number(c.perWeek || c.f[1] || 3)), 1, 14)`; `key = String(c.key || c.tag)` keeping only ASCII letters, digits, `_`, `.`,
  `-`; `{key, title: c.title || goal.title, tag: c.tag, templateId, goalId, spacingKey, d: c.d || [max(15, dOpt - 15), dOpt, dOpt + 15],
  f: c.f || [max(1, freq - 1), freq, min(14, freq + 1)], IP: clamp(c.IP || profile.IP, 1, 5), IC likewise (LDG-1: an in-range text stays
  text), spacingMinH: c.spacingMinH != null ? c.spacingMinH : (freq >= 7 ? 8 : 16), spacingIdealH: c.spacingIdealH || max(12,
  round(168 / freq)), maxPerBwc: c.maxPerBwc || 1, phases: c.phases || ['ANY'], volumeDriver: !!c.volumeDriver}` with `profile` LDD-3's
  of `c.tag`. The page's form always stores numeric `minutes`, `perWeek`, `IP` and `IC` (`logic.js:2507`).

### 11.4 Goal plans

- **LDD-8** (**Executed**: family `expand`, 270 cases; `engine.js:523-533`, `561-566`) `expandGoalActivities(goals, prefs, now)`: `goals`
  must be a list (anything else throws `TypeError`; so does a `null` goal); sorted by `String(id || goalId)` (UTF-16, stable); each goal
  through LDD-4; a goal whose status is not `ACTIVE` is skipped; a goal without templates gives `{goalId, title, reasonCode:
  'MISSING_ACTIVITY_PARAMETERS', missingParameters: ['activityTypeTag', 'weeklyFrequency', 'sessionDurationMinutes'],
  suggestedTemplateIds: LDD-5 of its title}`; otherwise one plan per template (LDD-9). Returns `{plans, clarificationPrompts,
  trajectoryWarnings}`. The caller passes LDP-2's preferences; only `intensityLevel` is read.
- **LDD-9** (**Executed**: `expand/*@<archetype>@<level>@<intensity>`, 96 cases; `engine.js:534-541`, `561-562`) For a template with `f =
  [fMED, fTarget, fMRV]` (its first three items; `f` must be iterable) and `[dMin, dOpt, dMax] = d.map(ceilTo15)`: `base` is `fMED` for a
  novice, `fTarget` intermediate, `min(fMRV, fTarget + 1)` advanced; `stretch` is `fMRV`; then under `LIGHT` both are `fMED`, under
  `BALANCED` `stretch = base`, under `AGGRESSIVE` and `HARDCORE` unchanged. After LDD-10, the plan is `{goal, template, fMED (as stored),
  base: max(fMED, base), stretch: max(base, stretch), dMin, dOpt, dMax, status, projection, spacingMinH}` with `spacingMinH` the template's
  `spacingMinHNovice` for a novice when that is truthy, else its `spacingMinH`. (**Read**) A `d` with fewer than three items gives
  undefined lengths; the corpus does not hold one beyond LDD-7.
- **LDD-10** (**Executed**: `expand/*pace:*`, 162 cases, `target*`; `engine.js:543-560`) Pacing for a volume driver: when the template's
  `volumeDriver` is truthy and the goal's target has `units > 0`, a truthy `deadlineEpoch` and `unitsPerHour > 0`: `left = max(0, units -
  (completed || 0))`, `weeks = max(1, (deadline - now) / 604800)`, `need = ceil((left / unitsPerHour) * 60 / weeks)` minutes a week,
  `least = fMED * dMin`, `most = fMRV * dMax`. When `need > most`: status `DEADLINE_UNSUSTAINABLE`, `minutes = most`, `projection =
  round(now + (left / unitsPerHour) * 60 / most * 604800)` and a warning `{goalId, code: 'DEADLINE_UNSUSTAINABLE',
  projectedCompletionEpoch: projection, message: 'At a safe maximum of ' + most + ' min/week, ' + title + ' finishes later than your
  deadline.'}`; else `minutes = max(least, need)`. Then `k = clamp(ceil(minutes / dOpt), fMED, fMRV)`, `dOpt = clamp(ceilTo15(minutes /
  k), dMin, dMax)`, `base = max(base, k)`, `stretch = max(stretch, base)`. Stored target values are read through `Number` (`"100"` works).

### 11.5 Goal information and the per-task numbers

- **LDE-1** (**Executed**: family `goalinfo`, 23 cases; every `scenario` and `fuzz` case; `engine.js:1190-1203`) `goalInfoMap(goals, now)`:
  for each goal (LDD-4) by id, a later one with the same id replacing an earlier one, `{lag, priority, title}`; `lag` is 0.5 unless the
  target is truthy with `units > 0`, a truthy `deadlineEpoch`, a truthy `createdEpoch` and `deadlineEpoch > createdEpoch` (LDG-6's
  comparison: as text when both are text), then `clamp(0.5 + 2 * (clamp((now - created) / (deadline - created), 0, 1) -
  clamp((completed || 0) / units, 0, 1)), 0, 1)`.
- **LDE-2** (**Executed**: family `prep`, 366 cases, and every task of `scenario` and `fuzz`; `engine.js:899-908`) `prepTask(S, t, goalInfo)`
  sets, in this order: `dOptSlots = ceil(dOpt / 15)`; `dMinSlots = min(dOptSlots, ceil(dMin / 15))`; `_prio = 0.60 * ((p - 1) / 4) +
  0.40 * ((questWeight || 0) / 5)` with `p = priority || 3`; `_lag` the goal's lag (LDE-1) when `goalId` is truthy and known, else 0.5;
  `_urgBase = exp(-3 * max(0, (deadlineEpoch - now - dOpt * 60) / (HN - H0)))` when `deadlineEpoch` is truthy (LDF-1), else 0.25;
  `_high = IP >= 4 || IC >= 4` (LDG-6's comparison: `"4"` is high). `H0` and `HN` are the plan's bounds (LDT-16), `now` the plan's clock.
- **LDE-3** (**Executed**: family `drain`, 271 cases; `engine.js:820-836`, `164-165`) `recomputeBwc(S, bw)`, for the slots `i` from the wake
  cycle's `startSlot` up to its `endSlot`: `drainP[i]` and `drainC[i]` take the drain so far, **stored in a `Float32Array`** (rounded to
  the nearest single-precision value; the running sums stay doubles); then the sums grow, per slot, by `LOAD_DRAIN[load] / 4` for a HARD
  slot with a load (a load class missing from the table throws `TypeError`), by `0.015 * taskIP / 4` and `0.015 * taskIC / 4` for a TASK
  or LOCKED slot, or fall by `0.06 / 4` (never below 0) for a RECOVERY slot or a MAINT or COMMUTE slot whose painting block (LDG-3) is
  restorative (a tag naming no block throws `TypeError`). Then `runLen[i]` (from the end backwards) and `runBack[i]` (from the start) count
  consecutive FREE slots at or after `nowSlot`, in an `Int16Array`. Slots outside the cycle are untouched.
- **LDE-4** (**Executed**: families `scenario`, 110 cases, and `fuzz`, 400; `engine.js:1205-1228`) The demand half of `generateSchedule`, in
  order: availability (LDA-10) gives the days, `H0`, `HN` and the preferences; weeks (LDC-1); goal plans (LDD-8) of `input.goals || []` at
  `now = currentTimeEpoch`; the completion history items with `startEpoch >= H0 - 7 days` (a filter that changes no candidate:
  only history inside the plan's weeks counts, LDC-2); candidate tasks (LDC-2 to LDC-10) with the
  kept sessions and used instance ids `prepareState` finds (placement, WP-LD4; a case gives them); goal info (LDE-1) of `input.goals`;
  LDE-2 on every task; the fairness share of each goal id that has a task, in UTF-16 order: its priority (3 when it has no info) over the
  sum, a sum of 0 or NaN counting as 1.

### 11.6 Quests

- **LDQ-1** (**Executed**: family `quest`, 1,246 cases: 30 members x 30 odd values and left out, 10 requirement members x 30, 11 odd
  shapes, 5 whole values; `engine.js:568-586`) `normalizeQuest(q)` (`null` throws `TypeError`): `id: String(q.id || q.questId)`, `title: String(q.title ||
  'Quest')`, `tier`: `MAIN_STORY` and `MANDATORY` MANDATORY, `CORE` CORE, `SIDE`, `BOUNTY` and `OPTIONAL` OPTIONAL by the text of
  `q.tier || q.questTier`, anything else CORE; `status: q.status || 'ACTIVE'`, `deadlineEpoch: q.deadlineEpoch || null`,
  `prerequisiteQuestIds`: each item of the stored list through `String` (a truthy value that is not a list throws `TypeError`), `exclusive:
  !!(exclusive || requiresDedicatedExclusiveTime)`, `schedulingMode: q.schedulingMode || 'SESSIONS'`, `recurrence: q.recurrence ||
  null`, `minSessionMinutes`, `sessionMinutes`, `minIntensity` each `|| 0`, `appliedEventIds || []`, `bounty || null`, `source ||
  'host'`, `onlyDays`: a copy of a non-empty stored list, else null, `windowStartEpoch || null`, `hostRef || null`, all as stored; and
  `requirements` (LDQ-2). Note what it leaves out: `anchor` (LD15).
- **LDQ-2** (**Executed**: `quest/normalizeQuest/req.*`, 300 cases; `odd*`) The requirements: the stored list when it is a non-empty list,
  else one built from the quest: `{requirementId: 'r0', activityTypeTag: tag || activityTypeTag, targetCount: requiredCount ||
  targetCount || 0, currentCount: progressCount || currentCount || 0, targetMinutes: requiredMinutes || 0, currentMinutes:
  progressMinutes || 0}`. Each (index `i`; a `null` item throws `TypeError`) becomes `{id: String(requirementId || id || 'r' + i), tag:
  activityTypeTag || tag || 'general' (as stored), targetCount, currentCount, targetMinutes, currentMinutes: each max(0, Number(x || 0))
  (NaN stays NaN), minSessionMinutes: its own || the quest's || 0, sessionMinutes likewise}`.
- **LDQ-3** (**Executed**: family `cycle`, 236 cases: 16 hand graphs, 220 seeded; `engine.js:590-608`) `findQuestCycles(quests)`: Tarjan's
  strongly connected components over each quest's prerequisites that name a quest of the list (others ignored; with two quests of one id,
  the later one's prerequisites), visited from each id in UTF-16 order, a prerequisite's list in stored order; a component of two or more,
  or one quest naming itself, is a cycle; the result is the set of their ids, in the order the components close (each component in the
  order it is popped).
- **LDQ-4** (**Executed**: family `coverage`, 24 cases; `engine.js:1496-1503`) `questCoverage(quests, planned, status)`: for each active
  quest (status exactly `ACTIVE`), `{questId, title, tier, status: status[id] || quest status, deadlineEpoch, scheduledSessions: the planned
  sessions carrying one of its requirements, remainingCount: the sum of max(0, targetCount - currentCount), recurrence, covered}` with
  `covered` null when the scheduling mode is `NONE`, else, daily, at least one session, else at least `remainingCount` sessions. Minutes
  are not counted: a quest asking only for minutes is covered with no session at all (LD25).

### 11.7 Candidate tasks

- **LDC-1** (**Executed**: `ids/weeksOf/*`, 102 cases; `engine.js:1152-1159`) `weeksOf(days)`: the plan's days in runs of seven from the
  first: `{index, key: the first day's key, startEpoch: its start, endEpoch: the last day's end, days: how many}` (the last run may be
  shorter). Weeks start on the plan's first day, not on a Monday.
- **LDC-2** (**Executed**: families `candidates`, `scenario`, `fuzz`; `engine.js:614-622`, `624-632`) Counting: every history item and every
  kept session whose `startEpoch` falls in week `w` (`start <= epoch < end`, the first such week) and has a truthy `templateId` counts one
  for `templateId|w`. A `null` item throws `TypeError`. Then, for each plan (LDD-8 order) and each week: `scale = days / 7`, `total =
  max(0, round(stretch * scale))`, and `freshIds(used, templateId + '|' + week key + '|', max(0, total - counted))` (LDC-9) new sessions
  numbered `k = counted + j`.
- **LDC-3** (**Executed**: `candidates/*tiers@*`, `horizon*`, `history-*`, `kept-counts`; `engine.js:633-646`, `777-785`) Each goal session
  is `makeTask`'s defaults (`linkedQuestRequirementIds []`, `dependsOn []`, `minLagMin 0`, `exclusive false`, `requiresPrior null`,
  `blockedReason null`, `origStartEpoch null`, `isPlaced false`) with: `kind 'GOAL'`, the template's `title`, `templateId`, `tag`, `IP`,
  `IC`, `spacingKey` and `spacingIdealH` as stored, `goalId`, `goalTitle`, `linkedGoalIds [goalId]`, `priority` (LDD-4), `questWeight 0`,
  `tier` 3 (`WEEKLY_MINIMUM`) for `k < round(fMED * scale)`, else 2 (`WEEKLY_TARGET`) for `k < round(base * scale)`, else 1
  (`STRETCH_VOLUME`); the plan's `dOpt`, `dMin`, `dMax`, `spacingMinH`; `maxPerBwc || 1`, `avoidBeforeSleepMin || 0`, `phases ||
  ['ANY']`, `preferRestDay: !!`; `windowStart: max(week start, now)`, `windowEnd: week end`, `deadlineEpoch null`, `week`, `sessionIndex
  k`, `requiresPrior`: for a template with one, `{templateIds: goalId + ':' + each key, count}`; `goalStatus`: the plan's status.
- **LDC-4** (**Executed**: `candidates/*` quest cases; `engine.js:649-663`, `786-789`) The quests, through LDQ-1 and sorted by id
  (stable), each given a status in this order: not `ACTIVE`: its stored status; in a cycle (LDQ-3): `DEPENDENCY_CYCLE` and a rejection
  `'<title> is part of a circular prerequisite chain, so it cannot be scheduled.'`; a prerequisite that is a known quest neither
  `COMPLETED` nor `ACTIVE`: `BLOCKED_BY_PREREQUISITE` and `'<title> waits for a prerequisite quest that is not active.'` (an unknown
  prerequisite does not block); scheduling mode `NONE`: `PASSIVE` and a reminder `{questId, title, anchor: 'WAKE'}` (always `WAKE`, LD15);
  a truthy deadline at or before now: `DEADLINE_PASSED`; else `SCHEDULING`. A rejection is `diag` with no task: `{taskId: questId, title:
  null, tier: null, reasonCode, humanExplanation, linkedQuestIds: [], goalId: null}`. A scheduling quest's windows: daily (`recurrence ===
  'DAILY'`), one per plan day whose weekday is in `onlyDays` (strict: `"1"` is no weekday) or every day, `{start: max(day start, now),
  end: day end, key: day key, isToday}`; otherwise one `{start: max(now, windowStartEpoch || 0), end: min(deadline || Infinity, last week's
  end), key: 'once'}`. For each requirement and each window not empty (`end <= start` skips it): the count still needed is `max(0, target -
  current)`, but on a daily quest's later days `max(0, targetCount || 1)` from zero (LD17), and the minutes likewise; each kept session that
  carries this quest and requirement (a stored link, strict equality) and starts inside the window takes one off the count (while above
  0) and its rounded minutes off the minutes; the session length is `ceilTo15(sessionMinutes || minSessionMinutes || profile.d)` (LDD-3);
  a count of 0 with minutes left becomes `ceil(minutes / length)`; with a count of 0, nothing more (a fractional count stays fractional:
  LDC-9 gives its ceiling).
- **LDC-5** (**Executed**: `candidates/*deadline-in-*`, `tier-*`, `daily*`; `engine.js:678-680`) The requirement's tier: 4 when the deadline is
  truthy, the quest is not OPTIONAL and `deadline - now <= 86400 + length * 60`, or when the quest is daily, not OPTIONAL and the window
  ends within a day of now (today); else 2 for OPTIONAL, else 3. Its link is `{questId, reqId, questTitle, deadlineEpoch}` with the
  window's end for a daily quest, else the stored deadline.
- **LDC-6** (**Executed**: `candidates/*quest-merged*`, `assessment-*`, `checkpoint`, `depth-ranking`, `ranking-ties`, `min-*`, `two-quests-same-tag`,
  `custom-goal-quest`; `engine.js:681-701`, `716-730`) Coalescing: unless the quest is exclusive, the compatible tasks are those already
  made (goal sessions and earlier quests' sessions, in creation order) with a non-empty `goalId`, not exclusive, not `QUEST`, without a
  truthy `coverable`, whose tag LDD-1 matches the requirement's, that is not a check-in tag (ending `.assessment` or `.checkpoint`) unless
  equal to the requirement's, with `dMax >= (minSessionMinutes || 0)` and `max(IP, IC) >= minIntensity`. They are grouped by template (in
  order of first appearance) and ranked by LDD-2 depth of the group's first task (deeper first), then by how many of the group's tasks lie
  in the window (`windowStart < end && windowEnd > start`, more first), then by template id; the best group's first task is `best`. For a
  non-daily quest: the best group's tasks in the window not yet carrying this quest, by week then session index, take the requirement on
  in place until the count is reached: the link is added, `kind` becomes `MERGED`, the window narrows to the intersection, the deadline
  becomes the link's (when truthy), a truthy `minSessionMinutes` raises `dMin` to its `ceilTo15` and `dOpt`, `dMax` to at least that, the
  tier rises to the requirement's when higher (reason `CRITICAL_DEADLINE` for 4, else `ACTIVE_QUEST`), and `questWeight` to at least
  `QUEST_WEIGHT[tier]`. Then the rest is made of copies of `best` (`extraFrom`): its members, with `id` from `freshIds(used, 'qx|' + quest
  + '|' + req + '|' + window key + '|', rest)`, `kind 'MERGED'`, `sessionIndex 100 + k`, the link alone, the requirement's tier and
  reason, `questWeight`, the window, the link's deadline, `dependsOn []`, `requiresPrior null`, `coverable null`, and `dOpt`, `dMin` each
  at least `ceilTo15(minSessionMinutes || 15)` (`dMax` unchanged).
- **LDC-7** (**Executed**: `candidates/*quest-alone`, `session*`, `exclusive`, `legacy-quest`; `engine.js:731-733`, `753-766`) Whatever the
  coalescing did not cover becomes the quest's own sessions, ids from `'q|' + quest + '|' + req + '|' + key + '|'`: `kind 'QUEST'`, title
  the quest's, `templateId` and `spacingKey` `'quest:' + quest + ':' + req`, the requirement's tag, `goalId null`, `priority 3`, the
  weight, tier and reason (`CRITICAL_DEADLINE`, `ACTIVE_QUEST` for 3, `SIDE_QUEST` for 2), `dMin = ceilTo15(minSessionMinutes || max(15,
  length - 15))`, `dOpt = max(length, dMin)`, `dMax = dOpt + 15`, `IP` and `IC` the profile's, `spacingMinH` 0 for an exclusive quest
  else 3, `spacingIdealH 24`, `maxPerBwc` 1 exclusive else 2, `avoidBeforeSleepMin` 120 when the profile's IP is 4 or more, `phases
  ['ANY']`, the window, the link's deadline, `week: max(0, week of the window's start)`, `sessionIndex k`, `exclusive`, `questId`. (**Read**)
  The parameter meant to give these sessions a goal session's spacing (`sameKindTpl`) never takes effect (LD18).
- **LDC-8** (**Executed**: `candidates/*daily*`, `exclusive-daily`; `scenario` office; `engine.js:702-715`) A daily quest is not merged in
  place: with `cover = {reqTag, start, end, link, exactOnly: exclusive}`, when there is a `best` whose `spacingMinH || 0` is at most 24,
  the whole count is made of copies of `best` (LDC-6's `extraFrom`, ids from `'qx|...'`) with `coverable: cover`; otherwise of the quest's
  own sessions (LDC-7) with `coverable: cover`. Either way nothing is left for LDC-7's last step.
- **LDC-9** (**Executed**: `ids/freshIds/*`, 71 cases, and every task id; `engine.js:768-776`) `freshIds(used, base, count)`: for `k = 0, 1,
  2, ...` while fewer than `count` ids are found and `k < count + 200`: `id = stableId('t', base + k)` (LDH-3), skipped when `used` holds
  `'i' + id.slice(1)` (the instance id of that task: `i_` and the same digits). So an id is a function of the base and `k` only (no
  randomness, no order dependence), at most 200 used ids are skipped (`used-first201#2` gives one id where two were asked, LD24), a
  fractional count gives its ceiling (`#2.5` gives three ids), and NaN, 0 or less give none. Two different bases never collide unless
  `cyrb53` does (106 bits); the same base and `k` always give the same id, which is what keeps a re-plan's task ids stable.
- **LDC-10** (**Executed**: `candidates/*prereq-*`; `engine.js:737-751`) Last, for each `SCHEDULING` quest and each prerequisite that is a
  known quest not `COMPLETED`: every task carrying the quest gets the ids of every task carrying the prerequisite appended to `dependsOn`
  (once per prerequisite listed: a repeated prerequisite appends twice) and `minLagMin` at least 15; when there are no such tasks while the
  prerequisite still needs sessions (a count or minutes target above what is done), `blockedReason` `BLOCKED_BY_PREREQUISITE`. A task
  carrying both a quest and its prerequisite depends on itself (LD16). The result is `{tasks sorted by id (stable), passiveReminders,
  rejected, questStatus, quests}`.

### 11.8 Floating point

- **LDF-1** (**Executed**: family `float`, `exp`, 2,121 cases, and every `_urgBase`; `engine.js:906`, `993`, `1006`) `Math.exp` as the
  recording Chromium (153) computes it is **correctly rounded**: the double nearest the exact value, for each of the 2,103 finite recorded
  arguments (checked against 60-digit decimal arithmetic), across the arguments the engine's formulas give (urgency `-3x`, the spacing
  Gaussians, the movement decay) and 400 random ones. It is not fdlibm: Java's `StrictMath.exp` (fdlibm) differs in the last bit on
  about one value in ten, and node 22's JavaScript engine, which is fdlibm too, gives the same values as `StrictMath` (LD20). Native uses
  `PageMath.exp`: double-double evaluation (Dekker products, no fused multiply-add) rounded once, with a 60-digit fallback whenever the
  rounding cannot be decided, and for subnormal or near-overflow results.
- **LDF-2** (**Executed**: `float/pow/*`, 720 cases, `float/square/*`, 400) `Math.pow(0.5, x)` (the check-in decay, `engine.js:844`) is
  correctly rounded likewise, including the tie at `0.5^1075` (exactly half the smallest subnormal), which rounds to even, 0; `x ** 2` is
  `x * x` exactly. JavaScript's special values: `pow(x, NaN)` NaN, `pow(NaN, 0)` 1, `pow(1, ±Infinity)` NaN.

### 11.9 What was measured

- 10,556 cases in Chromium 153 (the file, about 19.5 MB of text, 1.1 MB gzipped); the native code on the JVM reproduces all of them
  exactly (`DemandGoldenTest`). Node 22 agrees on every case except 337 `float` values and the `_urgBase` of 176 `prep`, `scenario` and
  `fuzz` cases, in the last bit (LD20); the web test requires exactly that.
- `StrictMath` misses 343 of the 2,841 recorded `exp` and `pow` values, the JVM's `Math` 7; `PageMath` none.
- The ten scenarios at seed 1 in all 11 strict zones, started on Monday 2026-10-26 06:00 local (their deadlines and goal dates moved
  with the start); 400 seeded inputs (0 to 5 goals of every archetype and custom ones, levels, statuses, targets; 0 to 5 quests of every
  tier, daily and once, `onlyDays`, exclusivity, session minutes, minimum intensity, prerequisites, two-requirement quests, passive and
  paused ones; history, kept sessions and used ids; horizons of 1 to 28 days starting at any quarter hour in March, October, November
  and December 2026; every intensity).
- Source mutations of the native code, each of which makes the replay fail (section 8.3).

---

## 12. Placement: preparation, energy, feasibility, scoring and the construction (part 4, WP-LD4)

This part covers the front of `generateSchedule` up to its repair: `buildState` (`engine.js:850-867`) and the energy model (`804-848`, but
`recomputeBwc`, LDE-3), the spacing ledger (`868-869`), the recovery reservation (`874-886`), `prepareState` (`1163-1188`) with `occupy`
(`1075-1087`) and `bwcIndexAt` (`1160`), `taskEarliestLatest` (`909-926`), `checkHard` (`929-962`), `fairnessMult`, `energyFit` and
`scorePlacement` (`964-1015`), `q6` (`802`), `evalInBwc` and `evalAcross` (`1017-1047`), `commit` (`1050-1074`), `anchorsDependents`
(`1106-1109`), the construction phase of `generateSchedule` (`1205-1288`), and `buildRejection` (`1394-1431`), which the page calls after
the repair but which is a pure function of the state and is recorded here on the state the construction leaves. New prefixes: `LDR-n`
preparation and energy, `LDK-n` feasibility, `LDU-n` scoring, `LDW-n` the construction, `LDJ-n` rejections. **Executed** rules cite
families of `lifemax_modular_v73/tests/fixtures/ld-placement-golden.json.gz` (section 8.4); the native code in
`android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector` (`PlanState`, `EnergyModel`, `PlanPreparation`, `Feasibility`,
`PlacementScore`, `Placer`, `PlanConstruction`, `Rejections`) replays every case.

Out of this part, for WP-LD5 and WP-LD6: `removeInst`, `snapshot`, `restore`, `stateFingerprint`, `runLocalRepair`, `fillPass`, the
diagnostics of displaced sessions, `explainInstance`, `segments`, `capacityByDay`, `recoveryBlocks`, `assembleOutput`, `inputHash`,
`validateSchedule`, `rescheduleActivity` and the ledger. A committed session's `title`, `tag`, `kind`, `goalTitle`, `explanation`, `moves`
and `source` (a deep copy of its task, `taskSnapshot` `1110-1115`) are copies the output assembly reads; the recorder keeps the members
placement decides (the session's projection in `tools/gen_ld_placement_golden.js`), and LD5 records the rest with the output.

The stage boundary. The recorder runs the page's own `generateSchedule` with two calls put into its text (`tools/ld_engine_vm.js`
`STAGES`): after the first scores (`1245`) it copies the score table, and before `/* PHASE 2` (`1289`) it records the whole state and
throws, so nothing after the construction runs. With no stage function the calls do nothing; the web test checks that the hooked engine
plans the ten scenarios exactly as the page's.

### 12.1 Preparation and energy

- **LDR-1** (**Executed**: family `prepare`, 178 cases, every `place` case; `engine.js:850-867`) `buildState(input, derived)`: the clock
  `now = currentTimeEpoch`; the first plannable slot `nowSlot = clamp(slotCeil(now + leadMinutes * 60), 0, n)` (LDT-17; `leadMinutes`
  as stored, LDP-1, not checked: `0`, `5`, `60`, `600` and `-30` are recorded, `prepare/prepare/lead-*`); the sleep need
  `(playerAvailability || {}).sleepRequirementMinutes || 480`, kept as given (text `"540"` divides as 540); the check-in (LDR-5); every
  waking day's sessions, per-key counts (an object keyed by the key's text), goal minutes and high count emptied; then each completion
  history item with a truthy `spacingKey` adds its `startEpoch` to the spacing ledger under that key (a map keyed by the value itself, in
  the order keys are first seen: history, then kept sessions, then placements); then LDR-2.
- **LDR-2** (**Executed**: family `prepare` (`baseP`, `baseC`, `sleepFactors`), `dst`, every `place` case; `engine.js:804-819`) For each
  waking day (LDA-13) its sleep factor is `clamp(sleepBeforeMin / need, 0.5, 1.05)` and for each slot `i` of the day, with `tau = (epochOf(i)
  - wakeEpoch) / 3600` hours since waking, `baseP[i] = baselineP(tau) * factor` and `baseC[i] = baselineC(tau) * factor`, **each stored
  in a `Float32Array`** (the product is a double, rounded once to single precision on the store; multiplying in single precision differs,
  mutation `base multiplied in single precision`). `baselineP(t)`: `0.55 + 0.25 t` below 1, 0.90 below 5.5, 0.80 below 7.5, 0.90 below
  11.5, `0.80 - 0.5 * (t - 11.5) / 5` up to and including 16.5, else 0.30; `baselineC(t)`: `0.50 + 0.35 t`, 0.95, 0.70, 0.80, `0.75 -
  0.45 * (t - 11.5) / 5`, 0.30 on the same bounds; the operations in that order. Then the day's drain (LDE-3). Slots outside every waking
  day keep base 0. A sleep need of 0 is 480 (`||`); `1e9` gives the floor 0.5 (`prepare/prepare/sleep-need-*`).
- **LDR-3** (**Executed**: `prepare` (`existing-*`, `locked-*`, `started-*`, the re-plan seeds), `rules/place/replan-*`, every `fuzz` re-plan;
  `engine.js:1163-1188`, `1075-1087`) `prepareState`: availability (LDA-10) for the input's own members, LDR-1, then the stored sessions
  (`existingSchedule || []`) sorted by `startEpoch` ascending, ties (and a NaN difference) by `instanceId` with LDG-2, and taken in order:
  a session whose `lifecycleState` is not `PLANNED` or `IN_PROGRESS` is history; one wholly outside the plan (`endEpoch <= H0` or
  `startEpoch >= HN`) is history when it ended by the clock, else dropped; one that is pinned (`isUserLocked` truthy) or has started
  (`startEpoch <= now`) is checked for a clash: the first slot from `max(its first slot, nowSlot)` up to its last that is not FREE (the
  slots before `nowSlot` are never checked, so a started session may overlap sleep or a commitment there); a clash with a session that
  has not ended displaces it (with the clashing code); otherwise, when the slot of `max(startEpoch, now)` lies in a waking day, it is
  **kept**: a copy with that day's id and `kept: true` is placed as LOCKED (`occupy`); with no waking day there it is history. Any other
  planned session is replannable, by `instanceId`. `occupy(session, day, code)`: its slots `clamp(slotOf(start), 0, n)` up to
  `clamp(slotCeil(end), 0, n)` take the code, the session's index (`taskOwner`) and its `IP` and `IC` as a `Uint8Array` stores them
  (ToUint8: `"5"` is 5, `300` is 44, missing 0; `prepare/prepare/locked-intensity-text`); the session joins the state's list (and its id
  map) and the day's list; the day's count for its spacing key's text rises by one, its goal minutes by 15 per slot (every session counts,
  goal or quest), its high count when `IP >= 4 || IC >= 4` (LDG-6); its start joins the spacing ledger; each id in `linkedGoalIds` gains
  the minutes in the fairness ledger, and the ledger's total gains them once when the list is not empty; then the day's drain (LDE-3).
  A stored session whose `linkedGoalIds` is missing stops the plan with a `TypeError` (`prepare/prepare/locked-no-links`,
  `rules/place/throw-no-links`; LD27). Then LDR-4.
- **LDR-4** (**Executed**: `prepare` (`recovery-*`, `lead-*`, `now-*`), every `place` case; `engine.js:874-886`) Recovery is reserved
  before anything is placed: for each waking day, the FREE slots from `max(startSlot, nowSlot)` are counted (`freeSlots`); `left =
  min(ceil(minDailyRecoveryMinutes / 15), free)` (`recoverySlots`); the day is short (`recoveryShortfall`) when it has fewer free slots than
  needed, ends after `nowSlot` and did not start before the plan; then from the day's last slot backwards, down to the first counted one,
  FREE slots become RECOVERY until `left` is spent (the latest free time of the day is the protected downtime); then its drain.
- **LDR-5** (**Executed**: `prepare` (`checkin-*`, `energyP`, `energyC` at every slot), `rules/place/checkin-*`, `fuzz`; `engine.js:837-848`,
  `861-862`) The check-in counts when `energyProfile` is truthy, its `checkin` truthy, `checkin.epoch` truthy and `checkin.level` names one of
  `LOW`, `NORMAL`, `HIGH` (values 0.30, 0.65, 0.95); an epoch of 0 or a level `TIRED` leave none. `energyAt(i, axis)`: `e = base[i] -
  drain[i]` (both single-precision values read as doubles, subtracted as doubles), `clamp(e, 0, 1)`; then, when `epochOf(i) >= epoch`
  (LDG-6: a text epoch compares as a number, `prepare/prepare/checkin-text-epoch`), `a = Math.pow(0.5, (epochOf(i) - epoch) / 21600)`
  (LDF-2) and `e = a * value + (1 - a) * e`. Before the check-in, or with none, the cut value.
- **LDR-6** (**Executed**: family `prepare`) What the preparation leaves, as the golden file records it: the slot codes, owners, the two
  intensity bytes, `baseP`, `baseC`, `drainP`, `drainC` (single precision), `runLen` and `runBack` (16-bit) at every slot; each day's counts,
  goal minutes, high count, free and recovery slots and shortfall; the sessions kept; the spacing and fairness ledgers; history, kept,
  displaced and replannable ids in the order found.

### 12.2 Feasibility

- **LDK-1** (**Executed**: every probe of every `place` case (`el`), `rules/place/quest-chain*`, `prior-volume`, `lead-60`; `engine.js:909-926`)
  `taskEarliestLatest(t)`: `earliest = max(windowStart, now)`, `latest = min(windowEnd, deadlineEpoch || Infinity, HN)`. For each task id
  `d` it depends on (LDC-10), the session `'i' + d.slice(1)` must be on the plan, else the window is blocked (`PREREQ`, 12); `earliest =
  max(earliest, its end + (minLagMin || 0) * 60)`. With a prior volume (LDC-3): the ends of the plan's sessions of those templates that
  start in `[windowStart, windowEnd)`, then the **starts** of the input's completion history items of those templates that start in
  `[windowStart - 7 days, windowEnd)` (LD26), sorted ascending; fewer than `count` blocks the window (`PRIOR_VOLUME`, 14), else `earliest =
  max(earliest, the count-th)`. The window in slots: `earliestSlot = max(nowSlot, slotCeil(earliest))`, `latestSlot = min(n,
  slotOf(latest))` (the task may use slots up to, not including, `latestSlot`).
- **LDK-2** (**Executed**: the reason tallies of every probe, where each refusal is counted: BUSY 1,423,006 times, CAP 497,575, PER_DAY
  151,402, SPACING 85,378, HIGH_COUNT 15,664, FATIGUE 7,450, CONSECUTIVE 3,201, POST_SHIFT 2,745, BEFORE_SLEEP 160, DOWNSTREAM 15,
  and 12,384 days outside a window; `rules/place/high-cap-*`, `consecutive-30`, `daily-cap-30`, `heavy-shift`, `custom-*`; `engine.js:929-962`)
  `checkHard(t, day, s, n, window)`, in this order, the first failing test answering: `s < earliestSlot || s + n > latestSlot` WINDOW;
  `runLen[s] < n` BUSY; `day.goalMin + 15 n > maxDailyGoalMinutes` CAP (4); `(day.perKey[spacingKey] || 0) >= maxPerBwc` PER_DAY (5;
  LDG-6, so a stored `"2"` is 2); for a high task (LDE-2) `day.highCount >= maxHighIntensityPerDay` (stored, not checked: 0 refuses
  every high task) HIGH_COUNT (13); with a truthy `avoidBeforeSleepMin`, `epochOf(s + n) > sleepEpoch - avoidBeforeSleepMin * 60`
  BEFORE_SLEEP (7); with sessions of its key in the ledger and `spacingMinH > 0`, any of them with `|epochOf(s) - start| < spacingMinH *
  3600` SPACING (6; only starts are compared); `IP >= 4` with the day's heavy-shift marker (LDA-16) at or before `s` POST_SHIFT (9); for
  `IP >= 4 || IC >= 4`: for each slot `k` of the `n`, with `own = 0.015 * k / 4`, `energyAt(s + k, physical) - own * IP < 0.45` (when
  `IP >= 4`) or the same for the cognitive axis (when `IC >= 4`) FATIGUE (8); then the run of demanding slots (TASK or LOCKED with either
  stored intensity `>= 4`) touching `[s, s + n)` on both sides inside the day, plus `n`, times 15, over `maxConsecutiveHighLoadMinutes`
  CONSECUTIVE (10); finally, when the day has sessions, each of them that starts at or after `epochOf(s + n)` and is high (on its stored
  values): with `aP = 0.015 * IP * n / 4` and `aC` likewise, `energyAt(its first slot, physical) - aP < 0.45` (its `IP >= 4`) or the
  cognitive test (its `IC >= 4`) DOWNSTREAM (11). Else OK. Inside `evalInBwc` the WINDOW and BUSY tests never fail (the loop bounds and its
  own run test come first, LD30).

### 12.3 Scoring

- **LDU-1** (**Executed**: the cells of every `place` case (each active task's first raw best score in each waking day), every placement's
  score, every probe's `detail` and `across`; `engine.js:969-1015`) `scorePlacement(t, day, s, n)` with `st = epochOf(s)`:
  - urgency: `_urgBase` (LDE-2); with a truthy deadline, times `0.85 + 0.15 * (1 - clamp((st - now) / max(1, deadline - now), 0, 1))`;
  - energy: `min(energyFit(energyAt(s, physical), IP), energyFit(energyAt(s, cognitive), IC))`, `energyFit(a, I)`: `d = a - 0.18 I`;
    `1 - 0.30 d` when `d >= 0`, else `max(0, 1 - 2.8 d d)`;
  - preference: the wake phase `phaseOf((st - wakeEpoch) / 3600)` (WAKE below 1 h, PEAK below 5.5, DIP below 7.5, SECONDARY below 11.5,
    else WIND); 0.60, or 1.0 when the slot is in a PREFERRED window (LDA-17) or else when `phases.includes(phase)` (a list's item; a
    text's part, `"SECONDARY"`; another value throws `TypeError`, `rules/place/custom-phases-object`; `ANY` matches no phase); then 0.15
    for each avoid window (stored, LDG-6) with `startMin <= localMinute < endMin`, the minute being the slot's wall clock (LDT-16); on a
    preferred rest day (`(preferredRestDays || []).includes(the slot's day's weekday)`, a list holding the number, or a text holding its
    digit: `["1"]` holds nothing, `rules/place/rest-day-text`) a high task gets 0.15, and a task that prefers rest days gets at least 1.0;
  - spacing: 1 with no session of its key in the ledger; else `near` the hours to the nearest one, `h = spacingIdealH`, `sig = max(4, h *
    0.5)`, `d = near - h`: `near >= h` gives `max(0.6, exp(-(d * d) / (2 * ((2 sig) * (2 sig)))))`, else `exp(-(d * d) / ((2 sig) * sig))`
    (LDF-1; `x ** 2` is `x * x`, LDF-2);
  - variety: 0.5 when the slot before `s` or the slot `s + n` (inside the grid) is a TASK (not LOCKED) whose leading axis (`IP > IC` of its
    stored bytes, else cognitive) differs from the task's (`IP > IC`, LDG-6), else 0;
  - `wsum = 24 urgency + 18 _prio + 18 energy + 12 preference + 12 spacing + 10 _lag + 6 variety`, added left to right (an undefined
    `_lag` makes it NaN);
  - penalties: fragmentation `f(runBack[s] - 1) + f(runLen[s] - n)` with `f(1) = 3`, `f(2) = 1.5`, else 0; movement, when `origStartEpoch`
    is truthy and not `st`: with `hrs = (origStartEpoch - now) / 3600`, 25 below 24 hours, else `clamp(5 + 15 * exp(-(hrs - 24) / 72), 5,
    20)`; compression `(dOptSlots - n) * 8`;
  - `U = q6(F * (wsum - fragmentation - movement - compression))`, `F` LDU-2. The detail form adds `F`, `wsum`, the seven weighted
    factors, the penalties, the phase, both energies and the rest-day flag.
- **LDU-2** (**Executed**: every placement's `F`, `rules/place/fair-cap*`, `twin-*`; `engine.js:964-968`, `802`) `fairnessMult(t)` is 1 in
  raw mode (the construction's table, LDW-2), for a task without a goal, or while the ledger's total is 0; else `clamp(1 + 1.5 * ((share[goal]
  || 0) - (goalMin[goal] || 0) / total), 0.75, 1.25)`. `q6(x) = Math.round(x * 1e6) / 1e6` (LDN-1's round, ties up). A negative utility
  times `F > 1` is more negative, so fairness pushes an under-served goal's poor placements further down (LD31; no recorded placement has a
  negative score with `F != 1`).

### 12.4 The construction

- **LDW-1** (**Executed**: every `place` case; `engine.js:1017-1047`) `evalInBwc(t, day, window)`: a day ending at or before
  `earliestSlot` or starting at or after `latestSlot` has nothing (one WINDOW in a tally); else for `n` from `dOptSlots` down to
  `dMinSlots`, for `s` from `max(startSlot, earliestSlot)` to `min(endSlot, latestSlot) - n` inclusive: `runLen[s] < n` skips (one BUSY
  in a tally, at the shortest length only); LDK-2's refusal skips (tallied); else the score (LDU-1), and a strictly higher one replaces
  the best: the longest length wins over a shorter one at an equal score, then the earliest start. `evalAcross(t)`: the blocked window's
  reason, or each day in order, counting the days that fit and keeping the first strictly highest; `second` the highest score of the other
  fitting days (0 when none); none fits is BUSY.
- **LDW-2** (**Executed**: `cells` and `placements` of every `place` case; `engine.js:1205-1288`) The construction. The tasks are the demand
  (LDE-4) compiled with the preparation's kept sessions plus its history sessions skipped voluntarily at tier 2 or less (`tier <= 2`, LDG-6)
  and, as used ids, the `instanceId` of every history and kept session (LDW-7); each task's `origStartEpoch` is the replannable session
  `'i' + id.slice(1)`'s `startEpoch` (LDR-3). The shares (LDE-4) go into the fairness ledger. The active tasks are those without a
  `blockedReason`. A table holds, for each active task and waking day, the raw best (LDW-1 with fairness off): score, slot, length; a task
  with no prerequisites and no prior volume keeps the window it had at the start, the others re-ask LDK-1 each time. Then for tier 4, 3,
  2, 1, first the tasks without a daily cover and then those with one (LDC-8), up to 2,000 rounds each: every unplaced active task of that
  tier and kind (a task with prerequisites or prior volume re-scored first) is a candidate when some day fits: its best day (the first
  strictly highest raw score), the second-highest raw score (an equal score counts), `F` (LDU-2), `bestF = q6(best * F)`, `secondF =
  q6(second * F)` or 0 when only one day fits, `regret = bestF - secondF`, `scarcity = 15 / max(1, days that fit)`, `key = q6(bestF + 0.35
  * regret + scarcity)`. The candidate with the highest key wins (LDW-3), unless held back (LDW-4); it is committed (LDW-6) at its best
  day's slot and length, recorded with its selection (`k`, the day, slot, length, `bestF`, regret, count, key, `F`), a weekly-minimum
  goal session lowers its goal's count of minimum sessions left, and every unplaced active task is re-scored: in every day when it shares
  the winner's spacing key, else in the winner's day only. A round with no candidate ends the kind.
- **LDW-3** (**Executed**: `rules/place/twin-goals`, `twin-custom`, every `place` case; mutations `equal keys to the larger id`, `later day
  wins a tie`, `later slot wins a tie`; `engine.js:1274-1275`, `1028`, `1042`) Ties and determinism. An equal key goes to the smaller task
  id in UTF-16 order: task ids are `cyrb53` hashes of template, week and index (LDH-2, LDC-9), so which of two equally good sessions is
  placed first is decided by a hash (LD32). Within a day the first highest start wins, across days the first highest day, in the cells the
  first highest day. Every list is built in a fixed order (tasks by id, LDC-2; days in time order; sessions in commit order); no object's
  key order is observed by placement (`medLeft` is only searched, `perKey` and the ledgers only read by key; LDJ-1 sorts its reason codes
  numerically). Sorts: the stored sessions by start then id (a stable sort, LDR-3). The recorder runs every case twice on fresh state and
  requires the same answer.
- **LDW-4** (**Executed**: `capRelaxed` placements (24), `rules/place/fair-cap*`; `engine.js:1246-1253`, `1274`,
  `1277`) The share cap: a candidate of a goal is held back when the plan's tasks name two goals or more, the fairness total is at least 180
  minutes, another goal still has weekly-minimum sessions left, and `(goalMin[goal] + 15 n) / (total + 15 n) > max(0.60, share[goal] +
  0.20)`. Held candidates compete among themselves by the same rule; when no candidate is free, the best held one is placed and marked
  `capRelaxed`.
- **LDW-5** (**Executed**: family `anchors`, 15 cases, and `anchors` of every `place` case; `engine.js:1106-1109`) `anchorsDependents(session)`:
  some task is placed, not covered (`coveredBy` falsy), and its `dependsOn` (when truthy) includes the session's `taskId` (a list by item; a
  text by part, `anchors/anchors/text-list`). Used by the repair (LD5) to keep such a session in place.
- **LDW-6** (**Executed**: `rules/place/quest-daily-*`, the `coveredBy` of 899 tasks; mutations `coverage ignores
  exclusivity`, `coverage lets check-ins cover`; `engine.js:1050-1074`) `commit(t, day, s, n)`: a session `'i' + t.id.slice(1)` of `[epochOf(s),
  epochOf(s + n))`, its length, `minDurationSlots = dMinSlots`, `optDurationSlots = dOptSlots`, the task's tier, `PLANNED`, not pinned,
  the day's id, the task's `IP`, `IC`, key, goal links and quest links (copies), `compressed = n < dOptSlots`, `origStartEpoch || null`;
  placed with `occupy` as TASK (LDR-3), and the task is placed. Then, when the session is linked to a goal, each other task in id order with a
  daily cover, not placed, whose cover's day `[start, end)` holds the session's start, whose tag fits (an exclusive quest's cover only its
  exact tag; else LDD-1 and a check-in tag, `.assessment` or `.checkpoint`, only exactly) and that requirement not already on the session:
  that task is placed, `coveredBy` the session, and the session takes a copy of the link.
- **LDW-7** (**Executed**: every `place` case with existing sessions; `engine.js:1209-1228`) The demand half runs on the prepared state:
  the weeks of its days, the goal plans at `now`, the history filter, the candidates with the kept and used ids of LDW-2, the goal info,
  `prepTask` on every task, the shares (LDE-4 exactly).

### 12.5 Rejections

- **LDJ-1** (**Executed**: `rejections` of every `place` case, every reason code but DOWNSTREAM_FATIGUE (8,195: daily limit 2,706, recovery
  1,880, one per day 904, no free window 885, prerequisite 609, deadline 363, spacing 322, high-intensity cap 232, fatigue 102,
  consecutive 69, blocked 53, earlier sessions 38, post-shift 28, before sleep 4); `engine.js:1394-1431`) `buildRejection(t)` for an unplaced task, in
  this order: a `blockedReason` gives it with `"<title>" waits on a prerequisite quest.`; a blocked window gives
  `PREREQUISITE_NOT_SCHEDULED` or `NEEDS_EARLIER_SESSIONS` with `"<title>" could not be placed because <reason words>.`; a window shorter
  than `dMinSlots` gives `DEADLINE_TOO_CLOSE`, `"<title>" has no time left in its window` and ` before the quest deadline` (a truthy
  deadline) or ` this week`, and `.`; otherwise each waking day the window touches is tried with a tally (LDW-1) and given one code: the
  most counted of CAP, SPACING, FATIGUE, POST_SHIFT, PER_DAY, HIGH_COUNT, BEFORE_SLEEP, CONSECUTIVE, DOWNSTREAM, PRIOR_VOLUME (the first in
  that list on a tie), or with none counted RECOVERY when a run of FREE or RECOVERY slots of `dMinSlots` lies in the day's part of the
  window, else BUSY. The days per code (`perDay`, keyed by code) are ranked by count, then code ascending; the first names the reason
  (`NO_FREE_WINDOW`, `DAILY_EFFORT_LIMIT`, `ONE_PER_DAY_RULE`, `SPACING_RULE`, `TOO_CLOSE_TO_SLEEP`, `ESTIMATED_FATIGUE`,
  `POST_SHIFT_PROTECTION`, `CONSECUTIVE_LOAD_CAP`, `DOWNSTREAM_FATIGUE`, `HIGH_INTENSITY_DAILY_CAP`, `RECOVERY_PROTECTED`; BUSY when no
  day was tried), and the sentence is `"<title>"` + ` (quest: <quest titles, ", ">)` when linked + ` could not be scheduled — ` + `<n>
  day(s): <words>; <n> day(s): <words>` for the top two when two or more codes, else the words, + `.` + the hint of the code (CAP ` Raise
  “Max daily effort” in Settings, or choose a lighter intensity.`, SPACING ` Sessions are kept at least <spacingMinH>h apart.`, FATIGUE
  ` Energy estimates are heuristics; an energy check-in can override them.`, POST_SHIFT ` Heavy lifting is not scheduled after a long
  physical shift.`, RECOVERY ` Life Director keeps <minDailyRecoveryMinutes> minutes of downtime per day and will not trade it for
  tasks.`). The diagnosis: `{taskId, title, tier, reasonCode, humanExplanation, linkedQuestIds, goalId}` plus `perDay` for the last kind.
  The words are `R_TEXT` (`engine.js:794-801`). Explanation strings are recorded as part of the contract (plan WP-LD4's decision,
  recommended yes): they are shown in the sheets.

### 12.6 Floating point and speed

- Every score, energy and key is computed in IEEE doubles in the page's operation order; `baseP`, `baseC`, `drainP` and `drainC` are single
  precision on store (LDR-2, LDE-3) and read back as doubles; the bytes and 16-bit arrays convert as JavaScript's typed arrays do (LDR-3).
  `Math.exp` (spacing, movement, urgency) and `Math.pow` (the check-in) are the page's correctly rounded values (LDF-1, LDF-2; native
  `PageMath`). A placement compares scores exactly, and `q6` rounds them to millionths, so one bit in an exp can change which slot wins:
  node 22 (fdlibm) records a different answer from Chromium for some prepare and place cases (counted in the web test), which makes LD20
  a placement question, not only an urgency one (OQ-12).
- Speed (`PlacementPerfTest`, a four-week plan with eight advanced goals at the hardcore intensity and five quests): see section 12.7.

### 12.7 What was measured

- 2,998 cases in Chromium 153 (the file, about 47.7 MB of text, 5.2 MB gzipped); the native code on the JVM reproduces all of them
  exactly (`PlacementGoldenTest`, about 20 seconds for the whole file). Every prepare and place case gave the same answer twice in the
  recorder. Node 22 (fdlibm) records a different answer for 136 of them (prepare and place cases with a check-in or with spacing and
  movement exponentials one bit apart), so a bit of exp or pow moves sessions (OQ-15).
- The seeded inputs: any strict zone; one to fourteen days starting at any quarter hour of January, March, April, June, September to
  December 2026 or 2027; the clock at the start or up to most of the plan later, sometimes off the quarter hour; sleep rules at eight
  bedtimes and five lengths or none (the default bedtime), naps; work at four day patterns, seven start times and six lengths with
  commutes, every load class and none, decompression; an 8-day rotation of heavy shifts; classes; unavailable and preferred windows;
  validity dates; one-off commitments of every kind; sick and blackout days; up to six goals of every archetype, level, status and target,
  and custom ones; up to five quests of every tier, daily and once, `onlyDays`, minutes, exclusivity, session lengths and prerequisite
  chains; completion history with and without spacing keys; a check-in at any level, early or late; every intensity, and the daily,
  recovery, consecutive and high-intensity limits, rest days, avoid windows, lead minutes, meals off, a routine of 0 or 60 minutes; and
  for a third of them (and every `prepare` seed) the engine's own plan as the existing schedule of a later clock, its sessions in
  every lifecycle state, some pinned and some moved.
- Speed. `PlanConstruction.construct` on `PlacementPerfTest`'s four-week plan (eight advanced goals at the hardcore intensity, five
  quests, a check-in; 166 sessions placed) takes 107 ms on the JVM of the test machine (median of five, after two warm-up runs; the
  page's own construction on the same input takes about 1,700 ms in node on the same machine). A phone will be several times slower.
  The first version took 1.7 s (on the machine under load): a profile showed about 70% of the time in `PageMath.pow`, the check-in's pull worked out on every energy
  read, and 20% in `PageMath.exp`. Both are now kept, exactly: the pull per slot (it depends on the slot alone) and each exponential per
  argument (`PlanState.exp`, a map from the argument's double to `PageMath.exp`'s result, so every value is the one `PageMath` gives;
  the replay proves no case changed). What remains is the search itself (`evalInBwc` over every start and length, re-run for every
  task after each placement), map lookups of the spacing ledger and the preferences, and `PageMath` at about 5%.
- Source mutations of the native code, each of which makes the replay fail (section 8.4).

---

## 13. Repair, output and validation (part 5, WP-LD5)

Part 5 is everything `generateSchedule` does after the construction (section 12): the bounded local repair with its snapshots and
roll-backs, the fill pass, the rejections, the explanations, the segments, capacity and recovery blocks, the goal allocation and quest
coverage, the output object and its `inputHash`; and `validateSchedule`, which the output embeds and which the page also calls on its own.
`rescheduleActivity` and its hold placement (`prepareStateWithHold`, `placeByRadius`, the change record) are part 6 (section 14, plan
WP-LD5's original scope; section 1.2). The stored Life Director state's `validate` (`engine.js:2259-2265`, the store module) is the store
codec's (WP-LD7). Native: `PlanTransaction` (`PlanSnapshot`, `PlanRemoval`), `LocalRepair`, `Explanations` (and `PageText`), `PlanSegments`,
`ScheduleJson` (the page objects), `ScheduleValidation` (and `PageKeys`), `InputHash`, `PlanSchedule` (the whole plan), in `core/lifedirector`.
IDs: `LDX-n` the repair and the fill pass, `LDO-n` the output, `LDV-n` validation. Every rule is **Executed** by the families of section
8.5 unless marked **Read**. The output is the stored schedule (`ST.schedule`), so every member is part of the contract except
`computeTimeMs` (a measured time, plan 4.2(7)); explanation strings are recorded and compared (OQ-16).

### 13.1 Removing a session and transactions

- **LDX-1** (`engine.js:1088-1105`; every repair, mutations `remove keeps the spacing start`, `remove keeps covered tasks`, `remove keeps the
  key count`) `removeInst(S, inst)`: the slots from `clamp(slotOf(start), 0, n)` to `clamp(slotCeil(end), 0, n)` become FREE with owner -1
  and intensities 0 (a recovery slot under the session is not restored: the session never sat on one). The session leaves `S.instances`
  by identity (`splice(indexOf, 1)`: a session not in the list would take the last one with it; never happens, the repair only removes
  listed sessions), and the index by id. The first waking day with the session's day id (`bwcId`) loses it from its list, one of its
  spacing key's count (an absent key would become NaN), `(b - a) * 15` goal minutes, one high count for a session with `IP >= 4 || IC >=
  4`, and its drain is worked out again (LDE-3). The spacing ledger loses the first start equal to the session's (strict equality). Each
  linked goal loses the minutes in the fairness ledger, and the total too when the session has any goal link. The task with the session's
  `taskId` is no longer placed; every task covered by the session (`coveredBy === instanceId`) is uncovered and no longer placed.
- **LDX-2** (`engine.js:1119-1139`; family `repair`, every rollback (44,297 recorded); mutations `restore keeps drains`, `... free runs`,
  `... intensities`, `... fairness minutes`, `... spacing`, `... covers`) `snapshot(S)` copies: the slot codes, owners, task intensities,
  drains (single precision) and free runs (16-bit); each waking day's session list (the session objects themselves, not copies), key
  counts, goal minutes and high count; the session list; the fairness ledger (`deepClone`: the shares too); the spacing ledger (each key's
  list copied, the keys in their order); every task's `isPlaced` and `coveredBy`; and each session's number of quest links.
  `restore(S, z)` puts all of it back, rebuilds the index by id from the session list (the last of a repeated id), and cuts each session's
  quest links back to the number it had (links added by a trial's covers go; none is ever given back). Not kept, and so not rolled back:
  the iteration count (LDX-3), a task's `displacedBy` (set only by a move that succeeds), the days' recovery counts and the base energies
  (the repair changes neither). A kept session's link count is read from its stored `linkedQuestRequirementIds` (`.length`): a stored
  session without that list makes every snapshot, and so every repair attempt, throw a `TypeError` (LD45). `stateFingerprint`
  (`engine.js:1141-1147`) is used only by the page's tests and is not ported: the recorder's own STATE (format in
  `tools/gen_ld_schedule_golden.js`) is recorded at every repair target instead.

### 13.2 The repair

- **LDX-3** (`engine.js:1289-1292`, `1309-1314`, `1375`; family `repair` (1,549 targets recorded), 34 plans that spend the budget; mutations
  `budget 399`, `budget checked after it grows (A)`, `(B)`, `targets lowest tier first`, `targets by id descending`) After the
  construction, the active tasks not placed with tier 3 or more are the targets, sorted by tier descending, then id ascending (`cmpStr`,
  UTF-16 order). The budget is 400 moves (`maxIter`), counted in `S.iterations`, which nothing resets or rolls back. For each target in
  turn: skipped when placed (a cover or an earlier move may have placed it) or when `S.iterations >= 400`; skipped when its window is
  blocked (LDK-1); then move A (LDX-4) and, if still unplaced, move B (LDX-5). Every waking day move A looks at costs one move, **before**
  its window test, so a target spends a move on every day of the plan even where it cannot go (LD43), and the count is incremented before
  it is compared (`++S.iterations > maxIter`), so a run stops at 401 (the most recorded is 402: move A's last day and move B's first
  increment each once more). The log: attempts, successes, rollbacks, `changed`, and the moves `{type: 'COMPRESSION', taskId, bwcId}`,
  `{type: 'SWAP', taskId, relocated: <instanceId>}`, `{type: 'EVICT', taskId, shed: <instanceId>}`.
- **LDX-4** (`engine.js:1316-1346`; 660 recorded; mutations `shrink latest first`, `freed must exceed`, `only lower tiers movable`,
  `shrunk session always re-placed by score`) Move A, elastic compression, for each waking day in order: break when the target is placed
  or the budget is spent (LDX-3); skip a day ending at or before the window's earliest slot or starting at or after its latest. The
  shrinkable sessions are the day's sessions (in its list's order) that are not pinned, not kept, start after `now`, are longer than their
  `minDurationSlots`, have a tier at most the target's, whose task is on the plan and that anchor no dependent (LDW-5); sorted by tier, then
  start (stable). When their spare slots (`durationSlots - minDurationSlots`, summed) are fewer than the target's `dMinSlots` the day is
  skipped (no attempt). Otherwise one attempt: snapshot (LDX-2); each shrinkable session in order is removed (LDX-1) and its task's window
  recomputed (a blocked window fails the move); if LDK-2 allows its task at its old start with `dMinSlots` it is committed there, else at
  its best place in that day (LDW-1; none fails the move), keeping its instance id (`commit` with `{instanceId, explanation: null}`). If no
  session failed, the target's best place in that day alone (`evalAcross(t, [day])`) is committed and recorded `{phase: 2, move:
  'COMPRESSION', score, count}`: a success, and the next target. Any failure, or no place for the target, restores the snapshot and counts
  a rollback. A shrunk session keeps its construction record (phase 1, its old selection numbers) for its explanation.
- **LDX-5** (`engine.js:1347-1374`; 184 swaps, 13 evictions recorded; mutations `evict tier 1 only`, `no relocation in a swap`, `incumbents
  latest first`) Move B, one for one: the incumbents are the plan's sessions (in list order) that are not pinned, not kept, start after
  `now`, have a tier at most the target's, a task on the plan and no anchored dependent; sorted by tier, start, then instance id. For each:
  one move (`++S.iterations > maxIter` breaks), one attempt, a snapshot; the incumbent is removed and the target's best place over the
  whole plan found (LDW-1). If it fits it is committed; then the incumbent's task's best place: if that fits, it is committed there with the
  incumbent's instance id, the target recorded `SWAP` and the incumbent `RELOCATED` (each with its own score and count), a success. If it
  does not fit and the incumbent's tier is at most 2 while the target's is at least 3, the incumbent's task is marked `displacedBy` the
  target's id and stays off the plan, the target recorded `EVICT`, a success. Anything else restores the snapshot and counts a rollback.
  The list of incumbents is a copy made before the loop (the page's own comment), so removals do not disturb it, but it is not rebuilt
  after a rollback either.

### 13.3 The fill pass

- **LDX-6** (`engine.js:1292`, `1377-1392`; 75 `FILL` placements recorded; mutations `fill regret 0.30`, `fill takes displaced tasks`, `fill
  tie to the larger id`, `fill after every repair`) Only when the repair changed something (`log.changed`): for tier 4, 3, 2, 1, up to 200
  rounds each: of the active tasks (in order) of that tier that are not placed and not `displacedBy`, each one's best place over the plan
  (LDW-1, with fairness: `rawMode` is off) gives `key = q6(score + 0.35 * (score - second) + 15 / max(1, count))`; the first highest key,
  or on an equal key the smaller task id, is committed and recorded `{phase: 2, move: 'FILL', score, count}`; a round with no fitting task
  ends the tier. Unlike the construction, the fill pass has no share cap (LDW-4) and no cover pass (LDW-2): plain and coverable tasks of a
  tier compete together (LD42).

### 13.4 Rejections and the order of the diagnostics

- **LDO-1** (`engine.js:1293-1298`, `786-789`, `1394-1395`; every `generate` case (11,315 diagnoses); mutations `displaced listed first`,
  `displaced rejection ignored`) `unscheduledDiagnostics` in this order: the demand's own rejections (quests that make no sessions, LDC-4:
  `{taskId: questId, title: null, tier: null, reasonCode, humanExplanation, linkedQuestIds: [], goalId: null}`); then, for every task in the
  tasks' order (blocked ones included) that is not placed, `buildRejection` on the final state (LDJ-1), with one rule ahead of LDJ-1's: a
  task marked `displacedBy` (LDX-5) gives `DISPLACED_BY_HIGHER_PRIORITY`, `"<title>" made room for higher-priority quest or minimum-dose
  work this week.`; then each displaced stored session of the preparation (LDR-3) as `{taskId: <instanceId>, title: null, tier: null,
  reasonCode: 'EXCUSED_DISPLACED', humanExplanation: '"<stored title>" was locked but now conflicts with a commitment, so it was excused
  without penalty.', linkedQuestIds: [], goalId: null, instanceId}` (the stored title as `String()` gives it: a missing one reads
  `undefined`).

### 13.5 Explanations

- **LDO-2** (`engine.js:1299-1300`, `1432-1466`; 25,191 scored and 880 locked explanations; cases `rules/generate/explain-*`, `kept-*`;
  mutations `explanation: top 3 factors`, `... scarce up to 3 days`, `... factors to whole points`, `... replacement taken literally`,
  `... started read as pinned`) The planned sessions are sorted by start, then instance id (`cmpStr`); each without a truthy `explanation`
  gets `explainInstance(S, inst, record)`, `record` being the placement record of its instance id (LDW-2's selection with `phase: 1`, or a
  repair or fill record; none for a kept session). A kept session keeps a truthy stored explanation as it is. Otherwise:
  - a kept or pinned session: `{tier: <its stored tier>, summary, factors: [], locked: true}`, the summary `You pinned this time, so Life
    Director never moves it automatically.` when its stored `isUserLocked` is truthy, else `Already started; kept in place.`;
  - a committed session: when its task's urgency base is not a number (`!_urgBase && _urgBase !== 0`: NaN, from a deadline that is not a
    number) the task is prepared again with no goal information first (LDE-2; its lag becomes 0.5, which this and every later score of the
    task reads; LD39). Then `scorePlacement(S, t, its day, slotOf(start), durationSlots, true)` on the final state, the session itself in
    it (LDU-1). The factors, in LDU-1's order (urgency, priority, energy, preference, spacing, lag, variety), each `Math.round(v * 10) / 10`,
    sorted by points descending (a stable sort; equal points keep that order) and the first four kept. The summary is these sentences
    joined by one space:
    1. `Scheduled because <why>.` where `why` is the tier reason's words (`CRITICAL_DEADLINE` its quest deadline is less than a day away,
       `ACTIVE_QUEST` it advances an active quest, `SIDE_QUEST` it is an optional side quest placed in spare capacity, `WEEKLY_MINIMUM` it
       keeps the goal at its weekly minimum, `WEEKLY_TARGET` it moves the goal toward its weekly target, `STRETCH_VOLUME` it is optional
       extra volume your intensity setting allows, `RESIDUAL` it finishes the rest of a partially completed session, `REPLACEMENT` it
       replaces a session that was missed or displaced; any other: it serves your goals); for `ACTIVE_QUEST` with quest links: `it advances
       “<t1>” and “<t2>”`; for a reason starting `WEEKLY` with a truthy `goalTitle`: the first `the goal` replaced by `“<goal title>”` with
       JavaScript's replacement patterns (`$$`, `$&`, `` $` ``, `$'` are interpreted, `$1` is not: LD38, `rules/generate/explain-dollar-title`);
    2. `This time falls in <phase words> (estimated energy: body <round(eP*100)>%, mind <round(eC*100)>%).` (WAKE just after waking, PEAK
       your primary energy peak, DIP your mid-cycle dip, SECONDARY your secondary peak, WIND your wind-down hours);
    3. when the record's `count` is 1 or 2: `Only <n> day(s) could fit it, so it was placed before more flexible tasks.`;
    4. when compressed: `Shortened to <durationSlots*15> min (from <optDurationSlots*15>) to fit the available time.`;
    5. when the spacing ledger holds more than one start for its key and the task's `spacingMinH >= 24`: `Kept at least <spacingMinH>h from
       similar sessions for recovery.`;
    6. when it has quest links and goal links and the reason is not `ACTIVE_QUEST`: `Also counts toward “<t1>”, “<t2>” — one session, no
       duplicate scheduling.`;
    7. for a `SWAP` record `Placed by local repair after swapping with a more flexible task.`; for `COMPRESSION` `Placed by shortening
       flexible tasks that day.`;
    8. on a preferred rest day for a high-intensity task: `It lands on a preferred rest day because no better slot existed.`
    The object: `{tier, tierReason, summary, factors: [{factor, points}...], fairness: round(F*100)/100, penalties: {fragmentation,
    movement, compression}, selection, energy: {physical: round(eP*100)/100, cognitive: round(eC*100)/100}, wakePhase}`; `selection` is null
    without a record, else `{regret: round((regret || 0)*10)/10, feasibleDays: count || null, key: key || null, phase, move: move || null}`
    (a repair record has no regret or key: 0 and null). Quest titles are the session's links' `questTitle` (covers included).

### 13.6 Segments, capacity, recovery blocks

- **LDO-3** (`engine.js:1468-1482`; every `generate` case (136,716 segments); `rules/generate/segments-*`; mutations `segments ignore a
  session end`, `segments drop the block reference`) `segments(S)`: from slot 0, a run is the slots with the first slot's code and painting
  block (`g.tag`); for a TASK or LOCKED slot, the owner is the first session in `S.instances` whose `[start, end)` holds the slot's start,
  and the run also ends where the owner ends. Each run: `{startEpoch, endEpoch, code: <SLOT_NAMES>, title, kind, instanceId, refId}`:
  title the owner's, else `Free time` (FREE), `Protected downtime` (RECOVERY), the block's title (missing: left out), or the code's name
  with no block; kind `TASK` for an owner, else the block's kind (missing: left out), else `RECOVERY` or `FREE`; instanceId the owner's or
  null; refId the block's (the rule or commitment id as text) or null. Kept sessions show where the preparation wrote them, the part before
  the clock included (LD28).
- **LDO-4** (`engine.js:1483-1495`; mutation `capacity counts kept as upkeep`) `capacityByDay(S)`: for each plan day, minutes (15 a slot) by
  the final code: free, task (TASK and LOCKED), recovery, hard (HARD, COMMUTE, EMERGENCY, PERSONAL), sleep, and every other code as
  `maintMin`; `otherMin` is always 0; `usableMin = freeMin + taskMin + recoveryMin`; with the day's `dateKey` and `dow`.
- **LDO-5** (`engine.js:887-896`) `recoveryBlocks(S)`: each run of RECOVERY slots `{startEpoch, endEpoch, recoveryType:
  'PROTECTED_DOWNTIME', bwcId}` (the waking day of its first slot, or null).

### 13.7 Goal allocation, quest coverage and the output

- **LDO-6** (`engine.js:1508-1516`; 6,468 rows; `rules/generate/alloc-*`; mutation `allocation keeps the first status`) For each goal plan
  (LDD-8, in order) a row per goal id, made by its first plan: `{goalId, title, priority, targetShare: share || 0, scheduledMin: 0,
  plannedSessions: 0, status: <first plan's>, projectionEpoch: <first plan's projection>, templates: []}`; every plan adds `{templateId,
  title, med: fMED, base, stretch, minutes: dOpt}` and a status other than `ON_TRACK` replaces the row's. Then every planned session adds
  `durationSlots * 15` and one session to the row of each of its goal links (a kept session's stored links and length, coerced); `actualShare`
  is the row's minutes over all rows' (1 when 0). Rows sorted by goal id.
- **LDO-7** (`engine.js:1496-1503`; LDQ-4 on the plan) `questCoverage(quests, planned, questStatus)` with the planned sessions' quest links:
  a committed session's links (covers included), a kept session's stored list (no list throws a `TypeError` once an active quest exists,
  LD45).
- **LDO-8** (`engine.js:1504-1528`, `1110-1115`; every `generate` case; mutation `planned ties by id descending`) The output object:
  `engineVersion '1.0.0'`, `schemaVersion 1`, `status` (`INVARIANT_VIOLATION` when validation fails, else `CONSTRAINT_WARNING` with any
  warning, else `VALID_SCHEDULE`), `generatedAtEpoch: now`, `computeTimeMs`, `timezone` (the zone value as given, LDA-10), `horizonStartEpoch`,
  `horizonEndEpoch`, `inputHash` (LDO-9), `scheduledInstances` (the sorted sessions, LDO-2), `historyInstances` (the preparation's history,
  the stored objects unchanged), `recoveryBlocks`, `passiveReminders`, `unscheduledDiagnostics` (LDO-1), `clarificationPrompts`,
  `trajectoryWarnings` (LDD-9, LDD-10), `warnings` (availability's, LDA, LDS), `validation` (LDV), `capacity`, `segments`, `goalAllocation`,
  `questCoverage`, `displacedLockedIds` (the displaced stored sessions' ids), `repair` (`{attempts, successes, rollbacks, moves}`) and
  `iterations`. A committed session is `{instanceId, taskId, templateId, title, tag, kind, goalId, goalTitle: goalTitle || null,
  linkedGoalIds, linkedQuestRequirementIds, startEpoch, endEpoch, durationSlots, minDurationSlots, optDurationSlots, tier, lifecycleState:
  'PLANNED', isUserLocked: false, bwcId, IP, IC, spacingKey, compressed, origStartEpoch, explanation, moves: 0, source}`, `source` being
  `taskSnapshot(t)`: the task's `coverable, id, kind, title, templateId, tag, goalId, goalTitle, linkedGoalIds, linkedQuestRequirementIds,
  priority, questWeight, tier, tierReason, dOpt, dMin, dMax, IP, IC, spacingKey, spacingMinH, spacingIdealH, maxPerBwc, avoidBeforeSleepMin,
  phases, preferRestDay, windowStart, windowEnd, deadlineEpoch, week, sessionIndex, dependsOn, minLagMin, exclusive, requiresPrior`, each
  present one through `deepClone` (`JSON.parse(JSON.stringify(x))`: NaN and the infinities become null, `-0` 0; `engine.js:62`). A kept
  session is its stored object with `bwcId` and `kept: true` (and its explanation when it had none).
- **LDO-9** (`engine.js:1529-1532`; family `hash` (37) and every `generate` case; mutations `hash names unsorted`, `hash keeps the clock`)
  `inputHash(input)`: `pick` is a shallow copy of the input without `clock`; the hash is `stableId('in', A + B)` (LDH-2) where A is
  `JSON.stringify(pick, Object.keys(pick).sort())` and B `JSON.stringify(pick)`. A, with an array replacer, writes at every depth only the
  members named like a top-level member, in the sorted list's order (`hash/hash/nested-top-name`, `unsorted-names`); B is the whole input
  in its own key order (array-index keys first, ascending, then insertion order). So the hash covers the whole input (plan D12 is right
  only about A) and depends on the order of every object's members (`hash/hash/key-order`, LD37). The page stores it and never compares it.
  Native writes both halves itself (`InputHash`; numbers keep their stored text, others as `Number.prototype.toString`).

### 13.8 Validation

`validateSchedule(v)` (`engine.js:1537-1614`) with `v = {engineInput, planned, rejected, recoveryBlocks, S}` in the plan (no `displacedIds`),
or any object a caller passes. It reads the sessions as stored values with JavaScript's coercions (LDG-6) and throws where the page throws
(family `validate`: `planned-null-item`, `planned-text`, `no-input`, `bad-zone`, `overlap-locked`).

- **LDV-1** (`engine.js:1538-1567`; `validate/validate/on-*`, `overlap*`, `cross-day-*`, `zero-length`, `started-past`, `off-grid`,
  `text-epochs`; mutations `validation: started slots checked`, `validation: zero length allowed`) Availability is derived again from the
  input (LDA-10; a missing input or a bad zone throws), never the planner's grid, so recovery reservations and sessions are not in it.
  `now` is the input's `currentTimeEpoch` as stored, `nowSlot = clamp(slotCeil(now + leadMinutes * 60), 0, n)`; the availability warnings
  `SLEEP_BELOW_FLOOR` and `SLEEP_COMMITMENT_OVERLAP` are kept. Then for each session (`(planned || []).forEach`: not a list throws): `a =
  slotOf(start)`, `b = slotCeil(end)`; `b <= a` is `I1 Zero-length instance`; for each slot from `max(0, a)` below `min(n, b)`: before
  `nowSlot` of a session that started (`start <= now`) the slot is only marked as the session's; a slot not FREE is `I1 "<title>" overlaps
  <SLOT_NAMES>` (and stops the session's slots); a slot already marked (another session, or a pinned one) is `I1_OVERLAP "<title>" overlaps
  "<other's title>"`, and when the other is pinned (marked -2) the page reads `planned[-2].title` and throws a `TypeError` (LD35); a slot of
  another waking day than the session's first (the day of the first slot, -1 for free time no day owns) is `I1_SLEEP_CROSS Crosses a sleep
  boundary`. A session whose first slot has a day joins that day's list. A pinned session's marks become -2 (its time is the player's, and
  is not counted below). A session without an explanation with a truthy `summary` is `I10 Missing explanation`.
- **LDV-2** (`engine.js:1559-1566`; `validate/validate/deadline*`, `window`, `prereq-*`, `source-*`) For a session not pinned, not kept,
  starting after `now`, with `src = source || {}`: a truthy `src.deadlineEpoch` it ends after is `H_DEADLINE`; a truthy `src.windowEnd` it
  ends after is `H_WINDOW`; each of `src.dependsOn` (a text by unit; a non-list, non-text throws) not matched by a session whose `taskId`
  equals it or whose `instanceId` is `'i' + dep.slice(1)` is `I5 Prerequisite not scheduled`; a matched one whose `endEpoch + (minLagMin ||
  0) * 60` is after the session's start is `I5 "<title>" starts before its prerequisite ends`.
- **LDV-3** (`engine.js:1568-1576`; `validate/validate/spacing*`; mutation `validation: spacing for kept sessions`) Sessions not pinned with
  a truthy `source` whose `spacingMinH > 0` are grouped by `spacingKey` (a `Map`: keys in the order first seen, SameValueZero), each group
  sorted by start (stable); a session closer than `spacingMinH` hours to the one before it in its group, starting after `now`, not kept, is
  `H_SPACING "<title>" is closer than <spacingMinH>h to a similar session`.
- **LDV-4** (`engine.js:1577-1593`; `validate/validate/downtime*`, `shortfall`, `status-shortfall`; mutations `validation: downtime
  inclusive`, `validation: daily cap inclusive`) `need = ceil(minDailyRecoveryMinutes / 15)`. Each waking day not over (`endSlot >
  nowSlot`) and not starting before the plan: from `max(startSlot, nowSlot)`, its FREE slots not pinned (`free`) and those holding a
  session (`used`); `free - used < min(need, free)` is `I3 Wake cycle <date> keeps only <(free-used)*15> min of downtime (needs <min*15>)`,
  else the day is compliant; `free < need` adds the warning `RECOVERY_SHORTFALL <date>: your commitments leave only <free*15> free minutes,
  so all of it is kept as downtime.` (with `bwcId`); when the day holds an automatic session (not pinned, not kept) and the minutes of its
  sessions that are not pinned (kept ones included) exceed `maxDailyGoalMinutes`, `H_DAILY_CAP <date> exceeds the daily effort limit`.
- **LDV-5** (`engine.js:1595-1603`; `validate/validate/pin-*`, `rejected-no-words`; the 8 I7 violations of generated plans, LD36) Every
  stored session of `existingSchedule` that is pinned, PLANNED or IN_PROGRESS, and inside the plan (`endEpoch > H0`, `startEpoch < HN`) must
  be planned with the same start and end, unless excused: its id in `displacedIds` (a list by SameValueZero, a text by substring) or a
  rejection with that `instanceId`; else `I7 A pinned activity was moved`. Each rejection without a truthy `humanExplanation` is `I10
  Rejected task without explanation` (with its `taskId`).
- **LDV-6** (`engine.js:1604-1613`; mutations `validation: SQI weight 35`, `validation: fairness keys as inserted`) `req` sessions and
  rejections of tier `>= 3`, `all` sessions and rejections with a truthy tier; `coverageRequired = req / (req + reqMiss)` (1 when 0),
  `coverageAll` likewise, `recoveryCompliance` compliant days over counted days (1 when none), `compressed` the share of compressed
  sessions (0 when none); `fairness` 1, or with the planner's state and two goals or more and a fairness total: `1 - sum(|goalMin/total -
  share|) / 2` over the shares' keys in `Object.keys` order (array-index keys first: `rules/generate/alloc-numeric-ids`, the order of a
  floating sum); `sqi = round((40 coverageRequired + 15 coverageAll + 20 recoveryCompliance + 15 fairness + 10 (1 - compressed)) * 10) /
  10`. The result: `{isValid: no violation, violatedInvariants, warnings, qualityMetrics: {sqi, coverageRequired, coverageAll,
  recoveryCompliance, fairness: round(fairness * 1000) / 1000, compressedFraction}}`.

### 13.9 Floating point and speed

- Nothing in part 5 computes an exp or pow of its own; the repair and the fill pass re-run LDW-1 (so LD20 reaches them: node 22, fdlibm,
  records a different answer for 18 of the 2,915 cases, all of them `generate` or `repair` cases whose placements read an exp one bit apart), and the explanation
  re-scores each session once (LDU-1). The rounded display numbers (`Math.round(v * 10) / 10`, `* 100`, `* 1000`) follow PageValues.round
  (JavaScript's `Math.round`, -0 kept). The validation's fairness is a sum in `Object.keys` order (LDV-6).
- Speed: section 13.10.

### 13.10 What was measured

- 2,915 cases in Chromium 153 (about 99.7 MB of text, 9.6 MB gzipped); the native code reproduces all of them exactly
  (`ScheduleGoldenTest`, about 10 seconds for the whole file on the JVM). Every case gave the same answer twice in the recorder; node 22
  (fdlibm) differs on 18 whole plans (LD20). The counts of what was recorded are in section 8.5.
- The repair's budget counts days, not attempts (LD43): on `SchedulePerfTest`'s four weeks it is spent after 311 attempts and 4
  successes, with 151 tasks left out.
- Speed. `PlanSchedule.generate` on `SchedulePerfTest`'s plan (four weeks, eight advanced goals at the hardcore intensity, eight quests;
  the repair spends its whole budget: 401 moves, 311 attempts; 129 sessions planned; the case `rules/generate/repair-four-weeks`, so the
  timed answer is the page's) takes 85 ms on the JVM of the test machine (median of five, after two warm-up runs; `PlacementPerfTest`'s
  construction alone, on its own four-week plan, 111 ms in the same run). The page takes about 1,000 ms for the same plan in node on the
  same machine. The repair needed no cache beyond part 4's: a snapshot copies the slot arrays (about 2,700 slots of eight arrays for four
  weeks) and the lists, and the whole file of 2,915 plans replays in about 10 seconds. A phone will be several times slower.

## 14. Rescheduling and hold placement (part 6, WP-LD5b)

Part 6 is `rescheduleActivity` (`engine.js:1619-1790`): the local repair of a plan after one change, for every trigger (`MISSED`,
`SKIPPED`, `RESIDUAL`, `MOVE`, `NEW_COMMITMENT`, `BLOCK_HOURS`, `BLACKOUT_DAY`, `SICK`, `QUEST_CHANGED`, `TIME_CHANGED`); its hold
placement (`prepareStateWithHold`, `engine.js:1798-1842`), the placement by radius (`placeByRadius`, `engine.js:1843-1903`), `dayEndAfter`
(`engine.js:1791-1795`, LDT-18), the refusals of a move, the change record and the rebuilt output. The plan's own trigger list in WP-LD5
says `MISSED`, `SKIPPED`, `MOVE`, `RESIDUAL`, `TIME_CHANGED`, `NEW_COMMITMENT`, `BLOCK_HOURS`, `BLACKOUT_DAY`, `SICK`; the code also has
`QUEST_CHANGED` (the page's demo uses it), and no `late`, `early`, `hold` or `insert` trigger exists: a late or early session is a `MOVE`
or a completion (WP-LD6), a hold is what every trigger does to the sessions it does not touch, and an insert is radius placement. The page
calls it from `logic.js:145-157` (its `reschedule` and `planTimeChange`) with an engine input it builds afresh (`buildInput`,
`logic.js:110-118`: the clock as the horizon start, and the current plan's sessions and history as `existingSchedule`). Native:
`Reschedule` (the request and its order), `RescheduleTriggers` (`TriggerPlan`, `StoredSession`), `HoldPreparation` (`HeldPlan`,
`HeldSessions`), `SourceTask` (`makeTask` on a stored snapshot), `RadiusPlacement` (`Replacement`, `RescheduleRecords`),
`RescheduleOutput`, in `core/lifedirector`; it reuses part 4's and part 5's `PlanState`, `Placer`, `Feasibility`, `PlanSnapshot`,
`PlanRemoval`, `Rejections`, `Explanations`, `PlanSegments`, `ScheduleValidation`, `InputHash`, `ScheduleJson` and `PlanSchedule`
(for `TIME_CHANGED`'s fresh plan and the goal allocation). IDs: `LDM-n` the request and the triggers, `LDB-n` the hold, `LDY-n` the placement
by radius, `LDZ-n` the output and the change record. Every rule is **Executed** by the families of section 8.6 unless marked **Read**.
The output is the stored schedule (`ST.schedule`) and the change record goes to `ST.changeLog`, so every member of both is part of the
contract except their `computeTimeMs` (measured times, plan 4.2(7)); member order is compared too (it reaches `inputHash` and the saved
text). Nothing is wired to a screen; the page stays the only planner.

### 14.1 The request and the triggers

- **LDM-1** (`engine.js:1620-1633`, `1638`, `1647`, `1695`; families `refuse`, every case) The request is `{engineInput, currentOutput,
  trigger}`; `trigger || {}`. The engine input is copied by `deepClone` (JSON: NaN and the infinities become null, -0 becomes 0) and every
  change the trigger makes is to the copy; the clock is the copy's `currentTimeEpoch`. The current plan's `scheduledInstances` (which must
  be a list) are copied one by one (`prevPlanned`), its `historyInstances` too (none: an empty list); the sessions to keep start as all of
  `prevPlanned`. A session a trigger names (`instanceId`) is looked up among them; none throws `Error('Instance not found: ...')`. A type
  outside the ten (or none) throws `Error('Unknown trigger ...')` after the copies are made. A throw changes nothing (the page shows
  nothing and keeps its plan). Native: `Reschedule.run`, `RescheduleTriggers.plan` (`EngineRefusal`).
- **LDM-2** (`engine.js:1636-1643`, `1632`; families `miss`, `hold`, `radius`, `sequence`) `MISSED`, `SKIPPED`: the session is retired:
  its task id joins the gone set, it leaves the kept sessions, and a copy with `lifecycleState` `MISSED_UNEXPLAINED` (or
  `SKIPPED_VOLUNTARY`) and `resolvedAtEpoch` the clock is appended to the history. Action `{action: 'MISS' | 'SKIP', instanceId}`. A
  session of tier 3 or more with a source is placed again (`REPLACEMENT`, replacing it, preferring the clock's day); a skip also keeps it
  off the skipped session's waking day (its `bwcId`). A tier-2 or lower session is simply gone.
- **LDM-3** (`engine.js:1645-1654`, `1699-1713`; families `move`, `refuse`, `dst`, `scenario`) `MOVE`: the session leaves the kept
  sessions and comes back as `moved`, `Object.assign({}, it, {startEpoch: newStartEpoch, endEpoch: newStartEpoch + (end - start),
  isUserLocked: true, rescheduledAdvance: true, moves: (moves || 0) + 1, explanation: {tier, summary: 'You moved this here and it is pinned;
  Life Director will not move it automatically.', factors: [], locked: true}})` (new members appended in that order). Action `{action:
  'MOVE', instanceId, from: old start, to: new start}`. The moved session goes first into the hold (LDB-1). The move is refused, and the
  result is `{rejectedMove: {reason, message}, updatedSchedule: the current plan itself, engineInput: the one passed in (not the copy),
  changeRecord: null}`, when, in this order: the hold let it go (`BLOCKED_TIME`, 'That time overlaps sleep, work, a meal or another fixed
  block.'); it is not on the held state at all, being wholly before the plan or starting at or after its end (`OUTSIDE_PLAN`, 'That time is
  outside the current planning window.'); or its waking day's recovery after the hold, `recoverySlots * 15`, is below
  `minDailyRecoveryMinutes` and the day did not start before the plan (that exemption is **Read**: no recorded move reaches such a
  day) (`RECOVERY_PROTECTED`, 'Moving it there would cut that day below your
  minimum downtime.'). The last check reads the day after the hold, so a day whose free time is short of the minimum anyway refuses every
  move into it (`refuse/reschedule/move-short-day-*`). A move to a time that is already over is accepted (LD55); a move onto a session
  pinned before it depends on which starts first (LD53).
- **LDM-4** (`engine.js:1644`; family `residual`) `RESIDUAL`: `trigger.task` (the page's `residualTask` from `recordCompletion`,
  `engine.js:1968-1973`: the session's source with a new id, the remaining minutes as `dOpt`, `dMin`, `dMax`, `tierReason: 'RESIDUAL'`,
  `windowStart` the clock) is placed (`RESIDUAL`, replacing `parentInstanceId`, preferring the clock's day). Nothing is retired.
- **LDM-5** (`engine.js:1655-1659`; family `disrupt`) `NEW_COMMITMENT`: `trigger.commitment` is pushed onto the copy's
  `fixedCommitments` (created, as the input's last member, when it is falsy), so the hold paints it as hard time and lets go of what it
  covers (LDB-1, LDB-4). Displaced sessions are all placed again.
- **LDM-6** (`engine.js:1655-1662`, `1723-1725`, `1738`; families `disrupt`, `radius`, `dst`) `BLOCK_HOURS`, `BLACKOUT_DAY`, `SICK`: the
  copy's `playerAvailability` (created when falsy) gets `blackouts` (created when falsy) with `Object.assign({kind: 'SICK' | 'BLACKOUT'},
  commitment)` pushed (so `kind` comes first, and a commitment's own `kind` wins). A displaced session of tier 2 or lower that starts before
  the shed limit is not placed again (action `{action: 'SHED', instanceId, reason: 'LOW_TIER_AFTER_DISRUPTION'}`): for `BLOCK_HOURS` the
  limit is the next local midnight after the block's start (`dayEndAfter` in `timezone || playerAvailability.timezone || 'UTC'`, LDT-18),
  for a blackout and sickness it is never reached (everything low is shed). For `SICK`, every task to place (not only the displaced) gets
  `notBefore` the commitment's end (LDY-1).
- **LDM-7** (`engine.js:1664-1694`; families `quest`, `time`, `sequence`) `QUEST_CHANGED` and `TIME_CHANGED` carry nothing: the change is
  in the engine input. `QUEST_CHANGED` normalises the input's quests (LDQ-1; by id, the last of an id) and, for each kept session not
  pinned, starting after the clock, with quest links: keeps the links whose quest is active, and sets each kept link's `deadlineEpoch` to
  its quest's (`|| null`) when they differ (`!==`) and the quest is not daily. Nothing changed: next. No link left and no goal link: the
  session is retired as `CANCELLED_BY_QUEST` (action `DELETE`, reason `QUEST_CLOSED`). Otherwise the session takes the kept links and a
  source with those links and `deadlineEpoch` the earliest truthy link deadline (null for none); when that deadline is before the session
  ends, it is retired as `EXCUSED_DISPLACED` with `excuseReason: 'QUEST_DEADLINE_CHANGED'` (action `EXCUSE`) and placed again with tier 4
  and `tierReason: 'CRITICAL_DEADLINE'` when the deadline is at most 25 hours (90,000 s) from the clock, else tier `max(3, tier)` and its
  reason, and `windowEnd: min(windowEnd || deadline, deadline)`; else action `RELINK`. `TIME_CHANGED` plans the input from scratch
  (`generateSchedule`, part 5) and, unless that plan fails validation, places every planned task whose id no current session has
  (`TIME_FREED`, preferring the clock's day); the current sessions are only held. Its change record has no `triggerReason` (LD47).

### 14.2 The hold

- **LDB-1** (`engine.js:1698-1700`, `1798-1821`; every family) The hold is the kept sessions, each `Object.assign({}, it, {_hold: true})`,
  with the moved session (no mark) first. `prepareStateWithHold` builds a fresh state (LDR-1) for the input with `existingSchedule: []`
  (so LDR-3's own sorting never runs), then puts each held session back, pinned ones first (`isUserLocked` truthy), then by start, then by
  `cmpStr(instanceId)`: a session wholly before the plan or starting at or after its end is skipped (not displaced; LDB-4 carries the
  former); one that overlaps a slot that is not free from the plannable slot on (and is not over) or whose start (or the clock, if later)
  is in no waking day is let go (`displaced`, as it was held); else it is copied with `bwcId` its day's id and `kept` (its `isUserLocked`
  when truthy, else whether it has started), and placed (LDR-3 `occupy`) as LOCKED when pinned or started, else as a TASK slot with a live
  task (LDB-2). Pinned sessions are placed before the others so that a pinned one never yields to an auto-placed one; between two pinned
  ones the earlier start wins (LD53). Native: `HoldPreparation.prepare`.
- **LDB-2** (`engine.js:777-785`, `1815-1819`; every family) A movable held session with a truthy source gets its task again:
  `makeTask(Object.assign({}, source, {id: taskId || source.id}))`: the defaults (`kind 'GOAL'`, empty links, no prerequisites, lag 0, not
  exclusive, no prior volume, not blocked, priority 3, quest weight 0), then the snapshot's members, its three lists copied (`|| []`);
  prepared (LDE-2, with the input's goal info), marked placed, and added to the state's tasks (`taskById`: the last of an id). The
  session's `taskId` becomes the task's id. The task's own snapshot, when it is committed again (a compression, an eviction), is
  `taskSnapshot` of those members (LDO-8): native keeps them as stored (`SourceTask`) and writes the snapshot from them. Native reads a
  snapshot only of the engine's types (every snapshot the engine writes); another shape is `UnreadableSource`, never a silent difference.
- **LDB-3** (`engine.js:1822-1840`, `1709-1712`; families `hold` (`recovery-protect-*`), `miss`, `quest`, `residual`, `sequence`) Recovery is
  reserved on every day (LDR-4), then each day not started before the plan and not over keeps its downtime: while its recovery slots are
  fewer than `min(need, freeSlots + the stored lengths of its movable sessions)` (movable: not kept, not pinned, starting after the clock)
  and it has a movable session, the one of the lowest tier, then the latest start, then `cmpStr(instanceId)` is taken off (LDX-1) and let go
  (as it was held), the day's recovery slots become free and recovery is reserved again; at most 20 times a day. Any trigger can do this
  (a held day can be short after an availability change); the session is excused with the trigger's name (LD51).
- **LDB-4** (`engine.js:1714-1737`; families `hold`, `disrupt`, `quest`, `time`, `sequence`) After the refusals: every held session that is
  `PLANNED` or `IN_PROGRESS` and ends at or before the plan's start is carried to the output untouched (`carryPast`). Each session the
  hold let go joins the gone set and the history (the held copy with `_hold`, LD46, then `lifecycleState: 'EXCUSED_DISPLACED'`,
  `resolvedAtEpoch`, `excuseReason` the trigger's type), action `EXCUSE` with that reason, and is placed again (`REPLACEMENT`, preferring
  its own start's day) unless shed (LDM-6). Then, up to 20 rounds while anything changes, every movable session on the state whose source's
  `dependsOn` names a gone task leaves too (LDX-1; history with `excuseReason: 'PREREQUISITE_MOVED'`, action `EXCUSE`), its task id joins
  the gone set, and it is placed again. Only gone tasks cascade: a prerequisite moved (pinned) after its dependent stays, and the plan fails
  `I5` (LD56).

### 14.3 Placement by radius

- **LDY-1** (`engine.js:1741-1755`; every family that places) Each task to place gets its id first, in the order the tasks were listed:
  `stableId('t', (origInstanceId || source.id) + ':r:' + index + ':' + clock)` (LD49); a source id maps to its replacement's id (the last
  of an id). They are placed in order of prerequisite depth (how many chained prerequisites among the tasks to place, at most 20), then
  tier (higher first, `|| 0`), then `cmpStr(source.id || '')`. Each: `makeTask(Object.assign({}, source, {id: new id, tierReason:
  'RESIDUAL' for a residual, else source.tierReason || 'REPLACEMENT'}))`; `windowStart = max(windowStart || clock, notBefore)` when it has a
  truthy `notBefore`, then `max(windowStart || clock, clock)`; `windowEnd` the plan's end when falsy or not after the start; each
  prerequisite id replaced by its replacement's id when it has one (an unreplaced one stays and, being gone, blocks: LDJ-1
  `PREREQUISITE_NOT_SCHEDULED`); prepared (LDE-2) and added to the state's tasks.
- **LDY-2** (`engine.js:1843-1858`; every family that places) `placeByRadius`: the allowed days are all but the skipped one. The freeze
  zone ends at the clock for a tier-4 task, else 24 hours after it. A placement commits (LDW-6, its new id with `i`), records `{phase: 'R'
  + radius, score, count}` (and its move) for its explanation, and adds action `{action: 'INSERT', instanceId, radius, replaces:
  origInstanceId || null, to: start}`. Radius 1: the day of `max(preferBwcEpoch || clock, clock)`, when allowed, by LDW-1 alone. Radius 2:
  LDW-1 over the allowed days. Neither moves anything.
- **LDY-3** (`engine.js:1860-1877`; families `radius`, `disrupt`, `sequence`, mutations `radius 3 shrinks three`, `compression without kept
  false`) Radius 3, each allowed day in order, spending one of `S.iterations` (shared with radius 4, never reset in the reschedule) and
  skipping the day once it is past 600: up to two of the day's sessions that are not kept, not pinned, start after the freeze zone, are
  longer than their shortest length (stored members for a held session), have their task on the state and anchor no dependent (LDW-5),
  lowest tier then earliest; when they can free at least one slot, a snapshot (LDX-2) is taken and each is taken off and committed again
  where it started at its task's shortest length (keeping its id and explanation, with a `kept: false` member last), if LDK-2 allows it;
  then LDW-1 in that day. Success: actions `{action: 'COMPRESS', instanceId, fromSlots: its stored length}` for each, then the insert
  (`move: 'COMPRESSION'`). Otherwise the snapshot is restored.
- **LDY-4** (`engine.js:1878-1902`; families `radius`, `scenario`, `seeds`, `sequence`) Radius 4: a task of tier 2 or lower stops (radius 4,
  nothing placed). A tier 3 or 4 task may evict: the sessions not kept, not pinned, of a lower tier than the task and at most 3, starting
  after the freeze zone, with their task on the state and anchoring no dependent, lowest tier then latest then id; tried one at a time,
  then every pair in the same day; each set spends one iteration and the search stops past 900. For a set: snapshot, take them off, LDW-1
  over the allowed days; success places the task (`move: 'EVICT'`) and then each evicted session's task goes to its best place over all
  days (the skipped day included), keeping its id (action `{action: 'MOVE', instanceId, from, to}`, recorded `RELOCATED` for its
  explanation), or is shed (`{action: 'SHED', instanceId, reason: 'DISPLACED_BY_HIGHER_PRIORITY'}`): it leaves the plan with no history and
  no rejection (LD50). Otherwise restore and try the next set.
- **LDY-5** (`engine.js:1756-1759`; families `radius`, `disrupt`, `sequence`, mutation `dropped text never added`) Each placed task adds
  `{taskId, title, radius, origInstanceId}` to `radiusUsed` (the radius it ended at; a member that is undefined is left out); one not
  placed gets LDJ-1's rejection, with ' It was dropped for this week to keep your other plans stable.' added for a tier 2 or lower that
  reached radius 4 (always, for a low tier that did not fit), and the radius actions follow the trigger's in the change record.

### 14.4 The output and the change record

- **LDZ-1** (`engine.js:1762-1765`; every family) The planned sessions: every session on the state, copied without `_hold` (a held one as
  held: its stored members with `bwcId` and `kept`; a placed one with `commit`'s members, LDO-8), and the carried ones (LDB-4) whose id is
  not on the state and whose task is not gone; by start then `cmpStr(instanceId)`.
- **LDZ-2** (`engine.js:1767-1771`, `1504-1527`; every family) The output is `assembleOutput` (LDO-8) on the held state: the input's goal
  plans (LDD-8) for the allocation, its normalised quests for coverage with no planner status (each quest's own status), the history,
  the rejections (this reschedule's first, then the current plan's `unscheduledDiagnostics` whose `taskId` no planned session has), the
  current plan's `passiveReminders`, `clarificationPrompts` and `trajectoryWarnings` (`|| []`) as they are, `displacedLockedIds` the
  sessions the hold let go, `repair: null`, `iterations` the reschedule's. The held state has no fairness shares, so every goal's
  `targetShare` is 0 and the scores of this reschedule's placements are pulled down by each goal's current share (LD48).
- **LDZ-3** (`engine.js:1766`, `1440-1466`; every family, `residual/reschedule/same-clock-then-miss`) A planned session is explained
  (LDO-2) when it has no explanation or it has a radius record: a held kept or pinned one says so; a held movable one (only when a session
  placed now took its id, LD49) is scored as the task of its id (the last) with its own length, links, goal title, compression and tier;
  a placed one as part 5. The selection's `phase` is the record's `R1` to `R4`, its `regret` 0 and `key` null; a compression adds 'Placed by
  shortening flexible tasks that day.', an eviction and a relocation nothing. Replacements keep their source's `tierReason`, so the
  'it replaces a session that was missed or displaced' wording is never reached from the engine's own sessions (LD54).
- **LDZ-4** (`engine.js:1506`, `1537-1614`; every family) The output's validation is `validateSchedule` on the changed input (part 5,
  LDV) with no `displacedIds`: a pinned session of the input's `existingSchedule` that this reschedule retired, displaced or moved again is
  `I7`, and the status `INVARIANT_VIOLATION`, which the page refuses to publish (LD52). A moved prerequisite can leave `I5` (LD56).
- **LDZ-5** (`engine.js:1773-1789`; every family) The change record: `changeId` `stableId('chg', type + ':' + (trigger.instanceId || '') +
  ':' + clock + ':' + number of actions)`, `timestampEpoch` the clock, `triggerReason` by type (`MISSED_TASK_REPAIR`, `USER_SKIP`,
  `PARTIAL_COMPLETION`, `USER_MANUAL_MOVE`, `NEW_COMMITMENT_ADDED`, `TIME_BLOCKED`, `DAY_BLACKOUT`, `SICK_MODE_ACTIVATED`,
  `QUEST_MODIFIED`; none for `TIME_CHANGED`, LD47), `actions` (the trigger's, the hold's, the radius actions), `radiusUsed`,
  `affectedInstanceIds` (the actions' truthy instance ids, once each, in order), `churn` over the current plan's sessions starting after the
  clock other than the trigger's (`futureInstances`): `collateralMoved` those not let go by the hold that are gone or start elsewhere,
  `compressed` those with the same start and another end, `ratio` the first over the count (0 for none); `computeTimeMs` (measured; the
  output takes the same value). The result is `{updatedSchedule, engineInput: the changed copy, changeRecord, rejectedMove: null}`.

### 14.5 Floating point and speed

- Nothing in part 6 computes an exp or pow of its own; the placements re-run LDW-1 and the explanations LDU-1, so LD20 reaches them: node 22
  (fdlibm) records a different answer for 2 of the 2,298 cases, both one bit of a movement penalty in an explanation.
- Speed: section 14.6.

### 14.6 What was measured

- 2,298 cases (4,329 steps) in Chromium 153 (about 137.7 MB of text, 12.0 MB gzipped); the native code reproduces every step exactly,
  member order included (`RescheduleGoldenTest`, about 14 seconds for the whole file on the JVM, the plan of every case made natively
  first). Every case gave the same answer twice in the recorder; node 22 (fdlibm) differs on 2 cases (LD20: one bit of a movement penalty
  in an explanation, `miss/reschedule/seed242`, `disrupt/reschedule/seed80`). The counts of what was recorded are in section 8.6.
- Speed. On `SchedulePerfTest`'s four-week plan (129 sessions), `Reschedule.run` takes 8 ms for a missed urgent session, 36 ms for a
  two-day sick spell over its start and 120 ms for `TIME_CHANGED` (which plans the whole input again) on the JVM of the test machine
  (`ReschedulePerfTest`, median of five after two warm-up runs); the page takes 17, 101 and 1,114 ms for the same three in node on the
  same machine. Nothing needed a cache beyond parts 4 and 5: a reschedule is one hold, a few placements and one output. A phone will be
  several times slower.
- Mutations. 50 source mutations of the part's code (the hold, the recovery protection, the refusals, each radius, the triggers, the placement order, the output and the change record), 47 caught; the 3 survivors are explained: `a clash in the past displaces` is equivalent (a session over before the clock has no slot from the plannable one on, so it never clashes), and two bounds no input reaches: the truncated-day exemption of `RECOVERY_PROTECTED` (no recorded move lands in a day that started before the plan) and the radius-3 budget of 600 (the most any recorded reschedule spends is 195). `snapshot from the candidate` and `low tiers not shed` were first missed (a pattern that did not compile, and no snapshot holding a member a candidate never holds) and are caught since `residual/reschedule/null-goal-title` was added.
