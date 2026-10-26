
/* ==========================================================================
   v79.2 · the box
   He used to hop onto the box with the two-frame leap and then sit up tall,
   so it only ever covered his feet. Now:
   - Every hop into or out of the box is its own jump: a crouch and wiggle,
     a push-off stretch, a tucked arc over the rim, a drop into the box with
     a little squash, and the box rocks when he lands.
   - Inside, he can sink below the rim. The front panel already draws in
     front of him; he is clipped at the box floor and walls, so how much of
     him shows is just how deep he sits.
   - Peek-a-boo: ducked down to the ear tips, rising until his eyes clear the
     rim, ducking again, popping up with a trill, batting a paw over the edge.
   Depths are measured from the art (ear tips and eyes above his feet) for the
   adult and every kitten stage, so "peeking" lands on his eyes at any age.
   ========================================================================== */
var PET_BOX={
  JUMP_T:0.66,      /* seconds for the hop in (plus a little per pixel) */
  OUT_T:0.56,
  APEX:20,          /* arc height above the higher end, in his pixels */
  OUT_APEX:15,
  SQUASH_T:0.17,    /* landing squash */
  WOB_T:0.8,        /* the box rocks after he lands in it */
  RIM_ROW:10        /* first row of the front panel in the box art */
};
/* [ear tips, top of eyes, bottom of eyes] above his feet; -1 = grown up, 0..5 = kitten stages */
var PET_BOX_FACE={
  sit: {'-1':[44,32,29],0:[29,18,14],1:[31,20,16],2:[35,23,20],3:[37,25,22],4:[39,27,24],5:[42,30,27]},
  look:{'-1':[46,34,30],0:[30,20,15],1:[32,22,17],2:[36,25,21],3:[38,27,23],4:[41,30,26],5:[43,32,28]},
  loaf:{'-1':[30,16,14],0:[21,9,6],1:[22,10,7],2:[25,12,10],3:[26,13,11],4:[27,14,12],5:[28,15,13]}
};
PET_OPT_CAT.boxplay='play';
var PET_BOX_FRONT_Z=9;   /* he waits and lands this much nearer the viewer than the box, so he is never half behind it */

function petBoxProp(){for(const pr of PET_PROPS)if(pr.k==='box')return pr;return null}
function petBoxFace(kind){const gi=petGrowIdx(petData());const t=PET_BOX_FACE[kind]||PET_BOX_FACE.sit;return t[gi]||t['-1']}
/* where the box is on screen: rim, floor and inside walls, in world pixels */
function petBoxGeom(rt){
  const pr=petBoxProp();if(!pr)return null;const im=petPropImg(rt,'box_front'),w=im?im.w:44,h=im?im.h:24,kp=petDepthScalePct(pr.z)/100,fy=petFloorY(pr.z);
  return {rimY:fy-h*kp+1+PET_BOX.RIM_ROW*kp,fy:fy+1,x0:pr.x-w*kp/2+2*kp,x1:pr.x+w*kp/2-2*kp,x:pr.x,k:kp};
}
/* how far to sink so that a feature `H` pixels above his feet shows `show` pixels over the rim */
function petBoxSinkFor(rt,spec){
  if(typeof spec==='number')return spec;
  const g=petBoxGeom(rt),c=rt.c,kc=petK(c);if(!g)return 0;
  const f=petBoxFace(spec.pose||'sit'),H=spec.at==='ears'?f[0]:spec.at==='eyetop'?f[1]:f[2];
  return Math.max(0,H-(spec.show||0)+(g.rimY-PET_SURF.box.y)/kc);
}
/* is he inside the box right now (so he gets clipped and can sink)? */
function petBoxIn(rt){
  const c=rt.c,s=c.step;
  if(s&&s.k==='hop'&&s.box){const u=Math.min(1,s.t/Math.max(0.01,s.T));return s.box==='in'?u>=0.4:u<0.5}
  return c.surf==='box';
}

