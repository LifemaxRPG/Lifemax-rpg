# Native economy: audit and plan

Audit date: 2026-11-08. Branch audited: `codex/director-dial-controls` at `9cf6b97`. Read-only audit of the page
(`lifemax_modular_v73`), `docs/specs/ECONOMY_SPEC.md`, `docs/specs/QUESTS_SPEC.md` and the Kotlin under
`android-wrapper/native/src/main/java/com/lifemax/rpg/core/economy/`. The only thing run was
`node lifemax_modular_v73/tests/economy-golden.test.js`.

Goal: a native economy that is provably identical to the page, so that saving can later move from the page to native.

## 1. Summary

- The economy core is ported and checked case for case. All 880 golden cases recorded from the page are replayed by Kotlin
  (`EconomyGoldenTest`, one parameterised test per case, plus `EconomyGoldenCountTest`, which fails if a case in the file is
  not run). Nothing is skipped or loosened except `journalCoach.days/pats` (allowed by spec section 21).
- The golden JSON is read in place from `lifemax_modular_v73/tests/fixtures/economy-golden.json` by
  `core/golden/GoldenFiles.kt`; it is never copied, so there is one file for the page test and the Kotlin test.
- Drift: the spec text is stale only in places (citations and header), and no economy rule changed on the page after the
  spec. The page golden test passes at HEAD (10 of 10 checks, 880 of 880 cases).
- Nothing outside the core module uses it yet. The module is not wired to any screen. The save codec that turns the
  page's save JSON into an `EconomySave` and back is in `src/main` (`core/save`, WP1 done), also not wired to anything.
- Not ported: the Daily Hand's draw (seed, generator, pick, ensure, reroll replacement), quest content (default quests,
  class packs, library), the weekday filter, `luckToday`, and every adapter that supplies the context.

## 2. Native status against the spec

Status: complete = every rule of the section is implemented and replayed by golden cases; partial = some of it; missing = none.
Tests are the families of golden cases (count in brackets) replayed by `EconomyGoldenTest`, which is the only Kotlin
verification of the economy. There are no hand-written Kotlin unit tests for it.

| Spec section | Native status | File | Tests (golden families) |
|---|---|---|---|
| 0 Numbers, dates, strings | complete | `Numbers.kt`, `EconomyClock.kt` | time (11), zone (7), day (4), `dayNum`/`ecoDayKey` calls |
| 1 Boundary | n/a | - | - |
| 2 Inputs and outputs (ledger, save fields) | complete (WP1) | `EconomySave.kt`, `Activity.kt`; codec `core/save/EconomySaveCodec.kt` | load (3), save (4), derive (4); every case round-trips through the production codec (`SaveCodecGoldenTest`) |
| 3 Constants | complete | `Rules.kt` (`Eco`, `Rules`) | rules (4), `ECO` case |
| 4 Level curves | complete | `Levels.kt` | curve (63) |
| 5 Derived XP, ledger is the truth | complete | `EconomySave.rebuilt()` | derive (4), save (4) |
| 6 Scoring, rules, duration curve, preview | complete | `Scoring.kt`, `LogActivity.kt`, `Rules.kt` | preview (67), dur (26), log-clean (9) |
| 7 Award pipeline `ecoAward` | complete | `Scoring.kt` | award (57) |
| 8 Rested XP | complete | `Scoring.kt` / `EconomySave` (`restedPeek`, `restedTake`) | rested (40) |
| 9 Daily cap | complete | `Scoring.kt` (`cap`) | cap (76) |
| 10 Attribute split, ascension stars | complete | `Scoring.kt` (`starXp`) | stars (38) |
| 11.1 Log | complete | `LogActivity.kt` | log (50), log-clean (9) |
| 11.2 Quests (toggle, create, xp, locks) | complete as economy; no quest content | `Quests.kt` | quest (60) |
| 11.3 Health sync | complete behind an interface; Android adapter not wired to core | `Health.kt`, `HealthState.kt`; `bridge/web/HealthSyncPlugin.kt` exists separately | health (63) |
| 11.4 Voice past-log | complete | `Voice.kt` | voice (21) |
| 11.5 Life Director awards | complete for the two award entry points (`xpFor`, `awardActivity`, `completeQuest`); the Life Director engine itself is not in this module | `LifeDirector.kt` | director (59) |
| 11.6 Nutrition, cat care, money | complete | `Bonuses.kt` | nutrition (11), care (6), money (26) |
| 11.7 Plan, catalog, coach, dev boosts | complete | `FixedAwards.kt`, `Coach.kt` | plan (7), catalog (27), coach (11) |
| 11.8 Path matrix | covered by the above | - | - |
| 12 Sparks (credit, spend, buy, shop) | complete | `Sparks.kt` | sparks (33) |
| 13 Perks | complete | `Sparks.kt`, `Scoring.kt` | perk (4) |
| 14 Ascension | complete | `Ascension.kt` | ascend (6) |
| 15 Decay, rested accrual, rest days | complete | `Decay.kt` | decay (35), rest (7) |
| 16 Streak and shields | complete | `Decay.kt` | streak (8), shield (13) |
| 17 Save pipeline, take-back, boot, migration | complete as pure functions; boot order is the shell's | `SavePipeline.kt` | save (4), load (3), migrate (8) |
| 18 Undo and removal | complete | `SavePipeline.kt`, `FixedAwards.kt`, `Bonuses.kt` | undo (4) |
| 19 Numeric hazards | complete | `Numbers.kt` | covered across families |
| 20 Defects | reproduced as written (see section 6) | - | cases cite D-IDs |
| 21 Recording notes | n/a | - | - |

