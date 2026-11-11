# Life Director store specification: `LifeDirectorStore` and the native store codec

Status: **complete for the store** (WP-LD7). It describes the page's Life Director store (`LifeDirectorStore`, the second module of
`engine.js`) as it behaves at branch `codex/director-dial-controls`, commit `8cfbf4f`, and the native codec that reproduces it
(`android-wrapper/native/src/main/java/com/lifemax/rpg/core/directorstore`). The page is the authority: this document describes it and
changes nothing. It is the store part of the Life Director engine specification (`LIFE_DIRECTOR_ENGINE_SPEC.md` points here); it has a
file of its own because the store is not scheduler code and has its own rule prefix. Nothing here is wired to a screen; the page is still
the only writer.

Paths: `engine.js` and `logic.js` are `lifemax_modular_v73/src/features/life-director/engine.js` and `logic.js`; Kotlin files are in
`core/directorstore` unless named otherwise.

Contents

0. How to read this
1. Boundary
2. Keys and the storage (LST-1, LST-24, LST-29)
3. The schema: `defaultState`, `validate`, `migrate` (LST-10 to LST-15)
4. Making a store (LST-2)
5. Load (LST-3 to LST-9)
6. Save and clear (LST-16 to LST-21)
7. The backup text (LST-22, LST-23)
8. Reading and writing JSON (LST-17, LST-26)
9. Native-only rules: strict reading, faithful writes, the host around the store (LST-25, LST-27, LST-28)
10. Defects (LS01 to LS12) and deviations (DV-1)
11. Golden cases: the index
12. Shadow mode: how the store joins WP-LD12
13. Open owner decision: where the key lives once native owns it, and the backup format's future

---

## 0. How to read this

Rule IDs are `LST-n` (the engine spec's `LDS-n` is sleep; do not confuse them). Defects are `LSnn`, native deviations `DV-n`. Each rule is:

- **Executed**: the golden file `lifemax_modular_v73/tests/fixtures/ld-store-golden.json.gz` holds cases recorded from the real page that
  show it (families or case ids are given), and `StoreGoldenTest` replays every one natively.
- **Read**: derived from reading the code only.

JavaScript's `undefined` (a member that is not there) and JSON's `null` are different throughout; "truthy" is JavaScript's truthiness
(`undefined`, `null`, `false`, `0`, `-0`, `NaN` and `""` are false). Lengths are in UTF-16 units, as JavaScript counts them.

## 1. Boundary

| Where | What | Native |
|---|---|---|
| `engine.js:2234-2340` | `LifeDirectorStore`: `create`, `defaultState`, `migrate`, `validate`, `KEY`, `BAK`, `CURRENT`; a store's `load`, `save`, `exportJson`, `importJson`, `clear`, `available` | `StoreKeys`, `StoreSchema`, `DirectorStore`, `BackupText`, `PageJson`, `JsValues` |
| `logic.js:71-83` `persist` | trims history and the change log, stamps `updatedEpoch`, calls `save` | LST-25 (Read); WP-LD9 |
| `logic.js:84-99` `boot` | `create(localStorage)`, `load`, then fills `ledger`, `questOverlay`, `hostSync`; banners from the report | LST-25 (Read); WP-LD9 |
| `logic.js:2384-2406` `Do.exportBackup`, `importBackup`, `reset` | the backup text, a restore that saves or changes nothing, a reset | LST-21 to LST-23; the restore is a sequence step in the cases |
| `33-android.js:45` | the widget reads `schedule` from the main key directly | consumer, re-pointed at cutover (section 13) |
| `33-android.js:173-175` | device backups copy and restore the main key (not `.bak`, not quarantine keys) | LS12 (Read) |

The store's text has not changed since the first commit that has it (`4672118`, then inside `logic.js`) except `clear()`, which before
`33eff52` (2026-11-06) copied the main key to `.bak` and removed the main key (family `older`).

## 2. Keys and the storage

**LST-1** Keys (Executed: every family). Main `lifemax_director_state_v1` (`engine.js:2244`); recovery copy `lifemax_director_state_v1.bak`
(2245); probe `lifemax_director_state_v1.probe` with the value `"1"` (2284); quarantine key for a key `k`: `lifemax_director_quarantine_`
followed by `k` with every UTF-16 unit outside `[A-Za-z0-9_]` replaced by `_` (2294), so `..._lifemax_director_state_v1` for main and
`..._lifemax_director_state_v1_bak` for `.bak`. `StoreKeys`.

**LST-24** Chromium's `localStorage` (Executed: family `browser`, on a clean origin's real storage). A write fails with
`QuotaExceededError`, message ``Failed to execute 'setItem' on 'Storage': Setting the value of '<key>' exceeded the quota.``, when the sum
over all keys of key length plus value length, counted in UTF-16 units whatever the characters (measured for ASCII, Latin-1, CJK,
surrogate pairs and lone surrogates), would exceed 5,242,880; the value being replaced no longer counts. Lone surrogates are stored and
read back unchanged. A failed write leaves the old value. `MemoryStorage(quotaUnits)` and the tests' `ScriptedStorage` count the same way,
and the browser family is replayed natively against that model (`browser/save-fills-exactly` fits to the unit, `save-one-over` does not).

