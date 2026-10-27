#!/usr/bin/env python3
"""Builds src/runtime/segments/50-gym3d-moves.js from the form-mechanics pose data in gemini/workouts/moves-v1-*.py
(the Gemini-designed poses after the geometry checks) plus the cycle / equipment metadata below.
  python3 tools/make_moves.py
Pose arrays: [x, y, torso, neck, thigh_n, shin_n, thigh_f, shin_f, upper_n, fore_n, upper_f, fore_f] (degrees, 0 up, 90 forward)."""
import runpy, io, json, contextlib, os
H = 'gemini/workouts/'
def load(f):
    with contextlib.redirect_stdout(io.StringIO()): return runpy.run_path(H + f)['D']
D = {}
for f in ('moves-v1-batch1-validate.py', 'moves-v1-validate.py', 'moves-v1-batch2-validate.py'): D.update(load(f))
def setp(i, pose, **kw):
    for k, v in kw.items(): D[i][1][pose][k] = v
# small fixes found by the checker (seat heights so feet sit on the floor, hands on the ankle for the quad stretch, ...)
for i in ('dumbbell_chest_press', 'lat_pulldown', 'incline_machine_press', 'incline_dumbbell_press', 'reverse_pec_deck', 'seated_calf_raise', 'back_extension_machine'):
    for p in D[i][1].values(): p[1] = .52
for p in D['stationary_bike'][1].values(): p[1] = .95
for p in D['cable_face_pull'][1].values(): p[1] = .96
for p in D['standing_quad_stretch'][1].values(): p[10] = p[11] = 199          # far hand reaches the folded ankle
p = D['seated_hamstring_stretch'][1]['A']; p[2] = 35; p[3] = 30; p[8] = p[9] = p[10] = p[11] = 141   # hinge forward, hands to the shin
# the flow keeps its feet planted: anchor on the ankle instead of the hip
D['full_body_mobility_flow'] = ('ankle', {k: [0, .08] + v[2:] for k, v in D['full_body_mobility_flow'][1].items()})

