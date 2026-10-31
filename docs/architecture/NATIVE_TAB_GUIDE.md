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
2. Web: write `50-native-<tab>.js` with `projection()` and `onAction()`, call `NativeTabKit.create` (copy `50-native-character.js`), add the segment to `src/index.template.html` after `50-native-tabkit.js` and run `python3 lifemax_modular_v73/tools/gen_module_map.py` (`check_source.py` fails in CI while the module map and symbol index are stale), add the setting toggle and the `lmaLoad()` key. Write a node test with the projection fixtures (copy `tests/native-character.test.js`). If the tab edits data, the web handler stays the only writer until A14/A15's receipts exist; delegate and record the hybrid dependency in `MIGRATION.md`.
3. Native: add the feature folders, a `Native<Tab>Plugin` that extends `ComposeTabPlugin`, a container, and register the plugin in `MainActivity.java`. Add a catalog entry in `BRIDGE_CATALOG.json`.
4. Fixtures: add the tab to `tools/gen_native_fixtures.js` so its projection comes from the web app, not from hand-written JSON.
5. Screenshots: add a `<Tab>ScreenshotTest` (copy `CharacterScreenshotTest`), run `tools/build_android_local.sh record`, look at the images, and commit them. Build the screen from `core/designsystem` (cards, pills, bars, icons, `Modifier.tapTarget` for anything tappable; for a screen that follows the page's styles, `PageTheme` with your colours extending `PageColors`, the page's classes as `PageCard`, `PageTag`, `PageButton`, `PageEyebrow` and `PageHead`, the small shadow as `pageShadow`, and the colours the page's CSS fixes from `PageInk`), and give every screenshot rule `maxPercentDifference = 0.001`; a component you add there also gets its own section in `GalleryScreenshotTest`.
6. Prove it: `tools/build_android_local.sh` (compile, unit tests, screenshots), `python3 tools/check_native_format.py <ktfmt jar>`, `python3 lifemax_modular_v73/tools/lint_js.py`, `python3 lifemax_modular_v73/check_source.py`, and the node tests. Run `tests/native-handover-sim.test.js` (Playwright) with the new tab added to its fakes.
7. Bump `NativeApi.VALUE`, update `MIGRATION.md`, push, and read the CI result including the emulator step.

## Rules the shared pieces rely on

- Calls to a plugin arrive in the order made; a close naming an old session must be ignored by a plugin that has sessions.
- A hidden screen is only hidden: it receives no updates, takes no Back button and runs nothing continuous (A24).
- A screen never decodes raw page JSON in its composables once it is converted; the data layer decodes and rejects. Every repository takes the palette with `pageTokens()` (`core/data/PageJson.kt`), which requires every token NativeTabKit sends, so a missing one rejects the projection instead of drawing in the fallback grey.
- Nothing in a tab calls `navigate()` or closes another tab directly; hand-over belongs to `NativeTabs`.
- A choice of one among several (a switch, section tabs, day chips, presets) is items drawn with `Modifier.selectable(selected, role = ...)` inside a parent with `Modifier.selectableGroup()`, as `PaneSwitch`, the Library's switch, Character's Now / vs last week and Life Director's tabs and days are, so TalkBack says which one is on and where it sits in the group.
- To go to another tab, a screen emits the action `navigate` with the view (`ComposeTabPlugin`'s `emit`). The kit goes there without closing first, so a native destination opens before this screen closes and a web one closes it at once (A23). Any other action closes the screen before the page runs it. A page handler that itself leaves the view (Recipes' "Log this recipe" opens Meals with a sheet) is better a command: it runs while the screen still covers the page, and the hand-over closes the screen, so the page never flashes. Such a command's reply never arrives: by then the screen is on its way out, and a screen on its way out gets no update (the kit sends none once the page has left the tab's view, so the leaving screen never takes the shared bars from the one replacing it).
- A command's reply reaches only a showing screen. A screen that closes while the page answers (the owner leaves the tab during a save) never gets it, so a view model that waits for an answer stops waiting when the screen opens again (`LogViewModel.opened`), keeping its operation id so a retry records the change at most once.
- The kit closes every tab's screen at once when the page puts something over its view that `blocked()` counts (a sheet or other top overlay, the launch update prompt, a noscroll body, plus what the tab's own `cfg.blocked` adds: Library and Workout plan add the level-up, `.luov`): it watches the body itself. A tab whose own actions can end in a level-up while its screen shows should add `.luov` to its `cfg.blocked`. A tab whose screen should come back by itself once the page is clear again passes `watch(observe)` (Library, Workout plan), naming any nodes of its own to watch; the others come back on the next visit. No tab keeps a MutationObserver of its own for this.
