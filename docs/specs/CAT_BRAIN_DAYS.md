# Cat brain days (CB1)

What the native cat brain (docs/specs/CAT_BRAIN_PLAN.md, phase CB1) does with a whole simulated day, as
the day viewer prints it, and an honest look at whether it reads like a cat. Regenerate with

```
CAT_DAY=/tmp/cat-days ./gradlew :app:testDebugUnitTest --tests '*CatDayViewerTest*'
```

which runs the six cases of `CatDays.CASES` (test scope) for 24 h of simulated time on live bodies at
120 Hz sub-steps and writes `<case>.txt` and `<case>.svg`. The owner's day is the same in each case
unless said: views open 07:45-08:15, 12:30-12:42, 18:00-19:00 and 21:30-21:45, meals at 08:00 and
18:15, the page's hunger stages in between (petStageOf: 0 under 8 h, 1 to 14 h, 2 to 26 h, 3 to 50 h).
Below, the page's hunger lines are left out of the timelines. A line is an activity begun:
`time cat previous→activity place (level of its main drive, why)`. Strip letters: S sleep, L loaf,
W window, K parkour, R wander, G groom, E eat, B beg, X scratch, N knead, Y waking routine.

## Assessment

**What reads right.**
- *Sleep.* Adults sleep 12-16 h (lazy about 15-16, playful about 12-13, two-cat average about the same),
  a kitten 14-15 h in many 25-50 min naps by day. Nights are two or three bouts of 2-4.5 h with a short
  waking interlude (wash, a look out of a window, back to sleep), not one block, and the morning starts
  with the waking routine (yawn, stretch, shake, often a wash) between 06:00 and 08:00, later for the lazy
  cat. Afternoon naps cluster in the nap windows; the lamps and bed hours settle everybody after 22:00.
- *Meals.* A meal is answered at once from anywhere (a sleeping cat yawns and stretches first), takes
  the web's 7.5 s eat and 4.5 s wash, and an interrupted nap is resumed afterwards for the time it had
  left. A washed-after-dinner groom follows most meals.
- *Window time and parkour.* Window watching is the main waking pastime (1.3-2.6 h), more at dawn and
  dusk ("birds out") and for outgoing cats. Parkour is occasional: the lazy and shy cats climb once a
  day or not at all, the playful one five or six times (the side table and the tree top most, the fridge via the counter
  once or twice), never a kitten to the fridge (geometry) and never a weak cat above the floor props.
- *Characters differ.* Averaged over twelve simulated days (PersonaMixTest) the lazy cat sleeps 15.5 h, the
  playful one 12.1 h, the shy one 15.0 h. The shy cat sleeps in the box and behind the cushion, rests behind the bed, and
  spends about 80 % of its waking time low or hidden against about 50 % for the playful one; the lazy cat
  rarely wanders and climbs once a day at most.
- *Hunger.* The weak cat (very hungry all day, fed at 19:00) keeps to the bed and the floor by its bowl,
  lies about more than it moves, begs at the bowl whenever the owner looks in and is awake (a deeply
  asleep hungry cat is left asleep), and after its meal has a burst of curiosity: it wanders, scratches
  and climbs for the first time that day.
- *No machine-gun switching.* Rests last 8-40 min, window watches 6-25 min, naps 30-120 min; the short
  activities (scratch 6-15 s, knead, the waking routine) are beats between them. No activity ever ran
  into the watchdog.

**What does not read right yet (and why).**
- *Rest dominates the waking day.* Besides sleep, loafing takes 60-80 % of the awake time (plan 4.1
  asked for no awake activity above 35 %; every other activity holds that bound, see PersonaMixTest).
  CB1 has no play, toys, owner interaction or pair activities; those are CB3-CB6 and will take a large
  share of this time. Until then the days look like an indoor cat's quiet day.
- *Wander, then loaf.* A playful cat's waking hour is often "wander to another room's floor, sit, then
  go and loaf on a favourite cushion". It is cat-like (a patrol), but it repeats; the zoomies, toys and
  hunts of later phases are what a real cat fills it with.
- *Two cats ignore each other.* They never share a perch or a floor spot (the place book), but they do
  not greet, follow or curl up: the pair activities are CB5.
- *The begging cat walks back to its rest spot between begs* instead of waiting at the bowl.
- *Pupils and a front-facing sit are art gaps* (plan 1.10), not faked.

## The tuning knobs

All in `feature/pet/brain/BrainTuning.kt` unless named:

| knob | now | what it changes |
|---|---|---|
| `Drives.ENERGY_AWAKE_TAU` | 4.3 h | sleep pressure: how soon a cat is tired again (the main lever on sleep hours) |
| `Drives.ENERGY_SLEEP_TAU`, `SOFT_SLEEP_GAIN` | 75 min, ×1.6 | how fast a nap restores energy, and how much a soft spot helps |
| `Persona.DRAIN_LO/HI`, `REST_LO/HI`, `LAZY_SLEEPY` | 0.75-1.45, 0.75-1.6, 0.08 | how far a lazy cat sleeps and rests more than a spontaneous one |
| `Persona.KITTEN_*` | drain ×2, naps 25-45 min, sleepy below 0.85 | kitten sleep share and nap length |
| `Persona.WEAK_LOAF`, `WEAK_SLEEP`, `WEAK_BOWL_REACH` | ×1.5, ×0.35, 60 px | a weak cat's lethargy and how close it stays to its bowl |
| `Schedule.*` | wake 6.0-7.4 (+0.6 lazy), bed 22.0-23.5, 2-4 naps | the shape of the day (factors on sleep and getting about) |
| `Schedule.NIGHT_BOUT_LO/HI_H` | 2.2-4.5 h | how often a cat stirs in the night |
| `Durations.*`, `Cooldowns.*` | see file | activity lengths and how soon an activity may come back (the anti-dithering levers) |
| `Select.NOVELTY`, `SAME_ELSEWHERE`, `NOISE`, `TRAVEL_S` | 0.5/0.7/0.85, ×0.4, ±6 %, 30 s | variety, staying put, randomness, how much distance matters |
| per-activity `BASE` (RestActivities.kt, MoveActivities.kt, CareActivities.kt) | see files | the relative pull of each activity |
| `Places.*` | liking 0.6 + 0.8×like, soft ×1.4, sun ×1.8 10-16 h | which places a cat chooses |
| `ToneTuning` (feature/pet/body/BodyTone.kt) | ears, tail, pace ±15 %, breath ±25 % | how strongly the mood shows on the body |

## The days

### A lazy adult (Ted: ou 0.15, sp 0.15)

