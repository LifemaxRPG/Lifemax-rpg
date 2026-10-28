/* ============================================================================
   Smart Diet (Food tab, "Plan" and "Shopping" panes): what to eat today, one-tap logging, the weekly shopping list.
   Data: #smartDiet (built by tools/make_smartdiet.py from the validated Gemini files): a catalog of ingredients and two
   7-day diets for the Athlete class. Each ingredient line has a "flex" range; the day is scaled by one factor that is
   solved so the day lands on the user's calorie target for their activity level. Preferences (vegetarian, dairy-free)
   apply the meal swaps. Saved in state.mealsMeta.sd. Logged plan meals are normal foodLog entries with entry.sd.
   ============================================================================ */
function sdData(){return window.__sdData||(window.__sdData=(function(){try{return JSON.parse(document.getElementById('smartDiet').textContent)}catch(e){console.warn('[smart diet]',e);return {cat:{},plans:{}}}})())}
function sdLevels(){return [['rest','Rest',0.9],['light','Light',1],['moderate','Moderate',1.12],['hard','Hard',1.25]]}
function sdS(){mealsEnsure();const m=state.mealsMeta;if(!m.sd)m.sd={diet:'standard',level:'auto',veg:false,df:false,over:{},shop:{}};return m.sd}
function sdUi(){const S=fv2S();if(!S.sd)S.sd={next:false};return S.sd}
function sdSlotOf(s){return s==='breakfast'?'breakfast':s==='lunch'?'lunch':s==='dinner'?'dinner':'snacks'}
function sdSlotName(s){return {breakfast:'Breakfast',lunch:'Lunch',dinner:'Dinner',snack:'Snack',pre_workout:'Before training',post_workout:'After training'}[s]||s}
function sdWeekday(day){return ['sun','mon','tue','wed','thu','fri','sat'][new Date(day+'T12:00:00').getDay()]}
function sdMonday(day){const d=new Date(day+'T12:00:00'),dow=(d.getDay()+6)%7;return fv2DayShift(day,-dow)}
/* ---------------------------------------------------------------- the maths */
function sdSwapOn(sw,S){return (S.veg&&sw.label==='Vegetarian')||(S.df&&/^Dairy-free/.test(sw.label))}
function sdMealIngs(meal,S){
  const out=meal.ingredients.map(function(i){return {ing:i.ing,g:i.g,flex:i.flex,label:i.label}});
  (meal.swaps||[]).forEach(function(sw){if(!sdSwapOn(sw,S))return;const ix=out.findIndex(function(i){return i.ing===sw.replace});if(ix>=0)out[ix]={ing:sw.with,g:sw.g,flex:out[ix].flex,label:null}});
  return out;
}
var SD_DAIRY=['ing_milk_2','ing_whey_protein','ing_greek_yogurt_nonfat','ing_cottage_cheese_lowfat'];
var SD_MEAT=['ing_chicken_breast_raw','ing_chicken_rotisserie_cooked','ing_beef_ground_90_raw','ing_turkey_ground_raw','ing_turkey_deli_sliced_low_sodium','ing_pork_tenderloin_raw','ing_shrimp_raw','ing_tuna_canned_water','ing_salmon_canned_water'];
/* does the meal, with the swaps this person's preferences turn on, still break a preference? */
function sdMealOk(m,S){const ids=sdMealIngs(m,S).map(function(i){return i.ing});return !((S.df&&ids.some(function(x){return SD_DAIRY.indexOf(x)>=0}))||(S.veg&&ids.some(function(x){return SD_MEAT.indexOf(x)>=0})))}
/* a meal that cannot be made to fit is replaced by another of the same kind that does */
function sdFit(P,m,S,k){if(sdMealOk(m,S))return m;const all=Object.keys(P.meals).map(function(x){return P.meals[x]}),fam=['snack','pre_workout','post_workout'];
  let c=all.filter(function(x){return x.slot===m.slot&&sdMealOk(x,S)});
  if(!c.length&&fam.indexOf(m.slot)>=0)c=all.filter(function(x){return fam.indexOf(x.slot)>=0&&sdMealOk(x,S)});
  return c.length?c[k%c.length]:m}
function sdFactor(i,s){return i.flex?Math.max(i.flex.min,Math.min(i.flex.max,s)):1}
function sdTotals(ings,s){const C=sdData().cat,t={kcal:0,p:0,c:0,f:0,fib:0,sug:0,na:0,g:0};
  ings.forEach(function(i){const g=i.g*sdFactor(i,s),n=C[i.ing].p;t.g+=g;t.kcal+=g/100*n.kcal;t.p+=g/100*n.p;t.c+=g/100*n.c;t.f+=g/100*n.f;t.fib+=g/100*n.fiber;t.sug+=g/100*n.sugar;t.na+=g/100*n.sodium_mg});return t}
function sdSolve(lists,target){const all=[].concat.apply([],lists),k=function(s){return sdTotals(all,s).kcal};
  let lo=0.3,hi=2;if(k(lo)>=target)return lo;if(k(hi)<=target)return hi;
  for(let n=0;n<40;n++){const m=(lo+hi)/2;if(k(m)<target)lo=m;else hi=m}return (lo+hi)/2}
