/* ---------- v106 · Bottom bar: looks as it always did; slide a thumb along it to run through the tabs ----------
   The finger picks a tab (a full-width swipe covers them all). The bar just shows that tab as the active one and
   scrolls only when it has to keep it in view. The page follows as soon as the finger slows (and when it lifts),
   so fast swipes stay light; nothing is animated while scrubbing. Taps work as before, with a short slide.
   Phones only (<= 860px wide). The page animation is lmaNavTo in 34-navigation.js. */
const LMA_SCRUB={slop:5,settle:100,gain:.92,fast:.16};
/* how far a finger has to travel along the bar: 1 is the old pace (the bar's full width reaches the end tab), higher means the same movement gets you through more tabs */
const LMA_SCRUB_GAINS={normal:1,fast:1.8,faster:2.6};
function lmaScrubGain(){const k=lmaLoad().scrubSpeed;return LMA_SCRUB_GAINS[k]||LMA_SCRUB_GAINS.fast}
function lmaSetScrubSpeed(k){lmaLoad().scrubSpeed=LMA_SCRUB_GAINS[k]?k:'fast';lmaSave();toast('Bar scrubbing: '+lmaLoad().scrubSpeed)}
const lmaScrub={sb:null,items:[],drag:null,sel:-1,timer:0,block:0,cost:{},veil:null,veilOn:false};
const lmaScrubOn=()=>!!(window.matchMedia&&matchMedia('(max-width:860px)').matches);
/* a tick for each tab passed, firmer pulses at the start and end of a scrub (system haptics through the Android app) */
function lmaBuzz(kind){
 try{
  if(!fxOpt().haptics)return;
  const N=lmaNative()&&lmaPlugin('NavHaptics');
  if(N&&N[kind]){N[kind]().catch(()=>{});return}
  haptic(kind==='tick'?6:kind==='start'?10:14);
 }catch(e){}
}
/* while scrubbing, a blurred picture of the tab you are over fills the page area; the real page replaces it when you rest or let go.
   The pictures are small snapshots the Android app takes of each tab a moment after you visit it (kept between launches). A tab
   you have never opened yet shows a soft tinted blur until the first visit. */
