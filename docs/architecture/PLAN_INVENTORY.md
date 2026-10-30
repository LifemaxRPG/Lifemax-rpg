# Workout plan native slice inventory
Baseline: 5f20de5 (Native API 23). Page view `plan` (`src/views/plan.html`), drawn into `#planBody` by `renderPlan` (06-runtime).

Authoritative code: the programs and their days are `planDay` with `athletePlanDay` (06-runtime) and `scholarPlanDay` for the three class programs (14-runtime); the class data is `CLASS_CONTENT[...].plan` (00 to 03-runtime). Progress is `pcheck`, `dayDone`, `dayPct`, `dayCls`, `lastVal`, `lastLog` (06-runtime). The writers are `planToggle`, `planVal`, `planLog`, `startPlan`, `leavePlan` (06-runtime). The guided workout runner is `gwr*` (21 and 22-runtime), and the 3D gym is a layer over it (50-gym3d, which wraps `gwrOpen` and `gwrRender`). Do not recalculate any of it in Kotlin.

Nothing else re-wraps `renderPlan`, `planToggle`, `selectDay` or `selectWeek`. The only page code that renders into `#planBody` is `renderPlan`, the three class offers (14-runtime) and `gwrLauncher` (22-runtime), which `renderPlan` puts at the top.

## States

| State | When | What the page shows | Native phase 1 |
|---|---|---|---|
| No plan, Athlete or no class | `!state.plan` | the Athlete offer (inline in `renderPlan`): start date and "Sign up for this plan" | stays on the page (`blocked()`) |
| No plan, Scholar, Entrepreneur or Explorer | `!state.plan` and a class with an offer | `scholarOfferHtml`, `entrepreneurOfferHtml` or `explorerOfferHtml` (14-runtime) | stays on the page |
| Before the first day | `today < plan.from` | "Starts M/D/YYYY"; week 1 is marked current; future days are locked | drawn |
| Running | the plan is in its eight weeks | "Week n of 8" | drawn |
| Over | `today > start + 55 days` | "Program complete"; no week is current; the selection falls back to `plan.from` | drawn |

The selected day can be one of these:
- a training day up to today (sets can be ticked);
- a future day (the "Locked until the day arrives." note; checkboxes and fields disabled);
- a rest day (no items; tag "Week n · rest");
- done (✓ and 100%);
- missed (×).

Slots before `plan.from` in week 1 have no day: the web leaves them empty.

The guided workout can be in one of these states:
- none;
- saved (the launcher says "Workout in progress … Resume" on every day);
- open: `#guidedWorkout.open` plus `body.noscroll`, either the classic screen or the 3D gym (Settings, `localStorage.lma_gwr3d`).

Things that cover the plan on the page: the level-up overlay (`.luov`, 07-runtime `showLevelUp`), the sheets that `lmaTopOverlay` knows, and toasts.

## Displayed facts and where they come from

All of these are in `renderPlan` (06-runtime) unless noted. The projection field is in `50-native-plan.js`.

| Fact | Web | Projection |
|---|---|---|
| Program title (ACTIVE PLAN card) | inline id → name map | `program` |
| Status line | `before ? 'Starts '+date : over ? 'Program complete' : 'Week wk of 8'` | `status` |
| Sessions done, on track | `done of tot sessions done · round(done/due) % on track`; `due` counts training days up to today | `summary` |
| Progress bar, hero count `done/tot`, cycle line | `done / tot` | `sessions.done`, `sessions.total` |
| Hero copy ("Training protocol · 08 weeks", "Built by consistency." and the rest) | static | native strings |
| Week tile: `01`, `WEEK 01`, `n of m sessions`, meter, mark ✓ ● ○ | per week | `weeks[].number`, `label`, `meta`, `done`, `total`, `complete`, `current` (the mark: ✓ if complete, ● if selected, ○ otherwise) |
| Daily missions heading `WEEK 01 / Oct 26 — Nov 1` | selected week | `weeks[].label` + `range` |
| Day tile: weekday, date, code or icon, kind, ✓ or × | `dayCls`, `d.code` or the label's last capital letter | `days[].weekday`, `dayOfMonth`, `glyph`, `icon`, `kindLabel`, `status`, `today` |
| Tile description (aria-label) | `label, Month D` | `days[].description` |
| Legend (Completed, Missed, Today, Upcoming) | static, theme colours | native strings and tokens |
| Program key tags | `A / C · Strength` for class programs, `A / C / E` for foundation, `A / B / C` plus "Athletic" for legacy | `key[]` (`icon`, `label`) |
| Day card heading, tag, note | `Wed, Oct 28 · label`, `Week n · p% done`, `d.note` | `days[].title`, `tag`, `note`, `icon` |
| Locked note | `planSel > today` and not rest | `days[].locked` |
| Item: title, optional tag, detail, one checkbox per set | `d.items` | `items[].title`, `optional`, `detail`, `sets[]` |
| Item value field ("Weight used" or "Minutes done") and "last: X" | `it.wt` or `it.min`; `lastVal` | `items[].value` (`text`, `placeholder`, `last`), or null |
| Session log and "Last time:" or "Previous:" | `c.log`; `lastLog` | `items[].log` (`text`, `previous`) |
| Guided workout launcher | `gwrLauncher` (22-runtime), for the selected day | `days[].guided`; `session` takes its place on every day when a session is saved |
| General rules | `scholarRulesHtml`, `legacyAthleteRulesHtml`, `athleteRulesHtml` (14-runtime) | `rules.items[]` and `rules.progression[]` (`lead`, `text`) |
| Where the XP orbs fly | `fxTarget` (07-runtime): the centre of `#brandLogo` in the top bar | `celebrationTarget` (`x`, `y`, page pixels from the top of the page, where the shared bar draws the bar) |

