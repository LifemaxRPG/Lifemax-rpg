/* ---------- LifeMax Voice generation 4: the way people really talk.
   Self-corrections ("at 8, wait no, make it 9"), two things in one breath ("log a run and check my XP"), editing the card that is
   showing ("make it 45 minutes"), "right after that", a daily briefing, "what's next", goal progress, a suggestion for the time
   you have, comparisons ("did I walk more today than yesterday"), the workout plan and library, a focus timer, opening other
   apps, a "what can I say" list, routines and shortcuts you teach it. Plain web code that wraps the earlier voice segments
   (45, 46, 49); nothing here needs a native change. ---------- */

let g4Frame=null,g4Last=null,g4Queue=[],g4Acc=[],g4On=false,g4Date=null,g4Apps=null,g4AppsBusy=false,g4TimerH=null,g4Launcher=null,g4More=false;
const G4_TIMER='lifemax_voice_timer_v1',G4_ALIAS='lifemax_voice_alias_v1',G4_NOTIF=7770011;

/* ===== 1. self-correction: the later words win, the rest of the sentence stays ===== */
const G4_MARK=/ (?:(?:oh |um |uh |ok |okay )?(?:wait|hold on|hang on)(?: a (?:sec|second|minute))?(?: no| sorry)?|no wait|no no(?: no)?|(?:sorry |no )?i (?:meant|mean)|sorry(?= (?:make|it'?s|let'?s|lets|change|say|do|at|for|on|tomorrow|today|\d))|scratch that(?: make it| make that)?|actually(?= (?:make|let'?s|lets|it'?s|its|i mean|i meant|change|say|do|at |for |on |to |tomorrow|today|noon|midnight|\d|half|an? hour))|make that|make it(?= \d| noon| midnight| tomorrow| today| tonight| (?:monday|tuesday|wednesday|thursday|friday|saturday|sunday))|no make it|no it'?s|or rather|rather) /g;
const G4_DAY='(?:sunday|monday|tuesday|wednesday|thursday|friday|saturday)',G4_MON='(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*';
function g4Strip(l,E){
 if(E.clock){
  l=l.replace(/\b(?:from )?\d{1,2}(?::\d{2})?(?: ?(?:am|pm))? ?(?:to|until|till|-) ?\d{1,2}(?::\d{2})?(?: ?(?:am|pm))?\b(?! ?(?:min|hour|hr|h\b))/g,' ')
   .replace(/\b(?:at|around|about|by|@) ?\d{1,2}(?::\d{2})?(?: ?(?:am|pm))?\b(?! ?(?:min|hour|hr|h\b))/g,' ')
   .replace(/\b\d{1,2}(?::\d{2})? ?(?:am|pm)\b/g,' ').replace(/\b\d{1,2}:\d{2}\b/g,' ').replace(/\b(?:noon|midnight)\b/g,' ');
 }
 if(E.clock||E.part)l=l.replace(/\b(?:in the |this |at )?(?:morning|afternoon|evening|night)\b/g,' ').replace(/\b(?:after|before) work\b/g,' ');
 if(E.date)l=l.replace(new RegExp('\\bday after tomorrow\\b|\\btomorrow\\b|\\btoday\\b|\\btonight\\b|\\b(?:(?:next|this|on) )?'+G4_DAY+'\\b|\\b'+G4_MON+' \\d{1,2}(?:st|nd|rd|th)?\\b','g'),' ');
 if(E.dur)l=l.replace(/\b(?:for )?\d+(?:\.\d+)? ?(?:hours?|hrs?|h)\b(?: (?:and )?\d+ ?(?:minutes?|mins?|m)\b)?|\b(?:for )?\d+ ?(?:minutes?|mins?|min)\b|\b(?:for )?an? hour\b/g,' ');
 return l.replace(/\s+/g,' ');
}
function g4Repair(raw,ctx){
 const n=' '+lmvNormalize(raw)+' ';let last=null,m;G4_MARK.lastIndex=0;
 while((m=G4_MARK.exec(n))){last={i:m.index,len:m[0].length};G4_MARK.lastIndex=m.index+1}
 if(!last)return null;
 let left=n.slice(0,last.i),right=' '+n.slice(last.i+last.len);
 left+=' ';
 for(let k=0;k<4;k++)left=left.replace(/ (?:actually|wait|no|nope|sorry|oh|um|uh|ok|okay|hold on|hang on|scratch that|i mean|i meant|rather|or rather|no no) $/,' ').replace(/\s+$/,' ');
 if(!left.trim()||!right.trim())return null;
 for(let k=0;k<2;k++)right=right.replace(/^ ?(?:(?:no|ok|okay|so)\s)?(?:make (?:it|that)|let'?s make it|lets make it|let'?s say|lets say|let'?s do|lets do|let'?s go with|it'?s|its|it should be|change (?:it|that) to|set (?:it|that) to|say|do|go with|make|put it (?:at|on))\b ?/,' ');
 const bare=/^ ?(\d{1,2})(?::(\d{2}))? ?(am|pm)? ?$/.exec(right);if(bare)right=' at '+bare[1]+(bare[2]?':'+bare[2]:'')+(bare[3]?' '+bare[3]:'')+' ';
 const e=lmvExtract(right,ctx,null),E={clock:e.hour!=null||e.endHour!=null,part:!!(e.daypart||e.anchor),date:!!(e.date||e.dateAmbig),dur:e.dur!=null};
 if(!(E.clock||E.part||E.date||E.dur))return null;
 return (g4Strip(left,E)+' '+right).replace(/\s+/g,' ').trim();
}

/* ===== 2. two things in one sentence, split only where the second half is plainly a new request ===== */
const G4_TRIG='(?:check|show|tell|log|record|schedule|put|add|book|open|launch|feed|pet|play|complete|finish|mark|cancel|move|reschedule|start|set|what|what\'s|whats|how|when|did|read|list|take me|go to|remind|give me|view|find|search|create|delete|remove|skip|is there|am i|do i|have i)';
function g4Valid(a,b,c){
 const la=lmvLow(a).trim(),lb=lmvLow(b);
 if(la.split(' ').length<2||lb.trim().split(' ').length<2)return false;
 if(/\b(?:between|from) [\d:]+(?: ?(?:am|pm))?$/.test(la))return false;                  /* "between 2 and 4 PM" */
 if(new RegExp('^ (?:please |also |then |just )*'+G4_TRIG+'\\b').test(lb))return true;
 /* "schedule coding at 2 PM for an hour and a 15 minute walk at 3:30 PM": a length, a name and a time, after a scheduling first half */
 if(/^ (?:schedule|put|add|book|plan|block)\b/.test(' '+la)&&/^ (?:an? |the |my )?\d+(?:\.\d+)?[\s-]?(?:minutes?|mins?|hours?|hrs?)\b(?: of)? [a-z][a-z ]*? (?:at|from|around) \d/.test(lb))return true;
 return false;
}
function g4Split(raw){
 const txt=String(raw||'').trim(),re=/[,;]?\s+(and then|and also|and|then)\s+/gi;let m;const cands=[];
 while((m=re.exec(txt)))cands.push({i:m.index,end:m.index+m[0].length,c:m[1].toLowerCase()});
 for(let k=0;k<cands.length;k++){const c=cands[k],a=txt.slice(0,c.i),b=txt.slice(c.end);
  if(g4Valid(a,b,c.c)){
   let more=false;const re2=/[,;]?\s+(and then|and also|and|then)\s+/gi;let m2;while((m2=re2.exec(b))){if(g4Valid(b.slice(0,m2.index),b.slice(m2.index+m2[0].length),m2[1])){more=true;break}}
   let second=b;
   if(!new RegExp('^ ?(?:please |also |then |just )*'+G4_TRIG+'\\b','i').test(' '+lmvLow(b).trim())){const v=/^\s*(schedule|put|add|book|plan|block)\b/i.exec(txt);second=(v?v[1]:'schedule')+' '+b}
   return {a:a,b:second,more:more};
  }}
 return null;
}
function g4Reset(){g4Frame=null;g4Queue=[];g4Acc=[];g4On=false;g4Date=null;g4More=false}
function g4Summ(f){
 const lines=(f.notices||[]).map(function(n){return n.text});
 if(f.slot&&!f.query){const s=f.slot;return {title:'Saved “'+f.title+'”',lines:[lmvNiceDate(s.localDate)+' · '+lmvNiceTime(s.localStartTime)+' – '+lmvNiceTime(s.localEndTime)],actions:f.actions||[]}}
 return {title:f.title||f.head||'Done',lines:lines,actions:f.actions||[]};
}
function g4Run(clause){
 const f0=lmvFrame;if(f0&&(f0.state==='CONFIRMED'||f0.state==='CANCELLED'||f0.state==='FAILED'))lmvFrame=null;
 g4Orig(clause,{});
 const f=lmvFrame;g4Frame=f;
 if(f&&f.state==='AWAITING_CLARIFICATION'&&f.question&&f.question.kind==='date'&&g4Date)lmvAnswer({date:g4Date});
}
function g4Pump(){
 if(!g4On)return;
 for(let guard=0;guard<4;guard++){
  const f=lmvFrame;
  if(!f||(g4Frame&&f!==g4Frame)){g4Reset();return}
  if(f.state==='PREVIEWED'||f.state==='AWAITING_CLARIFICATION'){f.pre=g4Acc.map(function(a){return '✓ '+a.title});return}
  if(f.state==='CANCELLED'||f.state==='FAILED'){g4Reset();return}
  if(f.state!=='CONFIRMED')return;
  if(!f.g4done){f.g4done=1;g4Acc.push(g4Summ(f));if(f.slot)g4Date=f.slot.localDate}
  if(g4Queue.length){g4Run(g4Queue.shift());continue}
  /* both parts are finished: one card */
  if(g4Acc.length>1){
   const lines=[],acts=[];g4Acc.forEach(function(a,i){lines.push((i+1)+'. '+a.title);a.lines.slice(0,5).forEach(function(l){lines.push('     '+l)});(a.actions||[]).forEach(function(x){if(!acts.some(function(y){return y.label===x.label}))acts.push(x)})});
   if(g4More)lines.push('I did the first two. Say the rest again and I will do that next.');
   f.pre=null;lmvLines(f,'Done','Did '+g4Acc.length+' things',lines,acts.slice(0,3));f.g4done=1;
  }
  g4Reset();return;
 }
}

/* ===== 3. edit the card that is showing ("make it 45 minutes") ===== */
function g4Edit(clean,ctx){
 const f=lmvFrame;if(!f||f.state!=='PREVIEWED'||!f.slot||f.act||f.query||f.intentId!=='life_director.create_activity')return null;
 let s=lmvLow(clean).replace(/^ (?:(?:no|nope|ok|okay|um|uh|hmm|wait|actually|sorry|oh|well|so|and|also|please|hey|lifemax) )+/,' ');
 if(/^ (?:yes|yeah|yep|sure|confirm|save|cancel|never ?mind|no|nope|stop|forget)\b ?$/.test(s))return null;
 const tm=/^ (?:call it|name it|rename it(?: to)?|rename this(?: to)?|change (?:the )?(?:title|name)(?: of it)?(?: to)?|set (?:the )?(?:title|name) to|title it|label it)\s+(.+?)\s*\??$/i.exec(' '+String(clean).trim().replace(/^(?:no|nope|ok|okay|um|uh|wait|actually|sorry|oh|well|so|and|please)[,.]?\s+/i,'')+' ');
 const S=f.slot,sh=+S.localStartTime.slice(0,2),sm=+S.localStartTime.slice(3,5);
 const base={title:f.title,date:S.localDate,hour:sh,minute:sm,mer:'24',daypart:null,anchor:null,dur:S.durationMinutes,endHour:null,endMinute:0,endMer:null,dst:null,dateAmbig:null};
 let patch=null,why='';
 if(tm){let t=tm[1].trim().replace(/[.?!]+$/,'');if(!t||t.length>60)return null;t=t.charAt(0).toUpperCase()+t.slice(1);patch=Object.assign({},base,{title:t});why='Renamed to “'+t+'”.'}
 if(!patch){
  const sf=/^ (?:(?:push|move|bump|shift|delay)(?: it| this| that)? )?(back|forward|earlier|later|up|ahead)?(?: by)? ?(\d+(?:\.\d+)?) ?(minutes?|mins?|min|hours?|hrs?|hr|h|m)(?: (earlier|later|back|forward|sooner))?\s*\??$/.exec(lmvNormalize(clean).replace(/^ ?(?:no|nope|ok|okay|um|uh|wait|actually|sorry|oh|well|so|and|please) /,' ').replace(/^/,' '));
  const hasShiftVerb=/^ (?:push|move|bump|shift|delay)\b/.test(s)||/ (?:earlier|later|sooner)\s*\??$/.test(s);
  if(sf&&hasShiftVerb){
   const w=(sf[1]||sf[4]||'later'),neg=/^(earlier|forward|up|ahead|sooner)$/.test(w),k=/^h/.test(sf[3])?60:1,delta=Math.round(+sf[2]*k)*(neg?-1:1),tot=sh*60+sm+delta;
   if(tot<0||tot>=1440)return null;
   patch=Object.assign({},base,{hour:Math.floor(tot/60),minute:tot%60});why='Moved '+lmvDurText(Math.abs(delta))+(neg?' earlier.':' later.');
  }
 }
 if(!patch){
  let t=s.replace(/^ (?:make it|make that|make the (?:duration|length|time|date|day)|let'?s make it|lets make it|change (?:the )?(?:duration|length|time|date|day|start(?: time)?)(?: to)?|change it to|set (?:the )?(?:duration|length|time|date|day) to|set it to|move it to|move this to|move that to|move it|reschedule(?: it)?(?: to| for)?|switch it to|switch to|start at|start it at|begin at|let'?s say|how about|what about|just|put it|do it|have it) ?/,' ').replace(/ (?:instead|actually|rather|please|then)\s*\??$/,' ').replace(/\s*\?\s*$/,' ');
  if(/^ \d{1,2}(?::\d{2})? $/.test(t)&&!/\d{1,2}:\d{2}/.test(t))t=' at '+t.trim()+' ';
  const e=lmvExtract(t,ctx,null);
  const hasTime=e.hour!=null,hasRange=e.hour!=null&&e.endHour!=null,hasDate=!!(e.date||e.dateAmbig),hasDur=e.dur!=null,hasPart=!!e.daypart&&e.hour==null;
  if(!(hasTime||hasDate||hasDur||hasPart)||e.title||e.anchor)return null;
  patch=Object.assign({},base);const bits=[];
  if(hasTime){
   let h=e.hour,mer='24';
   if(e.mer==='am'||e.mer==='pm'){mer=e.mer}
   else if(h>=13||h===0){mer='24'}
   else if(e.daypart){mer=e.daypart==='morning'?'am':'pm'}
   else mer=sh>=12?'pm':'am';
   if(h===12&&mer==='am'&&!e.mer&&!e.daypart)mer='24';
   patch.hour=h;patch.minute=e.minute||0;patch.mer=mer;
   if(hasRange){patch.endHour=e.endHour;patch.endMinute=e.endMinute||0;patch.endMer=e.endMer||null;patch.dur=null}
   bits.push('time');
  }else if(hasPart){const dm=lmvDaypartDefault(e.daypart);patch.hour=Math.floor(dm/60);patch.minute=dm%60;patch.mer='24';bits.push('time')}
  if(hasDur){patch.dur=e.dur;patch.endHour=null;bits.push('length')}
  if(hasDate){patch.date=e.date||e.dateAmbig.options[0];bits.push('day')}
  why='Changed the '+bits.join(' and ')+'.';
 }
 const sn=f.raw?Object.assign({},f.raw):null;
 lmvMerge(f,patch);lmvAdvance(f,ctx);
 if(f.state==='PREVIEWED'&&f.slot)f.notices.unshift({kind:'info',text:why+' Now '+lmvNiceDate(f.slot.localDate)+', '+lmvNiceTime(f.slot.localStartTime)+' – '+lmvNiceTime(f.slot.localEndTime)+' ('+lmvDurText(f.slot.durationMinutes)+').'});
 else if(!f.slot&&sn&&f.state==='AWAITING_CLARIFICATION'){}
 lmvRender();return lmvSnapshot();
}

/* ===== 4. "right after that", "right before deep work" ===== */
function g4Chain(clean,ctx,meta){
 const s=lmvLow(clean);
 const m=/^ (?:and |also |then |now )?(.+?) (?:right |immediately |directly |just )?(after|before) (that|it|this|that one|the (?:last|previous|first) (?:one|activity|block|thing|session)|(?:my |the )?[a-z][a-z0-9 ]*?) ?\??$/.exec(s);
 if(!m)return null;
 const left=m[1],dir=m[2],tgt=m[3].trim();
 if(/^(?:my |the )?(?:work|school|class)\b/.test(tgt)||/\b(?:today|tomorrow|tonight|am|pm|noon|midnight)\b|\d/.test(tgt))return null;     /* "after work" is an anchor, not an activity */
 if(/^(?:what|when|how|did|do|is|are|am|where|which|who|can you tell)\b/.test(left.trim()))return null;
 const create=/\b(put|add|schedule|book|create|block|set up|plan|squeeze|throw in|slot|insert)\b/.test(left)||/^ ?(?:an? |the )?\d+(?:\.\d+)?[\s-]?(?:minutes?|mins?|hours?|hrs?)\b/.test(left.trim());
 if(!create)return null;
 const pron=/^(that|it|this|that one|the (?:last|previous|first) .*)$/.test(tgt);
 let ref=null;
 if(pron){
  if(g4Last&&Date.now()-g4Last.ts<6*3600*1000)ref=g4Last;
  else{const t=lmvToday(ctx),r=lmvResolve('',ctx,[t,lmvAddDays(t,1)]);if(r&&r.item){const it=r.item,a=lmvParts(it.startEpoch*1000,ctx.tz),b=lmvParts(it.endEpoch*1000,ctx.tz);ref={title:it.title,date:lmvYmd(a),start:lmvPad(a.h)+':'+lmvPad(a.mi),end:lmvPad(b.h)+':'+lmvPad(b.mi)}}}
  if(!ref){const f=lmvFrame=lmvNewFrame();f.transcript=clean;lmvLines(f,'Voice','Right after what?',['Schedule something first, then say “put a 15 minute break right after that”.'],[]);lmvRender();return lmvSnapshot()}
 }else{
  const t=lmvToday(ctx),dates=[t,lmvAddDays(t,1)];if(g4Last&&dates.indexOf(g4Last.date)<0)dates.push(g4Last.date);
  const r=lmvResolve(tgt,ctx,dates);
  if(r&&r.many){const f=lmvFrame=lmvNewFrame();f.transcript=clean;lmvLines(f,'Voice','Which one do you mean?',r.many.map(function(x){return x.title}),[]);lmvRender();return lmvSnapshot()}
  if(!r||!r.item)return null;
  const it=r.item,a=lmvParts(it.startEpoch*1000,ctx.tz),b=lmvParts(it.endEpoch*1000,ctx.tz);ref={title:it.title,date:lmvYmd(a),start:lmvPad(a.h)+':'+lmvPad(a.mi),end:lmvPad(b.h)+':'+lmvPad(b.mi)};
 }
 const ex=lmvExtract(left.replace(/\b(?:the |a |an )?(?:last|previous) (?:one|activity|block)\b/,' ').replace(/\b(?:squeeze|fit|slot|throw|tuck)(?: me| it)? in\b/g,' ').replace(/\b(?:squeeze|fit)\b/g,' '),ctx,null);
 let dur=ex.dur;const dflt=dur==null;if(dflt)dur=dir==='before'?30:null;
 const f=lmvFrame=lmvNewFrame();f.transcript=clean;f.confidence=meta&&meta.confidence!=null?meta.confidence:1;
 let hh,mm;
 if(dir==='after'){const p=ref.end.split(':').map(Number);hh=p[0];mm=p[1]}
 else{const p=ref.start.split(':').map(Number),st=p[0]*60+p[1]-dur;if(st<0){lmvLines(f,'Voice','That would start before midnight',['Pick a shorter length, or a time of your own.'],[]);lmvRender();return lmvSnapshot()}hh=Math.floor(st/60);mm=st%60}
 lmvMerge(f,{title:ex.title,date:ref.date,hour:hh,minute:mm,mer:'24',dur:dur,daypart:null,anchor:null,endHour:null,endMinute:0,endMer:null});
 lmvAdvance(f,ctx);
 if(f.state==='PREVIEWED'&&f.slot)f.notices.unshift({kind:'info',text:'Placed right '+dir+' “'+ref.title+'” ('+lmvNiceTime(dir==='after'?ref.end:ref.start)+').'});
 lmvRender();return lmvSnapshot();
}

/* ===== 5. new domain answers ===== */
function g4Out(id,extra){return Object.assign({id:id,conf:.92,question:true},extra||{})}
function g4Classify(text){
 const s=lmvLead(lmvLow(text)).replace(/\s*\?+\s*$/,' '),T=s.trim();
 if(/^ (?:what can i say|what can you do|what do you understand|voice (?:commands|help)|(?:show|list) (?:me )?(?:the |all )?(?:voice )?commands|what are the (?:voice )?commands|how do i use (?:voice|this)|help(?: me)?) ?\??$/.test(s))return g4Out('assistant.voice_help');
 /* teaching a shortcut */
 const ta=/^ (?:when i say|if i say|whenever i say)\s+["“']?(.{2,40}?)["”']?,?\s+(?:then |you |please )*(?:do|run|say|means?|should (?:do|run))\s+["“']?(.{3,120}?)["”']?\s*$/.exec(s);
 if(ta)return g4Out('assistant.teach_alias',{phrase:ta[1].trim(),cmd:ta[2].trim()});
 const fa=/^ (?:forget|delete|remove) (?:my |the )?(?:shortcut |routine )?["“']?(.{2,40}?)["”']?(?: shortcut)? ?$/.exec(s);
 if(fa&&g4AliasGet(fa[1].trim())!=null)return g4Out('assistant.forget_alias',{phrase:fa[1].trim()});
 if(/^ (?:what|which|list|show)(?: me)?(?: are)?(?: my| the)? (?:shortcuts|voice shortcuts|routines)(?: do i have)? ?\??$/.test(s))return g4Out('assistant.list_alias');
 /* timer and focus sessions */
 if(/^ (?:cancel|stop|clear|end|delete|turn off|kill)(?: my| the)?(?: focus)? ?(?:timer|countdown|pomodoro|focus session|focus timer)s? ?$/.test(s))return g4Out('navigation_system.start_focus_session',{act:'cancel'});
 if(/\b(?:how (?:much )?(?:time|long)|how many (?:minutes|seconds)|what'?s the time)\b.*\b(?:left|remaining)\b|\btime (?:left|remaining)\b|^ (?:is|do i have) (?:my|the|a) timer\b/.test(s)&&(g4TimerGet()||/\btimer|focus|pomodoro|countdown\b/.test(s)||/^ how (?:much|long)(?: time)?(?: is| do i have)? (?:left|remaining|to go)\b/.test(s)))return g4Out('navigation_system.start_focus_session',{act:'query'});
 if(/\b(?:timer|pomodoro|focus (?:session|timer|block)|countdown)\b/.test(s)&&/\b(?:start|set|begin|run|put on|create|kick off|fire up)\b/.test(s)||/^ (?:start|begin) (?:a |an )?\d+(?:\.\d+)? ?(?:minutes?|mins?|min|hours?|hrs?)\b.*\b(?:focus|pomodoro|timer|study|work|deep work)\b/.test(s)||/^ (?:start|begin) (?:a |an |my )?(?:pomodoro|focus session)\b/.test(s))return g4Out('navigation_system.start_focus_session',{act:'start'});
 /* launching another app */
 const ap=/^ (?:open|launch|start|run|fire up|bring up|switch to) (?:the |my )?(.+?)(?: app| application)? ?$/.exec(s);
 if(ap){
  const nm=ap[1].trim(),sys=/^(?:android |phone |system |device )settings$/.test(nm);
  const isSection=!sys&&LMV_SECTIONS.some(function(x){return x.re.test(nm)});
  if(sys||(!isSection&&g4KnownApp(nm)))return g4Out('navigation_system.launch_external_app',{app:sys?'settings':nm,sys:sys});
 }
 /* workout plan and library */
 if(/^ (?:what'?s|what is|what do i have|read|tell me|what workout (?:do i have|is))(?: me)?(?: on| in| for)?(?: my| the| today'?s)?(?: workout)? plan\b|^ what'?s (?:my )?workout(?: plan)?(?: for)? today|^ what workout do i have today|^ what (?:is|are) (?:my|the) workouts? (?:for )?today/.test(s)||/^ what'?s on my workout plan/.test(s))return g4Out('fitness.workout_plan',{act:'show'});
 if(/^ (?:start|begin|do|kick off|let'?s do) (?:today'?s|my|the) (?:workout|workout plan|training|training plan|plan)\b/.test(s)||/^ start (?:a |my )?workout ?$/.test(s))return g4Out('fitness.workout_plan',{act:'start'});
 const lib=/^ (?:search|find|look up|look for|look through)(?: in| through)?(?: my| the)? (?:library|media|books)(?: for)? (.+?) ?\??$/.exec(s)||/^ (?:search|find|look up|look for) (.+?) (?:in|on) (?:my |the )?(?:library|media)\s*\??$/.exec(s);
 if(lib)return g4Out('library.search',{q:lib[1].trim()});
 /* routines */
 const rt=/^ (?:start|run|do|begin|play|open) (?:my |the )?(morning|evening|night|bed ?time|wind ?down|midday|daily) (?:routine|ritual|briefing)\s*\??$/.exec(s)||/^ (good morning|good evening|good night) ?$/.exec(s);
 if(rt)return g4Out('assistant.routine',{which:/morning/.test(rt[1])?'morning':/evening|night|bed|wind/.test(rt[1])?'evening':'morning'});
 /* goals, briefing, what is next */
 if(/\bhow close am i\b.*\bgoals?\b|\bam i on track\b|^ (?:did|have) i (?:hit|reach|reached|make|made|meet|met|complete|completed|finish|finished) (?:my |the )?(?:daily |step |xp )?(?:goals?|targets?)\b|\bwhat do i have left(?: to do)?(?: today)?\b(?!.*\b(?:schedule|quests?|calendar|plans?)\b)|\bhow many more steps\b|\bhow (?:much|many) (?:more )?(?:xp|steps)\b.*\b(?:need|left|to (?:hit|reach|get)|until|for)\b.*\b(?:goal|today|daily)\b|\bhow much (?:xp )?(?:do i have )?left\b.*\b(?:goal|today)\b|\bam i (?:there|done) (?:yet|for the day|today)\b|\b(?:daily )?goals? progress\b/.test(s)&&!/\blevel\b/.test(s))return g4Out('assistant.check_goal_progress');
 if(/^ (?:how am i doing|how'?s my day|how is my day|hows my day)\b|\b(?:daily|morning|evening|day'?s?) (?:briefing|summary|rundown|recap|update)\b|^ (?:give me |show me |read me |what'?s )?(?:my |a |the )?(?:status report|status update|briefing|rundown|day summary|summary of (?:my|the) day)\b|^ (?:catch me up|where do i stand|brief me|status report|daily status)\b/.test(s))return g4Out('assistant.daily_briefing');
 if(/^ (?:what'?s|what is|whats) (?:next|up next|coming up|on deck)\b|^ what (?:do i have|have i got|is) (?:next|coming up)\b|^ what am i (?:supposed to be doing|meant to be doing|up to|doing)(?: right)?(?: now)?\b|^ what should i be doing(?: right)?(?: now)?\b|^ when is my next (?:activity|thing|event|appointment|block|session|commitment)\b|^ what'?s (?:happening|on) (?:right )?now\b|^ what'?s up next\b|^ what do i do (?:next|now)\b|^ what'?s my next\b/.test(s))return g4Out('life_director.whats_next');
 if(/^ what should i do\b|^ what can i do\b|^ i'?m bored\b|^ i am bored\b|^ (?:suggest|recommend|give me) (?:me )?(?:something|an activity|a task|an idea|ideas|something to do)\b|\bi have (?:\d+(?:\.\d+)?|an?|half an?) ?(?:minutes?|mins?|hours?|hrs?|hour) (?:free|spare|to spare|to kill|available|of free time)\b|\bhow (?:can|do) i (?:get|earn|make|gain) more xp\b|^ what should i work on\b|^ (?:any )?(?:ideas|suggestions)\b|^ what (?:else )?can i (?:do|knock out|get done)\b|^ i (?:want|need) something to do\b|^ give me something to do\b/.test(s))return g4Out('assistant.recommend_action');
 /* comparisons and trends */
 if(/\b(?:more|less|fewer|better|worse|higher|lower)\b.*\b(?:than|compared to|versus|vs)\b/.test(s)&&/\b(?:walk|walked|steps?|xp|earn|earned|workouts?|exercise|worked out|calories|ate|eat|activit\w*|logged|do|did|get|got|burn|burned)\b/.test(s)||/^ compare\b/.test(s)||/\bhow does (?:this|today|yesterday|my)\b.*\bcompare\b/.test(s)||/\b(?:best|worst|highest|lowest|biggest|top)\b.*\b(?:day|days)\b|\b(?:what|which) day\b.*\b(?:most|least|fewest|best|worst|highest|lowest)\b|\bwhich day did i\b.*\b(?:most|least|fewest|best|worst)\b/.test(s)&&/\b(?:steps?|xp|workouts?|calories|walk\w*|activit\w*|earn\w*|exercise|did|get|got)\b/.test(s)||/\baverage (?:daily |per day |a day )?(?:steps?|xp|calories|workouts?)\b|\b(?:steps?|xp|calories) (?:on average|per day|a day|average)\b/.test(s)&&/\b(?:week|month|days)\b/.test(s)||/^ (?:am i|have i been|is my \w+) (?:walking|earning|doing|getting|exercising)\b.*\b(?:more|less|better|worse)\b/.test(s))return g4Out('analytics.compare_periods');
 return null;
}
(function(){const c=lmvClassify;lmvClassify=function(text){let g=null;try{g=g4Classify(text)}catch(e){console.warn('[voice g4]',e)}return g||c.apply(this,arguments)}})();

function g4Cap(s){return s.charAt(0).toUpperCase()+s.slice(1)}
function g4Num(n){return Math.round(n).toLocaleString()}
function g4NextItems(ctx,date){
 const a=lmvLD.api();if(!a||!a.ready())return null;let items=null;try{items=a.dayItems(date)}catch(e){return null}
 return (items||[]).filter(function(it){return it.kind!=='SLEEP'&&it.kind!=='PREFERRED'&&it.kind!=='UNAVAILABLE'});
}
function g4Rem(ctx,date){
 const items=g4NextItems(ctx,date),now=Math.floor(ctx.now/1000);if(items===null)return null;
 return {cur:items.filter(function(x){return x.startEpoch<=now&&x.endEpoch>now})[0]||null,next:items.filter(function(x){return x.startEpoch>now})[0]||null};
}
function g4Mark(it,ctx){if(!it)return;const a=lmvParts(it.startEpoch*1000,ctx.tz),b=lmvParts(it.endEpoch*1000,ctx.tz);g4Last={id:it.id,title:it.title,date:lmvYmd(a),start:lmvPad(a.h)+':'+lmvPad(a.mi),end:lmvPad(b.h)+':'+lmvPad(b.mi),ts:Date.now()}}
function g4Quests(){
 if(typeof state==='undefined'||!Array.isArray(state.quests))return {total:0,left:[],done:0};
 const day=state.quests.filter(function(q){try{return q.cadence==='daily'&&questToday(q)&&!questLocked(q)}catch(e){return false}});
 const done=day.filter(function(q){try{return isQuestDone(q)}catch(e){return false}});
 return {total:day.length,left:day.filter(function(q){return done.indexOf(q)<0}),done:done.length};
}
function g4Goals(ctx){
 const today=lmvToday(ctx),xp=lmvXpOf(lmvActsOn(today));let pace=100;try{pace=ovPace(ovMap())}catch(e){}
 const sm=lmvStepMap(),H=sm.H,steps=sm.map[today]!=null?sm.map[today]:0,goal=(H&&H.goal)||8000,on=!!(H&&H.on);
 return {today:today,xp:xp,pace:pace,steps:steps,goal:goal,stepsOn:on,q:g4Quests()};
}
function g4Brief(f,ctx){
 const g=g4Goals(ctx),lv=characterLevel();let title='';try{title=titleFor(lv.level)}catch(e){}let st=0;try{st=streak()}catch(e){}
 const lines=['Level '+lv.level+(title?' · '+title:'')+' · '+st+' day streak',
  'XP today: '+g4Num(g.xp)+' / '+g4Num(g.pace)+(g.xp>=g.pace?' (+'+Math.round((g.xp/g.pace-1)*100)+'%)':' ('+Math.round(g.xp/g.pace*100)+'%)')];
 lines.push(g.stepsOn?'Steps: '+g4Num(g.steps)+' / '+g4Num(g.goal):'Steps: Samsung Health is not connected');
 lines.push(g.q.total?'Quests: '+g.q.done+' of '+g.q.total+' done today'+(g.q.left.length?' · '+g.q.left.length+' to go':''):'Quests: none active today');
 const rn=g4Rem(ctx,g.today);
 if(rn===null)lines.push('Schedule: Life Director is not set up yet');
 else if(rn.cur)lines.push('Now: '+rn.cur.title+' until '+lmvClock(rn.cur.endEpoch*1000,ctx.tz)+(rn.next?' · next '+rn.next.title+' at '+lmvClock(rn.next.startEpoch*1000,ctx.tz):''));
 else if(rn.next)lines.push('Next: '+rn.next.title+' at '+lmvClock(rn.next.startEpoch*1000,ctx.tz));
 else lines.push('Nothing else scheduled today');
 return {lines:lines,g:g,lv:lv,st:st};
}
function g4Handle(cls,text,f,ctx){
 const id=cls.id,today=lmvToday(ctx);
 if(id==='assistant.voice_help')return g4Help(f);
 if(id==='assistant.daily_briefing'){const b=g4Brief(f,ctx);return lmvLines(f,'Briefing',b.g.xp+' XP today · level '+b.lv.level,b.lines,[lmvOpenChip('dashboard','Overview')])}
 if(id==='life_director.whats_next'){
  const rn=g4Rem(ctx,today);
  if(rn===null)return lmvLines(f,'Schedule','Life Director is not set up yet',['Open Life Director to set your sleep and work hours first.'],[lmvOpenChip('life-director','Life Director',true)]);
  const now=Math.floor(ctx.now/1000),lines=[];let head;
  if(rn.cur){const left=Math.max(1,Math.round((rn.cur.endEpoch-now)/60));lines.push('Right now: '+rn.cur.title+' ('+lmvClock(rn.cur.startEpoch*1000,ctx.tz)+' – '+lmvClock(rn.cur.endEpoch*1000,ctx.tz)+'), '+lmvDurText(left)+' left');g4Mark(rn.cur,ctx);head='Now: '+rn.cur.title}
  if(rn.next){const mins=Math.max(1,Math.round((rn.next.startEpoch-now)/60));lines.push('Next: '+rn.next.title+' at '+lmvClock(rn.next.startEpoch*1000,ctx.tz)+', in '+lmvDurText(mins));if(!rn.cur){g4Mark(rn.next,ctx);head='Next: '+rn.next.title+' in '+lmvDurText(mins)}}
  else{lines.push('You have nothing else scheduled for the rest of today.');
   const tm=g4NextItems(ctx,lmvAddDays(today,1));if(tm&&tm.length)lines.push('Tomorrow starts with '+tm[0].title+' at '+lmvClock(tm[0].startEpoch*1000,ctx.tz)+'.');
   if(!rn.cur)head='Nothing else today'}
  return lmvLines(f,'Right now',head||'Nothing else today',lines,[lmvOpenChip('life-director','Life Director',true)]);
 }
 if(id==='assistant.check_goal_progress'){
  const g=g4Goals(ctx),lines=[],rx=Math.max(0,Math.round(g.pace-g.xp)),rs=Math.max(0,Math.round(g.goal-g.steps));
  lines.push(rx?'XP: '+g4Num(g.xp)+' of '+g4Num(g.pace)+' · '+g4Num(rx)+' to go':'XP: '+g4Num(g.xp)+' of '+g4Num(g.pace)+' · goal reached');
  if(g.stepsOn)lines.push(rs?'Steps: '+g4Num(g.steps)+' of '+g4Num(g.goal)+' · '+g4Num(rs)+' to go':'Steps: '+g4Num(g.steps)+' of '+g4Num(g.goal)+' · goal reached');
  else lines.push('Steps: connect Samsung Health on the Overview to track them');
  lines.push(g.q.total?(g.q.left.length?'Quests: '+g.q.left.length+' left ('+g.q.left.slice(0,3).map(function(q){return q.name}).join(', ')+')':'Quests: all '+g.q.total+' done'):'Quests: none active today');
  const all=!rx&&(!g.stepsOn||!rs)&&!g.q.left.length;
  return lmvLines(f,'Goals',all?'You hit every daily goal':'Not there yet',lines,[lmvOpenChip('dashboard','Overview'),lmvOpenChip('quests','Quests')]);
 }
 if(id==='assistant.recommend_action')return g4Recommend(f,text,ctx);
 if(id==='analytics.compare_periods')return g4Compare(f,text,ctx);
 if(id==='fitness.workout_plan')return g4Plan(f,cls);
 if(id==='library.search')return g4Library(f,cls);
 if(id==='navigation_system.start_focus_session')return g4TimerDo(f,cls,text);
 if(id==='navigation_system.launch_external_app')return g4AppDo(f,cls);
 if(id==='assistant.routine')return g4Routine(f,cls,ctx);
 if(id==='assistant.teach_alias'){
  const r=g4AliasPut(cls.phrase,cls.cmd);
  return r.ok?lmvLines(f,'Shortcut','Saved shortcut “'+cls.phrase+'”',['When you say “'+cls.phrase+'” I will do: '+cls.cmd+'.','Say “forget '+cls.phrase+'” to remove it.'],[]):lmvLines(f,'Shortcut','Could not save that shortcut',[r.msg],[]);
 }
 if(id==='assistant.forget_alias'){g4AliasDel(cls.phrase);return lmvLines(f,'Shortcut','Forgot “'+cls.phrase+'”',[],[])}
 if(id==='assistant.list_alias'){const a=g4AliasAll(),k=Object.keys(a);return lmvLines(f,'Shortcuts',k.length?k.length+' shortcut'+(k.length===1?'':'s'):'No shortcuts yet',k.length?k.map(function(x){return '“'+x+'” → '+a[x]}):['Teach one: “when I say gym time, do log a 45 minute workout”.'],[])}
 return null;
}
(function(){const d=lmvDomain;lmvDomain=function(cls,text,f,ctx){
 if(/^(?:assistant|analytics|fitness|library)\./.test(cls.id)||/^life_director\.whats_next$/.test(cls.id)||/^navigation_system\.(?:start_focus_session|launch_external_app)$/.test(cls.id)){
  f.intentId=cls.id;f.transcript=text;
  try{return g4Handle(cls,text,f,ctx)}catch(e){console.warn('[voice g4]',e);return lmvLines(f,'Voice','Something went wrong',['I could not do that: '+(e&&e.message||e)],[])}
 }
 return d.apply(this,arguments);
}})();

/* ----- smart suggestion ----- */
function g4Recommend(f,text,ctx){
 const n=lmvNormalize(text),wm=/(\d+(?:\.\d+)?) ?(minutes?|mins?|min|hours?|hrs?|hr)\b/.exec(n);
 const win=wm?Math.round(+wm[1]*(/^h/.test(wm[2])?60:1)):null,g=g4Goals(ctx),c=[];
 g.q.left.forEach(function(q){c.push({score:40+(q.xp||0),t:q.name,why:'a daily quest'+(q.xp?' worth '+q.xp+' XP':''),min:20,chip:lmvOpenChip('quests','Quests')})});
 try{if(state.plan){const d=planDay(getToday());if(d&&d.kind!=='rest'&&!dayDone(getToday()))c.push({score:70,t:'Today’s workout: '+d.label,why:'your plan for today',min:45,chip:lmvOpenChip('plan','Workout plan')})}}catch(e){}
 if(g.stepsOn&&g.steps<g.goal){const need=g.goal-g.steps,mins=Math.max(5,Math.round(need/100)),use=win?Math.min(mins,win):mins;c.push({score:50,t:'A walk',why:'about '+g4Num(use*100)+' steps in '+lmvDurText(use)+' ('+g4Num(need)+' to your goal)',min:use,chip:{label:'Log a '+use+' min walk',run:function(){lmvHandle('log a '+use+' minute walk',{})}}})}
 try{const p=petData();if(p&&p.alive&&!(p.vacation&&p.vacation.on)&&petStage(p)>=2&&petCanFeed(p,Date.now()))c.push({score:45,t:'Feed '+p.name,why:p.name+' is ready for a meal',min:2,chip:{label:'Feed',run:function(){lmvPetDo('feed')}}})}catch(e){}
 const rn=g4Rem(ctx,g.today);
 if(rn&&rn.next){const mins=Math.round((rn.next.startEpoch-Math.floor(ctx.now/1000))/60);if(win&&mins>0&&mins<win)c.forEach(function(x){if(x.min>mins)x.score-=15})}
 let list=c.filter(function(x){return !win||x.min<=win}).sort(function(a,b){return b.score-a.score});
 if(!list.length&&c.length)list=c.slice().sort(function(a,b){return a.min-b.min});
 if(!list.length)list=[{t:'Log something small',why:'ten minutes of anything earns XP',min:10,chip:lmvOpenChip('log','Log activity')}];
 const top=list.slice(0,2),head=(win?'With '+lmvDurText(win)+': ':'')+top[0].t;
 return lmvLines(f,'Suggestion',head,top.map(function(x,i){return (i+1)+'. '+x.t+' — '+x.why}),top.map(function(x){return x.chip}));
}

/* ----- comparisons and trends ----- */
function g4Metric(s){
 if(/\b(?:calories?|kcal|ate|eaten|eat|food)\b/.test(s))return 'kcal';
 if(/\bxp\b|\bexperience\b|\bearn\w*\b/.test(s))return 'xp';
 if(/\b(?:workouts?|exercise\w*|worked out|work out|trained|training)\b/.test(s))return 'workouts';
 if(/\b(?:walk\w*|steps?|stairs|moved|move)\b/.test(s))return 'steps';
 if(/\bactivit\w*|\blogged\b/.test(s))return 'acts';
 return 'steps';
}
const G4_UNIT={steps:'steps',xp:'XP',workouts:'workouts',kcal:'kcal',acts:'activities'};
function g4IsEx(a){try{if(ecoPassive(a)||a.type==='coach'||a.type==='quest')return false}catch(e){}return a.type==='strength'||a.type==='running'||a.type==='plan'||a.primary==='physique'||a.primary==='endurance'}
function g4DayVal(m,d,sm){
 if(m==='steps')return sm.map[d]!=null?sm.map[d]:0;
 if(m==='xp')return lmvXpOf(lmvActsOn(d));
 if(m==='workouts')return lmvActsOn(d).filter(g4IsEx).length;
 if(m==='acts')return lmvActsOn(d).filter(function(a){return a.type!=='coach'}).length;
 if(m==='kcal'){let k=0;try{mealsEnsure();state.foodLog.forEach(function(e){if(e.date===d)k+=Number(e.kcal)||0})}catch(e){}return k}
 return 0;
}
function g4Compare(f,text,ctx){
 const s=' '+lmvPhon(text)+' ',m=g4Metric(s),unit=G4_UNIT[m],today=lmvToday(ctx),sm=lmvStepMap();
 if(m==='steps'&&!(sm.H&&sm.H.on))return lmvLines(f,'Activity','Step counting is not connected',['Connect Samsung Health on the Overview to compare steps.'],[lmvOpenChip('dashboard','Overview')]);
 const rngScope=function(t){const sc=lmvScope(t,ctx,true);return sc.explicit&&sc.kind!=='all'?sc:null};
 const sum=function(sc){return lmvDays(sc).reduce(function(a,d){return a+g4DayVal(m,d,sm)},0)};
 const fmt=function(v){return g4Num(v)+' '+unit};
 /* best / worst day, and averages, inside one period */
 const ext=/\b(best|worst|highest|lowest|biggest|top|most|least|fewest)\b/.exec(s);
 if((ext&&/\b(?:day|days)\b/.test(s))||/\baverage\b|\bon average\b|\bper day\b|\ba day\b/.test(s)){
  let sc=rngScope(s)||lmvScope('this week',ctx,true);
  if(sc.kind==='day')sc=lmvScope('this week',ctx,true);
  const days=lmvDays(sc).filter(function(d){return d<=today}),vals=days.map(function(d){return {d:d,v:g4DayVal(m,d,sm)}});
  if(!days.length)return lmvLines(f,'Compare','Nothing to look at yet',['That period has not started.'],[]);
  const tot=vals.reduce(function(a,x){return a+x.v},0),avg=tot/vals.length;
  if(/\baverage\b|\bon average\b|\bper day\b|\ba day\b/.test(s)&&!(ext&&/\bday\b/.test(s)&&/\b(?:best|worst|highest|lowest)\b/.test(s)))
   return lmvLines(f,'Compare',g4Num(avg)+' '+unit+' a day '+sc.label,['Total '+fmt(tot)+' over '+vals.length+' day'+(vals.length===1?'':'s')],[lmvOpenChip('dashboard','Overview')]);
  const low=/\b(worst|lowest|least|fewest)\b/.test(s),pick=vals.slice().sort(function(a,b){return low?a.v-b.v:b.v-a.v})[0];
  const other=vals.slice().sort(function(a,b){return low?b.v-a.v:a.v-b.v})[0];
  return lmvLines(f,'Compare',(low?'Lowest':'Best')+' day for '+unit+' '+sc.label+': '+lmvNice(pick.d)+' ('+fmt(pick.v)+')',[(low?'Highest':'Lowest')+': '+lmvNice(other.d)+' ('+fmt(other.v)+')','Average '+g4Num(avg)+' '+unit+' a day over '+vals.length+' day'+(vals.length===1?'':'s')],[lmvOpenChip('dashboard','Overview')]);
 }
 /* two periods */
 let sepRe=/\b(?:than|versus|vs|compared (?:to|with)|compare(?:d)? to|against)\b/,parts=s.split(sepRe);
 if(parts.length<2&&/^ compare\b/.test(s)){parts=s.split(/\b(?:to|with|and|against)\b/)}
 let A=rngScope(parts[0]||''),B=parts.length>1?rngScope(parts.slice(1).join(' ')):null;
 if(!A&&!B){A=lmvScope('today',ctx,true);B=lmvScope('yesterday',ctx,true)}
 else if(!A){A=B.kind==='day'?lmvScope('today',ctx,true):lmvScope('this week',ctx,true)}
 else if(!B){B=A.kind==='day'?(A.date===today?lmvScope('yesterday',ctx,true):lmvDayScope(lmvAddDays(A.date,-1),today)):lmvScope('last week',ctx,true)}
 if(A.kind==='range'&&A.label==='this week'&&B.label==='last week'){/* a fair week-on-week: the same number of days of each */
  const k=lmvDays(A).length,ws=lmvDays(B);const B2={kind:'range',start:B.start,end:lmvAddDays(B.start,k-1),label:'last week (same '+k+' day'+(k===1?'':'s')+')',date:lmvAddDays(B.start,k-1)};B=B2}
 let a=sum(A),b=sum(B),perDay=A.kind!==B.kind;
 if(perDay){a=a/Math.max(1,lmvDays(A).length);b=b/Math.max(1,lmvDays(B).length)}
 const d=a-b,la=g4Cap(A.label),lb=g4Cap(B.label),pc=b>0?Math.round(Math.abs(d)/b*100):null;
 const per=perDay?' a day':'';
 const verdict=d===0?'The same '+unit+' '+A.label+' as '+B.label+'.':(d>0?'+'+g4Num(d)+' '+unit+' '+A.label:g4Num(-d)+' fewer '+unit+' '+A.label)+(pc!=null&&d!==0?' ('+(d>0?'+':'−')+pc+'%)':'');
 return lmvLines(f,'Compare',la+': '+g4Num(a)+' '+unit+per+' vs. '+lb+': '+g4Num(b)+' '+unit+per,[verdict,d>0?'You are ahead.':d<0?'You are behind '+B.label+'.':'Dead even.'],[lmvOpenChip('dashboard','Overview')]);
}

/* ----- workout plan and library ----- */
function g4Plan(f,cls){
 if(!state.plan)return lmvLines(f,'Workout plan','You have no active workout plan',['Open Workout plan to pick one.'],[lmvOpenChip('plan','Workout plan')]);
 const t=getToday(),d=planDay(t);
 if(cls.act==='start'){lmvGo({id:'plan'});try{selectDay(t)}catch(e){}}
 if(!d)return lmvLines(f,'Workout plan','Nothing planned for today',['Your plan has no day for today.'],[lmvOpenChip('plan','Workout plan')]);
 if(d.kind==='rest')return lmvLines(f,'Workout plan','Rest day',['Prescribed recovery. Nothing to log today.'],[lmvOpenChip('plan','Workout plan')]);
 const done=(function(){try{return dayDone(t)}catch(e){return false}})();
 const lines=(d.items||[]).slice(0,8).map(function(it){return it.l+(it.n?' · '+it.n+' sets':'')+(it.opt?' (optional)':'')});
 if(done)lines.push('Today is done.');
 return lmvLines(f,cls.act==='start'?'Let’s go':'Workout plan',(cls.act==='start'?'Starting: ':'Today: ')+d.label,lines.length?lines:['Open the plan to see the details.'],[lmvOpenChip('plan','Workout plan')]);
}
function g4Library(f,cls){
 const q=cls.q.replace(/[.?!]+$/,'');
 const items=Array.isArray(state.mediaItems)?state.mediaItems:[],ql=q.toLowerCase();
 const hits=items.filter(function(x){return [x.title,x.creator,x.notes,x.type].some(function(v){return (v||'').toLowerCase().indexOf(ql)>=0})});
 lmvGo({id:'media'});
 setTimeout(function(){try{const el=document.getElementById('mediaSearch');if(el){el.value=q;el.dispatchEvent(new Event('input',{bubbles:true}))}}catch(e){}},60);
 return lmvLines(f,'Library',hits.length?hits.length+' match'+(hits.length===1?'':'es')+' for “'+q+'”':'Nothing in your library for “'+q+'”',hits.slice(0,5).map(function(x){return x.title+(x.creator?' · '+x.creator:'')}),[lmvOpenChip('media','Library')]);
}

/* ----- routines and shortcuts you teach it ----- */
function g4Routine(f,cls,ctx){
 const b=g4Brief(f,ctx);
 if(cls.which==='morning'){
  const lines=b.lines.slice(),q=b.g.q.left;
  if(q.length)lines.push('Quests to start with: '+q.slice(0,3).map(function(x){return x.name}).join(', '));
  try{const p=petData();if(p&&p.alive&&petCanFeed(p,Date.now()))lines.push(p.name+' is ready for breakfast.')}catch(e){}
  return lmvLines(f,'Morning','Good morning. Here is your day',lines,[lmvOpenChip('quests','Quests'),lmvOpenChip('life-director','Life Director',true)]);
 }
 const tm=g4NextItems(ctx,lmvAddDays(b.g.today,1)),lines=['XP today: '+g4Num(b.g.xp)+' of '+g4Num(b.g.pace),b.g.stepsOn?'Steps: '+g4Num(b.g.steps)+' of '+g4Num(b.g.goal):'Steps are not connected',b.g.q.total?'Quests: '+b.g.q.done+' of '+b.g.q.total+' done':'No quests today'];
 if(tm&&tm.length)lines.push('Tomorrow starts with '+tm[0].title+' at '+lmvClock(tm[0].startEpoch*1000,ctx.tz));
 lines.push('Streak: '+b.st+' day'+(b.st===1?'':'s'));
 return lmvLines(f,'Evening','Wrapping up the day',lines,[lmvOpenChip('history','Journal')]);
}
function g4AliasAll(){try{return JSON.parse(localStorage.getItem(G4_ALIAS)||'{}')||{}}catch(e){return {}}}
function g4AliasKey(p){return String(p||'').toLowerCase().replace(/[^a-z0-9 ]/g,' ').replace(/\s+/g,' ').trim()}
function g4AliasGet(p){const a=g4AliasAll(),k=g4AliasKey(p);return Object.prototype.hasOwnProperty.call(a,k)?a[k]:null}
function g4AliasPut(p,cmd){
 const k=g4AliasKey(p),a=g4AliasAll();
 if(k.length<2)return {ok:false,msg:'That phrase is too short.'};
 if(Object.keys(a).length>=20&&!a[k])return {ok:false,msg:'You have 20 shortcuts already. Forget one first.'};
 let c=null;try{c=g4Classify(cmd)||lmvClassify(lmvPhon(cmd))}catch(e){}
 if(g4AliasKey(cmd)===k)return {ok:false,msg:'A shortcut cannot do itself.'};
 a[k]=String(cmd).slice(0,120);try{localStorage.setItem(G4_ALIAS,JSON.stringify(a))}catch(e){return {ok:false,msg:'Could not save it on this device.'}}
 return {ok:true};
}
function g4AliasDel(p){const a=g4AliasAll();delete a[g4AliasKey(p)];try{localStorage.setItem(G4_ALIAS,JSON.stringify(a))}catch(e){}}

/* ----- help: what can I say ----- */
const G4_HELP=[
 ['Schedule',['Schedule a workout tomorrow at 9','Put a 15 minute break right after that','Move my workout to 6 PM','Cancel my workout today']],
 ['Now and next',['What’s next?','How am I doing today?','How close am I to my goals?','I have 30 minutes free, what should I do?']],
 ['Quests and XP',['What are my quests?','How much XP did I earn this week?','How many XP until level 5?','Mark my workout done']],
 ['Steps and workouts',['How many steps do I have today?','Did I walk more today than yesterday?','What was my best day for steps this week?','What’s on my workout plan?']],
 ['Log what you did',['Log a 30 minute run','I walked for 20 minutes yesterday','Log a 45 minute workout and check my XP']],
 ['Cat',['How is my cat?','Feed my cat','Play with my cat']],
 ['Timer and apps',['Start a 25 minute focus timer','How much time is left?','Open Firefox','Search my library for dune']],
 ['Go to a screen',['Open my quests','Open the library','Take me to the cat room','Open Life Director']],
 ['Shortcuts',['Start my morning routine','When I say gym time, do log a 45 minute workout']]
];
function g4Help(f){
 lmvLines(f,'Help','What can I say?',['Tap any example to run it. Speak naturally; you can correct yourself (“at 8, wait no, make it 9”) or do two things at once.'],[]);
 f.help=G4_HELP;return lmvSnapshot();
}

/* ----- timers ----- */
function g4TimerGet(){try{const t=JSON.parse(localStorage.getItem(G4_TIMER)||'null');return t&&t.end>Date.now()?t:null}catch(e){return null}}
function g4TimerSet(t){try{if(t)localStorage.setItem(G4_TIMER,JSON.stringify(t));else localStorage.removeItem(G4_TIMER)}catch(e){}}
function g4TimerArm(t){
 clearTimeout(g4TimerH);g4TimerH=null;if(!t)return;
 g4TimerH=setTimeout(function(){g4TimerDone(t)},Math.min(2147000000,Math.max(0,t.end-Date.now())));
 try{const LN=typeof lmaPlugin==='function'?lmaPlugin('LocalNotifications'):null;
  if(LN&&typeof lmaPermission==='function')lmaPermission(false).then(function(p){if(p!=='granted')return;try{LN.createChannel({id:LMA_CHANNEL,name:'Reminders',description:'Workouts, planned sessions and check-ins',importance:4,visibility:1})}catch(e){}
   LN.schedule({notifications:[{id:G4_NOTIF,title:'Timer done',body:t.label?t.label+' · '+t.total+' min':t.total+' minutes are up',channelId:LMA_CHANNEL,schedule:{at:new Date(t.end),allowWhileIdle:true}}]})}).catch(function(){})}catch(e){}
}
function g4TimerCancelNative(){try{const LN=typeof lmaPlugin==='function'?lmaPlugin('LocalNotifications'):null;if(LN)LN.cancel({notifications:[{id:G4_NOTIF}]}).catch(function(){})}catch(e){}}
function g4TimerDone(t){
 const cur=g4TimerGet();if(cur&&cur.end>t.end)return;
 g4TimerSet(null);g4TimerH=null;
 try{toast('Timer done'+(t.label?' · '+t.label:''))}catch(e){}
 try{if(navigator.vibrate)navigator.vibrate([200,100,200])}catch(e){}
 try{const f=lmvFrame=lmvNewFrame();f.transcript='';lmvLines(f,'Timer','Time is up',[(t.label?t.label+' · ':'')+t.total+' minutes finished.'],[{label:'5 more minutes',run:function(){g4TimerStart(5,t.label)}}]);lmvHudOpen(true);lmvRender()}catch(e){}
}
function g4TimerStart(min,label){
 const prev=g4TimerGet(),t={end:Date.now()+min*60000,total:min,label:label||'',made:Date.now()};
 g4TimerSet(t);g4TimerArm(t);return prev;
}
function g4TimerDo(f,cls,text){
 const n=lmvNormalize(text),cur=g4TimerGet();
 if(cls.act==='cancel'){if(!cur)return lmvLines(f,'Timer','No timer is running',[],[]);clearTimeout(g4TimerH);g4TimerSet(null);g4TimerCancelNative();return lmvLines(f,'Timer','Timer cancelled',[(cur.label?cur.label+' · ':'')+cur.total+' minute timer stopped.'],[])}
 if(cls.act==='query'){
  if(!cur)return lmvLines(f,'Timer','No timer is running',['Say “start a 25 minute focus timer”.'],[]);
  const left=Math.max(0,Math.round((cur.end-Date.now())/1000)),mm=Math.floor(left/60),ss=left%60;
  return lmvLines(f,'Timer',mm+' min '+(ss<10?'0':'')+ss+' s left',[(cur.label?cur.label+' · ':'')+cur.total+' minute timer'],[{label:'Cancel timer',run:function(){clearTimeout(g4TimerH);g4TimerSet(null);g4TimerCancelNative();lmvLines(lmvFrame,'Timer','Timer cancelled',[],[])}}]);
 }
 const dm=/(\d+(?:\.\d+)?) ?(hours?|hrs?|hr|h|minutes?|mins?|min|m)\b/.exec(n);
 let min=dm?Math.round(+dm[1]*(/^h/.test(dm[2])?60:1)):25;
 if(min<1||min>12*60)return lmvLines(f,'Timer','That length does not work',['Pick between 1 minute and 12 hours.'],[]);
 let label='';const lm=/\bfor ([a-z][a-z ]{1,40})$/.exec(n.replace(/\b(?:a|an|the|my) /g,''));if(lm&&!/\d/.test(lm[1])&&!/^(?:minutes?|hours?|mins?|a while)/.test(lm[1]))label=g4Cap(lm[1].trim());
 const prev=g4TimerStart(min,label);
 return lmvLines(f,'Timer',min+' minute '+(/pomodoro/.test(n)?'pomodoro':/focus/.test(n)?'focus session':'timer')+' started',[(label?label+' · ':'')+'I will tell you when it ends.'].concat(prev?['Replaced the earlier timer.']:[]),[{label:'Cancel timer',run:function(){clearTimeout(g4TimerH);g4TimerSet(null);g4TimerCancelNative();lmvLines(lmvFrame,'Timer','Timer cancelled',[],[])}}]);
}

/* ----- opening other apps ----- */
const G4_ALIASES={spotify:['myspoti','spotify'],myspoti:['myspoti','spotify'],music:['myspoti','spotify','music'],browser:['firefox','chrome','brave','browser','internet'],internet:['firefox','chrome','internet','browser'],firefox:['firefox'],chrome:['chrome'],claude:['claude'],gemini:['gemini'],youtube:['youtube'],maps:['maps'],camera:['camera'],gallery:['gallery','photos'],photos:['photos','gallery'],phone:['phone','dialer'],messages:['messages'],gmail:['gmail'],calculator:['calculator'],clock:['clock'],files:['files','my files'],calendar:['calendar'],store:['play store'],settings:['settings']};
function g4Alnum(s){return String(s||'').toLowerCase().replace(/[^a-z0-9]/g,'')}
async function g4LoadApps(){
 if(g4Apps||g4AppsBusy)return g4Apps;
 try{if(!(typeof lmaNative==='function'&&lmaNative()))return null}catch(e){return null}
 g4AppsBusy=true;
 try{const P=lmaPlugin('AppLauncher');if(P){const r=await P.getApps();g4Apps=(r&&r.apps||[]).map(function(a){return {pkg:a.pkg,label:a.label}})}}catch(e){}
 g4AppsBusy=false;return g4Apps;
}
function g4AppPool(){
 const out=[],seen={};
 try{(lmaLoad().apps||[]).forEach(function(a){if(a&&a.pkg&&!seen[a.pkg]){seen[a.pkg]=1;out.push({pkg:a.pkg,label:a.label||a.pkg})}})}catch(e){}
 (g4Apps||[]).forEach(function(a){if(!seen[a.pkg]){seen[a.pkg]=1;out.push(a)}});
 return out;
}
function g4KnownApp(name){
 const k=g4Alnum(name);if(!k)return false;
 if(G4_ALIASES[k]||G4_ALIASES[name.trim()])return true;
 return g4AppPool().some(function(a){const l=g4Alnum(a.label);return l===k||(k.length>=4&&l.indexOf(k)===0)});
}
function g4AppMatches(name,sys){
 const k=g4Alnum(name),pool=g4AppPool(),terms=(G4_ALIASES[k]||[]).map(g4Alnum).concat([k]);
 let hits=pool.filter(function(a){return terms.some(function(t){return g4Alnum(a.label)===t})});
 if(!hits.length)hits=pool.filter(function(a){const l=g4Alnum(a.label);return terms.some(function(t){return t.length>=3&&(l.indexOf(t)===0||t.indexOf(l)===0&&l.length>=4)})});
 if(!hits.length)hits=pool.filter(function(a){const l=g4Alnum(a.label);return terms.some(function(t){return t.length>=4&&l.indexOf(t)>=0})});
 if(sys)hits=hits.filter(function(a){return /settings/.test(a.pkg)||/^settings$/i.test(a.label)}).sort(function(a,b){return (/^com\.(?:android|samsung\.android)\.settings$/.test(b.pkg)?1:0)-(/^com\.(?:android|samsung\.android)\.settings$/.test(a.pkg)?1:0)});
 return hits;
}
function g4Launch(pkg){
 if(g4Launcher)return Promise.resolve(g4Launcher(pkg));
 const L=window.AppsShortcuts;if(L&&L.launch)return L.launch(pkg);
 const P=lmaPlugin('AppLauncher');return P?P.launch({pkg:pkg}):Promise.reject(new Error('no launcher'));
}
function g4AppDo(f,cls){
 const native=(function(){try{return !!lmaNative()}catch(e){return false}})(),label=g4Cap(cls.app);
 if(!native&&!g4Launcher)return lmvLines(f,'Apps','Opening other apps works in the Android app',['Say “open '+cls.app+'” there and I will launch it.'],[]);
 const hits=g4AppMatches(cls.app,cls.sys);
 if(!hits.length){
  if(!g4Apps&&!g4AppsBusy){g4LoadApps();return lmvLines(f,'Apps','Looking for “'+cls.app+'”…',['Say it again in a moment once the app list has loaded.'],[])}
  return lmvLines(f,'Apps','I could not find “'+cls.app+'”',['It is not on your phone’s app list. Add it to the Overview shelf, or check the name.'],[]);
 }
 if(hits.length===1||g4Alnum(hits[0].label)===g4Alnum(cls.app)){
  const a=hits[0];g4Launch(a.pkg);return lmvLines(f,'Opening',a.label,['Opening '+a.label+'.'],[]);
 }
 return lmvLines(f,'Apps','Which one?',hits.slice(0,4).map(function(a){return a.label}),hits.slice(0,4).map(function(a){return {label:'Open '+a.label,run:function(){g4Launch(a.pkg);lmvFrame=null;lmvHudOpen(false)}}}));
}

/* ===== the wrapper that ties it together ===== */
const g4Orig=lmvHandle;
lmvHandle=function(text,meta){
 meta=meta||{};
 if(!lmvEnabled()||(meta.utteranceId&&lmvDelivered.has(meta.utteranceId)))return g4Orig(text,meta);
 if(g4On&&!(lmvFrame&&(lmvFrame.state==='PREVIEWED'||lmvFrame.state==='AWAITING_CLARIFICATION')))g4Reset();
 let clean=String(text==null?'':text).slice(0,500),ctx=null;
 try{
  ctx=lmvCtx();
  /* a shortcut you taught it */
  const al=g4AliasGet(clean);if(al!=null&&!/^ ?(?:forget|delete|remove)\b/i.test(clean))clean=al;
  const f=lmvFrame,active=f&&(f.state==='AWAITING_CLARIFICATION'||f.state==='PREVIEWED');
  /* the card on screen: "make it 45 minutes" */
  if(f&&f.state==='PREVIEWED'){const r=g4Edit(clean,ctx);if(r)return r}
  /* "wait no, make it 9" */
  const rep=g4Repair(clean,ctx);if(rep)clean=rep;
  /* "right after that" */
  if(!active||(f&&f.state==='PREVIEWED')){const r=g4Chain(clean,ctx,meta);if(r)return r}
  /* two things at once */
  if(!active&&!/^\s*(?:hey |ok |okay )?(?:when|if|whenever) i say\b/i.test(clean)){
   const sp=g4Split(clean);
   if(sp){g4Reset();g4On=true;g4Queue=[sp.b];g4More=!!sp.more;g4Orig(sp.a,meta);g4Frame=lmvFrame;g4Pump();lmvRender();return lmvSnapshot()}
  }
 }catch(e){console.warn('[voice g4]',e)}
 g4Orig(clean,meta);
 try{g4Pump()}catch(e){console.warn('[voice g4]',e)}
 lmvRender();return lmvSnapshot();
};
/* remember where the last activity went, so "right after that" has something to point at */
(function(){const ex=lmvExecute;lmvExecute=function(f){const r=ex.apply(this,arguments);try{if(f&&f.state==='CONFIRMED'&&f.slot&&f.title)g4Last={id:f.entityId,title:f.title,date:f.slot.localDate,start:f.slot.localStartTime,end:f.slot.localEndTime,ts:Date.now()}}catch(e){}return r}})();
/* a card can carry lines from the first half of a two-part request, and the help list */
(function(){const rr=lmvRender;lmvRender=function(){
 rr.apply(this,arguments);
 try{
  const f=lmvFrame,hud=lmvUI&&lmvUI.hud;if(!f||!hud||!hud.firstChild)return;
  if(f.pre&&f.pre.length){const ref=hud.querySelector('.lmv-q')||hud.querySelector('.lmv-card');f.pre.forEach(function(t){const p=lmvEl('p','lmv-note lmv-info',t);if(ref)hud.insertBefore(p,ref);else hud.appendChild(p)})}
  if(f.help&&f.state==='CONFIRMED'){
   const box=lmvEl('div','lmv-help');
   f.help.forEach(function(sec){box.appendChild(lmvEl('p','lmv-hcat',sec[0]));const row=lmvEl('div','lmv-chips');sec[1].forEach(function(ex){const b=lmvEl('button','lmv-chip',ex);b.type='button';b.addEventListener('click',function(){lmvHandle(ex,{});lmvRender()});row.appendChild(b)});box.appendChild(row)});
   const rows=hud.querySelectorAll('.lmv-chips'),chips=rows.length?rows[rows.length-1]:null;hud.insertBefore(box,chips);
  }
 }catch(e){console.warn('[voice g4 render]',e)}
}})();
/* the capability list the tests and diagnostics read */
[['assistant.daily_briefing','assistant','Daily briefing','How am I doing today'],['life_director.whats_next','life_director','What is next','What is next'],['assistant.check_goal_progress','assistant','Goal progress','How close am I to my goals'],['assistant.recommend_action','assistant','Suggest what to do','I have 30 minutes free, what should I do'],['analytics.compare_periods','analytics','Compare days and weeks','Did I walk more today than yesterday'],['fitness.workout_plan','fitness','Workout plan','What is on my workout plan'],['library.search','library','Search the library','Search my library for dune'],['navigation_system.start_focus_session','navigation_system','Focus timer','Start a 25 minute focus timer'],['navigation_system.launch_external_app','navigation_system','Open another app','Open Firefox'],['assistant.voice_help','assistant','What can I say','What can I say'],['assistant.routine','assistant','Routines','Start my morning routine'],['assistant.teach_alias','assistant','Your own shortcuts','When I say gym time, do log a 45 minute workout']].forEach(function(c){LMV_CAPS.push({intentId:c[0],domain:c[1],title:c[2],exampleUtterance:c[3],evaluateStatus:function(){return c[0]==='life_director.whats_next'?lmvCapStatus(true):'EXECUTABLE'}})});
window.LifeMaxVoiceG4=Object.freeze({
 repair:function(t){return g4Repair(t,lmvCtx())},split:g4Split,last:function(){return g4Last},timer:g4TimerGet,
 setApps:function(a){g4Apps=a},setLauncher:function(fn){g4Launcher=fn},aliases:g4AliasAll,clearAliases:function(){try{localStorage.removeItem(G4_ALIAS)}catch(e){}},cancelTimer:function(){clearTimeout(g4TimerH);g4TimerSet(null)},fireTimer:function(){const t=g4TimerGet();if(t)g4TimerDone(t)}
});
(function g4Boot(){
 setTimeout(function(){try{const t=g4TimerGet();if(t)g4TimerArm(t);g4LoadApps()}catch(e){}},3000);
})();
