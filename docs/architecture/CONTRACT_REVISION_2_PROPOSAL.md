# Contract revision 2: proposed amendments

Status: folded into `CONTRACT.md` as Revision 2 on 2026-10-30 at the owner's direction. This file is kept as the rationale and evidence record; where the two differ, `CONTRACT.md` governs.
Author: Claude (written against the repository at Native API 17, 2026-10-30).

This document does three things. It says what in Revision 1 should stay exactly as it is. It lists gaps that cost us real bugs this week, with evidence. It then proposes numbered amendments (A22 onward, plus edits to A04, A16, A17 and A21) that close those gaps. Every claim about the repository is marked **verified** (I ran or read it) or **proposal**.

## 1. What to keep untouched

Revision 1's strongest parts are the ones that stop us lying to ourselves, and they should survive any revision:

- Presentation and authority are tracked separately (section 1). A Compose screen over a web-owned save is `NATIVE_SCREEN` + `WEB_OWNED`, and the delegated web sheets are listed as dependencies.
- The mutation gate (A14, A15): no native write capability without operation identity, an epoch, one commit boundary and tested reconciliation. This is the part most likely to prevent a corrupted save.
- Freshness rules (A13): a session id plus a monotonic sequence, and late replies may never overwrite newer state.
- Evidence discipline (A21 and the ADR's "required evidence"): an appealing directory tree is not evidence.
- The rejected-ideas list in ADR 0001 (rollback via `JSON.parse`, a 64-entry receipt ring). Keep it as a permanent "do not reintroduce" record.

## 2. Gaps, with evidence

### G1. Navigation is specified as "one coordinator" but built as a chain of independent hooks

A16 requires one coordinator. **Verified:** 18 separate runtime segments wrap `navigate()` (`grep -l "navigate=function"`), and each native tab (Overview, Character, Food) opens and closes itself from its own wrapper, with its own timers and its own "blocked" test. Order of wrapping is load order.

**Evidence it bites:** the old logic closed the tab you left before the next one opened. In a simulation with fake native screens and random delays (`tests/native-handover.test.js`, **verified**), the old logic fails 3 of 4 checks: the screen you left is gone before the new one is up (a gap in which the covered web page shows through), the final state after rapid switching is wrong, and several hops settle on no screen at all. On a phone this showed as flashing, and as two headers drawn at once.

### G2. The shell (top and bottom bars) is a DOM screenshot, re-captured on every tab change

**Verified:** `NativeOverviewSnapshot.capture()` walks the web DOM, reads computed styles node by node and ships a vector scene to Android, which redraws it. The scene contains the highlighted tab, so every tab change needs a fresh capture, and a stale or half-transitioned capture shows the wrong tab. This is exactly the bug in the screenshots of 2026-10-30 (Character page, Food highlighted).

A17 already says "do not use whole-page software snapshots as a substitute for native live UI". The shell is a small snapshot, but it is a snapshot, and it is the single most fragile component in the tab path. Today it is an undocumented exception.

### G3. The performance baseline in A17 is not what ships

A17 requires release or profileable builds for acceptance. **Verified:** until this week CI built and published `assembleDebug` with `debuggable` on, and the published APK is what the owner runs. Compose in a debuggable build is several times slower than in a non-debuggable one. All screens measured on the phone were measured in the slow configuration. (Fixed in commit `87e1b51`; the rule below keeps it fixed.)

**Verified measurement caveat:** the emulator test's "switch time" polled every 250 ms and ran JavaScript per check, so its 700 to 1,200 ms figure was mostly test overhead. Page-side phases were 50 to 460 ms. A performance budget needs a measurement that cannot be dominated by the test itself.

### G4. Hidden-but-alive screens need rules

Keeping Character and Food composed between switches (instead of rebuilding them) removes the cold-start cost, but a hidden Compose screen keeps its composition. **Verified:** the Food screen registers an unconditional `BackHandler`, so a hidden Food screen would have swallowed the Android Back button on other tabs (fixed by an `active` flag). The contract has no rule for this class of hazard (back handlers, key and focus handling, animations, update delivery, memory).

### G5. Code-quality gates cover only the newest slice

**Verified:**

| Measure | Value |
|---|---|
| `NativeNutritionPlugin.kt` (legacy flat directory) | 1,903 lines in one file |
| ktfmt check in CI | `native/src` only; the flat legacy directory is unchecked |
| `50-native-overview.js` | 158 lines, 41 lines over 160 characters, longest line 1,143 characters |
| Lines over 400 characters across the 80 runtime segments | 362 |
| JS formatter or linter config in the repo | none |
| Bridge catalog entries | 1 (Character). Nutrition, Overview shell, Cash and the tab shell have none |
| Ledger baseline table | build 172; the current native state is not recorded |

The contract promises "beautifully organised" code in spirit (A09) but gives no mechanism that stops the legacy tree and the JS segments from staying unreadable, or from growing.

## 3. Proposed amendments

### Edits to existing articles

**A04 (host).** Add: the destination host is a state machine with named states (`Idle`, `Opening(target)`, `Showing(target)`, `Closing(previous)`), not a set of independent open/close calls. The one-host rule applies to the tab shell and the tab bodies together.

**A16 (coordinator).** Replace "one coordinator owns primary-destination selection" with the testable form in A22 and A23 below.

**A17 (visual and performance).** Add: the shipped artifact is the measured artifact. CI must publish a non-debuggable build. Add the budgets in A25.

**A21 (verification).** Add the simulation tier in A28.

### New articles

**A22. The shell is native state, not a captured drawing.**
The web layer publishes a small semantic `ShellState` (proposal):

```
ShellState { sequence, tabs: [{id, label, iconRef}], activeTab, theme: {tokens...}, topbar: {title, actions[]} }
```

Native draws the bars from that state. The active tab is data, so a tab change never needs a DOM capture and can never show a stale highlight. Until this exists, the DOM capture path is a documented exception with a retirement condition (all three native tabs read `ShellState`). While the exception stands: capture only after `lmaSyncActive()`, with transitions disabled, and key any cache on the lit tab. (This is what commit `6656999` does.)

**A23. Make-before-break hand-over between native destinations.**
Rules, all enforced by tests (see A28):

1. The destination opens first. The previous destination closes only after the new one reports shown or failed. A timeout (proposal: 2.5 s) is the last resort and is recorded as a fault in the trace (A29).
2. Going to a web destination closes the previous one immediately.
3. Every scene or state message carries the page's monotonic sequence. The receiver ignores a message older than the one it holds.
4. Shared UI (the bars) has an owner token. Only the current owner may release it. A release from a stale owner is a no-op.
5. At most one set of bars is visible at any time.
6. Every close path (navigation, observers, refresh handlers, errors) goes through the same hand-over. A direct close that bypasses it is a defect.

(Implemented in JS as `NativeTabs` and natively as `ChromeHost` in this repository. See ADR 0002.)

**A24. Hidden-but-alive screens.**
A screen may stay composed while hidden only if all of these hold, and each is tested:
- it exposes explicit `show(data)` and `hide()`; a hidden screen receives no updates and no focus;
- it registers no back handler, key handler or lifecycle callback that is active while hidden (the active flag is passed in);
- it runs no continuous animation or timer while hidden;
- it is brought to the front when shown;
- it has a memory budget recorded in its catalog entry.

**A25. Performance budgets and how they are measured.**
Proposed starting budgets (to be confirmed on the owner's phone, not asserted): a warm tab switch reaches a drawn frame in at most 100 ms at the 95th percentile; a cold first open in at most 300 ms. Measurement is taken on the Android side from the touch event to the second drawn frame, on a physical device, in the shipped build. The in-app readout (Settings, Appearance, "Show tab timing") is the instrument. Emulator numbers are trend evidence only, never an acceptance result (A17 already says so; this names the instrument).

**A26. Code-quality gates for everything, with a ratchet for the legacy.**
- Kotlin: ktfmt covers `native/src` today. Move legacy files into the canonical tree one at a time; a legacy file may not be edited without being moved, split and formatted in the same change, or listed in `legacy-exceptions.txt` with a reason.
- JavaScript: add Prettier and ESLint config for the authored segments. New or edited segments must pass. No new line over 160 characters. `legacy-js-exceptions.txt` lists existing offenders; the list may only shrink.
- Size ratchet: CI fails if the number of lines over 160 characters, or the length of any listed legacy file, grows.
- A file over 600 lines is split by feature before further growth (`NativeNutritionPlugin.kt` is the first case: screen composables, snapshot decoding and the Capacitor plugin are three files).
- No "Utils", "Helpers" or "Manager" files (already A09); add: the name of a type says what it owns.

**A27. Documentation is generated or verified, not remembered.**
- Every native plugin has a catalog entry; a CI test fails if a registered plugin has none. (Today: 1 entry for roughly 8 native surfaces.)
- The ledger in `MIGRATION.md` is refreshed in the same change that alters a screen's presentation or authority.
- Each boundary-changing decision gets an ADR in the same change (ADR 0002 records the hand-over).

**A28. A simulation tier between unit tests and the emulator.**
Tab hand-over, stale-reply ordering and rapid switching are testable without Android by running the real web layer with fake native plugins that have random delays (`tests/native-handover.test.js`). Required for any change to navigation or hand-over. The test must be shown to fail on the logic it replaces. Emulator tests stay as non-blocking evidence (the CI emulator step now runs after publish and cannot hold back an update).

**A29. Observability.**
A bounded in-memory trace (ring buffer) of tab events with timestamps (tap, open call received, shown, closed, timeouts, rejected stale scenes), readable from Settings in developer mode and attached to bug reports. Bugs about transitions are diagnosed from the trace, not from screenshots.

**A30. Retire the navigate-wrapper chain.**
Propose a single navigation bus in the web layer: `LifeMaxNav.before(view)`, `LifeMaxNav.after(view)`, with registered listeners instead of 18 functions each reassigning `navigate`. Native tabs register listeners rather than wrapping. Ordering becomes explicit, and the hand-over (A23) lives in one place.

## 4. Target shape of the tab path (proposal)

```
 web (authority)                                  Android
 ───────────────                                  ───────
 LifeMaxNav (A30) ──ShellState + TabIntent──►  TabCoordinator (state machine, A04/A23)
   ▲                                              │  owns: active tab, sequence, owner token
   │ semantic actions only                        ▼
   └──────────────◄──────── events ──────────  TabHost
                                                 ├─ Shell (bars, drawn from ShellState)   one instance, never rebuilt
                                                 ├─ OverviewBody (the live WebView)
                                                 ├─ CharacterBody (Compose, hidden-but-alive, A24)
                                                 └─ FoodBody      (Compose, hidden-but-alive, A24)
```

Tap on the shell: the coordinator highlights the tab immediately from native state, shows a warm body in the same frame, and tells the web layer where to go. The web layer's reply only corrects data. No tab change waits on a bridge round trip to look right.

## 5. Order of work (each step is small and independently shippable)

| # | Step | Status |
|---|---|---|
| 1 | Non-debuggable shipped build; on-device tab timing; shared bars; keep screens alive | done (Native API 15) |
| 2 | Settled capture and stale-scene rejection for the bars | done (Native API 16) |
| 3 | Make-before-break hand-over and one-visible-bars rule, with the simulation test | done (Native API 17) |
| 4 | Catalog entries and a ledger refresh for the plugins that exist; CI check that every plugin has one | proposal |
| 5 | Prettier, ESLint and the legacy ratchets (A26) | proposal |
| 6 | `ShellState` published by the web layer; native draws bars from it; DOM capture retired (A22) | proposal |
| 7 | `LifeMaxNav` replaces the wrapper chain (A30) | proposal |
| 8 | Native `TabCoordinator` state machine; tap highlights natively before the web replies | proposal |
| 9 | Split `NativeNutritionPlugin.kt` into the canonical tree and format it | proposal |
| 10 | Review Cash (activity-based) against A04 and decide: migrate into the host, or keep as a recorded exception | proposal |

Steps 6 to 8 are the ones that make tab switching structurally correct instead of tested-correct. They are also the largest, so they should come after 4 and 5 (which make everything else safer to change).

## 6. What I am not sure about

- The budgets in A25 are starting guesses. They need numbers from the owner's phone before they become rules.
- Moving the shell to native state (A22) changes who owns the top bar's contents (avatar, mic and "Log activity" buttons). Which of those are tabs-level and which are per-page is a product decision, not an engineering one.
- A30 touches 18 files. Whether to do it before or after the native coordinator (step 8) is a trade-off: before is safer for web behavior, after avoids rewriting the hooks twice.
- Hidden-but-alive screens cost memory. I have not measured it. A24's budget requirement exists because of that.
- Everything about phone-side smoothness is unmeasured by me. I can verify sequencing and structure here; only the device can confirm feel.

## 7. Evidence index

| Claim | How it was checked |
|---|---|
| Old hand-over fails, new passes | `tests/native-handover.test.js` run against the previous and current JavaScript |
| 18 segments wrap `navigate` | `grep -l "navigate=function\|navigate = function"` over `src/runtime/segments` |
| Overview line lengths, nutrition plugin size | `awk` and `wc` over the native segments and the flat native directory |
| Debuggable build shipped | `assembleDebug` in the workflow and the Gradle patch before commit `87e1b51` |
| Emulator timing inflated | test source: `waitUntil` polls every 250 ms; JS phase timings from the same run |
| A hidden Food screen would capture Back | `BackHandler { dispatch("close") }` was unconditional in `NativeNutritionPlugin.kt`; the `active` flag was added in commit `87e1b51` |