function sdActivityMin(day){let m=0;try{(state.activities||[]).forEach(function(a){if(a&&a.date&&new Date(a.date).toLocaleDateString('en-CA')===day&&['physique','endurance','adventure'].indexOf(a.primary)>=0)m+=Number(a.duration)||0})}catch(e){}return m}
function sdAutoLevel(day,w,forShop){if(forShop)return w.type==='training'?'light':'rest';const m=sdActivityMin(day);return m>=60?'hard':m>=30?'moderate':m>0?'light':(w.type==='training'?'light':'rest')}
/* the plan for one day. Meals you have logged keep what you logged, skipped meals drop out, and the meals still to come are
   scaled so the whole day lands on the target given everything already eaten (including food that was not on the plan). */
function sdLogged(day,it,i){return dayFoodLog(day).find(function(e){return e.sd&&e.sd.meal===it.meal.id&&e.sd.i===i})}
function sdDay(day,forShop){
  const S=sdS(),D=sdData(),P=D.plans[S.diet]||D.plans.standard;if(!P)return null;
  const w=P.week.find(function(x){return x.day===sdWeekday(day)});if(!w)return null;
  const over=(forShop?{}:S.over[day])||{},ids=w.meals.map(function(id,i){return over[i]&&P.meals[over[i]]?over[i]:id});
  const dk=['mon','tue','wed','thu','fri','sat','sun'].indexOf(w.day),meals=ids.map(function(id,i){return sdFit(P,P.meals[id],S,dk+i)}),lists=meals.map(function(m){return sdMealIngs(m,S)});
  const auto=S.level==='auto',level=auto||!sdLevels().some(function(l){return l[0]===S.level})?sdAutoLevel(day,w,forShop):S.level;
  const base=Math.max(0,+(state.mealsMeta&&state.mealsMeta.calGoal)||0)||P.ref.kcal,mult=sdLevels().find(function(l){return l[0]===level})[2],target=Math.round(base*mult/10)*10;
  const skip=forShop?{}:(S.skip&&S.skip[day])||{},entries=forShop?[]:dayFoodLog(day);
  const items=meals.map(function(m,i){const it={meal:m,ings:lists[i],i:i,skipped:!!skip[i]};const e=forShop?null:sdLogged(day,{meal:m},i);if(e){it.logged=e;it.tot={kcal:+e.kcal||0,p:+e.p||0,c:+e.c||0,f:+e.f||0,fib:+e.fib||0,sug:+e.sug||0,na:+e.na||0,g:+e.grams||0}}return it});
  const eaten=entries.reduce(function(a,e){return a+(+e.kcal||0)},0),open=items.filter(function(it){return !it.logged&&!it.skipped}),openLists=open.map(function(it){return it.ings});
  const s0=sdSolve(lists,target),rem=Math.max(0,target-eaten),s=open.length?sdSolve(openLists,rem):1;
  items.forEach(function(it){if(it.logged)return;it.tot=it.skipped?{kcal:0,p:0,c:0,f:0,fib:0,sug:0,na:0,g:0}:sdTotals(it.ings,s)});
  const sum=function(list){return list.reduce(function(a,it){['kcal','p','c','f','fib','sug','na','g'].forEach(function(k){a[k]+=it.tot[k]});return a},{kcal:0,p:0,c:0,f:0,fib:0,sug:0,na:0,g:0})};
  const planTot=sum(items),extra={kcal:0,p:0,c:0,f:0,fib:0};entries.forEach(function(e){if(!e.sd){extra.kcal+=+e.kcal||0;extra.p+=+e.p||0;extra.c+=+e.c||0;extra.f+=+e.f||0;extra.fib+=+e.fib||0}});
  const all=[].concat.apply([],openLists),kmin=sdTotals(all,0.3).kcal,kmax=sdTotals(all,2).kcal;
  const tot={kcal:planTot.kcal+extra.kcal,p:planTot.p+extra.p,c:planTot.c+extra.c,f:planTot.f+extra.f,fib:planTot.fib+extra.fib,na:planTot.na,sug:planTot.sug,g:planTot.g};
  const nextIdx=items.findIndex(function(it){return !it.logged&&!it.skipped});
  return {day:day,P:P,w:w,items:items,s:s,s0:s0,level:level,auto:auto,target:target,base:base,tot:tot,eaten:eaten,extra:extra,rem:rem,next:nextIdx,openN:open.length,kmin:kmin,kmax:kmax,actMin:sdActivityMin(day)};
}
function sdLogMeal(day,i,quiet){
  const d=sdDay(day);if(!d)return null;const it=d.items[i];if(!it||sdLogged(day,it,i))return null;const t=it.tot,r=function(v){return Math.round(v*10)/10};
  const e={id:'food_'+Date.now()+'_'+Math.random().toString(36).slice(2,6),date:day,slot:sdSlotOf(it.meal.slot),foodId:'smartdiet:'+it.meal.id,name:it.meal.name,grams:Math.max(1,Math.round(t.g)),kcal:Math.round(t.kcal),kcalEstimated:false,p:r(t.p),c:r(t.c),f:r(t.f),fib:r(t.fib),sug:r(t.sug),na:Math.round(t.na),at:new Date().toISOString(),sd:{diet:sdS().diet,meal:it.meal.id,i:i}};
  mealsEnsure();state.foodLog.push(e);if(!quiet){fv2S().lastId=e.id;save();fv2Hap('success');renderMeals();fv2Snack(it.meal.name+' logged · '+fv2Fmt(e.kcal)+' kcal','Undo',function(){state.foodLog=state.foodLog.filter(function(x){return x.id!==e.id});save();renderMeals()})}
  return e;
}
/* ---------------------------------------------------------------- the Plan pane */
function sdRing(frac,col){const c=2*Math.PI*30,f=Math.max(0,Math.min(1,frac));return '<svg class="sd-ring" viewBox="0 0 72 72"><circle cx="36" cy="36" r="30" class="t"/><circle cx="36" cy="36" r="30" class="a" stroke="'+col+'" transform="rotate(-90 36 36)" style="stroke-dasharray:'+(c*f).toFixed(1)+' '+c.toFixed(1)+'"/></svg>'}
function sdPaneSwitch(pane){const I={log:'<path d="M4 7h16M4 12h16M4 17h10"/>',plan:'<rect x="4" y="5" width="16" height="15" rx="3"/><path d="M8 3v4M16 3v4M4 10h16M8 14h3"/>',shop:'<path d="M5 7h14l-1.500 11H6.500zM9 7a3 3 0 0 1 6 0"/>'};
  return '<div class="sd-panes" role="tablist">'+[['plan','Plan'],['log','Log'],['shop','Shopping']].map(function(p){return '<button type="button" role="tab" class="'+(pane===p[0]?'on':'')+'" data-a="pane" data-v="'+p[0]+'"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">'+I[p[0]]+'</svg>'+p[1]+'</button>'}).join('')+'</div>'}
