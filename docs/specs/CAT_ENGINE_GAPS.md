# Native cat engine: what is still missing

Status: sections 1 to 7 are the audit (docs only, written before any code changed); section 8 records what was built from it (work packages C0, C1, C4 and the breeding check) and section 9 the decisions still open. The audit lists what the native cat engine
(Kotlin, `android-wrapper/native/src/main/java/com/lifemax/rpg/feature/pet`) still lacks compared with
the web page's pet system, so the owner can decide what to fill.

How this was made: I read the plan and as-built documents named in the task, grepped the page's pet
segments for each capability, and grepped the Kotlin sources for the matching piece. I did not run
the app, the page, or any test, and I did not open every file end to end. Anything I did not read or
run is marked **unverified**. Line numbers are from this worktree (base `origin/codex/director-dial-controls`).

Citations: page files are under `lifemax_modular_v73/src/runtime/segments/` and written
`file:line` (so `37-pet-morph.js:96` is that file, line 96); data files under
`lifemax_modular_v73/src/data/`. Kotlin paths are relative to
`android-wrapper/native/src/main/java/com/lifemax/rpg/feature/pet/` unless they start with `app/` or `bridge/`.

Words used:
- **Page** is the web app inside the phone app. It is the only writer of the save today (the rule in
  `CAT_TAB_PLAN.md` section 0 and `CAT_BRAIN_PLAN.md` section 0). **Native** is the Kotlin side.
- **Projection** is the read-only snapshot the page sends native to draw (`pet.projection.v1`,
  `pet.panels.v1`, `pet.world.v1`).
- **Command** is a request native sends the page ("feed", "play", "social"); the page decides and saves.
- **Blob** is the one opaque chunk of behaviour state native hands the page to store in
  `state.petWorld` (`CAT_BRAIN_PLAN.md` 2.3).
- **Genome** is the cat's stored genes (`state.pet.dna`, about 0.8 KB, `32-pet-dna.js:25`). **Gamete** is
  one parent's half of a genome. **Locus** is one gene position. **QTL** is a trait built from several
  genes that add up (ear length, personality).
- **Golden case** is a recorded input and expected output kept as a test, so the Kotlin copy must match
  the page's answer exactly.
- **Cutover** (CT-L) means moving a piece of logic and its saving from the page to Kotlin.
- Size: S is under about 300 lines with tests, M about 300 to 1,200, L more than 1,200 (rough, +/-30 %).

Where things stand (read from `CAT_TAB_PLAN.md`, `CAT_BRAIN_PLAN.md`, `PET_TAB_INVENTORY.md`,
`MIGRATION.md`): the Cat tab, touch, panels, rig, 2.5D room, parkour, the native brain (CB0 to CB2, CB4
part A and the first slice of part B, CB5 pair), native Play and the demo are built. Native owns
**behaviour** (what a cat does minute by minute, drawn). The page still owns **identity and
economics**: growth, hunger, bond, XP, coins, genetics, adoption, breeding, album, saving. Not built
as of this audit: CB3 (owner interaction and moments), the rest of CB4 (solo play, gifts, dwell),
CB6 (cheers and zoomies), CB7 (sound), CB8 and CT10 (default on, retire the page room). The Cat tab is
still an opt-in beta (Settings, "Native Cat (beta)", `NativePetPlugin`/`50-native-pet.js`; no
`defaultOn` found, unverified on the phone).

---

## 1. Breeding: the plain answer

**Short version.** The page has a real breeding system. A kitten can come about two ways: by
adoption of a random litter (no parents of yours), or by pairing two cats you own in the Breeding
lab. Native has none of the breeding: no pairing, no litter, no cattery screen, no adoption screen. The
Cat tab says so itself ("the cattery and adopting another cat open on the page",
`50-native-pet.js:40`, `50-native-pet-panels.js:25`).

### 1.1 What exists on the page

Everything is in `37-pet-morph.js` (284 lines) plus the genome code in `32-pet-dna.js` (712 lines).

1. **Adoption of a first cat.** The intro offers either four fixed coats (`PET_COATS`, `15-runtime.js:55`)
   or a litter of four random kittens (`petDnaNewLitter`, `32-pet-dna.js:482`). That function invents a
   random mother and a random father (`petDnaRandom`, `32-pet-dna.js:90`) and conceives four kittens from
   them, so even adoption uses the breeding code. The player picks one kitten, a name and a personality
   label, "Another litter" re-rolls (`petNewLitter`, `32-pet-dna.js:505`). The chosen genome is passed on as
   `window.__petDnaNext`. The cat is then created by `petAdopt` (`15-runtime.js:65`).
2. **Adoption of a second cat.** "A second stray by the kitchen window" (`29-pet-pair.js:393`):
   pick one of the four coats, a name, a personality, "Let them in" (`petPairAdoptClick`,
   `29-pet-pair.js:409`). Maximum two living cats at home (`petPairAdoptClick` refuses when `state.pet2.alive`).
