/* ---------- Overview: a row of app shortcuts (Android app only; same list as the Apps tab, with each app's own icon) ---------- */
let ovAppsFetching=false;
/* null outside the Android app, where the tile is hidden. icon is the app's own icon as a data URL, '' until it has arrived; an app
   stored without a label has '' */
function ovAppsData(){
 if(!lmaNative())return null;
 const apps=lmaLoad().apps;
 return {title:'Apps',link:'Edit ›',items:apps.map(function(a){const label=a.label??'';return {pkg:a.pkg,label:label,a11y:'Open '+label,icon:a.icon||''}}),add:'Add app',
  hint:apps.length?'':'Add the apps you open most and they will sit right here.'};
}
function ovAppsHtml(d){
 return '<div class="ov-h"><h3>'+esc(d.title)+'</h3><button type="button" class="ov-link" onclick="navigate(\'apps\');LifeMaxFeatures.open(\'apps\')">'+esc(d.link)+'</button></div><div class="ova-row">'
  +d.items.map(function(a,i){return '<button type="button" class="ova-app" data-i="'+i+'" aria-label="'+esc(a.a11y)+'"><img alt="" src="'+esc(a.icon||'data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==')+'"><span>'+esc(a.label)+'</span></button>'}).join('')
  +'<button type="button" class="ova-app ova-add" aria-label="'+esc(d.add)+'"><i>+</i><span>'+esc(d.add)+'</span></button></div>'
  +(d.hint?'<p class="muted" style="font-size:12.5px;margin:6px 0 0">'+esc(d.hint)+'</p>':'');
}
function ovAppsRender(){
 const el=document.getElementById('ovApps');if(!el)return;
 const tile=el.closest('.ov-tile'),d=ovAppsData();
 if(tile)tile.style.display=d?'':'none';
 if(!d)return;
 el.innerHTML=ovAppsHtml(d);
 el.onclick=function(e){
  const b=e.target.closest('.ova-app');if(!b||!window.AppsShortcuts)return;
  if(b.classList.contains('ova-add'))AppsShortcuts.openPicker();
  else{const a=lmaLoad().apps[+b.dataset.i];if(a)AppsShortcuts.launch(a.pkg)}
 };
 /* An app that is not installed (a backup restored on another phone) never gets an icon: draw again only when one arrived, or
    this would ask Android for it forever. */
 const AS=window.AppsShortcuts,need=d.items.filter(function(a){return !a.icon}).map(function(a){return a.pkg});
 if(need.length&&AS&&!ovAppsFetching){ovAppsFetching=true;AS.loadIcons(need).then(function(m){let ch=false;lmaLoad().apps.forEach(function(a){if(!a.icon&&m[a.pkg]){a.icon=m[a.pkg];ch=true}});if(ch){lmaSave();ovAppsRender()}}).catch(function(){}).then(function(){ovAppsFetching=false})}
}
(function ovAppsBoot(){
 ovTileAdd({id:'apps',name:'App shortcuts',sub:'Your phone apps in one row',icon:'grid',col:'#a78bfa'});
 const nt=ovTileNote;ovTileNote=function(id){return id==='apps'&&!lmaNative()?'Appears in the Android app':nt.apply(this,arguments)};
 const ro=renderOverview;renderOverview=function(){const r=ro.apply(this,arguments);try{ovAppsRender()}catch(e){console.warn('[apps row]',e)}return r};
 setTimeout(function(){try{ovAppsRender()}catch(e){}},1500);
})();

/* the level and name tile opens the Character page, which no longer has its own tab */
(function ovHeroOpen(){
 document.addEventListener('click',e=>{
  const h=e.target.closest&&e.target.closest('#ovHero');if(!h||e.target.closest('button,a,input'))return;
  navigate('character');
 });
})();
