# LifeMax native migration architecture contract

Revision 2 — 2026-10-30 (Revision 1, the final planning baseline of 2026-10-29, is kept; changes are listed below)

Revision 2 changes: A04, A16, A17 and A21 gain enforceable detail; A22 to A30 are new (shell, hand-over, hidden screens, performance budgets, quality gates, documentation checks, simulation tier, observability, navigation bus). Each new article says whether it is **Implemented** (and where), **Required for new work**, or **Planned**. Rationale and evidence for every change are in `CONTRACT_REVISION_2_PROPOSAL.md`; the hand-over decision is ADR 0002.

This contract governs new and migrated code. It is based on the reviewed build 172 snapshot, commit `bcb3daee08adf291c73bddd6ed49ff293d13b048`. It does not assert that the proposed architecture or protocol already exists. Before implementation, compare the current branch against that baseline. Existing exceptions are recorded below, not silently declared compliant.

## 1. Destination and migration strategy

The destination is a fully native app: native presentation, native authoritative product logic and persistence, and no required WebView or JavaScript engine. During migration, integrate each usable slice into the existing app immediately. Preserve user saves, established visuals, behavior and existing platform integrations.

Track presentation separately from domain authority:

- Presentation: `WEB_SCREEN`, `HYBRID_SHELL`, `NATIVE_SCREEN`.
- Authority: `WEB_OWNED` or `NATIVE_OWNED`.
- Native verification: `NOT_STARTED`, `SHADOW_VERIFYING`, `VERIFIED`.
- Web dependency retirement: `REQUIRED` or `RETIRED`.

Shadow verification is a non-authoritative activity, not a third writer. A Compose screen can be native presentation while its domain remains web-owned. A delegated web edit sheet must be recorded as a remaining presentation dependency; do not call that entire interaction native.

## 2. Identity, builds and source organization

**A01.** Preserve applicationId `com.lifemax.rpg`, package-linked integrations and existing data locations. New first-party packages use `com.lifemax.rpg.*`. Changing install identity requires a separate migration decision.

**A02.** Start with the existing application module. Introduce a Gradle module only when it enforces a useful boundary, isolates an extracted engine, or provides demonstrated build/test value. Packages organize code; checks and module boundaries enforce dependencies.

**A03.** New canonical authored Kotlin/Java lives under `android-wrapper/native/src/main/java/com/lifemax/rpg/`. Register this directly in the generated app's Gradle source sets through `patch_project.py`. Register `native/src/test/java` likewise. Keep legacy flat source copying temporarily; never compile a class through both paths. Verify paths relative to the generated app directory, inclusion exactly once, rename/removal behavior and JVM test discovery. Generated Android projects and web dist output are not authored source.

**A04.** New primary native tabs use one MainActivity destination host and navigation coordinator. Separate activities are exceptions requiring a documented platform/lifecycle reason, not the default per tab. Existing CashActivity is a temporary legacy exception and does not block Character. Do not change it merely to make the directory tree look uniform.

The destination host is a state machine with named states (`Idle`, `Opening(target)`, `Showing(target)`, `Closing(previous)`), not a set of independent open and close calls. The one-host rule covers the tab shell and the tab bodies together.

Proposed layout:

```
android-wrapper/
  native/
    [legacy flat Java/Kotlin retained temporarily]
    src/main/java/com/lifemax/rpg/
      app/                         composition root, destination host, navigation
      core/designsystem/           theme, tokens, components
      core/model/                  genuinely shared value types only
      bridge/web/                  transport, lifecycle, serialization, adapters
      feature/character/
        presentation/              route, screen, state holder, UiState/actions
        domain/                    capability interfaces and feature models/rules
        data/                      web-backed implementation and mapping
    src/test/java/com/lifemax/rpg/
  android-test/                    existing instrumentation sources initially
  res/                            canonical existing resources
  patch_project.py
  [other existing patchers]
docs/architecture/
  CONTRACT.md
  MIGRATION.md
  BRIDGE_CATALOG.json              one entry per native destination (plugin, web module, writes, delegated actions)
  decisions/
```

## 3. Dependencies and responsibilities