```
00:00 Ted   sleep  bed  (level .60, why: bed hours)
03:41 Ted   sleep→waking routine  bed  (level 1.00, why: after sleep)
03:41 Ted   waking routine→groom  bed  (level .50, why: wants a wash)
03:41 Ted   groom→loaf  bed  (level .01, why: rests)
03:53 Ted   loaf→sleep  bed  (level .96, why: bed hours)
06:23 Ted   sleep→waking routine  bed  (level 1.00, why: after sleep)
06:23 Ted   waking routine→scratch  sofa arm  (level .57, why: marks its house)
06:23 Ted   scratch→window watch  kitchen sill  (level .67, why: curious)
06:40 Ted   window watch→sleep  bed  (level .91, why: bed hours)
07:45 owner open
07:47 Ted   sleep→waking routine  bed  (level .98, why: after sleep)
07:47 Ted   waking routine→groom  bed  (level .53, why: wants a wash)
07:50 Ted   groom→wander  living rug  (level .13, why: curious)
07:51 Ted   wander→loaf  bed  (level .02, why: rests)
08:00 owner feed
08:00 Ted   loaf→eat  bowl  (level .09, why: fed)
08:00 Ted   eat→loaf  bed  (level .01, why: resume)
08:15 owner close
08:32 Ted   loaf→groom  bed  (level .64, why: wants a wash)
08:35 Ted   groom→wander  living rug  (level .39, why: curious)
08:40 Ted   wander→parkour visit  side table  (level .52, why: surveys its house)
08:53 Ted   parkour visit→sleep  bed  (level .72, why: tired)
11:29 Ted   sleep→waking routine  bed  (level .99, why: after sleep)
11:29 Ted   waking routine→loaf  bed  (level .00, why: rests)
11:44 Ted   loaf→groom  bed  (level .45, why: wants a wash)
11:46 Ted   groom→window watch  living sill  (level .36, why: curious)
12:13 Ted   window watch→loaf  living sill  (level .17, why: rests)
12:30 owner open
12:42 owner close
12:54 Ted   loaf→sleep  bed  (level .71, why: tired)
14:56 Ted   sleep→waking routine  bed  (level .98, why: after sleep)
14:56 Ted   waking routine→groom  bed  (level .51, why: wants a wash)
14:58 Ted   groom→window watch  living sill  (level .44, why: curious)
15:26 Ted   window watch→scratch  sofa arm  (level .63, why: marks its house)
15:27 Ted   scratch→loaf  bed  (level .17, why: rests)
15:58 Ted   loaf→wander  living rug  (level .24, why: curious)
16:00 Ted   wander→loaf  sofa  (level .02, why: rests)
16:20 Ted   loaf→sleep  sofa end  (level .70, why: nap window)
18:00 owner open
18:15 owner feed
18:15 Ted   sleep→eat  bowl  (level .57, why: fed)
18:15 Ted   eat→sleep  sofa end  (level .97, why: resume)
18:48 Ted   sleep→waking routine  sofa end  (level .99, why: after sleep)
18:48 Ted   waking routine→groom  sofa end  (level .68, why: wants a wash)
18:51 Ted   groom→window watch  living sill  (level .46, why: curious)
18:59 Ted   window watch→scratch  tree post  (level .57, why: marks its house)
19:00 owner close
19:00 Ted   scratch→loaf  sofa end  (level .07, why: rests)
19:45 Ted   loaf→wander  kitchen floor  (level .36, why: curious)
19:48 Ted   wander→loaf  bed  (level .02, why: rests)
20:36 Ted   loaf→sleep  bed  (level .68, why: nap window)
21:30 owner open
21:45 owner close
21:46 Ted   sleep→waking routine  bed  (level .93, why: after sleep)
21:46 Ted   waking routine→groom  bed  (level .56, why: wants a wash)
21:48 Ted   groom→scratch  tree post  (level .60, why: marks its house)
21:49 Ted   scratch→wander  living rug  (level .53, why: curious)
21:52 Ted   wander→loaf  bed  (level .03, why: rests)
22:23 Ted   loaf→wander  living rug  (level .45, why: curious)
22:27 Ted   wander→loaf  bed  (level .03, why: rests)
22:40 Ted   loaf→loaf  bed  (level .00, why: rests)
23:25 Ted   loaf→sleep  bed  (level .66, why: bed hours)

Ted   |SSSSSSSSLSSSLSSLSSSLLSLS|

Ted   sleep 16.1h, loaf 5.7h, window watch 1.3h, wander 0.3h, groom 0.3h, parkour visit 0.2h, scratch 0.1h, eat 0.0h
```

### A playful, outgoing adult (Pip: ou 0.85, sp 0.85)

