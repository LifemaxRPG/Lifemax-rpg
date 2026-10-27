
/* ===== Two cats (v80): a second cat, and what two cats do together =====
   The pet engine was written for one cat: almost everything reads petData() and PET_RT. Instead of
   rewriting it, the second cat has its own saved record (state.pet2) and its own runtime (rt2), and
   any engine call can run "as" that cat: petAs(1, fn) points petData() at state.pet2, PET_RT at rt2
   and the bowl side at the far side of the bowl for the length of fn. So the routine, the brain, likes,
   play and the box all work for both cats unchanged. Heavy or shared things (sprite sheets, the room
   canvas, sound, camera, bowl, claw marks, glass fog, the box's wobble) are forwarded from rt2 to the
   first runtime, so there is one copy of each.
   state.pet is never touched, so a one-cat save loads exactly as before.
   Saved: state.pet2 (same shape as state.pet) and state.petPair (friendship, about 300 B).
   Together: a director (petPairTick) looks for a moment every few seconds and runs one interaction
   as a short list of stages; each stage gives both cats a plan, waits until both have done their
   part, then moves on. Touching either cat, feeding, or anything else that replaces a cat's plan
   ends the interaction quietly.
   - Nose touch: they meet face to face. Early on it can end in a hiss; later in a cheek rub.
   - Chase: stalk, wiggle, pounce, a bolt across the room, a belly-up tussle, sometimes swap.
   - Grooming: one grooms, the other purrs; close friends then nap side by side.
   Plus: they keep a little space between them, and a spot the other cat is on (bed, sill, box,
   cushion...) is not jumped onto; he settles beside it instead.
   Hooks: 15 (petData, PET_EATDIR), 16 (walk and hop start), 24 (scene), 26 (body-language
   attributes, timers). Tuning in PET_PAIR_CFG. */
var PET_PAIR_CFG={
  CHECK_S:6,              /* how often the pair looks for something to do together */
  START_P:0.45,           /* chance a check starts something, if anything fits */
  COOL_S:[40,110],        /* quiet time after an interaction */
  STAGE_MAX_S:26,         /* a stage that takes longer than this is given up */
  REL_START:0.1,DAY_CAP:0.16,
  GAIN:{greet:0.03,hiss:0.015,chase:0.035,groom:0.045,nap:0.03},
  CHASE_REL:0.15,GROOM_REL:0.35,NAP_REL:0.6,HISS_BELOW:0.25,
  GREET_GAP_MIN:20,       /* minutes between ordinary nose touches */
  EAT2:{x:121,dir:-1}     /* the second cat eats from the other side of the bowl */
};
var PET_PAIR={rt1:null,rt2:null,focus:0,I:null,nextAt:0,coolUntil:0,ptr:null,pend:null,adopting:false,arrival:false,seenAt:0,renderAt:0};
var PET_PAIR_CTX1={i:1};
var PET_PAIR_SHARED=['A','bg','items','living','kitchen','sheets','dsheets','gs','gsBusy','dsg','icon','xImg','propImg','off','octx','edge','ectx',
  'ac','sfx','sfxLoading','sfxFailed','sfxLast','cam','par','marks','bowl','fog','boxWob','shift','rebuilding'];
var PET_PAIR_SINGLE={bed:1,sill:1,ksill:1,mid:1,top:1,box:1,pouf:1,cushion:1};   /* room for one cat */
var PET_PAIR_LEVELS=[[0.15,'Strangers','They are still working each other out. A hiss at the first meetings is normal; give them time.'],
  [0.3,'Wary','A polite distance: short nose touches, the odd hiss, a lot of watching.'],
  [0.55,'Getting along','They share the house without fuss and play chase now and then.'],
  [0.8,'Friends','They groom each other and sometimes nap side by side.'],
  [9,'Inseparable','Best friends: chasing, grooming and curling up together.']];

/* ---------- running as one cat or the other ---------- */
function petPairOn(){const P=PET_PAIR;return !!(P&&P.rt2&&P.rt2.ready&&state.pet2&&state.pet2.alive&&state.pet&&state.pet.alive)}
function petAs(i,fn,args,dataOnly){
  const P=PET_PAIR;if(i===1&&!(state.pet2&&(dataOnly||P.rt2)))i=0;
  const pc=PET_CTX,pr=PET_RT,ex=PET_EATX,ed=PET_EATDIR;
  if(i===1){PET_CTX=PET_PAIR_CTX1;if(!dataOnly)PET_RT=P.rt2;PET_EATX=PET_PAIR_CFG.EAT2.x;PET_EATDIR=PET_PAIR_CFG.EAT2.dir}
  else{PET_CTX=null;if(!dataOnly&&P.rt1)PET_RT=P.rt1;PET_EATX=87;PET_EATDIR=1}
  try{return fn.apply(null,args||[])}finally{PET_CTX=pc;PET_RT=pr;PET_EATX=ex;PET_EATDIR=ed}
}
/* a timer set by one cat runs as that cat (and not at all if that runtime is gone) */
function petWithRt(rt,fn){const P=PET_PAIR;if(!rt)return;if(P&&rt===P.rt2&&state.pet2)return petAs(1,fn);if(rt===PET_RT||(P&&rt===P.rt1))return petAs(0,fn)}
function petPairRt(i){return i?PET_PAIR.rt2:PET_PAIR.rt1}
function petPairP(i){return i?state.pet2:state.pet}
function petPairOther(rt){const P=PET_PAIR;if(!P||!P.rt2||!rt)return null;return rt===P.rt1?P.rt2:rt===P.rt2?P.rt1:null}
function petPairIsFocus(rt){const P=PET_PAIR;if(!P||!petPairOn())return true;return rt===(P.focus?P.rt2:P.rt1)}

/* ---------- the friendship record ---------- */
function petPairData(){
  const a=state.pet,b=state.pet2;if(!a||!b)return null;let d=state.petPair;
  if(!d||d.v!==1||d.a!==a.born||d.b!==b.born)d=state.petPair={v:1,a:a.born,b:b.born,rel:PET_PAIR_CFG.REL_START,met:0,day:'',gain:0,
    n:{greet:0,hiss:0,chase:0,groom:0,nap:0},last:null,lastGreet:0,seen:Date.now(),away:null};
  return d;
}
function petPairLevel(rel){for(const L of PET_PAIR_LEVELS)if(rel<L[0])return L;return PET_PAIR_LEVELS[PET_PAIR_LEVELS.length-1]}
function petPairGain(kind,amt,noCount){
  const d=petPairData();if(!d)return;const day=petDay();if(d.day!==day){d.day=day;d.gain=0}
  const g=Math.max(0,Math.min(amt==null?(PET_PAIR_CFG.GAIN[kind]||0.02):amt,PET_PAIR_CFG.DAY_CAP-d.gain));
  const before=petPairLevel(d.rel)[1];d.rel=Math.round(Math.min(1,d.rel+g)*1000)/1000;d.gain+=g;
  if(!noCount)d.n[kind]=(d.n[kind]||0)+1;
  const after=petPairLevel(d.rel)[1];if(after!==before&&!noCount)setTimeout(function(){toast(state.pet.name+' and '+(state.pet2||{}).name+': '+after.toLowerCase()+'.')},600);
}
function petPairNote(kind,who,text){const d=petPairData();if(!d)return;d.last={k:kind,t:text,at:Date.now()}}
/* both cats feel it: brain affect, if the brain is there */
function petPairFeel(i,dv,da){try{petAs(i,function(){const p=petData();if(!p)return;const b=petBrain(p);petAffect(b,dv,da);const rt=PET_RT;if(rt&&rt.br)rt.br.dirty=true})}catch(e){}}

