"""Hi-res (2x) cat frames: smooth-scale the index art, slim the outline to one pixel, and redraw the face as a separate
layer that can show different expressions. Works on palette-index frames (see sprite_lib)."""
import sys; sys.path.insert(0, 'tools')
from sprite_lib import *

def scale2x(fr):
    h, w = fr.shape; P = np.pad(fr, 1, mode='edge'); out = np.zeros((h * 2, w * 2), np.uint8)
    for y in range(h):
        for x in range(w):
            p = P[y + 1, x + 1]; a = P[y, x + 1]; b = P[y + 1, x + 2]; c = P[y + 1, x]; d = P[y + 2, x + 1]
            e0 = a if (c == a and c != d and a != b) else p
            e1 = b if (a == b and a != c and b != d) else p
            e2 = c if (d == c and d != b and c != a) else p
            e3 = d if (b == d and b != a and d != c) else p
            out[2 * y, 2 * x] = e0; out[2 * y, 2 * x + 1] = e1; out[2 * y + 1, 2 * x] = e2; out[2 * y + 1, 2 * x + 1] = e3
    return out

def slim_outline(fr):
    """2px outlines -> 1px (inner pixel removed on the top/left side; on the right/bottom outer edge, the inner one)."""
    h, w = fr.shape; o = fr == OUT; P = np.pad(fr, 2, mode='constant'); out = fr.copy()
    fill = lambda v: v != 0 and v != OUT
    for y in range(h):
        for x in range(w):
            if not o[y, x]: continue
            Y, X = y + 2, x + 2
            for (dy, dx) in ((0, 1), (1, 0)):          # axis: x then y; n1 = back, n2 = forward
                b1 = P[Y - dy, X - dx]; f1 = P[Y + dy, X + dx]; b2 = P[Y - 2 * dy, X - 2 * dx]; f2 = P[Y + 2 * dy, X + 2 * dx]
                # rule 1: fill behind, outline ahead (2px band, fill on the top/left)
                if fill(b1) and f1 == OUT: out[y, x] = b1; break
                # rule 2: outline behind, fill ahead, and nothing beyond the band (outer edge on the top/left)
                if b1 == OUT and fill(f1) and b2 == 0: out[y, x] = f1; break
    return out

# ---------------------------------------------------------------------------------------------------------------
# Region-based 2x: label the cells the outline encloses, smooth each region's shape, draw a clean 1px outline.
def _label_cells(fr):
    """4-connected components of non-outline opaque pixels; returns label map (0 = none) and count."""
    h, w = fr.shape; lab = np.zeros((h, w), np.int32); n = 0
    for y in range(h):
        for x in range(w):
            if fr[y, x] in (0, OUT) or lab[y, x]: continue
            n += 1; lab[y, x] = n; q = deque([(y, x)])
            while q:
                cy, cx = q.popleft()
                for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    yy, xx = cy + dy, cx + dx
                    if 0 <= yy < h and 0 <= xx < w and not lab[yy, xx] and fr[yy, xx] not in (0, OUT): lab[yy, xx] = n; q.append((yy, xx))
    return lab, n

def _nearest_fill(fr, lab):
    """give every outline pixel the label and class of the nearest cell pixel (so shapes meet in the middle of the outline)."""
    h, w = fr.shape; L = lab.copy(); C = fr.copy(); todo = (fr == OUT)
    C[todo] = 0
    q = deque([(y, x) for y in range(h) for x in range(w) if L[y, x]]); seen = L > 0
    while q:
        cy, cx = q.popleft()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            yy, xx = cy + dy, cx + dx
            if 0 <= yy < h and 0 <= xx < w and not seen[yy, xx] and fr[yy, xx] == OUT:
                seen[yy, xx] = True; L[yy, xx] = L[cy, cx]; C[yy, xx] = C[cy, cx]; q.append((yy, xx))
    return L, C

def _up2_soft(field):
    """bilinear 2x of a float map (edge-clamped)."""
    h, w = field.shape; P = np.pad(field, 1, mode='edge'); out = np.zeros((2 * h, 2 * w), np.float32)
    for oy in (0, 1):
        for ox in (0, 1):
            sy = -1 if oy == 0 else 1; sx = -1 if ox == 0 else 1
            out[oy::2, ox::2] = (9 * P[1:-1, 1:-1] + 3 * P[1 + sy:h + 1 + sy if sy > 0 else h + sy, 1:-1][:h] * 0 if False else 0)
    return out

