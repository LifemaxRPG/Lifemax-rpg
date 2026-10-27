"""python3 tools/chk_reach.py [id ...]  : shows where a reach target cannot be met (limb would stretch)"""
import sys, math; sys.path.insert(0, 'tools')
import moves_v2 as M
from anim_lib import *
for i, r in M.REG.items():
    if len(sys.argv) > 1 and i not in sys.argv[1:]: continue
    bad = []
    for q in range(r['N'] * 3):
        p = r['fn'](q / (r['N'] * 3)); pts = world_pts(p)
        for l, k in (('ln', 'ankN'), ('lf', 'ankF'), ('an', 'wrN'), ('af', 'wrF')):
            s = p[l]
            if s[0] == 'i':
                e = math.dist((s[1], s[2]), pts[k])
                if e > .012: bad.append((q / (r['N'] * 3), l, round(e, 3)))
    if bad: print(i, bad[:4], '... worst', max(b[2] for b in bad))
