function seedReadingList(){
 if(state.mediaSeededV1)return;
 if(!Array.isArray(state.mediaItems))state.mediaItems=[];
 const existing=new Set(state.mediaItems.map(x=>(x.title||'').trim().toLowerCase()));
 const additions=DEFAULT_READING_LIST.filter(x=>!existing.has(x.title.toLowerCase()));
 state.mediaItems.push(...additions);
 state.mediaSeededV1=true;
}
/* The save's format version. load() brings an older save up to it with migrateSave(). A save from a newer build (an APK or
   web update that was rolled back) is never migrated or written: this build shows it read-only until that build is back. */
const SAVE_SCHEMA=1;
/* Why the core save must not be written right now, in words for the owner ('' when it may be). writeSave() refuses while it is
   set and says so once; only a whole-save replacement the owner confirms (import, reset, delete all) clears it. */
let saveReadOnly='',saveReadOnlyTold=false;
function freshState(){return {schema:SAVE_SCHEMA,name:'The Life Architect',build:'The Life Architect',classId:null,activities:[],attrXP:Object.fromEntries(ATTRS.map(a=>[a.id,0])),skillXP:{},quests:JSON.parse(JSON.stringify(defaultQuests)),questDone:{},benchmarks:[],mediaItems:[],customRules:{},tutorialDone:false,tipsSeen:{},created:new Date().toISOString()}}
function setLightMode(enabled){document.documentElement.classList.toggle('light-theme',!!enabled);try{localStorage.setItem('lifeMaxTheme',enabled?'light':'dark')}catch(e){}const toggle=document.getElementById('lightModeToggle');if(toggle)toggle.checked=!!enabled;toast(enabled?'Light mode enabled':'Dark mode enabled')}
function initTheme(){let theme='dark';try{theme=localStorage.getItem('lifeMaxTheme')||'dark'}catch(e){}document.documentElement.classList.toggle('light-theme',theme==='light');const toggle=document.getElementById('lightModeToggle');if(toggle)toggle.checked=theme==='light'}
/* Whatever goes wrong, the stored save is never replaced here: a save this build cannot handle is shown read-only, and an unreadable
   one is copied under its own key before a starter profile is stored (if no copy fits, nothing is stored). */
function load(){
 let raw=null,saved=null;
 try{raw=localStorage.getItem(STORE);saved=raw&&JSON.parse(raw)}catch(e){console.warn('[load] save unreadable',e)}
 if(saved&&typeof saved==='object'){
  state={...freshState(),...saved,schema:Number(saved.schema)||0};state.attrXP={...freshState().attrXP,...(state.attrXP||{})};
  if(Array.isArray(state.activities)&&state.activities.length){try{rebuildAttrXP();rebuildSkillXP()}catch(e){}}
  rules={...structuredClone(DEFAULT_RULES),...(state.customRules||{})};
  const kept=' To keep it safe, this version shows it but saves nothing, so changes you make now are not kept. Install the latest update (Settings, App updates) to carry on.';
  if(state.schema>SAVE_SCHEMA){saveReadOnly='Your progress was last saved by a newer version of LifeMax.'+kept;return}
  try{migrateSave()}catch(e){console.warn('[load] migration failed',e);saveReadOnly='This version could not bring your save up to date.'+kept;return}
  writeSave();return;
 }
 if(raw){try{localStorage.setItem(STORE+'_unreadable_'+Date.now(),raw);setTimeout(()=>toast('Your save could not be read. A copy was kept on this device.'),2500)}catch(e){saveReadOnly='Your save could not be read and there is no room to keep a copy of it, so nothing is saved for now. Free up space and restart LifeMax, or import a backup (Settings, Data management).'}}
 state=freshState();rules=structuredClone(DEFAULT_RULES);seedReadingList();writeSave();
}
/* Brings the save up to SAVE_SCHEMA. A future format change goes here as `if(state.schema<2){...}` and stays additive until the
   build has reported healthy. The steps from before the schema number are idempotent flags and run on every load: the economy
   rework (xpSchemaV2), the reading list (mediaSeededV1) and the class quests (classContent). */
