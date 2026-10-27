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
function petHdEye(px,CW,CH,cy,cx,kind,side,cols){
 const d=function(y,x,v){petHdDot(px,CW,CH,y,x,v,cols)};
 if(kind==='happy'||kind==='closed'){                     /* a thick arch ^ (happy) or curve u (closed): two pixels deep */
  const rx=4.8;for(let dx=-Math.floor(rx);dx<=Math.floor(rx);dx++){const t=dx/rx,yy=kind==='happy'?-(1-t*t)*3.2:(1-t*t)*1.8,y0=cy+yy+(kind==='happy'?2:0);d(y0-.5,cx+dx,150);d(y0+.5,cx+dx,150);d(y0+1.5,cx+dx,150)}
  return;
 }
 const P={open:[6.6,5.0,1.7,.2],wide:[6.4,5.8,2.0,.1],sleepy:[6.6,5.0,1.7,.2],angry:[6.6,5.0,1.7,.2],sad:[6.6,5.0,1.7,.2]}[kind]||[6.6,5.0,1.7,.2];
 const clip=kind==='angry'?function(xx,yy){return yy>=-P[1]*.20+(xx*-side)*.55-.2}:kind==='sad'?function(xx,yy){return yy>=-P[1]*.20-(xx*-side)*.55-.2}:kind==='sleepy'?function(xx,yy){return yy>=-P[1]*.05}:null;
 const M=petHdEyeMask(P[0],P[1],P[2],side,P[3],clip),inner=petHdErode(M,2),core=petHdErode({m:inner,W:M.W,H:M.H},0);
 let top=M.H,bot=-1;for(let y=0;y<M.H;y++)for(let x=0;x<M.W;x++)if(inner[y][x]){top=Math.min(top,y);bot=Math.max(bot,y)}
 for(let y=0;y<M.H;y++)for(let x=0;x<M.W;x++){
  if(!M.m[y][x])continue;const py=cy+y-M.cy,pxx=cx+x-M.cx;
  if(!inner[y][x]){d(py,pxx,150);continue}                      /* the rim, two pixels thick */
  const xx=x-M.cx,v=(y-top)/Math.max(1,bot-top);
  let c=v<.4?160:170;
  if(Math.abs(xx-(side<0?.5:-.5))<=.6)c=150;                    /* a thin slit pupil, two pixels wide */
  d(py,pxx,c);
 }
 /* the highlight sits inside the iris, towards the top outer corner */
 {const hy=cy+top+1-M.cy,hx=cx+(side<0?-3:2);[[0,0],[0,1],[1,0],[1,1]].forEach(function(q){d(hy+q[0],hx+q[1],180)})}
}
/* expr: neutral, happy, sleepy, surprised, angry, sad, blink. a = {l,r,nose,mouth} in the 2x frame (already moved with the body). wk = whisker length factor */
function petHdFace(px,CW,CH,a,expr,cols,wk){
 const E={neutral:['open','open'],happy:['happy','happy'],sleepy:['sleepy','sleepy'],surprised:['wide','wide'],angry:['angry','angry'],sad:['sad','sad'],blink:['closed','closed']}[expr]||['open','open'];
 const d=function(y,x,v){petHdDot(px,CW,CH,y,x,v,cols)};
 petHdEye(px,CW,CH,a.l[0],a.l[1],E[0],-1,cols);petHdEye(px,CW,CH,a.r[0],a.r[1],E[1],1,cols);
 const ny=a.nose[0],nx=a.nose[1],my=ny+3.5;
 /* nose: a chunky pink triangle with a dark edge */
 [[-2,-2,3],[-1,-2,3],[0,-1,2]].forEach(function(r,i){const y=ny-1+i;for(let x=-2;x<=2;x++){const a0=Math.abs(x);if(i===0&&a0<=2||i===1&&a0<=1||i===2&&a0<=0)d(y,nx+x,i===0&&x<0?140:i===2?120:130)}});
 [[-3,-1],[3,-1],[-3,0],[3,0],[-2,1],[2,1],[-1,2],[1,2],[0,3],[-2,-2],[-1,-2],[0,-2],[1,-2],[2,-2],[-3,-2],[3,-2]].forEach(function(p){d(ny+p[1]-.0,nx+p[0],200)});
 /* the line from the nose to the mouth, two pixels wide */
 d(ny+2.4,nx-.5,200);d(ny+2.4,nx+.5,200);d(ny+3.4,nx-.5,200);d(ny+3.4,nx+.5,200);
 const line=function(list,dy0){list.forEach(function(p){const y=my+(dy0||0)+p[1],x=nx+p[0];d(y,x,200);d(y+1,x,200)})};
 if(expr==='happy'){[[-5,0],[-4,1],[-3,2],[-2,2],[-1,3],[0,3],[1,3],[2,2],[3,2],[4,1],[5,0]].forEach(function(p){d(my-.5+p[1],nx+p[0],200);d(my+.5+p[1],nx+p[0],200)});[-2,-1,0,1,2].forEach(function(x){d(my+3.5,nx+x,210);d(my+4.5,nx+x,x===-2||x===2?210:210)})}
 else if(expr==='surprised'){for(let dy=0;dy<4;dy++)for(let dx=-2;dx<=2;dx++){const edge=dy===0||dy===3||dx===-2||dx===2;if(!(dy===0||dy===3)||Math.abs(dx)<=1)d(my+dy,nx+dx,edge?200:120)}}
 else if(expr==='angry'){line([[-4,1],[-3,1],[-2,0],[-1,0],[0,0],[1,0],[2,0],[3,1],[4,1]])}
 else if(expr==='sad'){line([[-4,2],[-3,1],[-2,0],[-1,0],[0,0],[1,0],[2,0],[3,1],[4,2]],1)}
 else line([[-5,0],[-4,1],[-3,2],[-2,2],[-1,1],[0,0],[1,1],[2,2],[3,2],[4,1],[5,0]]);
 /* whiskers: three thin strokes either side, as long as this cat's whisker gene says */
 const len=Math.max(3,Math.round(7*(wk||1)));
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
  if(petHour&&(petHour()<5||petHour()>=23)&&!/walk|run/.test(c.anim||''))return 'sleepy';
  if(typeof petCanFeed==='function'&&p&&p.alive&&petCanFeed(p,now)&&p.lastFed&&now-p.lastFed>9*3600e3)return 'sad';
 }catch(e){}
 return 'neutral';
}
function petHdMood(rt,expr,sec){rt.hdMood={expr:expr,until:Date.now()+sec*1000}}
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
  const expr=kind==='depth'&&anim==='walk_back'?'neutral':petHdExpr(rt,p,c);
  const mk=morph&&!petRigNeutral(morph)?[petRigQ(morph.em),petRigQ(morph.ew),petRigQ(morph.tl),petRigQ(morph.lg),petRigQ(morph.bl),petRigQ(morph.wk)].join(','):'n';
  const key=[kind,p.coat,anim,f,mk,expr].join('|'),hit=PET_HD.fr.get(key);if(hit){PET_HD.hits++;return hit}
  const src=petRigSrcPixels(sh,ri,f,W,H);
  let px,CW,CH,ox=0,oy=0,X=function(v){return v},Y=function(v){return v};
  const fr=petHdRigFrame(kind,anim,f),fi=K.face[anim]&&K.face[anim][f];
  if(morph&&!petRigNeutral(morph)&&fr){
   const m={em:morph.em,ew:morph.ew,tl:morph.bob?Math.min(morph.tl,0.3):morph.tl,lg:morph.lg,bl:morph.bl,wk:morph.wk};
   const r=petRigCompose(src,petHdClaim(fr,src,W,H),m,null,W,H,hs);px=r.px;CW=r.CW;CH=r.CH;ox=r.MX;oy=r.MT;X=r.X;Y=r.Y;
  }else{px=new Uint32Array(src);CW=W;CH=H}
  if(fi){
   const mp=function(pt){return [Y(pt[0]*hs)+oy,X(pt[1]*hs)+ox]};
   const a={l:mp(fi.l),r:mp(fi.r),nose:mp(fi.nose),mouth:mp(fi.mouth)};
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
