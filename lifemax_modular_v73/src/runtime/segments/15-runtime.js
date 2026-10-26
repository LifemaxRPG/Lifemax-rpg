t(){var s=document.querySelector('#chRadar svg'),g=s&&s.querySelector('#chAll');if(!g||!g.getBBox)return;try{var b=g.getBBox();if(b.width>10&&b.height>10)s.setAttribute('viewBox',(b.x-8).toFixed(1)+' '+(b.y-8).toFixed(1)+' '+(b.width+16).toFixed(1)+' '+(b.height+16).toFixed(1))}catch(e){}}
function chDetailHtml(a,D){var d=attrLevel(a.id),col=ATTR_COL[a.id],dc=chDecay(a),wk=D.wk[a.id],per=D.per[a.id],mx=Math.max.apply(null,per.concat([1])),short={none:'Not started',off:'Off',ok:'Safe',soon:'Soon',zero:'Empty',decay:'Active'}[dc.k];
 var spark=per.map(function(v,i){var dt=new Date(D.days[i]+'T12:00:00');return '<div title="'+v+' XP"><i style="height:'+Math.max(8,Math.round(v/mx*100))+'%'+(v?'':';opacity:.22')+'"></i><small>'+'SMTWTFS'.charAt(dt.getDay())+'</small></div>'}).join('');
 return '<div class="ch-detail" style="--c:'+col+'"><div class="ch-d-top"><span class="ch-d-ic">'+px(a.icon,22)+'</span><div class="ch-d-id"><div class="ch-d-name">'+a.name+'</div><div class="ch-d-sub">'+d.into.toLocaleString()+' / '+d.needed.toLocaleString()+' XP to next · '+attrXP(a.id).toLocaleString()+' total</div></div><span class="ch-d-lv">LV '+d.level+'</span></div>'
 +'<div class="ch-bar" style="margin-top:12px"><i style="width:'+d.pct.toFixed(1)+'%"></i></div>'
 +'<div class="ch-d-stats"><div class="ch-d-stat"><small>THIS WEEK</small><b class="'+(wk?'up':'')+'">'+(wk?'+'+wk.toLocaleString()+' XP':'None')+'</b></div><div class="ch-d-stat"><small>LAST TRAINED</small><b>'+dc.sub+'</b></div><div class="ch-d-stat"><small>DECAY</small><b style="color:'+dc.col+'">'+short+'</b></div></div>'
 +'<div class="ch-d-note" style="color:'+dc.col+'">'+dc.txt+'</div><div class="ch-spark">'+spark+'</div>'
 +'<button class="btn ch-d-btn" type="button" onclick="openAttr(\''+a.id+'\')">Log '+a.short+' activity</button></div>'}
function chRenderBuild(){var el=document.getElementById('chRadar');if(!el)return;var D=chCompute(),L=ATTRS.map(function(a){return attrLevel(a.id).level}),P=ATTRS.map(function(a){return levelData(D.prev[a.id]).level}),foc=chFocus(),cap=chCap(L,P);
 if(!chManual||!chSel||!ATTRS.some(function(a){return a.id===chSel})){var best=0;L.forEach(function(l,i){if(l>L[best])best=i});chSel=L[best]>1?ATTRS[best].id:(foc[0]||ATTRS[0].id)}
 el.innerHTML=chRadar(L,P,foc,cap);el.classList.toggle('cmp',chCmp);
 var ar=chArchetype(L),mx=Math.max.apply(null,L),mn=Math.min.apply(null,L),hi=ATTRS[L.indexOf(mx)],lo=ATTRS[L.indexOf(mn)],ch=ATTRS.map(function(a,i){return P[i]<L[i]?a.short+' +'+(L[i]-P[i]):''}).filter(Boolean);
 var ins=document.getElementById('chIns');if(ins)ins.innerHTML='<span class="ch-pill arch" title="'+ar.d+'">'+ar.n+'</span>'+(mx>1?'<button type="button" class="ch-pill" style="--c:'+ATTR_COL[hi.id]+'" onclick="chSelect(\''+hi.id+'\')">Strongest · '+hi.short+' '+mx+'</button>':'')+(mx>mn?'<button type="button" class="ch-pill warn" style="--c:'+ATTR_COL[lo.id]+'" onclick="chSelect(\''+lo.id+'\')">Weakest · '+lo.short+' '+mn+'</button>':'');
 var note=document.getElementById('chCmpNote');if(note){note.style.display=chCmp?'':'none';note.textContent=ch.length?'Level gains this week: '+ch.join(', ')+'.':'No level changes in the last 7 days.'}
 var sn=document.getElementById('chSegNow'),sw=document.getElementById('chSegWk');if(sn){sn.classList.toggle('on',!chCmp);sw.classList.toggle('on',chCmp)}
 chPaintSel(false);chFit();window.__chD=D}
function chPaintSel(withDetail){var root=document.getElementById('chRadar');if(!root)return;root.querySelectorAll('[data-id]').forEach(function(n){n.classList.toggle('sel',n.getAttribute('data-id')===chSel)});
 var a=ATTRS.find(function(x){return x.id===chSel}),box=document.getElementById('chDetail');if(a&&box){box.innerHTML=chDetailHtml(a,window.__chD||chCompute())}}
