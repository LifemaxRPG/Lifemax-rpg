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
function fv2ToggleFav(f){const a=fv2Favs(),i=a.findIndex(x=>x.id===f.id);if(i>=0)a.splice(i,1);else a.unshift({id:f.id,n:f.n,cat:f.cat||'',brand:f.brand||'',kcal:f.kcal,p:f.p,c:f.c,f:f.f,fib:f.fib||0,sug:f.sug||0,na:f.na||0,port:f.port||[]});save();return i<0}
function fv2PL(l){l=String(l||'serving').replace(/\bRACC\b/i,'serving');return l.charAt(0).toUpperCase()+l.slice(1)}
function fv2Unit(f){const p=(f.port||[])[0];return p?{g:p.g,l:fv2PL(p.l)}:{g:100,l:'100 g'}}
function fv2KcalFor(f,g){return scaleFood(f,g).kcal}

/* ------------------------------------------------------------------ logging */
function fv2Log(food,grams,slot,opts){
 mealsEnsure();grams=Math.max(1,Math.round((+grams||100)*10)/10);const s=scaleFood(food,grams),day=mealsDayKey();
 const e={id:'food_'+Date.now()+'_'+Math.random().toString(36).slice(2,6),date:day,slot:slot||fv2Slot(),foodId:food.id,name:food.n,grams,...s,at:new Date().toISOString()};
 state.foodLog.push(e);fv2S().lastId=e.id;save();fv2Hap('success');renderMeals();
 fv2Snack((food.n.length>26?food.n.slice(0,25)+'…':food.n)+' · '+fv2Fmt(s.kcal)+' kcal','Undo',function(){state.foodLog=state.foodLog.filter(x=>x.id!==e.id);save();renderMeals()});
 return e;
}
function fv2Snack(msg,act,fn){let el=document.getElementById('fv2Snack');if(!el){el=document.createElement('div');el.id='fv2Snack';el.className='fv2-snack';document.body.appendChild(el)}
 el.innerHTML='<span></span>'+(act?'<button type="button">'+esc(act)+'</button>':'');el.firstChild.textContent=msg;el.classList.add('on');
 if(act)el.querySelector('button').onclick=function(){el.classList.remove('on');fn&&fn()};clearTimeout(el._t);el._t=setTimeout(function(){el.classList.remove('on')},4200)}
function deleteMealEntry(id){mealsEnsure();state.foodLog=state.foodLog.filter(x=>x.id!==id);save();renderMeals()}
function fv2CopySlot(slot){const from=fv2DayShift(mealsDayKey(),-1),src=state.foodLog.filter(e=>e.date===from&&e.slot===slot);if(!src.length)return;
 const ids=src.map(e=>{const n=Object.assign({},e,{id:'food_'+Date.now()+'_'+Math.random().toString(36).slice(2,6),date:mealsDayKey(),at:new Date().toISOString()});state.foodLog.push(n);return n.id});
 save();fv2Hap('success');renderMeals();fv2Snack('Copied '+src.length+' item'+(src.length>1?'s':'')+' from yesterday','Undo',function(){state.foodLog=state.foodLog.filter(x=>ids.indexOf(x.id)<0);save();renderMeals()})}
