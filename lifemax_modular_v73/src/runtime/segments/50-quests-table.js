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
 'c*=mix(1.,ao,.6);if(uFlat>.5)c+=uColor*vec3(1.,.8,.55)*.5;float dist=length(uEye-vP);c=mix(c,vec3(.03,.025,.06),1.-exp(-dist*uFogK));c=c/(c+vec3(.8));c=pow(c,vec3(1./2.2));o=vec4(c,1.);}';
var T3_CVS='#version 300 es\nlayout(location=0)in vec3 aP;layout(location=1)in vec3 aN;layout(location=2)in vec2 aUV;layout(location=3)in float aF;uniform mat4 uM,uVP;out vec3 vP;out vec3 vN;out vec2 vUV;out float vF;\n'+
 'void main(){vec4 w=uM*vec4(aP,1.);vP=w.xyz;vN=mat3(uM)*aN;vUV=aUV;vF=aF;gl_Position=uVP*w;}';
var T3_CFS='#version 300 es\nprecision highp float;in vec3 vP;in vec3 vN;in vec2 vUV;in float vF;out vec4 o;uniform sampler2D uFront,uBack;uniform vec3 uEye,uL1,uL2;uniform float uI1,uI2,uAmb,uSheen,uFogK,uFade;\n'+
 'vec3 lt(vec3 P,vec3 N,vec3 lp,float I){vec3 d=lp-P;float r2=dot(d,d);float att=I/(1.+r2*.0075);return vec3(1.,.7,.42)*max(dot(N,normalize(d)),0.)*att;}\n'+
 'void main(){vec3 N=normalize(vN);vec3 V=normalize(uEye-vP);vec3 alb=pow((vF<.5?texture(uFront,vUV):texture(uBack,vUV)).rgb,vec3(2.2));\n'+
 'vec3 c=alb*(vec3(.12,.13,.2)*uAmb+lt(vP,N,uL1,uI1)+lt(vP,N,uL2,uI2));\n'+
 'vec3 Lh=normalize(vec3(-.4,.7,.6));vec3 R=reflect(-Lh,N);c+=vec3(1.,.93,.78)*uSheen*pow(max(dot(R,V),0.),26.)*.3;\n'+
 'float dist=length(uEye-vP);c=mix(c,vec3(.03,.025,.06),(1.-exp(-dist*uFogK))*.8);c=c/(c+vec3(.9));c=pow(c,vec3(1./2.2));o=vec4(c,1.)*uFade;}';
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
async function t3face(q,numeral){
 var W=640,H=1120,cv=document.createElement('canvas');cv.width=W;cv.height=H;var c=cv.getContext('2d'),u=qartUrl&&qartUrl(q.attribute);
 var im=u?await t3img(u):null;
 if(im)c.drawImage(im,0,0,W,H);else{c.fillStyle='#1a1428';c.fillRect(0,0,W,H)}
 var pn=QART_PANEL[q.attribute]||[69,10.4],top=H*pn[0]/100,bot=H*(1-pn[1]/100),x0=W*.09,x1=W*.91,cx=W/2,arc=QPX_ARC[q.attribute]||QPX_ARC.discipline,tier=q.tier||1;
 var ph=bot-top,y=top+ph*.1;
 for(var i=0;i<3;i++){c.save();c.translate(x0+14+i*22,y+12);c.rotate(Math.PI/4);c.fillStyle=i<tier?'#b8761a':'rgba(70,40,10,.25)';c.fillRect(-7,-7,14,14);c.restore()}
 t3px(c,arc.name,cx,y,3,'#7a4a12');t3px(c,numeral,x1-10-qpxWidth(numeral)*3/2,y,3,'#7a4a12');
 var lines=qpxWrap(q.name,Math.floor((x1-x0)/5),2),ny=y+34;lines.forEach(function(l,k){t3px(c,l,cx,ny+k*48,5,'#2b1608')});
 c.fillStyle='#4a2e14';c.font='italic 27px Georgia,serif';c.textAlign='center';
 var words=String(q.desc||'').split(/\s+/),line='',dl=[],maxW=x1-x0-10;words.forEach(function(w){var t=line?line+' '+w:w;if(c.measureText(t).width>maxW&&line){dl.push(line);line=w}else line=t});if(line)dl.push(line);dl=dl.slice(0,2);
 var dy=ny+lines.length*48+30;dl.forEach(function(l,k){c.fillText(l,cx,dy+k*31)});
 return cv;
}
var T3_SHARED=null;
async function t3load(){
 if(T3_SHARED)return T3_SHARED;
 var tex={};for(var k in TABLE_TEX)tex[k]=await t3img('data:image/webp;base64,'+TABLE_TEX[k]);
 T3_SHARED={tex:tex,mesh:t3parseObj(TABLE_OBJ),cyl:t3cylinder(2.3,11,18)};return T3_SHARED;
}
/* ---- the scene ---- */
async function dhTable3D(el,cards,d,replay){
 var vw=innerWidth,vh=innerHeight,sh;
 try{sh=await t3load()}catch(e){console.warn('[table load]',e);return false}
 var sc=document.createElement('div');sc.id='fateScene';sc.className='fs-3d';
 sc.innerHTML='<canvas class="fs-gl"></canvas><div class="fs-title"></div><div class="fs-cap"></div><button class="fs-skip" type="button" aria-label="Skip the reading">'+qpxText('SKIP',{s:2,w:60,lines:1})+'</button>';
 document.body.appendChild(sc);
 var cvs=sc.querySelector('.fs-gl'),dprs=Math.min(1.75,window.devicePixelRatio||1);cvs.width=Math.round(vw*dprs);cvs.height=Math.round(vh*dprs);
 var gl=cvs.getContext('webgl2',{alpha:false,antialias:true,depth:true,powerPreference:'high-performance'});
 if(!gl){sc.remove();return false}
 var ST={flash:0,flare:1,shake:0},skipped=false,alive=true,raf=0,tw=[],waits=[],awaitFlip=null;
 var progs={},P=function(n,vs,fs){var p=gl.createProgram();[[gl.VERTEX_SHADER,vs],[gl.FRAGMENT_SHADER,fs]].forEach(function(a){var s=gl.createShader(a[0]);gl.shaderSource(s,a[1]);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw new Error(n+': '+gl.getShaderInfoLog(s));gl.attachShader(p,s)});gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw new Error(n+' link: '+gl.getProgramInfoLog(p));p._u={};progs[n]=p;return p};
 var UL=function(p,n){if(!(n in p._u))p._u[n]=gl.getUniformLocation(p,n);return p._u[n]};
 var pTab,pCard,pBlob,pFlame,pFog;
 try{pTab=P('tab',T3_VS,T3_FS);pCard=P('card',T3_CVS,T3_CFS);pBlob=P('blob',T3_BVS,T3_BFS);pFlame=P('flame',T3_FLVS,T3_FLFS);pFog=P('fog',CINE_FOG_V,CINE_FOG_F)}
 catch(e){console.warn('[table gl]',e);sc.remove();try{gl.getExtension('WEBGL_lose_context').loseContext()}catch(x){}return false}
 /* geometry */
 var vao=function(data,layout){var v=gl.createVertexArray();gl.bindVertexArray(v);var b=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,b);gl.bufferData(gl.ARRAY_BUFFER,data,gl.STATIC_DRAW);layout.forEach(function(l){gl.enableVertexAttribArray(l[0]);gl.vertexAttribPointer(l[0],l[1],gl.FLOAT,false,l[3],l[2])});return v};
 var tabV=vao(sh.mesh,[[0,3,0,44],[1,3,12,44],[2,2,24,44],[3,3,32,44]]),tabN=sh.mesh.length/11;
 var cylV=vao(sh.cyl,[[0,3,0,44],[1,3,12,44],[2,2,24,44],[3,3,32,44]]),cylN=sh.cyl.length/11;
 var cq=new Float32Array([-.5,-.5,.01,0,0,1,0,1,0, .5,-.5,.01,0,0,1,1,1,0, .5,.5,.01,0,0,1,1,0,0, -.5,-.5,.01,0,0,1,0,1,0, .5,.5,.01,0,0,1,1,0,0, -.5,.5,.01,0,0,1,0,0,0,
  .5,-.5,-.01,0,0,-1,0,1,1, -.5,-.5,-.01,0,0,-1,1,1,1, -.5,.5,-.01,0,0,-1,1,0,1, .5,-.5,-.01,0,0,-1,0,1,1, -.5,.5,-.01,0,0,-1,1,0,1, .5,.5,-.01,0,0,-1,0,0,1]);
 var cardV=vao(cq,[[0,3,0,36],[1,3,12,36],[2,2,24,36],[3,1,32,36]]);
 var bq=new Float32Array([-.5,0,-.5,0,1,0,0,0,0, .5,0,-.5,0,1,0,1,0,0, .5,0,.5,0,1,0,1,1,0, -.5,0,-.5,0,1,0,0,0,0, .5,0,.5,0,1,0,1,1,0, -.5,0,.5,0,1,0,0,1,0]);
 var blobV=vao(bq,[[0,3,0,36],[2,2,24,36]]);
 var fq=vao(new Float32Array([-1,-1,1,-1,-1,1,1,1]),[[0,2,0,0]]);
 var fogQ=(function(){var v=gl.createVertexArray();gl.bindVertexArray(v);var b=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,b);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,1,1]),gl.STATIC_DRAW);var l=gl.getAttribLocation(pFog,'p');gl.enableVertexAttribArray(l);gl.vertexAttribPointer(l,2,gl.FLOAT,false,0,0);return v})();
 /* textures */
 var mkTex=function(src,repeat){var t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,src);gl.generateMipmap(gl.TEXTURE_2D);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR_MIPMAP_LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);var w=repeat?gl.REPEAT:gl.CLAMP_TO_EDGE;gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,w);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,w);var ex=gl.getExtension('EXT_texture_filter_anisotropic');if(ex)gl.texParameterf(gl.TEXTURE_2D,ex.TEXTURE_MAX_ANISOTROPY_EXT,4);return t};
 var tBase=mkTex(sh.tex.base,true),tNorm=mkTex(sh.tex.normal,true),tOrm=mkTex(sh.tex.orm,true);
 var backUrl=qartUrl('back'),backImg=backUrl?await t3img(backUrl):null,tBack=mkTex(backImg||sh.tex.base,false);
 var qs=[0,1,2].map(function(i){return dhCards().find(function(z){return z.slot===i})||{tier:1,attribute:'discipline',name:'Quest',desc:''}});
 var faces=await Promise.all(qs.map(function(q,i){return t3face(q,DH_NUMERALS[i])})),tFace=faces.map(function(c){return mkTex(c,false)});
 /* the cards */
 var CW=11.2,CH=CW*1659/948,TY=72.05,rnd=function(a,b){return a+Math.random()*(b-a)};
 var faceDown=function(yaw){return t3qmul(t3qax([0,1,0],yaw+180),t3qax([1,0,0],90))},faceUp=function(yaw){return t3qmul(t3qax([0,1,0],yaw),t3qax([1,0,0],-90))};
 var C=[];for(var i=0;i<10;i++)C.push({p:[0,300,0],q:faceDown(0),front:null,sh:0,fade:1,yaw:0});
 var dealt=[C[9],C[8],C[7]];dealt.forEach(function(c,i){c.front=tFace[i]});
 /* camera */
 var EYE=[0,138,68],TGT=[0,72,-2],FOV=56*Math.PI/180,asp=cvs.width/cvs.height,proj=t3persp(FOV,asp,2,600),bas=t3basis(EYE,TGT),camQ=t3qfromBasis(bas.r,bas.u,bas.f);
 var curEye=EYE.slice(),viewM=t3view(EYE,bas),vp=t3mul(proj,viewM);
 var project=function(w){var m=vp,x=m[0]*w[0]+m[4]*w[1]+m[8]*w[2]+m[12],y=m[1]*w[0]+m[5]*w[1]+m[9]*w[2]+m[13],ww=m[3]*w[0]+m[7]*w[1]+m[11]*w[2]+m[15];return [(x/ww*.5+.5)*vw,(1-(y/ww*.5+.5))*vh]};
 var cardRect=function(c){var M=t3qmat(c.q,c.p,[CW,CH,1]),pts=[[-.5,-.5,0],[.5,-.5,0],[.5,.5,0],[-.5,.5,0]].map(function(a){return project([M[0]*a[0]+M[4]*a[1]+M[8]*a[2]+M[12],M[1]*a[0]+M[5]*a[1]+M[9]*a[2]+M[13],M[2]*a[0]+M[6]*a[1]+M[10]*a[2]+M[14]])}),xs=pts.map(function(p){return p[0]}),ys=pts.map(function(p){return p[1]}),l=Math.min.apply(0,xs),r=Math.max.apply(0,xs),t=Math.min.apply(0,ys),b=Math.max.apply(0,ys);return {left:l,right:r,top:t,bottom:b,width:r-l,height:b-t}};
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
  gl.uniformMatrix4fv(UL(pCard,'uVP'),false,vp);gl.uniform3fv(UL(pCard,'uEye'),curEye);gl.uniform3fv(UL(pCard,'uL1'),flames[0]);gl.uniform3fv(UL(pCard,'uL2'),flames[1]);gl.uniform1f(UL(pCard,'uI1'),L1);gl.uniform1f(UL(pCard,'uI2'),L2);gl.uniform1f(UL(pCard,'uFogK'),.0042);gl.uniform1i(UL(pCard,'uFront'),3);gl.uniform1i(UL(pCard,'uBack'),4);
  gl.activeTexture(gl.TEXTURE4);gl.bindTexture(gl.TEXTURE_2D,tBack);
  C.forEach(function(c){if(c.fade<=0)return;gl.uniformMatrix4fv(UL(pCard,'uM'),false,t3qmat(c.q,c.p,[CW,CH,1]));gl.uniform1f(UL(pCard,'uAmb'),c.amb||1.3);gl.uniform1f(UL(pCard,'uSheen'),c.sheen||0);gl.uniform1f(UL(pCard,'uFade'),c.fade);gl.activeTexture(gl.TEXTURE3);gl.bindTexture(gl.TEXTURE_2D,c.front||tBack);gl.drawArrays(gl.TRIANGLES,0,12)})};
 var L1=3,L2=3,gutter=1;
 var render=function(){
  var t=(performance.now()-t0)/1000;C.forEach(function(c){if(c.lift)c.p=[curEye[0]+bas.f[0]*LD,curEye[1]+bas.f[1]*LD,curEye[2]+bas.f[2]*LD]});ST.flash*=.9;ST.shake*=.9;
  L1=gutter*(5.2+.8*Math.sin(t*11)+.5*Math.sin(t*23+1))*(1+ST.flash*.6);L2=gutter*(5.2+.8*Math.sin(t*9+2)+.5*Math.sin(t*19))*(1+ST.flash*.6);
  curEye=[EYE[0]+rnd(-1,1)*ST.shake*.5+Math.sin(t*.4)*.8,EYE[1]+rnd(-1,1)*ST.shake*.5,EYE[2]+Math.cos(t*.3)*.6];viewM=t3view(curEye,bas);vp=t3mul(proj,viewM);
  gl.viewport(0,0,cvs.width,cvs.height);gl.clearColor(.01,.008,.03,1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
  gl.disable(gl.DEPTH_TEST);gl.disable(gl.BLEND);gl.useProgram(pFog);gl.bindVertexArray(fogQ);
  var c1=project(flames[0]),c2=project(flames[1]);
  gl.uniform1f(UL(pFog,'uT'),t);gl.uniform1f(UL(pFog,'uDim'),1);gl.uniform1f(UL(pFog,'uFlare'),ST.flare*gutter*.55);gl.uniform1f(UL(pFog,'uFlash'),ST.flash);gl.uniform2f(UL(pFog,'uRes'),cvs.width,cvs.height);gl.uniform2f(UL(pFog,'uC1'),c1[0]/vw,c1[1]/vh);gl.uniform2f(UL(pFog,'uC2'),c2[0]/vw,c2[1]/vh);gl.drawArrays(gl.TRIANGLE_STRIP,0,4);
  gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);
  /* table and candles */
  gl.useProgram(pTab);gl.uniformMatrix4fv(UL(pTab,'uVP'),false,vp);gl.uniform3fv(UL(pTab,'uEye'),curEye);gl.uniform3fv(UL(pTab,'uL1'),flames[0]);gl.uniform3fv(UL(pTab,'uL2'),flames[1]);gl.uniform1f(UL(pTab,'uI1'),L1);gl.uniform1f(UL(pTab,'uI2'),L2);gl.uniform1f(UL(pTab,'uNormY'),-1);gl.uniform1f(UL(pTab,'uAmb'),.55);gl.uniform1f(UL(pTab,'uFogK'),.0042);
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
 var loop=function(){if(!alive)return;var now=performance.now();for(var i=tw.length-1;i>=0;i--){var a=tw[i],p=a.ms?Math.min(1,(now-a.t0)/a.ms):1;a.fn(a.ease(p));if(p>=1){tw.splice(i,1);a.res()}}render();raf=requestAnimationFrame(loop)};
 var skip=function(){if(skipped)return;skipped=true;tw.forEach(function(a){a.ms=0});waits.forEach(function(w){clearTimeout(w[0]);w[1]()});if(awaitFlip)awaitFlip.finish()};
 sc.querySelector('.fs-skip').addEventListener('click',skip);
 /* the table pose of each dealt card and deck */
 var LD=CH/(.74*2*Math.tan(FOV/2)),DECK=[22,0,12],slots=[[-12,0,6],[0,0,6],[12,0,6]];
 /* turning a lifted card with a finger (real 3D rotation about its vertical axis) */
 var flipByHand=function(c,lifted){return new Promise(function(res){
  var a=180,rx=0,drag=false,sx=0,sy=0,a0=180,samples=[],seen=false,settling=0,done=false,hint=document.createElement('span');hint.className='fs-hint';hint.innerHTML='<i></i>';sc.appendChild(hint);
  var idle=setTimeout(function(){hint.classList.add('on')},2600),lp=lifted.slice();
  var apply=function(){c.q=t3qmul(t3qmul(camQ,t3qax([0,1,0],a)),t3qax([1,0,0],rx));c.sheen=.08+.4*Math.abs(Math.sin(a*Math.PI/180));c.lift=true;c.amb=4.2};
  apply();
  var finish=function(){if(done)return;done=true;clearTimeout(idle);cancelAnimationFrame(settling);sc.removeEventListener('pointerdown',down);sc.removeEventListener('pointermove',mv);sc.removeEventListener('pointerup',up);sc.removeEventListener('pointercancel',up);hint.remove();a=0;rx=0;apply();awaitFlip=null;res()};
  var front=function(){return Math.cos(a*Math.PI/180)>0};
  var down=function(e){if(done||settling||e.target.closest('.fs-skip'))return;drag=true;sx=e.clientX;sy=e.clientY;a0=a;samples=[[performance.now(),a]];hint.classList.remove('on');clearTimeout(idle);try{sc.setPointerCapture(e.pointerId)}catch(x){}e.preventDefault()};
  var mv=function(e){if(!drag)return;a=a0+(e.clientX-sx)*.9;rx=Math.max(-26,Math.min(26,(e.clientY-sy)*-.12));apply();var now=performance.now();samples.push([now,a]);while(samples.length>2&&now-samples[0][0]>90)samples.shift();if(front()&&!seen){seen=true;ST.flash=Math.max(ST.flash,.2);hap('tap')}};
  var up=function(){if(!drag)return;drag=false;var n=samples.length,v=n>1?(samples[n-1][1]-samples[0][1])/Math.max(16,samples[n-1][0]-samples[0][0])*1000:0,boost=Math.max(-45,Math.min(45,v*.04)),target=Math.round((a+boost)/180)*180,from=a,fromRx=rx,t1=performance.now(),dur=520;
   var step=function(){var t=Math.min(1,(performance.now()-t1)/dur),c1=1.4,c3=c1+1,e=1+c3*Math.pow(t-1,3)+c1*Math.pow(t-1,2);a=from+(target-from)*e;rx=fromRx*(1-t);apply();
    if(t<1)settling=requestAnimationFrame(step);else{settling=0;a=target;rx=0;apply();if(Math.round(target/180)%2===0)finish();else{seen=false;idle=setTimeout(function(){hint.classList.add('on')},2200)}}};settling=requestAnimationFrame(step)};
  sc.addEventListener('pointerdown',down);sc.addEventListener('pointermove',mv);sc.addEventListener('pointerup',up);sc.addEventListener('pointercancel',up);
  awaitFlip={finish:finish};
 })};
 try{
  loop();
  await anim(700,function(t){sc.style.opacity=t},eO);
  C.forEach(function(c){c.p=[DECK[0],400,DECK[2]]});
  await type('THE TABLE IS SET',200);
  /* the deck drops onto the wood */
  var drops=[];
  for(var i=0;i<10&&!skipped;i++){(function(i){var c=C[i],yaw=rnd(-5,5),x=DECK[0]+rnd(-.6,.6),z=DECK[2]+rnd(-.6,.6),from=[x+rnd(-8,8),TY+70+i*9,z+rnd(-8,8)],to=[x,TY+i*.16,z],q0=faceDown(rnd(-40,40)),q1=faceDown(yaw);c.yaw=yaw;
   drops.push(wait(i*60).then(function(){if(skipped){c.p=to;c.q=q1;c.sh=.7;return}return anim(520,function(t){c.p=lerp3(from,to,t);c.q=t3slerp(q0,q1,t);c.sh=Math.min(.8,t*1.2)},eBounce)}))})(i)}
  await Promise.all(drops);if(!skipped)thud([DECK[0],TY,DECK[2]],22,2);
  /* it begins to move: the deck slides to the middle of the table */
  var mid=[0,TY,-3];
  await Promise.all(C.map(function(c,i){return move(c,[mid[0],TY+i*.16,mid[2]],faceDown(rnd(-3,3)),600)}));
  await type('THE DECK IS SHUFFLED',120);
  for(var r=0;r<2&&!skipped;r++){
   await Promise.all(C.map(function(c,i){var left=i%2===0;return move(c,[mid[0]+(left?-9:9),TY+(i>>1)*.16+.3,mid[2]],faceDown((left?-1:1)*7),260,{arc:3,ease:eO})}));
   hap('tap');try{var s=project([0,TY,mid[2]]);QFX.sparks(s[0],s[1],12+r*6,1+r%2)}catch(e){}
   await Promise.all(C.map(function(c,i){return wait(i*18).then(function(){return move(c,[mid[0],TY+i*.16,mid[2]],faceDown(rnd(-2,2)),280,{arc:4})})}));
  }
  /* three cards are laid */
  await type('THE CARDS ARE LAID',100);
  for(var k=0;k<3&&!skipped;k++){
   var c=dealt[k],sp=slots[k],yaw=(k-1)*3+rnd(-1.5,1.5),to=[sp[0],TY+.05,sp[2]];c.yaw=yaw;
   var mv1=move(c,to,faceDown(yaw),640,{arc:9,ease:eO});await wait(500);if(skipped)break;thud([to[0],TY,to[2]],14,1.4);S_flash(.18);await mv1;await wait(100);
  }
  /* the rest of the deck is pushed aside */
  if(!skipped)await Promise.all(C.filter(function(c){return dealt.indexOf(c)<0}).map(function(c,i){return move(c,[DECK[0]+1.4,TY+i*.16,DECK[2]],faceDown(rnd(-4,4)),450)}));
  /* the reading */
  var arcOf=function(q){return QPX_ARC[q.attribute]||QPX_ARC.discipline};
  if(!skipped){await type('YOUR CARDS AWAIT',150);gutter=.2;await wait(330);gutter=1;ST.flash=.5}
  for(var n=0;n<3&&!skipped;n++){
   var c=dealt[n],q=qs[n],tier=q.tier||1,arc=arcOf(q),slot=slots[n],lifted=[EYE[0]+bas.f[0]*LD,EYE[1]+bas.f[1]*LD,EYE[2]+bas.f[2]*LD];
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
   await move(c,[slot[0],TY+.05,slot[2]],faceUp(c.yaw),560,{ease:eIO,arc:4});c.sh=.7;if(!skipped){thud([slot[0],TY,slot[2]],8,.6)}
  }
 }catch(e){console.warn('[table]',e)}
 /* hand-off: the real cards are already face-up behind the fade */
 skipped=true;tw.forEach(function(a){a.ms=0});if(awaitFlip)awaitFlip.finish();
 d.up=[true,true,true];sc.dataset.await='';cap.innerHTML='';title.innerHTML='';sc.querySelector('.fs-skip').style.display='none';
 el.classList.remove('fd-pre');cards.forEach(function(c){c.classList.add('is-up')});
 dealt.forEach(function(c,i){c.front=tFace[i];c.q=faceUp(c.yaw||0);c.p=[slots[i][0],TY+.05,slots[i][2]];c.sh=.7});
 await new Promise(function(r){var t1=performance.now(),f=function(){var t=Math.min(1,(performance.now()-t1)/700);sc.style.opacity=1-t;if(t<1)requestAnimationFrame(f);else r()};f()});
 alive=false;cancelAnimationFrame(raf);try{gl.getExtension('WEBGL_lose_context').loseContext()}catch(e){}sc.remove();
 try{QFX.foilKick()}catch(e){}
 return true;
 function S_flash(v){ST.flash=Math.max(ST.flash,v)}
}
