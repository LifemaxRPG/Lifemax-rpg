/* ===== The Reading: the first deal of the day as a short film =====
   A candlelit table in fog (one fullscreen shader), the deck dropped from the dark, a riffle shuffle, three cards laid
   with a thud, then each card is lifted, turned and named. Skip is always one tap away. */
var CINE_FOG_V='#version 300 es\nin vec2 p;void main(){gl_Position=vec4(p,0.,1.);}';
var CINE_FOG_F='#version 300 es\nprecision mediump float;uniform float uT,uDim,uFlare,uFlash;uniform vec2 uRes,uC1,uC2;out vec4 o;\n'+
 'float h(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}\n'+
 'float n(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(h(i),h(i+vec2(1.,0.)),f.x),mix(h(i+vec2(0.,1.)),h(i+vec2(1.,1.)),f.x),f.y);}\n'+
 'float fbm(vec2 p){float a=.5,s=0.;for(int i=0;i<4;i++){s+=a*n(p);p=p*2.03+vec2(1.7,9.2);a*=.5;}return s;}\n'+
 'void main(){vec2 uv=gl_FragCoord.xy/uRes;uv.y=1.-uv.y;float asp=uRes.x/uRes.y;vec2 q=(uv-.5)*vec2(asp,1.);float vig=smoothstep(1.05,.2,length(q));\n'+
 'float fog=fbm(uv*vec2(2.2*asp,2.6)+vec2(uT*.03,-uT*.05))*.8+fbm(uv*vec2(5.*asp,5.)-vec2(uT*.07,uT*.03))*.35;\n'+
 'float f1=.78+.22*sin(uT*11.+n(vec2(uT*3.,1.))*6.),f2=.78+.22*sin(uT*9.+2.+n(vec2(uT*3.,7.))*6.);\n'+
 'float l1=exp(-pow(length((uv-uC1)*vec2(asp,1.)),2.)*9.)*f1,l2=exp(-pow(length((uv-uC2)*vec2(asp,1.)),2.)*9.)*f2;float lit=(l1+l2)*uFlare;\n'+
 'vec3 fogc=mix(vec3(.07,.05,.17),vec3(.04,.14,.18),uv.y)*fog;\n'+
 'vec3 col=vec3(.012,.008,.03)+fogc*(.22+lit*1.3)+vec3(1.,.6,.3)*lit*.3+vec3(.7,.78,1.)*uFlash*(.35+.4*fog);col*=mix(.45,1.,vig);\n'+
 'float a=uDim*.97;o=vec4(col*a,a);}';