def up2_labels(L, C):
    """smooth 2x of label map L and class map C by argmax over smoothed one-hot maps."""
    h, w = L.shape
    def bil(m):
        P = np.pad(m, 1, mode='edge').astype(np.float32); out = np.zeros((2 * h, 2 * w), np.float32)
        for oy in (0, 1):
            for ox in (0, 1):
                dy = -1 if oy == 0 else 1; dx = -1 if ox == 0 else 1
                a = P[1:h + 1, 1:w + 1]; b = P[1:h + 1, 1 + dx:w + 1 + dx]; c = P[1 + dy:h + 1 + dy, 1:w + 1]; d = P[1 + dy:h + 1 + dy, 1 + dx:w + 1 + dx]
                out[oy::2, ox::2] = (9 * a + 3 * b + 3 * c + d) / 16.0
        return out
    labs = [k for k in np.unique(L)]; best = np.full((2 * h, 2 * w), -1.0, np.float32); NL = np.zeros((2 * h, 2 * w), np.int32)
    for k in labs:
        s = bil((L == k).astype(np.float32)); upd = s > best; best[upd] = s[upd]; NL[upd] = k
    cl = [k for k in np.unique(C) if k != 0]; NC = np.zeros((2 * h, 2 * w), np.uint8)
    # class: argmax of smoothed class one-hots, restricted to classes that belong to the cell chosen at that pixel
    cellclasses = {k: set(np.unique(C[L == k]).tolist()) for k in labs if k != 0}
    scores = {c: bil((C == c).astype(np.float32)) for c in cl}
    bestc = np.full((2 * h, 2 * w), -1.0, np.float32)
    for c in cl:
        ok = np.vectorize(lambda l: c in cellclasses.get(l, ()))(NL) if False else None
    cls_cell = np.zeros((2 * h, 2 * w), bool)
    for c in cl:
        mask = np.zeros_like(NL, bool)
        for k, cs in cellclasses.items():
            if c in cs: mask |= (NL == k)
        s = np.where(mask, scores[c], -1.0); upd = s > bestc; bestc[upd] = s[upd]; NC[upd] = c
    NC[NL == 0] = 0
    return NL, NC

def hires_frame(fr):
    """72x48 index frame -> 144x96 with smooth shapes, a 1px outline and no face (face is drawn separately)."""
    lab, n = _label_cells(fr)
    L, C = _nearest_fill(fr, lab)
    NL, NC = up2_labels(L, C)
    out = NC.copy(); h, w = out.shape
    edge = np.zeros((h, w), bool); op = NL > 0
    P = np.pad(NL, 1, mode='constant')
    for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
        nb = P[1 + dy:h + 1 + dy, 1 + dx:w + 1 + dx]
        edge |= (nb != NL) & ((NL > 0) | (nb > 0))
    # outline sits on the shape's pixels along every cell boundary; between two cells keep it on one side only (the later label)
    own = np.zeros((h, w), bool)
    for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
        nb = P[1 + dy:h + 1 + dy, 1 + dx:w + 1 + dx]
        own |= (NL > 0) & ((nb == 0) | ((nb > 0) & (nb < NL)))
    out[own] = OUT
    out[NL == 0] = 0
    return out

# ---------------------------------------------------------------------------------------------------------------
# Face as a layer: find where the old face is, remove it, draw new eyes / nose / mouth / whiskers for an expression.
EYE = {150, 160, 170, 180}
def face_info(fr):
    """eye clusters (centres), nose and mouth centres in the ORIGINAL frame; None if the face is not front-on."""
    h, w = fr.shape; seen = np.zeros((h, w), bool); cl = []
    for y in range(h):
        for x in range(w):
            if fr[y, x] in EYE and not seen[y, x]:
                q = deque([(y, x)]); seen[y, x] = True; pts = []
                while q:
                    cy, cx = q.popleft(); pts.append((cy, cx))
                    for dy in (-1, 0, 1):
                        for dx in (-1, 0, 1):
                            yy, xx = cy + dy, cx + dx
                            if 0 <= yy < h and 0 <= xx < w and not seen[yy, xx] and fr[yy, xx] in EYE: seen[yy, xx] = True; q.append((yy, xx))
                if len(pts) >= 4: cl.append(pts)
    if len(cl) < 2: return None
    cl.sort(key=lambda p: -len(p)); cl = cl[:2]; cl.sort(key=lambda p: np.mean([c[1] for c in p]))
    ctr = [(np.mean([c[0] for c in p]) + .5, np.mean([c[1] for c in p]) + .5, len(p)) for p in cl]
    (ly, lx, ln), (ry, rx, rn) = ctr
    mx, my = (lx + rx) / 2, (ly + ry) / 2
    nose = [(y, x) for y in range(h) for x in range(w) if fr[y, x] in (120, 130, 140) and abs(x + .5 - mx) <= 3.2 and my < y + .5 <= my + 7]
    mouth = [(y, x) for y in range(h) for x in range(w) if fr[y, x] in (200, 210) and abs(x + .5 - mx) <= 5 and my < y + .5 <= my + 9]
    ny, nx = (np.mean([p[0] for p in nose]) + .5, np.mean([p[1] for p in nose]) + .5) if nose else (my + 3.2, mx)
    return dict(l=(ly, lx), r=(ry, rx), mid=(my, mx), nose=(ny, nx), mouth=(np.mean([p[0] for p in mouth]) + .5, np.mean([p[1] for p in mouth]) + .5) if mouth else (ny + 1.5, nx), span=rx - lx, tilt=ry - ly)