Also in the golden file: passive (7), fresh (1). The generator's own `NOT_RECORDED` list names the rules with no case and why
(for example D02, D12, D19, D25, D26, D27, D29; boot order; timers); the page test fails if that list and the spec disagree.

How native is verified today: `EconomyGoldenTest` runs inside the native unit tests that CI runs in
`build-android.yml` ("Verify native tests and screenshots"). It was not run during this audit (disk).

### Gaps in verification

1. The cases are recorded from the page and replayed in Kotlin; nothing checks a Kotlin result against a live page run
   with the same state. The recording is the only bridge. That is sound while the page test keeps the file true.
2. Cases use a fresh save plus small deltas. No case replays a long real history, and nothing runs the page and Kotlin
   side by side over a random sequence of actions. The page has `tools/run_tests.sh sim` (random seeds); Kotlin has no
   equivalent.
3. `fixtures/saves` (real saves) are not used by the economy golden test.
4. Not covered by any golden case and not in Kotlin: see section 4.

## 3. Spec drift since `6b88d5c`

The spec says it describes `6b88d5c` (31 Oct 2026). Between `6b88d5c` and the spec commit (`71790c5`) only the spec changed.
After the spec, three more commits updated the spec and cases (`52e91d2`, `2eab396`, `f2a488a`, `3ba07e4`, `949f9d0`), the
last putting the Journal coach's new reward rule into section 11.7.3 and into Kotlin (`Coach.kt`). Since `6b88d5c` about
430 commits landed; those touching economy code are listed below.

No behavioural change after `6b88d5c` is missing from the spec or the golden file. Evidence: the page test re-records all 880
cases from the current page and finds no difference; `43-economy.js` has not changed at all since `6b88d5c`.

