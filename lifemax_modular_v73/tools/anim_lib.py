"""Authoring helpers for the 3D gym movements (see tools/make_moves.py).
World frame of a station: +x is the way the trainer faces, +y is up, metres, floor at y=0. Angles are degrees from straight up, positive toward +x.
A pose is a dict with: h=(x,y) hip joint, t torso, n neck, c spine curl (-1 round .. +1 arch), u effort 0..1,
and four limbs ln lf an af (near/far leg, near/far arm), each ('f', a, b) absolute angles or ('i', x, y, side[, foot/hand angle]) reach a target.
Leg ik: foot angle 100 is flat on the floor. Arm ik: hand angle 90 keeps a hand flat on the floor, left out the hand follows the forearm."""
import math
LT, LS, LTO, LU, LF = .448, .443, .528, .316, .218      # thigh, shin, hip joint to shoulder joint, upper arm, forearm to wrist joint
FLOOR = .08                                              # ankle joint height with the foot flat on the floor
def dirv(a): r = math.radians(a); return (math.sin(r), math.cos(r))
def ik2(o, t, l1, l2, s):
    dx, dy = t[0] - o[0], t[1] - o[1]; d = math.hypot(dx, dy); mx = l1 + l2 - 1e-3; mn = abs(l1 - l2) + 1e-3; d = min(max(d, mn), mx)
    be = math.atan2(dx, dy); al = math.acos(max(-1, min(1, (l1 * l1 + d * d - l2 * l2) / (2 * l1 * d)))); a1 = be - s * al
    kx, ky = o[0] + l1 * math.sin(a1), o[1] + l1 * math.cos(a1)
    return math.degrees(a1), math.degrees(math.atan2(t[0] - kx, t[1] - ky))
def shoulder(h, t): d = dirv(t); return (h[0] + d[0] * LTO, h[1] + d[1] * LTO)
def on_torso(h, t, along, fwd):
    """point at `along` metres up the torso line from the hip joint and `fwd` metres in front of it"""
    u = dirv(t); f = (math.cos(math.radians(t)), -math.sin(math.radians(t))); return (h[0] + u[0] * along + f[0] * fwd, h[1] + u[1] * along + f[1] * fwd)
def sm(p): p = min(1, max(0, p)); return p * p * (3 - 2 * p)           # smoothstep
def sm5(p): p = min(1, max(0, p)); return p * p * p * (p * (6 * p - 15) + 10)   # smootherstep: also zero acceleration at both ends
def lerp(a, b, k): return a + (b - a) * k
def mix(a, b, k):
    """blend two poses (same limb kinds); angles blend linearly because authors keep them continuous"""
    o = {}
    for key in ('t', 'n', 'c', 'u'): o[key] = lerp(a.get(key, 0), b.get(key, 0), k)
    o['lat'] = tuple(lerp(p, q, k) for p, q in zip(a.get('lat', (12, 6, 12, 6)), b.get('lat', (12, 6, 12, 6))))
    o['h'] = (lerp(a['h'][0], b['h'][0], k), lerp(a['h'][1], b['h'][1], k))
    for l in ('ln', 'lf', 'an', 'af'):
        x, y = a[l], b[l]; assert x[0] == y[0], (l, x, y)
        o[l] = (x[0],) + tuple(lerp(p, q, k) for p, q in zip(x[1:], y[1:]))
    return o
def pose(h, t=0, n=None, c=0, u=0, ln=None, lf=None, an=None, af=None, lat=None):
    """lat = (near upper, near forearm, far upper, far forearm) sideways swing of the arms in degrees (default 12, 6, 12, 6); a single pair applies to both arms"""
    if lat is None: lat = (12, 6, 12, 6)
    elif len(lat) == 2: lat = (lat[0], lat[1], lat[0], lat[1])
    return {'h': h, 't': t, 'n': t * .4 if n is None else n, 'c': c, 'u': u, 'ln': ln, 'lf': lf if lf is not None else ln, 'an': an, 'af': af if af is not None else an, 'lat': tuple(lat)}
def foot(x, y=FLOOR, ft=100, side=1): return ('i', x, y, side, ft)
def hand(x, y, side=1, hd=None): return ('i', x, y, side) + ((hd,) if hd is not None else ())
def fk(a, b): return ('f', a, b)
def pack(p):
    h = p['h']; v = [h[0], h[1], p['t'], p['n'], p['c'], p['u']]
    for l in ('ln', 'lf', 'an', 'af'):
        s = p[l]
        if s[0] == 'f': v += [s[1], s[2], 0, 0]
        else: v += [s[1], s[2], 1 if s[3] >= 0 else -1, s[4] if len(s) > 4 else -999]
    return v + [x for x in p.get('lat', (12, 6, 12, 6))]
def ltype(p): return ''.join(p[l][0] for l in ('ln', 'lf', 'an', 'af'))
def sample(fn, T, N):
    """N keys evenly over one cycle of T seconds from fn(phase 0..1); returns the data dict body"""
    keys = []; lt = None
    for i in range(N):
        p = fn(i / N); t = ltype(p); lt = lt or t; assert t == lt, ('limb kind changed', i, t, lt)
        keys.append([round(T * i / N, 4)] + [round(x, 3) for x in pack(p)])
    return {'T': T, 'lt': lt, 'K': keys}
def world_pts(p):
    """planar positions for checks: hip, shoulder, ankles, wrists (IK limbs solved with the rig lengths)"""
    h = p['h']; sh = shoulder(h, p['t']); out = {'hip': h, 'sh': sh}
    for name, spec, root, l1, l2 in (('ankN', p['ln'], h, LT, LS), ('ankF', p['lf'], h, LT, LS), ('wrN', p['an'], sh, LU, LF), ('wrF', p['af'], sh, LU, LF)):
        if spec[0] == 'i': a1, a2 = ik2(root, (spec[1], spec[2]), l1, l2, spec[3])
        else: a1, a2 = spec[1], spec[2]
        d1, d2 = dirv(a1), dirv(a2); k = (root[0] + d1[0] * l1, root[1] + d1[1] * l1); out[name] = (k[0] + d2[0] * l2, k[1] + d2[1] * l2); out[name[:-1] + 'K' + name[-1:]] = k
    return out
