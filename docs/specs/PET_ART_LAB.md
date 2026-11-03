# Cat animation lab

Status: built. Twelve new animation rows (jump in three parts, turn, the toy pickup set, waking yawn and head shake, head-bump, grooming another cat, nose touch) and three pair poses, for the four legacy coats, every genetic coat and all six kitten stages, made by a toolchain from the cat's own hand-drawn frames. The web room is unchanged; the native app ships the rows and their metadata and can play every new clip by name.

Toolchain: `lifemax_modular_v73/tools/pet_art/` (Node, no dependencies beyond the repo's `tools/pet_png.js`). Pose specs: `tools/pet_art/poses/`. Native: `feature/pet/body/Clips.kt` (the clips), `feature/pet/art/LabTable.kt` (the metadata), resources `pet/anims.json` and `pet/lab.json`.

## 1. What it is for

The cat's art is a fixed set of hand-drawn pose rows (28 side-view rows, 4 front/back rows). The engine plan wanted moves those rows do not have: a real jump instead of the two-frame leap, a turn instead of a mirror pop, a head that dips to pick a toy up and carries it, a waking routine, cozy and pair moments. The owner's rule: do not fake the art; it must be indistinguishable in style from the existing frames.

The lab makes new frames by **recomposing the existing frames**: cut a frame into parts along its own outlines, move or turn a part with a pixel-art-aware transform, put it back, repair the outline. Every output pixel is a copy of a source pixel (no blending, no new colours, the palette-index shading and the outline index stay), and every frame is reproducible from a short declarative spec.

## 2. How it works

### 2.1 Plates: four coats at once

The four legacy coats are four index sheets drawn on the same grid (the page's DNA composer, `petDnaCompose`, reads them pixel by pixel, so they must stay registered). The lab packs the four indices of a pixel into one 32-bit word (ginger, gray, black, tuxedo) and works on these "plates" (`lib/plate.js`). A move or a turn moves whole words, so all four coats move identically and stay registered; DNA coats composed from the new rows are therefore correct too (proved by the recorded page canvases, section 6).

### 2.2 Parts (`lib/parts.js`, `lib/geom.js`)

The rig data (`petRig.html`, made by `tools/rig_build.py` by cutting each frame along its outline cells) gives the ears, tail polyline, whiskers, the legs-only row band `l`, the torso column band `x` and a face point `h`. It has no head part, so the lab adds a **head cut**:

- size from the ear bases (their distance is the head's width at every growth stage), centre from the ear bases and `h`;
- the head is the fill reachable from the face without crossing an outline pixel and without leaving an ellipse around that centre, so the cut follows the art's own lines (chin, back of the head) and only crosses open fur at the neck;
- nothing below the chin row (two rows under the lowest eye, nose or mouth pixel; needed for kittens, whose heads are so big that the ellipse reaches the front legs);
- the outline ring around that fill, the rig's ears and whiskers, and any small specks of body cut off by the head go with the head.

Every opaque pixel belongs to exactly one part, so drawing the parts back gives the source frame exactly; the QA (`frameQa`) checks this for every source frame a spec cuts.

Moving the head draws the body, then the turned or moved head over it, then grows the body's fur into any gap at the neck, but only between the body and the new head position (`NECK_REACH` pixels).

Legs and torso are not cut out: they are changed with the rig's own method, whole-row and whole-column bands (`legBand`, `torsoBand`), which keep paws and faces untouched. When the rig has no band for a frame (most kitten frames), the band is measured the way `rig_build.py` does (first wide "belly" row above the paws).

### 2.3 Transforms (`lib/xform.js`)

- **RotSprite**: Scale2x three times (8x, equal packed words count as equal), turn with nearest neighbour about the pivot, sample the big pixel under each output pixel centre. Edges stay one pixel wide; diagonals stay clean.
- **Leg band**: remove (crouch, squash) or repeat (rise) whole rows inside the legs-only band; paws stay on the floor, everything above moves.
- **Torso band**: remove or repeat whole columns; shorter body (the three-quarter of the turn) or longer.
- **Scale** (nearest neighbour about an anchor), **shift**, **mirror**.

### 2.4 Repair (`lib/repair.js`)

The hand-drawn frames obey simple rules (measured over all 154 adult frames): every fill pixel except a whisker has its four side neighbours opaque (the outline closes every form) and no pixel floats alone. After the ops, inside the zone they changed (grown by two pixels; untouched art is never "repaired"): drop specks of up to 3 pixels, fill one-pixel cracks with the neighbouring fill, turn stray inner outline dots back into fill, re-draw the outline wherever fur touches clear, remove outline left dangling or doubled. The QA audit then reports any new open outline, lone pixel or a form running off the frame.

### 2.5 Face edits (`lib/face.js`)

By palette index (eyes 150..180, nose and mouth pinks 120..140, mouth line 200, tongue 210): `jaw` opens the mouth by k pixels below the mouth line (the yawn), `eyesFrom` copies eyes and lids from another frame.

### 2.6 Kittens

The six kitten stages have their own hand-drawn sheets (big-headed chibi proportions, not a scaled adult; the history holds no generator: they were imported as data). The lab therefore derives kitten rows **the same way as the adult rows, from each stage's own frames**: the same spec runs on the stage's sheet and rig, lengths are scaled by the stage's `sx` (0.6 .. 0.93, never rounded to zero), angles are kept. A kitten's yawn is made of that kitten's own idle, slow-blink and meow frames.

### 2.7 Front and back views

No lab rows on the depth sheets: none of the new moves is meaningful head-on except the turn, which uses the front view (`walk_front` frame 0 of the same growth stage) as its middle frame. Jump, pickup and pair poses happen side-on in the room; the engine keeps using the existing `walk_front`/`walk_back`/`sniff`/`bonk` for depth travel.

## 3. Pose specs

One JSON file per row in `tools/pet_art/poses/`, listed in sheet order in `poses/index.json` (append only: a row's position is its sheet row). A row:

```json
{ "name": "carry", "fps": 12, "loop": true, "contacts": [0, 4],
  "chain": { "from": ["pickup", "walk"], "to": ["toss", "walk"] },
  "about": "what it shows",
  "frames": [ { "src": "walk:0", "ops": [{ "part": "head", "rotate": -8, "shift": [0, -1] }] }, ... ] }
```

Optional row keys: `flipAtEnd` (turn), `bite` (pickup), `release` (toss), `touch` (bump, nose_touch), `stride` (a locomotion row's gait), `standsOn` (an airborne row measures its floor from this grounded frame).

A frame: `src` is one of the page's frames of the same kind (`row:frame`; `depth/row:frame` takes the depth sheet of the same growth stage); `mirror: true` draws it facing the other way; `ops` run in order:

| op | meaning |
|---|---|
| `{ "legs": k }` | remove k rows from the legs-only band (k < 0 adds rows); `"band": [a, b]` names the rows a..b above the floor instead of measuring them |
| `{ "torso": k }` | remove k columns from the torso band (k < 0 adds) |
| `{ "rotate": deg, "pivot": p }` | RotSprite turn of the whole frame, positive clockwise (nose down for a cat facing right); pivots `rearPaw`, `frontPaw`, `paws`, `centre` or `[x, y]` |
| `{ "shift": [dx, dy] }` | move by whole pixels |
| `{ "floor": true }` | move so the paws stand on the row's floor |
| `{ "part": "head", "rotate": deg, "shift": [dx, dy], "pivot": "neck" }` | move the head (pivot defaults to the neck: the middle of where head meets body fur) |
| `{ "face": "jaw", "by": k }`, `{ "face": "eyesFrom", "src": "row:f" }` | face edits |

Part and face ops come first (they use the rig's coordinates, which hold until a whole-frame op moves the art). Lengths are adult pixels.

## 4. How to add or change an animation

1. Write `poses/<name>.json` and append the name to `poses/index.json` (a new row goes at the end; never reorder).
2. `node tools/pet_art/build.js --sheets /tmp/sheets` and **look at** `<name>-<coat>.png` (each coat), `<name>-kittens.png` and `flipbook.png` at full size (6x, foot line green, mouth anchor red); `qa.txt` lists frames the checks flag. Iterate until the checklist below passes.
3. `node tools/pet_art/build.js` (writes the page data and the native test reference), then `node tools/gen_pet_native_assets.js`.
4. Native: add the clip at the end of `Clips.kt` (after the last lab clip, so older ids stay), with the row number, frames, fps and the foot row from `pet/anims.json`.
5. Re-record the page fixtures (`tools/gen_pet_art_fixtures.js`, `tools/gen_pet_coat_fixtures.js`): the page's canvases grow by the new row; the old rows must stay identical (the tests prove it).
6. Run `node tools/pet_art/build.js --check` (CI does), the art and body unit tests, and the pet page tests.

### Quality checklist (per frame, by eye at 6x, every coat and stage)

- Silhouette reads as the intended pose at 1x; no part floats or overlaps wrongly.
- Face intact: both eyes, nose, mouth, whiskers in their places; nothing smeared by a turn.
- Outline continuous: no gaps, no doubled outline, no dangling ends; seams at the neck invisible or reading as a natural line.
- Shading bands keep the light from the upper left as in the source rows (a turn of more than about 15 degrees starts to tilt the stripes visibly; keep heads under 18 degrees).
- Paws on the foot line in grounded frames; nothing clipped at the frame edge.
- Played in sequence, nothing pops: the same body length and head size frame to frame, contact frames planted.
- The four coats agree (the tuxedo blaze and paws, the tabby stripes move with the body).

## 5. The new rows

Rows 28..39 of the side-view sheets (adult and every kitten stage), after the page's 28 rows. Foot row (`bottom`): one below the lowest pixel of the first frame (the rule walk and idle_sit follow); per kitten stage in `petAssets.lab.stageBottom`. Mouth anchors: per frame, per kind, in frame pixels (side view facing +x), in `lab.json` (`mouth.adult`, `mouth.st0`..`st5`); the adult values below are rounded.

| row | sheet row | frames | fps | play | bottom (adult / st0..st5) | planted frames | adult mouth anchor per frame | from | to |
|---|---|---|---|---|---|---|---|---|---|
| jump_up | 28 | 4 | 12 | once | 46 / 45 45 45 45 45 46 | 0 1 2 | 56,28 55,28 53,21 56,22 | idle_sit, walk, stretch | jump_air |
| jump_air | 29 | 2 | 8 | once (hold 0 rising, 1 falling) | 46 / as walk | none | 56,25 55,34 | jump_up | jump_land |
| jump_land | 30 | 3 | 12 | once | 46 / 45 45 45 45 45 46 | 0 1 2 | 58,29 56,29 56,27 | jump_air | idle_sit, walk, stretch |
| turn | 31 | 4 | 10 | once, flip at end | 46 / 45 45 45 45 45 46 | 0..3 | 56,27 56,28 39,26 16,28 | idle_sit, walk, stretch | walk, stretch, idle_sit |
| pickup | 32 | 6 | 8 | once, bite on 4 | 46 / 45 45 45 45 46 46 | 0..5 | 54,26 57,30 59,37 60,39 58,39 58,29 | walk, stretch, eat | carry |
| carry | 33 | 8 | 12 | loop, walk stride | 46 / 45 45 45 45 46 46 | 0 4 (footfalls) | 56,23 every frame | pickup, walk | toss, walk |
| toss | 34 | 4 | 10 | once, release on 1 | 46 / 45 45 45 45 46 46 | 0..3 | 54,29 55,21 54,22 54,25 | carry, pickup | stretch, walk, idle_sit |
| yawn | 35 | 7 | 6 | once | 47 / 46 46 46 47 47 47 | sitting | 50,22 .. 49,20 .. 50,23 | idle_sit, loaf, sleep | idle_sit, stretch |
| shake | 36 | 5 | 14 | once | 47 / 46 46 46 47 47 47 | sitting | 50,20 50,25 50,21 50,24 50,22 | yawn, idle_sit | idle_sit, groom |
| bump | 37 | 4 | 6 | once, contact on 2 | 47 / 46 46 46 47 47 47 | sitting | 50,22 52,21 53,21 51,22 | idle_sit, purr | purr, idle_sit |
| groom_other | 38 | 6 | 5 | loop, licks on 1 and 3 | 47 / 46 46 46 47 47 47 | sitting | 52,24 53,27 53,23 53,27 53,23 52,26 | idle_sit | idle_sit, purr |
| nose_touch | 39 | 4 | 6 | once, contact on 2 | 46 / 45 45 45 45 46 46 | 0..3 | 54,26 56,27 57,29 55,27 | walk, stretch | stretch, rub, walk |

Notes for the engine:
- **Jump**: play `jump_up` on the spot, start the arc on its frame 3, hold `jump_air` frame 0 while rising and frame 1 once falling (its frame 1 is drawn two pixels lower in its cell so the raised tail fits; the arc places it), then `jump_land` at touchdown. The existing `crouch` row (a play bow) is not part of it.
- **Turn**: all four frames are drawn with the old facing (frame 3 is already mirrored in the sheet); flip the body's facing when the clip ends. The middle frame is the front view, with the chest band shortened for the dip.
- **Toy**: the toy rides the mouth anchor from `pickup`'s bite frame on, through `carry` (pendulum hangs from the anchor), until `toss`'s release frame; draw it after the cat so the muzzle overlaps it naturally. The reach ends about 3 px above a 6 px ball's top: the toy lifts the last bit along an arc into the mouth (owner's requirement 2).

### Pair poses (`lab.json` `pairs`)

Two coordinated singles, not a 144-wide frame: cat A plays row `a`, cat B plays row `b` with its frame origin `offset[kind]` pixels from A's (x along A's facing, y up is negative), facing the other way when `mirrorB`, drawn behind A unless `bFront`. Offsets are computed by the toolchain from the art (`fit`), per growth stage of A (for two cats of different stages use A's stage; the contact frame is where they must meet).

| pair | A | B | placement | offset adult (st0 .. st5) |
|---|---|---|---|---|
| snuggle | sleep | sleep (half a cycle out of phase) | spoon: same facing, B behind and 2 px further back | -23,-2 (-15,-1 .. -22,-2) |
| groom | groom_other | purr, mirrored | A's mouth on B's forehead on contact frame 1 | 39 (29 .. 37) |
| greet | nose_touch | nose_touch, mirrored | muzzles meet on frame 2 | 59 (37 .. 57) |

The existing `rub` row stays the side-to-side cheek rub; `bump` is the new forward head-bump toward a finger. `knead` (4 frames) was checked: it is clean and needs nothing. The `stretch` row (7 frames, from standing) remains the waking stretch; there is no loaf-to-stand stretch (section 8).

## 6. Integration and proof that nothing else changed

- **Page data**: the new rows are appended to the same coat sheets (`petAssets.coats`, `petGrowth.stages[i].coats`), so legacy and DNA coats get them. The page's own tables are not touched: `petAssets.anims` still lists exactly its 28 rows in the same order, and the stages' bottom tables are unchanged; the lab rows' table lives in `petAssets.lab` (`anims`, `stageBottom`, `rows`, `pairs`). The web room never asks for a lab row. `pethd`, `pet-portrait`, `pet-coat`, `native-pet` and `native-overview-cards` pass unchanged.
- **Fingerprints**: before the first lab row existed, every frame of every kind (1,204 frames, all four coats) was fingerprinted into `android-wrapper/native/src/test/resources/pet-lab/base-frames.json` (its own commit). `build.js --check` (CI) and `LabArtTest` both verify the shipped and page sheets against it.
- **Recorded page canvases**: re-recorded with `gen_pet_art_fixtures.js` and `gen_pet_coat_fixtures.js`; the first 1,344 rows of every adult, kitten and DNA-coat canvas are byte-identical to the previous recording (the sheets are 1,920 rows now).
- **Native**: `gen_pet_native_assets.js` writes the 40-row table to `anims.json`, the lab bottoms into `growth.json` and `lab.json`; `Clips.kt` has the 12 clips appended after the depth clips (older clip ids unchanged, so golden traces do not move); `LabTable` reads the metadata; `PetArtResources.loadLab()` loads it. `CatBody` plays any lab clip through a plain `act` step by name; using them in plans (the jump arc, the toy carry, the turn flip, pair staging) belongs to CT4/CT5.
- **Tests**: `LabArtTest` (base fingerprints; every lab frame recoloured by the native `Recolour` for the four coats equals the toolchain's own rendering in `pet-lab/lab-frames.json`; two genetic coats' lab rows equal the page's recorded room canvases for the adult and a kitten stage; metadata consistent), `LabClipsPreviewTest` (the preview harness: every lab clip played by a real `CatBody` from frame 0 through every frame, holding or cycling as declared, and every frame drawable for all coats and stages), plus the existing art and body tests.

## 7. Honest assessment (after the first prototypes of the jump and the toy set)

What reads well:
- **Jump**: all frames are the cat's own leap and gallop drawings, turned 7 to 12 degrees by RotSprite or crouched by the leg band. The crouch, push, airborne stretch, front-paws-down descent and landing squash read clearly and look drawn, not distorted.
- **Carry**: an eight-degree head lift on the real walk cycle; the face and ears survive untouched; phase-matched with walk.
- **Pickup**: the eat row's own head-lowering frames plus a 10 to 12 degree head dip reach the floor convincingly, with eyes closing on the bite.
- **Turn**: side, foreshortened three-quarter, front view, mirrored three-quarter reads as a real turn at 10 fps.
- **Greet and groom pairs**: the noses meet; the groom lick lands on the other cat's forehead.

What looks off or is weaker:
- **Neck seam on big head turns**: from about 15 degrees (the toss flick at 18) the back of the neck shows a one-pixel step where the turned head's outline meets the back line. Readable at 1x, visible at 6x. An artist's touch on `toss` frame 1 would remove it.
- **Yawn**: built from the meow frame with the jaw opened two more pixels and the head tipped back. It reads as a yawn at 1x, but the open mouth is small and the jaw edit leaves the whiskers slightly broken. A hand-drawn wide-open yawn face (one 20 x 12 px face patch per stage family) would make it unmistakable.
- **Shake**: only the head shakes (alternating 10-degree turns). A whole-body fur shake needs new body art (fluffed silhouette, motion lines in the fur) that recomposition cannot honestly produce; it is not faked.
- **Front frame of the turn**: the walk_front frame is about four pixels taller than the side frames even after the chest dip; at 10 fps it reads as the head coming toward the camera, which is acceptable, but a dedicated crouched front frame would be smoother.
- **Kittens**: the head cut works on all six stages, but the youngest kittens' heads cover their front legs, so head moves there carry a hint of the front paws; angles are kept small and it reads fine at 1x.

What I would ask of the owner or an artist (minimum): (1) one hand-touched `toss` flick frame for the adult (the kitten stages follow by recomposition); (2) a wide-yawn face patch; (3) if a body shake is wanted, a two-frame fluffed body drawing to recompose from.

## 8. Not done, and why

- Whole-body shake-off (needs new art, above).
- A stretch from the loaf (the existing stretch starts standing; a loaf-to-stand transition needs a frame the art does not have between loaf and stand).
- Leg segmentation as movable parts: the band operations covered every move made here without cutting legs out; cut-out legs would be needed for paw reaches the art has no source for.
- Lab rows on the HD 2x layer (`petHd.html`): out of scope; native does not draw HD (CAT_TAB_PLAN open question 3).
- Front/back (depth) lab rows: not meaningful for these moves (section 2.7).
- Engine use (jump arc timing, the turn flip, toy pendulum, pair staging) is CT4/CT5 work; the clips and metadata are ready for it.