```
00:00 Pip   sleep  sofa end  (level .60, why: bed hours)
03:43 Pip   sleep→waking routine  sofa end  (level 1.00, why: after sleep)
03:43 Pip   waking routine→groom  sofa end  (level .50, why: wants a wash)
03:45 Pip   groom→window watch  living sill  (level .65, why: curious)
04:04 Pip   window watch→sleep  sofa end  (level .93, why: bed hours)
07:28 Pip   sleep→waking routine  sofa end  (level 1.00, why: after sleep)
07:28 Pip   waking routine→scratch  sofa arm  (level .68, why: marks its house)
07:29 Pip   scratch→window watch  living sill  (level .45, why: curious)
07:38 Pip   window watch→groom  living sill  (level .47, why: wants a wash)
07:40 Pip   groom→wander  kitchen floor  (level .05, why: curious)
07:42 Pip   wander→parkour visit  fridge  (level .34, why: surveys its house)
07:45 owner open
07:52 Pip   parkour visit→loaf  box  (level .13, why: rests)
08:00 owner feed
08:00 Pip   loaf→eat  bowl  (level .19, why: fed)
08:00 Pip   eat→loaf  box  (level .05, why: resume)
08:15 owner close
08:19 Pip   loaf→groom  box  (level .65, why: wants a wash)
08:19 Pip   groom→wander  rug  (level .33, why: curious)
08:21 Pip   wander→loaf  sofa end  (level .02, why: rests)
08:32 Pip   loaf→window watch  living sill  (level .33, why: birds out)
08:42 Pip   window watch→wander  rug  (level .02, why: curious)
08:43 Pip   wander→loaf  sofa end  (level .08, why: rests)
08:55 Pip   loaf→groom  sofa end  (level .47, why: wants a wash)
08:56 Pip   groom→loaf  sofa end  (level .02, why: rests)
09:18 Pip   loaf→wander  rug  (level .40, why: curious)
09:20 Pip   wander→loaf  bed  (level .02, why: rests)
09:47 Pip   loaf→scratch  sofa arm  (level .58, why: marks its house)
09:47 Pip   scratch→wander  rug  (level .51, why: curious)
09:51 Pip   wander→groom  living rug  (level .46, why: wants a wash)
09:53 Pip   groom→loaf  sofa end  (level .04, why: rests)
10:15 Pip   loaf→window watch  living sill  (level .47, why: curious)
10:35 Pip   window watch→sleep  sofa end  (level .61, why: tired)
11:33 Pip   sleep→waking routine  sofa end  (level .89, why: after sleep)
11:33 Pip   waking routine→wander  kitchen floor  (level .16, why: curious)
11:35 Pip   wander→parkour visit  side table  (level .51, why: surveys its house)
11:42 Pip   parkour visit→loaf  living sill  (level .06, why: rests)
11:59 Pip   loaf→wander  kitchen floor  (level .26, why: curious)
12:02 Pip   wander→loaf  box  (level .03, why: rests)
12:10 Pip   loaf→groom  box  (level .56, why: wants a wash)
12:12 Pip   groom→loaf  box  (level .03, why: rests)
12:30 owner open
12:30 Pip   loaf→window watch  sill  (level .41, why: curious)
12:42 owner close
12:43 Pip   window watch→loaf  sill  (level .09, why: rests)
12:53 Pip   loaf→loaf  sill  (level .02, why: rests)
13:17 Pip   loaf→window watch  living sill  (level .40, why: curious)
13:30 Pip   window watch→sleep  sofa end  (level .65, why: nap window)
14:02 Pip   sleep→waking routine  sofa end  (level .82, why: after sleep)
14:02 Pip   waking routine→groom  sofa end  (level .49, why: wants a wash)
14:03 Pip   groom→scratch  sofa arm  (level .59, why: marks its house)
14:03 Pip   scratch→parkour visit  side table  (level .24, why: surveys its house)
14:10 Pip   parkour visit→loaf  living sill  (level .07, why: rests)
14:34 Pip   loaf→window watch  living sill  (level .32, why: curious)
14:44 Pip   window watch→wander  kitchen floor  (level .02, why: curious)
14:48 Pip   wander→loaf  sofa end  (level .10, why: rests)
15:07 Pip   loaf→groom  sofa end  (level .47, why: wants a wash)
15:09 Pip   groom→wander  rug  (level .27, why: curious)
15:10 Pip   wander→loaf  living sill  (level .03, why: rests)
15:20 Pip   loaf→window watch  kitchen sill  (level .32, why: curious)
15:40 Pip   window watch→sleep  bed  (level .62, why: tired)
17:04 Pip   sleep→waking routine  bed  (level .94, why: after sleep)
17:04 Pip   waking routine→scratch  sofa arm  (level .55, why: marks its house)
17:04 Pip   scratch→parkour visit  side table  (level .22, why: surveys its house)
17:10 Pip   parkour visit→wander  kitchen floor  (level .10, why: curious)
17:12 Pip   wander→loaf  behind the bed  (level .05, why: rests)
17:22 Pip   loaf→groom  behind the bed  (level .49, why: wants a wash)
17:23 Pip   groom→loaf  behind the bed  (level .02, why: rests)
17:49 Pip   loaf→window watch  living sill  (level .45, why: curious)
18:00 owner open
18:07 Pip   window watch→loaf  living sill  (level .11, why: rests)
18:15 owner feed
18:15 Pip   loaf→eat  bowl  (level .41, why: fed)
18:15 Pip   eat→loaf  living sill  (level .04, why: resume)
18:27 Pip   loaf→loaf  living sill  (level .01, why: rests)
18:43 Pip   loaf→groom  living sill  (level .76, why: wants a wash)
18:46 Pip   groom→wander  kitchen floor  (level .43, why: curious)
18:49 Pip   wander→loaf  box  (level .04, why: rests)
19:00 owner close
19:14 Pip   loaf→window watch  kitchen sill  (level .48, why: birds out)
19:28 Pip   window watch→parkour visit  fridge  (level .65, why: surveys its house)
19:31 Pip   parkour visit→wander  rug  (level .01, why: curious)
19:33 Pip   wander→loaf  bed  (level .14, why: rests)
19:47 Pip   loaf→sleep  bed  (level .62, why: tired)
21:13 Pip   sleep→waking routine  bed  (level .94, why: after sleep)
21:13 Pip   waking routine→wander  living rug  (level .37, why: curious)
21:16 Pip   wander→groom  floor  (level .49, why: wants a wash)
21:17 Pip   groom→loaf  sofa  (level .03, why: rests)
21:29 Pip   loaf→loaf  sofa  (level .00, why: rests)
21:30 owner open
21:45 owner close
21:48 Pip   loaf→scratch  sofa arm  (level .56, why: marks its house)
21:48 Pip   scratch→wander  rug  (level .50, why: curious)
21:51 Pip   wander→parkour visit  bookcase  (level .24, why: curious)
21:54 Pip   parkour visit→loaf  bed  (level .04, why: rests)
22:19 Pip   loaf→window watch  sill  (level .44, why: curious)
22:35 Pip   window watch→groom  sill  (level .48, why: wants a wash)
22:36 Pip   groom→wander  living rug  (level .02, why: curious)
22:38 Pip   wander→loaf  sofa end  (level .13, why: rests)
22:55 Pip   loaf→wander  rug  (level .22, why: curious)
22:57 Pip   wander→loaf  sofa end  (level .03, why: rests)
23:08 Pip   loaf→sleep  bed  (level .70, why: bed hours)

Pip   |SSSSSSSSLLSSLSLWSLLLSLLS|

Pip   sleep 12.3h, loaf 7.4h, window watch 2.7h, wander 0.6h, parkour visit 0.6h, groom 0.3h, eat 0.0h, scratch 0.0h
```

### A shy adult (Mia: sk 0.9, ou 0.2)

