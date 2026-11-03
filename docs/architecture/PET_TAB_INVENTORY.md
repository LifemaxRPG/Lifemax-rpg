# Cat tab (Native Cat, beta) native slice inventory

Baseline: phase CT1 of `docs/specs/CAT_TAB_PLAN.md` (Native API 49). Read against the code. Paths: web under `lifemax_modular_v73/src/runtime/segments/`, Kotlin under `android-wrapper/native/src/main/java/com/lifemax/rpg/`. The engine's own record is `docs/specs/PET_AS_BUILT.md`; the plan this phase follows is `docs/specs/CAT_TAB_PLAN.md` (section 1 is the full inventory of the web tab, this file is what CT1 drew).

## 1. What it is

The Cat tab's room and buttons drawn by a Compose screen (`NativePet`, tab `pet`, capability `pet.projection.v1`, `pet.coat.v1` for a genetic coat) behind Settings, Appearance, "Native Cat (beta)" (`nativePet`), **off by default**. The page is the only writer of the cat (`state.pet`, `pet2`, `petPair`, `careDay`, ...): the screen draws a projection and sends commands, which run the page's own functions and answer `ok` only once the save stored the change.

**In this first release the cats in the room are not the page's cats.** Each lives a day of its own on the phone (the Overview card's autopilot, `HouseLife`), so they do not follow the page's day schedule, do not play, do not answer touch and do not pair up. That ends with the brain link (CT3) and is said on the Settings row. What the owner does for them is real: a meal is the page's meal, and the room's cat walks to its bowl and eats when it is served.

The tab opens only for a living cat whose coat native can draw. The intro (no cat yet) and the memorial (a cat that has passed, which also happens while the screen shows) are the page's (`blocked()`), as is everything marked H below.

## 2. What the screen shows

| Element | Projection | Page source | Kotlin | Test |
|---|---|---|---|---|
| The room, pinned at the top, full width, whole device pixels per room pixel, the camera following the cats | `cats[]` `{name, coat, stage, weak}` | `petGrowIdx`, `petStage` | `presentation/RoomHost.kt` (shared with the Overview's cat card), `HouseStage.kt` | `PetScreenshotTest`, `RoomFidelityTest`, `RoomAllocationTest` |
| Eyebrow (KITTEN / COMPANION and the bond name), title, five hearts | `eyebrow`, `name`, `hearts` | `petStatusData` | `PetScreen.kt`, `PetParts.kt` (`PetHearts`) | `PetProjectionTest`, `native-pet.test.js` |
| Mood line (hungry text by stage) | `mood` | `petMoodText` via `petStatusData` | `PetScreen.kt` | same |
| Bond bar and its words | `bond {pct, text}` | `petStatusData` | `PetParts.kt` (`PetBondBar`) | same |
| A kitten's growth line | `grow {label, day, pct, text}` or null | `petGrowData` (extracted from `petGrowStatus`) | `PetGrowthLine` | `PetProjectionTest.aKittenHasAGrowthLine` |
| Feed button: label, waiting look | `feed {label, wait}` | `petStatusData`; two living cats: `petPairFeedData` ("Feed both") | `PetScreen.kt` | `native-pet.test.js` (the projection against the DOM) |
| Play button | none | | `PetSecondaryButton` | H, below |
| Slow blink button | none (`words.blinkHint` is its TalkBack text) | | `PetSecondaryButton` | `PetViewModelTest` |
| Stats: last meal ("Vacation / clock paused" on vacation), meals today, days together | `lastMeal`, `mealsToday`, `days` | `petStatusData` | `PetStats` (stacked rows with large text) | `PetProjectionTest`, `largerText` screenshot |
| Favorite place, favorite toy | `favs {place, toy}` | `petFavsData` (extracted from `petLikesRender`; reads only) | `PetFavourites` | `PetProjectionTest` |
| Hint line (stray, wary, hungry, close friend texts) | `hint` | `petStatusData` | `PetScreen.kt` | same |
| Care and settings (closed to begin with): the page's two paragraphs, Vacation mode switch, name | `care {how, bond, vacationHint, vacation, name, nameMax}` | `PET_CARE_TEXT` (shared with the page's card) | `PetCareCard.kt` | `theCareCardOpen` screenshots |
| The page's toast (it is under the screen) | `notice {id, text}` | `NativeTabKit` (`notices: true`) | `PetScreen.kt` (`NoticeToast`) | `PetViewModelTest` |
| One line about a second cat | `together` | | `MoreRow` | `PetProjectionTest.twoCatsShareOneFeedButton` |

Whose card it is: the focused cat of the page's switcher when two cats are alive and the page's second runtime exists (`PET_PAIR.focus`), else the first. There is no switcher in CT1; both cats live in the room.

