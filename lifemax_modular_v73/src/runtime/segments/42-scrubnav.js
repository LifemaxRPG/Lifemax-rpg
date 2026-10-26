/* ---------- v106 · Bottom bar: looks as it always did; slide a thumb along it to run through the tabs ----------
   The finger picks a tab (a full-width swipe covers them all). The bar just shows that tab as the active one and
   scrolls only when it has to keep it in view. The page follows as soon as the finger slows (and when it lifts),
   so fast swipes stay light; nothing is animated while scrubbing. Taps work as before, with a short slide.
   Phones only (<= 860px wide). The page animation is lmaNavTo in 34-navigation.js. */
const LMA_SCRUB={slop:7,settle:70,gain:.92};
const lmaScrub={sb:null,items:[],drag:null,sel:-1,timer:0,block:0};
const lmaScrubOn=()=>!!(window.matchMedia&&matchMedia('(max-width:860px)').matches);
function lmaScrubItems(){const S=lmaScrub;S.items=S.sb?[...S.sb.querySelectorAll('.navbtn[data-view]')].filter(b=>b.offsetWidth>0):[];return S.items}
function lmaScrubIndex(v){return lmaScrub.items.findIndex(b=>b.dataset.view===v)}
/* nudge the strip just enough that tab i is fully visible */
function lmaScrubReveal(i){
 const S=lmaScrub,b=S.items[i];if(!b)return;
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
 const go=()=>{const b=S.items[S.sel];if(b&&b.dataset.view!==lmaView())lmaNavTo(b.dataset.view,{skip:true,fast:true})};
 sb.addEventListener('touchstart',e=>{
  if(!lmaScrubOn()||e.touches.length!==1)return;
  const t=e.touches[0];lmaScrubItems();
  const st=Math.max(0,lmaScrubIndex(lmaView()));
  S.drag={x0:t.clientX,y0:t.clientY,start:st,moved:false,from:lmaView(),W:Math.max(200,sb.clientWidth)};
  S.sel=st;
 },{passive:true});
 sb.addEventListener('touchmove',e=>{
  const d=S.drag;if(!d||e.touches.length!==1)return;
  const t=e.touches[0],dx=t.clientX-d.x0,n=S.items.length;
  if(!d.moved){
   if(Math.abs(dx)<LMA_SCRUB.slop)return;
   if(Math.abs(t.clientY-d.y0)>Math.abs(dx)*1.5){S.drag=null;return}
   d.moved=true;
  }
  e.preventDefault();
  const per=d.W*LMA_SCRUB.gain/Math.max(1,n-1),i=Math.round(Math.min(n-1,Math.max(0,d.start-dx/per)));
  if(i!==S.sel){
   lmaScrubMark(i);haptic(5);
   clearTimeout(S.timer);S.timer=setTimeout(go,LMA_SCRUB.settle);
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
 navigate=function(v){const r=_nav.apply(this,arguments);try{lmaScrubSync(v)}catch(e){}return r};
 const go=()=>{try{lmaScrubInit()}catch(e){console.warn('[bottom bar]',e)}};
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',go);else go();
 setTimeout(()=>{try{lmaScrubSync()}catch(e){}},1200);
})();
