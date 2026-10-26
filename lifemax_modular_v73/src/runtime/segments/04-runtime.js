function seedReadingList(){
 if(state.mediaSeededV1)return;
 if(!Array.isArray(state.mediaItems))state.mediaItems=[];
 const existing=new Set(state.mediaItems.map(x=>(x.title||'').trim().toLowerCase()));
 const additions=DEFAULT_READING_LIST.filter(x=>!existing.has(x.title.toLowerCase()));
 state.mediaItems.push(...additions);
 state.mediaSeededV1=true;
}
function freshState(){return {name:'The Life Architect',build:'The Life Architect',classId:null,activities:[],attrXP:Object.fromEntries(ATTRS.map(a=>[a.id,0])),skillXP:{},quests:JSON.parse(JSON.stringify(defaultQuests)),questDone:{},benchmarks:[],mediaItems:[],customRules:{},tutorialDone:false,tipsSeen:{},created:new Date().toISOString()}}
function setLightMode(enabled){document.documentElement.classList.toggle('light-theme',!!enabled);try{localStorage.setItem('lifeMaxTheme',enabled?'light':'dark')}catch(e){}const toggle=document.getElementById('lightModeToggle');if(toggle)toggle.checked=!!enabled;toast(enabled?'Light mode enabled':'Dark mode enabled')}
function initTheme(){let theme='dark';try{theme=localStorage.getItem('lifeMaxTheme')||'dark'}catch(e){}document.documentElement.classList.toggle('light-theme',theme==='light');const toggle=document.getElementById('lightModeToggle');if(toggle)toggle.checked=theme==='light'}
function load(){try{const raw=localStorage.getItem(STORE);if(raw){state={...freshState(),...JSON.parse(raw)};state.attrXP={...freshState().attrXP,...(state.attrXP||{})};if(Array.isArray(state.activities)&&state.activities.length){try{rebuildAttrXP();rebuildSkillXP()}catch(e){}}rules={...structuredClone(DEFAULT_RULES),...(state.customRules||{})};seedReadingList();applyClassContent();localStorage.setItem(STORE,JSON.stringify(state));return}}catch(e){}state=freshState();rules=structuredClone(DEFAULT_RULES);seedReadingList();try{localStorage.setItem(STORE,JSON.stringify(state))}catch(e){}}
function save(){rebuildAttrXP();rebuildSkillXP();state.customRules=rules;checkBadges();checkLevelUps();try{localStorage.setItem(STORE,JSON.stringify(state))}catch(e){console.warn("Save failed",e);toast("Could not save to this browser (storage full?)")}renderAll()}
function xpForLevel(level){return 100+25*(level-1)}
function levelData(total){let level=1,left=Math.max(0,total);while(left>=xpForLevel(level)&&level<1000){left-=xpForLevel(level);level++}return {level,into:left,needed:xpForLevel(level),pct:Math.min(100,left/xpForLevel(level)*100)}}
function totalXP(){return Object.values(state.attrXP).reduce((a,b)=>a+b,0)}
function characterLevel(){return levelData(totalXP())}
function attrXP(id){return Number(state.attrXP[id]||0)}
function rebuildAttrXP(){
  const next=Object.fromEntries(ATTRS.map(a=>[a.id,0]));
  (state.activities||[]).forEach(a=>{
    const splits=a.xpSplit&&a.xpSplit.length?a.xpSplit:[{attr:a.primary,xp:a.xp||0}];
    splits.forEach(z=>{
      const id=z&&z.attr;
      if(id&&Object.prototype.hasOwnProperty.call(next,id))next[id]+=Math.max(0,Number(z.xp)||0);
      else if(id&&ATTRS.some(x=>x.id===id)){next[id]=(next[id]||0)+Math.max(0,Number(z.xp)||0)}
    });
  });
  const decay=state.decayTotal||{};
  ATTRS.forEach(a=>{next[a.id]=Math.max(0,(next[a.id]||0)-Math.max(0,Number(decay[a.id])||0))});
  state.attrXP=next;
}
function rebuildSkillXP(){
  const next={};
  (state.activities||[]).forEach(a=>{
    const sk=(a.skill||'').trim();if(!sk)return;
    next[sk]=(next[sk]||0)+Math.max(0,Number(a.xp)||0);
  });
  state.skillXP=next;
}

