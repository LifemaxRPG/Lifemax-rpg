Mix(a,b,t){const p=h=>[1,3,5].map(i=>parseInt(h.slice(i,i+2),16)),x=p(a),y=p(b);return '#'+x.map((v,i)=>Math.round(v+(y[i]-v)*t).toString(16).padStart(2,'0')).join('')}
function onbPal(col){const K='#090c14',W='#ffffff';return {o:K,c:col,d:onbMix(col,K,.38),D:onbMix(col,K,.62),l:onbMix(col,W,.38),L:onbMix(col,W,.72),W:'#f6f9ff',s:'#cfd6e6',g:'#9aa4b8',G:'#586178',k:'#2b3245',r:'#ff5a6a',R:'#b8324a',y:'#ffd35c',Y:'#d9971f',n:'#ff9a3c',N:'#c9561a',p:'#ff8fb8',P:'#c9507f',b:'#6bc5ff',B:'#2f7fc4',e:'#6fe39a',E:'#2a9a5c',q:'#ffd9b3',Q:'#f2b184',z:'#cd8560',Z:'#94543a',K:'#ffeed8'}}
/* Pixel-art class crests: 32x32 sprites, each letter is a palette colour, tinted by the class colour */
/* ---- Class crest animations: shared engine + one small actor per class ---- */
const onbDec=rows=>rows.map(r=>r.replace(/(.)(\d+)/g,(m,c,n)=>c.repeat(+n)));
function onbRowsToPaths(rows,pal,y0){const by={};y0=y0||0;
 rows.forEach((row,j)=>{const y=j+y0;let x=0;while(x<row.length){const ch=row[x];if(ch==='.'){x++;continue}let e=x;while(e<row.length&&row[e]===ch)e++;(by[ch]=by[ch]||[]).push('M'+x+' '+y+'h'+(e-x)+'v1h-'+(e-x)+'z');x=e}});
 return Object.keys(by).map(ch=>'<path fill="'+(pal[ch]||'#fff')+'" d="'+by[ch].join('')+'"/>').join('')}