/* ---------- the second runtime ---------- */
function petPairMakeRt(base){
  const rt={ready:true,t:0,parts:[],bubble:null,hold:null,ball:{on:false,x:0,vx:0,t:0},gift:null,peekY:60,greetedAt:0,lastMood:'',pair:1};
  PET_PAIR_SHARED.forEach(function(k){Object.defineProperty(rt,k,{get:function(){return base[k]},set:function(v){base[k]=v},enumerable:false,configurable:true})});
  try{rt.lastSeen=+localStorage.getItem('lm_pet_seen')||0}catch(e){rt.lastSeen=0}
  rt.c={x:92,y:PET_SURF.floor.y,z:PET_DEPTH.ART_Z,dir:1,surf:'floor',anim:'idle_sit',t:0,plan:[],step:null,hop:null};
  return rt;
}
function petPairEnsure(rt){
  const P=PET_PAIR;if(!PET_CTX&&rt&&rt.ready&&rt!==P.rt2)P.rt1=rt;
  const p2=state.pet2;if(!p2||!p2.alive||!state.pet||!state.pet.alive){if(P.rt2)petPairDrop();return}
  if(P.rt2||!P.rt1)return;
  const r2=petPairMakeRt(P.rt1);P.rt2=r2;
  petAs(1,function(){
    const p=petData(),gi=petGrowIdx(p);if(gi>=0)petLoadStage(r2,gi);
    if(P.arrival){                                     /* just adopted: in from the kitchen */
      const c=r2.c,o=P.rt1.c;c.x=332;c.z=40;c.y=petFloorY(40);c.dir=-1;
      const x=petClampHouse((o.surf==='floor'?o.x:120)+60);
      petInterrupt(r2,[{k:'walk',x:x,z:40,pair:1},{k:'face',dir:-1},petAct('idle_sit',2.5),petAct('meow',2.2,{snd:'beg'}),petAct('idle_sit',4)]);
      r2.lastSeen=Date.now();r2.greetedAt=Date.now();
    }else{petPlaceForNow(r2);petMaybeGreetOne(r2);petBrainSession(r2)}
  });
  petPairSession();
  if(P.arrival){P.arrival=false;P.nextAt=Date.now()+9000;setTimeout(function(){renderPetView(true)},0)}
}
function petPairDrop(){const P=PET_PAIR;if(P.rt2){try{petPurr(P.rt2,0)}catch(e){}}P.rt2=null;P.I=null;P.focus=0;P.ptr=null;P.pend=null}

/* ---------- keeping apart ---------- */
function petPairWalkStart(rt,s){
  try{
    const o=petPairOther(rt);if(!o||!o.c||!petPairOn()||s.pair||s.detour||rt.follow)return;
    const c=rt.c,nx=c.plan[0];if(c.surf!=='floor')return;
    if(nx&&(nx.k==='walk'||nx.k==='hop'||nx.k==='stalk'))return;      /* only where he ends up */
    if(Math.abs(s.x-PET_EATX)<0.6)return;                              /* his place at the bowl */
    const oc=o.c,os=oc.step;let ox=oc.x,oz=oc.z;
    if(os&&os.k==='walk'){ox=os.x;if(os.z!=null)oz=os.z}else if(oc.surf!=='floor')return;
    const z=s.z==null?PET_DEPTH.ART_Z:s.z;if(Math.abs(z-oz)>12)return;
    const gap=24*Math.max(petK({z:z}),petK(oc));if(Math.abs(s.x-ox)>=gap)return;
    let side=s.x>=ox?1:-1;if(Math.abs(s.x-ox)<1)side=c.x>=ox?1:-1;
    let x=petClampHouse(ox+side*gap);if(Math.abs(x-ox)<gap-1)x=petClampHouse(ox-side*gap);s.x=x;
  }catch(e){}
}
/* a spot the other cat is already on: he looks over, and settles beside it */
function petPairHopStart(rt,s){
  try{
    const o=petPairOther(rt);if(!o||!o.c||!petPairOn()||s.pair||!PET_PAIR_SINGLE[s.s])return false;
    const c=rt.c;if(c.surf===s.s)return false;
    if(c.plan.some(function(q){return q.k==='hop'&&q.s!=='floor'}))return false;   /* only the last climb */
    const oc=o.c,os=oc.step,onIt=oc.surf===s.s&&!(os&&os.k==='hop'&&os.s!==s.s),going=!!(os&&os.k==='hop'&&os.s===s.s);
    if(!onIt&&!going)return false;
    if(c.surf==='floor')for(let i=c.plan.length-1;i>=0;i--){const q=c.plan[i];if(q.k==='hop'&&q.s==='floor')c.plan.splice(i,1)}
    s.k='act';s.a='lookup';s.d=1.4;s.taken=s.s;c.dir=oc.x>=c.x?1:-1;petStartStep(rt,s);return true;
  }catch(e){return false}
}
/* placed by the routine (arrival, greeting, a long absence): never on top of the other cat */
function petPairUntangle(rt){
  try{
    const o=petPairOther(rt);if(!o||!o.c||!rt.c||!petPairOn())return;const a=rt.c,b=o.c;
    if(a.surf===b.surf&&PET_PAIR_SINGLE[a.surf]){
      const x=petClampHouse(b.x+(a.x<=b.x?-1:1)*30),zm=petZMinAt(x);
      a.surf='floor';a.x=x;a.boxSink=0;a.z=petClampN(Math.max(PET_DEPTH.ART_Z,zm),zm,Math.max(zm,petDepthMaxZ(petPairP(rt===PET_PAIR.rt2?1:0))));a.y=petFloorY(a.z);
    }else if(a.surf===b.surf&&Math.abs(a.x-b.x)<24&&Math.abs((a.z||0)-(b.z||0))<12){
      const sf=PET_SURF[a.surf],lim=function(x){return a.surf!=='floor'&&sf&&sf.x0!=null?Math.max(sf.x0,Math.min(sf.x1,x)):petClampHouse(x)};
      let x=lim(b.x+(a.x<=b.x?-1:1)*26);if(Math.abs(x-b.x)<14)x=lim(b.x+(a.x<=b.x?1:-1)*26);
      if(Math.abs(x-b.x)<14&&a.surf!=='floor'){a.surf='floor';x=petClampHouse(b.x+30);a.z=Math.max(PET_DEPTH.ART_Z,petZMinAt(x))}
      a.x=x;if(a.surf==='floor')a.y=petFloorY(a.z);
    }
  }catch(e){}
}

/* ---------- drawing the second cat into the first cat's scene ---------- */
function petPairScene(rt,o,at){
  const P=PET_PAIR;if(!P||!P.rt2||rt!==P.rt1||!petPairOn())return;
  const r2=P.rt2,c=r2.c,as1=function(f){return function(){petAs(1,f)}},at2=function(z,f){at(z,as1(f))};
  petAs(1,function(){
    if(r2.ball.on)at2(c.anim==='kick'||r2.ballFront?35.2:34.8,function(){petItem(r2,o,r2.ball.col||'ball_a',r2.ball.x-4,PET_SURF.floor.y-petToyH(r2.ball.col)-Math.round(r2.ball.h||0),r2.ball.flip)});
    if(r2.gift)at2(34.9,function(){petItem(r2,o,r2.gift.k,r2.gift.x-6,PET_SURF.floor.y-5)});
    if(r2.dangle)at2(0.5,function(){petDrawDangle(r2,o)});
    const cz=(petBoxIn(r2)?PET_SURF.box.z:c.z)+0.012;
    at2(cz,function(){petBoxDrawCat(r2,o)});
    petBrainScene(r2,o,at2,cz);
  });
}
function petPairSceneTop(rt,o){
  const P=PET_PAIR;if(!P||!P.rt2||rt!==P.rt1||!petPairOn())return;
  const r2=P.rt2,c=r2.c,dc=petParOff(rt,c.z)-petParBg(rt),k=petK(c);if(dc){o.save();o.translate(dc,0)}
  r2.parts.forEach(function(q){const f=1-q.age/q.life;if(f<0.15&&Math.floor(r2.t*14)%2)return;if(q.al)o.globalAlpha=q.al*Math.min(1,f*2);petItem(r2,o,q.s,q.x-4,q.y);if(q.al)o.globalAlpha=1});
  if(r2.bubble&&Math.floor(r2.t*0.7)%3!==2)petItem(r2,o,r2.bubble.s,c.x+(c.dir>0?6:-24)*k,c.y-50*k+Math.round(Math.sin(r2.t*5)*1));
  if(dc)o.restore();
}

