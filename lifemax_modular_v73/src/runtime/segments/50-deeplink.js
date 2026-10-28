/* v193: deep links from Google Assistant / Gemini / launcher shortcuts.
   lifemax://schedule/add?title=..&description=..   -> hand the title to the voice engine (it asks for the time)
   lifemax://cats/feed                              -> feed every living cat
   lifemax://open?feature=..                        -> open schedule / cats / quests
   Links that arrive before the app is ready wait in a queue. */
var LM_DL={q:[],ready:false,seen:{}};
function lmDlFeature(f){
 f=String(f||'').toLowerCase().replace(/[^a-z ]/g,' ').replace(/\s+/g,' ').trim();
 if(/\b(feed|feeding|food)\b/.test(f)&&/\bcats?\b|\bkittens?\b|feeding|^feed$/.test(f))return 'feed';
 if(/\b(schedule|calendar|director|agenda|plan)\b/.test(f))return 'schedule';
 if(/\bquests?\b/.test(f))return 'quests';
 if(/\bcats?\b|\bpets?\b|\bkittens?\b/.test(f))return 'cats';
 return '';
}
function lmDlParse(url){
 var m=/^lifemax:\/\/([^\/?#]*)([^?#]*)(?:\?([^#]*))?/i.exec(String(url||'').trim());
 if(!m)return null;
 var q={};(m[3]||'').split('&').forEach(function(kv){if(!kv)return;var i=kv.indexOf('='),k=i<0?kv:kv.slice(0,i),v=i<0?'':kv.slice(i+1);
  try{q[decodeURIComponent(k)]=decodeURIComponent(v.replace(/\+/g,' '))}catch(e){q[k]=v}});
 var host=m[1].toLowerCase(),path=m[2].replace(/^\/|\/$/g,'').toLowerCase();
 if(host==='schedule')return {kind:'schedule',title:(q.title||q.name||'').trim(),description:(q.description||'').trim()};
 if(host==='cats'||host==='cat')return {kind:path==='feed'?'feed':'cats'};
 if(host==='open'){var f=lmDlFeature(q.feature);return f?{kind:f}:{kind:'none',raw:q.feature||''}}
 return null;
}
function lmDlFeedAll(){
 var out=[],n=(state.pet2&&state.pet2.alive)?2:((state.pet&&state.pet.alive)?1:0);
 if(!n)return {fed:[],full:[],none:true};
 var fed=[],full=[],vac=false;
 for(var i=0;i<n;i++){
  var p=petPairP(i),r=petAs(i,petFeed,[],true);
  if(r.ok)fed.push(p.name);else if(r.why==='vacation')vac=true;else full.push(p.name);
 }
 if(fed.length){try{save()}catch(e){}}
 return {fed:fed,full:full,vac:vac};
}
function lmDlSay(t){try{toast(t)}catch(e){}}
function lmDlRun(link){
 if(!link)return false;
 try{
  if(link.kind==='feed'){
   var r=lmDlFeedAll();
   if(r.none)lmDlSay('You do not have a cat to feed yet.');
   else if(r.vac)lmDlSay('Vacation mode is on. Turn it off to feed your cats.');
   else if(r.fed.length)lmDlSay('Fed '+r.fed.join(' and ')+(r.full.length?'. '+r.full.join(' and ')+' is full.':''));
   else lmDlSay(r.full.join(' and ')+(r.full.length>1?' are':' is')+' not hungry yet.');
   lmvGo({id:'pet'});try{if(typeof petRenderStatus==='function')petRenderStatus()}catch(e){}
   return true;
  }
  if(link.kind==='cats'){lmvGo({id:'pet'});return true}
  if(link.kind==='quests'){lmvGo({id:'quests'});return true}
  if(link.kind==='schedule'){
   if(!link.title){lmvGo({id:'dashboard'});return true}
   try{lmvBuildUI();lmvHudOpen(true)}catch(e){}
   LifeMaxVoice.simulateUtterance('Put '+link.title+' on my schedule'+(link.description?' ('+link.description+')':''));
   return true;
  }
 }catch(e){console.warn('[deeplink]',e)}
 return false;
}
function lmHandleDeepLink(url){
 if(!url)return false;
 var link=lmDlParse(url);if(!link)return false;
 /* the same link can be delivered twice (launch url + appUrlOpen): ignore a repeat within 3 s */
 var now=Date.now();if(LM_DL.seen[url]&&now-LM_DL.seen[url]<3000)return true;LM_DL.seen[url]=now;
 if(!LM_DL.ready){LM_DL.q.push(link);return true}
 return lmDlRun(link);
}
function lmDlFlush(){LM_DL.ready=true;var q=LM_DL.q.splice(0);q.forEach(lmDlRun)}
(function(){
 var A=window.Capacitor&&Capacitor.Plugins&&Capacitor.Plugins.App;
 if(A){
  try{A.addListener('appUrlOpen',function(e){lmHandleDeepLink(e&&e.url)})}catch(e){}
  try{A.getLaunchUrl().then(function(r){if(r&&r.url)lmHandleDeepLink(r.url)}).catch(function(){})}catch(e){}
 }
 /* wait for the pets and the voice engine to finish loading before acting on a queued link */
 setTimeout(lmDlFlush,1800);
})();
window.LifeMaxDeepLink={handle:lmHandleDeepLink,parse:lmDlParse};
