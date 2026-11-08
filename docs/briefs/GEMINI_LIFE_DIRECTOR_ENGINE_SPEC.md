# Brief: write the Life Director scheduling engine spec

For: an outside coding agent (Gemini). This brief is self-contained: read it once, then read the files it names.

## 1. What this is for

LifeMax is a single-user life RPG for Android. The web app (`lifemax_modular_v73`, plain JavaScript) owns every rule and
the save; native Kotlin screens only draw what the page sends. The rules are being rewritten in Kotlin one area at a time.
**We don't translate the JavaScript line by line.** Each area gets:
1. a written spec that states every rule precisely enough to reimplement it without reading the JavaScript;
2. golden input→output cases recorded from the real page;
3. fresh Kotlin that must reproduce every case.

Two areas are done and are the worked examples for yours: `docs/specs/ECONOMY_SPEC.md` (XP and the economy) and
`docs/specs/QUESTS_SPEC.md` (quests and the Daily Hand). **Read `ECONOMY_SPEC.md` in full before you start, and skim
`QUESTS_SPEC.md`.** Copy their structure, numbering style, precision and way of citing sources. Also read
`docs/AGENT_BRIEF.md` §1–§4 (the repository and its rules).

**Your task: the Life Director's scheduling engine.** You write a spec, not code. Our side records the golden cases and
writes the Kotlin from your spec afterwards, so every rule you miss or state loosely becomes a bug later.

## 2. The snapshot

- Repository `LifemaxRPG/Lifemax-rpg`, branch `codex/director-dial-controls`, commit **`0af4afe`** (or later if you are
  given a newer snapshot; then say which commit you read).
- Every `file:line` you cite refers to that commit. Paths are relative to `lifemax_modular_v73/src/features/life-director/`
  unless they start with `docs/` or `lifemax_modular_v73/`.

## 3. The area

The Life Director plans the player's day: it takes tasks, goals, quests, meals, sleep and availability rules and places
them on a timeline ("the plan"), repairs the plan when things change or are missed, and records what was done.

### 3.1 In: specify completely (`engine.js`, 2622 lines, the pure scheduler)

| Topic | Where to start |
|---|---|
| Time model: the slot grid, epochs, time zones, local days, daylight-saving days | `engine.js`: `cyrb53` :34, `_fmt` :68, `ymdKey` :132, `epochOf` :182, `SLOT_SEC` |
| Sleep: how the sleep window is derived from rules, days, preferences and hard intervals | `deriveSleep` :273 |
| Task preparation: tags and matching, per-task data the placer reads, goal info, burden/weight ("bwc") | `tagMatches` :470, `recomputeBwc` :820, `prepTask` :899, `freshIds` :768 |
| Quest cycles and coverage: which quests the plan must cover and how coverage is judged | `findQuestCycles` :590, `questCoverage` :1496 |
| Anchors and dependents (what moves when an anchored item moves) | `anchorsDependents` :1106 |
| State preparation: how input (`prepareState`) becomes the placer's working state | `prepareState` :1163 |
| Placement and repair: the placer, scoring, fragmentation, local repair loops, retire rules, tie-breaking, determinism | `runLocalRepair` :1309 and the placer functions around :899–:1900 (find them all; the hash at :34 is the tie-break/seed) |
| The ledger: its shape, what is recorded, the player id | `emptyLedger` :1908 |
| Missed-item detection and grace | `detectMisses` :2048 |
| Input validation: what is accepted, refused, repaired | `validate` :2259 |
| The engine's host surface: what the page calls in, what comes back | `LifeMaxHost` :2355 |

Cover **every function** in `engine.js`. A function you leave out must be named in §1 Boundary with the reason.

### 3.2 Contracts only (state who calls in, what goes out, and the effect on the save; don't specify the caller's internals)

`logic.js` (3925 lines) is the page-side host. It belongs to the next brief. For now specify only the **contract** it
has with the engine:
- what it passes to `LifeMaxHost` and how it stores the result (`planTimeChange` :144, `maybeRollover` :269,
  `repairEarlyDone` :351, `hostQuests` :102, `shifts` :2934);
