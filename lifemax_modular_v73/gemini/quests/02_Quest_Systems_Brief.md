# LifeMax RPG: quest systems brief (build v153)

Everything below was read from the code, not guessed. Exact code is in `quest_code_excerpts.js`; every class quest is in `class_quests.json`; the whole app source is in the zip.

## What LifeMax is
A personal "real life as an RPG" Android app for one person (the owner). It runs offline on the phone, is also the phone's home screen, and has no server, no accounts and no other players. The player logs real effort and gets XP, levels, Sparks (a spendable currency), and a cat companion that grows with them. The app is plain JavaScript in one HTML page (data in the phone's local storage). A coding AI assistant (not a human team) builds whatever you design, in small steps, and tests it automatically.

## The character
- **8 attributes:** physique, endurance, intelligence, business, social, discipline, adventure, wellbeing. Every XP award goes to one or two of them.
- **Level curve:** L to L+1 costs `100 + 25*(L-1)` XP (same for character and attributes). Titles: 1 Novice, 3 Apprentice, 5 Adept, 8 Veteran, 12 Master, 18 Grandmaster, 25 Legend.
- **4 classes** (Athlete, Scholar, Entrepreneur, Explorer) chosen by the player; each brings its own quests and an 8-week plan. Class quests come in **stages that unlock at character level 1, 3, 5, 8, 12**.
- **Decay:** an attribute that goes untrained for a while starts to fade (the Overview shows a dot on each attribute that is "about to fade"). Planned rest days and rest tokens/streak shields stop decay and protect the streak. The player can tune decay in Settings.
- **Streak:** consecutive days with at least one real (non-passive) activity; rest days and shields keep it alive.
- **Sparks:** every XP earned also earns the same number of Sparks (decay never takes them back). Shop: colour themes 300, one equipped perk 600 (4 perks: Scholar's Flow, Iron Grace, Executive Edge, Compound Habit = +15% XP on every daily quest), rest token 250, streak shield 250, cat toys 150 to 450.
- **Daily XP caps (apply to everything):** soft cap 180 XP a day (beyond it, half value), hard cap 240. A small "rested XP" pool adds +50% on the next XP after days off (pool up to 300).
- **Badges** (~15): e.g. "Quest Taker = complete 5 quests".
- **"Today's luck" roll:** a 3D dice roll that gives a "fate number" 1000 to 9999 once a day. As far as I can tell it is decorative and changes nothing in the game today. It is a ready-made source of daily randomness.

## How quests work today
- **Where:** the **Quests** tab (the "Quest Board"). The Overview lists "Next moves" such as "2 daily quests left".
- **Data:** each quest is `{id, name, desc, cadence: 'daily'|'weekly'|'epic', xp, kind, attribute?, stage?, day?}` in `state.quests`. `day` limits a daily quest to one weekday (e.g. "Monday Syllabus Mapping"). `kind` is a free tag ("movement", "reading", "nutrition", "milestone" ...). `kind:'nutrition'` quests show in their own card.
- **A new player starts with 6:** 3 daily (15, 15, 20 XP), 2 weekly (35, 25), 1 epic (75). Choosing a class replaces them with that class's list: **Athlete 26, Scholar 35, Entrepreneur 35, Explorer 35 quests** (Athlete: 10 dailies; the others: 5 every-day dailies plus 2 weekday-specific ones per weekday; 6 weeklies; 10 epics in 5 stages of 2). XP per quest runs 10 to 150. See `class_quests.json`.
- **Completing:** the player ticks a checkbox. **Nothing verifies it.** A quest is not linked to anything the player logged (a "Move with intention" quest does not look at the workout they logged, the steps, or the Life Director session). Ticking adds a small activity record of type `quest`, XP to the quest's attribute (default discipline), and Sparks; unticking removes it.
- **Choosing a class** swaps the starter quests for the class list (custom quests are kept).
- **Periods:** a daily quest resets at local midnight (`id_YYYY-MM-DD`); weekly resets on Monday; epics are one-time (`id_once`).
- **Rewarded limits** (more can be ticked, they just pay 0): **3 daily quests a day, 3 weekly quests per rolling 7 days, 1 epic a day**, plus the daily XP cap above. So on a normal day the board shows 7 daily quests (Scholar, Entrepreneur, Explorer) or 10 (Athlete), but only 3 of them pay. The UI says "Quest XP limit reached for today" when you tick a fourth.
- **Custom quests:** "New quest" lets the player type a name, pick daily/weekly/epic and an XP value (1 to 500, default 25).
- **Locked epics:** stage 2 to 5 epics show a lock and "Unlock at level N".
- **UI:** the Quest Board has a hero with "Today's progress x / y" and a motivation line, a Daily list, a Weekly list, a Nutrition card, an Epic list, and the New quest button. Pixel-art style, dark theme. There is a flag icon, a trophy icon and a lock icon.
- **Voice:** the player can say "what are my quests", "complete the first one", "check off everything here".

## What a quest could be connected to (all exist today, all offline)
**Logged activities** (`state.activities`): `{type, name, duration (min), xp, primary, secondary, date, skill, notes, detail?}`. Types: `reading` (study), `business`, `strength`, `running` (all cardio), `language`, `recovery`, `practical`, `task`, `custom`, `plan` (workout plan day). Strength logs can carry `detail` (exercise, sets, reps, weight) and cardio logs distance and pace.
**Workout plan:** an 8-week program per class with daily sessions and per-exercise set checkboxes.
**Steps and health** (Samsung Health sync): steps per day for 30 days, daily goal (default 8000), sleep, workouts, weight.
**Life Director** (the daily planner): planned sessions and commitments on a 24-hour dial; sessions can be completed (XP) or skipped (penalty).
**Food log:** meals with kcal, protein, carbs, fat; calorie goal; a protein goal and a water log (in ml, default goal 2 L) were added recently.
**The cat:** feeding (about twice a day), petting (3 a day), playing (2 a day), level, personality traits, breeding with genes (ear length, tail length, coat), a cattery of extra cats. Cat care already feeds a small XP bonus.
**Money tab:** deposits (counted for XP above $50, capped daily and weekly).
**Journal:** a daily journal with a "reflective coach" and a fingerprint lock.
**Voice assistant:** rich commands, can log things, query anything above; can be extended with new commands.
**Notifications:** local notifications (reminders) work; haptics have an adjustable strength; sound effects exist.
**Plus:** a Sparks shop, perks, "ascension" (attribute stars, +5% XP per star), the luck roll, and the Overview "Next moves" list.

## Known weak spots in the quest system (for you to fix)
1. It is a checklist. There is no surprise, no choice, no story, no payoff moment.
2. 7 to 10 daily quests on the board, 3 pay: the board is noisy and the cap is a hidden surprise.
3. Nothing is verified, so ticking is honour-system; some quests (steps, workouts, meals logged, a planned session finished) could be confirmed automatically, and that would feel great.
4. Daily quests are the same every day (apart from a few weekday-named ones), so they go stale within a week.
5. Quests ignore the rest of the game: decay, streaks, the cat, the luck roll, the weekday, the player's weak attributes, the time of day.
6. No short-term vs long-term ladder beyond epics that unlock at levels.
7. Failing or skipping has no texture (and it must not feel like punishment).

## Hard constraints
- One player, offline, years of use. No servers, no accounts, no other players, no network at run time.
- **No AI at run time.** Everything must run from rules, tables and your authored content (you can author a lot of content up front). Randomness must be seeded by date (so reopening the app never rerolls) unless the player spends something to reroll.
- Real-life effort must always beat farming. Quests must not become the main XP source: today the daily XP cap is 180 soft / 240 hard.
- Be kind: no guilt, no streak-shaming; welcome the player back after a break.
- Phone-first UI, small screens, one thumb. Pixel-art style.
- Implementable in plain JavaScript in small shippable phases, with behaviour you can test automatically.
