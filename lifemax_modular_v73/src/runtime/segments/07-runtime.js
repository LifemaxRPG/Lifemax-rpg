=[];Object.keys(CAT).forEach(a=>CAT[a].forEach(e=>ACTS.push({id:a+'.'+e[0].toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,''),attr:a,name:e[0],desc:e[1],xp:e[2],min:e[3],fl:e[4],skill:e[5]||ATTR_SKILL[a]})));
['scholar','entrepreneur','explorer'].forEach(k=>CLASS_CONTENT[k].acts.forEach(e=>ACTS.push({id:e[0]+'.'+e[1].toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,''),attr:e[0],name:e[1],desc:e[2],xp:e[3],min:e[4],fl:e[5],skill:e[6],cls:k})));
const LINKS={'physique.complete-a-strength-workout':['strength'],'endurance.complete-a-cardio-session':['cardio','athletic'],'wellbeing.complete-a-gentle-recovery-session':['recovery']};
const PLAN_LINK={strength:'physique.complete-a-strength-workout',cardio:'endurance.complete-a-cardio-session',athletic:'endurance.complete-a-cardio-session',recovery:'wellbeing.complete-a-gentle-recovery-session'};
let shAttr=null,shF='all',shQ='',shOpen=null,shBusy=false;
function baseXP(a){return Math.max(1,Number((state.actXP||{})[a.id])||a.xp)}
function weekStart(){const d=new Date();d.setDate(d.getDate()-((d.getDay()+6)%7));return dstr(d)}
function recs(a){return state.activities.filter(r=>r.actId===a.id)}
function actStatus(a){const t=getToday(),rs=recs(a),n=rs.filter(r=>activityDate(r)===t).length;let blocked='',factor=1;
 if(a.dev||isDevAct(a.id)){return {blocked:'',factor:1,n,xp:Math.max(1,Number(a.xp)||xpToGainLevels(attrXP(a.attr),devActLevels(a.id)))}}
 if(a.fl.includes('o')&&rs.length)blocked='Already recorded';
 else if(a.fl.includes('d')&&n)blocked='Done today';
 else if(a.fl.includes('w')&&rs.some(r=>activityDate(r)>=weekStart()))blocked='Done this week';
 else if(n>=3)blocked='Daily limit reached';
 else factor=[1,.5,.25][n];
 const kinds=LINKS[a.id];
 if(!blocked&&kinds&&state.activities.some(r=>activityDate(r)===t&&r.type==='plan'&&kinds.includes(r.kind)&&r.xp>0))blocked='Already counted via your workout plan';
 if(!blocked&&a.id==='business.transfer-money-into-savings'&&state.activities.some(r=>activityDate(r)===t&&r.type==='money'))blocked='Already counted via the Money tab';
 return {blocked,factor,n,xp:Math.max(1,Math.round(baseXP(a)*factor))}}
function openAttr(id){shAttr=id;shF='all';shQ='';shOpen=null;document.body.classList.add('noscroll');renderSheet();sheetOpen('attrSheet')}
function closeAttr(){sheetLeave('attrSheet');document.getElementById('attrSheet').classList.remove('show');document.body.classList.remove('noscroll');shAttr=null}
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&shAttr)closeAttr()});
function renderSheet(){const A=ATTRS.find(x=>x.id===shAttr),d=attrLevel(A.id);
 document.getElementById('sheetPanel').innerHTML=`<div class="sh-head"><div class="sh-top"><h3 style="margin:0;font-size:18px;display:flex;gap:8px;align-items:center">${px(A.icon,22)} ${A.name}</h3><button class="sh-x" onclick="closeAttr()" aria-label="Close">✕</button></div><div style="display:flex;justify-content:space-between;gap:8px;margin:10px 0 6px;font-size:13px"><strong style="color:var(--gold)">LV ${d.level}</strong><span class="muted">${attrXP(A.id).toLocaleString()} total XP · ${Math.round(d.pct)}% to next</span></div><div class="xpbar"><div class="xpfill" style="width:${d.pct}%"></div></div><p class="muted" style="font-size:12px;margin:10px 0 0">${ATTR_INFO[A.id]}</p></div><div class="sh-tools"><input id="shSearch" class="pv" style="flex:1;min-width:130px;width:auto" placeholder="Search activities" value="${esc(shQ)}" oninput="shQ=this.value;renderList()">${[['all','All'],['quick','Quick'],['core','Core'],['mile','Milestones']].map(f=>`<button class="chip ${shF===f[0]?'on':''}" onclick="shF='${f[0]}';renderSheet()">${f[1]}</button>`).join('')}</div><div class="sh-list" id="shList"></div>`;renderList()}
