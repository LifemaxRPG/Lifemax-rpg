# Life Director engine specification: the scheduler in `engine.js`

Status: **part 1 of several** (WP-LD1). Sections 0 to 2 and the rules for time and hashing (sections 4 and 5) are written; the
scheduler's other parts (availability, demand, placement, rescheduling, ledger, store) follow as their work packages start
(`LIFE_DIRECTOR_NATIVE_PLAN.md` section 5). It describes the engine as it behaves at branch `codex/director-dial-controls`,
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
8. Golden cases: the index
9. Open questions for the owner

---

## 0. How to read this

Rules carry IDs and labels. IDs: `LDN-n` numbers and semantics, `LDT-n` time, `LDH-n` hashing and identities. Later parts will add
their own prefixes. Each rule is labelled:

- **Executed**: the golden file `lifemax_modular_v73/tests/fixtures/ld-kernel-golden.json` holds cases recorded from the real page that
  show it; the case ids or families are given. The native kernel replays every one of them (section 8).
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
| 59-62 `clamp`, `byId`, `cmpStr`, `deepClone` | small helpers; specified with their first caller | LD2 |
| 151-179 `createGrid` (the typed arrays other than `localMin` and `dayOf`), 184-198 `paint` | the availability grid | LD2 |
| 200-466 rules, preferences, sleep (`deriveSleep` 273-304 uses `zonedToEpoch`, `ymdAdd`, `ymdKey` exactly as section 4 states; its rules are availability), `deriveAvailability`, maintenance, meals | availability | LD2 |
| 468-800 tags, archetypes, templates, goals, quests, candidates, `freshIds` 768-776 | demand | LD3 |
| 804-1160 energy, state, scoring, commit and removal | placement | LD4 |
| 1163-1535 `generateSchedule`, repair, fill, output, `inputHash` 1529-1532 | generation | LD4 |
| 1537-1618 validation | validation | LD4 |
| 1619-1905 `rescheduleActivity`, hold placement (`dayEndAfter` 1791-1795 is in this part) | rescheduling | LD5 |
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

- **LDT-2** (**Executed**: family `zone`, 693 names: 659 valid, 34 not) `isValidTimeZone(tz)` is whether
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
  or be negative and roll the date; `wall/zonedToEpoch/*@*+1441`, `@2026/3/8/-1440`, `@2026/3/8/4320`). NaN for an Invalid Date. The
  result is an instant at which the zone's wall clock reads `W` (to the minute), chosen by LDT-7 to LDT-9.
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

### 6.3 What was measured

- The 15,231 golden cases: identical in Chromium 153 (the file) and in node 22 except the three of 6.2(6); the native kernel on the
  JVM (tz 2026b) reproduces all 15,231 exactly.
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

---

## 8. Golden cases: the index

File `lifemax_modular_v73/tests/fixtures/ld-kernel-golden.json` (15,231 cases, about 2.9 MB). Recorder
`lifemax_modular_v73/tools/gen_ld_kernel_golden.js` (Chromium, the real built page; node as a second runner), definitions
`tools/ld_kernel_cases.js` (no `Intl`: every instant comes from `Date.UTC` and a table of the 28 clock changes of 2026-2027 in the
zones that have them, which the web test checks against the recorded offsets), loader `tools/ld_engine_vm.js`. Web test
`tests/ld-kernel-golden.test.js`; native tests `KernelGoldenTest` (exact replay, family counts) and `KernelMutationTest` under
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
| `zone` | 693 | `isValidTimeZone` | every JVM and ICU zone id and link, legacy names, case variants, offsets, junk | LDT-2, LDT-3 |
| `alias` | 1386 | `tzOffsetSec` | every name of `zone` in January and July 2026 | LDT-3, LDT-5 |
| `parts` | 2248 | `localParts` | every 2026 change: 30-minute steps over a day, each minute for 10 minutes, the second either side; 2027 hourly; midnights of month, year and leap-day boundaries in 15 zones; 1970, 2038, 2100, fractions | LDT-4, LDT-21 |
| `offset` | 2023 | `tzOffsetSec` | the same instants | LDT-5 |
| `wall` | 4900 | `zonedToEpoch` | the dates around every change: each 15 minutes, each minute of every gap (488) and fold (422), overnight minutes to 2880; boundaries; roll-over, negative, fractional, two-digit years | LDT-6 to LDT-9, LDN-3 |
| `cal` | 334 | `ymdAdd`, `ymdKey`, `parseYmd`, `daysBetween` | leap years, month and year ends, 1969/1970, out-of-range fields, Invalid Dates, odd texts | LDT-11 to LDT-14, LDN-4 |
| `horizon` | 119 | `buildHorizon` | 7 starts in 15 zones (06:00, midnight, a second before, 1 day, 28 days, year end, 0 days); each 2026 change | LDT-15 |
| `grid` | 22 | `createGrid` | each 2026 change; plain days | LDT-16 |
| `slot` | 102 | `slotOf`, `slotCeil`, `epochOf`, constants | before, on and after slot edges, fractions | LDT-17, LDT-1 |
| `week` | 1071 | `dayEndAfter`, `weekKey`, `weekBounds` | Sundays, Mondays, midnights -1 s, year end, each 2026 change -1 s, at, +3 days | LDT-18, LDT-19 |
| `fuzz` | 1900 | the time functions; `cyrb53`, `stableId` | 1600 seeded instants, wall times and zones (40% within two hours of a change), 300 seeded texts of every UTF-16 shape | all |

Mutation checks (`KernelMutationTest`, committed; and source mutations of the native kernel, run by hand for WP-LD1): each of
these makes the replay fail: `java.time`'s fold default (227 cases), the gap resolved with the later offset (494), a hash constant
off by one (664) or a shift changed (663), 10-digit id halves (217), durations rounded down (17), the weekday off by one (533), no
1900 rule (25), 600-second slots (414), `daysBetween` truncated (3), the offset's fraction dropped (22), every grid day treated as
steady (13), case-sensitive names (30), offsets capped at 18 hours (6).

Not recorded in this part: `localParts` of an Invalid Date (throws), `tz === undefined` (device zone), years before 1900 (local mean
time offsets with seconds), zones with two changes within hours.

---

## 9. Open questions for the owner

- **OQ-1** The zone matrix: are these the zones that matter (the ones the owner lives in or travels to)? Adding one is a line in
  `tools/ld_kernel_cases.js` and a regenerated file.
- **OQ-2** Travel: is a zone change in the middle of a horizon in scope (today it forces a full re-plan at `maybeRollover`)?
- **OQ-3** Which tz data wins on the phone when the WebView's ICU and Android's tz data disagree (6.2(5): today Vancouver and Chisinau
  would)? Options: native follows its own data and the shadow comparison ignores those zones; or the page sends the offsets it used.
- **OQ-4** LD01: keep the second occurrence in a fold east of Greenwich (port as is), or move both page and native to the first?
  A change moves sessions on the autumn change day in those zones, so it needs a new golden file.
- **OQ-5** Plan D12 (`inputHash`): drop from the contract, or keep byte-equal (LDH-6)?
