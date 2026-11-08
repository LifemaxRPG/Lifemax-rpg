# Economy shadow mode (WP6): design and plan

Written 2026-11-08 on `codex/director-dial-controls`. WP6a and WP6b are built (their status is under each, section 6); the rest is
design. It covers WP6 of
`ECONOMY_NATIVE_PLAN.md` and fixes the transport that WP-LD12 (`LIFE_DIRECTOR_NATIVE_PLAN.md` 5.1) will reuse.

Inputs: `ECONOMY_SPEC.md` (sections 2, 11, 15-17, 20, 21), `QUESTS_SPEC.md`, `ECONOMY_NATIVE_PLAN.md` (WP1-WP8, defects),
`LIFE_DIRECTOR_NATIVE_PLAN.md` (5.1, 5.2, WP-LD7), `CONTRACT.md` (A10-A15, A20), ADR 0004, `DEMO_MODE.md`, `BRIDGE_CATALOG.json`,
`MIGRATION.md` (the `world` command and its outbox), and the code: `core/save` (WP1), `core/economy`, `core/quests` (WP2-WP4),
`core/adapters` (WP5: `DeviceClock`, `ContextFactory`, `SaveHolder`, `HandFacts`, `HealthAdapter`, `CoachStore`, `PageDays`), the
page's `04-runtime.js` (`save`, `writeSave`, `takeBack`, `load`), `13-runtime.js` (boot), `14-runtime.js` (`renderQuests`),
`39-health.js`, `50-quests-hand.js`, `engine.js` (`LifeMaxHost`), `50-native-audio.js` and `NativeAudioPlugin.kt` (a plugin that is
not a tab).

## 0. What shadow mode is, in one paragraph

The page stays the only writer and the only thing the owner sees. While a new Settings switch is on, the page tells native, for
each economy action it performs, what the tracked state was just before, what was asked, what the page read from outside (the
storage outcome, Health Connect answers, a confirm), and what the tracked state, return value and toasts were just after. Native
runs the same action on the same before-state with its own Kotlin (WP1-WP5), compares, and keeps a small, redacted, on-device
count of agreements and differences. Native never writes the save, never sends a command, never shows a computed value, never
uses the network. This is the evidence for WP8. CONTRACT A10: "Shadow outputs cannot award rewards or persist a second
authoritative save." Nothing in this design does either.

## 1. What is compared

### 1.1 The unit: one outermost action

An action is one call of a page entry point (a "shadow point", table 1.2), from entry to return, including everything it causes
synchronously: its `save()`, the `renderAll()` inside that save, the Daily Hand's `dhAutoVerify` (both the one in
`renderQuests` and the one in the SAVE-2 wrapper), nested `toggleQuest` calls and their saves, and a take-back. Shadow points
nest: only the outermost one produces an envelope; inner ones (the `toggleQuest` inside an auto-verify, the `save()` inside a
Log) are part of it. Reason: native must reproduce the composition the page performs, not only the pure functions. That
composition (core save, then the render's auto-verify with `dhEnsure`, then the wrapper's auto-verify, guarded by `dhBusy`) has
no golden family today (WP3 left "composing the two" to WP6), and it is exactly what a native writer must get right at WP8.

Alternatives rejected:
- *Per pure function* (each `ecoAward`, `ecoRestedTake`): already proven by 880 + 842 + 1011 golden cases; adds nothing a real
  save can show except volume, and misses the composition.
- *Per persisted save* (compare consecutive `writeSave` texts): other page code (food, plan ticks, hand card flags, journal
  redraw) edits tracked fields in memory between saves, and a failed save (D13) leaves its attempt in memory only. A difference
  could not be attributed to one action, and actions that do not save (QD15, a refused save) would be invisible. Kept as a
  cross-check only (1.4, `persist` counter).

### 1.2 Shadow points

`kind` is the envelope's name for the action. "Native" is the call the shadow runner makes (all exist today unless marked).

| kind | page entry (wrapped global) | native | env the page records |
|---|---|---|---|
| `log.record` | `recordActivity(d)` | `LogActivity.submit` / `record` (LOG-1..3, opId repeat) | storage outcome |
| `log.delete` | `deleteActivity(id)`, `removeAct`, `undoCat` | `SavePipeline.deleteActivity`, `FixedAwards.undo` (UNDO-1) | `confirm` answer |
| `voice.log` | `lmvLogNow(o)` | `Voice.logNow` (11.4) | storage outcome |
| `quest.toggle` | `toggleQuest(id, checked)` | `Quests.toggle` (QUEST-2/3, D05, D09, D14, D16, D17) | storage outcome |
| `quest.create` | `createQuest()`, `dhRest.create` | `Quests.create`, `CustomQuests` (QUEST-4, QD07, QD08) | `dom`: the form's values |
| `hand.ensure` | `dhEnsure()` when outermost (render) | `HandDealer.ensure` (QUESTS_SPEC, QD02, QD15) | `plan`, luck in the save |
| `hand.reroll` | `dhReroll(slot)` | `HandDealer.reroll` + `DailyHand.reroll` (SPARK-5, QD16, QD22) | storage outcome |
| `hand.verify` | `dhAutoVerify()` when outermost | `DailyHand.verify` composed (SAVE-2) | `plan.done`, water, cat |
| `health.sync` | `lmaHealthSync(manual)` (async) | `Health.sync` over a recorded port (11.3, D01, D10, D11, D34, D37) | `health`: every plugin answer |
| `director.award` | `LifeMaxHost.awardActivity`, `.completeQuest` | `LifeDirector.awardActivity`, `completeQuest` (LD-2..4, D06, D07, D18) | storage outcome |
| `bonus.care` | `ecoCare(kind)` | `Bonuses.catCare` (11.6.2, D03, D04) | storage outcome |
| `bonus.nutrition` | `ecoNutritionCheck()` | `Bonuses.nutrition` (unreachable, D02: coverage-exempt) | - |
| `money.record` / `money.remove` | `recordMoneyTransaction`, `removeMoneyTransaction` | `Bonuses.recordMoney`, `removeMoney` (11.6.3, D26) | storage outcome |
| `fixed.plan` | `planToggle(date, key, i, checked)` | `FixedAwards.tickPlan` (11.7.1) | `plan` |
| `fixed.catalog` | `confirmAct(id)` | `FixedAwards.confirm` (11.7.2) | storage outcome |
| `coach.pay` | `jcClaim(kind)`, `journalSaveReflection`, `journalSaveReview`, `jcCatchUp` | `Coach.claim`, `reflect`, `saveReview`, `catchUp` (11.7.3) | storage outcome |
| `spark.buy` | `ecoBuy(kind, id)` | `Sparks.buy` (12, 13, 16 shields) | storage outcome |
| `ascend` | `ecoAscend(id)` | `Ascension` (14) | storage outcome |
| `rest.take` | `ecoTakeRest()` | `Decay.takeRest` (REST-DAY-2) | storage outcome |
| `rules.save` | `saveRules`, `resetRules`, `saveBase`, `saveDecay` | `SavePipeline.saveRuleBases`, `resetRules`, `Decay.saveSettings` | `dom` |
| `decay.apply` | `applyDecay()` on `visibilitychange` | `Decay.apply` incl. `shieldTick` (DECAY-3..5, SHIELD-2, D21, D28, D35) | `plan` (rest days) |
| `boot.load` | `load()` (via the boot hook, 2.6) | `SavePipeline.load` + migrations (SAVE-5, MIG-1..6, D22) | stored text as base |
| `boot.decay` | the boot's `applyDecay()` + `writeSave()` | `Decay.apply` then a plain write (SAVE-6, D29) | `plan` |
| `save.other` | a bare `save()` with no shadow point around it | `SavePipeline.save` composed (SAVE-1, SAVE-2, MIG, Sparks sync) | storage outcome |

