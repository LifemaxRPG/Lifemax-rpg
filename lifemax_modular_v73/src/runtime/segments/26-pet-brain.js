
/* ===== Pet brain (v78): the feline inner-life engine =====
   One layer on top of the existing routine (petThink), depth stage (24) and likes (25). It does not
   replace them: the day plan, bond levels, hunger and the depth tiers all work exactly as before.
   The brain adds a mood (valence x arousal), petting saturation, four attachment threads, a memory of
   good places and moments, the player's visiting rhythm, quirks, and a handful of new behaviours.
   It steers the cat by re-weighting the options petThink already offers and by adding new ones.
   Everything is saved in state.pet.brain (about 2 KB). Coordinates follow 24-pet-depth.js:
   posZ is 0 (back wall) to 100 (the glass). Tunable values are in PET_BRAIN. */
var PET_BRAIN={
  TICK_MS:1000,
  LAMBDA_V:0.15,LAMBDA_A:0.3,                        /* per minute, toward the baseline (v78.1: was 0.04/0.08, which let play pin him at the extremes) */
  SAT_DECAY:0.15,SAT_TAP:0.12,SAT_HOLD:0.09,SAT_WARN:0.65,SAT_MAX:0.85,RESPECT_WAIT_MS:8000,
  PET_TRUST_DAY_CAP:0.04,                            /* trust from petting alone, per day (no grinding) */
  PLAY_A:0.25,PLAY_V:-0.1,REM_A:-0.55,
  CONFLICT:0.12,HES_DZ:35,HES_COOLDOWN_MS:45000,
  MEM_ALPHA:0.18,MEM_EPISODES:24,MEM_CORE:8,KNEAD_W:0.28,
  ROUTINE_BUMP:0.15,ROUTINE_DECAY:0.98,VIGIL_H:0.55,VIGIL_FAM:0.35,MISSED_H:0.7,
  APPROACH_IDLE_MS:15000,APPROACH_RATE:0.035,APPROACH_FIRE:0.8,APPROACH_COOLDOWN_MS:240000,
  BLINK_P:0.05,BLINK_EVERY_MS:15000,                 /* v78.1: offered slow blinks were too frequent */
 BLINK_WINDOW_MS:2500,BLINK_MIN_Z:50,
  PAR_OPEN_MS:45000,PAR_IDLE_MS:20000,PAR_SEC:0.45,PAR_A:0.15,
  OFF_MIN_MS:5*60000,OFF_STEP_MS:30*60000,OFF_MAX_STEPS:12,
  FRAP_ENERGY:0.85,FRAP_P:0.25,FRAP_COOLDOWN_MS:2*3600000,ENERGY_RATE:0.02,
  HUNT_IDLE_MS:20000,TROPHY_TRUST:0.65,TROPHY_P:0.35,
  SESSION_GAP_MS:30*60000,PERSIST_MS:60000,
  OWNS_GLASS:true                                    /* the brain decides when he visits the glass (replaces the 20 s idle rule in 24) */
};
var PET_TRAIT_BASE={
  shy:    {v:0.10,a:-0.15,aut:0.75,sat:1.30,pitch:1.06},
  playful:{v:0.25,a:0.15, aut:0.60,sat:0.90,pitch:1.02},
  lazy:   {v:0.25,a:-0.25,aut:0.65,sat:0.80,pitch:0.94},
  curious:{v:0.20,a:0.05, aut:0.70,sat:1.00,pitch:1.00}
};
var PET_QUIRKS={
  paw_tuck_sleeper:{name:'Paw-tuck sleeper',text:'tucks a paw forward and shuts his eyes when sleeping deeply'},
  gravity_tester:  {name:'Gravity tester',text:'nudges toys along the floor, just to see what happens'},
  dramatic_flopper:{name:'Dramatic flopper',text:'flops over sideways instead of sitting when he is very happy'},
  greeble_spotter: {name:'Greeble spotter',text:'sometimes freezes and stares at absolutely nothing'},
  polite_paw_tap:  {name:'Polite paw tap',text:'raises a paw at the glass instead of meowing'},
  post_meal_zoomer:{name:'Post-meal zoomer',text:'gets a burst of energy right after eating'},
  shadow_chaser:   {name:'Shadow chaser',text:'pounces on his own shadow'},
  chirp_greeter:   {name:'Chirp greeter',text:'greets you with a little triple chirp'},
  night_owl_sentinel:{name:'Night owl',text:'is extra sociable and playful late at night',acq:1},
  toy_hoarder:     {name:'Toy hoarder',text:'gathers toys near his favorite place while you are away',acq:1},
  lap_barnacle:    {name:'Lap barnacle',text:'stays close to the glass whenever you are here',acq:1},
  hydro_inspector: {name:'Bowl inspector',text:'paws at the floor beside the bowl before eating',acq:1}
};
var PET_QUIRK_INNATE=['paw_tuck_sleeper','gravity_tester','dramatic_flopper','greeble_spotter','polite_paw_tap','post_meal_zoomer','shadow_chaser','chirp_greeter'];
var PET_QUIRK_ACQ=[['night_owl_sentinel','n',10],['toy_hoarder','t',15],['lap_barnacle','g',20],['hydro_inspector','k',12]];
var PET_CORE_TEXT={
  first_blink:'First slow blink shared',first_belly:'Slept belly-up near you for the first time',first_respect:'You stopped petting when he asked',
  first_trophy:'Brought you his first trophy',first_glass:'Came to the glass just to see you',first_vigil:'Waited up for you',
  first_zoomies:'You saw his first zoomies',first_co:'First quiet stretch of time side by side',quirk:'Grew a new quirk'
};
var PET_BRAIN_ERR=0;
function petBrainErr(where,e){if(PET_BRAIN_ERR<8){PET_BRAIN_ERR++;console.warn('pet brain: '+where,e)}}
function petBC(v,a,b){return Math.max(a,Math.min(b,v))}
function petR3(v){return Math.round(v*1000)/1000}
/* ---------- state: state.pet.brain ---------- */
function petMemZones(){return Object.keys(PET_SPOTS).concat(['front_living','front_bedroom','front_kitchen'])}
function migratePetBrainState(p){
  if(!p)return null;let b=p.brain;if(b&&b.v===1&&b.af&&b.at&&b.mem&&b.rt&&b.qk&&b.off)return b;
  b=b&&typeof b==='object'?b:{};const T=PET_TRAIT_BASE[p.trait]||PET_TRAIT_BASE.curious,r=petRng(petHash(p.born+'|brain'));
  /* v77 saves have bondPts (0..580+) and nothing else: seed the four threads from it so no progress is lost */
  const f=petBC((p.bondPts||0)/PET_BOND_AT[PET_BOND_AT.length-1],0,1);
  b.v=1;
  b.af=Object.assign({v:petR3(T.v),a:-0.1,s:0,t:Date.now()},b.af||{});
  b.at=Object.assign({fam:petR3(0.15+0.75*f),tru:petR3(0.2+0.7*f),sec:petR3(0.2+0.65*f),aut:T.aut,dg:0,day:'',style:''},b.at||{});
  b.gz=Object.assign({n:0},b.gz||{});
  b.sp=Object.assign({at:0},b.sp||{});
  if(!b.mem||!b.mem.w){const w={},Z=petMemZones();Z.forEach(function(z){w[z]=1/Z.length});b.mem={w:w,ep:[],core:[],fx:{}}}
  b.mem.ep=b.mem.ep||[];b.mem.core=b.mem.core||[];b.mem.fx=b.mem.fx||{};
  if(!b.rt||!Array.isArray(b.rt.h)||b.rt.h.length!==24)b.rt={h:new Array(24).fill(0.05),last:0,night:''};
  if(!b.qk||!Array.isArray(b.qk.inn)){const pool=PET_QUIRK_INNATE.slice(),inn=[];while(inn.length<2){inn.push(pool.splice(Math.floor(r()*pool.length),1)[0])}
    b.qk={inn:inn,acq:[],c:{n:0,t:0,g:0,k:0}}}
  b.qk.c=Object.assign({n:0,t:0,g:0,k:0},b.qk.c||{});b.qk.acq=b.qk.acq||[];
  b.sl=Object.assign({st:'none',pose:''},b.sl||{});
  b.zm=Object.assign({e:0.3,at:0},b.zm||{});
  b.hu=Object.assign({gifts:0},b.hu||{});
  b.co=Object.assign({min:0},b.co||{});
  b.off=Object.assign({at:Date.now(),acts:[],scene:''},b.off||{});
  b.items=Array.isArray(b.items)?b.items:[];
  b.vo=b.vo&&b.vo.p?b.vo:{p:petR3(petBC(T.pitch*(0.92+r()*0.16),0.88,1.14)),t:petR3(petBC((p.trait==='shy'?0.3:p.trait==='curious'?0.65:0.5)+r()*0.25-0.1,0.15,0.9))};
  p.brain=b;return b;
}
function petBrain(p){p=p||petData();return p?migratePetBrainState(p):null}
function petHasQ(b,id){return b.qk.inn.indexOf(id)>=0||b.qk.acq.indexOf(id)>=0}
function petBR(rt){
  if(!rt.br)rt.br={acc:0,som:{ear:'forward',tail:'neutral',pupil:'medium',mood:'content'},somKey:'',taps:[],hesAt:0,gaze:null,gazeCheck:0,auto:null,conflict:null,
    frap:false,hunt:null,stim:null,carry:null,co:null,coTry:0,viewSince:0,drive:0,blinkBtnAt:[],dirty:false,savedAt:Date.now(),beat:0,warnAt:0,respectAt:0,
    sessionAt:0,lastVocal:0,chatterAt:0,remUntil:0,remNext:0,flags:{}};
  return rt.br;
}
function petBrainPersist(force){
  const rt=PET_RT;if(!rt||!rt.br)return;const br=rt.br,now=Date.now();
  if(!br.dirty&&!force)return;if(!force&&now-br.savedAt<PET_BRAIN.PERSIST_MS)return;
  br.dirty=false;br.savedAt=now;
  /* save() would also re-render the whole app; the brain only needs the current state written */
  try{localStorage.setItem(STORE,JSON.stringify(state))}catch(e){}
}
/* ---------- System A: affect (valence x arousal) ---------- */
function petBrainBase(p,b,rt){
  const T=PET_TRAIT_BASE[p.trait]||PET_TRAIT_BASE.curious,st=petStage(p),A=b.at,h=petHour(),kit=Math.max(0,1-petGrowth(p));
  const blk=(rt&&rt.blk)||petBlockNow(p);
  let v=T.v+0.3*(A.sec-0.3)+0.15*(A.tru-0.3)-[0,0.05,0.25,0.45,0.6,0.7][st];
  let a=({sleep:-0.75,nap:-0.6,rest:-0.25,active:0.2})[blk.k]||0;a+=T.a+0.15*kit;
  if(blk.k==='active'&&((h>=5.5&&h<8.5)||(h>=18.5&&h<22)))a+=0.15;            /* dawn and dusk */
  if(petHasQ(b,'night_owl_sentinel')&&(h>=22||h<4)){v+=0.15;a+=0.15}
  if(st>=3)a-=0.3;
  a+=0.12*(b.zm.e-0.5);
  return {v:petBC(v,-1,1),a:petBC(a,-1,1)};
}
/* soft limits: each push counts for less the closer he already is to an extreme */
function petSoft(x,d){d=d||0;return petBC(x+d*(d>0?1-Math.max(0,x):1+Math.min(0,x)),-1,1)}
function petAffect(b,dv,da){b.af.v=petSoft(b.af.v,dv);b.af.a=petSoft(b.af.a,da)}
function petMoodOf(af){
  const v=af.v,a=af.a,s=af.s;
  if(s>=PET_BRAIN.SAT_WARN)return ['overstimulated','has had enough petting for now'];
  if(a<-0.55)return v>0?['drowsy','is deeply relaxed']:['tired','is tired and a little flat'];
  if(v>=0.35&&a>=0.35)return ['playful','is full of beans'];
  if(v>=0.35&&a<0)return ['content','is content and cosy'];
  if(v>=0.15)return a>0.15?['bright','is bright-eyed and curious']:['calm','is calm'];
  if(v<=-0.3&&a>0.2)return ['uneasy','is a bit on edge'];
  if(v<=-0.2)return ['sulky','is keeping to himself'];
  return ['watchful','is quietly watchful'];
}
/* ---------- System G: four attachment threads ---------- */
function petAttach(b,k,d){const A=b.at,floor=(k==='tru'||k==='sec')?0.15:0;A[k]=petR3(petBC(A[k]+d,floor,1))}
function petBondStyle(p,b){
  const A=b.at,t=p.trait;
  if(A.tru<0.35&&A.fam<0.4)return {id:'newcomer',name:'Cautious newcomer',text:'Still working out whether you are safe. Let him come to you.'};
  if(t==='shy'&&A.tru>=0.55&&A.sec>=0.5)return {id:'shadow',name:'Gentle shadow',text:'Sees you as a safe harbor. Quiet company and slow blinks mean the most to him.'};
  if((t==='curious'||A.aut>=0.75)&&A.tru>=0.55&&A.aut>=0.6)return {id:'partner',name:'Proud partner',text:'Comes and goes on his own terms, and brings you things to prove it.'};
  if(t==='lazy'&&A.sec>=0.55&&A.fam>=0.5)return {id:'velcro',name:'Velcro loaf',text:'Happiest wherever you are, ideally lying down.'};
  if(t==='playful'&&A.fam>=0.5)return {id:'gremlin',name:'Mischief gremlin',text:'Treats you as his favorite playmate. Expect ambushes.'};
  const m=Math.max(A.tru,A.fam,A.sec);
  if(m===A.tru&&A.tru>=0.5)return {id:'trusting',name:'Trusting companion',text:'Relaxes when you are around and lets you see his softer side.'};
  if(m===A.sec&&A.sec>=0.5)return {id:'settled',name:'Settled companion',text:'Feels at home with you nearby, even when he is busy with his own things.'};
  if(m===A.fam&&A.fam>=0.5)return {id:'housemate',name:'Familiar housemate',text:'Knows your habits well. Trust is still growing.'};
  return {id:'curious',name:'Curious companion',text:'Getting to know your rhythms, one visit at a time.'};
}
function petThreadWord(k,v){
  if(k==='fam')return v<0.3?'Still learning your scent':v<0.6?'Knows your routines':'You are part of his world';
  if(k==='tru')return v<0.3?'Unsure of your hands':v<0.6?'Mostly trusts you':v<0.8?'Trusts you deeply':'Utterly trusting';
  if(k==='sec')return v<0.35?'Startles easily around you':v<0.65?'Feels safe with you nearby':'You are his safe harbor';
  return v<0.4?'Feels a little crowded':v<0.7?'Comfortable doing his own thing':'Confident and self-directed';
}
/* ---------- System B: memory of places and moments ---------- */
function petZoneOf(c){
  if(!c)return null;if(c.surf==='floor'&&c.z>=72)return 'front_'+(petRoomOf(c.x)==='living'?'living':petRoomOf(c.x)==='kitchen'?'kitchen':'bedroom');
  const s=petSpotAt(c);return s&&PET_SPOTS[s]?s:null;
}
function petZoneName(z){if(!z)return 'the house';if(PET_SPOTS[z])return PET_SPOTS[z].name;const r=z.split('_')[1];return 'the front of the '+(r==='living'?'living room':r)}
function petMemPositive(b,zone,dv){
  if(!zone||!(dv>0.05))return;const W=b.mem.w;if(W[zone]==null)return;
  const g=PET_BRAIN.MEM_ALPHA*Math.min(1,dv/0.15);W[zone]+=g*(1-W[zone]);
  let s=0;for(const k in W)s+=W[k];for(const k in W)W[k]=petR3(W[k]/s);           /* a distribution, so one corner cannot take over forever */
}
function petMemEpisode(b,kind,zone,sal){
  const ep=b.mem.ep;ep.push([Math.floor(Date.now()/60000),kind,zone||'',Math.round(petBC(sal,0,1)*100)]);
  while(ep.length>PET_BRAIN.MEM_EPISODES)ep.shift();
}
function petMemCore(b,kind,zone,extra){
  if(b.mem.fx[kind])return false;b.mem.fx[kind]=1;
  b.mem.core.push([Math.floor(Date.now()/60000),kind,zone||'',extra||'']);while(b.mem.core.length>PET_BRAIN.MEM_CORE)b.mem.core.shift();
  return true;
}
function petComfortZone(p,b){
  const W=b.mem.w;let best=null,bw=-1;
  for(const z in W){if(PET_SPOTS[z]&&!petSpotOK(p,z))continue;if(/^front_/.test(z)&&petDepthMaxZ(p)<72)continue;if(W[z]>bw){bw=W[z];best=z}}
  return best;
}
function petZonePlan(rt,zone,r){
  const c=rt.c,p=petData();
  if(PET_SPOTS[zone])return petSpotPlan(rt,zone,r);
  const room=zone.split('_')[1],xr=room==='living'?[-140,-30]:room==='kitchen'?[214,330]:[44,170],x=xr[0]+r()*(xr[1]-xr[0]);
  return petDepthSetLastWalkZ(petRoute(c,'floor',x),petDepthAvoidProps(x,Math.min(petDepthMaxZ(p),74+r()*12)));
}
/* ---------- System B: the player's rhythm (24 hourly bins) ---------- */
function petRoutineVisit(b){
  const H=b.rt.h,hr=Math.floor(petHour())%24;
  for(let i=0;i<24;i++)H[i]=Math.round((i===hr?Math.min(1,H[i]+PET_BRAIN.ROUTINE_BUMP):H[i]*PET_BRAIN.ROUTINE_DECAY)*100)/100;
}
function petRoutinePeaks(b){const o=[];b.rt.h.forEach(function(v,i){if(v>=PET_BRAIN.VIGIL_H)o.push(i)});return o}
/* he expected you at a peak hour 1-4 h ago, and you did not come */
function petRoutineMissed(b,now){
  const H=b.rt.h,last=b.rt.last||0;
  for(let back=1;back<=4;back++){const t=petNow()-back*36e5,d=new Date(t);d.setMinutes(0,0,0);const hr=d.getHours();
    if(H[hr]>=PET_BRAIN.MISSED_H&&last<d.getTime()&&last>0)return hr}
  return -1;
}
/* ---------- System F: quirks that grow from how you live together ---------- */
function petQuirkCount(b,key,n){
  b.qk.c[key]=(b.qk.c[key]||0)+(n||1);
  for(const q of PET_QUIRK_ACQ){if(q[1]!==key||b.qk.acq.indexOf(q[0])>=0||b.qk.acq.length>=3)continue;
    if(b.qk.c[key]>=q[2]){b.qk.acq.push(q[0]);petMemCore(b,'quirk:'+q[0],'',q[0]);const p=petData();
      setTimeout(function(){toast(p.name+' has a new habit: '+PET_QUIRKS[q[0]].name.toLowerCase()+'.')},400)}}
}
/* ---------- the event bus: everything that changes how he feels ---------- */
function petBrainEvent(kind,o){
  try{
    const p=petData(),rt=PET_RT;if(!p||!p.alive)return;const b=petBrain(p);o=o||{};
    const br=rt?petBR(rt):null,zone=rt&&rt.c?petZoneOf(rt.c):null,v0=b.af.v;
    if(kind==='feed'){petAffect(b,0.2,0.15);petQuirkCount(b,'k');if(br)br.postMeal=Date.now();petMemEpisode(b,'feed',zone,0.4)}
    else if(kind==='play'){petAffect(b,0.06,0.18);b.zm.e=petR3(Math.max(0,b.zm.e-0.25))}
    else if(kind==='toy'){petAffect(b,0.1,0.2);petQuirkCount(b,'t');b.zm.e=petR3(Math.max(0,b.zm.e-0.25))}
    else if(kind==='toy_meh'){petAffect(b,-0.03,0)}
    else if(kind==='pet_beat'){petAffect(b,0.05,-0.03);const d=petDay();if(b.at.day!==d){b.at.day=d;b.at.dg=0}
      if(b.at.dg<PET_BRAIN.PET_TRUST_DAY_CAP){petAttach(b,'tru',0.004);b.at.dg=petR3(b.at.dg+0.004)}}
    else if(kind==='startle'){petAffect(b,-0.25,0.4)}
    else if(kind==='levelup'){petAffect(b,0.15,0.05)}
    else if(kind==='gift'){petAffect(b,0.1,0.05);petAttach(b,'tru',0.005)}
    else if(kind==='greet'){petAffect(b,0.1,0.1)}
    else if(kind==='blink'){petAffect(b,0.12,-0.05);petAttach(b,'tru',o.small?0.01:0.015);petAttach(b,'sec',0.012);b.gz.n++;petQuirkCount(b,'g');
      if(petMemCore(b,'first_blink',zone)&&rt)petSpawn(rt,'heart_big',rt.c.x,rt.c.y-46*petK(rt.c),0,-10,2)}
    else if(kind==='respect'){petAttach(b,'tru',0.015);petAttach(b,'aut',0.02);petQuirkCount(b,'g');petMemCore(b,'first_respect',zone)}
    else if(kind==='overstim'){petAffect(b,-0.12,0.25);if(o.ignored)petAttach(b,'tru',-0.01)}
    else if(kind==='spam'){petAffect(b,-0.03,0.15);petAttach(b,'aut',-0.01)}
    else if(kind==='auto_done'){petAttach(b,'aut',0.01)}
    else if(kind==='comfort_arrive'){petAffect(b,0.08,-0.05)}
    else if(kind==='auto_cut'){petAttach(b,'aut',-0.006)}
    const dv=b.af.v-v0;if(dv>0.05){petMemPositive(b,zone,dv);if(kind!=='pet_beat'&&kind!=='feed')petMemEpisode(b,kind,zone,dv*4)}
    if(br)br.dirty=true;
  }catch(e){petBrainErr('event '+kind,e)}
}
/* ---------- touch: saturation, boundaries, slow blinks, belly-up, zoomies ---------- */
function petBrainTouch(rt,hit,x,y){
  try{
    const p=petData(),b=petBrain(p),br=petBR(rt),c=rt.c,now=Date.now(),L=petLevel(p);
    br.taps.push(now);while(br.taps.length&&now-br.taps[0]>3000)br.taps.shift();
    if(br.taps.length>=4&&now-(br.spamAt||0)>20000){br.spamAt=now;petBrainEvent('spam');br.gaze=null;
      if(br.auto&&br.auto.k==='approach'&&b.at.tru<0.4){br.auto.cut=1;petInterrupt(rt,petPlanVeer(rt))}}
    if(br.auto&&hit==='room')br.auto.cut=1;
    if(hit==='room'){const it=petBrainItemAt(rt,x,y);if(it>=0){petBrainTakeItem(rt,it);return true}return false}
    if(hit!=='cat')return false;
    c.lock=null;
    if(L===0){petBrainEvent('startle');return false}
    /* a quick second tap cancels a gaze and winds him up a little */
    if(now-(br.lastCatTap||0)<350){br.gaze=null;petAffect(b,0,0.08)}br.lastCatTap=now;
    b.af.s=petR3(Math.min(1.2,b.af.s+PET_BRAIN.SAT_TAP*(PET_TRAIT_BASE[p.trait]||PET_TRAIT_BASE.curious).sat));
    br.respectAt=0;br.dirty=true;
    if(br.frap){const q=c.plan.slice();q.unshift({k:'hop',s:'floor',x:c.x+c.dir*4,hh:10});petInterrupt(rt,q);petBrainVoice(rt,'chirp');return true}
    const s=c.step;
    if(s&&s.k==='act'&&s.a==='sleep'&&s.belly){                       /* the belly trap */
      const feisty=p.trait==='playful'||b.af.a>0.1;
      if(feisty){petInterrupt(rt,[petAct('kick',1.6),petAct('stretch',2.2),petAct('idle_sit',3)]);petAffect(b,0.02,0.3);petSpawn(rt,'exclaim',c.x,c.y-34*petK(c),0,-6,0.8)}
      else{const rest=Object.assign({},s,{d:Math.max(4,s.d-s.t),t:0});petInterrupt(rt,[petAct('roll',1.6,{snd:'purreow'}),rest]);petAffect(b,0.06,-0.02)}
      return true}
    if(s&&s.k==='act'&&s.a==='sleep'&&(s.depth||0)<0.35&&L>=2)petBrainVoice(rt,'trill',{force:1});   /* "mrrp?" from a doze */
    return false;
  }catch(e){petBrainErr('touch',e);return false}
}
/* light sleep (the first stage): ears still swivel, a touch wakes him gently */
function petBrainDozing(rt){const s=rt.c.step;return !!(s&&s.k==='act'&&s.a==='sleep'&&(s.depth==null||s.depth<0.35)&&!s.belly)}
function petBrainRelease(rt,h){
  try{
    const p=petData(),b=petBrain(p),br=petBR(rt),c=rt.c,now=Date.now(),s=b.af.s;
    if(h.woke&&!h.keepSleep){c.plan=[petAct('stretch',2.0),petAct('meow',0.6,{quiet:1}),petAct('slowblink',1.8),petAct('idle_sit',2.4)];rt.awakeUntil=petNow()+4*60000}   /* WAKE_STRETCH_CASCADE, and he stays up a while */
    if(s>=PET_BRAIN.SAT_WARN&&s<=PET_BRAIN.SAT_MAX)br.respectAt=now;          /* you stopped when he asked: award it if you leave him be */
    else if(h.t>=1.1&&s<PET_BRAIN.SAT_WARN&&now-(br.gentleAt||0)>20*60000){br.gentleAt=now;petQuirkCount(b,'g')}
    if(h.t<0.45&&!h.moved&&br.gaze&&now<br.gaze.until){petGazeReturn(rt,false);}
    br.dirty=true;
  }catch(e){petBrainErr('release',e)}
}
/* while a finger rests on him; returns true if he has had enough and left */
function petBrainHold(rt,dt){
  try{
    const p=petData(),b=petBrain(p),br=petBR(rt),h=rt.hold,c=rt.c,L=h.L,T=PET_TRAIT_BASE[p.trait]||PET_TRAIT_BASE.curious,stage=petStage(p);
    b.af.s=Math.min(1.2,b.af.s+PET_BRAIN.SAT_HOLD*dt*T.sat*(1.25-0.5*b.at.tru)*(h.keepSleep?0.6:1));
    const s=b.af.s,now=Date.now();
    if(s>=PET_BRAIN.SAT_WARN&&!br.warnAt)br.warnAt=now;
    if(!h.keepSleep&&L>=2&&stage<3){
      if(b.af.a>0.35&&b.af.v>0.15&&h.t<1.8&&s<PET_BRAIN.SAT_WARN){c.anim='play';b.af.a=Math.max(-1,b.af.a-0.15*dt)}   /* wound up: bats at your finger */
      else if(b.af.a<-0.3&&s<PET_BRAIN.SAT_WARN){c.anim='purr';petPurr(rt,Math.min(1,(L>=4?0.95:0.75)+0.1))}            /* sleepy: leans into it */
    }
    if(s>PET_BRAIN.SAT_MAX&&!h.keepSleep){
      const ignored=br.warnAt&&now-br.warnAt>1500;rt.hold=null;br.retreat=now+6000;petPurr(rt,0);br.warnAt=0;
      petSpawn(rt,'exclaim',c.x,c.y-42*petK(c),0,-6,0.8);
      const x=petClampHouse(c.x+(c.dir>0?-1:1)*petRand(30,50)),q=c.surf!=='floor'?petRoute(c,'floor',x):[];
      q.push({k:'walk',x:x,z:petBC(petRand(25,50),petZMinAt(x),Math.max(petZMinAt(x),petDepthMaxZ(p))),nohes:1});
      q.push({k:'face',dir:x<c.x?1:-1});q.push(petAct('groom',3,{flank:1}));q.push(petAct('idle_sit',petRand(6,10)));
      petInterrupt(rt,q);petBrainEvent('overstim',{ignored:ignored});return true}
    const beat=L===1?1.6:0.85;
    if(s>=PET_BRAIN.SAT_WARN){h.beats=Math.floor(h.t/beat);petPurr(rt,h.keepSleep?0.2:0.25);return false}   /* ears back: no hearts now */
    const k=Math.floor(h.t/beat);if(k!==br.beat){br.beat=k;if(h.t>0.5)petBrainEvent('pet_beat')}
    return false;
  }catch(e){petBrainErr('hold',e);return false}
}
/* ---------- System C: the slow blink ---------- */
function petGazeCan(rt,p,b){
  const c=rt.c,s=c.step,A=b.af;
  if(rt.hold||rt.follow||!petDepthViewOpen()||petStage(p)>=2)return false;
  if(!s||s.k!=='act'||!/^(idle_sit|loaf|lookup)$/.test(s.a))return false;
  return A.v>0.15&&A.a>=-0.45&&A.a<=0.25;
}
function petGazeOffer(rt){
  const c=rt.c,br=petBR(rt),s=c.step,rest=s&&s.k==='act'?[Object.assign({},s,{t:0,d:Math.max(2,s.d-s.t)})]:[];
  br.gaze={until:Date.now()+1500+PET_BRAIN.BLINK_WINDOW_MS,by:'cat'};
  petInterrupt(rt,[petAct('slowblink',1.5),petAct('lookup',PET_BRAIN.BLINK_WINDOW_MS/1000,{gaze:1})].concat(rest,c.plan||[]));
}
function petGazeReturn(rt,small){
  const c=rt.c,br=petBR(rt);br.gaze=null;const rest=(c.plan||[]).filter(function(q){return !q.gaze});
  petInterrupt(rt,[petAct('slowblink',1.6),petAct('purr',2.6)].concat(rest));
  petSpawn(rt,'heart',c.x+petRand(-4,4),c.y-44*petK(c),0,-8,2.2);const q=rt.parts[rt.parts.length-1];if(q)q.al=0.3;
  petBrainEvent('blink',{small:small});
}
/* you blink at him (the Blink button) */
function petBlinkClick(){
  const rt=PET_RT,p=petData();if(!rt||!rt.ready||!p||!p.alive)return;
  try{
    const b=petBrain(p),br=petBR(rt),now=Date.now();
    br.blinkBtnAt=br.blinkBtnAt.filter(function(t){return now-t<6000});br.blinkBtnAt.push(now);
    if(br.blinkBtnAt.length>=3){petAffect(b,0,0.08);br.gaze=null;toast(p.name+' squints at you. Slow blinks work best one at a time.');return}
    if(br.gaze&&now<br.gaze.until){petGazeReturn(rt,false);return}
    const c=rt.c,s=c.step,asleep=s&&s.k==='act'&&s.a==='sleep';
    if(asleep){toast(p.name+' is asleep.');return}
    if(petGazeCan(rt,p,b)&&Math.random()<0.35+0.6*b.at.tru){
      setTimeout(function(){petWithRt(rt,function(){petGazeReturn(rt,true)})},650+Math.random()*500);return}
    if(s&&s.k==='act'&&!/^(eat|play|kick|bat|roll)$/.test(s.a)){const rest=Object.assign({},s,{t:0,d:Math.max(2,s.d-s.t)});petInterrupt(rt,[petAct('lookup',2.2),rest].concat(c.plan||[]))}
    petAffect(b,0.01,0);br.dirty=true;
  }catch(e){petBrainErr('blink',e)}
}
/* ---------- System D: body language ---------- */
function petSomatic(rt,p,b,br){
  const c=rt.c,s=c.step,A=b.af,a=s&&s.k==='act'?s.a:'',walking=s&&(s.k==='walk'||s.k==='stalk');
  const asleep=a==='sleep'||String(c.anim).indexOf('sleep')===0;
  let ear='forward',tail='neutral',pupil='medium';
  if(A.s>PET_BRAIN.SAT_WARN-0.05||A.v<-0.3||Date.now()<(br.retreat||0))ear='airplane_flat';
  else if(br.stim||br.greeble||(br.hunt&&br.hunt.stage==='lock'))ear='radar_swivel';
  else if(A.a<-0.2||asleep)ear='relaxed_side';
  if(A.s>=PET_BRAIN.SAT_WARN||br.hes||(A.v<-0.2&&A.a>0.1))tail='tip_twitch';
  else if(br.hunt)tail='low_swish';
  else if(asleep||a==='loaf'||a==='knead')tail='tucked_curl';
  else if((br.auto&&br.auto.k==='approach')||a==='rub'||br.greeting)tail='question_mark';
  else if(walking&&A.v>0.2)tail='high_flag';
  if(A.a>0.55||br.frap||(br.hunt&&br.hunt.stage!=='carry')||br.greeble)pupil='saucer_dilated';
  else if(!asleep&&A.a<-0.3&&A.v>0.2){const h=petHour();if(h>=10&&h<16)pupil='slit'}
  const m=petMoodOf(A)[0];
  br.som={ear:ear,tail:tail,pupil:pupil,mood:m};
  const key=ear+tail+pupil+m;if(key===br.somKey||!petPairIsFocus(rt))return;br.somKey=key;   /* v80: the attributes describe the cat you are looking at */
  ['petCanvas','petFullCanvas'].forEach(function(id){const cv=document.getElementById(id),el=cv&&cv.parentElement;if(!el)return;
    el.dataset.ear=ear;el.dataset.tail=tail;el.dataset.pupil=pupil;el.dataset.mood=m});
}
/* the art has no extra frames for this, so the existing frames are re-drawn: dilated pupils (iris -> pupil
   colour), ears pressed down and out, and a tail tip lifted 1-2 px, flicking at 3.5 Hz */
