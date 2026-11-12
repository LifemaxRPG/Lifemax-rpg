# Cat house lighting plan: a much stronger light system for the native cat rooms

Status: PLAN, nothing built. Docs only. Written against `origin/codex/director-dial-controls` (head `9ec8546`). Every claim about the code below was read in this tree; things I could not check are marked **UNVERIFIED**. Paths are under `android-wrapper/native/src/main/java/com/lifemax/rpg/feature/pet/` (`room/`, `art/`, `presentation/`) unless they start with `docs/` or `resources/` (`android-wrapper/native/src/main/resources/pet/`).

Plain-language words used below: **index map** = a picture whose pixel values are palette numbers, not colours. **Ramp** = a run of palette entries from dark to light for one material. **Ordered dithering** = a fixed checker-like pattern that fakes in-between tones with only two real colours, so it stays crisp pixel art. **Quantised** = rounded to a small set of allowed steps. **Hysteresis** = a value only changes once it is clearly past the middle between two steps, so it cannot flicker on the edge. **Occluder** = something that blocks light. **Mask** = a small picture drawn over the room (mostly clear, some tinted pixels). **Bake** = compute a picture once and keep it, instead of recomputing every frame.

## 1. What exists today (read from the code)

### 1.1 The room's light

| Piece | What it does | Where |
|---|---|---|
| Time of day | One **multiply tint** over the whole picture, cats included: white 7.5 to 17 h, warm `0xFFE2C8` 17 to 19.5, ramp to blue-grey `0x808AC4` by 21.5, night until 5, ramp back by 7.5. Copied from the page (`petTint`). | `room/RoomLight.kt` `tint`; drawn by `RoomScene.paintOverlay` as one `drawRect` with `BlendMode.Multiply` (`ComposeRoomCanvas.multiply`) |
| Sky | Six colour bands per window by hour (10 keys, 0 to 24 h), stars, sun/moon, clouds. | `RoomLight.skyAt`, `RoomScene.paintSky` |
| Window light on the floor | Four parallelogram patches per window (living room, bedroom), alpha 0.16 day (`0xFFECA0`) and 0.12 night (`0x96AAFF`), leaning right going down, sliding 2.4 px per hour away from noon. Nothing at dawn and dusk. Drawn with `RoomCanvas.quad`, which is **anti-aliased** (`ComposeRoomCanvas.quad` uses `drawPath`). | `room/DepthLight.kt` `FloorLight`, `DepthScene.paintFloorLight` |
| Seam shade | Stepped dark rows at the wall/floor seam (row 117): six floor rows and three wall rows. | `SeamShade` |
| Lamp glow | A cached mask (`RoomScene.glow`) plus a bright core, only when the tint is dark: `lampAlpha` rises to 0.32 as the room gets darker and bluer. Living room only (`LAMP_X`, `LAMP_REACH` 46). There is no lamp on/off state. | `RoomScene.paintOverlay`, `RoomLight.lampAlpha` |
| Contact shadows | Ellipse of three nested steps (dithered fringe, ring, core), whole pixels, alpha 0.10/0.12/0.16, colour = the art's ink or the room's darkest floor colour; size and strength scale with the depth level. Painted for furniture (`DepthScene.paintBand`) and cats (`HouseStage.draw`). | `room/ContactShadow.kt` |
| Rim light | Code exists (`RimLight`: window side, strength by distance, warm day / cool night / orange twilight, `RIM_REACH` 150) but **`RoomStyle.live` forces `rimLight = false`**: the offset pale copy of the cat read as an outline on the phone. | `DepthLight.kt`, `RoomStyle.kt:75-83`, `HouseStage.kt:403-415` |
| Depth strengths | `DepthLevel` carries `lightStrength` (1/1.3/1.6), `rimStrength`, `seamStrength`, `shadowSize/Strength` per level SUBTLE/MEDIUM/RICH. Live rooms use RICH at best (`RoomStyle.live`, `LiveDepth`). | `room/DepthLevel.kt` |

Findings that matter for the plan:

- **Cats are not lit at all.** They get the same flat multiply as the room, which is applied after everything (cats are painted between furniture bands, then `paintOverlay`). A cat in a sun patch, under the sofa, or next to the lamp looks identical to one anywhere else. The only cat-specific light is the disabled rim copy and the contact shadow.
- **There is no weather, no season and no date** in native or in the page's pet code (searched `weather` across `lifemax_modular_v73/src` and the native tree; hits are only quest text). Native's time input is `localHour()` in `presentation/HouseStage.kt:441`: the device clock plus time-zone offset, 0 to 24, read once a second (`HOUR_REFRESH_S`). `HouseStage` already takes an injectable `hourNow: () -> Double` (used by tests), so a preview override needs no new plumbing.
- **Steadiness hazard in today's code:** the floor-light quads are anti-aliased and their x comes from a float drift of the hour. Edge coverage can change a little every second. Small, but it is exactly the kind of "pixel popping" `docs/specs/PET_SPRITE_STEADINESS.md` fought. The new design must not inherit it.
- **Cost hazard in today's code:** `ContactShadow.fringe` draws each dither pixel as its own 1x1 `fill`. Dither drawn pixel by pixel is many draw calls; baked masks (one `RoomCanvas.overlay` draw with a version number, already implemented for the lamp glow) are the cheap way and the plan uses them.
- **The furniture already has a lighting ramp idea.** `VolumeModel.Palette` arranges a piece's colours into ramps and can place a colour "N steps lighter or darker along its ramp, stopping at a jump in luminance too big to be a shade" (`VolumeModel.kt:81-120`). `VolumeRaster.shade` uses it for the sides/tops shown when the camera sways. Lit furniture faces can reuse this, not invent it.

### 1.2 Frame cost and the guard's budget

