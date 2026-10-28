// Run: node tests/pacman.test.js [built HTML]
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(process.argv[2] || path.join(__dirname, '../src/runtime/segments/20-runtime.js'), 'utf8');
const start = source.indexOf("document.addEventListener('input',function(e){var t=e.target;", source.indexOf('window.openPacman=openPacman'));
assert(start >= 0, 'Pac-Man input listener exists');
const end = source.indexOf('},true);', start) + '},true);'.length;
let handler, opened, closed, blurred, stopped, cleared;
const sh = {q:'pac-ma',list:[{n:'old result'}],serial:7,_t:42};
vm.runInNewContext(source.slice(start,end), {
 document:{addEventListener(type,fn,capture){assert.equal(type,'input');assert.equal(capture,true);handler=fn}},
 openPacman(){opened++}, fv2S(){return {sh}}, fv2Close(){closed++},
 clearTimeout(id){cleared=id}
});
function input(id,value){
 opened=closed=blurred=stopped=0;cleared=null;
 const target={id,value,blur(){blurred++}};
 handler({target,stopPropagation(){stopped++}});
 return target;
}
for(const query of ['Pac-Man','pacman','PAC MAN','  PaC-Man  ']){
 Object.assign(sh,{q:query,list:[{}],serial:7,_t:42});
 const target=input('fv2Q',query);
 assert.equal(opened,1);assert.equal(closed,1);assert.equal(blurred,1);
 assert.equal(stopped,1);assert.equal(cleared,42);assert.equal(sh.serial,8);
 assert.equal(sh.q,'');assert.equal(sh.list.length,0);assert.equal(target.value,'');
}
for(const id of ['mealSearch','msSearch']){
 const target=input(id,'Pac-Man');assert.equal(opened,1);assert.equal(closed,0);
 assert.equal(target.value,'');
}
for(const query of ['banana','pac','pacman cereal','']){
 const target=input('fv2Q',query);assert.equal(opened,0);assert.equal(closed,0);
 assert.equal(stopped,0);assert.equal(target.value,query);
}
input('unrelatedSearch','pacman');assert.equal(opened,0);
handler({target:null});
console.log('PASS: Pac-Man variants, legacy fields, search cleanup, ordinary searches and unrelated inputs');
