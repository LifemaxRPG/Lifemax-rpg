
/* ===== Pet room depth (Feature 1.3 upgrade): a 2.5D stage with posX / posZ / elevationY =====
   The room is still one pixel-art canvas, so depth is drawn painter-style (back to front by posZ)
   instead of with CSS z-index planes. posZ 0 = the back wall, 100 = pressed against the screen.
   Scale uses the brief's formula exactly. Feet height uses a one-point perspective calibrated to
   this house: a cat at posZ 35 stands at y 132 at 100%, exactly where the cat always stood. */
var PET_OS=4;                       /* the room is drawn at 4x so a scaled cat stays crisp (4 = two canvas pixels per pixel of a 2x HD sprite) */
var PET_DEPTH={
  POS_X_MIN:-100,POS_X_MAX:100,POS_Z_MIN:0,POS_Z_MAX:100,ELEVATION_Y_MIN:0,ELEVATION_Y_MAX:40,
  SCALE_AT_Z_MIN_PCT:55,SCALE_Z_RANGE_PCT:130,
  WALK_SPEED_BASE_PCT:70,WALK_SPEED_Z_GAIN_PCT:60,
  SHADOW_MIN_SCALE_PCT:30,SHADOW_ELEV_SHRINK_MULT:2,
  PLANE_0_Z_INDEX:15,PLANE_1_Z_INDEX:45,PLANE_2_Z_INDEX:75,PLANE_3_Z_INDEX:95,LENS_FOG_Z_INDEX:98,
  PARALLAX_PLANE_0_PX:2,PARALLAX_PLANE_1_PX:6,PARALLAX_PLANE_2_PX:12,PARALLAX_PLANE_3_PX:16,PARALLAX_MAX_OFFSET_PX:16,
  DEPTH_TIER_1_MAX_Z:55,DEPTH_TIER_2_MAX_Z:84,DEPTH_TIER_3_MAX_Z:100,ZONE_2_TRUST_REQ:30,ZONE_3_TRUST_REQ:65,
  LENS_VISIT_PROB:30,LENS_TARGET_Z:95,LENS_SNIFF_MS:1200,LENS_FOG_DURATION_MS:1800,LENS_FOG_OPACITY_PCT:25,
  LENS_BONK_NUDGE_PX:3,IDLE_APPROACH_WAIT_SEC:20,
  /* not in the brief; added so an idle player is not visited every 20 s */
  LENS_COOLDOWN_SEC:120,
  /* this room's calibration */
  ART_Z:35,           /* the depth the existing room art is drawn at (100% scale) */
  FLOOR_Y_ART:132,    /* feet line at ART_Z, same as PET_SURF.floor.y */
  HORIZON_Y:98,       /* camera eye level in room pixels; chosen so a cat at the glass shows his whole face */
  ELEV_PX:68,         /* floor to the top of the cat tree = elevationY 40 */
  Z_SPEED:20          /* posZ units per second at a 100% walk multiplier */
};
function petClampN(v,a,b){return Math.max(a,Math.min(b,v))}
/* ---------- projection ---------- */
function petDepthScalePct(z){const D=PET_DEPTH;z=petClampN(z,D.POS_Z_MIN,D.POS_Z_MAX);return D.SCALE_AT_Z_MIN_PCT+Math.floor(z*D.SCALE_Z_RANGE_PCT/100)}
function petFloorY(z){const D=PET_DEPTH;return D.HORIZON_Y+(D.FLOOR_Y_ART-D.HORIZON_Y)*petDepthScalePct(z)/100}
function petZFromY(y){const D=PET_DEPTH,s=(y-D.HORIZON_Y)/(D.FLOOR_Y_ART-D.HORIZON_Y)*100;return petClampN((s-D.SCALE_AT_Z_MIN_PCT)*100/D.SCALE_Z_RANGE_PCT,D.POS_Z_MIN,D.POS_Z_MAX)}
function petWalkSpeedPct(z){const D=PET_DEPTH;return D.WALK_SPEED_BASE_PCT+Math.floor(petClampN(z,0,100)*D.WALK_SPEED_Z_GAIN_PCT/100)}
function petShadowScalePct(scalePct,elev){const D=PET_DEPTH;return Math.max(D.SHADOW_MIN_SCALE_PCT,scalePct-elev*D.SHADOW_ELEV_SHRINK_MULT)}
function petK(c){return petDepthScalePct(c&&c.z!=null?c.z:PET_DEPTH.ART_Z)/100}
function petZoneIndex(z){return z<=20?0:z<=55?1:z<=84?2:3}          /* legacy zoneIndex, derived */
function petPosXOf(x){const mid=(PET_HMIN+PET_HMAX)/2,half=(PET_HMAX-PET_HMIN)/2;return Math.round(petClampN((x-mid)/half*100,-100,100))}
function petXOfPosX(px){const mid=(PET_HMIN+PET_HMAX)/2,half=(PET_HMAX-PET_HMIN)/2;return mid+petClampN(px,-100,100)/100*half}
function petElevOf(c){const g=petFloorY(c.z);return Math.round(petClampN((g-c.y)*PET_DEPTH.ELEVATION_Y_MAX/PET_DEPTH.ELEV_PX,0,PET_DEPTH.ELEVATION_Y_MAX))}
/* everything the brief's tests ask about, for one moment */
function petDepthProject(z,elevY){
  const k=petDepthScalePct(z),fy=petFloorY(z),ey=(elevY||0)*PET_DEPTH.ELEV_PX/PET_DEPTH.ELEVATION_Y_MAX;
  return {spriteScalePct:k,feetY:fy-ey,screenTopPct:Math.round((fy-ey)/144*100),shadowY:fy,shadowScalePct:petShadowScalePct(k,elevY||0),zIndex:Math.round(z),zoneIndex:petZoneIndex(z),walkSpeedPct:petWalkSpeedPct(z)};
}
/* ---------- trust and how far forward he will come ---------- */
function petTrust(p){if(!p)return 0;const L=petLevel(p);return L>=5?100:Math.round(L*20+petLevelPct(p)*0.2)}
function petDepthMaxZ(p){const D=PET_DEPTH;if(!p||petStage(p)>=3)return D.ART_Z;const t=petTrust(p);return t>=D.ZONE_3_TRUST_REQ?D.DEPTH_TIER_3_MAX_Z:t>=D.ZONE_2_TRUST_REQ?D.DEPTH_TIER_2_MAX_Z:D.DEPTH_TIER_1_MAX_Z}
/* furniture painted into the wall art cannot hide the cat, so he must stay in front of it.
   Each entry is [x0, x1, y of the lowest pixel of that piece in the art]. A cat whose feet are
   above that line would look as if he were standing on its side. */
