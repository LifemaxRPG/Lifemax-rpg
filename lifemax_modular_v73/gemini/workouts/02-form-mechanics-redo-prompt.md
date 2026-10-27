# TASK: design the movement mechanics ("form") for 30 exercises in LifeMax's guided-workout animator

(This is a fresh start. Ignore any earlier conversation about this task.)

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

Near side = the trainer's right side (toward the camera). Far side = his left. Give both sides explicitly (`_n` near, `_f` far); repeat the values for symmetrical moves.

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

Output ONLY valid JSON, in **3 parts of 10 moves each**. Put the label `PART n OF 3` on its own line before each JSON block, then wait for me to say "next". Each part is a JSON array. Do not repeat any move from an earlier part. After PART 3, output one more block labelled `CHECK` that is a JSON array of `{ "id": ..., "ankle_n_A": [x,y], "ankle_n_B": [x,y], "reachesTarget": true|false }` for every move (these are the near-side ankle positions you computed from the hip, to prove the geometry works).

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

## 4. The 30 moves to produce

**Group 1: 10 NEW moves** (never produced before). Use these ids and cover these exact plan names:

1. `dumbbell_rdl`: covers "Dumbbell Romanian Deadlift", "Dumbbell or Band Romanian Deadlift", "Dumbbell Romanian Deadlifts"
2. `seated_cable_row`: covers "Seated Cable Row or Supported Dumbbell Row", "Seated Cable Row"
3. `supported_dumbbell_row`: chest-supported or one-arm bench-supported row; covers "Seated Cable Row or Supported Dumbbell Row", "Dumbbell or Band Supported Row", "Dumbbell or Band Rows"
4. `standing_dumbbell_overhead_press`: covers "Dumbbell or Machine Overhead Shoulder Press", "Standing Overhead Dumbbell Press", "Dumbbell Overhead Press"
5. `machine_overhead_press`: seated; covers "Dumbbell or Machine Overhead Shoulder Press", "Machine Shoulder Press"
6. `dumbbell_reverse_lunge`: covers "Dumbbell Reverse Lunge or Step-Up", "Dumbbell Reverse Lunge or Split Squat", "Reverse Lunges". Unilateral.
7. `dumbbell_step_up`: onto a box about 0.40 m high; covers "Dumbbell Reverse Lunge or Step-Up", "Sturdy Step-Ups". Unilateral.
8. `dead_bug`: covers "Dead Bug Core Alternation", "Dead Bug Core Hold", "Dead Bug". Lying on the back, `alternateSides` true.
9. `outdoor_walk`: relaxed walk, 4-pose `loop`; covers "Easy Regenerative Walking or Light Cycling", "Brisk Outdoor or Treadmill Walk", "Brisk Walk or Light Jog", "Sustained Outdoor Walk", "Continuous Daypack Walk", "Extended Continuous Walk", "Easy Familiar Walk", "Easy walking or cycling"
10. `worlds_greatest_stretch`: `flow` (lunge, elbow to instep, rotate and reach up, hamstring rock back); covers "World's Greatest Stretch Sequence", "World's Greatest Stretch"

**Group 2: 2 more NEW moves**
11. `hamstring_adductor_flow`: `flow`, covers "Hamstring and Adductor Mobility Flow"
12. `thoracic_extension_roller`: on a foam roller across the mid-back, hands behind head; covers "Thoracic Extension over Foam Roller or Chair Back", "Thoracic Spine Foam Roll or Chair Extensions"

**Group 3: 18 CORRECTED moves.** I already have your earlier versions; they had the leg-angle mistakes described in section 1 (and a few more). Redo each from scratch with the same id, same `covers` as before, and the reference table above:
13. `forearm_plank` (covers "Forearm Plank Hold", "Forearm Plank", "Plank"): hold, torso about +85, legs about -95
14. `push_up` ("Push-ups or Incline Push-ups"): hands on floor, body in one straight line, legs trailing behind
15. `incline_push_up` ("Incline Push-Up (Bench, Desk, or Wall)", "Push-ups or Incline Push-ups"): hands on a 0.45 m bench
16. `prone_cobra` ("Floor Prone Cobras"): face down, legs on the floor trailing behind, chest lifts
17. `lying_leg_curl` ("Seated or Lying Leg Curl"): face down on a machine bench, legs trail behind, shin curls up toward the glutes
18. `seated_leg_curl` ("Seated or Lying Leg Curl"): seated, the curl ends with the shin tucked back under the seat
19. `glute_bridge` ("Glute Bridges") and 20. `glute_bridge_hold` ("Glute Bridge Hold"): on the back, knees up, feet flat
21. `treadmill_walk` ("Steady-State Aerobic Cardio (Treadmill, Bike, or Elliptical)", "Brisk Outdoor or Treadmill Walk"), 22. `treadmill_jog` ("Brisk Walk or Light Jog"), 23. `incline_walking` ("Incline Walking"), 24. `intervals_hard` ("Optional Conditioning Finisher (Weeks 5-7)", "Endurance Cardiovascular Conditioning"), 25. `intervals_easy` ("Optional Conditioning Finisher (Weeks 5-7)"), 26. `farmers_carry` ("Farmer's Carry"): all four-pose loops with legs around 180 (see reference table)
27. `goblet_squat` ("Leg Press or Goblet Squat", "Goblet Squat or Chair Sit-to-Stand", "Goblet Squats or Bodyweight Squats", "Goblet Squat (comfortable depth)"), 28. `bodyweight_squat` ("Bodyweight Squats", "Goblet Squats or Bodyweight Squats"), 29. `box_sit_to_stand` ("Bodyweight Box Sit-to-Stand", "Goblet Squat or Chair Sit-to-Stand"): squat-bottom shin must be 190 to 205
30. `dumbbell_split_squat` ("Dumbbell Reverse Lunge or Split Squat"): variantOf `dumbbell_reverse_lunge`. The back leg's shin must trail BEHIND (about 250 to 270), never forward.

Ids that already exist elsewhere and may be used in `variantOf`: leg_press, machine_chest_press, dumbbell_chest_press, lat_pulldown, assisted_pull_up, elliptical, stationary_bike, bird_dog, cat_cow, wall_slide, band_romanian_deadlift, standing_dumbbell_row.

Split the 30 moves into 3 parts in the order listed (1-10, 11-20, 21-30). Start with PART 1 now.
