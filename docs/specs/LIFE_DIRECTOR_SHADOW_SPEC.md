# Life Director shadow check: specification (WP-LD12)

Status: **built** (branch `logic/ld12-shadow`, Native API 135, capability `director.shadow.v1`). The native Life Director code that
already exists runs beside the page and is compared with it; the page stays the only writer and the only thing the owner sees. This
document describes what is compared, how the state reaches native, the limits and safety rules, where the counts go, and how it was
tested. It follows `ECONOMY_SHADOW_PLAN.md` (the economy check, sections 2 to 4) wherever it can and says where it differs.

Paths: `logic.js` is `lifemax_modular_v73/src/features/life-director/logic.js`; the page module is
`lifemax_modular_v73/src/runtime/segments/50-native-director-shadow.js`; Kotlin is `android-wrapper/native/src/main/java/com/lifemax/rpg/`
`core/directorshadow` unless another folder is named.

Contents

0. How to read this
1. What is compared, and what is not yet
2. Transport (SH-1 to SH-8)
3. The checks (SH-9 to SH-19)
4. Limits, budgets and self-disable (SH-20 to SH-29)
5. The ring, redaction, the allowlist and the report (SH-30 to SH-35)
6. Settings
7. Tests and mutation checks
8. Deviations from the economy check's plan
9. Open questions for the owner

---

## 0. How to read this

Rule IDs are `SH-n`. Each rule is:

- **Executed**: a test runs it. The page tests are `lifemax_modular_v73/tests/native-director-shadow.test.js` (the real built page in
  Chromium with a stand-in plugin, `tests/native-director-shadow-mock.js`, whose copy is built as native's is); the Kotlin tests are in
  `src/test/.../core/directorshadow` and `app/shadow/DirectorShadowWorkerTest.kt`. The strongest is `DirectorShadowStreamTest`: the
  messages the real page sent through a lived-in day (`tests/fixtures/director-shadow-stream.json.gz`, written by the page test with
  `RECORD=1`) are played through native's real session and checks, and every check agrees.
- **Read**: derived from reading the code only.

Every rule below is **Executed** unless marked **Read**.

## 1. What is compared, and what is not yet

The packages that exist in Kotlin and only read are compared now (plan 5.1 A, B and the store codec):

| Check | Page side | Native side | Kind |
|---|---|---|---|
| Store codec round trip (WP-LD7) | the exact text `LifeDirectorStore.save` just wrote | `DirectorStoreCodec.decode(AS_PAGE)`, `PageJson.text`, `DirectorStoreCodec.encode`, and a `STRICT` decode as a finding | `director.save` |
| Day, now and week read models (WP-LD8a) | `horizonDays`, `unplannedSegments` (first day), `dayBlocks` (each day), `liveTimeline`, `nowState`, `weekModel`, `nativeWeek`, and every `LifeMaxHost.xpFor` preview they asked | `DirectorReadModels` with the page's previews (`DirectorReads`) | `director.read` |
| Placement (WP-LD4) | `E.generateSchedule(input)` and its answer, from `regenerate` | `PlanSchedule.generate(input)` | `director.plan` |
| Local repair (WP-LD5) | `E.rescheduleActivity(req)` and its answer, from `reschedule` and `planTimeChange` | `Reschedule.run(req)` | `director.repair` |

Not yet (later stages of plan 5.1): the host commands and the tick (WP-LD9, `director.cmd` and `director.tick`, 5.1 C), commands that
award in both stores (`scope: "both"`, `ECONOMY_SHADOW_PLAN` section 7), the ledger's own engine calls (`recordCompletion`, `detectMisses`;
they run inside the host commands, so they join with 5.1 C), and the dial and Today's other parts (WP-LD8b). The ledger is covered now
as part of the saved state (its events and order travel in the store text and round-trip through the codec, SH-9).

## 2. Transport (SH-1 to SH-8)

- **SH-1** The check joins the economy check's plugin, `NativeShadow`. Its handshake lists `director.shadow.v1` beside the economy's
  capabilities. Every method called with `scope: "director"` (`configure`, `base`, `observe`, `summary`, `copyReport`, `clear`) goes to the
  Life Director check's own worker (`app/shadow/DirectorShadowWorker.kt`), which shares the economy's background thread; every other call is
  the economy's, unchanged. Without the capability, without the plugin, with the switch off or in a demo, the page module tells Life
  Director nothing (`LmaDirectorShadow.live` is false) and sends nothing.