/* ---------- the director ---------- */
function petPairFree(i,o){
  const rt=petPairRt(i),p=petPairP(i);if(!rt||!rt.c||!p||!p.alive)return false;o=o||{};
  if(petStage(p)>=(o.hungry?3:2))return false;
  if(rt.hold||rt.follow||rt.inLens||(rt.ball&&rt.ball.on)||rt.dangle)return false;
  if(Date.now()-(rt.lastTapAt||0)<8000)return false;
  if(petBoxIn(rt)||rt.c.surf==='box')return false;
  const s=rt.c.step;
  if(s&&(s.k==='hop'||s.k==='stalk'||s.swat||s.pat||s.hug||s.gaze))return false;
  if(s&&s.k==='act'&&/^(eat|play|kick|bat|roll|swat|scratch|scratch_in|scratch_out|knead)$/.test(s.a))return false;
  const sleeping=!!(s&&s.k==='act'&&s.a==='sleep');
  const br=rt.br;if(br&&(br.greeting||br.frap||br.hunt||br.carry||br.gaze))return false;
  const blk=rt.blk||petAs(i,function(){return petBlockNow(p)});
  if(blk&&blk.k==='sleep')return false;if(blk&&blk.k==='nap'&&!o.nap)return false;
  if(sleeping&&!o.wake)return false;              /* a nap is only broken for the very first meeting */
  return true;
}
function petPairEnergy(i){try{return petAs(i,function(){return petBrain(petData()).af.a})}catch(e){return 0}}
function petPairKit(i){const p=petPairP(i);return p?Math.max(0,1-petGrowth(p)):0}
function petPairTick(dt){
  const P=PET_PAIR,now=Date.now(),d=petPairData();if(!d)return;
  if(now-P.seenAt>30000){P.seenAt=now;d.seen=now}
  const I=P.I;
  if(I){
    for(const i of [0,1]){const rt=petPairRt(i);if(!rt||!rt.c||rt.c.plan!==I.plans[i]){petPairEnd(I,false,'plan '+i);return}}
    if(now-I.stT>PET_PAIR_CFG.STAGE_MAX_S*1000){petPairEnd(I,false,'timeout');return}
    if(I.done[0]&&I.done[1])petPairNext(I);
    return;
  }
  if(now<P.nextAt)return;P.nextAt=now+PET_PAIR_CFG.CHECK_S*1000;
  try{petPairMaybeStart()}catch(e){console.warn('[pet pair]',e)}
}
function petPairMaybeStart(){
  const P=PET_PAIR,d=petPairData(),now=Date.now(),C=PET_PAIR_CFG;
  if(!d.met){if(petPairFree(0,{hungry:1,nap:1,wake:1})&&petPairFree(1,{hungry:1,nap:1,wake:1}))petPairStart('greet',1);return}
  if(now<P.coolUntil||Math.random()>C.START_P)return;
  /* one is napping on the floor: a close friend may come and curl up beside him */
  if(d.rel>=C.NAP_REL)for(const i of [0,1]){const rt=petPairRt(i),s=rt.c.step,j=1-i,jb=petPairRt(j).blk;
    if(s&&s.k==='act'&&s.a==='sleep'&&rt.c.surf==='floor'&&!petBoxIn(rt)&&rt.blk&&rt.blk.k!=='sleep'&&(s.d-s.t)>60&&petPairFree(j,{nap:1})&&(jb&&(jb.k==='rest'||jb.k==='nap')||petPairEnergy(j)<0.1)){petPairStart('snuggle',j);return}}
  const rest=[0,1].map(function(i){const rt=petPairRt(i);return rt.blk&&(rt.blk.k==='rest'||rt.blk.k==='nap')});
  const nap=rest[0]&&rest[1];
  if(!petPairFree(0,{nap:nap})||!petPairFree(1,{nap:nap}))return;
  const e=(petPairEnergy(0)+petPairEnergy(1))/2,kit=Math.max(petPairKit(0),petPairKit(1)),play=(state.pet.trait==='playful'||state.pet2.trait==='playful')?1.5:1;
  const O=[],add=function(k,w){if(w>0)O.push([k,w])};
  add('greet',(now-(d.lastGreet||0))/60000>C.GREET_GAP_MIN?1.3:0.2);
  try{add('standoff',petRankWeight())}catch(e){}
  if(d.rel>=C.CHASE_REL||kit>0.3)add('chase',(rest[0]||rest[1]?0.3:1)*(0.6+Math.max(0,e))*(1+1.4*kit)*play);
  if(d.rel>=C.GROOM_REL)add('groom',(rest[0]||rest[1]?1.6:0.7)*(1.2-0.6*Math.max(0,e)));
  let tot=0;O.forEach(function(o){tot+=o[1]});let x=Math.random()*tot,pick=O[0];for(const o of O){if((x-=o[1])<=0){pick=o;break}}
  let a=Math.random()<0.5?0:1;
  if(pick[0]==='chase')a=petPairKit(1)>petPairKit(0)+0.2?1:petPairKit(0)>petPairKit(1)+0.2?0:a;    /* the younger one starts it */
  if(pick[0]==='groom')a=petPairKit(0)<petPairKit(1)-0.2?0:petPairKit(1)<petPairKit(0)-0.2?1:a;   /* the older one grooms */
  if((pick[0]==='chase'||pick[0]==='groom')&&Math.abs(petPairKit(0)-petPairKit(1))<=0.2)try{a=petRankBias(pick[0],a)}catch(e){}   /* no age gap: rank decides */
  if(pick[0]==='standoff')try{a=petRankChallenger()}catch(e){}
  petPairStart(pick[0],a);
}
function petPairStart(kind,a,opt){
  const P=PET_PAIR;if(!petPairOn())return false;if(P.I)petPairEnd(P.I,false);
  const I={kind:kind,a:a,b:1-a,queue:PET_PAIR_PLAYS[kind].slice(),plans:[null,null],done:[false,false],stT:Date.now(),rounds:0,opt:opt||{}};
  P.I=I;petPairNext(I);return true;
}
function petPairNext(I){
  const st=I.queue.shift();if(!st){petPairEnd(I,true);return}
  let spec=null;try{spec=st(I)}catch(e){console.warn('[pet pair]',e)}
  if(!spec){petPairEnd(I,false,'stage');return}
  I.stT=Date.now();I.done=[false,false];
  [['a',I.a],['b',I.b]].forEach(function(e){
    const i=e[1],fn=spec[e[0]],rt=petPairRt(i);if(spec.final&&!fn&&spec['keep'+e[0].toUpperCase()])return;
    petAs(i,function(){
      const r=fn?fn(rt,rt.c,petPairRt(1-i).c):[[],'idle_sit'],q=r[0]||[];
      q.forEach(function(s){s.pair=1;if(s.k==='walk')s.nohes=1});
      if(!spec.final){q.push({k:'fn',pair:1,f:function(){I.done[i]=true}});q.push(petAct(r[1]||'idle_sit',90,{pair:1,hold:1}))}
      else if(!q.length)q.push(petAct('idle_sit',2));
      petInterrupt(rt,q);I.plans[i]=rt.c.plan;
    });
  });
  if(spec.final)petPairEnd(I,true);
}
function petPairEnd(I,ok,why){
  const P=PET_PAIR;if(P.I!==I)return;P.I=null;P.last={kind:I.kind,ok:ok,why:why||'',stage:I.queue.length,at:Date.now()};
  const C=PET_PAIR_CFG;P.coolUntil=Date.now()+(C.COOL_S[0]+Math.random()*(C.COOL_S[1]-C.COOL_S[0]))*1000;
  if(!ok)[0,1].forEach(function(i){const rt=petPairRt(i);if(rt&&rt.c&&rt.c.plan===I.plans[i])petAs(i,function(){petInterrupt(rt,[petAct('idle_sit',2)])})});
}

