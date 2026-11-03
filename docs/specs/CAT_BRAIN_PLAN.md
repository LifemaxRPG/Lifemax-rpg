# Native cat brain: plan

Status: CB2 (world service and persistence) built, Native API 60: `CatWorldHost`, one world for the Overview card and the Cat tab, the `world` command end to end (outbox, epoch/revision, trimming, retries), import, catch-up seam (`Brain.catchUp`), owner guard, panel source swap; runs on the shipped autopilot until the native brain (CB1) is merged. CB0 (contract and harness) built, Native API 58: `pet.world.v1`, the `world` block, blob and command, the strict decoders, FakePage, WorldClock, the headless check and the day viewer skeleton; nothing behaves differently yet. Owner decision (3 Nov 2026): **"Native brain from scratch."** This plan replaces `CAT_TAB_PLAN.md` CT3 (the
live `mind` stream that lets the page's brain steer the native body), the page halves of CT4 and CT5 (game plan builders, pair director
through the stream) and the behaviour part of CT-L. The page keeps the cat's **identity and care economics**; **behaviour** (what a
cat does minute by minute) moves to a new, smaller brain in pure Kotlin, built for the new body. Citations are `segment:line` under
`lifemax_modular_v73/src/runtime/segments/` and Kotlin paths under `android-wrapper/native/src/main/java/com/lifemax/rpg/feature/pet/`.
Phases are named CB0..CB8 so they do not clash with CT or P numbers.

Builds on (must be merged first): CT1 tab (`presentation/PetScreen.kt`, `RoomHost.kt`), CT2 touch (`body/TouchModel.kt`, commit
`5ef6fdb`), parkour (`body/ParkourPlanner.kt`, `JumpCapability.kt`, `LeapDirector.kt`, `Solids.kt`, commit `f97ae67`, adds the fridge,
bookcase and side table surfaces), CT7 rig springs (commit `a642c85`), the art lab rows (`art/LabTable.kt`, `PET_ART_LAB.md`) and the
scrapbook page side (`50-pet-moments.js`, `50-pet-album.js`, `PET_SCRAPBOOK.md`).

## 0. Decisions in one screen

- **Two writers, two kinds of data, never the same field.** The page stays the only writer of the save. Native owns behaviour state
  but never writes it itself: it hands the page an opaque, versioned, size-limited blob (`state.petWorld`) through one strict
  command, exactly like every other command (ok only when `persist()` succeeded, repeat-safe by `op`).
- **One-directional where possible.** Page to native: a state block `world` (identity, care caps, context, cheer cues, the stored
  blob) in the existing tab projection. Native to page: care commands (as today), moment, social and dwell reports, and the blob.
  No plan stream, no `needPlan`, no mirror, no predictor, no latency budget.
- **One cat, one instance.** A process-wide `CatWorld` holds both cats, the toys and the room state. The Cat tab, the full-screen room
  and the Overview card draw the *same* world; a widget or wallpaper can later restore and fast-forward it from the blob.
- **Small and explainable brain:** drives + personality-weighted utility over a catalogue of activities, a short-horizon planner over
  the parkour graph, a circadian scheduler, reflexes for touch and cues. Seeded per cat, stepped on fixed sub-steps of simulated time,
  so a day replays bit-for-bit and 60 Hz equals 120 Hz.
- **The album keeps working unchanged in spirit:** native reports the same "first_*" core memories, blink, respect, gifts, quirks,
  pair events and spot/toy dwell; the page writes `p.brain.mem.fx`, `p.brain.qk.acq`, `p.gifts`, `state.petPair` and `p.likes`
  as before, so `PET_MOMENTS.reach` and the detector need one new wrapper line only.
- **Rollout** rides the existing Settings switch "Native Cat (beta)" (the only switch). Off = the web room and web brain exactly as
  today, until CT10. Deletion of the web behaviour code waits one release after the native brain is the default.

## 1. The brain model

### 1.1 Shape (package `feature/pet/brain/`, pure Kotlin, no Android or Compose types)

```
CatWorld ── clock (WorldClock) ── house (World, Solids, ParkourPlanner) ── SpotBook (reservations)
   │── props: Bowl, ToyBody[≤8], Gift?  ── SocialDirector (pair proposals, sync points)
   └── CatAgent[1..2] = CatMind (Drives, Mood, Personality, Memory, Schedule, Reflexes,
                                 ActivitySelector, ActivityRunner) + CatBody (shipped body)
   outputs: poses (read by renderers), WorldOutbox (moments, social, dwell, blob saves), sound cues
```

- `CatBody` stays the body (fixed 1/120 s sub-steps, `body/CatBody.kt:8-10`), plans of `PlanStep` (`body/Plan.kt`), affect via
  `setAffect`, `lookAt`, `react`. The brain never draws and never reads Compose state.
- `ActivityRunner` turns one chosen activity into a short script of `PlanStep`s plus brain-side beats (wait for `Arrived`, pick a toy
  up on the `bite` frame, sync with the other cat). It is the only thing that calls `replacePlan`/`extendPlan`.
