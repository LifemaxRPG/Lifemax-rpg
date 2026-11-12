/* The cattery states native-pet-cattery.test.js and tools/gen_pet_cattery_fixtures.js put the page through (page JavaScript run with `now`
   in scope at the fixed clock). */
const { CAT } = require('./native-pet-panels.scenarios');
const { CLOCK } = require('./native-pet-panels.scenarios');
const AT = Date.parse(CLOCK);

/* a home cat (a genetic female), three cattery cats (a male, a female, a sterile male) and, when asked, a litter of three */
const MK = `const mk=(seed,sex)=>{const d=petDnaRandom(petRng(petHash('ct6b-'+seed)),{sex:sex==='XXY'?'XY':sex});if(sex==='XXY')d.sex='XXY';d.gen=0;petDnaRegister(petDnaPheno(d).key);return d};`;
const BASE = (litter) => `${MK}delete state.pet2;state.petPair=null;state.petCatteryOps=[];state.petMemorial=[];
  state.pet=${CAT('Ted', '{}')};const d0=mk('home','XX');state.pet.dna=d0;state.pet.sex='f';state.pet.trait=petDnaPheno(d0).trait;state.pet.coat=petDnaPheno(d0).key;
  state.cattery={v:1,goal:'em+',litter:${litter ? `{mom:'Ted',dad:'Basil',at:${AT},kits:[mk('k1','XX'),mk('k2','XY'),mk('k3','XY')]}` : 'null'},cats:[
    {id:'c1',name:'Basil',dna:mk('m1','XY'),born:now-5*864e5},{id:'c2',name:'Olive',dna:mk('f1','XX'),born:now-9*864e5},{id:'c3',name:'Fig',dna:mk('x1','XXY'),born:now-2*864e5}]}`;
const FULL = `${BASE(true)};while(state.cattery.cats.length<PET_CATTERY_MAX)state.cattery.cats.push({id:'z'+state.cattery.cats.length,name:'Z'+state.cattery.cats.length,dna:mk('z'+state.cattery.cats.length,'XY'),born:now})`;
const NOCAT = `${MK}state.pet=null;delete state.pet2;state.petCatteryOps=[];state.petMemorial=[{name:'Pepper',coat:'ginger',days:12,passed:now-864e5,bond:2,album:null}];seed();window.__petLitter=petDnaNewLitter(4)`;
const PAIR = `${BASE(false)};seed();window.__petLitter=petDnaNewLitter(4)`;
const DEAD = `${BASE(false)};state.pet.alive=false;state.pet.passed=now;state.petMemorial=[{name:'Ted',coat:state.pet.coat,days:40,passed:now,bond:3,album:null}]`;
const NAME = (n) => `{document.querySelectorAll('#petNameIn').forEach(e=>e.remove());const i=document.createElement('input');i.id='petNameIn';i.value='${n}';document.body.appendChild(i);try{`; // the page's click reads the name from its field
module.exports = { AT, MK, BASE, FULL, NOCAT, PAIR, DEAD, NAME };