function renderList(){const q=shQ.toLowerCase();let list=ACTS.filter(a=>a.attr===shAttr&&(!a.cls||a.cls===state.classId)&&(!q||(a.name+' '+a.desc).toLowerCase().includes(q)));list=getDevActs(shAttr).concat(list);if(q)list=list.filter(a=>(a.name+' '+a.desc).toLowerCase().includes(q));
 const mile=a=>/[mo]/.test(a.fl)||a.xp>=40;
 if(shF==='quick')list=list.filter(a=>baseXP(a)<=15&&!mile(a));else if(shF==='core')list=list.filter(a=>baseXP(a)>15&&!mile(a));else if(shF==='mile')list=list.filter(mile);
 document.getElementById('shList').innerHTML=(list.length?list.map(actRow).join(''):'<div class="empty">No activities match.</div>')+`<div class="note" style="margin-top:14px">${ATTR_NOTE[shAttr]}</div>`}
function actRow(a){const st=actStatus(a),open=shOpen===a.id;
 const meta=[a.min?`<span class="tag">~${a.min} min</span>`:'',/m/.test(a.fl)?'<span class="tag">Name each milestone once</span>':'',a.fl.includes('o')?'<span class="tag">One time</span>':'',a.fl.includes('d')?'<span class="tag">Once a day</span>':'',a.fl.includes('w')?'<span class="tag">Once a week</span>':'',st.n?`<span class="tag" style="color:var(--accent2)">Done today ×${st.n}</span>`:'',st.blocked?`<span class="tag" style="color:var(--gold)">${st.blocked}</span>`:'',st.n?`<button class="btn small secondary" onclick="undoCat('${a.id}')">Undo last</button>`:''].join('');
 return `<div class="ar ${st.blocked?'blocked':''}"><button class="arh" onclick="toggleAct('${a.id}')" aria-expanded="${open}"><span><strong>${esc(a.name)}</strong><small>${esc(a.desc)}</small></span><span class="arxp">+${st.blocked?baseXP(a):st.xp}</span></button><div class="armeta">${meta}</div>${open?actForm(a,st):''}</div>`}
function actForm(a,st){const A=ATTRS.find(x=>x.id===a.attr);if(st.blocked)return `<div class="note" style="margin-bottom:10px">${st.blocked}. Nothing more to log right now.</div>`;
 return `<div class="arform">${a.min?`<div class="field"><label>Duration in minutes (at least ${a.min})</label><input id="actDur" type="number" min="${a.min}" max="600" value="${a.min}" inputmode="numeric"></div>`:''}${/[mr]/.test(a.fl)?`<div class="field"><label>${/m/.test(a.fl)?'Which milestone? (required)':'Result (optional)'}</label><input id="actRes" maxlength="80" placeholder="${/m/.test(a.fl)?'e.g. 10 clean pull-ups':'e.g. +5 lb on leg press'}"></div>`:''}<div class="field"><label>Note (optional)</label><textarea id="actNote" rows="2" maxlength="300"></textarea></div><div style="font-size:13px">Reward: <strong style="color:var(--accent2)">+${st.xp} XP</strong> to ${A.short} · skill: ${esc(a.skill)}${st.factor<1?' <span class="muted">(reduced, repeated today)</span>':''}</div><details><summary class="muted" style="cursor:pointer;font-size:12px">Customize base XP</summary><div style="display:flex;gap:8px;margin-top:8px"><input id="actBase" class="pv" type="number" min="1" max="200" value="${baseXP(a)}" style="width:90px"><button class="btn small secondary" onclick="saveBase('${a.id}')">Save</button><button class="btn small secondary" onclick="saveBase('${a.id}',1)">Reset</button></div></details><div style="display:flex;gap:8px"><button class="btn" style="flex:1" onclick="confirmAct('${a.id}')">Complete · +${st.xp} XP</button><button class="btn secondary" onclick="shOpen=null;renderList()">Cancel</button></div></div>`}
function toggleAct(id){shOpen=shOpen===id?null:id;renderList()}
function saveBase(id,reset){state.actXP=state.actXP||{};if(reset)delete state.actXP[id];else{const v=Math.min(200,Math.max(1,Math.round(Number(document.getElementById('actBase').value)||0)));if(v)state.actXP[id]=v}save();renderSheet();toast(reset?'XP reset to default':'XP updated')}
function fxClear(){document.querySelectorAll('.fx-root').forEach(n=>n.remove())}
/* ===== FX engine: canvas particles, XP orbs, level-up sequence, synth sounds ===== */
const ATTR_COL={physique:'#ff6b57',endurance:'#ff4d8d',intelligence:'#5b8cff',business:'#f5c542',social:'#ff9f43',discipline:'#a78bfa',adventure:'#3ddc84',wellbeing:'#4dd0e1'};
var chSel=null,chManual=false,chSort='level',chSkillsAll=false,chShown=false,chCmp=false,CH={cx:190,cy:172,R:96},CH_CIRC=339.29;
var CLASS_PLAN_KEY={scholar_light8:'scholar',entrepreneur_light8:'entrepreneur',explorer_outdoor8:'explorer'};
var PET_RT=null;