3. **The Breeding lab** (`petBreedHtml`, `37-pet-morph.js:133`, a collapsible block under the Cat room):
   - **Cattery**: extra cats kept as genomes only, no body or behaviour. Capacity `PET_CATTERY_MAX = 16`
     (`37-pet-morph.js:97`). Saved in `state.cattery = {v, cats[], goal, litter}` (`37-pet-morph.js:98`).
   - **Who can breed**: any female (XX) with any fertile male (XY). The roster is the two home cats plus
     every cattery cat (`petBreedRoster`, `37-pet-morph.js:99`). The only barrier is sex and the rare XXY male,
     who is sterile (`petDnaFertile`, `32-pet-dna.js:87`). There is **no age limit, no relatedness
     check, no cooldown and no cost**.
   - **Timing**: instant. The "Breed" button (`petBreedGo`, `37-pet-morph.js:205`) rolls the litter at once.
     No pregnancy, no waiting, no real-time clock.
   - **Litter size**: random 2 to 6 by a fixed table: 15 % two, 30 % three, 30 % four, 20 % five, 5 % six
     (`petBreedGo`, `37-pet-morph.js:206`).
   - **Inheritance model**: real meiosis. Each parent contributes one gamete (`petDnaGamete`,
     `32-pet-dna.js:99`): for each of 18 autosome chromosome pairs plus X, a random one of the two copies
     starts, and crossing-over switches copies between loci with a map-distance rule (about 1 centimorgan per
     Mb, Haldane function). A male sends his X or his Y with equal chance. Fertilisation
     (`petDnaConceive`, `32-pet-dna.js:118`) pairs the two gametes: XX female or XY male, about 1 in 2,500 XXY
     (`PET_DNA_XXY`, `32-pet-dna.js:71`), and each locus has a 2 in 100,000 chance of a new mutation per gamete
     (`PET_DNA_MUT`, `32-pet-dna.js:70`). Offspring record their parents' ids (`par:{m,f}`) and a generation
     counter (`dna.gen`, shown on the card).
   - **What is inherited**: the coat genes at their real named loci with real dominance (O on the X, B, D,
     A, Mc, Ta, C, W, L and so on, `32-pet-dna.js:8-14`), white spotting, eye colour, size, six personality
     scores (the "Feline Five" plus vocal) as QTL with a per-cat fixed non-inherited share (`petDnaScore`,
     `32-pet-dna.js:131`), and from v93 six body-shape traits (ear length, ear width, tail length, leg length,
     body length, whiskers) each over 3 to 5 loci, plus a recessive bobtail gene (`37-pet-morph.js:13-30`).
   - **Names**: kittens in a litter are "Kitten 1..n" until kept; "Keep" gives a random free name from the
     12 stock names plus 12 more (`petBreedName`, `37-pet-morph.js:108`), "Kit 2" and so on if all are taken.
     The owner can rename a cattery cat (14 characters) but not before keeping.
   - **Selection game**: a goal (a body trait and a direction) with a forecast of the expected value for a
     chosen pair from 120 simulated kittens (`petBreedForecast`, `37-pet-morph.js:167`), and a developer
     test that breeds ten generations to prove selection works (`petDevBreedEvolve`).
   - **Flow**: litter shown with previews, per-kitten Keep or Release, Keep all, Release all, "Another
     stray" adds a random unrelated adult to the cattery, "Bring home" moves one cattery cat into the second
     slot if it is free (`petBreedHome`, `37-pet-morph.js:232`), which builds the cat with
     `petPairAdopt(...)` using the stored genome and `born`. Release uses a browser confirm dialog.
4. **Not part of the lab**: you cannot start a litter from the two home cats "naturally" in the room (no
   courtship, no pregnancy shown, no kittens appear on their own). The cats' mating is not simulated.
   Nothing in the achievements, quests, album or XP rewards breeding (grep of `44-rewards.js`,
   `50-pet-album.js`, `50-pet-moments.js` for breed, cattery, litter found nothing).

### 1.2 What native has

- **Read-only genetics display**: the Genetics card (`presentation/PetDnaCard.kt`, 267 lines, from
  `data/PetPanelsJson.kt`, `domain/PetPanels.kt`) shows the rows the page builds (sex, karyotype, coat,
  genotypes, carries, hidden genes, personality bars, the full genome table when opened). The page writes
  the words (`petDnaPanelData`, `32-pet-dna.js:536`); native only draws them.
- **Genes that change what is drawn**: the six body-shape multipliers reach the rig renderer
  (`art/RigCompose.kt`, the `morph` fields of the projection), the personality scores feed the brain
  (`brain/Persona.kt`, `world/WorldBlock.kt:34`), and the coat is a `dna_...` key whose sheets the **page
  composes** and native requests (`art/PageCoatSource.kt`, command `coat {key, kind}`). Native recolours the
  four stock sheets itself (`art/Recolour.kt`) but has no code that composes a genetic coat. **Coat
  generation is page-only.**
- **Nothing else.** A search of all native Kotlin for `breed`, `cattery`, `litter`, `gamete` found only
  an unrelated constant (`SPLITTER` in `core/lifedirector/PageMath.kt`) and the two explanatory
  strings in the panels. No `adopt` for cats either (the other `adopt` hits are other tabs' view models).

### 1.3 What is missing in native, and what must be decided

Missing in native: the whole lab (roster, pairing, goal, forecast, litter, keep and release, bring
home, rename, stray), the adoption screens (first cat, second cat, "Open the door again"), kitten
previews drawn from genomes, and any native rule for who may breed.

Decisions the owner has to make before more code (recommendations in section 5, WP-C2 to WP-C5):
- Keep breeding as an unrestricted instant lab, or make it part of the cats' life (courtship, a due date,
  kittens arriving)?
- Which cats may breed: any sex-valid pair, adults only, not parent and child, not siblings?
- How many kittens, and how often?
- Who names them, and when?
- Does a kitten that stays in the cattery grow up in real time (it does today: `born` is the day it was kept)?
- Do kittens and parents get to be in the room at the same time, and what is the house cap (two cats today)?

### 1.4 Recommended design if the owner wants breeding to be part of the game

The page lab is already a sound genetics model and should not be rewritten. Keep it, and add a thin,
slow, real-time layer on top so breeding is something that happens to the owner's cats, not a menu.

1. **Who**: an adult female (XX, 30 days old or more, the growth end `PET_GROW_DAYS = 30`,
   `16-runtime.js:55`) and an adult fertile male, both at home or in the cattery, not full siblings and not
   parent and child (the genome already records `par`, so this is a lookup). One litter per mother per 14 real
   days.
