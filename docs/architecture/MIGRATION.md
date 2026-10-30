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


## Ledger as of Native API 17 (2026-10-30)

| Area | Presentation | Authority | Rollout | Remaining web dependency |
|---|---|---|---|---|
| Overview | Hybrid shell (native bars) over the live WebView | Web-owned | Native is the default; Classic is a setting | The whole body |
| Character | Compose screen, hidden-but-alive | Web-owned | Opt-in preview | Logging, Luck, benchmark editing |
| Food (Nutrition) | Compose screen (Plan, Log, Shopping), hidden-but-alive | Web-owned | Opt-in beta | Add-food sheets and swap sheet delegate to web |
| Cash | Native activity (Compose) | Web-owned commands | Existing | Commands and state |
| Life Director (Today) | Compose Today screen over the page while Today is clear (web half in place, native screen in progress) | Web-owned (Life Director store and engine) | Opt-in beta, off by default | Every tap but the day strip runs on the page: session, meal and timer sheets, Done, the guide, Something came up, Re-plan, banner buttons; Week, Goals, Quests, My time and Settings; dial handles and the build animation |
| Tab shell (bars) | Native drawing of a DOM capture | Web page state | Shared by the three native tabs | DOM capture (see CONTRACT_REVISION_2_PROPOSAL.md, A22) |

Statements here are from reading the repository and the Settings wiring; device acceptance is not recorded.

## Source layout as of Native API 20

Every native Kotlin file now lives in the canonical tree (`android-wrapper/native/src/main/java/com/lifemax/rpg`) and is formatted with ktfmt; CI checks all of them. Java plugins stay in `android-wrapper/native/` until a feature needs them converted.

| Path | Holds |
|---|---|
| `app/shell` | `ChromeHost`, `OverviewBars`, `TabTiming`, `LiveOverviewShell`: the shared bars and the tab hand-over |
| `app/CharacterContainer.kt` | composition root for Character |
| `bridge/web` | `ComposeTabPlugin` (the shared base of a native tab) and one Capacitor plugin per native destination: Character (a subclass), Food (`NativeNutrition`), Overview, Cash, plus `HealthSync` |
| `core/designsystem` | theme tokens from the page |
| `feature/character` | domain, data, presentation (the reference layout) |
| `feature/food/presentation` | `FoodContext` and one file per pane (Plan, Log, Shopping), the header, week strip, switcher and search bar; rendered by screenshot tests, still reading raw JSON |
| `feature/cash` | `CashActivity` and its session (a documented activity exception, A04) |

Removed in this step: the retired Canvas `OverviewActivity` (nothing opened it since the live Overview shell), its manifest entry and its test assertion.

## Prep completed before the native rewrite (Native API 20)

| Item | State |
|---|---|
| ktfmt over every native Kotlin file, enforced in CI | done |
| Every native Kotlin file in the canonical tree, retired Canvas Overview removed | done |
| Bridge catalog entry for every destination | done (CI test that enforces it still to come) |
| JavaScript ESLint ratchet in CI | done (`tools/lint_js.py`) |
| Local Android build and unit tests (`tools/build_android_local.sh`) | done |
| Screenshot tests with reference images, in CI | done (Character dark/light; Food Log, Plan, Shopping, Plan light) |
| `NativeTabKit` (web) and `ComposeTabPlugin` (native) | done; Character uses them, Food and Cash still have their own |
| Food screen split into a context and panes | done, screenshots identical |
| Tab hand-over simulation on virtual time, races fixed, safety net | done (A23 items 7 and 8, A28) |
| Log activity logic callable without the form, 163 golden cases | done |
| Food and Cash onto the shared scaffold | to do as each is converted |
| Automatic backups before any data or logic port | to do (owner decision) |

## Log activity (Native API 23)

Native presentation, web authority. `NativeLogPlugin` (ComposeTabPlugin) with `feature/log` (domain, data, presentation) and shared `core/designsystem/components`. The page projects presets, attributes, effort levels, the draft and its own XP preview (`50-native-log.js`); the screen sends `preview` and `submit` commands through the new command channel of NativeTabKit/ComposeTabPlugin, which never closes the screen. Opt-in beta in Settings. A prefill in the web form (Life Director, links) is taken over when the screen opens. Remaining before it can become the default: physical-device check by the owner, and the backlog items tagged Log.

## Life Director, Today (phase 1 of 4)

Native presentation of Today, web authority. Life Director stays the only writer: `LifeDirector.native` (`src/features/life-director/logic.js`) returns the frame (header, section tabs, banner, last message) and Today (strip, dial, centre, mix, up next, reminders, running order) as data from the same decisions as its renderers, says what the page is showing, runs a handed-back tap through the same code as the tapped element (`perform`), and tells listeners when what it shows changes. `50-native-director.js` (NativeTabKit) covers the page only while that is Today with nothing over it, keeps the chosen day itself (command `day`, read-only, today again on each open), and delegates every other tap: the screen closes, the page runs the tap, and the screen comes back as soon as Today is clear again (a sheet or the guide closes, Today is chosen again). Fixtures come from the engine planning a lived-in week (`tests/fixtures/director-day.js`, `tools/gen_native_fixtures.js`). Remaining web dependencies: everything in the ledger row above. Known gaps: the XP celebration of a Done or a finished timer plays on the page under the returning screen (the page's message comes back with it), and the selected day resets after a delegated tap. Next: the native screen (the rest of phase 1), dial dragging (phase 2), Week, Goals and Quests (phase 3), then sheets, forms, set-up and the guide (phase 4).