var PET_FLOOR_BACK_Y=118;            /* the skirting board meets the floor here in all three rooms */
var PET_Z_BLOCK=[[-180,-165,129],[-162,-30,128],[-26,-8,126],[10,66,137],[136,182,137],[206,246,128],[254,374,128]];
var PET_Z_GAP=3;                     /* feet stay this many pixels in front of the furniture's base */
function petZMinAt(x){
  let m=petZFromY(PET_FLOOR_BACK_Y+2);
  for(const b of PET_Z_BLOCK)if(x>=b[0]-10&&x<=b[1]+10)m=Math.max(m,Math.min(PET_DEPTH.ART_Z,petZFromY(b[2]+PET_Z_GAP)));
  return Math.ceil(m);
}
/* the highest zMin along a straight floor path */
function petZNeed(x0,x1){let m=0;const a=Math.min(x0,x1),b=Math.max(x0,x1);for(let x=a;x<=b;x+=4)m=Math.max(m,petZMinAt(x));return Math.max(m,petZMinAt(b))}
/* kitten frames, one sheet per growth stage */
function petDepthStageSheet(rt,p,gi){
  const S=rt.A.depth&&rt.A.depth.stages;if(!S||!S[gi])return null;rt.dsg=rt.dsg||{};const key=gi+'|'+p.coat,e=rt.dsg[key];
  if(e)return e.cv||null;rt.dsg[key]={};
  petLoadImg(S[gi].coats[p.coat]).then(function(img){const cv=document.createElement('canvas');cv.width=img.width;cv.height=img.height;
    const cx=cv.getContext('2d',{willReadFrequently:true});cx.drawImage(img,0,0);const id=cx.getImageData(0,0,cv.width,cv.height),d=id.data,pal=rt.A.pal[p.coat];
    for(let i=0;i<d.length;i+=4){const g=d[i];if(g===0){d[i+3]=0;continue}const c=pal[String(g)];if(c){d[i]=c[0];d[i+1]=c[1];d[i+2]=c[2];d[i+3]=255}else d[i+3]=0}
    cx.putImageData(id,0,0);rt.dsg[key].cv=cv});
  return null;
}
/* ---------- free-standing props: the cat can walk behind them, in front of them, or sit on them ---------- */
var PET_PROPS=[{k:'pouf',x:-34,z:45,top:5,half:12},{k:'cushion',x:-120,z:66,top:7,half:18},
  {k:'box',x:290,z:42,top:3.6,half:20,parts:['box_back','box_front']}];          /* the box is sat in, so it draws behind and in front of him */