const onbP=(rows,pal,y0)=>onbRowsToPaths(onbDec(rows),pal,y0);
const onbEase=u=>u<.5?2*u*u:1-Math.pow(-2*u+2,2)/2;
const onbR=(x,y,w,h,f,o)=>'<rect x="'+x+'" y="'+y+'" width="'+w+'" height="'+h+'" fill="'+f+'"'+(o<1?' opacity="'+o.toFixed(2)+'"':'')+'/>';
const onbRnd=s=>()=>{s=s*16807%2147483647;return(s-1)/2147483646};
const onbStars=(st,pal)=>st.map(s=>'<rect class="rk-star" style="animation-delay:'+(s[3]*.43).toFixed(2)+'s" x="'+s[0]+'" y="'+s[1]+'" width="1" height="1" fill="'+pal[s[2]]+'"/>').join('');
const onbSeq=(a,k,d)=>a[Math.max(0,Math.min(a.length-1,Math.floor(k/d)))];
function onbFx(parts,pal,cols,sizes){return parts.map(p=>{const q=p.age/p.life,s=sizes?sizes(q):1,o=q<.55?1:q<.8?.7:.4;return onbR(Math.round(p.x-s/2),Math.round(p.y-s/2),s,s,pal[p.c||cols[Math.min(cols.length-1,Math.floor(q*cols.length))]],o)}).join('')}
const ONB_ACTORS={
 entrepreneur:{
  build(pal){const A=ONB_DATA.entrepreneur;return '<g class="rk-stars">'+onbStars(A.stars,pal)+'</g><g class="rk-fx"></g><g class="rk-ship"><g class="rk-body">'+onbP(A.body,pal)+'</g><g class="rk-flame">'+onbP(A.flames[0].rows,pal,A.flames[0].y)+'</g></g>'},
  start(svg,pal){const A=ONB_DATA.entrepreneur,ship=svg.querySelector('.rk-ship'),fl=svg.querySelector('.rk-flame'),fx=svg.querySelector('.rk-fx'),flames=A.flames.map(f=>onbP(f.rows,pal,f.y));
   const TA=.9,TB=1,TC=1.1,TD=1.3,TI=2.6,TT=TA+TB+TC+TD+TI,T1=TA,T2=TA+TB,T3=T2+TC,T4=T3+TD,cols=['s','g','G','k'],SX=[3,7,11,21,25,29,14],rnd=onbRnd(7);
   let parts=[],fi=-1,spawn=0;const puff=(x,y,vx,vy,life)=>{if(parts.length<44)parts.push({x:x,y:y,vx:vx,vy:vy,life:life,age:0})};
   return{period:TT,
    frame(t,dt,wrap){if(wrap)parts=[];let dx=0,dy=0,show=true,f=0,streak=-1;spawn+=dt;
     if(t<T1){dx=t>.25?(Math.floor(t*30)%2?1:-1):0;f=t<.45?1+Math.floor(t*16)%2:3+Math.floor(t*20)%2;if(t>.2&&spawn>.07){spawn=0;for(let i=0;i<2;i++)puff(16+(rnd()-.5)*4,26,(rnd()-.5)*30,6+rnd()*8,.7+rnd()*.5)}}
     else if(t<T2){const u=t-T1;dy=-Math.round(60*u*u);f=3+Math.floor(t*24)%2;streak=u;if(spawn>.045){spawn=0;puff(16+(rnd()-.5)*3,26+dy,(rnd()-.5)*8,3+rnd()*6,.9+rnd()*.5)}}
     else if(t<T3){show=false}
     else if(t<T4){const u=(t-T3)/TD,e=1-Math.pow(1-u,3);dy=-Math.round(60*(1-e));if(u>.93)dy=1;f=u<.85?3+Math.floor(t*22)%2:1+Math.floor(t*16)%2;if(spawn>.09){spawn=0;puff(16+(rnd()-.5)*3,26+dy,(rnd()-.5)*10,3+rnd()*4,.6+rnd()*.4)}}
     else f=Math.floor(t*9)%3;
     parts=parts.filter(p=>(p.age+=dt)<p.life);parts.forEach(p=>{p.x+=p.vx*dt;p.y+=p.vy*dt;p.vx*=.96});
     let h='';if(streak>=0){const o=Math.min(1,streak*5)*Math.min(1,(1-streak)*6)*.55;SX.forEach((x,k)=>{h+=onbR(x,Math.round((k*9+streak*150)%40-6),1,4,pal.L,o)})}
     h+=onbFx(parts,pal,cols,q=>q<.2?2:q<.5?3:q<.8?4:3);fx.innerHTML=h;
     if(f!==fi){fi=f;fl.innerHTML=flames[f]}ship.setAttribute('transform','translate('+dx+' '+dy+')');ship.style.visibility=show?'visible':'hidden'},
    stop(){ship.removeAttribute('transform');ship.style.visibility='visible';fx.innerHTML='';fl.innerHTML=flames[0]}}}},
 scholar:{
  build(pal){const D=ONB_DATA.scholar;return '<g class="rk-stars">'+onbStars(D.stars,pal)+'</g><g class="sc-star">'+onbP(D.star,pal)+'</g><g class="sc-fx"></g><g class="sc-book">'+onbP(D.frames[0],pal)+'</g>'},
  start(svg,pal){const D=ONB_DATA.scholar,fr=D.frames.map(f=>onbP(f,pal)),n=fr.length-1,book=svg.querySelector('.sc-book'),star=svg.querySelector('.sc-star'),fx=svg.querySelector('.sc-fx'),rnd=onbRnd(11);
   let bi=0,parts=[],burst=false;
   return{period:8,
    frame(t,dt,wrap){if(wrap){parts=[];burst=false}let idx=0,bdy=0,on=true,sdy=Math.floor(t/.6)%2?-1:0;
     if(t<1.6){idx=(t>.9&&t<1.0)||(t>1.2&&t<1.3)?1:0}
     else if(t<2.5){const u=(t-1.6)/.9;idx=Math.round(n*onbEase(u));on=u<.2;sdy=0}
     else if(t<3.5){idx=n;on=false;bdy=t<2.6?1:0}
     else if(t<4.4){const u=(t-3.5)/.9;idx=Math.round(n*(1-onbEase(u)));on=false}
     else{const k=t-4.4;sdy=k<.1?-3:k<.2?-2:k<.3?-1:sdy;if(!burst){burst=true;for(let i=0;i<10;i++)parts.push({x:16,y:13,vx:(rnd()-.5)*44,vy:-16-rnd()*22,life:.6+rnd()*.5,age:0,c:['y','W','L'][i%3]})}}
     parts=parts.filter(p=>(p.age+=dt)<p.life);parts.forEach(p=>{p.x+=p.vx*dt;p.y+=p.vy*dt;p.vy+=40*dt});
     fx.innerHTML=onbFx(parts,pal,['y'],q=>q<.5?2:1);
     if(idx!==bi){bi=idx;book.innerHTML=fr[idx]}book.setAttribute('transform','translate(0 '+bdy+')');
     star.style.visibility=on?'visible':'hidden';star.setAttribute('transform','translate(0 '+sdy+')')},
    stop(){book.innerHTML=fr[0];book.removeAttribute('transform');star.style.visibility='visible';star.removeAttribute('transform');fx.innerHTML=''}}}},
 athlete:{
  build(pal){const D=ONB_DATA.athlete;return '<g class="rk-stars">'+onbStars(D.stars,pal)+'</g><g class="at-arm">'+onbP(D.frames[2],pal)+'</g><g class="at-fx"></g>'},
  start(svg,pal){const D=ONB_DATA.athlete,fr=D.frames.map(f=>onbP(f,pal)),arm=svg.querySelector('.at-arm'),fx=svg.querySelector('.at-fx');
   const LINES=[[12,9],[12,8],[12,7],[9,10],[8,9],[7,8],[15,10],[16,9],[17,8]],strain=LINES.map((p,i)=>onbR(p[0],p[1],1,1,pal[i%3===0?'W':'L'],1)).join('');
   let ai=2,fxOn=false;
   return{period:7.5,
    frame(t,dt){let i=2,dx=0,peak=false;
     if(t<1)i=2;else if(t<1.5)i=onbSeq([1,0,0],t-1,.17);else if(t<2)i=0;else if(t<2.5)i=onbSeq([1,2,3],t-2,.17);
     else if(t<3.3){i=4;peak=true}else if(t<3.6)i=3;else if(t<4.5)i=2;
     else if(t<4.7)i=onbSeq([1,0],t-4.5,.1);else if(t<5)i=onbSeq([1,2,3],t-4.7,.1);else if(t<5.7){i=4;peak=true}else if(t<6)i=3;else i=2;
     if(peak)dx=Math.floor(t*30)%2?1:-1;
     if(i!==ai){ai=i;arm.innerHTML=fr[i]}arm.setAttribute('transform','translate('+dx+' 0)');
     if(peak!==fxOn){fxOn=peak;fx.innerHTML=peak?strain:''}},
    stop(){arm.innerHTML=fr[2];arm.removeAttribute('transform');fx.innerHTML=''}}}},
 disciple:{
  build(pal){const D=ONB_DATA.disciple;return '<g class="rk-stars">'+onbStars(D.stars,pal)+'</g><g class="di-water">'+onbP(D.water,pal)+'</g><g class="di-fx"></g><g class="di-petals">'+onbP(D.frames[5],pal)+'</g><g class="di-orb">'+onbP(D.orbs[1],pal)+'</g>'},
  start(svg,pal){const D=ONB_DATA.disciple,fr=D.frames.map(f=>onbP(f,pal)),orbs=D.orbs.map(o=>onbP(o,pal)),pet=svg.querySelector('.di-petals'),orb=svg.querySelector('.di-orb'),fx=svg.querySelector('.di-fx');
   const RIP=[.2,3.9,5.4,7.0];let pi=5,oi=1;
   return{period:8.4,
    frame(t){let i=5,o=1,oy=[0,-1,0,1][Math.floor(t/.45)%4];
     if(t<1.4)i=5;else if(t<2.7){const u=(t-1.4)/1.3;i=Math.round(5*(1-onbEase(u)));o=i>2?1:0}
     else if(t<3.4){i=0;o=0;oy=1}else if(t<5.2){const u=(t-3.4)/1.8;i=Math.round(5*onbEase(u));o=i>=3?1:0;if(i<3)oy=1}
     else i=Math.floor((t-5.2)/1.1)%2?4:5;
     if(i!==pi){pi=i;pet.innerHTML=fr[i]}if(o!==oi){oi=o;orb.innerHTML=orbs[o]}orb.setAttribute('transform','translate(0 '+oy+')');
     let h='';RIP.forEach(r0=>{const a=t-r0;if(a<0||a>1.7)return;const rx=3+a*8.2,ry=Math.max(1,rx*.17),q=a/1.7;
      for(let y=24;y<32;y++){let x0=-1;for(let x=0;x<=32;x++){const v=x<32?Math.sqrt(Math.pow((x-15.5)/rx,2)+Math.pow((y-27.5)/ry,2)):9,on=Math.abs(v-1)<.2;if(on&&x0<0)x0=x;if(!on&&x0>=0){h+=onbR(x0,y,x-x0,1,pal[q<.4?'W':'b'],1-q);x0=-1}}}});
     fx.innerHTML=h},
    stop(){pet.innerHTML=fr[5];orb.innerHTML=orbs[1];orb.removeAttribute('transform');fx.innerHTML=''}}}},
 diplomat:{
  build(pal){const D=ONB_DATA.diplomat;return '<g class="rk-stars">'+onbStars(D.stars,pal)+'</g><g class="dp-b">'+onbP(D.B[2],pal)+'</g><g class="dp-bc">'+onbP(D.dots[3],pal)+'</g><g class="dp-a">'+onbP(D.A[2],pal)+'</g><g class="dp-ac">'+onbP(D.heartA[0],pal)+'</g><g class="dp-fx"></g>'},
  start(svg,pal){const D=ONB_DATA.diplomat,A=D.A.map(f=>onbP(f,pal)),B=D.B.map(f=>onbP(f,pal)),dots=D.dots.map(f=>onbP(f,pal)),hB=onbP(D.heartB,pal),hA=D.heartA.map(f=>onbP(f,pal));
   const gb=svg.querySelector('.dp-b'),gbc=svg.querySelector('.dp-bc'),ga=svg.querySelector('.dp-a'),gac=svg.querySelector('.dp-ac'),fx=svg.querySelector('.dp-fx'),rnd=onbRnd(5);
   const cur={a:2,b:2,bc:3,ha:0};let hearts=[],spawned=0;
   const upA=(a,ha)=>{if(a!==cur.a){cur.a=a;ga.innerHTML=a<0?'':A[a]}const h=a>=2?ha:-1;if(h!==cur.ha){cur.ha=h;gac.innerHTML=h<0?'':hA[h]}};
   const upB=(b,bc)=>{if(b!==cur.b){cur.b=b;gb.innerHTML=b<0?'':B[b]}const c=b>=2?bc:-1;if(c!==cur.bc){cur.bc=c;gbc.innerHTML=c<0?'':c==='h'?hB:dots[c]}};
   return{period:8,
    frame(t,dt,wrap){if(wrap){hearts=[];spawned=0}let a=2,b=2,bc=3,ha=0;
     if(t<1)a=b=2;
     else if(t<1.6){a=onbSeq([2,1,0,-1],t-1,.08);b=onbSeq([2,1,0,-1],t-1.1,.08)}
     else if(t<2.1){a=b=-1}
     else{a=t<2.1?-1:onbSeq([0,1,3,2],t-2.1,.09);b=t<2.5?-1:onbSeq([0,1,3,2],t-2.5,.09)}
     if(t>=2.9&&t<4.7)bc=Math.floor(t/.22)%3;
     if(t>=4.7&&t<6.5){bc='h';ha=Math.floor((t-4.7)/.22)%2;
      while(spawned<5&&t>=4.9+spawned*.3){spawned++;hearts.push({x:14+rnd()*8,y:13,vx:(rnd()-.5)*6,vy:-9-rnd()*5,life:1.1+rnd()*.4,age:0,ph:rnd()*6})}}
     else if(t>=6.5&&t<6.7)bc='h';
     upA(a,ha);upB(b,bc);
     hearts=hearts.filter(p=>(p.age+=dt)<p.life);hearts.forEach(p=>{p.y+=p.vy*dt;p.x+=Math.sin(p.age*5+p.ph)*10*dt+p.vx*dt});
     fx.innerHTML=hearts.map(p=>{const x=Math.round(p.x),y=Math.round(p.y),o=Math.max(.2,1-p.age/p.life),f=pal.p;return onbR(x,y,1,1,f,o)+onbR(x+2,y,1,1,f,o)+onbR(x,y+1,3,1,f,o)+onbR(x+1,y+2,1,1,f,o)}).join('')},
    stop(){gb.innerHTML=B[2];gbc.innerHTML=dots[3];ga.innerHTML=A[2];gac.innerHTML=hA[0];fx.innerHTML=''}}}},
 explorer:{
  build(pal){const D=ONB_DATA.explorer,cap=[];for(let y=0;y<32;y++){let r='';for(let x=0;x<32;x++){const d=Math.pow(x-15.5,2)+Math.pow(y-15.5,2);r+=x===15&&y===15?'W':d<=2.56?'y':'.'}cap.push(r)}
   return '<g class="rk-stars">'+onbStars(D.stars,pal)+'</g><g class="ex-base">'+onbP(D.base,pal)+'</g><g class="ex-needle">'+onbNeedle(pal,-Math.PI/4)+'</g><g class="ex-cap">'+onbRowsToPaths(cap,pal)+'</g><g class="ex-glint" opacity=".4"></g>'},
  start(svg,pal){const nd=svg.querySelector('.ex-needle'),gl=svg.querySelector('.ex-glint'),T=-Math.PI/4,W=8.5,Z=1.0,KICKS=[[1,34],[5.4,14]];
   const glint=p=>{const rows=[];for(let y=0;y<32;y++){let r='';for(let x=0;x<32;x++){const d=Math.pow(x-15.5,2)+Math.pow(y-15.5,2),s=x+y;r+=(d<=90&&(s===p||s===p+1))?'W':'.'}rows.push(r)}return onbRowsToPaths(rows,pal)};
   let last='',lg=-1;
   return{period:9,
    frame(t){let a=T;KICKS.forEach(k=>{const s=t-k[0];if(s>0)a+=k[1]/W*Math.exp(-Z*s)*Math.sin(W*s)});a+=t>6.4?Math.sin(t*3)*.02:0;
     const key=a.toFixed(2);if(key!==last){last=key;nd.innerHTML=onbNeedle(pal,a)}
     let g=-1;[1,5.4].forEach(s=>{const u=(t-s)/.55;if(u>=0&&u<=1)g=Math.round(9+u*14)});if(g!==lg){lg=g;gl.innerHTML=g<0?'':glint(g)}},
    stop(){nd.innerHTML=onbNeedle(pal,T);gl.innerHTML=''}}}}
};
function onbNeedle(pal,ang){const cx=16,cy=16,ux=Math.cos(ang),uy=Math.sin(ang),px=-uy,py=ux,L=11,W=3,tN=[cx+ux*L,cy+uy*L],tS=[cx-ux*L,cy-uy*L],s1=[cx+px*W,cy+py*W],s2=[cx-px*W,cy-py*W];
 const d=(p,q,r)=>(p[0]-r[0])*(q[1]-r[1])-(q[0]-r[0])*(p[1]-r[1]),inT=(x,y,a,b,c)=>{const P=[x,y],d1=d(P,a,b),d2=d(P,b,c),d3=d(P,c,a);return!((d1<0||d2<0||d3<0)&&(d1>0||d2>0||d3>0))},rows=[];
 for(let y=0;y<32;y++){let r='';for(let x=0;x<32;x++){const X=x+.5,Y=y+.5,sh=x+y>=32;r+=inT(X,Y,tN,s1,s2)?(sh?'R':'r'):inT(X,Y,tS,s1,s2)?(sh?'s':'W'):'.'}rows.push(r)}
 return onbRowsToPaths(rows,pal)}