# warm-up and cool-down movements authored here (same rig and angle rules); the plans describe these in text, see DESC
def arm(u): return [0, .08, 0, 0, 180, 180, 180, 180, u, u, u, u]
D['leg_swing'] = ('ankle', {'A': [0, .08, 0, 0, 180, 180, 140, 150, 150, 150, 150, 150], 'B': [0, .08, 0, 0, 180, 180, 215, 230, 150, 150, 150, 150]})
D['neck_roll'] = ('ankle', {'A': [0, .08, 0, -35, 180, 180, 180, 180, 180, 180, 180, 180], 'B': [0, .08, 0, 55, 180, 180, 180, 180, 180, 180, 180, 180]})
D['arm_circle'] = ('ankle', {'A': arm(0), 'B': arm(90), 'C': arm(180), 'D': arm(-90)})
D['knee_to_chest'] = ('ankle', {'A': arm(180), 'B': [0, .08, 0, 0, 180, 180, 40, 170, 120, 60, 120, 60]})
D['lying_breathing'] = ('hip', {'A': [0, .12, -90, -90, 90, 90, 90, 90, 90, 90, 90, 90]})
D['childs_pose'] = ('hip', {'A': [0, .194, 100, 100, 105, -90, 105, -90, 95, 95, 95, 95]})
D['legs_up_wall'] = ('hip', {'A': [0, .12, -90, -90, 0, 0, 0, 0, 90, 90, 90, 90]})
D['forward_fold'] = ('ankle', {'A': [0, .08, 140, 140, 180, 180, 180, 180, 170, 170, 170, 170]})
# id: (equipment, mode r/h/l/f, cycle, label regex, alternate sides)
M = {
 'leg_press': ('legpress', 'r', 'B2,-.5,A2,-.5', 'leg press'), 'goblet_squat': ('hinge', 'r', 'B2,-.5,A2,-.5', 'goblet|squat'),
 'machine_chest_press': ('chest', 'r', 'B2,-.5,A2,-.5', 'machine chest|chest press'), 'dumbbell_chest_press': ('bench', 'r', 'B1.5,-.5,A2,-.5', 'dumbbell chest|chest press'),
 'lat_pulldown': ('pulldown', 'r', 'B1.5,-.5,A2,-.5', 'pulldown'), 'assisted_pull_up': ('pullup', 'r', 'B2,-.5,A2,-.5', 'pull-up|pull up'),
 'seated_leg_curl': ('legcurl', 'r', 'B1.5,-.5,A2,-.5', 'leg curl'), 'lying_leg_curl': ('mobility', 'r', 'B1.5,-.5,A2,-.5', 'lying leg curl'),
 'forearm_plank': ('plank', 'h', '-4', 'plank'), 'treadmill_walk': ('walk', 'l', 'B.25,C.25,D.25,A.25', 'treadmill|walk'),
 'stationary_bike': ('bike', 'l', 'B.25,C.25,D.25,A.25', 'bike|cycling'), 'elliptical': ('ellip', 'l', 'B.3,C.3,D.3,A.3', 'elliptical'),
 'incline_machine_press': ('chest', 'r', 'B1.5,-.5,A2,-.5', 'incline machine|incline'), 'incline_dumbbell_press': ('bench', 'r', 'B1.5,-.5,A2,-.5', 'incline dumbbell|incline'),
 'leg_extension': ('legext', 'r', 'B1.5,-.5,A2,-.5', 'leg extension'), 'cable_face_pull': ('open', 'r', 'B2,-.5,A2,-.5', 'face pull'),
 'reverse_pec_deck': ('row', 'r', 'B1.5,-.5,A2,-.5', 'pec deck'), 'standing_calf_raise': ('calf', 'r', 'B1,-.5,A2,-.5', 'calf raise'),
 'seated_calf_raise': ('legext', 'r', 'B1,-.5,A2,-.5', 'seated calf'), 'pallof_press': ('open', 'r', 'B1.5,-1,A1.5,-.5', 'pallof'),
 'intervals_hard': ('walk', 'l', 'B.15,C.15,D.15,A.15', 'conditioning|endurance|finisher'), 'intervals_easy': ('walk', 'l', 'B.35,C.35,D.35,A.35', 'recovery'),
 'box_sit_to_stand': ('open', 'r', 'B2,-.5,A2,-.5', 'sit-to-stand|box'), 'band_row': ('open', 'r', 'B1.5,-.5,A2,-.5', 'band row|doorframe'),
 'full_body_mobility_flow': ('mobility', 'f', 'B5,C5,D6,-5,E6', 'mobility flow'), 'incline_push_up': ('plank', 'r', 'B1.5,-.5,A1.5,-.5', 'incline push|push-up'),
 'push_up': ('plank', 'r', 'B1.5,-.5,A1.5,-.5', 'push-up|push up'), 'prone_cobra': ('mobility', 'r', 'B1.5,-2,A1.5,-.5', 'cobra'),
 'kneeling_hip_flexor_stretch': ('mobility', 'h', '-4', 'hip flexor'), 'glute_bridge_hold': ('mobility', 'h', '-4', 'bridge hold'),
 'glute_bridge': ('mobility', 'r', 'B1.5,-1,A1.5,-.5', 'bridge'), 'step_touch': ('open', 'l', 'B.5,C.5,B.5,A.5', 'step touch|pacing'),
 'wall_slide': ('open', 'r', 'B2,-1,A2,-.5', 'wall slide'), 'standing_hamstring_sweep': ('open', 'r', 'B1.5,-.5,A1.5,-.5', 'hamstring sweep'),
 'bird_dog': ('mobility', 'r', 'B2,-.5,A2,-.5', 'bird'), 'cat_cow': ('mobility', 'r', 'B2,-.5,A2,-.5', 'cat'),
 'seated_torso_twist': ('mobility', 'h', '-4', 'torso twist'), 'single_leg_balance': ('open', 'h', '-4', 'balance'),
 'farmers_carry': ('hinge', 'l', 'B.3,C.3,D.3,A.3', 'carry'), 'incline_walking': ('walk', 'l', 'B.35,C.35,D.35,A.35', 'incline walk'),
 'standing_calf_stretch': ('open', 'h', '-4', 'calf stretch'), 'standing_quad_stretch': ('open', 'h', '-4', 'quad stretch'),
 'seated_hamstring_stretch': ('mobility', 'h', '-4', 'hamstring stretch'), 'chest_stretch': ('open', 'h', '-4', 'chest stretch'),
 'gentle_thoracic_rotations': ('mobility', 'r', 'B2,-.5,A2,-.5', 'thoracic rotation'), 'back_extension_machine': ('shoulder', 'r', 'B1.5,-1,A2,-.5', 'back extension'),
 'treadmill_jog': ('walk', 'l', 'B.2,C.2,D.2,A.2', 'jog'), 'bodyweight_squat': ('open', 'r', 'B2,-.5,A2,-.5', 'bodyweight squat'),
 'dumbbell_rdl': ('hinge', 'r', 'B2,-.5,A1.5,-.5', 'romanian|deadlift'), 'band_romanian_deadlift': ('open', 'r', 'B2,-.5,A1.5,-.5', 'band romanian'),
 'seated_cable_row': ('row', 'r', 'B2,-.5,A2,-.5', 'cable row'), 'supported_dumbbell_row': ('hinge', 'r', 'B1.5,-.5,A2,-.5', 'supported'),
 'standing_dumbbell_row': ('hinge', 'r', 'B1.5,-.5,A2,-.5', 'dumbbell row|rows'), 'standing_dumbbell_overhead_press': ('hinge', 'r', 'B1.5,-.5,A2,-.5', 'overhead|press'),
 'machine_overhead_press': ('shoulder', 'r', 'B1.5,-.5,A2,-.5', 'shoulder press'), 'dumbbell_reverse_lunge': ('hinge', 'r', 'B1.5,-.5,A1.5,-.5', 'lunge'),
 'dumbbell_split_squat': ('hinge', 'r', 'B1.5,-.5,A1.5,-.5', 'split squat'), 'dumbbell_step_up': ('hinge', 'r', 'B1.5,-.5,A1.5,-.5', 'step-up|step up'),
 'dead_bug': ('deadbug', 'r', 'B1.5,-1,A1.5,-.5', 'dead bug'), 'outdoor_walk': ('open', 'l', 'B.25,C.25,D.25,A.25', 'walk'),
 'worlds_greatest_stretch': ('mobility', 'f', 'B2,-2,C1.5,-2,D2,-2,A1.5,-1', "greatest stretch"), 'hamstring_adductor_flow': ('mobility', 'f', 'B3,-5,A3,-2', 'adductor'),
 'thoracic_extension_roller': ('mobility', 'r', 'B2,-2,A2,-1', 'thoracic|foam roll'),
 'leg_swing': ('open', 'r', 'B1,A1', 'leg swing'), 'neck_roll': ('open', 'r', 'B2,-.3,A2,-.3', 'neck'), 'arm_circle': ('open', 'l', 'B.6,C.6,D.6,A.6', 'arm circle'),
 'knee_to_chest': ('open', 'r', 'B1.5,-1,A1.5,-.5', 'knee-to-chest'), 'lying_breathing': ('mobility', 'h', '-4', 'breathing'), 'childs_pose': ('mobility', 'h', '-4', "child"),
 'legs_up_wall': ('mobility', 'h', '-4', 'legs-up'), 'forward_fold': ('open', 'h', '-4', 'forward fold'),
}
DESC = [['elliptical', 'elliptical'], ['jog', 'treadmill_jog'], ['incline walk|rowing', 'incline_walking'], ['cycling', 'stationary_bike'], ['walk|pacing', 'outdoor_walk'],
 ['romanian', 'dumbbell_rdl'], ['leg press', 'leg_press'], ['calf raise|ankle roll', 'standing_calf_raise'], ['hip hinge', 'band_romanian_deadlift'], ['squat', 'bodyweight_squat'],
 ['cat-cow', 'cat_cow'], ['leg swing', 'leg_swing'], ['figure-four', 'glute_bridge_hold'], ['lying', 'lying_breathing'], ['neck', 'neck_roll'], ['breathing', 'lying_breathing'], ['shrug|arm circle|torso twist|torso rotation', 'arm_circle'],
 ['knee-to-chest', 'knee_to_chest'], ['pelvic tilt', 'glute_bridge'], ['calf stretch', 'standing_calf_stretch'], ["child", 'childs_pose'], ['chest', 'chest_stretch'], ['quad', 'standing_quad_stretch'],
 ['hip flexor', 'kneeling_hip_flexor_stretch'], ['legs-up', 'legs_up_wall'], ['forward fold', 'forward_fold']]
