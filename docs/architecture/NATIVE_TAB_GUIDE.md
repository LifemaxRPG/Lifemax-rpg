# Adding a native tab

A native tab is a Compose screen laid over the page. The page (web code) stays the authority for data and rules until that tab's logic is converted; the screen draws a projection of it and sends actions back. Everything repetitive is in two shared pieces, so a tab is only what is its own.

## What a tab is made of

| Piece | Where | You write |
|---|---|---|
| Projection (web) | `src/runtime/segments/50-native-<tab>.js` | a `projection()` returning the facts the screen shows, and `onAction(event)` calling the page's own handlers |
| Web lifecycle | `NativeTabKit.create({...})` in `50-native-tabkit.js` | one call; it owns session/sequence, open/update/close, hand-over, refresh, the settings toggle, public entry points |
| Native plugin | `bridge/web/Native<Tab>Plugin.kt` extending `ComposeTabPlugin` | `tabId`, `capabilities`, `accept(snapshot, explicitOpen)`, `Content(top, bottom, emit)` |
| Domain | `feature/<tab>/domain` | the typed projection and the repository interface (no Android imports) |
| Data | `feature/<tab>/data` | the decoder from the page's JSON; rejects malformed data, keeps the last good state, ignores older sequences |
| Presentation | `feature/<tab>/presentation` | the screen, view model and cards; colours come from the theme tokens, never hard-coded |
| Composition root | `app/<Tab>Container.kt` | wires repository and view model for this plugin's lifetime |
| Tests | `native/src/test`, `lifemax_modular_v73/tests` | decoder and view-model unit tests, a screenshot test, a web projection test |

## Steps

1. Read `CONTRACT.md` (A03 to A07 boundary, A14/A15 writes, A23 hand-over, A24 hidden screens, A25 budgets) and the tab's inventory (see `CHARACTER_INVENTORY.md` for the shape). Record every displayed fact and every action the web tab has.
2. Web: write `50-native-<tab>.js` with `projection()` and `onAction()`, call `NativeTabKit.create` (copy `50-native-character.js`), add the segment to `src/index.template.html` after `50-native-tabkit.js`, add the setting toggle and the `lmaLoad()` key. Write a node test with the projection fixtures (copy `tests/native-character.test.js`). If the tab edits data, the web handler stays the only writer until A14/A15's receipts exist; delegate and record the hybrid dependency in `MIGRATION.md`.
3. Native: add the feature folders, a `Native<Tab>Plugin` that extends `ComposeTabPlugin`, a container, and register the plugin in `MainActivity.java`. Add a catalog entry in `BRIDGE_CATALOG.json`.
4. Fixtures: add the tab to `tools/gen_native_fixtures.js` so its projection comes from the web app, not from hand-written JSON.
5. Screenshots: add a `<Tab>ScreenshotTest` (copy `CharacterScreenshotTest`), run `tools/build_android_local.sh record`, look at the images, and commit them.
6. Prove it: `tools/build_android_local.sh` (compile, unit tests, screenshots), `python3 tools/check_native_format.py <ktfmt jar>`, `python3 lifemax_modular_v73/tools/lint_js.py`, and the node tests. Run `tests/native-handover-sim.test.js` (Playwright) with the new tab added to its fakes.
7. Bump `NativeApi.VALUE`, update `MIGRATION.md`, push, and read the CI result including the emulator step.

## Rules the shared pieces rely on

- Calls to a plugin arrive in the order made; a close naming an old session must be ignored by a plugin that has sessions.
- A hidden screen is only hidden: it receives no updates, takes no Back button and runs nothing continuous (A24).
- A screen never decodes raw page JSON in its composables once it is converted; the data layer decodes and rejects.
- Nothing in a tab calls `navigate()` or closes another tab directly; hand-over belongs to `NativeTabs`.
