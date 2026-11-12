# Cat sprite consistency audit (Native API 151)

Question: do the shipped native cat sprites shade consistently, and does anything stick out wrongly?
Trigger: a black kitten sitting on the cat tree looks as if its shading keeps changing. The earlier
audit (`PET_SPRITE_STEADINESS.md`) fixed per-pixel shimmer from scaling and rig steps; this one is
about the art itself: what the sheets draw from one frame to the next.

## Method

`python3 tools/audit_cat_sprites.py OUT_DIR` (plain Python, Pillow, numpy; about a minute; `--no-images`
skips the pictures). It reads `native/src/main/resources/pet/` the way the loaders do (8-bit grey
index PNGs, `palettes.json`, `anims.json`) and covers **56 sheets** (7 side kinds `stage0..5`/`adult`
and 7 depth kinds `dstage0..5`/`depth`, times 4 coats) = **6 412 frames** (every clip row, every frame).
It writes `audit.json` with every number and these pictures (4x, palette applied, grey background so a
black cat shows; cropped to the sheet kind's drawn area):

| file pattern | what |
|---|---|
| `contact_{coat}_{kind}[_1\|_2].png` | every clip row of a sheet, frames side by side |
| `heat_{coat}_{kind}[_1\|_2].png` | the same, dimmed, painting what changed since the previous frame (the row loops): magenta = coat shade became another coat shade, cyan = anything else |
| `crosscoat_{kind}[_1\|_2].png` | each clip at 3 frames, black / ginger / gray / tuxedo side by side |
| `crossstage_{coat}_[1\|2].png` | each clip's first and middle frame at stage 0..5 and adult |
| `beforeafter_*.png`, `gap_*.png`, `finding_*.png` | thumbnails of the findings below |

All in `/tmp/claude-0/-home-user-Lifemax-rpg/73f0826e-b578-5741-9922-ec416a3fc508/scratchpad/sprite-audit/`
(not committed). The rig (`rig/*.bin`) is not audited on its own: it carries no pixels, it cuts ears,
tail and whiskers out of these same side sheets at run time (`RigCompose.compose(src, ...)`), so every
index it moves is checked here.

Palette indices: 10 outline, 20-50 lit coat ramp, 60-80 stripe ramp, 90-110 whites, 120-140 pink, 150
pupil, 160-170 iris, 180-190 highlights/whiskers, 200 mouth, 210 tongue (confirmed against
`CatPalette` and the art: 150 is the pupil, not a second outline).

### Checks (the letters of the brief)

- **a** indices the coat's palette does not define (they would draw clear) and indices used by under
  7 pixels in a sheet. Result: none of either, all 56 sheets.
- **b** lone pixels (differ from all 8 neighbours): coat shades counted only when they differ by 8 L*
  or more from the neighbours' mean, plus lone whites/pinks and lone outline pixels.
- **c** outline ring: a coat pixel with a clear 4-neighbour is a **gap**; 2x2 outline blocks;
  outline pixels buried in the body. (Out of frame is not clear: a cat touching the frame edge has
  no ring there.)
- **d** shade flips per clip: coat to coat changes between consecutive frames (and wrap), the
  pixels whose shade differs anywhere in the row, and **pure flips**: a coat pixel whose shade
  changes while its 3x3 neighbourhood has the same kind of pixel (clear / outline / coat / other) in
  both frames. A pure flip is a shade change with nothing moving around it, which is what the eye
  reads as shimmer; a flip next to a moving edge is the art animating.
- **e** index maps diffed between coats (gray is the base): silhouette, outline, whites, shade.
- **f** per clip across stages: median box must not shrink as the cat grows, no frame below the
  row's floor, centre of mass jump in resting rows.
- **g** palette: ramps monotone in L*, CIE L* step and CIE76 delta E between adjacent levels.
- **h** the `_w` (weak) rows against their base rows: area, centre and height.
- **i** identical consecutive frames; a resting frame whose silhouette differs from the row's median
  by over 60 px.

Thresholds. The art has dithered fur texture, so about 10 lone coat pixels per frame, 56 buried
outline pixels and 12 outline 2x2 blocks per adult frame are the style, not defects (26 371 frame
findings before filtering). Texture checks (b coat, b white/pink, c double, c inside) are kept only
when a frame is at least 6 px and 1.8x above its own row's median. Gaps and lone outline pixels have
no baseline and are all kept. For shade flips there is no honest absolute threshold (a walk flips
hundreds of pixels because the body moves), so the flag is by kind of row: a resting row (sit, loaf,
sleep) should have **0 pure flips**; the shipped ones have 6 to 97 (below).

## Findings, most visible first

| # | what | where | size | looks like | real defect? | fix | thumbnails |
|---|---|---|---|---|---|---|---|
| 1 | **Coat shade flips with nothing moving** (dither toggles 40/50, 30/20, 30/40) | every resting row, all coats, all stages | black adult `idle_sit`: 117 flips over the loop, 97 pure, up to 16 per step (owner measured 7-16), 31 pixels differ somewhere in the row; stage 3: 61/49/9; stage 1: 18/14/5. `idle_sit_w` 113/89, `loaf` 38/32 (13 per step), `loaf_w` 38/35, `sleep` 18/15, `sleep_w` 24/19. Over the 196 resting rows (4 coats x 7 stages x 7 rows): 5 766 flips, 4 978 of them pure | cheeks, chest, flank and tail texture glittering while the cat sits. On black it is 19 L* (40 vs 50) per flipped pixel, on ginger 14. | yes: the art redraws texture per frame where the body is identical | **fixed**, `RestingShade` | `beforeafter_black_adult_idle_sit.png`, `beforeafter_black_stage3_idle_sit.png`, `beforeafter_black_stage1_loaf.png`, `heat_black_adult_1.png`, `heat_black_stage3_1.png` |
| 2 | **Outline ring open at the kitten muzzle**: 1-2 pixels of coat colour where the outline belongs on the front of the face | stages 0-3 (444 frames at stage 2 and 3, 412 at stage 1, 284 at stage 0), every row, identical in all four coats (437-438 frames per coat, 786 px per coat) | 3 150 px in 1 750 frames; adult 6 frames | a pale chip in the face outline that moves with the head | yes: ring closed everywhere else | **fixed**, `OutlineGaps` | `gap_gray_stage2_idle_sit_f0.png` |
| 3 | Tail sinks a row through the floor in frame 2 (`idle_sit` and `idle_sit_w` stage 2, `sleep` stage 4, `sleep_w` adult) | resting rows | 1 frame each | a lump under the cat that comes and goes | yes | already mended by `RestingFloor` (re-found by check f: 5 hits) | `finding_sunk_tail_stage2_idle_sit_frame2.png` |
| 4 | Black and tuxedo palette: lit ramp steps are close | `palettes.json` black/tuxedo | 20 to 30: L* +6.3 (CIE76 dE 14.3); 30 to 40: +9.2 (dE 9.7); 40 to 50: +18.9 (dE 21.6). Stripe ramp 60 to 70: +6.7 (dE 10.1). Ginger's smallest step is L* +14.3 (gray's 14.6) | why a flip on black reads strongly | not broken: monotone, distinguishable; not changed | keep the look; with #1 fixed the closeness no longer shows as flicker | table below |
| 5 | Lone outline pixel (1 px) | 656 frames, mostly stage 3 (268), stage 1 (108), depth 56; e.g. stage 0 `walk` frame 0 at x33 y24 | 668 px | a dark speck at the face edge | ambiguous (looks like a whisker root or nose tip) | left, listed | `crosscoat_stage0_1.png` |
| 6 | Black/tuxedo vs gray/ginger index differences beyond shading | adult 235 px, stage 4 28, stage 5 21: 284 silhouette px and 203 outline px in 128 frames, 3+2 px per frame, e.g. the toe pixel of the front paw (`idle_sit` x51-54 y39-40) | tiny | one toe-edge pixel differs between coats | art meaning it (per-coat hand edit), not a defect | none | `crosscoat_adult_1.png` |
| 7 | Texture outliers: frames with unusually many lone specks / buried outline / 2x2 blocks | `b-stray-coat` 7 frames (90 px; stage 2 `yawn`, stage 0 `run`), white/pink 15 (169 px), double outline 24 (252 px), inside 4 (112 px; stage 1 `groom` frame 0: 28 px) | | texture, not visible at 1x | art meaning it (the whole row is dithered) | none | `contact_gray_stage1_1.png` |
| 8 | `slowblink` frame 2 equals frame 3 at every stage | 7 sheets | | a held closed-eye beat | art meaning it | none | |
| 9 | `sleep_w` adult median box (51x20) is one row shorter than stage 5's (48x21) | check f, 1 hit | 1 row | a pose difference, wider and lower, not a shrink of the cat | art meaning it | none | |
| 10 | Depth rows: 6 outline-gap pixels per sheet (24 frames each in `depth`, `dstage0`, `dstage1`, `dstage4`, `dstage5`) | `depth`, `dstage*` | tiny | | yes, trivial | not mended: the depth sheet is also read directly by the rig layer | |