function chSelect(id){chManual=true;if(id===chSel)return;chSel=id;chPaintSel(true);haptic(8)}
function chSetCmp(v){chCmp=!!v;var el=document.getElementById('chRadar');if(el)el.classList.toggle('cmp',chCmp);var note=document.getElementById('chCmpNote');if(note){note.style.display=chCmp?'':'none'}var sn=document.getElementById('chSegNow'),sw=document.getElementById('chSegWk');if(sn){sn.classList.toggle('on',!chCmp);sw.classList.toggle('on',chCmp)}}
/* attribute cards */
function chAttrCard(a,D,foc,n){var d=attrLevel(a.id),col=ATTR_COL[a.id],w=D.wk[a.id],dc=chDecay(a),pill=dc.k==='decay'?'<span class="ch-pill bad">Decaying</span>':dc.k==='soon'?'<span class="ch-pill warn">Decay in '+dc.left+'d</span>':'';
 return '<div class="ch-ac" style="--c:'+col+';--i:'+(n||0)+'" role="button" tabindex="0" onclick="openAttr(\''+a.id+'\')" onkeydown="if(event.key===\'Enter\')openAttr(\''+a.id+'\')"><div class="ch-ac-top"><span class="ch-ac-ic">'+px(a.icon,20)+'</span><span class="ch-ac-lv">LV '+d.level+'</span></div><div class="ch-ac-nm">'+a.short+(foc.indexOf(a.id)>=0?' <i class="ch-star">★</i>':'')+'</div><div class="ch-bar"><i style="width:'+d.pct.toFixed(1)+'%"></i></div><div class="ch-ac-meta"><span>'+d.into.toLocaleString()+' / '+d.needed.toLocaleString()+'</span><span class="'+(w>0?'up':'')+'">'+(w>0?'+'+w.toLocaleString()+' wk':'—')+'</span></div>'+(pill?'<div class="ch-ac-pill">'+pill+'</div>':'')+'</div>'}
function chRenderAttrs(){var el=document.getElementById('allAttrs');if(!el)return;var D=window.__chD||chCompute(),foc=chFocus(),list=ATTRS.slice();
 if(chSort==='level')list.sort(function(a,b){return attrXP(b.id)-attrXP(a.id)});
 else if(chSort==='attention')list.sort(function(a,b){return chDecay(a).rank-chDecay(b).rank||attrXP(b.id)-attrXP(a.id)});
 else list.sort(function(a,b){return D.wk[b.id]-D.wk[a.id]||attrXP(b.id)-attrXP(a.id)});
 el.innerHTML=list.map(function(a,i){return chAttrCard(a,D,foc,i)}).join('');
 var bar=document.getElementById('chSort');if(bar)bar.innerHTML=[['level','Level'],['attention','Needs attention'],['week','This week']].map(function(m){return '<button type="button" class="'+(chSort===m[0]?'on':'')+'" onclick="chSetSort(\''+m[0]+'\')">'+m[1]+'</button>'}).join('')}
function chSetSort(m){chSort=m;chRenderAttrs()}
/* hero */
function renderCharacterHero(){var lv=characterLevel(),c=state.classId?classById(state.classId):null,next=TITLES.find(function(x){return x[0]>lv.level}),set=function(id,t){var e=document.getElementById(id);if(e)e.textContent=t},D=window.__chD||chCompute();
 set('chLvBadge','LV '+lv.level);set('chTitle',titleFor(lv.level).toUpperCase()+(c?' · '+c.name.toUpperCase():''));set('chXpText',lv.into.toLocaleString()+' / '+lv.needed.toLocaleString()+' XP');set('chXpTo','to level '+(lv.level+1));
 set('chNext',next?'Next title: '+next[1]+' at level '+next[0]+' ('+(next[0]-lv.level)+' to go)':'Highest title reached. You are a Legend.');
 var ring=document.getElementById('chRing'),bar=document.getElementById('chXpBar');
 if(ring){ring.setAttribute('data-off',(CH_CIRC*(1-lv.pct/100)).toFixed(1));if(chShown)ring.style.strokeDashoffset=ring.getAttribute('data-off')}
 if(bar){bar.setAttribute('data-pct',lv.pct.toFixed(1));if(chShown)bar.style.width=bar.getAttribute('data-pct')+'%'}
 var em=document.getElementById('chEmblem');if(em){var h='';try{if(c&&typeof onbSvgCrest==='function'&&ONB_CLASS_VISUALS[c.id])h=onbSvgCrest(c.id,ONB_CLASS_VISUALS[c.id].color)}catch(e){}if(em.getAttribute('data-k')!==(c?c.id:'')){em.innerHTML=h;em.setAttribute('data-k',c?c.id:'')}}
 var L=luckToday(),lt=L?luckTier(L.score):null,ch=document.getElementById('chChips');
 if(ch)ch.innerHTML='<div class="ch-chip"><small>STREAK</small><b>'+streak()+'<em>d</em></b></div><div class="ch-chip"><small>TOTAL XP</small><b>'+totalXP().toLocaleString()+'</b></div><div class="ch-chip"><small>THIS WEEK</small><b class="'+(D.total?'up':'')+'">'+(D.total?'+'+D.total.toLocaleString():'0')+'</b></div><div class="ch-chip luck" onclick="openLuck()"><small>LUCK</small><b style="color:'+(lt?lt.c:'var(--muted)')+'">'+(L?luckFmt(L.score):'Roll')+'</b></div>'}