function onbSvgCrest(kind,color){const a=ONB_ACTORS[kind],pal=onbPal(color||'#79e7ff');return '<svg viewBox="0 0 32 32" shape-rendering="crispEdges" aria-hidden="true" data-anim="'+kind+'">'+a.build(pal)+'</svg>'}
const RK={timer:0,raf:0,svg:null,target:null,act:null};
function onbAnimStop(){clearTimeout(RK.timer);cancelAnimationFrame(RK.raf);RK.raf=0;if(RK.act&&RK.act.stop)RK.act.stop();RK.act=null;RK.svg=null;RK.target=null}
function onbAnimSchedule(){const card=document.querySelector('.onb-class-card3d.active'),svg=card&&card.querySelector('svg[data-anim]');
 if(svg&&svg===RK.target)return;onbAnimStop();RK.target=svg||null;
 if(!svg||(window.matchMedia&&matchMedia('(prefers-reduced-motion: reduce)').matches))return;
 RK.timer=setTimeout(()=>onbAnimRun(svg),500)}
function onbAnimRun(svg){const kind=svg.getAttribute('data-anim'),A=ONB_ACTORS[kind];if(!A)return;
 const act=A.start(svg,onbPal(ONB_CLASS_VISUALS[kind].color));RK.svg=svg;RK.act=act;
 let t0=performance.now(),last=0,wrap=true;
 const live=()=>{const ob=document.getElementById('onboarding');return svg.isConnected&&svg.closest('.onb-class-card3d.active')&&(!ob||ob.classList.contains('show'))};
 function tick(now){if(!live()){onbAnimStop();return}RK.raf=requestAnimationFrame(tick);if(now-last<33)return;const dt=Math.min(.08,last?(now-last)/1000:.033);last=now;
  let t=(now-t0)/1000;if(t>=act.period){t0=now;t=0;wrap=true}act.frame(t,dt,wrap);wrap=false}
 RK.raf=requestAnimationFrame(tick)}