**LST-29** The storage interface (native). `KeyValueStorage`: `read(key)` gives `Found(text)`, `Missing` or `Failed(StorageError)`;
`write` and `remove` give null or the `StorageError(name, message)`. Calls never throw. The store's rules are a pure state machine over
it, so production can back it with the web view's storage (shadow), a native file or key store (after cutover), and tests with a fake
that fails on any call (`ScriptedStorage`: the nth matching get/set/remove throws a named error; a write can be silently dropped or
truncated; a size cap).

## 3. The schema

**LST-15** `defaultState(o)` (Executed: `defaults/*`, every `NEW` and `MEMORY_ONLY` load) (`engine.js:2248-2258`). `o` falsy is `{}`.
Members in this order: `schemaVersion` 1, `playerId` (`o.playerId` if truthy, else `"player"`), `createdEpoch` and `updatedEpoch` (both
`o.nowEpoch` if truthy, else 0; the value is taken as it is, a string included), `setupComplete` false, `availability` {`timezone`
(`o.timezone` if truthy, else `"UTC"`), `sleepRequirementMinutes` 480, `rules` [], `blackouts` []}, `fixedCommitments` [], `goals` [],
`prefs` {}, `schedule` null, `ledger` null, `changeLog` [], `energyCheckin` null, `questOverlay` {}, `labQuests` [], `bridge` {`enabled`
false, `source` null, `mapping` null}, `lastGeneratedWeekKey` null, `ui` {`tab` `"today"`}.

**LST-10** `validate(s)` (Executed: family `validate`, `seeds/*/validate`, `numbers/schemaVersion/*`) (`engine.js:2259-2265`), first
failing check wins:
1. `s` falsy or not an object or array: `"not an object"`.
2. `s.schemaVersion` is not the number 1 (`!==`: `1.0`, `1e0`, `10e-1` are 1; `"1"`, `true`, `[1]` are not; NaN and `-0` are not):
   `"wrong schemaVersion " + String(s.schemaVersion)`, where String is JavaScript's ToString (`undefined`, `null`, `1e+21`,
   `123456789012345680000`, an array's items joined by `,` with `null` items empty, `[object Object]`, a string as it is, lone surrogates
   included). `JsValues.text`.
3. `s.availability` falsy, or its `rules` not an array (a string, number or array `availability` has no `rules`):
   `"missing availability"`.
4. `s.goals` not an array: `"missing goals"`.
5. Otherwise null (accepted). Nothing else is checked: every other member, known or not, may hold anything.

**LST-11** `migrate(raw)` (Executed: family `migrate`) (`engine.js:2266-2281`). A value that is falsy or not an object or array comes back
unchanged. An object or array whose `schemaVersion` is `undefined`, `null`, 0 or `-0` (`== null || === 0`) is the pre-release
prototype and becomes a new state (LST-12 to LST-14); any other comes back unchanged, the same object (so a v1 state keeps every member,
known or not). An array has no `schemaVersion`, so every array becomes a new state.

**LST-12** The prototype's sleep (Executed: `migrate/sleep/*`, `migrate/sleep-shape/*`). When `sleep` is truthy and `sleep.bed` is
neither `undefined` nor `null` (a string or array `sleep` has no `bed`), one rule is pushed: {`id` `"sleep"`, `kind` `"SLEEP"`, `title`
`"Sleep"`, `days` [0..6], `startMin` `bed` exactly as stored (any JSON value), `durationMin` `((ToNumber(wake) - ToNumber(bed) + 1440) %
1440) || 480`}. ToNumber is JavaScript's (`JsValues.number`): `undefined` NaN, `null` 0, booleans 1/0, strings by StringToNumber (trimmed
of JavaScript white space and line terminators incl. U+FEFF, U+2028 and the Zs spaces but not U+180E; empty is 0; `Infinity` with a sign;
`0x`/`0o`/`0b` without a sign; decimal with optional sign, `5.` and `.5` allowed, no `_`; else NaN), arrays and objects through their
String. `%` is JavaScript's remainder (sign of the dividend; the same as IEEE `fmod`), so the length can be negative (`bed` 3000, `wake`
0 gives -120); `|| 480` replaces 0, `-0` and NaN.

