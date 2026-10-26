#!/usr/bin/env python3
"""v87 cat DNA: migration of old saves, pixel-exact legacy coats, genetics ratios, litter adoption, female pronouns, persistence.
   python3 tests/pet_dna_acceptance.py [dist/Life_Max_RPG_v87.html]   (Playwright + Chromium, ~40 s)"""
import sys, re
from pathlib import Path
from playwright.sync_api import sync_playwright
ROOT = Path(__file__).resolve().parents[1]
HTML = (ROOT / (sys.argv[1] if len(sys.argv) > 1 else "dist/Life_Max_RPG_v87.html")).as_uri()
ok = 0; fails = []
def check(name, cond, info=''):
    global ok
    print(('PASS ' if cond else 'FAIL ') + name + ('  ' + str(info) if info != '' else ''))
    if cond: ok += 1
    else: fails.append(name)
SEED = "localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,devMode:true}));"
OLD = "()=>{const now=Date.now();state.pet={v:1,name:'Mo',coat:'%s',trait:'%s',born:now-40*864e5,lastFed:now,meals:[],day:petDay(),fedToday:0,petsToday:0,playsToday:0,bondPts:300,care:0,vacation:{on:false,since:0},gifts:0,lastGiftDay:'',alive:true,passed:0,seen:{},greeted:0};localStorage.setItem(STORE,JSON.stringify(state))}"
with sync_playwright() as p:
    b = p.chromium.launch(); pg = b.new_context(viewport={'width': 393, 'height': 844}).new_page()
    errs = []; pg.on('pageerror', lambda e: errs.append(str(e)))
    pg.on('console', lambda m: errs.append(m.text) if '[pet dna]' in m.text else None)
    pg.add_init_script("if(!localStorage.getItem('lifemax_rpg_prototype_v1'))" + SEED)
    pg.goto(HTML); pg.wait_for_timeout(700)
    for coat, trait in (('ginger', 'curious'), ('gray', 'lazy'), ('black', 'playful'), ('tuxedo', 'shy')):
        pg.evaluate(OLD % (coat, trait)); pg.reload(); pg.wait_for_timeout(600)
        r = pg.evaluate("(()=>{const p=state.pet,ph=petPheno(p);return [p.coat,p.trait,p.sex,ph.key,ph.trait]})()")
        check(f'old {coat}/{trait} cat gets a matching genome, male, nothing else changes', r == [coat, trait, 'm', coat, trait], r)
    pg.evaluate("navigate('pet')"); pg.wait_for_timeout(2000)
    ex = pg.evaluate("""(async()=>{const rt=await petInit();let bad=0;for(const k of ['ginger','gray','black','tuxedo'])for(const kind of ['adult','depth','st0','st5','dst3']){
      const a=await petDnaSheet(k,kind);let ref;if(kind==='adult')ref=rt.sheets[k];else if(kind==='depth')ref=rt.dsheets[k];else{const m=/(\\d)/.exec(kind)[1];const U=kind[0]==='d'?rt.A.depth.stages[m].coats[k]:petGrowthJson().stages[m].coats[k];ref=await petTintSheet(U,rt.A.pal[k])}
      const da=a.getContext('2d').getImageData(0,0,a.width,a.height).data,db=ref.getContext('2d').getImageData(0,0,ref.width,ref.height).data;for(let i=0;i<da.length;i+=4){if(da[i+3]===0&&db[i+3]===0)continue;if(da[i]!==db[i]||da[i+1]!==db[i+1]||da[i+2]!==db[i+2]||da[i+3]!==db[i+3])bad++}}return bad})()""")
    check('legacy coats composed from genes are pixel-identical to the original sheets', ex == 0, ex)
    g = pg.evaluate("""(()=>{const r=petRng(7);let tm=0,tf=0,rm=0,rf=0,lh=0,n=3000;for(let i=0;i<n;i++){const k=petDnaConceive(petDnaRandom(r,{sex:'XX'}),petDnaRandom(r,{sex:'XY'}),r),ph=petDnaPheno(k);
      if(k.sex==='XY'){if(ph.tortie)tm++;if(ph.red)rm++}else if(k.sex==='XX'){if(ph.tortie)tf++;if(ph.red)rf++}}return [tm,tf,rm,rf]})()""")
    check('X-linked orange: no XY tortie, torties common in females, ginger mostly male', g[0] == 0 and g[1] > 300 and g[2] > 2 * g[3], g)
    m = pg.evaluate("""(()=>{const r=petRng(9),mom=petDnaRandom(r,{sex:'XX'}),dad=petDnaRandom(r,{sex:'XY'});mom.g.D=['D','d'];dad.g.D=['D','d'];mom.g.L=['L','l'];dad.g.L=['L','l'];
      let dd=0,ll=0,n=4000;for(let i=0;i<n;i++){const k=petDnaConceive(mom,dad,r);if(k.g.D.join()==='d,d')dd++;if(k.g.L.join()==='l,l')ll++}return [dd/n,ll/n]})()""")
    check('Mendel: carrier x carrier gives about 1/4 recessive', all(0.21 < x < 0.29 for x in m), m)
    # adoption from a litter: a female with a composed coat
    pg.evaluate("petAdoptNew();window.__petLitter=null;renderPetView(true)"); pg.wait_for_timeout(1500)
    check('the intro shows a litter of four', pg.evaluate("document.querySelectorAll('#petCoats .pet-kit').length") == 4)
    pick = pg.evaluate("""(()=>{for(let t=0;t<80;t++){const L=window.__petLitter;const i=L.kits.findIndex(d=>{const ph=petDnaPheno(d);return ph.sex==='f'&&ph.key.indexOf('dna_')===0});if(i>=0){petPickKit(i);return i}petNewLitter()}return -1})()""")
    pg.fill('#petNameIn', 'Luna'); pg.click('text=Welcome home'); pg.wait_for_timeout(3000)
    st = pg.evaluate("[state.pet.sex,state.pet.coat.indexOf('dna_')===0,petPheno(state.pet).key===state.pet.coat,!!PET_RT.sheets[state.pet.coat],!!PET_RT.dsheets[state.pet.coat],document.querySelector('.pet-head .pet-sex').textContent]")
    check('adopted kitten: female, own coat key, sheets built, ♀ by the name', pick >= 0 and st == ['f', True, True, True, True, '♀'], st)
    pg.evaluate("document.querySelector('.pet-more').open=true;petBrainRender(true)"); pg.wait_for_timeout(500)
    txt = pg.evaluate("document.getElementById('view-pet').innerText")
    check('female cat: no he/him/his left on the Cat screen', not re.findall(r'\b(he|him|his|He|His)\b', txt), re.findall(r'\b(he|him|his|He|His)\b', txt)[:5])
    check('genetics panel is shown', 'Genetics'.upper() in txt.upper() and '38,XX' in txt)
    pg.reload(); pg.wait_for_timeout(1200); pg.evaluate("navigate('pet')"); pg.wait_for_timeout(2000)
    st2 = pg.evaluate("[state.pet.sex,petPheno(state.pet).key===state.pet.coat,!!PET_RT.sheets[state.pet.coat]]")
    check('genome and coat survive a reload', st2 == ['f', True, True], st2)
    pg.evaluate("petDevGrow(0)"); pg.wait_for_timeout(1500)
    k = pg.evaluate("(()=>{const g=Object.keys(PET_RT.gs||{}).filter(k=>k.endsWith(state.pet.coat));return g.length})()")
    check('kitten stage sheet composed for the custom coat', k >= 1, k)
    check('no errors', not errs, errs[:5])
    b.close()
print(f'\n{ok} passed, {len(fails)} failed'); sys.exit(1 if fails else 0)
