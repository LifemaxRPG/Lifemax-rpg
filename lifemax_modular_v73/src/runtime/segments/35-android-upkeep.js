/* v91 · Android upkeep: in-app updates and automatic backups to a folder you choose.
   Everything here needs the Android shell; in a browser the Settings cards just say so. */
let lmaUpd={state:'idle',info:null,pct:0,err:''},lmaBakBusy=false,lmaUpdBound=false;
const lmaSetHtml=(id,h)=>{const el=document.getElementById(id);if(el)el.innerHTML=h};
const lmaAgo=t=>{if(!t)return 'never';const m=Math.round((Date.now()-t)/60000);return m<1?'just now':m<60?m+' min ago':m<1440?Math.round(m/60)+' h ago':Math.round(m/1440)+' days ago'};
/* ---------- updates ---------- */
async function lmaCheckUpdate(silent){
 const U=lmaPlugin('Updater');if(!U)return;
 if(lmaUpd.state==='checking'||lmaUpd.state==='downloading')return;
 lmaUpd.state='checking';lmaUpd.err='';lmaRenderUpdates();
 try{
  const r=await U.check();lmaUpd.info=r;
  lmaUpd.state=r.latest>r.current&&r.apk?'available':'latest';
  lmaLoad().updChecked=Date.now();lmaSave();
  if(silent&&lmaUpd.state==='available')toast('Update available · Settings → App updates');
 }catch(e){lmaUpd.state='error';lmaUpd.err=String((e&&e.message)||e).slice(0,140);if(silent)lmaUpd.state='idle'}
 lmaRenderUpdates();
}
async function lmaInstallUpdate(){
 const U=lmaPlugin('Updater');if(!U||!lmaUpd.info)return;
 if(!lmaUpdBound){lmaUpdBound=true;try{U.addListener('progress',e=>{lmaUpd.pct=e&&e.pct>=0?e.pct:-1;lmaRenderUpdates()})}catch(e){}}
 lmaUpd.state='downloading';lmaUpd.pct=0;lmaRenderUpdates();
 try{
  const r=await U.install({url:lmaUpd.info.apk});
  lmaUpd.state=r&&r.needsPermission?'permission':'opened';
 }catch(e){lmaUpd.state='error';lmaUpd.err=String((e&&e.message)||e).slice(0,140)}
 lmaRenderUpdates();
}
function lmaRenderUpdates(){
 if(!document.getElementById('lmaUpdBody'))return;
 if(!lmaNative()){lmaSetHtml('lmaUpdBody','<div class="note">Updates are managed inside the Android app.</div>');return}
 const u=lmaUpd,i=u.info,cur=i?i.current:'';
 let h='<div class="settingsline"><div style="flex:1"><strong>'+(i?'Installed build '+cur:'Installed build')+'</strong><div class="muted" style="font-size:12px">';
 h+=u.state==='checking'?'Checking…':u.state==='latest'?'You have the newest build.':u.state==='available'?'Build '+i.latest+' is ready to install.':u.state==='downloading'?'Downloading… '+(u.pct>=0?u.pct+'%':''):u.state==='permission'?'Allow “Install unknown apps” for LifeMax on the screen that just opened, come back, and tap Update again.':u.state==='opened'?'Android’s installer is open. Tap Update there.':u.state==='error'?'Could not check: '+esc(u.err):'Last checked '+lmaAgo(lmaLoad().updChecked)+'.';
 h+='</div></div>';
 if(u.state==='available'||u.state==='permission')h+='<button class="btn" onclick="lmaInstallUpdate()">Update</button>';
 else if(u.state!=='checking'&&u.state!=='downloading')h+='<button class="btn secondary" onclick="lmaCheckUpdate(false)">Check now</button>';
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
  if(Date.now()-(lmaLoad().updChecked||0)>12*3600e3)lmaCheckUpdate(true);
 },12000);
})();
