/* ---------- LifeMax Voice NLU v2: classify what a sentence is FOR before any scheduling happens, so a question is never
   mistaken for a new activity; understand "any time", "now", "in 30 minutes", "morning", "never mind" while a question
   is open; let a brand-new command barge in on a half-finished one; and run real handlers for quests, XP, cats, steps,
   food, the schedule and screens. Plain web code (needs no native change). Phase 1 scheduling lives in 45-voice.js. ---------- */

/* ===== semantic buckets: words that mean the same thing, grouped (compositional, not phrase lists) ===== */
const LMV_B={
 queryStart:/^ (?:(?:can|could|would|will) you )?(?:please )?(?:what|what's|whats|which|show|show me|check|view|list|tell me|read|read me|how many|how much|how is|how are|how's|how am|do i have|have i got|am i|when am i|when is|when do i|when are|is there|are there|got any|anything|give me|let me see|see|i want to see|display) /,
 createVerb:/\b(schedule|add|put|book|create|block out|block|plan|set up|setup|remind me|reserve|make me|i need to|i have to|i want to|i'd like to|i would like to|i've got to|ive got to|got to)\b/,
 createStrong:/\b(put|add|book|create|block out|set up|setup|remind me|reserve)\b|^ (?:(?:can|could|would|will) you )?(?:please )?schedule (?:me |a |an |my |the |some |\d)/,
 quest:/\b(quests?|dailies|daily quests?|weeklies|missions?|objectives?|challenges?|bount(?:y|ies)|to-?do quests?)\b/,
 stats:/\b(xp|exp|experience|levels?|stats?|character|attributes?|rank|streaks?|achievements?|progress|points)\b/,
 sched:/\b(schedule|agenda|calendar|plans?|planned|going on|lined up|on my plate|corona|life director|lifedirector|timetable|my day|the day|busy|booked|appointments?|meetings?)\b/,
 free:/\b(free|open|available|empty)\b/,
 pets:/\b(cats?|kitt(?:y|ies|ens?)|pets?|furballs?)\b/,
 petAct:/\b(feed|pet|cuddle|stroke|play with|play|brush|visit|check on|check in on)\b/,
 steps:/\b(steps?|step count|walked|walking|active minutes|fitness|activity today)\b/,
 workouts:/\b(workouts?|exercise|exercised|work(?:ed)? out|trained|training)\b/,
 food:/\b(calories|calorie|kcal|macros?|protein|carbs?|carbohydrates?|fat|fats|water|hydration|nutrition|meals?|eaten|ate|food)\b/,
 done:/\b(complete|completed|finish|finished|done with|i did|just did|check off|checked off|mark|tick off|cross off|crossed off|i just finished|i've finished|ive finished|did my)\b/,
 move:/\b(move|reschedule|push|push back|bump|shift|postpone|delay|change the time of|bring forward)\b/,
 cancelAct:/\b(cancel|delete|remove|clear|take off|drop|skip|scrap)\b/,
 nav:/^ (?:open|go to|take me to|switch to|bring up|launch|navigate to|go back to|back to|show me the|pull up|jump to|go) /,
 abort:/^(?:cancel|never ?mind|forget it|forget that|stop|nothing|abort|scratch that|no thanks|leave it|drop it|exit)$/,
 any:/^(?:any ?time|anytime|whenever(?: i'?m free| is fine| works| you want| you like| is good)?|you pick|you choose|up to you|doesn'?t matter|does not matter|don'?t care|dont care|no preference|open|any|surprise me|(?:the )?(?:first|next) (?:free|open|available)(?: slot| time| window)?|any time works|anything|either)$/,
 now:/^(?:now|right now|starting now|immediately|asap|as soon as possible|right away|this minute)$/,
 inN:/^in (\d+(?:\.\d+)?) ?(minutes?|mins?|min|hours?|hrs?|hr|h|m)$/,
 daypart:/^(?:in the |this |at |during the |sometime in the |early |late )?(morning|afternoon|evening|tonight|night)$/
};
const LMV_SECTIONS=[
 {re:/\b(corona|dial|life director|lifedirector|schedule|calendar|agenda|planner)\b/,id:'life-director',feature:true,label:'Life Director'},
 {re:/\b(home|overview|dashboard|main screen)\b/,id:'dashboard',label:'Overview'},
 {re:/\b(character|profile|rpg|stats|level)\b/,id:'character',label:'Character'},
 {re:/\b(quests?|missions?|dailies)\b/,id:'quests',label:'Quests'},
 {re:/\b(log activity|log|add activity)\b/,id:'log',label:'Log activity'},
 {re:/\b(workout plan|plan|training plan|workouts?)\b/,id:'plan',label:'Workout plan'},
 {re:/\b(library|media|books|journal|history)\b/,id:'media',label:'Library'},
 {re:/\b(recipes?|cookbook)\b/,id:'recipes',label:'Recipes'},
 {re:/\b(food|meals?|nutrition|macros|calories)\b/,id:'meals',label:'Food'},
 {re:/\b(money|budget|finance|finances|spending)\b/,id:'money',label:'Money'},
 {re:/\b(cats?|pets?|cat room|kitty|kitten)\b/,id:'pet',label:'Cat'},
 {re:/\b(settings|options|preferences)\b/,id:'settings',label:'Settings'},
 {re:/\b(diagnostics|voice diagnostics|voice settings)\b/,id:'__diag',label:'Voice diagnostics'}
];
const LMV_STOP=/\b(my|the|a|an|some|that|this|it|please|just|for|on|at|in|of|to|today|tomorrow|tonight|now|me|i|did|do|have|has|had|task|activity|session|thing|up|off|from|schedule|corona|life director|lifedirector|calendar)\b/g;

function lmvLow(raw){return ' '+String(raw==null?'':raw).toLowerCase().replace(/[‘’]/g,"'").replace(/[^a-z0-9:'\s\-.?]/g,' ').replace(/\s+/g,' ').trim()+' '}
function lmvLead(s){return s.replace(/^ (?:(?:hey|hi|hello|ok|okay|so|um|uh|actually|and|well|then|alright|all right|right|lifemax|life max) )+/,' ')}

/* ===== the classifier: what is this sentence for? (id, confidence, parts) ===== */
function lmvClassify(text){
 const raw=String(text==null?'':text),s0=lmvLow(raw),s=lmvLead(s0);
 const qs=LMV_B.queryStart.test(s);
 const has=function(k){return LMV_B[k].test(s)};
 /* an explicit scheduling verb makes it a command, even if it ends in a question mark ("can you put a workout on my schedule?") */
 const cmd=!qs&&(has('createStrong')||/^ (?:(?:can|could|would|will) you )?(?:please )?(?:schedule|plan|book|add|put|create|remind|block|set up|set me up|set me|set) /.test(s));
 const q=qs||(/\?\s*$/.test(raw.trim())&&!cmd&&!has('createVerb'));
 const out=function(id,conf,extra){return Object.assign({id:id,conf:conf,question:q},extra||{})};
 /* navigation: "open my quests", "take me to the cat room" (a verb of movement, not a question) */
 if(LMV_B.nav.test(s)||/\b(?:screen|page|tab|section)\s*$/.test(s.trim())&&/^ (?:show|open)/.test(s)){
  const rest=s.replace(LMV_B.nav,' ');
  for(let i=0;i<LMV_SECTIONS.length;i++)if(LMV_SECTIONS[i].re.test(rest))return out('navigation_system.open_section',.92,{section:LMV_SECTIONS[i]});
 }
 /* screens named after show/view with no open verb ("show my character"), and "log an activity" as the screen */
 const bareNav=/^ (?:show|show me|view|display|go|open) (?:my |the |a |an )?(character|workout plan|library|settings|overview|dashboard|log activity|activity|life director|corona dial|corona|cat room|journal|recipes)(?: screen| page| tab)? ?\??$/.exec(s);
 const nounNav=/^ (character|overview|dashboard|workout plan|library|settings|quests|life director|corona dial|cat room) (?:screen|page|tab) ?\??$/.exec(s);
 if(nounNav){for(let i=0;i<LMV_SECTIONS.length;i++)if(LMV_SECTIONS[i].re.test(nounNav[1]))return out('navigation_system.open_section',.9,{section:LMV_SECTIONS[i]})}
 if(bareNav){const key=bareNav[1]==='activity'?'log activity':bareNav[1];for(let i=0;i<LMV_SECTIONS.length;i++)if(LMV_SECTIONS[i].re.test(key))return out('navigation_system.open_section',.9,{section:LMV_SECTIONS[i]})}
 if(/^ (?:(?:can|could|would|will) you )?(?:please )?(?:log|add|record) (?:an? |my )?activity ?\??$/.test(s)){for(let i=0;i<LMV_SECTIONS.length;i++)if(LMV_SECTIONS[i].id==='log')return out('navigation_system.open_section',.9,{section:LMV_SECTIONS[i]})}
 /* logging something that already happened: "log a 30 minute run", "I did 20 minutes of yoga yesterday" */
 const logVerb=/^ (?:(?:can|could|would|will) you )?(?:please )?(?:log|record|track|add (?:a |an |my )?(?:activity|workout|session|run|walk))\b/.test(s);
 const didVerb=/\bi (?:just |also )?(?:did|ran|walked|jogged|lifted|studied|worked out|exercised|trained|practiced|meditated|stretched|swam|cycled|biked|hiked|read|cooked|cleaned)\b/.test(s);
 const hasDur=/\b\d+(?:\.\d+)?\s*(?:minutes?|mins?|hours?|hrs?)\b/.test(s)||/\b(?:an|a|half an|one) hour\b/.test(s);
 const stepsLog=/\b(?:i|ive|i've) (?:just |also )?(?:walked|took|did|got|hit) \d[\d ,.]*k? steps\b|^ (?:log|record|add) \d[\d ,.]*k? steps\b/.test(s);
 if(!q&&(logVerb||stepsLog||(didVerb&&hasDur)))return out('health_fitness.log_activity',.9);
 if(cmd&&!(has('move')||has('cancelAct')||has('done')))return out('life_director.create_activity',.9);
 /* things you do to an existing activity */
 if(!q||/^ (?:can|could|would|will) you /.test(s0)){
  if(has('move')&&!has('createStrong')&&/\b(?:to|by|back|forward|later|earlier|until|for)\b|\d/.test(s))return out('life_director.move_activity',.86,{text:s});
  if(has('cancelAct')&&!has('createStrong')&&!/^ (?:cancel|stop|skip)\s*$/.test(s))return out('life_director.cancel_activity',.86,{text:s});
  if(has('done')&&!has('createStrong')&&(!has('quest')||/\b(done|finished|completed?)\b/.test(s)&&!/\bquests?\b/.test(s)))return out('life_director.complete_activity',.86,{text:s});
 }
 /* cats and pets: by noun, or by the cat's own name */
 let petName=null;try{const p=typeof petData==='function'?petData():null;if(p&&p.name&&new RegExp('\\b'+String(p.name).toLowerCase().replace(/[^a-z0-9]/g,'')+'\\b').test(s.replace(/[^a-z0-9 ]/g,'')))petName=p.name}catch(e){}
 if(has('pets')||petName){
  if(has('petAct')&&!/^ (?:how|what|is|are)\b/.test(s)&&!/\bcheck on\b|\bcheck in on\b/.test(s)&&!has('nav'))return out('virtual_pets.perform_action',.88,{act:/\bfeed\b/.test(s)?'feed':/\bplay\b/.test(s)?'play':'pet',petName:petName});
  if(q||has('petAct')||petName)return out('virtual_pets.check_status',.86,{petName:petName});
 }
 /* quests, character, steps, food */
 if(has('quest')&&(q||!has('createVerb')))return out('rpg.view_quests',q?.93:.8,{left:/\b(left|remaining|to do|outstanding|undone|unfinished)\b/.test(s)});
 if(/^ (?:what|which)\b.*\b(?:did i (?:do|log|complete|finish|get done)|have i (?:done|logged|completed)|activities (?:did i|have i)|things did i)\b/.test(s)||/^ (?:what|which) activities\b.*\b(?:logged|did|done)\b/.test(s))return out('life_director.query_schedule',.9,{history:true});
 if(has('workouts')&&q&&!has('steps'))return out('health_fitness.query_workouts',.9);
 if(has('steps')&&(q||/^ (?:log|record|track)\b/.test(s))&&!/\blog\b/.test(s))return out('health_fitness.query_steps',.9);
 if(has('food')&&q&&!has('sched'))return out('nutrition.check_totals',.9,{water:/\b(water|hydration)\b/.test(s),macro:/\bprotein\b/.test(s)?'protein':/\bcarbs?\b|\bcarbohydrates?\b/.test(s)?'carbs':/\bfats?\b/.test(s)&&!/\bfat(?:igue)\b/.test(s)?'fat':''});
 if(has('stats')&&q&&!has('sched')&&!has('quest')){
  let metric='';const lvm=/\b(?:level|lv)\s*(\d+)\b/.exec(s);
  if(/\b(until|till|to reach|to get to|before)\b.*\b(level|lv)\b|\blevel up\b|\bnext level\b|\bhow close\b|\bhow far\b.*\b(level|lv)\b/.test(s))metric='toLevel';
  else if(/\bstreaks?\b/.test(s))metric='streak';
  else if(/\bachievements?\b/.test(s))metric='achievements';
  else if(/\b(rank|title)\b/.test(s))metric='title';
  else if(/\b(what|which) (?:level|lv)\b|\bmy level\b|\bwhat's my level\b/.test(s))metric='level';
  else if(/\bxp\b/.test(s)&&(/\b(earn|earned|get|got|gain|gained|make|made|did i)\b/.test(s)||/\b(today|yesterday|this week|last week|this month|ago|last night)\b/.test(s)))metric='xp';
  return out('rpg.query_stats',.9,{metric:metric,targetLevel:lvm?+lvm[1]:null,streak:metric==='streak',level:metric==='level'});
 }
 /* schedule: free time, then the plain schedule question */
 if(q&&(has('free')&&(/\b(when|am i|do i have|any)\b/.test(s)||has('sched')||/\b(time|slot|window|gap)\b/.test(s))&&!/\bquests?\b/.test(s)))return out('life_director.find_free_time',.88);
 if(q&&(has('sched')||/\b(today|tomorrow|tonight|this (?:morning|afternoon|evening)|on monday|on tuesday|on wednesday|on thursday|on friday|on saturday|on sunday|this week)\b/.test(s)&&/\b(what|anything|do i have|am i)\b/.test(s)))return out('life_director.query_schedule',.9);
 /* a question that matched nothing: say what can be done instead of starting an activity */
 if(q&&!has('createStrong'))return out('unknown_query',.8);
 return out('life_director.create_activity',has('createVerb')?.85:.5);
}

/* ===== helpers ===== */
function lmvToday(ctx){return lmvYmd(lmvParts(ctx.now,ctx.tz))}
function lmvClock(ms,tz){const p=lmvParts(ms,tz);return lmvNiceTime(lmvPad(p.h)+':'+lmvPad(p.mi))}
function lmvLines(f,head,title,lines,actions){
 f.state='CONFIRMED';f.undo=false;f.query=true;f.head=head;f.title=title;f.slot=null;f.question=null;f.act=null;
 f.notices=(lines||[]).map(function(t){return {kind:'info',text:t}});f.actions=actions||[];
 return lmvSnapshot();
}
function lmvGo(section){
 try{
  if(section.id==='__diag'){lmvDiagOpen();return}
  if(section.feature){navigate(section.id);if(window.LifeMaxFeatures)LifeMaxFeatures.open(section.id);return}
  if(typeof lmaNavTo==='function')lmaNavTo(section.id);else navigate(section.id);
 }catch(e){console.warn('[voice nav]',e)}
}
function lmvOpenChip(id,label,feature){return {label:'Open '+label,run:function(){lmvGo({id:id,feature:!!feature});lmvFrame=null;lmvHudOpen(false)}}}
function lmvDaypartWin(w){return w==='morning'?[6*60,12*60]:w==='afternoon'?[12*60,17*60]:w==='evening'?[17*60,22*60]:w==='tonight'?[19*60,24*60]:w==='night'?[20*60,24*60]:[9*60,21*60]}
function lmvDaypartDefault(w){return w==='morning'?9*60:w==='afternoon'?14*60:w==='evening'?18*60:w==='tonight'?20*60:w==='night'?21*60:9*60}

/* busy intervals (epoch seconds) on a local date: sleep, work, commitments, planned sessions */
function lmvBusy(date,ctx){
 const a=lmvLD.api();if(!a||!a.ready())return null;
 const p=date.split('-').map(Number),s0=Math.floor(lmvLocalToEpoch(p[0],p[1],p[2],0,0,ctx.tz).epochMs/1000),s1=s0+86400+3600;
 let out=[];try{out=a.overlaps(s0-3600,s1).map(function(x){return [x.startEpoch,x.endEpoch]})}catch(e){}
 try{a.sessions(date).forEach(function(x){out.push([x.startEpoch,x.endEpoch])})}catch(e){}
 return out;
}
/* first window of durMin minutes inside [fromMin,toMin) (minutes since local midnight) with nothing in it; step 15 */
function lmvFindSlot(date,durMin,ctx,fromMin,toMin){
 const busy=lmvBusy(date,ctx);if(!busy)return null;
 const p=date.split('-').map(Number);
 for(let m=Math.ceil(fromMin/15)*15;m+durMin<=toMin;m+=15){
  const st=Math.floor(lmvLocalToEpoch(p[0],p[1],p[2],Math.floor(m/60),m%60,ctx.tz).epochMs/1000),en=st+durMin*60;
  if(st*1000<ctx.now-60000)continue;
  if(!busy.some(function(b){return b[0]<en&&b[1]>st}))return m;
 }
 return null;
}
function lmvNowMin(ctx,round){const p=lmvParts(ctx.now,ctx.tz);let m=p.h*60+p.mi;return round?Math.ceil(m/round)*round:m}

/* ===== replies while a question is open: "any time", "now", "in 30 minutes", "morning", "never mind" ===== */
function lmvMeta(f,n,ctx){
 const kind=f.question&&f.question.kind;if(!kind)return null;
 const raw=f.raw||{},date=raw.date||lmvToday(ctx),dur=raw.dur||LMV_DEFAULT_MIN,today=lmvToday(ctx);
 const setTime=function(min,note){return {patch:{hour:Math.floor(min/60)%24,minute:min%60,mer:'24',daypart:null,dst:null},note:note}};
 if(kind==='time'||kind==='meridiem'){
  let m;
  if(LMV_B.any.test(n)){
   const from=date===today?Math.max(lmvNowMin(ctx,15),9*60):9*60,slot=lmvFindSlot(date,dur,ctx,from,21*60)
    ??lmvFindSlot(date,dur,ctx,date===today?lmvNowMin(ctx,15):0,24*60);
   if(slot==null)return {fail:'I could not find an open '+lmvDurText(dur)+' window on '+lmvNiceDate(date)+'. What time should I use?'};
   return setTime(slot,'Found an open slot at '+lmvNiceTime(lmvPad(Math.floor(slot/60))+':'+lmvPad(slot%60))+'.');
  }
  if(LMV_B.now.test(n)){const mi=lmvNowMin(ctx,5);return Object.assign(setTime(mi%1440,'Starting now.'),{date:mi>=1440?lmvAddDays(today,1):today})}
  if((m=LMV_B.inN.exec(n))){
   const k=/^h/.test(m[2])?60:1,tot=lmvNowMin(ctx,0)+Math.round(+m[1]*k),r=Math.ceil(tot/5)*5;
   return Object.assign(setTime(r%1440,'In '+m[1]+' '+(k===60?'hour':'minute')+(+m[1]===1?'':'s')+', at '+lmvNiceTime(lmvPad(Math.floor(r%1440/60))+':'+lmvPad(r%60))+'.'),{date:r>=1440?lmvAddDays(today,1):today});
  }
  if((m=LMV_B.daypart.exec(n))){
   const dp=m[1],w=lmvDaypartWin(dp),dd=dp==='tonight'?today:date;
   let from=Math.max(lmvDaypartDefault(dp),dd===today?lmvNowMin(ctx,15):0);
   const slot=lmvFindSlot(dd,dur,ctx,from,w[1])??lmvFindSlot(dd,dur,ctx,w[0],w[1]);
   if(slot==null)return {fail:'Nothing is open in the '+(dp==='tonight'?'evening':dp)+' on '+lmvNiceDate(dd)+'. What time should I use?'};
   return Object.assign(setTime(slot,'Picked '+lmvNiceTime(lmvPad(Math.floor(slot/60))+':'+lmvPad(slot%60))+' for the '+(dp==='tonight'?'evening':dp)+'.'),{date:dp==='tonight'?today:undefined});
  }
 }
 if(kind==='duration'&&LMV_B.any.test(n))return {patch:{dur:LMV_DEFAULT_MIN},note:'Using a standard '+LMV_DEFAULT_MIN+' minutes.'};
 if(kind==='date'&&LMV_B.any.test(n))return {patch:{date:today,dateAmbig:null},note:'Using today.'};
 return null;
}

/* ===== find an existing activity by what the user called it ===== */
function lmvTokens(t){return String(t||'').toLowerCase().replace(LMV_STOP,' ').replace(/[^a-z0-9 ]/g,' ').split(/\s+/).filter(function(w){return w.length>1})}
function lmvCandidates(date,ctx){
 const a=lmvLD.api();if(!a||!a.ready())return null;
 let out=[];try{out=a.sessions(date).concat(a.commitments(date))}catch(e){}
 return out;
}
let lmvLastRef=null;
function lmvResolve(textAfterVerb,ctx,dates){
 const toks=lmvTokens(textAfterVerb),pronoun=/\b(it|that|this|current|the current|right now|what i'm doing)\b/.test(textAfterVerb)&&!toks.length;
 let all=[];dates.forEach(function(d){const c=lmvCandidates(d,ctx);if(c)all=all.concat(c)});
 if(!all.length)return {none:true};
 if(pronoun||!toks.length){
  const nowS=Math.floor(ctx.now/1000);let cur=all.filter(function(x){return x.startEpoch<=nowS&&x.endEpoch>nowS})[0];
  if(!cur&&lmvLastRef)cur=all.filter(function(x){return String(x.id)===String(lmvLastRef.id)})[0];
  if(!cur){const nxt=all.filter(function(x){return x.startEpoch>nowS})[0];cur=nxt}
  return cur?{item:cur}:{none:true};
 }
 const scored=all.map(function(x){
  const tt=lmvTokens(x.title),hit=toks.filter(function(w){return tt.some(function(u){return u===w||u.indexOf(w)===0||w.indexOf(u)===0&&u.length>3})}).length;
  return {x:x,score:hit/toks.length};
 }).filter(function(r){return r.score>=.5}).sort(function(a,b){return b.score-a.score||a.x.startEpoch-b.x.startEpoch});
 if(!scored.length)return {none:true,toks:toks};
 if(scored.length>1&&scored[0].score===scored[1].score&&scored[0].x.title!==scored[1].x.title)return {many:scored.slice(0,3).map(function(r){return r.x})};
 return {item:scored[0].x};
}
function lmvTimeAfter(text){const i=text.search(/\b(?:to|until|for|at|by)\b\s*(?:\d|noon|midnight|tomorrow|today|tonight|monday|tuesday|wednesday|thursday|friday|saturday|sunday|next|the )/);return i>=0?text.slice(i):''}
function lmvDatesFor(text,ctx){const e=lmvExtract(text,ctx,null),t=lmvToday(ctx);return {raw:e,dates:[e.date||t].concat(e.date?[]:[lmvAddDays(t,1)])}}

/* ===== handlers for each domain ===== */
function lmvDomain(cls,text,f,ctx){
 const id=cls.id;f.intentId=id;f.transcript=text;
 const scope=cls.scope||lmvScope(text,ctx,!/^life_director\.(query_schedule|find)/.test(id)||!!cls.history);cls.scope=scope;cls.text=cls.text||text;
 try{
  let r=null;
  if(id==='rpg.view_quests')r=lmvHQuests(f,cls);
  else if(id==='rpg.query_stats')r=lmvHStats(f,cls,scope,ctx);
  else if(id==='life_director.query_schedule')r=lmvHSchedule(f,text,ctx,scope,cls);
  else if(id==='life_director.find_free_time')r=lmvHFree(f,text,ctx,scope);
  else if(id==='virtual_pets.check_status')r=lmvHPetStatus(f,cls);
  else if(id==='virtual_pets.perform_action')r=lmvHPetAct(f,cls);
  else if(id==='health_fitness.query_steps')r=lmvHSteps(f,scope,ctx);
  else if(id==='health_fitness.query_workouts')r=lmvHWorkouts(f,scope,ctx);
  else if(id==='health_fitness.log_activity')return lmvHLog(f,text,ctx,scope);
  else if(id==='nutrition.check_totals')r=lmvHFood(f,cls,scope,ctx);
  else if(id==='navigation_system.open_section'){lmvGo(cls.section);return lmvLines(f,'Opening',cls.section.label,['Opening '+cls.section.label+'.'],[])}
  else if(id==='life_director.complete_activity')return lmvHComplete(f,text,ctx);
  else if(id==='life_director.move_activity')return lmvHMove(f,text,ctx);
  else if(id==='life_director.cancel_activity')return lmvHCancel(f,text,ctx);
  else if(id==='unknown_query')return lmvLines(f,'Voice','I did not catch that',['I can schedule things, log what you did, read your schedule, free time, steps, workouts, XP, quests, food and cats, move or cancel an activity, and open any screen.','Try “how many steps did I walk yesterday” or “open the cat room”.'],[]);
  if(r&&/^(rpg|health_fitness|nutrition|life_director\.(query|find))/.test(id))lmvLastQ={id:id,cls:Object.assign({},cls),text:text,scope:scope,ts:Date.now()};
  return r;
 }catch(e){console.warn('[voice domain]',e);return lmvLines(f,'Voice','Something went wrong',['I could not do that: '+(e&&e.message||e)],[])}
}
function lmvHQuests(f,cls){
 if(typeof state==='undefined'||!Array.isArray(state.quests))return lmvLines(f,'Quests','Quests are not available',[],[]);
 const mk=function(q){let done=false;try{done=isQuestDone(q)}catch(e){}let locked=false;try{locked=questLocked(q)}catch(e){}return {q:q,done:done,locked:locked}};
 const day=state.quests.filter(function(q){return q.cadence==='daily'&&(typeof questToday!=='function'||questToday(q))}).map(mk);
 const week=state.quests.filter(function(q){return q.cadence==='weekly'}).map(mk);
 let all=day.concat(week).filter(function(r){return !r.locked});
 const total=all.length,left=all.filter(function(r){return !r.done});
 if(cls.left)all=left;
 const fin=Object.keys(state.questDone||{}).length;
 const rows=all.slice(0,10).map(function(r,i){return (i+1)+'. '+r.q.name+(r.done?' (done)':'')+(r.q.cadence==='weekly'?' (weekly)':'')});
 const head=cls.left?(left.length?left.length+' quest'+(left.length===1?'':'s')+' left':'Every quest is done'):(total?total+' active quest'+(total===1?'':'s')+' today · '+left.length+' to go':'No active quests');
 if(rows.length&&cls.finished!==false)rows.push('Quests finished all time: '+fin);
 return lmvLines(f,'Quests',head,rows.length?rows:['Nothing here yet.'],[lmvOpenChip('quests','Quests')]);
}
function lmvHStats(f,cls,scope,ctx){
 const lv=characterLevel(),tot=typeof totalXP==='function'?totalXP():0,today=lmvToday(ctx);
 let st=0;try{st=streak()}catch(e){}
 const ach=Object.keys(state.questDone||{}).length,tx=cls.text||'',mt=cls.metric||'';
 const title=(function(){try{return titleFor(lv.level)}catch(e){return ''}})();
 const done=scope.explicit?lmvXpOf(lmvActsIn(scope)):null;
 const nice=lmvNice;
 /* xp needed for a level */
 if(mt==='toLevel'){
  let target=cls.targetLevel||lv.level+1;if(target<=lv.level)target=lv.level+1;
  let need=Math.max(0,Math.round(lv.needed-lv.into));
  for(let l=lv.level+1;l<target;l++){try{need+=charXpForLevel(l)}catch(e){}}
  return lmvLines(f,'Character',need.toLocaleString()+' XP to level '+target,['You are level '+lv.level+' ('+Math.round(lv.into)+' / '+Math.round(lv.needed)+' XP into it)','Total XP: '+Math.round(tot).toLocaleString()],[lmvOpenChip('character','Character')]);
 }
 if(mt==='level')return lmvLines(f,'Character','You are level '+lv.level,['Title: '+title,Math.round(lv.into)+' / '+Math.round(lv.needed)+' XP to level '+(lv.level+1),'Total XP: '+Math.round(tot).toLocaleString()],[lmvOpenChip('character','Character')]);
 if(mt==='title')return lmvLines(f,'Character','Level '+lv.level+' '+title,['Total XP: '+Math.round(tot).toLocaleString()],[lmvOpenChip('character','Character')]);
 if(mt==='streak')return lmvLines(f,'Character',st+' day streak',[st?'Keep it going with one small thing today.':'Log anything today to start one.'],[lmvOpenChip('dashboard','Overview')]);
 if(mt==='achievements')return lmvLines(f,'Character',ach+' achievement'+(ach===1?'':'s')+' unlocked',['Quests finished all time: '+ach],[lmvOpenChip('quests','Quests')]);
 if(mt==='xp'&&scope.kind!=='all'){
  const list=lmvActsIn(scope),x=lmvXpOf(list),lines=[];
  if(scope.kind==='day'&&scope.date!==today)lines.push(scope.full);
  if(scope.kind==='range'){const d=lmvDays(scope).length;lines.push('Average '+Math.round(x/Math.max(1,d))+' XP a day over '+d+' days')}
  lines.push(list.length+' logged activit'+(list.length===1?'y':'ies'));
  if(scope.kind==='day'&&scope.date===today){lines.push('Total XP: '+Math.round(tot).toLocaleString()+' · level '+lv.level)}
  return lmvLines(f,'Character',x.toLocaleString()+' XP '+scope.label,lines,[lmvOpenChip('character','Character')]);
 }
 let todayXp=0;try{todayXp=lmvXpOf(lmvActsOn(today))}catch(e){}
 const lines=['Level '+lv.level+' · '+title+' · '+Math.round(lv.into)+' / '+Math.round(lv.needed)+' XP to level '+(lv.level+1),'Total XP: '+Math.round(tot).toLocaleString()+' · today '+todayXp,'Streak: '+st+' day'+(st===1?'':'s')+' · achievements '+ach];
 try{const top=ATTRS.slice().sort(function(a,b){return attrXP(b.id)-attrXP(a.id)})[0];if(top&&attrXP(top.id)>0)lines.push('Strongest: '+top.name+' ('+Math.round(attrXP(top.id))+' XP)')}catch(e){}
 return lmvLines(f,'Character',Math.round(tot).toLocaleString()+' XP · level '+lv.level,lines,[lmvOpenChip('character','Character')]);
}
function lmvHSchedule(f,text,ctx,scope,cls){
 const today=lmvToday(ctx),a=lmvLD.api();
 const history=(cls&&cls.history)||(scope.kind==='day'&&scope.date<today)||scope.kind==='range'||scope.kind==='all';
 if(history){
  /* what was done: the activity log, never today's plan */
  if(scope.isFuture)return lmvLines(f,'Schedule',scope.label+' has not happened yet',['Ask “what is on my schedule '+scope.label+'” to see the plan.'],[]);
  const list=lmvActsIn(scope).filter(function(x){return x.type!=='coach'}).sort(function(a,b){return new Date(a.date)-new Date(b.date)});
  const head=list.length?'You logged '+list.length+' thing'+(list.length===1?'':'s')+' '+scope.label+' · '+lmvXpOf(list)+' XP':'Nothing logged '+scope.label;
  const lines=[];if(scope.kind==='day'&&scope.date!==today)lines.push(scope.full);
  list.slice(0,12).forEach(function(x){lines.push((scope.kind==='day'?'':lmvNice(activityDate(x))+'  ')+lmvClock(new Date(x.date).getTime(),ctx.tz)+'  '+x.name+' (+'+(x.xp||0)+')')});
  return lmvLines(f,'Your log',head,lines.length?lines:['Your Journal has nothing for that.'],[lmvOpenChip('history','Journal')]);
 }
 const date=scope.date;let items=null;try{items=a&&a.ready()?a.dayItems(date):null}catch(x){}
 if(items===null)return lmvLines(f,'Schedule','Life Director is not set up yet',['Open Life Director to set your sleep and work hours first.'],[lmvOpenChip('life-director','Life Director',true)]);
 const w=scope.dayPart?lmvDaypartWin(scope.dayPart):null;
 if(w)items=items.filter(function(it){const p=lmvParts(it.startEpoch*1000,ctx.tz),m=p.h*60+p.mi;return m>=w[0]&&m<w[1]});
 const label=(date===today?'Today':date===lmvAddDays(today,1)?'Tomorrow':lmvNice(date))+(scope.dayPart&&scope.dayPart!=='night'?' '+(scope.dayPart==='evening'?'evening':scope.dayPart):'');
 const lines=items.slice(0,10).map(function(it){return lmvClock(it.startEpoch*1000,ctx.tz)+'  '+it.title});
 const head=items.length?label+': '+items.length+' thing'+(items.length===1?'':'s')+' planned':label+': nothing planned';
 return lmvLines(f,'Your schedule',head,lines.length?lines:['Your schedule is clear.'],[lmvOpenChip('life-director','Life Director',true)]);
}
function lmvHFree(f,text,ctx,scope){
 const e={daypart:scope&&scope.dayPart},today=lmvToday(ctx),date=(scope&&scope.date)||today,busy=lmvBusy(date,ctx);
 if(!busy)return lmvLines(f,'Schedule','Life Director is not set up yet',[],[lmvOpenChip('life-director','Life Director',true)]);
 if(date<today)return lmvLines(f,'Free time','That day is over',['I can only look for open time today or later.'],[]);
 const w=e.daypart?lmvDaypartWin(e.daypart):[7*60,22*60],gaps=[];
 let from=Math.max(w[0],date===today?lmvNowMin(ctx,15):0);
 while(from<w[1]&&gaps.length<5){
  const s=lmvFindSlot(date,30,ctx,from,w[1]);if(s==null)break;
  let e2=s+30;while(e2+15<=w[1]&&lmvFindSlot(date,e2+15-s,ctx,s,e2+15)===s)e2+=15;
  gaps.push([s,e2]);from=e2+15;
 }
 const t=function(m){return lmvNiceTime(lmvPad(Math.floor(m/60))+':'+lmvPad(m%60))};
 const lines=gaps.map(function(g){return t(g[0])+' to '+t(g[1])+'  ('+lmvDurText(g[1]-g[0])+')'});
 return lmvLines(f,'Free time',gaps.length?'You are free '+(date===today?'today':lmvNiceDate(date)):'No open time '+(date===today?'today':'on '+lmvNiceDate(date)),lines.length?lines:['Nothing open in that window.'],[lmvOpenChip('life-director','Life Director',true)]);
}
function lmvPets(){const out=[];try{if(state.pet)out.push(state.pet);if(state.pet2)out.push(state.pet2)}catch(e){}return out}
function lmvHPetStatus(f,cls){
 const p=typeof petData==='function'?petData():null;
 if(!p)return lmvLines(f,'Cat','You do not have a cat yet',['Open the Cat tab to meet one.'],[lmvOpenChip('pet','Cat')]);
 if(!p.alive)return lmvLines(f,'Cat',p.name+' is not with you any more',[],[lmvOpenChip('pet','Cat')]);
 const now=Date.now(),lines=[];
 try{lines.push('Level '+petLevel(p)+(p.trait?' · '+p.trait:''))}catch(e){}
 try{const can=petCanFeed(p,now);lines.push(can?'Hungry: ready for a meal':'Fed: next meal in about '+Math.max(1,Math.round(petNextMealIn(p,now)))+' h')}catch(e){}
 try{lines.push('Fed today: '+(p.fedToday||0)+' · petted today: '+(p.petsToday||0))}catch(e){}
 return lmvLines(f,'Cat',p.name+' is doing '+(p.vacation&&p.vacation.on?'fine on vacation':'well'),lines,[{label:'Feed',run:function(){lmvPetDo('feed')}},lmvOpenChip('pet','Cat')]);
}
function lmvPetDo(act){
 const f=lmvFrame||lmvNewFrame();lmvFrame=f;
 lmvHPetAct(f,{act:act});lmvRender();
}
function lmvHPetAct(f,cls){
 const p=typeof petData==='function'?petData():null;
 if(!p||!p.alive)return lmvLines(f,'Cat','You do not have a cat to look after',[],[lmvOpenChip('pet','Cat')]);
 if(cls.act==='feed'){
  const r=petFeed();
  if(!r.ok){const why=r.why==='vacation'?'Vacation mode is on. Turn it off to feed '+p.name+'.':r.why==='full'?p.name+' is full. Next meal in about '+Math.max(1,Math.round(r.inHours||1))+' h.':'There is no cat to feed.';return lmvLines(f,'Cat','Not now',[why],[lmvOpenChip('pet','Cat')])}
  try{save()}catch(e){}try{toast('Fed '+p.name)}catch(e){}
  return lmvLines(f,'Cat','Fed '+p.name,[r.levelUp?p.name+' leveled up!':'A good meal.'],[lmvOpenChip('pet','Cat')]);
 }
 if(cls.act==='pet'){
  const r=petPetDone();try{save()}catch(e){}
  return lmvLines(f,'Cat','You petted '+p.name,[r.counted===false?p.name+' has had plenty of petting today.':'Purring.'],[lmvOpenChip('pet','Cat')]);
 }
 lmvGo({id:'pet'});
 return lmvLines(f,'Cat','Opening the cat room',[p.name+' is waiting.'],[]);
}
function lmvStepMap(){
 let H=null;try{H=lmaHealthCfg()}catch(e){}
 const map={},S=(H&&H.snap)||{};(S.steps30||S.steps||[]).forEach(function(d){map[d.date]=d.count});
 if(H&&H.today&&H.today.date)map[H.today.date]=H.today.count;
 return {H:H,map:map,first:Object.keys(map).sort()[0]||null};
}
function lmvHSteps(f,scope,ctx){
 const sm=lmvStepMap(),H=sm.H,today=lmvToday(ctx),days=lmvDays(scope);
 const acts=lmvActsIn(scope),xp=lmvXpOf(acts),lines=[];
 const goal=(H&&H.goal)||8000;
 if(!(H&&H.on)){
  lines.push(acts.length+' logged activit'+(acts.length===1?'y':'ies')+' '+scope.label+' · '+xp+' XP','Connect Samsung Health on the Overview to count steps.');
  return lmvLines(f,'Activity','Step counting is not connected',lines,[lmvOpenChip('dashboard','Overview')]);
 }
 let head;
 if(scope.kind==='day'){
  const known=sm.map[scope.date]!=null,cnt=known?sm.map[scope.date]:0;
  if(!known&&sm.first&&scope.date<sm.first)return lmvLines(f,'Activity','I do not have steps that far back',['Steps are kept for the last 30 days.'],[lmvOpenChip('dashboard','Overview')]);
  if(scope.isFuture)return lmvLines(f,'Activity','That day has not happened yet',[],[]);
  head=cnt.toLocaleString()+' step'+(cnt===1?'':'s')+' '+scope.label;
  if(scope.date!==today)lines.push(scope.full);
  lines.push(Math.round(cnt/goal*100)+'% of your '+goal.toLocaleString()+' goal');
  lines.push(acts.length+' logged activit'+(acts.length===1?'y':'ies')+' '+scope.label+' · '+xp+' XP');
  if(scope.date!==today&&sm.map[today]!=null)lines.push('Today so far: '+sm.map[today].toLocaleString()+' steps');
 }else{
  const have=days.filter(function(d){return sm.map[d]!=null}),tot=have.reduce(function(a,d){return a+sm.map[d]},0),span=scope.kind==='all'?have.length:days.length;
  head=tot.toLocaleString()+' steps '+scope.label;
  lines.push('Average '+(span?Math.round(tot/span).toLocaleString():0)+' a day over '+span+' day'+(span===1?'':'s'));
  const best=have.slice().sort(function(a,b){return sm.map[b]-sm.map[a]})[0];if(best)lines.push('Best day: '+lmvNice(best)+' ('+sm.map[best].toLocaleString()+')');
  lines.push(acts.length+' logged activit'+(acts.length===1?'y':'ies')+' · '+xp+' XP');
 }
 return lmvLines(f,'Activity',head,lines,[lmvOpenChip('dashboard','Overview')]);
}
function lmvHWorkouts(f,scope,ctx){
 const isEx=function(a){try{if(ecoPassive(a)||a.type==='coach'||a.type==='quest')return false}catch(e){}return a.type==='strength'||a.type==='running'||a.type==='plan'||a.primary==='physique'||a.primary==='endurance'};
 const ex=lmvActsIn(scope).filter(isEx),mins=ex.reduce(function(s,a){return s+(Number(a.duration)||0)},0);
 if(scope.isFuture)return lmvLines(f,'Workouts','That day has not happened yet',[],[]);
 const head=ex.length?ex.length+' workout'+(ex.length===1?'':'s')+' '+scope.label+' · '+lmvDurText(mins):'No workouts logged '+scope.label;
 const lines=[];if(scope.kind==='day'&&scope.date!==lmvToday(ctx))lines.push(scope.full);
 ex.slice(0,8).forEach(function(a){lines.push((scope.kind==='day'?'':lmvNice(activityDate(a))+'  ')+lmvClock(new Date(a.date).getTime(),ctx.tz)+'  '+a.name+(a.duration?' · '+a.duration+' min':''))});
 if(!ex.length)lines.push('Nothing under strength, cardio or the workout plan.');
 return lmvLines(f,'Workouts',head,lines,[lmvOpenChip('plan','Workout plan'),lmvOpenChip('log','Log activity')]);
}
function lmvHFood(f,cls,scope,ctx){
 if(cls.water)return lmvLines(f,'Nutrition','Water is not tracked yet',['I can read calories and macros from your food log.'],[lmvOpenChip('meals','Food')]);
 if(scope.isFuture)return lmvLines(f,'Nutrition','That day has not happened yet',[],[]);
 const days=scope.kind==='all'?[]:lmvDays(scope);let entries=[];
 try{mealsEnsure();entries=scope.kind==='all'?state.foodLog.slice():state.foodLog.filter(function(x){return x.date>=scope.start&&x.date<=scope.end})}catch(e){}
 const t=entries.reduce(function(a,e){return {k:a.k+(Number(e.kcal)||0),p:a.p+(Number(e.p)||0),c:a.c+(Number(e.c)||0),f:a.f+(Number(e.f)||0)}},{k:0,p:0,c:0,f:0});
 const goal=Math.max(0,Number(state.mealsMeta&&state.mealsMeta.calGoal)||0);
 const span=Math.max(1,scope.kind==='day'?1:days.length||1);
 const only=cls.macro||'';
 let head;
 if(only==='protein')head=Math.round(t.p)+' g protein '+scope.label;else if(only==='carbs')head=Math.round(t.c)+' g carbs '+scope.label;else if(only==='fat')head=Math.round(t.f)+' g fat '+scope.label;
 else head=Math.round(t.k).toLocaleString()+' kcal '+scope.label+(goal&&scope.kind==='day'?' of '+goal.toLocaleString():'');
 const lines=[];if(scope.kind==='day'&&scope.date!==lmvToday(ctx))lines.push(scope.full);
 lines.push('Protein '+Math.round(t.p)+' g · carbs '+Math.round(t.c)+' g · fat '+Math.round(t.f)+' g');
 lines.push(entries.length+' item'+(entries.length===1?'':'s')+' logged'+(entries.length?'':' '+scope.label));
 if(scope.kind!=='day')lines.push('Average '+Math.round(t.k/span).toLocaleString()+' kcal a day');
 else if(goal&&entries.length)lines.push(t.k>goal?Math.round(t.k-goal)+' kcal over your goal':Math.round(goal-t.k)+' kcal left');
 return lmvLines(f,'Nutrition',head,lines,[lmvOpenChip('meals','Food')]);
}
/* ===== acting on an existing activity: complete (now), move / cancel (preview, then confirm) ===== */
function lmvPick(f,r,what){
 if(r.none)return lmvLines(f,'Voice','I could not find that activity',['Nothing matching on '+(what||'those days')+'. Say its name as it appears on your schedule.'],[lmvOpenChip('life-director','Life Director',true)]);
 if(r.many)return lmvLines(f,'Voice','Which one?',r.many.map(function(x){return x.title}),[]);
 return null;
}
function lmvHComplete(f,text,ctx){
 const t=lmvToday(ctx),r=lmvResolve(text.replace(/^.*?\b(?:complete|completed|finish|finished|done with|did|check off|checked off|mark|tick off|cross off|crossed off)\b/i,' ').replace(/\b(as done|as complete|as completed|done|complete|completed|off)\b/g,' '),ctx,[t]),p=lmvPick(f,r,'today');
 if(p)return p;
 const it=r.item;
 if(it.kind!=='SESSION')return lmvLines(f,'Voice',it.title,['That is a calendar item, not a session, so there is nothing to complete. You can move or cancel it.'],[]);
 const a=lmvLD.api(),res=a.completeSession(it.id);
 if(res.ok){lmvLastRef={id:it.id,title:it.title};return lmvLines(f,'Done','Marked “'+it.title+'” done',['XP and quests were updated.'],[lmvOpenChip('life-director','Life Director',true)])}
 return lmvLines(f,'Voice',res.reason==='NOT_STARTED'?'“'+it.title+'” has not started yet':'Could not complete “'+it.title+'”',[res.reason==='NOT_STARTED'?'You can mark it done once it starts, or open it in Life Director.':'Open it in Life Director to finish it there.'],[lmvOpenChip('life-director','Life Director',true)]);
}
function lmvActFrame(f,head,title,lines,label,run){
 f.state='PREVIEWED';f.query=false;f.slot=null;f.question=null;f.head=head;f.title=title;f.notices=[];f.conflicts=[];f.actions=[];
 f.act={lines:lines,label:label,run:run};
 return lmvSnapshot();
}
function lmvHCancel(f,text,ctx){
 const t=lmvToday(ctx),d=lmvDatesFor(text,ctx),body=text.replace(/^.*?\b(?:cancel|delete|remove|clear|take|drop|skip|scrap)\b/i,' ').replace(/\b(off|from|my schedule|my calendar|schedule|calendar|today|tomorrow)\b/gi,' ');
 const r=lmvResolve(body,ctx,d.dates),p=lmvPick(f,r,'your schedule');if(p)return p;
 const it=r.item,a=lmvLD.api(),when=lmvNiceDate(lmvYmd(lmvParts(it.startEpoch*1000,ctx.tz)))+' · '+lmvClock(it.startEpoch*1000,ctx.tz);
 return lmvActFrame(f,'Confirm','Remove “'+it.title+'”?',[when,it.kind==='SESSION'?'It will be skipped and the day re-planned.':'It will be taken off your schedule.'],'Confirm remove',function(){
  const res=it.kind==='SESSION'?a.skipSession(it.id):{ok:a.remove(it.id)};
  return res.ok?lmvLines(f,'Done','Removed “'+it.title+'”',[],[]):lmvLines(f,'Voice','Could not remove it',[],[]);
 });
}
function lmvHMove(f,text,ctx){
 const t=lmvToday(ctx),d=lmvDatesFor(text,ctx),a=lmvLD.api();
 const rel=/\b(\d+(?:\.\d+)?)\s*(hours?|hrs?|hr|h|minutes?|mins?|min|m)\b/.exec(lmvNormalize(text));
 const early=/\b(earlier|forward|ahead|sooner|bring forward)\b/.test(text.toLowerCase());
 const tail=lmvTimeAfter(lmvLow(text));
 let body=text.replace(/^.*?\b(?:move|reschedule|push back|push|bump|shift|postpone|delay|change the time of|bring forward)\b/i,' ');
 if(tail)body=body.slice(0,Math.max(0,body.toLowerCase().indexOf(tail.trim().slice(0,8))));
 body=body.replace(/\b(back|forward|later|earlier|by|ahead|sooner)\b/gi,' ').replace(/\d+(?:\.\d+)?\s*(?:hours?|hrs?|hr|h|minutes?|mins?|min|m)\b/gi,' ');
 const r=lmvResolve(body,ctx,d.dates),p=lmvPick(f,r,'your schedule');if(p)return p;
 const it=r.item,dur=it.endEpoch-it.startEpoch;let ns=null;
 if(rel&&!/\bto\s+\d|\bto (?:noon|midnight|tomorrow)/.test(text.toLowerCase())){const k=/^h/.test(rel[2])?3600:60;ns=it.startEpoch+(early?-1:1)*Math.round(+rel[1]*k)}
 else{
  const e=lmvExtract(tail||text,ctx,null),sp=lmvParts(it.startEpoch*1000,ctx.tz),date=e.date||lmvYmd(sp);
  if(e.hour==null&&e.daypart==null&&!e.date)return lmvLines(f,'Voice','Move it to when?',['Say a time, like “move gym to 6 PM”, or a shift, like “push it back 30 minutes”.'],[]);
  let h=e.hour,mi=e.minute||0;
  if(h==null){const dp=lmvDaypartDefault(e.daypart);h=Math.floor(dp/60);mi=dp%60}
  else if(e.mer==='am'||e.mer==='pm')h=lmvTo24(h,e.mer);
  else if(h<=12&&h>=1){const g=lmvGuess(it.title,h)||(sp.h>=12?'pm':'am');h=lmvTo24(h,g)}
  const q=date.split('-').map(Number);ns=Math.floor(lmvLocalToEpoch(q[0],q[1],q[2],h%24,mi,ctx.tz).epochMs/1000);
 }
 const ne=ns+dur,from=lmvClock(it.startEpoch*1000,ctx.tz),to=lmvClock(ns*1000,ctx.tz),dayTo=lmvNiceDate(lmvYmd(lmvParts(ns*1000,ctx.tz)));
 const clash=(function(){try{return a.overlaps(ns,ne).filter(function(x){return x.kind!=='SLEEP'&&x.title!==it.title})}catch(e){return []}})();
 return lmvActFrame(f,'Confirm','Move “'+it.title+'”?',[from+'  →  '+to+'  ('+dayTo+')'].concat(clash.length?['Overlaps '+clash.map(function(c){return c.title}).join(', ')+'.']:[]),'Move it',function(){
  const res=it.kind==='SESSION'?a.moveSession(it.id,ns):a.moveCommitment(it.id,ns,ne);
  if(res.ok)lmvLastRef={id:it.id,title:it.title};
  return res.ok?lmvLines(f,'Done','Moved “'+it.title+'”',[from+'  →  '+to],[lmvOpenChip('life-director','Life Director',true)]):lmvLines(f,'Voice','Could not move it',[res.reason==='PLAN_REJECTED'?'Life Director could not fit it there.':'Try a different time.'],[]);
 });
}
