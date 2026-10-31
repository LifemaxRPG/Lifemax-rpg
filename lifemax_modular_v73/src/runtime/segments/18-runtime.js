ves(){
  const mv=[],p=petData();
  if(!(state.activities||[]).length){mv.push({pri:0,col:'var(--accent)',icon:'bolt',t:'Log your first activity',s:'Every level starts with one. Pick any attribute.',cta:'Log',fn:"navigate('log')"});}
  ATTRS.map(function(a){return {a:a,d:chDecay(a)}}).filter(function(x){return x.d.k==='decay'||x.d.k==='soon'}).sort(function(x,y){return x.d.rank-y.d.rank}).slice(0,2).forEach(function(x){
    mv.push({pri:x.d.k==='decay'?1:3.5,col:ATTR_COL[x.a.id],icon:x.a.icon,t:x.a.short+(x.d.k==='decay'?' is fading':' is due a session'),s:x.d.txt+' · trained '+x.d.sub,cta:'Train',fn:"openAttr('"+x.a.id+"')",warn:x.d.k==='decay'?'bad':'warn'})});
  const dq=(state.quests||[]).filter(function(q){return q.cadence==='daily'&&questToday(q)&&!questLocked(q)}),left=dq.filter(function(q){return !isQuestDone(q)});
  if(dq.length){if(left.length){const xp=left.reduce(function(s,q){return s+(q.xp||0)},0);mv.push({pri:2,col:'var(--gold)',icon:'flag',t:left.length+' daily quest'+(left.length>1?'s':'')+' left',s:left.slice(0,2).map(function(q){return q.name}).join(' · ')+(left.length>2?' +'+(left.length-2):''),cta:'+'+xp+' XP',fn:"navigate('quests')",pill:1})}else mv.push({pri:9,col:'var(--good)',icon:'trophy',t:'Daily quests complete',s:'Nicely done. Weekly quests are next.',done:1,fn:"navigate('quests')"})}
  if(state.plan){const d=planDay(getToday());if(d){if(d.kind==='rest')mv.push({pri:7,col:'var(--accent)',icon:'bed',t:'Rest day',s:'Prescribed recovery. Nothing to log.',done:1,fn:"navigate('plan')"});else{const dn=dayDone(getToday());mv.push({pri:dn?9:3,col:'var(--accent)',icon:d.icon||'dumbbell',t:dn?'Workout done':'Today: '+(d.short&&d.short!==d.label?d.short+' · ':'')+(d.label||d.cell||'Session'),s:dn?'Session complete. Well earned.':'+'+(d.xp||0)+' XP when every box is ticked',done:dn?1:0,fn:"navigate('plan')",cta:dn?'':'Open'})}}}
  if(!luckToday())mv.push({pri:6,col:'#ffd35c',icon:'dice',t:"Roll today's luck",s:'Four dice, one fate number',cta:'Roll',fn:'openLuck()'});
  if(p&&p.alive&&petStage(p)>=2&&!p.vacation.on)mv.push({pri:petStage(p)>=3?0:2.5,col:'#ff7a9a',icon:'paw',t:p.name+(petStage(p)>=3?' really needs a meal':' is hungry'),s:'A meal about twice a day is all it needs.',cta:'Feed',fn:"navigate('pet')",warn:petStage(p)>=3?'bad':'warn'});
  if(!p&&!(state.petInviteHidden))mv.push({pri:8,col:'#ff9bb0',icon:'paw',t:'A stray kitten keeps stopping by',s:'Open the door and see who it is.',cta:'Meet',fn:"navigate('pet')"});
  mv.sort(function(a,b){return a.pri-b.pri});return mv.slice(0,4);
}
function ovNextHtml(){
  const mv=ovMoves();
  return '<div class="ov-h"><h3>Next moves</h3><span class="muted">most useful first</span></div><div class="ov-moves">'+mv.map(function(x){
    return '<button type="button" class="ov-move'+(x.done?' done':'')+(x.warn?' '+x.warn:'')+'" style="--c:'+x.col+'" onclick="'+x.fn+'"><span class="ov-mic">'+px(x.icon,20)+'</span><span class="ov-mt"><b>'+esc(x.t)+'</b><small>'+esc(x.s)+'</small></span>'+(x.cta?'<span class="ov-cta'+(x.pill?' pill':'')+'">'+esc(x.cta)+'</span>':'<span class="ov-chev">'+(x.done?'✓':'›')+'</span>')+'</button>'}).join('')+'</div>';
}
/* ---------- attribute tiles ---------- */
function ovAttrsHtml(){
  const D=chCompute(),foc=chFocus();
  const dot=function(a,td){const d=chDecay(a);if(td>0)return ['today','Trained today'];return [d.k,d.txt]};
  return '<div class="ov-h"><h3>Attributes</h3><button type="button" class="ov-link" onclick="navigate(\'character\')">Details ›</button></div><div class="ov-gems">'+ATTRS.map(function(a){
    const l=attrLevel(a.id),col=ATTR_COL[a.id],wk=D.wk[a.id],td=D.per[a.id][6],st=dot(a,td);
    return '<button type="button" class="ov-gem" style="--c:'+col+'" onclick="openAttr(\''+a.id+'\')" title="'+esc(st[1])+'"><div class="ov-g1"><span class="ov-gic">'+px(a.icon,18)+'</span><span class="ov-gnm">'+a.short+(foc.indexOf(a.id)>=0?' <i class="ch-star">★</i>':'')+'</span><i class="ov-dot s-'+st[0]+'"></i></div><div class="ov-g2"><b>LV '+l.level+'</b><em>'+(wk>0?'+'+wk.toLocaleString():'quiet')+'</em></div><div class="ch-bar"><i style="width:'+l.pct.toFixed(1)+'%"></i></div></button>'}).join('')
   +'</div><div class="ov-legend"><span><i class="ov-dot s-today"></i>today</span><span><i class="ov-dot s-ok"></i>safe</span><span><i class="ov-dot s-soon"></i>due soon</span><span><i class="ov-dot s-decay"></i>fading</span></div>';
}
/* ---------- week chart ---------- */
function ovWeekHtml(){
  const D=chCompute(),m=ovMap(),pace=ovPace(m),days=D.days.map(function(k,i){return {k:k,i:i,xp:ATTRS.reduce(function(s,a){return s+D.per[a.id][i]},0)}});
  const max=Math.max(pace*1.2,40,Math.max.apply(null,days.map(function(d){return d.xp}))),wk=days.reduce(function(s,d){return s+d.xp},0),prev=ovSum(m,7,13),delta=prev>0?Math.round((wk-prev)/prev*100):null;
  const best=days.reduce(function(b,d){return d.xp>b.xp?d:b},days[0]);
  const bars=days.map(function(d){
    const segs=ATTRS.map(function(a){const v=D.per[a.id][d.i];return v>0?'<i style="height:'+(v/max*100).toFixed(1)+'%;background:linear-gradient(180deg,'+ATTR_COL[a.id]+','+ATTR_COL[a.id]+'bb)"></i>':''}).join('');
    const dt=new Date(d.k+'T12:00:00');
    return '<button type="button" class="ov-bar'+(d.i===OV_SEL?' sel':'')+(d.i===6?' today':'')+'" onclick="ovPick('+d.i+')" aria-label="'+d.k+' '+d.xp+' XP"><span class="ov-bs">'+(segs||'<i class="empty"></i>')+'</span><small>'+(d.i===6?'Today':'SMTWTFS'.charAt(dt.getDay()))+'</small></button>'});
  const top=ATTRS.map(function(a){return {a:a,v:D.wk[a.id]}}).filter(function(x){return x.v>0}).sort(function(x,y){return y.v-x.v}).slice(0,3);
  return '<div class="ov-h"><h3>This week</h3><span class="ov-sum"><b>'+wk.toLocaleString()+' XP</b>'+(delta===null?'':'<em class="'+(delta>=0?'up':'dn')+'">'+(delta>=0?'▲ ':'▼ ')+Math.abs(delta)+'% vs last week</em>')+'</span></div>'
   +'<div class="ov-chart"><div class="ov-pace" style="bottom:calc('+(pace/max*100).toFixed(1)+'% + 22px)"><span>pace '+pace+'</span></div><div class="ov-bars">'+bars.join('')+'</div></div>'
   +'<div class="ov-tip" id="ovTip">'+ovTipText(D,days,OV_SEL)+'</div>'
   +(top.length?'<div class="ov-top">'+top.map(function(x){return '<span style="--c:'+ATTR_COL[x.a.id]+'"><i></i>'+x.a.short+' '+x.v.toLocaleString()+'</span>'}).join('')+'</div>':'<div class="ov-top muted" style="font-size:12px">Log an activity and your week lights up here.</div>');
}
function ovTipText(D,days,i){const d=days[i];if(!d)return '';const parts=ATTRS.map(function(a){return {a:a,v:D.per[a.id][i]}}).filter(function(x){return x.v>0}).sort(function(x,y){return y.v-x.v});const dt=new Date(d.k+'T12:00:00'),lab=i===6?'Today':dt.toLocaleDateString(undefined,{weekday:'long'});return '<b>'+lab+'</b> · '+d.xp+' XP'+(parts.length?' — '+parts.map(function(x){return x.a.short+' '+x.v}).join(', '):' — a rest day')}
function ovPick(i){OV_SEL=i;const D=chCompute(),m=ovMap();const days=D.days.map(function(k,j){return {k:k,xp:ATTRS.reduce(function(s,a){return s+D.per[a.id][j]},0)}});const t=document.getElementById('ovTip');if(t)t.innerHTML=ovTipText(D,days,i);document.querySelectorAll('#ovWeek .ov-bar').forEach(function(b,j){b.classList.toggle('sel',j===i)});haptic(6)}
/* ---------- luck, recent ---------- */
function ovLuckHtml(){const L=luckToday(),t=L?luckTier(L.score):null;return '<div class="eyebrow">DAILY LUCK</div><div class="ov-luck-n'+(L?'':' dim')+'"'+(L?' style="color:'+t.c+'"':'')+'>'+(L?luckFmt(L.score):'— — — —')+'</div><div class="ov-luck-t">'+(L?t.name:'Not rolled yet')+'</div><button type="button" class="btn small '+(L?'secondary':'')+' ov-luck-btn" onclick="openLuck()">'+(L?'View roll':'Roll the dice')+'</button>'}
function ovRecentHtml(){
  const list=(state.activities||[]).slice().sort(function(a,b){return new Date(b.date)-new Date(a.date)}).slice(0,4);
  return '<div class="ov-h"><h3>Recent activity</h3><button type="button" class="ov-link" onclick="navigate(\'history\')">History ›</button></div>'+(list.length?'<div class="ov-feed">'+list.map(function(a){const at=ovAttrOf(a.primary),col=ATTR_COL[at.id];return '<div class="ov-fi" style="--c:'+col+'"><span class="ov-fic">'+px(at.icon,18)+'</span><div style="min-width:0;flex:1"><b>'+esc(a.name||'Activity')+'</b><small>'+at.short+' · '+ovAgo(a)+(a.duration?' · '+a.duration+' min':'')+'</small></div><span class="ov-fxp">+'+(a.xp||0)+'</span></div>'}).join('')+'</div>':'<div class="empty" style="padding:14px 0"><div>⌁</div>Nothing logged yet. Your first activity shows up here.</div>');
}
/* ---------- render + motion ---------- */
function renderOverview(){
  const v=document.getElementById('ovHero');if(!v)return;
  const set=function(id,h){const e=document.getElementById(id);if(e&&e.__h!==h){e.innerHTML=h;e.__h=h}};
  set('ovHero',ovHeroHtml());set('ovNext',ovNextHtml());set('ovAttrs',ovAttrsHtml());set('ovWeek',ovWeekHtml());set('ovLuck',ovLuckHtml());set('ovRecent',ovRecentHtml());
  const cr=document.getElementById('ovCrest');if(cr){const cls=state.classId?classById(state.classId):null,k=cls?cls.id:'';if(cr.dataset.k!==k){let h='';try{if(cls&&typeof onbSvgCrest==='function'&&ONB_CLASS_VISUALS[cls.id])h=onbSvgCrest(cls.id,ONB_CLASS_VISUALS[cls.id].color)}catch(e){}cr.innerHTML=h;cr.dataset.k=k}}
  const d=document.getElementById('ovDate');if(d)d.textContent=new Date().toLocaleDateString(undefined,{weekday:'long',month:'long',day:'numeric'}).toUpperCase();
  const g=document.getElementById('ovHello');if(g&&g.textContent!==ovGreeting())g.textContent=ovGreeting();
  const duo=document.getElementById('ovDuo');if(duo)duo.classList.toggle('solo',!petData());
  ovPlanGlance();
  if(typeof ovLayoutApply==='function')ovLayoutApply();
}
/* Today's plan: Life Director's chrono dial, drawn by Life Director itself (hidden if the feature is missing) */
function ovPlanGlance(intro){const el=document.getElementById('ovPlan');if(!el)return;let ok=false;try{ok=!!(window.LifeDirector&&typeof LifeDirector.glance==='function'&&LifeDirector.glance(el,{intro:!!intro}))}catch(e){console.warn('overview plan',e)}el.hidden=!ok}
function ovReplay(){
  const view=document.getElementById('view-dashboard');if(!view)return;
  view.classList.remove('ov-in');void view.offsetWidth;view.classList.add('ov-in');
  ovPlanGlance(true);
  const n=document.getElementById('ovToday');if(n&&!fxReduced()){const to=+n.dataset.v||0;if(to>0){const t0=performance.now();(function f(now){const u=Math.max(0,Math.min(1,(now-t0)/900)),e=1-Math.pow(1-u,3);n.textContent=Math.round(to*e);if(u<1)requestAnimationFrame(f)})(t0)}}
  document.querySelectorAll('#ovHero .xpfill,#ovAttrs .ch-bar i').forEach(function(i){const w=i.dataset.w||i.style.width;i.style.transition='none';i.style.width='0%';void i.offsetWidth;i.style.transition='';requestAnimationFrame(function(){requestAnimationFrame(function(){i.style.width=w})})});
}

