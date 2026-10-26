/* ---------- v109 · Economy v2, second half: Sparks and the shop, perks, ascension, streak shields, rest tokens,
   nutrition and cat-care bonuses. XP earns Sparks one for one (decay never takes them back); Sparks buy cosmetics,
   toys, utilities and one equipped perk. ---------- */
const ECO_THEMES={emerald:['Emerald Surge','#34d399'],gold:['Solar Gold','#fbbf24'],violet:['Cyber Violet','#a78bfa'],crimson:['Crimson Iron','#f87171'],mono:['Mono Ink','#d4d4d8']};
const ECO_PERKS={scholar:['Scholar’s Flow','+10% XP on reading and language sessions of 45 min or more'],iron:['Iron Grace','+2 days of grace before Physique and Endurance start to decay'],exec:['Executive Edge','Life Director’s execution bonus rises from +10% to +20%'],compound:['Compound Habit','+15% XP on every daily quest']};
const ECO_TOY_PRICE={ball_b:150,yarn:150,feather:200,spring:300,wand:450};
const ECO_PRICE={theme:300,perk:600,rest:250,shield:250};
function ecoPerk(id){return !!state&&state.perk===id&&(state.perksOwned||[]).indexOf(id)>=0}
function ecoStars(a){return (state.attrStars||{})[a]||0}
function ecoStarXP(a,xp){return Math.round(xp*(1+.05*ecoStars(a)))}
function ecoSparks(){return Math.max(0,Math.round(Number(state.sparks)||0))}
/* every XP earned grants the same in Sparks; called from save() so every source is covered */
function ecoSparksSync(){
 let add=0;state.activities.forEach(a=>{if(!a.sp){a.sp=1;add+=Math.max(0,Number(a.xp)||0)}});
 if(add)state.sparks=(Number(state.sparks)||0)+add;
}
/* ---- ascension: at attribute level 20 an attribute can be banked for a star (+5% XP on that attribute) ---- */
function ecoAscend(id){
 const a=ATTRS.find(x=>x.id===id);if(!a)return;
 if(attrLevel(id).level<20){toast('Reach level 20 in '+a.short+' first');return}
 if(ecoStars(id)>=3){toast(a.short+' already has three stars');return}
 if(!confirm('Ascend '+a.name+'? Its level restarts at 1 (all '+attrXP(id).toLocaleString()+' XP stays banked in your character level) and it earns +5% XP for good.'))return;
 const c=attrXP(id);state.ascended=state.ascended||{};state.attrStars=state.attrStars||{};
 state.ascended[id]=(state.ascended[id]||0)+c;state.attrStars[id]=ecoStars(id)+1;
 save();toast(a.short+' ascended · '+'★'.repeat(ecoStars(id)));ecoShopRender();
}
/* ---- streak shields ---- */
function ecoActive(key){return ecoActsOn(key).some(a=>!ecoPassive(a))}
function ecoShieldTick(){
 const today=dayNum(getToday());
 if(state.shieldTick==null){state.shieldTick=today-1;return}
 state.shieldDays=state.shieldDays||{};
 let miss=state.shieldMiss||0,sh=state.streakShields||0;
 for(let n=state.shieldTick+1;n<=today-1;n++){
  const k=ecoDayKey(n),present=ecoActive(k)||ecoRestDay(k)||state.shieldDays[k];
  if(present){miss=0;continue}
  miss++;
  if(miss>=2&&sh>0&&state.activities.some(a=>!ecoPassive(a)&&dayNum(activityDate(a))>=n-30)){state.shieldDays[k]=true;sh--;miss=1;toast('A streak shield covered a missed day')}
 }
 state.shieldTick=today-1;state.shieldMiss=miss;
 const s=streak(),m=Math.floor(s/7);
 if(m>(state.shieldMilestone||0)){sh=Math.min(2,sh+(m-(state.shieldMilestone||0)));toast('Streak shield earned')}
 state.shieldMilestone=Math.min(m,Math.floor(s/7));
 state.streakShields=sh;
}
/* ---- nutrition: a day's calories within 15% of your goal ---- */
function ecoNutritionCheck(){
 if(!state||typeof dayFoodLog!=='function')return;
 const goal=Math.max(0,Number(state.mealsMeta&&state.mealsMeta.calGoal)||0);if(!goal)return;
 const day=getToday();state.nutriDone=state.nutriDone||{};if(state.nutriDone[day])return;
 const kcal=dayFoodLog(day).reduce((a,e)=>a+(Number(e.kcal)||0),0);
 if(kcal<goal*.85||kcal>goal*1.15)return;
 state.nutriDone[day]=1;
 const xp=ecoCap(ecoRestedTake(8),day);if(xp<=0)return;
 const p=Math.round(xp*.7),s=xp-p;
 state.activities.push({id:'nut_'+Date.now(),type:'nutrition',name:'Nutrition goal hit',icon:'heart',duration:0,skill:'Nutrition',primary:'wellbeing',secondary:'physique',xp,date:new Date().toISOString(),notes:kcal+' kcal against a goal of '+goal,xpSplit:[{attr:'wellbeing',xp:p},{attr:'physique',xp:s}]});
 save();toast('Nutrition goal hit · +'+xp+' XP');
}
/* ---- cat care: fed, played with and petted on the same day ---- */
function ecoCare(kind){
 const day=getToday();let c=state.careDay;if(!c||c.day!==day)c=state.careDay={day};
 c[kind]=1;if(!(c.feed&&c.play&&c.pet)||c.paid)return;c.paid=1;
 const xp=ecoCap(ecoRestedTake(10),day);if(xp<=0)return;
 state.activities.push({id:'care_'+Date.now(),type:'catcare',name:'Cat care: fed, played, petted',icon:'paw',duration:0,skill:'Cat Care',primary:'wellbeing',secondary:'social',xp,date:new Date().toISOString(),notes:'',xpSplit:[{attr:'wellbeing',xp:Math.round(xp*.7)},{attr:'social',xp:xp-Math.round(xp*.7)}]});
 save();toast('Cat care complete · +'+xp+' XP');
}
/* ---- the shop ---- */
function ecoApplyTheme(){
 let st=document.getElementById('ecoThemeCss');
 const t=ECO_THEMES[state&&state.theme];
 if(!t){if(st)st.remove();return}
 if(!st){st=document.createElement('style');st.id='ecoThemeCss';document.head.appendChild(st)}
 const c=t[1];
 st.textContent=':root:root,html.light-theme:root{--accent:'+c+';--accent-dim:color-mix(in srgb,'+c+' 18%,transparent);--accent-strong:color-mix(in srgb,'+c+' 78%,white)}';
}
function ecoSpend(n){if(ecoSparks()<n){toast('Not enough Sparks');return false}state.sparks=(Number(state.sparks)||0)-n;return true}
function ecoBuy(kind,id){
 state.perksOwned=state.perksOwned||[];state.themesOwned=state.themesOwned||[];state.toysBought=state.toysBought||{};
 if(kind==='theme'){
  if(state.themesOwned.indexOf(id)<0){if(!ecoSpend(ECO_PRICE.theme))return;state.themesOwned.push(id)}
  state.theme=state.theme===id?null:id;ecoApplyTheme();
 }else if(kind==='perk'){
  if(state.perksOwned.indexOf(id)<0){if(!ecoSpend(ECO_PRICE.perk))return;state.perksOwned.push(id)}
  state.perk=state.perk===id?null:id;
 }else if(kind==='toy'){
  if(state.toysBought[id])return;if(!ecoSpend(ECO_TOY_PRICE[id]))return;state.toysBought[id]=1;toast('Toy unlocked for your cat');
 }else if(kind==='rest'){
  const wk=Date.now()-7*864e5;if((state.restBoughtAt||0)>wk){toast('One rest token a week');return}
  if(!ecoSpend(ECO_PRICE.rest))return;state.restTokens=(state.restTokens||0)+1;state.restBoughtAt=Date.now();
 }else if(kind==='shield'){
  const mo=Date.now()-30*864e5;if((state.shieldBoughtAt||0)>mo){toast('One shield refill a month');return}
  if((state.streakShields||0)>=2){toast('Shields are already full');return}
  if(!ecoSpend(ECO_PRICE.shield))return;state.streakShields=2;state.shieldBoughtAt=Date.now();
 }
 save();ecoShopRender();try{ovRewardsRender()}catch(e){}
}
function ecoShopOpen(){
 if(document.querySelector('.rsheet'))return;
 const o=document.createElement('div');o.className='hsheet rsheet';o.setAttribute('role','dialog');o.setAttribute('aria-label','Rewards');
 o.addEventListener('click',e=>{const b=e.target.closest('[data-rs]');if(!b)return;const k=b.dataset.rs;
  if(k==='close')o.remove();else if(k==='buy')ecoBuy(b.dataset.kind,b.dataset.id);else if(k==='ascend')ecoAscend(b.dataset.id)});
 document.body.appendChild(o);ecoShopRender();
}
function ecoShopRender(){
 const o=document.querySelector('.rsheet');if(!o)return;
 const sp=ecoSparks(),own=a=>(a||[]).length;
 const btn=(kind,id,label,cls)=>'<button class="btn small '+(cls||'')+'" data-rs="buy" data-kind="'+kind+'" data-id="'+id+'">'+label+'</button>';
 let h='<header><button class="btn small secondary" data-rs="close">‹ Back</button><h2>Rewards</h2><span class="rs-bal">✦ '+sp.toLocaleString()+'</span></header><div class="hs-body">';
 h+='<div class="card"><p class="muted" style="margin:0 0 4px;font-size:13px">Every XP you earn also earns a Spark. Decay never takes Sparks back, and spending them never lowers your level.</p></div>';
 /* status */
 const sh=state.streakShields||0,tk=state.restTokens||0;
 h+='<div class="card"><h3>Streak and rest</h3><div class="ovh-chips"><div class="ovh-chip"><span>Streak</span><b>'+streak()+' days</b></div><div class="ovh-chip"><span>Shields</span><b>'+'🛡'.repeat(sh)+(sh?'':'none')+'</b></div><div class="ovh-chip"><span>Rest tokens</span><b>'+tk+'</b></div></div><p class="muted" style="font-size:12px;margin:8px 0 0">A shield is earned at every 7-day streak (up to 2) and covers a missed day. Two rest days a week are free; tokens cover extra ones.</p>'+
  '<ul class="hs-list"><li><div><b>Rest day token</b><small>One a week</small></div>'+btn('rest','x','✦ '+ECO_PRICE.rest)+'</li><li><div><b>Streak shield refill</b><small>Refills to 2, once a month</small></div>'+btn('shield','x','✦ '+ECO_PRICE.shield)+'</li></ul></div>';
 /* perks */
 h+='<div class="card"><h3>Perks <small class="muted">equip one</small></h3><ul class="hs-list">'+Object.keys(ECO_PERKS).map(id=>{const ow=(state.perksOwned||[]).indexOf(id)>=0,on=state.perk===id;
  return '<li><div><b>'+ECO_PERKS[id][0]+(on?' · equipped':'')+'</b><small>'+ECO_PERKS[id][1]+'</small></div>'+btn('perk',id,on?'Unequip':ow?'Equip':'✦ '+ECO_PRICE.perk,on?'secondary':'')+'</li>'}).join('')+'</ul></div>';
 /* themes */
 h+='<div class="card"><h3>Accent colours</h3><ul class="hs-list">'+Object.keys(ECO_THEMES).map(id=>{const ow=(state.themesOwned||[]).indexOf(id)>=0,on=state.theme===id;
  return '<li><div><b><i class="rs-dot" style="background:'+ECO_THEMES[id][1]+'"></i>'+ECO_THEMES[id][0]+(on?' · on':'')+'</b></div>'+btn('theme',id,on?'Turn off':ow?'Use':'✦ '+ECO_PRICE.theme,on?'secondary':'')+'</li>'}).join('')+'</ul></div>';
 /* toys */
 const toys=Object.keys(ECO_TOY_PRICE).filter(id=>typeof PET_TOYS!=='undefined'&&PET_TOYS[id]);
 h+='<div class="card"><h3>Cat toys <small class="muted">unlock early</small></h3><ul class="hs-list">'+toys.map(id=>{const bought=!!(state.toysBought||{})[id];
  return '<li><div><b>'+PET_TOYS[id].name+'</b><small>'+(bought?'Unlocked':'Opens on its own later, or now')+'</small></div>'+(bought?'<span class="muted">✓</span>':btn('toy',id,'✦ '+ECO_TOY_PRICE[id]))+'</li>'}).join('')+'</ul></div>';
 /* ascension */
 h+='<div class="card"><h3>Ascension</h3><p class="muted" style="font-size:12.5px;margin:0 0 6px">At attribute level 20 you can bank it for a star: the bar restarts at level 1, the XP still counts toward your character level, and that attribute earns +5% XP for good (three stars at most).</p><ul class="hs-list">'+
  ATTRS.map(a=>{const lv=attrLevel(a.id).level,st=ecoStars(a.id);
   return '<li><div><b>'+a.short+' <span style="color:var(--gold)">'+'★'.repeat(st)+'</span></b><small>Level '+lv+(lv>=20&&st<3?' · ready':'')+'</small></div>'+(st>=3?'<span class="muted">max</span>':lv>=20?'<button class="btn small" data-rs="ascend" data-id="'+a.id+'">Ascend</button>':'<span class="muted">'+(20-lv)+' to go</span>')+'</li>'}).join('')+'</ul></div>';
 h+='</div>';
 const top=(o.querySelector('.hs-body')||{}).scrollTop||0;o.innerHTML=h;const nb=o.querySelector('.hs-body');if(nb)nb.scrollTop=top;
}
/* ---- Overview tile ---- */
function ovRewardsRender(){
 const el=document.getElementById('ovRewards');if(!el)return;
 const sh=state.streakShields||0;
 el.innerHTML='<div class="ov-h"><h3>Rewards</h3><button type="button" class="ov-link" onclick="ecoShopOpen()">Shop ›</button></div><div class="ovh-chips"><div class="ovh-chip"><span>Sparks</span><b>✦ '+ecoSparks().toLocaleString()+'</b></div><div class="ovh-chip"><span>Streak</span><b>'+streak()+'d'+(sh?' · '+'🛡'.repeat(sh):'')+'</b></div>'+(state.restedXP>0?'<div class="ovh-chip"><span>Rested</span><b>+50% · '+Math.round(state.restedXP)+'</b></div>':'')+'</div>';
 el.onclick=e=>{if(!e.target.closest('button'))ecoShopOpen()};el.style.cursor='pointer';
}
(function ecoRewardsBoot(){
 const i=OV_TILES.findIndex(t=>t.id==='hero')+1;
 OV_TILES.splice(i,0,{id:'rewards',name:'Rewards',sub:'Sparks, shields and the shop',icon:'star',col:'#fbbf24'});
 OV_DEFAULT.splice(i,0,'rewards');
 const ro=renderOverview;renderOverview=function(){const r=ro.apply(this,arguments);try{ovRewardsRender()}catch(e){console.warn('[rewards tile]',e)}return r};
 /* cat care, nutrition, toys */
 const pf=petFeed;petFeed=function(){const r=pf.apply(this,arguments);try{if(r&&r.ok)ecoCare('feed')}catch(e){}return r};
 const pp=petPlayDone;petPlayDone=function(){const r=pp.apply(this,arguments);try{if(r&&r.ok)ecoCare('play')}catch(e){}return r};
 const pt=petPetDone;petPetDone=function(){const r=pt.apply(this,arguments);try{if(r&&r.ok)ecoCare('pet')}catch(e){}return r};
 const am=addMealEntry;addMealEntry=function(){const r=am.apply(this,arguments);try{setTimeout(ecoNutritionCheck,50)}catch(e){}return r};
 const tu=petToyUnlocked;petToyUnlocked=function(p,id){return !!(state.toysBought&&state.toysBought[id])||tu(p,id)};
 setTimeout(()=>{try{ecoApplyTheme();ovRewardsRender()}catch(e){}},800);
})();