- `ActivitySelector` scores eligible activities and picks one (1.5). `Reflexes` pre-empt the runner (touch, startle, feed cue,
  cheer cue, the other cat's invitation).

### 1.2 Time, determinism, headlessness

- **One simulated clock.** `CatWorld.advanceTo(monoNanos)` is idempotent per timestamp (two views on screen cannot double-step). It
  runs whole 1/120 s sub-steps; every 30th sub-step (0.25 s) is a **think quantum** where drives integrate and the selector may run.
  Drive changes use closed-form exponentials per quantum, never `dt`-proportional Euler on frame time. Hence any host frame rate
  gives the same state at every sub-step boundary.
- **Wall time** enters only through `WorldClock`: `epochMs` = start epoch + simulated elapsed (re-anchored to the real clock only on
  resume, which runs catch-up, 2.4) and `zoneOffsetMin`. The page's dev time shift (`petNow`, `15:138`) arrives as `clockOffsetMs`.
- **Randomness**: one `Rng` (`body/Rng.kt`) per cat per stream (`choice`, `idle`, `social`), seeded `Rng.hash("<catId>|<dayKey>|<stream>")`
  at local midnight and saved mid-day in the blob (`rng` ints), so a restored world continues the same sequence. `catId` is the album's
  `c<born base36>` (survives renames; `Autopilot.seedFor(name, coat)` changed seed on rename and is retired). World-level draws
  (social, toys) use the `social` stream of the lower cat id.
- **Headless proof**: the `brain` package depends only on `body` and `kotlin.*`; a Gradle source-set check (CB0) fails if it imports
  `android.*` or `androidx.*`. The day viewer and every golden test run it on the JVM with a fake clock.

### 1.3 State variables (per cat; all floats 0..1 unless noted; saved with 3 decimals)

| var | meaning | rises | falls | input from page? |
|---|---|---|---|---|
| `hunger` | read-only view of the page's stage 0..5 and `nextStageAt` | page | page (meal) | yes, never computed natively |
| `energy` | inverse sleep pressure | sleeping (×2 in a soft spot) | awake, by activity cost | no |
| `play` | boredom | awake time, kittens ×1.6 | play, hunting, zoomies | no |
| `curiosity` | wish to explore/see | awake, a new toy, a cue | explore, window, parkour visit | no |
| `social` | wish for the owner | owner present (a view visible) and no contact | touch, head-bump, comfort sit | no |
| `company` | wish for the other cat | awake, scaled by pair `rel` | pair activities | `rel` from page |
| `comfort` | wish for a soft/warm/sunny spot | tiredness, cold hours, after stress | resting in a liked spot | no |
| `territory` | patrol, perch high, scratch-mark | time, the other cat on "my" perch | perch visit, scratch | no |
| `groom` | wish to wash | after eating, play, sleep, rain of touch | groom | no |
| `trust` {fam,tru,sec,aut} | relationship with the owner (imported from `p.brain.at`) | respectful touch, care, slow blinks | overstimulation, startle | no (bond points stay page) |
| `mood` {v,a} in -1..1 | valence and arousal | events | decay to a baseline per hour, hunger, personality | no |
| `sat` | petting saturation (CT2 `TouchModel` owns the live value) | touch | time | no |

### 1.4 Personality: DNA scores to weights

Input `ps` = the page's `petPheno(p).ps` {sk, ou, dm, sp, fr, vo} (`32`, 0..1), plus `lazy = 1-(ou+sp)/2` (the page's own trait rule,
`32:~205`). Each weight is `lerp(lo, hi, score)`; numbers live in one `BrainTuning.Persona` object.

