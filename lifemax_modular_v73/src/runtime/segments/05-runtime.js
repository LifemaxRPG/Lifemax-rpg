function esc(s){return String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
['activityType','duration','quality','skillName','primaryAttr','secondaryAttr'].forEach(id=>document.addEventListener('input',e=>{if(e.target.id===id)updatePreview()}));

let prevSnap=null;
const TITLES=[[1,'Novice'],[3,'Apprentice'],[5,'Adept'],[8,'Veteran'],[12,'Master'],[18,'Grandmaster'],[25,'Legend'],[35,'Paragon'],[50,'Mythic'],[75,'Sovereign'],[100,'Ascended']];
function titleFor(l){let t=TITLES[0][1];TITLES.forEach(x=>{if(l>=x[0])t=x[1]});return t}
function snapshot(){return {c:characterLevel().level,a:ATTRS.map(a=>attrLevel(a.id).level),x:ATTRS.map(a=>attrXP(a.id)),t:totalXP()}}
function checkLevelUps(){const cur=snapshot(),p=prevSnap;prevSnap=cur;if(!p||!p.x)return;
 const gains=[],ups=[];ATTRS.forEach((a,i)=>{const d=cur.x[i]-p.x[i];if(d>0)gains.push({id:a.id,xp:d});if(cur.a[i]>p.a[i])ups.push({id:a.id,short:a.short,from:p.a[i],level:cur.a[i]})});
 const delta=gains.reduce((n,g)=>n+g.xp,0);if(delta>1500)return;
 if(delta>0)celebrateXP(delta,gains);
 if(cur.c>p.c||ups.length)setTimeout(()=>showLevelUp({charLevel:cur.c>p.c?cur.c:null,from:p.c,attrs:ups}),delta>0?950:0)}
function streak(){const set=new Set(state.activities.filter(a=>!ecoPassive(a)).map(activityDate));Object.keys(state.restDays||{}).forEach(k=>set.add(k));Object.keys(state.shieldDays||{}).forEach(k=>set.add(k));const d=new Date();let n=0,miss=0;for(let i=0;i<400;i++){if(set.has(d.toLocaleDateString('en-CA'))){n++;miss=0}else if(++miss>=2)break;d.setDate(d.getDate()-1)}return n}
const BADGES=[
{id:'first',icon:'bolt',name:'First Step',desc:'Log your first activity',t:()=>state.activities.length>=1},
{id:'ten',icon:'star',name:'Getting Going',desc:'Log 10 activities',t:()=>state.activities.length>=10},
{id:'fifty',icon:'target',name:'Habit Formed',desc:'Log 50 activities',t:()=>state.activities.length>=50},
{id:'s3',icon:'flame',name:'On a Roll',desc:'3-day streak',t:()=>streak()>=3},
{id:'s7',icon:'flame',name:'Week Warrior',desc:'7-day streak',t:()=>streak()>=7},
{id:'l5',icon:'mountain',name:'Rising',desc:'Reach character level 5',t:()=>characterLevel().level>=5},
{id:'l10',icon:'trophy',name:'Veteran',desc:'Reach character level 10',t:()=>characterLevel().level>=10},
{id:'round',icon:'star',name:'Well-Rounded',desc:'Every attribute at Lv 2+',t:()=>ATTRS.every(a=>attrLevel(a.id).level>=2)},
{id:'spec',icon:'trophy',name:'Specialist',desc:'Any attribute at Lv 5',t:()=>ATTRS.some(a=>attrLevel(a.id).level>=5)},
{id:'poly',icon:'book',name:'Polymath',desc:'Practice 3 different skills',t:()=>Object.values(state.skillXP).filter(v=>v>0).length>=3},
{id:'quest',icon:'flag',name:'Quest Taker',desc:'Complete 5 quests',t:()=>Object.keys(state.questDone).length>=5},
{id:'deep',icon:'clock',name:'Deep Work',desc:'One session of 90+ minutes',t:()=>state.activities.some(a=>a.duration>=90)},
{id:'saver',icon:'coin',name:'Saver',desc:'Deposit $100 in total',t:()=>M().tx.filter(t=>t.type==='deposit').reduce((s,t)=>s+t.amount,0)>=100}
];
function checkBadges(silent){state.badges=state.badges||{};BADGES.forEach(b=>{if(!state.badges[b.id]&&b.t()){state.badges[b.id]=new Date().toISOString();if(!silent)setTimeout(()=>toast(`Badge unlocked: ${b.name}`),600)}})}
function renderExtras(){renderFxSettings();document.documentElement.classList.toggle('reduce-motion',fxReduced());renderLuckCard();renderCharacterPage();if(window.__petReady)renderPetAll();if(window.__ovReady)renderOverview();document.querySelectorAll('[data-px]').forEach(e=>{if(!e.firstChild)e.innerHTML=px(e.dataset.px,e.dataset.s||16)});renderDecay();renderAvatar();renderTime();renderMoney();renderPlan();renderDevMode();
 const lv=characterLevel().level,b=state.badges||{};
 document.getElementById('heroTitle').textContent=`LV ${lv} · ${titleFor(lv).toUpperCase()} · ${(state.build||'').toUpperCase()}`;
 document.getElementById('streakCount').textContent=streak();
 document.getElementById('achievementCount').textContent=Object.keys(b).length;
 document.getElementById('badgeCount').textContent=`${Object.keys(b).length}/${BADGES.length}`;
 document.getElementById('badges').innerHTML=BADGES.map(x=>`<span class="badge ${b[x.id]?'':'locked'}" title="${x.desc}"><span>${px(b[x.id]?x.icon:'lock')}</span><span>${x.name}<small>${x.desc}</small></span></span>`).join('');
 const days=[];for(let i=6;i>=0;i--){const d=new Date();d.setDate(d.getDate()-i);const k=d.toLocaleDateString('en-CA');days.push({k,l:i===0?'Today':d.toLocaleDateString(undefined,{weekday:'short'}),xp:state.activities.filter(a=>activityDate(a)===k).reduce((s,a)=>s+a.xp,0)})}
 const mx=Math.max(20,...days.map(d=>d.xp));
 document.getElementById('weekBars').innerHTML=days.map((d,i)=>`<div class="bar ${i===6?'today':''}"><b>${d.xp||''}</b><i style="height:${d.xp/mx*80}%"></i><small>${d.l}</small></div>`).join('');
 
}

function getDecay(){return {on:true,grace:4,rate:3,...(state.decay||{})}}
function dayNum(k){const p=k.split('-').map(Number);return Math.round(Date.UTC(p[0],p[1]-1,p[2])/864e5)}
function lastTrained(id){let best=null;state.activities.forEach(a=>{if(ecoPassive(a))return;if(a.primary===id||a.secondary===id){const k=activityDate(a);if(!best||k>best)best=k}});return best}
function applyDecay(){try{ecoShieldTick()}catch(e){console.warn('[shields]',e)}const d=getDecay();if(!d.on)return false;state.decayApplied=state.decayApplied||{};state.decayLog=state.decayLog||[];const today=dayNum(getToday());let changed=false;
 ATTRS.forEach(a=>{const lt=lastTrained(a.id);if(!lt)return;const from=Math.max(dayNum(lt)+d.grace+((a.id==='physique'||a.id==='endurance')&&ecoPerk('iron')?2:0)+1,(state.decayApplied[a.id]||0)+1);let lost=0;
  for(let n=from;n<=today;n++){if(ecoRestDay(ecoDayKey(n)))continue;const xp=attrXP(a.id);if(xp<=0)break;const lv=levelData(xp);if(lv.into<=0)break;const l=Math.min(lv.into,Math.max(1,Math.min(15,Math.round(xpForLevel(lv.level)*d.rate/100))));state.attrXP[a.id]=xp-l;lost+=l}
  if(lost>0&&today-dayNum(lt)>=7)state.restedXP=Math.min(ECO.restedCap,(Number(state.restedXP)||0)+Math.round(lost*ECO.restedRate));
  if(today>=from)state.decayApplied[a.id]=today;
  if(lost>0){changed=true;state.decayTotal=state.decayTotal||{};state.decayTotal[a.id]=(state.decayTotal[a.id]||0)+lost;state.decayLog.unshift({date:getToday(),attr:a.id,xp:lost})}});
 state.decayLog=state.decayLog.slice(0,30);return changed}
function renderDecay(){const d=getDecay(),today=dayNum(getToday());
 const rows=ATTRS.map(a=>{const lt=lastTrained(a.id),xp=attrXP(a.id);let status,col='var(--muted)',sub;
  if(!lt){status='Not trained yet';sub='—'}else{const idle=today-dayNum(lt);sub=idle===0?'trained today':idle+' day'+(idle>1?'s':'')+' ago';
   if(!d.on){status='Decay off'}
   else if(idle<=d.grace){const left=d.grace-idle+1;status=`Decay starts in ${left} day${left>1?'s':''}`;col=left<=2?'var(--gold)':'var(--accent2)'}
   else if(xp<=0||levelData(xp).into<=0){status='Level is safe'}
   else{status=`Decaying ~${Math.min(levelData(xp).into,Math.max(1,Math.min(15,Math.round(xpForLevel(levelData(xp).level)*d.rate/100))))} XP/day`;col='var(--red)'}}
  return `<div class="minirow"><span>${px(a.icon)} <strong>${a.short}</strong> <small>LV ${attrLevel(a.id).level} · ${sub}</small></span><span style="color:${col};font-size:12px;font-weight:650">${status}</span></div>`}).join('');
 const log=(state.decayLog||[]).slice(0,4).map(e=>{const a=ATTRS.find(x=>x.id===e.attr);return `<div class="minirow"><small>${e.date} · ${a?a.short:e.attr}</small><span style="color:var(--red);font-size:12px">−${e.xp} XP</span></div>`}).join('');
 const rest=ecoRestDay(getToday()),left=ECO.restPerWeek-ecoRestUsed(),pool=Math.round(state.restedXP||0);document.getElementById('decayWatch').innerHTML=rows+'<div class="minirow" style="margin-top:10px"><span><strong>Rest day</strong> <small>'+(rest?'today is a rest day':left+' left this week')+(pool?' · '+pool+' rested XP ready (+50% on your next sessions)':'')+'</small></span>'+(rest||left<=0?'':'<button class="btn small secondary" onclick="ecoTakeRest()">Take today off</button>')+'</div>'+(log?'<div style="margin-top:12px;font-size:11px;color:var(--muted);letter-spacing:.6px">RECENT DECAY</div>'+log:'');
 const on=document.getElementById('decayOn');if(on&&document.activeElement!==document.getElementById('decayGrace')){on.checked=d.on;document.getElementById('decayGrace').value=d.grace;document.getElementById('decayRate').value=d.rate}}
function saveDecay(){const c=(v,lo,hi,df)=>Math.min(hi,Math.max(lo,Number(v)||df));state.decay={on:document.getElementById('decayOn').checked,grace:c(document.getElementById('decayGrace').value,1,14,4),rate:c(document.getElementById('decayRate').value,1,10,3)};save();toast('Decay settings saved')}
function ago(ms){const m=Math.floor(ms/6e4);if(m<1)return 'just now';if(m<60)return m+'m ago';const h=Math.floor(m/60);if(h<48)return h+'h '+(m%60)+'m ago';return Math.floor(h/24)+'d '+(h%24)+'h ago'}
function markVisit(){try{const now=Date.now(),last=state.lastVisit||0;if(!last||now-last>30*6e4)state.prevVisit=last||null;state.lastVisit=now;state.customRules=rules;localStorage.setItem(STORE,JSON.stringify(state))}catch(e){}}
function renderTime(){const v=document.getElementById('visitTag'),a=document.getElementById('actTag');if(!v||!a)return;const now=Date.now();
 v.innerHTML=px('smile')+' '+(state.prevVisit?'Last check-in '+ago(now-state.prevVisit):'First check-in');
 const acts=state.activities.filter(x=>x.type!=='quest');if(!acts.length){a.innerHTML=px('clock')+' No activity logged yet';return}
 const t=Math.max(...acts.map(x=>new Date(x.date).getTime()));a.innerHTML=px('clock')+' Last activity '+ago(now-t)}
setInterval(()=>{markVisit();renderTime()},30000);
document.addEventListener('visibilitychange',()=>{if(!document.hidden){markVisit();renderTime()}});
function buildReport(){const lv=characterLevel();
 const when=d=>new Date(d).toLocaleString([],{year:'numeric',month:'short',day:'numeric',hour:'numeric',minute:'2-digit'});
 const day=d=>new Date(d).toLocaleDateString([],{year:'numeric',month:'short',day:'numeric'});
 const b=state.badges||{};
 return {name:state.name||'The Life Architect',build:state.build||'',date:new Date().toLocaleDateString([],{year:'numeric',month:'long',day:'numeric'}),
  summary:[['Character level',lv.level+' ('+titleFor(lv.level)+')'],['Total XP',totalXP().toLocaleString()],['Current streak',streak()+' days'],['Activities logged',state.activities.filter(a=>a.type!=='quest').length],['Quests completed',Object.keys(state.questDone).length],['Badges earned',Object.keys(b).length+' of '+BADGES.length],['Savings balance',fmt(moneyBalance())]],
  sections:[
   ['Attributes',['Attribute','Level','Total XP'],ATTRS.map(a=>[a.name,attrLevel(a.id).level,attrXP(a.id).toLocaleString()])],
   ['Skills',['Skill','Level','XP'],Object.entries(state.skillXP||{}).filter(e=>e[1]>0).sort((x,y)=>y[1]-x[1]).map(e=>[e[0],levelData(e[1]).level,e[1].toLocaleString()])],
   ['Badges',['Badge','How earned','Unlocked'],BADGES.filter(x=>b[x.id]).map(x=>[x.name,x.desc,day(b[x.id])])],
   ['Benchmarks',['Benchmark','Result','Recorded'],(state.benchmarks||[]).map(x=>[x.name,x.value,day(x.date)])],
   ['Decay history',['Date','Attribute','XP lost'],(state.decayLog||[]).map(e=>[e.date,(ATTRS.find(a=>a.id===e.attr)||{}).short||e.attr,'-'+e.xp])],
   ['Savings transactions',['Date','Type','Amount','Note'],M().tx.slice().reverse().map(t=>[day(t.date),t.type==='deposit'?'Deposit':'Withdrawal',(t.type==='deposit'?'+':'-')+fmt(t.amount),t.note||''])],
   ['Activity history',['Date','Activity','Minutes','XP','Attribute','Notes'],state.activities.slice().sort((x,y)=>new Date(y.date)-new Date(x.date)).map(a=>[when(a.date),a.name,a.duration||0,'+'+a.xp,(ATTRS.find(x=>x.id===a.primary)||{}).short||'',a.notes||''])]
  ]}}
function reportText(){const r=buildReport();let t=`LIFEMAX - CHARACTER REPORT\n${r.name}${r.build?' ('+r.build+')':''}\nGenerated ${r.date}\n\nSUMMARY\n`+r.summary.map(x=>`  ${x[0]}: ${x[1]}`).join('\n')+'\n';
 r.sections.forEach(([title,head,rows])=>{t+=`\n${title.toUpperCase()}\n`;t+=rows.length?rows.map(x=>'  '+x.filter((v,i)=>v!==''||i<3).join('  |  ')).join('\n')+'\n':'  (none yet)\n'});return t}
function reportDoc(){const r=buildReport();const tbl=(h,rows)=>rows.length?`<table><tr>${h.map(x=>`<th>${esc(x)}</th>`).join('')}</tr>${rows.map(x=>`<tr>${x.map(v=>`<td>${esc(v)}</td>`).join('')}</tr>`).join('')}</table>`:'<p><i>None yet.</i></p>';
 return `<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:w="urn:schemas-microsoft-com:office:word" xmlns="http://www.w3.org/TR/REC-html40"><head><meta charset="utf-8"><title>LifeMax Report</title><style>body{font-family:Calibri,Arial,sans-serif;font-size:11pt;color:#222}h1{font-size:22pt;color:#2b3a67;margin-bottom:0}h2{font-size:14pt;color:#2b3a67;border-bottom:1px solid #bbb;padding-bottom:3px;margin-top:22px}table{border-collapse:collapse;width:100%}th{background:#2b3a67;color:#fff;text-align:left;padding:5px 8px;font-size:10pt}td{border-bottom:1px solid #ddd;padding:5px 8px;font-size:10pt}p.sub{color:#666;margin-top:2px}
/* Premium tactile calendar v2 — immersive training-cycle surface */
.plan-calendar-card{padding:0!important;overflow:hidden;border:1px solid color-mix(in srgb,var(--accent) 22%,var(--line))!important;background:linear-gradient(155deg,color-mix(in srgb,var(--panel) 94%,var(--accent) 6%),var(--panel))!important;box-shadow:0 18px 50px #00000022, inset 0 1px 0 #ffffff0a!important}
.plan-calendar{padding:0;overflow:hidden;position:relative}
.plan-calendar:before{content:"";position:absolute;right:-110px;top:-150px;width:300px;height:300px;border-radius:50%;pointer-events:none;background:radial-gradient(circle,color-mix(in srgb,var(--accent) 19%,transparent),transparent 68%);filter:blur(3px)}
.plan-cal-hero{position:relative;padding:22px 20px 20px;border-bottom:1px solid var(--line-soft);background:linear-gradient(120deg,#7c9cff0d,transparent 60%)}
.plan-cal-head{position:relative;display:flex;align-items:center;justify-content:space-between;gap:14px;margin:0}
.plan-cal-kicker{display:flex;align-items:center;gap:7px;font-size:9px;letter-spacing:.19em;text-transform:uppercase;color:var(--accent-strong);font-weight:850}
.plan-cal-kicker:before{content:"";display:block;width:16px;height:2px;background:var(--accent);border-radius:2px}
.plan-cal-title{font-size:clamp(22px,5vw,29px);font-weight:850;letter-spacing:-.065em;margin:8px 0 0;color:var(--text);line-height:1.08}
.plan-cal-sub{font-size:12px;line-height:1.5;color:var(--muted);margin-top:7px;max-width:245px}
.plan-cal-count{flex:0 0 88px;text-align:center;position:relative;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;aspect-ratio:1;border-radius:50%;background:radial-gradient(circle at 35% 25%,color-mix(in srgb,var(--accent) 20%,var(--panel)),var(--bg-elev) 70%);border:1px solid color-mix(in srgb,var(--accent) 40%,var(--line));box-shadow:inset 0 0 0 5px color-mix(in srgb,var(--accent) 5%,transparent),0 8px 22px #00000018}
.plan-cal-count strong{font-size:24px;line-height:1;color:var(--text);letter-spacing:-.07em}
.plan-cal-count strong span{font-size:12px!important;color:var(--muted)!important;font-weight:600}
.plan-cal-count-label{font-size:8px;text-transform:uppercase;letter-spacing:.12em;font-weight:800;color:var(--muted)}
.plan-cycle-line{height:3px;border-radius:99px;background:var(--line);overflow:hidden;margin-top:18px}
.plan-cycle-line span{display:block;height:100%;border-radius:inherit;background:linear-gradient(90deg,var(--accent),#a9baff);box-shadow:0 0 12px color-mix(in srgb,var(--accent) 55%,transparent);transition:width .35s ease}
.plan-week-section{padding:17px 0 0;position:relative}
.plan-section-label{display:flex;align-items:center;justify-content:space-between;padding:0 20px 10px;font-size:10px;font-weight:800;letter-spacing:.11em;text-transform:uppercase;color:var(--muted)}
.plan-section-label em{font-style:normal;color:var(--accent-strong);letter-spacing:0;text-transform:none;font-weight:650;font-size:11px}
.plan-week-track{display:flex;grid-template-columns:none;gap:8px;overflow-x:auto;overscroll-behavior-x:contain;scroll-snap-type:x mandatory;scrollbar-width:none;padding:0 20px 13px;-webkit-overflow-scrolling:touch}
.plan-week-track::-webkit-scrollbar,.plan-week-days::-webkit-scrollbar{display:none}
.plan-week{flex:0 0 78px;min-width:78px;min-height:80px;padding:12px 8px 10px;border-radius:15px;border:1px solid var(--line-soft);background:linear-gradient(155deg,var(--bg-elev),color-mix(in srgb,var(--panel2) 65%,var(--bg-elev)));color:var(--muted);text-align:left;scroll-snap-align:start;position:relative;overflow:hidden;transition:transform .18s,border-color .18s,background .18s,box-shadow .18s}
.plan-week:active,.plan-day-tile:active{transform:scale(.96)}
.plan-week span{display:block;font-size:10px;font-weight:850;letter-spacing:.08em}
.plan-week small{display:block;font-size:10px;margin-top:10px;color:var(--muted2);font-weight:600}
.plan-week:after{content:"";position:absolute;bottom:0;left:0;height:3px;width:var(--week-progress,0%);background:var(--good);border-radius:0 3px 3px 0}
.plan-week.active{background:linear-gradient(145deg,color-mix(in srgb,var(--accent) 20%,var(--panel)),color-mix(in srgb,var(--accent) 7%,var(--bg-elev)));border-color:var(--accent);color:var(--text);box-shadow:0 6px 18px color-mix(in srgb,var(--accent) 15%,transparent),inset 0 1px 0 #ffffff12}
.plan-week.active span{color:var(--accent-strong)}
.plan-week.active small{color:var(--text)}
.plan-week.current:not(.active){border-color:var(--gold)}
.plan-week.complete small{color:var(--good)}
.plan-day-section{padding:18px 0 0;border-top:1px solid var(--line-soft);position:relative}
.plan-day-heading{padding:0 20px 12px;display:flex;align-items:center;justify-content:space-between;gap:10px}
.plan-day-heading strong{display:block;font-size:15px;letter-spacing:-.035em;color:var(--text)}
.plan-day-heading small{display:block;margin-top:3px;font-size:11px;color:var(--muted)}
.plan-day-heading .swipe-hint{font-size:10px;color:var(--muted2);display:flex;align-items:center;gap:4px;white-space:nowrap}
.plan-week-days{display:flex;gap:9px;overflow-x:auto;scroll-snap-type:x mandatory;scrollbar-width:none;padding:0 20px 18px;-webkit-overflow-scrolling:touch;overscroll-behavior-x:contain}
.plan-day-tile{flex:0 0 73px;min-width:73px;min-height:108px;padding:11px 7px 9px;border:1px solid var(--line-soft);border-radius:15px;background:linear-gradient(160deg,var(--bg-elev),color-mix(in srgb,var(--panel2) 45%,var(--bg-elev)));color:var(--text);display:flex;flex-direction:column;align-items:center;gap:8px;transition:transform .18s,border-color .18s,background .18s,box-shadow .18s;scroll-snap-align:center;position:relative;overflow:hidden}
.plan-day-tile .pdt-name{font-size:9px;font-weight:850;color:var(--muted);letter-spacing:.09em}
.plan-day-tile .pdt-date{font-size:15px;line-height:1;font-weight:750;color:var(--text);font-variant-numeric:tabular-nums}
.plan-day-tile .pdt-icon{display:grid;place-items:center;width:31px;height:31px;border-radius:10px;background:var(--panel2);color:var(--text-dim);border:1px solid var(--line-soft)}
.plan-day-tile .pdt-code{font-size:13px;font-weight:900;line-height:1}
.plan-day-tile .pdt-status{position:absolute;right:7px;top:6px;font-size:10px;font-weight:900}
.plan-day-tile.selected{border-color:var(--accent);background:linear-gradient(155deg,color-mix(in srgb,var(--accent) 22%,var(--panel)),color-mix(in srgb,var(--accent) 7%,var(--bg-elev)));box-shadow:0 8px 20px color-mix(in srgb,var(--accent) 13%,transparent),inset 0 1px 0 #ffffff12;transform:translateY(-2px)}
.plan-day-tile.selected .pdt-name,.plan-day-tile.selected .pdt-date{color:var(--accent-strong)}
.plan-day-tile.selected .pdt-icon{background:var(--accent);border-color:transparent;color:#fff}
.plan-day-tile.done{border-color:#3dd68c55}
.plan-day-tile.done .pdt-status{color:var(--good)}
.plan-day-tile.missed{border-color:#f0717855}
.plan-day-tile.missed .pdt-status{color:var(--bad)}
.plan-day-tile.today:not(.selected){border-color:var(--gold)}
.plan-day-tile.today:not(.selected) .pdt-date{color:var(--gold)}
.plan-day-tile.rest-day{opacity:.68}
.plan-cal-bottom{padding:13px 20px 19px;background:color-mix(in srgb,var(--bg-elev) 60%,transparent);border-top:1px solid var(--line-soft)}
.plan-cal-legend{display:flex;align-items:center;gap:13px;flex-wrap:wrap;margin:0;padding:0;border:0;font-size:10px;color:var(--muted)}
.plan-cal-legend span{display:inline-flex;align-items:center;gap:6px}
.plan-cal-legend i{width:7px;height:7px;border-radius:3px;display:inline-block}
.plan-cal-key{display:flex;gap:6px;flex-wrap:wrap;margin-top:12px}
.plan-cal-key .tag{display:inline-flex;align-items:center;gap:5px;font-size:10px;padding:6px 8px;border-radius:8px;background:var(--panel2);border-color:var(--line-soft)}
@media(max-width:520px){
 .plan-cal-hero{padding:20px 16px 18px}.plan-cal-count{flex-basis:78px}.plan-cal-count strong{font-size:22px}.plan-cal-title{font-size:24px}.plan-cal-sub{font-size:11px;max-width:205px}
 .plan-section-label,.plan-day-heading{padding-left:16px;padding-right:16px}.plan-week-track{padding-left:16px;padding-right:16px;gap:7px}.plan-week{flex-basis:75px;min-width:75px;min-height:76px}
 .plan-week-days{padding-left:16px;padding-right:16px;gap:8px}.plan-day-tile{flex-basis:69px;min-width:69px;min-height:103px}.plan-cal-bottom{padding:12px 16px 16px}.plan-cal-legend{gap:10px}
}
@media(prefers-reduced-motion:reduce){.plan-week,.plan-day-tile,.plan-cycle-line span{transition:none}}

</style></head><body><h1>${esc(r.name)}</h1><p class="sub">${esc(r.build)} &middot; LifeMax report &middot; ${esc(r.date)}</p><h2>Summary</h2>${tbl(['Stat','Value'],r.summary)}${r.sections.map(x=>`<h2>${x[0]}</h2>${tbl(x[1],x[2])}`).join('')}</body></html>`}
function exportReport(kind){const doc=kind==='doc',content=doc?reportDoc():'\ufeff'+reportText();lmaDownload(content,'life-max-report-'+getToday()+(doc?'.doc':'.txt'),doc?'application/msword':'text/plain',doc?'Word report':'Text report')}
function M(){if(!state.money)state.money={account:'',start:0,goal:0,tx:[]};return state.money}
function fmt(n){return new Intl.NumberFormat(undefined,{style:'currency',currency:'USD'}).format(n)}
function moneyBalance(){const m=M();return Math.round(((Number(m.start)||0)+m.tx.reduce((s,t)=>s+(t.type==='deposit'?t.amount:-t.amount),0))*100)/100}
function addTx(){const amt=Math.round((parseFloat(document.getElementById('txAmount').value)||0)*100)/100;if(!(amt>0)){toast('Enter an amount');return}
 const type=document.getElementById('txType').value,note=document.getElementById('txNote').value.trim(),iso=new Date().toISOString();
 if(type==='withdraw'&&amt>moneyBalance()){toast('Not enough in the account');return}
 const tx={id:'tx_'+Date.now(),type,amount:amt,note,date:iso};let xp=0;
 if(type==='deposit'){const bal=moneyBalance(),peak=Math.max(Number(M().peakBalance)||0,bal),net=Math.max(0,bal+amt-peak);M().peakBalance=Math.max(peak,bal+amt);
  const wk=new Date();wk.setDate(wk.getDate()-6);const wks=wk.toLocaleDateString('en-CA'),moneyActs=state.activities.filter(a=>a.type==='money'),weekXP=moneyActs.filter(a=>activityDate(a)>=wks).reduce((n,a)=>n+(Number(a.xp)||0),0),doneToday=moneyActs.some(a=>activityDate(a)===getToday())||state.activities.some(a=>a.link==='business.transfer-money-into-savings'&&activityDate(a)===getToday());
  if(!doneToday&&net>=ECO.depositMin){xp=Math.min(ECO.depositCapDay,Math.max(5,Math.round(net/10)),Math.max(0,ECO.depositCapWeek-weekXP));xp=xp>0?ecoCap(ecoRestedTake(xp),getToday()):0}
  if(xp>0){const pX=Math.round(xp*.7),sX=xp-pX;const act={id:'act_'+Date.now(),type:'money',name:'Deposit '+fmt(amt),icon:'coin',duration:0,skill:'Saving',primary:'business',secondary:'discipline',xp,date:iso,notes:note,xpSplit:[{attr:'business',xp:pX},{attr:'discipline',xp:sX}]};
  state.activities.push(act);state.attrXP.business=(state.attrXP.business||0)+pX;state.attrXP.discipline=(state.attrXP.discipline||0)+sX;state.skillXP.Saving=(state.skillXP.Saving||0)+xp;tx.actId=act.id}}
 M().tx.push(tx);document.getElementById('txAmount').value='';document.getElementById('txNote').value='';save();toast(type==='deposit'?`Deposit recorded${xp?' · +'+xp+' XP':''}`:'Withdrawal recorded')}
function deleteTx(id){const m=M(),t=m.tx.find(x=>x.id===id);if(!t||!confirm('Remove this transaction? Any XP it earned is removed too.'))return;
 if(t.actId){const a=state.activities.find(x=>x.id===t.actId);if(a){state.attrXP.business=Math.max(0,(state.attrXP.business||0)-a.xp);state.skillXP.Saving=Math.max(0,(state.skillXP.Saving||0)-a.xp);if(!state.skillXP.Saving)delete state.skillXP.Saving;state.activities=state.activities.filter(x=>x.id!==t.actId)}}
 m.tx=m.tx.filter(x=>x.id!==id);save();toast('Transaction removed')}
function saveAccount(){const m=M();m.account=document.getElementById('acctName').value.trim().slice(0,40);m.start=parseFloat(document.getElementById('acctStart').value)||0;m.goal=Math.max(0,parseFloat(document.getElementById('acctGoal').value)||0);save();toast('Account saved')}
function renderMoney(){const m=M(),bal=moneyBalance(),el=id=>document.getElementById(id);if(!el('moneyBalance'))return;
 el('moneyBalance').textContent=fmt(bal);el('moneyAccount').textContent=m.account||'Savings account';
 const pct=m.goal>0?Math.min(100,Math.max(0,bal/m.goal*100)):0;el('moneyGoalBar').style.width=pct+'%';
 el('moneyGoalText').textContent=m.goal>0?`${fmt(bal)} of ${fmt(m.goal)} goal · ${Math.round(pct)}%`:'Set a goal in Account settings';
 const mo=new Date().toISOString().slice(0,7);el('moneyMonth').textContent='+'+fmt(m.tx.filter(t=>t.type==='deposit'&&activityDate(t)===activityDate(t)&&new Date(t.date).toLocaleDateString('en-CA').slice(0,7)===getToday().slice(0,7)).reduce((s,t)=>s+t.amount,0))+' this month';
 const rows=m.tx.slice().sort((a,b)=>new Date(b.date)-new Date(a.date));
 el('txList').innerHTML=rows.length?rows.map(t=>`<div class="minirow"><span><strong>${t.pay?'Paycheck':t.type==='deposit'?'Deposit':'Withdrawal'}</strong>${t.note?' · '+esc(t.note):''}<br><small>${new Date(t.date).toLocaleDateString()}</small></span><span style="display:flex;align-items:center;gap:10px"><strong style="color:${t.type==='deposit'?'var(--accent2)':'var(--red)'}">${t.type==='deposit'?'+':'−'}${fmt(t.amount)}</strong><button class="btn small secondary" onclick="deleteTx('${t.id}')">Remove</button></span></div>`).join(''):'<div class="empty"><div>$</div>No transactions yet.</div>';
 if(!['acctName','acctStart','acctGoal'].includes(document.activeElement&&document.activeElement.id)){el('acctName').value=m.account||'';el('acctStart').value=m.start||'';el('acctGoal').value=m.goal||''}}
let planSel=null;

/* —— Character classes —— */
const CLASSES=[
  {id:'athlete',name:'Athlete',glyph:'athlete',ready:true,
   tagline:'Strength · endurance · nutrition',
   focus:'Physical fitness, strength, endurance, nutrition, and overall health.',
   attrs:['Physique','Endurance','Wellbeing'],
   training:'High — 3 strength, 2 cardio, mobility, rest',
   diet:'Performance fuel targets (diet system next)',
   week:'Mon/Wed/Fri strength · Tue/Sat cardio · Thu recovery · Sun rest',
   accent:'#3dd68c'},
  {id:'scholar',name:'Scholar',glyph:'scholar',ready:true,
   tagline:'Learning · reading · intellect',
   focus:'Learning, reading, knowledge, critical thinking, and intellectual growth.',
   attrs:['Intelligence','Discipline','Wellbeing'],
   training:'Light mobility · focus blocks over gym volume',
   diet:'Steady, regular meals · plan included',
   week:'Study quests · reading goals · deep-work missions',
   accent:'#7c9cff'},
  {id:'entrepreneur',name:'Entrepreneur',glyph:'entrepreneur',ready:true,
   tagline:'Business · skills · independence',
   focus:'Building businesses, valuable skills, opportunities, and professional independence.',
   attrs:['Business','Discipline','Social'],
   training:'Optional maintenance — ship work first',
   diet:'Steady energy for long work days · plan included',
   week:'Outreach · builds · money missions',
   accent:'#e8b84a'},
  {id:'disciple',name:'Disciple',glyph:'disciple',ready:false,
   tagline:'Discipline · mindfulness · purpose',
   focus:'Discipline, mindfulness, personal values, inner growth, and living with purpose.',
   attrs:['Discipline','Wellbeing','Intelligence'],
   training:'Gentle movement · recovery rituals',
   diet:'Mindful eating habits (coming soon)',
   week:'Routines · reflection · consistency quests',
   accent:'#a78bfa'},
  {id:'diplomat',name:'Diplomat',glyph:'diplomat',ready:false,
   tagline:'Communication · relationships · leadership',
   focus:'Social skills, communication, relationships, networking, empathy, and leadership.',
   attrs:['Social','Discipline','Intelligence'],
   training:'Light activity · confidence through action',
   diet:'Social-life balance (coming soon)',
   week:'Conversations · events · connection quests',
   accent:'#f0a35a'},
  {id:'explorer',name:'Explorer',glyph:'explorer',ready:true,
   tagline:'Adventure · skills · new ground',
   focus:'Adventure, new experiences, places, practical skills, and leaving the comfort zone.',
   attrs:['Adventure','Endurance','Social'],
   training:'Mixed — outdoor, practical, varied',
   diet:'Fuel for the trail · plan included',
   week:'Outings · DIY · try-something-new quests',
   accent:'#2dd4bf'}
];
