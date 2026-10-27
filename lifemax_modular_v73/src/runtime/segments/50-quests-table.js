/* ===== The Reading, in 3D =====
   A small WebGL2 renderer: the wooden table (OBJ + PBR-lite shading with a normal map), two candles that light the scene,
   fog, soft card shadows, and the three cards as real 3D objects that fall, shuffle, are laid on the wood, lifted to you and
   turned with your finger. Falls back to the flat reading when WebGL2 is missing. */
var T3_VS='#version 300 es\nlayout(location=0)in vec3 aP;layout(location=1)in vec3 aN;layout(location=2)in vec2 aUV;layout(location=3)in vec3 aT;uniform mat4 uM,uVP;out vec3 vP;out vec3 vN;out vec2 vUV;out vec3 vT;\n'+
 'void main(){vec4 w=uM*vec4(aP,1.);vP=w.xyz;vN=mat3(uM)*aN;vT=mat3(uM)*aT;vUV=aUV;gl_Position=uVP*w;}';
var T3_FS='#version 300 es\nprecision highp float;in vec3 vP;in vec3 vN;in vec2 vUV;in vec3 vT;out vec4 o;\n'+
 'uniform sampler2D uBase,uNorm,uORM;uniform vec3 uEye,uL1,uL2,uColor;uniform float uI1,uI2,uFlat,uNormY,uAmb,uFogK;\n'+
 'vec3 lightAt(vec3 P,vec3 N,vec3 V,vec3 lp,float I,vec3 alb,float rough){vec3 d=lp-P;float r2=dot(d,d);vec3 L=normalize(d);float att=I/(1.+r2*.0075);float df=max(dot(N,L),0.);vec3 H=normalize(L+V);float sh=mix(60.,6.,rough);float sp=pow(max(dot(N,H),0.),sh)*(1.-rough)*.7*step(.0,dot(N,L));return (alb*df+sp)*vec3(1.,.66,.36)*att;}\n'+
 'void main(){vec3 N=normalize(vN),V=normalize(uEye-vP);vec3 alb,orm=vec3(1.,.7,0.);\n'+
 'if(uFlat>.5){alb=uColor;}else{alb=pow(texture(uBase,vUV).rgb,vec3(2.2));orm=texture(uORM,vUV).rgb;vec3 T=normalize(vT-N*dot(N,vT)),B=cross(N,T);vec3 nm=texture(uNorm,vUV).xyz*2.-1.;nm.y*=uNormY;N=normalize(mat3(T,B,N)*nm);}\n'+
 'float rough=orm.g,ao=orm.r;if(uFlat>.5){ao=1.;}vec3 c=alb*vec3(.07,.09,.16)*uAmb*ao+lightAt(vP,N,V,uL1,uI1,alb,rough)+lightAt(vP,N,V,uL2,uI2,alb,rough);\n'+
 'c*=mix(1.,ao,.6);if(uFlat>.5)c+=uColor*vec3(1.,.8,.55)*.5;float dist=length(uEye-vP);c=mix(c,vec3(0.),1.-exp(-dist*uFogK));c=c/(c+vec3(.8));c=pow(c,vec3(1./2.2));o=vec4(c,1.);}';
var T3_CVS='#version 300 es\nlayout(location=0)in vec3 aP;layout(location=1)in vec3 aN;layout(location=2)in vec2 aUV;layout(location=3)in float aF;uniform mat4 uM,uVP;out vec3 vP;out vec3 vN;out vec2 vUV;out float vF;\n'+
 'void main(){vec4 w=uM*vec4(aP,1.);vP=w.xyz;vN=mat3(uM)*aN;vUV=aUV;vF=aF;gl_Position=uVP*w;}';
var T3_CFS='#version 300 es\nprecision highp float;in vec3 vP;in vec3 vN;in vec2 vUV;in float vF;out vec4 o;uniform sampler2D uFront,uBack;uniform vec3 uEye,uL1,uL2;uniform float uI1,uI2,uAmb,uSheen,uFogK,uFade;\n'+
 'vec3 lt(vec3 P,vec3 N,vec3 lp,float I){vec3 d=lp-P;float r2=dot(d,d);float att=I/(1.+r2*.0075);return vec3(1.,.7,.42)*max(dot(N,normalize(d)),0.)*att;}\n'+
 'void main(){vec3 N=normalize(vN);vec3 V=normalize(uEye-vP);vec3 alb=vF>1.5?pow(vec3(.95,.8,.5),vec3(2.2)):pow((vF<.5?texture(uFront,vUV):texture(uBack,vUV)).rgb,vec3(2.2));\n'+
 'vec3 c=alb*(vec3(.12,.13,.2)*uAmb+lt(vP,N,uL1,uI1)+lt(vP,N,uL2,uI2));\n'+
 'vec3 Lh=normalize(vec3(-.4,.7,.6));vec3 R=reflect(-Lh,N);c+=vec3(1.,.93,.78)*uSheen*pow(max(dot(R,V),0.),26.)*.3;\n'+
 'float dist=length(uEye-vP);c=mix(c,vec3(0.),(1.-exp(-dist*uFogK))*.8);c=c/(c+vec3(.9));c=pow(c,vec3(1./2.2));o=vec4(c,1.)*uFade;}';
var T3_BVS='#version 300 es\nlayout(location=0)in vec3 aP;layout(location=2)in vec2 aUV;uniform mat4 uM,uVP;out vec2 vUV;void main(){vUV=aUV;gl_Position=uVP*uM*vec4(aP,1.);}';
var T3_BFS='#version 300 es\nprecision mediump float;in vec2 vUV;out vec4 o;uniform float uA;void main(){float d=length(vUV*2.-1.);float a=smoothstep(1.,.15,d)*uA;o=vec4(0.,0.,0.,a);}';
var T3_FLVS='#version 300 es\nlayout(location=0)in vec2 aQ;uniform mat4 uV,uP;uniform vec3 uC;uniform vec2 uS;out vec2 vQ;void main(){vQ=aQ;vec4 v=uV*vec4(uC,1.);v.xy+=aQ*uS;gl_Position=uP*v;}';
var T3_FLFS='#version 300 es\nprecision mediump float;in vec2 vQ;out vec4 o;uniform float uA,uT;void main(){vec2 q=vQ;q.y=q.y*.62-.1;q.x*=1.+q.y*.9;float d=length(q*vec2(1.8,1.));float a=pow(smoothstep(1.,0.,d),1.6)*uA;vec3 c=mix(vec3(1.,.55,.18),vec3(1.,.93,.7),smoothstep(.5,0.,d));o=vec4(c*a,a);}';
/* ---- tiny maths ---- */
function t3m(){var m=new Float32Array(16);m[0]=m[5]=m[10]=m[15]=1;return m}
function t3mul(a,b){var o=new Float32Array(16);for(var c=0;c<4;c++)for(var r=0;r<4;r++){var s=0;for(var k=0;k<4;k++)s+=a[k*4+r]*b[c*4+k];o[c*4+r]=s}return o}
function t3persp(f,asp,n,fa){var t=1/Math.tan(f/2),o=new Float32Array(16);o[0]=t/asp;o[5]=t;o[10]=(fa+n)/(n-fa);o[11]=-1;o[14]=2*fa*n/(n-fa);return o}
function t3sub(a,b){return [a[0]-b[0],a[1]-b[1],a[2]-b[2]]}
function t3dot(a,b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]}
function t3cross(a,b){return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]}
function t3norm(a){var l=Math.hypot(a[0],a[1],a[2])||1;return [a[0]/l,a[1]/l,a[2]/l]}
function t3basis(eye,tgt){var f=t3norm(t3sub(tgt,eye)),r=t3norm(t3cross(f,[0,1,0])),u=t3cross(r,f);return {r:r,u:u,f:f}}
function t3view(eye,b){var o=t3m();o[0]=b.r[0];o[4]=b.r[1];o[8]=b.r[2];o[1]=b.u[0];o[5]=b.u[1];o[9]=b.u[2];o[2]=-b.f[0];o[6]=-b.f[1];o[10]=-b.f[2];o[12]=-t3dot(b.r,eye);o[13]=-t3dot(b.u,eye);o[14]=t3dot(b.f,eye);return o}
function t3qmul(a,b){return [a[3]*b[0]+a[0]*b[3]+a[1]*b[2]-a[2]*b[1],a[3]*b[1]-a[0]*b[2]+a[1]*b[3]+a[2]*b[0],a[3]*b[2]+a[0]*b[1]-a[1]*b[0]+a[2]*b[3],a[3]*b[3]-a[0]*b[0]-a[1]*b[1]-a[2]*b[2]]}
function t3qax(ax,deg){var h=deg*Math.PI/360,s=Math.sin(h);return [ax[0]*s,ax[1]*s,ax[2]*s,Math.cos(h)]}
function t3slerp(a,b,t){var d=a[0]*b[0]+a[1]*b[1]+a[2]*b[2]+a[3]*b[3];if(d<0){b=[-b[0],-b[1],-b[2],-b[3]];d=-d}if(d>.9995){var r=[a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t,a[2]+(b[2]-a[2])*t,a[3]+(b[3]-a[3])*t],l=Math.hypot(r[0],r[1],r[2],r[3]);return [r[0]/l,r[1]/l,r[2]/l,r[3]/l]}var th=Math.acos(d),s=Math.sin(th),w1=Math.sin((1-t)*th)/s,w2=Math.sin(t*th)/s;return [a[0]*w1+b[0]*w2,a[1]*w1+b[1]*w2,a[2]*w1+b[2]*w2,a[3]*w1+b[3]*w2]}
function t3qmat(q,p,s){var x=q[0],y=q[1],z=q[2],w=q[3],o=new Float32Array(16);s=s||[1,1,1];
 o[0]=(1-2*(y*y+z*z))*s[0];o[1]=2*(x*y+z*w)*s[0];o[2]=2*(x*z-y*w)*s[0];o[4]=2*(x*y-z*w)*s[1];o[5]=(1-2*(x*x+z*z))*s[1];o[6]=2*(y*z+x*w)*s[1];o[8]=2*(x*z+y*w)*s[2];o[9]=2*(y*z-x*w)*s[2];o[10]=(1-2*(x*x+y*y))*s[2];o[12]=p[0];o[13]=p[1];o[14]=p[2];o[15]=1;return o}