function dhDealMode(){try{return localStorage.getItem('lma_qdeal')||'cine'}catch(e){return 'cine'}}
function dhSetDealMode(v){try{localStorage.setItem('lma_qdeal',v)}catch(e){}toast(v==='cine'?'Card deal: cinematic':v==='quick'?'Card deal: quick':'Card deal: instant')}
function cineBg(canvas,S){
 try{
  const g=canvas.getContext('webgl2',{alpha:true,premultipliedAlpha:true,antialias:false,depth:false});if(!g)return null;
  const mk=function(t,src){const s=g.createShader(t);g.shaderSource(s,src);g.compileShader(s);if(!g.getShaderParameter(s,g.COMPILE_STATUS))throw new Error(g.getShaderInfoLog(s));return s};
  const pr=g.createProgram();g.attachShader(pr,mk(g.VERTEX_SHADER,CINE_FOG_V));g.attachShader(pr,mk(g.FRAGMENT_SHADER,CINE_FOG_F));g.linkProgram(pr);
  if(!g.getProgramParameter(pr,g.LINK_STATUS))throw new Error(g.getProgramInfoLog(pr));
  const b=g.createBuffer();g.bindBuffer(g.ARRAY_BUFFER,b);g.bufferData(g.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,1,1]),g.STATIC_DRAW);
  const l=g.getAttribLocation(pr,'p');g.enableVertexAttribArray(l);g.vertexAttribPointer(l,2,g.FLOAT,false,0,0);
  const U={};['uT','uDim','uFlare','uFlash','uRes','uC1','uC2'].forEach(function(n){U[n]=g.getUniformLocation(pr,n)});
  const sc=.6;canvas.width=Math.round(innerWidth*sc);canvas.height=Math.round(innerHeight*sc);
  return {draw:function(t){g.viewport(0,0,canvas.width,canvas.height);g.useProgram(pr);g.uniform1f(U.uT,t);g.uniform1f(U.uDim,S.dim);g.uniform1f(U.uFlare,S.flare);g.uniform1f(U.uFlash,S.flash);g.uniform2f(U.uRes,canvas.width,canvas.height);g.uniform2f(U.uC1,.1,.93);g.uniform2f(U.uC2,.9,.93);g.drawArrays(g.TRIANGLE_STRIP,0,4)},
   lose:function(){try{const e=g.getExtension('WEBGL_lose_context');if(e)e.loseContext()}catch(x){}}};
 }catch(e){console.warn('[cine bg]',e);return null}
}
async function dhCinematic(el,cards,d,replay){
 const vw=innerWidth,vh=innerHeight,anims=[],waits=[],S={dim:0,flare:.7,flash:0};let skipped=false,alive=true,raf=0;
 const rnd=function(a,b){return a+Math.random()*(b-a)};
 const A=function(node,kf,o){const a=node.animate(kf,o);anims.push(a);return a.finished.catch(function(){})};
 const wait=function(ms){return new Promise(function(r){const t=setTimeout(r,skipped?0:ms);waits.push([t,r])})};
 const hap=function(n){try{if(window.LifeMaxHaptics)LifeMaxHaptics.play(n)}catch(e){}};
 const sc=document.createElement('div');sc.id='fateScene';
 const candle=function(side){return '<span class="fs-candle '+side+'">'+qpxSprite('candle',6,{F:'#ff7a2a',Y:'#ffd35c',C:'#efe6d2',B:'#6b4a2c'},'qfl')+'</span>'};
 sc.innerHTML='<canvas class="fs-bg"></canvas>'+candle('l')+candle('r')+'<div class="fs-stage"></div><div class="fs-title"></div><div class="fs-cap"></div><button class="fs-skip" type="button" aria-label="Skip the reading">'+qpxText('SKIP',{s:2,w:60,lines:1})+'</button>';
 document.body.appendChild(sc);
 const stage=sc.querySelector('.fs-stage'),cap=sc.querySelector('.fs-cap'),title=sc.querySelector('.fs-title');
 const bg=cineBg(sc.querySelector('.fs-bg'),S);if(!bg)sc.classList.add('fs-plain');
 const t0=performance.now();
 const loop=function(){if(!alive)return;const t=(performance.now()-t0)/1000;S.flash*=.9;if(bg)bg.draw(t);raf=requestAnimationFrame(loop)};loop();
 /* skipping: finish everything at once and drop to the hand-off */
 const skip=function(){if(skipped)return;skipped=true;anims.forEach(function(a){try{a.finish()}catch(e){}});waits.forEach(function(w){clearTimeout(w[0]);w[1]()})};
 sc.querySelector('.fs-skip').addEventListener('click',skip);
 const type=async function(text,hold){for(let i=1;i<=text.length&&!skipped;i++){cap.innerHTML=qpxText(text.slice(0,i),{s:2,w:vw-40,lines:1});await wait(30)}await wait(hold||400)};
 const cw=cards[0].getBoundingClientRect().width||280,ch=cw*1659/948;
 const s0=Math.min(.42,(vw*.265)/cw),s1=Math.min(1,(vh*.7)/ch,(vw*.94)/cw),gap=cw*s0*1.1,cy=vh*.5,sm=.5;
 const place=function(n,x,y,s,r){n.style.transform='translate('+x+'px,'+y+'px) scale('+s+') rotate('+(r||0)+'deg)'};
 const tf=function(x,y,s,r){return 'translate('+x+'px,'+y+'px) scale('+s+') rotate('+(r||0)+'deg)'};
 const shake=function(){return A(stage,[{transform:'translate(0,0)'},{transform:'translate(0,4px)'},{transform:'translate(-2px,-2px)'},{transform:'translate(1px,1px)'},{transform:'translate(0,0)'}],{duration:260,easing:'ease-out'})};
 const dust=function(x,y,n){try{for(let k=0;k<(n||16);k++)QFX.emit(x+rnd(-40,40),y+rnd(-10,20),rnd(-90,90),rnd(-50,10),rnd(.7,1.3),rnd(12,26),[.5,.42,.34])}catch(e){}};
 /* the real cards, cloned face-down, waiting inside the deck */
 const deckBackUrl=typeof qartUrl==='function'&&qartUrl('back');
 const clones=cards.map(function(c,i){
  const w=document.createElement('div');w.className='cine-card';w.style.cssText='width:'+cw+'px;height:'+ch+'px;margin:'+(-ch/2)+'px 0 0 '+(-cw/2)+'px;opacity:0';
  const n=c.cloneNode(true);n.classList.remove('is-up');n.style.cssText='';const dk=n.querySelector('.dh-dock');if(dk)dk.remove();const fb=n.querySelector('.fd-back');if(fb){fb.removeAttribute('onclick');fb.removeAttribute('onkeydown');fb.removeAttribute('tabindex')}w.appendChild(n);stage.appendChild(w);place(w,0,0,sm,0);return {w:w,n:n,c:c};
 });
 const backs=[];
 for(let i=0;i<10;i++){const b=document.createElement('div');b.className='cine-back';b.style.cssText='width:'+cw+'px;height:'+ch+'px;margin:'+(-ch/2)+'px 0 0 '+(-cw/2)+'px;opacity:0'+(deckBackUrl?';background-image:url('+deckBackUrl+')':'');b.style.zIndex=i+1;stage.appendChild(b);place(b,0,0,sm,0);backs.push(b)}

 /* turn the card with your finger: it rotates in 3D under the drag, carries on with a flick, and settles front or back */
 const flipByHand=function(k){return new Promise(function(res){
  const fi=k.n.querySelector('.fd-inner'),w=k.w;let a=180,rx=0,drag=false,sx=0,sy=0,a0=180,samples=[],seen=false,settling=0,done=false;
  const gl=document.createElement('span');gl.className='cine-gloss';k.n.querySelector('.fd-front').appendChild(gl);
  const hint=document.createElement('span');hint.className='fs-hint';hint.innerHTML='<i></i>';w.appendChild(hint);
  let idle=setTimeout(function(){hint.classList.add('on')},2600);
  const apply=function(){fi.style.transform='rotateX('+rx.toFixed(1)+'deg) rotateY('+a.toFixed(1)+'deg)';const c=Math.cos(a*Math.PI/180);gl.style.opacity=Math.abs(Math.sin(a*Math.PI/180))*.0+(.55*Math.abs(c)).toFixed(2);gl.style.backgroundPosition=(50-Math.sin(a*Math.PI/180)*90).toFixed(0)+'% 0';w.style.filter='drop-shadow('+(Math.sin(a*Math.PI/180)*14).toFixed(0)+'px 18px 22px rgba(0,0,0,.7))'};
  fi.style.transition='none';apply();
  const finish=function(){if(done)return;done=true;clearTimeout(idle);cancelAnimationFrame(settling);w.removeEventListener('pointerdown',down);w.removeEventListener('pointermove',move);w.removeEventListener('pointerup',up);w.removeEventListener('pointercancel',up);
   k.n.classList.add('is-up');fi.style.transform='';void fi.offsetWidth;fi.style.transition='';gl.remove();hint.remove();w.style.filter='';res()};
  const front=function(){return Math.cos(a*Math.PI/180)>0};
  const down=function(e){if(done||settling)return;drag=true;sx=e.clientX;sy=e.clientY;a0=a;samples=[[performance.now(),a]];hint.classList.remove('on');clearTimeout(idle);try{w.setPointerCapture(e.pointerId)}catch(x){}e.preventDefault()};
  const move=function(e){if(!drag)return;a=a0+(e.clientX-sx)*.85;rx=Math.max(-24,Math.min(24,(e.clientY-sy)*-.12));apply();
   const now=performance.now();samples.push([now,a]);while(samples.length>2&&now-samples[0][0]>90)samples.shift();
   if(front()&&!seen){seen=true;S.flash=Math.max(S.flash,.22);hap('tap')}};
  const up=function(){if(!drag)return;drag=false;
   const n=samples.length,v=n>1?(samples[n-1][1]-samples[0][1])/Math.max(16,samples[n-1][0]-samples[0][0])*1000:0;/* deg per second */
   const boost=Math.max(-45,Math.min(45,v*.04)),target=Math.round((a+boost)/180)*180,from=a,from_rx=rx,t0=performance.now(),dur=520;
   const step=function(){const t=Math.min(1,(performance.now()-t0)/dur),c1=1.4,c3=c1+1,e=1+c3*Math.pow(t-1,3)+c1*Math.pow(t-1,2);
    a=from+(target-from)*e;rx=from_rx*(1-t);apply();
    if(t<1){settling=requestAnimationFrame(step)}else{settling=0;a=target;rx=0;apply();
     if(Math.round(target/180)%2===0){finish()}else{seen=false;idle=setTimeout(function(){hint.classList.add('on')},2200)}}};
   settling=requestAnimationFrame(step)};
  w.addEventListener('pointerdown',down);w.addEventListener('pointermove',move);w.addEventListener('pointerup',up);w.addEventListener('pointercancel',up);
  waits.push([0,finish]);/* Skip resolves it */
 })};
 try{
  await A(sc,[{opacity:0},{opacity:1}],{duration:700,fill:'forwards'});
  const dimT=performance.now();const dimLoop=function(){S.dim=Math.min(1,(performance.now()-dimT)/900);if(S.dim<1&&alive)requestAnimationFrame(dimLoop)};dimLoop();
  await type('THE TABLE IS SET',200);
  /* the deck falls out of the dark */
  if(!skipped){
   await Promise.all(backs.map(function(b,i){b.style.opacity='1';return A(b,[{transform:tf(rnd(-30,30),-vh*.8,sm,rnd(-30,30))},{transform:tf(i*.5,-i*.8,sm,0)}],{duration:520,delay:i*55,easing:'cubic-bezier(.3,.9,.35,1)',fill:'forwards'})}));
   hap('success');shake();dust(vw/2,cy+ch*sm*.4,22);S.flare=1;
  }
  await type('THE DECK IS SHUFFLED',120);
  for(let r=0;r<2&&!skipped;r++){
   const spread=cw*sm*.5+r*8;
   await Promise.all(backs.map(function(b,i){const left=i%2===0;return A(b,[{transform:tf(i*.5,-i*.8,sm,0)},{transform:tf((left?-1:1)*spread,-i*.8,sm,left?-9:9)}],{duration:220,easing:'ease-out',fill:'forwards'})}));
   hap('tap');try{QFX.sparks(vw/2,cy,12+r*6,1+r%2)}catch(e){}
   await Promise.all(backs.map(function(b,i){const left=i%2===0;return A(b,[{transform:tf((left?-1:1)*spread,-i*.8,sm,left?-9:9)},{transform:tf(i*.5,-i*.8,sm,0)}],{duration:260,delay:i*18,easing:'cubic-bezier(.4,0,.2,1)',fill:'forwards'})}));
  }
  /* three cards laid down */
  await type('THE CARDS ARE LAID',100);
  for(let i=0;i<3&&!skipped;i++){
   const k=clones[i],x=(i-1)*gap,r=(i-1)*4+rnd(-2,2),top=backs[9-i];
   top.style.opacity='0';k.w.style.opacity='1';
   const done=A(k.w,[{transform:tf(0,-3,sm,0)},{transform:tf(x*.4,-cw*.5,s0*1.3,r*2),offset:.55},{transform:tf(x,cy-vh/2,s0*1.06,r),offset:.82},{transform:tf(x,cy-vh/2,s0,r)}],{duration:620,easing:'cubic-bezier(.25,.8,.3,1)',fill:'forwards'});
   await wait(470);if(skipped)break;
   hap('success');shake();dust(vw/2+x,cy+ch*s0*.45,14);S.flare=1;S.flash=Math.max(S.flash,.18);
   await done;await wait(120);
  }
  /* the reading */
  const arcOf=function(q){return QPX_ARC[q.attribute]||QPX_ARC.discipline};
  if(!skipped){
   backs.forEach(function(b){b.style.opacity='0'});
   await type('YOUR CARDS AWAIT',150);
   const gut=A(sc,[{filter:'brightness(1)'},{filter:'brightness(.55)'},{filter:'brightness(1)'}],{duration:520});S.flare=.15;await wait(330);S.flare=1;S.flash=.5;await gut;
  }
  for(let i=0;i<3&&!skipped;i++){
   const k=clones[i],x=(i-1)*gap,r=(i-1)*4,q=dhCards().find(function(z){return z.slot===i})||{tier:1,attribute:'discipline'},tier=q.tier||1,arc=arcOf(q);
   clones.forEach(function(o,j){o.w.style.zIndex=j===i?20:1;o.w.style.pointerEvents=j===i?'auto':'none'});
   clones.forEach(function(o,j){if(j!==i)A(o.w,[{opacity:1},{opacity:.28}],{duration:350,fill:'forwards'})});
   await A(k.w,[{transform:tf(x,cy-vh/2,s0,r)},{transform:tf(0,cy-vh/2-10,s1,0)}],{duration:500,easing:'cubic-bezier(.3,.8,.25,1)',fill:'forwards'});
   if(skipped)break;
   cap.innerHTML=qpxText('TURN THE CARD',{s:2,w:vw-40,lines:1});sc.dataset.await='flip';
   await flipByHand(k);sc.dataset.await='';cap.innerHTML='';if(skipped)break;
   S.flash=tier>=3?1:tier===2?.7:.45;hap(tier>=3?'big':'double');
   try{const rc=k.w.getBoundingClientRect();QFX.burst(rc,tier)}catch(e){}
   title.innerHTML='<span style="color:#f6ecd0;filter:drop-shadow(0 0 10px '+arc.ac+') drop-shadow(0 0 3px '+arc.ac+')">'+qpxText(arc.name,{s:3,w:vw-20,lines:1})+'</span><span class="fs-tier">'+qpxText(QPX_TIER[tier],{s:1,w:200,lines:1})+'</span>';
   A(title,[{opacity:0,transform:'translateY(10px) scale(.9)'},{opacity:1,transform:'none'}],{duration:420,fill:'forwards'});
   await wait(tier>=3?1250:800);if(skipped)break;
   A(title,[{opacity:1},{opacity:0}],{duration:300,fill:'forwards'});
   clones.forEach(function(o,j){if(j!==i)A(o.w,[{opacity:.28},{opacity:1}],{duration:350,fill:'forwards'})});
   await A(k.w,[{transform:tf(0,cy-vh/2-10,s1,0)},{transform:tf(x,cy-vh/2,s0,r)}],{duration:420,easing:'cubic-bezier(.4,0,.2,1)',fill:'forwards'});
  }
 }catch(e){console.warn('[cine]',e)}
 /* hand-off: all three face-up cards glide to the carousel while the room fades */
 skipped=true;anims.forEach(function(a){try{a.finish()}catch(e){}});
 d.up=[true,true,true];
 clones.forEach(function(k){const fi=k.n.querySelector('.fd-inner');if(fi){fi.style.transform='';fi.style.transition=''}k.n.classList.add('is-up');k.w.style.opacity='1';k.w.style.filter=''});
 cap.innerHTML='';title.innerHTML='';
 el.classList.remove('fd-pre');cards.forEach(function(c){c.classList.add('is-up');c.style.opacity='0'});
 const outs=clones.map(function(k,i){
  const rc=k.c.getBoundingClientRect(),tx=rc.left+rc.width/2-vw/2,ty=rc.top+rc.height/2-vh/2,ss=rc.width/cw,x=(i-1)*gap,r=(i-1)*4;
  const a=k.w.animate([{transform:tf(x,cy-vh/2,s0,r)},{transform:tf(tx,ty,ss,0)}],{duration:620,delay:i*70,easing:'cubic-bezier(.4,0,.2,1)',fill:'forwards'});return a.finished.catch(function(){});
 });
 sc.querySelector('.fs-skip').style.display='none';
 sc.animate([{backgroundColor:'rgba(5,3,10,1)'},{backgroundColor:'rgba(5,3,10,0)'}],{duration:700,fill:'forwards'});
 sc.querySelectorAll('.fs-bg,.fs-candle,.cine-back').forEach(function(n){n.animate([{opacity:1},{opacity:0}],{duration:600,fill:'forwards'})});
 await Promise.all(outs);
 cards.forEach(function(c){c.style.opacity=''});
 alive=false;cancelAnimationFrame(raf);if(bg)bg.lose();sc.remove();
 try{QFX.foilKick()}catch(e){}
}
setTimeout(function(){try{var s=document.getElementById('qdealSel');if(s)s.value=dhDealMode()}catch(e){}},0);