window.__ovReady=true;try{renderOverview()}catch(e){console.warn('overview',e)}

/* ===== Daily Luck: formula, dice physics, card ===== */
function luckTier(s){for(var i=0;i<LUCK_TIERS.length;i++)if(s<=LUCK_TIERS[i].max)return LUCK_TIERS[i];return LUCK_TIERS[LUCK_TIERS.length-1]}
function luckMsg(s){return LUCK_EXACT[s]||luckTier(s).msg}
function luckFmt(n){return Number(n).toLocaleString('en-US').replace('-','−')}
function luckSup(n){return String(n).split('').map(function(c){return '⁰¹²³⁴⁵⁶⁷⁸⁹'.charAt(+c)}).join('')}
function luckFib(n){var a=1,b=1;for(var i=3;i<=n;i++){var t=a+b;a=b;b=t}return b}
function luckRollDice(){var out=[],buf=new Uint32Array(8),i=8,c=(typeof crypto!=='undefined'&&crypto.getRandomValues)?crypto:null;
 while(out.length<4){if(i>=buf.length){if(c)c.getRandomValues(buf);else for(var j=0;j<buf.length;j++)buf[j]=Math.floor(Math.random()*4294967296);i=0}var v=buf[i++];if(v<4294967292)out.push(v%6+1)}return out}
