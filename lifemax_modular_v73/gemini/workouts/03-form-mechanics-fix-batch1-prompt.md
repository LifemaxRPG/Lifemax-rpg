# TASK: design the movement mechanics ("form") for 13 exercises (fixing mistakes in an earlier batch) in LifeMax's guided-workout animator

(This is a fresh start. Ignore any earlier conversation about this task. You produced an earlier version of these 13 moves and a geometry checker found real errors, listed in section 4. Redo them from scratch.)

You are a biomechanics-literate animator and strength coach. I am building a mobile fitness app (LifeMax). During a workout a 3D trainer character repeats the current exercise at the correct pace and with correct form. My animator does NOT use motion capture. It poses a skeleton from **joint angles in the side (sagittal) plane**, interpolating between key poses. For each move below, specify those poses and the tempo in the exact JSON format below, so I can paste your output straight into code. Accuracy matters more than anything else: if a real coach would wince, fix it.

## 1. The angle convention (the part earlier attempts got wrong, read twice)

The trainer faces **+x (forward)**; **+y is up**. All angles are in the x–y side plane.

**Segment angle** = the direction a segment points, from its proximal joint to its distal joint, in degrees, measured from straight UP, positive toward FORWARD. It is an ABSOLUTE world direction, never a joint flexion.

- 0 = up, 90 = forward, 180 = down, -90 (= 270) = backward, -180 = down.
- A segment's endpoint is: `x = L*sin(angle)`, `y = L*cos(angle)` from its start point. **Do this arithmetic for every segment, in every pose, before you write it down.**

Segment lengths (metres, fixed): torso (hip→shoulder) 0.52, neck/head 0.20, thigh (hip→knee) 0.44, shin (knee→ankle) 0.44, upper arm (shoulder→elbow) 0.32, forearm (elbow→wrist) 0.27. Foot is derived from the shin (flat, at a right angle); give `foot_n`/`foot_f` only to override (toes pointed, heels raised, foot flat while the shin leans). Foot angle = ankle→toe direction, same convention.

### Reference poses (copy these orientations exactly)

| Position | torso | thigh | shin | notes |
|---|---|---|---|---|
| Standing tall | 0 | **180** | **180** | legs point DOWN, not up. 0 would be legs in the air. |
| Standing, hips hinged forward (RDL, row) | 70 to 85 | 160 to 175 | 170 to 180 | torso leans forward, legs stay near vertical |
| Squat bottom | 35 to 45 | **75 to 90** (knee forward of hip) | **190 to 205** (shin leans so the ankle is BEHIND the knee) | a shin of 140 would put the ankle in front of the knee: wrong |
| Sitting, thighs horizontal, shins vertical | 0 | 90 | 180 | |
| Seated, leg extended forward | 0 | 90 | 90 | |
| Seated, hamstring curl fully curled | 0 | 90 | 200 to 215 | shin tucks back under the seat |
| Lying on your BACK, head pointing backward | **-90** | knees up and forward: **+30 to +60** (bent), **+85** (straight on floor) | for knees bent, feet flat on floor: roughly **165 to 190** | head and legs go in OPPOSITE directions |
| Lying FACE DOWN / plank / push-up, head forward | **+80 to +90** | **-95 to -100** (legs trail BEHIND, straight) | **-95 to -100** | head and legs go in OPPOSITE directions. If torso is +85, the legs are NOT +85. |
| Walking/jogging, leading leg forward | 0 to 15 | **150 to 170** (thigh forward of vertical) | 160 to 175 | all walking/running leg angles are **around 180, not 0**. Typical thigh range 150 to 205, shin 120 to 240. |
| Walking, trailing leg back | 0 to 15 | 195 to 210 | 200 to 250 (heel kicks up behind at toe-off) | |
| Arm hanging at side | | | | upper 180, fore 180 |
| Arm raised forward, straight | | | | upper 90, fore 90 |
| Arm overhead, straight | | | | upper 0 to 10, fore 0 to 10 |
| Elbow bent 90 at the side, forearm forward | | | | upper 180, fore 90 |

For walking and running: the near leg and the far leg must be roughly half a cycle apart (`farPhase` 0.5). Keep the ankle of the stance leg at floor height (y about 0.08 above the floor).

