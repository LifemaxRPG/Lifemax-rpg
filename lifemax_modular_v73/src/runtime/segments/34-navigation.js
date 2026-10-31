/* v89 · Navigation: Android Back button / gesture, and swipe left/right between tabs.
   Native Back calls window.lmaBack() (see MainActivity); in a browser nothing here changes behaviour except swipe. */
const LMA_SWIPE={minPx:72,minFrac:.2,ratio:2.2,maxMs:700,edge:36,slop:14};
let lmaNavStack=[],lmaNavSkip=false,lmaSw=null,lmaHintTimer=0,lmaSwipeNav=false;
function lmaView(){const v=document.querySelector('.view.active');return v?v.id.replace(/^view-/,''):''}
/* Media and Journal live together under the Library tab */
const LMA_GROUPS={library:['media','history'],food:['meals','recipes']};
const lmaTabKey=v=>v==='media'||v==='history'?'library':v==='meals'||v==='recipes'?'food':v;
const lmaResolve=v=>v==='library'?((state&&state.libPane)||'media'):v==='food'?((state&&state.foodPane)||'meals'):v;
/* slide the bottom bar so the tab you are on is in the middle. One mover only: the browser's own smooth scrollIntoView used to fight the
   bar's other scroll adjustments, which made the tabs jump about while you swiped through several. */
let lmaBarRaf=0;
function lmaBarTo(btn){
 const sb=document.querySelector('.sidebar');if(!sb||!btn||sb.scrollWidth<=sb.clientWidth+2)return;
 const to=Math.max(0,Math.min(sb.scrollWidth-sb.clientWidth,btn.offsetLeft-(sb.clientWidth-btn.offsetWidth)/2)),from=sb.scrollLeft;
 cancelAnimationFrame(lmaBarRaf);lmaBarRaf=0;
 if(Math.abs(to-from)<2){return}
 if(typeof lmaMotionOff==='function'&&lmaMotionOff()){sb.scrollLeft=to;return}
 const t0=performance.now(),D=170,step=t=>{const k=Math.min(1,(t-t0)/D),e=1-Math.pow(1-k,3);sb.scrollLeft=from+(to-from)*e;lmaBarRaf=k<1?requestAnimationFrame(step):0};
 lmaBarRaf=requestAnimationFrame(step);
}
/* exactly one bottom-bar tab is lit: the one for the page you are on */
function lmaSyncActive(){
 try{const k=lmaTabKey(lmaView()),bs=[...document.querySelectorAll('.navbtn[data-view]')];if(bs.some(b=>b.dataset.view===k))bs.forEach(b=>b.classList.toggle('active',b.dataset.view===k))}catch(e){}
}
function lmaTabIndex(v){v=lmaTabKey(v);return lmaTabList().findIndex(b=>b.dataset.view===v)}
const lmaVTok=()=>typeof document.startViewTransition==='function'&&!document.documentElement.classList.contains('reduce-motion')&&!(window.matchMedia&&matchMedia('(prefers-reduced-motion:reduce)').matches);
/* go to a tab, with the page area sliding across when the browser can do it.
   opts.skip: do not record it in Back history. opts.fast: no animation (used while scrubbing). */
