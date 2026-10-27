/* ---------- HD cat: the walking animations drawn at twice the pixel density, with strong outlines kept.
   The body comes from src/data/petHd.html (the old art smoothed to 2x, face removed). Everything that makes each cat
   individual still works at 2x: its coat (including the genetic coats, composed by 32-pet-dna at 2x), the ears, tail,
   leg and body shape (the existing rig data scaled up), and the face, which is drawn here, live, so it can change
   expression and follow the cat's whisker gene. Anything not yet redrawn keeps using the old art. ---------- */
var PET_HD={data:null,sheets:{},busy:{},fr:new Map(),rigfr:{},err:0,hits:0};
function petHdOn(){try{return state.hdCat!==false}catch(e){return true}}
function petHdJson(){
 if(PET_HD.data)return PET_HD.data;
 try{PET_HD.data=JSON.parse(document.getElementById('petHd').textContent)}catch(e){console.warn('[pet hd] data',e);PET_HD.data={hs:2,kinds:{}}}
 return PET_HD.data;
}
/* the coloured 2x sheet for this cat's coat and growth stage; null while it is still being made (the old art is used meanwhile) */
function petHdSheet(rt,coat,kind){
 const B=petHdJson(),K=B.kinds[kind];if(!K)return null;
 const key=kind+'|'+coat;rt.hd=rt.hd||{};
 if(rt.hd[key])return rt.hd[key];
 if(PET_HD.busy[key])return null;
 const pal=rt.A.pal[coat];if(!pal)return null;
 let url=K.sheet[coat];
 if(!url){if(typeof petDnaRecipe==='function'&&petDnaRecipe(coat)){if(typeof petDnaRegister==='function')petDnaRegister(coat);url='dna:hd'+kind+':'+coat}else return null}
 PET_HD.busy[key]=1;
 petTintSheet(url,pal).then(function(cv){rt.hd[key]=cv}).catch(function(e){PET_HD.err++;console.warn('[pet hd] sheet',e)}).then(function(){delete PET_HD.busy[key]});
 return null;
}
/* ----- the existing rig data (ears, tail, legs, body), scaled to the 2x frame ----- */
function petHdRigFrame(kind,anim,f){
 const key=kind+'|'+anim+'|'+f;if(PET_HD.rigfr[key]!==undefined)return PET_HD.rigfr[key];
 const lo=petRigFrameData(kind,anim,f);if(!lo){PET_HD.rigfr[key]=null;return null}
 const hs=2,W0=72,W1=144,grow=function(list){const o=[];for(let k=0;k<list.length;k++){const i=list[k],y=(i/W0)|0,x=i%W0;for(let dy=0;dy<hs;dy++)for(let dx=0;dx<hs;dx++)o.push((y*hs+dy)*W1+x*hs+dx)}return o};
 const o={lg:lo.lg,bd:lo.bd,tl:lo.tl,y:lo.y*hs+hs-1,l:lo.l?[lo.l[0]*hs,lo.l[1]*hs]:null,x:lo.x?[lo.x[0]*hs,lo.x[1]*hs]:null,ears:[],tail:null,wk:[]};
 lo.ears.forEach(function(e){o.ears.push({px:grow(e.px),b:[e.b[0]*hs,e.b[1]*hs],t:[e.t[0]*hs,e.t[1]*hs]})});
 if(lo.tail){const a=lo.tail.a.map(function(p){return [p[0]*hs,p[1]*hs]}),cum=[0];for(let i=1;i<a.length;i++)cum.push(cum[i-1]+Math.hypot(a[i][0]-a[i-1][0],a[i][1]-a[i-1][1]));o.tail={px:grow(lo.tail.px),a:a,cum:cum}}
 PET_HD.rigfr[key]=o;return o;
}
/* the smoothed 2x outline can sit a pixel outside the scaled-up part masks: let each part also take the outline pixels touching it, so no stray dots are left behind when it moves */
function petHdClaim(fr,src,W,H){
 const OL=PET_RIG_OUTLINE,owner=new Int16Array(W*H);
 const parts=[];fr.ears.forEach(function(e){parts.push(e)});if(fr.tail)parts.push(fr.tail);
 parts.forEach(function(pt,k){for(let j=0;j<pt.px.length;j++)owner[pt.px[j]]=k+1});
 const out=parts.map(function(pt){return {px:pt.px.slice(),src:pt}});
 for(let k=0;k<parts.length;k++){
  const seen=new Set(parts[k].px),add=[];
  for(let j=0;j<parts[k].px.length;j++){const i=parts[k].px[j],y=(i/W)|0,x=i%W;
   for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){const yy=y+dy,xx=x+dx;if(yy<0||xx<0||yy>=H||xx>=W)continue;const q=yy*W+xx;
    if(seen.has(q)||owner[q])continue;if((src[q]>>>24)&&src[q]===OL){seen.add(q);add.push(q)}}}
  add.forEach(function(q){owner[q]=k+1;out[k].px.push(q)});
 }
 const g=Object.assign({},fr);
 g.ears=fr.ears.map(function(e,k){return Object.assign({},e,{px:out[k].px})});
 if(fr.tail)g.tail=Object.assign({},fr.tail,{px:out[fr.ears.length].px});
 return g;
}
/* ----- the face: eyes, nose, mouth, whiskers, drawn straight into the frame's pixels ----- */
function petHdCols(rt,coat){
 const pal=rt.A.pal[coat]||{},out={};
 [120,130,140,150,160,170,180,190,200,210].forEach(function(i){const c=pal[String(i)];out[i]=c?((255<<24)|(c[2]<<16)|(c[1]<<8)|c[0])>>>0:0});
 return out;
}
function petHdDot(px,CW,CH,y,x,v,cols){
 y=Math.round(y);x=Math.round(x);if(y<0||x<0||y>=CH||x>=CW)return;const i=y*CW+x;if(!(px[i]>>>24)||!cols[v])return;px[i]=cols[v];
}
/* an eye is a pointed almond (the corners lift a little on the outside), a thick dark rim, green iris, a slit pupil and a highlight */
function petHdEyeMask(rx,ry,pw,side,tilt,clip){
 const W=Math.ceil(rx)*2+5,H=Math.ceil(ry)*2+5,cx=(W-1)/2,cy=(H-1)/2,m=[];
 for(let y=0;y<H;y++){m.push([]);for(let x=0;x<W;x++){
  const xx=x-cx,yy=y-cy,u=Math.abs(xx)/rx;let ok=false;
  if(u<=1){const sh=-tilt*xx*side,h=ry*Math.pow(1-Math.pow(u,pw),0.85);ok=Math.abs(yy-sh)<=h+0.001;
   if(ok&&clip)ok=clip(xx,yy,u)}
  m[y].push(ok)}}
 return {m:m,W:W,H:H,cx:cx,cy:cy};
}
function petHdErode(M,n){let m=M.m;for(let k=0;k<n;k++){const o=m.map(function(r){return r.slice()});for(let y=0;y<M.H;y++)for(let x=0;x<M.W;x++)if(m[y][x]){if(!(y>0&&m[y-1][x]&&y<M.H-1&&m[y+1][x]&&x>0&&m[y][x-1]&&x<M.W-1&&m[y][x+1]))o[y][x]=false}m=o}return m}
function petHdEye(px,CW,CH,cy,cx,kind,side,cols,sc){
 sc=sc||1;const d=function(y,x,v){petHdDot(px,CW,CH,y,x,v,cols)};
 if(kind==='happy'||kind==='closed'){                     /* a soft arch ^ (happy) or curve u (closed) */
  const rx=4.4*sc;for(let dx=-Math.floor(rx);dx<=Math.floor(rx);dx++){const t=dx/rx,yy=kind==='happy'?-(1-t*t)*3.0*sc:(1-t*t)*1.6*sc,y0=cy+yy+(kind==='happy'?2:0);d(y0,cx+dx,150);d(y0+1,cx+dx,150)}
  return;
 }
 const P0={open:[6.2,5.7,1.8,.12],wide:[6.0,6.4,1.95,.08],sleepy:[5.4,4.9,2.1,.14],angry:[5.4,4.9,2.1,.14],sad:[5.4,4.9,2.1,.14]}[kind]||[6.2,5.7,1.8,.12],P=[P0[0]*sc,P0[1]*sc,P0[2],P0[3]];
 const clip=kind==='angry'?function(xx,yy){return yy>=-P[1]*.18+(xx*-side)*.5-.2}:kind==='sad'?function(xx,yy){return yy>=-P[1]*.18-(xx*-side)*.5-.2}:kind==='sleepy'?function(xx,yy){return yy>=-P[1]*.05}:null;
 const M=petHdEyeMask(P[0],P[1],P[2],side,P[3],clip),inner=petHdErode(M,1);
 let top=M.H,bot=-1;for(let y=0;y<M.H;y++)for(let x=0;x<M.W;x++)if(inner[y][x]){top=Math.min(top,y);bot=Math.max(bot,y)}
 /* the upper lid is one pixel heavier than the rest of the outline, like eyeliner */
 const lid=M.m.map(function(r){return r.map(function(){return false})});
 for(let x=0;x<M.W;x++){let y=0;while(y<M.H&&!inner[y][x])y++;if(y<M.H&&y<top+3)lid[y][x]=true}
 const mid=(top+bot)/2,ph=Math.max(1.8,(bot-top)*.56);
 for(let y=0;y<M.H;y++)for(let x=0;x<M.W;x++){
  if(!M.m[y][x])continue;const py=cy+y-M.cy,pxx=cx+x-M.cx;
  if(!inner[y][x]||lid[y][x]){d(py,pxx,150);continue}
  const xx=x-M.cx,v=(y-top)/Math.max(1,bot-top);
  let c=v<.5?160:170;
  const pxo=xx-(side<0?-.4:.4);if(pxo*pxo/(1.5*1.5)+Math.pow((y-mid-.4)/ph,2)<=1)c=150;      /* a tall, soft pupil */
  d(py,pxx,c);
 }
 /* two highlights: a big one up and towards the outer corner, a small one low on the other side */
 {const hy=cy+top+1.6-M.cy,hx=cx+(side<0?-2.6:1.4);[[0,0],[0,1],[1,0],[1,1]].forEach(function(q){d(hy+q[0],hx+q[1],180)});d(cy+bot-1.4-M.cy+.4,cx+(side<0?2.4:-2.4),180)}
}
/* expr: neutral, happy, sleepy, surprised, angry, sad, blink. a = {l,r,nose,mouth} in the 2x frame (already moved with the body). wk = whisker length factor */
/* where the face sits in the head: the painted head is not always symmetric (a kitten's cheek can be wider on one side), so the eyes are nudged toward the middle of the head at eye height */
function petHdHeadShift(px,CW,CH,a){
 const mid=(a.l[1]+a.r[1])/2,span=a.r[1]-a.l[1],y0=Math.round((a.l[0]+a.r[0])/2-2*(a.sc||1));let sum=0,n=0;
 for(let k=-2;k<=2;k+=2){const y=y0+k;if(y<0||y>=CH)continue;
  const on=function(x){return x>=0&&x<CW&&(px[y*CW+x]>>>24)>24};
  let x=Math.round(mid);if(!on(x))continue;let l=x,r=x;while(on(l-1)&&x-l<span*2)l--;while(on(r+1)&&r-x<span*2)r++;
  const w=r-l+1;if(w<span*1.6||w>span*3.4)continue;sum+=(l+r)/2-mid;n++}
 if(!n)return 0;const sc=a.sc||1;return Math.max(-5*sc,Math.min(5*sc,sum/n*.9));
}
function petHdFace(px,CW,CH,a,expr,cols,wk){
 const sc=a.sc||1,X=function(v){return Math.round(v*sc)};
 const E={neutral:['open','open'],happy:['happy','happy'],sleepy:['sleepy','sleepy'],surprised:['wide','wide'],angry:['angry','angry'],sad:['sad','sad'],blink:['closed','closed']}[expr]||['open','open'];
 const d=function(y,x,v){petHdDot(px,CW,CH,y,x,v,cols)};
 /* every eye sits a little higher on the face and closer together, so a blink or a mood change never makes the eyes jump; only the plain round eyes (neutral, surprised) get the almond shape, the others keep theirs */
 const ey=-4.6*sc,ex=.5*sc,hx=petHdHeadShift(px,CW,CH,a);
 petHdEye(px,CW,CH,a.l[0]+ey,a.l[1]+ex+hx,E[0],-1,cols,sc);petHdEye(px,CW,CH,a.r[0]+ey,a.r[1]-ex+hx,E[1],1,cols,sc);
 const ny=a.nose[0],nx=Math.round(a.nose[1]+hx*.75),my=ny+3.2;
 /* a small, soft pink nose (no dark edge) */
 [-1,0,1].forEach(function(x){d(ny-.5,nx+x,x<0?140:130)});d(ny+.5,nx,120);
 /* a short line down from the nose */
 d(ny+1.6,nx,200);d(ny+2.4,nx,200);
 const line=function(list,dy0){list.forEach(function(p){d(my+(dy0||0)+p[1],nx+X(p[0]),200)})};
 if(expr==='happy'){[[-4,0],[-3,1],[-2,1],[-1,2],[0,2],[1,2],[2,1],[3,1],[4,0]].forEach(function(p){d(my-.5+p[1],nx+X(p[0]),200)});[-1,0,1].forEach(function(x){d(my+2.5,nx+x,210);d(my+3.5,nx+x,x===0?210:210)})}
 else if(expr==='surprised'){for(let dy=0;dy<3;dy++)for(let dx=-1;dx<=1;dx++){const edge=dy===0||dy===2||dx!==0;if(!(dy===0||dy===2)||dx===0)d(my+dy,nx+dx,edge?200:120)}}
 else if(expr==='angry'){line([[-3,1],[-2,0],[-1,0],[0,0],[1,0],[2,0],[3,1]])}
 else if(expr==='sad'){line([[-3,2],[-2,1],[-1,0],[0,0],[1,0],[2,1],[3,2]])}
 else line([[-4,0],[-3,1],[-2,1],[-1,0],[0,0],[1,0],[2,1],[3,1],[4,0]].map(function(p){return [p[0]>0?p[0]:p[0],p[1]]}));
 /* whiskers: thin pale strokes either side, as long as this cat's whisker gene says */
 const len=Math.max(3,Math.round(6*(wk||1)));
 [[-1,a.l[1]],[1,a.r[1]]].forEach(function(s){const sg=s[0],base=s[1];
  [[-1,3],[0,4],[1,5]].forEach(function(q){for(let t=0;t<len;t++)d(ny+1.6+q[0]*(1+t*.35),base+sg*(5+t),190)})});
}
/* ----- expression: how the cat feels right now ----- */
function petHdExpr(rt,p,c){
 const t=rt.t||0;
 /* a blink every few seconds */
 const cyc=(t%4.6);if(cyc>4.46)return 'blink';
 try{
  const now=Date.now();
  if(rt.hdMood&&now<rt.hdMood.until)return rt.hdMood.expr;
  if(petHour&&(petHour()<5||petHour()>=23)&&/^(idle_sit|loaf|lookup)/.test(c.anim||''))return 'sleepy';
  if(typeof petCanFeed==='function'&&p&&p.alive&&petCanFeed(p,now)&&p.lastFed&&now-p.lastFed>9*3600e3)return 'sad';
 }catch(e){}
 return 'neutral';
}
function petHdMood(rt,expr,sec){rt.hdMood={expr:expr,until:Date.now()+sec*1000}}
/* the cat's mood moves its ears and tail (flattened ears, a twitching tail tip): the same rig does it at 2x */
function petHdPose(rt,anim){
 try{
  const br=rt.br;if(!br||!br.som||typeof petBrainArt!=='function')return null;
  const art=petBrainArt(rt);let pose=null;
  if(br.som.ear==='airplane_flat'&&art.earRows.indexOf(anim)>=0&&petGrowIdx(petData())<0)pose={ear0:-36,ear1:36,flat:1,tail:0,tw:0};
  if(art.tailRows.indexOf(anim)>=0){let tw=0;if(br.som.tail==='tip_twitch')tw=[0,1,2,1][Math.floor(rt.t*14)%4];else if(Date.now()<br.remUntil)tw=[1,0,2,0][Math.floor(rt.t*10)%4];
   if(tw){pose=pose||{ear0:0,ear1:0,flat:0,tw:0};pose.tail=(tw-1)*7;pose.tw=tw}}
  return pose;
 }catch(e){return null}
}
function petHdAnimOf(rt,kind,meta){
 const dep=kind&&kind.charAt(0)==='d',tab=dep?(rt.A.depth&&rt.A.depth.anims):rt.A.anims;if(!tab||!meta)return null;
 for(const k in tab)if(tab[k].row===meta.row)return k;return null;
}
/* ----- one finished frame: coloured body, shaped by the cat's genes, face on top ----- */
function petHdPick(rt,p,kind,anim,f,meta,c){
 if(!kind||!petHdOn()||PET_HD.err>6)return null;
 const B=petHdJson(),K=B.kinds[kind];if(!K)return null;
 const ri=K.anims.indexOf(anim);if(ri<0)return null;
 const n=K.n[anim];f=f%n;
 const sh=petHdSheet(rt,p.coat,kind);if(!sh)return null;
 try{
  const hs=B.hs||2,W=72*hs,H=48*hs;
  let morph=null;try{const ph=petPheno(p);if(ph&&ph.morph){const g=(kind==='adult'||kind==='depth')?1:petGrowth(p);morph=petRigGrown(ph.morph,g)}}catch(e){}
  const fi0=K.face[anim]&&K.face[anim][f],pose=petHdPose(rt,anim);
  let expr=petHdExpr(rt,p,c);
  if(fi0&&!fi0.o)expr=(K.ce&&K.ce[anim])||'closed';           /* a frame drawn with closed eyes keeps them closed */
  const mk=morph&&!petRigNeutral(morph)?[petRigQ(morph.em),petRigQ(morph.ew),petRigQ(morph.tl),petRigQ(morph.lg),petRigQ(morph.bl),petRigQ(morph.wk)].join(','):'n';
  const key=[kind,p.coat,anim,f,mk,expr,pose?petRigPoseKey(pose):''].join('|'),hit=PET_HD.fr.get(key);if(hit){PET_HD.hits++;return hit}
  const src=petRigSrcPixels(sh,ri,f,W,H);
  let px,CW,CH,ox=0,oy=0,X=function(v){return v},Y=function(v){return v};
  const fr=petHdRigFrame(kind,anim,f),fi=K.face[anim]&&K.face[anim][f];
  if(fr&&((morph&&!petRigNeutral(morph))||pose)){
   const m0=morph||{em:1,ew:1,tl:1,lg:1,bl:1,wk:1},m={em:m0.em,ew:m0.ew,tl:m0.bob?Math.min(m0.tl,0.3):m0.tl,lg:m0.lg,bl:m0.bl,wk:m0.wk};
   const r=petRigCompose(src,petHdClaim(fr,src,W,H),m,pose,W,H,hs);px=r.px;CW=r.CW;CH=r.CH;ox=r.MX;oy=r.MT;X=r.X;Y=r.Y;
  }else{px=new Uint32Array(src);CW=W;CH=H}
  if(fi){
   const mp=function(pt){return [Y(pt[0]*hs)+oy,X(pt[1]*hs)+ox]};
   const a={l:mp(fi.l),r:mp(fi.r),nose:mp(fi.nose),mouth:mp(fi.mouth),sc:Math.max(.78,Math.min(1.12,fi.span*2/17.8))};
   petHdFace(px,CW,CH,a,expr,petHdCols(rt,p.coat),morph?morph.wk:1);
  }
  const cv=document.createElement('canvas');cv.width=CW;cv.height=CH;const x=cv.getContext('2d'),id=x.createImageData(CW,CH);
  new Uint32Array(id.data.buffer).set(px);x.putImageData(id,0,0);
  const out={cv:cv,ox:ox,oy:oy,hs:hs};
  if(PET_HD.fr.size>500)PET_HD.fr.delete(PET_HD.fr.keys().next().value);PET_HD.fr.set(key,out);PET_HD.hits++;return out;
 }catch(e){PET_HD.err++;console.warn('[pet hd]',e);return null}
}

