eaderMarkup(g,subtitle){
  const d=planDay(g.date);
  return `<div class="gwr-crumbs"><span>TRAINING PROTOCOL</span><span>/</span><span>WEEK ${String(d?.week||1).padStart(2,'0')}</span><span>/</span><span class="active">${esc(subtitle)}</span></div>`;
}
function gwrProgressMarkup(g){
  const s=gwrStats(g);
  return `<div class="gwr-progress-meta"><small>PROGRAM PROGRESS</small><strong>${s.doneReq.length} / ${s.needed.length} SETS</strong></div><div class="gwr-meter" aria-label="Required sets completed: ${s.pct}%"><i style="width:${s.pct}%"></i></div>`;
}
function gwrVisualMarkup(it,d,g){
  const s=gwrStats(g);
  return `<div class="gwr-panel gwr-visual-panel"><div class="gwr-visual-header"><span class="gwr-visual-type">${esc(d.kind.toUpperCase())} // LIVE GUIDE</span><span class="gwr-visual-count">W${String(d.week).padStart(2,'0')} · ${esc(d.short)}</span></div><div class="gwr-pixel-stage">${gwrArt(it,d.kind)}</div><div class="gwr-visual-footer"><div class="gwr-visual-foot"><small>SETS LOGGED</small><strong>${s.completed.length}</strong></div><div class="gwr-visual-foot"><small>ELAPSED</small><strong data-gwr-elapsed>${gwrFmtDuration(gwrElapsed(g))}</strong></div><div class="gwr-visual-foot"><small>VOLUME LB</small><strong>${s.volume.toLocaleString()}</strong></div></div></div>`;
}
function gwrQueueMarkup(g,current){
  const d=planDay(g.date),c=state.plan.checks[g.date]||{};
  const rows=d.items.map((it,index)=>{
    const a=(c.s||{})[it.k]||[],done=a.filter(Boolean).length;
    const skipped=g.skip?.[it.k];
    const here=current&&it.k===current.it.k;
    const status=skipped?'SKIPPED':done>=it.n?'DONE':`${done}/${it.n}`;
    return `<div class="gwr-queue-row"><div class="gwr-queue-num ${done>=it.n?'done':here?'now':''}">${String(index+1).padStart(2,'0')}</div><div class="gwr-queue-name">${esc(c.substitute?.[it.k]||it.l)}</div><div class="gwr-queue-detail">${status}</div></div>`;
  }).join('');
  return `<div class="gwr-panel gwr-sequence"><div class="gwr-sequence-top"><strong>Mission sequence</strong><span>${d.items.length} EXERCISES</span></div>${rows}</div>`;
}
function gwrRenderIntro(g,d){
  const total=d.items.reduce((sum,it)=>sum+it.n,0),core=d.items.filter(it=>!it.opt).reduce((sum,it)=>sum+it.n,0),name=d.label;
  const c=state.plan.checks[g.date]||{},done=gwrStats(g);
  return `<div class="gwr-body">${gwrHeaderMarkup(g,'MISSION BRIEFING')}<div class="gwr-kicker">MISSION LOADED // ${(g.date===getToday()?'TODAY':'SELECTED DATE')}</div><h1 class="gwr-headline">${esc(name)}</h1><p class="gwr-subtitle">Your whole workout, in one guided flow. We'll cue each movement, capture every real set, run your rest periods, and keep your plan and XP synchronized.</p><div class="gwr-dashboard"><div>${gwrVisualMarkup(d.items[0],d,g)}${gwrQueueMarkup(g,null)}</div><div><div class="gwr-panel gwr-action-panel">${gwrProgressMarkup(g)}<div class="gwr-current-marker">SYSTEM READY</div><h2 class="gwr-ex-name">Your session is queued.</h2><p class="gwr-prescription">${esc(parseD(g.date).toLocaleDateString(undefined,{weekday:'long',month:'long',day:'numeric'}))}</p><div class="gwr-intro-table"><div class="gwr-intro-stat"><small>EXERCISES</small><strong>${d.items.length}</strong></div><div class="gwr-intro-stat"><small>SETS</small><strong>${total}</strong></div><div class="gwr-intro-stat"><small>SESSION XP</small><strong>+${d.xp}</strong></div></div><div class="gwr-target-callout"><div><small>ALREADY COMPLETED</small><b>${done.doneReq.length} of ${core} required sets</b></div><span class="gwr-chip">AUTO SYNC</span></div><div class="gwr-current-marker">CHOOSE TODAY'S WORKOUT</div><p class="gwr-prescription">Pick an available training day from this week, or keep the selected session.</p><div class="gwr-pick-grid">${gwrPickMarkup(g)}</div><button class="gwr-primary" onclick="gwrBegin()">${px('bolt',19)} ${done.doneReq.length>=core?'View finished session':'Start guided workout'}</button><div class="gwr-work-note">${px('shield',17)}<span>Only confirm sets you actually complete. Your training program decides the rep targets and the workout XP; this interface does not award bonus XP for tapping through.</span></div></div></div></div></div>`;
}
function gwrRenderActive(g,d,st){
  const c=state.plan.checks[g.date]||{},info=gwrNumTarget(st.it),label=c.substitute?.[st.it.k]||st.it.l;
  const arr=(c.s||{})[st.it.k]||[];
  const prev=lastVal(st.it.k,g.date)||'';
  const earlier=(c.setDetails?.[st.it.k]||[]).filter(Boolean);
  const previousEntry=earlier.length?earlier[earlier.length-1]:null;
  const prevLabel=previousEntry?`Earlier today: ${previousEntry.reps?previousEntry.reps+' reps':previousEntry.minutes?previousEntry.minutes+' minutes':previousEntry.seconds?previousEntry.seconds+' seconds':'completed'}${previousEntry.weight!==undefined?' · '+previousEntry.weight+' lb':''}`:prev?`Last time: ${esc(prev)}`:'No previous data — start with a manageable effort.';
  const dotHtml=Array.from({length:st.it.n},(_,j)=>`<span class="gwr-set-dot ${arr[j]?'done':j===st.j?'now':''}">${arr[j]?'✓':j+1}</span>`).join('');
  let fields='';
  if(info.kind!=='check')fields+=`<div class="gwr-input-wrap"><label for="gwrActualValue">${esc(info.label)}</label><input id="gwrActualValue" type="number" min="1" step="${info.kind==='reps'?1:'.5'}" inputmode="decimal" placeholder="${info.suggest||'—'}" value="${info.suggest||''}" autocomplete="off"></div>`;
  if(st.it.wt){fields+=`<div class="gwr-input-wrap"><label for="gwrActualWeight">Weight · lb</label><input id="gwrActualWeight" type="number" min="0" max="2000" step="2.5" inputmode="decimal" placeholder="Optional" autocomplete="off"></div>`}
  if(!fields)fields=`<div class="gwr-target-callout"><div><small>SESSION STEP</small><b>Finish the movement as prescribed, then confirm.</b></div><span class="gwr-chip">NO WEIGHT REQUIRED</span></div>`;
  return `<div class="gwr-body">${gwrHeaderMarkup(g,'ACTIVE SESSION')}<div class="gwr-dashboard"><div>${gwrVisualMarkup(st.it,d,g)}${gwrQueueMarkup(g,st)}</div><div><div class="gwr-panel gwr-action-panel">${gwrProgressMarkup(g)}<div class="gwr-current-marker">EXERCISE ${String(st.ex+1).padStart(2,'0')} / ${String(d.items.length).padStart(2,'0')} · SET ${st.j+1} / ${st.it.n}</div><h1 class="gwr-ex-name">${esc(label)}</h1><p class="gwr-prescription">${esc(st.it.d)} ${st.it.opt?' · OPTIONAL':''}</p><div class="gwr-set-dots">${dotHtml}</div><div class="gwr-target-callout"><div><small>YOUR PRESCRIPTION</small><b>${esc(st.it.d)}</b></div><span class="gwr-chip">SET ${st.j+1} / ${st.it.n}</span></div><div class="gwr-fields ${info.kind==='check'&&!st.it.wt?'cols-one':''}">${fields}</div><div class="gwr-last">${esc(prevLabel)}</div><div class="gwr-rpe-label">HOW HARD WAS IT? · OPTIONAL</div><div class="gwr-rpe-row">${[6,7,8,9,10].map(i=>`<button type="button" class="gwr-rpe-button" data-rpe="${i}" aria-pressed="false" onclick="gwrPickRpe(${i})">RPE ${i}</button>`).join('')}</div><button class="gwr-primary" onclick="gwrLogSet()">${px('check',18)} ${info.kind==='check'?'Mark step complete':'Log completed set'}</button><div class="gwr-actions-row"><button class="gwr-secondary" onclick="gwrSubstituteExercise()">${px('shuffle',15)} Substitute</button><button class="gwr-secondary" onclick="gwrSkipExercise()">${st.it.opt?'Skip optional':'Skip exercise'}</button></div><div class="gwr-instruction"><b>Training cue:</b> ${esc(gwrCue(st.it,d))}<br><b>Session guide:</b> ${esc(d.note||'Use controlled, comfortable movement and rest when needed.')}</div></div></div></div></div>`;
}
function gwrCue(it,d){
  const name=(it.l||'').toLowerCase();
  if(/warm-up/i.test(name))return 'Start easy and progressively increase effort. Treat preparatory sets as practice, not a maximum-effort test.';
  if(/cool-down/i.test(name))return 'Bring your breathing and pace down gradually. Comfortable mobility only.';
  if(/leg press/i.test(name))return 'Adjust the seat so your back stays supported. Move through a comfortable range without locking your knees forcefully.';
  if(/squat|lunge/i.test(name))return 'Use a controlled pace, keep balance, and stop the range before technique breaks down.';
  if(/press/i.test(name))return 'Set the machine to fit your body. Maintain steady control through the pressing range.';
  if(/pulldown|row|pull-up/i.test(name))return 'Keep your torso stable and move smoothly. Avoid jerking the weight to finish repetitions.';
  if(/cardio|walk|run|bike|treadmill|elliptical/i.test(name))return 'Stay at the prescribed effort. You should be able to reduce pace when needed without feeling pressured.';
  if(/stretch|mobility|recovery/i.test(name))return 'Move gently. Stretching should feel comfortable, not sharp or painful.';
  return 'Use a manageable effort and controlled technique. Stop if you feel sharp or unusual pain.';
}
function gwrRenderRest(g,d){
  const upcoming=gwrStepNext(g),restLeft=Math.max(0,Math.ceil((g.restUntil-Date.now())/1000));
  if(!upcoming){gwrComplete();return ''}
  return `<div class="gwr-body">${gwrHeaderMarkup(g,'RECOVERY WINDOW')}<div class="gwr-dashboard"><div>${gwrVisualMarkup(upcoming.it,d,g)}${gwrQueueMarkup(g,upcoming)}</div><div><div class="gwr-panel gwr-action-panel">${gwrProgressMarkup(g)}<div class="gwr-current-marker">SET RECORDED / REST MODE</div><h1 class="gwr-ex-name">Take your recovery.</h1><p class="gwr-prescription">Next up: ${esc(upcoming.it.l)} · Set ${upcoming.j+1} of ${upcoming.it.n}</p><div class="gwr-rest-ring"><svg viewBox="0 0 180 180" aria-hidden="true"><circle class="gwr-rest-track" cx="90" cy="90" r="78" fill="none" stroke-width="9"/><circle data-gwr-rest-progress class="gwr-rest-fill" cx="90" cy="90" r="78" fill="none" stroke-width="9" stroke-linecap="round" stroke-dasharray="490.09" stroke-dashoffset="${490.09*(1-restLeft/Math.max(1,g.restLength))}"/></svg><div class="gwr-rest-number"><span data-gwr-rest>${String(Math.floor(restLeft/60)).padStart(2,'0')}:${String(restLeft%60).padStart(2,'0')}</span><small>REST REMAINING</small></div></div><p class="gwr-rest-help">Your last set is saved to the same checklist as the original plan. Recover your breathing before the next effort.</p><div class="gwr-rest-tip"><strong>Up next:</strong> ${esc(upcoming.it.l)}<br>${esc(upcoming.it.d)}</div><div class="gwr-actions-row"><button class="gwr-secondary" onclick="gwrAdjustRest(-15)">− 15 sec</button><button class="gwr-secondary" onclick="gwrAdjustRest(15)">+ 15 sec</button></div><button class="gwr-primary" onclick="gwrRestDone()">${px('bolt',18)} Ready for next set</button><div class="gwr-actions-row"><button class="gwr-secondary" onclick="gwrUndoLast()">Undo last set</button><button class="gwr-secondary" onclick="gwrPause()">Pause session</button></div></div></div></div></div>`;
}
function gwrRenderComplete(g,d){
  const s=gwrStats(g),c=state.plan.checks[g.date]||{},act=c.actId&&state.activities.find(a=>a.id===c.actId);
  const xp=act?.xp||0,all=s.doneReq.length===s.needed.length;
  const name=all?'Mission accomplished.':'Progress safely recorded.';
  return `<div class="gwr-body">${gwrHeaderMarkup(g,'MISSION REPORT')}<div class="gwr-complete-symbol">${px(all?'check':'shield',45)}</div><div class="gwr-kicker">${all?'FULL SESSION COMPLETE':'PARTIAL SESSION'}</div><h1 class="gwr-headline">${name}</h1><p class="gwr-subtitle">${all?'Every required set is checked off in your existing plan. Your XP was awarded exactly once by LifeMax’s normal plan rules.':'Everything you actually completed is saved. Unfinished required exercises remain unchecked; no completion XP was awarded for this partial workout.'}</p><div class="gwr-result-stats"><div class="gwr-result-stat"><small>SETS COMPLETED</small><strong>${s.completed.length}</strong></div><div class="gwr-result-stat"><small>SESSION TIME</small><strong>${gwrFmtDuration(gwrElapsed(g))}</strong></div><div class="gwr-result-stat"><small>VOLUME LB</small><strong>${s.volume.toLocaleString()}</strong></div><div class="gwr-result-stat"><small>XP EARNED</small><strong>+${xp}</strong></div></div><div class="gwr-dashboard"><div>${gwrVisualMarkup(d.items[0],d,g)}</div><div><div class="gwr-panel gwr-action-panel">${gwrProgressMarkup(g)}<div class="gwr-current-marker">FINAL CHECKPOINT</div><h2 class="gwr-ex-name">${esc(d.label)}</h2><p class="gwr-prescription">${s.doneReq.length} / ${s.needed.length} required sets completed · ${d.items.length} exercises</p><div class="gwr-target-callout"><div><small>SYNC STATUS</small><b>Saved to Plan + Session History</b></div><span class="gwr-chip">LOCAL DEVICE</span></div><button class="gwr-primary" onclick="gwrExitComplete()">${px('check',18)} Return to workout plan</button>${!all?'<button class="gwr-secondary" style="width:100%;margin-top:10px" onclick="gwrReopenPartial()">Return to remaining exercises</button>':''}<div class="gwr-work-note">${px('shield',17)}<span>XP follows LifeMax's existing plan completion rules. Logged weights and repetitions are user-entered; the app cannot independently verify them.</span></div></div></div></div></div>`;
}
/* ── v74 guided workout visual overhaul (Claude). Same state machine, same handlers, same tick hooks; new markup + 05-training.css. ── */
function gwrV60TimerRing(type,remaining,max){
 const left=Math.max(0,remaining),total=Math.max(1,max),ratio=Math.min(1,left/total),dash=452.39;
 const attr=type==='work'?'data-gwr-timer-ring':'data-gwr-rest-progress';
 return `<div class="gx-ring ${type==='work'?'is-work':'is-rest'}"><svg viewBox="0 0 170 170" aria-hidden="true"><circle class="gx-ring-track" cx="85" cy="85" r="72" fill="none" stroke-width="9"/><circle class="gx-ring-fill" ${attr} cx="85" cy="85" r="72" fill="none" stroke-width="9" stroke-linecap="round" stroke-dasharray="${dash}" stroke-dashoffset="${dash*(type==='work'?ratio:1-ratio)}" transform="rotate(-90 85 85)"/></svg><div class="gx-ring-label"><strong ${type==='work'?'data-gwr-worktime':'data-gwr-rest'}>${gwrFmtDuration(left)}</strong><span>${type==='work'?'left in this interval':'rest left'}</span></div></div>`;
}
function gwrV60Lead(g,d,st){
 const c=state.plan.checks[g.date]||{},s=gwrStats(g),live=g.phase!=='intro'&&g.phase!=='complete';
 const segs=d.items.map(it=>{
  const done=((c.s||{})[it.k]||[]).filter(Boolean).length,sk=!!g.skip?.[it.k],cur=live&&st&&st.it.k===it.k;
  const cls=sk?'is-skipped':done>=it.n?'is-done':cur?'is-current':done>0?'is-partial':'';
  return `<i class="${cls}" style="flex:${Math.max(1,it.n)};--f:${done>=it.n?100:Math.round(100*done/Math.max(1,it.n))}%"></i>`;
 }).join('');
 return `<div class="gx-route" role="img" aria-label="${s.doneReq.length} of ${s.needed.length} required sets done">${segs}</div>`;
}
function gwrV60Scene(it,d,g){
 const c=state.plan?.checks?.[g?.date]||{},label=c.substitute?.[it?.k]||it?.l;
 return `<button type="button" class="gx-portrait" onclick="gwrDemoOpen()" aria-label="Show how to do ${esc(label||'this exercise')}">${gwrDemoArt({l:label},d.kind,fxReduced())}<span class="gx-portrait-play" aria-hidden="true"><svg viewBox="0 0 8 8"><path d="M2 1h1v1h1v1h1v1h1v1H5v1H4v1H3v1H2z" fill="currentColor"/></svg></span></button>`;
}
function gwrNudge(id,delta,min){
 const el=document.getElementById(id);if(!el)return;
 const v=Number(el.value)||0,next=Math.max(Number(min)||0,Math.round((v+delta)*10)/10);
 el.value=String(next);
 if(!fxReduced()){haptic(6);GWX.sfx('tick',Math.round(next)%24);el.animate&&el.animate([{transform:'scale(1.14)',color:'var(--accent-strong)'},{transform:'scale(1)'}],{duration:260,easing:'cubic-bezier(.2,1.6,.4,1)'})}
}
function gwrV60Body(g,d,st){
 const all=gwrStats(g),phase=g.phase;
 const screen=(cls,body,dock)=>`<div class="gx-screen ${cls}"><div class="gx-body">${body}</div><div class="gx-dock">${dock}</div></div>`;
 if(phase==='intro'){
   const base=addDays(g.date,-((parseD(g.date).getDay()+6)%7));
   const opts=Array.from({length:7},(_,i)=>{const date=addDays(base,i),x=planDay(date);if(!x||x.kind==='rest'||date>getToday())return '';return `<option value="${date}" ${date===g.date?'selected':''}>${esc(parseD(date).toLocaleDateString(undefined,{weekday:'short',month:'short',day:'numeric'}))} · ${esc(x.short)}${dayDone(date)?' (done)':''}</option>`}).join('');
   const c=state.plan.checks[g.date]||{};
   const log=d.items.map((it,i)=>{const done=((c.s||{})[it.k]||[]).filter(Boolean).length;return `<li class="${done>=it.n?'is-done':''}" style="--i:${i}"><span class="gx-log-n">${done>=it.n?px('check',12):i+1}</span><span class="gx-log-name">${esc(c.substitute?.[it.k]||it.l)}${it.opt?' <em>optional</em>':''}</span><span class="gx-log-rx">${esc(it.d)}</span></li>`}).join('');
   const when=parseD(g.date).toLocaleDateString(undefined,{weekday:'long',month:'long',day:'numeric'});
   return screen('is-intro',`<p class="gx-step">Week ${d.week} · ${esc(when)}</p><h1 class="gx-title">${esc(d.label)}</h1><div class="gx-summary"><div><strong>${d.items.length}</strong><span>exercises</span></div><div><strong>${all.all.length}</strong><span>sets</span></div><div class="is-xp"><strong>+${d.xp}</strong><span>XP</span></div></div><label class="gx-select"><span>Workout day</span><select id="gwrSessionDate" onchange="gwrChangeDate(this.value)">${opts}</select></label><ol class="gx-log">${log}</ol>`,
   `<button type="button" class="gx-btn primary" onclick="gwrBegin()">Start workout</button><p class="gx-dock-note">Every set you log is saved to your plan.</p>`);
 }
 if(phase==='complete'){
   const c=state.plan.checks[g.date]||{},act=c.actId&&state.activities.find(a=>a.id===c.actId),xp=act?.xp||0;
   const full=all.doneReq.length===all.needed.length;
   const trophy=`<svg class="gx-trophy-art" viewBox="0 0 64 64" shape-rendering="crispEdges" aria-hidden="true"><path fill="#a56c32" d="M11 12h8v7h-6v12h6v7h-7v-5H7V14h4zM45 12h8v7h-2v12h-6v7h7v-5h5V14h-4v-2z"/><path fill="#ffda8b" d="M17 10h30v22h-5v7h-6v7h11v6H17v-6h11v-7h-6v-7h-5z"/><path fill="#f6bf5c" d="M21 14h22v17h-5v5H26v-5h-5z"/><path fill="#fff4c5" d="M23 14h5v15h-5zM30 14h5v7h-5z"/><path fill="#946130" d="M24 46h16v4H24z"/></svg>`;
   return screen('is-complete',`<div class="gx-trophy ${full?'':'is-partial'}">${full?trophy:px('shield',40)}</div><h1 class="gx-title">${full?'Workout complete':'Progress saved'}</h1><p class="gx-note">${full?'Every required set is logged in your plan.':'Your logged sets are saved. Unfinished required sets stay open in your plan.'}</p><div class="gx-xp"><strong>+${xp}</strong><span>XP</span></div><div class="gx-summary"><div><strong>${all.completed.length}</strong><span>sets</span></div><div><strong>${gwrFmtDuration(gwrElapsed(g))}</strong><span>time</span></div><div><strong>${all.volume.toLocaleString()}</strong><span>lb moved</span></div></div>`,
   `<button type="button" class="gx-btn primary" onclick="gwrExitComplete()">Back to plan</button>${full?'':'<div class="gx-dock-row"><button type="button" class="gx-link" onclick="gwrReopenPartial()">Finish remaining sets</button></div>'}`);
 }
 const info=gwrNumTarget(st.it),isTimed=info.kind==='seconds'||info.kind==='minutes';
 const c=state.plan.checks[g.date]||{},label=c.substitute?.[st.it.k]||st.it.l;
 const prev=lastVal(st.it.k,g.date)||'';
 const unit=info.kind==='reps'?'reps':info.kind==='seconds'?'seconds':info.kind==='minutes'?'minutes':'';
 const doneArr=(c.s||{})[st.it.k]||[];
 const pips=Array.from({length:st.it.n},(_,i)=>`<i class="${doneArr[i]?'is-done':i===st.j?'is-current':''}" style="--i:${i}"></i>`).join('');
 const head=(tag)=>`<div class="gx-ex"><div class="gx-ex-text"><p class="gx-step">Exercise ${st.ex+1} of ${d.items.length}${st.it.opt?' · optional':''}${tag||''}</p><h1 class="gx-ex-name">${esc(label)}</h1><p class="gx-ex-rx">${esc(st.it.d)}</p></div>${gwrV60Scene(st.it,d,g)}</div><div class="gx-sets"><div class="gx-pips" aria-hidden="true">${pips}</div><span>Set ${st.j+1} of ${st.it.n}</span></div>`;
 const cue=`<p class="gx-cue"><b>Coach tip</b>${esc(gwrCue(st.it,d))}</p>`;
 if(phase==='ready'){
   const hero=info.suggest?`<div class="gx-hero"><strong>${esc(String(info.suggest))}</strong><span>${unit}</span></div>`:`<div class="gx-hero is-check"><strong>${px('check',30)}</strong><span>Complete as written</span></div>`;
   const last=st.it.wt?`<p class="gx-last">${prev?'Last time: '+esc(prev):'No weight logged yet. Pick a load you can control.'}</p>`:'';
   return screen('is-ready',`${head()}${hero}${last}${cue}`,
   `<button type="button" class="gx-btn primary" onclick="gwrGo()">${isTimed?'Start timer':'Start set'}</button><div class="gx-dock-row"><button type="button" class="gx-link" onclick="gwrSubstituteExercise()">Swap exercise</button><button type="button" class="gx-link" onclick="gwrSkipExercise()">Skip exercise</button></div>`);
 }
 if(phase==='timing'){
   const ms=Math.max(0,g.timerUntil-Date.now());
   return screen('is-timing',`${head('<span class="gx-live">Timer running</span>')}<div class="gx-ringbox">${gwrV60TimerRing('work',ms,g.timerTargetMs)}</div>${cue}`,
   `<button type="button" class="gx-btn primary" onclick="gwrTimerFinished(true)">Stop and review</button><div class="gx-dock-row"><button type="button" class="gx-link" onclick="gwrCancelInterval()">Cancel timer</button></div>`);
 }
 if(phase==='timer-done'){
   const secs=Math.max(1,Math.round((Number(g.timerActualMs)||0)/1000)),t=gwrFmtDuration(secs*1000),full=g.timerActualMs>=g.timerTargetMs;
   return screen('is-timer-done',`${head()}<div class="gx-confirm"><span class="gx-confirm-mark">${px('check',24)}</span><strong>${t}</strong><span>${full?'Interval finished':'Stopped early. Only the time you did gets logged.'}</span></div>`,
   `<button type="button" class="gx-btn primary" onclick="gwrLogSet()">Log ${t}</button><div class="gx-dock-row"><button type="button" class="gx-link" onclick="gwrCancelTimedReview()">Restart interval</button></div>`);
 }
 if(phase==='rest'){
   const left=Math.max(0,g.restUntil-Date.now());
   return screen('is-rest',`<p class="gx-rest-tag">${px('flame',16)} Set saved. Rest up.</p><div class="gx-ringbox">${gwrV60TimerRing('rest',left,g.restLength*1000)}</div><div class="gx-adjust"><button type="button" onclick="gwrAdjustRest(-15)" aria-label="Remove 15 seconds of rest">−15 s</button><button type="button" onclick="gwrAdjustRest(15)" aria-label="Add 15 seconds of rest">+15 s</button></div><div class="gx-next">${gwrV60Scene(st.it,d,g)}<div><span>Up next</span><strong>${esc(label)}</strong><small>Set ${st.j+1} of ${st.it.n} · ${esc(st.it.d)}</small></div></div>`,
   `<button type="button" class="gx-btn primary" onclick="gwrRestDone()">Start next set</button><div class="gx-dock-row"><button type="button" class="gx-link" onclick="gwrUndoLast()">Undo last set</button></div>`);
 }
 // active: GO was pressed for a rep/check exercise; record what actually happened.
 const prior=(c.setDetails?.[st.it.k]||[]).filter(Boolean).at(-1);
 const suggestedWeight=prior?.weight??(prev?Number(String(prev).match(/[\d.]+/)?.[0])||'':'');
 const stepper=(id,lbl,val,attrs,delta,min,aria)=>`<div class="gx-stepper"><label for="${id}">${lbl}</label><div class="gx-stepper-row"><button type="button" onclick="gwrNudge('${id}',-${delta},${min})" aria-label="Decrease ${aria}">−</button><input id="${id}" type="number" ${attrs} value="${esc(String(val))}" aria-label="${aria}"><button type="button" onclick="gwrNudge('${id}',${delta},${min})" aria-label="Increase ${aria}">+</button></div></div>`;
 let fields='';
 if(info.kind!=='check')fields+=stepper('gwrActualValue',info.kind==='reps'?'Reps done':esc(info.label),info.suggest||'',`min="1" inputmode="decimal" step="${info.kind==='reps'?'1':'.5'}"`,info.kind==='seconds'?5:info.kind==='minutes'?.5:1,1,'Amount completed');
 if(st.it.wt)fields+=stepper('gwrActualWeight','Weight (lb)',suggestedWeight,`min="0" max="2000" step="2.5" inputmode="decimal" placeholder="Optional"`,5,0,'Weight in pounds');
 if(!fields)fields=`<div class="gx-hero is-check"><strong>${px('check',30)}</strong><span>Finish the movement, then log it</span></div>`;
 return screen('is-active',`${head('<span class="gx-live">Set in progress</span>')}<div class="gx-fields">${fields}</div>${st.it.wt&&prev?`<p class="gx-last">Last time: ${esc(prev)}</p>`:''}`,
 `<button type="button" class="gx-btn primary" onclick="gwrLogSet()">Log set</button><div class="gx-dock-row"><button type="button" class="gx-link" onclick="gwrV60BackToReady()">Back</button></div>`);
}
function gwrCancelTimedReview(){const g=gwrCurrent();if(!g||g.phase!=='timer-done')return;g.timerActualMs=0;g.timerTargetMs=0;g.phase='ready';g.resumePhase='ready';gwrPersist();gwrRender()}
function gwrV60BackToReady(){const g=gwrCurrent();if(!g||g.phase!=='active')return;g.phase='ready';g.resumePhase='ready';gwrPersist();gwrRender()}
function gwrV60Confetti(){
 // v75: full-session victory sequence lives in gwrFxVictory. Plays once per session; partial sessions get the calm reveal.
 const root=document.getElementById('guidedWorkout'),g=gwrCurrent();
 if(!root||!g||g.phase!=='complete')return;
 const s=gwrStats(g),full=s.doneReq.length===s.needed.length,key=g.date+':'+g.startedAt;
 if(!full||fxReduced()||gwrFxCelebrated===key){root.querySelector('.gx-screen')?.classList.add('gx-reveal');return}
 gwrFxCelebrated=key;gwrFxVictory(g,planDay(g.date));
}
function gwrRender(){
 const g=gwrCurrent(),root=document.getElementById('guidedWorkout');if(!g||!root)return;
 const d=planDay(g.date);if(!d){gwrPause();return}
 const st=gwrStepNext(g);
 if(g.phase==='rest'&&!st){gwrComplete();return}
 if(!st&&g.phase!=='intro'&&g.phase!=='complete'){gwrComplete();return}
 const done=g.phase==='complete';
 const leftBtn=done
  ?`<button type="button" class="gx-top-btn" onclick="gwrExitComplete()" aria-label="Close workout"><svg viewBox="0 0 16 16" aria-hidden="true"><path d="M3 3l10 10M13 3L3 13" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg><span>Close</span></button>`
  :`<button type="button" class="gx-top-btn" onclick="gwrPause()" aria-label="Pause and save workout"><svg viewBox="0 0 16 16" aria-hidden="true"><path d="M4 2h3v12H4zM9 2h3v12H9z" fill="currentColor"/></svg><span>Pause</span></button>`;
 const prevShell=root.querySelector('.gx-shell'),prevScreen=prevShell?.querySelector(':scope>.gx-screen:not(.gx-ghost)'),prevPhase=prevShell?.dataset.phase,prevTop=prevScreen?prevScreen.offsetTop:0,prevH=prevScreen?prevScreen.offsetHeight:0;
 root.innerHTML=`<div class="gx-shell" data-phase="${esc(g.phase)}"><header class="gx-top">${leftBtn}<div class="gx-top-title"><strong>${esc(d.short||d.label)}</strong><span data-gwr-elapsed>${gwrFmtDuration(gwrElapsed(g))}</span></div><button type="button" class="gx-top-btn is-icon" onclick="gwrQuickReview()" aria-label="See all exercises">${px('grid',16)}</button></header>${gwrV60Lead(g,d,st)}${gwrV60Body(g,d,st)}</div>`;
 const newScreen=root.querySelector('.gx-screen');
 if(prevScreen&&newScreen&&prevPhase&&prevPhase!==g.phase&&prevH&&!fxReduced()){
   prevScreen.querySelectorAll('[id]').forEach(e=>e.removeAttribute('id'));
   prevScreen.querySelectorAll('*').forEach(e=>{for(const a of [...e.attributes])if(a.name.startsWith('data-gwr')||a.name==='onclick')e.removeAttribute(a.name)});
   prevScreen.classList.remove('gx-in-new','gx-in-set','gx-just-logged','gx-enter','gx-reveal');prevScreen.classList.add('gx-ghost');prevScreen.style.top=prevTop+'px';prevScreen.style.height=prevH+'px';prevScreen.setAttribute('aria-hidden','true');
   root.querySelector('.gx-shell').appendChild(prevScreen);newScreen.classList.add('gx-enter');
   setTimeout(()=>prevScreen.remove(),460);
 }
 gwrTick();gwrFloatSync();
 if(g.phase==='intro')gwrFxLastEx=null;
 if(st&&['ready','active','timing','timer-done'].includes(g.phase)){
   const setKey=st.it.k+':'+st.j,isNew=st.it.k!==gwrFxLastEx;
   if(g.phase==='ready'&&setKey!==gwrFxLastSet&&!fxReduced())gwrFxNextIntro(root,g,st,isNew);
   gwrFxLastEx=st.it.k;gwrFxLastSet=setKey;
 }
 if(done)gwrV60Confetti();
}
function gwrLauncher(){
  if(!state.plan)return '';
  const g=gwrCurrent(),date=planSel||getToday(),d=planDay(date);
  const permitted=d&&d.kind!=='rest'&&date<=getToday();
  let title='Guided workout',sub,btn,act;
  if(g){
    const gd=planDay(g.date),s=gwrStats(g);
    title='Workout in progress';sub=`${esc(gd?.label||'Session')} · ${s.doneReq.length} of ${s.needed.length} sets done`;btn='Resume';act='gwrResume()';
  }else if(permitted){
    sub=`${esc(d.label)} · ${d.items.length} exercises · ${d.items.reduce((n,it)=>n+it.n,0)} sets`;btn=dayDone(date)?'Review':'Start';act=`gwrStart('${date}')`;
  }else{
    sub=d&&d.kind==='rest'?'Rest day. Pick a training day below to start a session.':'Pick today or an earlier training day below.';btn='Start';act='';
  }
  return `<div class="gx-launch ${g?'is-resume':''}"><span class="gx-launch-icon">${px('dumbbell',20)}</span><div class="gx-launch-text"><strong>${title}</strong><span>${sub}</span></div><button type="button" class="gx-launch-btn" ${act?`onclick="${act}"`:'disabled'}>${btn}</button></div>`;
}
/* ── v75 AAA reward layer (Claude). Own canvas above the overlay, pixel-square particles to match the app's art,
   WebAudio stingers gated by Settings > sound, haptics gated by Settings > haptics, everything skipped under reduced motion. ── */
