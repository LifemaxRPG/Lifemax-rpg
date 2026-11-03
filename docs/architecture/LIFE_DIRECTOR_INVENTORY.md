# Life Director: what the page shows and does, and where each part is drawn

The page (`src/features/life-director/logic.js`) stays the only writer. This lists, section by section, every fact the page shows
and every action it offers, and which merge makes it native (plans/LIFE_DIRECTOR_3_4.md). "Page" means the screen hands the tap
over: it closes and the page's own handler runs.

## Frame (phase 1)

| Fact or action | Native since | How |
|---|---|---|
| Status dot and line, clock, ? | phase 1 | `frame().header`, `clock`; ? hands over `tour` |
| Section tabs | 3A | `frame().tabs`; a covered section is the `section` command, any other hands over `tab` |
| A sideways flick between sections | 3A | the next or previous tab, as a tap on it; the body slides in, the frame stays (none with reduced motion) |
| ? | 3A, 4B | plays the section's guide natively when the screen draws it; from Goals, Settings or Quests (4B) the guide the page names in `help` (tourHelp's pick: Today's, or set-up's until the week is built), switching to its section first (`GuideHelp`); otherwise, or when that section arrives without it, hands over `tour` |
| Banner and its buttons, last message | phase 1 | `frame().banner`, `notice`; buttons hand over |

## Today (phases 1–2; sheets in 4A)

Strip (`day` command), dial and its handles (`dialDrop`, `dialApply`), centre, mix, up next, reminders, running order: native.
Session, timer, meal, Move and "Something came up" sheets, Done, Skip, Pin, Re-plan: page until 4A.

### Taking a session off a day (`director.remove.v1`, Native API 50)

Hold a session in the running order (about 350 ms, with a short buzz) and it lifts: it follows the finger as a card, a little
larger with a shadow, the list closes the gap, and a trash ("Drop to remove") slides up above the bars. Over it the trash grows,
turns red and ticks; letting go there removes the session, letting go elsewhere (or Back, or a second finger) springs it back
with no change. The list scrolls when the finger nears its top or bottom edge, not while it is over the trash. No confirm sheet:
the drag is the deliberate act and Undo is the way back. Reduced motion: no slide, scale or spring.

| Fact or action | Projection / command | Notes |
|---|---|---|
| Whether a session can be removed | `today.rows[].remove {ok, reason}` on session rows only, and only for a screen that says `director.remove.v1` and `director.sheets.v1` | planned and no timer running: ok; done, part done, off the plan, running timer: not, with the page's reason, shown as the "not now" buzz and a message |
| Remove | `today skip {id, op}` (the existing Skip) | the engine's SKIPPED trigger, miss consequence and re-plan, for that occurrence only; no XP; answers `changed: true` and `undo` (the page's words) |
| Undo | `today unskip {id, op}` | plan and ledger back exactly as before that skip, while nothing else has changed the plan (and within two minutes); the message bar with Undo shows for seven seconds |
| TalkBack | custom action "Remove from today" on each removable row and each removable dial segment; the trash is a live region while a session is held | same commands, no drag |

The same hold works on the dial: a session's arc segment lifts exactly like its row (same 350 ms hold, "lift" buzz, card, trash, `skip`
with an op id, Undo, and the same refusals read from that session's `remove` flag; no page change, the segment is matched to its row
by session id). The segment stays as a ghost while its card is up, and the card shrinks back into it on a put-back; the list does not
auto-scroll for a dial lift. Gesture arbitration on the dial (`presentation/drag/DialTouch`, pure): a finger that lands on a drag
handle drags the handle; a finger on a session segment the page answers for waits: released first it is a tap (the session sheet, as
before), still for 350 ms it lifts (or refuses), and moving past the touch slop, a second finger or a touch another part took before
then cancels the wait and the dial's plain touch (arc scrolling) carries on. Meals, appointments, bands and sessions without a
`remove` flag neither lift nor buzz. The segment under the finger is found by `DialGeometry.arcAt`, the tap's own hit test. The row
and the segment share one controller and one `DragModel` (`DragSource.ROW` / `DIAL`). TalkBack: each removable session's segment (a
48 dp node) offers "Remove from today" like its row. The gesture belongs to the list (initial pass) for rows, to the dial for
segments; a row scrolling out of view does not end a row lift. Code: `presentation/drag` (`DragRemoveController`, the gesture,
`RemoveOverlay`, `RemoveTarget`, `RemovableRow`, `UndoBar`, `DialTouch`, `DialAnchor`), `presentation/dial` (`DialLiftGesture`,
`DialGhost`) and the pure `domain/DragModel`. On the phone only: the feel of the 350 ms hold and the buzzes, the spring, auto-scroll
speed on a long day, that a hold on a small segment is easy to land and the tab swipe and list never steal it after the lift, and how
TalkBack speaks the action and the trash.

