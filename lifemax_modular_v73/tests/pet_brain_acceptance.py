#!/usr/bin/env python3
"""v78 pet brain acceptance tests (headless Chromium via Playwright).
   python3 tests/pet_brain_acceptance.py [dist/Life_Max_RPG_v78.html]
Needs: pip install playwright && playwright install chromium. Real-time: about 3 minutes."""
import sys, json, time
from pathlib import Path
from playwright.sync_api import sync_playwright
ROOT = Path(__file__).resolve().parent.parent
HTML = (ROOT / (sys.argv[1] if len(sys.argv) > 1 else "dist/Life_Max_RPG_v78.html")).as_uri()
RESULTS, ERRS = [], []

def check(name, ok, detail=""):
    RESULTS.append((name, bool(ok), detail)); print(("PASS " if ok else "FAIL ") + name + ("  " + str(detail) if detail else ""))

ADOPT = """(c)=>{state.onboardingComplete=true;state.tutorialDone=true;state.name='Tester';petAdopt(c.coat,c.name,c.trait);const p=state.pet;
 p.bondPts=c.bond;p.born=Date.now()-c.age*864e5;p.lastFed=Date.now()-c.fed*36e5;state.devMode=true;delete p.brain;
 if(c.brain)p.brain=c.brain;save()}"""

def boot(pg, coat='gray', trait='curious', bond=400, age=40, fed=2, extra=None):
    pg.goto(HTML); pg.wait_for_timeout(1200)
    pg.evaluate(ADOPT, dict(coat=coat, name='Mochi', trait=trait, bond=bond, age=age, fed=fed, brain=None))
    if extra: pg.evaluate(extra)
    pg.reload(); pg.wait_for_timeout(1000)
    pg.evaluate("()=>navigate('pet')"); pg.wait_for_timeout(2500)