/* The Luck Formula. d = four dice. Returns every intermediate number so the animation and the maths cannot drift apart. */
function luckCompute(d){
 var F=d[0]*1000+d[1]*100+d[2]*10+d[3],S=d[0]+d[1]+d[2]+d[3],P=d[0]*d[1]*d[2]*d[3],Q=d.reduce(function(a,x){return a+x*x},0),V=new Set(d).size;
 var root=Math.floor(Math.sqrt(777*F)),sq=88*Q,gold=Math.floor(LUCK_PHI*P),sev=Math.pow(7,V),integ=54*V*V,fib=luckFib(S),tax=-13*S;
 var defs=[
  {k:'F',label:'F = Σ dᵢ · 10^(4−i)',steps:[luckFmt(d[0]*1000)+'+'+d[1]*100+'+'+d[2]*10+'+'+d[3]],val:luckFmt(F)},
  {k:'P',label:'P = Π dᵢ',steps:[d.join(' × ')],val:luckFmt(P)},
  {k:'V',label:'V = |{d₁,d₂,d₃,d₄}|',steps:['|{'+d.join(', ')+'}|'],val:String(V)},
  {k:'phi',label:'φ = (1 + √5) / 2',steps:['(1 + 2.2360680) / 2'],val:'1.6180',sig:'phi'}];
 var terms=[
  {k:'root',label:'⌊√(<b class="sg" data-s="777">777</b>·F)⌋',steps:['⌊√(777 × '+luckFmt(F)+')⌋','⌊√'+luckFmt(777*F)+'⌋','⌊'+Math.sqrt(777*F).toFixed(2)+'⌋'],val:root,sig:777},
  {k:'sq',label:'<b class="sg" data-s="88">88</b> · Σ dᵢ²',steps:['88 × ('+d.map(function(x){return x+'²'}).join('+')+')','88 × '+Q],val:sq,sig:88},
  {k:'gold',label:'⌊<b class="sg" data-s="phi">φ</b> · P⌋',steps:['⌊1.618 × '+P+'⌋','⌊'+(LUCK_PHI*P).toFixed(2)+'⌋'],val:gold,sig:'phi'},
  {k:'sev',label:'<b class="sg" data-s="7">7</b>^V',steps:['7'+luckSup(V)],val:sev,sig:7},
  {k:'int',label:'∫₀^V <b class="sg" data-s="108">108</b>x dx',steps:['∫₀'+luckSup(V)+' 108x dx','[54x²]₀'+luckSup(V),'54 × '+V+'²'],val:integ,sig:108},
  {k:'fib',label:'<b class="sg" data-s="fib">Fib</b>(S)',steps:['Fib('+S+')'],val:fib,sig:'fib',fibN:S},
  {k:'pro',label:'<b class="sg" data-s="888">888</b>',steps:['888'],val:888,sig:888},
  {k:'tax',label:'− <b class="sg" data-s="13">13</b> · Σ dᵢ',steps:['−13 × '+S],val:tax,sig:13}];
 var inner=terms.reduce(function(a,t){return a+t.val},0),x3=3*inner,rem=x3%8888;
 return {d:d.slice(),F:F,S:S,P:P,Q:Q,V:V,defs:defs,terms:terms,inner:inner,x3:x3,quot:Math.floor(x3/8888),rem:rem,score:1000+rem}}