function chReplay(){['chRadar','allAttrs'].forEach(function(id){var r=document.getElementById(id);if(r){r.classList.remove('ch-in');void r.offsetWidth;r.classList.add('ch-in')}});
 var ring=document.getElementById('chRing'),bar=document.getElementById('chXpBar');chShown=true;
 if(ring){ring.style.transition='none';ring.style.strokeDashoffset=String(CH_CIRC);void ring.getBoundingClientRect();ring.style.transition='';requestAnimationFrame(function(){requestAnimationFrame(function(){ring.style.strokeDashoffset=ring.getAttribute('data-off')||String(CH_CIRC)})})}
 if(bar){bar.style.transition='none';bar.style.width='0%';void bar.offsetWidth;bar.style.transition='';requestAnimationFrame(function(){requestAnimationFrame(function(){bar.style.width=(bar.getAttribute('data-pct')||0)+'%'})})}
 document.querySelectorAll('#view-character .ch-bar i').forEach(function(i){var w=i.style.width;i.style.transition='none';i.style.width='0%';void i.offsetWidth;i.style.transition='';requestAnimationFrame(function(){requestAnimationFrame(function(){i.style.width=w})})});chFit()}
function renderCharacterPage(){if(!document.getElementById('chRadar'))return;window.__chD=chCompute();renderCharacterHero();chRenderBuild();chRenderAttrs()}
/* skills, coloured by the attribute they mostly train */
function renderSkills(){var el=document.getElementById('skillsList');if(!el)return;var arr=Object.entries(state.skillXP||{}).filter(function(e){return e[1]>0}).sort(function(a,b){return b[1]-a[1]});
 if(!arr.length){el.innerHTML='<div class="empty"><div>⌁</div>Skills appear here as you practice them.</div>';return}
 var am={};(state.activities||[]).forEach(function(a){var sk=(a.skill||'').trim();if(!sk)return;am[sk]=am[sk]||{};var p=a.primary||'';am[sk][p]=(am[sk][p]||0)+(a.xp||0)});
 var colOf=function(n){var m=am[n]||{},best=null,bv=-1;Object.keys(m).forEach(function(k){if(m[k]>bv){bv=m[k];best=k}});return ATTR_COL[best]||'var(--accent)'};
 el.innerHTML=(chSkillsAll?arr:arr.slice(0,6)).map(function(e){var d=levelData(e[1]);return '<div class="ch-sk" style="--c:'+colOf(e[0])+'"><div class="ch-sk-top"><strong>'+esc(e[0])+'</strong><span class="ch-sk-lv">LV '+d.level+'</span></div><div class="ch-bar"><i style="width:'+d.pct.toFixed(1)+'%"></i></div><small>'+e[1].toLocaleString()+' XP · '+Math.round(d.pct)+'% to next</small></div>'}).join('')+(arr.length>6?'<button class="btn small secondary" type="button" style="margin-top:6px" onclick="chSkillsAll=!chSkillsAll;renderSkills()">'+(chSkillsAll?'Show fewer':'Show all '+arr.length)+'</button>':'')}