function fv2AddWater(ml){state.waterLog=state.waterLog||[];const id='water_'+Date.now()+'_'+Math.random().toString(36).slice(2,6);state.waterLog.push({id,date:mealsDayKey(),ml,at:new Date().toISOString()});save();fv2Hap('tap');renderMeals();
 fv2Snack('+'+ml+' ml water','Undo',function(){state.waterLog=state.waterLog.filter(x=>x.id!==id);save();renderMeals()})}

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
 const pane=S.pane||'log';
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
 const big=document.getElementById('fv2Big');if(big&&(+big.dataset.from)!==(+big.dataset.to)&&!(window.fxReduced&&fxReduced())){const a=+big.dataset.from,b=+big.dataset.to,t0=performance.now();(function st(n){const k=Math.min(1,(n-t0)/650),e=1-Math.pow(1-k,3);big.textContent=fv2Fmt(a+(b-a)*e);if(k<1&&document.body.contains(big))requestAnimationFrame(st)})(t0)}
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
function fv2Head(title,back){return '<div class="fv2-sh"><button type="button" class="bk" data-s2="back" aria-label="'+(back?'Back':'Close')+'">'+(back?'‹':'✕')+'</button><h2>'+esc(title)+'</h2><span></span></div>'}
function fv2Paint(){const sh=fv2S().sh,p=document.querySelector('#fv2Ov .sheet');if(!p)return;let h='';
 if(sh.view==='search'){
  h=fv2Head('Add food')+'<div class="fv2-sb"><div class="fv2-si"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="M20 20l-3.500-3.500"/></svg><input id="fv2Q" type="search" placeholder="Search chicken, rice, yogurt…" autocomplete="off" value="'+esc(sh.q)+'" enterkeyhint="search"></div>'+fv2SlotPills()
   +'<div class="fv2-tabs"><button type="button" data-tab="recent" class="'+(sh.tab==='recent'?'on':'')+'">Recent</button><button type="button" data-tab="fav" class="'+(sh.tab==='fav'?'on':'')+'">Favorites</button><button type="button" data-tab="mine" class="'+(sh.tab==='mine'?'on':'')+'">My foods</button></div></div><div class="fv2-list" id="fv2List"></div>'
   +'<div class="fv2-foot"><button type="button" class="ghost" data-s2="quick">Quick calories</button><button type="button" class="ghost" data-s2="create">Create food</button></div>';
  p.innerHTML=h;fv2List();
 }else if(sh.view==='portion'||sh.view==='edit'){h=fv2Portion();p.innerHTML=h}
 else if(sh.view==='create'){h=fv2Create();p.innerHTML=h}
 else if(sh.view==='quick'){h=fv2Head('Quick calories',true)+'<div class="fv2-form"><label>Calories<input id="fv2QK" type="number" inputmode="numeric" min="0" placeholder="e.g. 450"></label><label>Name <small>(optional)</small><input id="fv2QN" type="text" maxlength="60" placeholder="Restaurant lunch"></label>'+fv2SlotPills()+'</div><div class="fv2-foot"><button type="button" class="pri" data-s2="quicksave">Add to '+esc(fv2Slots().find(s=>s[0]===sh.slot)[1])+'</button></div>';p.innerHTML=h;setTimeout(function(){const i=document.getElementById('fv2QK');i&&i.focus()},120)}
 else if(sh.view==='goal'){h=fv2Goal();p.innerHTML=h;fv2GoalLive()}}
function fv2Match(f,toks){const hay=((f.n||'')+' '+(f.brand||'')+' '+(f.cat||'')).toLowerCase();return toks.every(t=>hay.indexOf(t)>=0)}
function fv2Row(f,i,sub){const u=f._grams?{g:f._grams,l:f._grams+' g'}:fv2Unit(f);const k=fv2KcalFor(f,u.g);
 return '<div class="fv2-r" data-i="'+i+'"><button type="button" class="m" data-r="'+i+'"><b>'+esc(f.n)+'</b><span>'+esc((f.brand?f.brand+' · ':'')+(sub||u.l))+' · P '+Math.round(scaleFood(f,u.g).p)+' C '+Math.round(scaleFood(f,u.g).c)+' F '+Math.round(scaleFood(f,u.g).f)+'</span></button><span class="k">'+fv2Fmt(k)+'<small>kcal</small></span><button type="button" class="q" data-q="'+i+'" aria-label="Add '+esc(f.n)+'">+</button></div>'}
function fv2List(){const sh=fv2S().sh,box=document.getElementById('fv2List');if(!box)return;let list=[],note='';const q=sh.q.trim().toLowerCase();
 if(q){list=sh.list;note=list.length?'':'<div class="fv2-none"><b>No matches yet</b><span>Try another word, or create the food yourself.</span></div>'}
 else if(sh.tab==='recent'){list=fv2Recents(20);sh.list=list;note=list.length?'':'<div class="fv2-none"><b>Nothing logged yet</b><span>Search for a food above. Everything you log shows up here for one-tap repeats.</span></div>'}
 else if(sh.tab==='fav'){list=fv2Favs().map(f=>Object.assign({},f));sh.list=list;note=list.length?'':'<div class="fv2-none"><b>No favorites yet</b><span>Open any food and tap the star to keep it here.</span></div>'}
 else{list=(state.customFoods||[]).slice(0,60);sh.list=list;note=list.length?'':'<div class="fv2-none"><b>No custom foods</b><span>Create a food with its label values and it will live here.</span></div>'}
 box.innerHTML=list.map((f,i)=>fv2Row(f,i,q?'':(f._grams?'last '+f._grams+' g':''))).join('')+note}
