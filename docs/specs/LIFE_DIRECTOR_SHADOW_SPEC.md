# Life Director shadow check: specification (WP-LD12)

Status: **built, two stages**: stage 1 (branch `logic/ld12-shadow`, Native API 135) compares the store, the read models, placement and
repair; stage 2 (WP-LD12b, branch `logic/ld12b-shadow`, Native API 136) adds the host commands and the tick (section 3b). Capability
`director.shadow.v1` throughout. The native Life Director code that already exists runs beside the page and is compared with it; the page stays the only writer and the only thing the owner sees. This
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
3b. Commands and the tick (SH-36 to SH-49)
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
| Host commands (WP-LD9; WP-LD10's `Today.timer`) | every `Do.*` and `Today.*` command, and `finishInstance` and `logToday` when called on their own (the page's Done button, voice), with the state and controller memory before, the payload, the moment, every host and store call with its answer, the reply, and the memory and state after | `HostCommands.run` against `PageAnswers` (the page's answers) | `director.cmd` |
| The tick (WP-LD9) | the tick's body while Life Director is on screen, and `syncHost`, `processMisses`, `maybeRollover` when the Overview's glance runs them | `HostCommands.run` (`Tick`, `SyncHost`, `ProcessMisses`, `MaybeRollover`) | `director.tick` |

Stage 2 also covers the ledger's own engine calls (`recordCompletion`, `detectMisses`, `applyMissConsequence`, `reconcileQuestProgress`):
they run inside the commands, and their results are in the reply and the state after.

Not yet: what the economy itself would answer an award (WP-LD11's `EconomyHost`) is not compared; the page's answer is replayed (SH-48,
a gap). The timer's once-a-second check (`focusTick`) is a command here only when it finishes a timer (`Today.timer autoFinish`,
SH-47). Boot is compared only inside `Do.reset`. The dial's `applyDial` and Today's other parts (WP-LD8b). The ledger is covered as part
of the saved state too (its events and order travel in the store text and round-trip through the codec, SH-9).

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
  `read` (`director.read`), `engine` (`director.plan`, `director.repair`) or `cmd` (`director.cmd`, `director.tick`; SH-40). Decoding is strict (`DirectorShadowJson`, on `core/save`'s
  `JsonReader`): exact keys, whole numbers, `base < seq`, `t1 >= t0`, a known zone, a flag word, a kind `director.<word>`; a refusal names a key,
  never a value. A kind native does not know is read for its changes and counted `skipped.unknownKind`.
- **SH-4** The fingerprint is the engine's version and the store's schema (`LifeDirectorEngine.ENGINE_VERSION + "/" + LifeDirectorStore.CURRENT`,
  native `PlanSchedule.ENGINE_VERSION` and `StoreKeys.SCHEMA_VERSION`): `1.1.0/1` (stage 1 sent `1.0.0/1`). OQ-S1 is accepted: the
  version is raised, page and native together, with any change to the engine or the host commands; stage 2 raised it to `1.1.0`. The
  version is also written into every plan (`engineVersion`), so the engine's goldens were rewritten with the substitution a re-record
  makes (only `engineVersion`, the same length; `director-week-golden.json`'s Diagnostics line likewise). The native
  `director-settings.json` fixture keeps its recorded Diagnostics line `engine 1.0.0`, so the native screenshots stay as they were. Read:
  the fingerprint still does not cover the page's words.
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

## 3b. Commands and the tick (SH-36 to SH-49)

**What runs through the check.** `logic.js` routes every command through `command(op, name, payload, run)`. The changes are
behaviour-neutral: the helper itself; `Do` and `Today` built by `commands()`, which wraps each member; `finishInstance` and `logToday`
split into the wrapper and `finishRun` / `logTodayRun`; the tick's body (after its two on-screen guards, which the host golden's hook
still finds); the glance's three steps; `render` counting `presenting` around `renderNow`; and the `shadowVars` accessor beside
`shadowReads`. With the check off, `command` is `run()`. With it on, only the outermost command is told (a `finishInstance` inside
`Today.done`, the tick's steps inside the tick are not told again): `LmaDirectorShadow.command(...)` before it runs, `end(answer, throw)`
after. The host golden (`ld-host-golden`) and timer golden re-record unchanged.

- **SH-36** The ops are `today` and `do` (by name, with the payload as given: the page's own buttons and a native screen's
  `nativeTodayRun` / `nativeRun` alike; the boot `nativeRun` does first is outside the command), `finish` (`{id, minutes, opt}`) and
  `logToday` (`{id, minutes, opId}`), which travel as `director.cmd`; and `tick`, `syncHost`, `processMisses`, `maybeRollover`, which travel
  as `director.tick`. Native runs `HostCommands.run(state, nowMs, host, command)` with the same command (`HostCommand.Today`, `Do`,
  `Finish`, `LogToday`, `Tick`, `SyncHost`, `ProcessMisses`, `MaybeRollover`); an op it does not know is skipped (`unknownOp`).
- **SH-37** While the command runs, the page module wraps the eleven `LifeMaxHost` functions the commands call (HC-4), the store's `load`,
  `save`, `clear`, `exportJson` and `importJson` (and `LifeDirectorStore.create`, so the store `reset` makes is wrapped too) and
  `crypto.randomUUID`, and restores every one when the command ends (the page test checks the host is itself again). Each wrapper calls
  the real function and returns its real answer; it notes `[fn, args, answer]` as the host golden's recorder does (`tools/ld_host_vm.js`),
  except a call the host makes of itself (`H.playerId` asks `H.connected`) and a call made while the screen draws (`presenting`: `render`
  asks `connected` and `xpFor` for its own words; the goldens never drew). `activitiesSince(null)` is noted as its one latest activity
  (`syncHost` reads only whether there is one and the latest date). Arguments and answers travel as JSON texts kept once per command
  (`texts`; calls name them by index). A host function that throws marks the command and it is not sent (`host`). Native's
  `PageAnswers` answers each of native's calls with the page's answer at the same place; a call of another name there, or one past the
  page's last, is a difference (`<op>.calls[i].fn`, `<op>.calls[#]`), and so is a page call native never made (`<op>.calls[#]`). The ids
  are the page's minted ids, in order. Native never reaches the economy, the quests, the store or the screen (SH-34).
- **SH-38** Compared: every host call's arguments (`<op>.<name>.calls[i].<fn>`; `store.save` takes none, the state is compared instead),
  the reply (`.reply`; a throw is `{threw, message}` as in the goldens; `finish`'s and `logToday`'s ledger answer without its `ledger`,
  which is the state's), the controller's memory after (`.session`: banner, notice, tab, day, the held dial change, the take-back, the
  take-back's op, the last save, the last refused completion), and the state after (`.state`); each in the page's normal form, with
  `computeTimeMs` left out wherever it is (a timing). The host's answers are not compared: native's are the page's by construction.
- **SH-39** The memory before travels as the page module's `session()` and becomes native's `HostState`. The states travel as changes:
  the envelope's `ops` take native's copy to the state before the command (a save inside the command has already moved the copy on; the
  changes take it back), and `cmd.after` from there to the state after, where the copy stays. The page test checks both stitched texts
  against `JSON.stringify(ST)` before and after every command, byte for byte.
- **SH-40** The envelope's `cmd` is `{op, name, payload, nowMs, deviceZone, planZone, planOffset, retires, ids, calls, texts, reply,
  session: {before, after}, after}`, decoded strictly as the rest (exact keys; call indexes inside `texts`; every text JSON; a function
  name of letters with an optional `store.`; an op of letters; `director.tick` exactly for the four tick ops). A refusal names the key.
- **SH-41** The Skip's take-back holds a copy of the plan and the ledger and the plan's text (HC-17): about twice the plan. It travels in
  full the first time a command carries it in its memory before (again after each base), then as `{id, text, at, digest}`; native
  keeps the latest two it was sent, by digest. The digest is FNV-1a over the UTF-16 code units, with the length, of the copy as JSON
  text and of the plan's text, each with every `"computeTimeMs":<n>` as 0 (the plan's timing is the page's own, so the take-back
  native makes after a Skip is compared by this digest). A full take-back whose digest native computes otherwise is a difference
  (`.takeBack.digest`); a digest native does not hold is skipped (`takeBack`).
- **SH-42** The plan zone's offset at the command's moment, computed by the page's engine, is compared with native's before the run, as
  SH-18 (`tzdata`).
- **SH-43** The moment is `Date.now()` as the command starts (`nowMs`); the module notes when it ended (`t1`). A command whose start and
  end are in different seconds is flagged `second` and skipped, as SH-19: `nowEpoch()` may have read either. Inside one second the page
  still reads the clock in milliseconds (`say`'s notice, the take-back, the timer's `start` and `pause`) while native has its one
  moment: a number native wrote as its moment is the same as the page's when the page's lies within the command (`nowMs` to `t1`, both
  included).
- **SH-44** A command's native run is timed: one longer than 1.5 s makes native compare one in four of the next commands and ticks
  (`sampled`), as SH-24 for plans.
- **SH-45** The page's cost is the capture before, the wrappers' notes and the capture after, never the command's own work; it counts in
  SH-23's budget with the envelope's building.
- **SH-46** Sizes: an envelope of a command past 512 KB is sent after a base of the state before it, and skipped (`size`) when still too
  large; the next change starts from the state before. A take-back that did not fit is sent again with the next command.
- **SH-47** Rates: a tick or tick step that changed nothing (no answer, the same state and memory) is sent at most once a minute per op
  (`quiet`; the glance runs every 30 s on the Overview). The timer's own finish (`Today.timer` `autoFinish`, which the page retries every
  second while it is refused, LT07) is sent at most once a minute per timer (`repeat`).
- **SH-48** Gap: where the economy's answer matters (an award, a quest check-off), native replays the page's answer and compares the
  arguments. Comparing the answer with what WP-LD11's `EconomyHost` would give needs the economy's state at the instant of the award,
  which only the economy check's own copy holds, in its own order (`scope: "both"`, `ECONOMY_SHADOW_PLAN` section 7). Not built: no
  cheap capture exists on the page (the main save is megabytes and is the economy check's to send). **Read.**
- **SH-49** Redaction as SH-31: payloads, titles, notices and backup texts reach native's memory only; the ring keeps paths.
  `DirectorCommandsTest` checks that no goal title, activity name, id or operation id from the day's commands is in the ring or the
  report.

**What a command difference could be (the allowlist).** Native ports the host commands with the page's defects as they are (host spec
section 10, timer spec, ledger spec: no native deviations), so a known LH, LT or LG defect is reproduced on both sides and is no
difference. Over the lived-in day with the real LifeMax host, every command and tick agreed. What could still show:

| Would show as | Cause | Proposed |
|---|---|---|
| `<op>.calls[i].activityExists[0]`, `.state.hostSync...` | a LifeMax activity whose id is not text (native's `HostActivity.id` is a `String`, the page keeps the value); the app writes text ids today, an old save may hold numbers | fix natively (keep the page's value); allowlist only if the owner wants it tolerated first: **DA-3** (proposed, not added) |
| `<op>.state...`, `<op>.session...` | `render` changing the state or memory inside a command (drawn on the page, never in the goldens); not seen | a difference until ruled |
| `<op>.calls[#]`, `<op>.calls[i].fn` | a host answer of a shape the goldens never had; not seen with the real host | a difference |
| none | LH01 to LH14, LT01 to LT17, LG defects: reproduced on both sides (LH02 and D8 through the digest of SH-41; LH11 and LT07 rate-limited by SH-47; LT05 and LT06 in the tick and the misses) | none needed |
| not compared | D1 (the award's day by the device's zone) is in the economy's answer (SH-48) | none until `scope: "both"` |

No allowlist entry is added; DA-1 and DA-2 still apply to command paths (a last-bit number is DA-2).


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
- Kotlin, 44 tests: `DirectorShadowStreamTest` (the recorded day: 23 checks, 10 saves, 6 reads, 4 plans, 3 repairs, all the same, a read across a second skipped, and redaction), `DirectorChecksTest` (each check on the day's real data, and each kind of change noticed: a text native writes otherwise, a
  strict finding, a deep text allowed, a read model, a missing preview, a zone offset, a moved session, a last bit, `computeTimeMs`),
  `TextMirrorTest`, `JsonPathsTest`, `DirectorShadowJsonTest` (19 malformed messages), `DirectorShadowSessionTest` (switch, stale, Battery Saver, base on its
  way, resync, repeat, capture length, bad change, errors, slow worker, sampling, too large, report), `DirectorShadowWorkerTest`.
- Mutation checks: 28 source mutations of `core/directorshadow` (each compiled over the built main classes and the tests above run):
  one did not compile; of the other 27, 26 are caught. The survivor is equivalent: a split object's dropped members kept in memory
  (`TextMirror`, `retainAll` removed) are never stitched, since the member list decides what is written. The first sweep left 8 more
  alive; `JsonPathsTest` and new cases in `DirectorChecksTest` and `DirectorShadowStreamTest` (a space the page never writes, a preview
  for other minutes, a missing day, an absent `rejectedMove`, a moved session that must stay unexplained, a read across a second) were
  added for them.
- Unchanged with the four lines in `logic.js`: `ld-host-golden` and `ld-readmodels-golden` (both re-record in Chromium and compare),
  `native-director`, `director-finish`, `native-settings` (123), `native-shadow` (60), `native-shadow-card` (54),
  `native-shadow-transparent`, `demo-mode`.

Stage 2 (WP-LD12b):

- Page: `tests/native-director-shadow.test.js` now 35 checks. Added: 21 commands a native Today, My time and Settings send, in a
  person's order (pin and its repeat, skip, take-back, skip again, done and done again, log for today and its repeat, the timer's
  start, pause and cancel, block hours, energy, re-plan, a goal's priority, a rest day, intensity, a custom goal, a backup, a gone
  goal), each one envelope with its op, name and payload, native's copy the page's state before and after it byte for byte, its reply;
  a done's award, quest read, save and toast with the award's arguments and answer; the take-back in full once, then by digest; nothing
  drawn counted; no command across a second; the tick on screen with an activity logged elsewhere taken in; the glance's three steps,
  quiet ones once a minute; the timer's own finish once a minute and the host itself again after a command. The lived-in day's stream
  (`director-shadow-stream.json.gz`, re-recorded) now holds 22 commands and 26 ticks besides the saves, reads, plans and repairs.
- Kotlin: 58 tests in `core/directorshadow` and `DirectorShadowWorkerTest` (stage 1's 44, `DirectorChecksTest` reading commands in the
  stream, and `DirectorCommandsTest`, 14): every command and tick of the day replays to the page (reply, calls, memory, state); a reply,
  a missing, extra or other host call, an award's arguments, an award the economy refused (native then words and records it otherwise),
  the state and the memory after, the memory before (a tab, a failed save), a completion's options, the clock window at both ends,
  `computeTimeMs`, the take-back's digest and an unknown one, `tzdata`, an unknown op, the tick following LifeMax's log, a host without
  `retiredUntimedQuest`, `PageAnswers` itself, `second`, sampling, redaction of what commands carried, and malformed `cmd` members.
- Mutation checks: 34 source mutations of the new code (`CommandCheck`, `PageAnswers`, the `cmd` reader, the session's command path, `JsonPaths`' skip and clock rule), each compiled over the built main classes and the tests above run: all 34 compiled; 33 are caught. The survivor is equivalent: the ledger left out of every reply instead of only a completion's (no other reply has a `ledger`). The first sweep left 10 alive; the clock window's end, the memory before (a tab, a failed save), a completion's options, `PageAnswers` itself, a host without `retiredUntimedQuest` and a call index at the end were added for them.
- Unchanged with `logic.js`'s command hooks: `ld-host-golden` and `ld-timer-golden` (re-record in Chromium and compare: the vm's hooks
  are still found once), and the earlier list.

## 8. Deviations from the economy check's plan

`ECONOMY_SHADOW_PLAN.md` section 7 sketched how this check joins; built differently, each for a reason:

- **Its own ring, switch and card** instead of the same ring and a second line on the economy's card: the owner can run either check
  alone, a stop of one (slow, errors) does not stop the other, and the economy card's tests and native fixtures stay as they are.
- **Its own `seq` per page session** instead of one across both scopes: no envelope here touches the economy's keys. Stage 2 compares
  commands that award, but with the page's own award answer (SH-48), so one ordered stream is still not needed; `scope: "both"` would
  need it.
- **Commands as one envelope each** (`director.cmd`, `director.tick`) instead of `director.award` as an economy kind (section 7 there):
  the award is one call among a command's host calls here.
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
  change or zone change, a skip with take-back, a missed block with regroup and a background timer. Stage 2 covers the skip with
  take-back and the missed block with regroup (commands and tick); the background timer needs WP-LD10's background work, which the page
  does not have (it is compared when the page finishes a timer). Recommended: count days from stage 2's release for all five, the
  timer as the page's own finish until native runs it in the background.
- **OQ-S3** Each `strict.*` and `encode.wrongType.*` finding needs a ruling before cutover (store spec section 13).
- **OQ-S4** The allowlist proposal of section 3b: no entry is added; **DA-3** (a LifeMax activity id that is not text) is proposed only
  if the owner wants it tolerated before native reads such an id as the page does. Recommended: no DA-3, fix natively when one shows.
- **OQ-S5** SH-48: compare what the native economy would answer an award (`EconomyHost`) through `scope: "both"`, one ordered stream
  across the two checks? Recommended before cutover (D1 and D2 live in that answer).
- **OQ-S6** The settings line still says the phone works out "Life Director's plans, its week and what it saves"; it now also replays
  what your taps do. The words were left as they are (the screenshots stay; no update-history entry). Change them?