function sdSettingsHtml(d){const S=sdS(),D=sdData();
  const diets=Object.keys(D.plans).map(function(k){return '<button type="button" class="'+(S.diet===k?'on':'')+'" data-a="sd-diet" data-v="'+k+'"><b>'+esc(D.plans[k].name)+'</b><small>'+(k==='simple'?'Fewest steps and ingredients':'Real recipes, batch cooking')+'</small></button>'}).join('');
  const lv=[['auto','Auto']].concat(sdLevels().map(function(l){return [l[0],l[1]]})).map(function(l){const on=S.level===l[0]||(l[0]==='auto'&&!sdLevels().some(function(x){return x[0]===S.level}));return '<button type="button" class="'+(on?'on':'')+'" data-a="sd-level" data-v="'+l[0]+'"><i style="--h:'+(['auto','rest','light','moderate','hard'].indexOf(l[0])*25)+'%"></i>'+l[1]+'</button>'}).join('');
  const lvName=sdLevels().find(function(l){return l[0]===d.level})[1];
  const why=S.level==='auto'?'Auto picked <b>'+lvName+'</b> '+(d.actMin?'because you logged '+d.actMin+' min of training today':'from today\'s '+(d.w.type==='training'?'training':'rest')+' day'):'You set <b>'+lvName+'</b> for this plan';
  return '<details class="sd-set"'+(sdUi().setOpen?' open':'')+'><summary data-a="sd-toggle"><span><b>'+esc(D.plans[S.diet].name)+'</b><small>'+(S.level==='auto'?'Auto · ':'')+lvName+(S.veg?' · Vegetarian':'')+(S.df?' · Dairy-free':'')+'</small></span><i>Adjust</i></summary><div class="sd-diets">'+diets+'</div><div class="sd-eyebrow">FUEL LEVEL</div><div class="sd-lv">'+lv+'</div><p class="sd-why">'+why+'. Target '+fv2Fmt(d.target)+' kcal'+(state.mealsMeta&&+state.mealsMeta.calGoal?' ('+fv2Fmt(d.base)+' base × '+sdLevels().find(function(l){return l[0]===d.level})[2]+')':'')+'.</p>'
   +'<div class="sd-prefs"><button type="button" class="'+(S.veg?'on':'')+'" data-a="sd-pref" data-v="veg">Vegetarian</button><button type="button" class="'+(S.df?'on':'')+'" data-a="sd-pref" data-v="df">Dairy-free</button></div></details>'}
