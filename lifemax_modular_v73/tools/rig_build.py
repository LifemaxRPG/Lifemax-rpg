#!/usr/bin/env python3
"""Offline rig builder for the cat sprites.

Splits every 72x48 frame of the adult sheet into parts (ears, tail, whiskers) and finds where the legs and
the body can be stretched, then writes src/data/petRig.html (a JSON block the runtime reads).

The art draws a dark outline (palette slot 10) around every form. Cutting a frame along those outlines gives
"cells": each ear is a small cell, the tail is a chain of small ring cells, the hind leg is a mid-size cell.
Needs numpy and Pillow (build time only). Usage:  python3 tools/rig_build.py [--qa]
"""
import re, json, base64, io, sys
from collections import deque
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / 'src/data'
W, H = 72, 48
OUTLINE, WHISKER = 10, 190
BIG = 70          # cells with at least this many pixels belong to the torso group
RING_MAX = 45     # tail ring cells are small
# The tail may only be re-drawn at another length where it sticks out free of the body. In every sitting, lying,
# grooming or kneading pose it lies curled along the floor, and stretching that makes a spike, so those rows keep the original art.
FREE_TAIL = {'walk', 'walk_w', 'run', 'stretch', 'leap', 'rub', 'eat'}

def load_json(name, tag):
    s = (DATA / name).read_text(encoding='utf-8')
    return json.loads(re.search(r'id="%s">(.*?)</script>' % tag, s, re.S).group(1))

def sheet(b64):
    return np.array(Image.open(io.BytesIO(base64.b64decode(b64.split(',')[1]))))

def components4(M):
    h, w = M.shape
    lab = np.zeros((h, w), np.int32)
    comps = []
    for y in range(h):
        for x in range(w):
            if M[y, x] and lab[y, x] == 0:
                n = len(comps) + 1
                q = deque([(y, x)]); lab[y, x] = n; pts = []
                while q:
                    cy, cx = q.popleft(); pts.append((cy, cx))
                    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        ny, nx = cy + dy, cx + dx
                        if 0 <= ny < h and 0 <= nx < w and M[ny, nx] and lab[ny, nx] == 0:
                            lab[ny, nx] = n; q.append((ny, nx))
                comps.append(pts)
    return lab, comps

def adjacency(lab, n, reach=2):
    h, w = lab.shape
    adj = {i: set() for i in range(1, n + 1)}
    ys, xs = np.nonzero(lab)
    for y, x in zip(ys, xs):
        a = lab[y, x]
        for dy in range(-reach, reach + 1):
            for dx in range(-reach, reach + 1):
                yy, xx = y + dy, x + dx
                if 0 <= yy < h and 0 <= xx < w:
                    b = lab[yy, xx]
                    if b and b != a:
                        adj[a].add(b)
    return adj

def runs_of(pixels):
    """[(y,x)] -> flat [y,x0,x1, ...] in row order"""
    by = {}
    for y, x in pixels:
        by.setdefault(y, []).append(x)
    out = []
    for y in sorted(by):
        xs = sorted(by[y]); a = b = xs[0]
        for x in xs[1:]:
            if x == b + 1: b = x
            else: out += [y, a, b]; a = b = x
        out += [y, a, b]
    return out

def angle(u, v):
    nu, nv = np.hypot(*u), np.hypot(*v)
    if nu == 0 or nv == 0: return 0
    return np.degrees(np.arccos(max(-1, min(1, (u[0]*v[0] + u[1]*v[1]) / (nu*nv)))))