Nothing else flagged: no undefined or rare indices, every ramp monotone in every coat (check g), no
stage shrinks except #9, no resting frame off its centre of mass by over 3 px (check f), `_w` rows
are 3 to 10 % smaller in area than their base rows (ratio 0.90 to 0.97), same centre. Moving rows (walk, run, jump, turn,
stretch) flip up to 259 pixels per step on the adult (`jump_up`); that is the body moving, and the
pure flips in them (104 in `jump_up`) sit on moving edges, so they are left. `purr` (110 pure over 4
frames, 32 per step on the adult), `meow` (64) and `knead` (32) also flip texture while the cat is
nearly still; they were not frozen because their head and body do move a little, see "Not done".

### Palette (check g), adjacent steps, L* and CIE76 dE

| coat | lit 20-30 | lit 30-40 | lit 40-50 | stripe 60-70 | stripe 70-80 |
|---|---|---|---|---|---|
| black, tuxedo | 6.3 / 14.3 | 9.2 / 9.7 | 18.9 / 21.6 | 6.7 / 10.1 | 10.2 / 11.8 |
| ginger | 16.0 / 21.3 | 16.1 / 19.2 | 14.3 / 20.2 | 10.2 / 19.3 | 14.6 / 17.0 |
| gray | 14.6 / 14.7 | 16.7 / 16.8 | 17.3 / 17.4 | 10.3 / 10.5 | 12.2 / 12.3 |

