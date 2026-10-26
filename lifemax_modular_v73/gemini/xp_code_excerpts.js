// LifeMax RPG v104 - the code that decides XP, levels, decay and rewards. Excerpts, long lines trimmed.

// ===== 00-runtime.js lines 2-22: Attributes, activity types (base XP), default quests =====
const ATTRS=[
 {id:'physique',name:'Strength & Physique',short:'Physique',icon:'dumbbell'},
 {id:'endurance',name:'Endurance & Vitality',short:'Endurance',icon:'heart'},
 {id:'intelligence',name:'Intelligence & Knowledge',short:'Intelligence',icon:'book'},
 {id:'business',name:'Wealth & Business',short:'Business',icon:'briefcase'},
 {id:'social',name:'Social & Communication',short:'Social',icon:'chat'},
 {id:'discipline',name:'Discipline & Execution',short:'Discipline',icon:'target'},
 {id:'adventure',name:'Adventure & Practical Skills',short:'Adventure',icon:'mountain'},
 {id:'wellbeing',name:'Wellbeing & Recovery',short:'Wellbeing',icon:'moon'}
];
const DEFAULT_RULES={
 reading:{name:'Focused reading / study',icon:'book',base:20,skill:'Knowledge',primary:'intelligence',secondary:'discipline',desc:'Practice focused learning'},
 business:{name:'Focused business work',icon:'briefcase',base:25,skill:'Entrepreneurship',primary:'business',secondary:'discipline',desc:'Build something valuable'},
 strength:{name:'Strength workout',icon:'dumbbell',base:32,skill:'Lifting',primary:'physique',secondary:'discipline',desc:'Strength and resistance training'},
 running:{name:'Run / cardio',icon:'heart',base:27,skill:'Running',primary:'endurance',secondary:'discipline',desc:'Cardio or running session'},
 language:{name:'Language practice',icon:'chat',base:20,skill:'Spanish',primary:'intelligence',secondary:'social',desc:'Build a language skill'},
 recovery:{name:'Sleep / recovery routine',icon:'moon',base:16,skill:'Recovery Routine',primary:'wellbeing',secondary:'discipline',desc:'Complete a planned recovery routine'},
 practical:{name:'Practical skill practice',icon:'mountain',base:23,skill:'Practical Skills',primary:'adventure',secondary:'intelligence',desc:'Practice a useful hands-on skill'},
 task:{name:'Difficult planned task',icon:'target',base:15,skill:'Execution',primary:'discipline',secondary:'',desc:'Follow through on a meaningful task'},
 custom:{name:'Custom activity',icon:'star',base:20,skill:'Custom Skill',primary:'discipline',secondary:'',desc:'Your own activity'}
};

// ===== 04-runtime.js lines 14-18: Level curve =====
function xpForLevel(level){return 100+25*(level-1)}
function levelData(total){let level=1,left=Math.max(0,total);while(left>=xpForLevel(level)&&level<1000){left-=xpForLevel(level);level++}return {level,into:left,needed:xpForLevel(level),pct:Math.min(100,left/xpForLevel(level)*100)}}
function totalXP(){return Object.values(state.attrXP).reduce((a,b)=>a+b,0)}
function characterLevel(){return levelData(totalXP())}
function attrXP(id){return Number(state.attrXP[id]||0)}

// ===== 04-runtime.js lines 179-182: XP preview, logging an activity (diminishing returns), quest completion =====
function calcPreview(){const id=document.getElementById('activityType').value,r=rules[id]||rules.custom;const dur=Math.max(0,Number(document.getElementById('duration').value)||0);const quality=Number(document.getElementById('quality').value)||1;/* 30 min = 1× base; scales linearly to 3× at 90+ min; short sessions floor at 0.4× */const durFactor=dur<=0?0:Math.min(3,Math.max(0.4,dur/30));let base=r.base*durFactor;let total=Math.max(0,Math.round(base*quality));return {total,primary:document.getElementById('primaryAttr').value||r.primary,secondary:document.getElementById('secondaryAttr').value,skill:(document.getElementById('skillName').value||r.skill).trim()||r.skill,durFactor}}
function updatePreview(){const x=calcPreview();document.getElementById('previewXP').textContent=x.total;document.getElementById('previewBar').style.width=Math.min(100,x.total/120*100)+'%';const p=ATTRS.find(a=>a.id===x.primary);const s=ATTRS.find(a=>a.id===x.secondary);let chunks=[];if(x.secondary&&x.secondary!==x.primary){const pXP=Math.round(x.total*.7),sXP=x.total-pXP;chunks.push(`<span class="preview-chip">${p?px(p.icon):''} ${p?.short||'Primary'} <strong>+${pXP}</strong></span>`,`<span class="preview-chip">${s?px(s.icon):''} ${s?.short||'Secondary'} <strong>+${sXP}</strong></span>`)}else chunks.push(`<span class="preview-chip">${p?px(p.icon):''} ${p?.short||'Attribute'} <strong>+${x.total}</strong></span>`);if(x.durFactor)chunks.push(`<span class="preview-chip">Duration <strong>${x.durFactor.toFixed(2)}×</strong></span>`);chunks.push(`<span class="preview-chip">Skill: <strong>${esc(x.skill||'Skill')}</strong></span>`);document.getElementById('previewSplit').innerHTML=chunks.join('')}
function submitActivity(e){e.preventDefault();const id=document.getElementById('activityType').value,r=rules[id]||rules.custom,x=calcPreview();if(!x.total){toast('Add a duration to calculate XP');return}const todayCount=state.activities.filter(a=>a.type===id&&activityDate(a)===getToday()).length;const factor=todayCount>=5?.25:todayCount>=3?.5:1;const xp=Math.max(1,Math.round(x.total*factor));const p=x.primary,s=x.secondary;let pXP=xp, sXP=0;if(s&&s!==p){pXP=Math.round(xp*.7);sXP=xp-pXP}const name=id==='custom'?(document.getElementById('customName').value.trim()||'Custom activity'):r.name;const activity={id:crypto.randomUUID?crypto.randomUUID():String(Date.now()),type:id,name,icon:r.icon,duration:Number(document.getElementById('duration').value)||0,quality:Number(document.getElementById('quality').value),skill:x.skill,primary:p,secondary:s,xp,date:new Date().toISOString(),notes:document.getElementById('activityNotes').value.trim(),xpSplit:[{attr:p,xp:pXP},...(s&&s!==p?[{attr:s,xp:sXP}]:[])]};state.activities.push(activity);activity.xpSplit.forEach(z=>state.attrXP[z.attr]=(state.attrXP[z.attr]||0)+z.xp);state.skillXP[x.skill]=(state.skillXP[x.skill]||0)+xp;save();document.getElementById('activityNotes').value='';document.getElementById('duration').value=30;document.getElementById('quality').value='1';toast(`Activity saved · +${xp} XP`);navigate('dashboard')}
function toggleQuest(id,checked){const q=state.quests.find(q=>q.id===id);if(!q)return;const A=q.attribute||'discipline';if(checked&&questLocked(q)){toast('Locked until you reach level '+stageLvl(q.stage));renderQuests();return}const key=questKey(q);if(checked&&!state.questDone[key]){const qid='quest_'+Date.now();state.questDone[key]=qid;const xp=Math.max(0,Number(q.xp)||0);const noteEl=document.getElementById('qnote-'+id);const qnote=(noteEl&&noteEl.value.trim())||'';state.attrXP[A]=(state.attrXP[A]||0)+xp;state.skillXP['Quest Completion']=(state.skillXP['Quest Completion']||0)+xp;state.activities.push({id:qid,skill:'Quest Completion',type:'quest',name:'Quest: '+q.name,icon:'flag',duration:0,xp,primary:A,date:new Date().toISOString(),notes:qnote||'Quest completed',xpSplit:[{attr:A,xp}]});toast(`Quest complete · +${xp} XP`)}else if(!checked&&state.questDone[key]){const aid=state.questDone[key],xp=Math.max(0,Number(q.xp)||0);delete state.questDone[key];state.attrXP[A]=Math.max(0,(state.attrXP[A]||0)-xp);state.skillXP['Quest Completion']=Math.max(0,(state.skillXP['Quest Completion']||0)-xp);state.activities=state.activities.filter(a=>a.id!==aid);toast('Quest undone · XP removed')}save()}