function renderOnboardingClasses(){
 const carousel=document.getElementById('onbClassCarousel'),dots=document.getElementById('onbClassDots');if(!carousel||!dots)return;
 const existing=document.getElementById('onbClassId').value;
 if(existing&&classById(existing))_onbSelectedClass=existing;
 if(!_onbSelectedClass||!classById(_onbSelectedClass))_onbSelectedClass='athlete';
 _onbCarouselIndex=Math.max(0,CLASSES.findIndex(c=>c.id===_onbSelectedClass));
 carousel.innerHTML='';dots.innerHTML='';
 CLASSES.forEach((c,i)=>{
  const v=ONB_CLASS_VISUALS[c.id]||ONB_CLASS_VISUALS.athlete;
  const card=document.createElement('button');card.type='button';card.className='onb-class-card3d';card.dataset.index=String(i);card.style.setProperty('--class-color',v.color);card.setAttribute('role','option');card.setAttribute('aria-label',`${c.name} class, ${c.ready?'playable':'coming soon'}`);
  card.innerHTML=`<div class="onb-class-card-content"><div class="onb-class-top"><span class="onb-class-number">CLASS // 0${i+1}</span><span class="onb-class-status ${c.ready?'ready':'soon'}">${c.ready?'PLAYABLE':'COMING SOON'}</span></div><div class="onb-class-crest">${onbSvgCrest(v.crest,v.color)}</div><div class="onb-class-name">${c.name}</div><div class="onb-class-tagline">${v.title}</div><p class="onb-class-desc">${v.desc}</p><div class="onb-class-hint"></div></div>`;
  card.addEventListener('click',()=>{if(!_onbWasDragged)selectOnboardingClass(c.id)});carousel.appendChild(card);
  const dot=document.createElement('button');dot.type='button';dot.setAttribute('aria-label',`Select ${c.name}`);dot.addEventListener('click',()=>selectOnboardingClass(c.id));dots.appendChild(dot);
 });
 document.getElementById('onbClassPrev').onclick=()=>setOnboardingCarouselIndex(_onbCarouselIndex-1);
 document.getElementById('onbClassNext').onclick=()=>setOnboardingCarouselIndex(_onbCarouselIndex+1);
 carousel.onkeydown=e=>{if(e.key==='ArrowLeft'){e.preventDefault();setOnboardingCarouselIndex(_onbCarouselIndex-1)}else if(e.key==='ArrowRight'){e.preventDefault();setOnboardingCarouselIndex(_onbCarouselIndex+1)}else if(e.key==='Enter'||e.key===' '){e.preventDefault();selectOnboardingClass(CLASSES[_onbCarouselIndex].id)}};
 carousel.onpointerdown=e=>{if(e.pointerType==='mouse'&&e.button!==0)return;_onbDrag={x:e.clientX,y:e.clientY,id:e.pointerId};_onbWasDragged=false;carousel.classList.add('dragging');try{carousel.setPointerCapture(e.pointerId)}catch(_){}};
 carousel.onpointermove=e=>{if(!_onbDrag)return;const dx=e.clientX-_onbDrag.x,dy=e.clientY-_onbDrag.y;if(Math.abs(dx)>8&&Math.abs(dx)>Math.abs(dy))_onbWasDragged=true};
 const endDrag=e=>{if(!_onbDrag)return;const dx=e.clientX-_onbDrag.x;_onbDrag=null;carousel.classList.remove('dragging');if(Math.abs(dx)>35){_onbWasDragged=true;setOnboardingCarouselIndex(_onbCarouselIndex+(dx<0?1:-1));setTimeout(()=>{_onbWasDragged=false},100)}else setTimeout(()=>{_onbWasDragged=false},0)};
 carousel.onpointerup=endDrag;carousel.onpointercancel=()=>{_onbDrag=null;carousel.classList.remove('dragging');_onbWasDragged=false};
 updateOnboardingCarousel();
}
function setOnboardingCarouselIndex(i){_onbCarouselIndex=(i+CLASSES.length)%CLASSES.length;selectOnboardingClass(CLASSES[_onbCarouselIndex].id)}
function updateOnboardingCarousel(){
 const carousel=document.getElementById('onbClassCarousel'),dots=document.getElementById('onbClassDots');if(!carousel||!carousel.children.length)return;
 [...carousel.children].forEach((card,i)=>{let offset=i-_onbCarouselIndex;while(offset>CLASSES.length/2)offset-=CLASSES.length;while(offset< -CLASSES.length/2)offset+=CLASSES.length;const abs=Math.abs(offset),mobile=window.matchMedia('(max-width:520px)').matches,x=offset*(mobile?112:132),rotate=offset*25,scale=offset===0?1.035:Math.max(.78,.88-abs*.025);card.style.transform=`translate(-50%,-50%) translateX(${x}px) rotateY(${-rotate}deg) scale(${scale})`;card.style.zIndex=String(20-abs);card.style.opacity=abs>2?'0':String(abs===0?1:Math.max(.38,.82-abs*.12));card.style.filter=abs===0?'none':`blur(${Math.min(abs*.55,1.25)}px) saturate(.72)`;card.style.pointerEvents=abs>2?'none':'auto';card.classList.toggle('active',i===_onbCarouselIndex);card.setAttribute('aria-selected',String(i===_onbCarouselIndex));card.tabIndex=i===_onbCarouselIndex?0:-1;const hint=card.querySelector('.onb-class-hint');if(hint)hint.textContent=i===_onbCarouselIndex?'SELECTED PATH':'VIEW CLASS'});
 [...dots.children].forEach((d,i)=>d.classList.toggle('active',i===_onbCarouselIndex));
 onbAnimSchedule();if(onboardingPreviewMode&&onboardingStep===5){var nb=document.getElementById('onbNext');if(nb)nb.innerHTML=onbPreviewLabel()}
}
function selectOnboardingClass(id){
 const index=CLASSES.findIndex(c=>c.id===id);if(index<0)return;
 _onbSelectedClass=id;_onbCarouselIndex=index;document.getElementById('onbClassId').value=id;document.getElementById('onbBuild').value=classById(id).name;updateOnboardingCarousel();
}
function renderOnboardingClassDetail(id){selectOnboardingClass(id||_onbSelectedClass||'athlete')}

