/* Run: node tests/director-meals.test.js. Uses real Smart Diet data and Life
   Director's actual rendering/click handlers with lightweight host DOM doubles. */
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.join(__dirname,'..'),read=p=>fs.readFileSync(path.join(root,p),'utf8');
const data=JSON.parse(read('src/data/smartDiet.html').match(/>([\s\S]*)<\/script>/)[1]);
let view='life-director',renderCount=0,messages=[],focused=false,scrolled=false,selected,missingPlan=false;
const uiState={},foodState={};
const card={tagName:'DETAILS',open:false,querySelector:()=>({open:false}),setAttribute(){},focus(){focused=true},scrollIntoView(){scrolled=true}};
const mealRoot={querySelector(selector){selected=selector;return card}};
const s={console,Date,Intl,Math,Number,String,JSON,Map,Set,Object,Array,Promise,
 state:{classId:'athlete',activities:[],mealsMeta:{calGoal:2500},foodLog:[]},mealsDay:'2026-10-28',
 __sdData:data,document:{getElementById:id=>id==='fv2Root'?mealRoot:null,addEventListener(){}},
 mealsEnsure(){},fv2S:()=>foodState,fv2Fmt:n=>Math.round(n).toLocaleString(),esc:v=>String(v).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])),
 getToday:()=> '2026-10-28',mealsDayKey:()=>s.mealsDay,dayFoodLog:d=>s.state.foodLog.filter(x=>x.date===d),
 fv2DayShift(d,n){const x=new Date(d+'T12:00:00');x.setDate(x.getDate()+n);return x.getFullYear()+'-'+String(x.getMonth()+1).padStart(2,'0')+'-'+String(x.getDate()).padStart(2,'0')},
 save(){},renderMeals(){renderCount++},fv2Hap(){},fv2Snack(){},toast:m=>messages.push(m),
 navigate:v=>{view=v},lmaNavTo:(v)=>{view=v},lmaMotionOff:()=>true,
 requestAnimationFrame:f=>f(),setTimeout:()=>0,clearTimeout(){},setInterval:()=>0,clearInterval(){},
 LifeMaxFeatures:{register(){},list:()=>[]}
};s.window=s;vm.createContext(s);vm.runInContext(read('src/runtime/segments/50-smartdiet.js'),s);
let passed=0;function check(name,fn){fn();passed++;console.log('PASS '+name)}
const ref={title:'Lunch',day:'2026-10-28',startEpoch:Date.parse('2026-10-28T12:00:00Z')/1000};
check('Breakfast, Lunch and Dinner resolve for every day in both diets',()=>{
 for(const diet of ['standard','simple'])for(let n=0;n<7;n++)for(const slot of ['breakfast','lunch','dinner']){
  s.sdS().diet=diet;const day=s.fv2DayShift('2026-10-26',n),match=s.sdDirectorMeal({title:slot,day});
  assert(match);assert.equal(match.slot,slot);assert.equal(s.sdDay(day).items[match.index].meal.name,match.name);
 }s.sdS().diet='standard';
});
const initial=s.sdDirectorMeal(ref),lunchIndex=initial.index;
check('recipe is linked by slot rather than clock time or next unlogged meal',()=>{
 assert.equal(s.sdDirectorMeal({...ref,startEpoch:Date.parse('2026-10-28T22:00:00Z')/1000}).index,lunchIndex);
 assert.notEqual(lunchIndex,s.sdDay(ref.day).next);
});
const other=Object.values(data.plans.standard.meals).find(m=>m.slot==='lunch'&&m.name!==initial.name);
s.sdS().over[ref.day]={[lunchIndex]:other.id};
check('a meal swap is reflected without regenerating Life Director',()=>assert.equal(s.sdDirectorMeal(ref).name,other.name));
s.sdS().veg=true;s.sdS().df=true;
check('preferences resolve the same recipe as the current Smart Diet plan',()=>assert.equal(s.sdDirectorMeal(ref).name,s.sdDay(ref.day).items[lunchIndex].meal.name));
s.sdS().veg=false;s.sdS().df=false;
const before=JSON.stringify(s.state.foodLog);
s.sdOpenDirectorMeal(ref);
check('tap selects the scheduled day and Plan, opens ingredients and focuses the meal',()=>{
 assert.equal(view,'meals');assert.equal(s.mealsDay,ref.day);assert.equal(foodState.pane,'plan');
 assert.equal(s.sdUi().open[ref.day+':'+lunchIndex],true);assert.equal(s.sdUi().ing[ref.day+':'+lunchIndex],true);
 assert(focused&&scrolled&&selected.includes('data-i="'+lunchIndex+'"'));assert.equal(card.open,true);assert(renderCount>0);
});
check('viewing a scheduled meal never logs it',()=>assert.equal(JSON.stringify(s.state.foodLog),before));
s.sdLogMeal(ref.day,lunchIndex,true);
check('logged meal keeps its link instead of advancing to the next meal',()=>{const m=s.sdDirectorMeal(ref);assert(m.logged);assert.equal(m.index,lunchIndex)});
s.sdS().skip={[ref.day]:{0:true}};
check('skipped meals still open their existing Restore controls',()=>assert(s.sdDirectorMeal({...ref,title:'Breakfast'}).skipped));
s.sdOpenDirectorMeal({...ref,day:'2026-10-29',title:'Dinner'});
check('tomorrow opens tomorrow even if Food was viewing another date',()=>assert.equal(s.mealsDay,'2026-10-29'));
check('unknown block names and invalid dates do not guess another meal',()=>{
 assert.equal(s.sdDirectorMeal({...ref,title:'Meal prep'}),null);assert.equal(s.sdDirectorMeal({...ref,day:'2026-02-30'}),null);
 assert.equal(s.sdDirectorMeal({title:'Dinner',startEpoch:NaN}),null);
});
s.sdOpenDirectorMeal({...ref,title:'Meal'});
check('unmatched meal falls back to the date plan with an explanation',()=>{assert.equal(view,'meals');assert(messages.at(-1).includes('No Smart Diet meal'))});
// Load the real scheduler/app. Expose closure-local handlers in this test only;
// production has no test API and the scheduler itself remains unchanged.
let director=read('src/features/life-director/logic.js');
const marker='  return { mount, glance, widgetSnapshot, voice: voiceApi';assert(director.includes(marker));
director=director.replace(marker,`  return { test: { doingName, scheduledMealRef, scheduledMeal, mealAnchor, roHtml, onClick, openFromGlance, dayBlocks,
 setRoot(r){root=r;ST={availability:{timezone:'Asia/Tokyo'},ui:{},schedule:null};} }, mount, glance, widgetSnapshot, voice: voiceApi`);