- `DepthGuard` (`room/DepthGuard.kt`) averages the last 90 frames' cost as a share of the display's frame budget (16.7 ms at 60 Hz, 8.3 ms at 120 Hz). It steps RICH, MEDIUM, SUBTLE, off (classic flat room) when the average is above **0.6** of the budget, steps back up one level only after **120 s** below **0.3**, with a 4 s hold, 45 warm-up frames, a probation back-off (up to 8x), and a cap of 6 budgets per sample. State is in memory only (`LiveDepth`).
- `DepthFrameCostTest` measures the painter's own work against a counting canvas (not the GPU). I did not run it for this plan, so I have **no number for today's frame cost**. WP-L1 records a baseline (JVM harness) before any light work, so each later package can be judged against it. The phone's real cost is only knowable on the phone (the readout in section 6).
- Memory today: the camera steps are 9 bitmaps, 3.5 MB at MEDIUM (`docs/architecture/PET_TAB_INVENTORY.md` section 12). The lamp mask and overlay bitmaps are small. A cat's adult side sheet is 576 x 1920 index pixels, i.e. 4.4 MB as an ARGB bitmap (1.1 MB as one byte per pixel).

### 1.3 How the cats are coloured, and what the art really looks like

- A cat sheet is an 8-bit grey PNG whose pixel value is a palette index (`art/SheetDecoder.kt`); `art/Recolour.kt` turns indices into ARGB with a 256-entry table from `art/CatPalette.kt`; `resources/pet/palettes.json` has the four shipped coats (ginger, gray, black, tuxedo). Frames are 72 x 48 (`anims.json`). Index 0 is clear; an index with no colour is clear.
- The header comment of `CatPalette` and the actual palette say: **10** outline; **20, 30, 40, 50** the coat ramp, dark to light; **60, 70, 80** stripes/markings (darker to lighter); **90, 100, 110** white; **120, 130, 140** pink (nose, ears, mouth); **150** pupil; **160, 170** iris; **180 to 210** details (eye shine, tongue, claws, and so on). (The brief called 60 to 80 a "shade ramp"; the palette header calls them stripes. I looked at pixels: they are the tabby/marking colour, drawn in three tones, and sit on top of both lit and shaded fur. WP-L2 settles this with a measured report, not a guess.)
- **The shading is baked in, lit from the upper left.** Printing frame 0 of `adult-ginger.png` as characters shows 5 (lightest) on the back and head top, 4 and 3 stepping down toward the belly and the far side, 2 in the deepest underside. Histogram of the adult ginger sheet: 20: 3.7k px, 30: 15.6k, 40: 41.6k, 50: 60.5k; stripes 60/70/80: 1.3k/6.4k/21.9k. The **adult black sheet uses only 20 to 50** (no stripes), so a black cat is a four-level coat. **UNVERIFIED across every frame and stage**: that is what the WP-L2 report checks.
- **The sprite is mirrored to face left** (`CatStage`, `scale(pose.scaleX * flip, ...)`), so the baked light comes from the upper right on a left-facing cat. Today a cat's lit side is decided by which way it walks, not by any light. A real light system must deal with this (section 4).
- Black is low-contrast: luminance (0.299R + 0.587G + 0.114B, my arithmetic from `palettes.json`) of black's ramp 20/30/40/50 is about 9.5, 23, 41, 88. Ginger's is about 68 to 188, gray's about 58 to 179. Black's three dark levels sit inside a 32-unit band, and its outline (`1a101c`, about 18) is **lighter** than its darkest body colour (`0a0810`). Going "darker" on a black cat is nearly invisible; going lighter shows.
- Genetic coats: the page composes the sheet and sends a grey-index PNG plus a palette (up to 64 entries) in a `coat` reply (`art/CoatReply.kt`, `PageCoatSource`). The index scheme is the same, so everything in section 3 works on the index sheet before any palette is applied. The rig (ears, tail, whiskers) composes frames from index layers recoloured with the same palette (`RigSet.kt:59` calls `Recolour.region` per layer) and caches by (set, clip, frame, grid point).

## 2. Goal and shape of the answer

"A more badass lighting system" means, in order of how much a person sees:

1. The cat is part of the light: brighter and warmer in a sun patch, darker under the sofa and in furniture shadow, cool in moonlight, warm beside the lamp, with a hint of rim from a window behind it.
2. The window light is real: patches move across the floor through the day, break around furniture, get long and orange at dusk, become a cool moon patch at night, and dim on an overcast day.
3. Furniture casts shadows, and the lamp makes a proper pool of light, a glow at night that looks pixel-art and not a smear.
4. All of it is crisp: hard steps and ordered dithering in the room's own palette, whole pixels only, nothing wobbling.
5. It costs about what the room costs today, steps itself down on a slow phone, and has an off switch.

The plan splits into a **data model**, a **cheap pixel pipeline**, and **cat lighting built on a split of the cat's art into base colour and shade**. The split comes first because every cat effect depends on it and because it is provable with a pixel-identical test, without anyone drawing anything.

## 3. Work package 1 of the design: split the cats' shading from their base colours

### 3.1 The split

Rule, total and palette-independent (a pure table in a new `art/ShadeSplit.kt`):

| Index | Family | Base index | Shade level 0..3 |
|---|---|---|---|
| 20, 30, 40, 50 | COAT | 20 | 0, 1, 2, 3 |
| 60, 70, 80 | STRIPE | 60 | 0, 1, 2 |
| 90, 100, 110 | WHITE | 90 | 0, 1, 2 |
| 120, 130, 140 | PINK | 120 | 0, 1, 2 |
| 0, 10, 150 to 210, **any other value** | FIXED | itself | 0 |

`split(IndexSheet) -> SplitSheet(base: ByteArray, shade: ByteArray)` and `join(base, shade) -> IndexSheet` with `index = base + 10 * shade` for the ramp families. Because every ramp is a run of tens and FIXED keeps unknown values as they are, `join(split(x)) == x` for **every** possible sheet value, shipped or not. `shade` can be stored at 2 bits per pixel (0.28 MB for the adult sheet) and `base` at one byte (1.1 MB).

The table above is a plan from the palette header and the pixel dump; WP-L2 first runs a **report over every shipped sheet** (adult, depth, st0 to st5, dst0 to dst5, four coats, the genetic fixtures in `src/test/resources/pet-coat/`, the rig layer sheets in `resources/pet/rig/`) listing which indices occur, so a surprise (a sheet that uses 25, a coat that puts stripes at 90) is caught before the table is trusted.