**A05.** Presentation consumes state and emits user intent. It does not call transport, write storage, calculate rewards or implement gameplay rules. Android resources, accessibility, insets and platform UI interactions are legitimate presentation needs. Layout math and formatting are not forbidden business logic.

**A06.** Destination state holders expose lifecycle-aware state and depend on feature capabilities/repositories, not Activity/WebView references. Prefer one coherent UiState per destination; do not require a ViewModel for every widget. Hoist recoverable navigation/input state deliberately. Use local saveable state for suitable local interactions. A ViewModel does not survive process death; durable state is reloaded, and small recoverable UI state is restored separately.

**A07.** Ported domain logic is independently testable Kotlin without Android, Compose, Room or bridge DTO dependencies. Add a use case or separate model representation when it earns its complexity, not for every getter. Keep serialization and persistence details in adapters.

**A08.** Use explicit constructor injection and an application composition root. No new global service locator. A DI framework is optional through a reasoned ADR, not a feature-count threshold. Application-scoped repositories may be long-lived; platform references must have explicit attachment/detachment and must not leak through them.

**A09.** Feature components remain local until stable reuse justifies sharing. Shared visual primitives belong under `core/designsystem/components`; avoid competing generic component directories. No unscoped Utils/Helpers dumping ground. Review cohesion, dependency direction and discoverability instead of imposing arbitrary function-length limits.

## 4. Authority and domain boundaries

**A10.** Each transactionally coherent scope has one authoritative rule/commit path. Multiple entry points may call that owner, but independently writable native and web gameplay truths are prohibited. Disposable read caches, import candidates and delivery metadata are not additional gameplay authorities.

The core save `lifemax_rpg_prototype_v1` stays web-owned during Character presentation migration. Activities, XP reconstruction, decay/ascension, quest completion, economy/badges and pet behavior have couplings that must be inventoried. Do not split ownership by tab or by the number of screens already migrated.

Other stores are candidates for separate ownership only after auditing writers, cross-store effects, backups and consumers. Life Director, preferences and static catalogs are not automatically independent merely because they occupy different files. Catalog compatibility is versioned according to actual behavioral dependencies; matching every unrelated asset hash is not a universal requirement.

A native-owned cutover requires: all writers accounted for; relevant web writers retired or routed to the new owner; transaction dependencies resolved; import/verification/recovery complete; and an explicit atomic ownership activation. Shadow outputs cannot award rewards or persist a second authoritative save.

**A11.** Native views request inventoried semantic operations. No general add-XP, overwrite-stats or guessed reward endpoints. The owner derives XP and applies the existing rules. Optimistic presentation of uncommitted XP/balances is not the default; show pending status and confirmed results.

## 5. Read protocol and freshness

**A12.** Keep a versioned static schema/capability catalog in the repository and validate implementation against it. Runtime negotiation reports supported protocol versions/capabilities, runtime bundle identity and host/session readiness. It does not download handlers or dynamically manufacture Kotlin types.

Use typed payloads at endpoints and structured JSON at transport boundaries, not JSON-inside-JSON strings. Validate required fields and compatible versions; tolerate documented additive fields. Bound payload sizes and pending requests. Use a dedicated gameplay transport adapter, not the OTA LiveWebPlugin as a business-logic container. Prefer the existing Capacitor boundary; do not expose a broad new raw JavaScript interface unnecessarily.

**A13.** The first read-only Character protocol identifies a `webSessionId` and a monotonic `projectionSequence` within that session. Every emitted full snapshot gets a sequence, including a requested refresh after legacy writes or time-dependent changes. A new web session invalidates prior subscriptions/caches and requires a fresh handshake and full snapshot. Pending replies are associated with their request/session generation; late replies cannot replace newer state.

Do NOT pretend that a durable gameplay revision already advances for every legacy writer. Source audit must establish how changes, imports/resets and foreground transitions trigger invalidation/refetch. Use projection sequencing for transport ordering while that legacy world remains in place. An unchanged durable save revision does not imply an unchanged time-derived projection.

