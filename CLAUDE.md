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

## New features are native

Do not build new web tabs or new web screens. A new feature gets a native (Kotlin/Compose) screen from the start, following
`docs/architecture/NATIVE_TAB_GUIDE.md`. Until the logic cutover the page is still the only writer of saved data, so the page
side of a new feature is limited to what a native screen needs: the saved state, the model it projects and the commands it
accepts. Design mock-ups for the owner to react to are fine; shipping a web tab is not. If a native screen truly cannot be
built for something, ask the owner before building it on the page.

## User-facing update history

Follow `docs/UPDATE_HISTORY.md` for every user-visible release change. Update
`lifemax_modular_v73/src/data/update-history.json` with short user-facing bullets.
The history is shown in Settings; keep earlier published entries and exclude unfinished work.
