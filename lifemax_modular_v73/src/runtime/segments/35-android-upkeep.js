/* v91 · Android upkeep: in-app updates and automatic backups to a folder you choose.
   Everything here needs the Android shell; in a browser the Settings cards just say so. */
let lmaUpdTries=0,lmaUpd={state:'idle',info:null,pct:0,err:'',autoErr:''},lmaBakRun=null,lmaUpdBound=false;
const LMA_UPD_BUSY=['checking','backup','downloading','webdl'];
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
 if(LMA_UPD_BUSY.indexOf(lmaUpd.state)>=0)return;
 lmaUpd.state='checking';lmaUpd.err='';lmaRenderUpdates();
 try{
  const r=await U.check();lmaUpd.info=r;
  let st=null;try{const L=lmaPlugin('LiveWeb');st=L?await L.status():null}catch(e){}
  lmaUpd.live=st;lmaUpd.plan=lmaUpdPlan(r,st);
  lmaUpd.state=lmaUpd.plan==='apk'?'available':lmaUpd.plan==='web'?'web':'latest';
  lmaLoad().updChecked=Date.now();lmaSave();lmaUpd.autoErr='';lmaUpdTries=0;
  if(lmaUpd.plan==='web'&&(silent||lmaLoad().webAuto!==false)){await lmaWebUpdate(!silent);return}
  if(lmaUpd.state==='available')lmaUpdPopOpen();
 }catch(e){lmaUpd.state='error';lmaUpd.err=String((e&&e.message)||e).slice(0,140);if(silent){lmaUpd.state='idle';lmaUpd.autoErr=lmaUpd.err;if(lmaUpdTries<3)setTimeout(()=>lmaCheckUpdate(true),[15000,45000,120000][lmaUpdTries++])}}
 lmaRenderUpdates();
}
/* a web-only update: back up first, then download it quietly, check it, and keep it ready (it starts on the next launch, or now if
   you tap Restart). Unasked (at launch), a failed backup leaves the update waiting in Settings instead. */
