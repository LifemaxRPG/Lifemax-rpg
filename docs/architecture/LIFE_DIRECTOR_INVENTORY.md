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
| ? | 3A | plays the section's guide natively when the screen draws it; otherwise hands over `tour` |
| Banner and its buttons, last message | phase 1 | `frame().banner`, `notice`; buttons hand over |

## Today (phases 1–2; sheets in 4A)

Strip (`day` command), dial and its handles (`dialDrop`, `dialApply`), centre, mix, up next, reminders, running order: native.
Session, timer, meal, Move and "Something came up" sheets, Done, Skip, Pin, Re-plan: page until 4A.

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

## Goals, Settings (3B), My time and set-up (4B), Lab

Page: their tabs hand over until their merge. Lab stays the page's (a developer harness).