def clear_face(hr, info):
    """remove eyes, mouth, whiskers and the nose from a hi-res frame (neighbouring fur / muzzle fills in)."""
    my, mx = info['mid']; s = info['span']
    y0 = int((my - 4) * 2); y1 = int((info['mouth'][0] + 3) * 2); x0 = int((mx - s / 2 - 5) * 2); x1 = int((mx + s / 2 + 5) * 2)
    kill = np.zeros(hr.shape, bool)
    reg = np.zeros(hr.shape, bool); reg[max(0, y0):y1, max(0, x0):x1] = True
    kill |= reg & np.isin(hr, (150, 160, 170, 180, 200, 210))
    kill |= reg & np.isin(hr, (120, 130, 140)) & (np.abs(np.arange(hr.shape[1])[None, :] - mx * 2) < s)   # the nose, not the inner ears
    # whiskers poke out past the head: where nothing surrounds them they just disappear, only the ones lying on fur are filled in
    wk = (hr == 190); h2 = hr.copy(); h2[wk] = 0
    P = np.pad(h2 != 0, 1, mode='constant')
    for y, x in zip(*np.nonzero(wk)):
        n = int(P[y, x + 1]) + int(P[y + 2, x + 1]) + int(P[y + 1, x]) + int(P[y + 1, x + 2])
        if n >= 3: h2[y, x] = 255; kill[y, x] = True
    kill &= (h2 != 0) | wk
    out = inpaint(h2, kill)
    out[out == 255] = 0
    return out

def _px(hr, y, x, v):
    y = int(round(y)); x = int(round(x))
    if 0 <= y < hr.shape[0] and 0 <= x < hr.shape[1] and hr[y, x] != 0: hr[y, x] = v

def _eye(hr, cy, cx, kind, side=1, scale=1.0):
    """one eye at hi-res centre (cy, cx). kind: open, wide, sleepy, happy, closed, angry, sad. side: -1 left eye, +1 right eye."""
    rx = 3.6 * scale; ry = {'open': 3.5, 'wide': 4.5, 'sleepy': 3.3, 'angry': 3.5, 'sad': 3.5}.get(kind, 3.5) * scale
    if kind == 'happy':                                   # a cheerful arch: ^
        for dx in range(-int(rx), int(rx) + 1):
            t = dx / rx; yy = -(1 - t * t) * 2.8
            _px(hr, cy + yy + 1.4, cx + dx, 150); _px(hr, cy + yy + 2.4, cx + dx, 150)
        return
    if kind == 'closed':                                  # a sleepy curve: u
        for dx in range(-int(rx), int(rx) + 1):
            t = dx / rx; yy = (1 - t * t) * 1.4
            _px(hr, cy + yy, cx + dx, 150); _px(hr, cy + yy + 1, cx + dx, 150)
        return
    for yy in range(-int(ry) - 1, int(ry) + 2):
        for xx in range(-int(rx) - 1, int(rx) + 2):
            e = (xx / rx) ** 2 + (yy / ry) ** 2
            if e > 1.0: continue
            inner = xx * -side                            # + towards the nose
            if kind == 'angry' and yy < -ry * .30 + inner * .62: continue
            if kind == 'sad' and yy < -ry * .30 - inner * .62: continue
            v = 160 if e > .5 else 170
            if e > .74: v = 150
            if abs(xx) <= (1 if kind != 'wide' else 0.5) and e < .72: v = 150
            _px(hr, cy + yy, cx + xx, v)
    if kind in ('open', 'wide', 'angry', 'sad', 'sleepy'):
        _px(hr, cy - ry * .4, cx - rx * .45, 180); _px(hr, cy - ry * .4, cx - rx * .45 + 1, 180); _px(hr, cy - ry * .4 + 1, cx - rx * .45, 180)
    if kind == 'sleepy':                                  # heavy lid covering the top half, drawn as a dark line
        for xx in range(-int(rx) - 1, int(rx) + 2):
            for yy in range(-int(ry) - 1, 1):
                if (xx / rx) ** 2 + (yy / ry) ** 2 <= 1.0 and yy < -ry * .15: _px(hr, cy + yy, cx + xx, 0 if False else hr[int(round(cy + yy)), int(round(cx + xx))])
        for xx in range(-int(rx) - 1, int(rx) + 2): _px(hr, cy - ry * .15, cx + xx, 150)

