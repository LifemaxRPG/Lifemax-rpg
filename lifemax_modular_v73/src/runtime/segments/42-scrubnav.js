/* ---------- v104 · Scrub bar: slide a thumb along the bottom bar to glide through every tab ----------
   One position (a fractional tab index) drives everything: the strip centres on it, a pill sits under it,
   icons swell like a dock around it, and a bubble names the tab. Dragging moves it with the finger
   (a full-width swipe covers all the tabs); letting go snaps to the nearest tab with a little momentum.
   Tapping a tab works as before. Phones only (<= 860px wide). */
const LMA_SCRUB={nw:66,vel:.11,hyst:.14,live:120};
const lmaScrub={pos:0,target:0,mag:0,magT:0,raf:0,last:0,drag:null,items:[],cur:0,timer:0,hold:0,sb:null,track:null,pill:null,bub:null,block:0};
const lmaScrubOn=()=>!!(window.matchMedia&&matchMedia('(max-width:860px)').matches);
function lmaScrubItems(){const S=lmaScrub;S.items=S.track?[...S.track.querySelectorAll('.navbtn[data-view]')].filter(b=>getComputedStyle(b).display!=='none'):[];return S.items}
function lmaScrubIndex(v){return lmaScrub.items.findIndex(b=>b.dataset.view===v)}
function lmaScrubRender(){
 const S=lmaScrub;if(!S.sb||!lmaScrubOn())return;
 const W=S.sb.clientWidth,nw=LMA_SCRUB.nw,m=S.mag,amp=.15+.45*m;
 S.track.style.transform='translate3d('+(W/2-(S.pos+.5)*nw).toFixed(2)+'px,0,0)';
 S.pill.style.transform='translate3d('+(S.pos*nw+4).toFixed(2)+'px,0,0)';
 const near=Math.round(S.pos);
 for(let i=0;i<S.items.length;i++){
  const b=S.items[i],d=Math.abs(i-S.pos),k=Math.max(0,1-d/1.7),k2=k*k,ico=b.firstElementChild;
  if(ico)ico.style.transform='translate3d(0,'+(-amp*k2*20).toFixed(2)+'px,0) scale('+(1+amp*k2).toFixed(3)+')';
  b.style.opacity=(1-m*(1-(.45+.55*Math.min(1,k*1.6)))).toFixed(3);
  b.classList.toggle('scrub-near',i===near);
 }
}
function lmaScrubTick(t){
 const S=lmaScrub;S.raf=0;
 const dt=Math.min(.05,Math.max(.001,(t-(S.last||t))/1000));S.last=t;
 if(S.drag)S.pos+=(S.drag.pos-S.pos)*(1-Math.exp(-dt*40));
 else S.pos+=(S.target-S.pos)*(1-Math.exp(-dt*12));
 S.mag+=(S.magT-S.mag)*(1-Math.exp(-dt*12));
 lmaScrubRender();
 if(S.drag||Math.abs(S.target-S.pos)>.002||Math.abs(S.magT-S.mag)>.004)S.raf=requestAnimationFrame(lmaScrubTick);
 else{S.pos=S.target;S.mag=S.magT;S.last=0;lmaScrubRender()}
}
function lmaScrubGo(){if(!lmaScrub.raf)lmaScrub.raf=requestAnimationFrame(lmaScrubTick)}
function lmaScrubBubble(show,text){
 const b=lmaScrub.bub;if(!b)return;
 if(text!=null)b.textContent=text;
 b.classList.toggle('show',!!show);
}
/* switch to the tab at idx, sliding the page in from the side it lies on */
function lmaScrubCommit(idx){
 const S=lmaScrub,btn=S.items[idx];if(!btn)return;
 const from=lmaView(),fi=lmaScrubIndex(from);
 if(btn.dataset.view===from)return;
 const skip=lmaNavSkip;lmaNavSkip=true;
 try{btn.click()}finally{lmaNavSkip=skip}
 const v=document.querySelector('.view.active');
 if(v&&!document.documentElement.classList.contains('reduce-motion')){
  v.classList.remove('vslide-l','vslide-r');void v.offsetWidth;
  v.classList.add(idx>=fi?'vslide-r':'vslide-l');
  v.addEventListener('animationend',function f(e){if(e.target===v){v.classList.remove('vslide-l','vslide-r');v.removeEventListener('animationend',f)}});
 }
 window.scrollTo(0,0);
}
function lmaScrubInit(){
 const S=lmaScrub,sb=document.querySelector('.sidebar');
 if(!sb||S.sb)return;
 S.sb=sb;
 const track=document.createElement('div');track.className='navtrack';
 S.pill=document.createElement('div');S.pill.className='navpill';track.appendChild(S.pill);
 [...sb.querySelectorAll('.navbtn')].forEach(b=>track.appendChild(b));
 sb.insertBefore(track,sb.firstChild);S.track=track;
 S.bub=document.createElement('div');S.bub.className='navbubble';S.bub.setAttribute('aria-hidden','true');document.body.appendChild(S.bub);
 lmaScrubItems();
 const idx=Math.max(0,lmaScrubIndex(lmaView()));S.pos=S.target=S.cur=idx;lmaScrubRender();
 sb.addEventListener('touchstart',e=>{
  if(!lmaScrubOn()||e.touches.length!==1)return;
  const t=e.touches[0];lmaScrubItems();
  S.drag={x0:t.clientX,y0:t.clientY,start:S.pos,pos:S.pos,moved:false,lx:t.clientX,lt:performance.now(),v:0,from:lmaView(),W:Math.max(200,sb.clientWidth)};
  S.cur=Math.round(S.pos);
 },{passive:true});
 sb.addEventListener('touchmove',e=>{
  const d=S.drag;if(!d||e.touches.length!==1)return;
  const t=e.touches[0],dx=t.clientX-d.x0,n=S.items.length;
  if(!d.moved){if(Math.abs(dx)<8&&Math.abs(t.clientY-d.y0)<24)return;if(Math.abs(t.clientY-d.y0)>Math.abs(dx)*1.5){S.drag=null;return}d.moved=true;S.magT=1;lmaScrubBubble(true,(S.items[S.cur]||{}).textContent);haptic(8)}
  e.preventDefault();
  const per=d.W*.92/Math.max(1,n-1),now=performance.now();
  d.pos=Math.min(n-1,Math.max(0,d.start-dx/per));
  const dt=now-d.lt;if(dt>8){d.v=d.v*.6+((d.lx-t.clientX)/per/(dt/1000))*.4;d.lx=t.clientX;d.lt=now}
  if(Math.abs(d.pos-S.cur)>.5+LMA_SCRUB.hyst){
   S.cur=Math.round(d.pos);haptic(6);lmaScrubBubble(true,(S.items[S.cur]||{}).textContent);
   clearTimeout(S.timer);S.timer=setTimeout(()=>lmaScrubCommit(S.cur),LMA_SCRUB.live);
  }
  lmaScrubGo();
 },{passive:false});
 const end=()=>{
  const d=S.drag;if(!d)return;S.drag=null;
  if(!d.moved)return;
  clearTimeout(S.timer);S.block=Date.now();
  const n=S.items.length,tgt=Math.min(n-1,Math.max(0,Math.round(d.pos+d.v*LMA_SCRUB.vel)));
  S.target=tgt;S.cur=tgt;S.magT=0;lmaScrubBubble(false);
  lmaScrubCommit(tgt);
  if(lmaView()!==d.from&&lmaNavStack[lmaNavStack.length-1]!==d.from)lmaNavStack.push(d.from);
  lmaScrubGo();
 };
 sb.addEventListener('touchend',end,{passive:true});
 sb.addEventListener('touchcancel',end,{passive:true});
 document.addEventListener('click',e=>{if(Date.now()-S.block<350&&e.target.closest&&e.target.closest('.sidebar')){e.stopPropagation();e.preventDefault()}},true);
 window.addEventListener('resize',()=>{lmaScrubItems();lmaScrubRender()});
}
(function lmaScrubBoot(){
 const _nav=navigate;
 navigate=function(v){
  const r=_nav.apply(this,arguments);
  try{const S=lmaScrub;if(S.sb&&!S.drag){const i=lmaScrubIndex(v);if(i>=0){S.target=S.cur=i;lmaScrubGo()}}}catch(e){}
  return r;
 };
 const go=()=>{try{lmaScrubInit()}catch(e){console.warn('[scrub nav]',e)}};
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',go);else go();
 setTimeout(()=>{try{lmaScrubItems();const i=lmaScrubIndex(lmaView());if(i>=0&&!lmaScrub.drag){lmaScrub.target=lmaScrub.pos=lmaScrub.cur=i;lmaScrubRender()}}catch(e){}},1200);
})();