var PET_VAR=typeof WeakMap!=='undefined'?new WeakMap():null;
function petBrainArt(rt){
  const d={earRows:['idle_sit','loaf','lookup','purr','slowblink','meow','idle_sit_w','loaf_w','knead'],tailRows:['idle_sit','loaf','lookup','purr','slowblink','sleep','sleep_w','idle_sit_w','loaf_w','groom','knead','meow'],
    doze:{anim:'loaf',f:3},belly:{anim:'roll',f:[0,1]},flop:{anim:'roll',f:2},pawTuck:{anim:'knead',f:0}};
  return Object.assign(d,(rt.A&&rt.A.brain)||{});
}
function petBrainVariant(rt,sh,row,W,H,anim,coat){
  try{
    const br=rt.br;if(!br||!PET_VAR||!sh)return null;const som=br.som,art=petBrainArt(rt);
    const dil=false;                                          /* v78.1: recolouring a 2-4 px iris read as solid black eyes; pupil state stays in data-pupil only */
    const ear=som.ear==='airplane_flat'&&art.earRows.indexOf(anim)>=0&&petGrowIdx(petData())<0;   /* kitten heads are too small for the ear redraw */
    let tw=0;if(art.tailRows.indexOf(anim)>=0){if(som.tail==='tip_twitch')tw=[0,1,2,1][Math.floor(rt.t*14)%4];else if(Date.now()<br.remUntil)tw=[1,0,2,0][Math.floor(rt.t*10)%4]}
    if(!dil&&!ear&&!tw)return null;
    const key=row+'|'+(dil?'d':'')+(ear?'e':'')+(tw?'t'+tw:'');
    let M=PET_VAR.get(sh);if(!M){M=new Map();PET_VAR.set(sh,M)}
    if(M.has(key))return M.get(key);
    const cv=petMakeVariant(rt,sh,row,W,H,dil,ear,tw,coat);M.set(key,cv);return cv;
  }catch(e){petBrainErr('variant',e);return null}
}
function petMakeVariant(rt,sh,row,W,H,dil,ear,tw,coat){
  const cv=document.createElement('canvas');cv.width=sh.width;cv.height=H;const x=cv.getContext('2d',{willReadFrequently:true});
  x.drawImage(sh,0,row*H,sh.width,H,0,0,sh.width,H);const id=x.getImageData(0,0,cv.width,H),d=id.data,SW=cv.width;
  const at=function(px,py){return (py*SW+px)*4};
  if(dil){const pal=rt.A.pal[coat]||{},iris=pal['160'],pup=pal['150'];
    if(iris&&pup)for(let i=0;i<d.length;i+=4)if(d[i+3]&&d[i]===iris[0]&&d[i+1]===iris[1]&&d[i+2]===iris[2]){d[i]=pup[0];d[i+1]=pup[1];d[i+2]=pup[2]}}
  const cells=Math.floor(SW/W);
  for(let f=0;f<cells;f++){
    const ox=f*W;
    if(ear){
      let top=-1;for(let yy=0;yy<H&&top<0;yy++)for(let xx=0;xx<W;xx++)if(d[at(ox+xx,yy)+3]){top=yy;break}
      if(top>=0&&top+5<H){
        const P=[];let sx=0;for(let yy=top;yy<top+4;yy++)for(let xx=0;xx<W;xx++){const i=at(ox+xx,yy);if(d[i+3]){P.push([xx,yy,d[i],d[i+1],d[i+2],d[i+3]]);sx+=xx}}
        if(P.length){const cx=sx/P.length;P.forEach(function(q){const i=at(ox+q[0],q[1]);d[i+3]=0});
          P.forEach(function(q){const nx=q[0]+(q[0]>cx?1:-1),ny=q[1]+2;if(nx<0||nx>=W||ny>=H)return;const i=at(ox+nx,ny);if(!d[i+3]||q[1]>=top+2){d[i]=q[2];d[i+1]=q[3];d[i+2]=q[4];d[i+3]=q[5]}})}}
    }
    if(tw){
      const y0=Math.max(0,H-14);let x0=-1;for(let xx=0;xx<W&&x0<0;xx++)for(let yy=y0;yy<H;yy++)if(d[at(ox+xx,yy)+3]){x0=xx;break}
      if(x0>=0)for(let xx=x0;xx<Math.min(W,x0+5);xx++){
        const col=[];for(let yy=y0;yy<H;yy++){const i=at(ox+xx,yy);if(d[i+3]){col.push([yy,d[i],d[i+1],d[i+2],d[i+3]]);d[i+3]=0}}
        const sh2=tw*(xx<x0+3?1:0)+(xx<x0+1?tw:0);
        col.forEach(function(q){const ny=Math.max(0,q[0]-sh2),i=at(ox+xx,ny);d[i]=q[1];d[i+1]=q[2];d[i+2]=q[3];d[i+3]=q[4]})}
    }
  }
  x.putImageData(id,0,0);return cv;
}
/* ---------- System D: voice. Recorded clips get a per-cat pitch; a few calls are synthesised ---------- */
function petBrainPitch(kind){try{const p=petData();if(!p||kind==='purr'||kind==='eat')return 1;return petBrain(p).vo.p}catch(e){return 1}}
function petBrainVoice(rt,kind,o){
  try{
    o=o||{};const p=petData();if(!p||!fxOpt().sound)return false;const b=petBrain(p),br=petBR(rt),now=Date.now();
    const gap=kind==='dream'?2000:4000/(b.vo.t+0.2);if(!o.force&&now-br.lastVocal<gap)return false;
    const ac=petSfxCtx(rt);if(!ac||ac.state!=='running')return false;br.lastVocal=now;
    const P=b.vo.p*(1+0.25*Math.max(0,1-petGrowth(p))),t=ac.currentTime+0.02,out=ac.createGain();out.gain.value=PET_MASTER;out.connect(ac.destination);
    const tone=function(t0,f0,f1,dur,peak,type,fm){const os=ac.createOscillator(),g=ac.createGain();os.type=type||'triangle';
      os.frequency.setValueAtTime(f0*P,t0);os.frequency.exponentialRampToValueAtTime(f1*P,t0+dur*0.75);
      if(fm){const m=ac.createOscillator(),mg=ac.createGain();m.frequency.value=fm;mg.gain.value=f0*P*0.12;m.connect(mg);mg.connect(os.frequency);m.start(t0);m.stop(t0+dur);m.onended=function(){m.disconnect();mg.disconnect()}}
      g.gain.setValueAtTime(0.0001,t0);g.gain.linearRampToValueAtTime(peak,t0+0.025);g.gain.exponentialRampToValueAtTime(0.0001,t0+dur);
      os.connect(g);g.connect(out);os.start(t0);os.stop(t0+dur+0.02);os.onended=function(){os.disconnect();g.disconnect()}};
    if(kind==='trill')tone(t,280,520,0.3,0.05,'triangle',28);                                  /* rising "mrrp?" */
    else if(kind==='chirp'){for(let i=0;i<3;i++)tone(t+i*0.15,620,900,0.11,0.04,'sine',0)}     /* triple chirp */
    else if(kind==='chatter'){for(let i=0;i<3;i++)tone(t+i*0.07,1500,1300,0.04,0.025,'square',0)}   /* ek-ek-ek at the window */
    else if(kind==='dream')tone(t,900,1100,0.05,0.008,'sine',0);
    else if(kind==='breath'){                                                              /* the silent meow: just air */
      const n=Math.floor(ac.sampleRate*0.35),buf=ac.createBuffer(1,n,ac.sampleRate),ch=buf.getChannelData(0);for(let i=0;i<n;i++)ch[i]=(Math.random()*2-1)*Math.sin(Math.PI*i/n);
      const src=ac.createBufferSource(),bp=ac.createBiquadFilter(),g=ac.createGain();src.buffer=buf;bp.type='bandpass';bp.frequency.value=1300*P;bp.Q.value=0.8;g.gain.value=0.06;
      src.connect(bp);bp.connect(g);g.connect(out);src.start(t);src.onended=function(){src.disconnect();bp.disconnect();g.disconnect()}}
    setTimeout(function(){try{out.disconnect()}catch(e){}},1500);
    return true;
  }catch(e){petBrainErr('voice',e);return false}
}
/* ---------- Layer 3: the utility arbiter. petThink offers options, the brain weighs them ---------- */
var PET_OPT_CAT={play:'play',roll:'play',kick:'play',bat:'play',zoomies:'play',favtoy:'play',hunt:'play',shadow:'play',
  idle:'rest',loaf:'rest',groom:'rest',shift:'rest',blink:'rest',
  approach:'social',cushion:'social',rub:'social',gift:'social',
  solo:'solo',tree:'solo',
  explore:'expl',investigate:'expl',patrol:'expl',window:'expl',wander:'expl',pouf:'expl',favspot:'expl'};