function luckPct(s){return Math.max(0,Math.min(100,(s-1000)/8999*100))}
function luckToday(){var t=getToday();return state.luck&&state.luck.date===t&&state.luck.ver===LUCK_VER&&state.luck.dice&&state.luck.dice.length===4?state.luck:null}
function luckResetToday(){var t=getToday();if(state.luck&&state.luck.date===t)state.luck=null;if(state.luckLog)delete state.luckLog[t];save();toast("Today's luck roll cleared")}
/* ---- 3D helpers for the dice: 3x3 rotation matrices (CSS axis convention), row major ---- */
function lkMul(A,B){var C=[];for(var i=0;i<3;i++)for(var j=0;j<3;j++)C[i*3+j]=A[i*3]*B[j]+A[i*3+1]*B[3+j]+A[i*3+2]*B[6+j];return C}
function lkRx(t){var c=Math.cos(t),s=Math.sin(t);return [1,0,0,0,c,-s,0,s,c]}
function lkRy(t){var c=Math.cos(t),s=Math.sin(t);return [c,0,s,0,1,0,-s,0,c]}
function lkRz(t){var c=Math.cos(t),s=Math.sin(t);return [c,-s,0,s,c,0,0,0,1]}
function lkAxis(a,t){var l=Math.hypot(a[0],a[1],a[2])||1,x=a[0]/l,y=a[1]/l,z=a[2]/l,c=Math.cos(t),s=Math.sin(t),u=1-c;return [c+x*x*u,x*y*u-z*s,x*z*u+y*s,y*x*u+z*s,c+y*y*u,y*z*u-x*s,z*x*u-y*s,z*y*u+x*s,c+z*z*u]}
function lkApply(R,v){return [R[0]*v[0]+R[1]*v[1]+R[2]*v[2],R[3]*v[0]+R[4]*v[1]+R[5]*v[2],R[6]*v[0]+R[7]*v[1]+R[8]*v[2]]}
function lkM3d(R,x,y,z){return 'matrix3d('+[R[0],R[3],R[6],0,R[1],R[4],R[7],0,R[2],R[5],R[8],0,x,y,z,1].map(function(n){return +n.toFixed(4)}).join(',')+')'}
var LK_FN={1:[0,0,1],6:[0,0,-1],2:[1,0,0],5:[-1,0,0],3:[0,-1,0],4:[0,1,0]};
function lkTarget(v){return v===1?lkRx(0):v===6?lkRy(Math.PI):v===2?lkRy(-Math.PI/2):v===5?lkRy(Math.PI/2):v===3?lkRx(-Math.PI/2):lkRx(Math.PI/2)}
/* The flight of one die: slides in from the far edge, falls, bounces four times, tumbles, and settles on the rolled face. u runs 0 to 1. */
function lkTrack(i,v,rnd){
 var xf=(i-1.5)*74+(rnd()-.5)*8,yf=(rnd()-.5)*10,x0=(rnd()-.5)*260,y0=-360-rnd()*40,z0=240+rnd()*60,yaw=(rnd()-.5)*.5,Rt=lkMul(lkRz(yaw),lkTarget(v)),
 a1=[rnd()-.5,rnd()-.5,rnd()-.5],a2=[rnd()-.5,rnd()-.5,.3+rnd()],th1=(2.2+rnd()*2)*6.2832,th2=(1+rnd()*1.5)*6.2832,B=[0,.30,.48,.61,.71,.78],H=[0,70+rnd()*20,28+rnd()*8,10,3];
 function pose(u){u=Math.max(0,Math.min(1,u));var e1=1-Math.pow(1-u,2.2),e2=1-Math.pow(1-u,3),z=0;
  if(u<B[1]){var s=u/B[1];z=z0*(1-s*s)}else{for(var k=1;k<5;k++){if(u<B[k+1]){var q=(u-B[k])/(B[k+1]-B[k]);z=H[k]*4*q*(1-q);break}}}
  var w=Math.pow(1-u,2.2);return {x:x0+(xf-x0)*e1,y:y0+(yf-y0)*e2,z:z,R:lkMul(lkMul(lkAxis(a1,th1*w),lkAxis(a2,th2*w)),Rt)}}
 return {pose:pose,impacts:B.slice(1),final:{x:xf,y:yf,R:Rt}}}
