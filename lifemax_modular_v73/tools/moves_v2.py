"""Hand-authored movements for the 3D gym. Each registers a function phase(0..1) -> pose over one cycle of T seconds.
Planted feet and gripped handles are reach targets, so they never slide; everything else is angles or positions that change smoothly."""
import math
from anim_lib import *
REG = {}
def mv(id, eq, mode, T, N, alt=0, tol=.03):
    def deco(f): REG[id] = dict(eq=eq, mode=mode, T=T, N=N, fn=f, alt=alt, tol=tol); return f
    return deco
def rep(p, a, h1, b, h2):
    """effort 0..1 for a rep: rise over fraction a, hold h1 at the top, fall over b, rest h2 (fractions sum to 1)"""
    if p < a: return sm5(p / a)
    if p < a + h1: return 1.0
    if p < a + h1 + b: return 1 - sm5((p - a - h1) / b)
    return 0.0
LEG = LT + LS
# ---------------------------------------------------------------- squats
def squat_body(e, depth=.40, back=.24, lean=40):
    hy = lerp(FLOOR + LEG * .995, FLOOR + depth + .12, e)
    hx = -back * sm(min(1, e * 1.2))
    return (hx, hy), lean * sm(min(1, e * 1.05))
@mv('bodyweight_squat', 'open', 'r', 4.8, 24)
def _(p):
    e = rep(p, .42, .08, .42, .08); h, t = squat_body(e)
    return pose(h, t, t * .25, u=e, ln=foot(0), an=fk(lerp(180, 100, sm(e)), lerp(180, 96, sm(e))))
@mv('goblet_squat', 'hinge', 'r', 4.8, 24)
def _(p):
    e = rep(p, .42, .08, .42, .08); h, t = squat_body(e)
    c = on_torso(h, t, .36, .15)
    return pose(h, t, t * .25, u=e, ln=foot(0), an=hand(c[0], c[1], -1))
@mv('box_sit_to_stand', 'open', 'r', 5.2, 26)
def _(p):
    e = rep(p, .38, .12, .38, .12); h, t = squat_body(e, depth=.42, back=.34, lean=26)
    return pose(h, t, t * .25, u=e, ln=foot(0), an=fk(lerp(180, 100, sm(e)), lerp(180, 96, sm(e))))
# ---------------------------------------------------------------- gait
def foot_angle(ph, ds):
    """toe direction: heel strikes toes-up, flat through stance, heel lifts at toe-off, relaxes through swing"""
    if ph < ds:
        s = ph / ds
        if s < .12: return lerp(82, 100, sm(s / .12))
        if s < .62: return 100
        return lerp(100, 138, sm((s - .62) / .38))
    s = (ph - ds) / (1 - ds)
    return lerp(138, 100, sm(min(1, s * 1.6)))
def leg_target(ph, T, v, ds, clear, lift_end=.02, base=FLOOR):
    """ankle joint position for a leg at phase ph (0 = heel strike) on a belt moving back at v; returns (x, y, ft)"""
    Ts = ds * T; hs = v * Ts / 2; Tw = (1 - ds) * T
    if ph < ds:
        x = hs - v * ph * T; s = ph / ds; y = base + lift_end * sm((s - .8) / .2)
    else:
        s = (ph - ds) / (1 - ds); u2 = s * s; u3 = u2 * s
        x = (2 * u3 - 3 * u2 + 1) * -hs + (u3 - 2 * u2 + u2 * 0 + s) * Tw * -v + (-2 * u3 + 3 * u2) * hs + (u3 - u2) * Tw * -v
        y = base + lift_end * (1 - sm(min(1, s * 3))) + clear * math.sin(math.pi * s) ** 2
    return x, y, foot_angle(ph, ds)
BELT = FLOOR + .20          # the treadmill deck stands this much above the floor (matches the prop)
def gait(ph, T, v, ds, clear, bob, lean, arm, flex, run=False, carry=False, reach=.985, base=BELT, flight=0.0):
    """hip height follows the stance legs: the hip rides over the planted foot like an inverted pendulum (flight arc when no foot is down)"""
    hs = v * ds * T / 2; Lt = LEG * reach
    ph2 = (ph + .5) % 1
    tg = [leg_target(q, T, v, ds, clear, base=base) for q in (ph, ph2)]
    down = [math.sqrt(max(.01, Lt * Lt - x * x)) for x, y, ft in tg if y - base < .05]
    if down: hy = min(down)
    else:
        w = (ph % .5 - ds) / (.5 - ds) if ds < .5 else 0; ends = math.sqrt(max(.01, Lt * Lt - hs * hs))
        hy = lerp(ends, ends, 0) + flight * math.sin(math.pi * max(0, min(1, w)))
    h = (0, base + hy)
    L = [foot(x, y, ft) for x, y, ft in tg]
    if carry: an, af = fk(180, 180), fk(180, 180)
    else:
        sw = lambda q: 180 + arm * math.cos(2 * math.pi * q)
        fl = flex if flex else 22
        an = fk(sw(ph), sw(ph) - fl); af = fk(sw(ph2), sw(ph2) - fl)
    t = lean + 1.2 * math.sin(4 * math.pi * ph)
    return pose(h, t, t * .3, u=0, ln=L[0], lf=L[1], an=an, af=af)