async function lmaWebUpdate(ask){
 const L=lmaPlugin('LiveWeb'),i=lmaUpd.info;if(!L||!i||['backup','webdl'].includes(lmaUpd.state))return;   /* one at a time, its backup included; native refuses a second download too */
 if(lmaUpd.live&&lmaUpd.live.staged===i.webBuild){lmaUpd.state='webready';lmaRenderUpdates();lmaWebPill();return}
 lmaUpd.state='backup';lmaRenderUpdates();
 if(!await lmaBackupFirst('update',ask)){lmaUpd.state='web';lmaUpd.err='It was not downloaded, because the backup before it failed.';lmaRenderUpdates();return}
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
 const was=lmaUpd.state;lmaUpd.state='backup';lmaRenderUpdates();
 if(!await lmaBackupFirst('update')){lmaUpd.state=was;lmaRenderUpdates();return}
 try{await L.restart()}catch(e){lmaUpd.state='error';lmaUpd.err=String((e&&e.message)||e).slice(0,140);lmaRenderUpdates()}
}
/* a small pill above the bottom bar, never blocking: one at a time, gone after 20 s */
function lmaPill(text,label,go,dismiss){
 if(document.querySelector('.lmaweb-pill'))return false;
 const o=document.createElement('div');o.className='lmaweb-pill';o.setAttribute('role','status');
 o.innerHTML='<span>'+text+'</span><button type="button" data-w="go">'+label+'</button><button type="button" data-w="x" aria-label="Dismiss">✕</button>';
 o.addEventListener('click',e=>{const b=e.target.closest('[data-w]');if(!b)return;o.remove();if(b.dataset.w==='go')go();else if(dismiss)dismiss()});
 document.body.appendChild(o);setTimeout(()=>o.remove(),20000);return true;
}
/* the update is downloaded: restart whenever you like */
function lmaWebPill(){if(!lmaUpd.pillGone)lmaPill('Update ready','Restart',lmaWebRestart,()=>{lmaUpd.pillGone=true})}
async function lmaInstallUpdate(){
 const U=lmaPlugin('Updater');if(!U||!lmaUpd.info||['backup','downloading'].includes(lmaUpd.state))return;
 const was=lmaUpd.state;lmaUpd.state='backup';lmaRenderUpdates();
 if(!await lmaBackupFirst('update')){lmaUpd.state=was;lmaRenderUpdates();return}
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
 if(u.state==='backup'){body='Backing up your progress before the update…';btns=''}
 else if(u.state==='downloading'){body='Downloading… '+(u.pct>=0?u.pct+'%':'');btns='<div class="updpop-bar"><i style="width:'+Math.max(4,u.pct)+'%"></i></div>'}
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
 h+=u.state==='checking'?'Checking…':u.state==='latest'?'No newer compatible update was reported by the server.':u.state==='backup'?'Backing up your progress before the update…':u.state==='web'?'A web update is available.'+(u.err?' '+esc(u.err):''):u.state==='webdl'?'Downloading the update… '+(u.pct>=0?u.pct+'%':''):u.state==='webready'?'Update downloaded. It starts next time you open the app, or tap Restart.':u.state==='available'?'Build '+i.latest+' is ready to install.':u.state==='downloading'?'Downloading… '+(u.pct>=0?u.pct+'%':''):u.state==='permission'?'Allow “Install unknown apps” for LifeMax on the screen that just opened, come back, and tap Update again.':u.state==='opened'?'Android’s installer is open. Tap Update there.':u.state==='error'?'Could not check: '+esc(u.err):'Last checked '+lmaAgo(lmaLoad().updChecked)+'.'+(u.autoErr?' The check at launch failed: '+esc(u.autoErr):'');
 h+='</div>'+(i?'<div class="muted" style="font-size:12px">Latest release: '+esc(i.latest)+(native?' · Android API '+native+(required?' / requires '+required:''):'')+'</div>':'')+'</div>';
 if(u.state==='available'||u.state==='permission')h+='<button class="btn" onclick="lmaInstallUpdate()">Update</button>';
 else if(u.state==='webready')h+='<button class="btn" onclick="lmaWebRestart()">Restart</button>';
 else if(u.state==='web')h+='<button class="btn" onclick="lmaWebUpdate(true)">Download</button>';
 else if(LMA_UPD_BUSY.indexOf(u.state)<0)h+='<button class="btn secondary" onclick="lmaCheckUpdate(false)">Check now</button>';
 h+='</div>';
 if(i&&i.stable===false&&u.state==='available')h+='<div class="note" style="margin-top:8px">This build was signed with a temporary key, so Android will likely refuse to install it over your current app. See ANDROID.md.</div>';
 lmaSetHtml('lmaUpdBody',h);
}
/* ---------- automatic backup ---------- */
/* A backup counts only once its file has been read back and checked (ADR 0004). Scheduled backups rotate as life-max-auto-*.json,
   and the snapshots taken before an update, an import or a reset each rotate on their own (life-max-before-update-*.json …), so
   frequent updates push neither the daily history nor the copy taken before a reset out of the folder. verified: when the newest
   verified backup was made; last: the newest verified scheduled one, which sets when the next is due. */
const LMA_DAY=86400e3,LMA_SNAPSHOTS=5;
function lmaBackupDue(){const b=lmaLoad().backup;return !!(b.on&&b.uri&&(!b.verified||Date.now()-b.last>=b.days*LMA_DAY-60e3))}
async function lmaPickBackupFolder(){
 const B=lmaPlugin('BackupFolder');if(!B)return;
 try{
  const r=await B.pickFolder();if(!r||!r.picked)return;
  const b=lmaLoad().backup;b.uri=r.uri;b.folder=r.name;b.on=true;b.status='';b.err='';lmaSave();lmaRenderBackup();
  // a backup still writing to the old folder (leaving the app for the picker can start one) finishes first; the new one starts with none verified
  (lmaBakRun||Promise.resolve()).then(()=>{b.verified=b.last=0;b.lastName='';lmaRunBackup(true)});
 }catch(e){toast('Could not use that folder')}
}
/* Back up now; resolves true once the file is read back and verified. before: what a snapshot is taken ahead of ('update', 'import',
   'reset'), or nothing for a scheduled backup. A run already under way is shared, not repeated. */