function sdSlotWhen(s){return {breakfast:'Morning',lunch:'Midday',snack:'Afternoon',dinner:'Evening',pre_workout:'Before training',post_workout:'After training'}[s]||''}
function sdSlotColor(s){return {breakfast:'#f2b84a',lunch:'#5fd08a',dinner:'#6aa4f0',snack:'#ef7a85',pre_workout:'#b58cff',post_workout:'#4fd6c0'}[s]||'#ef7a85'}
function sdSlotIcon(s){
  const P={breakfast:'M3 18h18M6.5 18a5.5 5.5 0 0 1 11 0M12 5.5v2.5M4.8 9.8l1.7 1.7M19.2 9.8l-1.7 1.7',lunch:'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4',dinner:'M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z',snack:'M12 8c-3-2-7 0-7 4.5 0 4 3 8 5 8 1 0 1.5-.5 2-.5s1 .5 2 .5c2 0 5-4 5-8C19 8 15 6 12 8zM12 8c0-2 1-3.5 3-4',pre_workout:'M13 3 5 14h6l-1 7 8-11h-6z',post_workout:'M4 12a8 8 0 0 1 14-5.3L20 9M20 4v5h-5M20 12a8 8 0 0 1-14 5.3L4 15M4 20v-5h5',extra:'M12 5v14M5 12h14'}[s]||'M12 5v14M5 12h14';
  return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="'+P+'"/></svg>';
}
function sdSlotLetter(s){return {breakfast:'B',lunch:'L',dinner:'D',snack:'S',pre_workout:'Pre',post_workout:'Post'}[s]||'?'}
/* the fuel line: the day as one bar, a segment per meal sized by its calories */
function sdFuelLine(d){
  const ex=d.extra.kcal,segs=d.items.map(function(it){return {w:Math.max(it.skipped?sdTotals(it.ings,d.s).kcal:it.tot.kcal,60),it:it}}),tot=segs.reduce(function(a,x){return a+x.w},0)+(ex>0?Math.max(ex,60):0);
  let h='<div class="sd-fl" role="img" aria-label="Today\'s meals">';
  segs.forEach(function(x,i){const it=x.it,st=it.logged?'done':it.skipped?'skip':i===d.next?'next':'todo';h+='<div class="seg '+st+'" style="flex:'+x.w/tot+';--sc:'+sdSlotColor(it.meal.slot)+'"><i></i>'+sdSlotIcon(it.meal.slot)+(st==='done'?'<b class="ck"></b>':'')+'</div>'});
  if(ex>0)h+='<div class="seg extra" style="flex:'+Math.max(ex,60)/tot+'"><i></i>'+sdSlotIcon('extra')+'</div>';
  return h+'</div>';
}
/* a short line of advice from the numbers, in plain words */
function sdCoach(d){
  const today=d.day===getToday(),hr=new Date().getHours(),nx=d.next>=0?d.items[d.next]:null,done=d.items.filter(function(i){return i.logged}).length,skipped=d.items.filter(function(i){return i.skipped});
  const planP=d.items.reduce(function(a,i){return a+(i.skipped?0:(i.logged?i.tot.p:sdTotals(i.ings,d.s0).p))},0)+d.extra.p,eatenP=dayFoodLog(d.day).reduce(function(a,e){return a+(+e.p||0)},0);
  if(!nx&&d.openN===0&&(done||skipped.length))return {i:'✓',t:'Day complete. <b>'+Math.round(eatenP)+' g protein</b> and <b>'+Math.round(d.tot.fib)+' g fiber</b>. Nicely done.'};
  if(d.extra.kcal>40&&d.openN)return {i:'↺',t:'You ate <b>'+fv2Fmt(d.extra.kcal)+' kcal</b> outside the plan, so I '+(d.s<d.s0?'trimmed':'adjusted')+' the meals still to come'+(Math.abs(d.s/d.s0-1)>0.01?' by about <b>'+Math.round(Math.abs(d.s/d.s0-1)*100)+'%</b>':'')+' to keep your '+fv2Fmt(d.target)+' kcal.'};
  if(skipped.length&&d.openN)return {i:'↺',t:'You skipped <b>'+esc(skipped[0].meal.name)+'</b>, so the rest of the day grew '+(d.s>d.s0?'by about <b>'+Math.round((d.s/d.s0-1)*100)+'%</b>':'a little')+' to cover it.'};
  if(today&&nx&&hr>=14&&eatenP<planP*0.4)return {i:'!',t:'Protein is behind today. <b>'+esc(nx.meal.name)+'</b> brings <b>'+Math.round(nx.tot.p)+' g</b>.'};
  if(nx&&!done&&today&&hr<11)return {i:'☀',t:'Start with <b>'+esc(nx.meal.name)+'</b>. '+(d.w.type==='training'?'Carbs up front fuel today\'s session.':'A steady start keeps the day on track.')};
  if(nx&&d.level==='hard')return {i:'⚡',t:'Hard day: portions are at the high end. <b>'+esc(nx.meal.name)+'</b> is up next.'};
  if(nx)return {i:'›',t:'<b>'+esc(nx.meal.name)+'</b> is up next: <b>'+fv2Fmt(nx.tot.kcal)+' kcal</b>, <b>'+Math.round(nx.tot.p)+' g</b> protein.'};
  return {i:'✓',t:'Everything for today is handled.'};
}
function sdIngLine(i,s){const C=sdData().cat,g=Math.round(i.g*sdFactor(i,s)),n=C[i.ing].n;return '<li><span>'+esc(n)+'</span><b>'+g+' g</b></li>'}
function sdMealCard(d,it,idx){
  const m=it.meal,t=it.tot,U=sdUi(),ov=U.open&&U.open[d.day+':'+idx],open=ov===undefined?idx===d.next:ov,sc=sdSlotColor(m.slot),st=it.logged?'done':it.skipped?'skipped':'';
  const shown=it.skipped?sdTotals(it.ings,d.s):t;
  const tags=(m.tags||[]).filter(function(x){return x==='leftover'||x==='vegan'||x==='no_cook'||x==='shake'||x==='quick'}).map(function(x){return '<em>'+({leftover:'Leftover',vegan:'Vegan',no_cook:'No cooking',shake:'Shake',quick:'Quick'}[x])+'</em>'}).join('');
  const state=it.logged?'<span class="sd-st ok"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.200" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.500l4.500 4.500L19 7"/></svg></span>':it.skipped?'<span class="sd-st sk">skipped</span>':idx===d.next?'<span class="sd-st nx">next</span>':'';
  return '<details class="sd-meal '+st+(idx===d.next?' isnext':'')+'" style="--sc:'+sc+'"'+(open?' open':'')+' data-i="'+idx+'"><summary data-a="sd-tg" data-i="'+idx+'"><i class="dot"></i><span class="who"><small>'+sdSlotName(m.slot)+' · '+sdSlotWhen(m.slot)+'</small><b>'+esc(m.name)+'</b></span>'+state+'<span class="k"><b>'+fv2Fmt(it.skipped?shown.kcal:t.kcal)+'</b><small>kcal</small></span></summary>'
   +'<div class="sd-more"><div class="sd-tags">'+tags+'<em>'+(m.active_min||0)+' min</em></div>'+sdMacroBar(shown)+'<div class="sd-mm"><span style="color:#ff7a8a">P <b>'+Math.round(shown.p)+'</b></span><span style="color:#ffc15e">C <b>'+Math.round(shown.c)+'</b></span><span style="color:#4fd6c0">F <b>'+Math.round(shown.f)+'</b></span><span>Fiber <b>'+Math.round(shown.fib)+'</b></span></div>'
   +'<ul class="sd-ings">'+it.ings.map(function(i){return sdIngLine(i,d.s)}).join('')+'</ul>'
   +'<details class="sd-how"><summary>Method</summary><ol>'+m.steps.map(function(s){return '<li>'+esc(s)+'</li>'}).join('')+'</ol>'+((m.equipment||[]).length?'<p>You need: '+esc(m.equipment.join(', '))+'.</p>':'')+'</details>'
   +'<footer>'+(it.logged?'<span class="ok">Logged · '+fv2Fmt(it.logged.kcal)+' kcal</span><button type="button" class="ghost" data-a="sd-unlog" data-i="'+idx+'">Undo</button>':it.skipped?'<span class="sk">Skipped</span><button type="button" class="ghost" data-a="sd-unskip" data-i="'+idx+'">Restore</button>':'<button type="button" class="pri" data-a="sd-log" data-i="'+idx+'">Log this meal</button><button type="button" class="ghost" data-a="sd-skip" data-i="'+idx+'">Skip</button><button type="button" class="ghost" data-a="sd-swap" data-i="'+idx+'">Swap</button>')+'</footer></div></details>';
}
function sdMacroBar(t){const p=t.p*4,c=t.c*4,f=t.f*9,s=p+c+f||1;return '<span class="sd-mb"><i style="width:'+p/s*100+'%;background:#ff7a8a"></i><i style="width:'+c/s*100+'%;background:#ffc15e"></i><i style="width:'+f/s*100+'%;background:#4fd6c0"></i></span>'}
/* the next meal, up top: what it is, what is in it, and the buttons to act on it */
function sdNextCard(d){
  const i=d.next,it=d.items[i],m=it.meal,t=it.tot,U=sdUi(),key=d.day+':'+i,ing=!!(U.ing&&U.ing[key]),sc=sdSlotColor(m.slot);
  const tags=(m.tags||[]).filter(function(x){return x==='leftover'||x==='vegan'||x==='no_cook'||x==='shake'||x==='quick'}).map(function(x){return {leftover:'Leftover',vegan:'Vegan',no_cook:'No cooking',shake:'Shake',quick:'Quick'}[x]}).join(' · ');
  const cell=function(v,l,c,cls){return '<div class="'+(cls||'')+'"><b'+(c?' style="color:'+c+'"':'')+'>'+v+'</b><small>'+l+'</small></div>'};
  const top=it.ings.slice().sort(function(a,b){return b.g*sdFactor(b,d.s)-a.g*sdFactor(a,d.s)}).slice(0,4).map(function(x){return esc(sdData().cat[x.ing].n)}).join(' · ');
  return '<section class="sd-next" data-i="'+i+'" style="--sc:'+sc+'"><div class="sd-nh"><span class="sd-eyebrow"><i></i>Up next · '+esc(sdSlotName(m.slot))+' · '+esc(sdSlotWhen(m.slot))+'</span><span class="sd-nt">'+(m.active_min||0)+' min'+(tags?' · '+tags:'')+'</span></div>'
   +'<h3>'+esc(m.name)+'</h3>'
   +'<div class="sd-stats">'+cell(fv2Fmt(t.kcal),'kcal','','k')+cell(Math.round(t.p)+'<u>g</u>','protein','#ff7a8a')+cell(Math.round(t.c)+'<u>g</u>','carbs','#ffc15e')+cell(Math.round(t.f)+'<u>g</u>','fat','#4fd6c0')+'</div>'
   +(ing?'':'<p class="sd-top">'+top+'</p>')
   +(ing?'<ul class="sd-ings">'+it.ings.map(function(x){return sdIngLine(x,d.s)}).join('')+'</ul><ol class="sd-steps">'+m.steps.map(function(s){return '<li>'+esc(s)+'</li>'}).join('')+'</ol>'+((m.equipment||[]).length?'<p class="sd-eq">You need: '+esc(m.equipment.join(', '))+'.</p>':''):'')
   +'<button type="button" class="sd-go" data-a="sd-log" data-i="'+i+'">Log this meal</button>'
   +'<div class="sd-na"><button type="button" data-a="sd-ing" data-i="'+i+'">'+(ing?'Hide details':'Ingredients')+'</button><button type="button" data-a="sd-swap" data-i="'+i+'">Swap</button><button type="button" data-a="sd-skip" data-i="'+i+'">Skip</button></div></section>';
}
function sdPlanHtml(){
  const day=mealsDayKey(),d=sdDay(day);if(!d)return '<div class="fv2-none"><b>No plan for this day</b><span>The Smart Diet plan is built for the Athlete class.</span></div>';
  const co=sdCoach(d),prepToday=(d.P.prep||[]).filter(function(p){return p.when===sdWeekday(day)}),left=Math.max(0,d.target-d.eaten),pP=d.items.reduce(function(a,i){return a+(i.skipped?0:i.tot.p)},0)+d.extra.p;
  const eatenP=dayFoodLog(day).reduce(function(a,e){return a+(+e.p||0)},0);
  const warn=d.openN&&d.rem<d.kmin-5?'<p class="sd-warn">You are already close to your target: the meals still to come are at their smallest ('+fv2Fmt(d.kmin)+' kcal).</p>':d.openN&&d.rem>d.kmax+5?'<p class="sd-warn">The meals left top out at about '+fv2Fmt(d.kmax)+' kcal, so you will finish a little under your target.</p>':'';
  let h=sdPaneSwitch('plan')+'<section class="sd-day"><div class="sd-dl"><span class="sd-eyebrow">'+esc(d.w.note||'TODAY')+' · '+(d.w.type==='training'?'TRAINING DAY':'REST DAY')+'</span><span class="sd-left"><b>'+fv2Fmt(left)+'</b> kcal to go</span></div>'
   +'<div class="sd-pbar"><i style="width:'+Math.min(100,d.eaten/Math.max(1,d.target)*100)+'%"></i></div>'
   +'<div class="sd-dn"><span><b>'+fv2Fmt(d.eaten)+'</b> of '+fv2Fmt(d.tot.kcal)+' kcal</span><span>Protein <b>'+Math.round(eatenP)+'</b> / '+Math.round(pP)+' g</span></div></section>';
  h+=d.next>=0?sdNextCard(d):'<div class="sd-coach sd-done"><i>'+co.i+'</i><p>'+co.t+'</p></div>';
  if(d.next>=0)h+='<div class="sd-coach"><i>'+co.i+'</i><p>'+co.t+'</p></div>';
  h+=warn;
  if(prepToday.length)h+='<section class="sd-prep"><div class="sd-eyebrow">PREP TODAY</div>'+prepToday.map(function(p){return '<p><b>'+p.minutes+' min</b> '+esc(p.task)+'</p>'}).join('')+'</section>';
  h+=sdSettingsHtml(d);
  const rest=d.items.map(function(it,i){return i===d.next?'':sdMealCard(d,it,i)}).join('');
  if(rest)h+='<div class="sd-eyebrow sd-rest">'+(d.next>=0?'REST OF TODAY':'TODAY')+'</div><div class="sd-timeline">'+rest+'</div>';
  h+='<div class="sd-actions"><button type="button" class="pri" data-a="sd-logall"'+(d.openN===0?' disabled':'')+'>Log the rest of the day</button></div>';
  h+='<p class="sd-foot">Portions follow your target and what you have already logged, using each ingredient\'s flexible range, so protein stays steady while carbs and fats move. Numbers are estimates from standard food-composition values.</p>';
  return h;
}
/* ---------------------------------------------------------------- the Shopping pane */
function sdShopList(start){
  const D=sdData(),agg={};
  for(let n=0;n<7;n++){const day=fv2DayShift(start,n),d=sdDay(day,true);if(!d)continue;
    d.items.forEach(function(it){it.ings.forEach(function(i){const g=i.g*sdFactor(i,d.s),a=agg[i.ing]||(agg[i.ing]={g:0,days:[]});a.g+=g;if(a.days.indexOf(n)<0)a.days.push(n)})})}
  return Object.keys(agg).map(function(id){const c=D.cat[id],a=agg[id],u=Math.max(1,c.s.unit_g),packs=Math.max(1,Math.ceil(a.g/u-0.04)),span=Math.max.apply(null,a.days)-Math.min.apply(null,a.days);
    return {id:id,n:c.n,g:Math.round(a.g),packs:packs,unit:c.s.unit,aisle:c.s.aisle,home:!!c.s.pantry,twice:c.s.keeps_days<=3&&span>=3&&a.days.length>=2}})
}
function sdAisleName(a){return {produce:'Produce',meat_seafood:'Meat & seafood',dairy_eggs:'Dairy & eggs',frozen:'Frozen',canned_jarred:'Cans & jars',bakery_grains:'Bread, grains & pasta',snacks:'Nuts & snacks',pantry:'Pantry',condiments_spices:'Sauces & spices'}[a]||a}
function sdShopHtml(){
  const S=sdS(),U=sdUi(),day=mealsDayKey(),start=fv2DayShift(sdMonday(day),U.next?7:0),list=sdShopList(start),chk=(S.shop[start]=S.shop[start]||{}),need=list.filter(function(x){return !x.home}),home=list.filter(function(x){return x.home});
  const got=need.filter(function(x){return chk[x.id]}).length,order=['produce','meat_seafood','dairy_eggs','frozen','canned_jarred','bakery_grains','snacks','pantry','condiments_spices'];
  const row=function(x){return '<button type="button" class="sd-row'+(chk[x.id]?' on':'')+'" data-a="sd-chk" data-id="'+esc(x.id)+'"><i><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.200" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.500l4.500 4.500L19 7"/></svg></i><span class="n">'+esc(x.n)+(x.twice?'<em>Buy twice, goes off</em>':'')+'</span><span class="q"><b>'+x.packs+' × '+esc(x.unit)+'</b><small>'+fv2Fmt(x.g)+' g needed</small></span></button>'};
  let h=sdPaneSwitch('shop')+'<div class="sd-weeksw"><button type="button" class="'+(!U.next?'on':'')+'" data-a="sd-week" data-v="this">This week</button><button type="button" class="'+(U.next?'on':'')+'" data-a="sd-week" data-v="next">Next week</button></div>'
   +'<section class="sd-hero sd-shop-h"><div class="sd-hl"><div class="sd-eyebrow">'+esc(sdData().plans[S.diet].name.toUpperCase())+' · WEEK OF '+new Date(start+'T12:00:00').toLocaleDateString(undefined,{month:'short',day:'numeric'}).toUpperCase()+'</div><div class="sd-big"><b>'+got+'/'+need.length+'</b><small>in the basket</small></div></div><div class="sd-hr">'+sdRing(need.length?got/need.length:0,'var(--good)')+'</div></section>'
   +'<div class="sd-actions"><button type="button" class="ghost" data-a="sd-copy">Copy list</button><button type="button" class="ghost" data-a="sd-clear">Clear ticks</button></div>';
  order.forEach(function(a){const items=need.filter(function(x){return x.aisle===a}).sort(function(p,q){return p.n.localeCompare(q.n)});if(items.length)h+='<section class="sd-aisle"><h4>'+sdAisleName(a)+'<span>'+items.length+'</span></h4>'+items.map(row).join('')+'</section>'});
  if(home.length)h+='<details class="sd-home"><summary>Probably at home <span>'+home.length+'</span></summary>'+home.sort(function(p,q){return p.n.localeCompare(q.n)}).map(row).join('')+'</details>';
  const tips=(sdData().plans[S.diet].notes||[]).map(function(n){return '<p>'+esc(n)+'</p>'}).join('');
  return h+'<section class="sd-prep"><div class="sd-eyebrow">TIPS</div>'+tips+'<p>Quantities are for all seven days at your current fuel level. Packs are rounded up, so you may have some left over.</p></section>';
}
function sdShopText(){const S=sdS(),U=sdUi(),start=fv2DayShift(sdMonday(mealsDayKey()),U.next?7:0),chk=S.shop[start]||{},list=sdShopList(start).filter(function(x){return !x.home});
  const order=['produce','meat_seafood','dairy_eggs','frozen','canned_jarred','bakery_grains','snacks','pantry','condiments_spices'];let t='Shopping list, week of '+start+'\n';
  order.forEach(function(a){const it=list.filter(function(x){return x.aisle===a});if(it.length){t+='\n'+sdAisleName(a)+'\n';it.forEach(function(x){t+=(chk[x.id]?'[x] ':'[ ] ')+x.n+' - '+x.packs+' x '+x.unit+'\n'})}});return t}