@mv('treadmill_walk', 'walk', 'l', 1.1, 22)
def _(p): return gait(p, 1.1, .95, .62, .11, .014, 4, 24, 0)
@mv('outdoor_walk', 'walk', 'l', 1.0, 20)
def _(p): return gait(p, 1.0, 1.1, .62, .12, .016, 4, 26, 0)
@mv('intervals_easy', 'walk', 'l', 1.3, 26)
def _(p): return gait(p, 1.3, .8, .64, .09, .012, 3, 20, 0)
@mv('incline_walking', 'walk', 'l', 1.1, 22)
def _(p): return gait(p, 1.1, .9, .62, .15, .014, 13, 24, 0)
@mv('treadmill_jog', 'walk', 'l', .72, 24)
def _(p): return gait(p, .72, 2.2, .42, .17, .035, 9, 36, 82, run=True, reach=.955, flight=.03)
@mv('intervals_hard', 'walk', 'l', .6, 24)
def _(p): return gait(p, .6, 3.1, .38, .22, .05, 14, 44, 84, run=True, reach=.955, flight=.03)
@mv('farmers_carry', 'hinge', 'l', .95, 19)
def _(p): return gait(p, .95, 1.0, .64, .09, .012, 2, 0, 0, carry=True, base=FLOOR)
# ---------------------------------------------------------------- cardio machines and pacing
@mv('stationary_bike', 'bike', 'l', .8, 24)
def _(p):
    a = 2 * math.pi * p; C = (.30, .36); R = .17
    def ank(ang):
        px, py = C[0] + R * math.sin(ang), C[1] + R * math.cos(ang)
        return foot(px - .07, py + .10, 100 + 10 * math.sin(ang + .6))
    h = (-.14, .96); t = 32
    return pose(h, t, t * .3, ln=ank(a), lf=ank(a + math.pi), an=hand(.56, 1.13, -1), af=hand(.56, 1.13, -1))
@mv('elliptical', 'ellip', 'l', 1.2, 24)
def _(p):
    a = 2 * math.pi * p; cx, cy, rx, ry = .02, .33, .24, .07
    def ank(ang): return foot(cx + rx * math.cos(ang), cy + ry * math.sin(ang) + .02, 100 + 8 * math.cos(ang))
    def grip(ang):
        al = -.30 * math.cos(ang)            # the handle swings the other way from the same-side foot
        return hand(.30 + .45 * math.sin(al), 1.90 - .45 * math.cos(al), -1)
    h = (-.02, 1.17 + .012 * math.cos(2 * a)); t = 8
    return pose(h, t, t * .3, ln=ank(a), lf=ank(a + math.pi), an=grip(a), af=grip(a + math.pi))
@mv('step_touch', 'open', 'l', 1.6, 32)
def _(p):
    """easy pacing / step touch seen from the side: the feet take turns tapping forward while the weight rocks over the other leg"""
    def tap(q):
        e = math.sin(math.pi * min(1, max(0, (q % 1) * 2))) if (q % 1) < .5 else 0
        return foot(.0 + .26 * sm5(min(1, max(0, (q % 1) * 2))) * (1 if (q % 1) < .5 else 0) + .26 * (1 - sm5(min(1, max(0, ((q % 1) - .5) * 2)))) * (1 if (q % 1) >= .5 else 0) - .02, FLOOR + .06 * e, 100 + 20 * e)
    sway = math.sin(2 * math.pi * p) * .03
    sw = lambda q: 180 + 20 * math.cos(2 * math.pi * q)
    h = (sway * .5, FLOOR + LEG * .985 - .006 * (1 + math.cos(4 * math.pi * p)))
    return pose(h, 2, 1, ln=tap(p), lf=tap(p + .5), an=fk(sw(p), sw(p) - 22), af=fk(sw(p + .5), sw(p + .5) - 22))
# ---------------------------------------------------------------- hinges, rows, presses (standing)
PLANT = lambda: foot(0.0)
def hinge_pose(e, lean=76, back=.30, hipy=.84):
    hx = -back * sm(min(1, e * 1.1)); hy = lerp(FLOOR + LEG * .995, hipy, e)
    return (hx, hy), lean * sm(min(1, e * 1.05))
@mv('dumbbell_rdl', 'hinge', 'r', 5.0, 25)
def _(p):
    e = rep(p, .46, .06, .36, .12); h, t = hinge_pose(e)
    return pose(h, t, t * .15, u=e, ln=PLANT(), an=fk(180, 180))
@mv('band_romanian_deadlift', 'open', 'r', 5.0, 25)
def _(p):
    e = rep(p, .46, .06, .36, .12); h, t = hinge_pose(e)
    return pose(h, t, t * .15, u=e, ln=PLANT(), an=fk(180, 180))
