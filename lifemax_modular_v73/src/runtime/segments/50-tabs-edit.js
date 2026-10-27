/* ===== Edit the bottom bar: order, which tabs show, and optional extras =====
   Works on the real buttons, so swiping between tabs and scrubbing along the bar follow the new order automatically. */
var LMA_TAB_EXTRA=[{view:'settings',label:'Settings',icon:'gear'}];
var LMA_TAB_DEFAULT=null;
function lmaTabsBar(){return document.querySelector('.sidebar')}
function lmaTabsButtons(){const sb=lmaTabsBar();return sb?[].slice.call(sb.querySelectorAll('.navbtn[data-view]')):[]}
function lmaTabsCfg(){const t=lmaLoad().tabs;return {order:t&&Array.isArray(t.order)?t.order:[],hidden:t&&Array.isArray(t.hidden)?t.hidden:[],extra:t&&Array.isArray(t.extra)?t.extra:[]}}
function lmaTabsStore(c){lmaLoad().tabs=c;lmaSave()}
function lmaTabsApply(){
 const sb=lmaTabsBar();if(!sb)return;const c=lmaTabsCfg();
 if(!LMA_TAB_DEFAULT)LMA_TAB_DEFAULT=lmaTabsButtons().filter(function(b){return !b.dataset.extra}).map(function(b){return b.dataset.view});
 const note=sb.querySelector('.sidebar-note');
 LMA_TAB_EXTRA.forEach(function(x){
  let b=sb.querySelector('.navbtn[data-view="'+x.view+'"]');
  if(c.extra.indexOf(x.view)>=0){
   if(!b){b=document.createElement('button');b.className='navbtn';b.dataset.view=x.view;b.dataset.extra='1';b.setAttribute('onclick',"navigate('"+x.view+"')");b.innerHTML='<span class="ico">'+px(x.icon,18)+'</span>'+x.label;sb.insertBefore(b,note||null)}
  }else if(b&&b.dataset.extra){b.remove()}
 });
 const all=lmaTabsButtons(),views=all.map(function(b){return b.dataset.view}),ord=c.order.filter(function(v){return views.indexOf(v)>=0});
 const sorted=ord.concat(views.filter(function(v){return ord.indexOf(v)<0}));
 sorted.forEach(function(v){const b=sb.querySelector('.navbtn[data-view="'+v+'"]');if(b)sb.insertBefore(b,note||null)});
 const hid=c.hidden.filter(function(v){return views.indexOf(v)>=0});
 const hideOk=views.length-hid.length>=2;
 all.forEach(function(b){b.classList.toggle('tab-off',hideOk&&hid.indexOf(b.dataset.view)>=0)});
 try{lmaSyncActive()}catch(e){}
}
function lmaTabsRender(){
 const el=document.getElementById('tabsEditBody');if(!el)return;
 const c=lmaTabsCfg(),bs=lmaTabsButtons();
 const row=function(v,label,ico,on,i,n,extra){return '<div class="settingsline tabrow'+(on?'':' off')+'"><span class="tabrow-ico">'+ico+'</span><div style="flex:1;min-width:0"><strong>'+esc(label)+'</strong></div>'
  +(extra?'':'<button class="tb" type="button" onclick="lmaTabMove(\''+v+'\',-1)" aria-label="Move '+esc(label)+' earlier"'+(i===0?' disabled':'')+'>&#9664;</button><button class="tb" type="button" onclick="lmaTabMove(\''+v+'\',1)" aria-label="Move '+esc(label)+' later"'+(i===n-1?' disabled':'')+'>&#9654;</button>')
  +'<input type="checkbox" class="check" '+(on?'checked ':'')+'onchange="lmaTabShow(\''+v+'\',this.checked)" aria-label="Show '+esc(label)+' in the bar"></div>'};
 const n=bs.length;let h=bs.map(function(b,i){const v=b.dataset.view,ico=(b.querySelector('.ico')||{}).innerHTML||'';return row(v,b.textContent.trim(),ico,!b.classList.contains('tab-off'),i,n,false)}).join('');
 LMA_TAB_EXTRA.forEach(function(x){if(c.extra.indexOf(x.view)<0)h+=row(x.view,x.label,px(x.icon,18),false,0,0,true)});
 el.innerHTML=h;
}
function lmaTabMove(v,d){
 const order=lmaTabsButtons().map(function(b){return b.dataset.view}),i=order.indexOf(v),j=i+d;if(i<0||j<0||j>=order.length)return;
 order.splice(j,0,order.splice(i,1)[0]);const c=lmaTabsCfg();c.order=order;lmaTabsStore(c);lmaTabsApply();lmaTabsRender();
}
function lmaTabShow(v,on){
 const c=lmaTabsCfg(),isExtra=LMA_TAB_EXTRA.some(function(x){return x.view===v});
 if(isExtra){const k=c.extra.indexOf(v);if(on&&k<0)c.extra.push(v);if(!on&&k>=0)c.extra.splice(k,1);if(on){const o=lmaTabsButtons().map(function(b){return b.dataset.view});c.order=o.filter(function(x){return x!==v}).concat([v])}}
 else{
  const visible=lmaTabsButtons().filter(function(b){return !b.classList.contains('tab-off')&&b.dataset.view!==v||(b.dataset.view===v&&on)}).length;
  if(!on&&visible<2){toast('Keep at least two tabs in the bar');lmaTabsRender();return}
  const k=c.hidden.indexOf(v);if(!on&&k<0)c.hidden.push(v);if(on&&k>=0)c.hidden.splice(k,1);
 }
 lmaTabsStore(c);lmaTabsApply();lmaTabsRender();
}
function lmaTabsReset(){lmaLoad().tabs=null;lmaSave();const sb=lmaTabsBar();if(sb){sb.querySelectorAll('.navbtn[data-extra]').forEach(function(b){b.remove()});const note=sb.querySelector('.sidebar-note');(LMA_TAB_DEFAULT||[]).forEach(function(v){const b=sb.querySelector('.navbtn[data-view="'+v+'"]');if(b){b.classList.remove('tab-off');sb.insertBefore(b,note||null)}})}lmaTabsApply();lmaTabsRender();toast('Bottom bar reset')}
setTimeout(function(){try{lmaTabsApply();lmaTabsRender()}catch(e){console.warn('[tabs]',e)}},0);