/* ---------- geometry ---------- */
function petPairSx(i){return petSx(petPairP(i))}
function petPairGo(c,x,z,run){const q=petRoute(c,'floor',petClampHouse(x));petDepthSetLastWalkZ(q,z);if(run)q.forEach(function(s){if(s.k==='walk')s.run=1});return q}
/* a shared depth both cats may stand at, near x */
function petPairZ(x0,x1,z){const lo=Math.max(petZMinAt(x0),petZMinAt(x1),petZMinAt((x0+x1)/2)),hi=Math.min(petDepthMaxZ(state.pet),petDepthMaxZ(state.pet2));
  z=petDepthAvoidProps((x0+x1)/2,z);return petClampN(z,lo,Math.max(lo,hi))}
function petPairFloorZ(c){return c.surf==='floor'?c.z:40}
function petPairSpawn(i,kind,x,y,n,spread){const rt=petPairRt(i);for(let j=0;j<(n||1);j++)petSpawn(rt,kind,x+petRand(-(spread||4),spread||4),y,petRand(-6,6),petRand(-16,-10),1.5)}
function petPairNames(I){return [petPairP(I.a).name,petPairP(I.b).name]}

/* ---------- the interactions ---------- */
/* a nose touch: meet face to face, heads low; how it ends depends on how well they know each other */
function petPairGreetMeet(I){
  const A=petPairRt(I.a).c,B=petPairRt(I.b).c,ax=A.x,bx=B.x;
  let mid=Math.abs(ax-bx)>140?bx+Math.sign(ax-bx||1)*40:(ax+bx)/2;mid=petClampN(mid,PET_HMIN+30,PET_HMAX-30);
  const side=ax<=bx?-1:1;let z=petPairZ(mid-20,mid+20,(petPairFloorZ(A)+petPairFloorZ(B))/2);
  const k=petDepthScalePct(z)/100,gap=22*k*(petPairSx(I.a)+petPairSx(I.b));
  I.mx=mid;I.mz=z;I.side=side;I.gap=gap;
  const woke=function(c,q){const s=c.step;if(s&&s.k==='act'&&s.a==='sleep')q.unshift(petAct('stretch',3.2));return q};
  return {a:function(rt,c){const q=woke(c,petPairGo(c,mid+side*gap/2,z));q.push({k:'face',dir:-side});return [q,'idle_sit']},
          b:function(rt,c){const q=woke(c,petPairGo(c,mid-side*gap/2,z));q.push({k:'face',dir:side});return [q,'idle_sit']}};
}
function petPairGreetSniff(I){
  return {a:function(rt,c){const k=petK(c);return [[{k:'fn',f:function(){petSpawn(rt,'sparkle',I.mx,c.y-14*k,0,-8,0.9)}},petAct('rub',1.8)],'idle_sit']},
          b:function(){return [[petAct('rub',1.8)],'idle_sit']}};
}
function petPairGreetEnd(I){
  const d=petPairData(),C=PET_PAIR_CFG,nm=petPairNames(I),first=!d.met;d.met=d.met||Date.now();d.lastGreet=Date.now();
  const shy=function(i){return petPairP(i).trait==='shy'?0.15:0};
  const pH=d.rel<C.HISS_BELOW?0.75-1.6*d.rel+shy(I.b):0.04;
  if(Math.random()<pH){                                  /* a hiss: B has had enough, A grooms it off */
    petPairGain('hiss');petPairNote('hiss',I.b,nm[1]+' hissed at '+nm[0]);petPairFeel(I.b,-0.06,0.15);petPairFeel(I.a,-0.03,0.08);
    if(first)toast(nm[1]+' and '+nm[0]+' have met. A hiss is normal at first.');
    return {final:1,
      b:function(rt,c,oc){const away=petClampHouse(c.x+(c.x>=oc.x?1:-1)*petRand(50,80)),q=[{k:'fn',f:function(){petPlay(rt,'hiss',0.75);petSpawn(rt,'exclaim',c.x+c.dir*10*petK(c),c.y-42*petK(c),0,-6,1.0)}},petAct('meow',0.9)];
        q.push.apply(q,petPairGo(c,away,c.z,true));q.push(petAct('idle_sit',2.5));q.push(petAct('groom',petRand(4,7)));return [q]},
      a:function(rt,c){return [[petAct('idle_sit',1.2),petAct('groom',petRand(3,5)),petAct('idle_sit',petRand(4,8))]]}};
  }
  petPairGain('greet');petPairFeel(I.a,0.04,0);petPairFeel(I.b,0.04,0);
  if(first)toast(nm[0]+' and '+nm[1]+' have met.');
  if(d.rel<0.5){petPairNote('greet',I.a,nm[0]+' and '+nm[1]+' touched noses');
    return {final:1,a:function(){return [[petAct('idle_sit',1.8),petAct('slowblink',2.2),petAct('idle_sit',petRand(4,8))]]},
      b:function(rt,c,oc){const q=[petAct('idle_sit',1.5)];q.push.apply(q,petPairGo(c,c.x+(c.x>=oc.x?1:-1)*petRand(30,50),c.z));q.push(petAct('loaf',petRand(15,30)));return [q]}};
  }
  petPairNote('greet',I.a,nm[0]+' rubbed cheeks with '+nm[1]);
  return {final:1,
    a:function(rt,c){const k=petK(c);return [[{k:'fn',f:function(){for(let j=0;j<2;j++)petSpawn(rt,'heart',I.mx+petRand(-4,4),c.y-30*k,petRand(-5,5),-12,1.6)}},petAct('rub',2.4,{snd:'purreow'}),petAct('idle_sit',petRand(5,9)),petAct('groom',petRand(3,5))]]},
    b:function(rt){return [[{k:'fn',f:function(){petBrainVoice(rt,'trill',{force:1})}},petAct('slowblink',2.4),petAct('idle_sit',petRand(6,10))]]}};
}
/* chase: stalk, wiggle, pounce; the other bolts, then a belly-up tussle */
function petPairChaseLine(I){
  const C=petPairRt(I.a).c,R=petPairRt(I.b).c,rx=R.x;let f=rx>=C.x?1:-1;
  if(rx+f*120>PET_HMAX-10||rx+f*120<PET_HMIN+10)f=-f;
  const xc=petClampHouse(rx-f*56),z=petPairZ(xc,rx,petPairFloorZ(R));I.f=f;I.z=z;
  return {a:function(rt,c){const q=petPairGo(c,xc,z);q.push({k:'face',dir:f});return [q,'idle_sit']},
          b:function(rt,c){const q=c.surf==='floor'&&Math.abs(c.x-rx)<2?[]:petPairGo(c,rx,z);q.push({k:'face',dir:-f});return [q,'idle_sit']}};
}
function petPairChaseStalk(I){
  return {a:function(){return [[petAct('crouch',petRand(0.5,0.8)),petAct('crouch',0.8,{wiggle:1})],'crouch']},
          b:function(){return [[petAct('idle_sit',1.1)],'idle_sit']}};
}
function petPairChaseGo(I){
  const f=I.f,R=petPairRt(I.b).c,T=petClampN(R.x+f*petRand(80,125),PET_HMIN+12,PET_HMAX-12),zT=petPairZ(T-20,T+20,I.z+petRand(-12,12)),k=petDepthScalePct(zT)/100;
  const gap=18*k*(petPairSx(I.a)+petPairSx(I.b));I.tx=T;I.tz=zT;
  return {a:function(rt,c){const q=[{k:'hop',s:'floor',x:petClampHouse(R.x-f*14),hh:14},petAct('idle_sit',0.25)];q.push({k:'walk',x:petClampHouse(T-f*gap),z:zT,run:1});q.push({k:'face',dir:f});return [q,'idle_sit']},
          b:function(rt,c){const q=[{k:'fn',f:function(){petSpawn(rt,'exclaim',c.x,c.y-42*petK(c),0,-6,0.7)}},petAct('idle_sit',0.15),{k:'walk',x:T,z:zT,run:1},{k:'face',dir:-f}];return [q,'idle_sit']}};
}
function petPairChaseTussle(I){
  return {a:function(rt,c){const k=petK(c);return [[{k:'fn',f:function(){for(let j=0;j<3;j++)petSpawn(rt,'sparkle',I.tx-I.f*6+petRand(-6,6),c.y-10*k,petRand(-12,12),petRand(-22,-8),0.9)}},petAct('play',2.4)],'idle_sit']},
          b:function(){return [[petAct('roll',2.4)],'idle_sit']}};
}
function petPairChaseMore(I){
  I.rounds++;
  if(I.rounds<3&&Math.random()<0.55){const t=I.a;I.a=I.b;I.b=t;I.queue.unshift(petPairChaseLine,petPairChaseStalk,petPairChaseGo,petPairChaseTussle,petPairChaseMore);
    return {a:function(){return [[petAct('idle_sit',0.6)],'idle_sit']},b:function(){return [[petAct('idle_sit',0.6)],'idle_sit']}}}
  const nm=petPairNames(I);petPairGain('chase');petPairNote('chase',I.a,nm[0]+' and '+nm[1]+' played chase');petPairFeel(I.a,0.06,0.1);petPairFeel(I.b,0.06,0.1);
  return {final:1,a:function(){return [[petAct('idle_sit',2.5),petAct('groom',petRand(4,6)),petAct('idle_sit',petRand(6,12))]]},
                  b:function(rt,c){const q=[petAct('stretch',3.2)];q.push.apply(q,petPairGo(c,c.x+(Math.random()<0.5?-1:1)*petRand(30,60),c.z));q.push(petAct('groom',petRand(4,6)));return [q]}};
}
/* grooming: one grooms the other, who purrs; friends may return the favour, close friends nap together */
function petPairGroomLine(I){
  const G=petPairRt(I.a).c,R=petPairRt(I.b).c,rx=petClampHouse(R.x),side=G.x<=rx?-1:1,z=petPairZ(rx-30,rx+30,petPairFloorZ(R)),k=petDepthScalePct(z)/100;
  const gap=k*(16*petPairSx(I.a)+15*petPairSx(I.b));I.rx=rx;I.side=side;I.z=z;I.gap=gap;
  return {a:function(rt,c){const q=petPairGo(c,rx+side*gap,z);q.push({k:'face',dir:-side});return [q,'idle_sit']},
          b:function(rt,c){const q=c.surf==='floor'&&Math.abs(c.x-rx)<2&&Math.abs(c.z-z)<1?[]:petPairGo(c,rx,z);q.push(petAct('loaf',1.5));return [q,'loaf']}};
}
function petPairGroomDo(I){
  return {a:function(rt,c){const k=petK(c);return [[{k:'fn',f:function(){petSpawn(rt,'heart',c.x-c.dir*-12*k,c.y-34*k,0,-10,1.6)}},petAct('groom',6.5)],'idle_sit']},
          b:function(rt,c){const k=petK(c);return [[petAct('purr',6.5),{k:'fn',f:function(){petSpawn(rt,'heart',c.x,c.y-36*k,0,-10,1.6)}}],'loaf']}};
}
function petPairGroomBack(I){
  const d=petPairData();if(d.rel<0.45||Math.random()>0.45)return {a:function(){return [[petAct('idle_sit',0.5)],'idle_sit']},b:function(){return [[petAct('loaf',0.5)],'loaf']}};
  I.back=1;
  return {a:function(){return [[petAct('purr',4.5)],'idle_sit']},b:function(rt,c,oc){return [[{k:'face',dir:oc.x>=c.x?1:-1},petAct('groom',4.5)],'loaf']}};
}
function petPairGroomEnd(I){
  const d=petPairData(),nm=petPairNames(I),rest=[I.a,I.b].every(function(i){const b=petPairRt(i).blk;return b&&(b.k==='rest'||b.k==='nap')});
  petPairGain('groom');petPairFeel(I.a,0.05,-0.05);petPairFeel(I.b,0.07,-0.08);
  petPairNote('groom',I.a,I.back?nm[0]+' and '+nm[1]+' groomed each other':nm[0]+' groomed '+nm[1]);
  const nap=I.opt.nap||(d.rel>=PET_PAIR_CFG.NAP_REL&&(rest||(petPairEnergy(I.a)+petPairEnergy(I.b))/2<0||Math.random()<0.3));
  if(nap){                                                 /* curled up together */
    petPairGain('nap');petPairNote('nap',I.a,nm[0]+' and '+nm[1]+' napped together');
    const t=petRand(90,200);
    return {final:1,a:function(rt,c,oc){const q=petPairGo(c,I.rx+I.side*I.gap*0.8,I.z+1.5);q.push({k:'face',dir:oc.dir});q.push(petAct('sleep',t));return [q]},
                    b:function(){return [[petAct('sleep',t+petRand(-10,20))]]}};
  }
  return {final:1,a:function(rt,c){const q=[petAct('idle_sit',2)];q.push.apply(q,petPairGo(c,c.x+I.side*petRand(30,50),c.z));q.push(petAct('loaf',petRand(20,40)));return [q]},
                  b:function(){return [[petAct('loaf',petRand(20,40))]]}};
}
/* curling up beside a napping friend (a = the one who comes over) */
function petPairSnuggle(I){
  const S=petPairRt(I.b).c,s=S.step,left=s&&s.k==='act'?Math.max(40,s.d-s.t):120,k=petK(S),side=petPairRt(I.a).c.x<=S.x?-1:1;
  const gap=k*(17*petPairSx(I.a)+17*petPairSx(I.b)),nm=petPairNames(I);
  petPairGain('nap');petPairNote('nap',I.a,nm[0]+' curled up next to '+nm[1]);petPairFeel(I.a,0.05,-0.08);petPairFeel(I.b,0.04,-0.04);
  return {final:1,a:function(rt,c){const q=petPairGo(c,S.x+side*gap,S.z+1.5);q.push({k:'face',dir:S.dir});q.push(petAct('knead',petRand(2.5,4)));q.push(petAct('sleep',left+petRand(-20,30)));return [q]},
          b:null,keepB:1};
}
var PET_PAIR_PLAYS={
  snuggle:[petPairSnuggle],
  greet:[petPairGreetMeet,petPairGreetSniff,petPairGreetEnd],
  chase:[petPairChaseLine,petPairChaseStalk,petPairChaseGo,petPairChaseTussle,petPairChaseMore],
  groom:[petPairGroomLine,petPairGroomDo,petPairGroomBack,petPairGroomEnd]
};

