ddMark(rt,x,y){
  rt.marks=rt.marks||[];rt.marks.push({x:Math.round(x),y:Math.round(y),t:rt.t,life:70,s:Math.floor(Math.random()*3)});
  if(rt.marks.length>16)rt.marks.shift();
}
function petDrawMarks(rt,o){
  const M=rt.marks;if(!M||!M.length)return;
  for(let k=M.length-1;k>=0;k--){
    const m=M[k],age=rt.t-m.t;if(age>m.life){M.splice(k,1);continue}
    const fade=age>m.life-14?(m.life-age)/14:1;
    for(let l=-1;l<=1;l++){                                  // three parallel claw lines
      const x0=m.x+l*3+(m.s-1)*0;
      for(let i=0;i<11;i++){
        const x=x0+Math.floor(i*0.28)+(l===1?Math.floor(i*0.1):0),y=m.y+i,a=(i<2||i>9?0.45:0.8)*fade;
        o.fillStyle='rgba(44,74,82,'+a+')';o.fillRect(x,y,1,1);
        o.fillStyle='rgba(236,246,238,'+(a*0.55)+')';o.fillRect(x+1,y,1,1);         // torn wallpaper edge
      }
    }
    if(age<1.3){                                             // a few flecks drift down
      for(let q=0;q<4;q++){const fx=m.x-3+q*3+((q*7+m.s)%3),fy=m.y+10+age*13+(q%2)*2;o.fillStyle='rgba(240,248,240,'+(1-age/1.3)+')';o.fillRect(fx,Math.round(fy),1,1)}
    }
  }
}
function petDrawCat(rt,o){
  const p=petData(),c=rt.c,A=rt.A,gi=petGrowIdx(p);let meta=A.anims[c.anim]||A.anims.idle_sit,sh=rt.sheets[p.coat],sc=1,W=A.W,H=A.H;
  const kz=petK(c);let k=kz;
  const dA=A.depth&&A.depth.anims[c.anim];let rigKind=gi<0?'adult':null;
  if(dA&&rt.dsheets&&rt.dsheets[p.coat]){meta=dA;sh=rt.dsheets[p.coat];W=A.depth.W;H=A.depth.H;rigKind='depth';
    if(gi>=0){sc=petSx(p);const ks=petDepthStageSheet(rt,p,gi);if(ks){sh=ks;rigKind='dst'+gi}else k=kz*sc}}   /* kittens have their own frames (big head); the adult ones shrunk until those load */
  else if(gi>=0){const g=petStageSheet(rt,p,gi);if(g){g.coat=p.coat;if(g.bottom[c.anim]==null&&g.bottom.idle_sit!=null){meta=A.anims.idle_sit}sh=g.cv;sc=g.sc;rigKind='st'+gi;meta=Object.assign({},meta,{bottom:g.bottom[c.anim]!=null?g.bottom[c.anim]:g.bottom.idle_sit})}}
  if(!sh||!meta)return;
  let f;if(c.anim==='leap'){const s=c.step;const u=s&&s.k==='hop'?Math.min(1,s.t/s.T):1;f=u<0.4?0:1}
  else if(c.anim==='swat'){const s0=c.step;f=Math.floor((s0&&s0.t||0)*meta.fps)%meta.n}
  else if(c.anim==='scratch_in'||c.anim==='scratch_out'){const s0=c.step;f=Math.min(meta.n-1,Math.floor((s0?s0.t:0)*meta.fps))}
  else if(c.anim==='stretch'||c.anim==='eat'||c.anim==='play'||c.anim==='groom'||c.anim==='knead'){f=Math.floor(c.t*meta.fps)%meta.n}
  else f=Math.floor(c.t*meta.fps*(c.fpsMul||1))%meta.n;
  if(PET_BLINK[c.anim]&&c.lock==null)f=petBlinkFrame(rt,c.anim,meta);   /* v78.1: eyes open, with a blink every few seconds instead of every loop */
  if(c.lock!=null)f=Math.min(meta.n-1,c.lock);                    /* v78: a held pose (doze, belly-up, a freeze) */
  const bv=petBrainVariant(rt,sh,meta.row,W,H,c.anim,p.coat),sy=bv?0:meta.row*H;
  const RG=typeof petRigPick==='function'?petRigPick(rt,p,sh,meta,c,f,bv,rigKind):null;   /* v94: the rig re-assembles the frame from its parts */
  if(bv)sh=bv;   /* v78: ears, tail tip, pupils */
  const bottom=meta.bottom||44;
  /* ground shadow: stays on the ground under a jump and shrinks with height */
  const s=c.step,hopping=s&&s.k==='hop'&&s.g0!=null;let gy=c.y,elev=0;
  if(hopping){const u=Math.min(1,s.t/s.T);gy=s.g0+(s.g1-s.g0)*u;elev=Math.max(0,(gy-c.y)*PET_DEPTH.ELEVATION_Y_MAX/PET_DEPTH.ELEV_PX)}
  const shk=petShadowScalePct(Math.round(kz*100),elev)/100;
  if(kz===1&&!hopping){const dx=Math.round(c.x)+(c.wx||0),wide=Math.round((/sleep|loaf|knead/.test(c.anim)?22:15)*sc);
    o.fillStyle='rgba(30,16,40,0.28)';o.fillRect(dx-wide,Math.round(c.y)-1,wide*2,2);o.fillRect(dx-wide+3,Math.round(c.y)+1,wide*2-6,1)}
  else{const q=function(v){return Math.round(v*PET_OS)/PET_OS},dx=c.x+(c.wx||0),wide=(/sleep|loaf|knead/.test(c.anim)?22:/walk_front|walk_back|sniff|bonk/.test(c.anim)?11:15)*sc*shk,hh=Math.max(1,2*shk);
    o.fillStyle='rgba(30,16,40,'+(0.28*Math.min(1,shk/kz+0.2)).toFixed(3)+')';o.fillRect(q(dx-wide),q(gy-hh/2),q(wide*2),q(hh));o.fillRect(q(dx-wide+3*shk),q(gy+hh/2),q(wide*2-6*shk),q(Math.max(1,shk)))}
  /* HD walking frames (twice the pixel density); anything not redrawn yet falls through to the original art */
  const HD=rigKind&&typeof petHdPick==='function'?petHdPick(rt,p,rigKind,petHdAnimOf(rt,rigKind,meta)||c.anim,f,meta,c):null;
  if(HD){const hs=HD.hs,hw=HD.cv.width/hs,hh=HD.cv.height/hs;
    if(k===1){const dy=Math.round(c.y-bottom+1),dx=Math.round(c.x)+(c.wx||0);o.save();o.translate(dx,0);if(c.dir<0)o.scale(-1,1);o.drawImage(HD.cv,0,0,HD.cv.width,HD.cv.height,-36-HD.ox/hs,dy-HD.oy/hs,hw,hh);o.restore();return}
    const X0=Math.round((c.x+(c.wx||0))*PET_OS)/PET_OS,Y0=Math.round(c.y*PET_OS)/PET_OS;
    o.save();o.translate(X0,Y0);o.scale(c.dir<0?-k:k,k);o.drawImage(HD.cv,0,0,HD.cv.width,HD.cv.height,-36-HD.ox/hs,-(bottom-1)-HD.oy/hs,hw,hh);o.restore();return}
  if(k===1){const dy=Math.round(c.y-bottom+1),dx=Math.round(c.x)+(c.wx||0);
    o.save();o.translate(dx,0);if(c.dir<0)o.scale(-1,1);if(RG)o.drawImage(RG.cv,0,0,RG.cv.width,RG.cv.height,-36-RG.ox,dy-RG.oy,RG.cv.width,RG.cv.height);else o.drawImage(sh,f*W,sy,W,H,-36,dy,W,H);o.restore();return}
  /* scaled: anchored at the feet, snapped to the 3x grid so pixels stay crisp */
  const X=Math.round((c.x+(c.wx||0))*PET_OS)/PET_OS,Y=Math.round(c.y*PET_OS)/PET_OS;
  o.save();o.translate(X,Y);o.scale(c.dir<0?-k:k,k);if(RG)o.drawImage(RG.cv,0,0,RG.cv.width,RG.cv.height,-36-RG.ox,-(bottom-1)-RG.oy,RG.cv.width,RG.cv.height);else o.drawImage(sh,f*W,sy,W,H,-36,-(bottom-1),W,H);o.restore();
}
/* ===== Pet screen: adoption, room, status, memorial, Overview card ===== */
var PET_LOOP={raf:0,last:0};
function petMoodText(rt,p){
  const st=petStage(p),n=p.name;
  if(st>=5)return n+' is very weak. Please feed '+n+'.';
  if(st===4)return n+' looks weak and tired. A meal would help a lot.';
  if(st===3)return n+' is very hungry and moving slowly.';
  if(st===2)return n+' is hungry. Maybe time for a meal?';
  return '';
}
function petAgo(ms){const h=ms/PET_H;if(h<1)return Math.max(1,Math.round(h*60))+' min ago';if(h<48)return (Math.round(h*10)/10)+' h ago';return Math.round(h/24)+' days ago'}
function petHeartsHtml(p){const L=petLevel(p);let r='';for(let i=1;i<=5;i++)r+='<span class="'+(i<=L?'on':'')+'">'+px('heart',16)+'</span>';return r}
function petWantRun(){const p=petData();if(!p||!p.alive||document.hidden)return false;const v=document.getElementById('view-pet'),d=document.getElementById('view-dashboard');return !!((v&&v.classList.contains('active')&&document.getElementById('petCanvas'))||(d&&d.classList.contains('active')&&document.getElementById('petPeekCanvas')))}
function petSync(){const want=petWantRun();if(want&&!PET_LOOP.raf){petInit().then(function(rt){PET_LOOP.last=performance.now();if(petWantRun()&&!PET_LOOP.raf){petMaybeGreet(rt);petBrainSession(rt);PET_LOOP.raf=requestAnimationFrame(petFrame)}})}else if(!want&&PET_LOOP.raf){cancelAnimationFrame(PET_LOOP.raf);PET_LOOP.raf=0;petSfxStopAll()}}
function petFrame(now){
  PET_LOOP.raf=0;if(!petWantRun())return;PET_LOOP.raf=requestAnimationFrame(petFrame);
  if(now-PET_LOOP.last<30)return;const dt=Math.min(0.1,(now-PET_LOOP.last)/1000);PET_LOOP.last=now;
  const rt=PET_RT;if(!rt||!rt.ready)return;petSeenNow(rt);petUpdate(rt,dt);petDepthTick(rt,dt);petCamUpdate(rt,dt);petDraw(rt);petBlit(rt);petRenderStatus(false);
}
/* ---------- camera: eases after the cat, leading a little in the direction it walks ---------- */
function petCamUpdate(rt,dt){
  const c=rt.c;if(!rt.cam)rt.cam={cx:c.x};
  const s=c.step,moving=s&&(s.k==='walk'||s.k==='stalk'||s.k==='hop');
  const target=c.x+(moving?c.dir*(s.run?26:14):0);
  const d=target-rt.cam.cx;
  if(Math.abs(d)>260){rt.cam.cx=target;return}            /* the cat was placed somewhere new: cut, don't pan */
  /* a cat that is resting never makes the room drift: small offsets are left alone, and once the camera is going it settles on a whole pixel */
  if(!moving&&!rt.camGo&&Math.abs(d)<14)return;
  rt.camGo=true;
  const k=s&&s.run?4.2:moving?2.6:1.6;
  rt.cam.cx+=d*(1-Math.exp(-k*dt));
  if(!moving&&Math.abs(target-rt.cam.cx)<0.6){rt.cam.cx=Math.round(target);rt.camGo=false}
}
/* left edge of a vw-wide view, kept inside the house */
function petCamLeft(rt,vw){const cx=rt.cam?rt.cam.cx:rt.c.x;return Math.max(PET_HX0,Math.min(PET_HX1-vw,cx-vw/2))}
/* draw the house from world x=left, vw wide, at scale s with the left edge at screen x=dx (sub-pixel smooth panning) */
function petDrawView(x,rt,left,vw,dx,dy,s){
  const ox=left-PET_HX0,sx=Math.floor(ox),fr=ox-sx,sw=Math.min(PET_HW-sx,Math.ceil(vw+fr)+1);
  x.drawImage(rt.off,sx*PET_OS,0,sw*PET_OS,144*PET_OS,Math.round(dx-fr*s),dy,Math.round(sw*s),Math.round(144*s));
}
/* the room card: whole-number pixel scale; a wide card shows more of the house instead of empty margins */
function petPaint(cv,rt){
  const box=cv.parentElement,cssW=Math.max(120,Math.floor(box.clientWidth||320)),d=window.devicePixelRatio||1,Wd=Math.floor(cssW*d),s=Math.max(1,Math.floor(Wd/192));
  if(cv._s!==s||cv._wd!==Wd){cv.width=Wd;cv.height=144*s;cv.style.width=(Wd/d)+'px';cv.style.height=(144*s/d)+'px';cv._s=s;cv._wd=Wd}
  const vw=Math.min(PET_HW,Wd/s),left=petCamLeft(rt,vw),x=cv.getContext('2d');x.imageSmoothingEnabled=false;
  const off=vw>=PET_HW?Math.floor((Wd-PET_HW*s)/2):0;
  if(off>0){x.drawImage(rt.edge,0,0,1,144,0,0,off,144*s);x.drawImage(rt.edge,PET_HW-1,0,1,144,off+PET_HW*s,0,cv.width-off-PET_HW*s,144*s)}
  petDrawView(x,rt,left,vw,off,0,s);
  cv._off=off;cv._camL=left;
}
function petRoomFullscreenOpen(){
  let overlay=document.getElementById('petRoomFullscreen');
  if(!overlay){
    overlay=document.createElement('div');
    overlay.id='petRoomFullscreen';
    overlay.innerHTML='<button type="button" class="pet-full-close" aria-label="Close full-screen room">✕ <span>Close</span></button><canvas id="petFullCanvas" aria-label="Mochi room full-screen"></canvas>';
    document.body.appendChild(overlay);
    overlay.querySelector('button').addEventListener('click',petRoomFullscreenClose);
    petBindCanvas(overlay.querySelector('canvas'));
  }
  overlay.classList.add('is-open');
  document.body.classList.add('pet-full-open');
  petBlitSoon();
}
function petRoomFullscreenClose(){
  const overlay=document.getElementById('petRoomFullscreen');
  if(overlay)overlay.classList.remove('is-open');
  document.body.classList.remove('pet-full-open');
}
function petPaintFullscreen(rt){
  const overlay=document.getElementById('petRoomFullscreen');
  if(!overlay||!overlay.classList.contains('is-open'))return;
  const cv=document.getElementById('petFullCanvas');
  const d=window.devicePixelRatio||1;
  const w=Math.max(1,Math.floor(window.innerWidth*d)),h=Math.max(1,Math.floor(window.innerHeight*d));
  if(cv.width!==w||cv.height!==h){cv.width=w;cv.height=h}
  const x=cv.getContext('2d');x.imageSmoothingEnabled=false;
  x.fillStyle='#10131c';x.fillRect(0,0,w,h);
  /* landscape screens see more of the house; the camera keeps following the cat */
  const scale=Math.min(w/192,h/144),vw=Math.min(PET_HW,w/scale),left=petCamLeft(rt,vw);
  const dw=Math.round(vw*scale),dh=Math.round(144*scale),ox=Math.floor((w-dw)/2),oy=Math.floor((h-dh)/2);
  x.save();x.beginPath();x.rect(ox,oy,dw,dh);x.clip();petDrawView(x,rt,left,vw,ox,oy,scale);x.restore();
  cv._s=scale;cv._off=ox;cv._offY=oy;cv._camL=left;
}
document.addEventListener('keydown',function(e){if(e.key==='Escape')petRoomFullscreenClose()});
function petBlit(rt){
  const cv=document.getElementById('petCanvas');
  if(cv&&cv.offsetParent!==null)petPaint(cv,rt);
  const pk=document.getElementById('petPeekCanvas');
  if(pk&&pk.offsetParent!==null)petPaintPeek(pk,rt);
  petPaintFullscreen(rt);
}
function petPaintPeek(cv,rt){
  // Overview shows a 192x144 window onto the house, scaled to the card
  // width, following the cat with the same camera as the Pet screen.
  const box=cv.parentElement;
  const cssW=Math.max(1,Math.floor(box.clientWidth||192));
  const d=window.devicePixelRatio||1;
  const pixelScale=Math.max(1,Math.floor(cssW*d/192));
  const drawW=192*pixelScale,drawH=144*pixelScale;
  if(cv.width!==drawW||cv.height!==drawH){
    cv.width=drawW;cv.height=drawH;
  }
  cv.style.width='100%';
  cv.style.height='auto';
  const x=cv.getContext('2d');
  x.imageSmoothingEnabled=false;
  x.clearRect(0,0,drawW,drawH);
  petDrawView(x,rt,petCamLeft(rt,192),192,0,0,pixelScale);
}
function petBindCanvas(cv){
  if(!cv||cv._bound)return;cv._bound=1;
  const pos=function(e){const r=cv.getBoundingClientRect(),s=cv._s||3,k=cv.width/r.width;return [((e.clientX-r.left)*k-(cv._off||0))/s+(cv._camL||0),((e.clientY-r.top)*k-(cv._offY||0))/s]};
  cv.addEventListener('pointerdown',function(e){if(!PET_RT||!PET_RT.ready)return;try{cv.setPointerCapture(e.pointerId)}catch(_){}const q=pos(e);petPointer(PET_RT,'down',q[0],q[1])});
  cv.addEventListener('pointermove',function(e){if(!PET_RT||!PET_RT.ready)return;const q=pos(e);petPointer(PET_RT,'move',q[0],q[1])});
  const up=function(e){if(!PET_RT||!PET_RT.ready)return;petPointer(PET_RT,'up',0,0)};
  cv.addEventListener('pointerup',up);cv.addEventListener('pointercancel',up);cv.addEventListener('pointerleave',up);
}
/* ---------- status panel text (updates in place, no rebuild) ---------- */
function petSet(id,t){const e=document.getElementById(id);if(e&&e.textContent!==t)e.textContent=t}
function petRenderStatus(force){
  const p=petData();if(!p||!p.alive)return;const rt=PET_RT,now=Date.now();petRollDay(p);
  const mood=petMoodText(rt,p);petSet('petMood',mood);
  const L=petLevel(p),next=L<5?PET_LEVELS[L+1]:null;
  petSet('petEyebrow',(petGrowIdx(p)>=0?'KITTEN · ':'COMPANION · ')+PET_LEVELS[L].toUpperCase());
  petGrowStatus(p,rt);
  petSet('petBondText',L>=5?'Devoted. '+p.name+' trusts you completely.':PET_LEVELS[L]+' · '+petLevelPct(p)+'% to '+next);
  const bar=document.getElementById('petBondBar');if(bar){const w=(L>=5?100:petLevelPct(p))+'%';if(bar.style.width!==w)bar.style.width=w}
  const hh=document.getElementById('petHearts');const key=String(L);if(hh&&hh.dataset.l!==key){hh.innerHTML=petHeartsHtml(p);hh.dataset.l=key}
  const fed=p.vacation.on?'paused':petAgo(now-p.lastFed);petSet('petStatFed',p.vacation.on?'Vacation':fed.replace(' ago',''));
  petSet('petStatFedL',p.vacation.on?'clock paused':'last meal');
  petSet('petStatToday',Math.min(p.fedToday,9)+' / 2');
  petSet('petStatDays',String(Math.max(1,Math.round((now-p.born)/864e5)+0)));
  const fb=document.getElementById('petFeedBtn');
  if(fb){const can=petCanFeed(p,now)&&!p.vacation.on;const lab=p.vacation.on?'Vacation mode':can?'Feed '+p.name:'Not hungry yet';if(fb.textContent!==lab)fb.textContent=lab;fb.classList.toggle('is-wait',!can)}
  const hint=document.getElementById('petHint');
  if(hint){let t='';if(L===0)t='Strays take a few days to trust you. Regular meals are how a cat learns you are safe.';else if(L===1)t='Hold a finger on '+p.name+' to pet. Short and gentle at first.';else if(petStage(p)>=2&&!p.vacation.on)t='A meal about twice a day is all '+p.name+' needs.';else if(L>=3)t='Try dragging your finger across the floor. '+p.name+' may follow.';if(hint.textContent!==t)hint.textContent=t}
  petRenderPeekText(p,mood);
  petLikesRender(force);
  petBrainRender(force);
}
/* ---------- the Pet screen ---------- */
function petCoatPreviewDraw(){
  if(!PET_RT||!PET_RT.ready)return;
  document.querySelectorAll('#petCoats canvas[data-coat]').forEach(function(cv){const sh=PET_RT.sheets[cv.dataset.coat],m=PET_RT.A.anims.idle_sit;if(!sh)return;const x=cv.getContext('2d');x.imageSmoothingEnabled=false;x.clearRect(0,0,cv.width,cv.height);x.drawImage(sh,0,m.row*PET_RT.A.H,PET_RT.A.W,PET_RT.A.H,0,0,cv.width,cv.height)});
}
function petPickCoat(c){document.querySelectorAll('#petCoats .pet-coat').forEach(function(b){b.classList.toggle('on',b.dataset.coat===c)});window.__petCoat=c}
function petPickTrait(t){document.querySelectorAll('#petTraits button').forEach(function(b){b.classList.toggle('on',b.dataset.t===t)});window.__petTrait=t}
function petAdoptClick(){
  const coat=window.__petCoat||'ginger',trait=window.__petTrait||'curious',name=(document.getElementById('petNameIn')||{}).value;
  const p=petAdopt(coat,name,trait);window.__petFresh=true;
  petInit().then(function(rt){rt.c.x=214;rt.c.surf='floor';rt.c.z=PET_DEPTH.ART_Z;rt.c.y=PET_SURF.floor.y;rt.c.dir=-1;petInterrupt(rt,[{k:'walk',x:110},{k:'face',dir:1},petAct('idle_sit',3),petAct('meow',2.4,{snd:'beg'})]);renderPetView(true);petSync()});
  toast(p.name+' has moved in.');
}
function petIntroHtml(){
  const mem=(state.petMemorial||[]).slice(-1)[0];
  const rand=PET_COATS[Math.floor(Math.random()*PET_COATS.length)][0];window.__petCoat=window.__petCoat||rand;window.__petTrait=window.__petTrait||'curious';
  return '<div class="card pet-intro"><div class="eyebrow">A VISITOR</div><h2>A stray kitten keeps stopping by</h2>'
   +'<p class="muted">It is tiny, and it sits outside the window most evenings. If you leave the door open, it might move in. It will grow up with you, a little bigger every day for a month. A kitten needs about two meals a day, a little attention, and someone to come back to.</p>'
   +'<div class="pet-coats" id="petCoats">'+PET_COATS.map(function(c){return '<button type="button" class="pet-coat'+(c[0]===window.__petCoat?' on':'')+'" data-coat="'+c[0]+'" onclick="petPickCoat(\''+c[0]+'\')"><canvas data-coat="'+c[0]+'" width="144" height="96"></canvas><span>'+c[1]+'</span></button>'}).join('')+'</div>'
   +'<label class="pet-lab" for="petNameIn">Name</label><input id="petNameIn" class="pet-in" maxlength="14" placeholder="'+PET_NAMES[Math.floor(Math.random()*PET_NAMES.length)]+'" autocomplete="off">'
   +'<label class="pet-lab">Personality</label><div class="pet-traits" id="petTraits">'+[['shy','Shy','hides, loves high places'],['playful','Playful','zoomies and toys'],['lazy','Lazy','naps a lot'],['curious','Curious','window and wandering']].map(function(t){return '<button type="button" data-t="'+t[0]+'" class="'+(t[0]===window.__petTrait?'on':'')+'" onclick="petPickTrait(\''+t[0]+'\')"><b>'+t[1]+'</b><small>'+t[2]+'</small></button>'}).join('')+'</div>'
   +'<button class="btn pet-go" onclick="petAdoptClick()">Welcome home</button>'
   +'<p class="muted" style="font-size:12px;margin:12px 0 0">No notifications, ever. A calm companion: feed it about twice a day. If you will be away, Vacation mode pauses its clock.'+(mem?' In memory: '+esc(mem.name)+'.':'')+'</p></div>';
}
function petRoomHtml(p){
  return '<div class="card pet-card"><div class="pet-head"><div><div class="eyebrow" id="petEyebrow">COMPANION</div><h2 id="petTitle">'+esc(p.name)+'</h2></div><div class="pet-hearts" id="petHearts"></div></div>'
   +'<div class="pet-stage pet-stage-open" onclick="petRoomFullscreenOpen()" role="button" tabindex="0" aria-label="Open full-screen room" onkeydown="if(event.key===\'Enter\'||event.key===\' \'){event.preventDefault();petRoomFullscreenOpen()}"><canvas id="petCanvas" aria-label="'+esc(p.name)+' in its room"></canvas><span class="pet-expand-hint">⛶ View room</span></div>'
   +'<div class="pet-mood" id="petMood"></div>'
   +'<div class="pet-mind" id="petMind"></div>'
   +'<div class="pet-bond"><div class="pet-bondbar"><i id="petBondBar" style="width:0%"></i></div><small id="petBondText"></small></div><div class="pet-grow" id="petGrow" style="display:none"></div>'
   +'<div class="pet-actions"><button class="btn" id="petFeedBtn" type="button" onclick="petFeedClick()">Feed</button><button class="btn secondary" type="button" onclick="petPlayClick()">Play</button><button class="btn secondary pet-blink-btn" type="button" onclick="petBlinkClick()" title="A slow blink is how cats say they feel safe">Slow blink</button></div>'
   +'<div class="pet-toys" id="petToys" hidden><div class="pet-toys-head"><b>Pick a toy</b><button type="button" class="pet-toys-x" onclick="petToyTray(false)" aria-label="Close toys">Close</button></div><div class="pet-toy-grid" id="petToyGrid"></div></div>'
   +'<div class="pet-stats"><div><b id="petStatFed">—</b><small id="petStatFedL">last meal</small></div><div><b id="petStatToday">0 / 2</b><small>meals today</small></div><div><b id="petStatDays">1</b><small>days together</small></div></div>'
   +'<div class="pet-favs" id="petFavs"></div>'
   +'<p class="pet-hint" id="petHint"></p></div>'
   +'<details class="card pet-more"><summary>Care and settings</summary>'
   +'<div class="pet-care"><p><b>How it works.</b> Feed about twice a day, at least four hours apart. A hungry cat tells you with its behavior, never with a notification. A cat that goes a very long time with no food will get weak, and about a week without food can be fatal.</p>'
   +'<p><b>Bond.</b> It grows from regular meals, gentle petting (3 a day count) and play (2 a day count). Strays are shy at first. Close friends greet you, follow your finger, rub against you and sometimes bring gifts.</p></div>'
   +'<div class="settingsline"><div style="flex:1"><strong>Vacation mode</strong><div class="muted" style="font-size:12px">Pauses hunger while you are away. Turn it off when you are back.</div></div><input type="checkbox" class="check" id="petVac" '+(p.vacation.on?'checked':'')+' onchange="petSetVacation(this.checked);petRenderStatus(true)"></div>'
   +'<div class="settingsline" style="margin-top:10px"><div style="flex:1"><label class="pet-lab" for="petRenameIn" style="margin:0 0 6px">Name</label><input id="petRenameIn" class="pet-in" maxlength="14" value="'+esc(p.name)+'" onchange="petRename(this.value);renderPetView(true)"></div></div>'
   +'<div class="pet-care" id="petLikes"></div>'
   +'<div class="pet-care pet-inner" id="petInner"></div>'
   +'<div id="petDev"></div></details>';
}
function petMemorialHtml(p){
  const days=Math.max(1,Math.round((p.passed-p.born)/864e5));
  return '<div class="card pet-intro pet-memorial"><div class="eyebrow">IN MEMORY</div><h2>'+esc(p.name)+'</h2><div class="pet-mem-art"><canvas id="petMemCanvas" width="144" height="96"></canvas></div>'
   +'<p class="muted">'+esc(p.name)+' is gone. You shared '+days+' day'+(days===1?'':'s')+' together, and '+esc(p.name)+' reached the "'+PET_LEVELS[petLevel(p)]+'" stage of trust. Cats need steady meals, and it had been too long. That is a hard thing, and it matters that you care.</p>'
   +'<button class="btn" onclick="petAdoptNew();renderPetView(true)">Open the door again</button></div>';
}
/* v79.3: every new cat feature registers a test button here (petDevTest), newest first */
var PET_DEV_TESTS=[];
function petDevTest(label,fn,since){PET_DEV_TESTS.push({label:label,fn:fn,since:since||''})}
function petDevRunTest(i){const t=PET_DEV_TESTS[i];if(!t||!PET_RT||!PET_RT.ready)return;try{t.fn()}catch(e){console.warn('[pet test]',t.label,e)}}
function petDevTestsHtml(){
  if(!PET_DEV_TESTS.length)return '';
  const order=PET_DEV_TESTS.map(function(t,i){return i}).reverse();
  return '<div style="margin-top:8px"><b style="font-size:13px">Test new features</b><div class="pet-devrow">'+order.map(function(i){const t=PET_DEV_TESTS[i];
    return '<button type="button" class="btn small" onclick="petDevRunTest('+i+')"'+(t.since?' title="added in '+esc(t.since)+'"':'')+'>'+esc(t.label)+'</button>'}).join('')+'</div></div>';
}
function petDevHtml(){
  if(!state.devMode)return '';
  return '<div class="note" style="margin-top:12px"><b>Developer: companion</b>'+petDevTestsHtml()+'<div class="pet-devrow">'
   +[['+4 h',4],['+8 h',8],['+1 day',24],['+3 days',72],['+6 days',144]].map(function(b){return '<button type="button" class="btn secondary small" onclick="petDevAge('+b[1]+')">'+b[0]+'</button>'}).join('')
   +'<button type="button" class="btn secondary small" onclick="petDevFed()">Reset meal</button><button type="button" class="btn secondary small" onclick="petDevGreet()">Greeting</button><button type="button" class="btn secondary small" onclick="petDevPounce()">Pounce</button><button type="button" class="btn secondary small" onclick="petDevScratch()">Scratch</button><button type="button" class="btn secondary small" onclick="petDevGrow(0)">Newborn</button><button type="button" class="btn secondary small" onclick="petDevGrow(5)">Age +5 days</button><button type="button" class="btn secondary small" onclick="petDevGrow(99)">Grown up</button><button type="button" class="btn secondary small" onclick="petDevPlay(1)">Roll</button><button type="button" class="btn secondary small" onclick="petDevPlay(2)">Kick</button><button type="button" class="btn secondary small" onclick="petDevPlay(3)">Feather</button>'
   +'<button type="button" class="btn secondary small" onclick="petDevBond(1)">Bond +1 level</button><button type="button" class="btn secondary small" onclick="petDevBond(-1)">Bond −1 level</button><button type="button" class="btn secondary small" onclick="petDevDepth(-1)">Walk to back</button><button type="button" class="btn secondary small" onclick="petDevDepth(1)">Walk to front</button><button type="button" class="btn secondary small" onclick="petDevDepth(2)">Visit the glass</button><button type="button" class="btn secondary small" onclick="petDevDepth(3)">Sit on cushion</button><button type="button" class="btn secondary small" onclick="petDevLikes(3)">Likes +3 days</button><button type="button" class="btn secondary small" onclick="petDevLikes(-1)">Reset likes</button></div><div class="muted" id="petDevDepth" style="font-size:12px;margin-top:6px"></div>'+petBrainDevHtml()+'<div id="petDevRoutine">'+petDevRoutine()+'</div></div>';
}
function petFmtH(h){const m=Math.round(h*60),hh=Math.floor(m/60)%24,mm=m%60;return (hh<10?'0':'')+hh+':'+(mm<10?'0':'')+mm}
function petDevRoutine(){
  const rt=PET_RT,p=petData();if(!rt||!rt.ready||!p)return '';
  const S=petSchedule(p,petDayKey()),b=petBlockNow(p),h=petHour();
  const lab={sleep:'sleeping',nap:'napping',rest:'resting',active:'active'};
  return '<div class="muted" style="font-size:12px;margin-top:8px">Now: <b>'+(b.k==='sleep'&&(b.a===0||b.b===24)?'night sleep':lab[b.k]||b.k)+'</b> until '+petFmtH(b.b)+' at '+b.spot+'</div><div class="pet-routine">'+S.blocks.map(function(x){const now=h>=x.a&&h<x.b;return '<div'+(now?' class="now"':'')+'><span>'+petFmtH(x.a)+'–'+petFmtH(x.b)+'</span><b>'+(x.k==='sleep'&&(x.a===0||x.b===24)?'night':x.k)+'</b><em>'+x.spot+'</em></div>'}).join('')+'</div>';
}
function petDevAge(h){const p=petData();if(!p)return;p.lastFed-=h*PET_H;const r=petCheck();save();if(r==='passed'){renderPetView(true);return}if(PET_RT&&PET_RT.ready)petPlaceForNow(PET_RT);petRenderStatus(true)}
function petDevPlay(n){if(PET_RT&&PET_RT.ready){const rt=PET_RT;rt.dangle=null;petInterrupt(rt,petPlayPlan(rt,['','roll','kick','bat'][n]))}}
function petDevGrow(m){const p=petData();if(!p)return;if(m===0)p.born=Date.now();else if(m===99)p.born=Date.now()-31*864e5;else p.born-=m*864e5;if(PET_RT){PET_RT.sched={};PET_RT.gsLast=null}save();if(PET_RT&&PET_RT.ready){petLoadStage(PET_RT,petGrowIdx(p));petRenderStatus(true)}toast('Age: day '+(Math.floor(petAgeDays(p))+1))}
function petDevScratch(){if(PET_RT&&PET_RT.ready){const rt=PET_RT,c=rt.c,q=petRoute(c,'floor',72);q.push({k:'face',dir:1});q.push(petAct('scratch_in',0.43));q.push(petAct('scratch',7));q.push(petAct('scratch_out',0.43));q.push(petAct('stretch',3.2));q.push(petAct('idle_sit',3));petInterrupt(rt,q)}}
function petDevGreet(){if(PET_RT&&PET_RT.ready)petMaybeGreet(PET_RT,true)}
function petDevPounce(){if(PET_RT&&PET_RT.ready){PET_RT.ball.on=false;petInterrupt(PET_RT,petPounceChain(PET_RT,false))}}
function petDevFed(){const p=petData();if(!p)return;p.lastFed=Date.now()-10*PET_H;p.fedToday=0;save();petRenderStatus(true)}
function petDevDepth(m){const rt=PET_RT;if(!rt||!rt.ready)return;const c=rt.c,x=petClampHouse(rt.cam?rt.cam.cx:c.x);rt.dangle=null;rt.ball.on=false;
  if(m===2){petInterrupt(rt,petLensPlan(rt));return}
  if(m===3){const sf=PET_SURF.cushion,q=petRoute(c,'cushion',(sf.x0+sf.x1)/2);q.push(petAct('loaf',20));petInterrupt(rt,q);return}
  const q=c.surf!=='floor'?petRoute(c,'floor',x):[];q.push({k:'walk',x:x+(m<0?20:-20),z:m<0?Math.max(petZMinAt(x),6):PET_DEPTH.DEPTH_TIER_2_MAX_Z});q.push(petAct('idle_sit',8));petInterrupt(rt,q)}