@mv('standing_dumbbell_row', 'hinge', 'r', 4.6, 23)
def _(p):
    e = rep(p, .34, .10, .44, .12); h = (-.26, .865); t = 62
    return pose(h, t, 30, u=e, ln=PLANT(), an=fk(lerp(180, 236, sm(e)), lerp(180, 190, sm(e))))
@mv('supported_dumbbell_row', 'hinge', 'r', 4.8, 24)
def _(p):
    e = rep(p, .34, .12, .42, .12); h = (-.22, .88); t = 52
    return pose(h, t, 30, u=e, ln=PLANT(), an=fk(lerp(180, 232, sm(e)), lerp(180, 192, sm(e))))
@mv('band_row', 'open', 'r', 4.6, 23)
def _(p):
    e = rep(p, .32, .10, .46, .12); h = (0, FLOOR + LEG * .99)
    return pose(h, 3, 2, u=e, ln=PLANT(), an=fk(lerp(92, 196, sm(e)), lerp(92, 100, sm(e))))
@mv('standing_dumbbell_overhead_press', 'hinge', 'r', 4.8, 24)
def _(p):
    e = rep(p, .34, .10, .44, .12); h = (0, FLOOR + LEG * .995); t = 0 - 3 * sm(e)
    s = shoulder(h, t)
    tgt = hand(s[0] + .06, lerp(s[1] + .10, s[1] + .54, sm5(e)), -1)
    return pose(h, t, 0, u=e, ln=PLANT(), an=tgt)
@mv('machine_overhead_press', 'shoulder', 'r', 4.8, 24)
def _(p):
    e = rep(p, .34, .10, .44, .12); h = (0, .52); t = 0
    s = shoulder(h, t)
    return pose(h, t, 0, u=e, ln=foot(.45), an=hand(s[0] + .10, lerp(s[1] + .08, s[1] + .52, sm5(e)), -1))
@mv('standing_calf_raise', 'calf', 'r', 4.2, 21)
def _(p):
    e = rep(p, .24, .12, .52, .12); lift = .095 * sm5(e); h = (0, FLOOR + LEG * .995 + lift)
    return pose(h, 0, 0, u=e, ln=foot(.03 * sm5(e), FLOOR + lift, lerp(100, 150, sm5(e))), an=fk(180, 180))
@mv('seated_calf_raise', 'legext', 'r', 4.2, 21)
def _(p):
    e = rep(p, .24, .12, .52, .12); lift = .08 * sm5(e)
    return pose((0, .52), 0, 0, u=e, ln=foot(.46, FLOOR + lift, lerp(100, 148, sm5(e))), an=fk(150, 120))
@mv('pallof_press', 'open', 'r', 4.6, 23)
def _(p):
    e = rep(p, .32, .22, .32, .14); h = (0, FLOOR + LEG * .99); s = shoulder(h, 0)
    return pose(h, 0, 0, u=e, ln=PLANT(), an=hand(lerp(s[0] + .16, s[0] + .50, sm5(e)), s[1] - .17, -1))
@mv('cable_face_pull', 'open', 'r', 5.0, 25)
def _(p):
    e = rep(p, .36, .10, .42, .12); h = (0, FLOOR + LEG * .99); s = shoulder(h, 0)
    return pose(h, 0, 0, u=e, ln=PLANT(), an=hand(lerp(s[0] + .50, s[0] + .14, sm5(e)), s[1] + .06, 1), lat=(lerp(12, 60, sm(e)), lerp(6, 40, sm(e))))
@mv('leg_swing', 'open', 'r', 2.2, 22)
def _(p):
    w = math.sin(2 * math.pi * p); th = 178 - 38 * w; sh = th + 12 + 22 * max(0, -w)
    return pose((0, FLOOR + LEG * .995), 0, 0, ln=PLANT(), lf=fk(th, sh), an=fk(150, 150), af=fk(150, 150))
@mv('knee_to_chest', 'open', 'r', 4.2, 21)
def _(p):
    e = rep(p, .36, .22, .30, .12); h = (0, FLOOR + LEG * .995)
    th = lerp(180, 44, sm5(e)); sh = lerp(180, 168, sm5(e))
    kx, ky = h[0] + LT * math.sin(math.radians(th)), h[1] + LT * math.cos(math.radians(th))
    s = shoulder(h, 0); g = sm5((ky - 1.12) / .18)
    return pose(h, lerp(0, -3, e), 0, u=e, ln=PLANT(), lf=fk(th, sh), an=hand(lerp(s[0] + .12, kx + .02, g), lerp(s[1] - .40, ky + .10, g), 1), af=hand(lerp(s[0] + .14, kx + .04, g), lerp(s[1] - .40, ky + .08, g), 1))
@mv('neck_roll', 'open', 'r', 5.6, 28)
def _(p):
    e = .5 - .5 * math.cos(2 * math.pi * p)
    return pose((0, FLOOR + LEG * .995), 0, lerp(-30, 52, e), ln=PLANT(), an=fk(180, 180))
