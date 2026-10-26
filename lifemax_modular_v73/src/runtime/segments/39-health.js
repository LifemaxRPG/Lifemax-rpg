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
 const r=o.rule||rules[o.type]||rules.custom;
 const day=new Date(o.at).toLocaleDateString('en-CA');
 const total=o.xp!=null?o.xp:Math.max(0,Math.round(r.base*ecoDur(o.duration)));
 const ec=ecoAward(o.type,total,o.duration,{day:day});
 if(ec.xp<=0)return 0;
 const xp=ec.xp;
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
  let exAll=[];
  const now=Date.now(),from=Math.max(H.since||0,now-3*86400e3),sum={ex:0,steps:0,sleep:0,xp:0},snap={at:now},day0=new Date();day0.setHours(0,0,0,0);
  if(!H.since)H.since=now-2*86400e3;
  if(H.ex&&has('exercise')){
   const r=await P.readExercise({from:now-7*86400e3,to:now});
   exAll=r.sessions||[];
   snap.exList=(r.sessions||[]).filter(function(s){return s.end<=now&&s.end-s.start>=5*60e3}).sort(function(a,b){return b.start-a.start}).slice(0,12).map(function(s){return {start:s.start,end:s.end,name:s.title||lmaHcNice(String(s.type)),src:s.origin?lmaHcSrc(s.origin):''}});
   const td=(r.sessions||[]).filter(function(s){return s.start>=day0.getTime()&&s.end<=now&&s.end-s.start>=5*60e3});
   snap.ex={count:td.length,min:td.reduce(function(a,s){return a+Math.round((s.end-s.start)/60000)},0)};
   for(const s of r.sessions||[]){
    const key='x'+s.id,min=Math.round((s.end-s.start)/60000);
    if(H.seen[key]||s.end>now||min<5||s.start<from)continue;
    const t=String(s.type),kind=LMA_HC_RECOVERY.test(t)?'recovery':LMA_HC_STRENGTH.test(t)?'strength':'running',nice=s.title||lmaHcNice(t);
    /* a workout you already logged by hand within two hours is not paid twice */
    if(state.activities.some(function(a){return a.src!=='health'&&a.type===kind&&Math.abs(new Date(a.date).getTime()-s.start)<=2*3600e3})){H.seen[key]=s.end;continue}
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
    /* steps already paid as a run do not count twice; an absurd count with no workout is capped */
    const dayEx=exAll.filter(function(e){return new Date(e.start).toLocaleDateString('en-CA')===d.date});
    const cardio=dayEx.filter(function(e){return !LMA_HC_RECOVERY.test(String(e.type))&&!LMA_HC_STRENGTH.test(String(e.type))}).reduce(function(m,e){return m+Math.max(0,(e.end-e.start)/60000)},0);
    let eff=d.count;if(eff>35000&&!dayEx.length)eff=8000;eff-=Math.round(cardio)*100;
    H.seen[key]=Date.parse(d.date+'T23:59:00');
    if(eff<4000)continue;
    const xp=lmaHealthAward({type:'steps',rule:{icon:'target',skill:'Walking',primary:'endurance',secondary:''},name:'Daily steps: '+d.count.toLocaleString(),duration:0,at:d.date+'T20:00:00',hc:key,skill:'Walking',primary:'endurance',secondary:'',xp:Math.min(15,Math.round((eff-2000)/1000*1.5)),note:'Imported from Health Connect'});
    sum.steps++;sum.xp+=xp;
   }
  }
  if(H.sleep&&has('sleep')){
   const r=await P.readSleep({from:now-8*86400e3,to:now});
   const last=(r.sessions||[]).filter(function(s){return s.end<=now}).sort(function(a,b){return b.end-a.end})[0];
   if(last)snap.sleep={min:Math.round((last.end-last.start)/60000),end:last.end};
   snap.nights=(r.sessions||[]).filter(function(s){return s.end<=now&&s.end-s.start>=3*3.6e6}).sort(function(a,b){return a.end-b.end}).slice(-7).map(function(s){return {end:s.end,min:Math.round((s.end-s.start)/60000)}});
   /* only the longest sleep ending on each day pays; a night with a workout in the middle of it does not */
   const best={};
   for(const s of r.sessions||[]){const d=new Date(s.end).toLocaleDateString('en-CA');if(s.end<=now&&(!best[d]||s.end-s.start>best[d].end-best[d].start))best[d]=s}
   for(const s of r.sessions||[]){
    const key='z'+s.id,hrs=(s.end-s.start)/3.6e6;
    if(H.seen[key]||s.end>now||hrs<3||s.end<from)continue;
    H.seen[key]=s.end;
    if(best[new Date(s.end).toLocaleDateString('en-CA')]!==s)continue;
    const busy=exAll.some(function(e){return e.start<s.end-30*60e3&&e.end>s.start+30*60e3})||state.activities.some(function(a){const t=new Date(a.date).getTime();return a.src!=='health'&&t>s.start+30*60e3&&t<s.end-30*60e3});
    const pts=hrs<5.5||hrs>12||busy?0:hrs<6.5?8:hrs<7.5?12:hrs<=9.5?15:12;
    if(!pts)continue;
    const h=Math.floor(hrs),m=Math.round((hrs-h)*60),xp=lmaHealthAward({type:'sleep',rule:{icon:'moon',skill:'Sleep',primary:'wellbeing',secondary:''},name:'Sleep: '+h+'h '+String(m).padStart(2,'0')+'m',duration:0,at:s.end,hc:key,skill:'Sleep',primary:'wellbeing',secondary:'',xp:pts,note:'Imported from Health Connect'});
    sum.sleep++;sum.xp+=xp;
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
