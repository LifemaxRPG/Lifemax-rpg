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
  const now=Date.now(),from=Math.max(H.since||0,now-3*86400e3),sum={ex:0,steps:0,sleep:0,xp:0};
  if(!H.since)H.since=now-2*86400e3;
  if(H.ex&&has('exercise')){
   const r=await P.readExercise({from:from,to:now});
   for(const s of r.sessions||[]){
    const key='x'+s.id,min=Math.round((s.end-s.start)/60000);
    if(H.seen[key]||s.end>now||min<5)continue;
    const t=String(s.type),kind=LMA_HC_RECOVERY.test(t)?'recovery':LMA_HC_STRENGTH.test(t)?'strength':'running',nice=s.title||lmaHcNice(t);
    const xp=lmaHealthAward({type:kind,name:nice+(s.origin?' · '+lmaHcSrc(s.origin):''),duration:min,at:s.start,hc:key,skill:kind==='running'?lmaHcNice(t.replace(/_treadmill|_stationary|_machine|_open_water|_pool/,'')):undefined,note:'Imported from Health Connect'});
    H.seen[key]=s.end;sum.ex++;sum.xp+=xp;if(s.origin)H.src=lmaHcSrc(s.origin);
   }
  }
  if(H.steps&&has('steps')){
   const r=await P.readSteps({from:from,to:now}),today=new Date().toLocaleDateString('en-CA');
   for(const d of r.days||[]){
    if(d.date===today){H.today={date:d.date,count:d.count};continue}      /* a day counts once it is over */
    const key='p'+d.date;if(H.seen[key]||d.count<2000)continue;
    const xp=lmaHealthAward({type:'custom',name:'Daily steps: '+d.count.toLocaleString(),duration:0,at:d.date+'T20:00:00',hc:key,skill:'Walking',primary:'endurance',secondary:'discipline',xp:Math.round(Math.min(d.count,15000)/1000*3),note:'Imported from Health Connect'});
    H.seen[key]=Date.parse(d.date+'T23:59:00');sum.steps++;sum.xp+=xp;
   }
  }
  if(H.sleep&&has('sleep')){
   const r=await P.readSleep({from:from,to:now});
   for(const s of r.sessions||[]){
    const key='z'+s.id,hrs=(s.end-s.start)/3.6e6;
    if(H.seen[key]||s.end>now||hrs<3)continue;
    const h=Math.floor(hrs),m=Math.round((hrs-h)*60),xp=lmaHealthAward({type:'recovery',name:'Sleep: '+h+'h '+String(m).padStart(2,'0')+'m',duration:hrs>=7?90:hrs>=6?60:hrs>=4.5?40:30,at:s.end,hc:key,skill:'Sleep',note:'Imported from Health Connect'});
    H.seen[key]=s.end;sum.sleep++;sum.xp+=xp;
   }
  }
  if(H.wt&&has('weight')){
   const r=await P.readWeight({from:now-60*86400e3,to:now});
   if(r&&r.kg&&r.time>(H.wtTime||0)){const lb=Math.round(r.kg*2.20462*10)/10;H.wtTime=r.time;if(state.weight!==lb){state.weight=lb;sum.wt=lb}}
  }
  for(const k of Object.keys(H.seen))if(H.seen[k]<now-21*86400e3)delete H.seen[k];
  H.last=now;
  const bits=[];if(sum.ex)bits.push(sum.ex+' workout'+(sum.ex===1?'':'s'));if(sum.steps)bits.push(sum.steps+' day'+(sum.steps===1?'':'s')+' of steps');if(sum.sleep)bits.push(sum.sleep+' night'+(sum.sleep===1?'':'s')+' of sleep');if(sum.wt)bits.push('weight '+sum.wt+' lb');
  H.msg=bits.length?'Added '+bits.join(', ')+(sum.xp?' (+'+sum.xp+' XP)':'')+'.':'Up to date.';
  if(bits.length||manual)toast('Health sync: '+(bits.length?bits.join(', ')+(sum.xp?' · +'+sum.xp+' XP':''):'nothing new'));
 }catch(e){H.msg='Sync failed: '+String((e&&e.message)||e).slice(0,100)}
 finally{lmaHealthBusy=false;save();lmaHealthRender()}
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