@mv('arm_circle', 'open', 'l', 2.4, 24)
def _(p):
    a = 2 * math.pi * p; h = (0, FLOOR + LEG * .995); s = shoulder(h, 0); R = .50
    hd = lambda q: hand(s[0] + R * math.sin(q), s[1] + R * math.cos(q), 1 if math.cos(q) < .2 else -1)
    return pose(h, 0, 0, ln=PLANT(), an=hd(a), af=hd(a))
# ---------------------------------------------------------------- seated machines and benches
SEAT = (0, .52)
def seated_legs(): return foot(.45)
@mv('machine_chest_press', 'chest', 'r', 4.6, 23)
def _(p):
    e = rep(p, .34, .10, .44, .12); h = SEAT; t = -3
    return pose(h, t, 0, u=e, ln=seated_legs(), an=hand(lerp(.17, .47, sm5(e)), .90 - .02 * e, -1), lat=(lerp(40, 12, sm(e)), lerp(20, 6, sm(e))))
@mv('incline_machine_press', 'chest', 'r', 4.6, 23)
def _(p):
    e = rep(p, .34, .10, .44, .12); h = SEAT; t = -32; c = on_torso(h, t, .50, lerp(.12, .50, sm5(e)))
    return pose(h, t, -20, u=e, ln=seated_legs(), an=hand(c[0], c[1], -1), lat=(lerp(40, 12, sm(e)), lerp(20, 6, sm(e))))
@mv('dumbbell_chest_press', 'bench', 'r', 4.6, 23)
def _(p):
    e = rep(p, .34, .10, .44, .12); h = (0, .60); t = -90; c = on_torso(h, t, .46, lerp(.12, .52, sm5(e)))
    return pose(h, t, -90, u=e, ln=seated_legs(), an=hand(c[0], c[1], -1), lat=(lerp(58, 14, sm(e)), lerp(24, 6, sm(e))))
@mv('incline_dumbbell_press', 'bench', 'r', 4.6, 23)
def _(p):
    e = rep(p, .34, .10, .44, .12); h = (0, .60); t = -32; c = on_torso(h, t, .48, lerp(.12, .52, sm5(e)))
    return pose(h, t, -22, u=e, ln=seated_legs(), an=hand(c[0], c[1], -1), lat=(lerp(58, 14, sm(e)), lerp(24, 6, sm(e))))
@mv('lat_pulldown', 'pulldown', 'r', 4.6, 23)
def _(p):
    e = rep(p, .34, .10, .44, .12); h = SEAT; t = lerp(-4, -13, sm(e)); s = shoulder(h, t)
    return pose(h, t, -8, u=e, ln=seated_legs(), an=hand(s[0] + lerp(.08, .06, sm(e)), s[1] + lerp(.47, .06, sm5(e)), 1), lat=(lerp(30, 38, sm(e)), lerp(10, 25, sm(e))))
@mv('seated_cable_row', 'row', 'r', 4.8, 24)
def _(p):
    e = rep(p, .36, .10, .42, .12); h = SEAT; t = lerp(24, -2, sm5(e)); neck = lerp(12, 0, e)
    return pose(h, t, neck, u=e, ln=seated_legs(), an=hand(lerp(.56, .13, sm5(e)), .64, -1), lat=(10, 6))
@mv('reverse_pec_deck', 'row', 'r', 4.6, 23)
def _(p):
    e = rep(p, .34, .12, .42, .12)
    return pose(SEAT, 0, 0, u=e, ln=seated_legs(), an=fk(90, 90), lat=(lerp(8, 82, sm5(e)), lerp(8, 82, sm5(e))))
@mv('leg_press', 'legpress', 'r', 5.0, 25)
def _(p):
    e = rep(p, .38, .10, .40, .12); h = (0, .55); t = -48
    end = (.62, 1.17); start = (.37, .92)
    return pose(h, t, -30, u=e, ln=foot(lerp(start[0], end[0], sm5(e)), lerp(start[1], end[1], sm5(e)), 10), an=fk(150, 122))
@mv('leg_extension', 'legext', 'r', 4.6, 23)
def _(p):
    e = rep(p, .34, .14, .40, .12); sh = lerp(184, 100, sm5(e))
    return pose(SEAT, -4, -2, u=e, ln=fk(92, sh), an=fk(160, 128))
@mv('seated_leg_curl', 'legcurl', 'r', 4.6, 23)
def _(p):
    e = rep(p, .34, .14, .40, .12); sh = lerp(98, 205, sm5(e))
    return pose(SEAT, -4, -2, u=e, ln=fk(92, sh), an=fk(160, 128))
@mv('lying_leg_curl', 'prone', 'r', 4.6, 23)
def _(p):
    e = rep(p, .34, .14, .40, .12); h = (0, .58); sh = lerp(-92, 6, sm5(e))
    return pose(h, 90, 96, u=e, ln=fk(-92, sh), an=hand(.90, .46, 1))
