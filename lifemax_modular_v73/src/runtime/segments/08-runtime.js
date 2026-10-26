evokeAudioUrl(){
  if(audioCtx.objectUrl){try{URL.revokeObjectURL(audioCtx.objectUrl)}catch(e){}audioCtx.objectUrl=null}
}
function closeAudioPlayer(reset){
  const el=document.getElementById('audioEl');
  if(el){el.pause();el.removeAttribute('src');el.load()}
  revokeAudioUrl();
  audioCtx.mediaId=null;
  const bar=document.getElementById('audioPlayer');
  if(bar){bar.classList.remove('show','minimized')}
  const minBtn=document.getElementById('audioMinBtn');
  if(minBtn){minBtn.textContent='—';minBtn.title='Minimize player';minBtn.setAttribute('aria-label','Minimize player')}
  if(reset){/* stay closed */}
}
function toggleMinimizeAudioPlayer(){
  const bar=document.getElementById('audioPlayer');
  if(!bar||!bar.classList.contains('show'))return;
  const min=bar.classList.toggle('minimized');
  const minBtn=document.getElementById('audioMinBtn');
  if(minBtn){
    minBtn.textContent=min?'□':'—';
    minBtn.title=min?'Expand player':'Minimize player';
    minBtn.setAttribute('aria-label',min?'Expand player':'Minimize player');
  }
  updateAudioPlayBtn();
}
function expandAudioPlayer(){
  const bar=document.getElementById('audioPlayer');
  if(!bar)return;
  bar.classList.remove('minimized');
  const minBtn=document.getElementById('audioMinBtn');
  if(minBtn){minBtn.textContent='—';minBtn.title='Minimize player';minBtn.setAttribute('aria-label','Minimize player')}
}
async function playMediaAudio(id, fromStart){
  const item=mediaById(id);
  if(!item||!item.audio){toast('Attach an audio file first');return}
  const el=document.getElementById('audioEl');
  const bar=document.getElementById('audioPlayer');
  if(!el||!bar)return;
  try{
    // If already loaded this item, just play
    if(audioCtx.mediaId===id&&el.src){
      bar.classList.add('show');
      expandAudioPlayer();
      await el.play();
      updateAudioPlayBtn();
      return;
    }
    closeAudioPlayer(true);
    const rec=await audioDbGet(id);
    if(!rec||!rec.blob){
      toast('Audio file missing — attach it again');
      delete item.audio;save();
      return;
    }
    revokeAudioUrl();
    audioCtx.objectUrl=URL.createObjectURL(rec.blob);
    audioCtx.mediaId=id;
    el.src=audioCtx.objectUrl;
    document.getElementById('audioPlayerTitle').textContent=item.title||rec.name||'Audiobook';
    document.getElementById('audioPlayerSub').textContent=(item.audio.name||rec.name||'')+' · '+fmtBytes(item.audio.size||rec.size||0);
    bar.classList.add('show');
    expandAudioPlayer();
    const resume=(!fromStart && item.audio.position>5)?item.audio.position:0;
    const onMeta=()=>{
      el.removeEventListener('loadedmetadata', onMeta);
      if(item.audio){item.audio.duration=el.duration||item.audio.duration||0}
      if(resume && resume < (el.duration||Infinity)-2){el.currentTime=resume}
      updateAudioTimes();
      updateMediaAudioProgress(id,el.currentTime,el.duration);
      el.play().then(updateAudioPlayBtn).catch(()=>{updateAudioPlayBtn();toast('Tap Play to start (browser blocked autoplay)')});
    };
    el.addEventListener('loadedmetadata', onMeta);
    el.load();
  }catch(err){
    console.warn(err);
    toast('Could not play this file in this browser');
  }
}
function updateAudioPlayBtn(){
  const el=document.getElementById('audioEl');
  const btn=document.getElementById('audioPlayBtn');
  const mini=document.getElementById('audioMiniPlayBtn');
  if(!el)return;
  const label=el.paused?'Play':'Pause';
  const glyph=el.paused?'▶':'❚❚';
  if(btn)btn.textContent=label;
  if(mini){mini.textContent=glyph;mini.title=label;mini.setAttribute('aria-label',label)}
}
function audioTogglePlay(){
  const el=document.getElementById('audioEl');if(!el||!el.src)return;
  if(el.paused)el.play().then(updateAudioPlayBtn).catch(()=>toast('Playback blocked'));
  else{el.pause();updateAudioPlayBtn()}
  persistAudioPosition();
}
function audioSkip(sec){
  const el=document.getElementById('audioEl');if(!el||!el.src)return;
  el.currentTime=Math.max(0,Math.min((el.duration||0), el.currentTime+sec));
  updateAudioTimes();persistAudioPosition(true);
}
function audioSetRate(v){
  const el=document.getElementById('audioEl');if(!el)return;
  el.playbackRate=Number(v)||1;
}
function audioSeekInput(v){
  audioCtx.seeking=true;
  const el=document.getElementById('audioEl');if(!el||!el.duration)return;
  const t=(Number(v)/1000)*el.duration;
  document.getElementById('audioTimeCur').textContent=fmtAudioTime(t);
}
function audioSeekChange(v){
  const el=document.getElementById('audioEl');if(!el||!el.duration)return;
  el.currentTime=(Number(v)/1000)*el.duration;
  audioCtx.seeking=false;
  updateAudioTimes();if(audioCtx.mediaId)updateMediaAudioProgress(audioCtx.mediaId,el.currentTime,el.duration);persistAudioPosition(true);
}
function updateAudioTimes(){
  const el=document.getElementById('audioEl');if(!el)return;
  const cur=el.currentTime||0, dur=el.duration||0;
  document.getElementById('audioTimeCur').textContent=fmtAudioTime(cur);
  document.getElementById('audioTimeDur').textContent=dur?fmtAudioTime(dur):'—';
  if(!audioCtx.seeking){
    const seek=document.getElementById('audioSeek');
    if(seek&&dur)seek.value=Math.round((cur/dur)*1000);
  }
}
function persistAudioPosition(force){
  const id=audioCtx.mediaId;if(!id)return;
  const el=document.getElementById('audioEl');if(!el)return;
  const item=mediaById(id);if(!item||!item.audio)return;
  item.audio.position=el.currentTime||0;
  if(el.duration)item.audio.duration=el.duration;
  const write=()=>{
    try{state.customRules=rules;localStorage.setItem(STORE,JSON.stringify(state))}catch(e){}
  };
  if(force){clearTimeout(audioCtx.saveTimer);write();return}
  clearTimeout(audioCtx.saveTimer);
  audioCtx.saveTimer=setTimeout(write, 1200);
}
function wireAudioElement(){
  const el=document.getElementById('audioEl');if(!el||el._wired)return;
  el._wired=true;
  el.addEventListener('timeupdate',()=>{updateAudioTimes();if(audioCtx.mediaId)updateMediaAudioProgress(audioCtx.mediaId,el.currentTime,el.duration);persistAudioPosition()});
  el.addEventListener('play', updateAudioPlayBtn);
  el.addEventListener('pause', ()=>{updateAudioPlayBtn();persistAudioPosition(true)});
  el.addEventListener('ended', ()=>{
    updateAudioPlayBtn();
    const id=audioCtx.mediaId;
    const item=mediaById(id);
    if(!item||!item.audio)return;
    // The native ended event means playback genuinely reached the file's end.
    // Mark this book complete exactly once; do not reset progress to 0.
    const duration=Number.isFinite(el.duration)?el.duration:(Number(item.audio.duration)||0);
    item.audio.duration=duration;
    item.audio.position=duration||el.currentTime||0;
    if(item.status!=='finished'){
      item.status='finished';
      item.finishedAt=new Date().toISOString();
      item.updatedAt=item.finishedAt;
      save();
      toast('Book finished · '+item.title);
    }else{
      save();
    }
    updateMediaAudioProgress(id,item.audio.position,duration);
    renderMedia();
  });
  el.addEventListener('error', ()=>toast('This browser cannot decode that audio file'));
}
function mediaAudioButtons(x){
  if(x.audio&&x.audio.name){
    const pos=x.audio.position?(' · resume '+fmtAudioTime(x.audio.position)):'';
    const dur=x.audio.duration?(' / '+fmtAudioTime(x.audio.duration)):'';
    return `<div class="media-audio-row">
      <button type="button" class="btn small" onclick="playMediaAudio('${x.id}')">▶ Play</button>
      <button type="button" class="btn small secondary" onclick="pickAudioForMedia('${x.id}')">Replace file</button>
      <button type="button" class="btn small secondary" onclick="removeMediaAudio('${x.id}')">Remove audio</button>
    </div>
    <div class="media-audio-meta">${esc(x.audio.name)} · ${fmtBytes(x.audio.size)}${pos}${dur}</div>`;
  }
  return `<div class="media-audio-row"><button type="button" class="btn small secondary" onclick="pickAudioForMedia('${x.id}')">🎧 Attach .m4b / audio</button></div>`;
}