let gwrFxLastEx=null,gwrFxLastSet='',gwrFxCelebrated='',gwrFxTimers=[];
const GWX=(()=>{
 let cv,ctx,W=0,H=0,P=[],raf=0,last=0,ac=null,master=null,noiseBuf=null;
 const R=(a,b)=>a+Math.random()*(b-a),pick=a=>a[Math.floor(Math.random()*a.length)];
 function size(){if(!cv)return;const d=Math.min(2,devicePixelRatio||1);W=innerWidth;H=innerHeight;cv.width=Math.round(W*d);cv.height=Math.round(H*d);ctx.setTransform(d,0,0,d,0,0)}
 function init(){if(cv&&cv.isConnected)return;cv=document.createElement('canvas');cv.id='gwrFxCanvas';cv.setAttribute('aria-hidden','true');cv.style.cssText='position:fixed;inset:0;width:100%;height:100%;pointer-events:none;z-index:99997';document.body.appendChild(cv);ctx=cv.getContext('2d');size();if(!init.bound){init.bound=1;addEventListener('resize',size)}}
 function add(p){if(P.length>460)return;init();p.age=-(p.delay||0);P.push(p);if(!raf){last=performance.now();raf=requestAnimationFrame(loop)}}
 function loop(t){const dt=Math.min(.05,(t-last)/1000);last=t;ctx.clearRect(0,0,W,H);
  for(let i=P.length-1;i>=0;i--){const p=P[i];p.age+=dt;if(p.age<0)continue;if(p.age>=p.life){P.splice(i,1);if(p.done)p.done();continue}const k=p.age/p.life;
   if(p.k==='spark'){p.x+=p.vx*dt;p.y+=p.vy*dt;p.vy+=p.g*dt;p.vx*=1-p.drag*dt;p.vy*=1-p.drag*.4*dt;const s=p.s*(1-k*.6);ctx.globalCompositeOperation='lighter';ctx.fillStyle=p.c;ctx.globalAlpha=(1-k)*.22;ctx.fillRect(p.x-s*1.9,p.y-s*1.9,s*3.8,s*3.8);ctx.globalAlpha=1-k*k;ctx.fillRect(p.x-s/2,p.y-s/2,s,s);if(k<.3){ctx.fillStyle='#fff';ctx.fillRect(p.x-s/4,p.y-s/4,s/2,s/2)}}
   else if(p.k==='ring'){const e=1-Math.pow(1-k,3);ctx.globalCompositeOperation='lighter';ctx.globalAlpha=(1-k)*.85;ctx.strokeStyle=p.c;ctx.lineWidth=Math.max(.6,p.w*(1-k));ctx.beginPath();ctx.arc(p.x,p.y,6+(p.r1-6)*e,0,6.2832);ctx.stroke()}
   else if(p.k==='orb'){const u=k<.5?2*k*k:1-Math.pow(-2*k+2,2)/2,v=1-u,x=v*v*p.x+2*v*u*p.cx+u*u*p.tx,y=v*v*p.y+2*v*u*p.cy+u*u*p.ty;p.tr.push(x,y);if(p.tr.length>18)p.tr.splice(0,2);ctx.globalCompositeOperation='lighter';ctx.fillStyle=p.c;for(let j=0;j<p.tr.length;j+=2){const a=j/p.tr.length,s=1.5+a*4;ctx.globalAlpha=a*.55;ctx.fillRect(p.tr[j]-s/2,p.tr[j+1]-s/2,s,s)}const g=ctx.createRadialGradient(x,y,0,x,y,15);g.addColorStop(0,'#fff');g.addColorStop(.28,p.c);g.addColorStop(1,'rgba(0,0,0,0)');ctx.globalAlpha=1;ctx.fillStyle=g;ctx.fillRect(x-15,y-15,30,30)}
   else if(p.k==='conf'){p.x+=p.vx*dt+Math.sin(p.age*6+p.ph)*30*dt;p.y+=p.vy*dt;p.vy+=p.g*dt;p.vx*=1-.9*dt;p.vy=Math.min(p.vy,260);p.rot+=p.vr*dt;ctx.save();ctx.globalCompositeOperation='source-over';ctx.translate(p.x,p.y);ctx.rotate(p.rot);ctx.globalAlpha=k>.82?(1-k)*5.5:1;const flip=Math.cos(p.age*7+p.ph);ctx.fillStyle=flip>0?p.c:p.c2;ctx.scale(1,Math.abs(flip)*.85+.15);ctx.fillRect(-p.w/2,-p.h/2,p.w,p.h);ctx.restore()}
   else if(p.k==='ember'){p.x+=p.vx*dt+Math.sin(p.age*3+p.ph)*14*dt;p.y+=p.vy*dt;p.vy*=1-.4*dt;ctx.globalCompositeOperation='lighter';ctx.fillStyle=p.c;ctx.globalAlpha=Math.sin(Math.PI*k)*(.55+.45*Math.sin(p.age*16+p.ph));ctx.fillRect(p.x-p.s/2,p.y-p.s/2,p.s,p.s);ctx.globalAlpha*=.25;ctx.fillRect(p.x-p.s*1.5,p.y-p.s*1.5,p.s*3,p.s*3)}
   else if(p.k==='star'){const s=p.s*Math.sin(Math.PI*k);ctx.globalCompositeOperation='lighter';ctx.globalAlpha=.95;ctx.fillStyle=p.c;ctx.fillRect(p.x-s,p.y-s*.13,s*2,s*.26);ctx.fillRect(p.x-s*.13,p.y-s,s*.26,s*2)}}
  ctx.globalAlpha=1;ctx.globalCompositeOperation='source-over';
  if(P.length)raf=requestAnimationFrame(loop);else{raf=0;ctx.clearRect(0,0,W,H)}}
 function col(v,f){try{return getComputedStyle(document.documentElement).getPropertyValue(v).trim()||f}catch(e){return f}}
 /* audio */
 function A(){if(!fxOpt().sound)return null;try{if(!ac){const C=window.AudioContext||window.webkitAudioContext;if(!C)return null;ac=new C();master=ac.createGain();master.gain.value=.5;const dl=ac.createDelay(.6),fb=ac.createGain(),wet=ac.createGain();dl.delayTime.value=.17;fb.gain.value=.3;wet.gain.value=.2;master.connect(ac.destination);master.connect(dl);dl.connect(fb);fb.connect(dl);dl.connect(wet);wet.connect(ac.destination)}if(ac.state==='suspended')ac.resume();return ac}catch(e){return null}}
 function tone(f,t0,d,type,v,f2){const a=A();if(!a)return;const o=a.createOscillator(),g=a.createGain(),t=a.currentTime+t0;o.type=type;o.frequency.setValueAtTime(f,t);if(f2)o.frequency.exponentialRampToValueAtTime(f2,t+d);g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(v,t+.008);g.gain.exponentialRampToValueAtTime(.0001,t+d);o.connect(g);g.connect(master);o.start(t);o.stop(t+d+.05)}
 function noise(t0,d,v,f0,f1){const a=A();if(!a)return;if(!noiseBuf){noiseBuf=a.createBuffer(1,a.sampleRate,a.sampleRate);const c=noiseBuf.getChannelData(0);for(let i=0;i<c.length;i++)c[i]=Math.random()*2-1}const s=a.createBufferSource(),bp=a.createBiquadFilter(),g=a.createGain(),t=a.currentTime+t0;s.buffer=noiseBuf;bp.type='bandpass';bp.Q.value=1.4;bp.frequency.setValueAtTime(f0,t);bp.frequency.exponentialRampToValueAtTime(f1,t+d);g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(v,t+d*.3);g.gain.exponentialRampToValueAtTime(.0001,t+d);s.connect(bp);bp.connect(g);g.connect(master);s.start(t);s.stop(t+d+.05)}
 const SND={
  set(){tone(1319,0,.16,'sine',.15);tone(1976,.065,.3,'sine',.12);tone(659,0,.1,'triangle',.05)},
  land(){tone(2637,0,.09,'sine',.045);tone(3520,.03,.12,'sine',.03)},
  whoosh(){noise(0,.32,.09,600,3200)},
  impact(){tone(160,0,.42,'sine',.38,42);noise(0,.22,.14,1800,300);tone(80,0,.5,'triangle',.18,40)},
  clear(){[523,659,784,1047,1319].forEach((f,i)=>{tone(f,.08+i*.065,.38,'triangle',.08);tone(f*2,.1+i*.065,.3,'sine',.035)});for(let i=0;i<6;i++)tone(2400+Math.random()*2400,.45+i*.05,.18,'sine',.02)},
  boom(){tone(110,0,1.1,'sine',.42,36);noise(0,.9,.12,300,90)},
  swell(){[262,330,392,523].forEach((f,i)=>tone(f,i*.03,1.8,'triangle',.06))},
  fanfare(){[[523,0],[659,.09],[784,.18],[1047,.3],[784,.42],[1047,.5]].forEach(([f,t])=>{tone(f,t,.32,'square',.035);tone(f,t,.45,'triangle',.07)});[523,659,784,1047].forEach(f=>tone(f,.62,1.6,'triangle',.06));for(let i=0;i<10;i++)tone(2000+Math.random()*2600,.7+i*.06,.2,'sine',.022)},
  record(){[784,988,1175,1568].forEach((f,i)=>{tone(f,i*.06,.3,'triangle',.08);tone(f*2,i*.06+.01,.25,'sine',.03)});tone(2093,.3,.6,'sine',.05)},
  tick(i){tone(1400+Math.min(i,24)*38,0,.05,'square',.022)}
 };
 return {col,
  sfx(n,a){try{SND[n]&&SND[n](a)}catch(e){}},
  burst(x,y,cols,n,sp,o){o=o||{};const sz=o.size||[3,7];for(let i=0;i<n;i++){const a=o.a0!=null?R(o.a0,o.a1):R(0,6.2832),v=sp*R(.35,1);add({k:'spark',x,y,vx:Math.cos(a)*v,vy:Math.sin(a)*v-(o.lift||0),g:o.g==null?600:o.g,drag:o.drag||2,s:Math.round(R(sz[0],sz[1])),c:pick(cols),life:R(.45,o.life||1.1),delay:o.delay||0})}},
  ring(x,y,c,r1,life,w,delay){add({k:'ring',x,y,c,r1,life,w:w||4,delay:delay||0})},
  orbs(x,y,tx,ty,c,n,done){for(let i=0;i<n;i++){const dx=tx-x,dy=ty-y,d=Math.hypot(dx,dy)||1,o=R(-.5,.5)*d;add({k:'orb',x:x+R(-10,10),y:y+R(-10,10),tx,ty,cx:(x+tx)/2-dy/d*o,cy:(y+ty)/2+dx/d*o-d*.18,c,tr:[],life:R(.5,.68),delay:i*.045,done:i===n-1?done:null})}},
  confetti(x,y,n,a0,a1,sp,cols,delay){for(let i=0;i<n;i++){const a=R(a0,a1)*Math.PI/180,v=sp*R(.5,1);add({k:'conf',x,y,vx:Math.cos(a)*v,vy:Math.sin(a)*v,g:900,rot:R(0,6),vr:R(-10,10),ph:R(0,6),w:R(5,9),h:R(9,15),c:cols[i%cols.length],c2:cols[(i+1)%cols.length],life:R(2.4,3.6),delay:(delay||0)+R(0,.12)})}},
  embers(x,y,w,n,cols,delay){for(let i=0;i<n;i++)add({k:'ember',x:x+R(-w/2,w/2),y:y+R(-4,4),vx:R(-14,14),vy:-R(60,170),ph:R(0,6),s:Math.round(R(2,5)),c:pick(cols),life:R(1.1,2.2),delay:(delay||0)+R(0,.5)})},
  star(x,y,c,s,life,delay){add({k:'star',x,y,c,s,life,delay:delay||0})},
  clear(){P.length=0}
 };
})();
function gwrFxLater(fn,ms){const id=setTimeout(()=>{gwrFxTimers=gwrFxTimers.filter(x=>x!==id);if(!document.getElementById('guidedWorkout')?.classList.contains('open'))return;fn()},ms);gwrFxTimers.push(id);return id}
function gwrFxCount(el,to,ms,fmt,onTick){if(!el)return;const t0=performance.now();to=Number(to)||0;const step=t=>{if(!el.isConnected)return;const k=Math.min(1,(t-t0)/ms),e=1-Math.pow(1-k,3),v=Math.round(to*e);if(v!==el._gv){el._gv=v;el.textContent=fmt?fmt(v):v.toLocaleString();if(onTick)onTick(v)}if(k<1)requestAnimationFrame(step)};el.textContent=fmt?fmt(0):'0';requestAnimationFrame(step)}
function gwrFxLetters(text,base){let i=base||0;return String(text).split(/\s+/).filter(Boolean).map(w=>`<span class="gx-w">${[...w].map(ch=>`<span style="--i:${i++}">${esc(ch)}</span>`).join('')}</span>`).join(' ')}
function gwrFxShake(root,strong){const sh=root?.querySelector('.gx-shell');if(!sh||!sh.animate)return;const a=strong?7:3;sh.animate([{transform:'translate(0,0)'},{transform:`translate(${-a}px,${a*.6}px)`},{transform:`translate(${a*.8}px,${-a*.5}px)`},{transform:`translate(${-a*.5}px,${-a*.3}px)`},{transform:`translate(${a*.3}px,${a*.2}px)`},{transform:'translate(0,0)'}],{duration:strong?340:220,easing:'ease-out'})}
function gwrFxSeg(root,g,st){if(!root||!g||!st)return null;const d=planDay(g.date);const idx=d?d.items.findIndex(it=>it.k===st.it.k):-1;return idx<0?null:root.querySelectorAll('.gx-route i')[idx]||null}
function gwrFxSetDone(st){
 const g=gwrCurrent(),root=document.getElementById('guidedWorkout');if(!g||!st||!root)return;
 const good=GWX.col('--good','#3dd68c'),acc=GWX.col('--accent','#7c9cff');
 const o=(typeof lastTap==='object'&&lastTap&&lastTap.y>innerHeight*.45)?lastTap:{x:innerWidth/2,y:innerHeight-90};
 GWX.ring(o.x,o.y,good,96,.55,4);GWX.ring(o.x,o.y,'#ffffff',54,.35,2);
 GWX.burst(o.x,o.y,[good,good,'#ffffff',acc],30,380,{g:760,lift:120});
 GWX.sfx('set');haptic([12,30,18]);
 const seg=gwrFxSeg(root,g,st);
 if(seg){
  const n=st.it.n,done=(state.plan.checks[g.date]?.s?.[st.it.k]||[]).filter(Boolean).length;
  seg.style.setProperty('--f',Math.round(100*Math.max(0,done-1)/n)+'%');void seg.offsetWidth;
  const r=seg.getBoundingClientRect(),tx=r.left+Math.max(4,r.width*Math.min(1,done/n)),ty=r.top+r.height/2;
  GWX.orbs(o.x,o.y-10,tx,ty,good,7,()=>{if(!seg.isConnected)return;seg.style.setProperty('--f',Math.round(100*done/n)+'%');seg.classList.remove('gx-glint');void seg.offsetWidth;seg.classList.add('gx-glint');GWX.burst(tx,ty,[good,'#ffffff'],12,150,{g:0,drag:4,size:[2,4],life:.6});GWX.star(tx,ty,'#ffffff',12,.45);GWX.sfx('land')});
 }
 const rec=gwrFxRecord(g,st);const chip=document.createElement('div');chip.className='gx-setchip'+(rec?' is-record':'');chip.setAttribute('role','status');
 chip.innerHTML=`<svg viewBox="0 0 16 16" aria-hidden="true" shape-rendering="crispEdges"><path d="M2 8h3v1h1v1h1V9h1V8h1V7h1V6h1V5h1V4h1v3h-1v1h-1v1h-1v1H9v1H8v1H7v1H6v-1H5v-1H4v-1H3V9H2z" fill="currentColor"/></svg><span>${rec?'New record · '+esc(rec.label):`Set ${st.j+1} of ${st.it.n} logged`}</span>`;
 root.appendChild(chip);gwr63RewardTimer=setTimeout(()=>{chip.remove();gwr63RewardTimer=null},rec?2600:1700);
 if(rec)gwrFxLater(()=>{const r=chip.getBoundingClientRect(),x=r.left+r.width/2,y=r.top+r.height/2;GWX.ring(x,y,'#ffd27a',140,.7,4);GWX.burst(x,y,['#ffd27a','#fff1c4','#ffffff'],46,360,{g:420,lift:60,size:[3,7],life:1.1});for(let i=0;i<4;i++)GWX.star(x+(Math.random()-.5)*r.width,y+(Math.random()-.5)*40,'#ffffff',10,.5,i*.08);GWX.sfx('record');haptic([20,40,20,40,50])},260);
 root.querySelector('.gx-ring')?.classList.add('gx-ring-charge');root.querySelector('.gx-screen')?.classList.add('gx-just-logged');
 gwrFxHalfway(g,rec?2700:1500);
}
function gwrFxCrest(){return `<svg viewBox="0 0 32 32" shape-rendering="crispEdges" aria-hidden="true"><path fill="#7a4a1c" d="M6 3h20v2h2v12h-1v4h-2v3h-2v2h-3v2h-2v2h-4v-2h-2v-2H9v-2H7v-3H5v-4H4V5h2z"/><path fill="#ffd27a" d="M7 5h18v1h1v11h-1v3h-1v3h-2v2h-3v2h-2v1h-2v-1h-2v-2H9v-2H8v-3H7v-3H6V6h1z"/><path fill="#fff1c4" d="M8 6h6v2H9v6H8z"/><path fill="#1d2433" d="M9 8h14v8h-1v3h-1v2h-2v2h-2v1h-2v-1h-2v-2h-2v-2H10v-3H9z"/><path fill="#5ff0a8" d="M11 14h2v1h1v1h1v-1h1v-1h1v-1h1v-1h1v-1h2v2h-1v1h-1v1h-1v1h-1v1h-1v1h-2v-1h-1v-1h-1v-1h-1z"/><path fill="#d8fff0" d="M11 14h2v1h-2zM19 11h2v1h-2z"/></svg>`}
function gwrFxExerciseCleared(st){
 const g=gwrCurrent(),root=document.getElementById('guidedWorkout');if(!g||!st||!root)return;
 const d=planDay(g.date),c=state.plan.checks[g.date]||{},idx=d.items.findIndex(it=>it.k===st.it.k);
 const label=c.substitute?.[st.it.k]||st.it.l,det=(c.setDetails?.[st.it.k]||[]).filter(Boolean);
 const sets=(c.s?.[st.it.k]||[]).filter(Boolean).length,reps=det.reduce((n,x)=>n+(Number(x.reps)||0),0),mins=det.reduce((n,x)=>n+(Number(x.minutes)||0)+(Number(x.seconds)||0)/60,0),vol=Math.round(det.reduce((n,x)=>n+(Number(x.reps)||0)*(Number(x.weight)||0),0));
 const stats=[[sets,'sets']];if(reps)stats.push([reps,'reps']);if(vol)stats.push([vol,'lb moved']);if(!reps&&mins)stats.push([Math.round(mins*10)/10,'minutes']);
 const rec=gwrFxRecord(g,st);const nx=gwrStepNext(g),nxLabel=nx?(c.substitute?.[nx.it.k]||nx.it.l):'';
 const ov=document.createElement('div');ov.className='gx-clear';ov.setAttribute('role','status');ov.setAttribute('aria-label',`${label} cleared`);
 ov.innerHTML=`<div class="gx-clear-dim"></div><div class="gx-clear-card"><div class="gx-clear-rays"></div><div class="gx-clear-emblem">${gwrFxCrest()}</div><p class="gx-clear-kicker">Exercise ${idx+1} of ${d.items.length} cleared</p><h2 class="gx-clear-title">${gwrFxLetters(label)}</h2>${rec?`<p class="gx-clear-record">New record · ${esc(rec.label)}</p>`:''}<div class="gx-clear-stats">${stats.map(([v,l])=>`<div><strong data-to="${v}">0</strong><span>${l}</span></div>`).join('')}</div>${nxLabel?`<div class="gx-clear-next"><span>Up next</span><strong>${esc(nxLabel)}</strong></div>`:''}</div>`;
 root.appendChild(ov);
 const gold='#ffd27a',good=GWX.col('--good','#3dd68c');
 let exited=false;
 const exit=()=>{if(exited||!ov.isConnected)return;exited=true;const card=ov.querySelector('.gx-clear-card'),seg=gwrFxSeg(root,g,st);
   ov.classList.add('is-leaving');
   if(card&&seg&&card.animate){const a=card.getBoundingClientRect(),b=seg.getBoundingClientRect();const dx=b.left+b.width/2-(a.left+a.width/2),dy=b.top+b.height/2-(a.top+a.height/2);GWX.sfx('whoosh');
     card.animate([{transform:'translate(0,0) scale(1)',opacity:1},{transform:`translate(${dx*.3}px,${dy*.3}px) scale(.55)`,opacity:1,offset:.45},{transform:`translate(${dx}px,${dy}px) scale(.04)`,opacity:.2}],{duration:460,easing:'cubic-bezier(.5,0,.75,0)',fill:'forwards'}).onfinish=()=>{ov.remove();if(!seg.isConnected)return;seg.style.setProperty('--f','100%');seg.classList.remove('gx-glint');void seg.offsetWidth;seg.classList.add('gx-glint');const x=b.left+b.width/2,y=b.top+b.height/2;GWX.burst(x,y,[gold,good,'#ffffff'],22,220,{g:300,size:[2,5],life:.8});GWX.ring(x,y,gold,40,.4,3);GWX.sfx('land')}}
   else ov.remove()};
 ov.addEventListener('click',()=>{if(performance.now()-t0>350)exit()});const t0=performance.now();
 const em=()=>{const e=ov.querySelector('.gx-clear-emblem');if(!e)return{x:innerWidth/2,y:innerHeight*.38};const r=e.getBoundingClientRect();return{x:r.left+r.width/2,y:r.top+r.height/2}};
 GWX.sfx('whoosh');haptic([20,40,30,60,40]);
 gwrFxLater(()=>{if(!ov.isConnected)return;const p=em();gwrFxShake(root,true);GWX.sfx('impact');GWX.ring(p.x,p.y,gold,170,.7,6);GWX.ring(p.x,p.y,'#ffffff',110,.45,3);GWX.ring(p.x,p.y,good,230,.9,2,.08);
   GWX.burst(p.x,p.y,[gold,gold,'#fff1c4','#ffffff',good],70,520,{g:520,lift:180,size:[3,8],life:1.3});
   for(let i=0;i<6;i++)GWX.star(p.x+(Math.random()-.5)*190,p.y+(Math.random()-.5)*130,'#ffffff',9+Math.random()*9,.5,.1+i*.07);
   GWX.sfx('clear')},290);
 gwrFxLater(()=>{ov.querySelectorAll('.gx-clear-stats strong').forEach((s,i)=>gwrFxCount(s,s.dataset.to,650+i*120))},620);
 gwrFxLater(exit,2350);
 gwrFxHalfway(g,3000);
}
function gwrFxNextIntro(root,g,st,isNewEx){
 const scr=root.querySelector('.gx-screen');if(!scr)return;
 scr.classList.add(isNewEx?'gx-in-new':'gx-in-set');
 if(!isNewEx)return;
 const d=planDay(g.date),rest=d.items.filter((it,i)=>i>st.ex&&!g.skip?.[it.k]).length;
 if(st.ex>0&&rest===0){scr.classList.add('gx-boss');gwrFxBanner('Last exercise',(state.plan.checks[g.date]?.substitute?.[st.it.k])||st.it.l,'boss');return}
 GWX.sfx('whoosh');
 const h=scr.querySelector('.gx-hero strong');if(h&&/^\d+$/.test(h.textContent.trim())){const to=Number(h.textContent);gwrFxLater(()=>gwrFxCount(h,to,520,null,v=>{if(v&&v%Math.max(1,Math.ceil(to/6))===0)GWX.sfx('tick',v)}),260)}
}
function gwrFxVictory(g,d){
 const root=document.getElementById('guidedWorkout');if(!root)return;
 const s=gwrStats(g),c=state.plan.checks[g.date]||{},act=c.actId&&state.activities.find(a=>a.id===c.actId),xp=act?.xp||0;
 const trophy=`<svg viewBox="0 0 64 64" shape-rendering="crispEdges"><path fill="#a56c32" d="M11 12h8v7h-6v12h6v7h-7v-5H7V14h4zM45 12h8v7h-2v12h-6v7h7v-5h5V14h-4v-2z"/><path fill="#ffda8b" d="M17 10h30v22h-5v7h-6v7h11v6H17v-6h11v-7h-6v-7h-5z"/><path fill="#f6bf5c" d="M21 14h22v17h-5v5H26v-5h-5z"/><path fill="#fff4c5" d="M23 14h5v15h-5zM30 14h5v7h-5z"/><path fill="#946130" d="M24 46h16v4H24z"/><path fill="#fff5cd" d="M30 21h4v7h7v4h-7v7h-4v-7h-7v-4h7z" opacity=".55"/></svg>`;
 const mask=`url("data:image/svg+xml,${encodeURIComponent(trophy.replace('<svg ','<svg xmlns="http://www.w3.org/2000/svg" '))}")`;
 const ov=document.createElement('div');ov.className='gx-victory';ov.setAttribute('role','status');ov.setAttribute('aria-label',`Workout complete. ${xp} XP earned.`);
 ov.innerHTML=`<div class="gx-v-beam"></div><div class="gx-v-rays"></div><div class="gx-v-stage"><div class="gx-v-trophy">${trophy}<i class="gx-v-shine" style="-webkit-mask-image:${mask};mask-image:${mask}"></i></div><h2 class="gx-v-title">${gwrFxLetters('Workout complete')}</h2><div class="gx-v-xp"><strong>+0</strong><span>XP</span></div><div class="gx-v-stats"><div><strong data-to="${s.completed.length}">0</strong><span>sets</span></div><div><strong class="is-text">${gwrFxDurationText(gwrElapsed(g))}</strong><span>time</span></div><div><strong data-to="${Math.round(s.volume)}">0</strong><span>lb moved</span></div></div><p class="gx-v-skip">Tap to continue</p></div>`;
 root.appendChild(ov);
 const gold='#ffd27a',good=GWX.col('--good','#3dd68c'),acc=GWX.col('--accent','#7c9cff'),cols=[gold,'#ffffff',good,acc,'#f6bf5c'];
 const t0=performance.now();let gone=false;
 const done=()=>{if(gone)return;gone=true;gwrFxTimers.forEach(clearTimeout);gwrFxTimers=[];ov.classList.add('is-leaving');root.querySelector('.gx-screen')?.classList.add('gx-reveal');setTimeout(()=>ov.remove(),420)};
 ov.addEventListener('click',()=>{if(performance.now()-t0>700)done()});
 const tro=()=>{const e=ov.querySelector('.gx-v-trophy');if(!e)return{x:innerWidth/2,y:innerHeight*.3,w:120,b:innerHeight*.36};const r=e.getBoundingClientRect();return{x:r.left+r.width/2,y:r.top+r.height/2,w:r.width,b:r.bottom-8}};
 GWX.sfx('boom');haptic([30,60,40]);
 gwrFxLater(()=>{const p=tro();GWX.sfx('swell');GWX.ring(p.x,p.y,gold,220,.9,6);GWX.ring(p.x,p.y,'#ffffff',140,.55,3);GWX.burst(p.x,p.y,[gold,'#fff1c4','#ffffff'],60,460,{g:380,lift:140,size:[3,8],life:1.3});gwrFxShake(root,true);haptic([25,50,35]);
   for(let i=0;i<5;i++)gwrFxLater(()=>{const q=tro();GWX.embers(q.x,q.b,q.w*.7,14,[gold,'#fff1c4','#f6bf5c'])},i*380)},480);
 gwrFxLater(()=>{const p=tro();for(let i=0;i<7;i++)GWX.star(p.x+(Math.random()-.5)*p.w*1.3,p.y+(Math.random()-.5)*p.w,'#ffffff',10+Math.random()*10,.55,i*.09)},950);
 gwrFxLater(()=>{const el=ov.querySelector('.gx-v-xp strong');let ticks=0;gwrFxCount(el,xp,950,v=>'+'+v,v=>{if(ticks++%Math.max(1,Math.ceil(xp/14))===0){GWX.sfx('tick',ticks);el.animate&&el.animate([{transform:'scale(1.1)'},{transform:'scale(1)'}],{duration:110})}})},1450);
 gwrFxLater(()=>{const el=ov.querySelector('.gx-v-xp');if(!el)return;const r=el.getBoundingClientRect(),x=r.left+r.width/2,y=r.top+r.height/2;el.classList.add('is-landed');GWX.sfx('fanfare');haptic([20,40,20,40,60]);GWX.ring(x,y,gold,160,.7,5);GWX.burst(x,y,[gold,'#ffffff','#fff1c4'],50,420,{g:500,lift:100,life:1.2});
   GWX.confetti(-10,innerHeight+10,70,-80,-45,980,cols);GWX.confetti(innerWidth+10,innerHeight+10,70,-135,-100,980,cols);
   GWX.confetti(-10,innerHeight+10,40,-75,-50,860,cols,.55);GWX.confetti(innerWidth+10,innerHeight+10,40,-130,-105,860,cols,.55);
   ov.querySelectorAll('.gx-v-stats strong[data-to]').forEach((s,i)=>gwrFxCount(s,s.dataset.to,700+i*150))},2450);
 gwrFxLater(done,6200);
}
function gwrFxDurationText(ms){return gwrFmtDuration(ms)}
let gwrFxCdLast=-1;
function gwrFxCountdown(kind,left){
 const ring=document.querySelector('#guidedWorkout .gx-ring');if(!ring)return;
 const fin=left>0&&left<=3;ring.classList.toggle('is-final',fin);
 if(!fin){gwrFxCdLast=-1;return}
 if(left===gwrFxCdLast||fxReduced())return;gwrFxCdLast=left;
 const lab=ring.querySelector('.gx-ring-label strong');if(lab&&lab.animate)lab.animate([{transform:'scale(1.28)'},{transform:'scale(1)'}],{duration:420,easing:'cubic-bezier(.2,1.6,.4,1)'});
 const r=ring.getBoundingClientRect(),x=r.left+r.width/2,y=r.top+r.height/2,c=kind==='rest'?'#ffd27a':GWX.col('--accent','#7c9cff');
 GWX.ring(x,y,c,r.width*.66,.7,3);if(left===1)GWX.burst(x,y-r.width*.42,[c,'#ffffff'],14,160,{g:0,drag:3,size:[2,4],life:.6});
 GWX.sfx('tick',left===1?24:left*4);haptic(left===1?[14]:[8]);
}
function gwrFxRipple(e){
 if(fxReduced())return;const b=e.target.closest&&e.target.closest('.gx-btn.primary,.gx-adjust button,.gx-stepper-row button,.gx-top-btn');if(!b)return;
 const r=b.getBoundingClientRect(),d=Math.max(r.width,r.height)*2.2,s=document.createElement('span');s.className='gx-ripple';
 s.style.cssText=`width:${d}px;height:${d}px;left:${e.clientX-r.left-d/2}px;top:${e.clientY-r.top-d/2}px`;b.appendChild(s);setTimeout(()=>s.remove(),650);
}
function gwrFxRecord(g,st){
 // Personal record: heaviest weight ever for this exercise (or most reps, for unweighted rep work). Needs earlier data to compare against.
 if(!g||!st)return null;const all=state.plan?.checks||{},k=st.it.k,today=all[g.date]?.setDetails?.[k]||[],cur=today[st.j];if(!cur)return null;
 const useW=Number(cur.weight)>0,val=x=>useW?Number(x?.weight)||0:Number(x?.reps)||0;if(!useW&&!(Number(cur.reps)>0))return null;
 let best=0,has=false;
 for(const [date,ch] of Object.entries(all))for(const [i,x] of (ch?.setDetails?.[k]||[]).entries()){if(!x||(date===g.date&&i===st.j))continue;const v=val(x);if(v>0){has=true;if(v>best)best=v}}
 const v=val(cur);return has&&v>best?{label:useW?`${v} lb`:`${v} reps`,prev:best}:null;
}
let gwrFxWakeLock=null;
async function gwrFxWake(on){
 try{if(on&&!gwrFxWakeLock&&navigator.wakeLock&&document.visibilityState==='visible'){gwrFxWakeLock=await navigator.wakeLock.request('screen');gwrFxWakeLock.addEventListener('release',()=>{gwrFxWakeLock=null})}
 else if(!on&&gwrFxWakeLock){const l=gwrFxWakeLock;gwrFxWakeLock=null;await l.release()}}catch(e){gwrFxWakeLock=null}
}
let gwrFxHold=null,gwrFxHeld=false;
function gwrFxHoldStart(e){
 const b=e.target.closest&&e.target.closest('.gx-stepper-row button');if(!b)return;
 const m=(b.getAttribute('onclick')||'').match(/gwrNudge\('(\w+)',(-?[\d.]+),(-?[\d.]+)\)/);if(!m)return;
 gwrFxHoldEnd();gwrFxHeld=false;let n=0;
 gwrFxHold=setTimeout(function rep(){gwrFxHeld=true;gwrNudge(m[1],Number(m[2]),Number(m[3]));n++;gwrFxHold=setTimeout(rep,n>8?55:110)},420);
}
function gwrFxHoldEnd(){if(gwrFxHold){clearTimeout(gwrFxHold);gwrFxHold=null}}
function gwrFxBanner(title,sub,tone){
 const root=document.getElementById('guidedWorkout');if(!root||fxReduced()||!root.classList.contains('open'))return;
 root.querySelector('.gx-banner')?.remove();
 const b=document.createElement('div');b.className='gx-banner'+(tone?' is-'+tone:'');b.setAttribute('role','status');
 b.innerHTML=`<i class="gx-bar top"></i><i class="gx-bar bottom"></i><div class="gx-band"><i class="gx-streak"></i><strong>${gwrFxLetters(title)}</strong>${sub?`<span>${esc(sub)}</span>`:''}</div>`;
 root.appendChild(b);GWX.sfx('whoosh');gwrFxLater(()=>{GWX.sfx(tone==='boss'?'impact':'set');const r=b.querySelector('.gx-band')?.getBoundingClientRect();if(r){const c=tone==='boss'?'#ff8a5c':'#ffd27a';GWX.burst(r.left+r.width/2,r.top+r.height/2,[c,'#ffffff','#ffd27a'],36,420,{g:200,size:[2,6],life:.9,a0:-.5,a1:.5});GWX.burst(r.left+r.width/2,r.top+r.height/2,[c,'#ffffff'],36,420,{g:200,size:[2,6],life:.9,a0:2.64,a1:3.64})}haptic(tone==='boss'?[30,50,30]:[16,30,16])},380);
 setTimeout(()=>b.remove(),2100);
}
function gwrFxHalfway(g,delay){
 if(!g||g.fxHalf)return;const s=gwrStats(g);if(!s.needed.length||s.doneReq.length*2<s.needed.length||s.doneReq.length>=s.needed.length)return;
 g.fxHalf=true;gwrPersist();gwrFxLater(()=>gwrFxBanner('Halfway there',`${s.doneReq.length} of ${s.needed.length} sets done`),delay);
}
/* ── v79 exercise demonstrations (Claude). A jointed pixel figure (SVG + SMIL), one start/end pose pair per movement pattern,
   mapped from exercise names. Used in the exercise portrait (tap for a large "how to" sheet). Still pose under reduced motion. ── */