Shading is not only the coat: white patches (tuxedo, bicolour) and pink ears/nose have their own three-tone ramps and must light the same way, or a white chest would not darken in shadow while the fur does.

### 3.2 Recombine at load and draw, pixel-identical today

New `art/LitRecolour.kt`: `region(split, palette, rect, light)` where `light` carries a **shade offset** (-2..+1 for the coat, scaled down for the smaller ramps), a **tint** and a **dither phase** (section 5.2). With the neutral light (offset 0, no tint) it produces exactly `Recolour.region`'s ARGB. Until a feature flag turns lighting on, the app keeps calling `Recolour` and `CatArt` unchanged; the neutral path is the new code's own self-check.

Regression tests (new `art/ShadeSplitTest`, `LitRecolourIdentityTest`), JVM, no screenshot tolerance:

1. For every `.png` under `resources/pet/sheets/` (all coats, stages, side and depth), `join(split(s))` equals `s` byte for byte.
2. For every coat in `palettes.json` and every genetic fixture palette, every clip in `AnimTable`/`Clips.all`, every frame of every clip at both sheet kinds: `LitRecolour.region(split(s), p, frameRect, NEUTRAL).pixels` equals `Recolour.region(s, p, frameRect).pixels`.
3. The rig: for every `RigSet` fixture case (`src/test/resources/pet-rig/cases.json`), the composed frame through the new path equals the composed frame through the old path (every grid point the existing `RigComposeFidelityTest` covers).
4. `CatArt`-level: the bitmaps `CatSprites` receives with lighting off are the same pixels as before (hash every sheet of every look).
5. A property test: random index sheets (all 256 values) round-trip.
6. Palette health check run on every shipped and fixture palette: each ramp strictly brightens in luminance, and reports its spread (the number that drives section 3.4). A genetic palette that fails is not an error: it is flagged and gets a flat (no-shade-shift) lighting response.

### 3.3 What this buys

Lighting never touches the hand-drawn art. It changes (a) a cat's shade offset, (b) a tint applied to the palette, and later (c) a per-pixel direction offset (section 4). Moving a shade level is `index = base + 10 * clamp(shade + offset)` followed by the usual palette lookup: no redrawing, and it works for any coat, including a genetic coat that did not exist when the art was drawn.

### 3.4 The black coat, and genetic coats

- **Negative offsets on a low-contrast ramp do nothing visible**, so the plan does not pretend. `CoatLook` (computed once per palette) holds: the ramp spread (luminance of level 3 minus level 0), a **down gain** (how many levels a dark-going light may move: 2 for ginger and gray; 1 for black) and **two virtual levels**: level "-1" a quarter toward the outline colour, level "+4" lifted 25 percent toward the light's colour. Virtual levels exist only for non-neutral light, so identity (section 3.2) is untouched.
- For black this means: shadow and night mostly **do not make it visibly darker** (it was already near the floor of the palette; real black cats read the same), while a sun patch or the lamp **lifts it to a sheen** using the blue-violet top of its ramp plus the virtual level. This is also the honest reason the **multiply tint cannot carry the whole look**: multiplying near-black does nothing, so a black cat needs additive light (a small additive tint, section 5.3).
- Genetic and recoloured coats go through the same split because it acts on indices. Their palette arrives from the page; `CoatLook` is computed from it on the native side (no page change). A palette with a broken ramp (non-monotonic) is flagged and gets flat lighting.
- Eyes, outline and details are FIXED and never shift. One planned exception (WP-L9): at night the iris/shine indices may gain one tone from nearby light so eyes seem to catch the lamp.

## 4. The lighting model

### 4.1 Light sources as data

A new resource `resources/pet/room/lighting.json` (next to `layers.json`), read by a pure `room/LightData.kt` (strict decoder like the other pet resources, unknown field = refusal, tested). Four kinds, all in house pixels (the room's own pixels, 0 at the house's left edge):

```
ambient:  keys by hour -> { colour (multiply), lift (additive, for dark coats), mood }   // extends RoomLight's tint keys
sun:      keys by hour -> { elevation, azimuth-side, colour, strength }                   // drives patches and cast shadows
windows:  [{ room, x, glass rect, faces:"floor", panes:2x2, gap }]                         // the three existing windows
points:   [{ id, room, x, y, colour, strength, radius, kind:"lamp"|"screen"|"spark",
             when:"dark"|"always"|"night", flicker:"none" }]                               // lamps, a TV if one is ever added
weather:  { clear:{sun:1.0, ambient:1.0, sky:..}, overcast:{sun:0.35,..}, rain:{sun:0.2,..}, ... }
```

Defaults reproduce today's values (the page's tint keys, the 0.16/0.12 patch alphas, the lamp's alpha 0.32 and reach 46) so that with lighting at its **Basic** tier the room is pixel-identical to today (`RoomFidelityTest` stays green).

- **Ambient by time of day**: the existing five tint keys, plus a `lift` (small additive blue at night for dark coats and a hint of warm at dusk). Ambient stays one cheap pass (section 5.3).
- **Weather outside the window**: no weather source exists (section 1.1). Recommendation (OQ-L1): a **cosmetic weather chosen by a seeded day number** (clear most days, overcast and rain on some), with the page and the network untouched. It changes the sky colour keys, dims the sun patches (strength x 0.35 overcast, patches soft/short-edged; none in rain) and tints the ambient cool. Real weather needs location and a network call: not proposed.
- **Sun patches that move**: the window's 2x2 panes cast four patches on the floor (the shapes `FloorLight` has today), now driven by sun elevation and side: longer and more slanted in the morning and evening, near the window at noon, and (new) **onto the wall beside** when the sun is low, in a second mask. They move **one whole pixel at a time**, about one pixel per 25 minutes at today's 2.4 px/hour, with hysteresis (section 7).
- **Night and moon**: night uses the same patch shapes with a cool colour at a third of the strength, plus a soft cool glow over the window (section 4.5).
- **Lamps**: the floor lamp (`LAMP_X`, `LAMP_Y`, `LAMP_REACH`) becomes a `points` entry whose pool and glow scale with darkness, as `lampAlpha` does now. Bedroom and kitchen get no lamp art today, so no points until house art adds one (section 9).
- **Screen glow**: **no TV or screen exists in the room art** (the `layers.json` pieces are sofa, cushions, floor lamp, side table and plant, bookcase, counter, fridge, cat tree, rugs, and the item atlas has bowls, bed, box, pouf, cushion). The `screen` kind is defined so a future piece is a data entry, not code; nothing is drawn until then.
- **Light from hearts and sparkles**: `TouchParticles` emits hearts and sparkles. A `spark` point (warm pink, radius 10 to 14 px, 0.3 s) adds a one-step tint to the cat and the floor beneath it. It is cheap and charming but not needed for the first release (WP-L9).