function t3qfromBasis(r,u,f){/* camera-to-world rotation with columns r,u,-f */var m00=r[0],m10=r[1],m20=r[2],m01=u[0],m11=u[1],m21=u[2],m02=-f[0],m12=-f[1],m22=-f[2],tr=m00+m11+m22,q;
 if(tr>0){var S=Math.sqrt(tr+1)*2;q=[(m21-m12)/S,(m02-m20)/S,(m10-m01)/S,.25*S]}else if(m00>m11&&m00>m22){var S2=Math.sqrt(1+m00-m11-m22)*2;q=[.25*S2,(m01+m10)/S2,(m02+m20)/S2,(m21-m12)/S2]}else if(m11>m22){var S3=Math.sqrt(1+m11-m00-m22)*2;q=[(m01+m10)/S3,.25*S3,(m12+m21)/S3,(m02-m20)/S3]}else{var S4=Math.sqrt(1+m22-m00-m11)*2;q=[(m02+m20)/S4,(m12+m21)/S4,.25*S4,(m10-m01)/S4]}return q}
function t3parseObj(text){
 var v=[],vt=[],vn=[],out=[];
 text.split('\n').forEach(function(l){var p=l.trim().split(/\s+/);if(p[0]==='v')v.push([+p[1],+p[2],+p[3]]);else if(p[0]==='vt')vt.push([+p[1],+p[2]]);else if(p[0]==='vn')vn.push([+p[1],+p[2],+p[3]]);
  else if(p[0]==='f'){var idx=p.slice(1).map(function(s){var a=s.split('/');return {v:v[+a[0]-1],t:vt[+a[1]-1]||[0,0],n:vn[+a[2]-1]}});
   for(var i=1;i<idx.length-1;i++){var A=idx[0],B=idx[i],C=idx[i+1],e1=t3sub(B.v,A.v),e2=t3sub(C.v,A.v),fn=t3norm(t3cross(e1,e2)),du1=B.t[0]-A.t[0],dv1=B.t[1]-A.t[1],du2=C.t[0]-A.t[0],dv2=C.t[1]-A.t[1],r=du1*dv2-du2*dv1||1e-9,tg=t3norm([(e1[0]*dv2-e2[0]*dv1)/r,(e1[1]*dv2-e2[1]*dv1)/r,(e1[2]*dv2-e2[2]*dv1)/r]);
    [A,B,C].forEach(function(q){var n=q.n||fn;out.push(q.v[0],q.v[1],q.v[2],n[0],n[1],n[2],q.t[0],1-q.t[1],tg[0],tg[1],tg[2])})}}});
 return new Float32Array(out);
}
function t3cylinder(r,h,seg){var o=[];for(var i=0;i<seg;i++){var a0=i/seg*6.2832,a1=(i+1)/seg*6.2832,c0=Math.cos(a0),s0=Math.sin(a0),c1=Math.cos(a1),s1=Math.sin(a1);
 var P=[[r*c0,0,r*s0,c0,0,s0],[r*c1,0,r*s1,c1,0,s1],[r*c1,h,r*s1,c1,0,s1],[r*c0,h,r*s0,c0,0,s0]];[0,1,2,0,2,3].forEach(function(k){var q=P[k];o.push(q[0],q[1],q[2],q[3],q[4],q[5],0,0,1,0,0)});
 [[0,0,0],[r*c1,h,r*s1],[r*c0,h,r*s0]].forEach(function(q,j){var p=j===0?[0,h,0]:q;o.push(p[0],p[1],p[2],0,1,0,0,0,1,0,0)})}return new Float32Array(o)}