@mv('back_extension_machine', 'shoulder', 'r', 4.8, 24)
def _(p):
    e = rep(p, .38, .14, .36, .12); h = SEAT; t = lerp(30, -12, sm5(e)); c = on_torso(h, t, .38, .10)
    return pose(h, t, t * .5, u=e, ln=seated_legs(), an=hand(c[0], c[1], -1), af=hand(c[0] + .02, c[1] + .03, -1))
@mv('assisted_pull_up', 'pullup', 'r', 5.0, 25)
def _(p):
    e = rep(p, .38, .10, .40, .12); t = lerp(-4, -12, sm(e)); hy = lerp(.78, 1.04, sm5(e)); h = (-.05, hy)
    return pose(h, t, lerp(0, -20, e), u=e, ln=fk(172, -78), an=hand(.06, 1.80, 1), lat=(lerp(28, 38, sm(e)), lerp(10, 20, sm(e))))
# ---------------------------------------------------------------- floor work
BL = LTO + LT + LS                 # shoulder joint to ankle joint with a straight body
def plank_line(sy, fx, fy=.125):
    """straight body from planted toes (fx, fy) to a shoulder at height sy; returns hip, torso angle, shoulder x"""
    sx = fx + math.sqrt(BL * BL - (sy - fy) ** 2); S = (sx, sy); A = (fx, fy); d = math.dist(S, A)
    hip = (S[0] + (A[0] - S[0]) / d * LTO, S[1] + (A[1] - S[1]) / d * LTO)
    t = math.degrees(math.atan2(S[0] - hip[0], S[1] - hip[1])); return hip, t, sx
def toes(fx, fy=.125): return foot(fx, fy, 128)
@mv('push_up', 'plank', 'r', 4.4, 22)
def _(p):
    e = rep(p, .36, .06, .46, .12); fx = -.95; sy = lerp(.592, .27, sm5(e)); hip, t, sx = plank_line(sy, fx)
    hx = fx + math.sqrt(BL * BL - (.592 - .125) ** 2)
    return pose(hip, t, t + 6, u=e, ln=toes(fx), an=hand(hx, .075, -1, 90), lat=(lerp(8, 42, sm(e)), lerp(4, 18, sm(e))))
@mv('incline_push_up', 'plank', 'r', 4.4, 22)
def _(p):
    e = rep(p, .36, .06, .46, .12); hy = .52; sy0 = hy + .515; fx = -.70; sy = lerp(sy0, hy + .22, sm5(e)); hip, t, sx = plank_line(sy, fx)
    hx = fx + math.sqrt(BL * BL - (sy0 - .125) ** 2)
    return pose(hip, t, t + 6, u=e, ln=toes(fx), an=hand(hx, hy, -1, 90), lat=(lerp(8, 40, sm(e)), lerp(4, 18, sm(e))))
@mv('forearm_plank', 'plank', 'h', 5.0, 20)
def _(p):
    fx = -.90; sy = .391 + .004 * math.sin(2 * math.pi * p); hip, t, sx = plank_line(sy, fx)
    return pose(hip, t, t + 8, ln=toes(fx), an=hand(sx + .218, .075, -1, 90), lat=(6, 0))
@mv('prone_cobra', 'mobility', 'r', 5.4, 27)
def _(p):
    e = rep(p, .28, .36, .28, .08); t = lerp(88, 60, sm5(e)); h = (0, .115)
    return pose(h, t, t - 12, u=e, ln=foot(-.88, .115, 160), an=fk(lerp(-98, -72, sm5(e)), lerp(-98, -72, sm5(e))), lat=(lerp(22, 38, e), lerp(22, 38, e)))
def bridge_pose(e):
    S = (-.50, .125); b = math.radians(lerp(2, 44, sm5(e))); h = (S[0] + LTO * math.cos(b), S[1] + LTO * math.sin(b))
    t = math.degrees(math.atan2(S[0] - h[0], S[1] - h[1]))
    return pose(h, t, -90, u=e, ln=foot(.47, .09, 100), an=fk(92, 92), lat=(14, 8))
@mv('glute_bridge', 'mobility', 'r', 4.6, 23)
def _(p): return bridge_pose(rep(p, .30, .22, .34, .14))
@mv('glute_bridge_hold', 'mobility', 'h', 5.0, 20)
def _(p): return bridge_pose(.97 + .03 * math.sin(2 * math.pi * p))
@mv('dead_bug', 'deadbug', 'r', 4.8, 24, alt=1)
def _(p):
    e = rep(p, .30, .20, .32, .18); g = sm5(e); h = (0, .14)
    return pose(h, -90, -90, u=e, ln=fk(0, 90), lf=fk(lerp(0, 86, g), lerp(90, 86, g)), an=fk(lerp(0, -86, g), lerp(0, -86, g)), af=fk(0, 0), lat=(10, 6))
def quad(e=0, hxs=0):
    return (0, .52), 80
