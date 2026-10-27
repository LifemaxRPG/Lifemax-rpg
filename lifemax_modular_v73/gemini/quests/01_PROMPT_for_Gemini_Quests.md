You are a senior game designer who specialises in habit-forming and "life as an RPG" apps. You know behavioural psychology (variable rewards, autonomy, competence, anticipation, loss aversion, the endowed-progress effect) and you know why most habit apps get boring after two weeks. You also think like a systems designer: every idea you give me must come with exact rules and numbers, because a coding AI assistant will build it from your answer.

## The job
I'm building **LifeMax RPG**, a solo, offline Android app that turns real life into an RPG: the player logs real effort (workouts, study, work, sleep, steps, meals, saving money) and earns XP, levels and Sparks, and a cat companion grows with them. It also acts as the phone's home screen.

The app already has **quests**, but they are a plain checklist: the same handful of daily and weekly tasks, ticked by hand with no verification, no surprise, no story and no payoff. **I want the quest system to become a genuinely fun mechanic: something the player looks forward to opening every day.** I do not know how to do that, which is why I'm asking you. One idea I floated: a random quest of the day. I don't know what else belongs, so I'm giving you full freedom to invent the mechanic. Please do the thinking: don't ask me questions, and where you must assume something, state the assumption and carry on.

## What I'm attaching
1. `02_Quest_Systems_Brief.md`: read this first. How the game, the economy and quests work today, what the quest system could connect to, its weak spots and the hard constraints.
2. `quest_code_excerpts.js`: the real code for quests, limits and caps.
3. `class_quests.json`: every class quest that exists today (name, description, XP, kind, attribute, stage, weekday).
4. `LifeMax-source.zip`: the whole app source, for checking any detail. You don't need to read all of it. The most relevant files are in `lifemax_modular_v73/src/runtime/segments/` (`04-runtime.js` quests and activities, `13-runtime.js` class content, `14-runtime.js` Quest Board, `43-economy.js` XP rules and caps, `44-rewards.js` Sparks, shop, perks, `18-runtime.js` Overview and the luck roll, `06-runtime.js` workout plan) and `src/views/quests.html` for the screen. There is also `VOICE.md` and `README.md` (a change log).

## The constraints (they are real, so please respect them)
- One player, offline, using it for years. No server, no accounts, no other players, no network.
- **No AI or language model at run time.** Everything must run from rules, tables and the content you author. Author plenty of content up front. Randomness must be **seeded by date** (reopening the app never rerolls) unless the player spends something to reroll.
- Real effort must always beat farming. Quests must **not** become the main source of XP (there is a daily XP soft cap of 180 and hard cap of 240 already). Show me how your numbers keep quests a spice, not the meal.
- **Kind, never shaming.** No guilt, no "you failed" screens, no streak-shaming. A week away should end with a warm welcome back and a good way to restart.
- Phone-first, one-thumb, pixel-art look, dark theme. Haptics, sound and notifications are available.
- It will be built by a coding AI assistant in small phases, so every phase must be shippable on its own, and behaviour must be testable automatically.

