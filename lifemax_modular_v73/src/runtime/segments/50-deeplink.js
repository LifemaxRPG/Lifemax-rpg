/* v193: deep links from Google Assistant / Gemini / launcher shortcuts.
   lifemax://schedule/add?title=..&description=..   -> hand the title to the voice engine (it asks for the time)
   lifemax://cats/feed                              -> open the cat room and ask before feeding every hungry cat
   lifemax://open?feature=..                        -> open schedule / cats / quests
   Links that arrive before the app is ready wait in a queue.
   Hardening (backlog SEC-3): any app or web page can send these links, so a link never changes the save by itself.
   Titles and descriptions are cut to 120 characters, with control and direction-override characters removed; a schedule
   request stops at the voice preview (LifeMaxVoice.fromLink); "feed" opens the cat room and asks first. A link Android
   hands back after a reload of the page in the same session (an over-the-air restart) does not run again. */
var LM_DL={q:[],ready:false,seen:{},maxText:120,maxUrl:4096,maxQueue:5,ranKey:'lifemax_dl_ran'};
function lmDlFeature(f){
 f=String(f||'').toLowerCase().replace(/[^a-z ]/g,' ').replace(/\s+/g,' ').trim();
 if(/\b(feed|feeding|food)\b/.test(f)&&/\bcats?\b|\bkittens?\b|feeding|^feed$/.test(f))return 'feed';
 if(/\b(schedule|calendar|director|agenda|plan)\b/.test(f))return 'schedule';
 if(/\bquests?\b/.test(f))return 'quests';
 if(/\bcats?\b|\bpets?\b|\bkittens?\b/.test(f))return 'cats';
 return '';
}
/* text from a link: no control or invisible formatting characters (direction overrides among them), except the zero-width
   joiner and non-joiner that emoji and some scripts need; single spaces; at most max characters */
