# TASK: design the movement mechanics ("form") for every exercise in LifeMax's guided-workout animator

You are a biomechanics-literate animator and strength coach. I am building a mobile fitness app (LifeMax). During a workout a 3D trainer character repeats the current exercise at the correct pace and with correct form while the user does their set. My animator does NOT use motion capture. It poses a skeleton from a small set of **joint angles in the side (sagittal) plane**, interpolating between key poses. Your job: for each exercise below, specify those poses and the tempo, in the exact JSON format below, so I can paste your output straight into my code. Accuracy of the form matters more than anything else: if a real coach would wince at a pose, fix it.

## 1. The skeleton and the angle convention (read carefully)

The trainer faces **+x (forward)**. **+y is up.** The camera looks at him from his right side (+z toward camera). Everything you specify is in the x–y (side) plane except the optional sideways ("lateral") arm lift.

**Segment angle** = the direction of a body segment, from its proximal joint to its distal joint, in degrees, measured from straight UP and positive toward FORWARD:
- 0 = straight up, 90 = straight forward, 180 = straight down, -90 = straight backward, -180/180 = down.
- It is an ABSOLUTE direction in the world, not a joint flexion. Example: standing, the thigh (hip→knee) is 180 (pointing down) and the shin (knee→ankle) is 180. Sitting on a bench with thighs horizontal and shins vertical: thigh 90, shin 180. Hamstring curl with the shin lifted behind: shin goes toward -90.
- Torso angle = hip→shoulder direction. Standing 0; hinged forward 80; reclined seat -20; lying on your back with the head pointing backward: torso -90; face-down plank with head forward: torso about 85.

Segments and fixed lengths (metres; bones never stretch, so choose angles that make hands reach their handles and feet reach their platforms):
- torso (hip→shoulder) 0.52, neck/head (shoulder→head centre) 0.20
- thigh (hip→knee) 0.44, shin (knee→ankle) 0.44
- upper arm (shoulder→elbow) 0.32, forearm (elbow→wrist) 0.27
- foot: derived from the shin automatically (sole flat against the shin at a right angle). Give `foot_n`/`foot_f` ONLY to override (toes pointed, heels raised, foot flat on the floor while the shin leans). Foot angle = ankle→toe direction, same convention.

Near side = the trainer's right side (toward the camera). Far side = his left. Give both sides explicitly (`_n` = near, `_f` = far). For a symmetrical move, repeat the same values.

Lateral arm lift (optional, degrees, default 12 for upper arm and 6 for forearm): how far each arm segment swings OUT sideways from the side plane. 0 = in the plane, 90 = straight out to the side. Needed for flyes, lateral raises, face pulls (elbows wide), push-up elbow flare, wall slides.

**Anchor** = where the body is pinned while posed:
- `"hip"`: the hip joint is at `pos` (seated machine work, lying/floor work, planks). `pos` = [x, y] in metres: y = hip height above the floor. For a seated machine give the seat height (about 0.45-0.55); lying flat on a bench/mat about 0.14-0.45; plank about 0.30.
- `"ankle"`: the near ankle is at `pos` (standing work). y about 0.08 on the floor; give y higher for a calf raise on tiptoe or a step.
x is a horizontal offset in metres from the equipment's centre line (0 = centred). Use x to place a lunge, a step-up or a stagger stance.

Joint limits (do not exceed): knee flexion 0-145 deg; hip flexion 0-120 deg (knee up) and extension to -20; elbow flexion 0-145; shoulder flexion 0-180 and extension to -50; spine: torso change of at most 90 deg relative to the thigh when not intentionally rounded; neck within 40 deg of the torso line.

## 2. Output format

Output ONLY valid JSON (no comments, no trailing commas, no markdown prose outside the JSON). Send the answer in parts of at most 12 moves each, labelled `PART 1 OF N` on its own line before each JSON block, and wait for me to say "next". Each part is a JSON array of move objects.