A durable `saveEpoch`/`authorityRevision` becomes mandatory before exposing mutation capabilities. Once introduced, every relevant commit/import/reset path must honor it. A read-only UI must not silently insert bridge metadata into the user's save just to complete a handshake. The wire catalog distinguishes volatile projection sequence from durable authority revision. Concrete Character payload fields are finalized from source inventory, not invented in this contract.

## 6. Mutation safety gate — required before any new native write capability

**A14.** Timeout, coroutine cancellation or a lost response means outcome UNKNOWN unless non-dispatch/non-commit is positively established. Do not invent a new operation ID, assume failure, or automatically repeat a reward under a new ID.

A mutation design must provide all of the following before it can ship:

1. Stable operation identity, reused with identical semantic payload on retry, and an authority/save epoch validated again at commit.
2. Owner-validated input and payload equivalence. A client-supplied hash is not trusted proof. If hashes are used, the catalog defines canonical encoding and the owner recomputes them.
3. An authoritative serialized command/restore boundary. Do not hold an asynchronous gap between checking state and committing a result without a defined conflict/revalidation rule.
4. A commit that stores affected state, authority revision and result receipt as one logical storage operation, with explicit storage-failure behavior.
5. A proven bounded retry/receipt policy. A UUID plus an evicting 64-entry/16-KB map CANNOT distinguish an old evicted request from a new request. Use a documented mechanism such as persisted client sequencing/high-water rejection with explicit client lifecycle, or another validated admission/retention scheme. Old requests whose safety cannot be established are rejected/reconciled, never silently re-executed. Capacity is measured, not dictated by an arbitrary ring size.
6. A durable native pending-operation record before dispatch when restart recovery is promised. This is delivery metadata, not a second gameplay save. Keep it minimal, scoped to the correct save and cleared only after terminal reconciliation. Do not silently replay stale destructive intents on app restart.
7. Tested receipt lookup/reconciliation after lost acknowledgment and process death; explicit conflict, unsupported, invalid-epoch, storage-error and unresolved outcomes.

The mutating capability remains absent from the catalog/handshake until an ADR and tests establish these guarantees. The UI-only slice does not need a prematurely designed general transaction engine.

**A15.** A generic wrapper around existing global-mutating handlers is NOT automatically transactional. JSON.parse(lastCommittedSerialized) allocates a new graph; it is not zero-copy and cannot undo other stores, native notifications, toasts, captured object references or already-dispatched effects. A stale retained string can also erase a more recent legacy save.

Prefer operation-specific staged transitions: compute/validate a candidate from the current authoritative state without visible effects, serialize the candidate and receipt, commit, publish the committed in-memory state, then render/emit effects. If a rollback-based adapter is unavoidable, its affected state, aliases, other stores and side effects must be audited and tested. It must not declare compliance merely by assigning a parsed object to global state.

Distinguish failure BEFORE commit from an exception AFTER commit. Once storage succeeds, do not roll the UI back to an older save and report definite failure because a later render/toast failed. Reload/reconcile the committed state. localStorage's single-key logical write boundary is not a multi-store transaction or a promise of filesystem-level crash durability; document the actual guarantees.

## 7. Lifecycle, navigation and performance

**A16.** One coordinator owns primary-destination selection and Back behavior, including existing navigate/lmaNavTo entry points. "One coordinator" is testable, not aspirational: the rules in A23 and A30 define it, and a navigation change without the A28 simulation test does not meet it. A native route must not create a competing history or leave hybrid controls above the wrong content. Remove temporary views/listeners on exit and restore the original screen on safe fallback. New tab navigation must avoid unnecessary activity/window transitions.

Web-owned reads/commands cannot depend on requestAnimationFrame, a visible DOM tab or an animation loop. Keep the required host reachable without keeping hidden pet/gym graphics rendering continuously. Do not globally pause WebView timers needed by the authority. Any render-pause hook is proposed work, not an existing verified API. Test readiness, native-covering-web, background/resume, host recreation and process recovery; JavaScript evaluation is asynchronous and is not guaranteed to finish in a fixed number of milliseconds.

**A17.** Preserve the existing visual language using verified light/dark/user-accent tokens, fonts, spacing, shapes, chart geometry and motion. Do not substitute an illustrative neon palette. Support accessibility, content descriptions, font scaling, reduced motion and insets. Do not use whole-page software snapshots as a substitute for native live UI. Small custom native charts/Canvas components remain legitimate where measured and accessible.

