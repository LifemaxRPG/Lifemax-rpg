owlWait(rt){
  const c=rt.c,q=petRoute(c,'floor',PET_EATX);q.push({k:'face',dir:PET_EATDIR});q.push(petAct('meow',petRand(4,8),{bubble:'bubble',snd:'beg'}));return q;
}
function petPlan(rt,kind){
  if(kind==='ball')return petPounceChain(rt,false);
  return [];
}
/* a toy on the floor: slides, hops and bounces off the walls */
function petToyUpdate(rt,dt){
  const b=rt.ball;if(!b.on)return;b.vx=b.vx||0;b.vy=b.vy||0;b.h=b.h||0;
  b.x+=b.vx*dt;b.vx*=Math.exp(-((b.col==='mouse'||b.col==='feather')?4.6:b.col==='yarn'?0.9:1.5)*dt);if(Math.abs(b.vx)<2)b.vx=0;
  if(b.x<32){b.x=32;b.vx=Math.abs(b.vx)*0.6}else if(b.x>156){b.x=156;b.vx=-Math.abs(b.vx)*0.6}
  b.vy-=320*dt;b.h+=b.vy*dt;if(b.h<=0){b.h=0;if(b.vy<-45)b.vy=-b.vy*(b.col==='spring'?0.62:0.36);else b.vy=0}
  if(Math.abs(b.vx)>8)b.flip=b.vx<0;
}
function petKnock(rt,dir){const b=rt.ball,c=rt.c;if(!b.on)return;b.vx=dir*petRand(78,128);b.vy=petRand(75,115)*(b.col==='spring'?1.45:1);b.flip=dir<0;for(let i=0;i<5;i++)petSpawn(rt,'sparkle',b.x+petRand(-5,5),PET_SURF.floor.y-12,petRand(-14,14),petRand(-26,-8),0.9);haptic(7)}
/* stalk, settle, wiggle, pounce: the classic sequence, once or twice */
function petPounceChain(rt,keep,opt){
  const c=rt.c,b=rt.ball;opt=opt||{};
  if(!keep||!b.on){b.on=true;b.x=petRand(72,128);b.vx=0;b.vy=0;b.h=0;b.t=0;b.col=opt.col||petLikesPickToy(rt,'chase');b.flip=false}
  const col=b.col;
  const q=petRoute(c,'floor',petClampX(b.x>96?b.x-72:b.x+72));
  const rounds=opt.rounds||(1+(Math.random()<0.55?1:0)+(petLikes(petData()).favToy===col?1:0));
  for(let i=0;i<rounds;i++){q.push({k:'stalk',off:36});q.push(petAct('crouch',petRand(0.4,0.7)));q.push(petAct('crouch',0.7,{wiggle:1}));q.push({k:'pounce'});q.push(petAct('play',petRand(2.4,3.6)))}
  q.push(petAct('idle_sit',2,{endBall:1}));if(!opt.col||opt.solo)q.push({k:'fn',f:function(){petLikesPlayed(col,false)}});return q;
}
/* ---------- greeting you when you come back ---------- */
var PET_AWAY_MS=30*60*1000;
function petSeenNow(rt){rt.lastSeen=Date.now();if(Date.now()-(rt.seenSaved||0)>20000){rt.seenSaved=Date.now();try{localStorage.setItem('lm_pet_seen',String(Date.now()))}catch(e){}}}
function petGreetPlan(rt){
  const lens=petDepthGreet(rt);if(lens)return lens;
  const c=rt.c,p=petData(),L=petLevel(p),q=[petAct('stretch',3.4)];
  q.push.apply(q,petDepthSetLastWalkZ(petRoute(c,'floor',96),Math.min(petDepthMaxZ(p),60)));q.push({k:'face',dir:1});
  q.push(petAct('lookup',1.9));
  if(Math.random()<(L>=4?0.55:0.4))q.push(petAct('meow',1.8,{snd:L>=3?'purreow':'idle'}));else q.push(petAct('slowblink',2.5));
  q.push(petAct('idle_sit',3));return q;
}
function petMaybeGreet(rt,force){
  const p=petData();if(!p||!p.alive||!rt||!rt.ready)return false;const now=Date.now();
  const away=rt.lastSeen?now-rt.lastSeen:0,stale=!!rt.lastSeen&&away>=10*60000&&!window.__petFresh;
  const resync=function(){if(stale){petPlaceForNow(rt);rt.lastSeen=now}return false};
  if(!force){
    if(!rt.lastSeen||away<PET_AWAY_MS||window.__petFresh)return resync();
    if(rt.greetedAt&&now-rt.greetedAt<PET_AWAY_MS)return resync();
    const L=petLevel(p),blk=petBlockNow(p);
    if(L<2||petStage(p)>=3)return resync();
    if(blk.k==='sleep')return resync();                       // never wake a cat from its night
    if(blk.k==='nap'&&L<3)return resync();                    // only close friends are woken from a nap
  }
  petPlaceForNow(rt);rt.lastSeen=now;                          // he is where the routine says he would be now
  rt.greetedAt=now;rt.awakeUntil=petNow()+4*60*1000;
  petInterrupt(rt,petBrainGreet(rt,petGreetPlan(rt)));return true;
}
/* ---------- growing up: a kitten becomes a cat over 30 days ---------- */
var PET_GROW_DAYS=30,PET_GJ=null;
function petGrowthJson(){if(PET_GJ)return PET_GJ;const e=document.getElementById('petGrowth');if(!e)return null;try{PET_GJ=JSON.parse(e.textContent)}catch(x){PET_GJ=null}return PET_GJ}
function petAgeDays(p){return Math.max(0,(petNow()-p.born)/864e5)}
function petGrowth(p){return Math.min(1,petAgeDays(p)/PET_GROW_DAYS)}
function petGrowIdx(p){const d=petAgeDays(p);return d>=PET_GROW_DAYS?-1:Math.min(5,Math.floor(d/5))}
async function petLoadStage(rt,idx){
  if(idx<0||!rt||!rt.A)return;const p=petData(),G=petGrowthJson();if(!p||!G||!G.stages[idx])return;
  rt.gs=rt.gs||{};const key=idx+'|'+p.coat;if(rt.gs[key]||(rt.gsBusy&&rt.gsBusy[key]))return;rt.gsBusy=rt.gsBusy||{};rt.gsBusy[key]=1;
  try{const st=G.stages[idx],img=await petLoadImg(st.coats[p.coat]),cv=document.createElement('canvas');cv.width=img.width;cv.height=img.height;
    const cx=cv.getContext('2d',{willReadFrequently:true});cx.drawImage(img,0,0);const id=cx.getImageData(0,0,cv.width,cv.height),d=id.data,pal=rt.A.pal[p.coat];
    for(let i=0;i<d.length;i+=4){const g=d[i];if(g===0){d[i+3]=0;continue}const c=pal[String(g)];if(c){d[i]=c[0];d[i+1]=c[1];d[i+2]=c[2];d[i+3]=255}else d[i+3]=0}
    cx.putImageData(id,0,0);rt.gs[key]={cv:cv,bottom:st.bottom,sc:st.sx}}catch(e){}
  delete rt.gsBusy[key];
}
function petStageSheet(rt,p,idx){const g=rt.gs&&rt.gs[idx+'|'+p.coat];if(g){rt.gsLast=g;return g}petLoadStage(rt,idx);return (rt.gsLast&&rt.gsLast.coat===p.coat)?rt.gsLast:null}
function petGrowStatus(p,rt){
  const gi=petGrowIdx(p),mark=gi<0?6:gi,el=document.getElementById('petGrow');
  if(p.growSeen==null){p.growSeen=mark;save()}
  else if(mark>p.growSeen){
    p.growSeen=mark;save();
    if(rt&&rt.ready){for(let i=0;i<7;i++)petSpawn(rt,'sparkle',rt.c.x+petRand(-16,16),rt.c.y-petRand(8,34),petRand(-10,10),petRand(-34,-10),1.3);petSpawn(rt,'heart',rt.c.x,rt.c.y-40*petK(rt.c),0,-12,1.6)}
    toast(mark>=6?p.name+' is all grown up!':p.name+' is growing up. A little bigger today.');haptic(12);
  }
  if(el){
    let h='';
    if(gi>=0){const d=Math.floor(petAgeDays(p)),left=Math.max(1,PET_GROW_DAYS-d);h='<div class="pet-grow-row"><span>'+(d<10?'NEW KITTEN':d<21?'GROWING KITTEN':'ALMOST GROWN')+'</span><span>DAY '+(d+1)+' OF '+PET_GROW_DAYS+'</span></div><div class="pet-grow-bar"><i style="width:'+Math.round(petGrowth(p)*100)+'%"></i></div><small>Grown up in '+left+(left===1?' day':' days')+'. Kittens play more, nap more and have higher little voices.</small>'}
    if(el.__h!==h){el.innerHTML=h;el.__h=h;el.style.display=h?'':'none'}
  }
}
/* ---------- playing: belly-up roll, bunny kick and the dangling feather ---------- */
function petSx(p){const i=petGrowIdx(p);if(i<0)return 1;const G=petGrowthJson();return G&&G.stages[i]?G.stages[i].sx:1}
function petPlayPlan(rt,kind){
  const c=rt.c,p=petData(),L=petLevel(p),sx=petSx(p),pause=function(){return petAct('idle_sit',petRand(8,16))},q=[];
  if(kind==='roll'){q.push.apply(q,petRoute(c,'floor',petRand(88,122)));q.push(petAct('roll',petRand(5,9),{snd:L>=3?'purreow':'idle'}));q.push(petAct('stretch',3.2));q.push(pause())}
  else if(kind==='kick'){q.push.apply(q,petRoute(c,'floor',petRand(66,118)));q.push({k:'face',dir:1});
    q.push({k:'fn',f:function(){const b=rt.ball;b.on=true;b.col=petLikesPickToy(rt,'chase',['mouse','ball_a','ball_b','yarn']);rt.kickCol=b.col;b.x=c.x+18.2*sx;b.vx=0;b.vy=0;b.h=0;b.flip=false}});
    q.push(petAct('kick',petRand(4,8),{endBall:1}));q.push({k:'fn',f:function(){petLikesPlayed(rt.kickCol,false,0.7)}});q.push(pause())}
  else if(kind==='bat'){q.push.apply(q,petRoute(c,'floor',petRand(60,112)));q.push({k:'face',dir:1});
    q.push({k:'fn',f:function(){rt.dangle={x:c.x,ang:0,vel:0,fade:0}}});
    q.push(petAct('scratch_in',0.43));q.push(petAct('bat',petRand(6,10)));q.push(petAct('scratch_out',0.43));
    q.push({k:'fn',f:function(){if(rt.dangle)rt.dangle.fade=3.5;if(!rt.wandFromYou)petLikesPlayed('wand',false);rt.wandFromYou=false}});q.push(petAct('stretch',3.2));q.push(pause())}
  return q;
}
function petDangleUpdate(rt,dt){
  const d=rt.dangle;if(!d)return;const L=Math.max(30,rt.c.y-34*petSx(petData()));
  d.vel+=(-(300/L)*Math.sin(d.ang)-0.7*d.vel)*dt;d.ang=Math.max(-0.5,Math.min(0.5,d.ang+d.vel*dt));
  if(d.fade>0){d.fade-=dt;if(d.fade<=0)rt.dangle=null}
}
function petDrawDangle(rt,o){
  const d=rt.dangle;if(!d)return;const L=rt.c.y-34*petSx(petData()),fx=d.x+L*Math.sin(d.ang),fy=L*Math.cos(d.ang);
  o.globalAlpha=d.fade>0&&d.fade<1?d.fade:1;
  const n=Math.ceil(L);o.fillStyle='rgba(236,239,250,0.9)';
  for(let i=0;i<=n;i++){const t=i/n;o.fillRect(Math.round(d.x+(fx-d.x)*t),Math.round(fy*t),1,1)}
  petItem(rt,o,'feather',Math.round(fx-6),Math.round(fy-1),d.ang<0);o.globalAlpha=1;
}
function petInterrupt(rt,plan){const c=rt.c;if(rt.inLens&&!(plan&&plan.lens))rt.inLens=false;c.plan=plan;c.step=null;c.hop=null}   /* v78: an interrupted glass visit no longer blocks later ones */
function petPlaceForNow(rt){
  const p=petData();if(!p||!rt.ready)return;const c=rt.c,stage=petStage(p),blk=petBlockNow(p),r=petRng(petHash(p.born+'|'+Math.floor(petNow()/60000)+'|place')),h=petHour();
  c.plan=[];c.step=null;c.hop=null;rt.restBlock=null;rt.wasSleeping=false;
  if(stage>=3){c.surf='floor';c.z=PET_DEPTH.ART_Z;c.y=PET_SURF.floor.y;c.x=PET_EATX;c.dir=PET_EATDIR;c.plan=[{k:'face',dir:PET_EATDIR},petAct('loaf',petRand(14,26),{bubble:'bubble'})];return}
  const snap=function(surf,x){c.surf=surf;c.x=x;c.z=PET_SURF[surf]&&PET_SURF[surf].z!=null?PET_SURF[surf].z:PET_DEPTH.ART_Z;c.y=PET_SURF[surf].y;c.dir=r()<0.5?1:-1;if(surf==='floor')petDepthPlaceFloor(rt,c,r)};
  if(blk.k==='sleep'||blk.k==='nap'||blk.k==='rest'){
    const sp=blk.spot;
    if(PET_SPOT_X[sp])PET_SPOT_X[sp].place(rt,c,r,snap);
    else if(sp==='bed')snap('bed',36);else if(sp==='sill')snap('sill',40+Math.round(r()*26));
    else if(sp==='tree'){if(r()<0.5)snap('top',151+Math.round(r()*7));else snap('mid',148+Math.round(r()*10))}
    else if(sp==='couch')snap('couch',-130+Math.round(r()*64));else if(sp==='counter')snap('counter',290+Math.round(r()*46));
    else if(sp==='living')snap('floor',-146+Math.round(r()*110));else if(sp==='kitchen')snap('floor',214+Math.round(r()*120));
    else snap('floor',sp==='rug'?92+Math.round(r()*30):44+Math.round(r()*100));
    if(blk.k==='sleep'||blk.k==='nap'){c.plan=[petAct('sleep',Math.min(900,Math.max(8,(blk.b-h)*3600)),{until:blk.b})];rt.wasSleeping=true}
    else{rt.restBlock=blk.a;c.plan=[petAct(petSpotRestAct(sp),petRand(25,60))]}
  }else{snap('floor',50+Math.round(r()*80));c.plan=[petAct('idle_sit',petRand(4,10))]}
}
/* ---------- simulation ---------- */
function petStartStep(rt,s){
  const c=rt.c;s.t=0;c.fpsMul=1;c.wx=0;c.lock=null;rt.ballFront=false;petBrainStartStep(rt,s);
  if(!(rt.hold&&rt.hold.on)&&!(s.k==='act'&&(s.a==='purr'||s.a==='knead')))petPurr(rt,0);
  if(s.k==='walk'){petPairWalkStart(rt,s);petDepthStartWalk(rt,s)}   /* v80: two cats keep a little apart */
  else if(s.k==='stalk'){const b=rt.ball,side=b.x>=c.x?-1:1;s.x=petClampX(b.x+side*s.off);c.dir=s.x>=c.x?1:-1;c.fpsMul=0.45;s.limit=Math.abs(s.x-c.x)/14*2.4+4}
  else if(s.k==='pounce'){const b=rt.ball;c.dir=b.x>=c.x?1:-1;s.k='hop';s.s='floor';s.x=petClampX(b.x-c.dir*17);s.hh=15;s.knock=true;petStartStep(rt,s);return}
  else if(s.k==='hop'){if(petPairHopStart(rt,s))return;if(petBoxHopStart(rt,s))return;   /* v80: a spot the other cat is on */const sf=PET_SURF[s.s],y1=petSurfY(s.s,c.z),x1=s.x==null?c.x:s.x;const dx=x1-c.x,dy=y1-c.y;s.x0=c.x;s.y0=c.y;s.g0=c.y;s.g1=y1;s.x1=x1;s.y1=y1;s.T=0.42+0.0035*Math.abs(dx)+0.004*Math.abs(dy);s.h=s.hh||(10+Math.max(0,-dy)*0.35);if(Math.abs(dx)>1)c.dir=dx>0?1:-1;c.anim='leap'}
  else if(s.k==='act'){c.anim=petAnimName(rt,s.a);if(s.flip)c.dir*=-1;if(s.bubble&&petStage(petData())>=2)rt.bubble={s:'bubble',t:0,d:s.d};if(s.snd){petMeow(typeof s.snd==='string'?s.snd:'idle');petSpawn(rt,'note',c.x+(c.dir>0?20:-20)*petK(c),c.y-40*petK(c),3,-10,1.5)}if(s.a==='play'&&rt.ball.on)rt.ball.t=0}
  else if(s.k==='face'){c.dir=s.dir}
  else if(s.k==='fn'){s.f();}
}
function petUpdate(rt,dt){
  const p=petData();if(!p||!p.alive||!rt.ready)return;petToyUpdate(rt,dt);petDangleUpdate(rt,dt);petBoxTick(rt,dt);petPurrTick(rt);const c=rt.c,stage=petStage(p),weak=stage>=3;rt.t+=dt;c.t+=dt;
  // holding a finger on the cat: handled separately
  if(rt.hold&&rt.hold.on){petUpdateHold(rt,dt);return}
  if(!c.step){c.step=c.plan.shift();if(!c.step){petThink(rt);c.step=c.plan.shift()}if(c.step)petStartStep(rt,c.step)}
  const s=c.step;if(!s){c.anim=petAnimName(rt,'idle_sit');return}
  if(s.k==='walk'){petDepthWalk(rt,s,dt,weak)}else if(s.k==='stalk'){
    const dx=s.x-c.x,m=14*dt;s.t+=dt;c.anim=petAnimName(rt,'walk');c.fpsMul=0.45;
    if(Math.abs(dx)<=m){c.x=s.x;c.dir=rt.ball.x>=c.x?1:-1;c.step=null}else{c.x+=Math.sign(dx)*m;c.dir=Math.sign(dx)}
    if(s.t>s.limit){c.dir=rt.ball.x>=c.x?1:-1;c.step=null}
  }else if(s.k==='hop'&&s.box){petBoxJumpTick(rt,s,dt)   /* v79.2: into and out of the box */
  }else if(s.k==='hop'){
    s.t+=dt;const u=Math.min(1,s.t/s.T);c.x=s.x0+(s.x1-s.x0)*u;c.y=s.y0+(s.y1-s.y0)*u-4*s.h*u*(1-u);c.anim='leap';
    if(u>=1){c.x=s.x1;c.y=s.y1;c.surf=s.s;if(PET_SURF[s.s]&&PET_SURF[s.s].z!=null)c.z=PET_SURF[s.s].z;c.step=null;if(s.knock){petKnock(rt,c.dir)}}
  }else if(s.k==='act'){
    s.t+=dt;c.anim=petAnimName(rt,s.a);c.wx=s.wiggle?((Math.floor(s.t*30)%2)?1:-1):0;petBrainAct(rt,s,dt);
    if(s.a==='purr')petPurr(rt,0.6);else if(s.a==='knead')petPurr(rt,0.3);
    if(s.a==='scratch'){
      const meta=rt.A.anims.scratch,T=c.t*meta.fps/meta.n,kR=Math.floor(T-0.15),kL=Math.floor(T-0.65);
      if(s.kR==null){s.kR=kR;s.kL=kL}
      const dyF=Math.round(c.y-meta.bottom+1);
      if(kR!==s.kR){s.kR=kR;petAddMark(rt,c.x+8.0+Math.random()*3-1.5,dyF+4+Math.random()*3-1.5)}
      if(kL!==s.kL){s.kL=kL;petAddMark(rt,c.x-8.0+Math.random()*3-1.5,dyF+4+Math.random()*3-1.5)}
    }
    if(s.a==='kick'&&rt.ball.on){const b=rt.ball;b.x=c.x+c.dir*18.2*petSx(petData())+Math.sin(c.t*24)*0.8;b.vx=0;b.h=Math.max(0,Math.sin(c.t*12*Math.PI/3))*1.6;b.flip=c.dir<0}
    if(s.swat||s.pat||s.hug)petPlayTick(rt,s,dt);
    if(s.sink!=null)petBoxSinkTick(rt,s,dt);   /* v79.2: depth in the box */   /* v79: floor swats, pats, belly-up juggle */
    if(s.a==='bat'&&rt.dangle){const d=rt.dangle,meta=rt.A.anims.bat,T=c.t*meta.fps/meta.n,kL=Math.floor(T-0.35),kR=Math.floor(T-0.85);if(s.kL==null){s.kL=kL;s.kR=kR}if(kL!==s.kL){s.kL=kL;d.vel-=0.55}if(kR!==s.kR){s.kR=kR;d.vel+=0.55}}
    if(s.a==='eat'){if(s.nextSfx==null)s.nextSfx=0.25;if(s.t>=s.nextSfx){petPlay(rt,'eat',0.9);s.nextSfx+=1.2+Math.random()*0.35}}
    if(s.bubble&&petStage(petData())>=2){if(s.nextBeg==null)s.nextBeg=0.5;if(s.t>=s.nextBeg){petPlay(rt,'beg',0.85);s.nextBeg+=petRand(7,12)}}
    if(s.ball&&rt.ball.on){const b=rt.ball;b.t+=dt;if(Math.floor(b.t*2.2)%2===0)b.vx=14*c.dir*0.6;else b.vx=-6*c.dir*0.5;b.x=Math.max(30,Math.min(150,b.x+b.vx*dt))}
    if(s.a==='sleep'&&Math.floor(rt.t*0.8)!==Math.floor((rt.t-dt)*0.8))petSpawn(rt,'zzz'+(Math.floor(rt.t*0.8)%3),c.x+(c.dir>0?18:-18)*petK(c),c.y-34*petK(c),-3*c.dir,-9,2.6);
    if(s.a==='purr'&&Math.floor(rt.t*1.2)!==Math.floor((rt.t-dt)*1.2))petSpawn(rt,'note',c.x+(c.dir>0?20:-20)*petK(c),c.y-38*petK(c),3,-10,1.6);
    if(s.until!=null&&petHour()>=s.until&&s.until>=1&&petHour()<s.until+1)s.t=s.d;
    if(s.t>=s.d){if(s.a==='purr'||s.a==='knead')petPurr(rt,0);if(s.endBall)rt.ball.on=false;if(s.endBowl)rt.bowl.food=false;c.step=null;c.wx=0;petPlayStepEnd(rt,s);if(s.a==='play'&&(s.countPlay!==false)||s.countPlay){const r=petPlayDone();if(r.counted){save();if(r.levelUp)petLevelUp(rt,r.levelUp)}}}
  }else if(s.k==='face'||s.k==='fn'){c.step=null}
  if(rt.bowl.food){const eating=c.step&&c.step.k==='act'&&c.step.a==='eat';rt.bowl.idle=eating?0:(rt.bowl.idle||0)+dt;if(rt.bowl.idle>14){rt.bowl.food=false;rt.bowl.idle=0}}
  // bubble timer
  if(rt.bubble){rt.bubble.t+=dt;if(rt.bubble.t>rt.bubble.d)rt.bubble=null}
  // particles
  for(let i=rt.parts.length-1;i>=0;i--){const q=rt.parts[i];q.age+=dt;q.x+=q.vx*dt;q.y+=q.vy*dt;if(q.age>=q.life)rt.parts.splice(i,1)}
  // keep on a surface
  if(c.step&&c.step.k!=='hop'){const sf=PET_SURF[c.surf];if(c.surf==='bed'){c.y=sf.y}else if(sf){c.y=petSurfY(c.surf,c.z);if(sf.x0!=null)c.x=Math.max(sf.x0-6,Math.min(sf.x1+6,c.x))}}
}
function petSpawn(rt,spr,x,y,vx,vy,life){rt.parts.push({s:spr,x:x,y:y,vx:vx||0,vy:vy||-12,life:life||1.4,age:0})}
function petLevelUp(rt,lv){
  const p=petData(),c=rt.c;for(let i=0;i<5;i++)petSpawn(rt,'heart_big',c.x+petRand(-14,14),c.y-30*petK(c)+petRand(-6,6),petRand(-8,8),petRand(-26,-14),1.8);
  toast(p.name+' trusts you more now: '+PET_LEVELS[lv]);haptic([10,40,10]);petBrainEvent('levelup');
}
/* ---------- touch: hold to pet, tap the bowl or toy, follow a finger ---------- */
function petCatBox(rt){const c=rt.c,k=petK(c),sk=(c.boxSink||0)*k;
  if(sk>0&&typeof petBoxGeom==='function'){const g=petBoxGeom(rt);if(g)return {x0:c.x-22*k,x1:c.x+22*k,y0:c.y+sk-48*k,y1:Math.min(c.y+sk+3*k,g.rimY+3)}}   /* v79.2: sunk in the box, only what shows */
  return {x0:c.x-26*k,x1:c.x+26*k,y0:c.y-38*k,y1:c.y+3*k}}
