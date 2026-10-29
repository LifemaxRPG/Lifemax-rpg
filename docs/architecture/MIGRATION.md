# Migration plan and initial ownership ledger

## Current baseline

| Area | Presentation | Authority | Web dependency |
|---|---|---|---|
| Overview build 172 | Hybrid native controls/live web body | Existing web owners | Required |
| Character | Web | Core gameplay, web-owned | Required |
| Cash | Existing native activity/presentation | Existing web command/state path | Required |
| Core gameplay | Mixed consumers | Web-owned | Required |
| Life Director | Web | Feature/core coupling audit required | Required |
| Settings/platform stores | Mixed | Inventory each store; no new cutover assumed | Required where currently used |

These are baseline descriptions, not a claim of complete writer inventory or transaction safety. Native Cash currently delegates commands through 50-native-cash.js, calls existing web operations/save, verifies persisted money and attempts in-memory rollback. Its session-local seen map does NOT establish restart-safe idempotency or atomic whole-gameplay commit. Do not copy that pattern as the new mutation contract.

## Character: first implementation sequence

### C0 — Inventory and deterministic fixtures

Read current character.html, 01/04/07/14/15-runtime.js, later wrappers, navigation, stores and CSS. Produce CHARACTER_INVENTORY.md identifying exact displayed facts/derived values, chart and comparison modes, selection/sorting, skill progression, avatar, weight/BMI, benchmarks and every action/sheet/navigation path. The eight IDs are physique, endurance, intelligence, business, social, discipline, adventure and wellbeing. Benchmark addition is visibly present in the baseline; inventory its complete handler path and any generated attribute-detail actions rather than claiming Character has no mutations.

Use synthetic fixtures for new, established and legacy/ascended states, plus missing/custom/large-history cases. Do not publish personal saves. Measure fixture size and storage headroom; no 5-MB quota assumption is a substitute for handling actual write failure. Capture the current visual and interaction baseline.

### C1 — Build and native foundations

Register direct authored source/test roots idempotently and verify compile/test discovery, inclusion once and rename/removal behavior. Use the existing Compose setup. Add only required theme primitives, manual composition root and one MainActivity destination host coordinated with existing navigation. Preserve existing APK signing, package identity and update flow.

### C2 — Read-only native Character prototype

Finalize the static character projection schema from C0. Implement capability/readiness negotiation, full snapshots and session/projection-sequence freshness through a feature-owned repository and lifecycle-bound transport. No new native gameplay writes or durable bridge metadata solely for this read slice. Snapshot invalidation must observe legacy changes and time-derived values even when durable save revisions do not exist.

Build native charts/cards, selection, comparison and sorting from the authoritative projection and local UI state. Render no Character DOM/captured page in the native presentation. Include loading/error/reconnect states, font scaling, light/dark/accent themes and reduced motion. Native formatting/layout math is allowed; rewards and gameplay calculations remain authoritative.

Integrate behind a persisted native/classic choice with session fallback on unavailable capabilities. An incomplete developer prototype is not a default production replacement. For existing mutating actions, either delegate explicitly to their working web presentation and record that hybrid dependency, or hold default rollout until the native interaction is complete. Never silently omit a button or pretend it saved locally.

### C3 — Lifecycle and acceptance

Verify shared state after real web actions, stale/out-of-order read responses, live/time-sensitive changes, native covering the web authority, navigation/Back, rapid tab changes, background/resume, rotation, process recreation, unreadable/unavailable data and Classic cleanup. Confirm no duplicate hosts, leaks, background render load or transition flash. Record physical-device performance separately from automated/emulator checks.

### C4 — Mutations and full Character interaction parity

Before each native write capability, adopt an operation-specific command/commit/reconciliation design satisfying CONTRACT A14–A15. Audit legacy save handlers and side effects; do not wrap arbitrary global mutations and label them atomic. Test the failure matrix before wiring the control. Replace remaining web sheets/edits and verify both native and existing web consumers observe the same authoritative results without duplicate effects.

Only then label Character native presentation with complete interaction parity. Core authority can still remain web-owned. Retiring that dependency is a separate later cluster cutover.

## Per-feature/aggregate record template

- Presentation implementation, native coverage and remaining web sheets/tools.
- Host, entry points and Back/navigation owner.
- Authority scope/owner, persistence keys, schema version IF VERIFIED, stored facts and derived projections.
- All writers and cross-domain/store side effects; gaps explicitly recorded.
- Read/command catalog versions; session/projection freshness and, when implemented, epoch/revision semantics.
- Pending-operation recovery and receipt-retention/admission policy for each mutating capability.
- Native rule/importer shadow-verification status and equivalence evidence.
- Consumers remaining; safe fallback combinations and import/export/older-reader limits.
- Rollout state, current commit/build, automated/screenshot/device evidence.
- Temporary exceptions, next ownership gate and retirement conditions.

## Native Character prototype implementation

The `codex/native-character` slice implements Compose presentation behind the opt-in Native Character preview setting (Classic remains default). Models and owner calculations enter through `50-native-character.js`, with a volatile session/sequence and no durable gameplay writes. Kotlin decodes into immutable projections; a feature repository interface and ViewModel own native presentation state. The platform plugin attaches ComposeView to MainActivity and owns its lifecycle references. Main/test roots are registered directly in Gradle. ktfmt 0.53 formatting is checksum-pinned and checked for the new source tree.

Implemented: hero, radar current/previous comparison and selection, attribute sorting/cards, selected detail and spark bars, skills/show-all, benchmark readout and original-handler delegation. Logging, Luck and benchmark editing remain explicit web dependencies. Native ownership, safe mutating commands and complete native interaction parity are NOT implemented. Physical-device testing, restored transient UI state and complete visual/accessibility parity remain rollout gates; do not label this fully migrated Character or enable it by default.

Evidence: Node projection test, JVM selection/sequence/schema tests, Android Compose-in-MainActivity/no-WebView-subtree test and unchanged core-save assertion; reference/native/scroll/light screenshots are emitted by CI for review. Synthetic projection fixture is not a claim of complete raw-save migration fixtures.
