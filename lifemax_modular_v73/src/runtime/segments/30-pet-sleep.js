
/* ===== Bedtime (v81): asleep where you can see him, with his favourite toy =====
   1. Never hidden. The living-room rug spot lies right behind the big floor cushion, so a cat
      napping there showed only his ears. Whenever a cat lies down on the floor (sleep or loaf),
      or is placed there asleep, petSleepHidden() measures how much of him the pouf, cushion or box
      would cover. More than PET_SLEEP.HIDE_MAX, and he lies down just in front of it instead (or
      beside it, if he does not trust you enough to come that far forward).
   2. His favourite toy comes to bed. When the routine sends him to sleep, he takes his favourite toy
      (or, while he has none, the one he plays with most): he walks to it if it is lying on the floor,
      picks it up, carries it in his mouth (the brain's carry art), and puts it down by his face before
      he curls up. On a bed, sill or other raised spot it stays beside him (rt.sideToy); when he gets
      up he takes it with him and drops it once he settles somewhere on the floor, where it becomes an
      ordinary floor toy (state.pet.brain.items) you can tap to start a game. Found asleep when you
      open the app, he already has it.
   Works for both cats (everything is per runtime). Wraps petStartStep, petThink, petPlaceForNow,
   petDepthTick and petBrainScene; no other segment changes. Tuning in PET_SLEEP. */
var PET_SLEEP={HIDE_MAX:0.08,FRONT_DZ:6,TOY_P:0.85,TOY_P_KIT:1,PICK_R:34};

/* ---------- 1. never asleep behind something ---------- */
function petSleepCover(rt,c,x,z){
  const p=petData(),kc=petDepthScalePct(z)/100*(p?petSx(p):1),y=petFloorY(z);
  const cx0=x-22*kc,cx1=x+22*kc,cy0=y-16*kc,cy1=y,area=(cx1-cx0)*(cy1-cy0);let worst=0,by=null;
  for(const pr of PET_PROPS){
    if(z>=pr.z)continue;                                   /* he is in front of it */
    const im=petPropImg(rt,pr.parts?pr.parts[1]:pr.k);if(!im)continue;
    const kp=petDepthScalePct(pr.z)/100,w=im.w*kp,h=im.h*kp,fy=petFloorY(pr.z);
    const ox=Math.max(0,Math.min(cx1,pr.x+w/2)-Math.max(cx0,pr.x-w/2)),oy=Math.max(0,Math.min(cy1,fy)-Math.max(cy0,fy-h+1)),f=ox*oy/area;
    if(f>worst){worst=f;by={pr:pr,w:w,kc:kc}}
  }
  return {f:worst,by:by};
}
function petSleepHidden(rt,c,x,z){return petSleepCover(rt,c,x==null?c.x:x,z==null?c.z:z).f>PET_SLEEP.HIDE_MAX}
/* the nearest place close by where he is fully in view */
function petSleepSpot(rt,c){
  const cov=petSleepCover(rt,c,c.x,c.z);if(cov.f<=PET_SLEEP.HIDE_MAX||!cov.by)return null;
  const pr=cov.by.pr,p=petData(),zmax=petDepthMaxZ(p),O=[];
  const zf=pr.z+PET_SLEEP.FRONT_DZ;if(zf<=zmax)O.push([c.x,Math.max(zf,petZMinAt(c.x))]);
  const side=c.x>=pr.x?1:-1,dx=cov.by.w/2+22*cov.by.kc+3;
  [side,-side].forEach(function(sd){for(const m of [1,1.6,2.4,3.4]){const x=petClampHouse(pr.x+sd*dx*m);O.push([x,petClampN(c.z,petZMinAt(x),Math.max(petZMinAt(x),zmax))])}});
  O.sort(function(a,b){return (Math.abs(a[0]-c.x)+Math.abs(a[1]-c.z))-(Math.abs(b[0]-c.x)+Math.abs(b[1]-c.z))});
  for(const o of O)if(!petSleepHidden(rt,c,o[0],o[1]))return o;
  return O[0]||null;
}
var petSleepLying=/^(sleep|loaf)$/;
petPairWrap('petStartStep',function(orig){return function(rt,s){
  try{const c=rt.c;
    if(s&&s.k==='act'&&petSleepLying.test(s.a)&&c&&c.surf==='floor'&&!s.vis&&!(rt.hold&&rt.hold.on)){
      const to=petSleepSpot(rt,c);
      if(to){c.plan.unshift(Object.assign({},s,{vis:1,t:0}));s.k='walk';s.x=to[0];s.z=to[1];s.vis=1;s.pair=1;s.nohes=1;delete s.a;delete s.d}}
  }catch(e){}
  return orig(rt,s);
}});
petPairWrap('petPlaceForNow',function(orig){return function(rt){
  const r=orig(rt);
  try{const c=rt.c;if(c&&c.surf==='floor'){const to=petSleepSpot(rt,c);if(to){c.x=to[0];c.z=to[1];c.y=petFloorY(c.z)}}
    const s=c&&c.plan&&c.plan[0];if(s&&s.k==='act'&&s.a==='sleep')petSleepToyPlace(rt)}catch(e){}
  return r;
}});