- **SH-2** `configure({scope, text})`, text `{v:1, scope:"director", enabled, sid, page:{build, fingerprint, faults}}`, answers
  `{ok, state, reason?}`: `off` when not enabled, `stale` when the fingerprint differs, `paused` under Battery Saver, otherwise `on`; a check
  that stopped itself stays `disabled` until a new app version. Another `v` is refused as `unsupported`.
- **SH-3** An envelope is `{v, scope, sid, seq, base, kind, t0, t1, zone, ops, cost:{ms}, flags}` and one of `bytes` (`director.save`),
  `read` (`director.read`) or `engine` (`director.plan`, `director.repair`). Decoding is strict (`DirectorShadowJson`, on `core/save`'s
  `JsonReader`): exact keys, whole numbers, `base < seq`, `t1 >= t0`, a known zone, a flag word, a kind `director.<word>`; a refusal names a key,
  never a value. A kind native does not know is read for its changes and counted `skipped.unknownKind`.
- **SH-4** The fingerprint is the engine's version and the store's schema (`LifeDirectorEngine.ENGINE_VERSION + "/" + LifeDirectorStore.CURRENT`,
  native `PlanSchedule.ENGINE_VERSION` and `StoreKeys.SCHEMA_VERSION`): `1.0.0/1`. Read: it does not cover the page's words or a change to
  `logic.js` that keeps the version (section 9, OQ-S1).
- **SH-5** The state travels as the page's own JSON texts of its parts. The page splits `ST` at its top-level members, `schedule` at its
  members with `scheduledInstances` and `historyInstances` split into items, `ledger` at its members with `events` split into members and
  `order` into items, and `changeLog` into items; every other value is `JSON.stringify`'s text of it (`capture` in the page module). Put
  together in the page's order (`TextMirror.stitch`), the parts give exactly `JSON.stringify(ST)`, the text the store writes: the page test
  checks the stitched copy against the stored text byte for byte after every save, and its length against the store's `bytes`.