function attrLevel(id){return levelData(attrXP(id))}
function getToday(){return new Date().toLocaleDateString('en-CA')}
function activityDate(a){return new Date(a.date).toLocaleDateString('en-CA')}
function todayActivities(){return state.activities.filter(a=>activityDate(a)===getToday())}
function questKey(q){let d=new Date();if(q.cadence==='daily')return q.id+'_'+getToday();if(q.cadence==='weekly'){const first=new Date(d);first.setDate(d.getDate()-((d.getDay()+6)%7));return q.id+'_'+first.toLocaleDateString('en-CA')}return q.id+'_once'}
function isQuestDone(q){return !!state.questDone[questKey(q)]}
function toast(msg){const el=document.getElementById('toast');el.textContent=msg;el.classList.add('show');clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove('show'),2200)}
function navigate(view){if(view!=='meals')fuelCancelMotion();document.querySelectorAll('.view').forEach(v=>v.classList.toggle('active',v.id==='view-'+view));document.querySelectorAll('.navbtn').forEach(b=>b.classList.toggle('active',b.dataset.view===view));if(view==='log')updatePreview();if(view==='meals')renderMeals();if(view==='recipes')renderRecipes();if(view==='plan')renderPlan();if(view==='character')chReplay();if(view==='pet')petOpen();if(view==='dashboard')ovReplay();window.scrollTo({top:0,behavior:'smooth'});maybeShowFeatureTip(view);try{petSync()}catch(e){}}
function attrCard(a){const d=attrLevel(a.id);return `<div class="attr" role="button" tabindex="0" onclick="openAttr('${a.id}')" onkeydown="if(event.key==='Enter')openAttr('${a.id}')"><div class="attrtop"><span class="attrname">${px(a.icon)} ${a.name}</span><span class="attrlevel">LV ${d.level}</span></div><div class="xpbar"><div class="xpfill" style="width:${d.pct}%"></div></div><div class="attrmeta"><span>${attrXP(a.id).toLocaleString()} total XP</span><span>${Math.round(d.pct)}% to next</span></div></div>`}
function renderAttrs(){document.getElementById('dashboardAttrs').innerHTML=ATTRS.map(attrCard).join('')}
function renderActivities(){const recent=state.activities.slice().sort((a,b)=>new Date(b.date)-new Date(a.date)).slice(0,5);const markup=recent.length?recent.map(a=>`<div class="activity"><div class="acticon">${iconHtml(a.icon,20)}</div><div class="actmain"><strong>${esc(a.name)}</strong><small>${new Date(a.date).toLocaleString([], {month:'short',day:'numeric',hour:'numeric',minute:'2-digit'})} · ${a.duration||0} min</small></div><div class="actxp">+${a.xp} XP</div></div>`).join(''):`<div class="empty"><div>✦</div>No activities yet.<br><button class="btn small" style="margin-top:12px" onclick="navigate('log')">Log your first activity</button></div>`;document.getElementById('recentActivities').innerHTML=markup}
function renderBenchmarks(){document.getElementById('benchmarks').innerHTML=state.benchmarks.length?state.benchmarks.map((b,i)=>`<div class="minirow"><span><strong>${esc(b.name)}</strong><br><small>${esc(b.value)}</small></span><button class="btn small secondary" onclick="deleteBenchmark(${i})">Remove</button></div>`).join(''):'<p class="muted" style="font-size:12px">No benchmarks yet. Add a measurable result you care about.</p>'}
function renderHistory(){
  if(!state.dayReflections)state.dayReflections={};
  const days=journalDays();
  if(!histSel||!days.includes(histSel))histSel=days[0]||getToday();
  const daysEl=document.getElementById('journalDays');
  const entryEl=document.getElementById('journalEntry');
  if(!daysEl||!entryEl)return;
  if(!days.length){
    daysEl.innerHTML='';
    entryEl.innerHTML=`<div class="empty"><div>◷</div><strong>No journal entries yet</strong><br>Log an activity, complete a quest, or finish a plan session. Everything you enter will show up here by day.</div>`;
    return;
  }
  daysEl.innerHTML=days.map(d=>{
    const acts=actsForDay(d);
    const hasRef=!!(state.dayReflections[d]||'').trim();
    const label=journalDayLabel(d);
    return `<button type="button" class="journal-day-btn ${d===histSel?'on':''}" onclick="selectJournalDay('${d}')">${esc(label)}<small>${acts.length} logged${hasRef?' · reflection':''}</small></button>`;
  }).join('');
  entryEl.innerHTML=renderJournalDay(histSel);
}
let histSel=null;
function journalDays(){
  const set=new Set();
  (state.activities||[]).forEach(a=>{if(a&&a.date)set.add(activityDate(a))});
  Object.keys(state.dayReflections||{}).forEach(k=>{if((state.dayReflections[k]||'').trim())set.add(k)});
  if(state.plan&&state.plan.checks){
    Object.keys(state.plan.checks).forEach(d=>{
      const c=state.plan.checks[d];
      if(c&&c.log&&Object.keys(c.log).some(k=>(c.log[k]||'').trim()))set.add(d);
    });
  }
  return Array.from(set).sort((a,b)=>b.localeCompare(a));
}
function actsForDay(d){
  return (state.activities||[]).filter(a=>activityDate(a)===d).sort((a,b)=>new Date(a.date)-new Date(b.date));
}
function journalDayLabel(d){
  const today=getToday();
  if(d===today)return 'Today';
  const y=new Date();y.setDate(y.getDate()-1);
  if(d===y.toLocaleDateString('en-CA'))return 'Yesterday';
  try{
    const dt=new Date(d+'T12:00:00');
    return dt.toLocaleDateString(undefined,{weekday:'short',month:'short',day:'numeric'});
  }catch(e){return d}
}
function journalDayTitle(d){
  try{
    const dt=new Date(d+'T12:00:00');
    return dt.toLocaleDateString(undefined,{weekday:'long',month:'long',day:'numeric',year:'numeric'});
  }catch(e){return d}
}
function selectJournalDay(d){histSel=d;renderHistory()}
function saveDayReflection(d,val){
  if(!state.dayReflections)state.dayReflections={};
  const t=val.trim();
  if(t)state.dayReflections[d]=t;else delete state.dayReflections[d];
  state.customRules=rules;
  try{localStorage.setItem(STORE,JSON.stringify(state))}catch(e){}
  // soft refresh day list badges without losing textarea focus
  const daysEl=document.getElementById('journalDays');
  if(daysEl){
    const days=journalDays();
    daysEl.innerHTML=days.map(day=>{
      const acts=actsForDay(day);
      const hasRef=!!(state.dayReflections[day]||'').trim();
      return `<button type="button" class="journal-day-btn ${day===histSel?'on':''}" onclick="selectJournalDay('${day}')">${esc(journalDayLabel(day))}<small>${acts.length} logged${hasRef?' · reflection':''}</small></button>`;
    }).join('');
  }
}
function planLogsForDay(d){
  if(!state.plan||!state.plan.checks||!state.plan.checks[d])return [];
  const c=state.plan.checks[d];
  const log=c.log||{};
  const v=c.v||{};
  // resolve labels from planDay if possible
  const day=typeof planDay==='function'?planDay(d):null;
  const labelOf=k=>{
    if(day&&day.items){const it=day.items.find(x=>x.k===k);if(it)return it.l}
    return k;
  };
  const keys=Array.from(new Set([...Object.keys(log),...Object.keys(v)]));
  return keys.map(k=>{
    const note=(log[k]||'').trim();
    const weight=(v[k]||'').trim();
    if(!note&&!weight)return null;
    return {key:k,label:labelOf(k),note,weight};
  }).filter(Boolean);
}
function renderJournalDay(d){
  const acts=actsForDay(d);
  const logs=planLogsForDay(d);
  const ref=state.dayReflections&&state.dayReflections[d]||'';
  const totalXP=acts.reduce((s,a)=>s+(a.xp||0),0);
  let body='';
  if(!acts.length&&!logs.length){
    body=`<div class="empty" style="padding:24px 8px"><div>✎</div>Nothing logged this day yet.${d===getToday()?' Start with an activity, quest, or plan session.':''}</div>`;
  }else{
    body=acts.map(a=>{
      const attr=(ATTRS.find(x=>x.id===a.primary)||{}).short||'';
      const time=new Date(a.date).toLocaleTimeString([], {hour:'numeric',minute:'2-digit'});
      const notes=(a.notes||'').trim();
      // For plan activities, attach that day's exercise logs under the plan block once
      let extra='';
      if(a.type==='plan'&&logs.length){
        extra=`<div class="j-plan-logs">${logs.map(l=>`<div><b>${esc(l.label)}</b>${l.weight?' · '+esc(l.weight):''}${l.note?' — '+esc(l.note):''}</div>`).join('')}</div>`;
      }
      return `<div class="j-item"><div class="j-item-top"><div class="acticon">${iconHtml(a.icon,18)}</div><div class="j-item-body"><div style="display:flex;align-items:baseline;gap:6px;flex-wrap:wrap"><strong>${esc(a.name)}</strong><span class="j-time">${esc(time)}${attr?' · '+esc(attr):''}${a.duration?` · ${a.duration} min`:''}</span><span class="j-xp">+${a.xp||0}</span></div>${notes?`<div class="j-notes">${esc(notes)}</div>`:''}${extra}</div></div></div>`;
    }).join('');
    // If there are plan logs but no plan activity entry, still show them
    if(logs.length&&!acts.some(a=>a.type==='plan')){
      body+=`<div class="j-item"><div class="j-item-top"><div class="acticon">${px('dumbbell',18)}</div><div class="j-item-body"><strong>Workout notes</strong><div class="j-plan-logs">${logs.map(l=>`<div><b>${esc(l.label)}</b>${l.weight?' · '+esc(l.weight):''}${l.note?' — '+esc(l.note):''}</div>`).join('')}</div></div></div></div>`;
    }
  }
  return `<div class="journal-date">${esc(journalDayTitle(d))}</div>
    <div class="journal-meta">${acts.length} entr${acts.length===1?'y':'ies'} · ${totalXP} XP${logs.length?` · ${logs.length} exercise note${logs.length===1?'':'s'}`:''}</div>
    ${body}
    <div class="j-reflect"><label for="dayReflection">Reflection <span style="font-weight:400;color:var(--muted2)">(optional)</span></label>
    <textarea id="dayReflection" maxlength="4000" placeholder="How did today feel? What mattered? What will you do differently?" onchange="saveDayReflection('${d}',this.value)">${esc(ref)}</textarea></div>`;
}
function renderStats(){const lvl=characterLevel();document.getElementById('heroName').textContent=state.name||'The Life Architect';document.getElementById('heroLevel').textContent=lvl.level;document.getElementById('heroXP').textContent=totalXP().toLocaleString()+' XP';document.getElementById('heroBar').style.width=lvl.pct+'%';document.getElementById('heroProgress').textContent=`${lvl.into} / ${lvl.needed} XP to next level`;document.getElementById('heroPercent').textContent=Math.round(lvl.pct)+'%';document.getElementById('achievementCount').textContent=Object.keys(state.questDone).length;document.getElementById('totalActivities').textContent=state.activities.length;document.getElementById('todayXP').textContent=todayActivities().reduce((s,a)=>s+a.xp,0)+' XP earned';document.getElementById('totalXPStat').textContent=totalXP().toLocaleString();let strongest=ATTRS.slice().sort((a,b)=>attrXP(b.id)-attrXP(a.id))[0];document.getElementById('strongestStat').textContent=attrXP(strongest.id)>0?strongest.short:'—';document.getElementById('skillsStat').textContent=Object.values(state.skillXP).filter(v=>v>0).length;document.getElementById('questsStat').textContent=Object.keys(state.questDone).length;let tq=todayActivities().length;let qd=state.quests.filter(q=>q.cadence==='daily'&&isQuestDone(q)).length;document.getElementById('dailyMini').innerHTML=`<div class="minirow"><span>Activities logged</span><strong>${tq}</strong></div><div class="minirow"><span>Quests completed</span><strong>${qd}</strong></div><div class="minirow"><span>Active skills</span><strong>${Object.values(state.skillXP).filter(v=>v>0).length}</strong></div>`;document.getElementById('todayLabel').textContent=new Date().toLocaleDateString(undefined,{weekday:'long',month:'long',day:'numeric'}).toUpperCase()}
function renderRules(){document.getElementById('xpRules').innerHTML=Object.entries(rules).map(([id,r])=>`<div class="settingsline"><div style="flex:1"><strong>${iconHtml(r.icon)} ${esc(r.name)}</strong><div class="muted" style="font-size:11px">${esc(r.skill)} · base XP</div></div><input aria-label="${esc(r.name)} base XP" id="rule-${id}" type="number" min="1" max="200" value="${r.base}" style="width:78px;background:#0b1223;color:var(--text);border:1px solid var(--line);padding:8px;border-radius:9px"></div>`).join('')}
function renderAll(){renderStats();renderExtras();renderAttrs();renderActivities();renderSkills();renderBenchmarks();renderQuests();renderHistory();renderRules();renderMedia();renderMeals();if(document.getElementById('characterName'))document.getElementById('characterName').value=state.name;if(document.getElementById('characterBuild'))document.getElementById('characterBuild').value=state.build;if(document.getElementById('characterWeight'))document.getElementById('characterWeight').value=state.weight||'';if(document.getElementById('characterHeightFt'))document.getElementById('characterHeightFt').value=state.heightFt||'';if(document.getElementById('characterHeightIn'))document.getElementById('characterHeightIn').value=state.heightIn||'';renderCharacterIntro();populateAttrSelects();renderRecipes()}
function populateAttrSelects(){const p=document.getElementById('primaryAttr'),s=document.getElementById('secondaryAttr');if(!p)return;const opts=ATTRS.map(a=>`<option value="${a.id}">${a.name}</option>`).join('');let pv=p.value,sv=s.value;p.innerHTML=opts;s.innerHTML='<option value="">No secondary attribute</option>'+opts;p.value=pv||'intelligence';s.value=sv||''}
function activityPresetChanged(){const id=document.getElementById('activityType').value,r=rules[id]||rules.custom;document.getElementById('customNameWrap').style.display=id==='custom'?'block':'none';document.getElementById('skillName').value=r.skill;document.getElementById('primaryAttr').value=r.primary;document.getElementById('secondaryAttr').value=r.secondary||'';updatePreview()}
function calcPreview(){const id=document.getElementById('activityType').value,r=rules[id]||rules.custom;const dur=Math.max(0,Number(document.getElementById('duration').value)||0);const quality=Number(document.getElementById('quality').value)||1;/* 30 min = 1× base; scales linearly to 3× at 90+ min; short sessions floor at 0.4× */const durFactor=dur<=0?0:Math.min(3,Math.max(0.4,dur/30));let base=r.base*durFactor;let total=Math.max(0,Math.round(base*quality));return {total,primary:document.getElementById('primaryAttr').value||r.primary,secondary:document.getElementById('secondaryAttr').value,skill:(document.getElementById('skillName').value||r.skill).trim()||r.skill,durFactor}}
function updatePreview(){const x=calcPreview();document.getElementById('previewXP').textContent=x.total;document.getElementById('previewBar').style.width=Math.min(100,x.total/120*100)+'%';const p=ATTRS.find(a=>a.id===x.primary);const s=ATTRS.find(a=>a.id===x.secondary);let chunks=[];if(x.secondary&&x.secondary!==x.primary){const pXP=Math.round(x.total*.7),sXP=x.total-pXP;chunks.push(`<span class="preview-chip">${p?px(p.icon):''} ${p?.short||'Primary'} <strong>+${pXP}</strong></span>`,`<span class="preview-chip">${s?px(s.icon):''} ${s?.short||'Secondary'} <strong>+${sXP}</strong></span>`)}else chunks.push(`<span class="preview-chip">${p?px(p.icon):''} ${p?.short||'Attribute'} <strong>+${x.total}</strong></span>`);if(x.durFactor)chunks.push(`<span class="preview-chip">Duration <strong>${x.durFactor.toFixed(2)}×</strong></span>`);chunks.push(`<span class="preview-chip">Skill: <strong>${esc(x.skill||'Skill')}</strong></span>`);document.getElementById('previewSplit').innerHTML=chunks.join('')}
function submitActivity(e){e.preventDefault();const id=document.getElementById('activityType').value,r=rules[id]||rules.custom,x=calcPreview();if(!x.total){toast('Add a duration to calculate XP');return}const todayCount=state.activities.filter(a=>a.type===id&&activityDate(a)===getToday()).length;const factor=todayCount>=5?.25:todayCount>=3?.5:1;const xp=Math.max(1,Math.round(x.total*factor));const p=x.primary,s=x.secondary;let pXP=xp, sXP=0;if(s&&s!==p){pXP=Math.round(xp*.7);sXP=xp-pXP}const name=id==='custom'?(document.getElementById('customName').value.trim()||'Custom activity'):r.name;const activity={id:crypto.randomUUID?crypto.randomUUID():String(Date.now()),type:id,name,icon:r.icon,duration:Number(document.getElementById('duration').value)||0,quality:Number(document.getElementById('quality').value),skill:x.skill,primary:p,secondary:s,xp,date:new Date().toISOString(),notes:document.getElementById('activityNotes').value.trim(),xpSplit:[{attr:p,xp:pXP},...(s&&s!==p?[{attr:s,xp:sXP}]:[])]};state.activities.push(activity);activity.xpSplit.forEach(z=>state.attrXP[z.attr]=(state.attrXP[z.attr]||0)+z.xp);state.skillXP[x.skill]=(state.skillXP[x.skill]||0)+xp;save();document.getElementById('activityNotes').value='';document.getElementById('duration').value=30;document.getElementById('quality').value='1';toast(`Activity saved · +${xp} XP`);navigate('dashboard')}
function toggleQuest(id,checked){const q=state.quests.find(q=>q.id===id);if(!q)return;const A=q.attribute||'discipline';if(checked&&questLocked(q)){toast('Locked until you reach level '+stageLvl(q.stage));renderQuests();return}const key=questKey(q);if(checked&&!state.questDone[key]){const qid='quest_'+Date.now();state.questDone[key]=qid;const xp=Math.max(0,Number(q.xp)||0);const noteEl=document.getElementById('qnote-'+id);const qnote=(noteEl&&noteEl.value.trim())||'';state.attrXP[A]=(state.attrXP[A]||0)+xp;state.skillXP['Quest Completion']=(state.skillXP['Quest Completion']||0)+xp;state.activities.push({id:qid,skill:'Quest Completion',type:'quest',name:'Quest: '+q.name,icon:'flag',duration:0,xp,primary:A,date:new Date().toISOString(),notes:qnote||'Quest completed',xpSplit:[{attr:A,xp}]});toast(`Quest complete · +${xp} XP`)}else if(!checked&&state.questDone[key]){const aid=state.questDone[key],xp=Math.max(0,Number(q.xp)||0);delete state.questDone[key];state.attrXP[A]=Math.max(0,(state.attrXP[A]||0)-xp);state.skillXP['Quest Completion']=Math.max(0,(state.skillXP['Quest Completion']||0)-xp);state.activities=state.activities.filter(a=>a.id!==aid);toast('Quest undone · XP removed')}save()}
function showCustomQuest(){document.getElementById('customQuestCard').style.display='block';document.getElementById('newQuestName').focus()}
function hideCustomQuest(){document.getElementById('customQuestCard').style.display='none'}
function createQuest(){const name=document.getElementById('newQuestName').value.trim();if(!name){toast('Give your quest a name');return}state.quests.push({id:'custom_'+Date.now(),name,desc:'Custom quest created by you.',cadence:document.getElementById('newQuestCadence').value,xp:Math.min(500,Math.max(1,Number(document.getElementById('newQuestXP').value)||25)),kind:'custom'});document.getElementById('newQuestName').value='';hideCustomQuest();save();toast('Quest created')}
function deleteActivity(id){const a=state.activities.find(a=>a.id===id);if(!a)return;if(!confirm('Undo this activity and remove its XP from your stats?'))return;state.activities=state.activities.filter(a=>a.id!==id);M().tx=M().tx.filter(t=>t.actId!==id);if(a.planDate&&state.plan&&state.plan.checks[a.planDate])delete state.plan.checks[a.planDate].actId;Object.keys(state.questDone).forEach(k=>{if(state.questDone[k]===id)delete state.questDone[k]});(a.xpSplit||[{attr:a.primary,xp:a.xp}]).forEach(z=>state.attrXP[z.attr]=Math.max(0,(state.attrXP[z.attr]||0)-z.xp));state.skillXP[a.skill]=Math.max(0,(state.skillXP[a.skill]||0)-a.xp);if(state.skillXP[a.skill]===0)delete state.skillXP[a.skill];save();toast('Activity undone')}

