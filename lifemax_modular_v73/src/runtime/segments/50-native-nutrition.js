/* Native Nutrition beta: real web state is authoritative; no fixture data or duplicate saves. */
(function () {
  const N={plugin:null,open:false,opening:false,session:'',ready:null,queued:false,blocked:false,updating:false};
  function plugin(){
    try {
      const c=window.Capacitor;
      return c && c.isNativePlatform && c.isNativePlatform() &&
        c.isPluginAvailable && c.isPluginAvailable('NativeNutrition') ? c.Plugins.NativeNutrition : null;
    }catch(e){return null}
  }
  function enabled(){return typeof lmaLoad==='function' && lmaLoad().nativeNutrition===true}
  function syncSettings(){
    const toggle=document.getElementById('nativeNutritionToggle');
    const hint=document.getElementById('nativeNutritionHint');
    const available=!!plugin();
    if(toggle){toggle.checked=available&&enabled();toggle.disabled=!available;}
    if(hint)hint.textContent=available
      ? 'Opt-in Android Nutrition beta. Uses your real Food log; advanced actions open classic Food.'
      : 'Requires an Android APK with Native Nutrition support. Classic Food remains available.';
  }
  window.setNativeNutritionEnabled=function(on){
    if(typeof lmaLoad!=='function'||typeof lmaSave!=='function')return;
    const settings=lmaLoad();settings.nativeNutrition=!!on;lmaSave();
    if(!on)close();
    syncSettings();
    if(on&&view()==='meals'){N.blocked=false;open();}
  };
  function view(){return typeof lmaView==='function'?lmaView():''}
  function snapshot(){
    if(typeof dayFoodLog!=='function'||typeof mealsDayKey!=='function'||
       typeof fv2Goals!=='function'||typeof fv2Tot!=='function'||typeof fv2Water!=='function')return null;
    const day=mealsDayKey(),entries=dayFoodLog(day);
    if(!day||!Array.isArray(entries))return null;
    const t=fv2Tot(entries),g=fv2Goals();
    if(!Number.isFinite(t.kcal)||!Number.isFinite(g.cal))return null;
    const data={
      version:1,day:day,
      dayLabel:new Date(day+'T12:00:00').toLocaleDateString(undefined,{weekday:'long',month:'short',day:'numeric'}),
      totals:{kcal:t.kcal,p:t.p,c:t.c,f:t.f},
      goals:{cal:g.cal,p:g.p,c:g.c,f:g.f,water:g.water},
      waterMl:fv2Water(day),
      light:document.documentElement.classList.contains('light-theme'),
      entries:entries.map(function(e){return {
        slot:String(e.slot||'snacks'),name:String(e.name||'Food'),
        kcal:Number(e.kcal)||0,p:Number(e.p)||0,c:Number(e.c)||0,f:Number(e.f)||0
      }})
    };
    if(JSON.stringify(data).length>245000)return null;
    return data;
  }
  async function ready(p){
    if(!N.ready)N.ready=(async function(){
      await p.addListener('action',onAction);
      await p.addListener('closed',function(e){
        if(e.session===N.session){N.open=false;N.opening=false;N.session='';N.blocked=true;}
      });
    })().catch(function(e){N.ready=null;throw e});
    return N.ready;
  }
  async function close(){
    const id=N.session,p=N.plugin;N.open=false;N.opening=false;N.blocked=true;N.session='';
    if(p&&id)try{await p.close({session:id})}catch(e){console.warn('NativeNutrition close',e)}
  }
  async function open(){
    if(N.open||N.opening||N.blocked||!enabled()||view()!=='meals')return;
    if(document.querySelector('.sheet-ov.show,.ld-sheet.open,.updpop,#onboarding.show'))return;
    const p=plugin();if(!p)return;
    const s=snapshot();if(!s){console.warn('NativeNutrition: authoritative snapshot unavailable');return}
    N.opening=true;N.plugin=p;
    try {
      await ready(p);
      const caps=await p.capabilities();
      if(!caps||caps.version!==1||!caps.authoritativeData)return;
      if(view()!=='meals'||!enabled())return;
      N.session='nutrition_'+Date.now();
      await p.open({session:N.session,snapshot:s});
      N.open=true;N.blocked=false;
    }catch(e){N.open=false;N.session='';console.warn('Native Nutrition open failed',e)}
    finally{N.opening=false}
  }
  function scheduleSync(){
    if(!N.open||N.queued||N.updating)return;
    N.queued=true;
    requestAnimationFrame(async function(){
      N.queued=false;
      if(!N.open||view()!=='meals')return;
      const s=snapshot();if(!s){close();return}
      N.updating=true;
      try{await N.plugin.update({session:N.session,snapshot:s})}
      catch(e){console.warn('Native Nutrition sync failed',e);close()}
      finally{N.updating=false}
    });
  }
  async function fallback(command){
    await close();
    if(command==='recipes'){navigate('recipes');return}
    if(command==='plan'||command==='shop'){
      if(typeof fv2S==='function'){fv2S().pane=command==='plan'?'plan':'shop';if(typeof renderMeals==='function')renderMeals();}
    }else if(command==='add'||command==='goals'){
      if(typeof fv2Open==='function')fv2Open(command==='add'?(typeof fv2Slot==='function'?fv2Slot():'lunch'):null,command==='goals'?'goal':'search');
    }
  }
  function onAction(e){
    if(!N.open||e.session!==N.session)return;
    const command=e.command;
    if(command==='previous'||command==='next'){
      try{
        if(typeof setMealsDay!=='function'||typeof fv2DayShift!=='function')return fallback('web');
        setMealsDay(fv2DayShift(mealsDayKey(),command==='previous'?-1:1));
        if(typeof renderMeals==='function')renderMeals();
        scheduleSync();
      }catch(err){console.warn('Native Nutrition day',err);fallback('web')}
    }else if(command==='water'){
      try{
        if(typeof fv2AddWater!=='function'||mealsDayKey()!==getToday())return fallback('web');
        fv2AddWater(250);
        if(typeof renderMeals==='function')renderMeals();
        scheduleSync();
      }catch(err){console.warn('Native Nutrition water',err);fallback('web')}
    }else fallback(command);
  }
  window.NativeNutritionBeta={snapshot:snapshot,open:open,close:close,enabled:enabled};
  const nav=navigate;
  navigate=function(destination){
    const result=nav.apply(this,arguments);
    if(destination!=='meals'&&N.open)close();
    if(destination==='meals'){N.blocked=false;setTimeout(open,80)}
    if(destination==='settings')syncSettings();
    return result;
  };
  if(typeof renderMeals==='function'){
    const original=renderMeals;
    renderMeals=function(){const result=original.apply(this,arguments);scheduleSync();return result};
  }
  if(typeof save==='function'){
    const original=save;
    save=function(){const result=original.apply(this,arguments);scheduleSync();return result};
  }
  syncSettings();setTimeout(syncSettings,800);
})();