/* ---------- 2. his favourite toy comes to bed ---------- */
function petSleepToyOf(p){
  const lk=petLikes(p);if(!lk)return null;
  if(lk.favToy&&PET_TOYS[lk.favToy]&&PET_TOYS[lk.favToy].mode==='chase')return lk.favToy;
  let best=null,n=1;for(const id of Object.keys(lk.plays||{}))if(PET_TOYS[id]&&PET_TOYS[id].mode==='chase'&&lk.plays[id]>n){n=lk.plays[id];best=id}
  return best;                                             /* no favourite yet: the one he plays with most, if any */
}
function petSleepToyItem(b,k){return (b.items||[]).findIndex(function(it){return it[0]===k&&!it[3]})}
function petSleepToyPick(rt,k){const b=petBrain(petData()),br=petBR(rt),i=petSleepToyItem(b,k);if(i>=0)b.items.splice(i,1);br.carry=k;rt.toyCarry={k:k};br.dirty=true}
/* put it down by his face: on the floor it becomes an ordinary floor toy, up on furniture it stays beside him */
function petSleepToyDrop(rt){
  const k=rt.toyCarry&&rt.toyCarry.k;if(!k)return;const c=rt.c,b=petBrain(petData()),br=petBR(rt),kk=petK(c);
  if(br.carry===k)br.carry=null;rt.toyCarry=null;
  if(c.surf==='floor'){const x=petClampHouse(c.x+c.dir*18*kk),z=Math.min(Math.max(c.z+4,petZMinAt(x)),petDepthMaxZ(petData())+4);   /* a little in front of him, so it shows */b.items=(b.items||[]).filter(function(it){return it[0]!==k||it[3]});
    b.items.push([k,Math.round(x),Math.round(z*10)/10,0]);while(b.items.length>3)b.items.shift()}
  else{const sf=PET_SURF[c.surf];let x=c.x+c.dir*13*kk;if(sf&&sf.x0!=null)x=Math.max(sf.x0-4,Math.min(sf.x1+4,x));
    rt.sideToy={k:k,surf:c.surf,x:x,y:c.surf==='bed'?126:c.y,z:sf&&sf.z!=null?sf.z:c.z}}   /* on the bed it sits on the rim, in front of the bed's front edge */
  br.dirty=true;
}
/* bedtime plan from the routine: fetch the toy (or bring it from wherever he keeps it), then put it down before curling up */
function petSleepToyPlan(rt){
  const c=rt.c,q=c.plan,p=petData();if(!p||!q||!q.length)return;
  const si=q.findIndex(function(s){return s.k==='act'&&s.a==='sleep'});if(si<0||q.some(function(s){return s.pair||s.toyDrop}))return;
  if(petStage(p)>=3)return;const k=petSleepToyOf(p);if(!k)return;
  if(Math.random()>(petGrowth(p)<1?PET_SLEEP.TOY_P_KIT:PET_SLEEP.TOY_P))return;
  const br=petBR(rt);if(br.carry&&!rt.toyCarry)return;      /* already carrying a trophy */
  const side=rt.sideToy;if(side&&side.k===k&&side.surf===c.surf&&Math.abs(side.x-c.x)<PET_SLEEP.PICK_R&&q.every(function(s){return s.k!=='hop'&&s.k!=='walk'}))return;   /* it is already by his bed */
  const b=petBrain(p),i=petSleepToyItem(b,k);
  if(i>=0&&!rt.toyCarry){const it=b.items[i];
    if(c.surf==='floor'&&q.every(function(s){return s.k!=='hop'&&s.k!=='walk'})&&Math.abs(it[1]-c.x)<PET_SLEEP.PICK_R)return;   /* sleeping on the floor right beside it */
    const fetch=petRoute(c,'floor',it[1]);petDepthSetLastWalkZ(fetch,Math.max(it[2],petZMinAt(it[1])));
    fetch.push({k:'face',dir:it[1]>=c.x?1:-1});
    fetch.push(petAct('eat',0.6,{toyPick:1,nextSfx:99,insp:1}));          /* head down: picks it up (no crunching) */
    fetch.push({k:'fn',toyPick:1,f:function(){petSleepToyPick(rt,k)}});
    /* the way to bed was routed from where he stood: route it again from the toy */
    const ki=si>0&&q[si-1].k==='act'&&q[si-1].a==='knead'?si-1:si,rest=q.slice(ki),spot=rt.blk&&rt.blk.spot,from={surf:'floor',x:it[1],z:it[2],dir:1};
    let go=null;try{if(spot)go=petSpotPlan({c:from,blk:rt.blk,ball:{},A:rt.A},spot,petRng(petHash(p.born+'|toybed')))}catch(e){go=null}
    if(!go)go=petRoute(from,'floor',c.x).concat(q.slice(0,ki));
    c.plan=fetch.concat(go,[{k:'fn',toyDrop:1,f:function(){petSleepToyDrop(rt)}}],rest);return}
  if(!rt.toyCarry&&!side)q.unshift({k:'fn',f:function(){petSleepToyPick(rt,k)}});
  const kn=si>0&&q[si-1].k==='act'&&q[si-1].a==='knead'?si-1:si;
  q.splice(kn+(rt.toyCarry||side?0:1),0,{k:'fn',toyDrop:1,f:function(){if(!rt.toyCarry&&rt.sideToy){rt.toyCarry={k:rt.sideToy.k};rt.sideToy=null}petSleepToyDrop(rt)}});
}
petPairWrap('petThink',function(orig){return function(rt){const r=orig(rt);try{petSleepToyPlan(rt)}catch(e){console.warn('[pet sleep]',e)}return r}});
/* found asleep (opening the app, a long absence): he already has it */
function petSleepToyPlace(rt){
  const p=petData(),c=rt.c;if(!p||petStage(p)>=3)return;const k=petSleepToyOf(p);if(!k||rt.toyCarry)return;
  const b=petBrain(p),br=petBR(rt);if(br.carry)return;
  b.items=(b.items||[]).filter(function(it){return it[0]!==k||it[3]});
  rt.toyCarry={k:k};petSleepToyDrop(rt);
}
/* every frame: picks it up when he leaves it on furniture, puts it down when he settles */
function petSleepToyTick(rt){
  const c=rt.c;if(!c)return;const s=c.step,br=rt.br;
  const t=rt.sideToy;
  if(t&&!rt.toyCarry&&(c.surf!==t.surf||Math.abs(c.x-t.x)>PET_SLEEP.PICK_R)){if(br&&!br.carry){br.carry=t.k;rt.toyCarry={k:t.k}}rt.sideToy=null}
  if(rt.toyCarry){
    if(br&&br.carry!==rt.toyCarry.k){rt.toyCarry=null;return}      /* something else took over his mouth */
    const moving=s&&(s.k==='walk'||s.k==='hop'||s.k==='stalk'),pending=(s&&(s.toyDrop||s.toyPick))||c.plan.some(function(q){return q.toyDrop});
    if(!moving&&!pending&&s&&s.k==='act'&&!(rt.hold&&rt.hold.on))petSleepToyDrop(rt);
  }
}
petPairWrap('petDepthTick',function(orig){return function(rt,dt){const r=orig(rt,dt);try{petSleepToyTick(rt)}catch(e){}return r}});
petPairWrap('petBrainScene',function(orig){return function(rt,o,at,cz){const r=orig(rt,o,at,cz);
  try{const t=rt.sideToy;if(t){const k=petDepthScalePct(t.z)/100;at(t.surf==='bed'?40.1:cz+0.004,function(){petBrainDrawToy(rt,o,t.k,t.x,t.y+1,k*0.9,t.x<rt.c.x)})}}catch(e){}
  return r}});