async function fv2Search(){const sh=fv2S().sh,q=sh.q.trim().toLowerCase(),ser=++sh.serial;if(!q){fv2List();return}
 const toks=q.split(/\s+/).filter(Boolean);let local=allFoods().filter(f=>fv2Match(f,toks));
 local.sort((a,b)=>((a.n||'').toLowerCase().startsWith(toks[0])?0:1)-((b.n||'').toLowerCase().startsWith(toks[0])?0:1)||(a.n||'').length-(b.n||'').length);local=local.slice(0,25);sh.list=local;fv2List();
 try{const db=await usdaDb();const tx=db.transaction('foods','readonly');const idx=tx.objectStore('foods').index('terms');const req=idx.getAll(IDBKeyRange.bound(toks[0],toks[0]+'￿'),80);const found=await new Promise((res,rej)=>{req.onsuccess=()=>res(req.result||[]);req.onerror=()=>rej(req.error)});db.close();if(ser!==sh.serial)return;
  const seen=new Set(local.map(f=>f.id)),br=[];for(const f of found){if(seen.has(f.id)||!fv2Match(f,toks))continue;seen.add(f.id);usdaFoodsCache[f.id]=f;br.push(f);if(br.length>=30)break}
  if(br.length){sh.list=local.concat(br);fv2List()}}catch(e){}}
function fv2SheetInput(ev){const sh=fv2S().sh,t=ev.target;
 if(t.id==='fv2Q'){sh.q=t.value;clearTimeout(sh._t);sh._t=setTimeout(fv2Search,110)}
 else if(t.id==='fv2G'){sh.grams=Math.max(0,+t.value||0);fv2Live()}
 else if(t.dataset&&t.dataset.goal){fv2GoalLive(t.dataset.goal)}}
function fv2SheetClick(ev){const sh=fv2S().sh,t=ev.target;let b;
 if((b=t.closest('[data-s2]'))){const a=b.dataset.s2;
  if(a==='back'){fv2Back();return}
  if(a==='quick'){fv2Go('quick');return}
  if(a==='create'){fv2Go('create');return}
  if(a==='quicksave'){const k=Math.round(+document.getElementById('fv2QK').value||0);if(k<=0){fv2Snack('Enter the calories');return}const nm=(document.getElementById('fv2QN').value||'').trim()||'Quick calories';
   const f={id:'quick_'+Date.now(),n:nm,cat:'Quick',kcal:k,p:0,c:0,f:0,fib:0,sug:0,na:0,port:[{l:'1 entry',g:100}]};fv2Close();fv2Log(f,100,sh.slot);return}
  if(a==='log'){fv2DoLog();return}
  if(a==='fav'){const on=fv2ToggleFav(sh.pick);fv2Hap('tap');const bt=document.querySelector('.fv2-star');if(bt){bt.classList.toggle('on',on);bt.setAttribute('aria-pressed',on)}return}
  if(a==='step'){const d=+b.dataset.d,g=sh.grams,st=g<50?5:g<200?10:25;sh.grams=Math.max(1,Math.round((g+d*st)/st)*st);if(d<0&&sh.grams<1)sh.grams=1;fv2Live(true);return}
  if(a==='mult'){const u=fv2Unit(sh.pick);sh.grams=Math.max(1,Math.round(u.g*(+b.dataset.x)*10)/10);fv2Live(true);return}
  if(a==='serv'){sh.grams=+b.dataset.g;fv2Live(true);return}
  if(a==='save'){fv2SaveEdit();return}
  if(a==='del'){const id=sh.edit.id,e=sh.edit;state.foodLog=state.foodLog.filter(x=>x.id!==id);save();fv2Close();renderMeals();fv2Snack('Removed '+e.name,'Undo',function(){state.foodLog.push(e);save();renderMeals()});return}
  if(a==='again'){const e=sh.edit,n=Object.assign({},e,{id:'food_'+Date.now()+'_'+Math.random().toString(36).slice(2,6),date:getToday(),at:new Date().toISOString()});state.foodLog.push(n);save();fv2Close();mealsDay=getToday();renderMeals();fv2Hap('success');fv2Snack('Logged again today');return}
  if(a==='createsave'){fv2SaveCreate();return}
  if(a==='goalsave'){fv2SaveGoal();return}
  if(a==='preset'){fv2Preset(b.dataset.p);return}}
 if((b=t.closest('[data-s]'))&&b.closest('.fv2-pills')){sh.slot=b.dataset.s;b.parentNode.querySelectorAll('button').forEach(x=>x.classList.toggle('on',x===b));const lb=document.getElementById('fv2LogBtn');if(lb)fv2Live();return}
 if((b=t.closest('[data-tab]'))){sh.tab=b.dataset.tab;sh.q='';const q=document.getElementById('fv2Q');if(q)q.value='';b.parentNode.querySelectorAll('button').forEach(x=>x.classList.toggle('on',x===b));fv2List();return}
 if((b=t.closest('[data-q]'))){const f=sh.list[+b.dataset.q];if(!f)return;const u=f._grams?{g:f._grams}:fv2Unit(f);fv2Close();fv2Log(f,u.g,sh.slot);return}
 if((b=t.closest('[data-r]'))){const f=sh.list[+b.dataset.r];if(!f)return;sh.pick=f;sh.grams=f._grams||fv2Unit(f).g;fv2Go('portion');return}}