```
00:00 Mia   sleep  behind the cushion  (level .60, why: bed hours)
03:58 Mia   sleep→waking routine  behind the cushion  (level .98, why: after sleep)
03:58 Mia   waking routine→loaf  behind the cushion  (level .00, why: rests)
04:12 Mia   loaf→sleep  behind the cushion  (level .94, why: bed hours)
07:00 Mia   sleep→waking routine  behind the cushion  (level .99, why: after sleep)
07:01 Mia   waking routine→window watch  living sill  (level .71, why: birds out)
07:26 Mia   window watch→groom  living sill  (level .67, why: wants a wash)
07:29 Mia   groom→scratch  sofa arm  (level .63, why: marks its house)
07:29 Mia   scratch→loaf  behind the cushion  (level .16, why: rests)
07:45 owner open
07:56 Mia   loaf→wander  kitchen floor  (level .24, why: curious)
08:00 owner feed
08:00 Mia   wander→eat  bowl  (level .24, why: fed)
08:00 Mia   eat→groom  bowl  (level .62, why: wants a wash)
08:03 Mia   groom→loaf  behind the cushion  (level .05, why: rests)
08:15 owner close
08:35 Mia   loaf→sleep  behind the cushion  (level .70, why: tired)
09:20 Mia   sleep→waking routine  behind the cushion  (level .84, why: after sleep)
09:20 Mia   waking routine→window watch  living sill  (level .41, why: curious)
09:40 Mia   window watch→groom  living sill  (level .46, why: wants a wash)
09:41 Mia   groom→loaf  behind the cushion  (level .14, why: rests)
10:22 Mia   loaf→sleep  behind the cushion  (level .67, why: tired)
11:57 Mia   sleep→waking routine  behind the cushion  (level .91, why: after sleep)
11:57 Mia   waking routine→scratch  sofa arm  (level .66, why: marks its house)
11:57 Mia   scratch→window watch  living sill  (level .43, why: curious)
12:18 Mia   window watch→groom  living sill  (level .52, why: wants a wash)
12:20 Mia   groom→loaf  behind the bed  (level .14, why: rests)
12:30 owner open
12:42 owner close
12:49 Mia   loaf→wander  kitchen floor  (level .25, why: curious)
12:51 Mia   wander→loaf  behind the bed  (level .02, why: rests)
13:32 Mia   loaf→sleep  behind the cushion  (level .65, why: nap window)
15:03 Mia   sleep→waking routine  behind the cushion  (level .90, why: after sleep)
15:03 Mia   waking routine→window watch  sill  (level .51, why: curious)
15:20 Mia   window watch→groom  sill  (level .56, why: wants a wash)
15:22 Mia   groom→scratch  sofa arm  (level .63, why: marks its house)
15:22 Mia   scratch→loaf  behind the cushion  (level .11, why: rests)
15:40 Mia   loaf→wander  rug  (level .17, why: curious)
15:43 Mia   wander→loaf  behind the cushion  (level .03, why: rests)
15:53 Mia   loaf→loaf  behind the cushion  (level .01, why: rests)
16:31 Mia   loaf→sleep  behind the cushion  (level .67, why: tired)
17:47 Mia   sleep→waking routine  behind the cushion  (level .88, why: after sleep)
17:47 Mia   waking routine→window watch  living sill  (level .49, why: birds out)
18:00 owner open
18:10 Mia   window watch→scratch  sofa arm  (level .57, why: marks its house)
18:11 Mia   scratch→loaf  behind the cushion  (level .14, why: rests)
18:15 owner feed
18:15 Mia   loaf→eat  bowl  (level .59, why: fed)
18:15 Mia   eat→loaf  behind the cushion  (level .09, why: resume)
18:41 Mia   loaf→groom  behind the cushion  (level .83, why: wants a wash)
18:43 Mia   groom→loaf  behind the cushion  (level .01, why: rests)
19:00 owner close
19:15 Mia   loaf→sleep  behind the cushion  (level .65, why: tired)
21:26 Mia   sleep→waking routine  behind the cushion  (level .94, why: after sleep)
21:26 Mia   waking routine→loaf  behind the cushion  (level .00, why: rests)
21:30 owner open
21:38 Mia   loaf→wander  rug  (level .61, why: curious)
21:39 Mia   wander→groom  rug  (level .51, why: wants a wash)
21:40 Mia   groom→loaf  behind the cushion  (level .01, why: rests)
21:45 owner close
21:53 Mia   loaf→scratch  tree post  (level .58, why: marks its house)
21:53 Mia   scratch→loaf  behind the cushion  (level .00, why: rests)
22:13 Mia   loaf→wander  rug  (level .60, why: curious)
22:16 Mia   wander→loaf  behind the cushion  (level .02, why: rests)
22:36 Mia   loaf→groom  behind the cushion  (level .46, why: wants a wash)
22:38 Mia   groom→loaf  behind the cushion  (level .02, why: rests)
22:52 Mia   loaf→sleep  behind the cushion  (level .72, why: bed hours)

Mia   |SSSSSSSLLSSSLLSLLSLSSLLS|

Mia   sleep 15.2h, loaf 6.5h, window watch 1.8h, groom 0.3h, wander 0.2h, eat 0.0h, waking routine 0.0h, scratch 0.0h
```

### A kitten (Bean, stage 2, playful DNA)