/* ---------- developer: test buttons ---------- */
function petDevSleepHidden(){
  const rt=PET_RT,p=petData();if(!rt||!rt.ready||!p)return;const c=rt.c;rt.dangle=null;rt.ball.on=false;
  c.surf='floor';c.boxSink=0;c.x=-120;c.z=Math.max(petZMinAt(-120),44);c.y=petFloorY(c.z);c.step=null;
  petInterrupt(rt,[petAct('sleep',60)]);toast('Lying down behind the cushion: he should shuffle into view first.');
}
function petDevSleepToy(){
  const rt=PET_RT,p=petData();if(!rt||!rt.ready||!p)return;const lk=petLikes(p);if(!petSleepToyOf(p)){lk.favToy='mouse';save()}
  const k=petSleepToyOf(p),b=petBrain(p),c=rt.c;rt.dangle=null;rt.ball.on=false;rt.sideToy=null;rt.toyCarry=null;petBR(rt).carry=null;
  b.items=(b.items||[]).filter(function(it){return it[0]!==k});const x=petClampHouse(c.x+(c.x<100?60:-60));b.items.push([k,Math.round(x),Math.max(40,petZMinAt(x)),0]);
  const q=petSpotPlan(rt,'bed',petRng(1));q.push(petAct('knead',3));q.push(petAct('sleep',60));c.step=null;c.plan=q;rt.blk={a:0,b:24,k:'sleep',spot:'bed'};petSleepToyPlan(rt);petInterrupt(rt,c.plan);
  toast('Bedtime: he fetches the '+PET_TOYS[k].name.toLowerCase()+' and takes it to bed.');
}
petDevTest('Asleep behind the cushion',petDevSleepHidden,'v81');
petDevTest('Bedtime with favourite toy',petDevSleepToy,'v81');
