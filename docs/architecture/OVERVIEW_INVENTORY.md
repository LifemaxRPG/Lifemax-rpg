# Overview (cards) native slice inventory

Baseline: branch `track/h-docs` on `integ/journal` (2026-11-02, Native API 44). Read against the code; where it differs from plans/OVERVIEW_BODY.md the text says so. Paths: web under `lifemax_modular_v73/src/runtime/segments/`, Kotlin under `android-wrapper/native/src/main/java/com/lifemax/rpg/feature/overview/`, tests under `lifemax_modular_v73/tests/` and `android-wrapper/native/src/test/`.

## 1. What it is

The Overview's head and cards drawn by a Compose screen (`NativeOverviewCards`, tab `dashboard`, capability `overview.cards.v1`) behind Settings, Appearance, "Native Overview cards (beta)" (`nativeOverviewCards`), **off by default**. The page is the only writer; every card's fields come from the data function the page's own card draws from. `50-native-overview.js` owns the view and registers one composite that asks the cards first, then the live shell (ADR 0012). The cards give up for the session on a missing capability or a rejected open or update (`onFail`), and the live shell takes the view.

Built beyond the plan's phase 1: the native editor (phase 2), arrival motion, `DialIntro`, the cold-start veil and the cat portrait (phase 3). Phase 4 (default on) is the owner's call and is not done; no default has been changed.

## 2. Cards and what each shows

| Tile id | Card | Projection (`tiles.*`) | Page source | Kotlin | Test |
|---|---|---|---|---|---|
| head | date, greeting (heading), Edit, class crest | `head` | `ovHeadData` | `cards/OverviewHead.kt` | `OverviewTest`, `OverviewSemanticsTest` |
| (tip) | feature tip with a dismiss | `tip` | `FEATURE_TIPS.dashboard` | `OverviewTipCard` | `OverviewViewModelTest.theTipIsDismissedOnceAndARefusalSaysWhy` |
| plan | Today's plan glance: title, link, dial of sessions, centre, foot, set-up and no-day states | `plan` | `LifeDirector.native.glance` | Life Director's `DirectorGlance` | `DirectorGlanceTest`, `overview-glance.test.js` |
| next | Next moves, up to 4 | `next` (`go`: view, attr or luck) | `ovNextData` | `cards/NextMovesCard.kt` | `native-overview-cards.test.js`, `OverviewCardsAScreenshotTest` |
| hero | ring, level, XP, streak, foot | `hero` | `ovHeroData` | `HeroCard.kt`, `TodayRing.kt` | `OverviewTest`, screenshots |
| rewards | Sparks, streak, shields chips, Shop | `rewards` | `ovRewardsData` | `SmallCards.kt` | `OverviewSemanticsTest.theRewardsCardIsOneButton...` |
| apps | App shortcuts row, Add app, hint | `apps` (null off the app) | `ovAppsData` | `AppsCard.kt` | `OverviewViewModelTest.appShortcutsStay...` |
| health | Activity today: set-up (off) or steps, bar, 7 days, chips (on) | `health` (null off the app) | `ovHealthData` | `HealthCard.kt` | `OverviewCardsBTest` |
| attrs | Attributes: 8 gems with dot, level, week | `attrs` | `ovAttrsData` | `AttributesCard.kt` | `OverviewSemanticsTest.theGems...` |
| week | This week: total, delta, stacked bars, pace line, day readout, top pills | `week` | `ovWeekData` | `WeekCard.kt`, `WeekChart.kt` | `OverviewCardsBTest`, `OverviewViewModelTest.theWeekDaySurvives...` |
| cat | Cat: portrait, name, hearts; or "IN MEMORY" icon card; none without a cat | `cat` | `petPeekData`, `petPortrait` | `CatCard.kt`, `PetPortrait.kt` | `OverviewPortraitTest` |
| luck | Daily luck: score, tier text, Roll / View roll | `luck` | `ovLuckData` | `SmallCards.kt` | `OverviewViewModelTest.theLuckRollKeepsItsOperation...` |
| recent | Recent activity: 4 logs or the empty line, History | `recent` | `ovRecentData` | `SmallCards.kt` | screenshots |

