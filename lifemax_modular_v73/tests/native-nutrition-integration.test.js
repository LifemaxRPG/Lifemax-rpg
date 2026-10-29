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
for(const v of ['dayFoodLog','fv2Tot','fv2Goals','fv2Water','addMealsWater','renderMeals']){
 assert.match(bridge,new RegExp(v),'Bridge must use the existing canonical model: '+v);
}
assert.doesNotMatch(bridge,/state\.foodLog\.push|state\.waterLog\.push|localStorage\.setItem/,'Native bridge must not duplicate persistence');
assert.match(template,/50-native-nutrition\.js/,'Bridge must be included in HTML build');
assert.match(settings,/id="nativeNutritionToggle"/,'Setting must be visible under Appearance');
assert.match(settings,/setNativeNutritionEnabled/,'Setting must be wired');
assert.match(api,/VALUE = 11/,'New native plugin requires Native API 11');
assert.match(native,/@CapacitorPlugin\(name = "NativeNutrition"\)/);
assert.match(native,/data\.has\("totals"\)/);
assert.doesNotMatch(native,/DeterministicNutritionFixtures|MockNutritionRepository/,'Never publish fixture data as live Food');
assert.match(original,/function fv2Goals/,'Original Food engine must remain intact');
console.log('Native Nutrition opt-in / no-demo-data contracts PASS');
