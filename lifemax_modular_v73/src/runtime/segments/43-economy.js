/* ---------- v107 · Economy v2 (from the Gemini design review) ----------
   Character level has its own, steeper curve; attributes and skills keep theirs. Logging has a 5-minute minimum,
   a duration curve capped at 3x, at most 3 rewarded logs per type per day, a 6 h/day manual-time cap and a daily XP
   soft/hard cap. Passive health data (steps, sleep) is paid by tier, no longer feeds streaks or stops decay.
   Decay is gentler, never lowers a level, skips rest days and feeds a "rested" bonus for coming back. */
const ECO={charBase:200,charSlope:75,softCap:180,hardCap:240,minMin:5,maxPerType:3,maxManualMin:360,restedCap:300,restedRate:.5,restedBonus:.5,
 dailyQuests:3,weeklyQuests:3,epicPerDay:1,depositMin:50,depositCapDay:25,depositCapWeek:50,restPerWeek:2};
const ECO_OLD_RULES={reading:[20,'discipline'],business:[25,'discipline'],strength:[32,'discipline'],running:[27,'discipline'],language:[20,'social'],recovery:[16,'discipline'],practical:[23,'intelligence'],task:[15,''],custom:[20,'']};
const charXpForLevel=L=>ECO.charBase+ECO.charSlope*(L-1);
const charCumXP=L=>37.5*L*L+87.5*L-125;           /* XP needed to reach character level L */
/* steps and sleep from Health Connect (older records were stored as 'custom' / 'recovery' with these skills) */
const ecoPassive=a=>a&&(a.type==='steps'||a.type==='coach'||a.type==='sleep'||(a.src==='health'&&(a.skill==='Walking'||a.skill==='Sleep')));
const ecoDayKey=n=>new Date(n*864e5).toISOString().slice(0,10);
function ecoActsOn(day){return state.activities.filter(a=>activityDate(a)===day)}
/* the duration curve: 30 min = 1x, 60 min = 2x, 120 min+ = 3x; under 5 minutes earns nothing */
function ecoDur(dur){dur=Number(dur)||0;return dur<ECO.minMin?0:dur<=60?dur/30:Math.min(3,2+(dur-60)/60)}
/* the same curve in words, for the web form and the native Log */
const ECO_DURATION_RULES='30 min = base XP, up to 3× at 120 min. Under '+ECO.minMin+' min earns nothing.';
/* a day's XP counted toward the cap (epic quests are exempt) */
function ecoDayXP(day,acts=ecoActsOn(day)){return acts.reduce((n,a)=>n+(a.epic?0:(Number(a.xp)||0)),0)}
function ecoRestedPeek(x){const pool=Number(state.restedXP)||0;return pool>0&&x>0?x+Math.min(pool,Math.round(x*ECO.restedBonus)):x}
function ecoRestedTake(x){
 const pool=Number(state.restedXP)||0;if(pool<=0||x<=0)return x;
 const b=Math.min(pool,Math.round(x*ECO.restedBonus));state.restedXP=pool-b;return x+b;
}
/* soft cap halves what lands above 180, hard cap stops at 240; t is the day's XP so far */
function ecoCap(x,day,t=ecoDayXP(day)){
 if(x<=0||t>=ECO.hardCap)return 0;
 let out=x;
 if(t+x>ECO.softCap){const over=t+x-Math.max(ECO.softCap,t);out=x-over+Math.round(over*.5)}
 return Math.max(0,Math.min(out,ECO.hardCap-t));
}
/* everything that earns XP comes through here: returns {xp, msg}. msg says why xp is not simply total: the refusal when
   xp is 0, otherwise each rule that changed it, joined by ' · ' (empty when none did) */