/* ---------- while you were away ---------- */
function petPairSession(){
  const d=petPairData();if(!d||!d.met)return;const now=Date.now(),gap=now-(d.seen||now);d.seen=now;
  if(gap<30*60000||state.pet.vacation.on||state.pet2.vacation.on)return;
  petPairGain('away',Math.min(0.06,0.02*gap/36e5),true);
  const n=[state.pet.name,state.pet2.name],r=d.rel;
  const t=r<0.3?'kept a polite distance':r<0.55?'took turns at the sunny spot':r<0.8?'played chase and groomed each other':'spent most of it curled up together';
  d.away={at:now,t:n[0]+' and '+n[1]+' '+t+'.'};
}

/* ---------- second adoption ---------- */
function petPairAdoptOpen(){PET_PAIR.adopting=true;window.__petCoat=(PET_COATS.find(function(c){return c[0]!==(state.pet||{}).coat})||PET_COATS[0])[0];window.__petTrait='playful';petPairDecorate(true);
  const el=document.getElementById('petPair');if(el)el.scrollIntoView({block:'start',behavior:'smooth'})}
function petPairAdoptClose(){PET_PAIR.adopting=false;petPairDecorate(true)}
function petPairAdoptHtml(){
  const p=state.pet;
  return '<div class="eyebrow">ANOTHER VISITOR</div><h2>A second stray by the kitchen window</h2>'
   +'<p class="muted">Another kitten has been turning up. Two cats keep each other company: they work each other out over a few days, then play chase, groom each other and nap together. '+esc(p.name)+' may hiss at first.</p>'
   +'<div class="pet-coats" id="petCoats">'+PET_COATS.map(function(c){return '<button type="button" class="pet-coat'+(c[0]===window.__petCoat?' on':'')+'" data-coat="'+c[0]+'" onclick="petPickCoat(\''+c[0]+'\')"><canvas data-coat="'+c[0]+'" width="144" height="96"></canvas><span>'+c[1]+'</span></button>'}).join('')+'</div>'
   +'<label class="pet-lab" for="petNameIn">Name</label><input id="petNameIn" class="pet-in" maxlength="14" placeholder="'+PET_NAMES.filter(function(n){return n!==p.name})[Math.floor(Math.random()*(PET_NAMES.length-1))]+'" autocomplete="off">'
   +'<label class="pet-lab">Personality</label><div class="pet-traits" id="petTraits">'+[['shy','Shy','hides, loves high places'],['playful','Playful','zoomies and toys'],['lazy','Lazy','naps a lot'],['curious','Curious','window and wandering']].map(function(t){return '<button type="button" data-t="'+t[0]+'" class="'+(t[0]===window.__petTrait?'on':'')+'" onclick="petPickTrait(\''+t[0]+'\')"><b>'+t[1]+'</b><small>'+t[2]+'</small></button>'}).join('')+'</div>'
   +'<div class="pet-pair-go"><button class="btn secondary" type="button" onclick="petPairAdoptClose()">Not now</button><button class="btn" type="button" onclick="petPairAdoptClick()">Let them in</button></div>';
}
function petPairAdopt(coat,name,trait){
  const now=Date.now(),taken=(state.pet||{}).name;let n=(name||'').trim().slice(0,14);
  if(!n){const pool=PET_NAMES.filter(function(x){return x!==taken});n=pool[Math.floor(Math.random()*pool.length)]}
  state.pet2={v:1,name:n,coat:coat,trait:trait||'curious',born:now,lastFed:now-10*PET_H,meals:[],day:petDay(),fedToday:0,petsToday:0,playsToday:0,bondPts:0,care:0,
    vacation:{on:!!(state.pet&&state.pet.vacation.on),since:state.pet&&state.pet.vacation.on?now:0},gifts:0,lastGiftDay:'',alive:true,passed:0,seen:{},greeted:0};
  state.petPair=null;petPairData();return state.pet2;
}
function petPairAdoptClick(){
  if(!state.pet||!state.pet.alive||(state.pet2&&state.pet2.alive))return;
  const p=petPairAdopt(window.__petCoat||'gray',(document.getElementById('petNameIn')||{}).value,window.__petTrait||'playful');
  const P=PET_PAIR;P.adopting=false;P.arrival=true;P.focus=1;if(P.rt2)petPairDrop();P.focus=1;
  save();toast(p.name+' has moved in. Tap a name, or a cat in the room, to switch.');petSync();
}