function petDevBond(d){const p=petData();if(!p)return;const l=Math.max(0,Math.min(5,petLevel(p)+d));p.bondPts=PET_BOND_AT[l];save();petRenderStatus(true)}
function renderPetView(force){
  const v=document.getElementById('view-pet');if(!v)return;
  const p=petData();const mode=!p?'intro':p.alive?'room':'memorial';
  const stamp=mode+(p?p.name+p.coat:'');
  if(!force&&v.dataset.stamp===stamp){if(mode==='room')petRenderStatus(false);return}
  v.dataset.stamp=stamp;
  if(mode==='intro'){v.innerHTML=petIntroHtml();petInit().then(petCoatPreviewDraw)}
  else if(mode==='memorial'){v.innerHTML=petMemorialHtml(p);petInit().then(function(rt){const cv=document.getElementById('petMemCanvas');if(!cv)return;const m=rt.A.anims.sleep_w,x=cv.getContext('2d');x.imageSmoothingEnabled=false;x.drawImage(rt.sheets[p.coat],0,m.row*rt.A.H,rt.A.W,rt.A.H,0,0,144,96)})}
  else{v.innerHTML=petRoomHtml(p);const dv=document.getElementById('petDev');if(dv)dv.innerHTML=petDevHtml();petBindCanvas(document.getElementById('petCanvas'));petInit().then(function(){petRenderStatus(true);petBlitSoon()})}
}
function petBlitSoon(){const rt=PET_RT;if(rt&&rt.ready){petDraw(rt);petBlit(rt)}}
async function petOpen(){
  const r=petCheck();if(r==='passed')toast((petData()||{name:'Your cat'}).name+' has passed away.');
  renderPetView(true);
  const p=petData();if(!p||!p.alive){petSync();return}
  const rt=await petInit();
  // a long absence earns a greeting
  petMaybeGreet(rt);
  window.__petFresh=false;petBlitSoon();petSync();
}
/* ---------- Overview card ---------- */
function petRenderPeekText(p,mood){
  petSet('petPeekMood',mood||'');
}
function renderPetPeek(){
  const el=document.getElementById('petPeek');if(!el)return;
  const p=petData();const mode=!p?'invite':p.alive?'alive':'gone';const stamp=mode+(p?p.name+p.coat+petLevel(p):'');
  if(el.dataset.stamp===stamp){petSync();return}
  el.dataset.stamp=stamp;
  if(mode==='invite'){el.innerHTML='<div class="pet-peek-in"><div class="pet-peek-ico">'+px('paw',22)+'</div><div style="flex:1;min-width:0"><div class="eyebrow">A VISITOR</div><strong>A stray kitten keeps stopping by</strong><div class="muted" style="font-size:12.5px;margin-top:2px">Open the door and see who it is.</div></div><span class="pet-peek-go">→</span></div>'}
  else if(mode==='gone'){el.innerHTML='<div class="pet-peek-in"><div class="pet-peek-ico">'+px('heart',22)+'</div><div style="flex:1"><div class="eyebrow">IN MEMORY</div><strong>'+esc(p.name)+'</strong></div><span class="pet-peek-go">→</span></div>'}
  else{el.innerHTML='<div class="pet-peek-stage"><canvas id="petPeekCanvas" aria-hidden="true"></canvas></div><div class="pet-peek-row"><div style="flex:1;min-width:0"><div class="eyebrow">COMPANION</div><strong>'+esc(p.name)+'</strong><div class="muted" id="petPeekMood" style="font-size:12.5px;margin-top:2px"></div></div><div class="pet-hearts small">'+petHeartsHtml(p)+'</div></div>';petInit().then(function(){petPlaceForNow&&0;petBlitSoon();petRenderStatus(true)})}
  petSync();
}
function renderPetAll(){renderPetPeek();renderPetView(false)}
document.addEventListener('pointerdown',function(){if(PET_RT&&PET_RT.ready)petSfxCtx(PET_RT)},{passive:true});
document.addEventListener('visibilitychange',function(){if(document.hidden&&PET_RT&&PET_RT.ready){petSeenNow(PET_RT);petSfxStopAll()}petSync();if(!document.hidden){const r=petCheck();if(r==='passed'){renderPetAll()}}});
setInterval(function(){const r=petCheck();if(r==='passed'){toast((petData()||{name:'Your cat'}).name+' has passed away.');renderPetAll()}},60000);