def segment_frame(fg, fb):
    S = (fg > 0) | (fb > 0)
    if not S.any():     # an empty cell (some kitten rows are blank)
        return {'H': None, 'ears': [], 'tail': None, 'whisk': [], 'bottom': 0}, S, np.zeros((H, W), np.int16), S.copy()
    wall = (fg == OUTLINE)
    lab, comps = components4(S & ~wall)
    n = len(comps)
    st = {}
    for i, pts in enumerate(comps, 1):
        ys = [p[0] for p in pts]; xs = [p[1] for p in pts]
        st[i] = dict(id=i, area=len(pts), cy=sum(ys)/len(ys), cx=sum(xs)/len(xs), y0=min(ys), y1=max(ys), x0=min(xs), x1=max(xs), pts=pts)
    adj = adjacency(lab, n)
    big = {i for i, c in st.items() if c['area'] >= BIG}
    small = [c for c in st.values() if c['area'] < BIG]
    face = [(y, x) for y in range(H) for x in range(W) if fg[y, x] in (160, 170, 180, 200, 210)]
    Hc = (sum(p[1] for p in face)/len(face), sum(p[0] for p in face)/len(face)) if face else None
    # tail chain: the longest path through ring-sized cells that starts beside a big cell
    sm = {c['id'] for c in small}
    best = []
    def dfs(path, seen):
        nonlocal best
        if len(path) > len(best): best = list(path)
        for nb in adj[path[-1]]:
            if nb in sm and nb not in seen and st[nb]['area'] <= RING_MAX:
                seen.add(nb); path.append(nb); dfs(path, seen); path.pop(); seen.discard(nb)
    for c in small:
        if c['area'] <= RING_MAX and (adj[c['id']] & big):
            dfs([c['id']], {c['id']})
    tail = best if len(best) >= 3 else []
    mainc = max(st.values(), key=lambda c: c['area']) if st else None
    # orient the chain: the base end is the one nearest the rear of the body
    if tail and mainc:
        rear_x = min(p[1] for p in mainc['pts'])
        rear_y = np.mean([p[0] for p in mainc['pts'] if p[1] <= rear_x + 1])
        d0 = np.hypot(st[tail[0]]['cx'] - rear_x, st[tail[0]]['cy'] - rear_y)
        d1 = np.hypot(st[tail[-1]]['cx'] - rear_x, st[tail[-1]]['cy'] - rear_y)
        if d1 < d0: tail = tail[::-1]
        # trim stray cells at the tip end: the chain should keep its heading
        cen = [(st[i]['cx'], st[i]['cy']) for i in tail]
        while len(tail) > 4:
            v1 = (cen[-1][0]-cen[-2][0], cen[-1][1]-cen[-2][1]); v0 = (cen[-2][0]-cen[-3][0], cen[-2][1]-cen[-3][1])
            # how far the last ring sits off the line the chain was following
            nv0 = np.hypot(*v0) or 1.0
            off = abs(v1[0]*v0[1] - v1[1]*v0[0]) / nv0
            if angle(v1, v0) > 65 or off > 3.0: tail = tail[:-1]; cen = cen[:-1]
            else: break
    if tail:
        bot = max(y for y in range(H) if S[y].any())
        if sum(1 for i in tail if st[i]['cy'] >= bot - 9) >= 0.7 * len(tail):
            while len(tail) > 4 and st[tail[-1]]['cy'] < bot - 9: tail = tail[:-1]
    if tail:
        # a vertical chain that reaches the floor is a leg, not a tail (kittens have no rings on their tails)
        bot = max(y for y in range(H) if S[y].any())
        ys = [p[0] for i in tail for p in st[i]['pts']]; xs = [p[1] for i in tail for p in st[i]['pts']]
        if max(ys) >= bot - 2 and (max(ys) - min(ys)) >= 1.2 * (max(xs) - min(xs) + 1): tail = []
    tails = set(tail)
    ears = []
    if big:
        topc = min((st[i] for i in big), key=lambda c: c['y0'])
        for c in small:
            if c['id'] in tails or c['area'] < 5: continue
            near = [b for b in big if b in adj[c['id']]]
            if not near: continue
            b = st[near[0]]
            if Hc is not None: ok = c['cy'] < Hc[1] - 3 and abs(c['cx'] - Hc[0]) <= 15
            else: ok = c['cy'] < b['cy'] - 3 and b['x0'] - 4 <= c['cx'] <= b['x1'] + 4 and c['cy'] < topc['y0'] + 12
            if ok: ears.append(c['id'])
    ears = sorted(ears, key=lambda i: -st[i]['area'])[:2]
    # part labels; outline pixels join the part they touch
    P = np.zeros((H, W), np.int16)
    for k, e in enumerate(ears): 
        for (y, x) in st[e]['pts']: P[y, x] = 1 + k
    for i in tail:
        for (y, x) in st[i]['pts']: P[y, x] = 100
    Q = P.copy()
    for y in range(H):
        for x in range(W):
            if S[y, x] and wall[y, x] and P[y, x] == 0:
                votes = {}
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        yy, xx = y+dy, x+dx
                        if (dy or dx) and 0 <= yy < H and 0 <= xx < W and P[yy, xx]:
                            votes[P[yy, xx]] = votes.get(P[yy, xx], 0) + 1
                if votes: Q[y, x] = max(votes, key=lambda k: (votes[k], -k))
    P = Q
    out = {'H': [round(Hc[0], 1), round(Hc[1], 1)] if Hc else None}
    # ears: base = where the ear touches the head, tip = farthest pixel
    earl = []
    for k, e in enumerate(ears):
        px = [(int(y), int(x)) for y, x in zip(*np.nonzero(P == 1 + k))]
        cell = st[e]['pts']
        contact = [p for p in cell if any(0 <= p[0]+dy < H and 0 <= p[1]+dx < W and lab[p[0]+dy, p[1]+dx] in big for dy in range(-2, 3) for dx in range(-2, 3))]
        if not contact: contact = [max(cell, key=lambda p: p[0])]
        bx = sum(p[1] for p in contact)/len(contact); by = sum(p[0] for p in contact)/len(contact)
        tip = max(px, key=lambda p: (p[1]-bx)**2 + (p[0]-by)**2)
        earl.append(dict(px=px, base=(bx, by), tip=(float(tip[1]), float(tip[0]))))
    earl.sort(key=lambda e: e['base'][0])
    out['ears'] = earl
    # tail
    if tail:
        px = [(int(y), int(x)) for y, x in zip(*np.nonzero(P == 100))]
        first = st[tail[0]]
        contact = [p for p in first['pts'] if any(0 <= p[0]+dy < H and 0 <= p[1]+dx < W and lab[p[0]+dy, p[1]+dx] in big for dy in range(-2, 3) for dx in range(-2, 3))]
        bpt = (sum(p[1] for p in contact)/len(contact), sum(p[0] for p in contact)/len(contact)) if contact else (first['cx'], first['cy'])
        ax = [bpt] + [(st[i]['cx'], st[i]['cy']) for i in tail]
        # extend to the tip end of the last ring
        last = st[tail[-1]]; pl = ax[-2] if len(ax) > 1 else bpt
        tp = max(last['pts'], key=lambda p: (p[1]-pl[0])**2 + (p[0]-pl[1])**2)
        ax.append((float(tp[1]), float(tp[0])))
        # light smoothing
        sm_ax = [ax[0]] + [((ax[i-1][0]+2*ax[i][0]+ax[i+1][0])/4, (ax[i-1][1]+2*ax[i][1]+ax[i+1][1])/4) for i in range(1, len(ax)-1)] + [ax[-1]]
        length = sum(np.hypot(sm_ax[i+1][0]-sm_ax[i][0], sm_ax[i+1][1]-sm_ax[i][1]) for i in range(len(sm_ax)-1))
        out['tail'] = dict(px=px, ax=sm_ax, thin=bool(length >= 8 and len(px)/max(length, 1) <= 6.9))
    else:
        out['tail'] = None
    # whiskers: the lightest outline-coloured strokes at the muzzle
    wk = [(int(y), int(x)) for y, x in zip(*np.nonzero((fg == WHISKER) & (P == 0)))]
    wl = []
    if wk and Hc is not None:
        M = np.zeros((H, W), bool)
        for y, x in wk: M[y, x] = True
        wl_lab, wc = components4(M)
        for pts in wc:
            root = min(pts, key=lambda p: (p[1]-Hc[0])**2 + (p[0]-Hc[1])**2)
            tipw = max(pts, key=lambda p: (p[1]-root[1])**2 + (p[0]-root[0])**2)
            if (tipw[1]-root[1])**2 + (tipw[0]-root[0])**2 >= 2:
                wl.append(dict(px=pts, root=(float(root[1]), float(root[0])), tip=(float(tipw[1]), float(tipw[0]))))
    out['whisk'] = wl
    # leg / body stretch regions on what is left of the body
    body = S & (P == 0)
    out['bottom'] = int(max(y for y in range(H) if S[y].any()))
    return out, S, P, body

