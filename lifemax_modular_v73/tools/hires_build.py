#!/usr/bin/env python3
"""Build src/data/petHd.html: 2x walking sheets for every coat and growth stage, with the face removed (drawn at runtime)
and where each face is. Usage: python3 tools/hires_build.py"""
import sys; sys.path.insert(0, 'tools')
from hires import *
import json

HS = 2
ROWS = {'adult': ['walk', 'walk_w'], 'depth': ['walk_front', 'walk_back']}
COATS = ['ginger', 'gray', 'black', 'tuxedo']

def sources():
    s, m, A = load_block('petAssets.html', 'petAssets'); s2, m2, G = load_block('petGrowth.html', 'petGrowth')
    out = {'adult': (A['coats'], A['anims'], 'adult'), 'depth': (A['depth']['coats'], A['depth']['anims'], 'depth')}
    for i, st in enumerate(G['stages']): out['st%d' % i] = (st['coats'], A['anims'], 'adult')
    for i, st in enumerate(A['depth']['stages']): out['dst%d' % i] = (st['coats'], A['depth']['anims'], 'depth')
    return out

def build():
    block = {'v': 1, 'hs': HS, 'kinds': {}}
    for kind, (coats, anims, fam) in sources().items():
        names = ROWS[fam]; sheets = {c: dec(coats[c]) for c in COATS}
        n_max = max(anims[a]['n'] for a in names)
        W, H = 72, 48
        info = {}; out = {c: np.zeros((len(names) * H * HS, n_max * W * HS), np.uint8) for c in COATS}
        stats = []
        for r, a in enumerate(names):
            meta = anims[a]; info[a] = []
            for f in range(meta['n']):
                fr0 = frame(sheets['ginger'], meta['row'], f); fi = face_info(fr0) if fr0.any() else None
                if fi: info[a].append({k: ([round(v[0], 1), round(v[1], 1)] if isinstance(v, tuple) else round(float(v), 1)) for k, v in fi.items()})
                else: info[a].append(None)
                for c in COATS:
                    fr = frame(sheets[c], meta['row'], f)
                    hr = slim_free(scale2x(fr))
                    if fi and hr.any(): hr = clear_face(hr, fi)
                    out[c][r * H * HS:(r + 1) * H * HS, f * W * HS:(f + 1) * W * HS] = hr
            stats.append((a, sum(1 for x in info[a] if x), meta['n']))
        block['kinds'][kind] = {'anims': names, 'sheet': {c: enc(out[c]) for c in COATS}, 'face': info, 'n': {a: anims[a]['n'] for a in names}}
        print(kind, stats)
    return block

def slim_free(x): return x      # strong outlines are kept: no thinning

if __name__ == '__main__':
    b = build()
    p = DATA / 'petHd.html'
    p.write_text('<script type="application/json" id="petHd">' + json.dumps(b, separators=(',', ':')) + '</script>\n', encoding='utf-8')
    print('petHd.html', p.stat().st_size, 'bytes')