Derived values: XP per completed day, set by `planDay`:

| Program | Strength | Cardio | Recovery | Mobility | Rest |
|---|---|---|---|---|---|
| foundation and legacy | 40 | 30 | 20 | | 0 |
| class light programs | 30 | 25 | 15 | 20 | 0 |

The XP is split 70/30 between the primary and secondary attribute. It is 0 when the linked catalog activity (`PLAN_LINK`) was already logged that day.

## Actions and what they write

| Action | Handler | Writes | Side effects | Phase 1 |
|---|---|---|---|---|
| Select week | `selectWeek(w)` | `planSel` (page global, not saved) | `renderPlan` | native, local. Native selects the week's Monday as the page does. When the plan starts mid-week, it keeps a selected day of that week, as the page does, or else takes the week's first plan day, where the page falls back to today (a quirk). |
| Select day | `selectDay(date)` | `planSel` | `renderPlan` | native, local. The page's own selection is followed when it changes (`selected.revision`). |
| Tick or clear a set | `planToggle(date,key,i,checked)` | `plan.checks[date].s[key][i]`. When every required set is done and there is no `actId`, it adds activity `plan_<date>` (type `plan`, skill Training, `xpSplit`), adds attribute and Training XP, and sets `actId`. When the day is no longer done, `removeAct` takes the XP back and deletes `actId`. | `save()`: XP rebuilt, `checkBadges` (badge toast after 600 ms), `checkLevelUps` (`celebrateXP`, then the level-up overlay after 950 ms), storage write, `renderAll`. The save wrappers run `lmaSoon` (Android sync), the Food sync and `dhAutoVerify`, which can complete the daily-hand quest "Follow today's plan" (`toggleQuest`: quest XP and activity). Toast "X complete · +N XP" or "Session reopened · XP removed". It now returns `{saved, message}` and toasts success only after a save that worked. | kit command `toggle` |
| Value | `planVal(date,key,v)` | `checks[date].v[key]` (trimmed) | `save()` | delegated: the page's field |
| Session log | `planLog(date,key,v)` | `checks[date].log[key]` (deleted when empty) | `save()`. It is read by the Journal (`planLogsForDay`, 04-runtime) and its day list. | delegated: the page's field |
| Guided workout | `gwrStart(date)`, `gwrResume()` | `state.guidedWorkout` (the session, its phase, timers, skips). Per logged set: `checks[date].setDetails[key][j]`, `v[key] = "<w> lb"`, then `planToggle(…, true)`. Also `checks.substitute[key]`. On finish, `state.workoutHistory` (newest first, at most 120). `gwrExitComplete` clears the session. | Overlay plus `body.noscroll`, a 250 ms tick while open, the FX canvas, WebAudio, haptics, the 3D gym | delegated |
| Leave plan | `leavePlan()` | `confirm`, then `state.plan = null` and `planSel = null`. Earned XP and activities stay; a saved session no longer matches. | `save()`, toast "Plan ended" | delegated |
| Sign up | `startPlan()` | `state.plan = {id: classPlanId(classId) or 'athlete_beginner8', classId, start: Monday of #planStart, from, checks: {}}` | `save()`, toast | page only (offers are not drawn) |
| General rules, swipe strips | `<details>`, scroll | none | none | native, local |

## Entry points

- Bottom bar "Workout plan" (`navigate('plan')`, `index.template.html`; tabs are editable).
- The Overview "Today" card (18-runtime, `navigate('plan')`).
- Life Director: `H.openPlanDay(key)` opens the view and calls `selectDay` (`features/life-director/logic.js`); `readPlanSessions` reads `planDay`.
- Voice: open "plan" (46-voice-nlu); the plan summary and "start" (50-voice-gen4: `lmvGo('plan')` then `selectDay(today)`); ticking and unticking today's sets with undo (50-voice-gen5, `planToggle`).
- Onboarding finish creates the class plan from this week (12-runtime).
- `switchClassTo` clears the plan (14-runtime).
- Deleting a plan activity clears `actId` and leaves the sets ticked (04-runtime `deleteActivity`).
- The daily-hand quest checks `dayDone(today)` (50-quests-hand).
- Escape and Back close the runner (22-runtime).

## Stores

