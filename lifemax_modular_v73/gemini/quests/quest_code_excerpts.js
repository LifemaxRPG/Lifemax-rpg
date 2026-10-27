/* Excerpts of the real LifeMax RPG code that touches quests, so you can see exactly how they work today.
 * The whole app is in the zip; this is only the part that matters for the quest redesign. Function names are real; file names (segments) are noted. */

/* --- freshState  (04-runtime.js) --- */
function freshState(){return {name:'The Life Architect',build:'The Life Architect',classId:null,activities:[],attrXP:Object.fromEntries(ATTRS.map(a=>[a.id,0])),skillXP:{},quests:JSON.parse(JSON.stringify(defaultQuests)),questDone:{},benchmarks:[],mediaItems:[],customRules:{},tutorialDone:false,tipsSeen:{},created:new Date().toISOString()}}

/* --- getToday  (04-runtime.js) --- */
function getToday(){return new Date().toLocaleDateString('en-CA')}

/* --- questKey  (04-runtime.js) --- */
function questKey(q){let d=new Date();if(q.cadence==='daily')return q.id+'_'+getToday();if(q.cadence==='weekly'){const first=new Date(d);first.setDate(d.getDate()-((d.getDay()+6)%7));return q.id+'_'+first.toLocaleDateString('en-CA')}return q.id+'_once'}

/* --- isQuestDone  (04-runtime.js) --- */
function isQuestDone(q){return !!state.questDone[questKey(q)]}

/* --- questToday  (13-runtime.js) --- */
function questToday(q){return !q.day||q.day==='any'||q.day===['sun','mon','tue','wed','thu','fri','sat'][new Date().getDay()]}

/* --- questLocked  (13-runtime.js) --- */
function questLocked(q){return (q.stage||1)>1&&characterLevel().level<stageLvl(q.stage)}

/* --- stageLvl  (13-runtime.js) --- */
function stageLvl(s){return [0,1,3,5,8,12][s]||1}

/* --- toggleQuest  (04-runtime.js) --- */
function toggleQuest(id,checked){const q=state.quests.find(q=>q.id===id);if(!q)return;const A=q.attribute||'discipline';if(checked&&questLocked(q)){toast('Locked until you reach level '+stageLvl(q.stage));renderQuests();return}const key=questKey(q);if(checked&&!state.questDone[key]){const qid='quest_'+Date.now();state.questDone[key]=qid;const xp=ecoQuestXP(q);if(!xp&&(Number(q.xp)||0)>0)toast(q.cadence==='epic'?'One epic quest reward per day':'Quest XP limit reached for '+(q.cadence==='weekly'?'this week':'today'));const noteEl=document.getElementById('qnote-'+id);const qnote=(noteEl&&noteEl.value.trim())||'';state.attrXP[A]=(state.attrXP[A]||0)+xp;state.skillXP['Quest Completion']=(state.skillXP['Quest Completion']||0)+xp;state.activities.push({id:qid,cad:q.cadence,epic:q.cadence==='epic',skill:'Quest Completion',type:'quest',name:'Quest: '+q.name,icon:'flag',duration:0,xp,primary:A,date:new Date().toISOString(),notes:qnote||'Quest completed',xpSplit:[{attr:A,xp}]});if(xp>0)toast(`Quest complete · +${xp} XP`)}else if(!checked&&state.questDone[key]){const aid=state.questDone[key],xp=Math.max(0,Number(q.xp)||0);delete state.questDone[key];state.attrXP[A]=Math.max(0,(state.attrXP[A]||0)-xp);state.skillXP['Quest Completion']=Math.max(0,(state.skillXP['Quest Completion']||0)-xp);state.activities=state.activities.filter(a=>a.id!==aid);toast('Quest undone · XP removed')}save()}

/* --- createQuest  (04-runtime.js) --- */
function createQuest(){const name=document.getElementById('newQuestName').value.trim();if(!name){toast('Give your quest a name');return}state.quests.push({id:'custom_'+Date.now(),name,desc:'Custom quest created by you.',cadence:document.getElementById('newQuestCadence').value,xp:Math.min(500,Math.max(1,Number(document.getElementById('newQuestXP').value)||25)),kind:'custom'});document.getElementById('newQuestName').value='';hideCustomQuest();save();toast('Quest created')}