The shipped artifact is the measured artifact: CI publishes a non-debuggable build (**Implemented**, `patch_gradle.py`), and acceptance numbers come from that build. Budgets and the measuring instrument are in A25.

UI tickers redraw from authoritative values/deadlines; they do not award progress once per tick. Native simulations need explicit elapsed-time and background-execution semantics. Do not assume an always-running service is necessary.

Performance acceptance uses repeatable device scenarios and release/profileable builds, with refresh rate, fixture, build and thermal conditions recorded. Compare frame-time/jank distributions, memory behavior and leaks against the working baseline. Physical results are reported separately from emulator evidence. No universal zero-jank, 60-FPS or 5-MB-delta promise is made.

## 8. Time, units, compatibility and recovery

**A18.** Distinguish instants, local dates, recurring local times/zones and monotonic elapsed durations. New wire fields define representation and precision explicitly; adapters preserve existing epoch-second/millisecond and local-date semantics. Native types should express those meanings with java.time. Do not silently convert legacy pounds/feet/inches or migrate precision during a UI port.

**A19.** OTA/APK compatibility is part of the runtime contract. In the reviewed LiveWeb.prepare implementation, cached active/staged builds no newer than the installed APK are already discarded; newer valid stages may be promoted and unhealthy bundles fall back. Preserve and test that existing behavior rather than reimplementing a supposed missing guard. Native API minimum checks and gameplay capability negotiation solve related but different compatibility problems. Protocol mismatch should safely return to a supported web presentation when one exists, with a useful failure state rather than hanging.

Over-the-air downloads come only from GitHub (**Implemented**, `DownloadRules.java`, backlog SEC-1 and SEC-5). The first request must be https on github.com; every redirect is checked before it is contacted and must stay https on github.com or `*.githubusercontent.com` (CI publishes to the `latest` release, which GitHub serves from release-assets.githubusercontent.com); each download has a size cap and only one APK install and one web download run at a time; the web bundle keeps its SHA-256 check. A change to where CI publishes must keep these rules satisfied (`tests/updater-network.test.py` reads the workflow).

**A20.** Before native ownership: version source/destination formats; inventory all writers/stores; validate an import candidate; retain a recoverable original; verify domain invariants; atomically activate ownership; and test interruption/recovery. No screen-count or bridge-latency threshold determines database choice. Choose Room/DataStore/files from actual access and transaction requirements.

Imports/restores/resets are serialized with commands. Once mutation epochs exist, a replacement save gets a new epoch and does not reactivate old receipts. Old pending commands/results cannot mutate or replace the restored save. Unknown epochs require authoritative renegotiation, not blindly accepting whichever event arrives last. Define minimum-reader/export compatibility before introducing incompatible native data; preserve unsupported originals instead of overwriting them with defaults.

A Classic UI toggle is safe only while that presentation uses the correct owner. It is not a database downgrade. After cutover, legacy writers must remain blocked even if a deep link or old preference opens a web screen. Backup export must read the active owner and have a defined recovery/import policy.

Input from outside the app (**Implemented**, backlog SEC-3, SEC-6 and NAV-4; **Required for new work**). A `lifemax://` link or an intent extra can come from any app, so it never changes the save by itself: it may open a screen, and anything that writes asks first or stops at a preview (`50-deeplink.js`, `LifeMaxVoice.fromLink`); text from a link is capped and cleaned. `MainActivity` accepts the `lma_open` extra only for screens listed in `LaunchRules.java`, and drops a launch intent that Android hands back after process death, a re-creation or a reopen from Recents. The FileProvider shares only the folders listed in `res/xml/file_paths.xml`, each next to the code that shares from it. Native code does not log personal data (it logs nothing as of Native API 23); keep it that way.

## 9. Verification, exceptions and completion

**A21.** CI checks formatting/static rules, build/source inclusion, test discovery, concrete protocol schemas, relevant domain behavior, preferences and lifecycle integration. New mutation tests cover duplicates, evicted/stale retries, lost ACKs, payload conflicts, failed storage, post-commit errors, restore races and restart reconciliation. Existing legacy code is not declared fixed by these new tests; scope evidence honestly. Navigation, hand-over and stale-reply ordering are also covered by the simulation tier in A28.