def stretch_regions(body, S, head):
    if not body.any(): return None, None
    """rows [ya,yb) of the legs that can be stretched, columns [xa,xb) of the torso"""
    ys = [y for y in range(H) if body[y].any()]
    if not ys: return None, None
    bottom = max(ys)
    def maxrun(y):
        m = 0; r = 0
        for x in range(W):
            if body[y, x]: r += 1; m = max(m, r)
            else: r = 0
        return m
    y = bottom - 4
    while y > 0 and maxrun(y) < 16: y -= 1          # first wide row going up = the belly
    legs = None
    if 7 <= bottom - y <= 26 and all(0 < maxrun(yy) <= 16 for yy in range(y + 1, bottom - 3)) and maxrun(y) >= 22:
        ya = y + 2
        if bottom - 4 - ya >= 4: legs = (ya, bottom - 3)
    xs = [x for x in range(W) if body[:, x].any()]
    bw, bh = max(xs) - min(xs) + 1, max(ys) - min(ys) + 1
    bod = None
    if bw >= 1.35 * bh:
        xa, xb = min(xs), max(xs)
        hi = xa + 0.52*(xb-xa)
        if head: hi = min(hi, head[0] - 13)
        lo = xa + 0.28*(xb-xa)
        bod = (int(lo), int(hi)) if hi - lo >= 4 else None
    return legs, bod

