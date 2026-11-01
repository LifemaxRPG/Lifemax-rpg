# Brief: write the quests and Daily Hand spec

For: an outside coding agent (Gemini). This brief is self-contained: read it once, then read the files it names.

## 1. What this is for

LifeMax is a single-user life RPG for Android. The web app (`lifemax_modular_v73`, plain JavaScript) owns every rule and
the save; native Kotlin screens only draw what the page sends. The rules are being rewritten in Kotlin one area at a time.
**We don't translate the JavaScript line by line.** Each area gets:
1. a written spec that states every rule precisely enough to reimplement it without reading the JavaScript;
2. golden input→output cases recorded from the real page;
3. fresh Kotlin that must reproduce every case.

The first area, XP and the economy, is done. Its spec is the worked example for yours:
`docs/specs/ECONOMY_SPEC.md`. **Read it in full before you start.** Copy its structure, its numbering style, its precision
and its way of citing sources. Also read `docs/AGENT_BRIEF.md` §1–§4 (the repository and its rules).

**Your task is step 1 for the next area: quests and the Daily Hand.** You write a spec, not code. Our side records the
golden cases and writes the Kotlin from your spec afterwards, so every rule you miss or state loosely becomes a bug later.

## 2. The snapshot

- Repository `LifemaxRPG/Lifemax-rpg`, branch `claude/github-android-app-d3wlth`, commit **`3eeaa1e`** (or later if you are
  given a newer snapshot; then say which commit you read).
- Every `file:line` you cite refers to that commit. Paths below are relative to `lifemax_modular_v73/src/runtime/segments/`
  unless they start with `docs/` or `lifemax_modular_v73/`.

## 3. The area

### 3.1 In: specify completely

| Topic | Where to start |
|---|---|
| The quest model: fields, cadences (daily, weekly, epic, others you find), stages, kinds, the `hand` flag, milestones | `04-runtime.js` (`questKey` :86, `isQuestDone` :87, `toggleQuest` :239), `13-runtime.js` (`stageLvl` :272, `questLocked` :273), `freshState` and the default quests |
| Periods: when a daily or weekly quest resets, what "done" means per period, how `questDone` keys age | `questKey`, `isQuestDone`, every reader of `state.questDone` |
| Stage locks: which level opens which stage, what a locked quest refuses | `stageLvl`, `questLocked`, callers |
| Creating, editing, deleting and undoing quests (custom quests), and their limits and defaults | `createQuest` and the quest board's handlers (find them: `14-runtime.js` around `renderQuests` :34, `qosToggle`) |
| The quest board's order and grouping: what is shown, in what order, and why (the native screen will need it) | `renderQuests` (`14-runtime.js:34`) and its helpers. Rules only, not markup |
| The Daily Hand: the daily deal of three cards, the seed and random generator, which template each slot draws and why (class attributes, weakest attribute, luck score, recent XP), card tiers, `dhMakeQuest` | `50-quests-hand.js`: `dhSeed`, `dhRng`, `dhLuckScore`, `dhRecentAttrXP`, `dhWeakestAttr`, `dhPick`, `dhClassAttrs`, `dhDraw`, `dhMakeQuest`, `dhEnsure`, `dhCards`, `QUEST_LIBRARY` (:8) |
| Rerolls: free rerolls, cost, what a reroll changes and what it keeps | `dhRerollCost` (:116), `dhReroll`, `DH_FREE_REROLLS`, `DH_REROLL_COST` |
| Card verification: every `verify` kind (`self`, `none`, `act:<type>:<min>`, `any:<min>`, `water:<pct>`, `pet:<feed\|pet\|play>`, `plan`, and any others), which records count, and auto-verification inside saves | `dhVerifyMet` (:133), `dhAutoVerify` |
| The one-time Daily Hand migration (`dhMig`) and anything else that rewrites `state.quests` | `dhEnsure` and its callers |
| The deal's timing rules (when a new hand is due, what "the day" is), not its animation | `dhSchedDeal`, `dhStage`, `dhTick`, `dhIsUp`, `dhTurn`, `dhReveal`: state changes only |
| The Ledger's content: which quests, records and badges it lists, in what order | `50-quests-table.js` from about :357 (`renderLedger`): the data and order, not the drawing |

### 3.2 Contracts only (state who calls in, what goes out, and the effect on the save; don't specify the caller's internals)

