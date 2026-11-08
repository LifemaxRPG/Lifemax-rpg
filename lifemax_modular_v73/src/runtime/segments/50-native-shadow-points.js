/* Native economy check (beta), the shadow points (docs/specs/ECONOMY_SHADOW_PLAN.md 1.2): which page entry points are one
   economy action each, under which kind, and what each reads from outside that the transport records as its env. Read by
   50-native-shadow.js, which wraps them only while the owner's switch is on and the app says economy.shadow.v1. Nothing here
   runs on its own.
   A point is [kind, owner, name, reads]: owner() is the object the function lives on (window, or LifeMaxHost), name its
   property. reads lists the env parts beyond what every action records (its writeSave() answers, its confirm and prompt
   answers): 'plan' (the plan's rest days and whether today is done), 'health' (every Health Connect answer, through a
   recording proxy while the sync runs), 'dom:<ids>' (the input values the entry reads; an id ending in * is every element whose
   id starts so, and {0} stands for the entry's first argument: toggleQuest reads its quest's note field). One kind is one
   entry (WP6b: native runs each kind as one call). Not shadowed, on purpose: imports,
   restores, resets and the demo (REPLACES: they start a new base), dev boosts and pure reads (plan 1.2). The 3D table's
   dhRest.create is made while the table is open, so its save() is recorded as save.other. A class switch applies its quest
   pack through applyClassContent (quest.pack, WP6c); the boot's own load applies it inside boot.load. */