```
00:00 Bean  sleep  bed  (level .60, why: bed hours)
01:30 Bean  sleep→waking routine  bed  (level .94, why: after sleep)
01:30 Bean  waking routine→wander  living rug  (level .46, why: curious)
01:32 Bean  wander→loaf  bed  (level .01, why: rests)
01:54 Bean  loaf→sleep  cushion  (level .85, why: bed hours)
03:24 Bean  sleep→waking routine  cushion  (level .98, why: after sleep)
03:24 Bean  waking routine→groom  cushion  (level .55, why: wants a wash)
03:25 Bean  groom→scratch  sofa arm  (level .55, why: marks its house)
03:26 Bean  scratch→window watch  living sill  (level .62, why: curious)
03:36 Bean  window watch→sleep  bed  (level .91, why: bed hours)
05:06 Bean  sleep→waking routine  bed  (level .99, why: after sleep)
05:06 Bean  waking routine→wander  kitchen floor  (level .26, why: curious)
05:08 Bean  wander→loaf  bed  (level .01, why: rests)
05:31 Bean  loaf→sleep  sofa end  (level .88, why: bed hours)
06:26 Bean  sleep→waking routine  sofa end  (level .96, why: after sleep)
06:26 Bean  waking routine→wander  rug  (level .49, why: curious)
06:28 Bean  wander→groom  tree post  (level .53, why: wants a wash)
06:28 Bean  groom→window watch  living sill  (level .36, why: birds out)
06:46 Bean  window watch→wander  rug  (level .00, why: curious)
06:50 Bean  wander→scratch  tree post  (level .59, why: marks its house)
06:50 Bean  scratch→parkour visit  bookcase  (level .24, why: surveys its house)
06:56 Bean  parkour visit→loaf  tree top  (level .18, why: rests)
07:10 Bean  loaf→wander  kitchen floor  (level .18, why: curious)
07:12 Bean  wander→sleep  cushion  (level .71, why: tired)
07:36 Bean  sleep→waking routine  cushion  (level .82, why: after sleep)
07:36 Bean  waking routine→groom  cushion  (level .48, why: wants a wash)
07:38 Bean  groom→loaf  cushion  (level .02, why: rests)
07:45 owner open
07:45 Bean  loaf→wander  kitchen floor  (level .27, why: curious)
07:47 Bean  wander→sleep  cushion  (level .78, why: tired)
08:00 owner feed
08:00 Bean  sleep→eat  bowl  (level .11, why: fed)
08:00 Bean  eat→sleep  cushion  (level .83, why: resume)
08:15 owner close
08:22 Bean  sleep→waking routine  cushion  (level .89, why: after sleep)
08:22 Bean  waking routine→groom  cushion  (level .54, why: wants a wash)
08:23 Bean  groom→wander  rug  (level .29, why: curious)
08:26 Bean  wander→loaf  bed  (level .03, why: rests)
08:37 Bean  loaf→sleep  bed  (level .83, why: tired)
09:06 Bean  sleep→waking routine  bed  (level .91, why: after sleep)
09:06 Bean  waking routine→wander  living rug  (level .34, why: curious)
09:08 Bean  wander→parkour visit  bookcase  (level .38, why: surveys its house)
09:15 Bean  parkour visit→loaf  bookcase  (level .06, why: rests)
09:39 Bean  loaf→groom  bookcase  (level .49, why: wants a wash)
09:42 Bean  groom→window watch  sill  (level .39, why: curious)
09:58 Bean  window watch→sleep  sill  (level .67, why: tired)
10:21 Bean  sleep→waking routine  sill  (level .75, why: after sleep)
10:21 Bean  waking routine→loaf  bed  (level .02, why: rests)
10:35 Bean  loaf→sleep  bed  (level .71, why: nap window)
11:09 Bean  sleep→waking routine  bed  (level .86, why: after sleep)
11:09 Bean  waking routine→wander  kitchen floor  (level .31, why: curious)
11:12 Bean  wander→groom  rug  (level .45, why: wants a wash)
11:14 Bean  groom→loaf  bed  (level .04, why: rests)
11:30 Bean  loaf→wander  living rug  (level .35, why: curious)
11:31 Bean  wander→sleep  sofa end  (level .77, why: nap window)
11:57 Bean  sleep→waking routine  sofa end  (level .86, why: after sleep)
11:57 Bean  waking routine→wander  rug  (level .32, why: curious)
12:00 Bean  wander→parkour visit  side table  (level .54, why: surveys its house)
12:10 Bean  parkour visit→sleep  cushion  (level .76, why: tired)
12:30 owner open
12:40 Bean  sleep→waking routine  cushion  (level .87, why: after sleep)
12:40 Bean  waking routine→wander  kitchen floor  (level .13, why: curious)
12:41 Bean  wander→loaf  box  (level .02, why: rests)
12:42 owner close
13:03 Bean  loaf→groom  box  (level .53, why: wants a wash)
13:05 Bean  groom→window watch  sill  (level .36, why: curious)
13:16 Bean  window watch→sleep  bed  (level .72, why: tired)
13:47 Bean  sleep→waking routine  bed  (level .86, why: after sleep)
13:47 Bean  waking routine→wander  living rug  (level .10, why: curious)
13:50 Bean  wander→loaf  rug  (level .02, why: rests)
13:58 Bean  loaf→sleep  sofa end  (level .81, why: nap window)
14:36 Bean  sleep→waking routine  sofa end  (level .92, why: after sleep)
14:36 Bean  waking routine→wander  rug  (level .25, why: curious)
14:39 Bean  wander→loaf  bed  (level .02, why: rests)
14:53 Bean  loaf→groom  bed  (level .50, why: wants a wash)
14:54 Bean  groom→parkour visit  side table  (level .48, why: surveys its house)
14:59 Bean  parkour visit→wander  rug  (level .15, why: curious)
15:00 Bean  wander→sleep  bed  (level .78, why: nap window)
15:22 Bean  sleep→waking routine  bed  (level .86, why: after sleep)
15:22 Bean  waking routine→loaf  bed  (level .01, why: rests)
15:42 Bean  loaf→wander  living rug  (level .38, why: curious)
15:43 Bean  wander→window watch  sill  (level .31, why: curious)
15:50 Bean  window watch→sleep  sill  (level .76, why: nap window)
16:26 Bean  sleep→waking routine  sill  (level .85, why: after sleep)
16:26 Bean  waking routine→wander  kitchen floor  (level .15, why: curious)
16:29 Bean  wander→groom  floor  (level .50, why: wants a wash)
16:29 Bean  groom→loaf  bed  (level .03, why: rests)
16:56 Bean  loaf→sleep  bed  (level .74, why: tired)
17:25 Bean  sleep→waking routine  bed  (level .86, why: after sleep)
17:25 Bean  waking routine→wander  living rug  (level .43, why: curious)
17:27 Bean  wander→window watch  living sill  (level .30, why: curious)
17:32 Bean  window watch→loaf  bed  (level .05, why: rests)
17:49 Bean  loaf→wander  kitchen floor  (level .27, why: curious)
17:51 Bean  wander→scratch  tree post  (level .57, why: marks its house)
17:51 Bean  scratch→sleep  bed  (level .75, why: tired)
18:00 owner open
18:15 owner feed
18:15 Bean  sleep→eat  bowl  (level .53, why: fed)
18:15 Bean  eat→sleep  bed  (level .85, why: resume)
18:29 Bean  sleep→waking routine  bed  (level .89, why: after sleep)
18:29 Bean  waking routine→groom  bed  (level .66, why: wants a wash)
18:30 Bean  groom→wander  kitchen floor  (level .28, why: curious)
18:33 Bean  wander→parkour visit  tree top  (level .29, why: surveys its house)
18:40 Bean  parkour visit→loaf  tree top  (level .07, why: rests)
19:00 owner close
19:04 Bean  loaf→wander  kitchen floor  (level .35, why: curious)
19:06 Bean  wander→sleep  cushion  (level .71, why: tired)
19:29 Bean  sleep→waking routine  cushion  (level .82, why: after sleep)
19:29 Bean  waking routine→wander  rug  (level .28, why: curious)
19:31 Bean  wander→loaf  kitchen floor  (level .01, why: rests)
19:40 Bean  loaf→groom  kitchen floor  (level .47, why: wants a wash)
19:43 Bean  groom→sleep  cushion  (level .77, why: tired)
20:10 Bean  sleep→waking routine  cushion  (level .87, why: after sleep)
20:10 Bean  waking routine→wander  rug  (level .39, why: curious)
20:11 Bean  wander→window watch  living sill  (level .31, why: curious)
20:30 Bean  window watch→sleep  sofa  (level .77, why: tired)
21:01 Bean  sleep→waking routine  sofa  (level .88, why: after sleep)
21:01 Bean  waking routine→parkour visit  tree top  (level .45, why: surveys its house)
21:04 Bean  parkour visit→wander  kitchen floor  (level .06, why: curious)
21:07 Bean  wander→groom  floor  (level .45, why: wants a wash)
21:09 Bean  groom→loaf  bed  (level .07, why: rests)
21:29 Bean  loaf→sleep  bed  (level .75, why: tired)
21:30 owner open
21:45 owner close
22:01 Bean  sleep→waking routine  bed  (level .88, why: after sleep)
22:01 Bean  waking routine→wander  living rug  (level .35, why: curious)
22:04 Bean  wander→loaf  cushion  (level .02, why: rests)
22:21 Bean  loaf→wander  rug  (level .39, why: curious)
22:23 Bean  wander→sleep  sofa end  (level .79, why: tired)
23:00 Bean  sleep→waking routine  sofa end  (level .90, why: after sleep)
23:00 Bean  waking routine→wander  rug  (level .33, why: curious)
23:03 Bean  wander→loaf  bed  (level .02, why: rests)
23:24 Bean  loaf→sleep  bed  (level .81, why: bed hours)

Bean  |SSSSSSSSSLSSSSSSSSSSSSSS|

Bean  sleep 15.1h, loaf 5.5h, window watch 1.4h, wander 1.0h, parkour visit 0.6h, groom 0.3h, eat 0.0h, scratch 0.0h
```