def build_sheet(g, b, anims, qa=None):
    rig = {}
    for name, a in anims.items():
        frames = []
        for f in range(a['n']):
            fg = g[a['row']*H:(a['row']+1)*H, f*W:(f+1)*W]; fb = b[a['row']*H:(a['row']+1)*H, f*W:(f+1)*W]
            out, S, P, body = segment_frame(fg, fb)
            legs, bod = stretch_regions(body, S, out['H'])
            out['legs'] = legs; out['bod'] = bod
            frames.append(out)
        # a row stretches its legs / body only if every frame can, so the shape never pops between frames
        lg = all(fr['legs'] for fr in frames); bd = all(fr['bod'] for fr in frames)
        tl = all(fr['tail'] and fr['tail']['thin'] for fr in frames)
        rig[name] = dict(legs=lg, body=bd, tail=tl, frames=frames)
        if qa is not None: qa.append((name, a, g, b, frames))
    return rig

def enc_frame(fr, legs_ok, body_ok, tail_ok):
    r = {}
    if fr['H']: r['h'] = fr['H']
    r['e'] = [dict(r=runs_of(e['px']), b=[round(e['base'][0], 1), round(e['base'][1], 1)], t=[round(e['tip'][0], 1), round(e['tip'][1], 1)]) for e in fr['ears']]
    if fr['tail'] and tail_ok:
        r['t'] = dict(r=runs_of(fr['tail']['px']), a=[[round(p[0], 1), round(p[1], 1)] for p in fr['tail']['ax']])
    if fr['whisk']:
        r['w'] = [dict(r=runs_of(w['px']), b=[w['root'][0], w['root'][1]], t=[w['tip'][0], w['tip'][1]]) for w in fr['whisk']]
    if legs_ok: r['l'] = list(fr['legs'])
    if body_ok: r['x'] = list(fr['bod'])
    r['y'] = fr['bottom']
    return r