function petNoise(p,name){                                    /* smooth, seeded, +-0.06: day-to-day variation, never jitter */
  const u=Date.now()/600000,i=Math.floor(u),f=u-i,a=petRng(petHash(p.born+'|'+name+'|'+i))(),b=petRng(petHash(p.born+'|'+name+'|'+(i+1)))();
  return ((a+(b-a)*(f*f*(3-2*f)))-0.5)*0.12;
}
function petBrainArbiter(rt,O,add,o){
  try{
    if(o.stage>=3)return;const blk=o.blk;const p=petData(),b=petBrain(p),br=petBR(rt),A=b.af,T=b.at,c=rt.c,r=o.r,R=o.R;
    br.waking=!!rt.wasSleeping&&blk.k!=='sleep'&&blk.k!=='nap';
    if(blk.k==='sleep'||blk.k==='nap')return;
    const lap=petHasQ(b,'lap_barnacle');
    const M={play:petBC((A.a+0.15)/0.4,0.15,1.8)*(A.v>PET_BRAIN.PLAY_V?1:0.35),rest:1+Math.max(0,-A.a)*1.2,
      social:(0.4+2*T.tru*T.sec+0.3*T.fam)*(1+Math.max(0,A.v))*(A.v<-0.2?0.5:1)*(lap?1.6:1),
      solo:(0.6+T.aut)*(1+Math.max(0,-A.v)),expl:1+0.6*Math.max(0,A.a)};
    const lk=petLikes(p),W=b.mem.w;
    for(const it of O){const cat=PET_OPT_CAT[it[0]];let m=cat?M[cat]:1;
      if(it[0]==='favspot'&&lk.favSpot)m*=1+3*(W[lk.favSpot]||0);
      if(it[0]==='zoomies')m*=0.4+1.6*b.zm.e;
      it[1]*=m*Math.exp(petNoise(p,it[0]))}
    const floorish=c.surf==='floor'||c.surf==='couch'||c.surf==='bed'||petIsDepthSurf(c.surf);
    if(A.v<0)add('comfort',1+5*(-A.v),function(){return petPlanComfort(rt,r)});
    if(petHasQ(b,'dramatic_flopper')&&A.v>0.6&&floorish)add('flop',1.25,function(){return petPlanFlop(rt,R)});
    if(petHasQ(b,'greeble_spotter'))add('greeble',blk.k==='active'?0.6:0.3,function(){return petPlanGreeble(rt,R)});
    if(blk.k==='active'){
      if(A.a>0.15&&Date.now()-(rt.lastTapAt||0)>PET_BRAIN.HUNT_IDLE_MS&&!br.stim&&!br.hunt)add('hunt',1.4*M.play*o.play,function(){return petPlanHunt(rt,r)});
      if(petHasQ(b,'shadow_chaser'))add('shadow',0.75*M.play,function(){return petPlanShadow(rt,R)});
      if(petHasQ(b,'gravity_tester')&&(rt.ball.on||b.items.length))add('gravity',1.1,function(){return petPlanGravity(rt,R)});
    }
  }catch(e){petBrainErr('arbiter',e)}
}
function petBrainPicked(rt,pick,O){
  try{
    const p=petData(),b=petBrain(p),br=petBR(rt),now=Date.now();if(!pick)return pick;
    if(O.length>=2){const S=O.slice().sort(function(a,c){return c[1]-a[1]});const w1=S[0][1],w2=S[1][1];
      if((pick===S[0]||pick===S[1])&&w1>0&&(w1-w2)/w1<PET_BRAIN.CONFLICT){const alt=pick===S[0]?S[1]:S[0];br.conflict={at:now,alt:alt[2],name:alt[0]}}}
    if(pick[0]==='zoomies')b.zm.e=0;
    if(petStage(p)>=2)return pick;
    const h=petHour(),dusk=(h>=5.5&&h<8.5)||(h>=18.5&&h<22),cool=now-(b.zm.at||0)>PET_BRAIN.FRAP_COOLDOWN_MS;
    const meal=br.postMeal&&now-br.postMeal<120000&&petHasQ(b,'post_meal_zoomer');
    if(meal){br.postMeal=0;b.af.a=petBC(b.af.a+0.4,-1,1);if(now-(b.zm.at||0)>20*60000&&Math.random()<0.6)return ['frap',1,function(){return petPlanFrap(rt)}]}
    if(br.waking&&cool&&b.zm.e>PET_BRAIN.FRAP_ENERGY&&dusk&&Math.random()<PET_BRAIN.FRAP_P)return ['frap',1,function(){return petPlanFrap(rt)}];
    return pick;
  }catch(e){petBrainErr('picked',e);return pick}
}
/* ---------- plans (lists of steps, the same format petThink uses) ---------- */
function petBrainPause(){return petAct('idle_sit',petRand(14,30))}
function petPlanComfort(rt,r){
  const p=petData(),b=petBrain(p),z=petComfortZone(p,b)||'bed',q=petZonePlan(rt,z,r);
  if((b.mem.w[z]||0)>PET_BRAIN.KNEAD_W)q.push(petAct('knead',2.5));
  q.push(petAct('loaf',petRand(40,90)));q.push({k:'fn',f:function(){petBrainEvent('comfort_arrive')}});q.push(petBrainPause());return q;
}
function petPlanVeer(rt){
  const c=rt.c,p=petData(),x=petClampHouse(c.x+petRand(-60,60)),q=c.surf!=='floor'?petRoute(c,'floor',x):[];
  q.push({k:'walk',x:x,z:petBC(petRand(38,55),petZMinAt(x),Math.max(petZMinAt(x),petDepthMaxZ(p))),nohes:1});
  q.push(petAct('groom',petRand(3,5)));q.push({k:'fn',f:function(){if(rt.br)rt.br.auto=null}});q.push(petBrainPause());return q;
}
function petPlanApproach(rt,variant){
  const c=rt.c,p=petData(),b=petBrain(p),br=petBR(rt),zmax=petDepthMaxZ(p),cx=rt.cam?rt.cam.cx:c.x,x=petClampHouse(cx+petRand(-24,24));
  const start={k:'fn',f:function(){br.auto={k:'approach',v:variant,at:Date.now(),cut:0};b.sp.at=Date.now();br.drive=0;br.dirty=true}};
  const end={k:'fn',f:function(){const a=br.auto;br.auto=null;if(a&&!a.cut){petBrainEvent('auto_done');petMemEpisode(b,'glass',petZoneOf(c),0.5);if(petDepthViewOpen())petMemCore(b,'first_glass',petZoneOf(c))}}};
  let q;
  if(variant==='sniff'&&petDepthCanLens(rt)){
    q=petLensPlan(rt);const i=q.findIndex(function(s){return s.k==='act'&&s.a==='sniff'});
    if(i>=0&&petHasQ(b,'polite_paw_tap'))q.splice(i+1,0,petAct('play',1.0));
    q.unshift(start);const j=q.findIndex(function(s,k){return k>i&&s.k==='walk'});q.splice(j>0?j:q.length,0,end);return q}
  q=c.surf!=='floor'?petRoute(c,'floor',x):[];q.unshift(start);
  q.push({k:'walk',x:x,z:Math.min(zmax,variant==='loaf'?92:96),nohes:1});
  if(variant==='bunt'){q.push(petAct('rub',3.2,{snd:'purreow'}));if(b.at.tru>0.75&&Math.random()<0.4)q.push(petAct('meow',0.7,{silent:1}));q.push(petAct('lookup',2.5))}
  else if(variant==='loaf'){q.push(petAct('lookup',1.5));q.push(petAct('loaf',petRand(30,60)))}
  else{q.push(petAct('sniff',1.2));if(petHasQ(b,'polite_paw_tap'))q.push(petAct('play',1.0));q.push(petAct('lookup',3))}
  q.push(end);
  if(variant!=='loaf')q.push({k:'walk',x:petClampHouse(x+(Math.random()<0.5?-1:1)*petRand(24,50)),z:petRand(40,60),nohes:1});
  q.push(petAct('idle_sit',petRand(6,12)));return q;
}
function petPlanParallel(rt){
  const c=rt.c,p=petData(),b=petBrain(p),br=petBR(rt),cx=rt.cam?rt.cam.cx:c.x,side=Math.random()<0.5?-1:1,x=petClampHouse(cx+side*petRand(46,70));
  const q=c.surf!=='floor'?petRoute(c,'floor',x):[];
  q.push({k:'walk',x:x,z:Math.min(petDepthMaxZ(p),petHasQ(b,'lap_barnacle')?92:88),nohes:1});q.push({k:'face',dir:x<cx?1:-1});
  q.push({k:'fn',f:function(){br.co={at:Date.now()};if(petMemCore(b,'first_co',petZoneOf(c)))br.dirty=true}});
  q.push(petAct('loaf',petRand(120,300),{par:1}));
  q.push({k:'fn',f:function(){br.co=null}});q.push(petAct('stretch',3.2));q.push(petBrainPause());return q;
}
function petHuntKind(){const h=petHour();return h>=8&&h<17?'glint':h>=20||h<6?'moth':'mote'}
function petPlanHunt(rt,r){
  const c=rt.c,p=petData(),b=petBrain(p),br=petBR(rt),zmax=petDepthMaxZ(p);
  let dir=r()<0.5?-1:1;let sx=petClampHouse(c.x+dir*(50+r()*40));if(Math.abs(sx-c.x)<30){dir=-dir;sx=petClampHouse(c.x+dir*(50+r()*40))}
  const sz=petBC(28+r()*30,petZMinAt(sx),Math.max(petZMinAt(sx),Math.min(zmax,62))),side=sx>=c.x?1:-1,kx=petClampHouse(sx-side*28);
  const q=c.surf!=='floor'?petRoute(c,'floor',kx):[];
  q.unshift({k:'fn',f:function(){br.stim={k:petHuntKind(),x0:sx,x:sx,z:sz,t:0};br.hunt={stage:'stalk',at:Date.now()}}});
  q.push({k:'walk',x:kx,z:sz,nohes:1});q.push({k:'face',dir:side});
  q.push({k:'fn',f:function(){if(br.hunt)br.hunt.stage='lock'}});q.push(petAct('crouch',1.5));
  q.push({k:'fn',f:function(){if(br.hunt)br.hunt.stage='wiggle'}});q.push(petAct('crouch',0.8,{wiggle:1}));
  q.push({k:'fn',f:function(){if(br.hunt)br.hunt.stage='pounce'}});q.push({k:'hop',s:'floor',x:petClampHouse(sx-side*6),hh:14});
  q.push({k:'fn',f:function(){
    const s0=br.stim;br.stim=null;if(s0)for(let i=0;i<4;i++)petSpawn(rt,'sparkle',s0.x+petRand(-4,4),petFloorY(s0.z)-6,petRand(-10,10),petRand(-20,-8),0.8);
    petBrainEvent('play');
    const lk=petLikes(p),toy=lk.favToy&&PET_TOYS[lk.favToy].mode==='chase'?lk.favToy:(Math.random()<0.5?'mouse':'ball_a');
    if(b.at.tru>=PET_BRAIN.TROPHY_TRUST&&Math.random()<PET_BRAIN.TROPHY_P&&petStage(p)<2){
      const cx=rt.cam?rt.cam.cx:c.x,tx=petClampHouse(cx+petRand(-20,20)),tz=Math.min(zmax,90);
      c.plan.unshift(
        {k:'fn',f:function(){br.carry=toy;if(br.hunt)br.hunt.stage='carry'}},
        {k:'walk',x:tx,z:tz,nohes:1},
        {k:'fn',f:function(){br.carry=null;b.items.push([toy,Math.round(c.x+c.dir*10),Math.round(c.z*10)/10,1]);while(b.items.length>3)b.items.shift();b.hu.gifts++;
          petPlay(rt,'idle',0.45,{rate:0.82});if(petDepthViewOpen())petMemCore(b,'first_trophy',petZoneOf(c));petMemEpisode(b,'trophy',petZoneOf(c),0.6);br.dirty=true}},
        petAct('lookup',2.5),petAct('slowblink',1.6))}}});
  q.push(petAct('play',1.2));
  q.push({k:'fn',f:function(){br.hunt=null;br.carry=null;petBrainEvent('auto_done')}});q.push(petBrainPause());return q;
}
function petRoomRange(x){const r=petRoomOf(x);return r==='living'?[-146,-24]:r==='kitchen'?[214,338]:[40,180]}
function petPlanFrap(rt){
  const c=rt.c,p=petData(),b=petBrain(p),br=petBR(rt),zmax=petDepthMaxZ(p),xr=petRoomRange(c.x),dir=c.dir||1;
  const q=c.surf!=='floor'?petRoute(c,'floor',petBC(c.x,xr[0],xr[1])):[];
  q.push({k:'fn',f:function(){br.frap=true;if(petDepthViewOpen()&&petMemCore(b,'first_zoomies',petZoneOf(c)))br.dirty=true}});
  q.push(petAct('idle_sit',1.2,{lockF:0}));q.push({k:'fn',f:function(){petBrainVoice(rt,'chirp')}});
  q.push({k:'hop',s:'floor',x:petClampHouse(c.x+dir*5),hh:8});
  for(let i=0;i<3;i++){const x=i%2?xr[0]+petRand(0,18):xr[1]-petRand(0,18),z0=petZMinAt(x),z=i%2?Math.min(Math.max(z0,zmax),80):z0+2;
    q.push({k:'walk',x:x,z:Math.max(z0,z),run:1,nohes:1});if(i<2)q.push(petAct('idle_sit',1.0,{lockF:0,flip:i===0}))}
  q.push({k:'fn',f:function(){br.frap=false;b.zm.e=0;b.zm.at=Date.now();b.af.a=0.1;br.dirty=true}});
  q.push(petAct('groom',2.2));q.push(petAct('idle_sit',petRand(6,12)));return q;
}
function petPlanGreeble(rt,R){
  const c=rt.c,br=petBR(rt),xr=petRoomRange(c.x),d=(c.x-xr[0]<xr[1]-c.x)?-1:1;
  return [{k:'face',dir:d},{k:'fn',f:function(){br.greeble=true}},petAct('idle_sit',4,{lockF:0}),{k:'fn',f:function(){br.greeble=false}},petAct('groom',1.5),petBrainPause()];
}
function petPlanShadow(rt){
  const c=rt.c;return [petAct('crouch',0.6,{wiggle:1}),{k:'hop',s:c.surf==='floor'?'floor':c.surf,x:c.x+(c.dir||1)*6,hh:10},{k:'fn',f:function(){c.dir=-(c.dir||1)}},petAct('play',1.2),petAct('idle_sit',petRand(4,8))];
}
function petPlanFlop(rt,R){const art=petBrainArt(rt);return [petAct('roll',0.8),petAct('roll',R(12,30),{lockF:art.flop.f}),petAct('loaf',R(30,60))]}
function petPlanGravity(rt){
  const c=rt.c,p=petData(),b=petBrain(p);
  if(b.items.length){const it=b.items[Math.floor(Math.random()*b.items.length)],side=it[1]>=c.x?1:-1,q=c.surf!=='floor'?petRoute(c,'floor',it[1]-side*14):[];
    q.push({k:'walk',x:petClampHouse(it[1]-side*14),z:it[2],nohes:1});q.push({k:'face',dir:side});q.push(petAct('lookup',1.2));
    q.push({k:'fn',f:function(){it[1]=Math.round(petClampHouse(it[1]+side*12))}});q.push(petAct('idle_sit',2.5));return q}
  const bl=rt.ball,side=bl.x>=c.x?1:-1,q=petRoute(c,'floor',petClampX(bl.x-side*14));q.push({k:'face',dir:side});q.push(petAct('lookup',1.2));
  q.push({k:'fn',f:function(){if(bl.on){bl.vx=side*30;bl.vy=18;bl.flip=side<0}}});q.push(petAct('idle_sit',2.5));return q;
}
function petPlanMissed(rt){
  const c=rt.c,p=petData(),br=petBR(rt),cx=rt.cam?rt.cam.cx:c.x,x=petClampHouse(cx+petRand(-16,16));
  const q=c.surf!=='floor'?petRoute(c,'floor',x):[];
  q.push({k:'fn',f:function(){br.greeting=true}});q.push({k:'walk',x:x,z:Math.min(petDepthMaxZ(p),94),run:1,nohes:1});
  q.push({k:'fn',f:function(){petBrainVoice(rt,'chirp',{force:1})}});q.push(petAct('lookup',2));
  q.push({k:'fn',f:function(){br.gaze={until:Date.now()+1500+PET_BRAIN.BLINK_WINDOW_MS,by:'cat'}}});q.push(petAct('slowblink',1.5));q.push(petAct('lookup',2.5,{gaze:1}));
  q.push({k:'fn',f:function(){br.greeting=false}});q.push(petAct('idle_sit',3));return q;
}
/* the greeting from 16 passes through here */
function petBrainGreet(rt,q){
  try{
    const p=petData(),b=petBrain(p),br=petBR(rt);petBrainEvent('greet');
    if(petRoutineMissed(b)>=0)return petPlanMissed(rt);
    if(petHasQ(b,'chirp_greeter'))q.splice(1,0,{k:'fn',f:function(){petBrainVoice(rt,'chirp',{force:1})}});
    else if(b.af.v>0.2&&q.length>1)q.splice(1,0,{k:'fn',f:function(){petBrainVoice(rt,'trill')}});
    q.unshift({k:'fn',f:function(){br.greeting=true}});q.push({k:'fn',f:function(){br.greeting=false}});
    return q;
  }catch(e){petBrainErr('greet',e);return q}
}
/* ---------- step hooks (from petStartStep and petUpdate) ---------- */
function petBrainStartStep(rt,s){
  try{
    const c=rt.c,p=petData();if(!p)return;const b=petBrain(p),br=petBR(rt),now=Date.now();
    if(s.k==='act'&&s.a==='meow'&&s.silent)petBrainVoice(rt,'breath',{force:1});
    if(s.k==='act'&&s.a==='eat'&&!s.insp&&petHasQ(b,'hydro_inspector')){c.plan.unshift(Object.assign({},s,{insp:1}));s.a='play';s.d=0.9;s.insp=1;delete s.endBowl;return}
    /* Feature 2: torn between two wishes, he stops partway, licks a shoulder or looks at you, then decides */
    if(s.k==='walk'&&!s.nohes&&!s.detour&&s.z!=null&&c.surf==='floor'&&Math.abs(s.z-c.z)>PET_BRAIN.HES_DZ&&now-br.hesAt>PET_BRAIN.HES_COOLDOWN_MS
      &&!br.frap&&!rt.follow&&!rt.hold&&!s.run&&petStage(p)<2){
      const torn=br.conflict&&now-br.conflict.at<15000;
      if(torn||(b.at.aut>0.6&&Math.random()<0.5)){
        br.hesAt=now;const rest=Object.assign({},s,{nohes:1,t:0});delete rest.limit;
        const mx=c.x+(s.x-c.x)*0.45;s.x=mx;s.z=Math.max(petZMinAt(mx),c.z+(s.z-c.z)*0.45);s.nohes=1;
        const alt=torn?br.conflict.alt:null;br.conflict=null;
        c.plan.unshift({k:'fn',f:function(){br.hes=true}},Math.random()<0.5?petAct('groom',1.2):petAct('lookup',1.5),
          {k:'fn',f:function(){br.hes=false;if(alt&&Math.random()<(0.25+0.3*b.at.aut)){try{const q=alt();c.plan=q}catch(e){}}}},rest);
      }
    }
  }catch(e){petBrainErr('start',e)}
}
function petBrainAct(rt,s,dt){
  try{
    const c=rt.c,p=petData();c.lock=s.lockF!=null?s.lockF:null;
    if(s.a==='sleep'&&petStage(p)<3){
      const b=petBrain(p),br=petBR(rt),art=petBrainArt(rt),now=Date.now();
      if(s.depth==null){s.depth=br.deepStart?0.75:0;br.deepStart=false}
      const deepOK=b.af.a<PET_BRAIN.REM_A&&b.af.v>0;
      s.depth=Math.min(deepOK?1:0.69,s.depth+dt/240*(0.6+0.8*b.at.sec));
      let st;
      if(s.belly){c.anim='roll';c.lock=art.belly.f[Math.floor(rt.t*0.5)%2];st='belly'}
      else if(s.depth<0.35){c.anim=art.doze.anim;c.lock=art.doze.f;st='doze'}
      else if(s.depth<0.7){c.anim='sleep';st='curl'}
      else{st='rem';
        if(petHasQ(b,'paw_tuck_sleeper')){c.anim=art.pawTuck.anim;c.lock=art.pawTuck.f}else c.anim='sleep';
        if(now>br.remNext){br.remUntil=now+600;br.remNext=now+petRand(8000,14000);if(Math.random()<0.3&&petDepthViewOpen())petBrainVoice(rt,'dream')}
        if(!s.bellyChk){s.bellyChk=1;if(b.at.tru>=0.75&&b.af.v>0.4&&Math.random()<0.4&&(c.surf==='floor'||c.surf==='bed'||c.surf==='couch'||petIsDepthSurf(c.surf))){s.belly=1;
          if(petDepthViewOpen()&&petMemCore(b,'first_belly',petZoneOf(c)))br.dirty=true}}}
      const pose=st==='belly'?'belly_up':st==='rem'&&petHasQ(b,'paw_tuck_sleeper')?'paw_tuck':st==='doze'?'loaf':'crescent';
      if(b.sl.st!==st||b.sl.pose!==pose){b.sl.st=st;b.sl.pose=pose;br.dirty=true}
    }
    if(s.par){                                                 /* Feature 13: opens one eye now and then to check you are still there */
      if(s.np==null)s.np=s.t+petRand(25,45);
      if(s.t>=s.np){if(s.t<s.np+1.5){c.anim='lookup';if(!s.pp){s.pp=1;petPurr(rt,0.22);const b=petBrain(p);if(b.at.tru>0.75&&Math.random()<0.25)petBrainVoice(rt,'breath')}}
        else{s.np=s.t+petRand(25,45);s.pp=0;petPurr(rt,0)}}
    }
  }catch(e){petBrainErr('act',e)}
}
/* ---------- System E: life goes on while you are away ---------- */
function petOffActs(id,p,o){
  const sp=o.spot&&PET_SPOTS[o.spot]?PET_SPOTS[o.spot].name:'his bed',pr=/bed|box/.test(o.spot||'bed')?'in ':'on ';
  return ({sleep:'slept '+pr+sp,nap:'napped '+pr+sp,rest:'lounged '+pr+sp,patrol:'patrolled the house',toy:'batted the '+(o.toy?PET_TOYS[o.toy].name.toLowerCase():'toy')+' around the '+(o.room||'house'),
    window:'watched '+(o.night?'the night':'the birds')+' from a window',groom:'had a long wash',water:'had a drink of water',hunt:'stalked '+(o.night?'a moth':'a sunbeam'),greeble:'stared at something only he could see'})[id];
}
function petOfflineCatchup(rt,p,b){
  const off=b.off,now=Date.now(),gap=now-(off.at||now);
  if(gap<PET_BRAIN.OFF_MIN_MS){off.at=now;return null}
  const n=Math.min(PET_BRAIN.OFF_MAX_STEPS,Math.floor(gap/PET_BRAIN.OFF_STEP_MS)),r=petRng(petHash(p.born+'|off|'+off.at)),cache={},lk=petLikes(p);
  const T={playful:1.6,curious:1.2,shy:0.7,lazy:0.6}[p.trait]||1,acts=[];let slept=0,toyMoved=null;
  for(let i=0;i<n;i++){
    const t=off.at+(i+1)*gap/(n+1),d=new Date(t),key=petKeyOf(d),S=cache[key]||(cache[key]=petBuildDay(p,key)),hr=d.getHours()+d.getMinutes()/60;
    const blk=S.blocks.find(function(x){return hr>=x.a&&hr<x.b})||S.blocks[S.blocks.length-1],night=hr>=20||hr<6;
    if(blk.k==='sleep'||blk.k==='nap'){slept+=gap/(n+1)/36e5;acts.push({id:blk.k,spot:blk.spot});continue}
    if(blk.k==='rest'){acts.push({id:'rest',spot:blk.spot});continue}
    const O=[['patrol',2],['toy',1.5*T],['window',1.2],['groom',1],['water',1],['hunt',0.8*T]];if(petHasQ(b,'greeble_spotter'))O.push(['greeble',0.6]);
    let tot=0;O.forEach(function(o){tot+=o[1]});let x=r()*tot,id=O[0][0];for(const o of O){if((x-=o[1])<=0){id=o[0];break}}
    const a={id:id,night:night};
    if(id==='toy'){const toy=lk.favToy&&PET_TOYS[lk.favToy].mode==='chase'?lk.favToy:['mouse','ball_a','ball_b'][Math.floor(r()*3)];
      let tx=PET_HMIN+20+r()*(PET_HMAX-PET_HMIN-40);
      if(petHasQ(b,'toy_hoarder')){const fav=lk.favSpot||'bed';tx={bed:52,couch:-96,cushion:-120,pouf:-34,box:262,lrug:-100,rug:104,tree:132,kitchen:270,counter:300,sill:60,lsill:-96,ksill:318}[fav]||52;tx+=r()*24-12}
      const tz=petBC(30+r()*26,petZMinAt(tx),Math.max(petZMinAt(tx),petDepthMaxZ(p)));toyMoved=[toy,Math.round(tx),Math.round(tz*10)/10,0];a.toy=toy;a.room=({living:'living room',bedroom:'bedroom',kitchen:'kitchen'})[petRoomOf(tx)]}
    acts.push(a);
  }
  if(toyMoved){b.items=b.items.filter(function(it){return it[0]!==toyMoved[0]||it[3]});b.items.push(toyMoved);while(b.items.length>3)b.items.shift()}
  /* the three most telling things, newest last, no repeats */
  const seen={},pick=[];for(let i=acts.length-1;i>=0&&pick.length<3;i--){const a=acts[i],k=a.id==='nap'?'sleep':a.id;if(seen[k])continue;seen[k]=1;pick.unshift(petOffActs(a.id,p,a))}
  if(n>0)off.acts=pick.filter(Boolean);
  b.zm.e=petR3(Math.min(1,b.zm.e+slept*0.06));
  const base=petBrainBase(p,b,rt),f=Math.min(1,gap/36e5);b.af.v=petR3(b.af.v+(base.v-b.af.v)*f);b.af.a=petR3(b.af.a+(base.a-b.af.a)*f);
  b.af.s=petR3(Math.max(0,b.af.s-PET_BRAIN.SAT_DECAY*gap/60000));
  off.at=now;return {gap:gap,n:n,r:r};
}
/* ---------- a visit begins: rhythm, catch-up, vigil, arrival scene ---------- */
function petBrainSession(rt){
  try{
    const p=petData();if(!p||!p.alive||!rt||!rt.ready)return;const b=petBrain(p),br=petBR(rt),c=rt.c,now=Date.now();
    if(br.sessionAt&&now-br.sessionAt<5000)return;br.sessionAt=now;br.viewSince=0;
    const isNew=now-(b.rt.sess||0)>=PET_BRAIN.SESSION_GAP_MS,missed=petRoutineMissed(b),info=petOfflineCatchup(rt,p,b);
    if(info&&info.n>0)br.awayFresh=now;
    const greeted=now-(rt.greetedAt||0)<5000,stage=petStage(p),L=petLevel(p),hr=Math.floor(petHour())%24;
    if(isNew){
      petAttach(b,'fam',0.005);
      if(hr>=22||hr<4){const nk=petKeyOf(new Date(petNow()-(hr<4?864e5:0)));if(b.rt.night!==nk){b.rt.night=nk;petQuirkCount(b,'n')}}
      const vigil=!greeted&&stage<3&&L>=1&&b.rt.h[hr]>=PET_BRAIN.VIGIL_H&&b.at.fam>=PET_BRAIN.VIGIL_FAM;
      petRoutineVisit(b);b.rt.sess=now;b.rt.last=petNow();
      if(vigil){petBrainVigil(rt);br.dirty=true;petBrainPersist(true);return}
      if(!greeted&&stage<3&&L>=1&&missed>=0){rt.awakeUntil=petNow()+4*60000;petInterrupt(rt,petPlanMissed(rt));petBrainPersist(true);return}
    }else b.rt.last=petNow();
    if(!greeted&&info&&info.gap>=15*60000&&stage<2)petBrainArrival(rt,p,b,info);
    br.dirty=true;petBrainPersist(true);
  }catch(e){petBrainErr('session',e)}
}
/* he knows this is when you come, and he is already sitting at the glass */
function petBrainVigil(rt){
  const c=rt.c,p=petData(),b=petBrain(p),br=petBR(rt),xr=petRoomRange(c.x),x=petBC(c.x,xr[0]+10,xr[1]-10);
  c.surf='floor';c.x=x;c.z=petBC(90,petZMinAt(x),Math.max(petZMinAt(x),petDepthMaxZ(p)));c.y=petFloorY(c.z);c.dir=1;c.step=null;c.hop=null;
  if(rt.cam)rt.cam.cx=c.x;rt.awakeUntil=petNow()+4*60000;rt.wasSleeping=false;
  const q=[{k:'fn',f:function(){br.greeting=true}},petAct('lookup',petRand(4,6)),{k:'fn',f:function(){petBrainVoice(rt,petHasQ(b,'chirp_greeter')?'chirp':'trill',{force:1})}},petAct('stretch',2.4)];
  if(petDepthCanLens(rt))q.push({k:'walk',x:x,z:96,nohes:1},petAct('sniff',1.0));
  q.push({k:'fn',f:function(){br.gaze={until:Date.now()+1500+PET_BRAIN.BLINK_WINDOW_MS,by:'cat'}}},petAct('slowblink',1.5),petAct('lookup',2.5,{gaze:1}),{k:'fn',f:function(){br.greeting=false}},petAct('idle_sit',petRand(6,10)));
  c.plan=q;petMemCore(b,'first_vigil',petZoneOf(c));petBrainEvent('greet');
}
/* caught in the act: he was in the middle of something when you opened the door */
function petBrainArrival(rt,p,b,info){
  const c=rt.c,blk=petBlockNow(p),r=info.r,br=petBR(rt);
  if(blk.k==='sleep'||blk.k==='nap'){if(info.gap>=2*36e5)br.deepStart=true;b.off.scene='asleep';return}
  if(r()>=0.55){b.off.scene='resting';return}
  const O=[['mid_groom',3],['waking_stretch',2]];if(/sill$/.test(c.surf))O.push(['window_stare',4]);if(c.surf==='floor')O.push(['playing_with_toy',2.5]);
  let tot=0;O.forEach(function(o){tot+=o[1]});let x=r()*tot,sc=O[0][0];for(const o of O){if((x-=o[1])<=0){sc=o[0];break}}
  let first;
  if(sc==='mid_groom')first=[petAct('groom',petRand(4,7))];
  else if(sc==='waking_stretch')first=[petAct('stretch',3.2)];
  else if(sc==='window_stare'){first=[petAct('lookup',petRand(8,14))];if(petHour()>=7&&petHour()<19&&b.af.a>0)setTimeout(function(){petWithRt(rt,function(){petBrainVoice(rt,'chatter')})},900)}
  else{const lk=petLikes(p),toy=lk.favToy&&PET_TOYS[lk.favToy].mode==='chase'?lk.favToy:'mouse';
    b.items=b.items.filter(function(it){return it[0]!==toy||it[3]});b.items.push([toy,Math.round(petClampHouse(c.x+c.dir*16)),Math.round(c.z*10)/10,0]);while(b.items.length>3)b.items.shift();
    first=[petAct('play',2.6),petAct('idle_sit',2)]}
  c.step=null;c.plan=first.concat(c.plan||[]);b.off.scene=sc;
}
/* ---------- things lying on the floor: trophies he brought you, toys he left about ---------- */
function petBrainToyImg(rt,id){
  const m=rt.A.room.meta[id];if(m)return {img:rt.items,sx:m[0],sy:m[1],w:m[2],h:m[3]};
  const im=petPropImg(rt,id);if(!im||!im.img.complete)return null;return {img:im.img,sx:0,sy:0,w:im.w,h:im.h};
}
function petBrainDrawToy(rt,o,id,x,fy,k,flip){
  const t=petBrainToyImg(rt,id);if(!t)return;const q=function(v){return Math.round(v*PET_OS)/PET_OS},w=t.w*k,h=t.h*k;
  if(flip){o.save();o.translate(q(x),0);o.scale(-1,1);o.drawImage(t.img,t.sx,t.sy,t.w,t.h,q(-w/2),q(fy-h+1),q(w),q(h));o.restore()}
  else o.drawImage(t.img,t.sx,t.sy,t.w,t.h,q(x-w/2),q(fy-h+1),q(w),q(h));
}
function petBrainItemAt(rt,x,y){
  const p=petData(),b=petBrain(p);for(let i=b.items.length-1;i>=0;i--){const it=b.items[i],k=petDepthScalePct(it[2])/100,fy=petFloorY(it[2]);
    if(Math.abs(x-it[1])<=9*k&&y>=fy-13*k&&y<=fy+3)return i}return -1;
}
function petBrainTakeItem(rt,i){
  const p=petData(),b=petBrain(p),it=b.items[i];if(!it)return;b.items.splice(i,1);const k=petDepthScalePct(it[2])/100,fy=petFloorY(it[2]);
  for(let j=0;j<4;j++)petSpawn(rt,'sparkle',it[1]+petRand(-6,6),fy-8*k,petRand(-10,10),petRand(-24,-10),1.1);
  if(it[3]){petBrainEvent('gift');petLikesBump(p,'toy',it[0],0.5);toast('You take the '+PET_TOYS[it[0]].name.toLowerCase()+'. '+p.name+' looks very pleased with himself.')}
  else if(petStage(p)<3){rt.dangle=null;petInterrupt(rt,petPounceChain(rt,false,{col:it[0],rounds:1}).concat([{k:'fn',f:function(){petLikesPlayed(it[0],true,0.6)}}]))}
  petBR(rt).dirty=true;petBrainPersist(true);
}
/* called from petDepthScene: adds the floor items, the hunting target and the toy in his mouth to the depth-sorted list */
function petBrainScene(rt,o,at,cz){
  try{
    const p=petData(),b=petBrain(p),br=petBR(rt),c=rt.c;
    b.items.forEach(function(it){at(it[2]-0.05,function(){petBrainDrawToy(rt,o,it[0],it[1],petFloorY(it[2]),petDepthScalePct(it[2])/100,false)})});
    const s=br.stim;if(s)at(s.z-0.05,function(){
      const k=petDepthScalePct(s.z)/100,fy=petFloorY(s.z),tw=0.5+0.5*Math.sin(s.t*9);
      if(s.k==='glint'){o.fillStyle='rgba(255,236,170,'+(0.28+0.3*tw).toFixed(2)+')';o.fillRect(Math.round(s.x-3*k),Math.round(fy-1),Math.max(1,Math.round(6*k)),Math.max(1,Math.round(2*k)));
        o.fillStyle='rgba(255,255,235,'+(0.5+0.4*tw).toFixed(2)+')';o.fillRect(Math.round(s.x),Math.round(fy-1),1,1)}
      else{const ey=(s.k==='moth'?22:16)*k+Math.sin(s.t*(s.k==='moth'?11:1.3))*3*k;o.fillStyle=s.k==='moth'?'rgba(226,222,236,0.95)':'rgba(255,252,226,'+(0.55+0.4*tw).toFixed(2)+')';
        const w=s.k==='moth'?(Math.floor(s.t*12)%2?2:1):1;o.fillRect(Math.round(s.x-w/2),Math.round(fy-ey),w,1)}});
    if(br.carry&&p.alive)at(cz+0.005,function(){const k=petK(c),front=c.anim==='walk_front',mx=front?c.x:c.x+c.dir*21*k,my=c.y-(front?9:14)*k;petBrainDrawToy(rt,o,br.carry,mx,my,k*0.85,c.dir<0)});
  }catch(e){petBrainErr('scene',e)}
}
/* ---------- the clock: once a frame (cheap), and once a second (the thinking) ---------- */
function petBrainFrame(rt,dt){
  try{
    const p=petData();if(!p||!p.alive||!rt.c)return;const b=petBrain(p),br=petBR(rt);
    if(br.stim){const s=br.stim;s.t+=dt;s.x=s.x0+Math.sin(s.t*0.7)*3;if(s.t>30&&!br.hunt)br.stim=null}
    petSomatic(rt,p,b,br);
    if(rt.t-(br.gfxChk||0)>2){br.gfxChk=rt.t;if(petSpritesLost(rt))petRebuildSprites(rt)}
    br.acc+=dt;if(br.acc>=PET_BRAIN.TICK_MS/1000){const sec=Math.min(5,br.acc);br.acc=0;petBrainTick(rt,p,b,br,sec)}
  }catch(e){petBrainErr('frame',e)}
}
function petBrainTick(rt,p,b,br,sec){
  const c=rt.c,now=Date.now(),A=b.af,s=c.step,view=petDepthViewOpen(),stage=petStage(p);
  /* drift toward where he would naturally be right now */
  const base=petBrainBase(p,b,rt);
  A.v=petR3(A.v+(base.v-A.v)*(1-Math.exp(-PET_BRAIN.LAMBDA_V*sec/60)));
  A.a=petR3(A.a+(base.a-A.a)*(1-Math.exp(-PET_BRAIN.LAMBDA_A*sec/60)));
  if(!rt.hold)A.s=petR3(Math.max(0,A.s-PET_BRAIN.SAT_DECAY*sec/60));
  if(A.s<PET_BRAIN.SAT_WARN-0.1)br.warnAt=0;
  A.t=now;b.off.at=now;                                       /* the offline clock starts from the last moment he was watched */
  if(rt.inLens&&now-(rt.lensAt||0)>45000)rt.inLens=false;      /* a glass visit that was interrupted part-way must not block later visits */
  const asleep=!!(s&&s.k==='act'&&s.a==='sleep'),still=asleep||!!(s&&s.k==='act'&&/^(loaf|idle_sit|lookup)$/.test(s.a));
  if(still)b.zm.e=petR3(Math.min(1,b.zm.e+PET_BRAIN.ENERGY_RATE*sec/60*(asleep?1:0.5)));
  if(view){const tgt=(A.v+1)/2;b.at.sec=petR3(petBC(b.at.sec+(tgt-b.at.sec)*(1-Math.exp(-0.03*sec/60)),0.15,1))}
  /* stopped when he asked, and left him be: that is how trust is built */
  if(br.respectAt&&!rt.hold&&now-br.respectAt>=PET_BRAIN.RESPECT_WAIT_MS){br.respectAt=0;petBrainEvent('respect')}
  /* slow blink offer */
  if(br.gaze&&now>br.gaze.until)br.gaze=null;
  if(!br.gaze&&view&&now-br.gazeCheck>=PET_BRAIN.BLINK_EVERY_MS){br.gazeCheck=now;
    if(c.z>=PET_BRAIN.BLINK_MIN_Z&&!br.auto&&!br.co&&petGazeCan(rt,p,b)&&Math.random()<PET_BRAIN.BLINK_P*b.at.tru)petGazeOffer(rt)}
  /* Feature 5: left alone with the room open, he comes to you on his own */
  if(!view){br.viewSince=0;br.drive=0}else{if(!br.viewSince)br.viewSince=now;
    const idle=now-Math.max(br.viewSince,rt.lastTapAt||0),busy=s&&(s.k==='hop'||(s.k==='act'&&/^(eat|play|kick|bat|roll|scratch|scratch_in|scratch_out|meow|crouch)$/.test(s.a)));
    const free=!br.auto&&!br.co&&!br.frap&&!br.hunt&&!rt.inLens&&!rt.hold&&!rt.follow&&!rt.dangle&&!asleep&&!busy&&stage<2&&petLevel(p)>=1;
    if(free&&idle>=PET_BRAIN.APPROACH_IDLE_MS){
      br.drive=Math.min(1,br.drive+PET_BRAIN.APPROACH_RATE*(b.at.tru+b.at.sec)*(petHasQ(b,'lap_barnacle')?1.3:1)*sec);
      if(now-b.sp.at>=PET_BRAIN.APPROACH_COOLDOWN_MS&&br.drive+petNoise(p,'approach')>PET_BRAIN.APPROACH_FIRE){
        const v=A.a>0.2?'sniff':A.v>0.4?'bunt':A.a<0?'loaf':(b.at.tru>0.6?'bunt':'sniff');petInterrupt(rt,petPlanApproach(rt,v));br.drive=0}}
    /* Feature 13: quiet company */
    const open=now-br.viewSince;
    if(!br.co&&free&&open>=PET_BRAIN.PAR_OPEN_MS&&now-(rt.lastTapAt||0)>=PET_BRAIN.PAR_IDLE_MS&&now-br.coTry>60000&&b.at.sec>=(petHasQ(b,'lap_barnacle')?0.3:PET_BRAIN.PAR_SEC)&&A.a<PET_BRAIN.PAR_A&&s&&s.k==='act'){
      br.coTry=now;if(Math.random()<0.5)petInterrupt(rt,petPlanParallel(rt))}
  }
  if(br.co){if(!(s&&s.k==='act'&&s.par)&&!(s&&s.k==='walk')&&!(s&&s.k==='fn')&&!(s&&s.k==='face'))br.co=null;
    else if(s&&s.par){A.v=petR3(Math.min(1,A.v+0.03*sec/60));petAttach(b,'sec',0.004*sec/60);petAttach(b,'aut',0.004*sec/60);b.co.min=Math.round((b.co.min+sec/60)*100)/100;
      if(A.a>0.45){br.co=null;petInterrupt(rt,[petAct('stretch',3.2),petBrainPause()])}}}
  /* ek-ek-ek at the window */
  if(view&&s&&s.k==='act'&&s.a==='lookup'&&/sill$/.test(c.surf)&&A.a>0&&now-br.chatterAt>25000){br.chatterAt=now;const h=petHour();if(h>=7&&h<19&&Math.random()<0.35)petBrainVoice(rt,'chatter')}
  if(br.auto&&now-br.auto.at>120000)br.auto=null;
  if(br.hunt&&now-br.hunt.at>90000){br.hunt=null;br.stim=null;br.carry=null}
  if(br.frap&&!(s&&(s.run||s.k==='hop'||(s.k==='act'&&s.lockF===0))))br.frap=false;
  b.at.style=petBondStyle(p,b).name;
  br.dirty=true;petBrainPersist(false);
  if(state.devMode&&petPairIsFocus(rt)){const el=document.getElementById('petDevBrain');if(el)el.textContent=petBrainReadout(rt,p,b,br)}
}
function petBrainReadout(rt,p,b,br){
  const A=b.af,T=b.at,f=function(v){return (v>=0?'+':'')+v.toFixed(2)};
  return 'V '+f(A.v)+' · A '+f(A.a)+' · sat '+A.s.toFixed(2)+' · '+petMoodOf(A)[0]+' | fam '+T.fam.toFixed(2)+' tru '+T.tru.toFixed(2)+' sec '+T.sec.toFixed(2)+' aut '+T.aut.toFixed(2)+' | '+br.som.ear+'/'+br.som.tail+'/'+br.som.pupil
    +' | drive '+br.drive.toFixed(2)+' · energy '+b.zm.e.toFixed(2)+' · sleep '+b.sl.st+(br.auto?' · approach:'+br.auto.v:'')+(br.co?' · companion':'')+(br.hunt?' · hunt:'+br.hunt.stage:'')+(br.frap?' · ZOOMIES':'')+(br.gaze?' · blink window':'')
    +' | quirks '+b.qk.inn.concat(b.qk.acq).join(', ')+' | save '+JSON.stringify(b).length+' B';
}
/* ---------- the Cat screen: who he is becoming ---------- */
function petAgoShort(min){const d=Date.now()/60000-min;if(d<60)return 'just now';if(d<24*60)return 'today';if(d<48*60)return 'yesterday';return Math.round(d/1440)+' days ago'}
function petBrainRender(force){
  try{
    const p=petData(),rt=PET_RT;if(!p||!p.alive)return;const b=petBrain(p),now=Date.now();
    const el=document.getElementById('petMind');
    if(el&&(force||now-(+el.dataset.t||0)>1000)){el.dataset.t=now;
      const st=petBondStyle(p,b),md=petMoodOf(b.af),hungry=petStage(p)>=2;
      let h='<div class="pet-mind-row"><span class="pet-style" data-style="'+st.id+'">'+esc(st.name)+'</span><span class="pet-mood-chip" data-m="'+md[0]+'">'+md[0]+'</span></div>';
      h+='<p class="pet-mind-text">'+(hungry?'':esc(p.name)+' '+md[1]+'. ')+esc(st.text)+'</p>';
      const br=rt&&rt.br;if(br&&br.awayFresh&&now-br.awayFresh<15*60000&&b.off.acts&&b.off.acts.length){
        const L=b.off.acts;h+='<p class="pet-away"><b>While you were away</b> '+esc(p.name)+' '+esc(L.length>1?L.slice(0,-1).join(', ')+' and '+L[L.length-1]:L[0])+'.</p>'}
      if(el.innerHTML!==h)el.innerHTML=h}
    const inn=document.getElementById('petInner');
    if(inn&&(force||now-(+inn.dataset.t||0)>15000)){inn.dataset.t=now;
      const A=b.at,row=function(lab,k){return '<div class="pet-thread"><span>'+lab+'</span><i><em style="width:'+Math.round(A[k]*100)+'%"></em></i><small>'+petThreadWord(k,A[k])+'</small></div>'};
      let h='<p><b>'+esc(p.name)+'\u2019s inner world.</b> How he feels about you is more than one number. Respecting his signals (ears back, a twitching tail) builds trust. Letting him come to you builds confidence.</p>';
      h+=row('Familiarity','fam')+row('Trust','tru')+row('Safety','sec')+row('Confidence','aut');
      const Q=b.qk.inn.map(function(q){return [q,'Born with it']}).concat(b.qk.acq.map(function(q){return [q,'Learned with you']}));
      h+='<div class="pet-likes-h">Quirks</div>'+Q.map(function(q){const d=PET_QUIRKS[q[0]];return '<div class="pet-quirk"><b>'+d.name+'</b> <small>'+q[1]+'</small><span>He '+d.text+'.</span></div>'}).join('');
      const C=b.mem.core.slice().reverse();
      if(C.length)h+='<div class="pet-likes-h">Moments he remembers</div>'+C.map(function(m){const t=m[1].indexOf('quirk:')===0?'Picked up a habit: '+PET_QUIRKS[m[3]||m[1].slice(6)].name.toLowerCase():PET_CORE_TEXT[m[1]]||m[1];
        return '<div class="pet-memory"><span>'+esc(t)+(m[2]?' <em>'+esc(petZoneName(m[2]))+'</em>':'')+'</span><small>'+petAgoShort(m[0])+'</small></div>'}).join('');
      const stats=[];if(b.gz.n)stats.push(b.gz.n+' slow blink'+(b.gz.n>1?'s':'')+' shared');if(b.hu.gifts)stats.push(b.hu.gifts+' troph'+(b.hu.gifts>1?'ies':'y')+' brought to you');if(b.co.min>=1)stats.push(Math.round(b.co.min)+' quiet minutes side by side');
      if(stats.length)h+='<p class="muted pet-brain-stats">'+stats.join(' · ')+'</p>';
      const top=petComfortZone(p,b);if(top&&b.mem.w[top]>0.12)h+='<p class="muted pet-brain-stats">When he needs comfort he goes to '+esc(petZoneName(top))+'.</p>';
      inn.innerHTML=h}
  }catch(e){petBrainErr('render',e)}
}
/* ---------- v78.1: natural blinking ----------
   The idle rows of the art loop a blink every 8 frames (about once a second). Cats blink far less,
   so those rows now hold the open-eye frames and run the blink frames every 4-11 s. */
