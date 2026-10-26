#!/usr/bin/env python3
"""v79.2 the box: smooth jump in, sinking below the rim, peek-a-boo, jump out.
   python3 tests/pet_box_acceptance.py [dist/Life_Max_RPG_v79_2.html]   (Playwright + Chromium, ~1 minute)"""
import sys
from pathlib import Path
from playwright.sync_api import sync_playwright
ROOT = Path(__file__).resolve().parents[1]
HTML = (ROOT / (sys.argv[1] if len(sys.argv) > 1 else "dist/Life_Max_RPG_v79_2.html")).as_uri()
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
    for age, label in ((40, 'adult'), (3, 'kitten')):
        pg.evaluate(f"() => {{ const pt=petAdopt('ginger','Mo','playful'); pt.born=Date.now()-{age}*864e5; pt.lastFed=Date.now(); pt.bondPts=300; save(); navigate('pet'); }}")
        pg.wait_for_timeout(2500)
        pg.evaluate("() => { const c=PET_RT.c; c.surf='floor'; c.x=220; c.z=PET_DEPTH.ART_Z; c.y=petSurfY('floor',c.z); petInterrupt(PET_RT, petBoxPlayPlan(PET_RT,{rounds:2,stay:1})) }")
        jump = []; sinks = []; inbox = 0; peeked = False
        for i in range(70):
            pg.wait_for_timeout(120)
            st = pg.evaluate("(()=>{const c=PET_RT.c,s=c.step;return [s&&s.k==='hop'&&s.box, c.lock, c.anim, c.surf, c.boxSink||0, Math.round(c.y), s&&s.a]})()")
            if st[0] == 'in': jump.append((st[2], st[1], st[5]))
            if st[3] == 'box': inbox += 1; sinks.append(st[4])
        poses = [(a, l) for a, l, y in jump]
        check(f'{label}: hop into the box uses the new jump (push-off, tuck)', ('leap', 1) in poses or ('leap', 0) in poses, poses[:6])
        check(f'{label}: he ends up in the box', inbox > 10, inbox)
        ears = pg.evaluate("petBoxFace('sit')[0]")   # his height to the ear tips: kittens duck less far
        check(f'{label}: he sinks below the rim and comes back up (peek-a-boo)', sinks and max(sinks) > 0.4 * ears and min(sinks) < max(sinks) - 5, [round(min(sinks), 1), round(max(sinks), 1)] if sinks else [])
        hit = pg.evaluate("(()=>{const rt=PET_RT,bx=petCatBox(rt),g=petBoxGeom(rt);return [petHit(rt,rt.c.x,bx.y0+5), rt.c.boxSink>2?petHit(rt,rt.c.x,g.rimY+8):'room']})()")
        check(f'{label}: tapping his head in the box touches him, tapping the box does not', hit == ['cat', 'room'], hit)
        pg.evaluate("() => { const c=PET_RT.c; c.plan=[]; petInterrupt(PET_RT, petRoute(c,'floor',330).concat([petAct('idle_sit',4)])) }")
        outs = []
        for i in range(20):
            pg.wait_for_timeout(100)
            outs.append(pg.evaluate("(()=>{const c=PET_RT.c,s=c.step;return [s&&s.k==='hop'&&s.box, c.surf, c.z, c.boxSink||0]})()"))
        check(f'{label}: hops out with the new jump and lands in front of the box', any(o[0] == 'out' for o in outs) and outs[-1][1] == 'floor' and outs[-1][3] == 0, outs[-1])
    check('no page errors', not errs, errs[:3])
    b.close()
print(f'\n{ok}/{ok + len(fails)} passed')
sys.exit(1 if fails else 0)