/* --- deleteActivity  (04-runtime.js) --- */
function deleteActivity(id){const a=state.activities.find(a=>a.id===id);if(!a)return;if(!confirm('Undo this activity and remove its XP from your stats?'))return;state.activities=state.activities.filter(a=>a.id!==id);M().tx=M().tx.filter(t=>t.actId!==id);if(a.planDate&&state.plan&&state.plan.checks[a.planDate])delete state.plan.checks[a.planDate].actId;Object.keys(state.questDone).forEach(k=>{if(state.questDone[k]===id)delete state.questDone[k]});(a.xpSplit||[{attr:a.primary,xp:a.xp}]).forEach(z=>state.attrXP[z.attr]=Math.max(0,(state.attrXP[z.attr]||0)-z.xp));state.skillXP[a.skill]=Math.max(0,(state.skillXP[a.skill]||0)-a.xp);if(state.skillXP[a.skill]===0)delete state.skillXP[a.skill];save();toast('Activity undone')}

/* --- ecoQuestXP  (43-economy.js) --- */
function ecoQuestXP(q){
 let base=Math.max(0,Number(q.xp)||0);const cad=q.cadence,day=getToday();
 const qs=state.activities.filter(a=>a.type==='quest');
 if(cad==='epic'){return qs.filter(a=>a.epic&&activityDate(a)===day).length>=ECO.epicPerDay?0:ecoRestedTake(base)}
 if(cad==='daily'&&ecoPerk('compound'))base=Math.round(base*1.15);
 const lim=cad==='weekly'?ECO.weeklyQuests:ECO.dailyQuests;
 const since=cad==='weekly'?(()=>{const d=new Date();d.setDate(d.getDate()-6);return d.toLocaleDateString('en-CA')})():day;
 const n=qs.filter(a=>a.cad===cad&&activityDate(a)>=since).length;
 if(n>=lim)return 0;
 return ecoCap(ecoRestedTake(base),day);
}

/* --- ecoCap  (43-economy.js) --- */
function ecoCap(x,day){
 if(x<=0)return 0;
 const t=ecoDayXP(day);if(t>=ECO.hardCap)return 0;
 let out=x;
 if(t+x>ECO.softCap){const over=t+x-Math.max(ECO.softCap,t);out=x-over+Math.round(over*.5)}
 return Math.max(0,Math.min(out,ECO.hardCap-t));
}

/* --- ecoDayXP  (43-economy.js) --- */
function ecoDayXP(day){return ecoActsOn(day).reduce((n,a)=>n+(a.epic?0:(Number(a.xp)||0)),0)}

/* --- ecoRestedTake  (43-economy.js) --- */
function ecoRestedTake(x){
 const pool=Number(state.restedXP)||0;if(pool<=0||x<=0)return x;
 const b=Math.min(pool,Math.round(x*ECO.restedBonus));state.restedXP=pool-b;return x+b;
}

/* --- ecoRestDay  (43-economy.js) --- */
function ecoRestDay(key){
 if((state.restDays||{})[key])return true;
 try{const pd=planDay(key);if(pd&&pd.kind==='rest')return true}catch(e){}
 return ecoActsOn(key).some(a=>a.type==='recovery'&&!ecoPassive(a)&&(Number(a.duration)||0)>=20);
}

/* --- streak  (05-runtime.js) --- */
function streak(){const set=new Set(state.activities.filter(a=>!ecoPassive(a)).map(activityDate));Object.keys(state.restDays||{}).forEach(k=>set.add(k));Object.keys(state.shieldDays||{}).forEach(k=>set.add(k));const d=new Date();let n=0,miss=0;for(let i=0;i<400;i++){if(set.has(d.toLocaleDateString('en-CA'))){n++;miss=0}else if(++miss>=2)break;d.setDate(d.getDate()-1)}return n}

/* --- characterLevel  (04-runtime.js) --- */
function characterLevel(){return levelData(totalXP()+(state.legacyCharXPBonus||0)+Object.values(state.ascended||{}).reduce((a,b)=>a+b,0),charXpForLevel)}

/* --- getDecay  (05-runtime.js) --- */
function getDecay(){return {on:true,grace:4,rate:3,...(state.decay||{})}}

