/* ============================================================================
   Food tab v2: macro-ring instrument, week strip, meal cards, a full add-food sheet.
   Replaces renderMeals / openMealSheet / addRecipeToMeal of the first Food tab. Same data:
   state.foodLog, state.customFoods, state.waterLog, state.mealsMeta{calGoal,protGoal,carbGoal,fatGoal,waterGoalMl,favs}.
   Everything here is reached through functions (no top-level lets) so the boot code can call renderMeals early.
   ============================================================================ */
function fv2Slots(){return [['breakfast','Breakfast','#f2b84a'],['lunch','Lunch','#5fd08a'],['dinner','Dinner','#6aa4f0'],['snacks','Snacks','#ef7a85']]}
function fv2Mac(){return {p:['Protein','#ff7a8a'],c:['Carbs','#ffc15e'],f:['Fat','#4fd6c0']}}
function fv2S(){return window.__fv2||(window.__fv2={ringSel:null,prev:{k:0,p:0,c:0,f:0,n:0},wired:false,lastId:null,sh:{open:false,view:'search',stack:[],slot:'lunch',q:'',tab:'recent',pick:null,grams:100,list:[],serial:0,edit:null}})}
function fv2Fmt(n){return Math.round(n).toLocaleString()}
function fv2Slot(){const h=new Date().getHours()+new Date().getMinutes()/60;return h<10.5?'breakfast':h<15?'lunch':h<21?'dinner':'snacks'}
function fv2Tot(es){return es.reduce((a,e)=>{a.kcal+=+e.kcal||0;a.p+=+e.p||0;a.c+=+e.c||0;a.f+=+e.f||0;a.fib+=+e.fib||0;a.sug+=+e.sug||0;a.na+=+e.na||0;return a},{kcal:0,p:0,c:0,f:0,fib:0,sug:0,na:0})}
function fv2Goals(){const m=state.mealsMeta||{},cal=Math.max(0,+m.calGoal||0);return {cal,p:+m.protGoal>0?Math.round(+m.protGoal):Math.round(cal*.3/4),c:+m.carbGoal>0?Math.round(+m.carbGoal):Math.round(cal*.4/4),f:+m.fatGoal>0?Math.round(+m.fatGoal):Math.round(cal*.3/9),water:+m.waterGoalMl>0?Math.round(+m.waterGoalMl):2000}}
function fv2Water(day){return (state.waterLog||[]).filter(w=>w.date===day).reduce((a,w)=>a+(+w.ml||0),0)}
function fv2Hap(n){try{if(window.LifeMaxHaptics)LifeMaxHaptics.play(n||'tap')}catch(e){}}
function fv2DayShift(day,n){const d=new Date(day+'T12:00:00');d.setDate(d.getDate()+n);return d.toLocaleDateString('en-CA')}
function fv2Per100(e){const g=Math.max(1,+e.grams||100),k=100/g,r=x=>Math.round((+x||0)*k*100)/100;return {kcal:r(e.kcal),p:r(e.p),c:r(e.c),f:r(e.f),fib:r(e.fib),sug:r(e.sug),na:r(e.na)}}
/* a food object (per 100 g) rebuilt from a logged entry, so USDA and voice foods work without the database */
function fv2FoodOf(e){const base=(e.foodId&&(foodById(e.foodId)||(typeof usdaFoodsCache!=='undefined'&&usdaFoodsCache[e.foodId])))||null;if(base)return base;const p=fv2Per100(e);return Object.assign({id:e.foodId||('name:'+e.name),n:e.name,cat:'Logged',port:[]},p)}
function fv2Recents(limit){mealsEnsure();const seen=new Set(),out=[];const es=state.foodLog.slice().sort((a,b)=>new Date(b.at||b.date)-new Date(a.at||a.date));
 for(const e of es){const key=e.foodId&&e.foodId!=='voice_est'?e.foodId:'n:'+e.name;if(seen.has(key))continue;seen.add(key);const f=Object.assign({},fv2FoodOf(e));f._grams=Math.round(+e.grams)||100;f._last=e.kcal;out.push(f);if(out.length>=(limit||12))break}return out}
function fv2Favs(){const m=state.mealsMeta=state.mealsMeta||{calGoal:0};return m.favs=m.favs||[]}
function fv2IsFav(f){return fv2Favs().some(x=>x.id===f.id)}
function fv2ToggleFav(f){const a=fv2Favs(),i=a.findIndex(x=>x.id===f.id),was=a.slice();if(i>=0)a.splice(i,1);else a.unshift({id:f.id,n:f.n,cat:f.cat||'',brand:f.brand||'',kcal:f.kcal,p:f.p,c:f.c,f:f.f,fib:f.fib||0,sug:f.sug||0,na:f.na||0,port:f.port||[]});
 return fv2Saved(()=>{a.splice(0,a.length,...was)})?i<0:i>=0} // whether the food is a favourite now
function fv2PL(l){l=String(l||'serving').replace(/\bRACC\b/i,'serving');return l.charAt(0).toUpperCase()+l.slice(1)}
function fv2Unit(f){const p=(f.port||[])[0];return p?{g:p.g,l:fv2PL(p.l)}:{g:100,l:'100 g'}}
function fv2KcalFor(f,g){return scaleFood(f,g).kcal}

/* ------------------------------------------------------------------ logging */
/* Stores a Food change. One that could not be stored is taken back (undo), and the snackbar says why in place of the
   success it would have shown: false. save() itself says nothing while the save is read-only. */
function fv2Saved(undo){if(save())return true;undo();renderMeals();fv2Snack(unsavedMessage());return false}
function fv2Log(food,grams,slot,opts){
 mealsEnsure();grams=Math.max(1,Math.round((+grams||100)*10)/10);const s=scaleFood(food,grams),day=mealsDayKey();
 const e={id:'food_'+Date.now()+'_'+Math.random().toString(36).slice(2,6),date:day,slot:slot||fv2Slot(),foodId:food.id,name:food.n,grams,...s,at:new Date().toISOString()};
 state.foodLog.push(e);if(!fv2Saved(()=>{state.foodLog=state.foodLog.filter(x=>x!==e)}))return null;
 fv2S().lastId=e.id;fv2Hap('success');renderMeals();
 fv2Snack((food.n.length>26?food.n.slice(0,25)+'…':food.n)+' · '+fv2Fmt(s.kcal)+' kcal','Undo',function(){const was=state.foodLog;state.foodLog=was.filter(x=>x.id!==e.id);if(fv2Saved(()=>{state.foodLog=was}))renderMeals()});
 return e;
}
function fv2Snack(msg,act,fn){if(window.__fv2Notice)window.__fv2Notice(msg,act,fn);let el=document.getElementById('fv2Snack');if(!el){el=document.createElement('div');el.id='fv2Snack';el.className='fv2-snack';document.body.appendChild(el)}
 el.innerHTML='<span></span>'+(act?'<button type="button">'+esc(act)+'</button>':'');el.firstChild.textContent=msg;el.classList.add('on');
 if(act)el.querySelector('button').onclick=function(){el.classList.remove('on');fn&&fn()};clearTimeout(el._t);el._t=setTimeout(function(){el.classList.remove('on')},4200)}
