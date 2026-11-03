# Pet album and scrapbook (phase S1, page side)

The owner's wish: the cat is photographed automatically as it grows up, whenever a memorable moment happens, and when the cat
passes away the app shows a scrapbook of those moments.

This document covers **phase S1, the page's half**: the moment catalogue, the detector, the data in the save and the page API. The
page owns the data. **Nothing here is advertised to native**: no capability, plugin or bridge command exists yet. A later phase makes
native stage the portraits and draw the scrapbook from the API below.

Code: `lifemax_modular_v73/src/runtime/segments/50-pet-moments.js` (catalogue) and `50-pet-album.js` (detector, data, API).
Test: `lifemax_modular_v73/tests/pet-album.test.js`.

## Tiers

| Tier | Meaning | Album entry? |
|------|---------|--------------|
| high | a memorable moment worth a photograph | yes, once |
| mid  | a notable event (a bond level-up shown by the brain; the bond level itself is the high moment `bond`) | never |
| low  | everyday care and play: meals, petting beats, toys, startles, greetings | never |

`PET_MOMENT_EVENTS` lists the brain event kinds and their tier; an unlisted kind is low. Only the three high events (`gift`, `blink`,
`respect`) make the detector look again at once; the rest return `{recorded:false, why:'tier'}`.

## Moment catalogue

`reach` is read from the cat; nothing is guessed. A ladder kind can happen once per rung `n`; the entry id is
`<catId>:<kind>:<n>`. `catId` is `c` plus the cat's `born` in base 36, so it survives renames, reloads and the second cat taking the
first cat's place.

| kind | n | what the page detects | pose | who appears | caption (template) |
|------|---|-----------------------|------|-------------|--------------------|
| adopt | 1 | the cat is new (adopted within 6 h of the album appearing) | sit | self | {name} came home today. Day one together. |
| grow | 1..5, 6 | `petGrowIdx` changes (a kitten stage every 5 days), 6 = grown after `PET_GROW_DAYS` (30) | play, play, stretch, sit, sit, portrait | self | {name} is a {stage} now, {days} days in. / ...is all grown up |
| bond | 1..5 | `petLevel` reaches Wary, Curious, Friendly, Fond, Devoted (`PET_BOND_AT`) | sit, sit, play, blink, loaf | self | Your bond with {name} reached "{bond}". |
| gift | 1 | first present (`p.gifts` >= 1) | gift | self | {name} brought you a present for the first time. |
| blink | 1 | core memory `first_blink` (a slow blink returned) | blink | self | You and {name} shared a slow blink... |
| respect | 1 | core memory `first_respect` | loaf | self | You stopped when {name} asked you to. |
| glass | 1 | core memory `first_glass` | gaze | self | {name} looked out through the glass at you. |
| comfort | 1 | core memory `first_co` | sit | self | {name} came to sit near you, just to be close. |
| trophy | 1 | core memory `first_trophy` | pounce | self | {name} caught something and carried it to the glass. |
| zoomies | 1 | core memory `first_zoomies` | zoom | self | {name} had the zoomies for the first time. |
| belly | 1 | core memory `first_belly` | belly | self | {name} rolled over and showed you a belly. |
| vigil | 1 | core memory `first_vigil` | gaze | self | {name} was already waiting at the glass. |
| fav_spot | 1 | `likes.favSpot` set (25-pet-likes.js) | loaf | self | {name} has a favourite place: {spot}. |
| fav_toy | 1 | `likes.favToy` set | play | self | {name} has a favourite toy: the {toy}. |
| quirk | 1..3 | `brain.qk.acq` grows (a habit acquired) | sit | self | {name} picked up a new habit: {quirk}. |
| days | 30, 100, 200 | days together | portrait | self | {days} days together with {name}. |
| birthday | 1, 2, ... | every 365 days | portrait | self | {name} turned {years}. |
| recovery | 1 | `petFeed` succeeded while hunger stage >= 3 (event, not a state) | eat | self | {name} was very hungry, and your meal came just in time. |
| pair_nap | 1 | `petPair.n.nap` >= 1 | curl_with_other | both | {name} and {other} napped side by side for the first time. |
| pair_level | 1..4 | `PET_PAIR_LEVELS` index reaches Wary, Getting along, Friends, Inseparable | sit_with_other, sit_with_other, groom_other, curl_with_other | both | {name} and {other} are now {pair}. |
| rank | 1 | `petPair.rk.settled` | sit_with_other | both | {name} and {other} have settled who is in charge. |

Considered and dropped: the *first slow blink the cat started* (the page cannot tell it from a returned one) and a first toy bought
(no per-cat event; the favourite toy covers it). The pair kinds go into both cats' albums (each has its own cap and its own id).
A friendship level is recorded once per cat for the cat's lifetime, whichever cat it is paired with.

Poses (`PET_POSES`): sit, loaf, sleep, play, groom, stretch, blink, gaze, pounce, zoom, belly, eat, gift, portrait, sit_with_other,
groom_other, curl_with_other. A kind has one pose, or one per rung.

## Detector

`petAlbumScan(now)` reads every living cat and writes only `p.album`. It never uses `petRng`, `petMinuteRng` or `Math.random`, never
creates the brain or likes records, and persists with `writeSave()` only (no redraw, no XP or badge pass), so a meal, a plan or a
seeded sequence is exactly what it was. It runs after the page's own events, wrapped without changing their results:
`petFeed` (and a recovery check from the hunger stage before the meal), `petPetDone`, `petPlayDone`, `petCheck` (the 60 s timer
and every Cat view), `petBrainEvent`, `petAdopt`.

