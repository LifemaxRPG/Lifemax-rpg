/* ===== The threshold: the splash you see when you first open the Quest Board each day =====
   A dark void, a watching eye, candles and drifting embers, and one button. Pressing it lights the way into the reading. */
var dhGating=false;
function dhGateBox(){
 const sb=document.querySelector('.sidebar'),r=sb?sb.getBoundingClientRect():null;
 if(r&&r.top>innerHeight*.5)return {left:0,top:0,right:0,bottom:Math.max(0,innerHeight-r.top)};/* phone: stop above the bottom bar so you can still leave */
 return {left:r&&r.width<innerWidth*.4?r.right:0,top:0,right:0,bottom:0};
}
function dhGate(){
 return new Promise(function(res){
  const box=dhGateBox(),g=document.createElement('div');g.id='dhGate';g.setAttribute('role','dialog');g.setAttribute('aria-label','Get your quests');
  g.style.cssText='left:'+box.left+'px;top:'+box.top+'px;right:'+box.right+'px;bottom:'+box.bottom+'px';
  const candle=function(side){return '<span class="gate-candle '+side+'">'+qpxSprite('candle',7,{F:'#ff7a2a',Y:'#ffd35c',C:'#efe6d2',B:'#6b4a2c'},'qfl')+'</span>'};
  g.innerHTML='<span class="gate-glow"></span>'+candle('l')+candle('r')
   +'<div class="gate-inner"><span class="gate-eye">'+qpxSprite('eye',12,{X:'#cdb8ff',I:'#8a5cff',o:'#05030a'})+'</span>'
   +'<div class="gate-eyebrow">'+qpxText('A NEW DAY. A NEW DRAW.',{s:1,w:300,lines:1})+'</div>'
   +'<h2 class="gate-title">'+qpxText('YOUR CARDS ARE WAITING',{s:3,w:Math.min(330,innerWidth-48),lines:3})+'<span class="sr">Your cards are waiting</span></h2>'
   +'<p class="gate-sub">Three quests, drawn from the dark. Take what the table gives you.</p>'
   +'<button class="gate-btn" type="button" aria-label="Get your quests"><span class="gate-flame l"></span><span class="gate-btn-t">'+qpxText('GET YOUR QUESTS',{s:2,w:240,lines:1})+'</span><span class="gate-flame r"></span></button>'
   +'<button class="gate-skip" type="button">'+qpxText('NOT NOW',{s:1,w:80,lines:1})+'</button></div>';
  document.body.appendChild(g);
  g.animate([{opacity:0},{opacity:1}],{duration:650,easing:'ease-out',fill:'forwards'});
  const hap=function(n){try{if(window.LifeMaxHaptics)LifeMaxHaptics.play(n)}catch(e){}};
  /* embers rising from the candles */
  const ember=setInterval(function(){try{[.14,.86].forEach(function(f){QFX.emit(innerWidth*f+(Math.random()-.5)*14,innerHeight-box.bottom-70,(Math.random()-.5)*26,-(40+Math.random()*50),1.4+Math.random(),4+Math.random()*7,[1,.55+Math.random()*.2,.2])})}catch(e){}},260);
  let done=false;
  const end=function(v){if(done)return;done=true;clearInterval(ember);clearInterval(watch);res(v)};
  const close=function(ms){g.style.pointerEvents='none';const a=g.animate([{opacity:1},{opacity:0}],{duration:ms,fill:'forwards'});a.finished.then(function(){g.remove()},function(){g.remove()})};
  g.querySelector('.gate-btn').addEventListener('click',function(){
   if(done)return;const b=this,r=b.getBoundingClientRect();b.classList.add('lit');hap('success');
   try{QFX.burst({left:r.left,top:r.top,width:r.width,height:r.height,right:r.right,bottom:r.bottom},3)}catch(e){}
   g.querySelector('.gate-eye').classList.add('open');
   setTimeout(function(){hap('big');end('go');setTimeout(function(){close(500)},1100)},520);
  });
  g.querySelector('.gate-skip').addEventListener('click',function(){hap('tap');end('skip');close(350)});
  /* leaving the Quest Board (the bar is still reachable) takes the splash away; it comes back next visit */
  const watch=setInterval(function(){if(!dhViewOn()){end('leave');close(200)}},250);
 });
}