function renderLuckCard(){var el=document.getElementById('luckCard');if(!el)return;
 var L=luckToday(),tier=L?luckTier(L.score):null,log=state.luckLog||{},chips='';
 for(var i=6;i>=0;i--){var dt=new Date();dt.setDate(dt.getDate()-i);var k=dt.toLocaleDateString('en-CA'),s=log[k],tr=s?luckTier(s):null;
  chips+='<div class="luck-chip"><span>'+'SMTWTFS'.charAt(dt.getDay())+'</span><b style="color:'+(tr?tr.c:'var(--muted2)')+'">'+(s?luckFmt(s):'·')+'</b></div>'}
 el.innerHTML='<div class="card luck-card"><div style="display:flex;justify-content:space-between;align-items:flex-start;gap:10px"><div><div class="eyebrow">DAILY LUCK</div>'
 +(L?'<div class="luck-score" style="color:'+tier.c+'">'+luckFmt(L.score)+'</div><div class="luck-tier" style="color:'+tier.c+'">'+tier.name.toUpperCase()+'</div>'+(luckMsg(L.score)?'<p class="muted" style="font-size:12px;margin:4px 0 0">'+luckMsg(L.score)+'</p>':'')
  :'<div class="luck-score" style="color:var(--muted2)">— — — —</div><p class="muted" style="font-size:12px;margin:4px 0 0">Roll four dice. Fate runs them through the Luck Formula.</p>')
 +'</div><button class="btn" onclick="openLuck()" style="white-space:nowrap">'+px('dice',16)+' '+(L?'Replay':"Roll today's luck")+'</button></div>'
 +(L?'<div class="luck-mini"><i style="left:'+luckPct(L.score).toFixed(1)+'%;background:'+tier.c+'"></i></div>':'')
 +'<div class="luck-chips">'+chips+'</div></div>'}