function petHit(rt,x,y){
  const b=petCatBox(rt);
  if(x>=b.x0&&x<=b.x1&&y>=b.y0&&y<=b.y1)return 'cat';
  const f=PET_BOWL.food;if(x>=f.x-10&&x<=f.x+10&&y>=f.top-4&&y<=f.top+14)return 'bowl';
  if(rt.ball.on&&Math.abs(x-rt.ball.x)<8&&y>=PET_SURF.floor.y-10)return 'ball';
  if(rt.gift&&Math.abs(x-rt.gift.x)<9&&y>=PET_SURF.floor.y-12)return 'gift';
  return 'room';
}
function petPointer(rt,type,x,y){
  const p=petData();if(!p||!p.alive||!rt.ready)return;const c=rt.c,L=petLevel(p);
  if(type==='down'){
    const hit=petHit(rt,x,y);petDepthPointer(rt,'down',x,hit);if(petBrainTouch(rt,hit,x,y))return;
    if(hit==='bowl'){petFeedClick();return}
    if(hit==='ball'){petInterrupt(rt,petPounceChain(rt,true));return}
    if(hit==='gift'){rt.gift=null;p.gifts=(p.gifts||0)+1;petBrainEvent('gift');for(let i=0;i<4;i++)petSpawn(rt,'sparkle',x+petRand(-8,8),y-8,petRand(-10,10),petRand(-24,-10),1.2);save();toast(p.name+' brought you a present. '+p.gifts+' so far.');return}
    if(hit==='cat'){
      const sleeping=c.anim.indexOf('sleep')===0||!!(c.step&&c.step.k==='act'&&c.step.a==='sleep'),doze=sleeping&&petBrainDozing(rt);   /* v78: a dozing cat is drawn loafing */
      if(L===0){ // a stranger startles and runs to hide
        petPlay(rt,'hiss',0.95);rt.bubble=null;petSpawn(rt,'exclaim',c.x+(c.dir>0?10:-10)*petK(c),c.y-42*petK(c),0,-6,1.0);
        const away=petClampHouse(c.x+(c.dir>0?-1:1)*40),q=petRoute(c,'floor',away);q.forEach(function(z){if(z.k==='walk')z.run=1});
        if(Math.random()<0.5){q.push.apply(q,petRoute({surf:'floor',x:away},'bed',36))}else{q.push.apply(q,petRoute({surf:'floor',x:away},'mid',152))}
        q.push(petAct('loaf',petRand(8,14)));petInterrupt(rt,q);return}
      const keep=sleeping&&L>=3&&!doze;                               /* v78: a light doze always wakes, with a trill */
      rt.hold={on:true,t:0,since:rt.t,x:x,moved:false,beats:0,L:L,flee:L===1,keepSleep:keep};
      c.step=null;c.plan=[];c.hop=null;if(sleeping&&!keep){rt.hold.woke=1;if(!doze)petSpawn(rt,'exclaim',c.x,c.y-42*petK(c),0,-6,0.8)}
      haptic(8);return}
    if(L>=2&&c.surf==='floor'&&petStage(p)<3){rt.follow={x:x,t:0};petInterrupt(rt,[{k:'walk',x:petClampHouse(x-20),z:petDepthTapZ(rt,x,y)}])}
  }else if(type==='move'){
    petDepthPointer(rt,'move',x);
    if(rt.hold&&rt.hold.on){rt.hold.moved=true}
    else if(rt.follow&&L>=2&&petStage(p)<3){rt.follow.x=x;if(!rt.followNext||rt.t-rt.followNext>0.35){rt.followNext=rt.t;petInterrupt(rt,[{k:'walk',x:petClampHouse(x-20),z:petDepthTapZ(rt,x,y)}])}}
  }else if(type==='up'){
    petDepthPointer(rt,'up',0);rt.follow=null;
    if(rt.hold&&rt.hold.on){
      const h=rt.hold;rt.hold=null;petPurr(rt,0);
      if(h.t>=1.1){const r=petPetDone();if(r.counted){petLikesPetted(rt);save();if(r.levelUp)petLevelUp(rt,r.levelUp)}}
      c.step=null;c.plan=h.keepSleep?[]:[petAct(L>=3?'slowblink':'idle_sit',2.4)];petBrainRelease(rt,h)
    }
  }
}
function petUpdateHold(rt,dt){
  const p=petData(),c=rt.c,h=rt.hold,L=h.L;h.t+=dt;
  // the cat's response depends on how much it trusts you
  if(L===1&&h.t>2.6){petPurr(rt,0);const q=petRoute(c,'floor',c.x+(c.dir>0?-1:1)*34);rt.hold=null;petSpawn(rt,'exclaim',c.x,c.y-42*petK(c),0,-6,0.8);petInterrupt(rt,[{k:'walk',x:petClampHouse(c.x+(c.dir>0?-40:40)),run:1},petAct('idle_sit',3)]);return}
  const stage=petStage(p);
  c.anim=h.keepSleep?(stage>=3?'sleep_w':'sleep'):stage>=3?'idle_sit_w':(L===1?'idle_sit':L===2?'slowblink':'purr');
  if(L===1&&!h.keepSleep)c.anim='idle_sit';
  if(h.t>0.45&&L>=2&&stage<3)petPurr(rt,h.keepSleep?0.3:L>=4?0.95:L===3?0.75:0.5);else if(L<2||stage>=3)petPurr(rt,0);
  if(petBrainHold(rt,dt))return;                                  /* v78: saturation, mood-dependent response, overstimulation */
  const beat=L===1?1.6:0.85;
  if(Math.floor(h.t/beat)!==h.beats){h.beats=Math.floor(h.t/beat);if(h.t>0.5){petSpawn(rt,L>=4?'heart_big':'heart',c.x+(c.dir>0?14:-14)*petK(c)+petRand(-6,6),c.y-40*petK(c),petRand(-5,5),-14,1.5);haptic(6);if(L>=4&&Math.random()<0.5)petSpawn(rt,'note',c.x+petRand(-12,12),c.y-44*petK(c),2,-10,1.4)}}
  for(let i=rt.parts.length-1;i>=0;i--){const q=rt.parts[i];q.age+=dt;q.x+=q.vx*dt;q.y+=q.vy*dt;if(q.age>=q.life)rt.parts.splice(i,1)}
}
/* ---------- care actions called from the UI ---------- */
function petFeedClick(){
  const rt=PET_RT,p=petData();if(!p||!rt||!rt.ready)return;
  if(!p.alive)return;
  const r=petFeed();
  if(!r.ok){
    if(r.why==='vacation')toast('Vacation mode is on. Turn it off to feed '+p.name+'.');
    else{const eating=rt.c.step&&rt.c.step.k==='act'&&rt.c.step.a==='eat';const h=Math.ceil(r.inHours*10)/10;toast(p.name+' is not hungry yet. A meal in about '+(h<1?Math.max(1,Math.round(h*60))+' min':(Math.round(h*10)/10)+' h')+' is just right.');
      if(!eating&&!rt.bowl.food)petInterrupt(rt,petRoute(rt.c,'floor',PET_EATX).concat([{k:'face',dir:PET_EATDIR},petAct('idle_sit',1.6),petAct('groom',3)]))}
    petRenderStatus();return}
  rt.bowl.food=true;haptic([8,30,8]);petBrainEvent('feed');
  save();
  petInterrupt(rt,petRoute(rt.c,'floor',PET_EATX).concat([{k:'face',dir:PET_EATDIR},petAct('eat',7.5,{endBowl:1}),petAct('groom',4.5)]));
  rt.eatPending=true;if(petLevel(p)>=1)petPlay(rt,petLevel(p)>=3?'purreow':'idle',0.9,{delay:0.25});
  if(r.levelUp)petLevelUp(rt,r.levelUp);
  petRenderStatus();
}
function petPlayClick(){
  const rt=PET_RT,p=petData();if(!p||!rt||!rt.ready||!p.alive)return;
  if(petStage(p)>=3){toast(p.name+' is too tired to play. A meal first.');return}
  petToyTray();            /* Play opens the toy tray; picking a toy starts the game */
}
/* ---------- sound: real recorded clips, balanced at playback ---------- */
var PET_SFX_VOL={beg:0.8,idle:0.75,purreow:0.8,purr:0.6,eat:0.7,hiss:0.55},PET_MASTER=0.9;
function petSfxCtx(rt){
  if(!rt||!fxOpt().sound)return null;
  try{const C=window.AudioContext||window.webkitAudioContext;if(!C)return null;rt.ac=rt.ac||new C();if(rt.ac.state==='suspended')rt.ac.resume();return rt.ac}catch(e){return null}
}
function petB64(d){const b=atob(d.split(',')[1]),u=new Uint8Array(b.length);for(let i=0;i<b.length;i++)u[i]=b.charCodeAt(i);return u.buffer}
async function petSfxLoad(rt){
  if(rt.sfx||rt.sfxLoading||rt.sfxFailed)return;const ac=petSfxCtx(rt);if(!ac)return;rt.sfxLoading=true;
  try{
    const J=JSON.parse(document.getElementById('petSounds').textContent),out={};
    for(const k of Object.keys(J)){out[k]=[];for(const c of J[k]){try{out[k].push({buf:await ac.decodeAudioData(petB64(c.d)),gain:c.g})}catch(e){}}}
    rt.sfx=out;rt.sfxLast={};
  }catch(e){rt.sfxFailed=true}
  rt.sfxLoading=false;
}
function petPlay(rt,kind,vol,opt){
  const ac=petSfxCtx(rt);if(!ac)return null;
  if(!rt.sfx){petSfxLoad(rt);return null}
  if(ac.state!=='running')return null;
  const arr=rt.sfx[kind];if(!arr||!arr.length)return null;
  let i=Math.floor(Math.random()*arr.length);if(arr.length>1&&i===rt.sfxLast[kind])i=(i+1)%arr.length;rt.sfxLast[kind]=i;
  const clip=arr[i],src=ac.createBufferSource(),g=ac.createGain();
  const pp=petData(),kitP=pp&&kind!=='purr'&&kind!=='eat'&&kind!=='hiss'?Math.max(0,1-petGrowth(pp)):0;
  src.buffer=clip.buf;src.playbackRate.value=((opt&&opt.rate)||(0.96+Math.random()*0.08))*(1+0.30*kitP)*petBrainPitch(kind);
  g.gain.value=clip.gain*(PET_SFX_VOL[kind]||0.7)*(vol==null?1:vol)*PET_MASTER;
  src.connect(g);g.connect(ac.destination);src.start(ac.currentTime+((opt&&opt.delay)||0));return {src:src,g:g};
}
/* purring: clips are chained and crossfaded so it sounds like one continuous purr for as long as you hold the cat */
function petPurr(rt,vol){
  const P=rt.purr=rt.purr||{on:false,vol:0,nodes:[],nextAt:0,last:-1};
  if(!vol){
    if(P.on){P.on=false;const ac=rt.ac;if(ac){const now=ac.currentTime;P.nodes.forEach(function(n){try{n.g.gain.cancelScheduledValues(now);n.g.gain.setValueAtTime(n.g.gain.value,now);n.g.gain.linearRampToValueAtTime(0,now+0.45);n.src.stop(now+0.5)}catch(e){}})}P.nodes=[]}
    return;
  }
  const ac0=petSfxCtx(rt);if(!ac0)return;if(!rt.sfx){petSfxLoad(rt);return}if(ac0.state!=='running')return;
  P.vol=vol;if(!P.on){P.on=true;P.nextAt=0}
}
function petPurrTick(rt){
  const P=rt.purr;if(!P||!P.on||!rt.ac||!rt.sfx||!rt.sfx.purr||!rt.sfx.purr.length)return;
  const ac=rt.ac,now=ac.currentTime;
  P.nodes=P.nodes.filter(function(n){return n.end>now});
  if(P.nextAt>now+0.12&&P.nodes.length)return;
  const arr=rt.sfx.purr;let i=Math.floor(Math.random()*arr.length);if(arr.length>1&&i===P.last)i=(i+1)%arr.length;P.last=i;
  const clip=arr[i],src=ac.createBufferSource(),g=ac.createGain(),dur=clip.buf.duration,xf=0.4,start=Math.max(now+0.02,P.nextAt-xf),peak=clip.gain*PET_SFX_VOL.purr*P.vol*PET_MASTER;
  src.buffer=clip.buf;g.gain.setValueAtTime(0,start);g.gain.linearRampToValueAtTime(peak,start+xf);g.gain.setValueAtTime(peak,start+dur-xf);g.gain.linearRampToValueAtTime(0,start+dur);
  src.connect(g);g.connect(ac.destination);src.start(start);P.nodes.push({src:src,g:g,end:start+dur});P.nextAt=start+dur;
}
function petSfxStopAll(rt){rt=rt||PET_RT;if(!rt)return;try{petPurr(rt,0)}catch(e){}}
/* the old synthesised meow stays as a fallback in case the recordings cannot be decoded */
function petMeow(kind){
  const rt=PET_RT;if(!rt)return;
  if(rt.sfx&&!rt.sfxFailed){petPlay(rt,kind||'idle');return}
  if(!rt.sfxFailed&&fxOpt().sound){petSfxLoad(rt);return}
  if(!fxOpt().sound)return;try{const C=window.AudioContext||window.webkitAudioContext;if(!C)return;rt.ac=rt.ac||new C();const a=rt.ac;if(a.state==='suspended')a.resume();
    const t=a.currentTime,o=a.createOscillator(),g=a.createGain();o.type='triangle';o.frequency.setValueAtTime(520,t);o.frequency.linearRampToValueAtTime(860,t+0.12);o.frequency.linearRampToValueAtTime(480,t+0.42);
    g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(0.045,t+0.06);g.gain.exponentialRampToValueAtTime(0.0001,t+0.46);o.connect(g);g.connect(a.destination);o.start(t);o.stop(t+0.5)}catch(e){}
}
/* ---------- drawing ---------- */
var PET_SKY=[[0,[10,14,44],[28,38,92]],[5,[22,26,70],[96,70,120]],[6.2,[70,90,170],[246,160,120]],[8,[104,170,232],[206,234,255]],[12,[88,166,232],[190,228,255]],[17,[110,160,226],[255,218,160]],[18.8,[56,56,128],[255,138,90]],[20.5,[22,26,72],[84,60,120]],[22,[10,14,44],[28,38,92]],[24,[10,14,44],[28,38,92]]];
function petSkyAt(h){let a=PET_SKY[0],b=PET_SKY[1];for(let i=0;i<PET_SKY.length-1;i++)if(h>=PET_SKY[i][0]&&h<=PET_SKY[i+1][0]){a=PET_SKY[i];b=PET_SKY[i+1];break}const t=(h-a[0])/Math.max(0.001,b[0]-a[0]);const m=function(x,y){return [0,1,2].map(function(k){return Math.round(x[k]+(y[k]-x[k])*t)})};return [m(a[1],b[1]),m(a[2],b[2])]}
function petTint(h){
  if(h>=7.5&&h<17)return [255,255,255];
  if(h>=17&&h<19.5)return [255,226,200];
  if(h>=19.5&&h<21.5){const t=(h-19.5)/2;return [Math.round(255-t*120),Math.round(226-t*98),Math.round(200-t*20)]}
  if(h>=21.5||h<5)return [128,138,196];
  if(h>=5&&h<7.5){const t=(h-5)/2.5;return [Math.round(128+t*127),Math.round(138+t*117),Math.round(196+t*59)]}
  return [255,255,255]}