@mv('bird_dog', 'mobility', 'r', 5.2, 26, alt=1)
def _(p):
    e = rep(p, .34, .22, .30, .14); g = sm5(e); h, t = quad(); s = shoulder(h, t)
    wr = (s[0] - .01, .075)
    ext_leg = foot(lerp(-.44, -.89, g), lerp(.075, .52, g), 100, 1)
    ext_arm = hand(lerp(wr[0], s[0] + .53, g), lerp(.075, s[1] - .0, g), 1)
    return pose(h, t, t, u=e, ln=ext_leg, lf=foot(-.44, .075, 140), an=hand(wr[0], wr[1], 1, 90), af=ext_arm)
@mv('cat_cow', 'mobility', 'r', 5.6, 28)
def _(p):
    e = .5 - .5 * math.cos(2 * math.pi * p); c = lerp(-1, 1, e); h, t = quad(); s = shoulder(h, t)
    return pose(h, t, t + lerp(38, -34, e), c=c, ln=foot(-.44, .075, 140), an=hand(s[0] - .01, .075, 1, 90))
@mv('gentle_thoracic_rotations', 'mobility', 'r', 5.6, 28)
def _(p):
    e = rep(p, .36, .12, .38, .14); g = sm5(e); h, t = quad(); s = shoulder(h, t); H0 = (s[0] - .04, s[1] + .20)
    ua = lerp(158, 12, g); d = dirv(ua); E = (s[0] + d[0] * LU, s[1] + d[1] * LU); fa = math.degrees(math.atan2(H0[0] - E[0], H0[1] - E[1]))
    return pose(h, t, t, u=e, ln=foot(-.44, .075, 140), an=fk(ua, fa), af=hand(s[0] - .01, .075, 1, 90), lat=(lerp(14, 64, g), lerp(6, 40, g), 6, 4))
@mv('thoracic_extension_roller', 'mobility', 'r', 5.6, 28)
def _(p):
    e = rep(p, .34, .26, .30, .10); g = sm5(e); h = (0, .13); s = shoulder(h, -90)
    return pose(h, -90 - 6 * g, -90 - 38 * g, c=g, u=e, ln=foot(.47, .09, 100), an=hand(s[0] - .12 - .04 * g, s[1] + .16, 1), af=hand(s[0] - .12 - .04 * g, s[1] + .16, 1), lat=(50, 20))
# ---------------------------------------------------------------- lunges and step-ups
@mv('dumbbell_reverse_lunge', 'hinge', 'r', 4.8, 24, alt=1)
def _(p):
    e = rep(p, .34, .10, .38, .18); g = sm5(e)
    h = (lerp(0, -.24, g), lerp(FLOOR + LEG * .995, .60, g)); rear = foot(lerp(-.02, -.62, g), lerp(.08, .13, sm(g)), lerp(100, 138, sm(g)))
    return pose(h, lerp(0, 6, g), 2, u=e, ln=foot(0), lf=rear, an=fk(180, 180))
@mv('dumbbell_split_squat', 'hinge', 'r', 4.6, 23)
def _(p):
    e = rep(p, .38, .10, .38, .14); g = sm5(e)
    h = (-.03, lerp(.92, .56, g))
    return pose(h, lerp(2, 6, g), 2, u=e, ln=foot(.22), lf=foot(-.36, .125, 138), an=fk(180, 180))
@mv('dumbbell_step_up', 'step', 'r', 5.0, 25)
def _(p):
    e = rep(p, .36, .12, .38, .14); g = sm5(e)
    h = (lerp(.12, .30, g), lerp(.93, 1.335, g))
    far = foot(lerp(.0, .34, sm(g)), lerp(.09, .84, g), lerp(125, 100, sm(g)))
    return pose(h, lerp(10, 2, g), 3, u=e, ln=foot(.30, .48), lf=far, an=fk(180, 180))
# ---------------------------------------------------------------- stretches and mobility
def keyflow(p, seq):
    """seq = [(pose, seconds of hold, seconds to arrive from the previous pose), ...] around one cycle; poses ease into each other"""
    T = sum(h + a for _, h, a in seq); t = p * T; acc = 0
    for i, (ps, h, a) in enumerate(seq):
        prev = seq[i - 1][0]
        if t < acc + a: return mix(prev, ps, sm5((t - acc) / a))
        acc += a
        if t < acc + h: return ps
        acc += h
    return seq[0][0]
