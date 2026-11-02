# Pet (cat) system: as built in the web app

Scope: `lifemax_modular_v73/src/runtime/segments/` (called "seg" below), `src/data/*.html`, `src/views`, `tests/`, and the native Overview cat card.
Short names: `15` = `15-runtime.js`, `16` = `16-runtime.js`, `17` = `17-runtime.js`. Line numbers are from the `.wt/final` worktree.
"NOT DETERMINED" marks what I could not establish from reading. Nothing was run.

## 0. One-paragraph picture

The cat is a 2D pixel-art canvas game, not WebGL. The ADR 0003 wording "current WebGL scene" is inaccurate. A 576x144 "house" (living room | bedroom | kitchen) is drawn at 4x into an offscreen 2D canvas every animation frame, then blitted to the Pet tab, the full-screen overlay and the Overview peek card. The cat is a palette-indexed sprite sheet (28 animation rows, 72x48 px frames). It is recoloured at load and optionally re-assembled by a "rig" (ears, tail, legs, torso stretched by genes) and an "HD" 2x layer with a live-drawn face. The cat is moved by a plan queue of steps (walk, hop, act, face, fn). A utility-weighted chooser (`petThink`) picks plans, and a "brain" (mood, attachment, memory, quirks) re-weights the options. All logic, state and drawing are in the page. The only native consumer today is the Overview cat card, which shows one still PNG made by `petPortrait`. ADR 0003 (`docs/architecture/decisions/0003-pet-room-and-quest-table-stay-web.md`) says the room stays web until the Cat tab's turn, and records the owner's 30 Oct 2026 wish for a 2.5D room.

## 1. Data model

### 1.1 Saved state (all inside the one localStorage JSON blob)
- Storage: key `lifemax_rpg_prototype_v1` (`01-runtime.js:46`), `SAVE_SCHEMA=1` (`04-runtime.js:11`). `writeSave()` (`04:40`) stringifies the whole `state`. `save()` (`04:44`) also re-renders everything; the brain uses bare `writeSave()` (`26:100`) at most every 60 s (`PERSIST_MS`).
- `state.pet` created by `petAdopt` (`15:65-71`): `{v:1, name(<=14 chars), coat, trait, born(ms), lastFed(ms, set to now-10h so the first meal is allowed), meals[<=30 ms stamps], day, fedToday, petsToday, playsToday, bondPts, care, vacation:{on,since}, gifts, lastGiftDay, alive, passed, seen:{}, greeted}`.
- Added lazily by later segments: `p.dna` (32), `p.sex`, `p.likes` (25:`petLikes`, v:1), `p.brain` (26:`migratePetBrainState`, v:1, about 2 KB), `p.bond={posX,posZ,elevationY,at}` (24:117, the saved room position, used only if under 10 min old, 24:123), `p.erodedDays`, `p.dnaPrev` (dev undo).
- Other top-level state: `state.pet2` (second cat, same shape), `state.petPair` (friendship, `29-pair:62-67`, includes `rk` rank record `29-rank:~40`), `state.petMemorial[]` (`{name,coat,days,passed,bond}`, `15:109`), `state.careDay` (`44-rewards:60`), `state.toysBought` (`44:86`), `state.hdCat` (HD toggle, `50-hd:7`), `state.petInviteHidden` (`18:12`), `state.cattery` (breeding lab, `37:92`).
- Also outside the save: `localStorage 'lm_pet_seen'` (last time the room was watched, `16:29`).

### 1.2 Needs and stats
- There is one hard need: hunger. It is wall-clock hours since `lastFed`, frozen while `vacation.on` (`petHours` `15:72-77`). Stages by hours: `<8` 0, `<14` 1, `<26` 2, `<50` 3, `<120` 4, else 5 (`petStageOf` `15:78`).
- Feeding is allowed at 4 h or more since the last meal (`15:83-84`). `petFeed` gives bond +6 for each of the first 2 meals a day. The 2nd meal of the day also gives `care++` (`15:86-95`).
- Petting: bond +2, counted 3 times a day (`15:96-99`). Play: bond +3, counted 2 times a day (`15:100-104`).
- `petGain` gives no bond if hunger stage >= 2 (`15:82`).
- Bond levels: `PET_BOND_AT=[0,30,90,200,360,580]`, names Stray, Wary, Curious, Friendly, Fond, Devoted (`15:57-58`). Hearts shown = level (max 5, `17:73`).
- Consequences (`petCheck` `15:105-115`): at 96 h or more, bond is eroded by 8 per whole day past 96 h. At 168 h or more the cat dies (`alive=false`, memorial pushed). `setInterval` 60 s and `visibilitychange` call it (`17:336`).
- There is no energy, happiness or health meter in the saved state. The only dynamic mood is the brain's `valence`/`arousal` (see 2.4), which is saved but is not a "need".
- Growth: age = `now - born`. A kitten for 30 days (`PET_GROW_DAYS` `16:55`). `petGrowIdx` is 0-5 per 5 days, -1 when grown (`16:59`). Kitten sprite scale `sx` is 0.6, 0.6667, 0.7333, 0.8, 0.8667, 0.9333 (`petGrowth.html`).
- Trust for depth is derived, not saved: `petTrust = L>=5 ? 100 : L*20 + levelPct*0.2` (`24:45`).

### 1.3 How real-life activity feeds the pet
There is no direct XP-to-pet link. Hunger does not depend on user activity. The couplings are all one-way, pet to XP, or pet state read by other features:
- Cat care bonus: `ecoCare` (`44-rewards:60-66`). When feed, play and pet have all happened on one day it pushes one `catcare` activity worth `ecoCap(ecoRestedTake(10))` XP, split 70% wellbeing and 30% social. Hooks are wrappers around `petFeed`, `petPlayDone` and `petPetDone` (`44:147-149`).
- Quests with `verify:'pet:feed|pet|play'` read `p.fedToday/petsToday/playsToday` (`50-quests-hand:53-56,139`).
- Overview "next moves" card: hungry-cat nudge, priority 0 or 2.5 (`18-runtime:11`). A "stray kitten keeps stopping by" invite shows when there is no cat (`18:12`).
- Journal coach reads `p.meals`, `fedToday` and `petsToday` for a per-day `care` flag (`47-journal-coach:55-56`).
- Voice and deep link: `lifemax://cats/feed` (`50-deeplink:3,35-50`), the voice intents (`45-voice:218-219`, `46-voice-nlu:15-16,85`, `50-voice-gemini:15,49`) and `petFeed` from NLU (`46:341`).
- Haptics setting text mentions petting (`50-haptics:56`).
- No notifications. The design is "a hungry cat tells you with behaviour, never a notification" (`17:239`, `19:207`). No widget reads the cat (`40-widget.js` has no cat code; Kotlin hits are only the Overview card and Journal chips).