/* ===== Daily Luck: dice roll, chalk calculation and result (plain black void) ===== */
var LK={run:0,ov:null,rec:null,ac:null,k:1,fast:true,phase:'roll',dust:[],raf:0,dice:[],lastShake:0};
var LK_PIPS={1:[[2,2]],2:[[1,1],[3,3]],3:[[1,1],[2,2],[3,3]],4:[[1,1],[1,3],[3,1],[3,3]],5:[[1,1],[1,3],[2,2],[3,1],[3,3]],6:[[1,1],[2,1],[3,1],[1,3],[2,3],[3,3]]};
var LK_TICKS=[16.7,38.3,63,75.4,87.7],LK_LIGHT=[.28,-.42,.86];
/* ---------- dice sound: the only sound in this sequence ---------- */
function luckAc(){if(!fxOpt().sound)return null;try{var C=window.AudioContext||window.webkitAudioContext;if(!C)return null;if(!LK.ac)LK.ac=new C();if(LK.ac.state==='suspended')LK.ac.resume();return LK.ac}catch(e){return null}}
function luckTone(f,d,type,v){var a=luckAc();if(!a)return;try{var o=a.createOscillator(),g=a.createGain(),t=a.currentTime;o.type=type||'sine';o.frequency.value=f;g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(v||.04,t+.01);g.gain.exponentialRampToValueAtTime(.0001,t+d);o.connect(g);g.connect(a.destination);o.start(t);o.stop(t+d+.02)}catch(e){}}
function luckNoise(dur,vol,f1,q){var a=luckAc();if(!a)return;try{var n=Math.max(1,Math.floor(a.sampleRate*dur)),b=a.createBuffer(1,n,a.sampleRate),c=b.getChannelData(0);for(var i=0;i<n;i++)c[i]=(Math.random()*2-1)*(1-i/n);var s=a.createBufferSource(),bp=a.createBiquadFilter(),g=a.createGain();s.buffer=b;bp.type='bandpass';bp.frequency.value=f1;bp.Q.value=q||.8;g.gain.value=vol;s.connect(bp);bp.connect(g);g.connect(a.destination);s.start()}catch(e){}}
function luckImpact(vol){if(typeof QSFX!=='undefined'&&QSFX.on()&&QSFX.has('die-throw')){QSFX.play(vol>.45?'die-throw':'dice-throw',{vol:Math.min(1,.35+vol*1.1),rate:vol>.45?1:1.15})}else{luckNoise(.07,vol*.55,1300+Math.random()*1800,.7);luckTone(68+Math.random()*30,.13,'sine',vol*.4)}haptic(Math.round(8+vol*18))}
/* Speed is set per phase: the dice roll and the score reveal stay at normal speed, only the formula part runs fast. */
function luckSetK(){var k=LK.phase==='formula'?(LK.fast?.5:1):1;if(fxReduced())k=Math.min(k,.4);LK.k=k;if(LK.ov)LK.ov.style.setProperty('--k',k)}
/* Fade the dice lines in or out one by one, driven frame by frame so nothing can cut it short. */
function luckLineAnim(lines,out,tok){var ps=lines.querySelectorAll('path'),t0=performance.now(),sc=fxReduced()?.3:1,dur=(out?2600:800)*sc,gap=(out?320:90)*sc;
 function f(now){if(tok!==LK.run)return;var done=true;ps.forEach(function(p,i){var x=Math.max(0,Math.min(1,(now-t0-i*gap)/dur));if(x<1)done=false;var e=x<.5?2*x*x:1-Math.pow(-2*x+2,2)/2;p.style.opacity=(out?1-e:e).toFixed(3)});if(!done)requestAnimationFrame(f)}
 requestAnimationFrame(f)}
