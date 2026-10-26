/* ---------- Journal coach: the Journal (Library tab) reads your own data and says something useful about it.
   Offline and deterministic: every sentence comes from a rule and a template, chosen by the date so it never flickers.
   Designed by Gemini (RULES: day archetypes, reflection prompts, 9 patterns, welcome back, weekly and monthly review),
   adapted to the data this app really has. Coach rewards use the existing economy (Sparks follow XP) and are marked
   type 'coach', so they never count as a training day or a streak day. Never mentions weight, food or calories. ---------- */
const JC={baseDefault:150,baseReset:120,minPatternDays:7,cooldownDays:7,reflectMin:40,weeklyMin:50,monthlyMin:60,rewardReflect:15,rewardWeekly:30,rewardMonthly:50,dialMinSched:120};
let jcCache={sig:'',idx:null,day:{}};
function jcStore(){
 if(typeof state==='undefined')return {days:{},reviews:{},cooldown:{},pats:{},welcome:{},base:null};
 const s=state.journalCoach||(state.journalCoach={});
 ['days','reviews','cooldown','pats','welcome'].forEach(function(k){if(!s[k])s[k]={}});
 return s;
}
/* ----- small utilities ----- */
function jcAdd(ymd,n){const a=ymd.split('-').map(Number),t=new Date(Date.UTC(a[0],a[1]-1,a[2]+n));return t.getUTCFullYear()+'-'+String(t.getUTCMonth()+1).padStart(2,'0')+'-'+String(t.getUTCDate()).padStart(2,'0')}
function jcDow(ymd){const a=ymd.split('-').map(Number);return new Date(Date.UTC(a[0],a[1]-1,a[2])).getUTCDay()}
function jcHash(s){let h=2166136261;for(let i=0;i<s.length;i++){h^=s.charCodeAt(i);h=Math.imul(h,16777619)>>>0}return h}
function jcFill(t,v){return t.replace(/\{(\w+)\}/g,function(m,k){return v[k]!=null?v[k]:''})}
function jcEsc(s){return typeof esc==='function'?esc(s):String(s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]})}
function jcMean(a){return a.length?a.reduce(function(x,y){return x+y},0)/a.length:0}
function jcCat(){try{const p=typeof petData==='function'?petData():null;return p&&p.alive?p:null}catch(e){return null}}
function jcIndex(){
 const acts=(state.activities||[]),sig=acts.length+'|'+getToday();
 if(jcCache.sig!==sig){jcCache={sig:sig,idx:{},day:{}};acts.forEach(function(a){if(!a||!a.date)return;const d=activityDate(a);(jcCache.idx[d]||(jcCache.idx[d]=[])).push(a)})}
 return jcCache.idx;
}
const jcCoachAct=function(a){return a&&a.type==='coach'};
const jcPassive=function(a){try{return ecoPassive(a)}catch(e){return false}};
/* a day's own numbers */
function jcBaseOn(d){const b=jcStore().base;return b&&d>=b.from&&d<=b.until?b.value:null}
function jcDay(d){
 if(jcCache.day[d])return jcCache.day[d];
 const idx=jcIndex(),all=(idx[d]||[]).filter(function(a){return !jcCoachAct(a)}),eff=all.filter(function(a){return !jcPassive(a)});
 const xp=all.reduce(function(s,a){return s+Math.max(0,Number(a.xp)||0)},0);
 const cat={};all.forEach(function(a){const k=a.primary||'discipline';cat[k]=(cat[k]||0)+Math.max(0,Number(a.xp)||0)});
 const top=Object.keys(cat).sort(function(a,b){return cat[b]-cat[a]})[0]||null;
 const topName=top?((ATTRS.find(function(x){return x.id===top})||{}).short||top):'your main skill';
 /* the prior 14 days: average XP on days you were active */
 const prior=[];for(let i=1;i<=14;i++){const k=jcAdd(d,-i),a=(idx[k]||[]).filter(function(x){return !jcCoachAct(x)&&!jcPassive(x)});if(a.length)prior.push((idx[k]||[]).filter(function(x){return !jcCoachAct(x)}).reduce(function(s,x){return s+Math.max(0,Number(x.xp)||0)},0))}
 const reset=jcBaseOn(d);
 const avg=reset!=null?reset:prior.length>=3?Math.round(jcMean(prior)):JC.baseDefault;
 let dial=null,sched=0;
 try{const st=window.LifeDirector&&LifeDirector.coach&&LifeDirector.coach.dayStats(d);if(st&&st.schedMin>0){sched=st.schedMin;dial=Math.round(st.doneMin/st.schedMin*100)}}catch(e){}
 const quests=all.filter(function(a){return a.type==='quest'}).length;
 const p=jcCat();let care=null;
 if(p){const m=(p.meals||[]).filter(function(t){return new Date(t).toLocaleDateString('en-CA')===d});care=m.length||(d===getToday()&&(p.fedToday>0||p.petsToday>0))?1:((p.meals||[]).length>=30&&d<new Date((p.meals||[])[0]).toLocaleDateString('en-CA')?null:0)}
 const rest=!!(state.restDays&&state.restDays[d]);
 const out={d:d,xp:xp,n:eff.length,nAll:all.length,ref:!!((state.dayReflections||{})[d]||'').trim(),R:avg?xp/avg:0,avg:avg,dial:dial,sched:sched,quests:quests,care:care,top:top,topName:topName,cat:cat,rest:rest,hasCat:!!p,catName:p?p.name:''};
 jcCache.day[d]=out;return out;
}
/* consecutive empty days (no effort logged, no reflection) just before d */
function jcGap(d){let g=0;for(let i=1;i<400;i++){const k=jcAdd(d,-i),m=jcDay(k);if(m.n>0||m.ref)break;g++}
 /* only a gap if there was ever something before it */
 const first=(state.activities||[]).reduce(function(m,a){const k=activityDate(a);return !m||k<m?k:m},null);
 return first&&jcAdd(d,-g-1)>=first?g:0}