function renderRecipes(){
 const list=document.getElementById('recipesList');if(!list)return;
 const q=(document.getElementById('recipeSearch')?.value||'').trim().toLowerCase();
 const cat=document.getElementById('recipeCategory')?.value||'all';
 const hits=RECIPES.filter(r=>(!r.cls||r.cls===state.classId)&&(cat==='all'||r.cat===cat)&&(!q||[r.n,r.cat,...r.ingredients].join(' ').toLowerCase().includes(q)));
 list.innerHTML=hits.length?hits.map(r=>`<article class="card" style="margin-bottom:0"><div style="display:flex;justify-content:space-between;align-items:flex-start;gap:10px"><div><div class="eyebrow">${esc(r.cat)}</div><h3 style="margin:4px 0 6px">${esc(r.n)}</h3><div class="muted" style="font-size:12px">${esc(r.yield)} · Prep ${esc(r.prep)} · Total ${esc(r.total)}</div></div><span class="tag">${r.kcal} kcal${r.cls?' (est.)':''}</span></div><div class="grid stats" style="grid-template-columns:repeat(3,minmax(0,1fr));margin:14px 0"><div class="card" style="padding:10px"><div class="statlabel">Protein</div><div class="statvalue" style="font-size:18px">${r.p}g</div></div><div class="card" style="padding:10px"><div class="statlabel">Carbs</div><div class="statvalue" style="font-size:18px">${r.c}g</div></div><div class="card" style="padding:10px"><div class="statlabel">Fat</div><div class="statvalue" style="font-size:18px">${r.f}g</div></div></div><details><summary style="cursor:pointer;font-weight:700;font-size:13px;padding:4px 0 10px">Ingredients & instructions</summary><strong style="font-size:12px">Ingredients</strong><ul style="padding-left:20px;margin:6px 0 12px;font-size:12.5px;color:var(--text-dim)">${r.ingredients.map((x,i)=>`<li style="margin:5px 0;list-style:none"><button type="button" class="ingredient-pick" onclick="selectRecipeIngredient('${r.id}',${i},this)" aria-pressed="false" style="font:inherit;text-align:left;color:inherit;background:var(--surface-2,rgba(127,127,127,.08));border:1px solid var(--border);border-radius:8px;padding:8px 10px;width:100%;cursor:pointer">${esc(x)} <span style="float:right;opacity:.7">${r.id==='rcp_pesto_chicken'&&i===0?'🛒':'＋'}</span></button></li>`).join('')}</ul><div id="recipeProduct-${r.id}" style="display:none;margin:0 0 12px"></div><strong style="font-size:12px">Instructions</strong><ol style="padding-left:20px;margin:6px 0 10px;font-size:12.5px;color:var(--text-dim)">${r.steps.map(x=>`<li style="margin:4px 0">${esc(x)}</li>`).join('')}</ol>${r.note?`<p class="muted" style="font-size:12px">${esc(r.note)}</p>`:''}</details><button class="btn" style="width:100%;margin-top:10px" onclick="addRecipeToMeal('${r.id}')">＋ Log this recipe</button><p class="muted" style="font-size:11px;margin:8px 0 0">Nutrition per serving, as listed in the cookbook.</p></article>`).join(''):'<div class="empty">No recipes match that search.</div>';
}
function selectRecipeIngredient(recipeId,index,clicked){
 const recipe=RECIPES.find(r=>r.id===recipeId);if(!recipe)return;
 const productLinks={'rcp_pesto_chicken':{0:'https://www.walmart.com/ip/27935840?sid=edfc41aa-c731-47b8-9fec-6d99adbba129'}};
 const host=document.getElementById('recipeProduct-'+recipeId);if(!host)return;
 const url=productLinks[recipeId]?.[index];
 const label=recipe.ingredients[index]||'ingredient';
 document.querySelectorAll('.ingredient-pick').forEach(b=>{b.style.outline='';b.setAttribute('aria-pressed','false')});
 if(clicked){clicked.style.outline='2px solid var(--accent)';clicked.setAttribute('aria-pressed','true')}
 host.style.display='block';
 host.innerHTML=url?`<div class="card" style="padding:12px;display:flex;align-items:center;justify-content:space-between;gap:10px"><div><strong style="font-size:13px">${esc(label)}</strong><div class="muted" style="font-size:11px;margin-top:3px">Walmart product link attached</div></div><a class="btn" href="${url}" target="_blank" rel="noopener noreferrer" style="white-space:nowrap;text-decoration:none">Open in Walmart ↗</a></div>`:`<div class="card" style="padding:10px;font-size:12px"><strong>${esc(label)}</strong><div class="muted" style="margin-top:3px">No Walmart product link added for this ingredient yet.</div></div>`;
}
function addRecipeToMeal(id){
 const f=RECIPE_FOODS.find(x=>x.id===id);if(!f)return;
 navigate('meals');fuelOpenLab();mealPick=f;
 document.getElementById('mealSearch').value=f.n;
 document.getElementById('mealGrams').value=f.port[0].g;
 document.getElementById('mealAddBtn').disabled=false;
 const row=document.getElementById('mealPortionRow'),sel=document.getElementById('mealPortion');
 row.style.display='block';sel.innerHTML=`<option value="${f.port[0].g}">1 serving (${f.port[0].g}g, cookbook nutrition)</option>`;sel.value=String(f.port[0].g);
 mealSearch();document.getElementById('mealAddBtn').scrollIntoView({behavior:'smooth',block:'center'});
}
function mealsEnsure(){
  if(!state.foodLog)state.foodLog=[];
  if(!state.customFoods)state.customFoods=[];
  if(!state.mealsMeta)state.mealsMeta={calGoal:0};
}
function allFoods(){
  mealsEnsure();
  return FOOD_DB.concat(state.customFoods||[],RECIPE_FOODS.filter(f=>!f.cls||f.cls===state.classId));
}
function foodById(id){return allFoods().find(f=>f.id===id)}
function mealsDayKey(d){return d||mealsDay||getToday()}
function setMealsDay(d){if(d!==mealsDay)fuelCancelMotion();mealsDay=d;renderMeals();fuelDayTransition()}
function mealsGoToday(){setMealsDay(getToday())}
function mealsShiftDay(n){
  const d=new Date((mealsDay||getToday())+'T12:00:00');
  d.setDate(d.getDate()+n);
  setMealsDay(d.toLocaleDateString('en-CA'));
}
let usdaFoodsCache=Object.create(null), mealSearchSerial=0;
const USDA_DB_NAME='lifemax_usda_branded_v1';
function usdaDb(){return new Promise((resolve,reject)=>{if(!('indexedDB' in window)){reject(new Error('This browser does not support local food database storage.'));return}const req=indexedDB.open(USDA_DB_NAME,1);req.onupgradeneeded=()=>{const db=req.result;if(!db.objectStoreNames.contains('foods')){const st=db.createObjectStore('foods',{keyPath:'id'});st.createIndex('terms','terms',{multiEntry:true});st.createIndex('name','n')}};req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error||new Error('Could not open local database'))})}
function usdaStatus(msg){const el=document.getElementById('usdaImportStatus');if(el)el.textContent=msg}
async function usdaCount(){try{const db=await usdaDb();const n=await new Promise((res,rej)=>{const r=db.transaction('foods').objectStore('foods').count();r.onsuccess=()=>res(r.result);r.onerror=()=>rej(r.error)});db.close();return n}catch(e){return 0}}
async function importUsdaFoods(ev){
 const file=ev.target.files&&ev.target.files[0];ev.target.value='';if(!file)return;
 if(!('DecompressionStream' in window)){usdaStatus('This browser cannot import .gz files. Try current Chrome.');return}
 if(!/\.gz$/i.test(file.name)){usdaStatus('Choose the compact USDA .jsonl.gz file.');return}
 let db;try{db=await usdaDb();usdaStatus('Opening compressed USDA file…');const txDone=tx=>new Promise((res,rej)=>{tx.oncomplete=res;tx.onerror=()=>rej(tx.error);tx.onabort=()=>rej(tx.error||new Error('Import cancelled'))});
 const stream=file.stream().pipeThrough(new DecompressionStream('gzip'));const reader=stream.getReader();const decoder=new TextDecoder();let carry='',batch=[],count=0,lastUpdate=0;
 async function flush(){if(!batch.length)return;const tx=db.transaction('foods','readwrite');const st=tx.objectStore('foods');for(const f of batch){f.terms=[...new Set((f.n+' '+(f.brand||'')+' '+(f.cat||'')).toLowerCase().match(/[a-z0-9]+/g)||[])];st.put(f)}await txDone(tx);batch=[]}
 while(true){const {value,done}=await reader.read();if(done)break;carry+=decoder.decode(value,{stream:true});let lines=carry.split('\n');carry=lines.pop()||'';for(const line of lines){if(!line.trim())continue;try{const f=JSON.parse(line);if(!f.id||!f.n)continue;f.port=(f.serving>0&&String(f.servingUnit||'g').toLowerCase()==='g')?[{l:f.household||'1 serving',g:f.serving}]:[];batch.push(f);count++;if(batch.length>=400)await flush()}catch(e){}}if(count-lastUpdate>=5000){lastUpdate=count;usdaStatus('Importing USDA foods… '+count.toLocaleString()+' processed');}}
 carry+=decoder.decode();if(carry.trim()){try{const f=JSON.parse(carry);if(f.id&&f.n){f.terms=[...new Set((f.n+' '+(f.brand||'')+' '+(f.cat||'')).toLowerCase().match(/[a-z0-9]+/g)||[])];f.port=(f.serving>0&&String(f.servingUnit||'g').toLowerCase()==='g')?[{l:f.household||'1 serving',g:f.serving}]:[];batch.push(f)}}catch(e){}}await flush();db.close();const total=await usdaCount();usdaStatus('USDA database ready · '+total.toLocaleString()+' foods stored on this device');toast('USDA food database imported');mealSearch();
 }catch(err){if(db)db.close();console.error(err);usdaStatus('Import did not finish: '+(err.message||'storage or file error'));toast('USDA import failed — check available device storage')}
}
function renderMealHits(hits){const box=document.getElementById('mealSearchResults');if(!box)return;box.classList.add('show');box.innerHTML=hits.length?hits.map(f=>`<button type="button" class="meal-hit ${mealPick&&mealPick.id===f.id?'on':''}" onclick="selectMealFood('${f.id}')">${mealGlyph(f.cat,f.n,'mh-ic')}<span class="mh-txt"><strong>${esc(f.n)}</strong><small>${esc(f.cat||'Food')}${f.brand?' · '+esc(f.brand):''} · ${foodEnergyLabel(f)} / 100g · P ${f.p||0} · C ${f.c||0} · F ${f.f||0}</small></span></button>`).join(''):'<div class="empty" style="padding:16px">No matches. Add a custom food.</div>'}
async function mealSearch(){
 const q=(document.getElementById('mealSearch')?.value||'').trim().toLowerCase();const box=document.getElementById('mealSearchResults');if(!box)return;const serial=++mealSearchSerial;
 if(q.length<1){box.classList.remove('show');box.innerHTML='';return}
 const local=allFoods().filter(f=>(f.n||'').toLowerCase().includes(q)||(f.cat||'').toLowerCase().includes(q)).slice(0,12);renderMealHits(local);
 try{const db=await usdaDb();const tx=db.transaction('foods','readonly');const idx=tx.objectStore('foods').index('terms');const req=idx.getAll(IDBKeyRange.bound(q,q+'\uffff'),35);const found=await new Promise((res,rej)=>{req.onsuccess=()=>res(req.result||[]);req.onerror=()=>rej(req.error)});db.close();if(serial!==mealSearchSerial)return;const seen=new Set(local.map(f=>f.id));const branded=[];for(const f of found){if(!seen.has(f.id)){seen.add(f.id);usdaFoodsCache[f.id]=f;branded.push(f)}}renderMealHits(local.concat(branded).slice(0,40))}catch(e){/* Local bundled foods still work if IndexedDB is unavailable. */}
}
function selectMealFood(id){
  mealPick=usdaFoodsCache[id]||foodById(id);
  if(!mealPick)return;
  document.getElementById('mealSearch').value=mealPick.n;
  document.getElementById('mealAddBtn').disabled=false;
  const ports=mealPick.port||[];
  const row=document.getElementById('mealPortionRow');
  const sel=document.getElementById('mealPortion');
  if(ports.length){
    row.style.display='block';
    sel.innerHTML='<option value="">100 g (default)</option>'+ports.map(p=>`<option value="${p.g}">${esc(p.l)} (${p.g}g)</option>`).join('');
  }else{row.style.display='none';sel.innerHTML=''}
  mealSearch();
}
function mealPortionPicked(){
  const g=document.getElementById('mealPortion').value;
  if(g)document.getElementById('mealGrams').value=g;
}
function foodEnergyInfo(f){
  const stated=Math.max(0,Number(f.kcal)||0);
  // Some bundled USDA Foundation records omit energy while carrying valid macros.
  // Use a transparent 4/4/9 macro estimate only in that specific missing-data case.
  const fromMacros=Math.max(0,(Number(f.p)||0)*4+(Number(f.c)||0)*4+(Number(f.f)||0)*9);
  const estimated=stated<=0&&fromMacros>0;
  return {value:estimated?fromMacros:stated,estimated};
}
function foodEnergyLabel(f){
  const e=foodEnergyInfo(f);
  return (e.estimated?'≈':'')+Math.round(e.value)+' kcal'+(e.estimated?' (est.)':'');
}
function scaleFood(f, grams){
  const m=(Number(grams)||0)/100;
  const r=k=>Math.round((Number(f[k])||0)*m*10)/10;
  const energy=foodEnergyInfo(f);
  return {kcal:Math.round(energy.value*m),kcalEstimated:energy.estimated,p:r('p'),c:r('c'),f:r('f'),fib:r('fib'),sug:r('sug'),na:r('na')};
}
function addMealEntry(){
  mealsEnsure();
  if(!mealPick){toast('Pick a food first');return}
  const grams=Math.max(1,Number(document.getElementById('mealGrams').value)||100);
  const slot=document.getElementById('mealSlot').value||'snacks';
  const s=scaleFood(mealPick, grams);
  const day=mealsDayKey();
  const fuelPreviousEnergy=dayFoodLog(day).reduce((a,e)=>a+(Number(e.kcal)||0),0);
  state.foodLog.push({
    id:'food_'+Date.now()+'_'+Math.random().toString(36).slice(2,6),
    date:day,
    slot,
    foodId:mealPick.id,
    name:mealPick.n,
    grams,
    ...s,
    at:new Date().toISOString()
  });
  mealPick=null;
  document.getElementById('mealSearch').value='';
  document.getElementById('mealSearchResults').classList.remove('show');
  document.getElementById('mealAddBtn').disabled=true;
  document.getElementById('mealPortionRow').style.display='none';
  save();
  toast(`Logged · ${s.kcal} kcal`);
  renderMeals();
  fuelAfterLog(slot,s,fuelPreviousEnergy);
}
function deleteMealEntry(id){
  fuelCancelMotion();
  mealsEnsure();
  state.foodLog=state.foodLog.filter(x=>x.id!==id);
  save();renderMeals();toast('Removed');
}
function showCustomFood(){var el=document.getElementById('customFoodCard');if(!el)return;el.style.display='block';el.classList.add('show')}
function hideCustomFood(){var el=document.getElementById('customFoodCard');if(!el)return;el.style.display='none';el.classList.remove('show')}
function saveCustomFood(){
  mealsEnsure();
  const name=(document.getElementById('cfName').value||'').trim();
  if(!name){toast('Name required');return}
  const f={
    id:'custom_'+Date.now(),
    n:name,
    cat:'Custom',
    kcal:Number(document.getElementById('cfKcal').value)||0,
    p:Number(document.getElementById('cfP').value)||0,
    c:Number(document.getElementById('cfC').value)||0,
    f:Number(document.getElementById('cfF').value)||0,
    fib:0,sug:0,na:0,
    port:[]
  };
  state.customFoods.unshift(f);
  hideCustomFood();
  save();
  selectMealFood(f.id);
  toast('Custom food saved');
}
function saveMealsGoal(){
  mealsEnsure();
  state.mealsMeta.calGoal=Math.max(0,Number(document.getElementById('mealsCalGoal').value)||0);
  save();renderMeals();
}
function dayFoodLog(day){
  mealsEnsure();
  return state.foodLog.filter(x=>x.date===day);
}