Order comes from `layout` (`ovLayout()`), never the JSON; a tile the layout hides is not drawn; an unknown id is skipped (`OverviewTest.theOrderIsTheLayoutsNotTheJsons`). Also in the projection: `tileInfo` (editor rows), `editor` (OV_EDIT_WORDS), `images` (by FNV-1a key), `notice`, `celebrations`, `celebrationTarget`, `motion.reduced`, `haptics`, `arrival`.

## 3. Actions and commands

Destinations are the `navigate` action with a view; the A23 hand-over closes the screen after a native destination opens, or at once for a web one.

| Tap | Wire | Page runs | Afterwards | Test |
|---|---|---|---|---|
| Hero, Attributes "Details ›" | navigate `character` | hand-over | native Character if on | `OverviewViewModelTest.everyTapReachesThePage` |
| A Next move with a view | navigate (log, quests, plan, pet) | hand-over | | same |
| Recent "History ›" | navigate `history` | hand-over | the Journal, behind its lock | same |
| Health off "Set up health sync" | navigate `settings` | hand-over | | same |
| Cat card | navigate `pet` | hand-over | the live room (ADR 0003) | same |
| Glance: arc, centre, link, button | command `plan {act, id}` | `LifeDirector.native.fromGlance` | leaves the view | `native-overview-cards.test.js` |
| Gem, or a Train move | command `attr {id, tile}` | scroll tile into view, `openAttr` | sheet; screen steps aside | same |
| Roll / View roll, a luck move | command `luck {op}` | `openLuck` (rolls and saves the day once) | steps aside; a repeat changes nothing | same |
| Rewards card or Shop | command `shop` | `ecoShopOpen` | steps aside | same |
| Health card | command `health` | `lmaHealthOpen` | steps aside | same |
| An app | command `appLaunch {pkg}` | `AppsShortcuts.launch`, only for an added app | stays | `...appShortcutsStayAndOnlyThePagesAppsLaunch` |
| Add app | command `appAdd` | picker | steps aside | |
| Apps "Edit ›" | command `appsEdit` | Apps tab | leaves | |
| Tip ✕ | command `tip {op}` | `dismissFeatureTip('dashboard')` | stays; repeat "Already recorded." | |
| Edit (older app) | command `edit` | `ovEditOpen` | steps aside | |
| Editor change | command `layout {order, hidden}`, `layoutReset` | `ovLayoutSave` | stays; refusal snaps back with the page's words | `OverviewEditControllerTest`, `native-overview-editor.test.js` |
| Week day | local | | selection kept in the view model, 6 ms buzz | `OverviewCardsBTest.theWeekPickIsTheSixMillisecondBuzz` |

Operation ids are kept per (command, target) until a terminal reply (`OverviewTaps`); a waiting command stops waiting when the screen reopens. Wire names: `OverviewTest.commandsGoOutAsThePagesReplyTableHasThem`.

## 4. Covering rules (when the screen steps aside, and comes back)

`cfg.blocked` (plus the kit's base rule: view, onboarding, top overlay, noscroll): gave up; `innerWidth > 860` (landscape: the page's wide Overview); the thumbnail warm pass; a celebration the page plays (`celebrating()`, one `afterCelebrations` wait); `.luov` (level-up) or `.lmv-hud.on` (voice HUD); the page's `#ovEdit` open. Sheets that are body children, the noscroll class, `#ovEdit`'s hidden attribute, `lmvHudOpen` and `resize` bring it back. Tested in `native-overview-cards.test.js` (each rule, both ways), the hand-over simulation in four modes, `native-swipe-overview.test.js`.

## 5. Other behaviours

