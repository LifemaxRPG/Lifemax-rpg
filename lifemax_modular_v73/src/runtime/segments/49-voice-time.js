/* ---------- LifeMax Voice, temporal layer: questions and logs about the PAST as well as the future. One resolver turns
   "yesterday", "two days ago", "last Friday", "this week", "past 7 days", "October 20", "earlier today" into a day or a
   range; every read handler uses it, so an answer is always about the day that was asked, and a day with nothing in it
   says so ("0 steps yesterday") instead of quietly reporting today. Also: speech-to-text near-miss repair ("west" for
   "quests"), a follow-up memory ("what about yesterday?"), and logging an activity that already happened. ---------- */

/* ===== speech-to-text repair: common mishearings of this app's words, then a one-letter fuzzy match ===== */
const LMV_DICT=['quests','schedule','calendar','workout','workouts','calories','protein','achievements','streak','experience','character','library','settings','overview','dashboard','macros','journal','corona','director'];
function lmvLev(a,b){if(a===b)return 0;if(Math.abs(a.length-b.length)>1)return 2;const m=a.length,n=b.length,d=[];for(let i=0;i<=m;i++){d[i]=[i]}for(let j=1;j<=n;j++)d[0][j]=j;for(let i=1;i<=m;i++)for(let j=1;j<=n;j++)d[i][j]=Math.min(d[i-1][j]+1,d[i][j-1]+1,d[i-1][j-1]+(a[i-1]===b[j-1]?0:1));return d[m][n]}
function lmvPhon(raw){
 let s=' '+String(raw==null?'':raw).toLowerCase().replace(/[‘’]/g,"'")+' ';
 s=s.replace(/\b(my|daily|active|the|our|your|open|show|check|list|view|are|have|any|all|finished|completed|about)\s+(west|wests|crest|crests|requests?|kwests?|quist|quists|quest's|questions?)\b/g,'$1 quests');
 s=s.replace(/\bcarona\b|\bkorona\b|\bcorona's\b/g,'corona').replace(/\bcrown (dial|clock|wheel)\b/g,'corona $1');
 s=s.replace(/\blife (?:direct (?:her|or|er)|directer|director's|directors)\b/g,'life director');
 s=s.replace(/\bmack rose\b|\bmacos\b|\bmac os\b|\bmacro's\b|\bmack roes\b/g,'macros');
 s=s.replace(/\bwork out\b/g,'workout').replace(/\bwork outs\b/g,'workouts');
 s=s.replace(/\bex[\s-]?pee\b|\bx[\s-]?p\b|\bexp\b/g,'xp').replace(/\bexperience points?\b/g,'xp');
 s=s.replace(/\bhow many (stairs|stents|steppes?|stepps?|stops)\b/g,'how many steps');
 s=s.replace(/\bhow far did i (?:walk|walked|go)\b/g,'how many steps did i walk').replace(/\bwhat'?s my step count\b|\bwhat is my step count\b/g,'how many steps do i have');
 s=s.replace(/\b[a-z]{6,}\b/g,function(w){if(LMV_DICT.indexOf(w)>=0)return w;for(let i=0;i<LMV_DICT.length;i++){const d=LMV_DICT[i];if(Math.abs(d.length-w.length)<=1&&d.length>=6&&w[0]===d[0]&&lmvLev(w,d)<=1)return d}return w});
 return s.replace(/\s+/g,' ').trim();
}

/* ===== the temporal scope: a day or a range, in the past or future ===== */
const LMV_WORDN={zero:0,one:1,two:2,three:3,four:4,five:5,six:6,seven:7,eight:8,nine:9,ten:10,eleven:11,twelve:12,thirteen:13,fourteen:14,fifteen:15,a:1,an:1};
function lmvWeekStart(ymd){return lmvAddDays(ymd,-((lmvWeekday(ymd)+6)%7))}
function lmvNice(ymd){const a=ymd.split('-').map(Number);return new Date(Date.UTC(a[0],a[1]-1,a[2])).toLocaleDateString('en-US',{timeZone:'UTC',weekday:'long',month:'short',day:'numeric'})}
function lmvDayScope(date,today,label,extra){
 const diff=Math.round((Date.parse(date+'T12:00:00Z')-Date.parse(today+'T12:00:00Z'))/86400000);
 const l=label||(diff===0?'today':diff===-1?'yesterday':diff===1?'tomorrow':lmvNice(date));
 return Object.assign({kind:'day',date:date,start:date,end:date,label:l,isPast:diff<0,isFuture:diff>0,explicit:true,full:lmvNice(date)},extra||{});
}
function lmvScope(text,ctx,preferPast){
 const today=lmvToday(ctx),raw=lmvPhon(text),n=' '+lmvNormalize(raw)+' ',s=' '+raw+' ';
 const past=/\b(did|had|was|were|walked|ate|eaten|earned|logged|got|took|ran|worked|finished|completed|burned|made|done|have i)\b/.test(s);
 const rng=function(a,b,l){return {kind:'range',start:a,end:b,date:b,label:l,isPast:b<today,isFuture:false,explicit:true,full:lmvNice(a)+' to '+lmvNice(b)}};
 let m;
 if(/\b(all time|overall|in total|total xp|lifetime|ever)\b/.test(s)&&!/\b(this week|today|yesterday)\b/.test(s))return {kind:'all',start:'0000-01-01',end:today,date:today,label:'all time',isPast:false,explicit:true,full:'all time'};
 if(/\blast week\b/.test(s)){const ws=lmvAddDays(lmvWeekStart(today),-7);return rng(ws,lmvAddDays(ws,6),'last week')}
 if(/\b(this week|so far this week)\b/.test(s))return rng(lmvWeekStart(today),today,'this week');
 if(/\blast month\b/.test(s)){const a=today.split('-').map(Number),pm=new Date(Date.UTC(a[0],a[1]-2,1)),st=pm.getUTCFullYear()+'-'+lmvPad(pm.getUTCMonth()+1)+'-01',en=lmvAddDays(today.slice(0,7)+'-01',-1);return rng(st,en,'last month')}
 if(/\bthis month\b/.test(s))return rng(today.slice(0,7)+'-01',today,'this month');
 if((m=/\b(?:past|last|previous)\s+(\d+)\s+days?\b/.exec(n))&&+m[1]>=2&&+m[1]<=90)return rng(lmvAddDays(today,-(+m[1]-1)),today,'the past '+m[1]+' days');
 if(/\bday before yesterday\b/.test(s))return lmvDayScope(lmvAddDays(today,-2),today);
 if((m=/\b(\d+)\s+days?\s+ago\b/.exec(n))&&+m[1]<=400)return lmvDayScope(lmvAddDays(today,-(+m[1])),today);
 if(/\blast night\b/.test(s))return lmvDayScope(lmvAddDays(today,-1),today,'last night',{dayPart:'night'});
 if(/\byesterday\b/.test(s)){const dp=/\bmorning\b/.test(s)?'morning':/\bafternoon\b/.test(s)?'afternoon':/\b(evening|night)\b/.test(s)?'evening':null;return lmvDayScope(lmvAddDays(today,-1),today,'yesterday',{dayPart:dp})}
 if(/\b(earlier today|so far today|this morning|this afternoon|this evening|tonight)\b/.test(s)){const dp=/\bmorning\b/.test(s)?'morning':/\bafternoon\b/.test(s)?'afternoon':/\b(evening|tonight)\b/.test(s)?'evening':null;return lmvDayScope(today,today,'today',{dayPart:dp,earlier:/\bearlier\b/.test(s)||/\bthis (morning|afternoon)\b/.test(s)})}
 if(/\bday after tomorrow\b/.test(s))return lmvDayScope(lmvAddDays(today,2),today);
 if(/\btomorrow\b/.test(s)){const dp=/\bmorning\b/.test(s)?'morning':/\bafternoon\b/.test(s)?'afternoon':/\b(evening|night)\b/.test(s)?'evening':null;return lmvDayScope(lmvAddDays(today,1),today,'tomorrow',{dayPart:dp})}
 if(/\btoday\b/.test(s))return lmvDayScope(today,today,'today',{dayPart:/\bmorning\b/.test(s)?'morning':/\bafternoon\b/.test(s)?'afternoon':null});
 if((m=/\blast\s+(sunday|monday|tuesday|wednesday|thursday|friday|saturday)\b/.exec(s))){const want=LMV_WD.indexOf(m[1]),cur=lmvWeekday(today);let back=(cur-want+7)%7;if(back===0)back=7;return lmvDayScope(lmvAddDays(today,-back),today)}
 if((m=/\b(?:(next|this|on)\s+)?(sunday|monday|tuesday|wednesday|thursday|friday|saturday)\b/.exec(s))){
  const want=LMV_WD.indexOf(m[2]),cur=lmvWeekday(today);let up=(want-cur+7)%7;
  if((past||preferPast)&&m[1]!=='next'){const back=(cur-want+7)%7;return lmvDayScope(lmvAddDays(today,-back),today)}
  if(m[1]==='next'&&up===0)up=7;return lmvDayScope(lmvAddDays(today,up),today);
 }
 if((m=/\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b/.exec(s))){
  const mo=LMV_MON.indexOf(m[1])+1,yy=+today.slice(0,4);let c=yy+'-'+lmvPad(mo)+'-'+lmvPad(+m[2]);
  if(past&&c>today)c=(yy-1)+'-'+lmvPad(mo)+'-'+lmvPad(+m[2]);else if(!past&&c<today&&false)c=(yy+1)+'-'+lmvPad(mo)+'-'+lmvPad(+m[2]);
  return lmvDayScope(c,today);
 }
 if((m=/\b(\d{1,2})\/(\d{1,2})\b/.exec(s))&&+m[1]>=1&&+m[1]<=12&&+m[2]>=1&&+m[2]<=31){const yy=+today.slice(0,4);let c=yy+'-'+lmvPad(+m[1])+'-'+lmvPad(+m[2]);if(c>today&&(past||preferPast))c=(yy-1)+'-'+lmvPad(+m[1])+'-'+lmvPad(+m[2]);return lmvDayScope(c,today)}
 return Object.assign(lmvDayScope(today,today,'today'),{explicit:false});
}
function lmvDays(scope){const out=[];if(scope.kind==='all')return out;for(let d=scope.start;d<=scope.end&&out.length<400;d=lmvAddDays(d,1))out.push(d);return out}
function lmvActsOn(date){return (state.activities||[]).filter(function(a){return a&&a.date&&activityDate(a)===date})}
function lmvActsIn(scope){
 if(scope.kind==='all')return (state.activities||[]).slice();
 const d0=scope.start,d1=scope.end;return (state.activities||[]).filter(function(a){if(!a||!a.date)return false;const k=activityDate(a);return k>=d0&&k<=d1})
}
function lmvXpOf(list){return list.reduce(function(s,a){return s+Math.max(0,Number(a.xp)||0)},0)}

/* ===== follow-up memory: "what about yesterday?" re-asks the last question with a new day ===== */
let lmvLastQ=null;
const LMV_FOLLOW=/^(?:and |but |so )?(?:(?:what|how) about|and what about|what of|and for|and on|and)\s+(.+?)\s*\??$/;
function lmvFollowUp(clean,ctx){
 if(!lmvLastQ||Date.now()-lmvLastQ.ts>90000)return null;
 const p=lmvPhon(clean),m=LMV_FOLLOW.exec(p),bare=p.replace(/\?$/,'');
 const sc=lmvScope(p,ctx);
 /* a bare time phrase ("yesterday", "this week") or "what about <time>" */
 const timeOnly=function(t){return /^(?:the |for |on |in )?(?:today|yesterday|tomorrow|tonight|last night|this week|last week|this month|last month|all time|overall|in total|the day before yesterday|\d+ days ago|(?:the )?(?:past|last) \d+ days|(?:last |this |next |on )?(?:sunday|monday|tuesday|wednesday|thursday|friday|saturday)|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.? \d{1,2}(?:st|nd|rd|th)?)$/.test(t.trim())};
 const rest=m?m[1]:bare;
 if((m||bare.length<=28)&&timeOnly(rest)&&sc.explicit){
  const cls=Object.assign({},lmvLastQ.cls,{scope:sc});
  return {cls:cls,text:lmvLastQ.text};
 }
 /* a different metric, same day: "and calories?" "what about xp?" */
 if(m){
  const c2=lmvClassify('what is my '+rest);
  if(c2.id!=='life_director.create_activity'&&c2.id!=='unknown_query'&&/^(rpg|health_fitness|nutrition|virtual_pets|life_director\.(query|find))/.test(c2.id)){
   const own=lmvScope(rest,ctx);
   return {cls:Object.assign({},c2,{scope:own.explicit?own:lmvLastQ.scope||null}),text:'what is my '+rest};
  }
 }
 return null;
}

/* ===== logging something that already happened ===== */
const LMV_TYPES=[
 [/\b(run|ran|running|jog|jogged|jogging|cardio|treadmill|cycle|cycled|cycling|bike|biked|swim|swam|swimming|hike|hiked|hiking|walk|walked|walking|elliptical|rowing)\b/,'running','Run / cardio'],
 [/\b(weights?|lift|lifted|lifting|gym|strength|workout|worked out|push[\s-]?ups?|pull[\s-]?ups?|squats?|bench|exercise|exercised|trained|training|crossfit)\b/,'strength','Strength workout'],
 [/\b(read|reading|study|studied|studying|homework|course)\b/,'reading','Focused reading / study'],
 [/\b(spanish|french|german|italian|japanese|language|duolingo)\b/,'language','Language practice'],
 [/\b(yoga|stretch|stretched|stretching|meditat\w*|nap|napped|sleep|recovery|mobility)\b/,'recovery','Sleep / recovery routine'],
 [/\b(business|client|invoice|proposal|emails?|deep work)\b/,'business','Focused business work'],
 [/\b(cook|cooked|clean|cleaned|repair|fixed|garden|gardening|woodwork|practice|practiced|laundry|chores?)\b/,'practical','Practical skill practice']
];
function lmvActType(s){for(let i=0;i<LMV_TYPES.length;i++)if(LMV_TYPES[i][0].test(s))return {type:LMV_TYPES[i][1],name:LMV_TYPES[i][2],noun:(LMV_TYPES[i][0].exec(s)||[''])[0]};return {type:'custom',name:'Custom activity',noun:''}}
function lmvLogXp(type,dur,day,peek){
 const r=(typeof rules!=='undefined'&&rules[type])||rules.custom,total=Math.max(0,Math.round(r.base*ecoDur(dur)));
 return ecoAward(type,total,dur,{manual:true,day:day,peek:!!peek});
}
/* same record the Log activity screen makes, but for a chosen day and time */
function lmvLogNow(o){
 const r=rules[o.type]||rules.custom,ec=lmvLogXp(o.type,o.duration,o.day,false);
 if(ec.xp<=0)return {ok:false,msg:ec.msg||'No XP for that one'};
 const p=r.primary,s=r.secondary;let pXP=ec.xp,sXP=0;if(s&&s!==p){pXP=Math.round(ec.xp*.7);sXP=ec.xp-pXP}
 pXP=ecoStarXP(p,pXP);if(s&&s!==p)sXP=ecoStarXP(s,sXP);const xp=pXP+sXP;
 const act={id:crypto.randomUUID?crypto.randomUUID():String(Date.now()),type:o.type,name:o.name,icon:r.icon,duration:o.duration,quality:1,skill:r.skill,primary:p,secondary:s,xp:xp,date:new Date(o.atMs).toISOString(),notes:'Logged by voice',xpSplit:[{attr:p,xp:pXP}].concat(s&&s!==p?[{attr:s,xp:sXP}]:[]),src:'voice'};
 state.activities.push(act);act.xpSplit.forEach(function(z){state.attrXP[z.attr]=(state.attrXP[z.attr]||0)+z.xp});state.skillXP[r.skill]=(state.skillXP[r.skill]||0)+xp;
 try{save()}catch(e){}try{toast('Activity saved · +'+xp+' XP')}catch(e){}
 try{if(document.getElementById('dailyQuests'))renderStats()}catch(e){}
 return {ok:true,xp:xp,id:act.id};
}
function lmvHLog(f,text,ctx,scope){
 const s=lmvPhon(text),today=lmvToday(ctx);
 if(/\bsteps?\b/.test(s)&&!/\b(min|minutes|hours?|hrs?)\b/.test(s))return lmvLines(f,'Activity','Steps come from Samsung Health',['I cannot add a step count by hand. To record the walk itself, say something like “log a 30 minute walk”.'],[lmvOpenChip('dashboard','Overview')]);
 if(scope.explicit&&scope.kind!=='day')return lmvLines(f,'Activity','Which day?',['Say one day, like “yesterday” or “this morning”.'],[]);
 if(scope.isFuture)return lmvLines(f,'Activity','That has not happened yet',['I only log things that already happened. To plan it, say “schedule …”.'],[]);
 const ex=lmvExtract(text.replace(/\b(earlier today|this morning|last night|yesterday|today)\b/gi,' '),ctx,null),ty=lmvActType(s);
 let dur=ex.dur,defaulted=false;if(!dur||dur<5){dur=30;defaulted=true}
 if(dur>480)return lmvLines(f,'Activity','That is a very long session',['The most I log at once is 8 hours. Say a shorter length.'],[]);
 const date=scope.explicit?scope.date:today;
 /* when: end of the activity is "now" for today, otherwise a plausible time of day */
 const nowMs=ctx.now;let at;
 const dp=scope.dayPart||(scope.label==='last night'?'night':null);
 const hourFor={morning:9,afternoon:14,evening:19,night:21};
 if(date===today&&!dp)at=nowMs-(scope.earlier?Math.min(dur,90)*60000:0);
 else{const h=dp?hourFor[dp]:(date===today?null:12);const q=date.split('-').map(Number),h0=h==null?lmvParts(nowMs,ctx.tz).h:h;at=lmvLocalToEpoch(q[0],q[1],q[2],h0,0,ctx.tz).epochMs;if(date===today&&at>nowMs)at=nowMs-60000}
 if(at>nowMs+60000)at=nowMs-60000;
 const name=ty.type==='custom'?(ty.noun?ty.noun.charAt(0).toUpperCase()+ty.noun.slice(1):'Activity'):(ty.noun&&/^(walk|walked|walking|yoga|swim|swam|swimming|hike|hiked|hiking|cycle|cycled|cycling|bike|biked|jog|jogged|jogging)$/.test(ty.noun)?{walk:'Walk',walked:'Walk',walking:'Walk',yoga:'Yoga',swim:'Swim',swam:'Swim',swimming:'Swim',hike:'Hike',hiked:'Hike',hiking:'Hike',cycle:'Cycle',cycled:'Cycle',cycling:'Cycle',bike:'Bike ride',biked:'Bike ride',jog:'Jog',jogged:'Jog',jogging:'Jog'}[ty.noun]:ty.name);
 const peek=lmvLogXp(ty.type,dur,date,true);
 const when=(date===today?'Today':lmvNice(date))+' · '+lmvClock(at,ctx.tz);
 return lmvActFrame(f,'Confirm','Log “'+name+'”?',[when,lmvDurText(dur)+(defaulted?' (no length given, so a standard 30 minutes)':''),peek.xp>0?'About +'+peek.xp+' XP':peek.msg,'This is an activity that already happened, so it counts toward that day.'],'Log it',function(){
  const res=lmvLogNow({type:ty.type,name:name,duration:dur,day:date,atMs:at});
  return res.ok?lmvLines(f,'Done','Logged “'+name+'”',['+'+res.xp+' XP · '+(date===today?'today':lmvNice(date))],[lmvOpenChip('dashboard','Overview')]):lmvLines(f,'Voice','Could not log it',[res.msg],[]);
 });
}