One source for the words: `petStatusData(p, now)` (17-runtime.js) is what `petRenderStatus` fills the DOM from and what the projection reads, so the page's card and the screen cannot say different things (`native-pet.test.js` compares them state by state: content, hungry, very hungry, weak, vacation, kitten, stray, devoted, with likes). It reads and writes nothing, not even the day roll (a new day shows 0 meals).

## 3. Commands and actions

| Tap | Wire | Page runs | Afterwards | Test |
|---|---|---|---|---|
| Feed | command `feed {op}` | `petFeedClick` (wrapped for two cats: whoever is hungry), after `petInit` and `petPairEnsure`; the Feed gate first | reply `ok` only once stored; the next projection carries the fed cue; the screen's cat goes to its bowl | `native-pet.test.js` (meal, repeat, refusal, vacation, failed save, two cats), `PetViewModelTest` |
| Feed, not due | the same command | refused in the page's words (`petFeedRefusal`): `why` `full` with `inHours`, or `vacation` | `full`: reaction `bowl` (the cat goes to its bowl and washes, as the page's does) | same |
| Slow blink | command `blink` | `petBlinkClick`; its toast (asleep, or three blinks in six seconds) is the reply's message and is not also shown as a notice | reaction `blink`: the room's cat blinks back (and looks up if it was doing nothing) | same, `FeedingTest` |
| Vacation switch | command `vacation {on}` | `petSetVacation` for both cats | stored or taken back; the same value again "Already recorded." | same |
| Rename | command `rename {name}` | `petRename` for the cat whose card shows, 1 to 14 characters | stored or taken back | same |
| A genetic coat's sheet | command `coat {key, kind}` | `petCoatAnswer` (50-pet-coat.js, the Overview cards' command too) | the room draws the coat | `native-pet.test.js`, `CoatReplyTest` |
| Play | action `play` | `petPlayClick` (opens the page's toy tray) | H: the screen steps aside (`handedOver`) and comes back on the next visit to the tab | `native-pet.test.js` |
| "View room" (corner chip) | action `room` | `petRoomFullscreenOpen` | H: the screen comes back when the full-screen room closes (`pet-full-open`, `watch`) | same |
| "More on the Cat page" | action `page` | none (the page's own Cat view is under the screen) | H: back on the next visit | same |

Operation ids: Feed's id is kept by the view model until the page's answer is final (landed or refused) and sent again after an answer that never arrived (the screen closed while the page answered); the page remembers the last 40 that landed and answers "Already recorded." without a second meal. A reply reaches only a showing screen.

The fed cue: every meal, however served, is `petFeed()` returning `ok`, so `50-native-pet.js` wraps it once and records `{cat, id}`; the projection carries the last two with `ago` milliseconds. The screen plays each id once; the first projection after it opens plays only a meal less than four seconds old (the owner came here from a deep link or the voice, which feed through the page), never an old one. The cue is the only trigger for the cat's walk to its bowl: the command's reply adds nothing, so there is never a second walk.

## 4. Covering rules (when the screen steps aside, and comes back)

`cfg.blocked` plus the kit's base rule (view, onboarding, top overlay, noscroll): a cat that is not alive, or whose coat native cannot draw (a genetic coat before the handshake has said `pet.coat.v1`, or without it); a visit handed to the page (`handedOver`, cleared by `arrive()` when the owner comes back to the tab); the page's full-screen room (`body.pet-full-open`); `.luov` (a level-up, which a meal's cat-care XP can end in). `watch()` lets the screen come back by itself when the full-screen room closes. `render: renderPetView` refreshes the screen after every page render, so a cat that passes (`petCheck`) steps the screen aside for the memorial at once.

The page's own room loop (`petFrame`) stops while the screen covers the view: `petWantRun` asks `NativePet.covering()`, and `afterOpen` and `afterClose` call `petSync` (so the page's canvas is not drawn twice). Consequences for CT1: the page's cats do not move while the screen is up, and things that only its loop does wait for the page's own Cat view: the "is growing up" toast and the new-favourite toast, the brain's periodic save. Nothing here loses data (all of it is derived or re-derived).

## 5. The room (shared with the Overview card)

`RoomHost` (`feature/pet/presentation`) is the Overview card's room stage lifted out of `CatCard`: `rememberRoomState` loads the art and builds the `HouseStage` (state `Loading`, `Unavailable` or `Ready`), `RoomHost` draws it (`HouseLiveView`: one frame clock, no frames while hidden, a still pose with the phone's "remove animations"). The card passes its own frame (a 4:3 box with a hairline) and fallbacks (the plain strip, the still portrait); this screen pins it full width above the cards. The refactor moves no pixel of the card: every existing screenshot and `RoomFidelityTest` are unchanged.

What the owner's care does in the room (`HouseStage.feed`, `visitBowl`, `slowBlink` over `HouseLife.serveMeal`, `visitBowl`, `slowBlink`): the cat leaves what it was doing, takes the room's route to its bowl (the first cat the left bowl facing right, the second the right bowl facing left, as the page's `EAT2`), eats 7.5 s and washes 4.5 s; the bowl shows food for 14 s; a cat that is full goes to its bowl, sits 1.6 s and washes 3 s; a slow blink is returned (a sleeping cat is left alone) and a cat doing nothing looks up for 2.2 s. The autopilot rests a moment after the plan and carries on; it draws nothing from its random stream for these plans, so a house that is never cared for replays exactly as before (`HouseLifeTest`, `FeedingTest`).

A genetic coat's sheets are asked of the page: `CatArt.coats` is one source for every screen with a room, so the screen that opens (`accept` with an explicit open) binds its own requester; replies to `coat` are handled by whichever plugin receives them.

The cat's sheets carry twelve extra animation rows after the page's 28 (jump_up, jump_air, jump_land, turn, pickup, carry, toss, yawn, shake, bump, groom_other, nose_touch; Native API 52), made by the art lab (`docs/specs/PET_ART_LAB.md`). The room draws them like any row (`CatArt` resolves every clip by name); the autopilot does not choose them yet (CT4/CT5). Their mouth anchors and pair offsets are in `LabTable` (`lab.json`).
The room has one more switch, `RoomStyle` (CT9, off): `rememberRoomState(looks, style = ...)` and `HouseStage(kit, house, style)` take it, and the default `RoomStyle.CLASSIC` draws the room as above; `RoomStyle.DEPTH25` draws the 2.5D room (furniture as layers, parallax, thickness, soft shadows, window light, rim light; `PET_ENGINE_PLAN.md` section 5.1). No screen passes it yet.

## 6. Tests

- Web: `tests/native-pet.test.js` (44 checks: the switch and its words, the intro and the memorial, the projection against the page's card for ten states and writing nothing, the stopped loop, every command with its refusals, repeats and failed saves, two cats, the voice's feed cue, the coat command and the capabilities, Play, the room, more, a level-up); `native-handover-sim.test.js` (the Cat tab is a fake `NativePet` in every mode, with a living cat in the starting save; `PETMUT=blocked` fails it); `native-settings.test.js` (the switch runs `setNativePetEnabled`); fixtures `tools/gen_pet_fixtures.js`.
- Kotlin: `PetProjectionTest` (every fixture, 34 malformed variants refused with the last good state kept), `PetRepositoryTest` (ordering, sessions, the replies, the commands' payloads), `PetViewModelTest` (waiting, operation ids, cues played once, messages once, nothing while hidden), `FeedingTest` (the walk to the bowl, both bowls, down from the sofa, a full cat, blinks, determinism, the autopilot afterwards), `RoomAllocationTest` (a frame of the Cat screen with the owner's care allocates nothing), `PetScreenshotTest` (content on the sofa dark and light, at night, hungry, very hungry, weak, on vacation, a kitten with its growth line, a stray, two cats, a genetic coat, large text, the care card open dark and light, Feed waiting for the page).

## 7. Rollout and what needs the phone

Off by default, "(beta)". `NativeApi.VALUE` 49. Not the default: that is after CT5 (plan, open question 6). On the phone: the feel of the room at 60 fps on a large canvas (target under 4 ms of CPU a frame), the page's loop really stopped under the screen (`petWantRun` through `diagnostics`), the haptic of Feed (the page's own buzz), a deep-link feed playing its cue, the coat command with a real genetic cat, and TalkBack over the room and the buttons.

Not done in CT1, by plan: touch and the full-screen room (CT2), the brain driving the cats (CT3), toys and play (CT4), sleep spots, the box and the pair (CT5), the panels, the cattery, the intro and the memorial (CT6).

## 8. The panels (CT6 part A, capability `pet.panels.v1`, Native API 52)

Read-mostly cards for the cat whose card shows, from the `panels` block of the projection (`50-native-pet-panels.js`); older pairings lack the block and the screen is as in sections 1 to 7. Every word is the page's: the DOM renderers were split into data functions (shared by the DOM and the projection) and HTML functions, and the DOM is byte-identical to before (golden file `tests/fixtures/pet-panels-golden.json`).

| Card | Where | Projection | Page source (data function) | Kotlin | Notes |
|---|---|---|---|---|---|
| Mind row: bond style chip, mood chip, the sentence, "While you were away" | inside the cat's card, under the mood line | `panels.mind` | `petMindData` (26) | `PetMindCards.kt` (`PetMindRow`) | the away note only while fresh (15 min) |
| Together: switcher between two cats, level, bar, text, away note, counts, pecking order, last | a card right under the cat's card | `panels.pair {card, focus, cats}` | `petPairCardData`, `petRankCardData` (29) | `PetTogetherCard.kt` | only with two living cats; `focus {i}` runs `petPairFocus`; the pair's behaviour is CT5 |
| What the cat likes: places and toys as bars, the favourite marked | collapsible, after Care and settings | `panels.likes` | `petLikesData` (25) | `PetMindCards.kt` (`PetLikesCard`) | |
| Inner life: four threads, quirks, memories, small print, comfort zone | collapsible | `panels.inner` | `petInnerData` (26) | `PetMindCards.kt` (`PetInnerCard`) | |
| Genetics: sex, karyotype, coat, ten gene rows, carries, hidden, body shape, temperament bars, mutations, full genome | collapsible | `panels.dna` | `petDnaPanelData` (32), `petMorphPanelData` (37) | `PetDnaCard.kt` | read only; the genome rows (about forty) are sent only while the table is open (`expand {open}`, 30 minutes at most) |
| Toys: every toy with the tray's word or what it waits for ("Unlocks at Friend"), early unlock for Sparks | collapsible | `panels.toys` | `petToysData` (25), `ECO_TOY_PRICE` (44) | `PetToysCard.kt` | `buyToy {id}` is the Rewards shop's `ecoBuy`; Play and the tray stay on the page (CT4) |

The projection builds the panels from a copy of the cat (`Object.assign({}, cat)`) under `petAs(focus)`, so it writes nothing and makes no brain, genes or likes record; a female cat's texts get "she" as the page's DOM fixer gives them; a part that fails to build is null and its card is left out. Strict decoder: `data/PetPanelsJson.kt`. Commands: `focus`, `buyToy`, `expand` (BRIDGE_CATALOG.json). Not shown: the memorial list (the web's Cat tab shows past cats only as "In memory" on the intro), the switcher's sprite faces (names only), the second-cat invitation and adoption (CT6 part B).

Tests: `native-pet-panels.test.js` (golden HTML, DOM equality for nine states, capability gate, nothing written, commands, a seeded hand-over simulation with `PANELMUT=twice|focus` mutations that must fail it); Kotlin `PetPanelsTest`, `PetPanelCommandsTest`; screenshots `PetPanelsScreenshotTest`; fixtures `pet-panels-*.json` from `tools/gen_pet_fixtures.js`.
## Photo album and scrapbook (S1, page side only)

The page now keeps a per-cat album of memorable moments (state.pet.album, state.pet2.album, copied into state.petMemorial on passing) and the API for a later native phase to stage portraits and draw the scrapbook. Nothing is projected to native yet and no capability exists. See PET_SCRAPBOOK.md.

## 9. The solid furniture (CT9, Native API 53, off)

Behind the same `RoomStyle` switch (still off; `RoomStyle.DEPTH25` turns it on, `DEPTH25_STILL` without sway), the 2.5D room's furniture is no longer a flat sprite with a dark copy behind it: each of the 13 standing pieces is a small solid rendered by `feature/pet/room` (`VolumeModel`, `VolumeRaster`, `VolumeSheets`, `DepthOrder`, `DepthScene`), from depth maps generated by `tools/pet_room_volume.js` out of the measured profiles in `tools/pet_room_volume_spec.js` (CI: `gen_pet_native_assets.js --check` and `gen_pet_room_layers.js --check`). How it works, the numbers of the three depth levels and the tests: `docs/specs/PET_ENGINE_PLAN.md` section 5.1.

- **What the screens see.** Nothing until a screen passes the style. At rest every piece is its painted sprite exactly (tested per piece and for the whole room); a camera sway shows the sides and tops in each piece's own colours, the floor shears row by row, and cats pass behind, in front of and inside the pieces by depth.
- **What changed outside the room package.** `RoomCanvas.sheet` (a picture the room renders at run time, one cached bitmap per camera step): `ComposeRoomCanvas` implements it with an array of slots; `PetArtResources.loadRoom` reads the layer pictures by their path under `room/` (the volume maps live in `room/volume/`). `HouseStage` is untouched; it calls `ContactShadow.paint` as before (its new colour parameter defaults to the art's ink).
- **What the body needs** (`feature/pet/body`, not changed here; the manifest's `volume` entries are the contract). A cat on `couch`, `mid`, `top` or `counter` is drawn at that surface's depth behind the piece's front (`volume.surfaces`: 4, 1, 3, 5 floor rows) and moves with it when the camera sways; a cat on `mid` has the top platform drawn wholly over it (`volume.covered`), because the sitting pose is taller than the gap between the platforms (the body could prefer a lying pose there). A cat on the floor sorts by `World.floorY(z)`, so a body that walks a cat behind the sofa, beside the tree or under the counter's overhang needs nothing more than a z. The fridge has no surface yet: if the parkour graph adds a fridge top, the profile's top is 4 to 9 rows back (a surface depth of about 6) and `pet_room_volume_spec.js` takes it in `surfaces`.
