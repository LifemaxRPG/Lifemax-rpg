import math
# order: px,py,torso,neck,thn,shn,thf,shf,upn,fon,upf,fof
S=lambda *a:list(a)
D={}
def m(id,anchor,**p): D[id]=(anchor,p)
a180=[180]*4
m('dumbbell_rdl','ankle',A=S(0,.08,0,0,180,180,180,180,180,180,180,180),B=S(0,.08,75,20,170,180,170,180,180,180,180,180))
m('seated_cable_row','hip',A=S(0,.42,10,0,90,140,90,140,100,90,100,90),B=S(0,.42,-5,-5,90,140,90,140,190,90,190,90))
m('supported_dumbbell_row','hip',A=S(0,.95,45,45,170,170,170,170,180,180,180,180),B=S(0,.95,45,45,170,170,170,170,225,135,225,135))
m('standing_dumbbell_overhead_press','ankle',A=S(0,.08,0,0,180,180,180,180,180,0,180,0),B=S(0,.08,-5,0,180,180,180,180,5,5,5,5))
m('machine_overhead_press','hip',A=S(0,.52,0,0,90,180,90,180,180,0,180,0),B=S(0,.52,0,0,90,180,90,180,5,5,5,5))
m('dumbbell_reverse_lunge','ankle',A=S(0,.08,0,0,180,180,180,180,180,180,180,180),B=S(0,.08,5,5,90,180,180,270,180,180,180,180))
m('dumbbell_step_up','ankle',A=S(0,.48,5,5,100,190,180,180,180,180,180,180),B=S(0,.48,0,0,180,180,180,180,180,180,180,180))
m('dead_bug','hip',A=S(0,.12,-90,-90,0,90,0,90,0,0,0,0),B=S(0,.12,-90,-90,85,85,0,90,-85,-85,0,0))
m('outdoor_walk','ankle',A=S(.3,.08,5,5,155,170,205,210,210,210,150,150),B=S(0,.08,5,5,175,185,175,185,185,185,185,185),C=S(-.2,.15,5,5,205,210,155,170,150,150,210,210),D=S(.1,.15,5,5,195,190,175,185,185,185,185,185))
m('worlds_greatest_stretch','ankle',A=S(0,.08,0,0,180,180,180,180,180,180,180,180),B=S(.4,.08,70,40,90,180,180,270,180,180,180,180),C=S(.4,.08,50,20,90,180,180,270,0,0,180,180),D=S(.4,.08,70,40,120,120,180,270,180,180,180,180))
m('hamstring_adductor_flow','ankle',A=S(0,.08,0,0,120,120,180,90,180,180,180,180),B=S(0,.08,60,30,120,120,180,90,120,120,120,120))
m('thoracic_extension_roller','hip',A=S(0,.12,-90,-90,30,150,30,150,-30,-120,-30,-120),B=S(0,.12,-90,-130,30,150,30,150,-30,-130,-30,-130))
m('forearm_plank','hip',A=S(0,.3,76,80,-104,-104,-104,-104,180,90,180,90))
m('push_up','ankle',A=S(0,.08,66,66,-114,-114,-114,-114,180,180,180,180),B=S(0,.08,87,87,-93,-93,-93,-93,-70,140,-70,140))
m('incline_push_up','ankle',A=S(0,.08,47,47,-133,-133,-133,-133,168,168,168,168),B=S(0,.08,72,72,-108,-108,-108,-108,-45,135,-45,135))
m('prone_cobra','hip',A=S(0,.12,90,90,-90,-90,-90,-90,-90,-90,-90,-90),B=S(0,.12,75,75,-90,-90,-90,-90,-90,-90,-90,-90))
m('lying_leg_curl','hip',A=S(0,.45,90,90,-90,-90,-90,-90,180,90,180,90),B=S(0,.45,90,90,-90,0,-90,0,180,90,180,90))
m('seated_leg_curl','hip',A=S(0,.45,0,0,90,90,90,90,160,90,160,90),B=S(0,.45,0,0,90,210,90,210,160,90,160,90))
m('glute_bridge','hip',A=S(0,.1,-90,-90,60,120,60,120,-90,-90,-90,-90),B=S(0,.3,-110,-90,70,142,70,142,-90,-90,-90,-90))
m('glute_bridge_hold','hip',A=S(0,.3,-110,-90,70,142,70,142,-90,-90,-90,-90))
m('treadmill_walk','ankle',A=S(.2,.08,5,5,155,170,205,210,210,210,150,150),B=S(0,.08,5,5,175,185,175,185,185,185,185,185),C=S(-.2,.15,5,5,205,210,155,170,150,150,210,210),D=S(0,.15,5,5,195,190,175,185,185,185,185,185))
m('treadmill_jog','ankle',A=S(.3,.2,10,10,140,150,210,220,160,160,210,210),B=S(0,.15,10,10,175,190,175,190,185,185,185,185),C=S(-.3,.2,10,10,210,220,140,150,210,210,160,160),D=S(0,.15,10,10,175,190,175,190,185,185,185,185))
m('incline_walking','ankle',A=S(.2,.15,15,15,150,165,215,220,210,210,150,150),B=S(-.1,.1,15,15,175,185,175,185,185,185,185,185),C=S(-.3,.25,15,15,215,220,150,165,150,150,210,210),D=S(0,.2,15,15,175,185,175,185,185,185,185,185))
m('intervals_hard','ankle',A=S(.4,.15,20,20,130,140,220,230,150,150,220,220),B=S(0,.2,20,20,170,180,170,180,185,185,185,185),C=S(-.3,.15,20,20,220,230,130,140,220,220,150,150),D=S(0,.2,20,20,170,180,170,180,185,185,185,185))
m('intervals_easy','ankle',A=S(.2,.08,0,0,170,180,195,200,200,200,170,170),B=S(-.1,.08,0,0,180,185,180,185,185,185,185,185),C=S(-.2,.15,0,0,195,200,170,180,170,170,200,200),D=S(0,.15,0,0,185,185,180,185,185,185,185,185))
m('farmers_carry','ankle',A=S(.2,.08,0,0,170,180,195,200,180,180,180,180),B=S(-.1,.08,0,0,180,185,180,185,180,180,180,180),C=S(-.2,.15,0,0,195,200,170,180,180,180,180,180),D=S(0,.15,0,0,185,185,180,185,180,180,180,180))
m('goblet_squat','ankle',A=S(0,.08,0,0,180,180,180,180,160,90,160,90),B=S(0,.08,40,20,80,195,80,195,130,90,130,90))
m('bodyweight_squat','ankle',A=S(0,.08,0,0,180,180,180,180,180,180,180,180),B=S(0,.08,40,20,80,195,80,195,90,90,90,90))
m('box_sit_to_stand','ankle',A=S(0,.08,0,0,180,180,180,180,160,90,160,90),B=S(0,.08,25,10,90,180,90,180,120,90,120,90))
m('dumbbell_split_squat','ankle',A=S(0,.08,0,0,180,180,205,205,180,180,180,180),B=S(0,.08,5,5,90,180,180,265,180,180,180,180))
L=dict(t=.52,th=.44,sh=.44,ua=.32,fa=.27)
v=lambda l,a:(l*math.sin(math.radians(a)),l*math.cos(math.radians(a)))
add=lambda p,q:(p[0]+q[0],p[1]+q[1])
def solve(anchor,p):
    px,py,t,n,thn,shn,thf,shf,upn,fon,upf,fof=p
    if anchor=='hip': hip=(px,py)
    else:
        a=(px,py); hip=(a[0]-v(.44,thn)[0]-v(.44,shn)[0], a[1]-v(.44,thn)[1]-v(.44,shn)[1])
    kn=add(hip,v(.44,thn)); an=add(kn,v(.44,shn))
    kf=add(hip,v(.44,thf)); af=add(kf,v(.44,shf))
    sh=add(hip,v(.52,t)); en=add(sh,v(.32,upn)); wn=add(en,v(.27,fon)); ef=add(sh,v(.32,upf)); wf=add(ef,v(.27,fof))
    return dict(hip=hip,kn=kn,an=an,kf=kf,af=af,sh=sh,wn=wn,wf=wf,head=add(sh,v(.2,n)))
