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
