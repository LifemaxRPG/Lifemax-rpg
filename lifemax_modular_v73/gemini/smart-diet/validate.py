#!/usr/bin/env python3
"""Validate Smart Diet data from Gemini.
Usage: python3 validate.py athlete-catalog.json athlete-standard.json [athlete-simple.json ...]
Prints every problem and exits 1 if there is any."""
import json, sys
SLOTS={'breakfast','lunch','dinner','snack','pre_workout','post_workout'}
ALLERGENS={'egg','dairy','gluten','soy','tree_nut','peanut','fish','shellfish','sesame'}
AISLES={'produce','meat_seafood','dairy_eggs','bakery_grains','canned_jarred','frozen','pantry','snacks','condiments_spices'}
DAYS=['mon','tue','wed','thu','fri','sat','sun']
LIMITS={'standard':dict(ings=14,active=40,steps=99,week_ings=60),'simple':dict(ings=8,active=15,steps=6,week_ings=35)}
errs=[]
def bad(msg): errs.append(msg)
def num(x): return isinstance(x,(int,float)) and not isinstance(x,bool)
def load(p):
    try: return json.load(open(p,encoding='utf-8'))
    except Exception as e: bad(f'{p}: cannot read JSON: {e}'); return None
def check_catalog(c):
    ids={}
    if c.get('schema')!='smartdiet.catalog.v1': bad('catalog: schema must be smartdiet.catalog.v1')
    for i in c.get('ingredients',[]):
        k=i.get('id','?')
        if not str(k).startswith('ing_'): bad(f'catalog {k}: id must start with ing_')
        if k in ids: bad(f'catalog {k}: duplicate id')
        ids[k]=i
        if i.get('state') not in ('raw','cooked','packaged'): bad(f'catalog {k}: state must be raw|cooked|packaged')
        p=i.get('per100g',{})
        for f in ('kcal','p','c','f','fiber','sugar','sodium_mg'):
            if not num(p.get(f)) or p[f]<0: bad(f'catalog {k}: per100g.{f} missing or negative')
        else_ok=all(num(p.get(f)) for f in ('kcal','p','c','f'))
        if else_ok:
            est=4*p['p']+4*p['c']+9*p['f']
            if abs(est-p['kcal'])>max(25,0.15*p['kcal']): bad(f'catalog {k}: kcal {p["kcal"]} does not match 4p+4c+9f = {est:.0f}')
            if p['p']+p['c']+p['f']>100.5: bad(f'catalog {k}: macros add up to more than 100 g per 100 g')
        s=i.get('shop',{})
        if s.get('aisle') not in AISLES: bad(f'catalog {k}: shop.aisle invalid')
        if not s.get('unit'): bad(f'catalog {k}: shop.unit missing')
        if not num(s.get('unit_g')) or s['unit_g']<=0: bad(f'catalog {k}: shop.unit_g must be > 0')
        if not isinstance(s.get('pantry'),bool): bad(f'catalog {k}: shop.pantry must be true/false')
        if not num(s.get('keeps_days')) or s['keeps_days']<=0: bad(f'catalog {k}: shop.keeps_days must be > 0')
        if not i.get('usda_hint'): bad(f'catalog {k}: usda_hint missing')
    return ids
def meal_macros(m,cat,mult=None):
    t=dict(kcal=0,p=0,c=0,f=0,fiber=0,sugar=0,sodium_mg=0)
    for ing in m['ingredients']:
        c=cat[ing['ing']]['per100g'];g=ing['g']
        if mult:
            fx=ing.get('flex')
            if fx: g*=fx[mult]
        for a,b in (('kcal','kcal'),('p','p'),('c','c'),('f','f'),('fiber','fiber'),('sugar','sugar'),('sodium_mg','sodium_mg')): t[a]+=g/100*c[b]
    return t