/* --- chDecay  (14-runtime.js) --- */
function chDecay(a){var d=getDecay(),today=dayNum(getToday()),lt=lastTrained(a.id),xp=attrXP(a.id);
 if(!lt)return {k:'none',txt:'Not trained yet',sub:'never',col:'var(--muted)',rank:3};
 var idle=today-dayNum(lt),sub=idle===0?'today':idle+' day'+(idle>1?'s':'')+' ago';
 if(!d.on)return {k:'off',txt:'Decay is off',sub:sub,col:'var(--muted)',rank:4};
 if(idle<=d.grace){var left=d.grace-idle+1;return {k:left<=2?'soon':'ok',txt:'Decay starts in '+left+' day'+(left>1?'s':''),sub:sub,col:left<=2?'var(--gold)':'var(--good)',left:left,rank:left<=2?1:3.5}}
 if(xp<=0)return {k:'zero',txt:'Nothing left to lose',sub:sub,col:'var(--muted)',rank:3.8};
 return {k:'decay',txt:'Decaying about '+Math.max(1,Math.round(xp*d.rate/100))+' XP a day',sub:sub,col:'var(--bad)',rank:0}}

/* --- ecoSparksSync  (44-rewards.js) --- */
function ecoSparksSync(){
 let add=0;state.activities.forEach(a=>{if(!a.sp){a.sp=1;add+=Math.max(0,Number(a.xp)||0)}});
 if(add)state.sparks=(Number(state.sparks)||0)+add;
}

/* --- ecoPerk  (44-rewards.js) --- */
function ecoPerk(id){return !!state&&state.perk===id&&(state.perksOwned||[]).indexOf(id)>=0}

/* --- renderQuests  (14-runtime.js) --- */
function renderQuests(){
 const daily=state.quests.filter(q=>q.cadence==='daily'&&q.kind!=='nutrition'&&questToday(q));
 const weekly=state.quests.filter(q=>q.cadence==='weekly'&&q.kind!=='nutrition');
 const nut=state.quests.filter(q=>q.kind==='nutrition'&&(q.cadence!=='daily'||questToday(q)));
 const empty=label=>`<div class="qos-empty"><span class="qos-empty-icon">${px('flag')}</span><span>${label}</span></div>`;
 document.getElementById('dailyQuests').innerHTML=daily.length?daily.map(questRow).join(''):empty('No daily missions yet. Forge one of your own.');
 document.getElementById('weeklyQuests').innerHTML=weekly.length?weekly.map(questRow).join(''):empty('No weekly missions yet. Build your next objective.');
 const nc=document.getElementById('nutritionCard'),ne=document.getElementById('nutritionQuests');
 if(nc)nc.style.display=nut.length?'':'none';if(ne)ne.innerHTML=nut.map(questRow).join('');
 const epics=state.quests.filter(q=>q.cadence==='epic').sort((a,b)=>(a.stage||0)-(b.stage||0));
 document.getElementById('achievements').innerHTML=epics.map(q=>{
  const locked=questLocked(q),done=isQuestDone(q),xp=Math.max(0,Number(q.xp)||0);
  const sub=locked?`Unlock at level ${stageLvl(q.stage)}`:(q.desc||'One-time milestone');
  return `<div class="qos-epic ${locked?'is-locked':''}"><span class="qos-epic-icon">${px(locked?'lock':done?'trophy':'flag')}</span><span class="qos-epic-label"><strong>${esc(q.name)}</strong><small>${esc(sub)}</small></span><span class="qos-epic-xp">+${xp} XP</span>${locked?'':`<input type="checkbox" ${done?'checked':''} aria-label="${done?'Undo':'Complete'} ${esc(q.name)}" onchange="qosToggle('${q.id}',this.checked)">`}</div>`;
 }).join('')||empty('Your first epic milestone awaits.');
 qosUpdateSummary(daily,weekly,nut);
}