let mealSheetSlot='breakfast', mealSheetPick=null;
function openMealSheet(slot){
  mealSheetSlot=slot||'snacks';
  mealSheetPick=null;
  const names={breakfast:'Breakfast',lunch:'Lunch',dinner:'Dinner',snacks:'Snacks'};
  const title=document.getElementById('mealSheetTitle');
  if(title)title.textContent='Add to '+ (names[mealSheetSlot]||mealSheetSlot);
  const search=document.getElementById('msSearch');
  if(search)search.value='';
  const grams=document.getElementById('msGrams');
  if(grams)grams.value=100;
  document.getElementById('msPortionField').style.display='none';
  document.getElementById('msAddBtn').disabled=true;
  document.body.classList.add('noscroll');
  renderMealSheetList('');
  sheetOpen('mealSheet');
  setTimeout(()=>{if(search)search.focus()},430);
}
function closeMealSheet(){
  sheetLeave('mealSheet');
  document.getElementById('mealSheet').classList.remove('show');
  document.body.classList.remove('noscroll');
  mealSheetPick=null;
}
function recentFoods(limit){
  mealsEnsure();
  const seen=new Map();
  // newest first from foodLog
  const logs=[...state.foodLog].sort((a,b)=>new Date(b.at||b.date)-new Date(a.at||a.date));
  for(const e of logs){
    const id=e.foodId||('name:'+e.name);
    if(seen.has(id))continue;
    const f=e.foodId?foodById(e.foodId):null;
    seen.set(id,{
      id:e.foodId||id,
      n:e.name,
      cat:f?f.cat:'Recent',
      kcal:f?f.kcal: (e.grams?Math.round((e.kcal||0)*100/e.grams):e.kcal||0),
      p:f?f.p:0,c:f?f.c:0,f:f?f.f:0,
      port:f?f.port:[],
      gramsHint:e.grams||100,
      recent:true
    });
    if(seen.size>=(limit||12))break;
  }
  return Array.from(seen.values());
}
function renderMealSheetList(q){
  const box=document.getElementById('mealSheetList');
  if(!box)return;
  q=(q||'').trim().toLowerCase();
  let html='';
  const recent=recentFoods(12);
  if(!q && recent.length){
    html+='<div class="meal-sheet-sec">Recent</div>';
    html+=recent.map(f=>mealSheetHitHtml(f,true)).join('');
  }
  const pool=allFoods();
  const hits=q?pool.filter(f=>(f.n||'').toLowerCase().includes(q)||(f.cat||'').toLowerCase().includes(q)).slice(0,50)
    :pool.slice(0,30);
  html+='<div class="meal-sheet-sec">'+(q?'Results':'Browse')+'</div>';
  html+=hits.length?hits.map(f=>mealSheetHitHtml(f,false)).join(''):'<div class="empty" style="padding:20px">No matches.</div>';
  box.innerHTML=html;
}
function mealSheetHitHtml(f,isRecent){
  const on=mealSheetPick&&mealSheetPick.id===f.id?'on':'';
  const meta=`${esc(f.cat||'Food')} · ${foodEnergyLabel(f)}/100g · P ${f.p||0} C ${f.c||0} F ${f.f||0}${isRecent&&f.gramsHint?` · last ${f.gramsHint}g`:''}`;
  return `<button type="button" class="meal-hit ${on}" onclick="mealSheetSelect('${String(f.id).replace(/'/g,"\\'")}')">${mealGlyph(f.cat,f.n,'mh-ic')}<span class="mh-txt"><strong>${esc(f.n)}</strong><span class="meal-hit-meta">${meta}</span></span></button>`;
}
function mealSheetSearch(){
  renderMealSheetList(document.getElementById('msSearch').value);
}
function mealSheetSelect(id){
  // resolve from all foods or recent synthetic
  let f=foodById(id);
  if(!f){
    const r=recentFoods(30).find(x=>x.id===id);
    if(r)f=r;
  }
  if(!f)return;
  mealSheetPick=f;
  document.getElementById('msAddBtn').disabled=false;
  if(f.gramsHint)document.getElementById('msGrams').value=f.gramsHint;
  const ports=f.port||[];
  const field=document.getElementById('msPortionField');
  const sel=document.getElementById('msPortion');
  if(ports.length){
    field.style.display='block';
    sel.innerHTML='<option value="">100 g</option>'+ports.map(p=>`<option value="${p.g}">${esc(p.l)} (${p.g}g)</option>`).join('');
  }else{field.style.display='none';sel.innerHTML=''}
  renderMealSheetList(document.getElementById('msSearch').value);
}
function msPortionPicked(){
  const g=document.getElementById('msPortion').value;
  if(g)document.getElementById('msGrams').value=g;
}
function mealSheetAdd(){
  mealsEnsure();
  if(!mealSheetPick){toast('Pick a food');return}
  const grams=Math.max(1,Number(document.getElementById('msGrams').value)||100);
  // Prefer DB food for accurate macros
  let f=foodById(mealSheetPick.id)||mealSheetPick;
  // If only recent name without DB, scale from per100 if present
  const s=scaleFood(f, grams);
  const day=mealsDayKey();
  const fuelPreviousEnergy=dayFoodLog(day).reduce((a,e)=>a+(Number(e.kcal)||0),0);
  state.foodLog.push({
    id:'food_'+Date.now()+'_'+Math.random().toString(36).slice(2,6),
    date:day,
    slot:mealSheetSlot,
    foodId:f.id,
    name:f.n,
    grams,
    ...s,
    at:new Date().toISOString()
  });
  closeMealSheet();
  save();
  toast(`${f.n} · ${s.kcal} kcal`);
  renderMeals();
  fuelAfterLog(mealSheetSlot,s,fuelPreviousEnergy);
}
document.add