def sheet_sets(A, G):
    """name -> (ginger sheet, black sheet, anims, W, H)"""
    sets = {'adult': (A['coats'], A['anims'])}
    for i, st in enumerate(G['stages']): sets['st%d' % i] = (st['coats'], A['anims'])
    d = A['depth']
    sets['depth'] = (d['coats'], d['anims'])
    for i, st in enumerate(d['stages']): sets['dst%d' % i] = (st['coats'], d['anims'])
    return sets

def main():
    qa_kinds = [a.split('=')[1].split(',') for a in sys.argv if a.startswith('--qa=')]
    qa_kinds = qa_kinds[0] if qa_kinds else (['adult'] if '--qa' in sys.argv else [])
    only = [a.split('=')[1].split(',') for a in sys.argv if a.startswith('--only=')]
    A = load_json('petAssets.html', 'petAssets'); G = load_json('petGrowth.html', 'petGrowth')
    sets = sheet_sets(A, G)
    out = {'v': 2, 'W': W, 'H': H}
    for kind, (coats, anims) in sets.items():
        if only and kind not in only[0]: continue
        g = sheet(coats['ginger']); b = sheet(coats['black'])
        qa = [] if kind in qa_kinds else None
        rig = build_sheet(g, b, anims, qa)
        out[kind] = {n: dict(lg=int(r['legs']), bd=int(r['body']), tl=int(r['tail'] and n in FREE_TAIL), f=[enc_frame(fr, r['legs'], r['body'], r['tail'] and n in FREE_TAIL) for fr in r['frames']]) for n, r in rig.items()}
        ok = lambda k: sum(1 for r in rig.values() if r[k])
        print('%-6s rows %2d  tail ok %2d  legs ok %2d  body ok %2d  ears-per-frame %s' % (kind, len(rig), ok('tail'), ok('legs'), ok('body'),
              sorted({len(fr['ears']) for r in rig.values() for fr in r['frames']})))
        if qa is not None:
            qa_dir = ROOT / 'tools/qa'; qa_dir.mkdir(exist_ok=True); qa_images(qa, qa_dir, kind)
    txt = '<script type="application/json" id="petRig">' + json.dumps(out, separators=(',', ':')) + '</script>'
    (DATA / 'petRig.html').write_text(txt, encoding='utf-8')
    print('petRig.html', len(txt), 'bytes')

def qa_images(qa, outdir, kind='adult'):
    scale = 3
    for i in range(0, len(qa), 7):
        chunk = qa[i:i+7]; cols = 4
        im = Image.new('RGB', (W*scale*cols, H*scale*len(chunk)), (0, 0, 0)); d = ImageDraw.Draw(im)
        for r, (name, a, g, b, frames) in enumerate(chunk):
            d.text((2, r*H*scale + 2), name, fill=(255, 255, 255))
            for k in range(cols):
                f = round(k*(a['n']-1)/(cols-1)) if a['n'] > 1 else 0
                fg = g[a['row']*H:(a['row']+1)*H, f*W:(f+1)*W]
                tile = Image.new('RGB', (W, H), (30, 34, 44)); px = tile.load()
                for y in range(H):
                    for x in range(W):
                        if fg[y, x]: px[x, y] = (110, 112, 125)
                fr = frames[f]
                for e in fr['ears']:
                    for (y, x) in e['px']: px[x, y] = (235, 60, 60)
                if fr['tail']:
                    for (y, x) in fr['tail']['px']: px[x, y] = (70, 210, 100)
                for w in fr['whisk']:
                    for (y, x) in w['px']: px[x, y] = (80, 200, 255)
                if fr['legs']:
                    for x in range(W):
                        if (fg[fr['legs'][0], x] > 0): px[x, fr['legs'][0]] = (255, 120, 255)
                if fr['bod']:
                    for y in range(H):
                        for xx in fr['bod']:
                            if fg[y, xx] > 0: px[xx, y] = (255, 160, 60)
                im.paste(tile.resize((W*scale, H*scale), Image.NEAREST), (k*W*scale, r*H*scale))
        im.save(outdir / ('%s_%d.png' % (kind, i//7)))

if __name__ == '__main__':
    main()