### 4.2 Quantised to the palette

Light is never a smooth blend. Every light's reach is turned into **bands of whole pixels**, like `ContactShadow` already does:

- Strength 0 to 1 is cut into 4 steps (off, 25 percent, 50 percent, full). Step 1 is a 1-in-4 ordered-dither pattern of the light's colour at fixed low alpha, step 2 a checker, step 3 solid. A 4x4 Bayer matrix is fixed and **anchored to the house pixel grid** for room masks (so a static light never crawls).
- Colour is one of a short list per light (sun, moon, lamp warm, spark) at fixed alpha steps: three tones per light, not a continuous blend.
- Patches are painted into a **mask picture in art pixels** (`ArgbImage`), versioned, and drawn with one `RoomCanvas.overlay` call (existing, cached bitmap while the version is unchanged). Nothing is drawn as an anti-aliased shape. At the card (scale 5) and the full room (scale 9) the mask is scaled by a whole number with `FilterQuality.None`, so dither pixels are whole device blocks.

### 4.3 What a light does, per receiver

**(a) Room layers and volume faces.**
- Walls and floor (the three base images, rugs): receive patches and pools through the masks, painted before the furniture, as the floor light is today. The floor strips already move by whole pixels row by row (`rowShift`); the floor mask is drawn the same way (runs of rows with equal shift), so patches stay glued to the floor under camera sway.
- Furniture (volume faces, WP-L6): a face whose normal points toward a light takes the **next lighter colour on its ramp** (`Palette.step`), a face turned away the next darker, exactly as `VolumeRaster.shade` does for sway, but by light direction. The cached camera-step bitmaps (`VolumeSheets`, 9 at MEDIUM, 3.5 MB) are re-baked into lit copies **only when the quantised sun/ambient step changes** (a few times an hour at most, spread one step per frame with the existing `warmOne` mechanism). The sofa's seat catching a patch is the showpiece. This is the largest package and is optional after L1 to L5.

**(b) Cats.** Each cat has a **light state** computed at its position from the light model: `offset` (-2..+1 levels), `tint` (none, sun, moon, lamp, spark) and `rimSide/rimStrength`.
- *Where lit:* the cat's body centre (feet x, a point half the cat's height up) is tested against the sun-patch mask (+1), the cast-shadow mask (-1), the "inside piece" test the depth scene already has (`DepthScene.inside`/cover mask: a cat under the sofa or behind a piece, -1 or -2), the lamp's pool (+1 and warm tint), and the ambient level. The result is one small integer plus a tint id.
- *Relative to the room multiply:* the room's global multiply stays and darkens cats with the room, so the cat's own shade offset is only the **local difference** from ambient. That keeps cat and room consistent without a per-object ambient pass and is why the global multiply can remain the ambient pass (section 5.3).
- *Tint:* the cat's palette is blended 10 to 18 percent toward the light colour at bake time (warm in sun and by the lamp, cool in moonlight). Black coats take the additive lift of section 3.4.
- *Rim light:* the old offset-copy rim is rejected (the owner saw it as an outline). Replacement: **the rim is inside the sprite**. The first one or two opaque pixels on the lit edge of the silhouette, facing the window or lamp behind the cat, move **one level lighter** (a dithered-lighter edge, not a new outline). It needs the silhouette edge, available from the index sheet; it is part of WP-L5 and off unless the owner accepts it from a phone screenshot (OQ-L6).

**(c) Cast shadows.**
- *Furniture on the floor:* each standing piece has a footprint (house x range, `baseY`, depth in floor rows from `layers.json`/`volume`) and a height. A shadow is its footprint swept along the sun's floor direction by `height / tan(elevation)`, clipped to the floor and painted as a dithered dark band into the shadow mask, under the furniture (and over the sun patch, so a patch is cut by the sofa or the lamp pole). Night uses the moon with a short, weak shadow; overcast removes it. There is no ray tracing: pieces are boxes.
- *Cats:* the contact shadow stays; its direction and length follow the same sun (an offset toward the lit side and a longer shape at low sun), still whole-pixel ellipses.
- *Cat in a patch or under the sofa:* handled by the cat's light state above; its own shadow darkens in the patch (alpha up one step) and lightens in a pre-existing shadow.
- *Onto walls:* a lamp pole or plant shadow on the wall from the window light is **not** planned; it needs height-aware wall projection for small gain.

**(d) Glow and bloom, cheap.** No blur. A glow is a **pre-baked mask**: a few concentric dithered rings of the light's colour, drawn with an additive (plus) blend, which needs one small addition to `RoomCanvas` (a `glow(mask, version, x, y)` using `BlendMode.Plus`). Used for the lamp (replacing the single `glow` mask with a ring set), the window at night (a cool halo), and the lamp core. Three small draws; the bitmaps are tiny (the lamp's is 92 px wide, `LAMP_REACH` 46).

### 4.4 True directional lighting versus cheap options

True directional lighting needs a **normal** (which way the surface faces) at every pixel and the light's direction. The cat art has neither; the furniture has depth maps (front and back per column in `volume/*.png`) but no normal map.