## Fixes (loader side, `art/SheetMend.kt`, wired in `CatArt.assemble`; art files and palette unchanged)

1. **`RestingShade.freeze`** (resting rows only: sit, loaf, sleep and their `_w` rows, the
   `isHoldable` set `RestingFloor` uses): a coat-shade pixel takes the shade it has most often (earliest
   frame on a tie) among the frames where it is a coat shade and its 3x3 neighbourhood has the same
   kind of pixel in every cell. Eyes, mouth, ears, outline and anything that moved are left as drawn,
   so blinks and head moves are intact. Only coat shades change, to other coat shades; running it twice
   changes nothing. Pure flips in the 196 resting rows: 4 978 to 0. Flips overall in them: 5 766 to
   819 (the rest sit next to moving geometry). Black adult `idle_sit`: 117 to 21 flips, worst step 16 to
   4, pixels that differ anywhere 31 to 7. Black adult `loaf` worst step 13 to 2.
2. **`OutlineGaps.close`** (every side row): a coat pixel with a clear and an outline 4-neighbour inside
   its frame becomes outline (repeated up to 4 passes, since closing one pixel exposes the next).
   3 150 px in 1 750 frames; no coat pixel on the silhouette edge next to the outline remains.
3. The black palette is **not** changed: its ramp is monotone and the closest steps (L* 6 to 9) are
   distinguishable. If a phone still finds the 30 to 40 step too faint, raising index 40 from `2e2844`
   by about 4 L* would be the dial; not done without sight of the result.

Tests (`SheetMendTest`, 4 cases, plus `RestingTailTest` still green, 9 tests, run locally with kotlinc
and JUnit): every shipped coat at every stage and two genetic coats (calico, silver) have 0 gap pixels
after the mend and 0 pure flips in every resting row; only the intended pixels change; the mends are
idempotent; the unmended art has over 700 gap pixels, over 2 000 flipping pixel pairs and, for the black
adult `idle_sit`, over 40 frozen pixel-frames and 200 flipping pairs, so the tests fail on the old art.

## Screenshots to re-record

Pixels change only in kitten faces (stages 0-3, every pose) and in resting rows. Re-record every
screenshot class that draws a cat through `CatArt`: `CatHouseScreenshotTest`,
`CatLiveCoatScreenshotTest`, `CatLiveScreenshotTest`, `CatRigScreenshotTest`, `CatTurnScreenshotTest`,
`CatTreeClipScreenshotTest`, `SofaClipScreenshotTest`, `SofaVisibilityScreenshotTest`,
`LiveDepthCardScreenshotTest`, `LiveDepthTabScreenshotTest`, `OverviewPetPortraitScreenshotTest`,
`PetDemoScreenshotTest`, `PetScreenshotTest`, `PetTouchScreenshotTest`, `PairPosesScreenshotTest`,
`PetPanelsScreenshotTest`, `VolumeScreenshotTest`; and, if their fixtures show a kitten or a resting
cat, `OverviewCardsA/B`, `OverviewScreenScreenshotTest` and `GalleryScreenshotTest`. Not run here (no
Paparazzi); the CI tag `[refresh-cat-snapshots]` is the way.

## Not done, and needs the phone

- Freezing `purr`, `meow`, `knead` the same way (they flip 26-110 texture pixels while nearly still).
  The 3x3 rule would leave their moving parts alone, but the clips are meant to move; look at the
  heat sheets first.
- Depth rows keep their 6 gap pixels per sheet.
- Whether the frozen coat reads as calm on the cat tree: the owner's case may also include the
  head-following motion fixed separately.
- The rig is audited through its source sheets only.
