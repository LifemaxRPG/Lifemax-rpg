
/* ==========================================================================
   v79 · more ways to play
   Until now a toy meant one thing: stalk, wiggle, pounce. Three more:
   - Floor swats: he flops onto his side and bats the toy with a front paw
     (new 'swat' art row, drawn for every coat and kitten stage). Now and then
     a swat sends it flying and he gets up to go after it.
   - Swat and chase: he sits, pats the toy away, watches it go, runs after it,
     and does it again. Balls and yarn roll furthest, so they get this most.
   - Belly-up juggle: on his back with the toy held up in all four paws,
     then a kick that tosses it away.
   The pounce is still here; these share the time with it.
   ========================================================================== */
var PET_PLAY={
  SWAT_ESCAPE_P:0.2,      /* chance a strike sends the toy out of reach */
  SWAT_HIT_FRAME:3,       /* the art frame where the paw lands */
  SWAT_HOOK_FRAME:5,      /* the frame where he hooks it back */
  PAT_AT:0.42,            /* seconds into a sitting pat before the toy goes */
  CHASE_ROUNDS:[2,4],
  HUG_SECS:[4,7]
};
PET_OPT_CAT.swat='play';PET_OPT_CAT.chase='play';PET_OPT_CAT.hug='play';

/* how far in front of his chest the toy sits while he is lying on his side */
function petPlayReach(rt){return (30*petSx(petData())+2)*petK(rt.c)}
function petPlayToy(rt,col){const b=rt.ball;if(!b.on||col&&b.col!==col){b.on=true;b.col=col||petLikesPickToy(rt,'chase');b.x=Math.max(60,Math.min(132,rt.c.x+(rt.c.dir||1)*petRand(46,80)));   /* clear of the walls */b.vx=0;b.vy=0;b.h=0;b.t=0;b.flip=false}return b}
/* walk (or run) to the toy and stop `gap` from it, facing it. Sitting pats come in from the
   wall side so the toy goes out into the room; lying swats need room for his body behind him. */
function petPlayGoTo(rt,run,gap){
  const c=rt.c,b=rt.ball,reach=gap||petPlayReach(rt);
  let side=gap?(b.x<96?-1:1):(b.x>=c.x?-1:1);
  const fits=function(sd){const x=b.x+sd*reach;return x>=PET_XMIN&&x<=PET_XMAX};
  if(!fits(side))side=-side;
  const q=petRoute(c,'floor',petClampX(b.x+side*reach));if(run)q.forEach(function(s){if(s.k==='walk')s.run=1});
  q.push({k:'fn',f:function(){c.dir=rt.ball.x>=c.x?1:-1}});return q;
}
/* a sitting paw reaches about this far */
function petPatGap(rt){return (13*petSx(petData())+3)*petK(rt.c)}
function petPlayThen(rt,steps){rt.c.plan.unshift.apply(rt.c.plan,steps)}

/* ---------- floor swats ---------- */
function petSwatPlan(rt,opt){
  opt=opt||{};const col=petPlayToy(rt,opt.col).col;
  const q=petPlayGoTo(rt,false);
  q.push(petAct('crouch',petRand(0.3,0.5)));
  q.push(petAct('swat',petRand(3.2,5.6),{swat:1,countPlay:1}));
  q.push({k:'fn',f:function(){
    const esc=rt.playEscaped,more=opt.more==null?1:opt.more;rt.playEscaped=false;
    /* knocked it away: get up and go after it, maybe for another round */
    if(esc&&more>0){petPlayThen(rt,[petAct('lookup',0.5)].concat(Math.random()<0.5?petSwatPlan(rt,{col:col,more:more-1,offered:opt.offered}):petChasePlan(rt,{col:col,rounds:1,offered:opt.offered})));return}
    const end=[];if(esc)end.push(petAct('lookup',0.8));end.push(petAct('stretch',2.6),petAct('idle_sit',1.4,{endBall:1}));
    if(!opt.offered)end.push({k:'fn',f:function(){petLikesPlayed(col,false)}});
    petPlayThen(rt,end);
  }});
  return q;
}

/* ---------- swat and chase ---------- */
function petChaseRounds(rt,n){
  const q=[];
  for(let i=0;i<n;i++){
    q.push({k:'fn',f:function(){petPlayThen(rt,petPlayGoTo(rt,Math.abs(rt.ball.x-rt.c.x)>40,petPatGap(rt)))}});
    q.push(petAct('play',0.85,{pat:1,countPlay:i===n-1}));
    q.push(petAct('lookup',petRand(0.5,0.9)));   /* watches it roll */
  }
  return q;
}
function petChasePlan(rt,opt){
  opt=opt||{};const col=petPlayToy(rt,opt.col).col;
  const n=opt.rounds||Math.round(petRand(PET_PLAY.CHASE_ROUNDS[0],PET_PLAY.CHASE_ROUNDS[1]));
  const q=petChaseRounds(rt,n);
  /* the last one he catches: flop and swat it, or pounce on it */
  q.push({k:'fn',f:function(){petPlayThen(rt,Math.random()<0.55?petSwatPlan(rt,{col:col,more:0,offered:opt.offered}):petPounceChain(rt,true,{col:col,rounds:1,solo:!opt.offered}))}});
  return q;
}

