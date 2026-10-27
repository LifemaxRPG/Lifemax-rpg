/* ---------- Voice fallback log: what the voice engine could not understand, kept on this device so it can be exported and the
   engine taught. A sentence is logged when it matches no intent at all, or when the recogniser itself was unsure (confidence below
   0.55). Settings > Voice (beta) exports the list as a text file. Nothing leaves the phone unless you save and share the file. ---------- */
const VFB={key:'lifemax_voice_fallback_logs_v1',max:200,low:.55};
const VoiceFallbackLogger={
 getLogs:function(){try{const a=JSON.parse(localStorage.getItem(VFB.key)||'[]');return Array.isArray(a)?a:[]}catch(e){return []}},
 logFallback:function(rawTranscript,currentScreen,sessionId){
  const t=String(rawTranscript==null?'':rawTranscript).replace(/\s+/g,' ').trim().slice(0,300);if(!t)return null;
  const now=Date.now(),e={transcript:t,screen:String(currentScreen||'unknown'),sessionId:String(sessionId||''),timestamp:now,dateStr:new Date(now).toISOString()};
  const a=VoiceFallbackLogger.getLogs();a.push(e);
  try{localStorage.setItem(VFB.key,JSON.stringify(a.slice(-VFB.max)))}catch(x){return null}
  return e;
 },
 clearLogs:function(){try{localStorage.removeItem(VFB.key)}catch(e){}},
 exportLogsAsText:function(){
  const a=VoiceFallbackLogger.getLogs(),L=['=== LIFEMAX VOICE FALLBACK REPORT ===','Exported: '+new Date().toLocaleString(),'Total Unresolved Queries: '+a.length,'----------------------------------------'];
  a.forEach(function(e,i){L.push((i+1)+'. "'+e.transcript+'" | Screen: '+e.screen+' | Time: '+new Date(e.timestamp).toLocaleString())});
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
  try{
   if(dup||!lmvEnabled())return r;
   const clean=String(text==null?'':text);if(!clean.trim())return r;
   let miss=false;
   if(meta.confidence!=null&&meta.confidence<VFB.low)miss=true;
   if(r&&r.intentId==='unknown_query')miss=true;
   if(!miss&&!wasActive){const c=lmvClassify(lmvPhon(clean));if(c&&c.id==='life_director.create_activity'&&c.conf<VFB.low&&r&&r.state!=='CONFIRMED'&&r.state!=='PREVIEWED')miss=true}
   if(miss)VoiceFallbackLogger.logFallback(clean,typeof lmaView==='function'?(g5Screen?g5Screen():lmaView()):'',lmvBoot);
  }catch(e){console.warn('[voice fallback]',e)}
  return r;
 };
 const sr=lmvSettingsRender;
 lmvSettingsRender=function(){
  sr.apply(this,arguments);
  const el=document.getElementById('lmvSettingsBody');if(!el)return;
  const n=VoiceFallbackLogger.getLogs().length,row=lmvEl('div','settingsline'),left=lmvEl('div');left.style.flex='1';
  left.appendChild(lmvEl('strong','','Export Voice Fallback Log'));
  const sub=lmvEl('div','muted',n?n+' thing'+(n===1?'':'s')+' the voice engine did not understand are saved on this device. Export them as a text file to share.':'Nothing yet. Anything the voice engine does not understand will be saved here.');sub.style.fontSize='12px';left.appendChild(sub);
  const ex=lmvEl('button','btn small','Export');ex.type='button';ex.disabled=!n;ex.addEventListener('click',function(){VoiceFallbackLogger.exportFile()});
  const cl=lmvEl('button','btn small secondary','Clear');cl.type='button';cl.disabled=!n;cl.style.marginLeft='6px';cl.addEventListener('click',function(){VoiceFallbackLogger.clearLogs();lmvSettingsRender()});
  row.appendChild(left);row.appendChild(ex);row.appendChild(cl);el.appendChild(row);
 };
 window.VoiceFallbackLogger=Object.freeze(VoiceFallbackLogger);
})();
