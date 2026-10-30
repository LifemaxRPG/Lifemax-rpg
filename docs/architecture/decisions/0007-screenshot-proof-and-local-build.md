# ADR 0007: Native screens are proven by screenshots from real fixtures, built where the work is done

Status: implemented (A32).

## Context

The owner has no computer and judges each build on the phone; the engineer could not compile Android code and only saw a screen after a CI build and the owner's report. Visual regressions in refactors (the Food split, moving Character onto the scaffold) could not be proven absent. A blue wash in the first screenshots came from fixtures with made-up colour tokens.

## Decision

1. Android builds and tests run in the cloud session (`tools/build_android_local.sh`, SDK and Java 21 installed; a Google mirror of Maven Central in `~/.gradle/init.d` because Maven Central rate-limits the session's address).
2. Paparazzi renders every native screen on the JVM. The data comes from the web app itself (`tools/gen_native_fixtures.js` runs the real page and calls each projection), with the app's real palettes (`ScreenshotPalette`). References live in `native/src/test/snapshots`; CI verifies them on every build.
3. A refactor leaves the references unchanged; a deliberate visual change re-records them in the same commit, and the images are looked at before committing.
4. The unused `OverviewActivity` was removed; its surface helpers live on in `app/shell`.

## Consequences

- The Food screen split (1,555 lines into a context and panes) was proven pixel-identical.
- Paparazzi cannot show device-only behaviour (insets, IME, real fonts at the owner's scale, timing); those remain the owner's acceptance on the phone.
- The first Paparazzi download is large and can stall; the build is serialized behind a lock when several engineers share the session.