| Option | What it needs | What it gives | Verdict |
|---|---|---|---|
| A. Offset only | The shade split (section 3). One integer per cat. | Brighter in sun, darker in shadow, same shading form. Keeps hand-drawn art exactly. | **Build now (WP-L3).** |
| B. Row-span direction (pseudo-normal) | A derived "u" map: for each opaque pixel, its horizontal place within its row's span of the silhouette, -1 left to +1 right, quantised to 3 values. Derived by code from the sheet, no new art. | When the light comes from the side that disagrees with the baked upper-left, move the shade by `round(k * u * lightSide)` relative to the original, so form still reads as rounded. Gives mirrored (left-facing) cats consistent light, and a window behind a cat a believable rim. | **Build now (WP-L5).** The key idea is **delta lighting**: result = original level + (new lighting - lighting the art was drawn with). With the light unchanged the delta is zero, so the art is preserved. |
| C. Per body-part ellipsoid | Segmenting the body into head, torso, legs, tail. Only the ears, tail and whiskers are separate layers today (`RigKind`); the body is one piece. | Correct round shading per part. | **Leave.** Needs new art or a segmentation pass; B covers most of the look. |
| D. Inflated normals + full per-pixel N.L | A distance-transform "pillow" height field from the silhouette, normals from it, quantised N.L. | Fully directional, any light angle, front/back views too. | **Leave, optional WP-L10.** Build only if B looks wrong on the phone. Needs a separate decision on the depth-sheet (front/back view) cats. |
| E. Authored normal maps | New art per frame. | Best. | **Do not do.** |

Mirrored cats under option A alone: a cat facing left is lit from the upper right (section 1.3). With option A, compensate by **flattening**: when the light side disagrees with the (flipped) baked side, compress the shade toward the middle (`s' = 1 or 2` on the coat ramp, weighted 0.5) rather than invent a form, then let the rim and tint carry the direction. Option B replaces the flatten with a proper delta. The depth-sheet views (front and back) have symmetric light; use offset only.

## 5. Performance

### 5.1 Draw passes

Frame passes today (living room, RICH): sky bands (fills in the glass), 3 base strips with edge fills, seam (9 fills), floor-light (8 anti-aliased quads), rugs, about 200 furniture strips (the `DepthFrameCostTest` count) with a contact shadow per standing piece, the cats (one sprite each, or two with the rim), cat contact shadows, one full-screen Multiply, lamp mask and core.

Planned, light level FULL:

| Pass | Today | Planned |
|---|---|---|
| Floor light | 8 AA quads | 1 mask overlay (floor sun + moon patches, cut by furniture shadows) |
| Furniture shadows | per-piece ellipse `ContactShadow`, pixel fills | kept (ellipse for the contact) plus cast shape in the same shadow mask: 0 extra draws |
| Wall patches | none | 1 mask overlay (only when the sun is low) |
| Ambient | 1 full Multiply | 1 full Multiply (unchanged), plus 1 small additive lift draw at night for dark coats |
| Glow | 1 lamp mask + core | up to 3 additive glow masks (lamp, window at night, core) |
| Cats | 1 sprite (+1 if rim) | 1 sprite from a lit bitmap (no extra draw); rim is inside the bitmap |
| Net | | about 8 quad draws removed, about 7 small mask draws added (floor 1, wall 1, night lift 1, glow 3, plus the shadow mask 1); **no new full-screen blend pass** |

**UNVERIFIED**: GPU cost. The only measured thing to date is the JVM painter cost. The additive and multiply draws cover at most the room rect: at the full room (scale 9, 1080 x 2340 = 2.5 M device pixels) one more full-screen blend would be a visible cost; the plan adds none and keeps masks to the floor band (rows 118 to 144, about 26 of 144 rows).

### 5.2 Cats: variants, not per-frame filters