window.addEventListener('resize',()=>{if(document.getElementById('onbClassCarousel'))updateOnboardingCarousel()});
function showOnboardingStep(n){onboardingStep=Math.max(0,Math.min(5,n));document.querySelectorAll('.onb-step').forEach(el=>el.classList.toggle('active',Number(el.dataset.step)===onboardingStep));document.querySelectorAll('#onbProgress span').forEach((el,i)=>el.classList.toggle('active',i<=onboardingStep));const back=document.getElementById('onbBack'),next=document.getElementById('onbNext');back.disabled=onboardingStep===0;back.style.visibility=onboardingStep===0?'hidden':'visible';const shell=document.querySelector('#onboarding .onb-shell');if(shell)shell.classList.toggle('class-step',onboardingStep===5);next.innerHTML=onboardingStep===5?(onboardingPreviewMode?onbPreviewLabel():'Begin as this class <span aria-hidden="true">→</span>'):'Continue <span aria-hidden="true">→</span>';document.getElementById('onbKicker').textContent=['A new chapter begins','Let’s get to know you','Your starting point','One last detail','Make it yours','Choose your path'][onboardingStep];if(onboardingStep===5)renderOnboardingClasses();const active=document.querySelector('.onb-step.active input:not([type=file]):not([type=hidden])');if(active&&onboardingStep<5)setTimeout(()=>active.focus({preventScroll:true}),80)}
function nextOnboardingStep(){if(onboardingStep===0&&!document.getElementById('onbName').value.trim()){document.getElementById('onbName').focus();toast('What should we call you?');return}if(onboardingStep===1){const age=Number(document.getElementById('onbAge').value);if(age&&(age<13||age>120)){toast('Enter an age between 13 and 120, or leave it blank');return}}if(onboardingStep===2){const weight=Number(document.getElementById('onbWeight').value);if(weight&&(weight<1||weight>1400)){toast('Enter a valid weight, or leave it blank');return}}if(onboardingStep===3){const ft=Number(document.getElementById('onbHeightFt').value),inch=Number(document.getElementById('onbHeightIn').value);if(ft&&(ft<1||ft>8)){toast('Enter a valid height in feet');return}if(inch&&(inch<0||inch>11)){toast('Height inches must be between 0 and 11');return}}if(onboardingStep===5){if(!document.getElementById('onbClassId').value){toast('Select a class to continue');return}finishOnboarding();return}if(onboardingStep<5){showOnboardingStep(onboardingStep+1);return}finishOnboarding()}
function prevOnboardingStep(){if(onboardingStep>0)showOnboardingStep(onboardingStep-1)}
function finishOnboarding(){if(onboardingPreviewMode){const sel=(document.getElementById('onbClassId')||{}).value||state.classId||'athlete',nid=onbPreviewTarget();closeOnboardingPreview();if(nid){switchClassTo(nid);toast('Switched to '+classById(nid).name+' · plan ended, class content refreshed')}else if(sel!==(state.classId||'athlete'))toast(classById(sel).name+' is not playable yet · nothing changed');else toast('Preview closed · your save is unchanged');return}const name=document.getElementById('onbName').value.trim();const age=Number(document.getElementById('onbAge').value);const weight=Number(document.getElementById('onbWeight').value);const ft=Number(document.getElementById('onbHeightFt').value);const inch=Number(document.getElementById('onbHeightIn').value);state.name=name||'The Life Architect';state.age=age||null;const classId=(document.getElementById('onbClassId')||{}).value||'athlete';const cls=classById(classId);state.classId=cls.id;state.build=cls.name;applyClassContent();state.weight=weight||null;state.heightFt=ft||null;state.heightIn=inch||0;if(onboardingAvatarData)state.avatar=onboardingAvatarData;state.onboardingComplete=true;{const pid=classPlanId(cls.id);if(pid&&!state.plan){const v=getToday(),d=parseD(v);d.setDate(d.getDate()-((d.getDay()+6)%7));state.plan={id:pid,classId:cls.id,start:dstr(d),from:v,checks:{}}}}save();const modal=document.getElementById('onboarding');modal.classList.remove('show');modal.setAttribute('aria-hidden','true');document.body.style.overflow='';toast('Your journey starts now ✦');setTimeout(startTutorial,450)}
function populateOnboardingFromState(){document.getElementById('onbName').value=state.name==='The Life Architect'?'':(state.name||'');document.getElementById('onbAge').value=state.age||'';const cid=state.classId||'athlete';document.getElementById('onbClassId').value=cid;document.getElementById('onbBuild').value=state.build||classById(cid).name;_onbSelectedClass=cid;document.getElementById('onbWeight').value=state.weight||'';document.getElementById('onbHeightFt').value=state.heightFt||'';document.getElementById('onbHeightIn').value=state.heightIn||'';onboardingAvatarData=null;const photo=document.getElementById('onbPhoto'),img=document.getElementById('onbPhotoPreview');if(state.avatar){img.src=state.avatar;photo.classList.add('has-photo')}else{img.removeAttribute('src');photo.classList.remove('has-photo')}document.getElementById('onbPrivate').textContent=onboardingPreviewMode?'Preview mode · pick a different class and tap the button to switch to it.':'Your journey, your pace.';onboardingStep=0;showOnboardingStep(0)}
function previewOnboarding(){onboardingPreviewMode=true;populateOnboardingFromState();const modal=document.getElementById('onboarding');modal.classList.add('show');modal.setAttribute('aria-hidden','false');document.body.style.overflow='hidden';showOnboardingStep(5)}
function closeOnboardingPreview(){const modal=document.getElementById('onboarding');modal.classList.remove('show');modal.setAttribute('aria-hidden','true');document.body.style.overflow='';onboardingPreviewMode=false;onboardingAvatarData=null;document.getElementById('onbAvatarInput').value='';document.getElementById('onbPrivate').textContent='Your journey, your pace.';onboardingStep=0}