vm.runInContext(director,s);const test=vm.runInContext('LifeDirectorApp.test',s);
const fakeSheet={hidden:true,classList:{contains:()=>false}};
test.setRoot({contains:()=>true,querySelector:()=>fakeSheet});
const start=Date.parse('2026-10-28T23:00:00Z')/1000;
const block={kind:'MEAL',code:'MAINT',title:'Lunch',startEpoch:start,endEpoch:start+1800};
check('dial shows meal labels while recipes stay available through meal links',()=>{
 for(const title of ['Breakfast','Lunch','Dinner']){
  const b={...block,title};assert.equal(test.doingName(b),title);
  assert(test.scheduledMeal(b));assert(test.mealAnchor(b).includes('data-act="anchor"'));
 }
 assert.equal(test.doingName({...block,title:''}),'Meal');
});
check('Life Director passes its own timezone date, not the phone clock date',()=>assert.equal(test.scheduledMealRef(block).day,'2026-10-29'));
const html=test.roHtml([block],'Asia/Tokyo');
check('running order shows current recipe and accessible meal link',()=>{assert(html.includes('data-act="anchor"'));assert(html.includes('role="button" tabindex="0"'));assert(html.includes(s.esc(s.sdDirectorMeal({title:'Lunch',day:'2026-10-29'}).name)))});
const button={tagName:'LI',dataset:{act:'anchor',k:'meal',t:'Lunch',s:String(start),e:String(start+1800)},closest:()=>null};
test.onClick({target:{closest:()=>button}});
check('the actual Life Director click handler opens the linked meal',()=>{assert.equal(view,'meals');assert.equal(s.mealsDay,'2026-10-29')});
view='dashboard';test.openFromGlance(button);
check('Overview dial meal links open the meal directly',()=>assert.equal(view,'meals'));
const midnight=Date.parse('2026-10-29T15:00:00Z')/1000;
const split=test.dayBlocks({segments:[{...block,title:'Dinner',startEpoch:midnight-900,endEpoch:midnight+900}],scheduledInstances:[]},{startEpoch:midnight,endEpoch:midnight+86400},'Asia/Tokyo').find(x=>x.kind==='MEAL');
check('cross-midnight clipping retains the original dinner date',()=>{assert.equal(test.scheduledMealRef(split).day,'2026-10-29');assert(test.mealAnchor(split).includes('data-ms="'+(midnight-900)+'"'))});
const original=s.LifeMaxMeals;s.LifeMaxMeals=null;
check('Life Director remains usable without Smart Diet in a standalone build',()=>{assert.equal(test.scheduledMeal(block),null);test.onClick({target:{closest:()=>button}})});
s.LifeMaxMeals=original;
console.log(passed+' checks passed');