let lmaVTcur=null,lmaVTrun=null; // the unfinished slide and its page change
function lmaNavTo(v,opts){
 v=lmaResolve(v);
 const el=document.getElementById('view-'+v);if(!el)return false;
 const root=document.documentElement;let ran=false; // run() once: a newer request may make its change before the browser does
 const run=()=>{
  if(ran)return;ran=true;const sk=lmaNavSkip;lmaNavSkip=!!(opts&&opts.skip);
  try{navigate(v);if(el.classList.contains('lmf-feature')&&window.LifeMaxFeatures)LifeMaxFeatures.open(v)}finally{lmaNavSkip=sk}
  window.scrollTo(0,0);
 };
 /* a newer request wins: an unfinished slide's page change is made first, here (skipTransition() makes it only later, after this request, which it would undo) */
 if(lmaVTcur){const t=lmaVTcur,r=lmaVTrun;lmaVTcur=lmaVTrun=null;try{t.skipTransition()}catch(e){}try{r()}catch(e){}}
 const from=lmaView();if(!from||from===v||(opts&&opts.fast)||!lmaVTok()){run();return true}
 const a=lmaTabIndex(from),b=lmaTabIndex(v);
 root.dataset.vt=(a>=0&&b>=0&&b<a)?'back':'fwd';
 try{
  const t=document.startViewTransition(run);lmaVTcur=t;lmaVTrun=run;try{t.ready.catch(()=>{});t.updateCallbackDone.catch(()=>{})}catch(e){}
  const done=()=>{if(lmaVTcur===t){lmaVTcur=null;delete root.dataset.vt}};
  t.finished.then(done,done);
 }catch(e){delete root.dataset.vt;run()}
 return true;
}
function lmaGoView(v){return lmaNavTo(v,{skip:true})}
/* the topmost open popup, as {close()} or null */
function lmaTopOverlay(){
 const q=s=>document.querySelector(s),all=s=>[...document.querySelectorAll(s)];
 if(q('#onboarding.show,#tutorial.show'))return {close(){}}; /* swallow Back during first-run screens */
 const full=q('#petRoomFullscreen.is-open');if(full)return {close(){petRoomFullscreenClose()}};
 const up=q('.updpop');if(up)return {close(){up.remove()}};
 const dl=q('.lmdl-ask');if(dl)return {close(){dl.remove()}};
 const hs=q('.hsheet');if(hs)return {close(){hs.remove()}};
 const as=all('.apps-sheet').pop();
 if(as)return {close(){const b=as.querySelector('[data-k="__close"],[data-act="close"]');b?b.click():as.remove()}};
 const ld=q('.ld-sheet.open:not([hidden])');
 if(ld)return {close(){const b=ld.querySelector('.ld-sheet-bg');b?b.click():0}};
 const so=all('.sheet-ov.show').pop();
 if(so)return {close(){so.click()}};
 return null;
}
/* Android Back: close a popup, else the previous tab, else Overview. Returns true when it did something. */
function lmaBack(){
 const o=lmaTopOverlay();if(o){o.close();return true}
 while(lmaNavStack.length){const v=lmaNavStack.pop();if(v&&v!==lmaView()&&document.getElementById('view-'+v))return lmaGoView(v)}
 if(lmaView()!=='dashboard')return lmaGoView('dashboard');
 return false;
}
window.lmaBack=lmaBack;
/* remember where you came from so Back can return there */
(function lmaNavWrap(){
 const _nav=navigate;
 navigate=function(v){
  /* a swipe turns the cards' fade-in off; any other way of changing page turns it back on (before the page changes, so nothing replays) */
  if(!lmaSwipeNav)delete document.documentElement.dataset.swipeDir;
  const prev=lmaView(),r=_nav.apply(this,arguments),now=lmaView();
  lmaSyncActive();
  if(!lmaNavSkip&&prev&&now&&prev!==now){
   if(lmaNavStack[lmaNavStack.length-1]!==prev)lmaNavStack.push(prev);
   if(lmaNavStack.length>30)lmaNavStack.shift();
  }
  try{const b=document.querySelector('.navbtn[data-view="'+now+'"]')||document.querySelector('.navbtn.active');lmaBarTo(b)}catch(e){}
  return r;
 };
})();
/* ---------- swipe between tabs ---------- */
function lmaSwipeEnabled(){return lmaLoad().swipe!==false}
function lmaSetSwipe(on){lmaLoad().swipe=!!on;lmaSave();toast(on?'Swipe to switch tabs: on':'Swipe to switch tabs: off')}
function lmaTabList(){return [...document.querySelectorAll('.navbtn[data-view]')].filter(b=>b.getClientRects().length>0)}
/* true when a touch starting here should never turn into a tab swipe */
function lmaSwipeBlocked(t){
 if(!t||!t.closest)return true;
 if(t.closest('#view-life-director,#view-pet canvas,[data-noswipe],.sidebar'))return true; /* Life Director has its own swipes; the cat room is touch-played */
 for(let e=t;e&&e!==document.documentElement;e=e.parentElement){
  if(/^(INPUT|TEXTAREA|SELECT|CANVAS|VIDEO|AUDIO)$/.test(e.tagName)||e.isContentEditable)return true;
  const ox=getComputedStyle(e).overflowX;
  if((ox==='auto'||ox==='scroll')&&e.scrollWidth>e.clientWidth+4)return true; /* carousels, week strips, the tab bar itself */
 }
 return false;
}
function lmaSwipeHint(btn,dir){
 let h=document.getElementById('lmaSwipeHint');
 if(!h){h=document.createElement('div');h.id='lmaSwipeHint';document.body.appendChild(h)}
 const name=(btn.textContent||'').trim();
 h.textContent=dir<0?'›  '+name:name+'  ‹';h.className='show '+(dir<0?'to-next':'to-prev');
 clearTimeout(lmaHintTimer);lmaHintTimer=setTimeout(()=>{h.className=''},700);
}
/* Swiping between tabs, version 4: the cards follow your finger, then the new page's cards slide in.
   What moves is each card on the page (one CSS variable on the page, read by every card), never the page container as a whole:
   moving the whole container made the fixed bottom bar blink out on some phones, while moving cards is what the app's own page-change
   animation has always done. */