var PET_BLINK={idle_sit:{open:[0,1,2,6,7],blink:[3,4,5]},idle_sit_w:{open:[0,1,6,7],blink:[2,3,4,5]}};
function petBlinkGap(rt){const br=rt.br,A=petData()&&petData().brain&&petData().brain.af;let g=4+Math.random()*7;if(A&&A.a>0.4)g*=1.6;if(br&&(br.greeble||br.hunt))g*=2;return g}
function petBlinkFrame(rt,anim,meta){
  const B=PET_BLINK[anim],c=rt.c,e=rt.eye||(rt.eye={next:0,at:-1}),t=rt.t;
  if(!B||meta.n<8)return Math.floor(c.t*meta.fps)%meta.n;
  if(e.at>=0){const k=Math.floor((t-e.at)*meta.fps);if(k>=0&&k<B.blink.length)return B.blink[k];e.at=-1;e.next=t+petBlinkGap(rt)}
  if(!e.next||e.next-t>30)e.next=t+petBlinkGap(rt);
  if(t>=e.next){e.at=t;return B.blink[0]}
  return B.open[Math.floor(c.t*meta.fps*0.5)%B.open.length];
}
/* ---------- v78.1: sprite sheets that were wiped ----------
   Android Chrome can drop the pixels of GPU-backed canvases when the app goes to the background.
   The room is redrawn every frame so it survives, but the cat's sheets are built once: if they are
   lost, the cat turns invisible. Sheets are now CPU-backed; this check rebuilds them if it happens anyway. */