function deleteMealEntry(id){mealsEnsure();const was=state.foodLog;state.foodLog=was.filter(x=>x.id!==id);if(fv2Saved(()=>{state.foodLog=was}))renderMeals()}
function fv2CopySlot(slot){const from=fv2DayShift(mealsDayKey(),-1),src=state.foodLog.filter(e=>e.date===from&&e.slot===slot);if(!src.length)return;
 const ids=src.map(e=>{const n=Object.assign({},e,{id:'food_'+Date.now()+'_'+Math.random().toString(36).slice(2,6),date:mealsDayKey(),at:new Date().toISOString()});state.foodLog.push(n);return n.id});
 if(!fv2Saved(()=>{state.foodLog=state.foodLog.filter(x=>ids.indexOf(x.id)<0)}))return;
 fv2Hap('success');renderMeals();fv2Snack('Copied '+src.length+' item'+(src.length>1?'s':'')+' from yesterday','Undo',function(){const was=state.foodLog;state.foodLog=was.filter(x=>ids.indexOf(x.id)<0);if(fv2Saved(()=>{state.foodLog=was}))renderMeals()})}
function fv2AddWater(ml){state.waterLog=state.waterLog||[];const id='water_'+Date.now()+'_'+Math.random().toString(36).slice(2,6);state.waterLog.push({id,date:mealsDayKey(),ml,at:new Date().toISOString()});
 if(!fv2Saved(()=>{state.waterLog=state.waterLog.filter(x=>x.id!==id)}))return;fv2Hap('tap');renderMeals();
 fv2Snack('+'+ml+' ml water','Undo',function(){const was=state.waterLog;state.waterLog=was.filter(x=>x.id!==id);if(fv2Saved(()=>{state.waterLog=was}))renderMeals()})}

/* ------------------------------------------------------------------ the page */
function fv2RingSvg(t,g,sel,prev){
 const rings=[['p',126,16,t.p,g.p,'#ff7a8a'],['c',101,16,t.c,g.c,'#ffc15e'],['f',76,16,t.f,g.f,'#4fd6c0']];
 const pct=g.cal?t.kcal/g.cal:0,over=pct>1.0001;let s='<svg class="fv2-svg" viewBox="0 0 320 320" role="img" aria-label="Calories and macros"><defs><linearGradient id="fv2g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#7c9cff"/><stop offset="1" stop-color="#a77cff"/></linearGradient></defs>';
 for(let i=0;i<72;i++){const a=i/72*Math.PI*2-Math.PI/2,r0=i%6===0?137:141,r1=151,on=pct>0&&i/72<Math.min(1,pct);s+='<line x1="'+(160+Math.cos(a)*r0).toFixed(1)+'" y1="'+(160+Math.sin(a)*r0).toFixed(1)+'" x2="'+(160+Math.cos(a)*r1).toFixed(1)+'" y2="'+(160+Math.sin(a)*r1).toFixed(1)+'" class="fv2-tick'+(on?(over?' bad':' on'):'')+'"/>'}
 rings.forEach(function(r){const k=r[0],rad=r[1],w=r[2],circ=2*Math.PI*rad,frac=r[4]?Math.min(1,r[3]/r[4]):0,ov=r[4]?Math.max(0,Math.min(1,r[3]/r[4]-1)):0,pf=Math.min(1,prev[k]||0),dim=sel&&sel!==k?' dim':'';
  s+='<g class="fv2-ring'+dim+(sel===k?' sel':'')+'"><circle cx="160" cy="160" r="'+rad+'" class="fv2-track" stroke-width="'+w+'"/><circle cx="160" cy="160" r="'+rad+'" class="fv2-arc" stroke="'+r[5]+'" stroke-width="'+w+'" transform="rotate(-90 160 160)" style="opacity:'+(pf>0?1:0)+';stroke-dasharray:'+(circ*pf).toFixed(2)+' '+circ.toFixed(2)+'" data-to="'+(circ*frac).toFixed(2)+'" data-c="'+circ.toFixed(2)+'"/>'
  +(ov>0?'<circle cx="160" cy="160" r="'+rad+'" class="fv2-over" stroke-width="'+(w-5)+'" transform="rotate(-90 160 160)" style="stroke-dasharray:'+(circ*ov).toFixed(2)+' '+circ.toFixed(2)+'"/>':'')+'<circle cx="160" cy="160" r="'+rad+'" class="fv2-hit" stroke-width="'+(w+8)+'" data-a="macro" data-m="'+k+'"><title>'+fv2Mac()[k][0]+'</title></circle></g>'});
 return s+'</svg>';
}
function fv2Mid(t,g,S,left){const k=S.ringSel;
 if(k){const m=fv2Mac()[k],v=Math.round(t[k]),gl=g[k],lf=gl-t[k];return '<button type="button" class="fv2-mid sel" data-a="macro" data-m="'+k+'" style="--mc:'+m[1]+'" aria-label="'+m[0]+': '+v+' grams. Tap to go back to calories"><span class="l" style="color:'+m[1]+'">'+m[0].toUpperCase()+'</span><span class="v" id="fv2Big" data-to="'+v+'" data-from="'+v+'">'+fv2Fmt(v)+'</span><span class="l">GRAMS</span><span class="s">'+(gl?(lf>=0?'of '+gl+' g · '+Math.round(lf)+' left':Math.round(-lf)+' g over '+gl+' g'):'no target set')+'</span></button>'}
 return '<button type="button" class="fv2-mid" data-a="goal" aria-label="Edit daily targets">'+(g.cal?'<span class="v" id="fv2Big" data-to="'+Math.round(Math.abs(left))+'" data-from="'+Math.round(Math.abs(S.prev.n||0))+'">'+fv2Fmt(Math.abs(left))+'</span><span class="l'+(left<0?' bad':'')+'">'+(left<0?'KCAL OVER':'KCAL LEFT')+'</span><span class="s">'+fv2Fmt(t.kcal)+' of '+fv2Fmt(g.cal)+'</span>'
  :'<span class="v" id="fv2Big" data-to="'+Math.round(t.kcal)+'" data-from="'+Math.round(S.prev.n||0)+'">'+fv2Fmt(t.kcal)+'</span><span class="l">KCAL TODAY</span><span class="s set">Set a daily target</span>')+'</button>'}
