import math
# order: px,py,torso,neck,thn,shn,thf,shf,upn,fon,upf,fof
S=lambda *a:list(a)
D={}
def m(id,anchor,**p): D[id]=(anchor,p)
a180=[180]*4

m('leg_press','hip',A=S(0,.45,-45,-40,5,95,5,95,150,120,150,120),B=S(0,.45,-45,-40,45,45,45,45,150,120,150,120))
m('machine_chest_press','hip',A=S(0,.45,0,0,90,180,90,180,180,90,180,90),B=S(0,.45,0,0,90,180,90,180,90,90,90,90))
m('dumbbell_chest_press','hip',A=S(0,.45,-90,-90,90,180,90,180,90,0,90,0),B=S(0,.45,-90,-90,90,180,90,180,0,0,0,0))
m('lat_pulldown','hip',A=S(0,.45,-10,0,90,180,90,180,-15,-15,-15,-15),B=S(0,.45,-20,-10,90,180,90,180,160,30,160,30))
m('assisted_pull_up','hip',A=S(0,.7,0,0,180,-90,180,-90,0,0,0,0),B=S(0,1.2,-15,-10,180,-90,180,-90,160,30,160,30))
m('stationary_bike','hip',A=S(0,.7,20,0,110,130,150,170,50,120,50,120),B=S(0,.7,20,0,130,150,130,150,50,120,50,120),C=S(0,.7,20,0,150,170,110,130,50,120,50,120),D=S(0,.7,20,0,130,150,130,150,50,120,50,120))
m('elliptical','hip',A=S(0,.9,5,0,160,160,130,140,20,90,80,90),B=S(0,.88,5,0,145,150,145,150,50,90,50,90),C=S(0,.9,5,0,130,140,160,160,80,90,20,90),D=S(0,.88,5,0,145,150,145,150,50,90,50,90))
m('incline_machine_press','hip',A=S(0,.45,-30,-30,90,180,90,180,150,60,150,60),B=S(0,.45,-30,-30,90,180,90,180,60,60,60,60))
m('incline_dumbbell_press','hip',A=S(0,.45,-30,-30,90,180,90,180,150,0,150,0),B=S(0,.45,-30,-30,90,180,90,180,0,0,0,0))
m('leg_extension','hip',A=S(0,.55,0,0,90,170,90,170,160,90,160,90),B=S(0,.55,0,0,90,90,90,90,160,90,160,90))
m('cable_face_pull','ankle',A=S(0,.08,5,0,180,180,180,180,80,80,80,80),B=S(0,.08,5,0,180,180,180,180,-10,180,-10,180))
m('reverse_pec_deck','hip',A=S(0,.45,0,0,90,180,90,180,90,90,90,90),B=S(0,.45,0,0,90,180,90,180,90,90,90,90))
m('standing_calf_raise','ankle',A=S(0,.15,0,0,180,180,180,180,180,180,180,180),B=S(0,.2,0,0,180,180,180,180,180,180,180,180))
m('seated_calf_raise','hip',A=S(0,.45,0,0,90,180,90,180,180,90,180,90),B=S(0,.45,0,0,85,180,85,180,180,90,180,90))
m('pallof_press','ankle',A=S(0,.08,0,0,180,180,180,180,160,-20,160,-20),B=S(0,.08,0,0,180,180,180,180,90,90,90,90))
m('band_row','ankle',A=S(0,.08,5,0,160,180,160,180,90,90,90,90),B=S(0,.08,5,0,160,180,160,180,170,10,170,10))
m('full_body_mobility_flow','ankle',A=S(0,.08,0,0,180,180,180,180,180,180,180,180),B=S(0,.08,40,0,40,140,40,140,40,40,40,40),C=S(0,.08,90,90,180,180,180,180,180,180,180,180),D=S(.8,.08,45,45,135,135,135,135,-45,-45,-45,-45))
m('kneeling_hip_flexor_stretch','ankle',A=S(0,.08,0,0,90,180,170,90,180,180,180,180))
m('step_touch','ankle',A=S(0,.08,0,0,180,180,180,180,160,120,160,120),B=S(.4,.08,0,0,180,180,170,180,160,120,160,120),C=S(.6,.08,0,0,180,180,180,180,160,120,160,120))
m('wall_slide','ankle',A=S(0,.08,0,0,180,180,180,180,90,0,90,0),B=S(0,.08,0,0,180,180,180,180,20,0,20,0))
m('standing_hamstring_sweep','ankle',A=S(0,.08,0,0,160,160,180,180,180,180,180,180),B=S(0,.08,70,40,160,160,150,150,100,100,100,100))
m('bird_dog','hip',A=S(0,.5,90,90,180,90,180,90,180,180,180,180),B=S(0,.5,90,90,90,90,180,90,180,180,90,90))
m('cat_cow','hip',A=S(0,.5,90,120,180,90,180,90,180,180,180,180),B=S(0,.5,90,60,180,90,180,90,180,180,180,180))
m('seated_torso_twist','hip',A=S(0,.08,0,0,90,180,110,160,130,130,-160,180))
m('single_leg_balance','ankle',A=S(0,.08,0,0,180,180,90,180,170,170,170,170))
m('standing_calf_stretch','ankle',A=S(0,.08,20,20,-15,-15,30,30,90,90,90,90))
m('standing_quad_stretch','ankle',A=S(0,.08,0,0,180,20,180,180,170,90,90,90))
m('seated_hamstring_stretch','hip',A=S(0,.08,40,40,90,90,90,180,50,50,50,50))
m('chest_stretch','ankle',A=S(0,.08,10,0,180,180,160,180,90,0,180,180))
m('gentle_thoracic_rotations','hip',A=S(0,.5,90,90,180,90,180,90,180,180,180,180),B=S(0,.5,90,90,180,90,180,90,0,0,180,180))
m('back_extension_machine','hip',A=S(0,.45,30,30,90,180,90,180,30,30,30,30),B=S(0,.45,-15,-15,90,180,90,180,-15,-15,-15,-15))
m('band_romanian_deadlift','ankle',A=S(0,.08,0,0,180,180,180,180,180,180,180,180),B=S(0,.08,80,20,165,175,165,175,180,180,180,180))
m('standing_dumbbell_row','ankle',A=S(0,.08,70,40,160,170,160,170,180,180,180,180),B=S(0,.08,70,40,160,170,160,170,-40,-20,-40,-20))
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
LOOP={'stationary_bike','elliptical','step_touch','outdoor_walk','treadmill_walk','treadmill_jog','incline_walking','intervals_hard','intervals_easy','farmers_carry'}
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