const lmaMotionOff=()=>document.documentElement.classList.contains('reduce-motion')||(window.matchMedia&&matchMedia('(prefers-reduced-motion:reduce)').matches)||lmaLoad().swipeAnim===false;
function lmaSetSwipeAnim(on){lmaLoad().swipeAnim=!!on;lmaSave();toast(on?'Swipe animation: on':'Swipe animation: off. Swipes switch tabs instantly.')}
let lmaPgDx=0,lmaPgRaf=0,lmaPgTw=0,lmaPgView=null;
function lmaPgApply(){lmaPgRaf=0;const v=lmaPgView;if(v)v.style.setProperty('--swx',lmaPgDx.toFixed(1)+'px')}
function lmaPgSet(dx){lmaPgDx=dx;if(!lmaPgRaf)lmaPgRaf=requestAnimationFrame(lmaPgApply)}
function lmaPgStart(){lmaPgView=document.querySelector('.view.active');lmaPgDx=0;document.documentElement.classList.add('lma-drag');if(lmaPgView)lmaPgView.style.setProperty('--swx','0px')}
function lmaPgEnd(){
 cancelAnimationFrame(lmaPgRaf);lmaPgRaf=0;cancelAnimationFrame(lmaPgTw);lmaPgTw=0;
 if(lmaPgView)lmaPgView.style.removeProperty('--swx');lmaPgView=null;lmaPgDx=0;document.documentElement.classList.remove('lma-drag');
}
/* glide the cards from where they are to a place, then call done */
function lmaPgTween(to,ms,easing,done){
 cancelAnimationFrame(lmaPgRaf);lmaPgRaf=0;cancelAnimationFrame(lmaPgTw);
 const from=lmaPgDx,t0=performance.now();
 const step=t=>{const k=Math.min(1,(t-t0)/ms),e=easing(k);lmaPgDx=from+(to-from)*e;if(lmaPgView)lmaPgView.style.setProperty('--swx',lmaPgDx.toFixed(1)+'px');if(k<1)lmaPgTw=requestAnimationFrame(step);else{lmaPgTw=0;done&&done()}};
 lmaPgTw=requestAnimationFrame(step);
}
const lmaEaseOut=k=>1-Math.pow(1-k,3),lmaEaseIn=k=>k*k;
document.addEventListener('touchstart',e=>{
 lmaSw=null;
 if(lmaPgView)lmaPgEnd();                                   /* a new touch while the last one is still settling: put everything where it belongs */
 if(!lmaSwipeEnabled()||e.touches.length!==1||lmaTopOverlay()||document.body.classList.contains('noscroll'))return;
 if(window.visualViewport&&window.visualViewport.scale>1.02)return;
 const t=e.touches[0],w=window.innerWidth;
 if(t.clientX<LMA_SWIPE.edge||t.clientX>w-LMA_SWIPE.edge)return; /* the system's own back gesture lives at the edges */
 if(lmaSwipeBlocked(e.target))return;
 const s=window.getSelection&&window.getSelection();if(s&&!s.isCollapsed)return;
 lmaSw={x:t.clientX,y:t.clientY,t:Date.now(),dead:false,drag:false,pts:[[Date.now(),t.clientX]]};
},{passive:true});
document.addEventListener('touchmove',e=>{
 if(!lmaSw)return;
 if(e.touches.length!==1){const was=lmaSw.drag;lmaSw=null;if(was)lmaPgTween(0,140,lmaEaseOut,lmaPgEnd);return}
 const t=e.touches[0],dx=t.clientX-lmaSw.x,dy=t.clientY-lmaSw.y;
 if(!lmaSw.drag&&Math.abs(dy)>LMA_SWIPE.slop&&Math.abs(dy)*LMA_SWIPE.ratio>Math.abs(dx))lmaSw.dead=true; /* it is a scroll, not a swipe */
 if(lmaSw.dead)return;
 const now=Date.now();lmaSw.pts.push([now,t.clientX]);while(lmaSw.pts.length>2&&now-lmaSw.pts[0][0]>120)lmaSw.pts.shift();
 if(!lmaSw.drag){
  if(Math.abs(dx)>12&&Math.abs(dx)>Math.abs(dy)*1.4&&!lmaMotionOff()){
   const tabs=lmaTabList(),cur=tabs.findIndex(b=>b.dataset.view===lmaTabKey(lmaView()));
   if(cur<0)return;
   lmaSw.drag=true;lmaSw.cur=cur;lmaSw.n=tabs.length;lmaPgStart();
  }else return;
 }
 /* the cards follow the finger; where there is no further tab they stretch a little and no more */
 const dir=dx<0?1:-1,nx=lmaSw.cur+dir,edge=nx<0||nx>=lmaSw.n;
 lmaPgSet(edge?dx*.25:dx);
},{passive:true});
/* go to a tab because of a swipe: its cards slide in from the side you swiped toward */
function lmaSwipeGo(v,dir){
 lmaSwipeNav=true;
 try{if(dir&&!lmaMotionOff())document.documentElement.dataset.swipeDir=dir>0?'next':'prev';else delete document.documentElement.dataset.swipeDir;lmaNavTo(v,{fast:true})}finally{lmaSwipeNav=false}
 lmaSyncActive();
}
function lmaSwipeEnd(e){
 const s=lmaSw;lmaSw=null;
 if(!s||s.dead)return;
 const t=e.changedTouches&&e.changedTouches[0];if(!t){if(s.drag)lmaPgTween(0,140,lmaEaseOut,lmaPgEnd);return}
 const dx=t.clientX-s.x,dy=t.clientY-s.y,dt=Date.now()-s.t,w=window.innerWidth,dir=dx<0?1:-1;
 /* speed over the last moments of the swipe (px per ms) */
 let v=0;const p=s.pts;if(p.length>1){const a=p[0],b=p[p.length-1];if(b[0]>a[0])v=(b[1]-a[1])/(b[0]-a[0])}
 const tabs=lmaTabList(),cur=tabs.findIndex(b=>b.dataset.view===lmaTabKey(lmaView())),next=cur+dir;
 let go=false;
 if(cur>=0&&next>=0&&next<tabs.length&&!lmaTopOverlay()){
  if(s.drag)go=Math.abs(dx)>=Math.abs(dy)*1.2&&(Math.abs(dx)>=w*.22||(Math.abs(v)>.45&&Math.abs(dx)>=36&&Math.sign(v)===Math.sign(dx)));
  else go=dt<=LMA_SWIPE.maxMs&&Math.abs(dx)>=Math.max(LMA_SWIPE.minPx,w*LMA_SWIPE.minFrac)&&Math.abs(dx)>=Math.abs(dy)*LMA_SWIPE.ratio;   /* a quick swipe that never became a drag (animation off) */
 }
 if(!s.drag){if(go){lmaSwipeGo(tabs[next].dataset.view,dir);lmaSwipeHint(tabs[next],dir>0?-1:1)}return}
 if(!go){lmaPgTween(0,170,lmaEaseOut,lmaPgEnd);return}                      /* not far enough: the cards settle back */
 /* far enough: the cards carry on off the screen (faster if you flicked), then the next page takes over */
 const off=(dir>0?-1:1)*w,ms=Math.max(70,Math.min(150,Math.abs(off-lmaPgDx)/Math.max(.9,Math.abs(v)*1.3)));
 const target=tabs[next];
 lmaPgTween(off,ms,lmaEaseIn,()=>{
  lmaPgEnd();
  lmaSwipeGo(target.dataset.view,dir);
  lmaSwipeHint(target,dir>0?-1:1);
 });
}
document.addEventListener('touchend',lmaSwipeEnd,{passive:true});
document.addEventListener('touchcancel',()=>{const was=lmaSw&&lmaSw.drag;lmaSw=null;if(was)lmaPgTween(0,140,lmaEaseOut,lmaPgEnd)},{passive:true});
setTimeout(()=>{const c=document.getElementById('lmaSwipeToggle');if(c)c.checked=lmaSwipeEnabled();const d=document.getElementById('lmaSwipeAnimToggle');if(d)d.checked=lmaLoad().swipeAnim!==false;const g=document.getElementById('lmaScrubSpeed');if(g)g.value=LMA_SCRUB_GAINS[lmaLoad().scrubSpeed]?lmaLoad().scrubSpeed:'fast'},500);