/* ---------- the Cat screen: switcher and the Together card ---------- */
function petPairFocus(i){
  const P=PET_PAIR;if(i===P.focus||(i===1&&!petPairOn()))return;P.focus=i;
  [P.rt1,P.rt2].forEach(function(rt){if(rt&&rt.br)rt.br.somKey=''});
  try{petToyTray(false)}catch(e){}
  renderPetView(true);petBlitSoon();
}
function petPairDrawPortraits(){
  const rt=PET_PAIR.rt1||PET_RT;if(!rt||!rt.ready)return;const A=rt.A,m=A.anims.idle_sit;
  document.querySelectorAll('#petSwitch canvas[data-i]').forEach(function(cv){const p=petPairP(+cv.dataset.i),sh=p&&rt.sheets[p.coat];if(!sh)return;
    const x=cv.getContext('2d');x.imageSmoothingEnabled=false;x.clearRect(0,0,cv.width,cv.height);x.drawImage(sh,12,m.row*A.H,48,A.H,0,0,cv.width,cv.height)});
}
function petPairSwitchHtml(){
  const P=PET_PAIR;
  return '<div class="pet-switch" id="petSwitch" role="tablist" aria-label="Which cat">'+[0,1].map(function(i){const p=petPairP(i);
    return '<button type="button" role="tab" aria-selected="'+(P.focus===i)+'" class="'+(P.focus===i?'on':'')+'" onclick="petPairFocus('+i+')"><canvas data-i="'+i+'" width="32" height="32" aria-hidden="true"></canvas><span>'+esc(p.name)+'</span></button>'}).join('')+'</div>';
}
function petPairCardHtml(){
  const d=petPairData();if(!d)return '';const L=petPairLevel(d.rel),a=state.pet.name,b=state.pet2.name,n=d.n,now=Date.now();
  const bits=[];if(n.greet)bits.push(n.greet+' nose touch'+(n.greet>1?'es':''));if(n.hiss)bits.push(n.hiss+' hiss'+(n.hiss>1?'es':''));if(n.chase)bits.push(n.chase+' chase'+(n.chase>1?'s':''));if(n.groom)bits.push(n.groom+' grooming'+(n.groom>1?'s':''));if(n.nap)bits.push(n.nap+' nap'+(n.nap>1?'s':'')+' together');if(n.standoff)bits.push(n.standoff+' standoff'+(n.standoff>1?'s':''));
  let h='<div class="pet-pair-head"><div><div class="eyebrow">TOGETHER</div><h3>'+esc(a)+' &amp; '+esc(b)+'</h3></div><span class="pet-pair-lvl">'+L[1]+'</span></div>'
   +'<div class="pet-bondbar pet-pair-bar"><i style="width:'+Math.round(d.rel*100)+'%"></i></div>'
   +'<p class="pet-pair-text">'+(d.met?L[2]:esc(b)+' is new here. They have not properly met yet.')+'</p>';
  if(d.away&&now-d.away.at<15*60000)h+='<p class="pet-away"><b>While you were away</b>'+esc(d.away.t)+'</p>';
  if(bits.length)h+='<p class="muted pet-pair-counts">'+bits.join(' · ')+'</p>';
  try{h+=petRankHtml()}catch(e){}
  if(d.last)h+='<p class="muted pet-pair-counts">Last: '+esc(d.last.t)+', '+petAgo(now-d.last.at)+'.</p>';
  return h;
}
function petPairInviteHtml(){
  return '<div style="flex:1"><strong>A second cat</strong><div class="muted" style="font-size:12px">Another stray has been seen by the kitchen window. Two cats keep each other company: they meet, play chase, groom each other and nap together.</div></div><button type="button" class="btn secondary small" onclick="petPairAdoptOpen()">Meet</button>';
}
/* adds the pair parts to the Cat screen after it is built; safe to call any time */
function petPairDecorate(force){
  const card=document.querySelector('#view-pet .pet-card');if(!card||!state.pet||!state.pet.alive)return;
  const P=PET_PAIR,two=!!(state.pet2&&state.pet2.alive);
  let sw=document.getElementById('petSwitch');
  if(two&&petPairOn()&&!sw){const head=card.querySelector('.pet-head');if(head){head.insertAdjacentHTML('afterend',petPairSwitchHtml());petPairDrawPortraits()}}
  else if(sw&&!two)sw.remove();
  let pc=document.getElementById('petPair');const want=two||P.adopting;
  if(want&&!pc){card.insertAdjacentHTML('afterend','<div class="card pet-pair" id="petPair"></div>');pc=document.getElementById('petPair');force=true}
  else if(!want&&pc){pc.remove();pc=null}
  if(pc&&force){if(!two&&P.adopting){pc.innerHTML=petPairAdoptHtml();pc.classList.add('pet-intro');petInit().then(petCoatPreviewDraw)}else{pc.classList.remove('pet-intro');pc.innerHTML=petPairCardHtml();pc.dataset.t=Date.now()}}
  const more=document.querySelector('#view-pet details.pet-more');let inv=document.getElementById('petPairInvite');
  if(more&&!two&&!P.adopting&&!inv){const lk=document.getElementById('petLikes');if(lk){lk.insertAdjacentHTML('beforebegin','<div class="settingsline pet-pair-invite" id="petPairInvite">'+petPairInviteHtml()+'</div>')}}
  else if(inv&&(two||P.adopting))inv.remove();
}
/* per-frame bits: the shared Feed button and the Together card */
function petPairStatus(force){
  if(!petPairOn())return;const now=Date.now(),cats=[state.pet,state.pet2];
  const fb=document.getElementById('petFeedBtn');
  if(fb){const vac=cats.every(function(p){return p.vacation.on}),can=cats.filter(function(p){return !p.vacation.on&&petCanFeed(p,now)});
    const lab=vac?'Vacation mode':can.length===2?'Feed both':can.length?'Feed '+can[0].name:'Not hungry yet';if(fb.textContent!==lab)fb.textContent=lab;fb.classList.toggle('is-wait',!can.length)}
  const pc=document.getElementById('petPair');
  if(pc&&!PET_PAIR.adopting&&(force||now-(+pc.dataset.t||0)>2000)){pc.dataset.t=now;const h=petPairCardHtml();if(pc.__h!==h){pc.innerHTML=h;pc.__h=h}}
}

