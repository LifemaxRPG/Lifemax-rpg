# Cat rig audit (v119)

Method: `node tools/rig_audit.js <built html>` runs the real runtime compositor over all 224 animation rows (adult, six kitten stages, back/front views and their kitten stages), every frame, at 1.0x and at stress values (ears 2.2x and 0.45x, tail 2x and 0.5x, legs 1.5x and 0.7x, body 1.5x and 0.7x, whiskers 1.8x). It counts new stray pieces, new holes and exposed fill, and how many pixels differ from the original art at 1.0x. These are heuristics for picking what a person should look at, not a verdict.

## Findings
1. **A curled tail breaks when it is made longer (severe).** In poses where the tail wraps around or under the body the compositor treats it as a free tail and stretches it into a long spike or a line under the cat. Rows: `sleep` and `sleep_w` (adult and kitten stages 1-5), kitten `groom` (st0-4), `purr` (st0-1), `play` (st1, st3), `slowblink`, `meow`, `idle_sit`, `idle_sit_w` (st0), `knead` (st5).
2. **The re-drawn tail is not identical to the original even at normal length.** 30-110 pixels differ in almost every row that has a tail: worst are `idle_sit_w` (71), `loaf` / `knead` (64), `run` (59), `idle_sit` / `lookup` (57), `groom` (54).
3. **Shortening the tail leaves stray dots** (`run`, `loaf`, `knead`, `sleep`).
4. **Shortening the ears leaves small fragments or a notch** in most rows; clearest in the back view `walk_back` (dst1), and adult `scratch`, `roll`, `bat`, `stretch`.

## Rows kept as original art (no rig, nothing to fix)
adult `play`, `scratch_in`, `scratch_out`, `kick`, `swat` have no tail rig; `play`, `scratch`, `bat` also skip leg/body stretching.

## Rows with no findings above the thresholds (adult)
`walk`, `eat`, `rub`, `leap`, `walk_w`, `crouch`, `scratch_in`, `scratch_out`, `kick`, `swat`, `play`.

Full per-row numbers: `report.json` and `summary.txt`. Contact sheets for the flagged rows: `REVIEW_A_severe_*.png` and `REVIEW_B_minor_*.png` (left to right: original, rig at 1.0x with the count of differing pixels, then the worst stress renders).
