#!/usr/bin/env python3
"""v80 two cats: adoption, both cats running, the interactions, feeding, touch, spots, hunger, saving.
   python3 tests/pet_pair_acceptance.py [dist/Life_Max_RPG_v80.html]   (Playwright + Chromium, ~2 minutes)"""
import sys, json
from pathlib import Path
from playwright.sync_api import sync_playwright
ROOT = Path(__file__).resolve().parents[1]
HTML = (ROOT / (sys.argv[1] if len(sys.argv) > 1 else "dist/Life_Max_RPG_v80.html")).as_uri()
ok = 0; fails = []
def check(name, cond, info=''):
    global ok
    print(('PASS ' if cond else 'FAIL ') + name + ('  ' + str(info) if info != '' else ''))
    if cond: ok += 1
    else: fails.append(name)
SEED = "localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,devMode:true}));"
with sync_playwright() as p:
    b = p.chromium.launch(); ctx = b.new_context(viewport={'width': 393, 'height': 844}); pg = ctx.new_page()
    errs = []; pg.on('pageerror', lambda e: errs.append(str(e)))
    pg.on('console', lambda m: errs.append(m.text) if ('[pet pair]' in m.text or 'pet brain' in m.text) else None)
    pg.add_init_script("if(!localStorage.getItem('lifemax_rpg_prototype_v1'))" + SEED)
    pg.goto(HTML); pg.wait_for_timeout(600)
    pg.evaluate("() => { const pt=petAdopt('ginger','Mo','playful'); pt.born=Date.now()-40*864e5; pt.lastFed=Date.now(); pt.bondPts=300; save(); navigate('pet'); }")
    pg.wait_for_timeout(2000)
    one = pg.evaluate("[!!state.pet2, !!document.getElementById('petSwitch'), !!document.getElementById('petPairInvite'), petPairOn()]")
    check('one cat: no second cat, no tabs, an invitation under Care and settings', one == [False, False, True, False], one)
    pg.evaluate("petPairAdoptOpen()"); pg.wait_for_timeout(400)
    pg.fill('#petNameIn', 'Pip'); pg.evaluate("petPickCoat('tuxedo'); petPickTrait('shy')"); pg.click('text=Let them in'); pg.wait_for_timeout(2500)
    st = pg.evaluate("[petPairOn(), state.pet2.name, state.pet2.coat, state.pet2.trait, state.pet.name, PET_PAIR.focus, document.getElementById('petTitle').textContent, document.querySelectorAll('#petSwitch button').length]")
    check('adopting: Pip moves in, Mo unchanged, Pip selected, two tabs', st == [True, 'Pip', 'tuxedo', 'shy', 'Mo', 1, 'Pip', 2], st)
    moved = pg.evaluate("(async()=>{const c=PET_PAIR.rt2.c,a=[c.t,c.x];await new Promise(r=>setTimeout(r,1500));return [c.t-a[0],Math.round(Math.abs(c.x-a[1]))]})()")
    check('the second cat runs on its own clock (animates, walks in)', moved[0] > 1.0, moved)
    drawn = pg.evaluate("(()=>{let n=0;const o=PET_RT.octx;const _d=o.drawImage;o.drawImage=function(img){if(img===PET_RT.sheets.tuxedo||(PET_RT.gs&&Object.values(PET_RT.gs).some(g=>g.cv===img))||(PET_RT.dsheets&&img===PET_RT.dsheets.tuxedo))n++;return _d.apply(this,arguments)};petDraw(PET_RT);o.drawImage=_d;return n})()")
    check('the second cat is drawn in the room', drawn >= 1, drawn)
    # first meeting happens by itself
    met = 0
    for i in range(40):
        pg.wait_for_timeout(1000)
        met = pg.evaluate("state.petPair.met")
        if met: break
    n = pg.evaluate("state.petPair.n")
    check('first meeting happens on its own (nose touch or hiss)', bool(met) and (n['greet'] + n['hiss']) == 1, n)
    # each interaction, forced
    for kind, key in (('chase', 'chase'), ('groom', 'groom'), ('nap', 'nap'), ('greet', None)):
        before = pg.evaluate("JSON.stringify(state.petPair.n)")
        pg.evaluate("()=>{const a=PET_PAIR.rt1.c,b=PET_PAIR.rt2.c;[[a,40],[b,140]].forEach(([c,x])=>{c.surf='floor';c.x=x;c.z=40;c.y=petFloorY(40);c.step=null;c.plan=[petAct('idle_sit',99)]});state.pet.lastFed=state.pet2.lastFed=Date.now()}")
        pg.evaluate(f"petDevPair('{kind}')")
        done = False
        for i in range(40):
            pg.wait_for_timeout(500)
            if pg.evaluate("!PET_PAIR.I"): done = True; break
        after = json.loads(pg.evaluate("JSON.stringify(state.petPair.n)")); bef = json.loads(before)
        grew = (after[key] > bef[key]) if key else (after['greet'] + after['hiss'] > bef['greet'] + bef['hiss'])
        check(f'interaction {kind}: runs to the end and is counted', done and grew, [after, pg.evaluate('JSON.stringify(PET_PAIR.last)')])
        if kind == 'nap':
            for i in range(20):
                pg.wait_for_timeout(300)
                sl = pg.evaluate("[PET_PAIR.rt1.c.step&&PET_PAIR.rt1.c.step.a, PET_PAIR.rt2.c.step&&PET_PAIR.rt2.c.step.a, Math.abs(PET_PAIR.rt1.c.x-PET_PAIR.rt2.c.x)]")
                if sl[0] == 'sleep' and sl[1] == 'sleep': break
            check('nap together: both asleep, side by side', sl[0] == 'sleep' and sl[1] == 'sleep' and 10 < sl[2] < 40, sl)
    # touching a cat ends an interaction
    pg.evaluate("()=>{state.pet2.bondPts=120;const a=PET_PAIR.rt1.c,b=PET_PAIR.rt2.c;[[a,40],[b,140]].forEach(([c,x])=>{c.surf='floor';c.x=x;c.z=40;c.y=petFloorY(40);c.step=null;c.plan=[petAct('idle_sit',99)]});petDevPair('chase')}")
    pg.wait_for_timeout(600)
    pg.evaluate("(()=>{const rt=PET_PAIR.rt2,bx=petCatBox(rt);petPointer(PET_PAIR.rt1,'down',rt.c.x,(bx.y0+bx.y1)/2)})()"); pg.wait_for_timeout(1300)
    t = pg.evaluate("[!PET_PAIR.I, !!PET_PAIR.rt2.hold, !!PET_PAIR.rt1.hold]")
    pg.evaluate("petPointer(PET_PAIR.rt1,'up',0,0)"); pg.wait_for_timeout(300)
    check('holding the second cat pets it (not the first) and ends the interaction', t == [True, True, False], t)
    pg.evaluate("petPairFocus(0)"); pg.wait_for_timeout(300)
    pg.evaluate("(()=>{const rt=PET_PAIR.rt2,bx=petCatBox(rt);petPointer(PET_PAIR.rt1,'down',rt.c.x,(bx.y0+bx.y1)/2);petPointer(PET_PAIR.rt1,'up',0,0)})()"); pg.wait_for_timeout(400)
    check('tapping a cat in the room selects it', pg.evaluate("PET_PAIR.focus") == 1)
    # feeding both
    pg.evaluate("()=>{state.pet.lastFed=state.pet2.lastFed=Date.now()-6*36e5;state.pet.fedToday=state.pet2.fedToday=0;PET_PAIR.rt1.c.x=200;PET_PAIR.rt2.c.x=-60;[PET_PAIR.rt1.c,PET_PAIR.rt2.c].forEach(c=>{c.surf='floor';c.z=35;c.y=petFloorY(35)});petRenderStatus(true)}")
    lab = pg.evaluate("document.getElementById('petFeedBtn').textContent")
    pg.evaluate("petFeedClick()")
    eat = [None, None]
    for i in range(30):
        pg.wait_for_timeout(400)
        e = pg.evaluate("[0,1].map(i=>{const c=petPairRt(i).c;return c.step&&c.step.a==='eat'?[Math.round(c.x),c.dir]:null})")
        for j in (0, 1):
            if e[j]: eat[j] = e[j]
        if eat[0] and eat[1]: break
    check('Feed says "Feed both" and both eat, from either side of the bowl', lab == 'Feed both' and eat[0] == [87, 1] and eat[1] == [121, -1], [lab, eat])
    # vacation covers both
    pg.evaluate("petSetVacation(true)"); v = pg.evaluate("[state.pet.vacation.on, state.pet2.vacation.on]"); pg.evaluate("petSetVacation(false)")
    check('vacation mode pauses both cats', v == [True, True], v)
    # a spot the other cat is on
    pg.evaluate("()=>{const a=PET_PAIR.rt1,c=a.c;c.surf='bed';c.x=36;c.y=PET_SURF.bed.y;c.step=null;c.plan=[petAct('sleep',99)];const r=PET_PAIR.rt2;r.c.surf='floor';r.c.x=160;r.c.z=40;r.c.y=petFloorY(40);petAs(1,()=>{const q=petRoute(r.c,'bed',36);q.push(petAct('loaf',30));petInterrupt(r,q)})}")
    pg.wait_for_timeout(9000)
    sp = pg.evaluate("[PET_PAIR.rt1.c.surf, PET_PAIR.rt2.c.surf, Math.round(PET_PAIR.rt2.c.x)]")
    check('the bed is taken: the second cat settles beside it instead', sp[0] == 'bed' and sp[1] == 'floor', sp)
    # saving and reloading
    print('  before reload:', pg.evaluate("[!!state.pet,!!state.pet2]"))
    pg.evaluate("save()"); pg.reload(); pg.wait_for_timeout(800); pg.evaluate("navigate('pet')"); pg.wait_for_timeout(2500)
    rl = pg.evaluate("[!!state.pet2&&state.pet2.name, state.pet.name, petPairOn(), state.petPair&&state.petPair.met>0, document.querySelectorAll('#petSwitch button').length]")
    check('after a reload both cats and their friendship are back', rl == ['Pip', 'Mo', True, True, 2], rl)
    # a long stretch on their own
    pg.wait_for_timeout(45000)
    check('45 s of both cats on their own: no errors', not errs, errs[:3])
    # hunger: the second cat passing away
    pg.evaluate("()=>{state.pet2.lastFed=Date.now()-170*36e5;petCheck()}"); pg.wait_for_timeout(800)
    d = pg.evaluate("[state.pet2, state.pet.alive, (state.petMemorial||[]).slice(-1)[0].name, petPairOn()]")
    check('the second cat starving: remembered, removed, the first cat carries on', d == [None, True, 'Pip', False], d)
    # the first cat passing away while the second lives
    pg.evaluate("petDevPair('adopt')"); pg.wait_for_timeout(2500)
    pg.evaluate("()=>{state.pet.lastFed=Date.now()-170*36e5;state.pet2.lastFed=Date.now();petCheck()}"); pg.wait_for_timeout(1500)
    d = pg.evaluate("[state.pet.alive, state.pet.name!=='Mo', !state.pet2, (state.petMemorial||[]).slice(-1)[0].name, !!document.getElementById('petCanvas')]")
    check('the first cat starving: the second becomes the main cat, the room stays', d == [True, True, True, 'Mo', True], d)
    pg.wait_for_timeout(3000)
    check('no page errors', not errs, errs[:3])
    b.close()
    # an old one-cat save loads untouched
    b = p.chromium.launch(); pg = b.new_page(); errs2 = []; pg.on('pageerror', lambda e: errs2.append(str(e)))
    pg.add_init_script(SEED); pg.goto(HTML); pg.wait_for_timeout(500)
    pg.evaluate("() => { const pt=petAdopt('gray','Solo','lazy'); pt.lastFed=Date.now(); save(); navigate('pet'); }"); pg.wait_for_timeout(4000)
    s = pg.evaluate("[state.pet2===undefined, state.petPair===undefined, petPairOn(), !!PET_RT.c]")
    check('a one-cat household stays exactly one cat (nothing new saved)', s == [True, True, False, True] and not errs2, [s, errs2[:2]])
    b.close()
print(f'\n{ok}/{ok + len(fails)} passed')
sys.exit(1 if fails else 0)