const GWR_DEMO=(()=>{
 const S={r:[80,80],t:0,a:[0,0],l:[0,0]};
 const seat=(x,y)=>`<rect x="${x-13}" y="${y+3}" width="26" height="5" fill="#596579"/><rect x="${x-2}" y="${y+8}" width="4" height="${112-y-8}" fill="#46505f"/>`;
 const back=(x,y,h)=>`<rect x="${x-10}" y="${y-(h||30)}" width="5" height="${(h||30)+8}" fill="#46505f"/>`;
 const pulley=`<rect x="134" y="22" width="6" height="90" fill="#46505f"/><rect x="128" y="22" width="18" height="5" fill="#596579"/>`;
 const mat=`<rect x="16" y="110" width="128" height="3" fill="var(--accent)" opacity=".55"/>`;
 return {
  squat:{d:2.6,A:{...S,a:[-70,-40]},B:{r:[67,93],t:34,a:[-104,-40],l:[-76,80]}},
  sitstand:{d:2.8,A:{...S,a:[-80,0]},B:{r:[66,95],t:28,a:[-108,0],l:[-80,82]},p:()=>`<rect x="46" y="98" width="24" height="14" fill="#596579"/>`},
  legpress:{d:2.6,A:{r:[60,94],t:-58,a:[40,-30],l:[-165,95]},B:{l:[-126,0]},hand:0,plate:1,p:()=>`<path d="M44 104 L32 62 L38 60 L50 100 Z" fill="#46505f"/>${seat(58,94)}`},
  lunge:{d:2.6,A:{...S},B:{r:[80,95],t:4,a:[-4,0],l:[-80,80],lf:[34,58]}},
  stepup:{d:2.8,A:{r:[78,80],t:6,a:[0,0],l:[-55,55],lf:[0,0]},B:{r:[94,66],t:0,l:[0,0],lf:[18,30]},p:()=>`<rect x="88" y="98" width="34" height="14" fill="#596579"/><rect x="88" y="98" width="34" height="2" fill="#8391a6"/>`},
  hinge:{d:2.8,A:{...S},B:{r:[74,82],t:78,a:[-78,0],l:[-14,22]}},
  bridge:{d:2.6,A:{r:[74,105],t:-90,a:[0,0],l:[-120,110]},B:{r:[74,95],t:-68,a:[-22,0],l:[-100,96]},p:()=>mat,hand:0},
  calf:{d:1.8,A:{...S},B:{r:[80,76],l:[0,0,28]},hand:0},
  legext:{d:2.4,A:{r:[66,90],t:-6,a:[12,-40],l:[-90,90]},B:{l:[-90,8]},hand:0,p:()=>seat(66,90)+back(66,90)},
  legcurl:{d:2.4,A:{r:[78,95],t:90,a:[-90,0],l:[90,0]},B:{l:[90,96]},hand:0,p:()=>`<rect x="34" y="100" width="100" height="5" fill="#596579"/><rect x="44" y="105" width="4" height="7" fill="#46505f"/><rect x="120" y="105" width="4" height="7" fill="#46505f"/>`},
  chestpress:{d:2.4,A:{r:[64,92],t:-10,a:[60,-125],l:[-90,90]},B:{a:[-80,0]},p:()=>seat(64,92)+back(64,92,34)},
  pushup:{d:2.2,A:{r:[84,100],t:68,a:[-68,0],l:[70,0]},B:{r:[84,104],t:72,a:[-22,-75],l:[72,0]},hand:0,p:()=>mat},
  ohpress:{d:2.6,A:{...S,a:[-35,-140]},B:{a:[-178,0]}},
  wallslide:{d:3,A:{...S,a:[-120,-70]},B:{a:[-176,-5]},hand:0,p:()=>`<rect x="58" y="20" width="5" height="92" fill="#46505f"/>`},
  row:{d:2.4,A:{r:[62,96],t:22,a:[-112,0],l:[-80,62]},B:{t:-8,a:[40,-130]},prop:'handle',p:()=>pulley+`<rect x="50" y="99" width="26" height="4" fill="#596579"/>`},
  bentrow:{d:2.4,A:{r:[76,82],t:68,a:[-68,0],l:[-12,18]},B:{a:[40,-108]}},
  pulldown:{d:2.6,A:{r:[72,96],t:-8,a:[-172,0],l:[-90,90]},B:{a:[18,-188]},prop:'bar',p:()=>seat(72,96)+`<rect x="98" y="12" width="5" height="100" fill="#46505f"/>`},
  pallof:{d:2.8,A:{...S,a:[-20,-110]},B:{a:[-90,0]},prop:'handle',p:()=>pulley},
  plank:{d:3.2,A:{r:[80,99],t:76,a:[-76,-90],l:[76,0]},B:{r:[80,97],t:73,a:[-73,-90]},hand:0,p:()=>mat},
  deadbug:{d:2.8,A:{r:[92,104],t:-90,a:[-90,0],l:[-180,90]},B:{l:[-112,0],af:[-172,0]},hand:0,p:()=>mat},
  birddog:{d:2.8,A:{r:[66,90],t:90,a:[-90,0],l:[0,90]},B:{a:[-180,0],lf:[90,0]},hand:0,p:()=>mat},
  carry:{d:1,A:{r:[80,80],t:2,a:[0,0],l:[-20,15],lf:[22,25]},B:{l:[22,25],lf:[-20,15]}},
  walk:{d:1,A:{r:[80,80],t:4,a:[28,-25],af:[-28,-25],l:[-24,16],lf:[24,26]},B:{a:[-28,-25],af:[28,-25],l:[24,26],lf:[-24,16]},hand:0},
  fold:{d:3.4,A:{...S,a:[-178,0]},B:{r:[76,80],t:96,a:[-96,0],l:[-6,6]},hand:0},
  quad:{d:3.2,A:{r:[80,80],t:0,a:[30,-20],af:[-40,0],l:[12,150],lf:[0,0]},B:{r:[81,80],t:2},hand:0},
  calfstretch:{d:3.2,A:{r:[76,82],t:22,a:[-112,0],l:[-28,36],lf:[32,-12]},B:{r:[78,82],t:30,a:[-120,0]},hand:0,p:()=>`<rect x="122" y="20" width="6" height="92" fill="#46505f"/>`},
  kneel:{d:3.4,A:{r:[80,96],t:0,a:[0,0],l:[-80,80],lf:[30,62]},B:{r:[86,98],t:-6,a:[-176,0],l:[-66,66],lf:[46,44]},hand:0,p:()=>mat},
  cobra:{d:3.2,A:{r:[70,106],t:90,a:[-30,-120],l:[90,0]},B:{t:56,a:[-56,0]},hand:0,p:()=>mat},
  catcow:{d:3.2,A:{r:[66,90],t:84,a:[-84,0],l:[0,90]},B:{t:97,a:[-97,0]},hand:0,p:()=>mat},
  twist:{d:3,A:{...S,a:[-90,0],af:[60,-30]},B:{a:[60,-30],af:[-90,0]},hand:0},
  opener:{d:3.2,A:{...S,a:[-10,0]},B:{a:[48,0]},hand:0},
  extend:{d:3.4,A:{...S,a:[-178,0]},B:{t:-16,a:[-176,0]},hand:0},
  balance:{d:3,A:{r:[80,80],t:0,a:[-70,0],af:[-70,0],l:[-40,72],lf:[0,0]},B:{r:[81,80],t:-2},hand:0},
  flow:{d:3.6,A:{...S,a:[-178,0]},B:{r:[76,80],t:90,a:[-90,0],l:[-6,8]},hand:0}
 };
})();
function gwrDemoKey(name,kind){
 const n=String(name||'').toLowerCase().replace(/&#39;/g,"'");
 const M=[[/leg press/,'legpress'],[/leg extension/,'legext'],[/leg curl/,'legcurl'],[/calf raise/,'calf'],[/calf stretch/,'calfstretch'],[/step-?up/,'stepup'],[/lunge|split squat/,'lunge'],[/sit-to-stand|box sit/,'sitstand'],[/squat/,'squat'],[/romanian|deadlift|back extension/,'hinge'],[/bridge/,'bridge'],[/push-?up/,'pushup'],[/chest press|bench press|incline machine|incline.*press/,'chestpress'],[/wall slide/,'wallslide'],[/overhead|shoulder press/,'ohpress'],[/pulldown|pull-up/,'pulldown'],[/face pull|pec deck|cable row/,'row'],[/row/,'bentrow'],[/pallof/,'pallof'],[/plank/,'plank'],[/dead bug/,'deadbug'],[/bird-?dog/,'birddog'],[/farmer/,'carry'],[/cobra/,'cobra'],[/cat-?cow/,'catcow'],[/world|hip flexor|kneeling/,'kneel'],[/quad stretch/,'quad'],[/hamstring|adductor|sweep/,'fold'],[/twist|rotation/,'twist'],[/chest stretch/,'opener'],[/thoracic/,'extend'],[/balance/,'balance'],[/mobility flow|full-body/,'flow'],[/walk|cardio|jog|cycl|treadmill|elliptical|pacing|step touch|interval|finisher|conditioning|aerobic|warm-up|cool-down/,'walk']];
 for(const [re,k] of M)if(re.test(n))return k;
 return kind==='cardio'?'walk':kind==='recovery'?'flow':'squat';
}
let gwrDemoN=0;
function gwrDemoArt(it,kind,still){
 // v81 art direction: sculpted porcelain mannequin, spotlight, accent rim light, glossy floor reflection, ghost of the end pose.
 const name=it?.l||'',key=gwrDemoKey(name,kind),P=GWR_DEMO[key]||GWR_DEMO.walk,u='gd'+(++gwrDemoN);
 const A={...P.A},B0={...P.A,...P.B};if(!P.A.af)B0.af=P.B.af||B0.a;A.af=P.A.af||A.a;if(!P.B.af&&!P.A.af)B0.af=B0.a;A.lf=P.A.lf||A.l;B0.lf=P.B.lf||(P.A.lf&&!P.B.l?P.A.lf:B0.l);
 const Bp=still==='B'?B0:A,B=still?Bp:B0,AA=still?Bp:A,dur=P.d||2.6,fast=dur<1.5;
 const anim=(type,a,b)=>a===b?'':fast
  ?`<animateTransform attributeName="transform" type="${type}" values="${a};${b};${a}" keyTimes="0;.5;1" calcMode="spline" keySplines=".42 0 .58 1;.42 0 .58 1" dur="${dur}s" repeatCount="indefinite"/>`
  :`<animateTransform attributeName="transform" type="${type}" values="${a};${b};${b};${a};${a}" keyTimes="0;.4;.5;.9;1" calcMode="spline" keySplines=".5 0 .3 1;0 0 1 1;.5 0 .3 1;0 0 1 1" dur="${dur}s" repeatCount="indefinite"/>`;
 const lg=(id,hi,mid,lo,x)=>`<linearGradient id="${u}${id}" gradientUnits="userSpaceOnUse" x1="${-x}" y1="0" x2="${x}" y2="0"><stop offset="0" stop-color="${lo}"/><stop offset=".3" stop-color="${mid}"/><stop offset=".58" stop-color="${hi}"/><stop offset=".86" stop-color="${mid}"/><stop offset="1" stop-color="${lo}"/></linearGradient>`;
 const defs=`<defs>${lg('n','#ffffff','#d5ddef','#7f8db3',6)}${lg('f','#c3cde5','#94a3c8','#55638a',6)}${lg('t','#ffffff','#dbe2f2','#8796bb',7)}
 <radialGradient id="${u}h" gradientUnits="userSpaceOnUse" cx="-1.5" cy="-12.5" r="11"><stop offset="0" stop-color="#ffffff"/><stop offset=".55" stop-color="#d9e0f0"/><stop offset="1" stop-color="#7f8db3"/></radialGradient>
 <linearGradient id="${u}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="var(--accent)"/><stop offset="1" stop-color="var(--accent-strong)"/></linearGradient>
 <linearGradient id="${u}m1" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3a4357"/><stop offset=".12" stop-color="#2a3142"/><stop offset="1" stop-color="#161b26"/></linearGradient>
 <linearGradient id="${u}m2" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#141922"/><stop offset=".5" stop-color="#2c3445"/><stop offset="1" stop-color="#121720"/></linearGradient>
 <radialGradient id="${u}spot" cx=".5" cy="0" r="1" fx=".5" fy="0"><stop offset="0" stop-color="#c9d6ff" stop-opacity=".2"/><stop offset=".6" stop-color="#8fa6ff" stop-opacity=".05"/><stop offset="1" stop-color="#8fa6ff" stop-opacity="0"/></radialGradient>
 <radialGradient id="${u}pool" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="var(--accent)" stop-opacity=".28"/><stop offset="1" stop-color="var(--accent)" stop-opacity="0"/></radialGradient>
 <linearGradient id="${u}fade" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".5"/><stop offset=".5" stop-color="#fff" stop-opacity="0"/></linearGradient>
 <mask id="${u}rm" maskUnits="userSpaceOnUse" x="0" y="113" width="160" height="40"><rect x="0" y="113" width="160" height="40" fill="url(#${u}fade)"/></mask>
 <filter id="${u}rim" x="-30%" y="-30%" width="160%" height="160%"><feDropShadow dx="0" dy="0" stdDeviation="1.6" flood-color="var(--accent)" flood-opacity=".75"/><feDropShadow dx="0" dy="3" stdDeviation="2.2" flood-color="#000" flood-opacity=".45"/></filter></defs>`;
 const db=/dumbbell|goblet|farmer|db\b/i.test(name)&&P.hand!==0,hp=P.prop||(db?'db':'');
 const build=(A1,B1,ghost)=>{
  const R=(a,b,inner)=>ghost?`<g transform="rotate(${a||0})">${inner}</g>`:`<g transform="rotate(${a||0})">${anim('rotate',a||0,b||0)}${inner}</g>`;
  const F=g=>ghost?'currentColor':`url(#${u}${g})`;
  const cap=(len,w,g)=>`<line x1="0" y1="0" x2="0" y2="${len}" stroke="${F(g)}" stroke-width="${w}" stroke-linecap="round"/>`;
  const handProp=ghost?'':hp==='db'?`<rect x="-6.5" y="11.6" width="13" height="2.2" rx="1.1" fill="#9aa6bd"/><rect x="-8.5" y="8.6" width="4.2" height="8.2" rx="1.8" fill="url(#${u}m1)" stroke="var(--accent)" stroke-width=".5"/><rect x="4.3" y="8.6" width="4.2" height="8.2" rx="1.8" fill="url(#${u}m1)" stroke="var(--accent)" stroke-width=".5"/>`
   :hp==='bar'?`<rect x="-18" y="11.2" width="36" height="2.6" rx="1.3" fill="#9aa6bd"/>`:hp==='handle'?`<rect x="-5" y="10.6" width="10" height="3.6" rx="1.8" fill="url(#${u}m1)" stroke="var(--accent)" stroke-width=".6"/>`:'';
  const arm=(a,b,far)=>R(a[0],b[0],`${cap(13,5.2,far?'f':'n')}<g transform="translate(0 13)">${R(a[1],b[1],`${cap(11,4.4,far?'f':'n')}<ellipse cx="0" cy="12.4" rx="2.5" ry="2.9" fill="${F(far?'f':'n')}"/>${handProp}`)}</g>`);
  const shoe=ghost?`<rect x="-3.4" y="-2.6" width="11" height="4.8" rx="2.4" fill="currentColor"/>`:`<path d="M-3.4 -2.4 Q-3.6 2.4 0 2.4 L7 2.4 Q8.6 2.4 8.4 0.6 Q8 -1.4 4 -2 L-1.2 -3 Z" fill="url(#${u}s)"/><path d="M-3.2 1.6 L8.2 1.6" stroke="#ffffff" stroke-opacity=".75" stroke-width=".9" stroke-linecap="round"/>`;
  const leg=(a,b,far)=>R(a[0],b[0],`${cap(16,7.6,far?'f':'n')}<g transform="translate(0 16)">${R(a[1],b[1],`${cap(15,6.2,far?'f':'n')}<g transform="translate(0 15.4)">${R(a[2]||0,b[2]||0,shoe)}</g>${P.plate&&!far&&!ghost?`<rect x="-11" y="18" width="22" height="3.2" rx="1.4" fill="url(#${u}m1)" stroke="var(--accent)" stroke-width=".6"/>`:''}`)}</g>`);
  const torso=inner=>R(A1.t,B1.t,inner);
  const head=`<g transform="translate(1 -27)"><rect x="-2" y="-3.4" width="4" height="5" rx="2" fill="${F('n')}"/><ellipse cx="0.8" cy="-10.6" rx="7" ry="8.2" fill="${ghost?'currentColor':`url(#${u}h)`}"/></g>`;
  const body=`<path d="M-7.2 -24 Q-7.4 -28 -3 -28.2 L3 -28.2 Q7.4 -28 7.2 -24 L5.6 -6 Q5.4 2.6 0 2.8 Q-5.4 2.6 -5.6 -6 Z" fill="${ghost?'currentColor':`url(#${u}t)`}"/>${ghost?'':`<path d="M-5.2 -5.2 Q0 -3.6 5.2 -5.2" stroke="var(--accent)" stroke-opacity=".55" stroke-width=".8" fill="none"/>`}${head}`;
  return `<g transform="translate(${A1.r[0]} ${A1.r[1]})">${ghost?'':anim('translate',A1.r.join(' '),B1.r.join(' '))}${torso(`<g transform="translate(0 -22)">${arm(A1.af,B1.af,1)}</g>`)}${leg(A1.lf,B1.lf,1)}${torso(body)}${leg(A1.l,B1.l,0)}${torso(`<g transform="translate(0 -22)">${arm(A1.a,B1.a,0)}</g>`)}</g>`;
 };
 const props=(P.p?P.p():'').replace(/fill="#596579"/g,`fill="url(#${u}m1)"`).replace(/fill="#46505f"/g,`fill="url(#${u}m2)"`).replace(/fill="var\(--accent\)" opacity="\.55"/g,`fill="url(#${u}m1)"`).replace(/<rect /g,`<rect rx="2" stroke="var(--accent)" stroke-opacity=".4" stroke-width=".6" `).replace(/<path /g,`<path stroke="var(--accent)" stroke-opacity=".4" stroke-width=".6" `);
 const lying=AA.r[1]>=94,sx0=AA.r[0],sx1=B.r[0];
 const sanim=sx0===sx1?'':`<animate attributeName="cx" values="${sx0};${sx1};${sx0}" dur="${dur}s" repeatCount="indefinite" calcMode="spline" keyTimes="0;.5;1" keySplines=".42 0 .58 1;.42 0 .58 1"/>`;
 const ghost=still?'':`<g color="var(--accent)" opacity=".16">${build(B0,B0,true)}</g>`;
 const fig=`<g id="${u}fig">${props}${ghost}<g filter="url(#${u}rim)">${build(AA,B,false)}</g></g>`;
 return `<svg class="gwr-art gx-demo-art" data-demo="${key}" role="img" aria-label="Animated demonstration: ${esc(name)}" viewBox="14 18 132 104" xmlns="http://www.w3.org/2000/svg">${defs}<rect x="0" y="0" width="160" height="113" fill="url(#${u}spot)"/><rect x="0" y="113" width="160" height="20" fill="#0a0d14" opacity=".55"/><ellipse cx="80" cy="113.5" rx="58" ry="7" fill="url(#${u}pool)"/>${fig}<use href="#${u}fig" transform="translate(0 227) scale(1 -1)" opacity=".22" mask="url(#${u}rm)"/><rect x="0" y="112.6" width="160" height=".8" fill="var(--accent)" opacity=".35"/><ellipse cx="${sx0}" cy="113.4" rx="${lying?28:13}" ry="2.2" fill="#000" opacity=".45">${sanim}</ellipse></svg>`;
}
function gwrDemoOpen(){
 const g=gwrCurrent(),root=document.getElementById('guidedWorkout');if(!g||!root)return;
 const st=gwrStepNext(g);if(!st)return;const d=planDay(g.date),c=state.plan.checks[g.date]||{},label=c.substitute?.[st.it.k]||st.it.l;
 root.querySelector('.gx-demo')?.remove();
 const el=document.createElement('div');el.className='gx-demo';
 el.innerHTML=`<div class="gx-demo-panel" role="dialog" aria-modal="true" aria-label="How to do ${esc(label)}"><div class="gx-demo-stage">${gwrDemoArt({l:label},d.kind,fxReduced())}</div><p class="gx-demo-kicker">How to do it</p><h3>${esc(label)}</h3><p class="gx-demo-rx">${esc(st.it.d)}</p><p class="gx-demo-cue">${esc(gwrCue(st.it,d))}</p><button type="button" class="gx-btn primary" onclick="this.closest('.gx-demo').remove()">Got it</button></div>`;
 el.addEventListener('click',e=>{if(e.target===el)el.remove()});root.appendChild(el);
 if(!fxReduced()){GWX.sfx('whoosh');haptic(8)}
}
function gwrBoot(){
  if(!document.getElementById('guidedWorkout')){
    const ov=document.createElement('div');ov.id='guidedWorkout';ov.setAttribute('role','dialog');ov.setAttribute('aria-modal','true');ov.setAttribute('aria-label','Guided workout');document.body.appendChild(ov);
  }
  document.getElementById('guidedWorkout')?.addEventListener('pointerdown',gwrFxRipple);
  {const ov=document.getElementById('guidedWorkout');if(ov){
    ov.addEventListener('pointerdown',gwrFxHoldStart);['pointerup','pointercancel','pointerleave'].forEach(n=>ov.addEventListener(n,gwrFxHoldEnd));
    ov.addEventListener('click',e=>{if(gwrFxHeld&&e.target.closest&&e.target.closest('.gx-stepper-row button')){e.stopPropagation();e.preventDefault();gwrFxHeld=false}},true);
    ov.addEventListener('contextmenu',e=>{if(e.target.closest&&e.target.closest('.gx-stepper-row button'))e.preventDefault()});
    try{new MutationObserver(()=>gwrFxWake(ov.classList.contains('open'))).observe(ov,{attributes:true,attributeFilter:['class']})}catch(e){}
    document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible'&&ov.classList.contains('open'))gwrFxWake(true)});
  }}
  document.addEventListener('keydown',e=>{if(e.key==='Escape'&&document.getElementById('guidedWorkout')?.classList.contains('open')){e.preventDefault();e.stopPropagation();const g=gwrCurrent();g?.phase==='complete'?gwrExitComplete():gwrPause()}},true);
  gwrFloatSync();
}
setTimeout(gw