/* Settings card */
function petHdSettingsRender(){
 const el=document.getElementById('hdSettingsBody');if(!el)return;
 el.innerHTML='<div class="settingsline"><div style="flex:1"><strong>HD walking</strong><div class="muted" style="font-size:12.5px">Your cat walks with twice the pixel detail, a redrawn face and expressions. Other animations are still being redrawn one by one. Turn this off if anything looks wrong.</div></div><input type="checkbox" class="check" id="hdToggle"'+(petHdOn()?' checked':'')+'></div>';
 const t=document.getElementById('hdToggle');if(t)t.addEventListener('change',function(){state.hdCat=!!t.checked;try{save()}catch(e){}PET_HD.fr.clear()});
}
(function hdBoot(){
 const nav=navigate;navigate=function(v){const r=nav.apply(this,arguments);if(v==='settings')try{petHdSettingsRender()}catch(e){}return r};
 setTimeout(function(){try{petHdSettingsRender()}catch(e){}},1900);
})();
window.LifeMaxHD=Object.freeze({on:petHdOn,pick:petHdPick,stats:function(){return {hits:PET_HD.hits,err:PET_HD.err,sheets:Object.keys((PET_RT&&PET_RT.hd)||{})}},mood:petHdMood,clear:function(){PET_HD.fr.clear()}});