/* ---------- wrapping the one-cat entry points ---------- */
function petPairWrap(name,make){const orig=window[name];if(typeof orig==='function')window[name]=make(orig)}
function petPairUser(fn,self,args){const P=PET_PAIR;P.user=(P.user||0)+1;try{return fn.apply(self,args)}finally{P.user--}}
function petPairViaFocus(orig){return function(){const P=PET_PAIR,a=arguments;return petPairUser(function(){return petAs(petPairOn()?P.focus:0,orig,a)})}}
/* while two cats are doing something together, the brain does not pull either away; you still can */
petPairWrap('petInterrupt',function(orig){return function(rt,plan){const P=PET_PAIR,I=P&&P.I;
  if(I&&!P.user&&rt&&(rt===P.rt1||rt===P.rt2)&&plan&&plan.length&&!plan[0].pair)return;return orig(rt,plan)}});
['petPlayClick','petBlinkClick','petToyTray','petToyTrayRender','petOfferToy','petDevAge','petDevBond','petDevBrain','petDevDepth','petDevFed','petDevGreet','petDevGrow',
 'petDevLikes','petDevPlay','petDevPounce','petDevRunTest','petDevScratch','petDevPlay2','petDevBox'].forEach(function(n){petPairWrap(n,petPairViaFocus)});
petPairWrap('petRename',function(orig){return function(n){return petAs(petPairOn()?PET_PAIR.focus:0,orig,[n],true)}});
petPairWrap('petSetVacation',function(orig){return function(on){petAs(0,orig,[on],true);if(state.pet2&&state.pet2.alive)petAs(1,orig,[on],true)}});
var petMaybeGreetOne=petMaybeGreet;
petPairWrap('petMaybeGreet',function(orig){return function(rt,force){const P=PET_PAIR,r=orig(rt,force);if(!PET_CTX&&rt&&rt===P.rt1&&petPairOn()){petAs(1,orig,[P.rt2,force]);petPairSession()}return r}});
petPairWrap('petBrainSession',function(orig){return function(rt){const P=PET_PAIR,r=orig(rt);if(!PET_CTX&&rt&&rt===P.rt1&&petPairOn())petAs(1,orig,[P.rt2]);return r}});
petPairWrap('petPlaceForNow',function(orig){return function(rt){const r=orig(rt);petPairUntangle(rt);return r}});
/* feeding: whoever is hungry comes to the bowl; the second cat eats from the other side */
petPairWrap('petFeedClick',function(orig){return function(){return petPairUser(petPairFeed,null,[orig])}});
function petPairFeed(orig){{
  const P=PET_PAIR;if(!petPairOn())return petAs(0,orig);
  const now=Date.now(),can=[0,1].filter(function(i){const p=petPairP(i);return !p.vacation.on&&petCanFeed(p,now)});
  if(P.I)petPairEnd(P.I,false);
  if(!can.length)return petAs(P.focus,orig);
  can.forEach(function(i){petAs(i,orig)});
  if(can.length===2)[0,1].forEach(function(i){(petPairRt(i).c.plan||[]).forEach(function(s){delete s.endBowl})});   /* the bowl empties once both are done */
}}
/* touch: whichever cat is under your finger (the nearer one if both); taps on the room go to the selected cat */
petPairWrap('petPointer',function(orig){return function(rt,type,x,y){return petPairUser(petPairPointer,null,[orig,rt,type,x,y])}});
function petPairPointer(orig,rt,type,x,y){{
  const P=PET_PAIR;if(!petPairOn())return orig(rt,type,x,y);
  if(type==='down'){
    const h0=petAs(0,function(){return petHit(P.rt1,x,y)}),h1=petAs(1,function(){return petHit(P.rt2,x,y)});
    let who=P.focus;
    if(h0==='cat'&&h1==='cat')who=P.rt2.c.z>P.rt1.c.z?1:0;else if(h0==='cat')who=0;else if(h1==='cat')who=1;
    else if(h1==='ball'||h1==='gift')who=h0===h1?P.focus:1;else if(h0==='ball'||h0==='gift')who=0;
    P.ptr=who;if((h0==='cat'||h1==='cat')&&who!==P.focus)P.pend=who;
  }
  const who=P.ptr==null?P.focus:P.ptr,r=petAs(who,orig,[petPairRt(who),type,x,y]);
  if(type==='up'){P.ptr=null;if(P.pend!=null){const f=P.pend;P.pend=null;petPairFocus(f)}}
  return r;
}}
/* the status panel shows the selected cat */
petPairWrap('petRenderStatus',function(orig){return function(force){const r=petAs(petPairOn()?PET_PAIR.focus:0,orig,[force]);petPairStatus(force);return r}});
petPairWrap('renderPetView',function(orig){return function(force){
  const P=PET_PAIR;if(!(state.pet2&&state.pet2.alive)||!(state.pet&&state.pet.alive))P.focus=0;
  const det=document.querySelector('#view-pet details.pet-more'),open=!!(det&&det.open);
  const r=petAs(petPairOn()?P.focus:0,orig,[force]);
  const det2=document.querySelector('#view-pet details.pet-more');if(det2&&det2!==det&&open)det2.open=true;
  if(det2!==det||force)petPairDecorate(true);else petPairDecorate(false);
  return r;
}});
petPairWrap('renderPetPeek',function(orig){return function(){const r=petAs(0,orig);
  const s=document.querySelector('#petPeek .pet-peek-row strong');if(s&&state.pet&&state.pet.alive&&state.pet2&&state.pet2.alive){const t=state.pet.name+' & '+state.pet2.name;if(s.textContent!==t)s.textContent=t}
  return r}});