def fl_total(seq): return sum(h + a for _, h, a in seq)
def hang(h, t, dx=.05): s = shoulder(h, t); return hand(s[0] + dx, s[1] - .50, 1)
def flow_pose(h, t, ln, lf, an, af=None, n=None, c=0, lat=None): return pose(h, t, t * .3 if n is None else n, c=c, ln=ln, lf=lf, an=an, af=af, lat=lat)
_ST = flow_pose((0, FLOOR + LEG * .995), 0, foot(0), foot(0), hang((0, FLOOR + LEG * .995), 0))
def _wgs():
    s = lambda h, t: shoulder(h, t)
    stand = _ST
    h1 = (-.05, .50); t1 = 80; s1 = s(h1, t1)
    down = flow_pose(h1, t1, foot(.45), foot(-.62, .13, 138), hand(s1[0] - .06, .075, -1, 90), hand(s1[0] - .07, .075, -1, 90))
    t2 = 74; s2 = s(h1, t2)
    rot = flow_pose(h1, t2, foot(.45), foot(-.62, .13, 138), hand(s2[0] + .06, s2[1] + .52, -1), hand(s2[0] - .08, .075, -1, 90), lat=(14, 6, 14, 6))
    h3 = (-.15, .74); t3 = 78; s3 = s(h3, t3)
    back = flow_pose(h3, t3, foot(.45, .12, 70), foot(-.62, .13, 138), hand(.36, .30, -1), hand(.34, .28, -1))
    return [(stand, .8, 0), (down, 1.0, 1.9), (rot, 1.7, 1.5), (down, .4, 1.4), (back, 1.7, 1.7), (stand, .6, 2.2)]
WGS = _wgs()
@mv('worlds_greatest_stretch', 'mobility', 'f', fl_total(WGS), 56, alt=1, tol=.12)
def _(p): return keyflow(p, WGS)
def _hamadd():
    h0 = (0, .52); s0 = shoulder(h0, 0)
    up = flow_pose(h0, 0, foot(.77, .14, 70), foot(-.44, .075, 140), hand(.10, .70, -1), hand(.10, .70, -1))
    t1 = 66; h1 = (-.04, .52); s1 = shoulder(h1, t1)
    fold = flow_pose(h1, t1, foot(.77, .14, 70), foot(-.44, .075, 140), hand(.52, .24, -1), hand(.50, .24, -1))
    t2 = 82; h2 = (-.16, .50); s2 = shoulder(h2, t2)
    rock = flow_pose(h2, t2, foot(.77, .14, 70), foot(-.44, .075, 140), hand(.54, .20, -1), hand(.52, .20, -1))
    return [(up, 1.0, 0), (fold, 2.4, 2.2), (rock, 2.0, 1.8), (fold, .6, 1.6), (up, 1.0, 2.0)]
HAM = _hamadd()
@mv('hamstring_adductor_flow', 'mobility', 'f', fl_total(HAM), 48, alt=1, tol=.12)
def _(p): return keyflow(p, HAM)
def _fullflow():
    st = _ST
    hsq = (-.24, .60); tsq = 40; ssq = shoulder(hsq, tsq)
    squat = flow_pose(hsq, tsq, foot(0), foot(0), hand(ssq[0] + .30, ssq[1] + .06, -1))
    hf = (-.12, .90); tf = 140; sf = shoulder(hf, tf)
    fold = flow_pose(hf, tf, foot(0), foot(0), hand(sf[0] + .03, .075, -1, 90), hand(sf[0] + .05, .075, -1, 90))
    # downward dog: hands and feet planted, hips are the apex
    hx = .62; A = (-.52, .10); S = (hx, .075 + .532)
    d = math.dist(S, A); a = (LTO * LTO - (LT + LS) ** 2 + d * d) / (2 * d); hgt = math.sqrt(max(0, LTO * LTO - a * a)) if False else None
    # circles around S (radius LTO) and A (radius LEG*.99): pick the upper intersection
    r1, r2 = LTO, LEG * .985; a1 = (r1 * r1 - r2 * r2 + d * d) / (2 * d); hh = math.sqrt(max(0, r1 * r1 - a1 * a1))
    ux, uy = (A[0] - S[0]) / d, (A[1] - S[1]) / d; mx, my = S[0] + ux * a1, S[1] + uy * a1; H = (mx - uy * hh, my + ux * hh)
    if H[1] < mx * 0 + S[1]: H = (mx + uy * hh, my - ux * hh)
    td = math.degrees(math.atan2(S[0] - H[0], S[1] - H[1]))
    dog = flow_pose(H, td, foot(A[0], A[1], 100), foot(A[0], A[1], 100), hand(hx, .075, -1, 90), hand(hx, .075, -1, 90), n=td + 10)
    return [(st, .8, 0), (squat, 1.4, 2.4), (fold, 1.4, 2.6), (dog, 3.0, 2.6), (fold, .8, 2.4), (st, .8, 2.6)]
FULL = _fullflow()
@mv('full_body_mobility_flow', 'mobility', 'f', fl_total(FULL), 54, tol=.2)
def _(p): return keyflow(p, FULL)
@mv('kneeling_hip_flexor_stretch', 'mobility', 'h', 6.0, 24)
def _(p):
    b = .5 - .5 * math.cos(2 * math.pi * p); h = (lerp(.0, .07, b), .55 - .005 * b)
    return pose(h, lerp(0, -5, b), lerp(0, -8, b), ln=foot(.46), lf=foot(-.46, .10, 140), an=hand(h[0] + .02, h[1] + .12, 1), lat=(56, 30))
@mv('standing_calf_stretch', 'wallf', 'h', 6.0, 24)
def _(p):
    b = .5 - .5 * math.cos(2 * math.pi * p); h = (lerp(-.09, -.03, b), .917); t = lerp(19, 23, b); s = shoulder(h, t)
    return pose(h, t, t * .3, ln=foot(-.38), lf=foot(.22), an=hand(.60, s[1] - .02, -1), lat=(8, 4))