function fv2WeekStrip(day,g){const d=new Date(day+'T12:00:00'),dow=(d.getDay()+6)%7,mon=fv2DayShift(day,-dow),today=getToday();let h='';
 for(let i=0;i<7;i++){const k=fv2DayShift(mon,i),es=dayFoodLog(k),kc=fv2Tot(es).kcal,fr=g.cal?kc/g.cal:(kc?.5:0),ok=g.cal&&fr>=.9&&fr<=1.08,over=g.cal&&fr>1.08,circ=2*Math.PI*11,dd=new Date(k+'T12:00:00');
  h+='<button type="button" class="fv2-d'+(k===day?' on':'')+(k===today?' today':'')+'" data-a="day" data-d="'+k+'" aria-label="'+esc(dd.toLocaleDateString(undefined,{weekday:'long',month:'long',day:'numeric'}))+'"><span class="w">'+'MTWTFSS'[i]+'</span><span class="rg"><svg viewBox="0 0 28 28"><circle cx="14" cy="14" r="11" class="t"/><circle cx="14" cy="14" r="11" class="a'+(ok?' ok':over?' bad':'')+'" transform="rotate(-90 14 14)" style="stroke-dasharray:'+(circ*Math.min(1,fr)).toFixed(1)+' '+circ.toFixed(1)+'"/></svg><span class="n">'+dd.getDate()+'</span></span></button>'}
 return h}
function fv2Entry(e){const t=(+e.p||0)*4+(+e.c||0)*4+(+e.f||0)*9||1,pc=Math.round((+e.p||0)*4/t*100),cc=Math.round((+e.c||0)*4/t*100),fc=Math.max(0,100-pc-cc);
 return '<button type="button" class="fv2-e'+(fv2S().lastId===e.id?' new':'')+'" data-a="entry" data-id="'+esc(e.id)+'"><span class="nm">'+esc(e.name)+'</span><span class="kc">'+fv2Fmt(e.kcal)+'</span><span class="sb">'+fv2Fmt(e.grams)+' g · P '+Math.round(e.p)+' · C '+Math.round(e.c)+' · F '+Math.round(e.f)+'</span><span class="mb"><i style="width:'+pc+'%;background:#ff7a8a"></i><i style="width:'+cc+'%;background:#ffc15e"></i><i style="width:'+fc+'%;background:#4fd6c0"></i></span></button>'}