| score | drives / weights it sets |
|---|---|
| sk skittish | startle threshold 0.6..0.25, approach-to-glass threshold, hidden-spot liking ×1..1.8, trust gain ×1.2..0.6, saturation ×0.8..1.3 |
| ou outgoing | curiosity gain ×0.7..1.5, explore and window weight, `social` gain, greets the other cat first |
| dm dominant | territory gain ×0.6..1.6, high-perch liking, holds a perch when challenged, rank power (the page's `petRankPower`) |
| sp spontaneous | selector temperature 0.08..0.30, zoomies chance, activity switch rate (shorter minimum durations), play gain |
| fr friendly | `social` and `company` gain, head-bump and curl-up gates lowered, mutual grooming weight |
| vo vocal | meow/chirp/trill cue rate (sound), begging intensity, greeting chirp |
| lazy (derived) | energy drain ×0.8..1.25 inverse, sleep share target, loaf weight |

**Kittens** (`stage` 0..5 from the page): play ×1.6, energy drains ×1.3 but naps are short (20..45 min), jump limits from
`JumpCapability.of(growth, …)` (already), no gifts, follow the adult in a pair ("shadowing" weight). **Weak** (hunger stage ≥ 3):
`CatBody.weak` rows, energy capped at 0.5, no parkour above the floor props, no play, stays near the bowl; stage ≥ 4 only rests by the
bowl (keeps the web rule `15:243-244`, which is part of the care contract: "a hungry cat tells you with behaviour"). Stage 2 begs at the
bowl when the owner is present. **Vacation**: hunger frozen by the page; the bowl shows food (a sitter), no begging, normal life.

### 1.5 Selection: utility with a short-horizon plan

- Every think quantum the runner reports whether it is at an **interruptible point** (between script beats, or an activity's minimum
  time elapsed). Only then, or on a reflex, does the selector run; else it does nothing (cheap: most quanta are a few comparisons).
- `score(a) = base(a) × Π curve_i(drive_i) × persona(a) × schedule(a, hour, light) × mood(a) × memory(a) × novelty(a) × noise`, where
  curves are named response curves (`rise`, `fall`, `bell`) on 0..1; `memory` favours liked spots/toys (the page's `likes` scores and the
  world's own spot memory); `novelty` damps the last 3 activities (0.5, 0.7, 0.85); `noise` is ±6 % from the `choice` stream.
- **Travel cost:** for activities with a place, the planner (`ParkourPlanner`, Dijkstra over surfaces with effort cost) gives a route
  and its cost; `score /= 1 + travelSeconds/30 + effort²`. No route = not eligible. "Fridge only via the counter" is geometry, not a rule.
- **Commitment:** the running activity gets ×1.3 until its minimum time, so cats do not dither. Pick = softmax over the top 4 with the
  persona temperature. Ties broken by activity order (stable).
- **SpotBook:** an activity's place is reserved at pick time (single-occupancy surfaces: bed, the three sills, tree mid/top, box,
  pouf, cushion, fridge, bookcase, side table; floor slots with `HousePlans.GAP`) and released on leave. A reserved place is not
  eligible for the other cat, except shared places of a pair activity (curl up, groom).
- **Explain:** each pick records `{t, activity, top3 with scores, reason}` in a 32-entry ring (`CatMind.why`), shown by the day viewer
  and a dev overlay. This is the "explainable" requirement: every move can be traced to numbers.

### 1.6 Scheduler (time of day, room light)

Per cat per local day, from the day seed: wake 6.0..7.4 h (+0.6 × lazy), bed 22.0..23.5 h, two crepuscular activity peaks (dawn
±45 min of wake, dusk 17.5..20 h), 2..4 nap windows weighted to early afternoon. The schedule does not choose activities: it sets the
energy target curve (sleep pressure), the `schedule(a, hour, light)` factor (sleep ×3 inside bed hours, zoomies ×2.5 at dusk, sunny
spots ×1.8 10..16 h) and darkness from `room/RoomLight.kt`'s time-of-day (lamps on → cats settle). A `restDay` context lowers the
energy target and doubles nap windows (1.9).

### 1.7 Mood and how it shows (body springs)

Valence/arousal decay to a baseline (persona, hunger, time of day, trust) with half-lives v 4 min, a 2 min. Mapping, continuous where the
body allows: ears `EarState` (relaxed v>0.2 a<0.2, forward a>0.2, radar on a noise/cue, airplane v<-0.3 a>0.2) plus a new continuous
`tone(v, a)` on `CatBody` (ear spring bias, tail base height, walk speed ×0.85..1.15, blink rate, idle-life breath depth); tail
`TailState` (high_flag v>0.4 a>0.2, question_mark greeting, low_swish annoyed, tucked_curl anxious, tip_twitch focused/hunting).
Pupils: the 1x art has no dilation (the page disabled it, `26:356`), so pupils are an art gap (1.9), not faked.

### 1.8 Activities catalogue

Choreography uses shipped clips (`body/Clips.kt`, the 28 page rows, the 4 depth rows, the 12 lab rows 28..39 and the three pair poses).
"min..max" are minutes unless marked s. Interruptions: every activity yields to touch reflex, feed cue, startle, cheer cue (unless
deep sleep), and the other cat's accepted proposal.

| activity | preconditions | place / goal | choreography (rows) | duration | aftermath |
|---|---|---|---|---|---|
| sleep | energy < 0.35 or bed hours | liked sleep spot (bed, tree top, sofa, box, cushion, sills; `PET_SPOTS` sleep weights × likes) | walk/route, `knead` 4..10 s if trust ≥ 0.5, turn, `loaf` → `sleep`; depth light→deep after 6 min | 20..180 (night: until wake) | energy↑, waking routine |
| waking routine | leaving sleep | in place | ear flick (spring), `yawn`, `stretch`, `shake` (head), `groom` 2..5 s at 50 % | 6..12 s | groom↓ |
| loaf / rest | comfort or energy mid | liked rest spot | `loaf`, `idle_sit`, slow `slowblink` beats | 3..25 | comfort↓ |
| groom | groom > 0.5 | in place | `groom` (loop) | 0.5..3 | groom↓ |
| wander | curiosity mid | another room's floor | `walk`, `turn`, `sniff` (depth) at objects | 1..4 | curiosity↓ |
| window watch | curiosity, daylight | a sill (route; `lsill` via sofa, `ksill` via counter) | route, `idle_sit`/`loaf`, `lookup`, tail tip_twitch; chatter = `meow` + tip twitch | 3..20 | curiosity↓↓ |
| parkour visit | territory or curiosity, energy > 0.5, not weak | fridge, bookcase, tree top, side table | route through `LeapDirector` (`jump_up`/`jump_air`/`jump_land`), survey `idle_sit` + `lookup`, `loaf` | 2..15 | territory↓↓ |
| eat | page `fed` cue, bowl full | bowl (`PET_EATX` 87 / 121) | route, face, `eat` 7.5 s, `groom` 4.5 s (as the web, `16:247-262`) | 12 s | groom↑ |
| beg | hunger stage 2 (3+: weak) and owner present | bowl | `idle_sit` facing glass, `meow`, `react(BEG)` bobs | 0.5..3 | none (only food helps) |
| solo toy play | play > 0.5, a toy on the floor, not weak | toy | stalk (`crouch`, tip twitch), `bat`/`swat`/`kick`, pounce (`jump_up` short), chase (`run`) | 1..4 | play↓↓ |
| carry toy | after play, liking for a spot | toy → favourite spot or the glass | `pickup` (bite frame 4) → `carry` (pendulum toy on the mouth anchor) → `toss` (release frame 1) or drop | 10..40 s | trophy/gift path |
| owner play | owner offers a toy (Play/tray) | toy | as solo play, rounds 2..5, the wand dangles from the finger | 0.5..3 | `play` command (counts) |
| box | box liked, curiosity/comfort | box (route) | hop in + sink + wobble (CT5a numbers `PET_BOX`, `28`), `loaf`/`sleep` inside | 2..60 | comfort↓ |
| scratch | territory > 0.5 | tree base, sofa end | `scratch_in` → `scratch` → `scratch_out` | 6..15 s | territory↓ |
| zoomies | dusk or post-meal quirk, energy > 0.8, play > 0.6 | house floor + a leap | `run` laps with `turn`, a hop to sofa/bed and off, skid (rotation kick, shipped) | 15..40 s | play↓↓, energy↓ |
| knead | before sleep on a soft spot, or under a long stroke at trust ≥ 0.6 | in place | `knead` | 4..12 s | comfort↓ |
| glass visit | social > 0.6, trust tier allows z (55/84/100 from the page), awake | front zone (z ≥ 90) | walk front (`walk_front`), `sniff`, `bonk` + lens fog | 4..10 s | social↓, `first_glass` |
| comfort sit | social, owner present ≥ 45 s and still | front, z 70..90 | `loaf` facing glass, slow blinks | 1..6 | `first_co` |
| head-bump | finger resting on the room near the cat (CT2 hit), trust ≥ 0.5 | toward the finger | turn, walk to finger, `bump` (contact frame 2), `purr` | 2..5 s | social↓↓ |
| slow blink | owner taps Slow blink, or offered (trust ≥ 0.6, rare) | in place | `slowblink` (and returned) | 2..4 s | `blink` moment when returned |
| belly | very content (v > 0.6, a < 0), trust ≥ 0.8, near the front | in place | `roll` to its belly-up frame, held (as the web's lock) | 4..20 s | `first_belly`; belly trap on touch (CT2) |
| vigil | the owner's usual hour (rhythm bin ≥ 0.55), familiarity ≥ 0.35 | glass, before the view opens | already sitting at the front when the view opens | until greeted | `first_vigil` |
| hunt | idle, play mid, a floor item | item | `crouch`, wiggle, pounce, `carry` to the glass at trust ≥ 0.65 | 0.5..2 | `first_trophy` |
| gift | bond level 5 and `giftOk` (page) | a toy left at the glass | carry → `toss` gently at z 95 | once a day | owner taps → `gift` command |
| cheer reactions | cue (1.9) | front or a perch | see 1.9 | 3..20 s | mood↑ |
| pair: greet | both awake, met after ≥ 30 min apart | meet point | `nose_touch` pair pose (offset table), maybe hiss (low rel) | 3..6 s | `social greet/hiss` |
| pair: chase | rel ≥ 0.15 or a kitten | floor | stalk, pounce, `run`, tussle (`roll` + `bat`), swap roles | 20..60 s | `social chase` |
| pair: groom | rel ≥ 0.35, both resting | together | `groom_other` + `purr` mirrored (pair pose) | 20..60 s | `social groom` |
| pair: curl up | rel ≥ 0.6, both sleepy | one soft spot (bed, sofa, cushion) | spoon `snuggle` pose (sleep half a cycle apart) | 20..120 | `social nap` |
| pair: standoff | contested perch, rank unsettled | the perch | face off, ears airplane, `crouch`, `meow`; loser turns away | 4..10 s | `social standoff {winner}` |
| innate quirks | from `qk.inn` (imported) | — | flop (`roll`), greeble stare (`idle_sit` + radar ears), shadow pounce, paw tap at the glass (`bat` side view), chirp greeting (sound), post-meal zoomies, toy nudge (`kick` soft) | — | — |

Touch reflexes stay exactly CT2's `TouchModel` (levels, beats, warn/leave, belly trap). The brain receives its events (down, beat, up,
warn ignored, respect after 8 s) and turns them into trust/mood/social changes and moments.

### 1.9 Cheers you on (reacts to the owner's real progress, never changes a rule or XP)

| cue (page → native) | page source | reaction (awake) | asleep |
|---|---|---|---|
| `session_done` | Life Director writes that pay XP (`50-native-director.js` `today` → `todayRun('done'…)`, celebrations held) | trot to the front, `meow`/chirp, tail high_flag, `jump_up` on the spot, hearts | light sleep: wakes, routine, then cheers; deep: ear flick + tail thump |
| `level_up` | `checkLevelUps` (`05-runtime.js:8`) / `showLevelUp` (`07:95`) | a zoomie lap ending on the tree top or a sill, sparkle | wakes (always; it is rare) |
| `timer` (state: on/off) | a running Life Director timer (`todayRun('timer')`) or a guided workout (`gwr*`, `21-runtime.js`) | "keeps you company": a perch near the front facing out, loaf, slow blinks every 2..4 min; stretch when it ends | stays asleep near the front |
| `restDay` (state) | `state.restDays[getToday()]` (`43-economy.js:64-71`) | the day modifier of 1.6; more curl-ups and sunny naps, sleeps with the favourite toy | — |

Cue ids come like the shipped `fed` cue (`50-native-pet.js:85`): `{id, kind, ago}`, at most 4, played once. Native keeps the last 16
seen ids in the blob and ignores cues older than 10 min, so a reopen never replays one. Two cats: each reacts, staggered 0.4..1.2 s.

### 1.10 Art gaps (for the art lab; not invented here)

Already listed by `PET_ART_LAB.md` 7: a hand-touched `toss` flick frame, a wide-yawn face patch, a two-frame fluffed body for a real
shake. New from this catalogue: (4) a loaf-to-stand transition frame (the waking routine from a loaf cuts to `idle_sit` until then);
(5) a defensive arch/hiss pose for the standoff and the low-rel greet (until then: `crouch` + airplane ears + `meow`); (6) pupil
dilation frames or a face-index rule for excited/hunting; (7) a front-facing sit for the glass and comfort sit (until then: side
`loaf` at high z); (8) a window-chatter jaw (a lab face op, not new art). Everything else in 1.8 reuses existing rows.

## 2. The page/native contract (replaces the stream)

Capability `pet.world.v1`, advertised by `NativePetPlugin` **and** `NativeOverviewCardsPlugin`; the page sends the `world` block only
when the plugin has it. All decoders strict (unknown keys rejected, last good frame kept, `error{code}` counted, no private data logged).

### 2.1 Page to native: `world` block (in the Cat tab projection and the Overview cards projection, built by one page function `petWorldBlock()`)

```
world = { v:1, seq, epoch, clockOffsetMs,
  cats: [{ id:"c<born36>", slot:0|1, name, born, sex, coat, stage:-1..5, grownAt,
           ps:{sk,ou,dm,sp,fr,vo}, morph:{lg,bl}, bond:{lvl:0..5, pct}, zMax:35|55|84|100,
           hunger:{stage:0..5, lastFed, vacation, nextStageAt|null},
           care:{fedToday, petsToday, playsToday, feedAt, canPlay, giftOk},
           likes:{spots:{id:0..100}, toys:{id:0..100}, favSpot|null, favToy|null}, toys:[unlocked ids],
           quirks:{acq:[ids]}, legacy?:{…2.4} }],
  pair: { rel, level:0..4, lead:-1|0|1, settled, gainLeft } | null,
  context: { timer:bool, restDay:bool },
  cue: { fed:[{cat,id,ago}], cheer:[{id,kind,ago}] },
  stored: { epoch, rev, blob } | null }
```

- It is **state, not a stream**: a dropped or reordered projection self-heals (kit `sequence` already drops older ones). `seq` bumps on
  any change; `epoch` bumps when the page replaces the world (backup restore, cat passes, `petAdoptNew`), which makes native discard its
  in-memory world for the stored one.
- `zMax` is the page's `petDepthMaxZ` (`24:46`, trust tiers and the hunger hold), `feedAt` is when `petFeed`'s 4 h gate opens, `giftOk`
  is level 5 and `lastGiftDay !== today`. Native never re-derives care rules; between projections it advances `hunger.stage` at
  `nextStageAt` only.
- Size: ≤ 6 KB without `stored`; `stored.blob` ≤ 8 KB (2.3). `likes` sends at most 13 spots and 7 toys.

### 2.2 Native to page: commands (all `{op}`; repeat of a landed op answers ok, `changed:false`, "Already recorded.")

| command | when | page does | reply |
|---|---|---|---|
| `feed`, `vacation`, `rename`, `coat` | as today (`50-native-pet.js:146-196`) | unchanged | unchanged |
| `pet {op, cat, heldMs}` | CT2, stroke ≥ 1.1 s | `petPetDone` + `petLikesPetted` + `petLevelUp` cue | `{ok, message, buzz}` |
| `play {op, cat, toy, played}` | an owner-offered game ends | `petPlayDone` (if `played`) + likes credit for the toy | `{ok, changed}` |
| `gift {op, cat}` | owner taps the gift | `p.gifts++`, `lastGiftDay`; `petWorldMoment(cat,'gift')` | refuses when `giftOk` was false |
| `buyToy {id}` | tray | `ecoBuy('toy')` | as CT4 planned |
| `moment {op, cat, kind, at}` | 2.5 | `petWorldMoment` | ok when persisted |
| `social {op, kind, cats:[0,1], winner?, at}` | a pair activity completes | `petPairGain(kind)` (daily cap stays page, `29-pair:69`), `n[kind]++`; standoff: `petRankUpdate(rk, winner)` (`29-rank:35`) | ok, `gainLeft` in next projection |
| `dwell {op, cat, day, spots:{id:s}, toys:{id:s}}` | every 10 min awake and on hide | the likes daily credit (`25:86` logic fed by seconds instead of the web tick); favourites set by the page | ok |
| `world {op, epoch, rev, blob}` | 2.3 | store `state.petWorld`, `writeSave()` | ok only when persisted |

The blink button becomes native-only (the native brain decides whether the cat returns it; 3 in 6 s and asleep rules are ported into
`Reflexes`); a returned blink reports `moment blink`. `petBlinkClick` stays for the classic room.

### 2.3 World state persistence: `state.petWorld`

```
petWorld = { v:1, epoch, rev, at, imported:"legacy1"|"fresh",
  cats: { "<catId>": { pos:{s,x,z,dir}, act, actAt, dr:{en,pl,cu,so,co,cm,te,gr}, mood:{v,a},
          trust:{fam,tru,sec,aut}, spots:{id:0..100 (top 6)}, qk:{inn:[2 ids], c:{n,t,g,k}},
          rhythm:[24 × 0..100], fx:[reported first_* kinds], rng:[3 ints], day, sched:{wake,bed},
          voice, cheerSeen:[≤16 ids] } },
  pair: { last:{kind,at}, cool:{kind:until} } | null,
  toys: [{id, s, x, z, by:catId|null}] (≤ 8), gift: {cat, toy, x, z, day} | null,
  outbox: [{op, cmd, args}] (≤ 16), away: {from, lines:[≤ 3 strings]} | null }
```

- **Page side** (`50-native-cat-world.js`, new, after `50-native-pet.js`): accepts when `v` is a known integer, `epoch` equals the
  page's world epoch, `rev` > stored `rev`, the JSON is ≤ 8 KB and its `cats` keys are the living cats' ids. Otherwise
  `{ok:false, why:'stale'|'size'|'shape'}`. It never reads inside the blob. Writes with `writeSave()` (no re-render, as the web brain's
  `26:100`). Backups carry it automatically (it is in `state`); restore bumps the epoch, which CB0 keeps in localStorage `lifemax_pet_world_epoch_v1` beside the save so a backup still comes back exactly as made.
- **Native side**: writes when an activity changes (at most every 60 s), at once after a moment or a pair event, and on `ON_STOP`.
  `rev` strictly increases. A failed write keeps the world dirty and retries in 30 s; nothing waits on it. Over 8 KB: drop spot scores
  below the top 4, then toys not on screen, then `away`.
- **Decode** of a stored blob is strict; a bad blob (or a newer `v`) is ignored for that cat, which is then derived (2.4). Invariant:
  the page is the only writer of the save, native is the only author of the blob's contents, and nobody else edits it.

### 2.4 Start without a world, and the one-time import of the web brain

When `stored` lacks a living cat's id, the page includes `legacy` for that cat, built by `petWorldLegacy(p)` *reading* `p.brain`
without calling `migratePetBrainState` (so it creates nothing):
`legacy = { af:{v,a}, at:{fam,tru,sec,aut}, fx:[keys of mem.fx], inn:[qk.inn], c:qk.c, topZones:[the 6 heaviest mem.w keys with weights],
rhythm: rt.h (24), energy: zm.e, items:[b.items toy ids and spots], voice: vo.p, pos: p.bond if under 10 min old }`.

Mapping (lossy, sensible): mood ← af; trust ← at (same meaning: familiarity, trust, security, autonomy); spot memory ← topZones mapped to
`PET_SPOTS` keys ×100 (the `front_*` zones become the glass comfort room); `fx` ← reported moments (never re-emitted); innate quirks and
counters ← `inn`, `c`; rhythm ← `rt.h` ×100; energy ← `zm.e`; toys on the floor ← items at their spots; voice pitch ← `vo.p`; position ←
`bond` (posX/posZ/elevationY inverse of `24:115-119`) else the schedule's place for now. Missing brain (a fresh cat): defaults from `ps`.
Bond style, mood word and the four threads therefore continue; Ted and Lily keep their quirks and favourite places. `p.brain` stays in
the save untouched except the two fields the page keeps writing for the album: `mem.fx` and `qk.acq`.

### 2.5 Moments for the scrapbook (exactly one source, no duplicates)

The detector today listens to `petBrainEvent` (high kinds gift, blink, respect, `50-pet-moments.js` `PET_MOMENT_EVENTS`) and reads
`p.brain.mem.fx`, `p.brain.qk.acq`, `p.likes.favSpot/favToy`, `p.gifts`, `state.petPair.n.nap`, pair level and `rk.settled`
(`50-pet-moments.js`). With the native brain, `petBrainEvent` never fires (26 is not ticking), so:

| native `moment` kind | native trigger (view visible where the web required it) | page `petWorldMoment` writes | album entry |
|---|---|---|---|
| first_glass | glass visit completes while a view shows | `mem.fx.first_glass=1` → `petAlbumScan()` | glass |
| first_co | comfort sit ≥ 45 s | `fx.first_co` | comfort |
| first_zoomies | zoomies seen | `fx.first_zoomies` | zoomies |
| first_belly | belly near the front seen | `fx.first_belly` | belly |
| first_vigil | waiting at the glass when the view opens | `fx.first_vigil` | vigil |
| first_trophy | a hunted item carried to the glass | `fx.first_trophy` | trophy |
| blink | a slow blink returned | `fx.first_blink`, `petAlbumEvent('blink')` | blink |
| respect | owner stopped at the warning and waited 8 s (CT2 `TouchModel`) | `fx.first_respect`, `petAlbumEvent('respect')` | respect |
| quirk {id} | acquired-quirk counter crossed (`PET_QUIRK_ACQ` thresholds 10/15/20/12) | append `qk.acq` (valid id, once) | quirk ladder |
| (gift command) | owner taps the gift | `p.gifts++`, `petAlbumEvent('gift')` | gift |
| (social nap / levels / standoff) | pair activity | `petPair` via `petPairGain`, `petRankUpdate` | pair_nap, pair_level, rank |
| (dwell) | time in spots and with toys | `likes` credit → favourites | fav_spot, fav_toy |

Duplicates are impossible three ways: `op` = `<catId>:<kind>` for first_* (repeat-safe), the page's `fx` flag is idempotent, native
keeps `fx` in the blob. `recovery`, `bond`, `grow`, `days`, `birthday`, `adopt` stay pure page. `50-pet-album.js` gains one line:
`around("petWorldMoment", …)`. Exactly one brain is active per page life (2.7), so web and native never both report.

### 2.6 Offline catch-up and the "while you were away" text

On resume or cold start with a gap > 5 min, `CatWorld.catchUp(from, to)` runs the **same selector in abstract mode**: no body, activities
take their sampled durations and drive effects, positions jump to the activity's place (reachability checked by the planner, no
flights), pairs may curl up or groom, toys may be moved, a gift may be staged. Seed `"<catId>|off|<from>"`. Cost bound: ≤ 400 macro
steps; gaps over 24 h simulate only the last 24 h after resetting drives to the schedule's steady state. Under 5 ms on the phone.
Output: the end state (the cats are where the story says), and `away.lines` (≤ 3, e.g. "Ted slept on the sofa most of the afternoon",
"Lily batted the jingle ball around the kitchen", "They napped together on the bed"). The page's `petOfflineCatchup` (`26:630-657`) is
not called; the mind row's "while you were away" text comes from the world (PetMindCards reads native data, 2.8).

