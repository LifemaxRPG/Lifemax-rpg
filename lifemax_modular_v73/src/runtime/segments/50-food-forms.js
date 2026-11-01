/* ============================================================================
   The add-food sheet's words and rules, as data: what the page's own sheet (fv2Paint and its views in 50-food.js) draws and
   what native Food's sheet (the `sheet` projection in 50-native-nutrition.js) draws are read from here, so the two never
   disagree (one decision, two drawings). The writes (make a food, save the targets, change, remove or log again an entry) are
   here too, each answering whether the save succeeded; a refusal or a failed save says why in the page's own snackbar.
   ============================================================================ */

/* the sheet's head for a view: its title, and whether its one button steps Back (else it closes the sheet) */
function fv2HeadData(view){return view==='search'?{title:'Add food',back:false}:view==='portion'?{title:'Add food',back:true}
 :view==='edit'?{title:'Edit entry',back:false}:view==='quick'?{title:'Quick calories',back:true}
 :view==='create'?{title:'Create food',back:true}:{title:'Daily targets',back:false}}
/* the sheet's other words: the search box, the side actions, the buttons a screen reader names */
function fv2SheetWords(){return {search:'Search chicken, rice, yogurt…',quick:'Quick calories',create:'Create food',back:'Back',close:'Close',
 less:'Less',more:'More',grams:'grams',fav:'Favorite',kcal:'kcal',remove:'Remove',again:'Log today',searching:'Searching…'}}
/* the tabs of the empty search: [id, label] */
function fv2TabsData(){return [['recent','Recent'],['fav','Favorites'],['mine','My foods']]}

/* Quick calories: its two boxes and its button for the chosen meal */
function fv2QuickData(slot){return {kcal:{label:'Calories',hint:'e.g. 450'},name:{label:'Name',small:'(optional)',hint:'Restaurant lunch',max:60},
 save:'Add to '+fv2Slots().find(s=>s[0]===slot)[1]}}

/* Create food: the label's values for one serving. kind text or decimal; group 1 is the grid of four */
function fv2CreateData(){return {fields:[
  {key:'name',id:'fv2CN',kind:'text',label:'Name',max:80,hint:'Homemade protein shake'},
  {key:'brand',id:'fv2CB',kind:'text',label:'Brand',small:'(optional)',max:60},
  {key:'serving',id:'fv2CS',kind:'decimal',label:'Serving size',min:1,value:'100',unit:'grams'},
  {key:'kcal',id:'fv2CK',kind:'decimal',label:'Calories',min:0,group:1},
  {key:'p',id:'fv2CP',kind:'decimal',label:'Protein g',min:0,step:0.1,group:1},
  {key:'c',id:'fv2CC',kind:'decimal',label:'Carbs g',min:0,step:0.1,group:1},
  {key:'f',id:'fv2CF',kind:'decimal',label:'Fat g',min:0,step:0.1,group:1}],
 hint:'Enter the numbers for one serving, exactly as the label shows them.',save:'Save and continue'}}
/* a field of Create food as the page draws it */
function fv2CreateFieldHtml(f){const inp=f.kind==='text'?'<input id="'+f.id+'" type="text" maxlength="'+f.max+'"'+(f.hint?' placeholder="'+esc(f.hint)+'"':'')+'>'
  :'<input id="'+f.id+'" type="number" inputmode="decimal"'+(f.min!=null?' min="'+f.min+'"':'')+(f.step?' step="'+f.step+'"':'')+(f.value!=null?' value="'+f.value+'"':'')+'>';
 return '<label>'+esc(f.label)+(f.small?' <small>'+esc(f.small)+'</small>':'')+(f.unit?'<span class="u">'+inp+'<em>'+esc(f.unit)+'</em></span>':inp)+'</label>'}
/* Makes a food from its label values for one serving (v: the boxes' text by key) and keeps it in My foods. The food, or null
   when it has no name (the snackbar says so) or could not be saved. */
function fv2CreateFood(v){const t=k=>String(v[k]==null?'':v[k]),name=t('name').trim();if(!name){fv2Snack('Give it a name');return null}
 const sv=Math.max(1,+t('serving')||100),k=100/sv,r=x=>Math.round((+x||0)*k*100)/100;let kc=r(t('kcal'));const p=r(t('p')),c=r(t('c')),fa=r(t('f'));if(!kc&&(p||c||fa))kc=Math.round(p*4+c*4+fa*9);
 const f={id:'custom_'+Date.now(),n:name,brand:t('brand').trim(),cat:'Custom',kcal:kc,p,c,f:fa,fib:0,sug:0,na:0,port:sv===100?[]:[{l:'1 serving',g:sv}]};mealsEnsure();state.customFoods.unshift(f);
 if(!fv2Saved(()=>{state.customFoods=state.customFoods.filter(x=>x!==f)}))return null;return f}

/* The daily targets: the calories with their step, the presets, the three macros, water. With no calorie target yet the boxes
   start at 2000 kcal, balanced. */