function lmaRunBackup(manual,before){
 if(!lmaBakRun){lmaBakRun=lmaBackupChecked(manual,before).finally(()=>{lmaBakRun=null;lmaRenderBackup()});lmaRenderBackup()}
 return lmaBakRun;
}
/* Write, read back, check; only then prune the older files, in the folder the run started with. A file that fails its check or cannot
   be read back is deleted, so it never pushes a good one out. */
async function lmaBackupChecked(manual,before){
 const B=lmaPlugin('BackupFolder'),b=lmaLoad().backup,uri=b.uri;
 if(!B||!uri){if(manual)toast('Choose a folder first');return false}
 const d=new Date(),stamp=d.toLocaleDateString('en-CA')+'-'+String(d.getHours()).padStart(2,'0')+String(d.getMinutes()).padStart(2,'0'),prefix=before?'life-max-before-'+before+'-':'life-max-auto-';
 let w=null;
 try{
  const data=backupData(),text=JSON.stringify(data,null,2),want=backupFacts(data,text.length);
  w=await B.createBackup({uri,name:prefix+stamp+'.json',text});
  const why=backupProblem((await B.readBackup({file:w.file})).text,want);
  if(why)throw Error('the file read back '+why);
  await B.pruneBackups({uri,prefix,keep:before?LMA_SNAPSHOTS:b.keep}).catch(()=>{});
  b.verified=Date.now();if(!before)b.last=b.verified;b.status='ok';b.err='';b.lastName=w.name||'';if(manual)toast('Backup saved and verified');
 }catch(e){if(w)await B.deleteBackup({file:w.file}).catch(()=>{});const m=String((e&&e.message)||e);b.status=m.indexOf('ACCESS_LOST')>=0?'lost':'error';b.err=m.slice(0,160);if(manual)toast(b.status==='lost'?'Choose the folder again':'Backup failed')}
 lmaSave();
 return b.status==='ok';
}
/* Before an update, import or reset: with a folder chosen, take a verified snapshot and wait for it. If that fails the owner decides
   whether to go on without one; unasked, it does not go on. Resolves true to go on. */