### 2.7 Lifecycle: one CatWorld, page hidden, native not running

- **Owner of the brain:** the page decides once per page life and on switch change: `petBrainOwner = 'native'` when the switch is on and
  a plugin advertised `pet.world.v1`; then `petWantRun`/`petBrainTick` (`17:74-81`, `26:740-749`) never tick the web brain and the
  page's Cat view hides its room canvas (panels that stay page-only, intro, memorial, cattery, still show). Else `'web'`, today's path.
- **`CatWorldHost`** (app layer, process singleton; `CatWorld` itself is headless): created on the first attach of any view (Cat tab,
  full screen, Overview card, later widget), fed every `world` block from either plugin (newest `seq` wins), driven by the frame clock
  of the visible views through `advanceTo` (idempotent). No view visible: frozen; on the next attach it runs `catchUp` from the frozen
  time. Process death loses at most the last 60 s (the blob is the persistence).
- **Page hidden or slow:** nothing blocks; the outbox queues commands (in the blob, so a restart re-sends) and flushes when the page
  answers. Command order per cat is kept; every command is repeat-safe.
- **Before the first `world` block:** the world shows the cats from the cards' looks sitting with idle life, emits nothing, and replaces
  them on arrival (a cut, no walk). With the switch off, the Overview card keeps its shipped `HouseLife`/`Autopilot` until CB8.
