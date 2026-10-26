#!/usr/bin/env python3
"""v81 bedtime: never asleep behind the pouf, cushion or box; the favourite toy comes to bed.
   python3 tests/pet_sleep_acceptance.py [dist/Life_Max_RPG_v81.html]   (Playwright + Chromium, ~40 s)"""
import sys
from pathlib import Path
from playwright.sync_api import sync_playwright
ROOT = Path(__file__).resolve().parents[1]
HTML = (ROOT / (sys.argv[1] if len(sys.argv) > 1 else "dist/Life_Max_RPG_v81.html")).as_uri()
ok = 0; fails = []
def check(name, cond, info=''):
    global ok
    print(('PASS ' if cond else 'FAIL ') + name + ('  ' + str(info) if info != '' else ''))
    if cond: ok += 1
    else: fails.append(name)
with sync_playwright() as p:
    b = p.chromium.launch(); pg = b.new_page(viewport={'width': 393, 'height': 844})
    errs = []; pg.on('pageerror', lambda e: errs.append(str(e))); pg.on('console', lambda m: errs.append(m.text) if '[pet' in m.text else None)
    pg.add_init_script("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,devMode:true}));")
    pg.goto(HTML); pg.wait_for_timeout(600)
    pg.evaluate("() => { const pt=petAdopt('ginger','Mo','playful'); pt.born=Date.now()-40*864e5; pt.lastFed=Date.now(); pt.bondPts=300; save(); navigate('pet'); }")
    pg.wait_for_timeout(2000)
    for bond, kit in ((300, 40), (40, 40), (300, 3)):
        r = pg.evaluate(f"""(()=>{{state.pet.bondPts={bond};state.pet.born=Date.now()-{kit}*864e5;const rt=PET_RT,c={{surf:'floor',x:0,z:0,dir:1}};let hid=0,bad=[];
          for(let x=-150;x<=340;x+=5)for(let z=petZMinAt(x);z<=petDepthMaxZ(state.pet);z+=3){{c.x=x;c.z=z;if(!petSleepHidden(rt,c))continue;hid++;const s=petSleepSpot(rt,c);
            if(!s||petSleepHidden(rt,c,s[0],s[1])||Math.abs(s[0]-x)>170)bad.push([x,Math.round(z),s])}}return [hid,bad.length,bad.slice(0,3)]}})()""")
        check(f'every hidden floor spot has a visible one nearby (bond {bond}, age {kit} d)', r[0] > 20 and r[1] == 0, r)
    pg.evaluate("state.pet.bondPts=300;state.pet.born=Date.now()-40*864e5")
    pg.evaluate("petDevSleepHidden()"); pg.wait_for_timeout(6000)
    st = pg.evaluate("(()=>{const c=PET_RT.c;return [c.step&&c.step.a, petSleepCover(PET_RT,c,c.x,c.z).f]})()")
    check('lying down behind the cushion: he moves into view, then sleeps', st[0] == 'sleep' and st[1] <= 0.08, st)
    pg.evaluate("()=>{const c=PET_RT.c;c.surf='floor';c.x=-120;c.z=44;state.pet.lastFed=Date.now();PET_RT.rt=0;PET_RT.c.plan=[];const P=petPlaceForNow;}")
    st = pg.evaluate("(()=>{const rt=PET_RT,c=rt.c;c.surf='floor';c.x=-120;c.z=44;c.y=petFloorY(44);c.plan=[petAct('sleep',60)];const to=petSleepSpot(rt,c);return to?!petSleepHidden(rt,c,to[0],to[1]):false})()")
    check('placement asleep behind the cushion is moved into view', st)
    pg.evaluate("petDevSleepToy()")
    seen = set()
    for i in range(40):
        pg.wait_for_timeout(400)
        st = pg.evaluate("(()=>{const rt=PET_RT,c=rt.c,s=c.step;return [c.surf,s&&(s.k==='act'?s.a:s.k),rt.br.carry||'',rt.sideToy?rt.sideToy.k:'',petBrain(petData()).items.length]})()")
        if st[2]: seen.add('carry')
        if st[3] and st[1] == 'sleep': seen.add('bed'); break
    check('bedtime: he picks up the toy, carries it, and sleeps on the bed with it beside him', seen == {'carry', 'bed'}, [seen, st])
    pg.evaluate("()=>{const rt=PET_RT;petInterrupt(rt,petRoute(rt.c,'floor',120).concat([petAct('idle_sit',20)]))}"); pg.wait_for_timeout(5000)
    st = pg.evaluate("(()=>{const rt=PET_RT;return [rt.br.carry||'', !!rt.sideToy, petBrain(petData()).items.filter(i=>!i[3]).map(i=>i[0])]})()")
    check('getting up: he takes it along and leaves it on the floor where he settles', st[0] == '' and not st[1] and 'mouse' in st[2], st)
    pg.evaluate("()=>{const c=PET_RT.c;c.surf='bed';c.x=36;c.y=PET_SURF.bed.y;PET_RT.sideToy=null;PET_RT.toyCarry=null;c.plan=[petAct('sleep',60)];petSleepToyPlace(PET_RT)}")
    st = pg.evaluate("[PET_RT.sideToy&&PET_RT.sideToy.k, petBrain(petData()).items.filter(i=>i[0]==='mouse').length]")
    check('found asleep on the bed: the toy is already beside him (and not also on the floor)', st == ['mouse', 0], st)
    pg.evaluate("petDevPair('adopt')"); pg.wait_for_timeout(2500)
    pg.evaluate("()=>{const r=PET_PAIR.rt2;petAs(1,()=>{petLikes(petData()).favToy='yarn';const c=r.c;c.surf='floor';c.x=-120;c.z=44;c.y=petFloorY(44);c.step=null;r.blk={a:0,b:24,k:'nap',spot:'lrug'};c.plan=[petAct('sleep',60)];petSleepToyPlan(r);petInterrupt(r,c.plan)})}")
    pg.wait_for_timeout(9000)
    st = pg.evaluate("(()=>{const r=PET_PAIR.rt2,c=r.c;return petAs(1,()=>[c.step&&c.step.a, petSleepCover(r,c,c.x,c.z).f<=0.08, petBrain(petData()).items.map(i=>i[0])])})()")
    check('second cat: sleeps in view with its own favourite toy', st[0] == 'sleep' and st[1] and 'yarn' in st[2], st)
    pg.wait_for_timeout(2000)
    check('no page errors', not errs, errs[:3])
    b.close()
print(f'\n{ok}/{ok + len(fails)} passed')
sys.exit(1 if fails else 0)