/* ---------------------------------------------------------------- actions */
function sdAct(b){const a=b.dataset.a,S=sdS(),U=sdUi(),day=mealsDayKey();
  if(a==='sd-toggle'){U.setOpen=!U.setOpen;return}
  if(a==='sd-ing'){U.ing=U.ing||{};const k=day+':'+b.dataset.i;U.ing[k]=!U.ing[k];renderMeals();return}
  if(a==='sd-tg'){U.open=U.open||{};const k=day+':'+b.dataset.i,det=b.parentNode;U.open[k]=!det.open;return}
  if(a==='sd-diet'||a==='sd-level'||a==='sd-pref')U.setOpen=true;
  if(a==='sd-diet'){S.diet=b.dataset.v;save();renderMeals()}
  else if(a==='sd-level'){S.level=b.dataset.v;save();renderMeals()}
  else if(a==='sd-pref'){S[b.dataset.v]=!S[b.dataset.v];save();renderMeals()}
  else if(a==='sd-log'){if(S.skip&&S.skip[day])delete S.skip[day][+b.dataset.i];sdLogMeal(day,+b.dataset.i)}
  else if(a==='sd-skip'){S.skip=S.skip||{};(S.skip[day]=S.skip[day]||{})[+b.dataset.i]=true;save();fv2Hap('tap');renderMeals()}
  else if(a==='sd-unskip'){if(S.skip&&S.skip[day])delete S.skip[day][+b.dataset.i];save();renderMeals()}
  else if(a==='sd-unlog'){const d=sdDay(day),it=d&&d.items[+b.dataset.i];const e=it&&sdLogged(day,it,+b.dataset.i);if(e){state.foodLog=state.foodLog.filter(function(x){return x.id!==e.id});save();renderMeals()}}
  else if(a==='sd-logall'){const d=sdDay(day);if(!d)return;const ids=[];d.items.forEach(function(it,i){const e=sdLogMeal(day,i,true);if(e)ids.push(e.id)});if(ids.length){save();fv2Hap('success');renderMeals();fv2Snack('Logged '+ids.length+' meal'+(ids.length>1?'s':''),'Undo',function(){state.foodLog=state.foodLog.filter(function(x){return ids.indexOf(x.id)<0});save();renderMeals()})}}
  else if(a==='sd-swap')sdOpenSwap(+b.dataset.i);
  else if(a==='sd-week'){U.next=b.dataset.v==='next';renderMeals()}
  else if(a==='sd-chk'){const start=fv2DayShift(sdMonday(day),U.next?7:0),c=(S.shop[start]=S.shop[start]||{});c[b.dataset.id]=!c[b.dataset.id];if(!c[b.dataset.id])delete c[b.dataset.id];save();fv2Hap('tap');renderMeals()}
  else if(a==='sd-clear'){const start=fv2DayShift(sdMonday(day),U.next?7:0);S.shop[start]={};save();renderMeals()}
  else if(a==='sd-copy'){const t=sdShopText();try{navigator.clipboard.writeText(t).then(function(){fv2Snack('Shopping list copied')},function(){fv2Snack('Could not copy')})}catch(e){fv2Snack('Could not copy')}}
}
/* swap a planned meal for another of the same kind */
function sdOv(){let o=document.getElementById('sdOv');if(o)return o;o=document.createElement('div');o.id='sdOv';o.className='sheet-ov fv2-ov';o.innerHTML='<div class="sheet fv2-sheet" role="dialog" aria-modal="true"></div>';document.body.appendChild(o);
  o.addEventListener('click',function(ev){if(ev.target===o){sdCloseSwap();return}const b=ev.target.closest('[data-sw]');if(!b)return;
   const S=sdS(),day=mealsDayKey(),i=+o.dataset.i;if(b.dataset.sw==='x'){sdCloseSwap();return}
   S.over[day]=S.over[day]||{};if(b.dataset.sw==='reset')delete S.over[day][i];else S.over[day][i]=b.dataset.sw;save();sdCloseSwap();renderMeals();fv2Hap('tap')});return o}