def check_plan(d,cat,name):
    did=d.get('diet_id');L=LIMITS.get(did)
    if d.get('schema')!='smartdiet.plan.v1': bad(f'{name}: schema must be smartdiet.plan.v1')
    if not L: bad(f'{name}: diet_id must be standard or simple');return
    ref=d.get('reference',{});R=ref.get('kcal',2500)
    meals={}
    pre='std_' if did=='standard' else 'sim_'
    used_ing=set()
    for m in d.get('meals',[]):
        k=m.get('id','?')
        if not k.startswith(pre): bad(f'{name} {k}: id must start with {pre}')
        if k in meals: bad(f'{name} {k}: duplicate meal id')
        meals[k]=m
        if m.get('slot') not in SLOTS: bad(f'{name} {k}: bad slot')
        for a in m.get('allergens',[]):
            if a not in ALLERGENS: bad(f'{name} {k}: unknown allergen {a}')
        ings=m.get('ingredients',[])
        if not ings: bad(f'{name} {k}: no ingredients');continue
        if len(ings)>L['ings']: bad(f'{name} {k}: {len(ings)} ingredients (max {L["ings"]})')
        if not num(m.get('active_min')) or m['active_min']>L['active']: bad(f'{name} {k}: active_min {m.get("active_min")} (max {L["active"]})')
        if len(m.get('steps',[]))>L['steps']: bad(f'{name} {k}: {len(m["steps"])} steps (max {L["steps"]})')
        if not m.get('steps'): bad(f'{name} {k}: no steps')
        ok=True
        for ing in ings:
            if ing.get('ing') not in cat: bad(f'{name} {k}: ingredient {ing.get("ing")} not in catalog');ok=False;continue
            used_ing.add(ing['ing'])
            if not num(ing.get('g')) or ing['g']<=0: bad(f'{name} {k}: bad grams for {ing["ing"]}');ok=False
            if not ing.get('label'): bad(f'{name} {k}: label missing for {ing["ing"]}')
            fx=ing.get('flex')
            if fx is not None and not (isinstance(fx,dict) and num(fx.get('min')) and num(fx.get('max')) and 0<fx['min']<=1<=fx['max']): bad(f'{name} {k}: flex for {ing["ing"]} must be null or {{min<=1<=max}}');ok=False
        if not ok: continue
        t=meal_macros(m,cat);mm=m.get('macros',{})
        for a,tol in (('kcal',max(8,.04*t['kcal'])),('p',max(2,.06*t['p'])),('c',max(2,.06*t['c'])),('f',max(1.5,.06*t['f']))):
            if not num(mm.get(a)) or abs(mm[a]-t[a])>tol: bad(f'{name} {k}: macros.{a} = {mm.get(a)} but ingredients add up to {t[a]:.1f}')
        for sw in m.get('swaps',[]):
            if sw.get('replace') not in [i['ing'] for i in ings]: bad(f'{name} {k}: swap replaces an ingredient the meal does not use')
            if sw.get('with') not in cat: bad(f'{name} {k}: swap uses unknown ingredient {sw.get("with")}')
    week=d.get('week',[])
    if [w.get('day') for w in week]!=DAYS: bad(f'{name}: week must be mon..sun in order')
    train=sum(1 for w in week if w.get('type')=='training');rest=sum(1 for w in week if w.get('type')=='rest')
    if (train,rest)!=(4,3): bad(f'{name}: need 4 training and 3 rest days, got {train}/{rest}')
    usedm=set();week_ings=set()
    for w in week:
        day=w.get('day');ms=[]
        for mid in w.get('meals',[]):
            if mid not in meals: bad(f'{name} {day}: unknown meal {mid}');continue
            ms.append(meals[mid]);usedm.add(mid)
            for i in meals[mid]['ingredients']: week_ings.add(i['ing'])
        slots=[m['slot'] for m in ms]
        for need in ('breakfast','lunch','dinner'):
            if need not in slots: bad(f'{name} {day}: missing {need}')
        if not any(s in slots for s in ('snack','pre_workout','post_workout')): bad(f'{name} {day}: needs a snack or a pre/post-workout meal')
        if w.get('type')=='rest' and ('pre_workout' in slots or 'post_workout' in slots): bad(f'{name} {day}: rest day has pre/post-workout meals')
        if w.get('type')=='training' and not ('pre_workout' in slots or 'post_workout' in slots): bad(f'{name} {day}: training day needs a pre or post workout meal')
        order={'breakfast':0,'pre_workout':1,'lunch':2,'snack':3,'post_workout':4,'dinner':5}
        if len(set(slots))==len(slots) or True:
            pass
        if any(m.get('macros') is None for m in ms): continue
        tot=lambda key,mult=None:sum(meal_macros(m,cat,mult)[key] for m in ms if all(i['ing'] in cat for i in m['ingredients']))
        k1,p1,c1,f1,fb=tot('kcal'),tot('p'),tot('c'),tot('f'),tot('fiber')
        if abs(k1-R)>.05*R: bad(f'{name} {day}: {k1:.0f} kcal (needs {R} ±5%)')
        for lab,v,g in (('protein',p1,ref.get('protein_g',165)),('carbs',c1,ref.get('carbs_g',290)),('fat',f1,ref.get('fat_g',78))):
            if abs(v-g)>.10*g: bad(f'{name} {day}: {lab} {v:.0f} g (needs {g} ±10%)')
        if fb<30: bad(f'{name} {day}: fiber {fb:.0f} g (needs >= 30)')
        lo,hi=tot('kcal','min'),tot('kcal','max')
        if lo>.85*R: bad(f'{name} {day}: at minimum flex the day is {lo:.0f} kcal (must be <= {.85*R:.0f}); add more flexible carbs/fats')
        if hi<1.30*R: bad(f'{name} {day}: at maximum flex the day is {hi:.0f} kcal (must be >= {1.30*R:.0f}); widen flex ranges')
    for k in meals:
        if k not in usedm: bad(f'{name} {k}: meal is never used in the week')
    if len(week_ings)>L['week_ings']: bad(f'{name}: {len(week_ings)} distinct ingredients in the week (max {L["week_ings"]})')
    for p in d.get('prep',[]):
        for c in p.get('covers',[]):
            if c not in meals: bad(f'{name}: prep covers unknown meal {c}')
        if did=='simple' and num(p.get('minutes')) and p['minutes']>30: bad(f'{name}: simple-diet prep task over 30 minutes')
    return used_ing
def main():
    if len(sys.argv)<3: print(__doc__);sys.exit(2)
    c=load(sys.argv[1]);
    if c is None: print('\n'.join(errs));sys.exit(1)
    cat=check_catalog(c);used=set()
    for p in sys.argv[2:]:
        d=load(p)
        if d is not None: used|=check_plan(d,cat,p.split('/')[-1]) or set()
    for k in cat:
        if k not in used: bad(f'catalog {k}: not used by any meal')
    if errs:
        print(f'{len(errs)} problem(s):');print('\n'.join(' - '+e for e in errs));sys.exit(1)
    print('OK: catalog and plans are consistent')
main()