### 1.4 DNA (`32-pet-dna.js`, `37-pet-morph.js`)
- `p.dna = {v:1, id(8 chars of base-32), sex:'XX'|'XY'|'XXY', g:{locus:[alleles]}, dev(uint32), mut[], par?, legacy?, neutral?}` (`32:155-170`, `ph` built `32:~190`). The phenotype is computed, not stored, cached in a WeakMap keyed on `JSON.stringify(d.g)+sex` (`32:~180`). `petPheno(p)` (`32:432`).
- Named loci (`PET_LOCI` `32:59-70`, plus `Tb` bobtail `37:~50`): O (X-linked, orange/tortie), B (brown), D (dilute), A (agouti), Mc (mackerel/classic), Ta (ticked), C (colour-point, albino), W (dominant white, spotting), L (long hair), I (silver).
- QTL traits with + / - alleles (`PET_QTL` `32:73-83`): white-spotting grade `sm` (3 loci), eye pigment `ey` (4), body size `sz` (4, kg = (3 + 0.25*count) * 1.25 if male), and six personalities `sk, ou, dm, sp, fr, vo` (skittish, outgoing, dominant, spontaneous, friendly, vocal) with h2 0.4-0.5. Score = `0.5 + 0.5*(sqrt(h2)*z + sqrt(1-h2)*env)` where env comes from `dev` (`32:~177`).
- Body-shape genes (`37:PET_MORPH`): ear length `em`, ear width `ew`, tail length `tl`, leg length `lg`, body length `bl`, whisker length `wk`. Each is a multiplier `2^(R*score)` clamped 0.35-2.8, with `R` = 1.35, 0.6, 1.25, 0.75, 0.5, 1.0 (`37:~62-73`). Bobtail (`tb/tb`) forces `tl` to at most 0.3.
- Old cats: `petDnaOf` (`32:421`) builds a genome from `born|dna|coat` that reproduces the legacy coat and trait. `petMorphFill` (`37:~55`) fills old genomes with balanced alleles so every proportion is exactly 1.
- Legacy trait (shy/playful/lazy/curious) is derived from the personality scores: `shy=sk, playful=sp, curious=ou, lazy=1-(ou+sp)/2-0.04`, highest wins (`32:~205`).
- What the DNA drives in play: the coat sheet (see 3.2), eye colour (6 named plus blue/aqua/pale `PET_EYES`), body-shape rig, sex pronouns, `ph.ps` personality (used in rank 29-rank and the brain), and `ph.kg`.
- Breeding is groundwork only: `petDnaGamete`, `petDnaConceive`, `petDnaLitter` (meiosis with crossing-over, mutation 2e-5, XXY 1/2500). Used for the stray litters on adoption and the cattery (`37`).
- Adoption flow: `petIntroHtml`, `petDnaNewLitter(4)` offers 4 kits; legacy coats `PET_COATS` are ginger, gray, black, tuxedo (`15:55`).

## 2. The brain

### 2.1 Clocks and loop
- Frame loop: `requestAnimationFrame(petFrame)` (`17:76-81`). It runs only if `petWantRun()`: not `document.hidden`, and either the Pet view with `#petCanvas` is active or the Overview with `#petPeekCanvas` is active and the native Overview cards are not covering it (`17:74`).
- It skips if less than 30 ms since the last frame. So the cap is about 33 fps (about 30 fps on a 60 Hz screen; the 90/120 Hz case is NOT DETERMINED). `dt = min(0.1, elapsed)`.
- Per frame order: `petSeenNow`, `petUpdate` (movement and step machine, `16:140`), `petDepthTick` (which calls `petLikesTick` and `petBrainFrame`, `24:186`), `petCamUpdate`, `petDraw`, `petBlit`, `petRenderStatus`.
- The brain ticks once a second (`PET_BRAIN.TICK_MS=1000`, accumulator `26:740-748`, `petBrainTick` `26:749`). Likes credit runs every 60 s (`25:235`). `petCheck` also every 60 s (`17:336`). The pair director checks every 6 s (`PET_PAIR_CFG.CHECK_S`, `29-pair:23-24`).
- Offline: when the page is closed nothing runs. On open, `petOfflineCatchup` (`26:630-657`) synthesises at most 12 steps (`OFF_MAX_STEPS`) at 30 min spacing (`OFF_STEP_MS`) from the day schedule. The result is up to 3 lines of text ("slept in the bed", "had a long wash"), a possible toy moved to a floor spot (`b.items`, max 3), mood decay and energy gain. No positions are simulated.

### 2.2 Daily routine (deterministic)
- `petBuildDay(p, day)` (`15:144-166`) builds blocks `{a,b,k,spot}` with kinds sleep, nap, rest, active. It is seeded `petRng(petHash(born|day|routine))`. Wake 6.1-7.3 h (+0.7 lazy, -0.3 playful). Bed 22.2-23.5 h (clamped to 23.5). Naps and rests are placed by sleeping/resting spot picks weighted by taste (`petLikesPickSpot` `25:~55`). Cached 7 days in `rt.sched`.
- Current block = `petBlockNow` (`15:168`). `rt.awakeUntil` can override sleep/nap to rest for 4 min after a wake-up.
- Small choices inside a block use `petMinuteRng` (reseeded every minute from `born|minute|moment`, `15:170`).
- Placement on open: `petPlaceForNow` (`16:111-128`) teleports the cat to the block's spot (using a minute-seeded RNG `born|minute|place`) with a 4-60 s first action. A saved `bond` position under 10 min old is used for floor placement (`24:121-126`).

