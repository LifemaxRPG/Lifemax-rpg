/* ===== Sound for the Quest Board and the Luck dice =====
   Short recorded effects (Kenney Casino Audio, CC0) played through one shared AudioContext. They follow the existing
   "Sound" switch in Settings and have their own volume. Sounds decode the first time they are used. */
var QSFX=(function(){
 var ac=null,bufs={},pend={},last={},groups={};
 Object.keys(typeof SFX_DATA!=='undefined'?SFX_DATA:{}).forEach(function(k){var g=k.replace(/-\d+$/,'');(groups[g]=groups[g]||[]).push(k)});
 function vol(){try{var v=parseFloat(localStorage.getItem('lma_sfxvol'));return isNaN(v)?.7:Math.max(0,Math.min(1,v))}catch(e){return .7}}
 function on(){try{return fxOpt().sound&&vol()>0&&!document.hidden}catch(e){return false}}
 function ctx(){if(ac)return ac;try{var C=window.AudioContext||window.webkitAudioContext;if(!C)return null;ac=new C()}catch(e){ac=null}return ac}
 function load(k){
  if(bufs[k])return Promise.resolve(bufs[k]);if(pend[k])return pend[k];
  var a=ctx();if(!a||!SFX_DATA[k])return Promise.resolve(null);
  var bin=atob(SFX_DATA[k]),u=new Uint8Array(bin.length);for(var i=0;i<bin.length;i++)u[i]=bin.charCodeAt(i);
  pend[k]=new Promise(function(res){try{var p=a.decodeAudioData(u.buffer,function(b){bufs[k]=b;res(b)},function(){res(null)});if(p&&p.then)p.then(function(b){bufs[k]=b;res(b)},function(){res(null)})}catch(e){res(null)}});
  return pend[k];
 }
 function pick(name){
  if(SFX_DATA[name])return name;var g=groups[name];if(!g||!g.length)return null;
  var k;for(var tries=0;tries<4;tries++){k=g[Math.floor(Math.random()*g.length)];if(g.length<2||k!==last[name])break}last[name]=k;return k;
 }
 /* name is a group ("card-place", "dice-throw") or one exact file. o: {vol, rate, delay} */
 function play(name,o){
  o=o||{};if(!on())return;var k=pick(name);if(!k)return;var a=ctx();if(!a)return;
  if(a.state==='suspended')try{a.resume()}catch(e){}
  load(k).then(function(b){if(!b)return;try{var s=a.createBufferSource(),g=a.createGain();s.buffer=b;s.playbackRate.value=(o.rate||1)*(1+(Math.random()-.5)*.08);g.gain.value=Math.max(0,Math.min(1.4,(o.vol==null?1:o.vol)*vol()));s.connect(g);g.connect(a.destination);s.start(a.currentTime+(o.delay||0))}catch(e){}});
 }
 function setVol(v){try{localStorage.setItem('lma_sfxvol',String(v))}catch(e){}}
 return {play:play,on:on,vol:vol,setVol:setVol,has:function(n){return !!(SFX_DATA[n]||groups[n])},groups:function(){return Object.keys(groups)}};
})();
function qsfxSetVol(v){QSFX.setVol(+v);toast('Quest and dice sound: '+(+v===0?'off':v<.5?'low':v<.8?'medium':'high'));QSFX.play('card-place',{vol:1})}
setTimeout(function(){try{var s=document.getElementById('qsfxVol');if(s)s.value=String(QSFX.vol()>=.9?1:QSFX.vol()>=.6?.7:QSFX.vol()>0?.35:0)}catch(e){}},0);
