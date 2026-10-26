/* ---------- Home-screen widget: the Life Director dial (Android app only) ---------- */
let lmaWidgetBusy=false,lmaWidgetAt=0,lmaWidgetMsg='';
async function lmaWidgetPush(manual){
 const W=lmaNative()&&lmaPlugin('WidgetBridge');
 if(!W||lmaWidgetBusy||!window.LifeDirector||!LifeDirector.widgetSnapshot)return;
 lmaWidgetBusy=true;
 try{
  const snap=await LifeDirector.widgetSnapshot(720);
  if(!snap){lmaWidgetMsg='Set up Life Director first, then the widget can show your day.';return}
  await W.update(snap);lmaWidgetAt=Date.now();lmaWidgetMsg='';
 }catch(e){lmaWidgetMsg=String((e&&e.message)||e).slice(0,160);if(manual)toast('Widget: '+lmaWidgetMsg)}
 finally{lmaWidgetBusy=false;lmaWidgetRender()}
}
async function lmaWidgetPin(){
 const W=lmaPlugin('WidgetBridge');if(!W)return;
 await lmaWidgetPush(true);
 try{const r=await W.pin();if(!r||!r.requested)toast('Touch and hold an empty spot on your home screen, choose Widgets, then LifeMax RPG.')}
 catch(e){toast('Touch and hold an empty spot on your home screen, choose Widgets, then LifeMax RPG.')}
}
async function lmaWidgetRender(){
 const el=document.getElementById('lmaWidgetBody');if(!el)return;
 if(!lmaNative()){el.innerHTML='<div class="note">The home-screen widget is part of the Android app.</div>';return}
 let n=0,pin=false;
 try{const r=await lmaPlugin('WidgetBridge').status();n=r.count||0;pin=!!r.canPin}catch(e){}
 el.innerHTML=(lmaWidgetMsg?'<div class="note" style="margin-bottom:10px">'+esc(lmaWidgetMsg)+'</div>':'')+
  '<div class="settingsline"><div style="flex:1"><strong>'+(n?n+' on your home screen':'Not on your home screen yet')+'</strong><div class="muted" style="font-size:12px">'+(lmaWidgetAt?'Picture updated '+esc(lmaAgo(lmaWidgetAt)):'The picture updates whenever you leave the app.')+'</div></div>'+
  '<button class="btn small" onclick="lmaWidgetPin()">'+(pin?'Add widget':'How to add')+'</button></div>'+
  '<div class="settingsline"><div style="flex:1"><strong>Refresh the picture</strong><div class="muted" style="font-size:12px">Use after you change your plan. The hand moves on its own.</div></div><button class="btn small" '+(lmaWidgetBusy?'disabled ':'')+'onclick="lmaWidgetPush(true)">'+(lmaWidgetBusy?'Drawing…':'Refresh')+'</button></div>';
}
async function lmaWidgetLaunch(){
 try{const r=await lmaPlugin('WidgetBridge').consumeLaunch();if(r&&r.open==='life-director')navigate('life-director')}catch(e){}
}
(function lmaWidgetBoot(){
 const _nav=navigate;navigate=function(v){const r=_nav.apply(this,arguments);if(v==='settings')lmaWidgetRender();return r};
 document.addEventListener('visibilitychange',function(){
  if(!lmaNative())return;
  if(document.visibilityState==='hidden')lmaWidgetPush(false);else lmaWidgetLaunch();
 });
 setInterval(function(){if(lmaNative()&&document.visibilityState==='visible'&&Date.now()-lmaWidgetAt>10*60e3)lmaWidgetPush(false)},5*60e3);
 setTimeout(function(){if(lmaNative()){lmaWidgetLaunch();lmaWidgetPush(false)}lmaWidgetRender()},9000);
})();
