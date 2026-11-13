# Cat room: continuous 3D furniture (spike and plan)

Status: spike done, one prototype route built behind a developer switch (NativeApi 164), default OFF.
Nothing changes for users. Background: `PET_ENGINE_PLAN.md` sections 5.1 and 5.2 (solid furniture,
camera steps, the cross-fade of build 510), `CAT_TAB_PLAN.md` CT9.

## 1. The problem, measured

Each solid piece is drawn from one of 9 cached camera steps (MEDIUM). A step moves the deepest back
by at most one art pixel, so as the camera pans a side grows one art pixel at a time: 5 to 9 device
pixels on the phone. The cross-fade (build 510) blends two neighbouring steps, but a blend of two
pictures that differ by a whole art pixel still reads as a pixel-wide side fading in, not as a
surface turning. The cause is the quantisation of the geometry to art pixels, not the number of
steps: more steps at art resolution would change nothing, because neighbouring steps would give
identical pictures.

Continuous motion therefore needs the geometry placed at device-pixel precision while the art keeps
its art-pixel texels. Every option below is judged on that.

## 2. Options compared

### A1. `graphicsLayer` (rotationX/Y, cameraDistance)

Rotates a whole composable as a flat card with true perspective. A flat card shows no sides, so each
face would need its own composable (a layer per face, per piece), ordered by composition order. The
room is one `DrawScope` painted back to front by `DepthOrder`, with cats between the bands of a
piece; splitting it into composables breaks that order (a cat inside the cat tree, on the sofa
behind the cushion), and the faces of a pixel-art piece are not planar rectangles (the sofa has
arms, a seat that runs back, a backrest; the tree a round trunk). Perspective also scales texels
unevenly (mixels). Rejected.

### A2. Canvas affine/perspective quads (`Matrix.setPolyToPoly`, `android.graphics.Camera`, `drawVertices`)

Project each face of each solid as a textured or flat quad every frame. The projection we use is
oblique (parallel), so faces are parallelograms and an affine `Matrix` suffices. Workable for a box
(fridge: 3 faces), but our pieces are column models (a depth per painted pixel). Decomposed into
faces, the sofa is several hundred side and top runs; drawn as fills that is thousands of draw calls
a frame for the house, or one `drawVertices` per piece (fast on the GPU, but no anti-aliasing,
unknown coverage in Paparazzi's layoutlib, and painter's order between the faces of a column model
under an oblique view is not trivially correct without a depth buffer, which Canvas does not have).
The outline, crevice and slope rules of `VolumeRaster` would all have to be re-expressed as
geometry. Feasible, but the most work and the most risk of the Compose options.

### B. A GL surface (raw GLES, Filament, SceneView) hosted in Compose

- **Cost.** Filament's AAR is about 3 to 4 MB per ABI (more with gltfio and the material compiler
  outputs); SceneView adds ARCore-free 3D on top. Raw GLES costs no binary size but is the most code.
  Either needs models: our pieces exist only as depth maps, so meshes would have to be generated
  (marching the columns) or modelled by hand for all 13 + 10 pieces.
- **Verification.** Paparazzi renders through layoutlib with no GPU: a `SurfaceView`/`TextureView` is
  blank in screenshots. CI could only test the CPU side (mesh generation, sorting); every picture
  would be checked on the phone by eye. Today the room has 33 + 16 screenshots and pixel-exact JVM
  tests; that would be lost for the furniture.
- **Pixel-art look.** Achievable (render to a low-resolution target, nearest upscale, flat-shaded
  palettes, posterised lighting), but it is a re-art: the painted fronts would become textures on
  meshes, and the outline rule becomes a shader. Days of tuning on the phone.
- **Cats.** The cats are 2D sprites drawn by Compose. Sorting them with GL furniture needs either the
  cats inside GL (billboards with depth: the whole cat rig, lighting and recolour pipeline moved to
  GL) or several stacked surfaces split at every cat (not workable with cats inside the tree).
- **Touch.** Picking would go through a GL ray cast instead of today's 2D boxes.
- **Lighting** (`feature/pet/light`). Would be redone as shaders; the CPU recolour of the cats
  (LitRecolour) would not apply to GL furniture.
Rejected for this art style and this verification setup. It would be the right call only for a
free-rotating camera, which the room does not have.

### C1. Re-render the column raster every frame at the live yaw

`VolumeRaster` is already a real 3D renderer (an exact ray cast through the columns). Running it per
frame at the continuous yaw removes the steps' quantisation of the yaw but not of the pixels: it
still outputs art pixels, so the side still grows an art pixel at a time. At device resolution (4 to
6x supersampled) it would be smooth but costs 16 to 36x the 1.3 ms a step costs on the JVM, plus a
bitmap upload per frame. Rejected.