A MOVE is ONE distinct movement pattern. Several plan exercises share a move (for example "Leg Press or Goblet Squat" needs two moves: `leg_press` and `goblet_squat`). Do not invent extra exercises and do not skip any: every numbered exercise in section 4 must be covered by at least one move via `covers`.

Move schema (every field required unless marked optional):
```
{
  "id": "leg_press",                      // snake_case, unique
  "name": "Leg press (seated machine)",
  "covers": ["Leg Press", "Leg Press or Goblet Squat"],   // the EXACT plan names from section 4 this move can represent (copy them exactly)
  "variantOf": null,                      // or the id of the move this is an alternative to
  "equipment": "stack_machine_seated",    // one of: stack_machine_seated, plate_machine_seated, cable_stack, dumbbells, barbell, bench, incline_bench, mat, foam_roller, wall, chair, box_step, band, treadmill, bike, elliptical, stairs, none
  "equipmentNotes": "seat reclined about 45 deg, platform moves away from the body",
  "mode": "reps",                         // "reps" (counted, cycles A->B->A), "hold" (isometric or timed), "loop" (continuous cycle: walking, bike, jog), "flow" (a timed sequence of different poses: mobility flows)
  "unilateral": false,                    // true if each rep is done on one side at a time (lunges, dead bug, Pallof, stretches per side)
  "alternateSides": false,                // true if sides alternate rep by rep (dead bug, bird-dog, step touch, walking)
  "view": "side",                         // best camera: "side", "front" or "three_quarter"
  "anchor": "hip",
  "poses": {
    "A": {"pos": [0.0, 0.45], "torso": -48, "neck": -40,
          "thigh_n": 52, "shin_n": 108, "thigh_f": 52, "shin_f": 108,
          "upper_n": 150, "fore_n": 120, "upper_f": 150, "fore_f": 120},
    "B": { ...same keys... }
  },
  "cycle": [                              // one full rep, in order. seconds.
    {"to": "B", "dur": 2.0, "ease": "inOut"},
    {"hold": 0.5},
    {"to": "A", "dur": 2.0, "ease": "inOut"},
    {"hold": 0.5}
  ],
  "secondsPerRep": 5.0,                   // the sum of the cycle
  "breath": "exhale on B (pushing), inhale on A",
  "farPhase": 0.0,                        // for loop/alternating moves: how far through the cycle the FAR side is (0 = same, 0.5 = opposite)
  "equipmentMotion": "platform travels 0.3 m away from the hips between A and B",
  "contact": ["feet on the platform", "back on the pad", "hands on the grips"],
  "primaryMuscles": ["quadriceps", "glutes"],
  "secondaryMuscles": ["hamstrings", "calves"],
  "cues": ["Push through the whole foot", "Do not lock the knees", "Keep hips on the pad"],
  "commonMistakes": ["Knees collapsing inward", "Lower back lifting off the pad"],
  "safety": "Stop short of locking the knees; stop if the pelvis rolls under at the bottom.",
  "tips": "One sentence a coach would say."
}
```
Pose keys (all numbers in degrees except pos): `pos`, `torso`, `neck`, `thigh_n`, `shin_n`, `foot_n` (optional), `thigh_f`, `shin_f`, `foot_f` (optional), `upper_n`, `fore_n`, `upper_f`, `fore_f`, and optional `lat_upper_n`, `lat_fore_n`, `lat_upper_f`, `lat_fore_f`, `spineCurl` (-1 fully rounded, 0 neutral, +1 fully extended; only for cat-cow, thoracic extension, cobras and similar).

