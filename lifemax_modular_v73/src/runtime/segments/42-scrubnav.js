/* ---------- v106 · Bottom bar: looks as it always did; slide a thumb along it to run through the tabs ----------
   The finger picks a tab (a full-width swipe covers them all). The bar just shows that tab as the active one and
   scrolls only when it has to keep it in view. The page follows as soon as the finger slows (and when it lifts),
   so fast swipes stay light; nothing is animated while scrubbing. Taps work as before, with a short slide.
   Phones only (<= 860px wide). The page animation is lmaNavTo in 34-navigation.js. */
const LMA_SCRUB={slop:7,settle:130,gain:.92,fast:.16};
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
/* while scrubbing, a blurred veil over the page you were on names the tab; the real page replaces it when you rest or let go */
function lmaVeil(show){
 const S=lmaScrub;if(!S.veil)return;
 if(show===S.veilOn)return;S.veilOn=show;
 S.veil.classList.toggle('on',show);
}
function lmaVeilSet(i){
 const S=lmaScrub,b=S.items[i];if(!b||!S.veil)return;
 const ico=b.querySelector('.ico'),txt=b.textContent.trim();
 S.veil.querySelector('.sv-ico').innerHTML=ico?ico.innerHTML:'';
 S.veil.querySelector('.sv-name').textContent=txt;
}
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
 S.veil=document.createElement('div');S.veil.className='scrubveil';S.veil.setAttribute('aria-hidden','true');S.veil.innerHTML='<div class="sv-card"><div class="sv-ico"></div><div class="sv-name"></div></div>';document.body.appendChild(S.veil);
 /* switch the page to the tab the bar shows. The bar has already painted; the page work runs right after a frame,
    and how long each page takes is remembered, so heavy pages wait for the finger to rest longer */
 const go=()=>{
  const b=S.items[S.sel];if(!b)return;const v=b.dataset.view;if(v===lmaView())return;
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
  const W=d.W,x=Math.min(W,Math.max(0,t.clientX-d.L)),x0=Math.min(W-40,Math.max(40,d.x0-d.L));
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
  if(b.dataset.view!==lmaView())lmaNavTo(b.dataset.view,{skip:true,fast:true});
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
 navigate=function(v){const r=_nav.apply(this,arguments);try{lmaScrubSync(v)}catch(e){}return r};
 const go=()=>{try{lmaScrubInit()}catch(e){console.warn('[bottom bar]',e)}};
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',go);else go();
 setTimeout(()=>{try{lmaScrubSync()}catch(e){}},1200);
})();