/* ---------- the jump ---------- */
/* petStartStep calls this for every hop; it takes the ones into or out of the box */
function petBoxHopStart(rt,s){
  const c=rt.c;if(!PET_SURF.box)return false;
  const into=s.s==='box'&&c.surf!=='box',out=c.surf==='box'&&s.s!=='box';if(!into&&!out)return false;
  const sf=PET_SURF.box,kc=petK(c);
  s.box=into?'in':'out';s.x0=c.x;s.y0=c.y;s.z0=c.z;s.z1=into?sf.z:sf.z+PET_BOX_FRONT_Z;
  s.x1=into?Math.max(sf.x0,Math.min(sf.x1,s.x==null?(sf.x0+sf.x1)/2:s.x)):(s.x==null?c.x+(c.dir||1)*30:s.x);
  s.y1=petSurfY(s.s,s.z1);s.g0=s.y0;s.g1=s.y1;
  const dx=s.x1-s.x0;if(Math.abs(dx)>1)c.dir=dx>0?1:-1;
  s.T=(into?PET_BOX.JUMP_T:PET_BOX.OUT_T)+0.003*Math.abs(dx);
  s.h=(into?PET_BOX.APEX:PET_BOX.OUT_APEX)*kc;   /* the apex; turned into the arc constant below */
  /* the arc runs from where he is drawn to where he will be drawn, sink included, so the
     drop into the box is one continuous motion rather than a slide followed by a sink */
  s.sink0=c.boxSink||0;s.y0=c.y+s.sink0*kc;c.boxSink=0;
  s.sink1=into?petBoxSinkFor(rt,{pose:'sit',at:'ears',show:7}):0;
  s.yEnd=s.y1+s.sink1*kc;
  /* pick the arc so its peak is `apex` above the higher end, whatever the drop */
  {const A=s.h+Math.max(0,s.y0-s.yEnd),D=s.yEnd-s.y0,b=8*D+16*A;s.h=(b+Math.sqrt(Math.max(0,b*b-64*D*D)))/32}
  c.anim='leap';
  if(out){rt.boxWob=PET_BOX.WOB_T*0.5}
  return true;
}
function petBoxJumpTick(rt,s,dt){
  const c=rt.c;s.t+=dt;const u=Math.min(1,s.t/s.T);
  /* takeoff gathers for a moment; going in he is over the box early and drops in,
     coming out he rises first and then sails clear */
  const g=u<0.08?0:(u-0.08)/0.92,gx=s.box==='in'?1-(1-g)*(1-g):g*g;
  c.x=s.x0+(s.x1-s.x0)*gx;
  c.z=s.z0+(s.z1-s.z0)*gx;
  c.y=s.y0+(s.yEnd-s.y0)*g-4*s.h*g*(1-g);
  if(s.box==='in'){
    if(u<0.08){c.anim='crouch';c.lock=2}
    else if(u<0.4){c.anim='leap';c.lock=1}       /* push off, stretched */
    else if(u<0.74){c.anim='leap';c.lock=0}      /* tucked over the rim */
    else{c.anim=petAnimName(rt,'idle_sit');c.lock=0}   /* feet first into the box */
  }else{
    if(u<0.1){c.anim='crouch';c.lock=2}
    else if(u<0.55){c.anim='leap';c.lock=1}
    else{c.anim='leap';c.lock=0}
  }
  if(u>=1){
    c.x=s.x1;c.y=s.y1;c.surf=s.s;c.z=s.z1;
    c.lock=null;c.step=null;rt.boxSq=PET_BOX.SQUASH_T;c.boxSink=s.sink1;
    if(s.box==='in'){rt.boxWob=PET_BOX.WOB_T;haptic(5);
      const gm=petBoxGeom(rt);if(gm)for(let i=0;i<3;i++)petSpawn(rt,'sparkle',gm.x+petRand(-14,14),gm.rimY-2,petRand(-10,10),petRand(-16,-6),0.5)}
  }
}
function petBoxEase(kind,u){u=Math.max(0,Math.min(1,u));
  if(kind==='in')return u*u*u;
  if(kind==='inout')return u<0.5?4*u*u*u:1-Math.pow(-2*u+2,3)/2;
  if(kind==='back'){const k=1.9;return 1+(k+1)*Math.pow(u-1,3)+k*Math.pow(u-1,2)}   /* overshoots a little: a pop */
  return 1-Math.pow(1-u,3)}

/* ---------- sinking and rising inside ---------- */
/* an act with {sink:spec, sinkT, ease}: he moves to that depth while holding the pose */
function petBoxSinkTick(rt,s,dt){
  const c=rt.c;if(c.surf!=='box')return;
  if(s.sinkFrom==null){s.sinkFrom=c.boxSink||0;s.sinkTo=petBoxSinkFor(rt,s.sink);if(s.fx)try{petBrainVoice(rt,s.fx,{force:1})}catch(e){}}
  c.boxSink=s.sinkFrom+(s.sinkTo-s.sinkFrom)*petBoxEase(s.ease||'inout',s.t/Math.max(0.01,s.sinkT||0.4));
}
function petBoxTick(rt,dt){
  if(rt.boxSq>0)rt.boxSq=Math.max(0,rt.boxSq-dt);
  if(rt.boxWob>0)rt.boxWob=Math.max(0,rt.boxWob-dt);
  if(!petBoxIn(rt))rt.c.boxSink=0;
}
/* the box rocks: a damped sway, a pixel or so */
function petBoxWobble(rt){const w=rt&&rt.boxWob||0;if(w<=0)return 0;const a=w/PET_BOX.WOB_T;return Math.sin((PET_BOX.WOB_T-w)*26)*1.4*a*a}

/* ---------- drawing ---------- */
function petBoxDrawCat(rt,o){
  const c=rt.c,inBox=petBoxIn(rt),sq=rt.boxSq||0;
  if(!inBox&&sq<=0){petDrawCat(rt,o);return}
  const kc=petK(c);o.save();
  if(inBox){const g=petBoxGeom(rt);if(g){
    /* everything above the rim, and below it only what is inside the walls and above the floor */
    o.beginPath();o.rect(-4000,-4000,9000,g.rimY+4000);o.rect(g.x0+petBoxWobble(rt),g.rimY-0.5,g.x1-g.x0,g.fy-g.rimY);o.clip();
    o.translate(0,(c.boxSink||0)*kc)}}
  if(sq>0){const a=sq/PET_BOX.SQUASH_T,sx=1+0.09*a,sy=1-0.13*a;o.translate(c.x,c.y);o.scale(sx,sy);o.translate(-c.x,-c.y)}
  petDrawCat(rt,o);o.restore();
}

