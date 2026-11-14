/* Habits 1.1.0: habit stacking, built literally (docs/specs/HABITS_TAB_BRIEF.md).
   A stack is something you already do ("After coffee"); a habit is a block laid on it, in the order you do them. One button lays the
   next block. A block's material shows how set the habit is (glass, clay, stone, gold, by total repetitions: a missed day never takes
   any away), its height is the habit's size (two minutes, normal, stretch), a crack is one missed day and a wobble is two. A habit
   you are breaking is a tower that comes down a floor for each clean day.

   Saved under the optional key state.habits (v 1), written by writeSave() only, never read into existence:
     { v, who, n, stacks:[{id,cue,treat}], items:[{id,stack,kind:'build'|'fade',names:[2 min, normal, stretch],size:0..2,born,pair,
       scar,floors}], log:{'YYYY-MM-DD':{itemId:size+1}}, rough:'YYYY-MM-DD'|'', claimed:{'YYYY-MM-DD':[stackId]} }
   No XP, Sparks or streak are touched here. Dates are the app's local days (getToday, addDays).

   On screen (1.1): the scene's SVG, its sky and one <g> per stack (by id) stay on the page; a render redraws only what changed and
   moves the towers by their style, so a change of stack or of view is animated. A block not laid yet today is a blueprint; the
   next one glows. A tower too tall for the scene keeps its size and scrolls. Selecting a block shortens the scene so its dials fit. */
