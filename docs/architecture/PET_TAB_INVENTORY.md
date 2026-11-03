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

## 6. Tests

- Web: `tests/native-pet.test.js` (44 checks: the switch and its words, the intro and the memorial, the projection against the page's card for ten states and writing nothing, the stopped loop, every command with its refusals, repeats and failed saves, two cats, the voice's feed cue, the coat command and the capabilities, Play, the room, more, a level-up); `native-handover-sim.test.js` (the Cat tab is a fake `NativePet` in every mode, with a living cat in the starting save; `PETMUT=blocked` fails it); `native-settings.test.js` (the switch runs `setNativePetEnabled`); fixtures `tools/gen_pet_fixtures.js`.
- Kotlin: `PetProjectionTest` (every fixture, 34 malformed variants refused with the last good state kept), `PetRepositoryTest` (ordering, sessions, the replies, the commands' payloads), `PetViewModelTest` (waiting, operation ids, cues played once, messages once, nothing while hidden), `FeedingTest` (the walk to the bowl, both bowls, down from the sofa, a full cat, blinks, determinism, the autopilot afterwards), `RoomAllocationTest` (a frame of the Cat screen with the owner's care allocates nothing), `PetScreenshotTest` (content on the sofa dark and light, at night, hungry, very hungry, weak, on vacation, a kitten with its growth line, a stray, two cats, a genetic coat, large text, the care card open dark and light, Feed waiting for the page).

## 7. Rollout and what needs the phone

Off by default, "(beta)". `NativeApi.VALUE` 49. Not the default: that is after CT5 (plan, open question 6). On the phone: the feel of the room at 60 fps on a large canvas (target under 4 ms of CPU a frame), the page's loop really stopped under the screen (`petWantRun` through `diagnostics`), the haptic of Feed (the page's own buzz), a deep-link feed playing its cue, the coat command with a real genetic cat, and TalkBack over the room and the buttons.

Not done in CT1, by plan: touch and the full-screen room (CT2), the brain driving the cats (CT3), toys and play (CT4), sleep spots, the box and the pair (CT5), the panels, the cattery, the intro and the memorial (CT6).

## Photo album and scrapbook (S1, page side only)

The page now keeps a per-cat album of memorable moments (state.pet.album, state.pet2.album, copied into state.petMemorial on passing) and the API for a later native phase to stage portraits and draw the scrapbook. Nothing is projected to native yet and no capability exists. See PET_SCRAPBOOK.md.