function renderMeals(){
 mealsEnsure();const root=document.getElementById('fv2Root');if(!root)return;const S=fv2S();
 if(!mealsDay)mealsDay=getToday();const day=mealsDay,es=dayFoodLog(day),t=fv2Tot(es),g=fv2Goals(),today=getToday();
 if(!S.wired)fv2Wire(root);
 const left=g.cal-t.kcal,pct=g.cal?t.kcal/g.cal:0,dd=new Date(day+'T12:00:00');
 let h='<div class="fv2-head"><div><div class="fv2-eyebrow">'+(day===today?'Today':day===fv2DayShift(today,-1)?'Yesterday':'Viewing')+'</div><h1>'+esc(dd.toLocaleDateString(undefined,{weekday:'long',month:'short',day:'numeric'}))+'</h1></div>'
  +'<div class="fv2-nav"><button type="button" class="tx" data-a="goal">Targets</button><button type="button" data-a="step" data-n="-1" aria-label="Previous day">‹</button><button type="button" data-a="step" data-n="1" aria-label="Next day">›</button>'+(day!==today?'<button type="button" class="tx" data-a="today">Today</button>':'')+'</div></div>'
  +'<div class="fv2-week">'+fv2WeekStrip(day,g)+'</div>';
 const pane=S.pane||(typeof sdDay==='function'&&sdDay(mealsDayKey())?'plan':'log');
 if(pane!=='log'){h+=(pane==='plan'?sdPlanHtml():sdShopHtml());root.innerHTML=h;fv2Dock();return}
 h+=sdPaneSwitch('log');
 /* hero */
 h+='<section class="fv2-hero"><div class="fv2-dial"><div class="fv2-dial-in">'+fv2RingSvg(t,g,S.ringSel,S.prev)+fv2Mid(t,g,S,left)+'</div></div>'
  +'<div class="fv2-macros">'+['p','c','f'].map(function(k){const m=fv2Mac()[k],v=t[k],gl=g[k],lf=gl-v;return '<button type="button" class="fv2-mt'+(S.ringSel===k?' on':'')+'" data-a="macro" data-m="'+k+'" style="--mc:'+m[1]+'"><span class="n"><i></i>'+m[0]+'</span><span class="v">'+Math.round(v)+'<small>'+(gl?' / '+gl+' g':' g')+'</small></span><span class="b"><b style="width:'+(gl?Math.min(100,v/gl*100):0)+'%"></b></span><span class="r'+(lf<0?' bad':'')+'">'+(gl?(lf>=0?Math.round(lf)+' g left':Math.round(-lf)+' g over'):'no target')+'</span></button>'}).join('')+'</div></section>';
 /* quick add */
 const rc=fv2Recents(8);
 if(rc.length)h+='<section class="fv2-quick"><div class="fv2-sec">Quick add <span>one tap, '+esc(fv2Slots().find(s=>s[0]===fv2Slot())[1].toLowerCase())+'</span></div><div class="fv2-chips">'+rc.map(function(f,i){return '<button type="button" class="fv2-chip" data-a="quick" data-i="'+i+'"><b>'+esc(f.n.length>22?f.n.slice(0,21)+'…':f.n)+'</b><small>'+fv2Fmt(fv2KcalFor(f,f._grams))+' kcal</small></button>'}).join('')+'</div></section>';
 S.recents=rc;
 /* water */
 const wt=fv2Water(day),wp=Math.min(1,wt/g.water);
 h+='<section class="fv2-water"><div class="top"><div class="ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3s6 6.3 6 10.5A6 6 0 0 1 6 13.5C6 9.300 12 3 12 3z"/></svg></div><div class="tx"><b>Water</b><span>'+fv2Fmt(wt)+' / '+fv2Fmt(g.water)+' ml</span></div><div class="bt"><button type="button" data-a="water" data-ml="250">+250</button><button type="button" data-a="water" data-ml="500">+500</button></div></div><div class="bar"><b style="width:'+(wp*100)+'%"></b></div></section>';
 /* meals */
 h+='<div class="fv2-sec">Meals</div><div class="fv2-meals">';
 fv2Slots().forEach(function(sl){const list=es.filter(e=>e.slot===sl[0]),kc=fv2Tot(list).kcal,yest=state.foodLog.some(e=>e.date===fv2DayShift(day,-1)&&e.slot===sl[0]);
  h+='<section class="fv2-meal" style="--sc:'+sl[2]+'"><header><i></i><h3>'+sl[1]+'</h3><span class="k">'+(list.length?fv2Fmt(kc)+' kcal':'')+'</span><button type="button" class="add" data-a="add" data-s="'+sl[0]+'" aria-label="Add food to '+sl[1]+'">+</button></header>'
   +(list.length?'<div class="fv2-es">'+list.map(fv2Entry).join('')+'</div>':'<div class="fv2-empty"><button type="button" data-a="add" data-s="'+sl[0]+'">Add '+sl[1].toLowerCase()+'</button>'+(yest?'<button type="button" class="alt" data-a="copy" data-s="'+sl[0]+'">Copy yesterday</button>':'')+'</div>')+'</section>'});
 h+='</div>';
 /* details */
 const wk=[];for(let i=6;i>=0;i--){const k=fv2DayShift(day,-i);wk.push({k,kc:fv2Tot(dayFoodLog(k)).kcal})}const mx=Math.max(g.cal*1.15,...wk.map(x=>x.kc),1),avg=wk.filter(x=>x.kc>0);
 h+='<details class="fv2-det"><summary><span>Nutrition details</span><i>⌄</i></summary><div class="in"><div class="fv2-nut">'
  +[['Fiber',t.fib,28,'g'],['Sugar',t.sug,50,'g'],['Sodium',t.na,2300,'mg']].map(function(n){return '<div><span>'+n[0]+'</span><b>'+Math.round(n[1])+'<small> / '+n[2]+' '+n[3]+'</small></b><i><u style="width:'+Math.min(100,n[1]/n[2]*100)+'%"></u></i></div>'}).join('')+'</div>'
  +'<div class="fv2-wk-h"><span>Last 7 days</span><b>'+(avg.length?fv2Fmt(avg.reduce((a,x)=>a+x.kc,0)/avg.length)+' kcal avg':'no data')+'</b></div><div class="fv2-wk">'+wk.map(function(x){return '<div class="c'+(x.k===day?' on':'')+'"><div class="bar"><b style="height:'+Math.max(2,x.kc/mx*100)+'%"></b>'+(g.cal?'<u style="bottom:'+(g.cal/mx*100)+'%"></u>':'')+'</div><span>'+new Date(x.k+'T12:00:00').toLocaleDateString(undefined,{weekday:'narrow'})+'</span></div>'}).join('')+'</div></div></details>';
 h+='<div id="dietPlanWrap"></div>';
 root.innerHTML=h;
 try{renderDietPlan()}catch(e){}
 /* animate rings and the big number from the last drawn values */
 requestAnimationFrame(function(){requestAnimationFrame(function(){root.querySelectorAll('.fv2-arc').forEach(function(a){a.style.opacity=+a.dataset.to>0?1:0;a.style.strokeDasharray=a.dataset.to+' '+a.dataset.c})})});
 const big=document.getElementById('fv2Big');if(big&&(+big.dataset.from)!==(+big.dataset.to)&&!(window.fxReduced&&fxReduced())){const a=+big.dataset.from,b=+big.dataset.to,t0=performance.now();(function st(n){const k=Math.max(0,Math.min(1,(n-t0)/650)),e=1-Math.pow(1-k,3);big.textContent=fv2Fmt(a+(b-a)*e);if(k<1&&document.body.contains(big))requestAnimationFrame(st)})(t0)}
 S.prev={k:g.cal?t.kcal/g.cal:0,p:g.p?t.p/g.p:0,c:g.c?t.c/g.c:0,f:g.f?t.f/g.f:0,n:g.cal?Math.abs(left):t.kcal};
 S.lastId=null;fv2Dock();
}
function fv2Dock(){let d=document.getElementById('fv2Dock');const v=document.getElementById('view-meals');if(!v)return;
 if(!d){d=document.createElement('button');d.type='button';d.id='fv2Dock';d.className='fv2-dock';d.setAttribute('data-a','search');d.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="M20 20l-3.500-3.500"/></svg><span>Search foods, or log a meal</span><i>+</i>';v.appendChild(d);d.addEventListener('click',function(){fv2Open(fv2Slot())})}
 const sb=document.querySelector('.sidebar');v.style.setProperty('--fv2-nav',(sb&&getComputedStyle(sb).position==='fixed'?sb.offsetHeight:0)+'px')}
function fv2Wire(root){const S=fv2S();S.wired=true;
 root.addEventListener('click',function(ev){const b=ev.target.closest('[data-a]');if(!b)return;const a=b.dataset.a;
  if(a==='step')setMealsDay(fv2DayShift(mealsDayKey(),+b.dataset.n));
  else if(a==='today')mealsGoToday();
  else if(a==='day')setMealsDay(b.dataset.d);
  else if(a==='goal')fv2Open(null,'goal');
  else if(a==='macro'){S.ringSel=S.ringSel===b.dataset.m?null:b.dataset.m;renderMeals()}
  else if(a==='add')fv2Open(b.dataset.s);
  else if(a==='quick'){const f=S.recents[+b.dataset.i];if(f)fv2Log(f,f._grams,fv2Slot())}
  else if(a==='water')fv2AddWater(+b.dataset.ml);
  else if(a==='copy')fv2CopySlot(b.dataset.s);
  else if(a==='entry')fv2OpenEntry(b.dataset.id)
  else if(a==='pane'){S.pane=b.dataset.v;renderMeals()}
  else if(a.slice(0,3)==='sd-')sdAct(b)});
 window.addEventListener('resize',fv2Dock)}
function setMealsDay(d){mealsDay=d;renderMeals();const r=document.getElementById('fv2Root');if(r&&!(window.fxReduced&&fxReduced())&&r.animate)r.animate([{opacity:.4,transform:'translateY(6px)'},{opacity:1,transform:'none'}],{duration:260,easing:'cubic-bezier(.2,.8,.3,1)'})}
function pickMacro(k){const S=fv2S();S.ringSel=S.ringSel===k?null:k;renderMeals()}
function fuelCancelMotion(){}
function fuelOpenLab(slot){fv2Open(slot||fv2Slot())}
function openMealSheet(slot){fv2Open(slot||fv2Slot())}
function closeMealSheet(){fv2Close()}
function addRecipeToMeal(id){const f=RECIPE_FOODS.find(x=>x.id===id);if(!f)return;navigate('meals');fv2Open(fv2Slot(),'portion',f)}

/* ------------------------------------------------------------------ the sheet */
function fv2Ov(){let o=document.getElementById('fv2Ov');if(o)return o;o=document.createElement('div');o.id='fv2Ov';o.className='sheet-ov fv2-ov';o.innerHTML='<div class="sheet fv2-sheet" role="dialog" aria-modal="true"></div>';document.body.appendChild(o);
 o.addEventListener('click',function(ev){if(ev.target===o){fv2Back();return}fv2SheetClick(ev)});
 o.addEventListener('input',fv2SheetInput);o.addEventListener('keydown',function(ev){if(ev.key==='Enter'&&ev.target.id==='fv2Q'){ev.preventDefault();ev.target.blur()}});return o}
function fv2Open(slot,view,food){const S=fv2S(),sh=S.sh;sh.open=true;sh.slot=slot||sh.slot||fv2Slot();sh.stack=[];sh.q='';sh.tab=fv2Recents(1).length?'recent':(fv2Favs().length?'fav':'recent');sh.list=[];
 sh.view=view||'search';sh.pick=null;sh.edit=null;if(food){sh.pick=food;const u=fv2Unit(food);sh.grams=u.g;sh.view='portion'}
 const o=fv2Ov();fv2Paint();o.classList.add('show');document.body.classList.add('noscroll');requestAnimationFrame(function(){const p=o.querySelector('.sheet');p&&p.classList.add('fv2-in')});
 if(sh.view==='search')setTimeout(function(){const q=document.getElementById('fv2Q');q&&q.focus()},320)}
function fv2Close(){const o=document.getElementById('fv2Ov');if(!o)return;fv2S().sh.open=false;const p=o.querySelector('.sheet');p&&p.classList.remove('fv2-in');o.classList.remove('show');document.body.classList.remove('noscroll')}
function fv2Go(view){const sh=fv2S().sh;sh.stack.push(sh.view);sh.view=view;fv2Paint()}
function fv2Back(){const sh=fv2S().sh;if(sh.stack.length){sh.view=sh.stack.pop();fv2Paint();if(sh.view==='search')setTimeout(function(){const q=document.getElementById('fv2Q');q&&q.focus()},80)}else fv2Close()}
function fv2OpenEntry(id){const e=state.foodLog.find(x=>x.id===id);if(!e)return;const sh=fv2S().sh;sh.open=true;sh.stack=[];sh.edit=e;sh.pick=fv2FoodOf(e);sh.grams=+e.grams||100;sh.slot=e.slot;sh.view='edit';const o=fv2Ov();fv2Paint();o.classList.add('show');document.body.classList.add('noscroll');requestAnimationFrame(function(){const p=o.querySelector('.sheet');p&&p.classList.add('fv2-in')})}
function fv2SlotPills(){const sl=fv2S().sh.slot;return '<div class="fv2-pills">'+fv2Slots().map(function(s){return '<button type="button" data-s="'+s[0]+'" class="'+(sl===s[0]?'on':'')+'" style="--sc:'+s[2]+'">'+s[1]+'</button>'}).join('')+'</div>'}
function fv2Head(title,back){const w=fv2SheetWords();return '<div class="fv2-sh"><button type="button" class="bk" data-s2="back" aria-label="'+(back?w.back:w.close)+'">'+(back?'‹':'✕')+'</button><h2>'+esc(title)+'</h2><span></span></div>'}
function fv2Paint(){const sh=fv2S().sh,p=document.querySelector('#fv2Ov .sheet');if(!p)return;let h='';
 if(sh.view==='search'){
  const w=fv2SheetWords();h=fv2HeadFor('search')+'<div class="fv2-sb"><div class="fv2-si"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="M20 20l-3.500-3.500"/></svg><input id="fv2Q" type="search" placeholder="'+w.search+'" autocomplete="off" value="'+esc(sh.q)+'" enterkeyhint="search"></div>'+fv2SlotPills()
   +'<div class="fv2-tabs">'+fv2TabsData().map(t=>'<button type="button" data-tab="'+t[0]+'" class="'+(sh.tab===t[0]?'on':'')+'">'+t[1]+'</button>').join('')+'</div></div><div class="fv2-list" id="fv2List"></div>'
   +'<div class="fv2-foot"><button type="button" class="ghost" data-s2="quick">'+w.quick+'</button><button type="button" class="ghost" data-s2="create">'+w.create+'</button></div>';
  p.innerHTML=h;fv2List();
 }else if(sh.view==='portion'||sh.view==='edit'){h=fv2Portion();p.innerHTML=h}
 else if(sh.view==='create'){h=fv2Create();p.innerHTML=h}
 else if(sh.view==='quick'){const qd=fv2QuickData(sh.slot);h=fv2HeadFor('quick')+'<div class="fv2-form"><label>'+qd.kcal.label+'<input id="fv2QK" type="number" inputmode="numeric" min="0" placeholder="'+qd.kcal.hint+'"></label><label>'+qd.name.label+' <small>'+qd.name.small+'</small><input id="fv2QN" type="text" maxlength="'+qd.name.max+'" placeholder="'+qd.name.hint+'"></label>'+fv2SlotPills()+'</div><div class="fv2-foot"><button type="button" class="pri" data-s2="quicksave">'+esc(qd.save)+'</button></div>';p.innerHTML=h;setTimeout(function(){const i=document.getElementById('fv2QK');i&&i.focus()},120)}
 else if(sh.view==='goal'){h=fv2Goal();p.innerHTML=h;fv2GoalLive()}}
function fv2Match(f,toks){const hay=((f.n||'')+' '+(f.brand||'')+' '+(f.cat||'')).toLowerCase();return toks.every(t=>hay.indexOf(t)>=0)}
/* what a row of the list shows for a food: the amount a tap logs (the last amount used, else its first serving), its words
   (brand, then `sub` or the serving), and its kcal and macros at that amount */
function fv2RowData(f,sub){const u=f._grams?{g:f._grams,l:f._grams+' g'}:fv2Unit(f),s=scaleFood(f,u.g);
 return {name:f.n,detail:(f.brand?f.brand+' · ':'')+(sub||u.l),grams:u.g,kcal:fv2KcalFor(f,u.g),p:Math.round(s.p),c:Math.round(s.c),f:Math.round(s.f)}}
function fv2Row(f,i,sub){const d=fv2RowData(f,sub);
 return '<div class="fv2-r" data-i="'+i+'"><button type="button" class="m" data-r="'+i+'"><b>'+esc(d.name)+'</b><span>'+esc(d.detail)+' · P '+d.p+' C '+d.c+' F '+d.f+'</span></button><span class="k">'+fv2Fmt(d.kcal)+'<small>kcal</small></span><button type="button" class="q" data-q="'+i+'" aria-label="Add '+esc(d.name)+'">+</button></div>'}
/* the foods under a tab of the empty search: what you logged lately, your favourites, the foods you made */
function fv2TabFoods(tab){return tab==='recent'?fv2Recents(20):tab==='fav'?fv2Favs().map(f=>Object.assign({},f)):(state.customFoods||[]).slice(0,60)}
/* what an empty list says: [title, line] for a search with no match (q), or for an empty tab */
function fv2NoneWords(q,tab){return q?['No matches yet','Try another word, or create the food yourself.']
 :tab==='recent'?['Nothing logged yet','Search for a food above. Everything you log shows up here for one-tap repeats.']
 :tab==='fav'?['No favorites yet','Open any food and tap the star to keep it here.']
 :['No custom foods','Create a food with its label values and it will live here.']}
function fv2NoneHtml(w){return '<div class="fv2-none"><b>'+w[0]+'</b><span>'+w[1]+'</span></div>'}
function fv2List(){const sh=fv2S().sh,box=document.getElementById('fv2List');if(!box)return;let list=[],note='';const q=sh.q.trim().toLowerCase();
 if(q){list=sh.list;note=list.length?'':fv2NoneHtml(fv2NoneWords(q,sh.tab))}
 else if(sh.tab==='recent'){list=fv2TabFoods('recent');sh.list=list;note=list.length?'':fv2NoneHtml(fv2NoneWords('',sh.tab))}
 else if(sh.tab==='fav'){list=fv2TabFoods('fav');sh.list=list;note=list.length?'':fv2NoneHtml(fv2NoneWords('',sh.tab))}
 else{list=fv2TabFoods('mine');sh.list=list;note=list.length?'':fv2NoneHtml(fv2NoneWords('',sh.tab))}
 box.innerHTML=list.map((f,i)=>fv2Row(f,i,q?'':(f._grams?'last '+f._grams+' g':''))).join('')+note}
/* the words of a typed search (lower case) */
function fv2SearchTokens(q){return String(q||'').trim().toLowerCase().split(/\s+/).filter(Boolean)}
/* the page's own foods for those words: names that start with the first word first, then the shorter names; up to 25 */
function fv2LocalFoods(toks){let local=allFoods().filter(f=>fv2Match(f,toks));
 local.sort((a,b)=>((a.n||'').toLowerCase().startsWith(toks[0])?0:1)-((b.n||'').toLowerCase().startsWith(toks[0])?0:1)||(a.n||'').length-(b.n||'').length);return local.slice(0,25)}
/* the USDA database's foods for the same words that `local` does not have, up to 30; [] when the database cannot be read */
async function fv2UsdaFoods(toks,local){
 try{const db=await usdaDb();const tx=db.transaction('foods','readonly');const idx=tx.objectStore('foods').index('terms');const req=idx.getAll(IDBKeyRange.bound(toks[0],toks[0]+'\uffff'),80);const found=await new Promise((res,rej)=>{req.onsuccess=()=>res(req.result||[]);req.onerror=()=>rej(req.error)});db.close();
  const seen=new Set(local.map(f=>f.id)),br=[];for(const f of found){if(seen.has(f.id)||!fv2Match(f,toks))continue;seen.add(f.id);usdaFoodsCache[f.id]=f;br.push(f);if(br.length>=30)break}
  return br}catch(e){return[]}}
async function fv2Search(){const sh=fv2S().sh,q=sh.q.trim().toLowerCase(),ser=++sh.serial;if(!q){fv2List();return}
 const toks=fv2SearchTokens(q),local=fv2LocalFoods(toks);sh.list=local;fv2List();
 const br=await fv2UsdaFoods(toks,local);if(ser!==sh.serial)return;if(br.length){sh.list=local.concat(br);fv2List()}}
function fv2SheetInput(ev){const sh=fv2S().sh,t=ev.target;
 if(t.id==='fv2Q'){sh.q=t.value;clearTimeout(sh._t);sh._t=setTimeout(fv2Search,110)}
 else if(t.id==='fv2G'){sh.grams=Math.max(0,+t.value||0);fv2Live()}
 else if(t.dataset&&t.dataset.goal){fv2GoalLive(t.dataset.goal)}}
function fv2SheetClick(ev){const sh=fv2S().sh,t=ev.target;let b;
 if((b=t.closest('[data-s2]'))){const a=b.dataset.s2;
  if(a==='back'){fv2Back();return}
  if(a==='quick'){fv2Go('quick');return}
  if(a==='create'){fv2Go('create');return}
  if(a==='quicksave'){const k=Math.round(+document.getElementById('fv2QK').value||0);if(k<=0){fv2Snack('Enter the calories');return}const f=fv2QuickFood(k,document.getElementById('fv2QN').value);fv2Close();fv2Log(f,100,sh.slot);return}
  if(a==='log'){fv2DoLog();return}
  if(a==='fav'){const on=fv2ToggleFav(sh.pick);fv2Hap('tap');const bt=document.querySelector('.fv2-star');if(bt){bt.classList.toggle('on',on);bt.setAttribute('aria-pressed',on)}return}
  if(a==='step'){sh.grams=fv2StepGrams(sh.grams,+b.dataset.d);fv2Live(true);return}
  if(a==='mult'){sh.grams=fv2MultGrams(sh.pick,+b.dataset.x);fv2Live(true);return}
  if(a==='serv'){sh.grams=+b.dataset.g;fv2Live(true);return}
  if(a==='save'){fv2SaveEdit();return}
  if(a==='del'){if(fv2RemoveEntry(sh.edit))fv2Close();return}
  if(a==='again'){if(fv2LogAgain(sh.edit))fv2Close();return}
  if(a==='createsave'){fv2SaveCreate();return}
  if(a==='goalsave'){fv2SaveGoal();return}
  if(a==='preset'){fv2Preset(b.dataset.p);return}}
 if((b=t.closest('[data-s]'))&&b.closest('.fv2-pills')){sh.slot=b.dataset.s;b.parentNode.querySelectorAll('button').forEach(x=>x.classList.toggle('on',x===b));const lb=document.getElementById('fv2LogBtn');if(lb)fv2Live();return}
 if((b=t.closest('[data-tab]'))){sh.tab=b.dataset.tab;sh.q='';const q=document.getElementById('fv2Q');if(q)q.value='';b.parentNode.querySelectorAll('button').forEach(x=>x.classList.toggle('on',x===b));fv2List();return}
 if((b=t.closest('[data-q]'))){const f=sh.list[+b.dataset.q];if(!f)return;const u=f._grams?{g:f._grams}:fv2Unit(f);fv2Close();fv2Log(f,u.g,sh.slot);return}
 if((b=t.closest('[data-r]'))){const f=sh.list[+b.dataset.r];if(!f)return;sh.pick=f;sh.grams=f._grams||fv2Unit(f).g;fv2Go('portion');return}}
/* one step of the amount: 5 g below 50 g, 10 g below 200 g, 25 g above; d is 1 or -1; never below 1 g */
function fv2StepGrams(g,d){const st=g<50?5:g<200?10:25;return Math.max(1,Math.round((g+d*st)/st)*st)}
/* the amount for a multiple (½, 1, 1.5, 2, 3 times) of the food's first serving */
function fv2MultGrams(food,x){return Math.max(1,Math.round(fv2Unit(food).g*x*10)/10)}
/* a one-off food from calories alone (and an optional name), logged as 100 g of it */
function fv2QuickFood(kcal,name){return {id:'quick_'+Date.now(),n:String(name||'').trim()||'Quick calories',cat:'Quick',kcal:kcal,p:0,c:0,f:0,fib:0,sug:0,na:0,port:[{l:'1 entry',g:100}]}}
function fv2Totals(){const day=mealsDayKey();return fv2Tot(dayFoodLog(day))}
function fv2Portion(){const sh=fv2S().sh,f=sh.pick,ed=sh.view==='edit',s=scaleFood(f,sh.grams),w=fv2SheetWords();
 let sv='';fv2ServingsData(f,sh.grams).forEach(function(p){sv+='<button type="button" data-s2="serv" data-g="'+p.grams+'" class="'+(p.on?'on':'')+'">'+esc(p.label)+(p.small?'<small>'+p.grams+' g</small>':'')+'</button>'});
 const e0=ed?sh.edit:null;
 let imp='';fv2ImpactData(f,sh.grams,e0).forEach(function(d){imp+='<div class="fv2-im" style="--mc:'+d.color+'"><span>'+d.name+'</span><i><u style="width:'+d.before+'%"></u><b id="fv2Imb'+d.k+'" style="left:'+d.before+'%;width:'+d.add+'%"></b></i><em id="fv2Imv'+d.k+'">'+d.label+'</em></div>'});
 return fv2HeadFor(ed?'edit':'portion')+'<div class="fv2-pt"><div class="fv2-fh"><div><h3>'+esc(f.n)+'</h3><p>'+esc([f.brand,f.cat].filter(Boolean).join(' · ')||'Food')+'</p></div>'+(ed?'':'<button type="button" class="fv2-star'+(fv2IsFav(f)?' on':'')+'" data-s2="fav" aria-label="'+w.fav+'" aria-pressed="'+fv2IsFav(f)+'"><svg viewBox="0 0 24 24"><path d="M12 3.500l2.700 5.500 6 .9-4.400 4.200 1.100 6-5.400-2.900-5.400 2.900 1.100-6L3.300 9.900l6-.9z"/></svg></button>')+'</div>'
  +'<div class="fv2-big"><span id="fv2K">'+fv2Fmt(s.kcal)+'</span><small>'+w.kcal+'</small></div>'
  +'<div class="fv2-amt"><button type="button" data-s2="step" data-d="-1" aria-label="'+w.less+'">−</button><label><input id="fv2G" type="number" inputmode="decimal" min="1" value="'+sh.grams+'"><span>'+w.grams+'</span></label><button type="button" data-s2="step" data-d="1" aria-label="'+w.more+'">+</button></div>'
  +'<div class="fv2-serv">'+sv+'</div><div class="fv2-mult">'+FV2_MULTS.map(x=>'<button type="button" data-s2="mult" data-x="'+x+'">'+fv2MultLabel(x)+'</button>').join('')+'</div>'
  +'<div class="fv2-imp">'+imp+'<div class="fv2-sm" id="fv2Sm">'+fv2Small(s)+'</div></div>'
  +(ed?'':fv2SlotPills())+(ed?fv2SlotPills():'')+'</div>'
  +'<div class="fv2-foot">'+(ed?'<button type="button" class="ghost danger" data-s2="del">'+w.remove+'</button>'+(e0.date!==getToday()?'<button type="button" class="ghost" data-s2="again">'+w.again+'</button>':''):'')+'<button type="button" class="pri" id="fv2LogBtn" data-s2="'+(ed?'save':'log')+'">'+fv2LogLabel(s,ed)+'</button></div>'}
/* what an amount of a food adds to the day's protein, carbs and fat against the day's targets, for the bars of the portion view:
   `before` is how much of the target the day already has (an entry being edited is taken out of it), `add` how much this adds
   (both as percentages of the bar, together at most 100), `label` the words under the bar. e0 is the entry being edited, if any. */
function fv2ImpactData(f,grams,e0){const s=scaleFood(f,grams),g=fv2Goals(),base=fv2Totals(),bk=e0?{p:base.p-(+e0.p||0),c:base.c-(+e0.c||0),f:base.f-(+e0.f||0)}:base;
 return ['p','c','f'].map(function(k){const m=fv2Mac()[k],gl=g[k],a=bk[k],n=s[k];
  return {k:k,name:m[0],color:m[1],grams:n,goal:gl,before:gl?Math.min(100,a/gl*100):0,add:gl?Math.max(0,Math.min(100-a/gl*100,n/gl*100)):0,label:Math.round(n)+' g'+(gl?' · '+Math.round((a+n)/gl*100)+'%':'')}})}
/* the serving buttons of a food at an amount: its first four servings (each with its grams) and, unless it has one, 100 g;
   `on` marks the one the amount matches */
function fv2ServingsData(f,grams){const ports=(f.port||[]).slice(),out=ports.slice(0,4).map(function(p){return {label:fv2PL(p.l),grams:p.g,on:Math.abs(grams-p.g)<.5,small:true}});
 if(!ports.some(p=>p.g===100))out.push({label:'100 g',grams:100,on:grams===100,small:false});return out}
const FV2_MULTS=[.5,1,1.5,2,3];
function fv2MultLabel(x){return (x===.5?'½':x)+'×'}
function fv2Small(s){return '<span>Fiber <b>'+(+s.fib||0)+' g</b></span><span>Sugar <b>'+(+s.sug||0)+' g</b></span><span>Sodium <b>'+Math.round(+s.na||0)+' mg</b></span>'}
function fv2LogLabel(s,ed,slot){return (ed?'Save changes':'Add to '+fv2Slots().find(x=>x[0]===(slot||fv2S().sh.slot))[1])+' · '+fv2Fmt(s.kcal)+' kcal'}
function fv2Live(rewrite){const sh=fv2S().sh,s=scaleFood(sh.pick,sh.grams||0),e0=sh.view==='edit'?sh.edit:null;
 const k=document.getElementById('fv2K');if(k)k.textContent=fv2Fmt(s.kcal);
 fv2ImpactData(sh.pick,sh.grams||0,e0).forEach(function(d){const bb=document.getElementById('fv2Imb'+d.k),vv=document.getElementById('fv2Imv'+d.k);if(bb){bb.style.width=d.add+'%'}if(vv)vv.textContent=d.label});
 const sm=document.getElementById('fv2Sm');if(sm)sm.innerHTML=fv2Small(s);const lb=document.getElementById('fv2LogBtn');if(lb)lb.textContent=fv2LogLabel(s,sh.view==='edit');
 if(rewrite){const gi=document.getElementById('fv2G');if(gi)gi.value=sh.grams;document.querySelectorAll('.fv2-serv button').forEach(function(b){b.classList.toggle('on',Math.abs(+b.dataset.g-sh.grams)<.5)})}}
function fv2DoLog(){const sh=fv2S().sh;if(!sh.pick||!(sh.grams>0))return;const f=sh.pick,gr=sh.grams,sl=sh.slot;fv2Close();
 if(String(f.id).indexOf('custom_')!==0&&f.cat==='Logged'&&state.customFoods.every(x=>x.id!==f.id)){/* keep as is: per-100 numbers came from the entry */}
 fv2Log(f,gr,sl)}
function fv2SaveEdit(){const sh=fv2S().sh,e=sh.edit;if(!e)return;if(fv2EditEntry(e,sh.pick,sh.grams,sh.slot))fv2Close()}
function fv2Create(){const d=fv2CreateData(),fs=d.fields;return fv2HeadFor('create')+'<div class="fv2-form">'+fs.filter(f=>!f.group).map(fv2CreateFieldHtml).join('')
 +'<div class="fv2-grid">'+fs.filter(f=>f.group).map(fv2CreateFieldHtml).join('')+'</div>'
 +'<p class="fv2-hint">'+esc(d.hint)+'</p></div><div class="fv2-foot"><button type="button" class="pri" data-s2="createsave">'+esc(d.save)+'</button></div>'}
function fv2SaveCreate(){const v={};fv2CreateData().fields.forEach(x=>{const n=document.getElementById(x.id);v[x.key]=n?n.value:''});const f=fv2CreateFood(v);if(!f)return; // the form stays filled in
 const sh=fv2S().sh;sh.pick=f;sh.grams=fv2Unit(f).g;sh.view='portion';sh.stack=['search'];fv2Paint();fv2Hap('success')}
/* goals */
function fv2Goal(){const d=fv2GoalData(),w=fv2SheetWords();return fv2HeadFor('goal')+'<div class="fv2-form fv2-goal"><div class="fv2-gc"><button type="button" data-s2="goalstep" data-d="-'+d.cal.step+'" aria-label="'+w.less+'">−</button><label><input id="fv2GC" data-goal="cal" type="number" inputmode="numeric" value="'+d.cal.value+'"><span>'+d.cal.label+'</span></label><button type="button" data-s2="goalstep" data-d="'+d.cal.step+'" aria-label="'+w.more+'">+</button></div>'
 +'<div class="fv2-pre">'+d.presets.map(x=>'<button type="button" data-s2="preset" data-p="'+x.id+'">'+x.label+'<small>'+x.sub+'</small></button>').join('')+'</div>'
 +'<div class="fv2-grid">'+d.fields.map(x=>'<label style="--mc:'+x.color+'">'+x.label+'<input id="'+x.id+'" data-goal="'+x.key+'" type="number" inputmode="numeric" value="'+x.value+'"></label>').join('')+'</div>'
 +'<div class="fv2-gs" id="fv2Gs"></div><label>'+d.water.label+'<span class="u"><input id="fv2GW" type="number" inputmode="numeric" value="'+d.water.value+'"><em>'+d.water.unit+'</em></span></label></div><div class="fv2-foot"><button type="button" class="pri" data-s2="goalsave">'+d.save+'</button></div>'}
function fv2GoalLive(){const n=id=>+(document.getElementById(id)||{}).value||0,el=document.getElementById('fv2Gs');if(el)el.innerHTML=fv2GoalSumHtml(fv2GoalSum(n('fv2GC'),n('fv2GP'),n('fv2GCb'),n('fv2GF')))}
function fv2Preset(p){const g=fv2PresetGrams(p,document.getElementById('fv2GC').value);document.getElementById('fv2GP').value=g.p;document.getElementById('fv2GCb').value=g.c;document.getElementById('fv2GF').value=g.f;fv2GoalLive()}
function fv2SaveGoal(){const n=id=>(document.getElementById(id)||{}).value;if(!fv2SetGoals({cal:n('fv2GC'),p:n('fv2GP'),c:n('fv2GCb'),f:n('fv2GF'),water:n('fv2GW')}))return;fv2Close();renderMeals();fv2Hap('success')}
(function(){document.addEventListener('click',function(ev){const b=ev.target.closest&&ev.target.closest('#fv2Ov [data-s2="goalstep"]');if(!b)return;const i=document.getElementById('fv2GC');i.value=fv2GoalStep(i.value,+b.dataset.d);fv2GoalLive()});
 document.addEventListener('keydown',function(ev){if(ev.key==='Escape'&&fv2S().sh.open)fv2Back()})})();

/* ---------- Settings: the optional USDA branded-foods database (import, count, remove). The import button used to live on the old Food page. ---------- */
function fv2UsdaRender(){
  const el=document.getElementById('usdaSettingsBody');if(!el)return;
  el.innerHTML='<div class="settingsline"><div style="flex:1"><strong>Branded foods (USDA)</strong><div class="muted" style="font-size:12px">An optional database of packaged foods, kept on this device only. Choose the compact USDA <b>.jsonl.gz</b> file once, then search it from Food, Add food.</div></div></div>'
   +'<div class="settingsline"><div style="flex:1;min-width:0"><span id="usdaImportStatus" class="muted" style="font-size:12.5px">Checking…</span></div><button type="button" class="btn small" onclick="document.getElementById(\'usdaImportFile\').click()">Import file</button><input id="usdaImportFile" type="file" accept=".gz,application/gzip" style="display:none" onchange="importUsdaFoods(event).then(fv2UsdaRender)"><button type="button" id="usdaRemoveBtn" class="btn small secondary" style="margin-left:6px" onclick="fv2UsdaClear(this)">Remove</button></div>';
  usdaCount().then(function(n){usdaStatus(n?'USDA database ready · '+n.toLocaleString()+' foods stored on this device':'Not imported yet · optional');const b=document.getElementById('usdaRemoveBtn');if(b)b.disabled=!n})
}
function fv2UsdaClear(b){
  if(!b._arm){b._arm=1;b.textContent='Tap again to remove';setTimeout(function(){b._arm=0;if(b.isConnected)b.textContent='Remove'},4000);return}
  b._arm=0;
  const req=indexedDB.deleteDatabase(USDA_DB_NAME);
  req.onsuccess=req.onerror=req.onblocked=function(){try{for(const k in usdaFoodsCache)delete usdaFoodsCache[k]}catch(e){}toast('USDA database removed');fv2UsdaRender()}
}
(function(){const nav=navigate;navigate=function(v){const r=nav.apply(this,arguments);if(v==='settings')try{fv2UsdaRender()}catch(e){}return r};setTimeout(function(){try{fv2UsdaRender()}catch(e){}},1500)})();
