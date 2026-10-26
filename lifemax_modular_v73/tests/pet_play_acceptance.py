#!/usr/bin/env python3
"""v79.1 play games: floor swats, swat-and-chase, belly-up juggle.
   python3 tests/pet_play_acceptance.py [dist/Life_Max_RPG_v79_1.html]   (Playwright + Chromium, ~1 minute)"""
import sys, json, base64, io
from pathlib import Path
from playwright.sync_api import sync_playwright
ROOT = Path(__file__).resolve().parents[1]
HTML = (ROOT / (sys.argv[1] if len(sys.argv) > 1 else "dist/Life_Max_RPG_v79_1.html")).as_uri()
ok = 0; fails = []
def check(name, cond, info=''):
    global ok
    print(('PASS ' if cond else 'FAIL ') + name + ('  ' + str(info) if info != '' else ''))
    if cond: ok += 1
    else: fails.append(name)
with sync_playwright() as p:
    b = p.chromium.launch(); pg = b.new_page(viewport={'width': 393, 'height': 844})
    errs = []; pg.on('pageerror', lambda e: errs.append(str(e)))
    pg.add_init_script("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));")
    pg.goto(HTML); pg.wait_for_timeout(600)
    meta = pg.evaluate("(() => { const A=petAssetsJson(); const G=JSON.parse(document.getElementById('petGrowth').textContent); return [A.anims.swat, G.stages.map(s=>s.bottom.swat)] })()")
    check('swat row registered for adults and every kitten stage', meta[0] and meta[0]['row'] == 27 and all(x is not None for x in meta[1]), meta)
    for age, label in ((40, 'adult'), (3, 'kitten')):
        pg.evaluate(f"() => {{ const pt=petAdopt('gray','Mo','playful'); pt.born=Date.now()-{age}*864e5; pt.lastFed=Date.now(); pt.bondPts=300; save(); navigate('pet'); }}")
        pg.wait_for_timeout(2500)
        for n, name, want in ((1, 'floor swat', 'swat'), (2, 'swat and chase', 'play'), (3, 'belly-up juggle', 'roll')):
            pg.evaluate(f"petDevPlay2({n})"); seen = set(); front = False; held = []
            for i in range(30):
                pg.wait_for_timeout(250)
                st = pg.evaluate("[PET_RT.c.anim, !!PET_RT.ballFront, PET_RT.ball.on, PET_RT.ball.h||0, PET_RT.c.step&&PET_RT.c.step.hug]")
                seen.add(st[0]); front = front or st[1]
                if st[4]: held.append(st[3])
            check(f'{label}: {name} plays its pose', want in seen, sorted(seen))
            if n == 3: check(f'{label}: juggled toy is held up off the floor', held and min(held) > 4, [round(h, 1) for h in held[:4]])
            if n == 1: check(f'{label}: toy drawn in front of his paws while swatting', front)
    check('no page errors', not errs, errs[:3])
    b.close()
print(f'\n{ok}/{ok + len(fails)} passed')
sys.exit(1 if fails else 0)