/* —— First-run tutorial & feature tips —— */
const TUTORIAL_STEPS=[
  {
    kicker:'THE LOOP',
    title:'Your life is the game',
    body:'<p>Train an attribute and it levels up. Log real work — workouts, study, recovery, outreach — and you earn XP.</p><p style="margin-top:10px">Skip the work and the relevant stats fall behind. That is intentional.</p>'
  },
  {
    kicker:'DECAY',
    title:'What you ignore fades',
    body:'<p>After a short <strong>grace period</strong> (default 3 days) with no training, an attribute starts losing XP each day.</p><ul><li>Skill XP does <strong>not</strong> decay</li><li>Rest is fine when planned — chronic neglect is what costs progress</li><li>You can tune or turn off decay in Settings</li></ul><p style="margin-top:10px">The <strong>Decay watch</strong> on the Overview shows what is safe and what is slipping.</p>'
  },
  {
    kicker:'GETTING STARTED',
    title:'Log real sessions',
    body:'<p>Use <strong>Log activity</strong> for freeform sessions, or open an attribute on the Character sheet for a catalog of specific actions.</p><p style="margin-top:10px">Quests, the workout plan, meals, and savings can also award XP when you complete them for real.</p>'
  },
  {
    kicker:'YOU ARE READY',
    title:'One action is enough',
    body:'<p>Pick one thing you actually did today and log it. The first XP drop is the best way to feel how the system works.</p><p style="margin-top:10px">Tip cards will appear the first time you open each major section. Dismiss them anytime.</p>'
  }
];
let tutStep=0;
function ensureTipsState(){
  if(!state.tipsSeen||typeof state.tipsSeen!=='object')state.tipsSeen={};
  if(state.tutorialDone==null)state.tutorialDone=false;
}
function shouldShowTutorial(){
  ensureTipsState();
  if(state.tutorialDone)return false;
  // Show after profile onboarding, or for existing saves that never saw the tutorial
  if(state.onboardingComplete)return true;
  // Brand-new profile still in onboarding — wait until finishOnboarding
  return false;
}
function startTutorial(){
  ensureTipsState();
  if(state.tutorialDone)return;
  tutStep=0;
  const el=document.getElementById('tutorial');
  if(!el)return;
  renderTutorialStep();
  el.classList.add('show');
  el.setAttribute('aria-hidden','false');
  document.body.classList.add('noscroll');
}
function ren