/* --- qosUpdateSummary  (14-runtime.js) --- */
function qosUpdateSummary(daily,weekly,nut){
 const dDone=daily.filter(isQuestDone).length;
 const all=[...daily,...weekly,...nut];
 const avail=all.filter(q=>!isQuestDone(q)).reduce((sum,q)=>sum+Math.max(0,Number(q.xp)||0),0);
 const pct=daily.length?Math.round(dDone/daily.length*100):0;
 const txt=(id,v)=>{const el=document.getElementById(id);if(el)el.textContent=v};
 txt('qosDailyProgress',`${dDone} / ${daily.length}`);
 txt('qosDailyPct',`${pct}%`);
 txt('qosDailyTotal',daily.length);
 txt('qosWeeklyTotal',weekly.length);
 txt('qosAvailableXP',avail.toLocaleString());
 txt('qosDailyCount',`${daily.length} quests`);
 txt('qosWeeklyCount',`${weekly.length} quests`);
 txt('qosNutritionCount',`${nut.length} quests`);
 txt('qosMotivation',pct>=100&&daily.length?'Today’s missions cleared. Well played.':dDone?'Keep that momentum going.':'Your next quest is waiting.');
 const fill=document.getElementById('qosDailyFill');if(fill)fill.style.width=pct+'%';
}

/* --- questRow  (13-runtime.js) --- */
function questRow(q){
 const done=isQuestDone(q),reward=Math.max(0,Number(q.xp)||0);
 return `<div class="qos-quest ${done?'is-done':''}">
 <label class="qos-checkbox" aria-label="${done?'Undo':'Complete'} ${esc(q.name)}"><input type="checkbox" ${done?'checked':''} onchange="qosToggle('${q.id}',this.checked)"><span class="qos-checkbox-face"></span></label>
 <div class="qos-quest-copy"><strong>${esc(q.name)}</strong>${q.desc?`<small>${esc(q.desc)}</small>`:''}${done?'':`<input class="qos-qnote qnote" id="qnote-${q.id}" placeholder="Add a private note (optional)" maxlength="300" aria-label="Note for ${esc(q.name)}">`}</div>
 <div class="qos-reward">+${reward} XP</div></div>`;
}

/* --- luckToday  (18-runtime.js) --- */
function luckToday(){var t=getToday();return state.luck&&state.luck.date===t&&state.luck.ver===LUCK_VER&&state.luck.dice&&state.luck.dice.length===4?state.luck:null}

/* --- luckPct  (18-runtime.js) --- */
function luckPct(s){return Math.max(0,Math.min(100,(s-1000)/8999*100))}

/* (not found: luckResult) */

/* --- ECO limits (43-economy.js) --- */
const ECO={charBase:200,charSlope:75,softCap:180,hardCap:240,minMin:5,maxPerType:3,maxManualMin:360,restedCap:300,restedRate:.5,restedBonus:.5,
 dailyQuests:3,weeklyQuests:3,epicPerDay:1,depositMin:50,depositCapDay:25,depositCapWeek:50,restPerWeek:2};

/* --- ECO_PERKS (44-rewards.js) --- */
const ECO_PERKS={scholar:['Scholar’s Flow','+10% XP on reading and language sessions of 45 min or more'],iron:['Iron Grace','+2 days of grace before Physique and Endurance start to decay'],exec:['Executive Edge','Life Director’s execution bonus rises from +10% to +20%'],compound:['Compound Habit','+15% XP on every daily quest']};

/* --- ECO_PRICE (44-rewards.js) --- */
const ECO_PRICE={theme:300,perk:600,rest:250,shield:250};

/* --- the quests a brand-new player starts with (00-runtime.js) --- */
const DEFAULT_QUESTS=[{id:'q1',name:'Learn for 20 minutes',desc:'Focused study or reading. Show up or the attribute stalls.',cadence:'daily',xp:15,kind:'learning'},
{id:'q2',name:'Move with intention',desc:'Train or move with intent. Recovery only counts if you actually recover.',cadence:'daily',xp:15,kind:'movement'},
{id:'q3',name:'Advance a priority',desc:'Take one concrete step on an important project.',cadence:'daily',xp:20,kind:'business'},
{id:'q4',name:'Three meaningful sessions',desc:'Complete three workouts or planned movement sessions this week.',cadence:'weekly',xp:35,kind:'fitness'},
{id:'q5',name:'Weekly reset',desc:'Review your progress and choose next week’s priorities.',cadence:'weekly',xp:25,kind:'review'},
{id:'q6',name:'First proof of progress',desc:'Complete a personal milestone and record the result.',cadence:'epic',xp:75,kind:'milestone'}
];