### 2.3 Behaviour selection: plan queue
- Step kinds in `c.plan[]` (`16:129-139`, `140-183`): `walk {x,z?,run?}`, `hop {s:surface,x}`, `act {a:anim,d:seconds,...}`, `face {dir}`, `fn {f}`, `stalk`, `pounce`. `petRoute(c, surf, x)` (`15:204-217`) expands a destination into hop/walk steps through the surface graph (floor, bed, sill, tree mid/top, couch, counter, lsill, ksill, depth props pouf, cushion, box).
- `petThink` (`15:238`) runs when the plan is empty. It builds options `[name, weight, planFn]` from the block kind and the cat's state:
  - Hunger stage 4+ only rests by the bowl; stage 3 mostly rests plus an idle (`15:243-244`). Stage >= 2 begs at the bowl (`petBowlWait`, bubble and "beg" sound).
  - Sleep block: one `sleep` option (go to spot, knead if bond allows, sleep until block end).
  - Rest block: `goto` spot first, then loaf 5, idle 3, groom 2, window 4 (on a sill), slowblink 1.2 (L>=3), scratch, shift.
  - Active block: bowl, idle, groom, wander, explore (rooms), window, play (once per block), stretch, plus options from `petDepthOptions` (approach, solo, pouf, cushion; `24:128`) and `petLikesOptions` (`25:184`), roll, kick, bat, scratch, zoomies (evening or kitten), tree, rub (L>=2), gift (L>=5, once a day).
  - Trait multipliers: lazy 1.6, shy 1.7, playful 1.8, curious 1.7 on matching options. Kitten factor `kit = 1 - growth` scales activity up and idle down.
  - Then `petBrainArbiter` (`26:427-451`) multiplies each weight by category factors (`PET_OPT_CAT` `26:418`), by a smooth seeded noise of +-0.06 (`petNoise` `26:423`), and adds brain-only options (comfort, flop, greeble, hunt, shadow, gravity).
  - The pick is weighted-random from `r()` (the minute RNG). `petBrainPicked` (`26:452`) can swap in `frap` (zoomies) or record a "conflict" if the top two were within 12%.
- Pre-emption: touch, tap, hunger click, pair interactions, lens/glass visits and the brain call `petInterrupt(rt, plan)` (`16:110`). It just replaces `c.plan`, nulls the step and the hop. There is no blend (see section 7).

### 2.4 The brain (`26-pet-brain.js`, state `p.brain`)
- Affect: valence and arousal in [-1,1], plus petting saturation `s`. They decay toward a baseline (`petBrainBase` `26:103-113`) with `LAMBDA_V=0.15`, `LAMBDA_A=0.3` per minute. Baseline depends on trait, attachment, hunger stage, time block and dawn/dusk. Updates use soft limits (`petSoft` `26:115`). Mood word via `petMoodOf` (`26:117`).
- Four attachment threads `at.{fam,tru,sec,aut}` (`26:129`, bond style names `petBondStyle` `26:130-142`). Trust and security have a floor of 0.15. Daily petting trust is capped at 0.04 (`PET_TRUST_DAY_CAP`).
- Event bus `petBrainEvent(kind)` (`26:201`) with kinds feed, play, toy, pet_beat, startle, levelup, gift, greet, blink, respect, overstim, spam, auto_done, comfort_arrive, auto_cut. Each nudges affect and attachment.
- Memory: place weights `mem.w` (a distribution over `PET_SPOTS` plus `front_*`), 24 episodes and 8 core memories (`26:150-179`). Comfort zone choice feeds `comfort` plans.
- Player rhythm: 24 hourly bins `rt.h`, bump 0.15, decay 0.98 (`26:181-192`). Vigil if a peak hour (>= 0.55) and familiarity is high (`26:679`). Missed visit plan (`26:558`).
- Quirks (`26:35-50`): 8 innate (2 drawn per cat) and 4 acquired from counts n, t, g, k (nights, toys, gazes, meals; thresholds 10, 15, 20, 12).
- Touch handling (`26:228-289`): tap saturation 0.12, hold 0.09 per second, warning 0.65, max 0.85, belly-trap on sleeping belly-up, retreat and groom if over-stimulated. Slow blink offer/return (`26:291-323`). Respect reward 8 s after you stop (`RESPECT_WAIT_MS`).
- Glass visits (`OWNS_GLASS:true`): approach drive builds after 15 s idle with the room open, fires at 0.8 with a 240 s cooldown, variants sniff, bunt, loaf (`26:760-767`, `petPlanApproach` `26:478`). Quiet company (`petPlanParallel` `26:496`). Hunt, frap (zoomies), greeble, shadow, gravity, flop plans (`26:504-557`).
- Body language ("somatic"): ear (`forward, airplane_flat, radar_swivel, relaxed_side`), tail (`neutral, tip_twitch, low_swish, tucked_curl, question_mark, high_flag`), pupil (`medium, saucer_dilated, slit`). They are written to `data-ear/tail/pupil/mood` on the canvas parent (`26:325-344`) and used by the art redraws in 3.4.
- Voice: recorded clips (6 groups, 13 clips, `petSounds.html`) with per-cat pitch `b.vo.p`, plus synthesised trill, chirp, chatter, dream and breath tones using WebAudio (`26:394-416`). Purr is chained, crossfaded clips (`16:297`).

### 2.5 Play, box, sleep, likes
- Likes (`25`): 13 `PET_SPOTS` with attribute flags (sleep/rest weight, soft, high, hidden, sunny), 7 `PET_TOYS` with unlocks (level or days, plus purchase via `state.toysBought`). Taste = seeded random 0.6-1.5 times trait modifiers (`petTaste` `25:~28`). Daily credit from time spent (`petLikesDaily` `25:86`) and moments. `favSpot` needs 8 and a 1.15 lead, `favToy` 4 (`25:~24`).
- Play (`27`): `PET_PLAY` consts (swat escape 0.2, hit frame 3, hook frame 5, pat at 0.42 s). Games are pounce chain (`16:18`), floor swat, swat-and-chase, belly-up juggle ("hug"), wand dangle (`16:98-109`). Toy physics is hand-rolled in `petToyUpdate` (`16:9`: gravity 320, bounce 0.36 or 0.62 for the spring, friction by toy).
- Box (`28`): `PET_BOX` consts (jump 0.66 s, out 0.56 s, apex 20, squash 0.17 s, wobble 0.8 s). Sink depths come from the measured table `PET_BOX_FACE` (ear, eye-top, eye-bottom heights per growth stage).
- Sleep (`30`): `PET_SLEEP={HIDE_MAX:0.08,FRONT_DZ:6,TOY_P:0.85,TOY_P_KIT:1,PICK_R:34}`. A sleeping cat is never left more than 8% hidden by the pouf, cushion or box. His favourite toy is carried to bed.