// ===== 05-runtime.js lines 10-28: Streak and badges =====
 const delta=gains.reduce((n,g)=>n+g.xp,0);if(delta>1500)return;
 if(delta>0)celebrateXP(delta,gains);
 if(cur.c>p.c||ups.length)setTimeout(()=>showLevelUp({charLevel:cur.c>p.c?cur.c:null,from:p.c,attrs:ups}),delta>0?950:0)}
function streak(){const set=new Set(state.activities.map(activityDate));const d=new Date();let n=0,miss=0;for(let i=0;i<400;i++){if(set.has(d.toLocaleDateString('en-CA'))){n++;miss=0}else if(++miss>=2)break;d.setDate(d.getDate()-1)}return n}
const BADGES=[
{id:'first',icon:'bolt',name:'First Step',desc:'Log your first activity',t:()=>state.activities.length>=1},
{id:'ten',icon:'star',name:'Getting Going',desc:'Log 10 activities',t:()=>state.activities.length>=10},
{id:'fifty',icon:'target',name:'Habit Formed',desc:'Log 50 activities',t:()=>state.activities.length>=50},
{id:'s3',icon:'flame',name:'On a Roll',desc:'3-day streak',t:()=>streak()>=3},
{id:'s7',icon:'flame',name:'Week Warrior',desc:'7-day streak',t:()=>streak()>=7},
{id:'l5',icon:'mountain',name:'Rising',desc:'Reach character level 5',t:()=>characterLevel().level>=5},
{id:'l10',icon:'trophy',name:'Veteran',desc:'Reach character level 10',t:()=>characterLevel().level>=10},
{id:'round',icon:'star',name:'Well-Rounded',desc:'Every attribute at Lv 2+',t:()=>ATTRS.every(a=>attrLevel(a.id).level>=2)},
{id:'spec',icon:'trophy',name:'Specialist',desc:'Any attribute at Lv 5',t:()=>ATTRS.some(a=>attrLevel(a.id).level>=5)},
{id:'poly',icon:'book',name:'Polymath',desc:'Practice 3 different skills',t:()=>Object.values(state.skillXP).filter(v=>v>0).length>=3},
{id:'quest',icon:'flag',name:'Quest Taker',desc:'Complete 5 quests',t:()=>Object.keys(state.questDone).length>=5},
{id:'deep',icon:'clock',name:'Deep Work',desc:'One session of 90+ minutes',t:()=>state.activities.some(a=>a.duration>=90)},
{id:'saver',icon:'coin',name:'Saver',desc:'Deposit $100 in total',t:()=>M().tx.filter(t=>t.type==='deposit').reduce((s,t)=>s+t.amount,0)>=100}
];

// ===== 05-runtime.js lines 43-52: Attribute decay =====
function getDecay(){return {on:true,grace:3,rate:2,...(state.decay||{})}}
function dayNum(k){const p=k.split('-').map(Number);return Math.round(Date.UTC(p[0],p[1]-1,p[2])/864e5)}
function lastTrained(id){let best=null;state.activities.forEach(a=>{if(a.primary===id||a.secondary===id){const k=activityDate(a);if(!best||k>best)best=k}});return best}
function applyDecay(){const d=getDecay();if(!d.on)return false;state.decayApplied=state.decayApplied||{};state.decayLog=state.decayLog||[];const today=dayNum(getToday());let changed=false;
 ATTRS.forEach(a=>{const lt=lastTrained(a.id);if(!lt)return;const from=Math.max(dayNum(lt)+d.grace+1,(state.decayApplied[a.id]||0)+1);let lost=0;
  for(let n=from;n<=today;n++){const xp=attrXP(a.id);if(xp<=0)break;const l=Math.min(xp,Math.max(1,Math.round(xp*d.rate/100)));state.attrXP[a.id]=xp-l;lost+=l}
  if(today>=from)state.decayApplied[a.id]=today;
  if(lost>0){changed=true;state.decayTotal=state.decayTotal||{};state.decayTotal[a.id]=(state.decayTotal[a.id]||0)+lost;state.decayLog.unshift({date:getToday(),attr:a.id,xp:lost})}});
 state.decayLog=state.decayLog.slice(0,30);return changed}