/* ----- archetypes (priority order) ----- */
const JC_ARCH={
 WELCOME_BACK:{label:'Chapter Resumed',color:'#F59E0B'},OVERDRIVE:{label:'High Output',color:'#EF4444'},DIRECTOR_SYNC:{label:'Dial Master',color:'#3B82F6'},
 PIVOT_DAY:{label:'Off-Script Win',color:'#8B5CF6'},QUEST_HUNTER:{label:'Quest Clear',color:'#10B981'},BALANCED_STRIDE:{label:'Steady Stride',color:'#14B8A6'},
 REST_DAY:{label:'Rest Day',color:'#38BDF8'},COMPANION_ANCHOR:{label:'Quiet Anchor',color:'#EC4899'},LOW_POWER:{label:'Low Power Mode',color:'#64748B'}
};
function jcArch(d){
 const m=jcDay(d),today=getToday();
 if(!m.nAll&&!m.ref)return null;
 if(d===today&&jcGap(d)>=3)return 'WELCOME_BACK';
 if(m.R>=1.5&&m.xp>=250)return 'OVERDRIVE';
 if(m.dial!=null&&m.dial>=80&&m.sched>=JC.dialMinSched)return 'DIRECTOR_SYNC';
 if(m.dial!=null&&m.dial<40&&m.sched>=JC.dialMinSched&&m.R>=.85)return 'PIVOT_DAY';
 if(m.quests>=3&&m.R>=1)return 'QUEST_HUNTER';
 if(m.rest)return 'REST_DAY';
 if(m.R<.4&&m.care===1)return 'COMPANION_ANCHOR';
 if(m.R<.75)return 'LOW_POWER';
 return 'BALANCED_STRIDE';
}
const JC_TXT={
 OVERDRIVE:['You banked {xp} XP today, {pct}% above your 2-week average ({avg} XP). Massive push, mainly in {topCat}. Protect your recovery window tonight.','Heavy hitter day: {xp} XP logged. When you run this hot, tomorrow\'s biggest risk is friction at start-up. Set a tiny first step for tomorrow.','You cleared {xp} XP, a surge of {pct}% over your baseline. Enjoy the win without raising tomorrow\'s minimum bar.'],
 DIRECTOR_SYNC:['You hit {pct}% of your Life Director dial today ({xp} XP). Your time estimate and your actual energy were locked in.','High dial alignment ({pct}%). The plan you drew on the 24-hour wheel actually held up against reality today.','You followed {pct}% of your scheduled blocks and banked {xp} XP. Worth noting what made the schedule realistic today.'],
 PIVOT_DAY:['Only {pct}% of your Life Director dial happened as planned, but you still earned {xp} XP. You threw out the script and got things done anyway.','The 24-hour dial took a detour ({pct}% alignment), yet your output stayed strong ({xp} XP in {topCat}). Adaptability counts as a skill.','Your schedule broke, your momentum did not. {xp} XP logged off-dial. Consider adjusting tomorrow\'s wheel to match how you actually worked today.'],
 QUEST_HUNTER:['{quests} quests cleared and {xp} XP banked. Checking off concrete targets drove most of your momentum today.','You leaned into quest objectives today ({quests} completed, {xp} XP). Clear finish lines worked well for your focus.','Quest-heavy day: {quests} knocked out for {xp} XP. You traded open-ended grinding for clean completions.'],
 BALANCED_STRIDE:['{xp} XP across the board and {catName} is taken care of. No extreme spikes, just the kind of repeatable day that builds levels.','Solid baseline day: {xp} XP (near your {avg} XP average) plus check-ins with {catName}. Sustainable pace.','You kept the gears turning with {xp} XP and did not drop the small stuff with {catName}. Consistency beats heroics.'],
 BALANCED_PLAIN:['{xp} XP across the board. No extreme spikes, just the kind of repeatable day that builds levels.','Solid baseline day: {xp} XP, near your {avg} XP average. Sustainable pace.','You kept the gears turning with {xp} XP. Consistency beats heroics.'],
 REST_DAY:['You marked today as a rest day. Resting is part of the plan, not a gap in it.','A planned rest day ({xp} XP anyway). Recovery is what makes the next push possible.','Rest day logged. Nothing to make up for.'],
 COMPANION_ANCHOR:['Quiet day on the XP front ({xp} XP), but you still showed up for {catName}. Keeping the thread alive on low-energy days is how streaks survive.','Output was light ({xp} XP), yet you checked in on {catName}. Never underestimate a keep-the-pilot-light-on day.','You took your foot off the gas ({xp} XP) but kept your anchor habit with {catName}. Rest counts when it is intentional.'],
 LOW_POWER:['A quiet {xp} XP day. Every RPG has campfires between dungeons. Use tonight to reset, not to guilt-trip.','Low-output log ({xp} XP). Whether today was rest, friction, or just life happening off-screen, the slate resets on the next dial.','Light footprint today ({xp} XP). Data is just a mirror, not a judge. Leave a quick note below if you want to remember what pulled your focus.'],
 WELCOME_BACK:['Chapter resumed. Welcome back.','You are back. That is the only thing that matters today.','A new page. Start wherever it feels light.']
};
function jcInsight(d){
 const a=jcArch(d);if(!a)return null;
 const m=jcDay(d),v={xp:m.xp,pct:a==='OVERDRIVE'?Math.round((m.R-1)*100):m.dial!=null?m.dial:0,quests:m.quests,avg:m.avg,catName:m.catName,topCat:m.topName};
 const key=a==='BALANCED_STRIDE'&&!(m.hasCat&&m.care===1)?'BALANCED_PLAIN':a;
 const list=JC_TXT[key],t=list[jcHash(d)%list.length];
 return {arch:a,label:JC_ARCH[a].label,color:JC_ARCH[a].color,text:jcFill(t,v)};
}
/* ----- reflection prompts ----- */
const JC_POOLS={
 A:[['PRM_A1','You pushed hard today. What is one thing you can take off tomorrow\'s plate so you do not rubber-band into burnout?'],['PRM_A2','Did today\'s high XP come from genuine flow, or were you running on stress and urgency?'],['PRM_A3','Which completed task today actually moved your life forward, and which was just busywork?'],['PRM_A4','What fueled your focus during your highest-XP block today?']],
 B:[['PRM_B1','Looking at the 24-hour dial, which block was placed at the exact right time for your brain?'],['PRM_B2','Where did your schedule collide with reality today, and was the expectation or the environment to blame?'],['PRM_B3','If you re-ran today\'s 24-hour wheel, which block would you shrink by 30 minutes?'],['PRM_B4','What happened in the gaps between your scheduled blocks today?']],
 C:[['PRM_C1','Which activity today felt easiest to start once you got past the first two minutes?'],['PRM_C2','What is one small win from today that did not show up as XP?'],['PRM_C3','Did you spend your best hours on your highest-priority skill today?'],['PRM_C4','What single habit anchored the rest of your routine today?']],
 D:[['PRM_D1','Was today a deliberate rest day, or did you get stuck behind a specific wall of friction?'],['PRM_D2','What drained your battery before you even had a chance to log an activity?'],['PRM_D3','What is the smallest, 5-minute win that would make tomorrow feel like a step forward?'],['PRM_D4','If a good friend had the exact day you just had, what would you tell them tonight?']],
 E:[['PRM_E1','What was life like while you were away from the app: busy with good things, overwhelmed, or just needing a break from tracking?'],['PRM_E2','Forget catching up: what is the one anchor habit you want back on your dial today?'],['PRM_E3','What felt heaviest over the last few days, and can you drop it for the next 24 hours?'],['PRM_E4','What made you open LifeMax again today?']]
};
const JC_POOL_OF={OVERDRIVE:'A',DIRECTOR_SYNC:'B',PIVOT_DAY:'B',QUEST_HUNTER:'C',BALANCED_STRIDE:'C',COMPANION_ANCHOR:'D',LOW_POWER:'D',REST_DAY:'D',WELCOME_BACK:'E'};
function jcPrompt(d){
 const a=jcArch(d);if(!a)return null;
 const pool=JC_POOLS[JC_POOL_OF[a]],st=jcStore(),rec=st.days[d]||(st.days[d]={});
 let id=rec.promptId;
 if(!id||!pool.some(function(q){return q[0]===id}))id=pool[jcHash(d+'q')%pool.length][0];
 return {id:id,text:(pool.filter(function(q){return q[0]===id})[0]||pool[0])[1],pool:pool};
}
function jcSwap(d){
 const p=jcPrompt(d);if(!p)return;const i=p.pool.findIndex(function(q){return q[0]===p.id});
 jcStore().days[d].promptId=p.pool[(i+1)%p.pool.length][0];try{save()}catch(e){}renderHistory();
}
/* ----- rewards (once each; through the normal economy, never a streak or training day) ----- */
function jcAward(xp,name,note){
 const act={id:(crypto.randomUUID?crypto.randomUUID():String(Date.now())+Math.random()),type:'coach',name:name,icon:'book',duration:0,quality:1,skill:'Reflection',primary:'wellbeing',secondary:'',xp:xp,
  date:new Date().toISOString(),notes:note||'',xpSplit:[{attr:'wellbeing',xp:xp}],src:'coach'};
 state.activities.push(act);state.attrXP.wellbeing=(state.attrXP.wellbeing||0)+xp;state.skillXP.Reflection=(state.skillXP.Reflection||0)+xp;
 jcCache.sig='';return xp;
}
function jcCare(n){try{const p=jcCat();if(p&&typeof petGain==='function')petGain(p,n)}catch(e){}}
function jcOnReflect(d,text){
 const st=jcStore(),rec=st.days[d]||(st.days[d]={}),t=String(text||'').trim(),y=jcAdd(getToday(),-1);
 if(rec.xpAwarded||t.length<JC.reflectMin||(d!==getToday()&&d!==y))return;
 rec.xpAwarded=true;jcAward(JC.rewardReflect,'Journal reflection');jcCare(2);
 try{save()}catch(e){}
 try{toast('+'+JC.rewardReflect+' XP · +2 bond · Reflection saved')}catch(e){}
}
function jcCounter(el,d){
 const n=el.value.trim().length,rec=jcStore().days[d]||{},b=document.getElementById('jcCount');if(!b)return;
 b.textContent=rec.xpAwarded?'+'+JC.rewardReflect+' XP & +2 bond claimed':(d===getToday()||d===jcAdd(getToday(),-1))?(n>=JC.reflectMin?'Saves for +'+JC.rewardReflect+' XP':'Write '+JC.reflectMin+'+ characters for +'+JC.rewardReflect+' XP ('+n+')'):'Reflections on older days earn no XP';
 b.className='jc-count'+(rec.xpAwarded||n>=JC.reflectMin?' on':'');
}
/* ----- patterns over the last 21 days (one chip a day, a pattern rests 7 days after it is shown) ----- */
function jcPatterns(d){
 const idx=jcIndex(),days=[];for(let i=20;i>=0;i--){const k=jcAdd(d,-i);days.push(jcDay(k))}
 const act=days.filter(function(m){return m.n>0});
 if(act.length<JC.minPatternDays)return [];
 const last14=days.slice(7),act14=last14.filter(function(m){return m.n>0}),avg14=jcMean(act14.map(function(m){return m.xp}))||1;
 const out=[],catName=(jcCat()||{}).name||'your cat';
 const fmtH=function(h){const x=((h%24)+24)%24;return (x%12||12)+(x<12?' AM':' PM')};
 /* chronotype */
 const W=[['Morning',5,11],['Afternoon',11,17],['Evening',17,23],['Night shift',23,29]],tot={},all=[];
 last14.forEach(function(m){(idx[m.d]||[]).filter(function(a){return !jcCoachAct(a)&&!jcPassive(a)}).forEach(function(a){const h=new Date(a.date).getHours(),hh=h<5?h+24:h;all.push([hh,Math.max(0,Number(a.xp)||0)])})});
 const totalXP=all.reduce(function(s,x){return s+x[1]},0);
 if(totalXP>0)for(let i=0;i<W.length;i++){const s=all.filter(function(x){return x[0]>=W[i][1]&&x[0]<W[i][2]}).reduce(function(q,x){return q+x[1]},0);if(s/totalXP>=.6)out.push({id:'PAT_CHRONO_PEAK',text:'Pattern noticed: '+Math.round(s/totalXP*100)+'% of your XP over the last 2 weeks was logged between '+fmtH(W[i][1])+' and '+fmtH(W[i][2])+'. Put your hardest block there.'})}
 /* dial optimism */
 const dd=last14.filter(function(m){return m.sched>0});
 if(dd.filter(function(m){return m.sched>=480}).length>=5&&jcMean(dd.filter(function(m){return m.dial!=null}).map(function(m){return m.dial}))<50)out.push({id:'PAT_DIAL_OPTIMISM',text:'Pattern noticed: You are scheduling about '+Math.round(jcMean(dd.map(function(m){return m.sched}))/60)+'h a day on the Director dial but hitting '+Math.round(jcMean(dd.map(function(m){return m.dial||0})))+'%. Try cutting 2 blocks so the wheel feels winnable.'});
 /* boom and bust */
 let bb=0;for(let i=7;i<20;i++){const a=days[i],b=days[i+1];if(a.n&&a.R>=1.6&&b&&b.R<=.35&&b.d<=d)bb++}
 if(bb>=2)out.push({id:'PAT_BOOM_BUST',text:'Pattern noticed: Your 1.6x+ XP surge days are usually followed by a crash day. Try capping big days a little earlier to protect tomorrow.'});
 /* keystone habit */
 const byName={};days.forEach(function(m){const seen={};(idx[m.d]||[]).filter(function(a){return !jcCoachAct(a)&&!jcPassive(a)&&a.type!=='quest'}).forEach(function(a){const n=a.name;if(n&&!seen[n]){seen[n]=1;(byName[n]||(byName[n]=[])).push(m.d)}})});
 Object.keys(byName).forEach(function(n){const ds=byName[n];if(ds.length<5)return;const w=act.filter(function(m){return ds.indexOf(m.d)>=0}),wo=act.filter(function(m){return ds.indexOf(m.d)<0});if(wo.length<2)return;const a=jcMean(w.map(function(m){return m.xp})),b=jcMean(wo.map(function(m){return m.xp}));if(b>0&&a>=1.4*b)out.push({id:'PAT_KEYSTONE_HABIT:'+n,key:'PAT_KEYSTONE_HABIT',text:'Keystone habit found: On days you log "'+n+'", your total daily XP averages '+Math.round(a)+' vs. '+Math.round(b)+' when you skip it.'})});
 /* weekday dip */
 const names=['Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'];
 for(let w=0;w<7;w++){const ds=days.filter(function(m){return jcDow(m.d)===w&&m.d<d});if(ds.length>=3){const lastThree=ds.slice(-3);if(lastThree.every(function(m){return m.xp<.5*avg14}))out.push({id:'PAT_WEEKDAY_DRIFT:'+w,key:'PAT_WEEKDAY_DRIFT',text:'Pattern noticed: '+names[w]+'s have been your lowest-energy day 3 weeks running ('+Math.round(jcMean(lastThree.map(function(m){return m.xp})))+' XP avg). Consider making '+names[w]+' a planned light day.'})}}
 /* single-stat tunnel */
 const c10={};let t10=0;days.slice(-14).filter(function(m){return m.n>0}).slice(-10).forEach(function(m){Object.keys(m.cat).forEach(function(k){c10[k]=(c10[k]||0)+m.cat[k];t10+=m.cat[k]})});
 const top=Object.keys(c10).sort(function(a,b){return c10[b]-c10[a]})[0];
 if(top&&t10>0&&c10[top]/t10>=.75){const zero=ATTRS.filter(function(x){return !(c10[x.id]>0)});if(zero.length>=2)out.push({id:'PAT_MONO_GRIND',text:'Pattern noticed: '+Math.round(c10[top]/t10*100)+'% of your recent XP is in '+((ATTRS.find(function(x){return x.id===top})||{}).short||top)+'. Great specialization, but your '+zero[0].short+' has been collecting dust for 10 days.'})}
 /* minimum-viable days */
 const micro=last14.filter(function(m){return m.n>0&&m.xp>0&&m.xp<50}).length;
 if(micro>=3)out.push({id:'PAT_STREAK_SHIELD',text:'Pattern noticed: You had '+micro+' minimum-viable days (under 50 XP) in the last 2 weeks that kept your momentum unbroken. Small days work.'});
 /* journal lift */
 const after=[],afterNo=[];for(let i=1;i<days.length;i++){const m=days[i],pv=days[i-1];if(m.dial==null)continue;(pv.ref&&((state.dayReflections||{})[pv.d]||'').trim().length>=JC.reflectMin?after:afterNo).push(m.dial)}
 if(after.length>=4&&afterNo.length>=4){const a=jcMean(after),b=jcMean(afterNo);if(b>0&&a>=1.2*b)out.push({id:'PAT_REFLECTION_LIFT',text:'Pattern noticed: On days after you write a Journal reflection, your Life Director alignment is '+Math.round((a/b-1)*100)+'% higher.'})}
 /* cat care early */
 const p=jcCat();
 if(p&&(p.meals||[]).length>=8){
  const early=[],late=[];days.forEach(function(m){if(!m.n)return;const ts=(p.meals||[]).filter(function(t){return new Date(t).toLocaleDateString('en-CA')===m.d});if(!ts.length)return;(new Date(Math.min.apply(null,ts)).getHours()<12?early:late).push(m.xp)});
  if(early.length>=4&&late.length>=4){const a=jcMean(early),b=jcMean(late);if(b>0&&a>=1.25*b)out.push({id:'PAT_CAT_SYNERGY',text:'Pattern noticed: Days you feed '+catName+' before noon average '+Math.round((a/b-1)*100)+'% more XP than days you wait until later.'})}
 }
 return out;
}
function jcChip(d){
 const st=jcStore();
 if(st.pats[d]!==undefined)return st.pats[d]||null;
 const today=getToday();
 if(d>today||d<jcAdd(today,-3)){return null}      /* only recent days get a chip, so history never rewrites itself */
 const list=jcPatterns(d);let pick=null;
 for(let i=0;i<list.length&&!pick;i++){const p=list[i],k=p.key||p.id,last=st.cooldown[p.id];if(!last||Math.abs((Date.parse(d+'T12:00:00')-Date.parse(last+'T12:00:00'))/86400000)>=JC.cooldownDays)pick=p}
 if(pick){st.cooldown[pick.id]=d;st.pats[d]={id:pick.id,text:pick.text}}else st.pats[d]=0;
 try{save()}catch(e){}
 return st.pats[d]||null;
}
/* ----- care note: only ever words from the person, never data about weight or food ----- */
const JC_DISTRESS=/\b(kill myself|suicid\w*|want to die|end my life|self[- ]?harm|hurt myself|no reason to live|better off dead|can'?t go on)\b/i;
function jcCareNote(d){const r=((state.dayReflections||{})[d]||'');return JC_DISTRESS.test(r)?'<div class="jc-care"><b>That sounds really heavy.</b> You do not have to carry it alone. If you might hurt yourself, please call or text 988 (in the US) or your local emergency number right now, or reach out to someone you trust. The coach stays quiet today.</div>':''}
/* ----- welcome back ----- */
function jcWelcome(d){
 if(d!==getToday())return '';
 const g=jcGap(d);if(g<3)return '';
 const st=jcStore(),key=d,w=st.welcome[key]||(st.welcome[key]={tier:g>=14?3:g>=7?2:1,gap:g,claimed:{}});
 const cat=jcCat(),cn=cat?cat.name:'';const lv=(function(){try{return characterLevel().level}catch(e){return 1}})();
 if(w.tier===3&&!st.base){st.base={from:d,until:jcAdd(d,7),value:JC.baseReset};try{save()}catch(e){}}
 let head,body,chips='';
 const chip=function(id,label,fn,done){return '<button type="button" class="jc-chip'+(done?' done':'')+'" '+(done?'disabled':'onclick="'+fn+'"')+'>'+jcEsc(label)+'</button>'};
 if(w.tier===1){
  head='Welcome back. Ready when you are.';
  body='You were off-grid for '+g+' days. '+(cat?jcEsc(cn)+' held down the fort. ':'')+'No penalties, no backlog to clear. Today is a clean slate.';
  chips=chip('pet',cat?'Pet '+cn+' · +10 XP':'Claim +10 XP','jcClaim(\'pet\')',w.claimed.pet);
 }else if(w.tier===2){
  head='Good to see you again.';
  body='It has been '+g+' days since your last log. Life happens outside the screen. Do not try to make up for last week, just pick one small block on the Director dial today.';
  chips=chip('line','One-sentence catch-up · +20 XP','jcCatchUp()',w.claimed.line)+'<button type="button" class="jc-chip" onclick="lmaNavTo(\'life-director\');if(window.LifeMaxFeatures)LifeMaxFeatures.open(\'life-director\')">Open Life Director</button>';
 }else{
  head='New chapter unlocked.';
  body='You have been away for '+g+' days. Your level ('+lv+'), Sparks and '+(cat?jcEsc(cn):'everything')+' are right where you left them, and your rolling average is reset for a week so today feels fair.';
  chips=chip('fresh','Start fresh · +25 XP','jcClaim(\'fresh\')',w.claimed.fresh);
 }
 return '<div class="jc-welcome"><div class="jc-wh">'+jcEsc(head)+'</div><p>'+body+'</p><div class="jc-chips">'+chips+'</div></div>';
}
function jcClaim(kind){
 const st=jcStore(),w=st.welcome[getToday()];if(!w||w.claimed[kind])return;
 if(kind==='pet'){try{if(jcCat())petPetDone()}catch(e){}jcAward(10,'Welcome back','Returned after '+w.gap+' days');w.claimed.pet=1;try{toast('+10 XP · Welcome back')}catch(e){}}
 if(kind==='fresh'){jcAward(25,'New chapter','Returned after '+w.gap+' days');w.claimed.fresh=1;try{toast('+25 XP · New chapter')}catch(e){}}
 try{save()}catch(e){}renderHistory();
}
function jcCatchUp(){
 const t=prompt('One sentence: what have you been up to?');if(t==null)return;
 const s=String(t).trim();if(s.length<12){try{toast('Write a full sentence (12+ characters)')}catch(e){}return}
 const st=jcStore(),w=st.welcome[getToday()];if(!w||w.claimed.line)return;
 const d=getToday();state.dayReflections=state.dayReflections||{};state.dayReflections[d]=((state.dayReflections[d]||'')+(state.dayReflections[d]?'\n':'')+s).slice(0,4000);
 jcAward(20,'Catch-up note');w.claimed.line=1;try{save()}catch(e){}try{toast('+20 XP · Catch-up saved')}catch(e){}renderHistory();
}
/* ----- the day view's top: welcome, insight, reflection box ----- */
function jcTop(d){
 const care=jcCareNote(d);
 if(care)return care+jcReflectBox(d,null);
 const ins=jcInsight(d),chip=jcChip(d),w=jcWelcome(d);
 let h=w;
 if(ins)h+='<div class="jc-card" style="--jc:'+ins.color+'"><span class="jc-badge">'+jcEsc(ins.label)+'</span><p>'+jcEsc(ins.text)+'</p>'+(chip?'<div class="jc-pat">'+jcEsc(chip.text)+'</div>':'')+'</div>';
 return h+jcReflectBox(d,jcPrompt(d));
}
function jcReflectBox(d,pr){
 const ref=(state.dayReflections&&state.dayReflections[d])||'',rec=jcStore().days[d]||{};
 return '<div class="j-reflect">'+(pr?'<div class="jc-q"><span>'+jcEsc(pr.text)+'</span><button type="button" class="jc-swap" onclick="jcSwap(\''+d+'\')" aria-label="Swap question">↻</button></div>':'<label for="dayReflection">Reflection <span style="font-weight:400;color:var(--muted2)">(optional)</span></label>')+
  '<textarea id="dayReflection" maxlength="4000" placeholder="How did today feel? What mattered? What will you do differently?" oninput="jcCounter(this,\''+d+'\')" onchange="saveDayReflection(\''+d+'\',this.value)">'+jcEsc(ref)+'</textarea><div id="jcCount" class="jc-count'+(rec.xpAwarded?' on':'')+'">'+(rec.xpAwarded?'+'+JC.rewardReflect+' XP & +2 bond claimed':(d===getToday()||d===jcAdd(getToday(),-1)?'Write '+JC.reflectMin+'+ characters for +'+JC.rewardReflect+' XP':'Reflections on older days earn no XP'))+'</div></div>';
}
/* ----- the day list: coloured dots, quiet stretches, weekly and monthly reviews ----- */
function jcWeekId(d){const a=d.split('-').map(Number),t=new Date(Date.UTC(a[0],a[1]-1,a[2])),dn=(t.getUTCDay()+6)%7;t.setUTCDate(t.getUTCDate()-dn+3);const y=t.getUTCFullYear(),j=new Date(Date.UTC(y,0,4)),w=1+Math.round(((t-j)/86400000-3+((j.getUTCDay()+6)%7))/7);return y+'-W'+String(w).padStart(2,'0')}
function jcWeekStart(d){return jcAdd(d,-((jcDow(d)+6)%7))}
function jcReviewList(days){
 /* days: newest first. Weekly reviews need >=2 active days; monthly >=5 */
 const out=[],today=getToday(),weeks={},months={};
 days.forEach(function(d){const m=jcDay(d);if(m.n>0){const w=jcWeekStart(d);weeks[w]=(weeks[w]||0)+1;const mo=d.slice(0,7);months[mo]=(months[mo]||0)+1}});
 Object.keys(weeks).forEach(function(ws){
  const we=jcAdd(ws,6),done=we<today||(we===today&&new Date().getHours()>=20);
  if(weeks[ws]>=2&&done)out.push({key:'W:'+jcWeekId(ws),sort:we+'~',kind:'W',start:ws,end:we});
 });
 Object.keys(months).forEach(function(mo){
  const a=mo.split('-').map(Number),end=jcAdd(mo+'-01',new Date(Date.UTC(a[0],a[1],0)).getUTCDate()-1),done=end<today||(end===today&&new Date().getHours()>=20);
  if(months[mo]>=5&&done)out.push({key:'M:'+mo,sort:end+'~~',kind:'M',start:mo+'-01',end:end});
 });
 return out.sort(function(a,b){return b.sort.localeCompare(a.sort)}).slice(0,40);
}
function jcListHtml(days,sel){
 const items=days.map(function(d){return {sort:d,day:d}}).concat(jcReviewList(days).map(function(r){return {sort:r.sort,rev:r}}));
 items.sort(function(a,b){return b.sort.localeCompare(a.sort)});
 let html='',prevDay=null;
 items.forEach(function(it){
  if(it.rev){
   const r=it.rev,on=sel===r.key?' on':'';
   html+='<button type="button" class="journal-day-btn jc-rev'+on+'" onclick="selectJournalDay(\''+r.key+'\')">'+(r.kind==='W'?'Week '+r.key.split('-W')[1]+' review':new Date(r.start+'T12:00:00').toLocaleDateString(undefined,{month:'long'})+' chronicle')+'<small>'+(r.kind==='W'?'weekly':'monthly')+'</small></button>';return;
  }
  const d=it.day;
  if(prevDay){const gap=(Date.parse(prevDay+'T12:00:00')-Date.parse(d+'T12:00:00'))/86400000-1;if(gap>=3)html+='<div class="jc-quiet">'+gap+' quiet days ('+jcShort(jcAdd(d,1))+' to '+jcShort(jcAdd(prevDay,-1))+')</div>'}
  prevDay=d;
  const a=jcArch(d),acts=actsForDay(d),hasRef=!!((state.dayReflections||{})[d]||'').trim();
  html+='<button type="button" class="journal-day-btn '+(d===sel?'on':'')+'" onclick="selectJournalDay(\''+d+'\')">'+(a?'<i class="jc-dot" style="background:'+JC_ARCH[a].color+'"></i>':'')+jcEsc(journalDayLabel(d))+'<small>'+acts.length+' logged'+(hasRef?' · reflection':'')+'</small></button>';
 });
 return html;
}
function jcShort(d){return new Date(d+'T12:00:00').toLocaleDateString(undefined,{month:'short',day:'numeric'})}
/* ----- weekly and monthly review cards ----- */
function jcRange(a,b){const out=[];for(let d=a;d<=b;d=jcAdd(d,1))out.push(d);return out}
function jcReviewHtml(key){
 const kind=key[0],id=key.slice(2),st=jcStore(),rec=st.reviews[key]||(st.reviews[key]={});
 let start,end;
 if(kind==='W'){const m=/^(\d+)-W(\d+)$/.exec(id),y=+m[1],w=+m[2],j=new Date(Date.UTC(y,0,4)),mon=new Date(j.getTime()+((w-1)*7-((j.getUTCDay()+6)%7))*86400000);start=jcAdd(mon.toISOString().slice(0,10),0);end=jcAdd(start,6)}
 else{start=id+'-01';const a=id.split('-').map(Number);end=jcAdd(start,new Date(Date.UTC(a[0],a[1],0)).getUTCDate()-1)}
 const ds=jcRange(start,end).map(jcDay),act=ds.filter(function(m){return m.n>0}),total=ds.reduce(function(s,m){return s+m.xp},0),cn=jcCat()?jcCat().name:'';
 const catSum={};ds.forEach(function(m){Object.keys(m.cat).forEach(function(k){catSum[k]=(catSum[k]||0)+m.cat[k]})});
 const topK=Object.keys(catSum).sort(function(a,b){return catSum[b]-catSum[a]})[0],topName=topK?((ATTRS.find(function(x){return x.id===topK})||{}).short||topK):'your main skill',topPct=total?Math.round((catSum[topK]||0)/total*100):0;
 const best=ds.slice().sort(function(a,b){return b.xp-a.xp})[0],dn=function(d){return new Date(d+'T12:00:00').toLocaleDateString(undefined,{weekday:'long'})};
 const dials=ds.filter(function(m){return m.dial!=null});
 const avgDial=dials.length?Math.round(jcMean(dials.map(function(m){return m.dial}))):null,bestDial=dials.slice().sort(function(a,b){return b.dial-a.dial})[0];
 let head,grid,synth,qText,bonus,minLen;
 if(kind==='W'){
  const pw=jcRange(jcAdd(start,-7),jcAdd(start,-1)).map(jcDay),pt=pw.reduce(function(s,m){return s+m.xp},0),delta=pt>0?Math.round((total-pt)/pt*100):null;
  const catDays=jcCat()?ds.filter(function(m){return m.care===1}).length:null;
  head='Week '+id.split('-W')[1]+': '+total.toLocaleString()+' XP across '+act.length+'/7 days';
  grid=[['XP',total.toLocaleString()+' XP'+(delta==null?'':' ('+(delta>=0?'+':'')+delta+'% vs prior week)')],avgDial!=null?['Director sync',avgDial+'% avg alignment'+(bestDial?' ('+dn(bestDial.d)+' was sharpest)':'')]:null,['Top stat',topName+' ('+topPct+'% of output)'],catDays!=null?['Companion',catDays+'/7 days with '+cn]:null].filter(Boolean);
  synth=delta==null?'Your first tracked week. Nothing to compare yet, and that is fine. Notice what felt easy.':delta>=15?'Strong expansion week. You pushed volume up by '+delta+'%, led by '+dn(best.d)+' ('+best.xp+' XP). Watch your Monday start so you do not carry fatigue.':delta>-15?'Rock-steady week. Your output stayed within '+Math.abs(delta)+'% of last week, which is where real habit automaticity forms.':'A lighter week ('+total.toLocaleString()+' XP). Your anchor was '+topName+', which kept you moving even when the schedule got tight.';
  qText='What is ONE adjustment to your 24-hour Director dial that would make next week 10% smoother?';bonus=JC.rewardWeekly;minLen=JC.weeklyMin;
 }else{
  const startTotal=function(upto){let c=0;Object.keys(jcIndex()).forEach(function(k){if(k<upto)(jcIndex()[k]).forEach(function(a){if(!jcCoachAct(a))c+=Math.max(0,Number(a.xp)||0)})});return c};
  let l0=1,l1=1;try{const extra=(state.legacyCharXPBonus||0)+Object.values(state.ascended||{}).reduce(function(a,b){return a+b},0);l0=levelData(startTotal(start)+extra,charXpForLevel).level;l1=levelData(startTotal(jcAdd(end,1))+extra,charXpForLevel).level}catch(e){}
  const q=ds.reduce(function(s,m){return s+m.quests},0),refs=ds.filter(function(m){return m.ref}).length;
  let comebacks=0;ds.forEach(function(m,i){if(m.n>0&&i>0&&jcGap(m.d)>=3)comebacks++});
  const mid=Math.floor(ds.length/2),h1=ds.slice(0,mid).reduce(function(s,m){return s+m.xp},0),h2=ds.slice(mid).reduce(function(s,m){return s+m.xp},0),hd=h1>0?Math.round((h2-h1)/h1*100):null;
  const hours=Math.round(ds.reduce(function(s,m){return s+(m.sched||0)*((m.dial||0)/100)},0)/60);
  const mn=new Date(start+'T12:00:00').toLocaleDateString(undefined,{month:'long'});
  head=mn+' Chronicle · Level '+l0+' to '+l1;
  grid=[['Total XP',total.toLocaleString()],['Levels gained',String(Math.max(0,l1-l0))],['Quests cleared',String(q)],hours?['Hours on the dial',hours+' h']:null,['Reflections written',String(refs)],['Best day',jcShort(best.d)+' ('+best.xp+' XP)'],['Quiet stretches returned from',String(comebacks)]].filter(Boolean);
  synth=hd==null?'A fresh month. Notice what you want more of.':'Second-half momentum was '+(hd>=10?'rising':hd<=-10?'easing':'steady')+' ('+(hd>=0?'+':'')+hd+'% XP vs. the first half of the month). Your strongest stat was '+topName+' ('+topPct+'% of output).';
  qText='If '+mn+' was a chapter in your character\'s story, what was the title of this chapter, and what skill did you actually level up in real life?';bonus=JC.rewardMonthly;minLen=JC.monthlyMin;
 }
 const n=(rec.answer||'').trim().length;
 return '<div class="journal-date">'+jcEsc(head)+'</div><div class="journal-meta">'+jcShort(start)+' to '+jcShort(end)+'</div>'+
  '<div class="jc-grid">'+grid.map(function(g){return '<div><small>'+jcEsc(g[0])+'</small><b>'+jcEsc(g[1])+'</b></div>'}).join('')+'</div>'+
  '<div class="jc-card" style="--jc:#F59E0B"><span class="jc-badge">Coach</span><p>'+jcEsc(synth)+'</p></div>'+
  '<div class="j-reflect"><div class="jc-q"><span>'+jcEsc(qText)+'</span></div><textarea maxlength="2000" placeholder="Write if you want to." onchange="jcSaveReview(\''+key+'\',this.value)">'+jcEsc(rec.answer||'')+'</textarea><div class="jc-count'+(rec.paid?' on':'')+'">'+(rec.paid?'+'+bonus+' XP claimed':'Write '+minLen+'+ characters for +'+bonus+' XP')+'</div></div>';
}
function jcSaveReview(key,val){
 const st=jcStore(),rec=st.reviews[key]||(st.reviews[key]={}),t=String(val||'').trim();rec.answer=t.slice(0,2000);
 const bonus=key[0]==='W'?JC.rewardWeekly:JC.rewardMonthly,min=key[0]==='W'?JC.weeklyMin:JC.monthlyMin;
 if(!rec.paid&&t.length>=min){rec.paid=true;jcAward(bonus,key[0]==='W'?'Weekly review':'Monthly chronicle');try{toast('+'+bonus+' XP · Review saved')}catch(e){}}
 try{save()}catch(e){}renderHistory();
}
window.LifeMaxCoach=Object.freeze({day:jcDay,arch:jcArch,insight:jcInsight,prompt:jcPrompt,patterns:jcPatterns,gap:jcGap,reviewList:jcReviewList,reviewHtml:jcReviewHtml,reflect:jcOnReflect,store:jcStore,hash:jcHash,reset:function(){jcCache={sig:'',idx:null,day:{}}}});