### A weak, hungry cat (Sol: last fed 30 h before the day, fed at 19:00)

```
00:00 Sol   loaf  behind the bed  (level .50, why: rests)
00:35 Sol   loaf→sleep  bed  (level .46, why: bed hours)
03:14 Sol   sleep→waking routine  bed  (level .50, why: after sleep)
03:14 Sol   waking routine→groom  bed  (level .55, why: wants a wash)
03:15 Sol   groom→loaf  bed  (level .01, why: rests)
03:51 Sol   loaf→sleep  bed  (level .45, why: bed hours)
06:34 Sol   sleep→waking routine  bed  (level .50, why: after sleep)
06:35 Sol   waking routine→groom  bed  (level .50, why: wants a wash)
06:37 Sol   groom→loaf  bed  (level .03, why: rests)
06:54 Sol   loaf→sleep  bed  (level .47, why: bed hours)
07:05 Sol   sleep→waking routine  bed  (level .50, why: after sleep)
07:06 Sol   waking routine→loaf  behind the bed  (level .00, why: rests)
07:30 Sol   loaf→sleep  bed  (level .47, why: tired)
07:45 owner open
08:15 owner close
08:28 Sol   sleep→waking routine  bed  (level .50, why: after sleep)
08:28 Sol   waking routine→loaf  behind the bed  (level .00, why: rests)
08:57 Sol   loaf→groom  behind the bed  (level .58, why: wants a wash)
09:00 Sol   groom→sleep  bed  (level .46, why: tired)
10:25 Sol   sleep→waking routine  bed  (level .50, why: after sleep)
10:25 Sol   waking routine→loaf  behind the bed  (level .00, why: rests)
10:37 Sol   loaf→loaf  behind the bed  (level .00, why: rests)
11:08 Sol   loaf→sleep  bed  (level .45, why: tired)
12:09 Sol   sleep→waking routine  bed  (level .50, why: after sleep)
12:10 Sol   waking routine→loaf  behind the bed  (level .00, why: rests)
12:30 owner open
12:30 Sol   loaf→beg  bowl  (level .83, why: hungry, owner here)
12:31 Sol   beg→loaf  behind the bed  (level .01, why: resume)
12:37 Sol   loaf→beg  bowl  (level .86, why: hungry, owner here)
12:38 Sol   beg→sleep  bed  (level .46, why: tired)
12:42 owner close
13:48 Sol   sleep→waking routine  bed  (level .50, why: after sleep)
13:48 Sol   waking routine→groom  bed  (level .59, why: wants a wash)
13:51 Sol   groom→loaf  behind the bed  (level .03, why: rests)
14:05 Sol   loaf→sleep  bed  (level .48, why: tired)
14:57 Sol   sleep→waking routine  bed  (level .50, why: after sleep)
14:57 Sol   waking routine→loaf  behind the bed  (level .00, why: rests)
15:10 Sol   loaf→sleep  bed  (level .48, why: tired)
15:47 Sol   sleep→waking routine  bed  (level .50, why: after sleep)
15:47 Sol   waking routine→loaf  bed  (level .00, why: rests)
16:22 Sol   loaf→sleep  bed  (level .46, why: nap window)
17:15 Sol   sleep→waking routine  bed  (level .50, why: after sleep)
17:15 Sol   waking routine→groom  bed  (level .56, why: wants a wash)
17:16 Sol   groom→loaf  behind the bed  (level .01, why: rests)
17:44 Sol   loaf→loaf  behind the bed  (level .00, why: rests)
18:00 owner open
18:00 Sol   loaf→beg  bowl  (level .93, why: hungry, owner here)
18:00 Sol   beg→loaf  behind the bed  (level .01, why: resume)
18:15 Sol   loaf→beg  bowl  (level .95, why: hungry, owner here)
18:17 Sol   beg→loaf  bed  (level .02, why: rests)
18:54 Sol   loaf→beg  bowl  (level .98, why: hungry, owner here)
18:56 Sol   beg→sleep  bed  (level .38, why: tired)
19:00 owner close
19:00 owner feed
19:00 Sol   sleep→eat  bowl  (level .65, why: fed)
19:00 Sol   eat→sleep  bed  (level .43, why: resume)
20:12 Sol   sleep→waking routine  bed  (level .88, why: after sleep)
20:12 Sol   waking routine→scratch  sofa arm  (level .99, why: marks its house)
20:12 Sol   scratch→groom  sofa arm  (level .71, why: wants a wash)
20:13 Sol   groom→wander  kitchen floor  (level 1.00, why: curious)
20:17 Sol   wander→loaf  behind the bed  (level .03, why: rests)
20:34 Sol   loaf→wander  kitchen floor  (level .57, why: curious)
20:37 Sol   wander→parkour visit  side table  (level .49, why: surveys its house)
20:41 Sol   parkour visit→loaf  bed  (level .05, why: rests)
21:15 Sol   loaf→groom  bed  (level .56, why: wants a wash)
21:17 Sol   groom→loaf  bed  (level .02, why: rests)
21:30 owner open
21:45 owner close
21:51 Sol   loaf→wander  living rug  (level .63, why: curious)
21:53 Sol   wander→sleep  bed  (level .65, why: tired)
23:50 Sol   sleep→waking routine  bed  (level .97, why: after sleep)
23:50 Sol   waking routine→loaf  behind the bed  (level .00, why: rests)

Sol   |LSSLSSSSSSLSSSSSSLLSLLSS|

Sol   sleep 15.7h, loaf 7.7h, groom 0.2h, wander 0.2h, beg 0.1h, parkour visit 0.1h, waking routine 0.1h, eat 0.0h
```

