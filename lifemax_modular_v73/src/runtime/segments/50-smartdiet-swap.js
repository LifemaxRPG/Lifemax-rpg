/* ============================================================================
   Smart Diet's swap sheet, as data. The page's own sheet (sdOpenSwap in 50-smartdiet.js) and native Food's swap sheet (the `swap`
   field of the projection in 50-native-nutrition.js) both read sdSwapView, so the alternatives, their order and the words are
   decided once (one decision, two drawings). sdSwapApply is the one place a swap is made: both sheets' buttons call it, and it
   says whether the swap was saved (a swap that could not be stored is taken back and the snackbar says why).
   ============================================================================ */

/* what the sheet for the plan's meal i on day shows: its words, the other meals of the same kind (as the day's plan would
   offer them for the diet and food preferences) and the Back to the plan button while the meal is swapped; null for a meal the
   day has not */
function sdSwapView(day,i){
  const d=sdDay(day);if(!d||!d.items[i])return null;
  const cur=d.items[i].meal,P=d.P,S=sdS(),kind=['snack','pre_workout','post_workout'];
  const same=function(m){return m.slot===cur.slot||(kind.indexOf(cur.slot)>=0&&kind.indexOf(m.slot)>=0&&m.slot===cur.slot)};
  const rows=Object.keys(P.meals).map(function(k){return P.meals[k]}).filter(same).filter(function(m){return m.id!==cur.id&&sdMealOk(m,S)}).map(function(m){
    const t=sdTotals(sdMealIngs(m,S),d.s);
    return {id:m.id,name:m.name,detail:(m.active_min||0)+' min · P '+Math.round(t.p)+' C '+Math.round(t.c)+' F '+Math.round(t.f)+((m.tags||[]).indexOf('leftover')>=0?' · leftover':''),kcal:fv2Fmt(t.kcal),unit:'kcal'}});
  return {index:i,title:'Swap '+sdSlotName(cur.slot).toLowerCase(),lead:'Instead of ',name:cur.name,tail:'. The rest of the day rescales to keep your target.',
    rows:rows,none:rows.length?null:'No other options',reset:(S.over[day]&&S.over[day][i])?'Back to the plan':null,close:'Close'};
}

/* Swaps the plan's meal i on day for the meal id (null: back to the plan). Whether it was saved; false too for a meal the sheet
   does not offer (the plan changed under the tap). undoable adds the snackbar with Undo that native Food shows. */
function sdSwapApply(day,i,id,undoable){
  const v=sdSwapView(day,i);if(!v)return false;
  const row=id===null?null:v.rows.find(function(r){return r.id===id});
  if(id===null?!v.reset:!row)return false;
  const S=sdS(),had=!!(S.over[day]&&S.over[day][i]),was=had?S.over[day][i]:null;
  const put=function(has,val){const m=(S.over[day]=S.over[day]||{});if(has)m[i]=val;else delete m[i]};
  put(id!==null,id);
  if(!fv2Saved(function(){put(had,was)}))return false;
  renderMeals();fv2Hap('tap');
  if(undoable)fv2Snack(row?'Swapped to '+row.name:v.reset,'Undo',function(){put(had,was);if(fv2Saved(function(){put(id!==null,id)}))renderMeals()});
  return true;
}