function luckWAbs(ms,tok){return new Promise(function(r){setTimeout(r,fxReduced()?Math.min(ms,300):ms)}).then(function(){if(tok!==LK.run)throw 'lk-cancel'})}
function luckW(ms,tok){return new Promise(function(r){setTimeout(r,Math.max(0,ms*LK.k))}).then(function(){if(tok!==LK.run)throw 'lk-cancel'})}
/* ---------- build ---------- */
function luckSpiral(){var pts=[],mn=[1e9,1e9],mx=[-1e9,-1e9];for(var t=0;t<=12.57;t+=.08){var r=1.1*Math.pow(LUCK_PHI,2*t/Math.PI),x=r*Math.cos(t),y=r*Math.sin(t);pts.push([x,y]);mn=[Math.min(mn[0],x),Math.min(mn[1],y)];mx=[Math.max(mx[0],x),Math.max(mx[1],y)]}
 var s=96/Math.max(mx[0]-mn[0],mx[1]-mn[1]),d='',len=0;pts=pts.map(function(p){return [(p[0]-mn[0])*s+8,(p[1]-mn[1])*s+8]});pts.forEach(function(p,i){d+=(i?'L':'M')+p[0].toFixed(1)+','+p[1].toFixed(1);if(i)len+=Math.hypot(p[0]-pts[i-1][0],p[1]-pts[i-1][1])});return {d:d,len:Math.ceil(len)}}
function luckNoiseMask(){try{var c=document.createElement('canvas');c.width=c.height=128;var x=c.getContext('2d'),im=x.createImageData(128,128);for(var i=0;i<im.data.length;i+=4){var a=Math.random();im.data[i]=im.data[i+1]=im.data[i+2]=255;im.data[i+3]=a<.14?100:a<.36?200:255}x.putImageData(im,0,0);return 'url('+c.toDataURL()+')'}catch(e){return 'none'}}
function luckBuild(){if(LK.ov)return;var ov=document.createElement('div');ov.id='luckOv';ov.className='lk-ov';
 var tpl=luckCompute([1,2,3,4]),row=function(k,label,cls){return '<div class="cb-row'+(cls?' '+cls:'')+'" data-k="'+k+'"><span class="cb-e">'+label+'</span><span class="cb-v"></span></div>'};
 var defs=tpl.defs.map(function(x){return row(x.k,x.label)}).join(''),
  main=row('head','Λ = <b class="sg" data-s="1000">1000</b> + ( <b class="sg" data-s="3">3</b> · [')+tpl.terms.map(function(x){return row(x.k,(x.k==='tax'?'':'+ ')+x.label,'ind')}).join('')+row('foot','] )  mod <b class="sg" data-s="8888">8888</b>')+row('tot','Σ [ … ]','sub');
 var dieHtml=[0,1,2,3].map(function(n){return '<div class="lk-shadow3" id="lkS'+n+'"></div><div class="lk-die3" id="lkD'+n+'">'+[1,2,3,4,5,6].map(function(f){return '<div class="lk-face3 lk-g'+f+'">'+LK_PIPS[f].map(function(p){return '<i style="grid-area:'+p[0]+'/'+p[1]+'"></i>'}).join('')+'</div>'}).join('')+'</div>'}).join('');
 var sp=luckSpiral();
 ov.style.setProperty('--lk-noise',luckNoiseMask());
 var stars='';for(var q=0;q<80;q++){var r=Math.random(),cl=r<.06?'b':r<.3?'m':'',tint=Math.random();stars+='<i class="lk-star '+cl+(tint<.12?' w':tint<.24?' c':'')+'" style="left:'+(Math.random()*100).toFixed(1)+'%;top:'+(Math.random()*100).toFixed(1)+'%;--d:'+(2+Math.random()*4.5).toFixed(1)+'s;animation-delay:-'+(Math.random()*6).toFixed(1)+'s"></i>'}
 ov.innerHTML=stars+'<div class="lk-head"><span class="lk-title">DAILY LUCK</span><button class="lk-x" type="button" onclick="luckClose()">Close</button></div>'
 +'<div class="lk-main" id="lkMain"><div class="lk-scene" id="lkScene"><div class="lk-table"><div class="lk-surface"></div>'+dieHtml+'</div></div>'
 +'<div class="lk-board" id="lkBoard"><div class="cb-in"><div class="cb-fit" id="cbFit">'
 +'<div class="cb-fate"><div class="cb-dgs"><span class="cb-dg"></span><span class="cb-dg"></span><span class="cb-dg"></span><span class="cb-dg"></span></div><div class="cb-fnum" id="cbFnum"></div></div>'
 +'<div class="cb-rows">'+defs+'<div style="height:6px"></div>'+main+'</div><div class="cb-work" id="cbWork"></div>'
 +'<div class="cb-fib" id="cbFib"><div class="fb-seq" id="fbSeq"></div><div class="fb-side"><svg viewBox="0 0 112 112"><path class="fb-path" id="fbPath" d="'+sp.d+'" style="stroke-dasharray:'+sp.len+';stroke-dashoffset:'+sp.len+'" data-len="'+sp.len+'"></path></svg><div class="fb-ratio" id="fbRatio"></div></div></div>'
 +'<div class="cb-ans" id="cbAns"><span class="cb-ansT" id="cbAnsT"></span></div></div></div>'
 +'<canvas class="cb-dust" id="cbDust"></canvas></div>'
 +'<svg class="lk-lines" id="lkLines"></svg>'
 +'<div class="lk-result" id="lkResult"><div class="lk-final" id="lkFinal">0</div><div class="lk-tier" id="lkTier"></div><div class="lk-msg" id="lkMsg"></div>'
 +'<div class="lk-scale"><div class="lk-ticks">'+LK_TICKS.map(function(p){return '<span style="left:'+p+'%"></span>'}).join('')+'</div><div class="lk-mark" id="lkMark"><b id="lkMarkN"></b><i></i></div></div>'
  +'<div class="lk-btns"><button class="btn" type="button" onclick="luckClose()">Done</button><button class="btn secondary" type="button" onclick="luckReplay()">Replay</button></div></div></div>'
 +'<div class="lk-foot"><button class="lk-skip" id="lkSpeed" type="button" onclick="luckSpeed()">2×</button><button class="lk-skip" id="lkSkip" type="button" onclick="luckSkip()">SKIP</button></div>';
 document.body.appendChild(ov);LK.ov=ov;
 LK.dice=[0,1,2,3].map(function(n){var el=ov.querySelector('#lkD'+n);return {el:el,sh:ov.querySelector('#lkS'+n),faces:el.querySelectorAll('.lk-face3')}});
 document.addEventListener('keydown',function(e){if(e.key==='Escape'&&LK.ov&&LK.ov.classList.contains('on'))luckClose()});window.addEventListener('resize',function(){if(LK.ov&&LK.ov.classList.contains('on'))luckFit()})}
