/* ===== Quest effects: a small WebGL2 engine for the Fate Deck =====
   1. GPU particles. Each particle is written once at birth; the vertex shader works out where it is from its age,
      so a burst costs one buffer upload and no per-frame JavaScript per particle.
   2. Holographic foil on 2- and 3-star cards: one shader draws into a shared canvas and is copied onto each card.
   3. Tilt: cards lean toward your finger, or with the phone when it reports motion.
   Everything falls back to the plain cards when WebGL2 is missing, reduced motion is on, or the switch in Settings is off. */
var QFX=(function(){
 var MAXP=1400,STRIDE=10,PAL=[[.75,.65,1],[.45,.7,1],[1,.82,.35],[.48,.88,.63]];
 var cv=null,gl=null,prog=null,buf=null,data=new Float32Array(MAXP*STRIDE),head=0,deadAt=0,raf=0,lost=false,uni={},dpr=1;
 var fgl=null,fcv=null,fprog=null,funi={},fRaf=0,fLast=0,tiltX=0,tiltY=0,devX=0,devY=0,hasDev=false,ptr=null;
 function pref(){try{return localStorage.getItem('lma_qfx')!=='off'}catch(e){return true}}
 function on(){return pref()&&!lost&&!(typeof fxReduced==='function'&&fxReduced())}
 function sh(g,type,src){var s=g.createShader(type);g.shaderSource(s,src);g.compileShader(s);if(!g.getShaderParameter(s,g.COMPILE_STATUS))throw new Error(g.getShaderInfoLog(s));return s}
 function mk(g,vs,fs){var p=g.createProgram();g.attachShader(p,sh(g,g.VERTEX_SHADER,vs));g.attachShader(p,sh(g,g.FRAGMENT_SHADER,fs));g.linkProgram(p);if(!g.getProgramParameter(p,g.LINK_STATUS))throw new Error(g.getProgramInfoLog(p));return p}
 var PV='#version 300 es\nprecision highp float;in vec2 aO;in vec2 aV;in vec2 aT;in float aS;in vec3 aC;uniform float uNow;uniform vec2 uRes;uniform float uDpr;out vec3 vC;out float vA;out float vK;\n'+
  'void main(){float t=uNow-aT.x;float L=aT.y;if(t<0.||t>L){gl_Position=vec4(2.,2.,0.,1.);gl_PointSize=0.;vA=0.;vC=vec3(0.);vK=0.;return;}\n'+
  'float age=t/L;vec2 p=aO+aV*((1.-exp(-2.6*t))/2.6)+vec2(0.,70.)*t*t;vec2 n=vec2(p.x/uRes.x*2.-1.,1.-p.y/uRes.y*2.);gl_Position=vec4(n,0.,1.);\n'+
  'float tw=.75+.25*sin(t*22.+aO.x);gl_PointSize=aS*uDpr*(1.-age*.55)*tw;vA=pow(1.-age,1.5);vC=aC;vK=step(8.,aS);}';
 var PF='#version 300 es\nprecision mediump float;in vec3 vC;in float vA;in float vK;out vec4 o;\n'+
  'void main(){vec2 q=gl_PointCoord-.5;float d=length(q)*2.;if(d>1.)discard;float a=1.-d;a*=a;float cross=smoothstep(.07,0.,min(abs(q.x),abs(q.y)))*(1.-d)*vK*.9;float k=(a+cross)*vA;o=vec4(vC*k,k);}';
 var FV='#version 300 es\nin vec2 p;out vec2 uv;void main(){uv=p*.5+.5;gl_Position=vec4(p,0.,1.);}';
 var FF='#version 300 es\nprecision mediump float;in vec2 uv;uniform float uT;uniform vec2 uTilt;uniform float uTier;out vec4 o;\n'+
  'float h(vec2 c){return fract(sin(dot(c,vec2(127.1,311.7)))*43758.5453);}\n'+
  'vec3 pal(float t){return .5+.5*cos(6.28318*(t+vec3(0.,.33,.67)));}\n'+
  'void main(){vec2 u=vec2(uv.x,1.-uv.y);float ph=u.x*.7+u.y*.55+uTilt.x*.9+uTilt.y*.6+sin(u.y*6.+uT*.4)*.04;vec3 rb=pal(ph);\n'+
  'float band=smoothstep(.16,0.,abs(fract((u.x+u.y)*.5-uTilt.x*.8-uTilt.y*.4+uT*.03)-.5));\n'+
  'vec2 cell=floor(u*vec2(22.,34.));float r=h(cell);float tw=step(.972,r)*pow(.5+.5*sin(uT*3.+r*60.+uTilt.x*14.),8.);\n'+
  'vec2 e=min(u,1.-u);float edge=smoothstep(.07,0.,min(e.x,e.y));\n'+
  'vec3 tint=uTier>2.5?vec3(1.,.85,.5):vec3(.55,.75,1.);vec3 c=mix(tint,rb,uTier>2.5?.75:.45)*(.18+band*.75+edge*.5)+vec3(tw);\n'+
  'float a=clamp(max(max(c.r,c.g),c.b),0.,1.);o=vec4(c*a,a);}';
 function init(){
  if(gl||lost||!on())return !!gl;
  try{
   cv=document.createElement('canvas');cv.id='qfx';cv.setAttribute('aria-hidden','true');
   cv.style.cssText='position:fixed;inset:0;width:100%;height:100%;pointer-events:none;z-index:60;';
   gl=cv.getContext('webgl2',{alpha:true,premultipliedAlpha:true,antialias:false,depth:false,stencil:false,powerPreference:'high-performance'});
   if(!gl){cv=null;lost=true;return false}
   prog=mk(gl,PV,PF);buf=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buf);gl.bufferData(gl.ARRAY_BUFFER,data.byteLength,gl.DYNAMIC_DRAW);
   var vao=gl.createVertexArray();gl.bindVertexArray(vao);var B=STRIDE*4;
   [['aO',2,0],['aV',2,8],['aT',2,16],['aS',1,24],['aC',3,28]].forEach(function(a){var l=gl.getAttribLocation(prog,a[0]);gl.enableVertexAttribArray(l);gl.vertexAttribPointer(l,a[1],gl.FLOAT,false,B,a[2])});
   ['uNow','uRes','uDpr'].forEach(function(n){uni[n]=gl.getUniformLocation(prog,n)});
   gl.enable(gl.BLEND);gl.blendFunc(gl.ONE,gl.ONE);
   cv.addEventListener('webglcontextlost',function(e){e.preventDefault();lost=true;if(cv.parentNode)cv.parentNode.removeChild(cv)});
   document.body.appendChild(cv);size();window.addEventListener('resize',size);
   return true;
  }catch(e){console.warn('[qfx]',e);lost=true;gl=null;if(cv&&cv.parentNode)cv.parentNode.removeChild(cv);return false}
 }
 function size(){if(!cv)return;dpr=Math.min(2,window.devicePixelRatio||1);var w=Math.round(innerWidth*dpr),h=Math.round(innerHeight*dpr);if(cv.width!==w||cv.height!==h){cv.width=w;cv.height=h}}
 function now(){return performance.now()/1000}
 function put(o,i){/* write one particle into the ring buffer */
  var k=head*STRIDE;data.set(o,k);gl.bindBuffer(gl.ARRAY_BUFFER,buf);gl.bufferSubData(gl.ARRAY_BUFFER,k*4,data,k,STRIDE);head=(head+1)%MAXP}
 function emit(x,y,vx,vy,life,size,c,delay){
  if(!init())return;var t=now()+(delay||0);put([x,y,vx,vy,t,life,size,c[0],c[1],c[2]]);var end=t+life;if(end>deadAt)deadAt=end;kick();
 }
 function kick(){if(!raf)raf=requestAnimationFrame(frame)}
 function draw(){
  size();gl.viewport(0,0,cv.width,cv.height);gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT);
  gl.useProgram(prog);gl.uniform1f(uni.uNow,now());gl.uniform2f(uni.uRes,innerWidth,innerHeight);gl.uniform1f(uni.uDpr,dpr);
  gl.drawArrays(gl.POINTS,0,MAXP);
 }
 function frame(){raf=0;if(!gl||lost)return;draw();if(now()<deadAt)raf=requestAnimationFrame(frame);else{gl.clear(gl.COLOR_BUFFER_BIT)}}
 function col(tier,j){var c=PAL[Math.max(0,Math.min(3,tier-1))];j=j||0;return [Math.min(1,c[0]+(Math.random()-.5)*j),Math.min(1,c[1]+(Math.random()-.5)*j),Math.min(1,c[2]+(Math.random()-.5)*j)]}
 function rnd(a,b){return a+Math.random()*(b-a)}
 /* ---- effects ---- */
 function motes(cx,cy,n,tier){for(var i=0;i<n;i++){var a=rnd(0,6.283),s=rnd(20,90);emit(cx+rnd(-60,60),cy+rnd(-70,70),Math.cos(a)*s,Math.sin(a)*s-30,rnd(.7,1.4),rnd(4,9),col(tier||1,.3))}}
 function sparks(cx,cy,n,tier){for(var i=0;i<n;i++){var a=rnd(0,6.283),s=rnd(60,220);emit(cx,cy,Math.cos(a)*s,Math.sin(a)*s,rnd(.35,.8),rnd(5,11),col(tier||1,.25))}}
 function trail(r,tier){emit(r.left+r.width/2+rnd(-r.width*.3,r.width*.3),r.top+r.height/2+rnd(-r.height*.3,r.height*.3),rnd(-30,30),rnd(-30,30),rnd(.45,.9),rnd(5,12),col(tier||1,.3))}
 function burst(r,tier){
  var cx=r.left+r.width/2,cy=r.top+r.height/2,n=tier>=3?70:tier===2?50:36;
  emit(cx,cy,0,0,.55,Math.max(r.width,r.height)*.9,col(tier,.1));/* soft halo */
  for(var i=0;i<n;i++){var a=i/n*6.283+rnd(-.05,.05),s=rnd(140,300);emit(cx,cy,Math.cos(a)*s,Math.sin(a)*s,rnd(.5,1.0),rnd(5,13),col(tier,.25))}
  if(tier>=3)for(var j=0;j<30;j++)emit(cx+rnd(-r.width/2,r.width/2),r.bottom,rnd(-40,40),rnd(-260,-120),rnd(.8,1.5),rnd(7,14),col(3,.2),j*.01);
 }
 function claim(r){
  var cx=r.left+r.width/2,cy=r.top+r.height*.45;
  emit(cx,cy,0,0,.6,r.width*1.1,PAL[3]);
  for(var i=0;i<56;i++){var a=i/56*6.283,s=rnd(200,340);emit(cx,cy,Math.cos(a)*s,Math.sin(a)*s,rnd(.6,1.1),rnd(6,13),i%3?PAL[3]:PAL[2])}
  for(var j=0;j<70;j++)emit(cx+rnd(-r.width*.4,r.width*.4),r.top+r.height*.2,rnd(-70,70),rnd(-380,-140),rnd(.9,1.7),rnd(5,12),j%2?PAL[2]:PAL[3],j*.006);
 }
 /* ---- foil ---- */
 function foilInit(){
  if(fgl||lost||!on())return !!fgl;
  try{
   fcv=document.createElement('canvas');fcv.width=220;fcv.height=340;
   fgl=fcv.getContext('webgl2',{alpha:true,premultipliedAlpha:true,antialias:false,depth:false,preserveDrawingBuffer:false});
   if(!fgl){fcv=null;return false}
   fprog=mk(fgl,FV,FF);var b=fgl.createBuffer();fgl.bindBuffer(fgl.ARRAY_BUFFER,b);fgl.bufferData(fgl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,1,1]),fgl.STATIC_DRAW);
   var l=fgl.getAttribLocation(fprog,'p');fgl.enableVertexAttribArray(l);fgl.vertexAttribPointer(l,2,fgl.FLOAT,false,0,0);
   ['uT','uTilt','uTier'].forEach(function(n){funi[n]=fgl.getUniformLocation(fprog,n)});
   return true;
  }catch(e){console.warn('[qfx foil]',e);fgl=null;return false}
 }
 function foilCards(){return [].slice.call(document.querySelectorAll('#dailyHand .dh-card.is-up.fd-t2,#dailyHand .dh-card.is-up.fd-t3'))}
 function tiltFor(card,t){
  var x=0,y=0;
  if(ptr){var r=card.getBoundingClientRect();x=Math.max(-1,Math.min(1,(ptr.x-(r.left+r.width/2))/(r.width*.8)));y=Math.max(-1,Math.min(1,(ptr.y-(r.top+r.height/2))/(r.height*.8)))}
  else if(hasDev){x=devX;y=devY}
  else{x=Math.sin(t*.5+ +card.dataset.slot)*.5;y=Math.cos(t*.4+ +card.dataset.slot)*.4}
  return [x,y];
 }
 function foilFrame(ts){
  fRaf=0;
  if(!on()||document.hidden||!dhViewOn())return;
  var cards=foilCards();
  if(cards.length&&ts-fLast>=33){
   fLast=ts;var t=ts/1000;
   if(foilInit()){
    cards.forEach(function(c){
     var tl=tiltFor(c,t),tier=c.classList.contains('fd-t3')?3:2;
     fgl.useProgram(fprog);fgl.viewport(0,0,fcv.width,fcv.height);fgl.clearColor(0,0,0,0);fgl.clear(fgl.COLOR_BUFFER_BIT);
     fgl.uniform1f(funi.uT,t);fgl.uniform2f(funi.uTilt,tl[0],tl[1]);fgl.uniform1f(funi.uTier,tier);fgl.drawArrays(fgl.TRIANGLE_STRIP,0,4);
     var cz=c.querySelector('canvas.fd-foil');if(!cz){cz=document.createElement('canvas');cz.className='fd-foil';cz.width=220;cz.height=340;var f=c.querySelector('.fd-front');if(f)f.appendChild(cz)}
     var g2=cz.getContext('2d');g2.clearRect(0,0,cz.width,cz.height);g2.drawImage(fcv,0,0);
     /* lean toward the touch / the tilt of the phone */
     c.style.setProperty('--rx',(-tl[1]*7).toFixed(2)+'deg');c.style.setProperty('--ry',(tl[0]*9).toFixed(2)+'deg');
    });
   }
  }
  if(cards.length||dhDealing)fRaf=requestAnimationFrame(foilFrame);
 }
 function foilKick(){if(!fRaf&&on())fRaf=requestAnimationFrame(foilFrame)}
 function leanOnly(){/* tier-1 cards also lean, no foil */
  var cs=[].slice.call(document.querySelectorAll('#dailyHand .dh-card.is-up.fd-t1'));cs.forEach(function(c){var tl=tiltFor(c,performance.now()/1000);c.style.setProperty('--rx',(-tl[1]*6).toFixed(2)+'deg');c.style.setProperty('--ry',(tl[0]*8).toFixed(2)+'deg')})}
 document.addEventListener('pointermove',function(e){if(e.target&&e.target.closest&&e.target.closest('#dailyHand')){ptr={x:e.clientX,y:e.clientY};leanOnly();foilKick()}},{passive:true});
 document.addEventListener('pointerup',function(){ptr=null},{passive:true});
 window.addEventListener('deviceorientation',function(e){if(e.gamma==null)return;hasDev=true;var gx=Math.max(-1,Math.min(1,(e.gamma||0)/30)),gy=Math.max(-1,Math.min(1,((e.beta||45)-45)/30));devX+=(gx-devX)*.15;devY+=(gy-devY)*.15},{passive:true});
 function probe(){/* test helper: draws one frame and reports whether any pixel was lit */
  if(!init())return -1;draw();var px=new Uint8Array(cv.width*cv.height*4);gl.readPixels(0,0,cv.width,cv.height,gl.RGBA,gl.UNSIGNED_BYTE,px);var n=0;for(var i=3;i<px.length;i+=4)if(px[i]>8)n++;return n}
 return {on:on,init:init,emit:emit,motes:motes,sparks:sparks,trail:trail,burst:burst,claim:claim,foilKick:foilKick,probe:probe,
  setOn:function(v){try{localStorage.setItem('lma_qfx',v?'on':'off')}catch(e){}if(!v&&cv){cv.style.display='none'}else if(cv){cv.style.display=''}if(v)foilKick()},
  enabled:pref,supported:function(){try{var c=document.createElement('canvas');return !!c.getContext('webgl2')}catch(e){return false}}};
})();
function qfxSet(v){QFX.setOn(v);toast(v?'Quest effects: on':'Quest effects: off');if(typeof dhRenderHand==='function')dhRenderHand()}
setTimeout(function(){try{var c=document.getElementById('qfxToggle');if(c)c.checked=QFX.enabled()}catch(e){}},0);
