al=null,gwrRpe=null,gwrAnimationTimer=null;
const GWR_VER=1;
function gwrCurrent(){
  const g=state.guidedWorkout;
  if(!g||!state.plan||g.planId!==state.plan.id||g.planStart!==state.plan.start)return null;
  return g;
}
function gwrElapsed(g){return Math.max(0,Number(g.elapsedMs)||0)+(g.unpausedAt?Math.max(0,Date.now()-g.unpausedAt):0)}
function gwrFmtDuration(ms){const sec=Math.floor(Math.max(0,ms)/1000),m=Math.floor(sec/60);return m>=60?`${Math.floor(m/60)}:${String(m%60).padStart(2,'0')}:${String(sec%60).padStart(2,'0')}`:`${String(m).padStart(2,'0')}:${String(sec%60).padStart(2,'0')}`}
function gwrPersist(){save();gwrFloatSync()}
function gwrStepEntries(g){
  const d=planDay(g.date);if(!d)return [];
  const c=state.plan.checks[g.date]||{},s=c.s||{},skip=g.skip||{};
  const list=[];
  d.items.forEach((it,ex)=>{
    for(let j=0;j<it.n;j++)list.push({it,ex,j,done:!!(s[it.k]||[])[j],skipped:!!skip[it.k]});
  });
  return list;
}
function gwrStepNext(g){return gwrStepEntries(g).find(x=>!x.done&&!x.skipped)||null}
function gwrStats(g){
  const all=gwrStepEntries(g),completed=all.filter(x=>x.done),needed=all.filter(x=>!x.it.opt),doneReq=needed.filter(x=>x.done);
  const c=state.plan.checks[g.date]||{},det=c.setDetails||{};
  let volume=0,logged=0;
  for(const st of completed){
    const value=(det[st.it.k]||[])[st.j];if(!value)continue;
    logged++;
    if(value.weight>0&&value.reps>0)volume+=value.weight*value.reps;
  }
  const skippedRequired=needed.some(x=>x.skipped&&!x.done);
  return {all,completed,needed,doneReq,logged,volume,skippedRequired,pct:needed.length?Math.round(doneReq.length/needed.length*100):100};
}
function gwrNumTarget(it){
  const raw=String(it.d||''),title=String(it.l||'');
  if(/(?:warm-up|cool-down)/i.test(title)){
    // Warm-ups described by a concrete time are timed; otherwise manual confirmation.
    const tm=raw.match(/(\d+(?:\.\d+)?)(?:\s*[–—-]\s*\d+(?:\.\d+)?)?\s*min(?:utes?)?\b/i);
    return tm?{kind:'minutes',label:'Time completed (min)',suggest:tm[1]}:{kind:'check',label:'Confirm warm-up',suggest:''};
  }
  const secs=raw.match(/(\d+(?:\.\d+)?)(?:\s*[–—-]\s*\d+(?:\.\d+)?)?\s*(?:seconds?|secs?|s\b)(?=\s|$|\/|per)/i);
  const mins=raw.match(/(\d+(?:\.\d+)?)(?:\s*[–—-]\s*\d+(?:\.\d+)?)?\s*min(?:utes?)?\b/i);
  const reps=raw.match(/(\d+(?:\.\d+)?)(?:\s*[–—-]\s*\d+(?:\.\d+)?)?\s*(?:reps?|repetitions?)\b/i);
  // Timed holds sometimes also prescribe repetitions; keep normal reps if explicit.
  if(reps)return {kind:'reps',label:'Actual reps',suggest:String(Number(reps[1]))};
  if(secs)return {kind:'seconds',label:'Time completed (sec)',suggest:String(Number(secs[1]))};
  if(mins)return {kind:'minutes',label:'Time completed (min)',suggest:String(Number(mins[1]))};
  if(it.min)return {kind:'minutes',label:'Time completed (min)',suggest:''};
  if(!it.wt)return {kind:'check',label:'Mark set completed',suggest:''};
  return {kind:'reps',label:'Actual reps',suggest:''};
}
function gwrRestLength(it,d){
  if(/warm|cool/i.test(it.l||''))return 15;
  if(d.kind==='strength')return 90;
  if(d.kind==='cardio')return 20;
  return 40;
}
function gwrSeedForDay(date){
  const d=planDay(date);return d&&d.kind!=='rest'&&d.items.length&&date<=getToday();
}
function gwrStart(date){
  if(!state.plan){toast('Choose a training plan first');navigate('plan');return}
  date=date||planSel||getToday();
  if(!gwrSeedForDay(date)){toast('Choose an available training day (not a future or rest day)');return}
  const running=gwrCurrent();
  if(running&&running.date===date){gwrResume();return}
  if(running&&!confirm('Start another session? Your completed sets will remain in the plan, but the previous session will no longer be resumable.'))return;
  state.guidedWorkout={version:GWR_VER,planId:state.plan.id,planStart:state.plan.start,date,phase:'intro',resumePhase:'intro',startedAt:Date.now(),elapsedMs:0,unpausedAt:null,restUntil:0,restLength:90,skip:{}};
  gwrPersist();gwrOpen();
}
function gwrChangeDate(date){
  const g=gwrCurrent();if(!g||g.phase!=='intro'||!gwrSeedForDay(date))return;
  if(g.date===date)return;
  g.date=date;g.skip={};g.restUntil=0;g.startedAt=Date.now();g.elapsedMs=0;g.unpausedAt=null;
  gwrPersist();gwrRender();
}
function gwrResume(){
 const g=gwrCurrent();if(!g){toast('No saved session to resume');return}
 if(g.phase==='paused'){
   g.phase=g.resumePhase||'ready';
   if(g.phase==='rest')g.restUntil=Date.now()+Math.max(1000,Number(g.restRemainingMs)||1000);
   if(g.phase==='timing')g.timerUntil=Date.now()+Math.max(1000,Number(g.timerRemainingMs)||1000);
 }
 if(g.phase==='active'||g.phase==='ready'||g.phase==='timing'||g.phase==='rest'||g.phase==='timer-done')g.unpausedAt=Date.now();
 if(g.phase==='rest'&&g.restUntil&&Date.now()>g.restUntil)g.phase='ready';
 if(g.phase==='timing'&&g.timerUntil&&Date.now()>=g.timerUntil){g.timerActualMs=g.timerTargetMs;g.phase='timer-done'}
 gwrPersist();gwrOpen();
}
function gwrOpen(){
  const root=document.getElementById('guidedWorkout');if(!root)return;
  root.classList.add('open');document.body.classList.add('noscroll');
  if(!fxReduced()){root.classList.remove('gx-opening');void root.offsetWidth;root.classList.add('gx-opening');setTimeout(()=>root.classList.remove('gx-opening'),800)}
  gwrRender();gwrTickStart();gwrFloatSync();
}
function gwrTickStart(){
  if(gwrInterval)clearInterval(gwrInterval);
  gwrInterval=setInterval(gwrTick,250);
}
function gwrTick(){
 const g=gwrCurrent(),root=document.getElementById('guidedWorkout');
 if(!g||!root?.classList.contains('open')){if(gwrInterval)clearInterval(gwrInterval);gwrInterval=null;return}
 const elapsed=root.querySelector('[data-gwr-elapsed]');if(elapsed)elapsed.textContent=gwrFmtDuration(gwrElapsed(g));
 if(g.phase==='timing'){
   const remaining=Math.max(0,g.timerUntil-Date.now());
   const clock=root.querySelector('[data-gwr-worktime]');if(clock)clock.textContent=gwrFmtDuration(remaining);gwrFxCountdown('work',Math.ceil(remaining/1000));
   const bar=root.querySelector('[data-gwr-workfill]');if(bar)bar.style.width=(100*(1-remaining/Math.max(1000,g.timerTargetMs))).toFixed(2)+'%';
   const ring=root.querySelector('[data-gwr-timer-ring]');if(ring)ring.style.strokeDashoffset=String(452.39*(remaining/Math.max(1000,g.timerTargetMs)));
   if(remaining<=0)gwrTimerFinished(false);
 }
 if(g.phase==='rest'){
   const left=Math.max(0,Math.ceil((g.restUntil-Date.now())/1000));
   const counter=root.querySelector('[data-gwr-rest]');if(counter)counter.textContent=gwrFmtDuration(left*1000);gwrFxCountdown('rest',left);
   const circ=root.querySelector('[data-gwr-rest-progress]');if(circ)circ.style.strokeDashoffset=String(452.39*(1-left/Math.max(1,g.restLength)));
   if(left===0)gwrRestDone();
 }
}
function gwrPause(){
 const g=gwrCurrent();if(!g)return;
 if(g.unpausedAt){g.elapsedMs+=Math.max(0,Date.now()-g.unpausedAt);g.unpausedAt=null}
 if(g.phase!=='paused'&&g.phase!=='complete'){
   g.resumePhase=g.phase;
   if(g.phase==='timing')g.timerRemainingMs=Math.max(1000,g.timerUntil-Date.now());
   if(g.phase==='rest')g.restRemainingMs=Math.max(1000,g.restUntil-Date.now());
   g.phase='paused';
 }
 const overlay=document.getElementById('guidedWorkout');if(overlay)overlay.classList.remove('open');
 document.body.classList.remove('noscroll');
 if(gwrInterval)clearInterval(gwrInterval);gwrInterval=null;
 gwrPersist();renderPlan();
 if(g.phase!=='complete')toast('Session paused. Your progress is saved.');
}
function gwrBegin(){
 const g=gwrCurrent();if(!g)return;
 if(!g.unpausedAt)g.unpausedAt=Date.now();
 if(!gwrStepNext(g)){gwrComplete();return}
 g.phase='ready';g.resumePhase='ready';
 gwrPersist();gwrRender();
}
function gwrRestDone(){
 const g=gwrCurrent();if(!g||g.phase!=='rest')return;
 g.phase='ready';g.resumePhase='ready';g.restUntil=0;g.restRemainingMs=0;
 gwrPersist();gwrRender();
 if(!fxReduced())haptic(10);
}
function gwrAdjustRest(delta){
  const g=gwrCurrent();if(!g||g.phase!=='rest')return;
  g.restUntil=Math.max(Date.now(),g.restUntil+delta*1000);
  g.restLength=Math.max(1,Math.round((g.restUntil-Date.now())/1000));
  gwrPersist();gwrTick();
}
function gwrPickRpe(num){
  gwrRpe=gwrRpe===num?null:num;
  document.querySelectorAll('#guidedWorkout .gwr-rpe-button').forEach(b=>{
    const selected=Number(b.dataset.rpe)===gwrRpe;b.classList.toggle('selected',selected);b.setAttribute('aria-pressed',String(selected));
  });
}
function gwrGo(){
 const g=gwrCurrent();if(!g||g.phase!=='ready')return;
 const st=gwrStepNext(g);if(!st){gwrComplete();return}
 const info=gwrNumTarget(st.it);
 if(info.kind==='seconds'||info.kind==='minutes'){
   const amt=Number(info.suggest);
   // Some plans specify a timer category with no numeric target. Avoid silently
   // inventing a prescription: ask for the duration before the countdown begins.
   if(!Number.isFinite(amt)||amt<=0){
     const entered=prompt('Set duration in '+(info.kind==='minutes'?'minutes':'seconds')+' before starting:',info.kind==='minutes'?'5':'30');
     if(entered===null)return;
     const n=Number(entered);
     if(!Number.isFinite(n)||n<=0||n>(info.kind==='minutes'?120:7200)){toast('Enter a valid duration');return}
     g.timerTargetMs=n*(info.kind==='minutes'?60000:1000);
   }else g.timerTargetMs=amt*(info.kind==='minutes'?60000:1000);
   g.timerStartedAt=Date.now();g.timerUntil=Date.now()+g.timerTargetMs;
   g.timerActualMs=null;g.phase='timing';g.resumePhase='timing';
 }else{g.phase='active';g.resumePhase='active'}
 if(!g.unpausedAt)g.unpausedAt=Date.now();
 gwrPersist();gwrRender();
 if(!fxReduced())haptic(8);
}
function gwrTimerFinished(early){
 const g=gwrCurrent();if(!g||g.phase!=='timing')return;
 g.timerActualMs=early?Math.max(1000,g.timerTargetMs-Math.max(0,g.timerUntil-Date.now())):g.timerTargetMs;
 g.timerUntil=0;g.timerRemainingMs=0;
 g.phase='timer-done';g.resumePhase='timer-done';
 gwrPersist();gwrRender();
 if(!fxReduced())haptic(early?12:[24,60,18]);
}
function gwrCancelInterval(){
 const g=gwrCurrent();if(!g||g.phase!=='timing')return;
 g.timerUntil=0;g.timerTargetMs=0;g.timerActualMs=0;
 g.phase='ready';g.resumePhase='ready';gwrPersist();gwrRender();
}
function gwrQuickReview(){
 const g=gwrCurrent(),root=document.getElementById('guidedWorkout');if(!g||!root)return;
 const already=root.querySelector('#gwrV60Queue');
 if(already){already.remove();return}
 const d=planDay(g.date),steps=gwrStepEntries(g),active=gwrStepNext(g);
 const div=document.createElement('div');div.id='gwrV60Queue';div.className='gwr-v60-queue-backdrop';
 const rows=d.items.map((it,i)=>{
   const count=steps.filter(x=>x.it.k===it.k&&x.done).length;
   const skipped=!!g.skip?.[it.k];
   const current=active?.it.k===it.k;
   return `<div class="gwr-v60-queue-row ${current?'is-current':''}"><b>${String(i+1).padStart(2,'0')}</b><span>${esc(it.l)}</span><small>${skipped?'SKIPPED':count+'/'+it.n}</small></div>`;
 }).join('');
 div.innerHTML=`<div class="gwr-v60-queue-panel" role="dialog" aria-modal="true" aria-label="Workout itinerary"><div class="gwr-v60-queue-top"><div><small>FULL MISSION</small><h3>${esc(d.label)}</h3></div><button type="button" onclick="document.getElementById('gwrV60Queue')?.remove()" aria-label="Close itinerary">×</button></div><div class="gwr-v60-queue-list">${rows}</div><button class="gwr-v60-btn main" onclick="document.getElementById('gwrV60Queue')?.remove()">RETURN TO SESSION</button></div>`;
 div.addEventListener('click',e=>{if(e.target===div)div.remove()});root.appendChild(div);
}
let gwr63RewardTimer=null;
function gwr63Reward(kind,subtitle,st){
 // v75: delegates to the AAA reward layer in 22-runtime.js (gwrFxSetDone / gwrFxExerciseCleared).
 const root=document.getElementById('guidedWorkout');
 if(!root||!root.classList.contains('open'))return;
 if(gwr63RewardTimer){clearTimeout(gwr63RewardTimer);gwr63RewardTimer=null}
 root.querySelectorAll('.gwr63-reward,.gx-clear,.gx-setchip').forEach(x=>x.remove());
 if(fxReduced()){haptic(kind==='section'?[15,45,25]:[12]);return}
 if(kind==='section')gwrFxExerciseCleared(st);else gwrFxSetDone(st);
}
function gwrLogSet(){
 const g=gwrCurrent();if(!g||!['active','timer-done'].includes(g.phase))return;
 const st=gwrStepNext(g);if(!st){gwrComplete();return}
 const desc=gwrNumTarget(st.it),check=state.plan.checks[g.date]=state.plan.checks[g.date]||{s:{},v:{},log:{}};
 const vals={date:g.date,kind:desc.kind,at:new Date().toISOString(),effort:gwrRpe};
 const input=document.getElementById('gwrActualValue');
 if(desc.kind!=='check'){
   let amount;
   if(g.phase==='timer-done'&&(desc.kind==='minutes'||desc.kind==='seconds')){
     amount=desc.kind==='minutes'?Math.round((Number(g.timerActualMs)||0)/6000)/10:Math.round((Number(g.timerActualMs)||0)/1000);
   }else{
     if(!input||input.value.trim()===''||!Number.isFinite(Number(input.value))||Number(input.value)<=0){toast('Enter what you actually completed');input?.focus();return}
     amount=Number(input.value);
   }
   if(!Number.isFinite(amount)||amount<=0||amount>(desc.kind==='reps'?500:desc.kind==='seconds'?7200:360)){toast('Please check the reps or time.');input?.focus();return}
   vals[desc.kind]=amount;
 }
 if(st.it.wt){
   const field=document.getElementById('gwrActualWeight');
   if(field&&field.value.trim()!==''){
     const weight=Number(field.value);
     if(!Number.isFinite(weight)||weight<0||weight>2000){toast('Enter a valid weight in pounds');field.focus();return}
     vals.weight=weight;check.v=check.v||{};check.v[st.it.k]=`${weight} lb`;
   }
 }
 check.setDetails=check.setDetails||{};
 (check.setDetails[st.it.k]=check.setDetails[st.it.k]||[])[st.j]=vals;
 planToggle(g.date,st.it.k,st.j,true);renderPlan();gwrRpe=null;
 g.timerTargetMs=0;g.timerActualMs=0;g.timerUntil=0;
 if(!gwrStepNext(g)){gwrComplete();return}
 const sec=gwrRestLength(st.it,planDay(g.date));
 g.phase='rest';g.resumePhase='rest';g.restLength=sec;g.restUntil=Date.now()+sec*1000;
 gwrPersist();gwrRender();
 const justCleared=(state.plan.checks[g.date]?.s?.[st.it.k]||[]).filter(Boolean).length>=st.it.n;
 gwr63Reward(justCleared?'section':'set',justCleared?'ALL SETS LOGGED · NEXT EXERCISE UNLOCKED':`SET ${st.j+1} / ${st.it.n} · RECORDED`,st);
}
function gwrSkipExercise(){
  const g=gwrCurrent();if(!g||!['ready','active','timer-done'].includes(g.phase))return;
  const st=gwrStepNext(g);if(!st)return;
  const action=st.it.opt?'Skip optional exercise':'Skip exercise';
  if(!confirm(`${action}: ${st.it.l}? Skipped required sets will not count toward full session XP.`))return;
  g.skip[st.it.k]=true;
  if(!gwrStepNext(g))gwrComplete();else{gwrPersist();gwrRender()}
}
function gwrSubstituteExercise(){
  const g=gwrCurrent();if(!g||!['ready','active','timer-done'].includes(g.phase))return;
  const st=gwrStepNext(g);if(!st)return;
  const c=state.plan.checks[g.date]=state.plan.checks[g.date]||{s:{},v:{},log:{}};
  c.substitute=c.substitute||{};
  const prev=c.substitute[st.it.k]||st.it.l;
  const label=prompt('Machine unavailable? Enter the substitution you are actually performing. It will be recorded under the original planned movement:',prev);
  if(label===null)return;
  const cleaned=label.trim().slice(0,85);if(!cleaned)return;
  c.substitute[st.it.k]=cleaned;
  gwrPersist();gwrRender();
}
function gwrUndoLast(){
  const g=gwrCurrent();if(!g)return;
  const check=state.plan.checks[g.date]||{},details=check.setDetails||{};
  const done=gwrStepEntries(g).filter(x=>x.done&&((details[x.it.k]||[])[x.j]));
  if(!done.length){toast('No guided set to undo');return}
  const st=done.sort((a,b)=>Date.parse((details[b.it.k]||[])[b.j]?.at||0)-Date.parse((details[a.it.k]||[])[a.j]?.at||0))[0];
  // Uncheck first to correctly revoke plan completion XP if necessary.
  planToggle(g.date,st.it.k,st.j,false);
  if(check.setDetails?.[st.it.k])delete check.setDetails[st.it.k][st.j];
  if(g.phase==='complete')g.phase='active';
  g.restUntil=0;g.phase='ready';g.resumePhase='ready';
  gwrPersist();gwrRender();toast('Last guided set undone');
}
function gwrCaptureHistory(g){
  const c=state.plan.checks[g.date]||{},st=gwrStats(g),d=planDay(g.date);
  const id=`${g.planId}|${g.planStart}|${g.date}`;
  const act=c.actId&&state.activities.find(a=>a.id===c.actId);
  const record={id,planId:g.planId,planStart:g.planStart,date:g.date,label:d?.label||'Workout',kind:d?.kind||'',startedAt:g.startedAt,finishedAt:Date.now(),elapsedMs:Math.round(gwrElapsed(g)),completedSets:st.completed.length,requiredDone:st.doneReq.length,requiredTotal:st.needed.length,volumeLb:Math.round(st.volume),xp:act?.xp||0,partial:st.doneReq.length<st.needed.length,exercises:[]};
  if(d)record.exercises=d.items.map(it=>({name:c.substitute?.[it.k]||it.l,prescribed:it.d,sets:((c.setDetails||{})[it.k]||[]).filter(Boolean).map(v=>({...v}))}));
  state.workoutHistory=state.workoutHistory||[];
  state.workoutHistory=state.workoutHistory.filter(x=>x.id!==id);
  state.workoutHistory.unshift(record);
  state.workoutHistory=state.workoutHistory.slice(0,120);
}
function gwrComplete(){
  const g=gwrCurrent();if(!g)return;
  if(g.unpausedAt){g.elapsedMs+=Math.max(0,Date.now()-g.unpausedAt);g.unpausedAt=null}
  g.phase='complete';g.resumePhase='complete';g.restUntil=0;
  gwrCaptureHistory(g);gwrPersist();gwrRender();
  if(!fxReduced())haptic([18,40,26]);
}
function gwrReopenPartial(){
  const g=gwrCurrent();if(!g||g.phase!=='complete')return;
  g.skip={};g.phase='ready';g.resumePhase='ready';g.unpausedAt=Date.now();
  gwrPersist();gwrRender();
}
function gwrEndEarly(){
  const g=gwrCurrent();if(!g)return;
  if(!confirm('Finish this session now? Completed sets are saved, but unfinished required sets will not earn session-completion XP.'))return;
  gwrComplete();
}
function gwrExitComplete(){
  const g=gwrCurrent();if(!g)return;
  if(g.phase!=='complete'){gwrPause();return}
  state.guidedWorkout=null;gwrPersist();
  const root=document.getElementById('guidedWorkout');if(root)root.classList.remove('open');
  document.body.classList.remove('noscroll');
  if(gwrInterval)clearInterval(gwrInterval);gwrInterval=null;
  renderPlan();gwrFloatSync();
}
// The workout plan launcher remains the single place to resume a saved session.
// No persistent floating resume button over the rest of the app.
function gwrFloatSync(){
  document.getElementById('gwrFloatingResume')?.remove();
}