function sdCloseSwap(){const o=document.getElementById('sdOv');if(!o)return;const p=o.querySelector('.sheet');p&&p.classList.remove('fv2-in');o.classList.remove('show');document.body.classList.remove('noscroll')}
function sdOpenSwap(i){
  const day=mealsDayKey(),d=sdDay(day);if(!d)return;const cur=d.items[i].meal,o=sdOv(),P=d.P,same=function(m){return m.slot===cur.slot||(['snack','pre_workout','post_workout'].indexOf(cur.slot)>=0&&['snack','pre_workout','post_workout'].indexOf(m.slot)>=0&&m.slot===cur.slot)};
  const S=sdS(),list=Object.keys(P.meals).map(function(k){return P.meals[k]}).filter(same).filter(function(m){return m.id!==cur.id&&sdMealOk(m,S)});
  const rows=list.map(function(m){const t=sdTotals(sdMealIngs(m,S),d.s);return '<button type="button" class="sd-sw" data-sw="'+esc(m.id)+'"><span><b>'+esc(m.name)+'</b><small>'+(m.active_min||0)+' min · P '+Math.round(t.p)+' C '+Math.round(t.c)+' F '+Math.round(t.f)+((m.tags||[]).indexOf('leftover')>=0?' · leftover':'')+'</small></span><em>'+fv2Fmt(t.kcal)+'<small>kcal</small></em></button>'}).join('');
  o.dataset.i=i;o.querySelector('.sheet').innerHTML='<div class="fv2-sh"><button type="button" class="bk" data-sw="x" aria-label="Close">✕</button><h2>Swap '+esc(sdSlotName(cur.slot).toLowerCase())+'</h2><span></span></div><div class="fv2-list sd-swl"><p class="sd-swn">Instead of <b>'+esc(cur.name)+'</b>. The rest of the day rescales to keep your target.</p>'+(rows||'<div class="fv2-none"><b>No other options</b></div>')+'</div>'
   +((sdS().over[day]&&sdS().over[day][i])?'<div class="fv2-foot"><button type="button" data-sw="reset">Back to the plan</button></div>':'');
  o.classList.add('show');document.body.classList.add('noscroll');requestAnimationFrame(function(){o.querySelector('.sheet').classList.add('fv2-in')});
}

