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
| Screenshot tests with reference images, in CI | done (Character dark/light; Food Log, Plan, Shopping, Plan light; Workout plan) |
| `NativeTabKit` (web) and `ComposeTabPlugin` (native) | done; Character uses them, Food and Cash still have their own |
| Food screen split into a context and panes | done, screenshots identical |
| Tab hand-over simulation on virtual time, races fixed, safety net | done (A23 items 7 and 8, A28) |
| Log activity logic callable without the form, 163 golden cases | done |
| Food and Cash onto the shared scaffold | to do as each is converted |
| Automatic backups before any data or logic port | to do (owner decision) |

## Log activity (Native API 23)

Native presentation, web authority. `NativeLogPlugin` (ComposeTabPlugin) with `feature/log` (domain, data, presentation) and shared `core/designsystem/components`. The page projects presets, attributes, effort levels, the draft and its own XP preview (`50-native-log.js`); the screen sends `preview` and `submit` commands through the new command channel of NativeTabKit/ComposeTabPlugin, which never closes the screen. Opt-in beta in Settings. A prefill in the web form (Life Director, links) is taken over when the screen opens. Remaining before it can become the default: physical-device check by the owner, and the backlog items tagged Log.

## Workout plan (next Native API)

Native presentation, web authority. `NativePlanPlugin` (ComposeTabPlugin) with `feature/plan` (domain, data, presentation) draws the page's projection: the guided-workout launcher, the active plan, the calendar of weeks and days, the selected day's exercises and the General rules, and the page's toggle message and XP celebration. A ticked box shows the state asked for, faded, until the page answers; the answer comes with the page's fresh projection, so a refused or failed save shows the box as the page holds it, and a box whose answer is lost takes the projection's state again after 5 seconds. `50-native-plan.js` (NativeTabKit, `plan.projection.v1`, Settings "Native Workout plan (beta)", off by default) projects every week, day and item of the active plan, already worded as the page words it, with the page's selected day (`selected.revision`), the guided-workout launcher, the program key and the General rules. Day and week selection are the screen's own. Ticking a set is the `toggle` command, which runs the page's own `planToggle` with set semantics; the celebration comes back in the reply for the screen to draw, with the orbs flying to the top bar's logo as on the page, and the page plays its sound and buzz. A failed save is reported as not saved and, as after the page's own checkbox, stays in memory with everything the save set off until the next save stores it. Inventory: `PLAN_INVENTORY.md`; fixtures from the web app: `native/src/test/resources/fixtures/plan-*.json`.

| Remaining web dependency | How the screen reaches it |
|---|---|
| Guided workout runner (classic and 3D gym) | closes; `gwrResume` or `gwrStart(day)`; back when the runner closes |
| Value and session-log fields | closes; the page selects the day and focuses the field; back when the field loses focus |
| Leave plan (confirm) | closes; `leavePlan`; back if the plan is kept |
| Sign-up offers (no plan) | not covered; back when a plan is started |
| Level-up overlay, sheets, badge toasts | the screen steps aside for an overlay and comes back; badge toasts are not visible under the screen |

Screenshots: `PlanScreenshotTest` (each fixture dark and two light; the exercises of today with a box waiting for the page, of a done day and of a day to come; the toast in both themes). Not yet done: the owner's check on the phone, including whether the keyboard rises for a field handed to the page.
