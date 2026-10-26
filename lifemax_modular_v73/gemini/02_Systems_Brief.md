# LifeMax RPG — systems brief (build v104)

LifeMax RPG is a personal "real life as an RPG" app, now an Android app (it also acts as the phone's home screen). One person uses it. Everything runs offline on the phone. The player logs real-life effort (workouts, study, work on a business, sleep, saving money) and gets XP, levels, a cat companion that grows with them, a daily planner (Life Director), a money tab and a health sync with Samsung Health.

The facts below were read from the code, not guessed. The exact code is in `xp_code_excerpts.js`.

## Character and levels
- Eight attributes: Strength & Physique, Endurance & Vitality, Intelligence & Knowledge, Wealth & Business, Social & Communication, Discipline & Execution, Adventure & Practical Skills, Wellbeing & Recovery.
- Every XP award goes to one or two attributes. Total XP is the sum over all attributes.
- Level curve (character level and each attribute level use the same curve): going from level L to L+1 costs `100 + 25*(L-1)` XP. Level 1→2 = 100, 2→3 = 125, 10→11 = 325, 20→21 = 575. Cumulative XP to reach level L is about `12.5*L^2 + 87.5*L - 100`.
- Titles by character level: 1 Novice, 3 Apprentice, 5 Adept, 8 Veteran, 12 Master, 18 Grandmaster, 25 Legend.
- Badges: first activity, 10 and 50 activities, 3- and 7-day streaks, character level 5 and 10, every attribute at level 2+, any attribute at level 5, 3 different skills, 5 quests, one 90+ minute session, $100 deposited.
- Four classes (Athlete, Scholar, Entrepreneur, Explorer). Each brings its own plan and quests. Class quests are in stages that unlock at character level 1, 3, 5, 8, 12.

## Earning XP by logging an activity
- Nine activity types with base XP for a 30-minute session: study 20, business 25, strength 32, cardio 27, language 20, recovery routine 16, practical skill 23, difficult task 15, custom 20. Each has a primary attribute and usually Discipline (or another) as secondary. The user can edit these numbers.
- Duration factor: 30 min = 1.0×, scales linearly up to 3.0× at 90+ minutes, floor 0.4× for very short sessions.
- Effort multiplier chosen by the player: 0.8 (light), 1.0, 1.1 (challenging), 1.2 (high quality).
- Diminishing returns per activity type per day: the 4th and 5th logs of the same type that day get 0.5×, the 6th and later 0.25×.
- Split: if there is a secondary attribute, 70% to the primary and 30% to the secondary.
- Each activity also feeds a named skill (e.g. "Lifting", "Spanish") that has its own XP counter.

## Quests
- Daily quests (15–20 XP), weekly (25–35 XP) and one-off epic quests (75 XP). Completing one logs an activity worth its XP to one attribute (no diminishing returns, can be undone).

## Decay (use it or lose it)
- If an attribute has not been trained for more than 3 days (grace), it loses 2% of its XP per day (at least 1 XP) until it is trained again. Both numbers are adjustable in Settings. Streak: counts consecutive days with at least one activity; one missed day is forgiven, two end it.

## Health sync (Samsung Health through Android Health Connect), added recently
- Workouts: each recorded exercise session of 5+ minutes becomes an activity that uses the same formula as manual logs (strength-type sessions use the strength rule, yoga/stretching-type use the recovery rule, everything else counts as cardio). Same diminishing returns.
- Steps: a finished day counts if it had 2,000+ steps. XP = 3 per 1,000 steps, counted up to 15,000 steps (so at most 45 XP/day). Today's steps are shown live but only pay out once the day is over. Goes to Endurance (primary) and Discipline.
- Sleep: each sleep session of 3+ hours counts as a recovery activity with a pretend duration of 30/40/60/90 minutes for under 4.5h / 4.5–6h / 6–7h / 7h+. So a good night is worth about 48 XP (90 min × recovery base 16 at 3×), a short one about 16.
- Weight readings only update the character's weight; no XP.

## Money
- Deposits into the in-app savings account give XP: `clamp(round(amount/10), 5, 60)`, at most 3 rewarded deposits a day. Paychecks (from the shift-pay feature) are deposited with no XP. Shift pay estimates come from the Life Director schedule and wages.

## Cat companion
- A cat that is adopted as a kitten and grows over ~30 days. Bonding points raise the cat's level while it is a kitten; care actions (feeding every 4+ hours, play, petting) matter. Cats have DNA (coat, body shape, personality) and can be bred in a breeding lab. How the real player's habits affect the cat is currently light.

## Life Director (daily planner)
- Plans the player's goals into the free time around sleep, work and commitments, shown on a 24-hour dial. Sessions show an XP chip. Completing sessions logs activity. (Exact rules not included in the excerpts — ask if needed.)

## Known gaps / honest weak spots
- The XP sources were added at different times and have never been balanced against each other.
- No limit on total XP per day.
- Steps and sleep are easy, passive XP; a hard workout can be worth about the same as a normal night's sleep.
- No "prestige"/long-term goal beyond level 25 (Legend). No spending of XP; XP is only a score.
- The cat does not yet give back anything in the economy.