### More reference poses (the exact things an automated checker flagged last time)

| Position | torso | thigh | shin | arms |
|---|---|---|---|---|
| Hands-and-knees (quadruped), kneeling | **about 80** (shoulders slightly higher than hips so the straight arms reach the floor) | **180** (thigh vertical, knee under the hip) | **-90** (shin trails BEHIND along the floor, toes back) | upper 180, fore 180, wrist about 0.03 above floor. hip y about 0.52. |
| Bird-dog extended leg | | **-90** (straight back at hip height), shin **-90** | | extended arm: upper 90, fore 90 |
| Sitting on the floor, leg straight in front | 0 | **90** | **90** | hip y about 0.10 |
| Sitting on the floor, knee bent up, foot flat | 0 | **45 to 60** | **135 to 150** (foot back toward the hip, ankle y about 0.08) | |
| Standing, heel pulled to glute (quad stretch) | 0 | 180 (thigh hangs down) | **-20 to -35** (shin folds up and slightly back; heel near the glute) | the LIFTED leg goes on the FAR side (`_f`) and the standing leg is the NEAR side (`_n`), because the anchor is the near ankle. |
| Standing, rear leg straight behind (calf stretch, lunges) | 10 to 20 | rear leg **195 to 205**, front leg **150** | rear **195 to 205**, front **190 to 200** | rear leg is on the near side so the anchor is the rear heel (y 0.08) |
| Kneeling on one knee (half-kneeling) | 0 | front thigh **90**, rear thigh **170 to 180** | front shin **180**, rear shin **-90** | |
| Downward dog | **about 135** (hips up, head down toward the floor) | **about 225** (legs trail back and down) | about 225 | upper 135, fore 135, wrists on the floor |
| Forward fold, straight legs | **150 to 170** | 180 | 180 | arms hang toward the floor |
| Deep squat bottom | 40 | **100 to 110** | **200 to 210** | |

