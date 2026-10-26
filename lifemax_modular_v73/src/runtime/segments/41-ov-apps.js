/* ---------- Overview: a row of app shortcuts (Android app only; same list as the Apps tab, with each app's own icon) ---------- */
let ovAppsFetching=false;
function ovAppsRender(){
 const el=document.getElementById('ovApps');if(!el)return;
 const tile=el.closest('.ov-tile');
 if(!lmaNative()){if(tile)tile.style.display='none';return}
 if(tile)tile.style.display='';
 const apps=lmaLoad().apps,AS=window.AppsShortcuts;
 let h='<div class="ov-h"><h3>Apps</h3><button type="button" class="ov-link" onclick="navigate(\'apps\');LifeMaxFeatures.open(\'apps\')">Edit ›</button></div><div class="ova-row">';
 h+=apps.map(function(a,i){return '<button type="button" class="ova-app" data-i="'+i+'" aria-label="Open '+esc(a.label)+'"><img alt="" src="'+esc(a.icon||'data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==')+'"><span>'+esc(a.label)+'</span></button>'}).join('');
 h+='<button type="button" class="ova-app ova-add" aria-label="Add app"><i>+</i><span>Add app</span></button></div>';
 if(!apps.length)h+='<p class="muted" style="font-size:12.5px;margin:6px 0 0">Add the apps you open most and they will sit right here.</p>';
 el.innerHTML=h;
 el.onclick=function(e){
  const b=e.target.closest('.ova-app');if(!b||!window.AppsShortcuts)return;
  if(b.classList.contains('ova-add'))AppsShortcuts.openPicker();
  else{const a=lmaLoad().apps[+b.dataset.i];if(a)AppsShortcuts.launch(a.pkg)}
 };
 const need=apps.filter(function(a){return !a.icon}).map(function(a){return a.pkg});
 if(need.length&&AS&&!ovAppsFetching){ovAppsFetching=true;AS.loadIcons(need).then(function(m){let ch=false;lmaLoad().apps.forEach(function(a){if(!a.icon&&m[a.pkg]){a.icon=m[a.pkg];ch=true}});if(ch)lmaSave()}).catch(function(){}).then(function(){ovAppsFetching=false;ovAppsRender()})}
}
(function ovAppsBoot(){
 const i=OV_TILES.findIndex(function(t){return t.id==='hero'})+1;
 OV_TILES.splice(i,0,{id:'apps',name:'App shortcuts',sub:'Your phone apps in one row',icon:'grid',col:'#a78bfa'});
 OV_DEFAULT.splice(i,0,'apps');
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