Not shadowed, on purpose: imports, restores, resets and the demo's enter/leave (they replace the save: the shadow starts again
from a new base, 2.5); dev boosts (`devMode`, 11.7.4: owner-only tooling, counted as `skipped.dev`); pure reads (`ecoCap`,
previews: proven by goldens, no state); the quest board and ledger projections (read-only; a read shadow of them is possible
later with no new transport, section 7).

### 1.3 Outputs compared, per action

1. **returns**: the entry's return value in JSON normal form (AGENT_BRIEF section 4: `NaN` as `null`, `undefined` as `null`), or
   `{threw: true}` when the page threw (the error text is not sent).
2. **state**: for every owned path, the page's value after versus native's predicted value after. Owned paths are the
   `SaveFields.TRACKED` keys, restricted inside `SaveFields.PARTIAL` keys to the listed sub-paths, plus `dailyDraw`, `classContent`
   and `dhMig` whole for `hand.*`, `quest.*` and `boot.load` (HandDealer owns them). Equality is the golden rule: JSON-value
   equality, object key order ignored, array order kept, numbers equal as binary64. A field native did not change and the page
   did not change is equal by construction (both come from the same before-state).
3. **toasts**: every `toast()` text during the action, in order, exactly (the economy's messages are part of the contract, spec
   0.3). Includes the doubled "not saved" of a take-back (SAVE-4).
