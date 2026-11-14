# Working notes for Claude

## Which model

Work on Sonnet by default: it uses less of the owner's usage, and most remaining work follows written plans.

Tell the owner, in one line, before starting any of these, that switching to Opus (`/model`) is worth it, and why:
- a merge where both sides changed the same logic, especially the shared shell, NativeTabKit, ComposeTabPlugin or
  Life Director;
- debugging something whose cause isn't clear after a first look;
- reviewing work from Gemini or ChatGPT before it is merged;
- anything that moves saving from the web page to native (a logic-phase cutover);
- designing a new piece with no plan yet.

Then carry on with whatever model the owner chooses.

## No web development

The owner's rule: no more web development in this project. Do not add or extend web tabs, web screens, page logic or page
state. A new feature is fully native (Kotlin/Compose): its screen, its input, its logic and its saved data, which lives in
a native store (a versioned document written atomically, read strictly, with a safety copy, and included in backups) and
not in the page's save. Existing page code is touched only to fix a bug the owner reports or to remove something a native
replacement has taken over. Design mock-ups for the owner to react to are fine. If something truly needs a page change (for
example a bar entry that only the page can add), make the smallest possible change and tell the owner.

## User-facing update history

Follow `docs/UPDATE_HISTORY.md` for every user-visible release change. Update
`lifemax_modular_v73/src/data/update-history.json` with short user-facing bullets.
The history is shown in Settings; keep earlier published entries and exclude unfinished work.