ALT = {'bird_dog', 'dead_bug'}
bad = [i for i in M if i not in D] + [i for i in D if i not in M]
assert not bad, bad
import sys; sys.path.insert(0, 'tools')
import moves_v2, anim_lib
INDEX = json.load(open(H + 'moves-index.json'))
out = {}
for i, (eq, mode, cyc, kw) in M.items():
    anc, P = D[i]
    out[i] = {'a': eq, 'm': mode, 'n': 'h' if anc == 'hip' else 'a', 'c': cyc, 'k': kw, 'P': {k: [round(x, 3) for x in v] for k, v in P.items()}}
    if i in ALT: out[i]['alt'] = 1
for i, r in moves_v2.REG.items():
    body = anim_lib.sample(r['fn'], r['T'], r['N'])
    worst = 0
    for q in range(r['N'] * 3):          # every reach target must be within the rig's reach, or a limb would stretch
        pq = r['fn'](q / (r['N'] * 3)); pts = anim_lib.world_pts(pq)
        for l, kk in (('ln', 'ankN'), ('lf', 'ankF'), ('an', 'wrN'), ('af', 'wrF')):
            sp = pq[l]
            if sp[0] == 'i': worst = max(worst, ((sp[1] - pts[kk][0]) ** 2 + (sp[2] - pts[kk][1]) ** 2) ** .5)
    assert worst < r.get('tol', .03), (i, 'a reach target is out of reach by', round(worst, 3))
    old = out.get(i, {})
    out[i] = {'a': r['eq'], 'm': r['mode'], 'k': old.get('k', i.replace('_', ' ')), **body}
    if r['alt']: out[i]['alt'] = 1
js = '/* ===== Exercise movement data for the 3D gym, built by tools/make_moves.py. Do not edit by hand. ===== */\nvar G3_X=' + json.dumps(out, separators=(',', ':')) + ';\nvar G3_COVER=' + json.dumps(INDEX, separators=(',', ':')) + ';\nvar G3_DESC=' + json.dumps(DESC, separators=(',', ':')) + ';\n'
open('src/runtime/segments/50-gym3d-moves.js', 'w').write(js)
print('wrote', len(out), 'moves,', len(INDEX), 'plan names,', len(js) // 1024, 'KB')
