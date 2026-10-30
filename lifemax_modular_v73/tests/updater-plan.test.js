/* Run with Node: exercises the real update planner and Settings/install flow. */
const vm=require('node:vm'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const source=fs.readFileSync(path.join(__dirname,'../src/runtime/segments/35-android-upkeep.js'),'utf8');
const body={innerHTML:''},prefs={backup:{uri:''}},calls=[];let release,live,permission=false;
const U={check:async()=>release,addListener(){},install:async o=>{calls.push(['apk',o.url]);return {needsPermission:permission}}};
const L={status:async()=>live,fetch:async o=>{calls.push(['web',o.build]);live={...live,staged:o.build}},addListener(){}};
const s={console,Date,Math,Number,String,Promise,setTimeout(){},clearTimeout(){},setInterval(){},
 document:{getElementById:id=>id==='lmaUpdBody'?body:null,querySelector:()=>null,addEventListener(){}},
 lmaPlugin:n=>n==='Updater'?U:n==='LiveWeb'?L:null,lmaNative:()=>true,lmaLoad:()=>prefs,lmaSave(){},navigate(){},esc:String};
vm.createContext(s);vm.runInContext(source,s);vm.runInContext('lmaUpdPopOpen=()=>{popups++};lmaWebPill=()=>{};',Object.assign(s,{popups:0}));
const R={current:141,latest:143,apk:'https://github.com/example/app.apk',nativeApi:6,latestNativeApi:7,webBuild:143,webUrl:'https://example/app.html',webSha:'a'.repeat(64),webMinApi:7};
const ST={nativeApi:6,installedBuild:141,activeBuild:142,staged:0};let passed=0;
function check(name,fn){fn();passed++;console.log('PASS '+name)}
const plan=(r,st)=>s.lmaUpdPlan(r,st);
(async()=>{
 check('native Cash release offers APK despite newer OTA app',()=>assert.equal(plan(R,ST),'apk'));
 check('a higher web minimum API also requires APK',()=>assert.equal(plan({...R,latestNativeApi:6},ST),'apk'));
 check('installed APK build is distinct from active web build',()=>assert.equal(plan({...R,current:999},ST),'apk'));
 check('missing LiveWeb status falls back to APK',()=>assert.equal(plan(R,null),'apk'));
 check('web-only changes still use OTA',()=>assert.equal(plan({...R,latestNativeApi:6,webMinApi:6},ST),'web'));
 check('up-to-date compatible app reports none',()=>assert.equal(plan({...R,current:143},{...ST,nativeApi:7,installedBuild:143,activeBuild:143}),'none'));
 check('stale metadata older than the running OTA build is an error',()=>assert.throws(()=>plan({...R,latest:141,latestNativeApi:6,webMinApi:6},ST)));
 check('missing latest version is an error, not latest',()=>assert.throws(()=>plan({...R,latest:0},ST)));
 check('unknown installed version is an error',()=>assert.throws(()=>plan({...R,current:0},{...ST,installedBuild:0})));
 check('incompatible release without newer APK is an error',()=>assert.throws(()=>plan({...R,latest:141},ST)));
 check('malformed web download is an error',()=>assert.throws(()=>plan({...R,latestNativeApi:6,webMinApi:6,webSha:''},ST)));
 release={...R};live={...ST};await s.lmaCheckUpdate(false);
 check('Check now offers native APK installation',()=>{assert.equal(vm.runInContext('lmaUpd.state',s),'available');assert.equal(s.popups,1);assert.match(body.innerHTML,/lmaInstallUpdate/);assert.match(body.innerHTML,/Latest release: 143/);assert.match(body.innerHTML,/Android API 6 \/ requires 7/);assert.equal(calls.length,0)});
 permission=true;await s.lmaInstallUpdate();
 check('APK install uses the native downloader and reports Android permission',()=>{assert.deepEqual(calls[0],['apk',R.apk]);assert.equal(vm.runInContext('lmaUpd.state',s),'permission')});
 permission=false;await s.lmaInstallUpdate();
 check('retry after permission opens Android installer',()=>assert.equal(vm.runInContext('lmaUpd.state',s),'opened'));
 release={...R,latest:0};await s.lmaCheckUpdate(false);
 check('failed release verification is visible in Settings',()=>{assert.equal(vm.runInContext('lmaUpd.state',s),'error');assert.match(body.innerHTML,/Could not verify/)});
 release={...R,latestNativeApi:6,webMinApi:6};await s.lmaCheckUpdate(false);
 check('compatible web update still downloads and stages automatically',()=>{assert(calls.some(c=>c[0]==='web'&&c[1]===143));assert.equal(vm.runInContext('lmaUpd.state',s),'webready')});
 console.log(passed+' checks passed');
})().catch(e=>{console.error(e);process.exitCode=1});