def draw_face(hr, info, expr='neutral', ink=150):
    """draw an expression: neutral, happy, sleepy, surprised, angry, sad, blink."""
    out = hr.copy()
    ly, lx = [v * 2 for v in info['l']]; ry_, rx_ = [v * 2 for v in info['r']]
    ny, nx = [v * 2 for v in info['nose']]; my_, mx_ = [v * 2 for v in info['mouth']]
    k = {'neutral': ('open', 'open'), 'happy': ('happy', 'happy'), 'sleepy': ('sleepy', 'sleepy'), 'surprised': ('wide', 'wide'), 'angry': ('angry', 'angry'), 'sad': ('sad', 'sad'), 'blink': ('closed', 'closed')}[expr]
    _eye(out, ly, lx, k[0], side=-1); _eye(out, ry_, rx_, k[1], side=1)
    # nose: a small pink triangle
    for dy, w in ((0, 2), (1, 1)):
        for dx in range(-w + 1, w): _px(out, ny - 0.5 + dy, nx + dx, 130 if dy == 0 else 120)
    _px(out, ny - .5, nx - 1, 140)
    # mouth under the nose
    my2 = ny + 3.2
    if expr in ('neutral', 'sleepy', 'blink'):
        for dx, dy in ((-3, 0), (-2, 1), (-1, 1), (0, 0), (1, 1), (2, 1), (3, 0)): _px(out, my2 + dy, nx + dx, 200)
        _px(out, ny + 1.2, nx, 200); _px(out, ny + 2.2, nx, 200)
    elif expr == 'happy':
        for dx, dy in ((-4, 0), (-3, 1), (-2, 2), (-1, 2), (0, 2), (1, 2), (2, 2), (3, 1), (4, 0)): _px(out, my2 - 0.5 + dy, nx + dx, 200)
        for dx in (-1, 0, 1): _px(out, my2 + 2.5, nx + dx, 210)
        _px(out, ny + 1.2, nx, 200); _px(out, ny + 2.2, nx, 200)
    elif expr == 'surprised':
        for dy in (0, 1, 2):
            for dx in (-1, 0, 1):
                _px(out, my2 + dy, nx + dx, 200 if (dy in (0, 2) or dx != 0) else 120)
        _px(out, ny + 1.2, nx, 200)
    elif expr == 'angry':
        for dx, dy in ((-3, 1), (-2, 0), (-1, 0), (0, 0), (1, 0), (2, 0), (3, 1)): _px(out, my2 + dy, nx + dx, 200)
        _px(out, ny + 1.2, nx, 200); _px(out, ny + 2.2, nx, 200)
    elif expr == 'sad':
        for dx, dy in ((-3, 1), (-2, 0), (-1, 0), (0, 0), (1, 0), (2, 0), (3, 1)): _px(out, my2 + 1 + dy, nx + dx, 200)
        _px(out, ny + 1.2, nx, 200); _px(out, ny + 2.2, nx, 200)
    # whiskers: thin pale lines out from the cheeks
    for sgn, base in ((-1, lx), (1, rx_)):
        for i, (dy, dx) in enumerate(((-1, 3), (0, 4), (1, 5))):
            for t in range(0, 6):
                _px(out, ny + 1.2 + dy * (1 + t * .35), (base if sgn < 0 else base) + sgn * (4 + t + dx * .0) + (0 if sgn > 0 else 0), 190)
    return out