function lmDlText(s,max){
 s=String(s==null?'':s).replace(/[\u0000-\u001f\u007f-\u009f\u00ad\u061c\u180e\u200b\u200e\u200f\u2028-\u202e\u2060-\u206f\ufeff\ufff9-\ufffb]/g,' ').replace(/\s+/g,' ').trim();
 var cp=Array.from(s);
 return cp.length>max?cp.slice(0,max).join('').trim():s;
}
function lmDlParse(url){
 url=String(url||'').trim();
 if(url.length>LM_DL.maxUrl)return null;
 var m=/^lifemax:\/\/([^\/?#]*)([^?#]*)(?:\?([^#]*))?/i.exec(url);
 if(!m)return null;
 var q={};(m[3]||'').split('&').forEach(function(kv){if(!kv)return;var i=kv.indexOf('='),k=i<0?kv:kv.slice(0,i),v=i<0?'':kv.slice(i+1);
  try{q[decodeURIComponent(k)]=decodeURIComponent(v.replace(/\+/g,' '))}catch(e){q[k]=v}});
 var host=m[1].toLowerCase(),path=m[2].replace(/^\/|\/$/g,'').toLowerCase(),T=LM_DL.maxText;
 if(host==='schedule')return {kind:'schedule',title:lmDlText(q.title||q.name,T),description:lmDlText(q.description,T)};
 if(host==='cats'||host==='cat')return {kind:path==='feed'?'feed':'cats'};
 if(host==='open'){var ft=lmDlText(q.feature,T),f=lmDlFeature(ft);return f?{kind:f}:{kind:'none',raw:ft}}
 return null;
}
function lmDlAnd(names){return names.length>1?names.slice(0,-1).join(', ')+' and '+names[names.length-1]:names.join('')}
function lmDlNotHungry(names){return lmDlAnd(names)+(names.length>1?' are':' is')+' not hungry yet.'}
/* why nobody can be fed, from a plan or a feeding result */
function lmDlNobody(r){return r.none?'You do not have a cat to feed yet.':r.vac?'Vacation mode is on. Turn it off to feed your cats.':lmDlNotHungry(r.full)}
/* the living cats, as petPairP / petAs indexes */
function lmDlCats(){return [0,1].filter(function(i){var p=petPairP(i);return !!(p&&p.alive)})}
/* who feeding would reach, without feeding anyone */
function lmDlFeedPlan(){
 var cats=lmDlCats(),now=Date.now(),hungry=[],full=[],vac=false;
 cats.forEach(function(i){
  var p=petPairP(i);
  if(p.vacation&&p.vacation.on)vac=true;else if(petCanFeed(p,now))hungry.push(p.name);else full.push(p.name);
 });
 return {none:!cats.length,hungry:hungry,full:full,vac:vac};
}
function lmDlFeedAll(){
 var cats=lmDlCats(),fed=[],full=[],vac=false;
 cats.forEach(function(i){
  var p=petPairP(i),r=petAs(i,petFeed,[],true);
  if(r.ok)fed.push(p.name);else if(r.why==='vacation')vac=true;else full.push(p.name);
 });
 if(fed.length){try{save()}catch(e){}}
 return {none:!cats.length,fed:fed,full:full,vac:vac};
}
function lmDlSay(t){try{toast(t)}catch(e){}}
function lmDlFeedNow(){
 var r=lmDlFeedAll();
 lmDlSay(r.fed.length?'Fed '+lmDlAnd(r.fed)+'.'+(r.full.length?' '+lmDlNotHungry(r.full):''):lmDlNobody(r));
 try{if(typeof petRenderStatus==='function')petRenderStatus()}catch(e){}
 return r;
}
/* "feed the cats" from a link: open the cat room and ask; only the Feed button feeds */
function lmDlAskFeed(){
 lmvGo({id:'pet'});petRoomFullscreenClose();   /* the question would sit under the full-screen room */
 var plan=lmDlFeedPlan(),old=document.querySelector('.lmdl-ask');if(old)old.remove();
 if(!plan.hungry.length){lmDlSay(lmDlNobody(plan));return}
 var o=document.createElement('div');o.className='lmdl-ask';o.setAttribute('role','dialog');o.setAttribute('aria-modal','true');o.setAttribute('aria-label','Feed your cats?');
 o.innerHTML='<div class="updpop-card"><h3>Feed '+esc(lmDlAnd(plan.hungry))+'?</h3><p>A shortcut or a link asked LifeMax to feed your cats.'+
  (plan.full.length?' '+esc(lmDlNotHungry(plan.full)):'')+'</p>'+
  '<div class="updpop-btns"><button type="button" class="btn" data-dl="feed">Feed</button><button type="button" class="btn secondary" data-dl="no">Not now</button></div></div>';
 o.addEventListener('click',function(e){
  var b=e.target.closest('[data-dl]');
  if(!b){if(e.target===o)o.remove();return}
  o.remove();if(b.dataset.dl==='feed')lmDlFeedNow();
 });
 document.body.appendChild(o);
 try{o.querySelector('[data-dl="feed"]').focus()}catch(e){}
}
/* the links already acted on in this session of the page (sessionStorage survives a reload, not a new process) */
function lmDlRan(){try{var a=JSON.parse(sessionStorage.getItem(LM_DL.ranKey)||'[]');return Array.isArray(a)?a:[]}catch(e){return []}}
function lmDlMarkRan(url){if(!url)return;try{var a=lmDlRan().filter(function(u){return u!==url});a.push(url);sessionStorage.setItem(LM_DL.ranKey,JSON.stringify(a.slice(-8)))}catch(e){}}
function lmDlRun(link){
 if(!link)return false;
 lmDlMarkRan(link.url);
 try{
  if(link.kind==='feed'){lmDlAskFeed();return true}
  if(link.kind==='cats'){lmvGo({id:'pet'});return true}
  if(link.kind==='quests'){lmvGo({id:'quests'});return true}
  if(link.kind==='schedule'){
   if(!link.title){lmvGo({id:'dashboard'});return true}
   /* the preview is drawn on the page: leave a full-screen native tab for the Overview, which leaves the page showing */
   var cv=lmaView();if(window.NativeTabs&&cv!=='dashboard'&&NativeTabs.covered(cv))lmaNavTo('dashboard',{fast:true});
   try{lmvBuildUI();lmvHudOpen(true)}catch(e){}
   LifeMaxVoice.fromLink('Put '+link.title+' on my schedule'+(link.description?' ('+link.description+')':''));
   return true;
  }
 }catch(e){console.warn('[deeplink]',e)}
 return false;
}
/* launch: the URL the activity was started with (getLaunchUrl). The page asks for it on every load, so after an
   over-the-air restart it would come back; one already acted on in this session is skipped. */
function lmHandleDeepLink(url,launch){
 if(!url)return false;
 var link=lmDlParse(url);if(!link)return false;
 url=String(url).trim();link.url=url;
 if(launch&&lmDlRan().indexOf(url)>=0)return true;
 /* the same link can be delivered twice (launch url + appUrlOpen): ignore a repeat within 3 s */
 var now=Date.now();
 Object.keys(LM_DL.seen).forEach(function(k){if(now-LM_DL.seen[k]>=3000)delete LM_DL.seen[k]});
 if(LM_DL.seen[url])return true;LM_DL.seen[url]=now;
 if(!LM_DL.ready){if(LM_DL.q.length<LM_DL.maxQueue)LM_DL.q.push(link);return true}
 return lmDlRun(link);
}
function lmDlFlush(){LM_DL.ready=true;var q=LM_DL.q.splice(0);q.forEach(lmDlRun)}
(function(){
 var A=window.Capacitor&&Capacitor.Plugins&&Capacitor.Plugins.App;
 if(A){
  try{A.addListener('appUrlOpen',function(e){lmHandleDeepLink(e&&e.url)})}catch(e){}
  try{A.getLaunchUrl().then(function(r){if(r&&r.url)lmHandleDeepLink(r.url,true)}).catch(function(){})}catch(e){}
 }
 /* wait for the pets and the voice engine to finish loading before acting on a queued link */
 setTimeout(lmDlFlush,1800);
})();
window.LifeMaxDeepLink={handle:lmHandleDeepLink,parse:lmDlParse};