function renderDecay(){const d=getDecay(),today=dayNum(getToday());

// ===== 05-runtime.js lines 162-166: Money deposit XP =====
function addTx(){const amt=Math.round((parseFloat(document.getElementById('txAmount').value)||0)*100)/100;if(!(amt>0)){toast('Enter an amount');return}
 const type=document.getElementById('txType').value,note=document.getElementById('txNote').value.trim(),iso=new Date().toISOString();
 if(type==='withdraw'&&amt>moneyBalance()){toast('Not enough in the account');return}
 const tx={id:'tx_'+Date.now(),type,amount:amt,note,date:iso};let xp=0;
 if(type==='deposit'&&state.activities.filter(a=>a.type==='money'&&activityDate(a)===getToday()).length<3&&!state.activities.some(a=>a.link==='business.transfer-money-into-savings'&&activityDate(a)===getToday())){xp=Math.min(60,Math.max(5,Math.round(amt/10)));

// ===== 07-runtime.js lines 6-6: Base XP lookup =====
function baseXP(a){return Math.max(1,Number((state.actXP||{})[a.id])||a.xp)}

// ===== 13-runtime.js lines 272-273: Quest stage unlocks =====
function stageLvl(s){return [0,1,3,5,8,12][s]||1}
function questLocked(q){return (q.stage||1)>1&&characterLevel().level<stageLvl(q.stage)}

// ===== 15-runtime.js lines 82-82: Cat bonding points =====
function petGain(p,pts){if(petStage(p)>=2)return 0;const before=petLevel(p);p.bondPts+=pts;return petLevel(p)>before?petLevel(p):0}

// ===== 39-health.js: Health Connect sync -> XP =====
/* v97 · Health sync: Samsung Health (through Health Connect) becomes XP.
   Workouts, daily steps, nights of sleep and your weight are read on the phone, each exactly once
   (state.health.seen), and logged as ordinary activities marked src:'health'. Nothing leaves the phone. */
const LMA_HEALTH_TYPES=[['ex','Workouts','exercise'],['steps','Daily steps','steps'],['sleep','Sleep','sleep'],['wt','Weight','weight']];
let lmaHealthBusy=false;
function lmaHealthCfg(){
 const s=state;if(!s.health||typeof s.health!=='object')s.health={on:false,ex:true,steps:true,sleep:true,wt:true,seen:{},last:0,since:0,msg:'',sdk:'',granted:[],src:'',today:null};
 if(!s.health.seen)s.health.seen={};if(!Array.isArray(s.health.granted))s.health.granted=[];
 return s.health;
}
const LMA_HC_RECOVERY=/yoga|pilates|stretch|breathing|meditat|tai_chi/,LMA_HC_STRENGTH=/weightlift|strength|calisthenics|boot_camp|gymnastic|martial|boxing|climb/;
function lmaHcNice(t){return String(t||'workout').replace(/_/g,' ').replace(/\b\w/g,function(c){return c.toUpperCase()}).replace('High Intensity Interval Training','HIIT')}
const LMA_HC_SRC={'com.sec.android.app.shealth':'Samsung Health','com.google.android.apps.fitness':'Google Fit','com.fitbit.FitbitMobile':'Fitbit','com.garmin.android.apps.connectmobile':'Garmin'};
function lmaHcSrc(p){return LMA_HC_SRC[p]||(p?p.split('.').slice(-1)[0]:'')}
/* the same XP rules as the Log activity screen, without the form */
function lmaHealthAward(o){
 const r=rules[o.type]||rules.custom;
 const day=new Date(o.at).toLocaleDateString('en-CA');
 let total=o.xp!=null?o.xp:Math.max(0,Math.round(r.base*Math.min(3,Math.max(0.4,o.duration/30))));
 const same=state.activities.filter(function(a){return a.type===o.type&&String(a.date||'').slice(0,10)===day}).length;
 const xp=Math.max(1,Math.round(total*(same>=5?.25:same>=3?.5:1)));
 const p=o.primary||r.primary,sec=o.secondary===undefined?r.secondary:o.secondary;
 let pXP=xp,sXP=0;if(sec&&sec!==p){pXP=Math.round(xp*.7);sXP=xp-pXP}
 const act={id:crypto.randomUUID?crypto.randomUUID():String(Date.now())+Math.random(),type:o.type,name:o.name,icon:r.icon,duration:o.duration||0,quality:1,skill:o.skill||r.skill,primary:p,secondary:sec,xp:xp,
  date:new Date(o.at).toISOString(),notes:o.note||'',xpSplit:[{attr:p,xp:pXP}].concat(sec&&sec!==p?[{attr:sec,xp:sXP}]:[]),src:'health',hc:o.hc};
 state.activities.push(act);act.xpSplit.forEach(function(z){state.attrXP[z.attr]=(state.attrXP[z.attr]||0)+z.xp});
 state.skillXP[act.skill]=(state.skillXP[act.skill]||0)+xp;return xp;
}
async function lmaHealthSync(manual){
 const H=lmaHealthCfg(),P=lmaPlugin('HealthSync');
 if(!P){if(manual)toast('Health sync works in the Android app');return}
 if(!H.on&&!manual)return;if(lmaHealthBusy)return;lmaHealthBusy=true;
 try{
  const st=await P.status();H.sdk=st.sdk;H.granted=st.granted||[];
  if(st.sdk!=='available'){H.msg=st.sdk==='update'?'Health Connect needs an update from the Play Store.':'Health Connect is not available on this phone.';return}
  const want=LMA_HEALTH_TYPES.filter(function(t){return H[t[0]]}).map(function(t){return t[2]}),has=function(n){return H.granted.indexOf(n)>=0};
  if(want.some(function(n){return !has(n)})&&!want.some(has)){H.msg='Allow access to start syncing.';return}
  const now=Date.now(),from=Math.max(H.since||0,now-3*86400e3),sum={ex:0,steps:0,sleep:0,xp:0},snap={at:now},day0=new Date();day0.setHours(0,0,0,0);
  if(!H.since)H.since=now-2*86400e3;
  if(H.ex&&has('exercise')){
   const r=await P.readExercise({from:now-7*86400e3,to:now});
   snap.exList=(r.sessions||[]).filter(function(s){return s.end<=now&&s.end-s.start>=5*60e3}).sort(function(a,b){return b.start-a.start}).slice(0,12).map(function(s){return {start:s.start,end:s.end,name:s.title||lmaHcNice(String(s.type)),src:s.origin?lmaHcSrc(s.origin):''}});
   const td=(r.sessions||[]).filter(function(s){return s.start>=day0.getTime()&&s.end<=now&&s.end-s.start>=5*60e3});
   snap.ex={count:td.length,min:td.reduce(function(a,s){return a+Math.round((s.end-s.start)/60000)},0)};
   for(const s of r.sessions||[]){
    const key='x'+s.id,min=Math.round((s.end-s.start)/60000);
    if(H.seen[key]||s.end>now||min<5||s.start<from)continue;
    const t=String(s.type),kind=LMA_HC_RECOVERY.test(t)?'recovery':LMA_HC_STRENGTH.test(t)?'strength':'running',nice=s.title||lmaHcNice(t);
    const xp=lmaHealthAward({type:kind,name:nice+(s.origin?' · '+lmaHcSrc(s.origin):''),duration:min,at:s.start,hc:key,skill:kind==='running'?lmaHcNice(t.replace(/_treadmill|_stationary|_machine|_open_water|_pool/,'')):undefined,note:'Imported from Health Connect'});
    H.seen[key]=s.end;sum.ex++;sum.xp+=xp;if(s.origin)H.src=lmaHcSrc(s.origin);
   }
  }
  if(H.steps&&has('steps')){
   const r=await P.readSteps({from:now-30*86400e3,to:now}),today=new Date().toLocaleDateString('en-CA'),sinceDay=new Date(H.since||from).toLocaleDateString('en-CA');
   snap.steps30=(r.days||[]).slice(-30);snap.steps=snap.steps30.slice(-7);
   for(const d of r.days||[]){
    if(d.date===today){H.today={date:d.date,count:d.count};continue}      /* a day counts once it is over */
    const key='p'+d.date;if(H.seen[key]||d.count<2000||d.date<sinceDay)continue;
    const xp=lmaHealthAward({type:'custom',name:'Daily steps: '+d.count.toLocaleString(),duration:0,at:d.date+'T20:00:00',hc:key,skill:'Walking',primary:'endurance',secondary:'discipline',xp:Math.round(Math.min(d.count,15000)/1000*3),note:'Imported from Health Connect'});
    H.seen[key]=Date.parse(d.date+'T23:59:00');sum.steps++;sum.xp+=xp;
   }
  }
  if(H.sleep&&has('sleep')){
   const r=await P.readSleep({from:now-8*86400e3,to:now});
   const last=(r.sessions||[]).filter(function(s){return s.end<=now}).sort(function(a,b){return b.end-a.end})[0];
   if(last)snap.sleep={min:Math.round((last.end-last.start)/60000),end:last.end};
   snap.nights=(r.sessions||[]).filter(function(s){return s.end<=now&&s.end-s.start>=3*3.6e6}).sort(function(a,b){return a.end-b.end}).slice(-7).map(function(s){return {end:s.end,min:Math.round((s.end-s.start)/60000)}});
   for(const s of r.sessions||[]){
    const key='z'+s.id,hrs=(s.end-s.start)/3.6e6;
    if(H.seen[key]||s.end>now||hrs<3||s.end<from)continue;
    const h=Math.floor(hrs),m=Math.round((hrs-h)*60),xp=lmaHealthAward({type:'recovery',name:'Sleep: '+h+'h '+String(m).padStart(2,'0')+'m',duration:hrs>=7?90:hrs>=6?60:hrs>=4.5?40:30,at:s.end,hc:key,skill:'Sleep',note:'Imported from Health Connect'});
    H.seen[key]=s.end;sum.sleep++;sum.xp+=xp;
   }
  }
  if(H.wt&&has('weight')){
   const r=await P.readWeight({from:now-60*86400e3,to:now});
   if(r&&r.kg){const lb=Math.round(r.kg*2.20462*10)/10;snap.wt=lb;if(r.time>(H.wtTime||0)){H.wtTime=r.time;H.wtHist=(H.wtHist||[]).concat([{t:r.time,lb:lb}]).slice(-60);if(state.weight!==lb){state.weight=lb;sum.wt=lb}}}
  }
  for(const k of Object.keys(H.seen))if(H.seen[k]<now-21*86400e3)delete H.seen[k];
  H.last=now;H.snap=Object.assign(H.snap||{},snap);
  const bits=[];if(sum.ex)bits.push(sum.ex+' workout'+(sum.ex===1?'':'s'));if(sum.steps)bits.push(sum.steps+' day'+(sum.steps===1?'':'s')+' of steps');if(sum.sleep)bits.push(sum.sleep+' night'+(sum.sleep===1?'':'s')+' of sleep');if(sum.wt)bits.push('weight '+sum.wt+' lb');
  H.msg=bits.length?'Added '+bits.join(', ')+(sum.xp?' (+'+sum.xp+' XP)':'')+'.':'Up to date.';
  if(bits.length||manual)toast('Health sync: '+(bits.length?bits.join(', ')+(sum.xp?' · +'+sum.xp+' XP':''):'nothing new'));
 }catch(e){H.msg='Sync failed: '+String((e&&e.message)||e).slice(0,100)}
 finally{lmaHealthBusy=false;save();lmaHealthRender();try{ovHealthRender()}catch(e){}try{lmaHealthSheetRender()}catch(e){}}
}
async function lmaHealthAsk(){
 const P=lmaPlugin('HealthSync');if(!P)return;const H=lmaHealthCfg();
 try{const r=await P.request({types:LMA_HEALTH_TYPES.filter(function(t){return H[t[0]]}).map(function(t){return t[2]})});H.granted=r.granted||[]}
 catch(e){H.msg='Could not ask for access: '+String((e&&e.message)||e).slice(0,100)}
 save();lmaHealthRender();lmaHealthSync(true);
}
function lmaHealthSet(k,v){const H=lmaHealthCfg();H[k]=v;if(k==='on'&&v&&!H.since)H.since=Date.now()-2*86400e3;save();lmaHealthRender();if(k==='on'&&v){const need=LMA_HEALTH_TYPES.some(function(t){return H[t[0]]&&H.granted.indexOf(t[2])<0});need?lmaHealthAsk():lmaHealthSync(true)}else if(v)lmaHealthSync(false)}
function lmaHealthRender(){
 const el=document.getElementById('lmaHealthBody');if(!el)return;
 if(!lmaNative()){el.innerHTML='<div class="note">Health sync works in the Android app, through Health Connect.</div>';return}
 const H=lmaHealthCfg(),ago=H.last?lmaAgo(H.last):'never',off=!H.on;
 const miss=LMA_HEALTH_TYPES.filter(function(t){return H[t[0]]&&H.granted.indexOf(t[2])<0});
 const row=function(l,hint,ctl){return '<div class="settingsline"><div style="flex:1"><strong>'+l+'</strong>'+(hint?'<div class="muted" style="font-size:12px">'+hint+'</div>':'')+'</div>'+ctl+'</div>'};
 const tog=function(k,dis){return '<input type="checkbox" class="check" '+(H[k]?'checked ':'')+(dis?'disabled ':'')+'onchange="lmaHealthSet(\''+k+'\',this.checked)" aria-label="'+k+'">'};
 let h='';
 if(H.sdk==='unavailable'||H.sdk==='update')h+='<div class="note" style="margin-bottom:10px">'+esc(H.msg)+'</div>';
 else if(H.on&&miss.length)h+='<div class="note" style="margin-bottom:10px">Allow access to '+miss.map(function(t){return t[1].toLowerCase()}).join(', ')+' to start syncing. <button class="btn small" onclick="lmaHealthAsk()">Allow access</button></div>';
 h+=row('Sync health data','Workouts, steps, sleep and weight become XP. Read on this phone only.',tog('on'));
 h+=LMA_HEALTH_TYPES.map(function(t){return row(t[1],{ex:'Each workout, by how long it lasted.',steps:'Once a day is over: 2,000+ steps counts, 3 XP per 1,000 up to 15,000.',sleep:'Each night of 3+ hours, more XP for 6 to 7+ hours.',wt:'Updates your character weight from your latest reading.'}[t[0]],tog(t[0],off))}).join('');
 h+=row('Daily steps goal','Fills the bar on the Overview.','<select onchange="lmaHealthSet(\'goal\',+this.value)" style="'+LMA_IN+'">'+[5000,7500,8000,10000,12000,15000].map(function(v){return '<option value="'+v+'"'+((H.goal||8000)===v?' selected':'')+'>'+v.toLocaleString()+'</option>'}).join('')+'</select>');
 h+='<div class="settingsline"><div style="flex:1"><strong>Last sync</strong><div class="muted" style="font-size:12px">'+esc(ago)+(H.src?' · '+esc(H.src):'')+(H.msg?' · '+esc(H.msg):'')+(H.today?'<br>Today so far: '+H.today.count.toLocaleString()+' steps':'')+'</div></div><button class="btn small" '+(lmaHealthBusy?'disabled ':'')+'onclick="lmaHealthSync(true)">'+(lmaHealthBusy?'Syncing…':'Sync now')+'</button></div>';
 h+='<p class="muted" style="font-size:12px;margin:10px 0 0">Using Samsung Health? Open it, go to Settings → Health Connect, and let it write exercise, steps, sleep and weight. LifeMax then reads them from Health Connect. Each record is counted once.</p>';
 el.innerHTML=h;
}
(function lmaHealthBoot(){
 const _nav=navigate;navigate=function(v){const r=_nav.apply(this,arguments);if(v==='settings')lmaHealthRender();return r};
 document.addEventListener('visibilitychange',function(){if(document.visibilityState==='visible'&&lmaNative()&&lmaHealthCfg().on&&Date.now()-lmaHealthCfg().last>15*60e3)lmaHealthSync(false)});
 setInterval(function(){if(lmaNative()&&lmaHealthCfg().on)lmaHealthSync(false)},30*60e3);
 setTimeout(function(){lmaHealthRender();if(lmaNative()&&lmaHealthCfg().on)lmaHealthSync(false)},14000);
})();

/* ---------- the Overview tile ---------- */
function ovHealthDur(m){const h=Math.floor(m/60),r=m%60;return h?h+'h '+String(r).padStart(2,'0')+'m':r+'m'}
function ovHealthRender(){
 const el=document.getElementById('ovHealth');if(!el)return;
 el.onclick=ovHealthTap;el.style.cursor='pointer';
 const tile=el.closest('.ov-tile');if(!lmaNative()){if(tile)tile.style.display='none';return}
 if(tile)tile.style.display='';
 const H=lmaHealthCfg(),S=H.snap||{};
 if(!H.on){el.innerHTML='<div class="ov-h"><h3>Activity today</h3></div><p class="muted" style="font-size:13px;margin:0 0 10px">Connect Samsung Health to see your steps, workouts, sleep and weight here, and turn them into XP.</p><button class="btn small" onclick="navigate(\'settings\')">Set up health sync</button>';return}
 const goal=H.goal||8000,today=H.today&&H.today.date===new Date().toLocaleDateString('en-CA')?H.today.count:null,pct=today==null?0:Math.min(100,Math.round(today/goal*100));
 const days=(S.steps||[]).slice(-7),mx=Math.max(goal,...days.map(function(d){return d.count}),1);
 const chip=function(ico,l,v){return '<div class="ovh-chip"><span>'+l+'</span><b>'+v+'</b></div>'};
 let h='<div class="ov-h"><h3>Activity today</h3><span class="muted" style="font-size:11.5px">'+(H.src?esc(H.src)+' · ':'')+(H.last?esc(lmaAgo(H.last)):'not synced yet')+'</span></div>';
 h+='<div class="ovh-steps"><div><b>'+(today==null?'–':today.toLocaleString())+'</b><span> steps</span></div><small>'+(today==null?'Waiting for the first sync':pct>=100?'Goal reached 🎉':(goal-today).toLocaleString()+' to your '+goal.toLocaleString()+' goal')+'</small></div>';
 h+='<div class="ovh-bar"><i style="width:'+pct+'%"></i></div>';
 if(days.length)h+='<div class="ovh-days">'+days.map(function(d,i){const t=new Date(d.date+'T12:00:00');return '<div class="'+(i===days.length-1?'now':'')+'" title="'+d.count.toLocaleString()+'"><i style="height:'+Math.max(4,Math.round(d.count/mx*38))+'px"></i><small>'+'SMTWTFS'.charAt(t.getDay())+'</small></div>'}).join('')+'</div>';
 h+='<div class="ovh-chips">'+(S.ex?chip('','Workouts',S.ex.count?S.ex.count+' · '+ovHealthDur(S.ex.min):'none yet'):'')+(S.sleep?chip('','Last night',ovHealthDur(S.sleep.min)):'')+(S.wt?chip('','Weight',S.wt+' lb'):'')+'</div>';
el.innerHTML=h+'<div class="ovh-more">Details ›</div>';
}
function ovHealthTap(e){if(e.target.closest('button'))return;lmaHealthOpen()}
(function ovHealthBoot(){
 const i=OV_TILES.findIndex(function(t){return t.id==='hero'})+1;
 OV_TILES.splice(i,0,{id:'health',name:'Activity today',sub:'Steps, workouts, sleep and weight from Health Connect',icon:'bolt',col:'#34d399'});
 OV_DEFAULT.splice(i,0,'health');
 const nt=ovTileNote;ovTileNote=function(id){return id==='health'&&!lmaNative()?'Appears in the Android app':nt.apply(this,arguments)};
 const ro=renderOverview;renderOverview=function(){const r=ro.apply(this,arguments);try{ovHealthRender()}catch(e){console.warn('[health tile]',e)}return r};
 setTimeout(function(){try{ovHealthRender()}catch(e){}},1500);
})();

/* ---------- the expanded Health page (tap the Activity tile) ---------- */
function lmaHealthOpen(){
 if(document.querySelector('.hsheet'))return;
 const o=document.createElement('div');o.className='hsheet';o.setAttribute('role','dialog');o.setAttribute('aria-label','Health');
 o.addEventListener('click',function(e){const b=e.target.closest('[data-hs]');if(!b)return;if(b.dataset.hs==='close')o.remove();else if(b.dataset.hs==='sync')lmaHealthSync(true);else if(b.dataset.hs==='setup'){o.remove();navigate('settings')}});
 document.body.appendChild(o);lmaHealthSheetRender();
 const H=lmaHealthCfg();if(H.on)lmaHealthSync(false);
}
function lmaHealthSheetRender(){
 const o=document.querySelector('.hsheet');if(!o)return;
 const H=lmaHealthCfg(),S=H.snap||{},dur=ovHealthDur,day=function(t){return new Date(t).toLocaleDateString(undefined,{weekday:'short',month:'short',day:'numeric'})};
 const head='<header><button class="btn small secondary" data-hs="close">‹ Back</button><h2>Health</h2><button class="btn small" data-hs="sync"'+(lmaHealthBusy||!H.on?' disabled':'')+'>'+(lmaHealthBusy?'Syncing…':'Sync now')+'</button></header>';
 if(!H.on){o.innerHTML=head+'<div class="hs-body"><div class="card"><p style="margin:0 0 10px">Health sync is off. Turn it on to see your steps, workouts, sleep and weight here and earn XP for them.</p><button class="btn" data-hs="setup">Set up health sync</button></div></div>';return}
 const goal=H.goal||8000,tdy=H.today&&H.today.date===new Date().toLocaleDateString('en-CA')?H.today.count:null,pct=tdy==null?0:Math.min(100,Math.round(tdy/goal*100));
 const d30=(S.steps30||S.steps||[]),last7=d30.slice(-7),avg=last7.length?Math.round(last7.reduce(function(a,d){return a+d.count},0)/last7.length):0;
 const best=d30.reduce(function(m,d){return d.count>m.count?d:m},{count:0,date:''}),hit=d30.filter(function(d){return d.count>=goal}).length;
 const mx=Math.max(goal,...d30.map(function(d){return d.count}),1);
 let h=head+'<div class="hs-body">';
 h+='<div class="card hs-hero"><div class="hs-big"><b>'+(tdy==null?'–':tdy.toLocaleString())+'</b><span>steps today</span></div><div class="ovh-bar"><i style="width:'+pct+'%"></i></div><div class="muted" style="font-size:12.5px">'+(tdy==null?'Waiting for the first sync':pct>=100?'Goal of '+goal.toLocaleString()+' reached 🎉':(goal-tdy).toLocaleString()+' to your '+goal.toLocaleString()+' goal')+'</div></div>';
 if(d30.length){
  h+='<div class="card"><h3>Steps · last '+d30.length+' days</h3><div class="hs-chart">'+d30.map(function(d,i){const hh=Math.max(3,Math.round(d.count/mx*96));return '<div class="'+(d.count>=goal?'hit ':'')+(i===d30.length-1?'now':'')+'" title="'+esc(d.date)+': '+d.count.toLocaleString()+'"><i style="height:'+hh+'px"></i></div>'}).join('')+'<u style="bottom:'+Math.round(goal/mx*96)+'px"></u></div>'+
   '<div class="ovh-chips"><div class="ovh-chip"><span>7-day average</span><b>'+avg.toLocaleString()+'</b></div><div class="ovh-chip"><span>Best day</span><b>'+best.count.toLocaleString()+'</b></div><div class="ovh-chip"><span>Goal days</span><b>'+hit+' of '+d30.length+'</b></div></div></div>';
 }
 const ex=S.exList||[];
 h+='<div class="card"><h3>Workouts · last 7 days</h3>'+(ex.length?'<ul class="hs-list">'+ex.map(function(x){return '<li><div><b>'+esc(x.name)+'</b><small>'+esc(day(x.start))+(x.src?' · '+esc(x.src):'')+'</small></div><span>'+dur(Math.round((x.end-x.start)/60000))+'</span></li>'}).join('')+'</ul>':'<p class="muted" style="margin:0">No workouts recorded this week.</p>')+'</div>';
 const ng=S.nights||[];
 if(ng.length){const mxs=Math.max(9*60,...ng.map(function(n){return n.min})),avs=Math.round(ng.reduce(function(a,n){return a+n.min},0)/ng.length);
  h+='<div class="card"><h3>Sleep · last '+ng.length+' nights</h3><div class="hs-chart hs-sleep">'+ng.map(function(n){return '<div title="'+esc(day(n.end))+'"><i style="height:'+Math.max(3,Math.round(n.min/mxs*80))+'px"></i><small>'+(n.min/60).toFixed(1)+'</small></div>'}).join('')+'</div><div class="muted" style="font-size:12.5px">Average '+dur(avs)+' · last night '+(S.sleep?dur(S.sleep.min):'–')+'</div></div>';}
 else if(S.sleep)h+='<div class="card"><h3>Sleep</h3><p style="margin:0">Last night: <b>'+dur(S.sleep.min)+'</b></p></div>';
 const wh=H.wtHist||[];
 if(S.wt){let spark='';if(wh.length>1){const lo=Math.min(...wh.map(function(w){return w.lb})),hi=Math.max(...wh.map(function(w){return w.lb})),rg=Math.max(hi-lo,.5);spark='<svg viewBox="0 0 200 40" class="hs-spark" preserveAspectRatio="none"><polyline fill="none" stroke="#34d399" stroke-width="2" points="'+wh.map(function(w,i){return (i/(wh.length-1)*200).toFixed(1)+','+(36-(w.lb-lo)/rg*32).toFixed(1)}).join(' ')+'"/></svg>';}
  h+='<div class="card"><h3>Weight</h3><div class="hs-big"><b>'+S.wt+'</b><span>lb</span></div>'+spark+(wh.length>1?'<div class="muted" style="font-size:12.5px">'+(wh[wh.length-1].lb-wh[0].lb>=0?'+':'')+(wh[wh.length-1].lb-wh[0].lb).toFixed(1)+' lb since '+esc(day(wh[0].t))+'</div>':'')+'</div>';}
 h+='<p class="muted" style="font-size:12px;text-align:center">'+(H.last?'Last synced '+esc(lmaAgo(H.last)):'Not synced yet')+(H.src?' · '+esc(H.src):'')+(H.msg?' · '+esc(H.msg):'')+'</p></div>';
 const sc=o.querySelector('.hs-body'),top=sc?sc.scrollTop:0;
 o.innerHTML=h;const nb=o.querySelector('.hs-body');if(nb)nb.scrollTop=top;
}


// ===== 36-money-pay.js: Shift pay (money, no XP) =====
/* v92 · Paychecks: work shifts from Life Director become pending pay when they end, and move into the
   account when payday arrives. Data lives in state.money (pay, jobs, pending, seen), so backups carry it. */
const PAY_FREQ=[['weekly','Every week'],['biweekly','Every 2 weeks'],['semimonthly','Twice a month'],['monthly','Monthly']];
const payR2=n=>Math.round(n*100)/100;
/* take-home for one pending shift, using the tax setting as it is now */
const payNet=p=>payR2(p.gross*(1-(Number(payCfg().pay.tax)||0)/100));
function payCfg(){
 const m=M();
 if(!m.pay)m.pay={on:false,freq:'biweekly',next:'',lag:0,tax:0,from:getToday()};
 if(!m.jobs)m.jobs={};if(!m.pending)m.pending=[];if(!m.seen)m.seen={};
 return m;
}
function payRules(){try{return (window.LifeDirector&&LifeDirector.workRules&&LifeDirector.workRules())||[]}catch(e){return []}}
function payDayStr(d){const x=new Date(d);return x.toLocaleDateString(undefined,{weekday:'short',month:'short',day:'numeric'})}
function payNextDate(freq,d){
 const n=new Date(d.getTime());
 if(freq==='weekly')n.setDate(n.getDate()+7);
 else if(freq==='biweekly')n.setDate(n.getDate()+14);
 else if(freq==='monthly'){const day=d.getDate();n.setDate(1);n.setMonth(n.getMonth()+1);n.setDate(Math.min(day,new Date(n.getFullYear(),n.getMonth()+1,0).getDate()))}
 else{ /* twice a month: the chosen day and the day 15 apart */
  const day=d.getDate(),a=day<=15?day:day-15,b=a+15,last=m=>new Date(m.getFullYear(),m.getMonth()+1,0).getDate();
  if(day===a){n.setDate(Math.min(b,last(d)))}
  else{n.setDate(1);n.setMonth(n.getMonth()+1);n.setDate(Math.min(a,last(n)))}
 }
 return n;
}
const payISO=d=>{const p=n=>String(n).padStart(2,'0');return d.getFullYear()+'-'+p(d.getMonth()+1)+'-'+p(d.getDate())+'T'+p(d.getHours())+':'+p(d.getMinutes())};
/* find shifts that ended since the start date and add them to pending pay (each shift only once) */
function payScan(){
 const m=payCfg(),P=m.pay;if(!P.on||!window.LifeDirector||!LifeDirector.shifts)return false;
 const now=Math.floor(Date.now()/1000),startMs=Math.max(parseD(P.from||getToday()).getTime(),Date.now()-90*86400e3),from=Math.floor(startMs/1000);
 let list;try{list=LifeDirector.shifts(from,now)}catch(e){return false}
 const rules=payRules(),firstJob=rules.map(r=>m.jobs[r.id]).find(j=>j&&j.wage>0);
 let changed=false;
 for(const s of list){
  if(m.seen[s.key])continue;
  let job=s.ruleId?m.jobs[s.ruleId]:null;
  if(!s.ruleId){const r=rules.find(x=>(x.title||'').toLowerCase()===(s.title||'').toLowerCase());job=(r&&m.jobs[r.id])||firstJob}
  if(!job||!(job.wage>0))continue; /* no wage yet: leave it, it will be picked up once a wage is set */
  const hours=Math.max(0,(s.durationMin-(Number(job.brk)||0))/60),gross=payR2(hours*job.wage);
  m.pending.push({id:'pp_'+s.endEpoch+'_'+m.pending.length,key:s.key,title:s.title,start:s.startEpoch,end:s.endEpoch,hours:payR2(hours),rate:job.wage,gross});
  m.seen[s.key]=s.endEpoch;changed=true;
 }
 for(const k of Object.keys(m.seen))if(m.seen[k]<from-86400){delete m.seen[k];changed=true}
 return changed;
}
function payClear(cutoffMs,whenISO,note){
 const m=payCfg(),take=m.pending.filter(p=>p.end*1000<=cutoffMs);if(!take.length)return 0;
 const sum=payR2(take.reduce((s,p)=>s+payNet(p),0));
 m.tx.push({id:'tx_'+Date.now()+'_pay'+m.tx.length,type:'deposit',amount:sum,note:take.length+' shift'+(take.length===1?'':'s')+(note?' · '+note:''),date:whenISO,pay:true});
 m.pending=m.pending.filter(p=>p.end*1000>cutoffMs);
 return sum;
}
/* run on a timer and when the app is opened: catch up on shifts, then on any paydays that have passed */
function payTick(){
 const m=payCfg(),P=m.pay;if(!P.on)return;
 let changed=payScan(),cleared=0;
 if(P.next){
  let next=new Date(P.next),guard=0;
  while(!isNaN(next)&&next.getTime()<=Date.now()&&guard++<24){
   const sum=payClear(next.getTime()-(Number(P.lag)||0)*86400e3,next.toISOString(),'');
   if(sum)cleared+=sum;
   next=payNextDate(P.freq,next);P.next=payISO(next);changed=true;
  }
 }
 if(changed){save();if(cleared)toast('Paycheck of '+fmt(cleared)+' added to your account')}
}
function payClearNow(){
 const m=payCfg();if(!m.pending.length){toast('Nothing pending');return}
 const sum=payClear(Date.now(),new Date().toISOString(),'cleared by hand');save();toast(sum?'Paycheck of '+fmt(sum)+' added':'Nothing to clear');
}
function payRemovePending(id){const m=payCfg();m.pending=m.pending.filter(p=>p.id!==id);save();toast('Removed. It will not be counted again.')}
function paySet(k,v){
 const m=payCfg(),P=m.pay;
 if(k==='on'){P.on=!!v;if(v&&!P.from)P.from=getToday()}
 else if(k==='tax')P.tax=Math.min(60,Math.max(0,parseFloat(v)||0));
 else if(k==='lag')P.lag=Math.min(30,Math.max(0,Math.round(parseFloat(v)||0)));
 else if(k==='freq')P.freq=v;
 else if(k==='from'){P.from=v||getToday()}
 else if(k==='nextDate'||k==='nextTime'){
  const cur=P.next?new Date(P.next):null,d0=k==='nextDate'?v:(cur&&!isNaN(cur)?payISO(cur).slice(0,10):getToday()),t0=k==='nextTime'?v:(cur&&!isNaN(cur)?payISO(cur).slice(11):'09:00');
  if(d0)P.next=d0+'T'+(t0||'09:00');
 }
 save();payTick();
}
function payJob(id,field,v){const m=payCfg(),j=m.jobs[id]||(m.jobs[id]={wage:0,brk:0});j[field]=Math.max(0,parseFloat(v)||0);save();payTick()}
function payDays(days){const n=['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];return days.length===7?'Every day':days.length===5&&days.join()==='1,2,3,4,5'?'Mon–Fri':days.map(d=>n[d]).join(', ')}
function payTime(min){const d=new Date(2000,0,1,Math.floor(min/60),min%60);return d.toLocaleTimeString(undefined,{hour:'numeric',minute:'2-digit'})}
function payRender(){
 const m=payCfg(),P=m.pay,el=id=>document.getElementById(id);if(!el('payBody'))return;
 const on=el('payOn');if(on)on.checked=!!P.on;
 const pend=m.pending.slice().sort((a,b)=>a.end-b.end),total=payR2(pend.reduce((s,p)=>s+payNet(p),0));
 const line=el('moneyPendingLine');
 if(line){
  line.style.display=P.on?'flex':'none';
  if(P.on){const nd=P.next?new Date(P.next):null;line.innerHTML='<span>Pending pay <strong>'+fmt(total)+'</strong><span class="muted" style="font-size:12px"> · '+pend.length+' shift'+(pend.length===1?'':'s')+'</span></span><span class="muted" style="font-size:12px">'+(nd&&!isNaN(nd)?'Payday '+payDayStr(nd)+', '+nd.toLocaleTimeString(undefined,{hour:'numeric',minute:'2-digit'}):'Set your payday below')+'</span>'}
 }
 if(!P.on){el('payBody').innerHTML='<p class="muted" style="font-size:12px;margin:0">Turn this on to add your work shifts as pending pay.</p>';return}
 if(el('payBody').contains(document.activeElement)&&document.activeElement.tagName==='INPUT')return; /* do not rebuild while typing */
 const rules=payRules(),nd=P.next?new Date(P.next):null,ndOk=nd&&!isNaN(nd);
 const inp='style="background:var(--panel2);color:var(--text);border:1px solid var(--line);border-radius:8px;padding:7px 9px;font:inherit;width:100%"';
 let h='';
 if(!rules.length)h+='<div class="note" style="margin-bottom:10px">No work hours found. Add them in Life Director → My time, as a commitment of type Work.</div>';
 else{
  h+='<div class="statlabel" style="margin-bottom:6px">YOUR JOBS</div>';
  for(const r of rules){const j=m.jobs[r.id]||{};
   h+='<div class="card" style="padding:12px;margin-bottom:8px"><strong>'+esc(r.title)+'</strong><div class="muted" style="font-size:12px">'+esc(payDays(r.days))+' · '+payTime(r.startMin)+' · '+payR2(r.durationMin/60)+' h</div>'+
    '<div class="formgrid" style="margin-top:8px"><div class="field"><label>Hourly wage ($)</label><input type="number" min="0" step="0.01" inputmode="decimal" value="'+(j.wage||'')+'" placeholder="0.00" onchange="payJob(\''+esc(r.id)+'\',\'wage\',this.value)" '+inp+'></div>'+
    '<div class="field"><label>Unpaid break (min)</label><input type="number" min="0" step="5" inputmode="numeric" value="'+(j.brk||'')+'" placeholder="0" onchange="payJob(\''+esc(r.id)+'\',\'brk\',this.value)" '+inp+'></div></div></div>';}
 }
 h+='<div class="statlabel" style="margin:12px 0 6px">PAYDAY</div><div class="formgrid">'+
  '<div class="field"><label>How often</label><select onchange="paySet(\'freq\',this.value)" '+inp+'>'+PAY_FREQ.map(f=>'<option value="'+f[0]+'"'+(P.freq===f[0]?' selected':'')+'>'+f[1]+'</option>').join('')+'</select></div>'+
  '<div class="field"><label>Next payday</label><input type="date" value="'+(ndOk?payISO(nd).slice(0,10):'')+'" onchange="paySet(\'nextDate\',this.value)" '+inp+'></div>'+
  '<div class="field"><label>Time it arrives</label><input type="time" value="'+(ndOk?payISO(nd).slice(11):'09:00')+'" onchange="paySet(\'nextTime\',this.value)" '+inp+'></div>'+
  '<div class="field"><label>Hold back newest days of work</label><input type="number" min="0" max="30" inputmode="numeric" value="'+(P.lag||0)+'" onchange="paySet(\'lag\',this.value)" '+inp+'></div>'+
  '<div class="field"><label>Estimated tax withheld (%)</label><input type="number" min="0" max="60" step="0.5" inputmode="decimal" value="'+(P.tax||0)+'" onchange="paySet(\'tax\',this.value)" '+inp+'></div>'+
  '<div class="field"><label>Count shifts starting</label><input type="date" value="'+esc(P.from||'')+'" onchange="paySet(\'from\',this.value)" '+inp+'></div></div>'+
  '<div class="muted" style="font-size:12px;margin-top:6px">“Hold back” leaves the newest days unpaid until the next check, like a pay period that ends before payday. Extra shifts added in Life Director use the job with the same name, or your first job’s wage.</div>';
 h+='<div class="statlabel" style="margin:14px 0 6px">PENDING PAY</div>';
 if(!pend.length)h+='<div class="muted" style="font-size:13px">No shifts waiting. They appear here when they end.</div>';
 else{
  h+=pend.map(p=>'<div class="minirow"><span><strong>'+esc(p.title)+'</strong><br><small>'+payDayStr(p.end*1000)+' · '+p.hours+' h × '+fmt(p.rate)+'</small></span><span style="display:flex;align-items:center;gap:10px"><strong style="color:var(--accent2)">+'+fmt(payNet(p))+'</strong><button class="btn small secondary" onclick="payRemovePending(\''+p.id+'\')" title="Did not work this shift">Remove</button></span></div>').join('');
  h+='<div style="margin-top:10px;display:flex;gap:8px;flex-wrap:wrap;align-items:center"><button class="btn secondary" onclick="payClearNow()">Paycheck arrived: add '+fmt(total)+' now</button></div>';
 }
 el('payBody').innerHTML=h;
}
/* ---------- wiring ---------- */
(function payBoot(){
 const _rm=renderMoney;renderMoney=function(){const r=_rm.apply(this,arguments);try{payRender()}catch(e){console.warn('pay render',e)}return r};
 document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible')payTick()});
 setInterval(payTick,60e3);
 setTimeout(payTick,4000);
})();