function saveCharacter(){state.name=document.getElementById('characterName').value.trim()||'The Life Architect';state.build=document.getElementById('characterBuild').value;state.weight=Number(document.getElementById('characterWeight').value)||null;state.heightFt=Number(document.getElementById('characterHeightFt').value)||null;state.heightIn=Number(document.getElementById('characterHeightIn').value)||0;save();toast('Character saved')}
function xpToGainLevels(totalXP,levels){
  const d=levelData(Math.max(0,Number(totalXP)||0));
  let need=Math.max(0,d.needed-d.into);
  let lvl=d.level+1;
  for(let i=1;i<levels;i++){need+=xpForLevel(lvl);lvl++}
  return Math.max(1,need);
}
function toggleDevMode(on){
  state.devMode=!!on;
  const tools=document.getElementById('devModeTools');
  if(tools)tools.style.display=on?'block':'none';
  try{state.customRules=rules;localStorage.setItem(STORE,JSON.stringify(state))}catch(e){}
  toast(on?'Developer mode on · open an attribute sheet':'Developer mode off');
}
function renderDevMode(){
  const on=!!state.devMode;
  const cb=document.getElementById('devModeOn');
  const tools=document.getElementById('devModeTools');
  if(cb&&document.activeElement!==cb)cb.checked=on;
  if(tools)tools.style.display=on?'block':'none';
}
function isDevAct(id){return typeof id==='string'&&id.startsWith('dev.boost')}
function devActLevels(id){const m=/^dev\.boost(\d+)\./.exec(id||'');return m?Number(m[1]):2}
function getDevActs(attrId){
  if(!state.devMode||!attrId)return [];
  return [1,2].map(n=>{
    const xp=xpToGainLevels(attrXP(attrId),n);
    return {
      id:'dev.boost'+n+'.'+attrId,
      attr:attrId,
      name:'Dev: +'+n+' level'+(n>1?'s':''),
      desc:'Developer test. Awards '+xp.toLocaleString()+' XP — enough to advance this attribute by '+n+' level'+(n>1?'s':'')+' from its current progress.',
      xp:xp,
      min:0,
      fl:'',
      skill:'Developer',
      dev:true
    };
  }).reverse();
}
function actById(id){
  if(isDevAct(id)){
    const levels=devActLevels(id);
    const attr=id.split('.').slice(2).join('.')||id.replace(/^dev\.boost\d+\./,'');
    const xp=xpToGainLevels(attrXP(attr),levels);
    return {
      id,attr,name:'Dev: +'+levels+' level'+(levels>1?'s':''),
      desc:'Developer test. Awards enough XP to advance this attribute by '+levels+' level'+(levels>1?'s':'')+'.',
      xp,min:0,fl:'',skill:'Developer',dev:true
    };
  }
  return ACTS.find(x=>x.id===id);
}


