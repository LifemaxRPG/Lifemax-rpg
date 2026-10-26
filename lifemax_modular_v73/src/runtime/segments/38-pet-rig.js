/* ===== Cat rig (v94): the sprites, rebuilt from parts =====
   The art is still the hand-drawn frames. tools/rig_build.py cuts each frame along its own outlines into
   parts (ears, tail, whiskers) and finds where the legs and torso can be stretched; that data is in
   petRig. Here a frame is re-assembled with its parts transformed:
     - ears: scaled along and across their axis (base to tip), optionally turned;
     - tail: bent along its own centre line, so it can be longer or shorter without getting thicker;
     - whiskers: extended or trimmed;
     - legs / torso: a band of rows or columns is stretched (nearest neighbour, like re-spacing pixels);
   then the outline is repaired where a part was cut away. Nothing changes for a cat whose proportions are all
   about 1, and rows the builder could not map reliably keep their original art.
   petRigFrame(rt,sheet,kind,anim,frame,morph,pose) returns {cv,ox,oy}: a canvas bigger than 72x48 and where
   the original frame's top-left sits inside it, or null to draw the plain sheet. Results are cached. */
var PET_RIG={data:null,fr:{},src:null,out:null};
var PET_RIG_MX=76,PET_RIG_MR=76,PET_RIG_MT=44,PET_RIG_MB=8;
var PET_RIG_OUTLINE=(255<<24|28<<16|16<<8|26)>>>0;      /* palette slot 10, 26,16,28 (little-endian ABGR) */
function petRigJson(){
  if(PET_RIG.data)return PET_RIG.data;
  try{PET_RIG.data=JSON.parse(document.getElementById('petRig').textContent)}catch(e){console.warn('[pet rig] data',e);PET_RIG.data={adult:{}}}
  return PET_RIG.data;
}
function petRigIdx(r,W){const o=[];for(let i=0;i<r.length;i+=3)for(let x=r[i+1];x<=r[i+2];x++)o.push(r[i]*W+x);return o}
/* decode one frame of rig data once: pixel index lists and a cumulative-length table for the tail axis */
function petRigFrameData(kind,anim,f){
  const key=kind+'|'+anim+'|'+f;if(PET_RIG.fr[key]!==undefined)return PET_RIG.fr[key];
  const D=petRigJson(),row=D[kind]&&D[kind][anim],raw=row&&row.f[f];if(!raw){PET_RIG.fr[key]=null;return null}
  const W=D.W||72,o={lg:!!row.lg,bd:!!row.bd,tl:!!row.tl,y:raw.y,l:raw.l||null,x:raw.x||null,ears:[],tail:null,wk:[]};
  (raw.e||[]).forEach(function(e){o.ears.push({px:petRigIdx(e.r,W),b:[e.b[0]+0.5,e.b[1]+0.5],t:[e.t[0]+0.5,e.t[1]+0.5]})});
  if(raw.t&&row.tl){const a=raw.t.a.map(function(p){return [p[0]+0.5,p[1]+0.5]}),cum=[0];
    for(let i=1;i<a.length;i++)cum.push(cum[i-1]+Math.hypot(a[i][0]-a[i-1][0],a[i][1]-a[i-1][1]));
    o.tail={px:petRigIdx(raw.t.r,W),a:a,cum:cum}}
  (raw.w||[]).forEach(function(w){o.wk.push({px:petRigIdx(w.r,W),b:[w.b[0]+0.5,w.b[1]+0.5],t:[w.t[0]+0.5,w.t[1]+0.5]})});
  PET_RIG.fr[key]=o;return o;
}
function petRigSrcPixels(sh,row,f,W,H){
  PET_RIG.src=PET_RIG.src||new WeakMap();let M=PET_RIG.src.get(sh);if(!M){M=new Map();PET_RIG.src.set(sh,M)}
  const k=row+'|'+f;if(M.has(k))return M.get(k);
  const id=sh.getContext('2d',{willReadFrequently:true}).getImageData(f*W,row*H,W,H),u=new Uint32Array(id.data.buffer.slice(0));M.set(k,u);return u;
}
function petRigNeutral(m){
  if(!m)return true;if(m.bob)return false;
  for(const k of ['em','ew','tl','lg','bl','wk'])if(m[k]!=null&&Math.abs(m[k]-1)>0.04)return false;
  return true;
}
function petRigQ(v){return Math.round((v||1)*20)/20}
function petRigPoseKey(p){return p?[p.ear0||0,p.ear1||0,p.tail||0,p.flat?1:0,p.tw||0].join(','):''}
/* ---- the compositor ---- */
function petRigCompose(src,fr,m,pose,W,H){
  const MX=PET_RIG_MX,MT=PET_RIG_MT,CW=W+MX+PET_RIG_MR,CH=H+MT+PET_RIG_MB,out=new Uint32Array(CW*CH);
  const part=new Uint8Array(W*H);            /* 0 = body, 1.. = a part that gets re-drawn */
  fr.ears.forEach(function(e,i){e.px.forEach(function(p){part[p]=1+i})});
  if(fr.tail)fr.tail.px.forEach(function(p){part[p]=3});
  fr.wk.forEach(function(w){w.px.forEach(function(p){part[p]=4})});
  const sBody=fr.bd?petRigQ(m.bl):1,sLeg=fr.lg?petRigQ(m.lg):1;
  /* stretch bands: legs [ya,yb) rows, torso [xa,xb) columns */
  let ya=0,yb=0,hN=0,xa=0,xb=0,wN=0;
  if(fr.l&&sLeg!==1){ya=fr.l[0];yb=fr.l[1];hN=Math.max(1,Math.round((yb-ya)*sLeg))}
  if(fr.x&&sBody!==1){xa=fr.x[0];xb=fr.x[1];wN=Math.max(1,Math.round((xb-xa)*sBody))}
  const dY=hN?hN-(yb-ya):0,dX=wN?wN-(xb-xa):0;
  const X=function(x){return !wN||x<xa?x:x<xb?xa+(x-xa)*wN/(xb-xa):x+dX};
  const Y=function(y){return !hN||y<ya?y-dY:y<yb?ya-dY+(y-ya)*hN/(yb-ya):y};
  /* which source column / row feeds each destination one (the inverse of X and Y) */
  const cols=[],rows=[];
  for(let c=0;c<W+dX;c++)cols.push(!wN||c<xa?c:c<xa+wN?xa+Math.floor((c-xa)*(xb-xa)/wN):c-dX);
  for(let r=0;r<H+dY;r++)rows.push(!hN||r<ya?r:r<ya+hN?ya+Math.floor((r-ya)*(yb-ya)/hN):r-dY);
  /* body layer, with a note of which pixels sat against a removed part (their outline may need repair) */
  const edge=new Uint8Array(W*H);
  for(let y=0;y<H;y++)for(let x=0;x<W;x++){const i=y*W+x;if(part[i]||!(src[i]>>>24))continue;
    if((x>0&&part[i-1])||(x<W-1&&part[i+1])||(y>0&&part[i-W])||(y<H-1&&part[i+W]))edge[i]=1}
  const body=new Uint32Array(CW*CH),bedge=new Uint8Array(CW*CH);
  for(let r=0;r<rows.length;r++){const sy=rows[r],oy=r-dY+MT;if(oy<0||oy>=CH)continue;
    for(let c=0;c<cols.length;c++){const sx=cols[c],i=sy*W+sx;if(part[i])continue;const v=src[i];if(!(v>>>24))continue;
      const o=oy*CW+c+MX;body[o]=v;if(edge[i])bedge[o]=1}}
  /* part layers (drawn into `lay`, in canvas coordinates, shifted with the body where they attach) */
  const lay=new Uint32Array(CW*CH),top=new Uint32Array(CW*CH);let tgt=lay;
  const put=function(x,y,v){if(x>=0&&x<CW&&y>=0&&y<CH)tgt[y*CW+x]=v};
  const earS=[petRigQ(m.ew),petRigQ(m.em)];
  /* tail first, so the body and ears sit over it */
  if(fr.tail){const T=fr.tail,sl=petRigQ(m.tl),th=pose&&pose.tail?pose.tail*Math.PI/180:0,a0=T.a[0],ca=Math.cos(th),sa=Math.sin(th);
    const sx=X(a0[0])-a0[0],sy=Y(a0[1])-a0[1];
    const A=T.a.map(function(p){const dx=(p[0]-a0[0])*sl,dy=(p[1]-a0[1])*sl;return [a0[0]+dx*ca-dy*sa,a0[1]+dx*sa+dy*ca]});
    const n=A.length,len=[],tot=T.cum[n-1]*sl,dir=[];
    for(let i=0;i<n-1;i++){const dx=A[i+1][0]-A[i][0],dy=A[i+1][1]-A[i][1],l=Math.hypot(dx,dy)||1;len.push(l);dir.push([dx/l,dy/l])}
    let x0=1e9,x1=-1e9,y0=1e9,y1=-1e9;A.forEach(function(p){x0=Math.min(x0,p[0]);x1=Math.max(x1,p[0]);y0=Math.min(y0,p[1]);y1=Math.max(y1,p[1])});
    const sd=[],sl2=[];for(let i=0;i<n-1;i++){const dx=T.a[i+1][0]-T.a[i][0],dy=T.a[i+1][1]-T.a[i][1],l=Math.hypot(dx,dy)||1;sd.push([dx/l,dy/l]);sl2.push(l)}
    const R=7;
    for(let y=Math.floor(y0-R);y<=Math.ceil(y1+R);y++)for(let x=Math.floor(x0-R);x<=Math.ceil(x1+R);x++){
      const qx=x+0.5,qy=y+0.5;let best=1e9,bi=0,bl=0;
      for(let i=0;i<n-1;i++){const vx=qx-A[i][0],vy=qy-A[i][1],l=vx*dir[i][0]+vy*dir[i][1],t=Math.max(0,Math.min(len[i],l)),dx=vx-dir[i][0]*t,dy=vy-dir[i][1]*t,d=dx*dx+dy*dy;
        if(d<best){best=d;bi=i;bl=t}}
      if(best>R*R)continue;
      const dx=qx-A[bi][0],dy=qy-A[bi][1],w=dir[bi][0]*dy-dir[bi][1]*dx;
      let tp=0;for(let i=0;i<bi;i++)tp+=len[i];tp+=bl;                       /* arc length along the new axis */
      if(tp<-0.01||tp>tot+0.01)continue;
      const ts=Math.min(T.cum[n-1],tp/sl);let j=0;while(j<n-2&&T.cum[j+1]<=ts)j++;
      const u=(ts-T.cum[j])/(sl2[j]||1),px=T.a[j][0]+sd[j][0]*u*sl2[j]+(-sd[j][1])*w,py=T.a[j][1]+sd[j][1]*u*sl2[j]+sd[j][0]*w;
      const ix=Math.floor(px),iy=Math.floor(py);if(ix<0||ix>=W||iy<0||iy>=H)continue;const si=iy*W+ix;
      if(part[si]===3&&src[si]>>>24)put(Math.round(x+sx)+MX,Math.round(y+sy)+MT,src[si]);
    }}
  /* ears */
  tgt=top;
  fr.ears.forEach(function(e,k){
    const b=e.b,ux=e.t[0]-b[0],uy=e.t[1]-b[1],L=Math.hypot(ux,uy)||1,u=[ux/L,uy/L],nrm=[-u[1],u[0]];
    const th=(pose&&(k===0?pose.ear0:pose.ear1)||0)*Math.PI/180,ca=Math.cos(th),sa=Math.sin(th);
    const u2=[u[0]*ca-u[1]*sa,u[0]*sa+u[1]*ca],n2=[-u2[1],u2[0]],sl=earS[1]*(pose&&pose.flat?0.85:1),sw=earS[0];
    const sx=X(b[0])-b[0],sy=Y(b[1])-b[1];
    let x0=1e9,x1=-1e9,y0=1e9,y1=-1e9;
    e.px.forEach(function(p){const x=p%W+0.5,y=Math.floor(p/W)+0.5,t=(x-b[0])*u[0]+(y-b[1])*u[1],w=(x-b[0])*nrm[0]+(y-b[1])*nrm[1],
      fx=b[0]+t*sl*u2[0]+w*sw*n2[0],fy=b[1]+t*sl*u2[1]+w*sw*n2[1];x0=Math.min(x0,fx);x1=Math.max(x1,fx);y0=Math.min(y0,fy);y1=Math.max(y1,fy)});
    const pad=Math.ceil(Math.max(sl,sw))+1;
    for(let y=Math.floor(y0-pad);y<=Math.ceil(y1+pad);y++)for(let x=Math.floor(x0-pad);x<=Math.ceil(x1+pad);x++){
      const dx=x+0.5-b[0],dy=y+0.5-b[1],t=(dx*u2[0]+dy*u2[1])/sl,w=(dx*n2[0]+dy*n2[1])/sw,
        px=b[0]+t*u[0]+w*nrm[0],py=b[1]+t*u[1]+w*nrm[1],ix=Math.floor(px),iy=Math.floor(py);
      if(ix<0||ix>=W||iy<0||iy>=H)continue;const si=iy*W+ix;
      if(part[si]===1+k&&src[si]>>>24)put(Math.round(x+sx)+MX,Math.round(y+sy)+MT,src[si])}
  });
  /* whiskers: pixels kept in order of distance from the root, longer ones get extra pixels in the same direction */
  const sk=petRigQ(m.wk);
  fr.wk.forEach(function(w){
    const rx=w.b[0],ry=w.b[1],dx=w.t[0]-rx,dy=w.t[1]-ry,L=Math.hypot(dx,dy)||1,ux=dx/L,uy=dy/L,sx=X(rx)-rx,sy=Y(ry)-ry;
    let last=0;
    w.px.forEach(function(p){const x=p%W+0.5,y=Math.floor(p/W)+0.5,d=Math.hypot(x-rx,y-ry);
      if(sk<1&&d>L*sk+0.5)return;put(Math.round(x-0.5+sx)+MX,Math.round(y-0.5+sy)+MT,src[p]);if(!last||d>Math.hypot(last%W+0.5-rx,Math.floor(last/W)+0.5-ry))last=p});
    if(sk>1&&last){const col=src[last],ex=Math.round(L*(sk-1)),lx=last%W+0.5,ly=Math.floor(last/W)+0.5;
      for(let s=1;s<=ex;s++)put(Math.round(lx-0.5+ux*s+sx)+MX,Math.round(ly-0.5+uy*s+sy)+MT,col)}
  });
  /* compose: tail/ears/whiskers layer under the body for the tail, over it for ears and whiskers */
  const res=new Uint32Array(CW*CH);
  for(let i=0;i<res.length;i++){res[i]=top[i]>>>24?top[i]:body[i]>>>24?body[i]:lay[i]}
  /* repair the outline where a part was cut away and nothing now covers the cut */
  for(let y=0;y<CH;y++)for(let x=0;x<CW;x++){const i=y*CW+x;
    if(!bedge[i]||!(res[i]>>>24)||res[i]!==body[i]||(top[i]>>>24))continue;
    if(x===0||y===0||x===CW-1||y===CH-1||!(res[i-1]>>>24)||!(res[i+1]>>>24)||!(res[i-CW]>>>24)||!(res[i+CW]>>>24))res[i]=PET_RIG_OUTLINE}
  return {px:res,CW:CW,CH:CH};
}
function petRigFrame(rt,sh,kind,anim,f,morph,pose){
  try{
    if(!sh||!morph||(petRigNeutral(morph)&&!pose))return null;
    const A=rt.A,a=A.anims[anim];if(!a)return null;const W=A.W,H=A.H;
    const fr=petRigFrameData(kind,anim,f%a.n);if(!fr)return null;
    const m={em:morph.em,ew:morph.ew,tl:morph.bob?Math.min(morph.tl,0.3):morph.tl,lg:morph.lg,bl:morph.bl,wk:morph.wk};
    const key=[anim,f%a.n,petRigQ(m.em),petRigQ(m.ew),petRigQ(m.tl),petRigQ(m.lg),petRigQ(m.bl),petRigQ(m.wk),petRigPoseKey(pose)].join('|');
    PET_RIG.out=PET_RIG.out||new WeakMap();let C=PET_RIG.out.get(sh);if(!C){C=new Map();PET_RIG.out.set(sh,C)}
    let hit=C.get(key);if(hit)return hit;
    const src=petRigSrcPixels(sh,a.row,f%a.n,W,H),r=petRigCompose(src,fr,m,pose,W,H);
    const cv=document.createElement('canvas');cv.width=r.CW;cv.height=r.CH;const x=cv.getContext('2d'),id=x.createImageData(r.CW,r.CH);
    new Uint32Array(id.data.buffer).set(r.px);x.putImageData(id,0,0);
    hit={cv:cv,ox:PET_RIG_MX,oy:PET_RIG_MT,bottom:fr.y};
    if(C.size>600)C.delete(C.keys().next().value);C.set(key,hit);return hit;
  }catch(e){console.warn('[pet rig]',e);return null}
}
/* the draw hook used by petDrawCat (17): adult cats only; kittens, the front/back views and any row the rig cannot map keep their art */
function petRigPick(rt,p,sh,meta,c,f,bv,gi,depthRow){
  if(gi>=0||bv||depthRow)return null;
  const ph=petPheno(p);if(!ph||!ph.morph)return null;
  return petRigFrame(rt,sh,'adult',rt.A.anims[c.anim]?c.anim:'idle_sit',f,ph.morph,null);
}