**LST-13** The prototype's work (Executed: `migrate/work/*`, `migrate/sleep-work/*`, loads and imports that throw). `w = sleep's
neighbour s.work`; if falsy it is `[]`. If it is then not an array, `migrate` throws TypeError `(s.work || []).entries is not a function
or its return value is not iterable` (a string, number, `true`, an object, even one with an `entries` member) [LS01]. Each item `w` at
index `i` gives a rule `Object.assign({ id: 'work' + i, kind: 'WORK' }, w)`: an object's own members in JavaScript's key order (array-index
keys ascending first, LST-17) override or follow `id` and `kind`, except a `__proto__` member, which is not copied (assigning it sets the
rule's prototype) [LS07]; a string spreads its UTF-16 units under the keys `"0"`, `"1"`, ... (lone surrogates included); an array its
items under their index keys; a number, boolean or `null` adds nothing.

**LST-14** The rest of the prototype (Executed: `migrate/goals|prefs|prefs-settings|settings|playerId|timezone/*`, `migrate/v0-prototype`,
seed `v0`). The new state is `defaultState({playerId: s.playerId, timezone: s.timezone})` (so `createdEpoch` and `updatedEpoch` are 0)
with `goals` = `s.goals` if an array else [], `prefs` = the first truthy of `s.prefs`, `s.settings`, else {} (any truthy value, a string
included), the rules of LST-12 then LST-13, `setupComplete` = at least one rule, and `migratedFrom` 0 added last. Every other member of the
prototype is dropped [LS07].

## 4. Making a store

**LST-2** `create(storage)` (Executed: every case with a storage; `defaults/memory-only`, `browser/full-storage-probe`)
(`engine.js:2282-2284`). It writes the probe key and removes it, once. If either call throws, the store is unavailable for its whole life
(`available` false) and keeps the error; nothing else is tried [LS10]. The probe counts against the quota: a storage with fewer than 32
units free makes the store unavailable. `DirectorStore.init`.

## 5. Load

**LST-3** Unavailable (Executed: `defaults/memory-only`, `browser/full-storage-probe`, sequences with a probe fault). `load(opts)` gives
`defaultState(opts)` and the report {`source` `"MEMORY_ONLY"`, `recovered` false, `quarantined` null, `migratedFrom` null, `available`
false, `error` `String(error)`} (`engine.js:2287`), where String of an error is `name: message`, or the one that is not empty
(`StorageError.toString`). Nothing is read.

**LST-4** Order (Executed: family `load`, `seeds/*/main|bak-only|behind-corrupt`, `older/old-clear/*`). The main key, then `.bak`
(2288). A read that throws is skipped and its error kept (not reported) (2290); a missing key is skipped (2291). Each key is read once.

**LST-5** Unparseable text (Executed: `load/corrupt-*`, `seeds/*/behind-corrupt`, `browser/quarantine-no-room`). When `JSON.parse` throws,
the text is written to the key's quarantine key, overwriting whatever was there; a failed write is ignored; `report.quarantined` becomes
that key if not set already (the first one named stays, even if its write failed [LS04]); the next key is tried (2293-2297). The damaged
key itself is left in place [LS03].

**LST-6** Parsed but refused (Executed: `load/invalid-*`). The value is migrated (LST-11) and validated (LST-10); a refusal sets
`report.invalid` to its reason (a later refusal replaces it) and the next key is tried (2298-2301). Nothing is written: the refused text
is not quarantined [LS02].

**LST-7** Accepted (Executed: `load/*`, `seeds/*`, `numbers/updatedEpoch/*`, `unknown/*`, `older/*`). `report.source` is the key,
`report.recovered` is whether it is `.bak`, and when the stored `schemaVersion` was not the number 1, `report.migratedFrom` is 0 for
`undefined` or `null` and the value itself otherwise (0 or `-0`, recorded as `{"$num":"-0"}`) (2302-2304). The state is the migrated value;
for a v1 state that is the parsed object itself, every member and number value as `JSON.parse` gave it.

**LST-8** Nothing accepted (Executed: `load/*`, `defaults/new/*`). `report.source` `"NEW"`, the state `defaultState(opts)` (2306-2307);
`quarantined` and `invalid` as LST-5 and LST-6 left them. Nothing is written; the first save then rotates whatever the main key holds into
`.bak` [LS09].

**LST-9** A migration that throws (Executed: `load/*` whose out is `threw`; `migrate/work/*`). The TypeError of LST-13 propagates out of
`load` (2299): no report, `.bak` not read, nothing written. In the page `boot` throws and Life Director does not start [LS01].
`LoadOutcome.Threw`.

The report's members, in the page's order: `source`, `recovered`, `quarantined`, `migratedFrom`, `available`, then `invalid` and `error`
when set. `LoadReport`.

## 6. Save and clear

**LST-16** Refusals before any write (Executed: family `save`, sequences). Unavailable: `"Browser storage is unavailable here; changes last
only until this page closes."` (2310). Invalid: `"Refusing to save invalid state: " + reason` (2311-2312). `JSON.stringify` cannot throw
on a state read from JSON, so `"Could not serialise state: ..."` (2314) is unreachable from stored data (a cyclic or BigInt state built
in memory only).

**LST-17** The text written (Executed: every save; `numbers/*`, `unknown/*`, `browser/lone-surrogates`) is `JSON.stringify(state)`:
numbers as `Number.prototype.toString` writes them, whatever text they were read from (`1.0` → `1`, `1e0` → `1`, `-0` → `0`, `1e21` →
`1e+21`, `123456789012345678901` → `123456789012345680000`, `1e400` read as Infinity → `null`); strings escaped as well-formed
`JSON.stringify` (`"`, `\`, `\b \f \n \r \t`, other units below U+0020 as `\u00xx`, lone surrogates as `\udxxx`, U+2028 as itself);
an object's members in JavaScript's own-key order: keys that are array indexes (the canonical text of an integer 0 to 4294967294; not
`"01"`, `"-1"`, `"4294967295"`) ascending first, then the others in the order they were first added (a repeated key in the text keeps its
first place and its last value); `__proto__` read by `JSON.parse` is an ordinary member and is written. `PageJson.text`.

**LST-18** Rotation (Executed: `seeds/*/into-empty|over-itself|rotates`, family `save`, sequences, `browser/rotation*`)
(`engine.js:2315-2320`), in this order: read the main key; if it holds a text different from the new one, write that text to `.bak`;
write the new text to the main key; read the main key back. Success when it reads back equal: `{ok: true, bytes: length of the text in
UTF-16 units}`. Saving the same text again leaves `.bak` alone, so `.bak` is the last different text, not the last save.

**LST-19** Storage failures (Executed: family `save`, sequences with storage steps, `browser/save-one-over`, `browser/rotation-over-quota`).
Any read or write of LST-18 that throws ends the save; the steps before it stay done (a rotated `.bak` stays when the main write fails).
The error is kept and the message is `"Storage is full. Export a backup and clear old Life Director history."` when `/quota/i` matches
`name + message` (joined without a space: a name `Error` with the message `quota` matches, as does a name `NotQuotaError`), else the
error's message, or `String(error)` when the message is empty (2321-2323).

**LST-20** Read-back (Executed: `save/*` with lossy writes). When the main key reads back as anything else (a write silently dropped or
truncated by the storage), the result is `{ok: false, error: "Write verification failed"}`; `.bak` and the main key keep what the writes
left [LS11].

**LST-21** `clear(opts)` is `save(defaultState(opts))` (Executed: `defaults/clear/*`, sequences, `older/old-clear-then-reset/*`)
(2336): the previous state is rotated into `.bak` like any save.

## 7. The backup text

**LST-22** `exportJson(state)` (Executed: `seeds/*/export`, `backup/export*`, `numbers/updatedEpoch/*`, sequences) (2326) is
`JSON.stringify({format: "lifemax-director-backup", version: 1, state}, null, 1)`: one space per level, a newline before each member and
before the closing bracket, `": "` after a key, empty objects and arrays as `{}` and `[]`, numbers and strings and member order as LST-17.
A `state` that is `undefined` is left out; `null` is written. `BackupText.write`, `PageJson.indented`.

**LST-23** `importJson(text)` (Executed: `seeds/*/import`, `backup/import/*`, `backup/round-trip*`, sequences) (2327-2332): `JSON.parse`
(a SyntaxError propagates; its words are V8's and are not part of the contract: native answers `ImportOutcome.NotJson` with its own
reason); when the result is truthy and its `format` is exactly the string `"lifemax-director-backup"`, its `state` (possibly `undefined`)
is taken, otherwise the whole value is (a raw state text imports too); `version` is never read; then `migrate` (its TypeError propagates,
LS01) and `validate`, whose refusal throws `Error("Backup is not valid: " + reason)`. logic.js `Do.importBackup` (2385-2391) shows the
error's message, else saves the state; if that save fails the previous state stays in memory and the reply is `"Not saved: " + error + "
Nothing was restored."` (the sequences' `import` step does the same).

## 8. Reading and writing JSON

**LST-26** Reading (Executed: every case; `depth/*`). Texts are read as `JSON.parse` reads them, with core/save's `JsonReader` (ECMA-404:
no BOM, comments, trailing commas, `NaN`, single quotes, leading zeros; `\u` escapes kept as the unit they name, lone surrogates included;
numbers to the nearest double and their text kept; a repeated key keeps its first place and last value). The page reads any depth;
native reads 512 levels and refuses deeper [DV-1].

LST-17 is how native writes as the page (`PageJson.text`, the default writer of `DirectorStore`). `PageJson.preserving` writes the same
layout but keeps each read number's own text; on a text the page wrote the two are identical (checked on every page-written text in the
file), and on a text the page did not write (`1.0`, `1e2`) the preserving writer keeps the text while the page would normalise it.

## 9. Native-only rules

**LST-25** The host around the store (Read; ported in WP-LD9). `persist` (`logic.js:71-83`) first drops `schedule.historyInstances` whose
`endEpoch` is not after now minus 21 days, keeps the last 60 `changeLog` entries and sets `updatedEpoch` to now, then saves; a refused save
sets the red banner `"Not saved: " + error`. `boot` (84-99) uses `localStorage` (or a storage whose calls throw `Error("no storage")` when
`localStorage` is not reachable), loads with `{playerId, timezone: device zone, nowEpoch: now}`, adds `ledger`, `questOverlay` and `hostSync`
when missing, and shows the banners: memory only; recovered ("Your last save was unreadable, so Life Director restored the previous good
copy. The damaged copy was kept as <quarantined or "a backup">."); upgraded from version `migratedFrom`. The seeds were written through
this host (section 11).

**LST-27** Strict reading (Kotlin `DirectorStoreCodec.decode`, `StoreReading.STRICT`; tests `StoreCodecTest`). After the page's own reading
(parse, migrate, validate), every field native will own must hold a JSON kind the page writes there (`OwnedFields`): numbers
(`schemaVersion`, `createdEpoch`, `updatedEpoch`, `migratedFrom`, `prefsLightUntil`, `availability.sleepRequirementMinutes`; `null` also
accepted, as `JSON.stringify(NaN)` writes it), strings (`playerId`, `availability.timezone`; `lastGeneratedWeekKey` and `prefsLightRestore`
also `null`), boolean `setupComplete`, objects (`availability`, `prefs`, `questOverlay`, `bridge`, `ui`, `hostSync`, `fixes`;
`schedule`, `ledger`, `energyCheckin`, `lastRejected` also `null`) and arrays (`fixedCommitments`, `goals`, `changeLog`, `labQuests`,
`availability.rules`, `availability.blackouts`). A field may be missing. A wrong kind is `StoreRefusal.WrongType(path, kind)`, naming
the field and never the value. Everything else, including the insides of `schedule`, `ledger` and `goals` (owned by the engine
packages), is carried as read, number texts included. `StoreReading.AS_PAGE` skips this check and accepts exactly what the page accepts:
it is for replay and shadow comparison and must not decide what native writes.

**LST-28** Faithful writes (Kotlin `DirectorStoreCodec.encode`; tests `StoreRoundTripTest`, `StoreFuzzTest`, `StoreCodecTest`). Encoding
the stored state unchanged gives the stored text itself, byte for byte. Otherwise the text is the preserving layout of LST-8, and it is
refused when the state holds a NaN or an infinity (`NonFinite(path)`: the page would store `null`), when `validate` or LST-27 refuses it,
or when the text does not read back to the same values with the same number texts (`NotFaithful`). Every result is typed; nothing throws.

Typed results everywhere: `LoadOutcome` (`Loaded(state, report)`, `Threw(name, message)`, `BeyondNative(key)`), `SaveOutcome`
(`Saved(units)`, `NotSaved(reason, message)` with `SaveRefusal` UNAVAILABLE, INVALID, STORAGE_FULL, STORAGE_ERROR, NOT_VERIFIED and the
page's message word for word), `ImportOutcome` (`Imported`, `NotJson`, `Invalid(message)`, `Threw`), `StoreDecode`, `StoreEncode`.

## 10. Defects and deviations

Each is behaviour the native store copies faithfully today (the golden cases pin it); "port as is" stands until the owner rules.

| ID | Where | What | Suggested ruling |
|---|---|---|---|
| LS01 | `engine.js:2275`, 2299, 2329 | A prototype-shaped state (no `schemaVersion`, or 0) whose `work` is truthy but not a list makes `migrate` throw. `load` throws, so `boot` throws and Life Director does not start on any later visit; `.bak` is never consulted. `importJson` shows V8's TypeError text to the player. Reachable only from a hand-edited or foreign text. | Treat as invalid (quarantine and fall back), at cutover |
| LS02 | 2301 | A text that parses but is refused is not quarantined. The next save rotates it into `.bak` (replacing the good `.bak` just loaded), the save after that discards it: a refused text is lost silently after two saves, against the module's own promise (2237). | Quarantine refused texts as unparseable ones are |
| LS03 | 2296, 2317 | After a recovery from `.bak`, the first save copies the damaged main text over `.bak`. Until that save's main write succeeds, the only good copy on disk is gone; if it fails (quota), storage holds two damaged copies and the state lives only in memory and the quarantine key. | Do not rotate a main text that failed to load |
| LS04 | 2295-2296 | `report.quarantined` names the quarantine key even when writing it failed (a full storage), so the banner says the damaged copy was kept when it was not (`browser/quarantine-no-room`). | Name it only when written |
| LS05 | 2294-2295 | One quarantine key per source key: each load that meets a damaged text overwrites the previous quarantined copy; quarantine keys are never removed and keep counting against the quota. | Keep, but report it in Settings |
| LS06 | 2288-2303 | A missing main key with a present `.bak` is reported `recovered`, and the banner says "Your last save was unreadable". States left by the old `clear()` (before `33eff52`) are brought back this way after a reset (`older/old-clear/*`). | Informational |
| LS07 | 2271-2277 | The prototype migration keeps only `goals`, `prefs`/`settings`, `sleep`, `work`, `playerId`, `timezone`; every other member is dropped. A work rule's `__proto__` member becomes the rule's prototype in memory (its members are then visible to the engine, never saved). | Port as is (no prototype states exist in the field) |
| LS08 | 2301 | `report.invalid` keeps only the last reason: when both copies are refused only `.bak`'s reason is reported. | Informational |
| LS09 | 2306, 2317 | When nothing loads, the player starts fresh and the first save rotates the refused main text into `.bak`, destroying the previous `.bak` (which was not quarantined either, LS02). | With LS02 |
| LS10 | 2284 | The probe is a write. With storage full (32 units or fewer free), the probe fails, the store is unavailable for the session, and `load` returns a new default state although the saved one is intact and readable: Life Director appears empty (`browser/full-storage-probe`; native `StoreFuzzTest` meets it on its own). | Probe by reading, or load before deciding |
| LS11 | 2317-2319 | A failed read-back leaves `.bak` rotated and the main key with what the storage kept; the next load reads that. | Informational |
| LS12 | `33-android.js:173-175` | A device backup holds only the main key; a device restore writes it straight to storage, with no rotation, validation or quarantine. | Fix at cutover (section 13) |

**DV-1** Depth (native). Native reads 512 levels of nesting (`JsonReader.MAX_DEPTH`) and the page any depth. A stored text deeper than
that, which the page would load, is not quarantined natively: `DirectorStore.load` stops with `LoadOutcome.BeyondNative(key)` and writes
nothing, and the codec refuses with `StoreRefusal.TooDeep`. Exactly 8 cases (`depth/load|save/512|513|600|2000`) differ for this reason,
and `StoreGoldenTest` requires that set and no other. No state the page writes comes near it (the deepest seed, the lived-in day, nests 7 levels).

## 11. Golden cases: the index

File `lifemax_modular_v73/tests/fixtures/ld-store-golden.json.gz` (3,925 cases, 15 MB unzipped, 0.46 MB zipped): written by
`tools/gen_ld_store_golden.js`, cases defined by `tools/ld_store_cases.js`, seeds captured by `tools/ld_store_seeds.js`, the store's text
loaded by `tools/ld_engine_vm.js` (`storeSource`, `loadStore`). The web test `tests/ld-store-golden.test.js` captures the seeds again from
the real page, defines the cases again from them, records every case again in Chromium and fails on any byte of drift; node records
every case but the browser family a second time and must agree on all of them (it does). Every case is run twice and must agree.

Seeds (texts the real page wrote, under Playwright's clock and `tests/fixtures/director-day.js`): `fresh` (a new player after Life
Director's own reset), `planned` (the lived-in day as the page plans it at boot), `lived` and `lived-bak` (after the day is played, and the
`.bak` the page left), `lived-export` (the page's backup text), `imported` (the main key after restoring that backup on a page holding only
a fresh state), `unfinished` (set-up not finished, after a guide is marked seen), `v0` (a prototype state after the page loaded and saved
it).

| Family | Cases | What |
|---|---|---|
| seeds | 74 | every seed loaded as main, as `.bak` only, behind a damaged main; imported; validated, migrated, saved into empty storage, over itself and over another, exported |
| validate | 421 | every check in order on JSON corner values: top-level kinds, `schemaVersion` × `availability` × `goals` shapes, NaN, `-0`, Infinity, message texts |
| migrate | 476 | top-level kinds; `schemaVersion` variants; 47 × 8 `bed` and `wake` values; `sleep` shapes; 27 `work` shapes (throwing ones, `__proto__`, index keys, strings with surrogates); `goals`, `prefs`, `settings`, `playerId`, `timezone` |
| defaults | 37 | `defaultState`, a `NEW` load and a `clear` under 12 option sets; memory-only |
| load | 1000 | main and `.bak` each absent, a seed, damaged (16 kinds of damage), refused, or a prototype; reads, quarantine writes and the probe failing; small quotas |
| save | 700 | 9 states × 7 storages × 12 fault sets × lossy writes × quotas |
| sequence | 400 | a load then 3 to 11 random steps: saves, edits, loads, reloads, resets, restores, exports, faults, quotas and outside changes |
| backup | 61 | 30 import texts (backups, raw states, wrong formats, versions, broken JSON, prototypes, a repeated `state`); exports of trees and states; import, export, reload round trips |
| numbers | 175 | 25 number texts in three fields, saved, loaded, exported; and as `schemaVersion`, validated and loaded (a `-0` migrates) |
| unknown | 44 | 11 unknown members (index keys, `__proto__`, empty and non-BMP keys, a repeated key, deep values) at the top of three seeds and inside `availability` and `ledger`, through load, save, edit, save, export |
| older | 13 | what the old `clear()` left, and states without the fields `boot` adds later |
| depth | 14 | nesting 10 to 2000 levels, loaded and saved (DV-1) |
| fuzz | 500 | random trees in random members of the small seeds, a fifth of them then damaged, through load, save, reload, load |
| browser | 10 | Chromium's real `localStorage` on a clean origin: the quota to the unit, rotation over and under it, a full storage's probe, a quarantine with no room, lone surrogates |

Native: `StoreGoldenTest` (every case; per-family counts fixed; DV-1's set exact), `StoreRoundTripTest` (every text in the file: the
page's seeds are in page normal form; decode then encode is the identity on every state text (over 1,500); a changed `updatedEpoch` changes
nothing else, number texts included), `StoreFuzzTest` (3,000 random trees through every entry point, 3,000 mangled texts, 1,000
save-then-load round trips under random quotas), `StoreCodecTest` (LST-27, LST-28, refusals, storages that fail, JavaScript coercions).
Mutations (each run against the four store test classes):

43 source mutations of `core/directorstore` (one change each; the store tests run against it); 42 are caught:

| Mutation | Caught by |
|---|---|
| quarantine keeps dots | StoreCodecTest, StoreGoldenTest (69 of 3925 golden cases) |
| probe never removed | StoreCodecTest, StoreFuzzTest, StoreGoldenTest (2554 of 3925 golden cases) |
| bak read before main | StoreGoldenTest (1792 of 3925 golden cases) |
| failed read stops the load | StoreGoldenTest (91 of 3925 golden cases) |
| quarantine names the last key | StoreGoldenTest (19 of 3925 golden cases) |
| quarantine not written | StoreGoldenTest (337 of 3925 golden cases) |
| first invalid reason kept | StoreGoldenTest (7 of 3925 golden cases) |
| migratedFrom 0 for -0 | StoreGoldenTest (1 of 3925 golden cases) |
| bak always rotated | StoreGoldenTest (626 of 3925 golden cases) |
| bak failure ignored | StoreGoldenTest (83 of 3925 golden cases) |
| no read-back | StoreCodecTest, StoreGoldenTest (8 of 3925 golden cases) |
| quota by name only | StoreGoldenTest (52 of 3925 golden cases) |
| empty message not replaced | StoreGoldenTest (7 of 3925 golden cases) |
| error text with a dash | StoreGoldenTest (273 of 3925 golden cases) |
| memory-only report says available | StoreGoldenTest (274 of 3925 golden cases) |
| schemaVersion "1" accepted | StoreGoldenTest, StoreRoundTripTest |
| availability object required | not caught: equivalent. Every JSON value that is truthy but not an object has no `rules` member, so the two conditions agree on all stored data |
| arrays not objects | StoreGoldenTest (57 of 3925 golden cases) |
| v0 only when missing | StoreGoldenTest (41 of 3925 golden cases) |
| sleep length without default | StoreGoldenTest (216 of 3925 golden cases) |
| sleep length unwrapped | StoreGoldenTest (187 of 3925 golden cases) |
| prefs ignore settings | StoreGoldenTest (3 of 3925 golden cases) |
| __proto__ copied | StoreGoldenTest (3 of 3925 golden cases) |
| strings not spread | StoreCodecTest, StoreGoldenTest (5 of 3925 golden cases) |
| setupComplete always true | StoreGoldenTest (191 of 3925 golden cases) |
| work rule ids from 1 | StoreGoldenTest (16 of 3925 golden cases) |
| default tab week | StoreGoldenTest (1558 of 3925 golden cases) |
| nowEpoch not defaulted | StoreGoldenTest (3 of 3925 golden cases) |
| index keys in insertion order | StoreCodecTest, StoreGoldenTest (14 of 3925 golden cases) |
| index keys sorted as text | StoreGoldenTest (1 of 3925 golden cases) |
| page form keeps number texts | StoreGoldenTest (52 of 3925 golden cases) |
| export indent 2 | StoreGoldenTest, StoreRoundTripTest (273 of 3925 golden cases) |
| export without space after colon | StoreGoldenTest, StoreRoundTripTest (273 of 3925 golden cases) |
| backup format ignored | StoreGoldenTest (48 of 3925 golden cases) |
| stringNumber without hex | StoreCodecTest, StoreGoldenTest (6 of 3925 golden cases) |
| array index limit 2^32-1 | StoreCodecTest, StoreGoldenTest (4 of 3925 golden cases) |
| js space without line separator | StoreCodecTest, StoreGoldenTest (6 of 3925 golden cases) |
| null array items written | StoreCodecTest, StoreGoldenTest (6 of 3925 golden cases) |
| strict reading off | StoreCodecTest |
| non-finite allowed | StoreCodecTest |
| no value read-back check | StoreCodecTest |
| unchanged state re-encoded | StoreCodecTest, StoreFuzzTest, StoreRoundTripTest |
| deep text quarantined | StoreCodecTest, StoreGoldenTest (4 of 3925 golden cases) |

## 12. Shadow mode: how the store joins WP-LD12

Designed for, not built (`ECONOMY_SHADOW_PLAN.md` section 7). In shadow the page stays the only writer and native never touches the web
view's storage. The page sends the director scope (`scope: "director"`, the top-level members of `ST` as JSON text, then record deltas
for `schedule.scheduledInstances`, `schedule.historyInstances`, `ledger.events`, `changeLog`) over the economy's transport once
`director.shadow.v1` is advertised. Native keeps a second in-memory copy:

- the mirror is a `DirectorStore` over a `MemoryStorage` (no quota), filled from the page's text with `DirectorStoreCodec.decode(text,
  AS_PAGE)` so that it accepts exactly what the page accepts, and compared with `StoreReading.STRICT` separately (a strict refusal is a
  finding, not a divergence);
- the director epoch is raised by `Do.importBackup`, `Do.reset`, a load that reports `recovered` or `quarantined`, and a device restore;
  on a new epoch native drops its mirror and decodes the page's full text again;
- for each page `persist`, native computes the text it would have written (`PageJson.text` of its mirror after the command) and compares
  it with the page's text byte for byte; the report carries field paths and lengths, never values.

## 13. Open owner decision: where the key lives once native owns it

Today the key lives in the web view's `localStorage`, which native code cannot read without the web view running. Recommendation:

1. **During shadow (WP-LD12):** no change. The page writes `localStorage`; native holds only the in-memory mirror (section 12).
2. **Cutover (WP-LD13), one release:** native storage becomes the home: an app-private file per key (`lifemax_director_state_v1.json`,
   `.bak.json`, quarantine files), written atomically (write a temporary file, sync, rename) behind `KeyValueStorage`, with the same
   rotation and read-back rules (LST-18 to LST-20) so the contract and the golden cases still hold. A quota is no longer needed; keep a
   soft cap with the page's words so a runaway state is still caught.
   - Migration, once, on the first start of the native build: if the native main file is missing, read the web view's main key and `.bak`
     through the page, decode each (`AS_PAGE`), and write both native files byte for byte (main to main, `.bak` to `.bak`); copy any
     quarantine keys as they are. Then record the migration in a marker file. Never delete the web view's keys in this release.
   - Mirror: after every native save, native hands the page the new text and the page writes it to `localStorage` unchanged (no
     rotation of its own), so the widget reader (`33-android.js:45`), the device backup (`LMA_EXTRA_KEYS`) and a rollback still see current
     data.
   - Re-point the consumers (plan 5.2 item 4) to native: the widget, device backups (which should then include `.bak` and the quarantine
     files, fixing LS12), Money's `shifts`/`workRules`, voice, the coach, the Overview glance.
3. **Rollback:** a switch makes the page the writer again. Because the page's `localStorage` mirror was kept current, the page loads it
   with its own `load` and carries on; native stops writing and, on the next switch back, re-runs the migration step from the page's
   keys. Rollback needs no data conversion in either direction because both sides write the same text (LST-17).
4. **The release after:** stop the mirror, keep reading the web view's keys once more for anyone who skipped a release, then remove them.

The backup text's future: keep `lifemax-director-backup` version 1 exactly as today (LST-22, LST-23) so backups move both ways between
page and native builds. If the format ever changes, raise `version` and have `importJson` read `version` (today it is ignored), and keep
accepting version 1 and raw state texts. The words a native import shows for a text that is not JSON are native's (LST-23); the owner may
want them reviewed.

Defects to rule on before cutover: LS01, LS02 and LS03 (data can be lost or Life Director can fail to start), LS10 (the player sees an
empty Life Director when storage is full) and LS12 (device backups). The rest can be ruled when the host (WP-LD9) is ported.
