/* ===== Settings categories: one group of cards at a time instead of one long list ===== */
var SET_CATS=[
 {id:'look',name:'Look & feel',cards:['Demo mode','Appearance','Bottom bar','Navigation','Tab previews','Cat graphics','Haptics','Quest effects','Guided workout']},
 {id:'native',name:'Native mode',cards:['Native tabs']},
 {id:'voice',name:'Voice & journal',cards:['Voice (beta)','Journal privacy']},
 {id:'phone',name:'Phone & sync',cards:['App updates','Home-screen widget','Health sync','Calendar import','Automatic backup','Notifications']},
 {id:'game',name:'Character & game',cards:['Character profile','Rewards','Decay system','XP rules']},
 {id:'data',name:'Data & advanced',cards:['Food database','Data management','Run a demo','Developer mode']}
];
/* The master switch changes the existing tab preferences through their own handlers. Individual choices remain independent. */
var LMA_NATIVE_TAB_TOGGLES=[
 'nativeOverviewToggle','nativeOverviewCardsToggle','nativeCharacterToggle','nativeNutritionToggle',
 'nativePetToggle','nativeLogToggle','nativePlanToggle','nativeDirectorToggle','nativeRecipesToggle',
 'nativeLibraryToggle','nativeJournalToggle','nativeSettingsToggle'
];
function lmaSyncNativeMode(){
 const master=document.getElementById('nativeModeToggle');if(!master)return;
 master.checked=LMA_NATIVE_TAB_TOGGLES.some(function(id){return !!document.getElementById(id)?.checked});
}
function lmaSetNativeMode(on){
 for(const id of LMA_NATIVE_TAB_TOGGLES){
  const box=document.getElementById(id);if(!box||box.disabled||box.checked===!!on)continue;
  box.checked=!!on;
  box.dispatchEvent(new Event('change',{bubbles:true}));
 }
 lmaSyncNativeMode();
}
var setCat=(function(){let id=null;try{id=localStorage.getItem('lma_set_cat')}catch(e){}return SET_CATS.some(function(c){return c.id===id})?id:SET_CATS[0].id})(); // a stale or broken value would hide every card
function setCatOf(card){const h=card.querySelector(':scope>h3');if(!h)return null;const t=h.textContent.trim();const c=SET_CATS.find(function(x){return x.cards.indexOf(t)>=0});return c?c.id:null}
function setApplyCats(){
 const v=document.getElementById('view-settings');if(!v)return;
 let bar=document.getElementById('setCatBar');
 if(!bar){
  bar=document.createElement('div');bar.id='setCatBar';bar.className='set-cats';bar.setAttribute('role','tablist');
  bar.innerHTML=SET_CATS.map(function(c){return '<button type="button" role="tab" data-cat="'+c.id+'" onclick="setPickCat(\''+c.id+'\')">'+c.name+'</button>'}).join('');
  const head=v.querySelector('.pagehead');head?head.after(bar):v.prepend(bar);
 }
 v.querySelectorAll('.card').forEach(function(card){
  if(card.closest('.card')!==card)return;
  const c=setCatOf(card);if(c)card.dataset.setcat=c;
  card.classList.toggle('set-hide',!!c&&c!==setCat);
 });
 v.querySelectorAll('.grid.two-col').forEach(function(g){g.classList.toggle('set-hide',!g.querySelector('.card:not(.set-hide)'))});
 bar.querySelectorAll('button').forEach(function(b){const on=b.dataset.cat===setCat;b.classList.toggle('on',on);b.setAttribute('aria-selected',on)});
 lmaSyncNativeMode();
}
function setPickCat(id){setCat=id;try{localStorage.setItem('lma_set_cat',id)}catch(e){}setApplyCats();const bar=document.getElementById('setCatBar');if(bar)bar.scrollIntoView({block:'nearest'})}
(function(){
 const v=document.getElementById('view-settings');if(!v)return;
 v.addEventListener('change',function(e){if(LMA_NATIVE_TAB_TOGGLES.indexOf(e.target?.id)>=0)queueMicrotask(lmaSyncNativeMode)});
 let busy=false;
 new MutationObserver(function(){if(busy)return;busy=true;try{setApplyCats()}finally{setTimeout(function(){busy=false},0)}}).observe(v,{childList:true,subtree:true});
 setApplyCats();
})();