- **Celebrations** that start while the cards show reach them through `fxSink` and play once per id (`PageCelebration`); the page plays sound and buzz 120 ms after sending.
- **Notice**: the page's toast, as `notice {id, text}`, drawn with `PageToast` once per id (`OverviewViewModelTest.thePagesMessageShowsOnce`).
- **Arrival** (`arrival` counter): ring sweep, week bars, count-up, fills, rows rising, the glance's dial; never when hidden or with reduced motion (`OverviewArrivalTest`).
- **Veil**: `html.native-boot` hides the page's Overview during a cold start until the screen opens, declines, or 1500 ms.
- **Pet loop** stops while the cards cover the page (`NativeOverviewCards.covering()`); `lm_pet_seen` is refreshed only in the room.
- **Tab swipe**: native neighbour both ways with Native tab swipe on; none with it off. The apps row and the editor's grip take `noTabSwipe()`.
- **Refresh**: `renderOverview`, `ovAppsRender`, command replies, the sink, resume, every 15 s while visible.

## 6. Covering rule coverage by Kotlin tests

`OverviewTest` (decoder, strictness, ordering), `OverviewViewModelTest`, `OverviewCardsBTest`, `OverviewEditorTest` and `OverviewEditControllerTest` (slot maths, TalkBack moves, refusal), `OverviewArrivalTest`, `OverviewPortraitTest`, `OverviewSemanticsTest`; screenshots `OverviewScreenScreenshotTest`, `OverviewCardsA/B`, `OverviewEditorScreenshotTest`, `OverviewEditorInScreenScreenshotTest`, `OverviewArrivalScreenshotTest`, `OverviewPetPortraitScreenshotTest`.

## 7. TalkBack parity pass