- `lifemax_rpg_prototype_v1`: `state.plan` (`id`, `classId`, `start`, `from`, `checks{date: {s, v, log, actId, setDetails, substitute}}`), `state.guidedWorkout`, `state.workoutHistory`, the `plan_<date>` activities, and attribute and skill XP (rebuilt from activities on save).
- `lifemax_android_prefs_v1`: the `nativePlan` switch.
- `localStorage.lma_gwr3d`: the 3D gym switch.
- `planSel` is not stored.

## Native phase 1 (`50-native-plan.js`, `plan.projection.v1`)

The screen draws every state that has a plan:
- all eight weeks with every day and item (selection is local, so no round trip);
- the launcher;
- the key;
- the rules.

The `toggle` command calls `planToggle`:
- It has set semantics: the set ends as asked, and a repeat answers `changed: false` without saving.
- It refuses unknown sets, malformed dates and future days.
- A failed save answers `ok: false` with the page's own "Could not save to this browser (storage full?)". The change stays in memory, as after the page's own checkbox, together with what the save set off (badges, a daily-hand quest completed by `dhAutoVerify`), and the next save stores it all. The projection sent with the reply shows the boxes as the page holds them. Nothing is taken back: undoing only the tick would leave the quest and the badges of a day that is not done.
- The day's XP is paid once (`actId`).
- `celebrateXP` is held: the reply carries `celebrations` for the screen to draw, and the page plays `xpCue` (sound and buzz) 120 ms later. A failed save carries neither.
- The kit drops the refresh that the command's own renders asked for, because the projection sent with the reply already carries them: one projection per tick, not two.
- A box waits for its answer for at most 5 seconds. If the answer is lost on the way (A14: the outcome is unknown), the next projection after that decides what the box shows, and the box takes taps again.

Delegated (the screen closes, the page's handler runs, `revisit()` follows):
- the guided workout (`gwrResume` or `gwrStart`);
- editing a value or a session log (the page selects the day and focuses its field);
- leaving the plan.

The screen steps aside (`blocked()`) when any of these is true:
- there is no plan;
- one of the page's plan fields has the focus;
- a level-up overlay is up;
- the kit's own rules apply (another view, an overlay that `lmaTopOverlay` knows, `body.noscroll`).

A `MutationObserver` on the body class and children, and on `#planBody`, plus `focusout` and `change` on the plan fields, closes the screen when the page covers it and brings it back after. This covers the runner closing, a level-up ending, a field losing focus, and a plan started from an offer.

## The native screen (`NativePlanPlugin`, `feature/plan`)

It draws the projection with the page's sizes, colours and spacing (measured with `getComputedStyle` at phone width) and shows the toggle's message as a toast above the bottom bar and its celebration as `celebrateXP` draws it. The page's CSS effects come from `core/designsystem/Css.kt` (`boxShadow`, `cssLinearGradient`, `mix`). Where it differs from the page:
- The day card and the General rules are separate cards below the calendar. On the page they sit inside the calendar card (see the quirks below).
- The week and day strips keep their scroll position. The page re-renders them, so they jump back to the start after every tap.
- The inset 1 px highlights of the cards and tiles are not drawn. The drop shadows are.
- The fields are not editable on the screen: a tap hands the field to the page (delegated, above). The session-log field has no resize handle.
- The "Workout plan XP" feature tip the page shows once is not drawn.
- A tick that pays more than one award (the day's XP and the daily-hand quest's) shows one "+N XP" label with the sum, where the page draws a label per award over each other.
- The orbs fly to the logo and burst there as on the page, but the logo does not pulse: the bar is the page's captured drawing. The page's orbs leave a trail of small sparks and its particles add their light (`lighter`); the screen draws three fading echoes behind each orb and blends normally.
- A tick that levels up: the page opens its level-up overlay 950 ms later, and the screen steps aside for it in the middle of its own burst and toast. The page's own toast, under the overlay, carries the same message.
- After a tap on a field, the page focuses it from a Capacitor listener, which is not a tap inside the WebView. Whether Android raises the keyboard for that is still to be checked on the phone; if it does not, the field needs a second tap. Back that only hides the keyboard leaves the field focused, so the screen comes back once the field loses the focus.
- Pixel icons are drawn cell by cell (`PixelIcon`, shared), so at some sizes a faint seam can show between cells, which the page's icons do not have.
- Each set box answers taps over 48 dp around it, as Compose widens small targets, and a tap between two boxes goes to the nearer one. The page's boxes take taps on their 20 px only.

## Known web quirks

- `gwrLauncher` escapes labels that the foundation and class programs have already escaped, so the page shows `&amp;`. The projection carries the plain label.
- Legacy strength tiles show "S": the glyph is the label's last capital. This is reproduced.
- "last:" and "Last time:" match by item key across sessions (E0 of another session). This is reproduced.
- Badge toasts and other page toasts are not visible under the native screen. The toggle reply carries the toggle's own message.
- `renderPlan` leaves the calendar's `.plan-calendar` element unclosed, so the day card and the General rules card are drawn inside the calendar card's border. The native screen draws them as separate cards, as the markup means them.