Record boundary-changing decisions in short ADRs. Each migrated feature records ownership, actual writers, compatibility/fallback limits, remaining web dependencies and acceptance evidence. Legacy Cash activity/command handling and the retired Overview activity are explicit existing exceptions; audit them before reuse and track retirement without blocking an unrelated read-only UI slice.

The final app has no required DOM-rendered destinations, web-only interaction sheets, JS-owned business rules, web persistence authority, gameplay bridge or web OTA bootstrap dependency. Native integrations, backups/import/export, migration recovery, accessibility and physical-device acceptance are complete. Java is still native; language conversion, a particular database or making every plugin a singleton is not an exit criterion.

## 10. Shell, hand-over, hidden screens, performance and quality (Revision 2)

**A22. The shell is native state, not a captured drawing.** *Planned; the DOM capture is a recorded exception until then.*
The web layer publishes a small semantic `ShellState` (proposal for its shape: `sequence`, `tabs[{id, label, iconRef}]`, `activeTab`, `theme` tokens, `topbar` title and actions). Native draws the bars from it. The active tab is data, so a tab change needs no DOM capture and cannot show a stale highlight. Until all native tabs read `ShellState`, `NativeOverviewSnapshot.capture()` is an existing exception with this retirement condition. While it stands: capture only after `lmaSyncActive()`, with transitions disabled, key any cache on the lit tab, and re-publish a settled scene after a screen opens (**Implemented**, `50-native-overview.js`).

**A23. Make-before-break hand-over between native destinations.** *Implemented* (`NativeTabs` in `50-native-overview.js`, `ChromeHost` in `app/shell/ChromeHost.kt`; ADR 0002).
1. The destination opens first. The previous destination closes only after the new one has opened or failed. A last-resort timeout (2.5 s) is a recorded fault, not normal flow.
2. A web destination closes the previous native one at once.
3. Every scene or state message carries the page's monotonic sequence; a receiver ignores a message older than the one it holds.
4. Shared UI (the bars) has an owner token; only the current owner may release it; a stale owner's release is a no-op.
5. At most one set of bars is visible.
6. Every close path (navigation, observers, refresh handlers, errors) goes through the same hand-over. A direct close that bypasses it is a defect.
7. A cancelled open is repeated. A close that arrives while an open is in flight runs right after it (calls reach a plugin in order), so an open whose tab is wanted again is issued again behind that close; a destination that was merely cancelled is never left blank.
8. Safety net: tabs register how to ask whether they are open or opening and how to open and close (`NativeTabs.register`). 600 ms after the last navigation, once nothing is in flight, `NativeTabs.heal()` opens the current view's tab if it is missing and closes any other native tab still showing. It repairs; it does not replace items 1 to 7.

**A24. Hidden-but-alive screens.** *Required for new work; Character and Food comply except where noted in the ledger.*
A screen may stay composed while hidden only if: it has explicit `show(data)` and `hide()`; a hidden screen receives no updates and no focus; it registers no back, key or lifecycle handler that is active while hidden (the visible flag is passed in); it runs no continuous animation or timer while hidden; it is brought to the front when shown; and its memory cost is recorded in its catalog entry (not yet measured for either screen).

**A25. Performance budgets and how they are measured.** *Budgets proposed, to be confirmed.*
Starting budgets, to be confirmed on the owner's phone and not asserted until then: a warm tab switch reaches a drawn frame in at most 100 ms at the 95th percentile; a cold first open in at most 300 ms. Measurement is taken on the Android side from the touch event to the second drawn frame, on a physical device, in the shipped build (**Implemented**: `TabTiming`, shown by Settings, Appearance, "Show tab timing"). Emulator figures are trend evidence only and are never an acceptance result; the emulator step runs after publishing and cannot block a release.