function fv2Totals(){const day=mealsDayKey();return fv2Tot(dayFoodLog(day))}
function fv2Portion(){const sh=fv2S().sh,f=sh.pick,ed=sh.view==='edit',s=scaleFood(f,sh.grams),g=fv2Goals(),base=fv2Totals(),ports=(f.port||[]).slice();
 let sv='';ports.slice(0,4).forEach(function(p){sv+='<button type="button" data-s2="serv" data-g="'+p.g+'" class="'+(Math.abs(sh.grams-p.g)<.5?'on':'')+'">'+esc(fv2PL(p.l))+'<small>'+p.g+' g</small></button>'});
 if(!ports.some(p=>p.g===100))sv+='<button type="button" data-s2="serv" data-g="100" class="'+(sh.grams===100?'on':'')+'">100 g</button>';
 const e0=ed?sh.edit:null,bk=e0?{kcal:base.kcal-(+e0.kcal||0),p:base.p-(+e0.p||0),c:base.c-(+e0.c||0),f:base.f-(+e0.f||0)}:base;
 let imp='';['p','c','f'].forEach(function(k){const m=fv2Mac()[k],gl=g[k],a=bk[k],n=s[k],tot=a+n;imp+='<div class="fv2-im" style="--mc:'+m[1]+'"><span>'+m[0]+'</span><i><u style="width:'+(gl?Math.min(100,a/gl*100):0)+'%"></u><b id="fv2Imb'+k+'" style="left:'+(gl?Math.min(100,a/gl*100):0)+'%;width:'+(gl?Math.max(0,Math.min(100-a/gl*100,n/gl*100)):0)+'%"></b></i><em id="fv2Imv'+k+'">'+Math.round(n)+' g'+(gl?' · '+Math.round(tot/gl*100)+'%':'')+'</em></div>'});
 return fv2Head(ed?'Edit entry':'Add food',!ed)+'<div class="fv2-pt"><div class="fv2-fh"><div><h3>'+esc(f.n)+'</h3><p>'+esc([f.brand,f.cat].filter(Boolean).join(' · ')||'Food')+'</p></div>'+(ed?'':'<button type="button" class="fv2-star'+(fv2IsFav(f)?' on':'')+'" data-s2="fav" aria-label="Favorite" aria-pressed="'+fv2IsFav(f)+'"><svg viewBox="0 0 24 24"><path d="M12 3.500l2.700 5.500 6 .9-4.400 4.200 1.100 6-5.400-2.900-5.400 2.900 1.100-6L3.300 9.900l6-.9z"/></svg></button>')+'</div>'
  +'<div class="fv2-big"><span id="fv2K">'+fv2Fmt(s.kcal)+'</span><small>kcal</small></div>'
  +'<div class="fv2-amt"><button type="button" data-s2="step" data-d="-1" aria-label="Less">−</button><label><input id="fv2G" type="number" inputmode="decimal" min="1" value="'+sh.grams+'"><span>grams</span></label><button type="button" data-s2="step" data-d="1" aria-label="More">+</button></div>'
  +'<div class="fv2-serv">'+sv+'</div><div class="fv2-mult">'+[.5,1,1.5,2,3].map(x=>'<button type="button" data-s2="mult" data-x="'+x+'">'+(x===.5?'½':x)+'×</button>').join('')+'</div>'
  +'<div class="fv2-imp">'+imp+'<div class="fv2-sm" id="fv2Sm">'+fv2Small(s)+'</div></div>'
  +(ed?'':fv2SlotPills())+(ed?fv2SlotPills():'')+'</div>'
  +'<div class="fv2-foot">'+(ed?'<button type="button" class="ghost danger" data-s2="del">Remove</button>'+(e0.date!==getToday()?'<button type="button" class="ghost" data-s2="again">Log today</button>':''):'')+'<button type="button" class="pri" id="fv2LogBtn" data-s2="'+(ed?'save':'log')+'">'+fv2LogLabel(s,ed)+'</button></div>'}
