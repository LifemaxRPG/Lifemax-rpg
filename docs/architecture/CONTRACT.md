# LifeMax native migration architecture contract

Revision 1 — final planning baseline, 2026-10-29

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
  BRIDGE_CATALOG.json              add concrete schemas after source inventory
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

**A16.** One coordinator owns primary-destination selection and Back behavior, including existing navigate/lmaNavTo entry points. A native route must not create a competing history or leave hybrid controls above the wrong content. Remove temporary views/listeners on exit and restore the original screen on safe fallback. New tab navigation must avoid unnecessary activity/window transitions.

Web-owned reads/commands cannot depend on requestAnimationFrame, a visible DOM tab or an animation loop. Keep the required host reachable without keeping hidden pet/gym graphics rendering continuously. Do not globally pause WebView timers needed by the authority. Any render-pause hook is proposed work, not an existing verified API. Test readiness, native-covering-web, background/resume, host recreation and process recovery; JavaScript evaluation is asynchronous and is not guaranteed to finish in a fixed number of milliseconds.

**A17.** Preserve the existing visual language using verified light/dark/user-accent tokens, fonts, spacing, shapes, chart geometry and motion. Do not substitute an illustrative neon palette. Support accessibility, content descriptions, font scaling, reduced motion and insets. Do not use whole-page software snapshots as a substitute for native live UI. Small custom native charts/Canvas components remain legitimate where measured and accessible.

UI tickers redraw from authoritative values/deadlines; they do not award progress once per tick. Native simulations need explicit elapsed-time and background-execution semantics. Do not assume an always-running service is necessary.

Performance acceptance uses repeatable device scenarios and release/profileable builds, with refresh rate, fixture, build and thermal conditions recorded. Compare frame-time/jank distributions, memory behavior and leaks against the working baseline. Physical results are reported separately from emulator evidence. No universal zero-jank, 60-FPS or 5-MB-delta promise is made.

## 8. Time, units, compatibility and recovery

**A18.** Distinguish instants, local dates, recurring local times/zones and monotonic elapsed durations. New wire fields define representation and precision explicitly; adapters preserve existing epoch-second/millisecond and local-date semantics. Native types should express those meanings with java.time. Do not silently convert legacy pounds/feet/inches or migrate precision during a UI port.

**A19.** OTA/APK compatibility is part of the runtime contract. In the reviewed LiveWeb.prepare implementation, cached active/staged builds no newer than the installed APK are already discarded; newer valid stages may be promoted and unhealthy bundles fall back. Preserve and test that existing behavior rather than reimplementing a supposed missing guard. Native API minimum checks and gameplay capability negotiation solve related but different compatibility problems. Protocol mismatch should safely return to a supported web presentation when one exists, with a useful failure state rather than hanging.

**A20.** Before native ownership: version source/destination formats; inventory all writers/stores; validate an import candidate; retain a recoverable original; verify domain invariants; atomically activate ownership; and test interruption/recovery. No screen-count or bridge-latency threshold determines database choice. Choose Room/DataStore/files from actual access and transaction requirements.

Imports/restores/resets are serialized with commands. Once mutation epochs exist, a replacement save gets a new epoch and does not reactivate old receipts. Old pending commands/results cannot mutate or replace the restored save. Unknown epochs require authoritative renegotiation, not blindly accepting whichever event arrives last. Define minimum-reader/export compatibility before introducing incompatible native data; preserve unsupported originals instead of overwriting them with defaults.

A Classic UI toggle is safe only while that presentation uses the correct owner. It is not a database downgrade. After cutover, legacy writers must remain blocked even if a deep link or old preference opens a web screen. Backup export must read the active owner and have a defined recovery/import policy.

## 9. Verification, exceptions and completion

**A21.** CI checks formatting/static rules, build/source inclusion, test discovery, concrete protocol schemas, relevant domain behavior, preferences and lifecycle integration. New mutation tests cover duplicates, evicted/stale retries, lost ACKs, payload conflicts, failed storage, post-commit errors, restore races and restart reconciliation. Existing legacy code is not declared fixed by these new tests; scope evidence honestly.

Record boundary-changing decisions in short ADRs. Each migrated feature records ownership, actual writers, compatibility/fallback limits, remaining web dependencies and acceptance evidence. Legacy Cash activity/command handling and the retired Overview activity are explicit existing exceptions; audit them before reuse and track retirement without blocking an unrelated read-only UI slice.

The final app has no required DOM-rendered destinations, web-only interaction sheets, JS-owned business rules, web persistence authority, gameplay bridge or web OTA bootstrap dependency. Native integrations, backups/import/export, migration recovery, accessibility and physical-device acceptance are complete. Java is still native; language conversion, a particular database or making every plugin a singleton is not an exit criterion.