function gwrPickMarkup(g){
  if(!state.plan)return '';
  const base=addDays(g.date,-((parseD(g.date).getDay()+6)%7));
  const cells=[];
  for(let i=0;i<7;i++){
    const date=addDays(base,i),d=planDay(date);
    if(!d||d.kind==='rest')continue;
    const available=date<=getToday();
    const selected=date===g.date;
    cells.push(`<button type="button" class="gwr-pick ${selected?'selected':''}" onclick="gwrChangeDate('${date}')" ${available?'':'disabled'} aria-pressed="${selected}"><div class="gwr-pick-date">${['MON','TUE','WED','THU','FRI','SAT','SUN'][i]} · ${parseD(date).getDate()}</div><div class="gwr-pick-title">${esc(d.label)}</div><div class="gwr-pick-meta">${d.items.reduce((n,it)=>n+it.n,0)} sets · ${dayDone(date)?'completed':available?'ready':'upcoming'}</div></button>`);
  }
  return cells.join('');
}
function gwrArt(it,kind){
  const txt=(it?.l||'').toLowerCase();
  const mode=kind==='cardio'||/treadmill|elliptical|cycling|cardio|endurance|walking|running/.test(txt)?'cardio':kind==='recovery'||/stretch|mobility|warm-up|cool-down/.test(txt)?'mobility':/leg press|squat|lunge|calf|step-up/.test(txt)?'legs':/pulldown|row|pull-up|face pull/.test(txt)?'pull':/plank|pallof|core/.test(txt)?'core':'press';
  const sky='var(--bg-elev)',metal='#596579',metalHi='#8391a6',accent='var(--accent)',accentHi='var(--accent-strong)',ink='#1b2431',skin='#dcb89a',cloth='#7593bb',shine='#a6ccff';
  let equipment='';
  if(mode==='legs')equipment=`<rect x="20" y="95" width="122" height="6" fill="${metal}"/><rect x="29" y="101" width="6" height="17" fill="${metal}"/><rect x="129" y="101" width="6" height="17" fill="${metal}"/><rect x="42" y="68" width="10" height="28" fill="${metalHi}"/><rect x="35" y="61" width="17" height="7" fill="${metal}"/><rect x="52" y="80" width="67" height="5" fill="${metalHi}"/><rect x="113" y="35" width="8" height="64" fill="${metal}"/><rect x="108" y="39" width="16" height="17" fill="${accent}"/><rect x="108" y="65" width="16" height="7" fill="${accent}"/><rect x="109" y="83" width="14" height="8" fill="${metalHi}"/><rect x="58" y="58" width="7" height="31" fill="${metal}"/><rect x="46" y="55" width="21" height="8" fill="${metalHi}"/>`;
  if(mode==='pull')equipment=`<rect x="37" y="18" width="8" height="91" fill="${metal}"/><rect x="125" y="18" width="8" height="91" fill="${metal}"/><rect x="37" y="17" width="96" height="7" fill="${metalHi}"/><rect x="60" y="30" width="46" height="5" fill="${accentHi}"/><rect x="79" y="24" width="7" height="35" fill="${metalHi}"/><rect x="34" y="108" width="104" height="7" fill="${metal}"/><rect x="53" y="85" width="52" height="7" fill="${metalHi}"/><rect x="59" y="92" width="6" height="16" fill="${metal}"/><rect x="99" y="92" width="6" height="16" fill="${metal}"/><rect x="128" y="62" width="13" height="31" fill="${ink}"/><rect x="128" y="70" width="13" height="6" fill="${accent}"/>`;
  if(mode==='press')equipment=`<rect x="22" y="96" width="119" height="7" fill="${metal}"/><rect x="31" y="103" width="7" height="13" fill="${metal}"/><rect x="128" y="103" width="7" height="13" fill="${metal}"/><rect x="54" y="66" width="56" height="7" fill="${metalHi}"/><rect x="60" y="73" width="7" height="23" fill="${metal}"/><rect x="99" y="73" width="7" height="23" fill="${metal}"/><rect x="28" y="44" width="111" height="6" fill="${metalHi}"/><rect x="36" y="36" width="10" height="22" fill="${ink}"/><rect x="46" y="39" width="9" height="16" fill="${accent}"/><rect x="110" y="39" width="9" height="16" fill="${accent}"/><rect x="119" y="36" width="10" height="22" fill="${ink}"/><rect x="84" y="43" width="5" height="22" fill="${metal}"/>`;
  if(mode==='cardio')equipment=`<rect x="24" y="96" width="115" height="9" fill="${metal}"/><rect x="32" y="105" width="7" height="13" fill="${metal}"/><rect x="124" y="105" width="7" height="13" fill="${metal}"/><rect x="120" y="42" width="9" height="55" fill="${metalHi}"/><rect x="103" y="37" width="25" height="17" fill="${ink}"/><rect x="108" y="41" width="13" height="8" fill="${accent}"/><rect x="92" y="63" width="32" height="5" fill="${metalHi}"/><rect x="32" y="87" width="94" height="7" fill="${metalHi}"/><rect x="43" y="90" width="54" height="4" fill="${accent}"/>`;
  if(mode==='core'||mode==='mobility')equipment=`<rect x="25" y="90" width="115" height="8" fill="${accent}" opacity=".73"/><rect x="28" y="98" width="109" height="5" fill="${metal}"/><rect x="35" y="103" width="91" height="4" fill="${ink}"/>`;
  let person='';
  if(mode==='legs')person=`<g class="gwr-art-motion"><rect x="69" y="37" width="16" height="17" fill="${skin}"/><rect x="68" y="32" width="18" height="8" fill="${ink}"/><rect x="65" y="51" width="22" height="26" fill="${cloth}"/><rect x="65" y="57" width="5" height="12" fill="${accentHi}"/><rect x="72" y="78" width="8" height="16" fill="${ink}"/><rect x="80" y="78" width="17" height="6" fill="${ink}"/><rect x="91" y="80" width="17" height="8" fill="${cloth}"/><rect x="68" y="54" width="6" height="15" fill="${skin}"/><rect x="59" y="65" width="11" height="6" fill="${skin}"/></g>`;
  if(mode==='pull')person=`<g class="gwr-art-motion"><rect x="75" y="51" width="15" height="16" fill="${skin}"/><rect x="74" y="46" width="17" height="7" fill="${ink}"/><rect x="70" y="66" width="24" height="23" fill="${cloth}"/><rect x="73" y="88" width="7" height="16" fill="${ink}"/><rect x="84" y="88" width="7" height="16" fill="${ink}"/><rect x="65" y="51" width="6" height="26" fill="${skin}"/><rect x="65" y="48" width="13" height="6" fill="${skin}"/><rect x="92" y="51" width="6" height="26" fill="${skin}"/><rect x="90" y="48" width="14" height="6" fill="${skin}"/></g>`;
  if(mode==='press')person=`<g class="gwr-art-motion"><rect x="75" y="48" width="17" height="17" fill="${skin}"/><rect x="73" y="44" width="20" height="7" fill="${ink}"/><rect x="70" y="65" width="29" height="20" fill="${cloth}"/><rect x="76" y="85" width="8" height="14" fill="${ink}"/><rect x="90" y="85" width="8" height="14" fill="${ink}"/><rect x="63" y="53" width="7" height="18" fill="${skin}"/><rect x="57" y="48" width="13" height="7" fill="${skin}"/><rect x="100" y="53" width="7" height="18" fill="${skin}"/><rect x="100" y="48" width="13" height="7" fill="${skin}"/></g>`;
  if(mode==='cardio')person=`<g class="gwr-art-motion"><rect x="61" y="37" width="17" height="17" fill="${skin}"/><rect x="60" y="33" width="19" height="8" fill="${ink}"/><rect x="58" y="54" width="25" height="24" fill="${cloth}"/><rect x="55" y="57" width="7" height="23" fill="${skin}"/><rect x="82" y="57" width="8" height="19" fill="${skin}"/><rect x="60" y="78" width="9" height="16" fill="${ink}"/><rect x="58" y="91" width="19" height="6" fill="${ink}"/><rect x="75" y="77" width="9" height="17" fill="${ink}"/><rect x="78" y="91" width="18" height="6" fill="${ink}"/></g>`;
  if(mode==='mobility'||mode==='core')person=`<g class="gwr-art-motion"><rect x="36" y="61" width="16" height="16" fill="${skin}"/><rect x="35" y="57" width="18" height="7" fill="${ink}"/><rect x="51" y="66" width="46" height="16" fill="${cloth}"/><rect x="96" y="69" width="12" height="11" fill="${ink}"/><rect x="108" y="77" width="8" height="14" fill="${ink}"/><rect x="116" y="87" width="11" height="7" fill="${ink}"/><rect x="52" y="80" width="7" height="11" fill="${skin}"/><rect x="46" y="88" width="14" height="5" fill="${skin}"/></g>`;
  return `<svg class="gwr-art" role="img" aria-label="Original pixel art exercise illustration" viewBox="0 0 160 130" preserveAspectRatio="xMidYMid meet" xmlns="http://www.w3.org/2000/svg" style="image-rendering:pixelated"><rect width="160" height="130" fill="transparent"/><g opacity=".3" fill="${metal}">${Array.from({length:7},(_,i)=>`<rect x="${20+i*20}" y="${12+i%2*3}" width="3" height="3"/>`).join('')}</g><rect x="11" y="117" width="139" height="3" fill="${metal}" opacity=".37"/><rect x="18" y="121" width="124" height="2" fill="${metal}" opacity=".17"/><g>${equipment}</g>${person}<g class="gwr-art-glint" fill="${accentHi}"><rect x="137" y="22" width="3" height="11"/><rect x="133" y="26" width="11" height="3"/><rect x="20" y="48" width="3" height="8"/><rect x="17" y="51" width="9" height="2"/></g><rect x="11" y="9" width="17" height="3" fill="${accent}"/><rect x="11" y="9" width="3" height="13" fill="${accent}"/><rect x="132" y="9" width="17" height="3" fill="${accent}"/><rect x="146" y="9" width="3" height="13" fill="${accent}"/></svg>`;
}
function gwrH