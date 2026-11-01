/* ---------- next moves: the four most useful things to do now. go is where a tap leads: {view}, {attr} or {luck} ---------- */
function ovMoves(){
  const mv=[],p=petData();
  if(!(state.activities||[]).length){mv.push({pri:0,color:'var(--accent)',icon:'bolt',title:'Log your first activity',sub:'Every level starts with one. Pick any attribute.',cta:'Log',go:{view:'log'}});}
  ATTRS.map(function(a){return {a:a,d:chDecay(a)}}).filter(function(x){return x.d.k==='decay'||x.d.k==='soon'}).sort(function(x,y){return x.d.rank-y.d.rank}).slice(0,2).forEach(function(x){
    mv.push({pri:x.d.k==='decay'?1:3.5,color:ATTR_COL[x.a.id],icon:x.a.icon,title:x.a.short+(x.d.k==='decay'?' is fading':' is due a session'),sub:x.d.txt+' · trained '+x.d.sub,cta:'Train',go:{attr:x.a.id},warn:x.d.k==='decay'?'bad':'warn'})});
  const dq=(state.quests||[]).filter(function(q){return q.cadence==='daily'&&questToday(q)&&!questLocked(q)}),left=dq.filter(function(q){return !isQuestDone(q)});
  if(dq.length){if(left.length){const xp=left.reduce(function(s,q){return s+(q.xp||0)},0);mv.push({pri:2,color:'var(--gold)',icon:'flag',title:left.length+' daily quest'+(left.length>1?'s':'')+' left',sub:left.slice(0,2).map(function(q){return q.name}).join(' · ')+(left.length>2?' +'+(left.length-2):''),cta:'+'+xp+' XP',go:{view:'quests'},pill:true})}else mv.push({pri:9,color:'var(--good)',icon:'trophy',title:'Daily quests complete',sub:'Nicely done. Weekly quests are next.',done:true,go:{view:'quests'}})}
  if(state.plan){const d=planDay(getToday());if(d){if(d.kind==='rest')mv.push({pri:7,color:'var(--accent)',icon:'bed',title:'Rest day',sub:'Prescribed recovery. Nothing to log.',done:true,go:{view:'plan'}});else{const dn=dayDone(getToday());mv.push({pri:dn?9:3,color:'var(--accent)',icon:d.icon||'dumbbell',title:dn?'Workout done':'Today: '+(d.short&&d.short!==d.label?d.short+' · ':'')+(d.label||d.cell||'Session'),sub:dn?'Session complete. Well earned.':'+'+(d.xp||0)+' XP when every box is ticked',done:dn,go:{view:'plan'},cta:dn?'':'Open'})}}}
  if(!luckToday())mv.push({pri:6,color:'#ffd35c',icon:'dice',title:"Roll today's luck",sub:'Four dice, one fate number',cta:'Roll',go:{luck:true}});
  if(p&&p.alive&&petStage(p)>=2&&!p.vacation.on)mv.push({pri:petStage(p)>=3?0:2.5,color:'#ff7a9a',icon:'paw',title:p.name+(petStage(p)>=3?' really needs a meal':' is hungry'),sub:'A meal about twice a day is all it needs.',cta:'Feed',go:{view:'pet'},warn:petStage(p)>=3?'bad':'warn'});
  if(!p&&!(state.petInviteHidden))mv.push({pri:8,color:'#ff9bb0',icon:'paw',title:'A stray kitten keeps stopping by',sub:'Open the door and see who it is.',cta:'Meet',go:{view:'pet'}});
  mv.sort(function(a,b){return a.pri-b.pri});return mv.slice(0,4);
}
function ovNextData(){return {title:'Next moves',note:'most useful first',moves:ovMoves()}}
/* the page's own handler for a move's go */
function ovGoJs(go){return go.attr?"openAttr('"+go.attr+"')":go.luck?'openLuck()':"navigate('"+go.view+"')"}
function ovNextHtml(d){
  return '<div class="ov-h"><h3>'+esc(d.title)+'</h3><span class="muted">'+esc(d.note)+'</span></div><div class="ov-moves">'+d.moves.map(function(x){
    return '<button type="button" class="ov-move'+(x.done?' done':'')+(x.warn?' '+x.warn:'')+'" style="--c:'+x.color+'" onclick="'+ovGoJs(x.go)+'"><span class="ov-mic">'+px(x.icon,20)+'</span><span class="ov-mt"><b>'+esc(x.title)+'</b><small>'+esc(x.sub)+'</small></span>'+(x.cta?'<span class="ov-cta'+(x.pill?' pill':'')+'">'+esc(x.cta)+'</span>':'<span class="ov-chev">'+(x.done?'✓':'›')+'</span>')+'</button>'}).join('')+'</div>';
}
/* ---------- attribute tiles: level, this week's XP and a status dot (trained today, or the decay state) ---------- */
function ovAttrsData(){
  const D=chCompute(),foc=chFocus();
  return {title:'Attributes',link:'Details ›',gems:ATTRS.map(function(a){
    const l=attrLevel(a.id),wk=D.wk[a.id],st=D.per[a.id][6]>0?{k:'today',txt:'Trained today'}:chDecay(a);
    return {id:a.id,short:a.short,icon:a.icon,color:ATTR_COL[a.id],focus:foc.indexOf(a.id)>=0,dot:{kind:st.k,text:st.txt},level:'LV '+l.level,week:wk>0?'+'+wk.toLocaleString():'quiet',pct:l.pct}}),
   legend:[{kind:'today',label:'today'},{kind:'ok',label:'safe'},{kind:'soon',label:'due soon'},{kind:'decay',label:'fading'}]};
}
function ovAttrsHtml(d){
  return '<div class="ov-h"><h3>'+esc(d.title)+'</h3><button type="button" class="ov-link" onclick="navigate(\'character\')">'+esc(d.link)+'</button></div><div class="ov-gems">'+d.gems.map(function(g){
    return '<button type="button" class="ov-gem" style="--c:'+g.color+'" onclick="openAttr(\''+g.id+'\')" title="'+esc(g.dot.text)+'"><div class="ov-g1"><span class="ov-gic">'+px(g.icon,18)+'</span><span class="ov-gnm">'+esc(g.short)+(g.focus?' <i class="ch-star">★</i>':'')+'</span><i class="ov-dot s-'+g.dot.kind+'"></i></div><div class="ov-g2"><b>'+esc(g.level)+'</b><em>'+esc(g.week)+'</em></div><div class="ch-bar"><i style="width:'+g.pct.toFixed(1)+'%"></i></div></button>'}).join('')
   +'</div><div class="ov-legend">'+d.legend.map(function(l){return '<span><i class="ov-dot s-'+l.kind+'"></i>'+esc(l.label)+'</span>'}).join('')+'</div>';
}
/* ---------- week chart: each day's XP stacked by attribute, against the pace line ---------- */
function ovWeekData(){
  const D=chCompute(),m=ovMap(),pace=ovPace(m),days=D.days.map(function(k,i){return {k:k,i:i,xp:ATTRS.reduce(function(s,a){return s+D.per[a.id][i]},0)}});
  const max=Math.max(pace*1.2,40,Math.max.apply(null,days.map(function(d){return d.xp}))),wk=days.reduce(function(s,d){return s+d.xp},0);
  const top=ATTRS.map(function(a){return {a:a,v:D.wk[a.id]}}).filter(function(x){return x.v>0}).sort(function(x,y){return y.v-x.v}).slice(0,3);
  return {title:'This week',total:wk.toLocaleString()+' XP',delta:ovDelta(wk,ovSum(m,7,13),' vs last week'),pace:{label:'pace '+pace,frac:pace/max},selected:OV_SEL,
    bars:days.map(function(d){return {date:d.k,label:d.i===6?'Today':'SMTWTFS'.charAt(new Date(d.k+'T12:00:00').getDay()),today:d.i===6,a11y:d.k+' '+d.xp+' XP',
      segs:ATTRS.filter(function(a){return D.per[a.id][d.i]>0}).map(function(a){return {color:ATTR_COL[a.id],frac:D.per[a.id][d.i]/max}}),tip:ovTipParts(D,days,d.i)}}),
    top:top.map(function(x){return {color:ATTR_COL[x.a.id],text:x.a.short+' '+x.v.toLocaleString()}}),empty:'Log an activity and your week lights up here.'};
}
/* the line under the chart for the day picked: its name, then its XP and where it went */
function ovTipParts(D,days,i){const d=days[i],parts=ATTRS.map(function(a){return {a:a,v:D.per[a.id][i]}}).filter(function(x){return x.v>0}).sort(function(x,y){return y.v-x.v});return {label:i===6?'Today':new Date(d.k+'T12:00:00').toLocaleDateString(undefined,{weekday:'long'}),rest:d.xp+' XP'+(parts.length?' — '+parts.map(function(x){return x.a.short+' '+x.v}).join(', '):' — a rest day')}}
function ovTipHtml(t){return t?'<b>'+esc(t.label)+'</b> · '+esc(t.rest):''}
function ovWeekHtml(d){
  const sel=d.bars[d.selected];
  return '<div class="ov-h"><h3>'+esc(d.title)+'</h3><span class="ov-sum"><b>'+esc(d.total)+'</b>'+(d.delta?'<em class="'+(d.delta.up?'up':'dn')+'">'+esc(d.delta.text)+'</em>':'')+'</span></div>'
   +'<div class="ov-chart"><div class="ov-pace" style="bottom:calc('+(d.pace.frac*100).toFixed(1)+'% + 22px)"><span>'+esc(d.pace.label)+'</span></div><div class="ov-bars">'+d.bars.map(function(b,i){
     return '<button type="button" class="ov-bar'+(i===d.selected?' sel':'')+(b.today?' today':'')+'" onclick="ovPick('+i+')" aria-label="'+esc(b.a11y)+'"><span class="ov-bs">'+(b.segs.length?b.segs.map(function(s){return '<i style="height:'+(s.frac*100).toFixed(1)+'%;background:linear-gradient(180deg,'+s.color+','+s.color+'bb)"></i>'}).join(''):'<i class="empty"></i>')+'</span><small>'+esc(b.label)+'</small></button>'}).join('')+'</div></div>'
   +'<div class="ov-tip" id="ovTip">'+ovTipHtml(sel&&sel.tip)+'</div>'
   +(d.top.length?'<div class="ov-top">'+d.top.map(function(x){return '<span style="--c:'+x.color+'"><i></i>'+esc(x.text)+'</span>'}).join('')+'</div>':'<div class="ov-top muted" style="font-size:12px">'+esc(d.empty)+'</div>');
}
function ovPick(i){OV_SEL=i;const b=ovWeekData().bars[i],t=document.getElementById('ovTip');if(t)t.innerHTML=ovTipHtml(b&&b.tip);document.querySelectorAll('#ovWeek .ov-bar').forEach(function(e,j){e.classList.toggle('sel',j===i)});haptic(6)}
/* ---------- luck, recent ---------- */
function ovLuckData(){const L=luckToday(),t=L?luckTier(L.score):null;return {eyebrow:'DAILY LUCK',rolled:!!L,score:L?luckFmt(L.score):'— — — —',color:L?t.c:'',text:L?t.name:'Not rolled yet',button:L?'View roll':'Roll the dice'}}
function ovLuckHtml(d){return '<div class="eyebrow">'+esc(d.eyebrow)+'</div><div class="ov-luck-n'+(d.rolled?'':' dim')+'"'+(d.rolled?' style="color:'+d.color+'"':'')+'>'+esc(d.score)+'</div><div class="ov-luck-t">'+esc(d.text)+'</div><button type="button" class="btn small '+(d.rolled?'secondary':'')+' ov-luck-btn" onclick="openLuck()">'+esc(d.button)+'</button>'}
function ovRecentData(){
  const list=(state.activities||[]).slice().sort(function(a,b){return new Date(b.date)-new Date(a.date)}).slice(0,4);
  return {title:'Recent activity',link:'History ›',items:list.map(function(a){const at=ovAttrOf(a.primary);return {name:a.name||'Activity',icon:at.icon,color:ATTR_COL[at.id],meta:at.short+' · '+ovAgo(a)+(a.duration?' · '+a.duration+' min':''),xp:'+'+(a.xp||0)}}),
    empty:{icon:'⌁',text:'Nothing logged yet. Your first activity shows up here.'}};
}
function ovRecentHtml(d){
  return '<div class="ov-h"><h3>'+esc(d.title)+'</h3><button type="button" class="ov-link" onclick="navigate(\'history\')">'+esc(d.link)+'</button></div>'+(d.items.length?'<div class="ov-feed">'+d.items.map(function(a){return '<div class="ov-fi" style="--c:'+a.color+'"><span class="ov-fic">'+px(a.icon,18)+'</span><div style="min-width:0;flex:1"><b>'+esc(a.name)+'</b><small>'+esc(a.meta)+'</small></div><span class="ov-fxp">'+esc(a.xp)+'</span></div>'}).join('')+'</div>':'<div class="empty" style="padding:14px 0"><div>'+esc(d.empty.icon)+'</div>'+esc(d.empty.text)+'</div>');
}
/* the label-and-value chips Rewards and Activity today share */
function ovChipsHtml(chips){return '<div class="ovh-chips">'+chips.map(function(c){return '<div class="ovh-chip"><span>'+esc(c.label)+'</span><b>'+esc(c.value)+'</b></div>'}).join('')+'</div>'}
/* ---------- the head: today's date, the greeting and the class crest ---------- */
function ovHeadData(){const cls=state.classId?classById(state.classId):null;return {date:new Date().toLocaleDateString(undefined,{weekday:'long',month:'long',day:'numeric'}).toUpperCase(),hello:ovGreeting(),classId:cls?cls.id:''}}
/* ---------- render + motion. Each card is a data function (what it says) and an HTML function that draws it ---------- */
function renderOverview(){
  const v=document.getElementById('ovHero');if(!v)return;
  const set=function(id,h){const e=document.getElementById(id);if(e&&e.__h!==h){e.innerHTML=h;e.__h=h}};
  set('ovHero',ovHeroHtml(ovHeroData()));set('ovNext',ovNextHtml(ovNextData()));set('ovAttrs',ovAttrsHtml(ovAttrsData()));set('ovWeek',ovWeekHtml(ovWeekData()));set('ovLuck',ovLuckHtml(ovLuckData()));set('ovRecent',ovRecentHtml(ovRecentData()));
  const h=ovHeadData(),cr=document.getElementById('ovCrest');
  if(cr&&cr.dataset.k!==h.classId){let s='';try{if(h.classId&&typeof onbSvgCrest==='function'&&ONB_CLASS_VISUALS[h.classId])s=onbSvgCrest(h.classId,ONB_CLASS_VISUALS[h.classId].color)}catch(e){}cr.innerHTML=s;cr.dataset.k=h.classId}
  const d=document.getElementById('ovDate');if(d)d.textContent=h.date;
  const g=document.getElementById('ovHello');if(g&&g.textContent!==h.hello)g.textContent=h.hello;
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