2. **How it starts**: the owner taps "Pair them" on the Together card of two compatible home cats, or in the
   lab for cattery cats. Home cats must have friendship at or above "friends" (the page's pair level) so the
   existing pair relationship matters. No hidden randomness about whether it "takes".
3. **Timing**: a short real-time wait, 3 days, shown as "expecting" on the mother's card. During the wait
   the mother's brain gets a small tired and nesting modifier (sleeps more, likes the box and bed). No
   health risk and no loss: the app is a calm companion and never punishes.
4. **Litter size**: keep the page's table (2 to 6) but cap what can live at home by the house cap, and let
   the rest go to the cattery (16) so nothing forces a release.
5. **Inheritance**: exactly the page's `petDnaConceive`, unchanged, so coats and shapes the owner already
   knows keep working. Drawn with a seed derived from the parents' ids and the due date so a litter replays
   exactly (the page uses `Math.random` today, `37-pet-morph.js:206`, which makes a bug unreproducible).
6. **Names**: kittens arrive unnamed; the owner names each when keeping (a name field in the keep step),
   with the stock-name suggestion as a placeholder.
7. **Care of kittens**: a kitten kept at home follows the same feeding rules and 30-day growth. A cattery
   kitten shows its age and grows in the background (today it does, because `born` is the keep date).

If the owner wants a simple answer: **make no change to the rules; port the lab to a native screen
unchanged (WP-C3) and treat anything in this section as a later optional package (WP-C5)**.

One finding worth fixing whichever way: the "Another stray" button promises "a random unrelated adult"
(`37-pet-morph.js:163`), but the stray is stored with `born: Date.now()` (`petBreedAdd`, `37-pet-morph.js:212`),
so when brought home it starts as a newborn kitten, not an adult. Either the label or the `born` is wrong.
Unverified on a device; reading the code, this is what happens.

---

## 2. Capability audit

Reading the tables: **Native** says `full` (native does it), `partly`, `draws only` (draws a projection
the page computed), or `absent`. **Writer** is who stores the result today: page or native. "Phone check"
is what the owner should look at.

### 2.1 Care and growth

| Capability | Page | Native | Writer | Size | Depends on | Risks | Phone check |
|---|---|---|---|---|---|---|---|
| Growth stages: kitten to adult over 30 days, six stages of 5 days (`PET_GROW_DAYS`, `petGrowIdx`) | `16-runtime.js:55-59`, `petGrowth.html` | partly: draws the stage the page sends (`grow{day,of,show}`, `art/Growth.kt`, `body/Tuning.kt`), the brain reads `stage` for kitten rules (`brain/Persona.kt`). Age is never computed in native | page | S to move the day count; M with art checks | projection | stage art per coat (the as-built notes list hand-touched pieces still missing) | a kitten's size and walk at day 0, 12, 29 |
| Hunger stages 0..5, 4 h between meals, 168 h death, 96 h bond erosion | `15-runtime.js:76-115` | draws only: hunger stage arrives in the world block (`CatProfile.hungerStage`), the brain begs and the body looks weak | page | M | none until cutover | deleting the page check without the memorial flow breaks passing | a very hungry cat at the bowl |
| Feeding (button, bowl walk, eat, wash) | `16-runtime.js:260`, `15-runtime.js:86` | full for the body (`brain/CareActivities.kt`, `body/Feeding.kt`), the **rules** are the page's via command `feed {op}` | page | done | n/a | two cats walking to bowls (fixed in section 16 of the inventory; re-verify) | feed two cats at night |
| Water | page shows a water bowl prop (`15-runtime.js:135`, `24-pet-depth.js:246`) and a "had a drink of water" line in the away story (`26-pet-brain.js:628`). There is **no thirst need and no refill** | absent (grep of native for water found nothing) | none | S if wanted | none | none | n/a |
| Litter, toilet | **absent on the page** (grep `litter box`, `litter tray`: none in the pet segments) | absent | none | S to M if wanted, needs art for a tray | art | adds chores to a "no pressure" pet | n/a |
| Vacation (clock pauses) and rest day | `15-runtime.js:116`, `17-runtime.js` | full for the effect on behaviour (block fields, `CatMind` "vacation:on|off"); command `vacation {on}` | page | done | n/a | none seen | Settings toggle, bowl stays full |
| Rename | `15-runtime.js:120` | command `rename`, native UI in the care card | page | done | n/a | none | rename to 14 characters |
| Death, memorial, "Open the door again" | `15-runtime.js:105-123`, `17-runtime.js:247-262` | absent: the tab closes when the mode leaves "room" (`blocked()`), the page shows the memorial | page | M | CT6b | the first screen a new player sees is the page's | let a test cat pass, see the memorial |

### 2.2 Mood, personality, trust, memories

| Capability | Page | Native | Writer | Size | Depends on | Risks | Phone check |
|---|---|---|---|---|---|---|---|
| Mood (valence and arousal), shown through ears and tail | `26-pet-brain.js` (923 lines; mood and `petAffect`) | **full, rewritten**: `brain/Mood.kt`, `body/BodyTone.kt`, `body/Ears.kt`, `body/Tail.kt` | native owns it, saved in the blob | done | n/a | not a copy of the page numbers by design (owner decision, `CAT_BRAIN_PLAN.md`) | watch the ears over a day |
| Personality (six scores: skittish, outgoing, dominant, spontaneous, friendly, vocal) | `32-pet-dna.js:55-66`, label `p.trait` | full as inputs: `brain/Persona.kt` | page (genes) | done | n/a | the page's old four-label trait and the six scores can disagree on a cat from before v87 (page notes `32-pet-dna.js:36`) | compare a shy and a playful cat's day |
| Bond points and the six levels (Stray, Wary, Curious, Friendly, Fond, Devoted) | `15-runtime.js:56-59,94-100` | draws only (bar and words in the Cat tab) | page | M for the maths | cutover | the bond sums need the care caps (3 pets and 2 plays a day) | the bar after a stroke |
| Trust thread (familiarity, trust, security, autonomy) | `26-pet-brain.js` `at` | **partly**: `MindState` keeps four trust values (`brain/MindState.kt`) imported from the page once (`host/WorldImport.kt`); nothing in the brain yet moves them from owner actions (CB3 not built) | native blob | M | CB3 | the two trust numbers (page level and native thread) can drift | **unverified** |
| Mind row and inner life panels (style, mood word, threads, quirks, memories) | `26-pet-brain.js:801-826` | draws only, with the world's own mood word and away story swapped in (`presentation/PanelSource.kt`) | page and native | done | n/a | the four threads come from the old brain once and then freeze | open Inner life |
| Quirks (innate and acquired) | `26-pet-brain.js` `PET_QUIRKS` | partly: innate quirks imported; acquired-quirk counters not advanced (the `quirk` moment report is decoded but never produced) | page | M | CB3 | the page's brain stops ticking when native owns the cat, so quirks stop growing: **unverified** | play 10 days, check quirks |
| Memories, "first" moments (glass, comfort, zoomies, belly, vigil, trophy, blink, respect) | `26-pet-brain.js`, `50-pet-moments.js` | **absent**: `MomentReport` exists in the contract (`world/WorldCommands.kt`, kinds `first_glass` ... `quirk`) and the page decoder accepts it, but nothing in the native brain creates one (grep for `MomentReport(` finds only the decoder `world/WorldCommandJson.kt:146`) | page | M (CB3) | CB3 activities | the album and the inner life stay quiet while native owns the cat | see section 2.6 |
| While you were away story | `26-pet-brain.js:630-657` `petOfflineCatchup` | **full, new**: `brain/CatchUpRun.kt` plays the last hour fast and gives up to three lines, pair moments included | native | done | n/a | gaps over 24 h only simulate the last 24 h (by design) | close the app for 3 h, reopen |

### 2.3 Two-cat social

Native has this (`brain/SocialDirector.kt`, `PairPlays.kt`, `PairRests.kt`, `PairActivities.kt`,
`SocialChoice.kt`, `SocialTuning.kt`, poses in `body/PairPoses.kt`, command `social`, page recorder
`50-native-pet-social.js`). Page source: `29-pet-pair.js` (599 lines) and `29-pet-rank.js` (101).

| Behaviour | Native | Gap |
|---|---|---|
| Nose touch greeting, cheek rub, hiss | full (`PairPlays`, below friendship 0.25 can end in a hiss) | none known |
| Chase with stalk, pounce, run, tussle, roles swapped | full | none known |
| Grooming each other, favour returned | full | none known |
| Curl up and nap together (sofa, rug, bed) | full | art contact per growth stage is a phone check |
| Standoff over a perch (pecking order) | full, the page's rank rules (`petRankContest`) | winner climbing is a phone check |
| **Bump** (the head bump) | absent: there is no head-bump activity in the brain (grep `HeadBump` finds nothing); the plan put it in CB3 | add as a pair or owner act, needs the `head_bump` art row (`PET_ART_LAB.md`) |
| Friendship levels, daily cap 0.16, notes, counts | page records them (`petPairGain`, `petPairNote`) from the `social` command | page remains the writer; fine |
| First meeting | brain offers it once; catch-up does not repeat it | none known |
| Kitten shadowing an adult | full (`Shadow`) | none known |

Writer: page (through the `social` command). Size to finish: S (the head bump only). Phone check:
section 17 of `PET_TAB_INVENTORY.md` lists three (contact per stage, rate in a real day, the standoff climb).

### 2.4 Toys, play, sleep, houses, parkour, touch

| Capability | Page | Native | Writer | Size | Depends on | Risks | Phone check |
|---|---|---|---|---|---|---|---|
| Toy list, unlocks, words (Favorite, New, Loves it), early unlock with Sparks | `25-pet-likes.js`, `44-rewards.js:6,85,120` | draws only, plus command `buyToy` (`presentation/PetToysCard.kt`) | page | done | n/a | none | buy a toy |
| Owner-offered play: tray, cat fetches, carry, toss, bat, chase, pounce | `27-pet-play.js` (145 lines), `16-runtime.js:9-27,98-109` | **full** (`brain/PlayActivities.kt` OwnerPlay, `body/Toy*.kt`, `body/Carry.kt`, `body/PlayActs.kt`, `presentation/PetToyTray.kt`) with the page counting the game via `play {op,cat,toy,played}` | page counts, native plays | done | n/a | the **feel** needs the phone (inventory section 16) | offer each toy to each cat |
| Solo play (a cat plays alone with a toy it likes) | web brain option `toy` (`26-pet-brain.js:640`) | **absent**: the only toy activity is the owner's game (`PlayActivities.kt` has one class). Toys lie about the room (`host/WorldToys.kt`) but no cat picks one up unasked | native | M | CB4 part B | likes (favourite toy) need dwell to move (below) | leave the app; do toys move? |
| Gift tap (cat leaves a present) | `16-runtime.js:204-209`, `p.gifts` | **absent**: `GiftTaken` and the blob's gift field exist in the contract, nothing stages a gift | page | S to M | CB4 | none | none yet |
| Dwell (time at a spot or with a toy feeds favourites) | `25-pet-likes.js` | **absent**: `DwellReport` is decoded (`world/WorldCommandJson.kt:113`), never produced | page | S | CB4 | when native owns the cat the favourite place and toy stop changing, **unverified** | check Favorite place after a week |
| Sleeping spots: bed, sofa, cushion, rug, box, tree, sills | `30-pet-sleep.js` (141), `28-pet-box.js` (190) | **full as choice** (`brain/Spots.kt` has bed, sofa, cushion, box, tree, sills; `brain/SpotBook.kt`; hidden-spot rule, `body/Occluders.kt`) | native | done | n/a | **box sink** (the cat sitting lower than the rim, `PET_BOX_FACE`, `28-pet-box.js:27`): I found no sink code in native (grep `sink` has no hit in `body/` or `brain/`); how the box looks in 2.5D is **unverified** | watch a cat in the box |
| Sleeping with a toy | `30-pet-sleep.js:61-68` | absent | page | S | solo play | none | n/a |
| Cat tree and parkour | `24-pet-depth.js`, tree surfaces | **full and ahead of the page**: `body/ParkourPlanner.kt`, `Jump*.kt`, `LeapDirector.kt`, `Solids.kt`, fridge, bookcase, side table, demo button | native | done | n/a | none known | developer "Demo parkour" |
| Petting and touch: tap, hold, saturation, leave, belly trap, spam | `16-runtime.js:201-246`, `26-pet-brain.js:228-289` | **full** for tap, hold, saturation, ears, leaving, hearts, haptics (`body/TouchModel.kt`, `Petting.kt`); the page counts the pet via `touch`/`stroke`. Belly trap and the sleeper's depth are **not** done; follow-the-finger and parallax drag are not done | page counts | M | CB3 | the reflex and the page's judge can disagree on level | stroke at each trust level |
| Glass visit, vigil, comfort sit, head bump, respect | `24-pet-depth.js:236-242`, `26-pet-brain.js:478` | **absent** (CB3) | page | M | CB3 | quiet album | see CB3 |
| Zoomies | `26-pet-brain.js` option | **absent** (CB6): the contract lists `first_zoomies` and `post_meal_zoomer` only as names | page | S | CB6 | none | n/a |
| Cheers on real progress (Life Director sessions, level ups, timers) | `26-pet-brain.js`, deep link | absent: only the contract's cue shapes exist (`world/WorldContract.kt` `MAX_CHEER_CUES`) | page | M | CB6 | never a notification (owner rule) | finish a session |
| House: three rooms, furniture, camera | `17-runtime.js`, backdrops | full, plus 2.5D solid furniture (`room/`) | native | done | n/a | frame cost with two cats and toys is a phone check | scroll the room |
| Look-at (head and eyes follow the finger or a toy) | `50-pet-hd.js:74` eyes only | `body/Attention.kt` computes it but "reports intent"; no head part is drawn (CT8) | native | M (spike first) | art or rig head mask | decision needed | owner decision in section 4 |

### 2.5 Sounds, shop, achievements, notifications, shapes of saved data

| Capability | Page | Native | Writer | Size | Depends on | Risks | Phone check |
|---|---|---|---|---|---|---|---|
| Voice and sounds: 13 clips, per-cat pitch, chained purr, synthesised trill and chirp | `16-runtime.js:310-331` (`petPurr`, `petMeow`), `petSounds.html` (420,136 bytes, `src/data/petSounds.html`), `26-pet-brain.js:394-416` | **absent**, except that the page plays the purr while native owns the room (`touch` command `purr`) | page | M (CB7, `SoundPool`) | clip list and licence | the purr starting late without a page gesture is a phone check | stroke a cat, any delay? |
| Shop and items: toys only (`ECO_TOY_PRICE`), no food, furniture or collars | `44-rewards.js:6,85` | draws only plus `buyToy` | page | done | n/a | none | buy and use |
| Achievements | **none for the cat** (grep `achiev`, `badge` in pet segments and `44-rewards.js`: none). The nearest are the album's moments and the "cat care" XP activity (`44-rewards.js:59-64`) | absent | page | n/a | n/a | n/a | n/a |
| Notifications | **none by design**: "No notifications, ever" (`17-runtime.js:252`) | none, and should stay none (CB owner rule: cheers never notify) | n/a | n/a | n/a | n/a | n/a |
| Cat care XP (fed, played, petted in one day) | `44-rewards.js:59-64,146-149` | uses the page's hooks through commands | page | done | n/a | n/a | one day of all three |
| Offline catch-up | page `26-pet-brain.js:630` (web brain), page day plans `15-runtime.js:144` `petBuildDay` | native's own (`brain/CatchUpRun.kt`, `Schedule.kt`) | native | done | n/a | big gap behaviour is a design choice | reopen after a day |

### 2.6 Album, portraits, memories

| Capability | Page | Native | Writer | Size | Depends on | Risks | Phone check |
|---|---|---|---|---|---|---|---|
| Album of moments (cap 3 a week, 200 entries per cat, `entries[]`) and memorial copy of 60 entries | `50-pet-album.js` (362 lines), `50-pet-moments.js` (112) | **absent**: "Nothing is projected to native yet and no capability exists" (`PET_TAB_INVENTORY.md`, Photo album section; `50-pet-album.js:2-4`) | page | L (projection, scrapbook screen, staged portraits) | CB3 moments, art poses | the scrapbook phase is described in `docs/architecture/PET_SCRAPBOOK.md` (**not read**) | n/a |
| Still portrait for the Overview card | `50-pet-portrait.js` (85) | **full**: Overview `CatCard.kt`, `PetPortrait.kt`, native live room | page makes the picture; native draws the live cat | done | n/a | none | Overview card |
| Staged portraits for album entries (a photo of the pose) | `50-pet-album.js` `photo:'pending'` | absent | n/a | M | album projection, poses | needs the cat drawn in a named pose on request | n/a |

With the native brain owning behaviour, the page's brain events (`petBrainEvent`) stop firing, so any
moment the album detects from them (glass, zoomies, belly, vigil, trophy, blink, respect, quirk, gift,
favourite place and toy) would stop being recorded. The contract has the report slots
(`MomentReport.MOMENT_KINDS`) but no native code fills them. **Unverified** whether the page's detector still
finds the pair and bond rungs on its own (the plan says it does; I did not run it).

### 2.7 Saved-data shapes and which keys hold them

All live in the one save blob `lifemax_rpg_prototype_v1`, `SAVE_SCHEMA = 1`
(`PET_AS_BUILT.md:229`). The page writes every key. Counts are how often the page source mentions the key.

| Key | What it holds | Native touches it |
|---|---|---|
| `state.pet`, `state.pet2` | one cat: name, coat, trait, born, lastFed, meals[], day, fedToday, petsToday, playsToday, bondPts, care, vacation{on,since}, gifts, lastGiftDay, alive, passed, seen, greeted, `dna`, `brain` (old brain), `likes`, `album` (`15-runtime.js:65-70`, `29-pet-pair.js:402`) | reads through projections; writes only via commands |
| `state.petPair` | friendship record with rank `rk`, counts `n`, last note (`29-pair:62-67`) | reads; writes via `social` |
| `state.petMemorial[]` | passed cats `{name,coat,days,passed,bond,album}` (`15-runtime.js:108`) | none (page shows it) |
| `state.cattery` | `{v,cats[{id,name,dna,born}],goal,litter}` | none |
| `state.toysBought` | toys unlocked early | `buyToy` command |
| `state.careDay` | day's feed, play, pet flags for the XP | none |
| `state.petWorld` | native's blob: `{v, epoch, rev, at, imported, cats{...}, pair, toys[<=8], gift, outbox[<=16], away}` (`CAT_BRAIN_PLAN.md` 2.3), at most 8 KB | the **only** key native authors, through the `world` command; the page never reads inside it |
| `state.petSocialOps`, `state.petPlayOps` | last 40 operation ids so a repeat counts once | commands |
| `state.hdCat` | HD face layer flag (`PET_AS_BUILT.md:229`) | none |
| `state.petInviteHidden` | hides the "another visitor" invite | none |
| local storage `lifemax_pet_world_epoch_v1` | the world's epoch so a restored backup comes back exactly | page |

Genome shape (`32-pet-dna.js:90-95`): `{v, id (8 characters), sex 'XX'|'XY'|'XXY', g:{locus:[allele,allele]}, dev (fixed random share), mut[], par:{m,f}, gen}`. Native never decodes `g`; the page puts
the readable rows in the projection.

Page-only rules that a Kotlin cutover (CT-L) would have to copy exactly: the care maths
(`15-runtime.js:57-123`), the day schedule's seeded random numbers (`15-runtime.js:141-170`, FNV-1a and
mulberry32, `PET_AS_BUILT.md` section 8), and the genetics (`32` 712 lines, `37` 284 lines). The plan's
golden-test route is in `CAT_TAB_PLAN.md` CT-L.