for(const pr of PET_PROPS){const k=petDepthScalePct(pr.z)/100,half=pr.half*k;
  PET_SURF[pr.k]={y:Math.round(petFloorY(pr.z)-pr.top*k*1.6),x0:Math.round(pr.x-half*0.55),x1:Math.round(pr.x+half*0.55),z:pr.z,prop:1,half:half}}
function petIsDepthSurf(s){return !!(PET_SURF[s]&&PET_SURF[s].prop)}
function petSurfY(surf,z){if(surf==='floor')return petFloorY(z);const sf=PET_SURF[surf];return sf?sf.y:petFloorY(z)}
function petDepthAvoidProps(x,z){for(const pr of PET_PROPS){const sf=PET_SURF[pr.k];if(Math.abs(x-pr.x)<sf.half+10&&Math.abs(z-pr.z)<6)return z<pr.z?pr.z-6:pr.z+6}return z}
function petDepthPickZ(x,r,lo,hi){
  const p=petData(),zmin=petZMinAt(x),zmax=petDepthMaxZ(p);lo=Math.max(zmin,lo==null?zmin:lo);hi=Math.min(zmax,hi==null?zmax:hi);
  if(hi<lo)return petClampN(PET_DEPTH.ART_Z,zmin,Math.max(zmin,zmax));return petClampN(petDepthAvoidProps(x,lo+(hi-lo)*r()),zmin,zmax)}