function migrateSave(){ecoMigrate();seedReadingList();applyClassContent();state.schema=SAVE_SCHEMA}
/* The one writer of the core save; answers whether it was stored. */
function writeSave(){
 if(saveReadOnly){if(!saveReadOnlyTold){saveReadOnlyTold=true;const why=saveReadOnly;setTimeout(()=>alert(why),2500)}return false}
 try{localStorage.setItem(STORE,JSON.stringify(state));return true}catch(e){console.warn('[save] not stored',e);return false}
}
function save(){rebuildAttrXP();rebuildSkillXP();try{ecoMigrate();ecoSparksSync()}catch(e){console.warn('[economy migration]',e)}state.customRules=rules;checkBadges();checkLevelUps();const saved=writeSave();if(!saved&&!saveReadOnly)toast("Could not save to this browser (storage full?)");renderAll();return saved}
function xpForLevel(level){return 100+25*(level-1)}
function levelData(total,fn){fn=fn||xpForLevel;let level=1,left=Math.max(0,total);while(left>=fn(level)&&level<1000){left-=fn(level);level++}return {level,into:left,needed:fn(level),pct:Math.min(100,left/fn(level)*100)}}
function totalXP(){return Object.values(state.attrXP).reduce((a,b)=>a+b,0)}
function characterLevel(){return levelData(totalXP()+(state.legacyCharXPBonus||0)+Object.values(state.ascended||{}).reduce((a,b)=>a+b,0),charXpForLevel)}
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
  const asc=state.ascended||{};ATTRS.forEach(a=>{next[a.id]=Math.max(0,(next[a.id]||0)-Math.max(0,Number(decay[a.id])||0)-Math.max(0,Number(asc[a.id])||0))});
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
  if(window.LifeMaxCoach)LifeMaxCoach.reset();
  if(!histSel||(!days.includes(histSel)&&!/^[WM]:/.test(histSel)))histSel=days[0]||getToday();
  const daysEl=document.getElementById('journalDays');
  const entryEl=document.getElementById('journalEntry');
  if(!daysEl||!entryEl)return;
  const hero=document.getElementById('journalHero');if(hero&&window.LifeMaxCoach)hero.innerHTML=jcHeroHtml();
  if(!days.length){
    daysEl.innerHTML='';
    entryEl.innerHTML=`<div class="empty"><div>◷</div><strong>No journal entries yet</strong><br>Log an activity, complete a quest, or finish a plan session. Everything you enter will show up here by day.</div>`;
    return;
  }
  daysEl.innerHTML=window.LifeMaxCoach?jcListHtml(days,histSel):days.map(d=>{
    const acts=actsForDay(d);
    const hasRef=!!(state.dayReflections[d]||'').trim();
    const label=journalDayLabel(d);
    return `<button type="button" class="journal-day-btn ${d===histSel?'on':''}" onclick="selectJournalDay('${d}')">${esc(label)}<small>${acts.length} logged${hasRef?' · reflection':''}</small></button>`;
  }).join('');
  entryEl.innerHTML=/^[WM]:/.test(histSel)&&window.LifeMaxCoach?jcReviewHtml(histSel):renderJournalDay(histSel);
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
  writeSave();
  try{if(window.LifeMaxCoach)jcOnReflect(d,t)}catch(e){console.warn('[coach]',e)}
  // soft refresh day list badges without losing textarea focus
  const daysEl=document.getElementById('journalDays');
  if(daysEl){
    if(window.LifeMaxCoach)LifeMaxCoach.reset();
    const days=journalDays();
    daysEl.innerHTML=window.LifeMaxCoach?jcListHtml(days,histSel):days.map(day=>{
      const acts=actsForDay(day);
      const hasRef=!!(state.dayReflections[day]||'').trim();
      return `<button type="button" class="journal-day-btn ${day===histSel?'on':''}" onclick="selectJournalDay('${day}')">${esc(journalDayLabel(day))}<small>${acts.length} logged${hasRef?' · reflection':''}</small></button>`;
    }).join('');
  }
  const cnt=document.getElementById('jcCount');if(cnt&&window.LifeMaxCoach){const ta=document.getElementById('dayReflection');if(ta)jcCounter(ta,d)}
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
  const col=a=>window.LifeMaxCoach?jcAttrColor(a.primary):'var(--accent)';
  const planBlock=l=>`<div class="j-plan-logs">${l.map(x=>`<div><b>${esc(x.label)}</b>${x.weight?' · '+esc(x.weight):''}${x.note?' — '+esc(x.note):''}</div>`).join('')}</div>`;
  let body='';
  if(!acts.length&&!logs.length){
    body=`<div class="jx-empty"><div class="jx-empty-ico">✎</div><strong>Nothing logged this day yet</strong><span>${d===getToday()?'Start with an activity, quest, or plan session and it will land here.':'A blank page is still a page.'}</span></div>`;
  }else{
    body='<div class="jx-timeline">'+acts.map((a,i)=>{
      const attr=(ATTRS.find(x=>x.id===a.primary)||{}).short||'';
      const dt=new Date(a.date),time=dt.toLocaleTimeString([], {hour:'numeric',minute:'2-digit'});
      const notes=(a.notes||'').trim();
      let extra='';
      if(a.type==='plan'&&logs.length)extra=planBlock(logs);
      return `<div class="jx-item" style="--c:${col(a)};--i:${i}"><div class="jx-time">${esc(time)}</div><div class="jx-node"></div><div class="jx-card"><div class="jx-card-top"><div class="acticon">${iconHtml(a.icon,18)}</div><div class="jx-card-main"><strong>${esc(a.name)}</strong><div class="jx-sub">${attr?`<span class="jx-pill" style="--c:${col(a)}">${esc(attr)}</span>`:''}${a.duration?`<span>${a.duration} min</span>`:''}${a.skill&&a.skill!==a.name?`<span>${esc(a.skill)}</span>`:''}</div></div><div class="jx-xpv">+${a.xp||0}<small>XP</small></div></div>${notes?`<div class="j-notes">${esc(notes)}</div>`:''}${extra}</div></div>`;
    }).join('')+'</div>';
    if(logs.length&&!acts.some(a=>a.type==='plan')){
      body+=`<div class="jx-card jx-plan"><div class="jx-card-top"><div class="acticon">${px('dumbbell',18)}</div><div class="jx-card-main"><strong>Workout notes</strong></div></div>${planBlock(logs)}</div>`;
    }
  }
  let dtHtml;
  try{
    const dt=new Date(d+'T12:00:00');
    dtHtml=`<div class="jx-dayhead"><div class="jx-bignum">${dt.getDate()}</div><div class="jx-dayname"><b>${esc(dt.toLocaleDateString(undefined,{weekday:'long'}))}</b><span>${esc(dt.toLocaleDateString(undefined,{month:'long',year:'numeric'}))}</span></div><div class="jx-dayxp"><b>${totalXP}</b><small>XP today</small></div></div>`;
  }catch(e){dtHtml=`<div class="journal-date">${esc(journalDayTitle(d))}</div>`}
  const meta=`<div class="jx-meta"><span>${acts.length} entr${acts.length===1?'y':'ies'}</span>${logs.length?`<span>${logs.length} exercise note${logs.length===1?'':'s'}</span>`:''}</div>`;
  return `${dtHtml}${meta}${window.LifeMaxCoach?jcTop(d):''}${body}${window.LifeMaxCoach?'':`<div class="j-reflect"><label for="dayReflection">Reflection <span style="font-weight:400;color:var(--muted2)">(optional)</span></label>
    <textarea id="dayReflection" maxlength="4000" placeholder="How did today feel? What mattered? What will you do differently?" onchange="saveDayReflection('${d}',this.value)">${esc(ref)}</textarea></div>`}`;
}
function renderStats(){const lvl=characterLevel();document.getElementById('heroName').textContent=state.name||'The Life Architect';document.getElementById('heroLevel').textContent=lvl.level;document.getElementById('heroXP').textContent=totalXP().toLocaleString()+' XP';document.getElementById('heroBar').style.width=lvl.pct+'%';document.getElementById('heroProgress').textContent=`${lvl.into} / ${lvl.needed} XP to next level`;document.getElementById('heroPercent').textContent=Math.round(lvl.pct)+'%';document.getElementById('achievementCount').textContent=Object.keys(state.questDone).length;document.getElementById('totalActivities').textContent=state.activities.length;document.getElementById('todayXP').textContent=todayActivities().reduce((s,a)=>s+a.xp,0)+' XP earned';document.getElementById('totalXPStat').textContent=totalXP().toLocaleString();let strongest=ATTRS.slice().sort((a,b)=>attrXP(b.id)-attrXP(a.id))[0];document.getElementById('strongestStat').textContent=attrXP(strongest.id)>0?strongest.short:'—';document.getElementById('skillsStat').textContent=Object.values(state.skillXP).filter(v=>v>0).length;document.getElementById('questsStat').textContent=Object.keys(state.questDone).length;let tq=todayActivities().length;let qd=state.quests.filter(q=>q.cadence==='daily'&&isQuestDone(q)).length;document.getElementById('dailyMini').innerHTML=`<div class="minirow"><span>Activities logged</span><strong>${tq}</strong></div><div class="minirow"><span>Quests completed</span><strong>${qd}</strong></div><div class="minirow"><span>Active skills</span><strong>${Object.values(state.skillXP).filter(v=>v>0).length}</strong></div>`;document.getElementById('todayLabel').textContent=new Date().toLocaleDateString(undefined,{weekday:'long',month:'long',day:'numeric'}).toUpperCase()}
function renderRules(){document.getElementById('xpRules').innerHTML=Object.entries(rules).map(([id,r])=>`<div class="settingsline"><div style="flex:1"><strong>${iconHtml(r.icon)} ${esc(r.name)}</strong><div class="muted" style="font-size:11px">${esc(r.skill)} · base XP</div></div><input aria-label="${esc(r.name)} base XP" id="rule-${id}" type="number" min="1" max="200" value="${r.base}" style="width:78px;background:#0b1223;color:var(--text);border:1px solid var(--line);padding:8px;border-radius:9px"></div>`).join('')}
function renderAll(){renderStats();renderExtras();renderAttrs();renderActivities();renderSkills();renderBenchmarks();renderQuests();renderHistory();renderRules();renderMedia();renderMeals();if(document.getElementById('characterName'))document.getElementById('characterName').value=state.name;if(document.getElementById('characterBuild'))document.getElementById('characterBuild').value=state.build;if(document.getElementById('characterWeight'))document.getElementById('characterWeight').value=state.weight||'';if(document.getElementById('characterHeightFt'))document.getElementById('characterHeightFt').value=state.heightFt||'';if(document.getElementById('characterHeightIn'))document.getElementById('characterHeightIn').value=state.heightIn||'';renderCharacterIntro();populateAttrSelects();renderRecipes()}
function populateAttrSelects(){const p=document.getElementById('primaryAttr'),s=document.getElementById('secondaryAttr');if(!p)return;const opts=ATTRS.map(a=>`<option value="${a.id}">${a.name}</option>`).join('');let pv=p.value,sv=s.value;p.innerHTML=opts;s.innerHTML='<option value="">No secondary attribute</option>'+opts;p.value=pv||'intelligence';s.value=sv||''}
function activityPresetChanged(){const id=document.getElementById('activityType').value,r=rules[id]||rules.custom;document.getElementById('customNameWrap').style.display=id==='custom'?'block':'none';document.getElementById('skillName').value=r.skill;document.getElementById('primaryAttr').value=r.primary;document.getElementById('secondaryAttr').value=r.secondary||'';updatePreview()}
/* The XP form as data: the page form and the native Log screen both build one of these and hand it to the same owner functions below. */
function activityDraftFromForm(){const v=id=>document.getElementById(id).value;return {type:v('activityType'),customName:v('customName'),duration:v('duration'),quality:v('quality'),skill:v('skillName'),primary:v('primaryAttr'),secondary:v('secondaryAttr'),notes:v('activityNotes')}}
function activityPreview(d){const r=rules[d.type]||rules.custom;const dur=Math.max(0,Number(d.duration)||0);const quality=Number(d.quality)||1;/* 30 min = 1× base; scales linearly to 3× at 90+ min; short sessions floor at 0.4× */const durFactor=ecoDur(dur);const q=dur<20?Math.min(1,quality):quality;let base=r.base*durFactor;let total=Math.max(0,Math.round(base*q));return {total,primary:d.primary||r.primary,secondary:d.secondary||'',skill:(d.skill||r.skill).trim()||r.skill,durFactor}}
function calcPreview(){return activityPreview(activityDraftFromForm())}
function updatePreview(){const x=calcPreview();document.getElementById('previewXP').textContent=x.total;document.getElementById('previewBar').style.width=Math.min(100,x.total/120*100)+'%';const p=ATTRS.find(a=>a.id===x.primary);const s=ATTRS.find(a=>a.id===x.secondary);let chunks=[];if(x.secondary&&x.secondary!==x.primary){const pXP=Math.round(x.total*.7),sXP=x.total-pXP;chunks.push(`<span class="preview-chip">${p?px(p.icon):''} ${p?.short||'Primary'} <strong>+${pXP}</strong></span>`,`<span class="preview-chip">${s?px(s.icon):''} ${s?.short||'Secondary'} <strong>+${sXP}</strong></span>`)}else chunks.push(`<span class="preview-chip">${p?px(p.icon):''} ${p?.short||'Attribute'} <strong>+${x.total}</strong></span>`);if(x.durFactor)chunks.push(`<span class="preview-chip">Duration <strong>${x.durFactor.toFixed(2)}×</strong></span>`);chunks.push(`<span class="preview-chip">Skill: <strong>${esc(x.skill||'Skill')}</strong></span>`);document.getElementById('previewSplit').innerHTML=chunks.join('')}
/* Award and save one logged activity. Returns {ok, xp, message}; it never touches the page. The form handler and the native Log screen both call it. */
function recordActivity(d){if(d.opId){const done=state.activities.find(a=>a.opId===d.opId);if(done)return {ok:true,xp:done.xp,message:`Activity saved · +${done.xp} XP`,duplicate:true}}const id=d.type,r=rules[id]||rules.custom,x=activityPreview(d);if(!x.total)return {ok:false,message:'Log at least 5 minutes to earn XP'};const duration=Number(d.duration)||0;const ec=ecoAward(id,x.total,duration,{manual:true});if(ec.xp<=0)return {ok:false,message:ec.msg};const xp=ec.xp;const p=x.primary,s=x.secondary;let pXP=xp, sXP=0;if(s&&s!==p){pXP=Math.round(xp*.7);sXP=xp-pXP}pXP=ecoStarXP(p,pXP);if(s&&s!==p)sXP=ecoStarXP(s,sXP);const xpTot=pXP+sXP;const name=id==='custom'?(String(d.customName||'').trim()||'Custom activity'):r.name;const activity={id:crypto.randomUUID?crypto.randomUUID():String(Date.now()),...(d.opId?{opId:String(d.opId)}:{}),type:id,name,icon:r.icon,duration,quality:Number(d.quality),skill:x.skill,primary:p,secondary:s,xp:xpTot,date:new Date().toISOString(),notes:String(d.notes||'').trim(),xpSplit:[{attr:p,xp:pXP},...(s&&s!==p?[{attr:s,xp:sXP}]:[])]};state.activities.push(activity);activity.xpSplit.forEach(z=>state.attrXP[z.attr]=(state.attrXP[z.attr]||0)+z.xp);state.skillXP[x.skill]=(state.skillXP[x.skill]||0)+xp;if(!save()){const i=state.activities.indexOf(activity);if(i>=0)state.activities.splice(i,1);rebuildAttrXP();rebuildSkillXP();renderAll();return {ok:false,message:saveReadOnly?'Not saved: this version is read-only for your save':'Could not save to this browser (storage full?)'}}return {ok:true,xp,message:`Activity saved · +${xp} XP`}}
function submitActivity(e){e.preventDefault();const res=recordActivity(activityDraftFromForm());if(!res.ok){toast(res.message);return}document.getElementById('activityNotes').value='';document.getElementById('duration').value=30;document.getElementById('quality').value='1';toast(res.message);navigate('dashboard')}
function toggleQuest(id,checked){const q=state.quests.find(q=>q.id===id);if(!q)return;const A=q.attribute||'discipline';if(checked&&questLocked(q)){toast('Locked until you reach level '+stageLvl(q.stage));renderQuests();return}const key=questKey(q);if(checked&&!state.questDone[key]){const qid='quest_'+Date.now();state.questDone[key]=qid;const xp=ecoQuestXP(q);if(!xp&&(Number(q.xp)||0)>0)toast(q.cadence==='epic'?'One epic quest reward per day':'Quest XP limit reached for '+(q.cadence==='weekly'?'this week':'today'));const noteEl=document.getElementById('qnote-'+id);const qnote=(noteEl&&noteEl.value.trim())||'';state.attrXP[A]=(state.attrXP[A]||0)+xp;state.skillXP['Quest Completion']=(state.skillXP['Quest Completion']||0)+xp;state.activities.push({id:qid,cad:q.cadence,epic:q.cadence==='epic',skill:'Quest Completion',type:'quest',name:'Quest: '+q.name,icon:'flag',duration:0,xp,primary:A,date:new Date().toISOString(),notes:qnote||'Quest completed',xpSplit:[{attr:A,xp}]});if(xp>0)toast(`Quest complete · +${xp} XP`)}else if(!checked&&state.questDone[key]){const aid=state.questDone[key],xp=Math.max(0,Number(q.xp)||0);delete state.questDone[key];state.attrXP[A]=Math.max(0,(state.attrXP[A]||0)-xp);state.skillXP['Quest Completion']=Math.max(0,(state.skillXP['Quest Completion']||0)-xp);state.activities=state.activities.filter(a=>a.id!==aid);toast('Quest undone · XP removed')}save()}
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
  state.customRules=rules;writeSave();
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
/* What a backup file holds. While the save is read-only that is the save as stored, not what is on screen. */
function backupData(){return {app:'Life Max RPG Prototype',version:1,exportedAt:new Date().toISOString(),state:saveReadOnly?JSON.parse(localStorage.getItem(STORE)):state,rules,extras:lmaBackupExtras()}}
function backupJson(){return JSON.stringify(backupData(),null,2)}
function exportData(){lmaDownload(backupJson(),'life-max-backup-'+getToday()+'.json','application/json','Backup')}
/* The save inside a parsed backup file (or a bare save), or null when it is not one. */
function backupSave(data){const s=data&&typeof data==='object'?(data.state||data):null;return s&&Array.isArray(s.activities)&&s.attrXP&&typeof s.attrXP==='object'?s:null}
/* The facts a backup must keep, compared between the file as written and as read back (ADR 0004). */
function backupFacts(data,size){const s=backupSave(data)||{};return {schema:s.schema,exportedAt:data&&data.exportedAt,activities:(s.activities||[]).length,rules:Object.keys(data&&data.rules||{}).length,extras:Object.keys(data&&data.extras||{}).length,size}}
/* Why a backup read back cannot be trusted ('is cut short …', 'has activities 3 instead of 4'), or '' when it can: it must be a backup
   the import accepts, with the schema, counts and size of the file that was written. */