/* ===== Pet: a cat that lives in a small room. Care loop and bond ===== */
var PET_COATS=[['ginger','Ginger tabby'],['gray','Gray tabby'],['black','Black cat'],['tuxedo','Tuxedo']];
var PET_TRAITS={shy:'Shy',playful:'Playful',lazy:'Lazy',curious:'Curious'};
var PET_LEVELS=['Stray','Wary','Curious','Friendly','Fond','Devoted'];
var PET_BOND_AT=[0,30,90,200,360,580];
var PET_NAMES=['Mochi','Biscuit','Pepper','Miso','Luna','Waffles','Tofu','Maple','Juniper','Pickle','Sesame','Clover'];
var PET_H=36e5;
var PET_CTX=null;                    /* v80: which cat the engine is running as (null = the first cat, state.pet); see 29-pet-pair.js */
function petData(){return PET_CTX?(state.pet2||null):(state.pet||null)}
function petDay(){return getToday()}
function petRollDay(p){const d=petDay();if(p.day!==d){p.day=d;p.fedToday=0;p.petsToday=0;p.playsToday=0}}
function petAdopt(coat,name,trait){
  const now=Date.now();
  state.pet={v:1,name:(name||'').trim().slice(0,14)||PET_NAMES[Math.floor(Math.random()*PET_NAMES.length)],coat:coat,trait:trait||'curious',born:now,
    lastFed:now-10*PET_H,meals:[],day:petDay(),fedToday:0,petsToday:0,playsToday:0,bondPts:0,care:0,
    vacation:{on:false,since:0},gifts:0,lastGiftDay:'',alive:true,passed:0,seen:{},greeted:0};
  save();return state.pet;
}
function petHours(p,now){
  now=now||Date.now();
  const end=p.vacation&&p.vacation.on?p.vacation.since:now;
  return Math.max(0,(end-p.lastFed)/PET_H);
}
/* hunger stage: 0 content, 1 peckish, 2 hungry, 3 very hungry, 4 weak, 5 critical */
function petStageOf(h){return h<8?0:h<14?1:h<26?2:h<50?3:h<120?4:5}
function petStage(p,now){return petStageOf(petHours(p,now))}
function petLevel(p){let l=0;for(let i=0;i<PET_BOND_AT.length;i++)if(p.bondPts>=PET_BOND_AT[i])l=i;return l}
function petLevelPct(p){const l=petLevel(p);if(l>=PET_BOND_AT.length-1)return 100;const a=PET_BOND_AT[l],b=PET_BOND_AT[l+1];return Math.round((p.bondPts-a)/(b-a)*100)}
function petGain(p,pts){if(petStage(p)>=2)return 0;const before=petLevel(p);p.bondPts+=pts;return petLevel(p)>before?petLevel(p):0}
function petNextMealIn(p,now){return Math.max(0,4-petHours(p,now))}
function petCanFeed(p,now){return p.alive&&petHours(p,now)>=4}
/* feeding: needs at least 4 hours since the last meal, so a normal two-a-day rhythm is exactly right */
function petFeed(now){
  const p=petData();if(!p||!p.alive)return {ok:false,why:'none'};
  now=now||Date.now();petRollDay(p);
  if(p.vacation.on)return {ok:false,why:'vacation'};
  if(!petCanFeed(p,now))return {ok:false,why:'full',inHours:petNextMealIn(p,now)};
  p.lastFed=now;p.meals.push(now);if(p.meals.length>30)p.meals.shift();p.fedToday++;
  let up=0;if(p.fedToday<=2){up=petGain(p,6);if(p.fedToday===2)p.care++}
  return {ok:true,levelUp:up};
}
/* petting counts three times a day, playing twice, so there is nothing to grind */
function petPetDone(){
  const p=petData();if(!p||!p.alive)return {ok:false};petRollDay(p);
  if(p.petsToday>=3)return {ok:true,counted:false};p.petsToday++;return {ok:true,counted:true,levelUp:petGain(p,2)};
}
function petPlayDone(){
  const p=petData();if(!p||!p.alive)return {ok:false};petRollDay(p);
  if(p.playsToday>=2)return {ok:true,counted:false};p.playsToday++;return {ok:true,counted:true,levelUp:petGain(p,3)};
}
/* gentle consequences: a long run with no food only slowly erodes the bond, and a week without food is the end */
function petCheck(now){
  const p=petData();if(!p||!p.alive)return null;now=now||Date.now();petRollDay(p);
  if(p.vacation.on)return null;
  const h=petHours(p,now);
  if(h>=168){p.alive=false;p.passed=now;
    state.petMemorial=state.petMemorial||[];state.petMemorial.push({name:p.name,coat:p.coat,days:Math.max(1,Math.round((now-p.born)/864e5)),passed:now,bond:petLevel(p)});
    save();return 'passed'}
  if(h>=96){const d=Math.floor((h-96)/24);if((p.erodedDays||0)<d){p.bondPts=Math.max(0,p.bondPts-8*(d-(p.erodedDays||0)));p.erodedDays=d}}
  else p.erodedDays=0;
  return null;
}
function petSetVacation(on){
  const p=petData();if(!p||!p.alive)return;const now=Date.now();
  if(on&&!p.vacation.on){p.vacation={on:true,since:now}}
  else if(!on&&p.vacation.on){p.lastFed+=now-p.vacation.since;p.vacation={on:false,since:0}}
  save();
}
function petRename(n){const p=petData();if(!p)return;n=(n||'').trim().slice(0,14);if(n){p.name=n;save()}}
function petAdoptNew(){state.pet=null;save()}

