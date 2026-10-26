/* Apps 1.0.0 — a launcher shelf for the other apps on the phone (Android app only). */
const AppsFeature=(function(){
 let root=null,editing=false,sheet=null,all=null;
 const P=()=>lmaLoad();
 const tileHtml=(a,i)=>{
  const icon=a.icon?'<img alt="" data-pkg="'+esc(a.pkg)+'" src="'+esc(iconOf(a.pkg))+'">':'<div class="apps-fallback">'+esc((a.label||'?').charAt(0))+'</div>';
  return '<div style="display:flex;flex-direction:column;align-items:center"><button class="apps-tile" data-act="launch" data-i="'+i+'" aria-label="Open '+esc(a.label)+'">'+icon+'<span class="apps-name">'+esc(a.label)+'</span></button>'+
   (editing?'<div class="apps-edit"><button data-act="left" data-i="'+i+'" aria-label="Move left">◀</button><button class="apps-x" data-act="remove" data-i="'+i+'" aria-label="Remove">✕</button><button data-act="right" data-i="'+i+'" aria-label="Move right">▶</button></div>':'')+'</div>';
 };
 function render(){
  if(!root)return;
  let h='<div class="pagehead"><div><div class="eyebrow">LAUNCHER</div><h2>Apps</h2><p>Your shortcuts to the other apps on this phone.</p></div></div>';
  if(!lmaNative()){
   h+='<div class="card"><p style="margin:0">The Apps tab opens the other apps on your phone, so it only works inside the Android app.</p></div>';
   root.innerHTML=h;return;
  }
  const apps=P().apps;
  h+='<div class="card"><div class="apps-grid">'+apps.map(tileHtml).join('')+
   '<div style="display:flex;flex-direction:column;align-items:center"><button class="apps-tile apps-add" data-act="add" aria-label="Add app"><div class="apps-fallback">＋</div><span class="apps-name">Add app</span></button></div></div>'+
   (apps.length?'<div class="apps-bar"><button class="btn small secondary" data-act="edit">'+(editing?'Done':'Edit shortcuts')+'</button></div>':'<p class="muted" style="font-size:12px;margin:10px 0 0">Tap “Add app” to pick the apps you want here.</p>')+'</div>';
  h+='<div class="card" style="margin-top:14px"><h3>Use LifeMax as your home screen</h3><p class="muted" style="font-size:12px;margin-top:-5px">Choose LifeMax RPG as the default Home app. Pressing Home then brings you here, and you can switch back any time in the same place.</p><button class="btn secondary" data-act="home">Open home app settings</button></div>';
  root.innerHTML=h;
 }
 const errText=e=>String((e&&(e.message||e.errorMessage))||e||'unknown error').slice(0,200);
 async function fetchIcons(pkgs){
  const L=lmaPlugin('AppLauncher'),out={};
  if(!L)return out;
  for(let i=0;i<pkgs.length;i+=20){
   try{Object.assign(out,(await L.getIcons({pkgs:pkgs.slice(i,i+20)})).icons||{})}catch(e){}
   if(sheet)paintIcons(out);
  }
  return out;
 }
 function paintIcons(map){
  if(!sheet)return;
  sheet.querySelectorAll('img[data-pkg]').forEach(im=>{const u=map[im.dataset.pkg];if(u&&!im.src.startsWith('data:'))im.src=u});
 }
 async function loadAll(){
  const L=lmaPlugin('AppLauncher');
  if(!L)throw new Error('The native launcher is not available'+(lmaLastErr?': '+lmaLastErr:''));
  all=(await L.getApps()).apps||[];
  /* refresh names and icons of saved shortcuts */
  const by=new Map(all.map(a=>[a.pkg,a]));let ch=false;
  for(const s of P().apps){const f=by.get(s.pkg);if(f&&f.label!==s.label){s.label=f.label;ch=true}}
  const need=P().apps.filter(s=>!s.icon).map(s=>s.pkg);
  if(need.length){const m=await fetchIcons(need);for(const s of P().apps)if(!s.icon&&m[s.pkg]){s.icon=m[s.pkg];ch=true}}
  if(ch){lmaSave();if(!sheet)render()}
  return all;
 }
 const iconCache={};
 function iconOf(pkg){return iconCache[pkg]||'data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=='}
 function sheetHtml(q){
  const have=new Set(P().apps.map(a=>a.pkg)),ql=(q||'').toLowerCase();
  const rows=(all||[]).filter(a=>!ql||a.label.toLowerCase().indexOf(ql)>=0).map(a=>'<button class="apps-row" data-act="pick" data-pkg="'+esc(a.pkg)+'"><img alt="" data-pkg="'+esc(a.pkg)+'" src="'+esc(iconOf(a.pkg))+'"><span class="apps-lbl">'+esc(a.label)+'</span>'+(have.has(a.pkg)?'<span class="apps-chk">✓</span>':'')+'</button>').join('');
  return rows||'<p class="muted" style="padding:16px 4px">'+(all?'No apps match.':'Loading apps…')+'</p>';
 }
 function openSheet(){
  sheet=document.createElement('div');sheet.className='apps-sheet';
  sheet.innerHTML='<header><input type="search" placeholder="Search apps" autocomplete="off" aria-label="Search apps"><button class="btn small" data-act="close">Done</button></header><div class="apps-list">'+sheetHtml('')+'</div>';
  document.body.appendChild(sheet);
  const list=sheet.querySelector('.apps-list');
  sheet.querySelector('input').addEventListener('input',e=>{list.innerHTML=sheetHtml(e.target.value);paintIcons(iconCache)});
  sheet.addEventListener('click',e=>{
   const b=e.target.closest('[data-act]');if(!b)return;
   if(b.dataset.act==='close'){closeSheet();return}
   if(b.dataset.act==='pick'){
    const a=(all||[]).find(x=>x.pkg===b.dataset.pkg);if(!a)return;
    const apps=P().apps,i=apps.findIndex(x=>x.pkg===a.pkg);
    if(i>=0)apps.splice(i,1);else apps.push({pkg:a.pkg,label:a.label,icon:iconCache[a.pkg]||''});
    lmaSave();list.innerHTML=sheetHtml(sheet.querySelector('input').value);paintIcons(iconCache);
   }
  });
  if(all)return;
  loadAll().then(()=>{
   if(!sheet)return;
   list.innerHTML=sheetHtml(sheet.querySelector('input').value);
   return fetchIcons(all.map(a=>a.pkg)).then(m=>Object.assign(iconCache,m));
  }).catch(err=>{if(list)list.innerHTML='<p class="muted" style="padding:16px 4px">Could not read the app list.<br><small>'+esc(errText(err))+'</small></p>'});
 }
 function closeSheet(){if(sheet){sheet.remove();sheet=null}let ch=false;for(const a of P().apps)if(!a.icon&&iconCache[a.pkg]){a.icon=iconCache[a.pkg];ch=true}if(ch)lmaSave();render()}
 async function onClick(e){
  const b=e.target.closest('[data-act]');if(!b)return;
  const act=b.dataset.act,apps=P().apps,i=+b.dataset.i;
  if(act==='launch'){
   try{await lmaPlugin('AppLauncher').launch({pkg:apps[i].pkg})}catch(err){toast('Could not open it: '+errText(err))}
  }else if(act==='add'){openSheet()}
  else if(act==='edit'){editing=!editing;render()}
  else if(act==='remove'){apps.splice(i,1);lmaSave();render()}
  else if(act==='left'&&i>0){[apps[i-1],apps[i]]=[apps[i],apps[i-1]];lmaSave();render()}
  else if(act==='right'&&i<apps.length-1){[apps[i+1],apps[i]]=[apps[i],apps[i+1]];lmaSave();render()}
  else if(act==='home'){try{await lmaPlugin('AppLauncher').openHomeSettings()}catch(err){toast('Open Android Settings → Apps → Default apps → Home app')}}
 }
 function mount(el){
  root=el.querySelector('.apps-root');
  if(!root.dataset.bound){root.dataset.bound='1';root.addEventListener('click',onClick)}
  editing=false;render();
  if(lmaNative())loadAll().catch(()=>{});
 }
 return {mount};
})();
if(window.LifeMaxFeatures)LifeMaxFeatures.register({id:'apps',mount:el=>AppsFeature.mount(el)});