function ecoAward(type,total,dur,o){
 o=o||{};const day=o.day||getToday();
 if(!(total>0))return {xp:0,msg:'Add at least '+ECO.minMin+' minutes to earn XP'};
 const acts=ecoActsOn(day),same=acts.filter(a=>a.type===type).length;
 const factor=same<ECO.maxPerType-1?1:same===ECO.maxPerType-1?.5:0;
 if(!factor)return {xp:0,msg:'Daily XP limit reached for this activity type'};
 if(o.manual){const mins=acts.filter(a=>a.type!=='quest'&&a.src!=='health').reduce((n,a)=>n+(Number(a.duration)||0),0);if(mins+(Number(dur)||0)>ECO.maxManualMin)return {xp:0,msg:'Daily active-time limit (6 hours) reached'}}
 const why=factor<1?['Last of this type today: half XP']:[];
 let x=Math.max(1,Math.round(total*factor));
 if((type==='reading'||type==='language')&&(Number(dur)||0)>=45&&ecoPerk('scholar')){const s=Math.round(x*1.1);if(s>x)why.push(ECO_PERKS.scholar[0]+' +'+(s-x));x=s}
 const r=o.peek?ecoRestedPeek(x):ecoRestedTake(x);if(r>x)why.push('Rested +'+(r-x));
 const t=ecoDayXP(day,acts);x=ecoCap(r,day,t);if(x>0&&x<r)why.push(t+x<ECO.hardCap?'XP above '+ECO.softCap+' a day is halved':'Reaches the daily XP cap');
 return x>0?{xp:x,msg:why.join(' · ')}:{xp:0,msg:'Daily XP cap reached'};
}
/* quests: daily 3 a day, weekly 3 a week, epic 1 a day; XP values stay as the quest says */
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
/* rest days: planned rest costs nothing (no decay, streak safe) */
function ecoRestDay(key){
 if((state.restDays||{})[key])return true;
 try{const pd=planDay(key);if(pd&&pd.kind==='rest')return true}catch(e){}
 return ecoActsOn(key).some(a=>a.type==='recovery'&&!ecoPassive(a)&&(Number(a.duration)||0)>=20);
}
function ecoRestUsed(){const since=new Date();since.setDate(since.getDate()-6);const s=since.toLocaleDateString('en-CA');return Object.keys(state.restDays||{}).filter(k=>k>=s).length}
function ecoTakeRest(){
 const d=getToday();state.restDays=state.restDays||{};
 if(state.restDays[d]){toast('Today is already a rest day');return}
 if(ecoRestUsed()>=ECO.restPerWeek){if((state.restTokens||0)>0){state.restTokens--;toast('Rest token used')}else{toast('Two rest days per week are used');return}}
 state.restDays[d]=true;save();toast('Rest day taken. No decay today.');renderDecay();
}
/* one-time move to the v2 economy; character level and every attribute level stay exactly where they are */
function ecoMigrate(){
 if(!state||state.xpSchemaV2)return;
 const raw=ATTRS.reduce((n,a)=>n+attrXP(a.id),0),old=levelData(raw);
 const target=charCumXP(old.level)+Math.round(old.into/old.needed*charXpForLevel(old.level));
 state.legacyCharXPBonus=Math.max(0,Math.round(target-raw));
 state.decayApplied=state.decayApplied||{};const t=dayNum(getToday());ATTRS.forEach(a=>{state.decayApplied[a.id]=t});
 if(state.decay&&state.decay.grace===3&&state.decay.rate===2)delete state.decay;
 const nw=DEFAULT_RULES;
 Object.keys(ECO_OLD_RULES).forEach(id=>{const r=rules&&rules[id],o=ECO_OLD_RULES[id];
  if(r&&nw[id]&&Number(r.base)===o[0]&&(r.secondary||'')===o[1]){r.base=nw[id].base;r.primary=nw[id].primary;r.secondary=nw[id].secondary}});
 state.customRules=rules;
 try{M().peakBalance=moneyBalance()}catch(e){}
 state.sparks=Math.round(raw*.5);state.activities.forEach(a=>{a.sp=1});
 state.xpSchemaV2=true;
}