/* Life Director links by the scheduled calendar date and meal slot, never by a
   persisted recipe ID or clock hour. Resolve again on every tap so swaps,
   preferences, portion changes, and logged/skipped meals stay current. */
function sdDirectorDay(ref){
  if(!ref)return null;
  if(ref.day){
    const day=String(ref.day);if(!/^\d{4}-\d{2}-\d{2}$/.test(day))return null;
    const d=new Date(day+'T12:00:00');return !isNaN(d)&&d.getFullYear()===+day.slice(0,4)&&d.getMonth()+1===+day.slice(5,7)&&d.getDate()===+day.slice(8,10)?day:null;
  }
  const epoch=Number(ref.startEpoch);if(!Number.isFinite(epoch)||epoch<=0)return null;
  const d=new Date(epoch*1000);if(isNaN(d))return null;
  return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');
}
function sdDirectorSlot(ref){
  const key=String(ref&&(ref.slot||ref.title)||'').trim().toLowerCase().replace(/[\s-]+/g,'_');
  return {breakfast:'breakfast',lunch:'lunch',dinner:'dinner',snack:'snack',snacks:'snack',before_training:'pre_workout',pre_workout:'pre_workout',after_training:'post_workout',post_workout:'post_workout'}[key]||null;
}
function sdDirectorMeal(ref){
  const day=sdDirectorDay(ref),slot=sdDirectorSlot(ref);if(!day||!slot)return null;
  const d=sdDay(day);if(!d)return null;
  const i=d.items.findIndex(function(it){return it.meal.slot===slot});if(i<0)return null;
  const it=d.items[i];return {day:day,index:i,slot:slot,name:it.meal.name,kcal:Math.round(it.tot.kcal),logged:!!it.logged,skipped:!!it.skipped};
}
function sdOpenDirectorMeal(ref){
  const day=sdDirectorDay(ref);if(!day)return false;
  const match=sdDirectorMeal(ref),U=sdUi();
  mealsDay=day;fv2S().pane='plan';
  if(match){const key=day+':'+match.index;U.open=U.open||{};U.ing=U.ing||{};U.open[key]=true;U.ing[key]=true}
  if(typeof lmaNavTo==='function')lmaNavTo('meals',{fast:true});else navigate('meals');
  renderMeals();
  if(!match){toast('No Smart Diet meal is linked to this block. Here is the plan for that day.');return true}
  requestAnimationFrame(function(){
    const root=document.getElementById('fv2Root');if(!root||mealsDayKey()!==day)return;
    const card=root.querySelector('.sd-next[data-i="'+match.index+'"],.sd-meal[data-i="'+match.index+'"]');if(!card)return;
    if(card.tagName==='DETAILS')card.open=true;
    const method=card.querySelector('.sd-how');if(method)method.open=true;
    card.setAttribute('tabindex','-1');card.focus({preventScroll:true});
    card.scrollIntoView({block:'start',behavior:typeof lmaMotionOff==='function'&&lmaMotionOff()?'auto':'smooth'});
  });
  return true;
}
window.LifeMaxMeals=Object.freeze({resolveScheduled:sdDirectorMeal,openScheduled:sdOpenDirectorMeal});
