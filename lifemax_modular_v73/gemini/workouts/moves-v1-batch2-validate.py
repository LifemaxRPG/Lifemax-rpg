import math
# order: px,py,torso,neck,thn,shn,thf,shf,upn,fon,upf,fof
S=lambda *a:list(a)
D={}
def m(id,anchor,**p): D[id]=(anchor,p)
a180=[180]*4

m('bird_dog','hip',A=S(0,.52,80,80,180,-90,180,-90,180,180,180,180),B=S(0,.52,80,80,-90,-90,180,-90,180,180,90,90))
m('cat_cow','hip',A=S(0,.52,80,80,180,-90,180,-90,180,180,180,180),B=S(0,.52,80,80,180,-90,180,-90,180,180,180,180))
m('gentle_thoracic_rotations','hip',A=S(0,.52,80,80,180,-90,180,-90,35,135,180,180),B=S(0,.52,80,80,180,-90,180,-90,-15,75,180,180))
m('kneeling_hip_flexor_stretch','hip',A=S(0,.52,0,0,180,-90,90,180,180,180,180,180))
m('standing_calf_stretch','ankle',A=S(0,.08,15,15,200,200,150,195,90,90,90,90))
m('standing_quad_stretch','ankle',A=S(0,.08,0,0,180,180,180,-25,180,180,180,180))
m('seated_hamstring_stretch','hip',A=S(0,.134,0,0,90,90,50,140,90,90,90,90))
m('seated_torso_twist','hip',A=S(0,.134,0,0,90,90,50,140,105,90,105,90))
m('full_body_mobility_flow','hip',A=S(0,.96,0,0,180,180,180,180,180,180,180,180),B=S(0,.593,40,40,105,205,105,205,90,90,90,90),C=S(0,.96,160,160,180,180,180,180,90,180,90,180),D=S(0,.72,125,125,225,225,225,225,125,125,125,125),E=S(0,.96,0,0,180,180,180,180,180,180,180,180))
m('assisted_pull_up','hip',A=S(0,.69,0,0,165,260,165,260,0,0,0,0),B=S(0,1.023,0,0,165,260,165,260,75,-50,75,-50))
m('seated_cable_row','hip',A=S(0,.52,0,0,90,180,90,180,150,128,150,128),B=S(0,.52,0,0,90,180,90,180,195,120,195,120))
m('machine_chest_press','hip',A=S(0,.52,0,0,90,180,90,180,135,75,135,75),B=S(0,.52,0,0,90,180,90,180,100,112.5,100,112.5))
m('cable_face_pull','hip',A=S(0,.88,0,0,180,180,180,180,70,70,70,70),B=S(0,.88,0,0,180,180,180,180,85,50,85,50))
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
LOOP={'full_body_mobility_flow','outdoor_walk','treadmill_walk','treadmill_jog','incline_walking','intervals_hard','intervals_easy','farmers_carry'}
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