/* ===== Pet scene: assets, behaviour, interaction, drawing ===== */
var PET_SURF={floor:{y:132,x0:34,x1:158},bed:{y:128,x:36},sill:{y:74,x0:38,x1:76},mid:{y:100,x0:146,x1:158},top:{y:64,x0:150,x1:158}};
var PET_XMIN=34,PET_XMAX=158;function petClampX(x){return Math.max(PET_XMIN,Math.min(PET_XMAX,x))}
/* ---------- the house: living room | bedroom | kitchen, side by side; the camera follows the cat ---------- */
var PET_HX0=-192,PET_HX1=384,PET_HW=576,PET_HMIN=-154,PET_HMAX=346;
PET_SURF.floor.x0=PET_HMIN;PET_SURF.floor.x1=PET_HMAX;
PET_SURF.couch={y:109,x0:-136,x1:-60};PET_SURF.counter={y:91,x0:286,x1:340};
var PET_WINDOWS=[-152,0,280];                 /* sky offsets of the three windows, relative to the bedroom window */
function petClampHouse(x){return Math.max(PET_HMIN,Math.min(PET_HMAX,x))}
function petRoomOf(x){return x<-4?'living':x>=196?'kitchen':'bedroom'}
var PET_BOWL={food:{x:104,top:126},water:{x:124,top:126}};
var PET_EATX=87,PET_EATDIR=1;      /* v80: the second cat eats from the other side of the bowl (29 swaps these) */
var PET_WEAK={idle_sit:'idle_sit_w',walk:'walk_w',loaf:'loaf_w',sleep:'sleep_w'};
function petNow(){return Date.now()+((PET_RT&&PET_RT.shift)||0)}
function petHour(){const d=new Date(petNow());return d.getHours()+d.getMinutes()/60+d.getSeconds()/3600}
function petDayKey(){const d=new Date(petNow());return d.getFullYear()+'-'+(d.getMonth()+1)+'-'+d.getDate()}
function petHash(s){let h=2166136261>>>0;s=String(s);for(let i=0;i<s.length;i++){h^=s.charCodeAt(i);h=Math.imul(h,16777619)}return h>>>0}
function petRng(seed){let a=seed>>>0;return function(){a=(a+0x6D2B79F5)>>>0;let t=a;t=Math.imul(t^(t>>>15),t|1);t^=t+Math.imul(t^(t>>>7),t|61);return((t^(t>>>14))>>>0)/4294967296}}
/* ---------- a cat's day: built from the date and the cat itself, so the same moment always gives the same plan ---------- */
function petBuildDay(p,day){
  const kit=Math.max(0,1-petGrowth(p)),r=petRng(petHash(p.born+'|'+day+'|routine')),tr=p.trait,lazy=tr==='lazy',play=tr==='playful',shy=tr==='shy';
  const wake=6.1+r()*1.2+(lazy?0.7:0)-(play?0.3:0),bed=Math.min(23.5,22.2+r()*1.3-(lazy?0.3:0)+(play?0.4:0));
  const B=[];let t=0;
  const push=function(len,k,spot){const b=Math.min(24,t+len);if(b-t<0.02)return;B.push({a:t,b:b,k:k,spot:spot});t=b};
  const pick=function(arr){return arr[Math.floor(r()*arr.length)]};
  const sleepSpot=function(hr){return petLikesPickSpot(p,r,'sleep',hr)};              /* weighted by what this cat likes */
  const night=sleepSpot(2);
  push(wake,'sleep',night);                       // the night
  push(0.5+r()*0.6,'active','floor');             // a lively morning
  let last='active';
  while(t<17.4){                                  // the quiet middle of the day
    let k;if(last==='active')k=r()<0.5?'nap':'rest';else if(last==='rest')k=r()<0.34?'nap':'active';else k=r()<0.4?'rest':'active';
    const len=k==='nap'?(0.75+r()*1.25)*(lazy?1.2:1)*(1+0.3*kit):k==='rest'?(0.3+r()*0.6)*(1-0.3*kit):(0.16+r()*0.35)*(1+0.7*kit);
    push(len,k,k==='nap'?sleepSpot(t):k==='rest'?petLikesPickSpot(p,r,'rest',t):'floor');last=k==='nap'?'nap':k;
  }
  while(t<bed-0.05){                              // a livelier evening
    const k=last==='active'?'rest':'active',len=k==='active'?0.4+r()*0.6:0.2+r()*0.35;
    push(Math.min(len,bed-t),k,k==='rest'?petLikesPickSpot(p,r,'rest',t):'floor');last=k;
  }
  if(t<24)push(24-t,'sleep',night);
  return {blocks:B,wake:wake,bed:bed};
}
function petSchedule(p,day){const rt=PET_RT;rt.sched=rt.sched||{};const key=p.born+'|'+day+'|'+p.trait;if(!rt.sched[key]){const k=Object.keys(rt.sched);if(k.length>6)delete rt.sched[k[0]];rt.sched[key]=petBuildDay(p,day)}return rt.sched[key]}
function petBlockNow(p){const S=petSchedule(p,petDayKey()),h=petHour();return S.blocks.find(function(b){return h>=b.a&&h<b.b})||S.blocks[S.blocks.length-1]}
/* the small choices inside a block come from a generator reseeded every minute from the clock */
function petMinuteRng(rt,p){const m=Math.floor(petNow()/60000);if(rt.rngMin!==m){rt.rngMin=m;rt.rng=petRng(petHash(p.born+'|'+m+'|moment'))}return rt.rng}

