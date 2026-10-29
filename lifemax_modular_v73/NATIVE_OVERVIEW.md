# Native Overview

This is a native Android presentation of the existing Overview, not an independent
rewrite of its state or CSS layout engine. `OverviewActivity` contains native Canvas
surfaces inside Android scroll views and exposes its actions and static text to
TalkBack. It contains no WebView and stores no gameplay data.

This Activity uses Android's software Canvas path for predictable surface/shadow
composition. The other activities retain their existing rendering settings.

The existing page supplies measured card/text geometry, computed theme colors,
gradients and action identities. Android paints those primitives. Only original
SVG/canvas/image artwork is rasterized as assets; no screenshot of the page is
used. Tool actions return to the existing web tools and their original handlers.
This preserves a single authoritative save and the current customization/order.

The browser and older APKs retain the web Overview. Native API 9 is required.
A failed opening falls back to the original screen. A process restart also falls
back to the web host until the next valid snapshot. The native activity is private.
Only existing, connected DOM targets in the displayed scene can be activated.

## Checking

- `node tests/native-overview.test.js`: fallback and bridge contracts.
- `node tests/native-overview.browser.js`: measured text/artwork, themes and original
  tool actions (set `NATIVE_REFERENCE` to the modular source directory when running
  outside a full checkout).
- `python3 lifemax_modular_v73/tests/native-overview-build.test.py` from repository
  root: repeatable build/manifest patches and plugin registration.
- `python3 tests/native-cash-build.test.py` from repository root: existing build patches.
- The `codex/native-overview` branch builds an APK and runs `OverviewParityTest`
  in an API 34 emulator before any release is published. The test seeds sample data,
  captures the original Android WebView and native output, verifies the new Activity
  contains no WebView, checks image dimensions/difference and checks shared state.
- The `Native-Overview-review` workflow artifact contains the reference, native PNG,
  difference report and instrumentation reports. The numerical comparison is a
  broad regression check, not a claim that every pixel matches.

## Limits to evaluate on a phone

Layout measurement and state still depend on the shared web host. This version
is not a standalone Compose layout or an offline duplicate of the app engine.
Artwork refreshes every 2.5 seconds while this screen is visible; animated tool
previews therefore do not run at their original frame rate. Native text/gradient
rasterization can differ slightly from Chromium. Pixel-perfect parity and physical
phone performance require reviewing the screenshots and device behavior.