- **Overview = Cat tab:** the card draws the same `CatWorld` through `RoomCamera` framing the focus cat (no more `minX..maxX` strip).
  **Widget/wallpaper later:** `CatWorld.restore(blob).catchUp(now).snapshot()` is a pure function; nothing live is needed.

### 2.8 Panels that switch source

With the native brain, the mind row (bond style, mood word, away) and inner life (threads, quirks, memories, comfort) of
`pet.panels.v1` (`presentation/PetMindCards.kt`, `data/PetPanelsJson.kt`) are filled from `CatWorld`; the page stops sending those
fields when `pet.world.v1` is on. Likes bars, DNA, Together card counts and rank stay page data.

## 3. What is retired on the page, and when

| page code | lines | fate | when |
|---|---|---|---|
| `26-pet-brain.js` | 923 | delete behaviour; keep `PET_QUIRKS`, `PET_CORE_TEXT` (album captions) and a 40-line `petWorldLegacy` reader in a new file | CB8 + one release |
| `25-pet-likes.js` | 268 | keep `PET_SPOTS`, `PET_TOYS`, `petLikes` data, favourites, toy unlocks/words; delete options/tick/pick-spot (now dwell-fed) | CB8 |
| `27-pet-play.js`, `28-pet-box.js`, `30-pet-sleep.js` | 476 | delete (behaviour) | CT10 |
| `29-pet-pair.js` | 599 | keep data, `petPairGain`, levels, adopt, Together card; delete director, plays, `petAs`, wrappers | CT10 |
| `29-pet-rank.js` | 100 | keep (math and card) | stays |
| `24-pet-depth.js` | 258 | keep `petTrust`/`petDepthMaxZ` (feed `zMax`); delete drawing, walking, pointer | CT10 |
| `15-runtime.js` | 301 | keep care maths `15:57-123` and `petNow`; delete schedule `15:141-170`, `petThink` `15:238`, routes | CT10 |
| `16/17` room loop, `50-pet-hd.js`, `38-pet-rig.js` compose | ~2,100 | delete | CT10 (plan's CT10 + 1 release) |
| `32-pet-dna.js`, `37-pet-morph.js`, `50-pet-coat.js`, `44-rewards.js` hooks, `50-pet-album.js`, `50-pet-moments.js`, quests, deep link, voice | — | stay (identity, economy) | — |

Safety: until CT10 the classic room and web brain run whenever the switch is off or the app is older; switching off after a native
period resumes the web brain from its frozen `p.brain` (milestones and favourites continue because the page kept writing them). The
handover simulation gets a `world` mode (switch flips mid-session, page reload, stale `rev`, wrong `epoch`).

## 4. Phases

Sizes are lines incl. tests (±30 %); a pass = one agent, one worktree, green local build. Every phase bumps `NativeApi.VALUE`, adds a
`BRIDGE_CATALOG.json` revision and `MIGRATION.md` row, keeps files under 600 lines and existing screenshots byte-identical.

| phase | scope | page | native | tests | size | passes |
|---|---|---|---|---|---|---|
| **CB0** contract + harness | data classes and strict decoders for `world`, blob, commands; `FakePage` driver; `WorldClock`; headless source-set check; day viewer skeleton; page `petWorldBlock`, `petWorldLegacy`, `world` command stub | 250 | 450 | 350 | 1,050 | 1 |
| **CB1** brain core | drives, mood, persona, scheduler, selector, runner, SpotBook, explain ring; activities: sleep + waking routine, loaf, groom, wander, window, parkour visit, eat, beg, scratch, knead; `tone(v,a)` on `CatBody` | 0 | 1,900 | 1,300 | 3,200 | 3-4 |
| **CB2** world service + persistence | `CatWorldHost`, Overview/Cat tab share one world, `world` command end to end, import, catch-up + away lines, brain owner guard, panel source swap | 400 | 900 | 700 | 2,000 | 2-3 |
| **CB3** owner interaction | TouchModel → brain, head-bump, slow blink, comfort sit, glass + fog, vigil, belly, respect; `moment` + album wrapper | 200 | 700 | 500 | 1,400 | 2 |
| **CB4** toys + box | `ToyBody` physics (`16:9-27` numbers), pickup/carry pendulum on `LabTable` mouth anchors, toss, solo/owner play, hunt, trophy, gift, box sink, tray, `play`/`gift`/`dwell` | 250 | 1,200 | 800 | 2,250 | 3 |
| **CB5** pair | `SocialDirector`, greet/chase/groom/curl up/standoff with pair offsets, `social` command, kitten shadowing | 150 | 850 | 600 | 1,600 | 2 |
| **CB6** cheers + zoomies | cue builders (director, level up, timer, rest day), reactions, zoomies, rest-day modifier | 200 | 350 | 250 | 800 | 1 |
| **CB7** native sound (optional, parallel after CB3) | SoundPool port of the 13 clips, per-cat pitch, chained purr, cues from the runner | 50 | 400 | 150 | 600 | 1 |
| **CB8** default + retire | `defaultOn` after phone sign-off; one release later delete 26/25 behaviour (−1,100 page lines); CT10 deletes the rest (−2,900) | −4,000 | −700 (Autopilot, HouseLife, CatLife) | 200 | — | 1-2 |

Total ≈ 12,900 added lines, 16-19 passes, then about 4,700 lines deleted.

**Order and parallelism.** CB0 first (one agent, the stronger model for the contract design per CLAUDE.md). Then in parallel:
A = CB1 (owns `brain/`, `CatBody.kt` `tone` hook); B = CB2 page half + `CatWorldHost` against `FakePage` (owns `50-native-cat-world.js`,
plugins); C = CB4 `ToyBody` and carry physics as a standalone body add-on (owns `ToyBody.kt`, `Carry.kt`). Merge A+B → CB2 integration →
CB3 → CB4 brain wiring → CB5 → CB6; CB7 any time after CB3. **Hotspots:** `CatBody.kt`/`Plan.kt`/`Tuning.kt` (one owner per window; add
via new files and small hooks), `50-native-pet.js` and `50-native-overview-cards.js` (one shared `petWorldBlock` in its own file),
`50-pet-album.js` (one line), `17-runtime.js` guard (CB2 only), `HouseLiveView.kt`/`HouseStage.kt` (Overview; rerun its screenshot,
fidelity and allocation tests), `NativeApi.VALUE`, `BRIDGE_CATALOG.json`, `native-handover-sim.test.js` (append a mode, not a fake).

**Needs the owner's phone:** CB1 believability of a real day (after the day viewer), CB2 the import on the real save (Ted and Lily: the
owner exports a backup; the import test runs locally and is never committed), CB3 head-bump and touch feel, CB4 pickup/carry/toss feel,
CB5 pair contact per growth stage, CB6 cheer timing, frame cost with two cats and toys (< 4 ms, no per-frame allocation).

