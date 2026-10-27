/* ===== Guided workout in a 3D gym =====
   A scene layer on top of the existing guided-workout state machine (21/22-runtime). It never changes how sets are saved: it reads
   gwrCurrent(), draws the gym, walks the camera from machine to machine, plays a little trainer doing the exercise at pace, and
   fills in the same inputs the classic screen uses before calling gwrLogSet(). Turn it off in Settings for the classic screen. */
var G3_VS='#version 300 es\nlayout(location=0)in vec3 aP;layout(location=1)in vec3 aN;uniform mat4 uM,uVP;out vec3 vP;out vec3 vN;void main(){vec4 w=uM*vec4(aP,1.);vP=w.xyz;vN=mat3(uM)*aN;gl_Position=uVP*w;}';
var G3_FS='#version 300 es\nprecision highp float;in vec3 vP;in vec3 vN;out vec4 o;uniform vec3 uCol,uEm,uEye,uFocus,uAcc;uniform float uMode,uT,uGlow;\n'+
 'void main(){vec3 N=normalize(vN),V=normalize(uEye-vP);vec3 base=uCol;\n'+
 'if(uMode>.5&&uMode<1.5){vec2 g=abs(fract(vP.xz*1.)-.5);float line=smoothstep(.485,.5,max(g.x,g.y));base=mix(base,base*1.9+vec3(.03),line*.55);float lane=smoothstep(.04,0.,abs(vP.z-1.55))+smoothstep(.04,0.,abs(vP.z+1.55));base=mix(base,uAcc*.9,lane*.55);}\n'+
 'vec3 L1=normalize(vec3(-.35,.9,.45)),L2=normalize(vec3(.6,.4,-.2));float d1=max(dot(N,L1),0.),d2=max(dot(N,L2),0.)*.35;\n'+
 'float spot=exp(-pow(length((vP.xz-uFocus.xz)*vec2(.34,.5)),2.));\n'+
 'float lit=.07+.22*d1+d2*.5+spot*1.0*d1+spot*.16;vec3 c=base*lit*vec3(1.,.98,.95);\n'+
 'float rim=pow(1.-max(dot(N,V),0.),3.)*.35;c+=uAcc*rim*.35;\n'+
 'if(uMode>1.5){float s=.5+.5*sin(vP.x*3.+uT*.6);c=mix(base*.55,base*1.0+uAcc*.03,s*.18);}\n'+
 'c+=uEm*(1.+uGlow);float dist=length(uEye-vP);c=mix(c,vec3(.02,.025,.035),1.-exp(-dist*.035));c=c/(c+vec3(.75));c=pow(c,vec3(1./2.2));o=vec4(c,1.);}';
/* ---- meshes: unit box, cylinder (y 0..1, radius 1), sphere ---- */
function g3MeshBox(){var v=[],F=[[[1,0,0],[0,1,0],[0,0,1]],[[-1,0,0],[0,0,1],[0,1,0]],[[0,1,0],[0,0,1],[1,0,0]],[[0,-1,0],[1,0,0],[0,0,1]],[[0,0,1],[1,0,0],[0,1,0]],[[0,0,-1],[0,1,0],[1,0,0]]];
 F.forEach(function(f){var n=f[0],a=f[1],b=f[2],P=function(s,t){return [n[0]*.5+a[0]*s*.5+b[0]*t*.5,n[1]*.5+a[1]*s*.5+b[1]*t*.5,n[2]*.5+a[2]*s*.5+b[2]*t*.5]};
  [[-1,-1],[1,-1],[1,1],[-1,-1],[1,1],[-1,1]].forEach(function(q){var p=P(q[0],q[1]);v.push(p[0],p[1],p[2],n[0],n[1],n[2])})});return new Float32Array(v)}
function g3MeshCyl(seg){var v=[];for(var i=0;i<seg;i++){var a0=i/seg*6.2832,a1=(i+1)/seg*6.2832,c0=Math.cos(a0),s0=Math.sin(a0),c1=Math.cos(a1),s1=Math.sin(a1);
 [[c0,0,s0,c0,0,s0],[c1,0,s1,c1,0,s1],[c1,1,s1,c1,0,s1],[c0,0,s0,c0,0,s0],[c1,1,s1,c1,0,s1],[c0,1,s0,c0,0,s0]].forEach(function(q){v.push.apply(v,q)});
 [[0,1,0,0,1,0],[c1,1,s1,0,1,0],[c0,1,s0,0,1,0],[0,0,0,0,-1,0],[c0,0,s0,0,-1,0],[c1,0,s1,0,-1,0]].forEach(function(q){v.push.apply(v,q)})}return new Float32Array(v)}
function g3MeshSphere(la,lo){var v=[],P=function(i,j){var th=i/la*Math.PI,ph=j/lo*6.2832,x=Math.sin(th)*Math.cos(ph),y=Math.cos(th),z=Math.sin(th)*Math.sin(ph);return [x,y,z,x,y,z]};
 for(var i=0;i<la;i++)for(var j=0;j<lo;j++){var a=P(i,j),b=P(i+1,j),c=P(i+1,j+1),d=P(i,j+1);[a,b,c,a,c,d].forEach(function(q){v.push.apply(v,q)})}return new Float32Array(v)}
/* matrices */
function g3MBox(cx,cy,cz,sx,sy,sz,ry){var m=t3m(),c=Math.cos(ry||0),s=Math.sin(ry||0);m[0]=c*sx;m[2]=-s*sx;m[5]=sy;m[8]=s*sz;m[10]=c*sz;m[12]=cx;m[13]=cy;m[14]=cz;return m}
function g3MBoxZ(cx,cy,cz,sx,sy,sz,rz){var m=t3m(),c=Math.cos(rz||0),s=Math.sin(rz||0);m[0]=c*sx;m[1]=s*sx;m[4]=-s*sy;m[5]=c*sy;m[10]=sz;m[12]=cx;m[13]=cy;m[14]=cz;return m}
function g3MSeg(a,b,rx,rz){var d=[b[0]-a[0],b[1]-a[1],b[2]-a[2]],ref=Math.abs(d[2])<Math.hypot(d[0],d[1])*.9?[0,0,1]:[1,0,0],x=t3norm(t3cross(d,ref)),z=t3norm(t3cross(x,d)),m=t3m();
 m[0]=x[0]*rx;m[1]=x[1]*rx;m[2]=x[2]*rx;m[4]=d[0];m[5]=d[1];m[6]=d[2];m[8]=z[0]*(rz||rx);m[9]=z[1]*(rz||rx);m[10]=z[2]*(rz||rx);m[12]=a[0];m[13]=a[1];m[14]=a[2];return m}
