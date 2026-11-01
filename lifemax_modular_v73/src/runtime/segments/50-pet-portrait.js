/* ---------- The cat's portrait: one still, idle frame for the native Overview's cat card (plan OVERVIEW_BODY §4 phase 3).
   The pet room stays web (ADR 0003). The card cannot show the live room, so the page hands it a picture of the cat
   sitting, drawn by the room's own petDrawCat (17-runtime.js, wrapped by 32-pet-dna): the same sheets, kitten stage,
   genetic coat, body shape (37-pet-morph / 38-pet-rig) and HD face (50-pet-hd). No drawing rule is copied here.
   What is fixed for a portrait, so it is the same picture every time for the same cat:
   - the idle sit the room uses for this cat right now (petAnimName: a weak cat sits the weak way), first frame, facing right;
   - eyes open, a neutral face, ears and tail at rest (no mood, no blink);
   - feet on the floor at 100% scale, with the room's own ground shadow; a transparent background, no time-of-day tint.
   The picture is a PNG data URL, PET_PORTRAIT.W x PET_PORTRAIT.H room pixels drawn at PET_PORTRAIT.S canvas pixels each
   (2: one canvas pixel per HD pixel), cached by what decides its look (coat key, which carries the colour genes and eyes;
   growth stage; pose; HD on or off; the body-shape genes), so a projection every 15 s costs a map lookup.
   While the room's art for this cat is still loading the portrait is not drawn (null), so a half-loaded picture
   (say the adult art standing in for a kitten's) is never cached; petPortrait(p, onReady) calls onReady once it can. */
var PET_PORTRAIT={W:96,H:64,S:2,cache:new Map(),waits:{}};
/* where the cat stands in the portrait, in room pixels: centred, feet 8 px above the bottom so the shadow fits */
function petPortraitFeet(){return {x:PET_PORTRAIT.W/2,y:PET_PORTRAIT.H-8}}
/* which runtime holds the shared art (sheets, kitten stages, HD sheets): the first cat's, also for the second cat */
function petPortraitRt(){const P=typeof PET_PAIR!=='undefined'?PET_PAIR:null;return (P&&P.rt1)||PET_RT}
/* which cat slot p is (petAs: 0 = state.pet, 1 = state.pet2), or -1 */
function petPortraitSlot(p){return p&&p===state.pet?0:p&&p===state.pet2?1:-1}
/* runs fn as cat p: petData() then answers p, so the room's code draws this cat (dataOnly: the shared runtime stays) */
function petPortraitAs(p,fn){return petAs(petPortraitSlot(p),fn,[],true)}
/* the pose the room would sit this cat in now */
function petPortraitAnim(){return petAnimName(PET_RT,'idle_sit')}
/* the body-shape genes as the rig would draw them today (a kitten shows them grown in part), rounded as the rig rounds */
function petPortraitMorphKey(p){
  try{const ph=petPheno(p);if(!ph||!ph.morph)return 'n';const gi=petGrowIdx(p),m=petRigGrown(ph.morph,gi<0?1:petGrowth(p));
    return petRigNeutral(m)?'n':[m.em,m.ew,m.tl,m.lg,m.bl,m.wk].map(petRigQ).join(',')+(m.bob?'b':'')}catch(e){return 'n'}
}
function petPortraitKey(p){return [p.coat,petGrowIdx(p),petPortraitAnim(),petHdOn()?'hd':'lo',petPortraitMorphKey(p)].join('|')}
/* is everything the room would use for this frame loaded? Asking starts the loads, the way the room's own draw does */
function petPortraitReady(rt,p){
  if(!rt.sheets[p.coat]){if(typeof petDnaEnsureSheets==='function')petDnaEnsureSheets(rt,p.coat);return false}
  const gi=petGrowIdx(p);
  if(gi>=0&&!(rt.gs&&rt.gs[gi+'|'+p.coat])){petLoadStage(rt,gi);return false}
  const kind=gi<0?'adult':'st'+gi,K=petHdOn()&&PET_HD.err<=6?petHdJson().kinds[kind]:null;
  if(K&&K.anims.indexOf(petPortraitAnim())>=0&&!petHdSheet(rt,p.coat,kind))return false;
  return true;
}
/* the portrait on a fresh canvas, s canvas pixels per room pixel (the room itself draws at PET_OS). Returns null while loading. */
function petPortraitCanvas(p,s){
  const rt=petPortraitRt();if(!p||!p.alive||petPortraitSlot(p)<0||!rt||!rt.ready)return null;
  return petPortraitAs(p,function(){
    if(!petPortraitReady(rt,p))return null;
    const ft=petPortraitFeet(),cv=document.createElement('canvas');cv.width=PET_PORTRAIT.W*s;cv.height=PET_PORTRAIT.H*s;
    const o=cv.getContext('2d');o.setTransform(s,0,0,s,0,0);o.imageSmoothingEnabled=false;
    /* a stand-in runtime over the real one: it shares the art (and the caches the draw fills, such as rt.hd) but has
       its own cat, clock, eyes and mood, so drawing the portrait never moves the cat in the room */
    rt.hd=rt.hd||{};rt.gs=rt.gs||{};
    const pr=Object.create(rt);
    pr.c={x:ft.x,y:ft.y,z:PET_DEPTH.ART_Z,dir:1,surf:'floor',anim:petPortraitAnim(),t:0,plan:[],step:null,hop:null,lock:null,wx:0};
    pr.t=0;pr.eye={next:0,at:-1};pr.br=null;pr.hdMood={expr:'neutral',until:Infinity};pr.hdSeed=1;
    const prev=PET_RT;PET_RT=pr;
    try{petDrawCat(pr,o)}finally{PET_RT=prev}
    return cv;
  });
}
/* the portrait of state.pet or state.pet2 as a PNG data URL, or null (no cat, a cat that has passed, or its art still loading).
   onReady (optional) is called once, when a portrait that was not ready can be drawn. */
function petPortrait(p,onReady){
  if(!p||!p.alive||petPortraitSlot(p)<0)return null;
  const rt=petPortraitRt();
  if(!rt||!rt.ready){if(typeof petInit==='function')petInit().then(function(){petPortraitWait(p,onReady)});return null}
  const key=petPortraitAs(p,function(){return petPortraitKey(p)}),hit=PET_PORTRAIT.cache.get(key);
  if(hit)return hit;
  const cv=petPortraitCanvas(p,PET_PORTRAIT.S);
  if(!cv){petPortraitWait(p,onReady);return null}
  const url=cv.toDataURL('image/png');
  if(PET_PORTRAIT.cache.size>=4)PET_PORTRAIT.cache.delete(PET_PORTRAIT.cache.keys().next().value);
  PET_PORTRAIT.cache.set(key,url);
  return url;
}
/* polls (at most 30 times, 400 ms apart) until the art has loaded, then tells the caller once */
function petPortraitWait(p,onReady){
  if(typeof onReady!=='function')return;
  const slot=petPortraitSlot(p);if(PET_PORTRAIT.waits[slot])return;
  let n=0;
  const look=function(){
    const q=slot===1?state.pet2:state.pet,rt=petPortraitRt();
    if(!q||!q.alive||n++>30){delete PET_PORTRAIT.waits[slot];return}
    if(rt&&rt.ready&&petPortraitAs(q,function(){return petPortraitReady(rt,q)})){delete PET_PORTRAIT.waits[slot];try{onReady()}catch(e){console.warn('[pet portrait]',e)}return}
    PET_PORTRAIT.waits[slot]=setTimeout(look,400);
  };
  PET_PORTRAIT.waits[slot]=setTimeout(look,400);
}