const HabitsFeature=(function(){
 const LINE=66,HW=80,D=28,FH=16,HS=[40,62,88],ANG=Math.atan(D/HW)*180/Math.PI,MAXW=12,BASE=362,HZ=296;
 const NONE=Object.freeze({v:1,who:'',n:0,stacks:[],items:[],log:{},rough:'',claimed:{}});
 /* top, left and right face of each material; 'plan' is a block not laid yet today, drawn as a blueprint */
 const GOLD=['url(#hbGoldT)','url(#hbGoldL)','url(#hbGoldR)'],PLAN='rgba(140,168,255,.07)';
 const MAT={glass:['rgba(159,216,255,.5)','rgba(159,216,255,.35)','rgba(159,216,255,.2)'],clay:['#e2a37b','#b46d48','#935438'],
  stone:['#c6d2ea','#94a6ca','#7386ad'],gold:GOLD,treat:GOLD,fade:['#a85c7b','#70364f','#582a3e'],slab:['#6f7fa6','#4c5b82','#3d4a6d'],
  lock:['#9c8757','#77643a','#5e4f2c'],plan:[PLAN,PLAN,PLAN]};
 const TINT={glass:'#9fd8ff',clay:'#dd9a72',stone:'#a9bbdc',gold:'#f3cf63'};
 const MATNAME={glass:'Glass · brand new',clay:'Clay · taking shape',stone:'Stone · nearly automatic',gold:'Gold · runs itself',fade:'Coming down'};
 /* the scene's own colours for night, the hour around sunrise and sunset, and day */
 const PAL={night:{sky:['#060a18','#0b1330','#1d2c63'],gnd:['#111a3d','#090d1f'],tile:['#1a2447','#151d3b'],line:'rgba(143,165,255,.12)',edge:['#10173a','#0b1029'],far:'#0e1530',near:'#131c3d',haze:'rgba(110,140,255,.2)',halo:'rgba(233,237,255,.18)',ink:'#cfd8ff'},
  dusk:{sky:['#1d2350','#6b4b7c','#e39a74'],gnd:['#3b2d52','#221a36'],tile:['#4d3d68','#42335b'],line:'rgba(255,214,190,.14)',edge:['#33264a','#281d3c'],far:'#57406f',near:'#412f5c',haze:'rgba(255,196,150,.3)',halo:'rgba(255,200,150,.4)',ink:'#f6e3d6'},
  day:{sky:['#2f66b8','#6fa5dd','#c4e2f5'],gnd:['#86b392','#5f9276'],tile:['#dbe3ee','#c8d3e2'],line:'rgba(40,60,110,.16)',edge:['#a3b2c8','#8d9db5'],far:'#a4c5e8',near:'#86add9',haze:'rgba(255,255,255,.4)',halo:'rgba(255,243,196,.4)',ink:'#12303a'}};
 const DEFS='<radialGradient id="hbShade"><stop stop-color="rgba(0,0,0,.55)"/><stop offset="1" stop-color="rgba(0,0,0,0)"/></radialGradient>'+
  '<linearGradient id="hbLit" x1="0" x2="0" y1="0" y2="1"><stop stop-color="rgba(255,255,255,.16)"/><stop offset="1" stop-color="rgba(255,255,255,0)"/></linearGradient>'+
  '<linearGradient id="hbDim" x1="0" x2="0" y1="0" y2="1"><stop stop-color="rgba(0,0,0,0)"/><stop offset="1" stop-color="rgba(0,0,0,.22)"/></linearGradient>'+
  '<linearGradient id="hbGoldT" x1="0" x2="1" y1="0" y2="1"><stop stop-color="#fff8d2"/><stop offset="1" stop-color="#f1d06a"/></linearGradient>'+
  '<linearGradient id="hbGoldL" x1="0" x2="0" y1="0" y2="1"><stop stop-color="#fff1b0"/><stop offset=".45" stop-color="#e9b93f"/><stop offset="1" stop-color="#a8791c"/></linearGradient>'+
  '<linearGradient id="hbGoldR" x1="0" x2="0" y1="0" y2="1"><stop stop-color="#e8c558"/><stop offset=".45" stop-color="#c4932b"/><stop offset="1" stop-color="#8a6114"/></linearGradient>'+
  '<linearGradient id="hbSheen"><stop stop-color="rgba(255,255,255,0)"/><stop offset=".5" stop-color="rgba(255,255,255,.85)"/><stop offset="1" stop-color="rgba(255,255,255,0)"/></linearGradient>';
 const STARTERS=['After waking up','After coffee','After lunch','After getting home','At lights out'];
 const CAT=['X...X...','XX.XX...','XXXXX...','XoXoX..X','XXXXX..X','.XXXXXX.','.XXXXXX.','.XXXXXX.'];
 const ICON='<svg viewBox="0 0 28 28" width="32" height="32" aria-hidden="true"><path d="M14 3 25 8.5 14 14 3 8.5Z" opacity=".4"/><path d="M3 8.5 14 14v11L3 19.5Z" opacity=".85"/><path d="M25 8.5 14 14v11l11-5.5Z" opacity=".62"/></svg>';
 const GLYPH={cue:'<circle cx="12" cy="5" r="2.2"/><path d="M12 7.2V20M8 10.5h8M5 13.5a7 7 0 0 0 14 0"/>',
  pair:'<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
  treat:'<path d="M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.8z"/>'};
 let root=null,host=null,focus=0,sel=null,city=false,weeks=0,combo=0,laid=0,felled=0,upg=0,sheet=null;
 /* what is on screen: the nodes that stay (one <g> per stack, by id), and where the focused tower is scrolled to */
 let ui=null,nodes={},drag=null,moved=0;const view={pan:null,now:0,max:0,k:1,m:null,live:null,n:-1,city:false};

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
  /* built of due: today's blocks laid, of the habits you are building (a clean day on one you are breaking is not a block laid) */
  const build=[].concat(...stacks.map(s=>s.blocks)).filter(b=>b.kind!=='fade');
  return {day:d,live,who:h.who,rough:live&&h.rough===d,laid:h.log[d]?Object.keys(h.log[d]).length:0,built:build.filter(b=>b.done).length,due:build.length,stacks};
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
  /* earlier (-1) or later (1) in its stack: the order you do them in */
  move(id,dir){return commit(h=>{
   const it=h.items.find(x=>x.id===id);if(!it)return false;
   const mine=h.items.filter(x=>x.stack===it.stack),i=mine.indexOf(it),other=mine[i+(dir<0?-1:1)];if(!other)return false;
   const a=h.items.indexOf(it),b=h.items.indexOf(other);h.items[a]=other;h.items[b]=it;
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
 const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
 const n1=v=>Math.round(v*10)/10;
 /* a label on at most two lines, broken between words */
 function wrap(s,n){if(s.length<=n)return [s];const i=s.lastIndexOf(' ',n);return i<3?[fit(s,n)]:[s.slice(0,i),fit(s.slice(i+1),n)]}
 /* the same few random numbers for the same block, every time it is drawn */
 function dice(key){let h=2166136261;for(let i=0;i<key.length;i++)h=Math.imul(h^key.charCodeAt(i),16777619);return ()=>{h=(Math.imul(h,1664525)+1013904223)>>>0;return h/4294967296}}
 /* one block: its three faces, what its material looks like up close, then whatever stands on its faces (o.extra).
    o: k width, key (what makes its marks its own), win lit windows, tint a blueprint's footing colour, flash, extra */
 function prism(y,h,m,cls,attrs,o){
  o=o||{};const k=o.k||1,w=HW*k,d=D*k,c=MAT[m],lf=(u,v)=>n1(-w+u*w)+','+n1(y+d+u*d+v),rf=(u,v)=>n1(w-u*w)+','+n1(y+d+u*d+v),mid=v=>'0,'+n1(y+2*d+v);
  const L=lf(0,0)+' '+mid(0)+' '+mid(h)+' '+lf(0,h),R=rf(0,0)+' '+mid(0)+' '+mid(h)+' '+rf(0,h),T='0,'+n1(y)+' '+rf(0,0)+' '+mid(0)+' '+lf(0,0);
  const hex=lf(0,0)+' 0,'+n1(y)+' '+rf(0,0)+' '+rf(0,h)+' '+mid(h)+' '+lf(0,h),face=(p,f)=>'<polygon class="hb-face" points="'+p+'" fill="'+f+'"/>';
  const ring=/\bsel\b/.test(cls)?'<polygon class="hb-fx hb-ring" points="'+hex+'"/>':'';
  let out='<g class="hb-blk '+cls+'" '+attrs+'>'+(m==='glass'?'<polygon points="'+hex+'" fill="#27406b"/>':m==='plan'?'<polygon points="'+lf(0,0)+' '+mid(0)+' '+rf(0,0)+' '+rf(0,h)+' '+mid(h)+' '+lf(0,h)+'" fill="rgba(22,36,88,.56)"/>':'')+face(L,c[1])+face(R,c[2])+face(T,c[0]);
  if(m==='plan')return out+(o.tint?'<polyline class="hb-fx" points="'+lf(0,h)+' '+mid(h)+' '+rf(0,h)+'" fill="none" stroke="'+o.tint+'" stroke-width="2.5" stroke-linejoin="round" opacity=".85"/>':'')+(o.extra||'')+ring+'</g>';
  const r=dice(o.key||m),ln=(p,s,sw)=>'<polyline class="hb-fx" points="'+p+'" fill="none" stroke="'+s+'" stroke-width="'+sw+'"/>';
  if(m!=='glass'&&c!==GOLD)out+='<polygon class="hb-fx" points="'+L+'" fill="url(#hbLit)"/><polygon class="hb-fx" points="'+R+'" fill="url(#hbDim)"/>';
  if(m==='clay'){ /* bricks: the joints of one course sit over the middle of the bricks below */
   let p='';
   for(let i=0,v=0;v<h-3;i++,v+=11){const e=n1(Math.min(h,v+11)-v);if(v)p+='M'+lf(0,v)+'L'+mid(v)+'L'+rf(0,v);for(let u=i%2?.125:.25;u<1;u+=.25)p+='M'+lf(u,v)+'v'+e+'M'+rf(u,v)+'v'+e}
   out+='<path class="hb-fx hb-course" d="'+p+'"/>';
  }
  if(m==='stone'){ /* a few big irregular blocks, and light catching the top edge */
   const a=h*(.35+r()*.3),b=h*(.35+r()*.3),up=r()<.5;
   out+='<path class="hb-fx hb-course" d="M'+lf(0,a)+'L'+lf(1,a)+'M'+lf(.25+r()*.5,0)+'v'+n1(a)+'M'+lf(.25+r()*.5,a)+'v'+n1(h-a)+'M'+rf(0,b)+'L'+rf(1,b)+'M'+rf(.3+r()*.4,up?0:b)+'v'+n1(up?b:h-b)+'"/>'+ln(lf(0,1)+' '+mid(1)+' '+rf(0,1),'rgba(255,255,255,.55)',1);
  }
  if(m==='glass')out+='<polygon class="hb-fx" points="'+lf(.08,0)+' '+lf(.3,0)+' '+lf(.6,h)+' '+lf(.38,h)+'" fill="rgba(255,255,255,.2)"/><polygon class="hb-fx" points="'+lf(.37,0)+' '+lf(.43,0)+' '+lf(.73,h)+' '+lf(.67,h)+'" fill="rgba(255,255,255,.12)"/>'+
   ln(lf(0,0)+' '+mid(0)+' '+rf(0,0),'rgba(230,246,255,.6)',1)+ln(mid(0)+' '+mid(h),'rgba(230,246,255,.45)',1);
  if(o.win)for(let j=0;j<3;j++){ /* three windows a floor on each face, most of them lit */
   const u=.1+j*.29,q=(f,col)=>'<polygon class="hb-fx" points="'+f(u,7)+' '+f(u+.2,7)+' '+f(u+.2,h-7)+' '+f(u,h-7)+'" fill="'+col+'"/>';
   out+=q(lf,r()<.8?'#ffd27a':'#40192b')+q(rf,r()<.7?'#e9b45c':'#34131f');
  }
  out+=ln(lf(0,h-1)+' '+mid(h-1)+' '+rf(0,h-1),'rgba(0,0,0,.28)',2)+ln(lf(0,0)+' 0,'+n1(y)+' '+rf(0,0),'rgba(255,255,255,.34)',1);
  if(m==='glass')out+='<polygon class="hb-fx" points="'+hex+'" fill="none" stroke="#e6f6ff" stroke-width="1.5" stroke-linejoin="round"/>';
  const up=/\bup\b/.test(cls);
  if(c===GOLD||up){ /* a strip of light that crosses the block: now and then on gold, once when a block changes material */
   const strip=cl=>'<polygon class="'+cl+'" points="-10,'+n1(y-4)+' 20,'+n1(y-4)+' 0,'+n1(y+2*d+h+4)+' -30,'+n1(y+2*d+h+4)+'" fill="url(#hbSheen)"';
   out+='<clipPath id="hbc'+o.key+'"><polygon points="'+hex+'"/></clipPath><g class="hb-fx" clip-path="url(#hbc'+o.key+')">'+(c===GOLD?strip('hb-spec')+' style="animation-delay:-'+n1(r()*5)+'s"/>':'')+(up?strip('hb-swp')+'/>':'')+'</g>';
  }
  return out+(o.extra||'')+(o.flash?'<polygon class="hb-fx hb-flash" points="'+T+'" fill="#fff"/>':'')+ring+'</g>';
 }
 const sign=(x0,y0,a,cx,y,t,cls,max)=>'<text class="hb-sign'+(cls?' '+cls:'')+'" text-anchor="middle" data-fit="'+max+'" transform="translate('+x0+','+n1(y0)+') skewY('+n1(a)+')" x="'+cx+'" y="'+n1(y)+'">'+esc(t)+'</text>';
 /* a name on the left face (two lines when it is long) and a count on the right; narrow leaves the face's right-hand third free */
 function labels(y,h,name,count,cls,narrow){
  const t=wrap(name,14);
  return t.map((x,i)=>sign(-HW,y+D,ANG,narrow?33:HW/2,h/2+3.5+(t.length>1?(i?6:-6):0),x,cls,narrow?52:66)).join('')+sign(0,y+2*D,-ANG,HW/2,h/2+3.5,count,cls,64);
 }
 /* a crack (or the gold seam left by coming back), down the right-hand third of the left face, its own shape for each block */
 function crack(y,h,key,cls){
  const r=dice(key+'c'),n=h>50?5:4,at=(u,v)=>n1(-HW+u*HW)+','+n1(y+D+u*D+v);let d='',br='';
  for(let i=0;i<=n;i++){const u=.77+(i%2?.11:0)+r()*.06,v=h*i/n;d+=(i?'L':'M')+at(u,v);if(i===2)br='M'+at(u,v)+'L'+at(.97,v+3+r()*6)}
  return '<path class="'+cls+'" d="'+d+br+'"/>';
 }
 function cat(cx,by){let s='';const p=3.4;CAT.forEach((row,r)=>{for(let c=0;c<row.length;c++)if(row[c]!=='.')s+='<rect x="'+n1(cx-14+c*p)+'" y="'+n1(by-CAT.length*p+r*p)+'" width="'+(p+.2)+'" height="'+(p+.2)+'" fill="'+(row[c]==='o'?'#f6d679':'#05070d')+'"/>'});return '<g class="hb-cat">'+s+'</g>'}
 function tree(x,y,id,name){return '<g class="hb-tree" data-b="'+id+'" role="button" tabindex="0" aria-label="'+esc(name)+', gone"><rect x="'+(x-2.5)+'" y="'+(y-22)+'" width="5" height="22" fill="#6b4a2f"/><circle cx="'+x+'" cy="'+(y-30)+'" r="14" fill="#3f9d6b"/><circle cx="'+(x-9)+'" cy="'+(y-23)+'" r="9" fill="#348a5c"/><circle cx="'+(x+9)+'" cy="'+(y-24)+'" r="10" fill="#46ad77"/></g>'}
 /* the ground a tower stands on: nine paving tiles with an edge, and the tower's shadow falling to the right */
 function plot(f,p){
  const w=HW*f,d=D*f,cy=-D;
  let o='<g class="hb-fx"><polygon points="'+(-w)+','+cy+' 0,'+n1(cy+d)+' 0,'+n1(cy+d+5)+' '+(-w)+','+(cy+5)+'" fill="'+p.edge[0]+'"/><polygon points="'+w+','+cy+' 0,'+n1(cy+d)+' 0,'+n1(cy+d+5)+' '+w+','+(cy+5)+'" fill="'+p.edge[1]+'"/>';
  for(let i=0;i<3;i++)for(let j=0;j<3;j++){const x=(i-j)*w/3,y=cy+(i+j-2)*d/3;o+='<polygon points="'+n1(x)+','+n1(y-d/3)+' '+n1(x+w/3)+','+n1(y)+' '+n1(x)+','+n1(y+d/3)+' '+n1(x-w/3)+','+n1(y)+'" fill="'+p.tile[(i+j)%2]+'" stroke="'+p.line+'" stroke-width="1"/>'}
  return o+'<ellipse cx="'+HW*.34+'" cy="'+(cy+7)+'" rx="'+HW*1.18+'" ry="'+D*1.05+'" fill="url(#hbShade)"/></g>';
 }
 /* dust thrown out along a block's base when it lands */
 function dust(y,h){
  let o='';
  for(let i=0;i<10;i++){const left=i<5,u=(i%5+.5)/5;o+='<circle class="hb-dust" cx="'+n1((left?-1:1)*(HW-u*HW))+'" cy="'+n1(y+D+u*D+h)+'" r="'+(2.5+i%3)+'" style="--dx:'+(left?-22:22)+'px;animation-delay:'+(250+i%3*30)+'ms"/>'}
  return o;
 }
 /* the floor that has just been knocked off a habit you are breaking, and its rubble */
 function fallen(y,key){
  const r=dice(key+'f');let o=prism(y,26,'fade','fall','',{key:key+'x',win:true});
  for(let i=0;i<6;i++)o+='<rect class="hb-debris" x="'+n1((r()*2-1)*HW*.9)+'" y="'+n1(y+D+18+r()*22)+'" width="'+(4+i%3*2)+'" height="'+(3+i%2*2)+'" fill="'+(i%2?'#a85c7b':'#582a3e')+'" style="--dx:'+n1((r()-.5)*90)+'px;--rot:'+n1((r()-.5)*400)+'deg;animation-delay:'+i*25+'ms"/>';
  return o;
 }
 /* one tower, drawn up from its foundation at (0,0); answers the markup, how tall it is, and where each block's middle is */
 function tower(s,on,live,pal){
  let y=-FH-2*D,out=plot(city?1.3:1.7,pal),fx='',roof=y;const marks={};
  const cue=wrap(s.cue.toUpperCase(),12),order=s.blocks.filter(b=>!b.cleared),gone=s.blocks.filter(b=>b.cleared),li=order.findIndex(b=>b.id===laid);
  const dip=i=>li>i?' style="animation-delay:'+(250+(li-i)*20)+'ms"':'';
  out+=prism(y,FH,'slab',li>=0?'dip':'','data-stack="'+s.id+'" role="button" tabindex="0" aria-label="'+esc(s.cue)+', edit this foundation"'+dip(-1),
   {extra:sign(-HW,y+D,ANG,HW/2,FH/2+3.2,cue[0],'cue',70)+(cue[1]?sign(0,y+2*D,-ANG,HW/2,FH/2+3.2,cue[1],'cue',70):'')});
  order.forEach((b,i)=>{
   const fade=b.kind==='fade',plan=live&&!b.done&&!fade,floors=fade?Math.min(b.left,MAXW):1,h=fade?26:HS[b.size],key=s.id+b.id,was=laid===b.id;
   for(let f=0;f<floors;f++){
    y-=h;const top=f===floors-1;
    const cls=[on&&sel===b.id?'sel':'',on&&live&&s.next===b&&top?'next':'',plan?'plan':'',was?'drop':'',was&&upg===b.id?'up':'',b.miss>=2?'wob':'',li>i?'dip':''].join(' ');
    const extra=top?(b.miss>=1?crack(y,h,key,'hb-crack'):b.scar?crack(y,h,key,'hb-seam'):'')+
     labels(y,h,b.name,fade?b.reps+(b.reps===1?' day clean':' days clean'):b.reps+(b.reps===1?' rep':' reps'),plan||fade||b.mat==='glass'?'lt':'',b.miss>=1||b.scar):'';
    out+=prism(y,h,plan?'plan':b.mat,cls,'data-b="'+b.id+'"'+(top?' role="button" tabindex="0" aria-label="'+esc(b.name)+', '+(fade?b.reps+' days clean':b.reps+' reps')+'"':'')+dip(i),
     {extra,key:key+'f'+f,win:fade&&!top,tint:plan?TINT[b.mat]:'',flash:was});
    if(!plan)roof=y;
   }
   marks[b.id]=y+D+h/2;
   if(was)fx+=dust(y,h);
   if(felled===b.id)fx+=fallen(y-26,key);
  });
  /* a habit that is gone is a tree on the lot beside the tower, so nothing is ever built around it */
  if(gone.some(b=>b.id===felled))fx+=fallen(y-26,s.id+felled);
  gone.slice(0,4).forEach((b,i)=>{
   const x=[-HW-16,HW+16,-HW-2,HW+2][i],ty=i<2?-D:-D+14;marks[b.id]=-D-24;
   fx+=(on&&sel===b.id?'<ellipse class="hb-fx hb-ring" cx="'+x+'" cy="'+ty+'" rx="17" ry="6"/>':'')+tree(x,ty,b.id,b.name);
  });
  /* the rooftop reward sits flush on the roof: dull until the stack is done, gold when it can be claimed, flagged once it has been */
  if(s.treat&&live&&s.blocks.length){
   const open=!s.next&&!s.claimed,k=.7,yt=y+D*(1-k)-22,px=HW*k*.52,py=n1(yt+D*k);
   out+=prism(yt,22,open||s.claimed?'treat':'lock','hb-treat'+(open?' lit':''),'data-treat="'+s.id+'" role="button" tabindex="0" aria-label="Rooftop reward: '+esc(s.treat)+'"',{k,key:s.id+'t',
    extra:'<text class="hb-sign" data-fit="74" text-anchor="middle" x="'+(s.claimed?-8:0)+'" y="'+n1(yt+D*k+3.5)+'">'+esc(s.claimed?'Enjoyed':fit(s.treat,14))+'</text>'+
     (s.claimed?'<path class="hb-fx" d="M'+px+','+py+'v-25" stroke="#fff7d6" stroke-width="1.6" fill="none"/><path class="hb-fx hb-flag" d="M'+px+','+n1(py-25)+'l13,4.5l-13,5z" fill="#ff6b6b"/>':'')});
   y=yt-(s.claimed?6:0);
  }
  if(on&&live&&!city){
   const gy=y-14;y=gy-34;
   out+='<g class="hb-blk hb-ghost" data-new="'+s.id+'" role="button" tabindex="0" aria-label="Stack a habit here"><polygon points="0,'+y+' 50,'+(gy-17)+' 0,'+gy+' -50,'+(gy-17)+'"/><text class="hb-sign" text-anchor="middle" x="0" y="'+n1(gy-13.5)+'">+ stack a habit</text></g>';
  }
  if(on&&live&&typeof state!=='undefined'&&state.pet)fx+=cat(HW*(s.treat?.47:.36),roof+D*(s.treat?1.45:1.32));
  return {svg:out+fx,height:8-y,top:y,marks};
 }
 /* night, the hour around sunrise and sunset, or day, and where the sun or moon stands */
 function clock(){
  const n=new Date(),hr=n.getHours()+n.getMinutes()/60,night=hr<5.5||hr>=20.5,t=clamp(night?((hr+3.5)%24)/9:(hr-5.5)/15,0,1);
  return {ph:night?'night':hr<7.5||hr>=18?'dusk':'day',x:36+t*250,y:104-Math.sin(t*Math.PI)*46};
 }
 function skyline(seed,lo,hi){let r=seed,x=-130,d='M-130,600';while(x<530){r=(r*73+19)%997;const w=12+r%20;r=(r*73+19)%997;d+='V'+(HZ-lo-r%(hi-lo))+'H'+(x+=w)}return d+'V600Z'}
 /* everything behind the towers. Drawn once for each time of day, not on every render */
 function skyHtml(ph){
  const p=PAL[ph],hz=n1(HZ/400);let st='',lit='';
  if(ph!=='day'){let r=7;for(let i=0;i<46;i++){r=(r*73+19)%997;const x=r%400;r=(r*73+19)%997;st+='<circle'+(i%7?'':' class="hb-twk" style="animation-delay:-'+n1(i%5*.48)+'s"')+' cx="'+x+'" cy="'+(r%(ph==='night'?240:120))+'" r="'+(i%7?0.7:1.2)+'" fill="#d5ddff" opacity="'+n1((ph==='night'?0.25:0.1)+(i%5)*0.1)+'"/>'}
   let q=11;for(let i=0;i<16;i++){q=(q*73+19)%997;const x=q%400;q=(q*73+19)%997;lit+='<rect x="'+x+'" y="'+(HZ-11+q%8)+'" width="1.6" height="1.6" fill="#ffd27a" opacity=".75"/>'}}
  else st='<g class="hb-cloud"><g transform="translate(120,74)" fill="#fff" opacity=".85"><ellipse rx="34" ry="11"/><ellipse cx="-14" cy="-8" rx="16" ry="11"/><ellipse cx="10" cy="-11" rx="20" ry="14"/></g></g>'+
   '<g class="hb-cloud" style="animation-delay:-31s"><g transform="translate(170,132) scale(.7)" fill="#fff" opacity=".7"><ellipse rx="34" ry="11"/><ellipse cx="-12" cy="-8" rx="18" ry="11"/><ellipse cx="14" cy="-10" rx="18" ry="12"/></g></g>';
  return '<defs><linearGradient id="hbSky" x1="0" x2="0" y1="0" y2="1"><stop stop-color="'+p.sky[0]+'"/><stop offset="'+n1(hz*.6)+'" stop-color="'+p.sky[1]+'"/><stop offset="'+hz+'" stop-color="'+p.sky[2]+'"/></linearGradient>'+
   '<linearGradient id="hbGnd" x1="0" x2="0" y1="0" y2="1"><stop stop-color="'+p.gnd[0]+'"/><stop offset=".3" stop-color="'+p.gnd[1]+'"/></linearGradient>'+
   '<linearGradient id="hbHaze" x1="0" x2="0" y1="0" y2="1"><stop stop-color="'+p.haze+'" stop-opacity="0"/><stop offset="1" stop-color="'+p.haze+'"/></linearGradient>'+
   '<radialGradient id="hbHalo"><stop stop-color="'+p.halo+'"/><stop offset="1" stop-color="'+p.halo+'" stop-opacity="0"/></radialGradient></defs>'+
   '<rect x="-200" y="-200" width="800" height="900" fill="url(#hbSky)"/>'+st+
   '<g class="hb-orb">'+(ph==='night'?'<circle r="40" fill="url(#hbHalo)"/><path d="M-4,-9A9,9 0 1,1 -4,9A4.2,9 0 0,0 -4,-9Z" fill="#e9edff" transform="rotate(-28)"/>':'<circle r="'+(ph==='day'?34:46)+'" fill="url(#hbHalo)"/><circle r="12" fill="'+(ph==='dusk'?'#ffd9a8':'#fff3c4')+'"/>')+'</g>'+
   '<g class="hb-par far"><path d="'+skyline(5,26,64)+'" fill="'+p.far+'"/></g><g class="hb-par near"><path d="'+skyline(23,12,36)+'" fill="'+p.near+'"/>'+lit+'</g>'+
   '<g class="hb-par gnd"><rect x="-200" y="'+(HZ-30)+'" width="800" height="30" fill="url(#hbHaze)"/><rect x="-200" y="'+HZ+'" width="800" height="500" fill="url(#hbGnd)"/><rect x="-200" y="'+HZ+'" width="800" height="1" fill="'+p.line+'"/></g>';
 }
 /* a long label is squeezed to the face it is written on, once it is on the page and can be measured */
 function fitText(g){
  g.querySelectorAll('text[data-fit]').forEach(t=>{try{const max=+t.dataset.fit;if(t.getComputedTextLength()>max){t.setAttribute('textLength',max);t.setAttribute('lengthAdjust','spacingAndGlyphs')}}catch(e){}});
 }
 const put=(el,html)=>{if(el._h===html)return false;el._h=html;el.innerHTML=html;return true};
 const cityFit=n=>{const k=Math.min(.7,340/(n*190));return {k,gap:n>1?(400-2*HW*k-28)/(n-1):0}};
 /* where every tower stands. The nodes stay, so a change of stack, of view or of scroll is a move, not a redraw.
    A tower too tall for the scene keeps a readable size and is scrolled: by itself to the next block, or by a vertical drag */
 function place(stagger){
  const m=view.m,live=view.live,n=m.stacks.length,s=live.stacks[focus],picked=!city&&!!s&&s.blocks.some(b=>b.id===sel),top=(picked?100:0)+46,c=cityFit(n);
  const f=!city&&m.stacks[focus]&&nodes[m.stacks[focus].id];let pan=0;view.max=0;
  if(f){
   const t=f._t,aim=picked?t.marks[sel]:s.next?t.marks[s.next.id]:t.top;
   view.k=clamp((BASE-top)/t.height,.9,1.25);view.max=Math.max(0,view.k*t.height-(BASE-top));
   pan=clamp(view.pan===null?(picked?235:180)-BASE-view.k*aim:view.pan,0,view.max);
  }
  view.now=pan;
  m.stacks.forEach((st,i)=>{
   const g=nodes[st.id],t=g._t,o=i-focus;let x=200,y=BASE+pan,k=view.k,op=1;
   if(city){k=Math.min(c.k,290/t.height);x=n>1?14+HW*c.k+i*c.gap:200;y=352}
   else if(o){k=Math.min(.5,150/t.height);x=200+o*152;y=330+pan;op=Math.abs(o)===1&&!picked?.55:0}
   g.style.transitionDelay=stagger?i*40+'ms':'';
   g.style.transform='translate('+n1(x)+'px,'+n1(y)+'px) scale('+Math.round(k*1000)/1000+')';
   g.style.opacity=op;g.style.pointerEvents=op?'':'none';g.dataset.i=i;
  });
  const px=city?0:Math.min(focus,5),par=(q,dx,dy)=>{const e=ui.bg.querySelector(q);if(e)e.style.transform='translate('+dx+'px,'+n1(dy)+'px)'};
  par('.far',-px*8,pan*.5);par('.near',-px*18,pan*.75);par('.gnd',0,pan);
  ui.sky.classList.toggle('tall',view.max>0);
 }
 function shell(){
  root.innerHTML='<div class="hb-hud"></div><div class="hb-sky" data-noswipe="1"><svg viewBox="0 0 400 400" preserveAspectRatio="xMidYMax slice" role="img" aria-label="Your habit stacks"><defs>'+DEFS+'</defs><g class="hb-bg"></g><g class="hb-towers"></g></svg><div class="hb-over"></div></div><div class="hb-below"></div>';
  const q=c=>root.querySelector(c);ui={hud:q('.hb-hud'),sky:q('.hb-sky'),bg:q('.hb-bg'),tw:q('.hb-towers'),over:q('.hb-over'),below:q('.hb-below'),ph:''};nodes={};
 }
 function strikeHtml(m){
  const s=m.stacks[focus];
  if(!m.stacks.length)return ['hb-strike','newstack','Start here','Lay your first foundation','+'];
  if(!s.blocks.length)return ['hb-strike','new','Empty foundation','Stack a habit on '+s.cue.toLowerCase(),'+'];
  const nx=s.next;
  if(nx&&nx.kind==='fade')return ['hb-strike clean','lay','Stayed clean today','Knock a floor off: '+nx.name,'↓'];
  if(nx)return ['hb-strike','lay','Lay the next block'+(combo?' · combo ×'+(combo+1):''),nx.name,ICON];
  if(s.treat&&!s.claimed)return ['hb-strike claim','claim','Stack complete · rooftop unlocked','Claim: '+s.treat,'★'];
  const more=m.stacks.findIndex((x,i)=>i!==focus&&x.next);
  return more<0?['hb-strike rest','','Skyline complete','Every block is laid today','']:['hb-strike rest','goto','This stack is done','Go to '+m.stacks[more].cue.toLowerCase(),'→'];
 }
 function tuneHtml(m){
  const s=m.stacks[focus],b=s&&s.blocks.find(x=>x.id===sel);
  if(!b)return '<span class="hb-tag">'+(m.stacks.length?'Tap a block to tune it · tap a foundation to rename it':'A foundation is something you already do every day')+'</span>';
  const fade=b.kind==='fade';
  const say=b.cleared?'This one is gone. A tree stands where it was.':b.done?(b.scar?'You missed this once and came back. The gold seam stays.':''):b.miss>=2?'Missed twice. It is probably too big for a tired day. Turn Size down and keep the rep.':b.miss===1?'Missed yesterday. One crack is nothing. Lay the small version today.':'';
  /* a dial: what it is called, a small picture of what it does, and what it is set to; only Size has a pointer, with three places */
  const kn=(k,l,g,v,on,o)=>'<button class="hb-knob'+(o&&o.weak?' weak':'')+'" data-act="knob" data-k="'+k+'"><span class="l">'+l+'</span><span class="dial'+(on?' on':'')+(o&&o.ptr!==undefined?' ptr" style="--r:'+o.ptr+'deg':'')+'"><svg viewBox="0 0 24 24"'+(o&&o.solid?' class="f"':'')+' aria-hidden="true">'+g+'</svg></span><span class="v">'+esc(v)+'</span></button>';
  const bars=fade?[0,1,2].map(i=>'<rect x="5" y="'+(5.5+i*5)+'" width="14" height="3" rx="1"/>').join(''):[0,1,2].map(i=>'<rect x="'+(5+i*5.25)+'" y="'+(14-i*4.5)+'" width="3.5" height="'+(6+i*4.5)+'" rx="1"'+(i<=b.set?'':' opacity=".25"')+'/>').join('');
  return '<div class="hb-top"><button class="hb-name" data-act="names">'+esc(b.name)+'</button><span class="hb-mat">'+MATNAME[b.mat]+'</span></div>'+(say?'<p class="hb-say">'+say+'</p>':'')+
   '<div class="hb-knobs">'+kn('cue','Cue',GLYPH.cue,s.cue,true)+kn('pair',fade?'Instead':'Pair',GLYPH.pair,b.pair||'Nothing yet',!!b.pair)+
   kn('size',fade?'Floors':'Size',bars,fade?b.left+' left':['2 minutes','Normal','Stretch'][b.set],true,fade?{solid:true}:{solid:true,ptr:(b.set-1)*52,weak:!b.done&&b.miss>=2&&b.set>0})+
   kn('treat','Reward',GLYPH.treat,s.treat||'None yet',!!s.treat,{solid:!!s.treat})+'</div>'+
   (s.blocks.length>1?'<div class="hb-row"><button class="hb-link" data-act="earlier"'+(s.blocks[0]===b?' disabled':'')+'>↓ Do it earlier</button><button class="hb-link" data-act="later"'+(s.blocks[s.blocks.length-1]===b?' disabled':'')+'>Do it later ↑</button></div>':'')+
   '<div class="hb-row">'+(b.done?'<button class="hb-link" data-act="undo">Undo today’s block</button>':'<span></span>')+'<button class="hb-link end" data-act="retire">'+(fade?'Stop tracking this':'Retire this habit')+'</button></div>';
 }
 function render(){
  if(!root)return;
  const day=city&&weeks?addDays(getToday(),-7*weeks):null,m=model(day),live=model(),n=m.stacks.length,off=still();
  if(focus>=n)focus=Math.max(0,n-1);
  if(!ui||!root.contains(ui.sky))shell();
  if(off)laid=felled=upg=0;
  root.classList.toggle('hb-still',off);
  const s=live.stacks[focus],picked=!city&&!!s&&s.blocks.some(b=>b.id===sel);
  const who=live.who||(typeof CLASSES!=='undefined'&&state.classId&&(CLASSES.find(c=>c.id===state.classId)||{}).name)||'';
  /* the header: who you are becoming, and how many of today's blocks are laid (habits you are building only) */
  const roll=!off&&view.n>=0&&live.built>view.n;view.n=live.built;
  put(ui.hud,'<button class="hb-who" data-act="who"><span class="hb-tag">Becoming</span><b>'+esc(who||'Tap to say who')+'</b></button>'+
   (live.due?'<div class="hb-count"><strong'+(roll?' class="roll"':'')+'><b>'+live.built+'</b></strong> <span>/ '+live.due+' today'+(combo>1?' · <em>combo ×'+combo+'</em>':'')+'</span></div>':''));
  /* the scene: the sky is drawn once for each time of day, each tower keeps its node and only its inside is redrawn when it changes */
  const c=clock(),pal=PAL[c.ph],fitc=cityFit(n),keep={};
  if(ui.ph!==c.ph){ui.ph=c.ph;ui.bg.innerHTML=skyHtml(c.ph)}
  ui.bg.querySelector('.hb-orb').setAttribute('transform','translate('+n1(c.x)+','+n1(c.y)+')');
  ui.sky.classList.toggle('sel',picked);ui.sky.classList.toggle('city',city);
  (n?m.stacks:[{id:'+'}]).forEach((st,i)=>{
   let g=nodes[st.id],html;keep[st.id]=1;
   if(!g){g=nodes[st.id]=document.createElementNS('http://www.w3.org/2000/svg','g');g.setAttribute('class','hb-tower');g.dataset.id=st.id;ui.tw.appendChild(g)}
   if(n){
    const t=g._t=tower(st,!city&&i===focus,m.live,pal),k=Math.min(fitc.k,290/t.height);
    html=t.svg+(city?'<text class="hb-city" text-anchor="middle" data-fit="'+n1((n>1?fitc.gap-6:300)/k)+'" x="0" y="'+n1(D*.3+5+13/k)+'" style="font-size:'+n1(10.5/k)+'px" fill="'+pal.ink+'">'+esc(fit(st.cue,20))+'</text>':'');
   }else{
    html=plot(1.7,pal)+'<g class="hb-blk hb-ghost" data-act="newstack" role="button" tabindex="0" aria-label="Lay your first foundation"><polygon points="0,'+(-2*D)+' '+HW+','+(-D)+' 0,0 '+(-HW)+','+(-D)+'"/><text class="hb-sign" text-anchor="middle" x="0" y="'+(-D+4)+'">+ lay a foundation</text></g>';
    g.style.transform='translate(200px,'+BASE+'px) scale(1.15)';
   }
   if(put(g,html))fitText(g);
  });
  for(const id in nodes)if(!keep[id]){nodes[id].remove();delete nodes[id]}
  view.m=m;view.live=live;
  if(n)place(view.city!==city);
  view.city=city;
  const arrow=(a,l,d)=>'<button class="hb-nav '+a+'" data-act="'+a+'" aria-label="'+l+'"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="'+d+'"/></svg></button>';
  put(ui.over,(n>1&&!city?(focus>0?arrow('prev','Previous stack','M15 4l-8 8 8 8'):'')+(focus<n-1?arrow('next','Next stack','M9 4l8 8-8 8'):''):'')+
   (n?'<button class="hb-view" data-act="view">'+(city?'Tower view':'City view')+'</button>':'')+
   (city?'<button class="hb-view left" data-act="newstack">+ Foundation</button>':'<div class="hb-dots">'+m.stacks.map((x,i)=>'<i class="'+(i===focus?'on':'')+'"></i>').join('')+'</div>'));
  const st=strikeHtml(live);
  put(ui.below,city?'<div class="hb-lapse"><div class="r"><span>Time-lapse</span><b></b></div><input type="range" min="0" max="'+MAXW+'" data-act="lapse" aria-label="Weeks ago"><div class="r"><span>'+MAXW+' weeks ago</span><span>today</span></div></div>':
   '<button class="'+st[0]+'" data-act="'+st[1]+'"'+(st[1]?'':' disabled')+'><span><small>'+esc(st[2])+'</small><b>'+esc(st[3])+'</b></span><span class="k">'+st[4]+'</span></button>'+
   (!picked&&live.stacks.some(x=>x.blocks.some(b=>b.kind!=='fade'))?'<button class="hb-rough'+(live.rough?' on':'')+'" data-act="rough">'+(live.rough?'Rough day is on · blocks are 2 minutes today':'Rough day? Shrink today’s blocks to 2 minutes')+'</button>':'')+
   '<div class="hb-tune">'+tuneHtml(live)+'</div>');
  if(city){ui.below.querySelector('.hb-lapse b').textContent=weeks?weeks+(weeks===1?' week ago':' weeks ago'):'Today';ui.below.querySelector('[data-act=lapse]').value=MAXW-weeks}
  laid=felled=upg=0;
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
   picks:s?null:STARTERS.filter(c=>!read().stacks.some(x=>x.cue.toLowerCase()===c.toLowerCase())).map(c=>({v:c,t:c})),
   pick(v){if(api.addStack(v)){focus=read().stacks.length-1;city=false;shut()}},
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
 function go(d){const n=read().stacks.length;if(city||!n)return;const to=Math.max(0,Math.min(n-1,focus+d));view.pan=null;if(to!==focus){focus=to;sel=null;combo=0;buzz('tick')}render()}
 const buzz=k=>{try{if(typeof lmaBuzz==='function')lmaBuzz(k)}catch(e){}};
 function act(a,el){
  const m=model(),s=m.stacks[focus],b=s&&s.blocks.find(x=>x.id===sel);
  view.pan=null;
  if(a==='prev')return go(-1);
  if(a==='next')return go(1);
  if(a==='view'){city=!city;weeks=0;sel=null;return render()}
  if(a==='newstack')return stackSheet(null);
  if(a==='new')return habitSheet(s.id,'build');
  if(a==='goto'){const more=m.stacks.findIndex((x,i)=>i!==focus&&x.next);if(more>=0){focus=more;sel=null;combo=0}return render()}
  if(a==='lay'&&s&&s.next){
   const nx=s.next;
   if(api.lay(nx.id)){
    if(nx.kind!=='fade'){laid=nx.id;combo++;const now=matAt(nx.reps+1);if(now!==nx.mat){upg=nx.id;toast(nx.names[1]+' is now '+now+(now==='gold'?'. It runs itself.':''))}}
    else{felled=nx.id;if(nx.left===1)toast(nx.names[1]+' is gone. A tree stands where it was.')}
    buzz('end');
   }
   return render();
  }
  if(a==='claim'&&s){if(api.claim(s.id))buzz('end');return render()}
  if(a==='rough'){api.setRough(!m.rough);return render()}
  if(a==='who')return open({title:'Who are you becoming?',lead:'Every block you lay is a vote for this person.',fields:[{label:'I am becoming',value:read().who,hint:'a reader, an athlete',max:40}],save(v){if(api.setWho(v[0]))shut()}});
  if(a==='shut')return shut();
  if(a==='pick'&&sheet&&sheet.pick)return sheet.pick(el.dataset.v);
  if(a==='danger'&&sheet&&sheet.drop)return sheet.drop();
  if(!b)return;
  if(a==='earlier'||a==='later'){api.move(b.id,a==='earlier'?-1:1);buzz('tick');return render()}
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
  if(moved&&e.timeStamp-moved<350&&e.target.closest('.hb-sky'))return; /* the end of a drag is not a tap */
  const t=e.target.closest('[data-act]');
  if(t&&(root.contains(t)||host.contains(t))){if(t.dataset.act!=='lapse')act(t.dataset.act,t);return}
  const tw=e.target.closest('.hb-tower');if(!tw)return;
  const i=+tw.dataset.i;view.pan=null;
  if(city){city=false;weeks=0;focus=i;sel=null;return render()}
  if(i!==focus){focus=i;sel=null;combo=0;return render()}
  const g=e.target.closest('[data-new],[data-b],[data-stack],[data-treat]');if(!g)return;
  if(g.dataset.new)return habitSheet(g.dataset.new,'build');
  if(g.dataset.treat)return stackSheet(g.dataset.treat);
  if(g.dataset.stack)return stackSheet(g.dataset.stack);
  sel=sel===g.dataset.b?null:g.dataset.b;render();
 }
 /* a sideways swipe on the scene changes stack; an up-and-down drag scrolls a tower too tall to fit (when it fits, the page scrolls) */
 function onMove(e){
  if(!drag||(e.pointerType==='mouse'&&!e.buttons)){drag=null;return}
  const dx=e.clientX-drag.x,dy=e.clientY-drag.y;
  if(!drag.way&&Math.max(Math.abs(dx),Math.abs(dy))>8){drag.way=Math.abs(dx)>Math.abs(dy)?'x':'y';if(drag.way==='y'&&view.max>0)ui.sky.classList.add('drag')}
  if(drag.way==='y'&&view.max>0&&!city){view.pan=clamp(drag.pan+dy*400/drag.w,0,view.max);place(false)}
 }
 function onLift(e){
  if(!drag)return;
  const d=drag,dx=e.clientX-d.x;drag=null;ui.sky.classList.remove('drag');
  moved=d.way?e.timeStamp:0;
  if(e.type==='pointerup'&&d.way==='x'&&Math.abs(dx)>40)go(dx<0?1:-1);
 }
 function mount(el){
  if(!root){
   root=el.querySelector('.hb')||el;
   host=document.createElement('div');host.className='hb hb-host';document.body.appendChild(host);
   root.addEventListener('click',onClick);host.addEventListener('click',onClick);
   host.addEventListener('submit',e=>{e.preventDefault();if(sheet&&sheet.save)sheet.save(vals())});
   root.addEventListener('input',e=>{if(e.target.dataset.act==='lapse'){weeks=MAXW-(+e.target.value);render()}});
   root.addEventListener('keydown',e=>{if(e.key==='Enter'&&e.target.closest&&e.target.closest('.hb-blk,.hb-tree'))e.target.dispatchEvent(new MouseEvent('click',{bubbles:true}))});
   root.addEventListener('pointerdown',e=>{drag=ui&&e.target.closest('.hb-sky')&&!e.target.closest('button')?{x:e.clientX,y:e.clientY,pan:view.now,w:ui.sky.clientWidth||400,way:''}:null});
   root.addEventListener('pointermove',onMove);root.addEventListener('pointerup',onLift);root.addEventListener('pointercancel',onLift);
   const m=model(),due=m.stacks.findIndex(s=>s.next);if(due>=0)focus=due;
  }
  combo=0;render();
 }
 return Object.assign({mount,render},api);
})();
window.Habits=HabitsFeature;
if(window.LifeMaxFeatures)LifeMaxFeatures.register({id:'habits',mount:el=>HabitsFeature.mount(el)});