function fv2Small(s){return '<span>Fiber <b>'+(+s.fib||0)+' g</b></span><span>Sugar <b>'+(+s.sug||0)+' g</b></span><span>Sodium <b>'+Math.round(+s.na||0)+' mg</b></span>'}
function fv2LogLabel(s,ed){const sh=fv2S().sh;return (ed?'Save changes':'Add to '+fv2Slots().find(x=>x[0]===sh.slot)[1])+' · '+fv2Fmt(s.kcal)+' kcal'}
function fv2Live(rewrite){const sh=fv2S().sh,s=scaleFood(sh.pick,sh.grams||0),g=fv2Goals(),base=fv2Totals(),e0=sh.view==='edit'?sh.edit:null,bk=e0?{p:base.p-(+e0.p||0),c:base.c-(+e0.c||0),f:base.f-(+e0.f||0)}:base;
 const k=document.getElementById('fv2K');if(k)k.textContent=fv2Fmt(s.kcal);
 ['p','c','f'].forEach(function(m){const gl=g[m],a=bk[m],n=s[m],bb=document.getElementById('fv2Imb'+m),vv=document.getElementById('fv2Imv'+m);if(bb){bb.style.width=(gl?Math.max(0,Math.min(100-a/gl*100,n/gl*100)):0)+'%'}if(vv)vv.textContent=Math.round(n)+' g'+(gl?' · '+Math.round((a+n)/gl*100)+'%':'')});
 const sm=document.getElementById('fv2Sm');if(sm)sm.innerHTML=fv2Small(s);const lb=document.getElementById('fv2LogBtn');if(lb)lb.textContent=fv2LogLabel(s,sh.view==='edit');
 if(rewrite){const gi=document.getElementById('fv2G');if(gi)gi.value=sh.grams;document.querySelectorAll('.fv2-serv button').forEach(function(b){b.classList.toggle('on',Math.abs(+b.dataset.g-sh.grams)<.5)})}}
function fv2DoLog(){const sh=fv2S().sh;if(!sh.pick||!(sh.grams>0))return;const f=sh.pick,gr=sh.grams,sl=sh.slot;fv2Close();
 if(String(f.id).indexOf('custom_')!==0&&f.cat==='Logged'&&state.customFoods.every(x=>x.id!==f.id)){/* keep as is: per-100 numbers came from the entry */}
 fv2Log(f,gr,sl)}
function fv2SaveEdit(){const sh=fv2S().sh,e=sh.edit;if(!e)return;const s=scaleFood(sh.pick,sh.grams);Object.assign(e,{grams:sh.grams,slot:sh.slot},s);save();fv2Close();fv2S().lastId=e.id;renderMeals();fv2Hap('success')}
function fv2Create(){const sh=fv2S().sh;return fv2Head('Create food',true)+'<div class="fv2-form"><label>Name<input id="fv2CN" type="text" maxlength="80" placeholder="Homemade protein shake"></label><label>Brand <small>(optional)</small><input id="fv2CB" type="text" maxlength="60"></label>'
 +'<label>Serving size<span class="u"><input id="fv2CS" type="number" inputmode="decimal" min="1" value="100"><em>grams</em></span></label>'
 +'<div class="fv2-grid"><label>Calories<input id="fv2CK" type="number" inputmode="decimal" min="0"></label><label>Protein g<input id="fv2CP" type="number" inputmode="decimal" min="0" step="0.1"></label><label>Carbs g<input id="fv2CC" type="number" inputmode="decimal" min="0" step="0.1"></label><label>Fat g<input id="fv2CF" type="number" inputmode="decimal" min="0" step="0.1"></label></div>'
 +'<p class="fv2-hint">Enter the numbers for one serving, exactly as the label shows them.</p></div><div class="fv2-foot"><button type="button" class="pri" data-s2="createsave">Save and continue</button></div>'}
