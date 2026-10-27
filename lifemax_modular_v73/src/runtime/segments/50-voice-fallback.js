/* ---------- Voice fallback log: what the voice engine could not understand, kept on this device so it can be exported and the
   engine taught. A sentence is logged when it matches no intent at all, or when the recogniser itself was unsure (confidence below
   0.55). Settings > Voice (beta) exports the list as a text file. Nothing leaves the phone unless you save and share the file. ---------- */
const VFB={key:'lifemax_voice_fallback_logs_v1',likes:'lifemax_voice_ratings_v1',max:200,maxLikes:100,low:.55};
const VoiceFallbackLogger={
 getLogs:function(){try{const a=JSON.parse(localStorage.getItem(VFB.key)||'[]');return Array.isArray(a)?a:[]}catch(e){return []}},
 logFallback:function(rawTranscript,currentScreen,sessionId,extra){
  const t=String(rawTranscript==null?'':rawTranscript).replace(/\s+/g,' ').trim().slice(0,300);if(!t)return null;
  const now=Date.now(),e={transcript:t,screen:String(currentScreen||'unknown'),sessionId:String(sessionId||''),timestamp:now,dateStr:new Date(now).toISOString(),kind:'auto'};
  if(extra&&typeof extra==='object')Object.keys(extra).forEach(function(k){if(extra[k]!=null&&extra[k]!=='')e[k]=extra[k]});
  const a=VoiceFallbackLogger.getLogs();a.push(e);
  try{localStorage.setItem(VFB.key,JSON.stringify(a.slice(-VFB.max)))}catch(x){return null}
  return e;
 },
 clearLogs:function(){try{localStorage.removeItem(VFB.key);localStorage.removeItem(VFB.likes)}catch(e){}},
 getLikes:function(){try{const a=JSON.parse(localStorage.getItem(VFB.likes)||'[]');return Array.isArray(a)?a:[]}catch(e){return []}},
 /* a thumbs up or down on one response; a thumbs down becomes a log entry (or is added to the automatic one for the same sentence) */
 rate:function(rating,info){
  const t=String(info.transcript||'').replace(/\s+/g,' ').trim().slice(0,300);if(!t)return null;
  const base={intent:info.intent||'',response:String(info.response||'').slice(0,160)};
  if(rating==='up'){const a=VoiceFallbackLogger.getLikes();a.push({transcript:t,screen:String(info.screen||'unknown'),sessionId:String(info.sessionId||''),timestamp:Date.now(),dateStr:new Date().toISOString(),intent:base.intent,response:base.response});try{localStorage.setItem(VFB.likes,JSON.stringify(a.slice(-VFB.maxLikes)))}catch(e){}return a[a.length-1]}
  const comment=String(info.comment||'').replace(/\s+/g,' ').trim().slice(0,300),all=VoiceFallbackLogger.getLogs(),now=Date.now();
  for(let i=all.length-1;i>=0&&i>all.length-6;i--){const e=all[i];
   if(e.transcript===t&&e.sessionId===String(info.sessionId||'')&&now-e.timestamp<5*60000&&e.kind!=='thumbs-down'){e.kind='auto+thumbs-down';if(comment)e.comment=comment;if(base.intent)e.intent=base.intent;if(base.response)e.response=base.response;try{localStorage.setItem(VFB.key,JSON.stringify(all))}catch(x){}return e}}
  return VoiceFallbackLogger.logFallback(t,info.screen,info.sessionId,Object.assign({kind:'thumbs-down',comment:comment},base));
 },
 exportLogsAsText:function(){
  const a=VoiceFallbackLogger.getLogs(),L=['=== LIFEMAX VOICE FALLBACK REPORT ===','Exported: '+new Date().toLocaleString(),'Total Unresolved Queries: '+a.length,'----------------------------------------'];
  a.forEach(function(e,i){
   let line=(i+1)+'. "'+e.transcript+'" | Screen: '+e.screen+' | Time: '+new Date(e.timestamp).toLocaleString();
   if(e.kind==='thumbs-down'||e.kind==='auto+thumbs-down')line+=' | Rated: thumbs down'+(e.comment?' | Comment: "'+e.comment+'"':'');
   if(e.intent)line+=' | Heard as: '+e.intent;if(e.response)line+=' | Reply: "'+e.response+'"';
   L.push(line)});
  const up=VoiceFallbackLogger.getLikes();
  if(up.length){L.push('','--- Thumbs up ('+up.length+') ---');up.forEach(function(e,i){L.push((i+1)+'. "'+e.transcript+'" | Screen: '+e.screen+(e.intent?' | Heard as: '+e.intent:'')+(e.response?' | Reply: "'+e.response+'"':''))})}
  return L.join('\n')+'\n';
 },
 exportFile:function(){
  const d=new Date().toLocaleDateString('en-CA'),txt=VoiceFallbackLogger.exportLogsAsText();
  if(typeof lmaDownload==='function')return lmaDownload(txt,'lifemax-voice-fallbacks-'+d+'.txt','text/plain','Voice fallback log');
  const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([txt],{type:'text/plain'}));a.download='lifemax-voice-fallbacks-'+d+'.txt';document.body.appendChild(a);a.click();setTimeout(function(){URL.revokeObjectURL(a.href);a.remove()},1500);
 }
};
(function(){
 const prev=lmvHandle;
 lmvHandle=function(text,meta){
  meta=meta||{};
  const dup=!!(meta.utteranceId&&lmvDelivered.has(meta.utteranceId)),f0=lmvFrame,wasActive=!!(f0&&(f0.state==='AWAITING_CLARIFICATION'||f0.state==='PREVIEWED'));
  const r=prev.apply(this,arguments);
  try{if(!dup&&lmvFrame&&!lmvFrame.said&&String(text||'').trim())lmvFrame.said=String(text).trim()}catch(e){}
  try{
   if(dup||!lmvEnabled())return r;
   const clean=String(text==null?'':text);if(!clean.trim())return r;
   let miss=false;
   if(meta.confidence!=null&&meta.confidence<VFB.low)miss=true;
   if(r&&r.intentId==='unknown_query')miss=true;
   if(!miss&&!wasActive){const c=lmvClassify(lmvPhon(clean));if(c&&c.id==='life_director.create_activity'&&c.conf<VFB.low&&r&&r.state!=='CONFIRMED'&&r.state!=='PREVIEWED')miss=true}
   if(miss)VoiceFallbackLogger.logFallback(clean,typeof lmaView==='function'?(g5Screen?g5Screen():lmaView()):'',lmvBoot,{intent:r&&r.intentId||'',response:r&&r.title||''});
  }catch(e){console.warn('[voice fallback]',e)}
  return r;
 };
 /* thumbs up / down under every answer */
 const SVG_UP='<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M7 11v9H4v-9h3zM7 11l4-8c1.5 0 2.5 1 2.5 2.500V9H19a2 2 0 0 1 2 2.300l-1.200 6.500A2 2 0 0 1 17.800 20H7"/></svg>';
 const SVG_DOWN=SVG_UP.replace('<svg ','<svg style="transform:rotate(180deg)" ');
 const info=function(f){return {transcript:f.said||f.transcript,intent:f.intentId||'',response:f.title||f.head||'',screen:typeof lmaView==='function'?(typeof g5Screen==='function'?g5Screen():lmaView()):'',sessionId:lmvBoot}};
 const fbRow=function(f){
  const box=lmvEl('div','lmv-fb'),st=f.fb||{};
  const msg=function(t){box.appendChild(lmvEl('span','lmv-fb-msg',t))};
  if(st.mode==='up'){msg('Thanks. Marked as a good answer.');return box}
  if(st.mode==='down-done'){msg(st.comment?'Thanks. Your note was saved. Export it from Settings.':'Thanks. Saved. Export it from Settings.');return box}
  if(st.mode==='ask'){
   const ta=lmvEl('textarea','lmv-fb-text');ta.rows=2;ta.maxLength=300;ta.placeholder='What went wrong? (optional)';ta.value=st.draft||'';ta.setAttribute('aria-label','Why was this answer not good? Optional');
   ta.addEventListener('input',function(){f.fb.draft=ta.value});box.appendChild(ta);
   const row=lmvEl('div','lmv-fb-btns'),send=lmvEl('button','lmv-chip lmv-go','Send'),skip=lmvEl('button','lmv-chip lmv-ghost','Skip comment');send.type=skip.type='button';
   const done=function(c){const i=info(f);i.comment=c;VoiceFallbackLogger.rate('down',i);f.fb={mode:'down-done',comment:c};lmvRender()};
   send.addEventListener('click',function(){done(ta.value)});skip.addEventListener('click',function(){done('')});row.appendChild(send);row.appendChild(skip);box.appendChild(row);
   if(st.focus){st.focus=false;setTimeout(function(){try{ta.focus()}catch(e){}},30)}
   return box;
  }
  box.appendChild(lmvEl('span','lmv-fb-q','Was this right?'));
  const up=lmvEl('button','lmv-fb-btn');up.type='button';up.setAttribute('aria-label','Thumbs up');up.innerHTML=SVG_UP;
  const dn=lmvEl('button','lmv-fb-btn');dn.type='button';dn.setAttribute('aria-label','Thumbs down');dn.innerHTML=SVG_DOWN;
  up.addEventListener('click',function(){VoiceFallbackLogger.rate('up',info(f));f.fb={mode:'up'};lmvRender()});
  dn.addEventListener('click',function(){f.fb={mode:'ask',draft:'',focus:true};lmvRender()});
  box.appendChild(up);box.appendChild(dn);return box;
 };
 const rr=lmvRender;
 lmvRender=function(){
  rr.apply(this,arguments);
  try{const f=lmvFrame,hud=lmvUI&&lmvUI.hud;
   if(!f||!hud||!hud.firstChild||lmvListening||!f.transcript)return;
   if(['PREVIEWED','CONFIRMED','FAILED','AWAITING_CLARIFICATION','CANCELLED'].indexOf(f.state)<0)return;
   hud.appendChild(fbRow(f));
  }catch(e){console.warn('[voice feedback]',e)}
 };
 const sr=lmvSettingsRender;
 lmvSettingsRender=function(){
  sr.apply(this,arguments);
  const el=document.getElementById('lmvSettingsBody');if(!el)return;
  const n=VoiceFallbackLogger.getLogs().length,nu=VoiceFallbackLogger.getLikes().length,row=lmvEl('div','settingsline'),left=lmvEl('div');left.style.flex='1';
  left.appendChild(lmvEl('strong','','Export Voice Fallback Log'));
  const sub=lmvEl('div','muted',n||nu?n+' flagged answer'+(n===1?'':'s')+' (not understood, or marked thumbs down)'+(nu?' and '+nu+' marked good':'')+' are saved on this device. Export them as a text file to share.':'Nothing yet. Anything the voice engine does not understand, and every thumbs up or down you give an answer, is saved here.');sub.style.fontSize='12px';left.appendChild(sub);
  const ex=lmvEl('button','btn small','Export');ex.type='button';ex.disabled=!(n||nu);ex.addEventListener('click',function(){VoiceFallbackLogger.exportFile()});
  const cl=lmvEl('button','btn small secondary','Clear');cl.type='button';cl.disabled=!(n||nu);cl.style.marginLeft='6px';cl.addEventListener('click',function(){VoiceFallbackLogger.clearLogs();lmvSettingsRender()});
  row.appendChild(left);row.appendChild(ex);row.appendChild(cl);el.appendChild(row);
 };
 window.VoiceFallbackLogger=Object.freeze(VoiceFallbackLogger);
})();