function petItem(rt,o,k,x,y,flip){const m=rt.A.room.meta[k];if(!m){petXItem(rt,o,k,x,y,flip);return}x=Math.round(x);y=Math.round(y);if(flip){o.save();o.translate(x+m[2],0);o.scale(-1,1);o.drawImage(rt.items,m[0],m[1],m[2],m[3],0,y,m[2],m[3]);o.restore()}else o.drawImage(rt.items,m[0],m[1],m[2],m[3],x,y,m[2],m[3])}
function petDraw(rt){
  const o=rt.octx,p=petData(),c=rt.c,h=petHour(),T=rt.t;
  o.setTransform(PET_OS,0,0,PET_OS,0,0);o.imageSmoothingEnabled=false;
  o.clearRect(0,0,PET_HW,144);
  o.save();o.translate(-PET_HX0+petParBg(rt),0);
  // sky behind each window (every window has the same 64x56 glass as the bedroom's)
  const sk=petSkyAt(h),night=h>=19.5||h<5.5;
  for(const wx of PET_WINDOWS){o.save();o.translate(wx,0);o.beginPath();o.rect(22,14,64,56);o.clip();
  for(let y=14;y<70;y++){const t=Math.min(5,Math.floor((y-14)/56*6))/5;const col=[0,1,2].map(function(k){return Math.round(sk[0][k]+(sk[1][k]-sk[0][k])*t)});o.fillStyle='rgb('+col.join(',')+')';o.fillRect(22,y,64,1)}
  if(night||h<7||h>=18.5){for(let i=0;i<16;i++){const sx=24+((i*37+11)%60),sy=16+((i*23+5)%34),a=0.5+0.5*Math.sin(T*1.7+i*1.9);if(!night&&(h>=7&&h<18.5))continue;o.fillStyle='rgba(255,255,230,'+(night?(a>0.35?1:0.35):0.25)+')';o.fillRect(sx,sy,i%5===0?2:1,i%5===0?2:1)}}
  const arcH=function(a,b){return (h-a+24)%24/(b-a)};
  if(h>=6&&h<19){const u=(h-6)/13;petItem(rt,o,'sun',22+u*58-9,62-Math.sin(u*Math.PI)*42)}
  else{const u=((h>=19?h-19:h+5)/11);petItem(rt,o,'moon',22+u*58-7,62-Math.sin(u*Math.PI)*40)}
  if(!night){petItem(rt,o,'cloud1',22+((T*1.6+wx*0.37)%110)-30,22);petItem(rt,o,'cloud2',22+((T*1.0+70+wx*0.21)%110)-24,40)}
  o.restore()}
  if(rt.living)o.drawImage(rt.living,-192,0);
  o.drawImage(rt.bg,0,0);
  if(rt.kitchen)o.drawImage(rt.kitchen,192,0);
  petDrawMarks(rt,o);
  petDrawDangle(rt,o);
  // light on the floor from the window
  for(const wx of [PET_WINDOWS[0],PET_WINDOWS[1]]){
  if(h>=7.5&&h<17.5){o.fillStyle='rgba(255,236,160,0.16)';o.beginPath();o.moveTo(wx+30,118);o.lineTo(wx+90,118);o.lineTo(wx+112,142);o.lineTo(wx+52,142);o.closePath();o.fill()}
  else if(night){o.fillStyle='rgba(150,170,255,0.12)';o.beginPath();o.moveTo(wx+30,118);o.lineTo(wx+90,118);o.lineTo(wx+112,142);o.lineTo(wx+52,142);o.closePath();o.fill()}}
  // furniture behind the cat
  rt.ectx.clearRect(0,0,PET_HW,144);rt.ectx.drawImage(rt.off,0,0,PET_HW*PET_OS,144*PET_OS,0,0,PET_HW,144);
  // furniture, toys, props and the cat, back to front by depth
  petDepthScene(rt,o,p);
  o.restore();
  // time-of-day tint
  const tn=petTint(h);if(tn[0]<250||tn[1]<250){o.globalCompositeOperation='multiply';o.fillStyle='rgb('+tn.join(',')+')';o.fillRect(0,0,PET_HW,144);o.globalCompositeOperation='source-over';const e=rt.ectx;e.globalCompositeOperation='multiply';e.fillStyle='rgb('+tn.join(',')+')';e.fillRect(0,0,PET_HW,144);e.globalCompositeOperation='source-over';
    // the living-room lamp is on in the evening
    if(rt.living){const lx=-172-PET_HX0,ly=34,g=o.createRadialGradient(lx,ly,2,lx,ly,46);const a=Math.min(0.32,(255-tn[2]+255-tn[1])/420);g.addColorStop(0,'rgba(255,214,140,'+a+')');g.addColorStop(1,'rgba(255,214,140,0)');o.fillStyle=g;o.fillRect(lx-46,0,92,144);o.fillStyle='rgba(255,236,180,'+(a*1.6)+')';o.fillRect(lx-7,26,15,16)}}
  petDepthGlass(rt,o);
}
/* ---------- claw marks on the wall (low-bond cats scratch it) ---------- */
function petA