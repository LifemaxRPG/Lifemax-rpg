# Chrono finish

This pass keeps the existing 24-hour face, colors, radii, labels, touch targets,
center content and scheduling behavior. It adds depth with SVG paint and a small
amount of motion, without a canvas, WebGL context or external assets.

- The chapter ring, fixed-time channel, session channel and center have separate
  surfaces. Fine directional highlights and dark contact edges separate them.
- Session and meal edges travel with their parent blocks. Outer edge strokes
  follow the existing planner sweep, and the stationary layers settle slightly
  apart during lock-in. No idle rotation or new repeating animation is added.
- The live hand eases between nearby readings. Its SVG transform remains the
  actual clock position; reduced motion, hidden pages and large jumps update
  immediately. The midnight transition takes the short path.
- Every SVG owns its gradient IDs. Today, Overview, setup and the widget export
  can coexist without borrowing another dial's paint definitions.
- Skipped and partially completed blocks retain their original dashed treatment.
  All decorative geometry ignores pointer events and is hidden from accessibility.

## Verification

From this directory:

```sh
node tests/director-meals.test.js
node tests/director-finish.test.js
node tests/director-finish.browser.js
```

The first two commands run 25 checks without browser dependencies; the release
workflow runs both before building the app. The browser check requires Playwright
and Chromium at `/usr/bin/chromium`.

Browser coverage uses the actual renderer, styles and animation code: phone
widths of 320/360/390/430 pixels, center text bounds, session/meal hit testing,
multiple dials, the real widget PNG exporter, hand pivot, build completion,
interruption cleanup and browser errors. It writes a fixture preview and widget
image under `/tmp/dial-finish-*.png`. The preview contains sample schedule data.

The baseline review also compared all interactive SVG elements against the
previous version: action attributes, geometry and accessible labels matched.
Physical-device smoothness and battery use still need observation on the phone.