function addBenchmark(){const name=document.getElementById('benchName').value.trim(),value=document.getElementById('benchValue').value.trim();if(!name||!value){toast('Enter a name and result');return}state.benchmarks.push({name,value,date:new Date().toISOString()});document.getElementById('benchName').value='';document.getElementById('benchValue').value='';save();toast('Benchmark added')}
function deleteBenchmark(i){state.benchmarks.splice(i,1);save();toast('Benchmark removed')}
function saveRules(){Object.keys(rules).forEach(id=>{const el=document.getElementById('rule-'+id);if(el)rules[id].base=Math.min(200,Math.max(1,Number(el.value)||1))});save();toast('XP rules saved')}
function resetRules(){rules=structuredClone(DEFAULT_RULES);save();toast('Default XP rules restored')}
function backupJson(){return JSON.stringify({app:'Life Max RPG Prototype',version:1,exportedAt:new Date().toISOString(),state,rules,extras:lmaBackupExtras()},null,2)}
function exportData(){lmaDownload(backupJson(),'life-max-backup-'+getToday()+'.json','application/json','Backup')}
function importData(e,fromOnboarding){const file=e.target.files[0];if(!file)return;const reader=new FileReader();reader.onload=()=>{try{const data=JSON.parse(reader.result);const imported=data.state||data;if(!imported.activities||!imported.attrXP)throw new Error('Invalid backup');state={...freshState(),...imported};if(fromOnboarding===true)state.onboardingComplete=true;prevSnap=null;rules={...structuredClone(DEFAULT_RULES),...(data.rules||state.customRules||{})};save();const more=lmaRestoreExtras(data.extras);toast('Backup imported');if(more||fromOnboarding===true)setTimeout(()=>location.reload(),500)}catch(err){alert('Could not import this file. Please choose a Life Max JSON backup.')}e.target.value=''};reader.readAsText(file)}
function resetDemo(){if(!confirm('Reset to a clean starter profile? This replaces the current local data. Export a backup first if needed.'))return;state=freshState();rules=structuredClone(DEFAULT_RULES);save();toast('Starter profile restored')}
function deleteAll(){if(!confirm('Delete all Life Max data saved in this browser? This cannot be undone. Export a backup first if needed.'))return;localStorage.removeItem(STORE);state=freshState();rules=structuredClone(DEFAULT_RULES);save();toast('Local data cleared')}