Rules, all constants in `PET_ALBUM`:

- **Idempotent.** `album.w[kind]` is the highest rung handled; a rung is recorded or let go once, however often the detector looks.
  The entry id also guards.
- **Weekly cap** `WEEK_CAP` (3) per cat per real week. A held-back moment waits (`album.seen`) in catalogue order and takes the next
  free place; after `DEFER_MS` (8 days) it is let go, never recorded late.
- **Debounce** `DEBOUNCE_MS` (30 min): the same kind twice in that window waits.
- **Ladders** skip lower rungs reached at once (only the highest new rung is photographed).
- **Older saves**: a cat that already lives in a save with no album gets a quiet baseline (everything reached so far is marked done,
  nothing is photographed for the past). A cat younger than 6 h starts with its adoption.
- **Size**: `MAX` (200) entries per cat; the least valuable go first by catalogue `weight` (never `adopt`). A damaged album in a
  backup is repaired entry by entry (unknown kinds dropped, strings bounded, poses and photo states cleaned).

## Data in the save

`state.pet.album` and `state.pet2.album` (they travel with the cat, so a backup or restore carries them; no schema bump, the field is
additive and rebuilt when missing):

```
album = {
  v: 1, id: "c<born base36>",
  entries: [{ id, kind, n, at (ms), day (days together), caption, pose,
              facts: { name, days, stage, stageIdx (-1 grown, 0..5), bond, bondLvl, coat, trait,
                       other?, otherCoat?, otherStage?, pair?, spot?, toy?, quirk?, years? },
              photo: null | 'pending' | 'taken', op? }],
  w: { kind: highest rung handled },  seen: { kind: ms first held back }
}
```

`state.petMemorial[]` entries gain `album`: `{v, id, entries (at most MEM_MAX = 60, same shape), sum: {spot, toy, quirks[], trait,
gifts}}`, copied by `petCheck` at the moment a cat passes (so it survives the cat object and `petAdoptNew`). Only the newest `MEM_KEEP`
(12) memorials keep their entries; older ones keep `sum` and `count`.

## Page API (no native capability yet)

Pure functions, plus one command that writes. They are also reachable as `PetAlbum.commands` in the Cat tab's strict command shape
(unknown or missing keys are refused with `{ok:false, message}`).

| function / command | answers |
|--------------------|---------|
| `petAlbumList(catIndex)` / `list {cat}` | the living cat's entries, oldest first (0 = first cat, 1 = second); `[]` when none |
| `petAlbumPending()` / `pending {}` | entries with `photo:'pending'` (living cats, then memorials): the entry plus `catId`, `cat` (-1 for a memorial), `memorial` (-1 for a living cat) and `look {coat, stage, trait, other: {coat, stage} \| null}` from the entry's own facts |
| `petAlbumPhotoTaken(id, op)` / `photoTaken {id, op}` | `{ok, changed, message}`: sets `photo:'taken'` and stores `op`. ok only when `writeSave()` stored it (else the entry goes back to pending); the same entry again, with the same op or another, answers ok, `changed:false`, "Already recorded." |
| `petScrapbookData(ref)` / `scrapbook {memorial}` or `{cat}` | `{title, name, coat, alive, days, passed (ms, 0 if alive), born, bond {level, name}, favSpot, favToy, quirks[], moments[], photosTaken}`; each moment: the entry plus `taken` and `look`. A bare number is a memorial index; `null` when nothing is there |

## Future projection shape (for the native phase)

Native should ask `pending`, stage a portrait for each (cat `look` at the stage, `pose`, the other cat when `look.other`), save the
picture, then send `photoTaken {id, op}` with a fresh `op` per attempt. A snapshot for the scrapbook screen is exactly
`petScrapbookData`. A capability string (suggested `pet.album.v1`) will gate both, advertised by the plugin and required by the
page before it sends anything, as the other Cat tab commands do.

## Storage plan

- **Metadata only in the save** (above). The save never holds pixels.
- **Picture files** live in the app's private storage, keyed by cat id and moment id: `files/pet-album/<catId>/<momentId>.webp`
  (the entry id is `<catId>:<kind>:<n>`; use `<kind>-<n>` for the file name).
- **A missing file degrades to a caption card**: the scrapbook draws the caption and a drawn pose placeholder for any moment whose
  file is gone, so a restored backup, a cleared cache or a failed write never breaks the page. `photo:'taken'` is a claim that the
  picture existed; native re-stages (and re-sends `photoTaken`) only for entries marked `pending`.
- **Cats that pass** keep their pictures (the folder stays); the memorial copy keeps the captions.

## Question for the owner: backups

Backups (the JSON file and the folder backup) carry the album **metadata** automatically. The **picture files** are in the app's
private storage and are not in those backups. Choose:

1. *Photos stay on this phone only* (simple, small backups; after a restore every caption card shows, and pictures come back only if
   the album marks them pending again, which native can do on request).
2. *Add the pictures to the folder backup* (larger backups, a restored phone shows the real scrapbook).

Until you choose, the plan is option 1 with a "re-take pictures" action that marks the missing ones pending.