| Change after `6b88d5c` | Economy effect | Reflected in spec / golden? |
|---|---|---|
| `0f9026c`, `699ff1f`, `8a3494e` Journal coach: words stored first, reward paid in a save of its own; welcome rewards pay only the offered chip; `journalSaveReflection` / `journalSaveReview` return `{ok, changed, paid, message}` | Yes, for the reward path | Yes: spec 11.7.3 (updated in `949f9d0`), golden `coach` family, `Coach.kt` |
| `ebb47ab` writers that claimed success on a failed save now take back (Media, Food `fv2Log`, import) | No award changes. Moves D02's neighbourhood (Food log) but not the unreachable nutrition bonus | Not an economy rule; D13 list is unchanged and still accurate for award paths |
| `fbb422d` Life Director "Log it for today" notice wording and `xpWords` table | Wording of the notice only | Messages come from the host adapter outside section 11.5; the golden `director` family is unchanged |
| `ce25ffa`, `0d0c0c2`, `97d301c`, `78c5bdb`, `f76ea07`, `b44734d` Life Director completes timed sessions automatically; untimed cards; midnight advance | A new trigger for the existing award entry points (`awardActivity`, `completeQuest`), no new award rule | Spec 11.5 says the trigger is outside; award rules unchanged. The trigger is Life Director logic phase work |
| `858c2a7` Life Director engine moved from `logic.js` to `engine.js`, unchanged | None | Citations only: spec cites `logic.js:2487-2521` and similar; the code is now in `engine.js` (for example `H.awardActivity` at `engine.js:2523`). Citations need refreshing |
| Overview refactors (`ovRewardsData`, `ovHealthData`, `ovLuckData`, `ovTileAdd`, `04-runtime`/`44-rewards`/`39-health`/`18-runtime`) | None (presentation as data) | Not economy. Only the Rewards tile and Health tile read the same fields |
| `06-runtime` plan offers (`planOfferHtml`, `startPlan(start)`, `leavePlan(sure)`) | None to plan-session XP | Section 11.7.1 unaffected; golden `plan` unchanged |
| `39-health` settings row label (`aria-label`) | None | D12 text unchanged |

Spec upkeep items (no behaviour):

1. Header still says "exactly as at `6b88d5c`"; it should say it is kept true by the golden test and name the page commit of
   the last regeneration.
2. Citations into `logic.js` (D06, D07, D19, sections 11.5 and others) should point to `engine.js` / `logic.js` as they now
   stand, or be replaced by function names.
3. The page's `QUESTS_SPEC.md` was reviewed at `3eeaa1e`. Since then only `18-runtime.js` of the files it cites changed
   (Overview "data" refactor, no change to `luckToday` itself); the rules are intact. It has no golden file.
4. Proposed drift guard: a short script (CI, page side) that fails when a file the spec cites changes without the spec's
   "last checked at" line moving. The golden test already catches changes on covered paths; this catches changes to
   uncovered paths such as the Daily Hand.

Page-side test result: `node lifemax_modular_v73/tests/economy-golden.test.js` passes, 10 of 10 checks (880 of 880 cases,
110 rule IDs, 37 defects).

## 4. What the native Quest tab needs beyond what exists

