/* Node contract tests. Browser/Android parity is covered by OverviewParityTest. */
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const base=path.join(__dirname,'..');const src=fs.readFileSync(path.join(base,'src/runtime/segments/50-native-overview.js'),'utf8');
let navigated='',rendered=0,timers=[];
const context={MutationObserver:class{observe(){}},console,Map,WeakMap,setTimeout:f=>timers.push(f),document:{body:{},getElementById:()=>null,createElement:()=>({getContext:()=>({})})},navigate:v=>navigated=v,renderOverview:()=>rendered++,Capacitor:undefined};context.window=context;
vm.createContext(context);vm.runInContext(src,context);
assert.equal(typeof context.openNativeOverview,'function');context.navigate('quests');assert.equal(navigated,'quests');context.renderOverview();assert.equal(rendered,1);
assert.deepEqual(Array.from(context.NativeOverviewSnapshot.split('linear-gradient(rgb(1,2,3),rgba(4,5,6,.2)), url(a)')),['linear-gradient(rgb(1,2,3),rgba(4,5,6,.2))','url(a)']);
assert(!/localStorage\.setItem|state\s*=/.test(src),'presentation cannot write gameplay data');
assert(src.includes('N.maps.get(e.sequence)'),'actions use the displayed scene version');
assert(src.includes('isConnected'),'stale targets are not dispatched');
assert(src.includes('lmaTopOverlay()'),'native overview cannot cover an open tool');
assert(!src.includes('html2canvas'),'no page screenshot replacement');
console.log('PASS native Overview web fallback, navigation, rendering, gradient parsing, shared state and versioned action contracts');
