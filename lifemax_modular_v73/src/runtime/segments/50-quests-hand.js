/* ===== The Daily Hand: three drawn quest cards a day (Maintenance, Class, Wildcard) =====
   Cards are materialised as ordinary daily quests (id dh_<date>_<slot>_<n>, hand:true) so XP, the 3-a-day cap,
   undo, voice and badges all keep working. Custom quests, weeklies and epics are untouched. */
/* var, not const: the Quest Board can render during boot, before this segment has run */
var DH_FREE_REROLLS=1,DH_REROLL_COST=50,DH_HOLD_MS=900;
var DH_CLASS_ATTR={athlete:['physique','endurance','wellbeing'],scholar:['intelligence','discipline','wellbeing'],entrepreneur:['business','social','discipline'],explorer:['adventure','endurance','wellbeing']};
/* [attribute, tier, name, description, verify]  verify: self | act:type:minutes | any:minutes | water:pct | pet:feed|pet|play | plan | none */
var QUEST_LIBRARY=[
['physique',1,'Move your body','Any strength or mobility work for 10 minutes.','act:strength:10'],
['physique',2,'Proper strength session','25 minutes of resistance training.','act:strength:25'],
['physique',3,'Heavy day','45 minutes of strength work, full effort and clean form.','act:strength:45'],
['physique',1,'Posture reset','Stand tall, roll the shoulders, 20 slow squats.','self'],
['physique',2,'Bodyweight circuit','Three rounds: push-ups, squats, plank.','self'],
['physique',1,'Stretch it out','Five minutes of stretching, no phone.','self'],
['physique',2,'Follow today\'s plan','Finish today\'s planned workout.','plan'],
['endurance',1,'Brisk walk','Walk 15 minutes at a pace that lifts the heart rate.','act:running:15'],
['endurance',2,'Steady cardio','25 minutes of running, cycling or similar.','act:running:25'],
['endurance',3,'Long effort','45 minutes of sustained cardio.','act:running:45'],
['endurance',1,'Take the stairs','Use stairs every chance today.','self'],
['endurance',2,'Interval burst','Six rounds of 30 seconds hard, 60 easy.','self'],
['endurance',1,'Breathe deep','Five minutes of slow nasal breathing.','self'],
['intelligence',1,'Read a little','Read for 10 minutes.','act:reading:10'],
['intelligence',2,'Focused reading','Read for 25 minutes without interruption.','act:reading:25'],
['intelligence',3,'Deep study','45 minutes of hard study or reading.','act:reading:45'],
['intelligence',1,'Learn one thing','Look up something you wondered about and write one line on it.','self'],
['intelligence',2,'Language practice','15 minutes with a language.','act:language:15'],
['intelligence',2,'Teach it back','Explain something you learned to a person or to the page.','self'],
['intelligence',1,'Brain puzzle','One puzzle, riddle or chess problem.','self'],
['business',1,'One small step','Do one concrete five-minute step on a project.','act:business:5'],
['business',2,'Focused work block','25 minutes of deep work on something that matters.','act:business:25'],
['business',3,'Ship something','45 minutes of work that ends with something finished.','act:business:45'],
['business',1,'Clear the inbox','Answer the one message you have been avoiding.','self'],
['business',2,'Money check','Review spending and write down one decision.','self'],
['business',2,'Plan the week\'s needle-mover','Pick the single task that matters most this week.','self'],
['social',1,'Send a kind message','Message someone you appreciate.','self'],
['social',2,'Real conversation','Call or meet someone for a proper catch-up.','self'],
['social',1,'Give a compliment','Mean it and be specific.','self'],
['social',2,'Reach out','Contact someone you have not spoken to in a month.','self'],
['social',3,'Host or join','Spend time in person with friends or family.','self'],
['social',1,'Listen first','In your next conversation, ask two questions before talking about yourself.','self'],
['discipline',1,'Make the bed, clear the desk','Reset your space for five minutes.','self'],
['discipline',1,'Do the thing you dread','Start the task you keep putting off, just for five minutes.','self'],
['discipline',2,'Finish a task','Complete any task from your list.','act:task:5'],
['discipline',2,'No-scroll hour','One hour with no social media or short video.','self'],
['discipline',3,'Hard thing first','Do the hardest task of the day before anything else, and finish it.','self'],
['discipline',1,'Plan tomorrow','Write down tomorrow\'s top three.','self'],
['discipline',2,'Hit the water goal','Reach your daily water goal.','water:100'],
['adventure',1,'Go outside','Spend 15 minutes outdoors, no headphones.','self'],
['adventure',2,'Try something new','A new food, route, hobby or place.','self'],
['adventure',2,'Take the long way','Explore a street or trail you have never walked.','self'],
['adventure',3,'Mini expedition','Spend an hour exploring somewhere unfamiliar.','self'],
['adventure',1,'Photograph something interesting','Look for one thing worth keeping.','self'],
['adventure',2,'Play with the cat','Play with your cat today.','pet:play'],
['wellbeing',1,'Drink some water','Drink half of your daily water goal.','water:50'],
['wellbeing',1,'Feed the cat','Look after your companion.','pet:feed'],
['wellbeing',1,'Pet the cat','A calm minute with your companion.','pet:pet'],
['wellbeing',2,'Recovery block','20 minutes of real recovery: stretching, a walk, or quiet.','act:recovery:20'],
['wellbeing',2,'Early wind-down','Screens off 30 minutes before bed.','self'],
['wellbeing',1,'Three good things','Write down three things that went well.','self'],
['wellbeing',2,'Eat something green','Add vegetables to a meal.','self'],
['wellbeing',3,'Full care day','Hit your water goal, move, and rest properly.','water:100']
].map(function(r,i){return {id:'t'+(i+1),attr:r[0],tier:r[1],name:r[2],desc:r[3],verify:r[4],xp:[0,10,20,30][r[1]]}});
function dhSeed(str){let h=2166136261;for(let i=0;i<str.length;i++){h^=str.charCodeAt(i);h=Math.imul(h,16777619)}return h>>>0}
function dhRng(seed){let s=seed>>>0;return function(){s=(s+0x6D2B79F5)>>>0;let t=s;t=Math.imul(t^(t>>>15),t|1);t^=t+Math.imul(t^(t>>>7),t|61);return((t^(t>>>14))>>>0)/4294967296}}
function dhLuckScore(){const L=luckToday();return L&&L.score?L.score:5500}
function dhRecentAttrXP(days){
 const since=new Date();since.setDate(since.getDate()-(days-1));const s=since.toLocaleDateString('en-CA'),out={};
 ATTRS.forEach(function(a){out[a.id]=0});
 state.activities.forEach(function(a){if(a.type==='quest'||activityDate(a)<s)return;(a.xpSplit||[{attr:a.primary,xp:a.xp}]).forEach(function(x){if(x&&x.attr in out)out[x.attr]+=Math.max(0,Number(x.xp)||0)})});
 return out;
}
function dhWeakestAttr(){const r=dhRecentAttrXP(7),ids=ATTRS.map(function(a){return a.id}).sort(function(a,b){return r[a]-r[b]||ids0(a)-ids0(b)});function ids0(x){return ATTRS.findIndex(function(a){return a.id===x})}return ids[0]}
/* pick one template: pool is filtered by attribute set, rest-day rule, tier odds from luck, and what is already drawn */
function dhPick(rng,attrs,taken,date,slot){
 const rest=ecoRestDay(date);
 let pool=QUEST_LIBRARY.filter(function(t){return(!attrs||attrs.indexOf(t.attr)>=0)&&taken.indexOf(t.id)<0&&!(rest&&t.tier===3)});
 if(!pool.length)pool=QUEST_LIBRARY.filter(function(t){return taken.indexOf(t.id)<0&&!(rest&&t.tier===3)});
 const luck=(dhLuckScore()-1000)/8999;/* 0..1 */
 const w=function(t){const base=t.tier===1?1.2-luck*0.4:t.tier===2?1:0.5+luck*1.0;return slot==='maintenance'&&t.tier===3?base*0.3:base};
 const tot=pool.reduce(function(a,t){return a+w(t)},0);let r=rng()*tot;
 for(let i=0;i<pool.length;i++){r-=w(pool[i]);if(r<=0)return pool[i]}return pool[pool.length-1];
}
function dhClassAttrs(){return DH_CLASS_ATTR[state.classId]||null}
function dhDraw(date,nonce,slotIdx,taken){
 const slots=['maintenance','class','wildcard'],slot=slots[slotIdx];
 var rng=dhRng(dhSeed(date+'|'+dhLuckScore()+'|'+nonce+'|'+slotIdx));
 const attrs=slot==='maintenance'?[dhWeakestAttr()]:slot==='class'?dhClassAttrs():null;
 return dhPick(rng,attrs,taken,date,slot);
}
function dhMakeQuest(t,date,slotIdx,nonce){
 return {id:'dh_'+date+'_'+slotIdx+'_'+nonce,hand:true,slot:slotIdx,tpl:t.id,name:t.name,desc:t.desc,cadence:'daily',xp:t.xp,kind:'hand',attribute:t.attr,verify:t.verify,tier:t.tier};
}
/* make sure today's three cards exist; drop earlier days' cards (their history stays in activities and questDone) */
function dhEnsure(){
 if(!window.QUEST_LIBRARY&&typeof QUEST_LIBRARY==='undefined')return null;
 const date=getToday();
 state.quests=state.quests||[];
 if(!state.dhMig){/* one-time: the old fixed class dailies give way to the Hand; custom quests stay */
  state.quests=state.quests.filter(function(q){return !(/^(q[1-3]|cc_.*)$/.test(q.id)&&q.cadence==='daily'&&q.kind!=='nutrition'&&q.kind!=='hydration')});
  state.dhMig=1;
 }
 state.quests=state.quests.filter(function(q){return !q.hand||q.id.indexOf('dh_'+date+'_')===0});
 let d=state.dailyDraw;
 if(!d||d.date!==date||!d.cards||d.cards.length!==3){
  const taken=[],cards=[];
  for(let i=0;i<3;i++){const t=dhDraw(date,0,i,taken);taken.push(t.id);cards.push({tpl:t.id,id:'dh_'+date+'_'+i+'_0',n:0})}
  d=state.dailyDraw={date:date,nonce:0,rerollsUsed:0,cards:cards};
 }
 d.cards.forEach(function(c,i){
  if(state.quests.some(function(q){return q.id===c.id}))return;
  const t=QUEST_LIBRARY.find(function(x){return x.id===c.tpl});if(t)state.quests.push(dhMakeQuest(t,d.date,i,c.n));
 });
 return d;
}
function dhCards(){return state.quests.filter(function(q){return q.hand})}
function dhRerollCost(){const d=state.dailyDraw;return d&&d.rerollsUsed<DH_FREE_REROLLS?0:DH_REROLL_COST}
function dhReroll(slotIdx){
 const d=dhEnsure(),c=d.cards[slotIdx];if(!c)return false;
 const q=state.quests.find(function(x){return x.id===c.id});
 if(q&&isQuestDone(q)){toast('Finished cards cannot be rerolled');return false}
 const cost=dhRerollCost();
 if(cost>0&&!ecoSpend(cost))return false;
 d.rerollsUsed++;d.nonce++;
 const taken=d.cards.map(function(x){return x.tpl});
 const t=dhDraw(d.date,d.nonce,slotIdx,taken);
 state.quests=state.quests.filter(function(x){return x.id!==c.id});
 d.cards[slotIdx]={tpl:t.id,id:'dh_'+d.date+'_'+slotIdx+'_'+d.nonce,n:d.nonce};d.up=d.up||[false,false,false];d.up[slotIdx]=false;
 state.quests.push(dhMakeQuest(t,d.date,slotIdx,d.nonce));
 toast(cost?('Rerolled for '+cost+' Sparks'):'Free reroll used');
 save();return true;
}
/* ---- auto-verification: look at what was really logged today ---- */
function dhVerifyMet(q,date){
 const v=String(q.verify||'self').split(':');if(v[0]==='self'||v[0]==='none')return false;
 const acts=state.activities.filter(function(a){return a.type!=='quest'&&a.type!=='coach'&&activityDate(a)===date});
 if(v[0]==='act')return acts.some(function(a){return a.type===v[1]&&(Number(a.duration)||0)>=(Number(v[2])||0)});
 if(v[0]==='any')return acts.some(function(a){return(Number(a.duration)||0)>=(Number(v[1])||0)});
 if(v[0]==='water'){const goal=Math.max(1,Number(state.mealsMeta&&state.mealsMeta.waterGoalMl)||2000),tot=(state.waterLog||[]).filter(function(w){return w.date===date}).reduce(function(a,w){return a+(Number(w.ml)||0)},0);return tot>=goal*(Number(v[1])||100)/100}
 if(v[0]==='pet'){const p=petData();if(!p||!p.alive||p.day!==petDay())return false;return v[1]==='feed'?p.fedToday>0:v[1]==='pet'?p.petsToday>0:p.playsToday>0}
 if(v[0]==='plan'){try{return !!dayDone(date)}catch(e){return false}}
 return false;
}
var dhBusy=false;
function dhAutoVerify(){
 if(dhBusy)return 0;let n=0;dhBusy=true;
 try{
  dhEnsure();const date=getToday();
  dhCards().slice().forEach(function(q){if(!isQuestDone(q)&&dhVerifyMet(q,date)){toggleQuest(q.id,true);n++}});
 }catch(e){console.warn('[daily hand]',e)}finally{dhBusy=false}
 return n;
}
var _dhSave=save;
save=function(){const r=_dhSave.apply(this,arguments);if(!dhBusy&&state.quests)dhAutoVerify();return r};
/* ---- hold to claim (self-reported cards) ---- */
var dhHoldT=null,dhHoldId=null;
function dhHoldStart(id,btn){
 dhHoldEnd(true);dhHoldId=id;if(btn)btn.classList.add('holding');
 dhHoldT=setTimeout(function(){dhHoldT=null;const b=btn;if(b)b.classList.remove('holding');try{if(window.LifeMaxHaptics)LifeMaxHaptics.play('success')}catch(e){}qosToggle(id,true)},DH_HOLD_MS);
}
function dhHoldEnd(silent){if(dhHoldT){clearTimeout(dhHoldT);dhHoldT=null}document.querySelectorAll('.dh-claim.holding').forEach(function(b){b.classList.remove('holding')});dhHoldId=null}
var DH_SLOT_NAMES=['Maintenance','Class','Wildcard'],DH_NUMERALS=['I','II','III'],dhDealtDate=null;
/* ---- Fate Deck: face-down cards you turn over, in a swipeable row ---- */
function dhIsUp(q){const d=state.dailyDraw;return !!(isQuestDone(q)||(d&&d.up&&d.up[q.slot]))}
/* a revealed card can be turned over to its back by tapping a corner, and turned back by tapping the back */
function dhTurn(slot){
 const d=dhEnsure();if(!d||!d.up||!d.up[slot])return;d.turned=d.turned||[false,false,false];d.turned[slot]=!d.turned[slot];
 try{localStorage.setItem(STORE,JSON.stringify(state))}catch(e){}
 const el=document.querySelector('#dailyHand .dh-card[data-slot="'+slot+'"]');if(el)el.classList.toggle('is-turned',!!d.turned[slot]);dhSig='';
 try{if(window.LifeMaxHaptics)LifeMaxHaptics.play('tap')}catch(e){}
 try{if(el){const r=el.getBoundingClientRect();QFX.sparks(r.left+r.width/2,r.top+r.height*.4,10,1)}}catch(e){}
}
function dhReveal(slot){
 const d=dhEnsure();if(!d)return;d.up=d.up||[false,false,false];if(d.up[slot]){if(d.turned&&d.turned[slot])dhTurn(slot);return}d.up[slot]=true;
 try{localStorage.setItem(STORE,JSON.stringify(state))}catch(e){}
 const el=document.querySelector('#dailyHand .dh-card[data-slot="'+slot+'"]');if(el)el.classList.add('is-up');
 try{if(window.LifeMaxHaptics)LifeMaxHaptics.play('double')}catch(e){}
 try{if(el){const q=dhCards().find(function(x){return x.slot===slot});QFX.burst(el.getBoundingClientRect(),q?q.tier||1:1);setTimeout(QFX.foilKick,700)}}catch(e){}
}
function dhRingSvg(tier){
 let pips='';for(let i=0;i<3;i++){const a=(-90+(i-1)*22)*Math.PI/180,x=60+54*Math.cos(a),y=60+54*Math.sin(a);pips+='<circle cx="'+x.toFixed(1)+'" cy="'+y.toFixed(1)+'" r="3.2" class="'+(i<tier?'on':'off')+'"/>'}
 let rays='';for(let i=0;i<24;i++){const a=i*15*Math.PI/180;rays+='<line x1="'+(60+40*Math.cos(a)).toFixed(1)+'" y1="'+(60+40*Math.sin(a)).toFixed(1)+'" x2="'+(60+(i%2?44:48)*Math.cos(a)).toFixed(1)+'" y2="'+(60+(i%2?44:48)*Math.sin(a)).toFixed(1)+'"/>'}
 return '<svg class="fd-ring" viewBox="0 0 120 120" aria-hidden="true"><circle cx="60" cy="60" r="36" class="fd-r1"/><g class="fd-rays">'+rays+'</g>'+pips+'</svg>';
}
function dhCardHtml(q){
 const done=isQuestDone(q),up=dhIsUp(q),auto=!/^(self|none)/.test(q.verify||'self'),A=ATTRS.find(function(a){return a.id===q.attribute})||ATTRS[0];
 const arc=QPX_ARC[q.attribute]||QPX_ARC.discipline,cost=dhRerollCost(),tier=q.tier||1;
 const foot=done?'<span class="dh-state">Fulfilled</span>':auto?'<span class="dh-state dh-auto">Fulfils itself when you log it</span>':
  '<button class="dh-claim" type="button" aria-label="Hold to claim '+esc(q.name)+'" onpointerdown="dhHoldStart(\''+q.id+'\',this)" onpointerup="dhHoldEnd()" onpointerleave="dhHoldEnd()" onpointercancel="dhHoldEnd()" oncontextmenu="return false" onkeydown="if(event.key===\'Enter\'||event.key===\' \'){event.preventDefault();qosToggle(\''+q.id+'\',true)}"><svg viewBox="0 0 44 44" aria-hidden="true"><circle class="fd-track" cx="22" cy="22" r="19"/><circle class="fd-prog" cx="22" cy="22" r="19"/></svg><span class="dh-claim-t">Hold</span></button>';
 const rr=done?'':'<button class="dh-reroll" type="button" onclick="dhReroll('+q.slot+')" aria-label="Redraw '+esc(q.name)+'">Redraw · '+(cost?cost+' Sparks':'free')+'</button>';
 let pips='';for(let i=0;i<3;i++)pips+='<i class="'+(i<tier?'on':'')+'"></i>';
 const art=qartHas(q.attribute);
 return '<div class="dh-card fd-t'+tier+(art?' has-art':'')+(up?' is-up':'')+(done?' is-done':'')+((state.dailyDraw&&state.dailyDraw.turned&&state.dailyDraw.turned[q.slot])?' is-turned':'')+'" data-slot="'+q.slot+'" style="--i:'+q.slot+';--ac:'+arc.ac+';--acr:'+qpxRgb(arc.ac)+'">'
  +'<div class="dh-flip"><div class="fd-inner">'
  +'<div class="fd-face fd-back" onclick="dhReveal('+q.slot+')" role="button" tabindex="0" aria-label="Turn over card '+DH_NUMERALS[q.slot]+'" onkeydown="if(event.key===\'Enter\'||event.key===\' \'){dhReveal('+q.slot+')}">'+qcardBack(q.slot)+'</div>'
  +(art?'<div class="fd-face fd-front art">'+qartFace(q,{numeral:DH_NUMERALS[q.slot],done:done})+'</div>':'<div class="fd-face fd-front">'+qcardCorners()
  +'<div class="dh-top"><span class="fd-num">'+qpxText(DH_NUMERALS[q.slot],{s:2,w:60})+'</span><span class="dh-slot">'+qpxText(DH_SLOT_NAMES[q.slot],{s:1,w:150,lines:1})+'</span></div>'
  +qcardArt(q)
  +'<div class="qarc">'+qpxText(arc.name,{s:2,w:230,lines:1})+'<span class="qtier">'+qpxText(QPX_TIER[tier],{s:1,w:200,lines:1})+'</span></div>'
  +'<h4 class="dh-name">'+qpxText(q.name,{s:2,w:228,lines:3})+'<span class="sr">'+esc(q.name)+'</span></h4><small class="dh-desc">'+esc(q.desc||'')+'</small>'
  +'<div class="dh-meta"><span class="qpips" title="Difficulty">'+pips+'</span><span class="dh-attr">'+esc(A.short)+'</span><span class="dh-xp">'+qpxText('+'+Math.max(0,Number(q.xp)||0)+' XP',{s:2,w:100,lines:1})+'</span></div>'
  +(done?'<span class="qstamp">'+qpxText('FULFILLED',{s:3,w:240,lines:1})+'</span>':'')
  +'</div>')+'</div>'+['tl','tr','bl','br'].map(function(k){return '<button class="dh-corner '+k+'" type="button" aria-label="Flip the card over" onclick="dhTurn('+q.slot+')"></button>'}).join('')+'</div>'
  +'<div class="dh-dock">'+foot+'<span class="dock-mid"><span class="dock-slot">'+qpxText(DH_SLOT_NAMES[q.slot],{s:1,w:110,lines:1})+'</span><span class="dh-xp">'+qpxText('+'+Math.max(0,Number(q.xp)||0)+' XP',{s:2,w:100,lines:1})+'</span></span>'+rr+'</div></div>';
}
var dhDealing=false,dhPrevDone=null,dhSig='';
function dhViewOn(){const v=document.getElementById('view-quests');return !!(v&&v.classList.contains('active'))}
function dhRenderHand(){
 const el=document.getElementById('dailyHand');if(!el||dhDealing)return;const d=dhEnsure();
 const cards=dhCards().sort(function(a,b){return a.slot-b.slot}),keep=el.scrollLeft;
 const sig=cards.map(function(q){return q.id+(isQuestDone(q)?'1':'0')+(dhIsUp(q)?'u':'d')+((d&&d.turned&&d.turned[q.slot])?'t':'f')}).join('|')+'|'+dhRerollCost()+'|'+(d&&d.dealt?1:0);
 if(sig===dhSig&&el.firstChild){dhSchedDeal(el,d,cards);return}
 dhSig=sig;
 const prevDone=dhPrevDone||{};dhPrevDone={};
 el.innerHTML=cards.map(dhCardHtml).join('');el.scrollLeft=keep;
 cards.forEach(function(q){const dn=isQuestDone(q);dhPrevDone[q.id]=dn;if(dn&&prevDone[q.id]===false&&dhViewOn()){const c=el.querySelector('.dh-card[data-slot="'+q.slot+'"]');if(c){try{QFX.claim(c.getBoundingClientRect());if(window.LifeMaxHaptics)LifeMaxHaptics.play('success')}catch(e){}}}});
 try{QFX.foilKick()}catch(e){}
 dhSchedDeal(el,d,cards);
}
/* the reading starts only if you stay on the Quest Board a moment, so swiping or scrubbing past it does not trigger it */
var dhDealTimer=0;
function dhSchedDeal(el,d,cards){
 if(!d||d.dealt||!cards.length||!dhViewOn()||dhDealing)return;
 el.classList.add('fd-pre');
 if(dhDealTimer)return;
 dhDealTimer=setTimeout(function(){dhDealTimer=0;if(dhViewOn()&&!dhDealing)dhMaybeDeal();else if(!dhDealing){const h=document.getElementById('dailyHand');if(h)h.classList.remove('fd-pre')}},900);
}
/* ---- shuffle and lay-out: a riffle in a small stage, then each real card flies from the deck to its place (FLIP) ---- */
function dhStage(){
 let st=document.getElementById('dailyStage');
 if(!st){st=document.createElement('div');st.id='dailyStage';st.className='fd-stage';st.hidden=true;const h=document.getElementById('dailyHand');h.parentNode.insertBefore(st,h)}
 return st;
}
function dhWait(anims){return Promise.all(anims.map(function(a){return a.finished.catch(function(){})}))}
function dhTick(n){try{if(window.LifeMaxHaptics)LifeMaxHaptics.play(n||'tap')}catch(e){}}
async function dhMaybeDeal(replay){
 const el=document.getElementById('dailyHand'),d=state.dailyDraw;
 if(!el||!d||dhDealing||(d.dealt&&replay!==true)||!dhViewOn())return;
 const cards=[].slice.call(el.querySelectorAll('.dh-card'));if(!cards.length)return;
 if(fxReduced()||!cards[0].animate){d.dealt=true;el.classList.remove('fd-pre');try{localStorage.setItem(STORE,JSON.stringify(state))}catch(e){}return}
 dhDealing=true;
 try{
  if(replay===true){cards.forEach(function(c){c.classList.remove('is-up')});d.up=[false,false,false]}
  el.classList.add('fd-pre');el.scrollLeft=0;
  const mode=typeof dhDealMode==='function'?dhDealMode():'quick';
  if(mode==='table'&&typeof dhTable3D==='function'){const ok=await dhTable3D(el,cards,d,replay===true);if(ok!==false)return}
  if((mode==='cine'||mode==='table')&&typeof dhCinematic==='function'){await dhCinematic(el,cards,d,replay===true);return}
  if(mode==='off')return;
  const st=dhStage();st.hidden=false;st.innerHTML='';
  const N=10,backs=[];
  for(let i=0;i<N;i++){const b=document.createElement('div');b.className='fd-sback';if(typeof qartUrl==='function'&&qartUrl('back'))b.style.backgroundImage='url('+qartUrl('back')+')';b.style.zIndex=i;b.style.transform='translate('+(i*0.6)+'px,'+(-i*0.8)+'px)';st.appendChild(b);backs.push(b)}
  await dhWait([st.animate([{opacity:0,transform:'scale(.9)'},{opacity:1,transform:'none'}],{duration:220,fill:'both'})]);
  for(let round=0;round<3;round++){
   const split=backs.map(function(b,i){const left=i%2===0,x=(left?-1:1)*(54+round*6);return b.animate([{transform:b.style.transform},{transform:'translate('+x+'px,'+(-i*0.8)+'px) rotate('+(left?-7:7)+'deg)'}],{duration:240,easing:'ease-out',fill:'forwards'})});
   await dhWait(split);dhTick('tap');try{const r=st.getBoundingClientRect();QFX.sparks(r.left+r.width/2,r.top+r.height/2+10,10+round*6,1+round%2);QFX.motes(r.left+r.width/2,r.top+r.height/2,6,1)}catch(e){}
   const merge=backs.map(function(b,i){const left=i%2===0,x=(left?-1:1)*(54+round*6);return b.animate([{transform:'translate('+x+'px,'+(-i*0.8)+'px) rotate('+(left?-7:7)+'deg)'},{transform:'translate('+(i*0.6)+'px,'+(-i*0.8)+'px)'}],{duration:260,delay:i*18,easing:'cubic-bezier(.4,0,.2,1)',fill:'forwards'})});
   await dhWait(merge);try{const r=st.getBoundingClientRect();QFX.sparks(r.left+r.width/2,r.top+r.height/2,8,2)}catch(e){}
  }
  /* lay out: stage fades while the three real cards fly from the deck to their places */
  const sr=st.getBoundingClientRect(),cx=sr.left+sr.width/2,cy=sr.top+sr.height/2;
  el.classList.remove('fd-pre');
  const fly=cards.map(function(c,i){const r=c.getBoundingClientRect(),dx=cx-(r.left+r.width/2),dy=cy-(r.top+r.height/2),spin=(i-1)*8;
   c.style.opacity='1';
   return c.animate([{transform:'translate('+dx+'px,'+dy+'px) scale(.45) rotate('+spin+'deg)',opacity:0},{opacity:1,offset:.15},{transform:'translate('+(dx*0.15)+'px,'+(dy*0.15-30)+'px) scale(1.04) rotate('+(-spin*0.4)+'deg)',offset:.8},{transform:'none',opacity:1}],{duration:620,delay:i*220,easing:'cubic-bezier(.2,.75,.25,1)',fill:'backwards'})});
  st.animate([{opacity:1},{opacity:0}],{duration:260,fill:'forwards'});
  let flying=true;const trailLoop=function(){if(!flying)return;try{cards.forEach(function(c,i){const tier=+((c.className.match(/fd-t(\d)/)||[])[1])||1;QFX.trail(c.getBoundingClientRect(),tier);QFX.trail(c.getBoundingClientRect(),tier)})}catch(e){}requestAnimationFrame(trailLoop)};trailLoop();
  cards.forEach(function(c,i){setTimeout(function(){dhTick('tap')},i*220+520)});
  await dhWait(fly);flying=false;
  st.hidden=true;st.innerHTML='';
 }catch(e){console.warn('[deal]',e)}
 finally{
  dhDealing=false;el.classList.remove('fd-pre');cards.forEach(function(c){c.style.opacity=''});
  const st=document.getElementById('dailyStage');if(st){st.hidden=true}
  d.dealt=true;try{localStorage.setItem(STORE,JSON.stringify(state))}catch(e){}
 }
}
function dhReplayDeal(){if(!dhDealing)dhMaybeDeal(true)}
var _dhNav=navigate;
navigate=function(v){const r=_dhNav.apply(this,arguments);if(v==='quests'&&state.dailyDraw&&!state.dailyDraw.dealt)setTimeout(function(){try{dhRenderHand()}catch(e){}},120);return r};
window.LifeMaxDailyHand={deal:dhMaybeDeal,replay:dhReplayDeal,reveal:dhReveal,library:QUEST_LIBRARY,ensure:dhEnsure,reroll:dhReroll,cards:dhCards,verify:dhAutoVerify,verifyMet:dhVerifyMet,draw:dhDraw,weakest:dhWeakestAttr,cost:dhRerollCost};
/* boot already rendered the board before this segment existed; deal and draw once now */
try{dhEnsure();renderQuests()}catch(e){console.warn("[daily hand boot]",e)}
