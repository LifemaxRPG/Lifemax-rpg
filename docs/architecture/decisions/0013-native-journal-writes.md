# ADR 0013: Native Journal writes: the page stores, native sends the words

Status: phase 2 implemented, behind Settings, Appearance, "Native Journal (beta)", off by default (capability `library.journal.write.v1`; plan `plans/JOURNAL_AND_LIBRARY_SHEETS.md` section 3.4).

## Context

Phase 1 drew the Journal natively but handed every write to the page's own field: the screen stepped aside, and came back after the celebrations. Phase 2 lets the owner type in a native notebook field. The Journal pays XP for a reflection, a review answer, a welcome chip and the catch-up, so a write must never pay twice, pay mid-sentence, or lose the owner's words when a save fails.

## Decision

1. **The page stays the only writer.** Native sends the words and the page stores them through its own owners (`journalSaveReflection`, `journalSaveReview`, `jcCatchUpWith`, `jcClaim`), the same functions the page's own fields call. One rule, two drawings.
2. **Words and reward are separate saves.** The words are stored first with a plain write; the reward is paid in a save of its own with a take-back of the reward alone. A reward that cannot be stored never takes the words back, and ok means the words are stored.
3. **Autosave pays nothing.** `reflect` and `answer` carry `final`. The screen autosaves 1.5 s after typing stops with `final:false` (words only); leaving the field, the tab or the app sends `final:true`, which may pay. The reward is paid once, keyed by the page's own receipts: `days[d].xpAwarded`, `welcome[today].claimed[kind]`, `reviews[key].paid`, stored in the same save as the coach activity.
4. **Text writes are a set with a base check.** The payload carries `base`, the words the screen last saw. The page refuses with `conflict` (and the stored words, never while locked) when it holds neither `base` nor the new words. A repeat of stored words writes nothing, unless their last write failed (the page remembers), when it writes them again.
5. **Repeat-safe by natural receipts, no operation ids.** A lost reply is retried with the same payload: the receipts make it land at most once. Claims and the catch-up answer "Already recorded." The screen keeps unconfirmed words in its draft until the page's own text equals them; nothing is replayed after a restart.
6. **Celebration in the reply.** A paying command runs in `holdCelebrations` and returns `celebrations`; the screen draws it with core PageCelebration. When no screen shows (a flush after the screen closed, or the Journal locked) the page plays it.
7. **Flush on leaving.** The screen sends the final write when the tab closes and when the app stops, before dropping anything (A24: nothing else runs while hidden). While locked, text writes are still stored (the words are the owner's and nothing private comes back); claim and catch-up are refused.
8. **Privacy is unchanged (A35).** A hidden or locked screen holds no Journal frame data. Only words the page may not yet hold stay in the draft.

## Consequences

- No step-aside for writing when the capability is present; the phase 1 hand-over stays for a screen without it (`edit:"page"`).
- Native Journal (beta) stays one switch; no durable receipt for the screen's pending state across process death (A14/A15 gap).
- A conflict keeps the owner's words and replaces the page's on the next save; a merge is not attempted.