function fxOpt(){return {sound:true,haptics:true,reduce:false,...(state.fx||{})}}
function fxReduced(){return fxOpt().reduce||(window.matchMedia&&matchMedia('(prefers-reduced-motion: reduce)').matches)}
function haptic(p){if(!fxOpt().haptics)return;try{navigator.vibrate&&navigator.vibrate(p)}catch(e){}}
let lastTap={x:innerWidth/2,y:innerHeight*.6};
document.addEventListener('pointerdown',e=>{lastTap={x:e.clientX,y:e.clientY}},true);
const FX=(()=>{let cv,ctx,W=0,H=0,P=[],raf=0,last=0;
 function size(){const d=Math.min(2,devicePixelRatio||1);W=innerWidth;H=innerHeight;cv.width=W*d;cv.height=H*d;ctx.setTransform(d,0,0,d,0,0)}
 function init(){if(cv)return;cv=document.createElement('canvas');cv.style.cssText='position:fixed;inset:0;width:100%;height:100%;pointer-events:none;z-index:97';document.body.appendChild(cv);ctx=cv.getContext('2d');size();addEventListener('resize',size)}
 function add(p){init();p.age=-(p.delay||0);P.push(p);if(!raf){last=performance.now();raf=requestAnimationFrame(loop)}}
 function loop(t){const dt=Math.min(.05,(t-last)/1000);last=t;ctx.clearRect(0,0,W,H);
  for(let i=P.length-1;i>=0;i--){const p=P[i];p.age+=dt;if(p.age<0)continue;if(p.age>=p.life){if(p.done)p.done();P.splice(i,1);continue}const k=p.age/p.life;
   ctx.globalCompositeOperation=p.k==='conf'?'source-over':'lighter';
   if(p.k==='spark'){p.x+=p.vx*dt;p.y+=p.vy*dt;p.vy+=p.g*dt;p.vx*=1-1.6*dt;const s=p.s*(1-k*.55);ctx.globalAlpha=(1-k)*.28;ctx.fillStyle=p.c;ctx.fillRect(p.x-s*1.6,p.y-s*1.6,s*3.2,s*3.2);ctx.globalAlpha=1-k;ctx.fillRect(p.x-s/2,p.y-s/2,s,s)}
   else if(p.k==='ring'){const e=1-Math.pow(1-k,3);ctx.globalAlpha=(1-k)*.9;ctx.strokeStyle=p.c;ctx.lineWidth=p.w*(1-k)+1;ctx.beginPath();ctx.arc(p.x,p.y,p.r0+(p.r1-p.r0)*e,0,6.2832);ctx.stroke()}
   else if(p.k==='orb'){const u=k*k,v=1-u,x=v*v*p.x+2*v*u*p.cx+u*u*p.tx,y=v*v*p.y+2*v*u*p.cy+u*u*p.ty;if(Math.random()<.7)add({k:'spark',x,y,vx:(Math.random()-.5)*40,vy:(Math.random()-.5)*40,g:0,s:3,c:p.c,life:.35});const r=7+(1-k)*4,g=ctx.createRadialGradient(x,y,0,x,y,r*2.6);g.addColorStop(0,'#fff');g.addColorStop(.25,p.c);g.addColorStop(1,'transparent');ctx.globalAlpha=1;ctx.fillStyle=g;ctx.beginPath();ctx.arc(x,y,r*2.6,0,6.2832);ctx.fill()}
   else if(p.k==='conf'){p.x+=p.vx*dt+Math.sin(p.age*6+p.ph)*26*dt;p.y+=p.vy*dt;p.vy+=p.g*dt;p.vx*=1-.6*dt;p.rot+=p.vr*dt;ctx.save();ctx.translate(p.x,p.y);ctx.rotate(p.rot);ctx.globalAlpha=k>.8?(1-k)*5:1;ctx.fillStyle=p.c;ctx.scale(1,Math.abs(Math.cos(p.age*5+p.ph))*.8+.2);ctx.fillRect(-p.w/2,-p.h/2,p.w,p.h);ctx.restore()}
   else if(p.k==='star'){const s=p.s*Math.sin(Math.PI*k);ctx.globalAlpha=.95;ctx.fillStyle=p.c;ctx.fillRect(p.x-s,p.y-s*.16,s*2,s*.32);ctx.fillRect(p.x-s*.16,p.y-s,s*.32,s*2)}}
  ctx.globalAlpha=1;if(P.length)raf=requestAnimationFrame(loop);else{raf=0;ctx.clearRect(0,0,W,H)}}
 const R=(a,b)=>a+Math.random()*(b-a);
 return {burst(x,y,c,n,sp,g){for(let i=0;i<n;i++){const a=R(0,6.2832),v=sp*R(.3,1);add({k:'spark',x,y,vx:Math.cos(a)*v,vy:Math.sin(a)*v-sp*.25,g:g==null?500:g,s:Math.round(R(3,7)),c:Math.random()<.2?'#fff':c,life:R(.5,1.2)})}},
  ring(x,y,c,r1,life,w){add({k:'ring',x,y,c,r0:6,r1,life,w:w||5})},
  orb(x,y,tx,ty,c,delay,done){const dx=tx-x,dy=ty-y,d=Math.hypot(dx,dy)||1,o=R(-.45,.45)*d;add({k:'orb',x,y,tx,ty,cx:(x+tx)/2-dy/d*o,cy:(y+ty)/2+dx/d*o-d*.15,c,delay:delay/1000,life:R(.65,.95),done})},
  confetti(x,y,n,a0,a1,sp,cols){for(let i=0;i<n;i++){const a=R(a0,a1)*Math.PI/180,v=sp*R(.55,1);add({k:'conf',x,y,vx:Math.cos(a)*v,vy:Math.sin(a)*v,g:1300,rot:R(0,6),vr:R(-9,9),ph:R(0,6),w:R(6,10),h:R(10,16),c:cols[i%cols.length],life:R(2,3.2)})}},
  star(x,y,c,s,life){add({k:'star',x,y,c,s,life})}}})();