## Week (3A, `director.week.v1`)

| Fact or action | Projection | Notes |
|---|---|---|
| Sessions (and done), time planned, XP on the table | `week.stats` | the page's words |
| Hour ticks 00–24 | `week.ticks` | |
| A row per day: name, date, count, ✓done, minutes, TalkBack line | `week.days[]` | today outlined |
| Each day's bar | `days[].segs` (kind, state, left, width in %) | from `weekSegs`, the page's own bar |
| Tap a day | `gotoDay` command | Today on that day, page underneath too |
| Legend | `week.legend` | |
| Where your XP goes | `week.yield` (ARGB colour, done and plan shares) | absent with no XP |
| How the week fits, what could not fit | `week.fit` | the list is folded, as the page's details |
| Week's guide | 3A step 5: `guide` (steps with anchors week.rows, week.stats, week.yield; `auto` when not seen) | native coach marks; Next, Back, Skip, a tap outside the card; Android Back is Skip; finished or skipped is `tourSeen`; the page starts none of its own while a screen draws it (`guidedBy`) |
| Set-up's guide | 4B: `guide` key setup on My time (anchors time.steps, time.sleep, time.commitments, time.routines, time.goals, time.build; the sleep step mode mini, round, with the Bedtime and Wake up `tags`; last button "Let’s go") | the same coach marks; the tags drawn on `MiniSleepDial(tags = miniGuideTags(p, run))` |
| Week without a plan | page | `showing()` is 'first-run' |

## Quests (3A, `director.week.v1`)

| Fact or action | Projection | Notes |
|---|---|---|
| Quest Board card, or "not detected" | `quests.head` | `open` empty when not connected |
| Open Quest Board | hands over `openQuests` | the 3D table stays web (ADR 0003) |
| Each quest: title, due, status, tone, why it could not fit | `quests.rows[]` | |
| All-day reminders | `quests.passive[]` | |
| Empty list | `quests.empty` | |
| Milestones: summary, note, rows | `quests.milestones` | absent when none is unlocked |

## Goals (3B, `director.goals.v1`)

| Fact or action | Projection / command | Notes |
|---|---|---|
| How your time is shared | `goals.explain` | |
| Each goal: name, kind and level (paused) | `goals.cards[]` | paused cards dimmed, as the page's .paused |
| Priority | `do goalPriority {id, value}` | the page's select drawn as five words in a row |
| Target and scheduled share, sessions line | `cards[].share` (0..1 with the page's percentages) | from `goalShare`, with the engine's fallback |
| Trajectory warning | `cards[].warning` | |
| Clarify ("what this goal looks like") | `do clarify {id, arch}` | |
| Pause / Resume | `do goalPause {id, paused}` | the state wanted, so a repeat changes nothing |
| Remove | asks first in the page's words (`removeConfirm`), then `do goalRemove {id}` | |
| No goals yet | `goals.empty` | |
| Add a goal | hands over `addGoal` | the page's goal sheet until 4B |

## Settings (3B, `director.goals.v1`)

| Fact or action | Projection / command | Notes |
|---|---|---|
| How much to schedule | `do pref {key: intensityLevel}` | |
| The three daily limits and the grace period | `do prefRange {key, value}` | sliders with the page's label at every stop (31, 23, 15, 16), sent when the finger lets go |
| Rest days | `do restDay {day, on}` | the state wanted |
| Planning window, what a miss costs | `do pref` | the rest tokens in bold (spans) |
| Show the guide again | `do tourReset` | |
| Copy backup | `do exportBackup` answers the text; the phone's clipboard takes it | |
| Restore backup | a sheet with a field (its draft kept while the screen hides), then `do importBackup {text}` | a refused restore reopens the sheet with the draft; a backup that cannot be saved takes itself back |
| Reset Life Director | asks first in the page's words, then `do reset` | |
| Diagnostics, Simulation Lab switch | `settings.diagnostics`, `do labToggle {on}` | folded, as the page's details; the Lab section itself stays the page's |

Every `do` answers ok only once the page has saved; a refused write answers with the page's "Not saved" words, which the
screen shows, and the page's banner says so too.

## My time and set-up (4B), Lab

Page: their tabs hand over until their merge. Lab stays the page's (a developer harness).