### 2.8 What the screens show today (read from the docs and the source list)

- **Cat tab** (`presentation/PetScreen.kt`, `PetRoute.kt`): the live room, name, hearts, mood line, bond bar,
  growth line, last meal, meals today, days together, favourite place and toy, hint, Feed, Slow blink,
  Play (the native tray), Care and settings, then collapsible Likes, Inner life, Genetics, Toys and the
  Together card. The developer demo button appears only in developer mode. The page's intro (no cat) and
  memorial still show on the page.
- **Overview card** (`feature/overview/presentation/cards/CatCard.kt`): the same live world through
  `SharedStages`, with a still portrait fallback.
- **Character tab**: the search of `feature/character` found no pet code; the cat appears nowhere there
  (read from file listing only; I did not open the tab's sources).

---

## 3. Top gaps, in plain words

1. **The page's moment reporting from native is empty.** The native brain does not yet tell the page about
   first glass visits, zoomies, quirks, gifts or dwell time. The album, favourites and quirks may go quiet
   once someone uses the native cat for weeks (**unverified**, but the report slots are decoded and
   never filled).
2. **No native breeding, adoption, cattery or memorial.** All four are page-only, so a new player's
   first screen is the page's.
3. **No sound** in the native room (the page keeps playing the purr).
4. **No solo play, gifts or zoomies**, so the cats are calmer than the web cats.
5. **No album or scrapbook** on the phone.
6. **Box look** and **head bump** and **look-at** are art or engine decisions still open.
7. The tab is a beta, off by default, and the page's Cat room is the safety net until CT10 and CB8.
8. **No thirst or litter** exist on either side. This is not a gap against the page, but the task's
   "water, litter" list should be marked as a product choice (section 4).

---

## 4. Questions for the owner

| # | Question | Recommendation |
|---|---|---|
| Q1 | Should breeding be a lab (as now), or part of the cats' life with a wait and kittens that arrive? | Keep the lab as is and port it (WP-C3). Add the "expecting" layer (WP-C5) only after you have used the native lab for a few weeks. |
| Q2 | Who may breed: any sex-valid pair, or adults only and no close relatives? | Adults (30 days) and no parent-child or sibling pairs, one litter per mother per 14 days. It costs almost nothing since the genome records parents. |
| Q3 | Should the second-cat adoption and the first-cat intro stay a choice of four coats, or only a random litter? | Keep both. They are cheap to port and the coat picker is what the owner already knows. |
| Q4 | Names: random on keep (today) or named by the owner on keep? | Owner names on keep, with the random name as a placeholder. |
| Q5 | Should a cat have thirst and a litter tray? | No. The page has none and its rule is "no pressure" (`17-runtime.js:252`). If you want the water bowl to matter, make it a flavour line only (already one in the away story). |
| Q6 | Sound: port the 13 clips to native (WP-C9) or keep the page playing them? | Port, but only the purr and the meow first, if the purr is late on your phone. Otherwise leave it. |
| Q7 | Head and eye look-at: spend a spike (one agent pass) or drop it? | One spike with screenshots; go or no-go from your eyes (`CAT_TAB_PLAN.md` CT8). |
| Q8 | Is the cat tab ready to be the default (CT10 and CB8)? | Only after you accept WP-C1 (moments), WP-C6 (the intro and memorial) and have used it for a week. |
| Q9 | Album and scrapbook on the phone: now or later? | Later (WP-C8). It needs the moment reports first and a staged portrait for each entry. |
| Q10 | The "stray" in the lab should be a newborn or an adult? | Adult, so set `born` to 30 days ago, and keep the label. |

---

## 5. Work packages, in dependency order

Types: **E** pure engine work provable by golden cases or unit tests (no phone needed to decide it is right);
**D** needs an owner decision first; **A** needs art; **P** needs the phone to judge feel.

| WP | What | Size | Type | Needs | Depends on |
|---|---|---|---|---|---|
| **WP-C0** | Check the open **unverified** items in this file by reading and running: does the page still record glass, zoomies, blink, favourites and quirks while native owns a cat (run the page's own tests with `NativeCatWorld.owns` on); does the box show the cat sunk. Output: a short results table. | S | E | none | none |
| **WP-C1** | Native moment reports: make the brain send `moment`, `gift`, `dwell` for the kinds already in the contract (`MomentReport.MOMENT_KINDS`), page side `petWorldMoment` and the one album wrapper line (`CAT_BRAIN_PLAN.md` 2.5). Provable with the FakePage driver and `native-cat-world.test.js`: an op id makes it once, repeats answer "Already recorded". | M | E | none | WP-C0 |
| **WP-C2** | CB3 owner interaction: glass visit and fog, vigil, comfort sit, belly, respect, head bump, slow-blink answer, follow-the-finger. Uses WP-C1 for reports. | L | E for brain rules, A for head-bump row, P for feel | art lab row `head_bump` (exists in `PET_ART_LAB.md`, per the plan) | WP-C1 |
| **WP-C3** | Native Breeding lab, adoption and cattery screens, unchanged rules: roster, mother and father pickers, goal and forecast, litter with previews (via `PageCoatSource`), keep, release, rename, bring home, stray; the intro (first-cat litter of four) and the second-cat adoption; commands call the page's own `petBreedGo`, `petBreedKeep`, `petPairAdopt`, `petAdopt` (one command per action, repeat-safe, ok only once saved). The page's `confirm`/`prompt` become native dialogs with the same refusals. | L | E for the page node tests and decoders; P for the screens | none | none (can start now, parallel to WP-C1) |
| **WP-C4** | Native memorial and "Open the door again", then relax `blocked()` so the tab opens with no cat and after a passing. | M | E | none | WP-C3 |
| **WP-C5** | Breeding as part of the game (section 1.4): adult and relative rules, per-mother cooldown, a 3-day "expecting" wait with a nesting tilt on the mother's day, seeded litters, owner names on keep. Rules computed in the page (it writes saves) first; golden cases fix the litter for a given seed. | M | D (Q1, Q2, Q4), E | WP-C3 | owner decision |
| **WP-C6** | CB4 rest: solo play, gifts staged and tapped, dwell, sleeping with a toy, the box sink if WP-C0 shows it missing. | M | E, A for a box sink art check, P | none | WP-C1 |
| **WP-C7** | CB6 cheers and zoomies; the rest day modifier. | S to M | E | none | WP-C2 |
| **WP-C8** | Album and scrapbook on the phone: projection with a capability, strict decoder, a screen, staged portraits (the cat drawn in a named pose on request). `PET_SCRAPBOOK.md` is the spec (**not read here**). | L | E for data, A for any new pose, P | WP-C1, WP-C2 | owner decision Q9 |
| **WP-C9** | Native sound (CB7): `SoundPool` port of the 13 clips, per-cat pitch, chained purr, cues from the runner. | M | E for the cue list, P for the timing | licence and asset check for `petSounds.html` | none (can run any time after WP-C2) |
| **WP-C10** | Look-at spike: one agent pass, screenshots, owner go or no-go; then build (CT8b). | M | D (Q7), A (head mask) | none | none |
| **WP-C11** | Default on and retire (CT10, CB8): make the tab the default after the owner has lived on it, keep the page room as "Classic" for one release, then delete the dead page code listed in `CAT_BRAIN_PLAN.md` section 3. | M | D (Q8), P | WP-C1, C2, C3, C4, C6, C7 and the phone checks | all of the above |
| **WP-C12** | Logic cutover (CT-L): care maths, schedule random numbers, DNA, likes and pair rules to Kotlin with golden cases from the page (200 synthetic cats x 14 days; the 2,000 crosses of `petDevDnaStats`; page-write then Kotlin-read of the same save). | L, several passes | E throughout; larger-model review (see below) | none | WP-C11 |

Grouping for the owner:
- **Pure engine work provable with tests**: WP-C0, C1, C3 (rules and decoders), C4, C5 (litter given a seed), C6 (rules), C7, C12.
- **Needs an owner decision first**: WP-C5 (Q1, Q2, Q4), WP-C8 (Q9), WP-C10 (Q7), WP-C11 (Q8), and Q3, Q5, Q6, Q10 shape WP-C3, WP-C9.
- **Needs art**: WP-C2 (head bump row), WP-C6 (a box sink check), WP-C8 (new staged poses), WP-C10 (a head part). `PET_ART_LAB.md` also lists four hand-touched pieces still wanted (toss flick, wide yawn, body shake, hiss arch).
- **Judged only on the phone**: WP-C2 touch feel, WP-C6 toss and carry feel, WP-C9 purr timing, WP-C11.

Per `CLAUDE.md`, switching to the larger model (`/model`) is worth it for WP-C2 (design with no plan), WP-C11
(moves saving), WP-C12 (a logic cutover), and for reviewing any outside work before merging.

---

## 6. Not verified in this audit

- I did not run the app, the page, any node test or any Kotlin test.
- I did not read `docs/architecture/PET_SCRAPBOOK.md`, `PET_SPRITE_STEADINESS.md` in full, the Kotlin tests,
  or the bodies of `brain/*Activities.kt` beyond their ids; claims about them come from file names, greps and
  the inventory text.
- "Absent" for native means a grep of `feature/pet`, `app/` and `bridge/web/NativePetPlugin.kt` found no
  match, not a proof; a feature could live under another name.
- The Character tab statement rests on a grep of `feature/character` only.
- Whether the page's detectors keep working while native owns the cat is inferred from
  `CAT_BRAIN_PLAN.md` 2.5 and the empty native senders, not from a run.

---

## 7. Page oddities met while porting the cattery (CT6b)

Recorded, not fixed: CT6b ports the lab, the cattery and the adoption flows with the page's rules unchanged. Each of these
is what the page does now; the native screens show the same.

- **CD01. "Another stray" is called an adult but is stored as a newborn.** The button's text says "A random unrelated adult
  joins the cattery" (`petBreedHtml`, `37-pet-morph.js:163`), but `petBreedAdd` stores `born: Date.now()` (`:212`). The cattery
  card then reads "0 d old" and a stray brought home starts as a kitten (`petBreedHome` copies `born`). The native screen shows the
  page's words and the page's age.
- **CD02. The adoption litter outlives the adoption.** `window.__petLitter` (`32-pet-dna.js:482,668`) is not cleared when a
  kitten is adopted (`petAdoptWith`, `petPairAdoptWith`), so the next intro (after a passing and "Open the door again") offers
  the same four kittens, one of which is the cat that died or is at home. The pair adoption resets it when opened
  (`petPairAdoptOpen`); the first-cat intro does not. The native Adoption screen resets it on `view` as the pair card does.
- **CD03. The lab's "no mate" note pointed at a button the lab does not have.** The page's lab said "Try Another litter below to find a
  mate" (`37-pet-morph.js:151`) but "Another litter" is the intro's button. Fixed for the native screen (section 8.4): its
  note now says "Use Another stray below to find a mate" and the lab screen has an Another stray button when there is no mother or no
  fertile father to pair. The page's own lab is unchanged.
- **CD04. Breeding again throws away an unkept litter without asking.** `petBreedGo` replaces `cattery.litter` (`:207`), so the
  kittens not yet kept or released are lost on the next Breed. Nothing confirms it. The native screen sends the litter's `at` stamp
  with Keep and Release so a stale tap is refused, but Breed itself behaves as the page does.
- **CD05. A rename longer than 14 characters is cut silently.** `petBreedRename` trims and slices to 14 (`:230`) with no word
  to the owner; the native field stops typing at 14, and the `cattery` command refuses a longer name rather than cutting it.
- **CD06. Any sex-valid pair may breed, including kittens a day old, parent and child, and full siblings.** Known (section 1.1) and
  listed here because the native Breed button shows the same pairs; WP-C5 decides.
- **CD07. The first-cat intro and the memorial could not open natively.** `blocked()` in `50-native-pet.js` kept the page's view for a
  missing or dead cat. Fixed (WP-C4, Native API 157): a screen that says `pet.empty.v1` (and has `pet.cattery.v1`) now gets the tab with
  no cat at home, on the cattery's Adoption (first cat) or Memorial screen; an older screen is blocked as before.

---

## 8. What was done about this audit (work packages C0, C1, C4 and the breeding check)

Status: built and tested locally (Native API 157); not run on a phone. Page tests, the local Kotlin run and what is unverified are listed
at the end of each part.

### 8.1 WP-C0: the open "unverified" items, checked by reading the callers and running the page's own tests

| Item (section) | Result | How |
|---|---|---|
| Does the page still record glass, zoomies, blink, favourites and quirks while native owns the cat? (2.2, 2.6) | **No, except what the commands reach.** The room loop is what calls `petBrainFrame`, `petLikesTick` (`24-pet-depth.js:217`) and `petBrainSession`; `petWantRun` is false while native owns the view (`native-cat-world.test.js`: "the web peek loop stays off", passes). So: no first glass, comfort, zoomies, belly, vigil or trophy; no daily likes credit, so no new favourite place or toy; the night-owl quirk count stops. The feed, play and stroke commands call the page's own functions, which still count the meals, toy, gentle-stroke quirks (`k`, `t`, `g`) and the album's gift, blink and respect events. | grep of callers; page tests |
| Does the album's pair and bond detection still work? (2.6) | Yes: pair nap, level and rank are read from `state.petPair`, which the `social` command writes; bond and growth are read from the cat. | `native-pet-social.test.js` (36 pass) |
| Does the box show the cat sunk? (2.4) | **No.** No sink code in native `body/` or `brain/` (grep `sink`: only the touch sink). `SurfaceId.BOX` exists with `BOX_FRONT_Z` for occlusion only. Still a WP-C6 art check. | grep |
| Page tests with the native brain on | All pass at this base: `native-cat-world` 63, `native-pet-cattery` 46, `native-pet-social` 36, `native-pet-play` 28, `native-pet` 51, `pet-album` 48, `native-pet-panels` 137, `native-pet-touch` 25, `native-pet-demo` 9. | run |

Not done: a run on the phone.

### 8.2 WP-C1: moment, gift and dwell reports

Page side (new segment `50-native-pet-moments.js`, tests `native-pet-moments.test.js`, 68 checks): the three commands the plan had named
and nobody had built.
- `moment {op, cat, kind, at, quirk?}`: first_glass, first_co, first_zoomies, first_belly, first_vigil, first_trophy, blink, respect, quirk. It
  sets what the web brain would have (`mem.fx` through `petMemCore`, `qk.acq`) and lets the album look again, so the album's own rules (three a
  week, no repeats) apply. Repeat-safe by op (`state.petMomentOps`, last 40); ok only once stored, a refused save is taken back and silent.
- `gift {op, cat}`: refused unless the cat is Devoted and has not given one today; counts the gift as the page's own tap does.
- `dwell {op, cat, day, spots, toys}`: credits the likes as the page's daily credit does: 0.7 points an hour in a place (the page gives 1 for
  a nap, 0.8 a rest, 0.5 otherwise), 0.5 a minute with a toy, old habits fade 5 % when a new day is credited; a day older than a week or in the
  future is refused. These constants are the page's `SPOT_PER_HOUR` and `TOY_PER_MIN`: dials, not measured on a phone.

