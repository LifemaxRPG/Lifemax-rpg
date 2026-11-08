# Maintaining the in-app update history

The single source is `lifemax_modular_v73/src/data/update-history.json`.
Settings → Phone & sync → App updates → What's new displays it offline in native and classic Settings.

For each release with user-visible changes:

1. Keep entries newest first. The entry for the next release uses `"build": "current"`;
   the assembler stamps its actual GitHub Actions build number into the bundled page.
2. Before starting a NEW release entry, replace the previous `current` with its published
   build number from the latest release notes. Preserve its date, title, and bullets. If
   it never published, keep editing that current entry instead of archiving a failed build.
3. Add short, concrete bullets saying what changed and where the user can find it. Group
   related changes. Exclude temporary experiments, internal refactors, and work that is
   still inactive. Do not claim a pending build has shipped.
4. For a change already covered by the current entry, edit its bullet rather than duplicating it.
5. Run `python3 lifemax_modular_v73/tests/update-history.test.py` and the relevant feature checks.
   When changing a runtime segment, regenerate its module map/index as required by check_source.py.

History begins with the release introducing this feature. Earlier build histories have not
been reconstructed. Do not invent old build numbers or promise that unreleased work is available.