const LMA_SHADOW_POINTS=(function(){
 const W=()=>window, HOST=()=>(typeof LifeMaxHost==='object'&&LifeMaxHost?LifeMaxHost:null);
 const points=[
  ['log.record',W,'recordActivity',[]],
  ['log.delete',W,'deleteActivity',[]],
  ['log.remove',W,'removeAct',[]],
  ['fixed.undo',W,'undoCat',[]],
  ['voice.log',W,'lmvLogNow',[]],
  ['quest.toggle',W,'toggleQuest',['dom:qnote-{0}']],
  ['quest.create',W,'createQuest',['dom:newQuestName,newQuestCadence,newQuestXP']],
  ['quest.pack',W,'applyClassContent',[]],
  ['hand.ensure',W,'dhEnsure',['plan']],
  ['hand.reroll',W,'dhReroll',[]],
  ['hand.verify',W,'dhAutoVerify',['plan']],
  ['health.sync',W,'lmaHealthSync',['health']],
  ['director.award',HOST,'awardActivity',[]],
  ['director.quest',HOST,'completeQuest',[]],
  ['bonus.care',W,'ecoCare',[]],
  ['bonus.nutrition',W,'ecoNutritionCheck',[]],
  ['money.record',W,'recordMoneyTransaction',[]],
  ['money.remove',W,'removeMoneyTransaction',[]],
  ['fixed.plan',W,'planToggle',['plan']],
  ['fixed.catalog',W,'confirmAct',['dom:actDur,actRes,actNote']],
  ['coach.claim',W,'jcClaim',[]],
  ['coach.reflect',W,'journalSaveReflection',[]],
  ['coach.review',W,'journalSaveReview',[]],
  ['coach.catchup',W,'jcCatchUp',[]],
  ['spark.buy',W,'ecoBuy',[]],
  ['ascend',W,'ecoAscend',[]],
  ['rest.take',W,'ecoTakeRest',['plan']],
  ['rules.save',W,'saveRules',['dom:rule-*']],
  ['rules.reset',W,'resetRules',[]],
  ['rules.base',W,'saveBase',['dom:actBase']],
  ['rules.decay',W,'saveDecay',['dom:decayOn,decayGrace,decayRate']],
  ['decay.apply',W,'applyDecay',['plan']],
  ['save.other',W,'save',[]],
 ];
 /* whole-save replacements: their own saves are not actions; afterwards the shadow starts again from a new base (2.5) */
 const replaces=[[W,'applyBackup'],[W,'resetDemo'],[W,'deleteAll']];
 const PLAN_DAYS=60, DECAY_DAYS=400;
 /* the earliest day the decay pass can ask about (WP6d): each trained attribute's walk starts after its last training day and
    after the day it was last decayed (decayApplied holds day numbers; with decay off there is no walk), the shield tick after its
    mark; at most DECAY_DAYS back. from is a lower bound: a walk that starts later asks nothing before it. */
 function decayFrom(today,w){
  const t=dayNum(today),lows=[];
  if(getDecay().on)for(const a of ATTRS){const lt=lastTrained(a.id);if(lt)lows.push(Math.max(dayNum(lt)+1,(Number(w.applied&&w.applied[a.id])||0)+1))}
  if(w.tick!=null)lows.push(Number(w.tick)+1);
  return ecoDayKey(Math.max(Math.min(t,...lows.filter(Number.isFinite)),t-DECAY_DAYS));
 }
 /* plan facts the economy reads (2.3 env.plan): today done, and the rest days in [from, today]; today alone for a kind that does
    not read the plan itself, whose save may still deal and check the Daily Hand (WP6c). The decay passes look back as far as
    their walk can (decayFrom); the boot's passes its own starting point, taken before the pass moved it. */
 function plan(kind,todayOnly,walk){
  if(typeof planDay!=='function'||typeof getToday!=='function')return null;
  const today=getToday();
  let from=todayOnly?today:addDays(today,-PLAN_DAYS);
  if(!todayOnly&&(kind==='decay.apply'||kind==='boot.decay'))from=decayFrom(today,walk||{applied:state.decayApplied,tick:state.shieldTick});
  const rest=[];
  for(let d=from;d<=today;d=addDays(d,1)){const p=planDay(d);if(p&&p.kind==='rest')rest.push(d)}
  return {done:!!dayDone(today),rest,from};
 }
 /* the input values the entry reads, by element id (id* for every element whose id starts so); an element that is not there is
    left out; {0} is the entry's first argument */
 function dom(ids,args){
  const out={},put=el=>{if(el&&el.id)out[el.id]=el.type==='checkbox'?!!el.checked:String(el.value)};
  for(const raw of ids){
   const id=raw.replace('{0}',args&&args.length?String(args[0]):'');
   if(id.endsWith('*'))document.querySelectorAll('[id^="'+id.slice(0,-1)+'"]').forEach(put);
   else put(document.getElementById(id));
  }
  return out;
 }
 /* a HealthSync stand-in that answers as the real one and keeps every answer: each read's range and answer, the instant just
    after the status answer (N5), and with full (an app that says economy.shadow.day.v1) the part that threw and its message as
    lmaHealthSync shows it */
 const READS={readExercise:'exercise',readSteps:'steps',readSleep:'sleep',readWeight:'weight'};
 const said=e=>String((e&&e.message)||e).slice(0,300);
 function recordingHealth(real,h,full){
  const proxy={};
  proxy.status=async function(){
   let s;
   try{s=await real.status.apply(real,arguments)}catch(e){if(full){h.fail='status';h.error=said(e)}throw e}
   h.now=Date.now();h.status=JSON.parse(JSON.stringify(s));return s;
  };
  for(const [m,part] of Object.entries(READS))proxy[m]=async function(range){
   try{const a=await real[m].apply(real,arguments);(h[part]=h[part]||[]).push({from:range&&range.from,to:range&&range.to,answer:JSON.parse(JSON.stringify(a))});return a}
   catch(e){h.fail=part;if(full)h.error=said(e);throw e}
  };
  return proxy;
 }
 /* what a sync records before it asks anything (economy.shadow.day.v1): whether another sync still runs (lmaHealthSync then
    returns at once) */
 function healthStart(full){
  const h={};
  if(full&&typeof lmaHealthBusy!=='undefined'&&lmaHealthBusy)h.busy=true;
  return h;
 }
 return {points,replaces,plan,dom,recordingHealth,healthStart};
})();
