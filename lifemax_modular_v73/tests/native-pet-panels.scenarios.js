/* The cat states native-pet-panels.test.js (and the golden HTML it checks) put the page's Cat view through: each is page JavaScript
   run with `now` in scope, at the fixed clock CLOCK, so the words and the seeded genes are the same on every run. */
const CLOCK = '2026-10-30T12:00:00Z';

const CAT = (name, o = '{}') => `Object.assign({v:1,name:${JSON.stringify(name)},coat:'ginger',trait:'curious',born:now-40*864e5,lastFed:now-5*36e5,meals:[],day:getToday(),fedToday:0,petsToday:0,playsToday:0,bondPts:PET_BOND_AT[3],care:0,vacation:{on:false,since:0},gifts:0,lastGiftDay:'',alive:true,passed:0,seen:{},greeted:0},${o})`;

/* a likes record with a clear favourite place and toy */
const LIKES = `(function(){const ks=Object.keys(PET_SPOTS),sp={},t={};sp[ks[0]]=14;sp[ks[1]]=8;sp[ks[2]]=5;sp[ks[3]]=2;t.ball_a=9;t.feather=5;t.mouse=2;
  return {v:1,spots:sp,toys:t,plays:{ball_a:7,feather:3,mouse:1},seen:{},dayT:0,favSpot:ks[0],favToy:'ball_a',news:[]}})()`;

/* the inner life of a bonded cat: threads, an innate and a learned quirk, three memories, the three small-print stats and a comfort zone */
const BRAIN = `(function(p){const b=petBrain(p),m=Math.floor(Date.now()/60000);
  Object.assign(b.at,{fam:0.72,tru:0.81,sec:0.58,aut:0.66});b.af.v=0.5;b.af.a=-0.1;b.af.s=0;
  b.qk.inn=['dramatic_flopper','chirp_greeter'];b.qk.acq=['lap_barnacle'];
  b.mem.core=[[m-3*24*60,'first_blink','front_living',''],[m-30,'first_trophy','',''],[m-2*60,'quirk','','lap_barnacle']];
  b.gz.n=3;b.hu.gifts=2;b.co.min=14;const z=Object.keys(b.mem.w);z.forEach(k=>{b.mem.w[k]=0.01});b.mem.w[z[0]]=0.6;
  b.off.acts=['dozed on the sofa','watched the window'];return p})(state.pet)`;

/* two cats that have met, with a settled order, counts, a last event and a "while you were away" line */
const PAIR = `(function(){const d=petPairData(),r=petRankData();d.rel=0.52;d.met=now-5*864e5;d.n={greet:4,hiss:1,chase:2,groom:1,nap:1,standoff:2};
  d.last={t:'groomed each other',at:now-3*36e5};d.away={at:now-5*60000,t:state.pet.name+' and '+state.pet2.name+' took turns at the sunny spot.'};
  r.n=8;r.cf=[0.82,0.41];r.settled=true;r.lead=0;r.hist=[0,0,0,1,0,0]})()`;

/* a genetic cat grown from a seed: kind 'f' (a female), 'xxy' (a sterile male) or 'm' */
const dna = (seed, kind) => `(function(){const want='${kind}';let d,ph;
  for(let i=0;i<4000;i++){d=petDnaRandom(petRng(petHash('ct6a-${seed}-'+i)),{});ph=petDnaPheno(d);if(ph.key.indexOf('dna_')===0&&(want==='f'?ph.sex==='f':want==='xxy'?ph.sterile:ph.sex==='m'&&!ph.sterile))break}
  const p=state.pet;p.dna=d;p.sex=ph.sex;p.trait=ph.trait;p.coat=ph.key;petDnaRegister(ph.key);
  if(want==='f')d.mut=['Mc'];})()`;

const SCENARIOS = {
  kitten: `state.pet=${CAT('Pip', '{coat:"tuxedo",born:now-12*864e5,bondPts:PET_BOND_AT[1]}')}`,
  stray: `state.pet=${CAT('Miso', '{born:now-864e5,bondPts:0}')}`,
  bonded: `state.pet=${CAT('Miso', `{bondPts:PET_BOND_AT[4],likes:${LIKES}}`)};${BRAIN}`,
  hungry: `state.pet=${CAT('Miso', `{lastFed:now-30*36e5,likes:${LIKES}}`)};${BRAIN}`,
  vacation: `state.pet=${CAT('Miso', `{vacation:{on:true,since:now-3*36e5},likes:${LIKES}}`)};${BRAIN}`,
  twoCats: `state.pet=${CAT('Ted', `{bondPts:PET_BOND_AT[4],likes:${LIKES}}`)};state.pet2=${CAT('Lily', '{coat:"gray",trait:"shy",born:now-30*864e5,bondPts:PET_BOND_AT[2]}')};${BRAIN};${PAIR}`,
  geneticMale: `state.pet=${CAT('Miso', '{}')};${dna('a', 'm')};${BRAIN}`,
  geneticFemale: `state.pet=${CAT('Nori', '{bondPts:PET_BOND_AT[4]}')};${dna('b', 'f')};${BRAIN}`,
  geneticSterile: `state.pet=${CAT('Tango', '{}')};${dna('c', 'xxy')}`,
};

module.exports = { CLOCK, CAT, SCENARIOS };