- what it reads from quests and the economy (cite `QUESTS_SPEC.md` and `ECONOMY_SPEC.md` IDs; don't restate them);
- which `state.*` fields it reads or writes (types, defaults, who else writes them).

### 3.3 Out: name them in the boundary section with the reason

Everything visual: `view.html`, `style.css`, the dial drawing (`dialHtml`, `surfaceArc`, arcs and animation), sheets and
forms' markup, the tour, and the native projection code (`nativeRows`, `nativeDay`, `nativeForm`, `nativeDrop`) except for
the rules they take from the engine.

## 4. How to work

1. **Read the code; don't guess.** Every rule gets an ID (`LDE-n`, `SLP-n`, `PLC-n`, `REP-n`, `MIS-n`, `VAL-n` and so on)
   and a source (`file:line` or a range).
2. **Run what you can, and label everything.** You have Node. `engine.js` is pure, so load it into a Node `vm` sandbox and
   call the real functions with small hand-made inputs, the way the economy spec's author did. Then mark each rule:
   - **Executed:** you ran the real function and saw this result. Give the input and output.
   - **Read:** you derived it from reading the code only.

   Never write PASS, ✓ or "verified" for anything you did not execute. If you can't run something, say so plainly; that
   is fine.
3. **Pin the tricky semantics** as the economy spec's §0 does: rounding, integer vs float, time zones and daylight-saving
   changes (a 23-hour and a 25-hour day), midnight and week boundaries, sort stability and tie-breaks, iteration order of
   objects, the hash and any randomness (it must be reproducible), and limits (maximum iterations, maximum items).
4. **Determinism.** State exactly which inputs the output depends on. A plan must be the same for the same input; if it
   is not, that is a defect, report it.
5. **Defects.** Wherever today's behaviour looks like a bug or an accident, describe it as `LD01`, `LD02`… in a section of
   its own, with how to see it. Don't change the code, and don't specify the "fixed" behaviour: the spec describes what the
   app does today.
6. **Golden-case notes.** End with a section, like the economy spec's §21, for whoever records the golden cases: which
   inputs to vary, and what to hold fixed (time zone, "now", seeds, existing ledger) so a case is repeatable. Propose a
   list of at least 40 distinct cases covering sleep, placement, anchors, repair, misses, coverage, validation and DST.

## 5. What to deliver

1. **`docs/specs/LIFE_DIRECTOR_ENGINE_SPEC.md`** in the structure of `ECONOMY_SPEC.md`:
   - §0 How to read this;
   - §1 Boundary (in, contracts, out);
   - §2 Inputs and outputs (every field of the engine's input and output, with types and defaults);
   - constants;
   - the rules by topic;
   - the defects;
   - golden-case notes.

   Every rule should have an ID, a source and an Executed or Read label.
2. **`docs/specs/LIFE_DIRECTOR_ENGINE_SPEC_REPORT.md`**, short:
   - the commit you read;
   - every check you executed (the exact command or script, and its output);
   - what you could not run, and why;
   - your open questions;
   - anything in `ECONOMY_SPEC.md` or `QUESTS_SPEC.md` you think is wrong, with evidence.
3. **Optional:** the probe scripts you used, in `docs/specs/probes/life-director/`, runnable with `node <script>` from the
   repository root, loading `engine.js` without changing it.

**Do not:**
- change any code;
- touch any other file;
- write Kotlin;
- write model or product names into the documents.

If you work on GitHub, use a new branch named `gemini/life-director-engine-spec`, and **never push to
`claude/github-android-app-d3wlth` or `codex/director-dial-controls`**. Otherwise deliver the files in a zip.

## 6. How it will be checked

- About 30 rules are sampled against the code at your commit: each must say what the code does, with a correct source.
- Every function in `engine.js` must be covered, or excluded with a reason.
- Every rule marked Executed must be reproducible from the report or the probes.
- The golden-case recorder is then written from your spec. Rules it can't express, or cases where the page disagrees with
  your spec, come back to you as corrections.

A spec that says "Read" honestly is worth far more than one that claims checks it didn't run.