/* ---------- belly-up juggle ---------- */
function petHugPlan(rt,opt){
  opt=opt||{};const col=petPlayToy(rt,opt.col).col,c=rt.c;
  const q=petPlayGoTo(rt,false,4*petK(c));   /* right up to it: he scoops it up as he rolls over */
  q.push(petAct('crouch',0.35));
  q.push(petAct('roll',petRand(PET_PLAY.HUG_SECS[0],PET_PLAY.HUG_SECS[1]),{hug:1,countPlay:1}));
  q.push({k:'fn',f:function(){const b=rt.ball;rt.ballFront=false;b.vx=c.dir*petRand(40,80);b.vy=petRand(80,120)*(b.col==='spring'?1.3:1);haptic(6)}});   /* the kick-off */
  q.push(petAct('lookup',0.8));
  q.push(petAct('stretch',2.6));
  q.push(petAct('idle_sit',1.2,{endBall:1}));
  if(!opt.offered)q.push({k:'fn',f:function(){petLikesPlayed(col,false)}});
  return q;
}

/* ---------- per-frame: what the paws do to the toy ---------- */
function petPlayTick(rt,s,dt){
  const c=rt.c,b=rt.ball;if(!b.on)return;
  if(s.swat){
    rt.ballFront=true;
    const meta=rt.A.anims.swat,f=Math.floor(s.t*meta.fps)%meta.n,home=c.x+c.dir*petPlayReach(rt);
    if(s.lastF==null){s.lastF=f;if(Math.abs(b.x-home)<14){b.x=home;b.vx=0}}
    if(f!==s.lastF){s.lastF=f;
      if(f===PET_PLAY.SWAT_HIT_FRAME){
        if(!s.escaped&&s.t>1.2&&Math.random()<PET_PLAY.SWAT_ESCAPE_P){   /* too hard: off it goes */
          s.escaped=true;rt.playEscaped=true;petKnock(rt,c.dir);s.d=Math.min(s.d,s.t+0.5)}
        else if(!s.escaped){b.vx=c.dir*petRand(26,44);b.vy=petRand(28,58)*(b.col==='spring'?1.4:1);petSpawn(rt,'sparkle',b.x,PET_SURF.floor.y-10,c.dir*6,-14,0.6);haptic(4)}
      }
    }
    /* between strikes he hooks it back toward his chest */
    if(!s.escaped&&(f===PET_PLAY.SWAT_HOOK_FRAME||f<=1)){b.vx=0;b.x+=(home-b.x)*Math.min(1,7*dt)}
  }
  else if(s.pat){
    if(!s.patted&&s.t>=PET_PLAY.PAT_AT){s.patted=true;petKnock(rt,c.dir);b.vx*=petRand(0.55,0.85);b.vy*=0.5}
  }
  else if(s.hug){
    /* held up on his paws, jiggled as he bicycles */
    rt.ballFront=true;const sx=petSx(petData()),k=petK(c);
    b.vx=0;b.vy=0;b.flip=c.dir<0;
    b.x=c.x+c.dir*3*sx*k+Math.sin(s.t*7.3)*1.4;
    b.h=(8+(sx-0.6)*25)*k+Math.abs(Math.sin(s.t*5.1))*2.5;
  }
}
function petPlayStepEnd(rt,s){if(s.swat||s.hug)rt.ballFront=false}

/* ---------- choosing ---------- */
/* a toy you hand him: the old pounce, or one of the new games, by toy and mood */
function petToyGame(rt,id,rounds,love){
  const kit=petGrowIdx(petData())>=0,roller=id==='ball_a'||id==='ball_b'||id==='yarn',small=id==='mouse'||id==='feather';
  const w={pounce:1.3,swat:small?1.4:1.0,chase:roller?1.6:0.5,hug:id==='spring'||small?0.9:0.6};
  if(kit){w.swat*=1.3;w.hug*=1.3}
  let tot=0;for(const k in w)tot+=w[k];let x=Math.random()*tot,pick='pounce';for(const k in w){if((x-=w[k])<=0){pick=k;break}}
  const o={col:id,offered:true};
  if(pick==='swat')return petSwatPlan(rt,Object.assign(o,{more:love?2:1}));
  if(pick==='chase')return petChasePlan(rt,Object.assign(o,{rounds:Math.max(2,rounds+1)}));
  if(pick==='hug')return petHugPlan(rt,o);
  return petPounceChain(rt,false,{col:id,rounds:rounds});
}
/* playing on his own: offered to petThink beside the pounce, the kick and the roll */
function petPlayOptions(rt,add,R,r,pause,o){
  if(o.stage>=3||!(o.L>=1||o.kit>0.2))return;
  add('swat',1.0*o.play*(1+1.6*o.kit),function(){rt.lastPlayAt=petNow();return petSwatPlan(rt,{}).concat([pause()])});
  add('chase',0.8*o.play*(1+1.4*o.kit),function(){rt.lastPlayAt=petNow();return petChasePlan(rt,{}).concat([pause()])});
  add('hug',0.7*o.play*(1+1.8*o.kit),function(){rt.lastPlayAt=petNow();return petHugPlan(rt,{}).concat([pause()])});
}
/* dev buttons: petDevPlay2(1..3) */
function petDevPlay2(n){if(PET_RT&&PET_RT.ready){const rt=PET_RT;rt.dangle=null;rt.ball.on=false;petInterrupt(rt,[null,petSwatPlan,petChasePlan,petHugPlan][n](rt,{}))}}
petDevTest('Floor swat',function(){petDevPlay2(1)},'v79.1');
petDevTest('Swat & chase',function(){petDevPlay2(2)},'v79.1');
petDevTest('Belly-up juggle',function(){petDevPlay2(3)},'v79.1');