const SFX=(()=>{let ac;function A(){if(!ac){const C=window.AudioContext||window.webkitAudioContext;if(!C)return null;ac=new C()}if(ac.state==='suspended')ac.resume();return ac}
 function tone(f,t0,d,type,v){const a=A();if(!a)return;const o=a.createOscillator(),g=a.createGain(),t=a.currentTime+t0;o.type=type;o.frequency.value=f;g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(v,t+.012);g.gain.exponentialRampToValueAtTime(.0001,t+d);o.connect(g);g.connect(a.destination);o.start(t);o.stop(t+d+.03)}
 return {xp(n){if(!fxOpt().sound)return;(n>=25?[523,659,784,1047]:[659,880,1175]).forEach((f,i)=>tone(f,i*.06,.2,'square',.04))},
  level(epic){if(!fxOpt().sound)return;(epic?[523,659,784,1047,1319,1568]:[523,659,784,1047]).forEach((f,i)=>{tone(f,i*.09,.4,'square',.045);tone(f*2,i*.09+.02,.5,'triangle',.03)});if(epic){[262,330,392].forEach(f=>tone(f,.55,1.5,'triangle',.07));for(let i=0;i<9;i++)tone(2000+Math.random()*2200,.6+i*.07,.22,'sine',.02)}}}})();
function fxTarget(){let el=shAttr&&document.querySelector('#sheetPanel .xpbar');if(!el||!el.getBoundingClientRect().width)el=document.getElementById('brandLogo');const r=el?el.getBoundingClientRect():null;if(!r||!r.width||r.bottom<0)return {x:innerWidth/2,y:44,el:null};return {x:r.left+r.width/2,y:Math.min(innerHeight-30,Math.max(30,r.top+r.height/2)),el}}
function pulse(el){if(!el)return;el.classList.remove('fx-pulse');void el.offsetWidth;el.classList.add('fx-pulse');setTimeout(()=>el.classList.remove('fx-pulse'),700)}
/* A native screen saving an activity holds the celebration (it would play under the native screen, on the same frames as the switch
   to the next tab) and plays it afterwards: holdCelebrations(run) returns {result, held: [[delta, gains], ...]}. A screen that draws
   the celebration itself plays only xpCue(delta), the sound and the buzz. */
let fxHold=null;
function holdCelebrations(run){const prev=fxHold;fxHold=[];try{return {result:run(),held:fxHold}}finally{fxHold=prev}}
/* afterCelebrations(fn) runs fn once no XP celebration or level-up is playing or queued, so a native screen can wait before it
   covers the page again. A level-up starts while its XP is still flying (950 ms in, of 1550), so the two hand over. */