**Hands that hold a fixed handle or bar:** compute the wrist position in every pose (shoulder = hip + 0.52 at the torso angle; elbow = shoulder + 0.32 at the upper-arm angle; wrist = elbow + 0.27 at the forearm angle). For a pull-up bar, an overhead handle or a horizontal cable/machine path, the wrist HEIGHT must stay the same (or follow the machine's path) between poses A and B. List the wrist height in the CHECK block for A and B.

Near side = the trainer's right side (toward the camera).
Far side = his left. Give both sides explicitly (`_n` near, `_f` far); repeat the values for symmetrical moves.

Optional lateral arm lift (degrees, default 12 upper / 6 forearm): how far each arm segment swings out sideways from the side plane (0 = in-plane, 90 = straight out to the side). Needed for flyes, raises, wide-elbow moves.

**Anchor:**
- `"hip"`: hip joint is at `pos` ([x, y] metres; y = hip height above the floor). Seated: seat height 0.45 to 0.55. Lying on a bench: bench height. Floor work: plank hip about 0.30, lying on floor about 0.12.
- `"ankle"`: the near ankle is at `pos` (standing work). y about 0.08 on the floor; larger on a step.
x is a horizontal offset from the equipment centre line (0 = centred).

Joint limits: knee flexion 0–145, hip flexion 0–120 (and extension to -20), elbow flexion 0–145, shoulder flexion 0–180 and extension to -50, neck within 40 deg of the torso line.

## 2. Self-check before you output EVERY pose (silently fix, do not print)

1. Compute both ankles from the hip with the formula above. For standing, floor-supported or lunge moves the supporting ankle must come out at the floor (ankle y about 0.08 when anchor is ankle; for anchor "hip" check that foot y is above 0 and the hip height makes sense).
2. Head and legs must point in opposite directions when the torso is horizontal (plank, push-up, bridge, cobra, curl).
3. Standing legs are about 180. If a walking pose has thigh or shin near 0, you have made the mistake: add 180.
4. Squat/lunge shins: knee forward of the hip means the shin points down and slightly BACK (angle above 180).
5. Hands must reach what they hold (bar, handle, floor, wall, own knee) in both A and B.
6. No knee bending backward (the shin may only swing behind the thigh line: shin angle >= thigh angle for a forward-facing knee), no feet through the floor, no arms through the torso.
7. Realistic tempo: effort phase 1.5 to 2 s, lowering 2 s, pauses 0 to 1 s. Holds are slow and calm.

## 3. Output format

Output ONLY valid JSON, in **2 parts (PART 1 has 7 moves, PART 2 has 6)**. Put the label `PART n OF 2` on its own line before each JSON block, then wait for me to say "next". Each part is a JSON array. Do not repeat any move from an earlier part. After PART 2, output one more block labelled `CHECK` that is a JSON array of `{ "id": ..., "ankle_n_A": [x,y], "ankle_n_B": [x,y], "wrist_n_A": [x,y], "wrist_n_B": [x,y], "reachesTarget": true|false }` for every move (the near-side ankle and wrist positions you computed from the hip, to prove the geometry works: feet on the floor, hands on the bar or floor).

Move schema (all fields required unless marked optional):
```
{
  "id": "dumbbell_rdl",                 // snake_case, unique
  "name": "Dumbbell Romanian Deadlift",
  "covers": ["exact plan name", "..."],  // use EXACTLY the plan names given in section 4
  "variantOf": null,                     // or the id of another move in THIS task or in the list of existing ids below
  "equipment": "dumbbells",              // stack_machine_seated, plate_machine_seated, cable_stack, dumbbells, barbell, bench, incline_bench, mat, foam_roller, wall, chair, box_step, band, treadmill, bike, elliptical, stairs, none
  "equipmentNotes": "...",               // enough geometry (seat angle, bench height, handle height) to place props
  "mode": "reps",                        // reps (cycles A->B->A), hold, loop (continuous cycle), flow (timed sequence of poses)
  "unilateral": false,                   // each rep one side at a time; poses are for the NEAR side, I mirror them
  "alternateSides": false,               // sides alternate rep by rep: give both sides explicitly
  "view": "side",                        // side, front or three_quarter
  "anchor": "ankle",
  "poses": { "A": { "pos": [0.0, 0.08], "torso": 0, "neck": 0,
      "thigh_n": 180, "shin_n": 180, "thigh_f": 180, "shin_f": 180,
      "upper_n": 180, "fore_n": 180, "upper_f": 180, "fore_f": 180 },
    "B": { ... } },
  "cycle": [ {"to": "B", "dur": 2.0, "ease": "inOut"}, {"hold": 0.5}, {"to": "A", "dur": 1.5, "ease": "inOut"}, {"hold": 0.5} ],
  "secondsPerRep": 4.5,                  // = sum of cycle
  "breath": "...", "farPhase": 0.0, "equipmentMotion": "...",
  "contact": ["..."], "primaryMuscles": ["..."], "secondaryMuscles": ["..."],
  "cues": ["..."], "commonMistakes": ["..."], "safety": "...", "tips": "..."
}
```
Pose keys (degrees except pos): `pos`, `torso`, `neck`, `thigh_n`, `shin_n`, optional `foot_n`, `thigh_f`, `shin_f`, optional `foot_f`, `upper_n`, `fore_n`, `upper_f`, `fore_f`, optional `lat_upper_n/_f`, `lat_fore_n/_f`, optional `spineCurl` (-1 fully rounded, 0 neutral, +1 fully extended; cat-cow, thoracic extension only).

Mode rules:
- `reps`: poses A (start) and B (end of effort). Add extra poses (M, C...) only if the path is not a straight A-to-B blend; the cycle may pass through any named pose.
- `hold`: pose A only, plus `"holdSway": {"amplitudeDeg": 1.5, "period": 4}`; cycle is a single `{"hold": 4.0}`.
- `loop`: at least 4 evenly spaced poses A, B, C, D for one full cycle (one stride pair or pedal turn). The far side runs `farPhase` behind (0.5 for walking). `secondsPerRep` = one full cycle.
- `flow`: poses in order, `cycle` visits them each with a `dur`.

## 4. The 13 moves to redo, with what was wrong

Use these ids and exactly these `covers` names.

PART 1 (7 moves, quadruped, floor and stretch moves)
1. `bird_dog`, covers "Bird-Dog". Was wrong: shins pointed forward (should be -90, trailing), wrists were 9 cm below the floor (torso 90 with hips at 0.50; use torso about 80, hips about 0.52), and the extended leg pointed FORWARD under the chest (should be thigh -90, straight back). Opposite arm and leg, `alternateSides` true.
2. `cat_cow`, covers "Cat-Cow Stretch". Same quadruped mistakes (shins, wrists below floor). Keep `spineCurl` -1 to +1.
3. `gentle_thoracic_rotations`, covers "Gentle thoracic rotations". Same quadruped mistakes; one hand behind the head, elbow rotating up (unilateral).
4. `kneeling_hip_flexor_stretch`, covers "Kneeling Hip Flexor Stretch", "Hip flexor stretch". Was wrong: the rear shin pointed forward (should be -90). Hold, unilateral.
5. `standing_calf_stretch`, covers "Standing Calf Stretch", "Calf stretch". Was wrong: the legs pointed UP (angles -15 and 30). Rear leg straight at about 200, front knee bent, hands on a wall at shoulder height (hands: upper 90, fore 90). Hold, unilateral.
6. `standing_quad_stretch`, covers "Standing Quad Stretch". Was wrong: the shin was 20 (heel in front of the face) and the lifted leg was on the near side so the hips came out at the floor. Standing leg = NEAR side, lifted leg = FAR side with the shin folded to about -25. Hold, unilateral.
7. `seated_hamstring_stretch`, covers "Seated Hamstring Stretch", "Hamstring stretch". Was wrong: the bent leg's shin was 180 (through the floor). Straight leg 90/90 and bent leg thigh 45 to 60, shin 135 to 150 (foot on the floor); hips on the floor. Hold, unilateral.

PART 2 (6 moves, flows and fixed-handle moves)
8. `seated_torso_twist`, covers "Seated Torso Twist", "Gentle Torso Twists". Was wrong: hip at the floor but the shins pointed 180 (through the floor). Sit on the floor with one leg straight (90/90) and the other knee bent up with the foot on the floor (see reference). Hold, unilateral.
9. `full_body_mobility_flow`, covers "Full-Body Mobility Flow". Was wrong: the "squat" pose had the hips at the floor (thigh 40, shin 140 is nonsense), the "forward fold" was a flat back (torso 90), and the "downward dog" had the legs pointing forward. Redo it as a `flow` of 5 poses with real angles from the reference table: A standing tall, B deep squat, C forward fold, D downward dog, E back to standing. Total about 25 to 30 s.
10. `assisted_pull_up`, covers "Lat Pulldown or Assisted Pull-Up", "Assisted Pull-Up or Lat Pulldown". Was wrong: in B the wrists were 17 cm lower than in A, so the hands let go of the bar. The wrist height must stay equal at the bar (about 1.8 m) between A and B.
11. `seated_cable_row`, covers "Seated Cable Row or Supported Dumbbell Row", "Seated Cable Row". Was wrong: the wrists were at 0.81 m in A and 0.55 m in B; a cable row path is horizontal, so keep the wrist height constant (about 0.55 to 0.6 m) while the elbows pull back. The feet must also sit about 0.08 above the floor (seat/hip height 0.42 with those shins).
12. `machine_chest_press`, covers "Machine or Dumbbell Chest Press", "Machine Chest Press". Was wrong: the wrists rose 32 cm during the press; a chest press handle path is horizontal at about chest height (0.85 to 0.9 m) for both A and B. Seat/hip height 0.52 so the feet are on the floor.
13. `cable_face_pull`, covers "Cable Face Pull or Reverse Pec Deck", "Cable Face Pull". Was wrong: in B the upper arm was -10 (pointing up) with the forearm at 180 (pointing down), which is not a face pull. Elbows high and wide (use lat_upper about 80), hands next to the ears at about 1.6 m, forearms pointing up and slightly forward (about 20 to 40).

Ids that already exist elsewhere and may be used in `variantOf`: lat_pulldown, leg_press, goblet_squat, standing_dumbbell_row.

Start with PART 1 now.
