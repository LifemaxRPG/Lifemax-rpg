# Pet sprite steadiness audit (Native API 99)

The owner's report, on the native cat (Overview card and Cat tab) with the cat sitting still: "you can see pixels popping up and disappearing, the coat looks like it's constantly changing and flickering because of little pixel changes." The room shake (inventory 12a) and the pale rim outline were fixed before this audit and are not part of it. This page is the audit of every sprite animation for pixel consistency between frames: how it was measured, the causes found, the fix, and the numbers per clip before and after.

## How it is measured

`SteadinessProbe` (test code, `feature/pet/presentation`) draws a cat's poses frame by frame on a software canvas (`SpriteRaster`) exactly as `drawSpriteAt` asks the Compose canvas to: the same picture (the rig's composed frame or the sheet's own frame), the same placement against the paws, the same `translate`, `rotate`, `scale` and, after the fix, the same bands; every device pixel centre is taken back through the transform and sampled nearest neighbour, which is what `FilterQuality.None` does. Each frame is compared with the one before after taking away the sprite's own whole-pixel move (so a walking cat is not "changed" by walking).

- **Clock and scale.** 120 Hz (8.3 ms frames, Galaxy S25 Ultra). The card draws `pixelScale(width)` = floor(width / 192) device pixels per art pixel: 5 on the phone (about 990 px wide at FHD+). The full-screen Cat tab room draws `fullScale` = 9 (1080 x 2340). Both are whole numbers already (`CatStage.pixelScale`, `HouseStage.fullScale`): the "non-integer pixel scale" hypothesis is ruled out for the rooms.
- **Looks.** A shipped coat grown (ginger) and as a kitten (tuxedo stage 2), and two genetic coats from the page's recorded replies with non-neutral proportions, so the rig composes every frame (calico grown, silver kitten stage 1).
- **Scenes.** Every clip of `Clips.all` (the page's 28 rows, the 4 depth rows, the 12 lab rows, which include the three pair poses `bump`, `groom_other`, `nose_touch` and the toy acts `pickup`, `carry`, `toss`; `play`, `bat`, `swat`, `kick` are page rows) acted in place by a real `CatBody` for 3 s after it settles; the resting rows for 8 s at both scales; walking, running and front/back walking for 6 s; and two cats living 40 s of their day in the house (the Overview card's room).
- **What a step is charged to.** `ART` (the clip, its frame or the way the cat faces changed: the art meant it), `RIG` (the rig's grid point changed: an ear, the tail or the whiskers moved a step), `BREATH` (the breath moved a step), `BODY` (turn, squash or stretch moved a step), or `NONE`. A pixel change on a `NONE` step is a change nothing meant: shimmer.
- **Columns.** changed steps (frames that differ from the one before), changed device pixels per step (min/median/max), coat pixels changed off the art's own frame changes (opaque in both frames, colour changed), unexplained steps (`NONE` steps that changed pixels, with their pixels), a-b-a pixels (pixels back on the colour they had two frames before), and after the fix the steps per cause.
- **Running it.** `LMA_STEADINESS_REPORT=1 ./gradlew :app:testDebugUnitTest --tests "*SpriteSteadinessReportTest*"` writes `app/build/steadiness-report.md` (a few minutes). `SteadyCatTest` runs always.

## Causes found (on the code of Native API 94)

1. **The breath was a vertical scale of the whole cat** (`CatStage.kt:116` at API 94: `scale(pose.scaleX * flip, pose.scaleY * pose.breath, ...)`, the breath from `Breathing.scale`, `body/Tail.kt:123`, 1 +- 1.2 % awake and 3 % asleep, a continuous sine). Scaling pixel art by 1.003 or 1.011 without smoothing changes which art row each device row samples, so every few frames a different handful of rows inside the coat is doubled or dropped. That is the coat "constantly changing": on a resting cat it is all but all of the change. Measured by drawing the same poses with the breath set to 1: the unexplained steps of a resting `idle_sit` fall from 497 (47 404 px) to 13 (8 996 px) in 8 s, `loaf` from 290 to 0, `sleep` from 236 to 0; in the house from 2 824 to 810.
2. **The springs' turn, squash and stretch were drawn raw** (`CatStage.kt:115-116` at API 94; values from `CatBody.compose`, `body/CatBody.kt:546-548`). A spring settling after a posture change, a footfall or a lean hovers for a while a hair from rest (up to 0.5 degree and 0.6 % on a sitting cat), and every crossing of a sampling edge flips a few coat pixels. These are the remaining 13 steps of a resting `idle_sit` and 810 of the house cat's.
3. **The rig's grid point was plain rounding with no hysteresis** (`RigMotion.read` and `RigGrid.set`/`steps`, `RigGrid.kt:111` at API 94). An ear, the tail or a whisker spring on the edge between two grid steps flicked the composed ear or tail back and forth (a, b, a): 2 to 3 times in 8 s on a resting cat, 30 times in the house cat's 40 s. Each flick re-draws the ear or the tail tip.
4. **Checked and not a cause.** The pixel scale is whole on both screens (above). The sprite is placed on whole device pixels (`HouseStage.draw`, `drawSprite` round the paws). The rig's composed frames are cached by (set, clip, frame, grid point) and composing is deterministic, so the same pose is the same pixels (the probe never saw a change with no cause once the values are held). Shear on turns: the turn is a narrowing scale (`Locomotion` `turn.scaleX`), not a shear; it is held like the other scales now. The rim light (off in the live rooms) and the room's overlay are not the sprite. The purr's `vibration` is not drawn.

## The fix (Native API 99)

One rule: the drawing changes only when something meant to move has moved a whole step, and then by whole pixels.

- **`SpriteSteady`** (`presentation/SpriteSteady.kt`, one per cat on screen, keyed by its pose): every value the drawing reads from the springs is held on a grid of steps with hysteresis (`SteadyStep`: the first value goes to its nearest step, so a single drawing such as a screenshot is plain rounding; after that a step changes only when the value is a quarter step past the middle between two steps). Turn in 1 degree steps, squash and stretch in steps of 1/32, each rig axis on its own `RigGrid` step (`RigMotion.values`, `RigGrid.setSteps`), and the breath (below). Numbers in `SteadyTuning`.
- **The breath is a lift, not a scale.** The rows of the picture above a cut (`BREATH_CUT_SHARE` 35 % of the cat's reach above the paws) are lifted or sunk by whole device pixels (`rise`), the row at the cut stretching over the gap; the paws and legs stay planted. The lift is what the old scale gave the top of the cat (reach `BREATH_REACH_ROWS` 45 art rows for a sitting cat, scaled by the posture's `Clip.height`), so the breath's size and rhythm are as before: about 1 to 3 device pixels at the card, 1 to 5 at the full-screen room, one pixel at a time and never back within the hold. A breath that has not risen draws the picture in one piece exactly as before.
- **`SpritePlan`** (`presentation/SpritePlan.kt`): what `drawSpriteAt` draws, decided without a canvas: the picture (rig slot or sheet frame), its placement, the held transform and the bands. The app and the probe run the same plan, so the tests measure the real drawing. `RigPainter` now only keeps the slots' bitmaps.
- Nothing allocates per frame (the plan and bands are reused; `SpriteSteady.of` is a lookup after the first frame). No art file, animation, timing or tuning of the body changed.

## Results

Resting cat, the owner's case (ginger, `idle_sit`, 8 s at 120 Hz):

| scale | before: frames changed | before: changed with no cause | before: a-b-a px | after: frames changed | after: changed with no cause | after: what changed |
|---|---|---|---|---|---|---|
| card (5) | 527 of 959 | 497 (47 404 px) | 551 | 56 of 959 | 0 | 30 art frames (the blink and idle frames), 26 breath steps |
| room (9) | 681 of 959 | 650 (162 434 px) | 1 069 | 98 of 959 | 0 | 30 art frames, 68 breath steps |

Every resting row, every look and both scales now have **0 unexplained steps**: between the art's own frames and breath steps the frames are byte-identical. Every clip of the shipped art (all 44, for the four looks; the silver kitten has no depth rows in the fixtures) has 0 unexplained steps after the fix, where before each had 41 to 217 in 3 s. The moving rows and the house have 0 as well (the house cat from 2 824 unexplained steps, 2.1 million pixels, to 0).

What remains, and why it is not shimmer:

- **Breath steps change many pixels at once** (about 1 000 to 3 000 at the card): the whole upper body moves one device pixel, so every edge of the coat pattern moves with it. That is the motion the breath means, drawn as a clean whole-pixel step. If it reads as too busy on the phone, `BREATH_REACH_ROWS` (size) and `BREATH_CUT_SHARE` (how much of the cat lifts) are the dials.
- **a-b-a pixels after the fix** come only from two deliberate steps on consecutive frames (an art frame followed by a tail step on a footfall, or a breath step next to an art frame): measured in the run and walk rows and the house, where the tail swings on each stride. They are not a value hovering on an edge; hysteresis makes that impossible for every held value.
- **Rig steps while moving** (ears and tail following their springs) are intended secondary motion.
- **Nothing could not be made stable.** No clip is flagged.

## Screenshots

Every existing screenshot that shows a cat changed and was re-recorded (114 images; `LifeDirectorScreenshotTest_sessionSheets_past.png` and every image without a cat are unchanged). Each change is on the cat only: the breath is no longer a fractional scale of the whole cat (it was mid-breath in almost every capture) and a hovering turn or squash is drawn on its step. Pixels changed per image, cat area only: `CatLive*` 1 500 to 9 900 of 911 000 (the walking cat most, its 2.2 degree lean now 2 degrees); `CatRig*` 1 800 to 6 700; `CatHouse*` 3 800 to 6 500; `LiveDepthCard*` 6 200 to 8 400; `LiveDepthTab*` 900 to 1 400 of 461 000; `PetScreenshotTest_*` 33 to 860 of 360 000; `PetTouch*` 240 to 3 700; `SofaVisibility*` 1 900 to 5 300; `VolumeScreenshotTest_*` 1 800 to 2 800 of 442 368. The cats look the same at a glance; side by sides (old, new, changed pixels) were made for review.

## Regression tests

`SteadyCatTest`: a resting cat (`idle_sit`, `loaf`, `sleep`; a plain coat and a genetic coat with proportions) draws byte-identical frames on every step nothing meant to change, and stays still on most frames; the old drawing is still seen to shimmer by the probe (so the probe cannot go blind); the breath is a one-pixel step that never flicks back, even with noise on it; an ear spring hovering on a step's edge holds its step; `SteadyStep` rounding and hold; and a cat that has not breathed is drawn in one piece with the old geometry. `SpriteSteadinessReportTest` writes the tables below on demand.

## Needs the phone

Whether the resting cat now reads as calm on the card and in the Cat tab, and whether the whole-pixel breath reads as breathing rather than as a nudge (dials above). The probe models the canvas's nearest-neighbour sampling; the GPU's own edge rule for a pixel centre exactly on an edge can differ by a pixel, which only matters while something moves.

## Tables (before = Native API 94, after = Native API 99)

The full output of `SpriteSteadinessReportTest`, card scale 5 unless the s column says 9.

### Resting rows, 8 s at 120 Hz

| scene | look | s | frames | before: changed steps | before: px/step min/med/max | before: coat px off-art | before: unexplained steps (px) | before: a-b-a px | after: changed steps | after: px/step min/med/max | after: coat px off-art | after: unexplained steps (px) | after: a-b-a px | after: art/rig/breath/body steps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| idle_sit | ginger adult | 5 | 959 | 527 | 0/20/2271 | 38532 | 497 (47404) | 551 | 56 | 0/0/2825 | 35165 | 0 (0) | 0 | 30/0/26/0 |
| idle_sit_w | ginger adult | 5 | 959 | 494 | 0/10/2238 | 37540 | 474 (46459) | 516 | 47 | 0/0/2425 | 35690 | 0 (0) | 5 | 20/0/27/0 |
| loaf | ginger adult | 5 | 959 | 305 | 0/0/1975 | 18020 | 290 (23745) | 210 | 43 | 0/0/1955 | 33675 | 0 (0) | 60 | 15/0/28/0 |
| loaf_w | ginger adult | 5 | 959 | 267 | 0/0/2405 | 16205 | 255 (21460) | 155 | 40 | 0/0/2255 | 30930 | 0 (0) | 0 | 12/0/28/0 |
| sleep | ginger adult | 5 | 959 | 252 | 0/0/1055 | 17890 | 236 (23330) | 0 | 46 | 0/0/1310 | 29350 | 0 (0) | 0 | 15/0/31/0 |
| sleep_w | ginger adult | 5 | 959 | 216 | 0/0/1155 | 15270 | 203 (20195) | 0 | 43 | 0/0/1140 | 24560 | 0 (0) | 0 | 12/0/31/0 |
| idle_sit | tuxedo kitten st2 | 5 | 959 | 400 | 0/0/1160 | 19660 | 370 (25419) | 249 | 56 | 0/0/1705 | 21380 | 0 (0) | 0 | 30/0/26/0 |
| idle_sit_w | tuxedo kitten st2 | 5 | 959 | 378 | 0/0/1073 | 19356 | 358 (25248) | 210 | 47 | 0/0/1235 | 22540 | 0 (0) | 0 | 20/0/27/0 |
| loaf | tuxedo kitten st2 | 5 | 959 | 222 | 0/0/1110 | 9490 | 207 (12675) | 5 | 43 | 0/0/1100 | 17925 | 0 (0) | 10 | 15/0/28/0 |
| loaf_w | tuxedo kitten st2 | 5 | 959 | 210 | 0/0/1060 | 9335 | 198 (12635) | 10 | 40 | 0/0/1125 | 17170 | 0 (0) | 0 | 12/0/28/0 |
| sleep | tuxedo kitten st2 | 5 | 959 | 203 | 0/0/445 | 10425 | 187 (13885) | 35 | 46 | 0/0/755 | 15670 | 0 (0) | 0 | 15/0/31/0 |
| sleep_w | tuxedo kitten st2 | 5 | 959 | 201 | 0/0/750 | 10445 | 188 (13875) | 0 | 43 | 0/0/810 | 16740 | 0 (0) | 0 | 12/0/31/0 |
| idle_sit | calico (genetic) adult | 5 | 959 | 548 | 0/20/2288 | 38935 | 516 (48096) | 571 | 58 | 0/0/3025 | 36580 | 0 (0) | 10 | 30/2/26/0 |
| idle_sit_w | calico (genetic) adult | 5 | 959 | 524 | 0/5/2264 | 37683 | 502 (46935) | 563 | 49 | 0/0/2490 | 36445 | 0 (0) | 25 | 20/2/27/0 |
| loaf | calico (genetic) adult | 5 | 959 | 316 | 0/0/2250 | 19220 | 299 (24795) | 245 | 45 | 0/0/2240 | 35775 | 0 (0) | 95 | 15/2/28/0 |
| loaf_w | calico (genetic) adult | 5 | 959 | 269 | 0/0/2660 | 17160 | 255 (22475) | 155 | 42 | 0/0/2505 | 32690 | 0 (0) | 15 | 12/2/28/0 |
| sleep | calico (genetic) adult | 5 | 959 | 273 | 0/0/1275 | 18795 | 257 (24510) | 5 | 46 | 0/0/1370 | 30865 | 0 (0) | 0 | 15/0/31/0 |
| sleep_w | calico (genetic) adult | 5 | 959 | 227 | 0/0/1135 | 15890 | 214 (21015) | 5 | 43 | 0/0/1200 | 25850 | 0 (0) | 0 | 12/0/31/0 |
| idle_sit | silver (genetic) kitten st1 | 5 | 959 | 339 | 0/0/1070 | 16951 | 309 (22091) | 254 | 56 | 0/0/1200 | 16820 | 0 (0) | 0 | 30/0/26/0 |
| idle_sit_w | silver (genetic) kitten st1 | 5 | 959 | 328 | 0/0/920 | 16476 | 308 (21799) | 259 | 47 | 0/0/1200 | 19675 | 0 (0) | 0 | 20/0/27/0 |
| loaf | silver (genetic) kitten st1 | 5 | 959 | 194 | 0/0/1130 | 6930 | 179 (9805) | 0 | 43 | 0/0/1160 | 12360 | 0 (0) | 20 | 15/0/28/0 |
| loaf_w | silver (genetic) kitten st1 | 5 | 959 | 181 | 0/0/1310 | 6030 | 169 (9020) | 5 | 40 | 0/0/1275 | 11340 | 0 (0) | 0 | 12/0/28/0 |
| sleep | silver (genetic) kitten st1 | 5 | 959 | 183 | 0/0/585 | 8570 | 167 (11415) | 20 | 46 | 0/0/670 | 13705 | 0 (0) | 0 | 15/0/31/0 |
| sleep_w | silver (genetic) kitten st1 | 5 | 959 | 182 | 0/0/675 | 9115 | 169 (12060) | 0 | 43 | 0/0/725 | 15035 | 0 (0) | 0 | 12/0/31/0 |
| idle_sit | ginger adult | 9 | 959 | 681 | 0/117/7272 | 131877 | 650 (162434) | 1069 | 98 | 0/0/6849 | 165870 | 0 (0) | 90 | 30/0/68/0 |
| idle_sit_w | ginger adult | 9 | 959 | 656 | 0/108/6768 | 129468 | 635 (160381) | 785 | 74 | 0/0/5373 | 128547 | 0 (0) | 27 | 20/0/54/0 |
| loaf | ginger adult | 9 | 959 | 457 | 0/0/6696 | 65700 | 442 (85500) | 99 | 57 | 0/0/6327 | 90855 | 0 (0) | 0 | 15/0/42/0 |
| loaf_w | ginger adult | 9 | 959 | 412 | 0/0/7497 | 59112 | 400 (79092) | 126 | 54 | 0/0/7290 | 83493 | 0 (0) | 0 | 12/0/42/0 |
| sleep | ginger adult | 9 | 959 | 396 | 0/0/3699 | 57879 | 380 (75744) | 144 | 69 | 0/0/5211 | 91521 | 0 (0) | 90 | 15/0/54/0 |
| sleep_w | ginger adult | 9 | 959 | 351 | 0/0/3663 | 49851 | 338 (66204) | 54 | 65 | 0/0/5076 | 75024 | 0 (0) | 0 | 12/0/53/0 |
| idle_sit | tuxedo kitten st2 | 9 | 959 | 575 | 0/54/4124 | 67318 | 545 (85798) | 322 | 98 | 0/0/3933 | 100881 | 0 (0) | 9 | 30/0/68/0 |
| idle_sit_w | tuxedo kitten st2 | 9 | 959 | 547 | 0/36/3960 | 67702 | 527 (86412) | 328 | 74 | 0/0/2430 | 81162 | 0 (0) | 0 | 20/0/54/0 |
| loaf | tuxedo kitten st2 | 9 | 959 | 349 | 0/0/3339 | 36684 | 334 (46710) | 0 | 57 | 0/0/3510 | 48294 | 0 (0) | 0 | 15/0/42/0 |
| loaf_w | tuxedo kitten st2 | 9 | 959 | 327 | 0/0/3402 | 35865 | 315 (45648) | 9 | 54 | 0/0/3591 | 46377 | 0 (0) | 0 | 12/0/42/0 |
| sleep | tuxedo kitten st2 | 9 | 959 | 323 | 0/0/1629 | 32913 | 307 (44289) | 36 | 69 | 0/0/2457 | 48942 | 0 (0) | 36 | 15/0/54/0 |
| sleep_w | tuxedo kitten st2 | 9 | 959 | 324 | 0/0/2403 | 35100 | 311 (47232) | 36 | 65 | 0/0/3600 | 51156 | 0 (0) | 0 | 12/0/53/0 |
| idle_sit | calico (genetic) adult | 9 | 959 | 691 | 0/126/7794 | 134067 | 659 (165579) | 1208 | 100 | 0/0/7335 | 172449 | 0 (0) | 153 | 30/2/68/0 |
| idle_sit_w | calico (genetic) adult | 9 | 959 | 681 | 0/117/6790 | 130143 | 659 (162135) | 915 | 76 | 0/0/5553 | 131184 | 0 (0) | 63 | 20/2/54/0 |
| loaf | calico (genetic) adult | 9 | 959 | 463 | 0/0/7587 | 69165 | 446 (88407) | 126 | 59 | 0/0/7254 | 96714 | 0 (0) | 0 | 15/2/42/0 |
| loaf_w | calico (genetic) adult | 9 | 959 | 414 | 0/0/8298 | 61425 | 400 (81684) | 126 | 56 | 0/0/8100 | 88335 | 0 (0) | 0 | 12/2/42/0 |
| sleep | calico (genetic) adult | 9 | 959 | 425 | 0/0/4293 | 60723 | 409 (79587) | 135 | 69 | 0/0/5877 | 96273 | 0 (0) | 126 | 15/0/54/0 |
| sleep_w | calico (genetic) adult | 9 | 959 | 360 | 0/0/3582 | 51876 | 347 (68922) | 72 | 65 | 0/0/5121 | 78993 | 0 (0) | 0 | 12/0/53/0 |
| idle_sit | silver (genetic) kitten st1 | 9 | 959 | 483 | 0/18/3553 | 56127 | 453 (71149) | 389 | 98 | 0/0/3321 | 79911 | 0 (0) | 0 | 30/0/68/0 |
| idle_sit_w | silver (genetic) kitten st1 | 9 | 959 | 480 | 0/6/3399 | 56454 | 460 (72273) | 313 | 74 | 0/0/2466 | 70956 | 0 (0) | 9 | 20/0/54/0 |
| loaf | silver (genetic) kitten st1 | 9 | 959 | 288 | 0/0/3672 | 27792 | 273 (36252) | 45 | 57 | 0/0/3726 | 33291 | 0 (0) | 0 | 15/0/42/0 |
| loaf_w | silver (genetic) kitten st1 | 9 | 959 | 284 | 0/0/4230 | 26811 | 272 (35658) | 18 | 54 | 0/0/4131 | 30618 | 0 (0) | 0 | 12/0/42/0 |
| sleep | silver (genetic) kitten st1 | 9 | 959 | 288 | 0/0/1836 | 27396 | 272 (36954) | 36 | 69 | 0/0/1944 | 42750 | 0 (0) | 9 | 15/0/54/0 |
| sleep_w | silver (genetic) kitten st1 | 9 | 959 | 289 | 0/0/2061 | 30744 | 276 (41058) | 45 | 65 | 0/0/3150 | 45954 | 0 (0) | 0 | 12/0/53/0 |

### Every clip acted in place, 3 s, card scale

| scene | look | s | frames | before: changed steps | before: px/step min/med/max | before: coat px off-art | before: unexplained steps (px) | before: a-b-a px | after: changed steps | after: px/step min/med/max | after: coat px off-art | after: unexplained steps (px) | after: a-b-a px | after: art/rig/breath/body steps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| idle_sit | ginger adult | 5 | 359 | 186 | 0/10/2110 | 11800 | 176 (14445) | 145 | 19 | 0/0/2825 | 12200 | 0 (0) | 0 | 10/0/9/0 |
| walk | ginger adult | 5 | 359 | 189 | 0/10/11065 | 13960 | 154 (18170) | 555 | 45 | 0/0/11035 | 19320 | 0 (0) | 785 | 35/0/10/0 |
| run | ginger adult | 5 | 359 | 192 | 0/40/18575 | 14725 | 144 (19010) | 1500 | 56 | 0/0/18700 | 17710 | 0 (0) | 1255 | 48/0/8/0 |
| sleep | ginger adult | 5 | 359 | 96 | 0/0/1050 | 7045 | 91 (9090) | 0 | 16 | 0/0/1295 | 10250 | 0 (0) | 0 | 5/0/11/0 |
| loaf | ginger adult | 5 | 359 | 111 | 0/0/1955 | 6640 | 106 (8760) | 125 | 15 | 0/0/1955 | 12165 | 0 (0) | 0 | 5/0/10/0 |
| groom | ginger adult | 5 | 359 | 174 | 0/0/8135 | 11275 | 159 (13710) | 340 | 25 | 0/0/8080 | 13805 | 0 (0) | 380 | 15/0/10/0 |
| eat | ginger adult | 5 | 359 | 183 | 0/5/19670 | 13855 | 168 (18445) | 385 | 24 | 0/0/19590 | 18180 | 0 (0) | 0 | 15/0/9/0 |
| play | ginger adult | 5 | 359 | 197 | 0/25/8040 | 11605 | 170 (14210) | 315 | 37 | 0/0/7950 | 14225 | 0 (0) | 80 | 27/0/10/0 |
| stretch | ginger adult | 5 | 359 | 170 | 0/0/345 | 14020 | 170 (18150) | 80 | 10 | 0/0/2225 | 17830 | 0 (0) | 0 | 0/0/10/0 |
| lookup | ginger adult | 5 | 359 | 195 | 0/25/605 | 12635 | 186 (15435) | 90 | 24 | 0/0/1680 | 20370 | 0 (0) | 0 | 9/0/15/0 |
| slowblink | ginger adult | 5 | 359 | 182 | 0/10/300 | 12235 | 182 (14945) | 90 | 10 | 0/0/1725 | 13920 | 0 (0) | 0 | 0/0/10/0 |
| purr | ginger adult | 5 | 359 | 179 | 0/0/2100 | 11440 | 168 (13855) | 125 | 20 | 0/0/3415 | 12845 | 0 (0) | 0 | 11/0/9/0 |
| rub | ginger adult | 5 | 359 | 182 | 0/15/10335 | 15620 | 167 (20160) | 385 | 24 | 0/0/11325 | 18255 | 0 (0) | 75 | 15/0/9/0 |
| knead | ginger adult | 5 | 359 | 115 | 0/0/2605 | 6525 | 104 (8565) | 140 | 21 | 0/0/2615 | 11335 | 0 (0) | 0 | 11/0/10/0 |
| meow | ginger adult | 5 | 359 | 197 | 0/25/4080 | 11985 | 180 (14705) | 315 | 27 | 0/0/3975 | 13925 | 0 (0) | 105 | 17/0/10/0 |
| leap | ginger adult | 5 | 359 | 182 | 0/10/315 | 16285 | 182 (21295) | 135 | 15 | 0/0/2385 | 27885 | 0 (0) | 0 | 0/0/15/0 |
| idle_sit_w | ginger adult | 5 | 359 | 175 | 0/0/990 | 11695 | 167 (14325) | 130 | 17 | 0/0/2425 | 11920 | 0 (0) | 0 | 8/0/9/0 |
| walk_w | ginger adult | 5 | 359 | 153 | 0/0/4580 | 11710 | 130 (15300) | 125 | 33 | 0/0/4565 | 17085 | 0 (0) | 10 | 23/0/10/0 |
| loaf_w | ginger adult | 5 | 359 | 97 | 0/0/2235 | 5850 | 93 (7765) | 50 | 14 | 0/0/2255 | 11125 | 0 (0) | 0 | 4/0/10/0 |
| sleep_w | ginger adult | 5 | 359 | 82 | 0/0/1115 | 6110 | 78 (8045) | 0 | 15 | 0/0/1140 | 8560 | 0 (0) | 0 | 4/0/11/0 |
| crouch | ginger adult | 5 | 359 | 188 | 0/10/185 | 10620 | 188 (13590) | 70 | 10 | 0/0/1885 | 14940 | 0 (0) | 0 | 0/0/10/0 |
| scratch | ginger adult | 5 | 359 | 181 | 0/10/4095 | 13190 | 160 (16160) | 135 | 34 | 0/0/5530 | 20020 | 0 (0) | 280 | 21/0/13/0 |
| scratch_in | ginger adult | 5 | 359 | 182 | 0/10/275 | 14560 | 182 (18005) | 110 | 10 | 0/0/2075 | 16610 | 0 (0) | 0 | 0/0/10/0 |
| scratch_out | ginger adult | 5 | 359 | 182 | 0/10/275 | 14255 | 182 (17680) | 110 | 10 | 0/0/1990 | 15750 | 0 (0) | 0 | 0/0/10/0 |
| roll | ginger adult | 5 | 359 | 106 | 0/0/9000 | 7355 | 83 (9300) | 295 | 27 | 0/0/9960 | 7115 | 0 (0) | 185 | 23/0/4/0 |
| kick | ginger adult | 5 | 359 | 104 | 0/0/15065 | 4910 | 69 (6245) | 365 | 44 | 0/0/15195 | 9745 | 0 (0) | 140 | 35/0/9/0 |
| bat | ginger adult | 5 | 359 | 192 | 0/15/5400 | 10020 | 165 (12715) | 235 | 37 | 0/0/5320 | 11550 | 0 (0) | 560 | 27/0/10/0 |
| swat | ginger adult | 5 | 359 | 101 | 0/0/17135 | 5190 | 74 (6640) | 50 | 37 | 0/0/17285 | 4760 | 0 (0) | 205 | 27/0/10/0 |
| walk_front | ginger adult | 5 | 359 | 179 | 0/0/16915 | 10770 | 149 (12605) | 520 | 40 | 0/0/16885 | 14050 | 0 (0) | 315 | 30/0/10/0 |
| walk_back | ginger adult | 5 | 359 | 179 | 0/0/16325 | 10685 | 149 (12895) | 585 | 40 | 0/0/16220 | 13450 | 0 (0) | 545 | 30/0/10/0 |
| sniff | ginger adult | 5 | 359 | 173 | 0/0/6900 | 9825 | 144 (11590) | 1235 | 39 | 0/0/6825 | 13970 | 0 (0) | 1630 | 29/0/10/0 |
| bonk | ginger adult | 5 | 359 | 147 | 0/0/230 | 9255 | 147 (11230) | 75 | 10 | 0/0/1455 | 12000 | 0 (0) | 0 | 0/0/10/0 |
| jump_up | ginger adult | 5 | 359 | 190 | 0/10/370 | 17275 | 190 (22935) | 130 | 10 | 0/0/2985 | 22540 | 0 (0) | 0 | 0/0/10/0 |
| jump_air | ginger adult | 5 | 359 | 190 | 0/15/315 | 13765 | 190 (18335) | 110 | 15 | 0/0/1955 | 22380 | 0 (0) | 0 | 0/0/15/0 |
| jump_land | ginger adult | 5 | 359 | 163 | 0/0/320 | 15215 | 163 (19935) | 170 | 10 | 0/0/2690 | 21340 | 0 (0) | 0 | 0/0/10/0 |
| turn | ginger adult | 5 | 359 | 161 | 0/0/290 | 13750 | 161 (17585) | 120 | 10 | 0/0/2375 | 18760 | 0 (0) | 0 | 0/0/10/0 |
| pickup | ginger adult | 5 | 359 | 182 | 0/5/315 | 14540 | 182 (19570) | 115 | 10 | 0/0/2680 | 21030 | 0 (0) | 0 | 0/0/10/0 |
| carry | ginger adult | 5 | 359 | 192 | 0/30/12080 | 14110 | 157 (18545) | 600 | 45 | 0/0/12035 | 18710 | 0 (0) | 910 | 35/0/10/0 |
| toss | ginger adult | 5 | 359 | 170 | 0/0/340 | 15320 | 170 (20290) | 110 | 10 | 0/0/2395 | 18720 | 0 (0) | 0 | 0/0/10/0 |
| yawn | ginger adult | 5 | 359 | 182 | 0/10/300 | 12530 | 182 (15240) | 95 | 10 | 0/0/1750 | 14170 | 0 (0) | 0 | 0/0/10/0 |
| shake | ginger adult | 5 | 359 | 190 | 0/15/270 | 11440 | 190 (14205) | 110 | 10 | 0/0/1615 | 12820 | 0 (0) | 0 | 0/0/10/0 |
| bump | ginger adult | 5 | 359 | 190 | 0/15/290 | 11825 | 190 (14595) | 90 | 10 | 0/0/1675 | 13420 | 0 (0) | 0 | 0/0/10/0 |
| groom_other | ginger adult | 5 | 359 | 182 | 0/5/11385 | 10850 | 167 (13425) | 235 | 25 | 0/0/11225 | 13620 | 0 (0) | 600 | 15/0/10/0 |
| nose_touch | ginger adult | 5 | 359 | 170 | 0/0/320 | 15245 | 170 (20395) | 135 | 10 | 0/0/2545 | 19840 | 0 (0) | 0 | 0/0/10/0 |
| idle_sit | tuxedo kitten st2 | 5 | 359 | 139 | 0/0/1150 | 6005 | 129 (7750) | 65 | 19 | 0/0/1705 | 7395 | 0 (0) | 0 | 10/0/9/0 |
| walk | tuxedo kitten st2 | 5 | 359 | 130 | 0/0/4965 | 6200 | 95 (8155) | 180 | 45 | 0/0/4975 | 7730 | 0 (0) | 275 | 35/0/10/0 |
| run | tuxedo kitten st2 | 5 | 359 | 146 | 0/0/9675 | 6450 | 98 (8435) | 390 | 56 | 0/0/9640 | 7905 | 0 (0) | 460 | 48/0/8/0 |
| sleep | tuxedo kitten st2 | 5 | 359 | 76 | 0/0/445 | 3845 | 71 (5195) | 0 | 16 | 0/0/755 | 5480 | 0 (0) | 0 | 5/0/11/0 |
| loaf | tuxedo kitten st2 | 5 | 359 | 80 | 0/0/1075 | 3415 | 75 (4590) | 0 | 15 | 0/0/970 | 6505 | 0 (0) | 0 | 5/0/10/0 |
| groom | tuxedo kitten st2 | 5 | 359 | 130 | 0/0/5900 | 5470 | 115 (7110) | 155 | 25 | 0/0/5900 | 7750 | 0 (0) | 190 | 15/0/10/0 |
| eat | tuxedo kitten st2 | 5 | 359 | 105 | 0/0/13005 | 4990 | 90 (6760) | 130 | 24 | 0/0/12905 | 6500 | 0 (0) | 0 | 15/0/9/0 |
| play | tuxedo kitten st2 | 5 | 359 | 147 | 0/0/4820 | 5740 | 120 (7435) | 50 | 37 | 0/0/4800 | 8485 | 0 (0) | 60 | 27/0/10/0 |
| stretch | tuxedo kitten st2 | 5 | 359 | 101 | 0/0/175 | 6705 | 101 (8865) | 45 | 10 | 0/0/1045 | 7350 | 0 (0) | 0 | 0/0/10/0 |
| lookup | tuxedo kitten st2 | 5 | 359 | 143 | 0/0/500 | 6480 | 134 (8370) | 15 | 24 | 0/0/1095 | 13065 | 0 (0) | 0 | 9/0/15/0 |
| slowblink | tuxedo kitten st2 | 5 | 359 | 124 | 0/0/170 | 5865 | 124 (7635) | 35 | 10 | 0/0/955 | 7540 | 0 (0) | 0 | 0/0/10/0 |
| purr | tuxedo kitten st2 | 5 | 359 | 133 | 0/0/1535 | 5760 | 122 (7545) | 95 | 20 | 0/0/2440 | 8025 | 0 (0) | 0 | 11/0/9/0 |
| rub | tuxedo kitten st2 | 5 | 359 | 112 | 0/0/6610 | 6290 | 97 (8155) | 420 | 24 | 0/0/6985 | 6890 | 0 (0) | 40 | 15/0/9/0 |
| knead | tuxedo kitten st2 | 5 | 359 | 84 | 0/0/850 | 3335 | 73 (4500) | 5 | 21 | 0/0/835 | 5765 | 0 (0) | 0 | 11/0/10/0 |
| meow | tuxedo kitten st2 | 5 | 359 | 136 | 0/0/2920 | 5670 | 119 (7320) | 85 | 27 | 0/0/2830 | 7905 | 0 (0) | 80 | 17/0/10/0 |
| leap | tuxedo kitten st2 | 5 | 359 | 111 | 0/0/180 | 7370 | 111 (9810) | 90 | 15 | 0/0/1085 | 11415 | 0 (0) | 0 | 0/0/15/0 |
| idle_sit_w | tuxedo kitten st2 | 5 | 359 | 132 | 0/0/365 | 5975 | 124 (7785) | 55 | 17 | 0/0/1235 | 7520 | 0 (0) | 0 | 8/0/9/0 |
| walk_w | tuxedo kitten st2 | 5 | 359 | 112 | 0/0/2030 | 5615 | 89 (7415) | 30 | 33 | 0/0/2025 | 6260 | 0 (0) | 0 | 23/0/10/0 |
| loaf_w | tuxedo kitten st2 | 5 | 359 | 76 | 0/0/1060 | 3425 | 72 (4610) | 0 | 14 | 0/0/1125 | 6275 | 0 (0) | 0 | 4/0/10/0 |
| sleep_w | tuxedo kitten st2 | 5 | 359 | 76 | 0/0/745 | 4115 | 72 (5465) | 0 | 15 | 0/0/810 | 5830 | 0 (0) | 0 | 4/0/11/0 |
| crouch | tuxedo kitten st2 | 5 | 359 | 87 | 0/0/110 | 4045 | 87 (5460) | 15 | 10 | 0/0/835 | 5820 | 0 (0) | 0 | 0/0/10/0 |
| scratch | tuxedo kitten st2 | 5 | 359 | 146 | 0/0/1175 | 5920 | 125 (7595) | 60 | 34 | 0/0/1330 | 10175 | 0 (0) | 0 | 21/0/13/0 |
| scratch_in | tuxedo kitten st2 | 5 | 359 | 101 | 0/0/105 | 3450 | 101 (4685) | 25 | 10 | 0/0/615 | 4530 | 0 (0) | 0 | 0/0/10/0 |
| scratch_out | tuxedo kitten st2 | 5 | 359 | 66 | 0/0/65 | 2480 | 66 (3140) | 0 | 10 | 0/0/300 | 1790 | 0 (0) | 0 | 0/0/10/0 |
| roll | tuxedo kitten st2 | 5 | 359 | 69 | 0/0/5420 | 3210 | 46 (4520) | 185 | 27 | 0/0/5665 | 3175 | 0 (0) | 110 | 23/0/4/0 |
| kick | tuxedo kitten st2 | 5 | 359 | 89 | 0/0/2945 | 2195 | 54 (3135) | 60 | 44 | 0/0/3170 | 3960 | 0 (0) | 60 | 35/0/9/0 |
| bat | tuxedo kitten st2 | 5 | 359 | 129 | 0/0/2560 | 3905 | 102 (5320) | 290 | 37 | 0/0/2490 | 5720 | 0 (0) | 360 | 27/0/10/0 |
| swat | tuxedo kitten st2 | 5 | 359 | 86 | 0/0/11390 | 2415 | 59 (3485) | 40 | 37 | 0/0/11430 | 2060 | 0 (0) | 85 | 27/0/10/0 |
| walk_front | tuxedo kitten st2 | 5 | 359 | 150 | 0/0/12345 | 6630 | 120 (8180) | 460 | 40 | 0/0/12380 | 8790 | 0 (0) | 295 | 30/0/10/0 |
| walk_back | tuxedo kitten st2 | 5 | 359 | 147 | 0/0/12685 | 6965 | 120 (8930) | 500 | 35 | 0/0/12800 | 8640 | 0 (0) | 300 | 25/0/10/0 |
| sniff | tuxedo kitten st2 | 5 | 359 | 144 | 0/0/6000 | 6080 | 115 (7580) | 505 | 39 | 0/0/6085 | 9040 | 0 (0) | 1150 | 29/0/10/0 |
| bonk | tuxedo kitten st2 | 5 | 359 | 111 | 0/0/140 | 5410 | 111 (6845) | 70 | 10 | 0/0/750 | 5610 | 0 (0) | 0 | 0/0/10/0 |
| jump_up | tuxedo kitten st2 | 5 | 359 | 126 | 0/0/215 | 8190 | 126 (10425) | 70 | 10 | 0/0/1470 | 11490 | 0 (0) | 0 | 0/0/10/0 |
| jump_air | tuxedo kitten st2 | 5 | 359 | 101 | 0/0/135 | 5010 | 101 (6715) | 35 | 15 | 0/0/750 | 7275 | 0 (0) | 0 | 0/0/15/0 |
| jump_land | tuxedo kitten st2 | 5 | 359 | 101 | 0/0/170 | 6620 | 101 (8545) | 55 | 10 | 0/0/1335 | 9990 | 0 (0) | 0 | 0/0/10/0 |
| turn | tuxedo kitten st2 | 5 | 359 | 101 | 0/0/170 | 6125 | 101 (8000) | 55 | 10 | 0/0/1150 | 8480 | 0 (0) | 0 | 0/0/10/0 |
| pickup | tuxedo kitten st2 | 5 | 359 | 94 | 0/0/165 | 5985 | 94 (7915) | 55 | 10 | 0/0/1210 | 8700 | 0 (0) | 0 | 0/0/10/0 |
| carry | tuxedo kitten st2 | 5 | 359 | 146 | 0/0/5965 | 6480 | 111 (8680) | 185 | 45 | 0/0/6005 | 8690 | 0 (0) | 340 | 35/0/10/0 |
| toss | tuxedo kitten st2 | 5 | 359 | 111 | 0/0/165 | 6385 | 111 (8685) | 85 | 10 | 0/0/1040 | 7410 | 0 (0) | 0 | 0/0/10/0 |
| yawn | tuxedo kitten st2 | 5 | 359 | 124 | 0/0/170 | 5840 | 124 (7610) | 35 | 10 | 0/0/950 | 7470 | 0 (0) | 0 | 0/0/10/0 |
| shake | tuxedo kitten st2 | 5 | 359 | 132 | 0/0/190 | 5855 | 132 (7545) | 20 | 10 | 0/0/930 | 7380 | 0 (0) | 0 | 0/0/10/0 |
| bump | tuxedo kitten st2 | 5 | 359 | 126 | 0/0/170 | 5620 | 126 (7415) | 40 | 10 | 0/0/920 | 7150 | 0 (0) | 0 | 0/0/10/0 |
| groom_other | tuxedo kitten st2 | 5 | 359 | 134 | 0/0/8605 | 5090 | 119 (6850) | 110 | 25 | 0/0/8670 | 6950 | 0 (0) | 335 | 15/0/10/0 |
| nose_touch | tuxedo kitten st2 | 5 | 359 | 107 | 0/0/150 | 6075 | 107 (8345) | 75 | 10 | 0/0/1070 | 7650 | 0 (0) | 0 | 0/0/10/0 |
| idle_sit | calico (genetic) adult | 5 | 359 | 194 | 0/15/2275 | 12095 | 184 (14830) | 165 | 19 | 0/0/3025 | 12760 | 0 (0) | 0 | 10/0/9/0 |
| walk | calico (genetic) adult | 5 | 359 | 228 | 0/35/13995 | 15280 | 193 (20490) | 610 | 45 | 0/0/13900 | 20525 | 0 (0) | 980 | 35/0/10/0 |
| run | calico (genetic) adult | 5 | 359 | 225 | 0/65/21020 | 15815 | 177 (21485) | 1720 | 56 | 0/0/21160 | 18195 | 0 (0) | 1250 | 48/0/8/0 |
| sleep | calico (genetic) adult | 5 | 359 | 105 | 0/0/1235 | 7410 | 100 (9570) | 5 | 16 | 0/0/1370 | 10810 | 0 (0) | 0 | 5/0/11/0 |
| loaf | calico (genetic) adult | 5 | 359 | 115 | 0/0/2240 | 6935 | 110 (9150) | 150 | 15 | 0/0/2240 | 12755 | 0 (0) | 0 | 5/0/10/0 |
| groom | calico (genetic) adult | 5 | 359 | 183 | 0/5/8840 | 11655 | 168 (14210) | 350 | 25 | 0/0/8790 | 14500 | 0 (0) | 425 | 15/0/10/0 |
| eat | calico (genetic) adult | 5 | 359 | 225 | 0/15/22605 | 15235 | 210 (20775) | 325 | 24 | 0/0/22515 | 19995 | 0 (0) | 0 | 15/0/9/0 |
| play | calico (genetic) adult | 5 | 359 | 197 | 0/20/8560 | 11660 | 170 (14365) | 335 | 37 | 0/0/8470 | 14515 | 0 (0) | 65 | 27/0/10/0 |
| stretch | calico (genetic) adult | 5 | 359 | 217 | 0/15/350 | 15080 | 217 (20005) | 105 | 10 | 0/0/2365 | 18590 | 0 (0) | 0 | 0/0/10/0 |
| lookup | calico (genetic) adult | 5 | 359 | 198 | 0/25/570 | 12960 | 189 (15870) | 100 | 24 | 0/0/1750 | 21240 | 0 (0) | 0 | 9/0/15/0 |
| slowblink | calico (genetic) adult | 5 | 359 | 190 | 0/10/335 | 12750 | 190 (15660) | 90 | 10 | 0/0/1830 | 14790 | 0 (0) | 0 | 0/0/10/0 |
| purr | calico (genetic) adult | 5 | 359 | 189 | 0/5/1935 | 11775 | 178 (14305) | 120 | 20 | 0/0/3315 | 13345 | 0 (0) | 0 | 11/0/9/0 |
| rub | calico (genetic) adult | 5 | 359 | 226 | 0/25/11080 | 16640 | 211 (22245) | 380 | 24 | 0/0/12235 | 19250 | 0 (0) | 75 | 15/0/9/0 |
| knead | calico (genetic) adult | 5 | 359 | 119 | 0/0/2780 | 6800 | 108 (8925) | 170 | 21 | 0/0/2795 | 11925 | 0 (0) | 0 | 11/0/10/0 |
| meow | calico (genetic) adult | 5 | 359 | 197 | 0/15/4365 | 12205 | 180 (15070) | 370 | 27 | 0/0/4275 | 14340 | 0 (0) | 125 | 17/0/10/0 |
| leap | calico (genetic) adult | 5 | 359 | 217 | 0/20/435 | 17770 | 217 (23385) | 115 | 15 | 0/0/2525 | 29535 | 0 (0) | 0 | 0/0/15/0 |
| idle_sit_w | calico (genetic) adult | 5 | 359 | 186 | 0/5/1010 | 11850 | 178 (14575) | 135 | 17 | 0/0/2490 | 12155 | 0 (0) | 0 | 8/0/9/0 |
| walk_w | calico (genetic) adult | 5 | 359 | 164 | 0/0/4755 | 12275 | 141 (16260) | 175 | 33 | 0/0/4815 | 18580 | 0 (0) | 10 | 23/0/10/0 |
| loaf_w | calico (genetic) adult | 5 | 359 | 97 | 0/0/2485 | 6145 | 93 (8135) | 50 | 14 | 0/0/2505 | 11720 | 0 (0) | 0 | 4/0/10/0 |
| sleep_w | calico (genetic) adult | 5 | 359 | 86 | 0/0/1095 | 6320 | 82 (8335) | 0 | 15 | 0/0/1195 | 9015 | 0 (0) | 0 | 4/0/11/0 |
| crouch | calico (genetic) adult | 5 | 359 | 188 | 0/10/200 | 10660 | 188 (13765) | 80 | 10 | 0/0/1915 | 15000 | 0 (0) | 0 | 0/0/10/0 |
| scratch | calico (genetic) adult | 5 | 359 | 192 | 0/15/4090 | 13410 | 171 (16470) | 145 | 34 | 0/0/5585 | 20765 | 0 (0) | 240 | 21/0/13/0 |
| scratch_in | calico (genetic) adult | 5 | 359 | 190 | 0/15/275 | 14865 | 190 (18410) | 105 | 10 | 0/0/2140 | 17180 | 0 (0) | 0 | 0/0/10/0 |
| scratch_out | calico (genetic) adult | 5 | 359 | 190 | 0/15/275 | 14565 | 190 (18090) | 110 | 10 | 0/0/2060 | 16330 | 0 (0) | 0 | 0/0/10/0 |
| roll | calico (genetic) adult | 5 | 359 | 106 | 0/0/9025 | 7385 | 83 (9375) | 265 | 27 | 0/0/10005 | 7170 | 0 (0) | 175 | 23/0/4/0 |
| kick | calico (genetic) adult | 5 | 359 | 110 | 0/0/15435 | 5305 | 75 (6740) | 360 | 44 | 0/0/15610 | 10420 | 0 (0) | 140 | 35/0/9/0 |
| bat | calico (genetic) adult | 5 | 359 | 193 | 0/20/5080 | 8700 | 166 (11400) | 245 | 37 | 0/0/5020 | 9740 | 0 (0) | 540 | 27/0/10/0 |
| swat | calico (genetic) adult | 5 | 359 | 105 | 0/0/16985 | 5505 | 78 (6990) | 75 | 37 | 0/0/17125 | 5195 | 0 (0) | 380 | 27/0/10/0 |
| walk_front | calico (genetic) adult | 5 | 359 | 185 | 0/5/17500 | 11640 | 155 (13605) | 525 | 40 | 0/0/17440 | 14765 | 0 (0) | 330 | 30/0/10/0 |
| walk_back | calico (genetic) adult | 5 | 359 | 185 | 0/5/16585 | 11730 | 155 (14065) | 615 | 40 | 0/0/16490 | 14595 | 0 (0) | 555 | 30/0/10/0 |
| sniff | calico (genetic) adult | 5 | 359 | 177 | 0/0/7150 | 10660 | 148 (12505) | 1255 | 39 | 0/0/7075 | 14715 | 0 (0) | 1640 | 29/0/10/0 |
| bonk | calico (genetic) adult | 5 | 359 | 152 | 0/0/255 | 10000 | 152 (12060) | 80 | 10 | 0/0/1505 | 12460 | 0 (0) | 0 | 0/0/10/0 |
| jump_up | calico (genetic) adult | 5 | 359 | 190 | 0/10/385 | 17305 | 190 (22965) | 125 | 10 | 0/0/2970 | 22390 | 0 (0) | 0 | 0/0/10/0 |
| jump_air | calico (genetic) adult | 5 | 359 | 190 | 0/15/315 | 13845 | 190 (18415) | 110 | 15 | 0/0/1980 | 22725 | 0 (0) | 0 | 0/0/15/0 |
| jump_land | calico (genetic) adult | 5 | 359 | 163 | 0/0/320 | 15140 | 163 (19860) | 170 | 10 | 0/0/2660 | 21040 | 0 (0) | 0 | 0/0/10/0 |
| turn | calico (genetic) adult | 5 | 359 | 161 | 0/0/300 | 13730 | 161 (17565) | 120 | 10 | 0/0/2360 | 18610 | 0 (0) | 0 | 0/0/10/0 |
| pickup | calico (genetic) adult | 5 | 359 | 182 | 0/5/335 | 14580 | 182 (19610) | 110 | 10 | 0/0/2670 | 20930 | 0 (0) | 0 | 0/0/10/0 |
| carry | calico (genetic) adult | 5 | 359 | 192 | 0/30/11955 | 14250 | 157 (18685) | 580 | 45 | 0/0/11910 | 18785 | 0 (0) | 840 | 35/0/10/0 |
| toss | calico (genetic) adult | 5 | 359 | 170 | 0/0/340 | 15230 | 170 (20200) | 115 | 10 | 0/0/2365 | 18440 | 0 (0) | 0 | 0/0/10/0 |
| yawn | calico (genetic) adult | 5 | 359 | 182 | 0/10/315 | 12745 | 182 (15455) | 95 | 10 | 0/0/1805 | 14740 | 0 (0) | 0 | 0/0/10/0 |
| shake | calico (genetic) adult | 5 | 359 | 190 | 0/10/270 | 11800 | 190 (14565) | 110 | 10 | 0/0/1675 | 13440 | 0 (0) | 0 | 0/0/10/0 |
| bump | calico (genetic) adult | 5 | 359 | 190 | 0/10/300 | 11980 | 190 (14750) | 95 | 10 | 0/0/1725 | 13940 | 0 (0) | 0 | 0/0/10/0 |
| groom_other | calico (genetic) adult | 5 | 359 | 181 | 0/5/11610 | 11055 | 166 (13630) | 255 | 25 | 0/0/11450 | 14060 | 0 (0) | 635 | 15/0/10/0 |
| nose_touch | calico (genetic) adult | 5 | 359 | 170 | 0/0/325 | 15360 | 170 (20510) | 140 | 10 | 0/0/2530 | 19710 | 0 (0) | 0 | 0/0/10/0 |
| idle_sit | silver (genetic) kitten st1 | 5 | 359 | 118 | 0/0/875 | 5205 | 108 (6840) | 75 | 19 | 0/0/1200 | 5895 | 0 (0) | 0 | 10/0/9/0 |
| walk | silver (genetic) kitten st1 | 5 | 359 | 118 | 0/0/4015 | 5330 | 83 (6825) | 85 | 45 | 0/0/4080 | 6060 | 0 (0) | 175 | 35/0/10/0 |
| run | silver (genetic) kitten st1 | 5 | 359 | 137 | 0/0/7165 | 5255 | 89 (6955) | 260 | 56 | 0/0/7130 | 5915 | 0 (0) | 235 | 48/0/8/0 |
| sleep | silver (genetic) kitten st1 | 5 | 359 | 69 | 0/0/585 | 3200 | 64 (4315) | 0 | 16 | 0/0/670 | 4800 | 0 (0) | 0 | 5/0/11/0 |
| loaf | silver (genetic) kitten st1 | 5 | 359 | 70 | 0/0/1130 | 2520 | 65 (3580) | 0 | 15 | 0/0/1060 | 4525 | 0 (0) | 0 | 5/0/10/0 |
| groom | silver (genetic) kitten st1 | 5 | 359 | 120 | 0/0/5520 | 4650 | 105 (6045) | 175 | 25 | 0/0/5585 | 6355 | 0 (0) | 150 | 15/0/10/0 |
| eat | silver (genetic) kitten st1 | 5 | 359 | 85 | 0/0/11825 | 4570 | 70 (5950) | 25 | 24 | 0/0/11825 | 5455 | 0 (0) | 0 | 15/0/9/0 |
| play | silver (genetic) kitten st1 | 5 | 359 | 136 | 0/0/5195 | 5165 | 109 (6630) | 115 | 37 | 0/0/5260 | 7835 | 0 (0) | 70 | 27/0/10/0 |
| stretch | silver (genetic) kitten st1 | 5 | 359 | 94 | 0/0/185 | 5935 | 94 (7525) | 40 | 10 | 0/0/890 | 6270 | 0 (0) | 0 | 0/0/10/0 |
| lookup | silver (genetic) kitten st1 | 5 | 359 | 121 | 0/0/260 | 5385 | 112 (7085) | 75 | 24 | 0/0/825 | 9435 | 0 (0) | 0 | 9/0/15/0 |
| slowblink | silver (genetic) kitten st1 | 5 | 359 | 111 | 0/0/135 | 4980 | 111 (6705) | 60 | 10 | 0/0/795 | 6020 | 0 (0) | 0 | 0/0/10/0 |
| purr | silver (genetic) kitten st1 | 5 | 359 | 119 | 0/0/970 | 4910 | 108 (6525) | 90 | 20 | 0/0/1700 | 7150 | 0 (0) | 0 | 11/0/9/0 |
| rub | silver (genetic) kitten st1 | 5 | 359 | 100 | 0/0/6060 | 5415 | 85 (6780) | 230 | 24 | 0/0/6265 | 5470 | 0 (0) | 35 | 15/0/9/0 |
| knead | silver (genetic) kitten st1 | 5 | 359 | 74 | 0/0/755 | 2360 | 63 (3370) | 0 | 21 | 0/0/745 | 3690 | 0 (0) | 0 | 11/0/10/0 |
| meow | silver (genetic) kitten st1 | 5 | 359 | 125 | 0/0/3050 | 5135 | 108 (6750) | 235 | 27 | 0/0/3080 | 6665 | 0 (0) | 65 | 17/0/10/0 |
| leap | silver (genetic) kitten st1 | 5 | 359 | 101 | 0/0/170 | 6130 | 101 (7950) | 35 | 15 | 0/0/820 | 8415 | 0 (0) | 0 | 0/0/15/0 |
| idle_sit_w | silver (genetic) kitten st1 | 5 | 359 | 113 | 0/0/370 | 4995 | 105 (6700) | 80 | 17 | 0/0/1200 | 6605 | 0 (0) | 0 | 8/0/9/0 |
| walk_w | silver (genetic) kitten st1 | 5 | 359 | 100 | 0/0/1775 | 4845 | 77 (6080) | 10 | 33 | 0/0/1775 | 4610 | 0 (0) | 0 | 23/0/10/0 |
| loaf_w | silver (genetic) kitten st1 | 5 | 359 | 65 | 0/0/1310 | 2210 | 61 (3325) | 0 | 14 | 0/0/1275 | 4125 | 0 (0) | 0 | 4/0/10/0 |
| sleep_w | silver (genetic) kitten st1 | 5 | 359 | 70 | 0/0/635 | 3525 | 66 (4705) | 0 | 15 | 0/0/720 | 5235 | 0 (0) | 0 | 4/0/11/0 |
| crouch | silver (genetic) kitten st1 | 5 | 359 | 76 | 0/0/110 | 3645 | 76 (4830) | 0 | 10 | 0/0/765 | 5050 | 0 (0) | 0 | 0/0/10/0 |
| scratch | silver (genetic) kitten st1 | 5 | 359 | 125 | 0/0/1075 | 5125 | 104 (6680) | 125 | 34 | 0/0/1215 | 7495 | 0 (0) | 25 | 21/0/13/0 |
| scratch_in | silver (genetic) kitten st1 | 5 | 359 | 81 | 0/0/110 | 2730 | 81 (3420) | 20 | 10 | 0/0/480 | 3600 | 0 (0) | 0 | 0/0/10/0 |
| scratch_out | silver (genetic) kitten st1 | 5 | 359 | 56 | 0/0/70 | 1950 | 56 (2480) | 0 | 10 | 0/0/235 | 1430 | 0 (0) | 0 | 0/0/10/0 |
| roll | silver (genetic) kitten st1 | 5 | 359 | 64 | 0/0/4740 | 2575 | 41 (3775) | 200 | 27 | 0/0/4930 | 2610 | 0 (0) | 100 | 23/0/4/0 |
| kick | silver (genetic) kitten st1 | 5 | 359 | 84 | 0/0/2660 | 1665 | 49 (2490) | 35 | 44 | 0/0/2875 | 3040 | 0 (0) | 50 | 35/0/9/0 |
| bat | silver (genetic) kitten st1 | 5 | 359 | 113 | 0/0/2255 | 2985 | 86 (3955) | 195 | 37 | 0/0/2255 | 4695 | 0 (0) | 175 | 27/0/10/0 |
| swat | silver (genetic) kitten st1 | 5 | 359 | 81 | 0/0/10580 | 1810 | 54 (2725) | 5 | 37 | 0/0/10655 | 1620 | 0 (0) | 45 | 27/0/10/0 |
| walk_front | silver (genetic) kitten st1 | 5 | no art for this coat |
| walk_back | silver (genetic) kitten st1 | 5 | no art for this coat |
| sniff | silver (genetic) kitten st1 | 5 | no art for this coat |
| bonk | silver (genetic) kitten st1 | 5 | no art for this coat |
| jump_up | silver (genetic) kitten st1 | 5 | 359 | 111 | 0/0/140 | 6040 | 111 (7805) | 70 | 10 | 0/0/1205 | 9010 | 0 (0) | 0 | 0/0/10/0 |
| jump_air | silver (genetic) kitten st1 | 5 | 359 | 76 | 0/0/125 | 4545 | 76 (5900) | 0 | 15 | 0/0/630 | 5685 | 0 (0) | 0 | 0/0/15/0 |
| jump_land | silver (genetic) kitten st1 | 5 | 359 | 91 | 0/0/155 | 5595 | 91 (7055) | 30 | 10 | 0/0/1105 | 8070 | 0 (0) | 0 | 0/0/10/0 |
| turn | silver (genetic) kitten st1 | 5 | 359 | 91 | 0/0/155 | 5255 | 91 (6615) | 30 | 10 | 0/0/930 | 6850 | 0 (0) | 0 | 0/0/10/0 |
| pickup | silver (genetic) kitten st1 | 5 | 359 | 81 | 0/0/150 | 5365 | 81 (6810) | 5 | 10 | 0/0/1070 | 7740 | 0 (0) | 0 | 0/0/10/0 |
| carry | silver (genetic) kitten st1 | 5 | 359 | 134 | 0/0/5630 | 5590 | 99 (7405) | 220 | 45 | 0/0/5675 | 7155 | 0 (0) | 385 | 35/0/10/0 |
| toss | silver (genetic) kitten st1 | 5 | 359 | 101 | 0/0/150 | 5860 | 101 (7720) | 25 | 10 | 0/0/810 | 5680 | 0 (0) | 0 | 0/0/10/0 |
| yawn | silver (genetic) kitten st1 | 5 | 359 | 111 | 0/0/135 | 5055 | 111 (6780) | 60 | 10 | 0/0/810 | 6190 | 0 (0) | 0 | 0/0/10/0 |
| shake | silver (genetic) kitten st1 | 5 | 359 | 117 | 0/0/160 | 5115 | 117 (6885) | 75 | 10 | 0/0/830 | 6280 | 0 (0) | 0 | 0/0/10/0 |
| bump | silver (genetic) kitten st1 | 5 | 359 | 117 | 0/0/125 | 4855 | 117 (6525) | 65 | 10 | 0/0/740 | 5670 | 0 (0) | 0 | 0/0/10/0 |
| groom_other | silver (genetic) kitten st1 | 5 | 359 | 122 | 0/0/8890 | 4690 | 107 (6195) | 145 | 25 | 0/0/8945 | 6075 | 0 (0) | 215 | 15/0/10/0 |
| nose_touch | silver (genetic) kitten st1 | 5 | 359 | 94 | 0/0/155 | 6005 | 94 (7640) | 20 | 10 | 0/0/935 | 6510 | 0 (0) | 0 | 0/0/10/0 |

### Moving rows, 6 s, card scale

| scene | look | s | frames | before: changed steps | before: px/step min/med/max | before: coat px off-art | before: unexplained steps (px) | before: a-b-a px | after: changed steps | after: px/step min/med/max | after: coat px off-art | after: unexplained steps (px) | after: a-b-a px | after: art/rig/breath/body steps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| walk | ginger adult | 5 | 719 | 423 | 0/50/11170 | 52960 | 341 (60632) | 2378 | 98 | 0/0/15019 | 46079 | 0 (0) | 1143 | 72/7/18/1 |
| run | ginger adult | 5 | 719 | 497 | 0/105/35230 | 353728 | 345 (290551) | 52466 | 178 | 0/0/35424 | 335541 | 0 (0) | 50928 | 94/44/19/21 |
| walk_front_back | ginger adult | 5 | 719 | 560 | 0/99/36783 | 166661 | 478 (181060) | 18353 | 91 | 0/0/36882 | 107844 | 0 (0) | 2707 | 47/16/17/11 |
| walk | tuxedo kitten st2 | 5 | 719 | 307 | 0/0/4975 | 21049 | 230 (26556) | 624 | 91 | 0/0/6912 | 17059 | 0 (0) | 265 | 72/0/18/1 |
| run | tuxedo kitten st2 | 5 | 719 | 438 | 0/44/21377 | 159538 | 295 (146855) | 18333 | 151 | 0/0/21640 | 154279 | 0 (0) | 18157 | 94/15/19/23 |
| walk_front_back | tuxedo kitten st2 | 5 | 719 | 520 | 0/40/23401 | 66921 | 449 (81825) | 3576 | 75 | 0/0/23270 | 48409 | 0 (0) | 677 | 46/1/17/11 |
| walk | calico (genetic) adult | 5 | 719 | 491 | 0/60/14050 | 59343 | 408 (67833) | 2939 | 99 | 0/0/16303 | 49575 | 0 (0) | 1209 | 72/8/18/1 |
| run | calico (genetic) adult | 5 | 719 | 548 | 0/110/38025 | 364027 | 396 (302683) | 56122 | 178 | 0/0/38274 | 345234 | 0 (0) | 54316 | 94/44/19/21 |
| walk_front_back | calico (genetic) adult | 5 | 719 | 567 | 0/114/39991 | 179020 | 485 (191954) | 19441 | 96 | 0/0/40069 | 118976 | 0 (0) | 2858 | 47/21/17/11 |
| walk | silver (genetic) kitten st1 | 5 | 719 | 286 | 0/0/4015 | 18223 | 207 (22477) | 352 | 91 | 0/0/5843 | 13665 | 0 (0) | 155 | 72/0/18/1 |
| run | silver (genetic) kitten st1 | 5 | 719 | 420 | 0/30/17931 | 142452 | 277 (127256) | 16140 | 151 | 0/0/18100 | 136929 | 0 (0) | 16366 | 94/15/19/23 |
| walk_front_back | silver (genetic) kitten st1 | 5 | 370 | 345 | 0/43/18437 | 41316 | 286 (48203) | 2153 | 52 | 0/0/18820 | 26163 | 0 (0) | 427 | 38/1/6/7 |

### Two cats in the house, 40 s, card scale

| scene | look | s | frames | before: changed steps | before: px/step min/med/max | before: coat px off-art | before: unexplained steps (px) | before: a-b-a px | after: changed steps | after: px/step min/med/max | after: coat px off-art | after: unexplained steps (px) | after: a-b-a px | after: art/rig/breath/body steps |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| house cat 0 | calico (genetic) adult | 5 | 4799 | 2309 | 0/0/38070 | 334958 | 2173 (417415) | 21276 | 276 | 0/0/37705 | 338757 | 0 (0) | 15822 | 92/21/140/23 |
| house cat 1 | ginger adult | 5 | 4799 | 3086 | 0/55/37023 | 1711375 | 2824 (2139451) | 242074 | 494 | 0/0/36992 | 1487699 | 0 (0) | 181478 | 175/27/142/150 |
