/* v204: Calendar import. Anything that lands in the phone's calendar (for example "Hey Gemini, add dentist tomorrow at 3 to my calendar")
   becomes a fixed commitment in the Life Director, which re-plans the rest of the day around it. Read only: LifeMax never writes to the calendar.
   Android only (the native CalendarSync plugin reads the calendar). Changes and deletions in the calendar are followed. */
var LMCAL={key:'lifemax_calsync_v1',days:14,busy:false,timer:null};
function lmcLoad(){try{const o=JSON.parse(localStorage.getItem(LMCAL.key)||'{}');return {on:!!o.on,ids:o.ids||{},last:o.last||0,msg:o.msg||'',n:o.n||0}}catch(e){return {on:false,ids:{},last:0,msg:'',n:0}}}
function lmcSave(s){try{localStorage.setItem(LMCAL.key,JSON.stringify(s))}catch(e){}}
function lmcApi(){try{return window.LifeDirector&&LifeDirector.voice||null}catch(e){return null}}
function lmcPlugin(){return typeof lmaPlugin==='function'?lmaPlugin('CalendarSync'):null}
/* which calendar events count: timed, not declined, not marathon-length, with a title */
function lmcKeep(e,now){
 if(!e||!e.title||!String(e.title).trim())return false;
 if(e.declined)return false;
 if(e.allDay)return false;                                  /* an all-day entry has no hours to plan around, so it is never imported as a timed block */
 if(!(e.end>e.start))return false;
 if(e.end-e.start>12*3600e3)return false;
 if(e.end<=now)return false;
 return true;
}
function lmcId(e){return 'cal_'+e.id+'_'+Math.floor(e.start/60000)}
async function lmcSync(manual){
 const S=lmcLoad();
 if(LMCAL.busy||(!S.on&&!manual))return null;
 const P=lmcPlugin();if(!P){return null}
 const api=lmcApi();
 LMCAL.busy=true;let res={added:0,removed:0,note:''};
 try{
  const st=await P.status();if(!st.granted){S.msg='Allow calendar access to import events.';lmcSave(S);return res}
  if(!api||!api.ready()){S.msg='Set up Life Director first, then events will come in.';lmcSave(S);return res}
  const now=Date.now(),r=await P.events({from:now-3600e3,to:now+LMCAL.days*86400e3}),list=(r&&r.events)||[],want={};
  list.forEach(function(e){if(lmcKeep(e,now))want[lmcId(e)]=e});
  const titles=[];
  Object.keys(want).forEach(function(id){
   if(S.ids[id])return;
   const e=want[id],out=api.add({id:id,title:String(e.title).trim().slice(0,80),startEpoch:Math.floor(e.start/1000),endEpoch:Math.floor(e.end/1000)});
   if(out&&out.ok){S.ids[id]={s:e.start,t:String(e.title).trim().slice(0,80)};res.added++;titles.push(S.ids[id].t)}
  });
  /* moved or deleted in the calendar: take the old block out (only future ones; past blocks stay as history) */
  Object.keys(S.ids).forEach(function(id){
   if(want[id])return;
   if(S.ids[id].s>now&&S.ids[id].s<now+LMCAL.days*86400e3){try{api.remove(id)}catch(e){}res.removed++}
   delete S.ids[id];
  });
  S.last=now;S.n=Object.keys(S.ids).length;S.msg='';lmcSave(S);
  if(res.added)try{toast(res.added===1?'Added “'+titles[0]+'” from your calendar':'Added '+res.added+' events from your calendar')}catch(e){}
 }catch(e){S.msg='Could not read the calendar: '+String((e&&e.message)||e).slice(0,100);lmcSave(S)}
 finally{LMCAL.busy=false}
 try{lmcRender()}catch(e){}
 return res;
}
async function lmcSet(k,v){
 const S=lmcLoad();S[k]=v;lmcSave(S);
 if(k==='on'&&v){
  const P=lmcPlugin();
  try{const g=await P.request();if(!g.granted){S.on=false;S.msg='Calendar access was not allowed.';lmcSave(S);lmcRender();return}}catch(e){S.on=false;lmcSave(S);lmcRender();return}
  await lmcSync(true);
 }
 if(k==='on'&&!v){/* leave the already imported events alone; they are ordinary commitments now */}
 lmcRender();
}
function lmcRender(){
 const el=document.getElementById('lmaCalBody');if(!el)return;
 if(!(typeof lmaNative==='function'&&lmaNative())){el.innerHTML='<div class="note">Calendar import is available in the Android app.</div>';return}
 const S=lmcLoad();
 let h='<div class="settingsline"><div style="flex:1"><strong>Import from my calendar</strong><div class="muted" style="font-size:12px">Events in your phone’s calendar become fixed blocks in your Life Director, and the rest of the day re-plans around them. Say “Hey Gemini, add dentist tomorrow at 3 PM to my calendar” and it shows up here. Read only.</div></div><input type="checkbox" class="check" '+(S.on?'checked ':'')+'onchange="lmcSet(\'on\',this.checked)" aria-label="Import from my calendar"></div>';
 if(S.msg)h+='<div class="note" style="margin-top:8px">'+esc(S.msg)+'</div>';
 h+='<div class="settingsline"><div style="flex:1"><strong>Last check</strong><div class="muted" style="font-size:12px">'+(S.last?(typeof lmaAgo==='function'?lmaAgo(S.last):new Date(S.last).toLocaleTimeString())+' · '+S.n+' event'+(S.n===1?'':'s')+' tracked, next '+LMCAL.days+' days':'Not yet')+'</div></div><button class="btn small" '+(S.on?'':'disabled ')+'onclick="lmcSync(true)">Check now</button></div>';
 el.innerHTML=h;
}
(function(){
 const nav=navigate;navigate=function(v){const r=nav.apply(this,arguments);if(v==='settings')try{lmcRender()}catch(e){}return r};
 document.addEventListener('visibilitychange',function(){if(document.visibilityState==='visible')setTimeout(function(){lmcSync(false)},800)});
 setInterval(function(){if(document.visibilityState==='visible')lmcSync(false)},5*60e3);
 setTimeout(function(){lmcRender();lmcSync(false)},4000);
 window.LifeMaxCalendar={keep:lmcKeep,id:lmcId,sync:lmcSync};
})();
