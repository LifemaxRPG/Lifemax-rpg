# TASK FOR GEMINI: Design the "Reflective Journal Coach" for LifeMax

You are the systems designer for **LifeMax**, a personal life-RPG app on one Android phone (Galaxy S25 Ultra). It tracks activities as XP, schedules the day on a 24-hour dial ("Life Director"), has quests, a shop, and a virtual cat. I (the owner) do not want code from you. I want **precise logic, rules, text and numbers** that an engineer can implement straight from your answer. In an earlier round you designed the XP economy and it was implemented almost exactly as written, so be that exact again.

## What we are building
The app has a **Journal** (inside the Library tab). Today it is only a record: a list of days, and for the selected day a compiled list of everything logged plus an optional free-text reflection box. It looks backward and says nothing about what the data means.

Expand it into a **reflective coach**: it turns the data the app already has into short, kind, useful insight, asks good reflection questions, notices patterns over weeks, and welcomes people back after an absence. Everything appears **inside the Journal** (as extra content on a day, plus weekly and monthly review entries in the same day list). No new tab.

## Hard constraints
1. **Offline and rule-based.** No AI, no internet, no cloud at runtime. All text comes from rules and templates that you write. Think "a very good rule engine with a large phrase bank", not a chatbot.
2. **Only data the app already has** (inventory below). If a good insight needs data we do not collect, say so in a separate "would need new data" list, and keep it out of the main design.
3. **Never guilt-trip, shame, or lecture.** No streak-loss panic, no "you failed". The user may have ADHD-style inconsistency, shift work and bad sleep. The tone is a calm friend who notices things. Missed days are information, not sin.
4. **Small and glanceable.** A day insight is 1 to 3 short lines. A weekly review fits one phone screen with an optional "more".
5. **Deterministic.** The same data must always give the same text (no randomness, or randomness seeded from the date) so entries do not change every time you open them.
6. **Honest.** Never claim a cause. Say "on days after under 6 hours of sleep you did fewer sessions", not "poor sleep makes you lazy". State sample size; stay silent when there is not enough data.

## Data inventory (what exists on the phone)
- **Activities** (the log): name, type, skill, primary and secondary attribute, XP earned, duration in minutes, timestamp. Attributes are fixed RPG stats (e.g. endurance, discipline); skills are named (e.g. Walking, Quest Completion).
- **Day reflection:** one optional free-text note per day. No mood field exists.
- **Workout plan notes:** per-day, per-exercise log text.
- **Quests:** daily, weekly and epic quests with done/not-done per period, XP value.
- **Life Director (the scheduler):** planned sessions per day with a lifecycle (planned, done, partial, skipped, excused), the planned time and duration, work/sleep/commitment blocks, one-off appointments, rest tokens, goals with weekly targets.
- **Health (Samsung Health sync, when connected):** daily steps, workouts, nightly sleep duration, weight entries.
- **Food log:** items per meal slot (breakfast, lunch, dinner, snacks) with calories, protein, carbs, fat; an optional daily calorie goal. No water tracking.
- **XP economy:** character level and XP curve, streak (rest days allowed), daily cap and pace, decay and rest-day mechanics, Sparks (a currency), shop and perks, ascension.
- **Cat:** feeding, petting, play, bond level, care streak.
- **Time:** local date and time, day of week, time zone.
Assume 1 to 3 months of history at most for most users, and gaps are normal.

## What I need back (deliverables)

### A. Signal catalogue
A table of **every signal** the coach can compute from the data above. For each: name, exact formula (windows, units, thresholds), minimum data needed before it is allowed to speak, and which screen sections it feeds. Include at least: effort and load vs the person's own recent baseline, consistency (a forgiving definition that handles rest days), sleep vs next-day output, time-of-day patterns (when do sessions actually happen vs when planned), skipped-session patterns (which kinds, which days, after what), streak health, nutrition adherence, activity variety, cat-care, and "good day" detection. Say how you define "personal baseline" so a quiet user and a hardcore user both get fair feedback.

### B. Day insight rules
A rule table: **condition -> priority -> message template(s)**, so that each day shows at most 1 to 3 lines chosen by priority and no repeated wording on consecutive days. Provide template variants (at least 3 per rule) with placeholders. Cover: great day, ordinary day, quiet day, rest day, a day with a skipped session, a day after bad sleep, a comeback day, a day with only a reflection and no activity, and "nothing logged" (should the journal even show these? decide and justify).

### C. Reflection prompts
A prompt bank. Each prompt: id, when it appears (condition), the question, and optional 3 to 5 tap-to-answer chips that write a short line into the day's reflection. Cover wins, skipped sessions, low-energy days, high-output days, rest days, after a streak ends, and weekly questions. Questions must be specific and answerable in under 10 seconds. Include a rule so the user is never asked more than one prompt a day and can dismiss prompts permanently by type.

### D. Weekly and monthly review
- Exact structure (sections, order, what each shows), the numbers used, and how it ranks what to mention (max 3 highlights, max 1 "worth trying" suggestion).
- A suggestion engine: rules that map a detected pattern to **one** small, concrete, optional experiment (e.g. move a recurring session to the time it usually really happens). Include how a suggestion is chosen, how it is remembered, and how the next review reports on whether it was tried (without judgement).
- A monthly layer: what is different from weekly (trends, best week, level and attribute growth, "chapter title" for the month).
- When the review is generated (what day or trigger), and what it shows if the week was empty or partial.

### E. Comeback ("welcome back") design
What the Journal and the app say and do after 3, 7, 14 and 30+ days without logging. Soft restart rules that interact safely with the existing streak, decay and rest-day systems (propose exactly how, but do not break the XP economy: tell me which existing numbers it would touch). A first-day-back entry that celebrates showing up. Include copy.

### F. Memory and search
How the user can find past days: which tags or labels are auto-assigned to a day (rules), what filters the day list should offer, and what a "best days" or "on this day" surface shows. Keep it to what is possible offline with the data above.

### G. Edge cases and safety
Rules for: first week of use (almost no data), time zone change, sparse or contradictory data, deleted entries, health sync off, someone logging many things at once, extremely low or high numbers, and **sensitive topics** (weight, food, low mood). For weight and food, define strict guardrails so the coach never encourages restriction, never praises weight loss, and never shows calorie numbers in a judgemental way. If a reflection contains words suggesting distress, define a gentle fixed response and what the coach stops doing.

### H. Worked examples
At least **8 complete example days** and **3 weekly reviews** and **2 comeback entries**: show the input data in a compact table, the signals computed, the rules that fired, and the exact final text. Include one deliberately awkward case (e.g. great output but terrible sleep and a skipped session) and show how it stays kind and honest.

### I. Implementation notes for the engineer
Data structures needed (what to store: e.g. dismissed prompt types, last suggestion, cached review), when to compute (on open, daily, weekly), how to keep it cheap on a phone, and a test plan (inputs with expected outputs) so I can verify the implementation against you.

### J. What would need new data
A short, ranked list of new things the app could start collecting (e.g. a one-tap mood or energy rating) that would most improve the coach, with the exact UI cost for each. Do not build the main design on these.

## Format
- Plain text and tables I can paste to an engineer. No code, but exact formulas and pseudocode-level conditions are welcome.
- Number every rule and prompt (e.g. DAY-07, PRM-12, SUG-03) so we can refer to them.
- Where you must make a choice, **pick one and say why**; do not list options without a recommendation.
- If something is ambiguous in this brief, state your assumption in one line and continue; do not stop to ask.
- Length: as long as needed to be implementable, but front-load a one-page summary of the whole design.