- **SH-6** A change is `["o", path, keys]` (an object split into these members, in this order), `["a", path, length]` (a list split into
  items) or `["t", path, text]` (a part's exact text, sent as a JSON string). Paths are at most 4 steps; at most 20,000 changes a message.
- **SH-7** Native applies an envelope's changes only when its `base` is the copy's `seq` and its `sid` is the session's; a change at a place
  the copy lacks drops the copy (`refused.badDelta`), and the page sends a base before its next state. A repeated `seq` is ignored
  (`info.duplicate`). Plan and repair envelopes carry no changes and need no copy.
- **SH-8** A base is the state's changes from nothing (`{"ops":[...]}`), sent in parts of at most 256 KB, at most 32. The page sends one when
  it starts, after a resync and when a change alone is too large to send. While native has no copy and no base is on its way it answers
  `observe` with `resync` (`noBase`); a base already queued lets the envelope behind it in (`DirectorShadowWorker`, `basePending`).

## 3. The checks (SH-9 to SH-19)

**The store codec (`director.save`).** After each save Life Director tells the module (`logic.js` `persist`: `LmaDirectorShadow.saved(ST,
r.bytes)`, only when the save was ok), which captures the parts at once, inside the save, so they are exactly the saved text.

- **SH-9** Native stitches the text; a length unlike the store's `bytes` is `skipped.capture` and a resync (never a difference: the copy is
  wrong, not native's code). It decodes the text as the page reads it (`AS_PAGE`); a refusal is a difference `store.decode.<reason>`, except
  a text nested deeper than native reads (DV-1 of the store spec), which is allowed as `DA-1`.
- **SH-10** `PageJson.text` of the decoded state must be the text (a difference `store.text.<member>` names the first top-level member
  whose text differs), and `DirectorStoreCodec.encode` of it must give the text again (`store.encode`, or `store.encode.<refusal>`).
- **SH-11** The strict reading is a finding, not a difference: a `STRICT` refusal is counted `strict.<reason>` (with the field path for a
  wrong kind) and an encode refused only for an owned field's kind `encode.wrongType.<path>`, as the store spec's section 12 asks. Each
  needs a ruling before cutover.

**The read models (`director.read`).** At most once a minute after a save, and once when the check starts, the module runs the page's
own read models through `LifeDirectorApp.shadowReads()` (a read-only accessor added to `logic.js`'s controller; the functions are the
page's own), after the save's `setTimeout(0)`.

- **SH-12** The page sends `out` in the read-model recorder's shape (`tools/gen_ld_readmodels_golden.js`): `days`, `unplanned`, `blocks`,
  `timeline`, `now`, `week`, `native`, `xp`, a session named `s<k>` or `h<k>` by where it is stored, a section the page threw in as
  `{threw: name}`; with the state those functions read (the envelope's changes, captured at the same moment), `nowMs`, the device zone,
  the plan zone and its offset. Nothing is sent without a finished set-up and a plan (`skipped.noPlan` on the page).
- **SH-13** Native computes the same sections (`DirectorReads.compute`, the replay of `ReadModelsGoldenTest` in main code) and compares
  each in the page's normal form (`-0` is `0`; NaN and the infinities are `null`); a native throw that stands for the page's is
  `{threw: name}` as in the golden replay.
- **SH-14** The XP previews are the page's: while its read models run, the module records every `LifeMaxHost.xpFor(type, minutes, 1,
  dateKey)` call and its answer (the host object is restored after); native answers its own questions from that list in order, a question
  the page did not ask as a failed preview, and compares the list of questions (`read.xp`).
- **SH-19** A read whose start and end fall in different seconds is flagged `second` and skipped (`skipped.second`): the page's
  `nowEpoch()` may have read either. Executed for the flag on the page; the skip is Read.

**Placement and repair (`director.plan`, `director.repair`).** `logic.js` routes its three engine calls through `planned(kind, input,
run)`: while the check is live the module stringifies the input before the engine runs and the answer after it (`LmaDirectorShadow.planning`).

- **SH-15** Native runs `PlanSchedule.generate` (plan) or `Reschedule.run` (repair) on the same input and compares the whole answer,
  without `computeTimeMs` (the plan's and the change record's).
- **SH-16** A repair is compared as `{updatedSchedule, engineInput, changeRecord, rejectedMove}` with members that are `null` or absent left
  out on both sides.
- **SH-17** The input travels as JSON: a NaN or an infinity in it reaches native as `null` (Read; no current input holds one; a difference
  it caused would show in the report).
- **SH-18** Every comparison carries the plan zone's offset at its moment, computed by the page's engine (`util.tzOffsetSec`); when
  native's (`java.time`) differs, or native does not know the zone, the comparison is skipped as `tzdata` (engine spec OQ-3), never a
  difference.

## 4. Limits, budgets and self-disable (SH-20 to SH-29)

- **SH-20** `observe` is answered before any work: `paused` (Battery Saver), the state when not on, `tooLarge` past 512 KB, `noBase` (SH-8).
- **SH-21** Sizes: an envelope at most 512 KB (the page sends a base instead of a delta that is larger, and skips one still too large as
  `size`); a base at most 32 parts of 256 KB; the stitched state at most 4 MB, past which the check stops itself (`tooLarge`).
- **SH-22** The page's queue holds 16; past it the queue is dropped and a base follows (`skipped.queue`). Native's worker holds 16 likewise.
- **SH-23** The page's cost per action is its capture inside the save or the plan plus the envelope built and posted after it, measured;
  five in a row over the limit (30 ms for each MB of the state's text, never under 50 ms, set at each base) stop the page side until the
  page loads again (`slow`). Three page faults stop it the same way (`faults`).
- **SH-24** The engine's CPU budget: an input over 384 KB (or an answer over 768 KB) is not sent (`engineSize` on the page); a native plan
  or repair that runs longer than 1.5 s makes native compare only one in 4 of the next (`skipped.sampled`), until one is quick again.
- **SH-25** Native's worker time is kept for the last 50 envelopes; a 95th percentile over 2 s stops the check (`slow`).
- **SH-26** Five internal errors in 24 hours stop the check (`errors`); an error is counted by class name only.
- **SH-27** More than 4 bases in an hour stop the check (`resyncLoop`).
- **SH-28** A check that stopped itself stays stopped until the owner turns it off and on, or a new app version starts (as the economy's).
- **SH-29** Battery Saver pauses the check (`paused`); the page asks again when it comes back to the foreground.

## 5. The ring, redaction, the allowlist and the report (SH-30 to SH-35)

- **SH-30** The counts live in `noBackupFilesDir/director-shadow/ring.json`, the economy ring's format (`ShadowLedger`, at most 128 KB,
  written atomically at most every 30 s and when the app stops), beside the economy's ring and apart from it, so the two switches and their
  counts are independent. Not in device or folder backups. The copy of the state is in memory only.
- **SH-31** A difference is kept as paths, never values: a member by name when it is a schema word (lower camel case, letters only), any
  other member (ids, dates, keys the owner made) as `*#` and its salted hash, list items by index, at most 12 paths. The stream test feeds
  the lived-in day with a changed week and checks that no goal title, appointment, id or player name appears in the ring or the report.
- **SH-32** Two finite numbers within one unit in the last place are a last-bit difference (engine spec OQ-12, OQ-15).
- **SH-33** The allowlist: `DA-1` a text deeper than native reads (store DV-1); `DA-2` a last-bit number. A difference all of whose paths
  are allowed is counted `allowed`, not `different`. New entries are signed off by the owner in their pull request, as the economy's O-6.
- **SH-34** Native never writes the page's state, never sends a command, never answers with a value it computed and never shows one: the
  page gets a state word and counts (`summary`), and the report is built by native and put on the clipboard by native.
- **SH-35** Copy report gives, at most 16 KB: the app and page builds, both fingerprints, counts per check (compared, same, allowed,
  different, and skips by reason), the store's strict findings, refusals and errors by name, page and native costs, the times the check
  stopped itself, and the latest 20 differences with their paths and allowlist ids.

## 6. Settings

Settings, Data & advanced, its own card ("Native Life Director check") right under the economy check's: **Native Life Director check (beta)**, a switch, off by default
(`nativeDirectorShadow` in `lifemax_android_prefs_v1`; native Settings binds it in its BOUND list). Help line: "Your phone also works out
Life Director's plans, its week and what it saves, without saving or showing any of it, and counts any difference. Nothing leaves the
phone." Under it the status line ("Checked 23 times since 1 Nov · none different", or paused, stale, stopped and why), and Copy report
and Clear (Clear asks in the card first). The demo turns it off (the plugin is already in the demo's OFF list).

## 7. Tests and mutation checks

- Page: `tests/native-director-shadow.test.js`, 22 checks: off by default and its place and words; nothing without the plugin, without
  the capability or in a demo; a stale fingerprint; the first base and read; the lived-in day (`tests/fixtures/director-day.js`) and four
  later re-plans across midnight sending saves, plans, a repair and reads; nothing refused; every save stitched to the store's length and
  the last to the stored text byte for byte; the read's sections, moment and zones; each plan's input and answer; the page budget (p95
  under 50 ms); still live with no fault; the switch off; the same saved state with the check on as off; a resync; a throwing plugin.
- Kotlin, 41 tests: `DirectorShadowStreamTest` (the recorded day: 23 checks, 10 saves, 6 reads, 4 plans, 3 repairs, all the same, and
  redaction), `DirectorChecksTest` (each check on the day's real data, and each kind of change noticed: a text native writes otherwise, a
  strict finding, a deep text allowed, a read model, a missing preview, a zone offset, a moved session, a last bit, `computeTimeMs`),
  `TextMirrorTest`, `DirectorShadowJsonTest` (19 malformed messages), `DirectorShadowSessionTest` (switch, stale, Battery Saver, base on its
  way, resync, repeat, capture length, bad change, errors, slow worker, sampling, too large, report), `DirectorShadowWorkerTest`.
- Mutation checks: MUTATION_RESULT

## 8. Deviations from the economy check's plan

`ECONOMY_SHADOW_PLAN.md` section 7 sketched how this check joins; built differently, each for a reason:

- **Its own ring, switch and card** instead of the same ring and a second line on the economy's card: the owner can run either check
  alone, a stop of one (slow, errors) does not stop the other, and the economy card's tests and native fixtures stay as they are.
- **Its own `seq` per page session** instead of one across both scopes: no envelope here touches the economy's keys (commands that award,
  `scope: "both"`, are 5.1 C), so one ordered stream is not needed yet.
- **The read shadow is an envelope** (`director.read`, with the state it read) instead of a `shadowSeq` added to the projection: the
  projection only carries `week` while the native Life Director screen covers Week, and many owners will not use that screen.
- **The state is sent as text parts** (SH-5) instead of JSON values per top-level key, so native has the page's exact saved text for the
  codec's round trip.
- `logic.js` gains four behaviour-neutral lines (the `shadow()` and `planned()` helpers, the save notice, the `shadowReads` accessor); the
  read-model, host and adapter recorders' hooks still apply exactly once.

## 9. Open questions for the owner

- **OQ-S1** The fingerprint covers the engine version and the store schema only. A page update over the air that changes Life Director's
  words or logic without raising the version would show as differences rather than `stale`. Recommended: raise `ENGINE_VERSION` with any
  engine change, and accept that word changes show as differences until the app matches (they are visible in the report as `read.*`).
- **OQ-S2** Exit criteria (plan 5.1 point 4): 30 consecutive days with zero unexplained differences, covering a weekly rollover, a clock
  change or zone change, a skip with take-back, a missed block with regroup and a background timer. The last three need 5.1 C (commands
  and tick); recommended to count this stage's days towards the 30 for the read models, placement and the codec only.
- **OQ-S3** Each `strict.*` and `encode.wrongType.*` finding needs a ruling before cutover (store spec section 13).
