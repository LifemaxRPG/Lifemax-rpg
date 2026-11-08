/* Native economy check (beta), the boot's half (docs/specs/ECONOMY_SHADOW_PLAN.md 2.5, WP6a). While the owner's switch
   (nativeShadow in lifemax_android_prefs_v1) is on, the app offers the NativeShadow plugin and no demo runs, it keeps what
   the boot's load() read from storage and the economy's state just after load() and just after the boot's applyDecay(): the
   transport (50-native-shadow.js) sends them as the first base and the boot.load and boot.decay actions once the plugin has
   answered. With the switch off it reads one preference and changes nothing. Nothing here writes anything.
   It runs before 13-runtime.js calls load(): the segments from 06 to 12 are cut in the middle of statements, so this is the
   last place a statement of its own can stand before the boot. The key lists are native's (core/shadow/ShadowKeys.kt). */
const LMA_SHADOW_BOOT=(function(){
 /* The save keys the economy owns (core/save SaveFields.TRACKED), then the read-only inputs native's adapters need. */
 const TRACKED=['activities','attrXP','skillXP','restedXP','sparks','perk','perksOwned','attrStars','ascended','decayTotal','decayApplied','decayLog','decay','legacyCharXPBonus','xpSchemaV2','customRules','quests','questDone','restDays','restTokens','restBoughtAt','streakShields','shieldTick','shieldMiss','shieldDays','shieldMilestone','shieldBoughtAt','themesOwned','theme','toysBought','nutriDone','careDay','mealsMeta','foodLog','money','actXP','plan','health','weight','devMode','dailyDraw','journalCoach','dayReflections'];
 const INPUTS=['waterLog','pet','luck','classId','classContent','dhMig','schema'];
 const KEYS=TRACKED.concat(INPUTS), REC=['activities','quests','decayLog'];
 /* foodLog crosses as the two fields the economy reads (SaveFields.PARTIAL): never what was eaten */
 function project(key,v){
  if(key==='foodLog'&&Array.isArray(v))return v.map(e=>e&&typeof e==='object'?{date:e.date,kcal:e.kcal}:e);
  return v;
 }
 /* The JSON text of every shadow key of `s`: a record key as one text per record, any other key whole; absent keys are left out.
    A value JSON cannot write (a cycle) makes the whole capture fail, which the caller counts as a fault. */
 function capture(s){
  const out={};
  for(const k of KEYS){
   const v=s?project(k,s[k]):undefined;
   if(v===undefined)continue;
   if(REC.indexOf(k)>=0&&Array.isArray(v))out[k]={items:v.map(x=>JSON.stringify(x)??'null')};
   else{const t=JSON.stringify(v);if(t!==undefined)out[k]=t}
  }
  return out;
 }
 const boot={KEYS,REC,TRACKED,INPUTS,project,capture,on:false,stored:null,loaded:null,decayed:null,saves:{load:[],decay:[]},times:{},fault:false};
 try{
  const p=JSON.parse(localStorage.getItem('lifemax_android_prefs_v1')||'null');
  const C=window.Capacitor;
  boot.on=!!p&&p.nativeShadow===true&&!(window.LM_DEMO&&window.LM_DEMO.on)&&!!C&&typeof C.isPluginAvailable==='function'&&C.isPluginAvailable('NativeShadow');
 }catch(e){boot.on=false}
 if(!boot.on)return boot;
 /* runs fn with every writeSave() answer recorded into list */
 function recordingWrites(list,fn){
  const real=window.writeSave;
  window.writeSave=function(){const ok=real.apply(this,arguments);list.push({ok:!!ok});return ok};
  try{return fn()}finally{window.writeSave=real}
 }
 const realLoad=window.load,realDecay=window.applyDecay;
 /* assigned through window, as every later wrapper of a page function is: the page calls these by their global names */
 window.load=function(){
  window.load=realLoad; // only the boot's own load
  try{boot.stored=localStorage.getItem(STORE)}catch(e){boot.stored=null}
  const t0=Date.now(),self=this,args=arguments;
  const r=recordingWrites(boot.saves.load,()=>realLoad.apply(self,args));
  try{boot.times.load=[t0,Date.now()];boot.loaded=capture(state)}catch(e){boot.fault=true}
  return r;
 };
 window.applyDecay=function(){
  window.applyDecay=realDecay; // only the boot's first decay, the one right after load()
  const t0=Date.now(),r=realDecay.apply(this,arguments);
  try{
   boot.times.decay=[t0,Date.now()];boot.decayed=capture(state);
   if(r){const real=window.writeSave;window.writeSave=function(){window.writeSave=real;const ok=real.apply(this,arguments);boot.saves.decay.push({ok:!!ok});return ok}}
  }catch(e){boot.fault=true}
  return r;
 };
 return boot;
})();