Native side (Native API 157; `brain/Dwell.kt`, `CatMind`, `CatWorld`, `MindState`, `CatWorldHost`; tests `brain/DwellMomentsTest`):
- **Dwell is produced.** Each cat counts the seconds it spends at a page place and with a page toy (solo play), per local day, and reports it
  when ten minutes have gathered, when the day ends, when the view closes or the app stops, and for the catch-up's story. Counting allocates
  nothing.
- **First zoomies is produced**, once per cat for good, only while the owner has a view open; it is kept in the saved cat (`fx`) so a restart
  does not report it again.
- Blink and respect: the owner's slow-blink button and strokes already reach the page through their own commands (`blink`, `touch`), which
  call the page's own brain events, so the page records them; native does not report them a second time.

Still not produced natively (they need the CB3 owner interaction work, WP-C2): first glass, comfort, belly, vigil, trophy, the gift staged and
tapped (the page command is ready; nothing stages a gift or draws one to tap), and the night-owl quirk count (the other three quirk counters
are counted page-side by the feed, play and stroke commands).

### 8.3 WP-C4: the intro and the memorial on the native Cat tab

`pet.empty.v1` (Native API 157). For a screen that says it and `pet.cattery.v1`, the tab no longer steps aside for no cat or a dead cat: the
page sends a projection with no cat in it (`empty: {screen: "adopt" | "memorial"}` and the cattery block), and the screen shows the cattery's
Adoption (the first cat's litter of four, name, "Welcome home") or Memorial ("Open the door again") with no room behind it. Adopting a first
cat makes the ordinary Cat card; reopening the door turns the memorial into the adoption; a cat that passes while the tab is open turns it
into the memorial instead of closing it. An older screen is blocked as before. Page tests `native-pet-empty.test.js` (16 checks), Kotlin
`PetEmptyTest` (decoder, 8 malformed variants refused) and `PetEmptyViewModelTest` (the screen is asked for once, never in a loop). One page
bug found on the way and fixed: the page's own memorial threw when the dead cat's coat was a genetic one whose sheet was not built
(`17-runtime.js`).

### 8.4 Breeding, end to end

`native-pet-breeding-path.test.js` drives the native cattery's commands the way a player does: one male at home, empty cattery, the lab says
there is no mate, Another stray until a mother and a father exist (2 strays at the test's seed), Breed, Keep, Bring home. The kitten lives at
home as the second cat, with both parents recorded and generation 1, and the native screen can draw it; a second kitten cannot come home while
the slot is taken. So the system is reachable on the native tab (when "Native Cat (beta)" is on) from the Cat tab's entry card
(Cattery, Breeding lab). **One dead end was found and fixed:** with no mother or no fertile father the lab told the player to use a button
that is not on it (CD03); the native lab now has Another stray. Things that still make it feel unfinished, all owner decisions (section 9):
the stray arrives as a newborn (CD01), any pair may breed (CD06), the beta is off by default.

### 8.5 Not verified

Nothing here was run on a phone or in the full Gradle build (screenshots, Paparazzi, `RoomAllocationTest`). Kotlin was compiled and tested with
`kotlinc` and JUnit on the JVM for the headless packages (body, brain, world, host, art, room), and for the pet `domain`, `data` and
`PetViewModel` with stubs for two core types (`HapticScale`, `haptics`). The two new Compose changes (`PetRoute.EmptyHome`, the header without
a Back arrow, the lab's stray button) are type-checked only by reading: `presentation` Compose files were not compiled here.
`LiveBrainEndToEndTest.sixHoursOfTheRealBrainWithTheOwnerAKillAndAnOfflineGap` fails the same way at the base commit (a cat found on the
couch where a floor was expected, line 198) and is left alone.

## 9. Gaps that still need an owner decision, with the recommended default

| # | Gap | Recommended default |
|---|---|---|
| Q1, Q2, Q4 | Breeding as part of the cats' life (WP-C5): waiting time, who may breed, naming | Keep the lab as is for now; later add adults only (30 days), no parent-child or siblings, one litter per mother per 14 days, owner names on keep |
| Q10 / CD01 | "Another stray" is a newborn but the button says adult | Make it an adult (`born` 30 days ago); it is the only way to find a mate without a second cat |
| Q8 | Native Cat tab on by default (WP-C11) | Not yet: first accept the moments, adoption and memorial on the phone for a week |
| Q9 | Album and scrapbook on the phone (WP-C8) | Later, after WP-C2 produces the rest of the moments |
| Q6 | Native sound (WP-C9) | Port the purr and the meow only if the purr is late on the phone |
| Q7 | Head and eye look-at (WP-C10) | One spike with screenshots |
| Q5 | Thirst and a litter tray | No |
| new | Favourites' calibration under native: 0.7 points an hour in a place, 0.5 a minute with a toy | Keep; look at "Favorite place" after a week on the phone and adjust `SPOT_PER_HOUR` and `TOY_PER_MIN` |
| new | A gift from a native cat (staged, drawn, tappable) | Build with WP-C2/C6 once the art for a gift exists; the page command is ready |
