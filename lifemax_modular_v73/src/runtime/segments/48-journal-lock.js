/* ---------- Optional Journal lock. Turn it on in Settings; opening the Journal then asks for your fingerprint or screen lock.
   Android does the checking (nothing is stored here but the on/off choice). It locks again when you leave the app, or
   a minute after you leave the Journal. While the Journal is open the screen is hidden from the app switcher and
   screenshots, and tab previews never capture it. ---------- */
const JL={graceMs:60000};
let jlUnlocked=false,jlLeftAt=0,jlPrompting=false,jlPrevView='';
function jlOn(){return !!(typeof state!=='undefined'&&state.journalLock&&state.journalLock.on)}
function jlPlugin(){try{return typeof lmaNative==='function'&&lmaNative()?lmaPlugin('BiometricLock'):null}catch(e){return null}}
async function jlStatus(){const p=jlPlugin();if(!p)return {available:false,kind:'web'};try{return await p.status()}catch(e){return {available:false,kind:'unavailable'}}}
async function jlAuth(subtitle){const p=jlPlugin();if(!p)return {ok:false,error:'web'};try{return await p.authenticate({title:'Unlock your Journal',subtitle:subtitle||'Your reflections stay private.'})}catch(e){return {ok:false,error:String(e&&e.message||e)}}}
function jlSecure(on){const p=jlPlugin();if(p)try{p.secure({on:!!on})}catch(e){}}
function jlViewIsJournal(){try{return lmaView()==='history'}catch(e){return false}}
function jlSection(){return document.getElementById('view-history')}
function jlOverlay(){
 const s=jlSection();if(!s)return null;
 let o=document.getElementById('jlOverlay');
 if(!o){
  o=document.createElement('div');o.id='jlOverlay';o.className='jl-overlay';
  o.innerHTML='<div class="jl-card"><div class="jl-ico"><svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="5" y="11" width="14" height="9" rx="2.5"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/><circle cx="12" cy="15.5" r="1.2" fill="currentColor"/></svg></div><h3>Journal locked</h3><p>Your reflections are private. Unlock with your fingerprint or screen lock.</p><button type="button" class="btn" id="jlUnlockBtn">Unlock</button><div class="jl-err" id="jlErr"></div></div>';
  s.appendChild(o);
  o.querySelector('#jlUnlockBtn').addEventListener('click',function(){jlPrompt()});
 }
 return o;
}
/* show or hide the cover to match the lock state */
function jlApply(){
 const s=jlSection();if(!s)return;
 const locked=jlOn()&&!jlUnlocked;
 s.classList.toggle('jlocked',locked);
 if(jlOn())jlOverlay();
 const o=document.getElementById('jlOverlay');if(o)o.style.display=locked?'':'none';
 jlSecure(jlOn()&&jlViewIsJournal());
}
async function jlPrompt(){
 if(jlPrompting||!jlOn()||jlUnlocked)return;
 jlPrompting=true;const err=document.getElementById('jlErr');if(err)err.textContent='';
 const r=await jlAuth();jlPrompting=false;
 if(r&&r.ok){jlUnlocked=true;jlApply();try{renderHistory()}catch(e){}return true}
 const e2=document.getElementById('jlErr');if(e2)e2.textContent=r&&r.cancelled?'':'Could not unlock. Try again.';
 return false;
}
function jlRelock(){jlUnlocked=false;jlApply()}
function jlOnNavigate(v,prev){
 if(v==='history'){
  if(jlOn()){if(jlUnlocked&&Date.now()-jlLeftAt>JL.graceMs)jlUnlocked=false;jlApply();if(!jlUnlocked)jlPrompt()}
  else jlApply();
 }else{
  if(prev==='history'){jlLeftAt=Date.now()}
  jlSecure(false);
  const s=jlSection();if(s&&!jlOn())s.classList.remove('jlocked');
 }
}
/* Settings card */
function jlSettingsRender(){
 const el=document.getElementById('jlSettingsBody');if(!el)return;
 const native=typeof lmaNative==='function'&&lmaNative();
 el.innerHTML='<div class="settingsline"><div style="flex:1"><strong>Lock the Journal</strong><div class="muted" style="font-size:12.5px">'+(native?'Ask for your fingerprint or screen lock to open the Journal. It also hides the Journal from the app switcher and screenshots while it is open.':'Available in the Android app.')+'</div></div><input type="checkbox" class="check" id="jlToggle"'+(jlOn()?' checked':'')+(native?'':' disabled')+'></div><div class="muted" id="jlMsg" style="font-size:12.5px;margin-top:6px"></div>';
 const t=document.getElementById('jlToggle');if(!t)return;
 t.addEventListener('change',async function(){
  const msg=document.getElementById('jlMsg'),want=t.checked;msg.textContent='';
  if(want){
   const st=await jlStatus();
   if(!st.available){t.checked=false;msg.textContent=st.kind==='none_enrolled'?'Set up a fingerprint or screen lock in Android settings first, then try again.':'This phone cannot do a secure unlock right now.';return}
   const r=await jlAuth('Confirm it is you to turn the lock on.');
   if(!(r&&r.ok)){t.checked=false;return}
   state.journalLock={on:true};jlUnlocked=true;
   try{delete lmaThumbs.history;delete lmaThumbs.library;lmaThumbSave()}catch(e){}
   try{save()}catch(e){}msg.textContent='The Journal is locked.';
  }else{
   const r=await jlAuth('Confirm it is you to turn the lock off.');
   if(!(r&&r.ok)){t.checked=true;return}
   state.journalLock={on:false};try{save()}catch(e){}msg.textContent='The lock is off.';
  }
  jlApply();
 });
}
window.LifeMaxJournalLock=Object.freeze({on:jlOn,apply:jlApply,relock:jlRelock,prompt:jlPrompt,unlocked:function(){return jlUnlocked},render:jlSettingsRender,status:jlStatus});
(function jlBoot(){
 const nav=navigate;navigate=function(v){const prev=jlPrevView||(function(){try{return lmaView()}catch(e){return ''}})();const r=nav.apply(this,arguments);jlPrevView=v;try{jlOnNavigate(v,prev)}catch(e){console.warn('[lock]',e)}if(v==='settings')try{jlSettingsRender()}catch(e){}return r};
 document.addEventListener('visibilitychange',function(){if(document.visibilityState!=='visible'&&jlOn()){jlUnlocked=false;jlApply()}});
 setTimeout(function(){try{jlApply();jlSettingsRender();if(jlOn()&&jlViewIsJournal())jlPrompt()}catch(e){console.warn('[lock]',e)}},1800);
})();