### 2.6 Pair and social (`29-pet-pair.js`, `29-pet-rank.js`)
- A second cat (`state.pet2`) runs as a second runtime. `petAs(i, fn)` (`29-pair:47-54`) swaps the globals `PET_CTX`, `PET_RT`, `PET_EATX`, `PET_EATDIR` for the call, so all single-cat code serves both cats. Shared render objects are forwarded (`PET_PAIR_SHARED`, `29-pair:36`).
- This design relies on monkey-wrapping existing functions at load time (`petPairWrap`, `29-pair:474`): `petStartStep`, `petThink`, `petPlaceForNow`, `petDepthTick`, `petBrainScene`, `petDrawCat`, `petAssetsJson`, `petLoadImg`, `petAdopt` and others are wrapped by 29, 30 and 32. Order of segment concatenation matters (see 8).
- Director (`petPairTick` `29-pair:190`): every 6 s, 45% chance (`START_P`) to start an interaction when both cats are "free". Cool-down 40-110 s. A stage times out at 26 s.
- Interactions (`PET_PAIR_PLAYS` `29-pair:372`): `greet` (nose touch, may hiss, later a cheek rub), `chase` (stalk, pounce, run, tussle, repeat), `groom`, `snuggle` (nap beside), and `standoff` weighted by rank. Relationship `rel` starts at 0.1, the daily gain cap is 0.16, per-kind gains are in `GAIN`. Levels at 0.15, 0.3, 0.55, 0.8 (`PET_PAIR_LEVELS`). Gates: chase from rel 0.15 or a kitten present, groom 0.35, nap 0.6.
- Rank (`29-rank`): confidence `cf[2]` from DNA (`petRankBase`: 0.5 + 0.5*(dm-0.5) - 0.3*(sk-0.5) + 0.15*(ou-0.5), trait +-). Power = 0.36*dm + 0.26*growth + 0.38*cf. A contest is a logistic on the power gap (K=9, edge 0.03). Win/lose changes confidence by base + surprise*x. A "settled" order needs 3 contests and a 0.08 gap. Drifts 6%/day back to base.
- Single-occupancy spots (`PET_PAIR_SINGLE`: bed, sill, ksill, mid, top, box, pouf, cushion) are not jumped onto if the other cat is there.

## 3. Rendering

### 3.1 Technology
- Canvas 2D only. The Pet tab has `<canvas id="petCanvas">`, the full-screen overlay `#petFullCanvas` (created in `17:112-125`) and the Overview `#petPeekCanvas`. All three blit from one offscreen `rt.off` of `PET_HW*PET_OS` x `144*PET_OS` = 2304x576 (`15:~190`, `PET_OS=4` `24:7`), `imageSmoothingEnabled=false`.
- The whole room is redrawn every frame (`petDraw` `16:337-372`): sky gradient per window (56 horizontal line fills per window), stars, sun and moon arc, clouds, living/bedroom/kitchen background PNGs, claw marks, window light polygons, the depth-sorted scene, a multiply time-of-day tint, a lamp radial glow, and the lens fog.
- Views are pixel-scaled integer: `petPaint` (`17:103`) uses `s = floor(deviceWidth/192)` and shows a 192-wide window of the 576 house, or more on wide screens. Full screen uses a non-integer `min(w/192,h/144)` scale (`17:131-146`). The Overview card uses an integer pixel scale of 192x144 (`17:154-170`).

