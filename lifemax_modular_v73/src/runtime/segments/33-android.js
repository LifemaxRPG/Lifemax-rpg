/* v88 · Android shell: system bars, smart reminders, and the bridge the Apps tab uses.
   In a normal browser everything here quietly does nothing (lmaNative() is false). */
const LMA_PREF_KEY='lifemax_android_prefs_v1';
const LMA_CHANNEL='lifemax_reminders';
const LMA_DEFAULTS={
 notif:{on:true,sessions:true,lead:15,workout:true,workoutTime:'17:00',nudge:true,checkin:false,checkinTime:'20:30',quiet:true,quietFrom:'22:00',quietTo:'07:00'},
 apps:[],
 backup:{on:false,uri:'',folder:'',days:1,keep:7,last:0,status:'',err:'',lastName:''}
};
const LMA_IN='width:auto;background:var(--panel2);color:var(--text);border:1px solid var(--line);border-radius:8px;padding:6px 8px;font:inherit';
let lmaPrefs=null,lmaPlugins={},lmaSyncTimer=0,lmaLastSig='',lmaSyncBusy=false;
function lmaNative(){try{return !!(window.Capacitor&&Capacitor.isNativePlatform&&Capacitor.isNativePlatform())}catch(e){return false}}
let lmaLastErr='';
/* Capacitor registers every native plugin itself, so use that proxy; registering again throws. */
function lmaPlugin(name){
 if(!lmaNative())return null;
 if(lmaPlugins[name])return lmaPlugins[name];
 try{
  const have=window.Capacitor.Plugins&&window.Capacitor.Plugins[name];
  const p=have||Capacitor.registerPlugin(name);
  if(p)lmaPlugins[name]=p;
  return p||null;
 }catch(e){lmaLastErr=String(e&&e.message||e);return null}
}
function lmaLoad(){
 if(lmaPrefs)return lmaPrefs;
 let raw=null;try{raw=JSON.parse(localStorage.getItem(LMA_PREF_KEY)||'null')}catch(e){}
 raw=raw&&typeof raw==='object'?raw:{};
 lmaPrefs={notif:Object.assign({},LMA_DEFAULTS.notif,raw.notif||{}),apps:Array.isArray(raw.apps)?raw.apps:[],pixel:raw.pixel!==false,swipe:raw.swipe!==false,updChecked:+raw.updChecked||0,backup:Object.assign({},LMA_DEFAULTS.backup,raw.backup||{})};
 return lmaPrefs;
}
function lmaSave(){try{localStorage.setItem(LMA_PREF_KEY,JSON.stringify(lmaLoad()))}catch(e){console.warn('LMA prefs not saved',e)}}
function lmaHM(s){const m=/^(\d{1,2}):(\d{2})$/.exec(s||'');return m?(+m[1])*60+(+m[2]):0}
function lmaAt(dateKey,hm){const m=lmaHM(hm),d=parseD(dateKey);d.setHours(Math.floor(m/60),m%60,0,0);return d}
function lmaQuiet(d){
 const n=lmaLoad().notif;if(!n.quiet)return false;
 const t=d.getHours()*60+d.getMinutes(),a=lmaHM(n.quietFrom),b=lmaHM(n.quietTo);
 return a===b?false:(a<b?(t>=a&&t<b):(t>=a||t<b));
}
function lmaFmtTime(d){return d.toLocaleTimeString(undefined,{hour:'numeric',minute:'2-digit'})}
/* Life Director's planned sessions: live from the feature if it has loaded, otherwise from its saved copy. */
function lmaDirectorSessions(){
 let sched=null;
 try{sched=window.LifeDirector&&LifeDirector.getSchedule()}catch(e){}
 if(!sched){try{const r=JSON.parse(localStorage.getItem('lifemax_director_state_v1')||'null');sched=r&&r.schedule}catch(e){}}
 return ((sched&&sched.scheduledInstances)||[]).filter(i=>i&&i.lifecycleState==='PLANNED'&&i.startEpoch).map(i=>({title:i.title||'Session',start:new Date(i.startEpoch*1000),end:new Date((i.endEpoch||i.startEpoch)*1000)}));
}
/* The list of notifications that should exist right now (next three days). */
function lmaWantedReminders(now){
 const n=lmaLoad().notif,out=[],horizon=new Date(now.getTime()+72*3600e3),today=getToday(),sess=lmaDirectorSessions();
 const push=(key,at,title,body)=>{if(at>now&&at<horizon&&!lmaQuiet(at))out.push({key,at,title,body})};
 if(n.sessions){
  for(const s of sess){
   const at=new Date(s.start.getTime()-n.lead*60e3);
   push('s|'+s.title+'|'+s.start.getTime(),at,s.title,n.lead>0?'Starts in '+n.lead+' min · '+lmaFmtTime(s.start):'Starting now');
  }
 }
 if(n.workout){
  for(let i=0;i<3;i++){
   const k=addDays(today,i);let d=null;
   try{d=planDay(k)}catch(e){}
   if(!d||d.kind==='rest')continue;
   let done=false;try{done=dayDone(k)}catch(e){}
   if(done)continue;
   const label=d.label||'Workout';
   /* Life Director already reminds about sessions it scheduled for this day, so don't double up. */
   const covered=n.sessions&&sess.some(s=>s.start.toLocaleDateString('en-CA')===k&&(s.title.indexOf(label)>=0||/workout|train|lift|strength|cardio|mobility|gym|run\b/i.test(s.title)));
   if(!covered)push('w|'+k,lmaAt(k,n.workoutTime),label+' today',(d.note||'Your plan has a session today.').slice(0,110));
   if(n.nudge&&i===0&&!covered)push('wn|'+k,lmaAt(k,'20:00'),'Still time for '+label,'Today’s session isn’t checked off yet.');
  }
 }
 if(n.checkin){
  for(let i=0;i<3;i++){
   const k=addDays(today,i);
   let logged=false;if(i===0){try{logged=(state.activities||[]).some(a=>String(a.date||'').slice(0,10)===k)}catch(e){}}
   if(!logged)push('c|'+k,lmaAt(k,n.checkinTime),'Daily check-in','Log what you did today and keep your streak going.');
  }
 }
 out.sort((a,b)=>a.at-b.at);
 return out.slice(0,40);
}
function lmaId(key){let h=2166136261;for(let i=0;i<key.length;i++){h^=key.charCodeAt(i);h=Math.imul(h,16777619)}return (h>>>0)%2000000000+1}
async function lmaPermission(ask){
 const LN=lmaPlugin('LocalNotifications');if(!LN)return 'unsupported';
 try{
  let p=await LN.checkPermissions();
  if(p.display==='prompt'||p.display==='prompt-with-rationale'){if(!ask)return 'prompt';p=await LN.requestPermissions()}
  return p.display;
 }catch(e){return 'unsupported'}
}
async function lmaSync(force){
 const LN=lmaPlugin('LocalNotifications');if(!LN||lmaSyncBusy)return;
 lmaSyncBusy=true;
 try{
  const n=lmaLoad().notif;
  const want=n.on?lmaWantedReminders(new Date()):[];
  const sig=want.map(w=>w.key+'@'+w.at.getTime()+w.title).join(';');
  const pend=(await LN.getPending()).notifications||[];
  if(!force&&sig===lmaLastSig&&pend.length===want.length)return;
  if(pend.length)await LN.cancel({notifications:pend.map(p=>({id:p.id}))});
  lmaLastSig=sig;
  if(!want.length)return;
  if(await lmaPermission(false)!=='granted')return;
  try{await LN.createChannel({id:LMA_CHANNEL,name:'Reminders',description:'Workouts, planned sessions and check-ins',importance:4,visibility:1})}catch(e){}
  await LN.schedule({notifications:want.map(w=>({id:lmaId(w.key),title:w.title,body:w.body,channelId:LMA_CHANNEL,schedule:{at:w.at,allowWhileIdle:true}}))});
 }catch(e){console.warn('Reminder sync failed',e)}
 finally{lmaSyncBusy=false}
}
function lmaSoon(ms){clearTimeout(lmaSyncTimer);lmaSyncTimer=setTimeout(()=>lmaSync(false),ms||2500)}
/* Colour the area behind the status and gesture bars to match the current theme. */
function lmaApplyBars(){
 const P=lmaPlugin('AppLauncher');if(!P)return;
 try{
  let c=getComputedStyle(document.body).backgroundColor;
  if(!c||c==='rgba(0, 0, 0, 0)'||c==='transparent')c=getComputedStyle(document.documentElement).backgroundColor;
  const m=/rgba?\((\d+),\s*(\d+),\s*(\d+)/.exec(c)||[0,12,14,18],r=+m[1],g=+m[2],b=+m[3];
  const hex='#'+[r,g,b].map(v=>v.toString(16).padStart(2,'0')).join(''),lum=(0.299*r+0.587*g+0.114*b)/255;
  P.setSystemBars({color:hex,light:lum>0.55}).catch(()=>{});
 }catch(e){}
}
/* ---------- Settings → Notifications card ---------- */
function lmaSet(path,val){
 const n=lmaLoad().notif;n[path]=val;lmaSave();lmaRenderSettings();
 if(path==='on'&&val)lmaPermission(true).then(()=>{lmaRenderSettings();lmaSync(true)});else lmaSync(true);
}
async function lmaTestNotification(){
 const LN=lmaPlugin('LocalNotifications');if(!LN){toast('Notifications work in the Android app');return}
 if(await lmaPermission(true)!=='granted'){toast('Allow notifications first');lmaRenderSettings();return}
 try{await LN.createChannel({id:LMA_CHANNEL,name:'Reminders',description:'Workouts, planned sessions and check-ins',importance:4,visibility:1})}catch(e){}
 await LN.schedule({notifications:[{id:1,title:'LifeMax RPG',body:'Reminders are working.',channelId:LMA_CHANNEL,schedule:{at:new Date(Date.now()+5000),allowWhileIdle:true}}]});
 toast('Test reminder in 5 seconds');
}
async function lmaExactTiming(){
 const LN=lmaPlugin('LocalNotifications');if(!LN)return;
 try{await LN.changeExactNotificationSetting()}catch(e){toast('Open Settings → Apps → Special access → Alarms & reminders')}
}
async function lmaRenderSettings(){
 const host=document.getElementById('lmaNotifBody');if(!host)return;
 const n=lmaLoad().notif;
 if(!lmaNative()){host.innerHTML='<div class="note">Reminders are available in the Android app. Install the APK and they will appear here.</div>';return}
 const perm=await lmaPermission(false);
 let exact='';
 try{const LN=lmaPlugin('LocalNotifications'),e=await LN.checkExactNotificationSetting();exact=e&&e.exact_alarm;}catch(e){}
 const row=(label,hint,ctl)=>'<div class="settingsline"><div style="flex:1"><strong>'+label+'</strong>'+(hint?'<div class="muted" style="font-size:12px">'+hint+'</div>':'')+'</div>'+ctl+'</div>';
 const tog=(k,dis)=>'<input type="checkbox" class="check" '+(n[k]?'checked ':'')+(dis?'disabled ':'')+'onchange="lmaSet(\''+k+'\',this.checked)" aria-label="'+k+'">';
 const time=(k)=>'<input type="time" value="'+n[k]+'" onchange="lmaSet(\''+k+'\',this.value)" style="'+LMA_IN+'">';
 const off=!n.on;
 let h='';
 if(perm==='denied')h+='<div class="note" style="margin-bottom:10px">Notifications are blocked for this app. Turn them on in Android Settings → Apps → LifeMax RPG → Notifications.</div>';
 else if(n.on&&perm!=='granted')h+='<div class="note" style="margin-bottom:10px">Android needs your permission first. <button class="btn small" onclick="lmaPermission(true).then(()=>{lmaRenderSettings();lmaSync(true)})">Allow notifications</button></div>';
 if(n.on&&perm==='granted'&&exact==='denied')h+='<div class="note" style="margin-bottom:10px">Without “Alarms &amp; reminders” access Android may delay reminders by several minutes. <button class="btn small secondary" onclick="lmaExactTiming()">Allow exact timing</button></div>';
 h+=row('Reminders','Master switch. Off cancels everything scheduled.',tog('on'));
 h+=row('Planned sessions','Life Director sessions, shortly before they start.',tog('sessions',off));
 h+=row('Reminder lead time','How early to nudge you.','<select onchange="lmaSet(\'lead\',+this.value)" '+(off||!n.sessions?'disabled':'')+' style="'+LMA_IN+'">'+[0,5,10,15,30,60].map(v=>'<option value="'+v+'"'+(n.lead===v?' selected':'')+'>'+(v?v+' min':'At start')+'</option>').join('')+'</select>');
 h+=row('Workout days','A reminder on days your workout plan has a session, until it is checked off. Skipped when Life Director already reminds you.',tog('workout',off));
 h+=row('Workout time','',time('workoutTime').replace('<input','<input '+(off||!n.workout?'disabled':'')));
 h+=row('Evening nudge','A second reminder at 8 PM if today’s workout is still open.',tog('nudge',off||!n.workout));
 h+=row('Daily check-in','A nightly prompt to log your day (skipped once you have logged something).',tog('checkin',off));
 h+=row('Check-in time','',time('checkinTime').replace('<input','<input '+(off||!n.checkin?'disabled':'')));
 h+=row('Quiet hours','No reminders in this window.',tog('quiet',off));
 h+='<div class="settingsline"><div style="flex:1"><strong>Quiet from / to</strong></div><div style="display:flex;gap:6px">'+time('quietFrom').replace('<input','<input '+(off||!n.quiet?'disabled':''))+time('quietTo').replace('<input','<input '+(off||!n.quiet?'disabled':''))+'</div></div>';
 h+='<div style="margin-top:12px"><button class="btn secondary" onclick="lmaTestNotification()">Send a test reminder</button></div>';
 h+='<div class="muted" style="font-size:12px;margin-top:8px">Reminders are rescheduled whenever you open the app, check something off or change your plan, and cover the next three days.</div>';
 host.innerHTML=h;
}
/* ---------- saving files and backups ---------- */
const LMA_EXTRA_KEYS=['lifemax_director_state_v1','lifemax_android_prefs_v1','lifeMaxTheme'];
function lmaBackupExtras(){const o={};for(const k of LMA_EXTRA_KEYS){try{let v=localStorage.getItem(k);if(typeof v==='string'){if(k==='lifemax_android_prefs_v1'){try{const j=JSON.parse(v);if(j&&j.backup){j.backup={...j.backup,uri:'',folder:'',on:false,last:0,status:''}}v=JSON.stringify(j)}catch(e){}}o[k]=v}}catch(e){}}return o}
function lmaRestoreExtras(x){if(!x||typeof x!=='object')return false;let n=0;for(const k of LMA_EXTRA_KEYS){if(typeof x[k]==='string'){try{localStorage.setItem(k,x[k]);n++}catch(e){}}}return n>0}
/* Save text as a file: Android's "Save as" picker in the app, a normal download in a browser. */
async function lmaDownload(text,filename,mime,label){
 const FS=lmaPlugin('FileSaver');
 if(FS){
  try{const r=await FS.saveFile({name:filename,mime:mime,text:text});toast(r&&r.saved?(label||'File')+' saved':'Save cancelled');return !!(r&&r.saved)}
  catch(e){toast('Could not save: '+String((e&&e.message)||e).slice(0,90));return false}
 }
 const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([text],{type:mime}));a.download=filename;a.click();
 setTimeout(()=>URL.revokeObjectURL(a.href),2000);toast((label||'File')+' exported');return true;
}
/* ---------- wiring ---------- */
(function lmaBoot(){
 const _save=save;save=function(){const r=_save.apply(this,arguments);lmaSoon();return r};
 const _nav=navigate;navigate=function(v){const r=_nav.apply(this,arguments);if(v==='settings')lmaRenderSettings();lmaSoon(4000);return r};
 const _light=setLightMode;setLightMode=function(){const r=_light.apply(this,arguments);setTimeout(lmaApplyBars,50);return r};
 document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible'){lmaApplyBars();lmaSoon(1500)}});
 setInterval(()=>lmaSoon(100),5*60e3);
 setTimeout(()=>{lmaRenderSettings();lmaApplyBars();lmaSync(false)},3000);
})();