### C2. Sprite stacking from the column models (RECOMMENDED, prototype built)

The classic pseudo-3D pixel-art technique: the piece is cut into thin slices of depth (two per floor
row); each slice is an ordinary bitmap made once from the column model, holding the pixels whose
column reaches that depth, coloured by the raster's own rules (front = painted pixel; a rim = side or
top shaded along the piece's ramp; slopes keep the painted colour; crevices darker; the far edge
outlined). Each frame the slices are drawn back to front, each moved by `depth * (vx, vy)` for the
piece's yaw as it is now, at the nearest device pixel. Neighbouring slices are 0.07 to 0.14 art
pixels apart, under one device pixel, so a side grows a device pixel at a time. The texels stay
whole art pixels and the palette is the piece's own; only where the sides end is finer than an art
pixel.

Why it fits:
- the slices are exactly the depth bands `DepthOrder` already sorts by, so the cats go between them
  with no change (a cat on the seat, behind the arm, inside the tree);
- at rest it is the painted sprite exactly, and at a camera step its silhouette is the raster's to
  within one art pixel (tested);
- it uses only `drawImage` with integer device offsets: Paparazzi and the JVM software canvas render
  it, so screenshots keep covering the furniture;
- no per-frame rendering: stacks are made once (about 2 ms a piece) and drawing allocates nothing;
- no new dependency, no binary size;
- the lighting system can light slices the way it lights other bitmaps (a lit copy per hour band, as
  `LitSheets` does for cats), because slices are plain bitmaps in the piece's palette.

## 3. The prototype (built)

- `feature/pet/room/SolidStack.kt`: `Solid3d` (OFF, SOFA = sofa + its two cushions, ALL),
  `StackTuning` (2 slices a row, outline at most 1 px), `StackView` (the projection as pure
  arithmetic: `vx = -yaw * depthGain`, `vy = -pitch * yaw^2`, slice offsets, outline width), and
  `SolidStack` (slices in two shadings, left and right sides, plus inked copies, packed into one
  bitmap; `paintBand` draws one depth band).
- `StepMix.stack(p, bands)`: a stacked piece's bands replace its step bands, with the same sort keys
  (`sortFront - (band + 0.5)`), so `DepthOrder` is untouched.
- `DepthScene.solid3d`: chooses which pieces are stacks; each is drawn at its continuous yaw (the
  same yaw the steps would approximate: idle + perspective against the screen's middle); a cat on a
  stacked piece takes the same continuous yaw.
- `RoomCanvas.stack(...)` takes a fractional house position; `ComposeRoomCanvas` places it at the
  nearest device pixel (own bitmap cache, `MAX_STACKS` slots); the default (tests, software canvas)
  rounds to the house pixel.
- Developer switch: `LiveDepth.solid3d` (in memory, OFF at every launch), cycled by the
  "3D furniture: off / sofa / all" button in the developer panel under the Cat tab
  (`LightingDevPanel`), shown in the depth readout ("3D sofa").
- Tests (`SolidStackTest`, pure JVM): rest picture equals the sprite for every piece and both
  shadings; every colour from the piece's own palette; at each of the 9 MEDIUM steps no pixel more
  than one art pixel off the raster's silhouette (worst agreement 0.81, the 1 to 2 px lamp stem; the
  big pieces 0.96+), outline pixels 10,339 vs the raster's 10,234; the sofa's left edge swept in
  yaw steps of 0.005 at 6 device px per art px moves at most 1 device pixel at a time (17 device px
  of travel) where the steps jump 6; the room at rest is pixel-identical with OFF, SOFA and ALL; a
  cat on the couch is still drawn after every sofa band and the sway shows the side.

### Measured on the JVM (`STACKCOST`, MEDIUM, live camera following a cat across the house)

| | off (steps) | sofa | all |
|---|---|---|---|
| painter (ordering + calls, counting canvas) | 0.06 to 0.11 ms | 0.02 ms | 0.02 ms |
| draw calls a frame (all, incl. floor) | 1116 | 1111 | 1023 |
| software canvas, 6x | 6.0 to 6.8 ms | 6.1 to 6.5 ms | 7.2 to 8.1 ms |

The painter is cheaper with stacks (no cross-fade bookkeeping, no re-sorting when a step changes).
Making all 13 stacks: about 25 ms once (made lazily the first time a piece is stacked, on the main
thread in the prototype). Bitmaps: 7.0 MB for all 13 stacks (512-wide atlases, both shadings plus
inked copies) on top of the steps' 3.5 MB, which stay made while the prototype is on.