function petRand(a,b){return a+Math.random()*(b-a)}
function petPick(arr){return arr[Math.floor(Math.random()*arr.length)]}
function petAssetsJson(){return JSON.parse(document.getElementById('petAssets').textContent)}
function petLoadImg(src){return new Promise(function(res){const i=new Image();i.onload=function(){res(i)};i.onerror=function(){res(i)};i.src=src})}
async function petInit(){
  if(PET_RT&&PET_RT.ready)return PET_RT;
  if(PET_RT&&PET_RT.loading)return PET_RT.loading;
  const rt={ready:false,sheets:{},t:0,parts:[],bubble:null,hold:null,ball:{on:false,x:0,vx:0,t:0},bowl:{food:false},gift:null,peekY:60,greetedAt:0,lastMood:''};
  PET_RT=rt;
  rt.loading=(async function(){
    const A=petAssetsJson();rt.A=A;
    rt.bg=await petLoadImg(A.room.bg);rt.items=await petLoadImg(A.room.items);rt.living=A.room.living?await petLoadImg(A.room.living):null;rt.kitchen=A.room.kitchen?await petLoadImg(A.room.kitchen):null;
    for(const coat of Object.keys(A.coats)){
      const img=await petLoadImg(A.coats[coat]);const cv=document.createElement('canvas');cv.width=img.width;cv.height=img.height;
      const cx=cv.getContext('2d',{willReadFrequently:true});cx.drawImage(img,0,0);const id=cx.getImageData(0,0,cv.width,cv.height),d=id.data,pal=A.pal[coat];
      for(let i=0;i<d.length;i+=4){const g=d[i];if(g===0){d[i+3]=0;continue}const c=pal[String(g)];if(c){d[i]=c[0];d[i+1]=c[1];d[i+2]=c[2];d[i+3]=255}else d[i+3]=0}
      cx.putImageData(id,0,0);rt.sheets[coat]=cv}
    rt.dsheets={};if(A.depth)for(const coat of Object.keys(A.depth.coats)){                 /* approach / retreat / sniff frames */
      const img=await petLoadImg(A.depth.coats[coat]);const cv=document.createElement('canvas');cv.width=img.width;cv.height=img.height;
      const cx=cv.getContext('2d',{willReadFrequently:true});cx.drawImage(img,0,0);const id=cx.getImageData(0,0,cv.width,cv.height),d=id.data,pal=A.pal[coat];
      for(let i=0;i<d.length;i+=4){const g=d[i];if(g===0){d[i+3]=0;continue}const c=pal[String(g)];if(c){d[i]=c[0];d[i+1]=c[1];d[i+2]=c[2];d[i+3]=255}else d[i+3]=0}
      cx.putImageData(id,0,0);rt.dsheets[coat]=cv}
    petDepthPropImgs(rt);for(const k of Object.keys((A.depth&&A.depth.toys)||{}))petPropImg(rt,k);
    rt.off=document.createElement('canvas');rt.off.width=PET_HW*PET_OS;rt.off.height=144*PET_OS;rt.octx=rt.off.getContext('2d');rt.octx.imageSmoothingEnabled=false;rt.edge=document.createElement('canvas');rt.edge.width=PET_HW;rt.edge.height=144;rt.ectx=rt.edge.getContext('2d');rt.ectx.imageSmoothingEnabled=false;
    {const p0=petData();if(p0&&p0.alive)await petLoadStage(rt,petGrowIdx(p0))}
    rt.c={x:92,y:PET_SURF.floor.y,z:PET_DEPTH.ART_Z,dir:1,surf:'floor',anim:'idle_sit',t:0,plan:[],step:null,hop:null};
    try{rt.lastSeen=+localStorage.getItem('lm_pet_seen')||0}catch(e){}
    rt.ready=true;rt.loading=null;petPlaceForNow(rt);rt.cam={cx:rt.c.x};setTimeout(function(){petSfxLoad(rt)},600);return rt})();
  return rt.loading;
}
/* ---------- behaviour ---------- */
function petAnimName(rt,a){const p=petData();if(!p)return a;const weak=petStage(p)>=3;if(weak){return PET_WEAK[a]||(a==='run'?'walk_w':'idle_sit_w')}return a}
function petRoute(c,surf,x){
  if((surf==='lsill'||surf==='ksill')&&c.surf!==surf){const via=surf==='lsill'?'couch':'counter',sf=PET_SURF[surf],vf=PET_SURF[via];x=Math.max(sf.x0,Math.min(sf.x1,x));
    const q=c.surf===via?[]:petRoute(c,via,Math.max(vf.x0,Math.min(vf.x1,x)));q.push({k:'hop',s:surf,x:x});return q}
  if(c.surf===surf&&surf!=='floor'){const sf=PET_SURF[surf];return surf==='bed'?[]:[{k:'walk',x:sf&&sf.x0!=null?Math.max(sf.x0,Math.min(sf.x1,x)):x}]}
  const s=[];let cur=c.surf;const toFloor=function(fx){if(cur!=='floor'){if(cur==='couch'||cur==='counter')fx=c.x+((fx>=c.x)?16:-16);else if(petIsDepthSurf(cur)){const ps=PET_SURF[cur];fx=fx>=c.x?ps.x1+ps.half*0.6+6:ps.x0-ps.half*0.6-6}s.push({k:'hop',s:'floor',x:fx});cur='floor'}};
  if(surf==='floor'){x=petClampHouse(x);toFloor(x);s.push({k:'walk',x:x})}
  else if(petIsDepthSurf(surf)){const sf=PET_SURF[surf];x=Math.max(sf.x0,Math.min(sf.x1,x));const side=(c.x<=x?-1:1),bx=surf==='box',ax=petClampHouse((side<0?sf.x0:sf.x1)+side*(bx?sf.half*0.2+2:sf.half*0.6+8));toFloor(ax);s.push({k:'walk',x:ax,z:bx?sf.z+PET_BOX_FRONT_Z:sf.z});s.push({k:'hop',s:surf,x:x})}   /* v79.2: the box is jumped into from just in front of it */
  else if(surf==='couch'||surf==='counter'){const sf=PET_SURF[surf];x=Math.max(sf.x0,Math.min(sf.x1,x));const ax=x+(surf==='couch'?20:-18);toFloor(ax);s.push({k:'walk',x:ax});s.push({k:'hop',s:surf,x:x})}
  else if(surf==='bed'){toFloor(50);s.push({k:'walk',x:50});s.push({k:'hop',s:'bed',x:36})}
  else if(surf==='sill'){toFloor(54);s.push({k:'walk',x:Math.max(38,Math.min(70,x))});s.push({k:'hop',s:'sill',x:x})}
  else if(surf==='mid'){if(cur==='top'){s.push({k:'hop',s:'mid',x:x})}else{toFloor(140);s.push({k:'walk',x:138});s.push({k:'hop',s:'mid',x:x})}}
  else if(surf==='top'){if(cur!=='mid'&&cur!=='top'){toFloor(140);s.push({k:'walk',x:138});s.push({k:'hop',s:'mid',x:150});cur='mid'}s.push({k:'hop',s:'top',x:x})}
  return s;
}
function petAct(a,d,o){return Object.assign({k:'act',a:a,d:d},o||{})}
function petAtSpot(c,spot){
  if(PET_SPOT_X[spot])return PET_SPOT_X[spot].at(c);
  if(spot==='bed')return c.surf==='bed';if(spot==='sill')return c.surf==='sill';if(spot==='tree')return c.surf==='mid'||c.surf==='top';
  if(spot==='couch')return c.surf==='couch';if(spot==='counter')return c.surf==='counter';
  if(spot==='living'||spot==='kitchen')return c.surf==='floor'&&petRoomOf(c.x)===spot;
  if(spot==='rug')return c.surf==='floor'&&c.x>=86&&c.x<=128;return c.surf==='floor'}