function petDepthSetLastWalkZ(q,z){for(let i=q.length-1;i>=0;i--){if(q[i].k==='hop')return q;if(q[i].k==='walk'){q[i].z=z;return q}}return q}
function petDepthFloorRoute(c,x,r,lo,hi){x=petClampHouse(x);const q=petRoute(c,'floor',x);return petDepthSetLastWalkZ(q,petDepthPickZ(x,r,lo==null?10:lo,hi==null?62:hi))}
/* ---------- walking in depth ---------- */
function petDepthStartWalk(rt,s){
  const c=rt.c,p=petData(),weak=petStage(p)>=3;
  if(s.z==null)s.z=c.surf==='floor'?PET_DEPTH.ART_Z:c.z;
  if(c.surf==='floor'){
    s.z=Math.max(petZMinAt(s.x),petClampN(s.z,0,100));
    /* a straight line would cut through furniture: step out in front of it, go across, then step back */
    const need=petZNeed(c.x,s.x);
    if(!s.detour&&(c.z<need-0.5||s.z<need-0.5)&&Math.abs(s.x-c.x)>2){
      const rest=[];
      if(c.z<need-0.5){rest.push({k:'walk',x:s.x,z:Math.max(need,s.z),run:s.run,detour:1});if(s.z<need-0.5)rest.push({k:'walk',x:s.x,z:s.z,run:s.run,detour:1});
        c.plan.unshift.apply(c.plan,rest);s.x=c.x;s.z=need;s.detour=1}
      else{c.plan.unshift({k:'walk',x:s.x,z:s.z,run:s.run,detour:1});s.z=need;s.detour=1}
    }
  }
  c.dir=s.x>=c.x?1:-1;c.anim=s.run?'run':'walk';
  const k=petK(c),vx=(s.run?96:weak?17:32)*k,vz=PET_DEPTH.Z_SPEED*petWalkSpeedPct(c.z)/100*(s.run?2.6:weak?0.55:1);
  s.limit=(Math.abs(s.x-c.x)/vx+Math.abs(s.z-c.z)/vz)*2.2+4;
}
function petDepthWalk(rt,s,dt,weak){
  const c=rt.c,k=petK(c);
  const vx=(s.run?96:weak?17:32)*k,vz=PET_DEPTH.Z_SPEED*petWalkSpeedPct(c.z)/100*(s.run?2.6:weak?0.55:1);
  const dx=s.x-c.x,dz=s.z-c.z,T=Math.max(Math.abs(dx)/vx,Math.abs(dz)/vz);s.t+=dt;
  /* Lateral, Approach or Retreat walk, chosen from what is left of the path */
  if(Math.abs(dz)>Math.abs(dx)&&Math.abs(dz)>1.5){c.anim=dz>0?'walk_front':'walk_back';c.fpsMul=s.run?1.8:weak?0.6:1}
  else{c.anim=s.run?'run':petAnimName(rt,'walk');c.fpsMul=1}
  if(T<=dt){c.x=s.x;c.z=s.z;c.step=null;petDepthArrived(rt)}
  else{const f=dt/T;c.x+=dx*f;c.z+=dz*f;if(Math.abs(dx)>0.5)c.dir=dx>0?1:-1}
  if(c.surf==='floor'){const zm=petZMinAt(c.x);if(c.z<zm-1)c.z=Math.min(zm,c.z+PET_DEPTH.Z_SPEED*2*dt);c.y=petFloorY(c.z)}
  if(s.t>s.limit)c.step=null;
}
/* ---------- saved position: state.pet.bond ---------- */
function petDepthStore(rt,force){
  const p=petData(),c=rt.c;if(!p||!c)return;
  p.bond=p.bond||{};p.bond.posX=petPosXOf(c.x);p.bond.posZ=Math.round(c.z*10)/10;p.bond.elevationY=petElevOf(c);p.bond.at=Date.now();
  if(force||Date.now()-(rt.bondSavedAt||0)>20000){rt.bondSavedAt=Date.now();save()}
}
function petDepthArrived(rt){petDepthStore(rt,false)}
function petDepthPlaceFloor(rt,c,r){
  const p=petData(),bd=p&&p.bond,zmax=petDepthMaxZ(p);
  if(bd&&bd.posZ!=null&&Date.now()-(bd.at||0)<10*60000){c.x=petClampHouse(petXOfPosX(bd.posX));c.z=petClampN(bd.posZ,petZMinAt(c.x),Math.max(petZMinAt(c.x),zmax))}
  else c.z=petDepthPickZ(c.x,r,14,Math.min(55,zmax));
  c.y=petFloorY(c.z);
}
/* ---------- choices the cat can make (called from petThink) ---------- */
function petDepthOptions(rt,add,R,r,pause,o){
  const c=rt.c,p=petData(),t=petTrust(p),zmax=petDepthMaxZ(p),cx=rt.cam?rt.cam.cx:c.x;
  if(o.stage>=3)return;
  /* come toward you and settle in the foreground */
  if(t>=PET_DEPTH.ZONE_2_TRUST_REQ)add('approach',1.2*o.cur*(0.5+t/100),function(){
    const x=petClampHouse(cx+R(-36,36)),q=petRoute(c,'floor',x);petDepthSetLastWalkZ(q,petDepthPickZ(x,r,58,Math.min(84,zmax)));
    q.push({k:'face',dir:x<cx?1:-1});q.push(petAct(r()<0.55?'lookup':'loaf',R(10,24)));q.push(pause());return q});
  /* wander off to the back of the room for some time alone */
  add('solo',0.8*o.shy,function(){const x=petClampHouse(c.x+R(-70,70)),q=petRoute(c,'floor',x);petDepthSetLastWalkZ(q,petDepthPickZ(x,r,0,22));q.push(petAct(r()<0.5?'loaf':'groom',R(20,50)));q.push(pause());return q});
  /* the pouf in the living room */
  add('pouf',0.5*o.cur,function(){const sf=PET_SURF.pouf,q=petRoute(c,'pouf',R(sf.x0,sf.x1));q.push(petAct(r()<0.5?'loaf':'idle_sit',R(20,50)));q.push.apply(q,petRoute({surf:'pouf',x:(sf.x0+sf.x1)/2,z:sf.z},'floor',R(-80,40)));q.push(pause());return q});
  /* the big foreground cushion, only once he trusts you enough to come that close */
  if(zmax>=PET_SURF.cushion.z)add('cushion',0.6,function(){const sf=PET_SURF.cushion,q=petRoute(c,'cushion',R(sf.x0,sf.x1));q.push(petAct(r()<0.6?'loaf':'idle_sit',R(30,80)));q.push.apply(q,petRoute({surf:'cushion',x:(sf.x0+sf.x1)/2,z:sf.z},'floor',R(-120,-40)));q.push(pause());return q});
}
/* ---------- the camera-glass visit ---------- */
function petLensPlan(rt){
  const c=rt.c,D=PET_DEPTH,p=petData(),t=petTrust(p),x=petClampHouse(rt.cam?rt.cam.cx:c.x);
  rt.lensAt=Date.now();rt.inLens=true;
  const q=c.surf!=='floor'?petRoute(c,'floor',x):[];
  q.push({k:'walk',x:x,z:D.LENS_TARGET_Z,lens:1});
  q.push({k:'fn',f:function(){petDepthFog(rt)}});
  q.push(petAct('sniff',D.LENS_SNIFF_MS/1000));
  if(Math.random()*100<t){q.push({k:'fn',f:function(){setTimeout(function(){petDepthBonk(rt)},160)}});q.push(petAct('bonk',0.55));q.push(petAct('sniff',0.6))}
  q.push(petAct('lookup',petRand(3,6)));
  q.push({k:'walk',x:petClampHouse(x+(Math.random()<0.5?-1:1)*petRand(24,56)),z:petRand(40,60)});
  q.push({k:'fn',f:function(){rt.inLens=false;petDepthStore(rt,true)}});
  q.push(petAct('idle_sit',petRand(6,12)));
  q.lens=1;return q;
}
function petDepthCanLens(rt){const p=petData();return !!p&&petStage(p)<2&&petTrust(p)>=PET_DEPTH.ZONE_3_TRUST_REQ&&petDepthMaxZ(p)>=PET_DEPTH.LENS_TARGET_Z}
/* a high-trust greeting sometimes walks right up to the glass */
function petDepthGreet(rt){if(!petDepthCanLens(rt)||Math.random()*100>=PET_DEPTH.LENS_VISIT_PROB)return null;const q=petLensPlan(rt);q.unshift(petAct('stretch',3.4));return q}
function petDepthFog(rt){const c=rt.c,k=petK(c);rt.fog={x:c.x,y:c.y-23*k,t0:performance.now(),k:k};petSpawn(rt,'sparkle',c.x+petRand(-6,6),c.y-30*k,0,-6,0.6)}
function petDepthBonk(rt){
  const D=PET_DEPTH,px=D.LENS_BONK_NUDGE_PX,p=petData();
  ['petCanvas','petFullCanvas'].forEach(function(id){const cv=document.getElementById(id);if(cv&&cv.animate)cv.animate([{transform:'translate(0,0)'},{transform:'translate(0,'+px+'px)'},{transform:'translate(0,0)'}],{duration:200,easing:'ease-out'})});
  haptic([14]);petPurr(rt,0.8);const c=rt.c,k=petK(c);petSpawn(rt,'heart_big',c.x+petRand(-10,10),c.y-50*k,petRand(-6,6),-16,1.6);
  if(p&&state.devMode)rt.bonks=(rt.bonks||0)+1;
}
/* ---------- parallax: a horizontal drag, or tilting the phone ---------- */
var PET_TILT=null;
window.addEventListener('deviceorientation',function(e){if(e&&e.gamma!=null)PET_TILT=petClampN(e.gamma/25,-1,1)});
function petParPx(z){
  const D=PET_DEPTH,A=[[0,D.PARALLAX_PLANE_0_PX],[45,D.PARALLAX_PLANE_1_PX],[75,D.PARALLAX_PLANE_2_PX],[95,D.PARALLAX_PLANE_3_PX]];
  if(z<=A[0][0])return A[0][1];for(let i=0;i<A.length-1;i++)if(z<=A[i+1][0]){const u=(z-A[i][0])/(A[i+1][0]-A[i][0]);return A[i][1]+(A[i+1][1]-A[i][1])*u}
  return Math.min(D.PARALLAX_MAX_OFFSET_PX,A[A.length-1][1]);
}
function petParOff(rt,z){const k=rt.par?rt.par.k:0;return k?Math.round(-k*petParPx(z)):0}
function petParBg(rt){return petParOff(rt,0)}
function petDepthPointer(rt,type,x,hit){
  rt.par=rt.par||{k:0,target:0};
  if(type==='down'){rt.lastTapAt=Date.now();if(hit==='room')rt.par.x0=x}
  else if(type==='move'&&rt.par.x0!=null)rt.par.target=petClampN((x-rt.par.x0)/48,-1,1);
  else if(type==='up'){rt.par.x0=null;rt.par.target=0}
}
function petDepthTapZ(rt,x,y){const c=rt.c,p=petData();if(y<petFloorY(0)-2)return c.z;return petClampN(petZFromY(y),petZMinAt(x),Math.max(petZMinAt(x),petDepthMaxZ(p)))}
/* ---------- per-frame bookkeeping ---------- */
function petDepthViewOpen(){const v=document.getElementById('view-pet'),f=document.getElementById('petRoomFullscreen');return !!((v&&v.classList.contains('active')&&document.getElementById('petCanvas'))||(f&&f.classList.contains('is-open')))}
function petDepthTick(rt,dt){
  const c=rt.c,D=PET_DEPTH,now=Date.now();
  petLikesTick(rt);petBrainFrame(rt,dt);
  rt.par=rt.par||{k:0,target:0};const tgt=rt.par.x0!=null?rt.par.target:(PET_TILT!=null?PET_TILT:0);rt.par.k+=(tgt-rt.par.k)*(1-Math.exp(-8*dt));if(Math.abs(rt.par.k)<0.01&&!tgt)rt.par.k=0;
  if(rt.fog&&performance.now()-rt.fog.t0>D.LENS_FOG_DURATION_MS)rt.fog=null;
  const p=petData();if(p&&c){p.bond=p.bond||{};p.bond.posX=petPosXOf(c.x);p.bond.posZ=Math.round(c.z*10)/10;p.bond.elevationY=petElevOf(c)}
  /* left alone with the room open: a trusting cat comes to sniff the glass */
  if(!petDepthViewOpen()){rt.viewSince=0}else if(!rt.viewSince)rt.viewSince=now;
  if(!(typeof PET_BRAIN!=='undefined'&&PET_BRAIN.OWNS_GLASS)&&rt.viewSince&&!rt.inLens&&petDepthCanLens(rt)&&!rt.hold&&!rt.follow&&!rt.ball.on&&!rt.dangle){   /* v78: the brain's approach drive decides instead */
    const idle=now-Math.max(rt.viewSince,rt.lastTapAt||0,(rt.lensAt||0)+(D.LENS_COOLDOWN_SEC-D.IDLE_APPROACH_WAIT_SEC)*1000);
    const s=c.step,busy=s&&(s.k==='hop'||(s.k==='act'&&/^(eat|play|kick|bat|roll|scratch|scratch_in|scratch_out|meow)$/.test(s.a)));
    const blk=rt.blk||petBlockNow(p),asleep=blk.k==='sleep'||blk.k==='nap'||String(c.anim).indexOf('sleep')===0;
    if(idle>=D.IDLE_APPROACH_WAIT_SEC*1000&&!busy&&!asleep)petInterrupt(rt,petLensPlan(rt));
  }
  if(state.devMode&&petPairIsFocus(rt)){const el=document.getElementById('petDevDepth');if(el){const pr=petDepthProject(c.z,petElevOf(c));el.textContent='posX '+petPosXOf(c.x)+' · posZ '+c.z.toFixed(1)+' · elevationY '+petElevOf(c)+' · scale '+pr.spriteScalePct+'% · zone '+pr.zoneIndex+' · trust '+petTrust(p)+' · max Z '+petDepthMaxZ(p)}}
}
/* ---------- drawing ---------- */
function petDepthPropImgs(rt){
  if(rt.propImg)return rt.propImg;rt.propImg={};const P=rt.A.depth&&rt.A.depth.props;if(!P)return rt.propImg;
  for(const k of Object.keys(P)){const i=new Image();i.src=P[k].url;rt.propImg[k]={img:i,w:P[k].w,h:P[k].h}}return rt.propImg;
}
function petDrawProp(rt,o,pr,part){
  const im=petPropImg(rt,part||pr.k);if(!im||!im.img.complete)return;const k=petDepthScalePct(pr.z)/100,w=im.w*k,h=im.h*k,fy=petFloorY(pr.z);
  const q=function(v){return Math.round(v*PET_OS)/PET_OS},wob=pr.k==='box'?petBoxWobble(rt):0;   /* v79.2: rocks when he lands */
  o.drawImage(im.img,0,0,im.w,im.h,q(pr.x-w/2+wob),q(fy-h+1),q(w),q(h));
}
/* replaces the fixed back-to-front order: everything is sorted by depth */
function petDepthScene(rt,o,p){
  const c=rt.c,L=[],kickFront=c.anim==='kick'||!!rt.ballFront,bg=petParBg(rt);
  const at=function(z,f){L.push({z:z,f:f})};
  at(30,function(){petItem(rt,o,'bed_back',10,118)});
  at(34,function(){petItem(rt,o,rt.bowl.food?'bowl_food':'bowl_empty',PET_BOWL.food.x-9,PET_BOWL.food.top);petItem(rt,o,'bowl_water',PET_BOWL.water.x-9,PET_BOWL.water.top)});
  const ball=function(){petItem(rt,o,rt.ball.col||'ball_a',rt.ball.x-4,PET_SURF.floor.y-petToyH(rt.ball.col)-Math.round(rt.ball.h||0),rt.ball.flip)};
  if(rt.ball.on)at(kickFront?35.2:34.8,ball);
  if(rt.gift)at(34.9,function(){petItem(rt,o,rt.gift.k,rt.gift.x-6,PET_SURF.floor.y-5)});
  at(40,function(){petItem(rt,o,'bed_front',10,126)});
  for(const pr of PET_PROPS){if(pr.parts){at(pr.z-0.2,function(){petDrawProp(rt,o,pr,pr.parts[0])});at(pr.z+0.2,function(){petDrawProp(rt,o,pr,pr.parts[1])})}else at(pr.z,function(){petDrawProp(rt,o,pr)})}
  /* the cat sorts after anything at its own depth (so it sits on a prop, not inside it) */
  const cz=(petBoxIn(rt)?PET_SURF.box.z:c.z)+0.01;if(p&&p.alive)at(cz,function(){petBoxDrawCat(rt,o)});   /* inside the box he sorts between its back and front */
  if(p&&p.alive)petBrainScene(rt,o,at,cz);
  petPairScene(rt,o,at);   /* v80: the second cat, its toy and its things, sorted with everything else */
  L.sort(function(a,b){return a.z-b.z});
  for(const it of L){const d=petParOff(rt,it.z===cz&&c.surf!=='floor'&&!petIsDepthSurf(c.surf)?0:it.z)-bg;if(d){o.save();o.translate(d,0);it.f();o.restore()}else it.f()}
  const dc=petParOff(rt,c.z)-bg,k=petK(c);if(dc){o.save();o.translate(dc,0)}
  rt.parts.forEach(function(q){const f=1-q.age/q.life;if(f<0.15&&Math.floor(rt.t*14)%2)return;if(q.al)o.globalAlpha=q.al*Math.min(1,f*2);petItem(rt,o,q.s,q.x-4,q.y);if(q.al)o.globalAlpha=1});
  if(rt.bubble&&p&&p.alive&&Math.floor(rt.t*0.7)%3!==2)petItem(rt,o,rt.bubble.s,c.x+(c.dir>0?6:-24)*k,c.y-50*k+Math.round(Math.sin(rt.t*5)*1));
  if(dc)o.restore();
  petPairSceneTop(rt,o);   /* v80: the second cat's particles and speech bubble */
}
/* on the glass, after the time-of-day tint: the breath puff from a sniff */
var PET_FOG_MASK=null;
function petDepthGlass(rt,o){
  const f=rt.fog;if(!f)return;const D=PET_DEPTH,u=(performance.now()-f.t0)/D.LENS_FOG_DURATION_MS;if(u>=1){rt.fog=null;return}
  if(!PET_FOG_MASK){PET_FOG_MASK=[];for(let y=0;y<16;y++)for(let x=0;x<24;x++){const a=(x+0.5-12)/12,b=(y+0.5-8)/8,d=a*a+b*b,lump=0.12*Math.sin(x*1.3)+0.1*Math.cos(y*1.7);if(d<0.92+lump){const edge=d>0.6;if(!edge||((x+y)%2===0))PET_FOG_MASK.push([x,y,edge?0.6:1])}}}
  const peak=D.LENS_FOG_OPACITY_PCT/100,a=peak*(u<0.15?u/0.15:1-(u-0.15)/0.85),ox=Math.round(f.x-PET_HX0-12+petParOff(rt,D.PLANE_3_Z_INDEX)),oy=Math.round(f.y-8-u*3);
  for(const m of PET_FOG_MASK){o.fillStyle='rgba(240,246,255,'+(a*m[2]).toFixed(3)+')';o.fillRect(ox+m[0],oy+m[1],1,1)}
}