async function lmaBackupFirst(what,ask=true){
 if(!lmaNative()||!lmaLoad().backup.uri)return true;
 if(await lmaRunBackup(false,what))return true;
 const b=lmaLoad().backup,why=b.status==='lost'?'Android no longer lets LifeMax write to the backup folder':b.err;
 return ask&&confirm('LifeMax could not make a verified backup before this '+what+': '+why+'.\n\nGo on without a fresh backup?');
}
function lmaSetBackup(k,v){const b=lmaLoad().backup;b[k]=v;lmaSave();lmaRenderBackup();if(k==='on'&&v&&b.uri&&!b.verified)lmaRunBackup(true)}
/* What Settings warns about, or '' when backups are in order. */
function lmaBackupWarning(){
 const b=lmaLoad().backup;
 if(!b.uri)return 'No backup folder is chosen, so your progress exists only on this phone.';
 if(b.status==='lost')return 'Android no longer lets LifeMax write to that folder. Choose it again.';
 if(b.status==='error')return 'The last backup failed: '+esc(b.err);
 if(!b.on)return 'Automatic backup is off, so new progress is not copied anywhere.';
 if(Date.now()-b.verified>2*b.days*LMA_DAY)return b.verified?'The last verified backup was made '+lmaAgo(b.verified)+'.':'No backup has been verified yet. Tap Back up now.';
 return '';
}
function lmaRenderBackup(){
 if(!document.getElementById('lmaBakBody'))return;
 if(!lmaNative()){lmaSetHtml('lmaBakBody','<div class="note">Automatic backups are available in the Android app.</div>');return}
 const b=lmaLoad().backup,off=!b.uri,warn=lmaBackupWarning();
 const row=(l,hint,ctl)=>'<div class="settingsline"><div style="flex:1"><strong>'+l+'</strong>'+(hint?'<div class="muted" style="font-size:12px">'+hint+'</div>':'')+'</div>'+ctl+'</div>';
 const sel=(k,opts,dis)=>'<select '+(dis?'disabled ':'')+'onchange="lmaSetBackup(\''+k+'\',+this.value)" style="'+LMA_IN+'">'+opts.map(o=>'<option value="'+o[0]+'"'+(b[k]===o[0]?' selected':'')+'>'+o[1]+'</option>').join('')+'</select>';
 let h=warn?'<div class="note" style="margin-bottom:10px;border-color:var(--warn);color:var(--text)">'+warn+'</div>':'';
 h+=row('Automatic backup','Saves a backup file into a folder you pick, when you leave the app and one is due. Each file is read back and checked before it counts.','<input type="checkbox" class="check" '+(b.on&&!off?'checked ':'')+(off?'disabled ':'')+'onchange="lmaSetBackup(\'on\',this.checked)" aria-label="Automatic backup">');
 h+=row('Folder',off?'Nothing chosen yet.':esc(b.folder||'Chosen'),'<button class="btn small secondary" onclick="lmaPickBackupFolder()">'+(off?'Choose folder':'Change')+'</button>');
 h+=row('How often','',sel('days',[[1,'Daily'],[3,'Every 3 days'],[7,'Weekly']],off));
 h+=row('Keep the newest','Older automatic backups are deleted. Copies taken before an update, an import or a reset are kept apart, the '+LMA_SNAPSHOTS+' newest of each.',sel('keep',[[3,'3 files'],[7,'7 files'],[14,'14 files'],[30,'30 files']],off));
 h+='<div class="settingsline"><div style="flex:1"><strong>Last verified backup</strong><div class="muted" style="font-size:12px">'+(b.verified?lmaAgo(b.verified)+(b.lastName?' · '+esc(b.lastName):''):'None yet')+'</div></div><button class="btn small" '+(off||lmaBakRun?'disabled ':'')+'onclick="lmaRunBackup(true)">'+(lmaBakRun?'Saving…':'Back up now')+'</button></div>';
 lmaSetHtml('lmaBakBody',h);
}
/* Once, gently: with no folder chosen, a pill on a page the owner can see points to Settings. */
function lmaBackupAsk(){
 const b=lmaLoad().backup,v=lmaView();
 if(!lmaNative()||b.uri||b.asked||v==='settings'||document.querySelector('#onboarding.show,#tutorial.show')||(window.NativeTabs&&NativeTabs.covered(v)))return;
 if(lmaPill('Your progress has no backup yet','Settings',lmaOpenBackupSettings)){b.asked=Date.now();lmaSave()}
}
function lmaOpenBackupSettings(){setPickCat('phone');lmaNavTo('settings',{fast:true});document.getElementById('lmaBakCard')?.scrollIntoView({block:'start'})}
/* ---------- wiring ---------- */
(function lmaUpkeepBoot(){
 const _nav=navigate;navigate=function(v){const r=_nav.apply(this,arguments);if(v==='settings'){lmaRenderUpdates();lmaRenderBackup()}lmaBackupAsk();return r};
 document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='hidden'&&lmaNative()&&lmaBackupDue())lmaRunBackup(false)});
 setInterval(()=>{if(lmaNative()&&lmaBackupDue())lmaRunBackup(false)},6*3600e3);
 setTimeout(()=>{
  lmaRenderUpdates();lmaRenderBackup();
  if(!lmaNative())return;
  if(lmaBackupDue())lmaRunBackup(false);
  lmaBackupAsk();
  lmaCheckUpdate(true);
 },4000);
 document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible'&&lmaNative()&&Date.now()-(lmaLoad().updChecked||0)>30*60e3)lmaCheckUpdate(true)});
})();
