/* ---------- Haptic strength: Settings > Haptics lets you choose Light, Normal, Strong or Max (and turn it off). In the Android app the
   strength is real (the phone's vibrator motor at a chosen amplitude; needs the build that has the Vibrate permission and the
   updated NavHaptics plugin). Everywhere else, and in older builds, Strong and Max make the buzzes longer, since a browser cannot
   ask for harder. ---------- */
const HAP_LEVELS={
 light:{label:'Light',dm:.7,add:0,amp:90,min:4},
 normal:{label:'Normal',dm:1,add:0,amp:150,min:5},
 strong:{label:'Strong',dm:1.7,add:8,amp:230,min:16},
 max:{label:'Max',dm:2.5,add:16,amp:255,min:28}
};
let hapNative=null;                    /* null: not asked yet, true: the phone's vibrator is ours, false: use the browser */
function hapLevelKey(){const k=(fxOpt().hapticLevel||'normal');return HAP_LEVELS[k]?k:'normal'}
function hapScale(p,L){
 const one=function(d){return Math.min(HAP_MAX,Math.max(L.min,Math.round(d*L.dm+L.add)))};
 return Array.isArray(p)?p.map(function(d,i){return i%2===0?one(d):d}):one(+p||10);
}
const HAP_MAX=260;
function hapNav(){try{return typeof lmaNative==='function'&&lmaNative()?lmaPlugin('NavHaptics'):null}catch(e){return null}}
function hapSend(p,L){
 const N=hapNav();
 if(N&&hapNative!==false){
  try{
   const q=Array.isArray(p)?p:[p],r=N.pattern({timings:q,amp:L.amp});
   if(r&&r.then)r.then(function(){hapNative=true}).catch(function(){hapNative=false;try{navigator.vibrate&&navigator.vibrate(p)}catch(e){}});
   return;
  }catch(e){hapNative=false}
 }
 try{navigator.vibrate&&navigator.vibrate(p)}catch(e){}
}
haptic=function(p){
 if(!fxOpt().haptics)return;
 const L=HAP_LEVELS[hapLevelKey()],sc=hapScale(p,L);
 hapSend(sc,L);
};
/* the bottom bar: a system tick at Normal; at the other strengths, a motor pulse of the chosen strength */
(function(){
 const lb=lmaBuzz;
 lmaBuzz=function(kind){
  try{
   if(!fxOpt().haptics)return;
   const k=hapLevelKey();
   if(k==='normal')return lb.apply(this,arguments);
   const base=kind==='tick'?6:kind==='start'?10:14;haptic(base);
  }catch(e){}
 };
})();
function hapPlay(name){
 const pats={tap:[12],double:[14,50,14],success:[18,30,18],big:[40,40,40,60,160]};
 haptic(pats[name]||[12]);
}
function hapSet(patch){state.fx=Object.assign({},fxOpt(),patch);try{save()}catch(e){}hapRender()}
function hapRender(){
 const el=document.getElementById('hapBody');if(!el)return;
 const o=fxOpt(),k=hapLevelKey();el.textContent='';
 const row=document.createElement('div');row.className='settingsline';
 row.innerHTML='<div style="flex:1"><strong>Haptic feedback</strong><div class="muted" style="font-size:12px">Little buzzes when you earn XP, change tabs, pet the cat and so on.</div></div>';
 const cb=document.createElement('input');cb.type='checkbox';cb.className='check';cb.checked=!!o.haptics;cb.addEventListener('change',function(){hapSet({haptics:cb.checked})});row.appendChild(cb);el.appendChild(row);
 const seg=document.createElement('div');seg.style.cssText='display:flex;gap:6px;margin:10px 0 8px;flex-wrap:wrap';seg.setAttribute('role','group');seg.setAttribute('aria-label','Haptic strength');
 Object.keys(HAP_LEVELS).forEach(function(key){const b=document.createElement('button');b.type='button';b.className='btn small'+(key===k?'':' secondary');b.textContent=HAP_LEVELS[key].label;b.setAttribute('aria-pressed',key===k?'true':'false');b.disabled=!o.haptics;b.addEventListener('click',function(){hapSet({hapticLevel:key});hapPlay('double')});seg.appendChild(b)});
 el.appendChild(seg);
 const t=document.createElement('div');t.style.cssText='display:flex;gap:6px;flex-wrap:wrap';
 [['tap','Tap'],['double','Double tap'],['success','XP gain'],['big','Level up']].forEach(function(x){const b=document.createElement('button');b.type='button';b.className='btn small secondary';b.textContent='Try: '+x[1];b.disabled=!o.haptics;b.addEventListener('click',function(){hapPlay(x[0])});t.appendChild(b)});
 el.appendChild(t);
 const n=document.createElement('div');n.className='muted';n.style.cssText='font-size:12px;margin-top:8px';
 n.textContent=hapNative===true?'Strength is set on the phone’s vibration motor, so Strong and Max are properly harder.':hapNav()?(hapNative===false?'This build of the app cannot set the motor strength yet, so Strong and Max make the buzzes longer. Install the newest app build for real strength.':'Checking what this phone can do…'):'In a browser, Strong and Max make the buzzes longer. In the Android app they also get harder.';
 el.appendChild(n);
}
(function hapBoot(){
 const nav=navigate;navigate=function(v){const r=nav.apply(this,arguments);if(v==='settings')try{hapRender()}catch(e){}return r};
 setTimeout(function(){
  try{hapRender()}catch(e){}
  const N=hapNav();if(N&&hapNative===null){try{const r=N.pulse({ms:1,amp:1});if(r&&r.then)r.then(function(){hapNative=true;hapRender()}).catch(function(){hapNative=false;hapRender()})}catch(e){hapNative=false}}
 },2200);
})();
window.LifeMaxHaptics=Object.freeze({levels:HAP_LEVELS,scale:function(p,k){return hapScale(p,HAP_LEVELS[k])},play:hapPlay,native:function(){return hapNative},reset:function(){hapNative=null}});