4. **saves**: the number and order of `writeSave()` calls and whether each stored. The outcome is an *input* to native (the
   page's storage answered it), the count is an *output* (D16: a toggle always saves; native must ask for the same saves).
5. **encode check** (WP8 readiness): `EconomySaveCodec.encode(beforeDocument, nativeSave)` must succeed (no `NOT_FAITHFUL`,
   no `NON_FINITE_NUMBER`) and its owned paths must equal the page's after-state. This proves the writer, not only the model.
6. **summary numbers** for the report only: change in total XP, Sparks, rested XP and character level, page and native.

Granularity of a difference: a leaf path (`restedXP`, `attrXP.physique`, `activities[+1].xp`, `questDone.*`, `toasts[0]`,
`saves.count`, `returns.xp`). At most 12 paths per mismatch are kept, then `+N more`.

### 1.4 Normalisations (not differences by construction)

These are the only places where page and native may legitimately differ, each with a mechanical rule. Anything else is a
mismatch or an explicit allowlist entry (5.2).

- **N1 minted ids.** The page mints `crypto.randomUUID()` ids (LOG-2), four base-36 tokens (catalog) and clock ids (`quest_`,
  `nut_`, `care_`, `act_`, D17). Native runs with `ReplayIds`, an `IdSource` that hands out, in order, the UUIDs and tokens of the
  records the page added (read from the page's after-delta); clock ids are mapped by position among the action's new records.
  The number of new ids and their prefix must match; asking for more ids than the page minted is a mismatch (`ids.extra`).
- **N2 instants.** The envelope carries `t0` (`Date.now()` at entry) and `t1` (at exit). Native's clock is fixed at `t0` for the
  whole action (`DeviceClock` semantics: one reading per action). An instant at a declared instant path (`ShadowInstants.kt`, each
  citing the rule that writes it: new records' `date`, `restBoughtAt`, `shieldBoughtAt`, `health.last`, `health.since`, the
  new money record's `date`) is equal when the page's value lies in `[t0, t1]` and native's is `t0`.
- **N3 midnight.** When `localDate(t0) != localDate(t1)` in the action's zone, the action is not compared
  (`skipped.midnight`): the page may have used two days, native uses one (the case `DeviceClock` names).
- **N4 zone and locale.** Native uses the page's zone (`Intl.DateTimeFormat().resolvedOptions().timeZone`) and
  `navigator.language`, not the device's, because the question is whether native computes what the page computed. The page also
  sends its UTC offset at `t0`; if java.time disagrees for that zone and instant (different tz data in the WebView), the action is
  skipped as `skipped.tzdata`. When the page's zone differs from `ZoneId.systemDefault()` at receipt (the WebView keeps its zone
  until reload), it is counted as `info.zoneLag` (evidence for the WP8 zone ruling and LD's D1), not a mismatch.
- **N5 async health.** `health.sync` reads `Date.now()` after its first await; the page's recording proxy stamps that instant
  (`env.health.now`) and native's sync clock uses it.

## 2. Transport

### 2.1 How native gets the state: options weighed

| Option | Verdict | Why |
|---|---|---|
| Native reads WebView `localStorage` directly | rejected | Chromium keeps it in an internal LevelDB under `app_webview`, locked by the WebView, committed lazily; no supported API; and it holds the last *persisted* save, not memory (D13, render edits) |
| Page mirrors the save into a native file on every write | rejected | a second persisted copy of the gameplay save, which A10 forbids for shadow; a second store to back up, restore and keep private |
| Whole save text before and after every action | rejected as the default | a real save is 0.2-3 MB (`fixtures/saves/established.json` is 239 KB for 420 records, about 320 bytes a record); two full stringifies plus two bridge copies of up to 3 MB on the page thread per tap is tens of ms of jank and needless battery |
| Hook `writeSave()` and send its text | rejected as the default; kept as the base | reuses the string `writeSave` already built (no extra stringify), but sees only persisted states and not actions; good for the initial base, too coarse per action |
| **Per-action delta of the tracked keys against what native already holds** | **chosen** | small envelopes (typically 1-20 KB), deterministic, attributable per action, robust to foreign in-memory edits, needs no native access to WebView storage |

### 2.2 The mirror

Native holds, in memory only, a **mirror**: a `JsonObject` of the SHADOW keys for the current page session. SHADOW keys are the
43 `SaveFields.TRACKED` keys plus read-only inputs the adapters need: `waterLog`, `pet`, `luck`, `classId`, `classContent`,
`dhMig`, `schema`. Untracked page data (media, journal entries, food names beyond `date`/`kcal`, badges) is never sent.
`foodLog` is sent projected to `[{date, kcal}]` (all the economy reads, PARTIAL); `dayReflections` is sent as text, because the
coach rewards what was written (decision O-3).

The page keeps a cache of the exact JSON text it last sent per SHADOW key (and per `activities` record). At an action's entry it
stringifies each SHADOW key, compares with its cache, and the differences are the **before-delta**; at exit the same against the
before texts gives the **after-delta**. Native applies the before-delta to its mirror (that is the action's before-state), runs the
action, applies the after-delta (that is the page's after-state), compares, and keeps the page's after-state as the mirror. The
page's cache and native's mirror therefore move in lock step, checked by sequence numbers.

The mirror is never written to disk. Process death loses it; the next page session sends a new base.

### 2.3 Messages (capability `economy.shadow.v1`, protocol `v: 1`)

Plugin `NativeShadow` (a plain Capacitor plugin like `NativeAudio`, not a tab). Methods:

```
handshake()            -> {protocol: 1, capabilities: ["economy.shadow.v1"], fingerprint: "<hex>", state: "off"|"on"|"disabled"|"stale", reason?}
configure({v:1, enabled, sid, page:{build, schema, fingerprint, faults}})
                       -> {ok, state, reason?}                 ok only once native's switch state is stored
base({v:1, scope:"economy", sid, epoch, seq, part, parts, text})
                       -> {ok} | {ok:false, reason}            text: one slice of {"keys":{...}}; ok:true on the last part means decoded
observe(envelope)      -> {accepted:true} | {accepted:false, resync:true|false, reason}   answered before any work
summary()              -> {v:1, state, reason?, since, counts:{...}, last?:{at, kind, paths}}
copyReport()           -> {ok, chars}                          native puts the redacted report on the clipboard itself
clear()                -> {ok}                                 ok only once the ring file is rewritten empty
```

Envelope (`observe`):

```json
{"v":1,"scope":"economy","sid":"p-5f3a9c","epoch":3,"seq":118,"base":117,"kind":"quest.toggle",
 "args":["q_daily_water",true],"t0":1793700000123,"t1":1793700000141,"zone":"Europe/London","offset":0,"locale":"en-GB",
 "env":{"saves":[{"ok":true}],"plan":{"done":false,"rest":["2026-11-02"],"from":"2026-09-09"}},
 "before":{"set":{},"del":[],"rec":{}},
 "after":{"set":{"sparks":412,"restedXP":33,"questDone":{"daily:2026-11-08:q_daily_water":"<id>"}},"del":[],
          "rec":{"activities":{"len":1043,"at":[[1042,{"id":"…","type":"quest","xp":15,"…":"…"}]]}}},
 "returns":null,"toasts":["Quest complete · +15 XP"],"cost":{"ms":6.4},"flags":[]}
```

- `set` replaces a SHADOW key's whole value; `del` removes keys; `rec` is the record delta for array keys listed in `REC_KEYS`
  (economy: `activities`, `quests`, `decayLog`): the new length and the changed `[index, record]` pairs. More than 256 changed
  records falls back to `set` for that key.
- `flags`: `interleaved`, `threw`, `readOnly` (the page's `saveReadOnly`), `nested-async`.
- `env.plan`: `done` is `dayDone(today)`; `rest` lists the plan's rest days in `[from, today]`; `from` is `today - 60 days`, or for
  `decay.*` the earliest day the decay walk can visit (`min(decayApplied) + 1`, at most 400 days back). Native's `PlanFacts` answers
  from the list; a question about a day outside the window marks the action `skipped.planWindow` instead of guessing.
- `env.health`: `{now, status, exercise:[{from,to,answer}], steps:[...], sleep:[...], weight:[...], fail?}`, the plugin's answers
  as JSON, recorded by a proxy the shadow wraps around `lmaPlugin('HealthSync')` only while the sync runs (golden `env.health`
  format, so a mismatch is directly a golden case).
- `env.confirm`, `env.prompt`: the owner's answers, in order; `env.dom`: the input values the entry read, by element id.

### 2.4 Limits, strictness, ordering

- Strict decoders (`ShadowEnvelopeJson`, on `core/save`'s `JsonReader`, no `org.json`): exact key set for `v: 1`; a wrong kind,
  a key outside SHADOW keys, a negative or non-integer `seq`, `t1 < t0`, an unknown zone id, or a delta whose `rec` index is out of
  range rejects the envelope (`accepted:false`, `reason`, counted). `kind` is an open set: a kind native does not know is accepted
  and counted `skipped.unknownKind`, so a newer page can shadow more than an older app compares. `args` and `env` are decoded by
  the kind's own decoder, strictly.
- Sizes: envelope at most 256 KB of text (`args` 16 KB, `env` 64 KB, toasts 20 x 300 characters, `saves` 8); larger is not sent:
  the page counts `skipped.size` and drops its cache (the next envelope triggers a base). Base at most 8 MB in 256 KB parts
  (32 parts); a larger save disables the shadow with `tooLarge` (Settings says so).
- Ordering: one `seq` per page session (`sid`, random at page start), incremented per envelope and per base. Native accepts an
  envelope only when its `base` equals the mirror's `seq`; anything else answers `resync:true`, discards the mirror, and the page
  sends a base before its next envelope. A repeated `(sid, seq)` is answered `accepted:true` and ignored (repeat-safe, like op ids
  elsewhere). Capacitor delivers a plugin's calls in order on its handler thread; the sequence check does not rely on it.
- `epoch` increases on import, restore, reset, a demo enter/leave and a Life Director backup restore (section 7); an envelope of
  an older epoch is refused with `resync:true`.
- Versioning: `v` changes only for an incompatible envelope; native answers an unknown `v` with `accepted:false, reason:
  "unsupported"` and the page stops sending for the session. The capability string changes only with `v`.
- Older page or older app: no `NativeShadow` plugin, or no `economy.shadow.v1` in the handshake, means the page module does
  nothing (no wrappers installed, no cost). A page that does not send means native does nothing.
- Content fingerprint: `handshake().fingerprint` is the hash of what native's rules were built from (the `hash` of the packaged
  `quest-content.json` and a new `economyHash` over `ECO`, `DEFAULT_RULES` and the duration rules, written by the page's
  generators). The page computes the same at start (`crypto.subtle`, once). Different means the page was updated over the air
  without the app (or the reverse): native's rules are not the page's, so the shadow is `stale`, compares nothing, and Settings
  says "Waiting for the matching app update". Without this, every OTA page change would read as a native defect.

### 2.5 Page side

- `12-shadow-boot.js` (small, before `13-runtime.js`): if the stored preference `nativeShadow` is true and the page is not in the
  demo, wraps `load` and keeps the stored text it read and the state after `load()` and after the boot's `applyDecay()`. These
  become the first base and the `boot.load` / `boot.decay` envelopes once the handshake answers. With the switch off it reads
  one preference and does nothing else.
- `50-native-shadow.js` (transport, cache, deltas, base, settings card) and `50-native-shadow-points.js` (the table of 1.2: which
  global to wrap, how to read its `env`). Wrappers are installed after every segment has run (so `save` is wrapped outside the
  three existing `save` wrappers, `33-android`, `50-quests-hand`, `50-native-nutrition`; a test asserts the order), and only while
  the switch is on and the capability is present.
- A wrapper is transparent by construction:

```js
function point(kind, orig, readEnv) {
  return function () {
    let rec = null;
    try { if (live() && !depth) rec = begin(kind, arguments, readEnv); } catch (e) { fault(e); }
    depth++;
    let r;
    try { r = orig.apply(this, arguments); }
    catch (e) { depth--; try { if (rec) end(rec, { threw: true }); } catch (_) { fault(_); } throw e; }
    depth--;
    try { if (rec) (r && typeof r.then === "function") ? settle(rec, r) : end(rec, r); } catch (e) { fault(e); }
    return r;
  };
}
```

  `begin` stringifies the SHADOW keys and starts capturing `toast`, `writeSave` results, `confirm`/`prompt` answers; `end` takes
  the after texts synchronously and queues the envelope; the queue is posted with `setTimeout(0)` after the action, never inside
  it, and `observe` is not awaited. An async entry (`health.sync`) is ended when its promise settles; if any other shadow point or
  bare save runs while it is pending, it is flagged `interleaved` and native skips it (`skipped.interleaved`); the other action
  is compared normally.
- Imports, restores, resets and the demo raise `epoch` and drop the cache; the next envelope is preceded by a base.

### 2.6 Native side

- `core/shadow` (pure Kotlin, CONTRACT A07, the same import rules as `core/economy`): `ShadowEnvelope` and its decoder,
  `ShadowMirror` (base assembly, deltas, sequence and epoch), `ShadowRunner` (kind to native call, with `ReplayIds`, a fixed
  `EconomyClock(t0, zone)`, `Storage` from `env.saves`, `RecordedHealth` (a `HealthReads` over `env.health`), `EnvPlanFacts`, the
  WP5 `HandFactsAdapter` and `SaveCoachStore` over the before-state), `PageSaveComposition` (save, render auto-verify, wrapper
  auto-verify, in the page's order), `ShadowDiff` (owned paths, N1-N5, the encode check), `KnownDifferences` (5.2), `Redactor`,
  `ShadowCounters`. Each file under 600 lines.
- `bridge/web/NativeShadowPlugin.kt`: decodes nothing itself; copies the call's text into the worker's queue and answers at once.
- `app/shadow/ShadowWorker`: one background thread at `THREAD_PRIORITY_BACKGROUND`, a queue of at most 16 envelopes (overflow:
  the newest is dropped, `skipped.queue`, and the mirror is marked for resync). It never posts to the UI thread.
- `app/shadow/ShadowRing`: the only file the shadow writes (section 3).
- Storage outcome mapping: every `env.saves[i].ok` true gives `Storage.Writable`; all false gives `ReadOnly` when `flags` has
  `readOnly`, else `Full`; a mix in one action (possible only if storage fills between two saves of one tap) is skipped as
  `skipped.mixedStorage` (native's `EconomyContext` has one storage state per action; widening it is not worth it for shadow).
- Codec reading: the before-state is decoded with `SaveReading.AS_PAGE` (replay what the page tolerates, as `SaveHolder` does).
  The same state is also checked with `STRICT`; a strict refusal is counted `codec.strict` with its path (never a value): a native
  writer at WP8 would refuse that save, so each one needs a ruling before cutover.

## 3. Where mismatches go, and how the owner sees them

### 3.1 The ring

`noBackupFilesDir/economy-shadow/ring.json` (not in device backups or folder backups: diagnostics, not gameplay data; ADR 0004 and
0010 unaffected). Written atomically (temporary file, then rename), at most once every 30 s while it changes, and when the app
stops. At most 128 KB; bounded content:

- header: `{v:1, since, salt, state, reason?, app:{versionCode, nativeApi}, faults:{native, page}}`;
- counters per kind: `seen, compared, same, allowed, different, skipped{midnight, interleaved, size, queue, planWindow, tzdata,
  mixedStorage, unknownKind, dev, stale}`, plus `info{zoneLag, persistLag, d13Kept}` and `codec{strict, encode}`;
- entries, at most 200, coalesced by signature (kind + sorted paths + allowlist id): a repeat raises `count` and `last` instead of
  adding an entry; the oldest go first.

An entry: `{id:"m-17", first, last, count, kind, pageBuild, paths:[...], page:{xp:+15, sparks:+15, rested:-7}, native:{...},
summary, allow?}`. Times are kept to the minute.

### 3.2 Redaction (never raw personal data)

What may appear in the ring or the report: kind names, field paths with fixed key names, numbers from economy fields (XP,
Sparks, rested XP, durations, counts, levels), booleans, attribute ids, cadences, the page's built-in activity types and quest ids
(those in the packaged `QuestContent` and `DEFAULT_RULES`), and toast *templates* (an economy message with its digits as `#`,
when it matches a template native knows). Everything else (activity names and notes, quest names the owner wrote, skills,
custom rule names, food, journal text, money notes, ids) appears only as `<text n=12 #a1b2c3>`: its length and the first six
hex digits of SHA-256 with the ring's per-install `salt` (so a short name cannot be looked up). Paths into owner-keyed maps use
`*#hash` (`skillXP.*#3f09aa`); activity records are named by position relative to the action (`activities[+1]`, the first new
record) or by hashed id. `Redactor` has a test that feeds every golden save and asserts no string from the save appears in
its output.

### 3.3 Settings

A page card in Settings, Data & advanced, so both the classic and the native Settings screen draw it:

- **Native economy check (beta)**, switch, off by default (`nativeShadow` in `lifemax_android_prefs_v1`, bound in native
  Settings' BOUND list through `setNativeShadowEnabled`). Help line: "Your phone also works out every XP and Sparks change the
  app makes, without saving or showing it, and counts any difference. Nothing leaves the phone."
- When on, a status row filled from `summary()` when the card is drawn: "Checked 1,150 actions since 10 Nov · 3 different ·
  last 18 Nov 09:31 (quest)". Other states: "Paused: waiting for the matching app update" (`stale`), "Stopped after errors.
  Turn it off and on to try again." (`disabled`), "Your save is too large for the check" (`tooLarge`).
- Buttons: **Copy report** (native builds the text and puts it on the clipboard; the page never sees it), **Clear** (asks
  first; ok only once the ring is empty on disk).
- No notification, no badge, no network. Turning the switch off removes the wrappers at once and keeps the ring until Clear.

### 3.4 Sharing with the assistant

The owner taps Copy report and pastes it into the chat. Shape (plain text, at most about 6 KB):

```
LifeMax economy check v1 · app 1.42 (api 102) · page b7c19e0 · on since 2026-11-10 (12 days)
actions 1,204 · compared 1,150 · same 1,141 · allowed 6 · different 3 · skipped 54 (midnight 2, interleaved 9, stale 43)
by kind: log.record 210/210 · quest.toggle 338/340 · hand.ensure 12/12 · health.sync 40/40 · decay.apply 9/9 · ...
codec: strict refusals 0 · encode refusals 0 · cost page p50 4 ms p95 11 ms · native p50 38 ms p95 120 ms
m-17 x2 quest.toggle 18 Nov 09:31 → 18 Nov 21:02  paths restedXP; activities[+1].xp
     page xp +15 rested -7 · native xp +15 rested 0 · toasts same
```

A difference that needs more than this is reproduced from the corpus (6f) or with decision O-4.

## 4. Safety

- **Never writes.** `core/shadow` and the plugin have no path to the page's command channel, `notifyListeners` or any plugin
  that writes; the only file is the ring. A reachability test (as `HeadlessCheckTest` does for the cat brain) fails if
  `core/shadow` reaches `EconomySaveCodec`'s encoded text from any I/O, or if the plugin calls anything but the worker and the ring.
- **Never shows.** Nothing native computed is drawn; the Settings card shows counts and the report is copied by the owner.
- **Never throws into the page.** Every page-side step is in `try`; the original function runs exactly once with its own
  arguments, `this`, return value and exception; plugin calls are fire-and-forget with `.catch(() => {})`. Three page faults in
  one page session switch the page side off for that session (`faults.page` is reported at the next `configure`).
- **Never blocks the UI thread.** Native work runs on the worker; Capacitor plugin methods run on the bridge's handler thread and
  return before any work.
- **Budgets** (asserted by the perf test on a 1 MB and a 3 MB synthetic ledger, then read from the owner's report):

| What | Target | Hard limit and response |
|---|---|---|
| Page main thread per action (two stringify passes, compare, capture) | p50 under 5 ms, p95 under 15 ms at 1 MB | five actions in a row over 50 ms: page side off for the session, `faults.page` |
| Envelope size | typically 1-20 KB | 256 KB: not sent, `skipped.size`, resync |
| Base | once per page session | 8 MB; over 4 resyncs an hour: `disabled` (`resyncLoop`) |
| Native worker per envelope | p50 under 50 ms, p95 under 200 ms (decode and diff dominate) | p95 over 2 s across 50 actions: `disabled` (`slow`) |
| Native memory | mirror tree about 6 times the text: about 20 MB at 3 MB | text over 4 MB: `tooLarge`, nothing held |
| Battery | no timers, wake-ups, jobs or network of its own; work only after a page action | Battery Saver on: envelopes dropped and counted `skipped.battery` (decision O-5) |
| Disk | ring at most 128 KB, one write per 30 s at most | write failure: counted, no retry loop |

- **Self-disable.** Five internal errors (an exception in decode, run or diff, each caught and counted by class name only) within
  24 hours, a resync loop, or the slow limit set `state: disabled` with the reason, persisted in the ring header; `configure`
  answers `disabled` and the page stops sending. The owner turns the switch off and on (or installs a new app version) to retry.
- **Refused saves (WP1 refusals).** A page save that fails (`writeSave` false) is replayed as such (`Storage.Full` or `ReadOnly`),
  so native must reproduce the page's take-back (Log, Life Director, coach) and its D13 non-take-back (attempt kept in memory)
  exactly; each D13 occurrence is also counted `info.d13Kept` by kind, as evidence for the D13 ruling. A codec refusal of the
  before-state is a `codec.strict` count, and a native encode refusal is a mismatch of its own kind (`codec.encode`).
- **Clock changes.** Native uses the page's `t0`, so a manual clock change cannot create a false difference; a jump is noted
  (`info.clockJump`: `t0` earlier than the previous `t1`, or more than a day later without a boot). The day roll is compared where
  the page performs it (`decay.apply`, `boot.decay`, `hand.ensure`).
- **Zone changes.** N4: the page's zone is used; a lag against the device zone is counted `info.zoneLag`.
- **Demo mode.** The page module and the boot hook do nothing when `LM_DEMO.on`; `NativeShadow` is added to the `OFF` list in
  `50-demo-mode.js` (DEMO_MODE section 4 rule: a plugin that writes outside localStorage) and to `DEMO_MODE.md`. The proof is about
  the owner's real save only.
- **Take-back (D13).** The after-state is the page's memory after the action, take-back included; the `persist` cross-check
  (`writeSave` results versus memory) counts `info.persistLag` when memory and storage differ after an action (an unsaved attempt
  waiting for the next save). Native's prediction must match memory, not storage, because that is what the page does today.
- **Privacy.** On the bridge the envelope carries the owner's text (activity names, quest names, reflections) in memory only;
  the mirror is never on disk; the ring and report are redacted (3.2); native logs nothing (CONTRACT section 8).

## 5. What a run proves, and how to judge it

### 5.1 Exit criteria for WP8 (owner ruling O-1; recommended values)

1. **Time and volume.** The switch on for 30 consecutive days on the owner's phone, with at least 1,500 compared actions, and the
   last 30 days free of unexplained differences: each difference is fixed in native (and has not recurred for 7 days) or carries
   an allowlist id.
2. **Coverage on the device.** At least: `log.record` 100, `quest.toggle` 100 (10 of them unchecks), `hand.ensure` with a deal
   25 (one per day), `hand.verify` that paid 10, `hand.reroll` 5 (one paid in Sparks), `health.sync` that paid 20 (steps, sleep and a
   workout each at least 3), `boot.load` 10, `decay.apply` or `boot.decay` that changed something 3, `save.other` 100, plus
   `director.award` 20 if the owner uses Life Director. Kinds the owner never uses (ascend, shop, money, catalog, plan, care,
   rest) are covered by the corpus (6f) and listed in the report as "not seen on this phone".
3. **Defects reproduced.** Every defect reachable through a shadow point is reproduced identically through the transport by the
   corpus: D01, D03, D04, D05, D06, D07, D08, D09, D10, D11, D13, D14, D16, D17, D18, D20, D21, D22, D28, D29, D33, D34, D35, D37,
   QD02, QD15, QD16, QD19-QD23 (each has golden cases; 6f runs them as envelopes). On the device, any of them that occurs must show
   as "same", which it will only if native reproduces it.
4. **Events.** At least one day roll with the app open and one at boot, one failed or read-only save (the corpus' failure
   injection is enough if none happens), one zone or DST change (the corpus if none in the window).
5. **Health of the check.** No self-disable in the last 14 days; zero internal errors; page p95 under 15 ms; skipped under 5% of
   actions and never for `size`; `codec.strict` and `codec.encode` both 0 (or each ruled).

### 5.2 Known and accepted differences

`core/shadow/KnownDifferences.kt`, an explicit list, each entry `{id, kinds, path pattern, condition, reason, ruling}`, covered
by a test that it still matches something in the corpus (a dead entry fails). Starting list, all from ECONOMY_SPEC 21 (fields a
port may ignore) and so pre-approved:

| id | kinds | path | reason |
|---|---|---|---|
| A-01 | any | `journalCoach.days.*`, `journalCoach.pats` | the journal's redraw adds them (COACH-1, spec 21); not a coach award |
| A-02 | any | `plan.checks.*.s` | plan ticks, specified but outside the economy (PLAN-1, spec 21) |
| A-03 | `money.*`, `save.other` | `money` paycheck settings | dated by the day, outside the economy (spec 21) |

Anything else needs an entry written in a PR and signed off by the owner; native fixing a defect early is not allowed during
shadow (native must equal the page; a fix lands on the page first with a golden, as LIFE_DIRECTOR_NATIVE_PLAN 5.1 says).

### 5.3 What the owner does

Install the app version that has it, turn on Settings, Data & advanced, Native economy check (beta), and use the app as usual. Once
a week, glance at the line under the switch. If it says something is different, tap Copy report and paste it to the assistant.
After about a month with nothing different, the cutover (WP8) can be planned.

## 6. Work packages

Sizes as in ECONOMY_NATIVE_PLAN (S under a day, M two to four days, L about a week). Every package keeps the page the only writer,
adds its tests to CI (`tools/run_tests.sh` for the page, the native unit tests in `build-android.yml`) and changes no golden file
except where it adds one.

### WP6a. Transport, decoder and a no-op shadow (M, risk medium)

Scope: `12-shadow-boot.js`, `50-native-shadow.js`, `50-native-shadow-points.js` (wrappers for every kind of 1.2, envelopes sent);
`NativeShadowPlugin`, `ShadowWorker`, `ShadowEnvelope` decoder, `ShadowMirror`, counters, a minimal ring (counts only), the
Settings card with the switch and the counts; the fingerprint (`economyHash` added by `tools/gen_quest_content.js`). Native
compares nothing yet: it only applies deltas and records, per kind, which owned paths each action touched (the **touch map**).
That map, from the owner's phone, shows any page code that changes tracked fields during an action that no plan has listed
(render side effects) before a line of comparison is written.
Tests: `tests/native-shadow.test.js` (mock plugin: envelopes for each kind, deltas, base parts, resync, epoch on import and
restore, async and interleaved health sync, demo does nothing, off does nothing, absent plugin does nothing);
`tests/native-shadow-transparent.test.js` (the economy, quests and board golden cases and the 40 `sim` seeds run with shadow on,
off, with a plugin that rejects every call and with one that throws: returns, state, toasts and `localStorage` writes byte for
byte identical; the `save` wrapper is outermost); Kotlin `ShadowEnvelopeJsonTest` (40 or more malformed variants),
`ShadowMirrorTest` (sequence, duplicates, gaps, epochs, size caps, 8 MB base), `ShadowWorkerTest` (overflow, never on the main
looper).
Bridge: new plugin and capability `economy.shadow.v1`; NativeApi bump (the owner raises it at merge); `BRIDGE_CATALOG.json` entry
(`writes: []`, `mutationsGate: "no native writes"`); `DEMO_MODE.md` and the demo `OFF` list; an ADR "Economy shadow mode"
recording A10 compliance; MIGRATION.md entry. Page change: yes (three new segments, one Settings card).
Decisions: O-2, O-5.

**Status: built** (Native API 106, owner decisions O-1 to O-6 at their recommended defaults). What exists:
- Page: `05-shadow-boot.js`, `50-native-shadow-points.js` (32 entry points of table 1.2 and three whole-save replacements),
  `50-native-shadow.js`; the switch "Native economy check (beta)" (`nativeShadow`, Settings, Data & advanced, off by default, bound in
  native Settings) with a status line from `summary()`; `NativeShadow` in the demo's `OFF` list; module map regenerated.
- Native: `core/shadow` (`ShadowLimits`, `ShadowKeys`, `ShadowMessages`, `ShadowEnvelopeJson`, `ShadowMirror`, `TouchPaths`,
  `ShadowRedactor`, `ShadowLedger`, `ShadowLedgerJson`, `ShadowSession`), `app/shadow/ShadowWorker` and `ShadowRing`,
  `bridge/web/NativeShadowPlugin.kt` (handshake, configure, base, observe, summary, clear). Compares nothing: each applied envelope
  records its kind's touched fields (and, under `between`, what changed since the last action) in the ring, coalesced by kind and paths.
- Fingerprint: `quest-content.json` schema 2 carries `economyHash` (SHA-256 of `{eco: ECO, rules: DEFAULT_RULES, durations:
  ECO_DURATION_RULES}`) inside the hashed content, so `contentHash` alone is the fingerprint; the page computes it with `crypto.subtle`.
- Tests: `tests/native-shadow.test.js` (31 checks), `tests/native-shadow-transparent.test.js` (economy 880 cases with native answering,
  rejecting and throwing; quests 842 answering; board 1011 answering and throwing), Kotlin `ShadowEnvelopeJsonTest` (55 malformed
  envelopes and the base and configure variants), `ShadowMirrorTest`, `ShadowSessionTest`, `ShadowReplayTest` (every stateful case of
  the economy, quests, board and device-context goldens and a real save replayed through the transport, the copy equal after each),
  `ShadowKeysTest` (page and native lists equal), `ShadowRingTest` (200 entries, 128 KB, atomic write, no word of any fixture save in the
  ring file), `ShadowWorkerTest` (own thread, queue of 16, faults, write interval, Battery Saver), `ShadowPerfTest` (3 MB save).
- Measured: a 3 MB save crosses in 16 base parts; envelopes stay under 3 KB; the page's main-thread cost per action at 3 MB is about
  12 ms p50 and 15 ms p95 in desktop Chromium (a phone is slower: the 50 ms slow rule may turn the page side off on a very large save;
  the owner's report will tell); native applies an envelope in under 4 ms p95 on the JVM.
- Differences from the text above, with reasons: the boot hook is `05-shadow-boot.js`, not `12-`, because segments 06 to 12 are cut in
  the middle of statements and 05 is the last place a statement can stand before the boot; every plugin method takes one `text` (the
  message's JSON) so native reads it with `core/save`'s `JsonReader` and not through the bridge's `org.json`; a bare `save()` while an
  async action waits and nothing else runs counts as that action's own (the Health sync saves in its `finally`), any other point marks
  it interleaved; Battery Saver answers state `paused` and the page sends nothing until it configures again on the next return to the
  app (no envelope is built for nothing); `env` is checked strictly only for `saves` in 6a, the other parts are kept for the kinds'
  decoders of 6b-6d; `dhRest.create` (an object that exists only while the 3D table is open) is recorded as `save.other`, dev boosts as
  edits between actions; the ring keeps the touch map entries already (6e adds the mismatch entries, the report, Copy report and
  Clear's button); the page has no build label, so `page.build` is empty. Not run with the check on: the 40 `sim` seeds (6f).

### WP6b. Economy actions compared (L, risk medium)

Scope: `ShadowRunner` for `log.*`, `voice.log`, `director.award`, `bonus.*`, `money.*`, `fixed.*`, `coach.pay`, `spark.buy`, `ascend`,
`rest.take`, `rules.save`, `save.other`, `boot.load`; `PageSaveComposition` for the economy half (SAVE-1, MIG, Sparks sync);
`ShadowDiff` with N1-N4 and the encode check; `KnownDifferences` A-01..A-03.
Tests: the **corpus**: `tools/gen_shadow_corpus.js` drives the built page (Playwright, the golden generators' fixed clocks and
zones) through each economy golden case whose call is an entry point, and through the `sim` seeds, with a recording mock of
`NativeShadow`, writing `tests/fixtures/shadow-corpus.json` (bases and envelopes, one scenario a line); `tests/shadow-corpus.test.js`
records it again and fails on any difference. Kotlin `ShadowCorpusTest` replays every scenario through decoder, mirror, runner and
diff and expects zero differences and the expected skips; `ShadowMutationTest` applies a dozen rule mutations (cap table, rested
rate, Sparks rounding, a toast word, D05 "fixed") and expects each to produce a difference at the expected path (the comparison is
not vacuous).
Bridge: none. Decision: O-3.

**Status: built** (Native API 109 on this branch; the integrator renumbers at merge). What exists:
- Native, `core/shadow`: `ShadowEntries` (one entry per compared kind: the native call, glue included, and the env parts it may
  carry), `ShadowInput` (strict per-kind readers of `args` and `env`, typed refusals `skipped.input`; `EnvPlanFacts` over `env.plan`),
  `ShadowRunner` (the skips, then the entry through the WP5 `ContextFactory` over the before-state, a clock fixed at `t0` in the
  page's zone, the page's locale, `ReplayIds` (N1), the storage outcome of `env.saves`; the codec's merge for the prediction and
  its full encode check), `ShadowDiff` (owned paths, PARTIAL sub-paths only, N1 clock ids and N2 instants in `[t0, t1]`, redacted
  leaf paths), `ShadowComparison` (state, return value, toasts, number of writes, ids asked for, encode check; the verdict),
  `KnownDifferences` (the allowlist), `ShadowContent` (page content: none in the app), `PageFresh` (`freshState()` for `boot.load`).
  `ShadowSession` compares every applied envelope of a compared kind after recording its touch map; `ShadowLedger` counts
  `compared, same, allowed, different` per kind, `codec{strict, strict.<field>, encode, nonFinite, checked}`, `info.d13Kept.<kind>`,
  `info.zoneLag`, and keeps difference entries (`m-N`: kind, paths, allowlist ids, both sides' XP, Sparks and rested changes),
  coalesced, at most 200, unexplained ones kept longest when the ring is fitted to 128 KB. `core/economy`: an optional
  `EconomyContext.attempts` hears each write a save attempts (the number of saves compared). `core/save`:
  `EconomySaveCodec.merged` (the merge `encode` lays, without the text round trip). `NativeShadowPlugin.summary` adds the
  comparison's counts and the last unexplained difference's time and kind.
- Compared kinds (22 and the boot's load): `log.record`, `log.delete`, `fixed.undo`, `voice.log`, `director.award`,
  `director.quest`, `bonus.care`, `bonus.nutrition`, `money.record`, `money.remove`, `fixed.plan`, `fixed.catalog`, `coach.claim`,
  `coach.catchup`, `spark.buy`, `ascend`, `rest.take`, `rules.save`, `rules.reset`, `rules.base`, `rules.decay`, `save.other`,
  `boot.load`. Left for later, counted `skipped.later`: `quest.*`, `hand.*` (6c), `health.sync`, `decay.apply`, `boot.decay` (6d).
  Not native as one action, `skipped.notNative`: `log.remove` (`removeAct`, a cache edit a save always follows), `coach.reflect` and
  `coach.review` (the journal's own text saves around the coach's reward). Without page content in the app, `fixed.plan` and
  `fixed.catalog` are `skipped.content` on the phone (the tests give the goldens' content).
- Skips, each counted by reason: `later`, `notNative`, `unknownKind`, `threw`, `interleaved`, `midnight` (N3), `tzdata` (N4),
  `mixedStorage`, `input`, `hand` (the action changed `dailyDraw`, `classContent` or `dhMig`: the Daily Hand's composition is 6c's),
  `content`, `codec` (the page's own reading refuses the before-state), `planWindow`, `error` (an internal error, five in a day
  disable the check).
- Allowlist: A-01 (`journalCoach.days.*`, `.pats`, COACH-1), A-02 (`plan.checks.*.s`, PLAN-1), A-03 (`money` put in by the money
  screen's redraw where the save had none, `money.*` and `save.other`), A-04 (a "not saved" toast said twice by a failed save and its
  take-back, SAVE-4), all four from spec 21; and **A-05, proposed here and awaiting the owner's sign-off (O-6)**: the redraw inside a
  save puts back an empty `foodLog`, `mealsMeta` or `dayReflections` when the save has none (the golden generator's header names
  it; reached by `load/newer-save-read-only`, a stored save without those keys followed by a save).
- Tests (Kotlin, compiled with kotlinc and run with JUnit directly, not under gradle: the build machine had under 2 GB of disk): `ShadowGoldenCompareTest` (the 193
  economy golden cases whose calls are entry points of these kinds, 217 envelopes through the fake page's transport and the
  comparison: 216 same, 1 allowed (A-03, A-05), 0 unexplained, none skipped; counts pinned), `ShadowCorpusTest` (the real page's
  envelopes for the boot and 19 actions on the established save, `tools/gen_shadow_corpus.js` into
  `tests/fixtures/shadow-corpus.json`: 20 compared, 20 same), `ShadowMutationTest` (13 wrong rules, from the cap table to D05
  "fixed" and an extra write: every action a mutation changes is reported unexplained at the rule's field, none absorbed),
  `ShadowComparisonTest` (page lies at eight paths, each allowlist entry live and only for what it names, N1/N2 windows, every
  skip reason with the copy still in step, five errors disable, a strict refusal counted), `ShadowRedactionTest` (every string of
  every golden save and injected owner words absent from the ring), `ShadowMismatchRingTest`, `ShadowPerfTest` (3 MB save, 11,000
  records: observe and compare p50 about 52 ms, p95 about 135 ms on the JVM). Page: `tests/native-shadow.test.js` gains the split
  kinds, `env.plan` for `rest.take`, `rule-*` inputs for `saveRules`, and the Settings row of an app that compares.
- Differences from the text above, with reasons:
  - The corpus is two parts: the goldens go through the transport in Kotlin (`GoldenActions`, the fake page), because the
    golden generator replaces `toast`, `confirm` and `prompt` after the shadow wraps them, so a page-recorded golden corpus would carry
    no toasts or answers; and a real-page corpus of the boot and 19 actions (`gen_shadow_corpus.js`), whose clock runs (the
    transport posts on a timer), so it is regenerated on purpose rather than recorded again and compared by a page test. The goldens
    hold no `writeSave()` calls, so the golden envelopes carry native's own count; the real-page corpus proves the count. The `sim`
    seeds through the check are left to 6f.
  - One kind is one entry: `log.delete` became `log.delete`, `log.remove`, `fixed.undo`; `director.award` and `director.quest`;
    `coach.pay` became `coach.claim`, `coach.reflect`, `coach.review`, `coach.catchup`; `rules.save` became `rules.save`,
    `rules.reset`, `rules.base`, `rules.decay` (an envelope does not name its entry, and `saveRules` and `resetRules` send the same
    one). `rest.take` records `env.plan`, `confirmAct` its form (`dom:actDur,actRes,actNote`), `saveRules` its inputs (`dom:rule-*`).
  - `ids.extra` is a difference only when the action's writes stored: a take-back drops the record whose id the page minted.
  - A NaN where the page stores null (D24's quality) is counted `codec.nonFinite`, not a difference; the state is compared as the
    page writes it. Each such count needs a ruling before WP8's writer.
  - The prediction uses the codec's merge (`EconomySaveCodec.merged`); the full encode check (write, read back, faithfulness) runs on
    every action of a save of up to 2,000 records and on one in 16 above (the native budget: it costs about five decodings).
  - New records are named by id (`activities[+1]` is a record whose id the before-state lacks), so a record added after one is
    removed (the Daily Hand's auto-verify re-completing a quest after an undo) is still a new record for N1 and N2.
  - A top-level object's members are shown in the ring only when they are the app's fixed words (an unknown attribute id is
    hashed); the touch map follows the same rule.

### WP6c. Quests, Daily Hand and the save composition (M, risk medium-high)

Scope: `quest.toggle`, `quest.create`, `hand.ensure`, `hand.reroll`, `hand.verify`; `PageSaveComposition` completed with the
render's `dhAutoVerify` (ensure, then verify through `toggleQuest`) and the SAVE-2 wrapper's, with the `dhBusy` guard; `EnvPlanFacts`;
the hand's no-op ensures (most renders) are sent one in 50 and otherwise counted, so a daily deal is always compared but every
redraw does not cost an envelope.
Tests: a new page golden family `save/compose-*` (recorded by extending `gen_economy_golden.js` with the Daily Hand on: a Log that
fulfils a card, a toggle whose save fulfils another, a deal at midnight inside a save, a failed save with cards), replayed by
Kotlin directly and through the corpus; quests golden (842) and board golden (1011) cases that are entry points, as envelopes.
Bridge: none. Decision: none beyond QD rulings already defaulted to "keep".

### WP6d. Health, decay and the day roll (M, risk medium)

Scope: `health.sync` over `RecordedHealth` (N5, the async and interleave rules), `decay.apply`, `boot.decay`, shield ticks inside
them, `env.plan` windows for decay; `info.clockJump`, `info.zoneLag`.
Tests: the 63 `health` and 35 `decay` / 21 `shield` and `streak` golden cases as envelopes; corpus scenarios over 14 simulated days
opened daily and once (D21), a DST night, Santiago's missing midnight, a clock moved back a day; failure injection of a plugin read
that throws (HEALTH-0). Bridge: none. Decision: none.

### WP6e. Mismatch ring, redaction, report and Settings (M, risk low-medium)

Scope: `ShadowRing` (coalescing, bounds, atomic writes, header state), `Redactor`, `summary`, `copyReport`, `clear`, the full Settings
card text and states, self-disable rules and budgets enforcement, Battery Saver pause.
Tests: `RedactorTest` (every fixture save and corpus envelope: no save string appears in ring or report), `ShadowRingTest`
(200-entry and 128 KB bounds, coalescing, crash between temp and rename, clear), `SelfDisableTest`, a page test for the card in
classic and native Settings (`native-settings.test.js` gains the card), screenshot references for the native Settings card if it
changes the native drawing (it should not: it is an ordinary page card). Bridge: methods already in WP6a. Decision: O-4.

### WP6f. Proof harness (M to L, risk low; runs alongside 6b-6e)

Scope: the corpus generator (6b) extended with: every real fixture save in `tests/fixtures/saves` as a base with a scripted day of
actions; fuzz (`ShadowFuzzTest`: seeded random envelopes and deltas, reordered, dropped, duplicated, truncated base parts; never a
crash, always a counted reason); failure injection (worker exceptions until self-disable; page faults; a plugin that answers slowly or
never; the queue full); the perf test (`tests/native-shadow-perf.test.js` with Chromium CPU throttling 4x on 1 MB and 3 MB ledgers for
the page budget; `ShadowPerfTest` on the JVM for the worker, with the device numbers then taken from the owner's report).
Bridge: none. Decision: none.

Order: 6a, then 6e's ring and counts, then 6b, 6c, 6d (6f grows with each). The switch can ship from 6a: the owner's phone starts
producing the touch map and counts while 6b-6d land, and each later package only turns more kinds from `skipped.unknownKind` into
compared.

### Owner decisions

| | Question | Recommendation |
|---|---|---|
| O-1 | What clears WP6 for WP8 (the original WP6 question: what mismatch rate blocks cutover) | Section 5.1: 30 days, 1,500 actions, zero unexplained differences, the coverage list, the corpus reproducing every reachable defect |
| O-2 | Name, place and default of the switch | "Native economy check (beta)", Settings, Data & advanced, off by default |
| O-3 | May the coach's reflection text cross to native, in memory only, for `coach.pay` | Yes: the coach rewards what was written; it is never stored or reported. The alternative is to leave coach rewards out of the proof |
| O-4 | A "Save case…" button that writes one unredacted difference (the action and the touched fields) to a file the owner picks, after a warning | Yes, in WP6e, never automatic; the copied report stays redacted |
| O-5 | Pause the check while Battery Saver is on | Yes (counted, not hidden) |
| O-6 | Allowlist entries beyond A-01..A-03 | Each signed off by the owner in its PR; no native-only fixes during shadow |

## 7. How the Life Director store joins later (WP-LD12)

The transport above is designed for two stores from the start; LD12 adds a scope, not a protocol.

- **Same plugin, same ring, same Settings card.** Native advertises `director.shadow.v1` beside `economy.shadow.v1` once LD's native
  code exists (LD8 for reads, LD9 for commands). The card gains a second line (counts per scope); the report a second block.
- **A second mirror.** `scope: "director"` holds the top-level keys of `ST` from `lifemax_director_state_v1` (the page reads its
  own key; native never touches WebView storage). The LD store codec (WP-LD7) decodes it, as `core/save` does for the economy.
  `REC_KEYS` for this scope: `schedule.scheduledInstances`, `schedule.historyInstances`, `ledger.events`, `changeLog` (dotted paths,
  same record delta). One `seq` per page session across both scopes, so native sees one ordered stream.
- **Kinds.** `director.cmd` (a `Do.*`, `Today.*` or `dialApply` with its op id; `env` holds the `LifeMaxHost` answers the command
  received), `director.tick` (`syncHost`, `processMisses`, `maybeRollover`), `director.engine` (LD 5.1 B: engine input and output
  for `regenerate` and `reschedule`, `computeTimeMs` ignored), and the read shadow (LD 5.1 A) needs no envelope: the page adds
  `shadowSeq` to the `LifeDirector.native` projection it already sends, and native compares its own read model, computed from the
  director mirror at that `seq`, with the projection it received.
- **Commands that award.** An LD command that completes a session writes both stores. It is one envelope with `scope: "both"` and
  before/after deltas for each scope; the economy's `director.award` point is then nested inside it (not a separate envelope), and
  native runs the LD command against the native economy through the WP-LD11 adapters, comparing both stores and the host calls as a
  list. While LD12 is not built, `director.award` stays an economy kind (this plan).
- **Epochs.** The director scope has its own epoch, raised by `Do.importBackup`, `Do.reset`, a `.bak` restore and a quarantine;
  a device backup restore raises both.
- **Change to LD 5.1.** LD 5.1 A proposed sending `ST` only from a debug build and logging mismatching states to a device file.
  This plan replaces that with the owner's release build behind the switch and the redacted ring, so LIFE_DIRECTOR_NATIVE_PLAN 5.1
  should be updated to point here when LD12 starts. LD's exit criteria (30 days, a weekly rollover, a DST or zone change, a skip
  with take-back, a missed block with regroup, a background timer) are counted per scope with the same machinery.

## 8. Summary for the owner

After an app update, there is a new switch in Settings (Data & advanced): "Native economy check (beta)". It is off until you turn it
on. When it is on, nothing about the app changes: the web app still saves everything and decides every reward. In the background, the
phone's own new code works out the same XP, Sparks, rested XP, quests and Daily Hand for each thing you do, and only counts whether it
got the same answer. It never saves, never shows its own numbers, and sends nothing anywhere.

Under the switch you will see a line like "Checked 1,150 actions · 0 different". Use the app normally and look at that line now and
then. If it ever says something is different, tap Copy report and paste it to the assistant; the report has numbers and field names,
not your activity names, notes or journal. After about a month with nothing different (and a few kinds of day covered: normal days,
health syncs, a missed day), the next step, letting the phone's code do the saving with a way back, can be planned.

The few choices you are asked to make are in the table at the end of section 6; the recommended answer is given for each.