Method: the merged semantics tree Compose builds (what TalkBack is given) is read for each state in `OverviewSemanticsTest` (this module has no Compose test rule; `SemanticsProbe` reads the view's semantics owner after Paparazzi lays out a frame; `LMA_SEMANTICS_DUMP=<folder>` writes each tree as text). The web column is from the page's markup.

| Node | Web (today's body) | Native | Status |
|---|---|---|---|
| Date, greeting | eyebrow div, `h1` | text, heading | same |
| Edit | button, `aria-label`, `aria-expanded` while open | button, page's a11y text, **expand/collapse action** | gap fixed |
| Crest | decorative | cleared | same |
| Tip | icon `aria-hidden`; ✕ button | icon cleared; ✕ "Dismiss tip" button, 48 dp | same |
| Card titles | `h3` | heading | same |
| Next move | button, text including `✓` or `›` | button; `✓` read for a done move, `›` hidden | gap fixed |
| Hero | clickable div, no role | one button, merged text | better (role); reads the avatar initial and arrows as the page does |
| Rewards | clickable div + link | one button, link part of it | gap fixed (was a second button) |
| Apps | buttons, `aria-label`; add button label, plus in text | buttons by the page's label; plus cleared | gap fixed |
| Health | clickable div; days with labels | one button; each day its own node "label a11y"; off state keeps its Settings button as a second stop (a different destination) | same |
| Gem | button, text, dot `title` | one button, one description "name, level, week, dot" | gap fixed (words plus a description may be read as the description alone) |
| Week days | seven buttons, no group | radio buttons in a selectable group, exactly one selected, the page's label | better |
| Day readout | not live | polite live region | deliberate: the readout is not in the focused node |
| Cat | clickable div | one button, the name ("IN MEMORY" for a cat gone) | hearts not spoken, on the page either (BACKLOG) |
| Luck | text and a button | text, button 48 dp | same |
| Recent | rows, link | rows, link 48 dp | same |
| Glance | link and dial | heading, link, a 48 dp node per arc named by the page with state, foot | owned by Life Director's dial |
| Toast | `aria-live=polite` | polite live region (`PageToast`) | same |
| Editor | `h3`, Done, rows with eye (`aria-pressed`) and grip (arrow keys) | heading, hint, Done, rows with TalkBack actions (move up/down/top/bottom, hide/show), eye button, Reset | actions replace arrow keys |

Targets: every clickable node reports at least 48 x 48 dp (touch bounds), checked for all states; links keep the page's drawn size and are widened to 48 dp for touch. Nothing is read twice in a row and no button sits inside a button except Health off's set-up button. Reading order is the layout's order; the editor opens in place right after Edit, so the next stop is its heading; the head, tip and tiles are one list.

Reduced motion: `motion.reduced` (the page's setting or the system's) turns off arrival motion, the editor's glide and open animation, and the celebration's flight; Compose also follows the system animation scale.

**Only a phone can confirm:** how TalkBack speaks the merged nodes, in particular a node that has both words and a description; that focus returns to Edit when the editor closes (done only with touch exploration on); that the Edit button announces expanded/collapsed; that linear navigation never reaches the web page behind the screen (plan phase check 12); the live readout and the toast not being spoken twice; the glance arcs' order against the dial; large text and reduced motion (checks 15); that the cat card, with no hearts spoken, is acceptable.

## 8. Rollout

Off by default. Plan phase 4: the switch may default on once the owner says so (A33, and Native tab swipe default on or open decision 5 answered); `defaultOn` per NATIVE_TAB_GUIDE.md. Not done. The phone checks of plans/OVERVIEW_BODY.md section 5 and the memory and tab timing entries in the catalog are open.


## Live cat (Native cats, beta)

Settings, Appearance: "Native cats (beta)" (`nativeCats`, off by default; needs Native Overview cards). With it on and the screen listing capability `overview.cat.live.v2` (Native API 48), the cat tile's `live` is `{cats: [...]}`: one or two cats, each exactly `{coat, stage, weak}` and a `name` (the first is the page's first cat, the second its second cat when it is alive and native has art for it). A screen that lists only `overview.cat.live.v1` (Native API 46) is sent the old shape, the first cat's bare `{coat, stage, weak}`; with neither, or with the switch off, `live` is `null`. The page picks the shape by the handshake, the decoder (`liveCats`) is strict about both (exact keys, a list of one or two, a name of 1 to 40 characters, a name only in the list shape), and a cat card that is not alive carries no `live`.

With live cats, the cat card draws the room itself instead of the still portrait (`feature/pet/room`, `feature/pet/presentation`): the whole 576 x 144 house, as the web room paints it (`RoomScene` follows `petDraw`: the sky behind each window in six bands with stars, sun or moon and clouds by the device's local hour, the three painted rooms, the window light on the floor, then the furniture and the cats back to front by depth: bed back, bowls, bed front, the box, the pouf, the cushion, each cat between the pieces it stands among, then the multiply time-of-day tint and the living room lamp's glow). The card shows a window of at least 192 room pixels onto the house at a whole number of device pixels per room pixel, `FilterQuality.None`, the bottom of the house on the bottom edge (a tall card shows the top row of the walls stretched up), and the ground shadow is the room's two rows at alpha 0.28. The painted backdrops (living room at house x 0, bedroom at 192, kitchen at 384) and the props ship as assets written by `tools/gen_pet_native_assets.js` (checked in CI by `--check`) and the item atlas is the phase P2 one.

The camera (`RoomCamera`, `CameraDirector`) is the web's (`petCamUpdate`): it eases after the cat with a lead of 14 px (26 running), at rate 1.6 resting, 2.6 walking, 4.2 running, leaves a resting cat alone inside 14 px and settles on a whole pixel, cuts when the target jumps more than 260 px, and keeps the view inside the house. With two cats it frames the midpoint when they are within 110 px of each other, and otherwise follows the one that moves (staying with a cat until it has been still for 0.6 s while the other travels); with neither moving it stays. A cat whose sprites are not here is not drawn and the camera ignores it.

The cats (`HouseLife`, one `CatBody` and one `Autopilot` in house mode each, seeded by the cat's name and coat) roam the whole house, world x -154 to 346, on the floor baseline (feet row 132 at depth 35): they sit, groom, stretch, loaf, wander a few steps, walk to another room now and then, eat at the bowls (a hungry cat more often; the first cat at x 87 facing right, the second at x 121 facing left), nap on the bed, sit on the sofa, climb the cat tree and watch from the bedroom window sill, always coming down again (`HousePlans`, over the P1 `RoutePlanner` and `World` surfaces). They stop clear of the cushion, the pouf, the box and the bed's front rim (the room draws them in front of a cat at the floor baseline). They keep a polite distance: each cat reserves the stretch of floor its plan uses (and the bed or tree it will be on), the other plans only what keeps 40 px clear of it, so two cats on the same surface are never closer than that and the floor order of the two changes only when one goes up on furniture. The food bowl shows food when neither cat is hungry. Nothing in a day reads a clock: the same seeds and frame times replay the same day at any frame rate. The room's own clock (clouds, stars) and the hour are the only things that follow time.

Drawing is one `withFrameNanos` loop, no allocation per frame (`RoomAllocationTest`), and it runs only while the card is visible and the app resumed; with the page's reduced motion or the phone's "remove animations" the room is drawn once, the cats sitting, and the clock never runs. If the first cat's sprites are not here (loading, a refused coat) the still portrait stays; if the room art cannot load the cat lives in the plain strip of the earlier phase.

The four legacy coats (ginger, gray, black, tuxedo) at any growth stage draw live at once. A genetic `dna_...` coat draws live too when the screen also lists `overview.cat.coat.v1` (Native API 47) and the page holds the coat (`petCoatKnown`, either cat's): the page stays the only composer, and native asks it for the sheets with the command `coat {key, kind}` (`kind`: `adult`, `depth`, `st0`..`st5`, `dst0`..`dst5`; `50-pet-coat.js`), answered `{ok, coat, kind, png, palette}`: the grey-index PNG `petDnaCompose` builds (what native recolours, not RGBA) as base64 (at most 600,000 characters) and the coat's 21-index palette as `{"index": "rrggbb"}`. Native (`PageCoatSource`, `CoatReply`, `CatArt`) asks once per (coat, kind), the side view first, then the depth view at once (the walk toward and away needs it at any moment), keeps the answers in small LRU caches (the sprites of up to four cats of different coats and stages), and keeps a refused coat's cat undrawn for good; a request with no reply in 20 s is asked again, at most 3 times. The reply is decoded strictly (exact keys, matching coat and kind, an 8-bit grey PNG of at most 1.5 million pixels, a palette of 1 to 64 entries with index 1..255 and six lowercase hex digits). Not yet used: the ears, tail and whisker offsets of `CatPose` and the head look (they need the rig, which is not ported); the plain sheet frames carry the art's own poses; the web room's claw marks, toys and gifts, and the glass of the lens are not part of the card's room.

Tests: `OverviewTest` (the decoder, strict, both shapes), `HouseLifeTest` (two cats: determinism, 60 and 120 Hz, separation, every room visited, never outside the house), `RoomCameraTest` (easing, clamp, cut, which cat), `RoomFidelityTest` (the empty house at five hours against the web page's own canvas, recorded by `tools/gen_pet_room_fixtures.js`: every pixel within 2 on any channel; the sky bands, painted rooms, sprites, stars and props are exact, only 8-bit blends, the multiply and the anti-aliased light and lamp edges differ), `RoomAllocationTest`, `CatLiveScreenshotTest` and `CatLiveCoatScreenshotTest` (the plain strip, unchanged), `CatHouseScreenshotTest` (both cats, day and night, the living room and the kitchen window, light and dark, a genetic second cat, large text), `PageCoatSourceTest`, `CoatReplyTest`, `CoatFidelityTest`, `native-overview-cards.test.js` (the projection with one cat, two, a genetic second cat, an older screen, the switch off) and `pet-coat.test.js`. Phone only: smoothness at 120 Hz, battery, how the two cats feel together, the real clock's light.