### Two cats in one house (Ted playful, Lily shy)

```
00:00 Ted   sleep  sofa  (level .60, why: bed hours)
00:00 Lily  sleep  box  (level .60, why: bed hours)
03:28 Lily  sleep→waking routine  box  (level .98, why: after sleep)
03:28 Lily  waking routine→groom  box  (level .49, why: wants a wash)
03:29 Lily  groom→loaf  box  (level .01, why: rests)
03:47 Lily  loaf→sleep  box  (level .92, why: bed hours)
03:58 Ted   sleep→waking routine  sofa  (level 1.00, why: after sleep)
03:58 Ted   waking routine→groom  sofa  (level .51, why: wants a wash)
04:00 Ted   groom→window watch  living sill  (level .66, why: curious)
04:14 Ted   window watch→sleep  cushion  (level .94, why: bed hours)
06:51 Ted   sleep→waking routine  cushion  (level 1.00, why: after sleep)
06:51 Ted   waking routine→wander  rug  (level .37, why: curious)
06:53 Ted   wander→parkour visit  tree top  (level .66, why: surveys its house)
07:03 Ted   parkour visit→loaf  behind the bed  (level .06, why: rests)
07:19 Ted   loaf→wander  living rug  (level .27, why: curious)
07:21 Ted   wander→groom  floor  (level .51, why: wants a wash)
07:23 Ted   groom→loaf  cushion  (level .03, why: rests)
07:31 Ted   loaf→loaf  sofa end  (level .01, why: rests)
07:42 Lily  sleep→waking routine  box  (level 1.00, why: after sleep)
07:42 Lily  waking routine→groom  box  (level .49, why: wants a wash)
07:44 Lily  groom→window watch  kitchen sill  (level .74, why: curious)
07:45 owner open
07:57 Ted   loaf→window watch  sill  (level .51, why: birds out)
08:00 owner feed
08:00 Ted   window watch→eat  bowl  (level .29, why: fed)
08:00 Lily  window watch→eat  bowl  (level .16, why: fed)
08:00 Ted   eat→wander  kitchen floor  (level .28, why: curious)
08:00 Lily  eat→loaf  behind the bed  (level .10, why: rests)
08:02 Ted   wander→groom  floor  (level .65, why: wants a wash)
08:03 Ted   groom→loaf  box  (level .04, why: rests)
08:15 owner close
08:28 Lily  loaf→groom  behind the bed  (level .66, why: wants a wash)
08:29 Ted   loaf→wander  rug  (level .45, why: curious)
08:29 Lily  groom→scratch  sofa arm  (level .68, why: marks its house)
08:30 Lily  scratch→wander  kitchen floor  (level .24, why: curious)
08:30 Ted   wander→window watch  living sill  (level .31, why: curious)
08:32 Lily  wander→loaf  bed  (level .03, why: rests)
08:46 Ted   window watch→loaf  sofa  (level .12, why: rests)
09:04 Lily  loaf→wander  living rug  (level .36, why: curious)
09:06 Ted   loaf→parkour visit  side table  (level .61, why: surveys its house)
09:08 Lily  wander→loaf  bed  (level .03, why: rests)
09:15 Ted   parkour visit→groom  side table  (level .46, why: wants a wash)
09:17 Ted   groom→wander  rug  (level .09, why: curious)
09:18 Ted   wander→loaf  sofa end  (level .10, why: rests)
09:31 Lily  loaf→sleep  behind the cushion  (level .69, why: tired)
09:31 Ted   loaf→sleep  sofa  (level .65, why: tired)
11:06 Ted   sleep→waking routine  sofa  (level .95, why: after sleep)
11:06 Ted   waking routine→window watch  living sill  (level .41, why: curious)
11:13 Ted   window watch→loaf  sofa  (level .04, why: rests)
11:31 Ted   loaf→groom  sofa  (level .48, why: wants a wash)
11:31 Ted   groom→wander  rug  (level .28, why: curious)
11:33 Ted   wander→parkour visit  tree top  (level .38, why: surveys its house)
11:38 Ted   parkour visit→loaf  tree mid  (level .05, why: rests)
11:53 Lily  sleep→waking routine  behind the cushion  (level .95, why: after sleep)
11:53 Lily  waking routine→groom  behind the cushion  (level .54, why: wants a wash)
11:54 Lily  groom→window watch  living sill  (level .49, why: curious)
12:04 Ted   loaf→window watch  sill  (level .38, why: curious)
12:14 Ted   window watch→wander  kitchen floor  (level .02, why: curious)
12:15 Lily  window watch→scratch  tree post  (level .59, why: marks its house)
12:16 Lily  scratch→loaf  box  (level .13, why: rests)
12:16 Ted   wander→loaf  sofa  (level .08, why: rests)
12:23 Ted   loaf→groom  sofa  (level .47, why: wants a wash)
12:26 Ted   groom→loaf  cushion  (level .05, why: rests)
12:30 owner open
12:42 owner close
12:46 Ted   loaf→window watch  living sill  (level .36, why: curious)
12:52 Lily  loaf→wander  rug  (level .28, why: curious)
12:55 Lily  wander→groom  sofa arm  (level .45, why: wants a wash)
12:58 Lily  groom→loaf  behind the bed  (level .04, why: rests)
13:05 Ted   window watch→loaf  living sill  (level .13, why: rests)
13:22 Ted   loaf→scratch  tree post  (level .57, why: marks its house)
13:22 Ted   scratch→wander  kitchen floor  (level .22, why: curious)
13:24 Ted   wander→loaf  bed  (level .03, why: rests)
13:39 Lily  loaf→sleep  box  (level .66, why: nap window)
13:51 Ted   loaf→groom  bed  (level .49, why: wants a wash)
13:54 Ted   groom→window watch  sill  (level .46, why: curious)
14:02 Ted   window watch→sleep  bed  (level .61, why: tired)
14:41 Lily  sleep→waking routine  box  (level .85, why: after sleep)
14:42 Lily  waking routine→window watch  sill  (level .49, why: curious)
14:51 Lily  window watch→scratch  tree post  (level .56, why: marks its house)
14:51 Lily  scratch→loaf  behind the cushion  (level .06, why: rests)
15:09 Lily  loaf→groom  behind the cushion  (level .52, why: wants a wash)
15:10 Lily  groom→loaf  behind the cushion  (level .02, why: rests)
15:22 Ted   sleep→waking routine  bed  (level .93, why: after sleep)
15:22 Ted   waking routine→wander  living rug  (level .25, why: curious)
15:24 Ted   wander→parkour visit  tree top  (level .49, why: surveys its house)
15:33 Ted   parkour visit→loaf  sill  (level .07, why: rests)
15:40 Ted   loaf→wander  living rug  (level .15, why: curious)
15:43 Ted   wander→loaf  living sill  (level .04, why: rests)
15:46 Lily  loaf→sleep  behind the cushion  (level .69, why: tired)
15:57 Ted   loaf→loaf  cushion  (level .01, why: rests)
16:18 Ted   loaf→groom  cushion  (level .55, why: wants a wash)
16:19 Ted   groom→wander  rug  (level .47, why: curious)
16:23 Ted   wander→loaf  bed  (level .03, why: rests)
16:34 Ted   loaf→window watch  sill  (level .35, why: curious)
16:52 Ted   window watch→loaf  sill  (level .13, why: rests)
17:04 Ted   loaf→wander  living rug  (level .16, why: curious)
17:06 Ted   wander→loaf  cushion  (level .04, why: rests)
17:33 Ted   loaf→groom  cushion  (level .54, why: wants a wash)
17:34 Ted   groom→sleep  sofa  (level .66, why: nap window)
18:00 owner open
18:06 Lily  sleep→waking routine  behind the cushion  (level .95, why: after sleep)
18:06 Lily  waking routine→wander  rug  (level .54, why: curious)
18:09 Lily  wander→groom  sofa arm  (level .46, why: wants a wash)
18:10 Lily  groom→loaf  behind the bed  (level .02, why: rests)
18:15 owner feed
18:15 Ted   sleep→eat  bowl  (level .11, why: fed)
18:15 Lily  loaf→eat  bowl  (level .16, why: fed)
18:15 Lily  eat→loaf  behind the bed  (level .01, why: resume)
18:15 Ted   eat→sleep  sofa  (level .85, why: resume)
18:26 Lily  loaf→wander  kitchen floor  (level .41, why: curious)
18:27 Lily  wander→loaf  box  (level .01, why: rests)
18:38 Ted   sleep→waking routine  sofa  (level .91, why: after sleep)
18:39 Ted   waking routine→scratch  sofa arm  (level .64, why: marks its house)
18:39 Ted   scratch→window watch  living sill  (level .51, why: curious)
18:54 Ted   window watch→wander  kitchen floor  (level .01, why: curious)
18:57 Ted   wander→groom  floor  (level .60, why: wants a wash)
18:58 Ted   groom→parkour visit  side table  (level .35, why: surveys its house)
19:00 owner close
19:00 Lily  loaf→groom  box  (level .70, why: wants a wash)
19:01 Ted   parkour visit→loaf  cushion  (level .13, why: rests)
19:02 Lily  groom→scratch  sofa arm  (level .62, why: marks its house)
19:02 Lily  scratch→window watch  living sill  (level .49, why: birds out)
19:19 Ted   loaf→wander  rug  (level .23, why: curious)
19:22 Ted   wander→loaf  sofa  (level .03, why: rests)
19:26 Lily  window watch→parkour visit  side table  (level .33, why: surveys its house)
19:31 Lily  parkour visit→loaf  behind the cushion  (level .21, why: rests)
19:37 Ted   loaf→groom  sofa  (level .46, why: wants a wash)
19:39 Ted   groom→wander  rug  (level .32, why: curious)
19:40 Ted   wander→loaf  sofa  (level .02, why: rests)
19:59 Ted   loaf→wander  rug  (level .43, why: curious)
20:02 Ted   wander→loaf  sofa end  (level .03, why: rests)
20:03 Lily  loaf→sleep  behind the cushion  (level .62, why: tired)
20:18 Ted   loaf→wander  rug  (level .38, why: curious)
20:19 Ted   wander→loaf  bed  (level .01, why: rests)
20:35 Ted   loaf→wander  living rug  (level .43, why: curious)
20:36 Ted   wander→scratch  tree post  (level .56, why: marks its house)
20:37 Ted   scratch→loaf  cushion  (level .02, why: rests)
21:03 Ted   loaf→sleep  cushion  (level .64, why: nap window)
21:30 owner open
21:34 Lily  sleep→waking routine  behind the cushion  (level .89, why: after sleep)
21:34 Lily  waking routine→groom  behind the cushion  (level .48, why: wants a wash)
21:36 Lily  groom→loaf  behind the cushion  (level .01, why: rests)
21:45 owner close
21:46 Lily  loaf→loaf  behind the cushion  (level .00, why: rests)
22:03 Lily  loaf→wander  rug  (level .51, why: curious)
22:05 Ted   sleep→waking routine  cushion  (level .90, why: after sleep)
22:05 Ted   waking routine→groom  cushion  (level .52, why: wants a wash)
22:06 Ted   groom→wander  kitchen floor  (level .60, why: curious)
22:07 Lily  wander→loaf  behind the cushion  (level .03, why: rests)
22:09 Ted   wander→parkour visit  tree top  (level .43, why: surveys its house)
22:16 Ted   parkour visit→loaf  behind the bed  (level .07, why: rests)
22:21 Lily  loaf→loaf  behind the cushion  (level .00, why: rests)
22:31 Lily  loaf→loaf  behind the cushion  (level .00, why: rests)
22:34 Ted   loaf→wander  kitchen floor  (level .34, why: curious)
22:35 Ted   wander→loaf  cushion  (level .02, why: rests)
22:55 Ted   loaf→wander  rug  (level .42, why: curious)
22:57 Ted   wander→loaf  bed  (level .02, why: rests)
23:03 Lily  loaf→sleep  behind the cushion  (level .67, why: bed hours)
23:14 Ted   loaf→sleep  cushion  (level .76, why: bed hours)

Ted   |SSSSSSSLLSSLLLSSLLSLLSLS|
Lily  |SSSSSSSSLLSSLLSLSSLLSSLS|

Ted   sleep 12.4h, loaf 8.0h, window watch 1.8h, wander 0.7h, parkour visit 0.7h, groom 0.3h, eat 0.0h, waking routine 0.0h, scratch 0.0h
Lily  sleep 15.6h, loaf 6.6h, window watch 1.2h, wander 0.3h, groom 0.2h, parkour visit 0.1h, eat 0.0h, scratch 0.0h, waking routine 0.0h
```