@mv('standing_quad_stretch', 'open', 'h', 6.0, 24)
def _(p):
    b = .5 - .5 * math.cos(2 * math.pi * p); h = (0, FLOOR + LEG * .995)
    return pose(h, lerp(0, -3, b), 0, ln=foot(0), lf=fk(lerp(178, 174, b), lerp(-34, -42, b)), an=fk(100, 96), af=fk(lerp(200, 203, b), lerp(206, 208, b)), lat=(10, 4, 4, 0))
@mv('chest_stretch', 'open', 'h', 6.0, 24)
def _(p):
    b = .5 - .5 * math.cos(2 * math.pi * p); h = (0, FLOOR + LEG * .995)
    return pose(h, lerp(-2, -8, b), lerp(-4, -14, b), ln=foot(0), an=fk(lerp(196, 232, b), lerp(200, 226, b)), lat=(2, 0))
@mv('seated_hamstring_stretch', 'mobility', 'h', 6.0, 24)
def _(p):
    b = .5 - .5 * math.cos(2 * math.pi * p); h = (0, .135); t = lerp(30, 46, b); s = shoulder(h, t)
    return pose(h, t, t * .4, ln=foot(.88, .135, 72), lf=foot(.36, .08, 100), an=hand(s[0] + .26, .20, -1), af=hand(s[0] + .24, .22, -1))
@mv('seated_torso_twist', 'mobility', 'h', 6.4, 26)
def _(p):
    b = .5 - .5 * math.cos(2 * math.pi * p); h = (0, .135)
    return pose(h, lerp(0, 3, b), 0, ln=foot(.88, .135, 72), lf=foot(.36, .08, 100), an=fk(lerp(120, 200, b), lerp(100, 190, b)), af=fk(lerp(110, 60, b), lerp(100, 80, b)), lat=(lerp(12, 62, b), lerp(6, 40, b), lerp(60, 14, b), lerp(40, 6, b)))
@mv('single_leg_balance', 'open', 'h', 6.0, 24)
def _(p):
    a = 2 * math.pi * p; h = (.008 * math.sin(a), FLOOR + LEG * .995)
    return pose(h, 1.2 * math.sin(a + 1), 0, ln=foot(0), lf=fk(90 + 2 * math.sin(a), 178), an=fk(180, 180), lat=(68 + 6 * math.sin(a), 20, 68 - 6 * math.sin(a), 20))
@mv('standing_hamstring_sweep', 'open', 'r', 4.8, 24)
def _(p):
    e = rep(p, .38, .12, .38, .12); g = sm5(e); t = lerp(0, 80, g); h = (lerp(0, -.14, g), lerp(.955, .905, g)); s = shoulder(h, t)
    return pose(h, t, t * .3, u=e, ln=foot(.27, .12, 70), lf=foot(-.06), an=hand(s[0] + .05, s[1] - .50, -1), af=hand(s[0] + .06, s[1] - .50, -1))
@mv('wall_slide', 'wallb', 'r', 5.2, 26)
def _(p):
    e = rep(p, .38, .20, .32, .10); g = sm5(e); h = (0, FLOOR + LEG * .995)
    return pose(h, 0, 0, u=e, ln=foot(0), an=fk(0, 0), lat=(lerp(88, 30, g), lerp(4, 24, g)))
@mv('childs_pose', 'mobility', 'h', 6.0, 24)
def _(p):
    b = .5 - .5 * math.cos(2 * math.pi * p); h = (0, .194 - .006 * b); t = 100 + 2 * b
    return pose(h, t, t, c=-.1 * b, ln=fk(105, -90), lf=fk(105, -90), an=hand(.98, .075, -1, 90), lat=(10, 4))
@mv('legs_up_wall', 'mobility', 'h', 6.0, 24)
def _(p):
    b = .5 - .5 * math.cos(2 * math.pi * p)
    return pose((0, .13), -90, -90, c=.1 * b, ln=fk(lerp(1, -1, b), lerp(1, -1, b)), an=fk(90, 90), lat=(30, 12))
@mv('lying_breathing', 'mobility', 'h', 5.0, 20)
def _(p):
    b = .5 - .5 * math.cos(2 * math.pi * p)
    return pose((0, .125 + .004 * b), -90, -90, c=.35 * b - .1, ln=foot(.88, .125, 72), lf=foot(.88, .125, 72), an=fk(92, 92), lat=(16, 8))
@mv('forward_fold', 'open', 'h', 6.4, 26)
def _(p):
    b = .5 - .5 * math.cos(2 * math.pi * p); t = lerp(136, 150, b); h = (lerp(-.12, -.16, b), lerp(.93, .90, b)); s = shoulder(h, t)
    return pose(h, t, t, ln=foot(0), an=hand(s[0] + .02, .08, -1), af=hand(s[0] + .04, .08, -1))
