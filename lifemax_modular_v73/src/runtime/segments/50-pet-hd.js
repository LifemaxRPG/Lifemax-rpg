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
function petHdEye(px,CW,CH,cy,cx,kind,side,cols){
 const rx=3.6,ry={open:3.5,wide:4.5,sleepy:3.3,angry:3.5,sad:3.5}[kind]||3.5,d=function(y,x,v){petHdDot(px,CW,CH,y,x,v,cols)};
 if(kind==='happy'){for(let dx=-Math.floor(rx);dx<=Math.floor(rx);dx++){const t=dx/rx,yy=-(1-t*t)*2.8;d(cy+yy+1.4,cx+dx,150);d(cy+yy+2.4,cx+dx,150)}return}
 if(kind==='closed'){for(let dx=-Math.floor(rx);dx<=Math.floor(rx);dx++){const t=dx/rx,yy=(1-t*t)*1.4;d(cy+yy,cx+dx,150);d(cy+yy+1,cx+dx,150)}return}
 for(let yy=-Math.floor(ry)-1;yy<=Math.floor(ry)+1;yy++)for(let xx=-Math.floor(rx)-1;xx<=Math.floor(rx)+1;xx++){
  const e=Math.pow(xx/rx,2)+Math.pow(yy/ry,2);if(e>1)continue;
  const inner=xx*-side;
  if(kind==='angry'&&yy<-ry*.30+inner*.62)continue;
  if(kind==='sad'&&yy<-ry*.30-inner*.62)continue;
  let v=e>.5?160:170;if(e>.74)v=150;if(Math.abs(xx)<=(kind!=='wide'?1:.5)&&e<.72)v=150;
  d(cy+yy,cx+xx,v);
 }
 if(kind!=='happy'){d(cy-ry*.4,cx-rx*.45,180);d(cy-ry*.4,cx-rx*.45+1,180);d(cy-ry*.4+1,cx-rx*.45,180)}
 if(kind==='sleepy'){for(let xx=-Math.floor(rx)-1;xx<=Math.floor(rx)+1;xx++)d(cy-ry*.15,cx+xx,150)}
}
/* expr: neutral, happy, sleepy, surprised, angry, sad, blink. a = {l,r,nose,mouth} in the 2x frame (already moved with the body). wk = whisker length factor */
function petHdFace(px,CW,CH,a,expr,cols,wk){
 const E={neutral:['open','open'],happy:['happy','happy'],sleepy:['sleepy','sleepy'],surprised:['wide','wide'],angry:['angry','angry'],sad:['sad','sad'],blink:['closed','closed']}[expr]||['open','open'];
 const d=function(y,x,v){petHdDot(px,CW,CH,y,x,v,cols)};
 petHdEye(px,CW,CH,a.l[0],a.l[1],E[0],-1,cols);petHdEye(px,CW,CH,a.r[0],a.r[1],E[1],1,cols);
 const ny=a.nose[0],nx=a.nose[1],my=ny+3.2;
 d(ny-.5,nx-1,130);d(ny-.5,nx,130);d(ny-.5,nx-1,140);d(ny+.5,nx,120);
 const line=function(list,dy0){list.forEach(function(p){d(my+(dy0||0)+p[1],nx+p[0],200)})};
 if(expr==='happy'){[[-4,0],[-3,1],[-2,2],[-1,2],[0,2],[1,2],[2,2],[3,1],[4,0]].forEach(function(p){d(my-.5+p[1],nx+p[0],200)});[-1,0,1].forEach(function(x){d(my+2.5,nx+x,210)})}
 else if(expr==='surprised'){for(let dy=0;dy<3;dy++)for(let dx=-1;dx<=1;dx++)d(my+dy,nx+dx,(dy===0||dy===2||dx!==0)?200:120)}
 else if(expr==='angry'){line([[-3,1],[-2,0],[-1,0],[0,0],[1,0],[2,0],[3,1]])}
 else if(expr==='sad'){line([[-3,1],[-2,0],[-1,0],[0,0],[1,0],[2,0],[3,1]],1)}
 else line([[-3,0],[-2,1],[-1,1],[0,0],[1,1],[2,1],[3,0]]);
 d(ny+1.2,nx,200);d(ny+2.2,nx,200);
 /* whiskers: three thin strokes either side, as long as this cat's whisker gene says */
 const len=Math.max(3,Math.round(6*(wk||1)));
 [[-1,a.l[1]],[1,a.r[1]]].forEach(function(s){const sg=s[0],base=s[1];
  [[-1,3],[0,4],[1,5]].forEach(function(q){for(let t=0;t<len;t++)d(ny+1.2+q[0]*(1+t*.35),base+sg*(4+t),190)})});
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