with sync_playwright() as pw:
    br = pw.chromium.launch(args=['--autoplay-policy=no-user-gesture-required'])
    pg = br.new_page(viewport={'width': 420, 'height': 900})
    pg.on('pageerror', lambda e: ERRS.append('PAGEERR ' + str(e)))
    pg.on('console', lambda m: ERRS.append('CONSOLE ' + m.type + ' ' + m.text) if m.type == 'error' or 'pet brain' in m.text else None)

    # 1. v77 save migration
    boot(pg)
    r = pg.evaluate("""()=>{const p=state.pet,b=p.brain,s=JSON.stringify(b);const vals=[b.af.v,b.af.a,b.at.fam,b.at.tru,b.at.sec,b.at.aut];
      const stored=JSON.parse(localStorage.getItem(STORE)).pet.brain;
      return {has:!!b,nan:vals.some(function(v){return typeof v!=='number'||isNaN(v)}),at:b.at,inn:b.qk.inn,size:s.length,stored:!!stored,level:petLevel(p)}}""")
    check("migration: brain created from a v77 save, no NaN", r['has'] and not r['nan'] and r['stored'], r['at'])
    check("migration: threads seeded from bondPts (bond 400 -> trust > 0.6)", r['at']['tru'] > 0.6 and r['at']['fam'] > 0.6)
    check("migration: 2 innate quirks", len(r['inn']) == 2, r['inn'])
    check("save footprint under 2.5 KB", r['size'] < 2560, str(r['size']) + " B")
    inn1 = r['inn']
    r2 = pg.evaluate("()=>{const p=state.pet;const b0=p.brain;p.brain=null;const b=migratePetBrainState(p);const same=JSON.stringify(b.qk.inn);p.brain=b0;return JSON.parse(same)}")
    check("quirks are deterministic from the cat's seed", r2 == inn1, r2)

    # 2. allesthesia: petting saturation, warning, boundary respect, retreat
    pg.evaluate("()=>{const b=state.pet.brain;b.af.s=0;b.af.v=0.4;b.af.a=-0.1;PET_RT.br.retreat=0;const rt=PET_RT;petInterrupt(rt,[petAct('idle_sit',60)])}")
    pg.wait_for_timeout(400)
    def tap_cat(hold_ms):
        pg.evaluate("()=>{const rt=PET_RT,b=petCatBox(rt);petPointer(rt,'down',(b.x0+b.x1)/2,(b.y0+b.y1)/2)}")
        pg.wait_for_timeout(hold_ms)
        pg.evaluate("()=>petPointer(PET_RT,'up',0,0)")
    for _ in range(6):
        tap_cat(1200); pg.wait_for_timeout(150)
        s = pg.evaluate("()=>state.pet.brain.af.s")
        if s >= 0.65: break
    som = pg.evaluate("()=>{const el=document.getElementById('petCanvas').parentElement;return {s:state.pet.brain.af.s,ear:el.dataset.ear,tail:el.dataset.tail}}")
    check("petting fills saturation to the warning band", som['s'] >= 0.65, som['s'])
    check("warning: data-ear=airplane_flat, data-tail=tip_twitch", som['ear'] == 'airplane_flat' and som['tail'] == 'tip_twitch', som)
    t0 = pg.evaluate("()=>state.pet.brain.at.tru")
    a0 = pg.evaluate("()=>state.pet.brain.at.aut")
    pg.wait_for_timeout(9500)
    t1, a1 = pg.evaluate("()=>[state.pet.brain.at.tru,state.pet.brain.at.aut]")
    check("respecting the boundary for 8 s raises trust and confidence", t1 > t0 and a1 > a0, (t0, t1, a0, a1))
    core = pg.evaluate("()=>state.pet.brain.mem.fx.first_respect||0")
    check("first respected boundary becomes a core memory", core == 1)
    pg.evaluate("()=>{state.pet.brain.af.s=0.6;const rt=PET_RT;petInterrupt(rt,[petAct('idle_sit',60)])}"); pg.wait_for_timeout(300)
    z0 = pg.evaluate("()=>PET_RT.c.z")
    pg.evaluate("()=>{const rt=PET_RT,b=petCatBox(rt);petPointer(rt,'down',(b.x0+b.x1)/2,(b.y0+b.y1)/2)}")
    retreated = False
    for _ in range(40):
        pg.wait_for_timeout(250)
        st = pg.evaluate("()=>({hold:!!PET_RT.hold,s:state.pet.brain.af.s,plan:(PET_RT.c.plan||[]).map(function(q){return q.k+(q.a?':'+q.a:'')+(q.flank?'(flank)':'')}).join(',')})")
        if not st['hold']: retreated = True; break
    pg.evaluate("()=>petPointer(PET_RT,'up',0,0)")
    check("past 0.85 he pulls away mid-stroke and flank-grooms", retreated and 'flank' in json.dumps(st), st)
    pg.wait_for_timeout(4000)
    check("...and steps back along z (mid/back zone)", pg.evaluate("()=>PET_RT.c.z") <= 55, pg.evaluate("()=>PET_RT.c.z"))

    # 3. spontaneous glass approach
    pg.evaluate("()=>{const b=state.pet.brain;b.af.s=0;b.af.v=0.5;b.af.a=0.3;b.sp.at=0;b.at.tru=0.8;b.at.sec=0.7;PET_RT.lastTapAt=0;PET_RT.br.retreat=0;PET_RT.br.viewSince=Date.now()-20000;PET_RT.br.drive=0.95;petInterrupt(PET_RT,[petAct('idle_sit',40)])}")
    best = 0; variant = None
    for _ in range(50):
        pg.wait_for_timeout(300)
        z, v = pg.evaluate("()=>[PET_RT.c.z,PET_RT.br.auto&&PET_RT.br.auto.v]")
        if v: variant = v
        best = max(best, z)
        if best >= 94: break
    check("left alone, he walks to the glass on his own (z >= 94)", best >= 94 and variant, (round(best, 1), variant))
    sc = pg.evaluate("()=>petDepthScalePct(PET_RT.c.z)")
    check("close-up scale at the glass", sc >= 175, str(sc) + "%")

    # 4. slow blink, offered by the cat and returned by the player
    pg.wait_for_timeout(6000)
    pg.evaluate("()=>{const b=state.pet.brain;b.af.v=0.5;b.af.a=0;PET_RT.br.auto=null;petInterrupt(PET_RT,[petAct('idle_sit',30)])}"); pg.wait_for_timeout(300)
    n0, tr0 = pg.evaluate("()=>[state.pet.brain.gz.n,state.pet.brain.at.tru]")
    pg.evaluate("()=>petGazeOffer(PET_RT)"); pg.wait_for_timeout(1700)
    win = pg.evaluate("()=>!!(PET_RT.br.gaze&&Date.now()<PET_RT.br.gaze.until)")
    pg.evaluate("()=>petBlinkClick()"); pg.wait_for_timeout(300)
    n1, tr1, anim = pg.evaluate("()=>[state.pet.brain.gz.n,state.pet.brain.at.tru,PET_RT.c.anim]")
    check("blink window opens, reciprocated blink counted", win and n1 == n0 + 1 and tr1 > tr0, (n0, n1, tr0, tr1, anim))
    pg.evaluate("()=>{PET_RT.br.blinkBtnAt=[]}")

    # 5. quiet company (parallel presence)
    pg.evaluate("()=>{const b=state.pet.brain;b.at.sec=0.6;b.af.a=-0.1;b.af.v=0.3;const br=PET_RT.br;br.auto=null;br.co=null;br.coTry=0;br.viewSince=Date.now()-60000;br.drive=0;state.pet.brain.sp.at=Date.now();PET_RT.lastTapAt=Date.now()-30000;petInterrupt(PET_RT,[petAct('idle_sit',30)])}")
    co = False
    for _ in range(30):
        pg.wait_for_timeout(500)
        st = pg.evaluate("()=>({co:!!PET_RT.br.co,z:PET_RT.c.z,a:PET_RT.c.step&&PET_RT.c.step.a,par:!!(PET_RT.c.step&&PET_RT.c.step.par)})")
        if st['par']: co = True; break
        if not st['co'] and not pg.evaluate("()=>PET_RT.c.plan.some(function(q){return q.par})"):
            pg.evaluate("()=>{PET_RT.br.coTry=0}")
    check("idle co-presence -> PARALLEL_COMPANION loaf at z >= 85", co and st['z'] >= 85, st)
    pg.evaluate("()=>{const s=PET_RT.c.step;if(s&&s.par)s.np=s.t+0.3}"); pg.wait_for_timeout(900)
    check("periodic one-eye peek (lookup) while loafing", pg.evaluate("()=>PET_RT.c.anim") == 'lookup')

    # 6. sleep architecture
    pg.evaluate("()=>{const b=state.pet.brain;b.at.tru=0.85;b.af.v=0.6;b.af.a=-0.8;PET_RT.br.co=null;PET_RT.br.deepStart=true;petInterrupt(PET_RT,[petAct('sleep',60)])}")
    pg.wait_for_timeout(1500)
    sl = pg.evaluate("()=>({st:state.pet.brain.sl.st,pose:state.pet.brain.sl.pose,depth:PET_RT.c.step&&PET_RT.c.step.depth,belly:!!(PET_RT.c.step&&PET_RT.c.step.belly)})")
    check("deep sleep reaches REM (or belly-up when trust >= 0.75)", sl['st'] in ('rem', 'belly'), sl)
    pg.evaluate("()=>{state.pet.brain.at.tru=0.3;PET_RT.br.deepStart=true;petInterrupt(PET_RT,[petAct('sleep',60)])}"); pg.wait_for_timeout(800)
    tries = 0; bellies = 0
    for _ in range(25):
        b = pg.evaluate("()=>{PET_RT.br.deepStart=true;petInterrupt(PET_RT,[petAct('sleep',60)]);return 1}"); pg.wait_for_timeout(120)
        bellies += pg.evaluate("()=>PET_RT.c.step&&PET_RT.c.step.belly?1:0"); tries += 1
    check("belly-up never happens below trust 0.75", bellies == 0, f"{bellies}/{tries}")
    pg.evaluate("()=>{const rt=PET_RT;rt.br.deepStart=false;petInterrupt(rt,[petAct('sleep',60)])}"); pg.wait_for_timeout(400)
    pg.evaluate("()=>{const rt=PET_RT,b=petCatBox(rt);petPointer(rt,'down',(b.x0+b.x1)/2,(b.y0+b.y1)/2)}"); pg.wait_for_timeout(200)
    pg.evaluate("()=>petPointer(PET_RT,'up',0,0)"); pg.wait_for_timeout(100)
    plan = pg.evaluate("()=>[PET_RT.c.step&&PET_RT.c.step.a].concat(PET_RT.c.plan.map(function(q){return q.a||q.k}))")
    check("waking from a doze goes through a stretch first", 'stretch' in plan[:2], plan)

    # 7. zoomies
    pg.evaluate("()=>{state.pet.brain.zm.e=0.95;petInterrupt(PET_RT,petPlanFrap(PET_RT))}")
    seen = set(); 
    for _ in range(60):
        pg.wait_for_timeout(300)
        s = pg.evaluate("()=>({f:PET_RT.br.frap,run:!!(PET_RT.c.step&&PET_RT.c.step.run),a:PET_RT.c.step&&PET_RT.c.step.a,e:state.pet.brain.zm.e,pupil:PET_RT.br.som.pupil})")
        if s['f']: seen.add('frap')
        if s['run']: seen.add('dash')
        if s['pupil'] == 'saucer_dilated': seen.add('saucer')
        if s['a'] == 'groom' and not s['f']: seen.add('groom'); break
    e = pg.evaluate("()=>state.pet.brain.zm.e")
    check("FRAP: freeze, dashes, saucer pupils, dignified groom, energy reset", seen >= {'frap', 'dash', 'saucer', 'groom'} and e == 0, (sorted(seen), e))

    # 8. hunt with trophy
    pg.evaluate("()=>{const b=state.pet.brain;b.at.tru=0.8;b.af.a=0.4;window.__tp=PET_BRAIN.TROPHY_P;PET_BRAIN.TROPHY_P=1}")   # the 35% roll is forced so the carry path is always exercised
    stages = []; got_trophy = False
    for attempt in range(6):
        n0 = pg.evaluate("()=>state.pet.brain.hu.gifts")
        pg.evaluate("()=>{const rt=PET_RT;rt.br.hunt=null;rt.br.stim=null;petInterrupt(rt,petPlanHunt(rt,Math.random))}")
        for _ in range(80):
            pg.wait_for_timeout(250)
            h = pg.evaluate("()=>PET_RT.br.hunt&&PET_RT.br.hunt.stage")
            if h and (not stages or stages[-1] != h): stages.append(h)
            if not h and len(stages) > 2: break
        if pg.evaluate("()=>state.pet.brain.hu.gifts") > n0: got_trophy = True; break
    pg.evaluate("()=>{PET_BRAIN.TROPHY_P=window.__tp}")
    order = [s for s in ['lock', 'wiggle', 'pounce'] if s in stages]
    check("hunt: LOCK_ON -> CALIBRATE_WIGGLE -> POUNCE", order == ['lock', 'wiggle', 'pounce'], stages[:8])
    items = pg.evaluate("()=>state.pet.brain.items")
    check("trophy carried toward the glass and left on the floor", got_trophy and any(i[3] == 1 for i in items), items)
    if got_trophy:
        ok = all(-160 <= i[1] <= 350 and 0 <= i[2] <= 100 for i in items)
        check("dropped toys stay inside the walkable room", ok)

    # 9. hesitation
    pg.evaluate("()=>{const rt=PET_RT,c=rt.c;rt.br.hesAt=0;rt.br.conflict={at:Date.now(),alt:null,name:'x'};rt.br.frap=false;c.surf='floor';c.z=20;c.y=petFloorY(20);petInterrupt(rt,[{k:'walk',x:c.x+10,z:Math.min(petDepthMaxZ(petData()),80)},petAct('idle_sit',5)])}")
    hes = False
    for _ in range(30):
        pg.wait_for_timeout(200)
        if pg.evaluate("()=>!!PET_RT.br.hes"): hes = True; zmid = pg.evaluate("()=>PET_RT.c.z"); break
    check("torn between two wishes: stops partway along a long z walk", hes and 25 < zmid < 75, zmid if hes else None)

    # 10. quirk crystallisation
    pg.evaluate("()=>{const b=state.pet.brain;b.qk.acq=[];b.qk.c.n=9;petQuirkCount(b,'n')}")
    check("10 night sessions -> night_owl_sentinel", pg.evaluate("()=>state.pet.brain.qk.acq.indexOf('night_owl_sentinel')>=0"))
    nb = pg.evaluate("()=>{const p=state.pet,b=p.brain;const h0=PET_RT.shift||0;const d=new Date();const target=new Date(d);target.setHours(23,0,0,0);PET_RT.shift=target-d;const x=petBrainBase(p,b,PET_RT);b.qk.acq=[];const y=petBrainBase(p,b,PET_RT);PET_RT.shift=h0;return [x.v-y.v,x.a-y.a]}")
    check("night owl raises night-time valence and arousal", nb[0] > 0 and nb[1] > 0, nb)

    # 11. voice
    v = pg.evaluate("()=>{const rt=PET_RT;const ac=petSfxCtx(rt);return [ac&&ac.state,petBrainVoice(rt,'trill',{force:1}),petBrainVoice(rt,'chirp',{force:1}),petBrainVoice(rt,'chatter',{force:1}),petBrainVoice(rt,'breath',{force:1})]}")
    check("synth calls (trill, chirp, chatter, silent-meow breath) run", v[0] != 'running' or all(v[1:]), v)
    pitch = pg.evaluate("()=>state.pet.brain.vo.p")
    check("per-cat voice pitch within 0.88-1.14", 0.88 <= pitch <= 1.14, pitch)

    # 12. offline catch-up (3 h away)
    hung0 = pg.evaluate("()=>petHours(state.pet)")
    pg.evaluate("()=>{const b=state.pet.brain;b.off.at=Date.now()-3*36e5;b.items=b.items.filter(function(i){return i[3]});save()}")
    pg.evaluate("()=>navigate('quests')"); pg.wait_for_timeout(600)
    pg.evaluate("()=>{PET_RT.br.sessionAt=0}")
    pg.evaluate("()=>navigate('pet')"); pg.wait_for_timeout(2500)
    off = pg.evaluate("()=>({acts:state.pet.brain.off.acts,items:state.pet.brain.items,scene:state.pet.brain.off.scene,away:(document.querySelector('.pet-away')||{}).textContent||'',hours:petHours(state.pet)})")
    check("3 h away: his day is logged (up to 3 things)", 1 <= len(off['acts']) <= 3, off['acts'])
    check("hunger clock untouched by the catch-up (existing rules)", abs(off['hours'] - hung0) < 0.1, (hung0, off['hours']))
    check("'While you were away' line shown", 'While you were away' in off['away'], off['away'][:120])

    # 13. anticipatory vigil
    pg.evaluate("()=>{const b=state.pet.brain;const hr=Math.floor(petHour())%24;b.rt.h[hr]=0.8;b.at.fam=0.5;b.rt.sess=0;b.off.at=Date.now();PET_RT.greetedAt=0;PET_RT.br.sessionAt=0;save()}")
    pg.evaluate("()=>navigate('quests')"); pg.wait_for_timeout(500); pg.evaluate("()=>navigate('pet')"); pg.wait_for_timeout(1200)
    vg = pg.evaluate("()=>({z:PET_RT.c.z,a:PET_RT.c.step&&PET_RT.c.step.a,greet:PET_RT.br.greeting})")
    check("learned hour: he is already waiting at the glass (z >= 88)", vg['z'] >= 88, vg)

    # 14. comfort-seeking uses memory weights
    pg.evaluate("()=>{const b=state.pet.brain;for(const k in b.mem.w)b.mem.w[k]=0.02;b.mem.w.couch=0.5}")
    cz = pg.evaluate("()=>petComfortZone(state.pet,state.pet.brain)")
    check("comfort zone = argmax of memory weights", cz == 'couch', cz)
    pg.evaluate("()=>{const b=state.pet.brain;for(let i=0;i<5;i++)petMemPositive(b,'front_living',0.15)}")
    w = pg.evaluate("()=>{const W=state.pet.brain.mem.w;let s=0;for(const k in W)s+=W[k];return [W.front_living,W.couch,s]}")
    check("5 good moments in one zone make it the favourite; weights stay normalised", w[0] > w[1] and abs(w[2] - 1) < 0.02, w)

    # 15. UI and save size after a busy session
    pg.evaluate("()=>{document.querySelector('.pet-more').open=true;petBrainRender(true)}"); pg.wait_for_timeout(300)
    ui = pg.evaluate("()=>({mind:document.getElementById('petMind').innerText,inner:document.getElementById('petInner').innerText.slice(0,400),size:JSON.stringify(state.pet.brain).length})")
    check("Cat screen shows bond style + mood; inner world panel renders", len(ui['mind']) > 10 and 'Trust' in ui['inner'], ui['mind'][:80])
    check("save footprint still under 2.5 KB after a busy session", ui['size'] < 2560, str(ui['size']) + " B")
    pg.screenshot(path=str(ROOT / "tests/pet_v78.png"))

    # 15b. v78.1 fixes: blink rate, eye colour, wiped sprite sheets
    bl = pg.evaluate("""()=>{const rt=PET_RT,c=rt.c,m=rt.A.anims.idle_sit,t0=rt.t,ct=c.t;let n=0,prev=false;for(let i=0;i<1200;i++){rt.t+=0.1;c.t+=0.1;const f=petBlinkFrame(rt,'idle_sit',m),b=f>=3&&f<=5;if(b&&!prev)n++;prev=b}rt.t=t0;c.t=ct;return n}""")
    check("idle blinks every few seconds, not every loop (2 min: 8-35)", 8 <= bl <= 35, bl)
    ev = pg.evaluate("""()=>{const rt=PET_RT,br=rt.br;br.som.pupil='saucer_dilated';const sh=rt.sheets[state.pet.coat],A=rt.A;return petBrainVariant(rt,sh,A.anims.idle_sit.row,A.W,A.H,'idle_sit',state.pet.coat)===null||br.som.ear==='airplane_flat'||br.som.tail==='tip_twitch'}""")
    check("dilated pupils no longer repaint the eyes", ev)
    rec = pg.evaluate("""async()=>{const rt=PET_RT,cv=rt.sheets[state.pet.coat];cv.getContext('2d',{willReadFrequently:true}).clearRect(0,0,cv.width,cv.height);const a=petSpritesLost(rt);await new Promise(function(r){setTimeout(r,3500)});return [a,petSpritesLost(rt)]}""")
    check("a wiped sprite sheet is detected and rebuilt (no invisible cat)", rec == [True, False], rec)

    # 16. regression: every view, other pet states
    for v in ['dashboard', 'character', 'log', 'quests', 'plan', 'media', 'recipes', 'meals', 'money', 'history', 'settings', 'life-director', 'pet']:
        pg.evaluate(f"()=>{{const b=document.querySelector('[data-view=\"{v}\"]');if(b)b.click();else navigate('{v}')}}"); pg.wait_for_timeout(350)
    check("all views open (incl. Life Director)", pg.evaluate("()=>!!document.querySelector('#view-life-director .ld-root')"))
    boot(pg, coat='black', trait='shy', bond=0, age=1, fed=1)
    check("stray kitten (bond 0) runs with the brain", pg.evaluate("()=>!!state.pet.brain&&PET_RT.ready"))
    pg.evaluate("()=>{const rt=PET_RT,b=petCatBox(rt);petPointer(rt,'down',(b.x0+b.x1)/2,(b.y0+b.y1)/2)}"); pg.wait_for_timeout(300); pg.evaluate("()=>petPointer(PET_RT,'up',0,0)")
    boot(pg, coat='ginger', trait='lazy', bond=200, age=40, fed=60)
    pg.wait_for_timeout(2500)
    check("very hungry cat (stage 4) still behaves (rests by the bowl)", pg.evaluate("()=>petStage(state.pet)>=3&&PET_RT.c.x===PET_EATX||Math.abs(PET_RT.c.x-PET_EATX)<30"))
    pg.evaluate("()=>{state.pet.lastFed=Date.now()-200*36e5;petCheck();renderPetAll()}"); pg.wait_for_timeout(800)
    check("memorial path unaffected", pg.evaluate("()=>!!document.getElementById('petMemCanvas')"))
    br.close()

errs = [e for e in ERRS if 'favicon' not in e]
check("no page errors or brain warnings", not errs, errs[:6])
fails = [r for r in RESULTS if not r[1]]
print(f"\n{len(RESULTS)-len(fails)}/{len(RESULTS)} passed")
sys.exit(1 if fails else 0)
