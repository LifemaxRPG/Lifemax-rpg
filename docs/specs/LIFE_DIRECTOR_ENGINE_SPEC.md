# Life Director engine specification: the scheduler in `engine.js`

Status: **parts 1 and 2 of several** (WP-LD1, WP-LD2). Sections 0 to 2 and the rules for time and hashing (sections 4 and 5) are
part 1; availability (rules, preferences, sleep, the painted grid) is part 2, section 10. The scheduler's other parts (demand,
placement, rescheduling, ledger, store) follow as their work packages start (`LIFE_DIRECTOR_NATIVE_PLAN.md` section 5). It describes the engine as it behaves at branch `codex/director-dial-controls`,
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
8. Golden cases: the index (8.1 the kernel, 8.2 availability)
9. Open questions for the owner
10. Availability: rules, preferences, sleep and the painted grid (part 2)

---

## 0. How to read this

Rules carry IDs and labels. IDs: `LDN-n` numbers and semantics, `LDT-n` time, `LDH-n` hashing and identities (part 1); `LDG-n` the
grid and value rules, `LDA-n` availability, `LDP-n` preferences, `LDS-n` sleep (part 2, section 10). Later parts will add their own
prefixes. Each rule is labelled:

- **Executed**: a golden file holds cases recorded from the real page that show it; the case ids or families are given. Part 1's
  file is `lifemax_modular_v73/tests/fixtures/ld-kernel-golden.json`, part 2's `ld-availability-golden.json`. The native code replays
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
| 60, 62 `byId`, `deepClone` | small helpers; specified with their first caller | LD3, LD4 |
| 151-179 `createGrid` (the typed arrays other than `localMin` and `dayOf`), 184-198 `paint` | part 2 (LDG-3, LDG-4) | LD2, done |
| 200-428 rules, preferences, sleep, `deriveAvailability`, maintenance, meals; `logic.js:577-588` `ruleOn` | part 2, section 10 | LD2, done |
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
- **OQ-5** Plan D12 (`inputHash`): drop from the contract, or keep byte-equal (LDH-6)?
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