Rules by mode:
- `reps`: define poses A (start) and B (end of the working phase). Add extra poses (`M`, `C`...) only if the path is not a straight A-to-B blend (for example a squat where the torso leans forward in the middle). The cycle may pass through any named pose. Provide an `ease` of `inOut`, `out` or `in` for each leg.
- `hold`: define pose A only and add `"holdSway": {"amplitudeDeg": 1.5, "period": 4}` for the tiny breathing motion; the cycle is a single `{"hold": 4.0}` entry.
- `loop`: define the poses of one full cycle (walking: at least 4 poses A,B,C,D evenly spaced). The far side runs `farPhase` behind (0.5 for walking, cycling, elliptical). `secondsPerRep` is one full cycle (one stride pair or one pedal turn).
- `flow`: poses in order with a `cycle` that visits them, each with a `dur`.
- For `unilateral` moves give the poses for the NEAR side performing the work; I will mirror them for the other side. For `alternateSides` give both sides explicitly in each pose.

## 3. Quality bar and self-check

Before you output each move, check and silently fix:
1. Hands/feet reach what they hold or push: with the segment lengths above, the wrist or ankle ends up where the handle, bar, platform or floor is in BOTH A and B. If a hand has to grip a handle at a fixed point while the elbow bends, make the angles consistent with that.
2. No impossible geometry: no knee bending backward, feet through the floor (ankle y >= 0.07 above the floor), arms through the torso, spine folded more than the limits above.
3. The tempo is realistic for a controlled rep: usually 1.5-2 s on the effort phase, 0-1 s pause, 2 s lowering. Stretches and holds: slow, calm.
4. Name the machine/bench geometry in `equipmentNotes` in enough detail (seat angle, pad position, handle height above the floor, where the weight stack or cable runs) that I can place props around the figure.
5. Safety notes are specific to that movement.

## 4. The exercises to cover (85 plan strings; copy `covers` names EXACTLY as written, without the numbering or the rep text)