let fxUntil=0;const fxCalm=[];
function afterCelebrations(fn){fxCalm.push(fn);fxSettle()}
function fxSettle(){if(!luBusy&&Date.now()>=fxUntil)fxCalm.splice(0).forEach(f=>f())}
function celebrateXP(delta,gains){if(fxHold){fxHold.push([delta,gains]);return}const red=fxReduced(),o=lastTap,main=gains.slice().sort((a,b)=>b.xp-a.xp)[0],col=ATTR_COL[main.id]||'#6c8cff',tg=fxTarget();
 FX.ring(o.x,o.y,col,80,.55,5);FX.burst(o.x,o.y,col,red?8:30,280,420);
 gains.forEach(g=>{const c=ATTR_COL[g.id]||col,n=Math.min(red?4:16,3+Math.round(g.xp/3));for(let i=0;i<n;i++)FX.orb(o.x,o.y,tg.x,tg.y,c,i*38,()=>{const t=fxTarget();FX.burst(t.x,t.y,c,3,130,80);if(i===n-1)pulse(t.el)})});
 const t=document.createElement('div');t.className='fx-xp';t.style.cssText='left:'+Math.min(innerWidth-70,Math.max(70,o.x))+'px;top:'+Math.max(110,o.y-24)+'px;--c:'+col;const a=ATTRS.find(x=>x.id===main.id);t.innerHTML='+'+delta+' XP'+(gains.length===1&&a?'<small>'+a.short+'</small>':'');document.body.appendChild(t);fxUntil=Math.max(fxUntil,Date.now()+1550);setTimeout(()=>{t.remove();fxSettle()},1550);
 xpCue(delta)}
function xpCue(delta){SFX.xp(delta);haptic(delta>=25?[18,30,18]:12)}
let luBusy=false;const luQ=[];
function showLevelUp(ev){if(luBusy){luQ.push(ev);return}luBusy=true;const red=fxReduced(),epic=ev.charLevel!=null,m=ev.attrs[0];
 const col=epic?'#ffd36a':ATTR_COL[m.id],acc=m?ATTR_COL[m.id]:'#6c8cff',from=epic?ev.from:m.from,to=epic?ev.charLevel:m.level;
 const icon=epic?'trophy':(ATTRS.find(a=>a.id===m.id)||{}).icon||'star',sub=epic?titleFor(to):(ATTRS.find(a=>a.id===m.id)||{}).name;
 const chips=(epic||ev.attrs.length>1?ev.attrs:[]).map((a,i)=>`<span class="lu-chip" style="--cc:${ATTR_COL[a.id]};animation-delay:${1.5+i*.12}s">${px((ATTRS.find(x=>x.id===a.id)||{}).icon,16)} ${a.short} Lv ${a.level}</span>`).join('');
 const ov=document.createElement('div');ov.className='luov'+(epic?' epic':'')+(red?' reduce':'');ov.style.setProperty('--c',col);
 ov.innerHTML=`<div class="lu-fl"></div><div class="lu-rays"></div><div class="lu-pillar"></div><div class="lu-card"><div class="lu-kick">${epic?'CHARACTER':'ATTRIBUTE'}</div><div class="lu-ico">${px(icon,epic?92:76)}</div><h2 class="lu-t">${'LEVEL UP!'.split('').map((ch,i)=>`<span style="animation-delay:${.35+i*.05}s">${ch===' '?'&nbsp;':ch}</span>`).join('')}</h2><div class="lu-n"><span>LV ${from}</span><i style="font-style:normal">→</i><b>LV ${to}</b></div><div class="lu-s">${sub}</div><div class="lu-chips">${chips}</div><div class="lu-tap">Tap to continue</div></div>`;
 document.body.appendChild(ov);requestAnimationFrame(()=>ov.classList.add('in'));
 const cx=innerWidth/2,cy=innerHeight*.42,W=Math.max(innerWidth,innerHeight),born=Date.now();
 SFX.level(epic);haptic(epic?[40,40,40,60,160]:[30,30,70]);
 FX.ring(cx,cy,col,W*.75,1.1,12);setTimeout(()=>FX.ring(cx,cy,acc,W*.6,1,7),170);setTimeout(()=>FX.ring(cx,cy,'#ffffff',W*.5,.9,4),340);
 FX.burst(cx,cy,col,red?20:epic?120:64,560,320);setTimeout(()=>FX.burst(cx,cy,acc,red?10:40,380,260),250);
 if(!red){const cols=[col,'#ffffff',acc,'#ffd36a'];if(epic)for(let t=0;t<1100;t+=55)setTimeout(()=>{FX.confetti(innerWidth*.04,innerHeight,5,-82,-58,1350,cols);FX.confetti(innerWidth*.96,innerHeight,5,-122,-98,1350,cols)},700+t);else setTimeout(()=>FX.confetti(cx,cy,34,-170,-10,620,cols),380)}
 const iv=setInterval(()=>{FX.star(cx+(Math.random()-.5)*Math.min(innerWidth*.8,420),cy+(Math.random()-.5)*300,Math.random()<.5?col:'#fff',8+Math.random()*12,.7+Math.random()*.6)},red?260:110);
 let closed=false;function close(){if(closed)return;closed=true;clearInterval(iv);ov.classList.remove('in');ov.classList.add('out');setTimeout(()=>{ov.remove();luBusy=false;const n=luQ.shift();if(n)showLevelUp(n);else fxSettle()},380)}
 ov.addEventListener('click',()=>{if(Date.now()-born>700)close()});setTimeout(close,epic?7000:4600)}