Already in Kotlin: quest done/undone keys (QUEST-1), `toggle` (check, uncheck, locks, 3-a-day and rolling-week cap, epic once
a day, Compound, rested XP, D05, D09, D16, D17), custom quest creation (QUEST-4), `DailyHand.verify` (auto-verify after every
save, SAVE-2), `DailyHand.isMet` for every check scheme, `rerollCost` and `reroll` (free once, then 50 Sparks, "Finished cards
cannot be rerolled"). All replayed by the `quest` and `sparks` families.

Needed and missing:

| Need | Page source | Native today |
|---|---|---|
| Daily hand draw: `dhSeed`, `dhRng` (32-bit `Math.imul` hash and generator), `dhLuckScore`, `dhPick` weights, `dhDraw`, `dhWeakestAttr`, `dhRecentAttrXP`, rest-day rule, three slots (maintenance, class, wildcard) | `50-quests-hand.js:63-92` | missing |
| `dhEnsure` (today's three cards, drop yesterday's, `dhMig` migration, `dailyDraw` state) and the replacement draw inside `dhReroll` (nonce, taken templates) | `50-quests-hand.js:93-125` | missing; Kotlin `reroll` takes the card's quest id and leaves drawing to the caller ("outside the economy") |
| Quest library (`QUEST_LIBRARY`), `DH_CLASS_ATTR`, `defaultQuests`, `CLASS_CONTENT`, class packs and `applyClassContent` | `50-quests-hand.js:8`, `00-runtime.js:31` | data exists in `core/quests` (WP2: `QuestContent`, read from the page's export); the rules that use it (draw, `applyClassContent`) are missing |
| `luckToday` score (the hand's luck input), 5500 fallback | `18-runtime.js:128` | missing (native Character has a luck screen, not shared with this module) |
| Weekday filter and period keys for a day, quest board projection (which quests show, "available XP", nutrition epic, unknown cadence QD11) | QUESTS_SPEC 3.3, 6 | missing (key per period exists) |
| Claim: there is no separate claim step. Checking a card is `toggle(checked = true)`; auto-verify claims it. "Claim" in the economy sense (coach chips) is `Coach.claim`, which exists | - | exists |
| Reroll cost display (`dhRerollCost`) | | exists (`rerollCost`) |
| 3-a-day cap and its refusal toast | `43-economy.js:57` | exists (`Quests.xpFor`, `complete`) |
| Quest XP shown on completion, board celebration reports listed XP (D20) | `14-runtime.js` | the XP paid is returned; the celebration is UI |
| Hand facts supplied to verify: water percent, pet counts, plan done | `dhVerifyMet` | `DailyHandFacts` exists; the adapter that fills it from real state does not |
| Production save codec | - | exists (`core/save`, WP1) |
| Hand card UI state (`turned`, `up`, reveal, deal mode) | `50-quests-hand.js:156-240` | missing; presentation, stored in `dailyDraw`, must be preserved on write |

## 5. Plan

Order is by dependency, then risk. Sizes: S under a day, M two to four days, L about a week or more. "Decision" marks work
that needs an owner ruling before it starts. Every package ends with: the golden file extended or a new golden file, the
page test regenerating it, the Kotlin test replaying it, both green in CI.

### WP1. Production save codec with unknown-field preservation (M, risk high)

Scope: move `SaveCodec.kt` from `src/test` to `src/main` as the one place that turns the page's save JSON into `EconomySave`
and back. It must carry every field the native economy does not model (round-trip keeps them byte-for-byte in value),
because once native writes the save, losing a field the page owns is data loss. Define the TRACKED field list in main, not in
the test.
Tests: decode then encode is the identity for the starting state of all 880 cases and for every file in
`tests/fixtures/saves`; property test over random JSON for unknown keys; number round-trip cases for `NaN`, `-0`, large doubles
(spec 19).
Golden cases to add: none new; a `codec/roundtrip` family recorded from the page's own `JSON.stringify(state)`.
Decision: none.

**Status: done** (not wired to anything; the page is still the only writer). What exists:

- `android-wrapper/native/src/main/java/com/lifemax/rpg/core/save/`: pure Kotlin, no `org.json`.
  - `JsonValue.kt`, `JsonReader.kt`, `JsonWriter.kt`: JSON exactly as `JSON.parse` and `JSON.stringify` see it. Numbers keep
    their text (`-0`, `1e400`, `1.0`, 20-digit integers come back byte for byte); a computed number is written as
    `Number.prototype.toString` writes it (448 page-recorded doubles agree); strings escape as `JSON.stringify` (lone
    surrogates as `\udxxx`); a repeated key keeps its first place and last value; nesting is capped at 512 and refused beyond.
  - `SaveFields.kt`: `SaveFields.TRACKED` (the 43 top-level keys the economy models) and `SaveFields.PARTIAL` (the nested
    keys it models inside `mealsMeta`, `foodLog`, `money`, `plan`, `health`, `dailyDraw`, `journalCoach`); the refusal types.
  - `EconomySaveReader.kt`, `EconomySaveWriter.kt`: the tracked fields to and from `EconomySave`, read as the page reads them
    (NUM-3) and written in the page's shapes.
  - `JsonMerge.kt`: the three-way merge that writes only what the economy changed over the stored save. Records with an `id`
    (activities, quests, money transactions) are followed by id, so a changed record keeps its own extra keys.
  - `EconomySaveCodec.kt`: `decode(text | json, reading) -> Decoded(save, document) | Refused(refusal)` and
    `encode(document, save) -> Encoded(text, document) | Refused(refusal)`. Never throws. Encoding unchanged returns the stored
    text itself; every encode reads its own output back and refuses (`NOT_FAITHFUL`) unless the economy reads back exactly
    and every untracked top-level field is identical.
- Strictness: `SaveReading.STRICT` (default) refuses a save whose tracked field has a JSON type the page never writes there
  (`WRONG_TYPE`, naming field and path, `*` for owner-chosen keys, never a value); `null` is accepted wherever the page can
  write it (`JSON.stringify(NaN)`). A NaN or infinity the economy put where a stored number was is refused
  (`NON_FINITE_NUMBER`) rather than stored as `null`. `SaveReading.AS_PAGE` reads every value as the page does and writes
  NaN as `null`, as the page would; it exists to replay the page on the golden cases' deliberately malformed saves (and later
  shadow mode) and must not decide what native writes.
- Tests (`src/test/.../core/save/`): `SaveCodecGoldenTest` (all 880 starting states and every `env.stored` save: identity,
  repeatability, a change to every kind of tracked field keeping every other value; the strict reading refuses exactly the 10
  cases that store a malformed tracked value, at the expected paths), `SaveFixturesTest` (the four `fixtures/saves` and the
  page-recorded family), `SaveCodecFuzzTest` (seeded random trees, number lexemes, mangled text, 300 saves sprinkled with
  random untracked keys), `SaveCodecStrictnessTest`, `SaveCodecCoverageTest` (every `EconomySave` field written and read
  back; a new model field fails it). `EconomyGoldenTest` now runs through the production codec; the test-side `SaveCodec`
  is a thin adapter between plain values and `JsonValue`.
- Page side: `tools/gen_codec_roundtrip.js` writes `tests/fixtures/codec-roundtrip.json` (doubles and strings with their
  `JSON.stringify` text, and what the page stores for the new and extras saves and a probe save full of JSON corner cases);
  `tests/codec-roundtrip.test.js` records it again and fails on any difference. No page code changed.

### WP2. Page-driven content export for quests (S to M, risk low)

Scope: instead of hand-copying `QUEST_LIBRARY`, `CLASS_CONTENT`, `DH_CLASS_ATTR`, default quests, tier weights, a generator
writes them to `tests/fixtures/quest-content.json` from the page, and Kotlin loads them from a resource generated at build
time or checked in with a test that fails when it differs from the page. Replaces `GoldenContent.kt`.
Tests: a page test that regenerates the file and compares; a Kotlin test that parses it and checks counts and ids.
Decision: none.

**Status: done** (not wired to anything). What exists:

- Page side: `tools/gen_quest_content.js` loads the built page in Chromium and writes `tests/fixtures/quest-content.json`
  (schemaVersion 1, a SHA-256 content hash, counts, one entry per line): `ATTRS` order, `DH_FREE_REROLLS`, `DH_REROLL_COST`,
  `DH_CLASS_ATTR`, the 53 `QUEST_LIBRARY` templates, `defaultQuests`, `stageLvl(0..5)`, `CLASSES` ids and readiness, and the 131
  `CLASS_CONTENT` quests of the four ready classes (as the page has them after all its segments ran). It also writes the same
  bytes to `android-wrapper/native/src/main/resources/quests/quest-content.json`, the copy the app reads.
  `tests/quest-content.test.js` records the content again from the page and fails on any difference from the fixture, and fails
  when the packaged copy is not the same bytes.
- Kotlin (`core/quests`): `QuestContent` (typed content) and `QuestContentLoader` (strict decoder, no exceptions: unknown or
  missing key, wrong kind, tier outside 1 to 3, fractional or negative XP, unknown attribute, duplicate template, quest or class
  id, count or hash mismatch, other schema version all give `Refused(path, reason)`). `QuestContentTest` checks the packaged
  resource equals the fixture byte for byte, counts, ids, verify kinds, class attribute coverage, agreement with `Attribute` and
  `Sparks.REROLL_PRICE`, and one refusal per kind of damage. `GoldenContent.kt` now takes the default quests from the loader.
- Not in the file, because the page keeps them in code: the tier odds and luck constants inside `dhPick`, the slot order, and the
  migration patterns (`q1`-`q3` and `cc_*` in `dhEnsure`, `q1`-`q6` and `cc_*` in `applyClassContent`). WP3 and WP4 accept them
  by golden cases. `GoldenContent.kt` still holds the attribute-sheet catalog entries, plan links and plan days (not quest
  content) and the save schema number.

### WP3. Daily Hand draw in Kotlin (M, risk medium)

Scope: `dhSeed`, `dhRng`, `dhLuckScore`, `dhPick`, `dhDraw`, `dhWeakestAttr`, `dhRecentAttrXP`, `dhEnsure`, `dhMakeQuest`, and
the replacement draw in `dhReroll`; `luckToday` input as a plain value in the context. Float and 32-bit hazards: `Math.imul`,
`>>>`, division by 4294967296, tier weights with `luck` as a double, `toLocaleDateString('en-CA')` as the day key, tie order of
`ATTRS`.
Tests, new golden file `quests-golden.json` (page generator `tools/gen_quests_golden.js`, same pattern as the economy one),
following QUESTS_SPEC section 13: seed and first five `dhRng` outputs; `dhDraw` for every slot, nonces 0 to 3, rest day and
active day, each class and none, luck 1000, 5500 fallback and 9999; reroll free, 50 Sparks, 49 Sparks, done card, three-template
exclusion, instant auto-verify, stale turn; `dhEnsure` across local midnight, class switch (QD02), migration `dhMig`.
Decision: whether to keep QD02, QD15, QD16 (OQ-8 in QUESTS_SPEC). Default: keep page behaviour.

### WP4. Quest board, period keys and weekday filter (S to M, risk low)

Scope: which quests show today, period keys per day, the board and summary projection (QUESTS_SPEC sections 3.3 and 6),
custom quest XP rules (QD07, QD08 kept), locked epics.
Tests: `quests-golden.json` families for weekday filter, stage locks at each threshold, class packs and migration, custom
quest refusal and clamping, board projections including QD11.
Decision: QD11 (a nutrition epic shows twice; unknown cadence shows nowhere): keep.

### WP5. Context adapters for the real app (M, risk medium)

Scope: build `EconomyContext` from the real device: clock and zone, `DailyHandFacts` (water percent, pet counts, plan done) from
their own native sources, Health Connect adapter implementing the `Health` interface over the existing `HealthSyncPlugin`,
journal coach store. No writes.
Tests: fake adapters run the existing golden `health` and `quest` cases through the adapter layer (not only the pure
functions); a clock-change and time-zone case family (`zone`, `time`) through the adapter.
Decision: none.

### WP6. Shadow mode: native computes, page still writes (L, risk medium)

Scope: with the codec and adapters in place, on every page save native decodes the saved state, replays the same action and
logs any difference in the result and in the next save (ledger, `attrXP`, `skillXP`, `restedXP`, `sparks`, `questDone`).
Needs a command log of page actions (the page already routes Log, Life Director, quests, health through a few entry points).
Add a randomised differential test: the page's `sim` seeds produce action sequences; Kotlin replays them; states must match
after every step.
Tests: differential replay of at least the 40 sim seeds the page already runs, plus the real saves in `fixtures/saves`.
Decision: yes, a human decides what mismatch rate blocks cutover (target zero).

### WP7. Life Director logic (L, outside this plan's scope)

The award entry points are done (`LifeDirector.kt`). The engine (`engine.js`, `logic.js`, about 4,600 lines changed since the
spec baseline, plus automatic completion of timed sessions) needs its own spec and golden family before it can write.

### WP8. Cutover: native becomes the only writer (L, risk high)

Scope: after WP6 shows zero differences over a trial period, native saves; the page reads. Includes the take-back policy
(D13), migration (D22) and boot (D29) behaviour as owner-decided.
Tests: the whole golden set plus WP6 differential on every build.
Decision: required (section 6).

### Parallel housekeeping (S, low risk)

- Refresh the spec header and `logic.js` citations; add a "last checked at" line (section 3).
- Add the drift guard.
- Add Kotlin unit tests only where the golden file cannot reach (codec failures, malformed JSON).

## 6. Defects (spec section 20) that need an owner ruling

Rule: keep page behaviour unless the owner decides otherwise. Native reproduces all of them today. Rulings are needed where
moving to native forces a choice, where the defect is exploitable, or where the owner might not want it carried forward.

Forced by the move to native (a choice is unavoidable):

- **D13** failed save is not taken back for quests, voice, health, nutrition, care, money, plan, catalog, shop, rest days
  (memory keeps the attempt). Native writes will be atomic. Ruling: reproduce (attempt survives to the next save) or take
  back everywhere.
- **D15, D16** in-memory cache edits and saving when nothing changed. Native has no mutable cache; D16 changes how often a
  save and auto-verify run. Ruling: keep "toggle always saves" or save only on change.
- **D17** id collisions (`quest_`, `nut_`, `care_`, `act_` built from the millisecond clock). Native could add a counter. Ruling:
  keep (identical ids) or make unique.
- **D22, D29** migration and boot save. Native boot order is its own. Ruling: where decay is applied and written (D21, D29
  follow from this).

Exploitable or plainly wrong; owner should decide whether to fix:

- **D01** step days paid again (days 22-30). Real XP inflation.
- **D14** Sparks never revoked: toggling a quest or deleting and re-logging pays Sparks each time.
- **D26** paycheck deposits do not move `peakBalance`: money can be withdrawn and deposited for XP.
- **D37** a workout over about two hours logged by hand is paid twice (Health).
- **D06, D07** days can end above 240 XP (stars and the Life Director execution bonus after the cap).
- **D08** uncapped awards count toward the cap total, so logging order changes pay.
- **D34** passive health data drains rested XP; **D35** quests, nutrition, care, deposits keep attributes from decaying.
- **D05, D03, D04** rested XP spent when the cap pays almost nothing; fixed flag lost.

Choice between page text and page rule:

- **D12** health settings text vs rules (steps 3 XP per 1,000 vs 1.5; sleep from 3 h vs 5.5 h). Owner picks which is right;
  the other is changed.
- **D02** nutrition bonus unreachable. Owner decides: delete it from native, or wire it.
- **D27, D36** hard-coded numbers in messages, "7h 60m" naming. Cosmetic.

Quest-side (QUESTS_SPEC): **QD02** class switch keeps today's card, **QD07** custom XP not rounded, **QD15** new day's draw
not saved until something else saves, **QD16** reroll keeps the `turned` flag, **QD17** shorter sessions do not add up.
OQ-8 asks the same question; recommended default is to reproduce all five for golden parity first, then fix behind a flag.

Not needing a ruling (no behaviour or already moot): D09 is kept; D10, D11, D18, D19, D20, D21, D23, D24, D25, D28, D30-D33.

## 7. Recommended first work package

WP1, the production save codec, with the content export (WP2) beside it. Reasons: everything else (adapters, shadow mode,
the Quest tab, cutover) reads or writes the save through it; it has the highest cost of an error (silent data loss when native
becomes a writer); it needs no owner decision; and its tests are almost entirely already recorded (880 states plus real
saves).
