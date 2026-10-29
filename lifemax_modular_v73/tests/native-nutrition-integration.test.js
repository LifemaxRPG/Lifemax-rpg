/* Native Nutrition rollout contracts. Run with node; no package dependencies. */
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'..');
const bridge=fs.readFileSync(path.join(root,'src/runtime/segments/50-native-nutrition.js'),'utf8');
const original=fs.readFileSync(path.join(root,'src/runtime/segments/50-food.js'),'utf8');
const template=fs.readFileSync(path.join(root,'src/index.template.html'),'utf8');
const settings=fs.readFileSync(path.join(root,'src/views/settings.html'),'utf8');
const api=fs.readFileSync(path.join(root,'../android-wrapper/native/NativeApi.java'),'utf8');
const native=fs.readFileSync(path.join(root,'../android-wrapper/native/NativeNutritionPlugin.kt'),'utf8');
new Function(bridge);
assert.match(bridge,/nativeNutrition===true/,'Native beta must be OFF by default');
assert.match(bridge,/isNativePlatform/,'Do not open native UI on web');
assert.match(bridge,/isPluginAvailable\('NativeNutrition'\)/,'Gate on plugin support');
for(const v of ['dayFoodLog','fv2Tot','fv2Goals','fv2Water','fv2AddWater','renderMeals']){
 assert.match(bridge,new RegExp(v),'Bridge must use the existing canonical model: '+v);
}
assert.doesNotMatch(bridge,/state\.foodLog\.push|state\.waterLog\.push|localStorage\.setItem/,'Native bridge must not duplicate persistence');
assert.match(template,/50-native-nutrition\.js/,'Bridge must be included in HTML build');
assert.match(settings,/id="nativeNutritionToggle"/,'Setting must be visible under Appearance');
assert.match(settings,/setNativeNutritionEnabled/,'Setting must be wired');
assert.ok(Number(api.match(/VALUE = (\d+)/)?.[1]) >= 11,'Native Nutrition requires Native API 11 or newer');
assert.match(native,/@CapacitorPlugin\(name = "NativeNutrition"\)/);
assert.match(native,/data\.has\("totals"\)/);
assert.doesNotMatch(native,/DeterministicNutritionFixtures|MockNutritionRepository/,'Never publish fixture data as live Food');
assert.match(original,/function fv2Goals/,'Original Food engine must remain intact');

const vm=require('node:vm');
const day='2026-10-29';
const savedEntry={id:'saved-meal-42',date:day,slot:'lunch',name:'Real lunch',grams:180,kcal:472,p:32,c:49,f:14};
const runtime={
 console,Date,setTimeout(){},clearTimeout(){},requestAnimationFrame(){},
 document:{
  getElementById(){return null},querySelector(){return null},
  documentElement:{classList:{contains(){return false}}}
 },
 lmaLoad(){return {nativeNutrition:false}},lmaSave(){},lmaView(){return 'meals'},
 dayFoodLog(key){return key===day?[savedEntry]:[]},mealsDayKey(){return day},
 fv2Tot(entries){return entries.reduce((a,e)=>({
  kcal:a.kcal+e.kcal,p:a.p+e.p,c:a.c+e.c,f:a.f+e.f,fib:0,sug:0,na:0
 }),{kcal:0,p:0,c:0,f:0,fib:0,sug:0,na:0})},
 fv2Goals(){return {cal:2200,p:150,c:220,f:70,water:2000}},
 fv2Water(){return 750},getToday(){return day},
 fv2DayShift(key,delta){const d=new Date(key+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+delta);return d.toISOString().slice(0,10)},
 fv2S(){return {pane:'log'}},fv2Slot(){return 'lunch'},fv2Recents(){return []},
 navigate(){},renderMeals(){},save(){}
};
runtime.window=runtime;
vm.runInNewContext(bridge,runtime,{filename:'50-native-nutrition.js'});
const projection=runtime.NativeNutritionBeta.snapshot();
assert.equal(projection.entries.length,1);
assert.equal(projection.entries[0].id,savedEntry.id);
assert.equal(projection.totals.kcal,savedEntry.kcal);
assert.equal(projection.waterMl,750);
assert.equal(projection.week.length,7);
assert.equal(projection.plan,null,'Unavailable Smart Diet data must not become a sample plan');
assert.equal(projection.shop,null,'Unavailable shopping data must not become a sample list');

console.log('Native Nutrition opt-in / no-demo-data contracts PASS');