function renderFxSettings(){const o=fxOpt(),g=id=>document.getElementById(id);if(!g('fxSound'))return;g('fxSound').checked=o.sound;g('fxHaptics').checked=o.haptics;g('fxReduce').checked=o.reduce}
function saveFx(){const g=id=>document.getElementById(id).checked;state.fx={sound:g('fxSound'),haptics:g('fxHaptics'),reduce:g('fxReduce')};save()}
function fxPreview(level){const l=characterLevel().level;if(level)showLevelUp({charLevel:l+1,from:l,attrs:[{id:'physique',short:'Physique',from:1,level:2},{id:'discipline',short:'Discipline',from:1,level:2}]});else celebrateXP(30,[{id:'physique',xp:30}])}
function floatXP(){} // superseded by celebrateXP() (fires from checkLevelUps)

function confirmAct(id){if(shBusy)return;const a=actById(id),st=actStatus(a);if(!a||st.blocked){toast(st.blocked||'Unavailable');return}
 const g=x=>{const el=document.getElementById(x);return el?el.value.trim():''};
 const dur=a.min?Number(g('actDur'))||0:0;if(a.min&&dur<a.min){toast('Needs at least '+a.min+' minutes');return}
 const res=g('actRes');if(/m/.test(a.fl)){if(!res){toast('Name the milestone first');return}if(recs(a).some(r=>(r.result||'').toLowerCase()===res.toLowerCase())){toast('That milestone is already recorded');return}}
 const last=recs(a).slice(-1)[0];if(last&&Date.now()-new Date(last.date)<2500)return;
 shBusy=true;setTimeout(()=>shBusy=false,900);
 const attrId=a.attr||shAttr;const A=ATTRS.find(x=>x.id===attrId)||ATTRS[0];
 const xp=a.dev||isDevAct(a.id)?xpToGainLevels(attrXP(attrId),devActLevels(a.id)):st.xp;
 const act={id:'act_'+Date.now()+'_'+Math.random().toString(36).slice(2,6),type:'catalog',actId:a.id,link:a.id,name:a.name,icon:A.icon,duration:dur,result:res,skill:a.skill||'Practice',primary:attrId,secondary:'',xp,date:new Date().toISOString(),notes:g('actNote'),xpSplit:[{attr:attrId,xp}]};
 state.activities.push(act);shOpen=null;save();floatXP(xp);toast(a.name+' · +'+xp+' XP');renderSheet()}
function undoCat(id){const a=actById(id),r=recs(a).filter(x=>activityDate(x)===getToday()).slice(-1)[0];if(!r)return;removeAct(r.id);save();renderSheet();toast('Undone · XP removed')}
function renderAvatar(){document.getElementById('brandName').textContent=state.name||'LifeMax';['heroAvatar','charAvatar','brandLogo','characterMainAvatar'].forEach(id=>{const el=document.getElementById(id);if(!el)return;if(state.avatar){el.style.backgroundImage=`url("${state.avatar}")`;el.textContent=''}else{el.style.backgroundImage='none';el.textContent=((state.name||'?').trim()[0]||'?').toUpperCase()}});renderCharacterIntro()}
function renderCharacterIntro(){const n=document.getElementById('characterMainName');if(n)n.textContent=state.name||'The Life Architect';const w=document.getElementById('characterWeightDisplay');if(w)w.textContent=state.weight?`${Number(state.weight).toLocaleString()} lb`:'—';const b=document.getElementById('characterBmiDisplay');if(b){const inches=(Number(state.heightFt)||0)*12+(Number(state.heightIn)||0);const bmi=state.weight&&inches>0?703*Number(state.weight)/(inches*inches):null;b.textContent=bmi?bmi.toFixed(1):'—'}}
function avatarPicked(e){const f=e.target.files[0];e.target.value='';if(!f)return;if(!f.type.startsWith('image/')){toast('Please choose an image file');return}
 const r=new FileReader();r.onload=()=>{const img=new Image();img.onload=()=>{try{const n=256,c=document.createElement('canvas');c.width=c.height=n;const m=Math.min(img.width,img.height);c.getContext('2d').drawImage(img,(img.width-m)/2,(img.height-m)/2,m,m,0,0,n,n);state.avatar=c.toDataURL('image/jpeg',.85);save();toast('Profile picture updated')}catch(err){delete state.avatar;toast('Could not save that image')}};img.onerror=()=>toast('Could not read that image');img.src=r.result};r.readAsDataURL(f)}