/* Scale the whole formula block down when the screen is short, so no row is ever cut off or overlapped. */
function luckFit(){var b=luckEl('#lkBoard'),f=luckEl('#cbFit');if(!b||!f)return;f.style.transform='none';f.style.width='100%';var avail=b.clientHeight-6,need=f.offsetHeight,s=need>0?Math.min(1,avail/need):1;if(s<1){f.style.width=(100/s).toFixed(2)+'%';f.style.transform='scale('+s.toFixed(3)+')'}return s}
function luckEl(s){return LK.ov.querySelector(s)}
/* ---------- chalk dust and writing ---------- */
function luckPuff(x,y,n,spread){if(fxReduced())return;for(var i=0;i<n&&LK.dust.length<140;i++)LK.dust.push({x:x,y:y,vx:(Math.random()-.5)*spread,vy:Math.random()*16+4,age:0,life:.5+Math.random()*.7,s:.8+Math.random()*1.8})}
function luckDustStart(){var cv=luckEl('#cbDust'),b=luckEl('#lkBoard'),dpr=Math.min(2,window.devicePixelRatio||1);cv.width=Math.max(1,Math.round((b.clientWidth||390)*dpr));cv.height=Math.max(1,Math.round((b.clientHeight||520)*dpr));var cx=cv.getContext('2d');if(!cx)return;
 var last=performance.now();function f(now){if(!LK.ov||!LK.ov.classList.contains('on')){LK.raf=0;return}var dt=Math.min(.05,(now-last)/1000);last=now;cx.setTransform(dpr,0,0,dpr,0,0);cx.clearRect(0,0,cv.width,cv.height);
  for(var i=LK.dust.length-1;i>=0;i--){var p=LK.dust[i];p.age+=dt;if(p.age>=p.life){LK.dust.splice(i,1);continue}p.x+=p.vx*dt;p.y+=p.vy*dt;p.vy+=30*dt;cx.fillStyle='rgba(240,240,234,'+((1-p.age/p.life)*.65).toFixed(2)+')';cx.fillRect(p.x,p.y,p.s,p.s)}
  LK.raf=requestAnimationFrame(f)}cancelAnimationFrame(LK.raf);LK.raf=requestAnimationFrame(f)}
function luckWrite(el,ms,tok){return new Promise(function(res){var br=luckEl('#lkBoard').getBoundingClientRect(),r=el.getBoundingClientRect(),dur=Math.max(60,ms*LK.k);
 el.style.transition='none';el.style.clipPath='inset(0 100% 0 0)';void el.offsetWidth;el.style.transition='clip-path '+dur+'ms linear';el.style.clipPath='inset(0 0 0 0)';
 var t0=performance.now();
 function f(now){if(tok!==LK.run){res();return}var u=Math.max(0,Math.min(1,(now-t0)/dur)),x=r.left-br.left+r.width*u,y=r.top-br.top+r.height*(.62+Math.sin(u*40)*.13);if(Math.random()<.7)luckPuff(x,y+4,1,26);if(u<1)requestAnimationFrame(f);else res()}
 requestAnimationFrame(f)})}
function luckErase(el,tok){var br=luckEl('#lkBoard').getBoundingClientRect(),r=el.getBoundingClientRect();el.style.transition='clip-path '+Math.max(40,230*LK.k)+'ms ease-in';el.style.clipPath='inset(0 0 0 100%)';for(var i=0;i<6;i++)luckPuff(r.left-br.left+r.width*(i/6),r.top-br.top+r.height*.6,2,40);return luckW(240,tok)}
function luck