const LMA_THUMB_KEY='lifemax_thumbs_v1';
let lmaThumbs={},lmaThumbTimer=0,lmaThumbBusy=false;
try{lmaThumbs=JSON.parse(localStorage.getItem(LMA_THUMB_KEY)||'{}')||{}}catch(e){lmaThumbs={}}
function lmaThumbSave(){try{localStorage.setItem(LMA_THUMB_KEY,JSON.stringify(lmaThumbs))}catch(e){}}
let lmaThumbErr='',lmaThumbWarmBusy=false;
async function lmaThumbSnapNow(v){
 if(window.LifeMaxJournalLock&&LifeMaxJournalLock.on()&&lmaView()==='history')return false;     /* never keep a picture of a locked Journal */
 const P=lmaNative()&&lmaPlugin('PageThumb');
 if(!P){lmaThumbErr='The snapshot helper is not in this build';return false}
 try{
  const r=await P.snap({div:4});
  if(r&&r.img&&r.img.length>400){lmaThumbs[v]=r.img;lmaThumbSave();lmaThumbErr='';return true}
  lmaThumbErr='The snapshot came back empty';
 }catch(e){lmaThumbErr=String((e&&e.message)||e).slice(0,160)}
 return false;
}
function lmaThumbSchedule(v){
 clearTimeout(lmaThumbTimer);
 lmaThumbTimer=setTimeout(async()=>{
  const S=lmaScrub;
  if(!lmaNative()||lmaThumbBusy||lmaThumbWarmBusy||S.veilOn||S.drag||document.visibilityState!=='visible'||lmaTabKey(lmaView())!==v)return;
  lmaThumbBusy=true;
  try{await lmaThumbSnapNow(v)}catch(e){}
  lmaThumbBusy=false;try{lmaThumbRender()}catch(e){}
 },lmaSwipeNav?2600:1100);
}
/* one pass through every tab so each has a real screenshot (runs once after install, or from Settings); touching the screen stops it */
async function lmaThumbWarm(force){
 const S=lmaScrub;
 if(!lmaNative()||lmaThumbWarmBusy||S.drag)return;
 lmaScrubItems();
 const views=S.items.map(b=>b.dataset.view),todo=views.filter(v=>force||!lmaThumbs[v]);
 if(!todo.length)return;
 lmaThumbWarmBusy=true;const home=lmaView();let stop=false;
 const bar=document.createElement('div');bar.className='thumbwarm';bar.innerHTML='<i></i>';document.body.appendChild(bar);
 const halt=()=>{stop=true};document.addEventListener('touchstart',halt,{once:true,passive:true,capture:true});
 let n=0,fails=0;
 for(const v of todo){
  if(stop||document.visibilityState!=='visible')break;
  lmaNavTo(v,{fast:true,skip:true});
  await new Promise(r=>setTimeout(r,650));
  if(stop)break;
  const ok=await lmaThumbSnapNow(v);n++;bar.firstChild.style.width=Math.round(n/todo.length*100)+'%';
  if(!ok&&++fails>=2)break;
 }
 document.removeEventListener('touchstart',halt,true);bar.remove();
 lmaNavTo(home,{fast:true,skip:true});
 lmaThumbWarmBusy=false;lmaThumbRender();
}
function lmaThumbRender(){
 const el=document.getElementById('lmaThumbBody');if(!el)return;
 if(!lmaNative()){el.innerHTML='<div class="note">Tab previews are made in the Android app.</div>';return}
 const have=Object.keys(lmaThumbs).length,tot=lmaScrub.items.length||14;
 el.innerHTML='<div class="settingsline"><div style="flex:1"><strong>'+have+' of '+tot+' tabs captured</strong><div class="muted" style="font-size:12px">'+(lmaThumbErr?'Last problem: '+esc(lmaThumbErr):'These are the blurred pictures shown while you scrub the bottom bar. Each tab updates when you visit it.')+'</div></div><button class="btn small" '+(lmaThumbWarmBusy?'disabled ':'')+'onclick="lmaThumbWarm(true)">'+(lmaThumbWarmBusy?'Working…':'Capture all')+'</button></div>';
}
/* thin white outline icons for the veil (24 x 24, drawn with a 1.6 px stroke) */
const LMA_TAB_ICONS={
 dashboard:'<rect x="3.5" y="3.5" width="7" height="7" rx="1.5"/><rect x="13.5" y="3.5" width="7" height="7" rx="1.5"/><rect x="3.5" y="13.5" width="7" height="7" rx="1.5"/><rect x="13.5" y="13.5" width="7" height="7" rx="1.5"/>',
 character:'<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4.4 3.6-7 8-7s8 2.6 8 7"/>',
 pet:'<path d="M5 9 4 3l5 3h6l5-3-1 6c1 2 1 4 0 6-1.5 3-4.500 5-7 5s-5.500-2-7-5c-1-2-1-4 0-6z"/><circle cx="9.500" cy="12" r=".6"/><circle cx="14.500" cy="12" r=".6"/><path d="M11 15h2l-1 1z"/>',
 log:'<circle cx="12" cy="12" r="9"/><path d="M12 7.500v9M7.500 12h9"/>',
 quests:'<path d="M5 21V4"/><path d="M5 4h11l-2 4 2 4H5"/>',
 plan:'<rect x="4.500" y="3.500" width="15" height="17" rx="2"/><path d="M8.500 9h7M8.500 13h7M8.500 17h4"/>',
 media:'<path d="M12 6C10 4.500 7 4 4 4v14c3 0 6 .5 8 2 2-1.500 5-2 8-2V4c-3 0-6 .5-8 2z"/><path d="M12 6v14"/>',
 meals:'<path d="M3.500 11h17a8.500 8.500 0 0 1-17 0z"/><path d="M8 4.500c0 2 2 2 2 4M13 3.500c0 2 2 2 2 5"/>',
 recipes:'<path d="M7 14a4 4 0 1 1 1.500-7.700A4 4 0 0 1 16 6.500a4 4 0 0 1 1 7.500V19H7z"/><path d="M7 16.500h10"/>',
 money:'<circle cx="12" cy="12" r="9"/><path d="M14.500 9.500C14 8.500 13 8 12 8c-1.500 0-2.500.8-2.500 2s1 1.700 2.500 2 2.500.8 2.500 2-1 2-2.500 2c-1.100 0-2.100-.5-2.600-1.500M12 6v2M12 16v2"/>',
 history:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.500 2"/>',
 settings:'<circle cx="12" cy="12" r="3"/><circle cx="12" cy="12" r="7.500"/><path d="M12 2v2.500M12 19.500V22M2 12h2.500M19.500 12H22M4.900 4.900l1.800 1.800M17.300 17.300l1.800 1.800M4.900 19.100l1.800-1.800M17.300 6.700l1.800-1.800"/>',
 apps:'<rect x="7" y="2.500" width="10" height="19" rx="2.500"/><path d="M10.500 18.500h3"/>',
 food:'<path d="M3.500 11h17a8.500 8.500 0 0 1-17 0z"/><path d="M8 4.500c0 2 2 2 2 4M13 3.500c0 2 2 2 2 5"/>',
 library:'<path d="M12 6C10 4.500 7 4 4 4v14c3 0 6 .5 8 2 2-1.500 5-2 8-2V4c-3 0-6 .5-8 2z"/><path d="M12 6v14"/>',
 'life-director':'<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5.500"/><path d="M12 12V7.500M12 12l3.500 2"/><path d="M12 3v1.500M21 12h-1.500M12 21v-1.500M3 12h1.500"/>'
};
function lmaTabIcon(v){return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">'+(LMA_TAB_ICONS[v]||'<circle cx="12" cy="12" r="8"/>').replace(/<(\w+)/g,'<$1 vector-effect="non-scaling-stroke"')+'</svg>'}
function lmaVeil(show){
 const S=lmaScrub;if(!S.veil)return;
 if(show===S.veilOn)return;S.veilOn=show;
 S.veil.classList.toggle('on',show);
}
function lmaVeilSet(i){
 const S=lmaScrub,b=S.items[i];if(!b||!S.veil)return;
 const v=b.dataset.view,img=S.veil.querySelector('.sv-img'),t=lmaThumbs[v];
 if(t){if(img.getAttribute('src')!==t)img.src=t;img.style.display=''}else{img.style.display='none';img.removeAttribute('src')}
 let h=0;for(let k=0;k<v.length;k++)h=(h*31+v.charCodeAt(k))%360;
 S.veil.style.setProperty('--sv-hue',h);
 S.veil.querySelector('.sv-ico').innerHTML=lmaTabIcon(v);
 S.veil.querySelector('.sv-name').textContent=b.textContent.trim();
}
function lmaScrubItems(){const S=lmaScrub;S.items=S.sb?[...S.sb.querySelectorAll('.navbtn[data-view]')].filter(b=>b.offsetWidth>0):[];return S.items}
function lmaScrubIndex(v){v=lmaTabKey(v);return lmaScrub.items.findIndex(b=>b.dataset.view===v)}
/* nudge the strip just enough that tab i is fully visible */
function lmaScrubReveal(i){
 const S=lmaScrub,b=S.items[i];if(!b)return;
 if(S.drag){cancelAnimationFrame(lmaBarRaf);lmaBarRaf=0}else if(lmaBarRaf)return;     /* the bar is already gliding to the tab */
 const l=b.offsetLeft,r=l+b.offsetWidth,sl=S.sb.scrollLeft,w=S.sb.clientWidth;
 if(l-10<sl)S.sb.scrollLeft=Math.max(0,l-10);else if(r+10>sl+w)S.sb.scrollLeft=r+10-w;
}
/* show tab i as the current one on the bar right away (the real navigation follows) */
function lmaScrubMark(i){
 const S=lmaScrub;S.sel=i;
 for(let j=0;j<S.items.length;j++)S.items[j].classList.toggle('active',j===i);
 lmaScrubReveal(i);
}
function lmaScrubSync(v){const S=lmaScrub;if(!S.sb||(S.drag&&S.drag.moved))return;lmaScrubItems();const i=lmaScrubIndex(v||lmaView());if(i>=0){S.sel=i;lmaScrubReveal(i)}}
function lmaScrubInit(){
 const S=lmaScrub,sb=document.querySelector('.sidebar');
 if(!sb||S.sb)return;
 S.sb=sb;lmaScrubItems();
 S.veil=document.createElement('div');S.veil.className='scrubveil';S.veil.setAttribute('aria-hidden','true');S.veil.innerHTML='<img class="sv-img" alt="" decoding="sync"><div class="sv-mid"><div class="sv-ico"></div><div class="sv-name"></div></div>';document.body.appendChild(S.veil);
 /* switch the page to the tab the bar shows. The bar has already painted; the page work runs right after a frame,
    and how long each page takes is remembered, so heavy pages wait for the finger to rest longer */
 const go=()=>{
  const b=S.items[S.sel];if(!b)return;const v=b.dataset.view;if(v===lmaTabKey(lmaView()))return;
  if(S.drag&&S.drag.speed>LMA_SCRUB.fast){clearTimeout(S.timer);S.timer=setTimeout(go,60);return}   /* still moving quickly: keep gliding, switch when it eases */
  requestAnimationFrame(()=>setTimeout(()=>{
   if(!S.drag||S.items[S.sel]!==b)return;
   const t0=performance.now();lmaNavTo(v,{skip:true,fast:true});lmaScrubMark(S.sel);
   requestAnimationFrame(()=>requestAnimationFrame(()=>{const c=performance.now()-t0;S.cost[v]=S.cost[v]?S.cost[v]*.4+c*.6:c;if(S.drag&&S.items[S.sel]===b)lmaVeil(false)}));
  },0));
 };
 const settleFor=i=>{const b=S.items[i],c=b&&S.cost[b.dataset.view];return c?Math.max(LMA_SCRUB.settle,Math.min(360,Math.round(c*1.4))):LMA_SCRUB.settle};
 sb.addEventListener('touchstart',e=>{
  if(!lmaScrubOn()||e.touches.length!==1)return;
  const t=e.touches[0];lmaScrubItems();
  const st=Math.max(0,lmaScrubIndex(lmaView()));
  const r=sb.getBoundingClientRect();
  S.drag={x0:t.clientX,y0:t.clientY,start:st,moved:false,from:lmaView(),W:Math.max(200,r.width),L:r.left};
  S.sel=st;
 },{passive:true});
 sb.addEventListener('touchmove',e=>{
  const d=S.drag;if(!d||e.touches.length!==1)return;
  const t=e.touches[0],dx=t.clientX-d.x0,n=S.items.length;
  if(!d.moved){
   if(Math.abs(dx)<LMA_SCRUB.slop)return;
   if(Math.abs(t.clientY-d.y0)>Math.abs(dx)*1.5){S.drag=null;return}
   d.moved=true;lmaBuzz('start');lmaVeilSet(S.sel>=0?S.sel:d.start);
  }
  e.preventDefault();
  const nowT=performance.now();if(d.lt){const dt=Math.max(1,nowT-d.lt);d.speed=(d.speed||0)*.5+Math.abs(t.clientX-d.lx)/dt*.5}d.lt=nowT;d.lx=t.clientX;
  /* the whole bar is the whole scrub: your starting point is the tab you are on, the left edge is the first tab and the
     right edge the last, with the stretch in between spread evenly on each side */
  const W=d.W,x0=Math.min(W-40,Math.max(40,d.x0-d.L)),x=Math.min(W,Math.max(0,x0+(t.clientX-d.L-x0)*lmaScrubGain()));
  const f=x<=x0?d.start*(x/x0):d.start+(n-1-d.start)*((x-x0)/(W-x0));
  const i=Math.round(Math.min(n-1,Math.max(0,f)));
  if(i!==S.sel){
   lmaScrubMark(i);lmaBuzz('tick');lmaVeilSet(i);lmaVeil(true);
   clearTimeout(S.timer);S.timer=setTimeout(go,settleFor(i));
  }
 },{passive:false});
 const end=()=>{
  const d=S.drag;if(!d)return;S.drag=null;
  clearTimeout(S.timer);
  if(!d.moved)return;
  S.block=Date.now();lmaBuzz('end');
  const b=S.items[S.sel];if(!b)return;
  if(b.dataset.view!==lmaTabKey(lmaView()))lmaNavTo(b.dataset.view,{skip:true,fast:true});
  requestAnimationFrame(()=>requestAnimationFrame(()=>lmaVeil(false)));
  if(b.dataset.view!==d.from&&lmaNavStack[lmaNavStack.length-1]!==d.from)lmaNavStack.push(d.from);
 };
 sb.addEventListener('touchend',end,{passive:true});
 sb.addEventListener('touchcancel',end,{passive:true});
 /* a tap on a tab: same destination, with the slide; swallow the click that ends a scrub */
 document.addEventListener('click',e=>{
  const b=e.target.closest&&e.target.closest('.sidebar .navbtn[data-view]');if(!b)return;
  if(Date.now()-S.block<400){e.stopPropagation();e.preventDefault();return}
  if(!lmaScrubOn())return;
  e.stopPropagation();e.preventDefault();
  lmaNavTo(b.dataset.view);
 },true);
 window.addEventListener('resize',()=>lmaScrubSync());
}
(function lmaScrubBoot(){
 const _nav=navigate;
 navigate=function(v){
  const r=_nav.apply(this,arguments);
  try{
   const g=lmaTabKey(v);
   if(g==='library')state.libPane=v;if(g==='food')state.foodPane=v;
   /* grouped pages, and the pages reached from the Overview (Character), light up their tab on the bar */
   const lit=g!==v?g:null;
   if(lit)document.querySelectorAll('.navbtn').forEach(b=>b.classList.toggle('active',b.dataset.view===lit));
   document.querySelectorAll('.libseg button').forEach(b=>b.classList.toggle('on',b.dataset.lib===v));
   lmaScrubSync(v);lmaThumbSchedule(lmaTabKey(lmaView()));
  }catch(e){}
  return r;
 };
 const go=()=>{try{lmaScrubInit()}catch(e){console.warn('[bottom bar]',e)}};
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',go);else go();
 setTimeout(()=>{try{lmaScrubSync();lmaThumbSchedule(lmaTabKey(lmaView()));lmaThumbRender()}catch(e){}},1200);
 setTimeout(()=>{try{if(lmaNative()&&!lmaScrub.drag)lmaThumbWarm(false)}catch(e){}},9000);
 const _nav2=navigate;navigate=function(v){const r=_nav2.apply(this,arguments);if(v==='settings')try{lmaThumbRender()}catch(e){}return r};
})();