const FV2_PRESETS=[['bal','Balanced','30 / 40 / 30',[.3,.4,.3]],['hi','High protein','40 / 35 / 25',[.4,.35,.25]],['low','Low carb','30 / 25 / 45',[.3,.25,.45]]];
function fv2GoalData(){const g=fv2Goals(),cal=g.cal||2000,m=fv2Mac();return {cal:{label:'calories a day',value:cal,step:50},
 presets:FV2_PRESETS.map(x=>({id:x[0],label:x[1],sub:x[2]})),
 fields:[['p','fv2GP',g.cal?g.p:Math.round(cal*.3/4)],['c','fv2GCb',g.cal?g.c:Math.round(cal*.4/4)],['f','fv2GF',g.cal?g.f:Math.round(cal*.3/9)]].map(x=>({key:x[0],id:x[1],label:m[x[0]][0]+' g',color:m[x[0]][1],value:x[2]})),
 water:{label:'Water goal',value:g.water,unit:'ml'},save:'Save targets'}}
/* the macros' grams a preset gives a calorie target (2000 kcal when none is typed) */
function fv2PresetGrams(p,cal){const sp=FV2_PRESETS.find(x=>x[0]===p)[3];cal=+cal||2000;return {p:Math.round(cal*sp[0]/4),c:Math.round(cal*sp[1]/4),f:Math.round(cal*sp[2]/9)}}
/* the calorie box after a step of d */
function fv2GoalStep(cal,d){return Math.max(0,(+cal||0)+d)}
/* what the macros add up to against the calorie target: the line under the boxes */
function fv2GoalSum(cal,p,c,f){const k=(+p||0)*4+(+c||0)*4+(+f||0)*9,cl=+cal||0;
 return {lead:'Your macros add up to ',strong:fv2Fmt(k)+' kcal',sep:cl?' · ':'',note:cl?(k>cl+30?fv2Fmt(k-cl)+' over your calorie target':k<cl-30?fv2Fmt(cl-k)+' under':'matches your target'):'',bad:!!cl&&k>cl+30}}
function fv2GoalSumHtml(s){return s.lead+'<b>'+s.strong+'</b>'+s.sep+(s.bad?'<span class="bad">'+s.note+'</span>':s.note)}
/* Saves the daily targets from the boxes' values (v: cal, p, c, f, water); whether they were saved */
function fv2SetGoals(v){const n=x=>Math.max(0,Math.round(+x||0));mealsEnsure();const m=state.mealsMeta,keys=['calGoal','protGoal','carbGoal','fatGoal','waterGoalMl'],was={};keys.forEach(k=>{was[k]=m[k]});
 m.calGoal=n(v.cal);m.protGoal=n(v.p);m.carbGoal=n(v.c);m.fatGoal=n(v.f);m.waterGoalMl=n(v.water)||2000;return fv2Saved(()=>{Object.assign(m,was)})}

/* An entry changed to an amount of its food and a meal; whether it was saved (else it is as it was) */
function fv2EditEntry(e,food,grams,slot){const s=Object.assign({grams:grams,slot:slot},scaleFood(food,grams)),was={};Object.keys(s).forEach(k=>{was[k]=e[k]});Object.assign(e,s);
 if(!fv2Saved(()=>{Object.assign(e,was)}))return false;fv2S().lastId=e.id;renderMeals();fv2Hap('success');return true}
/* Removes an entry, with Undo in the snackbar; whether it was saved */
function fv2RemoveEntry(e){const id=e.id,was=state.foodLog;state.foodLog=was.filter(x=>x.id!==id);if(!fv2Saved(()=>{state.foodLog=was}))return false;renderMeals();
 fv2Snack('Removed '+e.name,'Undo',function(){state.foodLog.push(e);if(fv2Saved(()=>{state.foodLog=state.foodLog.filter(x=>x!==e)}))renderMeals()});return true}
/* Logs an entry again today, and shows today; whether it was saved */
function fv2LogAgain(e){const n=Object.assign({},e,{id:'food_'+Date.now()+'_'+Math.random().toString(36).slice(2,6),date:getToday(),at:new Date().toISOString()});state.foodLog.push(n);
 if(!fv2Saved(()=>{state.foodLog=state.foodLog.filter(x=>x!==n)}))return false;mealsDay=getToday();renderMeals();fv2Hap('success');fv2Snack('Logged again today');return true}
/* the page's head of a view */
function fv2HeadFor(view){const h=fv2HeadData(view);return fv2Head(h.title,h.back)}
/* the boxes of the daily targets as they start (the saved targets, or 2000 kcal balanced), as text */
function fv2GoalDraft(){const g=fv2GoalData(),d={cal:String(g.cal.value),water:String(g.water.value)};g.fields.forEach(f=>{d[f.key]=String(f.value)});return d}
/* the daily targets with the boxes at d (their text as typed) and the line under them, for a screen that draws them */
function fv2GoalView(d){const g=fv2GoalData();return {cal:{label:g.cal.label,value:d.cal,step:g.cal.step,min:0},presets:g.presets,
 fields:g.fields.map(f=>({key:f.key,label:f.label,color:f.color,value:d[f.key]})),water:{label:g.water.label,value:d.water,unit:g.water.unit},
 sum:fv2GoalSum(d.cal,d.p,d.c,d.f),save:g.save}}
