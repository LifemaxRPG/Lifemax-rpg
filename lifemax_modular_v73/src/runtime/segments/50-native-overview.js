/* Live hybrid Overview: Android header/navigation over the original resumed WebView.
   The page body, pets and timers are never captured or copied. */
(function(){
 const N={open:false,opening:false,session:'',plugin:null,ready:null,actions:new Map(),navActions:new Map(),maps:new Map(),buildActions:null,seq:0,painting:false,lastPaint:'',queued:false,suspended:false};
 const ids=new WeakMap(),assets=new Map();let nextId=0;
 const cssCanvas=document.createElement('canvas'),colorCtx=cssCanvas.getContext('2d',{willReadFrequently:true});cssCanvas.width=cssCanvas.height=1;
 let colorProbe;
 function color(value){
  if(!value||value==='none'||value==='transparent')return 0;
  // CSS color-mix resolves to color(srgb ...) on Android. Some WebView Canvas
  // versions cannot parse that syntax and silently retain the previous fill.
  if(value.startsWith('color-mix(')){
   if(!colorProbe){colorProbe=document.createElement('span');colorProbe.style.cssText='display:none;position:fixed;pointer-events:none';document.documentElement.appendChild(colorProbe)}
   colorProbe.style.color=value;const resolved=getComputedStyle(colorProbe).color;
   if(resolved!==value)return color(resolved);
  }
  const srgb=value.match(/^color\(\s*srgb\s+([^\s]+)\s+([^\s]+)\s+([^\s/)]+)(?:\s*\/\s*([^\s)]+))?\s*\)$/);
  if(srgb){const channel=v=>Math.round(Math.max(0,Math.min(1,parseFloat(v)/(v.endsWith('%')?100:1)))*255);return((channel(srgb[4]||'1')<<24)|(channel(srgb[1])<<16)|(channel(srgb[2])<<8)|channel(srgb[3]))>>>0}
  colorCtx.clearRect(0,0,1,1);colorCtx.fillStyle=value;colorCtx.fillRect(0,0,1,1);const a=colorCtx.getImageData(0,0,1,1).data;return ((a[3]<<24)|(a[0]<<16)|(a[1]<<8)|a[2])>>>0
 }
 const num=v=>parseFloat(v)||0;
 // innerWidth rounds CSS pixels on Android; the fractional layout width keeps
 // native coordinates aligned with the WebView at non-integer screen densities.
 const viewportWidth=()=>document.documentElement.getBoundingClientRect().width||innerWidth;
 const split=v=>{let depth=0,start=0,out=[];for(let i=0;i<v.length;i++){if(v[i]==='(')depth++;if(v[i]===')')depth--;if(v[i]===','&&!depth){out.push(v.slice(start,i).trim());start=i+1}}out.push(v.slice(start).trim());return out};
 function plugin(){try{const c=window.Capacitor;return c&&c.isNativePlatform()&&c.isPluginAvailable('NativeOverview')?c.Plugins.NativeOverview:null}catch(_){return null}}
 function enabled(){return typeof lmaLoad!=='function'||lmaLoad().nativeOverview!==false}
 function syncSetting(){
  const input=document.getElementById('nativeOverviewToggle'),hint=document.getElementById('nativeOverviewHint');
  if(!input)return;
  const available=!!plugin();input.checked=available&&enabled();input.disabled=!available;
  if(hint)hint.textContent=available?'Use the Android Overview screen. Turn off to return to the classic Overview.':typeof lmaNative==='function'&&lmaNative()?'Install the latest Android update to use this Overview.':'Available in the Android app.';
 }
 async function setEnabled(on){
  if(typeof lmaLoad!=='function'||typeof lmaSave!=='function')return;
  lmaLoad().nativeOverview=!!on;lmaSave();syncSetting();
  if(!on)mask(false);
  if(!on&&(N.open||N.opening)&&N.plugin&&N.session)await N.plugin.close({session:N.session});
  if(on&&lmaView()==='dashboard')await open(true);
 }
 window.setNativeOverviewEnabled=setEnabled;
 window.syncNativeOverviewSetting=syncSetting;
 function blocked(){return !enabled()||!state.onboardingComplete||lmaView()!=='dashboard'||window.innerWidth>860||typeof lmaThumbWarmBusy!=='undefined'&&lmaThumbWarmBusy||typeof lmaTopOverlay==='function'&&!!lmaTopOverlay()||document.querySelector('#onboarding:not([aria-hidden="true"]).show,#tutorial.show,.ld-sheet.open,.sheet-ov.show,.updpop,.hsheet,.apps-sheet,.lock-ov,.journal-lock')||document.body.classList.contains('noscroll')}
 function identify(el){if(!ids.has(el))ids.set(el,'o'+(++nextId));const id=ids.get(el);(N.buildActions||N.actions).set(id,el);if(el.closest('.sidebar'))N.navActions.set(id,el);return id}
 const drawableProps=['fill','stroke','stroke-width','stroke-linecap','stroke-linejoin','stroke-dasharray','stroke-dashoffset','fill-opacity','stroke-opacity','opacity','color','font-family','font-size','font-weight','letter-spacing','text-anchor','dominant-baseline','paint-order','display','visibility','filter'];
 async function svgImage(el){const clone=el.cloneNode(true),src=[el,...el.querySelectorAll('*')],dst=[clone,...clone.querySelectorAll('*')];for(let i=0;i<src.length;i++){const cs=getComputedStyle(src[i]);for(const p of drawableProps){let v=cs.getPropertyValue(p);v=v.replace(/url\("?[^"#)]*(#[^")]+)"?\)/g,'url($1)');dst[i].style.setProperty(p,v)}}clone.setAttribute('xmlns','http://www.w3.org/2000/svg');const rect=el.getBoundingClientRect();clone.setAttribute('width',String(Math.max(1,rect.width)));clone.setAttribute('height',String(Math.max(1,rect.height)));return raster('data:image/svg+xml;charset=utf-8,'+encodeURIComponent(new XMLSerializer().serializeToString(clone)),rect.width,rect.height)}
 async function raster(src,w,h){const key=src+'|'+w+'|'+h;if(assets.has(key))return assets.get(key);const task=new Promise((ok,no)=>{const im=new Image();im.onload=()=>{try{const cv=document.createElement('canvas');const d=Math.min(devicePixelRatio||1,3);cv.width=Math.max(1,Math.ceil(w*d));cv.height=Math.max(1,Math.ceil(h*d));cv.getContext('2d').drawImage(im,0,0,cv.width,cv.height);ok(cv.toDataURL('image/png'))}catch(e){no(e)}};im.onerror=no;im.src=src});assets.set(key,task);if(assets.size>160)assets.delete(assets.keys().next().value);return task}
 function gradients(s){return split(s.backgroundImage||'none').filter(v=>/^(linear|radial)-gradient/.test(v)).map(v=>{const terms=split(v.slice(v.indexOf('(')+1,-1)),first=terms[0];const colors=terms.filter(t=>/^(rgba?\(|#[\da-f]|color(?:-mix)?\(|transparent)/i.test(t));return{radial:v.startsWith('radial'),direction:first,positions:colors.map((t,i)=>{const m=t.match(/\)\s+(-?[\d.]+)%$|transparent\s+(-?[\d.]+)%$|#[\da-f]+\s+(-?[\d.]+)%$/i);return m?num(m[1]||m[2]||m[3])/100:i/Math.max(1,colors.length-1)}),colors:colors.map(t=>color(t.replace(/\s+-?[\d.]+%$/, '')))}})}
 async function capture(roots,nav){const nodes=[],hits=[],offset=nav?roots[0].getBoundingClientRect():{x:0,y:-scrollY};let maxY=0;
  const actionSelector='button,[onclick],[role="button"],.ov-bar,#ovHero,#petPeek,#ovHealth,#ovRewards,.ova-app,.ld-glance';
  const rect=el=>{const r=el.getBoundingClientRect();if(nav&&el===roots[0])return{x:0,y:0,w:el.scrollWidth,h:r.height};return{x:r.x-offset.x+(nav?roots[0].scrollLeft:0),y:r.y-offset.y,w:r.width,h:r.height}};
  function box(el,opacity){const s=getComputedStyle(el),r=rect(el);if(!r.w||!r.h||s.display==='none'||s.visibility==='hidden'||el.hidden)return null;const o=opacity*(el.dataset.nativeOverviewMasked?1:num(s.opacity||1));if(!o)return null;maxY=Math.max(maxY,r.y+r.h);const borders=['Top','Right','Bottom','Left'].map(side=>({width:num(s['border'+side+'Width']),color:color(s['border'+side+'Color'])}));const bg=color(s.backgroundColor),bw=num(s.borderTopWidth),br=num(s.borderTopLeftRadius);const g=gradients(s);if(bg||borders.some(b=>b.width)||g.length)nodes.push({kind:'box',...r,color:bg,border:color(s.borderTopColor),borderWidth:bw,borders,radius:br,opacity:o,gradients:g,shadows:split(s.boxShadow||'none').filter(v=>v!=='none').map(v=>({inset:v.includes('inset'),color:color((v.match(/rgba?\([^)]*\)|color\([^)]*\)/)||['transparent'])[0]),lengths:(v.match(/-?[\d.]+px/g)||[]).map(num)}))});
   const action=el.matches(actionSelector);if(action&&!el.disabled){const id=identify(el);hits.push({...r,id,label:el.getAttribute('aria-label')||el.textContent.trim()||el.title||'Open',button:true,stay:el.matches('.ov-bar')})}
   return{s,r,o};
  }
  async function walk(el,opacity=1){const info=box(el,opacity);if(!info)return;const{s,r,o}=info;const clipped=/hidden|clip|auto|scroll/.test(s.overflowX+' '+s.overflowY);if(clipped)nodes.push({kind:'clip',...r,radius:num(s.borderTopLeftRadius)});const end=()=>{if(clipped)nodes.push({kind:'unclip'})};const ps=getComputedStyle(el,'::before');if(ps.content==='\"\"'&&ps.position==='absolute'&&num(ps.width)>0){const left=num(ps.left),top=num(ps.top),width=num(ps.width),height=num(ps.height)||r.h-top-num(ps.bottom)-num(s.borderTopWidth)-num(s.borderBottomWidth);nodes.push({kind:'box',x:r.x+num(s.borderLeftWidth)+left,y:r.y+num(s.borderTopWidth)+top,w:width,h:height,color:color(ps.backgroundColor),opacity:o*num(ps.opacity||1),radius:num(ps.borderTopLeftRadius),gradients:gradients(ps),shadows:split(ps.boxShadow||'none').filter(v=>v!=='none').map(v=>({inset:v.includes('inset'),color:color((v.match(/rgba?\([^)]*\)|color\([^)]*\)/)||['transparent'])[0]),lengths:(v.match(/-?[\d.]+px/g)||[]).map(num)}))})}
   if(el.matches('svg,canvas')){const data=el.tagName.toLowerCase()==='canvas'?el.toDataURL('image/png'):await svgImage(el);nodes.push({kind:'image',...r,data,opacity:o});if(el.matches('svg'))for(const target of el.querySelectorAll('[data-act],[role="slider"]')){const q=rect(target),path=target.matches('path')?target:target.querySelector('path.ld-hit'),m=path&&path.getScreenCTM();if(q.w&&q.h)hits.push({...q,id:identify(target),label:target.getAttribute('aria-label')||target.textContent||'Open session',path:path&&path.getAttribute('d'),stroke:path?num(path.getAttribute('stroke-width')||getComputedStyle(path).strokeWidth):0,matrix:m?[m.a,m.c,m.e-offset.x,m.b,m.d,m.f-offset.y,0,0,1]:null})}end();return}
   if(el.matches('img')){if(el.complete&&el.naturalWidth)nodes.push({kind:'image',...r,data:await raster(el.src,r.w,r.h),opacity:o});end();return}
   const image=(s.backgroundImage||'').match(/url\(["']?(.*?)["']?\)/);if(image){const pos=s.backgroundPosition.split(' ').map(num),size=s.backgroundSize.split(' ').map(num);nodes.push({kind:'backgroundImage',...r,data:image[1].startsWith('data:image/')?image[1]:await raster(new URL(image[1],location.href).href,r.w,r.h),opacity:o,position:pos,size,cover:s.backgroundSize==='cover',radius:num(s.borderTopLeftRadius)})}
   for(const child of el.childNodes){if(child.nodeType===1)await walk(child,o);else if(child.nodeType===3&&child.textContent.trim())text(child,s,o)}end();
  }
  function text(node,s,opacity){const range=document.createRange();range.selectNodeContents(node);const rects=[...range.getClientRects()].filter(r=>r.width&&r.height);if(!rects.length)return;const value=node.textContent.replace(/\s+/g,' ');const font=num(s.fontSize),weight=num(s.fontWeight)||400;
   function emit(str,r){if(!str.trim())return;if(!node.parentElement.closest(actionSelector))hits.push({x:r.x-offset.x+(nav?roots[0].scrollLeft:0),y:r.y-offset.y,w:r.width,h:r.height,id:'',label:str,button:false});nodes.push({kind:'text',x:r.x-offset.x+(nav?roots[0].scrollLeft:0),y:r.y-offset.y,w:r.width,h:r.height,text:s.textTransform==='uppercase'?str.toUpperCase():str,color:color(s.color),font,weight,spacing:num(s.letterSpacing),mono:/mono/i.test(s.fontFamily),italic:s.fontStyle==='italic',opacity})}
   if(rects.length===1){emit(value,rects[0]);return}
   let line='',last=null;for(let i=0;i<node.textContent.length;i++){range.setStart(node,i);range.setEnd(node,i+1);const r=range.getBoundingClientRect();if(!r.width)continue;if(last&&Math.abs(last.y-r.y)>2){emit(line,last);line='';last=null}if(!last)last={x:r.x,y:r.y,width:r.width,height:r.height};else last.width=r.right-last.x;line+=node.textContent[i]}if(last)emit(line,last);
  }
  for(const root of roots)if(root)await walk(root);return{nodes,hits,height:maxY,width:nav?roots[0].scrollWidth:viewportWidth()};
 }
 function mask(on){for(const el of [document.querySelector('.topbar'),document.querySelector('.sidebar')])if(el){if(on){if(!el.dataset.nativeOverviewMasked){el.dataset.nativeOverviewOpacity=el.style.opacity;el.dataset.nativeOverviewMasked='1';el.style.opacity='0'}}else if(el.dataset.nativeOverviewMasked){el.style.opacity=el.dataset.nativeOverviewOpacity||'';delete el.dataset.nativeOverviewMasked;delete el.dataset.nativeOverviewOpacity}}}
 async function snapshot(){if(document.fonts)await document.fonts.ready;N.buildActions=new Map();const header=await capture([document.querySelector('.topbar')]);const sidebar=document.querySelector('.sidebar'),nav=await capture([sidebar],true);N.actions=N.buildActions;N.buildActions=null;N.maps.set(++N.seq,N.actions);while(N.maps.size>4)N.maps.delete(N.maps.keys().next().value);return{version:2,sequence:N.seq,width:viewportWidth(),header,nav,navScroll:sidebar.scrollLeft,background:color(getComputedStyle(document.body).backgroundColor)}}
 function signature(scene){return JSON.stringify({width:scene.width,height:scene.height,background:scene.background,light:scene.light,header:scene.header,nav:scene.nav})}
 async function send(){if(!N.open)return;if(blocked()){await N.plugin.close({session:N.session});return}if(N.painting){N.queued=true;return}N.painting=true;try{const scene=await snapshot(),paint=signature(scene);if(N.open&&paint!==N.lastPaint){await N.plugin.update({session:N.session,snapshot:scene});N.lastPaint=paint}}catch(e){console.warn('Native Overview refresh',e);mask(false);await N.plugin.close({session:N.session});N.open=false}finally{N.painting=false;if(N.queued){N.queued=false;setTimeout(send,80)}}}
 async function ready(p){if(!N.ready)N.ready=(async()=>{await p.addListener('failed',e=>{if(e.session===N.session){window.__nativeOverviewError=e.reason;console.warn('Native Overview',e.reason)}});await p.addListener('refresh',e=>{if(e.session===N.session)send()});await p.addListener('activate',e=>{if(e.session!==N.session)return;const target=(N.maps.get(e.sequence)||N.actions).get(e.action);if(target&&target.isConnected)target.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true}));send()});await p.addListener('closed',e=>{if(e.session!==N.session)return;N.open=false;N.opening=false;N.suspended=true;mask(false);if(Number.isFinite(e.scroll))scrollTo(0,e.scroll);const target=(N.maps.get(e.sequence)||N.actions).get(e.action);if(target&&target.isConnected){target.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true}))}else if(e.action)toast('Overview changed. Please try that action again.');if(lmaView()==='dashboard'&&enabled())setTimeout(()=>{if(!N.open&&!N.opening&&!blocked())open()},100)})})().catch(e=>{N.ready=null;throw e});return N.ready}
 function diagnostics(){return{plugin:!!plugin(),view:lmaView(),onboarded:!!state.onboardingComplete,width:innerWidth,overlay:typeof lmaTopOverlay==='function'&&!!lmaTopOverlay(),bodyClasses:document.body.className,blocked:!!blocked(),open:N.open,opening:N.opening}}
 async function open(force){
  if(window.__nativeOverviewManual&&!force)return{opened:false,reason:'manual'};
  const p=plugin();if(!p||N.open||N.opening||blocked())return{opened:N.open,diagnostics:diagnostics()};
  N.opening=true;N.plugin=p;
  try{const capabilities=await p.capabilities().catch(()=>null);if(!capabilities?.liveWeb)return{opened:false,reason:'live-shell-update-required'};await ready(p);await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));const page=document.getElementById('view-dashboard');const animations=page&&page.getAnimations?page.getAnimations({subtree:true}).filter(a=>a.effect&&a.effect.getTiming().iterations!==Infinity):[];if(animations.length)await Promise.race([Promise.allSettled(animations.map(a=>a.finished)),new Promise(r=>setTimeout(r,700))]);if(blocked())return{opened:false,diagnostics:diagnostics()};
   const scene=await snapshot();if(blocked())return{opened:false,diagnostics:diagnostics()};N.session='overview_'+Date.now();N.open=true;N.suspended=false;await p.open({session:N.session,snapshot:scene});N.lastPaint=signature(scene);await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));if(N.open)mask(true);return{opened:N.open};
  }catch(e){N.open=false;mask(false);console.warn('Native Overview',e);toast('Native Overview could not open. Your original Overview is available.');return{opened:false,error:String(e)}}finally{N.opening=false}
 }

 const nav=navigate;navigate=function(v){N.suspended=false;const result=nav.apply(this,arguments);if(v==='dashboard')setTimeout(open,80);if(v==='settings')syncSetting();if(v!=='dashboard'&&N.open){mask(false);N.plugin.close({session:N.session})}return result};
 window.openNativeOverview=()=>open(true);
 // The first-install tab-thumbnail pass navigates through every web page. It must
 // not navigate the shared host behind a visible native Overview. Settings can
 // still request that pass after returning to the web tools.
 if(typeof lmaThumbWarm==='function'){const warm=lmaThumbWarm;lmaThumbWarm=function(){if(N.open||N.opening)return Promise.resolve();return warm.apply(this,arguments)}}
 // Refresh only after the shared model has rendered; native owns no gameplay state.
 const ro=renderOverview;renderOverview=function(){const r=ro.apply(this,arguments);if(N.open)send();return r};
 new MutationObserver(()=>{if(N.open&&blocked()){mask(false);N.plugin.close({session:N.session})}}).observe(document.body,{childList:true,subtree:true,attributes:true,attributeFilter:['class']});
 setTimeout(()=>{if(plugin()&&!blocked())open()},1800);
 syncSetting();setTimeout(syncSetting,700);
 window.NativeOverviewSnapshot={capture,snapshot,color,split,diagnostics,activate:id=>{const target=N.navActions.get(id)||N.actions.get(id);if(target?.isConnected)target.click()}};
})();