## Think first, then decide
Before you design anything, spend real effort on these questions and show me your reasoning:
1. **Why do people love quests in games** (Diablo's bounty, Hades' keepsakes and prophecies, Slay the Spire's events, Destiny's weekly challenges, Genshin's commissions, Stardew's bundles and mailbox, Animal Crossing's daily visitors, Duolingo's chests and quests, Habitica's party quests, Pokemon GO's field research)? What exactly creates the "I want to open the app and see what's today's thing" feeling, in a game with no other players?
2. **Why do habit-app quest systems usually fail** after two weeks, and how will yours avoid that?
3. Which of these levers belong in LifeMax, and which don't? Use any, combine them, or invent your own: a random quest of the day; choose 1 of 3 offered quests; reroll; mystery or sealed quests that reveal their reward on completion; wagers (stake some Sparks on completing today's quest); chains or story arcs across days; bosses or weekly raids that need several real-life actions across several attributes; class-flavoured quests; "the cat's request" quests; quests that react to the player (weak or fading attributes, a streak at risk, time of day, weekday, season, how long they've been away); a luck-roll-driven daily draw (there is already a daily dice roll that currently does nothing); loot and collectibles; titles and cosmetics; a quest journal or codex that fills up over months; limited-time events; difficulty tiers the player picks; companions or rivals that are simulated, not real people.
4. **Verification:** which quests can be confirmed automatically from data the app already has (see the brief), which need an honour tick, and how should the two feel different? An automatically completed quest popping its reward is a big part of the magic.

## What I want back
Start with **three genuinely different concepts** in a short paragraph each (name, the fantasy, the daily loop, why it's fun, the biggest risk). Then **pick one** (or a deliberate blend) and justify the choice against the constraints. Then write the full spec of the chosen design, in these sections:

1. **The vision in one paragraph, and a typical day:** minute by minute, what the player sees and feels in the app: morning, during the day, evening. Then a typical week, and month 3.
2. **The core loops:** daily, weekly and long-term (months). What's the reason to come back tomorrow? In a month? Next year?
3. **Mechanics, rule by rule:** for each mechanic give its purpose, exact rules, every number (XP, Sparks, probabilities, cooldowns, limits, level gates), and edge cases: missed days, rest days and vacation, time zones and midnight, rerolls, what happens to unfinished quests, having several quests at once, class stages, the existing 3-per-day paid-quest cap and how it changes or goes away.
4. **The generator:** the exact algorithm that picks today's quest(s) from a template library, in pseudocode: inputs from the player's state, weights, a date-seeded random function (give one), an anti-repeat memory, difficulty scaling by character level, and fairness rules (never offer something impossible that day, such as a 10 km run on a rest day or a gym quest at 11 pm).
5. **Data model:** every new or changed field in the player's saved state and in a quest template, as JSON examples with types. Say how old saves are migrated without losing anything.
6. **The content library**, as valid JSON I can paste into a file: at least **60 quest templates** spread across all 8 attributes and the 4 classes, with tiers (easy, normal, hard), a verification method for each (an exact condition on existing data such as "a logged cardio activity of at least 20 minutes today", "steps today at least 6000", "a Life Director session completed", "the cat fed twice today", "protein goal reached", or `self` for an honour tick), the XP and Sparks reward, and **flavour text in a warm, witty voice** (show me the voice with at least 15 finished examples). Also give 5 example story arcs or chains and 3 example bosses or events if your design uses them.
7. **Rewards and balance:** a table of rewards by tier and level, the total extra XP and Sparks quests can add per day and per week at levels 1, 5, 10 and 20, and a **week-by-week simulation of one imagined player for 4 weeks** (including a bad week) showing the numbers. Prove the daily XP caps are never made pointless.
8. **Screens and moments:** how the Quest Board and the Overview change, described precisely enough to build: layout, states (offered, accepted, in progress, auto-completed, claimed, expired), the reveal and reward moments (animation, sound, haptic), empty and returning-player states, and what a notification says (and when it stays silent). Include 5 voice commands ("what's my quest of the day", "reroll it" ...).
9. **Phased build plan:** Phase 1 must be a small slice that can ship in a day and already be fun; then phases 2 and 3. For each phase, list the files and functions likely to change (use the real names from the zip), and give **acceptance criteria written as testable behaviour** ("Given a player at level 4 with a fading Physique and no activity logged today, when it is the first launch of the day, then the offered quests contain at least one Physique quest ...").
10. **What I should measure and how I'll know it's working:** the app has a log where the player can thumbs-down a bad answer, so propose lightweight signals (completed vs offered, rerolls, days opened) and what would make you change the design.
11. **What you deliberately cut**, and the risks.

## Style
- Be concrete. Numbers, tables, JSON and pseudocode, not adjectives. Show the math where it matters.
- Optimise for fun first, then for buildability. If the best idea is hard to build, say so and give the cheapest version that keeps most of the magic.
- Keep it as long as it needs to be, but don't pad. Put the summary of the chosen design at the very top so I can read that first.