function removeAvatar(){if(!state.avatar){toast('No picture to remove');return}delete state.avatar;save();toast('Profile picture removed')}

/* A media item's status as the page words it. */
const MEDIA_STATUS={unread:'Want to read / watch',progress:'In progress',finished:'Finished'};
function showMediaForm(item){
 const card=document.getElementById('mediaFormCard');card.style.display='block';
 document.getElementById('mediaFormTitle').textContent=item?'Edit media item':'Add to your library';
 document.getElementById('mediaEditId').value=item?item.id:'';
 document.getElementById('mediaTitle').value=item?item.title:'';
 document.getElementById('mediaType').value=item?item.type:'Book';
 document.getElementById('mediaCreator').value=item?item.creator||'':'';
 document.getElementById('mediaStatus').value=item?item.status:'unread';
 document.getElementById('mediaLink').value=item?item.link||'':'';
 document.getElementById('mediaNotes').value=item?item.notes||'':'';
 card.scrollIntoView({behavior:'smooth',block:'start'});setTimeout(()=>document.getElementById('mediaTitle').focus(),250);
}
function hideMediaForm(){document.getElementById('mediaFormCard').style.display='none';document.getElementById('mediaForm').reset();document.getElementById('mediaEditId').value=''}
function saveMediaItem(e){
 e.preventDefault();const v=id=>document.getElementById(id).value;
 const res=mediaUpsert({id:v('mediaEditId'),title:v('mediaTitle'),type:v('mediaType'),creator:v('mediaCreator'),status:v('mediaStatus'),link:v('mediaLink'),notes:v('mediaNotes')});
 if(!res.ok){toast(res.message);return}
 hideMediaForm();navigate('media');toast(res.message);
}
/* Add a media item, or change the one whose id is f.id, by the form's rules (a title, an http or https link, the form's lengths),
   and save. Returns {ok, id, message}; it never touches the page. A save that fails is taken back out and reported. */