LOOP={'outdoor_walk','treadmill_walk','treadmill_jog','incline_walking','intervals_hard','intervals_easy','farmers_carry'}
UNI={'dumbbell_reverse_lunge','dumbbell_step_up','dumbbell_split_squat','hamstring_adductor_flow','worlds_greatest_stretch'}
issues={}
def f(id,pn,msg): issues.setdefault(id,[]).append(f'{pn}: {msg}')
for id,(anc,P) in D.items():
    for pn,p in P.items():
        r=solve(anc,p)
        for k in('an','af','kn','kf','wn','wf','head','sh'):
            if r[k][1]< -0.02 and not(id in('thoracic_extension_roller',)): f(id,pn,f'{k} below floor y={r[k][1]:.2f}')
        for k in('an','af'):
            if r[k][1]<.02: f(id,pn,f'{k} y={r[k][1]:.2f} through floor')
        # knee bending the wrong way: shin should not swing forward of thigh line when leg is hanging/forward-facing
        for side,th,sh_ in (('n',p[4],p[5]),('f',p[6],p[7])):
            d=(sh_-th+540)%360-180   # signed change, + = shin rotated forward (clockwise from up)
            if d<-8 and id not in('hamstring_adductor_flow',) : f(id,pn,f'knee bends backward ({side}) thigh {th} shin {sh_}')
        # hip height sanity for standing
        if anc=='ankle' and id not in('push_up','incline_push_up'):
            if r['hip'][1]<.2 and id!='worlds_greatest_stretch': f(id,pn,f'hip too low y={r["hip"][1]:.2f}')
            if r['hip'][1]>1.12 and id!='dumbbell_step_up': f(id,pn,f'hip too high y={r["hip"][1]:.2f}')
        # torso horizontal: legs must go opposite way to head
        if abs(math.cos(math.radians(p[2])))<.45 and id!='worlds_greatest_stretch':
            ts=math.sin(math.radians(p[2])); ls=math.sin(math.radians(p[4]))
            if ts*ls>.2: f(id,pn,f'head and legs same direction (torso {p[2]}, thigh {p[4]})')
        # stance leg ankles on floor / far ankle height for standing
        if id in('dumbbell_split_squat','dumbbell_reverse_lunge','worlds_greatest_stretch') and r['af'][1]>.30: f(id,pn,f'rear foot floating y={r["af"][1]:.2f}')
        if anc=='ankle' and id not in LOOP and id not in UNI and id not in('dumbbell_step_up',):
            if r['af'][1]>.45: f(id,pn,f'far ankle floating y={r["af"][1]:.2f}')
            if abs(r['af'][0]-p[0])>.45: f(id,pn,f'far ankle x off from near by {r["af"][0]-p[0]:.2f}')
        if id in LOOP and anc=='ankle':
            lo=min(r['an'][1],r['af'][1]); 
            if lo>.4: f(id,pn,f'both feet airborne? low ankle y={lo:.2f}')
        if id in('push_up','incline_push_up','forearm_plank'):
            f_=[('wrist y',r['wn'][1])]
            if id=='push_up' and not(.0<=r['wn'][1]<=.12): f(id,pn,f'hand not on floor: wrist y={r["wn"][1]:.2f}')
            if id=='incline_push_up' and not(.3<=r['wn'][1]<=.55): f(id,pn,f'hand not on bench: wrist y={r["wn"][1]:.2f}')
            if id=='forearm_plank' and not(.0<=r['wn'][1]<=.12): f(id,pn,f'forearm not on floor: wrist y={r["wn"][1]:.2f}')
            # body line straightness
            f_ = abs(((p[2]+180)-p[4]+540)%360-180)
            if f_>8: f(id,pn,f'body not a straight line (torso {p[2]} vs legs {p[4]}: off {f_:.0f} deg)')
            if abs(r['hip'][1]-(r['sh'][1]+r['an'][1])/2)>.25: pass
            if id!='forearm_plank' and abs(r['an'][1]-.08)>.1: f(id,pn,f'toes not on floor y={r["an"][1]:.2f}')
        if id in('seated_cable_row','lat','leg_press'): pass
        if id in('standing_dumbbell_overhead_press','machine_overhead_press') and pn=='B':
            if r['wn'][1]-r['sh'][1]<.45: f(id,pn,'wrists not overhead')
        if id=='goblet_squat' or id=='bodyweight_squat' or id=='box_sit_to_stand':
            pass
        if id=='glute_bridge' or id=='glute_bridge_hold':
            if abs(r['an'][1]-.08)>.12: f(id,pn,f'feet not on floor y={r["an"][1]:.2f}')
            if r['sh'][1]>r['hip'][1]+.3 and pn=='A': pass
        if id in('prone_cobra','lying_leg_curl'):
            ank=r['an'][1]
            if id=='prone_cobra' and ank>.2: f(id,pn,f'feet lifted y={ank:.2f}')
        if id=='dead_bug':
            pass
        if id=='dumbbell_reverse_lunge' and pn=='B':
            pass
        if id=='dumbbell_split_squat':
            if r['af'][1]>.4 and False: pass
for id in D:
    print(('FAIL ' if id in issues else 'ok   ')+id)
    for s in issues.get(id,[]): print('      ',s)
print(len(issues),'of',len(D),'moves flagged')
