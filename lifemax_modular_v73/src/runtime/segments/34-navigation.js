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
let lmaVTcur=null;
function lmaNavTo(v,opts){
 v=lmaResolve(v);
 const el=document.getElementById('view-'+v);if(!el)return false;
 const from=lmaView(),root=document.documentElement;
 const run=()=>{
  const sk=lmaNavSkip;lmaNavSkip=!!(opts&&opts.skip);
  try{navigate(v);if(el.classList.contains('lmf-feature')&&window.LifeMaxFeatures)LifeMaxFeatures.open(v)}finally{lmaNavSkip=sk}
  window.scrollTo(0,0);
 };
 /* a newer request wins: let an unfinished slide jump to its end first */
 if(lmaVTcur){try{lmaVTcur.skipTransition()}catch(e){}lmaVTcur=null}
 if(!from||from===v||(opts&&opts.fast)||!lmaVTok()){run();return true}
 const a=lmaTabIndex(from),b=lmaTabIndex(v);
 root.dataset.vt=(a>=0&&b>=0&&b<a)?'back':'fwd';
 try{
  const t=document.startViewTransition(run);lmaVTcur=t;try{t.ready.catch(()=>{});t.updateCallbackDone.catch(()=>{})}catch(e){}
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
/* Swiping between tabs, version 3. The page itself is never moved while you swipe (sliding the whole page made the bottom bar blink out
   on some phones). Instead a small tag at the edge shows where you are heading and fills as you pull; let go and the new page appears
   at once with its cards sliding in from the side you swiped toward: the same kind of card animation the app has always used. */
const lmaMotionOff=()=>document.documentElement.classList.contains('reduce-motion')||(window.matchMedia&&matchMedia('(prefers-reduced-motion:reduce)').matches)||lmaLoad().swipeAnim===false;
function lmaSetSwipeAnim(on){lmaLoad().swipeAnim=!!on;lmaSave();toast(on?'Swipe animation: on':'Swipe animation: off. Swipes switch tabs instantly.')}
let lmaIndRaf=0,lmaIndState=null,lmaDragDx=0;
function lmaInd(){let e=document.getElementById('lmaSwipeInd');if(!e){e=document.createElement('div');e.id='lmaSwipeInd';e.setAttribute('aria-hidden','true');document.body.appendChild(e)}return e}
function lmaIndPaint(){
 lmaIndRaf=0;const s=lmaIndState,e=lmaInd();
 if(!s){e.className='';e.style.opacity='';e.style.transform='';return}
 const p=Math.min(1,Math.abs(lmaDragDx)/(window.innerWidth*.22)),sg=s.dir>0?1:-1;
 if(e.dataset.name!==s.name||e.dataset.dir!==String(s.dir)){e.dataset.name=s.name;e.dataset.dir=String(s.dir);e.textContent=s.dir>0?s.name+'  ›':'‹  '+s.name}
 e.className='show '+(s.dir>0?'to-next':'to-prev')+(p>=1?' ready':'');
 e.style.opacity=String(.3+.7*p);e.style.transform='translateY(-50%) translateX('+(sg*(1-p)*28)+'px) scale('+(.92+.08*p)+')';
}
function lmaIndSet(st,dx){lmaIndState=st;lmaDragDx=dx;if(!lmaIndRaf)lmaIndRaf=requestAnimationFrame(lmaIndPaint)}
function lmaIndHide(){lmaIndState=null;lmaDragDx=0;if(lmaIndRaf){cancelAnimationFrame(lmaIndRaf);lmaIndRaf=0}const e=document.getElementById('lmaSwipeInd');if(e){e.className='';e.style.opacity='';e.style.transform='';delete e.dataset.name}}
document.addEventListener('touchstart',e=>{
 lmaSw=null;lmaIndHide();
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
 if(e.touches.length!==1){lmaSw=null;lmaIndHide();return}
 const t=e.touches[0],dx=t.clientX-lmaSw.x,dy=t.clientY-lmaSw.y;
 if(!lmaSw.drag&&Math.abs(dy)>LMA_SWIPE.slop&&Math.abs(dy)*LMA_SWIPE.ratio>Math.abs(dx))lmaSw.dead=true; /* it is a scroll, not a swipe */
 if(lmaSw.dead)return;
 const now=Date.now();lmaSw.pts.push([now,t.clientX]);while(lmaSw.pts.length>2&&now-lmaSw.pts[0][0]>120)lmaSw.pts.shift();
 if(!lmaSw.drag){
  if(Math.abs(dx)>12&&Math.abs(dx)>Math.abs(dy)*1.4&&!lmaMotionOff()){
   const tabs=lmaTabList(),cur=tabs.findIndex(b=>b.dataset.view===lmaTabKey(lmaView()));
   if(cur<0)return;
   lmaSw.drag=true;lmaSw.cur=cur;lmaSw.tabs=tabs;
  }else return;
 }
 const dir=dx<0?1:-1,nx=lmaSw.cur+dir;
 if(nx<0||nx>=lmaSw.tabs.length)lmaIndSet(null,dx);                       /* nowhere further to go: no tag */
 else lmaIndSet({dir:dir,name:(lmaSw.tabs[nx].textContent||'').trim()},dx);
},{passive:true});
/* go to a tab because of a swipe: its cards slide in from the side you swiped toward */
function lmaSwipeGo(v,dir){
 lmaSwipeNav=true;
 try{if(dir&&!lmaMotionOff())document.documentElement.dataset.swipeDir=dir>0?'next':'prev';else delete document.documentElement.dataset.swipeDir;lmaNavTo(v,{fast:true})}finally{lmaSwipeNav=false}
 lmaSyncActive();
}
function lmaSwipeEnd(e){
 const s=lmaSw;lmaSw=null;lmaIndHide();
 if(!s||s.dead)return;
 const t=e.changedTouches&&e.changedTouches[0];if(!t)return;
 const dx=t.clientX-s.x,dy=t.clientY-s.y,dt=Date.now()-s.t,w=window.innerWidth,dir=dx<0?1:-1;
 /* speed over the last moments of the swipe (px per ms) */
 let v=0;const p=s.pts;if(p.length>1){const a=p[0],b=p[p.length-1];if(b[0]>a[0])v=(b[1]-a[1])/(b[0]-a[0])}
 const tabs=lmaTabList(),cur=tabs.findIndex(b=>b.dataset.view===lmaTabKey(lmaView())),next=cur+dir;
 if(cur<0||next<0||next>=tabs.length||lmaTopOverlay())return;
 let go=false;
 if(s.drag)go=Math.abs(dx)>=Math.abs(dy)*1.2&&(Math.abs(dx)>=w*.22||(Math.abs(v)>.45&&Math.abs(dx)>=36&&Math.sign(v)===Math.sign(dx)));
 else go=dt<=LMA_SWIPE.maxMs&&Math.abs(dx)>=Math.max(LMA_SWIPE.minPx,w*LMA_SWIPE.minFrac)&&Math.abs(dx)>=Math.abs(dy)*LMA_SWIPE.ratio;   /* a quick swipe that never became a drag (animation off) */
 if(!go)return;
 lmaSwipeGo(tabs[next].dataset.view,dir);
 lmaSwipeHint(tabs[next],dir>0?-1:1);
}
document.addEventListener('touchend',lmaSwipeEnd,{passive:true});
document.addEventListener('touchcancel',()=>{lmaSw=null;lmaIndHide()},{passive:true});
setTimeout(()=>{const c=document.getElementById('lmaSwipeToggle');if(c)c.checked=lmaSwipeEnabled();const d=document.getElementById('lmaSwipeAnimToggle');if(d)d.checked=lmaLoad().swipeAnim!==false},500);