/* ---------- peek-a-boo ---------- */
function petBoxPose(anim,d,sink,sinkT,ease,extra){return petAct(anim,d,Object.assign({sink:sink,sinkT:sinkT,ease:ease},extra||{}))}
function petBoxPeekRounds(rt,n){
  const R=petRand,q=[];
  const hide={pose:'sit',at:'ears',show:10},peek={pose:'sit',at:'eyes',show:1},peekL={pose:'look',at:'eyes',show:1},half={pose:'sit',at:'eyes',show:8},paw={pose:'sit',at:'eyes',show:10};
  /* from the landing crouch: ears first, then the eyes come up over the edge */
  q.push(petBoxPose('idle_sit',R(0.7,1.3),hide,0.35,'out'));
  q.push(petBoxPose('idle_sit',R(1.4,2.4),peek,0.9,'inout'));
  for(let i=0;i<n;i++){
    const pick=Math.random();
    if(pick<0.32){            /* duck! ... and slowly back up */
      q.push(petBoxPose('idle_sit',R(0.6,1.6),hide,0.16,'in'));
      q.push(petBoxPose(Math.random()<0.5?'lookup':'idle_sit',R(1.2,2.4),Math.random()<0.5?peekL:peek,0.8,'inout'));
    }else if(pick<0.6){       /* pop up with a trill, then sink back to a peek */
      q.push(petBoxPose('idle_sit',0.35,hide,0.14,'in'));
      q.push(petBoxPose('idle_sit',R(0.9,1.4),0,0.26,'back',{fx:'trill'}));
      q.push(petBoxPose('idle_sit',R(1.0,2.0),peek,0.6,'inout'));
    }else if(pick<0.85){      /* a paw over the rim, batting at nothing */
      q.push(petBoxPose('idle_sit',0.4,half,0.3,'out'));
      q.push(petBoxPose('play',R(1.4,2.4),paw,0.2,'out'));
      q.push(petBoxPose('idle_sit',R(0.8,1.6),peek,0.5,'inout'));
    }else{                    /* look around from just under the edge */
      q.push(petBoxPose('lookup',R(1.4,2.6),peekL,0.5,'inout',{flip:1}));
      q.push(petBoxPose('idle_sit',R(0.6,1.2),peek,0.3,'inout',{flip:1}));
    }
  }
  return q;
}
function petBoxPlayPlan(rt,opt){
  opt=opt||{};const c=rt.c,sf=PET_SURF.box;
  const q=petRoute(c,'box',(sf.x0+sf.x1)/2);
  /* a moment of anticipation before the hop, if he is walking up to it */
  const hop=q.findIndex(function(s){return s.k==='hop'&&s.s==='box'});
  if(hop>=0)q.splice(hop,0,{k:'fn',f:function(){c.dir=PET_SURF.box.x0>c.x?1:-1}},petAct('crouch',petRand(0.45,0.7),{wiggle:1}));
  q.push.apply(q,petBoxPeekRounds(rt,opt.rounds||Math.round(petRand(2,4))));
  q.push({k:'fn',f:function(){petBrainEvent('play')}});
  if(opt.stay||Math.random()<0.35)q.push(petBoxPose('loaf',petRand(20,45),{pose:'loaf',at:'eyes',show:2},0.8,'inout'));   /* if it fits, he sits */
  else q.push.apply(q,petRoute({surf:'box',x:(sf.x0+sf.x1)/2,dir:c.dir},'floor',petRand(250,330)));
  return q;
}
/* offered to petThink while he is free to play */
function petBoxOptions(rt,add,R,r,pause,o){
  if(o.stage>=3||!PET_SURF.box)return;
  add('boxplay',0.9*o.play*(1+1.4*o.kit),function(){rt.lastPlayAt=petNow();return petBoxPlayPlan(rt,{}).concat([pause()])});
}
/* when he was going to sit in the box anyway (a favourite spot, a nap), the hop is the new one;
   now and then he peeks out before settling */
function petBoxSettle(rt,q){if(Math.random()<0.45)q.push.apply(q,petBoxPeekRounds(rt,1));return q}
/* test button: set him down just in front of the box so the jump starts right away */
function petDevBox(){if(!PET_RT||!PET_RT.ready)return;const rt=PET_RT,c=rt.c,sf=PET_SURF.box;rt.ball.on=false;rt.dangle=null;
  c.surf='floor';c.boxSink=0;c.x=sf.x0-30;c.z=sf.z+PET_BOX_FRONT_Z;c.y=petSurfY('floor',c.z);c.dir=1;
  petInterrupt(rt,petBoxPlayPlan(rt,{rounds:3}))}
petDevTest('Box peek-a-boo',petDevBox,'v79.2');