function petSpotPlan(rt,spot,r){
  const c=rt.c;if(petAtSpot(c,spot))return [];
  if(PET_SPOT_X[spot])return PET_SPOT_X[spot].plan(rt,r,c);
  if(spot==='bed')return petRoute(c,'bed',36);
  if(spot==='sill')return petRoute(c,'sill',40+Math.round(r()*26));
  if(spot==='tree'){const q=petRoute(c,'mid',148+Math.round(r()*10));if(r()<0.5){q.push.apply(q,petRoute({surf:'mid',x:154},'top',151+Math.round(r()*7)))}return q}
  if(spot==='rug')return petRoute(c,'floor',92+Math.round(r()*30));
  if(spot==='couch')return petRoute(c,'couch',-130+Math.round(r()*64));
  if(spot==='counter')return petRoute(c,'counter',290+Math.round(r()*46));
  if(spot==='living')return petRoute(c,'floor',-146+Math.round(r()*110));
  if(spot==='kitchen')return petRoute(c,'floor',214+Math.round(r()*120));
  return petRoute(c,'floor',44+Math.round(r()*100));
}
function petThink(rt){
  const p=petData(),c=rt.c,stage=petStage(p),L=petLevel(p),tr=p.trait,h=petHour(),kit=Math.max(0,1-petGrowth(p)),streak=(petNow()-(rt.lastPlayAt||0))<45000?1+2.2*kit:1;
  let blk=petBlockNow(p);const r=petMinuteRng(rt,p);
  if((blk.k==='sleep'||blk.k==='nap')&&rt.awakeUntil&&petNow()<rt.awakeUntil)blk={a:blk.a,b:blk.b,k:'rest',spot:'rug'};
  rt.blk=blk;
  const R=function(a,b){return a+r()*(b-a)};
  const lazy=tr==='lazy'?1.6:1,shy=tr==='shy'?1.7:1,play=tr==='playful'?1.8:1,cur=tr==='curious'?1.7:1;
  const O=[];const add=function(n,w,f){if(w>0)O.push([n,w,f])};
  const restByBowl=function(){const q=petRoute(c,'floor',PET_EATX);q.push({k:'face',dir:PET_EATDIR});q.push(petAct('loaf',R(18,34),{bubble:'bubble'}));return q};
  if(stage>=4){add('rest',1,restByBowl)}
  else if(stage===3){add('rest',8,restByBowl);add('idle',2,function(){return [petAct('idle_sit',R(8,16),{bubble:'bubble'})]})}
  else if(blk.k==='sleep'||blk.k==='nap'){
    // a real sleep: go to this block's sleeping place and stay until the block ends
    add('sleep',1,function(){const q=petSpotPlan(rt,blk.spot,r);if(!rt.wasSleeping&&((L>=3&&blk.spot==='bed')||(L>=2&&petLikes(p).favSpot===blk.spot)))q.push(petAct('knead',R(2.5,4)));q.push(petAct('sleep',Math.min(900,Math.max(8,(blk.b-h)*3600)),{until:blk.b}));return q});
  }else if(blk.k==='rest'){
    const onSpot=rt.restBlock===blk.a;
    if(!onSpot){rt.restBlock=blk.a;add('goto',1,function(){const q=petSpotPlan(rt,blk.spot,r);q.push(petAct(petSpotRestAct(blk.spot),R(70,150)));return q})}
    else{
      add('loaf',5,function(){return [petAct('loaf',R(80,200))]});
      add('idle',3,function(){return [petAct('idle_sit',R(45,100))]});
      add('groom',2,function(){return [petAct('groom',R(10,16)),petAct('loaf',R(50,110))]});
      if(/sill$/.test(c.surf))add('window',4,function(){return [petAct('lookup',R(70,160))]});
      if(L>=3)add('blink',1.2,function(){return [petAct('slowblink',R(3,5)),petAct('loaf',R(60,140))]});
      if(L<=1&&kit===0)add('scratch',1.1,function(){const q=petRoute(c,'floor',R(62,86));q.push({k:'face',dir:1});q.push(petAct('scratch_in',0.43));q.push(petAct('scratch',R(5,8)));q.push(petAct('scratch_out',0.43));q.push(petAct('loaf',R(40,80)));return q});
      add('shift',0.4,function(){const q=petDepthFloorRoute(c,c.x+R(-30,30),r,c.z-15,c.z+15);q.push(petAct('loaf',R(70,150)));return q});
    }
  }else{
    // an active spell: still unhurried, with a long pause after everything
    const pause=function(){return petAct('idle_sit',R(18,42)*(1-0.55*kit))};
    if(stage>=1)add('bowl',stage>=2?7:1.5,function(){return petBowlWait(rt)});
    add('idle',4*(1-0.6*kit),function(){return [petAct('idle_sit',R(30,65)*(1-0.5*kit))]});
    add('groom',2.2*(1-0.4*kit),function(){return [petAct('groom',R(10,16)),pause()]});
    add('wander',2.2*(1+0.8*kit),function(){const q=petDepthFloorRoute(c,c.x+R(-60,60),r,8,72);q.push(pause());return q});
    add('explore',2.4*cur*(1+0.5*kit),function(){
      const here=petRoomOf(c.x),opts=['living','bedroom','kitchen'].filter(function(k){return k!==here}),to=opts[Math.floor(r()*opts.length)];let q;
      if(to==='living'){if(r()<0.45){q=petRoute(c,'couch',R(-130,-66));q.push(petAct(r()<0.5?'loaf':'idle_sit',R(30,70)))}else{q=petDepthFloorRoute(c,R(-150,-30),r);q.push(petAct(r()<0.4?'lookup':'idle_sit',R(14,30)))}}
      else if(to==='kitchen'){if(r()<0.4&&L>=1){q=petRoute(c,'counter',R(290,336));q.push(petAct(r()<0.5?'loaf':'idle_sit',R(20,50)))}else{q=petDepthFloorRoute(c,R(214,334),r);q.push({k:'face',dir:r()<0.5?1:-1});q.push(petAct(r()<0.5?'lookup':'idle_sit',R(12,26)))}}
      else q=petDepthFloorRoute(c,R(50,140),r);
      q.push(pause());return q});
    add('window',1.3*cur,function(){const x=40+Math.round(R(0,26)),q=petRoute(c,'sill',x);q.push(petAct('lookup',R(40,90)));q.push.apply(q,petRoute({surf:'sill',x:x},'floor',R(60,100)));q.push(pause());return q});
    if(rt.playedBlock!==blk.a)add('play',1.6*play*(1+1.2*kit)*streak,function(){rt.playedBlock=blk.a;return petPlan(rt,'ball').concat([pause()])});
    add('stretch',0.8,function(){return [petAct('stretch',3.2),pause()]});
    petDepthOptions(rt,add,R,r,pause,{cur:cur,shy:shy,play:play,kit:kit,L:L,stage:stage});
    petLikesOptions(rt,add,R,r,pause,{cur:cur,shy:shy,play:play,kit:kit,L:L,stage:stage});
    const trust=(kit>0.3||L>=2)?1:L===1?0.25:0,toys=(L>=1||kit>0.2)?1:0;
    if(trust&&petNow()-(rt.rollAt||0)>90000)add('roll',1.6*trust*(1+1.8*kit)*streak,function(){rt.rollAt=petNow();return petPlayPlan(rt,'roll')});
    if(toys)add('kick',1.1*play*(1+1.6*kit)*streak,function(){return petPlayPlan(rt,'kick')});
    if(toys&&!rt.dangle)add('bat',1.0*play*(1+1.8*kit)*streak,function(){return petPlayPlan(rt,'bat')});
    if(L<=2&&kit===0)add('scratch',[3.6,2.6,1.0][L],function(){const q=petRoute(c,'floor',R(62,86));q.push({k:'face',dir:1});q.push(petAct('scratch_in',0.43));q.push(petAct('scratch',R(5,9)));q.push(petAct('scratch_out',0.43));q.push(petAct('stretch',3.2));q.push(pause());return q});
    if((h>=17||kit>0.25)&&rt.zoomBlock!==blk.a)add('zoomies',1.4*play*(1+1.5*kit)*streak,function(){rt.zoomBlock=blk.a;const q=petRoute(c,'floor',PET_HMIN+8);q.forEach(function(z){if(z.k==='walk')z.run=1});q.push({k:'walk',x:PET_HMAX-8,run:1});q.push({k:'walk',x:60,run:1});q.push(pause());return q});
    add('tree',0.9*shy*cur,function(){const q=petRoute(c,'mid',R(148,158));let end='mid';if(r()<0.6){q.push.apply(q,petRoute({surf:'mid',x:154},'top',R(151,158)));end='top';q.push(petAct(r()<0.5?'loaf':'idle_sit',R(40,90)))}else q.push(petAct('idle_sit',R(30,60)));q.push.apply(q,petRoute({surf:end,x:154},'floor',R(60,120)));q.push(pause());return q});
    if(L>=2)add('rub',0.8,function(){const q=petRoute(c,'floor',126);q.push(petAct('rub',3.2,L>=3?{snd:'purreow'}:{}));q.push(pause());return q});
    if(L>=5&&!rt.gift&&p.lastGiftDay!==petDay())add('gift',3,function(){const q=petRoute(c,'floor',72);q.push({k:'face',dir:1});q.push(petAct('meow',2.2,{snd:'purreow'}));q.push({k:'fn',f:function(){rt.gift={k:petLikesGiftItem(r),x:92};p.lastGiftDay=petDay();save();petSpawn(rt,'sparkle',92,PET_SURF.floor.y-14,0,-10,1.2)}});q.push(petAct('slowblink',2.4));return q});
  }
  petBrainArbiter(rt,O,add,{blk:blk,stage:stage,L:L,kit:kit,r:r,R:R,h:h,cur:cur,shy:shy,play:play});   /* v78: mood, attachment, memory and quirks weigh in */
  let tot=0;O.forEach(function(o){tot+=o[1]});let x=r()*tot,pick=O[0];for(const o of O){if((x-=o[1])<=0){pick=o;break}}
  pick=petBrainPicked(rt,pick,O)||pick;
  rt.lastThought=pick[0];rt.thoughts=(rt.thoughts||0)+1;if(/^(play|zoomies|roll|kick|bat)$/.test(pick[0]))rt.lastPlayAt=petNow();
  if(blk.k!=='sleep'&&blk.k!=='nap'&&stage<2&&(pick[0]==='idle'||pick[0]==='window'||pick[0]==='wander'||pick[0]==='groom'||pick[0]==='loaf')&&petNow()-(rt.ambAt||0)>45000&&r()<[0,0.05,0.12,0.2,0.28,0.35][L]){rt.ambAt=petNow();setTimeout(function(){petWithRt(rt,function(){petPlay(rt,'idle',0.8)})},500+Math.random()*1500)}
  c.plan=pick[2]();
  // after a night or nap, wake up properly
  if(rt.wasSleeping&&blk.k!=='sleep'&&blk.k!=='nap'){c.plan.unshift(petAct('stretch',3.2));rt.wasSleeping=false}
  if(blk.k==='sleep'||blk.k==='nap')rt.wasSleeping=true;
}
function petB