function petSpritesLost(rt){
  try{const p=petData();if(!p||!rt.sheets)return false;const L=[rt.sheets[p.coat],rt.dsheets&&rt.dsheets[p.coat],rt.gsLast&&rt.gsLast.cv];
    for(const cv of L){if(!cv)continue;const x=cv.getContext('2d',{willReadFrequently:true});if(x&&x.isContextLost&&x.isContextLost())return true}
    const cv=rt.sheets[p.coat];if(cv&&cv.width){const d=cv.getContext('2d',{willReadFrequently:true}).getImageData(0,0,cv.width,rt.A.H).data;for(let i=3;i<d.length;i+=16)if(d[i])return false;return true}
  }catch(e){}return false;
}
async function petTintSheet(src,pal){
  const img=await petLoadImg(src),cv=document.createElement('canvas');cv.width=img.width;cv.height=img.height;const cx=cv.getContext('2d',{willReadFrequently:true});cx.drawImage(img,0,0);
  const id=cx.getImageData(0,0,cv.width,cv.height),d=id.data;for(let i=0;i<d.length;i+=4){const g=d[i];if(g===0){d[i+3]=0;continue}const c=pal[String(g)];if(c){d[i]=c[0];d[i+1]=c[1];d[i+2]=c[2];d[i+3]=255}else d[i+3]=0}
  cx.putImageData(id,0,0);return cv;
}
async function petRebuildSprites(rt){
  if(rt.rebuilding)return;rt.rebuilding=1;
  try{const A=rt.A;for(const coat of Object.keys(A.coats))rt.sheets[coat]=await petTintSheet(A.coats[coat],A.pal[coat]);
    if(A.depth)for(const coat of Object.keys(A.depth.coats))rt.dsheets[coat]=await petTintSheet(A.depth.coats[coat],A.pal[coat]);
    rt.gs={};rt.gsLast=null;rt.dsg={};rt.icon={};if(typeof WeakMap!=='undefined')PET_VAR=new WeakMap();
    const p=petData();if(p&&petGrowIdx(p)>=0)await petLoadStage(rt,petGrowIdx(p));
  }catch(e){petBrainErr('rebuild',e)}finally{rt.rebuilding=0}
}
document.addEventListener('visibilitychange',function(){if(!document.hidden){try{const rt=PET_RT;if(rt&&rt.ready&&petSpritesLost(rt))petRebuildSprites(rt)}catch(e){}}});
/* ---------- developer tools ---------- */
function petBrainDevHtml(){
  return '<div class="pet-devrow"><b style="width:100%;font-size:12px">Brain (v78)</b>'+[['Overstimulate',1],['Offer slow blink',2],['Glass approach',3],['Zoomies',4],['Hunt',5],['Quiet company',6],['Greeble',7],['Deep sleep here',8],['Away 3 h (sim)',9],['Peak hour now',10],['Mood: low',11],['Trust 0.85',12],['Reset brain',13]]
    .map(function(b){return '<button type="button" class="btn secondary small" onclick="petDevBrain('+b[1]+')">'+b[0]+'</button>'}).join('')+'</div><div class="muted" id="petDevBrain" style="font-size:11.5px;margin-top:6px;line-height:1.45"></div>';
}
function petDevBrain(n){
  const rt=PET_RT,p=petData();if(!rt||!rt.ready||!p)return;const b=petBrain(p),br=petBR(rt),c=rt.c;rt.dangle=null;
  if(n===1){b.af.s=0.6;rt.hold={on:true,t:1,since:rt.t,x:c.x,moved:false,beats:0,L:Math.max(2,petLevel(p)),flee:false,keepSleep:false};c.step=null;c.plan=[]}
  else if(n===2){petInterrupt(rt,[petAct('lookup',0.4)]);setTimeout(function(){petGazeOffer(rt)},500)}
  else if(n===3){b.sp.at=0;petInterrupt(rt,petPlanApproach(rt,b.af.a>0.2?'sniff':b.af.v>0.4?'bunt':'loaf'))}
  else if(n===4){b.zm.e=1;petInterrupt(rt,petPlanFrap(rt))}
  else if(n===5){petInterrupt(rt,petPlanHunt(rt,Math.random))}
  else if(n===6){b.at.sec=Math.max(b.at.sec,0.6);petInterrupt(rt,petPlanParallel(rt))}
  else if(n===7){petInterrupt(rt,petPlanGreeble(rt,petRand))}
  else if(n===8){br.deepStart=true;petInterrupt(rt,[petAct('sleep',120)])}
  else if(n===9){b.off.at=Date.now()-3*36e5;const info=petOfflineCatchup(rt,p,b);br.awayFresh=Date.now();if(info)petBrainArrival(rt,p,b,info);toast('Simulated 3 h away: '+(b.off.acts||[]).join('; '))}
  else if(n===10){const hr=Math.floor(petHour())%24;b.rt.h[hr]=0.8;b.at.fam=Math.max(b.at.fam,0.4);b.rt.sess=0;br.sessionAt=0;rt.greetedAt=0;petBrainSession(rt)}
  else if(n===11){b.af.v=-0.5;b.af.a=0.1}
  else if(n===12){b.at.tru=0.85;b.at.sec=Math.max(b.at.sec,0.7)}
  else if(n===13){p.brain=null;rt.br=null;petBrain(p)}
  br.dirty=true;petBrainPersist(true);petBrainRender(true);
}
document.addEventListener('visibilitychange',function(){if(document.hidden){try{const rt=PET_RT;if(rt&&rt.br){const p=petData(),b=petBrain(p);if(b)b.off.at=Date.now();petBrainPersist(true)}}catch(e){}}});
window.addEventListener('pagehide',function(){try{const rt=PET_RT;if(rt&&rt.br)petBrainPersist(true)}catch(e){}});

