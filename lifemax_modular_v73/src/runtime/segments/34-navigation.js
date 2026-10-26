/* v89 · Navigation: Android Back button / gesture, and swipe left/right between tabs.
   Native Back calls window.lmaBack() (see MainActivity); in a browser nothing here changes behaviour except swipe. */
const LMA_SWIPE={minPx:72,minFrac:.2,ratio:2.2,maxMs:700,edge:36,slop:14};
let lmaNavStack=[],lmaNavSkip=false,lmaSw=null,lmaHintTimer=0;
function lmaView(){const v=document.querySelector('.view.active');return v?v.id.replace(/^view-/,''):''}
/* Media and Journal live together under the Library tab */
const lmaTabKey=v=>v==='media'||v==='history'?'library':v;
const lmaResolve=v=>v==='library'?((state&&state.libPane)||'media'):v;
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
  const prev=lmaView(),r=_nav.apply(this,arguments),now=lmaView();
  if(!lmaNavSkip&&prev&&now&&prev!==now){
   if(lmaNavStack[lmaNavStack.length-1]!==prev)lmaNavStack.push(prev);
   if(lmaNavStack.length>30)lmaNavStack.shift();
  }
  try{const b=document.querySelector('.navbtn[data-view="'+now+'"]');if(b&&b.scrollIntoView)b.scrollIntoView({inline:'center',block:'nearest',behavior:'smooth'})}catch(e){}
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
document.addEventListener('touchstart',e=>{
 lmaSw=null;
 if(!lmaSwipeEnabled()||e.touches.length!==1||lmaTopOverlay()||document.body.classList.contains('noscroll'))return;
 if(window.visualViewport&&window.visualViewport.scale>1.02)return;
 const t=e.touches[0],w=window.innerWidth;
 if(t.clientX<LMA_SWIPE.edge||t.clientX>w-LMA_SWIPE.edge)return; /* the system's own back gesture lives at the edges */
 if(lmaSwipeBlocked(e.target))return;
 const s=window.getSelection&&window.getSelection();if(s&&!s.isCollapsed)return;
 lmaSw={x:t.clientX,y:t.clientY,t:Date.now(),dead:false};
},{passive:true});
document.addEventListener('touchmove',e=>{
 if(!lmaSw)return;
 if(e.touches.length!==1){lmaSw=null;return}
 const t=e.touches[0],dx=t.clientX-lmaSw.x,dy=t.clientY-lmaSw.y;
 if(Math.abs(dy)>LMA_SWIPE.slop&&Math.abs(dy)*LMA_SWIPE.ratio>Math.abs(dx))lmaSw.dead=true; /* it is a scroll, not a swipe */
},{passive:true});
document.addEventListener('touchend',e=>{
 const s=lmaSw;lmaSw=null;
 if(!s||s.dead)return;
 const t=e.changedTouches[0],dx=t.clientX-s.x,dy=t.clientY-s.y,dt=Date.now()-s.t;
 if(dt>LMA_SWIPE.maxMs||Math.abs(dx)<Math.max(LMA_SWIPE.minPx,window.innerWidth*LMA_SWIPE.minFrac)||Math.abs(dx)<Math.abs(dy)*LMA_SWIPE.ratio)return;
 if(lmaTopOverlay())return;
 const tabs=lmaTabList(),cur=tabs.findIndex(b=>b.dataset.view===lmaTabKey(lmaView())),next=cur+(dx<0?1:-1);
 if(cur<0||next<0||next>=tabs.length)return;
 tabs[next].click();
 lmaSwipeHint(tabs[next],dx<0?-1:1);
},{passive:true});
document.addEventListener('touchcancel',()=>{lmaSw=null},{passive:true});
setTimeout(()=>{const c=document.getElementById('lmaSwipeToggle');if(c)c.checked=lmaSwipeEnabled()},500);
