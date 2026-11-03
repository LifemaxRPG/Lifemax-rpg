/* ===== Edit the bottom bar: order, which tabs show, and optional extras =====
   Works on the real buttons, so swiping between tabs and scrubbing along the bar follow the new order automatically. */
var LMA_TAB_EXTRA=[{view:'settings',label:'Settings',icon:'gear'}];
var LMA_TAB_DEFAULT=null;
function lmaTabsBar(){return document.querySelector('.sidebar')}
function lmaTabsButtons(){const sb=lmaTabsBar();return sb?[].slice.call(sb.querySelectorAll('.navbtn[data-view]')):[]}
function lmaTabsCfg(){const t=lmaLoad().tabs;return {order:t&&Array.isArray(t.order)?t.order:[],hidden:t&&Array.isArray(t.hidden)?t.hidden:[],extra:t&&Array.isArray(t.extra)?t.extra:[]}}
function lmaTabsStore(c){c.qEnd=1;lmaLoad().tabs=c;lmaSave()}      /* an order the owner saved is theirs: it is never carried to a new default again */
/* The default order ends with Quests (the tabs a feature adds, such as Life Director, come before it); it used to sit between Log activity and
   Workout plan. A tab that is not in the saved order goes after the saved ones, Quests last of them. A saved order that is exactly the old
   default (the owner never moved a tab, the editor having stored the list as it was) follows the new default; any other saved order is the
   owner's own and stays as it is. Tabs added to the bar (Settings) keep their place after the others. Checked once per saved order. */
var LMA_TAB_LAST='quests';
var LMA_TAB_OLD_DEFAULT=['dashboard','character','food','pet','log','quests','plan','library','money'];
function lmaTabsCarried(order){
 const extra=function(v){return LMA_TAB_EXTRA.some(function(x){return x.view===v})},core=order.filter(function(v){return !extra(v)}),old=LMA_TAB_OLD_DEFAULT;
 const same=function(list){return core.length===list.length&&core.every(function(v,i){return v===list[i]})};
 const withDirector=same(old.concat('life-director'));
 if(!same(old)&&!withDirector)return order;
 const out=order.filter(function(v){return v!==LMA_TAB_LAST});
 if(withDirector)out.splice(out.map(extra).lastIndexOf(false)+1,0,LMA_TAB_LAST);       /* an order saved before Life Director had a tab leaves Quests out: it then falls last by itself */
 return out;
}
function lmaTabsMigrate(){
 const t=lmaLoad().tabs;if(!t||t.qEnd)return;
 t.qEnd=1;if(Array.isArray(t.order))t.order=lmaTabsCarried(t.order);lmaSave();
}
function lmaTabsApply(){
 const sb=lmaTabsBar();if(!sb)return;lmaTabsMigrate();const c=lmaTabsCfg();
 if(!LMA_TAB_DEFAULT){const v=lmaTabsButtons().filter(function(b){return !b.dataset.extra}).map(function(b){return b.dataset.view});LMA_TAB_DEFAULT=v.filter(function(x){return x!==LMA_TAB_LAST}).concat(v.filter(function(x){return x===LMA_TAB_LAST}))}
 const note=sb.querySelector('.sidebar-note');
 LMA_TAB_EXTRA.forEach(function(x){
  let b=sb.querySelector('.navbtn[data-view="'+x.view+'"]');
  if(c.extra.indexOf(x.view)>=0){
   if(!b){b=document.createElement('button');b.className='navbtn';b.dataset.view=x.view;b.dataset.extra='1';b.setAttribute('onclick',"navigate('"+x.view+"')");b.innerHTML='<span class="ico">'+px(x.icon,18)+'</span>'+x.label;sb.insertBefore(b,note||null)}
  }else if(b&&b.dataset.extra){b.remove()}
 });
 const all=lmaTabsButtons(),views=all.map(function(b){return b.dataset.view}),ord=c.order.filter(function(v){return views.indexOf(v)>=0});
 const rest=views.filter(function(v){return ord.indexOf(v)<0}),sorted=ord.concat(rest.filter(function(v){return v!==LMA_TAB_LAST}),rest.filter(function(v){return v===LMA_TAB_LAST}));
 sorted.forEach(function(v){const b=sb.querySelector('.navbtn[data-view="'+v+'"]');if(b)sb.insertBefore(b,note||null)});
 const hid=c.hidden.filter(function(v){return views.indexOf(v)>=0});
 const hideOk=views.length-hid.length>=2;
 all.forEach(function(b){b.classList.toggle('tab-off',hideOk&&hid.indexOf(b.dataset.view)>=0)});
 try{lmaSyncActive()}catch(e){}
}
function lmaTabsRender(){
 const el=document.getElementById('tabsEditBody');if(!el)return;
 const c=lmaTabsCfg(),bs=lmaTabsButtons();
 const row=function(v,label,ico,on,i,n,extra){return '<div class="settingsline tabrow'+(on?'':' off')+'" data-view="'+v+'"><span class="tabrow-ico">'+ico+'</span><div style="flex:1;min-width:0"><strong>'+esc(label)+'</strong></div>'
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