function mediaUpsert(f){
 const title=String(f.title||'').trim().slice(0,160);if(!title)return {ok:false,message:'Add a title first'};
 const link=String(f.link||'').trim().slice(0,500);
 if(link&&!/^https?:\/\//i.test(link))return {ok:false,message:'Use a valid http or https link'};
 if(!Array.isArray(state.mediaItems))state.mediaItems=[];
 const id=String(f.id||''),kept=state.mediaItems.slice(),index=kept.findIndex(x=>x.id===id);
 const item={id:id||('media_'+Date.now()+'_'+Math.random().toString(36).slice(2,7)),title,type:String(f.type||''),creator:String(f.creator||'').trim().slice(0,120),status:String(f.status||''),link,notes:String(f.notes||'').trim().slice(0,1000),updatedAt:new Date().toISOString()};
 if(index>=0)state.mediaItems[index]={...kept[index],...item};else state.mediaItems.unshift(item);
 if(!save()){state.mediaItems=kept;renderMedia();return {ok:false,message:unsavedMessage()}}
 return {ok:true,id:item.id,message:index>=0?'Media item updated':'Added to your library'};
}
function toggleMediaFinished(id,checked){
 const item=(state.mediaItems||[]).find(x=>x.id===id);if(!item)return;
 const was=[item.status,item.updatedAt];
 item.status=checked?'finished':'progress';item.updatedAt=new Date().toISOString();
 if(!save()){[item.status,item.updatedAt]=was;renderMedia();toast(unsavedMessage());return}
 toast(checked?'Marked as finished':'Moved back to in progress');
}
function setMediaStatus(id,status){
 const item=(state.mediaItems||[]).find(x=>x.id===id);if(!item)return;
 item.status=status;item.updatedAt=new Date().toISOString();save();toast('Status updated');
}
function deleteMediaItem(id){
 const item=(state.mediaItems||[]).find(x=>x.id===id);if(!item)return;
 if(!confirm('Remove "'+item.title.replace(/"/g,'\\"')+'" from your media library?'))return;
 const kept=state.mediaItems;state.mediaItems=kept.filter(x=>x.id!==id);
 if(!save()){state.mediaItems=kept;renderMedia();toast(unsavedMessage());return}
 if(audioCtx.mediaId===id)closeAudioPlayer(true);audioDbDelete(id);toast('Removed from library');
}

/* —— Audiobook / audio file support (IndexedDB) —— */
const AUDIO_DB='lifemax_audio_v1', AUDIO_STORE='files';
let audioCtx={mediaId:null, loading:null, objectUrl:null, seeking:false, saveTimer:null}; // loading: the id whose file is being read
function audioDbOpen(){
  return new Promise((resolve,reject)=>{
    const req=indexedDB.open(AUDIO_DB,1);
    req.onupgradeneeded=()=>{const db=req.result;if(!db.objectStoreNames.contains(AUDIO_STORE))db.createObjectStore(AUDIO_STORE)};
    req.onsuccess=()=>resolve(req.result);
    req.onerror=()=>reject(req.error||new Error('IndexedDB unavailable'));
  });
}
function audioDbPut(id, record){
  return audioDbOpen().then(db=>new Promise((resolve,reject)=>{
    const tx=db.transaction(AUDIO_STORE,'readwrite');
    tx.objectStore(AUDIO_STORE).put(record, id);
    tx.oncomplete=()=>resolve();
    tx.onerror=()=>reject(tx.error);
  }));
}
function audioDbGet(id){
  return audioDbOpen().then(db=>new Promise((resolve,reject)=>{
    const tx=db.transaction(AUDIO_STORE,'readonly');
    const req=tx.objectStore(AUDIO_STORE).get(id);
    req.onsuccess=()=>resolve(req.result||null);
    req.onerror=()=>reject(req.error);
  }));
}
function audioDbDelete(id){
  return audioDbOpen().then(db=>new Promise((resolve,reject)=>{
    const tx=db.transaction(AUDIO_STORE,'readwrite');
    tx.objectStore(AUDIO_STORE).delete(id);
    tx.oncomplete=()=>resolve();
    tx.onerror=()=>reject(tx.error);
  })).catch(()=>{});
}
function fmtAudioTime(sec){
  sec=Math.max(0,Math.floor(Number(sec)||0));
  const h=Math.floor(sec/3600), m=Math.floor((sec%3600)/60), s=sec%60;
  if(h>0)return h+':'+String(m).padStart(2,'0')+':'+String(s).padStart(2,'0');
  return m+':'+String(s).padStart(2,'0');
}
function fmtBytes(n){
  n=Number(n)||0;
  if(n<1024)return n+' B';
  if(n<1048576)return (n/1024).toFixed(1)+' KB';
  return (n/1048576).toFixed(1)+' MB';
}
function mediaById(id){return (state.mediaItems||[]).find(x=>x.id===id)}
function pickAudioForMedia(id){
  const input=document.getElementById('audioFileInput');
  if(!input){toast('Audio picker unavailable');return}
  input.onchange=async e=>{
    const file=e.target.files&&e.target.files[0];
    input.value='';
    input.onchange=null;
    if(!file)return;
    await attachAudioFile(id, file);
  };
  input.click();
}
async function attachAudioFile(id, file){
  const item=mediaById(id);if(!item)return;
  const max=512*1024*1024; // 512 MB soft limit
  if(file.size>max){toast('File too large (max ~512 MB)');return}
  toast('Saving audio to this device…');
  try{
    await audioDbPut(id,{
      blob:file,
      name:file.name,
      type:file.type||'audio/mp4',
      size:file.size,
      updatedAt:new Date().toISOString()
    });
    item.audio={
      name:file.name,
      size:file.size,
      type:file.type||'audio/mp4',
      position:item.audio&&item.audio.position||0,
      duration:item.audio&&item.audio.duration||0
    };
    if(!item.type||item.type==='Book'||item.type==='Other')item.type='Audiobook';
    if(item.status==='unread')item.status='progress';
    item.updatedAt=new Date().toISOString();
    save();
    toast('Audio attached · '+file.name);
    await playMediaAudio(id, true);
  }catch(err){
    console.warn(err);
    toast('Could not store audio (storage full or blocked)');
  }
}
async function removeMediaAudio(id){
  const item=mediaById(id);if(!item||!item.audio)return;
  if(!confirm('Remove the stored audio file for "'+item.title+'"? Playback position will be cleared.'))return;
  const was=[item.audio,item.updatedAt];
  delete item.audio;
  item.updatedAt=new Date().toISOString();
  if(!save()){[item.audio,item.updatedAt]=was;renderMedia();toast(unsavedMessage());return}
  if(audioCtx.mediaId===id)closeAudioPlayer(true);
  await audioDbDelete(id);
  toast('Audio removed');
}
function r