/* ---------- v105 · Bottom bar: static, with a highlight that slides to the chosen tab ----------
   Slide a thumb along the bar to run through the tabs (a full-width swipe covers them all). The highlight and the bar's
   scroll follow your finger; the page switches when you pause on a tab and when you let go. Tapping a tab works as before.
   Phones only (<= 860px wide). The page-to-page animation is lmaNavTo in 34-navigation.js. */
const LMA_SCRUB={slop:8,pause:240,gain:.92};
const lmaScrub={sb:null,pill:null,items:[],drag:null,sel:0,timer:0,block:0,raf:0,want:null};
const lmaScrubOn=()=>!!(window.matchMedia&&matchMedia('(max-width:860px)').matches);
function lmaScrubItems(){const S=lmaScrub;S.items=S.sb?[...S.sb.querySelectorAll('.navbtn[data-view]')].filter(b=>getComputedStyle(b).display!=='none'):[];return S.items}
function lmaScrubIndex(v){return lmaScrub.items.findIndex(b=>b.dataset.view===v)}
/* put the highlight under tab i; keep that tab in view */
function lmaScrubShow(i,instant){
 const S=lmaScrub,b=S.items[i];if(!b||!S.pill||!lmaScrubOn())return;
 S.sel=i;
 const w=Math.max(40,b.offsetWidth-8);
 if(instant){S.pill.style.transition='none'}
 S.pill.style.width=w+'px';S.pill.style.transform='translate3d('+(b.offsetLeft+4)+'px,0,0)';
 if(instant){void S.pill.offsetWidth;S.pill.style.transition=''}
 S.want=Math.max(0,Math.min(S.sb.scrollWidth-S.sb.clientWidth,b.offsetLeft+b.offsetWidth/2-S.sb.clientWidth/2));
 if(instant)S.sb.scrollLeft=S.want;else lmaScrubFollow();
}
/* the bar glides to keep the highlighted tab centred */
function lmaScrubFollow(){
 const S=lmaScrub;if(S.raf)return;
 const step=()=>{
  S.raf=0;if(S.want==null)return;
  const d=S.want-S.sb.scrollLeft;
  if(Math.abs(d)<1){S.sb.scrollLeft=S.want;return}
  S.sb.scrollLeft+=d*.22;S.raf=requestAnimationFrame(step);
 };
 S.raf=requestAnimationFrame(step);
}
function lmaScrubSync(v){const S=lmaScrub;if(!S.sb||S.drag&&S.drag.moved)return;lmaScrubItems();const i=lmaScrubIndex(v||lmaView());if(i>=0)lmaScrubShow(i)}
function lmaScrubInit(){
 const S=lmaScrub,sb=document.querySelector('.sidebar');
 if(!sb||S.sb)return;
 S.sb=sb;S.pill=document.createElement('div');S.pill.className='navpill';sb.insertBefore(S.pill,sb.firstChild);
 lmaScrubItems();
 const i0=lmaScrubIndex(lmaView());if(i0>=0)lmaScrubShow(i0,true);
 sb.addEventListener('touchstart',e=>{
  if(!lmaScrubOn()||e.touches.length!==1)return;
  const t=e.touches[0];lmaScrubItems();
  S.drag={x0:t.clientX,y0:t.clientY,start:Math.max(0,lmaScrubIndex(lmaView())),pos:0,moved:false,from:lmaView(),W:Math.max(200,sb.clientWidth)};
  S.drag.pos=S.drag.start;
 },{passive:true});
 sb.addEventListener('touchmove',e=>{
  const d=S.drag;if(!d||e.touches.length!==1)return;
  const t=e.touches[0],dx=t.clientX-d.x0,n=S.items.length;
  if(!d.moved){
   if(Math.abs(dx)<LMA_SCRUB.slop)return;
   if(Math.abs(t.clientY-d.y0)>Math.abs(dx)*1.5){S.drag=null;return}
   d.moved=true;haptic(8);
  }
  e.preventDefault();
  const per=d.W*LMA_SCRUB.gain/Math.max(1,n-1);
  d.pos=Math.min(n-1,Math.max(0,d.start-dx/per));
  const i=Math.round(d.pos);
  if(i!==S.sel){
   lmaScrubShow(i);haptic(6);
   clearTimeout(S.timer);S.timer=setTimeout(()=>{if(S.drag&&S.drag.moved)lmaNavTo(S.items[S.sel].dataset.view,{skip:true})},LMA_SCRUB.pause);
  }
 },{passive:false});
 const end=()=>{
  const d=S.drag;if(!d)return;S.drag=null;
  clearTimeout(S.timer);
  if(!d.moved)return;
  S.block=Date.now();
  const b=S.items[S.sel];if(!b)return;
  if(b.dataset.view!==lmaView())lmaNavTo(b.dataset.view,{skip:true});
  if(b.dataset.view!==d.from&&lmaNavStack[lmaNavStack.length-1]!==d.from)lmaNavStack.push(d.from);
 };
 sb.addEventListener('touchend',end,{passive:true});
 sb.addEventListener('touchcancel',end,{passive:true});
 /* a tap on a tab: same destination, with the page animation; swallow the click that ends a scrub */
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
 navigate=function(v){const r=_nav.apply(this,arguments);try{lmaScrubSync(v)}catch(e){}return r};
 const go=()=>{try{lmaScrubInit()}catch(e){console.warn('[bottom bar]',e)}};
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',go);else go();
 setTimeout(()=>{try{lmaScrubSync()}catch(e){}},1200);
})();