function g3MSph(c,rx,ry,rz){var m=t3m();m[0]=rx;m[5]=ry||rx;m[10]=rz||rx;m[12]=c[0];m[13]=c[1];m[14]=c[2];return m}
/* ---- the trainer: a planar rig. Angles are absolute segment directions in degrees: 0 up, 90 forward (+x), 180 down ---- */
var G3_L={torso:.52,thigh:.44,shin:.44,ua:.3,fa:.27};
function g3Dir(a){var r=a*Math.PI/180;return [Math.sin(r),Math.cos(r)]}
function g3Lerp(A,B,u){var o={};for(var k in A){var a=A[k],b=B[k];o[k]=(a&&typeof a==='object'&&!Array.isArray(a))?g3Lerp(a,b,u):(typeof a==='number'?a+(b-a)*u:a)}return o}
function g3FK(P){
 var d=g3Dir,add=function(p,v,l){return [p[0]+v[0]*l,p[1]+v[1]*l]},hip,T=P.torso;
 if(P.anchor==='ankle'){var t=d(P.legs.th),s=d(P.legs.sh);hip=[P.pos[0]-t[0]*G3_L.thigh-s[0]*G3_L.shin,P.pos[1]-t[1]*G3_L.thigh-s[1]*G3_L.shin]}else hip=P.pos.slice();
 var tv=d(T),sh=add(hip,tv,G3_L.torso),head=add(sh,tv,.2);
 var leg=function(l){var knee=add(hip,d(l.th),G3_L.thigh),ank=add(knee,d(l.sh),G3_L.shin);return {knee:knee,ank:ank}};
 var arm=function(a){var el=add(sh,d(a.ua),G3_L.ua),wr=add(el,d(a.fa),G3_L.fa);return {el:el,wr:wr}};
 return {hip:hip,sh:sh,head:head,legN:leg(P.legs),legF:leg(P.legsF||P.legs),armN:arm(P.arms),armF:arm(P.armsF||P.arms)};
}
/* movement archetypes: A and B are the two ends of one rep. anchor: where the body is pinned (hip on a seat, ankle on the floor). */
var G3_MOVES={
 chest:{seat:'machine',A:{anchor:'hip',pos:[0,.5],torso:-4,legs:{th:92,sh:178},arms:{ua:-100,fa:70}},B:{anchor:'hip',pos:[0,.5],torso:-4,legs:{th:92,sh:178},arms:{ua:88,fa:88}},tempo:4},
 shoulder:{seat:'machine',A:{anchor:'hip',pos:[0,.5],torso:-2,legs:{th:92,sh:178},arms:{ua:-125,fa:-5}},B:{anchor:'hip',pos:[0,.5],torso:-2,legs:{th:92,sh:178},arms:{ua:4,fa:4}},tempo:4},
 pulldown:{seat:'machine',A:{anchor:'hip',pos:[0,.5],torso:-6,legs:{th:92,sh:178},arms:{ua:12,fa:12}},B:{anchor:'hip',pos:[0,.5],torso:-12,legs:{th:92,sh:178},arms:{ua:172,fa:28}},tempo:4},
 row:{seat:'machine',A:{anchor:'hip',pos:[0,.5],torso:12,legs:{th:92,sh:175},arms:{ua:88,fa:88}},B:{anchor:'hip',pos:[0,.5],torso:-4,legs:{th:92,sh:175},arms:{ua:-100,fa:80}},tempo:4},
 legpress:{seat:'machine',A:{anchor:'hip',pos:[0,.45],torso:-48,legs:{th:52,sh:108},arms:{ua:150,fa:120}},B:{anchor:'hip',pos:[0,.45],torso:-48,legs:{th:76,sh:80},arms:{ua:150,fa:120}},tempo:4},
 legext:{seat:'machine',A:{anchor:'hip',pos:[0,.5],torso:-4,legs:{th:92,sh:182},arms:{ua:150,fa:110}},B:{anchor:'hip',pos:[0,.5],torso:-4,legs:{th:92,sh:98},arms:{ua:150,fa:110}},tempo:4},
 legcurl:{seat:'machine',A:{anchor:'hip',pos:[0,.5],torso:-4,legs:{th:92,sh:100},arms:{ua:150,fa:110}},B:{anchor:'hip',pos:[0,.5],torso:-4,legs:{th:92,sh:196},arms:{ua:150,fa:110}},tempo:4},
 squat:{seat:'free',A:{anchor:'ankle',pos:[0,.08],torso:0,legs:{th:176,sh:184},arms:{ua:100,fa:20}},B:{anchor:'ankle',pos:[0,.08],torso:26,legs:{th:98,sh:200},arms:{ua:100,fa:20}},tempo:4},
 hinge:{seat:'free',A:{anchor:'ankle',pos:[0,.08],torso:0,legs:{th:178,sh:182},arms:{ua:180,fa:180}},B:{anchor:'ankle',pos:[0,.08],torso:78,legs:{th:166,sh:188},arms:{ua:182,fa:178}},tempo:4},
 calf:{seat:'free',A:{anchor:'ankle',pos:[0,.08],torso:0,legs:{th:180,sh:180},arms:{ua:180,fa:180}},B:{anchor:'ankle',pos:[0,.2],torso:0,legs:{th:180,sh:180},arms:{ua:180,fa:180}},tempo:3},
 plank:{seat:'mat',A:{anchor:'hip',pos:[0,.28],torso:80,legs:{th:-96,sh:-96},arms:{ua:176,fa:90}},B:{anchor:'hip',pos:[0,.31],torso:80,legs:{th:-96,sh:-96},arms:{ua:176,fa:90}},tempo:4},
 deadbug:{seat:'mat',A:{anchor:'hip',pos:[0,.14],torso:-90,legs:{th:0,sh:90},legsF:{th:0,sh:90},arms:{ua:0,fa:0},armsF:{ua:0,fa:0}},B:{anchor:'hip',pos:[0,.14],torso:-90,legs:{th:-4,sh:90},legsF:{th:80,sh:100},arms:{ua:-80,fa:-80},armsF:{ua:0,fa:0}},tempo:4},
 mobility:{seat:'mat',A:{anchor:'ankle',pos:[0,.05],torso:-8,legs:{th:178,sh:182},arms:{ua:150,fa:160}},B:{anchor:'ankle',pos:[0,.05],torso:16,legs:{th:178,sh:182},arms:{ua:20,fa:10}},tempo:5},
 walk:{seat:'tread',A:{anchor:'ankle',pos:[0,.1],torso:4,legs:{th:150,sh:170},legsF:{th:-150,sh:-170},arms:{ua:150,fa:120},armsF:{ua:-150,fa:-120}},B:{anchor:'ankle',pos:[0,.1],torso:4,legs:{th:-150,sh:-170},legsF:{th:150,sh:170},arms:{ua:-150,fa:-120},armsF:{ua:150,fa:120}},tempo:1.6}
};
function g3Archetype(label){
 var t=String(label||'').toLowerCase();
 if(/leg press/.test(t))return 'legpress';if(/leg curl|hamstring curl/.test(t))return 'legcurl';if(/leg extension/.test(t))return 'legext';
 if(/chest press|bench press|incline|push-up|push up/.test(t))return 'chest';if(/shoulder press|overhead/.test(t))return 'shoulder';
 if(/pulldown|pull-up|pull up/.test(t))return 'pulldown';if(/row|face pull|pec deck|pallof/.test(t))return 'row';
 if(/romanian|deadlift|rdl|hinge/.test(t))return 'hinge';if(/calf/.test(t))return 'calf';if(/plank/.test(t))return 'plank';if(/dead bug|bug/.test(t))return 'deadbug';
 if(/squat|lunge|step-up|sit-to-stand|box/.test(t))return 'squat';
 if(/treadmill|elliptical|cycling|cardio|bike|walking|running|endurance|aerobic|conditioning/.test(t))return 'walk';
 return 'mobility';
}
/* ---- equipment: parts in station space (+x is the way the trainer faces, +z toward the camera) ---- */
var G3_C={frame:[.17,.19,.23],pad:null,plate:[.55,.58,.64],plateUp:[.78,.8,.86],pin:[.9,.15,.12],rubber:[.08,.085,.1],steel:[.5,.53,.58],bench:[.15,.16,.2]};
function g3StationParts(arch,mv,acc,lift,fk,selIdx){
 var P=[],B=function(cx,cy,cz,sx,sy,sz,col,em,ry){P.push({m:'box',M:g3MBox(cx,cy,cz,sx,sy,sz,ry),c:col,e:em})},
  C=function(a,b,r,col,em){P.push({m:'cyl',M:g3MSeg(a,b,r),c:col,e:em})},pad=acc.map(function(x){return x*.55});
 var stack=function(x){/* weight stack: 12 plates, those up to the pin ride up with the rep */
  for(var i=0;i<12;i++){var up=i<=selIdx,y=.2+i*.062+(up?lift*.34:0);B(x,y,0,.26,.05,.34,up?G3_C.plateUp:G3_C.plate,null)}
  B(x-.14,1.05,.17,.03,1.9,.03,G3_C.steel);B(x-.14,1.05,-.17,.03,1.9,.03,G3_C.steel);B(x,2.02,0,.34,.06,.42,G3_C.frame);
  B(x+.14,.2+selIdx*.062+lift*.34,0,.16,.025,.05,G3_C.pin,[.5,.05,.04])};
 if(mv.seat==='machine'){
  B(-.2,.04,0,1.7,.08,.95,G3_C.frame);                      /* base */
  B(-.62,1.05,.3,.1,2.1,.08,G3_C.frame);B(-.62,1.05,-.3,.1,2.1,.08,G3_C.frame);
  stack(-.62);
  var hip=fk.hip;B(hip[0]-.02,hip[1]-.07,0,.46,.1,.42,pad);   /* seat */
  if(arch==='legpress'){B(hip[0]-.36,hip[1]+.32,0,.1,.95,.42,pad,null,-.2);B(.88+lift*.14,.62,0,.08,.7,.7,G3_C.frame);C([-.1,.2,.28],[1.1,.2,.28],.025,G3_C.steel);C([-.1,.2,-.28],[1.1,.2,-.28],.025,G3_C.steel)}
  else{B(hip[0]-.2,hip[1]+.36,0,.1,.74,.38,pad)}
  if(arch==='chest'||arch==='shoulder'||arch==='row'){var w=fk.armN.wr;B(w[0],w[1],0,.05,.05,.46,G3_C.steel);C([-.4,w[1],0],[w[0],w[1],0],.035,G3_C.frame);}
  if(arch==='pulldown'){var w2=fk.armN.wr;B(.1,2.1,0,.06,.06,.5,G3_C.frame);B(w2[0]+.05,w2[1],0,.05,.05,.8,G3_C.steel);C([.1,2.1,.15],[w2[0]+.05,w2[1],.15],.012,G3_C.steel);C([.1,2.1,-.15],[w2[0]+.05,w2[1],-.15],.012,G3_C.steel)}
  if(arch==='legext'||arch==='legcurl'){var a=fk.legN.ank;C([a[0]-.03,a[1]-.02,-.2],[a[0]-.03,a[1]-.02,.2],.05,pad);C([hip[0]+.3,hip[1]-.1,0],[a[0],a[1],0],.03,G3_C.frame)}
 }else if(mv.seat==='free'){
  B(-.9,.5,-.25,.05,1,.05,G3_C.frame);B(-.9,.95,-.25,.5,.05,.05,G3_C.frame);B(-1.0,.3,-.25,.18,.18,.18,[.2,.2,.24]);B(-.8,.3,-.25,.18,.18,.18,[.2,.2,.24]);/* rack */
  if(arch!=='calf'){[fk.armN.wr,fk.armF.wr].forEach(function(w,i){var z=i?-.14:.14;C([w[0],w[1],z-.07],[w[0],w[1],z+.07],.025,G3_C.steel);B(w[0],w[1],z-.1,.09,.09,.06,G3_C.rubber);B(w[0],w[1],z+.1,.09,.09,.06,G3_C.rubber)})}
  else{B(.1,.02,0,.7,.06,.4,G3_C.frame)}
 }else if(mv.seat==='mat'){
  B(.1,.01,0,1.9,.04,.8,pad);C([-.7,.07,.5],[-.7,.07,-.5],.09,[.2,.2,.24]);B(1.2,.1,.5,.14,.2,.14,[.2,.2,.24]);
 }else if(mv.seat==='tread'){
  B(.15,.1,0,1.5,.14,.62,G3_C.rubber);B(.15,.19,0,1.34,.02,.5,[.13,.14,.17]);B(.98,.78,.36,.06,1.35,.06,G3_C.frame);B(.98,.78,-.36,.06,1.35,.06,G3_C.frame);B(.98,1.44,0,.2,.34,.8,G3_C.frame,[.0,.12,.2]);B(.88,1.44,0,.02,.3,.7,[.02,.3,.5],[0,.35,.55]);
 }
 return P;
}
function g3Figure(fk,acc,shirtCol){
 var parts=[],S=function(a,b,rx,rz,col,m){parts.push({m:m||'cyl',M:g3MSeg(a,b,rx,rz),c:col})},V=function(p,z){return [p[0],p[1],z||0]},
  skin=[.82,.62,.46],short=[.1,.11,.15],shoe=[.9,.9,.92],shirt=shirtCol||acc,dark=function(c,k){return c.map(function(x){return x*k})};
 var far=function(){S(V(fk.hip,-.09),V(fk.legF.knee,-.09),.075,.075,dark(short,.8));S(V(fk.legF.knee,-.09),V(fk.legF.ank,-.09),.055,.055,dark(skin,.7));
  parts.push({m:'box',M:g3MBox(fk.legF.ank[0]+.07,fk.legF.ank[1]-.03,-.09,.27,.08,.1),c:dark(shoe,.7)});
  S(V(fk.sh,-.18),V(fk.armF.el,-.18),.048,.048,dark(skin,.7));S(V(fk.armF.el,-.18),V(fk.armF.wr,-.18),.04,.04,dark(skin,.7))};
 far();
 S(V(fk.hip,0),V(fk.sh,0),.15,.1,shirt);parts.push({m:'sph',M:g3MSph(V(fk.head,0),.105),c:skin});parts.push({m:'sph',M:g3MSph([fk.head[0]-.01,fk.head[1]+.045,0],.112,.075,.112),c:[.1,.09,.08]});
 S(V(fk.hip,.09),V(fk.legN.knee,.09),.08,.08,short);S(V(fk.legN.knee,.09),V(fk.legN.ank,.09),.058,.058,skin);
 parts.push({m:'box',M:g3MBox(fk.legN.ank[0]+.07,fk.legN.ank[1]-.03,.09,.27,.08,.1),c:shoe});
 S(V(fk.sh,.18),V(fk.armN.el,.18),.05,.05,shirt);S(V(fk.armN.el,.18),V(fk.armN.wr,.18),.042,.042,skin);
 parts.push({m:'sph',M:g3MSph(V(fk.armN.wr,.18),.05),c:skin});
 return parts;
}
/* ---- the scene ---- */
var G3=null;
function g3Pref(){try{return localStorage.getItem('lma_gwr3d')!=='off'}catch(e){return true}}
function g3Want(){try{return g3Pref()&&!fxReduced()&&QFX.supported()}catch(e){return false}}
function g3SetPref(v){try{localStorage.setItem('lma_gwr3d',v?'on':'off')}catch(e){}toast('Guided workout: '+(v?'3D gym':'classic screen'))}
function g3Accent(){try{var c=getComputedStyle(document.documentElement).getPropertyValue('--accent').trim();if(/^#([0-9a-f]{6})$/i.test(c)){var n=parseInt(c.slice(1),16);return [((n>>16)&255)/255,((n>>8)&255)/255,(n&255)/255]}}catch(e){}return [.35,.65,1]}
var g3Dec=null;
function g3Plain(x){g3Dec=g3Dec||document.createElement('textarea');g3Dec.innerHTML=String(x==null?'':x);return g3Dec.value}
function g3LabelOf(g,it){var c=state.plan&&state.plan.checks[g.date];return (c&&c.substitute&&c.substitute[it.k])||g3Plain(it.l)}
function g3Last(name){var h=state.workoutHistory||[];for(var i=0;i<h.length;i++){var ex=(h[i].exercises||[]).find(function(e){return e.name===name});if(ex){var s=(ex.sets||[]).filter(function(x){return x&&x.weight>0});if(s.length)return {weight:s[s.length-1].weight,reps:s[s.length-1].reps,date:h[i].date}}}return null}
function g3Beep(f,d,v){try{var C=window.AudioContext||window.webkitAudioContext;if(!C||!fxOpt().sound||QSFX.vol()<=0)return;G3.ac=G3.ac||new C();var a=G3.ac,o=a.createOscillator(),g=a.createGain(),t=a.currentTime;o.type='square';o.frequency.value=f;g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime((v||.05)*QSFX.vol(),t+.01);g.gain.exponentialRampToValueAtTime(.0001,t+d);o.connect(g);g.connect(a.destination);o.start(t);o.stop(t+d+.02)}catch(e){}}
async function g3Open(){
 if(G3||!g3Want())return false;
 var g=gwrCurrent();if(!g)return false;var d=planDay(g.date);if(!d)return false;
 var sc=document.createElement('div');sc.id='gymScene';sc.innerHTML='<canvas class="g3-gl"></canvas><div class="g3-hud"></div><div class="g3-sheet" hidden></div><div class="g3-menu" hidden></div>';
 document.body.appendChild(sc);
 var cv=sc.querySelector('.g3-gl'),dpr=Math.min(1.6,window.devicePixelRatio||1);cv.width=Math.round(innerWidth*dpr);cv.height=Math.round(innerHeight*dpr);
 var gl=cv.getContext('webgl2',{alpha:false,antialias:true,depth:true,powerPreference:'high-performance'});
 if(!gl){sc.remove();return false}
 var S={sc:sc,cv:cv,gl:gl,alive:true,acc:g3Accent(),stations:[],cur:-1,raf:0,phaseKey:'',walk:null,eye:[0,1.55,5],tgt:[0,1.2,0],focus:[0,0,0],activeStart:0,t0:performance.now(),hudSig:'',lastDraw:0,log:null,idxByKey:{}};
 G3=S;
 try{
  var mk=function(t,src){var s=gl.createShader(t);gl.shaderSource(s,src);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(s));return s};
  var pr=gl.createProgram();gl.attachShader(pr,mk(gl.VERTEX_SHADER,G3_VS));gl.attachShader(pr,mk(gl.FRAGMENT_SHADER,G3_FS));gl.linkProgram(pr);if(!gl.getProgramParameter(pr,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(pr));
  S.pr=pr;S.U={};['uM','uVP','uCol','uEm','uEye','uFocus','uAcc','uMode','uT','uGlow'].forEach(function(n){S.U[n]=gl.getUniformLocation(pr,n)});
  var mkVao=function(data){var v=gl.createVertexArray();gl.bindVertexArray(v);var b=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,b);gl.bufferData(gl.ARRAY_BUFFER,data,gl.STATIC_DRAW);gl.enableVertexAttribArray(0);gl.vertexAttribPointer(0,3,gl.FLOAT,false,24,0);gl.enableVertexAttribArray(1);gl.vertexAttribPointer(1,3,gl.FLOAT,false,24,12);return {v:v,n:data.length/6}};
  S.mesh={box:mkVao(g3MeshBox()),cyl:mkVao(g3MeshCyl(12)),sph:mkVao(g3MeshSphere(8,12))};
 }catch(e){console.warn('[gym3d]',e);sc.remove();G3=null;return false}
 /* stations, one per exercise of the day */
 d.items.forEach(function(it,i){var arch=g3Archetype(g3LabelOf(g,it)),mv=G3_MOVES[arch];S.idxByKey[it.k]=i;var last=g3Last(g3LabelOf(g,it));
  var st={it:it,arch:arch,mv:mv,x:i*3.8,sel:last?Math.max(1,Math.min(10,Math.round(last.weight/12))):5,static:null};
  var fk=g3FK(g3Lerp(mv.A,mv.B,0));var T=t3m();T[12]=st.x;
  st.static=g3StationParts(arch,mv,S.acc,0,fk,st.sel).map(function(p){return {m:p.m,M:t3mul(T,p.M),c:p.c,e:p.e}});S.stations.push(st)});
 S.N=S.stations.length;
 /* the room */
 var L=S.N*3.8+8,room=[],A=S.acc,add=function(m,M,c,e,mode){room.push({m:m,M:M,c:c,e:e,mode:mode||0})};
 add('box',g3MBox(L/2-4,-.05,0,L+8,.1,8),[.06,.065,.075],null,1);
 add('box',g3MBox(L/2-4,1.8,-3.4,L+8,3.8,.1),[.1,.11,.14],null,2);
 add('box',g3MBox(L/2-4,3.7,0,L+8,.1,8),[.05,.05,.06]);
 add('box',g3MBox(L/2-4,2.72,-3.33,L+8,.05,.04),A,A.map(function(x){return x*1.2}));
 add('box',g3MBox(L/2-4,.55,-3.33,L+8,.04,.04),A,A.map(function(x){return x*.7}));
 for(var x=-3;x<L-4;x+=3.8){add('box',g3MBox(x+1.9,3.55,-1,.06,.06,5.2),[.9,.92,1],[.7,.72,.8]);add('box',g3MBox(x+1.9,1.8,-3.36,.12,3.6,.1),[.08,.09,.11])}
 add('box',g3MBox(-3.2,1.4,-1.6,.14,2.8,.14),[.08,.09,.11],A.map(function(x){return x*.25}));
 for(var k=0;k<Math.ceil(L/7);k++){var rx=-1+k*7.3;add('box',g3MBox(rx,.45,-2.9,1.8,.07,.3),[.12,.13,.16]);for(var q=0;q<5;q++){add('box',g3MBox(rx-.7+q*.35,.55,-2.9,.14,.14,.14),[.22,.23,.27]);add('box',g3MBox(rx-.7+q*.35,.32,-2.9,.16,.16,.16),[.2,.21,.25])}}
 S.room=room;S.L=L;
 var onResize=function(){cv.width=Math.round(innerWidth*dpr);cv.height=Math.round(innerHeight*dpr)};window.addEventListener('resize',onResize);S.onResize=onResize;
 S.proj=null;
 sc.querySelector('.g3-hud').addEventListener('click',g3HudClick);sc.querySelector('.g3-sheet').addEventListener('click',g3HudClick);sc.querySelector('.g3-menu').addEventListener('click',g3HudClick);
 S.eye=[-2.4,1.9,6.8];S.tgt=[-.4,1.3,0];
 g3Sync();S.raf=requestAnimationFrame(g3Loop);S.watch=setInterval(function(){var r=document.getElementById('guidedWorkout');if(!r||!r.classList.contains('open'))g3Close()},300);
 sc.animate([{opacity:0},{opacity:1}],{duration:500,fill:'forwards'});
 return true;
}
function g3Close(){var S=G3;if(!S)return;S.alive=false;cancelAnimationFrame(S.raf);clearInterval(S.watch);window.removeEventListener('resize',S.onResize);try{S.gl.getExtension('WEBGL_lose_context').loseContext()}catch(e){}S.sc.remove();G3=null}
/* camera + trainer per frame */
function g3Loop(){
 var S=G3;if(!S||!S.alive)return;S.raf=requestAnimationFrame(g3Loop);
 var now=performance.now(),g=gwrCurrent();if(!g)return;
 var moving=!!S.walk||g.phase==='active'||g.phase==='timing'||S.sheetAnim;
 if(document.hidden||(!moving&&now-S.lastDraw<60))return;S.lastDraw=now;
 var t=(now-S.t0)/1000,gl=S.gl,U=S.U;
 /* walking between machines */
 if(S.walk){var w=S.walk,p=Math.min(1,(now-w.t)/w.ms),e=p<.5?4*p*p*p:1-Math.pow(-2*p+2,3)/2;S.eye=[w.e0[0]+(w.e1[0]-w.e0[0])*e,w.e0[1]+(w.e1[1]-w.e0[1])*e+Math.sin(p*Math.PI)*.12,w.e0[2]+(w.e1[2]-w.e0[2])*e];S.tgt=[w.t0[0]+(w.t1[0]-w.t0[0])*e,w.t0[1]+(w.t1[1]-w.t0[1])*e,w.t0[2]+(w.t1[2]-w.t0[2])*e];S.focus=[S.tgt[0],0,S.tgt[2]];if(p>=1){S.walk=null;S.hudSig=''}}
 var bob=S.walk?Math.sin(t*9)*.015:Math.sin(t*.7)*.01,eye=[S.eye[0]+Math.sin(t*.3)*.04,S.eye[1]+bob,S.eye[2]],proj=t3persp(46*Math.PI/180,S.cv.width/S.cv.height,.1,80),bas=t3basis(eye,S.tgt),vp=t3mul(proj,t3view(eye,bas));
 gl.viewport(0,0,S.cv.width,S.cv.height);gl.clearColor(.02,.025,.035,1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);gl.useProgram(S.pr);
 gl.uniformMatrix4fv(U.uVP,false,vp);gl.uniform3fv(U.uEye,eye);gl.uniform3fv(U.uFocus,S.focus);gl.uniform3fv(U.uAcc,S.acc);gl.uniform1f(U.uT,t);
 var draw=function(p,glow){var m=S.mesh[p.m];gl.bindVertexArray(m.v);gl.uniformMatrix4fv(U.uM,false,p.M);gl.uniform3fv(U.uCol,p.c||[.5,.5,.5]);gl.uniform3fv(U.uEm,p.e||[0,0,0]);gl.uniform1f(U.uMode,p.mode||0);gl.uniform1f(U.uGlow,glow||0);gl.drawArrays(gl.TRIANGLES,0,m.n)};
 S.room.forEach(function(p){draw(p)});
 var cur=S.cur>=0?S.stations[S.cur]:null;
 S.stations.forEach(function(st,i){if(st!==cur)st.static.forEach(function(p){draw(p)})});
 /* the way ahead: moving chevrons on the floor toward the next machine */
 if(cur&&S.cur<S.N-1){var nx=S.stations[S.cur+1].x,x0=cur.x+1.7;for(var k=0;k<9;k++){var u=((t*.6+k/9)%1),px=x0+(nx-x0-1.4)*u;draw({m:'box',M:g3MBox(px,.012,2.05,.34,.01,.1,.0),c:S.acc,e:S.acc.map(function(x){return x*(1.1-u)})})}}
 if(cur){
  var mv=cur.mv,u=0,rep=0,ph=gwrCurrent().phase;
  var hold=cur.arch==='plank';
  if(ph==='active'||ph==='timing'){var cyc=(now-S.activeStart)/(mv.tempo*1000);rep=Math.floor(cyc);var f=cyc-rep;u=hold?(.5+.5*Math.sin(t*1.1))*.2:(.5-.5*Math.cos(f*6.2832));S.rep=rep+1}
  else{u=hold?0:.0;S.rep=0}
  var P=g3Lerp(mv.A,mv.B,u);P.pos=[P.pos[0],P.pos[1]+(ph==='active'||ph==='timing'?0:Math.sin(t*1.6)*.006)];
  var fk=g3FK(P),T=t3m();T[12]=cur.x;
  var par=g3StationParts(cur.arch,mv,S.acc,u,fk,cur.sel).concat(g3Figure(fk,S.acc));
  par.forEach(function(p){draw({m:p.m,M:t3mul(T,p.M),c:p.c,e:p.e})});
  /* a plate clink at the bottom of each rep on machines */
  if(ph==='active'&&mv.seat==='machine'&&rep!==S.lastRep){S.lastRep=rep;if(rep>0){try{QSFX.play('chips-stack',{vol:.35})}catch(e){}}}
  if(ph!=='active')S.lastRep=-1;
 }
 g3Tick();
}
/* ---- HUD ---- */
function g3Num(n){return Math.max(0,n|0)}
function g3Walk(to){
 var S=G3,st=S.stations[to],tgt=st?[st.x+.12,1.2,0]:[S.stations[0].x,1.2,0],eye=st?[st.x+.35,1.55,5.0]:[0,1.9,6.5];
 S.walk={t:performance.now(),ms:S.cur<0?1400:1900,e0:S.eye.slice(),e1:eye,t0:S.tgt.slice(),t1:tgt};
}
function g3Sync(){
 var S=G3;if(!S||!S.alive)return;var g=gwrCurrent();if(!g)return;var d=planDay(g.date);if(!d)return;var st=gwrStepNext(g);
 var idx=st?S.idxByKey[st.it.k]:-1;
 if(g.phase==='complete'){if(S.cur!==-2){S.cur=-2;var mid=(S.stations[S.N-1].x)/2;S.walk={t:performance.now(),ms:2200,e0:S.eye.slice(),e1:[mid,2.3,Math.max(8,S.N*1.1+4)],t0:S.tgt.slice(),t1:[mid,1.3,0]};S.focus=[mid,0,0]}}
 else if(g.phase==='intro'){if(S.cur!==-1){S.cur=-1}}
 else if(idx>=0&&idx!==S.cur){var first=S.cur<0;S.cur=idx;g3Walk(idx);if(!first){try{QSFX.play('card-slide',{vol:.0})}catch(e){}}}
 if((g.phase==='active'||g.phase==='timing')&&S.phaseKey!==g.phase+':'+(st?st.it.k+st.j:'')){S.activeStart=performance.now();S.lastRep=-1}
 S.phaseKey=g.phase+':'+(st?st.it.k+st.j:'');
 g3Hud(g,d,st);
}
function g3Hud(g,d,st){
 var S=G3,hud=S.sc.querySelector('.g3-hud'),ph=g.phase,sig=ph+'|'+(st?st.it.k+':'+st.j:'')+'|'+(S.log?1:0)+'|'+(S.walk?1:0);
 if(sig===S.hudSig&&ph!=='rest')return;S.hudSig=sig;
 var stats=gwrStats(g),done=stats.completed.length,total=stats.all.length;
 var top='<div class="g3-top"><button class="g3-icon" data-a="pause" aria-label="Pause and save"><svg viewBox="0 0 16 16"><path d="M4 2h3v12H4zM9 2h3v12H9z" fill="currentColor"/></svg></button><div class="g3-title"><b>'+esc(g3Plain(d.short||d.label))+'</b><span data-g3-elapsed>'+gwrFmtDuration(gwrElapsed(g))+'</span></div><button class="g3-icon" data-a="menu" aria-label="Menu"><svg viewBox="0 0 16 16"><path d="M2 4h12M2 8h12M2 12h12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg></button></div>'
  +'<div class="g3-rail"><i style="width:'+(total?Math.round(done/total*100):0)+'%"></i></div>';
 var body='';
 if(ph==='intro'){
  var rows=d.items.map(function(it,i){return '<div class="g3-wb-row"><b>'+String(i+1).padStart(2,'0')+'</b><span>'+esc(g3LabelOf(g,it))+'</span><small>'+it.n+' &times; '+esc(g3Plain(it.d).replace(/^\d+\s*sets?\s*[x×]\s*/i,'').split(/\s+[—-]\s+/)[0].slice(0,22))+'</small></div>'}).join('');
  body='<div class="g3-board"><small>TODAY</small><h2>'+esc(g3Plain(d.label))+'</h2><div class="g3-wb">'+rows+'</div></div><div class="g3-act"><button class="g3-big" data-a="begin">STEP ONTO THE FLOOR</button></div>';
 }else if(ph==='complete'){
  var act=state.plan.checks[g.date]&&state.plan.checks[g.date].actId&&state.activities.find(function(a){return a.id===state.plan.checks[g.date].actId});
  body='<div class="g3-board g3-final"><small>SESSION COMPLETE</small><h2>'+esc(g3Plain(d.label))+'</h2><div class="g3-stats"><div><b>'+stats.completed.length+'</b><span>SETS</span></div><div><b>'+(stats.volume?Math.round(stats.volume).toLocaleString():'0')+'</b><span>LB VOLUME</span></div><div><b>'+gwrFmtDuration(gwrElapsed(g))+'</b><span>TIME</span></div><div><b>+'+((act&&act.xp)||0)+'</b><span>XP</span></div></div></div><div class="g3-act"><button class="g3-big" data-a="finish">FINISH</button></div>';
 }else if(st){
  var it=st.it,label=g3LabelOf(g,it),info=gwrNumTarget(it),arch=S.stations[S.cur]?S.stations[S.cur].arch:'',cue=g3Plain(it.d);
  var dots=Array.from({length:it.n},function(_,j){var dn=((state.plan.checks[g.date]||{s:{}}).s[it.k]||[])[j];return '<i class="'+(dn?'on':j===st.j?'now':'')+'"></i>'}).join('');
  var head='<div class="g3-ex"><small>EXERCISE '+(S.cur+1)+' / '+S.N+'</small><h2>'+esc(label)+'</h2><div class="g3-sets"><span>SET '+(st.j+1)+' / '+it.n+'</span><span class="g3-dots">'+dots+'</span></div></div>';
  if(ph==='ready'){body=head+'<div class="g3-act"><div class="g3-cue">'+esc(cue)+'</div><button class="g3-big" data-a="go">START SET</button><div class="g3-row"><button class="g3-small" data-a="skip">Skip</button><button class="g3-small" data-a="sub">Swap machine</button></div></div>'}
  else if(ph==='active'){var tr=info.suggest||'';body=head+'<div class="g3-reps"><b data-g3-rep>1</b><span>REP</span><small>'+(info.kind==='reps'?'TARGET '+esc(String((g3Plain(it.d).match(/\d+(?:\s*[–—-]\s*\d+)?\s*(?:reps?)/i)||[tr+' reps'])[0])).toUpperCase():esc(info.label))+'</small></div><div class="g3-act"><button class="g3-big g3-done" data-a="setdone">SET DONE</button></div>'}
  else if(ph==='timing'){body=head+'<div class="g3-reps"><b data-g3-clock>00:00</b><span>REMAINING</span></div><div class="g3-act"><button class="g3-big g3-done" data-a="early">DONE EARLY</button></div>'}
  else if(ph==='timer-done'){body=head+'<div class="g3-reps"><b>DONE</b><span>TIME COMPLETE</span></div><div class="g3-act"><button class="g3-big g3-done" data-a="logtimer">LOG SET</button></div>'}
  else if(ph==='rest'){var nx=st,nlab=g3LabelOf(g,nx.it);body='<div class="g3-restwrap"><small>REST</small><div class="g3-clock" data-g3-rest>00:00</div><div class="g3-next">NEXT &middot; '+esc(nlab)+' &middot; SET '+(nx.j+1)+'/'+nx.it.n+'</div></div><div class="g3-act"><div class="g3-row"><button class="g3-small" data-a="rest-15">&minus;15s</button><button class="g3-small" data-a="rest+15">+15s</button></div><button class="g3-big" data-a="restskip">SKIP REST</button></div>'}
  else if(ph==='paused'){body=''}
 }
 hud.innerHTML=top+body;hud.dataset.phase=ph;
}
function g3Tick(){
 var S=G3;if(!S||!S.alive)return;var now=performance.now();if(now-(S.lastTick||0)<200)return;S.lastTick=now;
 var g=gwrCurrent();if(!g)return;var hud=S.sc.querySelector('.g3-hud');
 var el=hud.querySelector('[data-g3-elapsed]');if(el)el.textContent=gwrFmtDuration(gwrElapsed(g));
 var rp=hud.querySelector('[data-g3-rep]');if(rp&&S.rep)rp.textContent=S.rep;
 if(g.phase==='rest'){var left=Math.max(0,Math.ceil((g.restUntil-Date.now())/1000)),r=hud.querySelector('[data-g3-rest]');if(r){r.textContent=gwrFmtDuration(left*1000);r.classList.toggle('hot',left<=10)}
  if(left!==S.lastLeft){S.lastLeft=left;if(left>0&&left<=3)g3Beep(880,.12);if(left===0)g3Beep(1320,.5,.07)}}
 else S.lastLeft=-1;
 if(g.phase==='timing'){var rem=Math.max(0,g.timerUntil-Date.now()),c=hud.querySelector('[data-g3-clock]');if(c)c.textContent=gwrFmtDuration(rem)}
}
function g3Sheet(st,g){
 var S=G3,sh=S.sc.querySelector('.g3-sheet'),it=st.it,info=gwrNumTarget(it),label=g3LabelOf(g,it),last=g3Last(label);
 var w=null;if(it.wt){var lv=parseFloat(String(lastVal(it.k,g.date)||'').replace(/[^\d.]/g,''));w=last?last.weight:(isFinite(lv)&&lv>0?lv:0)}
 var reps=parseInt(info.suggest)||10;
 S.log={w:w,reps:reps,rpe:null,it:it,info:info,last:last};g3SheetRender();sh.hidden=false;sh.classList.remove('in');void sh.offsetWidth;sh.classList.add('in');
}
function g3SheetRender(){
 var S=G3,sh=S.sc.querySelector('.g3-sheet'),L=S.log;if(!L)return;
 var ghost=L.last?'<div class="g3-ghost">LAST TIME &middot; '+L.last.weight+' lb &times; '+(L.last.reps||'?')+'</div>':'';
 var wrow=L.w===null?'':'<div class="g3-field"><label>WEIGHT <small>lb</small></label><div class="g3-step"><button data-a="w-5">&minus;5</button><button data-a="w-2.5">&minus;2.5</button><b>'+(L.w)+'</b><button data-a="w+2.5">+2.5</button><button data-a="w+5">+5</button></div></div>';
 var rrow=L.info.kind==='reps'?'<div class="g3-field"><label>REPS DONE</label><div class="g3-step"><button data-a="r-1">&minus;</button><b>'+L.reps+'</b><button data-a="r+1">+</button></div></div>':(L.info.kind==='check'?'':'<div class="g3-field"><label>'+esc(L.info.label.toUpperCase())+'</label><div class="g3-step"><button data-a="r-5">&minus;5</button><b>'+L.reps+'</b><button data-a="r+5">+5</button></div></div>');
 var eff='<div class="g3-field"><label>EFFORT <small>how hard was it</small></label><div class="g3-eff">'+[6,7,8,9,10].map(function(n){return '<button data-a="eff'+n+'" class="'+(L.rpe===n?'on':'')+'">'+n+'</button>'}).join('')+'</div></div>';
 sh.innerHTML='<div class="g3-sheet-card"><div class="g3-grab"></div><h3>Log this set</h3>'+ghost+wrow+rrow+eff+'<div class="g3-row"><button class="g3-small" data-a="sheetx">Keep going</button><button class="g3-big" data-a="save">SAVE SET</button></div></div>';
}
function g3Save(){
 var S=G3,L=S.log;if(!L)return;var v=document.getElementById('gwrActualValue'),w=document.getElementById('gwrActualWeight');
 if(v&&L.info.kind!=='check')v.value=String(L.reps);if(w&&L.w!==null)w.value=String(L.w);
 if(L.rpe!=null&&gwrRpe!==L.rpe)gwrPickRpe(L.rpe);
 S.log=null;S.sc.querySelector('.g3-sheet').hidden=true;S.hudSig='';
 gwrLogSet();try{QSFX.play('chips-handle',{vol:.8})}catch(e){}
}
function g3HudClick(e){
 var b=e.target.closest('[data-a]');if(!b)return;var a=b.dataset.a,S=G3,g=gwrCurrent();if(!S||!g)return;var st=gwrStepNext(g),L=S.log;
 var chip=function(){try{QSFX.play('card-place',{vol:.5})}catch(x){}};
 if(a==='pause'){gwrPause();return}
 if(a==='menu'){var m=S.sc.querySelector('.g3-menu');m.innerHTML='<div class="g3-menu-card"><button data-a="m-itin">Itinerary</button><button data-a="m-undo">Undo last set</button><button data-a="m-skip">Skip this exercise</button><button data-a="m-sub">Swap machine</button><button data-a="m-end">End session now</button><button data-a="m-classic">Switch to classic screen</button><button data-a="m-x" class="g3-small">Close</button></div>';m.hidden=false;return}
 if(a.indexOf('m-')===0){S.sc.querySelector('.g3-menu').hidden=true;
  if(a==='m-itin')gwrQuickReview();else if(a==='m-undo')gwrUndoLast();else if(a==='m-skip')gwrSkipExercise();else if(a==='m-sub')gwrSubstituteExercise();else if(a==='m-end')gwrEndEarly();else if(a==='m-classic'){g3SetPref(false);g3Close()}return}
 if(a==='begin'){gwrBegin();return}
 if(a==='go'){gwrGo();return}
 if(a==='skip'){gwrSkipExercise();return}
 if(a==='sub'){gwrSubstituteExercise();return}
 if(a==='setdone'){chip();var info=gwrNumTarget(st.it);if(info.kind==='check'&&!st.it.wt){gwrLogSet()}else g3Sheet(st,g);return}
 if(a==='early'){gwrTimerFinished(true);return}
 if(a==='logtimer'){gwrLogSet();return}
 if(a==='restskip'){gwrRestDone();return}
 if(a==='rest+15'){gwrAdjustRest(15);return}
 if(a==='rest-15'){gwrAdjustRest(-15);return}
 if(a==='finish'){gwrExitComplete();return}
 if(a==='sheetx'){S.log=null;S.sc.querySelector('.g3-sheet').hidden=true;return}
 if(L){chip();var m;if(m=/^w([+-])([\d.]+)$/.exec(a))L.w=Math.max(0,Math.round((L.w+(m[1]==='+'?1:-1)*parseFloat(m[2]))*100)/100);else if(m=/^r([+-])(\d+)$/.exec(a))L.reps=Math.max(1,L.reps+(m[1]==='+'?1:-1)*parseInt(m[2]));else if(m=/^eff(\d+)$/.exec(a))L.rpe=L.rpe===+m[1]?null:+m[1];else if(a==='save'){g3Save();return}g3SheetRender()}
}
/* hook into the existing guided workout */
(function(){
 var oOpen=gwrOpen,oRender=gwrRender;
 gwrOpen=function(){oOpen.apply(this,arguments);try{if(!G3&&g3Want())g3Open()}catch(e){console.warn('[gym3d open]',e)}};
 gwrRender=function(){oRender.apply(this,arguments);try{if(G3)g3Sync()}catch(e){console.warn('[gym3d sync]',e)}};
})();
setTimeout(function(){try{var s=document.getElementById('gwr3dSel');if(s)s.value=g3Pref()?'on':'off'}catch(e){}},0);