1. Leg Press or Goblet Squat — 8-12 reps
2. Machine or Dumbbell Chest Press — 8-12 reps
3. Lat Pulldown or Assisted Pull-Up — 8-12 reps
4. Seated or Lying Leg Curl — 10-15 reps
5. Forearm Plank Hold — 30-45 seconds
6. Steady-State Aerobic Cardio (Treadmill, Bike, or Elliptical) — 25-30 minutes
7. Dumbbell Romanian Deadlift — 8-12 reps
8. Seated Cable Row or Supported Dumbbell Row — 8-12 reps
9. Dumbbell or Machine Overhead Shoulder Press — 8-12 reps
10. Dumbbell Reverse Lunge or Step-Up — 8-10 reps per leg
11. Dead Bug Core Alternation — 10 reps per side
12. Easy Regenerative Walking or Light Cycling — 10 minutes
13. World's Greatest Stretch Sequence — 5 reps per side
14. Hamstring and Adductor Mobility Flow — 40 seconds per side
15. Thoracic Extension over Foam Roller or Chair Back — 8 gentle reps
16. Incline Machine or Dumbbell Bench Press — 8-12 reps
17. Leg Extension Machine — 10-12 reps
18. Cable Face Pull or Reverse Pec Deck — 12-15 reps
19. Standing or Seated Calf Raise — 12-15 reps
20. Pallof Cable or Band Core Press — 10-12 reps per side
21. Endurance Cardiovascular Conditioning — 25-30 minutes
22. Optional Conditioning Finisher (Weeks 5-7) — 1 min challenging / 2 min easy recovery
23. Bodyweight Box Sit-to-Stand — 8 reps
24. Light Band or Doorframe Row — 10 reps
25. Full-Body Mobility Flow — 5 minutes
26. Goblet Squat or Chair Sit-to-Stand — 8-10 reps
27. Dumbbell or Band Romanian Deadlift — 10 reps
28. Dumbbell or Band Supported Row — 10-12 reps
29. Dead Bug Core Hold — 8 reps per side
30. Brisk Outdoor or Treadmill Walk — 25 minutes
31. Incline Push-Up (Bench, Desk, or Wall) — 8-12 reps
32. Standing Overhead Dumbbell Press — 8-10 reps
33. Floor Prone Cobras — 10-12 reps
34. Forearm Plank — 20-30 seconds
35. World's Greatest Stretch — 5 reps per side
36. Kneeling Hip Flexor Stretch — 40 seconds per side
37. Thoracic Spine Foam Roll or Chair Extensions — 8 gentle extensions
38. Glute Bridge Hold — 20-30 seconds
39. Continuous Easy Pacing or Step Touch — 5 minutes
40. Wall Slides for Shoulder Posture — 10 reps
41. Standing Hamstring Sweep — 8 reps per leg
42. Glute Bridges — 12-15 reps
43. Dumbbell or Band Rows — 10-12 reps
44. Bird-Dog — 8-10 reps per side
45. Brisk Walk or Light Jog — 20 minutes
46. Goblet Squats or Bodyweight Squats — 10-15 reps
47. Push-ups or Incline Push-ups — 8-15 reps
48. Dumbbell Romanian Deadlifts — 10-12 reps
49. Dumbbell Overhead Press — 8-12 reps
50. Sustained Outdoor Walk — 30 minutes
51. Cat-Cow Stretch — 10 slow reps
52. Seated Torso Twist — 30 seconds per side
53. Bodyweight Squats — 10-15 reps
54. Single-Leg Balance — 30 sec per leg
55. Reverse Lunges — 8 reps per leg
56. Standing Calf Raises — 15 reps
57. Continuous Daypack Walk — 25 minutes
58. Farmer's Carry — 45 sec
59. Sturdy Step-Ups — 10 reps per leg
60. Incline Walking — 20 minutes
61. Extended Continuous Walk — 50-60 minutes
62. Easy Familiar Walk — 20-25 minutes
63. Standing Calf Stretch — 30 sec per leg
64. Standing Quad Stretch — 30 sec per leg
65. Seated Hamstring Stretch — 30 sec per leg
66. Gentle Torso Twists — 1 minute
67. Leg Press — 8–12 reps
68. Machine Chest Press — 8–12 reps
69. Lat Pulldown — 8–12 reps
70. Seated Cable Row — 8–12 reps
71. Plank — 20–45 seconds
72. Goblet Squat (comfortable depth) — 8–12 reps
73. Incline Machine or Dumbbell Press — 8–12 reps
74. Assisted Pull-Up or Lat Pulldown — 8–12 reps
75. Dumbbell Reverse Lunge or Split Squat — 8–10 reps per leg
76. Machine Shoulder Press — 8–12 reps
77. Back Extension Machine — 10–15 reps
78. Dead Bug — 8–12 reps per side
79. Pallof Press — 10–12 reps per side
80. Easy walking or cycling — 5–10 min
81. Hip flexor stretch — 20–30 s per side
82. Hamstring stretch — 20–30 s per side
83. Chest stretch — 20–30 s
84. Calf stretch — 20–30 s per side
85. Gentle thoracic rotations — 8–10 reps per side

Notes on the list:
- "A or B" names (for example "Leg Press or Goblet Squat") are one plan entry with alternatives: create one move per alternative and put the full plan string in each move's `covers`.
- Cardio entries (treadmill, bike, elliptical, walking, jogging, incline walking, easy pacing) are `loop` moves. Create separate moves for treadmill walk, treadmill jog/run, stationary bike, elliptical and outdoor walk; for "Steady-State Aerobic Cardio (Treadmill, Bike, or Elliptical)" list all three in the relevant moves' `covers`.
- Stretches held per side are `hold` + `unilateral`.
- "World's Greatest Stretch" and the "Full-Body Mobility Flow" are `flow` moves.
- Intervals ("Optional Conditioning Finisher") are two `loop` moves: `intervals_hard` and `intervals_easy`.

## 5. When you are done

After the final part, output one more JSON block labelled `INDEX` that maps EVERY one of the 85 plan strings to the list of move ids that cover it, so I can check nothing is missing. Then list, in plain text, any exercise where you were unsure of the best form and say why.

Start with PART 1 now.