### 3.2 Cat art: sprite, not procedural
- Base sheets: 4 coats x one PNG. Each frame is 72x48. Rows are animations (28 rows, listed in `petAssets.html`; fps 1.5-16; `bottom` = foot row 46-47). Pixels store a grey index 0-210; `pal[coat]` maps 21 indices to RGB (e.g. `10` outline [26,16,28], `160/170` iris, `150` pupil, `120/130/140` nose and pad pinks, `20-110` coat bands) (`petInit` `15:176-190`). Index 0 is transparent. 8 growth stages x 4 coats have their own sheets (`petGrowth.html`) with their own `bottom` table.
- Front and back views: `depth` sheets `walk_front`, `walk_back` (6 frames, 10 fps), `sniff` (4 frames, 10 fps), `bonk` (2 frames, 6 fps). No front or back view for sit, sleep or any other action.
- Rows: idle_sit 8f/7fps, walk 8/12, run 6/16, sleep 4/2, loaf 4/2, groom 8/5, eat 6/5, play 6/9, stretch 7/4, lookup 4/3, slowblink 6/4, purr 4/4, rub 4/5, knead 4/4, meow 4/6, leap 2/4, idle_sit_w 8/5, walk_w 8/8, loaf_w 4/1.5, sleep_w 4/1.5, crouch 3/7, scratch 8/7, scratch_in 3/7, scratch_out 3/7, roll 8/8, kick 6/12, bat 8/9, swat 6/9. (`_w` = weak or hungry variants, `PET_WEAK` `15:137`.)
- Genetic coats: any coat key not in the four legacy ones starts with `dna_` and is composed from the four base sheets' index data (black = shape, ginger/gray = stripes, three white-spot masks) by `petDnaCompose` (`32:305`). It uses pseudo-URLs `dna:<kind>:<key>` handed to `petLoadImg` (wrapper `32:611-613`). Colour-points are detected from thin parts; long hair adds a fringed outline. The four legacy coats reproduce pixel for pixel.
- Style: 16-bit SNES-like pixel art, dark purple-black outline (26,16,28), palette-index recolouring, hard pixel edges, no anti-aliasing (the note on the first version says "the cat is drawn by a code rig, not by hand", `19:207`; 38's header says the frames are hand-drawn and cut into parts by `tools/rig_build.py` - the two statements disagree and I could not verify which is current).
- HD layer (`50-pet-hd.js`, data 3 MB `petHd.html`): a 2x (144x96) sheet per kind (adult, st0-st5, depth, dst0-dst5) and coat, all 28 rows plus 4 depth rows listed in the data. The face is drawn live at runtime (`petHdFace` `50-hd:109`, `petHdEye` `50-hd:74`) with expressions chosen by `petHdExpr` (`133`) and temporary moods `petHdMood`. A frame drawn with closed eyes keeps them closed. Falls back to the 1x sheet if the HD sheet is not ready or `state.hdCat===false` or more than 6 errors.
- Rig (`38-pet-rig.js`, data `petRig.html`, 385 KB): per frame it stores pixel index lists for ears (up to 2, base and tip), a tail polyline, whiskers, leg-stretch row band `l` and torso column band `x`. `petRigCompose` (`38:~58-135`) re-draws ears (rotate/scale about base), bends the tail along its centre line, trims or extends whiskers, stretches leg rows and torso columns by nearest neighbour, then repairs the outline. A canvas margin of 76 px left, 76 right, 44 top, 8 bottom is added. Result `{cv, ox, oy}`.
- Pose parameters exist: `pose={ear0, ear1, tail, flat, tw}` (`38:petRigPoseKey`) but only the brain's discrete states use them (`50-hd:150-159`): ears at -36/+36 degrees and `flat` for `airplane_flat`, tail offset `(tw-1)*7` for the twitch. For the non-HD path, ears and tail tip are re-drawn per pixel by `petMakeVariant` (`26:366-391`); pupil dilation is disabled in 1x art (`dil=false`, `26:356`).

### 3.3 Placement, scale and ground shadow
- Draw anchor is the foot row (`bottom`), at world `(c.x, c.y)`. `c.dir<0` mirrors by `scale(-1,1)` (`17:50-62`). Kitten scale `sc` is applied to the sheet (`17:30-31`). Depth scale `k=petK(c)` applies on top (`24:34`).
- At `k===1` the sprite is drawn at integer pixel positions. At other depths it is drawn at positions snapped to 1/4 px (`Math.round(v*PET_OS)/PET_OS`, `17:59-62`) and scaled with nearest-neighbour. Different cats at different depths therefore have different effective pixel sizes (1x art scaled by 0.55-1.85; HD art by half of that).
- Shadow: two filled rectangles, 2 px high, alpha 0.28 (`17:36-44`), shrinking with height and scale (`petShadowScalePct` `24:33`). Wider for sleep, loaf, knead (22 vs 15 half-width).

### 3.4 Animation approach and frame selection
- Frame index = `floor(c.t * fps * fpsMul) % n` where `c.t` is a cat-lifetime clock that is not reset at a step start. This holds for most rows. Exceptions: `leap` (0 for u<0.4 of the hop else 1), `swat` and `stretch/eat/play/groom/knead` use per-step time or `c.t` (`17:34-42`), `scratch_in/out` clamp to the last frame, `idle_sit` uses `petBlinkFrame` (`26:833`), and `c.lock` pins a frame (`lockF`).
- Effective cadence: game update at about 30 Hz; sprite cycles at 1.5-16 fps.
- Facing: horizontal mirror only.
- Motion: linear interpolation to the target (`petDepthWalk` `24:102-113`). `vx = 32*k` px/s walk, `96*k` run, `17*k` weak; `vz = 20 * walkSpeedPct/100 * (2.6 run | 0.55 weak | 1)`. Hop: parabola `y = lerp - 4*h*u*(1-u)` with `T = 0.42 + 0.0035|dx| + 0.004|dy|` (`16:129-139,150-153`).
- Particles: sprite sheets (hearts, notes, zzz, sparkle, exclaim) with constant velocity and 14 Hz blink at end of life (`24:230`).
- Camera: `cam.cx` eases to the cat with a lead of 14 px (26 when running) and rate `1.6-4.2` (`17:82-95`). Cut if the jump is over 260 px. Horizontal drag or the phone tilt (`deviceorientation gamma/25`) moves parallax layers by 2 to 16 px (`24:168-183`).
- Caches: palette-recoloured sheets `rt.sheets`, `rt.dsheets`, `rt.gs` (kitten), `rt.dsg`; HD frames `PET_HD.fr` (Map, 500 max, FIFO); rig frames `PET_RIG.out` per sheet (600 max) and `PET_RIG.fr`; ear/tail variants `PET_VAR` (WeakMap); DNA phenotype and recipe; `PET_PORTRAIT.cache` (4 PNG URLs); `rt.edge` (clean empty-room copy for side margins); `rt.sched` (7 days). There is a defence against the GPU dropping canvas pixels (`petSpritesLost`/`petRebuildSprites` `26:845`).

## 4. The room

### 4.1 Layout and coordinates
- World x runs -192..384 (`PET_HX0=-192`, `PET_HX1=384`, `PET_HW=576`). Cat x is clamped to -154..346 (`PET_HMIN/HMAX` `15:129`). Rooms by x: living `x<-4`, bedroom `-4..196`, kitchen `>=196` (`15:134`). Height 144 px. Three windows at offsets -152, 0, 280 (`15:132`).
- Furniture is painted into three background PNGs: `living` (x -192), `bg` (bedroom, x 0) and `kitchen` (x 192). These include sofa, cat bed back, cat tree, kitchen counter, windows, lamp. They are not separate sprites (so they cannot be depth-sorted individually). Separate sprites in `petAssets.room.items` (one atlas, `meta` = [x,y,w,h]): `bed_back` 56x16, `bed_front` 56x10, `bowl_empty/food/water` 18x10, toys `ball_a/ball_b/mouse/feather`, particle sprites, `sun/moon/cloud1/cloud2`. Depth props (separate PNGs): `pouf` 28x19, `cushion` 40x19, `box_back/box_front`, `yarn`, `spring`.
- Surfaces the cat can stand on, `PET_SURF` (`15:126-131`, `25:60`, `24:73-74`):

  | surface | y (feet) | x range | notes |
  |---|---|---|---|
  | floor | 132 at z 35 (`petFloorY(z)`) | -154..346 | depth walkable |
  | bed | 128 | x 36 | |
  | sill (bedroom) | 74 | 38..76 | |
  | mid (cat tree) | 100 | 146..158 | `top` reached from `mid` |
  | top (cat tree) | 64 | 150..158 | |
  | couch | 109 | -136..-60 | |
  | counter | 91 | 286..340 | |
  | lsill | 73 | -124..-72 | via couch |
  | ksill | 74 | 304..338 | via counter |
  | pouf | derived | x -34, z 45 | prop |
  | cushion | derived | x -120, z 66 | needs trust (max Z >= 66) |
  | box | derived | x 290, z 42 | `box_back` at z 41.8, `box_front` at 42.2 |

  Bowls: food x 104, water x 124, top y 126 (`PET_BOWL` `15:135`). Eating position x 87 facing right (second cat x 121 facing left).

### 4.2 Depth (`24-pet-depth.js`)
- Concept: "2.5D" painter's algorithm in one canvas. `posZ` 0..100 (0 back wall, 100 the glass). `posX` -100..100 maps to world x -154..346. `elevationY` 0..40 maps to up to 68 px above the floor (`ELEV_PX`). Saved in `bond` as `posX, posZ(1 decimal), elevationY` (`24:115-119`).
- Projection: `scale% = 55 + floor(z*130/100)` (94% at z 30, 146% at z 70, 178% at z 95). Feet y = `HORIZON_Y 98 + (132-98)*scale/100` (one-point perspective: z 35 gives scale 100% and y 132 so the old art lines up). Walk speed `70 + floor(z*60/100)`%. Shadow scale shrinks by `elev*2`. (`24:8-33`, `petDepthProject` `24:40`).
- Z limits: furniture painted into the walls would be drawn over the cat, so the cat must stay in front. `PET_Z_BLOCK` lists `[x0, x1, lowest pixel y]` of 7 furniture bases; `petZMinAt(x)` returns the z at which the feet are 3 px in front of the lowest one, capped at ART_Z 35 (`24:51-57`). Back wall line `PET_FLOOR_BACK_Y=118`. Walks that cross furniture first detour in front of it (`petZNeed`, `24:59,90-96`).
- How far forward: trust tiers: `max Z` = 55 (trust below 30), 84 (30 to 64), 100 (65 and up); a hungry cat of stage 3+ is held at 35 (`24:46`). The glass visit needs trust 65, stage under 2.
- Draw order (`petDepthScene` `24:213-234`): a list of `{z, draw}` sorted by z: bed_back z 30, bowls z 34, ball 34.8 (35.2 when kicked in front), gift 34.9, bed_front 40, props at their z, the cat at `c.z + 0.01` (the box swaps in `PET_SURF.box.z`), brain items, carried toy, the other cat. Particles and speech bubble after. The wall art is drawn before everything (z effectively 0).
- Parallax: per-item x offset from 4 anchor depths (0, 45, 75, 95 map to 2, 6, 12, 16 px); the wall art offset is the z=0 value (`24:170-176,228`). The cat on a raised surface uses z 0 offsets.
- Lens/glass: a lens fog pixel mask at z 95 (24x16 px breath puff, 1.8 s, 25% opacity, `24:236-242`) and a 3 px canvas "bonk" nudge via Web Animations plus haptic (`24:161-166`). `LENS_COOLDOWN_SEC=120` (own addition), `LENS_VISIT_PROB=30`.
- Camera: horizontal pan only; no vertical camera, no zoom, no perspective change. Static viewpoint (horizon fixed at y 98 of 144).
- Lighting: time-of-day multiply tint (`petTint` `16:329-335`: white 7.5-17 h, warm 17-19.5, blue-grey 128,138,196 from 21.5 to 5), window-light parallelograms (alpha 0.16 day, 0.12 night) and a lamp glow in the living room. No light per-object and no cast shadows other than the 2-px blob.

## 5. Where a cat is shown or affects the app

| Place | Source | What it does |
|---|---|---|
| Pet tab | `renderPetView` `17:289`, `petRoomHtml` `17:227`, `#view-pet` (`views/pet.html` is a one-line empty section) | Live canvas (tap opens full-screen), feed/play/slow-blink buttons, toy tray, bond bar, hearts, stats (last meal, meals today, days together), hint, care/vacation/rename/likes/inner-life details, DNA panel, pair "Together" card, cattery |
| Full-screen room | `petRoomFullscreenOpen` `17:112` | Overlay with its own canvas; Escape and back close it (`34-navigation:59`) |
| Overview cat tile | `dashboard.html` `.ov-tile[data-tile=cat]` `#petPeek`, `renderPetPeek` `17:325` | Live 192x144 peek canvas with name, hearts and mood line; hidden when no cat (`#ovDuo.solo`, `18:83`); tile editor id `cat` (`31-overview-edit:14,53,61`) |
| Native Overview cat card | `50-native-overview-cards:116-126`, `50-pet-portrait.js`, Kotlin `CatCard.kt`, `PetPortrait.kt`, `OverviewPortrait.kt` | One still: first cat sitting, PNG 96x64 room px at 2x (192x128), cropped and shown at an integer scale with nearest-neighbour; cached by key; polls up to 30 x 400 ms while art loads. Tap opens the web `pet` view |
| Overview next moves | `18-runtime:11-12` | Hungry cat card (priority 0 if stage >= 3, else 2.5); stray-kitten invite |
| Log rewards / XP | `44-rewards:59-66,147-149` | "Cat care" activity once a day if all three care actions are done; toy shop (`44:86,121-122`) |
| Quests | `50-quests-hand:53-56,139` | Three quests verified from pet counters |
| Journal coach | `47-journal-coach:31,55-58` | Day summary `care` and cat name |
| Voice and deep link | `45`, `46`, `50-deeplink`, `50-voice-gemini` | Feed and status by voice or `lifemax://cats/feed` |
| Toasts | many (`16:185`, `26:198`, `29-rank:~58`) | Level-up, new habit, upper hand |
| Haptics | `haptic([...])` in `petFeedClick`, hold, bonk | |
| Sound | `petSfxLoad` / `petPlay` | Plays only while the loop runs (`petSfxStopAll`); respects `fxOpt().sound` |
| Notifications, widgets | none | Deliberate: no notifications (`17:239`) |

## 6. Interfaces a native engine would need

The page must still own (today everything is page-owned; if the room goes native, these are the seams):
- Save: the web page is the sole writer of the `state` blob (ADR for native/web ownership is in `docs/architecture`; NOT DETERMINED here for the Cat). A native room needs a read/write bridge for `state.pet`, `pet2`, `petPair`, `petMemorial`, `careDay`, `toysBought`, `cattery` and the `petFeed/petPetDone/petPlayDone` side effects (`ecoCare` wrappers, `44:147-149`). Plan for the owner's rule on logic cutover (CLAUDE.md: moving saving from web to native is an Opus job).
- XP and economy: `ecoCare` writes to `state.activities` and calls `save()`; hunger cards (`18:11`), quests (`50-quests-hand:139`) and the journal coach read pet counters.
- Navigation: `navigate('pet')`, tile id `cat`, `{view:'pet'}` open link, full-screen back handling (`34:59`).
- Feed from voice and deep links: `lmDlFeedNow`, `petFeed` (`50-deeplink:42-57`).
- Overview portrait: `petPortrait(p, onReady)` contract (key, PNG data URL) consumed by Kotlin; a native renderer would replace it but must keep `CatPortrait.key` semantics and the null-while-loading case.
- Asset JSON: `petAssets` (365 KB, sprites as data URLs), `petGrowth` (898 KB), `petHd` (3 MB), `petRig` (386 KB), `petSounds` (420 KB) are all page-embedded; a native engine would need these decoded into native resources (sheet order and `pal` indices must be preserved).

Could be pure Kotlin (data and logic with no DOM):
- Care and bond maths (15:64-122): hours, stage, level, feed/pet/play limits, check/erosion/death, vacation shift.
- Day schedule and RNG (`petHash`, `petRng`, `petBuildDay`, `petMinuteRng`, `petLikesPickSpot`, `petTaste`).
- DNA: loci, meiosis, phenotype, recipe/key, morph values (all pure functions of `dna`).
- Brain: affect, attachment, memory, quirks, offline catch-up, the arbiter (all pure state plus callbacks; the dependencies on `rt` are positions and plan queue).
- Depth math (24:28-59), route planner (`petRoute`), pair director and rank.
- Animation selection tables (anim names, fps, frame counts, foot rows).

DOM coupled and has to be replaced in a rewrite:
- The 2D canvas draw (`petDraw`, `petDrawCat`, tint, particles), pointer handling (`petPointer`, `petDepthPointer`), WebAudio (`petPlay`, `petBrainVoice`, `petPurr`), `deviceorientation`, `data-*` attributes on the canvas parent, toast/haptic calls, `document.getElementById` status updates inside logic (`petRenderStatus`, `petBrainRender`, `petLikesRender`), the pet-pair monkey-patches and the `petAs` global-swap pattern, timers via `setTimeout`/`petWithRt`.

## 7. Weaknesses of the current animation, as visible in code

1. No blending. `petInterrupt` replaces the plan and nulls the step (`16:110`); `petStartStep` sets `c.anim` immediately (`16:133`). Frames switch at once. There are no transition clips between sit/walk/sleep/groom.
2. Phase pops. `f = floor(c.t*fps*fpsMul)`, where `c.t` is the cat's lifetime clock, so each new animation starts on whatever frame the clock lands on (`17:34-42`). Only `scratch_in/out`, `swat` and `leap` start at frame 0.
3. Jumps are two frames. `leap` shows frame 0 for the first 40% of the hop and frame 1 after (`17:35`); parabolic Y with no crouch, anticipation, stretch or landing squash except the box (`28`, bespoke).
4. Constant velocity. Walk, run and approach are linear lerps to the target with no acceleration or deceleration (`24:110`); the stride cycle is a fixed fps, so feet slip against speed except for `fpsMul` presets (1.8 run front/back, 0.6 weak, 0.45 stalk).
5. Direction change is a mirror. `c.dir` flips the draw scale in one frame (`17:50`); no turn animation. Front/back views exist only for walk (`walk_front/back`), sniff and bonk, and they pop in when `|dz|>|dx|` (`24:107`).
6. Secondary motion is discrete. Ears have 4 states (forward, flat, radar, relaxed), the tail tip has 3 offsets at 14 Hz or 10 Hz (`26:358`), and the rig's ear/tail transforms are driven only by those states. There is no spring or lag, no breathing, no head-follow or eye look-at, no whisker motion in time.
7. Blink is the only ambient face animation and is 8-frame `idle_sit` only (`PET_BLINK`, `26:831`); other rows have fixed eyes (HD draws a face, per expression, per frame, cached).
8. Mixed pixel density and scaling. 1x art scaled by 0.55-1.85 with nearest-neighbour at 1/4-px snapping next to 2x HD art; pixels visibly change size with depth and shimmer while moving in z. HD coverage is stated as "walking" in the header (`50-hd:1-5`) though the data lists all rows (NOT DETERMINED which are actually drawn at runtime).
9. Rig applies only to proportions (static per cat) plus discrete poses; it cannot rotate the head, bend the spine, or pose legs. Parts carry no depth ordering beyond "tail under body, ears and whiskers over".
10. Ground contact is a flat 2-px blob shadow; no light direction, no contact shadow, no occlusion between cat and painted furniture (handled by `PET_Z_BLOCK`, i.e., by forbidding positions).
11. Frame cap about 30 fps and a game step of 30 Hz with `dt<=0.1`. Rendering redraws the entire 2304x576 offscreen each frame; screen is re-blitted for each visible canvas.
12. Camera is 1D. No vertical movement, zoom or depth-of-field. The tilt parallax is "untested" per the note at `24:251`.
13. Interactions are scripted step lists (`petPairChase...`). Two cats move independently except through `petPairWalkStart` offsetting (`29-pair:108`); contacts (hiss, groom) are staged by position, not by animation sync.

## 8. Hard-coded numbers and magic that must be preserved for save compatibility

Save and identity:
- Storage key `lifemax_rpg_prototype_v1`; `SAVE_SCHEMA=1`; `state.pet`, `state.pet2`, `state.petPair`, `state.petMemorial`, `state.careDay`, `state.toysBought`, `state.hdCat`, `state.cattery`; `p.dna.v=1`, `p.likes.v=1`, `p.brain.v=1`, `petPair.v=1`, `rk.v=1`. A newer `schema` makes the save read-only (`04:28`).
- Brain migration requires specific fields (`b.af, b.at, b.mem, b.rt, b.qk, b.off`) and 24-bin `rt.h` (`26:62-86`). Bond maps old `bondPts` onto attachment: `fam = 0.15 + 0.75f`, `tru = 0.2 + 0.7f`, `sec = 0.2 + 0.65f`, `f = bondPts / 580`.
- Date keys: two formats are in use: `p.day`/`likes`/`brain` use `getToday()` = `en-CA` `YYYY-MM-DD` (`04:84`); the schedule and the likes/offline keys use unpadded `YYYY-M-D` (`petDayKey`/`petKeyOf`, `15:140`, `25:85`). Both feed seeds, so neither can change.

Seeds (all determinism depends on these exact strings and the hash/RNG):
- `petHash` = FNV-1a 32 (offset 2166136261, multiplier 16777619) (`15:141`); `petRng` = a mulberry32 variant (increment 0x6D2B79F5) (`15:142`).
- Seed strings: `born|day|routine`, `born|minute|moment`, `born|minute|place`, `born|brain`, `born|<kind>|<id>|taste`, `born|<name>|<10-minute bucket>` for noise (`26:423`), `born|off|<off.at>`, `born|dna|coat` (legacy DNA), `<dna.id>|<locus>` (morph fill), `<dev>|dev`, `<dev>|<trait key>` (env).
- Because the day plan is a pure function of `(born, date, trait)`, changing `petBuildDay`'s order of `r()` calls, constants or `petLikesPickSpot` would change what existing cats do at a given time. This includes the wake/bed formulas (6.1+r*1.2, 22.2+r*1.3) and block lengths (`15:144-166`).

Genetics (changing any of these changes existing cats' looks):
- `PET_DNA_CHROMS`, locus ids and allele order and frequencies (`32:55-70`), QTL ids and chromosomes and positions (`32:73-83`, `37`), score formula and `h2`s, `PET_EYES` order (index into `ec` map `[0,0,1,1,2,3,4,5,5]`), white grade rules, trait choice `lazy = 1-(ou+sp)/2-0.04`, `PET_MORPH` R values, the `0.35..2.8` clamp, `legacy`/`neutral` flags, legacy coat recipes `PET_DNA_LEGACY`.
- Coat key format `dna_<base>_<tortie>_<tab initial>_<white>_<silver>_<point|n>_<long>_<eye>` (`32:petDnaKey`) and the pseudo-URL `dna:<kind>:<key>`; palette grey-index meanings (white/spot index sets `petDnaIsW/S/F/P` 90-110, 60-80, 20-50, 75/85) (`32:301-304`).
- Sprite sheet layout: 72x48 frame, row order and frame counts (`petAssets.anims`), `bottom` rows, growth stages (`sx`), `depth` rows, `petRig`/`petHd` data indexed by the same row names and frame indices.

Economy and care:
- Meal gap 4 h; hunger thresholds 8/14/26/50/120 h; 96 h erosion of 8/day; 168 h death; bond gains 6/2/3 with daily caps 2/3/2; `PET_BOND_AT` values; kitten days 30; `fedToday` caps; gift once a day at L>=5; `ecoRestedTake(10)` for cat care XP; the three quest verify strings.

Position and room:
- `bond.posX/posZ/elevationY` map: posX = `(x - 96)/250*100` using `PET_HMIN=-154`, `PET_HMAX=346` (`24:36-37`), `posZ` 0-100, `ELEV_PX=68`. Depth constants (`PET_DEPTH` `24:8-26`): ART_Z 35, FLOOR_Y_ART 132, HORIZON_Y 98, scale formula 55 + floor(z*130/100), tiers 55/84/100, trust thresholds 30/65.
- `PET_SURF` y/x values, `PET_Z_BLOCK` rows, `PET_PROPS` positions, spot table `PET_SPOTS` keys (saved in `likes.spots` and `brain.mem.w` keys, including the `front_living/bedroom/kitchen` zone ids), toy ids (`PET_TOYS` keys saved in `likes.toys`, `brain.items`).
- Quirk ids (`PET_QUIRKS`), core-memory kinds, `br` flags are internal to the session; `b.qk.inn/acq/c`, `b.sl`, `b.zm`, `b.vo`, `b.hu`, `b.co`, `b.off`, `b.items` are saved.
- `PET_BRAIN` and `PET_TRAIT_BASE` values are tuning, not saved, but they define the seeded noise and baseline drift that existing `b.af` values drift toward.

Load-order and wrapper fragility (matters for any port that reuses the JS):
- Segments wrap earlier functions by reassigning globals (`petPairWrap` in 29, 30, 32; `44-rewards:147-149` wraps `petFeed` etc.; 32 wraps `petDrawCat`, `petAssetsJson`, `petLoadImg`, `petBrainArbiter`, `migratePetBrainState`, `toast`). The order is the filename order in `src/runtime/segments/`. Both `29-pet-pair.js` and `29-pet-rank.js` share the prefix 29 (their relative order is NOT DETERMINED from this survey).

## 9. Tests that touch the pet system
- `tests/pet-portrait.test.js` (134 lines, Playwright): portrait pixel-equals the room's own 4x draw of the cat; cached per look; second cat; null for a dead or loading cat.
- `tests/pethd.test.js` (68): HD data (hs 2, 14 kinds, 28 adult rows, 4 depth rows, face anchors), HD vs 1x.
- `tests/rank.test.js` (32): confidence from DNA, odds, underdog gain.
- Python acceptance (Playwright, minutes each, run against a built `dist/*.html`): `pet_brain_acceptance.py` (245 lines), `pet_dna_acceptance.py` (legacy coats pixel-exact, ratios, litter), `pet_pair_acceptance.py`, `pet_play_acceptance.py`, `pet_box_acceptance.py`, `pet_sleep_acceptance.py`.
- Cat appears in Overview fixtures (`tests/fixtures/overview-classic/established-pet.json`, `-two-cats`, `-pet-starving`) and Kotlin tests (`OverviewPortraitTest.kt`, `OverviewPetPortraitScreenshotTest.kt`, `OverviewSemanticsTest.kt`). I did not open the fixtures' pet blocks (they appear to hold the rendered card, not `state.pet`; NOT DETERMINED). Existing saves in `tests/fixtures/saves/*.json` contain no pet.
- Other tests that merely reference pets: `coach.test.js`, `deeplink.test.js`, `hand.test.js`, `voice*.test.js`, `native-overview-cards.test.js`.

## 10. Items I could not determine
- Whether all 28 HD rows are fully drawn at runtime (data lists them; header text says walking only).
- Actual frame rate on 90/120 Hz devices (30 ms gate implies about 30 fps at 60 Hz, 33 or 25 fps patterns at other rates).
- Whether `38-pet-rig` frames are hand-drawn art or code-generated (`19:207` vs `38:1-4`).
- Relative load order of `29-pet-pair.js` and `29-pet-rank.js`.
- Exact byte layout of the pet blocks in Overview fixtures; no `state.pet` fixture in `tests/fixtures/saves`.
- Whether `docs/architecture` has a Cat-specific ownership plan beyond ADR 0003 (only searched filenames).