A cat is drawn from one bitmap with `FilterQuality.None`. The lit look is **baked**, never a per-frame `ColorFilter`, because a colour filter cannot move a pixel between ramp levels and a per-frame colour filter allocates and re-uploads:
- **Bake unit = one clip row** (`anims.json`: a row of 6 to 8 frames at 72 x 48 is about 576 x 48 = 110 KB as ARGB), keyed by (sheet, row, light key). A clip row is baked on first use for a light key, in the background if it is not already ready (until it is, the previous key's row is drawn: no pop). A light key is (shade offset -2..+1, tint id 0..4, rim side 0/+1/-1) with at most about 6 resident per cat, least recently used first. A cat's resident cost is a few hundred KB, not several 4.4 MB sheets. Whole-sheet rebakes are avoided.
- The side sheet is split once at load (section 3), the split arrays held for a cat on screen: about 1.4 MB for an adult side sheet (1.1 MB base + 0.28 MB shade); the ARGB source bitmaps for neutral light remain what `CatArt` already holds.
- **Rig**: composed frames are cached by (set, clip, frame, grid point); the light key joins the key (`RigCache`) and the caches are size-capped. Rig layers are split once the same way.
- Bake cost: one clip row is 27.6 k pixels of table lookups, a fraction of a millisecond on a phone (**UNVERIFIED**; WP-L3 measures it); a whole-sheet bake (1.1 M pixels) is only the fallback.
- A change of light key for a cat is rare by design (section 7), so bakes are rare.

### 5.3 Ambient: stay cheap

The ambient light remains **one Multiply**, exactly the page's tint, as the room fidelity tests require today, with a small additive "lift" draw added only at night. This is cheaper than baking light into every layer and keeps the room pixel-identical at the Basic tier. Baked lit furniture (WP-L6) is the only place where ambient and local light mix in the bitmaps.

### 5.4 Memory (phone)

| Item | Size | Notes |
|---|---|---|
| Light masks (floor sun/moon+shadow, wall, glow rings) | about 0.07 MB (floor band 576 x 30) + 0.1 MB (wall) + small glow bitmaps: **under 0.5 MB** | art pixels; scale is applied at draw time |
| Light key variants per cat | about 0.1 MB x (rows used) x (keys) = **about 1 to 3 MB per cat** typical | LRU cap per cat; two cats on the card |
| Split arrays per cat | **about 1.4 MB** adult side (smaller at kitten stages) | kept only for cats on screen |
| Lit volume bitmaps (WP-L6) | **+3.5 MB** at MEDIUM (a lit set replaces the unlit one at the same steps; the unlit source is the pristine art already kept) | released with the room (`HouseStage.trimDepth`) |

All **UNVERIFIED** on the phone; `RoomAllocationTest` extends to assert no per-frame allocation with lighting on.

### 5.5 Extending the guard

`DepthGuard`'s ladder is RICH, MEDIUM, SUBTLE, off. Plan: add an independent **light tier** the guard sheds first, because lighting is the cheaper thing to lose without the owner noticing the room "flatten":

`FULL` (everything above) , `LITE` (ambient + sun patches + cat shade offset; no cast shadows, no glow rings, no rim), `BASIC` (today's look: tint, lamp glow, floor patch, contact shadows).

Guard rungs, in order down: RICH+FULL, RICH+LITE, RICH+BASIC, MEDIUM+BASIC, SUBTLE+BASIC, classic. Up in the reverse order, the same calm (120 s below 0.3), the same hold, back-off and probation. The thresholds (0.6 down, 0.3 up) do not change. `DepthLevel` is not given a light tier of its own (its `lightStrength` stays a multiplier); a small `RoomTier(depth, light)` pairs them and `RoomStyle` takes it. `DepthGuardTest` gains the new ladder with the fake clock. The user's setting (section 8) sets the ceiling the guard can rise to.

### 5.6 Developer readout

In developer mode only (the page's developer mode already reaches native as a projection flag for `pet.demo.v1`; the same route can carry a lighting-readout flag, **UNVERIFIED that no other route is already used**, confirm in WP-L8): a one-line overlay on the Cat tab with: hour used and its source (clock or preview), weather, light tier and guard load (`DepthGuard.load`, `changes`), light-mask versions (so a mask rebuild is visible), cats' light keys, bakes per minute and the last bake in ms, resident variant count and approximate MB. Never logged and never leaves the phone, as `LiveDepth` already does.

## 6. Time and weather inputs, and the page/native boundary

- **Clock.** Native owns the real clock for the room (`localHour()`); the page does not send an hour. No change.
- **Weather.** Does not exist anywhere. Cosmetic seeded weather (OQ-L1) is native-only and deterministic from the local date, so a day always looks the same; it never goes into the page's save.
- **Preview override.** A developer-only **time-of-day slider** on the Cat tab (0 to 24 h in 15 minute steps, plus play/pause that runs 1 hour per second, a weather picker, and "back to now"). It feeds `HouseStage`'s existing `hourNow` and a `weatherNow`; in memory only, reset when the screen closes. The owner scrubs dawn to night on the phone and sees the cats and the patches change in real time. The same hooks drive the screenshot tests (fixed hours: 6, 12, 18.75, 20.5, 23, matching the existing `pet-room/house-*.png`).
- **Saved settings** live with the page (the existing Native Cat switches ride the page's Settings; `docs/architecture/SETTINGS_INVENTORY.md`). Proposal: **Settings, Cat, "Cat lighting": Full / Simple / Off**, default **Full**. Simple = the LITE tier (no cast shadows, glow rings, rim); Off = Basic, exactly the room as it is today. The setting is the ceiling for the guard. How the choice reaches native (a projection field, as the reduce-motion flag does) is mirrored from the nearest existing switch in WP-L8 (**UNVERIFIED** which one; I did not trace the plumbing to the end).
- Per `CLAUDE.md`: every package that changes what the owner sees adds short bullets to `lifemax_modular_v73/src/data/update-history.json` (see `docs/UPDATE_HISTORY.md`), only when released, not for unfinished work.

## 7. Steadiness: lighting must never shimmer

The cat steadiness work (`docs/specs/PET_SPRITE_STEADINESS.md`) established the rule: the drawing changes only when something meant to move has moved a whole step. Lighting follows it:

1. **Whole pixels only.** No anti-aliased shapes (the current `FloorLight.quad` goes). Light masks are art pixel pictures drawn at an integer scale with `FilterQuality.None`.
2. **Quantised light, held.** Every light quantity a draw reads (a patch's x, sun step, ambient step, a cat's offset and tint) lives on a small grid and uses the existing `SteadyStep` hysteresis (a step changes only when the value is a quarter step past the middle). A patch moves one pixel at a time, about every 25 minutes.
3. **A cat's light key changes slowly:** its light is a target that is smoothed by a spring and then held on `SteadyStep`; a new key must persist 0.25 s to apply and cannot change twice within 0.75 s. A cat standing on the edge of a patch, or hovering by 0.3 px, changes key at most once.
4. **Dither anchors.** Room masks: anchored to the house pixel grid (static, so a still light is still). Cat bakes: anchored to the **sprite's frame pixel**, not the screen, so a still cat is steady and a moving cat's dither moves with the cat (its art frames change anyway; the probe charges those to ART).
5. **Ambient changes in steps.** Tint is recomputed once a second today; the plan quantises it so a step is at most 2 per channel and applied at most once a second. A hard step is invisible at that size, and two lights never change in the same frame by design where avoidable.
6. **Versioned masks.** A mask is rebuilt only when its quantised inputs change (its version); the pixels in between are byte-identical frame to frame.
7. **No per-frame floating colour.** Colours are looked up from the bake; none are blended per frame.
8. **Light bakes never replace a bitmap mid-frame** and a pending bake leaves the previous key on screen.

Tests (all JVM, extending the existing probe):
- `SteadinessProbe`/`SteadyCatTest` gain a cause **LIGHT** (a light-key change charged like ART, RIG, BREATH, BODY). The invariant stays **0 unexplained steps** for: a resting cat in a sun patch, under the sofa, beside the lamp, at night, for each coat (black, ginger, gray, tuxedo, calico and silver genetic fixtures), card and room scale.
- Edge test: a cat 0.3 px either side of a patch edge for 8 s changes key at most once, with no a-b-a.
- Day-sweep test: hour 0 to 24 in one-minute steps; diff the room frame each step; no frame may change a pixel that returns to its old value two steps later (no a-b-a), and the changed-pixel count per step stays under a bound (one patch pixel column plus at most one ambient step).
- `RoomStillnessTest` extended: lighting on, the room at rest, frame to frame byte-identical.
- `RoomAllocationTest`: no allocation per frame with the light tier FULL.
- Identity tests of section 3.2, plus: the Basic tier equals today's room (`RoomFidelityTest`).
- `DepthGuardTest`: the new ladder.

## 8. Work packages, in dependency order

Sizes: **S** about a day of agent work, **M** a few days, **L** a week or more. Each package keeps the room at its Basic tier (today's look) until the owner turns the next piece on, so nothing ships half-lit. Screenshots follow the existing practice (`LiveDepthCard*`, `LiveDepthTab*`, new `Light*` images at fixed hours 6, 12, 18.75, 20.5, 23).

| WP | Size | What | Depends on | Tests and screenshots | Owner checks on the phone |
|---|---|---|---|---|---|
| **L1** | S | **Baseline and readout.** Record `DepthFrameCostTest` per level and allocation numbers; add the developer readout skeleton (hour, tier, guard load, bake ms placeholders); add the hour/weather injection (`weatherNow`) beside `hourNow`. No visual change. | none | cost baseline written into this plan's appendix; `LiveDepthSceneTest` unchanged | the readout appears only in developer mode; cat room runs as before |
| **L2** | M | **Shade split.** `ShadeSplit`, the all-sheets index report (corrects the table of 3.1 if the data disagrees), `LitRecolour` with the neutral path, `CoatLook` and the palette health report. Not wired into the app. | none (parallel to L1) | `ShadeSplitTest`, `LitRecolourIdentityTest` (every coat, stage, clip, frame, sheet, rig case, genetic fixtures; byte identical); report file | nothing visible |
| **L3** | M | **Cats take light: offset and tint.** Wire the split into `CatArt`/`CatSprites` with the clip-row variant cache and the rig key; `CatLight` computes the offset from ambient, the cached sun mask (first version: the floor patches in house pixels) and the existing inside-piece test; `SpriteSteady` holds the key. Behind a developer-only flag at first. | L2, L1 | `CatLightTest` (offsets), steadiness probe with LIGHT cause, bake-cost and memory checks, 6 new screenshots (four coats in patch / out of patch / under sofa) | a cat walking through a window patch gets brighter and warmer, then returns; black cat in the patch shows a sheen; nothing flickers; resting cat still calm |
| **L4** | M | **Light data and masks.** `lighting.json` + decoder; replace `FloorLight` quads by the whole-pixel dithered **mask** (same shapes, same colours at Basic); sun path by hour with 1-pixel steps and hysteresis; moon patch; `RoomCanvas.glow` (additive) and the lamp ring mask. Basic tier is pixel-identical (by test); FULL differs. | L1 | `LightDataTest`, `FloorMaskTest` (shape equals old quad within a pixel at Basic), `RoomFidelityTest` (Basic), day-sweep steadiness test, `RoomStillnessTest` | scrub the day on the preview slider: patch slides in clean pixel steps, no edge crawl; the lamp glow looks like pixel art |
| **L5** | M | **Cast shadows, side-aware cats and rim.** Furniture footprint shadows in the floor mask (cut patches), sun-aware cat shadow, option B (row-span "u" map and delta lighting), and the in-sprite rim (off by default, OQ-L6). | L3, L4 | `CastShadowTest` (footprint sweep), `RowSpanTest`, mirrored-cat consistency test, screenshots at 6/12/18.75/23 h | sofa shadow lies correctly; a left-facing cat in a right-hand patch is lit on the right; rim reads as light, not outline |
| **L6** | L | **Lit furniture faces.** Re-bake the volume camera steps by sun/ambient step with `Palette` ramp steps; seat of the sofa and the top of the cat tree catch the patch; baked on a spread-out schedule. **Optional**; do last if L1 to L5 are good. | L4 | `LitVolumeTest` (identity at neutral, hash of every piece), memory check, screenshots | sofa and tree tops warm in the afternoon; no hitch when the hour steps |
| **L7** | S | **Guard tiers.** `RoomTier(depth, light)`, the new ladder, readout of the tier and load. | L4 (L3 for cats) | `DepthGuardTest` ladder with fake clock; `LiveDepthSceneTest` per tier | on a deliberately slow setting (developer "force load") the room sheds glow and shadows first and keeps the cat shading |
| **L8** | M | **Settings and the preview slider.** Settings "Cat lighting" Full/Simple/Off (default Full) mirrored from the nearest existing switch; developer time-of-day slider and weather picker; update-history bullets. | L3 at least; L4 for the full effect | `PetSettings` round-trip test, view model test, screenshots of the slider | the owner scrubs dawn to night; toggles Off and sees today's room |
| **L9** | S | **Extras:** cosmetic seeded weather in the room, spark/heart point lights, eye catch-light at night. Each is one data entry plus a small test. | L4, L3 | per-feature tests, screenshots | rain dims the room; a heart gives a brief warm flash on the cat |
| **L10** | L | **Optional true directional (option D):** inflated normals, per-pixel N.L, front/back sheets. Only if the owner finds B lacking. | L5 | `NormalMapTest`, golden images | any-angle light on any view |

Suggested release order: L1, L2, L3 (hidden), L4, L7, L8 (turns it on), then L5, L9, L6.

Merge risk by package: L3 touches `CatArt.kt`, `CatSprites.kt`, `SpriteSteady.kt`, `SpritePlan.kt`, `RigSet.kt`/`RigCache.kt` (the places the sprite steadiness and the sprite audit also touch); L4 touches `RoomCanvas.kt`, `ComposeRoomCanvas.kt`, `DepthLight.kt`, `DepthScene.kt`, `RoomScene.kt`; L6 touches `VolumeSheets.kt`; L7 touches `DepthGuard.kt`, `RoomStyle.kt`, `LiveDepth.kt`. None touch `NativeApi.java`.

Per `CLAUDE.md`, the owner should be told before L3, L5 and L6 (where a design question is open and a logic merge may be hard) that a stronger model is worth using for the work.

## 9. Art list

Procedural and honest (no drawing needed):
- Sun and moon patches, glow rings, dither patterns, cast shadow shapes, lit tint colours, cat shade offsets, rim: all code and `lighting.json`.
- The window pane shapes already exist (the 2x2 glass, `GLASS_X/Y/W/H`); patch shapes derive from them.
- Weather skies are the existing sky bands with different keys.

Needing art only if the owner wants more than this:
- **Window light masks** per room if a window gets a different frame (the bedroom and kitchen windows share the living room's shape today; a mullion pattern or curtain would be a small mask each). Not needed now.
- **Lamp cones** (a vertical downward cone from the lamp's shade): a 20 x 40 px mask if the owner wants a visible cone; otherwise the ring glow is enough.
- **Bedroom and kitchen lights** (a bedside lamp, an under-cabinet light): new props, tied to the house-expansion plan.
- **A TV or screen** prop for the `screen` kind: no art exists; nothing is built until one does.
- Option E (hand-made normals) is explicitly not asked for.

## 10. Assumptions about the other cat agents' work, and where we could collide

I could not read their plans (none of them is in `docs/specs/` on this base), so these are assumptions to check when they land:

- **Camera and depth skew work** (changing how the room is projected, `RoomCamera`, `Parallax`, `DepthSway`): I assume the floor is still drawn as runs of rows with whole-pixel shifts (`rowShift`) and furniture keeps its footprint (`baseY`, `depth`, house x) in `layers.json`. A change to skew or to how the floor shears would need the floor light mask to follow the same row runs. Conflict area: `DepthScene.kt` and `Parallax.kt` (L4/L5). I read `rowShift` from `Parallax`; if its signature changes, the floor mask draw changes with it.
- **Exploring** (the cat reaching new places): I assume more positions for cats means `CatLight` samples light at any `pose.x, pose.y, pose.z, pose.surface`. New areas need their own windows/lamps in `lighting.json` (data), not code. Conflict: if exploration adds a new coordinate space or a second house layout, `RoomLayout.windows` and the mask extents must follow.
- **House expansion plan** (more rooms/furniture): I assume the house stays a strip of 192 px rooms and that new pieces arrive as `layers.json` entries with `volume` maps (the lighting data lists windows and lamps by room). New rooms mean new `lighting.json` entries and a longer mask; `RoomLayout.HOUSE_WIDTH` is the only size I use. Conflict: `layers.json` format changes, and `HOUSE_WIDTH` assumed 576 in the memory table (section 5.4); recheck.
- **Sprite audit** (art fixes in the sheets, `RestingFloor`-style mends): I assume sheets keep the index scheme of section 3.1. A fix that adds new index values (new detail colours) is safe (FIXED fallback), but one that changes the meaning of a ramp (for example puts stripes on a different run) breaks the table: the L2 report fails loudly then. I also assume `RestingFloor` still mends frames at load; the split is taken **after** mending so the mended pixels are the ones lit.
- **Shared files** (from `CLAUDE.md`'s hot list): this plan does not touch the shared shell, `NativeTabKit`, `ComposePlugin` or Life Director. It touches pet room/art/presentation only.

## 11. Risks and open questions

| ID | Question or risk | Recommendation | Owner decision? |
|---|---|---|---|
| OQ-L1 | Weather: none exists. Options: none; a seeded cosmetic weather; real weather (location and network). | Seeded cosmetic weather (WP-L9), off the page and network; real weather not proposed. | **Yes** |
| OQ-L2 | Cats lit by a global multiply plus a local offset (the plan) versus lighting the room art in the bitmaps (everything baked per ambient step). | Local offset on top of the page's multiply; bake only the furniture (L6). Keeps the room identical at Basic and the cost low. | No (technical) |
| OQ-L3 | Default for "Cat lighting". | Full, with the guard to shed it. | **Yes** |
| OQ-L4 | Is the shade table (60 to 80 stripes, 90 to 110 white, 120 to 140 pink) right for every sheet? | L2 report decides; table corrected before use. | No |
| OQ-L5 | Black cats barely darken. Accept it, or add a per-coat "night tint" so black cats pick up blue-violet? | Accept it for shadow; add the small additive lift at night and a sheen in light. Owner views a black cat at night and in a patch on the phone. | **Yes, after seeing it** |
| OQ-L6 | In-sprite rim light: build? | Build behind a flag; keep off until the owner sees it on the phone (the old rim was rejected). | **Yes** |
| OQ-L7 | Mirrored (left-facing) cats are lit from the right today. Flatten (option A) or delta lighting (B)? | Flatten first (L3), B in L5. | No |
| OQ-L8 | Does the phone hold the extra bakes and masks within the guard's budget? Numbers are unmeasured. | L1 baseline and the readout answer it; the guard tier sheds lighting first. | No |
| OQ-L9 | `minSdk` and whether a shader (AGSL) palette lookup is available. | Not needed: the plan bakes on the CPU. **UNVERIFIED** if ever wanted. | No |
| OQ-L10 | The developer-mode flag route to native. | Use the route `pet.demo.v1` uses; confirm in L8. | No |
| OQ-L11 | Is the sun-patch drift (2.4 px/hour) and the day/dusk/night cut-offs (7.5 to 17.5 h) to be kept? | Keep; the sun table may add dusk/dawn patches (low, orange) which the page's room lacks. This makes the native room differ from the page's. | **Yes** (a deliberate move away from the page) |
| Risk | Dither crawl when the camera sways. | Masks are in house pixels and drawn row run by row run like the floor; day-sweep and sway tests. | n/a |
| Risk | Lit bakes cause a hitch when a cat's key changes. | Clip-row bakes, previous key stays until ready; tests count bake ms. | n/a |
| Risk | Light changes reintroduce shimmer. | Section 7 rules and tests; the invariant is 0 unexplained steps. | n/a |

## 12. Owner decisions in one place

1. Weather: seeded cosmetic, or none (OQ-L1).
2. Default Cat lighting level: Full, Simple or Off (OQ-L3).
3. After seeing it on the phone: black-cat handling (OQ-L5) and the in-sprite rim (OQ-L6).
4. Whether the native room may differ from the page's room (dusk and dawn patches, moon patch) (OQ-L11).
5. Whether to do WP-L6 (lit furniture) and WP-L10 (full directional lighting) at all after L1 to L5.
