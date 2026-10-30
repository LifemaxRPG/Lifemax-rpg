/* v91 · Android upkeep: in-app updates and automatic backups to a folder you choose.
   Everything here needs the Android shell; in a browser the Settings cards just say so. */
let lmaUpdTries=0,lmaUpd={state:'idle',info:null,pct:0,err:'',autoErr:''},lmaBakBusy=false,lmaUpdBound=false;
const lmaSetHtml=(id,h)=>{const el=document.getElementById(id);if(el)el.innerHTML=h};
const lmaAgo=t=>{if(!t)return 'never';const m=Math.round((Date.now()-t)/60000);return m<1?'just now':m<60?m+' min ago':m<1440?Math.round(m/60)+' h ago':Math.round(m/1440)+' days ago'};
/* ---------- updates ---------- */
/* What a new release needs: 'apk' (the Android shell changed), 'web' (only the app itself changed: download it, no install), or 'none'. */
function lmaUpdPlan(r,st){
 const latest=Number(r.latest),current=Number(st&&st.installedBuild)||Number(r.current);
 if(!Number.isFinite(latest)||latest<=0||!Number.isFinite(current)||current<=0)throw Error('Could not verify the installed and latest APK versions. Try checking again.');
 if(latest<Math.max(current,Number(st&&st.activeBuild)||0,Number(st&&st.staged)||0))throw Error('The server returned older release information than this app has already seen. Try checking again shortly.');
 const apkNewer=latest>current&&typeof r.apk==='string'&&r.apk.startsWith('https://');
 const native=Number(st&&st.nativeApi)||Number(r.nativeApi)||0;
 const required=Math.max(Number(r.latestNativeApi)||0,Number(r.webMinApi)||0);
 if(required&&native&&required>native){
  if(apkNewer)return 'apk';
  throw Error('This release requires a newer Android app, but no newer installable APK was provided.');
 }
 if(!st||!required||!Number(r.webBuild)||!native)return apkNewer?'apk':'none';
 if(Number(r.webBuild)>Math.max(current,Number(st.activeBuild)||0)){
  if(!r.webUrl||!/^https:\/\//.test(r.webUrl)||!r.webSha||!/^[a-f0-9]{64}$/i.test(r.webSha))throw Error('The web update is missing its download details. Try checking again.');
  return 'web';
 }
 return 'none';
}
async function lmaCheckUpdate(silent){
 const U=lmaPlugin('Updater');if(!U)return;
 if(['checking','downloading','webdl'].indexOf(lmaUpd.state)>=0)return;
 lmaUpd.state='checking';lmaUpd.err='';lmaRenderUpdates();
 try{
  const r=await U.check();lmaUpd.info=r;
  let st=null;try{const L=lmaPlugin('LiveWeb');st=L?await L.status():null}catch(e){}
  lmaUpd.live=st;lmaUpd.plan=lmaUpdPlan(r,st);
  lmaUpd.state=lmaUpd.plan==='apk'?'available':lmaUpd.plan==='web'?'web':'latest';
  lmaLoad().updChecked=Date.now();lmaSave();lmaUpd.autoErr='';lmaUpdTries=0;
  if(lmaUpd.plan==='web'&&(silent||lmaLoad().webAuto!==false)){await lmaWebUpdate();return}
  if(lmaUpd.state==='available')lmaUpdPopOpen();
 }catch(e){lmaUpd.state='error';lmaUpd.err=String((e&&e.message)||e).slice(0,140);if(silent){lmaUpd.state='idle';lmaUpd.autoErr=lmaUpd.err;if(lmaUpdTries<3)setTimeout(()=>lmaCheckUpdate(true),[15000,45000,120000][lmaUpdTries++])}}
 lmaRenderUpdates();
}
/* a web-only update: download it quietly, check it, and keep it ready (it starts on the next launch, or now if you tap Restart) */
async function lmaWebUpdate(){
 const L=lmaPlugin('LiveWeb'),i=lmaUpd.info;if(!L||!i||lmaUpd.state==='webdl')return;   /* one download at a time; native refuses a second too */
 if(lmaUpd.live&&lmaUpd.live.staged===i.webBuild){lmaUpd.state='webready';lmaRenderUpdates();lmaWebPill();return}
 lmaUpd.state='webdl';lmaUpd.pct=0;lmaRenderUpdates();
 try{
  if(!lmaUpdBound){lmaUpdBound=true}
  try{L.addListener('progress',e=>{lmaUpd.pct=e&&e.pct>=0?e.pct:-1;lmaRenderUpdates()})}catch(e){}
  await L.fetch({url:i.webUrl,sha256:i.webSha,build:i.webBuild});
  lmaUpd.live=await L.status();lmaUpd.state='webready';lmaWebPill();
 }catch(e){lmaUpd.state='error';lmaUpd.err=String((e&&e.message)||e).slice(0,140)}
 lmaRenderUpdates();
}
async function lmaWebRestart(){
 const L=lmaPlugin('LiveWeb');if(!L)return;
 try{await L.restart()}catch(e){lmaUpd.state='error';lmaUpd.err=String((e&&e.message)||e).slice(0,140);lmaRenderUpdates()}
}
/* a small pill: the update is downloaded, restart whenever you like */
function lmaWebPill(){
 if(document.querySelector('.lmaweb-pill')||lmaUpd.pillGone)return;
 const o=document.createElement('div');o.className='lmaweb-pill';o.setAttribute('role','status');
 o.innerHTML='<span>Update ready</span><button type="button" data-w="go">Restart</button><button type="button" data-w="x" aria-label="Dismiss">✕</button>';
 o.addEventListener('click',e=>{const b=e.target.closest('[data-w]');if(!b)return;if(b.dataset.w==='go')lmaWebRestart();else{lmaUpd.pillGone=true;o.remove()}});
 document.body.appendChild(o);setTimeout(()=>{if(o.parentNode)o.remove()},20000);
}
async function lmaInstallUpdate(){
 const U=lmaPlugin('Updater');if(!U||!lmaUpd.info||lmaUpd.state==='downloading')return;
 if(!lmaUpdBound){lmaUpdBound=true;try{U.addListener('progress',e=>{lmaUpd.pct=e&&e.pct>=0?e.pct:-1;lmaRenderUpdates()})}catch(e){}}
 lmaUpd.state='downloading';lmaUpd.pct=0;lmaRenderUpdates();
 try{
  const r=await U.install({url:lmaUpd.info.apk});
  lmaUpd.state=r&&r.needsPermission?'permission':'opened';
 }catch(e){lmaUpd.state='error';lmaUpd.err=String((e&&e.message)||e).slice(0,140)}
 lmaRenderUpdates();
}
/* the pop-up offered at launch when a newer build exists; "Later" hides it until the app is next opened */
let lmaUpdLater=0;
function lmaUpdPopOpen(){
 const i=lmaUpd.info;if(!i||lmaUpdLater===i.latest||document.querySelector('.updpop'))return;
 if(document.querySelector('#onboarding.show,#tutorial.show')){setTimeout(lmaUpdPopOpen,15000);return}
 const o=document.createElement('div');o.className='updpop';o.setAttribute('role','dialog');o.setAttribute('aria-label','Update available');
 o.addEventListener('click',e=>{const b=e.target.closest('[data-up]');if(!b)return;
  if(b.dataset.up==='later'){lmaUpdLater=lmaUpd.info.latest;o.remove()}
  else if(b.dataset.up==='go')lmaInstallUpdate();
  else if(b.dataset.up==='close')o.remove()});
 document.body.appendChild(o);lmaUpdPopRender();
}
function lmaUpdPopRender(){
 const o=document.querySelector('.updpop');if(!o)return;
 const u=lmaUpd,i=u.info||{};
 let body,btns;
 if(u.state==='downloading'){body='Downloading… '+(u.pct>=0?u.pct+'%':'');btns='<div class="updpop-bar"><i style="width:'+Math.max(4,u.pct)+'%"></i></div>'}
 else if(u.state==='opened'){body='Android’s installer is open. Tap Update there to finish.';btns='<button class="btn" data-up="close">OK</button>'}
 else if(u.state==='permission'){body='Allow “Install unknown apps” for LifeMax on the screen that just opened, come back, and tap Update again.';btns='<button class="btn" data-up="go">Update</button><button class="btn secondary" data-up="later">Later</button>'}
 else if(u.state==='error'){body='Could not update: '+esc(u.err);btns='<button class="btn" data-up="go">Try again</button><button class="btn secondary" data-up="later">Later</button>'}
 else{body='A newer build of LifeMax (build '+esc(i.latest)+') is ready. You have build '+esc(i.current)+'. Your data stays as it is.';btns='<button class="btn" data-up="go">Update now</button><button class="btn secondary" data-up="later">Later</button>'}
 o.innerHTML='<div class="updpop-card"><h3>Update available</h3><p>'+body+'</p><div class="updpop-btns">'+btns+'</div></div>';
}
function lmaRenderUpdates(){
 try{lmaUpdPopRender()}catch(e){}
 if(!document.getElementById('lmaUpdBody'))return;
 if(!lmaNative()){lmaSetHtml('lmaUpdBody','<div class="note">Updates are managed inside the Android app.</div>');return}
 const u=lmaUpd,i=u.info,cur=i?i.current:'';
 const live=u.live&&u.live.activeBuild?' · app updated over the air to build '+u.live.activeBuild:'';
 const native=i&&(Number(u.live&&u.live.nativeApi)||Number(i.nativeApi)),required=i&&Math.max(Number(i.latestNativeApi)||0,Number(i.webMinApi)||0);
 let h='<div class="settingsline"><div style="flex:1"><strong>'+(i?'Installed build '+cur:'Installed build')+live+'</strong><div class="muted" style="font-size:12px">';
 h+=u.state==='checking'?'Checking…':u.state==='latest'?'No newer compatible update was reported by the server.':u.state==='web'?'A web update is available.':u.state==='webdl'?'Downloading the update… '+(u.pct>=0?u.pct+'%':''):u.state==='webready'?'Update downloaded. It starts next time you open the app, or tap Restart.':u.state==='available'?'Build '+i.latest+' is ready to install.':u.state==='downloading'?'Downloading… '+(u.pct>=0?u.pct+'%':''):u.state==='permission'?'Allow “Install unknown apps” for LifeMax on the screen that just opened, come back, and tap Update again.':u.state==='opened'?'Android’s installer is open. Tap Update there.':u.state==='error'?'Could not check: '+esc(u.err):'Last checked '+lmaAgo(lmaLoad().updChecked)+'.'+(u.autoErr?' The check at launch failed: '+esc(u.autoErr):'');
 h+='</div>'+(i?'<div class="muted" style="font-size:12px">Latest release: '+esc(i.latest)+(native?' · Android API '+native+(required?' / requires '+required:''):'')+'</div>':'')+'</div>';
 if(u.state==='available'||u.state==='permission')h+='<button class="btn" onclick="lmaInstallUpdate()">Update</button>';
 else if(u.state==='webready')h+='<button class="btn" onclick="lmaWebRestart()">Restart</button>';
 else if(u.state==='web')h+='<button class="btn" onclick="lmaWebUpdate()">Download</button>';
 else if(u.state!=='checking'&&u.state!=='downloading'&&u.state!=='webdl')h+='<button class="btn secondary" onclick="lmaCheckUpdate(false)">Check now</button>';
 h+='</div>';
 if(i&&i.stable===false&&u.state==='available')h+='<div class="note" style="margin-top:8px">This build was signed with a temporary key, so Android will likely refuse to install it over your current app. See ANDROID.md.</div>';
 lmaSetHtml('lmaUpdBody',h);
}
/* ---------- automatic backup ---------- */
function lmaBackupDue(){const b=lmaLoad().backup;return !!(b.on&&b.uri&&Date.now()-b.last>=b.days*86400e3-60e3)}
async function lmaPickBackupFolder(){
 const B=lmaPlugin('BackupFolder');if(!B)return;
 try{
  const r=await B.pickFolder();if(!r||!r.picked)return;
  const b=lmaLoad().backup;b.uri=r.uri;b.folder=r.name;b.on=true;b.status='';b.err='';lmaSave();lmaRenderBackup();
  lmaRunBackup(true);
 }catch(e){toast('Could not use that folder')}
}
async function lmaRunBackup(manual){
 const B=lmaPlugin('BackupFolder'),b=lmaLoad().backup;
 if(!B||!b.uri){if(manual)toast('Choose a folder first');return}
 if(lmaBakBusy)return;lmaBakBusy=true;
 try{
  const d=new Date(),stamp=d.toLocaleDateString('en-CA')+'-'+String(d.getHours()).padStart(2,'0')+String(d.getMinutes()).padStart(2,'0');
  const r=await B.writeBackup({uri:b.uri,name:'life-max-auto-'+stamp+'.json',text:backupJson(),prefix:'life-max-auto-',keep:b.keep});
  b.last=Date.now();b.status='ok';b.err='';b.lastName=r&&r.name||'';if(manual)toast('Backup saved');
 }catch(e){const m=String((e&&e.message)||e);b.status=m.indexOf('ACCESS_LOST')>=0?'lost':'error';b.err=m.slice(0,120);if(manual)toast(b.status==='lost'?'Choose the folder again':'Backup failed')}
 lmaBakBusy=false;lmaSave();lmaRenderBackup();
}
function lmaSetBackup(k,v){const b=lmaLoad().backup;b[k]=v;lmaSave();lmaRenderBackup();if(k==='on'&&v&&b.uri&&!b.last)lmaRunBackup(true)}
function lmaRenderBackup(){
 if(!document.getElementById('lmaBakBody'))return;
 if(!lmaNative()){lmaSetHtml('lmaBakBody','<div class="note">Automatic backups are available in the Android app.</div>');return}
 const b=lmaLoad().backup,off=!b.uri;
 const row=(l,hint,ctl)=>'<div class="settingsline"><div style="flex:1"><strong>'+l+'</strong>'+(hint?'<div class="muted" style="font-size:12px">'+hint+'</div>':'')+'</div>'+ctl+'</div>';
 const sel=(k,opts,dis)=>'<select '+(dis?'disabled ':'')+'onchange="lmaSetBackup(\''+k+'\',+this.value)" style="'+LMA_IN+'">'+opts.map(o=>'<option value="'+o[0]+'"'+(b[k]===o[0]?' selected':'')+'>'+o[1]+'</option>').join('')+'</select>';
 let h='';
 if(b.status==='lost')h+='<div class="note" style="margin-bottom:10px">Android no longer lets LifeMax write to that folder. Choose it again.</div>';
 else if(b.status==='error')h+='<div class="note" style="margin-bottom:10px">The last backup failed: '+esc(b.err)+'</div>';
 h+=row('Automatic backup','Saves a backup file into a folder you pick, when you leave the app and one is due.','<input type="checkbox" class="check" '+(b.on&&!off?'checked ':'')+(off?'disabled ':'')+'onchange="lmaSetBackup(\'on\',this.checked)" aria-label="Automatic backup">');
 h+=row('Folder',off?'Nothing chosen yet.':esc(b.folder||'Chosen'),'<button class="btn small secondary" onclick="lmaPickBackupFolder()">'+(off?'Choose folder':'Change')+'</button>');
 h+=row('How often','',sel('days',[[1,'Daily'],[3,'Every 3 days'],[7,'Weekly']],off));
 h+=row('Keep the newest','Older automatic backups are deleted.',sel('keep',[[3,'3 files'],[7,'7 files'],[14,'14 files'],[30,'30 files']],off));
 h+='<div class="settingsline"><div style="flex:1"><strong>Last backup</strong><div class="muted" style="font-size:12px">'+(b.last?lmaAgo(b.last)+(b.lastName?' · '+esc(b.lastName):''):'None yet')+'</div></div><button class="btn small" '+(off||lmaBakBusy?'disabled ':'')+'onclick="lmaRunBackup(true)">'+(lmaBakBusy?'Saving…':'Back up now')+'</button></div>';
 lmaSetHtml('lmaBakBody',h);
}
/* ---------- wiring ---------- */
(function lmaUpkeepBoot(){
 const _nav=navigate;navigate=function(v){const r=_nav.apply(this,arguments);if(v==='settings'){lmaRenderUpdates();lmaRenderBackup()}return r};
 document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='hidden'&&lmaNative()&&lmaBackupDue())lmaRunBackup(false)});
 setInterval(()=>{if(lmaNative()&&lmaBackupDue())lmaRunBackup(false)},6*3600e3);
 setTimeout(()=>{
  lmaRenderUpdates();lmaRenderBackup();
  if(!lmaNative())return;
  if(lmaBackupDue())lmaRunBackup(false);
  lmaCheckUpdate(true);
 },4000);
 document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible'&&lmaNative()&&Date.now()-(lmaLoad().updChecked||0)>30*60e3)lmaCheckUpdate(true)});
})();
