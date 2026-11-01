# Chat protocol for the quests spec (read this first; it overrides the brief's delivery rules)

You are working in a chat window, not in a repository. Long answers get cut off in this window, and a cut-off answer loses
work. So you deliver the spec **in short numbered parts, one part per message**, and you wait for "next" before the following one.

The assignment itself is in `GEMINI_QUESTS_SPEC.md` (the brief): read it fully. Where the brief and this file disagree, this
file wins, because you are in a chat with a handful of files, not the whole repository.

## 1. The files you have

You have these files only, flat (no folders). Cite them by file name and line, for example `50-quests-hand.js:133`.

| File | What it is |
|---|---|
| `GEMINI_QUESTS_SPEC.md` | the assignment |
| `ECONOMY_SPEC.md` | the finished spec for the area before this one: copy its style and its numbering; cite its rule IDs for quest XP, the caps and rested XP; never restate or change them |
| `04-runtime.js` | `questKey`, `isQuestDone`, `toggleQuest`, the save, `takeBack` |
| `13-runtime.js` | `stageLvl`, `questLocked` |
| `14-runtime.js` | the quest board: `renderQuests`, `qosToggle`, custom quests |
| `50-quests-hand.js` | the Daily Hand: the deal, seed, rerolls, card verification, `QUEST_LIBRARY` |
| `50-quests-table.js` | the 3D table: only its ledger and quest-list data matter (from about line 357); ignore drawing and physics |
| `43-economy.js` | the XP rules the quest rules call |
| `44-rewards.js` | Sparks, badges and what quest completion triggers |

Anything the brief mentions that is **not** in these files (Life Director's quest bridge, the badge and level-up checks, pet
care, the workout plan's `dayDone`, `freshState`'s default quests) is **not in your kit**. Do not guess it. In the boundary
part, list each one under "not in the kit, so contract only", say what you could tell from how the files you do have call
it, and put what you would need to see in the open questions of the last part.

## 2. How to answer: the one rule that protects your work

**Never write more than about 800 words in one message.** If a part is getting long, end it at a rule boundary and move on
to the next part. A short part that arrives complete is worth more than a long one that is cut off.

### Message 0 (your first reply): the outline only

Reply with nothing but an outline of the whole spec as a numbered list of parts, about 10 to 14 of them. For each part give:
its title, the rule-ID prefix and range it will use, and the source files it reads. Order the parts so each one only needs
what came before: boundary and conventions first; then the quest model and periods; then stage locks; then custom quests;
then the board's order; then the Daily Hand's deal, rerolls and verification in several parts; then the ledger; then the
defects; then the golden-case notes; then the closing report. **Write no spec content in message 0.**

### Every later message: exactly one part

When the user says "next", write the next part, and only that part:

1. Start with the line `<<< PART k of N: title >>>`.
2. Put the whole part in **one Markdown code block** (so the user can copy it in one go).
3. End the code block's content with the line `<<< END PART k of N >>>`.
4. After the code block write one line outside it: `Rule IDs used so far: ... Open questions so far: ...` (at most three
   short lines), then `Next: part k+1, <title>`.
5. Stop. Do not preview later parts, do not recap earlier ones, and do not add commentary.

Number the rules continuously across parts (QST-1, QST-2, and so on), so that the user's pasted result needs no editing.

### If you were cut off, or the user says "resume part k"

Start part k again from its first line and make it shorter: split it into k.a and k.b if needed, and say so on the first
line. Never continue from the middle of a cut-off message.

## 3. Rules for the content (from the brief, restated for the chat)

- Every rule has an ID and a source: file name and line, or a line range. Check the line number against the file before you
  write it. A wrong citation is worse than none.
- Label every rule **Read** (you derived it from the code) or **Executed**. You may only write Executed if you actually ran
  code in this chat's code tool and saw the result; in that case show the code and its output inside the part. You can
  probably not run the JavaScript here, so most rules will be Read, and that is fine. Never write PASS, ✓ or "verified" for
  anything you did not run.
- Pin the tricky details exactly: rounding, local dates against UTC, string comparison of dates, what happens at midnight and
  across a clock change, the seed's exact inputs and the random generator's exact steps.
- Where the code looks buggy or accidental, describe it in the defects part as QD01, QD02 and so on, with how to see it. Do
  not change it and do not specify the "fixed" behaviour: the spec describes what the app does today.
- Do not write code in the spec except tiny snippets that pin a formula. Do not write Kotlin.
- Do not name any AI model or product in the document text.

## 4. The closing parts

The last three parts are, in order:

1. **Golden-case notes:** which inputs to vary, and what to hold fixed so a case is repeatable (the date, the class, the luck
   roll, the deal's seed, the existing records).
2. **Report:** the checks you executed (code and output), what you could not run and why, everything "not in the kit", your
   open questions, and anything in `ECONOMY_SPEC.md` you think is wrong, with the line that shows it.
3. A final line: `DONE: N parts`.

## 5. How your work will be used

The user pastes all your parts together into one document and hands it to a reviewer who samples about 30 rules against the
real code. Rules that cannot be checked, or that are cited wrongly, come back as corrections. Honest "Read" labels and honest
"not in the kit" notes are what make the spec usable.
