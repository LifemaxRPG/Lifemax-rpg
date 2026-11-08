/* Native economy check (beta), the shadow points (docs/specs/ECONOMY_SHADOW_PLAN.md 1.2): which page entry points are one
   economy action each, under which kind, and what each reads from outside that the transport records as its env. Read by
   50-native-shadow.js, which wraps them only while the owner's switch is on and the app says economy.shadow.v1. Nothing here
   runs on its own.
   A point is [kind, owner, name, reads]: owner() is the object the function lives on (window, or LifeMaxHost), name its
   property. reads lists the env parts beyond what every action records (its writeSave() answers, its confirm and prompt
   answers): 'plan' (the plan's rest days and whether today is done), 'health' (every Health Connect answer, through a
   recording proxy while the sync runs), 'dom:<ids>' (the input values the entry reads). Not shadowed, on purpose: imports,
   restores, resets and the demo (REPLACES: they start a new base), dev boosts and pure reads (plan 1.2). The 3D table's
   dhRest.create is made while the table is open, so its save() is recorded as save.other. */
const LMA_SHADOW_POINTS=(function(){
 const W=()=>window, HOST=()=>(typeof LifeMaxHost==='object'&&LifeMaxHost?LifeMaxHost:null);
 const points=[
  ['log.record',W,'recordActivity',[]],
  ['log.delete',W,'deleteActivity',[]],
  ['log.delete',W,'removeAct',[]],
  ['log.delete',W,'undoCat',[]],
  ['voice.log',W,'lmvLogNow',[]],
  ['quest.toggle',W,'toggleQuest',[]],
  ['quest.create',W,'createQuest',['dom:newQuestName,newQuestCadence,newQuestXP']],
  ['hand.ensure',W,'dhEnsure',['plan']],
  ['hand.reroll',W,'dhReroll',[]],
  ['hand.verify',W,'dhAutoVerify',['plan']],
  ['health.sync',W,'lmaHealthSync',['health']],
  ['director.award',HOST,'awardActivity',[]],
  ['director.award',HOST,'completeQuest',[]],
  ['bonus.care',W,'ecoCare',[]],
  ['bonus.nutrition',W,'ecoNutritionCheck',[]],
  ['money.record',W,'recordMoneyTransaction',[]],
  ['money.remove',W,'removeMoneyTransaction',[]],
  ['fixed.plan',W,'planToggle',['plan']],
  ['fixed.catalog',W,'confirmAct',[]],
  ['coach.pay',W,'jcClaim',[]],
  ['coach.pay',W,'journalSaveReflection',[]],
  ['coach.pay',W,'journalSaveReview',[]],
  ['coach.pay',W,'jcCatchUp',[]],
  ['spark.buy',W,'ecoBuy',[]],
  ['ascend',W,'ecoAscend',[]],
  ['rest.take',W,'ecoTakeRest',[]],
  ['rules.save',W,'saveRules',[]],
  ['rules.save',W,'resetRules',[]],
  ['rules.save',W,'saveBase',['dom:actBase']],
  ['rules.save',W,'saveDecay',['dom:decayOn,decayGrace,decayRate']],
  ['decay.apply',W,'applyDecay',['plan']],
  ['save.other',W,'save',[]],
 ];
 /* whole-save replacements: their own saves are not actions; afterwards the shadow starts again from a new base (2.5) */
 const replaces=[[W,'applyBackup'],[W,'resetDemo'],[W,'deleteAll']];
 const PLAN_DAYS=60, DECAY_DAYS=400;
 /* plan facts the economy reads (2.3 env.plan): today done, and the rest days in [from, today] */
 function plan(kind){
  if(typeof planDay!=='function'||typeof getToday!=='function')return null;
  const today=getToday();
  let from=addDays(today,-PLAN_DAYS);
  if(kind.indexOf('decay.')===0){
   const seen=Object.values(state.decayApplied||{}).filter(d=>typeof d==='string').sort()[0];
   if(seen)from=[addDays(seen,1),addDays(today,-DECAY_DAYS)].sort()[1];
  }
  const rest=[];
  for(let d=from;d<=today;d=addDays(d,1)){const p=planDay(d);if(p&&p.kind==='rest')rest.push(d)}
  return {done:!!dayDone(today),rest,from};
 }
 /* the input values the entry reads, by element id; an element that is not there is left out */
 function dom(ids){
  const out={};
  for(const id of ids){const el=document.getElementById(id);if(el)out[id]=el.type==='checkbox'?!!el.checked:String(el.value)}
  return out;
 }
 /* a HealthSync stand-in that answers as the real one and keeps every answer, in the golden cases' env.health form */
 const READS={readExercise:'exercise',readSteps:'steps',readSleep:'sleep',readWeight:'weight'};
 function recordingHealth(real,h){
  const proxy={};
  proxy.status=async function(){const s=await real.status.apply(real,arguments);h.now=Date.now();h.status=JSON.parse(JSON.stringify(s));return s};
  for(const [m,part] of Object.entries(READS))proxy[m]=async function(range){
   try{const a=await real[m].apply(real,arguments);(h[part]=h[part]||[]).push({from:range&&range.from,to:range&&range.to,answer:JSON.parse(JSON.stringify(a))});return a}
   catch(e){h.fail=part;throw e}
  };
  return proxy;
 }
 return {points,replaces,plan,dom,recordingHealth};
})();