devChatSeed('claude-pet-brain-v78-2026-10-24','Claude',[
'Pet brain (v78): the feline inner-life engine. Code in src/runtime/segments/26-pet-brain.js, saved in state.pet.brain (about 2 KB). Hooks in 15 (petThink), 16 (steps, touch, hold, feed, greet, sound pitch), 17 (drawing, session start, status, UI), 24 (tick, scene, glass) and 25 (toy plays).',
'• Mood: valence and arousal drift toward a baseline from trait, attachment, hunger, the routine block and dawn/dusk. Petting fills sensory saturation; at 0.65 the ears flatten and the tail tip flicks; past 0.85 he leaves and grooms. Stop during the warning and leave him for 8 s and trust and confidence go up.',
'• Attachment: familiarity, trust, safety, confidence. Seeded from v77 bondPts so nothing is lost. The bond levels, depth tiers and hunger rules are unchanged; the threads drive the new behaviours. Trust from petting alone is capped per day.',
'• Behaviour: the brain re-weights the options petThink already offers and adds comfort-seeking, hunting a glint/mote/moth with a trophy carried to the glass, quirk behaviours and full zoomies. Close-scored choices on a long walk toward or away from you can produce a mid-walk hesitation. The 20 s glass rule in 24 is replaced by an approach drive (sniff, cheek rub or loaf at the glass, 4 min cooldown).',
'• Body language: no new art. Dilated pupils, flattened ears and a flicking tail tip are redrawn from the existing frames at runtime (petMakeVariant); belly-up, doze and paw-tuck poses hold existing frames (petAssets.brain). The stage element also carries data-ear / data-tail / data-pupil / data-mood.',
'• Memory: 16 places (13 likes spots + the front of each room) get emotional weights; comfort-seeking goes to the best one (kneads first if strong). 24 recent episodes, 8 core memories shown under Care and settings.',
'• Visits: a 24-bin histogram of when you open the Cat screen. At a learned hour he waits at the glass; if you miss a usual hour he trots up chirping. Coming back after 5+ min runs up to 12 coarse steps of his day (from the real routine) and can leave a toy elsewhere and a "while you were away" line. Hunger is not touched.'
].join('\\n'));