- **Quest XP and its limits.** These are already specified as `QUEST-1`…`QUEST-5`, `CAP-*` and `REST-*` in
  `ECONOMY_SPEC.md`. **Cite those IDs; do not restate or change them.** If you find they are wrong, report it (§5), don't
  fork them.
- **Life Director's quest bridge** (`lifemax_modular_v73/src/features/life-director/logic.js`: `H.readQuests`,
  `H.completeQuest`, `H.isMilestone`, quest overlays): what Life Director reads from quests and what it may change.
- **Workout plan** (`dayDone`, `verify: 'plan'`), **pet care** (`petData`, `petDay`), **water** (`state.waterLog`,
  `mealsMeta.waterGoalMl`): only the values the quest rules read.
- **Badges and level-ups** triggered by quest completion: only that they are triggered, and from where.

### 3.3 Out: name them in the boundary section with the reason

- Everything visual: `50-quests-art.js`, `50-quests-cards.js`, `50-quests-cine.js`, `50-quests-fx.js`, `50-quests-gate.js`
  (the deal's gate dialog), the 3D table's drawing and physics in `50-quests-table.js`, and the HTML of the board and the Hand.
- `50-quests-table-data.js` and `QUEST_LIBRARY`'s text: content, not rules. Describe their shape and how rules read them;
  don't copy the entries out.

## 4. How to work

1. **Read the code; don't guess.** Every rule gets an ID (`QST-n`, `HAND-n`, `VER-n`, `LEDG-n` and so on) and a source
   (`file:line` or a range).
2. **Run what you can, and label everything.** You have Node. Where practical, load the real segment sources into a Node
   `vm` sandbox with small stubs (`state`, `getToday`, `activityDate`, `toast`, `save`, `renderAll`) and call the real
   functions, the way the economy spec's author did. The seeded deal (`dhSeed`, `dhRng`, `dhDraw`) is the most important
   thing to run. Then mark each rule:
   - **Executed:** you ran the real function and saw this result. Give the input and output.
   - **Read:** you derived it from reading the code only.

   Never write PASS, ✓ or "verified" for anything you did not execute. If you can't run something, say so plainly; that
   is fine.
3. **Pin the tricky semantics** as the economy spec's §0 does: rounding, local dates against UTC, `Date` arithmetic, string
   comparison of dates, what happens at midnight and across a daylight-saving change, and the seed's exact inputs.
4. **Defects.** Wherever today's behaviour looks like a bug or an accident, describe it as `QD01`, `QD02`… in a section of
   its own, with how to see it. Don't change the code, and don't specify the "fixed" behaviour: the spec describes what the
   app does today.
5. **Golden-case notes.** End with a section, like the economy spec's §21, for whoever records the golden cases: which
   inputs to vary, and what to seed or hold fixed so a case is repeatable (the date, the class, the luck roll, the deal's
   seed, existing records).

## 5. What to deliver

1. **`docs/specs/QUESTS_SPEC.md`** in the structure of `ECONOMY_SPEC.md`:
   - §0 How to read this;
   - §1 Boundary (in, contracts, out);
   - §2 Inputs and outputs (every `state.*` field read or written, with types);
   - constants;
   - the rules by topic;
   - the defects;
   - golden-case notes.

   Every rule should have an ID, a source and an Executed or Read label.
2. **`docs/specs/QUESTS_SPEC_REPORT.md`**, short:
   - the commit you read;
   - every check you executed (the exact command or script, and its output);
   - what you could not run, and why;
   - your open questions;
   - anything in `ECONOMY_SPEC.md` you think is wrong, with evidence.
3. **Optional:** the probe scripts you used, in `docs/specs/probes/quests/`, runnable with `node <script>` from the
   repository root, reading the segments without changing them.

**Do not:**
- change any code;
- touch any other file;
- write Kotlin;
- write model or product names into the documents.

If you work on GitHub, use a new branch named `gemini/quests-spec`, and **never push to `claude/github-android-app-d3wlth`**.
Otherwise deliver the files in a zip.

## 6. How it will be checked

- About 30 rules are sampled against the code at your commit: each must say what the code does, with a correct source.
- Every function named in §3.1 must be covered, or excluded with a reason.
- Every rule marked Executed must be reproducible from the report or the probes.
- The golden-case recorder is then written from your spec. Rules it can't express, or cases where the page disagrees with
  your spec, come back to you as corrections.

A spec that says "Read" honestly is worth far more than one that claims checks it didn't run.