**Overdraw** is the cost to watch: every slice is drawn whole, so a frame blits about 400,000 art
pixels of slices for all 13 pieces, 14.8x the sprites' own area (at 6 device px per art px and a
third of the house on screen, about 5 million device pixels a frame). The steps blit about 1x. On a
mid-range GPU that is likely fine at 60 Hz but it is exactly what only the phone can say (section 6).

## 4. Known gaps of the prototype (tuning, not blockers)

- The tops of slanted faces show faint one-device-pixel lines where a top face's slices step (the
  slope rule is decided per slice rim, not per ray); a small rule change or 4 slices a row.
- A cat on a stacked piece still moves in whole art pixels (the cat's draw takes an integer shift),
  so it can sit up to half an art pixel off the seat as the camera turns. Fix: a float shift for the
  cat layer, rounded to device pixels like the slices.
- The floor and wall still move in whole art pixels (`Parallax.shift`); against a continuously
  turning sofa the floor's steps may now be the thing that reads as choppy. Same fix (device-pixel
  offsets for the floor runs), separate work package.
- The stacks are made on the main thread when the switch is turned on (about 25 ms for ALL): one
  hitch, developer-only.
- The steps are still warmed and kept while pieces are stacked (memory: 3.5 + 7.0 MB).
- The outline is per slice ("ink" copy drawn for slices within the outline width of their back), so a
  piece whose columns end at very different depths outlines with the deepest back of each slice.

## 5. Plan to convert everything (if the owner likes the prototype)

Estimates in passes (one agent session each), Sonnet unless marked.

1. **Rim-only slices and memory** (1 pass): drop slice pixels that the next nearer slice always
   covers (interior), cut each slice into strips of its rims (fill rate down by roughly 5 to 10x),
   pack one shading per direction only on demand, stop warming the steps for stacked pieces.
2. **All 13 pieces at the owner's taste** (1 to 2 passes + phone look): slope lines, outline width,
   crevice shading per piece; screenshot set (`VolumeStackScreenshotTest`, about 12 images:
   each room at rest, at the live angle, at either end of the pan, cats on and behind pieces).
3. **Device-pixel cats and floor** (2 passes; Opus for the review): float shifts for the cats and the
   floor runs so nothing steps an art pixel against a continuously moving piece.
4. **Make it the default, retire the steps** (1 pass; Opus, it changes what every user sees): remove
   `VolumeSheets` steps, `StepBlend`, `StepMix` cross-fade from the live path (keep the raster for
   the rest picture and tests), stacks made on the background warmer, guard thresholds re-measured.
5. **The three new rooms** (bath, study, sun; 10 pieces in `layers_ext.json` with depth maps
   already): no new engine work, `SolidStack` takes any `VolumeModel`; 1 pass for checks, tuning and
   screenshots once those rooms are solids in `DepthScene`.
6. **Lighting** (1 pass, after WP-L of `CAT_LIGHTING_PLAN.md`): lit copies of the slices per light
   band, the same way the cats get theirs.

Total: about 7 to 9 passes, plus two or three phone checks by the owner. GL (option B) would be
roughly 4 to 6 weeks of work for the renderer, meshes, the cats in GL and lighting shaders, with no
CI pictures at the end.

## 6. What the owner should check on the phone

Developer mode on, Cat tab, the panel under the buttons: "3D furniture: off" cycles to "sofa" then
"all".
1. **Smoothness.** With "sofa", follow a cat across the living room (or let the camera pan): the
   sofa's sides and the top of its arms should grow and shrink smoothly, with no visible steps,
   compared with the counter and fridge (still stepped). Then "all".
2. **The look.** Do the sides still read as pixel art next to the painted fronts, or do the finer
   edges look out of style? Look at the arms, the backrest top, the cat tree's platforms and trunk,
   the lamp.
3. **Cats.** A cat on the sofa, a cat behind the arm, cats in the tree: still in front of and
   behind the right parts? Does a cat on the seat appear to slide a little as the camera turns
   (known, section 4)?
4. **Speed.** The developer readout shows the depth level and load ("Depth MEDIUM ... load N%"):
   with "all", does the load rise, and does the guard ever step down? Any hitch when switching on?
5. **The floor.** With the furniture smooth, does the floor's whole-pixel step now stand out?

Only the phone can answer: the GPU cost of the overdraw (section 3), the hitch of making the stacks,
and whether device-pixel edges look right at the phone's pixel scale.
