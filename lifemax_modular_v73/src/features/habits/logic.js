/* Habits 1.0.0: habit stacking, built literally (docs/specs/HABITS_TAB_BRIEF.md).
   A stack is something you already do ("After coffee"); a habit is a block laid on it, in the order you do them. One button lays the
   next block. A block's material shows how set the habit is (glass, clay, stone, gold, by total repetitions: a missed day never takes
   any away), its height is the habit's size (two minutes, normal, stretch), a crack is one missed day and a wobble is two. A habit
   you are breaking is a tower that comes down a floor for each clean day.

   Saved under the optional key state.habits (v 1), written by writeSave() only, never read into existence:
     { v, who, n, stacks:[{id,cue,treat}], items:[{id,stack,kind:'build'|'fade',names:[2 min, normal, stretch],size:0..2,born,pair,
       scar,floors}], log:{'YYYY-MM-DD':{itemId:size+1}}, rough:'YYYY-MM-DD'|'', claimed:{'YYYY-MM-DD':[stackId]} }
   No XP, Sparks or streak are touched here. Dates are the app's local days (getToday, addDays). */
const HabitsFeature=(function(){
 const LINE=66,HW=80,D=28,HS=[40,62,88],ANG=Math.atan(D/HW)*180/Math.PI,MAXW=12;
 const NONE=Object.freeze({v:1,who:'',n:0,stacks:[],items:[],log:{},rough:'',claimed:{}});
 const MAT={glass:['rgba(140,168,255,.26)','rgba(140,168,255,.16)','rgba(140,168,255,.10)','#a9bcff'],
  clay:['#dd9a72','#b46d48','#935438','#3a1d10'],stone:['#a9bbdc','#778bb4','#5d719a','#1c2640'],
  gold:['#f8dc85','#dcb146','#b98e2d','#4a3708'],fade:['#93506b','#70364f','#582a3e','#2a0f1b'],
  slab:['#6f7fa6','#4c5b82','#3d4a6d','#101728'],treat:['#f8dc85','#dcb146','#b98e2d','#4a3708'],
  lock:['rgba(248,220,133,.10)','rgba(248,220,133,.06)','rgba(248,220,133,.04)','#f8dc85']};
 const MATNAME={glass:'Glass · brand new',clay:'Clay · taking shape',stone:'Stone · nearly automatic',gold:'Gold · runs itself',fade:'Coming down'};
 const CAT=['X...X...','XX.XX...','XXXXX...','XoXoX..X','XXXXX..X','.XXXXXX.','.XXXXXX.','.XXXXXX.'];
 let root=null,host=null,focus=0,sel=null,city=false,weeks=0,combo=0,laid=0,sheet=null;

 /* ---------- the save ---------- */
 const read=()=>{const h=typeof state!=='undefined'&&state&&state.habits;return h&&h.v===1?h:NONE};
 function commit(change){
  const before=JSON.stringify(state.habits||null);
  if(!state.habits||state.habits.v!==1)state.habits={v:1,who:'',n:0,stacks:[],items:[],log:{},rough:'',claimed:{}};
  const out=change(state.habits);
  if(out===false||!writeSave()){
   if(before==='null')delete state.habits;else state.habits=JSON.parse(before);
   if(out!==false)toast(unsavedMessage());
   return null;
  }
  return out===undefined?true:out;
 }
 const clean=(s,n)=>String(s==null?'':s).replace(/\s+/g,' ').trim().slice(0,n);

 /* ---------- what a day looks like (pure reads) ---------- */
 const matAt=r=>r>=LINE?'gold':r>=30?'stone':r>=10?'clay':'glass';
 const doneOn=(h,id,d)=>!!(h.log[d]&&h.log[d][id]);
 function repsAt(h,id,d){let n=0;for(const k in h.log)if(k<=d&&h.log[k][id])n++;return n}
 /* days in a row before d, back to the habit's first day, with no block laid */
 function missed(h,it,d){let n=0,k=addDays(d,-1);while(k>=it.born&&!doneOn(h,it.id,k)&&n<30){n++;k=addDays(k,-1)}return n}
 function block(h,it,d,live){
  const reps=repsAt(h,it.id,d),done=live&&doneOn(h,it.id,d),fade=it.kind==='fade';
  const size=fade?1:done?h.log[d][it.id]-1:(live&&h.rough===d?0:it.size);
  const left=fade?Math.max(0,(it.floors||14)-reps):0;
  return {id:it.id,kind:it.kind,name:it.names[size]||it.names[1],names:it.names,size,set:it.size,reps,done,
   miss:live&&!fade&&!done?missed(h,it,d):0,scar:live&&!!it.scar,mat:fade?'fade':matAt(reps),left,cleared:fade&&left===0,pair:it.pair||''};
 }
 function model(day){
  const h=read(),d=day||getToday(),live=!day||day===getToday();
  const stacks=h.stacks.map(s=>{
   const blocks=h.items.filter(it=>it.stack===s.id&&it.born<=d).map(it=>block(h,it,d,live));
   return {id:s.id,cue:s.cue,treat:s.treat||'',claimed:!!(h.claimed[d]&&h.claimed[d].indexOf(s.id)>=0),blocks,
    next:live?blocks.find(b=>!b.done&&!b.cleared)||null:null};
  });
  return {day:d,live,who:h.who,rough:live&&h.rough===d,laid:h.log[d]?Object.keys(h.log[d]).length:0,stacks};
 }

 /* ---------- commands (each answers whether it was stored) ---------- */
 const api={
  model,
  addStack(cue){cue=clean(cue,28);if(!cue)return null;return commit(h=>{const id='s'+(++h.n);h.stacks.push({id,cue,treat:''});return id})},
  editStack(id,p){return commit(h=>{const s=h.stacks.find(x=>x.id===id);if(!s)return false;if(p.cue!==undefined&&clean(p.cue,28))s.cue=clean(p.cue,28);if(p.treat!==undefined)s.treat=clean(p.treat,24)})},
  removeStack(id){return commit(h=>{if(h.items.some(it=>it.stack===id))return false;h.stacks=h.stacks.filter(s=>s.id!==id)})},
  addHabit(stack,p){
   const name=clean(p&&p.name,32);if(!name)return null;
   return commit(h=>{
    if(!h.stacks.some(s=>s.id===stack))return false;
    const id='h'+(++h.n),fade=p.kind==='fade',small=clean(p.small,32)||name;
    h.items.push({id,stack,kind:fade?'fade':'build',names:[small,name,clean(p.big,32)||name],size:fade?1:0,born:getToday(),pair:'',scar:false,floors:fade?Math.max(3,Math.min(60,p.floors|0||14)):0});
    return id;
   });
  },
  editHabit(id,p){return commit(h=>{
   const it=h.items.find(x=>x.id===id);if(!it)return false;
   if(p.names){const n=p.names.map(x=>clean(x,32));if(!n[1])return false;it.names=[n[0]||n[1],n[1],n[2]||n[1]]}
   if(p.size!==undefined&&it.kind!=='fade')it.size=Math.max(0,Math.min(2,p.size|0));
   if(p.pair!==undefined)it.pair=clean(p.pair,28);
   if(p.stack!==undefined&&h.stacks.some(s=>s.id===p.stack)&&p.stack!==it.stack){it.stack=p.stack;h.items.splice(h.items.indexOf(it),1);h.items.push(it)}
  })},
  retire(id){return commit(h=>{if(!h.items.some(x=>x.id===id))return false;h.items=h.items.filter(x=>x.id!==id);for(const d in h.log)delete h.log[d][id]})},
  /* lay today's block (a clean day, for a habit you are breaking); coming back after a missed day leaves a gold seam */
  lay(id){return commit(h=>{
   const d=getToday(),it=h.items.find(x=>x.id===id);if(!it||doneOn(h,id,d))return false;
   if(it.kind!=='fade'&&missed(h,it,d)>=1)it.scar=true;
   (h.log[d]=h.log[d]||{})[id]=it.kind==='fade'?2:(h.rough===d?0:it.size)+1;
  })},
  undo(id){return commit(h=>{const d=getToday();if(!doneOn(h,id,d))return false;delete h.log[d][id];if(!Object.keys(h.log[d]).length)delete h.log[d]})},
  claim(id){return commit(h=>{const d=getToday(),s=model().stacks.find(x=>x.id===id);if(!s||!s.treat||s.next||s.claimed||!s.blocks.length)return false;h.claimed={[d]:(h.claimed[d]||[]).concat(id)}})},
  setRough(on){return commit(h=>{h.rough=on?getToday():''})},
  setWho(t){return commit(h=>{h.who=clean(t,40)})}
 };

 /* ---------- drawing ---------- */
 const still=()=>typeof lmaMotionOff==='function'&&lmaMotionOff();
 const fit=(s,n)=>s.length>n?s.slice(0,n-1).trimEnd()+'…':s;
 function prism(y,h,m,cls,attrs,k){
  const c=MAT[m],w=HW*(k||1),d=D*(k||1),dash=m==='lock'?' stroke="'+c[3]+'" stroke-dasharray="4 4"':'';
  return '<g class="hb-blk '+cls+'" '+attrs+'>'+
   '<polygon class="hb-face" points="'+(-w)+','+(y+d)+' 0,'+(y+2*d)+' 0,'+(y+2*d+h)+' '+(-w)+','+(y+d+h)+'" fill="'+c[1]+'"'+dash+'/>'+
   '<polygon class="hb-face" points="'+w+','+(y+d)+' 0,'+(y+2*d)+' 0,'+(y+2*d+h)+' '+w+','+(y+d+h)+'" fill="'+c[2]+'"'+dash+'/>'+
   '<polygon class="hb-face" points="0,'+y+' '+w+','+(y+d)+' 0,'+(y+2*d)+' '+(-w)+','+(y+d)+'" fill="'+c[0]+'"'+dash+'/>'+
   (m==='lock'?'':'<polyline points="'+(-w)+','+(y+d)+' 0,'+y+' '+w+','+(y+d)+'" fill="none" stroke="rgba(255,255,255,.38)" stroke-width="1"/>');
 }
 const sign=(x0,y0,a,y,t,lt)=>'<text class="hb-sign'+(lt?' lt':'')+'" text-anchor="middle" transform="translate('+x0+','+y0+') skewY('+a+')" x="'+HW/2+'" y="'+y+'">'+esc(t)+'</text>';
 function cat(cx,by){let s='';const p=3.4;CAT.forEach((row,r)=>{for(let c=0;c<row.length;c++)if(row[c]!=='.')s+='<rect x="'+(cx-14+c*p)+'" y="'+(by-CAT.length*p+r*p)+'" width="'+(p+.2)+'" height="'+(p+.2)+'" fill="'+(row[c]==='o'?'#f6d679':'#05070d')+'"/>'});return '<g class="hb-cat">'+s+'</g>'}
 function tree(y){return '<g class="hb-tree"><rect x="-3" y="'+(y+D-26)+'" width="6" height="26" fill="#6b4a2f"/><circle cx="0" cy="'+(y+D-34)+'" r="17" fill="#3f9d6b"/><circle cx="-11" cy="'+(y+D-26)+'" r="11" fill="#348a5c"/><circle cx="11" cy="'+(y+D-27)+'" r="12" fill="#46ad77"/></g>'}
 /* one tower, drawn up from its foundation at (0,0); answers the markup and how tall it is */
 function tower(s,on,live){
  let y=-14-2*D,out='',roof=null;
  out+='<ellipse cx="0" cy="'+(-D)+'" rx="'+(HW*1.25)+'" ry="'+(D*1.25)+'" fill="url(#hbShade)"/>';
  out+=prism(y,14,'slab',(on&&sel===s.id?'sel':''),'data-stack="'+s.id+'" role="button" aria-label="'+esc(s.cue)+', edit this foundation"')+sign(-HW,y+D,ANG,11,fit(s.cue.toUpperCase(),20))+'</g>';
  for(const b of s.blocks){
   if(b.cleared){out+='<g class="hb-blk" data-b="'+b.id+'">'+tree(y)+'</g>';continue}
   const fade=b.kind==='fade',floors=fade?Math.min(b.left,MAXW):1,h=fade?26:HS[b.size],lt=b.mat==='glass'||fade;
   for(let f=0;f<floors;f++){
    y-=h;const top=f===floors-1;
    const cls=[on&&sel===b.id?'sel':'',on&&live&&s.next===b&&top?'next':'',laid===b.id?'drop':'',b.miss>=2?'wob':'',live&&!b.done&&!fade?'todo':''].join(' ');
    out+=prism(y,h,b.mat,cls,'data-b="'+b.id+'"'+(top?' role="button" tabindex="0" aria-label="'+esc(b.name)+', '+(fade?b.reps+' days clean':b.reps+' reps')+'"':''));
    if(top){
     out+=sign(-HW,y+D,ANG,h/2+4,fit(b.name,15),lt)+sign(0,y+2*D,-ANG,h/2+4,fade?b.reps+(b.reps===1?' day clean':' days clean'):b.reps+(b.reps===1?' rep':' reps'),lt);
     const seam=(-HW*.7)+','+(y+D+8)+' '+(-HW*.55)+','+(y+D+h*.45)+' '+(-HW*.66)+','+(y+D+h*.6)+' '+(-HW*.5)+','+(y+D+h+4);
     if(b.miss>=1)out+='<polyline class="hb-crack" points="'+seam+'" stroke="'+MAT[b.mat][3]+'"/>';
     else if(b.scar)out+='<polyline class="hb-seam" points="'+seam+'"/>';
     if(laid===b.id&&!still())for(let i=0;i<8;i++)out+='<circle class="hb-dust" cx="'+((i-3.5)*HW/4)+'" cy="'+(y+2*D+h-2)+'" r="'+(1.4+i%2)+'" style="--dx:'+((i-3.5)*9)+'px"/>';
    }
    out+='</g>';if(b.done||!live||fade)roof=y;
   }
  }
  if(roof===null)roof=y;
  if(s.treat&&live&&s.blocks.length){
   const open=!s.next&&!s.claimed;
   out+=prism(y-26,22,open?'treat':'lock','hb-treat'+(open?' lit':''),'data-treat="'+s.id+'"',.55)+'<text class="hb-sign'+(open?'':' gold')+'" text-anchor="middle" x="0" y="'+(y-26+D*.55+4)+'">'+esc(s.claimed?'Enjoyed':fit(s.treat,14))+'</text></g>';
   y-=30;
  }
  if(on&&live&&!city){out+='<g class="hb-blk hb-ghost" data-new="'+s.id+'" role="button" tabindex="0" aria-label="Stack a habit here"><polygon points="0,'+(y-30)+' '+(HW*.6)+','+(y-30+D*.6)+' 0,'+(y-30+D*1.2)+' '+(-HW*.6)+','+(y-30+D*.6)+'"/><text class="hb-sign" text-anchor="middle" x="0" y="'+(y-30+D*.6+4)+'">+ stack a habit</text></g>';y-=34}
  if(on&&live&&typeof state!=='undefined'&&state.pet)out+=cat(HW*.45,roof+D*1.45);
  return {svg:out,height:-y+10};
 }
 function sky(){
  const n=new Date(),hr=n.getHours()+n.getMinutes()/60;
  const night=hr<5.5||hr>=20.5,edge=!night&&(hr<7.5||hr>=18);
  const c=night?['#070b1a','#0b1228','#111b3d']:edge?['#1d2350','#6b4b7c','#d98a6c']:['#2f66b8','#6fa5dd','#a9d0ee'];
  let st='';
  if(night||edge){let r=7;for(let i=0;i<46;i++){r=(r*73+19)%997;const x=r%400;r=(r*73+19)%997;st+='<circle cx="'+x+'" cy="'+(r%210)+'" r="'+(i%7?0.7:1.2)+'" fill="#d5ddff" opacity="'+((night?0.25:0.1)+(i%5)*0.1)+'"/>'}}
  const t=night?((hr+3.5)%24)/9:(hr-5.5)/15,x=36+Math.max(0,Math.min(1,t))*250,y=104-Math.sin(Math.max(0,Math.min(1,t))*Math.PI)*46;
  st+='<circle cx="'+x+'" cy="'+y+'" r="'+(night?9:13)+'" fill="'+(night?'#e9edff':edge?'#ffd9a8':'#fff3c4')+'" opacity=".9"/>';
  return '<defs><linearGradient id="hbSky" x1="0" x2="0" y1="0" y2="1"><stop stop-color="'+c[0]+'"/><stop offset=".62" stop-color="'+c[1]+'"/><stop offset="1" stop-color="'+c[2]+'"/></linearGradient>'+
   '<radialGradient id="hbShade"><stop stop-color="rgba(0,0,0,.42)"/><stop offset="1" stop-color="rgba(0,0,0,0)"/></radialGradient>'+
   '<linearGradient id="hbGround" x1="0" x2="0" y1="0" y2="1"><stop stop-color="rgba(6,9,20,0)"/><stop offset="1" stop-color="rgba(6,9,20,.78)"/></linearGradient></defs>'+
   '<rect width="400" height="400" fill="url(#hbSky)"/>'+st+'<rect y="250" width="400" height="150" fill="url(#hbGround)"/>';
 }
 function scene(m){
  const n=m.stacks.length;let out=sky();
  if(!n)out+='<g class="hb-blk hb-ghost" data-act="newstack" role="button" tabindex="0" aria-label="Lay your first foundation" transform="translate(200,330)"><polygon points="0,'+(-2*D)+' '+HW+','+(-D)+' 0,0 '+(-HW)+','+(-D)+'"/><text class="hb-sign" text-anchor="middle" x="0" y="'+(-D+4)+'">+ lay a foundation</text></g>';
  m.stacks.forEach((s,i)=>{
   const o=i-focus,on=!city&&o===0,t=tower(s,on,m.live);
   let tf,op=1;
   if(city){const k=Math.min(.7,340/(n*172),300/t.height),gap=n>1?(400-2*HW*k-28)/(n-1):0;tf='translate('+(n>1?14+HW*k+i*gap:200)+'px,374px) scale('+k+')'}
   else if(on)tf='translate(200px,376px) scale('+Math.min(1.25,292/t.height)+')';
   else{tf='translate('+(200+o*152)+'px,318px) scale(.56)';op=Math.abs(o)===1?.38:0}
   out+='<g class="hb-tower" data-i="'+i+'" style="transform:'+tf+';opacity:'+op+(op?'':';pointer-events:none')+'">'+t.svg+'</g>';
  });
  return out;
 }
 function strikeHtml(m){
  const s=m.stacks[focus];
  if(!m.stacks.length)return ['hb-strike','newstack','Start here','Lay your first foundation','+'];
  if(!s.blocks.length)return ['hb-strike','new','Empty foundation','Stack a habit on '+s.cue.toLowerCase(),'+'];
  const nx=s.next;
  if(nx&&nx.kind==='fade')return ['hb-strike clean','lay','Stayed clean today','Knock a floor off: '+nx.name,'↓'];
  if(nx)return ['hb-strike','lay','Lay the next block'+(combo?' · combo ×'+(combo+1):''),nx.name,'▲'];
  if(s.treat&&!s.claimed)return ['hb-strike claim','claim','Stack complete · rooftop unlocked','Claim: '+s.treat,'★'];
  const more=m.stacks.findIndex((x,i)=>i!==focus&&x.next);
  return more<0?['hb-strike rest','','Skyline complete','Every block is laid today','']:['hb-strike rest','goto','This stack is done','Go to '+m.stacks[more].cue.toLowerCase(),'→'];
 }
 function tuneHtml(m){
  const s=m.stacks[focus],b=s&&s.blocks.find(x=>x.id===sel);
  if(!b)return '<span class="hb-tag">'+(m.stacks.length?'Tap a block to tune it · tap a foundation to rename it':'A foundation is something you already do every day')+'</span>';
  const fade=b.kind==='fade';
  const say=b.cleared?'This one is gone. A tree stands where it was.':b.done?(b.scar?'You missed this once and came back. The gold seam stays.':''):b.miss>=2?'Missed twice. It is probably too big for a tired day. Turn Size down and keep the rep.':b.miss===1?'Missed yesterday. One crack is nothing. Lay the small version today.':'';
  const kn=(k,l,g,v,r,weak)=>'<button class="hb-knob'+(weak?' weak':'')+'" data-act="knob" data-k="'+k+'"><span class="l">'+l+'</span><span class="dial" style="--r:'+r+'deg">'+g+'</span><span class="v">'+esc(v)+'</span></button>';
  return '<div class="hb-top"><button class="hb-name" data-act="names">'+esc(b.name)+'</button><span class="hb-mat">'+MATNAME[b.mat]+'</span></div>'+(say?'<p class="hb-say">'+say+'</p>':'')+
   '<div class="hb-knobs">'+kn('cue','Cue','1',s.cue,focus*60-90)+kn('pair',fade?'Instead':'Pair','2',b.pair||'Nothing yet',b.pair?40:-80)+
   kn('size',fade?'Floors':'Size','3',fade?b.left+' left':['2 minutes','Normal','Stretch'][b.set],fade?0:b.set*90-90,!fade&&!b.done&&b.miss>=2&&b.set>0)+
   kn('treat','Reward','4',s.treat||'None yet',s.treat?40:-80)+'</div>'+
   '<div class="hb-row">'+(b.done?'<button class="hb-link" data-act="undo">Undo today’s block</button>':'<span></span>')+'<button class="hb-link end" data-act="retire">'+(fade?'Stop tracking this':'Retire this habit')+'</button></div>';
 }
 function render(){
  if(!root)return;
  const day=city&&weeks?addDays(getToday(),-7*weeks):null,m=model(day),live=model();
  if(focus>=m.stacks.length)focus=Math.max(0,m.stacks.length-1);
  const who=live.who||(typeof CLASSES!=='undefined'&&state.classId&&(CLASSES.find(c=>c.id===state.classId)||{}).name)||'';
  const st=strikeHtml(live);
  root.innerHTML=
   '<div class="hb-hud"><button class="hb-who" data-act="who"><span class="hb-tag">Becoming</span><b>'+esc(who||'Tap to say who')+'</b></button>'+
   '<div class="hb-count"><strong>'+live.laid+'</strong><span>'+(combo>1?'blocks today · <em>combo ×'+combo+'</em>':live.laid===1?'block laid today':'blocks laid today')+'</span></div></div>'+
   '<div class="hb-sky" data-noswipe="1"><svg viewBox="0 0 400 400" role="img" aria-label="Your habit stacks">'+scene(m)+'</svg>'+
   (m.stacks.length>1&&!city?'<button class="hb-nav l" data-act="prev" aria-label="Previous stack">‹</button><button class="hb-nav r" data-act="next" aria-label="Next stack">›</button>':'')+
   (m.stacks.length?'<button class="hb-view" data-act="view">'+(city?'Tower view':'City view')+'</button>':'')+
   (city?'<button class="hb-view left" data-act="newstack">+ Foundation</button>':'')+
   (city?'':'<div class="hb-dots">'+m.stacks.map((s,i)=>'<i class="'+(i===focus?'on':'')+'"></i>').join('')+'</div>')+'</div>'+
   (city?'<div class="hb-lapse"><div class="r"><span>Time-lapse</span><b>'+(weeks?weeks+(weeks===1?' week ago':' weeks ago'):'Today')+'</b></div><input type="range" min="0" max="'+MAXW+'" value="'+(MAXW-weeks)+'" data-act="lapse" aria-label="Weeks ago"><div class="r"><span>'+MAXW+' weeks ago</span><span>today</span></div></div>':
    '<button class="'+st[0]+'" data-act="'+st[1]+'"'+(st[1]?'':' disabled')+'><span><small>'+esc(st[2])+'</small><b>'+esc(st[3])+'</b></span><span class="k">'+st[4]+'</span></button>'+
    (live.stacks.some(s=>s.blocks.some(b=>b.kind!=='fade'))?'<button class="hb-rough'+(live.rough?' on':'')+'" data-act="rough">'+(live.rough?'Rough day is on · blocks are 2 minutes today':'Rough day? Shrink today’s blocks to 2 minutes')+'</button>':'')+
    '<div class="hb-tune">'+tuneHtml(live)+'</div>');
  laid=0;
  /* the sheet lives on the page's body, above the bottom bar and every card */
  host.innerHTML=sheet?sheetHtml():'';
  if(sheet){const f=host.querySelector('.hb-sheet input');if(f&&sheet.fresh){sheet.fresh=false;try{f.focus()}catch(e){}}}
 }

 /* ---------- small sheets for the few things that need typing ---------- */
 function open(def){sheet=Object.assign({fresh:true},def);render()}
 function sheetHtml(){
  const s=sheet;
  return '<div class="hb-veil" data-act="shut"></div><form class="hb-sheet" data-form="1"><h3>'+esc(s.title)+'</h3>'+(s.lead?'<p>'+esc(s.lead)+'</p>':'')+
   (s.fields||[]).map((f,i)=>'<label><span>'+esc(f.label)+'</span><input type="text" name="f'+i+'" maxlength="'+(f.max||32)+'" value="'+esc(f.value||'')+'" placeholder="'+esc(f.hint||'')+'" autocomplete="off"></label>').join('')+
   (s.picks?'<div class="hb-picks">'+s.picks.map(p=>'<button type="button" class="hb-pick'+(p.on?' on':'')+'" data-act="pick" data-v="'+esc(p.v)+'">'+esc(p.t)+'</button>').join('')+'</div>':'')+
   '<div class="hb-row"><button type="button" class="hb-link" data-act="shut">Cancel</button>'+(s.danger?'<button type="button" class="hb-link end" data-act="danger">'+esc(s.danger)+'</button>':'')+(s.fields?'<button type="submit" class="hb-go">'+esc(s.go||'Save')+'</button>':'')+'</div></form>';
 }
 const vals=()=>[].map.call(host.querySelectorAll('.hb-sheet input[type=text]'),i=>i.value);
 function stackSheet(id){
  const s=read().stacks.find(x=>x.id===id);
  open({title:s?'Foundation':'New foundation',lead:'Something you already do every day. Habits stack on top of it.',go:s?'Save':'Lay it',
   fields:[{label:'When',value:s?s.cue:'',hint:'After coffee',max:28}].concat(s?[{label:'Rooftop reward (unlocks when the stack is done)',value:s.treat||'',hint:'One episode',max:24}]:[]),
   danger:s&&!read().items.some(it=>it.stack===id)?'Remove':'',
   save(v){if(s){if(!api.editStack(id,{cue:v[0],treat:v[1]}))return}else{const nid=api.addStack(v[0]);if(!nid)return;focus=read().stacks.length-1;city=false}shut()},
   drop(){if(api.removeStack(id)){sel=null;shut()}}});
 }
 function habitSheet(stack,kind){
  const s=read().stacks.find(x=>x.id===stack);if(!s)return;
  open({title:kind==='fade'?'A habit to tear down':'New habit',lead:s.cue+', I will…',go:'Stack it',kind,stack,
   fields:kind==='fade'?[{label:'The habit you are breaking',hint:'Phone in bed'},{label:'Clean days to bring it down',value:'14',max:2}]:[{label:'The habit',hint:'Read 10 pages'},{label:'Its two-minute version',hint:'Read 1 page'}],
   picks:[{v:'build',t:'Build a habit',on:kind!=='fade'},{v:'fade',t:'Break a habit',on:kind==='fade'}],
   pick(v){habitSheet(stack,v)},
   save(v){const id=kind==='fade'?api.addHabit(stack,{name:v[0],kind:'fade',floors:parseInt(v[1],10)}):api.addHabit(stack,{name:v[0],small:v[1]});if(!id)return;sel=id;shut()}});
 }
 function shut(){sheet=null;render()}

 /* ---------- touch ---------- */
 function go(d){const n=read().stacks.length;if(city||!n)return;const to=Math.max(0,Math.min(n-1,focus+d));if(to!==focus){focus=to;sel=null;combo=0;buzz('tick')}render()}
 const buzz=k=>{try{if(typeof lmaBuzz==='function')lmaBuzz(k)}catch(e){}};
 function act(a,el){
  const m=model(),s=m.stacks[focus],b=s&&s.blocks.find(x=>x.id===sel);
  if(a==='prev')return go(-1);
  if(a==='next')return go(1);
  if(a==='view'){city=!city;weeks=0;sel=null;return render()}
  if(a==='newstack')return stackSheet(null);
  if(a==='new')return habitSheet(s.id,'build');
  if(a==='goto'){const more=m.stacks.findIndex((x,i)=>i!==focus&&x.next);if(more>=0){focus=more;sel=null;combo=0}return render()}
  if(a==='lay'&&s&&s.next){if(api.lay(s.next.id)){if(s.next.kind!=='fade'){laid=s.next.id;combo++}buzz('end')}return render()}
  if(a==='claim'&&s){if(api.claim(s.id))buzz('end');return render()}
  if(a==='rough'){api.setRough(!m.rough);return render()}
  if(a==='who')return open({title:'Who are you becoming?',lead:'Every block you lay is a vote for this person.',fields:[{label:'I am becoming',value:read().who,hint:'a reader, an athlete',max:40}],save(v){if(api.setWho(v[0]))shut()}});
  if(a==='shut')return shut();
  if(a==='pick'&&sheet&&sheet.pick)return sheet.pick(el.dataset.v);
  if(a==='danger'&&sheet&&sheet.drop)return sheet.drop();
  if(!b)return;
  if(a==='undo'){api.undo(b.id);combo=0;return render()}
  if(a==='retire')return open({title:b.kind==='fade'?'Stop tracking this?':'Retire this habit?',lead:'"'+b.names[1]+'" and its '+b.reps+(b.kind==='fade'?' clean days':' reps')+' will be removed.',danger:'Remove it',drop(){if(api.retire(b.id)){sel=null;shut()}}});
  if(a==='names')return open({title:'Name each size',lead:'The same habit at three sizes. On a hard day the two-minute one still counts.',
   fields:[{label:'Two minutes',value:b.names[0]},{label:'Normal',value:b.names[1]},{label:'Stretch',value:b.names[2]}],save(v){if(api.editHabit(b.id,{names:v}))shut()}});
  if(a==='knob'){
   const k=el.dataset.k;
   if(k==='size'&&b.kind!=='fade'){api.editHabit(b.id,{size:(b.set+2)%3});buzz('tick');return render()}
   if(k==='pair')return open({title:b.kind==='fade'?'What will you do instead?':'Pair it with something you enjoy',lead:b.kind==='fade'?'A replacement makes the old habit easier to drop.':'Do the thing you want only while you do the thing you need.',fields:[{label:b.kind==='fade'?'Instead':'Paired with',value:b.pair,hint:b.kind==='fade'?'A book on the pillow':'My favourite playlist',max:28}],save(v){if(api.editHabit(b.id,{pair:v[0]}))shut()}});
   if(k==='treat')return stackSheet(s.id);
   if(k==='cue')return open({title:'Move this block',lead:'Choose the foundation it stands on.',picks:m.stacks.map(x=>({v:x.id,t:x.cue,on:x.id===s.id})),pick(v){if(api.editHabit(b.id,{stack:v})){focus=read().stacks.findIndex(x=>x.id===v);shut()}}});
  }
 }
 function onClick(e){
  const t=e.target.closest('[data-act]');
  if(t&&(root.contains(t)||host.contains(t))){if(t.dataset.act!=='lapse')act(t.dataset.act,t);return}
  const tw=e.target.closest('.hb-tower');if(!tw)return;
  const i=+tw.dataset.i;
  if(city){city=false;weeks=0;focus=i;sel=null;return render()}
  if(i!==focus){focus=i;sel=null;combo=0;return render()}
  const g=e.target.closest('[data-new],[data-b],[data-stack],[data-treat]');if(!g)return;
  if(g.dataset.new)return habitSheet(g.dataset.new,'build');
  if(g.dataset.treat)return stackSheet(g.dataset.treat);
  if(g.dataset.stack)return stackSheet(g.dataset.stack);
  sel=sel===g.dataset.b?null:g.dataset.b;render();
 }
 let sx=null;
 function mount(el){
  if(!root){
   root=el.querySelector('.hb')||el;
   host=document.createElement('div');host.className='hb hb-host';document.body.appendChild(host);
   root.addEventListener('click',onClick);host.addEventListener('click',onClick);
   host.addEventListener('submit',e=>{e.preventDefault();if(sheet&&sheet.save)sheet.save(vals())});
   root.addEventListener('input',e=>{if(e.target.dataset.act==='lapse'){weeks=MAXW-(+e.target.value);render();const r=root.querySelector('[data-act=lapse]');if(r)r.focus()}});
   root.addEventListener('keydown',e=>{if(e.key==='Enter'&&e.target.closest&&e.target.closest('.hb-blk'))e.target.dispatchEvent(new MouseEvent('click',{bubbles:true}))});
   root.addEventListener('touchstart',e=>{sx=e.target.closest('.hb-sky')?e.touches[0].clientX:null},{passive:true});
   root.addEventListener('touchend',e=>{if(sx===null)return;const dx=e.changedTouches[0].clientX-sx;sx=null;if(Math.abs(dx)>40){e.stopPropagation();go(dx<0?1:-1)}},{passive:true});
   const m=model(),due=m.stacks.findIndex(s=>s.next);if(due>=0)focus=due;
  }
  combo=0;render();
 }
 return Object.assign({mount,render},api);
})();
window.Habits=HabitsFeature;
if(window.LifeMaxFeatures)LifeMaxFeatures.register({id:'habits',mount:el=>HabitsFeature.mount(el)});