function fv2SaveCreate(){const v=id=>{const n=document.getElementById(id);return n?n.value:''},name=v('fv2CN').trim();if(!name){fv2Snack('Give it a name');return}
 const sv=Math.max(1,+v('fv2CS')||100),k=100/sv,r=x=>Math.round((+x||0)*k*100)/100;let kc=r(v('fv2CK'));const p=r(v('fv2CP')),c=r(v('fv2CC')),fa=r(v('fv2CF'));if(!kc&&(p||c||fa))kc=Math.round(p*4+c*4+fa*9);
 const f={id:'custom_'+Date.now(),n:name,brand:v('fv2CB').trim(),cat:'Custom',kcal:kc,p,c,f:fa,fib:0,sug:0,na:0,port:sv===100?[]:[{l:'1 serving',g:sv}]};mealsEnsure();state.customFoods.unshift(f);save();
 const sh=fv2S().sh;sh.pick=f;sh.grams=sv;sh.view='portion';sh.stack=['search'];fv2Paint();fv2Hap('success')}
/* goals */
function fv2Goal(){const m=state.mealsMeta||{},g=fv2Goals(),cal=g.cal||2000;return fv2Head('Daily targets')+'<div class="fv2-form fv2-goal"><div class="fv2-gc"><button type="button" data-s2="goalstep" data-d="-50" aria-label="Less">−</button><label><input id="fv2GC" data-goal="cal" type="number" inputmode="numeric" value="'+cal+'"><span>calories a day</span></label><button type="button" data-s2="goalstep" data-d="50" aria-label="More">+</button></div>'
 +'<div class="fv2-pre"><button type="button" data-s2="preset" data-p="bal">Balanced<small>30 / 40 / 30</small></button><button type="button" data-s2="preset" data-p="hi">High protein<small>40 / 35 / 25</small></button><button type="button" data-s2="preset" data-p="low">Low carb<small>30 / 25 / 45</small></button></div>'
 +'<div class="fv2-grid"><label style="--mc:#ff7a8a">Protein g<input id="fv2GP" data-goal="p" type="number" inputmode="numeric" value="'+(g.cal?g.p:Math.round(cal*.3/4))+'"></label><label style="--mc:#ffc15e">Carbs g<input id="fv2GCb" data-goal="c" type="number" inputmode="numeric" value="'+(g.cal?g.c:Math.round(cal*.4/4))+'"></label><label style="--mc:#4fd6c0">Fat g<input id="fv2GF" data-goal="f" type="number" inputmode="numeric" value="'+(g.cal?g.f:Math.round(cal*.3/9))+'"></label></div>'
 +'<div class="fv2-gs" id="fv2Gs"></div><label>Water goal<span class="u"><input id="fv2GW" type="number" inputmode="numeric" value="'+g.water+'"><em>ml</em></span></label></div><div class="fv2-foot"><button type="button" class="pri" data-s2="goalsave">Save targets</button></div>'}
function fv2GoalLive(){const n=id=>+(document.getElementById(id)||{}).value||0,k=n('fv2GP')*4+n('fv2GCb')*4+n('fv2GF')*9,c=n('fv2GC'),el=document.getElementById('fv2Gs');if(el)el.innerHTML='Your macros add up to <b>'+fv2Fmt(k)+' kcal</b>'+(c?' · '+(k>c+30?'<span class="bad">'+fv2Fmt(k-c)+' over your calorie target</span>':k<c-30?fv2Fmt(c-k)+' under':'matches your target'):'')}
function fv2Preset(p){const c=+document.getElementById('fv2GC').value||2000,sp={bal:[.3,.4,.3],hi:[.4,.35,.25],low:[.3,.25,.45]}[p];document.getElementById('fv2GP').value=Math.round(c*sp[0]/4);document.getElementById('fv2GCb').value=Math.round(c*sp[1]/4);document.getElementById('fv2GF').value=Math.round(c*sp[2]/9);fv2GoalLive()}
function fv2SaveGoal(){const n=id=>Math.max(0,Math.round(+(document.getElementById(id)||{}).value||0));mealsEnsure();const m=state.mealsMeta;m.calGoal=n('fv2GC');m.protGoal=n('fv2GP');m.carbGoal=n('fv2GCb');m.fatGoal=n('fv2GF');m.waterGoalMl=n('fv2GW')||2000;save();fv2Close();renderMeals();fv2Hap('success')}
(function(){document.addEventListener('click',function(ev){const b=ev.target.closest&&ev.target.closest('#fv2Ov [data-s2="goalstep"]');if(!b)return;const i=document.getElementById('fv2GC');i.value=Math.max(0,(+i.value||0)+(+b.dataset.d));fv2GoalLive()});
 document.addEventListener('keydown',function(ev){if(ev.key==='Escape'&&fv2S().sh.open)fv2Back()})})();
