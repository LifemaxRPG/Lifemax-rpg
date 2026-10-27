/* ---------- LifeMax Voice, phase 1: say or type a scheduling request, answer what is unclear, preview it, save it into
   Life Director and refresh the Corona dial. Offline only (the native side uses the on-device recognizer or nothing).
   Pure logic first (normalizer, temporal resolver, parser), then the three-tier idempotency journal, the transaction
   state machine, the Life Director adapter, and the HUD / diagnostics UI. Everything here is plain web code. ---------- */
const LMV={flag:'lifemax_voice_enabled',prefs:'lifemax_voice_prefs_v1',journal:'lifemax_voice_op_journal_v1',maxOps:50,dupWindowMs:60000,lowConf:.8};
let lmvOverride=null,lmvFrame=null,lmvExec={},lmvDelivered=new Set(),lmvSeq=0,lmvLast={},lmvListening=false,lmvBridge={status:'unknown',detail:''},lmvLog=[];
const lmvBoot=Date.now().toString(36);
function lmvUuid(){try{return crypto.randomUUID()}catch(e){return 'x'+Math.random().toString(36).slice(2)+Date.now().toString(36)}}
function lmvEnabled(){try{return localStorage.getItem(LMV.flag)!=='false'}catch(e){return true}}
function lmvPrefs(){try{return Object.assign({alwaysPreview:true,debug:false},JSON.parse(localStorage.getItem(LMV.prefs)||'{}'))}catch(e){return {alwaysPreview:true,debug:false}}}
function lmvSetPrefs(p){try{localStorage.setItem(LMV.prefs,JSON.stringify(Object.assign(lmvPrefs(),p)))}catch(e){}}
function lmvCtx(){
 const o=lmvOverride||{};
 let tz=o.tz;if(!tz){try{tz=(window.LifeDirector&&LifeDirector.voice&&LifeDirector.voice.timezone())||Intl.DateTimeFormat().resolvedOptions().timeZone||'UTC'}catch(e){tz='UTC'}}
 return {now:o.now||Date.now(),tz:tz,workOn:o.workOn||function(d){try{return window.LifeDirector&&LifeDirector.voice?LifeDirector.voice.workOn(d):null}catch(e){return null}}};
}
/* ===== time helpers: wall clock in an IANA zone <-> epoch, with daylight-saving gap and fold detection ===== */
const lmvFmts={};
function lmvParts(ms,tz){
 const f=lmvFmts[tz]||(lmvFmts[tz]=new Intl.DateTimeFormat('en-US',{timeZone:tz,hourCycle:'h23',year:'numeric',month:'numeric',day:'numeric',hour:'numeric',minute:'numeric',weekday:'short'}));
 const o={};f.formatToParts(new Date(ms)).forEach(function(p){o[p.type]=p.value});
 return {y:+o.year,mo:+o.month,d:+o.day,h:(+o.hour)%24,mi:+o.minute,wd:['Sun','Mon','Tue','Wed','Thu','Fri','Sat'].indexOf(o.weekday)};
}
function lmvOffset(ms,tz){const m=ms-ms%60000,p=lmvParts(m,tz);return Date.UTC(p.y,p.mo-1,p.d,p.h,p.mi)-m}
function lmvSame(p,y,mo,d,h,mi){return p.y===y&&p.mo===mo&&p.d===d&&p.h===h&&p.mi===mi}
/* status: OK | GAP (the wall time does not exist: clocks jumped over it) | FOLD (it happens twice) */
function lmvLocalToEpoch(y,mo,d,h,mi,tz){
 const guess=Date.UTC(y,mo-1,d,h,mi),seen={},good=[];
 [-864e5,0,864e5].forEach(function(k){const e=guess-lmvOffset(guess+k,tz);if(!seen[e]){seen[e]=1;if(lmvSame(lmvParts(e,tz),y,mo,d,h,mi))good.push(e)}});
 if(good.length===1)return {epochMs:good[0],status:'OK'};
 if(good.length>1){good.sort(function(a,b){return a-b});return {epochMs:good[0],second:good[good.length-1],status:'FOLD'}}
 const e=guess-lmvOffset(guess-864e5,tz),sh=lmvParts(e,tz);
 return {epochMs:e,status:'GAP',shifted:{h:sh.h,mi:sh.mi}};
}
const lmvPad=function(n){return (n<10?'0':'')+n};
function lmvYmd(p){return p.y+'-'+lmvPad(p.mo)+'-'+lmvPad(p.d)}
function lmvAddDays(ymd,n){const a=ymd.split('-').map(Number),t=new Date(Date.UTC(a[0],a[1]-1,a[2]+n));return t.getUTCFullYear()+'-'+lmvPad(t.getUTCMonth()+1)+'-'+lmvPad(t.getUTCDate())}
function lmvWeekday(ymd){const a=ymd.split('-').map(Number);return new Date(Date.UTC(a[0],a[1]-1,a[2])).getUTCDay()}
function lmvNiceDate(ymd){const a=ymd.split('-').map(Number);return new Date(Date.UTC(a[0],a[1]-1,a[2])).toLocaleDateString('en-US',{timeZone:'UTC',weekday:'short',month:'short',day:'numeric'})}
function lmvNiceTime(hhmm){const a=hhmm.split(':').map(Number),h=a[0],m=a[1];return ((h%12)||12)+':'+lmvPad(m)+' '+(h<12?'AM':'PM')}
function lmvDurText(m){const h=Math.floor(m/60),r=m%60;return (h?h+' h':'')+(h&&r?' ':'')+(r?r+' min':'')}
const LMV_WD=['sunday','monday','tuesday','wednesday','thursday','friday','saturday'],LMV_MON=['jan','feb','mar','apr','may','jun','jul','aug','sep','oct','nov','dec'];
const LMV_WORDS=['twelve','one','two','three','four','five','six','seven','eight','nine','ten','eleven'];
/* ===== text normalizer: courtesies, spoken numbers, spoken durations and clock phrases ===== */
const LMV_NUM={zero:0,one:1,two:2,three:3,four:4,five:5,six:6,seven:7,eight:8,nine:9,ten:10,eleven:11,twelve:12,thirteen:13,fourteen:14,fifteen:15,sixteen:16,seventeen:17,eighteen:18,nineteen:19,twenty:20,thirty:30,forty:40,fifty:50};
function lmvNormalize(raw){
 let t=' '+String(raw==null?'':raw).toLowerCase().replace(/[‘’]/g,"'").replace(/a\.m\./g,' am ').replace(/p\.m\./g,' pm ')+' ';
 t=t.replace(/(\d)\.(\d)/g,'$1_$2').replace(/[^a-z0-9:'_\s\-]/g,' ').replace(/_/g,'.');
 t=t.replace(/\b(hey|hi|hello|ok|okay|please|kindly|can you|could you|would you|will you|i want to|i need to|i'd like to|i would like to|go ahead and|just|thanks|thank you)\b/g,' ');
 t=t.replace(/\b(twenty|thirty|forty|fifty)[\s-]+(one|two|three|four|five|six|seven|eight|nine)\b/g,function(m,a,b){return String(LMV_NUM[a]+LMV_NUM[b])});
 t=t.replace(/\b(zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty)\b/g,function(w){return String(LMV_NUM[w])});
 t=t.replace(/\bhalf an hour\b/g,' 30 minutes ').replace(/\ban hour and a half\b|\b1 hour and a half\b|\bhour and a half\b/g,' 90 minutes ')
  .replace(/\b(\d+) and a half hours?\b/g,function(m,n){return ' '+(+n+.5)+' hours '})
  .replace(/\b(an|a) hour\b/g,' 1 hour ').replace(/\bhour[\s-]*long\b/g,' hour ').replace(/\b(\d+)[\s-]*(hour|minute|min|hr)[\s-]*long\b/g,' $1 $2 ')
  .replace(/\bhalf past (\d{1,2})\b/g,' $1:30 ').replace(/\bquarter past (\d{1,2})\b/g,' $1:15 ')
  .replace(/\bquarter (?:to|of) (\d{1,2})\b/g,function(m,h){const n=+h===1?12:+h-1;return ' '+n+':45 '})
  .replace(/\b(\d{1,2}) o'?clock\b/g,' $1:00 ');
 return t.replace(/\s+/g,' ').trim();
}
/* ===== extraction: turn one utterance into raw fields (no guessing; unknown stays null) ===== */
function lmvExtract(text,ctx,expect){
 const f={title:null,date:null,dateAmbig:null,hour:null,minute:0,mer:null,daypart:null,dur:null,endHour:null,endMinute:0,endMer:null,anchor:null,intent:null};
 let w=' '+lmvNormalize(text)+' ';
 const take=function(re,fn){const m=re.exec(w);if(!m)return false;w=w.slice(0,m.index)+' '+w.slice(m.index+m[0].length);fn(m);return true};
 /* durations */
 take(/\b(?:for\s+)?(\d+(?:\.\d+)?)\s*(?:hours?|hrs?|h)\b(?:\s*(?:and\s*)?(\d+)\s*(?:minutes?|mins?|m)\b)?/,function(m){f.dur=Math.round(+m[1]*60+(m[2]?+m[2]:0))});
 if(f.dur==null)take(/\b(?:for\s+)?(\d+)\s*(?:minutes?|mins?|min)\b/,function(m){f.dur=+m[1]});
 /* "from 2 pm to 3:30 pm", "2 to 3" */
 take(/\b(?:from\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s*(?:to|until|till|-)\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b/,function(m){
  f.hour=+m[1];f.minute=m[2]?+m[2]:0;f.mer=m[3]||null;f.endHour=+m[4];f.endMinute=m[5]?+m[5]:0;f.endMer=m[6]||null});
 /* anchors */
 take(/\bafter work\b/,function(){f.anchor='after_work'});
 take(/\bbefore work\b/,function(){f.anchor='before_work'});
 /* dates */
 const nowP=lmvParts(ctx.now,ctx.tz),today=lmvYmd(nowP);
 take(/\bday after tomorrow\b/,function(){f.date=lmvAddDays(today,2)});
 take(/\btomorrow\b/,function(){f.date=lmvAddDays(today,1)});
 take(/\btonight\b/,function(){f.date=today;f.daypart='evening'});
 take(/\btoday\b/,function(){f.date=today});
 take(/\b(?:(next|this|on)\s+)?(sunday|monday|tuesday|wednesday|thursday|friday|saturday)\b/,function(m){
  const want=LMV_WD.indexOf(m[2]),cur=lmvWeekday(today);let up=(want-cur+7)%7;
  if(m[1]==='next'){if(up===0)up=7;const a=lmvAddDays(today,up),b=lmvAddDays(a,7);f.dateAmbig={weekday:m[2],options:[a,b]}}
  else f.date=lmvAddDays(today,up);
 });
 take(/\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b/,function(m){
  const mo=LMV_MON.indexOf(m[1])+1;let y=nowP.y;const cand=y+'-'+lmvPad(mo)+'-'+lmvPad(+m[2]);if(cand<today)y++;f.date=y+'-'+lmvPad(mo)+'-'+lmvPad(+m[2])});
 /* dayparts */
 take(/\b(morning|afternoon|evening|night)\b/,function(m){f.daypart=m[1]});
 take(/\bnoon\b/,function(){f.hour=12;f.minute=0;f.mer='pm'});
 take(/\bmidnight\b/,function(){f.hour=12;f.minute=0;f.mer='am'});
 /* a clock time */
 if(f.hour==null){
  const withPrep=/\b(?:at|around|about|by|@)\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b/,bare=/\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b|\b(\d{1,2}):(\d{2})\b/;
  if(!take(withPrep,function(m){f.hour=+m[1];f.minute=m[2]?+m[2]:0;f.mer=m[3]||null}))
   if(!take(bare,function(m){f.hour=+(m[1]||m[4]);f.minute=+(m[2]||m[5]||0);f.mer=m[3]||null}))
    if(expect==='time'||expect==='meridiem'){const m=/^\s*(\d{1,2})\s*$/.exec(w);if(m){f.hour=+m[1];w=' '}}
 }
 if(f.hour!=null&&(f.hour>24||f.minute>59))f.hour=null;
 if(f.endHour!=null&&(f.endHour>24||f.endMinute>59))f.endHour=null;
 /* intent */
 if(/\b(what'?s|what is|what do i have|what have i got|show me|read me)\b.*\b(schedule|plan|on|today|tomorrow|calendar|agenda|day)\b/.test(' '+lmvNormalize(text)+' ')&&!/\b(put|add|schedule me|book|create)\b/.test(' '+lmvNormalize(text)+' '))f.intent='query';
 /* the rest is the title */
 const t=w.replace(/\b(schedule|schedul|put|add|create|book|set up|setup|set|make|plan|log|me|my|on|onto|in|into|to|at|for|from|the|a|an|some|that|it|this|next|and|then|life director|lifedirector|corona dial|corona|dial|calendar|planner|up|around|about|by|until|till|of|i|have|want|need|like|please|yes|no|um|uh|so|also)\b/g,' ')
  .replace(/[^a-z0-9'\- ]/g,' ').replace(/\s+/g,' ').trim();
 f.title=t&&!/^\d+$/.test(t)&&t.length<=60?t.charAt(0).toUpperCase()+t.slice(1):null;
 return f;
}
/* ===== finalize: raw fields -> a CanonicalScheduleSlot, or exactly one question. Nothing is ever guessed. ===== */
function lmvAsk(kind,text,chips,flag){return {status:'NEED',kind:kind,question:text,chips:chips||[],flag:flag||null}}
function lmvFinalize(f,ctx){
 const ambig=[],nowP=lmvParts(ctx.now,ctx.tz),today=lmvYmd(nowP);
 if(!f.title)return lmvAsk('title','What should I call it?',[]);
 if(f.dateAmbig){
  const o=f.dateAmbig.options,cap=f.dateAmbig.weekday.charAt(0).toUpperCase()+f.dateAmbig.weekday.slice(1);
  return lmvAsk('date','Did you mean this '+cap+' ('+lmvNiceDate(o[0])+') or next '+cap+' ('+lmvNiceDate(o[1])+')?',o.map(function(d){return {label:lmvNiceDate(d),patch:{date:d,dateAmbig:null}}}),'NEXT_WEEKDAY_AMBIGUOUS');
 }
 if(!f.date)return lmvAsk('date','Which day?',[{label:'Today',patch:{date:today}},{label:'Tomorrow',patch:{date:lmvAddDays(today,1)}}]);
 let dur=f.dur;
 /* "after work" / "before work": only from hours Life Director really has */
 if(f.anchor&&f.hour==null){
  const w=ctx.workOn(f.date);
  if(!w)return lmvAsk('time','I don’t know your work hours for '+lmvNiceDate(f.date)+'. What time should I schedule it?',[],'ANCHOR_TIME_MISSING');
  if(f.anchor==='after_work'){const m=w.endMin%1440;f=Object.assign({},f,{hour:Math.floor(m/60),minute:m%60,mer:'24'})}
  else{if(dur==null)return lmvAsk('duration','How long should it be?',lmvDurChips());const m=((w.startMin-dur)+1440)%1440;f=Object.assign({},f,{hour:Math.floor(m/60),minute:m%60,mer:'24'})}
 }
 if(f.hour==null)return lmvAsk('time','What time?',[]);
 /* meridiem: only when it was said or clearly implied */
 let h=f.hour,mi=f.minute||0,mer=null;const assume=[];
 if(f.mer==='24'){}
 else if(f.mer==='am'||f.mer==='pm'){h=lmvTo24(h,f.mer)}
 else if(h>=13||h===0){}
 else if(f.daypart){
  if(f.daypart==='morning')h=h===12?0:h;
  else h=h===12?12:h+12;
  if(f.daypart==='night'&&f.hour===12)h=0;
 }
 else if(h===12){}
 else if(lmvGuess(f.title,h)){mer=lmvGuess(f.title,h);h=lmvTo24(h,mer);assume.push('MERIDIEM_INFERRED')}
 else{const w=LMV_WORDS[h%12]||String(h);return lmvAsk('meridiem',w.charAt(0).toUpperCase()+w.slice(1)+' in the morning or evening?',[{label:'Morning',patch:{mer:'am'}},{label:'Evening',patch:{mer:'pm'}}],'MERIDIEM_UNSPECIFIED')}
 if(h===24){h=0}
 /* an explicit end time gives the duration */
 if(f.endHour!=null&&dur==null){
  const em=f.endMinute||0,startMin=h*60+mi;
  if(f.endMer==='am'||f.endMer==='pm'){dur=((lmvTo24(f.endHour,f.endMer)*60+em)-startMin+1440)%1440||null}
  else{const c=[f.endHour%12,f.endHour%12+12].map(function(x){return x*60+em-startMin}).filter(function(x){return x>0}).sort(function(a,b){return a-b});dur=c.length?c[0]:null}
 }
 let durDefault=false;
 if(dur==null||dur<=0){dur=LMV_DEFAULT_MIN;durDefault=true}      /* nobody said how long: a standard half hour, shown in the preview */
 if(dur>16*60)return lmvAsk('duration','That is more than 16 hours. How long should it be?',lmvDurChips());
 /* wall clock -> epoch, checking daylight saving */
 const a=f.date.split('-').map(Number),r=lmvLocalToEpoch(a[0],a[1],a[2],h,mi,ctx.tz);
 let start=r.epochMs;
 if(r.status==='GAP'&&f.dst!=='shift'){
  ambig.push('DST_NONEXISTENT_TIME');
  const sh=lmvPad(r.shifted.h)+':'+lmvPad(r.shifted.mi);
  return lmvAsk('dst',lmvNiceTime(lmvPad(h)+':'+lmvPad(mi))+' does not exist on '+lmvNiceDate(f.date)+' (the clocks jump forward). Use '+lmvNiceTime(sh)+' instead?',[{label:'Use '+lmvNiceTime(sh),patch:{dst:'shift'}},{label:'Pick another time',patch:{hour:null,mer:null,daypart:null,dst:null}}],'DST_NONEXISTENT_TIME');
 }
 if(r.status==='FOLD'&&!f.dst){
  ambig.push('DST_REPEATED_TIME');
  return lmvAsk('dst',lmvNiceTime(lmvPad(h)+':'+lmvPad(mi))+' happens twice on '+lmvNiceDate(f.date)+' (the clocks go back). Which one?',[{label:'The first time',patch:{dst:'first'}},{label:'The second time',patch:{dst:'second'}}],'DST_REPEATED_TIME');
 }
 if(r.status==='FOLD'&&f.dst==='second')start=r.second;
 if(r.status==='GAP'){const sp=lmvParts(start,ctx.tz);h=sp.h;mi=sp.mi}
 const end=start+dur*60000,ep=lmvParts(end,ctx.tz);
 /* nothing in the past */
 if(f.date===today&&start<ctx.now-60000&&!f.pastOk){
  ambig.push('PAST_TIME_ON_TODAY');
  const tm=lmvAddDays(today,1);
  return lmvAsk('past',lmvNiceTime(lmvPad(h)+':'+lmvPad(mi))+' has already passed today. Schedule it for tomorrow at '+lmvNiceTime(lmvPad(h)+':'+lmvPad(mi))+' instead?',[{label:'Tomorrow',patch:{date:tm}},{label:'Pick another time',patch:{hour:null,mer:null,daypart:null,endHour:null}}],'PAST_TIME_ON_TODAY');
 }
 const odd=lmvOdd(f.title,h);
 if(odd)assume.push('UNUSUAL_TIME');
 return {status:'READY',oddText:odd,slot:{assumptions:assume,durationDefaulted:durDefault,localDate:f.date,localStartTime:lmvPad(h)+':'+lmvPad(mi),localEndTime:lmvPad(ep.h)+':'+lmvPad(ep.mi),durationMinutes:dur,ianaTimeZone:ctx.tz,startEpochMs:start,endEpochMs:end,ambiguities:ambig},title:f.title};
}
/* what a title usually means for the time of day: used to avoid asking "morning or evening?" when only one makes sense.
   win = the hours (24 h clock) such an activity normally falls in; infer(h) = which half of the clock an unmarked hour 1-11 means. */
const LMV_KINDS=[
 {re:/\b(wake|wake up|alarm|get up|sunrise)\b/,name:'waking up',win:[4,10],infer:function(h){return h>=4&&h<=10?'am':null}},
 {re:/\b(breakfast)\b/,name:'breakfast',win:[5,11],infer:function(h){return h>=5&&h<=11?'am':null}},
 {re:/\b(brunch)\b/,name:'brunch',win:[9,14],infer:function(h){return h>=9&&h<=11?'am':h<=2?'pm':null}},
 {re:/\b(lunch)\b/,name:'lunch',win:[10,15],infer:function(h){return h>=10&&h<=11?'am':h<=3?'pm':null}},
 {re:/\b(dinner|supper)\b/,name:'dinner',win:[16,22],infer:function(h){return h>=4&&h<=10?'pm':null}},
 {re:/\b(bar|pub|drinks?|happy hour|club|clubbing|party|date|date night|movies?|cinema|concert|show|karaoke|bowling|game night)\b/,name:'going out',win:[11,26],infer:function(h){return h<=11?'pm':null}},
 {re:/\b(nap)\b/,name:'a nap',win:[12,17],infer:function(h){return h<=4?'pm':null}},
 {re:/\b(bed|bedtime|go to sleep|sleep)\b/,name:'bedtime',win:[20,26],infer:function(h){return h>=8&&h<=11?'pm':null}},
 {re:/\b(meeting|meet|call|interview|appointment|dentist|doctor|consult(ation)?|conference|class|lecture|exam|presentation|standup|sync|review|office|work|shift|checkup)\b/,name:'that',win:[7,20],infer:function(h){return h>=8&&h<=11?'am':h<=6?'pm':null}},
 {re:/\b(workout|gym|run|jog|walk|yoga|lift|lifting|swim|hike|cardio|exercise|training|stretch)\b/,name:'exercise',win:[4,23],infer:function(h){return h<=3?'pm':null}}
];
function lmvKind(title){const t=String(title||'').toLowerCase();for(let i=0;i<LMV_KINDS.length;i++)if(LMV_KINDS[i].re.test(t))return LMV_KINDS[i];return null}
function lmvGuess(title,h){
 if(h<1||h>11)return null;
 const k=lmvKind(title);
 if(k)return k.infer(h);
 return h<=5?'pm':null;                    /* nothing is scheduled for 1 to 5 in the morning unless it says so */
}
function lmvOdd(title,h24){
 const k=lmvKind(title);if(!k)return null;
 const h=h24<k.win[0]?h24+24:h24;
 return h<k.win[0]||h>=k.win[1]?String(title)+' at '+lmvNiceTime(lmvPad(h24%24)+':00').replace(':00','')+' is an unusual time.':null;
}
const LMV_DEFAULT_MIN=30;
function lmvTo24(h,mer){if(mer==='am')return h===12?0:h;return h===12?12:h+12}
function lmvDurChips(){return [{label:'30 min',patch:{dur:30}},{label:'45 min',patch:{dur:45}},{label:'1 hour',patch:{dur:60}},{label:'90 min',patch:{dur:90}}]}
/* ===== capability registry ===== */
const LMV_CAPS=[
 {intentId:'life_director.create_activity',domain:'life_director',title:'Schedule an activity',exampleUtterance:'Put a one hour workout on my Life Director tomorrow morning at nine',evaluateStatus:function(){return lmvCapStatus(true)}},
 {intentId:'life_director.query_schedule',domain:'life_director',title:'What is on my schedule',exampleUtterance:'What is on my schedule tomorrow',evaluateStatus:function(){return lmvCapStatus(true)}},
 {intentId:'life_director.find_free_time',domain:'life_director',title:'When am I free',exampleUtterance:'When am I free today',evaluateStatus:function(){return lmvCapStatus(true)}},
 {intentId:'life_director.complete_activity',domain:'life_director',title:'Mark an activity done',exampleUtterance:'I just finished my workout',evaluateStatus:function(){return lmvCapStatus(true)}},
 {intentId:'life_director.move_activity',domain:'life_director',title:'Move an activity',exampleUtterance:'Push my workout back 30 minutes',evaluateStatus:function(){return lmvCapStatus(true)}},
 {intentId:'life_director.cancel_activity',domain:'life_director',title:'Cancel an activity',exampleUtterance:'Cancel my workout today',evaluateStatus:function(){return lmvCapStatus(true)}},
 {intentId:'rpg.view_quests',domain:'rpg',title:'Quests',exampleUtterance:'What are my quests today',evaluateStatus:function(){return 'EXECUTABLE'}},
 {intentId:'rpg.query_stats',domain:'rpg',title:'Character, XP and level',exampleUtterance:'What level am I',evaluateStatus:function(){return 'EXECUTABLE'}},
 {intentId:'health_fitness.query_workouts',domain:'health_fitness',title:'Workouts',exampleUtterance:'How many workouts did I do this week',evaluateStatus:function(){return 'EXECUTABLE'}},
 {intentId:'health_fitness.log_activity',domain:'health_fitness',title:'Log what you did',exampleUtterance:'Log a 30 minute run',evaluateStatus:function(){return 'EXECUTABLE'}},
 {intentId:'health_fitness.query_steps',domain:'health_fitness',title:'Steps and activity',exampleUtterance:'How many steps do I have today',evaluateStatus:function(){return 'EXECUTABLE'}},
 {intentId:'nutrition.check_totals',domain:'nutrition',title:'Calories and macros',exampleUtterance:'How many calories have I had today',evaluateStatus:function(){return 'EXECUTABLE'}},
 {intentId:'virtual_pets.check_status',domain:'virtual_pets',title:'How is my cat',exampleUtterance:'How is my cat doing',evaluateStatus:function(){return 'EXECUTABLE'}},
 {intentId:'virtual_pets.perform_action',domain:'virtual_pets',title:'Feed or pet my cat',exampleUtterance:'Feed my cat',evaluateStatus:function(){return 'EXECUTABLE'}},
 {intentId:'navigation_system.open_section',domain:'navigation_system',title:'Open a screen',exampleUtterance:'Take me to my quests',evaluateStatus:function(){return 'EXECUTABLE'}}
];
function lmvCapStatus(){
 try{if(!(window.LifeDirector&&LifeDirector.voice))return 'UNSUPPORTED_ON_DEVICE';if(!LifeDirector.voice.ready())return 'CONFIGURATION_REQUIRED'}catch(e){return 'UNSUPPORTED_ON_DEVICE'}
 return 'EXECUTABLE';
}
/* ===== operation journal: tier 1 delivery guard, tier 2 persistent journal, tier 3 probable-repeat fingerprint ===== */
function lmvHash(s){let h=2166136261;for(let i=0;i<s.length;i++){h^=s.charCodeAt(i);h=Math.imul(h,16777619)>>>0}return h.toString(16)}
function lmvFingerprint(intentId,title,slot){return lmvHash([intentId,String(title||'').trim().toLowerCase().replace(/\s+/g,' '),slot.localDate,slot.localStartTime,slot.durationMinutes].join('|'))}
function lmvJLoad(){try{const a=JSON.parse(localStorage.getItem(LMV.journal)||'[]');return Array.isArray(a)?a:[]}catch(e){return []}}
function lmvJSave(a){try{localStorage.setItem(LMV.journal,JSON.stringify(a.slice(-LMV.maxOps)))}catch(e){}}
function lmvJGet(op){return lmvJLoad().filter(function(e){return e.operationId===op})[0]||null}
function lmvJPut(e){const a=lmvJLoad(),i=a.findIndex(function(x){return x.operationId===e.operationId});e.updatedAtEpochMs=Date.now();if(i>=0)a[i]=e;else a.push(e);lmvJSave(a)}
function lmvDeliver(utteranceId){if(!utteranceId)return true;if(lmvDelivered.has(utteranceId))return false;lmvDelivered.add(utteranceId);if(lmvDelivered.size>400)lmvDelivered=new Set(Array.from(lmvDelivered).slice(-200));return true}
function lmvEntityId(op){return 'vo_'+String(op).replace(/^op_/,'')}
/* ===== Life Director adapter ===== */
const lmvLD={
 api:function(){return window.LifeDirector&&LifeDirector.voice||null},
 conflicts:function(slot){const a=lmvLD.api();if(!a||!a.ready())return [];try{return a.overlaps(Math.floor(slot.startEpochMs/1000),Math.floor(slot.endEpochMs/1000))}catch(e){return []}},
 /* by id; and, only when an earlier attempt may have been cut off, by title and slot among commitments no other operation owns */
 existing:function(op,title,slot,recovering){const a=lmvLD.api();if(!a)return null;try{
  const byId=a.find(lmvEntityId(op));if(byId||!recovering)return byId;
  const owned={};lmvJLoad().forEach(function(e){if(e.operationId!==op&&e.createdEntityId)owned[e.createdEntityId]=1});
  const m=a.findMatch(title,Math.floor(slot.startEpochMs/1000),Math.floor(slot.endEpochMs/1000));return m&&!owned[String(m.id)]?m:null}catch(e){return null}},
 sameSlot:function(title,slot){const a=lmvLD.api();if(!a)return false;try{return !!a.findMatch(title,Math.floor(slot.startEpochMs/1000),Math.floor(slot.endEpochMs/1000))}catch(e){return false}},
 create:function(op,title,slot){const a=lmvLD.api();if(!a)return {ok:false,reason:'NO_LIFE_DIRECTOR'};return a.add({id:lmvEntityId(op),title:title,startEpoch:Math.floor(slot.startEpochMs/1000),endEpoch:Math.floor(slot.endEpochMs/1000)})},
 remove:function(id){const a=lmvLD.api();return !!(a&&a.remove(id))},
 canUndo:function(){const a=lmvLD.api();return !!(a&&typeof a.remove==='function')}
};
/* ===== dialogue + transaction manager ===== */
function lmvNewFrame(){return {operationId:'op_'+lmvUuid(),state:'PROPOSED',raw:null,question:null,slot:null,title:null,notices:[],conflicts:[],dup:null,lowConf:false,dupOk:false,intentId:'life_director.create_activity',createdAt:Date.now(),error:null,entityId:null,undo:false,transcript:'',confidence:1}}
function lmvMerge(frame,patch){
 if(!frame.raw)frame.raw={title:null,date:null,dateAmbig:null,hour:null,minute:0,mer:null,daypart:null,dur:null,endHour:null,endMinute:0,endMer:null,anchor:null};
 Object.keys(patch).forEach(function(k){if(k==='intent'||patch[k]===undefined)return;frame.raw[k]=patch[k]});
}
function lmvLogLine(s){lmvLog.push(new Date().toLocaleTimeString()+'  '+s);if(lmvLog.length>60)lmvLog.shift()}
function lmvSnapshot(){const f=lmvFrame;return f?{act:f.act?{lines:f.act.lines.slice(),label:f.act.label}:null,operationId:f.operationId,intentId:f.intentId,head:f.head||null,actions:(f.actions||[]).map(function(a){return a.label}),state:f.state,question:f.question,slot:f.slot,title:f.title,notices:f.notices.slice(),conflicts:f.conflicts.slice(),dup:f.dup,entityId:f.entityId,undo:f.undo,error:f.error,raw:f.raw}:{state:'IDLE'}}
/* advance a frame as far as it can go without the user */
function lmvAdvance(f,ctx){
 const res=lmvFinalize(f.raw,ctx);
 lmvMark('t4_nlu_done');
 if(res.status==='NEED'){f.state='AWAITING_CLARIFICATION';f.question={kind:res.kind,text:res.question,chips:res.chips,flag:res.flag};f.slot=null;f.notices=[];return f}
 f.question=null;f.title=res.title;f.slot=res.slot;f.state='VALIDATED';
 const notices=[];
 if(res.slot.assumptions.indexOf('MERIDIEM_INFERRED')>=0)notices.push({kind:'info',text:'Assumed '+lmvNiceTime(res.slot.localStartTime).slice(-2)+' because of the kind of activity. Say or tap Cancel if that is wrong.'});
 if(res.slot.durationDefaulted)notices.push({kind:'info',text:'No length given, so I used a standard '+LMV_DEFAULT_MIN+' minutes.'});
 if(res.oddText)notices.push({kind:'duplicate',text:res.oddText});
 f.conflicts=lmvLD.conflicts(res.slot);
 if(f.conflicts.length)notices.push({kind:'conflict',text:'Overlaps '+f.conflicts.map(function(c){return c.title}).join(', ')+'.'});
 /* tier 3: a new request that looks just like one that was just saved */
 f.dup=null;
 const fp=lmvFingerprint(f.intentId,res.title,res.slot);f.fingerprint=fp;
 const recent=lmvJLoad().some(function(e){return e.status==='COMMITTED'&&e.semanticFingerprint===fp&&e.operationId!==f.operationId&&Date.now()-e.updatedAtEpochMs<LMV.dupWindowMs});
 if(!f.dupOk&&(recent||lmvLD.sameSlot(res.title,res.slot))){f.dup=true;notices.push({kind:'duplicate',text:'This looks the same as something already scheduled at that time.'})}
 if(f.confidence<LMV.lowConf)notices.push({kind:'confidence',text:'I was not sure I heard that right. Please check it.'});
 f.notices=notices;
 f.state='PREVIEWED';
 /* straight through only when the user turned the preview off and nothing needs a look */
 if(!lmvPrefs().alwaysPreview&&!notices.length)return lmvAuthorize(f);
 return f;
}
function lmvAuthorize(f){
 if(f.state!=='PREVIEWED'&&f.state!=='VALIDATED')return f;
 lmvMark('t5_auth');f.state='AUTHORIZED';
 return lmvExecute(f);
}
function lmvExecute(f){
 const op=f.operationId;
 if(lmvExec[op])return f;                                  /* a second tap while it is running does nothing */
 let j=lmvJGet(op);
 if(j&&j.status==='COMMITTED'){f.state='CONFIRMED';f.entityId=j.createdEntityId;f.undo=lmvLD.canUndo()&&!!f.entityId;return f}
 lmvExec[op]=true;
 try{
  const recovering=!!j;
  if(!j){j={operationId:op,intentId:f.intentId,canonicalSlots:Object.assign({title:f.title},f.slot),semanticFingerprint:f.fingerprint,status:'PREPARING',createdAtEpochMs:Date.now(),updatedAtEpochMs:Date.now()};lmvJPut(j)}
  f.state='EXECUTING';
  /* a PREPARING entry may have been saved before the response was lost: look first */
  const have=lmvLD.existing(op,f.title,f.slot,recovering);
  let id=null;
  if(have){id=String(have.id)}
  else{const r=lmvLD.create(op,f.title,f.slot);if(!r||!r.ok){j.status='FAILED';lmvJPut(j);f.state='FAILED';f.error=r&&r.reason==='NOT_SET_UP'?'Life Director is not set up yet. Open it and plan your days first.':'Life Director could not fit that in. Nothing was saved.';return f}id=String(r.id)}
  lmvMark('t6_saved');
  j.status='COMMITTED';j.createdEntityId=id;lmvJPut(j);
  f.state='COMMITTED';f.entityId=id;lmvLastRef={id:id,title:f.title};
  lmvAfterPaint(function(){lmvMark('t7_ui_done');lmvTelemetryDone()});
  f.state='CONFIRMED';f.undo=lmvLD.canUndo();
 }catch(e){const j2=lmvJGet(op);if(j2){j2.status='FAILED';lmvJPut(j2)}f.state='FAILED';f.error=String((e&&e.message)||e).slice(0,140)}
 finally{delete lmvExec[op]}
 return f;
}
function lmvUndo(){
 const f=lmvFrame;if(!f||f.state!=='CONFIRMED'||!f.undo||!f.entityId)return false;
 const ok=lmvLD.remove(f.entityId);
 if(ok){const j=lmvJGet(f.operationId);if(j){j.status='FAILED';j.undone=true;lmvJPut(j)}f.state='CANCELLED';f.undo=false;f.notices=[{kind:'info',text:'Removed.'}]}
 lmvRender();return ok;
}
/* the one entry point for anything the user says or types */
function lmvEnd(f,msg){f.state='CANCELLED';f.question=null;f.act=null;f.notices=[{kind:'info',text:msg||'Okay, cancelled.'}]}
function lmvRunAct(f){const a=f.act;f.act=null;if(a&&a.run)a.run()}
function lmvHandle(text,meta){
 meta=meta||{};
 if(!lmvEnabled())return lmvSnapshot();
 if(!lmvDeliver(meta.utteranceId)){lmvLogLine('duplicate delivery dropped '+meta.utteranceId);return lmvSnapshot()}
 const ctx=lmvCtx(),clean=String(text==null?'':text).slice(0,500);
 lmvMark('t3_stt_done');
 const n=lmvNormalize(clean);
 let f=lmvFrame;
 const active=f&&(f.state==='AWAITING_CLARIFICATION'||f.state==='PREVIEWED');
 /* yes / no while a preview is showing */
 if(active&&f.state==='PREVIEWED'){
  if(/^(yes|yeah|yep|confirm|confirmed|save|save it|do it|sure|go ahead|correct|that's right|thats right|create another|create duplicate|remove it|move it|confirm remove)$/.test(n)){if(f.act){lmvRunAct(f)}else{if(f.dup)f.dupOk=true;lmvAuthorize(f)}lmvRender();return lmvSnapshot()}
  if(/^(no|nope)$/.test(n)){lmvEnd(f,'Okay, cancelled.');lmvRender();return lmvSnapshot()}
 }
 /* universal escape: works at any point, leaves nothing half-built */
 if(LMV_B.abort.test(n)){if(f&&active)lmvEnd(f,'Okay, cancelled.');lmvRender();return lmvSnapshot()}
 /* a reply to an open question: "any time", "now", "in 30 minutes", "morning" ... */
 if(active&&f.state==='AWAITING_CLARIFICATION'){
  const mr=lmvMeta(f,n,ctx);
  if(mr){
   if(mr.fail){f.question.text=mr.fail;lmvRender();return lmvSnapshot()}
   const pt=Object.assign({},mr.patch);if(mr.date)pt.date=mr.date;
   f.autoNote=mr.note||null;lmvMerge(f,pt);lmvAdvance(f,ctx);
   if(f.autoNote&&f.state==='PREVIEWED')f.notices.unshift({kind:'info',text:f.autoNote});
   lmvRender();return lmvSnapshot();
  }
 }
 /* "what about yesterday?": the last question again, with a new day */
 if(!active){const fu=lmvFollowUp(clean,ctx);if(fu){f=lmvFrame=lmvNewFrame();f.confidence=meta.confidence==null?1:meta.confidence;const r=lmvDomain(fu.cls,fu.text,f,ctx);lmvRender();return r||lmvSnapshot()}}
 /* what is this sentence FOR? A question or another domain never becomes a new activity. */
 const cleanP=lmvPhon(clean),cls=lmvClassify(cleanP);
 if(cls.id!=='life_director.create_activity'&&cls.conf>=.78){
  if(active)f.state='CANCELLED';          /* barge-in: a new complete command replaces the half-finished one */
  f=lmvFrame=lmvNewFrame();f.confidence=meta.confidence==null?1:meta.confidence;
  const r=lmvDomain(cls,cleanP,f,ctx);lmvRender();return r||lmvSnapshot();
 }
 const fresh=!active;
 if(fresh){f=lmvFrame=lmvNewFrame();f.raw=null}
 f.transcript=clean;f.confidence=meta.confidence==null?1:meta.confidence;
 const expect=f.question?f.question.kind:null;
 const raw=lmvExtract(clean,ctx,expect);
 /* an answer to a pending question may carry only a time of day or a meridiem word */
 if(!fresh&&expect==='meridiem'){const w=' '+n+' ';if(/\b(am|morning)\b/.test(w))raw.mer=raw.mer||'am';else if(/\b(pm|evening|afternoon|night|tonight)\b/.test(w))raw.mer=raw.mer||'pm'}
 if(!fresh&&expect==='title'&&!raw.title){raw.title=n?n.charAt(0).toUpperCase()+n.slice(1):null}
 const patch={};
 Object.keys(raw).forEach(function(k){if(raw[k]!==null&&raw[k]!==undefined)patch[k]=raw[k]});
 if(raw.hour==null)delete patch.minute;if(raw.endHour==null)delete patch.endMinute;
 if(!fresh&&raw.hour!=null){patch.mer=raw.mer||null;patch.daypart=raw.daypart||null;patch.dst=null}
 if(!fresh&&raw.date){patch.dateAmbig=null}
 if(!fresh&&raw.dateAmbig){patch.date=null}
 if(!fresh&&expect&&expect!=='title'&&expect!=='dst'&&expect!=='past')delete patch.title;     /* answers never rename the activity */
 lmvMerge(f,patch);
 lmvAdvance(f,ctx);
 lmvRender();
 return lmvSnapshot();
}
/* chips and buttons */
function lmvAnswer(patch){
 const f=lmvFrame;if(!f||f.state!=='AWAITING_CLARIFICATION')return lmvSnapshot();
 lmvMerge(f,patch);
 lmvAdvance(f,lmvCtx());lmvRender();return lmvSnapshot();
}
function lmvConfirm(){const f=lmvFrame;if(!f||f.state!=='PREVIEWED')return lmvSnapshot();if(f.act){lmvRunAct(f);lmvRender();return lmvSnapshot()}if(f.dup)f.dupOk=true;lmvAuthorize(f);lmvRender();return lmvSnapshot()}
function lmvCancel(){const f=lmvFrame;if(f&&(f.state==='AWAITING_CLARIFICATION'||f.state==='PREVIEWED'))lmvEnd(f,'Okay, cancelled.');lmvRender();return lmvSnapshot()}
function lmvReset(){lmvFrame=null;lmvDelivered=new Set();lmvExec={};lmvRender()}
/* read-only query */
function lmvQuery(f,raw,ctx){
 f.intentId='life_director.query_schedule';
 const today=lmvYmd(lmvParts(ctx.now,ctx.tz)),date=raw.date||today;
 const a=lmvLD.api();let items=null;try{items=a&&a.ready()?a.dayItems(date):null}catch(e){}
 f.state='CONFIRMED';f.undo=false;f.title='Schedule for '+lmvNiceDate(date);
 f.notices=items===null?[{kind:'info',text:'Life Director is not set up yet.'}]:items.length?items.slice(0,8).map(function(it){const p=lmvParts(it.startEpoch*1000,ctx.tz);return {kind:'info',text:lmvNiceTime(lmvPad(p.h)+':'+lmvPad(p.mi))+'  '+it.title}}):[{kind:'info',text:'Nothing planned.'}];
 f.query=true;lmvRender();return lmvSnapshot();
}
/* ===== telemetry ===== */
const lmvMarks={};
function lmvMark(k){lmvMarks[k]=performance.now()}
function lmvAfterPaint(fn){requestAnimationFrame(function(){requestAnimationFrame(fn)})}
function lmvTelemetryDone(){
 const g=function(a,b){return lmvMarks[a]!=null&&lmvMarks[b]!=null?Math.round((lmvMarks[b]-lmvMarks[a])*10)/10:null};
 lmvLast={micActivationMs:g('t0_tap','t1_mic_ready'),eosToFinalTranscriptMs:g('t2_eos','t3_stt_done'),nluParseMs:g('t3_stt_done','t4_nlu_done'),persistenceMs:g('t5_auth','t6_saved'),coronaRefreshMs:g('t6_saved','t7_ui_done'),heapDeltaBytes:(performance.memory?performance.memory.usedJSHeapSize:null)};
 try{lmvDiagRender()}catch(e){}
}
/* ===== crash recovery: PREPARING entries left over from a reload ===== */
function lmvRecover(){
 const a=lmvJLoad();let ch=false;
 a.forEach(function(e){
  if(e.status!=='PREPARING')return;
  const have=lmvLD.api()&&(function(){try{return lmvLD.api().find(lmvEntityId(e.operationId))}catch(x){return null}})();
  if(have){e.status='COMMITTED';e.createdEntityId=String(have.id);e.updatedAtEpochMs=Date.now();ch=true}
  else if(Date.now()-e.updatedAtEpochMs>5*60000){e.status='FAILED';e.updatedAtEpochMs=Date.now();ch=true}
 });
 if(ch)lmvJSave(a);
}
/* ===== native bridge (Capacitor plugin "VoiceEngine"; commands: capabilities, requestPermission, start, stop, cancel) ===== */
function lmvPlugin(){return lmaNative()&&lmaPlugin('VoiceEngine')||null}
async function lmvBridgeInit(){
 const P=lmvPlugin();
 if(!P){lmvBridge={status:'NO_NATIVE_BRIDGE',detail:'Running in a browser: use the text simulator.'};return}
 try{
  if(!lmvBridge.bound){lmvBridge.bound=true;P.addListener('voice',lmvOnNative)}
  const c=await P.capabilities();lmvBridge={status:c.status,detail:'API '+c.api+' · '+c.engine+(c.permission?' · mic allowed':' · mic not allowed yet'),bound:true,cap:c};
 }catch(e){lmvBridge={status:'BRIDGE_ERROR',detail:String((e&&e.message)||e).slice(0,120),bound:lmvBridge.bound}}
}
function lmvOnNative(ev){
 if(!ev||ev.v!==1||typeof ev.event!=='string')return;
 const p=ev.payload||{};
 if(ev.event==='VOICE_READY'){lmvMark('t1_mic_ready');lmvListening=true;lmvRender()}
 else if(ev.event==='VOICE_EOS'){lmvMark('t2_eos');lmvListening=false;lmvRender()}
 else if(ev.event==='VOICE_PARTIAL'){lmvPartial=String(p.text||'').slice(0,300);lmvRender()}
 else if(ev.event==='VOICE_FINAL_TRANSCRIPT'){lmvListening=false;lmvPartial='';lmvHandle(String(p.text||''),{utteranceId:ev.utteranceId||p.utteranceId,confidence:typeof p.confidence==='number'?p.confidence:1});
  /* hands-free: after a question, listen for the answer */
  if(lmvFrame&&lmvFrame.state==='AWAITING_CLARIFICATION')setTimeout(lmvListen,250)}
 else if(ev.event==='VOICE_ERROR'){
  lmvListening=false;lmvPartial='';
  const code=String(p.code||'ENGINE_ERROR');
  lmvBridge.status=code==='NO_SPEECH'?lmvBridge.status:code;lmvVoiceMsg=({OFFLINE_ENGINE_NOT_READY:'Offline speech is not ready on this phone. Type your request instead (⌨), or add an offline speech pack in Android settings.',OFFLINE_MODEL_MISSING:'The phone’s offline speech pack is missing. Download it in Android Settings → Voice input / Speech services, or type your request (⌨).',NO_SPEECH:'I did not catch anything. Tap the mic and try again.',PERMISSION_MISSING:'Microphone permission is needed.'})[code]||'Speech engine error ('+(p.detail||'?')+').';
  lmvRender();
 }
}
let lmvPartial='',lmvVoiceMsg='';
async function lmvListen(){
 const P=lmvPlugin();lmvVoiceMsg='';
 if(!P){lmvVoiceMsg='Voice needs the Android app. Use the typing box (⌨) here.';lmvRender();return}
 try{
  lmvMark('t0_tap');
  const c=await P.capabilities();
  if(c.status!=='READY_ON_DEVICE'&&c.status!=='PERMISSION_MISSING'){lmvBridge.status=c.status;lmvVoiceMsg=c.status==='UNSUPPORTED_OS_VERSION'?'Offline speech needs Android 12 or newer. Use the typing box (⌨).':'Offline speech is not ready on this phone. Use the typing box (⌨), or add an offline speech pack in Android settings.';lmvRender();return}
  if(!c.permission){const g=await P.requestPermission();if(!g||!g.granted){lmvVoiceMsg='Microphone permission is needed.';lmvRender();return}}
  const sid='sess_'+lmvBoot+'_'+(++lmvSeq);
  const r=await P.start({sessionId:sid,locale:(navigator.language||'en-US')});
  if(r&&r.started===false){lmvVoiceMsg='Offline speech is not ready on this phone. Use the typing box (⌨).'}
 }catch(e){lmvVoiceMsg=String((e&&e.message)||e).slice(0,120)}
 lmvRender();
}
async function lmvStopListening(){try{const P=lmvPlugin();if(P)await P.cancel()}catch(e){}lmvListening=false;lmvPartial=''}
/* ===== UI: floating mic, HUD card, diagnostics + simulator (every untrusted string goes through textContent) ===== */
function lmvEl(tag,cls,text){const e=document.createElement(tag);if(cls)e.className=cls;if(text!=null)e.textContent=text;return e}
let lmvUI=null;
function lmvBuildUI(){
 if(lmvUI)return;
 const fab=lmvEl('button','btn secondary topgear lmv-fab');fab.type='button';fab.title='Voice';fab.setAttribute('aria-label','Voice');fab.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/></svg>';
 fab.addEventListener('click',function(){lmvHudOpen(true);if(lmvListening)lmvStopListening().then(lmvRender);else lmvListen()});
 const hud=lmvEl('div','lmv-hud');hud.setAttribute('role','dialog');hud.setAttribute('aria-label','Voice');
 const gear=document.querySelector('.top-actions .topgear');if(gear&&gear.parentNode)gear.parentNode.insertBefore(fab,gear);else document.body.appendChild(fab);document.body.appendChild(hud);
 fab.style.display='none';
 lmvUI={fab:fab,hud:hud,open:false};
}
function lmvHudOpen(on){if(!lmvUI)return;lmvUI.open=on;lmvUI.hud.classList.toggle('on',on);if(on)lmvRender()}
function lmvRender(){
 if(!lmvUI)return;
 lmvUI.fab.classList.toggle('lmv-live',lmvListening);
 const hud=lmvUI.hud;if(!lmvUI.open&&!lmvListening&&!(lmvFrame&&lmvFrame.state!=='IDLE'&&lmvFrame.state!=='CANCELLED'))return;
 if(!lmvUI.open&&(lmvListening||lmvFrame))lmvHudOpen(true);
 hud.textContent='';
 const f=lmvFrame,head=lmvEl('div','lmv-head');
 const st=lmvListening?'Listening…':f&&f.state==='AWAITING_CLARIFICATION'?'One more thing':f&&f.state==='PREVIEWED'?(f.act?(f.head||'Confirm'):'Check and save'):f&&f.state==='CONFIRMED'?(f.query?(f.head||'Your schedule'):'Saved'):f&&f.state==='FAILED'?'Could not save':'Voice';
 head.appendChild(lmvEl('b','',st));
 const kb=lmvEl('button','lmv-ic','⌨');kb.type='button';kb.setAttribute('aria-label','Type a command');kb.addEventListener('click',function(){lmvDiagOpen()});head.appendChild(kb);
 const x=lmvEl('button','lmv-ic','✕');x.type='button';x.setAttribute('aria-label','Close');x.addEventListener('click',function(){lmvStopListening();if(f&&(f.state==='AWAITING_CLARIFICATION'||f.state==='PREVIEWED'))f.state='CANCELLED';lmvHudOpen(false)});head.appendChild(x);
 hud.appendChild(head);
 if(lmvPartial)hud.appendChild(lmvEl('p','lmv-heard','“'+lmvPartial+'”'));
 else if(f&&f.transcript&&f.state!=='IDLE')hud.appendChild(lmvEl('p','lmv-heard','“'+f.transcript+'”'));
 if(lmvVoiceMsg)hud.appendChild(lmvEl('p','lmv-msg',lmvVoiceMsg));
 if(f&&f.state==='AWAITING_CLARIFICATION'&&f.question){
  hud.appendChild(lmvEl('p','lmv-q',f.question.text));
  const row=lmvEl('div','lmv-chips');
  f.question.chips.forEach(function(c){const b=lmvEl('button','lmv-chip',c.label);b.type='button';b.addEventListener('click',function(){lmvAnswer(c.patch)});row.appendChild(b)});
  const cx=lmvEl('button','lmv-chip lmv-ghost','Cancel');cx.type='button';cx.addEventListener('click',lmvCancel);row.appendChild(cx);
  hud.appendChild(row);
 }
 if(f&&f.state==='PREVIEWED'&&f.act){
  hud.appendChild(lmvEl('p','lmv-q',f.title));f.act.lines.forEach(function(t){hud.appendChild(lmvEl('p','lmv-note lmv-info',t))});
  const row=lmvEl('div','lmv-chips');
  const ok=lmvEl('button','lmv-chip lmv-go',f.act.label||'Confirm');ok.type='button';ok.addEventListener('click',lmvConfirm);
  const cx=lmvEl('button','lmv-chip lmv-ghost','Cancel');cx.type='button';cx.addEventListener('click',lmvCancel);row.appendChild(ok);row.appendChild(cx);hud.appendChild(row);
 }
 if(f&&f.state==='PREVIEWED'&&f.slot&&!f.act){hud.appendChild(lmvCard(f));
  f.notices.forEach(function(n){hud.appendChild(lmvEl('p','lmv-note lmv-'+n.kind,n.text))});
  const row=lmvEl('div','lmv-chips');
  const ok=lmvEl('button','lmv-chip lmv-go',f.dup?'Create another':'Save activity');ok.type='button';ok.addEventListener('click',lmvConfirm);
  const cx=lmvEl('button','lmv-chip lmv-ghost','Cancel');cx.type='button';cx.addEventListener('click',lmvCancel);row.appendChild(ok);row.appendChild(cx);hud.appendChild(row);
 }
 if(f&&f.state==='CONFIRMED'){
  if(f.slot&&!f.query)hud.appendChild(lmvCard(f));
  if(f.query)hud.appendChild(lmvEl('p','lmv-q',f.title));
  f.notices.forEach(function(n){hud.appendChild(lmvEl('p','lmv-note lmv-info',n.text))});
  const row=lmvEl('div','lmv-chips');
  (f.actions||[]).forEach(function(a){const b=lmvEl('button','lmv-chip',a.label);b.type='button';b.addEventListener('click',function(){a.run();lmvRender()});row.appendChild(b)});
  if(f.undo){const u=lmvEl('button','lmv-chip',"Undo");u.type='button';u.addEventListener('click',lmvUndo);row.appendChild(u)}
  const d=lmvEl('button','lmv-chip lmv-ghost','Done');d.type='button';d.addEventListener('click',function(){lmvFrame=null;lmvHudOpen(false)});row.appendChild(d);hud.appendChild(row);
 }
 if(f&&f.state==='FAILED'){hud.appendChild(lmvEl('p','lmv-note lmv-conflict',f.error||'Something went wrong.'));const row=lmvEl('div','lmv-chips');const r=lmvEl('button','lmv-chip lmv-go','Try again');r.type='button';r.addEventListener('click',function(){f.state='PREVIEWED';lmvAuthorize(f);lmvRender()});const d=lmvEl('button','lmv-chip lmv-ghost','Close');d.type='button';d.addEventListener('click',function(){lmvFrame=null;lmvHudOpen(false)});row.appendChild(r);row.appendChild(d);hud.appendChild(row)}
 if(f&&f.state==='CANCELLED'&&!lmvListening){hud.appendChild(lmvEl('p','lmv-q',f.notices&&f.notices[0]?f.notices[0].text:'Cancelled.'))}
 if(!f&&!lmvListening&&!lmvVoiceMsg)hud.appendChild(lmvEl('p','lmv-q','Tap the mic. Schedule something, or ask about your schedule, quests, XP, cat, steps or food.'));
}
function lmvCard(f){
 const s=f.slot,c=lmvEl('div','lmv-card');
 c.appendChild(lmvEl('div','lmv-ct',f.title));
 c.appendChild(lmvEl('div','lmv-cl',lmvNiceDate(s.localDate)));
 c.appendChild(lmvEl('div','lmv-cl',lmvNiceTime(s.localStartTime)+' – '+lmvNiceTime(s.localEndTime)+'  ·  '+lmvDurText(s.durationMinutes)+(s.durationDefaulted?' (standard)':'')));
 return c;
}
/* diagnostics + text simulator */
function lmvDiagOpen(){
 if(document.querySelector('.lmv-diag'))return;
 const o=lmvEl('div','hsheet lmv-diag');o.setAttribute('role','dialog');o.setAttribute('aria-label','Voice diagnostics');
 lmvBuildUI();document.body.appendChild(o);lmvBridgeInit().then(lmvDiagRender);lmvDiagRender();
}
function lmvDiagRender(){
 const o=document.querySelector('.lmv-diag');if(!o)return;
 const keep=o.querySelector('input'),val=keep?keep.value:'';
 o.textContent='';
 const head=lmvEl('header');const back=lmvEl('button','btn small secondary','‹ Back');back.type='button';back.addEventListener('click',function(){o.remove()});head.appendChild(back);head.appendChild(lmvEl('h2','','Voice'));head.appendChild(lmvEl('span','rs-bal',lmvEnabled()?'on':'off'));o.appendChild(head);
 const body=lmvEl('div','hs-body');o.appendChild(body);
 const card=function(title){const c=lmvEl('div','card');c.appendChild(lmvEl('h3','',title));body.appendChild(c);return c};
 /* simulator */
 const sim=card('Type a command');
 sim.appendChild(lmvEl('p','muted','Runs exactly what speech would, without the microphone.'));
 const inp=lmvEl('input','lmv-sim');inp.type='text';inp.placeholder='Put an hour workout tomorrow morning at nine';inp.value=val;inp.setAttribute('autocomplete','off');
 const send=lmvEl('button','btn','Send');send.type='button';
 const go=function(){const t=inp.value;if(!t.trim())return;inp.value='';lmvHudOpen(true);lmvHandle(t,{utteranceId:'sim_'+lmvBoot+'_'+(++lmvSeq)});lmvDiagRender()};
 send.addEventListener('click',go);inp.addEventListener('keydown',function(e){if(e.key==='Enter')go()});
 const row=lmvEl('div','lmv-simrow');row.appendChild(inp);row.appendChild(send);sim.appendChild(row);
 const ex=lmvEl('div','lmv-chips');
 ['Put an hour long workout on my Life Director tomorrow morning at nine','Schedule some gym time tomorrow','What is on my schedule today'].forEach(function(t){const b=lmvEl('button','lmv-chip lmv-ghost',t.length>34?t.slice(0,32)+'…':t);b.type='button';b.addEventListener('click',function(){inp.value=t});ex.appendChild(b)});
 sim.appendChild(ex);
 const f=lmvFrame;
 if(f&&f.state!=='IDLE')sim.appendChild(lmvEl('p','muted','State: '+f.state+(f.question?' · '+f.question.text:'')));
 /* status */
 const stc=card('Speech engine');
 [['Provider','Android on-device recognizer (API 31+), no cloud fallback'],['Status',lmvBridge.status],['Detail',lmvBridge.detail||'–']].forEach(function(r){const l=lmvEl('div','lmv-kv');l.appendChild(lmvEl('span','',r[0]));l.appendChild(lmvEl('b','',r[1]));stc.appendChild(l)});
 /* telemetry */
 const tc=card('Last command timings (measured on this phone)');
 const names=['micActivationMs','eosToFinalTranscriptMs','nluParseMs','persistenceMs','coronaRefreshMs','heapDeltaBytes'];
 names.forEach(function(k){const l=lmvEl('div','lmv-kv');l.appendChild(lmvEl('span','',k));l.appendChild(lmvEl('b','',lmvLast[k]==null?'–':String(lmvLast[k])));tc.appendChild(l)});
 /* journal */
 const jc=card('Operation journal');
 const ents=lmvJLoad().slice(-5).reverse();
 if(!ents.length)jc.appendChild(lmvEl('p','muted','Empty.'));
 ents.forEach(function(e){const l=lmvEl('div','lmv-kv');l.appendChild(lmvEl('span','',e.operationId.slice(0,11)+'… '+(e.canonicalSlots&&e.canonicalSlots.title||'')));l.appendChild(lmvEl('b','',e.status));jc.appendChild(l)});
 /* capabilities */
 const cc=card('Capabilities');
 LMV_CAPS.forEach(function(c){const l=lmvEl('div','lmv-kv');l.appendChild(lmvEl('span','',c.intentId));l.appendChild(lmvEl('b','',c.evaluateStatus()));cc.appendChild(l)});
 /* settings */
 const sc=card('Settings');
 const mk=function(label,checked,fn){const l=lmvEl('label','lmv-tog');const c=lmvEl('input');c.type='checkbox';c.checked=checked;c.addEventListener('change',function(){fn(c.checked)});l.appendChild(c);l.appendChild(lmvEl('span','',label));sc.appendChild(l)};
 mk('Voice enabled',lmvEnabled(),function(v){try{localStorage.setItem(LMV.flag,v?'true':'false')}catch(e){}lmvApplyFlag();lmvDiagRender()});
 mk('Always preview before saving',lmvPrefs().alwaysPreview,function(v){lmvSetPrefs({alwaysPreview:v})});
 if(lmvLog.length){const lc=card('Log');lc.appendChild(lmvEl('pre','lmv-log',lmvLog.slice(-12).join('\n')))}
}
function lmvApplyFlag(){
 if(!lmvEnabled()){if(lmvUI){lmvUI.fab.style.display='none';lmvHudOpen(false)}return}
 if(lmaNative()||lmvPrefs().debug){lmvBuildUI();lmvUI.fab.style.display=''}
}
/* Settings card */
function lmvSettingsRender(){
 const el=document.getElementById('lmvSettingsBody');if(!el)return;
 el.textContent='';
 const row=lmvEl('div','settingsline');const left=lmvEl('div');left.style.flex='1';left.appendChild(lmvEl('strong','','Voice scheduling'));left.appendChild(lmvEl('div','muted','Offline only. Speak or type a request and it goes into Life Director after you confirm.')).style.fontSize='12px';
 const open=lmvEl('button','btn small','Open');open.type='button';open.addEventListener('click',lmvDiagOpen);row.appendChild(left);row.appendChild(open);el.appendChild(row);
 const r2=lmvEl('div','settingsline');const l2=lmvEl('div');l2.style.flex='1';l2.appendChild(lmvEl('strong','','Turn voice on'));const c=lmvEl('input','check');c.type='checkbox';c.checked=lmvEnabled();c.addEventListener('change',function(){try{localStorage.setItem(LMV.flag,c.checked?'true':'false')}catch(e){}lmvApplyFlag()});r2.appendChild(l2);r2.appendChild(c);el.appendChild(r2);
}
/* public surface (also what the tests use) */
window.LifeMaxVoice=Object.freeze({
 simulateUtterance:function(text,opts){opts=opts||{};if(opts.now||opts.tz){lmvOverride={now:opts.now,tz:opts.tz,workOn:opts.workOn}}else lmvOverride=lmvOverride&&lmvOverride.sticky?lmvOverride:null;return lmvHandle(text,{utteranceId:opts.utteranceId,confidence:opts.confidence})},
 setClock:function(o){lmvOverride=o?{now:o.now,tz:o.tz,workOn:o.workOn,sticky:true}:null},
 answer:lmvAnswer,confirm:lmvConfirm,cancel:lmvCancel,undo:lmvUndo,reset:lmvReset,snapshot:lmvSnapshot,
 registry:function(){return LMV_CAPS.map(function(c){return {intentId:c.intentId,domain:c.domain,title:c.title,status:c.evaluateStatus()}})},
 _t:{scope:function(t){return lmvScope(t,lmvCtx())},phon:function(t){return lmvPhon(t)},classify:function(t){return lmvClassify(lmvPhon(t))},applyFlag:lmvApplyFlag,buildUI:lmvBuildUI,normalize:lmvNormalize,extract:lmvExtract,finalize:lmvFinalize,localToEpoch:lmvLocalToEpoch,journal:lmvJLoad,journalPut:lmvJPut,execute:function(op){return lmvFrame&&lmvFrame.operationId===op?lmvExecute(lmvFrame):null},fingerprint:lmvFingerprint,recover:lmvRecover,frame:function(){return lmvFrame},ctx:lmvCtx}
});
(function lmvBoot2(){
 setTimeout(function(){try{lmvRecover();lmvApplyFlag();lmvBridgeInit();lmvSettingsRender()}catch(e){console.warn('[voice]',e)}},2500);
 const nav=navigate;navigate=function(v){const r=nav.apply(this,arguments);if(v==='settings')try{lmvSettingsRender()}catch(e){}return r};
 document.addEventListener('visibilitychange',function(){if(document.visibilityState!=='visible'){lmvStopListening()}});
})();
