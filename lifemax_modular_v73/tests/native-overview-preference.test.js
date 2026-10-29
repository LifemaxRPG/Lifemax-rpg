/* The real Android preference loader/save path must preserve Classic across starts. */
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.join(__dirname,'..');
const bridge=fs.readFileSync(path.join(root,'src/runtime/segments/50-native-overview.js'),'utf8');
const android=fs.readFileSync(path.join(root,'src/runtime/segments/33-android.js'),'utf8').split('function lmaHM(')[0];
const storage=new Map();
function start(){
 const input={},hint={};
 const context={console,Map,WeakMap,setTimeout(){},innerWidth:390,state:{onboardingComplete:true},lmaView:()=> 'settings',navigate(){},renderOverview(){},
  localStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v)},
  document:{body:{className:'',classList:{contains:()=>false}},querySelector:()=>null,getElementById:id=>id==='nativeOverviewToggle'?input:id==='nativeOverviewHint'?hint:null,createElement:()=>({getContext:()=>({})})},
  Capacitor:{isNativePlatform:()=>true,isPluginAvailable:()=>true,Plugins:{NativeOverview:{}}}};
 context.window=context;vm.createContext(context);vm.runInContext(android,context);vm.runInContext(bridge,context);
 return {context,input,hint};
}
(async()=>{
 const first=start();assert.equal(first.input.checked,true);
 first.context.lmaLoad().apps=[{pkg:'kept.app'}];
 await first.context.setNativeOverviewEnabled(false);assert.equal(first.input.checked,false);
 const second=start();assert.equal(second.input.checked,false);
 assert.deepEqual(JSON.parse(storage.get('lifemax_android_prefs_v1')).apps,[{pkg:'kept.app'}]);
 assert.equal((await second.context.openNativeOverview()).opened,false);
 await second.context.setNativeOverviewEnabled(true);assert.equal(second.input.checked,true);
 assert.equal(start().input.checked,true);
 console.log('PASS Overview defaults to native, Classic survives reload, explicit open respects Classic, re-enable persists, and unrelated preferences survive');
})().catch(e=>{console.error(e);process.exitCode=1});