function backupProblem(text,want){let data=null;try{data=JSON.parse(text)}catch(e){}if(!backupSave(data))return typeof text==='string'&&text.length<want.size?'is cut short ('+text.length+' of '+want.size+' characters)':'is not a readable backup';const got=backupFacts(data,text.length),k=Object.keys(want).find(k=>got[k]!==want[k]);return k?'has '+k+' '+got[k]+' instead of '+want[k]:''}
/* Before anything replaces the whole save, the current one is kept under STORE+'_safety' with the reason and time. If that copy cannot be written, nothing is replaced. */
function keepSafetyCopy(reason){try{const raw=localStorage.getItem(STORE)||JSON.stringify(state);localStorage.setItem(STORE+'_safety',JSON.stringify({reason,at:new Date().toISOString(),save:raw}));return true}catch(e){console.warn('[safety copy]',e);return false}}
/* Apply a parsed backup file. Resolves {ok, message}. Refuses a backup made by a newer build, asks before replacing a save that has
   activities, takes a verified folder backup and keeps a safety copy first, and brings an older backup up to SAVE_SCHEMA the way
   load() does. All or nothing: when the new save cannot be stored, the stored one is loaded back. */
async function applyBackup(data,fromOnboarding){const imported=backupSave(data);if(!imported)return {ok:false,message:'Could not import this file. Please choose a Life Max JSON backup.'};const schema=Number(imported.schema)||0;if(schema>SAVE_SCHEMA)return {ok:false,message:'This backup was made by a newer version of LifeMax. Install the latest update, then import it again.'};const cur=(state.activities||[]).length;if(cur>0&&!confirm('Replace your current data ('+cur+' activities) with this backup ('+imported.activities.length+' activities)? Your current data is kept as a safety copy on this device.'))return {ok:false,message:'Import cancelled',cancelled:true};if(!await lmaBackupFirst('import'))return {ok:false,message:'Import cancelled',cancelled:true};if(!keepSafetyCopy('import'))return {ok:false,message:'Could not keep a safety copy of your current data, so nothing was imported.'};state={...freshState(),...imported,schema};if(fromOnboarding===true)state.onboardingComplete=true;prevSnap=null;rules={...structuredClone(DEFAULT_RULES),...(data.rules||state.customRules||{})};try{migrateSave()}catch(e){console.warn('[import] migration failed',e);load();return {ok:false,message:'This backup could not be brought up to date, so nothing was imported.'}}saveReadOnly='';if(!save()){load();renderAll();return {ok:false,message:'Could not save the imported data (storage full?), so nothing was imported.'}}const more=lmaRestoreExtras(data.extras);return {ok:true,message:'Backup imported',reload:!!more||fromOnboarding===true}}
function importData(e,fromOnboarding){const file=e.target.files[0];if(!file)return;const reader=new FileReader();reader.onload=async()=>{let data=null;try{data=JSON.parse(reader.result)}catch(err){}const r=await applyBackup(data,fromOnboarding);if(r.ok){toast(r.message);if(r.reload)setTimeout(()=>location.reload(),500)}else if(!r.cancelled)alert(r.message);e.target.value=''};reader.readAsText(file)}
async function resetDemo(){if(!confirm('Reset to a clean starter profile? This replaces the current local data. Export a backup first if needed.'))return;if(!await lmaBackupFirst('reset'))return;if(!keepSafetyCopy('reset')){alert('Could not keep a safety copy of your current data, so nothing was reset.');return}state=freshState();rules=structuredClone(DEFAULT_RULES);saveReadOnly='';if(!save()){load();renderAll();alert('Could not save the starter profile (storage full?), so nothing was reset.');return}toast('Starter profile restored')}
function deleteAll(){if(!confirm('Delete all Life Max data saved in this browser? This cannot be undone. Export a backup first if needed.'))return;Object.keys(localStorage).filter(k=>k===STORE||k.startsWith(STORE+'_')).forEach(k=>localStorage.removeItem(k));state=freshState();rules=structuredClone(DEFAULT_RULES);saveReadOnly='';save();toast('Local data cleared')}
