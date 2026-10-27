/* ---------- LifeMax Voice generation 5: a day that goes off-script.
   "Don't cancel my workout" never cancels it; "push everything back 30 minutes", "swap X and Y", "clear my afternoon";
   "if I have an hour free this afternoon, schedule guitar"; "the second one" and "what's left here"; sets, reps, weight and
   distance; several foods in one sentence, what is left of a goal, water in glasses and bottles; cats by name; macros you teach
   it; and a spoken Undo that works after the card has gone. Plain web code that wraps segments 45, 46, 49 and 50 (gen 4). ---------- */

let g5List=null,g5Batch=0,g5InUndo=0;
const G5_UNDO='lifemax_voice_undo_v1',G5_MACRO='lifemax_voice_macros_v1';

/* ===== the undo stack: every voice change that can be reversed leaves a small record behind ===== */
function g5Stack(){try{const a=JSON.parse(localStorage.getItem(G5_UNDO)||'[]');return Array.isArray(a)?a:[]}catch(e){return []}}
function g5Save(a){try{localStorage.setItem(G5_UNDO,JSON.stringify(a.slice(-25)))}catch(e){}}
function g5Push(rec){if(g5InUndo)return;rec.ts=Date.now();const a=g5Stack();a.push(rec);g5Save(a)}
function g5Drop(fn){g5Save(g5Stack().filter(function(r){return !fn(r)}))}
function g5Eps(){try{return LifeDirectorApp._debug().ST}catch(e){return null}}
function g5UndoApply(r){
 const a=lmvLD.api();g5InUndo++;
 try{
  if(r.t==='ld_remove'){
   if(!a||!a.find(r.id))return {gone:true};
   const ok=a.remove(r.id);const j=r.op?lmvJGet(r.op):null;if(j){j.status='UNDONE';j.undone=true;lmvJPut(j)}return {ok:!!ok};
  }
  if(r.t==='ld_restore'){const ST=g5Eps();if(!ST)return {ok:false};r.items.forEach(function(c){if(!(ST.fixedCommitments||[]).some(function(x){return String(x.id)===String(c.id)}))ST.fixedCommitments.push(c)});try{LifeDirector.regenerate('voice undo')}catch(e){}return {ok:true}}
  if(r.t==='ld_move'){
   const ST=g5Eps();let all=true;
   r.items.slice().reverse().forEach(function(m){const res=m.k==='C'?a.moveCommitment(m.id,m.s,m.e):a.moveSession(m.id,m.s);if(!(res&&res.ok))all=false});
   return {ok:all};
  }
  if(r.t==='act_remove'){if(!state.activities.some(function(x){return x.id===r.id}))return {gone:true};removeAct(r.id);try{save()}catch(e){}return {ok:true}}
  if(r.t==='food_remove'){const n=(state.foodLog||[]).length;state.foodLog=(state.foodLog||[]).filter(function(x){return r.ids.indexOf(x.id)<0});try{save()}catch(e){}return state.foodLog.length<n?{ok:true}:{gone:true}}
  if(r.t==='water_remove'){const n=(state.waterLog||[]).length;state.waterLog=(state.waterLog||[]).filter(function(x){return x.id!==r.id});try{save()}catch(e){}return state.waterLog.length<n?{ok:true}:{gone:true}}
  if(r.t==='quest_undo'){const q=(state.quests||[]).find(function(x){return x.id===r.id});if(!q||!isQuestDone(q))return {gone:true};toggleQuest(r.id,false);return {ok:true}}
  if(r.t==='plan_undo'){try{r.idx.forEach(function(i){planToggle(r.date,r.key,i,false)});return {ok:true}}catch(e){return {ok:false}}}
  if(r.t==='goal'){state.mealsMeta=state.mealsMeta||{};if(r.old==null)delete state.mealsMeta[r.key];else state.mealsMeta[r.key]=r.old;try{save()}catch(e){}return {ok:true}}
  return {ok:false,irreversible:true};
 }catch(e){console.warn('[voice undo]',e);return {ok:false}}
 finally{g5InUndo--}
}
function g5DoUndo(f){
 for(let k=0;k<25;k++){
  const a=g5Stack(),r=a.pop();g5Save(a);
  if(!r)return lmvLines(f,'Undo','Nothing to undo',['I have no recent change of mine to take back.'],[]);
  const res=g5UndoApply(r);
  if(res.gone)continue;
  if(res.irreversible)return lmvLines(f,'Undo','I cannot undo that one',['“'+r.label+'” cannot be reversed from here. Open the app to change it.'],[]);
  if(res.ok){lmvLines(f,'Undo','Undid: '+r.label+'.',['It is back the way it was.'],[]);return lmvSnapshot()}
  return lmvLines(f,'Undo','Could not undo “'+r.label+'”',['Something has changed since. Open the app to check.'],[]);
 }
 return lmvLines(f,'Undo','Nothing to undo',[],[]);
}
/* record what the Life Director seam does, so one place covers every voice move, removal and creation */
function g5HookLD(){
 const a=lmvLD.api();if(!a||a.__g5)return;try{
  const mc=a.moveCommitment,ms=a.moveSession,rm=a.remove,sk=a.skipSession,cs=a.completeSession;
  a.moveCommitment=function(id,s,e){const c=a.find(id),old=c?{id:String(c.id),k:'C',s:c.startEpoch,e:c.endEpoch,t:c.title}:null;const r=mc.apply(a,arguments);if(r&&r.ok&&old&&!g5Batch)g5Push({t:'ld_move',items:[old],label:'Moved “'+old.t+'”'});return r};
  a.moveSession=function(id,s){const ST=g5Eps(),i=ST&&(ST.schedule.scheduledInstances||[]).find(function(x){return x.instanceId===id}),old=i?{id:id,k:'S',s:i.startEpoch,t:i.title}:null;const r=ms.apply(a,arguments);if(r&&r.ok&&old&&!g5Batch)g5Push({t:'ld_move',items:[old],label:'Moved “'+old.t+'”'});return r};
  a.remove=function(id){const c=a.find(id),copy=c?JSON.parse(JSON.stringify(c)):null;const r=rm.apply(a,arguments);
   if(r&&copy&&!g5InUndo&&!g5Batch){const made=g5Stack().some(function(x){return x.t==='ld_remove'&&String(x.id)===String(id)});if(made)g5Drop(function(x){return x.t==='ld_remove'&&String(x.id)===String(id)});else g5Push({t:'ld_restore',items:[copy],label:'Removed “'+(copy.title||'an item')+'”'})}
   return r};
  a.skipSession=function(id){const ST=g5Eps(),i=ST&&(ST.schedule.scheduledInstances||[]).find(function(x){return x.instanceId===id}),t=i?i.title:'a session';const r=sk.apply(a,arguments);if(r&&r.ok&&!g5Batch)g5Push({t:'irreversible',label:'Skipped “'+t+'”'});return r};
  a.completeSession=function(id){const ST=g5Eps(),i=ST&&(ST.schedule.scheduledInstances||[]).find(function(x){return x.instanceId===id}),t=i?i.title:'a session';const r=cs.apply(a,arguments);if(r&&r.ok&&!g5Batch)g5Push({t:'irreversible',label:'Marked “'+t+'” done'});return r};
  a.__g5=true;
 }catch(e){console.warn('[voice g5 hook]',e)}
}
(function(){
 const ex=lmvExecute;lmvExecute=function(f){const r=ex.apply(this,arguments);try{if(f&&f.state==='CONFIRMED'&&f.entityId&&f.title&&!g5Stack().some(function(x){return x.t==='ld_remove'&&x.id===String(f.entityId)}))g5Push({t:'ld_remove',id:String(f.entityId),op:f.operationId,label:'Scheduled “'+f.title+'”'})}catch(e){}return r};
 const un=lmvUndo;lmvUndo=function(){const f=lmvFrame,id=f&&f.entityId;g5InUndo++;let r;try{r=un.apply(this,arguments)}finally{g5InUndo--}if(r&&id)g5Drop(function(x){return x.t==='ld_remove'&&String(x.id)===String(id)});return r};
 const ll=lmvLogNow;lmvLogNow=function(o){
  const d=lmvFrame&&lmvFrame.g5detail;if(d)o=Object.assign({},o,{name:d.name});
  const r=ll.call(this,o);
  try{if(r&&r.ok){const a=state.activities.find(function(x){return x.id===r.id});if(a){if(d){a.detail=d.detail;a.notes='Logged by voice · '+d.sum}g5Push({t:'act_remove',id:r.id,label:'Logged “'+a.name+'”'})}}}catch(e){}
  return r;
 };
})();

/* ===== times, windows and gaps ===== */
function g5Mins(h,mi,mer){h=+h;mi=+mi||0;if(mer==='am')h=h===12?0:h;else if(mer==='pm')h=h===12?12:h+12;else if(h<=7)h+=12;return h*60+mi}
function g5ClockRe(){return '(\\d{1,2})(?::(\\d{2}))? ?(am|pm)?'}
const G5_DP={morning:[6*60,12*60],afternoon:[12*60,17*60],evening:[17*60,22*60],night:[20*60,24*60],tonight:[19*60,24*60],lunch:[12*60,14*60],lunchtime:[12*60,14*60]};
/* "but not in the morning", "except between 12 and 2 PM", "anytime after 11 AM": windows to avoid, and the sentence without them */
function g5Constraints(raw){
 let t=String(raw||''),L=t.toLowerCase(),excl=[],notes=[];
 const cut=function(re,fn){let m;while((m=re.exec(L))){const s=m.index,e=s+m[0].length;fn(m);t=t.slice(0,s)+' '+t.slice(e);L=L.slice(0,s)+' '+L.slice(e);re.lastIndex=0}};
 const C=g5ClockRe();
 cut(new RegExp(',?\\s*(?:but |and )?(?:not|except|excluding|other than|avoiding|avoid|skipping|skip) (?:between|from) '+C+' ?(?:and|to|-) '+C+'\\b','i'),function(m){const a=g5Mins(m[1],m[2],m[3]||m[6]),b=g5Mins(m[4],m[5],m[6]||m[3]);excl.push([Math.min(a,b),Math.max(a,b)]);notes.push('skipping '+lmvNiceTime(g5Hm(Math.min(a,b)))+' to '+lmvNiceTime(g5Hm(Math.max(a,b))))});
 cut(/,?\s*(?:but |and )?(?:not|except|excluding|other than|avoiding|avoid|skipping|skip) (?:in |during |around |at |over )?(?:the )?(morning|afternoon|evening|night|lunch(?:time)?)\b/i,function(m){const w=G5_DP[m[1].toLowerCase()];excl.push(w.slice());notes.push('avoiding the '+m[1].toLowerCase())});
 cut(new RegExp(',?\\s*(?:but |and )?(?:not|except|excluding|avoiding|avoid) (before|after) '+C+'\\b','i'),function(m){const x=g5Mins(m[2],m[3],m[4]);excl.push(m[1].toLowerCase()==='before'?[0,x]:[x,1440]);notes.push('not '+m[1].toLowerCase()+' '+lmvNiceTime(g5Hm(x)))});
 if(!/\b(?:at|from|around|by|@)\s*\d/i.test(L)&&!/\bto \d/.test(L))cut(new RegExp('\\b(?:anytime|any time|whenever|sometime)?\\s*(after|before|from) '+C+'(?! ?(?:min|hour|hr|h\\b))\\b','i'),function(m){if(/^(?:after|before) work/.test(m[0]))return;const x=g5Mins(m[2],m[3],m[4]);excl.push(m[1].toLowerCase()==='before'?[x,1440]:[0,x]);notes.push(m[1].toLowerCase()==='before'?'before '+lmvNiceTime(g5Hm(x)):'after '+lmvNiceTime(g5Hm(x)))});
 t=t.replace(/\b(?:anytime|any time|sometime)\b/gi,' ');
 return {text:t.replace(/\s+/g,' ').trim(),excl:excl,notes:notes};
}
function g5Hm(m){m=Math.max(0,Math.min(1439,m));return lmvPad(Math.floor(m/60))+':'+lmvPad(m%60)}
function g5Gaps(date,ctx,a,b,excl,minLen){
 const busy=lmvBusy(date,ctx);if(!busy)return null;
 const p=date.split('-').map(Number),s0=Math.floor(lmvLocalToEpoch(p[0],p[1],p[2],0,0,ctx.tz).epochMs/1000);
 let from=a;if(date===lmvToday(ctx))from=Math.max(from,Math.ceil(lmvNowMin(ctx,0)/5)*5);
 let iv=busy.map(function(x){return [Math.round((x[0]-s0)/60),Math.round((x[1]-s0)/60)]});
 (excl||[]).forEach(function(w){iv.push([w[0],w[1]])});
 iv=iv.filter(function(x){return x[1]>from&&x[0]<b}).sort(function(x,y){return x[0]-y[0]});
 const gaps=[];let cur=from;
 iv.forEach(function(x){if(x[0]>cur)gaps.push([cur,Math.min(x[0],b)]);cur=Math.max(cur,x[1])});
 if(cur<b)gaps.push([cur,b]);
 return gaps.filter(function(g){return g[1]-g[0]>=(minLen||5)});
}
function g5Roundup(m){return Math.ceil(m/5)*5}
function g5T(m){return lmvNiceTime(g5Hm(m))}
/* a preview card for a title on a date at a minute of the day, built the normal way so every check still runs */
function g5Preview(clean,meta,ctx,title,date,startMin,dur,note){
 const f=lmvFrame=lmvNewFrame();f.transcript=clean;f.confidence=meta&&meta.confidence!=null?meta.confidence:1;
 lmvMerge(f,{title:title,date:date,hour:Math.floor(startMin/60)%24,minute:startMin%60,mer:'24',dur:dur,daypart:null,anchor:null,endHour:null,endMinute:0,endMer:null});
 lmvAdvance(f,ctx);if(f.state==='PREVIEWED'&&note)f.notices.unshift({kind:'info',text:note});
 lmvRender();return lmvSnapshot();
}
/* "schedule reading tomorrow, not in the morning": the title, day and length are used; the time is found */
function g5AutoSchedule(clean,meta,ctx,cons){
 const ex=lmvExtract(cons.text,ctx,null);
 if(ex.hour!=null||ex.anchor||!ex.title)return null;
 const today=lmvToday(ctx),date=ex.date||today,dur=ex.dur||LMV_DEFAULT_MIN;
 let a=7*60,b=22*60;if(ex.daypart&&G5_DP[ex.daypart]){a=G5_DP[ex.daypart][0];b=G5_DP[ex.daypart][1]}
 const gaps=g5Gaps(date,ctx,a,b,cons.excl,dur);
 if(gaps===null)return lmvAfterNoLD(clean);
 const g=gaps.filter(function(x){return x[1]-g5Roundup(x[0])>=dur})[0];
 if(!g){const f=lmvFrame=lmvNewFrame();f.transcript=clean;const best=(g5Gaps(date,ctx,a,b,cons.excl,5)||[]).sort(function(x,y){return (y[1]-y[0])-(x[1]-x[0])})[0];
  lmvLines(f,'Schedule','No '+lmvDurText(dur)+' slot fits',[best?'The longest open stretch is '+lmvDurText(best[1]-g5Roundup(best[0]))+' from '+g5T(g5Roundup(best[0]))+' to '+g5T(best[1])+'.':'Nothing is open then.'],[]);lmvRender();return lmvSnapshot()}
 return g5Preview(clean,meta,ctx,ex.title.charAt(0).toUpperCase()+ex.title.slice(1),date,g5Roundup(g[0]),dur,'Picked '+g5T(g5Roundup(g[0]))+', '+(cons.notes.join(' and ')||'a free slot')+'.');
}
function lmvAfterNoLD(clean){const f=lmvFrame=lmvNewFrame();f.transcript=clean;lmvLines(f,'Schedule','Life Director is not set up yet',['Open Life Director to set your sleep and work hours first.'],[lmvOpenChip('life-director','Life Director',true)]);lmvRender();return lmvSnapshot()}