**A26. Code-quality gates for everything, with a ratchet for the legacy.** *Required for new work; legacy ratchets Planned.*
- Kotlin: ktfmt 0.53 (`--kotlinlang-style`) covers every native Kotlin file, checked in CI. A legacy flat file may not be edited without being moved into the canonical tree, split by feature and formatted in the same change, unless it is listed in `legacy-exceptions.txt` with a reason.
- JavaScript: ESLint bug rules (`tools/eslint.config.mjs`) run over the built page in CI with a ratchet (`tools/lint-baseline.json`): a rule's count may fall, never rise. Prettier is deliberately not applied to the 80-segment runtime, because reformatting it would be an enormous diff over code that depends on load order; new segments follow the formatted style of `50-native-tabkit.js`.
- Ratchet: CI fails if the count of over-long lines or the length of a listed legacy file grows.
- A file over 600 lines is split by feature before it grows further. `NativeNutritionPlugin.kt` (1,903 lines at Revision 2) was the first case: it is now the plugin (`bridge/web`), the palette, the leaf components and the screen (`feature/food/presentation`). The 1,555-line screen is now `FoodContext` plus a file per pane (the screen is 84 lines, the largest pane 571), proven identical by the screenshot tests; the panes still read raw JSON and become a model, repository, view model and cards when Food is converted to the canonical form.
- The name of a type says what it owns; no Utils, Helpers or Manager files (A09).

**A27. Documentation is verified, not remembered.** *Planned.*
Every registered native plugin has a bridge-catalog entry, and a CI test fails when one lacks it (all five destinations have one as of Native API 20; the CI test that enforces it is still to be written). The ledger in `MIGRATION.md` is updated in the same change that alters a screen's presentation or authority. Each boundary-changing decision gets an ADR in the same change.

**A28. Simulation tier.** *Implemented for tab hand-over* (`lifemax_modular_v73/tests/native-handover-sim.test.js`, and the older `native-handover.test.js`).
Hand-over, stale-reply ordering and rapid switching are tested without Android by running the real web layer against fake native plugins. The simulation runs on virtual time (Playwright's clock) with seeded random delays and seeded tab-hopping sequences, so a failure is exactly repeatable (`SEED_BASE=n TRACE=1`). The fakes model the device honestly: a plugin's calls run in the order made, and Overview and Food ignore a close naming a session they no longer hold. It found real blank-screen races (about 1 in 70 rapid sequences) that are now fixed (items 7 and 8 of A23). Required for any change to navigation or hand-over. The test must be shown to fail on the logic it replaces. Playwright is not part of the CI node tests today; wiring it in is part of A27's follow-up.

**A31. One shared scaffold per side for every native tab.** *Implemented; Character uses it, Food and Cash do not yet.*
Web: `NativeTabKit.create({...})` (`50-native-tabkit.js`) owns session and sequence numbers, open, update, close, hand-over, refresh, the settings toggle and the public entry points. Native: `ComposeTabPlugin` (`bridge/web`) owns handshake, open, update, close, the kept-alive screen, the shared bars and insets. A new tab supplies its projection and actions on the web side, and `accept` and `Content` on the native side. See `NATIVE_TAB_GUIDE.md`.

**A32. Local build and screenshot proof.** *Implemented.*
`tools/build_android_local.sh` compiles and tests the Android app where the work is done (`tools/LOCAL_BUILD.md`). Paparazzi renders each native screen to an image on the JVM, from fixtures that the web app itself produced (`tools/gen_native_fixtures.js`), and CI compares them with the references in `native/src/test/snapshots`. A native refactor must leave the references unchanged; a deliberate visual change re-records them in the same commit. Emulator checks remain CI-only and non-blocking, and physical-device acceptance is still the owner's.

**A29. Observability.** *Partly implemented (`TabTiming`); the trace is Planned.*
A bounded in-memory trace of tab events with timestamps (tap, open call received, shown, closed, timeouts, rejected stale scenes), readable from Settings in developer mode and attachable to bug reports. Transition bugs are diagnosed from the trace, not from screenshots alone.

**A30. One navigation bus.** *Planned.*
The web layer gets a single `LifeMaxNav` with `before(view)` and `after(view)` listeners in place of every segment reassigning `navigate`. At Revision 2, 18 runtime segments wrap `navigate()` and ordering is load order. Native tab modules register listeners; the hand-over of A23 then lives in one place.

