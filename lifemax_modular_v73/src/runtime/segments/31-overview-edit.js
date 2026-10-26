
/* ===== Overview: edit layout (v85) =====
   The Overview is a column of tiles (.ov-tile[data-tile] inside #ovTiles). Edit turns the
   column into a short list of named rows: drag a row by its grip (or focus the grip and use
   the arrow keys) to reorder, tap the eye to hide or show a tile. Changes apply as you make
   them; Done closes the list. Saved in state.ovLayout = {order:[ids], hidden:[ids]}; tiles
   added in later versions slot in at their default place. Reset restores the default. */
const OV_TILES=[
  {id:'plan',name:'Today\u2019s plan',sub:'Life Director\u2019s live day dial',icon:'clock',col:'#38bdf8'},
  {id:'next',name:'Next moves',sub:'The most useful things to do now',icon:'bolt',col:'var(--accent)'},
  {id:'hero',name:'Level & today',sub:'Today\u2019s XP ring, level and streak',icon:'star',col:'var(--gold)'},
  {id:'attrs',name:'Attributes',sub:'All eight stats at a glance',icon:'target',col:'#a78bfa'},
  {id:'week',name:'This week',sub:'XP day by day',icon:'grid',col:'var(--good)'},
  {id:'cat',name:'Cat',sub:'Your companion\u2019s room',icon:'paw',col:'#ff7a9a'},
  {id:'luck',name:'Daily luck',sub:'Today\u2019s dice roll',icon:'dice',col:'#ffd35c'},
  {id:'recent',name:'Recent activity',sub:'Your last few logs',icon:'list',col:'#94a3b8'}
];
const OV_DEFAULT=OV_TILES.map(function(t){return t.id});
function ovLayout(){
  const L=state.ovLayout||{},known=OV_DEFAULT;
  let order=(Array.isArray(L.order)?L.order:[]).filter(function(id,i,a){return known.indexOf(id)>=0&&a.indexOf(id)===i});
  /* a tile this save has never seen goes back to its default neighbour */
  known.forEach(function(id,i){if(order.indexOf(id)<0){const before=known.slice(0,i).reverse().find(function(x){return order.indexOf(x)>=0});order.splice(before?order.indexOf(before)+1:0,0,id)}});
  const hidden=(Array.isArray(L.hidden)?L.hidden:[]).filter(function(id){return known.indexOf(id)>=0});
  return {order:order,hidden:hidden};
}
function ovLayoutSave(L){state.ovLayout={order:L.order.slice(),hidden:L.hidden.slice()};try{localStorage.setItem(STORE,JSON.stringify(state))}catch(e){console.warn('Save failed',e)}}
function ovLayoutApply(){
  const box=document.getElementById('ovTiles');if(!box)return;
  const L=ovLayout();
  L.order.forEach(function(id){const t=box.querySelector(':scope>.ov-tile[data-tile="'+id+'"]');if(t){box.appendChild(t);t.classList.toggle('ov-off',L.hidden.indexOf(id)>=0)}});
}
/* why a tile might be empty even when shown */
function ovTileNote(id){
  if(id==='cat'&&!petData())return 'Appears once you adopt a cat';
  if(id==='plan'&&!(window.LifeDirector&&typeof LifeDirector.glance==='function'))return 'Needs the Life Director feature';
  return '';
}
function ovEditRowsHtml(){
  const L=ovLayout();
  return L.order.map(function(id){
    const t=OV_TILES.find(function(x){return x.id===id}),off=L.hidden.indexOf(id)>=0,note=ovTileNote(id);
    let sub=t.sub;if(id==='cat'&&petData())sub=petData().name+'\u2019s room';
    return '<li class="ov-er'+(off?' off':'')+'" data-tile="'+id+'" style="--c:'+t.col+'">'
      +'<span class="ov-er-ic">'+px(t.icon,16)+'</span>'
      +'<span class="ov-er-t"><b>'+esc(t.name)+'</b><small>'+esc(off?'Hidden':(note||sub))+'</small></span>'
      +'<button type="button" class="ov-er-eye" aria-pressed="'+(!off)+'" aria-label="'+(off?'Show ':'Hide ')+esc(t.name)+'" onclick="ovEditToggle(\''+id+'\')">'+ovEye(!off)+'</button>'
      +'<span class="ov-er-grip" role="button" tabindex="0" aria-label="Move '+esc(t.name)+'. Arrow keys move it up or down."><i></i><i></i><i></i></span></li>'}).join('');
}
function ovEye(on){return '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>'+(on?'':'<path d="M3 3l18 18"/>')+'</svg>'}
function ovEditOpen(){
  const ed=document.getElementById('ovEdit'),box=document.getElementById('ovTiles'),view=document.getElementById('view-dashboard');if(!ed||!box)return;
  if(!ed.hidden)return ovEditClose();
  ed.innerHTML='<div class="ov-edit-h"><div><h3>Edit Overview</h3><small>Drag to reorder. Tap the eye to hide a tile.</small></div><button type="button" class="btn ov-edit-done" onclick="ovEditClose()">Done</button></div>'
    +'<ol class="ov-edit-list" id="ovEditList">'+ovEditRowsHtml()+'</ol>'
    +'<button type="button" class="ov-edit-reset" onclick="ovEditReset()">Reset to default</button>';
  ed.hidden=false;box.hidden=true;view.classList.add('ov-editing');
  const b=document.getElementById('ovEditBtn');if(b){b.classList.add('on');b.setAttribute('aria-expanded','true')}
  ovEditWire(document.getElementById('ovEditList'));
  if(!fxReduced()){ed.classList.remove('ov-edit-in');void ed.offsetWidth;ed.classList.add('ov-edit-in')}
  try{ed.scrollIntoView({block:'nearest'})}catch(e){}
  haptic(8);
}
function ovEditClose(quiet){
  const ed=document.getElementById('ovEdit'),box=document.getElementById('ovTiles'),view=document.getElementById('view-dashboard');if(!ed||ed.hidden)return;
  ed.hidden=true;ed.innerHTML='';box.hidden=false;view.classList.remove('ov-editing');
  const b=document.getElementById('ovEditBtn');if(b){b.classList.remove('on');b.setAttribute('aria-expanded','false')}
  ovLayoutApply();
  if(quiet)return;
  try{ovReplay()}catch(e){}
  window.scrollTo({top:0,behavior:fxReduced()?'auto':'smooth'});
}
function ovEditToggle(id){
  const L=ovLayout(),i=L.hidden.indexOf(id);
  if(i>=0)L.hidden.splice(i,1);else L.hidden.push(id);
  ovLayoutSave(L);ovLayoutApply();
  const row=document.querySelector('#ovEditList .ov-er[data-tile="'+id+'"]');
  if(row){const off=L.hidden.indexOf(id)>=0,t=OV_TILES.find(function(x){return x.id===id});row.classList.toggle('off',off);const e=row.querySelector('.ov-er-eye');e.innerHTML=ovEye(!off);e.setAttribute('aria-pressed',String(!off));e.setAttribute('aria-label',(off?'Show ':'Hide ')+t.name);
    let sub=t.sub;if(id==='cat'&&petData())sub=petData().name+'\u2019s room';row.querySelector('small').textContent=off?'Hidden':(ovTileNote(id)||sub)}
  haptic(6);
}
function ovEditReset(){
  state.ovLayout=null;ovLayoutSave(ovLayout());ovLayoutApply();
  const list=document.getElementById('ovEditList');if(list){ovEditFlip(list,function(){list.innerHTML=ovEditRowsHtml()});ovEditWire(list)}
  haptic([8,30,8]);
}
/* move rows with a FLIP so the list glides into its new order */
function ovEditFlip(list,change){
  const before={};list.querySelectorAll('.ov-er').forEach(function(r){before[r.dataset.tile]=r.getBoundingClientRect().top});
  change();
  if(fxReduced())return;
  list.querySelectorAll('.ov-er').forEach(function(r){const b=before[r.dataset.tile];if(b==null)return;const d=b-r.getBoundingClientRect().top;if(!d)return;r.animate([{transform:'translateY('+d+'px)'},{transform:'none'}],{duration:260,easing:'cubic-bezier(.2,.8,.2,1)'})});
}
function ovEditCommit(list){const L=ovLayout();L.order=[].map.call(list.querySelectorAll('.ov-er'),function(r){return r.dataset.tile});ovLayoutSave(L);ovLayoutApply()}
function ovEditWire(list){
  if(!list||list.__wired)return;list.__wired=true;
  /* keyboard: arrows on a grip move that row */
  list.addEventListener('keydown',function(e){
    const g=e.target.closest('.ov-er-grip');if(!g||(e.key!=='ArrowUp'&&e.key!=='ArrowDown'))return;
    e.preventDefault();const row=g.closest('.ov-er'),sib=e.key==='ArrowUp'?row.previousElementSibling:row.nextElementSibling;if(!sib)return;
    ovEditFlip(list,function(){if(e.key==='ArrowUp')list.insertBefore(row,sib);else list.insertBefore(sib,row)});
    ovEditCommit(list);row.querySelector('.ov-er-grip').focus();haptic(5);
  });
  /* drag by the grip (or anywhere on the row with a mouse) */
  let drag=null,hold=0;
  function start(row,e){
    const rows=[].slice.call(list.querySelectorAll('.ov-er')),r=row.getBoundingClientRect(),gap=rows.length>1?rows[1].getBoundingClientRect().top-rows[0].getBoundingClientRect().bottom:8;
    drag={row:row,rows:rows,from:rows.indexOf(row),to:rows.indexOf(row),y0:e.clientY,step:r.height+gap,id:e.pointerId,mids:rows.map(function(x){const b=x.getBoundingClientRect();return b.top+b.height/2})};
    row.classList.add('lift');list.classList.add('dragging');
    try{row.setPointerCapture(e.pointerId)}catch(err){}
    haptic(10);
  }
  function move(e){
    if(!drag||e.pointerId!==drag.id)return;
    e.preventDefault();
    const dy=e.clientY-drag.y0,y=drag.mids[drag.from]+dy;
    drag.row.style.transform='translateY('+dy+'px) scale(1.03)';
    let to=drag.from;
    for(let i=0;i<drag.mids.length;i++){if(i<drag.from&&y<drag.mids[i]){to=i;break}if(i>drag.from&&y>drag.mids[i])to=i}
    if(to!==drag.to){drag.to=to;haptic(4)}
    drag.rows.forEach(function(x,i){if(x===drag.row)return;let s=0;if(drag.from<to&&i>drag.from&&i<=to)s=-drag.step;if(drag.from>to&&i<drag.from&&i>=to)s=drag.step;x.style.transform=s?'translateY('+s+'px)':''});
    /* keep the lifted row on screen */
    const vh=window.innerHeight;if(e.clientY>vh-110)window.scrollBy(0,8);else if(e.clientY<90)window.scrollBy(0,-8);
  }
  function end(e){
    clearTimeout(hold);
    if(!drag||(e&&e.pointerId!==drag.id))return;
    const d=drag;drag=null;
    const target=d.rows[d.to];
    d.rows.forEach(function(x){x.style.transition='none';x.style.transform=''});
    const lifted=d.row.getBoundingClientRect().top;
    if(d.to!==d.from){if(d.to>d.from)list.insertBefore(d.row,target.nextSibling);else list.insertBefore(d.row,target)}
    void list.offsetWidth;d.rows.forEach(function(x){x.style.transition=''});
    d.row.classList.remove('lift');list.classList.remove('dragging');
    if(!fxReduced()){const off=lifted-d.row.getBoundingClientRect().top;if(off)d.row.animate([{transform:'translateY('+off+'px) scale(1.03)'},{transform:'none'}],{duration:220,easing:'cubic-bezier(.2,.8,.2,1)'})}
    if(d.to!==d.from){ovEditCommit(list);haptic(8)}
  }
  list.addEventListener('pointerdown',function(e){
    const row=e.target.closest('.ov-er');if(!row||e.target.closest('.ov-er-eye'))return;
    if(e.target.closest('.ov-er-grip')){e.preventDefault();start(row,e);return}
    /* with a mouse the whole row is a handle; on touch only the grip, so the list still scrolls */
    if(e.pointerType==='mouse'&&e.button===0){e.preventDefault();start(row,e)}
  });
  list.addEventListener('pointermove',move,{passive:false});
  list.addEventListener('pointerup',end);list.addEventListener('pointercancel',end);
  list.addEventListener('contextmenu',function(e){if(e.target.closest('.ov-er'))e.preventDefault()});
}
document.addEventListener('keydown',function(e){if(e.key==='Escape'){const ed=document.getElementById('ovEdit');if(ed&&!ed.hidden)ovEditClose()}});
try{ovLayoutApply()}catch(e){console.warn('overview layout',e)}
/* leaving the Overview closes the editor (changes are already saved) */
(function(){const nav=navigate;navigate=function(view){if(view!=='dashboard')ovEditClose(true);return nav.apply(this,arguments)}})();