/* ===== negation: "don't cancel my workout" must never cancel it ===== */
const G5_NEG=/^ (?:(?:please|hey|ok|okay|no|wait|stop|oh|um|uh|hmm|lifemax|life max|and|but|so|actually),? )*(?:(?:i )?(?:don'?t|do not|dont|do n'?t) (?:want|wanna|need)(?: you)? to|(?:please )?(?:don'?t|do not|dont|never|avoid|do n'?t))\b/;
function g5Negated(clean){
 const s=lmvLead(lmvLow(clean));
 if(!G5_NEG.test(' '+s.trim()+' '))return null;
 if(/\b(?:don'?t|do not|dont) (?:forget|let me forget|let me miss)\b/.test(s)||/^ (?:never ?mind|never mind)\b/.test(s))return null;
 if(/\b(?:don'?t|do not|dont) know\b/.test(s)||/\b(?:don'?t|do not|dont) (?:have|see|think|get|understand|remember|feel|like)\b/.test(s))return null;
 return true;
}
function g5Forget(clean){return clean.replace(/^\s*(?:please\s+)?(?:don'?t|do not|dont) (?:forget|let me forget) (?:to )?/i,'')}

/* ===== list and screen memory ===== */
function g5QuestList(left){
 if(!Array.isArray(state.quests))return [];
 const mk=function(q){let done=false;try{done=isQuestDone(q)}catch(e){}let locked=false;try{locked=questLocked(q)}catch(e){}return {q:q,done:done,locked:locked}};
 const day=state.quests.filter(function(q){return q.cadence==='daily'&&(typeof questToday!=='function'||questToday(q))}).map(mk),week=state.quests.filter(function(q){return q.cadence==='weekly'}).map(mk);
 let all=day.concat(week).filter(function(r){return !r.locked});
 if(left)all=all.filter(function(r){return !r.done});
 return all.slice(0,10).map(function(r){return {id:r.q.id,title:r.q.name,q:r.q,done:r.done}});
}
function g5PlanList(){
 try{if(!state.plan)return [];const d=planDay(getToday());if(!d||d.kind==='rest')return [];const c=pcheck(getToday());
  return (d.items||[]).map(function(it){return {id:it.k,title:it.l,it:it,date:getToday(),done:((c.s||{})[it.k]||[]).filter(Boolean).length>=it.n}})}catch(e){return []}
}
function g5SchedList(date,ctx,dp){
 const a=lmvLD.api();let items=null;try{items=a&&a.ready()?a.dayItems(date):null}catch(e){}if(!items)return [];
 const w=dp&&lmvDaypartWin(dp);if(w)items=items.filter(function(it){const p=lmvParts(it.startEpoch*1000,ctx.tz),m=p.h*60+p.mi;return m>=w[0]&&m<w[1]});
 const sess=a.sessions(date),com=a.commitments(date);
 return items.slice(0,10).map(function(it){const h=sess.concat(com).find(function(x){return x.title===it.title&&x.startEpoch===it.startEpoch});return {id:h?h.id:null,title:it.title,kind:h?h.kind:it.kind,startEpoch:it.startEpoch,endEpoch:it.endEpoch}});
}
(function(){
 const q=lmvHQuests;lmvHQuests=function(f,cls){const r=q.apply(this,arguments);try{g5List={kind:'quests',items:g5QuestList(!!cls.left),ts:Date.now()}}catch(e){}return r};
 const s=lmvHSchedule;lmvHSchedule=function(f,text,ctx,scope,cls){const r=s.apply(this,arguments);try{const today=lmvToday(ctx),hist=(cls&&cls.history)||(scope.kind==='day'&&scope.date<today)||scope.kind==='range'||scope.kind==='all';g5List=hist?null:{kind:'schedule',date:scope.date,items:g5SchedList(scope.date,ctx,scope.dayPart),ts:Date.now()}}catch(e){}return r};
 const p=g4Plan;g4Plan=function(f,cls){const r=p.apply(this,arguments);try{g5List={kind:'plan',items:g5PlanList(),ts:Date.now()}}catch(e){}return r};
})();
const G5_ORD={first:0,second:1,third:2,fourth:3,fifth:4,sixth:5,seventh:6,eighth:7,ninth:8,tenth:9,last:-1,final:-1};
const G5_ORDRE='first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|last|final|number \\d+|#\\d+|\\d+(?:st|nd|rd|th)';
function g5OrdIndex(w,len){w=w.trim();if(w in G5_ORD){const i=G5_ORD[w];return i<0?len-1:i}const m=/\d+/.exec(w);return m?+m[0]-1:-1}
function g5Screen(){const v=lmaView();return v==='media'||v==='history'?'library':v==='meals'||v==='recipes'?'food':v}
function g5NumWords(s){return s.replace(/\b(?:number|no\.?|#) ?(one|two|three|four|five|six|seven|eight|nine|ten)\b/g,function(m,w){return 'number '+({one:1,two:2,three:3,four:4,five:5,six:6,seven:7,eight:8,nine:9,ten:10})[w]})}
function g5Ordinal(f,s,clean,ctx){
 s=g5NumWords(s);const m=new RegExp('^ (?:please |hey |ok |okay )*(complete|finish|check off|check|tick off|mark|do|done with|uncheck|cancel|delete|remove|skip|move|reschedule|push|tell me more about|more about|more on|open|show|read)(?: off)? (?:me )?(?:the |my |item |number )?('+G5_ORDRE+')\\b(.*)$').exec(s);
 if(!m)return null;
 const verb=m[1],ord=m[2],rest=m[3]||'';
 const hint=/\b(?:in|on|from|of|under) (?:my |the |today'?s )?(quests?|workout plan|plan|schedule|calendar|library)\b/.exec(rest),scr=g5Screen();
 let list=null;
 if(hint){const h=hint[1];list=/quest/.test(h)?{kind:'quests',items:g5QuestList(false)}:/plan/.test(h)?{kind:'plan',items:g5PlanList()}:/schedule|calendar/.test(h)?{kind:'schedule',date:lmvToday(ctx),items:g5SchedList(lmvToday(ctx),ctx)}:null}
 if(!list&&g5List&&Date.now()-g5List.ts<10*60000&&lmvFrame&&lmvFrame.state==='CONFIRMED'&&lmvFrame.query)list=g5List;
 if(!list&&scr==='quests')list={kind:'quests',items:g5QuestList(false)};
 if(!list&&scr==='plan')list={kind:'plan',items:g5PlanList()};
 if(!list&&g5List&&Date.now()-g5List.ts<10*60000)list=g5List;
 if(!list)return false;
 const i=g5OrdIndex(ord,list.items.length),it=list.items[i];
 if(!it){lmvLines(f,'Voice',list.items.length?'There are only '+list.items.length+' in that list':'That list is empty',[],[]);return true}
 const done=/^(?:complete|finish|check off|check|tick off|mark|do|done with)$/.test(verb)||/\b(?:as )?(?:done|complete|completed|finished)\b/.test(rest)&&verb==='mark';
 if(list.kind==='quests'){
  if(done||verb==='uncheck'){
   const on=verb!=='uncheck',was=isQuestDone(it.q);
   if(on&&was){lmvLines(f,'Quests','“'+it.title+'” is already done',[],[]);return true}
   toggleQuest(it.id,on);try{save()}catch(e){}
   if(on)g5Push({t:'quest_undo',id:it.id,label:'Completed quest “'+it.title+'”'});
   g5List=Object.assign({},list,{items:g5QuestList(false),ts:Date.now()});
   lmvLines(f,on?'Done':'Quests',(on?'Checked off “':'Unchecked “')+it.title+'”',[on?'XP and the streak were updated.':'Its XP was taken back.'],[lmvOpenChip('quests','Quests')]);return true;
  }
  if(/more|read|show|open/.test(verb)){lmvLines(f,'Quest',it.title,[it.q.desc||'No description.',(it.q.xp?it.q.xp+' XP · ':'')+(it.q.cadence||'daily'),it.done?'Done':'Not done yet'],[lmvOpenChip('quests','Quests')]);return true}
  lmvLines(f,'Quests','I can check quests off or on',['Say “complete the first one” or “tell me more about number two”.'],[]);return true;
 }
 if(list.kind==='plan'){
  if(done||verb==='uncheck'){
   if(!state.plan){lmvLines(f,'Plan','No active workout plan',[],[]);return true}
   const on=verb!=='uncheck',n=it.it.n||1,idx=[];for(let k=0;k<n;k++)idx.push(k);
   const before=((pcheck(it.date).s||{})[it.id]||[]).map(Boolean);
   if(on){idx.forEach(function(k){if(!before[k]){planToggle(it.date,it.id,k,true)}});g5Push({t:'plan_undo',date:it.date,key:it.id,idx:idx.filter(function(k){return !before[k]}),label:'Marked “'+it.title+'” done'})}
   else idx.forEach(function(k){planToggle(it.date,it.id,k,false)});
   try{save()}catch(e){}
   lmvLines(f,on?'Done':'Plan',(on?'Marked “':'Unmarked “')+it.title+'”'+(on?' done':''),[n+' set'+(n===1?'':'s')+(on?' checked':' cleared')],[lmvOpenChip('plan','Workout plan')]);return true;
  }
  lmvLines(f,'Exercise',it.title,[it.it.d||'',(it.it.n||1)+' set'+((it.it.n||1)===1?'':'s'),it.done?'Done':'Not done yet'].filter(Boolean),[lmvOpenChip('plan','Workout plan')]);return true;
 }
 if(list.kind==='schedule'){
  const tl=it.title;
  if(done){return lmvHComplete(f,'complete '+tl,ctx),true}
  if(/^(cancel|delete|remove|skip)$/.test(verb)){lmvHCancel(f,'cancel '+tl,ctx);return true}
  if(/^(move|reschedule|push)$/.test(verb)){const tail=/\b(?:to|until|at|by|for|back|forward|later|earlier)\b.*$/.exec(rest);if(!tail){lmvLines(f,'Voice','Move it to when?',['Say a time, like “move the last one to 5 PM”.'],[]);return true}lmvHMove(f,'move '+tl+' '+tail[0],ctx);return true}
  lmvLines(f,'Schedule',tl,[lmvClock(it.startEpoch*1000,ctx.tz)+' – '+lmvClock(it.endEpoch*1000,ctx.tz)],[lmvOpenChip('life-director','Life Director',true)]);return true;
 }
 return false;
}
function g5ScreenSummary(f,ctx){
 const v=g5Screen(),sc=lmvScope('today',ctx,false);
 if(v==='quests')return lmvHQuests(f,{left:true});
 if(v==='plan')return g4Plan(f,{act:'show'});
 if(v==='dashboard'){const b=g4Brief(f,ctx);return lmvLines(f,'Overview',b.g.xp+' XP today',b.lines,[])}
 if(v==='character')return lmvHStats(f,{metric:'',text:'my stats'},sc,ctx);
 if(v==='food')return lmvHFood(f,{},sc,ctx);
 if(v==='pet')return lmvHPetStatus(f,{});
 if(v==='library'){const items=Array.isArray(state.mediaItems)?state.mediaItems:[];return lmvLines(f,'Library',items.length+' item'+(items.length===1?'':'s'),[items.filter(function(x){return x.status==='progress'}).length+' in progress · '+items.filter(function(x){return x.status==='finished'}).length+' finished · '+items.filter(function(x){return x.status==='unread'}).length+' to start'],[])}
 if(v==='log')return lmvLines(f,'Log activity','This is where you log what you did',['Say “log a 30 minute run” or “log bench press 3 sets of 10 at 135 pounds”.'],[]);
 if(v==='life-director'||/director/.test(v))return g4Handle({id:'life_director.whats_next'},'',f,ctx);
 return lmvLines(f,'Voice','This is the '+(v||'current')+' screen',['Ask about it by name, or say “what can I say”.'],[]);
}

/* ===== bulk changes to a day ===== */
function g5Movables(date,ctx){
 const a=lmvLD.api();if(!a||!a.ready())return null;let out=[];
 try{a.sessions(date).forEach(function(x){out.push({id:x.id,title:x.title,startEpoch:x.startEpoch,endEpoch:x.endEpoch,k:'S'})})}catch(e){}
 try{a.commitments(date).forEach(function(x){const c=a.find(x.id);if(c&&c.kind&&c.kind!=='WORK'&&c.kind!=='SLEEP')out.push({id:x.id,title:x.title,startEpoch:x.startEpoch,endEpoch:x.endEpoch,k:'C',raw:c})})}catch(e){}
 return out.sort(function(x,y){return x.startEpoch-y.startEpoch});
}
function g5ShiftParse(s){
 const n=lmvNormalize(s);let m=/(\d+(?:\.\d+)?) ?(minutes?|mins?|min|hours?|hrs?|hr|h)\b/.exec(n);
 let amt=m?Math.round(+m[1]*(/^h/.test(m[2])?60:1)):null;
 if(amt==null&&/\ban hour\b/.test(s))amt=60;if(amt==null&&/\bhalf an hour\b/.test(s))amt=30;
 const early=/\b(?:earlier|ahead|sooner|up)\b/.test(s)&&!/\blate\b/.test(s);
 return {amt:amt,sign:early?-1:1};
}
function g5Shift(f,s,clean,ctx){
 const today=lmvToday(ctx),pr=g5ShiftParse(s);
 if(!pr.amt)return lmvLines(f,'Schedule','How much?',['Say “push everything back 30 minutes” or “I am running 20 minutes late”.'],[]);
 const tm=/\btomorrow\b/.test(s),date=tm?lmvAddDays(today,1):today,dp=/\bafternoon\b/.test(s)?'afternoon':/\bmorning\b/.test(s)?'morning':/\bevening\b/.test(s)?'evening':null;
 let items=g5Movables(date,ctx);if(items===null)return lmvLines(f,'Schedule','Life Director is not set up yet',[],[lmvOpenChip('life-director','Life Director',true)]);
 const now=Math.floor(ctx.now/1000);
 if(date===today)items=items.filter(function(x){return x.endEpoch>now});
 if(dp){const w=lmvDaypartWin(dp);items=items.filter(function(it){const p=lmvParts(it.startEpoch*1000,ctx.tz),m=p.h*60+p.mi;return m>=w[0]&&m<w[1]})}
 if(!items.length)return lmvLines(f,'Schedule','Nothing left to move',['There is nothing planned '+(date===today?'for the rest of today':'then')+' that can move.'],[]);
 const d=pr.amt*pr.sign*60,lines=items.slice(0,8).map(function(x){return x.title+': '+lmvClock(x.startEpoch*1000,ctx.tz)+' → '+lmvClock((x.startEpoch+d)*1000,ctx.tz)});
 if(items.length>8)lines.push('and '+(items.length-8)+' more');
 const a=lmvLD.api();let clash=[];try{clash=items.some(function(x){return a.overlaps(x.startEpoch+d,x.endEpoch+d).some(function(o){return o.kind==='SLEEP'||o.kind==='WORK'})})}catch(e){}
 if(clash)lines.push('Careful: some of it would run into sleep or work time.');
 const label=pr.sign>0?'later':'earlier';
 return lmvActFrame(f,'Confirm','Move '+items.length+' thing'+(items.length===1?'':'s')+' '+lmvDurText(pr.amt)+' '+label+'?',lines,'Shift them',function(){return g5ApplyShift(f,items,d)});
}
function g5ApplyShift(f,items,d){
 const a=lmvLD.api(),done=[],order=d>0?items.slice().reverse():items.slice();let ok=true;g5Batch++;
 try{
  order.forEach(function(x){if(!ok)return;const r=x.k==='C'?a.moveCommitment(x.id,x.startEpoch+d,x.endEpoch+d):a.moveSession(x.id,x.startEpoch+d);if(r&&r.ok)done.push(x);else ok=false});
  if(!ok){done.slice().reverse().forEach(function(x){x.k==='C'?a.moveCommitment(x.id,x.startEpoch,x.endEpoch):a.moveSession(x.id,x.startEpoch)});return lmvLines(f,'Voice','Could not shift the day',['Life Director could not fit that, so nothing was changed.'],[])}
 }finally{g5Batch--}
 g5Push({t:'ld_move',items:items.map(function(x){return {id:x.id,k:x.k,s:x.startEpoch,e:x.endEpoch,t:x.title}}),label:'Shifted '+items.length+' thing'+(items.length===1?'':'s')+' by '+lmvDurText(Math.abs(d)/60)});
 return lmvLines(f,'Done','Moved '+items.length+' thing'+(items.length===1?'':'s'),[items.length+' shifted by '+lmvDurText(Math.abs(d)/60)+'.','Say “undo” to put them back.'],[lmvOpenChip('life-director','Life Director',true)]);
}
function g5Swap(f,s,clean,ctx){
 const m=/^ (?:swap|switch|exchange|trade|flip)(?: out)? (?:my |the )?(.+?) (?:and|with|for|&) (?:my |the )?(.+?)(?: (?:today|tomorrow|on .+|this .+))? ?$/.exec(s);if(!m)return null;
 const d=lmvDatesFor(clean,ctx),ra=lmvResolve(m[1],ctx,d.dates),rb=lmvResolve(m[2].replace(/\b(today|tomorrow|tonight)\b/g,' '),ctx,d.dates);
 const p1=lmvPick(f,ra,'your schedule');if(p1)return p1;const p2=lmvPick(f,rb,'your schedule');if(p2)return p2;
 const A=ra.item,B=rb.item;
 if(String(A.id)===String(B.id))return lmvLines(f,'Voice','That is the same activity twice',['Name two different things to swap.'],[]);
 const ns={a:B.startEpoch,b:A.startEpoch},da=A.endEpoch-A.startEpoch,db=B.endEpoch-B.startEpoch;
 const lines=[A.title+': '+lmvClock(A.startEpoch*1000,ctx.tz)+' → '+lmvClock(ns.a*1000,ctx.tz),B.title+': '+lmvClock(B.startEpoch*1000,ctx.tz)+' → '+lmvClock(ns.b*1000,ctx.tz),'Each keeps its own length.'];
 if(ns.a+da>ns.b&&ns.a<ns.b+da||ns.b+db>ns.a&&ns.b<ns.a+db)lines.push('Careful: after the swap the two run into each other.');
 return lmvActFrame(f,'Confirm','Swap “'+A.title+'” and “'+B.title+'”?',lines,'Swap them',function(){
  const a=lmvLD.api();g5Batch++;let ok=true;
  try{
   const mv=function(x,st){return x.kind==='SESSION'?a.moveSession(x.id,st):a.moveCommitment(x.id,st,st+(x.endEpoch-x.startEpoch))};
   const r1=mv(A,ns.a);const r2=mv(B,ns.b);ok=!!(r1&&r1.ok&&r2&&r2.ok);
   if(!ok){mv(A,A.startEpoch);mv(B,B.startEpoch)}
  }finally{g5Batch--}
  if(!ok)return lmvLines(f,'Voice','Could not swap them',['Life Director could not fit that, so nothing was changed.'],[]);
  g5Push({t:'ld_move',items:[{id:A.id,k:A.kind==='SESSION'?'S':'C',s:A.startEpoch,e:A.endEpoch,t:A.title},{id:B.id,k:B.kind==='SESSION'?'S':'C',s:B.startEpoch,e:B.endEpoch,t:B.title}],label:'Swapped “'+A.title+'” and “'+B.title+'”'});
  return lmvLines(f,'Done','Swapped “'+A.title+'” and “'+B.title+'”',[A.title+' is now at '+lmvClock(ns.a*1000,ctx.tz)+', '+B.title+' at '+lmvClock(ns.b*1000,ctx.tz)+'.','Say “undo” to swap them back.'],[lmvOpenChip('life-director','Life Director',true)]);
 });
}
function g5Clear(f,s,clean,ctx){
 const today=lmvToday(ctx),d=lmvDatesFor(clean,ctx),date=d.raw.date||today;
 let a=0,b=1440,label='all day';
 const C=g5ClockRe();let m;
 const dpm=/\b(morning|afternoon|evening|night|tonight)\b/.exec(s);
 if((m=new RegExp('\\b(after|from|past) '+C,'').exec(s))&&!/after work/.test(s)){a=g5Mins(m[2],m[3],m[4]);label='after '+g5T(a)}
 else if((m=new RegExp('\\bbefore '+C,'').exec(s))){b=g5Mins(m[1],m[2],m[3]);label='before '+g5T(b)}
 else if(dpm){const w=G5_DP[dpm[1]];a=w[0];b=w[1];label='the '+(dpm[1]==='tonight'?'evening':dpm[1])}
 else if(!/\b(day|everything|schedule|all)\b/.test(s))return null;
 let items=g5Movables(date,ctx);if(items===null)return lmvLines(f,'Schedule','Life Director is not set up yet',[],[lmvOpenChip('life-director','Life Director',true)]);
 const now=Math.floor(ctx.now/1000);
 items=items.filter(function(x){const p=lmvParts(x.startEpoch*1000,ctx.tz),mm=p.h*60+p.mi;return mm>=a&&mm<b&&(date!==today||x.endEpoch>now)});
 const dl=date===today?'today':lmvNiceDate(date);
 if(!items.length)return lmvLines(f,'Schedule','Nothing to clear',['Nothing is planned '+label+' '+dl+'.'],[]);
 const lines=items.slice(0,10).map(function(x){return x.title+'  ·  '+lmvClock(x.startEpoch*1000,ctx.tz)});if(items.length>10)lines.push('and '+(items.length-10)+' more');
 const sess=items.filter(function(x){return x.k==='S'}).length;if(sess)lines.push(sess+' planned session'+(sess===1?'':'s')+' will be skipped and the day re-planned. Undo cannot bring those back.');
 return lmvActFrame(f,'Confirm removal','Remove '+items.length+' thing'+(items.length===1?'':'s')+' '+label+' '+dl+'?',lines,'Yes, remove '+items.length,function(){
  const api=lmvLD.api();let n=0;const copies=[];g5Batch++;
  try{items.forEach(function(x){if(x.k==='C'){copies.push(JSON.parse(JSON.stringify(x.raw)));if(api.remove(x.id))n++}else{const r=api.skipSession(x.id);if(r&&r.ok)n++}})}finally{g5Batch--}
  if(copies.length)g5Push({t:'ld_restore',items:copies,label:'Cleared '+n+' thing'+(n===1?'':'s')+' '+label});else g5Push({t:'irreversible',label:'Cleared '+n+' thing'+(n===1?'':'s')+' '+label});
  return lmvLines(f,'Done','Cleared '+n+' thing'+(n===1?'':'s'),[label+' '+dl+' is clear.',copies.length?'Say “undo” to bring them back.':''].filter(Boolean),[lmvOpenChip('life-director','Life Director',true)]);
 });
}
/* "if I have an hour free this afternoon, schedule guitar practice", "squeeze in 20 minutes of meditation before dinner" */
function g5Fit(f,s,clean,ctx,meta){
 const today=lmvToday(ctx),cons=g5Constraints(clean),n=lmvNormalize(cons.text);
 const dm=/(\d+(?:\.\d+)?) ?(hours?|hrs?|hr|h|minutes?|mins?|min)\b/.exec(n);
 const dur=dm?Math.round(+dm[1]*(/^h/.test(dm[2])?60:1)):LMV_DEFAULT_MIN;
 const ex=lmvExtract(cons.text.replace(/^.*?\b(?:schedule|put|add|book|plan|squeeze in|fit in|fit|slot in|block out|block)\b/i,' ').replace(/\b(?:if i have|if i'?ve got|whenever i'?m free|when i'?m free|any ?time i'?m free)\b.*$/i,' ')+' '+cons.text,ctx,null);
 let title=null;
 const tm=/\b(?:schedule|put|add|book|plan|squeeze in|fit in|slot in|block out)\b(?: in)?\s+(?:an? |the |some |my )?(?:\d+(?:\.\d+)?[\s-]?(?:minutes?|mins?|hours?|hrs?)(?: of)? )?(.+?)(?: (?:before|after|whenever|today|tomorrow|this|tonight|at|for|between|if)\b.*)?$/i.exec(clean.replace(/^.*?\bif i (?:have|got|'ve got) [^,]*?,\s*/i,''));
 if(tm)title=tm[1].replace(/\b(?:an? |the |of )\b/gi,' ').replace(/\s+/g,' ').trim();
 if(!title||/^\d/.test(title))title=ex.title;
 if(!title)return lmvLines(f,'Schedule','What should I fit in?',['Say, for example, “squeeze in 20 minutes of meditation before dinner”.'],[]);
 title=title.charAt(0).toUpperCase()+title.slice(1);
 const date=/\btomorrow\b/.test(s)?lmvAddDays(today,1):ex.date||today;
 let a=7*60,b=22*60,dp=/\b(morning|afternoon|evening|tonight|night)\b/.exec(s);if(dp&&G5_DP[dp[1]]){a=G5_DP[dp[1]][0];b=G5_DP[dp[1]][1]}
 let note=null,beforeIt=null;
 const bm=/\bbefore ([a-z][a-z ]*?)(?: today| tomorrow| tonight)? ?$/.exec(s.replace(/[?.]+$/,''));
 if(bm&&!/^(?:work|noon|midnight|\d)/.test(bm[1].trim())&&!/\d/.test(bm[1])){
  const r=lmvResolve(bm[1].trim(),ctx,[date]);
  if(r&&r.item){const p=lmvParts(r.item.startEpoch*1000,ctx.tz);b=p.h*60+p.mi;beforeIt=r.item.title}
  else if(/\b(dinner|supper)\b/.test(bm[1])){b=18*60;beforeIt='dinner';note='I could not find a dinner block, so I used 6 PM.'}
  else if(/\blunch\b/.test(bm[1])){b=12*60;beforeIt='lunch';note='I could not find a lunch block, so I used noon.'}
  else if(/\bbreakfast\b/.test(bm[1])){b=9*60;beforeIt='breakfast';note='I could not find a breakfast block, so I used 9 AM.'}
 }
 const gaps=g5Gaps(date,ctx,a,b,cons.excl,5);if(gaps===null)return lmvAfterNoLD(clean);
 const fits=gaps.filter(function(g){return g[1]-g5Roundup(g[0])>=dur});
 const where=dp?'this '+(dp[1]==='tonight'?'evening':dp[1]):(date===today?'today':lmvNiceDate(date));
 if(fits.length){
  const g=beforeIt?fits[fits.length-1]:fits[0],st=beforeIt?Math.max(g5Roundup(g[0]),Math.floor((g[1]-dur)/5)*5):g5Roundup(g[0]);
  return g5Preview(clean,meta,ctx,title,date,st,dur,(note?note+' ':'')+(beforeIt?'Fitted right before '+beforeIt+'.':'Found an open '+lmvDurText(dur)+' '+where+'.'));
 }
 const best=gaps.map(function(g){return [g5Roundup(g[0]),g[1]]}).filter(function(g){return g[1]-g[0]>=10}).sort(function(x,y){return (y[1]-y[0])-(x[1]-x[0])})[0];
 const f2=lmvFrame=lmvNewFrame();f2.transcript=clean;
 if(!best)return lmvLines(f2,'Schedule','No room '+where,['Nothing 10 minutes or longer is open then.'],[]),lmvRender(),lmvSnapshot();
 const bl=best[1]-best[0];
 lmvLines(f2,'Schedule','You do not have a full '+lmvDurText(dur)+' free '+where+', but you have '+lmvDurText(bl)+' open from '+g5T(best[0])+' to '+g5T(best[1])+'. Schedule for '+lmvDurText(bl)+' instead?',[],
  [{label:'Schedule '+lmvDurText(bl),run:function(){g5Preview(clean,meta,lmvCtx(),title,date,best[0],bl,'Used the longest open stretch.')}}]);
 lmvRender();return lmvSnapshot();
}

/* ===== sets, reps, weight, distance ===== */
const G5_PACE={run:6,ran:6,jog:6.5,jogged:6.5,bike:3,biked:3,cycle:3,cycled:3,ride:3,rode:3,walk:12,walked:12,swim:20,swam:20,hike:15,hiked:15,row:5.5,rowed:5.5};
const G5_BW='push[- ]?ups?|pull[- ]?ups?|chin[- ]?ups?|sit[- ]?ups?|squats?|burpees?|lunges?|crunches|dips|jumping jacks|situps|pushups|pullups';
function g5Kg(w,u){return /^k/.test(u)?w:w*0.45359237}
function g5Title(t){return t.replace(/\s+/g,' ').trim().replace(/(^|\s)([a-z])/g,function(m,a,b){return a+b.toUpperCase()})}
function g5ParseWorkout(raw,ctx){
 const L=' '+lmvNormalize(raw)+' ';
 if(/^ (?:how|what|when|did i|do i|have i|which|show|tell|check)\b/.test(L))return null;
 let s=L,w=null,wu=null,m;
 if((m=/(?:at|with|using|@|of)? ?(\d+(?:\.\d+)?) ?(pounds?|lbs?|lb|kilograms?|kilos?|kgs?|kg)\b/.exec(s))){w=+m[1];wu=/^k/.test(m[2])?'kg':'lbs';s=s.replace(m[0],' ')}
 let sets=null,reps=null;
 if((m=/(\d+) sets? (?:of|x|by) (\d+)\b(?: reps?)?/.exec(s))||(m=/(\d+) ?x ?(\d+)\b/.exec(s))){sets=+m[1];reps=+m[2];s=s.replace(m[0],' ')}
 if(sets!=null&&sets>=1&&reps>=1&&/\b(log|record|add|did|just did|finished|completed)\b/.test(L)||sets!=null&&/\bbench|squat|deadlift|press|curl|row|pull|push|lunge|dip|raise|extension|fly\b/.test(s)){
  const name=s.replace(/\b(log|record|add|did|just|ive|i've|i|a|an|the|my|of|at|with|for|and|some|today|yesterday|this morning|last night|earlier|set|sets|rep|reps|workout|exercise|lifted|finished|completed)\b/g,' ').replace(/[^a-z\- ]/g,' ').replace(/\s+/g,' ').trim();
  if(!name)return null;
  return {kind:'strength',exercise:g5Title(name),sets:sets,reps:reps,weight:w,unit:wu||(w!=null?'lbs':null)};
 }
 if((m=new RegExp('(\\d+) (?:sets? of )?('+G5_BW+')\\b').exec(L))){return {kind:'strength',exercise:g5Title(m[2].replace(/s$/,'').replace(/[- ]?ups?$/,'-up').replace(/^push-up$/,'push-up')).replace(/Burpee$/,'Burpee'),sets:1,reps:+m[1],weight:null,unit:null,bodyweight:true}}
 const dm=/(\d+(?:\.\d+)?) ?(k|km|kms|kilometers?|kilometres?|miles?|mi)\b/.exec(L);
 const vb=/\b(run|ran|running|jog|jogged|jogging|bike|biked|biking|cycle|cycled|cycling|ride|rode|riding|walk|walked|walking|swim|swam|swimming|hike|hiked|hiking|row|rowed|rowing)\b/.exec(L);
 if(dm&&vb&&/\b(log|record|add|i|did|just|ran|walked|biked|rode|swam|hiked|run|walk|bike|ride)\b/.test(L)){
  const km=/^(k|km|kms|kilo|kilom)/.test(dm[2]),dist=+dm[1],du=km?'km':'mi';
  const ex=lmvExtract(raw.replace(/(\d+(?:\.\d+)?) ?(?:k|km|kms|kilometers?|kilometres?|miles?|mi)\b/gi,' '),ctx,null);
  const verb=vb[1].replace(/ing$/,'').replace(/^(ran)$/,'run');
  const key=Object.keys(G5_PACE).find(function(k){return vb[1].indexOf(k.slice(0,3))===0})||'run';
  const noun=/^(?:ran|run)/.test(vb[1])?'Run':/^jog/.test(vb[1])?'Jog':/^(?:bik|cycl|rid|rode)/.test(vb[1])?'Bike ride':/^(?:walk)/.test(vb[1])?'Walk':/^(?:swim|swam)/.test(vb[1])?'Swim':/^hik/.test(vb[1])?'Hike':'Row';
  return {kind:'cardio',exercise:noun,verb:key,distance:dist,du:du,dur:ex.dur||null};
 }
 return null;
}
function g5Workout(f,det,clean,ctx){
 const scopeWords=(/\b(yesterday|this morning|this afternoon|this evening|last night|earlier today|today)\b/i.exec(clean)||[''])[0];
 let dur=det.dur,est=false;
 if(det.kind==='cardio'&&!dur){const km=det.du==='km'?det.distance:det.distance*1.609344;dur=Math.max(5,Math.round(km*(G5_PACE[det.verb]||6)));est=true}
 if(det.kind==='strength'&&!dur){dur=Math.max(5,det.sets*3);est=true}
 const noun=det.kind==='strength'?'strength workout':/^(Bike)/.test(det.exercise)?'bike ride':det.exercise.toLowerCase();
 const t2='log a '+dur+' minute '+noun+' '+scopeWords;
 const sc=lmvScope(t2,ctx,true);
 const r=lmvHLog(f,t2,ctx,sc);
 if(!f.act)return r;
 let sum,line;
 if(det.kind==='strength'){
  const kg=det.weight!=null?g5Kg(det.weight,det.unit):null,vol=det.weight!=null?det.sets*det.reps*det.weight:null;
  sum=det.sets+' × '+det.reps+(det.weight!=null?' at '+det.weight+' '+det.unit:'')+(det.bodyweight?' (bodyweight)':'');
  line=det.exercise+': '+det.sets+' set'+(det.sets===1?'':'s')+' of '+det.reps+(det.weight!=null?' at '+det.weight+' '+det.unit+(vol?' · volume '+Math.round(vol).toLocaleString()+' '+det.unit:''):'');
  det.detail={exercise:det.exercise,sets:det.sets,reps:det.reps,weight:det.weight,unit:det.unit,volume:vol};
 }else{
  const km=det.du==='km'?det.distance:det.distance*1.609344,mi=det.du==='mi'?det.distance:det.distance/1.609344,pace=dur/km;
  sum=det.distance+' '+det.du+' in '+dur+' min';
  line=det.exercise+': '+det.distance+' '+det.du+' ('+(det.du==='km'?(Math.round(mi*10)/10)+' mi':(Math.round(km*10)/10)+' km')+') in '+dur+' min · '+Math.floor(pace)+':'+lmvPad(Math.round((pace%1)*60))+' per km';
  det.detail={exercise:det.exercise,distance:det.distance,distanceUnit:det.du,durationMinutes:dur,paceMinPerKm:Math.round(pace*100)/100};
 }
 f.g5detail={name:det.exercise,sum:sum,detail:det.detail};
 f.title='Log “'+det.exercise+'”?';
 f.act.lines=f.act.lines.filter(function(x){return !/standard 30 minutes/.test(x)});
 f.act.lines.splice(1,0,line);if(est)f.act.lines.splice(2,0,'Length estimated at '+dur+' minutes ('+(det.kind==='cardio'?'a typical pace':'about 3 minutes a set')+'). Say it again with a time to be exact.');
 return r;
}

/* ===== water, goals, meals ===== */
const G5_WML={glass:250,glasses:250,cup:250,cups:250,mug:300,mugs:300,pint:473,pints:473,bottle:500,bottles:500,'large bottle':1000,'big bottle':1000,liter:1000,liters:1000,litre:1000,litres:1000,l:1000,ml:1,milliliter:1,milliliters:1,millilitre:1,millilitres:1,oz:29.5735,ounce:29.5735,ounces:29.5735,'fl oz':29.5735};
function g5WaterToday(date){return (state.waterLog||[]).filter(function(w){return w.date===date}).reduce(function(a,w){return a+(Number(w.ml)||0)},0)}
function g5WaterGoal(){return Math.max(0,Number(state.mealsMeta&&state.mealsMeta.waterGoalMl)||2000)}
function g5ParseWater(raw){
 const n=' '+lmvNormalize(raw)+' ';
 const m=/\b(\d+(?:\.\d+)?|a|an|one|another) ?(large bottle|big bottle|bottles?|glass(?:es)?|cups?|mugs?|pints?|liters?|litres?|ml|milliliters?|millilitres?|oz|fl oz|ounces?|l)\b(?: of)? water/.exec(n)||/water (?:\w+ ){0,2}?(\d+(?:\.\d+)?) ?(ml|oz|ounces?|liters?|litres?|l|glass(?:es)?|cups?|bottles?)\b/.exec(n);
 if(!m)return null;
 const cnt=/^\d/.test(m[1])?+m[1]:1,unit=m[2].replace(/^(?:fl )?oz$/,'oz').replace(/es$/,function(x){return /glass/.test(m[2])?'es':x});
 const key=Object.keys(G5_WML).find(function(k){return k===m[2]})||(m[2].replace(/s$/,'') in G5_WML?m[2].replace(/s$/,''):null);
 if(!key)return null;
 const ml=Math.round(cnt*G5_WML[key]);
 return ml>0&&ml<=5000?{ml:ml,oz:Math.round(ml/29.5735*10)/10}:null;
}
function g5WaterAdd(f,ml,ctx){
 state.waterLog=state.waterLog||[];const date=lmvToday(ctx),id='water_'+Date.now()+'_'+Math.random().toString(36).slice(2,6);
 state.waterLog.push({id:id,date:date,ml:ml,at:new Date().toISOString(),voice:true});try{save()}catch(e){}
 g5Push({t:'water_remove',id:id,label:'Logged '+ml+' ml of water'});
 const tot=g5WaterToday(date),goal=g5WaterGoal(),oz=Math.round(ml/29.5735*10)/10;
 return lmvLines(f,'Done','+'+ml+' ml of water ('+oz+' oz)',['Today: '+tot.toLocaleString()+' ml of '+goal.toLocaleString()+' ml ('+Math.round(tot/goal*100)+'%)'+(state.mealsMeta&&state.mealsMeta.waterGoalMl?'':' · goal is the standard 2 L; say “set my water goal to 3 liters” to change it'),'Say “undo” to take it back.'],[]);
}
function g5Goal(key){const g=Number(state.mealsMeta&&state.mealsMeta[key])||0;return g>0?g:0}
const G5_EST=[
 {re:/^(?:eggs?|whole eggs?|boiled eggs?|scrambled eggs?|fried eggs?)$/,n:'Egg, whole (estimate)',per:{kcal:143,p:12.6,c:0.7,f:9.5},g:50},
 {re:/^(?:toast|slices? of toast|toasts?|bread|white bread|whole wheat bread|wheat toast|bread slices?)$/,n:'Toast or bread (estimate)',per:{kcal:265,p:9,c:49,f:3.2},g:30,unit:{slice:30,piece:30}},
 {re:/protein (?:shake|drink|smoothie)|shake/,n:'Protein shake (estimate)',per:{kcal:55,p:8.3,c:2,f:1},g:300,unit:{shake:300,scoop:30}},
 {re:/^(?:rice|white rice|brown rice|cooked rice)$/,n:'Rice, cooked (estimate)',per:{kcal:130,p:2.7,c:28,f:0.3},g:158,unit:{cup:158,bowl:200}},
 {re:/^(?:pasta|spaghetti|noodles|cooked pasta)$/,n:'Pasta, cooked (estimate)',per:{kcal:158,p:5.8,c:31,f:0.9},g:140,unit:{cup:140,bowl:220}},
 {re:/^(?:oatmeal|oats|porridge)$/,n:'Oatmeal, cooked (estimate)',per:{kcal:71,p:2.5,c:12,f:1.5},g:234,unit:{cup:234,bowl:250}},
 {re:/^(?:yogurt|greek yogurt|yoghurt)$/,n:'Greek yogurt, plain (estimate)',per:{kcal:59,p:10,c:3.6,f:0.4},g:170,unit:{cup:245}},
 {re:/^(?:apple|apples)$/,n:'Apple (estimate)',per:{kcal:52,p:0.3,c:13.8,f:0.2},g:182},
 {re:/^(?:banana|bananas)$/,n:'Banana (estimate)',per:{kcal:89,p:1.1,c:22.8,f:0.3},g:118},
 {re:/^(?:orange|oranges)$/,n:'Orange (estimate)',per:{kcal:47,p:0.9,c:11.8,f:0.1},g:131},
 {re:/^(?:milk|whole milk|skim milk)$/,n:'Milk (estimate)',per:{kcal:61,p:3.2,c:4.8,f:3.3},g:244,unit:{cup:244,glass:244}},
 {re:/^(?:peanut butter)$/,n:'Peanut butter (estimate)',per:{kcal:588,p:25,c:20,f:50},g:16,unit:{tbsp:16,tablespoon:16}},
 {re:/^(?:avocado|avocados)$/,n:'Avocado (estimate)',per:{kcal:160,p:2,c:8.5,f:14.7},g:150}
];
function g5Sing(w){return w.replace(/ies$/,'y').replace(/(?:ches|shes|sses|xes)$/,function(x){return x.slice(0,-2)}).replace(/([^s])s$/,'$1')}
function g5FoodFind(name){
 const nm=name.toLowerCase().trim();
 for(let i=0;i<G5_EST.length;i++)if(G5_EST[i].re.test(nm))return {est:G5_EST[i],name:G5_EST[i].n};
 const toks=nm.replace(/[^a-z ]/g,' ').split(/\s+/).filter(function(w){return w&&!/^(?:the|of|some|a|an|with|and|fresh|cooked|raw|plain)$/.test(w)}).map(g5Sing);
 if(!toks.length)return null;
 let best=null;
 allFoods().forEach(function(fd){
  const ln=fd.n.toLowerCase(),lt=ln.replace(/[^a-z ]/g,' ').split(/\s+/).map(g5Sing);
  if(!toks.every(function(t){return lt.indexOf(t)>=0}))return;
  if(!(Number(fd.kcal)>0||Number(fd.p)>0||Number(fd.c)>0||Number(fd.f)>0))return;
  let sc=(lt[0]===toks[0]?6:0)+(/\bcooked|ripe|prepared|heated\b/.test(ln)?2:0)-(/\braw|dried|dry|powder|frozen|flour|juice|canned|oil\b/.test(ln)&&!/\braw\b/.test(nm)?2:0)-(lt.length-toks.length)*0.25;
  if(!best||sc>best.sc)best={sc:sc,fd:fd};
 });
 return best?{fd:best.fd,name:best.fd.n}:null;
}
function g5ItemGrams(r,qty,unit){
 const per=r.est?r.est.g:(function(){const p=(r.fd.port||[]).find(function(x){return /piece|each|medium|whole|item|large|small|slice|breast|fillet/i.test(x.l)})||(r.fd.port||[])[0];return p?p.g:100})();
 const u=unit?unit.replace(/s$/,'').replace(/^glasse$/,'glass'):'';
 if(/^(?:g|gram)$/.test(u))return qty;if(/^(?:oz|ounce)$/.test(u))return qty*28.35;if(u==='ml')return qty;
 const eu=r.est&&r.est.unit&&r.est.unit[u];if(eu)return qty*eu;
 if(u==='cup')return qty*(r.est?(r.est.unit&&r.est.unit.cup)||240:((r.fd.port||[]).find(function(x){return /cup/i.test(x.l)})||{g:240}).g);
 if(u==='tbsp'||u==='tablespoon')return qty*15;if(u==='tsp'||u==='teaspoon')return qty*5;if(u==='scoop')return qty*30;if(u==='handful')return qty*30;if(u==='bowl')return qty*250;if(u==='slice')return qty*30;
 return qty*per;
}
function g5ParseMeal(raw,ctx){
 const L=String(raw||'').trim().replace(/[.?!]+$/,'');let slot=null,body=null,m;
 const S='(breakfast|brunch|lunch|dinner|supper|snacks?)';
 if((m=new RegExp('^(?:for |at )?'+S+',?:? (?:i )?(?:had|ate|have had|just had|just ate|eaten|got|am having|having)\\s+(.+)$','i').exec(L))){slot=m[1];body=m[2]}
 else if((m=new RegExp('^(?:i )?(?:just )?(?:had|ate|have eaten|have had)\\s+(.+?)\\s+(?:for|at|as my) (?:my )?(?:a )?'+S+'$','i').exec(L))){body=m[1];slot=m[2]}
 else if((m=new RegExp('^(?:log|add|record) (?:for |my )?'+S+'[:,]? (.+)$','i').exec(L))){slot=m[1];body=m[2]}
 else if((m=/^(?:i )?(?:just )?(?:had|ate)\s+(.+)$/i.exec(L))){body=m[1];const h=lmvParts(ctx.now,ctx.tz).h;slot=h<11?'breakfast':h<15?'lunch':h<21?'dinner':'snacks'}
 if(!body)return null;
 slot=slot.toLowerCase();slot=/^snack/.test(slot)?'snacks':slot==='brunch'?'breakfast':slot==='supper'?'dinner':slot;
 const parts=body.replace(/\band\b/gi,',').replace(/\bplus\b/gi,',').split(/\s*,\s*/).map(function(x){return x.trim()}).filter(Boolean);
 const items=parts.map(function(p){
  const n=lmvNormalize(p).replace(/^(?:some|a bit of|a little)\s+/,'');
  const q=/^(\d+(?:\.\d+)?|a|an)\s+(?:(slices?|pieces?|cups?|glass(?:es)?|bowls?|tbsp|tablespoons?|tsp|teaspoons?|scoops?|cans?|bottles?|servings?|handfuls?|oz|ounces?|grams?|g|ml|shakes?)\s+(?:of\s+)?)?(.+)$/.exec(n);
  let qty=1,unit='',name=n;if(q){qty=/^\d/.test(q[1])?+q[1]:1;unit=(q[2]||'').replace(/s$/,'');name=q[3]}
  else{const q2=/^(\d+(?:\.\d+)?)\s+(.+)$/.exec(n);if(q2){qty=+q2[1];name=q2[2]}}
  return {raw:p,qty:qty,unit:unit,name:name.trim()};
 });
 return {slot:slot,items:items};
}
function g5Meal(f,pm,ctx){
 const matched=[],missed=[];
 pm.items.forEach(function(it){
  const r=g5FoodFind(it.name);if(!r){missed.push(it.raw);return}
  const g=Math.round(g5ItemGrams(r,it.qty,it.unit)),e=r.est,per=e?e.per:r.fd;
  const m=g/100,kc=e?Math.round(per.kcal*m):scaleFood(r.fd,g).kcal,p=e?Math.round(per.p*m*10)/10:scaleFood(r.fd,g).p,c=e?Math.round(per.c*m*10)/10:scaleFood(r.fd,g).c,fa=e?Math.round(per.f*m*10)/10:scaleFood(r.fd,g).f;
  matched.push({it:it,r:r,g:g,kcal:kc,p:p,c:c,f:fa});
 });
 if(!matched.length)return lmvLines(f,'Nutrition','I could not match those foods',['Try one at a time, or add them in the Food tab.'].concat(missed.length?['Not found: '+missed.join(', ')]:[]),[lmvOpenChip('meals','Food')]);
 const tot=matched.reduce(function(a,x){return {k:a.k+x.kcal,p:a.p+x.p,c:a.c+x.c,f:a.f+x.f}},{k:0,p:0,c:0,f:0});
 const lines=matched.map(function(x){return (x.it.qty!==1||x.it.unit?x.it.qty+(x.it.unit?' '+x.it.unit:'')+' ':'')+x.it.name+'  →  '+x.g+' g · '+x.kcal+' kcal · '+(Math.round(x.p*10)/10)+' g protein'+(x.r.est?' (estimate)':'')});
 lines.push('Total: '+tot.k+' kcal · '+Math.round(tot.p)+' g protein · '+Math.round(tot.c)+' g carbs · '+Math.round(tot.f)+' g fat');
 if(missed.length)lines.push('Not found, left out: '+missed.join(', '));
 const label={breakfast:'Breakfast',lunch:'Lunch',dinner:'Dinner',snacks:'Snacks'}[pm.slot];
 return lmvActFrame(f,'Confirm','Log '+label.toLowerCase()+' ('+matched.length+' item'+(matched.length===1?'':'s')+')?',lines,'Log '+label.toLowerCase(),function(){
  mealsEnsure();const ids=[],day=lmvToday(lmvCtx());
  matched.forEach(function(x){const id='food_'+Date.now()+'_'+Math.random().toString(36).slice(2,6);ids.push(id);
   state.foodLog.push({id:id,date:day,slot:pm.slot,foodId:x.r.est?'voice_est':x.r.fd.id,name:x.r.est?x.r.est.n:x.r.fd.n,grams:x.g,kcal:x.kcal,kcalEstimated:!!x.r.est,p:x.p,c:x.c,f:x.f,fib:0,sug:0,na:0,at:new Date().toISOString(),voice:true})});
  try{save()}catch(e){}
  g5Push({t:'food_remove',ids:ids,label:'Logged '+label.toLowerCase()+' ('+ids.length+' items)'});
  return lmvLines(f,'Done','Logged '+label.toLowerCase(),[ids.length+' item'+(ids.length===1?'':'s')+' · '+tot.k+' kcal · '+Math.round(tot.p)+' g protein','Say “undo” to remove them.'],[lmvOpenChip('meals','Food')]);
 });
}
function g5Remaining(f,cls,ctx){
 const today=lmvToday(ctx);mealsEnsure();
 const eat=(state.foodLog||[]).filter(function(e){return e.date===today});
 const sum=function(k){return eat.reduce(function(a,e){return a+(Number(e[k])||0)},0)};
 const mac=cls.macro;
 if(mac==='water'){const goal=g5WaterGoal(),tot=g5WaterToday(today),left=Math.max(0,goal-tot);return lmvLines(f,'Water',left?left.toLocaleString()+' ml of water to go':'Water goal reached',['Today: '+tot.toLocaleString()+' ml ('+Math.round(tot/29.5735)+' oz) of '+goal.toLocaleString()+' ml'],[])}
 const key=mac==='protein'?'protGoal':'calGoal',have=mac==='protein'?sum('p'):sum('kcal'),goal=g5Goal(key),unit=mac==='protein'?'g protein':'kcal',noun=mac==='protein'?'protein':'calories';
 if(!goal)return lmvLines(f,'Nutrition','No '+noun+' goal is set',['Eaten today: '+Math.round(have).toLocaleString()+' '+unit,'Say “set my '+(mac==='protein'?'protein':'calorie')+' goal to '+(mac==='protein'?'150':'2200')+'”.'],[lmvOpenChip('meals','Food')]);
 const left=Math.round(goal-have);
 return lmvLines(f,'Nutrition',left>=0?left.toLocaleString()+' '+unit+' left today':Math.abs(left).toLocaleString()+' '+unit+' over today',['Eaten: '+Math.round(have).toLocaleString()+' of '+goal.toLocaleString()+' '+unit],[lmvOpenChip('meals','Food')]);
}

/* ===== cats ===== */
function g5Cats(){
 const out=[],seen={};
 try{lmvPets().forEach(function(p){if(p&&p.name){out.push({name:p.name,p:p,home:true});seen[p.name.toLowerCase()]=1}})}catch(e){}
 try{petBreedRoster().forEach(function(e){if(e&&e.name&&!seen[e.name.toLowerCase()]){out.push({name:e.name,p:e.p||null,home:!!e.home,dna:e.dna});seen[e.name.toLowerCase()]=1}})}catch(e){}
 try{petCattery().cats.forEach(function(c){if(c&&c.name&&!seen[c.name.toLowerCase()]){out.push({name:c.name,p:null,home:false,dna:c.dna});seen[c.name.toLowerCase()]=1}})}catch(e){}
 return out;
}
function g5CatNamed(s){const cats=g5Cats(),t=s.replace(/'s\b/g,' ').replace(/[^a-z0-9 ]/g,'');return cats.filter(function(c){const k=c.name.toLowerCase().replace(/[^a-z0-9]/g,'');return k&&new RegExp('\\b'+k+'\\b').test(t)})}
const G5_TRAIT={curious:'Curious: explores everything and comes to see what you are doing.',lazy:'Lazy: happiest lying down, ideally near you.',playful:'Playful: always up for a chase, expect ambushes.',shy:'Shy: slow to trust, needs quiet company.',bold:'Bold: confident and not easily startled.',gentle:'Gentle: calm and affectionate.'};
function g5CatDo(f,cls,ctx){
 const cats=cls.names&&cls.names.length?cls.names:null,all=g5Cats();
 const pick=cats||(all.length?[all[0]]:[]);
 if(cls.act==='treat')return lmvLines(f,'Cat','There are no treats in LifeMax yet',['I can feed '+(pick[0]?pick[0].name:'your cat')+' or pet them. Say “feed my cat”.'],[]);
 if(cls.act==='litter')return lmvLines(f,'Cat','There is no litter box to clean yet',['The cat room has no chores beyond feeding, petting and playing.'],[]);
 if(!all.length)return lmvLines(f,'Cat','You do not have a cat yet',['Open the Cat tab to meet one.'],[lmvOpenChip('pet','Cat')]);
 if(cls.act==='hungry'){
  const lines=[];let n=0;
  all.forEach(function(c){if(c.home&&c.p&&c.p.alive){let can=false;try{can=petCanFeed(c.p,Date.now())}catch(e){}lines.push(c.name+(can?' is ready for a meal':' has eaten; next meal in about '+Math.max(1,Math.round(petNextMealIn(c.p,Date.now())))+' h'));if(can)n++}else lines.push(c.name+' lives in the cattery, so no meals to worry about')});
  return lmvLines(f,'Cats',n?(n===1?'1 cat is hungry':n+' cats are hungry'):'Nobody is hungry',lines,[{label:'Feed',run:function(){lmvPetDo('feed')}}]);
 }
 if(cls.act==='play'){return lmvHPetAct(f,{act:'play'})}
 const c=pick[0];
 if(cls.act==='describe'){
  const lines=[];const p=c.p;
  if(p){
   lines.push(G5_TRAIT[p.trait]||('Personality: '+(p.trait||'still showing')));
   try{const b=PET_RT&&PET_RT.br?petBondStyle(p,PET_RT.br):null;if(b)lines.push('With you: '+b.name+'. '+b.text)}catch(e){}
   try{lines.push('Coat: '+String(p.coat).replace(/^dna_.*/,'a genetic coat').replace(/_/g,' '))}catch(e){}
   try{const ph=petPheno(p);if(ph&&ph.morph){const m=ph.morph;lines.push('Build: ears '+m.em.toFixed(1)+'× · tail '+m.tl.toFixed(1)+'× · legs '+m.lg.toFixed(1)+'× · body '+m.bl.toFixed(1)+'×'+(m.bob?' · bobtail':''))}}catch(e){}
   try{lines.push('Level '+petLevel(p)+' · '+Math.max(1,Math.round((Date.now()-p.born)/864e5))+' days with you')}catch(e){}
  }else lines.push(c.name+' lives in your cattery. Open the Cat tab to see their genes.');
  return lmvLines(f,'Cat',c.name+'’s personality',lines,[lmvOpenChip('pet','Cat')]);
 }
 if(c.p&&c.home){
  const p=c.p,lines=[];let can=false;try{can=petCanFeed(p,Date.now())}catch(e){}
  lines.push(can?'Hungry: ready for a meal':'Fed: next meal in about '+Math.max(1,Math.round(petNextMealIn(p,Date.now())))+' h');
  try{const b=PET_RT&&PET_RT.br?petBondStyle(p,PET_RT.br):null;if(b)lines.push('With you: '+b.name)}catch(e){}
  lines.push(G5_TRAIT[p.trait]||'');
  return lmvLines(f,'Cat',c.name+' is '+(can?'ready for a meal':'doing well'),lines.filter(Boolean),[{label:'Feed',run:function(){lmvPetDo('feed')}},lmvOpenChip('pet','Cat')]);
 }
 return lmvLines(f,'Cat',c.name+' lives in your cattery',['Cattery cats are kept as genes for breeding; open the Cat tab to see them.'],[lmvOpenChip('pet','Cat')]);
}

/* ===== macros you teach it ===== */
function g5Macros(){try{const m=JSON.parse(localStorage.getItem(G5_MACRO)||'{}');return m&&typeof m==='object'?m:{}}catch(e){return {}}}
function g5MacroKey(p){return String(p||'').toLowerCase().replace(/[^a-z0-9 ]/g,' ').replace(/\s+/g,' ').trim()}
function g5MacroPut(phrase,cmds){const k=g5MacroKey(phrase);if(k.length<2)return {ok:false,msg:'That phrase is too short.'};const m=g5Macros();if(!m[k]&&Object.keys(m).length>=30)return {ok:false,msg:'You have 30 shortcuts already. Forget one first.'};
 if(!cmds.length||cmds.length>5)return {ok:false,msg:'A shortcut can run between one and five things.'};
 if(cmds.some(function(c){return g5MacroKey(c)===k}))return {ok:false,msg:'A shortcut cannot run itself.'};
 m[k]={phrase:phrase,cmds:cmds};try{localStorage.setItem(G5_MACRO,JSON.stringify(m))}catch(e){return {ok:false,msg:'Could not save it on this device.'}}return {ok:true}}
function g5MacroDel(phrase){const m=g5Macros(),k=g5MacroKey(phrase);if(!m[k])return false;delete m[k];try{localStorage.setItem(G5_MACRO,JSON.stringify(m))}catch(e){}return true}
function g5SplitCmds(body){
 const t=String(body).trim().replace(/[.]+$/,''),out=[];let rest=t;
 for(let k=0;k<4;k++){const sp=g4Split(rest);if(!sp)break;out.push(sp.a.trim());rest=sp.b.trim()}
 out.push(rest);return out.filter(Boolean);
}
const G5_BUILTIN={
 'morning routine':'morning','start my day':'morning','start the day':'morning','good morning':'morning','morning briefing':'morning',
 'end of day review':'evening','end of day':'evening','wrap up my day':'evening','wrap up the day':'evening','wind down for bed':'evening','wind down':'evening','end my day':'evening','evening routine':'evening','night routine':'evening','bedtime routine':'evening'
};
let g5Depth=0;
function g5RunCmds(cmds,meta){
 if(g5Depth>3)return lmvLines(lmvFrame||lmvNewFrame(),'Shortcut','That shortcut calls itself',['Edit it so it does something else.'],[]);
 g5Depth++;try{return g5RunCmds2(cmds)}finally{g5Depth--}
}
function g5RunCmds2(cmds){
 if(cmds.length===1){g5Prev(cmds[0],{});return lmvSnapshot()}
 g4Reset();const q=cmds.slice(1);g5Prev(cmds[0],{});g4On=true;g4Queue=q;g4Frame=lmvFrame;g4Pump();lmvRender();return lmvSnapshot();
}

function g5TeachParse(raw){
 const t=String(raw||'').trim().replace(/[.!]+$/,'');let m;
 if(!/^(?:hey |ok |okay )?(?:when|if|whenever) i say\b/i.test(t))return null;
 m=/^(?:hey |ok |okay )?(?:when|if|whenever) i say\s+["“‘']([^"”’']{2,40})["”’']\s*,?\s*(?:then |you |please )*(?:do |run |say )?(.{3,200})$/i.exec(t)
  ||/^(?:hey |ok |okay )?(?:when|if|whenever) i say\s+([^,"”’]{2,40}?)\s*,\s*(?:then |you |please )*(?:do |run |say )?(.{3,200})$/i.exec(t)
  ||/^(?:hey |ok |okay )?(?:when|if|whenever) i say\s+(.{2,40}?)\s+(?:you |then |please )*(?:do|run|means?|should do|should run)\s+(.{3,200})$/i.exec(t);
 return m?[m[1].trim(),m[2].trim()]:null;
}
/* ===== classification ===== */
function g5Out(id,extra){return Object.assign({id:id,conf:.93,question:true},extra||{})}
function g5Classify(text){
 const s=g5NumWords(lmvLead(lmvLow(text)).replace(/\s*\?+\s*$/,' ')),T=s.trim();
 if(/^ (?:what exercises|what moves|what lifts|what workouts?|which exercises)\b.*\b(?:plan|today)\b/.test(s)||/^ what(?:'s| is| do i have) in (?:today'?s|my|the) (?:workout )?plan\b/.test(s))return g5Out('fitness.workout_plan',{act:'show'});
 if(/^ (?:find|show|get|look for|check|give)(?: me)?(?: some| any| my)? (?:free|open|available) (?:time|slots?|windows?|gaps?)\b/.test(s)&&!/\bschedule|put|book|add\b/.test(s))return g5Out('life_director.find_free_time');
 if(/^ (?:undo|undo that|undo it|undo last|undo the last(?: one| thing| command| change)?|take that back|take it back|revert(?: that| last| the last)?(?: command| change)?|reverse that|put it back) ?$/.test(s))return g5Out('voice.undo_last_action');
 /* macros */
 const ta=g5TeachParse(text);
 if(ta)return g5Out('voice.create_macro',{phrase:ta[0],body:ta[1]});
 const fa=/^ (?:forget|delete|remove) (?:my |the )?(?:shortcut |macro |routine )?["“']?(.{2,40}?)["”']?(?: shortcut| macro| routine)? ?$/.exec(s);
 if(fa&&(g5Macros()[g5MacroKey(fa[1])]||g4AliasGet(fa[1])!=null))return g5Out('voice.forget_macro',{phrase:fa[1].trim()});
 if(/^ (?:what|which|list|show)(?: me)?(?: are)?(?: my| the)? (?:shortcuts|macros|voice shortcuts|routines|voice macros)(?: do i have)? ?$/.test(s))return g5Out('voice.list_macros');
 const mk=g5MacroKey(T);if(g5Macros()[mk]||G5_BUILTIN[mk]!=null)return g5Out('voice.run_macro',{key:mk});
 /* screen and positions */
 if(/\b(?:here|this screen|this page|this tab|on screen)\b/.test(s)&&/^ (?:what do i have left|do i have anything left|anything left|what'?s left|what is left|what'?s on|what is on|what'?s here|what am i looking at|summari[sz]e|tell me about|how am i doing|what'?s going on|read)\b/.test(s))return g5Out('voice.screen_summary');
 if(/^ (?:summari[sz]e|read|what'?s on) this (?:screen|page|tab)\b/.test(s)||/^ what am i looking at\b/.test(s))return g5Out('voice.screen_summary');
 if(/^ (?:check off|complete|finish|mark|tick off) (?:everything|all of them|all of it|them all|all) (?:here|on this screen|on this page|in here)?/.test(s)&&g5Screen()==='quests')return g5Out('voice.complete_all_here');
 if(new RegExp('^ (?:please |hey |ok |okay )*(?:complete|finish|check off|check|tick off|mark|do|done with|uncheck|cancel|delete|remove|skip|move|reschedule|push|tell me more about|more about|more on|open|show|read)(?: off)? (?:me )?(?:the |my |item |number )?(?:'+G5_ORDRE+')\\b').test(s))return g5Out('voice.ordinal');
 /* a day gone off-script */
 const earlyWord=/\b(?:back|later|forward|ahead|earlier|sooner|up|late|behind)\b/;
 if(/^ (?:push|move|shift|bump|delay|slide|slip) (?:everything|all|the rest|my rest|the whole|my whole|my entire|my (?:afternoon|morning|evening)|the (?:afternoon|morning|evening)|my schedule|my day|the day|the schedule|the rest of (?:my|the) (?:day|schedule))/.test(s)&&(earlyWord.test(s)||/\d/.test(s)))return g5Out('life_director.shift_schedule');
 if(/\b(?:i'?m|i am|we'?re|im|running|i'?ve been|i got)\b.*\b(?:late|behind)\b/.test(s)&&/\b(?:running|am|'?m|im)\b/.test(s)&&!/\bfor work\b|\blate for\b/.test(s))return g5Out('life_director.shift_schedule');
 if(/^ (?:swap|switch|exchange|trade|flip)(?: my| the)? .+ (?:and|with|for|&) .+/.test(s)&&!/\bcats?\b|\bchannel|\bview\b/.test(s))return g5Out('life_director.swap_activities');
 if(/^ (?:clear|wipe|empty|free up|cancel|delete|remove|get rid of)(?: out)? (?:my |the )?(?:(?:whole |entire )?(?:afternoon|morning|evening|night|day|schedule)|everything|all(?: of)?(?: my)? (?:activities|things|plans|appointments|events)|anything)\b/.test(s)&&(/\b(?:afternoon|morning|evening|day|schedule|everything|after|before|all)\b/.test(s))&&!/\bquests?\b|\bcat\b/.test(s))return g5Out('life_director.clear_window');
 if(/^ (?:cancel|delete|remove|clear) everything (?:after|before|from|in|on)\b/.test(s))return g5Out('life_director.clear_window');
 if(/\bif i (?:have|got|'ve got) (?:an? |some |\d+|half an? )[^,]*?\b(?:free|open|available|spare|time)\b.*\b(?:schedule|put|add|book|plan|block)\b/.test(s)||/^ (?:squeeze|fit|slot) (?:in|me in)\b/.test(s)||/^ (?:can you |could you )?(?:please )?fit (?:in|a|an|some)\b/.test(s))return g5Out('life_director.conditional_schedule');
 /* what you did, in detail */
 if(!/^ (?:how|what|when|did i|do i|have i|which|show|tell)\b/.test(s)){try{const w=g5ParseWorkout(text,lmvCtx());if(w)return g5Out('health_fitness.log_detailed_workout',{det:w})}catch(e){}}
 /* water, food, goals */
 if(/^ (?:set|change|update|make) my (calorie|calories|protein|water) goal (?:to|at) /.test(s))return g5Out('nutrition.set_goal');
 if(/\bhow (?:much|many)\b.*\bwater\b|\bwater (?:have i|did i|so far|today|total|left)\b/.test(s)&&/^ (?:how|what|did|have|show|tell|check|do)\b/.test(s)&&!/\b(?:drank|log|add)\b/.test(s))return g5Out('nutrition.remaining',{macro:'water',water:true});
 if(/\b(?:drank|drink|drunk|had|add|log|record|chugged|finished|downed|sipped|poured)\b/.test(s)&&/\bwater\b/.test(s)&&!/^ (?:how|what|when|do|did|have|is|are)\b/.test(s)){const w=g5ParseWater(text);if(w)return g5Out('nutrition.log_water',{w:w})}
 if(/^ (?:i )?(?:just )?(?:drank|had|finished) (?:an? |one |another )?(?:glass|cup|bottle|mug|pint|liter|litre)\b/.test(s)&&/\bwater\b/.test(s)){const w=g5ParseWater(text);if(w)return g5Out('nutrition.log_water',{w:w})}
 const rm=/\b(?:how (?:much|many)|what) (?:more )?(protein|calories|carbs|carbohydrates|kcal|water)\b.*\b(?:left|remaining|still|to hit|to reach|until|more|do i need|can i|to go)\b/.exec(s)||/\bhow many calories can i (?:still )?(?:eat|have)\b/.exec(s)||/\b(protein|calories) (?:do i have )?left\b/.exec(s)||/\bwhat'?s left (?:of|for) my (protein|calories|water)\b/.exec(s);
 if(rm&&!/\bburn|\bdid i\b|\bhave i\b.*\beaten/.test(s)){const mac=/protein/.test(rm[0])?'protein':/water/.test(rm[0])?'water':/carb/.test(rm[0])?'carbs':'calories';if(mac!=='carbs')return g5Out('nutrition.remaining',{macro:mac})}
 if(!/^ (?:how|what|when|where|which|who|do|did|have|is|are|can|could|would|will|show|tell|check)\b/.test(s)){try{const pm=g5ParseMeal(text,lmvCtx());if(pm&&pm.items.length&&(/\b(?:for|at|as my) (?:my )?(?:breakfast|lunch|dinner|supper|snacks?|brunch)\b/.test(s)||/^ (?:for |at )?(?:breakfast|lunch|dinner|supper|snacks?|brunch)\b/.test(s)||pm.items.length>=2||/^ (?:i )?(?:just )?(?:had|ate)\b/.test(s)&&pm.items.every(function(i){return g5FoodFind(i.name)})))return g5Out('nutrition.log_meal',{pm:pm})}catch(e){}}
 /* cats by name */
 const named=g5CatNamed(s);
 if(/\b(?:which|what|who)\b.*\b(?:cat|cats|kitten|kittens|kitty)\b.*\b(?:hungry|needs? (?:food|feeding|a meal|to eat))\b|\b(?:is|are) (?:any|all|either|both) (?:of )?(?:my )?(?:the )?cats? hungry\b/.test(s)||(named.length&&/\bhungry\b/.test(s)))return g5Out('virtual_pets.query_needs',{act:'hungry',names:named});
 if(/\b(?:personality|personalities|traits?|temperament|genetics?|genes?|coat|what(?:'s| is) .* like)\b/.test(s)&&(named.length||/\b(?:my cat|my cats|the cat|my kitten|cat's|cats')\b/.test(s)))return g5Out('virtual_pets.describe',{act:'describe',names:named});
 if(/\b(?:treat|treats|snack for)\b/.test(s)&&(named.length||/\bcat|kitten|kitty\b/.test(s))&&/\b(?:give|get|feed|offer|toss)\b/.test(s))return g5Out('virtual_pets.give_treat',{act:'treat',names:named});
 if(/\b(?:litter ?box|litter|scoop)\b/.test(s)&&/\b(?:clean|scoop|change|empty|wash)\b/.test(s))return g5Out('virtual_pets.clean_litter',{act:'litter',names:named});
 if(/^ (?:play|pet|feed|cuddle)\b.*\b(?:all|both|every)\b.*\bcats?\b/.test(s))return g5Out('virtual_pets.perform_action',{act:/^ play/.test(s)?'play':/^ feed/.test(s)?'feed':'pet',all:true});
 if(named.length&&/^ (?:how is|how'?s|how are|is|what'?s|how has|tell me about|check on|check in on)\b/.test(s)&&!/\bhungry\b|\bpersonality\b/.test(s)&&!named.every(function(c){try{const p=petData();return p&&p.name&&p.name.toLowerCase()===c.name.toLowerCase()}catch(e){return false}}))return g5Out('virtual_pets.check_status',{act:'status',names:named,multi:true});
 return null;
}
(function(){const c=lmvClassify;lmvClassify=function(text){let g=null;try{g=g5Classify(text)}catch(e){console.warn('[voice g5]',e)}return g||c.apply(this,arguments)}})();

function g5Handle(cls,text,f,ctx){
 const id=cls.id,s=lmvLead(lmvLow(text)).replace(/\s*\?+\s*$/,' ');
 if(id==='voice.undo_last_action')return g5DoUndo(f);
 if(id==='voice.create_macro'){
  const cmds=g5SplitCmds(cls.body),r=g5MacroPut(cls.phrase,cmds);
  return r.ok?lmvLines(f,'Shortcut','Saved shortcut “'+cls.phrase+'”',['When you say “'+cls.phrase+'” I will: '+cmds.join(', then '),'Say “forget '+cls.phrase+'” to remove it.'],[]):lmvLines(f,'Shortcut','Could not save that shortcut',[r.msg],[]);
 }
 if(id==='voice.forget_macro'){const a=g5MacroDel(cls.phrase),b=g4AliasGet(cls.phrase)!=null;if(b)g4AliasDel(cls.phrase);return lmvLines(f,'Shortcut',a||b?'Forgot “'+cls.phrase+'”':'No shortcut called “'+cls.phrase+'”',[],[])}
 if(id==='voice.list_macros'){const m=g5Macros(),k=Object.keys(m),old=g4AliasAll();const lines=k.map(function(x){return '“'+m[x].phrase+'” → '+m[x].cmds.join(', then ')}).concat(Object.keys(old).map(function(x){return '“'+x+'” → '+old[x]}));return lmvLines(f,'Shortcuts',lines.length?lines.length+' shortcut'+(lines.length===1?'':'s'):'No shortcuts yet',lines.length?lines:['Teach one: “when I say beast mode, log a 60 minute workout and open my workout plan”.','Built in: “start my day”, “end of day review”.'],[])}
 if(id==='voice.run_macro')return g5RunMacro(f,cls,ctx);
 if(id==='voice.screen_summary')return g5ScreenSummary(f,ctx);
 if(id==='voice.complete_all_here'){
  const left=g5QuestList(true);if(!left.length)return lmvLines(f,'Quests','Every quest here is already done',[],[]);
  return lmvActFrame(f,'Confirm','Check off all '+left.length+' quests?',left.map(function(x){return x.title}),'Check them all',function(){left.forEach(function(x){toggleQuest(x.id,true);g5Push({t:'quest_undo',id:x.id,label:'Completed quest “'+x.title+'”'})});try{save()}catch(e){}return lmvLines(f,'Done','Checked off '+left.length+' quests',['Say “undo” to take the last one back.'],[lmvOpenChip('quests','Quests')])});
 }
 if(id==='voice.ordinal'){const r=g5Ordinal(f,s,text,ctx);if(r===false||r===null)return lmvLines(f,'Voice','The second one of what?',['Ask for a list first, like “what are my quests” or “what is on my schedule”, then say “complete the first one”.'],[]);return lmvSnapshot()}
 if(id==='life_director.shift_schedule')return g5Shift(f,s,text,ctx);
 if(id==='life_director.swap_activities')return g5Swap(f,s,text,ctx)||lmvLines(f,'Voice','Swap which two?',['Say “swap my workout and deep work today”.'],[]);
 if(id==='life_director.clear_window')return g5Clear(f,s,text,ctx)||lmvLines(f,'Voice','Clear which part of the day?',['Say “clear my afternoon” or “cancel everything after 6 PM today”.'],[]);
 if(id==='life_director.conditional_schedule')return g5Fit(f,s,text,ctx,{});
 if(id==='health_fitness.log_detailed_workout')return g5Workout(f,cls.det,text,ctx);
 if(id==='nutrition.log_water'){const r=g5WaterAdd(f,cls.w.ml,ctx);return r}
 if(id==='nutrition.remaining')return g5Remaining(f,cls,ctx);
 if(id==='nutrition.log_meal')return g5Meal(f,cls.pm,ctx);
 if(id==='nutrition.set_goal'){
  const m=/(calorie|calories|protein|water) goal (?:to|at) (\d+(?:\.\d+)?) ?(g|grams?|ml|liters?|litres?|l|oz|ounces?|kcal|calories?)?/.exec(lmvNormalize(text));if(!m)return lmvLines(f,'Voice','What number?',[],[]);
  let v=+m[2];const k=m[1]==='protein'?'protGoal':m[1]==='water'?'waterGoalMl':'calGoal';
  if(k==='waterGoalMl'){if(/^(?:l|liter|litre)/.test(m[3]||''))v=v*1000;else if(/^o/.test(m[3]||''))v=Math.round(v*29.5735);else if(v<20)v=v*1000}
  mealsEnsure();const old=state.mealsMeta[k]==null?null:state.mealsMeta[k];state.mealsMeta[k]=Math.round(v);try{save()}catch(e){}g5Push({t:'goal',key:k,old:old,label:'Set the '+m[1]+' goal'});
  return lmvLines(f,'Goal',m[1].charAt(0).toUpperCase()+m[1].slice(1)+' goal set to '+Math.round(v).toLocaleString()+(k==='protGoal'?' g':k==='waterGoalMl'?' ml':' kcal'),['Say “undo” to put it back.'],[]);
 }
 if(/^virtual_pets\.(?:query_needs|describe|give_treat|clean_litter)$/.test(id)||id==='virtual_pets.check_status'&&cls.multi)return g5CatDo(f,cls,ctx);
 if(id==='virtual_pets.perform_action'&&cls.all){const all=g5Cats().filter(function(c){return c.home});return lmvHPetAct(f,{act:cls.act})}
 return null;
}
(function(){const d=lmvDomain;lmvDomain=function(cls,text,f,ctx){
 if(/^(?:voice\.|life_director\.(?:shift_schedule|swap_activities|clear_window|conditional_schedule)$|health_fitness\.log_detailed_workout$|nutrition\.(?:log_water|remaining|log_meal|set_goal)$)/.test(cls.id)||/^virtual_pets\.(?:query_needs|describe|give_treat|clean_litter)$/.test(cls.id)||(cls.id==='virtual_pets.check_status'&&cls.multi)||(cls.id==='virtual_pets.perform_action'&&cls.all)){
  f.intentId=cls.id;f.transcript=text;
  try{const r=g5Handle(cls,text,f,ctx);if(r)return r}catch(e){console.warn('[voice g5]',e);return lmvLines(f,'Voice','Something went wrong',['I could not do that: '+(e&&e.message||e)],[])}
 }
 /* free time with places to avoid */
 if(cls.id==='life_director.find_free_time'){
  const cons=g5Constraints(text);
  if(cons.excl.length){
   const today=lmvToday(ctx),sc=lmvScope(cons.text,ctx,false),date=sc.date||today,dp=sc.dayPart&&G5_DP[sc.dayPart],a=dp?dp[0]:7*60,b=dp?dp[1]:22*60;
   const gaps=g5Gaps(date,ctx,a,b,cons.excl,30);if(gaps===null)return lmvLines(f,'Schedule','Life Director is not set up yet',[],[lmvOpenChip('life-director','Life Director',true)]);
   f.intentId=cls.id;
   return lmvLines(f,'Free time',gaps.length?'You are free '+(date===today?'today':lmvNiceDate(date))+', '+cons.notes.join(' and '):'No open time '+(date===today?'today':lmvNiceDate(date))+', '+cons.notes.join(' and '),gaps.slice(0,5).map(function(g){const s0=g5Roundup(g[0]);return g5T(s0)+' to '+g5T(g[1])+'  ('+lmvDurText(g[1]-s0)+')'}),[lmvOpenChip('life-director','Life Director',true)]);
  }
 }
 if(cls.id==='nutrition.check_totals'&&cls.water){const today=lmvToday(ctx),goal=g5WaterGoal(),tot=g5WaterToday(today);f.intentId=cls.id;return lmvLines(f,'Water',tot.toLocaleString()+' ml of water today',['That is '+Math.round(tot/29.5735)+' oz, '+Math.round(tot/goal*100)+'% of '+goal.toLocaleString()+' ml.'],[])}
 return d.apply(this,arguments);
}})();
function g5RunMacro(f,cls,ctx){
 const m=g5Macros()[cls.key];
 if(m)return g5RunCmds(m.cmds),lmvSnapshot();
 const kind=G5_BUILTIN[cls.key];
 if(kind==='morning'){
  const b=g4Brief(f,ctx),lines=b.lines.slice(),q=b.g.q.left;
  if(q.length)lines.push('Top quests: '+q.slice(0,3).map(function(x){return x.name}).join(', '));
  const rn=g4NextItems(ctx,b.g.today);if(rn&&rn.length)lines.push('Today: '+rn.slice(0,4).map(function(x){return lmvClock(x.startEpoch*1000,ctx.tz)+' '+x.title}).join(' · '));
  try{const p=petData();if(p&&p.alive&&petCanFeed(p,Date.now()))lines.push(p.name+' is ready for breakfast.')}catch(e){}
  return lmvLines(f,'Morning','Good morning. Here is your day',lines,[lmvOpenChip('quests','Quests'),lmvOpenChip('life-director','Life Director',true)]);
 }
 const g=g4Goals(ctx),today=g.today,done=lmvActsOn(today).filter(function(a){return a.type!=='coach'}),tm=g4NextItems(ctx,lmvAddDays(today,1));let st=0;try{st=streak()}catch(e){}
 const lines=[done.length?'Done today: '+done.slice(0,5).map(function(a){return a.name}).join(', ')+(done.length>5?' and '+(done.length-5)+' more':''):'Nothing logged today.','XP earned: '+g4Num(g.xp)+' of '+g4Num(g.pace),g.stepsOn?'Steps: '+g4Num(g.steps)+' of '+g4Num(g.goal):'Steps are not connected',g.q.total?'Quests: '+g.q.done+' of '+g.q.total+' done':'No quests today','Streak: '+st+' day'+(st===1?'':'s')];
 if(tm&&tm.length)lines.push('Tomorrow starts with '+tm[0].title+' at '+lmvClock(tm[0].startEpoch*1000,ctx.tz));else lines.push('Nothing is scheduled for tomorrow yet.');
 return lmvLines(f,'Evening','Wrapping up your day',lines,[lmvOpenChip('history','Journal')]);
}

/* ===== the wrapper ===== */
const g5Prev=lmvHandle;
lmvHandle=function(text,meta){
 meta=meta||{};
 if(!lmvEnabled()||(meta.utteranceId&&lmvDelivered.has(meta.utteranceId)))return g5Prev(text,meta);
 let clean=String(text==null?'':text).slice(0,500);
 try{
  g5HookLD();
  const ctx=lmvCtx(),f=lmvFrame,active=f&&(f.state==='AWAITING_CLARIFICATION'||f.state==='PREVIEWED');
  if(g5Negated(clean)){
   lmvDeliver(meta.utteranceId);
   if(active)lmvEnd(f,'Understood, I won’t do that.');
   else{const nf=lmvFrame=lmvNewFrame();nf.transcript=clean;lmvLines(nf,'Voice','Understood, I won’t do that',['Nothing was changed.'],[])}
   g4Reset();lmvRender();return lmvSnapshot();
  }
  if(/^\s*(?:please\s+)?(?:don'?t|do not|dont) (?:forget|let me forget) (?:to )?/i.test(clean))clean=g5Forget(clean);
  /* "schedule X, but not in the morning" / "anytime after 11 AM": only when there is a scheduling verb and no time given */
  if(!active||(f&&f.state==='PREVIEWED')){
   const sl=lmvLead(lmvLow(clean));
   if(/^ (?:(?:can|could|would|will) you )?(?:please )?(?:schedule|put|add|book|plan|block|set up|create)\b/.test(sl)&&!/\bif i (?:have|got)\b/.test(sl)){
    const cons=g5Constraints(clean);
    if(cons.excl.length){const r=g5AutoSchedule(clean,meta,ctx,cons);if(r)return r}
   }
  }
 }catch(e){console.warn('[voice g5]',e)}
 return g5Prev(clean,meta);
};
[['voice.undo_last_action','voice','Undo the last change','Undo that'],['life_director.shift_schedule','life_director','Push the day back','Push everything back 30 minutes'],['life_director.swap_activities','life_director','Swap two activities','Swap my workout and deep work today'],['life_director.clear_window','life_director','Clear part of the day','Clear my afternoon'],['life_director.conditional_schedule','life_director','Fit something in','If I have an hour free this afternoon, schedule guitar practice'],['health_fitness.log_detailed_workout','health_fitness','Log sets, reps, weight or distance','Log bench press 3 sets of 10 at 185 pounds'],['nutrition.log_water','nutrition','Log water','I drank a bottle of water'],['nutrition.log_meal','nutrition','Log a whole meal','For breakfast I had 3 eggs and toast'],['nutrition.remaining','nutrition','What is left of a goal','How much protein do I have left today'],['voice.create_macro','voice','Teach it a shortcut','When I say gym time, log a 60 minute workout and open my workout plan'],['voice.run_macro','voice','Run a shortcut','Start my day'],['voice.screen_summary','voice','What is on this screen','What do I have left here'],['voice.ordinal','voice','The second one','Complete the first one'],['virtual_pets.describe','virtual_pets','A cat’s personality','What is my cat’s personality']].forEach(function(c){LMV_CAPS.push({intentId:c[0],domain:c[1],title:c[2],exampleUtterance:c[3],evaluateStatus:function(){return /^life_director/.test(c[0])?lmvCapStatus(true):'EXECUTABLE'}})});
window.LifeMaxVoiceG5=Object.freeze({
 stack:g5Stack,clearStack:function(){try{localStorage.removeItem(G5_UNDO)}catch(e){}},macros:g5Macros,clearMacros:function(){try{localStorage.removeItem(G5_MACRO)}catch(e){}},
 constraints:g5Constraints,parseWorkout:function(t){return g5ParseWorkout(t,lmvCtx())},parseWater:g5ParseWater,parseMeal:function(t){return g5ParseMeal(t,lmvCtx())},negated:g5Negated,list:function(){return g5List}
});