/* ---- card faces drawn to canvases (the painted art plus the quest text) ---- */
function t3px(ctx,text,cx,y,scale,color){
 var s=qpxClean(text),w=qpxWidth(s)*scale,x=cx-w/2;ctx.fillStyle=color;
 for(var i=0;i<s.length;i++){var ch=s[i];if(ch===' '){x+=3*scale;continue}var g=QPX_FONT[ch]||QPX_FONT['?'];
  for(var yy=0;yy<7;yy++)for(var xx=0;xx<5;xx++)if(g[yy]&(16>>xx))ctx.fillRect(Math.round(x+xx*scale),Math.round(y+yy*scale),scale,scale);x+=6*scale}
}
function t3img(url){return new Promise(function(res){var im=new Image();im.onload=function(){res(im)};im.onerror=function(){res(null)};im.src=url})}
async function t3face(q,numeral,done){
 var W=640,H=1120,cv=document.createElement('canvas');cv.width=W;cv.height=H;var c=cv.getContext('2d'),u=qartUrl&&qartUrl(q.attribute);
 var im=u?await t3img(u):null;
 if(im)c.drawImage(im,0,0,W,H);else{c.fillStyle='#1a1428';c.fillRect(0,0,W,H)}
 var pn=qartPanelOf(q.attribute),top=H*pn[0]/100,bot=H*(1-pn[1]/100),x0=W*.09,x1=W*.91,cx=W/2,arc=QPX_ARC[q.attribute]||QPX_ARC.discipline,tier=q.tier||1;
 var ph=bot-top,y=top+ph*.1;
 for(var i=0;i<3;i++){c.save();c.translate(x0+14+i*22,y+12);c.rotate(Math.PI/4);c.fillStyle=i<tier?'#b8761a':'rgba(70,40,10,.25)';c.fillRect(-7,-7,14,14);c.restore()}
 t3px(c,arc.name,cx,y,3,'#7a4a12');t3px(c,numeral,x1-10-qpxWidth(numeral)*3/2,y,3,'#7a4a12');
 var lines=qpxWrap(q.name,Math.floor((x1-x0)/5),2),ny=y+34;lines.forEach(function(l,k){t3px(c,l,cx,ny+k*48,5,'#2b1608')});
 c.fillStyle='#4a2e14';c.font='italic 27px Georgia,serif';c.textAlign='center';
 var words=String(q.desc||'').split(/\s+/),line='',dl=[],maxW=x1-x0-10;words.forEach(function(w){var t=line?line+' '+w:w;if(c.measureText(t).width>maxW&&line){dl.push(line);line=w}else line=t});if(line)dl.push(line);dl=dl.slice(0,2);
 var dy=ny+lines.length*48+30;dl.forEach(function(l,k){c.fillText(l,cx,dy+k*31)});
 if(done){c.save();c.translate(W/2,H*.40);c.rotate(-.24);var tw2=qpxWidth('FULFILLED')*7+36;c.fillStyle='rgba(8,18,12,.82)';c.fillRect(-tw2/2,-34,tw2,68);c.strokeStyle='#7be0a0';c.lineWidth=5;c.strokeRect(-tw2/2,-34,tw2,68);t3px(c,'FULFILLED',0,-24,7,'#7be0a0');c.restore();c.fillStyle='rgba(0,0,0,.18)';c.fillRect(0,0,W,H)}
 return cv;
}
var T3_SHARED=null;
async function t3load(){
 if(T3_SHARED)return T3_SHARED;
 var tex={};for(var k in TABLE_TEX)tex[k]=await t3img('data:image/webp;base64,'+TABLE_TEX[k]);
 T3_SHARED={tex:tex,mesh:t3parseObj(TABLE_OBJ),cyl:t3cylinder(2.3,11,18)};return T3_SHARED;
}
function t3cardMesh(W,H,T,R,seg){
 var hx=W/2,hy=H/2,pts=[],cs=[[hx-R,hy-R,0],[-hx+R,hy-R,90],[-hx+R,-hy+R,180],[hx-R,-hy+R,270]];
 cs.forEach(function(c){for(var i=0;i<=seg;i++){var a=(c[2]+i*90/seg)*Math.PI/180;pts.push([c[0]+R*Math.cos(a),c[1]+R*Math.sin(a)])}});
 var n=pts.length,o=[],V=function(x,y,z,nx,ny,nz,u,v,f){o.push(x,y,z,nx,ny,nz,u,v,f)};
 for(var i=0;i<n;i++){var p=pts[i],q=pts[(i+1)%n];
  V(0,0,T/2,0,0,1,.5,.5,0);V(p[0],p[1],T/2,0,0,1,p[0]/W+.5,.5-p[1]/H,0);V(q[0],q[1],T/2,0,0,1,q[0]/W+.5,.5-q[1]/H,0);
  V(0,0,-T/2,0,0,-1,.5,.5,1);V(q[0],q[1],-T/2,0,0,-1,.5-q[0]/W,.5-q[1]/H,1);V(p[0],p[1],-T/2,0,0,-1,.5-p[0]/W,.5-p[1]/H,1);
  var dx=q[0]-p[0],dy=q[1]-p[1],l=Math.hypot(dx,dy)||1,nx=dy/l,ny=-dx/l;
  V(p[0],p[1],T/2,nx,ny,0,0,0,2);V(q[0],q[1],T/2,nx,ny,0,0,0,2);V(q[0],q[1],-T/2,nx,ny,0,0,0,2);
  V(p[0],p[1],T/2,nx,ny,0,0,0,2);V(q[0],q[1],-T/2,nx,ny,0,0,0,2);V(p[0],p[1],-T/2,nx,ny,0,0,0,2)}
 return new Float32Array(o);
}
/* ---- the scene ---- */
async function dhTable3D(el,cards,d,replay,gated,restOnly){
 var BX=dhGateBox(),vw=innerWidth-BX.left-BX.right,vh=innerHeight-BX.top-BX.bottom,sh;
 try{sh=await t3load()}catch(e){console.warn('[table load]',e);return false}
 var sc=document.createElement('div');sc.id='fateScene';sc.className='fs-3d';sc.setAttribute('data-noswipe','1');sc.style.cssText='left:'+BX.left+'px;top:'+BX.top+'px;right:'+BX.right+'px;bottom:'+BX.bottom+'px';
 var gateHtml=gated?'<div class="fs-gate"><div class="gate3-top"><div class="gate3-eyebrow">'+qpxText('A NEW DAY. A NEW DRAW.',{s:1,w:300,lines:1})+'</div><h2 class="gate3-title">'+qpxText('YOUR CARDS ARE WAITING',{s:3,w:Math.min(330,vw-40),lines:3})+'<span class="sr">Your cards are waiting</span></h2><div class="gate3-rule"><i></i></div><p class="gate3-sub">Three quests, drawn from the dark.</p></div><div class="gate3-bot"><button class="gate3-btn" type="button" aria-label="Get your quests"><span class="g3c tl"></span><span class="g3c tr"></span><span class="g3c bl"></span><span class="g3c br"></span><span class="gate3-btn-t">'+qpxText('GET YOUR QUESTS',{s:2,w:240,lines:1})+'</span></button><button class="gate3-skip" type="button">'+qpxText('NOT NOW',{s:1,w:80,lines:1})+'</button></div></div>':'';
 sc.innerHTML='<canvas class="fs-gl"></canvas>'+gateHtml+'<div class="fs-title"></div><div class="fs-cap"></div><button class="fs-skip" type="button" aria-label="Skip the reading"'+(gated?' style="display:none"':'')+'>'+qpxText('SKIP',{s:2,w:60,lines:1})+'</button>';
 document.body.appendChild(sc);
 var cvs=sc.querySelector('.fs-gl'),dprs=Math.min(1.75,window.devicePixelRatio||1);cvs.width=Math.round(vw*dprs);cvs.height=Math.round(vh*dprs);
 var gl=cvs.getContext('webgl2',{alpha:false,antialias:true,depth:true,powerPreference:'high-performance'});
 if(!gl){sc.remove();return false}
 var ST={flash:0,flare:1,shake:0},skipped=false,alive=true,raf=0,tw=[],waits=[],awaitFlip=null;
 var progs={},P=function(n,vs,fs){var p=gl.createProgram();[[gl.VERTEX_SHADER,vs],[gl.FRAGMENT_SHADER,fs]].forEach(function(a){var s=gl.createShader(a[0]);gl.shaderSource(s,a[1]);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw new Error(n+': '+gl.getShaderInfoLog(s));gl.attachShader(p,s)});gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw new Error(n+' link: '+gl.getProgramInfoLog(p));p._u={};progs[n]=p;return p};
 var UL=function(p,n){if(!(n in p._u))p._u[n]=gl.getUniformLocation(p,n);return p._u[n]};
 var pTab,pCard,pBlob,pFlame;
 try{pTab=P('tab',T3_VS,T3_FS);pCard=P('card',T3_CVS,T3_CFS);pBlob=P('blob',T3_BVS,T3_BFS);pFlame=P('flame',T3_FLVS,T3_FLFS)}
 catch(e){console.warn('[table gl]',e);sc.remove();try{gl.getExtension('WEBGL_lose_context').loseContext()}catch(x){}return false}
 /* geometry */
 var vao=function(data,layout){var v=gl.createVertexArray();gl.bindVertexArray(v);var b=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,b);gl.bufferData(gl.ARRAY_BUFFER,data,gl.STATIC_DRAW);layout.forEach(function(l){gl.enableVertexAttribArray(l[0]);gl.vertexAttribPointer(l[0],l[1],gl.FLOAT,false,l[3],l[2])});return v};
 var tabV=vao(sh.mesh,[[0,3,0,44],[1,3,12,44],[2,2,24,44],[3,3,32,44]]),tabN=sh.mesh.length/11;
 var cylV=vao(sh.cyl,[[0,3,0,44],[1,3,12,44],[2,2,24,44],[3,3,32,44]]),cylN=sh.cyl.length/11;
 var CW=11.2,CH=CW*1659/948,CT=.34,DT=.38,TY=72.05,TC=TY+CT/2,cardM=t3cardMesh(CW,CH,CT,.62,5),cardN=cardM.length/9;
 var cardV=vao(cardM,[[0,3,0,36],[1,3,12,36],[2,2,24,36],[3,1,32,36]]);
 var bq=new Float32Array([-.5,0,-.5,0,1,0,0,0,0, .5,0,-.5,0,1,0,1,0,0, .5,0,.5,0,1,0,1,1,0, -.5,0,-.5,0,1,0,0,0,0, .5,0,.5,0,1,0,1,1,0, -.5,0,.5,0,1,0,0,1,0]);
 var blobV=vao(bq,[[0,3,0,36],[2,2,24,36]]);
 var fq=vao(new Float32Array([-1,-1,1,-1,-1,1,1,1]),[[0,2,0,0]]);
 /* textures */
 var mkTex=function(src,repeat){var t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,src);gl.generateMipmap(gl.TEXTURE_2D);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR_MIPMAP_LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);var w=repeat?gl.REPEAT:gl.CLAMP_TO_EDGE;gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,w);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,w);var ex=gl.getExtension('EXT_texture_filter_anisotropic');if(ex)gl.texParameterf(gl.TEXTURE_2D,ex.TEXTURE_MAX_ANISOTROPY_EXT,4);return t};
 var tBase=mkTex(sh.tex.base,true),tNorm=mkTex(sh.tex.normal,true),tOrm=mkTex(sh.tex.orm,true);
 var backUrl=qartUrl('back'),backImg=backUrl?await t3img(backUrl):null,tBack=mkTex(backImg||sh.tex.base,false);
 var qs=[0,1,2].map(function(i){return dhCards().find(function(z){return z.slot===i})||{tier:1,attribute:'discipline',name:'Quest',desc:''}});
 var faces=await Promise.all(qs.map(function(q,i){return t3face(q,DH_NUMERALS[i],isQuestDone(q))})),tFace=faces.map(function(c){return mkTex(c,false)});
 /* the cards */
 var rnd=function(a,b){return a+Math.random()*(b-a)};
 var faceDown=function(yaw){return t3qmul(t3qax([0,1,0],yaw+180),t3qax([1,0,0],90))},faceUp=function(yaw){return t3qmul(t3qax([0,1,0],yaw),t3qax([1,0,0],-90))};
 var C=[];for(var i=0;i<10;i++)C.push({p:[0,300,0],q:faceDown(0),front:null,sh:0,fade:1,yaw:0});
 var dealt=[C[9],C[8],C[7]];dealt.forEach(function(c,i){c.front=tFace[i]});
 /* camera */
 var EYE=[0,138,68],TGT=[0,72,-2],REST_EYE=[0,112,70],REST_TGT=[0,72,6],FAR_EYE=[60,150,340],FAR_TGT=[0,38,0],eyeB=FAR_EYE.slice(),tgtB=FAR_TGT.slice(),FOV=56*Math.PI/180,asp=cvs.width/cvs.height,proj=t3persp(FOV,asp,3,1500),bas=t3basis(eyeB,tgtB),camQ=t3qfromBasis(bas.r,bas.u,bas.f);
 var curEye=eyeB.slice(),viewM=t3view(eyeB,bas),vp=t3mul(proj,viewM);
 var project=function(w){var m=vp,x=m[0]*w[0]+m[4]*w[1]+m[8]*w[2]+m[12],y=m[1]*w[0]+m[5]*w[1]+m[9]*w[2]+m[13],ww=m[3]*w[0]+m[7]*w[1]+m[11]*w[2]+m[15];return [BX.left+(x/ww*.5+.5)*vw,BX.top+(1-(y/ww*.5+.5))*vh]};
 var cardRect=function(c){var M=t3qmat(c.q,c.p,[1,1,1]),pts=[[-CW/2,-CH/2,0],[CW/2,-CH/2,0],[CW/2,CH/2,0],[-CW/2,CH/2,0]].map(function(a){return project([M[0]*a[0]+M[4]*a[1]+M[8]*a[2]+M[12],M[1]*a[0]+M[5]*a[1]+M[9]*a[2]+M[13],M[2]*a[0]+M[6]*a[1]+M[10]*a[2]+M[14]])}),xs=pts.map(function(p){return p[0]}),ys=pts.map(function(p){return p[1]}),l=Math.min.apply(0,xs),r=Math.max.apply(0,xs),t=Math.min.apply(0,ys),b=Math.max.apply(0,ys);return {left:l,right:r,top:t,bottom:b,width:r-l,height:b-t}};
 var CANDLES=[[-23,72,-27],[23,72,-27]],flames=CANDLES.map(function(p){return [p[0],p[1]+13.2,p[2]]});
 /* helpers */
 var hap=function(n){try{if(window.LifeMaxHaptics)LifeMaxHaptics.play(n)}catch(e){}};
 var eIO=function(t){return t<.5?4*t*t*t:1-Math.pow(-2*t+2,3)/2},eO=function(t){return 1-Math.pow(1-t,3)},eBounce=function(t){var n=7.5625,dd=2.75;if(t<1/dd)return n*t*t;if(t<2/dd){t-=1.5/dd;return n*t*t+.75}if(t<2.5/dd){t-=2.25/dd;return n*t*t+.9375}t-=2.625/dd;return n*t*t+.984375};
 var anim=function(ms,fn,ease){return new Promise(function(res){tw.push({t0:performance.now(),ms:skipped?0:ms,fn:fn,ease:ease||eIO,res:res})})};
 var wait=function(ms){return new Promise(function(r){var t=setTimeout(r,skipped?0:ms);waits.push([t,r])})};
 var lerp=function(a,b,t){return a+(b-a)*t},lerp3=function(a,b,t){return [lerp(a[0],b[0],t),lerp(a[1],b[1],t),lerp(a[2],b[2],t)]};
 var move=function(c,p1,q1,ms,o){o=o||{};var p0=c.p.slice(),q0=c.q.slice();return anim(ms,function(t){var pp=lerp3(p0,p1,t);if(o.arc)pp[1]+=o.arc*Math.sin(Math.PI*t);c.p=pp;c.q=t3slerp(q0,q1,t)},o.ease)};
 var dust=function(w,n){try{var s=project(w);for(var k=0;k<(n||14);k++)QFX.emit(s[0]+rnd(-34,34),s[1]+rnd(-8,14),rnd(-90,90),rnd(-50,10),rnd(.7,1.3),rnd(12,26),[.5,.42,.34])}catch(e){}};
 var thud=function(w,n,big){ST.shake=Math.max(ST.shake,big||1);hap('success');dust(w,n);ST.flare=1};
 var cap=sc.querySelector('.fs-cap'),title=sc.querySelector('.fs-title');
 var type=async function(text,hold){for(var i=1;i<=text.length&&!skipped;i++){cap.innerHTML=qpxText(text.slice(0,i),{s:2,w:vw-40,lines:1});await wait(30)}await wait(hold||400)};
 /* render */
 var t0=performance.now(),mm=t3m();
 var drawCards=function(){gl.useProgram(pCard);gl.bindVertexArray(cardV);
  gl.uniformMatrix4fv(UL(pCard,'uVP'),false,vp);gl.uniform3fv(UL(pCard,'uEye'),curEye);gl.uniform3fv(UL(pCard,'uL1'),flames[0]);gl.uniform3fv(UL(pCard,'uL2'),flames[1]);gl.uniform1f(UL(pCard,'uI1'),L1);gl.uniform1f(UL(pCard,'uI2'),L2);gl.uniform1f(UL(pCard,'uFogK'),.0016);gl.uniform1i(UL(pCard,'uFront'),3);gl.uniform1i(UL(pCard,'uBack'),4);
  gl.activeTexture(gl.TEXTURE4);gl.bindTexture(gl.TEXTURE_2D,tBack);
  C.forEach(function(c){if(c.fade<=0)return;gl.uniformMatrix4fv(UL(pCard,'uM'),false,t3qmat(c.q,c.p,[1,1,1]));gl.uniform1f(UL(pCard,'uAmb'),c.amb||1.3);gl.uniform1f(UL(pCard,'uSheen'),c.sheen||0);gl.uniform1f(UL(pCard,'uFade'),c.fade);gl.activeTexture(gl.TEXTURE3);gl.bindTexture(gl.TEXTURE_2D,c.front||tBack);gl.drawArrays(gl.TRIANGLES,0,cardN)})};
 var L1=3,L2=3,gutter=1,idleDrift=false,lastDrift=[0,0,0];
 var render=function(){
  var t=(performance.now()-t0)/1000;C.forEach(function(c){if(c.lift)c.p=[curEye[0]+bas.f[0]*LD,curEye[1]+bas.f[1]*LD,curEye[2]+bas.f[2]*LD]});ST.flash*=.9;ST.shake*=.9;
  L1=gutter*(5.2+.8*Math.sin(t*11)+.5*Math.sin(t*23+1))*(1+ST.flash*1.2);L2=gutter*(5.2+.8*Math.sin(t*9+2)+.5*Math.sin(t*19))*(1+ST.flash*1.2);
  bas=t3basis(eyeB,tgtB);camQ=t3qfromBasis(bas.r,bas.u,bas.f);var sw=Math.min(1,Math.max(0,1-Math.hypot(eyeB[0]-EYE[0],eyeB[2]-EYE[2])/60));lastDrift=idleDrift?[Math.sin(t*.22)*16,Math.sin(t*.17)*5,Math.cos(t*.2)*9]:[0,0,0];curEye=[eyeB[0]+lastDrift[0]+rnd(-1,1)*ST.shake*.5+Math.sin(t*.4)*.8*sw,eyeB[1]+lastDrift[1]+rnd(-1,1)*ST.shake*.5,eyeB[2]+lastDrift[2]+Math.cos(t*.3)*.6*sw];viewM=t3view(curEye,bas);vp=t3mul(proj,viewM);
  gl.viewport(0,0,cvs.width,cvs.height);gl.clearColor(0,0,0,1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
  gl.disable(gl.BLEND);
  gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);
  /* table and candles */
  gl.useProgram(pTab);gl.uniformMatrix4fv(UL(pTab,'uVP'),false,vp);gl.uniform3fv(UL(pTab,'uEye'),curEye);gl.uniform3fv(UL(pTab,'uL1'),flames[0]);gl.uniform3fv(UL(pTab,'uL2'),flames[1]);gl.uniform1f(UL(pTab,'uI1'),L1);gl.uniform1f(UL(pTab,'uI2'),L2);gl.uniform1f(UL(pTab,'uNormY'),-1);gl.uniform1f(UL(pTab,'uAmb'),.55);gl.uniform1f(UL(pTab,'uFogK'),.0016);
  gl.uniform1i(UL(pTab,'uBase'),0);gl.uniform1i(UL(pTab,'uNorm'),1);gl.uniform1i(UL(pTab,'uORM'),2);
  gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,tBase);gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,tNorm);gl.activeTexture(gl.TEXTURE2);gl.bindTexture(gl.TEXTURE_2D,tOrm);
  gl.uniform1f(UL(pTab,'uFlat'),0);gl.uniformMatrix4fv(UL(pTab,'uM'),false,mm);gl.bindVertexArray(tabV);gl.drawArrays(gl.TRIANGLES,0,tabN);
  gl.uniform1f(UL(pTab,'uFlat'),1);gl.uniform3f(UL(pTab,'uColor'),.82,.74,.58);gl.bindVertexArray(cylV);
  CANDLES.forEach(function(p){var m=t3m();m[12]=p[0];m[13]=p[1];m[14]=p[2];gl.uniformMatrix4fv(UL(pTab,'uM'),false,m);gl.drawArrays(gl.TRIANGLES,0,cylN)});
  /* soft shadows */
  gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);gl.depthMask(false);gl.useProgram(pBlob);gl.bindVertexArray(blobV);gl.uniformMatrix4fv(UL(pBlob,'uVP'),false,vp);
  C.forEach(function(c){if(c.sh<=.01)return;var m=t3qmat(c.q,[c.p[0]+1.2,72.06,c.p[2]+1.6],[1,1,1]);/* flat on the table: yaw only */var yaw=c.yaw||0,cy=Math.cos(yaw*Math.PI/180),sy=Math.sin(yaw*Math.PI/180),M=t3m();M[0]=cy*CW*1.45;M[2]=-sy*CW*1.45;M[8]=sy*CH*1.35;M[10]=cy*CH*1.35;M[12]=c.p[0]+1.4;M[13]=72.07;M[14]=c.p[2]+1.8;gl.uniformMatrix4fv(UL(pBlob,'uM'),false,M);gl.uniform1f(UL(pBlob,'uA'),c.sh*.42*c.fade);gl.drawArrays(gl.TRIANGLES,0,6)});
  gl.depthMask(true);gl.disable(gl.BLEND);
  drawCards();
  /* flames */
  gl.enable(gl.BLEND);gl.blendFunc(gl.ONE,gl.ONE);gl.depthMask(false);gl.useProgram(pFlame);gl.bindVertexArray(fq);gl.uniformMatrix4fv(UL(pFlame,'uV'),false,viewM);gl.uniformMatrix4fv(UL(pFlame,'uP'),false,proj);
  flames.forEach(function(f,i){gl.uniform3fv(UL(pFlame,'uC'),f);gl.uniform2f(UL(pFlame,'uS'),3.2,5.4);gl.uniform1f(UL(pFlame,'uA'),gutter*(.85+.15*Math.sin(t*14+i*2)));gl.uniform1f(UL(pFlame,'uT'),t);gl.drawArrays(gl.TRIANGLE_STRIP,0,4)});
  gl.depthMask(true);gl.disable(gl.BLEND);
 };
 var lastDraw=0,dragging=false,restMode=false;
 var loop=function(){if(!alive)return;var now=performance.now();if(document.hidden){raf=requestAnimationFrame(loop);return}if(restMode&&!tw.length&&!dragging&&!ST.shake&&ST.flash<.02&&now-lastDraw<48){raf=requestAnimationFrame(loop);return}lastDraw=now;for(var i=tw.length-1;i>=0;i--){var a=tw[i],p=a.ms?Math.min(1,(now-a.t0)/a.ms):1;a.fn(a.ease(p));if(p>=1){tw.splice(i,1);a.res()}}render();raf=requestAnimationFrame(loop)};
 var skip=function(){if(skipped)return;skipped=true;tw.forEach(function(a){a.ms=0});waits.forEach(function(w){clearTimeout(w[0]);w[1]()});if(awaitFlip)awaitFlip.finish()};
 sc.querySelector('.fs-skip').addEventListener('click',skip);
 /* the table pose of each dealt card and deck */
 var LD=CH/(.74*2*Math.tan(FOV/2)),DECK=[0,0,-17],SIDE=[28,0,-12],slots=[[-12.4,0,14],[0,0,14],[12.4,0,14]];
 /* turning a lifted card with a finger: it rotates about whatever axis your drag implies (arcball style), so a corner-to-corner
    drag flips it diagonally. On release it settles upright onto whichever face is turned toward you. */
 var qrot=function(q,v){var u=[q[0],q[1],q[2]],w=q[3],c1=t3cross(u,v),c2=t3cross(u,c1);return [v[0]+2*(w*c1[0]+c2[0]),v[1]+2*(w*c1[1]+c2[1]),v[2]+2*(w*c1[2]+c2[2])]};
 var QBACK=t3qax([0,1,0],180),QFRONT=[0,0,0,1];
 var flipByHand=function(c,lifted){return new Promise(function(res){
  var qv=QBACK.slice(),q0=QBACK.slice(),drag=false,sx=0,sy=0,samples=[],seen=false,settling=0,done=false,hint=document.createElement('span');hint.className='fs-hint';hint.innerHTML='<i></i>';sc.appendChild(hint);
  var idle=setTimeout(function(){hint.classList.add('on')},2600);
  var frontFacing=function(q){return qrot(q,[0,0,1])[2]>0};
  var apply=function(){c.q=t3qmul(camQ,qv);var nz=Math.abs(qrot(qv,[0,0,1])[2]);c.sheen=.08+.45*(1-nz);c.lift=true;c.amb=4.2};
  apply();
  var finish=function(){if(done)return;done=true;clearTimeout(idle);cancelAnimationFrame(settling);sc.removeEventListener('pointerdown',down);sc.removeEventListener('pointermove',mv);sc.removeEventListener('pointerup',up);sc.removeEventListener('pointercancel',up);hint.remove();qv=QFRONT.slice();apply();awaitFlip=null;res()};
  var down=function(e){if(done||settling||e.target.closest('.fs-skip'))return;drag=true;sx=e.clientX;sy=e.clientY;q0=qv.slice();samples=[[performance.now(),0,0]];hint.classList.remove('on');clearTimeout(idle);try{sc.setPointerCapture(e.pointerId)}catch(x){}e.preventDefault()};
  var rotFor=function(dx,dy,k){var len=Math.hypot(dx,dy);if(len<.5)return [0,0,0,1];/* axis is perpendicular to the drag, in the screen plane */return t3qax([-dy/len,dx/len,0],len*k)};
  var mv=function(e){if(!drag)return;var dx=e.clientX-sx,dy=e.clientY-sy;qv=t3qmul(rotFor(dx,dy,.9),q0);apply();var now=performance.now();samples.push([now,dx,dy]);while(samples.length>2&&now-samples[0][0]>90)samples.shift();if(frontFacing(qv)&&!seen){seen=true;ST.flash=Math.max(ST.flash,.2);hap('tap')}};
  var up=function(){if(!drag)return;drag=false;
   var n=samples.length,vx=0,vy=0;if(n>1){var dt=Math.max(16,samples[n-1][0]-samples[0][0]);vx=(samples[n-1][1]-samples[0][1])/dt*1000;vy=(samples[n-1][2]-samples[0][2])/dt*1000}
   var boost=rotFor(vx,vy,1);var vl=Math.hypot(vx,vy),bq=vl>1?t3qax([-vy/vl,vx/vl,0],Math.min(45,vl*.04)):[0,0,0,1],pred=t3qmul(bq,qv),towardFront=frontFacing(pred),target=towardFront?QFRONT:QBACK,from=qv.slice(),t1=performance.now(),dur=560;
   var step=function(){var t=Math.min(1,(performance.now()-t1)/dur),c1=1.2,c3=c1+1,e=1+c3*Math.pow(t-1,3)+c1*Math.pow(t-1,2);qv=t3slerp(from,target,e);apply();
    if(t<1)settling=requestAnimationFrame(step);else{settling=0;qv=target.slice();apply();if(towardFront)finish();else{seen=false;idle=setTimeout(function(){hint.classList.add('on')},2200)}}};settling=requestAnimationFrame(step)};
  sc.addEventListener('pointerdown',down);sc.addEventListener('pointermove',mv);sc.addEventListener('pointerup',up);sc.addEventListener('pointercancel',up);
  awaitFlip={finish:finish};
 })};
 try{
  var gateP=null,gateEmbers=null,gateWatch=null;
  if(gated){gateP=new Promise(function(res){var done=false,end=function(v){if(done)return;done=true;clearInterval(gateEmbers);clearInterval(gateWatch);res(v)};sc.querySelector('.gate3-btn').addEventListener('click',function(){end('go')});sc.querySelector('.gate3-skip').addEventListener('click',function(){end('skip')});gateWatch=setInterval(function(){if(!dhViewOn())end('leave')},250)})}
  loop();
  await anim(700,function(t){sc.style.opacity=t},eO);
  var teardown=function(){alive=false;cancelAnimationFrame(raf);try{gl.getExtension('WEBGL_lose_context').loseContext()}catch(e){}sc.remove()};
  if(restOnly){
   dealt.forEach(function(c,i){var yy=(i-1)*3;c.yaw=yy;c.front=tFace[i];c.p=[slots[i][0],TC,slots[i][2]];c.q=faceUp(yy);c.sh=.7});
   for(var ii=0;ii<10;ii++){var oc=C[ii];if(dealt.indexOf(oc)<0){oc.p=[SIDE[0],TC+(ii)*DT*.6,SIDE[2]];oc.q=faceDown(rnd(-4,4));oc.sh=.7}}
   eyeB=lerp3(FAR_EYE,EYE,.0);tgtB=FAR_TGT.slice();gutter=0;var up1=anim(1200,function(t){gutter=t},eO);
   await Promise.all([anim(1700,function(t){eyeB=lerp3([40,120,230],REST_EYE,t);tgtB=lerp3([0,50,0],REST_TGT,t)},eIO),up1]);gutter=1;
  }else{
  if(gated){
   /* the opening shot is the splash: the table floats in the void with the deck on it, and one button */
   C.forEach(function(c,i){c.p=[DECK[0],TC+i*DT,DECK[2]];c.q=faceDown(rnd(-3,3));c.sh=.7});
   gutter=0;idleDrift=true;var lightUp=anim(2200,function(t){gutter=t},eO);
   var gate=sc.querySelector('.fs-gate');gate.classList.add('on');
   gateEmbers=setInterval(function(){try{flames.forEach(function(f){var q=project(f);QFX.emit(q[0]+rnd(-6,6),q[1]-8,rnd(-14,14),-rnd(30,70),rnd(1.2,2.2),rnd(3,7),[1,.55+rnd(0,.2),.2])})}catch(e){}},300);
   var choice=await gateP;clearInterval(gateEmbers);
   if(choice==='leave'){teardown();return 'leave'}
   if(choice==='skip'){hap('tap');await anim(350,function(t){sc.style.opacity=1-t});teardown();return 'skip'}
   /* pressed: the button flares, the candles surge, then the camera is drawn in */
   var gb=sc.querySelector('.gate3-btn'),gr=gb.getBoundingClientRect();gb.classList.add('lit');hap('success');ST.flash=1;ST.shake=1.2;ST.flare=1;
   try{QFX.burst({left:gr.left,top:gr.top,width:gr.width,height:gr.height,right:gr.right,bottom:gr.bottom},3)}catch(e){}
   await wait(380);hap('big');
   sc.querySelector('.fs-skip').style.display='';
   var startEye=[eyeB[0]+lastDrift[0],eyeB[1]+lastDrift[1],eyeB[2]+lastDrift[2]];eyeB=startEye;idleDrift=false;
   anim(520,function(t){gate.style.opacity=1-t});
   await Promise.all([anim(3300,function(t){eyeB=lerp3(startEye,EYE,t);tgtB=lerp3(FAR_TGT,TGT,t)},eIO),lightUp]);gutter=1;gate.remove();
  }else{
  C.forEach(function(c){c.p=[DECK[0],400,DECK[2]]});
  gutter=0;var lightUp2=anim(2600,function(t){gutter=t},eO),dolly=anim(3300,function(t){eyeB=lerp3(FAR_EYE,EYE,t);tgtB=lerp3(FAR_TGT,TGT,t)},eIO);
  await type('THE TABLE IS SET',200);await Promise.all([lightUp2,dolly]);gutter=1;
  /* the deck drops onto the wood */
  var drops=[];
  for(var i=0;i<10&&!skipped;i++){(function(i){var c=C[i],yaw=rnd(-5,5),x=DECK[0]+rnd(-.6,.6),z=DECK[2]+rnd(-.6,.6),from=[x+rnd(-8,8),TC+70+i*9,z+rnd(-8,8)],to=[x,TC+i*DT,z],q0=faceDown(rnd(-40,40)),q1=faceDown(yaw);c.yaw=yaw;
   drops.push(wait(i*60).then(function(){if(skipped){c.p=to;c.q=q1;c.sh=.7;return}return anim(520,function(t){c.p=lerp3(from,to,t);c.q=t3slerp(q0,q1,t);c.sh=Math.min(.8,t*1.2)},eBounce)}))})(i)}
  await Promise.all(drops);if(!skipped)thud([DECK[0],TY,DECK[2]],22,2);
  }
  /* it begins to move: the deck slides to the middle of the table */
  var mid=[DECK[0],TY,DECK[2]];
  await Promise.all(C.map(function(c,i){return move(c,[mid[0],TC+i*DT,mid[2]],faceDown(rnd(-3,3)),600)}));
  await type('THE DECK IS SHUFFLED',120);
  for(var r=0;r<2&&!skipped;r++){
   await Promise.all(C.map(function(c,i){var left=i%2===0;return move(c,[mid[0]+(left?-9:9),TC+(i>>1)*DT+.3,mid[2]],faceDown((left?-1:1)*7),260,{arc:3,ease:eO})}));
   hap('tap');try{var s=project([0,TY,mid[2]]);QFX.sparks(s[0],s[1],12+r*6,1+r%2)}catch(e){}
   await Promise.all(C.map(function(c,i){return wait(i*18).then(function(){return move(c,[mid[0],TC+i*DT,mid[2]],faceDown(rnd(-2,2)),280,{arc:4})})}));
  }
  /* three cards are laid */
  await type('THE CARDS ARE LAID',100);
  for(var k=0;k<3&&!skipped;k++){
   var c=dealt[k],sp=slots[k],yaw=(k-1)*3+rnd(-1.5,1.5),to=[sp[0],TC,sp[2]];c.yaw=yaw;
   var mv1=move(c,to,faceDown(yaw),640,{arc:9,ease:eO});await wait(500);if(skipped)break;thud([to[0],TY,to[2]],14,1.4);S_flash(.18);await mv1;await wait(100);
  }
  /* the rest of the deck is pushed aside */
  if(!skipped)await Promise.all(C.filter(function(c){return dealt.indexOf(c)<0}).map(function(c,i){return move(c,[SIDE[0],TC+i*DT,SIDE[2]],faceDown(rnd(-4,4)),520,{arc:3})}));
  /* the reading */
  var arcOf=function(q){return QPX_ARC[q.attribute]||QPX_ARC.discipline};
  if(!skipped){await type('YOUR CARDS AWAIT',150);gutter=.2;await wait(330);gutter=1;ST.flash=.5}
  for(var n=0;n<3&&!skipped;n++){
   var c=dealt[n],q=qs[n],tier=q.tier||1,arc=arcOf(q),slot=slots[n],lifted=[curEye[0]+bas.f[0]*LD,curEye[1]+bas.f[1]*LD,curEye[2]+bas.f[2]*LD];
   var qBack=t3qmul(camQ,t3qax([0,1,0],180));
   c.amb=1.3;var ab=anim(700,function(t){c.amb=1.3+2.9*t});await move(c,lifted,qBack,700,{ease:eIO});c.lift=true;c.amb=4.2;if(skipped)break;
   c.sh=0;cap.innerHTML=qpxText('TURN THE CARD',{s:2,w:vw-40,lines:1});sc.dataset.await='flip';
   await flipByHand(c,lifted);sc.dataset.await='';cap.innerHTML='';if(skipped)break;
   ST.flash=tier>=3?1:tier===2?.7:.45;hap(tier>=3?'big':'double');try{QFX.burst(cardRect(c),tier)}catch(e){}
   title.innerHTML='<span style="color:#f6ecd0;filter:drop-shadow(0 0 10px '+arc.ac+') drop-shadow(0 0 3px '+arc.ac+')">'+qpxText(arc.name,{s:3,w:vw-20,lines:1})+'</span><span class="fs-tier">'+qpxText(QPX_TIER[tier],{s:1,w:200,lines:1})+'</span>';
   anim(420,function(t){title.style.opacity=t;title.style.transform='translateY('+(10-10*t)+'px)'},eO);
   await wait(tier>=3?1250:800);if(skipped)break;
   anim(300,function(t){title.style.opacity=1-t});c.sheen=0;
   c.lift=false;anim(560,function(t){c.amb=4.2-2.9*t});c.p=curEye.slice();c.p=[curEye[0]+bas.f[0]*LD,curEye[1]+bas.f[1]*LD,curEye[2]+bas.f[2]*LD];
   await move(c,[slot[0],TC,slot[2]],faceUp(c.yaw),560,{ease:eIO,arc:4});c.sh=.7;if(!skipped){thud([slot[0],TY,slot[2]],8,.6)}
  }
 }
 }catch(e){console.warn('[table]',e)}

 /* the reading ends (or was skipped): the scene stays as the Quest Board */
 skipped=true;tw.forEach(function(a){a.ms=0});if(awaitFlip)awaitFlip.finish();
 await new Promise(function(r){setTimeout(r,30)});skipped=false;tw.length=0;
 d.dealt=true;d.up=[true,true,true];try{localStorage.setItem(STORE,JSON.stringify(state))}catch(e){}
 sc.dataset.await='';cap.innerHTML='';title.innerHTML='';var skb=sc.querySelector('.fs-skip');if(skb)skb.style.display='none';var gt=sc.querySelector('.fs-gate');if(gt)gt.remove();
 el.classList.remove('fd-pre');cards.forEach(function(c){c.classList.add('is-up')});
 if(!restOnly){var e0=eyeB.slice(),t0b=tgtB.slice();await anim(800,function(t){eyeB=lerp3(e0,REST_EYE,t);tgtB=lerp3(t0b,REST_TGT,t)},eIO)}
 eyeB=REST_EYE.slice();tgtB=REST_TGT.slice();idleDrift=false;gutter=1;
 dealt.forEach(function(c,i){c.front=tFace[i];c.lift=false;c.amb=1.3;c.sheen=0;c.q=faceUp(c.yaw||0);c.p=[slots[i][0],TC,slots[i][2]];c.sh=.7});
 restMode=true;sc.classList.add('rest');
 var outcome=await new Promise(function(finishRest){
  var inspecting=-1,busy=false,seen={ids:[],done:[]},spinStop=null,pdown=null;
  var numerals=DH_NUMERALS;
  sc.insertAdjacentHTML('beforeend','<div class="fs-rest"><div class="rest-prog"></div><div class="rest-bar"><button type="button" class="rest-btn" data-a="ledger">'+qpxText('LEDGER',{s:2,w:90,lines:1})+'</button><button type="button" class="rest-btn" data-a="new">'+qpxText('NEW QUEST',{s:2,w:120,lines:1})+'</button><button type="button" class="rest-btn" data-a="again">'+qpxText('RESHUFFLE',{s:2,w:120,lines:1})+'</button></div></div><div class="fs-dock3" hidden></div><div class="fs-ledger" hidden></div><div class="fs-newq" hidden></div>');
  var prog=sc.querySelector('.rest-prog'),dock=sc.querySelector('.fs-dock3'),ledger=sc.querySelector('.fs-ledger'),newq=sc.querySelector('.fs-newq');
  var slotQuest=function(i){return dhCards().find(function(z){return z.slot===i})};
  var quad=function(c){var M=t3qmat(c.q,c.p,[1,1,1]);return [[-CW/2,-CH/2,0],[CW/2,-CH/2,0],[CW/2,CH/2,0],[-CW/2,CH/2,0]].map(function(a){return project([M[0]*a[0]+M[4]*a[1]+M[8]*a[2]+M[12],M[1]*a[0]+M[5]*a[1]+M[9]*a[2]+M[13],M[2]*a[0]+M[6]*a[1]+M[10]*a[2]+M[14]])})};
  var inQuad=function(P,x,y){var sgn=0;for(var i=0;i<4;i++){var a=P[i],b=P[(i+1)%4],cr=(b[0]-a[0])*(y-a[1])-(b[1]-a[1])*(x-a[0]);if(Math.abs(cr)<1e-6)continue;var sg=cr>0?1:-1;if(sgn&&sg!==sgn)return false;sgn=sg}return true};
  var hit=function(x,y){var best=-1,bd=1e9;dealt.forEach(function(c,i){if(inQuad(quad(c),x,y)){var dd=Math.hypot(c.p[0]-curEye[0],c.p[1]-curEye[1],c.p[2]-curEye[2]);if(dd<bd){bd=dd;best=i}}});return best};
  var qOf=function(i){return slotQuest(i)||qs[i]};
  var dockHtml=function(q){var done=isQuestDone(q),auto=!/^(self|none)/.test(q.verify||'self'),cost=dhRerollCost();
   var foot=done?'<span class="dh-state">Fulfilled</span>':auto?'<span class="dh-state dh-auto">Fulfils itself when you log it</span>':'<button class="dh-claim" type="button" aria-label="Hold to claim '+esc(q.name)+'" onpointerdown="dhHoldStart(\''+q.id+'\',this)" onpointerup="dhHoldEnd()" onpointerleave="dhHoldEnd()" onpointercancel="dhHoldEnd()" oncontextmenu="return false"><svg viewBox="0 0 44 44" aria-hidden="true"><circle class="fd-track" cx="22" cy="22" r="19"/><circle class="fd-prog" cx="22" cy="22" r="19"/></svg><span class="dh-claim-t">Hold</span></button>';
   var rr=done?'':'<button class="dh-reroll" type="button" onclick="dhSceneRedraw('+q.slot+')">Redraw · '+(cost?cost+' Sparks':'free')+'</button>';
   return foot+'<span class="dock-mid"><span class="dock-slot">'+qpxText(DH_SLOT_NAMES[q.slot],{s:1,w:110,lines:1})+'</span><span class="dh-xp">'+qpxText('+'+Math.max(0,Number(q.xp)||0)+' XP',{s:2,w:100,lines:1})+'</span></span>'+rr+'<button class="dock-x" type="button" onclick="dhSceneBack()" aria-label="Put the card back">&#10005;</button>'};
  var showDock=function(i){var q=qOf(i);dock.innerHTML=dockHtml(q);dock.hidden=false};
  var refreshProg=function(){var n=0,t=0;for(var i=0;i<3;i++){var q=qOf(i);if(q){t++;if(isQuestDone(q))n++}}
   var w=state.quests.filter(function(z){return z.cadence==='weekly'&&z.kind!=='nutrition'}),wd=w.filter(isQuestDone).length;
   var pips='';for(var i=0;i<3;i++){var q=qOf(i);pips+='<i class="'+(q&&isQuestDone(q)?'on':'')+'"></i>'}
   prog.innerHTML='<span class="rp-pips">'+pips+'</span>'+qpxText(n+' / '+t+' FULFILLED',{s:2,w:vw-40,lines:1})+(w.length?'<span class="rp-sub">'+qpxText('WEEKLY '+wd+' / '+w.length,{s:1,w:140,lines:1})+'</span>':'')};
  var retexture=async function(i,animate,burst){
   var q=qOf(i),cv=await t3face(q,numerals[i],isQuestDone(q)),tex=mkTex(cv,false),c=dealt[i],old=tFace[i];
   if(animate&&c.lift&&inspecting===i){await anim(520,function(t){var a=t*360;c.q=t3qmul(t3qmul(camQ,t3qax([0,1,0],a)),QFRONT);if(t>.25&&c.front!==tex)c.front=tex},eIO);c.q=t3qmul(camQ,QFRONT)}
   else c.front=tex;
   tFace[i]=tex;c.front=tex;if(old)gl.deleteTexture(old);
   if(burst){var tier=q.tier||1;try{QFX.claim(cardRect(c));hap('success');ST.flash=.8}catch(e){}}
  };
  var syncCards=async function(){
   if(busy||!alive)return;busy=true;
   try{for(var i=0;i<3;i++){var q=slotQuest(i);if(!q)continue;var dn=isQuestDone(q);
     if(seen.ids[i]===undefined){seen.ids[i]=q.id;seen.done[i]=dn;continue}
     if(seen.ids[i]!==q.id||seen.done[i]!==dn){var newly=dn&&!seen.done[i]&&seen.ids[i]===q.id;seen.ids[i]=q.id;seen.done[i]=dn;await retexture(i,true,newly);if(inspecting===i)showDock(i)}}
   refreshProg();if(!ledger.hidden)renderLedger()}catch(e){console.warn('[rest sync]',e)}
   busy=false};
  for(var i0=0;i0<3;i0++){var q0=slotQuest(i0);if(q0){seen.ids[i0]=q0.id;seen.done[i0]=isQuestDone(q0)}}
  refreshProg();
  /* inspecting a card: it is lifted to you, turns freely on any axis, and the dock shows its controls */
  var spinFree=function(c){
   var qv=QFRONT.slice(),q0=qv.slice(),drag=false,sx=0,sy=0,samples=[],settling=0;
   var apply=function(){c.q=t3qmul(camQ,qv);var nz=Math.abs(qrot(qv,[0,0,1])[2]);c.sheen=.08+.45*(1-nz);c.lift=true;c.amb=4.2};apply();
   var rotFor=function(dx,dy,k){var len=Math.hypot(dx,dy);if(len<.5)return [0,0,0,1];return t3qax([-dy/len,dx/len,0],len*k)};
   var down=function(e){if(settling||e.target.closest('button,.fs-dock3,.fs-ledger,.fs-newq,.fs-rest'))return;var P=quad(c);if(!inQuad(P,e.clientX,e.clientY))return;drag=true;dragging=true;sx=e.clientX;sy=e.clientY;q0=qv.slice();samples=[[performance.now(),0,0]];try{sc.setPointerCapture(e.pointerId)}catch(x){}e.preventDefault()};
   var mv=function(e){if(!drag)return;var dx=e.clientX-sx,dy=e.clientY-sy;qv=t3qmul(rotFor(dx,dy,.9),q0);apply();var now=performance.now();samples.push([now,dx,dy]);while(samples.length>2&&now-samples[0][0]>90)samples.shift()};
   var up=function(){if(!drag)return;drag=false;dragging=false;var n=samples.length,vx=0,vy=0;if(n>1){var dt=Math.max(16,samples[n-1][0]-samples[0][0]);vx=(samples[n-1][1]-samples[0][1])/dt*1000;vy=(samples[n-1][2]-samples[0][2])/dt*1000}
    var vl=Math.hypot(vx,vy),bq=vl>1?t3qax([-vy/vl,vx/vl,0],Math.min(45,vl*.04)):[0,0,0,1],front=qrot(t3qmul(bq,qv),[0,0,1])[2]>0,target=front?QFRONT:QBACK,from=qv.slice(),t1=performance.now();
    var step=function(){var t=Math.min(1,(performance.now()-t1)/520),c1=1.2,c3=c1+1,e=1+c3*Math.pow(t-1,3)+c1*Math.pow(t-1,2);qv=t3slerp(from,target,e);apply();if(t<1)settling=requestAnimationFrame(step);else{settling=0;qv=target.slice();apply()}};settling=requestAnimationFrame(step)};
   sc.addEventListener('pointerdown',down);sc.addEventListener('pointermove',mv);sc.addEventListener('pointerup',up);sc.addEventListener('pointercancel',up);
   return function(){cancelAnimationFrame(settling);sc.removeEventListener('pointerdown',down);sc.removeEventListener('pointermove',mv);sc.removeEventListener('pointerup',up);sc.removeEventListener('pointercancel',up)};
  };
  var inspect=async function(i){
   if(inspecting>=0||busy)return;busy=true;inspecting=i;var c=dealt[i];hap('tap');
   var LD2=LD*1.1,lifted=[curEye[0]+bas.f[0]*LD2,curEye[1]+bas.f[1]*LD2,curEye[2]+bas.f[2]*LD2];c.sh=0;c.sheen=.2;c.lift=false;
   var ab=anim(480,function(t){c.amb=1.3+2.9*t});await move(c,lifted,t3qmul(camQ,QFRONT),480,{ease:eIO});c.lift=true;c.amb=4.2;await ab;
   spinStop=spinFree(c);showDock(i);busy=false};
  var release=async function(){
   if(inspecting<0||busy)return;busy=true;var i=inspecting,c=dealt[i];if(spinStop){spinStop();spinStop=null}dock.hidden=true;
   c.lift=false;c.p=[curEye[0]+bas.f[0]*LD,curEye[1]+bas.f[1]*LD,curEye[2]+bas.f[2]*LD];c.q=c.q.slice();
   anim(480,function(t){c.amb=4.2-2.9*t});await move(c,[slots[i][0],TC,slots[i][2]],faceUp(c.yaw||0),500,{ease:eIO,arc:4});c.sh=.7;c.sheen=0;thud([slots[i][0],TY,slots[i][2]],6,.4);inspecting=-1;busy=false};
  /* the ledger: weekly missions, epics, your own quests and badges */
  var lgRow=function(q){var done=isQuestDone(q),locked=questLocked(q),xp=Math.max(0,Number(q.xp)||0);
   return '<div class="lg-row'+(done?' done':'')+(locked?' locked':'')+'"><button class="lg-chk" type="button" '+(locked?'disabled ':'')+'onclick="dhLedgerToggle(\''+q.id+'\')" aria-label="'+(done?'Undo ':'Complete ')+esc(q.name)+'">'+(done?'&#10003;':locked?'&#128274;':'')+'</button><div class="lg-copy"><strong>'+esc(q.name)+'</strong><small>'+esc(locked?('Unlocks at level '+stageLvl(q.stage)):(q.desc||''))+'</small></div><span class="lg-xp">+'+xp+'</span></div>'};
  var renderLedger=function(){
   var cu=state.quests.filter(function(q){return q.cadence==='daily'&&q.kind!=='nutrition'&&!q.hand&&questToday(q)}),we=state.quests.filter(function(q){return q.cadence==='weekly'&&q.kind!=='nutrition'}),nu=state.quests.filter(function(q){return q.kind==='nutrition'&&(q.cadence!=='daily'||questToday(q))}),ep=state.quests.filter(function(q){return q.cadence==='epic'}).sort(function(a,b){return(a.stage||0)-(b.stage||0)});
   var sec=function(t,list,empty){return '<div class="lg-sec">'+qpxText(t,{s:2,w:260,lines:1})+'</div>'+(list.length?list.map(lgRow).join(''):'<div class="lg-empty">'+esc(empty)+'</div>')};
   var b=state.badges||{},earned=BADGES.filter(function(x){return b[x.id]});
   ledger.innerHTML='<div class="lg-head"><div>'+qpxText('THE LEDGER',{s:3,w:260,lines:1})+'</div><button type="button" class="lg-close" onclick="dhSceneLedger(false)" aria-label="Close the ledger">&#10005;</button></div><div class="lg-body">'
    +sec('WEEKLY MISSIONS',we,'None this week.')+sec('YOUR OWN QUESTS',cu,'Forge one with New Quest.')+(nu.length?sec('NUTRITION',nu,''):'')+sec('EPIC MILESTONES',ep,'Your first epic awaits.')
    +'<div class="lg-sec">'+qpxText('BADGES '+earned.length+' / '+BADGES.length,{s:2,w:260,lines:1})+'</div><div class="lg-badges">'+(earned.length?earned.map(function(x){return '<span title="'+esc(x.desc)+'">'+esc(x.name)+'</span>'}).join(''):'<div class="lg-empty">None earned yet.</div>')+'</div></div>'};
  var openNew=function(on){newq.hidden=!on;if(on){newq.innerHTML='<div class="nq-card"><div class="nq-t">'+qpxText('NEW QUEST',{s:3,w:260,lines:1})+'</div><input id="nqName" maxlength="60" placeholder="A goal worth pursuing" aria-label="Quest name"><div class="nq-row"><select id="nqCad" aria-label="Cadence"><option value="daily">Daily</option><option value="weekly">Weekly</option></select><input id="nqXp" type="number" min="1" max="500" value="25" aria-label="XP"></div><div class="nq-row"><button type="button" class="rest-btn" onclick="dhSceneCreate()">'+qpxText('CREATE',{s:2,w:90,lines:1})+'</button><button type="button" class="rest-btn dim" onclick="dhSceneNew(false)">'+qpxText('CANCEL',{s:2,w:90,lines:1})+'</button></div></div>'}};
  var api={
   back:release,redraw:function(slot){if(dhReroll(slot)){}},
   ledger:function(on){if(on===undefined)on=ledger.hidden;if(on){renderLedger();ledger.hidden=false;release()}else ledger.hidden=true},
   open:function(on){openNew(on===undefined?true:on)},
   create:function(){var n=(document.getElementById('nqName').value||'').trim();if(!n){toast('Give your quest a name');return}
    state.quests.push({id:'custom_'+Date.now(),name:n,desc:'Custom quest created by you.',cadence:document.getElementById('nqCad').value,xp:Math.min(500,Math.max(1,Number(document.getElementById('nqXp').value)||25)),kind:'custom'});openNew(false);save();toast('Quest created');api.ledger(true)},
   toggle:function(id){var q=state.quests.find(function(z){return z.id===id});if(!q)return;qosToggle(id,!isQuestDone(q));setTimeout(function(){if(!ledger.hidden)renderLedger();refreshProg()},60)},
   again:function(){finishRest('replay')}
  };
  window.dhRest=api;
  sc.addEventListener('click',function(e){var b=e.target.closest('.rest-btn[data-a]');if(!b)return;var a=b.dataset.a;if(a==='ledger')api.ledger(true);else if(a==='new')api.open(true);else if(a==='again')api.again()});
  /* taps on the table */
  sc.addEventListener('pointerdown',function(e){if(e.target.closest('button,input,select,.fs-ledger,.fs-newq,.fs-dock3'))return;pdown={x:e.clientX,y:e.clientY,t:performance.now()}});
  sc.addEventListener('pointerup',function(e){if(!pdown)return;var d0=pdown;pdown=null;if(Math.hypot(e.clientX-d0.x,e.clientY-d0.y)>10||performance.now()-d0.t>450)return;
   if(!ledger.hidden||!newq.hidden)return;
   if(inspecting>=0){if(!inQuad(quad(dealt[inspecting]),e.clientX,e.clientY))release();return}
   var i=hit(e.clientX,e.clientY);if(i>=0)inspect(i)});
  var tick=setInterval(syncCards,450);
  var leave=setInterval(function(){if(!dhViewOn())finishRest('leave')},250);
  var embers=setInterval(function(){if(document.hidden)return;try{flames.forEach(function(f){var q=project(f);QFX.emit(q[0]+rnd(-6,6),q[1]-8,rnd(-14,14),-rnd(30,70),rnd(1.2,2.2),rnd(3,7),[1,.55+rnd(0,.2),.2])})}catch(e){}},700);
  api._stop=function(){clearInterval(tick);clearInterval(leave);clearInterval(embers);if(spinStop)spinStop()};
 });
 if(window.dhRest&&window.dhRest._stop)window.dhRest._stop();window.dhRest=null;
 await new Promise(function(r){var t1=performance.now(),dur=outcome==='replay'?1:450,f=function(){var t=Math.min(1,(performance.now()-t1)/dur);sc.style.opacity=1-t;if(t<1)requestAnimationFrame(f);else r()};f()});
 alive=false;cancelAnimationFrame(raf);try{gl.getExtension('WEBGL_lose_context').loseContext()}catch(e){}sc.remove();
 try{QFX.foilKick()}catch(e){}
 return outcome==='replay'?'replay':true;
 function S_flash(v){ST.flash=Math.max(ST.flash,v)}
}

function dhSceneBack(){if(window.dhRest)window.dhRest.back()}
function dhSceneRedraw(n){if(window.dhRest)window.dhRest.redraw(n)}
function dhSceneLedger(on){if(window.dhRest)window.dhRest.ledger(on)}
function dhSceneNew(on){if(window.dhRest)window.dhRest.open(on)}
function dhSceneCreate(){if(window.dhRest)window.dhRest.create()}
function dhLedgerToggle(id){if(window.dhRest)window.dhRest.toggle(id)}
