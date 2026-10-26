You are a senior game designer and systems/economy balancer. I'm building **LifeMax RPG**, a solo, offline Android app that turns real life into an RPG: the player gets XP, levels and a pet cat for real effort (workouts, study, work, sleep, steps, saving money). I have attached three files:

1. `02_Systems_Brief.md` — a plain-English description of how the game economy works today (read this first).
2. `xp_code_excerpts.js` — the actual code that awards XP, sets levels, applies decay and handles health sync.
3. `LifeMax_RPG_v104_lean.html` — the whole app as one file with images and data tables stripped, for reference only. You do not need to read all of it. Use it only to check a detail.

**Your task: design a balanced XP and progression system for this app, and give me exact numbers I can implement.** I'll hand your answer to a programmer (an AI coding assistant) who will implement it, so be concrete: formulas, tables, constants, and rules written precisely enough to code.

Work through this carefully before answering, and show the math where it matters.

**Design goals**
- One person, using the app for years. It should still feel rewarding at month 12 and not feel grindy at month 1.
- Honest real-life effort should always beat cheating or passive farming. Steps and sleep are passive, so they must matter but never outweigh a real workout or a real study session.
- Missing a few days should sting a little, not wreck the player. Coming back after a break (a week, a month, a holiday) should feel welcoming, not punishing.
- Everything the player does should be comparable on one fair scale: lifting, running, studying, working on a business, sleeping well, hitting a step goal, saving money.
- No one number should be able to run away. Add sensible daily or weekly caps and explain why each is there.

**Please deliver, in this order**
1. **Diagnosis.** Using the brief and code, tell me the 5 most serious balance problems in the current system, with numbers. For example: how many days of passive steps equal one real workout, how long to reach levels 10, 25 and 50 for a typical user, and whether decay can ever outpace a normal week.
2. **Target pacing.** A table of target character level by week and month for three player types (casual: 3 sessions/week; committed: daily; extreme: two sessions a day). State the level curve you recommend and what it costs per level. Keep or replace the current `100 + 25*(L-1)` curve, with reasons.
3. **XP values.** A table for every XP source (each of the 9 activity types, quests by cadence, steps, sleep, weight/no XP, savings deposits, cat care, Life Director sessions). Give the base number, the duration/effort formula, the daily cap, and the diminishing returns rule. Keep the 8 attributes and the 70/30 primary/secondary split unless you can show a better one.
4. **Decay, streaks and returning.** Replace or tune the current decay (3 days grace, 2% a day) and streak (1 missed day forgiven). Include a "welcome back" rule and a rest-day rule so planned rest does not cost XP.
5. **Long-term goals.** What happens after level 25? Propose prestige, seasons, mastery tiers or something better. Suggest a few things XP can be spent on or unlock (cat items, cosmetic themes, class abilities, anything small that I can build).
6. **Anti-cheese rules.** How to stop fake step shaking, logging the same thing over and over, and sleep-farming, using only data an Android phone has (Health Connect steps, exercise sessions, sleep sessions, weight).
7. **Migration.** The player already has XP under the old system. Give me a rule to convert it so nobody loses levels unfairly.
8. **A test plan.** The 10 most important numbers to tune first, and a quick simulation I could run (describe it in pseudocode, or give a short Python script) to check the pacing table before shipping.

**Format rules**
- Use tables for numbers. Put the single most important recommendation of each section first.
- Say plainly when you are guessing or when a choice is a matter of taste, and give your default.
- Do not write the app's code. Pseudocode and Python for the simulation are fine.
- If something in the brief seems wrong or incomplete, say so and tell me what to check in the code.
- Finish with a one-page summary I can paste straight to my coding assistant as the implementation spec.