devChatSeed('claude-pet-depth-v75-2026-10-24','Claude',[
'Pet room depth (v75): the Feature 1.3 "2.5D / camera-glass" brief, adapted to the canvas room. Everything lives in src/runtime/segments/24-pet-depth.js plus small hooks in 15, 16 and 17.',
'• Coordinates: rt.c.z is posZ (0 back wall, 100 the screen). Scale is the brief formula exactly (55 + floor(z*130/100)): 94% at 30, 146% at 70, 178% at 95. Feet height is a one-point perspective tuned to this floor (PET_DEPTH.HORIZON_Y), so posZ 35 is y 132 at 100%, exactly the old floor line. The brief\'s 32-82% screenTop formula does not fit this room and is not used.',
'• Saved: state.pet.bond = {posX, posZ, elevationY, at}. petZoneIndex(z) derives the old zoneIndex. Trust 0-100 is derived from the bond level (petTrust), so 30 and 65 land mid-Wary and early-Friendly.',
'• Drawing: the room canvas is now PET_OS = 3 times its pixel size so a scaled cat stays crisp. Instead of z-index planes, petDepthScene sorts the bed, bowls, toys, the pouf (z 45), the floor cushion (z 66) and the cat by depth. Furniture painted into the wall art cannot hide the cat, so PET_Z_BLOCK keeps him in front of it.',
'• Walks: any walk step can carry z. Without one, floor walks return to the art plane (z 35), so every older plan still works. walk_front / walk_back / sniff / bonk frames live in petAssets.depth.',
'• Glass visit: trust 65+, 20 s with the room open and no taps (plus a 120 s cooldown that is not in the brief), or 30% of greetings. Sniff 1.2 s, breath fog 1.8 s at 25%, and a 3 px canvas nudge on a bonk (chance = trust %).',
'• Tested in headless Chromium: acceptance numbers, 4 coats, kitten, tiers, pouf occlusion, cushion, glass visit, fullscreen portrait and landscape, a long simulated day with no errors. Not yet seen on a real phone, and the tilt parallax is untested.'
].join('\\n'));

devChatSeed('claude-pet-depth-v76-2026-10-24','Claude',[
'Pet depth (v76), from owner feedback on v75: the cat walked back and ended up standing on the side of the sofa.',
'• Cause: the furniture limits in PET_Z_BLOCK were measured wrong (about 10 px too high) and only checked at the end of a walk. Now each entry stores the y of that furniture\'s lowest pixel in the art, the limit is worked out from it (PET_Z_GAP px in front), and walks that would cut through furniture step out in front of it first (petZNeed). A long simulated day found no frame with the cat inside furniture.',
'• Walk cycles: approach and retreat are now 6 frames. Diagonal gait, a shoulder or hip roll, a head bob, and a tail that swings opposite the hips. On the retreat walk the lifted back paw shows its pink pads. Kittens get their own frames for each growth stage, with their own big head, instead of shrunken adult frames.'
].join('\\n'));