window.__petReady=true;try{petCheck();renderPetAll()}catch(e){console.warn('pet init',e)}

/* ===== Overview: a command center. Today ring, next moves, attribute tiles, week chart ===== */
var OV_SEL=6;
function ovKey(back){const d=new Date();d.setDate(d.getDate()-back);return d.toLocaleDateString('en-CA')}
function ovMap(){const m={};(state.activities||[]).forEach(function(a){const k=activityDate(a);m[k]=(m[k]||0)+(a.xp||0)});return m}
function ovSum(m,a,b){let s=0;for(let i=a;i<=b;i++)s+=m[ovKey(i)]||0;return s}
function ovGreeting(){const h=new Date().getHours(),n=(state.name||'').trim();const g=h<5?'Still up':h<12?'Good morning':h<17?'Good afternoon':h<22?'Good evening':'Late night';return n?g+', '+n:g}
function ovPace(m){const prev=ovSum(m,1,7);let active=0;for(let i=1;i<=7;i++)if(m[ovKey(i)])active++;if(active<2)return 100;return Math.max(60,Math.min(400,Math.round(prev/7/10)*10))}
function ovAgo(a){const t=new Date(a.date),n=new Date(),mins=Math.round((n-t)/6e4);if(mins<1)return 'just now';if(mins<60)return mins+' min ago';if(activityDate(a)===getToday())return Math.round(mins/60)+' h ago';if(activityDate(a)===ovKey(1))return 'Yesterday';return t.toLocaleDateString(undefined,{weekday:'short',month:'short',day:'numeric'})}
function ovAttrOf(id){return ATTRS.find(function(a){return a.id===id})||ATTRS[0]}
/* ---------- hero: today ring, level, streak, week ---------- */
function ovHeroHtml(){
  const lv=characterLevel(),title=titleFor(lv.level),cls=state.classId?classById(state.classId):null,D=chCompute(),m=ovMap(),today=m[getToday()]||0,pace=ovPace(m),R=62,gap=2.4;
  const parts=ATTRS.map(function(a){return {a:a,xp:D.per[a.id][6]}}).filter(function(x){return x.xp>0}).sort(function(x,y){return y.xp-x.xp});
  let acc=0,arcs='';const fill=Math.min(1,today/pace)*100;
  parts.forEach(function(x){const seg=Math.min(100-acc,x.xp/Math.max(today,1)*fill);if(seg<=0)return;const len=Math.max(0.5,seg-(parts.length>1?gap:0));arcs+='<circle class="ov-seg" cx="74" cy="74" r="'+R+'" pathLength="100" stroke="'+ATTR_COL[x.a.id]+'" style="--c:'+ATTR_COL[x.a.id]+'66" stroke-dasharray="'+len.toFixed(2)+' '+(100-len).toFixed(2)+'" stroke-dashoffset="'+(-acc).toFixed(2)+'"/>';acc+=seg});
  const over=today>pace?Math.round((today/pace-1)*100):0;
  const wk=ovSum(m,0,6),prev=ovSum(m,7,13),delta=prev>0?Math.round((wk-prev)/prev*100):null;
  let dots='';for(let i=6;i>=0;i--)dots+='<i class="'+(m[ovKey(i)]?'on':'')+(i===0?' now':'')+'"></i>';
  return '<div class="ov-hero-main"><div class="ov-ring"><svg viewBox="0 0 148 148" aria-hidden="true"><circle class="ov-ring-track" cx="74" cy="74" r="'+R+'"/><g transform="rotate(-90 74 74)"><g class="ov-segs">'+arcs+'</g></g></svg>'
   +'<div class="ov-ring-c"><div><small>TODAY</small><b id="ovToday" data-v="'+today+'" class="'+(over?'over':'')+'">'+today+'</b><span>of '+pace+' XP'+(over?' · +'+over+'%':'')+'</span></div></div></div>'
   +'<div class="ov-meta"><div class="ov-lvrow"><span class="ov-ava"'+(state.avatar?' style="background-image:url(&quot;'+state.avatar+'&quot;)"':'')+'>'+(state.avatar?'':esc(((state.name||'?').trim()[0]||'?').toUpperCase()))+'</span><span class="ov-lvbadge">'+lv.level+'</span><div style="min-width:0;flex:1"><div class="ov-title">'+title.toUpperCase()+(cls?'<br><span>'+cls.name.toUpperCase()+'</span>':'')+'</div></div><span class="ov-go" aria-hidden="true">›</span></div><div class="ov-xpt"><span>'+lv.into.toLocaleString()+' / '+lv.needed.toLocaleString()+' XP</span><span>LV '+(lv.level+1)+' →</span></div>'
   +'<div class="xpbar ov-xpbar"><div class="xpfill" data-w="'+lv.pct.toFixed(1)+'%" style="width:'+lv.pct.toFixed(1)+'%"></div></div>'
   +'<div class="ov-streak"><span class="ov-flame">'+px('flame',16)+'</span><b>'+streak()+'</b><span>day streak</span><span class="ov-dots">'+dots+'</span></div></div></div>'
   +'<div class="ov-hero-foot"><div><small>THIS WEEK</small><b>+'+wk.toLocaleString()+' XP</b>'+(delta===null?'':'<em class="'+(delta>=0?'up':'dn')+'">'+(delta>=0?'▲ ':'▼ ')+Math.abs(delta)+'%</em>')+'</div><div><small>TOTAL XP</small><b>'+totalXP().toLocaleString()+'</b></div><div><small>ACHIEVEMENTS</small><b>'+Object.keys(state.badges||{}).length+' / '+BADGES.length+'</b></div></div>';
}
/* ---------- next moves ---------- */
function ovMo