/* hunger: each cat on its own clock. If the first cat dies and the second lives, the second becomes the main cat */
petPairWrap('petCheck',function(orig){return function(now){
  const P=PET_PAIR;
  if(state.pet2&&state.pet2.alive){const r2=petAs(1,orig,[now],true);
    if(r2==='passed'){const n=state.pet2.name;state.pet2=null;state.petPair=null;petPairDrop();save();toast(n+' has passed away.');setTimeout(renderPetAll,0)}}
  const r1=petAs(0,orig,[now],true);
  if(r1==='passed'&&state.pet2&&state.pet2.alive){
    const n=state.pet.name;state.pet=state.pet2;state.pet2=null;state.petPair=null;
    if(P.rt2){const old=P.rt1;try{petPurr(old,0)}catch(e){}P.rt1=P.rt2;P.rt2=null;PET_RT=P.rt1}else if(PET_RT&&PET_RT.ready)petPlaceForNow(PET_RT);
    P.focus=0;P.I=null;save();toast(n+' has passed away. '+state.pet.name+' is still here with you.');setTimeout(renderPetAll,0);return null}
  return r1;
}});
/* the frame: both cats think and move, then one picture */
petPairWrap('petFrame',function(){return function(now){
  PET_LOOP.raf=0;if(!petWantRun())return;PET_LOOP.raf=requestAnimationFrame(petFrame);
  if(now-PET_LOOP.last<30)return;const dt=Math.min(0.1,(now-PET_LOOP.last)/1000);PET_LOOP.last=now;
  const rt=PET_RT;if(!rt||!rt.ready)return;
  petPairEnsure(rt);const P=PET_PAIR,two=petPairOn();
  petSeenNow(rt);petUpdate(rt,dt);petDepthTick(rt,dt);
  if(two){petAs(1,function(){const r2=P.rt2;petSeenNow(r2);petUpdate(r2,dt);petDepthTick(r2,dt)});petPairTick(dt)}
  petAs(two?P.focus:0,function(){petCamUpdate(PET_RT,dt)});
  petDraw(rt);petBlit(rt);petRenderStatus(false);
}});
document.addEventListener('visibilitychange',function(){try{const P=PET_PAIR;if(document.hidden&&P.rt2){P.rt2.lastSeen=Date.now();petAs(1,function(){const b=petBrain(petData());if(b)b.off.at=Date.now()});petPurr(P.rt2,0)}}catch(e){}});

/* ---------- developer: a test button for each interaction ---------- */
function petDevPair(k){
  const P=PET_PAIR;
  if(k==='adopt'){if(!state.pet||!state.pet.alive)return;if(!(state.pet2&&state.pet2.alive)){window.__petCoat=(PET_COATS.find(function(c){return c[0]!==state.pet.coat})||PET_COATS[0])[0];window.__petTrait='playful';petPairAdoptClick()}return}
  if(k==='remove'){state.pet2=null;state.petPair=null;petPairDrop();save();renderPetView(true);return}
  if(!petPairOn()){toast('Adopt a second cat first (the "Two cats: adopt" button).');return}
  const d=petPairData();
  if(k==='rel'){d.rel=Math.min(1,Math.round((d.rel+0.2)*100)/100);d.met=d.met||Date.now();save();petPairStatus(true);toast('Friendship '+d.rel.toFixed(2)+' · '+petPairLevel(d.rel)[1]);return}
  [0,1].forEach(function(i){const rt=petPairRt(i);rt.hold=null;rt.follow=null;rt.dangle=null;rt.ball.on=false;rt.inLens=false;rt.lastTapAt=0;
    const c=rt.c;if(c.surf!=='floor'||petBoxIn(rt)){c.surf='floor';c.boxSink=0;c.z=Math.max(PET_DEPTH.ART_Z,petZMinAt(c.x));c.y=petFloorY(c.z);c.step=null}});
  if(k!=='greet')d.met=d.met||Date.now();
  if(k==='nap'){d.rel=Math.max(d.rel,PET_PAIR_CFG.NAP_REL);petPairStart('groom',0,{nap:1});return}
  if(k==='groom')d.rel=Math.max(d.rel,PET_PAIR_CFG.GROOM_REL);
  petPairStart(k,k==='greet'?1:Math.random()<0.5?0:1);
}
petDevTest('Two cats: adopt',function(){petDevPair('adopt')},'v80');
petDevTest('Two cats: nose touch',function(){petDevPair('greet')},'v80');
petDevTest('Two cats: chase',function(){petDevPair('chase')},'v80');
petDevTest('Two cats: grooming',function(){petDevPair('groom')},'v80');
petDevTest('Two cats: nap together',function(){petDevPair('nap')},'v80');
petDevTest('Two cats: friendship +0.2',function(){petDevPair('rel')},'v80');
petDevTest('Two cats: remove second',function(){petDevPair('remove')},'v80');

devChatSeed('claude-pet-pair-v80-2026-10-25','Claude',[
'Two cats (v80): a second cat and what two cats do together. Code in src/runtime/segments/29-pet-pair.js; saved in state.pet2 (same shape as state.pet) and state.petPair (friendship).',
'• The engine still runs one cat at a time: petAs(1, fn) points petData() at state.pet2, PET_RT at the second runtime and the bowl side at the far side of the bowl. Assets, sound, camera, bowl and claw marks are shared (PET_PAIR_SHARED).',
'• Together: nose touch (can end in a hiss early on), chase (stalk, pounce, bolt, belly-up tussle, maybe swap) and grooming (then a nap together for close friends). Friendship 0-1 decides which happen; daily gain capped.',
'• They keep apart (petPairWalkStart), do not jump onto a spot the other is on (petPairHopStart) and are never placed on top of each other (petPairUntangle).',
'• UI: name tabs on the Cat screen (tapping a cat also switches), a Together card, Feed feeds whoever is hungry, Vacation covers both. If the first cat dies and the second lives, the second becomes the main cat.'
].join('\\n'));