### 4.1 Testing strategy

- **Golden day traces** (JVM): `CatDaySim(seed, persona, start, hours=24, cats=1|2)` runs the real world at 120 Hz sub-steps with a
  fake clock and owner script (opens the view 3 times, feeds twice, pets, offers a toy, one session_done, one timer, one level up).
  The trace (activity starts with place and `why` top-3, moments, commands, 1 Hz poses) is golden for 6 personas (lazy, skittish,
  playful, curious, dominant, friendly kitten) × 2 days; regenerated only on purpose with a review diff.
- **Sanity bounds** per persona over 20 seeds × 7 days: adult sleep 12..16 h/24 h, kitten 14..18 h with shorter bouts; lazy sleeps
  ≥ 1.5 h more than spontaneous on average; no awake activity > 35 % of awake time; zoomies only in dusk window or post-meal; at least
  one window watch and one parkour visit per day for ou > 0.6; weak cat: zero flights above floor props, ≥ 80 % of time within 60 px of
  the bowl; vacation: no beg.
- **Properties** (every sub-step, all seeds): no teleport (position delta ≤ the body's max speed × dt except a declared `place` cut);
  never stuck (a cat not sleeping, not loafing and not in a timed act changes pose or plan within 20 s); two cats never hold the same
  single-occupancy surface, floor gap ≥ `HousePlans.GAP` except pair poses; every flight within `JumpCapability` (re-checked by
  `LeapDirector` arcs); every cheer cue id produces exactly one reaction start per cat, none on replay; every first_* moment at most
  once per cat; outbox ops unique; blob ≤ 8 KB after 30 days.
- **60/120 Hz equality:** the same day stepped with 1/60, 1/120, jittered 1/90..1/30 and a 2 s stall gives identical poses at every
  shared sub-step and identical traces (bitwise floats).
- **Soak:** 2 cats × 60 simulated days in fast mode, random owner scripts and projection drops/reorders/duplicates, random process
  deaths (restore from the last written blob): no exception, no drive NaN or drift outside 0..1, reservations released, toy count
  constant, `rev` monotonic, catch-up of 1 h equals within tolerance the distribution of live hours (activity mix within ±10 points).
- **Contract:** strict decoder malformed variants keep the last good world; node tests drive the real `petWorldMoment`, `world`,
  `social`, `dwell`, `gift`, `play` handlers (persist failure → ok:false; repeat → "Already recorded."; stale rev; wrong epoch; size);
  import golden from three synthetic `p.brain` saves (v77 bond-only, full brain, damaged brain).
- **Day viewer** (`CatDayViewer`, a JVM test-scope main, run via a Gradle test filter): prints one line per activity
  (`07:12 Ted  sleep→waking routine  bed  (energy .91, why: wake hour)`), a 24-column-per-hour ASCII strip per cat (S sleep, L loaf,
  P play, W window, K parkour, E eat, G groom, Z zoomies, ♥ owner, = pair), the moments and commands, and writes an SVG timeline under
  `build/cat-days/`. Humans judge believability from it before phone time.

### 4.2 Cost against the old CT3/CT4/CT5 stream plan

Old: CT0 1 + CT3 5-6 + CT4 4 + CT5 5 = 15-16 passes, about 6,000 lines, and the behaviour still lives in a 923-line web brain plus likes,
play, box, sleep and pair (about 2,700 lines) that CT-L must later port with seed-exact plan-equality goldens; it carries the worst risks
of the plan (mirror/predictor drift, page throttling under the cover, closures that read the live world, two-sided latency, sound tied to
page events). New: 16-19 passes and about 12,900 lines, but no stream, no CT0 latency gate, no behaviour port in CT-L (only care maths
remain), and about 4,700 page lines deleted at the end. **Saved:** the riskiest phase (CT3), the CT-L brain/likes/pair port (estimated
8-10 passes), the double brain. **Risked:** the cats behave differently from the cats the owner knows (mitigated by the import and by
tuning against the day viewer); believability now rests on our tuning rather than on a tuned web brain; native sound becomes needed
(CB7); more native code to own. Net: about the same passes to a native default, far fewer passes to the end state.

### 4.3 Risks

- *Feels less alive than the web cat:* the day viewer and persona bounds come first; phone sign-off is a CB1 exit gate.
- *Overview card regressions:* the shared world replaces `HouseLife`; existing Overview screenshots are kept via a fixture world.
- *Album holes:* the 2.5 table is a test (each kind fed natively in a node test asserts the album entry once).
- *Commands while the Cat tab is not showing:* the `pet` kit must accept commands while warm and covered; CB0 proves it, else the
  outbox waits for the next open (acceptable: moments are not time-critical).
- *Blob loss on process death:* ≤ 60 s of behaviour; never care data (page-owned).

## 5. Questions for the owner (recommended default in brackets)

1. When you turn "Native Cat (beta)" off after using it, the cats return to the web brain's last saved mood and places (milestones,
   favourites and quirks continue). Acceptable? [Yes.]
2. Sound: the native room is silent except the touch purr (page) until CB7. Build CB7 right after CB3, or after CB6? [Right after CB3.]
3. Cheers: react to finished Life Director sessions, level ups, running timers and rest days, and nothing else (no streaks, no quests)?
   [Yes, these four; never a notification.]
4. Opening the app at night: the cats sleep in the dark; a trusting cat lifts its head and slow-blinks, but nobody fully wakes up unless
   it is a level up. [Yes.]
5. Vacation: a "sitter" fills the bowl, no begging, normal life; the hunger clock stays paused as today. [Yes.]
6. Art: ship with the recomposed rows and the fallbacks of 1.10, and ask an artist later for the four hand-touched pieces (toss flick,
   wide yawn, body shake, hiss arch)? [Yes; ask after CB5 when we know which ones show most.]
